package com.example.agrosense.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agrosense.data.api.ApiClient
import com.example.agrosense.data.model.CreateSensorRequest
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.data.model.SensorReading
import com.example.agrosense.data.storage.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class SensorState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val createdSensor: Sensor? = null,
    val sensors: List<Sensor> = emptyList(),
    // Lecturas para gráficas
    val readings: List<SensorReading> = emptyList(),
    val isLoadingReadings: Boolean = false,
    val readingsError: String? = null
)

class SensorViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app.applicationContext)

    private val _state = MutableStateFlow(SensorState())
    val state: StateFlow<SensorState> = _state

    fun createSensor(deviceId: String, name: String, location: String?) {
        viewModelScope.launch {
            try {
                _state.value = SensorState(isLoading = true)
                val token = session.getToken()
                if (token.isNullOrBlank()) {
                    _state.value = SensorState(error = "Sesión inválida. Inicia sesión nuevamente.")
                    return@launch
                }
                val res = ApiClient.sensorApi.createSensor(
                    auth = "Bearer $token",
                    body = CreateSensorRequest(
                        device_id = deviceId.trim(),
                        name = name.trim(),
                        location = location
                    )
                )
                _state.value = SensorState(createdSensor = res.sensor)
            } catch (e: Exception) {
                val msg = if (e is HttpException)
                    e.response()?.errorBody()?.string() ?: "Error HTTP ${e.code()}"
                else e.message ?: "Error desconocido"
                _state.value = SensorState(error = msg)
            }
        }
    }

    fun loadSensors() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val token = session.getToken()
                if (token.isNullOrBlank()) {
                    _state.value = _state.value.copy(isLoading = false, error = "Sesión inválida.")
                    return@launch
                }
                val res = ApiClient.sensorApi.listSensors(auth = "Bearer $token")
                _state.value = _state.value.copy(
                    isLoading = false,
                    sensors = res.sensors ?: emptyList()
                )
            } catch (e: Exception) {
                val msg = if (e is HttpException)
                    e.response()?.errorBody()?.string() ?: "Error HTTP ${e.code()}"
                else e.message ?: "Error cargando sensores"
                _state.value = _state.value.copy(isLoading = false, error = msg)
            }
        }
    }

    fun deleteSensor(id: Int) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val token = session.getToken()
                if (token.isNullOrBlank()) {
                    _state.value = _state.value.copy(isLoading = false, error = "Sesión inválida.")
                    return@launch
                }
                ApiClient.sensorApi.deleteSensor(auth = "Bearer $token", id = id)
                _state.value = _state.value.copy(
                    isLoading = false,
                    sensors = _state.value.sensors.filter { it.id != id }
                )
            } catch (e: Exception) {
                val msg = if (e is HttpException)
                    e.response()?.errorBody()?.string() ?: "Error HTTP ${e.code()}"
                else e.message ?: "Error eliminando sensor"
                _state.value = _state.value.copy(isLoading = false, error = msg)
            }
        }
    }

    fun loadReadings(sensorId: Int, limit: Int = 50) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoadingReadings = true, readingsError = null)
                val token = session.getToken()
                if (token.isNullOrBlank()) {
                    _state.value = _state.value.copy(isLoadingReadings = false, readingsError = "Sesión inválida.")
                    return@launch
                }
                val res = ApiClient.sensorApi.getReadings(
                    auth = "Bearer $token",
                    sensorId = sensorId,
                    limit = limit
                )
                // Backend devuelve DESC — invertimos para graficar cronológicamente
                _state.value = _state.value.copy(
                    isLoadingReadings = false,
                    readings = res.readings.reversed()
                )
            } catch (e: Exception) {
                val msg = if (e is HttpException)
                    e.response()?.errorBody()?.string() ?: "Error HTTP ${e.code()}"
                else e.message ?: "Error cargando lecturas"
                _state.value = _state.value.copy(isLoadingReadings = false, readingsError = msg)
            }
        }
    }

    fun clear() {
        _state.value = SensorState()
    }
}