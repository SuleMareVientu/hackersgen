#pragma once

#include <vector>
#include <opencv2/opencv.hpp>
#include <ceres/ceres.h>
#include "match_culling.h"
#include "triangulator.h"

class BundleAdjuster {
public:
    BundleAdjuster();
    ~BundleAdjuster();

    // Perform Bundle Adjustment on poses and tracks.
    // If optimize_cameras is false (default), camera poses are fixed as ARCore ground truth,
    // and only 3D point positions are refined.
    bool optimize(
        std::vector<CameraPose>& poses,
        std::vector<Track>& tracks,
        int reference_cam_idx = 0,
        const std::vector<float>& confidences = {},
        int max_time_seconds = 20,
        bool optimize_cameras = false);
};
