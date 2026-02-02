package com.roadhazard.app.ui.screens.auth

import androidx.credentials.Credential
import com.roadhazard.app.data.GoogleAuthManager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class SignupUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fullName: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    val googleAuthManager: GoogleAuthManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()
    
    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email)
    }
    
    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }
    
    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = confirmPassword)
    }
    
    fun onFullNameChange(fullName: String) {
        _uiState.value = _uiState.value.copy(fullName = fullName)
    }
    
    fun signup(onSuccess: () -> Unit) {
        val state = _uiState.value
        
        // Basic validation
        if (state.email.isBlank() || state.password.isBlank() || state.fullName.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please fill in all fields")
            return
        }

        // Email format validation - stricter regex to catch cases like .com1
        val emailPattern = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.(com|net|org|co\\.il)".toRegex()
        if (!state.email.matches(emailPattern)) {
            _uiState.value = _uiState.value.copy(errorMessage = "Invalid email format")
            return
        }

        // Password strength validation: 8+ chars, upper and lower case
        val passwordPattern = "^(?=.*[a-z])(?=.*[A-Z]).{8,}$".toRegex()
        if (!state.password.matches(passwordPattern)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Password must be at least 8 characters with at least one uppercase and one lowercase letter"
            )
            return
        }

        if (state.password != state.confirmPassword) {
            _uiState.value = _uiState.value.copy(errorMessage = "Passwords do not match")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                // Check if email already exists in Firestore
                val existingUser = firestore.collection("users")
                    .whereEqualTo("email", state.email)
                    .get()
                    .await()
                
                if (!existingUser.isEmpty) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Email is already registered"
                    )
                    return@launch
                }

                // Create user in Firebase Auth
                val result = auth.createUserWithEmailAndPassword(state.email, state.password).await()
                val user = result.user
                
                if (user != null) {
                    // Create user profile in Firestore
                    val userProfile = hashMapOf(
                        "uid" to user.uid,
                        "fullName" to state.fullName,
                        "email" to state.email,
                        "type" to "user",
                        "createdAt" to System.currentTimeMillis()
                    )
                    
                    firestore.collection("users").document(user.uid).set(userProfile).await()
                    
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to create user"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "An error occurred"
                )
            }
        }
    }
    
    fun signupWithGoogle(credential: Credential, onSuccess: () -> Unit) {
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
