package com.roadhazard.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Detection result from backend YOLOv12n inference
 */
data class DetectionResult(
    @SerializedName("detections")
    val detections: List<Detection>,
    
    @SerializedName("image_width")
    val imageWidth: Int,
    
    @SerializedName("image_height")
    val imageHeight: Int
)

/**
 * Individual hazard detection with bounding box
 */
data class Detection(
    @SerializedName("bbox")
    val bbox: List<Float>,  // [x1, y1, x2, y2] in original image coordinates
    
    @SerializedName("label")
    val label: String,
    
    @SerializedName("confidence")
    val confidence: Float
)
