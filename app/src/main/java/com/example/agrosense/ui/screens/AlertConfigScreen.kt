package com.example.agrosense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.data.model.ThresholdsRequest
import com.example.agrosense.ui.viewmodel.SensorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertConfigScreen(
    sensor: Sensor,
    sensorViewModel: SensorViewModel,
    onBack: () -> Unit
) {
    // Initialize fields from existing sensor thresholds
    var tempMin      by remember { mutableStateOf(sensor.temp_min?.toString() ?: "") }
    var tempMax      by remember { mutableStateOf(sensor.temp_max?.toString() ?: "") }
    var airHumMin    by remember { mutableStateOf(sensor.air_hum_min?.toString() ?: "") }
    var airHumMax    by remember { mutableStateOf(sensor.air_hum_max?.toString() ?: "") }
    var soilHumMin   by remember { mutableStateOf(sensor.soil_hum_min?.toString() ?: "") }
    var soilHumMax   by remember { mutableStateOf(sensor.soil_hum_max?.toString() ?: "") }

    var isSaving     by remember { mutableStateOf(false) }
    var saveError    by remember { mutableStateOf<String?>(null) }
    var saveSuccess  by remember { mutableStateOf(false) }

    fun parseField(s: String): Float? = s.trim().takeIf { it.isNotEmpty() }?.toFloatOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alertas — ${sensor.name}") },
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))

            Text(
                "Define los límites para recibir alertas. Deja un campo vacío para desactivar ese límite.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            // ── Temperatura ───────────────────────────────────────────────
            ThresholdSection(
                title    = "🌡 Temperatura (°C)",
                minValue = tempMin,
                maxValue = tempMax,
                onMinChange = { tempMin = it; saveSuccess = false },
                onMaxChange = { tempMax = it; saveSuccess = false }
            )

            Spacer(Modifier.height(16.dp))

            // ── Humedad del aire ──────────────────────────────────────────
            ThresholdSection(
                title    = "💧 Humedad del aire (%)",
                minValue = airHumMin,
                maxValue = airHumMax,
                onMinChange = { airHumMin = it; saveSuccess = false },
                onMaxChange = { airHumMax = it; saveSuccess = false }
            )

            Spacer(Modifier.height(16.dp))

            // ── Humedad del suelo ─────────────────────────────────────────
            ThresholdSection(
                title    = "🌱 Humedad del suelo (%)",
                minValue = soilHumMin,
                maxValue = soilHumMax,
                onMinChange = { soilHumMin = it; saveSuccess = false },
                onMaxChange = { soilHumMax = it; saveSuccess = false }
            )

            Spacer(Modifier.height(24.dp))

            // ── Feedback ──────────────────────────────────────────────────
            saveError?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            if (saveSuccess) {
                Text("¡Configuración guardada!", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }

            // ── Botón guardar ─────────────────────────────────────────────
            Button(
                onClick = {
                    saveError = null
                    saveSuccess = false
                    isSaving = true
                    val request = ThresholdsRequest(
                        temp_min     = parseField(tempMin),
                        temp_max     = parseField(tempMax),
                        air_hum_min  = parseField(airHumMin),
                        air_hum_max  = parseField(airHumMax),
                        soil_hum_min = parseField(soilHumMin),
                        soil_hum_max = parseField(soilHumMax)
                    )
                    sensorViewModel.saveThresholds(
                        sensorId  = sensor.id,
                        request   = request,
                        onSuccess = { isSaving = false; saveSuccess = true },
                        onError   = { msg -> isSaving = false; saveError = msg }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !isSaving
            ) {
                Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isSaving) "Guardando..." else "Guardar configuración")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ThresholdSection(
    title: String,
    minValue: String,
    maxValue: String,
    onMinChange: (String) -> Unit,
    onMaxChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = minValue,
                    onValueChange = onMinChange,
                    label = { Text("Mínimo") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("--") }
                )
                OutlinedTextField(
                    value = maxValue,
                    onValueChange = onMaxChange,
                    label = { Text("Máximo") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("--") }
                )
            }
        }
    }
}
