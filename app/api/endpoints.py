import os
import uuid
import shutil
import logging
from pathlib import Path
from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from fastapi.responses import FileResponse
from app.services.job_manager import manager
from app.services import system_monitor
from app import config

logger = logging.getLogger("endpoints")
router = APIRouter()

@router.post("/upload/video")
async def upload_video(file: UploadFile = File(...)):
    """Accepts video upload and creates a job."""
    if not file.filename.lower().endswith(".mp4"):
        raise HTTPException(status_code=400, detail="Only .mp4 video files are accepted.")
        
    job_id = f"job_{uuid.uuid4().hex[:8]}"
    metadata = manager.create_job(job_id, "video", file.filename)
    
    job_dir = manager.get_job_dir(job_id)
    video_path = job_dir / "input" / "video.mp4"
    
    try:
        with open(video_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
    except Exception as e:
        logger.error(f"Failed to save uploaded video: {e}")
        metadata["status"] = "failed"
        metadata["error_message"] = f"Failed to save video upload: {str(e)}"
        manager._save_job_status(job_id)
        raise HTTPException(status_code=500, detail="Failed to write file on server.")
        
    return {"job_id": job_id, "status": "uploaded"}

@router.post("/upload/dataset")
async def upload_dataset(file: UploadFile = File(...)):
    """Accepts Nerfstudio zip dataset, extracts and validates it."""
    if not file.filename.lower().endswith(".zip"):
        raise HTTPException(status_code=400, detail="Only .zip files are accepted.")
        
    job_id = f"job_{uuid.uuid4().hex[:8]}"
    metadata = manager.create_job(job_id, "dataset", file.filename)
    
    job_dir = manager.get_job_dir(job_id)
    zip_path = job_dir / "input" / "dataset.zip"
    
    try:
        # Save zip
        with open(zip_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
            
        # Validate & extract
        success = manager.prepare_dataset_zip(job_id, zip_path)

        
        # Remove zip file to save space
        if zip_path.exists():
            zip_path.unlink()
            
        if not success:
            raise HTTPException(status_code=400, detail="Dataset zip validation failed (missing transforms.json or images).")
            
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Dataset upload preparation failed: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to prepare dataset: {str(e)}")
        
    return {"job_id": job_id, "status": "uploaded"}

@router.get("/jobs")
async def list_jobs():
    """Returns all jobs and their states."""
    return manager.list_jobs()

@router.get("/jobs/{job_id}/status")
async def get_job_status(job_id: str):
    """Retrieves current job state, progress metrics, and last few log lines."""
    metadata = manager.get_job_status(job_id)
    if not metadata:
        raise HTTPException(status_code=404, detail="Job not found.")
        
    # Retrieve log tails
    job_dir = manager.get_job_dir(job_id)
    log_file = job_dir / "console.log"
    log_tail = []
    if log_file.exists():
        try:
            with open(log_file, "r", encoding="utf-8") as f:
                # Read last 50 lines
                lines = f.readlines()
                log_tail = lines[-50:]
        except Exception as e:
            log_tail = [f"Error reading logs: {e}"]
            
    return {
        "metadata": metadata,
        "logs": log_tail
    }

@router.post("/jobs/{job_id}/start")
async def start_job(
    job_id: str,
    num_iters: int = Form(30000),
    downscale: int = Form(1),
    use_cpu: bool = Form(False)
):
    """Starts a training job with configuration settings."""
    success = manager.start_job(job_id, num_iters=num_iters, downscale=downscale, use_cpu=use_cpu)
    if not success:
        raise HTTPException(status_code=400, detail="Job could not be started (already running or invalid ID).")
    return {"status": "started"}

@router.post("/jobs/{job_id}/stop")
async def stop_job(job_id: str):
    """Stops a running job."""
    success = manager.stop_job(job_id)
    if not success:
        raise HTTPException(status_code=400, detail="Job could not be stopped.")
    return {"status": "stopped"}

@router.get("/jobs/{job_id}/download")
async def download_model(job_id: str, format: str = "ply"):
    """Serves the output model file (splat.ply or conversion)."""
    metadata = manager.get_job_status(job_id)
    if not metadata:
        raise HTTPException(status_code=404, detail="Job not found.")
        
    if metadata["status"] != "completed":
        raise HTTPException(status_code=400, detail=f"Model download is not ready. Current job status: {metadata['status']}")
        
    job_dir = manager.get_job_dir(job_id)
    ply_path = job_dir / "splat.ply"
    
    if not ply_path.exists():
        raise HTTPException(status_code=404, detail="Model file splat.ply not found on server.")
        
    # Serve requested format.
    # Note: If the format is .splat, we serve it as ply or a placeholder if conversion not fully supported
    # In production, we return the output PLY, which can be renamed or rendered.
    # Let's support both extensions cleanly.
    filename = f"{job_id}.ply"
    if format.lower() == "splat":
        filename = f"{job_id}.splat"
        
    return FileResponse(
        path=ply_path,
        media_type="application/octet-stream",
        filename=filename
    )

@router.get("/system/stats")
async def get_system_stats():
    """Returns live hardware metrics and active jobs count."""
    stats = system_monitor.get_system_stats()
    
    # Calculate active running count
    active_count = sum(
        1 for j in manager.list_jobs() 
        if j.get("status") in ("preprocessing", "training")
    )
    
    stats["active_jobs_count"] = active_count
    return stats

@router.delete("/jobs/{job_id}/logs")
async def delete_job_logs(job_id: str):
    """Clears logs for a specific job."""
    metadata = manager.get_job_status(job_id)
    if not metadata:
        raise HTTPException(status_code=404, detail="Job not found.")
        
    job_dir = manager.get_job_dir(job_id)
    log_file = job_dir / "console.log"
    if log_file.exists():
        try:
            with open(log_file, "w", encoding="utf-8") as f:
                f.write("")
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"Failed to clear logs: {e}")
    return {"status": "logs_cleared"}

@router.delete("/jobs/{job_id}")
async def delete_job(job_id: str):
    """Deletes a job completely from the server."""
    success = manager.delete_job(job_id)
    if not success:
        raise HTTPException(status_code=404, detail="Job not found.")
    return {"status": "deleted"}

