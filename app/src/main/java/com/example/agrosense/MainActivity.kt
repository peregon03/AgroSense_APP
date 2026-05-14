package com.example.agrosense

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agrosense.data.local.entity.LocalSensorEntity
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.ui.screens.*
import com.example.agrosense.ui.viewmodel.AuthViewModel
import com.example.agrosense.ui.viewmodel.BleViewModel
import com.example.agrosense.ui.viewmodel.LocalViewModel
import com.example.agrosense.ui.viewmodel.SensorViewModel
import com.example.agrosense.ui.theme.AgroSenseTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AgroSenseTheme {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notifPermission = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
                LaunchedEffect(Unit) { notifPermission.launchPermissionRequest() }
            }

            val authVm: AuthViewModel     = viewModel()
            val sensorVm: SensorViewModel = viewModel()
            val bleVm: BleViewModel       = viewModel()
            val localVm: LocalViewModel   = viewModel()

            var screen by remember { mutableStateOf("loading") }

            // ── Vars modo NUBE ─────────────────────────────────────────────
            var wifiTargetSensor    by remember { mutableStateOf<Sensor?>(null) }
            var chartsTargetSensor  by remember { mutableStateOf<Sensor?>(null) }
            var pumpTargetSensor    by remember { mutableStateOf<Sensor?>(null) }
            var shareTargetSensor   by remember { mutableStateOf<Sensor?>(null) }
            var logsTargetSensor    by remember { mutableStateOf<Sensor?>(null) }
            var reportTargetSensor  by remember { mutableStateOf<Sensor?>(null) }
            var pendingWifiName     by remember { mutableStateOf("") }
            var pendingWifiApiKey   by remember { mutableStateOf("") }
            var pendingEmail        by remember { mutableStateOf("") }

            // ── Vars modo LOCAL ────────────────────────────────────────────
            var localChartsDeviceId   by remember { mutableStateOf("") }
            var localChartsDeviceName by remember { mutableStateOf("") }
            var localWifiSensor       by remember { mutableStateOf<LocalSensorEntity?>(null) }

            val authState by authVm.state.collectAsState()
            val appMode   by localVm.appMode.collectAsState()

            // ── Inicialización: leer modo guardado en DataStore ────────────
            LaunchedEffect(Unit) { localVm.initialize() }

            // ── Modo LOCAL: navegar según si hay usuario creado ────────────
            LaunchedEffect(appMode) {
                when (appMode) {
                    "cloud" -> authVm.checkSession()
                    "local" -> {
                        screen = if (localVm.hasLocalUser.value) "local_sensors_list" else "local_login"
                    }
                }
            }

            // ── Modo NUBE: responder a cambios de auth ─────────────────────
            // Solo actúa cuando appMode es "cloud" para no interferir con el modo local
            LaunchedEffect(authState.isLoggedIn, appMode) {
                if (appMode == "cloud") {
                    screen = if (authState.isLoggedIn) "profile" else "login"
                }
            }

            // ──────────────────────────────────────────────────────────────
            //  ROUTER
            // ──────────────────────────────────────────────────────────────

            when (screen) {

                // ══ MODO NUBE ══════════════════════════════════════════════

                "login" ->
                    LoginScreen(
                        vm                  = authVm,
                        onGoRegister        = { screen = "register" },
                        onForgotPassword    = { screen = "forgot_password" },
                        onNeedsVerification = { email -> pendingEmail = email; screen = "verify_email" },
                        onLocalMode         = { screen = "local_login" }
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
                        sensorVm         = sensorVm,
                        onRegisterSensor = { screen = "ble" },
                        onViewSensors    = { screen = "sensors_list" },
                        onViewShared     = { screen = "shared_sensors" },
                        onEditProfile    = { screen = "edit_profile" }
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
                        onSchedulePump = { sensor ->
                            pumpTargetSensor = sensor
                            screen = "pump_schedule"
                        },
                        onViewCharts = { sensor ->
                            chartsTargetSensor = sensor
                            screen = "charts"
                        },
                        onViewDeleted = { screen = "deleted_sensors" },
                        onShareSensor = { sensor ->
                            shareTargetSensor = sensor
                            screen = "share_sensor"
                        },
                        onViewLogs = { sensor ->
                            logsTargetSensor = sensor
                            screen = "action_logs"
                        },
                        onGenerateReport = { sensor ->
                            reportTargetSensor = sensor
                            screen = "report"
                        }
                    )

                "deleted_sensors" ->
                    DeletedSensorsScreen(
                        sensorViewModel = sensorVm,
                        onBack          = { screen = "sensors_list" }
                    )

                "shared_sensors" ->
                    SharedSensorsScreen(
                        sensorViewModel = sensorVm,
                        onBack          = { screen = "profile" },
                        onViewCharts    = { sensor -> chartsTargetSensor = sensor; screen = "charts" },
                        onSchedulePump  = { sensor -> pumpTargetSensor = sensor; screen = "pump_schedule" }
                    )

                "share_sensor" -> {
                    val sensor = shareTargetSensor
                    if (sensor != null) {
                        ShareSensorScreen(
                            sensor          = sensor,
                            sensorViewModel = sensorVm,
                            onBack          = { screen = "sensors_list" }
                        )
                    } else { LaunchedEffect(Unit) { screen = "sensors_list" } }
                }

                "report" -> {
                    val sensor = reportTargetSensor
                    if (sensor != null) {
                        ReportScreen(
                            sensor          = sensor,
                            sensorViewModel = sensorVm,
                            onBack          = { screen = "sensors_list" }
                        )
                    } else { LaunchedEffect(Unit) { screen = "sensors_list" } }
                }

                "action_logs" -> {
                    val sensor = logsTargetSensor
                    if (sensor != null) {
                        ActionLogsScreen(
                            sensor          = sensor,
                            sensorViewModel = sensorVm,
                            onBack          = { screen = "sensors_list" }
                        )
                    } else { LaunchedEffect(Unit) { screen = "sensors_list" } }
                }

                "wifi_config" -> {
                    // wifiTargetSensor → modo nube (Sensor con api_key del backend)
                    // localWifiSensor  → modo local (LocalSensorEntity, apiKey puede ser null)
                    // pendingWifi*     → viene directo del BleScreen tras registro nube
                    val wifiName   = wifiTargetSensor?.name
                        ?: localWifiSensor?.name
                        ?: pendingWifiName
                    val wifiApiKey = wifiTargetSensor?.api_key
                        ?: localWifiSensor?.apiKey
                        ?: pendingWifiApiKey

                    val backRoute = if (localWifiSensor != null) "local_sensors_list" else "sensors_list"

                    if (wifiName.isNotEmpty()) {
                        WifiConfigScreen(
                            bleViewModel = bleVm,
                            sensorName   = wifiName,
                            apiKey       = wifiApiKey ?: "",
                            onBack       = {
                                pendingWifiName   = ""
                                pendingWifiApiKey = ""
                                localWifiSensor   = null
                                screen = backRoute
                            }
                        )
                    } else { LaunchedEffect(Unit) { screen = backRoute } }
                }

                "pump_schedule" -> {
                    val sensor = pumpTargetSensor
                    if (sensor != null) {
                        PumpScheduleScreen(
                            sensor          = sensor,
                            sensorViewModel = sensorVm,
                            onBack          = { screen = "sensors_list" }
                        )
                    } else { LaunchedEffect(Unit) { screen = "sensors_list" } }
                }

                "charts" -> {
                    val sensor = chartsTargetSensor
                    if (sensor != null) {
                        ChartsScreen(
                            sensor          = sensor,
                            sensorViewModel = sensorVm,
                            onBack          = { screen = "sensors_list" },
                            onGoAddSensor   = { screen = "ble" }
                        )
                    } else { LaunchedEffect(Unit) { screen = "sensors_list" } }
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

                // ══ MODO LOCAL ═════════════════════════════════════════════

                "local_login" ->
                    LocalLoginScreen(
                        vm           = localVm,
                        onLoginSuccess = { screen = "local_sensors_list" },
                        onUseCloud   = {
                            localVm.setAppMode("cloud")
                            screen = "login"
                        }
                    )

                "local_sensors_list" ->
                    LocalSensorsListScreen(
                        localVm      = localVm,
                        bleVm        = bleVm,
                        onViewCharts = { deviceId ->
                            val sensor = localVm.state.value.sensors.find { it.deviceId == deviceId }
                            localChartsDeviceId   = deviceId
                            localChartsDeviceName = sensor?.name ?: deviceId
                            screen = "local_charts"
                        },
                        onConfigureWifi = { sensor ->
                            localWifiSensor = sensor
                            screen = "wifi_config"
                        },
                        onLogout = {
                            localVm.logout()
                            screen = "local_login"
                        }
                    )

                "local_charts" -> {
                    if (localChartsDeviceId.isNotEmpty()) {
                        LocalChartsScreen(
                            deviceId   = localChartsDeviceId,
                            deviceName = localChartsDeviceName,
                            localVm    = localVm,
                            bleVm      = bleVm,
                            onBack     = { screen = "local_sensors_list" }
                        )
                    } else { LaunchedEffect(Unit) { screen = "local_sensors_list" } }
                }

                else -> {}
            }
            } // AgroSenseTheme
        }
    }
}
