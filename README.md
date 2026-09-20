# OpenSplat Android

> **Real-time 3D Gaussian Splatting and AR for Android.** Built with Jetpack Compose, Google Filament, and ARCore.

[![Android 3D](https://img.shields.io/badge/Android%203D-Filament-34a853?logo=android)](https://github.com/google/filament)
[![Android AR](https://img.shields.io/badge/Android%20AR-ARCore-4285f4?logo=google)](https://developers.google.com/ar)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7f52ff?logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## Overview

OpenSplat Android is an open-source Android SDK and showcase application for real-time 3D Gaussian Splatting (3DGS) and Augmented Reality. It combines Google's Filament PBR rendering engine with ARCore tracking and an on-device optimization pipeline powered by Google LiteRT and Ceres Solver.

### Key Capabilities

- **Real-time Gaussian Splatting**: Fast rendering and interaction with 3D Gaussian Splat point clouds.
- **On-Device Training Pipeline**: Camera pose estimation and splat optimization directly on the device using LiteRT and Ceres Solver.
- **Augmented Reality Integration**: Place, lock, and view 3D splats in real-world environments using ARCore.
- **Interactive Editing & Export**: Bounding-box cropping, brush tools, and PLY/SPZ export capabilities.
- **Android 15+ 16 KB Page Alignment**: Full compliance with Google Play 16 KB ELF segment alignment requirements.

---

## Repository Structure

```
├── sceneview-core/       # Pure Kotlin math, collision detection, and triangulation
├── sceneview/            # 3D Jetpack Compose runtime powered by Google Filament
├── arsceneview/          # ARCore integration for Jetpack Compose
└── samples/
    ├── common/           # Shared UI helpers, gestures, and permission utilities
    └── android-demo/     # OpenSplat showcase application & training pipeline
```

---

## Getting Started

### Prerequisites

- Android Studio Ladybug (or newer)
- Android SDK 36 (minSdk 24)
- JDK 21
- Android NDK & CMake (for LiteRT and Ceres JNI builds)

### Build Commands

```bash
# Build Debug APK
./gradlew :samples:android-demo:assembleDebug

# Run Unit Tests
./gradlew :samples:android-demo:testDebugUnitTest

# Build Release APK
./gradlew :samples:android-demo:assembleRelease
```

---

## Quick Look

### Compose 3D Scene

```kotlin
@Composable
fun SplatViewerScreen() {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    SceneView(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        cameraManipulator = rememberCameraManipulator()
    ) {
        // Render 3D Gaussian Splats or glTF/GLB models
    }
}
```

### Compose AR Scene

```kotlin
@Composable
fun SplatARScreen() {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    ARSceneView(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        planeRenderer = true
    ) {
        // Place splat nodes anchored in the physical world
    }
}
```

---

## License

This project is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
