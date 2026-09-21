import os
import re
import sys
import shutil
import asyncio
import logging
import cv2
from pathlib import Path
from typing import Callable, Optional
from app import config

logger = logging.getLogger("splat_runner")

def extract_frames(video_path: Path, output_dir: Path, max_frames: int = 150) -> int:
    """
    Extracts frames from an MP4 video file and saves them as images.
    Returns the number of frames extracted.
    """
    output_dir.mkdir(parents=True, exist_ok=True)
    
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise ValueError(f"Could not open video file: {video_path}")
        
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    if total_frames <= 0:
        raise ValueError(f"Invalid frame count in video: {total_frames}")
        
    # Determine the step size to get at most max_frames
    step = max(1, total_frames // max_frames)
    
    count = 0
    frame_idx = 0
    while True:
        ret, frame = cap.read()
        if not ret:
            break
            
        if frame_idx % step == 0:
            frame_name = f"frame_{count:05d}.jpg"
            out_path = output_dir / frame_name
            cv2.imwrite(str(out_path), frame)
            count += 1
            if count >= max_frames:
                break
                
        frame_idx += 1
        
    cap.release()
    logger.info(f"Extracted {count} frames from video: {video_path}")
    return count

async def run_cmd_async(cmd: list, cwd: Path, log_file_path: Path, on_log_line: Optional[Callable[[str], None]] = None):
    """
    Runs a shell command asynchronously, writing outputs to log_file_path and executing on_log_line.
    """
    logger.info(f"Running command: {' '.join(cmd)} in Cwd: {cwd}")
    
    with open(log_file_path, "a", encoding="utf-8") as f:
        f.write(f"\n--- RUNNING: {' '.join(cmd)} ---\n")
        f.flush()
        
    # Inject local conda environment libraries if present
    env = os.environ.copy()
    conda_lib = config.BASE_DIR / "conda" / "lib"
    if conda_lib.exists():
        ld_path = env.get("LD_LIBRARY_PATH", "")
        if ld_path:
            env["LD_LIBRARY_PATH"] = f"{str(conda_lib)}:{ld_path}"
        else:
            env["LD_LIBRARY_PATH"] = str(conda_lib)
            
    process = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
        cwd=str(cwd),
        env=env
    )
    
    try:
        # Read stdout line by line
        while True:
            line_bytes = await process.stdout.readline()
            if not line_bytes:
                break
            line = line_bytes.decode("utf-8", errors="replace")
            
            # Write to log file
            with open(log_file_path, "a", encoding="utf-8") as f:
                f.write(line)
                
            if on_log_line:
                on_log_line(line)
                
        code = await process.wait()
        if code != 0:
            raise RuntimeError(f"Command {' '.join(cmd)} failed with exit code {code}")
        return code
    except asyncio.CancelledError:
        logger.info(f"Task cancelled. Terminating subprocess: {' '.join(cmd)}")
        try:
            process.terminate()
            # Wait briefly for process to terminate
            await asyncio.wait_for(process.wait(), timeout=3.0)
        except Exception as e:
            logger.warning(f"Error terminating subprocess: {e}")
            try:
                process.kill()
            except Exception:
                pass
        raise


async def run_colmap_reconstruction(images_dir: Path, workspace_dir: Path, log_path: Path):
    """
    Runs COLMAP sparse reconstruction pipeline.
    """
    colmap_bin = config.COLMAP_BINARY
    if not colmap_bin or not os.path.exists(colmap_bin) and shutil.which("colmap") is None:
        raise FileNotFoundError(f"COLMAP binary not found at '{colmap_bin}'. Please install COLMAP and add it to PATH.")

    database_path = workspace_dir / "database.db"
    sparse_path = workspace_dir / "sparse"
    sparse_path.mkdir(parents=True, exist_ok=True)
    
    # Step 1: Feature Extractor
    # On Windows, COLMAP requires proper argument quoting or raw strings
    feat_cmd = [
        colmap_bin, "feature_extractor",
        "--database_path", str(database_path),
        "--image_path", str(images_dir),
        "--ImageReader.single_camera", "1"
    ]
    await run_cmd_async(feat_cmd, workspace_dir, log_path)
    
    # Step 2: Exhaustive Matcher
    match_cmd = [
        colmap_bin, "exhaustive_matcher",
        "--database_path", str(database_path)
    ]
    await run_cmd_async(match_cmd, workspace_dir, log_path)
    
    # Step 3: Mapper (Sparse reconstruction)
    mapper_cmd = [
        colmap_bin, "mapper",
        "--database_path", str(database_path),
        "--image_path", str(images_dir),
        "--output_path", str(sparse_path)
    ]
    await run_cmd_async(mapper_cmd, workspace_dir, log_path)
    
    # Check if sparse reconstruction succeeded
    reconstruction_dir = sparse_path / "0"
    if not reconstruction_dir.exists():
        # Sometimes COLMAP saves to sparse/ or database doesn't align. Check if it was empty.
        subdirs = list(sparse_path.iterdir())
        if not subdirs:
            raise RuntimeError("COLMAP sparse mapper did not generate any reconstruction models.")
        reconstruction_dir = subdirs[0]
        
    logger.info(f"COLMAP Reconstruction successful. Output: {reconstruction_dir}")
    return reconstruction_dir

async def run_opensplat_training(
    dataset_dir: Path,
    output_ply: Path,
    log_path: Path,
    num_iters: int = 30000,
    downscale: int = 1,
    use_cpu: bool = False,
    progress_callback: Optional[Callable[[int, float], None]] = None
):
    """
    Runs Brush training on dataset_dir (acting as a drop-in replacement for OpenSplat),
    writing outputs to log_path. Parses output to feed step into progress_callback.
    """
    brush_bin = config.BRUSH_BINARY
    if not brush_bin or not os.path.exists(brush_bin) and shutil.which("brush") is None:
        raise FileNotFoundError(f"Brush binary not found at '{brush_bin}'. Please compile Brush.")

    job_dir = output_ply.parent
    export_dir = job_dir / "exports"
    export_dir.mkdir(parents=True, exist_ok=True)
    
    # Clean up old exports if they exist
    for f in export_dir.glob("export_*.ply"):
        try:
            f.unlink()
        except Exception:
            pass

    # Determine reporting interval based on total iterations to get ~30 steps
    report_every = max(10, num_iters // 30)

    cmd = [
        brush_bin,
        str(dataset_dir),
        "--total-train-iters", str(num_iters),
        "--export-path", str(export_dir),
        "--export-name", "export_{iter}.ply",
        "--export-every", str(report_every),
        "--rerun-log-distribution-every", str(report_every)
    ]

    if downscale > 1:
        # Scale max-resolution (default 1920) by downscale factor
        max_res = max(256, 1920 // downscale)
        cmd.extend(["--max-resolution", str(max_res)])

    # Set RUST_LOG=info in environment so Brush logs stats to stdout/stderr
    old_rust_log = os.environ.get("RUST_LOG")
    os.environ["RUST_LOG"] = "info"

    # Regex to parse step number
    pattern = re.compile(r"iter=(\d+)")

    def parse_log_line(line: str):
        match = pattern.search(line)
        if match and progress_callback:
            try:
                step = int(match.group(1))
                progress_callback(step, 0.0)
            except Exception:
                pass

    try:
        await run_cmd_async(cmd, dataset_dir, log_path, on_log_line=parse_log_line)
    finally:
        # Restore environment
        if old_rust_log is not None:
            os.environ["RUST_LOG"] = old_rust_log
        else:
            os.environ.pop("RUST_LOG", None)

    # Find the final exported file (the one with highest iteration number)
    exported_plys = list(export_dir.glob("export_*.ply"))
    if not exported_plys:
        raise RuntimeError(f"Brush training completed but no exported models were found in {export_dir}")

    def get_step_num(path: Path) -> int:
        match = re.search(r"export_(\d+)\.ply", path.name)
        return int(match.group(1)) if match else -1

    exported_plys.sort(key=get_step_num, reverse=True)
    final_ply = exported_plys[0]
    
    # Copy final PLY to the requested output path
    shutil.copy2(final_ply, output_ply)
    logger.info(f"Brush training completed. Model saved at {output_ply}")
