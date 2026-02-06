package com.roadhazard.app.ui.screens.upload

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import java.io.File
import java.io.InputStream
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showLocationMissingDialog by remember { mutableStateOf(false) }

    // Prepare permission list based on API level
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    fun hasLocationMetadata(uri: Uri): Boolean {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val exif = ExifInterface(inputStream)
                val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
                val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
                val long = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
                val longRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
                inputStream.close()
                lat != null && latRef != null && long != null && longRef != null
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            // Check metadata
            if (hasLocationMetadata(tempCameraUri!!)) {
                imageUri = tempCameraUri
            } else {
                showLocationMissingDialog = true
                // Do not keep the image if it's invalid
                // Optional: imageUri = null 
            }
        }
    }

    // Photo picker launcher (Gallery)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
             if (hasLocationMetadata(uri)) {
                imageUri = uri
            } else {
                showLocationMissingDialog = true
            }
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        // Logic reused from previous step, but simplified since we check more leniently now
        // And we handle permanent denial via check below or manual check
        
        // Re-check all required permissions
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        
        // Refined check for partial permissions (Coarse / Visual)
         val effectiveMissing = missingPermissions.filter { perm ->
            when (perm) {
                Manifest.permission.ACCESS_FINE_LOCATION -> {
                     ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                }
                Manifest.permission.READ_MEDIA_IMAGES -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                         ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) != PackageManager.PERMISSION_GRANTED
                    } else {
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU // If missing < 13, it's missing.
                    }
                }
                else -> true
            }
        }

        if (effectiveMissing.isEmpty()) {
            Toast.makeText(context, "Permissions granted! Try again.", Toast.LENGTH_SHORT).show()
        } else {
             // Check for permanent denial
             val activity = context as? Activity
             val shouldShowRationale = effectiveMissing.any {
                 activity?.let { act ->
                     ActivityCompat.shouldShowRequestPermissionRationale(act, it)
                 } ?: false
             }
             
             if (!shouldShowRationale) {
                 showSettingsDialog = true
             } else {
                 showPermissionRationaleDialog = true
             }
        }
    }

    fun checkPermissionsAndAction(action: () -> Unit) {
         val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        
        // Refined check
        val effectiveMissing = missingPermissions.filter { perm ->
            when (perm) {
                Manifest.permission.ACCESS_FINE_LOCATION -> {
                     ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                }
                Manifest.permission.READ_MEDIA_IMAGES -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                         ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) != PackageManager.PERMISSION_GRANTED
                    } else {
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                    }
                }
                else -> true
            }
        }

        if (effectiveMissing.isNotEmpty()) {
            val activity = context as? Activity
            val shouldShowRationale = effectiveMissing.any {
                activity?.let { act ->
                    ActivityCompat.shouldShowRequestPermissionRationale(act, it)
                } ?: false
            }
            
            if (shouldShowRationale) {
                showPermissionRationaleDialog = true
            } else {
                permissionLauncher.launch(effectiveMissing.toTypedArray())
            }
        } else {
            action()
        }
    }

    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            title = { Text("Permissions Required") },
            text = { Text("This app needs Camera, Location, and Photos permissions to report road hazards effectively. Please grant them to continue.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationaleDialog = false
                    // We need to launch the launcher. But we don't have access to "effectiveMissing" here easily without re-calculating or capturing.
                    // We'll just re-launch the full set defined in permissions, the system handles overlap.
                    permissionLauncher.launch(permissions.toTypedArray())
                }) {
                    Text("Grant")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationaleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Permissions Required") },
            text = { Text("It seems you have permanently denied some permissions. Please go to App Settings and enable permissions manually.") },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLocationMissingDialog) {
         AlertDialog(
            onDismissRequest = { showLocationMissingDialog = false },
            title = { Text("Location Missing") },
            text = { Text("The selected image does not contain location data (GPS coordinates). Please ensure 'Save location' is enabled in your Camera app settings and try again.") },
            confirmButton = {
                TextButton(onClick = {
                    showLocationMissingDialog = false
                }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Hazard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            
            // Image Preview Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Selected Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.height(64.dp).fillMaxWidth(), 
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("No image selected", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        checkPermissionsAndAction {
                             val uri = composeFileProviderUri(context)
                             tempCameraUri = uri
                             cameraLauncher.launch(uri)
                        }
                    }
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Camera")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        checkPermissionsAndAction {
                             photoPickerLauncher.launch(
                                 PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                             )
                        }
                    }
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Gallery")
                }
            }
            
            if (imageUri != null) {
                Spacer(modifier = Modifier.height(16.dp))
                // Placeholder for future analysis
                Text(
                    text = "Image ready for analysis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// Helper to create a temp file and get URI
private fun composeFileProviderUri(context: Context): Uri {
    val directory = File(context.cacheDir, "images")
    directory.mkdirs()
    val file = File.createTempFile(
        "selected_image_",
        ".jpg",
        directory
    )
    val authority = context.packageName + ".fileprovider"
    return FileProvider.getUriForFile(
        context,
        authority,
        file
    )
}
