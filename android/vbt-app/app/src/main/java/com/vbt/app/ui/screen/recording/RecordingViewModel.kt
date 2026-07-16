package com.vbt.app.ui.screen.recording

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.vbt.app.data.ble.BleConnectionState
import com.vbt.app.data.ble.HeartRateManager
import com.vbt.app.data.ble.VbtBleManager
import com.vbt.app.data.local.PreferencesManager
import com.vbt.app.domain.usecase.CalculatePowerUseCase
import com.vbt.app.recording.GallerySaver
import com.vbt.app.recording.OverlayMetric
import com.vbt.app.recording.OverlaySnapshot
import com.vbt.app.recording.OverlayTimeline
import com.vbt.app.recording.SetRecorder
import com.vbt.app.recording.VideoOverlayProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Faza ekranu nagrywania - steruje tym, co widać (podgląd / postęp / wynik). */
sealed interface RecordingPhase {
    data object Idle : RecordingPhase
    data object Recording : RecordingPhase
    data class Processing(val progress: Float) : RecordingPhase
    data class Saved(val uri: Uri) : RecordingPhase
    data class Error(val message: String) : RecordingPhase
}

data class RecordingUiState(
    val exerciseName: String = "",
    val loadKg: Float = 0f,
    val athleteName: String? = null,
    val connectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val liveVelocity: Float = 0f,
    val repCount: Int = 0,
    val heartRate: Int? = null,
    val selectedMetrics: List<OverlayMetric> = OverlayMetric.DEFAULT.toList(),
    // Metryki ostatniego zaliczonego powtórzenia (do podglądu kafelków na żywo)
    val lastRepMeanVelocityMs: Float = 0f,
    val lastRepPeakVelocityMs: Float = 0f,
    val lastRepPowerW: Float = 0f,
    val lastRepDistanceM: Float = 0f,
    val phase: RecordingPhase = RecordingPhase.Idle
) {
    val isRecording: Boolean get() = phase is RecordingPhase.Recording
}

@OptIn(FlowPreview::class)
@UnstableApi
@HiltViewModel
class RecordingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val bleManager: VbtBleManager,
    private val heartRateManager: HeartRateManager,
    private val calculatePower: CalculatePowerUseCase,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    companion object {
        private const val TAG = "RecordingViewModel"
        // Próbkowanie prędkości live do osi nakładki (~15 Hz) - dość gęste dla płynnego
        // paska, a nie generuje tysięcy identycznych klatek przy wypalaniu.
        private const val LIVE_SAMPLE_MS = 66L
    }

    val recorder = SetRecorder(appContext)
    private val processor = VideoOverlayProcessor(appContext)
    private val gallerySaver = GallerySaver(appContext)

    private val _uiState = MutableStateFlow(
        RecordingUiState(
            exerciseName = savedStateHandle.get<String>("exercise")?.takeIf { it.isNotBlank() } ?: "Pomiar",
            loadKg = savedStateHandle.get<String>("load")?.toFloatOrNull() ?: 0f,
            athleteName = savedStateHandle.get<String>("athlete")?.takeIf { it.isNotBlank() && it != "You" }
        )
    )
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    // Oś czasu nakładki budowana podczas nagrywania (dostęp tylko z Main - collectory
    // BLE i eventy kamery lecą przez viewModelScope/Main, więc bez synchronizacji).
    private val snapshots = mutableListOf<OverlaySnapshot>()
    private var recordStartElapsedMs: Long = 0L
    private var rawFile: File? = null

    // Bieżące metryki (składane w kolejny snapshot przy każdym zdarzeniu)
    private var repCount = 0
    private var lastRepMean = 0f
    private var lastRepPeak = 0f
    private var lastRepPower = 0f
    private var lastRepDist = 0f

    private var collectJobs = mutableListOf<Job>()

    init {
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
        viewModelScope.launch {
            bleManager.liveVelocity.collect { v ->
                _uiState.update { it.copy(liveVelocity = v) }
            }
        }
        viewModelScope.launch {
            heartRateManager.heartRate.collect { hr ->
                _uiState.update { it.copy(heartRate = hr) }
            }
        }
        viewModelScope.launch {
            preferencesManager.getOverlayMetricKeys().collect { keys ->
                _uiState.update { it.copy(selectedMetrics = OverlayMetric.fromKeysOrDefault(keys)) }
            }
        }
    }

    fun startRecording() {
        if (_uiState.value.isRecording) return
        val output = File(appContext.cacheDir, "vbt_raw_${System.currentTimeMillis()}.mp4")
        rawFile = output
        resetTimeline()

        recorder.start(
            outputFile = output,
            onStarted = {
                recordStartElapsedMs = SystemClock.elapsedRealtime()
                _uiState.update { it.copy(phase = RecordingPhase.Recording, repCount = 0) }
                appendSnapshot() // klatka startowa (t=0), żeby nakładka była od pierwszej sekundy
                beginCollectingEvents()
            },
            onFinalized = { error ->
                stopCollectingEvents()
                if (error != null) {
                    _uiState.update { it.copy(phase = RecordingPhase.Error(error)) }
                    output.delete()
                } else {
                    processRecording(output)
                }
            }
        )
    }

    fun stopRecording() {
        if (!_uiState.value.isRecording) return
        recorder.stop()
    }

    // ---- Zbieranie zdarzeń do osi czasu ----

    private fun beginCollectingEvents() {
        collectJobs += viewModelScope.launch {
            bleManager.liveVelocity.sample(LIVE_SAMPLE_MS).collect {
                if (_uiState.value.isRecording) appendSnapshot()
            }
        }
        collectJobs += viewModelScope.launch {
            bleManager.repResult.collect { rep ->
                if (!_uiState.value.isRecording) return@collect
                repCount += 1
                lastRepMean = rep.meanVelocityMs
                lastRepPeak = if (rep.peakVelocityMs > 0f) rep.peakVelocityMs else rep.meanVelocityMs
                lastRepPower = calculatePower.calculatePeakPower(_uiState.value.loadKg, lastRepPeak)
                lastRepDist = rep.distanceM
                _uiState.update {
                    it.copy(
                        repCount = repCount,
                        lastRepMeanVelocityMs = lastRepMean,
                        lastRepPeakVelocityMs = lastRepPeak,
                        lastRepPowerW = lastRepPower,
                        lastRepDistanceM = lastRepDist
                    )
                }
                appendSnapshot()
            }
        }
    }

    private fun stopCollectingEvents() {
        collectJobs.forEach { it.cancel() }
        collectJobs.clear()
    }

    private fun appendSnapshot() {
        val s = _uiState.value
        snapshots += OverlaySnapshot(
            timeMs = if (recordStartElapsedMs == 0L) 0L else SystemClock.elapsedRealtime() - recordStartElapsedMs,
            exerciseName = s.exerciseName,
            athleteName = s.athleteName,
            loadKg = s.loadKg,
            liveVelocityMs = s.liveVelocity,
            repCount = repCount,
            lastRepMeanVelocityMs = lastRepMean,
            lastRepPeakVelocityMs = lastRepPeak,
            lastRepPowerW = lastRepPower,
            heartRate = s.heartRate
        )
    }

    private fun resetTimeline() {
        snapshots.clear()
        repCount = 0
        lastRepMean = 0f
        lastRepPeak = 0f
        lastRepPower = 0f
        recordStartElapsedMs = 0L
    }

    // ---- Wypalanie + zapis ----

    private fun processRecording(raw: File) {
        val (w, h) = readFrameSize(raw)
        val timeline = OverlayTimeline(snapshots.toList())
        val output = File(appContext.cacheDir, "vbt_overlay_${System.currentTimeMillis()}.mp4")

        _uiState.update { it.copy(phase = RecordingPhase.Processing(0f)) }

        viewModelScope.launch {
            try {
                processor.process(raw, output, timeline, w, h).collectLatest { progress ->
                    _uiState.update { it.copy(phase = RecordingPhase.Processing(progress)) }
                }
                val displayName = "VBT_${_uiState.value.exerciseName.replace(Regex("[^A-Za-z0-9]"), "_")}_${System.currentTimeMillis()}.mp4"
                val uri = gallerySaver.save(output, displayName)
                _uiState.update { it.copy(phase = RecordingPhase.Saved(uri)) }
            } catch (e: Exception) {
                Log.e(TAG, "Wypalanie/zapis nieudane", e)
                _uiState.update { it.copy(phase = RecordingPhase.Error("Nie udało się przetworzyć nagrania: ${e.message}")) }
            } finally {
                raw.delete()
                output.delete()
            }
        }
    }

    /** Wymiary klatki po uwzględnieniu rotacji (Media3 komponuje nakładkę na wyprostowanym obrazie). */
    private fun readFrameSize(file: File): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) h to w else w to h
        } catch (e: Exception) {
            Log.w(TAG, "Nie udało się odczytać wymiarów klatki, domyślnie 1080x1920", e)
            1080 to 1920
        } finally {
            retriever.release()
        }
    }

    fun reconnectBle() = bleManager.reconnect()

    fun resetAfterSave() {
        _uiState.update { it.copy(phase = RecordingPhase.Idle) }
    }

    override fun onCleared() {
        super.onCleared()
        if (_uiState.value.isRecording) recorder.stop()
        stopCollectingEvents()
    }
}
