package com.example.agrosense.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agrosense.data.api.ApiClient
import com.example.agrosense.data.model.ChangePasswordRequest
import com.example.agrosense.data.model.LoginRequest
import com.example.agrosense.data.model.RegisterRequest
import com.example.agrosense.data.model.UpdateProfileRequest
import com.example.agrosense.data.model.User
import com.example.agrosense.data.storage.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class AuthState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val user: User? = null
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app.applicationContext)

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state

    private fun httpErrorMessage(e: HttpException): String {
        return try {
            e.response()?.errorBody()?.string() ?: "Error HTTP ${e.code()}"
        } catch (_: Exception) {
            "Error HTTP ${e.code()}"
        }
    }

    fun checkSession() {
        viewModelScope.launch {
            val token = session.getToken()
            val logged = !token.isNullOrBlank()
            _state.value = _state.value.copy(isLoggedIn = logged)
            if (logged) loadMe()
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                _state.value = AuthState(isLoading = true)
                val res = ApiClient.authApi.login(
                    LoginRequest(email = email.trim(), password = password)
                )
                session.saveToken(res.token)
                _state.value = AuthState(isLoggedIn = true, user = res.user)
                loadMe()
            } catch (e: Exception) {
                val msg = if (e is HttpException) httpErrorMessage(e) else (e.message ?: "Error desconocido")
                _state.value = AuthState(isLoading = false, error = msg, isLoggedIn = false)
            }
        }
    }

    fun register(
        nombre: String,
        apellido: String,
        email: String,
        password: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                _state.value = AuthState(isLoading = true)
                val res = ApiClient.authApi.register(
                    RegisterRequest(
                        first_name = nombre.trim(),
                        last_name  = apellido.trim(),
                        email      = email.trim(),
                        password   = password
                    )
                )
                session.saveToken(res.token)
                _state.value = AuthState(isLoggedIn = true, user = res.user)
                loadMe()
                onSuccess()
            } catch (e: Exception) {
                val msg = if (e is HttpException) httpErrorMessage(e) else (e.message ?: "Error desconocido")
                _state.value = AuthState(isLoading = false, error = msg, isLoggedIn = false)
            }
        }
    }

    fun loadMe() {
        viewModelScope.launch {
            try {
                val token = session.getToken()
                if (token.isNullOrBlank()) return@launch
                val res = ApiClient.userApi.me("Bearer $token")
                _state.value = _state.value.copy(user = res.user, error = null)
            } catch (e: Exception) {
                if (e is HttpException && (e.code() == 401 || e.code() == 403)) {
                    logout()
                } else {
                    val msg = if (e is HttpException) httpErrorMessage(e) else (e.message ?: "Error cargando perfil")
                    _state.value = _state.value.copy(error = msg)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            session.clear()
            _state.value = AuthState(isLoggedIn = false, user = null)
        }
    }

    fun updateProfile(
        firstName: String,
        lastName: String,
        email: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val token = session.getToken() ?: return@launch onError("No autenticado")
                val res = ApiClient.userApi.updateProfile(
                    "Bearer $token",
                    UpdateProfileRequest(
                        first_name = firstName.trim(),
                        last_name  = lastName.trim(),
                        email      = email.trim().lowercase()
                    )
                )
                if (res.isSuccessful) {
                    res.body()?.user?.let { user ->
                        _state.value = _state.value.copy(user = user, error = null)
                    }
                    onSuccess()
                } else {
                    val msg = res.errorBody()?.string() ?: "Error al actualizar"
                    onError(msg)
                }
            } catch (e: Exception) {
                onError(e.message ?: "Error desconocido")
            }
        }
    }

    fun changePassword(
        currentPassword: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val token = session.getToken() ?: return@launch onError("No autenticado")
                val res = ApiClient.userApi.changePassword(
                    "Bearer $token",
                    ChangePasswordRequest(
                        current_password = currentPassword,
                        new_password     = newPassword
                    )
                )
                if (res.isSuccessful) onSuccess()
                else {
                    val msg = res.errorBody()?.string() ?: "Error al cambiar contraseña"
                    onError(msg)
                }
            } catch (e: Exception) {
                onError(e.message ?: "Error desconocido")
            }
        }
    }

    // Limpia el error cuando el usuario empieza a escribir
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}