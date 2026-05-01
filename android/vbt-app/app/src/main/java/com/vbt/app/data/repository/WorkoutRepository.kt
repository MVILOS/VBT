package com.vbt.app.data.repository

import com.vbt.app.data.local.dao.RepResultDao
import com.vbt.app.data.local.dao.WorkoutSessionDao
import com.vbt.app.data.local.entity.RepResultEntity
import com.vbt.app.data.local.entity.SessionSetEntity
import com.vbt.app.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val sessionDao: WorkoutSessionDao,
    private val repResultDao: RepResultDao
) {
    suspend fun createSession(planId: Long? = null): Long {
        return sessionDao.insertSession(
            WorkoutSessionEntity(
                planId = planId,
                startedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun createActiveSession(planId: Long? = null, athleteServerId: Int? = null): Long {
        return sessionDao.insertSession(
            WorkoutSessionEntity(
                planId = planId,
                startedAt = System.currentTimeMillis(),
                status = "active",
                athleteServerId = athleteServerId
            )
        )
    }

    suspend fun finishSession(sessionId: Long) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.updateSession(session.copy(finishedAt = System.currentTimeMillis(), status = "finished"))
    }

    suspend fun updateSessionStatus(sessionId: Long, status: String) {
        sessionDao.updateStatus(sessionId, status)
    }

    suspend fun updateServerSessionId(sessionId: Long, serverSessionId: Int) {
        sessionDao.updateServerSessionId(sessionId, serverSessionId)
    }

    suspend fun deleteSession(sessionId: Long) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.deleteSession(session)
    }

    suspend fun getActiveSessions(): List<WorkoutSessionEntity> = sessionDao.getActiveSessions()

    suspend fun getSession(id: Long): WorkoutSessionEntity? = sessionDao.getSessionById(id)

    suspend fun getUnfinishedSession(): WorkoutSessionEntity? = sessionDao.getUnfinishedSession()

    fun getAllSessions(): Flow<List<WorkoutSessionEntity>> = sessionDao.getAllSessions()

    suspend fun getAllSessionsOnce(): List<WorkoutSessionEntity> = sessionDao.getAllSessionsOnce()

    suspend fun createSet(sessionId: Long, exerciseId: Long, setNumber: Int, loadKg: Float, planSetId: Long? = null): Long {
        return sessionDao.insertSet(
            SessionSetEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                planSetId = planSetId,
                setNumber = setNumber,
                actualLoadKg = loadKg,
                startedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun finishSet(setId: Long) {
        val set = sessionDao.getSetById(setId) ?: return
        sessionDao.updateSet(set.copy(finishedAt = System.currentTimeMillis(), isCompleted = true))
    }

    suspend fun updateSetLoad(setId: Long, newLoad: Float) {
        val set = sessionDao.getSetById(setId) ?: return
        sessionDao.updateSet(set.copy(actualLoadKg = newLoad))
    }

    fun getSetsForSession(sessionId: Long): Flow<List<SessionSetEntity>> =
        sessionDao.getSetsForSession(sessionId)

    suspend fun getSetsForSessionOnce(sessionId: Long): List<SessionSetEntity> =
        sessionDao.getSetsForSessionOnce(sessionId)

    suspend fun addRep(
        sessionSetId: Long,
        repNumber: Int,
        maxVelocityMs: Float,
        distanceM: Float,
        durationMs: Int,
        powerW: Float,
        deviceRepIndex: Int,
        deviceTimestamp: Long
    ): Long {
        return repResultDao.insert(
            RepResultEntity(
                sessionSetId = sessionSetId,
                repNumber = repNumber,
                maxVelocityMs = maxVelocityMs,
                distanceM = distanceM,
                durationMs = durationMs,
                powerW = powerW,
                deviceRepIndex = deviceRepIndex,
                deviceTimestamp = deviceTimestamp,
                recordedAt = System.currentTimeMillis()
            )
        )
    }

    fun getRepsForSet(setId: Long): Flow<List<RepResultEntity>> = repResultDao.getRepsForSet(setId)

    suspend fun getRepsForSetOnce(setId: Long): List<RepResultEntity> = repResultDao.getRepsForSetOnce(setId)

    fun getRepsForSession(sessionId: Long): Flow<List<RepResultEntity>> = repResultDao.getRepsForSession(sessionId)

    suspend fun softDeleteRep(repId: Long) = repResultDao.softDelete(repId)

    suspend fun undoDeleteRep(repId: Long) = repResultDao.undoDelete(repId)

    suspend fun findRepByDeviceIndex(deviceRepIndex: Int, setId: Long): RepResultEntity? =
        repResultDao.findByDeviceRepIndex(deviceRepIndex, setId)

    suspend fun getRepCount(setId: Long): Int = repResultDao.getRepCountForSet(setId)
}
