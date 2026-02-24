package com.example.agrosense.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.agrosense.ui.viewmodel.BleViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BleScreen(
    viewModel: BleViewModel,
    onBack: () -> Unit = {}
) {
    val devices by viewModel.devices.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val reading by viewModel.reading.collectAsState()

    // Permisos requeridos según versión de Android
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    val permissionsState = rememberMultiplePermissionsState(permissions)

    Column(modifier = Modifier.padding(16.dp)) {

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bluetooth (BLE)", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("Volver") }
        }

        Spacer(Modifier.height(12.dp))

        if (!permissionsState.allPermissionsGranted) {
            // Mostrar botón para pedir permisos
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Se necesitan permisos de Bluetooth para escanear dispositivos.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                        Text("Conceder permisos")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (permissionsState.allPermissionsGranted) {
                        viewModel.startScan()
                    } else {
                        permissionsState.launchMultiplePermissionRequest()
                    }
                }
            ) {
                Text("Escanear")
            }
            OutlinedButton(onClick = { viewModel.stopScan() }) {
                Text("Detener")
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Dispositivos encontrados:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (devices.isEmpty()) {
            Text("Aún no hay dispositivos. Pulsa \"Escanear\".")
        } else {
            devices.forEach { device ->
                ElevatedButton(
                    onClick = {
                        try {
                            viewModel.connect(device)
                        } catch (e: SecurityException) {
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(
                        try { device.name ?: device.address ?: "Dispositivo" }
                        catch (e: SecurityException) { device.address ?: "Dispositivo" }
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text("Device ID: ${deviceId ?: "--"}")
        Text("Lectura: ${reading ?: "--"}")
    }
}