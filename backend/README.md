# Road Hazard Detection Backend

Python FastAPI server for YOLOv12n inference on uploaded images.

## Setup

### 1. Install Dependencies

```bash
pip install -r requirements.txt
```

### 2. Place Model File

Place your trained `best.pt` YOLOv12n model in the `models/` directory:

```
backend/
├── models/
│   └── best.pt          # ← Your YOLO model here
├── app.py
├── requirements.txt
└── .env
```

### 3. Configure Environment

Copy `.env.example` to `.env` and adjust settings if needed:

```bash
cp .env.example .env
```

Default configuration:
- `HOST=0.0.0.0` (accessible from all network interfaces)
- `PORT=8000`
- `MODEL_PATH=./models/best.pt`
- `CONFIDENCE_THRESHOLD=0.5`
- `IOU_THRESHOLD=0.45`

## Running the Server

### Development Mode (with auto-reload)

```bash
uvicorn app:app --host 0.0.0.0 --port 8000 --reload
```

### Production Mode

```bash
uvicorn app:app --host 0.0.0.0 --port 8000
```

The server will start at `http://localhost:8000`

## API Documentation

Once running, visit:
- API Docs (Swagger): `http://localhost:8000/docs`
- Alternative Docs (ReDoc): `http://localhost:8000/redoc`

## Endpoints

### `GET /`
Health check endpoint

**Response:**
```json
{
  "status": "online",
  "model_loaded": true,
  "confidence_threshold": 0.5
}
```

### `POST /predict`
Upload image for hazard detection

**Request:**
- Method: `POST`
- Content-Type: `multipart/form-data`
- Body: `image` file (JPEG or PNG)

**Response:**
```json
{
  "detections": [
    {
      "bbox": [100.5, 200.3, 350.8, 450.2],
      "label": "pothole",
      "confidence": 0.95
    }
  ],
  "image_width": 1920,
  "image_height": 1080
}
```

## Testing with cURL

```bash
curl -X POST "http://localhost:8000/predict" \
  -F "image=@/path/to/test/image.jpg"
```

## Network Configuration

### For Android Emulator
Use `http://10.0.2.2:8000` in your Android app to access the server running on your host machine.

### For Physical Device
- Ensure device and computer are on the same WiFi network
- Use your computer's local IP address (e.g., `http://192.168.1.100:8000`)
- Find your IP with: `ipconfig getifaddr en0` (macOS) or `ipconfig` (Windows)

### For Production
Deploy to a cloud server and update the base URL in your Android app.
