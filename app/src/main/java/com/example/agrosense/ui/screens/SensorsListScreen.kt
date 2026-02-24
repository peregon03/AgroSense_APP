package com.example.agrosense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.agrosense.ui.viewmodel.BleViewModel
import com.example.agrosense.ui.viewmodel.SensorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsListScreen(
    vm: SensorViewModel,
    bleViewModel: BleViewModel,
    onBack: () -> Unit
) {
    val state       by vm.state.collectAsState()
    val bleDeviceId by bleViewModel.deviceId.collectAsState()
    val reading     by bleViewModel.reading.collectAsState()
    val isConnected by bleViewModel.isConnected.collectAsState()

    LaunchedEffect(Unit) { vm.loadSensors() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis sensores") },
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
                .padding(16.dp)
        ) {

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }

            state.error?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                ) { Text(it, modifier = Modifier.padding(12.dp)) }
                Spacer(Modifier.height(12.dp))
            }

            if (!state.isLoading && state.sensors.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Sensors, contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text("No tienes sensores registrados.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Usa «Agregar sensor» para vincular uno.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.sensors) { sensor ->
                        val isThisConnected = isConnected &&
                                bleDeviceId != null &&
                                bleDeviceId == sensor.device_id

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {

                                // Encabezado
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(sensor.name, style = MaterialTheme.typography.titleMedium)
                                    Icon(
                                        imageVector = if (isThisConnected) Icons.Filled.SignalWifi4Bar
                                        else Icons.Filled.SignalWifiOff,
                                        contentDescription = null,
                                        tint = if (isThisConnected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(Modifier.height(2.dp))
                                Text("Device ID: ${sensor.device_id}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                sensor.location?.let {
                                    Text("Ubicación: $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                // Lecturas en tiempo real si está conectado
                                if (isThisConnected) {
                                    Spacer(Modifier.height(12.dp))
                                    HorizontalDivider()
                                    Spacer(Modifier.height(10.dp))

                                    Text("Lectura en tiempo real",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(8.dp))

                                    if (reading != null) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ReadingChip(
                                                label = "🌡 Temp.",
                                                value = reading!!.temperature?.let { "%.1f °C".format(it) } ?: "--",
                                                modifier = Modifier.weight(1f)
                                            )
                                            ReadingChip(
                                                label = "💧 Hum. aire",
                                                value = reading!!.airHumidity?.let { "%.1f %%".format(it) } ?: "--",
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        ReadingChip(
                                            label = "🌱 Hum. suelo",
                                            value = reading!!.soilHumidity?.let { "%.1f %%".format(it) } ?: "--",
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        Text("Esperando datos del sensor...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ReadingChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(value, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}