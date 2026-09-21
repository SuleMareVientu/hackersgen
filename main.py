import sys
import os
import subprocess
import time
import logging
import threading
import uvicorn
from app.main import app as fastapi_app
from app.services.job_manager import manager
from app import config

# Configure global logger
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("main_entrypoint")

class FastAPIThread(threading.Thread):
    """Runs the FastAPI server in a background daemon thread."""
    def __init__(self, host: str, port: int):
        super().__init__()
        self.host = host
        self.port = port
        self.daemon = True
        
        # Configure uvicorn running parameters
        self.config = uvicorn.Config(
            fastapi_app, 
            host=self.host, 
            port=self.port, 
            log_level="info",
            loop="asyncio"
        )
        self.server = uvicorn.Server(self.config)

    def run(self):
        logger.info(f"Starting FastAPI backend server on http://{self.host}:{self.port}")
        self.server.run()

    def stop(self):
        logger.info("Stopping FastAPI backend server...")
        self.server.should_exit = True

def find_flutter_binary():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    is_windows = sys.platform.startswith("win")
    
    if getattr(sys, 'frozen', False):
        # We are running inside a PyInstaller bundle
        meipass = sys._MEIPASS
        if is_windows:
            bin_path = os.path.join(meipass, "gui_flutter", "gui_flutter.exe")
        else:
            bin_path = os.path.join(meipass, "gui_flutter", "gui_flutter")
        return bin_path
        
    # Development mode
    if is_windows:
        bin_path = os.path.join(base_dir, "gui_flutter", "build", "windows", "x64", "runner", "Release", "gui_flutter.exe")
    else:
        bin_path = os.path.join(base_dir, "gui_flutter", "build", "linux", "x64", "release", "bundle", "gui_flutter")
        
    # Auto-compile if local binary is missing in dev mode
    if not os.path.exists(bin_path):
        logger.info("Flutter binary not found. Attempting to build Flutter release version...")
        flutter_cmd = "flutter"
        # Search for flutter in typical develop folder as fallback
        dev_flutter = os.path.expanduser("~/develop/flutter/bin/flutter")
        if os.path.exists(dev_flutter):
            flutter_cmd = dev_flutter
        
        try:
            build_dir = os.path.join(base_dir, "gui_flutter")
            subprocess.run([flutter_cmd, "build", "linux" if not is_windows else "windows", "--release"], cwd=build_dir, check=True)
        except Exception as e:
            logger.error(f"Failed to auto-compile Flutter application: {e}")
            logger.error("Please run 'flutter build linux' or 'flutter build windows' manually under gui_flutter.")
            
    return bin_path

def main():
    # 1. Start the FastAPI server in a background thread
    server_thread = FastAPIThread(config.HOST, config.PORT)
    server_thread.start()
    
    # Wait briefly for FastAPI to bind to port and start accepting connections
    time.sleep(1.0)
    
    # 2. Launch the Flutter Application
    flutter_bin = find_flutter_binary()
    logger.info(f"Initializing Flutter GUI Application Interface: {flutter_bin}")
    
    exit_code = 1
    if os.path.exists(flutter_bin):
        env = os.environ.copy()
        env["OPENSPLAT_API_URL"] = config.API_URL
        try:
            # Block until the Flutter application exits
            result = subprocess.run([flutter_bin], env=env)
            exit_code = result.returncode
        except Exception as e:
            logger.error(f"Error executing Flutter GUI: {e}")
    else:
        logger.error(f"Flutter binary not found at: {flutter_bin}. Please compile the Flutter application.")
        
    # 3. Clean up upon closure
    logger.info("Application shutting down...")
    
    # Terminate all active jobs in the manager
    for job in list(manager.active_tasks.keys()):
        logger.info(f"Stopping active job {job} during shutdown...")
        manager.stop_job(job)
        
    server_thread.stop()
    sys.exit(exit_code)

if __name__ == "__main__":
    main()
