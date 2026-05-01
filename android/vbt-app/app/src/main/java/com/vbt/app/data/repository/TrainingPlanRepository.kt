package com.vbt.app.data.repository

import com.vbt.app.data.local.dao.TrainingPlanDao
import com.vbt.app.data.local.entity.PlanExerciseEntity
import com.vbt.app.data.local.entity.PlanSetEntity
import com.vbt.app.data.local.entity.TrainingPlanEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrainingPlanRepository @Inject constructor(
    private val planDao: TrainingPlanDao
) {
    fun getAllPlans(): Flow<List<TrainingPlanEntity>> = planDao.getAllPlans()

    suspend fun getPlanById(id: Long): TrainingPlanEntity? = planDao.getPlanById(id)

    suspend fun createPlan(name: String, notes: String? = null): Long {
        val now = System.currentTimeMillis()
        return planDao.insertPlan(
            TrainingPlanEntity(name = name, createdAt = now, updatedAt = now, notes = notes)
        )
    }

    suspend fun updatePlan(plan: TrainingPlanEntity) {
        planDao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePlan(plan: TrainingPlanEntity) = planDao.deletePlan(plan)

    fun getExercisesForPlan(planId: Long): Flow<List<PlanExerciseEntity>> =
        planDao.getExercisesForPlan(planId)

    suspend fun getExercisesForPlanOnce(planId: Long): List<PlanExerciseEntity> =
        planDao.getExercisesForPlanOnce(planId)

    suspend fun addExerciseToPlan(planId: Long, exerciseId: Long, orderIndex: Int): Long =
        planDao.insertPlanExercise(
            PlanExerciseEntity(planId = planId, exerciseId = exerciseId, orderIndex = orderIndex)
        )

    suspend fun deletePlanExercise(exercise: PlanExerciseEntity) =
        planDao.deletePlanExercise(exercise)

    fun getSetsForExercise(planExerciseId: Long): Flow<List<PlanSetEntity>> =
        planDao.getSetsForExercise(planExerciseId)

    suspend fun getSetsForExerciseOnce(planExerciseId: Long): List<PlanSetEntity> =
        planDao.getSetsForExerciseOnce(planExerciseId)

    suspend fun addSetToExercise(planExerciseId: Long, setNumber: Int, loadKg: Float, targetReps: Int? = null, targetRpe: Float? = null): Long =
        planDao.insertPlanSet(
            PlanSetEntity(
                planExerciseId = planExerciseId,
                setNumber = setNumber,
                targetLoadKg = loadKg,
                targetReps = targetReps,
                targetRpe = targetRpe
            )
        )

    suspend fun updateSet(set: PlanSetEntity) = planDao.updatePlanSet(set)

    suspend fun deleteSet(set: PlanSetEntity) = planDao.deletePlanSet(set)
}
