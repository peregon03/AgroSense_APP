package com.example.agrosense.data.api

import com.example.agrosense.data.model.ChangePasswordRequest
import com.example.agrosense.data.model.MeResponse
import com.example.agrosense.data.model.UpdateProfileRequest
import com.example.agrosense.data.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT

interface UserApi {
    @GET("me")
    suspend fun me(@Header("Authorization") authHeader: String): MeResponse

    @PUT("me")
    suspend fun updateProfile(
        @Header("Authorization") authHeader: String,
        @Body body: UpdateProfileRequest
    ): Response<MeResponse>

    @PUT("me/password")
    suspend fun changePassword(
        @Header("Authorization") authHeader: String,
        @Body body: ChangePasswordRequest
    ): Response<Unit>
}
