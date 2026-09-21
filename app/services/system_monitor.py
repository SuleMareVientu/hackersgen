import logging
import psutil
import shutil
import socket

logger = logging.getLogger("system_monitor")

# Initialize NVML for Nvidia GPU monitoring
NVML_AVAILABLE = False
try:
    import pynvml
    pynvml.nvmlInit()
    NVML_AVAILABLE = True
    logger.info("NVML initialized successfully for GPU monitoring.")
except Exception as e:
    logger.warning(f"Could not initialize NVML (Nvidia GPU monitoring will be unavailable): {e}")

def get_gpu_stats():
    """
    Retrieves Nvidia GPU usage if NVML is available.
    Returns a dict with load, memory, and name, or None if unavailable.
    """
    if not NVML_AVAILABLE:
        return {
            "available": False,
            "name": "N/A",
            "load": 0.0,
            "memory_used": 0,
            "memory_total": 0,
            "memory_percent": 0.0,
            "temp": 0
        }
    
    try:
        # For simplicity, we monitor the first GPU (index 0)
        handle = pynvml.nvmlDeviceGetHandleByIndex(0)
        name = pynvml.nvmlDeviceGetName(handle)
        if isinstance(name, bytes):
            name = name.decode("utf-8")
            
        utilization = pynvml.nvmlDeviceGetUtilizationRates(handle)
        mem_info = pynvml.nvmlDeviceGetMemoryInfo(handle)
        
        try:
            temp = pynvml.nvmlDeviceGetTemperature(handle, pynvml.NVML_TEMPERATURE_GPU)
        except Exception:
            temp = 0
            
        return {
            "available": True,
            "name": name,
            "load": float(utilization.gpu),
            "memory_used": int(mem_info.used),
            "memory_total": int(mem_info.total),
            "memory_percent": round((mem_info.used / mem_info.total) * 100, 1) if mem_info.total > 0 else 0.0,
            "temp": int(temp)
        }
    except Exception as e:
        logger.debug(f"Failed to fetch GPU stats: {e}")
        return {
            "available": False,
            "name": "Error fetching stats",
            "load": 0.0,
            "memory_used": 0,
            "memory_total": 0,
            "memory_percent": 0.0,
            "temp": 0
        }

def get_local_ip() -> str:
    """
    Finds the primary local IP address of the machine.
    """
    try:
        # Create a temporary socket to determine primary network interface IP
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        try:
            return socket.gethostbyname(socket.gethostname())
        except Exception:
            return "127.0.0.1"

def get_system_stats():
    """
    Aggregates CPU, RAM, Disk, and GPU stats.
    """
    cpu_percent = psutil.cpu_percent(interval=None)
    ram = psutil.virtual_memory()
    disk = psutil.disk_usage("/")
    
    gpu_stats = get_gpu_stats()
    
    return {
        "local_ip": get_local_ip(),
        "cpu": {
            "percent": cpu_percent,
            "cores": psutil.cpu_count(logical=True)
        },
        "ram": {
            "total": ram.total,
            "available": ram.available,
            "used": ram.used,
            "percent": ram.percent
        },
        "disk": {
            "total": disk.total,
            "used": disk.used,
            "free": disk.free,
            "percent": disk.percent
        },
        "gpu": gpu_stats
    }
