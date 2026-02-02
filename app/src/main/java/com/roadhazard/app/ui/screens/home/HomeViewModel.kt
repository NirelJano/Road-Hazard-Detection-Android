package com.roadhazard.app.ui.screens.home

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {
    
    val currentUserEmail: String?
        get() = auth.currentUser?.email
    
    val currentUserName: String?
        get() = auth.currentUser?.displayName
    
    fun logout(onLogoutComplete: () -> Unit) {
        auth.signOut()
        onLogoutComplete()
    }
}
