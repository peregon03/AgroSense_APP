package com.example.agrosense.data.ble

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

data class SensorReading(
    val soilHumidity: Float? = null,
    val temperature: Float? = null,
    val airHumidity: Float? = null,
    val rawJson: String? = null
)

class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "AgroSense_BLE"
    }

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val scanner get() = bluetoothAdapter?.bluetoothLeScanner

    // ── UUIDs ────────────────────────────────────────────────────────────────
    private val SERVICE_UUID        = UUID.fromString("0000A001-0000-1000-8000-00805F9B34FB")
    private val DEVICE_ID_UUID      = UUID.fromString("0000A002-0000-1000-8000-00805F9B34FB")
    private val READINGS_UUID       = UUID.fromString("0000A003-0000-1000-8000-00805F9B34FB")
    private val PUMP_UUID           = UUID.fromString("0000A004-0000-1000-8000-00805F9B34FB")
    private val HISTORY_REQ_UUID    = UUID.fromString("0000A005-0000-1000-8000-00805F9B34FB")
    private val HISTORY_DATA_UUID   = UUID.fromString("0000A006-0000-1000-8000-00805F9B34FB")
    private val WIFI_CONFIG_UUID    = UUID.fromString("0000A007-0000-1000-8000-00805F9B34FB")
    private val WIFI_STATUS_UUID    = UUID.fromString("0000A008-0000-1000-8000-00805F9B34FB")
    private val CCCD_UUID           = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothGatt: BluetoothGatt? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val deviceMap = mutableMapOf<String, Pair<BluetoothDevice, Long>>()

    // ── StateFlows públicos ──────────────────────────────────────────────────
    private val _devices      = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices

    private val _deviceId     = MutableStateFlow<String?>(null)
    val deviceId: StateFlow<String?> = _deviceId

    private val _reading      = MutableStateFlow<SensorReading?>(null)
    val reading: StateFlow<SensorReading?> = _reading

    private val _isConnected  = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting

    // "NOT_CONFIGURED" | "CONNECTING" | "CONNECTED" | "DISCONNECTED" | "ERROR:..."
    private val _wifiStatus   = MutableStateFlow("NOT_CONFIGURED")
    val wifiStatus: StateFlow<String> = _wifiStatus

    // Líneas del histórico recibidas por BLE
    private val _historyData  = MutableStateFlow<List<String>>(emptyList())
    val historyData: StateFlow<List<String>> = _historyData

    // Estado bomba (true = encendida)
    private val _pumpState    = MutableStateFlow(false)
    val pumpState: StateFlow<Boolean> = _pumpState

    // ── Scan ─────────────────────────────────────────────────────────────────

    fun startScan() {
        try {
            deviceMap.clear()
            _devices.value = emptyList()

            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner?.startScan(listOf(filter), settings, scanCallback)

            scope.launch {
                while (true) {
                    delay(3000)
                    val now = System.currentTimeMillis()
                    val removed = deviceMap.entries.removeAll { (_, v) -> now - v.second > 8000 }
                    if (removed) _devices.value = deviceMap.values.map { it.first }
                }
            }
        } catch (e: SecurityException) { Log.e(TAG, "startScan error", e) }
    }

    fun stopScan() {
        try {
            scanner?.stopScan(scanCallback)
            scope.coroutineContext.cancelChildren()
        } catch (e: SecurityException) { Log.e(TAG, "stopScan error", e) }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                val device = result.device
                val now = System.currentTimeMillis()
                val isNew = !deviceMap.containsKey(device.address)
                deviceMap[device.address] = Pair(device, now)
                if (isNew) _devices.value = deviceMap.values.map { it.first }
            } catch (e: SecurityException) { Log.e(TAG, "scanCallback error", e) }
        }
    }

    // ── Connect ──────────────────────────────────────────────────────────────

    fun connect(device: BluetoothDevice) {
        try {
            _isConnecting.value = true
            bluetoothGatt?.close()
            Log.d(TAG, "Conectando a ${device.address}")
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            _isConnecting.value = false
            Log.e(TAG, "connect error", e)
        }
    }

    fun connectByAddress(macAddress: String) {
        try {
            if (_isConnected.value) return
            _isConnecting.value = true
            bluetoothGatt?.close()
            val mac = macAddress.uppercase()
            Log.d(TAG, "Conectando por MAC: $mac")
            val device = bluetoothAdapter?.getRemoteDevice(mac) ?: run {
                _isConnecting.value = false
                return
            }
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            _isConnecting.value = false
            Log.e(TAG, "connectByAddress error", e)
        } catch (e: IllegalArgumentException) {
            _isConnecting.value = false
            Log.e(TAG, "MAC inválida: $macAddress", e)
        }
    }

    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: SecurityException) { Log.e(TAG, "disconnect error", e) }
        finally {
            bluetoothGatt    = null
            _isConnected.value  = false
            _isConnecting.value = false
            _deviceId.value     = null
            _reading.value      = null
            _wifiStatus.value   = "NOT_CONFIGURED"
            _pumpState.value    = false
        }
    }

    // ── Nuevas funciones BLE ─────────────────────────────────────────────────

    /**
     * Envía configuración WiFi al ESP32.
     * Formato: "ssid|password|apikey"
     */
    fun sendWifiConfig(ssid: String, password: String, apiKey: String) {
        val payload = "$ssid|$password|$apiKey"
        writeCharacteristic(WIFI_CONFIG_UUID, payload)
        Log.d(TAG, "sendWifiConfig: ssid=$ssid")
    }

    /**
     * Enciende o apaga la bomba/LED.
     * Envía "1" o "0" a PUMP_UUID.
     */
    fun controlPump(on: Boolean) {
        writeCharacteristic(PUMP_UUID, if (on) "1" else "0")
        Log.d(TAG, "controlPump: ${if (on) "ON" else "OFF"}")
    }

    /**
     * Solicita el histórico guardado en SPIFFS.
     * Envía "GET" a HISTORY_REQ_UUID.
     */
    fun requestHistory() {
        _historyData.value = emptyList()
        writeCharacteristic(HISTORY_REQ_UUID, "GET")
        Log.d(TAG, "requestHistory: GET enviado")
    }

    /**
     * Borra la configuración guardada en NVS del ESP32 (ssid, password, api_key).
     * El ESP32 responderá con "RESET_OK" en el WiFiStatus characteristic.
     */
    fun resetDevice() {
        writeCharacteristic(WIFI_CONFIG_UUID, "RESET")
        Log.d(TAG, "resetDevice: RESET enviado")
    }

    // ── Helper: escribir en una característica por UUID ──────────────────────

    private fun writeCharacteristic(uuid: UUID, value: String) {
        try {
            val gatt = bluetoothGatt ?: run {
                Log.e(TAG, "writeCharacteristic: gatt es null")
                return
            }
            val service = gatt.getService(SERVICE_UUID) ?: run {
                Log.e(TAG, "writeCharacteristic: servicio no encontrado")
                return
            }
            val char = service.getCharacteristic(uuid) ?: run {
                Log.e(TAG, "writeCharacteristic: característica $uuid no encontrada")
                return
            }

            val bytes = value.toByteArray(Charsets.UTF_8)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                char.value = bytes
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "writeCharacteristic error ($uuid)", e)
        }
    }

    // ── Helper: suscribir notificaciones ────────────────────────────────────

    private fun enableNotifications(gatt: BluetoothGatt, uuid: UUID) {
        try {
            val service = gatt.getService(SERVICE_UUID) ?: return
            val char    = service.getCharacteristic(uuid) ?: return
            gatt.setCharacteristicNotification(char, true)
            val descriptor = char.getDescriptor(CCCD_UUID) ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
            Log.d(TAG, "enableNotifications: $uuid")
        } catch (e: SecurityException) {
            Log.e(TAG, "enableNotifications error ($uuid)", e)
        }
    }

    // ── Cola secuencial de suscripciones ────────────────────────────────────
    // El stack BLE solo procesa una operación GATT a la vez.
    // Activamos el siguiente descriptor SOLO tras confirmar el anterior
    // en onDescriptorWrite, evitando rechazos silenciosos de Android.

    private val notifyQueue = ArrayDeque<UUID>()
    private var pendingGatt: BluetoothGatt? = null

    private fun enqueueNotifications(gatt: BluetoothGatt) {
        pendingGatt = gatt
        notifyQueue.clear()
        // READINGS primero — es el más crítico para ver datos en vivo
        notifyQueue.addAll(listOf(
            READINGS_UUID,
            WIFI_STATUS_UUID,
            HISTORY_DATA_UUID,
            PUMP_UUID
        ))
        processNextNotification()
    }

    private fun processNextNotification() {
        val gatt = pendingGatt ?: return

        val uuid = if (notifyQueue.isNotEmpty()) notifyQueue.removeFirst() else null

        if (uuid == null) {
            Log.d(TAG, "✅ Cola de notificaciones completada")
            return
        }

        enableNotifications(gatt, uuid)
    }

    // Buffer para acumular líneas del histórico entre START y END
    private val historyBuffer = mutableListOf<String>()
    // Buffer para reconstruir JSON de lecturas BLE
    private var jsonBuffer = ""

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "✅ Conectado - descubriendo servicios...")
                    _isConnected.value  = true
                    _isConnecting.value = false
                    try { gatt.discoverServices() } catch (e: SecurityException) {
                        Log.e(TAG, "discoverServices error", e)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "❌ Desconectado")
                    _isConnected.value  = false
                    _isConnecting.value = false
                    _reading.value      = null
                    notifyQueue.clear()
                    pendingGatt = null
                    try { gatt.close() } catch (e: SecurityException) {
                        Log.e(TAG, "gatt.close error", e)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered: status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Servicios no descubiertos correctamente")
                return
            }

            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Servicio AgroSense no encontrado")
                return
            }
            Log.d(TAG, "✅ Servicio AgroSense encontrado")

            try {
                // 1. Leer device ID
                val deviceIdChar = service.getCharacteristic(DEVICE_ID_UUID)
                if (deviceIdChar != null) {
                    Log.d(TAG, "Leyendo device ID...")
                    gatt.readCharacteristic(deviceIdChar)
                }
                // 2. Iniciar cola secuencial tras readCharacteristic
                scope.launch {
                    delay(400)
                    enqueueNotifications(gatt)
                }
            } catch (e: SecurityException) { Log.e(TAG, "onServicesDiscovered error", e) }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val charUuid = descriptor.characteristic.uuid
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "✅ Notificaciones activadas: $charUuid")
            } else {
                Log.e(TAG, "❌ Error activando notificaciones ($charUuid): status=$status")
            }
            // Avanzar al siguiente UUID de la cola, independientemente del resultado
            processNextNotification()
        }

        // ── onCharacteristicRead ─────────────────────────────────────────────

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == DEVICE_ID_UUID) {
                val id = characteristic.value.toString(Charsets.UTF_8).lowercase()
                Log.d(TAG, "Device ID leído (legacy): $id")
                _deviceId.value = id
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (characteristic.uuid == DEVICE_ID_UUID) {
                val id = value.toString(Charsets.UTF_8).lowercase()
                Log.d(TAG, "Device ID leído (API33+): $id")
                _deviceId.value = id
            }
        }

        // ── onCharacteristicChanged ──────────────────────────────────────────

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleNotification(characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(characteristic.uuid, value)
        }
    }

    // ── Despachar notificaciones entrantes ───────────────────────────────────

    private fun handleNotification(uuid: UUID, value: ByteArray) {
        val text = value.toString(Charsets.UTF_8)
        Log.d(TAG, "📥 Notificación [$uuid]: $text")

        when (uuid) {
            READINGS_UUID -> {
                jsonBuffer += text

                // Si detectamos cierre de JSON
                if (text.contains("}")) {
                    val completeJson = jsonBuffer
                    jsonBuffer = ""

                    Log.d(TAG, "✅ JSON completo recibido: $completeJson")

                    _reading.value = parseReading(completeJson)
                }
            }
            WIFI_STATUS_UUID -> {
                _wifiStatus.value = text
                Log.d(TAG, "WiFi status: $text")
            }
            PUMP_UUID -> {
                _pumpState.value = text.trim() == "1"
                Log.d(TAG, "Pump state: ${_pumpState.value}")
            }
            HISTORY_DATA_UUID -> {
                when {
                    text == "START" -> {
                        historyBuffer.clear()
                        Log.d(TAG, "Histórico: inicio recepción")
                    }
                    text.startsWith("END:") -> {
                        _historyData.value = historyBuffer.toList()
                        Log.d(TAG, "Histórico: ${historyBuffer.size} registros recibidos")
                    }
                    text == "EMPTY" -> {
                        _historyData.value = emptyList()
                        Log.d(TAG, "Histórico: vacío")
                    }
                    text == "ERROR" -> {
                        Log.e(TAG, "Histórico: error en ESP32")
                    }
                    else -> {
                        historyBuffer.add(text)
                    }
                }
            }
        }
    }

    // ── Parsear JSON de lecturas ─────────────────────────────────────────────

    private fun parseReading(json: String): SensorReading {
        return try {
            fun extractFloat(key: String): Float? {
                val regex = Regex("\"$key\"\\s*:\\s*([\\d.]+)")
                return regex.find(json)?.groupValues?.get(1)?.toFloatOrNull()
            }
            SensorReading(
                soilHumidity = extractFloat("soil_humidity"),
                temperature  = extractFloat("temperature"),
                airHumidity  = extractFloat("air_humidity"),
                rawJson      = json
            )
        } catch (e: Exception) {
            SensorReading(rawJson = json)
        }
    }
}