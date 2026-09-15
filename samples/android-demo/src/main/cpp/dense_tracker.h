#pragma once

#include <vector>
#include <opencv2/opencv.hpp>
#include "match_culling.h"
#include "triangulator.h"

struct DenseTrack {
    std::vector<TrackObservation> observations;
    cv::Point3f pt3d;
    bool valid = false;
};

class DenseTracker {
public:
    DenseTracker();
    ~DenseTracker();

    // Track dense candidates across consecutive frames and triangulate when parallax >= min_parallax_deg (§3.7)
    std::vector<DenseTrack> trackAndTriangulate(
        const std::vector<cv::Mat>& images,
        const std::vector<CameraPose>& poses,
        float min_parallax_deg = 1.2f,
        int grid_step = 3,
        int window_size = 8);

private:
    cv::Mat computeFundamentalMatrix(const CameraPose& pose_a, const CameraPose& pose_b);
    cv::Vec3f computeEpipolarLine(const cv::Point2f& pt, const cv::Mat& F);
    cv::Point2f projectToEpipolarLine(const cv::Point2f& pt, const cv::Vec3f& line);
    bool triangulateDenseTrack(
        DenseTrack& track, 
        const std::vector<CameraPose>& poses, 
        const std::vector<cv::Mat>& cam_centers,
        float min_parallax_deg);
};
