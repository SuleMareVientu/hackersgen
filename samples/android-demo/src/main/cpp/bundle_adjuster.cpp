#include "bundle_adjuster.h"
#include <ceres/rotation.h>
#include <android/log.h>

#define LOG_TAG "BundleAdjuster"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

struct SfmReprojectionError {
    SfmReprojectionError(double observed_x, double observed_y, double fx, double fy, double cx, double cy)
        : observed_x(observed_x), observed_y(observed_y), fx(fx), fy(fy), cx(cx), cy(cy) {}

    template <typename T>
    bool operator()(const T* const camera_r,
                    const T* const camera_t,
                    const T* const point,
                    T* residuals) const {
        T p[3];
        ceres::AngleAxisRotatePoint(camera_r, point, p);
        p[0] += camera_t[0];
        p[1] += camera_t[1];
        p[2] += camera_t[2];

        T xp = p[0] / p[2];
        T yp = p[1] / p[2];

        T predicted_x = T(fx) * xp + T(cx);
        T predicted_y = T(fy) * yp + T(cy);

        residuals[0] = predicted_x - T(observed_x);
        residuals[1] = predicted_y - T(observed_y);

        return true;
    }

    static ceres::CostFunction* Create(double observed_x, double observed_y,
                                       double fx, double fy, double cx, double cy) {
        return (new ceres::AutoDiffCostFunction<SfmReprojectionError, 2, 3, 3, 3>(
            new SfmReprojectionError(observed_x, observed_y, fx, fy, cx, cy)));
    }

    double observed_x;
    double observed_y;
    double fx, fy, cx, cy;
};

// Soft prior pulling poses towards original ARCore metric poses (§3.5)
struct PosePriorError {
    PosePriorError(const double* initial_r, const double* initial_t, double weight_r, double weight_t)
        : weight_r_(weight_r), weight_t_(weight_t) {
        r_init_[0] = initial_r[0]; r_init_[1] = initial_r[1]; r_init_[2] = initial_r[2];
        t_init_[0] = initial_t[0]; t_init_[1] = initial_t[1]; t_init_[2] = initial_t[2];
    }

    template <typename T>
    bool operator()(const T* const r, const T* const t, T* residuals) const {
        residuals[0] = T(weight_r_) * (r[0] - T(r_init_[0]));
        residuals[1] = T(weight_r_) * (r[1] - T(r_init_[1]));
        residuals[2] = T(weight_r_) * (r[2] - T(r_init_[2]));
        residuals[3] = T(weight_t_) * (t[0] - T(t_init_[0]));
        residuals[4] = T(weight_t_) * (t[1] - T(t_init_[1]));
        residuals[5] = T(weight_t_) * (t[2] - T(t_init_[2]));
        return true;
    }

    static ceres::CostFunction* Create(const double* initial_r, const double* initial_t, double weight_r, double weight_t) {
        return (new ceres::AutoDiffCostFunction<PosePriorError, 6, 3, 3>(
            new PosePriorError(initial_r, initial_t, weight_r, weight_t)));
    }

    double r_init_[3];
    double t_init_[3];
    double weight_r_;
    double weight_t_;
};

BundleAdjuster::BundleAdjuster() {}
BundleAdjuster::~BundleAdjuster() {}

bool BundleAdjuster::optimize(
    std::vector<CameraPose>& poses,
    std::vector<Track>& tracks,
    int reference_cam_idx,
    const std::vector<float>& confidences,
    int max_time_seconds,
    bool optimize_cameras) {

    if (poses.empty() || tracks.empty()) return false;

    ceres::Problem problem;

    std::vector<std::vector<double>> camera_r(poses.size(), std::vector<double>(3));
    std::vector<std::vector<double>> camera_t(poses.size(), std::vector<double>(3));

    for (size_t i = 0; i < poses.size(); ++i) {
        double R_arr[9];
        for (int r = 0; r < 3; ++r) {
            for (int c = 0; c < 3; ++c) {
                // Ceres expects COLUMN-MAJOR rotation matrix: R_arr[c * 3 + r]
                R_arr[c * 3 + r] = static_cast<double>(poses[i].R.at<float>(r, c));
            }
        }
        ceres::RotationMatrixToAngleAxis(R_arr, camera_r[i].data());
        camera_t[i][0] = static_cast<double>(poses[i].t.at<float>(0, 0));
        camera_t[i][1] = static_cast<double>(poses[i].t.at<float>(1, 0));
        camera_t[i][2] = static_cast<double>(poses[i].t.at<float>(2, 0));
    }

    auto initial_r = camera_r;
    auto initial_t = camera_t;

    std::vector<std::vector<double>> points_3d(tracks.size(), std::vector<double>(3));
    for (size_t i = 0; i < tracks.size(); ++i) {
        points_3d[i][0] = static_cast<double>(tracks[i].pt3d.x);
        points_3d[i][1] = static_cast<double>(tracks[i].pt3d.y);
        points_3d[i][2] = static_cast<double>(tracks[i].pt3d.z);
    }

    if (!optimize_cameras) {
        // Point-only BA: fix all camera poses to preserve exact ARCore metric poses
        for (size_t i = 0; i < poses.size(); ++i) {
            problem.AddParameterBlock(camera_r[i].data(), 3);
            problem.AddParameterBlock(camera_t[i].data(), 3);
            problem.SetParameterBlockConstant(camera_r[i].data());
            problem.SetParameterBlockConstant(camera_t[i].data());
        }
    } else {
        // Hard gauge anchor on the high-confidence reference frame (§3.5)
        int ref_idx = std::max(0, std::min(reference_cam_idx, static_cast<int>(poses.size() - 1)));
        problem.AddParameterBlock(camera_r[ref_idx].data(), 3);
        problem.AddParameterBlock(camera_t[ref_idx].data(), 3);
        problem.SetParameterBlockConstant(camera_r[ref_idx].data());
        problem.SetParameterBlockConstant(camera_t[ref_idx].data());

        // Add strong ARCore pose priors to all other cameras to maintain metric IMU scale
        for (size_t i = 0; i < poses.size(); ++i) {
            if (static_cast<int>(i) == ref_idx) continue;

            float conf = 1.0f;
            if (i < confidences.size()) {
                conf = std::max(0.1f, std::min(2.0f, confidences[i]));
            }

            double weight_r = 100.0 * conf;
            double weight_t = 100.0 * conf;

            ceres::CostFunction* prior_cost = PosePriorError::Create(
                initial_r[i].data(), initial_t[i].data(), weight_r, weight_t);
            problem.AddResidualBlock(prior_cost, nullptr, camera_r[i].data(), camera_t[i].data());
        }
    }

    int num_residuals = 0;
    for (size_t i = 0; i < tracks.size(); ++i) {
        if (!tracks[i].valid) continue;

        for (const auto& obs : tracks[i].observations) {
            if (obs.camera_idx >= poses.size()) continue;

            const auto& pose = poses[obs.camera_idx];
            double fx = pose.K.at<float>(0, 0);
            double fy = pose.K.at<float>(1, 1);
            double cx = pose.K.at<float>(0, 2);
            double cy = pose.K.at<float>(1, 2);

            ceres::CostFunction* cost_function = SfmReprojectionError::Create(
                obs.pt2d.x, obs.pt2d.y, fx, fy, cx, cy);

            ceres::LossFunction* loss_function = new ceres::HuberLoss(1.0);

            problem.AddResidualBlock(
                cost_function,
                loss_function,
                camera_r[obs.camera_idx].data(),
                camera_t[obs.camera_idx].data(),
                points_3d[i].data());
            num_residuals++;
        }
    }

    if (num_residuals == 0) return false;

    ceres::Solver::Options options;
    options.linear_solver_type = ceres::DENSE_SCHUR;
    options.max_num_iterations = 100;
    options.max_solver_time_in_seconds = static_cast<double>(max_time_seconds);
    options.minimizer_progress_to_stdout = false;

    ceres::Solver::Summary summary;
    ceres::Solve(options, &problem, &summary);

    LOGI("Bundle Adjustment finished: %s", summary.BriefReport().c_str());

    // Update optimized poses only if camera optimization was enabled
    if (optimize_cameras) {
        for (size_t i = 0; i < poses.size(); ++i) {
            double R_arr[9];
            ceres::AngleAxisToRotationMatrix(camera_r[i].data(), R_arr);
            for (int r = 0; r < 3; ++r) {
                for (int c = 0; c < 3; ++c) {
                    // Ceres returns COLUMN-MAJOR rotation matrix: R_arr[c * 3 + r]
                    poses[i].R.at<float>(r, c) = static_cast<float>(R_arr[c * 3 + r]);
                }
            }
            poses[i].t.at<float>(0, 0) = static_cast<float>(camera_t[i][0]);
            poses[i].t.at<float>(1, 0) = static_cast<float>(camera_t[i][1]);
            poses[i].t.at<float>(2, 0) = static_cast<float>(camera_t[i][2]);
        }
    }

    // Update optimized 3D points
    for (size_t i = 0; i < tracks.size(); ++i) {
        if (!tracks[i].valid) continue;
        tracks[i].pt3d.x = static_cast<float>(points_3d[i][0]);
        tracks[i].pt3d.y = static_cast<float>(points_3d[i][1]);
        tracks[i].pt3d.z = static_cast<float>(points_3d[i][2]);
    }

    return true;
}
