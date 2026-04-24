package com.example.agrosense.ui.screens

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// ── Paleta ─────────────────────────────────────────────────────────────────
private val ColorTemp    = Color(0xFFC0392B)   // rojo ladrillo — temperatura
private val ColorAire    = Color(0xFF1565C0)   // azul          — humedad aire
private val ColorCO2     = Color(0xFF6A1B9A)   // morado        — CO2
private val ColorMethane = Color(0xFFE65100)   // naranja       — metano

private val BgTemp    = Color(0xFFFFF8F5)
private val BgAire    = Color(0xFFF5F8FF)
private val BgCO2     = Color(0xFFF8F5FF)
private val BgMethane = Color(0xFFFFFAF0)

private val LabelMax = android.graphics.Color.parseColor("#27AE60")  // verde
private val LabelMin = android.graphics.Color.parseColor("#E74C3C")  // rojo

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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DateRangeSelector(
                selectedRange   = uiState.selectedRange,
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
                        color    = Color.Gray,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    ChartCard(
                        title          = "🌡 Temperatura",
                        readings       = uiState.readings,
                        valueExtractor = { it.temperature ?: 0f },
                        lineColor      = ColorTemp,
                        bgColor        = BgTemp,
                        range          = uiState.selectedRange,
                        unit           = "°C"
                    )
                    ChartCard(
                        title          = "💧 Humedad Aire",
                        readings       = uiState.readings,
                        valueExtractor = { it.air_humidity ?: 0f },
                        lineColor      = ColorAire,
                        bgColor        = BgAire,
                        range          = uiState.selectedRange,
                        unit           = "%"
                    )
                    ChartCard(
                        title          = "🌫 CO₂",
                        readings       = uiState.readings,
                        valueExtractor = { it.co2 ?: 0f },
                        lineColor      = ColorCO2,
                        bgColor        = BgCO2,
                        range          = uiState.selectedRange,
                        unit           = "ppm"
                    )
                    ChartCard(
                        title          = "🔥 Metano",
                        readings       = uiState.readings,
                        valueExtractor = { it.methane ?: 0f },
                        lineColor      = ColorMethane,
                        bgColor        = BgMethane,
                        range          = uiState.selectedRange,
                        unit           = "ppm"
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
            Text(
                "Período",
                fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp,
                color      = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    val temps   = readings.mapNotNull { it.temperature }
    val airH    = readings.mapNotNull { it.air_humidity }
    val co2     = readings.mapNotNull { it.co2 }
    val methane = readings.mapNotNull { it.methane }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryCard("🌡 Temp",   temps,   "°C",  Color(0xFFFFEBEE), ColorTemp,    Modifier.weight(1f))
        SummaryCard("💧 Aire",   airH,    "%",   Color(0xFFE3F2FD), ColorAire,    Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryCard("🌫 CO₂",    co2,     "ppm", Color(0xFFF3E5F5), ColorCO2,     Modifier.weight(1f))
        SummaryCard("🔥 Metano", methane, "ppm", Color(0xFFFFF8E1), ColorMethane, Modifier.weight(1f))
    }
}

@Composable
fun SummaryCard(
    title:       String,
    values:      List<Float>,
    unit:        String,
    bgColor:     Color,
    accentColor: Color,
    modifier:    Modifier
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
    title:          String,
    readings:       List<SensorReading>,
    valueExtractor: (SensorReading) -> Float,
    lineColor:      Color,
    bgColor:        Color,
    range:          DateRange,
    unit:           String = ""
) {
    // Último dato para el recuadro overlay
    val lastEntry = remember(readings) {
        readings
            .mapNotNull { r ->
                val ts = parseTimestamp(r.created_at) ?: return@mapNotNull null
                Triple(ts, valueExtractor(r), r.created_at)
            }
            .maxByOrNull { it.first }
    }
    val lastValue = lastEntry?.second
    val lastTime  = lastEntry?.first?.let { ts ->
        try {
            SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault())
                .apply { timeZone = TZ_CO }
                .format(Date(ts))
        } catch (_: Exception) { null }
    }

    var overlayOffset by remember(readings) { mutableStateOf(Offset.Zero) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {

            // ── Título con barra de color vertical ────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(start = 0.dp, top = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(22.dp)
                        .background(lineColor, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text       = if (unit.isNotEmpty()) "$title ($unit)" else title,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                    color      = lineColor
                )
            }

            Box {
                AndroidView(
                    factory = { context ->
                        LineChart(context).also { chart ->
                            chart.description.isEnabled = false
                            chart.legend.isEnabled      = false
                            chart.setTouchEnabled(true)
                            chart.isDragEnabled         = true
                            chart.setScaleEnabled(true)
                            chart.setPinchZoom(true)
                            chart.setDrawGridBackground(false)
                            chart.setDrawBorders(false)
                            chart.setExtraBottomOffset(14f)
                            chart.setExtraTopOffset(18f)    // espacio para etiqueta máximo
                            chart.axisRight.isEnabled   = false

                            chart.axisLeft.apply {
                                setDrawGridLines(true)
                                gridColor      = android.graphics.Color.parseColor("#EEEEEE")
                                gridLineWidth  = 0.4f
                                textColor      = android.graphics.Color.parseColor("#AAAAAA")
                                textSize       = 9f
                                setDrawAxisLine(false)
                            }
                            chart.xAxis.apply {
                                position = XAxis.XAxisPosition.BOTTOM
                                setDrawGridLines(false)
                                setAvoidFirstLastClipping(true)
                                textColor      = android.graphics.Color.parseColor("#AAAAAA")
                                textSize       = 9f
                                setDrawAxisLine(false)
                            }

                            // ── Listener: actualiza eje X y marcadores min/max al hacer zoom/pan
                            chart.setOnChartGestureListener(object : OnChartGestureListener {

                                fun refreshAll() {
                                    // Solo actualiza el formato del eje X según el zoom actual
                                    val vis = chart.visibleXRange
                                    val (gran, fmt) = when {
                                        vis <= 2   * 3600f  -> 900f        to "HH:mm"
                                        vis <= 24  * 3600f  -> 3600f       to "HH:mm"
                                        vis <= 3   * 86400f -> 3600f       to "EEE HH:mm"
                                        vis <= 30  * 86400f -> 3 * 86400f  to "dd/MM"
                                        else                -> 7 * 86400f  to "MMM yy"
                                    }
                                    chart.xAxis.granularity    = gran
                                    chart.xAxis.labelCount     = 6
                                    chart.xAxis.valueFormatter = object : ValueFormatter() {
                                        override fun getFormattedValue(value: Float): String =
                                            try {
                                                SimpleDateFormat(fmt, Locale.getDefault())
                                                    .apply { timeZone = TZ_CO }
                                                    .format(Date(value.toLong() * 1000L))
                                            } catch (_: Exception) { "" }
                                    }
                                    chart.invalidate()
                                }

                                override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) = refreshAll()
                                override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float)     = refreshAll()
                                override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
                                override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?)   {}
                                override fun onChartLongPressed(me: MotionEvent?)  {}
                                override fun onChartDoubleTapped(me: MotionEvent?) {}
                                override fun onChartSingleTapped(me: MotionEvent?) {}
                                override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
                            })
                        }
                    },
                    update = { chart ->
                        chart.marker = CustomMarkerView(chart.context, unit, range)
                        chart.setBackgroundColor(bgColor.toArgb())

                        val granSec = when (range) {
                            DateRange.TODAY   -> 3600f
                            DateRange.WEEK    -> 86400f
                            DateRange.MONTH   -> 3 * 86400f
                            DateRange.QUARTER -> 7 * 86400f
                        }
                        val initialFmt = when (range) {
                            DateRange.TODAY   -> SimpleDateFormat("HH:mm",  Locale.getDefault())
                            DateRange.WEEK    -> SimpleDateFormat("EEE dd", Locale.getDefault())
                            DateRange.MONTH   -> SimpleDateFormat("dd/MM",  Locale.getDefault())
                            DateRange.QUARTER -> SimpleDateFormat("dd/MM",  Locale.getDefault())
                        }.apply { timeZone = TZ_CO }

                        val gapThresholdSec = when (range) {
                            DateRange.TODAY   -> 2  * 3600f
                            DateRange.WEEK    -> 12 * 3600f
                            DateRange.MONTH   -> 2  * 86400f
                            DateRange.QUARTER -> 7  * 86400f
                        }

                        val allEntries = readings.mapNotNull { r ->
                            val tsMs = parseTimestamp(r.created_at) ?: return@mapNotNull null
                            Entry(tsMs / 1000f, valueExtractor(r))
                        }.sortedBy { it.x }

                        if (allEntries.isEmpty()) {
                            chart.data = null
                            chart.invalidate()
                            return@AndroidView
                        }

                        // Eje X ajustado al rango real de los datos
                        val dataMinX = allEntries.first().x
                        val dataMaxX = allEntries.last().x
                        val dataPad  = maxOf((dataMaxX - dataMinX) * 0.03f, granSec * 0.5f)
                        chart.xAxis.apply {
                            axisMinimum          = dataMinX - dataPad
                            axisMaximum          = dataMaxX + dataPad
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
                                    try { initialFmt.format(Date(value.toLong() * 1000L)) } catch (_: Exception) { "" }
                            }
                        }

                        // Partir datos en segmentos por huecos de tiempo
                        val segments = mutableListOf<MutableList<Entry>>()
                        var current  = mutableListOf(allEntries.first())
                        for (i in 1 until allEntries.size) {
                            if (allEntries[i].x - allEntries[i - 1].x > gapThresholdSec) {
                                segments += current
                                current = mutableListOf()
                            }
                            current += allEntries[i]
                        }
                        segments += current

                        // Segmentos principales — sin etiquetas de valor
                        val allSets = mutableListOf<ILineDataSet>()
                        segments.forEach { seg ->
                            val gradient = android.graphics.drawable.GradientDrawable(
                                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                                intArrayOf(
                                    lineColor.copy(alpha = 0.45f).toArgb(),
                                    android.graphics.Color.TRANSPARENT
                                )
                            )
                            allSets += LineDataSet(seg, title).apply {
                                color          = lineColor.toArgb()
                                setCircleColor(lineColor.toArgb())
                                circleRadius   = if (allEntries.size > 60) 0f else 1.5f
                                setDrawCircleHole(false)
                                lineWidth      = 2f
                                setDrawValues(false)   // sin etiquetas en los datos normales
                                mode           = LineDataSet.Mode.LINEAR
                                setDrawFilled(true)
                                fillDrawable   = gradient
                            }
                        }

                        // Datasets marcadores: UN solo punto cada uno (max = verde, min = rojo)
                        // Solo aparece la etiqueta exactamente en el máximo y mínimo global.
                        fun markerSet(entry: Entry, color: Int, label: String) =
                            LineDataSet(listOf(entry), label).apply {
                                this.color     = android.graphics.Color.TRANSPARENT
                                lineWidth      = 0f
                                setDrawCircles(false)
                                setDrawFilled(false)
                                setDrawValues(true)
                                valueTextSize  = 9f
                                setValueTextColor(color)
                                isHighlightEnabled = false
                                valueFormatter = object : ValueFormatter() {
                                    override fun getFormattedValue(value: Float) =
                                        "%.1f".format(value) + unit
                                }
                            }

                        allEntries.maxByOrNull { it.y }?.let { allSets += markerSet(it, LabelMax, "MAX_MARKER") }
                        allEntries.minByOrNull { it.y }?.let { allSets += markerSet(it, LabelMin, "MIN_MARKER") }

                        chart.data = LineData(allSets)
                        chart.setVisibleXRangeMinimum(granSec)
                        chart.animateX(400)
                        chart.invalidate()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(bottom = 4.dp)
                )

                // ── Recuadro último valor — arrastrable ─────────────────────
                lastValue?.let { value ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset { IntOffset(overlayOffset.x.roundToInt(), overlayOffset.y.roundToInt()) }
                            .padding(6.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    overlayOffset = Offset(
                                        overlayOffset.x + dragAmount.x,
                                        overlayOffset.y + dragAmount.y
                                    )
                                }
                            },
                        shape     = RoundedCornerShape(6.dp),
                        colors    = CardDefaults.cardColors(containerColor = lineColor.copy(alpha = 0.88f)),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(
                            modifier            = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text       = "%.1f%s".format(value, unit),
                                color      = Color.White,
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (lastTime != null) {
                                Text(
                                    text     = lastTime,
                                    color    = Color.White.copy(alpha = 0.9f),
                                    fontSize = 7.5.sp
                                )
                            }
                            Text(
                                text     = "último dato",
                                color    = Color.White.copy(alpha = 0.65f),
                                fontSize = 7.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

private val UTC   = java.util.TimeZone.getTimeZone("UTC")
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
            modifier              = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
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
