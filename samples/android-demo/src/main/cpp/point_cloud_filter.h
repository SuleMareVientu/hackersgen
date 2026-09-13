#pragma once

#include <vector>
#include <opencv2/opencv.hpp>
#include "triangulator.h"
#include "dense_tracker.h"
#include "match_culling.h"

class PointCloudFilter {
public:
    PointCloudFilter();
    ~PointCloudFilter();

    // Multi-view consistency filter for Tier 2 dense candidates (§3.8)
    // Enforces corroboration across >= min_views (e.g. >= 2 views) within reprojection tolerance
    std::vector<Track> filterConsistency(
        const std::vector<DenseTrack>& dense_tracks,
        const std::vector<CameraPose>& poses,
        int min_views = 2,
        float max_reproj_err = 2.5f);

    // Merge Tier 1 and Tier 2, apply statistical outlier removal and voxel thinning (§3.9)
    std::vector<Track> fuseAndFilter(
        const std::vector<Track>& tier1_tracks,
        const std::vector<Track>& tier2_tracks,
        float voxel_size_m = 0.0015f,
        int knn_k = 8,
        float std_ratio = 2.5f);
};
