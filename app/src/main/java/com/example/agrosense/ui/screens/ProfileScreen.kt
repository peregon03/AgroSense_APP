package com.example.agrosense.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.data.model.SensorReading
import com.example.agrosense.ui.viewmodel.AuthViewModel
import com.example.agrosense.ui.viewmodel.DateRange
import com.example.agrosense.ui.viewmodel.SensorUiState
import com.example.agrosense.ui.viewmodel.SensorViewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    vm: AuthViewModel,
    sensorVm: SensorViewModel,
    onRegisterSensor: () -> Unit = {},
    onViewSensors: () -> Unit = {},
    onViewShared: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
) {
    val state       by vm.state.collectAsState()
    val sensorState by sensorVm.state.collectAsState()
    val user = state.user

    // Estado del panel de gráfica rápida
    var qcSensor by remember { mutableStateOf<Sensor?>(null) }
    var qcVars   by remember { mutableStateOf(emptySet<String>()) }
    var qcRange  by remember { mutableStateOf(DateRange.TODAY) }

    LaunchedEffect(Unit) {
        vm.loadMe()
        sensorVm.loadSensors()
    }

    Scaffold(
        topBar = {
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Foto de perfil",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "AgroSense",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },

        // BottomNavBar — "Panel" activo
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick  = onNavigateHome,
                    icon     = { Icon(Icons.Filled.Dashboard, contentDescription = "Panel") },
                    label    = { Text("Panel") }
                )
                // "Perfil" navega a EditProfileScreen, que ahora contiene Cerrar Sesion
                NavigationBarItem(
                    selected = false,
                    onClick  = onEditProfile,
                    icon     = { Icon(Icons.Filled.Edit, contentDescription = "Perfil") },
                    label    = { Text("Perfil") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick  = onViewSensors,
                    icon     = { Icon(Icons.Filled.Sensors, contentDescription = "Sensores") },
                    label    = { Text("Sensores") }
                )
            }
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {

            Spacer(Modifier.height(16.dp))

            // Error card — logica original intacta
            state.error?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text     = err,
                        modifier = Modifier.padding(12.dp),
                        color    = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Saludo con nombre del usuario
            Text(
                text       = "Bienvenido Nuevamente,",
                style      = MaterialTheme.typography.bodyMedium,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text       = if (user != null) "${user.first_name} ${user.last_name}" else "...",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color      = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text       = "Acciones rapidas",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(12.dp))

            // Fila 1: Agregar sensor + Ver sensores
            Row(modifier = Modifier.fillMaxWidth()) {
                ActionCard(
                    modifier  = Modifier.weight(1f),
                    title     = "Agregar sensor",
                    subtitle  = "Buscar y vincular dispositivos",
                    icon      = Icons.Filled.AddCircle,
                    onClick   = onRegisterSensor
                )
                Spacer(Modifier.width(12.dp))
                ActionCard(
                    modifier  = Modifier.weight(1f),
                    title     = "Ver sensores",
                    subtitle  = "Listado y estado",
                    icon      = Icons.Filled.Sensors,
                    onClick   = onViewSensors
                )
            }

            Spacer(Modifier.height(12.dp))

            // Fila 2: Compartidos conmigo
            ActionCard(
                modifier  = Modifier.fillMaxWidth(),
                title     = "Compartidos conmigo",
                subtitle  = "Sensores que otros usuarios compartieron contigo",
                icon      = Icons.Filled.Group,
                onClick   = onViewShared
            )

            Spacer(Modifier.height(20.dp))

            // ── Panel de análisis rápido ──────────────────────────────────
            QuickChartPanel(
                sensors        = sensorState.sensors,
                selectedSensor = qcSensor,
                selectedVars   = qcVars,
                currentRange   = qcRange,
                state          = sensorState,
                onSensorSelect = { sensor ->
                    qcSensor = sensor
                    qcVars   = emptySet()
                    if (sensor != null) sensorVm.loadReadings(sensor.id, qcRange)
                },
                onVarsChange  = { qcVars = it },
                onRangeSelect = { range ->
                    qcRange = range
                    qcSensor?.let { sensorVm.loadReadings(it.id, range) }
                }
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text     = "AgroSense - Panel de usuario",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Panel de análisis rápido ──────────────────────────────────────────────────

private data class QcVar(
    val key:     String,
    val label:   String,
    val color:   Color,
    val extract: (SensorReading) -> Float?
)

private val ALL_QC_VARS = listOf(
    QcVar("temperature",  "Temperatura",   Color(0xFFF44336)) { it.temperature },
    QcVar("air_humidity", "Hum. Aire",     Color(0xFF2196F3)) { it.air_humidity },
    QcVar("co2",          "CO₂",           Color(0xFF9C27B0)) { it.co2 },
    QcVar("methane",      "Metano",        Color(0xFFFF9800)) { it.methane },
    QcVar("soil_temp",    "T. Suelo",      Color(0xFF795548)) { it.soil_temp },
    QcVar("soil_hum",     "H. Suelo",      Color(0xFF009688)) { it.soil_hum },
    QcVar("ec",           "Conductividad", Color(0xFFFFC107)) { it.ec },
    QcVar("ph",           "pH",            Color(0xFF3F51B5)) { it.ph },
    QcVar("nitrogen",     "Nitrógeno",     Color(0xFF4CAF50)) { it.nitrogen },
    QcVar("phosphorus",   "Fósforo",       Color(0xFFFF5722)) { it.phosphorus },
    QcVar("potassium",    "Potasio",       Color(0xFF00BCD4)) { it.potassium },
)

private fun qcParseTs(s: String): Long? {
    val fmts = arrayOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
    for (fmt in fmts) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            return sdf.parse(s)?.time
        } catch (_: Exception) { }
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickChartPanel(
    sensors:        List<Sensor>,
    selectedSensor: Sensor?,
    selectedVars:   Set<String>,
    currentRange:   DateRange,
    state:          SensorUiState,
    onSensorSelect: (Sensor?) -> Unit,
    onVarsChange:   (Set<String>) -> Unit,
    onRangeSelect:  (DateRange) -> Unit
) {
    var panelExpanded    by remember { mutableStateOf(true) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // ── Cabecera colapsable ───────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { panelExpanded = !panelExpanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.BarChart, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Análisis rápido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (selectedSensor != null) selectedSensor.name else "Selecciona un sensor",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selectedSensor != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { panelExpanded = !panelExpanded }) {
                    Icon(
                        if (panelExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = panelExpanded,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {

                    // ── Paso 1: Selector de sensor ────────────────────────
                    if (sensors.isEmpty()) {
                        QcPlaceholder(
                            icon    = Icons.Filled.Sensors,
                            message = "No tienes sensores registrados.",
                            detail  = "Registra un sensor para usar esta función."
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded         = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value         = selectedSensor?.name ?: "",
                                onValueChange = {},
                                readOnly      = true,
                                label         = { Text("Sensor") },
                                placeholder   = { Text("Selecciona un sensor…") },
                                trailingIcon  = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                                },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape    = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded         = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                sensors.forEach { sensor ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(sensor.name, fontWeight = FontWeight.Medium)
                                                if (!sensor.location.isNullOrBlank()) {
                                                    Text(
                                                        sensor.location,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Sensors, null, modifier = Modifier.size(20.dp))
                                        },
                                        onClick = { onSensorSelect(sensor); dropdownExpanded = false }
                                    )
                                }
                            }
                        }

                        if (selectedSensor == null) {
                            Spacer(Modifier.height(12.dp))
                            QcPlaceholder(
                                icon    = Icons.Filled.BarChart,
                                message = "Selecciona un sensor para continuar.",
                                detail  = null
                            )
                        } else {
                            Spacer(Modifier.height(12.dp))

                            // ── Paso 2: Rango ─────────────────────────────
                            Text(
                                "Rango de datos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier              = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DateRange.entries.forEach { range ->
                                    FilterChip(
                                        selected = currentRange == range,
                                        onClick  = { onRangeSelect(range) },
                                        label    = { Text(range.label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // ── Paso 3: Variables (máx. 4) ────────────────
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Variables",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selectedVars.size >= 4) {
                                    Text(
                                        "Máx. 4",
                                        style  = MaterialTheme.typography.labelSmall,
                                        color  = MaterialTheme.colorScheme.error,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier              = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ALL_QC_VARS.forEach { v ->
                                    val selected  = v.key in selectedVars
                                    val canSelect = selected || selectedVars.size < 4
                                    FilterChip(
                                        selected    = selected,
                                        enabled     = canSelect,
                                        onClick     = {
                                            val next = selectedVars.toMutableSet()
                                            if (selected) next.remove(v.key)
                                            else if (canSelect) next.add(v.key)
                                            onVarsChange(next)
                                        },
                                        label       = { Text(v.label, style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = if (selected) ({
                                            Box(Modifier.size(8.dp).background(v.color, CircleShape))
                                        }) else null
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // ── Área del chart ────────────────────────────
                            when {
                                state.isLoadingReadings -> {
                                    Box(
                                        modifier         = Modifier.fillMaxWidth().height(200.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                            Spacer(Modifier.height(10.dp))
                                            Text("Cargando datos…",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                state.readingsError != null -> QcPlaceholder(
                                    icon    = Icons.Filled.SignalWifiOff,
                                    message = "No se pudieron cargar los datos.",
                                    detail  = "Verifica tu conexión al servidor."
                                )
                                selectedVars.isEmpty() -> QcPlaceholder(
                                    icon    = Icons.Filled.BarChart,
                                    message = "Selecciona al menos una variable.",
                                    detail  = "Puedes combinar hasta 4 en la misma gráfica."
                                )
                                state.readings.isEmpty() -> QcPlaceholder(
                                    icon    = Icons.Filled.SignalWifi4Bar,
                                    message = "Sin datos para ${currentRange.label.lowercase()}.",
                                    detail  = "Verifica que el sensor esté encendido y enviando datos."
                                )
                                else -> {
                                    QuickLineChart(
                                        readings = state.readings,
                                        varDefs  = ALL_QC_VARS.filter { it.key in selectedVars },
                                        modifier = Modifier.fillMaxWidth().height(220.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${state.readings.size} pts · ${currentRange.label}  •  pellizca para hacer zoom",
                                        style    = MaterialTheme.typography.labelSmall,
                                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QcPlaceholder(
    icon:    ImageVector,
    message: String,
    detail:  String?
) {
    Box(
        modifier         = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null,
                modifier = Modifier.size(40.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(8.dp))
            Text(message,
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            if (detail != null) {
                Spacer(Modifier.height(4.dp))
                Text(detail,
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun QuickLineChart(
    readings: List<SensorReading>,
    varDefs:  List<QcVar>,
    modifier: Modifier = Modifier
) {
    val readingsState = rememberUpdatedState(readings)
    val varDefsState  = rememberUpdatedState(varDefs)

    AndroidView(
        modifier = modifier,
        factory  = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled    = false
                legend.isEnabled         = true
                setTouchEnabled(true)
                setPinchZoom(true)
                isDoubleTapToZoomEnabled = true
                setScaleEnabled(true)
                setDrawGridBackground(false)
                setNoDataText("Sin datos")
                extraBottomOffset = 8f

                xAxis.apply {
                    position           = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    labelRotationAngle = -30f
                    granularity        = 60f
                    setAvoidFirstLastClipping(true)
                }
                axisRight.isEnabled = false
                axisLeft.apply {
                    setDrawGridLines(true)
                    granularity          = 1f
                    isGranularityEnabled = true
                }
            }
        },
        update = { chart ->
            // El formatter lee chart.visibleXRange en tiempo de dibujo → se adapta al zoom automáticamente
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val tsMs     = value.toLong() * 1000L
                    val visRange = chart.visibleXRange  // segundos
                    val pattern  = when {
                        visRange <= 7_200f   -> "HH:mm"
                        visRange <= 86_400f  -> "HH:mm"
                        visRange <= 259_200f -> "dd/MM HH:mm"
                        else                 -> "dd/MM"
                    }
                    return try {
                        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(tsMs))
                    } catch (_: Exception) { "" }
                }
            }

            val rdgs = readingsState.value
            val vars = varDefsState.value

            var globalMinY = Float.MAX_VALUE
            var globalMaxY = -Float.MAX_VALUE

            val dataSets = vars.mapNotNull { v ->
                val entries = rdgs.mapNotNull { r ->
                    val y  = v.extract(r) ?: return@mapNotNull null
                    val ts = qcParseTs(r.created_at) ?: return@mapNotNull null
                    Entry((ts / 1000L).toFloat(), y)
                }.sortedBy { it.x }
                if (entries.isEmpty()) return@mapNotNull null

                val vMin = entries.minOf { it.y }
                val vMax = entries.maxOf { it.y }
                if (vMin < globalMinY) globalMinY = vMin
                if (vMax > globalMaxY) globalMaxY = vMax

                LineDataSet(entries, v.label).apply {
                    color          = v.color.toArgb()
                    lineWidth      = 2f
                    setDrawCircles(entries.size <= 100)
                    circleRadius   = 2.5f
                    setCircleColor(v.color.toArgb())
                    setDrawValues(false)
                    mode           = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity = 0.15f
                    setDrawFilled(false)
                }
            }

            if (dataSets.isEmpty()) { chart.data = null; chart.invalidate(); return@AndroidView }

            // Y-axis: padding suavizado (igual que ChartsScreen)
            val yRange = if (globalMaxY > globalMinY) globalMaxY - globalMinY else 1f
            val yPad   = maxOf(yRange * 0.5f, 2f)
            chart.axisLeft.apply {
                axisMinimum    = kotlin.math.floor((globalMinY - yPad).toDouble()).toFloat()
                axisMaximum    = kotlin.math.ceil((globalMaxY + yPad).toDouble()).toFloat()
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(v: Float) = "%.0f".format(v)
                }
            }

            chart.data = LineData(dataSets)
            chart.invalidate()
        }
    )
}

@Composable
private fun ActionCard(
    modifier  : Modifier = Modifier,
    title     : String,
    subtitle  : String,
    icon      : ImageVector,
    onClick   : () -> Unit
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick   = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}