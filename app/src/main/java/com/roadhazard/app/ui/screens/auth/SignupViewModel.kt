package com.roadhazard.app.ui.screens.auth

import androidx.credentials.Credential
import com.roadhazard.app.data.GoogleAuthManager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Advanced email validation utility
 */
object EmailValidator {
    // Popular email domains with common typos
    private val domainTypoMap = mapOf(
        // Gmail typos
        "gnail.com" to "gmail.com",
        "gmial.com" to "gmail.com",
        "gmai.com" to "gmail.com",
        "gamil.com" to "gmail.com",
        "gmaill.com" to "gmail.com",
        "gmil.com" to "gmail.com",
        "gemail.com" to "gmail.com",
        "g-mail.com" to "gmail.com",
        "gmal.com" to "gmail.com",
        "gmale.com" to "gmail.com",
        "gmaol.com" to "gmail.com",
        "gmaiil.com" to "gmail.com",
        "gimail.com" to "gmail.com",
        "fmail.com" to "gmail.com",
        // Yahoo typos
        "yaho.com" to "yahoo.com",
        "yahooo.com" to "yahoo.com",
        "yaoo.com" to "yahoo.com",
        "yhaoo.com" to "yahoo.com",
        "yhoo.com" to "yahoo.com",
        // Hotmail typos
        "hotmal.com" to "hotmail.com",
        "hotmial.com" to "hotmail.com",
        "hotmil.com" to "hotmail.com",
        "hotmaill.com" to "hotmail.com",
        "hotnail.com" to "hotmail.com",
        // Outlook typos
        "outloo.com" to "outlook.com",
        "outlok.com" to "outlook.com",
        "outlookk.com" to "outlook.com",
        // iCloud typos
        "iclous.com" to "icloud.com",
        "iclod.com" to "icloud.com",
        "icoud.com" to "icloud.com",
        // Walla typos (Israeli)
        "wala.co.il" to "walla.co.il",
        "wallaa.co.il" to "walla.co.il",
    )

    // Disposable/temporary email domains to block
    private val disposableDomains = setOf(
        "tempmail.com", "temp-mail.org", "guerrillamail.com", "10minutemail.com",
        "mailinator.com", "fakeinbox.com", "throwawaymail.com", "yopmail.com",
        "tempail.com", "tempsky.com", "dispostable.com", "getnada.com",
        "maildrop.cc", "trashmail.com", "spamgourmet.com", "mytrashmail.com",
        "sharklasers.com", "guerrillamailblock.com", "pokemail.net", "spam4.me",
        "tempmailaddress.com", "emailondeck.com", "fakemailgenerator.com"
    )

    // Valid TLDs for email addresses
    private val validTlds = setOf(
        "com", "net", "org", "edu", "gov", "io", "co", "info", "biz",
        "co.il", "org.il", "ac.il", "gov.il", "muni.il", "idf.il",
        "me", "app", "dev", "tech"
    )

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val suggestion: String? = null
    )

    fun validate(email: String): ValidationResult {
        val trimmedEmail = email.trim().lowercase()

        // Check if empty
        if (trimmedEmail.isBlank()) {
            return ValidationResult(false, "Please enter an email address")
        }

        // Basic format check
        val basicEmailPattern = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(\\.[a-zA-Z]{2,})?".toRegex()
        if (!trimmedEmail.matches(basicEmailPattern)) {
            return ValidationResult(false, "Invalid email format")
        }

        // Extract domain
        val atIndex = trimmedEmail.indexOf('@')
        if (atIndex == -1 || atIndex == trimmedEmail.length - 1) {
            return ValidationResult(false, "Invalid email format")
        }
        val domain = trimmedEmail.substring(atIndex + 1)

        // Check for disposable email
        if (disposableDomains.contains(domain)) {
            return ValidationResult(false, "Cannot register with a temporary email. Please use a real email address")
        }

        // Check for common typos and suggest correction
        val suggestedDomain = domainTypoMap[domain]
        if (suggestedDomain != null) {
            val correctedEmail = trimmedEmail.substring(0, atIndex + 1) + suggestedDomain
            return ValidationResult(
                false, 
                "Did you mean $correctedEmail?",
                correctedEmail
            )
        }

        // Check if TLD is valid (extract TLD from domain)
        val tldValid = validTlds.any { tld ->
            domain.endsWith(".$tld") || domain == tld
        }
        if (!tldValid) {
            return ValidationResult(false, "Domain extension doesn't look valid. Please check your email address")
        }

        // Check for double dots or other issues
        if (domain.contains("..") || trimmedEmail.contains("..")) {
            return ValidationResult(false, "Invalid email format - double dots detected")
        }

        return ValidationResult(true)
    }
}

data class SignupUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fullName: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val emailSuggestion: String? = null
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

        // Advanced email validation
        val emailValidation = EmailValidator.validate(state.email)
        if (!emailValidation.isValid) {
            _uiState.value = _uiState.value.copy(
                errorMessage = emailValidation.errorMessage,
                emailSuggestion = emailValidation.suggestion
            )
            return
        }

        // Password strength validation: 8+ chars, upper and lower case
        val passwordPattern = "^(?=.*[a-z])(?=.*[A-Z]).{8,}$".toRegex()
        if (!state.password.matches(passwordPattern)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Password must be at least 8 characters with uppercase and lowercase letters"
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
                        errorMessage = "This email is already registered"
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
    
    // Legacy Google Sign-In method
    fun signupWithGoogleLegacy(task: Task<GoogleSignInAccount>, onSuccess: () -> Unit) {
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
    
    fun setErrorMessage(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null, emailSuggestion = null)
    }

    fun acceptEmailSuggestion() {
        val suggestion = _uiState.value.emailSuggestion ?: return
        _uiState.value = _uiState.value.copy(
            email = suggestion,
            errorMessage = null,
            emailSuggestion = null
        )
    }
}
