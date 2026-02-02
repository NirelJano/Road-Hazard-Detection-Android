package com.roadhazard.app.ui.screens.auth

import androidx.credentials.Credential
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.roadhazard.app.data.GoogleAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    val googleAuthManager: GoogleAuthManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email)
    }
    
    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }
    
    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please fill in all fields")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                auth.signInWithEmailAndPassword(state.email, state.password).await()
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Login failed"
                )
            }
        }
    }
    
    // Legacy Google Sign-In method
    fun loginWithGoogleLegacy(task: Task<GoogleSignInAccount>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                val result = googleAuthManager.handleSignInResult(task)
                val user = result.user
                
                if (user != null) {
                    // Check if profile exists, if not create one
                    val doc = firestore.collection("users").document(user.uid).get().await()
                    if (!doc.exists()) {
                        val userProfile = hashMapOf(
                            "uid" to user.uid,
                            "fullName" to (user.displayName ?: "Google User"),
                            "email" to (user.email ?: ""),
                            "type" to "user",
                            "createdAt" to System.currentTimeMillis()
                        )
                        firestore.collection("users").document(user.uid).set(userProfile).await()
                    }
                    
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Google sign-in failed: User is null"
                    )
                }
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Google sign-in failed (code: ${e.statusCode})"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Google sign-in failed"
                )
            }
        }
    }
    
    // Credential Manager approach (kept for future use)
    fun loginWithGoogle(credential: Credential, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                val result = googleAuthManager.signInWithGoogle(credential)
                val user = result.user
                
                if (user != null) {
                    // Check if profile exists, if not create one
                    val doc = firestore.collection("users").document(user.uid).get().await()
                    if (!doc.exists()) {
                        val userProfile = hashMapOf(
                            "uid" to user.uid,
                            "fullName" to (user.displayName ?: "Google User"),
                            "email" to (user.email ?: ""),
                            "type" to "user",
                            "createdAt" to System.currentTimeMillis()
                        )
                        firestore.collection("users").document(user.uid).set(userProfile).await()
                    }
                    
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Google sign-in failed: User is null"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Google sign-in failed"
                )
            }
        }
    }
    
    fun setErrorMessage(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
