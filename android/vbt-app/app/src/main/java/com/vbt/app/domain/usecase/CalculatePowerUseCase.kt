package com.vbt.app.domain.usecase

import javax.inject.Inject

class CalculatePowerUseCase @Inject constructor() {
    fun calculateMeanPower(loadKg: Float, distanceM: Float, durationMs: Int): Float {
        if (durationMs <= 0) return 0f
        val force = loadKg * 9.81f
        val meanVelocity = distanceM / (durationMs / 1000f)
        return force * meanVelocity
    }

    fun calculatePeakPower(loadKg: Float, peakVelocityMs: Float): Float {
        val force = loadKg * 9.81f
        return force * peakVelocityMs
    }
}
