package com.roadhazard.app.ui.screens.auth

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "LoginScreen"

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onNavigateToSignup: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity
    val credentialManager = CredentialManager.create(context)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Road Hazard Detection",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true,
            enabled = !uiState.isLoading
        )
        
        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Password") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !uiState.isLoading
        )
        
        TextButton(
            onClick = onNavigateToForgotPassword,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Forgot Password?")
        }
        
        if (uiState.errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        Button(
            onClick = { viewModel.login(onNavigateToHome) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            enabled = !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Login")
            }
        }
        
        OutlinedButton(
            onClick = {
                if (activity == null) {
                    Log.e(TAG, "Activity context is null!")
                    viewModel.setErrorMessage("Cannot start Google Sign-In: No activity context")
                    return@OutlinedButton
                }
                
                scope.launch {
                    Log.d(TAG, "Starting Google Sign-In flow...")
                    try {
                        val request = viewModel.googleAuthManager.getGoogleRequest()
                        Log.d(TAG, "Created credential request, calling getCredential...")
                        
                        // Add timeout of 30 seconds
                        val result = withTimeoutOrNull(30_000L) {
                            credentialManager.getCredential(
                                context = activity,
                                request = request
                            )
                        }
                        
                        if (result == null) {
                            Log.e(TAG, "getCredential() timed out after 30 seconds!")
                            viewModel.setErrorMessage("Google Sign-In timed out. Try using a physical device instead of an emulator, or check that Google Play Services is updated.")
                            return@launch
                        }
                        
                        Log.d(TAG, "Got credential response: ${result.credential.type}")
                        viewModel.loginWithGoogle(result.credential, onNavigateToHome)
                    } catch (e: TimeoutCancellationException) {
                        Log.e(TAG, "Timeout exception: ${e.message}", e)
                        viewModel.setErrorMessage("Google Sign-In timed out. Please try again.")
                    } catch (e: NoCredentialException) {
                        Log.e(TAG, "NoCredentialException: ${e.message}", e)
                        viewModel.setErrorMessage("No Google accounts found. Please add a Google account to your device.")
                    } catch (e: GetCredentialCancellationException) {
                        Log.w(TAG, "User cancelled sign-in: ${e.message}")
                        viewModel.setErrorMessage("Sign-in was cancelled")
                    } catch (e: GetCredentialException) {
                        Log.e(TAG, "GetCredentialException: type=${e.type}, message=${e.message}", e)
                        viewModel.setErrorMessage("Google Sign-In Error: ${e.type} - ${e.message}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected error during sign-in: ${e.javaClass.simpleName} - ${e.message}", e)
                        viewModel.setErrorMessage("An unexpected error occurred: ${e.localizedMessage}")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading
        ) {
            Text("Sign in with Google")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Don't have an account?")
            TextButton(onClick = onNavigateToSignup) {
                Text("Sign Up")
            }
        }
    }
}
