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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Peak velocity per powtórzenie (0 = brak danych ze starego firmware)
        database.execSQL("ALTER TABLE rep_results ADD COLUMN peakVelocityMs REAL NOT NULL DEFAULT 0")
        // Tętno per sesja (nullable - HR opcjonalne)
        database.execSQL("ALTER TABLE workout_sessions ADD COLUMN avgHeartRate INTEGER")
        database.execSQL("ALTER TABLE workout_sessions ADD COLUMN maxHeartRate INTEGER")
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
    version = 3,
    exportSchema = false
)
abstract class VbtDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun trainingPlanDao(): TrainingPlanDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun repResultDao(): RepResultDao
}
