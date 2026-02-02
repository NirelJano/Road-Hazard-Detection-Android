package com.roadhazard.app.data

import android.content.Context
import android.util.Log
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.roadhazard.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GoogleAuthManager"

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth
) {
    private val webClientId by lazy {
        context.getString(R.string.web_client_id)
    }

    fun getGoogleRequest(): GetCredentialRequest {
        Log.d(TAG, "Creating Google request with webClientId: $webClientId")
        
        // Use GetSignInWithGoogleOption for the "Sign in with Google" button flow
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
            .build()

        return GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()
    }

    suspend fun signInWithGoogle(credential: androidx.credentials.Credential): AuthResult {
        Log.d(TAG, "Processing credential of type: ${credential.type}")
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        Log.d(TAG, "Got Google ID token, signing in with Firebase...")
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
        return auth.signInWithCredential(firebaseCredential).await()
    }
}
