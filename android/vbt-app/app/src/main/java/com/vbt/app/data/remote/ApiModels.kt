package com.vbt.app.data.remote

import com.google.gson.annotations.SerializedName

// Authentication
data class LoginRequest(
    val username: String? = null,
    val email: String? = null,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String,
    val user: UserDto
)

// User/Athlete
data class UserDto(
    val id: Int,
    val email: String? = null,
    val username: String,
    val role: String,
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("coach_id")
    val coachId: Int?
)

// Exercise
data class ExerciseDto(
    val id: Int,
    val name: String,
    val category: String?,
    val mvt: Float?,
    val description: String?
)

data class CreateExerciseRequest(
    val name: String,
    val category: String?,
    val mvt: Float?,
    val description: String?
)

// Training Plan
data class TrainingPlanDto(
    val id: Int,
    val name: String,
    val description: String?,
    @SerializedName("owner_id")
    val ownerId: Int?,
    @SerializedName("assigned_to")
    val assignedTo: Int?,
    @SerializedName("is_template")
    val isTemplate: Boolean,
    val exercises: List<PlanExerciseDto>
)

data class CreatePlanRequest(
    val name: String,
    val description: String?,
    @SerializedName("assigned_to")
    val assignedTo: Int?,
    @SerializedName("is_template")
    val isTemplate: Boolean,
    val exercises: List<PlanExerciseDto>
)

data class PlanExerciseDto(
    val id: Int?,
    @SerializedName("exercise_id")
    val exerciseId: Int,
    @SerializedName("order_index")
    val orderIndex: Int,
    val notes: String?,
    val sets: List<PlanSetDto>,
    val exercise: ExerciseDto?
)

data class PlanSetDto(
    val id: Int?,
    @SerializedName("set_number")
    val setNumber: Int,
    val reps: Int,
    @SerializedName("load_kg")
    val loadKg: Float,
    @SerializedName("load_percent_1rm")
    val loadPercent1rm: Float?,
    @SerializedName("target_velocity_min")
    val targetVelocityMin: Float?,
    @SerializedName("target_velocity_max")
    val targetVelocityMax: Float?,
    @SerializedName("rest_seconds")
    val restSeconds: Int
)

// Calendar
data class CalendarEntryDto(
    val id: Int,
    @SerializedName("athlete_id")
    val athleteId: Int,
    @SerializedName("plan_id")
    val planId: Int?,
    val date: String,
    @SerializedName("time_slot")
    val timeSlot: String?,
    val title: String,
    val notes: String?,
    val status: String
)

data class CreateCalendarEntryRequest(
    @SerializedName("athlete_id")
    val athleteId: Int?,
    @SerializedName("plan_id")
    val planId: Int?,
    val date: String,
    @SerializedName("time_slot")
    val timeSlot: String?,
    val title: String,
    val notes: String?
)

data class UpdateCalendarEntryRequest(
    val title: String? = null,
    val notes: String? = null,
    val date: String? = null,
    @SerializedName("time_slot")
    val timeSlot: String? = null,
    val status: String? = null,
    @SerializedName("plan_id")
    val planId: Int? = null
)

// Workout Session
data class WorkoutSessionDto(
    val id: Int,
    @SerializedName("athlete_id")
    val athleteId: Int,
    @SerializedName("plan_id")
    val planId: Int?,
    @SerializedName("calendar_entry_id")
    val calendarEntryId: Int?,
    @SerializedName("started_at")
    val startedAt: String,
    @SerializedName("finished_at")
    val finishedAt: String?,
    @SerializedName("duration_seconds")
    val durationSeconds: Int?,
    val notes: String?,
    val reps: List<RepResultDto>?
)

data class CreateSessionRequest(
    @SerializedName("athlete_id")
    val athleteId: Int,
    @SerializedName("plan_id")
    val planId: Int?,
    @SerializedName("calendar_entry_id")
    val calendarEntryId: Int?,
    val notes: String?,
    val reps: List<RepResultDto>
)

data class StartLiveSessionRequest(
    @SerializedName("athlete_id")
    val athleteId: Int?,
    @SerializedName("plan_id")
    val planId: Int?,
    val notes: String?
)

data class AppendRepsRequest(
    val reps: List<RepResultDto>,
    @SerializedName("finished_at")
    val finishedAt: String? = null
)

// Rep Result
data class RepResultDto(
    val id: Int?,
    @SerializedName("session_id")
    val sessionId: Int?,
    @SerializedName("exercise_id")
    val exerciseId: Int,
    @SerializedName("set_number")
    val setNumber: Int,
    @SerializedName("rep_number")
    val repNumber: Int,
    @SerializedName("mean_velocity")
    val meanVelocity: Double,
    @SerializedName("peak_velocity")
    val peakVelocity: Double,
    @SerializedName("load_kg")
    val loadKg: Double,
    @SerializedName("power_watts")
    val powerWatts: Double?,
    @SerializedName("estimated_1rm")
    val estimated1rm: Double?,
    val timestamp: String?
)

data class UpdateRepRequest(
    @SerializedName("load_kg")
    val loadKg: Double? = null,
    @SerializedName("set_number")
    val setNumber: Int? = null,
    @SerializedName("rep_number")
    val repNumber: Int? = null,
    @SerializedName("estimated_1rm")
    val estimated1rm: Double? = null
)

// Velocity Trace
data class VelocityPoint(
    @SerializedName("timestamp_ms")
    val timestampMs: Long,
    @SerializedName("velocity_ms")
    val velocityMs: Float
)

data class VelocityTraceRequest(
    val points: List<VelocityPoint>
)

// Analytics — server returns flat lists
data class VelocityTrendPointDto(
    val date: String,
    @SerializedName("mean_velocity") val meanVelocity: Double,
    @SerializedName("load_kg") val loadKg: Double,
    @SerializedName("exercise_name") val exerciseName: String
)

data class OneRmProgressPointDto(
    val date: String,
    @SerializedName("estimated_1rm") val estimated1rm: Double,
    @SerializedName("load_kg") val loadKg: Double
)

// Analytics — Fatigue Index
data class FatigueSetDto(
    @SerializedName("set_number") val setNumber: Int,
    val reps: Int,
    @SerializedName("mean_velocity") val meanVelocity: Double,
    @SerializedName("peak_velocity") val peakVelocity: Double,
    @SerializedName("load_kg") val loadKg: Double
)

data class FatigueIndexDto(
    @SerializedName("exercise_id") val exerciseId: Int,
    @SerializedName("exercise_name") val exerciseName: String,
    val sets: List<FatigueSetDto>,
    @SerializedName("fatigue_index_pct") val fatigueIndexPct: Double,
    @SerializedName("velocity_drop_ms") val velocityDropMs: Double,
    @SerializedName("best_set") val bestSet: Int,
    @SerializedName("readiness_zone") val readinessZone: String  // optimal/moderate/high/overreached
)

// Analytics — Weekly Load
data class WeeklyDayDto(
    val date: String,
    val sessions: Int,
    @SerializedName("total_reps") val totalReps: Int,
    @SerializedName("mean_velocity") val meanVelocity: Double,
    @SerializedName("total_volume_kg") val totalVolumeKg: Double
)

data class WeeklyLoadDto(
    val week: String,
    @SerializedName("training_days") val trainingDays: Int,
    @SerializedName("week_mean_velocity") val weekMeanVelocity: Double,
    @SerializedName("week_total_reps") val weekTotalReps: Int,
    @SerializedName("week_total_volume_kg") val weekTotalVolumeKg: Double,
    @SerializedName("weekly_fatigue_pct") val weeklyFatiguePct: Double,
    val days: List<WeeklyDayDto>
)

// Analytics — Week Comparison
data class WeekComparisonDto(
    val week: String,
    val sessions: Int,
    @SerializedName("total_reps") val totalReps: Int,
    @SerializedName("mean_velocity") val meanVelocity: Double,
    @SerializedName("max_velocity") val maxVelocity: Double,
    @SerializedName("mean_peak_velocity") val meanPeakVelocity: Double,
    @SerializedName("mean_load_kg") val meanLoadKg: Double,
    @SerializedName("max_load_kg") val maxLoadKg: Double,
    @SerializedName("total_volume_kg") val totalVolumeKg: Double,
    @SerializedName("best_estimated_1rm") val bestEstimated1rm: Double?
)

// User management
data class CreateAthleteRequest(
    val username: String,
    val email: String?,
    val password: String,
    val role: String = "athlete"
)

data class AssignByUsernameRequest(
    val username: String
)

data class AdminAssignRequest(
    @SerializedName("athlete_username") val athleteUsername: String,
    @SerializedName("coach_username") val coachUsername: String
)

// Dashboard
data class DashboardStatsDto(
    @SerializedName("total_athletes") val totalAthletes: Int,
    @SerializedName("active_plans") val activePlans: Int,
    @SerializedName("sessions_this_week") val sessionsThisWeek: Int
)

data class RecentSessionDto(
    val id: Int,
    @SerializedName("athlete_id") val athleteId: Int,
    @SerializedName("athlete_name") val athleteName: String,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("duration_seconds") val durationSeconds: Int?,
    @SerializedName("reps_count") val repsCount: Int
)
