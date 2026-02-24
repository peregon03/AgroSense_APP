package com.example.agrosense.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import com.example.agrosense.data.ble.BleManager
import com.example.agrosense.data.ble.SensorReading

class BleViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = BleManager(app.applicationContext)

    val devices     = manager.devices
    val deviceId    = manager.deviceId
    val reading     = manager.reading
    val isConnected = manager.isConnected

    fun startScan() = manager.startScan()
    fun stopScan()  = manager.stopScan()

    fun connect(device: BluetoothDevice) = manager.connect(device)
    fun disconnect() = manager.disconnect()

    override fun onCleared() {
        super.onCleared()
        manager.disconnect()
    }
}