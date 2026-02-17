package com.roadhazard.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Request body for deleting an image from Cloudinary
 */
data class DeleteImageRequest(
    @SerializedName("public_id")
    val publicId: String
)
