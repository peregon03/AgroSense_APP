package com.example.agrosense.data.api

import com.example.agrosense.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("register")
    suspend fun register(@Body req: RegisterRequest): Response<PendingEmailResponse>

    @POST("login")
    suspend fun login(@Body req: LoginRequest): Response<LoginResponse>

    @POST("verify-email")
    suspend fun verifyEmail(@Body req: VerifyEmailRequest): Response<LoginResponse>

    @POST("forgot-password")
    suspend fun forgotPassword(@Body req: ForgotPasswordRequest): Response<PendingEmailResponse>

    @POST("reset-password")
    suspend fun resetPassword(@Body req: ResetPasswordRequest): Response<Unit>

    @POST("resend-code")
    suspend fun resendCode(@Body req: ResendCodeRequest): Response<Unit>
}
