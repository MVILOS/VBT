package com.vbt.app.data.remote

import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    // Authentication
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<LoginResponse>

    // Exercises
    @GET("exercises")
    suspend fun getExercises(): Response<List<ExerciseDto>>

    @POST("exercises")
    suspend fun createExercise(@Body request: CreateExerciseRequest): Response<ExerciseDto>

    // Training Plans
    @GET("plans")
    suspend fun getPlans(): Response<List<TrainingPlanDto>>

    @POST("plans")
    suspend fun createPlan(@Body request: CreatePlanRequest): Response<TrainingPlanDto>

    @GET("plans/{id}")
    suspend fun getPlan(@Path("id") planId: Int): Response<TrainingPlanDto>

    @PUT("plans/{id}")
    suspend fun updatePlan(
        @Path("id") planId: Int,
        @Body request: CreatePlanRequest
    ): Response<TrainingPlanDto>

    @DELETE("plans/{id}")
    suspend fun deletePlan(@Path("id") planId: Int): Response<Unit>

    @POST("plans/{id}/assign/{athleteId}")
    suspend fun assignPlanToAthlete(
        @Path("id") planId: Int,
        @Path("athleteId") athleteId: Int
    ): Response<TrainingPlanDto>

    // Calendar
    @GET("calendar")
    suspend fun getCalendarEntries(
        @Query("athlete_id") athleteId: Int? = null,
        @Query("date_start") dateStart: String? = null,
        @Query("date_end") dateEnd: String? = null
    ): Response<List<CalendarEntryDto>>

    @POST("calendar")
    suspend fun createCalendarEntry(@Body request: CreateCalendarEntryRequest): Response<CalendarEntryDto>

    @PUT("calendar/{id}")
    suspend fun updateCalendarEntry(
        @Path("id") entryId: Int,
        @Body request: UpdateCalendarEntryRequest
    ): Response<CalendarEntryDto>

    @DELETE("calendar/{id}")
    suspend fun deleteCalendarEntry(@Path("id") entryId: Int): Response<Unit>

    // Workout Sessions
    @GET("sessions")
    suspend fun getSessions(
        @Query("athlete_id") athleteId: Int? = null
    ): Response<List<WorkoutSessionDto>>

    @POST("sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): Response<WorkoutSessionDto>

    @POST("sessions/live")
    suspend fun startLiveSession(@Body request: StartLiveSessionRequest): Response<WorkoutSessionDto>

    @POST("sessions/{id}/reps")
    suspend fun appendReps(
        @Path("id") sessionId: Int,
        @Body request: AppendRepsRequest
    ): Response<WorkoutSessionDto>

    @DELETE("sessions/{id}")
    suspend fun deleteSession(@Path("id") sessionId: Int): Response<Unit>

    @GET("sessions/{id}")
    suspend fun getSession(@Path("id") sessionId: Int): Response<WorkoutSessionDto>

    @PATCH("sessions/{sessionId}/reps/{repId}")
    suspend fun updateRep(
        @Path("sessionId") sessionId: Int,
        @Path("repId") repId: Int,
        @Body request: UpdateRepRequest
    ): Response<RepResultDto>

    @DELETE("sessions/{sessionId}/reps/{repId}")
    suspend fun deleteRep(
        @Path("sessionId") sessionId: Int,
        @Path("repId") repId: Int
    ): Response<Unit>

    // Athletes/Users
    @GET("users/athletes")
    suspend fun getAthletes(): Response<List<UserDto>>

    @GET("users/athletes/{id}")
    suspend fun getAthlete(@Path("id") athleteId: Int): Response<UserDto>

    @POST("users/athletes")
    suspend fun createAthlete(@Body request: CreateAthleteRequest): Response<UserDto>

    @POST("users/assign-by-username")
    suspend fun assignUserByUsername(@Body request: AssignByUsernameRequest): Response<UserDto>

    @POST("admin/assign-to-coach")
    suspend fun adminAssignToCoach(@Body request: AdminAssignRequest): Response<Map<String, String>>

    @DELETE("admin/unassign-from-coach")
    suspend fun adminUnassignFromCoach(@Body request: AdminAssignRequest): Response<Map<String, String>>

    @DELETE("users/athletes/{id}/unassign")
    suspend fun unassignAthlete(@Path("id") athleteId: Int): Response<Unit>

    // Velocity Trace
    @POST("sessions/{sessionId}/reps/{repId}/velocity-trace")
    suspend fun uploadVelocityTrace(
        @Path("sessionId") sessionId: Int,
        @Path("repId") repId: Int,
        @Body request: VelocityTraceRequest
    ): Response<Unit>

    // Analytics
    @GET("analytics/velocity-trend")
    suspend fun getVelocityTrend(
        @Query("athlete_id") athleteId: Int? = null,
        @Query("exercise_id") exerciseId: Int? = null,
        @Query("days") days: Int = 30
    ): Response<List<VelocityTrendPointDto>>

    @GET("analytics/1rm-progress")
    suspend fun get1rmProgress(
        @Query("athlete_id") athleteId: Int? = null,
        @Query("exercise_id") exerciseId: Int
    ): Response<List<OneRmProgressPointDto>>

    @GET("analytics/fatigue-index")
    suspend fun getFatigueIndex(
        @Query("session_id") sessionId: Int,
        @Query("athlete_id") athleteId: Int? = null,
        @Query("exercise_id") exerciseId: Int? = null
    ): Response<List<FatigueIndexDto>>

    @GET("analytics/weekly-load")
    suspend fun getWeeklyLoad(
        @Query("athlete_id") athleteId: Int? = null,
        @Query("weeks") weeks: Int = 6,
        @Query("exercise_id") exerciseId: Int? = null
    ): Response<List<WeeklyLoadDto>>

    @GET("analytics/week-comparison")
    suspend fun getWeekComparison(
        @Query("exercise_id") exerciseId: Int,
        @Query("athlete_id") athleteId: Int? = null,
        @Query("weeks") weeks: Int = 8
    ): Response<List<WeekComparisonDto>>

    // Dashboard
    @GET("dashboard/stats")
    suspend fun getDashboardStats(): Response<DashboardStatsDto>

    @GET("dashboard/recent-sessions")
    suspend fun getRecentSessions(
        @Query("limit") limit: Int = 5
    ): Response<List<RecentSessionDto>>
}
