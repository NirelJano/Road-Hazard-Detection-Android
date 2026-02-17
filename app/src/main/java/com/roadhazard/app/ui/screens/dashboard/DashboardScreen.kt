package com.roadhazard.app.ui.screens.dashboard

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.roadhazard.app.data.model.Report
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import kotlinx.coroutines.delay
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val reports by viewModel.reports.collectAsState()
    val isExpanded by viewModel.isReportsExpanded.collectAsState()
    
    // State for viewing image
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Map Component
        // When expanded: 50% height (weight 1f vs 1f).
        // When collapsed: Fills most space (weight 1f vs fixed small height).
        Box(
            modifier = Modifier
                .weight(1f) 
                .fillMaxWidth()
        ) {
            DashboardMap()
        }

        // 2. Reports Log Component
        // If expanded, use weight(1f) to share space equally.
        // If collapsed, use fixed small height/wrapContent.
        Box(
            modifier = if (isExpanded) {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(60.dp) // Collapsed height (header only)
                    .background(MaterialTheme.colorScheme.surface)
            }
        ) {
            ReportsLog(
                reports = reports,
                isExpanded = isExpanded,
                onToggleExpand = { viewModel.toggleReportsExpansion() },
                onViewImage = { url -> selectedImageUrl = url }
            )
        }
    }
    
    // Image Viewer Dialog
    if (selectedImageUrl != null) {
        ImageViewerDialog(
            imageUrl = selectedImageUrl!!,
            onDismiss = { selectedImageUrl = null }
        )
    }
}

// Set to true when valid Google Maps API Key is added to AndroidManifest.xml
private const val SHOW_MAP = false

@SuppressLint("UnrememberedMutableState")
@Composable
fun DashboardMap() {
    if (!SHOW_MAP) {
        // Placeholder when map is disabled
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Map Disabled", style = MaterialTheme.typography.titleMedium)
                Text("Add API Key to enable", style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }

    // FIX: Delay map loading to avoid "LayoutNode attached" crash during navigation transition
    var isMapReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100) // Wait for transition animation to stabilize
        isMapReady = true
    }

    if (!isMapReady) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        // Center on Israel: 31.0461° N, 34.8516° E
        val israel = LatLng(31.0461, 34.8516)
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(israel, 7.5f)
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            // Optional: Add some dummy markers for visualization
            Marker(
                state = MarkerState(position = LatLng(32.0853, 34.7818)),
                title = "Tel Aviv",
                snippet = "Pothole detected"
            )
            Marker(
                state = MarkerState(position = LatLng(31.7683, 35.2137)),
                title = "Jerusalem",
                snippet = "Road blocked"
            )
        }
    }
}

@Composable
fun ReportsLog(
    reports: List<Report>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onViewImage: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header / Title Row with Toggle Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Reports Log (${reports.size})",
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(onClick = onToggleExpand) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (isExpanded) "Minimize" else "Expand"
                )
            }
        }

        if (isExpanded) {
            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp)
            ) {
                HeaderCell("ID", 0.1f)
                HeaderCell("Type", 0.15f)
                HeaderCell("Loc", 0.15f)
                HeaderCell("Date", 0.15f)
                HeaderCell("Img", 0.1f)
                HeaderCell("Status", 0.15f)
                HeaderCell("By", 0.2f)
            }

            // List
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(reports) { report ->
                    ReportRow(report, onViewImage)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(weight)
    )
}

@Composable
fun ReportRow(
    report: Report, 
    onViewImage: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = report.id, modifier = Modifier.weight(0.1f), style = MaterialTheme.typography.bodyMedium)
        Text(text = report.hazardType, modifier = Modifier.weight(0.15f), style = MaterialTheme.typography.bodyMedium)
        Text(text = report.location, modifier = Modifier.weight(0.15f), style = MaterialTheme.typography.bodyMedium)
        Text(text = report.date, modifier = Modifier.weight(0.15f), style = MaterialTheme.typography.bodyMedium)
        
        // Image Indicator
        Box(modifier = Modifier.weight(0.1f)) {
            if (report.imageUrl != null) {
                Text(
                    text = "View", 
                    color = Color.Blue, 
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onViewImage(report.imageUrl) }
                )
            } else {
                Text("No", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Status with simple coloring
        val statusColor = when(report.status) {
            "Open", "New" -> Color.Red
            "In Progress" -> Color(0xFFFFA500) // Orange
            "Closed" -> Color.Green
            else -> Color.Gray
        }
        Text(
            text = report.status, 
            modifier = Modifier.weight(0.15f), 
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor
        )
        
        Text(text = report.reportedBy, modifier = Modifier.weight(0.2f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ImageViewerDialog(imageUrl: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(Color.Black, shape = RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
            
            AsyncImage(
                model = imageUrl,
                contentDescription = "Report Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, bottom = 16.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}
