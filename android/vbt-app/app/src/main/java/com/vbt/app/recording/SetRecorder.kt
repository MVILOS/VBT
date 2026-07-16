package com.vbt.app.recording

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Opakowanie CameraX do nagrywania podejścia (wideo-only, bez audio - stąd brak
 * potrzeby uprawnienia RECORD_AUDIO). [videoCapture] wiąże się z lifecycle w
 * RecordingScreen razem z podglądem; ta klasa steruje samym nagrywaniem.
 */
class SetRecorder(
    private val context: Context,
    quality: RecordingQuality = RecordingQuality.DEFAULT
) {

    // Wybrana jakość z Ustawień; FallbackStrategy schodzi niżej, gdy dany moduł
    // aparatu nie wspiera żądanej rozdzielczości.
    private val recorder = Recorder.Builder()
        .setQualitySelector(
            QualitySelector.from(
                quality.toCameraQuality(),
                FallbackStrategy.lowerQualityThan(quality.toCameraQuality())
            )
        )
        .build()

    val videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(recorder)

    private var activeRecording: Recording? = null

    val isRecording: Boolean get() = activeRecording != null

    /**
     * Rozpoczyna nagrywanie do [outputFile]. [onStarted] woła się, gdy CameraX
     * faktycznie ruszył (moment na ustawienie t0 osi czasu nakładki); [onFinalized]
     * dostaje null przy sukcesie lub komunikat błędu.
     */
    @SuppressLint("MissingPermission") // CAMERA sprawdzane w RecordingScreen przed wejściem
    fun start(
        outputFile: File,
        onStarted: () -> Unit,
        onFinalized: (error: String?) -> Unit
    ) {
        val options = FileOutputOptions.Builder(outputFile).build()
        activeRecording = videoCapture.output
            .prepareRecording(context, options)
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> onStarted()
                    is VideoRecordEvent.Finalize -> {
                        activeRecording = null
                        onFinalized(if (event.hasError()) "Błąd nagrywania (kod ${event.error})" else null)
                    }
                }
            }
    }

    fun stop() {
        activeRecording?.stop()
        activeRecording = null
    }
}
