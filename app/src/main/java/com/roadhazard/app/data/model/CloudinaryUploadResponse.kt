package com.roadhazard.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response from backend /upload-cloudinary endpoint
 */
data class CloudinaryUploadResponse(
    @SerializedName("image_url")
    val imageUrl: String
)
