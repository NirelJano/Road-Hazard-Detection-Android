package com.roadhazard.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.roadhazard.app.ui.screens.auth.ForgotPasswordScreen
import com.roadhazard.app.ui.screens.auth.LoginScreen
import com.roadhazard.app.ui.screens.auth.SignupScreen
import com.roadhazard.app.ui.screens.home.HomeScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Authentication Flow
        composable(route = Screen.Login.route) {
            LoginScreen(
                onNavigateToSignup = {
                    navController.navigate(Screen.Signup.route)
                },
                onNavigateToForgotPassword = {
                    navController.navigate(Screen.ForgotPassword.route)
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(route = Screen.Signup.route) {
            SignupScreen(
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(route = Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // Main App Flow
        composable(route = Screen.Home.route) {
            HomeScreen(
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route)
                },
                onNavigateToUpload = {
                    navController.navigate(Screen.Upload.route)
                },
                onNavigateToLiveDetection = {
                    navController.navigate(Screen.Camera.route)
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(route = Screen.Dashboard.route) {
            // Placeholder - DashboardScreen to be created
        }
        
        composable(route = Screen.Camera.route) {
            // Placeholder - CameraScreen to be created
        }
        
        composable(route = Screen.Upload.route) {
            // Placeholder - UploadScreen to be created
        }
    }
}
