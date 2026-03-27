package com.example.agrosense.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val AUTH_BASE_URL  = "http://3.15.133.197:3000/api/auth/"
    private const val USERS_BASE_URL = "http://3.15.133.197:3000/api/users/"
    private const val API_BASE_URL   = "http://3.15.133.197:3000/api/"

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val retrofitAuth: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(AUTH_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val retrofitUsers: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(USERS_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val retrofitApi: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(API_BASE_URL)
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

    val sensorApi: SensorApiService by lazy {
        retrofitApi.create(SensorApiService::class.java)
    }

    val alertApi: AlertApi by lazy {
        retrofitApi.create(AlertApi::class.java)
    }
}
