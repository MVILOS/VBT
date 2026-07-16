package com.vbt.app.recording

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

/**
 * Wypala nakładkę z parametrami VBT w surowy plik wideo, korzystając z Media3
 * Transformer + [OverlayEffect]. Wejście (surowe MP4 z CameraX) pozostaje nietknięte;
 * produkujemy nowy plik z wtopioną grafiką.
 *
 * [process] zwraca [Flow] postępu 0f..1f; normalne zakończenie strumienia = sukces
 * (plik wyjściowy gotowy), a błąd eksportu jest rzucany jako wyjątek strumienia.
 *
 * Uwaga wątkowa: Transformer wymaga Loopera i musi być tworzony/sterowany z jednego
 * wątku - wołamy go z głównego wątku (RecordingViewModel działa w viewModelScope/Main).
 */
@UnstableApi
class VideoOverlayProcessor(private val context: Context) {

    fun process(
        inputFile: File,
        outputFile: File,
        timeline: OverlayTimeline,
        frameWidth: Int,
        frameHeight: Int
    ): Flow<Float> = callbackFlow {
        val overlay = MetricsOverlay(timeline, OverlayRenderer(), frameWidth, frameHeight)
        val overlayEffect = OverlayEffect(ImmutableList.of(overlay))
        val effects = Effects(/* audioProcessors = */ emptyList(), /* videoEffects = */ listOf(overlayEffect))

        val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(inputFile)))
            .setEffects(effects)
            .build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    trySend(1f)
                    close()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    close(exportException)
                }
            })
            .build()

        transformer.start(editedItem, outputFile.absolutePath)

        // Odpytywanie postępu w osobnej korutynie; getProgress musi być wołane na tym
        // samym wątku co start (callbackFlow producent działa tu na Main - patrz VM).
        val poller = launch {
            val progressHolder = ProgressHolder()
            while (true) {
                if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    trySend(progressHolder.progress / 100f)
                }
                delay(200)
            }
        }

        awaitClose {
            poller.cancel()
            transformer.cancel()
        }
    }
}
