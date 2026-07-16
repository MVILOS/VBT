package com.vbt.app.recording

import com.vbt.app.domain.model.VelocityZone

/**
 * Pojedynczy stan parametrów nakładki w danym momencie nagrania.
 *
 * [timeMs] to czas względem startu nagrania (0 = pierwsza klatka), a nie zegar
 * czujnika - dzięki temu wypalanie nie wymaga mapowania zegara urządzenia BLE.
 * Zdarzenia z BLE (nowe powtórzenie, próbka prędkości live) są znakowane
 * `SystemClock.elapsedRealtime() - t0` w momencie odebrania w aplikacji.
 */
data class OverlaySnapshot(
    val timeMs: Long,
    val exerciseName: String,
    val athleteName: String?,
    val loadKg: Float,
    // Prędkość "na żywo" z czujnika (płynny pasek podczas ruchu)
    val liveVelocityMs: Float,
    // Metryki ostatniego ZALICZONEGO powtórzenia (0 dopóki nie ma powtórzeń)
    val repCount: Int,
    val lastRepMeanVelocityMs: Float,
    val lastRepPeakVelocityMs: Float,
    val lastRepPowerW: Float,
    val heartRate: Int?
) {
    val zone: VelocityZone
        get() = VelocityZone.fromVelocity(
            if (lastRepPeakVelocityMs > 0f) lastRepPeakVelocityMs else liveVelocityMs
        )
}

/**
 * Posortowana po czasie oś zdarzeń nakładki zebrana podczas nagrywania.
 *
 * Przy wypalaniu dla każdej klatki wideo o czasie prezentacji `timeUs` bierzemy
 * najświeższy snapshot z `timeMs <= timeUs/1000` ([snapshotAt]). Snapshoty muszą
 * być dodawane w kolejności rosnącego czasu (tak je produkuje RecordingViewModel).
 */
class OverlayTimeline(
    private val snapshots: List<OverlaySnapshot>
) {
    val isEmpty: Boolean get() = snapshots.isEmpty()

    /** Snapshot obowiązujący w czasie [timeUs] (mikrosekundy od startu nagrania). */
    fun snapshotAt(timeUs: Long): OverlaySnapshot? {
        if (snapshots.isEmpty()) return null
        val timeMs = timeUs / 1000
        // Zdarzeń jest niewiele (dziesiątki-setki na serię), więc liniowe
        // wyszukiwanie ostatniego <= timeMs jest w zupełności wystarczające.
        var result = snapshots.first()
        for (s in snapshots) {
            if (s.timeMs <= timeMs) result = s else break
        }
        return result
    }
}
