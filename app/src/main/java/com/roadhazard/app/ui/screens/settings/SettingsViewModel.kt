package com.roadhazard.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _changePasswordState = MutableStateFlow<ChangePasswordState>(ChangePasswordState.Idle)
    val changePasswordState: StateFlow<ChangePasswordState> = _changePasswordState.asStateFlow()

    fun resetState() {
        _changePasswordState.value = ChangePasswordState.Idle
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        val user = auth.currentUser
        if (user == null || user.email == null) {
            _changePasswordState.value = ChangePasswordState.Error("User not logged in")
            return
        }

        viewModelScope.launch {
            _changePasswordState.value = ChangePasswordState.Loading
            try {
                // 1. Re-authenticate
                val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
                user.reauthenticate(credential).await()

                // 2. Update Password
                user.updatePassword(newPassword).await()

                _changePasswordState.value = ChangePasswordState.Success
            } catch (e: Exception) {
                // Handle specific Firebase errors if needed (e.g., wrong password)
                _changePasswordState.value = ChangePasswordState.Error(e.message ?: "Failed to update password")
            }
        }
    }
}

sealed class ChangePasswordState {
    data object Idle : ChangePasswordState()
    data object Loading : ChangePasswordState()
    data object Success : ChangePasswordState()
    data class Error(val message: String) : ChangePasswordState()
}
