import os
import sys
import subprocess
import shutil
from pathlib import Path

def build_executable():
    print("=== OpenSplat GUI/Server Distributable Packager ===")
    
    base_dir = Path(__file__).resolve().parent
    
    # 1. Build the Flutter Release App First
    print("\nBuilding Flutter GUI Application...")
    flutter_bin_path = os.path.expanduser("~/develop/flutter/bin/flutter")
    if not os.path.exists(flutter_bin_path):
        flutter_bin_path = "flutter"
        
    flutter_dir = base_dir / "gui_flutter"
    is_windows = sys.platform.startswith("win")
    platform_target = "windows" if is_windows else "linux"
    
    try:
        subprocess.run([flutter_bin_path, "build", platform_target, "--release"], cwd=flutter_dir, check=True)
        print("Flutter GUI build complete.")
    except Exception as e:
        print(f"ERROR: Flutter compilation failed: {e}")
        sys.exit(1)

    # Ensure PyInstaller is installed
    try:
        import PyInstaller
    except ImportError:
        print("PyInstaller is not installed in the current environment.")
        print("Installing pyinstaller via pip...")
        subprocess.run([sys.executable, "-m", "pip", "install", "pyinstaller"], check=True)
        
    main_script = base_dir / "main.py"
    
    # Resolve full path to pyinstaller binary if in same directory as python
    pyinstaller_bin = os.path.join(os.path.dirname(sys.executable), "pyinstaller" + (".exe" if sys.platform.startswith("win") else ""))
    if not os.path.exists(pyinstaller_bin):
        pyinstaller_bin = "pyinstaller"
        
    # Define PyInstaller arguments
    # --onefile: Bundles everything into a single binary executable
    # --noconsole: Suppresses command prompt windows (perfect for desktop users)
    # --name: Specifies the executable name
    args = [
        pyinstaller_bin,
        "--onefile",
        "--noconsole",
        "--name=OpenSplatManager",
        f"--workpath={base_dir / 'build'}",
        f"--distpath={base_dir / 'dist'}",
        "--clean",
    ]
    
    # Add hidden imports that FastAPI/Uvicorn require
    hidden_imports = [
        "uvicorn.protocols.http.h11_impl",
        "uvicorn.protocols.http.flow_control",
        "uvicorn.protocols.websockets.wsproto_impl",
        "uvicorn.lifespan.on",
        "uvicorn.middleware.proxy_headers",
        "fastapi.middleware.cors",
        "email_validator",
        "pynvml",
        "psutil",
        "cv2",
    ]
    
    for imp in hidden_imports:
        args.extend(["--hidden-import", imp])
        
    # Determine Flutter build folder to package
    sep = ";" if is_windows else ":"
    if is_windows:
        flutter_bundle = flutter_dir / "build" / "windows" / "x64" / "runner" / "Release"
    else:
        flutter_bundle = flutter_dir / "build" / "linux" / "x64" / "release" / "bundle"
        
    if not flutter_bundle.exists():
        print(f"ERROR: Flutter bundle folder not found at {flutter_bundle}")
        sys.exit(1)
        
    # Add Flutter bundle directory as data to be extracted to 'gui_flutter' inside temp _MEIPASS
    args.extend(["--add-data", f"{flutter_bundle}{sep}gui_flutter"])
    
    # Add main target script
    args.append(str(main_script))
    
    print("\nExecuting PyInstaller command:")
    print(" ".join(args))
    print("\nStarting PyInstaller packaging... (this might take a few minutes as it compiles dependencies)")
    
    try:
        subprocess.run(args, check=True)
        print("\n==================================================")
        print("SUCCESS: Standalone executable created successfully!")
        
        executable_name = "OpenSplatManager"
        if is_windows:
            executable_name += ".exe"
            
        dist_path = base_dir / "dist" / executable_name
        print(f"Distributable binary path: {dist_path}")
        print("==================================================")
    except subprocess.CalledProcessError as e:
        print(f"\nERROR: PyInstaller compilation failed with code {e.returncode}")
        sys.exit(e.returncode)

if __name__ == "__main__":
    build_executable()
