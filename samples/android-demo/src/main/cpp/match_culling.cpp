#include "match_culling.h"
#include <android/log.h>
#include <set>
#include <algorithm>

#define LOG_TAG "MatchCuller"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

MatchCuller::MatchCuller() {}
MatchCuller::~MatchCuller() {}

float MatchCuller::computeBaseline(const CameraPose& a, const CameraPose& b) {
    // Assuming R, t are World-to-Camera (Extrinsics):
    // C = -R^T * t
    cv::Mat c_a = -a.R.t() * a.t;
    cv::Mat c_b = -b.R.t() * b.t;
    return static_cast<float>(cv::norm(c_a - c_b));
}

float MatchCuller::computeLookVectorDot(const CameraPose& a, const CameraPose& b) {
    // In OpenCV, camera looks down +Z axis.
    // The look vector in world space is the 3rd row of R (or R^T * [0, 0, 1]^T)
    cv::Mat look_a = a.R.row(2).t();
    cv::Mat look_b = b.R.row(2).t();
    
    // Normalize just in case
    look_a /= cv::norm(look_a);
    look_b /= cv::norm(look_b);
    
    return static_cast<float>(look_a.dot(look_b));
}

std::vector<std::pair<int, int>> MatchCuller::getValidPairs(const std::vector<CameraPose>& poses) {
    int n = poses.size();
    if (n < 2) return {};

    // For each frame, collect all viable candidates with an overlap quality score (§3.3)
    struct Candidate {
        int neighbor_idx;
        float score;
    };
    std::vector<std::vector<Candidate>> frame_candidates(n);

    for (int i = 0; i < n; ++i) {
        for (int j = 0; j < n; ++j) {
            if (i == j) continue;
            float baseline = computeBaseline(poses[i], poses[j]);
            float look_dot = computeLookVectorDot(poses[i], poses[j]);

            if (baseline < min_baseline_ || baseline > max_baseline_ || look_dot < min_look_dot_) {
                continue;
            }

            // Quality score: high viewing direction alignment weighted by moderate baseline
            float score = look_dot / (1.0f + baseline);
            frame_candidates[i].push_back({j, score});
        }
    }

    // Cap each frame's neighbors to max_neighbors_per_frame_ to enforce O(N) pair complexity
    std::set<std::pair<int, int>> unique_pairs;
    for (int i = 0; i < n; ++i) {
        auto& cands = frame_candidates[i];
        std::sort(cands.begin(), cands.end(), [](const Candidate& a, const Candidate& b) {
            return a.score > b.score;
        });

        int limit = std::min(static_cast<int>(cands.size()), max_neighbors_per_frame_);
        for (int k = 0; k < limit; ++k) {
            int j = cands[k].neighbor_idx;
            unique_pairs.insert({std::min(i, j), std::max(i, j)});
        }
    }

    std::vector<std::pair<int, int>> valid_pairs(unique_pairs.begin(), unique_pairs.end());
    LOGI("Generated %zu valid pairs (capped at %d neighbors/frame) out of %d total possible pairs",
         valid_pairs.size(), max_neighbors_per_frame_, (n * (n - 1)) / 2);
    return valid_pairs;
}
