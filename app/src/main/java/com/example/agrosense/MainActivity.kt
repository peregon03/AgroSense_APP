package com.example.agrosense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.ui.screens.*
import com.example.agrosense.ui.viewmodel.AlertViewModel
import com.example.agrosense.ui.viewmodel.AuthViewModel
import com.example.agrosense.ui.viewmodel.BleViewModel
import com.example.agrosense.ui.viewmodel.SensorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val authVm: AuthViewModel     = viewModel()
            val sensorVm: SensorViewModel = viewModel()
            val bleVm: BleViewModel       = viewModel()
            val alertVm: AlertViewModel   = viewModel()

            var screen by remember { mutableStateOf("loading") }

            // Sensores seleccionados para WiFi y para gráficas
            var wifiTargetSensor    by remember { mutableStateOf<Sensor?>(null) }
            var chartsTargetSensor  by remember { mutableStateOf<Sensor?>(null) }
            var alertTargetSensor   by remember { mutableStateOf<Sensor?>(null) }
            // WiFi config cuando viene directo desde BLE (sin objeto Sensor completo)
            var pendingWifiName     by remember { mutableStateOf("") }
            var pendingWifiApiKey   by remember { mutableStateOf("") }
            // Email pendiente de verificación o recuperación
            var pendingEmail        by remember { mutableStateOf("") }

            val state by authVm.state.collectAsState()
            val alertState by alertVm.state.collectAsState()

            LaunchedEffect(Unit) { authVm.checkSession() }
            LaunchedEffect(state.isLoggedIn) {
                screen = if (state.isLoggedIn) "profile" else "login"
                if (state.isLoggedIn) alertVm.loadAlerts()
            }

            when (screen) {
                "login" ->
                    LoginScreen(
                        vm                  = authVm,
                        onGoRegister        = { screen = "register" },
                        onForgotPassword    = { screen = "forgot_password" },
                        onNeedsVerification = { email -> pendingEmail = email; screen = "verify_email" }
                    )

                "register" ->
                    RegisterScreen(
                        vm            = authVm,
                        onBackToLogin = { screen = "login" },
                        onVerifyEmail = { email -> pendingEmail = email; screen = "verify_email" }
                    )

                "profile" ->
                    ProfileScreen(
                        vm               = authVm,
                        onRegisterSensor = { screen = "ble" },
                        onViewSensors    = { screen = "sensors_list" },
                        onViewCharts     = { screen = "sensors_list" },
                        onEditProfile    = { screen = "edit_profile" },
                        onViewAlerts     = { screen = "alerts" },
                        alertUnreadCount = alertState.unreadCount
                    )

                "ble" ->
                    BleScreen(
                        viewModel          = bleVm,
                        sensorViewModel    = sensorVm,
                        onBack             = { screen = "profile" },
                        onSensorRegistered = { sensorName, apiKey ->
                            if (apiKey.isNotEmpty()) {
                                wifiTargetSensor  = null
                                pendingWifiName   = sensorName
                                pendingWifiApiKey = apiKey
                                screen = "wifi_config"
                            } else {
                                screen = "sensors_list"
                            }
                        }
                    )

                "sensors_list" ->
                    SensorsListScreen(
                        vm                = sensorVm,
                        bleViewModel      = bleVm,
                        onBack            = { screen = "profile" },
                        onConfigureWifi   = { sensor ->
                            wifiTargetSensor = sensor
                            screen = "wifi_config"
                        },
                        onConfigureAlerts = { sensor ->
                            alertTargetSensor = sensor
                            screen = "alert_config"
                        },
                        onViewCharts = { sensor ->
                            chartsTargetSensor = sensor
                            screen = "charts"
                        }
                    )

                "wifi_config" -> {
                    val sensor = wifiTargetSensor
                    val wifiName   = sensor?.name   ?: pendingWifiName
                    val wifiApiKey = sensor?.api_key ?: pendingWifiApiKey
                    if (wifiName.isNotEmpty() && wifiApiKey.isNotEmpty()) {
                        WifiConfigScreen(
                            bleViewModel = bleVm,
                            sensorName   = wifiName,
                            apiKey       = wifiApiKey,
                            onBack       = {
                                pendingWifiName   = ""
                                pendingWifiApiKey = ""
                                screen = "sensors_list"
                            }
                        )
                    } else {
                        LaunchedEffect(Unit) { screen = "sensors_list" }
                    }
                }

                "alert_config" -> {
                    val sensor = alertTargetSensor
                    if (sensor != null) {
                        AlertConfigScreen(
                            sensor          = sensor,
                            sensorViewModel = sensorVm,
                            onBack          = { screen = "sensors_list" }
                        )
                    } else {
                        LaunchedEffect(Unit) { screen = "sensors_list" }
                    }
                }

                "alerts" ->
                    AlertsScreen(
                        vm     = alertVm,
                        onBack = { screen = "profile" }
                    )

                "charts" -> {
                    val sensor = chartsTargetSensor
                    if (sensor != null) {
                        ChartsScreen(
                            sensor          = sensor,
                            sensorViewModel = sensorVm,
                            onBack          = { screen = "sensors_list" },
                            onGoAddSensor   = { screen = "ble" }
                        )
                    } else {
                        LaunchedEffect(Unit) { screen = "sensors_list" }
                    }
                }

                "edit_profile" ->
                    EditProfileScreen(
                        vm     = authVm,
                        onBack = { screen = "profile" }
                    )

                "verify_email" ->
                    VerifyEmailScreen(
                        vm        = authVm,
                        email     = pendingEmail,
                        onSuccess = { screen = "profile" },
                        onBack    = { screen = "login" }
                    )

                "forgot_password" ->
                    ForgotPasswordScreen(
                        vm         = authVm,
                        onCodeSent = { email -> pendingEmail = email; screen = "reset_password" },
                        onBack     = { screen = "login" }
                    )

                "reset_password" ->
                    ResetPasswordScreen(
                        vm        = authVm,
                        email     = pendingEmail,
                        onSuccess = { screen = "login" },
                        onBack    = { screen = "forgot_password" }
                    )

                else -> {}
            }
        }
    }
}
