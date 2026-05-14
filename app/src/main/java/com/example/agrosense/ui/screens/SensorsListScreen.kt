package com.example.agrosense.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
    var expanded      by remember { mutableStateOf(false) }
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
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {

            // ── Header compacto (siempre visible) ────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isThisConnected) Icons.Filled.SignalWifi4Bar
                                  else Icons.Filled.SignalWifiOff,
                    contentDescription = null,
                    tint = if (isThisConnected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        sensor.name,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        sensor.location ?: sensor.device_id,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (isThisConnected) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "Conectado",
                            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style      = MaterialTheme.typography.labelSmall,
                            color      = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    imageVector        = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Colapsar" else "Expandir",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(20.dp)
                )
            }

            // ── Detalle expandible ────────────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))

                    // Fila 1: Gráficas + Programar riego
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onViewCharts, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.BarChart, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Gráficas", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(onClick = onSchedulePump, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.WaterDrop, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Programar", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Fila 2: Informe + Compartir
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onGenerateReport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Informe", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Share, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Compartir", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Fila 3: Historial + Eliminar
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onViewLogs, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.History, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Historial", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Filled.Delete, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Eliminar", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    // ── Control remoto ────────────────────────────────────────
                    Text("Control remoto",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))

                    val (overrideBg, overrideText) = when (sensor.pump_manual_override) {
                        true  -> MaterialTheme.colorScheme.primaryContainer to "Riego activo (manual)"
                        false -> MaterialTheme.colorScheme.errorContainer   to "Riego apagado (manual)"
                        null  -> MaterialTheme.colorScheme.surfaceVariant   to "Modo automático"
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(8.dp),
                        colors   = CardDefaults.cardColors(containerColor = overrideBg)
                    ) {
                        Text(overrideText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style    = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val isOn   = sensor.pump_manual_override == true
                        val isOff  = sensor.pump_manual_override == false
                        val isAuto = sensor.pump_manual_override == null

                        Button(
                            onClick  = { if (!isOn) pendingAction = RemoteAction.ON },
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor   = if (isOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) { Text("Encender", style = MaterialTheme.typography.labelSmall) }

                        Button(
                            onClick  = { if (!isOff) pendingAction = RemoteAction.OFF },
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = if (isOff) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor   = if (isOff) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) { Text("Apagar", style = MaterialTheme.typography.labelSmall) }

                        Button(
                            onClick  = { if (!isAuto) pendingAction = RemoteAction.AUTO },
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = if (isAuto) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor   = if (isAuto) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) { Text("Auto", style = MaterialTheme.typography.labelSmall) }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    // ── Botón conectar / desconectar BLE ─────────────────────
                    if (!isThisConnected) {
                        OutlinedButton(
                            onClick  = onConnect,
                            enabled  = !isThisConnecting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isThisConnecting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Conectando...")
                            } else {
                                Icon(Icons.Filled.Bluetooth, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Conectar")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick  = onDisconnect,
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Desconectar") }
                    }

                    // ── Sección BLE activa ────────────────────────────────────
                    if (isThisConnected) {
                        Spacer(Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick  = onPumpToggle,
                                modifier = Modifier.weight(1f),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = if (pumpState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor   = if (pumpState) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Filled.WaterDrop, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (pumpState) "Riego activo" else "Riego desactivado",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(onClick = onConfigureWifi, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Wifi, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("WiFi")
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))
                        Text("Lectura en tiempo real",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))

                        if (reading != null) {
                            // ── Ambiente ───────────────────────────────────
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ReadingChip("🌡 Temp.",     reading.temperature?.let { "%.1f °C".format(it) }  ?: "--", Modifier.weight(1f))
                                ReadingChip("💧 Hum.",      reading.airHumidity?.let { "%.1f %%".format(it) }  ?: "--", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ReadingChip("🌫 CO₂",       reading.co2?.let     { "%.0f ppm".format(it) } ?: "--", Modifier.weight(1f))
                                ReadingChip("🔥 Metano",    reading.methane?.let { "%.0f ppm".format(it) } ?: "--", Modifier.weight(1f))
                            }
                            // ── Suelo (solo si hay datos) ──────────────────
                            val hasSoil = reading.soilTemp != null || reading.soilHum != null ||
                                          reading.ec != null || reading.ph != null
                            if (hasSoil) {
                                Spacer(Modifier.height(8.dp))
                                Text("Suelo", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ReadingChip("🌱 T.Suelo",  reading.soilTemp?.let  { "%.1f °C".format(it) } ?: "--", Modifier.weight(1f))
                                    ReadingChip("💧 H.Suelo",  reading.soilHum?.let   { "%.1f %%".format(it) } ?: "--", Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ReadingChip("⚡ Cond.",    reading.ec?.let         { "%.0f µS/cm".format(it) } ?: "--", Modifier.weight(1f))
                                    ReadingChip("⚗ pH",       reading.ph?.let         { "%.2f".format(it) }       ?: "--", Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ReadingChip("🟢 N",        reading.nitrogen?.let   { "%.0f mg/kg".format(it) } ?: "--", Modifier.weight(1f))
                                    ReadingChip("🟠 P",        reading.phosphorus?.let { "%.0f mg/kg".format(it) } ?: "--", Modifier.weight(1f))
                                    ReadingChip("🔵 K",        reading.potassium?.let  { "%.0f mg/kg".format(it) } ?: "--", Modifier.weight(1f))
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Esperando datos del sensor...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
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
