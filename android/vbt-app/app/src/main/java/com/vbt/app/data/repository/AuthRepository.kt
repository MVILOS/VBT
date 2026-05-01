package com.vbt.app.data.repository

import com.vbt.app.data.local.PreferencesManager
import com.vbt.app.data.remote.ApiService
import com.vbt.app.data.remote.LoginRequest
import com.vbt.app.data.remote.RegisterRequest
import com.vbt.app.data.remote.UserDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val prefs: PreferencesManager
) {
    suspend fun login(username: String, password: String): Result<UserDto> {
        return try {
            val response = api.login(LoginRequest(username = username, password = password))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    prefs.saveToken(body.accessToken)
                    prefs.saveUser(body.user.id, body.user.username, body.user.role, body.user.coachId)
                    Result.success(body.user)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("Invalid credentials: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Connection error: ${e.message}", e))
        }
    }

    suspend fun register(username: String, password: String): Result<UserDto> {
        return try {
            val response = api.register(RegisterRequest(username = username, password = password))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    prefs.saveToken(body.accessToken)
                    prefs.saveUser(body.user.id, body.user.username, body.user.role, body.user.coachId)
                    Result.success(body.user)
                } else {
                    Result.failure(Exception("Pusta odpowiedź serwera"))
                }
            } else {
                val msg = response.errorBody()?.string() ?: "Błąd rejestracji"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Błąd połączenia: ${e.message}", e))
        }
    }

    suspend fun logout() {
        prefs.clear()
    }

    fun getCurrentRole(): Flow<String?> = prefs.getRole()

    fun getCurrentUserId(): Flow<Int?> = prefs.getUserId()

    fun getCurrentUsername(): Flow<String?> = prefs.getUsername()

    fun isLoggedIn(): Flow<Boolean> = prefs.isLoggedIn()
}
