package com.roadhazard.app.data.api

import com.roadhazard.app.data.model.CloudinaryUploadResponse
import com.roadhazard.app.data.model.DetectionResult
import com.roadhazard.app.data.model.DeleteImageRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Retrofit API service for road hazard detection backend
 */
interface DetectionApiService {
    
    @Multipart
    @POST("/predict")
    suspend fun predictImage(
        @Part image: MultipartBody.Part
    ): DetectionResult
    
    @Multipart
    @POST("/upload-cloudinary")
    suspend fun uploadToCloudinary(
        @Part image: MultipartBody.Part
    ): CloudinaryUploadResponse
    
    @POST("/delete-image")
    suspend fun deleteImage(
        @Body request: DeleteImageRequest
    )
}
