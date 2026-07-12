package com.vbt.app.di

import android.content.Context
import com.vbt.app.BuildConfig
import com.vbt.app.data.local.PreferencesManager
import com.vbt.app.data.remote.ApiService
import com.vbt.app.data.remote.SessionExpiredNotifier
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
    fun provideOkHttpClient(
        preferencesManager: PreferencesManager,
        sessionExpiredNotifier: SessionExpiredNotifier
    ): OkHttpClient {
        // Pełne logowanie request/response tylko w DEBUG - w release logi HTTP
        // (tokeny, dane użytkowników) nie mogą trafiać do logcat.
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
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

        // 401 poza endpointami logowania/rejestracji = token wygasł/nieprawidłowy:
        // czyścimy sesję i sygnalizujemy UI konieczność ponownego logowania.
        val sessionExpiryInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val path = chain.request().url.encodedPath
            val isAuthEndpoint = path.contains("/auth/login") || path.contains("/auth/register")
            if (response.code == 401 && !isAuthEndpoint) {
                runBlocking { preferencesManager.clear() }
                sessionExpiredNotifier.notifyExpired()
            }
            response
        }

        return OkHttpClient.Builder()
            .addInterceptor(jwtInterceptor)
            .addInterceptor(sessionExpiryInterceptor)
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
