package com.example.agrosense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.agrosense.ui.viewmodel.SensorViewModel

@Composable
fun RegisterSensorScreen(
    vm: SensorViewModel,
    onBack: () -> Unit
) {
    var deviceId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }

    val state by vm.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Registrar sensor", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = deviceId,
            onValueChange = { deviceId = it },
            label = { Text("Device ID") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Ubicación") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                vm.createSensor(
                    deviceId = deviceId,
                    name = name,
                    location = location.ifBlank { null }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading
        ) {
            Text(if (state.isLoading) "Registrando..." else "Registrar sensor")
        }

        state.error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }

    // ✅ Dialog éxito
    state.createdSensor?.let { s ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Sensor registrado ✅") },
            text = {
                Column {
                    Text("Nombre: ${s.name}")
                    Text("Ubicación: ${s.location ?: "-"}")
                    Text("Device ID: ${s.device_id}")
                    Spacer(Modifier.height(10.dp))
                    Text("API KEY:")
                    Text(s.api_key ?: "")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(s.api_key ?: ""))
                }) {
                    Text("Copiar API KEY")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.clear()
                    onBack()
                }) {
                    Text("Listo")
                }
            }
        )
    }
}
