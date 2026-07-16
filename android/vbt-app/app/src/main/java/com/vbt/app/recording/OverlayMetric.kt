package com.vbt.app.recording

/**
 * Metryki, które użytkownik może włączyć/wyłączyć na nakładce nagrywania
 * (Ustawienia). Kolejność enuma = kolejność renderowania kafelków. [key] jest
 * stabilny i trafia do DataStore (PreferencesManager.getOverlayMetricKeys).
 */
enum class OverlayMetric(val key: String, val label: String, val unit: String) {
    VMAX("vmax", "Vmax", "m/s"),
    AVG("avg", "Avg", "m/s"),
    POWER("power", "Moc", "W"),
    EAI("eai", "EAI", ""),
    ROM("rom", "ROM", "cm");

    /** Surowa wartość metryki z ostatniego zaliczonego powtórzenia w [s]. */
    fun value(s: OverlaySnapshot): Float = when (this) {
        VMAX -> s.lastRepPeakVelocityMs
        AVG -> s.lastRepMeanVelocityMs
        POWER -> s.lastRepPowerW
        // PLACEHOLDER: Elastic Acceleration Index jako stosunek Vmax/Vavg (wskaźnik
        // "elastycznego" przyspieszenia). Do potwierdzenia właściwego wzoru z użytkownikiem
        // - zmiana dotyczy tylko tej gałęzi.
        EAI -> if (s.lastRepMeanVelocityMs > 0f) s.lastRepPeakVelocityMs / s.lastRepMeanVelocityMs else 0f
        ROM -> s.lastRepDistanceM * 100f // metry -> centymetry
    }

    /** Sformatowana wartość do wyświetlenia (bez jednostki). */
    fun format(v: Float): String = when (this) {
        VMAX, AVG, EAI -> String.format("%.2f", v)
        POWER, ROM -> v.toInt().toString()
    }

    /** Czy metryka jest "prędkościowa" - kafelek koloruje się kolorem strefy. */
    val isVelocity: Boolean get() = this == VMAX || this == AVG

    companion object {
        val DEFAULT: Set<OverlayMetric> = setOf(VMAX, AVG, POWER)

        /**
         * Rozwija zapisane klucze na uporządkowaną (wg enuma) listę metryk.
         * [keys] == null (nigdy nie ustawiono) -> zestaw domyślny.
         */
        fun fromKeysOrDefault(keys: Set<String>?): List<OverlayMetric> {
            if (keys == null) return entries.filter { it in DEFAULT }
            return entries.filter { it.key in keys }
        }
    }
}
