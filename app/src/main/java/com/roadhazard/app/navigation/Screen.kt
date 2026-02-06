package com.roadhazard.app.navigation

sealed class Screen(val route: String) {
    // Authentication Flow
    data object Login : Screen("login")
    data object Signup : Screen("signup")
    data object ForgotPassword : Screen("forgot_password")
    
    // Main App Flow
    data object Home : Screen("home")
    data object Dashboard : Screen("dashboard")
    data object Camera : Screen("camera")
    data object Upload : Screen("upload")
    data object Settings : Screen("settings")
    data object ChangePassword : Screen("change_password")
    
    // Utility function to handle navigation with arguments
    fun withArgs(vararg args: String): String {
        return buildString {
            append(route)
            args.forEach { arg ->
                append("/$arg")
            }
        }
    }
}
