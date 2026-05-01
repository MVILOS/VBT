package com.vbt.app.domain.usecase

import javax.inject.Inject

class Estimate1RMUseCase @Inject constructor() {
    fun estimate(
        dataPoints: List<Pair<Float, Float>>, // (loadKg, peakVelocityMs)
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
