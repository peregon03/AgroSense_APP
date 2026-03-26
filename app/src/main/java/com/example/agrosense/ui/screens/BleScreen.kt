package com.example.agrosense.ui.screens

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.agrosense.ui.viewmodel.BleViewModel
import com.example.agrosense.ui.viewmodel.SensorViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BleScreen(
    viewModel: BleViewModel,
    sensorViewModel: SensorViewModel,
    onBack: () -> Unit = {},
    onSensorRegistered: () -> Unit = {}
) {
    val devices     by viewModel.devices.collectAsState()
    val deviceId    by viewModel.deviceId.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val sensorState by sensorViewModel.state.collectAsState()

    LaunchedEffect(Unit) { sensorViewModel.loadSensors() }

    // MACs ya registradas en minúsculas para comparar correctamente
    val registeredDeviceIds = sensorState.sensors.map { it.device_id.lowercase() }.toSet()

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    val permissionsState = rememberMultiplePermissionsState(permissions)

    var isScanning            by remember { mutableStateOf(false) }
    var selectedDevice        by remember { mutableStateOf<BluetoothDevice?>(null) }
    var showRegisterDialog    by remember { mutableStateOf(false) }
    var showAlreadyDialog     by remember { mutableStateOf(false) }
    var alreadyRegisteredName by remember { mutableStateOf("") }
    var sensorName            by remember { mutableStateOf("") }
    var sensorLocation        by remember { mutableStateOf("") }
    var isRegistering         by remember { mutableStateOf(false) }
    var registerError         by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agregar sensor BLE") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopScan()
                        // ✅ NO desconectamos al volver — la conexión sigue activa
                        onBack()
                    }) {
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
                .padding(16.dp)
        ) {

            if (!permissionsState.allPermissionsGranted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Se necesitan permisos de Bluetooth para escanear.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                            Text("Conceder permisos")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (isConnected) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.BluetoothConnected, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Conectado", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary)
                            deviceId?.let { Text("ID: $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (permissionsState.allPermissionsGranted) {
                            viewModel.startScan(); isScanning = true
                        } else {
                            permissionsState.launchMultiplePermissionRequest()
                        }
                    },
                    enabled = !isScanning, modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.BluetoothSearching, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Escanear")
                }
                OutlinedButton(
                    onClick = { viewModel.stopScan(); isScanning = false },
                    enabled = isScanning, modifier = Modifier.weight(1f)
                ) { Text("Detener") }
            }

            if (isScanning) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text("Buscando sensores...", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(20.dp))
            Text("Dispositivos encontrados:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (devices.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Presiona «Escanear» para buscar sensores.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(devices, key = { it.address }) { device ->
                        val macLower = device.address.lowercase()
                        val alreadyRegistered = registeredDeviceIds.contains(macLower)
                        val existingSensor = sensorState.sensors.find {
                            it.device_id.lowercase() == macLower
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (alreadyRegistered)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surface
                            ),
                            onClick = {
                                try {
                                    if (alreadyRegistered) {
                                        alreadyRegisteredName = existingSensor?.name ?: "Este sensor"
                                        showAlreadyDialog = true
                                    } else {
                                        selectedDevice = device
                                        sensorName = try { device.name ?: "" } catch (e: SecurityException) { "" }
                                        viewModel.stopScan()
                                        isScanning = false
                                        viewModel.connect(device)
                                        showRegisterDialog = true
                                    }
                                } catch (e: SecurityException) { e.printStackTrace() }
                            }
                        ) {
                            Row(modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (alreadyRegistered) Icons.Filled.CheckCircle
                                    else Icons.Filled.Bluetooth,
                                    contentDescription = null,
                                    tint = if (alreadyRegistered) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    val name = try { device.name } catch (e: SecurityException) { null }
                                    Text(name ?: "Dispositivo desconocido",
                                        style = MaterialTheme.typography.titleSmall)
                                    Text(device.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (alreadyRegistered) {
                                    Text("Registrado", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo: ya registrado
    if (showAlreadyDialog) {
        AlertDialog(
            onDismissRequest = { showAlreadyDialog = false },
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary) },
            title = { Text("Sensor ya vinculado") },
            text = { Text("\"$alreadyRegisteredName\" ya está registrado en tu cuenta.") },
            confirmButton = {
                Button(onClick = { showAlreadyDialog = false; onSensorRegistered() }) {
                    Text("Ver mis sensores")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlreadyDialog = false }) { Text("Cerrar") }
            }
        )
    }

    // Diálogo: registrar nuevo sensor
    if (showRegisterDialog) {
        AlertDialog(
            onDismissRequest = {
                showRegisterDialog = false
                // ✅ Solo desconectamos si el usuario cancela el registro
                viewModel.disconnect()
                selectedDevice = null
            },
            title = { Text("Registrar sensor") },
            text = {
                Column {
                    val displayId = deviceId ?: selectedDevice?.address ?: ""
                    Text("ID: $displayId", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (deviceId == null) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Leyendo ID del dispositivo...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = sensorName, onValueChange = { sensorName = it },
                        label = { Text("Nombre del sensor") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = sensorLocation, onValueChange = { sensorLocation = it },
                        label = { Text("Ubicación (opcional)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    registerError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = deviceId ?: selectedDevice?.address ?: return@Button
                        isRegistering = true
                        registerError = null
                        sensorViewModel.registerSensor(
                            deviceId = id.lowercase(),
                            name = sensorName.trim().ifBlank { "Mi sensor" },
                            location = sensorLocation.trim().ifBlank { null },
                            onSuccess = {
                                isRegistering = false
                                showRegisterDialog = false
                                onSensorRegistered()
                            },
                            onError = { msg ->
                                isRegistering = false
                                registerError = msg
                            }
                        )
                    },
                    enabled = !isRegistering
                ) { Text(if (isRegistering) "Guardando..." else "Registrar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRegisterDialog = false
                    viewModel.disconnect()
                    selectedDevice = null
                }) { Text("Cancelar") }
            }
        )
    }
}