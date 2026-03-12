package com.example.agrosense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agrosense.ui.screens.*
import com.example.agrosense.ui.viewmodel.AuthViewModel
import com.example.agrosense.ui.viewmodel.BleViewModel
import com.example.agrosense.ui.viewmodel.SensorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val authVm: AuthViewModel   = viewModel()
            val sensorVm: SensorViewModel = viewModel()
            val bleVm: BleViewModel     = viewModel()

            var screen by remember { mutableStateOf("loading") }
            val state by authVm.state.collectAsState()

            LaunchedEffect(Unit) { authVm.checkSession() }
            LaunchedEffect(state.isLoggedIn) {
                screen = if (state.isLoggedIn) "profile" else "login"
            }

            when (screen) {
                "login" ->
                    LoginScreen(authVm, onGoRegister = { screen = "register" })

                "register" ->
                    RegisterScreen(authVm, onBackToLogin = { screen = "login" })

                "profile" ->
                    ProfileScreen(
                        vm = authVm,
                        onRegisterSensor = { screen = "ble" },
                        onViewSensors    = { screen = "sensors_list" },
                        onViewCharts     = { screen = "charts" }
                    )

                "ble" ->
                    BleScreen(
                        viewModel = bleVm,
                        sensorViewModel = sensorVm,
                        onBack = { screen = "profile" },
                        onSensorRegistered = { screen = "sensors_list" }
                    )

                "sensors_list" ->
                    SensorsListScreen(
                        vm = sensorVm,
                        bleViewModel = bleVm,
                        onBack = { screen = "profile" }
                    )

                "charts" ->
                    ChartsScreen(
                        vm = sensorVm,
                        onBack = { screen = "profile" },
                        onGoAddSensor = { screen = "ble" }
                    )

                else -> {}
            }
        }
    }
}