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
                    if (z <= 0.01f) {
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
    float voxel_size_m,
    int knn_k,
    float std_ratio) {

    // 1. Merge Tier 1 (anchors) and Tier 2 (consistent dense fill)
    std::vector<Track> all_tracks;
    all_tracks.reserve(tier1_tracks.size() + tier2_tracks.size());

    for (const auto& t : tier1_tracks) {
        if (t.valid) all_tracks.push_back(t);
    }
    for (const auto& t : tier2_tracks) {
        if (t.valid) all_tracks.push_back(t);
    }

    if (all_tracks.size() < 10) return all_tracks;

    LOGI("Fused raw point cloud: %zu anchors + %zu dense = %zu total points",
         tier1_tracks.size(), tier2_tracks.size(), all_tracks.size());

    // 2. Statistical Outlier Removal (SOR) via grid-accelerated spatial hashing
    float search_cell_size = 0.010f; // 1.0cm query cells (scaled for 1.5mm resolution)
    std::unordered_map<int64_t, std::vector<int>> search_grid;

    for (size_t i = 0; i < all_tracks.size(); ++i) {
        const auto& p = all_tracks[i].pt3d;
        int ix = static_cast<int>(std::floor(p.x / search_cell_size));
        int iy = static_cast<int>(std::floor(p.y / search_cell_size));
        int iz = static_cast<int>(std::floor(p.z / search_cell_size));
        search_grid[hashVoxel(ix, iy, iz)].push_back(static_cast<int>(i));
    }

    std::vector<float> avg_distances(all_tracks.size(), 0.0f);
    std::vector<bool> sor_keep(all_tracks.size(), true);

    int num_threads_sor = std::max(1u, std::thread::hardware_concurrency());
    std::vector<std::thread> sor_workers;
    std::atomic<size_t> next_sor_idx{0};

    for (int t = 0; t < num_threads_sor; ++t) {
        sor_workers.emplace_back([&]() {
            while (true) {
                size_t i = next_sor_idx.fetch_add(1);
                if (i >= all_tracks.size()) break;

                const auto& p = all_tracks[i].pt3d;
                int ix = static_cast<int>(std::floor(p.x / search_cell_size));
                int iy = static_cast<int>(std::floor(p.y / search_cell_size));
                int iz = static_cast<int>(std::floor(p.z / search_cell_size));

                std::vector<float> neighbor_dists;

                for (int dx = -1; dx <= 1; ++dx) {
                    for (int dy = -1; dy <= 1; ++dy) {
                        for (int dz = -1; dz <= 1; ++dz) {
                            auto it = search_grid.find(hashVoxel(ix + dx, iy + dy, iz + dz));
                            if (it != search_grid.end()) {
                                for (int neighbor_idx : it->second) {
                                    if (neighbor_idx == static_cast<int>(i)) continue;
                                    const auto& np = all_tracks[neighbor_idx].pt3d;
                                    float d = cv::norm(p - np);
                                    neighbor_dists.push_back(d);
                                }
                            }
                        }
                    }
                }

                if (neighbor_dists.size() < 2) {
                    avg_distances[i] = 999.0f;
                    continue;
                }

                std::sort(neighbor_dists.begin(), neighbor_dists.end());
                int k_use = std::min(static_cast<int>(neighbor_dists.size()), knn_k);
                float sum_d = 0.0f;
                for (int k = 0; k < k_use; ++k) {
                    sum_d += neighbor_dists[k];
                }
                avg_distances[i] = sum_d / k_use;
            }
        });
    }
    for (auto& w : sor_workers) {
        w.join();
    }

    // Compute distribution statistics across valid points
    double sum = 0.0;
    int valid_count = 0;
    for (float d : avg_distances) {
        if (d < 900.0f) {
            sum += d;
            valid_count++;
        }
    }

    if (valid_count > 0) {
        double mean = sum / valid_count;
        double sq_sum = 0.0;
        for (float d : avg_distances) {
            if (d < 900.0f) {
                sq_sum += (d - mean) * (d - mean);
            }
        }
        double std_dev = std::sqrt(sq_sum / valid_count);
        double threshold = mean + std_ratio * std_dev;

        for (size_t i = 0; i < all_tracks.size(); ++i) {
            if (i < tier1_tracks.size()) {
                // Ground-truth Tier 1 anchor features are always protected from SOR rejection
                continue;
            }
            if (avg_distances[i] > threshold) {
                sor_keep[i] = false;
            }
        }
    }

    // 3. Voxel Grid Spatial Thinning (§3.9)
    std::unordered_map<int64_t, std::vector<int>> voxel_buckets;
    for (size_t i = 0; i < all_tracks.size(); ++i) {
        if (!sor_keep[i]) continue;
        const auto& p = all_tracks[i].pt3d;
        int vx = static_cast<int>(std::floor(p.x / voxel_size_m));
        int vy = static_cast<int>(std::floor(p.y / voxel_size_m));
        int vz = static_cast<int>(std::floor(p.z / voxel_size_m));
        voxel_buckets[hashVoxel(vx, vy, vz)].push_back(static_cast<int>(i));
    }

    std::vector<Track> final_filtered_tracks;
    final_filtered_tracks.reserve(tier1_tracks.size() + voxel_buckets.size() * 2);

    // Unconditionally retain all valid Tier 1 anchor tracks (§3.9)
    for (const auto& t : tier1_tracks) {
        if (t.valid) final_filtered_tracks.push_back(t);
    }

    // Retain up to MAX_POINTS_PER_VOXEL Tier 2 dense fill points per voxel (uniform 1.0-1.5mm pitch)
    const size_t MAX_POINTS_PER_VOXEL = 4;
    for (auto& kv : voxel_buckets) {
        auto& indices = kv.second;
        // Prioritize points with more multi-view observations
        std::sort(indices.begin(), indices.end(), [&](int a, int b) {
            return all_tracks[a].observations.size() > all_tracks[b].observations.size();
        });

        size_t kept_in_voxel = 0;
        for (int idx : indices) {
            if (static_cast<size_t>(idx) < tier1_tracks.size()) {
                // Already preserved as Tier 1 anchor
                continue;
            }
            final_filtered_tracks.push_back(std::move(all_tracks[idx]));
            kept_in_voxel++;
            if (kept_in_voxel >= MAX_POINTS_PER_VOXEL) break;
        }
    }

    LOGI("Fusion & Thinning complete (§3.9): retained %zu points (%zu anchors + %zu dense) after SOR & %.1fmm voxel grid",
         final_filtered_tracks.size(), tier1_tracks.size(),
         final_filtered_tracks.size() > tier1_tracks.size() ? final_filtered_tracks.size() - tier1_tracks.size() : 0,
         voxel_size_m * 1000.0f);
    return final_filtered_tracks;
}
