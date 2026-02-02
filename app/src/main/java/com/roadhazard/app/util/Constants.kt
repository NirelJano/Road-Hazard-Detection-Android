package com.roadhazard.app.util

object Constants {
    // Google Sign-In
    const val GOOGLE_WEB_CLIENT_ID = "747672611153-oroc8abtgbevc7l74m5dbnrg215drrp2.apps.googleusercontent.com"
    
    // Database
    const val DATABASE_NAME = "road_hazard_db"
    const val DATABASE_VERSION = 1
    
    // Shared Preferences
    const val PREFS_NAME = "road_hazard_prefs"
    const val KEY_USER_ID = "user_id"
    const val KEY_IS_LOGGED_IN = "is_logged_in"
    
    // API Endpoints (placeholder)
    const val BASE_URL = "https://api.roadhazard.com/"
    
    // Camera & ML
    const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    const val LOCATION_PERMISSION_REQUEST_CODE = 1002
    const val MIN_DETECTION_CONFIDENCE = 0.7f
    
    // WorkManager
    const val SYNC_WORK_TAG = "sync_hazards"
    const val UPLOAD_WORK_TAG = "upload_hazard"
    
    // Navigation Arguments
    const val ARG_HAZARD_ID = "hazard_id"
    const val ARG_USER_EMAIL = "user_email"
}
