package com.example.agrosense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agrosense.data.model.PumpSchedule
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.ui.viewmodel.SensorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PumpScheduleScreen(
    sensor: Sensor,
    sensorViewModel: SensorViewModel,
    onBack: () -> Unit
) {
    val scheduleState by sensorViewModel.pumpScheduleState.collectAsState()

    // Campos locales de edición
    var enabled       by remember { mutableStateOf(false) }
    var hour          by remember { mutableStateOf("06") }
    var minute        by remember { mutableStateOf("00") }
    var durationMin   by remember { mutableStateOf("30") }
    var saveSuccess   by remember { mutableStateOf(false) }

    // Cargar programación actual al entrar
    LaunchedEffect(sensor.id) {
        sensorViewModel.loadPumpSchedule(sensor.id)
    }

    // Sincronizar campos cuando llega la data
    LaunchedEffect(scheduleState.schedule) {
        scheduleState.schedule?.let { s ->
            enabled     = s.pump_schedule_enabled
            val parts   = s.pump_start_time?.split(":") ?: emptyList()
            hour        = parts.getOrNull(0)?.padStart(2, '0') ?: "06"
            minute      = parts.getOrNull(1)?.padStart(2, '0') ?: "00"
            durationMin = s.pump_duration_minutes.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Programar Riego", fontWeight = FontWeight.Bold)
                        Text(sensor.name, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor           = MaterialTheme.colorScheme.primary,
                    titleContentColor        = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            if (scheduleState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            scheduleState.error?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape  = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(it, modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            if (saveSuccess) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape  = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("¡Programación guardada!", modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Activar / desactivar ──────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Activar programación", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (enabled) "El riego seguirá el horario configurado"
                            else "El riego  no se encenderá automáticamente",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked  = enabled,
                        onCheckedChange = { enabled = it; saveSuccess = false }
                    )
                }
            }

            // ── Hora de encendido ─────────────────────────────────────────────
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Hora de encendido", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value         = hour,
                            onValueChange = {
                                if (it.length <= 2 && it.all(Char::isDigit)) {
                                    hour = it; saveSuccess = false
                                }
                            },
                            label         = { Text("Hora (0–23)") },
                            modifier      = Modifier.weight(1f),
                            singleLine    = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape         = RoundedCornerShape(12.dp),
                            enabled       = enabled
                        )
                        Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value         = minute,
                            onValueChange = {
                                if (it.length <= 2 && it.all(Char::isDigit)) {
                                    minute = it; saveSuccess = false
                                }
                            },
                            label         = { Text("Minuto (0–59)") },
                            modifier      = Modifier.weight(1f),
                            singleLine    = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape         = RoundedCornerShape(12.dp),
                            enabled       = enabled
                        )
                    }
                }
            }

            // ── Duración ──────────────────────────────────────────────────────
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Duración del riego", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Los aspersores estaran encendidos durante este tiempo desde la hora configurada",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    val dur = durationMin.toIntOrNull() ?: 0
                    Text(
                        "%d h %02d min".format(dur / 60, dur % 60),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value         = dur.toFloat(),
                        onValueChange = {
                            durationMin = it.toInt().toString()
                            saveSuccess = false
                        },
                        valueRange    = 1f..240f,
                        steps         = 238,
                        enabled       = enabled,
                        modifier      = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1 min", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("4 h", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Botón guardar ─────────────────────────────────────────────────
            Button(
                onClick = {
                    val h  = hour.toIntOrNull()?.coerceIn(0, 23) ?: 0
                    val m  = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    val dur = durationMin.toIntOrNull()?.coerceIn(1, 1440) ?: 30
                    val schedule = PumpSchedule(
                        pump_schedule_enabled  = enabled,
                        pump_start_time        = "%02d:%02d".format(h, m),
                        pump_duration_minutes  = dur
                    )
                    sensorViewModel.savePumpSchedule(sensor.id, schedule) { success ->
                        saveSuccess = success
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                enabled  = !scheduleState.isLoading
            ) {
                if (scheduleState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Guardar programación", fontWeight = FontWeight.SemiBold)
            }

            // ── Información ESP32 ─────────────────────────────────────────────
           /* Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("¿Cómo funciona?", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "El sensor ESP32 consulta el servidor periódicamente y enciende " +
                        "o apaga la bomba según el horario guardado aquí. " +
                        "No necesita estar conectado por Bluetooth.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }*/
        }
    }
}
