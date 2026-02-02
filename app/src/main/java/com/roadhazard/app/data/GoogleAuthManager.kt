package com.roadhazard.app.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
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

    // Legacy Google Sign-In Client (more reliable on emulators)
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    // Get Intent for legacy Google Sign-In
    fun getSignInIntent(): Intent {
        Log.d(TAG, "Creating legacy Google Sign-In intent")
        return googleSignInClient.signInIntent
    }

    // Handle result from legacy Google Sign-In
    suspend fun handleSignInResult(task: Task<GoogleSignInAccount>): AuthResult {
        return try {
            val account = task.getResult(ApiException::class.java)
            Log.d(TAG, "Google Sign-In successful, email: ${account.email}")
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential).await()
        } catch (e: ApiException) {
            Log.e(TAG, "Google Sign-In failed with code: ${e.statusCode}", e)
            throw e
        }
    }

    // Parse result from Activity result
    fun getSignedInAccountFromIntent(data: Intent?): Task<GoogleSignInAccount> {
        return GoogleSignIn.getSignedInAccountFromIntent(data)
    }

    // For Credential Manager approach (kept as backup)
    fun getGoogleRequest(): GetCredentialRequest {
        Log.d(TAG, "Creating Google request with webClientId: $webClientId")
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

    // Sign out
    fun signOut() {
        googleSignInClient.signOut()
        auth.signOut()
    }
}
