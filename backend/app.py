"""
FastAPI Backend for Road Hazard Detection
Provides /predict endpoint for YOLOv12n inference on uploaded images
and /upload-cloudinary endpoint for image uploads to Cloudinary
"""

from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from ultralytics import YOLO
from PIL import Image
import io
import os
from dotenv import load_dotenv
from typing import List
from pydantic import BaseModel
import cloudinary
import cloudinary.uploader

# Load environment variables
load_dotenv()

# Configure Cloudinary
cloudinary.config(
    cloud_name=os.getenv("CLOUDINARY_CLOUD_NAME"),
    api_key=os.getenv("CLOUDINARY_API_KEY"),
    api_secret=os.getenv("CLOUDINARY_API_SECRET"),
    secure=True
)

# Configuration
HOST = os.getenv("HOST", "0.0.0.0")
PORT = int(os.getenv("PORT", 8000))
MODEL_PATH = os.getenv("MODEL_PATH", "./models/best.pt")
CONFIDENCE_THRESHOLD = float(os.getenv("CONFIDENCE_THRESHOLD", 0.5))
IOU_THRESHOLD = float(os.getenv("IOU_THRESHOLD", 0.45))

# Initialize FastAPI app
app = FastAPI(
    title="Road Hazard Detection API",
    description="YOLOv12n inference for road hazard detection",
    version="1.0.0"
)

# CORS configuration - allow all origins for development
# TODO: Restrict to specific origins in production
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Allow all origins
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global model variable
model = None


# Response models
class Detection(BaseModel):
    bbox: List[float]  # [x1, y1, x2, y2] in original image coordinates
    label: str
    confidence: float


class DetectionResult(BaseModel):
    detections: List[Detection]
    image_width: int
    image_height: int


class CloudinaryUploadResult(BaseModel):
    image_url: str


@app.on_event("startup")
async def load_model():
    """Load YOLO model on server startup"""
    global model
    
    if not os.path.exists(MODEL_PATH):
        raise FileNotFoundError(
            f"Model file not found at {MODEL_PATH}. "
            f"Please place your best.pt file in the models/ directory."
        )
    
    print(f"Loading YOLO model from {MODEL_PATH}...")
    model = YOLO(MODEL_PATH)
    print("Model loaded successfully!")


@app.get("/")
async def root():
    """Health check endpoint"""
    return {
        "status": "online",
        "model_loaded": model is not None,
        "confidence_threshold": CONFIDENCE_THRESHOLD
    }


@app.post("/predict", response_model=DetectionResult)
async def predict_image(image: UploadFile = File(...)):
    """
    Predict road hazards in uploaded image
    
    Args:
        image: Uploaded image file (JPEG, PNG)
    
    Returns:
        DetectionResult with bounding boxes, labels, and confidence scores
    """
    
    # Validate model is loaded
    if model is None:
        raise HTTPException(status_code=500, detail="Model not loaded")
    
    # Validate file type
    if not image.content_type.startswith("image/"):
        raise HTTPException(
            status_code=400,
            detail=f"Invalid file type: {image.content_type}. Please upload an image."
        )
    
    try:
        # Read and open image
        image_bytes = await image.read()
        pil_image = Image.open(io.BytesIO(image_bytes))
        
        # Get original image dimensions
        image_width, image_height = pil_image.size
        
        # Run YOLO inference
        results = model.predict(
            source=pil_image,
            conf=CONFIDENCE_THRESHOLD,
            iou=IOU_THRESHOLD,
            verbose=False
        )
        
        # Extract detections
        detections = []
        
        if len(results) > 0:
            result = results[0]
            
            # Get boxes, confidences, and class labels
            if result.boxes is not None and len(result.boxes) > 0:
                boxes = result.boxes.xyxy.cpu().numpy()  # [x1, y1, x2, y2] format
                confidences = result.boxes.conf.cpu().numpy()
                class_ids = result.boxes.cls.cpu().numpy()
                
                # Get class names
                class_names = result.names
                
                for box, conf, cls_id in zip(boxes, confidences, class_ids):
                    x1, y1, x2, y2 = box
                    label = class_names[int(cls_id)]
                    
                    detections.append(Detection(
                        bbox=[float(x1), float(y1), float(x2), float(y2)],
                        label=label,
                        confidence=float(conf)
                    ))
        
        return DetectionResult(
            detections=detections,
            image_width=image_width,
            image_height=image_height
        )
    
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Error processing image: {str(e)}"
        )


@app.post("/upload-cloudinary", response_model=CloudinaryUploadResult)
async def upload_to_cloudinary(image: UploadFile = File(...)):
    """
    Upload image to Cloudinary and return the secure URL.
    
    Args:
        image: Uploaded image file (JPEG, PNG)
    
    Returns:
        CloudinaryUploadResult with the Cloudinary secure URL
    """
    
    # Validate file type
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(
            status_code=400,
            detail=f"Invalid file type: {image.content_type}. Please upload an image."
        )
    
    try:
        # Read image bytes
        image_bytes = await image.read()
        
        # Upload to Cloudinary
        result = cloudinary.uploader.upload(
            image_bytes,
            folder="road_hazard_reports",
            resource_type="image"
        )
        
        # Return the secure URL
        secure_url = result.get("secure_url")
        if not secure_url:
            raise HTTPException(
                status_code=500,
                detail="Cloudinary upload succeeded but no URL returned"
            )
        
        return CloudinaryUploadResult(image_url=secure_url)
    
    except cloudinary.exceptions.Error as e:
        raise HTTPException(
            status_code=500,
            detail=f"Cloudinary upload failed: {str(e)}"
        )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Error uploading image: {str(e)}"
        )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host=HOST, port=PORT)
