package com.example.agrosense.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agrosense.data.api.ApiClient
import com.example.agrosense.data.api.RegisterSensorRequest
import com.example.agrosense.data.model.ActionLog
import com.example.agrosense.data.model.DeletedSensorBackup
import com.example.agrosense.data.model.IrrigationSchedule
import com.example.agrosense.data.model.SensorShare
import com.example.agrosense.data.model.SharePermissionsRequest
import com.example.agrosense.data.model.ShareSensorRequest
import com.example.agrosense.data.model.SharedSensorEntry
import com.example.agrosense.data.model.IrrigationScheduleRequest
import com.example.agrosense.data.model.PumpOverrideRequest
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.data.model.SensorReading
import com.example.agrosense.data.model.ThresholdsRequest
import com.example.agrosense.data.model.ToggleScheduleRequest
import com.example.agrosense.data.storage.SessionManager
import kotlinx.coroutines.delay
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

data class IrrigationSchedulesUiState(
    val schedules: List<IrrigationSchedule> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class DeletedSensorsUiState(
    val backups: List<DeletedSensorBackup> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class SharedSensorsUiState(
    val sensors: List<SharedSensorEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class SensorSharesUiState(
    val shares: List<SensorShare> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ActionLogsUiState(
    val logs: List<ActionLog> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SensorViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app.applicationContext)
    private val api = ApiClient.sensorApi

    private val _state = MutableStateFlow(SensorUiState())
    val state: StateFlow<SensorUiState> = _state

    private val _irrigationState = MutableStateFlow(IrrigationSchedulesUiState())
    val irrigationState: StateFlow<IrrigationSchedulesUiState> = _irrigationState

    private val _deletedState = MutableStateFlow(DeletedSensorsUiState())
    val deletedState: StateFlow<DeletedSensorsUiState> = _deletedState

    private val _sharedSensorsState = MutableStateFlow(SharedSensorsUiState())
    val sharedSensorsState: StateFlow<SharedSensorsUiState> = _sharedSensorsState

    private val _sensorSharesState = MutableStateFlow(SensorSharesUiState())
    val sensorSharesState: StateFlow<SensorSharesUiState> = _sensorSharesState

    private val _actionLogsState = MutableStateFlow(ActionLogsUiState())
    val actionLogsState: StateFlow<ActionLogsUiState> = _actionLogsState

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
                    error = netError(e),
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
                onError(netError(e))
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
                    val errBody = response.errorBody()?.string()
                    val msg = try {
                        org.json.JSONObject(errBody ?: "").getString("message")
                    } catch (_: Exception) {
                        "Error al guardar (${response.code()})"
                    }
                    onError(msg)
                }
            } catch (e: Exception) {
                onError(netError(e))
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
                    readingsError = netError(e),
                    isLoadingReadings = false
                )
            }
        }
    }

    // ── Control manual remoto de bomba ────────────────────────────────────────

    fun setPumpOverride(
        sensorId: Int,
        override: Boolean?,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val token = session.getToken()
                    ?: run { onError("No hay sesión activa"); return@launch }
                val response = api.setPumpOverride("Bearer $token", sensorId, PumpOverrideRequest(override))
                if (response.isSuccessful) {
                    _state.value = _state.value.copy(
                        sensors = _state.value.sensors.map { s ->
                            if (s.id == sensorId) s.copy(pump_manual_override = override) else s
                        }
                    )
                    if (override == null) {
                        onSuccess("Modo automático activado")
                        return@launch
                    }
                    // Esperar confirmación del microcontrolador (max 10 s)
                    val deadline = System.currentTimeMillis() + 10_000L
                    while (System.currentTimeMillis() < deadline) {
                        delay(2_000)
                        try {
                            val ackRes = api.getPumpAckStatus("Bearer $token", sensorId)
                            if (ackRes.isSuccessful && ackRes.body()?.pending == false) {
                                val msg = if (override) "Motobomba encendida con éxito"
                                          else "Motobomba apagada con éxito"
                                onSuccess(msg)
                                return@launch
                            }
                        } catch (_: Exception) { }
                    }
                    onError("El microcontrolador no respondió. La instrucción fue guardada.")
                } else {
                    val errBody = response.errorBody()?.string()
                    val msg = try {
                        org.json.JSONObject(errBody ?: "").getString("message")
                    } catch (_: Exception) { "Error (${response.code()})" }
                    onError(msg)
                }
            } catch (e: Exception) {
                onError(netError(e))
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    // ── Programaciones de riego (múltiples) ───────────────────────────────────

    fun loadIrrigationSchedules(sensorId: Int) {
        viewModelScope.launch {
            _irrigationState.value = _irrigationState.value.copy(isLoading = true, error = null)
            try {
                val token = session.getToken() ?: return@launch
                val response = api.getIrrigationSchedules("Bearer $token", sensorId)
                if (response.isSuccessful) {
                    _irrigationState.value = IrrigationSchedulesUiState(
                        schedules  = response.body()?.schedules ?: emptyList(),
                        isLoading  = false
                    )
                } else {
                    _irrigationState.value = IrrigationSchedulesUiState(
                        isLoading = false,
                        error     = "Error al cargar programaciones (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _irrigationState.value = IrrigationSchedulesUiState(
                    isLoading = false,
                    error     = netError(e)
                )
            }
        }
    }

    fun createIrrigationSchedule(
        sensorId: Int,
        request: IrrigationScheduleRequest,
        onResult: (success: Boolean, error: String?) -> Unit
    ) {
        viewModelScope.launch {
            _irrigationState.value = _irrigationState.value.copy(isLoading = true, error = null)
            try {
                val token = session.getToken() ?: run { onResult(false, "No hay sesión activa"); return@launch }
                val response = api.createIrrigationSchedule("Bearer $token", sensorId, request)
                if (response.isSuccessful) {
                    val newSchedule = response.body()?.schedule
                    if (newSchedule != null) {
                        _irrigationState.value = _irrigationState.value.copy(
                            schedules = (_irrigationState.value.schedules + newSchedule)
                                .sortedBy { it.start_time },
                            isLoading = false
                        )
                    }
                    onResult(true, null)
                } else {
                    val msg = parseError(response.errorBody()?.string(), response.code())
                    _irrigationState.value = _irrigationState.value.copy(isLoading = false, error = msg)
                    onResult(false, msg)
                }
            } catch (e: Exception) {
                val msg = netError(e)
                _irrigationState.value = _irrigationState.value.copy(isLoading = false, error = msg)
                onResult(false, msg)
            }
        }
    }

    fun updateIrrigationSchedule(
        sensorId: Int,
        scheduleId: Int,
        request: IrrigationScheduleRequest,
        onResult: (success: Boolean, error: String?) -> Unit
    ) {
        viewModelScope.launch {
            _irrigationState.value = _irrigationState.value.copy(isLoading = true, error = null)
            try {
                val token = session.getToken() ?: run { onResult(false, "No hay sesión activa"); return@launch }
                val response = api.updateIrrigationSchedule("Bearer $token", sensorId, scheduleId, request)
                if (response.isSuccessful) {
                    val updated = response.body()?.schedule
                    if (updated != null) {
                        _irrigationState.value = _irrigationState.value.copy(
                            schedules = _irrigationState.value.schedules
                                .map { if (it.id == scheduleId) updated else it }
                                .sortedBy { it.start_time },
                            isLoading = false
                        )
                    }
                    onResult(true, null)
                } else {
                    val msg = parseError(response.errorBody()?.string(), response.code())
                    _irrigationState.value = _irrigationState.value.copy(isLoading = false, error = msg)
                    onResult(false, msg)
                }
            } catch (e: Exception) {
                val msg = netError(e)
                _irrigationState.value = _irrigationState.value.copy(isLoading = false, error = msg)
                onResult(false, msg)
            }
        }
    }

    fun toggleIrrigationSchedule(sensorId: Int, scheduleId: Int, enabled: Boolean) {
        viewModelScope.launch {
            // Actualización optimista en UI
            _irrigationState.value = _irrigationState.value.copy(
                schedules = _irrigationState.value.schedules.map {
                    if (it.id == scheduleId) it.copy(enabled = enabled) else it
                }
            )
            try {
                val token = session.getToken() ?: return@launch
                api.toggleIrrigationSchedule("Bearer $token", sensorId, scheduleId, ToggleScheduleRequest(enabled))
            } catch (_: Exception) {
                // Revertir si falla
                _irrigationState.value = _irrigationState.value.copy(
                    schedules = _irrigationState.value.schedules.map {
                        if (it.id == scheduleId) it.copy(enabled = !enabled) else it
                    }
                )
            }
        }
    }

    fun deleteIrrigationSchedule(sensorId: Int, scheduleId: Int, onResult: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val token = session.getToken() ?: run { onResult(false); return@launch }
                val response = api.deleteIrrigationSchedule("Bearer $token", sensorId, scheduleId)
                if (response.isSuccessful) {
                    _irrigationState.value = _irrigationState.value.copy(
                        schedules = _irrigationState.value.schedules.filter { it.id != scheduleId }
                    )
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    // ── Sensores compartidos conmigo ──────────────────────────────────────────

    fun loadSharedWithMe() {
        viewModelScope.launch {
            _sharedSensorsState.value = _sharedSensorsState.value.copy(isLoading = true, error = null)
            try {
                val token = session.getToken() ?: return@launch
                val response = api.getSharedWithMe("Bearer $token")
                if (response.isSuccessful) {
                    _sharedSensorsState.value = SharedSensorsUiState(
                        sensors   = response.body()?.shared_sensors ?: emptyList(),
                        isLoading = false
                    )
                } else {
                    _sharedSensorsState.value = SharedSensorsUiState(isLoading = false, error = "Error cargando sensores compartidos")
                }
            } catch (e: Exception) {
                _sharedSensorsState.value = SharedSensorsUiState(isLoading = false, error = netError(e))
            }
        }
    }

    fun setPumpOverrideShared(
        sensorId: Int,
        override: Boolean?,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val token = session.getToken() ?: run { onError("No hay sesión activa"); return@launch }
                val response = api.setPumpOverride("Bearer $token", sensorId, PumpOverrideRequest(override))
                if (response.isSuccessful) {
                    _sharedSensorsState.value = _sharedSensorsState.value.copy(
                        sensors = _sharedSensorsState.value.sensors.map { s ->
                            if (s.sensor_id == sensorId) s.copy(pump_manual_override = override) else s
                        }
                    )
                    if (override == null) {
                        onSuccess("Modo automático activado")
                        return@launch
                    }
                    val deadline = System.currentTimeMillis() + 10_000L
                    while (System.currentTimeMillis() < deadline) {
                        delay(2_000)
                        try {
                            val ackRes = api.getPumpAckStatus("Bearer $token", sensorId)
                            if (ackRes.isSuccessful && ackRes.body()?.pending == false) {
                                val msg = if (override) "Motobomba encendida con éxito"
                                          else "Motobomba apagada con éxito"
                                onSuccess(msg)
                                return@launch
                            }
                        } catch (_: Exception) { }
                    }
                    onError("El microcontrolador no respondió. La instrucción fue guardada.")
                } else {
                    val msg = parseError(response.errorBody()?.string(), response.code())
                    onError(msg)
                }
            } catch (e: Exception) {
                onError(netError(e))
            }
        }
    }

    // ── Gestión de accesos compartidos (propietario) ───────────────────────────

    fun loadSensorShares(sensorId: Int) {
        viewModelScope.launch {
            _sensorSharesState.value = _sensorSharesState.value.copy(isLoading = true, error = null)
            try {
                val token = session.getToken() ?: return@launch
                val response = api.getSensorShares("Bearer $token", sensorId)
                if (response.isSuccessful) {
                    _sensorSharesState.value = SensorSharesUiState(
                        shares    = response.body()?.shares ?: emptyList(),
                        isLoading = false
                    )
                } else {
                    _sensorSharesState.value = SensorSharesUiState(isLoading = false, error = "Error cargando accesos")
                }
            } catch (e: Exception) {
                _sensorSharesState.value = SensorSharesUiState(isLoading = false, error = netError(e))
            }
        }
    }

    fun shareSensor(
        sensorId: Int,
        request: ShareSensorRequest,
        onResult: (success: Boolean, error: String?) -> Unit
    ) {
        viewModelScope.launch {
            _sensorSharesState.value = _sensorSharesState.value.copy(isLoading = true, error = null)
            try {
                val token = session.getToken() ?: run { onResult(false, "No hay sesión activa"); return@launch }
                val response = api.shareSensor("Bearer $token", sensorId, request)
                if (response.isSuccessful) {
                    val newShare = response.body()?.share
                    if (newShare != null) {
                        val existing = _sensorSharesState.value.shares.any { it.id == newShare.id }
                        _sensorSharesState.value = _sensorSharesState.value.copy(
                            shares = if (existing)
                                _sensorSharesState.value.shares.map { if (it.id == newShare.id) newShare else it }
                            else
                                _sensorSharesState.value.shares + newShare,
                            isLoading = false
                        )
                    }
                    onResult(true, null)
                } else {
                    val msg = parseError(response.errorBody()?.string(), response.code())
                    _sensorSharesState.value = _sensorSharesState.value.copy(isLoading = false, error = msg)
                    onResult(false, msg)
                }
            } catch (e: Exception) {
                _sensorSharesState.value = _sensorSharesState.value.copy(isLoading = false, error = netError(e))
                onResult(false, netError(e))
            }
        }
    }

    fun updateSharePermissions(
        sensorId: Int,
        shareId: Int,
        request: SharePermissionsRequest
    ) {
        viewModelScope.launch {
            try {
                val token = session.getToken() ?: return@launch
                val response = api.updateSharePermissions("Bearer $token", sensorId, shareId, request)
                if (response.isSuccessful) {
                    val updated = response.body()?.share ?: return@launch
                    _sensorSharesState.value = _sensorSharesState.value.copy(
                        shares = _sensorSharesState.value.shares.map { if (it.id == shareId) updated else it }
                    )
                }
            } catch (_: Exception) { }
        }
    }

    fun revokeShare(sensorId: Int, shareId: Int, onResult: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val token = session.getToken() ?: run { onResult(false); return@launch }
                val response = api.revokeShare("Bearer $token", sensorId, shareId)
                if (response.isSuccessful) {
                    _sensorSharesState.value = _sensorSharesState.value.copy(
                        shares = _sensorSharesState.value.shares.filter { it.id != shareId }
                    )
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    // ── Historial de acciones ─────────────────────────────────────────────────

    fun loadActionLogs(sensorId: Int) {
        viewModelScope.launch {
            _actionLogsState.value = ActionLogsUiState(isLoading = true)
            try {
                val token = session.getToken() ?: return@launch
                val response = api.getActionLogs("Bearer $token", sensorId)
                if (response.isSuccessful) {
                    _actionLogsState.value = ActionLogsUiState(
                        logs      = response.body()?.logs ?: emptyList(),
                        isLoading = false
                    )
                } else {
                    _actionLogsState.value = ActionLogsUiState(
                        isLoading = false,
                        error     = "Error al cargar historial (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _actionLogsState.value = ActionLogsUiState(isLoading = false, error = netError(e))
            }
        }
    }

    // ── Sensores eliminados / respaldos ───────────────────────────────────────

    fun loadDeletedSensors() {
        viewModelScope.launch {
            _deletedState.value = _deletedState.value.copy(isLoading = true, error = null)
            try {
                val token = session.getToken() ?: return@launch
                val response = api.getDeletedSensors("Bearer $token")
                if (response.isSuccessful) {
                    _deletedState.value = DeletedSensorsUiState(
                        backups   = response.body()?.backups ?: emptyList(),
                        isLoading = false
                    )
                } else {
                    _deletedState.value = DeletedSensorsUiState(
                        isLoading = false,
                        error     = "Error al cargar respaldos (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _deletedState.value = DeletedSensorsUiState(isLoading = false, error = netError(e))
            }
        }
    }

    fun restoreSensor(
        backupId: Int,
        onResult: (success: Boolean, error: String?) -> Unit
    ) {
        viewModelScope.launch {
            _deletedState.value = _deletedState.value.copy(isLoading = true, error = null)
            try {
                val token = session.getToken() ?: run { onResult(false, "No hay sesión activa"); return@launch }
                val response = api.restoreSensor("Bearer $token", backupId)
                if (response.isSuccessful) {
                    // Quitar de la lista de respaldos
                    _deletedState.value = _deletedState.value.copy(
                        backups   = _deletedState.value.backups.filter { it.id != backupId },
                        isLoading = false
                    )
                    // Refrescar lista principal de sensores
                    loadSensors()
                    onResult(true, null)
                } else {
                    val msg = parseError(response.errorBody()?.string(), response.code())
                    _deletedState.value = _deletedState.value.copy(isLoading = false, error = msg)
                    onResult(false, msg)
                }
            } catch (e: Exception) {
                val msg = netError(e)
                _deletedState.value = _deletedState.value.copy(isLoading = false, error = msg)
                onResult(false, msg)
            }
        }
    }

    fun permanentlyDeleteBackup(backupId: Int, onResult: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val token = session.getToken() ?: run { onResult(false); return@launch }
                val response = api.permanentlyDeleteBackup("Bearer $token", backupId)
                if (response.isSuccessful) {
                    _deletedState.value = _deletedState.value.copy(
                        backups = _deletedState.value.backups.filter { it.id != backupId }
                    )
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    // ── Informe PDF ───────────────────────────────────────────────────────────

    fun downloadReport(
        sensorId: Int,
        from: String,
        to: String,
        onSuccess: (java.io.File) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val token = session.getToken() ?: run { onError("No hay sesión activa"); return@launch }
                val response = api.downloadReport("Bearer $token", sensorId, from, to)
                if (response.isSuccessful) {
                    val body = response.body() ?: run { onError("Respuesta vacía"); return@launch }
                    val reportsDir = java.io.File(getApplication<android.app.Application>().cacheDir, "reports")
                    reportsDir.mkdirs()
                    val file = java.io.File(reportsDir, "informe_${sensorId}_${from}_${to}.pdf")
                    body.byteStream().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    onSuccess(file)
                } else {
                    onError("Error al generar el informe (${response.code()})")
                }
            } catch (e: Exception) {
                onError(netError(e))
            }
        }
    }

    private fun parseError(body: String?, code: Int): String {
        return try {
            org.json.JSONObject(body ?: "").getString("message")
        } catch (_: Exception) {
            "Error ($code)"
        }
    }

    private fun netError(e: Exception): String = when (e) {
        is java.net.ConnectException       -> "No se pudo conectar al servidor. Verifica que el servidor esté encendido."
        is java.net.SocketTimeoutException -> "El servidor tardó demasiado en responder. Intenta de nuevo."
        is java.net.UnknownHostException   -> "Sin conexión a internet."
        else                               -> "Error de red: ${e.message}"
    }
}
