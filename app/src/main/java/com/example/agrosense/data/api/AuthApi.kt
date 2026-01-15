package com.example.agrosense.data.api
import com.example.agrosense.data.model.LoginRequest
import com.example.agrosense.data.model.LoginResponse
import com.example.agrosense.data.model.RegisterRequest
import com.example.agrosense.data.model.*
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("register")
    suspend fun register(@Body req: RegisterRequest): LoginResponse

    @POST("login")
    suspend fun login(@Body req: LoginRequest): LoginResponse
}
