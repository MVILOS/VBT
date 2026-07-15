package com.vbt.app.data.sync

import android.util.Log
import com.vbt.app.data.remote.ApiService
import com.vbt.app.data.remote.AppendRepsRequest
import com.vbt.app.data.remote.CreateExerciseRequest
import com.vbt.app.data.remote.CreateSessionRequest
import com.vbt.app.data.remote.ExerciseDto
import com.vbt.app.data.remote.RepResultDto
import com.vbt.app.data.remote.StartLiveSessionRequest
import com.vbt.app.data.repository.ExerciseRepository
import com.vbt.app.data.repository.WorkoutRepository
import com.vbt.app.domain.usecase.Estimate1RMUseCase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wspólna logika synchronizacji treningów z serwerem, używana przez
 * WorkoutViewModel (sesja "live") oraz SessionSyncWorker (zaległe sesje
 * offline). Wyodrębnione z WorkoutViewModel, żeby ta sama ścieżka budowania
 * CreateSessionRequest obsługiwała oba przypadki.
 */
@Singleton
class WorkoutSyncManager @Inject constructor(
    private val api: ApiService,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val estimate1RM: Estimate1RMUseCase
) {
    companion object {
        private const val TAG = "WorkoutSyncManager"
    }

    data class SyncResult(val synced: Int, val total: Int) {
        val allSynced: Boolean get() = synced == total
    }

    // Cache ćwiczeń serwerowych (id > 0) do dopasowywania po nazwie
    private val exerciseCacheMutex = Mutex()
    private val serverExerciseCache = mutableListOf<ExerciseDto>()

    // Powtórzenia oczekujące na push do trwającej sesji live na serwerze
    private val pendingReps = mutableListOf<RepResultDto>()

    val hasPendingReps: Boolean
        get() = synchronized(pendingReps) { pendingReps.isNotEmpty() }

    fun queueRep(rep: RepResultDto) {
        synchronized(pendingReps) { pendingReps.add(rep) }
    }

    fun clearPendingReps() {
        synchronized(pendingReps) { pendingReps.clear() }
    }

    /**
     * Przekształca powtórzenia danej serii czekające jeszcze w kolejce (np. po
     * korekcie ciężaru w trakcie serii - trzeba poprawić loadKg/moc/1RM zanim
     * polecą na serwer).
     */
    fun updatePendingReps(sessionId: Int, setNumber: Int, transform: (RepResultDto) -> RepResultDto) {
        synchronized(pendingReps) {
            for (i in pendingReps.indices) {
                val rep = pendingReps[i]
                if (rep.sessionId == sessionId && rep.setNumber == setNumber) {
                    pendingReps[i] = transform(rep)
                }
            }
        }
    }

    /**
     * Startuje sesję live na serwerze i zapisuje przydzielone serverSessionId
     * w lokalnej sesji Room. Zwraca null gdy offline / błąd (sesja zostanie
     * zsynchronizowana później przez syncSession/SessionSyncWorker).
     */
    suspend fun startLiveSession(localSessionId: Long, planId: Int?, athleteId: Int?): Int? {
        return try {
            val response = api.startLiveSession(
                StartLiveSessionRequest(athleteId = athleteId, planId = planId, notes = null)
            )
            val servId = if (response.isSuccessful) response.body()?.id else null
            if (servId == null) {
                Log.w(TAG, "startLiveSession: serwer odrzucił żądanie (code=${response.code()})")
            } else if (localSessionId > 0) {
                workoutRepository.updateServerSessionId(localSessionId, servId)
            }
            servId
        } catch (e: Exception) {
            Log.w(TAG, "startLiveSession: brak połączenia z serwerem", e)
            null
        }
    }

    /**
     * Wysyła zakolejkowane powtórzenia do sesji live (opcjonalnie zamykając ją
     * przez finishedAt). Zwraca true przy sukcesie; przy błędzie powtórzenia
     * pozostają w kolejce (i tak są też zapisane w Room).
     */
    suspend fun flushPendingReps(serverSessionId: Int, finishedAt: String? = null): Boolean {
        val toSend = synchronized(pendingReps) { pendingReps.toList() }
        if (toSend.isEmpty() && finishedAt == null) return true
        return try {
            val response = api.appendReps(serverSessionId, AppendRepsRequest(reps = toSend, finishedAt = finishedAt))
            if (response.isSuccessful) {
                synchronized(pendingReps) { pendingReps.removeAll(toSend) }
                true
            } else {
                Log.w(TAG, "flushPendingReps: serwer zwrócił code=${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "flushPendingReps: błąd sieci", e)
            false
        }
    }

    /**
     * Znajduje ćwiczenie po nazwie wśród ćwiczeń serwerowych; jeśli nie istnieje,
     * a jesteśmy online, tworzy je przez POST /api/exercises. Zwraca null gdy
     * offline - powtórzenia zostają wtedy tylko lokalnie w Room.
     */
    suspend fun resolveServerExercise(name: String, category: String?): ExerciseDto? {
        exerciseCacheMutex.withLock {
            if (serverExerciseCache.isEmpty()) {
                try {
                    val fetched = api.getExercises().body() ?: emptyList()
                    serverExerciseCache.addAll(fetched.filter { it.id > 0 })
                } catch (e: Exception) {
                    Log.w(TAG, "resolveServerExercise: nie można pobrać listy ćwiczeń", e)
                }
            }
            serverExerciseCache.find { it.name.equals(name, ignoreCase = true) }?.let { return it }
        }
        return try {
            val response = api.createExercise(
                CreateExerciseRequest(name = name, category = category, mvt = null, description = null)
            )
            val created = response.body()
            if (created != null) {
                exerciseCacheMutex.withLock { serverExerciseCache.add(created) }
            } else {
                Log.w(TAG, "resolveServerExercise: serwer nie utworzył ćwiczenia '$name' (code=${response.code()})")
            }
            created
        } catch (e: Exception) {
            Log.w(TAG, "resolveServerExercise: błąd sieci przy tworzeniu '$name'", e)
            null
        }
    }

    /**
     * Pełna synchronizacja zakończonej sesji zapisanej offline: buduje
     * CreateSessionRequest z danych Room i po sukcesie zapisuje serverSessionId.
     */
    suspend fun syncSession(localSessionId: Long): Boolean {
        val session = workoutRepository.getSession(localSessionId)
        if (session == null) {
            Log.w(TAG, "syncSession: brak lokalnej sesji id=$localSessionId")
            return false
        }
        if (session.serverSessionId != null) return true

        val sets = workoutRepository.getSetsForSessionOnce(localSessionId)
        val allReps = mutableListOf<RepResultDto>()
        var hadLocalReps = false

        for (set in sets) {
            val reps = workoutRepository.getRepsForSetOnce(set.id).filter { !it.isDeleted }
            if (reps.isEmpty()) continue
            hadLocalReps = true

            val exercise = exerciseRepository.getById(set.exerciseId)
            val serverEx = resolveServerExercise(exercise?.name ?: "Ćwiczenie", exercise?.category)
            if (serverEx == null) {
                Log.w(TAG, "syncSession: nie można dopasować ćwiczenia '${exercise?.name}' - przerwano")
                return false
            }

            reps.forEach { rep ->
                // Kolumna maxVelocityMs historycznie przechowuje mean velocity;
                // peakVelocityMs == 0 oznacza rekord sprzed peak-velocity.
                val mean = rep.maxVelocityMs
                val peak = if (rep.peakVelocityMs > 0f) rep.peakVelocityMs else rep.maxVelocityMs
                allReps.add(
                    RepResultDto(
                        id = null,
                        sessionId = null,
                        exerciseId = serverEx.id,
                        setNumber = set.setNumber,
                        repNumber = rep.repNumber,
                        meanVelocity = mean.toDouble(),
                        peakVelocity = peak.toDouble(),
                        loadKg = set.actualLoadKg.toDouble(),
                        powerWatts = rep.powerW.toDouble(),
                        estimated1rm = estimate1RM.estimate(set.actualLoadKg, mean, reps.size),
                        timestamp = null
                    )
                )
            }
        }

        if (hadLocalReps && allReps.isEmpty()) return false

        return try {
            val response = api.createSession(
                CreateSessionRequest(
                    athleteId = session.athleteServerId ?: 0,
                    planId = null,
                    calendarEntryId = null,
                    notes = session.notes,
                    reps = allReps
                )
            )
            val servId = if (response.isSuccessful) response.body()?.id else null
            if (servId != null) {
                workoutRepository.updateServerSessionId(localSessionId, servId)
                true
            } else {
                Log.w(TAG, "syncSession: serwer odrzucił sesję id=$localSessionId (code=${response.code()})")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncSession: błąd sieci dla sesji id=$localSessionId", e)
            false
        }
    }

    /** Synchronizuje wszystkie zakończone sesje bez serverSessionId. */
    suspend fun syncAllUnsynced(): SyncResult {
        val unsynced = workoutRepository.getUnsyncedSessions()
        var ok = 0
        for (session in unsynced) {
            if (syncSession(session.id)) ok++
        }
        if (unsynced.isNotEmpty()) {
            Log.i(TAG, "syncAllUnsynced: zsynchronizowano $ok/${unsynced.size} sesji")
        }
        return SyncResult(synced = ok, total = unsynced.size)
    }
}
