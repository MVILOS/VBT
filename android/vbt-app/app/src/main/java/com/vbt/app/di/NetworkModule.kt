package com.vbt.app.di

import android.content.Context
import com.vbt.app.data.local.PreferencesManager
import com.vbt.app.data.remote.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Backend jest za nginx, który wymusza HTTPS (port 80 robi 301 -> https) i CORS
    // ogranicza origin do https://130.61.232.212. Bezpośrednie http://...:8000
    // omijało nginx i trafiało prosto w kontener backendu - nieaktualne.
    private const val BASE_URL = "https://130.61.232.212/api/"

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager =
        PreferencesManager(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(preferencesManager: PreferencesManager): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val jwtInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()

            // Get token from DataStore synchronously using runBlocking
            val token = runBlocking {
                preferencesManager.getToken().first()
            }

            val requestBuilder = originalRequest.newBuilder()
            if (!token.isNullOrEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            chain.proceed(requestBuilder.build())
        }

        return OkHttpClient.Builder()
            .addInterceptor(jwtInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)
}
