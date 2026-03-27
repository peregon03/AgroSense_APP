package com.example.agrosense.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agrosense.data.api.ApiClient
import com.example.agrosense.data.model.SensorAlert
import com.example.agrosense.data.storage.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AlertUiState(
    val alerts: List<SensorAlert> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

class AlertViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app.applicationContext)
    private val api = ApiClient.alertApi

    private val _state = MutableStateFlow(AlertUiState())
    val state: StateFlow<AlertUiState> = _state

    fun loadAlerts() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val token = session.getToken() ?: return@launch
                val response = api.getAlerts("Bearer $token")
                if (response.isSuccessful) {
                    val body = response.body()
                    _state.value = _state.value.copy(
                        alerts = body?.alerts ?: emptyList(),
                        unreadCount = body?.unread_count ?: 0,
                        isLoading = false
                    )
                } else {
                    _state.value = _state.value.copy(isLoading = false, error = "Error cargando alertas")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Error de conexión")
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            try {
                val token = session.getToken() ?: return@launch
                val response = api.markAllRead("Bearer $token")
                if (response.isSuccessful) {
                    _state.value = _state.value.copy(
                        alerts = _state.value.alerts.map { it.copy(read = true) },
                        unreadCount = 0
                    )
                }
            } catch (_: Exception) { /* silent */ }
        }
    }
}
