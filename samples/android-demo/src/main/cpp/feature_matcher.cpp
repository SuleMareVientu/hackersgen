#include "feature_matcher.h"
#include <android/log.h>

#define LOG_TAG "FeatureMatcher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

FeatureMatcher::FeatureMatcher() {}
FeatureMatcher::~FeatureMatcher() {}

float FeatureMatcher::computeBaseline(const CameraPose& a, const CameraPose& b) {
    cv::Mat c_a = -a.R.t() * a.t;
    cv::Mat c_b = -b.R.t() * b.t;
    return static_cast<float>(cv::norm(c_a - c_b));
}

cv::Mat FeatureMatcher::computeFundamentalMatrix(const CameraPose& pose_a, const CameraPose& pose_b, const cv::Mat& K_a, const cv::Mat& K_b) {
    // Relative pose from A to B
    cv::Mat R_ab = pose_b.R * pose_a.R.t();
    cv::Mat t_ab = pose_b.t - R_ab * pose_a.t;

    // Essential matrix: E = [t]_x R
    cv::Mat tx = (cv::Mat_<float>(3, 3) << 
                   0, -t_ab.at<float>(2), t_ab.at<float>(1),
                   t_ab.at<float>(2), 0, -t_ab.at<float>(0),
                  -t_ab.at<float>(1), t_ab.at<float>(0), 0);
    cv::Mat E = tx * R_ab;

    // Fundamental matrix: F = K_b^-T E K_a^-1
    cv::Mat K_a_inv = K_a.inv();
    cv::Mat K_b_inv = K_b.inv();
    cv::Mat F = K_b_inv.t() * E * K_a_inv;
    return F;
}

std::vector<FeatureMatch> FeatureMatcher::matchMNN(const std::vector<Keypoint>& kpts_a, const std::vector<Keypoint>& kpts_b) {
    std::vector<FeatureMatch> matches;
    if (kpts_a.empty() || kpts_b.empty()) return matches;
    
    int Na = kpts_a.size();
    int Nb = kpts_b.size();
    
    cv::Mat desc_a(Na, 64, CV_32F);
    for (int i = 0; i < Na; ++i) {
        std::memcpy(desc_a.ptr<float>(i), kpts_a[i].descriptor.data(), 64 * sizeof(float));
    }
    
    cv::Mat desc_b(Nb, 64, CV_32F);
    for (int j = 0; j < Nb; ++j) {
        std::memcpy(desc_b.ptr<float>(j), kpts_b[j].descriptor.data(), 64 * sizeof(float));
    }
    
    // Matrix multiplication: A * B^T
    cv::Mat scores = desc_a * desc_b.t();
    
    // Single sequential row-major pass to find mutual nearest neighbors (§3.4)
    std::vector<int> best_in_b(Na, -1);
    std::vector<float> best_score_in_b(Na, -1.0f);
    std::vector<int> best_in_a(Nb, -1);
    std::vector<float> best_score_in_a(Nb, -1.0f);

    for (int i = 0; i < Na; ++i) {
        const float* row = scores.ptr<float>(i);
        for (int j = 0; j < Nb; ++j) {
            float val = row[j];
            if (val > best_score_in_b[i]) {
                best_score_in_b[i] = val;
                best_in_b[i] = j;
            }
            if (val > best_score_in_a[j]) {
                best_score_in_a[j] = val;
                best_in_a[j] = i;
            }
        }
    }

    const float MATCH_THRESHOLD = 0.82f;
    for (int i = 0; i < Na; ++i) {
        if (best_score_in_b[i] > MATCH_THRESHOLD) {
            int j = best_in_b[i];
            if (j >= 0 && best_in_a[j] == i && best_score_in_a[j] > MATCH_THRESHOLD) {
                FeatureMatch m;
                m.idx_a = i;
                m.idx_b = j;
                m.distance = best_score_in_b[i];
                matches.push_back(m);
            }
        }
    }
    
    return matches;
}

std::vector<FeatureMatch> FeatureMatcher::filterEpipolar(
    const std::vector<FeatureMatch>& matches,
    const std::vector<Keypoint>& kpts_a,
    const std::vector<Keypoint>& kpts_b,
    const CameraPose& pose_a,
    const CameraPose& pose_b) {
    
    std::vector<FeatureMatch> filtered_matches;
    if (matches.empty()) return filtered_matches;

    cv::Mat F = computeFundamentalMatrix(pose_a, pose_b, pose_a.K, pose_b.K);
    float baseline = computeBaseline(pose_a, pose_b);

    // Adaptive threshold: scale from 5px for narrow baseline to 3px for wide baseline,
    // scaled proportionally to resolution relative to 1080p
    float res_scale = (pose_a.h > 0) ? (static_cast<float>(std::min(pose_a.w, pose_a.h)) / 1080.0f) : 1.0f;
    float threshold = (5.0f - (baseline - 0.1f) * (3.0f / 0.9f)) * res_scale;
    threshold = std::max(1.5f, std::min(5.0f, threshold));

    for (const auto& match : matches) {
        cv::Point2f pt_a = kpts_a[match.idx_a].pt;
        cv::Point2f pt_b = kpts_b[match.idx_b].pt;

        cv::Mat p1 = (cv::Mat_<float>(3, 1) << pt_a.x, pt_a.y, 1.0f);
        cv::Mat p2 = (cv::Mat_<float>(3, 1) << pt_b.x, pt_b.y, 1.0f);

        // Epipolar line in image B: l2 = F * p1
        cv::Mat l2 = F * p1;
        float a = l2.at<float>(0);
        float b_line = l2.at<float>(1);
        float c = l2.at<float>(2);

        // Distance from point p2 to line l2: |p2^T F p1| / sqrt(a^2 + b^2)
        float distance = std::abs(p2.dot(l2)) / std::sqrt(a*a + b_line*b_line);

        if (distance <= threshold) {
            filtered_matches.push_back(match);
        }
    }

    LOGI("Epipolar filter: kept %zu / %zu matches (thresh=%.2f px)", filtered_matches.size(), matches.size(), threshold);
    return filtered_matches;
}
