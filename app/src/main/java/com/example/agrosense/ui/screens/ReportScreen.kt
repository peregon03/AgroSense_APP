package com.example.agrosense.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.ui.viewmodel.SensorViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    sensor: Sensor,
    sensorViewModel: SensorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var fromMillis by remember { mutableStateOf<Long?>(null) }
    var toMillis   by remember { mutableStateOf<Long?>(null) }

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    var isGenerating by remember { mutableStateOf(false) }
    var resultFile   by remember { mutableStateOf<File?>(null) }
    var errorMsg     by remember { mutableStateOf<String?>(null) }

    val fromDateState = rememberDatePickerState()
    val toDateState   = rememberDatePickerState()

    // DatePicker devuelve milisegundos en UTC medianoche — formatear siempre en UTC
    // para evitar el desfase de un día en zonas UTC-X
    fun millisToApi(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date(millis))
    }

    fun millisToDisplay(millis: Long): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date(millis))
    }

    fun openPdf(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Abrir informe con..."))
    }

    fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Informe AgroSense — ${sensor.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir informe"))
    }

    // ── Date pickers ──────────────────────────────────────────────────────────

    if (showFromPicker) {
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    fromDateState.selectedDateMillis?.let { fromMillis = it }
                    showFromPicker = false
                    resultFile = null
                    errorMsg = null
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = fromDateState)
        }
    }

    if (showToPicker) {
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    toDateState.selectedDateMillis?.let { toMillis = it }
                    showToPicker = false
                    resultFile = null
                    errorMsg = null
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = toDateState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Generar informe PDF", fontWeight = FontWeight.Bold)
                        Text(
                            sensor.name,
                            style = MaterialTheme.typography.labelMedium,
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

            // ── Selección de rango de fechas ──────────────────────────────────
            Card(
                shape  = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Rango de fechas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedButton(
                        onClick = { showFromPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (fromMillis != null) "Desde: ${millisToDisplay(fromMillis!!)}"
                            else "Seleccionar fecha de inicio"
                        )
                    }

                    OutlinedButton(
                        onClick = { showToPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (toMillis != null) "Hasta: ${millisToDisplay(toMillis!!)}"
                            else "Seleccionar fecha de fin"
                        )
                    }

                    if (fromMillis != null && toMillis != null && fromMillis!! > toMillis!!) {
                        Text(
                            "La fecha de inicio debe ser anterior a la de fin.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // ── Contenido del informe ─────────────────────────────────────────
            Card(
                shape  = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "El informe incluirá:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    val items = listOf(
                        "Estadísticas: mínimo, promedio y máximo de humedad y temperatura",
                        "Gráficas de comportamiento a lo largo del tiempo",
                        "Análisis de tendencias y alertas automáticas",
                        "Insights y recomendaciones generadas por Inteligencia Artificial"
                    )
                    items.forEach { item ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).padding(top = 1.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(item, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // ── Error ─────────────────────────────────────────────────────────
            errorMsg?.let { msg ->
                Card(
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        msg,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ── Botón generar ─────────────────────────────────────────────────
            val canGenerate = fromMillis != null && toMillis != null &&
                              fromMillis!! <= toMillis!! && !isGenerating

            Button(
                onClick = {
                    isGenerating = true
                    errorMsg = null
                    resultFile = null
                    sensorViewModel.downloadReport(
                        sensorId  = sensor.id,
                        from      = millisToApi(fromMillis!!),
                        to        = millisToApi(toMillis!!),
                        onSuccess = { file ->
                            isGenerating = false
                            resultFile = file
                        },
                        onError = { msg ->
                            isGenerating = false
                            errorMsg = msg
                        }
                    )
                },
                enabled  = canGenerate,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Generando informe…")
                } else {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Generar informe PDF")
                }
            }

            // ── Resultado ─────────────────────────────────────────────────────
            resultFile?.let { file ->
                Card(
                    shape  = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Informe generado",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick  = { openPdf(file) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ver PDF")
                            }
                            OutlinedButton(
                                onClick  = { sharePdf(file) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Compartir")
                            }
                        }
                    }
                }
            }
        }
    }
}
