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
            val authVm: AuthViewModel = viewModel()
            val sensorVm: SensorViewModel = viewModel()
            val bleVm: BleViewModel = viewModel()

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
                        onRegisterSensor = { screen = "ble" },      // ✅ aquí vamos a BLE
                        onViewSensors = { screen = "sensors_list" } // lo de siempre
                    )

                // ✅ NUEVA PANTALLA BLE
                "ble" ->
                    BleScreen(
                        viewModel = bleVm
                    )
                "ble" -> BleScreen(viewModel = bleVm, onBack = { screen = "profile" })

                "register_sensor" ->
                    RegisterSensorScreen(
                        vm = sensorVm,
                        onBack = { screen = "profile" }
                    )

                "sensors_list" ->
                    SensorsListScreen(
                        vm = sensorVm,
                        onBack = { screen = "profile" }
                    )



                else -> {}
            }
        }
    }
}
