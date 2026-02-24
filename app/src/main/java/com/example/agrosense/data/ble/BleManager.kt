package com.example.agrosense.data.ble

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

class BleManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val scanner get() = bluetoothAdapter?.bluetoothLeScanner

    private val SERVICE_UUID =
        UUID.fromString("0000A001-0000-1000-8000-00805F9B34FB")

    private val DEVICE_ID_UUID =
        UUID.fromString("0000A002-0000-1000-8000-00805F9B34FB")

    private val READINGS_UUID =
        UUID.fromString("0000A003-0000-1000-8000-00805F9B34FB")

    private var bluetoothGatt: BluetoothGatt? = null

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices

    private val _deviceId = MutableStateFlow<String?>(null)
    val deviceId: StateFlow<String?> = _deviceId

    private val _reading = MutableStateFlow<String?>(null)
    val reading: StateFlow<String?> = _reading

    fun startScan() {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(listOf(filter), settings, scanCallback)
    }

    fun stopScan() {
        scanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val current = _devices.value.toMutableList()
            if (!current.contains(result.device)) {
                current.add(result.device)
                _devices.value = current
            }
        }
    }

    fun connect(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)

            val deviceIdChar = service.getCharacteristic(DEVICE_ID_UUID)
            gatt.readCharacteristic(deviceIdChar)

            val readingChar = service.getCharacteristic(READINGS_UUID)
            gatt.setCharacteristicNotification(readingChar, true)

            val descriptor = readingChar.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
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
                _reading.value = characteristic.value.toString(Charsets.UTF_8)
            }
        }
    }
}
