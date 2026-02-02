package com.roadhazard.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val isEmailSent: Boolean = false,
    val errorMessage: String? = null,
    val emailSuggestion: String? = null
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()
    
    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, emailSuggestion = null)
    }
    
    fun resetPassword() {
        val email = _uiState.value.email
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter your email")
            return
        }
        
        // Use EmailValidator from SignupViewModel
        val emailValidation = EmailValidator.validate(email)
        if (!emailValidation.isValid) {
            _uiState.value = _uiState.value.copy(
                errorMessage = emailValidation.errorMessage,
                emailSuggestion = emailValidation.suggestion
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                auth.sendPasswordResetEmail(email).await()
                _uiState.value = _uiState.value.copy(isLoading = false, isEmailSent = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Failed to send reset email"
                )
            }
        }
    }
    
    fun acceptEmailSuggestion() {
        val suggestion = _uiState.value.emailSuggestion ?: return
        _uiState.value = _uiState.value.copy(
            email = suggestion,
            errorMessage = null,
            emailSuggestion = null
        )
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null, emailSuggestion = null)
    }
}
