package com.roadhazard.app.ui.screens.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.roadhazard.app.data.api.DetectionApiService
import com.roadhazard.app.data.model.DeleteImageRequest
import com.roadhazard.app.data.model.DetectionResult
import com.roadhazard.app.data.service.GeocodingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import androidx.exifinterface.media.ExifInterface

@HiltViewModel
class ImageUploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detectionApiService: DetectionApiService,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val geocodingService: GeocodingService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()
    
    /**
     * Upload image to backend for AI inference
     */
    fun uploadImage(uri: Uri) {
        viewModelScope.launch {
            try {
                // Try to extract GPS from EXIF
                val exifCoordinates = extractGpsFromExif(uri)
                android.util.Log.d("ImageUploadViewModel", "EXIF GPS result: $exifCoordinates")
                
                _uiState.value = _uiState.value.copy(
                    selectedImageUri = uri,
                    isLoading = true,
                    error = null,
                    detectionResult = null,
                    locationFromExif = exifCoordinates  // May be null - checked at save time
                )
                
                // Convert URI to file for multipart upload
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File(context.cacheDir, "upload_temp.jpg")
                file.outputStream().use { outputStream ->
                    inputStream?.copyTo(outputStream)
                }
                inputStream?.close()
                
                // Create multipart request
                val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("image", file.name, requestBody)
                
                // Call API
                val result = detectionApiService.predictImage(imagePart)
                
                // Clean up temp file
                file.delete()
                
                // Update state with results
                // If hazards detected, generate the bbox image for display AND saving
                var bboxImageUri: Uri? = null
                if (result.detections.isNotEmpty()) {
                    val bboxFile = createImageWithBoundingBoxes(uri, result)
                    bboxImageUri = Uri.fromFile(bboxFile)
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    detectionResult = result,
                    bboxImageUri = bboxImageUri,
                    error = null
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to analyze image: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Extract GPS coordinates from image EXIF metadata.
     * Handles cloud picker URIs gracefully (they strip GPS data).
     */
    private fun extractGpsFromExif(uri: Uri): LatLng? {
        android.util.Log.d("ImageUploadViewModel", "Starting EXIF extraction for URI: $uri")
        
        // Try with setRequireOriginal first (for local Photo Picker on Q+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // This is needed for Android 10+ to get the original file with GPS data
                val originalUri = MediaStore.setRequireOriginal(uri)
                val coords = getGpsFromStream(originalUri)
                if (coords != null) {
                    android.util.Log.d("ImageUploadViewModel", "Successfully extracted GPS using setRequireOriginal")
                    return coords
                }
            } catch (e: Exception) {
                android.util.Log.d("ImageUploadViewModel", "setRequireOriginal failed or not supported: ${e.message}")
            }
        }
        
        // Fall back to raw URI
        val coords = getGpsFromStream(uri)
        if (coords != null) {
            android.util.Log.d("ImageUploadViewModel", "Successfully extracted GPS using raw URI")
        } else {
            android.util.Log.d("ImageUploadViewModel", "No GPS data found in EXIF using any method")
        }
        return coords
    }
    
    /**
     * Read GPS from an InputStream opened from a URI
     */
    private fun getGpsFromStream(uri: Uri): LatLng? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                
                // Debug: Log some basic EXIF info to see if it even exists
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                val date = exif.getAttribute(ExifInterface.TAG_DATETIME)
                android.util.Log.d("ImageUploadViewModel", "EXIF Info - Make: $make, Model: $model, Date: $date")

                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    android.util.Log.d("ImageUploadViewModel", "GPS found in EXIF: ${latLong[0]}, ${latLong[1]}")
                    LatLng(latLong[0].toDouble(), latLong[1].toDouble())
                } else {
                    // Check specifically for GPS latitude tag to see if it's there but maybe malformed
                    val latTag = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
                    android.util.Log.d("ImageUploadViewModel", "GPS tags: Latitude Tag exists? ${latTag != null}")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageUploadViewModel", "Error reading EXIF for URI: $uri", e)
            null
        }
    }

    
    /**
     * Draw bounding boxes onto the original image and save as a new file.
     * The resulting image is what gets uploaded to Cloudinary and displayed in reports.
     */
    private fun createImageWithBoundingBoxes(uri: Uri, detectionResult: DetectionResult): File {
        // Load original bitmap
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        
        // Read EXIF orientation
        val exifInputStream = context.contentResolver.openInputStream(uri)
        val exif = exifInputStream?.let { ExifInterface(it) }
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        exifInputStream?.close()

        // Calculate rotation degrees
        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        // Rotate bitmap if needed
        val rotatedBitmap = if (rotationDegrees != 0f) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees)
            Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
        } else {
            originalBitmap
        }

        // Create a mutable copy to draw on from the (possibly rotated) bitmap
        val mutableBitmap = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // Capture original dimensions for coordinate transformation
        val originalWidth = originalBitmap.width.toFloat()
        val originalHeight = originalBitmap.height.toFloat()
        
        // If we created a new rotated bitmap, recycle both it and the original to save memory
        if (rotatedBitmap != originalBitmap) {
            rotatedBitmap.recycle()
        }
        originalBitmap.recycle()

        val canvas = Canvas(mutableBitmap)
        
         // Scale text/stroke proportionally to image size
        val scaleFactor = maxOf(mutableBitmap.width, mutableBitmap.height) / 1000f
        val strokeWidth = 6f * scaleFactor
        val textSize = 28f * scaleFactor
        val padding = 6f * scaleFactor
        
        // Green color for all bounding boxes
        val boxColor = android.graphics.Color.GREEN
        
        // Draw each detection
        detectionResult.detections.forEach { detection ->
            val (x1, y1, x2, y2) = detection.bbox
            
            // Transform coordinates if image was rotated
            // The backend returns coordinates relative to the ORIGINAL (unrotated) image
            var newX1 = x1
            var newY1 = y1
            var newX2 = x2
            var newY2 = y2
            
            if (rotationDegrees == 90f) {
                // 90 CW: x' = h - y, y' = x
                newX1 = originalHeight - y1
                newY1 = x1
                newX2 = originalHeight - y2
                newY2 = x2
            } else if (rotationDegrees == 180f) {
                // 180: x' = w - x, y' = h - y
                newX1 = originalWidth - x1
                newY1 = originalHeight - y1
                newX2 = originalWidth - x2
                newY2 = originalHeight - y2
            } else if (rotationDegrees == 270f) {
                // 270 CW: x' = y, y' = w - x
                newX1 = y1
                newY1 = originalWidth - x1
                newX2 = y2
                newY2 = originalWidth - x2
            }
            
            // Normalize coordinates (ensure left < right, top < bottom)
            val finalX1 = minOf(newX1, newX2)
            val finalY1 = minOf(newY1, newY2)
            val finalX2 = maxOf(newX1, newX2)
            val finalY2 = maxOf(newY1, newY2)
            
            // Draw bounding box
            val boxPaint = Paint().apply {
                color = boxColor
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                isAntiAlias = true
            }
            canvas.drawRect(finalX1, finalY1, finalX2, finalY2, boxPaint)
            
            // Draw label with background
            val labelText = "${detection.label} ${String.format("%.0f%%", detection.confidence * 100)}"
            val textPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                this.textSize = textSize
                isAntiAlias = true
                isFakeBoldText = true
            }
            val bgPaint = Paint().apply {
                color = boxColor
                style = Paint.Style.FILL
            }
            
            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(labelText, 0, labelText.length, textBounds)
            
            canvas.drawRect(
                finalX1, finalY1 - textBounds.height() - padding * 2,
                finalX1 + textBounds.width() + padding * 2, finalY1,
                bgPaint
            )
            canvas.drawText(labelText, finalX1 + padding, finalY1 - padding, textPaint)
        }
        
        // Save to file
        val file = File(context.cacheDir, "bbox_image_temp.jpg")
        FileOutputStream(file).use { out ->
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        // Recycle bitmaps
        mutableBitmap.recycle()
        // originalBitmap was already recycled above
        
        return file
    }
    
    /**
     * Generate next sequential report ID (starting from 2).
     * Queries all existing reports to find the highest numeric ID, then increments.
     */
    private suspend fun generateNextReportId(): String {
        return try {
            val snapshot = firestore.collection("reports").get().await()
            val existingIds = snapshot.documents.mapNotNull { doc ->
                doc.getString("id")?.toIntOrNull()
            }
            val nextId = if (existingIds.isEmpty()) 2 else (existingIds.max() + 1)
            nextId.toString()
        } catch (e: Exception) {
            // Fallback: use timestamp-based ID to avoid collisions
            android.util.Log.e("ImageUploadViewModel", "Error generating ID, using fallback", e)
            "RPT-${System.currentTimeMillis()}"
        }
    }
    
    /**
     * Save report to Firestore with image (including bounding boxes) uploaded to Cloudinary
     */
    fun saveReport() {
        val currentUri = _uiState.value.selectedImageUri ?: return
        val detectionResult = _uiState.value.detectionResult ?: return

        if (detectionResult.detections.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "No hazards detected")
            return
        }

        viewModelScope.launch {
            var publicId: String? = null
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                // Use ONLY GPS coordinates from the image's EXIF data
                val coordinates = _uiState.value.locationFromExif

                if (coordinates == null) {
                    // No GPS in image EXIF — cannot save report
                    android.util.Log.d("ImageUploadViewModel", "No GPS data found in image EXIF, rejecting save")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "The image does not contain location data. Please use an image with GPS metadata, or enable location tagging in your camera settings."
                    )
                    return@launch
                }

                android.util.Log.d("ImageUploadViewModel", "Using EXIF coordinates: ${coordinates.latitude}, ${coordinates.longitude}")
                
                // Convert coordinates to address (reverse geocoding)
                val locationAddress = geocodingService.getAddressFromCoordinates(coordinates)
                    ?: "Unknown location"
                
                // Get user info
                val currentUser = firebaseAuth.currentUser
                val userName = currentUser?.displayName ?: currentUser?.email ?: "Anonymous"
                
                // Reuse the bbox image that was already generated for display
                val bboxImageUri = _uiState.value.bboxImageUri
                val bboxImageFile = if (bboxImageUri != null) {
                    File(bboxImageUri.path!!)
                } else {
                    createImageWithBoundingBoxes(currentUri, detectionResult)
                }
                
                val requestBody = bboxImageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("image", bboxImageFile.name, requestBody)
                
                // Call backend to upload to Cloudinary
                val cloudinaryResponse = detectionApiService.uploadToCloudinary(imagePart)
                val imageUrl = cloudinaryResponse.imageUrl
                publicId = cloudinaryResponse.publicId
                
                // Clean up temp file
                bboxImageFile.delete()
                
                // Generate sequential report ID
                val reportId = generateNextReportId()
                
                // Get primary detection (highest confidence)
                val primaryDetection = detectionResult.detections.maxByOrNull { it.confidence }!!
                
                // Create report document
                val reportData = hashMapOf(
                    "id" to reportId,
                    "hazardType" to primaryDetection.label,
                    "location" to locationAddress,
                    "coordinates" to GeoPoint(coordinates.latitude, coordinates.longitude),
                    "date" to SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                    "imageUrl" to imageUrl,
                    "status" to "New",
                    "reportedBy" to userName
                )
                
                // Save to Firestore (use reportId as document ID)
                firestore.collection("reports")
                    .document(reportId)
                    .set(reportData)
                    .await()
                
                // Update state - report saved
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    reportSaved = true,
                    error = null
                )
                
                // Auto-clear after 5 seconds
                delay(5000)
                clearState()
                
            } catch (e: Exception) {
                // Cleanup if image was uploaded but operation failed (orphaned image)
                if (publicId != null) {
                    try {
                        detectionApiService.deleteImage(DeleteImageRequest(publicId!!))
                        android.util.Log.d("ImageUploadViewModel", "Successfully deleted orphaned image: $publicId")
                    } catch (deleteEx: Exception) {
                        android.util.Log.e("ImageUploadViewModel", "Failed to delete orphaned image: $publicId", deleteEx)
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to save report: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Clear all state (reset screen)
     */
    fun clearState() {
        // Clean up temp bbox image file
        _uiState.value.bboxImageUri?.path?.let { File(it).delete() }
        _uiState.value = UploadUiState()
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * UI state for upload screen
 */
data class UploadUiState(
    val selectedImageUri: Uri? = null,
    val isLoading: Boolean = false,
    val detectionResult: DetectionResult? = null,
    val bboxImageUri: Uri? = null,       // Pre-rendered image with bounding boxes drawn
    val error: String? = null,
    val reportSaved: Boolean = false,
    val locationFromExif: LatLng? = null  // GPS extracted from image EXIF
)
