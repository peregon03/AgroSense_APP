package com.example.agrosense.ui.screens

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.agrosense.data.model.SensorReading
import com.example.agrosense.ui.viewmodel.SensorViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    vm: SensorViewModel,
    onBack: () -> Unit,
    onGoAddSensor: () -> Unit
) {
    val state by vm.state.collectAsState()

    var selectedSensorId by remember { mutableStateOf<Int?>(null) }
    var selectedLimit    by remember { mutableStateOf(50) }
    var expanded         by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (state.sensors.isEmpty()) vm.loadSensors()
    }
    LaunchedEffect(state.sensors) {
        if (selectedSensorId == null && state.sensors.isNotEmpty()) {
            selectedSensorId = state.sensors.first().id
        }
    }
    LaunchedEffect(selectedSensorId, selectedLimit) {
        selectedSensorId?.let { vm.loadReadings(it, selectedLimit) }
    }

    val selectedSensor = state.sensors.find { it.id == selectedSensorId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gráficas históricas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        selectedSensorId?.let { vm.loadReadings(it, selectedLimit) }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Recargar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))

            if (!state.isLoading && state.sensors.isEmpty()) {
                EmptyState(onGoAddSensor = onGoAddSensor)
                return@Column
            }

            // ── Selector de sensor ────────────────────────────────────────────
            Text("Sensor", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = selectedSensor?.name ?: "Selecciona un sensor",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.sensors.forEach { sensor ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(sensor.name)
                                    Text(sensor.device_id,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = { selectedSensorId = sensor.id; expanded = false }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Selector de rango ─────────────────────────────────────────────
            Text("Número de lecturas", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(20 to "Últimas 20", 50 to "Últimas 50", 100 to "Últimas 100").forEach { (value, label) ->
                    FilterChip(
                        selected = selectedLimit == value,
                        onClick = { selectedLimit = value },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (state.isLoadingReadings) {
                Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Cargando datos...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Column
            }

            state.readingsError?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                ) { Text(it, modifier = Modifier.padding(12.dp)) }
                return@Column
            }

            if (state.readings.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Sensors, contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Sin datos registrados aún.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Conecta el sensor para empezar a recibir datos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                    }
                }
                return@Column
            }

            val readings = state.readings
            val tempValues = readings.mapNotNull { it.temperature }
            val airValues  = readings.mapNotNull { it.air_humidity }
            val soilValues = readings.mapNotNull { it.soil_humidity }

            // ── Tarjetas de resumen ───────────────────────────────────────────
            Text("Resumen del período", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("🌡 Temperatura", tempValues, "°C", Color(0xFFE53935), Modifier.weight(1f))
                SummaryCard("💧 Hum. aire",   airValues,  "%",  Color(0xFF1E88E5), Modifier.weight(1f))
                SummaryCard("🌱 Hum. suelo",  soilValues, "%",  Color(0xFF43A047), Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Toca la gráfica para ver el valor exacto y la fecha",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // ── Las 3 gráficas ────────────────────────────────────────────────
            InteractiveChartCard(
                title    = "Temperatura",
                unit     = "°C",
                readings = readings,
                lineColor = Color(0xFFE53935),
                getValue = { it.temperature }
            )
            Spacer(Modifier.height(16.dp))
            InteractiveChartCard(
                title    = "Humedad del aire",
                unit     = "%",
                readings = readings,
                lineColor = Color(0xFF1E88E5),
                getValue = { it.air_humidity }
            )
            Spacer(Modifier.height(16.dp))
            InteractiveChartCard(
                title    = "Humedad del suelo",
                unit     = "%",
                readings = readings,
                lineColor = Color(0xFF43A047),
                getValue = { it.soil_humidity }
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "${readings.size} lecturas  •  ${formatFullTimestamp(readings.first().created_at)} → ${formatFullTimestamp(readings.last().created_at)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Gráfica interactiva ───────────────────────────────────────────────────────

@Composable
private fun InteractiveChartCard(
    title: String,
    unit: String,
    readings: List<SensorReading>,
    lineColor: Color,
    getValue: (SensorReading) -> Float?
) {
    val values = readings.mapNotNull { getValue(it) }
    if (values.isEmpty()) return

    val minVal = values.min()
    val maxVal = values.max()
    val avgVal = values.average().toFloat()
    val range  = (maxVal - minVal).coerceAtLeast(0.1f)

    var touchedIndex by remember { mutableStateOf<Int?>(null) }

    // Colores capturados fuera del Canvas (contexto Composable)
    val surfaceBg      = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val inverseOnSurf  = MaterialTheme.colorScheme.inverseOnSurface
    val inverseSurf    = MaterialTheme.colorScheme.inverseSurface

    val gridArgb     = onSurfaceColor.copy(alpha = 0.07f).toArgb()
    val labelArgb    = onSurfaceColor.copy(alpha = 0.50f).toArgb()
    val avgLineColor = lineColor.copy(alpha = 0.40f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${"%.1f".format(minVal)}$unit – ${"%.1f".format(maxVal)}$unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 12.dp, height = 2.dp).background(avgLineColor))
                Spacer(Modifier.width(4.dp))
                Text(
                    "Promedio: ${"%.1f".format(avgVal)}$unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            // Box contiene Canvas + Tooltip superpuesto
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(surfaceBg, RoundedCornerShape(10.dp))
                        .pointerInput(values.size) {
                            detectTapGestures { tapOffset ->
                                if (values.size < 2) return@detectTapGestures
                                val padLeft = 40f
                                val padRight = 12f
                                val chartW = size.width.toFloat() - padLeft - padRight
                                val stepX = chartW / (values.size - 1)
                                val idx = ((tapOffset.x - padLeft) / stepX)
                                    .roundToInt()
                                    .coerceIn(0, values.size - 1)
                                touchedIndex = if (touchedIndex == idx) null else idx
                            }
                        }
                ) {
                    val w        = size.width
                    val h        = size.height
                    val padLeft  = 40f
                    val padRight = 12f
                    val padTop   = 12f
                    val padBot   = 24f
                    val chartW   = w - padLeft - padRight
                    val chartH   = h - padTop - padBot

                    fun xOf(i: Int) = padLeft + i * chartW / (values.size - 1).coerceAtLeast(1)
                    fun yOf(v: Float) = padTop + chartH * (1f - (v - minVal) / range)

                    // Grid + etiquetas eje Y
                    val yPaint = android.graphics.Paint().apply {
                        textSize = 22f
                        isAntiAlias = true
                        color = labelArgb
                    }
                    val steps = 4
                    repeat(steps + 1) { i ->
                        val ratio = i.toFloat() / steps
                        val yLine = padTop + chartH * (1f - ratio)
                        val yLabel = minVal + range * ratio
                        drawLine(
                            color = Color(gridArgb),
                            start = Offset(padLeft, yLine),
                            end   = Offset(w - padRight, yLine),
                            strokeWidth = 1f
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "%.0f".format(yLabel),
                            4f, yLine + 4f, yPaint
                        )
                    }

                    // Línea de promedio punteada
                    val avgY = yOf(avgVal)
                    var dashX = padLeft
                    while (dashX < w - padRight) {
                        drawLine(
                            color = avgLineColor,
                            start = Offset(dashX, avgY),
                            end   = Offset((dashX + 10f).coerceAtMost(w - padRight), avgY),
                            strokeWidth = 2f
                        )
                        dashX += 18f
                    }

                    if (values.size >= 2) {
                        // Área rellena
                        val fillPath = Path().apply {
                            moveTo(xOf(0), h - padBot)
                            lineTo(xOf(0), yOf(values[0]))
                            for (i in values.indices) lineTo(xOf(i), yOf(values[i]))
                            lineTo(xOf(values.size - 1), h - padBot)
                            close()
                        }
                        drawPath(fillPath, color = lineColor.copy(alpha = 0.10f))

                        // Línea principal
                        val linePath = Path().apply {
                            moveTo(xOf(0), yOf(values[0]))
                            for (i in values.indices) lineTo(xOf(i), yOf(values[i]))
                        }
                        drawPath(linePath, color = lineColor, style = Stroke(width = 2.5f))
                    }

                    // Puntos
                    if (values.size <= 40) {
                        for (i in values.indices) {
                            drawCircle(lineColor, radius = 4f, center = Offset(xOf(i), yOf(values[i])))
                            drawCircle(Color.White, radius = 2.5f, center = Offset(xOf(i), yOf(values[i])))
                        }
                    }

                    // Punto e indicador del tocado
                    touchedIndex?.let { idx ->
                        val px = xOf(idx)
                        val py = yOf(values[idx])
                        drawLine(
                            color = lineColor.copy(alpha = 0.4f),
                            start = Offset(px, padTop),
                            end   = Offset(px, h - padBot),
                            strokeWidth = 1.5f
                        )
                        drawCircle(lineColor, radius = 8f, center = Offset(px, py))
                        drawCircle(Color.White, radius = 5f, center = Offset(px, py))
                    }

                    // Etiquetas eje X
                    val xPaint = android.graphics.Paint().apply {
                        textSize = 20f
                        isAntiAlias = true
                        color = labelArgb
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    listOf(0, values.size / 2, values.size - 1).forEach { idx ->
                        if (idx < readings.size) {
                            drawContext.canvas.nativeCanvas.drawText(
                                formatShortTime(readings[idx].created_at),
                                xOf(idx), h - 4f, xPaint
                            )
                        }
                    }
                }

                // ── Tooltip — fuera del Canvas, dentro del Box ────────────────
                val currentIdx = touchedIndex
                if (currentIdx != null && currentIdx < readings.size) {
                    val reading = readings[currentIdx]
                    val value   = getValue(reading)
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = inverseSurf),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "${"%.1f".format(value)}$unit",
                                style = MaterialTheme.typography.titleMedium,
                                color = inverseOnSurf
                            )
                            Text(
                                formatFullTimestamp(reading.created_at),
                                style = MaterialTheme.typography.bodySmall,
                                color = inverseOnSurf.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Tarjeta resumen ───────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(
    label: String,
    values: List<Float>,
    unit: String,
    cardColor: Color,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return
    val avg = values.average().toFloat()
    val max = values.max()
    val min = values.min()

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("${"%.1f".format(avg)}$unit",
                style = MaterialTheme.typography.titleMedium,
                color = cardColor)
            Text("Promedio", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("↑", style = MaterialTheme.typography.labelSmall, color = cardColor)
                    Text("${"%.1f".format(max)}", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("↓", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${"%.1f".format(min)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(onGoAddSensor: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Sensors, contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text("No tienes sensores vinculados", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("Agrega un sensor para ver sus gráficas históricas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onGoAddSensor) { Text("Vincular sensor") }
        }
    }
}

// ── Helpers fecha ─────────────────────────────────────────────────────────────

private fun formatShortTime(iso: String): String {
    return try {
        val dateTime = OffsetDateTime.parse(iso)
            .atZoneSameInstant(ZoneId.of("America/Bogota"))

        dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        ""
    }
}

private fun formatFullTimestamp(iso: String): String {
    return try {
        val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")

        val date = inputFormat.parse(iso)

        val outputFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm")
        outputFormat.timeZone = java.util.TimeZone.getTimeZone("America/Bogota")

        outputFormat.format(date!!)
    } catch (e: Exception) {
        iso.take(16)
    }
}