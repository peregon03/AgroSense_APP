package com.example.agrosense.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.annotation.SuppressLint
import com.example.agrosense.data.ble.SensorReading
import com.example.agrosense.data.local.entity.LocalSensorEntity
import com.example.agrosense.ui.viewmodel.BleViewModel
import com.example.agrosense.ui.viewmodel.LocalViewModel

// Helper para acceder a device.address sin que el IDE ponga error inline
@SuppressLint("MissingPermission")
private fun getDeviceAddress(device: android.bluetooth.BluetoothDevice): String = device.address

// ── Paleta ────────────────────────────────────────────────────────────────────
private val LsPrimary        = Color(0xFF0D631B)
private val LsPrimaryDark    = Color(0xFF2E7D32)
private val LsSurface        = Color(0xFFFCF9F8)
private val LsSurfaceCont    = Color(0xFFF0EDED)
private val LsSurfaceContLow = Color(0xFFF6F3F2)
private val LsOnSurface      = Color(0xFF1B1C1C)
private val LsOnSurfaceVar   = Color(0xFF40493D)
private val LsConnected      = Color(0xFF1B5E20)
private val LsConnectedBg    = Color(0xFFE8F5E9)
private val LsError          = Color(0xFFBA1A1A)

// ── Paso del modal de agregar sensor ──────────────────────────────────────────
private enum class AddStep { SCANNING, CONNECTING, NAMING, DONE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSensorsListScreen(
    localVm: LocalViewModel,
    bleVm: BleViewModel,
    onViewCharts: (deviceId: String) -> Unit,
    onConfigureWifi: (LocalSensorEntity) -> Unit,
    onLogout: () -> Unit
) {
    val state       by localVm.state.collectAsState()
    val bleDevices  by bleVm.devices.collectAsState()
    val bleConnected by bleVm.isConnected.collectAsState()
    val bleConnecting by bleVm.isConnecting.collectAsState()
    val bleDeviceId by bleVm.deviceId.collectAsState()
    val bleReading  by bleVm.reading.collectAsState()
    val pumpState   by bleVm.pumpState.collectAsState()

    // ID del sensor actualmente conectado via BLE (en esta sesión)
    var connectedDeviceId by remember { mutableStateOf<String?>(null) }

    // Modal de agregar sensor
    var showAddModal by remember { mutableStateOf(false) }
    var addStep      by remember { mutableStateOf(AddStep.SCANNING) }
    var newSensorName by remember { mutableStateOf("") }
    var newSensorLocation by remember { mutableStateOf("") }
    var selectedMac  by remember { mutableStateOf("") }

    // Menú de opciones por sensor
    var menuOpenFor  by remember { mutableStateOf<String?>(null) }
    var deleteConfirmFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { localVm.loadSensors() }

    // Cuando BLE se conecta y tenemos el device ID → actualizar connectedDeviceId
    LaunchedEffect(bleDeviceId, bleConnected) {
        if (bleConnected && bleDeviceId != null) {
            connectedDeviceId = bleDeviceId
        } else if (!bleConnected) {
            if (addStep == AddStep.CONNECTING) addStep = AddStep.SCANNING
        }
    }

    // En el paso CONNECTING, esperar a que llegue el deviceId
    LaunchedEffect(bleDeviceId, addStep) {
        if (addStep == AddStep.CONNECTING && bleDeviceId != null) {
            selectedMac = bleDeviceId!!
            addStep = AddStep.NAMING
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mis sensores", fontWeight = FontWeight.Bold, color = LsOnSurface)
                        Spacer(Modifier.width(8.dp))
                        LocalModeChip()
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LsSurface),
                actions = {
                    IconButton(onClick = {
                        if (bleConnected) bleVm.disconnect()
                        onLogout()
                    }) {
                        Icon(Icons.Filled.Logout, "Cerrar sesión", tint = LsOnSurfaceVar)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    addStep = AddStep.SCANNING
                    newSensorName = ""
                    newSensorLocation = ""
                    selectedMac = ""
                    bleVm.startScan()
                    showAddModal = true
                },
                containerColor = LsPrimary
            ) {
                Icon(Icons.Filled.Add, "Agregar sensor", tint = Color.White)
            }
        },
        containerColor = LsSurface
    ) { padding ->

        if (state.sensors.isEmpty()) {
            // Estado vacío
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(
                        Icons.Outlined.Sensors,
                        contentDescription = null,
                        tint = LsOnSurfaceVar.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No hay sensores registrados",
                        fontWeight = FontWeight.SemiBold,
                        color = LsOnSurfaceVar,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Toca el botón + para agregar un nodo via Bluetooth",
                        color = LsOnSurfaceVar.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.sensors, key = { it.deviceId }) { sensor ->
                    val isThisConnected = bleConnected && connectedDeviceId == sensor.deviceId

                    SensorLocalCard(
                        sensor         = sensor,
                        isConnected    = isThisConnected,
                        isConnecting   = bleConnecting && connectedDeviceId == null,
                        reading        = if (isThisConnected) bleReading else null,
                        pumpOn         = pumpState,
                        menuOpen       = menuOpenFor == sensor.deviceId,
                        onConnect      = {
                            if (!bleConnected) bleVm.connectByAddress(sensor.deviceId)
                            else if (isThisConnected) bleVm.disconnect()
                        },
                        onPumpToggle   = { bleVm.controlPump(!pumpState) },
                        onViewCharts   = { onViewCharts(sensor.deviceId) },
                        onConfigWifi   = { onConfigureWifi(sensor) },
                        onMenuToggle   = {
                            menuOpenFor = if (menuOpenFor == sensor.deviceId) null else sensor.deviceId
                        },
                        onDelete       = { deleteConfirmFor = sensor.deviceId; menuOpenFor = null }
                    )
                }
            }
        }
    }

    // ── Modal: agregar sensor ─────────────────────────────────────────────────
    if (showAddModal) {
        ModalBottomSheet(
            onDismissRequest = {
                bleVm.stopScan()
                showAddModal = false
            },
            containerColor = LsSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                when (addStep) {
                    AddStep.SCANNING -> {
                        Text("Agregar sensor", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LsOnSurface)
                        Spacer(Modifier.height(4.dp))
                        Text("Dispositivos AgroSense cercanos:", style = MaterialTheme.typography.bodySmall, color = LsOnSurfaceVar)
                        Spacer(Modifier.height(16.dp))

                        if (bleDevices.isEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = LsPrimary)
                                Spacer(Modifier.width(12.dp))
                                Text("Buscando...", color = LsOnSurfaceVar)
                            }
                        } else {
                            bleDevices.forEach { device ->
                                @SuppressLint("MissingPermission")
                                val mac = getDeviceAddress(device)
                                val alreadySaved = state.sensors.any { it.deviceId.equals(mac, true) }
                                OutlinedCard(
                                    onClick = {
                                        if (!alreadySaved) {
                                            bleVm.stopScan()
                                            addStep = AddStep.CONNECTING
                                            bleVm.connect(device)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    enabled = !alreadySaved
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.BluetoothConnected, null, tint = LsPrimary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(mac.uppercase(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            if (alreadySaved) Text("Ya registrado", fontSize = 11.sp, color = LsOnSurfaceVar)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AddStep.CONNECTING -> {
                        Text("Conectando...", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LsOnSurface)
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = LsPrimary)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Leyendo ID del dispositivo...", color = LsOnSurfaceVar, modifier = Modifier.fillMaxWidth())
                    }

                    AddStep.NAMING -> {
                        Text("Nombrar sensor", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LsOnSurface)
                        Spacer(Modifier.height(4.dp))
                        Text("ID: ${selectedMac.uppercase()}", style = MaterialTheme.typography.labelSmall, color = LsOnSurfaceVar)
                        Spacer(Modifier.height(20.dp))

                        OutlinedTextField(
                            value = newSensorName,
                            onValueChange = { newSensorName = it },
                            label = { Text("Nombre del sensor *") },
                            placeholder = { Text("ej. Sector Norte") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Next
                            )
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newSensorLocation,
                            onValueChange = { newSensorLocation = it },
                            label = { Text("Ubicación (opcional)") },
                            placeholder = { Text("ej. Campo A, Bloque 2") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done
                            )
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                localVm.saveSensor(
                                    deviceId = selectedMac,
                                    name     = newSensorName.trim(),
                                    location = newSensorLocation.trim().ifEmpty { null },
                                    apiKey   = null
                                )
                                addStep = AddStep.DONE
                                showAddModal = false
                            },
                            enabled = newSensorName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LsPrimary)
                        ) {
                            Text("Guardar sensor", fontWeight = FontWeight.Bold)
                        }
                    }

                    AddStep.DONE -> {}
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // ── Confirmación de borrado ───────────────────────────────────────────────
    deleteConfirmFor?.let { deviceId ->
        AlertDialog(
            onDismissRequest = { deleteConfirmFor = null },
            icon = { Icon(Icons.Filled.Delete, null, tint = LsError) },
            title = { Text("Eliminar sensor") },
            text = { Text("¿Eliminar este sensor y su historial local?") },
            confirmButton = {
                TextButton(onClick = {
                    if (bleConnected && connectedDeviceId == deviceId) {
                        bleVm.disconnect()
                        connectedDeviceId = null
                    }
                    localVm.deleteSensor(deviceId)
                    deleteConfirmFor = null
                }) { Text("Eliminar", color = LsError, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmFor = null }) { Text("Cancelar") }
            }
        )
    }
}

// ── Tarjeta de sensor ─────────────────────────────────────────────────────────

@Composable
private fun SensorLocalCard(
    sensor: LocalSensorEntity,
    isConnected: Boolean,
    isConnecting: Boolean,
    reading: SensorReading?,
    pumpOn: Boolean,
    menuOpen: Boolean,
    onConnect: () -> Unit,
    onPumpToggle: () -> Unit,
    onViewCharts: () -> Unit,
    onConfigWifi: () -> Unit,
    onMenuToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Encabezado ────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(sensor.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = LsOnSurface)
                    if (sensor.location != null) {
                        Text(sensor.location, fontSize = 12.sp, color = LsOnSurfaceVar,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        sensor.deviceId.uppercase(),
                        fontSize = 11.sp,
                        color = LsOnSurfaceVar.copy(alpha = 0.5f)
                    )
                }
                // Estado BLE
                BleStatusChip(isConnected, isConnecting)
                Spacer(Modifier.width(4.dp))
                Box {
                    IconButton(onClick = onMenuToggle) {
                        Icon(Icons.Filled.MoreVert, "Opciones", tint = LsOnSurfaceVar)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuToggle) {
                        DropdownMenuItem(
                            text = { Text("Ver gráficas") },
                            leadingIcon = { Icon(Icons.Outlined.BarChart, null) },
                            onClick = { onMenuToggle(); onViewCharts() }
                        )
                        DropdownMenuItem(
                            text = { Text("Configurar WiFi") },
                            leadingIcon = { Icon(Icons.Outlined.Wifi, null) },
                            onClick = { onMenuToggle(); onConfigWifi() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Eliminar", color = LsError) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = LsError) },
                            onClick = onDelete
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Lecturas en vivo (cuando conectado) ───────────────────────
            if (isConnected && reading != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    reading.temperature?.let {
                        ReadingChip("🌡 %.1f°C".format(it), Color(0xFFFFEBEE))
                    }
                    reading.airHumidity?.let {
                        ReadingChip("💧 %.1f%%".format(it), Color(0xFFE3F2FD))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    reading.co2?.let {
                        ReadingChip("🌫 %.0f ppm".format(it), Color(0xFFEDE7F6))
                    }
                    reading.methane?.let {
                        ReadingChip("🔥 %.0f ppm".format(it), Color(0xFFFFF8E1))
                    }
                }
                val hasSoil = reading.soilTemp != null || reading.soilHum != null ||
                              reading.ec != null || reading.ph != null
                if (hasSoil) {
                    Spacer(Modifier.height(6.dp))
                    Text("Suelo", fontSize = 11.sp, color = LsOnSurfaceVar, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        reading.soilTemp?.let {
                            ReadingChip("🌱 %.1f°C".format(it), Color(0xFFE8F5E9))
                        }
                        reading.soilHum?.let {
                            ReadingChip("💧 %.1f%%".format(it), Color(0xFFE0F2F1))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        reading.ec?.let {
                            ReadingChip("⚡ %.0f µS/cm".format(it), Color(0xFFFFF9C4))
                        }
                        reading.ph?.let {
                            ReadingChip("⚗ pH %.2f".format(it), Color(0xFFE8EAF6))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        reading.nitrogen?.let {
                            ReadingChip("🟢 N %.0f".format(it), Color(0xFFE8F5E9))
                        }
                        reading.phosphorus?.let {
                            ReadingChip("🟠 P %.0f".format(it), Color(0xFFFBE9E7))
                        }
                        reading.potassium?.let {
                            ReadingChip("🔵 K %.0f".format(it), Color(0xFFE1F5FE))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Control bomba
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (pumpOn) LsConnectedBg else LsSurfaceCont)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.WaterDrop,
                            null,
                            tint = if (pumpOn) LsConnected else LsOnSurfaceVar,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Motobomba",
                            fontWeight = FontWeight.Medium,
                            color = if (pumpOn) LsConnected else LsOnSurface
                        )
                    }
                    Switch(
                        checked = pumpOn,
                        onCheckedChange = { onPumpToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor      = Color.White,
                            checkedTrackColor      = LsPrimary,
                            uncheckedThumbColor    = Color.White,
                            uncheckedTrackColor    = LsOnSurfaceVar.copy(alpha = 0.3f)
                        )
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Botón conectar/desconectar ────────────────────────────────
            OutlinedButton(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isConnected) LsError else LsPrimary
                )
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = LsPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Conectando...")
                } else if (isConnected) {
                    Icon(Icons.Outlined.BluetoothDisabled, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Desconectar")
                } else {
                    Icon(Icons.Outlined.BluetoothConnected, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Conectar via BLE")
                }
            }
        }
    }
}

// ── Chips auxiliares ──────────────────────────────────────────────────────────

@Composable
private fun BleStatusChip(isConnected: Boolean, isConnecting: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isConnected) LsConnectedBg else LsSurfaceCont)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isConnecting) {
            CircularProgressIndicator(modifier = Modifier.size(8.dp), strokeWidth = 1.5.dp, color = LsPrimary)
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) LsConnected else LsOnSurfaceVar.copy(alpha = 0.4f))
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            if (isConnecting) "Conectando" else if (isConnected) "BLE" else "Sin conexión",
            fontSize = 10.sp,
            color = if (isConnected) LsConnected else LsOnSurfaceVar,
            fontWeight = if (isConnected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ReadingChip(text: String, bgColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = LsOnSurface)
    }
}

@Composable
private fun LocalModeChip() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFE8F5E9))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.WifiOff, null, tint = LsPrimary, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text("Local", fontSize = 11.sp, color = LsPrimary, fontWeight = FontWeight.SemiBold)
    }
}
