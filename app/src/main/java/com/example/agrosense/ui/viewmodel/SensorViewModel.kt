package com.example.agrosense.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agrosense.data.api.ApiClient
import com.example.agrosense.data.model.CreateSensorRequest
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.data.storage.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class SensorState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val createdSensor: Sensor? = null,
    val sensors: List<Sensor> = emptyList()
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
                val msg = if (e is HttpException) {
                    e.response()?.errorBody()?.string() ?: "Error HTTP ${e.code()}"
                } else {
                    e.message ?: "Error desconocido"
                }

                _state.value = SensorState(error = msg)
            }
        }
    }

    fun clear() {
        _state.value = SensorState()
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
                _state.value = _state.value.copy(isLoading = false, sensors = res.sensors)

            } catch (e: Exception) {
                val msg = if (e is HttpException)
                    e.response()?.errorBody()?.string() ?: "Error HTTP ${e.code()}"
                else e.message ?: "Error cargando sensores"

                _state.value = _state.value.copy(isLoading = false, error = msg)
            }
        }
    }

}

