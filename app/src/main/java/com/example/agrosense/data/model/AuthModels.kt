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