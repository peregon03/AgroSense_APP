package com.example.agrosense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.data.model.SensorShare
import com.example.agrosense.data.model.SharePermissionsRequest
import com.example.agrosense.data.model.ShareSensorRequest
import com.example.agrosense.ui.viewmodel.SensorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSensorScreen(
    sensor: Sensor,
    sensorViewModel: SensorViewModel,
    onBack: () -> Unit
) {
    val sharesState by sensorViewModel.sensorSharesState.collectAsState()

    var email            by remember { mutableStateOf("") }
    var canViewGraphs    by remember { mutableStateOf(true) }
    var canSchedule      by remember { mutableStateOf(false) }
    var canControlPump   by remember { mutableStateOf(false) }
    var shareError       by remember { mutableStateOf<String?>(null) }
    var shareSuccess     by remember { mutableStateOf(false) }
    var revokeTarget     by remember { mutableStateOf<SensorShare?>(null) }

    LaunchedEffect(sensor.id) {
        sensorViewModel.loadSensorShares(sensor.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Compartir sensor", fontWeight = FontWeight.Bold)
                        Text(sensor.name, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                },
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

        LazyColumn(
            modifier        = Modifier.fillMaxSize().padding(padding),
            contentPadding  = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Progreso ──────────────────────────────────────────────────────
            if (sharesState.isLoading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            // ── Formulario agregar acceso ─────────────────────────────────────
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Agregar acceso", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium)

                        OutlinedTextField(
                            value         = email,
                            onValueChange = { email = it; shareError = null; shareSuccess = false },
                            label         = { Text("Email del usuario") },
                            singleLine    = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape         = RoundedCornerShape(12.dp),
                            modifier      = Modifier.fillMaxWidth(),
                            isError       = shareError != null
                        )

                        if (shareError != null) {
                            Text(shareError!!, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (shareSuccess) {
                            Text("Acceso compartido correctamente",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold)
                        }

                        Text("Permisos", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)

                        PermissionToggleRow(
                            label    = "Ver gráficas",
                            subtitle = "Puede ver el historial de lecturas del sensor",
                            checked  = canViewGraphs,
                            onCheckedChange = { canViewGraphs = it }
                        )
                        PermissionToggleRow(
                            label    = "Programar riego",
                            subtitle = "Puede crear y editar programaciones de riego",
                            checked  = canSchedule,
                            onCheckedChange = { canSchedule = it }
                        )
                        PermissionToggleRow(
                            label    = "Controlar motobomba",
                            subtitle = "Puede encender y apagar la bomba manualmente",
                            checked  = canControlPump,
                            onCheckedChange = { canControlPump = it }
                        )

                        Button(
                            onClick = {
                                shareError   = null
                                shareSuccess = false
                                val trimmed = email.trim()
                                if (trimmed.isEmpty()) { shareError = "Ingresa un email"; return@Button }
                                sensorViewModel.shareSensor(
                                    sensor.id,
                                    ShareSensorRequest(trimmed, canViewGraphs, canSchedule, canControlPump)
                                ) { ok, error ->
                                    if (ok) {
                                        email        = ""
                                        canViewGraphs  = true
                                        canSchedule    = false
                                        canControlPump = false
                                        shareSuccess   = true
                                    } else {
                                        shareError = error
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(12.dp),
                            enabled  = !sharesState.isLoading
                        ) {
                            Text("Compartir", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ── Accesos actuales ──────────────────────────────────────────────
            item {
                Text(
                    "Accesos actuales (${sharesState.shares.size})",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (sharesState.shares.isEmpty() && !sharesState.isLoading) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.width(12.dp))
                            Text("Este sensor no está compartido con nadie",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            items(sharesState.shares, key = { it.id }) { share ->
                ShareCard(
                    share    = share,
                    sensorId = sensor.id,
                    onPermissionsChange = { viewGraphs, schedule, controlPump ->
                        sensorViewModel.updateSharePermissions(
                            sensor.id, share.id,
                            SharePermissionsRequest(viewGraphs, schedule, controlPump)
                        )
                    },
                    onRevoke = { revokeTarget = it }
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // ── Confirmar revocar ─────────────────────────────────────────────────────
    revokeTarget?.let { share ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title  = { Text("Revocar acceso") },
            text   = { Text("¿Quitar el acceso de ${share.first_name} ${share.last_name} (${share.email})?") },
            confirmButton = {
                TextButton(onClick = {
                    sensorViewModel.revokeShare(sensor.id, share.id) {}
                    revokeTarget = null
                }) { Text("Revocar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { revokeTarget = null }) { Text("Cancelar") }
            }
        )
    }
}

// ── Tarjeta de acceso compartido ──────────────────────────────────────────────

@Composable
private fun ShareCard(
    share: SensorShare,
    sensorId: Int,
    onPermissionsChange: (viewGraphs: Boolean, schedule: Boolean, controlPump: Boolean) -> Unit,
    onRevoke: (SensorShare) -> Unit
) {
    var canViewGraphs  by remember(share.id) { mutableStateOf(share.can_view_graphs) }
    var canSchedule    by remember(share.id) { mutableStateOf(share.can_schedule) }
    var canControlPump by remember(share.id) { mutableStateOf(share.can_control_pump) }

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
                    Text("${share.first_name} ${share.last_name}",
                        fontWeight = FontWeight.SemiBold)
                    Text(share.email, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onRevoke(share) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Revocar",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text("Permisos", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))

            PermissionToggleRow(
                label    = "Ver gráficas",
                checked  = canViewGraphs,
                onCheckedChange = {
                    canViewGraphs = it
                    onPermissionsChange(it, canSchedule, canControlPump)
                }
            )
            PermissionToggleRow(
                label    = "Programar riego",
                checked  = canSchedule,
                onCheckedChange = {
                    canSchedule = it
                    onPermissionsChange(canViewGraphs, it, canControlPump)
                }
            )
            PermissionToggleRow(
                label    = "Controlar motobomba",
                checked  = canControlPump,
                onCheckedChange = {
                    canControlPump = it
                    onPermissionsChange(canViewGraphs, canSchedule, it)
                }
            )
        }
    }
}

// ── Fila de permiso con toggle ────────────────────────────────────────────────

@Composable
private fun PermissionToggleRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
