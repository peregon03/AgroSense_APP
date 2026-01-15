package com.example.agrosense


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agrosense.ui.screens.LoginScreen
import com.example.agrosense.ui.screens.ProfileScreen
import com.example.agrosense.ui.screens.RegisterScreen
import com.example.agrosense.ui.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vm: AuthViewModel = viewModel()

            var screen by remember { mutableStateOf("loading") }

            val state by vm.state.collectAsState()

            LaunchedEffect(Unit) { vm.checkSession() }
            LaunchedEffect(state.isLoggedIn) {
                screen = if (state.isLoggedIn) "profile" else "login"
            }

            when (screen) {
                "login" -> LoginScreen(vm, onGoRegister = { screen = "register" })
                "register" -> RegisterScreen(vm, onBackToLogin = { screen = "login" })
                "profile" -> ProfileScreen(vm)
                else -> {} // loading
            }
        }
    }
}
