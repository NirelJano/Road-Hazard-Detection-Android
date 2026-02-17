package com.roadhazard.app.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.roadhazard.app.data.model.Report
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun getReports(): Flow<List<Report>> = callbackFlow {
        val collection = firestore.collection("reports")
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
        
        val subscription = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            
            if (snapshot != null) {
                val reports = snapshot.documents.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        
                        // Map fields exactly as shown in the user's Firestore screenshot
                        val geoPoint = data["coordinates"] as? com.google.firebase.firestore.GeoPoint
                        val coordinates = if (geoPoint != null) LatLng(geoPoint.latitude, geoPoint.longitude) else null

                        Report(
                            id = data["id"] as? String ?: doc.id, // Use field 'id' if present, else doc ID
                            hazardType = data["hazardType"] as? String ?: "Unknown",
                            location = data["location"] as? String ?: "Unknown Location",
                            coordinates = coordinates,
                            date = data["date"] as? String ?: "",
                            imageUrl = data["imageUrl"] as? String,
                            status = data["status"] as? String ?: "Open",
                            reportedBy = data["reportedBy"] as? String ?: "Anonymous"
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
                trySend(reports)
            }
        }
        
        awaitClose { subscription.remove() }
    }
}
