# OpenSplat Android

> **Open-Source 3D Gaussian Splatting for Mobile.** The first-ever end-to-end 3D Gaussian Splatting studio running directly on Android devices.

[![Android 3D](https://img.shields.io/badge/Android%203D-Filament-34a853?logo=android)](https://github.com/google/filament)
[![Android AR](https://img.shields.io/badge/Android%20AR-ARCore-4285f4?logo=google)](https://developers.google.com/ar)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7f52ff?logo=kotlin)](https://kotlinlang.org)
[![C++17](https://img.shields.io/badge/C%2B%2B-17-00599C?logo=c%2B%2B)](https://isocpp.org)
[![Rust](https://img.shields.io/badge/Rust-Engine-dea584?logo=rust)](https://www.rust-lang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## The Vision: Democratizing 3D Gaussian Splatting

For years, high-fidelity 3D Gaussian Splatting (3DGS) has been locked behind expensive desktop workstations, power-hungry GPUs, and complex terminal workflows.

**OpenSplat Android** changes that. By combining state-of-the-art mobile computer vision, deep learning, and hardware-accelerated rasterization, it delivers the **first-ever implementation of complete Gaussian Splat training running locally on Android devices**—from camera capture to finished 3D scene, right in your pocket.

---

## Key Features

* **First-Ever Local On-Device Training**: Run full Gaussian Splatting backpropagation and rasterization directly on mobile GPUs powered by [brush-android](https://github.com/SuleMareVientu/brush-android) (Brush fork with Android AAR support).
* **On-Device SfM & AI Feature Matching**: Real-time pose estimation and Bundle Adjustment powered by **Google LiteRT**, **XFeat** deep features, and **Ceres Solver**.
* **AR-Guided Capture**: Guided capture experience backed by **Google ARCore** with real-time pose tracking and point cloud reconstruction.
* **Hybrid Training Pipeline (Local + LAN Server)**:
  * **On-Device**: Train directly on your phone with zero network connection.
  * **LAN Desktop Offload**: Seamlessly stream video or captured datasets to a self-hosted desktop GPU server over local Wi-Fi for workstation-scale training.
* **Real-time 3D & AR Viewing** *(In Progress)*: Inspect, transform, crop, and place models in physical space using **Google Filament** and ARCore.
* **Android 15 Ready**: Fully compliant with Google Play 16 KB ELF segment page alignment.

> [!WARNING]
> **Work In Progress**: Full real-time 3D and AR viewing of Gaussian Splat models (`.ply` / `.splat`) is actively in development and not yet fully implemented in the current build. Complete interactive splat rendering and AR placement features will be released in an upcoming update.

---

## Architecture & Technology Stack

| Layer | Technologies & Purpose |
| :--- | :--- |
| **UI & App Lifecycle** | **Kotlin 2.x**, **Jetpack Compose** (Material 3 Expressive) |
| **3D Rendering & AR** | **Google Filament** (PBR graphics engine), **ARCore** (6DoF tracking, spatial anchors) |
| **Native CV & SfM** | **C++17** (NDK), **Google LiteRT** (XFeat neural feature extraction), **Ceres Solver** (Bundle Adjustment), **OpenCV-Mobile**, **Eigen 3**, **ARM NEON** SIMD |
| **On-Device Training** | [**Brush Engine**](https://github.com/SuleMareVientu/brush-android) (Native **Rust** + **Tokio** + GPU Vulkan compute; fork with Android AAR support) |
| **Remote Server Client** | **OkHttp 4** REST API client for self-hosted LAN desktop servers with real-time hardware telemetry and chunked streaming |
| **Data & Formats** | **Kotlinx Serialization JSON**, Standard `.ply`, `.splat`, and Nerfstudio dataset formats |

---

## Repository Structure

```
├── sceneview-core/       # Pure Kotlin 3D math, raycasting, and geometry utils
├── sceneview/            # 3D Jetpack Compose runtime powered by Google Filament
├── arsceneview/          # ARCore integration for Jetpack Compose
└── samples/
    ├── common/           # Shared UI helpers, gestures, and permission utilities
    └── android-demo/     # Complete OpenSplat showcase app, SfM & training pipelines
```

---

## Getting Started

### Prerequisites

* Android Studio Ladybug (or newer)
* Android SDK 36 (minSdk 31, targetSdk 36)
* JDK 21
* Android NDK & CMake 3.22+

### Build Commands

```bash
# Build Debug APK
./gradlew :samples:android-demo:assembleDebug

# Run Unit Tests
./gradlew :samples:android-demo:testDebugUnitTest

# Build Release APK (with R8 minification & 16KB alignment)
./gradlew :samples:android-demo:assembleRelease
```

---

## License

This project is open source and licensed under the [Apache License 2.0](LICENSE).

