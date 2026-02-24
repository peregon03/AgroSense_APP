package com.example.agrosense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.agrosense.ui.viewmodel.SensorViewModel

@Composable
fun SensorsListScreen(
    vm: SensorViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.loadSensors() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Mis sensores", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        if (state.isLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.sensors) { s ->
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Device ID: ${s.device_id}")
                        Text("Ubicación: ${s.location ?: "-"}")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Volver")
        }
    }
}
