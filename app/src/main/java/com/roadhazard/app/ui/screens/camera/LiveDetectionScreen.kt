package com.roadhazard.app.ui.screens.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executor
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.Quality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDetectionScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var isCameraOpen by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPermissionDeniedMessage by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    
    val permissions = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val cameraGranted = perms[Manifest.permission.CAMERA] == true
        val locationGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                             perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        hasCameraPermission = cameraGranted
        hasLocationPermission = locationGranted
        
        if (cameraGranted && locationGranted) {
            isCameraOpen = true
            showPermissionDeniedMessage = false
        } else {
            // Check if permanently denied
            val activity = context as? Activity
            val shouldShowRationale = permissions.any {
                activity?.let { act ->
                    ActivityCompat.shouldShowRequestPermissionRationale(act, it)
                } ?: false
            }
            
            if (!shouldShowRationale && (!cameraGranted || !locationGranted)) {
                showSettingsDialog = true
            } else {
                showPermissionDeniedMessage = true
            }
        }
    }
    
    fun checkAndRequestPermissions() {
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        
        // Handle location permission more leniently (either FINE or COARSE is OK)
        val effectiveMissing = missingPermissions.filter { perm ->
            when (perm) {
                Manifest.permission.ACCESS_FINE_LOCATION -> {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                }
                else -> true
            }
        }
        
        if (effectiveMissing.isEmpty()) {
            isCameraOpen = true
            showPermissionDeniedMessage = false
        } else {
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
        }
    }
    
    // Permission Rationale Dialog
    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            title = { Text("הרשאות נדרשות") },
            text = { 
                Text("האפליקציה זקוקה להרשאות מצלמה ומיקום כדי לזהות מפגעים בכבישים ולדווח עליהם במיקום המדויק.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationaleDialog = false
                    permissionLauncher.launch(permissions.toTypedArray())
                }) {
                    Text("אשר הרשאות")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showPermissionRationaleDialog = false
                    showPermissionDeniedMessage = true
                }) {
                    Text("ביטול")
                }
            }
        )
    }
    
    // Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("הרשאות נדרשות") },
            text = { 
                Text("נראה שחסמת הרשאות באופן קבוע. אנא עבור להגדרות האפליקציה ואפשר הרשאות מצלמה ומיקום.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text("פתח הגדרות")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showSettingsDialog = false
                    showPermissionDeniedMessage = true
                }) {
                    Text("ביטול")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("זיהוי בזמן אמת") },
                navigationIcon = {
                    IconButton(onClick = {
                        // Stop recording if active
                        recording?.stop()
                        recording = null
                        isRecording = false
                        isCameraOpen = false
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזור")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!isCameraOpen) {
                // Initial state - Show "Open Camera" button
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "זיהוי מפגעים בזמן אמת",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "לחץ על הכפתור למטה כדי לפתוח את המצלמה ולהתחיל לזהות מפגעים בכבישים",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = { checkAndRequestPermissions() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .height(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("פתח מצלמה", style = MaterialTheme.typography.titleMedium)
                    }
                    
                    if (showPermissionDeniedMessage) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Text(
                                text = "⚠️ חובה לאשר הרשאות מצלמה ומיקום כדי להשתמש בתכונה זו",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            } else {
                // Camera is open - Show preview
                CameraPreviewWithRecording(
                    modifier = Modifier.fillMaxSize(),
                    lifecycleOwner = lifecycleOwner,
                    isRecording = isRecording,
                    onRecordingToggle = { shouldRecord ->
                        isRecording = shouldRecord
                    },
                    onRecordingUpdate = { rec ->
                        recording = rec
                    }
                )
                
                // Recording controls overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 48.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    RecordingButton(
                        isRecording = isRecording,
                        onClick = {
                            // Toggle recording state
                            isRecording = !isRecording
                        }
                    )
                }
                
                // Recording indicator
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    Color.Red.copy(alpha = 0.8f),
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color.White, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "מקליט",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreviewWithRecording(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner,
    isRecording: Boolean,
    onRecordingToggle: (Boolean) -> Unit,
    onRecordingUpdate: (Recording?) -> Unit
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var currentRecording by remember { mutableStateOf<Recording?>(null) }
    
    // Handle recording state changes
    LaunchedEffect(isRecording) {
        if (isRecording && currentRecording == null) {
            // Start recording
            videoCapture?.let { capture ->
                val videoFile = File(
                    context.getExternalFilesDir(null),
                    "recording_${System.currentTimeMillis()}.mp4"
                )
                
                val outputOptions = FileOutputOptions.Builder(videoFile).build()
                
                currentRecording = capture.output
                    .prepareRecording(context, outputOptions)
                    .start(ContextCompat.getMainExecutor(context)) { event ->
                        when (event) {
                            is VideoRecordEvent.Start -> {
                                // Recording started
                            }
                            is VideoRecordEvent.Finalize -> {
                                if (!event.hasError()) {
                                    Toast.makeText(
                                        context,
                                        "הקלטה נשמרה: ${videoFile.absolutePath}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "שגיאה בהקלטה",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                currentRecording = null
                                onRecordingUpdate(null)
                            }
                        }
                    }
                onRecordingUpdate(currentRecording)
            }
        } else if (!isRecording && currentRecording != null) {
            // Stop recording
            currentRecording?.stop()
            currentRecording = null
            onRecordingUpdate(null)
        }
    }
    
    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    androidx.camera.video.QualitySelector.from(Quality.HD)
                )
                .build()
            
            videoCapture = VideoCapture.withOutput(recorder)
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "שגיאה בפתיחת המצלמה", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))
        
        onDispose {
            currentRecording?.stop()
            currentRecording = null
            onRecordingUpdate(null)
        }
    }
    
    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

@Composable
fun RecordingButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(72.dp)
    ) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.White, shape = MaterialTheme.shapes.small)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.White, shape = CircleShape)
            )
        }
    }
}
