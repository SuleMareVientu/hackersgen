#pragma once

#include <vector>
#include <opencv2/opencv.hpp>
#include "match_culling.h" // For CameraPose

struct TrackObservation {
    int camera_idx;
    cv::Point2f pt2d;
};

struct Track {
    std::vector<TrackObservation> observations;
    cv::Point3f pt3d;
    bool valid;
};

class Triangulator {
public:
    Triangulator();
    ~Triangulator();

    // Triangulate a single track and apply geometric gates
    bool triangulateTrack(
        Track& track, 
        const std::vector<CameraPose>& poses);

    // Non-linear Levenberg-Marquardt refinement with Huber loss
    static bool refinePointLM(
        const std::vector<TrackObservation>& observations,
        const std::vector<CameraPose>& poses,
        cv::Point3f& pt3d,
        float max_reproj_err = 1.2f,
        int max_iterations = 10);

private:
    float computeParallax(const Track& track, const std::vector<CameraPose>& poses);
    float computeReprojectionError(const Track& track, const std::vector<CameraPose>& poses);

    float min_parallax_deg_ = 1.2f;
    float max_reproj_error_ = 1.2f; // pixels
};
