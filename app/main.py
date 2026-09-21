import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.endpoints import router as api_router

# Set up logging format
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[
        logging.StreamHandler()
    ]
)
logger = logging.getLogger("main")

app = FastAPI(
    title="OpenSplat Server",
    description="Backend API for 3D Gaussian Splatting and OpenSplat management",
    version="1.0.0"
)

# Enable CORS for local cross-origin development or GUI requests
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register endpoints under /api
app.include_router(api_router, prefix="/api")

@app.get("/")
def read_root():
    return {"status": "running", "service": "OpenSplat API Server"}
