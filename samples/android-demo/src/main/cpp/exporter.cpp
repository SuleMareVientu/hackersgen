#include "exporter.h"
#include <fstream>
#include <android/log.h>
#include <iomanip>
#include <thread>
#include <atomic>
#include <algorithm>

#define LOG_TAG "Exporter"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

Exporter::Exporter() {}
Exporter::~Exporter() {}

cv::Vec3b Exporter::computeTrackColor(const Track& track, const std::vector<cv::Mat>& images) {
    if (track.observations.empty() || images.empty()) {
        return cv::Vec3b(255, 255, 255);
    }
    
    int sum_b = 0, sum_g = 0, sum_r = 0;
    int count = 0;
    
    for (const auto& obs : track.observations) {
        if (obs.camera_idx < images.size()) {
            cv::Vec3b c = sampleColor(images[obs.camera_idx], obs.pt2d);
            sum_b += c[0];
            sum_g += c[1];
            sum_r += c[2];
            count++;
        }
    }
    
    if (count == 0) return cv::Vec3b(255, 255, 255);
    
    return cv::Vec3b(
        static_cast<uchar>(sum_b / count),
        static_cast<uchar>(sum_g / count),
        static_cast<uchar>(sum_r / count)
    );
}

cv::Vec3b Exporter::sampleColor(const cv::Mat& image, const cv::Point2f& pt2d) {
    if (image.empty()) return cv::Vec3b(255, 255, 255);
    
    int x = std::round(pt2d.x);
    int y = std::round(pt2d.y);
    
    x = std::max(0, std::min(x, image.cols - 1));
    y = std::max(0, std::min(y, image.rows - 1));
    
    if (image.channels() == 3) {
        return image.at<cv::Vec3b>(y, x);
    } else if (image.channels() == 4) {
        cv::Vec4b bgra = image.at<cv::Vec4b>(y, x);
        return cv::Vec3b(bgra[0], bgra[1], bgra[2]);
    }
    
    uchar gray = image.at<uchar>(y, x);
    return cv::Vec3b(gray, gray, gray);
}

bool Exporter::exportNerfstudio(
    const std::vector<cv::Mat>& images,
    const std::vector<std::string>& image_names,
    const std::vector<CameraPose>& poses,
    const std::vector<Track>& tracks,
    const std::string& output_dir,
    bool binary) {
    
    if (poses.empty()) return false;

    // 1. Export points.ply (primary for Nerfstudio/Brush)
    std::string ply_filename = "points.ply";
    std::string ply_path = output_dir + "/" + ply_filename;

    // Collect valid tracks
    std::vector<const Track*> valid_tracks;
    valid_tracks.reserve(tracks.size());
    for (const auto& track : tracks) {
        if (track.valid) valid_tracks.push_back(&track);
    }
    int num_valid_tracks = static_cast<int>(valid_tracks.size());

    if (binary) {
        std::ofstream ply_file(ply_path, std::ios::binary);
        if (!ply_file.is_open()) {
            LOGE("Failed to open %s for writing", ply_path.c_str());
            return false;
        }

        std::string header = 
            "ply\n"
            "format binary_little_endian 1.0\n"
            "comment vertical axis: y\n"
            "element vertex " + std::to_string(num_valid_tracks) + "\n"
            "property float x\n"
            "property float y\n"
            "property float z\n"
            "property uchar red\n"
            "property uchar green\n"
            "property uchar blue\n"
            "end_header\n";
        ply_file.write(header.c_str(), header.size());

#pragma pack(push, 1)
        struct VertexBinary {
            float x, y, z;
            uint8_t red, green, blue;
        };
#pragma pack(pop)

        std::vector<VertexBinary> buffer(num_valid_tracks);

        int num_threads = std::max(1u, std::thread::hardware_concurrency());
        std::vector<std::thread> workers;
        std::atomic<size_t> next_idx{0};

        for (int t = 0; t < num_threads; ++t) {
            workers.emplace_back([&]() {
                while (true) {
                    size_t i = next_idx.fetch_add(1);
                    if (i >= valid_tracks.size()) break;

                    const auto* track = valid_tracks[i];
                    cv::Vec3b color = computeTrackColor(*track, images);

                    // 180° rotation around X-axis (y -> -y, z -> -z) to invert upright axis
                    buffer[i].x =  track->pt3d.x;
                    buffer[i].y = -track->pt3d.y;
                    buffer[i].z = -track->pt3d.z;
                    buffer[i].red = static_cast<uint8_t>(color[2]);   // OpenCV BGR -> R
                    buffer[i].green = static_cast<uint8_t>(color[1]); // G
                    buffer[i].blue = static_cast<uint8_t>(color[0]);  // B
                }
            });
        }
        for (auto& w : workers) {
            w.join();
        }

        if (!buffer.empty()) {
            ply_file.write(reinterpret_cast<const char*>(buffer.data()), buffer.size() * sizeof(VertexBinary));
        }
        ply_file.close();
    } else {
        std::ofstream ply_file(ply_path);
        if (!ply_file.is_open()) {
            LOGE("Failed to open %s for writing", ply_path.c_str());
            return false;
        }

        ply_file << "ply\n";
        ply_file << "format ascii 1.0\n";
        ply_file << "comment vertical axis: y\n";
        ply_file << "element vertex " << num_valid_tracks << "\n";
        ply_file << "property float x\n";
        ply_file << "property float y\n";
        ply_file << "property float z\n";
        ply_file << "property uchar red\n";
        ply_file << "property uchar green\n";
        ply_file << "property uchar blue\n";
        ply_file << "end_header\n";

        std::vector<cv::Vec3b> colors(num_valid_tracks);
        int num_threads = std::max(1u, std::thread::hardware_concurrency());
        std::vector<std::thread> workers;
        std::atomic<size_t> next_idx{0};

        for (int t = 0; t < num_threads; ++t) {
            workers.emplace_back([&]() {
                while (true) {
                    size_t i = next_idx.fetch_add(1);
                    if (i >= valid_tracks.size()) break;
                    colors[i] = computeTrackColor(*valid_tracks[i], images);
                }
            });
        }
        for (auto& w : workers) {
            w.join();
        }

        for (size_t i = 0; i < valid_tracks.size(); ++i) {
            const auto* track = valid_tracks[i];
            const auto& color = colors[i];

            // 180° rotation around X-axis (y -> -y, z -> -z)
            ply_file << track->pt3d.x << " " << -track->pt3d.y << " " << -track->pt3d.z << " " 
                     << (int)color[2] << " " << (int)color[1] << " " << (int)color[0] << "\n";
        }
        ply_file.close();
    }

    // 2. Export transforms.json
    std::string json_path = output_dir + "/transforms.json";
    std::ofstream json_file(json_path);
    if (!json_file.is_open()) {
        LOGE("Failed to open %s for writing", json_path.c_str());
        return false;
    }

    json_file << "{\n";
    json_file << "  \"camera_model\": \"PERSPECTIVE\",\n";
    json_file << "  \"ply_file_path\": \"" << ply_filename << "\",\n";
    json_file << "  \"frames\": [\n";

    // 180° rotation around X-axis in world space: y -> -y, z -> -z
    cv::Mat R_flip_world = (cv::Mat_<float>(3, 3) << 
                            1,  0,  0,
                            0, -1,  0,
                            0,  0, -1);

    for (size_t i = 0; i < poses.size(); ++i) {
        std::string filename = (i < image_names.size()) ? image_names[i] : "image_" + std::to_string(i) + ".jpg";
        std::string rel_path = filename;
        if (rel_path.rfind("images/", 0) != 0) {
            rel_path = "images/" + rel_path;
        }
        
        // OpenCV world-to-camera is [R|t]. We need camera-to-world (c2w)
        cv::Mat c2w_R = poses[i].R.t();
        cv::Mat c2w_t = -c2w_R * poses[i].t;
        
        // Flip camera Y and Z to convert to OpenGL camera convention (+X right, +Y up, -Z forward)
        cv::Mat flip = (cv::Mat_<float>(3, 3) << 
                        1, 0, 0,
                        0, -1, 0,
                        0, 0, -1);
        c2w_R = c2w_R * flip;

        // Apply 180° X-axis world rotation to align with the inverted upright axis:
        // c2w_new = R_flip_world * c2w
        c2w_R = R_flip_world * c2w_R;
        c2w_t = R_flip_world * c2w_t;
        
        json_file << "    {\n";
        json_file << "      \"file_path\": \"" << rel_path << "\",\n";
        json_file << "      \"w\": " << poses[i].w << ",\n";
        json_file << "      \"h\": " << poses[i].h << ",\n";
        json_file << "      \"fl_x\": " << poses[i].K.at<float>(0, 0) << ",\n";
        json_file << "      \"fl_y\": " << poses[i].K.at<float>(1, 1) << ",\n";
        json_file << "      \"cx\": " << poses[i].K.at<float>(0, 2) << ",\n";
        json_file << "      \"cy\": " << poses[i].K.at<float>(1, 2) << ",\n";
        json_file << "      \"transform_matrix\": [\n";
        
        for (int r = 0; r < 4; ++r) {
            json_file << "        [";
            for (int c = 0; c < 4; ++c) {
                float val = 0.0f;
                if (r < 3 && c < 3) val = c2w_R.at<float>(r, c);
                else if (r < 3 && c == 3) val = c2w_t.at<float>(r, 0);
                else if (r == 3 && c == 3) val = 1.0f;
                
                json_file << std::fixed << std::setprecision(6) << val;
                if (c < 3) json_file << ", ";
            }
            json_file << "]";
            if (r < 3) json_file << ",\n";
            else json_file << "\n";
        }
        
        json_file << "      ]\n";
        json_file << "    }" << (i < poses.size() - 1 ? "," : "") << "\n";
    }

    json_file << "  ]\n";
    json_file << "}\n";
    json_file.close();

    LOGI("Exported Nerfstudio dataset to %s", output_dir.c_str());
    return true;
}
