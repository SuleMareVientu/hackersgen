# Contributing to OpenSplat Android

Thanks for your interest in contributing to OpenSplat Android! This guide outlines how to set up your environment, build the project, and submit changes.

---

## Development Environment Setup

### Prerequisites

- **JDK 21**
- **Android Studio Ladybug** (or newer)
- **Android SDK 36** (minSdk 24)
- **Android NDK & CMake** (installed via Android Studio SDK Manager for native C++ LiteRT and Ceres modules)

### Clone & Open

```bash
git clone https://github.com/SuleMareVientu/opensplat-android.git
cd opensplat-android
```

Open the project folder in Android Studio. Gradle will sync dependencies automatically.

---

## Building and Testing

### Build APKs

```bash
# Build libraries and debug demo app
./gradlew assembleDebug

# Build release APK
./gradlew :samples:android-demo:assembleRelease
```

### Run Tests

```bash
# Run unit tests across all modules
./gradlew test

# Run demo app unit tests
./gradlew :samples:android-demo:testDebugUnitTest
```

### Code Quality & Linting

```bash
# Run Android Lint
./gradlew :sceneview:lintDebug :arsceneview:lintDebug

# Run Detekt
./gradlew detekt
```

---

## Development Guidelines

1. **Threading & Filament**:
   - All Filament JNI calls and rendering operations must execute on the Android Main thread.
2. **Architecture**:
   - `:sceneview-core`: Pure Kotlin math, geometry, and collision logic.
   - `:sceneview`: Jetpack Compose wrapper for Google Filament PBR rendering.
   - `:arsceneview`: ARCore tracking and physical world anchoring for Compose.
   - `:samples:android-demo`: OpenSplat viewer, trainer (LiteRT + Ceres Solver), and AR application.
3. **Surgical Changes**:
   - Keep pull requests focused on a single feature, bugfix, or improvement. Avoid broad refactorings or cosmetic churn.

---

## Submitting Pull Requests

1. Create a feature branch from `main`.
2. Ensure your changes compile and pass unit tests:
   ```bash
   ./gradlew :samples:android-demo:assembleDebug :samples:android-demo:testDebugUnitTest
   ```
3. Open a Pull Request on GitHub describing what your changes do and how they were tested.
