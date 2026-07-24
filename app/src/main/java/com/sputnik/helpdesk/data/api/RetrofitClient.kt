package com.sputnik.helpdesk.data.api

import com.sputnik.helpdesk.App
import com.sputnik.helpdesk.BuildConfig
import com.sputnik.helpdesk.data.prefs.Prefs
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    fun create(prefs: Prefs): ApiService {
        val baseUrl = prefs.serverUrl.trimEnd('/') + "/api/"
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Accept", "application/json")
                    .apply { prefs.token?.let { addHeader("Authorization", "Bearer $it") } }
                    .build()
                val response = chain.proceed(req)
                if (response.code == 401 && !req.url.encodedPath.contains("auth/login")) {
                    prefs.clear()
                    App.sessionExpiredEvent.postValue(Unit)
                }
                response
            }
            .apply {
                if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
