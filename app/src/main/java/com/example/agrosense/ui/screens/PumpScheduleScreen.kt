package com.example.agrosense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.agrosense.data.model.IrrigationSchedule
import com.example.agrosense.data.model.IrrigationScheduleRequest
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.ui.viewmodel.SensorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PumpScheduleScreen(
    sensor: Sensor,
    sensorViewModel: SensorViewModel,
    onBack: () -> Unit
) {
    val irrigationState by sensorViewModel.irrigationState.collectAsState()

    // Control del diálogo de crear/editar
    var showDialog       by remember { mutableStateOf(false) }
    var editingSchedule  by remember { mutableStateOf<IrrigationSchedule?>(null) }
    var deleteTarget     by remember { mutableStateOf<IrrigationSchedule?>(null) }

    LaunchedEffect(sensor.id) {
        sensorViewModel.loadIrrigationSchedules(sensor.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Programar Riego", fontWeight = FontWeight.Bold)
                        Text(
                            sensor.name,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor            = MaterialTheme.colorScheme.primary,
                    titleContentColor         = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingSchedule = null; showDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar riego",
                    tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (irrigationState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            irrigationState.error?.let { msg ->
                Card(
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        msg,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (irrigationState.schedules.isEmpty() && !irrigationState.isLoading) {
                // Estado vacío
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.WaterDrop,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Sin programaciones de riego",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Toca + para agregar tu primer riego",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(irrigationState.schedules, key = { it.id }) { schedule ->
                        ScheduleCard(
                            schedule   = schedule,
                            onToggle   = { enabled ->
                                sensorViewModel.toggleIrrigationSchedule(sensor.id, schedule.id, enabled)
                            },
                            onEdit     = { editingSchedule = it; showDialog = true },
                            onDelete   = { deleteTarget = it }
                        )
                    }
                    // Espacio para el FAB
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    // ── Diálogo crear / editar ────────────────────────────────────────────────
    if (showDialog) {
        ScheduleDialog(
            initial    = editingSchedule,
            isSaving   = irrigationState.isLoading,
            onDismiss  = { showDialog = false },
            onSave     = { request ->
                val editing = editingSchedule
                if (editing == null) {
                    sensorViewModel.createIrrigationSchedule(sensor.id, request) { ok, _ ->
                        if (ok) showDialog = false
                    }
                } else {
                    sensorViewModel.updateIrrigationSchedule(sensor.id, editing.id, request) { ok, _ ->
                        if (ok) showDialog = false
                    }
                }
            }
        )
    }

    // ── Confirmación de eliminación ───────────────────────────────────────────
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title  = { Text("Eliminar programación") },
            text   = {
                val label = target.label?.takeIf { it.isNotBlank() } ?: target.start_time
                Text("¿Eliminar el riego \"$label\"?")
            },
            confirmButton = {
                TextButton(onClick = {
                    sensorViewModel.deleteIrrigationSchedule(sensor.id, target.id) {}
                    deleteTarget = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }
}

// ── Tarjeta de programación ───────────────────────────────────────────────────

@Composable
private fun ScheduleCard(
    schedule: IrrigationSchedule,
    onToggle: (Boolean) -> Unit,
    onEdit:   (IrrigationSchedule) -> Unit,
    onDelete: (IrrigationSchedule) -> Unit
) {
    val dur = schedule.duration_seconds
    val durationText = when {
        dur < 60       -> "${dur} s"
        dur % 60 == 0  -> "${dur / 60} min"
        else           -> "${dur / 60} min ${dur % 60} s"
    }

    Card(
        onClick   = { onEdit(schedule) },
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (schedule.enabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono
            Icon(
                Icons.Default.WaterDrop,
                contentDescription = null,
                tint = if (schedule.enabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))

            // Hora + etiqueta + duración
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = schedule.start_time,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (schedule.enabled)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                val subtitle = buildString {
                    schedule.label?.takeIf { it.isNotBlank() }?.let { append("$it  •  ") }
                    append(durationText)
                }
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Botón eliminar
            IconButton(onClick = { onDelete(schedule) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Toggle
            Switch(
                checked         = schedule.enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

// ── Diálogo crear / editar ────────────────────────────────────────────────────

@Composable
private fun ScheduleDialog(
    initial:  IrrigationSchedule?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave:   (IrrigationScheduleRequest) -> Unit
) {
    var label       by remember { mutableStateOf(initial?.label ?: "") }
    var hour        by remember {
        mutableStateOf(initial?.start_time?.split(":")?.getOrNull(0)?.padStart(2, '0') ?: "06")
    }
    var minute      by remember {
        mutableStateOf(initial?.start_time?.split(":")?.getOrNull(1)?.padStart(2, '0') ?: "00")
    }
    var durationSec by remember { mutableStateOf(initial?.duration_seconds ?: 300) }
    var enabled     by remember { mutableStateOf(initial?.enabled ?: true) }
    var hourError   by remember { mutableStateOf(false) }
    var minuteError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    if (initial == null) "Nuevo riego" else "Editar riego",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // ── Etiqueta ──────────────────────────────────────────────
                OutlinedTextField(
                    value         = label,
                    onValueChange = { label = it },
                    label         = { Text("Etiqueta (opcional)") },
                    placeholder   = { Text("ej. Riego mañana") },
                    singleLine    = true,
                    shape         = RoundedCornerShape(12.dp),
                    modifier      = Modifier.fillMaxWidth()
                )

                // ── Hora de inicio ────────────────────────────────────────
                Text("Hora de inicio", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value         = hour,
                        onValueChange = {
                            if (it.length <= 2 && it.all(Char::isDigit)) {
                                hour = it
                                hourError = it.toIntOrNull()?.let { v -> v !in 0..23 } ?: true
                            }
                        },
                        label         = { Text("Hora (0-23)") },
                        isError       = hourError,
                        modifier      = Modifier.weight(1f),
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape         = RoundedCornerShape(12.dp)
                    )
                    Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value         = minute,
                        onValueChange = {
                            if (it.length <= 2 && it.all(Char::isDigit)) {
                                minute = it
                                minuteError = it.toIntOrNull()?.let { v -> v !in 0..59 } ?: true
                            }
                        },
                        label         = { Text("Minuto (0-59)") },
                        isError       = minuteError,
                        modifier      = Modifier.weight(1f),
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape         = RoundedCornerShape(12.dp)
                    )
                }

                // ── Duración ──────────────────────────────────────────────
                Column {
                    Text("Duración", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    val durText = when {
                        durationSec < 60      -> "${durationSec} s"
                        durationSec % 60 == 0 -> "${durationSec / 60} min"
                        else                  -> "${durationSec / 60} min ${durationSec % 60} s"
                    }
                    Text(
                        durText,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                    // Rango: 10 s – 3600 s, pasos de 10 s → steps = 358
                    Slider(
                        value         = durationSec.toFloat(),
                        onValueChange = { durationSec = (it.toInt() / 10) * 10 },
                        valueRange    = 10f..3600f,
                        steps         = 358,
                        modifier      = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("10 s", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("1 h", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // ── Activar ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Activar programación", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                HorizontalDivider()

                // ── Botones ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(
                        onClick = {
                            val h = hour.toIntOrNull()?.coerceIn(0, 23) ?: 0
                            val m = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                            hourError   = hour.toIntOrNull()?.let { v -> v !in 0..23 } ?: true
                            minuteError = minute.toIntOrNull()?.let { v -> v !in 0..59 } ?: true
                            if (hourError || minuteError) return@Button
                            onSave(
                                IrrigationScheduleRequest(
                                    label            = label.trim().ifBlank { null },
                                    start_time       = "%02d:%02d".format(h, m),
                                    duration_seconds = durationSec,
                                    enabled          = enabled
                                )
                            )
                        },
                        enabled = !isSaving,
                        shape   = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Guardar")
                    }
                }
            }
        }
    }
}
