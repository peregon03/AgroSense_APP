package com.example.agrosense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.ui.viewmodel.BleViewModel
import com.example.agrosense.ui.viewmodel.SensorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsListScreen(
    vm: SensorViewModel,
    bleViewModel: BleViewModel,
    onBack: () -> Unit,
    onConfigureWifi: (sensor: Sensor) -> Unit = {},
    onConfigureAlerts: (sensor: Sensor) -> Unit = {},
    onViewCharts: (sensor: Sensor) -> Unit = {}
) {
    val state        by vm.state.collectAsState()
    val bleDeviceId  by bleViewModel.deviceId.collectAsState()
    val reading      by bleViewModel.reading.collectAsState()
    val isConnected  by bleViewModel.isConnected.collectAsState()
    val isConnecting by bleViewModel.isConnecting.collectAsState()
    val pumpState    by bleViewModel.pumpState.collectAsState()

    var sensorToDelete by remember { mutableStateOf<Sensor?>(null) }

    LaunchedEffect(Unit) { vm.loadSensors() }

    // Diálogo confirmar eliminación
    sensorToDelete?.let { sensor ->
        AlertDialog(
            onDismissRequest = { sensorToDelete = null },
            title = { Text("Eliminar sensor") },
            text = {
                Text(
                    "¿Estás seguro de que quieres eliminar \"${sensor.name}\"? " +
                            "Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.deleteSensor(sensor.id); sensorToDelete = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { sensorToDelete = null }) { Text("Cancelar") }
            }
        )
    }

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
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(it, modifier = Modifier.padding(12.dp)) }
                Spacer(Modifier.height(12.dp))
            }

            if (!state.isLoading && state.sensors.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Sensors,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No tienes sensores registrados.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Usa «Agregar sensor» para vincular uno.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.sensors, key = { it.id }) { sensor ->
                        val isThisConnected = isConnected &&
                                bleDeviceId?.lowercase() == sensor.device_id.lowercase()
                        val isThisConnecting = isConnecting && !isConnected

                        SensorCard(
                            sensor           = sensor,
                            isThisConnected  = isThisConnected,
                            isThisConnecting = isThisConnecting,
                            reading          = if (isThisConnected) reading else null,
                            pumpState        = pumpState,
                            onConnect        = { bleViewModel.connectByAddress(sensor.device_id) },
                            onDisconnect     = { bleViewModel.disconnect() },
                            onPumpToggle     = { bleViewModel.controlPump(!pumpState) },
                            onConfigureWifi  = { onConfigureWifi(sensor) },
                            onConfigureAlerts = { onConfigureAlerts(sensor) },
                            onViewCharts     = { onViewCharts(sensor) },
                            onDelete         = { sensorToDelete = sensor }
                        )
                    }
                }
            }
        }
    }
}

// ── Card de sensor ────────────────────────────────────────────────────────────

@Composable
private fun SensorCard(
    sensor: Sensor,
    isThisConnected: Boolean,
    isThisConnecting: Boolean,
    reading: com.example.agrosense.data.ble.SensorReading?,
    pumpState: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onPumpToggle: () -> Unit,
    onConfigureWifi: () -> Unit,
    onConfigureAlerts: () -> Unit,
    onViewCharts: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Encabezado ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isThisConnected) Icons.Filled.SignalWifi4Bar
                        else Icons.Filled.SignalWifiOff,
                        contentDescription = null,
                        tint = if (isThisConnected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(sensor.name, style = MaterialTheme.typography.titleMedium)
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
            Text(
                "Device ID: ${sensor.device_id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            sensor.location?.let {
                Text(
                    "Ubicación: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Acciones siempre visibles: gráficas + alertas ────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewCharts,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.BarChart, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Gráficas")
                }
                OutlinedButton(
                    onClick = onConfigureAlerts,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Alertas")
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── Botón conectar / desconectar ─────────────────────────────────
            if (!isThisConnected) {
                OutlinedButton(
                    onClick = onConnect,
                    enabled = !isThisConnecting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isThisConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Conectando...")
                    } else {
                        Icon(
                            Icons.Filled.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Conectar")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Desconectar") }
            }

            // ── Acciones adicionales (solo si conectado) ─────────────────────
            if (isThisConnected) {

                Spacer(Modifier.height(8.dp))

                // Fila: Bomba + Configurar WiFi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPumpToggle,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pumpState)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (pumpState)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            Icons.Filled.WaterDrop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (pumpState) "Bomba ON" else "Bomba OFF")
                    }

                    OutlinedButton(
                        onClick = onConfigureWifi,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.Wifi,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("WiFi")
                    }
                }

                // ── Lecturas en tiempo real ──────────────────────────────────
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Text(
                    "Lectura en tiempo real",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))

                if (reading != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReadingChip(
                            label  = "🌡 Temp.",
                            value  = reading.temperature?.let { "%.1f °C".format(it) } ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                        ReadingChip(
                            label  = "💧 Hum. aire",
                            value  = reading.airHumidity?.let { "%.1f %%".format(it) } ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ReadingChip(
                        label  = "🌱 Hum. suelo",
                        value  = reading.soilHumidity?.let { "%.1f %%".format(it) } ?: "--",
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Esperando datos del sensor...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ── Chip de lectura ───────────────────────────────────────────────────────────

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
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
