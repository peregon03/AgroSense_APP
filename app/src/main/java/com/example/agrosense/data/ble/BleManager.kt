package com.example.agrosense.data.ble

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
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

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val scanner get() = bluetoothAdapter?.bluetoothLeScanner

    private val SERVICE_UUID   = UUID.fromString("0000A001-0000-1000-8000-00805F9B34FB")
    private val DEVICE_ID_UUID = UUID.fromString("0000A002-0000-1000-8000-00805F9B34FB")
    private val READINGS_UUID  = UUID.fromString("0000A003-0000-1000-8000-00805F9B34FB")

    private var bluetoothGatt: BluetoothGatt? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Mapa interno: address -> (device, lastSeen timestamp)
    private val deviceMap = mutableMapOf<String, Pair<BluetoothDevice, Long>>()

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices

    private val _deviceId = MutableStateFlow<String?>(null)
    val deviceId: StateFlow<String?> = _deviceId

    private val _reading = MutableStateFlow<SensorReading?>(null)
    val reading: StateFlow<SensorReading?> = _reading

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    // ── Scan ────────────────────────────────────────────────────────────────

    fun startScan() {
        deviceMap.clear()
        _devices.value = emptyList()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(listOf(filter), settings, scanCallback)

        // Cada 3 segundos limpiamos dispositivos que no se ven hace más de 8s
        scope.launch {
            while (true) {
                delay(3000)
                val now = System.currentTimeMillis()
                val removed = deviceMap.entries.removeAll { (_, v) -> now - v.second > 8000 }
                if (removed) _devices.value = deviceMap.values.map { it.first }
            }
        }
    }

    fun stopScan() {
        scanner?.stopScan(scanCallback)
        scope.coroutineContext.cancelChildren()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val now = System.currentTimeMillis()
            val isNew = !deviceMap.containsKey(device.address)
            deviceMap[device.address] = Pair(device, now)
            if (isNew) _devices.value = deviceMap.values.map { it.first }
        }
    }

    // ── Connect ─────────────────────────────────────────────────────────────

    fun connect(device: BluetoothDevice) {
        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _isConnected.value = false
        _deviceId.value = null
        _reading.value = null
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _isConnected.value = true
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _isConnected.value = false
                    _reading.value = null
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            val service = gatt.getService(SERVICE_UUID) ?: return

            // Leer device ID
            val deviceIdChar = service.getCharacteristic(DEVICE_ID_UUID)
            if (deviceIdChar != null) gatt.readCharacteristic(deviceIdChar)

            // Suscribir a notificaciones de lecturas
            val readingChar = service.getCharacteristic(READINGS_UUID)
            if (readingChar != null) {
                gatt.setCharacteristicNotification(readingChar, true)
                val descriptor = readingChar.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                )
                descriptor?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(it)
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == DEVICE_ID_UUID) {
                _deviceId.value = characteristic.value.toString(Charsets.UTF_8)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == READINGS_UUID) {
                val json = characteristic.value.toString(Charsets.UTF_8)
                _reading.value = parseReading(json)
            }
        }
    }

    // ── Parser JSON simple (sin librería extra) ─────────────────────────────

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