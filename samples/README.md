# OpenSplat Android Samples

This directory contains the showcase application and shared utilities for 3D Gaussian Splatting and AR on Android.

## Modules

- **[`android-demo`](android-demo/)**: The full showcase Android application featuring:
  - Real-time 3D Gaussian Splat viewing and manipulation
  - On-device Splat training and reconstruction (LiteRT + Ceres Solver)
  - Augmented Reality (ARCore) integration for placing splats in real-world environments
  - Interactive brush, bounding box segmentation, and transform tools
  - PLY/SPZ export capabilities
- **[`common`](common/)**: Reusable UI components, lifecycle helpers, permission handlers, and Compose utilities.

## Building and Running

### Build Debug APK
```bash
./gradlew :samples:android-demo:assembleDebug
```

### Run Unit Tests
```bash
./gradlew :samples:android-demo:testDebugUnitTest
```

### Build Release APK
```bash
./gradlew :samples:android-demo:assembleRelease
```
