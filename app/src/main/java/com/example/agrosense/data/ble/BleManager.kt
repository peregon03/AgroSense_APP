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

    private val SERVICE_UUID   = UUID.fromString("0000A001-0000-1000-8000-00805F9B34FB")
    private val DEVICE_ID_UUID = UUID.fromString("0000A002-0000-1000-8000-00805F9B34FB")
    private val READINGS_UUID  = UUID.fromString("0000A003-0000-1000-8000-00805F9B34FB")
    private val CCCD_UUID      = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothGatt: BluetoothGatt? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val deviceMap = mutableMapOf<String, Pair<BluetoothDevice, Long>>()

    private val _devices     = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices

    private val _deviceId    = MutableStateFlow<String?>(null)
    val deviceId: StateFlow<String?> = _deviceId

    private val _reading     = MutableStateFlow<SensorReading?>(null)
    val reading: StateFlow<SensorReading?> = _reading

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting

    // ── Scan ────────────────────────────────────────────────────────────────

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

    // ── Connect ─────────────────────────────────────────────────────────────

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
            bluetoothGatt = null
            _isConnected.value = false
            _isConnecting.value = false
            _deviceId.value = null
            _reading.value = null
        }
    }

    // ── GATT Callbacks ──────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "✅ Conectado - descubriendo servicios...")
                    _isConnected.value = true
                    _isConnecting.value = false
                    try { gatt.discoverServices() } catch (e: SecurityException) { Log.e(TAG, "discoverServices error", e) }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "❌ Desconectado")
                    _isConnected.value = false
                    _isConnecting.value = false
                    _reading.value = null
                    try { gatt.close() } catch (e: SecurityException) { Log.e(TAG, "gatt.close error", e) }
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
                } else {
                    Log.e(TAG, "Característica DEVICE_ID no encontrada")
                }

                // 2. Suscribir notificaciones — delay para esperar readCharacteristic
                val readingChar = service.getCharacteristic(READINGS_UUID)
                if (readingChar != null) {
                    Log.d(TAG, "Suscribiendo a notificaciones de lecturas...")
                    gatt.setCharacteristicNotification(readingChar, true)

                    val descriptor = readingChar.getDescriptor(CCCD_UUID)
                    if (descriptor != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // API 33+ — nuevo método
                            val result = gatt.writeDescriptor(
                                descriptor,
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            )
                            Log.d(TAG, "writeDescriptor (API33+) resultado: $result")
                        } else {
                            // API < 33
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            val result = gatt.writeDescriptor(descriptor)
                            Log.d(TAG, "writeDescriptor (legacy) resultado: $result")
                        }
                    } else {
                        Log.e(TAG, "Descriptor CCCD no encontrado en READINGS_UUID")
                    }
                } else {
                    Log.e(TAG, "Característica READINGS_UUID no encontrada")
                }
            } catch (e: SecurityException) { Log.e(TAG, "onServicesDiscovered error", e) }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.d(TAG, "onDescriptorWrite: status=$status uuid=${descriptor.uuid}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "✅ Notificaciones activadas correctamente")
            } else {
                Log.e(TAG, "❌ Error activando notificaciones: $status")
            }
        }

        // API < 33
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

        // API 33+
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

        // API < 33
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == READINGS_UUID) {
                val json = characteristic.value.toString(Charsets.UTF_8)
                Log.d(TAG, "📥 Datos recibidos (legacy): $json")
                _reading.value = parseReading(json)
            }
        }

        // API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == READINGS_UUID) {
                val json = value.toString(Charsets.UTF_8)
                Log.d(TAG, "📥 Datos recibidos (API33+): $json")
                _reading.value = parseReading(json)
            }
        }
    }

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