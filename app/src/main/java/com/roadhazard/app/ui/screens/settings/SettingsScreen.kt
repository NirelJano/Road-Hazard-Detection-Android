package com.roadhazard.app.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChangePassword: () -> Unit
) {
    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf<String?>(null) } // "Camera" or "Location" or "Photos" or null
    
    // Determine strict media permission based on API level
    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // Check initial permission states
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var hasMediaPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, mediaPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    fun checkPermissions() {
        hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasMediaPermission = ContextCompat.checkSelfPermission(context, mediaPermission) == PackageManager.PERMISSION_GRANTED
    }

    // Refresh permissions when app resumes (e.g. returning from Settings)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        checkPermissions()
    }

    // Permission Launchers
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (!isGranted) {
             val activity = context as? Activity
             val shouldShowRationale = activity?.let { 
                 ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION) 
             } ?: false
             
             if (!shouldShowRationale) {
                 showSettingsDialog = "Location"
             }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
             val activity = context as? Activity
             val shouldShowRationale = activity?.let { 
                 ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) 
             } ?: false
             
             if (!shouldShowRationale) {
                 showSettingsDialog = "Camera"
             }
        }
    }
    
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMediaPermission = isGranted
        if (!isGranted) {
             val activity = context as? Activity
             val shouldShowRationale = activity?.let { 
                 ActivityCompat.shouldShowRequestPermissionRationale(it, mediaPermission) 
             } ?: false
             
             if (!shouldShowRationale) {
                 showSettingsDialog = "Photos"
             }
        }
    }
    
    if (showSettingsDialog != null) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = null },
            title = { Text("Permission Required") },
            text = { Text("The ${showSettingsDialog} permission is currently permanently denied. Please enable it in the App Settings.") },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialog = null
                    openAppSettings(context)
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Location Permission Toggle
            SettingsSwitchItem(
                title = "Location Permission",
                description = "Required for reporting hazards with location",
                checked = hasLocationPermission,
                onCheckedChange = { checked ->
                    if (checked) {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    } else {
                        openAppSettings(context)
                    }
                }
            )
            
            HorizontalDivider()

            // Camera Permission Toggle
            SettingsSwitchItem(
                title = "Camera Permission",
                description = "Required for taking photos of hazards",
                checked = hasCameraPermission,
                onCheckedChange = { checked ->
                    if (checked) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        openAppSettings(context)
                    }
                }
            )
            
            HorizontalDivider()
            
            // Media/Photos Permission Toggle
            SettingsSwitchItem(
                title = "Photos Access",
                description = "Required for uploading photos from gallery",
                checked = hasMediaPermission,
                onCheckedChange = { checked ->
                    if (checked) {
                        mediaPermissionLauncher.launch(mediaPermission)
                    } else {
                        openAppSettings(context)
                    }
                }
            )
            
            HorizontalDivider()
            
            Spacer(modifier = Modifier.height(16.dp))

            // Change Password Button
            Button(
                onClick = onNavigateToChangePassword,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Change Password")
            }
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}
