package com.example.agrosense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.data.model.SharedSensorEntry
import com.example.agrosense.ui.viewmodel.SensorViewModel
import kotlinx.coroutines.launch

private enum class SharedRemoteAction { ON, OFF, AUTO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedSensorsScreen(
    sensorViewModel: SensorViewModel,
    onBack: () -> Unit,
    onViewCharts: (sensor: Sensor) -> Unit = {},
    onSchedulePump: (sensor: Sensor) -> Unit = {}
) {
    val sharedState   by sensorViewModel.sharedSensorsState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { sensorViewModel.loadSharedWithMe() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Compartidos conmigo", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primary,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (sharedState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            sharedState.error?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(msg, modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            if (sharedState.sensors.isEmpty() && !sharedState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Sin sensores compartidos",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Cuando alguien comparta un sensor contigo aparecerá aquí",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sharedState.sensors, key = { "shared_${it.share_id}" }) { entry ->
                        SharedSensorCard(
                            entry          = entry,
                            onViewCharts   = { if (entry.can_view_graphs) onViewCharts(entry.toSensor()) },
                            onSchedulePump = { if (entry.can_schedule) onSchedulePump(entry.toSensor()) },
                            onRemoteControl = { override ->
                                sensorViewModel.setPumpOverrideShared(
                                    sensorId  = entry.sensor_id,
                                    override  = override,
                                    onSuccess = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                when (override) {
                                                    true  -> "Riego encendido"
                                                    false -> "Riego apagado"
                                                    null  -> "Modo automático"
                                                }
                                            )
                                        }
                                    },
                                    onError = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar("Error: $msg") }
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedSensorCard(
    entry: SharedSensorEntry,
    onViewCharts: () -> Unit,
    onSchedulePump: () -> Unit,
    onRemoteControl: (Boolean?) -> Unit
) {
    var pendingAction by remember { mutableStateOf<SharedRemoteAction?>(null) }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = {
                Text(when (action) {
                    SharedRemoteAction.ON   -> "Encender riego"
                    SharedRemoteAction.OFF  -> "Apagar riego"
                    SharedRemoteAction.AUTO -> "Modo automático"
                }, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(when (action) {
                    SharedRemoteAction.ON   -> "¿Encender el riego ahora?"
                    SharedRemoteAction.OFF  -> "¿Apagar el riego?"
                    SharedRemoteAction.AUTO -> "¿Volver al modo automático?"
                })
            },
            confirmButton = {
                Button(onClick = {
                    onRemoteControl(when (action) {
                        SharedRemoteAction.ON -> true
                        SharedRemoteAction.OFF -> false
                        SharedRemoteAction.AUTO -> null
                    })
                    pendingAction = null
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("Cancelar") }
            }
        )
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    Text("Compartido por: ${entry.ownerName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary)
                    entry.location?.let {
                        Text("Ubicación: $it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (entry.can_view_graphs) {
                OutlinedButton(onClick = onViewCharts, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.BarChart, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Gráficas")
                }
                Spacer(Modifier.height(2.dp))
            }

            if (entry.can_schedule) {
                OutlinedButton(onClick = onSchedulePump, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.WaterDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Programar riego")
                }
                Spacer(Modifier.height(2.dp))
            }

            if (entry.can_control_pump) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Control remoto", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))

                val (overrideBg, overrideText) = when (entry.pump_manual_override) {
                    true  -> MaterialTheme.colorScheme.primaryContainer to "Riego activo (manual)"
                    false -> MaterialTheme.colorScheme.errorContainer   to "Riego apagado (manual)"
                    null  -> MaterialTheme.colorScheme.surfaceVariant   to "Modo automático"
                }
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = overrideBg)) {
                    Text(overrideText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium)
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val isOn   = entry.pump_manual_override == true
                    val isOff  = entry.pump_manual_override == false
                    val isAuto = entry.pump_manual_override == null

                    Button(
                        onClick = { if (!isOn) pendingAction = SharedRemoteAction.ON },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor   = if (isOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) { Text("Encender", style = MaterialTheme.typography.labelSmall) }

                    Button(
                        onClick = { if (!isOff) pendingAction = SharedRemoteAction.OFF },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOff) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor   = if (isOff) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) { Text("Apagar", style = MaterialTheme.typography.labelSmall) }

                    Button(
                        onClick = { if (!isAuto) pendingAction = SharedRemoteAction.AUTO },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAuto) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor   = if (isAuto) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) { Text("Auto", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}
