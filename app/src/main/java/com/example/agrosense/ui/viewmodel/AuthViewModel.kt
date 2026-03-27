package com.example.agrosense.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agrosense.data.api.ApiClient
import com.example.agrosense.data.model.*
import com.example.agrosense.data.storage.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
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

    private fun errorBody(e: HttpException): String =
        try { e.response()?.errorBody()?.string() ?: "Error HTTP ${e.code()}" }
        catch (_: Exception) { "Error HTTP ${e.code()}" }

    private fun parseMessage(body: String): String =
        try { JSONObject(body).optString("message", body) } catch (_: Exception) { body }

    // ── Sesión ───────────────────────────────────────────────────────────────

    fun checkSession() {
        viewModelScope.launch {
            val token = session.getToken()
            val logged = !token.isNullOrBlank()
            _state.value = _state.value.copy(isLoggedIn = logged)
            if (logged) loadMe()
        }
    }

    // ── Login ────────────────────────────────────────────────────────────────

    fun login(
        email: String,
        password: String,
        onNeedsVerification: ((email: String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                _state.value = AuthState(isLoading = true)
                val res = ApiClient.authApi.login(
                    LoginRequest(email = email.trim().lowercase(), password = password)
                )
                when {
                    res.isSuccessful -> {
                        val body = res.body()!!
                        session.saveToken(body.token)
                        _state.value = AuthState(isLoggedIn = true, user = body.user)
                        loadMe()
                    }
                    res.code() == 403 -> {
                        // Correo no verificado
                        val rawBody = res.errorBody()?.string() ?: ""
                        val serverEmail = try { JSONObject(rawBody).optString("email", email.trim().lowercase()) }
                                          catch (_: Exception) { email.trim().lowercase() }
                        _state.value = AuthState(isLoading = false)
                        onNeedsVerification?.invoke(serverEmail)
                    }
                    else -> {
                        val msg = parseMessage(res.errorBody()?.string() ?: "")
                        _state.value = AuthState(isLoading = false, error = msg)
                    }
                }
            } catch (e: Exception) {
                _state.value = AuthState(isLoading = false, error = e.message ?: "Error desconocido")
            }
        }
    }

    // ── Registro ─────────────────────────────────────────────────────────────

    fun register(
        nombre: String,
        apellido: String,
        email: String,
        password: String,
        onNeedsVerification: (email: String) -> Unit
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
                if (res.isSuccessful) {
                    val serverEmail = res.body()?.email ?: email.trim().lowercase()
                    _state.value = AuthState(isLoading = false)
                    onNeedsVerification(serverEmail)
                } else {
                    val msg = parseMessage(res.errorBody()?.string() ?: "")
                    _state.value = AuthState(isLoading = false, error = msg)
                }
            } catch (e: Exception) {
                _state.value = AuthState(isLoading = false, error = e.message ?: "Error desconocido")
            }
        }
    }

    // ── Verificar correo ─────────────────────────────────────────────────────

    fun verifyEmail(
        email: String,
        code: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val res = ApiClient.authApi.verifyEmail(VerifyEmailRequest(email, code))
                if (res.isSuccessful) {
                    val body = res.body()!!
                    session.saveToken(body.token)
                    _state.value = AuthState(isLoggedIn = true, user = body.user)
                    onSuccess()
                } else {
                    val msg = parseMessage(res.errorBody()?.string() ?: "")
                    onError(msg)
                }
            } catch (e: Exception) {
                onError(e.message ?: "Error de conexión")
            }
        }
    }

    // ── Recuperar contraseña ─────────────────────────────────────────────────

    fun forgotPassword(
        email: String,
        onSuccess: (email: String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val res = ApiClient.authApi.forgotPassword(ForgotPasswordRequest(email.trim().lowercase()))
                if (res.isSuccessful) {
                    onSuccess(res.body()?.email ?: email.trim().lowercase())
                } else {
                    val msg = parseMessage(res.errorBody()?.string() ?: "")
                    onError(msg)
                }
            } catch (e: Exception) {
                onError(e.message ?: "Error de conexión")
            }
        }
    }

    // ── Restablecer contraseña ───────────────────────────────────────────────

    fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val res = ApiClient.authApi.resetPassword(
                    ResetPasswordRequest(email = email, code = code, new_password = newPassword)
                )
                if (res.isSuccessful) onSuccess()
                else onError(parseMessage(res.errorBody()?.string() ?: ""))
            } catch (e: Exception) {
                onError(e.message ?: "Error de conexión")
            }
        }
    }

    // ── Reenviar código ──────────────────────────────────────────────────────

    fun resendCode(
        email: String,
        type: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val res = ApiClient.authApi.resendCode(ResendCodeRequest(email, type))
                if (res.isSuccessful) onSuccess()
                else onError(parseMessage(res.errorBody()?.string() ?: ""))
            } catch (e: Exception) {
                onError(e.message ?: "Error de conexión")
            }
        }
    }

    // ── Perfil ───────────────────────────────────────────────────────────────

    fun loadMe() {
        viewModelScope.launch {
            try {
                val token = session.getToken()
                if (token.isNullOrBlank()) return@launch
                val res = ApiClient.userApi.me("Bearer $token")
                _state.value = _state.value.copy(user = res.user, error = null)
            } catch (e: Exception) {
                if (e is HttpException && (e.code() == 401 || e.code() == 403)) logout()
                else _state.value = _state.value.copy(error = e.message ?: "Error cargando perfil")
            }
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
                    onError(parseMessage(res.errorBody()?.string() ?: ""))
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
                    ChangePasswordRequest(current_password = currentPassword, new_password = newPassword)
                )
                if (res.isSuccessful) onSuccess()
                else onError(parseMessage(res.errorBody()?.string() ?: ""))
            } catch (e: Exception) {
                onError(e.message ?: "Error desconocido")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            session.clear()
            _state.value = AuthState(isLoggedIn = false, user = null)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
