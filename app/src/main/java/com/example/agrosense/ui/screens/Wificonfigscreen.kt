package com.example.agrosense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.agrosense.ui.viewmodel.BleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiConfigScreen(
    bleViewModel: BleViewModel,
    sensorName: String,
    apiKey: String,
    onBack: () -> Unit
) {
    val wifiStatus    by bleViewModel.wifiStatus.collectAsState()
    val isConnected   by bleViewModel.isConnected.collectAsState()

    var ssid          by remember { mutableStateOf("") }
    var password      by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var wasSent       by remember { mutableStateOf(false) }

    // Estado derivado de la notificación del ESP32
    val statusInfo = rememberWifiStatusInfo(wifiStatus, wasSent)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurar WiFi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // ── Icono + título ───────────────────────────────────────────────
            Icon(
                imageVector = Icons.Filled.Wifi,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Conectar «$sensorName» a WiFi",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Las credenciales se enviarán de forma segura al sensor vía Bluetooth.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(28.dp))

            // ── Alerta BLE desconectado ──────────────────────────────────────
            if (!isConnected) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "El sensor no está conectado por Bluetooth. " +
                                    "Conéctalo primero desde «Mis sensores».",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Formulario ───────────────────────────────────────────────────
            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it },
                label = { Text("Nombre de la red WiFi (SSID)") },
                leadingIcon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = isConnected && !statusInfo.isLoading
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                visualTransformation = if (passwordVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible)
                                Icons.Filled.VisibilityOff
                            else
                                Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible)
                                "Ocultar contraseña"
                            else
                                "Mostrar contraseña"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = isConnected && !statusInfo.isLoading
            )

            Spacer(Modifier.height(24.dp))

            // ── Botón borrar config guardada (para re-configurar) ────────────
            if (isConnected) {
                OutlinedButton(
                    onClick = { bleViewModel.resetDevice() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !statusInfo.isLoading
                ) {
                    Text("Borrar configuración guardada en el sensor")
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Botón enviar ─────────────────────────────────────────────────
            Button(
                onClick = {
                    wasSent = true
                    bleViewModel.sendWifiConfig(
                        ssid     = ssid.trim(),
                        password = password,
                        apiKey   = apiKey
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = isConnected && ssid.isNotBlank() && !statusInfo.isLoading,
                shape = RoundedCornerShape(14.dp)
            ) {
                if (statusInfo.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Conectando al WiFi...")
                } else {
                    Text("Enviar configuración")
                }
            }

            // ── Estado / feedback ────────────────────────────────────────────
            if ((wasSent || wifiStatus == "RESET_OK") && statusInfo.message.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = statusInfo.containerColor
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = statusInfo.icon,
                            contentDescription = null,
                            tint = statusInfo.iconTint
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                statusInfo.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = statusInfo.textColor
                            )
                            if (statusInfo.message.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    statusInfo.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusInfo.textColor
                                )
                            }
                        }
                    }
                }

                // Botón "Listo" cuando la conexión fue exitosa
                if (statusInfo.isSuccess) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Listo")
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Modelo de estado visual del feedback ─────────────────────────────────────

private data class WifiStatusInfo(
    val title: String,
    val message: String,
    val isLoading: Boolean,
    val isSuccess: Boolean,
    val containerColor: androidx.compose.ui.graphics.Color,
    val iconTint: androidx.compose.ui.graphics.Color,
    val textColor: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun rememberWifiStatusInfo(status: String, wasSent: Boolean): WifiStatusInfo {
    val colorScheme = MaterialTheme.colorScheme

    return remember(status, wasSent) {
        when {
            status == "RESET_OK" -> WifiStatusInfo(
                title          = "Configuración borrada",
                message        = "El sensor está limpio. Ahora ingresa los datos de la red y envía.",
                isLoading      = false,
                isSuccess      = false,
                containerColor = colorScheme.secondaryContainer,
                iconTint       = colorScheme.secondary,
                textColor      = colorScheme.onSecondaryContainer,
                icon           = Icons.Filled.Wifi
            )
            !wasSent || status == "NOT_CONFIGURED" -> WifiStatusInfo(
                title          = "",
                message        = "",
                isLoading      = false,
                isSuccess      = false,
                containerColor = colorScheme.surfaceVariant,
                iconTint       = colorScheme.onSurfaceVariant,
                textColor      = colorScheme.onSurfaceVariant,
                icon           = Icons.Filled.Wifi
            )
            status == "CONNECTING" -> WifiStatusInfo(
                title          = "Conectando...",
                message        = "El sensor está intentando conectarse al WiFi.",
                isLoading      = true,
                isSuccess      = false,
                containerColor = colorScheme.secondaryContainer,
                iconTint       = colorScheme.secondary,
                textColor      = colorScheme.onSecondaryContainer,
                icon           = Icons.Filled.Wifi
            )
            status == "CONNECTED" -> WifiStatusInfo(
                title          = "¡Conectado!",
                message        = "El sensor ya está enviando datos a la nube.",
                isLoading      = false,
                isSuccess      = true,
                containerColor = colorScheme.primaryContainer,
                iconTint       = colorScheme.primary,
                textColor      = colorScheme.onPrimaryContainer,
                icon           = Icons.Filled.CheckCircle
            )
            status.startsWith("ERROR:") -> {
                val errorCode = status.removePrefix("ERROR:")
                val humanMsg = when (errorCode) {
                    "no_se_pudo_conectar" -> "No se pudo conectar. Verifica el nombre y contraseña de la red."
                    "formato_invalido"    -> "Error interno de formato. Intenta de nuevo."
                    else                  -> "Error: $errorCode"
                }
                WifiStatusInfo(
                    title          = "Error de conexión",
                    message        = humanMsg,
                    isLoading      = false,
                    isSuccess      = false,
                    containerColor = colorScheme.errorContainer,
                    iconTint       = colorScheme.error,
                    textColor      = colorScheme.onErrorContainer,
                    icon           = Icons.Filled.ErrorOutline
                )
            }
            else -> WifiStatusInfo(
                title          = status,
                message        = "",
                isLoading      = false,
                isSuccess      = false,
                containerColor = colorScheme.surfaceVariant,
                iconTint       = colorScheme.onSurfaceVariant,
                textColor      = colorScheme.onSurfaceVariant,
                icon           = Icons.Filled.Wifi
            )
        }
    }
}