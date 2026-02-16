package com.roadhazard.app.ui.screens.upload

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.roadhazard.app.data.api.DetectionApiService
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
    
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    
    /**
     * Upload image to backend for AI inference
     */
    fun uploadImage(uri: Uri, isFromCamera: Boolean) {
        viewModelScope.launch {
            try {
                // Try to extract GPS from EXIF (best-effort, won't block upload)
                val exifCoordinates = extractGpsFromExif(uri)
                android.util.Log.d("ImageUploadViewModel", "EXIF GPS result: $exifCoordinates, isFromCamera: $isFromCamera")
                
                _uiState.value = _uiState.value.copy(
                    selectedImageUri = uri,
                    isLoading = true,
                    error = null,
                    detectionResult = null,
                    isFromCamera = isFromCamera,
                    locationFromExif = exifCoordinates  // May be null - that's OK
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
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    detectionResult = result,
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
        // Try with setRequireOriginal first (for local Photo Picker on Q+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val originalUri = MediaStore.setRequireOriginal(uri)
                val coords = getGpsFromStream(originalUri)
                if (coords != null) return coords
            } catch (e: Exception) {
                android.util.Log.d("ImageUploadViewModel", "setRequireOriginal not supported for this URI, trying raw URI")
            }
        }
        
        // Fall back to raw URI
        return getGpsFromStream(uri)
    }
    
    /**
     * Read GPS from an InputStream opened from a URI
     */
    private fun getGpsFromStream(uri: Uri): LatLng? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val exif = ExifInterface(inputStream)
                val latLong = FloatArray(2)
                val hasLatLong = exif.getLatLong(latLong)
                inputStream.close()
                
                if (hasLatLong) {
                    android.util.Log.d("ImageUploadViewModel", "GPS found: ${latLong[0]}, ${latLong[1]}")
                    LatLng(latLong[0].toDouble(), latLong[1].toDouble())
                } else {
                    android.util.Log.d("ImageUploadViewModel", "GPS not found in EXIF for URI: $uri")
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.d("ImageUploadViewModel", "Error reading EXIF for URI: $uri - ${e.message}")
            null
        }
    }
    
    /**
     * Get device's current location using FusedLocationProviderClient.
     * Returns null if location permission is not granted or location unavailable.
     */
    @SuppressLint("MissingPermission")
    private suspend fun getDeviceLocation(): LatLng? {
        return try {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasPermission) {
                android.util.Log.d("ImageUploadViewModel", "No location permission for device fallback")
                return null
            }
            
            val cancellationToken = CancellationTokenSource()
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).await()
            
            if (location != null) {
                android.util.Log.d("ImageUploadViewModel", "Device location: ${location.latitude}, ${location.longitude}")
                LatLng(location.latitude, location.longitude)
            } else {
                // Try last known location as final fallback
                val lastLocation = fusedLocationClient.lastLocation.await()
                if (lastLocation != null) {
                    android.util.Log.d("ImageUploadViewModel", "Last known location: ${lastLocation.latitude}, ${lastLocation.longitude}")
                    LatLng(lastLocation.latitude, lastLocation.longitude)
                } else {
                    android.util.Log.e("ImageUploadViewModel", "No device location available")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageUploadViewModel", "Error getting device location", e)
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
        
        // Create a mutable copy to draw on
        val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        // Scale text/stroke proportionally to image size
        val scaleFactor = maxOf(mutableBitmap.width, mutableBitmap.height) / 1000f
        val strokeWidth = 4f * scaleFactor
        val textSize = 28f * scaleFactor
        val padding = 6f * scaleFactor
        
        // Draw each detection
        detectionResult.detections.forEach { detection ->
            val (x1, y1, x2, y2) = detection.bbox
            
            // Choose color based on hazard type
            val boxColor = when (detection.label.lowercase()) {
                "pothole" -> android.graphics.Color.RED
                "crack" -> android.graphics.Color.YELLOW
                else -> android.graphics.Color.CYAN
            }
            
            // Draw bounding box
            val boxPaint = Paint().apply {
                color = boxColor
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                isAntiAlias = true
            }
            canvas.drawRect(x1, y1, x2, y2, boxPaint)
            
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
                x1, y1 - textBounds.height() - padding * 2,
                x1 + textBounds.width() + padding * 2, y1,
                bgPaint
            )
            canvas.drawText(labelText, x1 + padding, y1 - padding, textPaint)
        }
        
        // Save to file
        val file = File(context.cacheDir, "bbox_image_temp.jpg")
        FileOutputStream(file).use { out ->
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        // Recycle bitmaps
        originalBitmap.recycle()
        mutableBitmap.recycle()
        
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
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                // Get GPS coordinates based on image source
                var coordinates = _uiState.value.locationFromExif
                
                if (coordinates == null && _uiState.value.isFromCamera) {
                    // Camera: user is at the hazard right now, use device GPS
                    android.util.Log.d("ImageUploadViewModel", "Camera image without EXIF GPS, using device location...")
                    coordinates = getDeviceLocation()
                }
                
                if (coordinates == null) {
                    val errorMsg = if (_uiState.value.isFromCamera) {
                        "Could not determine location. Please enable location services and try again."
                    } else {
                        "The selected image does not contain GPS coordinates. Please use an image with location metadata."
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                    return@launch
                }
                
                android.util.Log.d("ImageUploadViewModel", "Using coordinates: ${coordinates.latitude}, ${coordinates.longitude}")
                
                // Convert coordinates to address (reverse geocoding)
                val locationAddress = geocodingService.getAddressFromCoordinates(coordinates)
                    ?: "Unknown location"
                
                // Get user info
                val currentUser = firebaseAuth.currentUser
                val userName = currentUser?.displayName ?: currentUser?.email ?: "Anonymous"
                
                // Create image with bounding boxes drawn on it
                val bboxImageFile = createImageWithBoundingBoxes(currentUri, detectionResult)
                
                val requestBody = bboxImageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("image", bboxImageFile.name, requestBody)
                
                // Call backend to upload to Cloudinary
                val cloudinaryResponse = detectionApiService.uploadToCloudinary(imagePart)
                val imageUrl = cloudinaryResponse.imageUrl
                
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
    val error: String? = null,
    val reportSaved: Boolean = false,
    val isFromCamera: Boolean = false,
    val locationFromExif: LatLng? = null  // GPS extracted from image EXIF
)
