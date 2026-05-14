package com.example.agrosense.ui.screens

import android.view.MotionEvent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import java.text.SimpleDateFormat
import java.util.*

// ── Paleta ─────────────────────────────────────────────────────────────────
private val ColorTemp     = Color(0xFFC0392B)
private val ColorAire     = Color(0xFF1565C0)
private val ColorCO2      = Color(0xFF6A1B9A)
private val ColorMethane  = Color(0xFFE65100)
private val ColorSoilTemp = Color(0xFF795548)
private val ColorSoilHum  = Color(0xFF00796B)
private val ColorEC       = Color(0xFFF57F17)
private val ColorPH       = Color(0xFF283593)
private val ColorN        = Color(0xFF2E7D32)
private val ColorP        = Color(0xFFBF360C)
private val ColorK        = Color(0xFF00838F)

private val LabelMax = android.graphics.Color.parseColor("#27AE60")
private val LabelMin = android.graphics.Color.parseColor("#E74C3C")

// ── Definición de variable ─────────────────────────────────────────────────
private data class ChartVariable(
    val key:       String,
    val label:     String,
    val unit:      String,
    val color:     Color,
    val extractor: (SensorReading) -> Float?
)

private val AIR_VARIABLES = listOf(
    ChartVariable("temperature",  "Temp Aire",     "°C",    ColorTemp)    { it.temperature },
    ChartVariable("air_humidity", "Hum Aire",      "%",     ColorAire)    { it.air_humidity },
    ChartVariable("co2",          "CO₂",           "ppm",   ColorCO2)     { it.co2 },
    ChartVariable("methane",      "Metano",        "ppm",   ColorMethane) { it.methane },
)

private val SOIL_VARIABLES = listOf(
    ChartVariable("soil_temp",   "Temp Suelo",    "°C",    ColorSoilTemp) { it.soil_temp },
    ChartVariable("soil_hum",    "Hum Suelo",     "%",     ColorSoilHum)  { it.soil_hum },
    ChartVariable("ec",          "Conductividad", "µS/cm", ColorEC)       { it.ec },
    ChartVariable("ph",          "pH",            "",      ColorPH)       { it.ph },
    ChartVariable("nitrogen",    "Nitrógeno",     "mg/kg", ColorN)        { it.nitrogen },
    ChartVariable("phosphorus",  "Fósforo",       "mg/kg", ColorP)        { it.phosphorus },
    ChartVariable("potassium",   "Potasio",       "mg/kg", ColorK)        { it.potassium },
)

// ══════════════════════════════════════════════════════════════════════════
//  Pantalla principal
// ══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    sensor: Sensor,
    sensorViewModel: SensorViewModel,
    onBack: () -> Unit,
    onGoAddSensor: () -> Unit
) {
    val uiState by sensorViewModel.state.collectAsState()

    // Variables seleccionadas para cada gráfica
    var selectedAirKeys  by remember { mutableStateOf(setOf("temperature", "air_humidity")) }
    var selectedSoilKeys by remember { mutableStateOf(emptySet<String>()) }

    // Gadgets activos (tarjetas de estadísticas) — independiente de la selección de gráfica
    var shownAirGadgets  by remember { mutableStateOf(emptySet<String>()) }
    var shownSoilGadgets by remember { mutableStateOf(emptySet<String>()) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    ErrorCard(uiState.readingsError!!) { sensorViewModel.loadReadings(sensor.id) }
                }
                uiState.readings.isEmpty() -> {
                    EmptyDataCard(range = uiState.selectedRange, onGoAddSensor = onGoAddSensor)
                }
                else -> {
                    Text(
                        "${uiState.readingsCount} registros — ${uiState.selectedRange.label}",
                        fontSize = 11.sp,
                        color    = Color.Gray,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    // ── SECCIÓN AMBIENTE ──────────────────────────────────
                    SectionCard(
                        title     = "Ambiente",
                        emoji     = "🌡",
                        variables = AIR_VARIABLES,
                        selectedKeys = selectedAirKeys,
                        onToggle  = { key, checked ->
                            selectedAirKeys = if (checked) selectedAirKeys + key
                                             else          selectedAirKeys - key
                            if (!checked) shownAirGadgets = shownAirGadgets - key
                        }
                    )

                    if (selectedAirKeys.isNotEmpty()) {
                        val airVars = AIR_VARIABLES.filter { it.key in selectedAirKeys }

                        GadgetsSection(
                            availableVars = airVars,
                            shownGadgets  = shownAirGadgets,
                            readings      = uiState.readings,
                            onToggle      = { key ->
                                shownAirGadgets = if (key in shownAirGadgets)
                                    shownAirGadgets - key else shownAirGadgets + key
                            }
                        )

                        MultiVariableChart(
                            readings     = uiState.readings,
                            selectedVars = airVars,
                            range        = uiState.selectedRange
                        )
                    } else {
                        HintCard("Selecciona al menos una variable de ambiente.")
                    }

                    Spacer(Modifier.height(4.dp))

                    // ── SECCIÓN SUELO ─────────────────────────────────────
                    SectionCard(
                        title     = "Suelo",
                        emoji     = "🌱",
                        variables = SOIL_VARIABLES,
                        selectedKeys = selectedSoilKeys,
                        onToggle  = { key, checked ->
                            selectedSoilKeys = if (checked) selectedSoilKeys + key
                                              else          selectedSoilKeys - key
                            if (!checked) shownSoilGadgets = shownSoilGadgets - key
                        }
                    )

                    if (selectedSoilKeys.isNotEmpty()) {
                        val soilVars = SOIL_VARIABLES.filter { it.key in selectedSoilKeys }

                        GadgetsSection(
                            availableVars = soilVars,
                            shownGadgets  = shownSoilGadgets,
                            readings      = uiState.readings,
                            onToggle      = { key ->
                                shownSoilGadgets = if (key in shownSoilGadgets)
                                    shownSoilGadgets - key else shownSoilGadgets + key
                            }
                        )

                        MultiVariableChart(
                            readings     = uiState.readings,
                            selectedVars = soilVars,
                            range        = uiState.selectedRange
                        )
                    } else {
                        HintCard("Selecciona al menos una variable de suelo.")
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
//  Tarjeta de sección con selector de variables
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionCard(
    title:        String,
    emoji:        String,
    variables:    List<ChartVariable>,
    selectedKeys: Set<String>,
    onToggle:     (String, Boolean) -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Chips de variables — primera fila (hasta 4)
            val row1 = variables.take(4)
            val row2 = variables.drop(4)

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row1.forEach { v ->
                    VariableChip(
                        variable = v,
                        selected = v.key in selectedKeys,
                        onToggle = onToggle,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (row2.isNotEmpty()) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row2.forEach { v ->
                        VariableChip(
                            variable = v,
                            selected = v.key in selectedKeys,
                            onToggle = onToggle,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Relleno para alinear chips a la izquierda
                    repeat(4 - row2.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun VariableChip(
    variable: ChartVariable,
    selected: Boolean,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick  = { onToggle(variable.key, !selected) },
        label    = {
            Text(
                variable.label,
                fontSize   = 9.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines   = 1
            )
        },
        modifier = modifier,
        colors   = FilterChipDefaults.filterChipColors(
            selectedContainerColor = variable.color,
            selectedLabelColor     = Color.White
        )
    )
}

// ══════════════════════════════════════════════════════════════════════════
//  Sección de gadgets (estadísticas descartables)
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun GadgetsSection(
    availableVars: List<ChartVariable>,
    shownGadgets:  Set<String>,
    readings:      List<SensorReading>,
    onToggle:      (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Fila de chips para agregar/quitar gadgets
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                "Estadísticas:",
                fontSize = 10.sp,
                color    = Color.Gray
            )
            availableVars.forEach { v ->
                val active = v.key in shownGadgets
                FilterChip(
                    selected = active,
                    onClick  = { onToggle(v.key) },
                    label    = { Text(v.label, fontSize = 10.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector        = if (active) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = null,
                            modifier           = Modifier.size(12.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = v.color.copy(alpha = 0.15f),
                        selectedLabelColor     = v.color,
                        selectedLeadingIconColor = v.color
                    )
                )
            }
        }

        // Gadget cards activos
        if (shownGadgets.isNotEmpty()) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableVars
                    .filter { it.key in shownGadgets }
                    .forEach { v ->
                        GadgetCard(
                            variable  = v,
                            readings  = readings,
                            onDismiss = { onToggle(v.key) }
                        )
                    }
            }
        }
    }
}

@Composable
private fun GadgetCard(
    variable:  ChartVariable,
    readings:  List<SensorReading>,
    onDismiss: () -> Unit
) {
    val validReadings = readings.filter { variable.extractor(it) != null }
    val values        = validReadings.mapNotNull { variable.extractor(it) }
    val avg = if (values.isNotEmpty()) values.average() else null
    val min = if (values.isNotEmpty()) values.min()    else null
    val max = if (values.isNotEmpty()) values.max()    else null

    // Último dato registrado con su timestamp
    val lastEntry = validReadings
        .mapNotNull { r -> parseTimestamp(r.created_at)?.let { ts -> ts to r } }
        .maxByOrNull { it.first }
    val lastValue = lastEntry?.second?.let { variable.extractor(it) }
    val lastTime  = lastEntry?.first?.let { ts ->
        try {
            SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault())
                .apply { timeZone = TZ_CO }
                .format(Date(ts))
        } catch (_: Exception) { null }
    }

    Card(
        modifier  = Modifier.width(136.dp),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = variable.color.copy(alpha = 0.09f)),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {

            // ── Encabezado ────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    variable.label,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = variable.color,
                    modifier   = Modifier.weight(1f),
                    maxLines   = 1
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Quitar gadget",
                        modifier = Modifier.size(13.dp),
                        tint     = Color.Gray
                    )
                }
            }

            if (avg != null && min != null && max != null) {

                // ── Prom | Último ─────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Prom.", fontSize = 7.sp, color = Color.Gray)
                        Text(
                            "%.1f%s".format(avg, variable.unit),
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color      = variable.color,
                            maxLines   = 1
                        )
                    }
                    if (lastValue != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Último", fontSize = 7.sp, color = Color.Gray)
                            Text(
                                "%.1f%s".format(lastValue, variable.unit),
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color      = variable.color.copy(alpha = 0.70f),
                                maxLines   = 1
                            )
                        }
                    }
                }

                // ── Máx / Mín ────────────────────────────────────────────
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("↑", fontSize = 9.sp, color = Color(0xFF27AE60), fontWeight = FontWeight.Bold)
                        Text("%.1f".format(max), fontSize = 9.sp, color = Color(0xFF27AE60))
                    }
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("↓", fontSize = 9.sp, color = Color(0xFFE74C3C), fontWeight = FontWeight.Bold)
                        Text("%.1f".format(min), fontSize = 9.sp, color = Color(0xFFE74C3C))
                    }
                }

                // ── Timestamp del último dato ─────────────────────────────
                if (lastTime != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        lastTime,
                        fontSize = 7.5.sp,
                        color    = Color.Gray,
                        maxLines = 1
                    )
                }
            } else {
                Text("Sin datos", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
//  Gráfica multi-variable
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun MultiVariableChart(
    readings:     List<SensorReading>,
    selectedVars: List<ChartVariable>,
    range:        DateRange
) {
    val gapThresholdSec = when (range) {
        DateRange.TODAY   -> 2  * 3600f
        DateRange.WEEK    -> 12 * 3600f
        DateRange.MONTH   -> 2  * 86400f
        DateRange.QUARTER -> 7  * 86400f
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            // Leyenda de colores
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                selectedVars.forEach { v ->
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(width = 14.dp, height = 3.dp),
                            color    = v.color,
                            shape    = RoundedCornerShape(2.dp)
                        ) {}
                        Text(
                            text     = if (v.unit.isNotEmpty()) "${v.label} (${v.unit})" else v.label,
                            fontSize = 10.sp,
                            color    = v.color,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

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
                        chart.setExtraTopOffset(8f)
                        chart.axisRight.isEnabled   = false

                        chart.axisLeft.apply {
                            setDrawGridLines(true)
                            gridColor     = android.graphics.Color.parseColor("#EEEEEE")
                            gridLineWidth = 0.4f
                            textColor     = android.graphics.Color.parseColor("#AAAAAA")
                            textSize      = 9f
                            setDrawAxisLine(false)
                        }
                        chart.xAxis.apply {
                            position = XAxis.XAxisPosition.BOTTOM
                            setDrawGridLines(false)
                            setAvoidFirstLastClipping(true)
                            textColor = android.graphics.Color.parseColor("#AAAAAA")
                            textSize  = 9f
                            setDrawAxisLine(false)
                        }

                        chart.setOnChartGestureListener(object : OnChartGestureListener {
                            fun refresh() {
                                val vis = chart.visibleXRange
                                val (gran, fmt) = when {
                                    vis <= 2  * 3600f  -> 900f       to "HH:mm"
                                    vis <= 24 * 3600f  -> 3600f      to "HH:mm"
                                    vis <= 3  * 86400f -> 3600f      to "EEE HH:mm"
                                    vis <= 30 * 86400f -> 3 * 86400f to "dd/MM"
                                    else               -> 7 * 86400f to "MMM yy"
                                }
                                chart.xAxis.granularity    = gran
                                chart.xAxis.labelCount     = 6
                                chart.xAxis.valueFormatter = object : ValueFormatter() {
                                    override fun getFormattedValue(value: Float) =
                                        try {
                                            SimpleDateFormat(fmt, Locale.getDefault())
                                                .apply { timeZone = TZ_CO }
                                                .format(Date(value.toLong() * 1000L))
                                        } catch (_: Exception) { "" }
                                }
                                chart.invalidate()
                            }
                            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) = refresh()
                            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float)     = refresh()
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

                    val allSets = mutableListOf<ILineDataSet>()
                    var globalMinX = Float.MAX_VALUE
                    var globalMaxX = -Float.MAX_VALUE
                    var globalMinY = Float.MAX_VALUE
                    var globalMaxY = -Float.MAX_VALUE

                    for (v in selectedVars) {
                        val entries = readings.mapNotNull { r ->
                            val tsMs  = parseTimestamp(r.created_at) ?: return@mapNotNull null
                            val value = v.extractor(r)              ?: return@mapNotNull null
                            Entry(tsMs / 1000f, value)
                        }.sortedBy { it.x }

                        if (entries.isEmpty()) continue

                        if (entries.first().x < globalMinX) globalMinX = entries.first().x
                        if (entries.last().x  > globalMaxX) globalMaxX = entries.last().x
                        val vMin = entries.minOf { it.y }
                        val vMax = entries.maxOf { it.y }
                        if (vMin < globalMinY) globalMinY = vMin
                        if (vMax > globalMaxY) globalMaxY = vMax

                        // Segmentar por huecos de tiempo
                        val segments = mutableListOf<MutableList<Entry>>()
                        var current  = mutableListOf(entries.first())
                        for (i in 1 until entries.size) {
                            if (entries[i].x - entries[i - 1].x > gapThresholdSec) {
                                segments += current; current = mutableListOf()
                            }
                            current += entries[i]
                        }
                        segments += current

                        segments.forEach { seg ->
                            allSets += LineDataSet(seg, v.label).apply {
                                color          = v.color.toArgb()
                                setCircleColor(v.color.toArgb())
                                circleRadius   = if (entries.size > 60) 0f else 1.5f
                                setDrawCircleHole(false)
                                lineWidth      = 2f
                                setDrawValues(false)
                                mode           = LineDataSet.Mode.LINEAR
                                setDrawFilled(false)
                                isHighlightEnabled = true
                            }
                        }

                        // Marcadores min/max solo con una variable seleccionada
                        if (selectedVars.size == 1) {
                            fun markerSet(entry: Entry, argbColor: Int, lbl: String) =
                                LineDataSet(listOf(entry), lbl).apply {
                                    this.color     = android.graphics.Color.TRANSPARENT
                                    lineWidth      = 0f
                                    setDrawCircles(false)
                                    setDrawFilled(false)
                                    setDrawValues(true)
                                    valueTextSize  = 9f
                                    setValueTextColor(argbColor)
                                    isHighlightEnabled = false
                                    valueFormatter = object : ValueFormatter() {
                                        override fun getFormattedValue(value: Float) =
                                            "%.1f${v.unit}".format(value)
                                    }
                                }
                            entries.maxByOrNull { it.y }?.let { allSets += markerSet(it, LabelMax, "MAX") }
                            entries.minByOrNull { it.y }?.let { allSets += markerSet(it, LabelMin, "MIN") }
                        }
                    }

                    if (allSets.isEmpty()) {
                        chart.data = null
                        chart.setNoDataText("Sin datos para las variables seleccionadas")
                        chart.invalidate()
                        return@AndroidView
                    }

                    // ── Eje Y amortiguado ─────────────────────────────────────────────────
                    // Padding mínimo = máx(50 % del rango real, 2 unidades) en cada extremo.
                    // Así, una variación de 0.3 en datos estables no infla visualmente la línea.
                    val yFloor = kotlin.math.floor(globalMinY.toDouble()).toFloat()
                    val yCeil  = kotlin.math.ceil(globalMaxY.toDouble()).toFloat()
                    val yRange = yCeil - yFloor
                    val yPad   = maxOf(yRange * 0.5f, 2f)

                    chart.axisLeft.apply {
                        axisMinimum          = yFloor - yPad
                        axisMaximum          = yCeil  + yPad
                        isGranularityEnabled = true
                        granularity          = 1f          // etiquetas en enteros
                        labelCount           = 5
                        valueFormatter       = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float) =
                                "%.0f".format(value)
                        }
                    }

                    val dataPad = maxOf((globalMaxX - globalMinX) * 0.03f, granSec * 0.5f)
                    chart.xAxis.apply {
                        axisMinimum          = globalMinX - dataPad
                        axisMaximum          = globalMaxX + dataPad
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

                    chart.data = LineData(allSets)
                    chart.setVisibleXRangeMinimum(granSec)
                    chart.animateX(400)
                    chart.invalidate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(bottom = 4.dp)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
//  Selector de rango de fecha
// ══════════════════════════════════════════════════════════════════════════

@Composable
fun DateRangeSelector(
    selectedRange:   DateRange,
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

// ── Helpers ────────────────────────────────────────────────────────────────

@Composable
private fun HintCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape    = RoundedCornerShape(10.dp)
    ) {
        Text(
            text     = text,
            modifier = Modifier.padding(12.dp),
            fontSize = 12.sp,
            color    = Color.Gray
        )
    }
}

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
