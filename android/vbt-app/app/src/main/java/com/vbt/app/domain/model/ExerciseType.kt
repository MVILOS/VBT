package com.vbt.app.domain.model

enum class ExerciseType(
    val displayName: String,
    val category: String,
    val defaultMinLiftVelocity: Float,
    val defaultEndLiftVelocity: Float,
    val defaultMinRepDistance: Float,
    val mvt: Float // Minimum Velocity Threshold for 1RM estimation
) {
    BACK_SQUAT("Back Squat", "squat", 0.08f, 0.04f, 0.20f, 0.30f),
    FRONT_SQUAT("Front Squat", "squat", 0.08f, 0.04f, 0.20f, 0.30f),
    BENCH_PRESS("Bench Press", "bench", 0.12f, 0.05f, 0.15f, 0.17f),
    OVERHEAD_PRESS("Overhead Press", "ohp", 0.10f, 0.05f, 0.20f, 0.20f),
    DEADLIFT("Deadlift", "deadlift", 0.08f, 0.04f, 0.30f, 0.15f),
    ROMANIAN_DEADLIFT("Romanian Deadlift", "deadlift", 0.06f, 0.03f, 0.20f, 0.15f),
    CUSTOM("Custom", "custom", 0.10f, 0.05f, 0.10f, 0.25f)
}
