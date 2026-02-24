package com.example.agrosense.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // ✅ Producción (Render) - SIEMPRE HTTPS
    private const val AUTH_BASE_URL = "https://agrosense-backend-bjw4.onrender.com/api/auth/"
    private const val USERS_BASE_URL = "https://agrosense-backend-bjw4.onrender.com/api/users/"

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            // ✅ Render free puede tardar en “despertar”
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val retrofitAuth: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(AUTH_BASE_URL) // debe terminar en /
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val retrofitUsers: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(USERS_BASE_URL) // debe terminar en /
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApi: AuthApi by lazy {
        retrofitAuth.create(AuthApi::class.java)
    }

    val userApi: UserApi by lazy {
        retrofitUsers.create(UserApi::class.java)
    }
    private const val API_BASE_URL =
        "https://agrosense-backend-bjw4.onrender.com/api/"

    private val retrofitApi: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val sensorApi: SensorApi by lazy {
        retrofitApi.create(SensorApi::class.java)
    }
}

