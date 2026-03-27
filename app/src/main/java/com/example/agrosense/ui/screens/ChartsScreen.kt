package com.example.agrosense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.data.model.SensorReading
import com.example.agrosense.ui.viewmodel.DateRange
import com.example.agrosense.ui.viewmodel.SensorViewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

// Colores de líneas de gráficas (semánticos por tipo de dato)
private val ColorTemp  = Color(0xFFE53935)  // rojo — temperatura
private val ColorAire  = Color(0xFF1565C0)  // azul — humedad aire
private val ColorSuelo = Color(0xFF4CAF50)  // verde — humedad suelo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    sensor: Sensor,
    sensorViewModel: SensorViewModel,
    onBack: () -> Unit,
    onGoAddSensor: () -> Unit
) {
    val uiState by sensorViewModel.state.collectAsState()

    LaunchedEffect(sensor.id) {
        sensorViewModel.loadReadings(sensor.id, DateRange.TODAY)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(sensor.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            sensor.location ?: "",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
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
            DateRangeSelector(
                selectedRange = uiState.selectedRange,
                onRangeSelected = { range -> sensorViewModel.selectRange(sensor.id, range) }
            )

            when {
                uiState.isLoadingReadings -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                uiState.readingsError != null -> {
                    ErrorCard(uiState.readingsError!!) {
                        sensorViewModel.loadReadings(sensor.id)
                    }
                }
                uiState.readings.isEmpty() -> {
                    EmptyDataCard(range = uiState.selectedRange, onGoAddSensor = onGoAddSensor)
                }
                else -> {
                    SummaryCards(readings = uiState.readings)

                    Text(
                        "${uiState.readingsCount} registros — ${uiState.selectedRange.label}",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    ChartCard(
                        title          = "🌡 Temperatura (°C)",
                        readings       = uiState.readings,
                        valueExtractor = { it.temperature ?: 0f },
                        lineColor      = ColorTemp,
                        range          = uiState.selectedRange
                    )
                    ChartCard(
                        title          = "💧 Humedad Aire (%)",
                        readings       = uiState.readings,
                        valueExtractor = { it.air_humidity ?: 0f },
                        lineColor      = ColorAire,
                        range          = uiState.selectedRange
                    )
                    ChartCard(
                        title          = "🌱 Humedad Suelo (%)",
                        readings       = uiState.readings,
                        valueExtractor = { it.soil_humidity ?: 0f },
                        lineColor      = ColorSuelo,
                        range          = uiState.selectedRange
                    )
                }
            }
        }
    }
}

// ── Selector de rango ──────────────────────────────────────────────────────

@Composable
fun DateRangeSelector(
    selectedRange: DateRange,
    onRangeSelected: (DateRange) -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Período", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateRange.entries.forEach { range ->
                    val isSelected = range == selectedRange
                    FilterChip(
                        selected = isSelected,
                        onClick  = { onRangeSelected(range) },
                        label    = {
                            Text(
                                range.label,
                                fontSize   = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor     = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }
}

// ── Tarjetas resumen (avg/min/max) ─────────────────────────────────────────

@Composable
fun SummaryCards(readings: List<SensorReading>) {
    val temps = readings.mapNotNull { it.temperature }
    val airH  = readings.mapNotNull { it.air_humidity }
    val soilH = readings.mapNotNull { it.soil_humidity }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryCard("🌡 Temp",  temps, "°C", Color(0xFFFFEBEE), ColorTemp,  Modifier.weight(1f))
        SummaryCard("💧 Aire",  airH,  "%",  Color(0xFFE3F2FD), ColorAire,  Modifier.weight(1f))
        SummaryCard("🌱 Suelo", soilH, "%",  Color(0xFFE8F5E9), ColorSuelo, Modifier.weight(1f))
    }
}

@Composable
fun SummaryCard(
    title: String,
    values: List<Float>,
    unit: String,
    bgColor: Color,
    accentColor: Color,
    modifier: Modifier
) {
    if (values.isEmpty()) return
    val avg = values.average()
    val min = values.min()
    val max = values.max()

    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accentColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text("%.1f%s".format(avg, unit), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accentColor)
            Text("↑ %.1f  ↓ %.1f".format(max, min), fontSize = 10.sp, color = Color.Gray)
        }
    }
}

// ── Gráfica individual ─────────────────────────────────────────────────────

@Composable
fun ChartCard(
    title: String,
    readings: List<SensorReading>,
    valueExtractor: (SensorReading) -> Float,
    lineColor: Color,
    range: DateRange
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            AndroidView(
                factory = { context ->
                    LineChart(context).apply {
                        description.isEnabled = false
                        legend.isEnabled      = false
                        setTouchEnabled(true)
                        isDragEnabled         = true
                        setScaleEnabled(true)
                        setPinchZoom(true)
                        setDrawGridBackground(false)
                        setExtraBottomOffset(12f)   // espacio para etiquetas rotadas
                        axisRight.isEnabled   = false
                        axisLeft.apply {
                            setDrawGridLines(true)
                            gridColor = android.graphics.Color.LTGRAY
                            textSize  = 10f
                        }
                        xAxis.apply {
                            position = XAxis.XAxisPosition.BOTTOM
                            setDrawGridLines(false)
                            setAvoidFirstLastClipping(true)
                            textSize = 9f
                        }
                    }
                },
                update = { chart ->
                    // Los timestamps en ms tienen 13 dígitos y Float solo soporta ~7.
                    // Usamos SEGUNDOS (dividir entre 1000) para mantener precisión.
                    val nowSec = System.currentTimeMillis() / 1000f
                    val rangeSeconds = when (range) {
                        DateRange.TODAY   -> 24 * 3600f
                        DateRange.WEEK    -> 7  * 24 * 3600f
                        DateRange.MONTH   -> 30 * 24 * 3600f
                        DateRange.QUARTER -> 90 * 24 * 3600f
                    }
                    val rangeStartSec = nowSec - rangeSeconds

                    val fmt = when (range) {
                        DateRange.TODAY   -> SimpleDateFormat("HH:mm",  Locale.getDefault())
                        DateRange.WEEK    -> SimpleDateFormat("EEE dd", Locale.getDefault())
                        DateRange.MONTH   -> SimpleDateFormat("dd/MM",  Locale.getDefault())
                        DateRange.QUARTER -> SimpleDateFormat("dd/MM",  Locale.getDefault())
                    }.apply { timeZone = TZ_CO }

                    val granSec = when (range) {
                        DateRange.TODAY   -> 3600f          // 1 hora
                        DateRange.WEEK    -> 86400f         // 1 día
                        DateRange.MONTH   -> 3 * 86400f     // 3 días
                        DateRange.QUARTER -> 7 * 86400f     // 1 semana
                    }

                    chart.xAxis.apply {
                        axisMinimum          = rangeStartSec
                        axisMaximum          = nowSec
                        isGranularityEnabled = true
                        granularity          = granSec
                        labelCount           = when (range) {
                            DateRange.TODAY   -> 6
                            DateRange.WEEK    -> 7
                            DateRange.MONTH   -> 6
                            DateRange.QUARTER -> 6
                        }
                        labelRotationAngle   = when (range) {
                            DateRange.TODAY -> 0f
                            else            -> -45f
                        }
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String =
                                try { fmt.format(Date(value.toLong() * 1000L)) } catch (_: Exception) { "" }
                        }
                    }

                    val entries = readings.mapNotNull { reading ->
                        val tsMs = parseTimestamp(reading.created_at) ?: return@mapNotNull null
                        Entry(tsMs / 1000f, valueExtractor(reading))  // segundos = precisión float OK
                    }

                    val dataSet = LineDataSet(entries, title).apply {
                        color             = lineColor.toArgb()
                        setCircleColor(lineColor.toArgb())
                        circleRadius      = if (entries.size > 60) 0f else 3f
                        setDrawCircleHole(false)
                        lineWidth         = 2f
                        setDrawValues(false)
                        mode              = LineDataSet.Mode.CUBIC_BEZIER
                        setDrawFilled(true)
                        fillColor         = lineColor.copy(alpha = 0.15f).toArgb()
                        fillAlpha         = 40
                    }

                    chart.data = if (entries.isEmpty()) null else LineData(dataSet)
                    // Evita que al hacer zoom los puntos desaparezcan
                    chart.setVisibleXRangeMinimum(granSec)
                    chart.animateX(400)
                    chart.invalidate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

private val UTC = java.util.TimeZone.getTimeZone("UTC")
private val TZ_CO = java.util.TimeZone.getTimeZone("America/Bogota")

fun parseTimestamp(ts: String?): Long? {
    if (ts == null) return null
    val formats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply { timeZone = UTC },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",     Locale.getDefault()).apply { timeZone = UTC },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",     Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss",           Locale.getDefault()).apply { timeZone = UTC }
    )
    for (fmt in formats) {
        try { return fmt.parse(ts)?.time } catch (_: Exception) {}
    }
    return null
}

@Composable
fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Row(
            modifier             = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment    = Alignment.CenterVertically
        ) {
            Text(message, color = Color(0xFFB71C1C), modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("Reintentar") }
        }
    }
}

@Composable
fun EmptyDataCard(range: DateRange, onGoAddSensor: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier            = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📊", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Sin datos para ${range.label.lowercase()}",
                fontWeight = FontWeight.SemiBold,
                fontSize   = 16.sp
            )
            Text(
                "El sensor enviará datos al servidor cada 30 segundos cuando esté conectado al WiFi.",
                fontSize = 13.sp,
                color    = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onGoAddSensor) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Agregar sensor")
            }
        }
    }
}
