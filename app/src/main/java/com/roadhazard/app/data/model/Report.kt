package com.roadhazard.app.data.model

import com.google.android.gms.maps.model.LatLng

data class Report(
    val id: String,
    val hazardType: String,
    val location: String, // You might want LatLng here later, but String for display for now
    val coordinates: LatLng?, // For storing exact location, not displayed in log
    val date: String,
    val imageUrl: String?, // Nullable if no image
    val status: String,
    val reportedBy: String
)
