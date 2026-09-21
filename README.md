# OpenSplat Server & GUI

A cross-platform (Linux & Windows) desktop application featuring a **FastAPI backend server** and a **native Material 3 Flutter dashboard GUI** for managing 3D Gaussian Splatting workflows with OpenSplat.

## Features

- **Video Upload**: Extract video frames using OpenCV and reconstruct sparse camera poses with COLMAP.
- **Nerfstudio Dataset Upload**: Accept Nerfstudio format `.zip` datasets and auto-validate their structure (`transforms.json`, `images/`).
- **Asynchronous Training Queue**: Trigger, monitor, and abort training jobs without freezing the UI.
- **Hardware Diagnostics**: Real-time stats showing CPU, RAM, Disk, and NVML GPU memory/temperatures/loads.
- **Model Exporter**: Download final trained `.ply` Gaussian model files directly.

---

## System Requirements

1. **Python 3.9 - 3.12**
2. **OpenSplat**: Follow the build instructions at [pierotofy/opensplat](https://github.com/pierotofy/opensplat) to compile the binary for your platform (Linux/Windows/macOS).
3. **COLMAP** (Optional): Required for processing `.mp4` video uploads. Download/install from the [COLMAP Releases](https://github.com/colmap/colmap/releases).

---

## Installation

1. **Clone the repository and navigate inside**:
   ```bash
   git clone <repository_url> OpenSplatServer
   cd OpenSplatServer
   ```

2. **Create a virtual environment and activate it**:
   - **Linux**:
     ```bash
     python3 -m venv venv
     source venv/bin/activate
     ```
   - **Windows**:
     ```cmd
     python -m venv venv
     venv\Scripts\activate
     ```

3. **Install python packages**:
   ```bash
   pip install -r requirements.txt
   ```

---

## Configuration & Environment Variables

The application automatically searches your system path (`PATH`) for the `opensplat` and `colmap` executables. If they are installed in custom locations, set the following environment variables before running:

- **Linux**:
  ```bash
  export OPENSPLAT_PATH="/path/to/bin/opensplat"
  export COLMAP_PATH="/path/to/bin/colmap"
  ```
- **Windows**:
  ```cmd
  set OPENSPLAT_PATH=C:\path\to\opensplat.exe
  set COLMAP_PATH=C:\Program Files\COLMAP\colmap.exe
  ```

---

## Running the Application

Start the application by running the master entrypoint script:
```bash
python main.py
```
This command starts the FastAPI server on `http://0.0.0.0:8000` in a background thread and automatically launches the compiled Material 3 Flutter GUI desktop window (compiling it on the fly if needed).

---

## Packaging as a Single Distributable App

We use PyInstaller to package the code and its dependencies into a standalone executable. 

For detailed packaging parameters and instructions, run the packaging assistant script:
```bash
python build_dist.py
```
This generates a single distributable bundle in the `dist/` directory.
