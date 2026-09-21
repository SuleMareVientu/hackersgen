import os
import shutil
import sys
from pathlib import Path

# Base workspace directory
BASE_DIR = Path(__file__).resolve().parent.parent

# Storage folders
UPLOAD_DIR = BASE_DIR / "data" / "uploads"
JOBS_DIR = BASE_DIR / "data" / "jobs"

# Ensure dirs exist
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
JOBS_DIR.mkdir(parents=True, exist_ok=True)

# Helper to locate binaries on the system path
def find_binary(name: str) -> str:
    # Check local bin directory first
    local_path = BASE_DIR / "bin" / (f"{name}.exe" if sys.platform.startswith("win") else name)
    if local_path.exists():
        return str(local_path)

    # Check local conda bin directory
    conda_path = BASE_DIR / "conda" / "bin" / (f"{name}.exe" if sys.platform.startswith("win") else name)
    if conda_path.exists():
        return str(conda_path)

    # On Windows, check for .exe
    if sys.platform.startswith("win"):
        exe_name = f"{name}.exe"
        path = shutil.which(exe_name)
        if path:
            return path
        # Check standard installation dirs for COLMAP on Windows
        if name == "colmap":
            possible_paths = [
                r"C:\Program Files\COLMAP\colmap.exe",
                r"C:\colmap\colmap.exe",
            ]
            for p in possible_paths:
                if os.path.exists(p):
                    return p
    else:
        path = shutil.which(name)
        if path:
            return path
    
    # Return name as fallback, relying on it being in system path or working dir
    return name

OPENSPLAT_BINARY = os.getenv("OPENSPLAT_PATH", find_binary("opensplat"))
COLMAP_BINARY = os.getenv("COLMAP_PATH", find_binary("colmap"))
BRUSH_BINARY = os.getenv("BRUSH_PATH", find_binary("brush"))

# Global App Settings
HOST = "0.0.0.0"
PORT = 8000
API_URL = f"http://0.0.0.0:{PORT}"

# Supported output model format options
SUPPORTED_FORMATS = [".ply", ".splat"]
