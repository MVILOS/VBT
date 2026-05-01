package com.vbt.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vbt.app.data.local.dao.*
import com.vbt.app.data.local.entity.*

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE workout_sessions ADD COLUMN status TEXT NOT NULL DEFAULT 'finished'")
        database.execSQL("ALTER TABLE workout_sessions ADD COLUMN serverSessionId INTEGER")
        database.execSQL("ALTER TABLE workout_sessions ADD COLUMN athleteServerId INTEGER")
    }
}

@Database(
    entities = [
        ExerciseDefinitionEntity::class,
        TrainingPlanEntity::class,
        PlanExerciseEntity::class,
        PlanSetEntity::class,
        WorkoutSessionEntity::class,
        SessionSetEntity::class,
        RepResultEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class VbtDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun trainingPlanDao(): TrainingPlanDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun repResultDao(): RepResultDao
}
