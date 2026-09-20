# OpenSplat Android Demo

Showcase application demonstrating 3D Gaussian Splatting capabilities on Android.

## Features

- **Splat Viewer**: Real-time rendering and interactive manipulation of 3D Gaussian Splats.
- **Splat Training**: On-device point cloud reconstruction and training powered by LiteRT and Ceres Solver.
- **AR Splat Capture**: Augmented Reality capture and placement powered by ARCore.
- Material 3 Expressive UI with Dark mode support.
- PLY and SPZ import and export.

## Run

```bash
./gradlew :samples:android-demo:assembleDebug
```

Install the APK on a connected device:

```bash
adb install -r samples/android-demo/build/outputs/apk/debug/android-demo-debug.apk
```

…or, with Google's [`android` CLI](https://developer.android.com/tools/agents/android-cli):

```bash
android run \
  --apks=samples/android-demo/build/outputs/apk/debug/android-demo-debug.apk \
  --activity=io.github.sceneview.demo/.MainActivity
```

## Requirements

- Android device (API 31+)
- For AR features: ARCore-compatible device
