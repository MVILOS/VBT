package com.vbt.app.domain.model

import androidx.compose.ui.graphics.Color

enum class VelocityZone(
    val label: String,
    val minVelocity: Float,
    val maxVelocity: Float,
    val color: Color
) {
    ABSOLUTE_STRENGTH("Strength", 0.0f, 0.35f, Color(0xFFFF0000)),
    STRENGTH_SPEED("Str-Speed", 0.35f, 0.6f, Color(0xFFFF8800)),
    POWER("Power", 0.6f, 0.9f, Color(0xFFFFCC00)),
    SPEED_STRENGTH("Spd-Str", 0.9f, 1.15f, Color(0xFF00CC00)),
    SPEED("Speed", 1.15f, 1.5f, Color(0xFF0088FF)),
    BALLISTIC("Ballistic", 1.5f, 5.0f, Color(0xFF8800FF));

    companion object {
        fun fromVelocity(velocity: Float): VelocityZone {
            return entries.lastOrNull { velocity >= it.minVelocity } ?: ABSOLUTE_STRENGTH
        }
    }
}
