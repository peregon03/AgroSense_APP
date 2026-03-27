package com.example.agrosense.data.model

data class RegisterRequest(
    val first_name: String,
    val last_name: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class User(
    val id: Int,
    val first_name: String,
    val last_name: String,
    val email: String
)

data class LoginResponse(
    val token: String,
    val user: User
)

data class MeResponse(
    val user: User
)

data class UpdateProfileRequest(
    val first_name: String,
    val last_name: String,
    val email: String
)

data class ChangePasswordRequest(
    val current_password: String,
    val new_password: String
)

data class PendingEmailResponse(val email: String)
data class VerifyEmailRequest(val email: String, val code: String)
data class ForgotPasswordRequest(val email: String)
data class ResetPasswordRequest(val email: String, val code: String, val new_password: String)
data class ResendCodeRequest(val email: String, val type: String)