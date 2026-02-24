package com.example.agrosense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.agrosense.ui.viewmodel.BleViewModel

@Composable
fun BleScreen(
    viewModel: BleViewModel,
    onBack: () -> Unit = {}
) {
    val devices by viewModel.devices.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val reading by viewModel.reading.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bluetooth (BLE)", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("Volver") }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { viewModel.startScan() }) {
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
            Text("Aún no hay dispositivos. Pulsa “Escanear”.")
        } else {
            devices.forEach { device ->
                ElevatedButton(
                    onClick = { viewModel.connect(device) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(device.name ?: device.address ?: "Dispositivo")
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Divider()
        Spacer(Modifier.height(12.dp))

        Text("Device ID: ${deviceId ?: "--"}")
        Text("Lectura: ${reading ?: "--"}")
    }
}
