package com.vbt.app.data.repository

import com.vbt.app.data.local.dao.ExerciseDao
import com.vbt.app.data.local.entity.ExerciseDefinitionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao
) {
    fun getAllExercises(): Flow<List<ExerciseDefinitionEntity>> = exerciseDao.getAllExercises()

    fun getByCategory(category: String): Flow<List<ExerciseDefinitionEntity>> =
        exerciseDao.getByCategory(category)

    suspend fun getById(id: Long): ExerciseDefinitionEntity? = exerciseDao.getById(id)

    suspend fun addExercise(exercise: ExerciseDefinitionEntity): Long =
        exerciseDao.insert(exercise)

    suspend fun updateExercise(exercise: ExerciseDefinitionEntity) =
        exerciseDao.update(exercise)

    suspend fun deleteExercise(exercise: ExerciseDefinitionEntity) =
        exerciseDao.delete(exercise)

    suspend fun findOrCreateByName(name: String, category: String?): Long {
        val existing = exerciseDao.getByName(name)
        if (existing != null) return existing.id
        return exerciseDao.insert(
            ExerciseDefinitionEntity(
                name = name,
                category = category ?: "custom",
                defaultMinLiftVelocity = 0.10f,
                defaultEndLiftVelocity = 0.05f,
                defaultMinRepDistance = 0.10f
            )
        )
    }
}
