#include "dense_tracker.h"
#include <android/log.h>
#include <cmath>
#include <thread>
#include <atomic>
#include <algorithm>

#define LOG_TAG "DenseTracker"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

DenseTracker::DenseTracker() {}
DenseTracker::~DenseTracker() {}

cv::Mat DenseTracker::computeFundamentalMatrix(const CameraPose& pose_a, const CameraPose& pose_b) {
    cv::Mat R_ab = pose_b.R * pose_a.R.t();
    cv::Mat t_ab = pose_b.t - R_ab * pose_a.t;

    cv::Mat tx = (cv::Mat_<float>(3, 3) << 
                   0, -t_ab.at<float>(2), t_ab.at<float>(1),
                   t_ab.at<float>(2), 0, -t_ab.at<float>(0),
                  -t_ab.at<float>(1), t_ab.at<float>(0), 0);
    cv::Mat E = tx * R_ab;

    cv::Mat K_a_inv = pose_a.K.inv();
    cv::Mat K_b_inv = pose_b.K.inv();
    return K_b_inv.t() * E * K_a_inv;
}

cv::Vec3f DenseTracker::computeEpipolarLine(const cv::Point2f& pt, const cv::Mat& F) {
    cv::Mat p = (cv::Mat_<float>(3, 1) << pt.x, pt.y, 1.0f);
    cv::Mat l = F * p;
    return cv::Vec3f(l.at<float>(0), l.at<float>(1), l.at<float>(2));
}

cv::Point2f DenseTracker::projectToEpipolarLine(const cv::Point2f& pt, const cv::Vec3f& line) {
    float a = line[0];
    float b = line[1];
    float c = line[2];
    float denom = a * a + b * b;
    if (denom < 1e-8f) return pt;

    float dist = (a * pt.x + b * pt.y + c) / denom;
    return cv::Point2f(pt.x - a * dist, pt.y - b * dist);
}

bool DenseTracker::triangulateDenseTrack(
    DenseTrack& track, 
    const std::vector<CameraPose>& poses, 
    const std::vector<cv::Mat>& cam_centers,
    float min_parallax_deg) {
    
    if (track.observations.size() < 2) return false;

    // Check physical baseline between first and last camera
    const auto& first_obs = track.observations.front();
    const auto& last_obs = track.observations.back();
    const auto& pose1 = poses[first_obs.camera_idx];
    const auto& pose2 = poses[last_obs.camera_idx];

    const cv::Mat& c1 = cam_centers[first_obs.camera_idx];
    const cv::Mat& c2 = cam_centers[last_obs.camera_idx];
    float baseline_dist = static_cast<float>(cv::norm(c1 - c2));
    if (baseline_dist < 0.008f) return false; // Minimum physical baseline: 8mm

    // DLT formulation
    cv::Mat A(static_cast<int>(track.observations.size() * 2), 4, CV_32F);
    for (size_t i = 0; i < track.observations.size(); ++i) {
        const auto& obs = track.observations[i];
        const auto& pose = poses[obs.camera_idx];

        cv::Mat Rt;
        cv::hconcat(pose.R, pose.t, Rt);
        cv::Mat P = pose.K * Rt;

        float u = obs.pt2d.x;
        float v = obs.pt2d.y;

        cv::Mat row0 = u * P.row(2) - P.row(0);
        cv::Mat row1 = v * P.row(2) - P.row(1);

        row0.copyTo(A.row(static_cast<int>(i * 2)));
        row1.copyTo(A.row(static_cast<int>(i * 2 + 1)));
    }

    cv::Mat X;
    cv::SVD::solveZ(A, X);

    float w = X.at<float>(3, 0);
    if (std::abs(w) < 1e-6f) return false;

    track.pt3d = cv::Point3f(
        X.at<float>(0, 0) / w,
        X.at<float>(1, 0) / w,
        X.at<float>(2, 0) / w
    );

    // Fast positive depth check in front of both cameras
    cv::Mat pt3d_m = (cv::Mat_<float>(3, 1) << track.pt3d.x, track.pt3d.y, track.pt3d.z);
    cv::Mat p_cam1 = pose1.R * pt3d_m + pose1.t;
    cv::Mat p_cam2 = pose2.R * pt3d_m + pose2.t;
    if (p_cam1.at<float>(2, 0) <= 0.05f || p_cam2.at<float>(2, 0) <= 0.05f) return false;

    // Parallax angle check
    cv::Point3f p = track.pt3d;
    cv::Point3f cam1_pt(c1.at<float>(0), c1.at<float>(1), c1.at<float>(2));
    cv::Point3f cam2_pt(c2.at<float>(0), c2.at<float>(1), c2.at<float>(2));

    cv::Point3f ray1 = p - cam1_pt;
    cv::Point3f ray2 = p - cam2_pt;
    float n1 = cv::norm(ray1);
    float n2 = cv::norm(ray2);
    if (n1 < 1e-4f || n2 < 1e-4f) return false;

    ray1 *= (1.0f / n1);
    ray2 *= (1.0f / n2);
    float dot = std::max(-1.0f, std::min(1.0f, ray1.dot(ray2)));
    float angle_deg = std::acos(dot) * 180.0f / 3.1415926535f;
    if (angle_deg < min_parallax_deg) return false;

    // Non-linear Levenberg-Marquardt refinement with Huber loss (§3.4, §3.7)
    // Enforces sub-pixel reprojection error (<= 1.2 px) and metric convergence
    if (!Triangulator::refinePointLM(track.observations, poses, track.pt3d, 1.2f, 10)) {
        return false;
    }

    track.valid = true;
    return true;
}

std::vector<DenseTrack> DenseTracker::trackAndTriangulate(
    const std::vector<cv::Mat>& images,
    const std::vector<CameraPose>& poses,
    float min_parallax_deg,
    int grid_step,
    int window_size) {

    std::vector<DenseTrack> triangulated_tracks;
    if (images.size() < 2 || poses.size() != images.size()) return triangulated_tracks;

    int n_frames = images.size();

    // Precompute camera optical centers
    std::vector<cv::Mat> cam_centers(n_frames);
    for (int i = 0; i < n_frames; ++i) {
        cam_centers[i] = -poses[i].R.t() * poses[i].t;
    }

    // Prepare grayscale representations for optical flow in parallel
    std::vector<cv::Mat> grays(n_frames);
    int num_threads_gray = std::max(1u, std::thread::hardware_concurrency());
    std::vector<std::thread> gray_workers;
    std::atomic<int> next_gray_idx{0};

    for (int t = 0; t < num_threads_gray; ++t) {
        gray_workers.emplace_back([&]() {
            while (true) {
                int i = next_gray_idx.fetch_add(1);
                if (i >= n_frames) break;
                if (images[i].channels() == 3) {
                    cv::cvtColor(images[i], grays[i], cv::COLOR_BGR2GRAY);
                } else if (images[i].channels() == 4) {
                    cv::cvtColor(images[i], grays[i], cv::COLOR_BGRA2GRAY);
                } else {
                    grays[i] = images[i];
                }
            }
        });
    }
    for (auto& w : gray_workers) { w.join(); }

    // Step across key frames to initiate dense rolling tracks (§3.7)
    struct WindowSpan {
        int start_frame;
        int end_frame;
    };
    std::vector<WindowSpan> windows;
    int stride = std::max(1, window_size / 2);

    for (int start_frame = 0; start_frame < n_frames - 1; start_frame += stride) {
        int end_frame = std::min(n_frames - 1, start_frame + window_size);
        if (end_frame - start_frame >= 2) {
            windows.push_back({start_frame, end_frame});
        }
    }

    // Parallel execution of rolling optical flow windows across all CPU cores
    std::vector<std::vector<DenseTrack>> window_results(windows.size());
    int num_threads_win = std::max(1u, std::thread::hardware_concurrency());
    std::vector<std::thread> win_workers;
    std::atomic<size_t> next_win_idx{0};

    for (int t = 0; t < num_threads_win; ++t) {
        win_workers.emplace_back([&]() {
            while (true) {
                size_t win_idx = next_win_idx.fetch_add(1);
                if (win_idx >= windows.size()) break;

                int start_frame = windows[win_idx].start_frame;
                int end_frame = windows[win_idx].end_frame;

                const cv::Mat& init_gray = grays[start_frame];
                int w = init_gray.cols;
                int h = init_gray.rows;
                int min_dim = std::min(w, h);

                int pyr_levels = (min_dim >= 1000) ? 4 : (min_dim >= 700 ? 3 : 2);
                int win_sz = (min_dim >= 1000) ? 25 : (min_dim >= 700 ? 21 : 15);
                cv::Size lk_win_size(win_sz, win_sz);

                float max_cyclic_drift = (min_dim >= 1000) ? 0.8f : (min_dim >= 700 ? 0.6f : 0.5f);
                float cyclic_drift_sq = max_cyclic_drift * max_cyclic_drift;

                float max_epipolar_dist = (min_dim >= 1000) ? 1.2f : (min_dim >= 700 ? 0.9f : 0.6f);

                // Compute gradient magnitude for fast vectorized texture detection
                cv::Mat grad_x, grad_y, grad_mag;
                cv::Sobel(init_gray, grad_x, CV_16S, 1, 0, 3);
                cv::Sobel(init_gray, grad_y, CV_16S, 0, 1, 3);
                cv::convertScaleAbs(grad_x, grad_x);
                cv::convertScaleAbs(grad_y, grad_y);
                cv::addWeighted(grad_x, 0.5, grad_y, 0.5, 0, grad_mag);

                // Seed dense grid points on textured regions (§3.7)
                std::vector<cv::Point2f> current_pts;
                for (int y = grid_step; y < h - grid_step; y += grid_step) {
                    for (int x = grid_step; x < w - grid_step; x += grid_step) {
                        if (grad_mag.at<uchar>(y, x) > 12) {
                            current_pts.emplace_back(static_cast<float>(x), static_cast<float>(y));
                        }
                    }
                }

                if (current_pts.empty()) continue;

                // Active tracks originating at start_frame
                std::vector<DenseTrack> active_tracks(current_pts.size());
                std::vector<bool> track_alive(current_pts.size(), true);

                for (size_t i = 0; i < current_pts.size(); ++i) {
                    active_tracks[i].observations.push_back({start_frame, current_pts[i]});
                }

                // Chained Lucas-Kanade optical flow with bidirectional cyclic consistency (§3.7)
                for (int f = start_frame; f < end_frame; ++f) {
                    std::vector<cv::Point2f> valid_prev_pts;
                    std::vector<size_t> valid_indices;

                    for (size_t i = 0; i < current_pts.size(); ++i) {
                        if (track_alive[i]) {
                            valid_prev_pts.push_back(current_pts[i]);
                            valid_indices.push_back(i);
                        }
                    }

                    if (valid_prev_pts.empty()) break;

                    std::vector<cv::Point2f> next_pts;
                    std::vector<uchar> fwd_status;
                    std::vector<float> fwd_err;

                    // Forward optical flow scaled to resolution
                    cv::calcOpticalFlowPyrLK(
                        grays[f], grays[f + 1],
                        valid_prev_pts, next_pts,
                        fwd_status, fwd_err,
                        lk_win_size, pyr_levels,
                        cv::TermCriteria(cv::TermCriteria::COUNT + cv::TermCriteria::EPS, 30, 0.01));

                    // Backward optical flow for cyclic verification
                    std::vector<cv::Point2f> back_pts;
                    std::vector<uchar> bwd_status;
                    std::vector<float> bwd_err;

                    cv::calcOpticalFlowPyrLK(
                        grays[f + 1], grays[f],
                        next_pts, back_pts,
                        bwd_status, bwd_err,
                        lk_win_size, pyr_levels,
                        cv::TermCriteria(cv::TermCriteria::COUNT + cv::TermCriteria::EPS, 30, 0.01));

                    cv::Mat F = computeFundamentalMatrix(poses[f], poses[f + 1]);

                    for (size_t j = 0; j < valid_indices.size(); ++j) {
                        size_t orig_idx = valid_indices[j];
                        if (!fwd_status[j] || !bwd_status[j]) {
                            track_alive[orig_idx] = false;
                            continue;
                        }

                        // Cyclic consistency check: forward-backward drift
                        float cyclic_dx = valid_prev_pts[j].x - back_pts[j].x;
                        float cyclic_dy = valid_prev_pts[j].y - back_pts[j].y;
                        if (cyclic_dx * cyclic_dx + cyclic_dy * cyclic_dy > cyclic_drift_sq) {
                            track_alive[orig_idx] = false;
                            continue;
                        }

                        cv::Point2f tracked_pt = next_pts[j];
                        // Boundary check
                        if (tracked_pt.x < 3 || tracked_pt.x >= w - 3 || tracked_pt.y < 3 || tracked_pt.y >= h - 3) {
                            track_alive[orig_idx] = false;
                            continue;
                        }

                        // Epipolar constraint verification (§3.7)
                        cv::Vec3f epipolar_line = computeEpipolarLine(valid_prev_pts[j], F);
                        float a = epipolar_line[0];
                        float b = epipolar_line[1];
                        float c = epipolar_line[2];
                        float line_norm = std::sqrt(a * a + b * b);

                        if (line_norm > 1e-6f) {
                            float dist = std::abs(a * tracked_pt.x + b * tracked_pt.y + c) / line_norm;
                            if (dist > max_epipolar_dist) { // Strict resolution-scaled epipolar gating
                                track_alive[orig_idx] = false;
                                continue;
                            }
                        }

                        current_pts[orig_idx] = tracked_pt;
                        active_tracks[orig_idx].observations.push_back({f + 1, tracked_pt});
                    }
                }

                // Filter tracks with at least 2 observations and triangulate
                std::vector<DenseTrack> valid_window_tracks;
                for (auto& track : active_tracks) {
                    if (track.observations.size() >= 2) {
                        if (triangulateDenseTrack(track, poses, cam_centers, min_parallax_deg)) {
                            valid_window_tracks.push_back(std::move(track));
                        }
                    }
                }
                window_results[win_idx] = std::move(valid_window_tracks);
            }
        });
    }
    for (auto& w : win_workers) { w.join(); }

    for (auto& wr : window_results) {
        triangulated_tracks.insert(triangulated_tracks.end(),
                                   std::make_move_iterator(wr.begin()),
                                   std::make_move_iterator(wr.end()));
    }

    LOGI("DenseTracker: generated %zu dense candidate points across frames", triangulated_tracks.size());
    return triangulated_tracks;
}
