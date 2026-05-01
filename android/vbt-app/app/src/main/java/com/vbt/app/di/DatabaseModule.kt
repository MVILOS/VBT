package com.vbt.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vbt.app.data.local.MIGRATION_1_2
import com.vbt.app.data.local.VbtDatabase
import com.vbt.app.data.local.dao.*
import com.vbt.app.data.local.entity.ExerciseDefinitionEntity
import com.vbt.app.domain.model.ExerciseType
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VbtDatabase {
        return Room.databaseBuilder(
            context,
            VbtDatabase::class.java,
            "vbt_database"
        )
        .addMigrations(MIGRATION_1_2)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val database = Room.databaseBuilder(
                        context,
                        VbtDatabase::class.java,
                        "vbt_database"
                    ).build()
                    seedExercises(database.exerciseDao())
                }
            }
        })
        .build()
    }

    private suspend fun seedExercises(dao: ExerciseDao) {
        val exercises = ExerciseType.entries.filter { it != ExerciseType.CUSTOM }.map { type ->
            ExerciseDefinitionEntity(
                name = type.displayName,
                category = type.category,
                defaultMinLiftVelocity = type.defaultMinLiftVelocity,
                defaultEndLiftVelocity = type.defaultEndLiftVelocity,
                defaultMinRepDistance = type.defaultMinRepDistance,
                isBuiltIn = true
            )
        }
        dao.insertAll(exercises)
    }

    @Provides
    fun provideExerciseDao(db: VbtDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideTrainingPlanDao(db: VbtDatabase): TrainingPlanDao = db.trainingPlanDao()

    @Provides
    fun provideWorkoutSessionDao(db: VbtDatabase): WorkoutSessionDao = db.workoutSessionDao()

    @Provides
    fun provideRepResultDao(db: VbtDatabase): RepResultDao = db.repResultDao()
}
