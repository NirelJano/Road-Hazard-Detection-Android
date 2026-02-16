package com.roadhazard.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun LocationInputDialog(
    onDismiss: () -> Unit,
    onAddressSubmit: (String) -> Unit,
    onCoordinatesSubmit: (Double, Double) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var addressInput by remember { mutableStateOf("") }
    var latitudeInput by remember { mutableStateOf("") }
    var longitudeInput by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
        title = { Text("Add Location") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tab selector
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Address") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Coordinates") }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                when (selectedTab) {
                    0 -> {
                        // Address input
                        OutlinedTextField(
                            value = addressInput,
                            onValueChange = { addressInput = it },
                            label = { Text("Address") },
                            placeholder = { Text("e.g., Pardes Hanna, Derech HaBanim") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 3
                        )
                    }
                    1 -> {
                        // Coordinates input
                        OutlinedTextField(
                            value = latitudeInput,
                            onValueChange = { latitudeInput = it },
                            label = { Text("Latitude") },
                            placeholder = { Text("e.g., 32.4668") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = longitudeInput,
                            onValueChange = { longitudeInput = it },
                            label = { Text("Longitude") },
                            placeholder = { Text("e.g., 34.9235") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (selectedTab) {
                        0 -> {
                            if (addressInput.isNotBlank()) {
                                onAddressSubmit(addressInput)
                            }
                        }
                        1 -> {
                            val lat = latitudeInput.toDoubleOrNull()
                            val lng = longitudeInput.toDoubleOrNull()
                            if (lat != null && lng != null) {
                                onCoordinatesSubmit(lat, lng)
                            }
                        }
                    }
                },
                enabled = when (selectedTab) {
                    0 -> addressInput.isNotBlank()
                    1 -> latitudeInput.toDoubleOrNull() != null && longitudeInput.toDoubleOrNull() != null
                    else -> false
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
