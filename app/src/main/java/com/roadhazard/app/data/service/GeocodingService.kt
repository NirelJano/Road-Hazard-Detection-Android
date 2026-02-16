package com.roadhazard.app.data.service

import android.content.Context
import android.location.Geocoder
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/**
 * Service for geocoding operations - converting between addresses and coordinates
 */
class GeocodingService @Inject constructor(
    private val context: Context
) {
    private val geocoder = Geocoder(context, Locale.getDefault())
    
    /**
     * Convert address string to GPS coordinates
     * @param address The address to geocode
     * @return LatLng coordinates or null if geocoding fails
     */
    suspend fun getCoordinatesFromAddress(address: String): LatLng? = withContext(Dispatchers.IO) {
        try {
            if (address.isBlank()) return@withContext null
            
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(address, 1)
            addresses?.firstOrNull()?.let {
                LatLng(it.latitude, it.longitude)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Convert GPS coordinates to address string (reverse geocoding)
     * @param latLng The coordinates to reverse geocode
     * @return Address string or null if reverse geocoding fails
     */
    suspend fun getAddressFromCoordinates(latLng: LatLng): String? = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            addresses?.firstOrNull()?.let { address ->
                // Try to get a concise address (street + city)
                val street = address.thoroughfare
                val city = address.locality
                
                when {
                    street != null && city != null -> "$street, $city"
                    else -> address.getAddressLine(0) // Full address line
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
