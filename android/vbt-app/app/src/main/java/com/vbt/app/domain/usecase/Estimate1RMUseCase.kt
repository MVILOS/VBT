package com.vbt.app.domain.usecase

import javax.inject.Inject

class Estimate1RMUseCase @Inject constructor() {

    companion object {
        // Klasyczna relacja mean velocity -> %1RM wg González-Badillo
        // (przysiad / wyciskanie leżąc). Punkty posortowane malejąco po prędkości;
        // pomiędzy punktami stosowana jest interpolacja liniowa.
        //   >= 1.00 m/s -> ~70% 1RM
        //      0.75 m/s -> ~80% 1RM
        //      0.50 m/s -> ~90% 1RM
        //   <= 0.30 m/s -> ~100% 1RM
        private val VELOCITY_TO_PERCENT_1RM = listOf(
            1.00f to 70f,
            0.75f to 80f,
            0.50f to 90f,
            0.30f to 100f
        )
    }

    /**
     * Estymacja 1RM metodą VBT: z ciężaru i średniej prędkości koncentrycznej
     * powtórzenia. Zwraca null gdy dane nie pozwalają na sensowną estymację
     * (brak ciężaru / prędkość poza fizjologicznym zakresem pomiaru).
     */
    fun estimateFromVelocity(loadKg: Float, meanVelocityMs: Float): Double? {
        if (loadKg <= 0f || meanVelocityMs <= 0f) return null
        val percent = percentOf1RM(meanVelocityMs)
        return (loadKg / (percent / 100.0))
    }

    /** %1RM odpowiadający średniej prędkości (interpolacja liniowa, z klamrą na końcach). */
    fun percentOf1RM(meanVelocityMs: Float): Double {
        val points = VELOCITY_TO_PERCENT_1RM
        if (meanVelocityMs >= points.first().first) return points.first().second.toDouble()
        if (meanVelocityMs <= points.last().first) return points.last().second.toDouble()
        for (i in 0 until points.size - 1) {
            val (vHigh, pctHigh) = points[i]
            val (vLow, pctLow) = points[i + 1]
            if (meanVelocityMs <= vHigh && meanVelocityMs >= vLow) {
                val t = (meanVelocityMs - vLow) / (vHigh - vLow)
                return (pctLow + t * (pctHigh - pctLow)).toDouble()
            }
        }
        return points.last().second.toDouble()
    }

    /**
     * Fallback bez danych prędkości: formuła Epleya z LICZBĄ POWTÓRZEŃ W SERII
     * (nie z numerem powtórzenia!).
     */
    fun estimateEpley(loadKg: Float, repsInSet: Int): Double {
        if (loadKg <= 0f || repsInSet <= 0) return 0.0
        return loadKg * (1 + repsInSet / 30.0)
    }

    /**
     * Preferuje estymację z prędkości; gdy niedostępna, spada do Epleya.
     */
    fun estimate(loadKg: Float, meanVelocityMs: Float?, repsInSet: Int): Double? {
        meanVelocityMs?.let { v ->
            estimateFromVelocity(loadKg, v)?.let { return it }
        }
        val epley = estimateEpley(loadKg, repsInSet)
        return if (epley > 0.0) epley else null
    }

    /**
     * Estymacja z profilu load-velocity (regresja liniowa po wielu punktach
     * (ciężar, prędkość) i ekstrapolacja do MVT ćwiczenia). Używana gdy dostępne
     * są co najmniej dwa punkty o różnych ciężarach.
     */
    fun estimateFromProfile(
        dataPoints: List<Pair<Float, Float>>, // (loadKg, velocityMs)
        mvt: Float
    ): Float? {
        if (dataPoints.size < 2) return null

        val n = dataPoints.size.toDouble()
        val sumX = dataPoints.sumOf { it.first.toDouble() }
        val sumY = dataPoints.sumOf { it.second.toDouble() }
        val sumXY = dataPoints.sumOf { (it.first * it.second).toDouble() }
        val sumX2 = dataPoints.sumOf { (it.first * it.first).toDouble() }

        val denominator = n * sumX2 - sumX * sumX
        if (denominator == 0.0) return null

        val slope = (n * sumXY - sumX * sumY) / denominator
        val intercept = (sumY - slope * sumX) / n

        if (slope >= 0) return null

        val estimated1RM = ((mvt - intercept) / slope).toFloat()
        return if (estimated1RM > 0) estimated1RM else null
    }
}
