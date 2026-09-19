#include "point_cloud_filter.h"
#include <android/log.h>
#include <unordered_map>
#include <algorithm>
#include <cmath>
#include <numeric>
#include <thread>
#include <atomic>

#define LOG_TAG "PointCloudFilter"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

PointCloudFilter::PointCloudFilter() {}
PointCloudFilter::~PointCloudFilter() {}

std::vector<Track> PointCloudFilter::filterConsistency(
    const std::vector<DenseTrack>& dense_tracks,
    const std::vector<CameraPose>& poses,
    int min_views,
    float max_reproj_err) {

    std::vector<Track> consistent_tracks;
    if (dense_tracks.empty()) return consistent_tracks;

    std::vector<bool> keep(dense_tracks.size(), false);
    int num_threads = std::max(1u, std::thread::hardware_concurrency());
    std::vector<std::thread> workers;
    std::atomic<size_t> next_idx{0};

    for (int t = 0; t < num_threads; ++t) {
        workers.emplace_back([&]() {
            while (true) {
                size_t i = next_idx.fetch_add(1);
                if (i >= dense_tracks.size()) break;
                const auto& dt = dense_tracks[i];
                if (!dt.valid) continue;
                if (static_cast<int>(dt.observations.size()) < min_views) continue;

                float total_err = 0.0f;
                bool all_valid = true;

                for (const auto& obs : dt.observations) {
                    if (obs.camera_idx >= poses.size()) {
                        all_valid = false;
                        break;
                    }
                    const auto& pose = poses[obs.camera_idx];
                    cv::Mat pt3d_mat = (cv::Mat_<float>(3, 1) << dt.pt3d.x, dt.pt3d.y, dt.pt3d.z);
                    cv::Mat pt_cam = pose.R * pt3d_mat + pose.t;
                    cv::Mat pt_proj = pose.K * pt_cam;

                    float z = pt_proj.at<float>(2, 0);
                    if (z < 0.20f || z > 3.50f) {
                        all_valid = false;
                        break;
                    }

                    float u_proj = pt_proj.at<float>(0, 0) / z;
                    float v_proj = pt_proj.at<float>(1, 0) / z;

                    float du = u_proj - obs.pt2d.x;
                    float dv = v_proj - obs.pt2d.y;
                    float err = std::sqrt(du * du + dv * dv);
                    if (err > max_reproj_err * 1.5f) {
                        all_valid = false;
                        break;
                    }
                    total_err += err;
                }

                if (!all_valid) continue;

                float mean_err = total_err / dt.observations.size();
                if (mean_err <= max_reproj_err) {
                    keep[i] = true;
                }
            }
        });
    }
    for (auto& w : workers) {
        w.join();
    }

    consistent_tracks.reserve(dense_tracks.size());
    for (size_t i = 0; i < dense_tracks.size(); ++i) {
        if (keep[i]) {
            Track t;
            t.observations = dense_tracks[i].observations;
            t.pt3d = dense_tracks[i].pt3d;
            t.valid = true;
            consistent_tracks.push_back(std::move(t));
        }
    }

    LOGI("Consistency filter (§3.8): retained %zu / %zu dense candidate points (min_views=%d, max_err=%.2f px)",
         consistent_tracks.size(), dense_tracks.size(), min_views, max_reproj_err);
    return consistent_tracks;
}

namespace {
    inline int64_t hashVoxel(int x, int y, int z) {
        // Spatial hash combining 3 coordinates
        const int64_t p1 = 73856093;
        const int64_t p2 = 19349663;
        const int64_t p3 = 83492791;
        return (x * p1) ^ (y * p2) ^ (z * p3);
    }
}

std::vector<Track> PointCloudFilter::fuseAndFilter(
    const std::vector<Track>& tier1_tracks,
    const std::vector<Track>& tier2_tracks,
    const std::vector<CameraPose>& poses,
    float voxel_size_m) {

    // 1. Audit Tier 1 tracks against camera poses (reprojection error & bounded depth)
    std::vector<Track> tier1_audited;
    tier1_audited.reserve(tier1_tracks.size());

    const float max_tier1_reproj_err = 1.8f;
    const float min_depth = 0.20f;
    const float max_depth = 3.50f;

    for (const auto& t : tier1_tracks) {
        if (!t.valid || t.observations.size() < 2) continue;

        bool valid = true;
        if (!poses.empty()) {
            cv::Mat pt3d_mat = (cv::Mat_<float>(3, 1) << t.pt3d.x, t.pt3d.y, t.pt3d.z);
            float total_err = 0.0f;
            for (const auto& obs : t.observations) {
                if (obs.camera_idx >= poses.size()) {
                    valid = false;
                    break;
                }
                const auto& pose = poses[obs.camera_idx];
                cv::Mat pt_cam = pose.R * pt3d_mat + pose.t;
                float z = pt_cam.at<float>(2, 0);
                if (z < min_depth || z > max_depth) {
                    valid = false;
                    break;
                }

                cv::Mat pt_proj = pose.K * pt_cam;
                float u_proj = pt_proj.at<float>(0, 0) / z;
                float v_proj = pt_proj.at<float>(1, 0) / z;
                float du = u_proj - obs.pt2d.x;
                float dv = v_proj - obs.pt2d.y;
                float err = std::sqrt(du * du + dv * dv);
                if (err > max_tier1_reproj_err * 1.5f) {
                    valid = false;
                    break;
                }
                total_err += err;
            }
            if (valid) {
                float mean_err = total_err / t.observations.size();
                if (mean_err > max_tier1_reproj_err) {
                    valid = false;
                }
            }
        }

        if (valid) {
            tier1_audited.push_back(t);
        }
    }

    LOGI("Audited Tier 1 tracks: %zu / %zu retained (pruned %zu rogue anchors with reproj error > %.1fpx or depth outside [%.2f, %.2f]m)",
         tier1_audited.size(), tier1_tracks.size(),
         tier1_tracks.size() - tier1_audited.size(),
         max_tier1_reproj_err, min_depth, max_depth);

    // 2. Merge audited Tier 1 and consistent Tier 2 dense fill
    std::vector<Track> all_tracks;
    all_tracks.reserve(tier1_audited.size() + tier2_tracks.size());

    size_t num_tier1 = tier1_audited.size();
    for (auto& t : tier1_audited) {
        all_tracks.push_back(std::move(t));
    }
    for (const auto& t : tier2_tracks) {
        if (t.valid) all_tracks.push_back(t);
    }

    if (all_tracks.size() < 10) return all_tracks;

    LOGI("Fused raw point cloud: %zu anchors + %zu dense = %zu total candidate points",
         num_tier1, tier2_tracks.size(), all_tracks.size());

    // 3. Ultra-Conservative Wide-Radius Isolation Filter (5.0cm radius, min 2 neighbors)
    // Solitary floaters in mid-air have 0 or 1 neighbor in a 10cm sphere.
    // Thin geometry (wires, chair legs, rims) has dozens of points within 5cm along the structure.
    const float isolation_radius = 0.050f; // 5.0 cm
    const float isolation_radius_sq = isolation_radius * isolation_radius;
    const float isolation_cell_size = 0.050f; // 5.0 cm grid cells

    std::unordered_map<int64_t, std::vector<int>> isolation_grid;
    for (size_t i = 0; i < all_tracks.size(); ++i) {
        const auto& p = all_tracks[i].pt3d;
        int ix = static_cast<int>(std::floor(p.x / isolation_cell_size));
        int iy = static_cast<int>(std::floor(p.y / isolation_cell_size));
        int iz = static_cast<int>(std::floor(p.z / isolation_cell_size));
        isolation_grid[hashVoxel(ix, iy, iz)].push_back(static_cast<int>(i));
    }

    std::vector<bool> keep_isolated(all_tracks.size(), true);
    int num_threads_iso = std::max(1u, std::thread::hardware_concurrency());
    std::vector<std::thread> iso_workers;
    std::atomic<size_t> next_iso_idx{0};

    for (int t = 0; t < num_threads_iso; ++t) {
        iso_workers.emplace_back([&]() {
            while (true) {
                size_t i = next_iso_idx.fetch_add(1);
                if (i >= all_tracks.size()) break;

                const auto& p = all_tracks[i].pt3d;
                int ix = static_cast<int>(std::floor(p.x / isolation_cell_size));
                int iy = static_cast<int>(std::floor(p.y / isolation_cell_size));
                int iz = static_cast<int>(std::floor(p.z / isolation_cell_size));

                int neighbor_count = 0;
                bool found_enough = false;

                for (int dx = -1; dx <= 1 && !found_enough; ++dx) {
                    for (int dy = -1; dy <= 1 && !found_enough; ++dy) {
                        for (int dz = -1; dz <= 1 && !found_enough; ++dz) {
                            auto it = isolation_grid.find(hashVoxel(ix + dx, iy + dy, iz + dz));
                            if (it != isolation_grid.end()) {
                                for (int neighbor_idx : it->second) {
                                    if (neighbor_idx == static_cast<int>(i)) continue;
                                    const auto& np = all_tracks[neighbor_idx].pt3d;
                                    float ddx = p.x - np.x;
                                    float ddy = p.y - np.y;
                                    float ddz = p.z - np.z;
                                    if ((ddx * ddx + ddy * ddy + ddz * ddz) <= isolation_radius_sq) {
                                        neighbor_count++;
                                        if (neighbor_count >= 2) {
                                            found_enough = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (neighbor_count < 2) {
                    keep_isolated[i] = false;
                }
            }
        });
    }
    for (auto& w : iso_workers) {
        w.join();
    }

    size_t isolated_removed = 0;
    for (bool k : keep_isolated) {
        if (!k) isolated_removed++;
    }
    LOGI("Wide-radius isolation filter (5cm): pruned %zu solitary floaters (< 2 neighbors in 10cm sphere)",
         isolated_removed);

    // 4. Voxel Grid Spatial Thinning (§3.9)
    // Preserves 1.5mm uniform pitch for dense manifold coverage without clumping
    std::unordered_map<int64_t, std::vector<int>> voxel_buckets;
    for (size_t i = 0; i < all_tracks.size(); ++i) {
        if (!keep_isolated[i]) continue;
        const auto& p = all_tracks[i].pt3d;
        int vx = static_cast<int>(std::floor(p.x / voxel_size_m));
        int vy = static_cast<int>(std::floor(p.y / voxel_size_m));
        int vz = static_cast<int>(std::floor(p.z / voxel_size_m));
        voxel_buckets[hashVoxel(vx, vy, vz)].push_back(static_cast<int>(i));
    }

    std::vector<Track> final_filtered_tracks;
    final_filtered_tracks.reserve(voxel_buckets.size() * 2);

    const size_t MAX_POINTS_PER_VOXEL = 2;
    size_t kept_anchors = 0;
    size_t kept_dense = 0;

    for (auto& kv : voxel_buckets) {
        auto& indices = kv.second;

        std::vector<int> t1_indices;
        std::vector<int> t2_indices;
        for (int idx : indices) {
            if (static_cast<size_t>(idx) < num_tier1) {
                t1_indices.push_back(idx);
            } else {
                t2_indices.push_back(idx);
            }
        }

        // Add Tier 1 anchors in this voxel
        for (int idx : t1_indices) {
            final_filtered_tracks.push_back(std::move(all_tracks[idx]));
            kept_anchors++;
        }

        // Sort Tier 2 by observation count (prioritize points seen by more views)
        std::sort(t2_indices.begin(), t2_indices.end(), [&](int a, int b) {
            return all_tracks[a].observations.size() > all_tracks[b].observations.size();
        });

        size_t t2_to_keep = std::min(t2_indices.size(), MAX_POINTS_PER_VOXEL);
        for (size_t k = 0; k < t2_to_keep; ++k) {
            final_filtered_tracks.push_back(std::move(all_tracks[t2_indices[k]]));
            kept_dense++;
        }
    }

    LOGI("Fusion & Thinning complete (§3.9): retained %zu points (%zu anchors + %zu dense) after isolation & %.1fmm voxel grid",
         final_filtered_tracks.size(), kept_anchors, kept_dense, voxel_size_m * 1000.0f);
    return final_filtered_tracks;
}
