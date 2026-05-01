package com.vbt.app.data.local.dao

import androidx.room.*
import com.vbt.app.data.local.entity.SessionSetEntity
import com.vbt.app.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getUnfinishedSession(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE status = 'active' ORDER BY startedAt DESC")
    suspend fun getActiveSessions(): List<WorkoutSessionEntity>

    @Query("UPDATE workout_sessions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE workout_sessions SET serverSessionId = :serverSessionId WHERE id = :id")
    suspend fun updateServerSessionId(id: Long, serverSessionId: Int)

    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    suspend fun getAllSessionsOnce(): List<WorkoutSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Delete
    suspend fun deleteSession(session: WorkoutSessionEntity)

    // Session sets
    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId ORDER BY setNumber")
    fun getSetsForSession(sessionId: Long): Flow<List<SessionSetEntity>>

    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId ORDER BY setNumber")
    suspend fun getSetsForSessionOnce(sessionId: Long): List<SessionSetEntity>

    @Query("SELECT * FROM session_sets WHERE id = :id")
    suspend fun getSetById(id: Long): SessionSetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: SessionSetEntity): Long

    @Update
    suspend fun updateSet(set: SessionSetEntity)
}
