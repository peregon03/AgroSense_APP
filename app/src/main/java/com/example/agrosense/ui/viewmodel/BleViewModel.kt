package com.example.agrosense.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import com.example.agrosense.data.ble.BleManager

class BleViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = BleManager(app.applicationContext)

    val devices = manager.devices
    val deviceId = manager.deviceId
    val reading = manager.reading

    fun startScan() = manager.startScan()
    fun stopScan() = manager.stopScan()
    fun connect(device: BluetoothDevice) = manager.connect(device)
}
