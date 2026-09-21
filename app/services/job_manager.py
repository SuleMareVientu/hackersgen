import os
import json
import zipfile
import shutil
import asyncio
import logging
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional
from app import config
from app.services import splat_runner

logger = logging.getLogger("job_manager")

class JobManager:
    def __init__(self):
        self.jobs_dir = config.JOBS_DIR
        self.active_tasks: Dict[str, asyncio.Task] = {}
        self.jobs_metadata: Dict[str, dict] = {}
        self._load_existing_jobs()

    def _load_existing_jobs(self):
        """Loads all existing job states from disk on startup."""
        if not self.jobs_dir.exists():
            return
            
        for job_folder in self.jobs_dir.iterdir():
            if job_folder.is_dir():
                status_file = job_folder / "status.json"
                if status_file.exists():
                    try:
                        with open(status_file, "r") as f:
                            self.jobs_metadata[job_folder.name] = json.load(f)
                    except Exception as e:
                        logger.error(f"Failed to load status for job {job_folder.name}: {e}")

    def _save_job_status(self, job_id: str):
        """Saves current job metadata to disk."""
        job_dir = self.jobs_dir / job_id
        job_dir.mkdir(parents=True, exist_ok=True)
        status_file = job_dir / "status.json"
        
        metadata = self.jobs_metadata.get(job_id)
        if metadata:
            try:
                # Write atomically to prevent corruption
                temp_file = status_file.with_suffix(".tmp")
                with open(temp_file, "w") as f:
                    json.dump(metadata, f, indent=2)
                if os.path.exists(status_file):
                    os.remove(status_file)
                os.rename(temp_file, status_file)
            except Exception as e:
                logger.error(f"Failed to save status for job {job_id}: {e}")

    def create_job(self, job_id: str, job_type: str, source_filename: str) -> dict:
        """Initializes a new job directory and status metadata."""
        job_dir = self.jobs_dir / job_id
        job_dir.mkdir(parents=True, exist_ok=True)
        (job_dir / "input").mkdir(parents=True, exist_ok=True)
        
        metadata = {
            "job_id": job_id,
            "job_type": job_type,
            "source_file": source_filename,
            "status": "uploaded",
            "progress": 0.0,
            "step": 0,
            "total_steps": 30000,
            "loss": 0.0,
            "error_message": "",
            "created_at": datetime.now().isoformat(),
            "updated_at": datetime.now().isoformat()
        }
        
        self.jobs_metadata[job_id] = metadata
        self._save_job_status(job_id)
        return metadata

    def get_job_dir(self, job_id: str) -> Path:
        return self.jobs_dir / job_id

    def get_job_status(self, job_id: str) -> Optional[dict]:
        return self.jobs_metadata.get(job_id)

    def list_jobs(self) -> List[dict]:
        # Return sorted by created_at descending
        return sorted(self.jobs_metadata.values(), key=lambda x: x.get("created_at", ""), reverse=True)

    def prepare_dataset_zip(self, job_id: str, zip_path: Path) -> bool:
        """Extracts and validates a Nerfstudio dataset zip."""
        job_dir = self.get_job_dir(job_id)
        input_dir = job_dir / "input"
        
        try:
            # Unzip
            with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                zip_ref.extractall(input_dir)
                
            # Let's inspect and clean up the extraction.
            # Sometimes the zip contains a nested folder.
            # We want to find transforms.json.
            transforms_file = None
            for p in input_dir.rglob("transforms.json"):
                transforms_file = p
                break
                
            if not transforms_file:
                raise ValueError("Dataset does not contain a transforms.json file.")
                
            # If transforms.json is nested, flatten the structure to input_dir
            if transforms_file.parent != input_dir:
                nested_dir = transforms_file.parent
                for item in nested_dir.iterdir():
                    shutil.move(str(item), str(input_dir))
                # Delete empty nested dirs
                shutil.rmtree(str(nested_dir), ignore_errors=True)
                
            # Verify images folder exists
            images_dir = input_dir / "images"
            if not images_dir.exists() or not images_dir.is_dir():
                # Let's see if there is another image directory
                possible_img_dirs = [p for p in input_dir.iterdir() if p.is_dir() and p.name in ("images", "images_2", "images_4", "images_8")]
                if possible_img_dirs:
                    # Rename it to images
                    shutil.move(str(possible_img_dirs[0]), str(images_dir))
                else:
                    raise ValueError("Dataset does not contain an images folder.")
                    
            logger.info(f"Successfully verified dataset for job {job_id}")
            return True
            
        except Exception as e:
            logger.error(f"Failed to prepare dataset for job {job_id}: {e}")
            metadata = self.jobs_metadata.get(job_id)
            if metadata:
                metadata["status"] = "failed"
                metadata["error_message"] = f"Dataset validation failed: {str(e)}"
                self._save_job_status(job_id)
            return False

    def start_job(self, job_id: str, num_iters: int = 30000, downscale: int = 1, use_cpu: bool = False) -> bool:
        """Launches the training workflow in the background."""
        metadata = self.jobs_metadata.get(job_id)
        if not metadata:
            return False
            
        if metadata["status"] in ("preprocessing", "training"):
            logger.warning(f"Job {job_id} is already running.")
            return False
            
        # Cancel any previous active task for this job
        if job_id in self.active_tasks:
            self.active_tasks[job_id].cancel()
            
        # Create an async task
        task = asyncio.create_task(self._run_job_pipeline(job_id, num_iters, downscale, use_cpu))
        self.active_tasks[job_id] = task
        return True

    def stop_job(self, job_id: str) -> bool:
        """Stops a running training workflow."""
        metadata = self.jobs_metadata.get(job_id)
        if not metadata:
            return False
            
        task = self.active_tasks.get(job_id)
        if task and not task.done():
            task.cancel()
            
        metadata["status"] = "stopped"
        metadata["updated_at"] = datetime.now().isoformat()
        self._save_job_status(job_id)
        
        if job_id in self.active_tasks:
            del self.active_tasks[job_id]
            
        logger.info(f"Job {job_id} stopped by user.")
        return True

    def delete_job(self, job_id: str) -> bool:
        """Stops and deletes a job completely from metadata and disk."""
        if job_id not in self.jobs_metadata:
            return False
            
        # First, ensure the job is stopped
        self.stop_job(job_id)
        
        # Delete job directory on disk
        job_dir = self.get_job_dir(job_id)
        if job_dir.exists():
            shutil.rmtree(job_dir, ignore_errors=True)
            
        # Remove from metadata dictionary
        del self.jobs_metadata[job_id]
        logger.info(f"Job {job_id} deleted completely.")
        return True

    async def _run_job_pipeline(self, job_id: str, num_iters: int, downscale: int, use_cpu: bool):
        """Asynchronous pipeline that executes reconstruction and training."""
        metadata = self.jobs_metadata[job_id]
        job_dir = self.get_job_dir(job_id)
        log_path = job_dir / "console.log"
        output_ply = job_dir / "splat.ply"
        
        # Clear log file
        if log_path.exists():
            log_path.unlink()
            
        try:
            # 1. Preprocessing Stage
            metadata["status"] = "preprocessing"
            metadata["progress"] = 5.0
            metadata["updated_at"] = datetime.now().isoformat()
            self._save_job_status(job_id)
            
            dataset_dir = job_dir / "input"
            
            if metadata["job_type"] == "video":
                # Video needs frame extraction and COLMAP reconstruction
                video_path = dataset_dir / "video.mp4"
                images_dir = dataset_dir / "images"
                
                with open(log_path, "a") as f:
                    f.write("--- STEP 1: Extracting video frames ---\n")
                
                # Extract frames
                loop = asyncio.get_running_loop()
                num_frames = await loop.run_in_executor(None, splat_runner.extract_frames, video_path, images_dir)
                
                metadata["progress"] = 15.0
                self._save_job_status(job_id)
                
                with open(log_path, "a") as f:
                    f.write(f"Extracted {num_frames} frames. Starting COLMAP sparse mapping...\n")
                
                # Run COLMAP
                reconstructed_dir = await splat_runner.run_colmap_reconstruction(images_dir, dataset_dir, log_path)
                
                # Set dataset_dir for OpenSplat to the reconstruction directory parent
                # OpenSplat will look for sparse/0/ and images/ inside it
                dataset_dir = dataset_dir
                
            else:
                # Zip dataset already extracted and validated
                with open(log_path, "a") as f:
                    f.write("Dataset already prepared. Skipping structure-from-motion...\n")
                await asyncio.sleep(0.5)
                
            # 2. Training Stage
            metadata["status"] = "training"
            metadata["progress"] = 30.0
            metadata["total_steps"] = num_iters
            metadata["updated_at"] = datetime.now().isoformat()
            self._save_job_status(job_id)
            
            # Progress callback for OpenSplat
            def on_progress(step: int, loss: float):
                # Map steps from [0, num_iters] to progress range [30.0, 100.0]
                train_progress = (step / num_iters) * 70.0
                metadata["progress"] = round(30.0 + train_progress, 1)
                metadata["step"] = step
                metadata["loss"] = round(loss, 5)
                metadata["updated_at"] = datetime.now().isoformat()
                self._save_job_status(job_id)
                
            await splat_runner.run_opensplat_training(
                dataset_dir=dataset_dir,
                output_ply=output_ply,
                log_path=log_path,
                num_iters=num_iters,
                downscale=downscale,
                use_cpu=use_cpu,
                progress_callback=on_progress
            )
            
            # 3. Completed
            metadata["status"] = "completed"
            metadata["progress"] = 100.0
            metadata["updated_at"] = datetime.now().isoformat()
            self._save_job_status(job_id)
            logger.info(f"Job {job_id} completed successfully.")
            
        except asyncio.CancelledError:
            metadata["status"] = "stopped"
            metadata["updated_at"] = datetime.now().isoformat()
            self._save_job_status(job_id)
            with open(log_path, "a") as f:
                f.write("\nJob stopped by user.\n")
            logger.info(f"Job {job_id} cancelled.")
            
        except Exception as e:
            metadata["status"] = "failed"
            metadata["error_message"] = str(e)
            metadata["updated_at"] = datetime.now().isoformat()
            self._save_job_status(job_id)
            with open(log_path, "a") as f:
                f.write(f"\nERROR: {str(e)}\n")
            logger.error(f"Job {job_id} failed: {e}")
            
        finally:
            if job_id in self.active_tasks:
                del self.active_tasks[job_id]

# Singleton instance of JobManager
manager = JobManager()
