package com.example.agrosense.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import com.example.agrosense.data.ble.BleManager

class BleViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = BleManager(app.applicationContext)

    // ── States existentes ────────────────────────────────────────────────────
    val devices      = manager.devices
    val deviceId     = manager.deviceId
    val reading      = manager.reading
    val isConnected  = manager.isConnected
    val isConnecting = manager.isConnecting

    // ── Nuevos states ────────────────────────────────────────────────────────
    val wifiStatus  = manager.wifiStatus   // "NOT_CONFIGURED"|"CONNECTING"|"CONNECTED"|"ERROR:..."
    val historyData = manager.historyData  // List<String> de líneas JSON recibidas
    val pumpState   = manager.pumpState    // true = bomba encendida

    // ── Funciones existentes ─────────────────────────────────────────────────
    fun startScan()                        = manager.startScan()
    fun stopScan()                         = manager.stopScan()
    fun connect(device: BluetoothDevice)   = manager.connect(device)
    fun connectByAddress(mac: String)      = manager.connectByAddress(mac)
    fun disconnect()                       = manager.disconnect()

    // ── Nuevas funciones ─────────────────────────────────────────────────────

    /**
     * Envía credenciales WiFi + API key al ESP32 vía BLE.
     * El ESP32 se conectará al WiFi y empezará a enviar datos a AWS.
     */
    fun sendWifiConfig(ssid: String, password: String, apiKey: String) =
        manager.sendWifiConfig(ssid, password, apiKey)

    /**
     * Enciende (true) o apaga (false) la bomba/LED del ESP32.
     */
    fun controlPump(on: Boolean) = manager.controlPump(on)

    /**
     * Solicita el histórico almacenado en el SPIFFS del ESP32.
     * Los datos llegarán en historyData StateFlow.
     */
    fun requestHistory() = manager.requestHistory()

    /**
     * Borra la configuración guardada en NVS del ESP32.
     * Útil para re-configurar un dispositivo que ya tenía credenciales.
     */
    fun resetDevice() = manager.resetDevice()

    override fun onCleared() {
        super.onCleared()
        manager.disconnect()
    }
}