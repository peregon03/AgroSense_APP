package com.example.agrosense.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agrosense.data.api.ApiClient
import com.example.agrosense.data.api.RegisterSensorRequest
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.data.model.SensorReading
import com.example.agrosense.data.model.ThresholdsRequest
import com.example.agrosense.data.storage.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Rangos de fecha disponibles
enum class DateRange(val label: String, val apiValue: String) {
    TODAY("Hoy", "today"),
    WEEK("Última semana", "week"),
    MONTH("Último mes", "month"),
    QUARTER("Últimos 3 meses", "quarter")
}

data class SensorUiState(
    val sensors: List<Sensor> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    // Lecturas / gráficas
    val readings: List<SensorReading> = emptyList(),
    val isLoadingReadings: Boolean = false,
    val readingsError: String? = null,
    val selectedRange: DateRange = DateRange.TODAY,
    val readingsCount: Int = 0
)

class SensorViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app.applicationContext)
    private val api = ApiClient.sensorApi

    private val _state = MutableStateFlow(SensorUiState())
    val state: StateFlow<SensorUiState> = _state

    // ── Cargar lista de sensores ───────────────────────────────────────────

    fun loadSensors() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val token = session.getToken() ?: return@launch
                val response = api.getSensors("Bearer $token")
                if (response.isSuccessful) {
                    _state.value = _state.value.copy(
                        sensors = response.body()?.sensors ?: emptyList(),
                        isLoading = false
                    )
                } else {
                    _state.value = _state.value.copy(
                        error = "Error al cargar sensores",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Error de conexión",
                    isLoading = false
                )
            }
        }
    }

    // ── Registrar sensor ───────────────────────────────────────────────────
    // Devuelve la api_key para que BleScreen la envíe al ESP32

    fun registerSensor(
        deviceId: String,
        name: String,
        location: String?,
        onSuccess: (apiKey: String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val token = session.getToken()
                    ?: run { onError("No hay sesión activa"); return@launch }
                val response = api.registerSensor(
                    "Bearer $token",
                    RegisterSensorRequest(deviceId, name, location)
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) onSuccess(body.sensor.api_key)
                    else onError("Respuesta vacía del servidor")
                } else {
                    onError("Error al registrar: ${response.code()}")
                }
            } catch (e: Exception) {
                onError("Error de conexión: ${e.message}")
            }
        }
    }

    // ── Eliminar sensor ────────────────────────────────────────────────────

    fun deleteSensor(sensorId: Int, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val token = session.getToken() ?: return@launch
                val response = api.deleteSensor("Bearer $token", sensorId)
                if (response.isSuccessful) {
                    _state.value = _state.value.copy(
                        sensors = _state.value.sensors.filter { it.id != sensorId }
                    )
                    onSuccess()
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Error al eliminar sensor")
            }
        }
    }

    // ── Guardar umbrales de alerta ─────────────────────────────────────────

    fun saveThresholds(
        sensorId: Int,
        request: ThresholdsRequest,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val token = session.getToken()
                    ?: run { onError("No hay sesión activa"); return@launch }
                val response = api.updateThresholds("Bearer $token", sensorId, request)
                if (response.isSuccessful) {
                    loadSensors() // Refrescar lista con nuevos umbrales
                    onSuccess()
                } else {
                    onError("Error al guardar: ${response.code()}")
                }
            } catch (e: Exception) {
                onError("Error de conexión: ${e.message}")
            }
        }
    }

    // ── Cambiar rango de fecha y recargar lecturas ─────────────────────────

    fun selectRange(sensorId: Int, range: DateRange) {
        _state.value = _state.value.copy(selectedRange = range)
        loadReadings(sensorId, range)
    }

    // ── Cargar lecturas por rango ──────────────────────────────────────────

    fun loadReadings(sensorId: Int, range: DateRange = _state.value.selectedRange) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoadingReadings = true,
                readingsError = null
            )
            try {
                val token = session.getToken() ?: return@launch
                val response = api.getReadings("Bearer $token", sensorId, range.apiValue)
                if (response.isSuccessful) {
                    val body = response.body()
                    _state.value = _state.value.copy(
                        readings = body?.readings ?: emptyList(),
                        readingsCount = body?.count ?: 0,
                        isLoadingReadings = false
                    )
                } else {
                    _state.value = _state.value.copy(
                        readingsError = "Error al cargar datos",
                        isLoadingReadings = false
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    readingsError = "Error de conexión",
                    isLoadingReadings = false
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
