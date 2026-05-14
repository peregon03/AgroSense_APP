package com.example.agrosense.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agrosense.data.local.LocalRepository
import com.example.agrosense.data.local.entity.LocalReadingEntity
import com.example.agrosense.data.local.entity.LocalSensorEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

// ── Rangos de fecha disponibles en modo local ──────────────────────────────
enum class LocalDateRange(val label: String) {
    TODAY("Hoy"),
    WEEK("Semana"),
    MONTH("Mes")
}

data class LocalUiState(
    // Autenticación
    val hasUser: Boolean         = false,
    val isLoggedIn: Boolean      = false,
    val userName: String         = "",
    val isLoading: Boolean       = false,
    val error: String?           = null,
    // Sensores
    val sensors: List<LocalSensorEntity> = emptyList(),
    // Historial / gráficas
    val readings: List<LocalReadingEntity> = emptyList(),
    val isLoadingHistory: Boolean = false,
    val selectedRange: LocalDateRange = LocalDateRange.TODAY,
    val readingCount: Int         = 0
)

class LocalViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LocalRepository(app)

    private val _state = MutableStateFlow(LocalUiState())
    val state: StateFlow<LocalUiState> = _state

    /** Modo de la app: "loading" | "cloud" | "local" */
    val appMode = MutableStateFlow("loading")

    /** true si ya existe un usuario local creado */
    val hasLocalUser = MutableStateFlow(false)

    // ══════════════════════════════════════════════════════════════════════
    //  Inicialización — llamar al arrancar la app
    // ══════════════════════════════════════════════════════════════════════

    fun initialize() {
        viewModelScope.launch {
            val mode  = repo.getAppMode()
            val hasU  = repo.hasUser()
            hasLocalUser.value = hasU
            _state.value = _state.value.copy(hasUser = hasU)
            appMode.value = mode   // dispara LaunchedEffect en MainActivity
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Modo de la app
    // ══════════════════════════════════════════════════════════════════════

    fun setAppMode(mode: String) {
        viewModelScope.launch {
            repo.setAppMode(mode)
            appMode.value = mode
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Autenticación local
    // ══════════════════════════════════════════════════════════════════════

    fun createUser(name: String, pin: String) {
        if (name.isBlank()) { _state.value = _state.value.copy(error = "Ingresa tu nombre"); return }
        if (pin.length != 4) { _state.value = _state.value.copy(error = "El PIN debe tener 4 dígitos"); return }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                repo.createUser(name, pin)
                repo.setAppMode("local")
                appMode.value = "local"
                hasLocalUser.value = true
                _state.value = _state.value.copy(
                    hasUser   = true,
                    isLoggedIn = true,
                    userName  = name.trim(),
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Error al crear perfil")
            }
        }
    }

    fun loginWithPin(pin: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val user = repo.validatePin(pin)
            if (user != null) {
                repo.setAppMode("local")
                appMode.value = "local"
                _state.value = _state.value.copy(
                    isLoggedIn = true,
                    userName   = user.name,
                    isLoading  = false
                )
            } else {
                _state.value = _state.value.copy(isLoading = false, error = "PIN incorrecto")
            }
        }
    }

    fun logout() {
        _state.value = _state.value.copy(isLoggedIn = false, userName = "")
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Sensores locales
    // ══════════════════════════════════════════════════════════════════════

    fun loadSensors() {
        viewModelScope.launch {
            val list = repo.getAllSensors()
            _state.value = _state.value.copy(sensors = list)
        }
    }

    fun saveSensor(deviceId: String, name: String, location: String?, apiKey: String?) {
        viewModelScope.launch {
            repo.saveSensor(deviceId, name, location, apiKey)
            loadSensors()
        }
    }

    fun deleteSensor(deviceId: String) {
        viewModelScope.launch {
            repo.deleteSensor(deviceId)
            loadSensors()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Historial y gráficas
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Guarda las líneas del histórico recibidas por BLE y actualiza el conteo.
     * Llama a [pruneOldReadings] automáticamente al terminar.
     */
    fun saveHistoryFromBle(deviceId: String, historyLines: List<String>) {
        if (historyLines.isEmpty()) return
        viewModelScope.launch {
            repo.saveReadingsFromHistory(deviceId, historyLines)
            repo.pruneOldReadings()
            val count = repo.countReadings(deviceId)
            _state.value = _state.value.copy(readingCount = count)
        }
    }

    /**
     * Carga lecturas del rango seleccionado desde la base de datos local.
     */
    fun loadReadings(deviceId: String, range: LocalDateRange = _state.value.selectedRange) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingHistory = true, selectedRange = range)
            val nowSec = System.currentTimeMillis() / 1000L
            val fromSec = when (range) {
                LocalDateRange.TODAY -> startOfTodaySec()
                LocalDateRange.WEEK  -> nowSec - 7L  * 24 * 3600
                LocalDateRange.MONTH -> nowSec - 30L * 24 * 3600
            }
            val readings = repo.getReadingsForRange(deviceId, fromSec, nowSec)
            _state.value = _state.value.copy(
                readings         = readings,
                isLoadingHistory = false,
                selectedRange    = range
            )
        }
    }

    // ── Utilidad de tiempo ─────────────────────────────────────────────────

    private fun startOfTodaySec(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 1000L
    }
}
