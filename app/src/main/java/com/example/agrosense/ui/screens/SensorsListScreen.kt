package com.example.agrosense.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.ui.viewmodel.BleViewModel
import com.example.agrosense.ui.viewmodel.SensorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class PumpToast(val message: String, val isSuccess: Boolean)

private enum class RemoteAction { ON, OFF, AUTO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsListScreen(
    vm: SensorViewModel,
    bleViewModel: BleViewModel,
    onBack: () -> Unit,
    onConfigureWifi: (sensor: Sensor) -> Unit = {},
    onViewCharts: (sensor: Sensor) -> Unit = {},
    onSchedulePump: (sensor: Sensor) -> Unit = {},
    onViewDeleted: () -> Unit = {},
    onShareSensor: (sensor: Sensor) -> Unit = {},
    onViewLogs: (sensor: Sensor) -> Unit = {},
    onGenerateReport: (sensor: Sensor) -> Unit = {}
) {
    val state         by vm.state.collectAsState()
    val bleDeviceId   by bleViewModel.deviceId.collectAsState()
    val reading       by bleViewModel.reading.collectAsState()
    val isConnected   by bleViewModel.isConnected.collectAsState()
    val isConnecting  by bleViewModel.isConnecting.collectAsState()
    val pumpState     by bleViewModel.pumpState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var sensorToDelete  by remember { mutableStateOf<Sensor?>(null) }
    var awaitingPumpAck by remember { mutableStateOf(false) }
    var pumpToast       by remember { mutableStateOf<PumpToast?>(null) }

    LaunchedEffect(pumpToast) {
        if (pumpToast != null) { delay(3500); pumpToast = null }
    }

    LaunchedEffect(Unit) { vm.loadSensors() }

    // Diálogo esperando ACK del microcontrolador
    if (awaitingPumpAck) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Enviando instrucción", fontWeight = FontWeight.Bold) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(14.dp))
                    Text("Esperando respuesta del Dispositivo…")
                }
            },
            confirmButton = {}
        )
    }

    // Diálogo confirmar eliminación
    sensorToDelete?.let { sensor ->
        AlertDialog(
            onDismissRequest = { sensorToDelete = null },
            title = { Text("Eliminar sensor") },
            text = {
                Text(
                    "¿Eliminar \"${sensor.name}\"?\n\n" +
                    "Se guardará un respaldo por 30 días. Podrás restaurarlo desde \"Sensores eliminados\"."
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Mis sensores") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onViewDeleted) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Sensores eliminados")
                    }
                }
            )
        }
    ) { padding ->

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

        Column(
            modifier = Modifier
                .fillMaxSize()
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

            val isEmpty = state.sensors.isEmpty()

            if (!state.isLoading && isEmpty) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
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

                    // ── Mis sensores ─────────────────────────────────────────
                    items(state.sensors, key = { it.id }) { sensor ->
                        val isThisConnected  = isConnected && bleDeviceId?.lowercase() == sensor.device_id.lowercase()
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
                            onViewCharts     = { onViewCharts(sensor) },
                            onSchedulePump   = { onSchedulePump(sensor) },
                            onShare          = { onShareSensor(sensor) },
                            onViewLogs       = { onViewLogs(sensor) },
                            onGenerateReport = { onGenerateReport(sensor) },
                            onDelete         = { sensorToDelete = sensor },
                            onRemoteControl  = { override ->
                                if (override != null) awaitingPumpAck = true
                                vm.setPumpOverride(
                                    sensorId  = sensor.id,
                                    override  = override,
                                    onSuccess = { msg ->
                                        awaitingPumpAck = false
                                        pumpToast = PumpToast(msg, isSuccess = true)
                                    },
                                    onError = { msg ->
                                        awaitingPumpAck = false
                                        pumpToast = PumpToast(msg, isSuccess = false)
                                    }
                                )
                            }
                        )
                    }

                }
            }
        } // Column

        // ── Toast resultado bomba ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = pumpToast != null,
            enter   = slideInVertically { it } + fadeIn(),
            exit    = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            pumpToast?.let { toast ->
                Card(
                    shape     = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(12.dp),
                    colors    = CardDefaults.cardColors(
                        containerColor = if (toast.isSuccess) Color(0xFF1B5E20)
                                         else MaterialTheme.colorScheme.error
                    )
                ) {
                    Row(
                        modifier          = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector      = if (toast.isSuccess) Icons.Default.CheckCircle
                                               else Icons.Default.Warning,
                            contentDescription = null,
                            tint             = Color.White,
                            modifier         = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text       = toast.message,
                            color      = Color.White,
                            style      = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        } // Box
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
    onViewCharts: () -> Unit,
    onSchedulePump: () -> Unit,
    onShare: () -> Unit,
    onViewLogs: () -> Unit,
    onGenerateReport: () -> Unit,
    onDelete: () -> Unit,
    onRemoteControl: (Boolean?) -> Unit
) {
    var pendingAction by remember { mutableStateOf<RemoteAction?>(null) }

    // ── Diálogo de confirmación de acción remota ──────────────────────────────
    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = {
                Text(
                    when (action) {
                        RemoteAction.ON   -> "Encender riego"
                        RemoteAction.OFF  -> "Apagar riego"
                        RemoteAction.AUTO -> "Modo automático"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    when (action) {
                        RemoteAction.ON   ->
                            "¿Encender el riego ahora?\n\n" +
                            "Esto anulará la programación automática hasta que selecciones " +
                            "\"Apagar\" o \"Auto\"."
                        RemoteAction.OFF  ->
                            "¿Apagar el riego?\n\n" +
                            "Si hay un riego activo o programado, se interrumpirá."
                        RemoteAction.AUTO ->
                            "¿Volver al modo automático?\n\n" +
                            "El riego seguirá la programación horaria configurada."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val override = when (action) {
                            RemoteAction.ON   -> true
                            RemoteAction.OFF  -> false
                            RemoteAction.AUTO -> null
                        }
                        onRemoteControl(override)
                        pendingAction = null
                    },
                    colors = if (action == RemoteAction.OFF)
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else ButtonDefaults.buttonColors()
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("Cancelar") }
            }
        )
    }

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
                IconButton(onClick = onViewLogs, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.History, contentDescription = "Historial",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Share, contentDescription = "Compartir",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp))
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

            // ── Acciones siempre visibles ─────────────────────────────────
            OutlinedButton(
                onClick = onViewCharts,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.BarChart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Gráficas")
            }

            Spacer(Modifier.height(2.dp))

            OutlinedButton(
                onClick = onSchedulePump,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.WaterDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Programar riego")
            }

            Spacer(Modifier.height(2.dp))

            OutlinedButton(
                onClick = onGenerateReport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Generar informe PDF")
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // ── Control remoto ────────────────────────────────────────────
            Text(
                "Control remoto",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))

            // Estado actual del control
            val (overrideBg, overrideText) = when (sensor.pump_manual_override) {
                true  -> MaterialTheme.colorScheme.primaryContainer to "Riego activo (manual)"
                false -> MaterialTheme.colorScheme.errorContainer   to "Riego apagado (manual)"
                null  -> MaterialTheme.colorScheme.surfaceVariant   to "Modo automático"
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = overrideBg)
            ) {
                Text(
                    overrideText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(Modifier.height(8.dp))

            // Botones de control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isOn   = sensor.pump_manual_override == true
                val isOff  = sensor.pump_manual_override == false
                val isAuto = sensor.pump_manual_override == null

                Button(
                    onClick = { if (!isOn) pendingAction = RemoteAction.ON },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOn) MaterialTheme.colorScheme.primary
                                         else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor   = if (isOn) MaterialTheme.colorScheme.onPrimary
                                         else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("Encender", style = MaterialTheme.typography.labelSmall) }

                Button(
                    onClick = { if (!isOff) pendingAction = RemoteAction.OFF },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOff) MaterialTheme.colorScheme.error
                                         else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor   = if (isOff) MaterialTheme.colorScheme.onError
                                         else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("Apagar", style = MaterialTheme.typography.labelSmall) }

                Button(
                    onClick = { if (!isAuto) pendingAction = RemoteAction.AUTO },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAuto) MaterialTheme.colorScheme.secondary
                                         else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor   = if (isAuto) MaterialTheme.colorScheme.onSecondary
                                         else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("Auto", style = MaterialTheme.typography.labelSmall) }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

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
                        Text(if (pumpState) "Riego activo" else "Riego desactivado")
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReadingChip(
                            label  = "🌫 CO₂",
                            value  = reading.co2?.let { "%.0f ppm".format(it) } ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                        ReadingChip(
                            label  = "🔥 Metano",
                            value  = reading.methane?.let { "%.0f ppm".format(it) } ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                    }
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
