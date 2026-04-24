package com.example.agrosense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agrosense.data.model.DeletedSensorBackup
import com.example.agrosense.ui.viewmodel.SensorViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedSensorsScreen(
    sensorViewModel: SensorViewModel,
    onBack: () -> Unit
) {
    val state by sensorViewModel.deletedState.collectAsState()

    var restoreTarget by remember { mutableStateOf<DeletedSensorBackup?>(null) }
    var deleteTarget  by remember { mutableStateOf<DeletedSensorBackup?>(null) }
    var errorMessage  by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        sensorViewModel.loadDeletedSensors()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sensores eliminados", fontWeight = FontWeight.Bold) },
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
        }
    ) { padding ->

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }


            // Aviso de expiración
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    "Los respaldos se conservan 30 días. Después se eliminan automáticamente.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            errorMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        msg,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (state.backups.isEmpty() && !state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Sensors,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No hay sensores eliminados",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Los sensores que elimines aparecerán aquí",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.backups, key = { it.id }) { backup ->
                        DeletedSensorCard(
                            backup    = backup,
                            onRestore = { restoreTarget = it },
                            onDelete  = { deleteTarget = it }
                        )
                    }
                }
            }
        }
    }

    // ── Confirmar restauración ────────────────────────────────────────────────
    restoreTarget?.let { backup ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title  = { Text("Restaurar sensor") },
            text   = {
                Text(
                    "¿Restaurar \"${backup.sensor_data.name}\"?\n\n" +
                    "Se recuperarán el sensor, sus programaciones de riego y las últimas " +
                    "${backup.readings_count} lecturas."
                )
            },
            confirmButton = {
                Button(onClick = {
                    val target = backup
                    restoreTarget = null
                    errorMessage  = null
                    sensorViewModel.restoreSensor(target.id) { ok, error ->
                        if (!ok) errorMessage = error
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) { Text("Cancelar") }
            }
        )
    }

    // ── Confirmar eliminación permanente ──────────────────────────────────────
    deleteTarget?.let { backup ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title  = { Text("Eliminar permanentemente") },
            text   = {
                Text(
                    "¿Eliminar el respaldo de \"${backup.sensor_data.name}\" de forma permanente?\n\n" +
                    "No podrás recuperar los datos después."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = backup
                    deleteTarget = null
                    sensorViewModel.permanentlyDeleteBackup(target.id) {}
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }
}

// ── Tarjeta de sensor eliminado ───────────────────────────────────────────────

@Composable
private fun DeletedSensorCard(
    backup:    DeletedSensorBackup,
    onRestore: (DeletedSensorBackup) -> Unit,
    onDelete:  (DeletedSensorBackup) -> Unit
) {
    val deletedFormatted = formatDate(backup.deleted_at)
    val expiresFormatted = formatDate(backup.expires_at)

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        backup.sensor_data.name,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp
                    )
                    Text(
                        "ID: ${backup.sensor_data.device_id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    backup.sensor_data.location?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { onDelete(backup) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar permanentemente",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "${backup.readings_count} lecturas guardadas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Eliminado: $deletedFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Expira: $expiresFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick   = { onRestore(backup) },
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Restaurar sensor")
            }
        }
    }
}

private fun formatDate(isoDate: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale("es"))
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdf.parse(isoDate.take(19)) ?: return isoDate.take(10)
        SimpleDateFormat("dd MMM yyyy", Locale("es")).format(date)
    } catch (_: Exception) {
        isoDate.take(10)
    }
}
