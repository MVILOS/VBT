package com.vbt.app.recording

import android.graphics.Bitmap
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay

/**
 * Nakładka Media3 wypalana w wideo podczas eksportu przez [VideoOverlayProcessor].
 * Dla każdej klatki (o czasie [presentationTimeUs]) zwraca Bitmapę z parametrami
 * obowiązującymi w tym momencie nagrania.
 *
 * Bitmapa ma rozmiar klatki wyjściowej, więc przy domyślnych ustawieniach nakładki
 * (wyśrodkowanie, skala 1:1) pokrywa całą klatkę.
 *
 * Cache po instancji snapshotu: [OverlayTimeline.snapshotAt] zwraca tę samą instancję
 * dopóki nie przekroczymy granicy zdarzenia, więc nie renderujemy w kółko identycznej
 * grafiki (Media3 i tak wtedy nie przesyła tekstury ponownie).
 */
@UnstableApi
class MetricsOverlay(
    private val timeline: OverlayTimeline,
    private val renderer: OverlayRenderer,
    private val metrics: List<OverlayMetric>,
    private val width: Int,
    private val height: Int
) : BitmapOverlay() {

    private var lastSnapshot: OverlaySnapshot? = null
    private var cachedBitmap: Bitmap? = null

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val snapshot = timeline.snapshotAt(presentationTimeUs)
        val cached = cachedBitmap
        if (cached != null && snapshot === lastSnapshot) return cached

        val bmp = renderer.render(snapshot, metrics, width, height)
        lastSnapshot = snapshot
        cachedBitmap = bmp
        return bmp
    }
}
