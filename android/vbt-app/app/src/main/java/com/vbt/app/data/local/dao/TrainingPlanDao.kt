package com.vbt.app.data.local.dao

import androidx.room.*
import com.vbt.app.data.local.entity.PlanExerciseEntity
import com.vbt.app.data.local.entity.PlanSetEntity
import com.vbt.app.data.local.entity.TrainingPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingPlanDao {
    @Query("SELECT * FROM training_plans ORDER BY updatedAt DESC")
    fun getAllPlans(): Flow<List<TrainingPlanEntity>>

    @Query("SELECT * FROM training_plans WHERE id = :id")
    suspend fun getPlanById(id: Long): TrainingPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: TrainingPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: TrainingPlanEntity)

    @Delete
    suspend fun deletePlan(plan: TrainingPlanEntity)

    // Plan exercises
    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY orderIndex")
    fun getExercisesForPlan(planId: Long): Flow<List<PlanExerciseEntity>>

    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY orderIndex")
    suspend fun getExercisesForPlanOnce(planId: Long): List<PlanExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanExercise(exercise: PlanExerciseEntity): Long

    @Update
    suspend fun updatePlanExercise(exercise: PlanExerciseEntity)

    @Delete
    suspend fun deletePlanExercise(exercise: PlanExerciseEntity)

    @Query("DELETE FROM plan_exercises WHERE planId = :planId")
    suspend fun deleteAllExercisesForPlan(planId: Long)

    // Plan sets
    @Query("SELECT * FROM plan_sets WHERE planExerciseId = :planExerciseId ORDER BY setNumber")
    fun getSetsForExercise(planExerciseId: Long): Flow<List<PlanSetEntity>>

    @Query("SELECT * FROM plan_sets WHERE planExerciseId = :planExerciseId ORDER BY setNumber")
    suspend fun getSetsForExerciseOnce(planExerciseId: Long): List<PlanSetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanSet(set: PlanSetEntity): Long

    @Update
    suspend fun updatePlanSet(set: PlanSetEntity)

    @Delete
    suspend fun deletePlanSet(set: PlanSetEntity)

    @Query("DELETE FROM plan_sets WHERE planExerciseId = :planExerciseId")
    suspend fun deleteAllSetsForExercise(planExerciseId: Long)
}
