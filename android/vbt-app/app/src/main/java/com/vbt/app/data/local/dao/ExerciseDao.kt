package com.vbt.app.data.local.dao

import androidx.room.*
import com.vbt.app.data.local.entity.ExerciseDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise_definitions ORDER BY name")
    fun getAllExercises(): Flow<List<ExerciseDefinitionEntity>>

    @Query("SELECT * FROM exercise_definitions WHERE id = :id")
    suspend fun getById(id: Long): ExerciseDefinitionEntity?

    @Query("SELECT * FROM exercise_definitions WHERE category = :category")
    fun getByCategory(category: String): Flow<List<ExerciseDefinitionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: ExerciseDefinitionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseDefinitionEntity>)

    @Update
    suspend fun update(exercise: ExerciseDefinitionEntity)

    @Delete
    suspend fun delete(exercise: ExerciseDefinitionEntity)

    @Query("SELECT COUNT(*) FROM exercise_definitions")
    suspend fun getCount(): Int

    @Query("SELECT * FROM exercise_definitions WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getByName(name: String): ExerciseDefinitionEntity?
}
