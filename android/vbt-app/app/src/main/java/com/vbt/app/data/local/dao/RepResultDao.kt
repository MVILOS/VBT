package com.vbt.app.data.local.dao

import androidx.room.*
import com.vbt.app.data.local.entity.RepResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RepResultDao {
    @Query("SELECT * FROM rep_results WHERE sessionSetId = :setId AND isDeleted = 0 ORDER BY repNumber")
    fun getRepsForSet(setId: Long): Flow<List<RepResultEntity>>

    @Query("SELECT * FROM rep_results WHERE sessionSetId = :setId AND isDeleted = 0 ORDER BY repNumber")
    suspend fun getRepsForSetOnce(setId: Long): List<RepResultEntity>

    @Query("SELECT * FROM rep_results WHERE sessionSetId IN (SELECT id FROM session_sets WHERE sessionId = :sessionId) AND isDeleted = 0 ORDER BY repNumber")
    fun getRepsForSession(sessionId: Long): Flow<List<RepResultEntity>>

    @Query("SELECT * FROM rep_results WHERE deviceRepIndex = :deviceRepIndex AND sessionSetId = :setId AND isDeleted = 0 LIMIT 1")
    suspend fun findByDeviceRepIndex(deviceRepIndex: Int, setId: Long): RepResultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rep: RepResultEntity): Long

    @Update
    suspend fun update(rep: RepResultEntity)

    @Query("UPDATE rep_results SET isDeleted = 1 WHERE id = :repId")
    suspend fun softDelete(repId: Long)

    @Query("UPDATE rep_results SET isDeleted = 0 WHERE id = :repId")
    suspend fun undoDelete(repId: Long)

    @Query("SELECT COUNT(*) FROM rep_results WHERE sessionSetId = :setId AND isDeleted = 0")
    suspend fun getRepCountForSet(setId: Long): Int
}
