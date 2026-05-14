package com.example.agrosense.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.agrosense.data.local.entity.LocalReadingEntity
import com.example.agrosense.ui.viewmodel.BleViewModel
import com.example.agrosense.ui.viewmodel.LocalDateRange
import com.example.agrosense.ui.viewmodel.LocalViewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

// ── Paleta ────────────────────────────────────────────────────────────────────
private val LcPrimary     = Color(0xFF0D631B)
private val LcSurface     = Color(0xFFFCF9F8)
private val LcSurfaceCont = Color(0xFFF0EDED)
private val LcOnSurface   = Color(0xFF1B1C1C)
private val LcOnSurfaceVar= Color(0xFF40493D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalChartsScreen(
    deviceId: String,
    deviceName: String,
    localVm: LocalViewModel,
    bleVm: BleViewModel,
    onBack: () -> Unit
) {
    val state      by localVm.state.collectAsState()
    val isConnected by bleVm.isConnected.collectAsState()
    val bleDeviceId by bleVm.deviceId.collectAsState()
    val historyData by bleVm.historyData.collectAsState()

    val currentRange = state.selectedRange

    // Al abrir la pantalla cargamos datos del rango actual
    LaunchedEffect(deviceId) {
        localVm.loadReadings(deviceId, currentRange)
    }

    // Cuando llega historial via BLE, guardarlo y recargar
    LaunchedEffect(historyData) {
        if (historyData.isNotEmpty()) {
            localVm.saveHistoryFromBle(deviceId, historyData)
            localVm.loadReadings(deviceId, currentRange)
        }
    }

    // Si BLE está conectado al abrir, solicitar historial automáticamente
    LaunchedEffect(isConnected, bleDeviceId) {
        if (isConnected && bleDeviceId == deviceId) {
            bleVm.requestHistory()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(deviceName, fontWeight = FontWeight.Bold, color = LcOnSurface, fontSize = 15.sp)
                        Text("Historial local", style = MaterialTheme.typography.labelSmall, color = LcOnSurfaceVar)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Volver", tint = LcOnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LcSurface),
                actions = {
                    if (isConnected && bleDeviceId == deviceId) {
                        IconButton(onClick = { bleVm.requestHistory() }) {
                            Icon(Icons.Outlined.Refresh, "Actualizar historial", tint = LcPrimary)
                        }
                    }
                }
            )
        },
        containerColor = LcSurface
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            // ── Estado BLE ────────────────────────────────────────────────
            BleHistoryStatusRow(isConnected && bleDeviceId == deviceId, state.isLoadingHistory)

            Spacer(Modifier.height(12.dp))

            // ── Selector de rango ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LocalDateRange.values().forEach { range ->
                    val selected = range == currentRange
                    FilterChip(
                        selected = selected,
                        onClick  = { localVm.loadReadings(deviceId, range) },
                        label    = { Text(range.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LcPrimary,
                            selectedLabelColor     = Color.White
                        )
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Conteo de registros ───────────────────────────────────────
            val count = state.readings.size
            Text(
                "$count lecturas en el rango · máx 30 días de historial local",
                style = MaterialTheme.typography.labelSmall,
                color = LcOnSurfaceVar
            )

            Spacer(Modifier.height(16.dp))

            if (count == 0) {
                NoDataCard(isConnected && bleDeviceId == deviceId)
            } else {
                val readings = state.readings

                // ── Temperatura ───────────────────────────────────────────
                MetricChartCard(
                    title     = "Temperatura",
                    unit      = "°C",
                    color     = AndroidColor.rgb(192, 57, 43),
                    readings  = readings,
                    extractor = { it.temperature }
                )

                Spacer(Modifier.height(16.dp))

                // ── Humedad del aire ──────────────────────────────────────
                MetricChartCard(
                    title     = "Humedad del aire",
                    unit      = "%",
                    color     = AndroidColor.rgb(21, 101, 192),
                    readings  = readings,
                    extractor = { it.airHumidity }
                )

                Spacer(Modifier.height(16.dp))

                // ── CO2 ───────────────────────────────────────────────────
                MetricChartCard(
                    title     = "CO2",
                    unit      = "ppm",
                    color     = AndroidColor.rgb(106, 27, 154),
                    readings  = readings,
                    extractor = { it.co2 }
                )

                Spacer(Modifier.height(16.dp))

                // ── Metano ────────────────────────────────────────────────
                MetricChartCard(
                    title     = "Metano (CH4)",
                    unit      = "ppm",
                    color     = AndroidColor.rgb(230, 81, 0),
                    readings  = readings,
                    extractor = { it.methane }
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Tarjeta de gráfica por métrica ────────────────────────────────────────────

@Composable
private fun MetricChartCard(
    title: String,
    unit: String,
    color: Int,
    readings: List<LocalReadingEntity>,
    extractor: (LocalReadingEntity) -> Float?
) {
    val values = readings.mapNotNull { r ->
        extractor(r)?.let { v -> Pair(r.timestamp, v) }
    }

    if (values.isEmpty()) return

    val avg = values.map { it.second }.average().toFloat()
    val min = values.minOf { it.second }
    val max = values.maxOf { it.second }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Encabezado + resumen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = LcOnSurface)
                Text(unit, fontSize = 12.sp, color = LcOnSurfaceVar)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryCard("Prom", String.format("%.1f", avg), Modifier.weight(1f))
                SummaryCard("Mín",  String.format("%.1f", min), Modifier.weight(1f))
                SummaryCard("Máx",  String.format("%.1f", max), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))

            // Gráfica MPAndroidChart
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                factory = { ctx ->
                    LineChart(ctx).apply {
                        description.isEnabled  = false
                        legend.isEnabled       = false
                        setTouchEnabled(true)
                        isDragEnabled          = true
                        setScaleEnabled(true)
                        setPinchZoom(true)
                        setDrawGridBackground(false)
                        setBackgroundColor(AndroidColor.WHITE)
                        xAxis.apply {
                            setDrawGridLines(false)
                            granularity    = 1f
                            setLabelCount(4, true)
                            textColor      = AndroidColor.rgb(64, 73, 61)
                            textSize       = 10f
                        }
                        axisLeft.apply {
                            setDrawGridLines(true)
                            gridColor      = AndroidColor.argb(30, 0, 0, 0)
                            textColor      = AndroidColor.rgb(64, 73, 61)
                            textSize       = 10f
                        }
                        axisRight.isEnabled = false
                    }
                },
                update = { chart ->
                    val firstTs = values.first().first
                    val entries = values.map { (ts, v) ->
                        Entry((ts - firstTs).toFloat(), v)
                    }

                    // Formatter de tiempo relativo en X
                    chart.xAxis.valueFormatter = object : ValueFormatter() {
                        private val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        override fun getFormattedValue(value: Float): String {
                            val ts = (firstTs + value.toLong()) * 1000L
                            return sdf.format(Date(ts))
                        }
                    }

                    val dataSet = LineDataSet(entries, title).apply {
                        this.color          = color
                        setCircleColor(color)
                        circleRadius        = 2f
                        lineWidth           = 2f
                        setDrawCircleHole(false)
                        setDrawValues(false)
                        mode                = LineDataSet.Mode.CUBIC_BEZIER
                        cubicIntensity      = 0.2f
                    }

                    chart.data = LineData(dataSet)
                    chart.animateX(400)
                    chart.invalidate()
                }
            )
        }
    }
}

// ── Componentes auxiliares ────────────────────────────────────────────────────

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(LcSurfaceCont)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = LcOnSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = LcOnSurfaceVar)
    }
}

@Composable
private fun NoDataCard(isConnected: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Cloud,
                contentDescription = null,
                tint = LcOnSurfaceVar.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("Sin datos en este rango", fontWeight = FontWeight.SemiBold, color = LcOnSurfaceVar)
            Spacer(Modifier.height(6.dp))
            Text(
                if (isConnected)
                    "Toca el botón ↻ para sincronizar el historial del WROVER"
                else
                    "Conecta el sensor via BLE para obtener el historial almacenado",
                style = MaterialTheme.typography.bodySmall,
                color = LcOnSurfaceVar.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun BleHistoryStatusRow(isConnectedToThisDevice: Boolean, isLoading: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isConnectedToThisDevice) Color(0xFFE8F5E9) else LcSurfaceCont)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = LcPrimary)
        } else {
            Icon(
                Icons.Outlined.BluetoothConnected,
                null,
                tint = if (isConnectedToThisDevice) LcPrimary else LcOnSurfaceVar,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (isLoading) "Actualizando historial..."
            else if (isConnectedToThisDevice) "BLE conectado · historial auto-sincronizado al abrir"
            else "Sin BLE · Mostrando historial guardado en el teléfono",
            style = MaterialTheme.typography.labelSmall,
            color = if (isConnectedToThisDevice) LcPrimary else LcOnSurfaceVar
        )
    }
}
