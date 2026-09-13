#include "triangulator.h"
#include <cmath>
#include <android/log.h>

#define LOG_TAG "Triangulator"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

Triangulator::Triangulator() {}
Triangulator::~Triangulator() {}

bool Triangulator::triangulateTrack(Track& track, const std::vector<CameraPose>& poses) {
    if (track.observations.size() < 2) return false;

    cv::Mat A(static_cast<int>(track.observations.size() * 2), 4, CV_32F);

    for (size_t i = 0; i < track.observations.size(); ++i) {
        const auto& obs = track.observations[i];
        if (obs.camera_idx >= poses.size()) return false;
        const auto& pose = poses[obs.camera_idx];

        cv::Mat Rt;
        cv::hconcat(pose.R, pose.t, Rt); // 3x4
        cv::Mat P = pose.K * Rt; // 3x4

        float u = obs.pt2d.x;
        float v = obs.pt2d.y;

        cv::Mat row0 = u * P.row(2) - P.row(0);
        cv::Mat row1 = v * P.row(2) - P.row(1);

        row0.copyTo(A.row(static_cast<int>(i * 2)));
        row1.copyTo(A.row(static_cast<int>(i * 2 + 1)));
    }

    cv::Mat X;
    cv::SVD::solveZ(A, X); // X is 4x1

    float w = X.at<float>(3, 0);
    if (std::abs(w) < 1e-6f) return false;

    track.pt3d = cv::Point3f(
        X.at<float>(0, 0) / w,
        X.at<float>(1, 0) / w,
        X.at<float>(2, 0) / w
    );

    // Parallax angle check
    float parallax = computeParallax(track, poses);
    if (parallax < min_parallax_deg_) return false;

    // Non-linear Levenberg-Marquardt refinement with Huber loss (§3.4)
    if (!refinePointLM(track.observations, poses, track.pt3d, max_reproj_error_, 10)) {
        return false;
    }

    track.valid = true;
    return true;
}

float Triangulator::computeParallax(const Track& track, const std::vector<CameraPose>& poses) {
    if (track.observations.size() < 2) return 0.0f;

    cv::Point3f pt = track.pt3d;
    float max_parallax = 0.0f;

    for (size_t i = 0; i < track.observations.size(); ++i) {
        cv::Mat c1 = -poses[track.observations[i].camera_idx].R.t() * poses[track.observations[i].camera_idx].t;
        cv::Point3f cam1(c1.at<float>(0, 0), c1.at<float>(1, 0), c1.at<float>(2, 0));
        cv::Point3f ray1 = pt - cam1;
        float norm1 = cv::norm(ray1);
        if (norm1 < 1e-6f) continue;
        ray1 *= (1.0f / norm1);

        for (size_t j = i + 1; j < track.observations.size(); ++j) {
            cv::Mat c2 = -poses[track.observations[j].camera_idx].R.t() * poses[track.observations[j].camera_idx].t;
            cv::Point3f cam2(c2.at<float>(0, 0), c2.at<float>(1, 0), c2.at<float>(2, 0));
            cv::Point3f ray2 = pt - cam2;
            float norm2 = cv::norm(ray2);
            if (norm2 < 1e-6f) continue;
            ray2 *= (1.0f / norm2);

            float dot = std::max(-1.0f, std::min(1.0f, ray1.dot(ray2)));
            float angle_rad = std::acos(dot);
            float angle_deg = angle_rad * 180.0f / 3.1415926535f;
            if (angle_deg > max_parallax) {
                max_parallax = angle_deg;
            }
        }
    }

    return max_parallax;
}

float Triangulator::computeReprojectionError(const Track& track, const std::vector<CameraPose>& poses) {
    if (track.observations.empty()) return 999.0f;

    float total_err = 0.0f;
    cv::Mat pt3d_mat = (cv::Mat_<float>(3, 1) << track.pt3d.x, track.pt3d.y, track.pt3d.z);

    for (const auto& obs : track.observations) {
        const auto& pose = poses[obs.camera_idx];
        cv::Mat pt_cam = pose.R * pt3d_mat + pose.t;
        cv::Mat pt_proj = pose.K * pt_cam;

        float z = pt_proj.at<float>(2, 0);
        if (std::abs(z) < 1e-6f) return 999.0f;

        float u_proj = pt_proj.at<float>(0, 0) / z;
        float v_proj = pt_proj.at<float>(1, 0) / z;

        float du = u_proj - obs.pt2d.x;
        float dv = v_proj - obs.pt2d.y;
        total_err += std::sqrt(du * du + dv * dv);
    }

    return total_err / static_cast<float>(track.observations.size());
}

bool Triangulator::refinePointLM(
    const std::vector<TrackObservation>& observations,
    const std::vector<CameraPose>& poses,
    cv::Point3f& pt3d,
    float max_reproj_err,
    int max_iterations) {

    if (observations.size() < 2) return false;

    cv::Vec3d X(pt3d.x, pt3d.y, pt3d.z);
    const double delta_huber = 1.0;

    for (int iter = 0; iter < max_iterations; ++iter) {
        cv::Matx33d H = cv::Matx33d::zeros();
        cv::Vec3d g(0.0, 0.0, 0.0);
        int valid_obs = 0;

        for (const auto& obs : observations) {
            if (obs.camera_idx >= poses.size()) continue;
            const auto& pose = poses[obs.camera_idx];

            double r00 = pose.R.at<float>(0, 0), r01 = pose.R.at<float>(0, 1), r02 = pose.R.at<float>(0, 2);
            double r10 = pose.R.at<float>(1, 0), r11 = pose.R.at<float>(1, 1), r12 = pose.R.at<float>(1, 2);
            double r20 = pose.R.at<float>(2, 0), r21 = pose.R.at<float>(2, 1), r22 = pose.R.at<float>(2, 2);

            double tx = pose.t.at<float>(0, 0);
            double ty = pose.t.at<float>(1, 0);
            double tz = pose.t.at<float>(2, 0);

            double Xc = r00 * X[0] + r01 * X[1] + r02 * X[2] + tx;
            double Yc = r10 * X[0] + r11 * X[1] + r12 * X[2] + ty;
            double Zc = r20 * X[0] + r21 * X[1] + r22 * X[2] + tz;

            if (Zc <= 0.01) continue;

            double fx = pose.K.at<float>(0, 0);
            double fy = pose.K.at<float>(1, 1);
            double cx = pose.K.at<float>(0, 2);
            double cy = pose.K.at<float>(1, 2);

            double inv_z = 1.0 / Zc;
            double inv_z2 = inv_z * inv_z;

            double u_proj = fx * Xc * inv_z + cx;
            double v_proj = fy * Yc * inv_z + cy;

            double ru = u_proj - obs.pt2d.x;
            double rv = v_proj - obs.pt2d.y;
            double err = std::sqrt(ru * ru + rv * rv);
            valid_obs++;

            double w = (err <= delta_huber) ? 1.0 : (delta_huber / err);

            double j00 = fx * inv_z;
            double j02 = -fx * Xc * inv_z2;
            double j11 = fy * inv_z;
            double j12 = -fy * Yc * inv_z2;

            double J00 = j00 * r00 + j02 * r20;
            double J01 = j00 * r01 + j02 * r21;
            double J02 = j00 * r02 + j02 * r22;
            double J10 = j11 * r10 + j12 * r20;
            double J11 = j11 * r11 + j12 * r21;
            double J12 = j11 * r12 + j12 * r22;

            H(0, 0) += w * (J00 * J00 + J10 * J10);
            H(0, 1) += w * (J00 * J01 + J10 * J11);
            H(0, 2) += w * (J00 * J02 + J10 * J12);

            H(1, 0) += w * (J01 * J00 + J11 * J10);
            H(1, 1) += w * (J01 * J01 + J11 * J11);
            H(1, 2) += w * (J01 * J02 + J11 * J12);

            H(2, 0) += w * (J02 * J00 + J12 * J10);
            H(2, 1) += w * (J02 * J01 + J12 * J11);
            H(2, 2) += w * (J02 * J02 + J12 * J12);

            g[0] += w * (J00 * ru + J10 * rv);
            g[1] += w * (J01 * ru + J11 * rv);
            g[2] += w * (J02 * ru + J12 * rv);
        }

        if (valid_obs < 2) return false;

        H(0, 0) += 1e-3;
        H(1, 1) += 1e-3;
        H(2, 2) += 1e-3;

        cv::Vec3d dX;
        if (!cv::solve(H, -g, dX, cv::DECOMP_CHOLESKY)) {
            if (!cv::solve(H, -g, dX, cv::DECOMP_SVD)) {
                break;
            }
        }

        X += dX;
        if (dX.dot(dX) < 1e-10) break;
    }

    // Final verification: cheirality and reprojection error
    double final_total_err = 0.0;
    for (const auto& obs : observations) {
        if (obs.camera_idx >= poses.size()) return false;
        const auto& pose = poses[obs.camera_idx];

        double r00 = pose.R.at<float>(0, 0), r01 = pose.R.at<float>(0, 1), r02 = pose.R.at<float>(0, 2);
        double r10 = pose.R.at<float>(1, 0), r11 = pose.R.at<float>(1, 1), r12 = pose.R.at<float>(1, 2);
        double r20 = pose.R.at<float>(2, 0), r21 = pose.R.at<float>(2, 1), r22 = pose.R.at<float>(2, 2);

        double tx = pose.t.at<float>(0, 0);
        double ty = pose.t.at<float>(1, 0);
        double tz = pose.t.at<float>(2, 0);

        double Xc = r00 * X[0] + r01 * X[1] + r02 * X[2] + tx;
        double Yc = r10 * X[0] + r11 * X[1] + r12 * X[2] + ty;
        double Zc = r20 * X[0] + r21 * X[1] + r22 * X[2] + tz;

        if (Zc <= 0.02) return false; // Minimum positive depth 2cm

        double fx = pose.K.at<float>(0, 0);
        double fy = pose.K.at<float>(1, 1);
        double cx = pose.K.at<float>(0, 2);
        double cy = pose.K.at<float>(1, 2);

        double u_proj = fx * Xc / Zc + cx;
        double v_proj = fy * Yc / Zc + cy;
        double du = u_proj - obs.pt2d.x;
        double dv = v_proj - obs.pt2d.y;
        final_total_err += std::sqrt(du * du + dv * dv);
    }

    double mean_err = final_total_err / observations.size();
    if (mean_err > max_reproj_err) return false;

    pt3d.x = static_cast<float>(X[0]);
    pt3d.y = static_cast<float>(X[1]);
    pt3d.z = static_cast<float>(X[2]);
    return true;
}

