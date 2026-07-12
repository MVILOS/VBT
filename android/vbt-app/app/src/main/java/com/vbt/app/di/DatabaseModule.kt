package com.vbt.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vbt.app.data.local.MIGRATION_1_2
import com.vbt.app.data.local.MIGRATION_2_3
import com.vbt.app.data.local.VbtDatabase
import com.vbt.app.data.local.dao.*
import com.vbt.app.data.local.entity.ExerciseDefinitionEntity
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
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

    // Podstawowa baza ćwiczeń MVT (Minimum Velocity Threshold), replika listy
    // EXERCISES z server/backend/app/main.py - wgrywana lokalnie przy pierwszym
    // uruchomieniu apki, żeby trening (freestyle) działał całkowicie offline,
    // nawet bez wcześniejszej synchronizacji z serwerem.
    // Triple = (nazwa, kategoria, mvt [m/s])
    private val BUILT_IN_EXERCISES: List<Triple<String, String, Float>> = listOf(
        // --- Ruchy olimpijskie ---
        Triple("Rwanie (Snatch)", "olympic", 0.80f),
        Triple("Zarzut (Clean)", "olympic", 0.75f),
        Triple("Wyrwanie (Jerk)", "olympic", 0.65f),
        Triple("Wyrwanie z rozkroku (Split Jerk)", "olympic", 0.65f),
        Triple("Szturm (Push Jerk)", "olympic", 0.60f),
        Triple("Podrzut - Zarzut i Wyrwanie (Clean & Jerk)", "olympic", 0.75f),
        Triple("Rwanie siłowe (Power Snatch)", "olympic", 1.00f),
        Triple("Rwanie z wieszania (Hang Snatch)", "olympic", 0.80f),
        Triple("Rwanie z klocków (Block Snatch)", "olympic", 0.80f),
        Triple("Szarpanie do rwania (Snatch Pull)", "olympic", 0.50f),
        Triple("Szarpanie do rwania z wieszania (Hang Snatch Pull)", "olympic", 0.50f),
        Triple("Zarzut siłowy (Power Clean)", "olympic", 0.85f),
        Triple("Zarzut z wieszania (Hang Clean)", "olympic", 0.75f),
        Triple("Zarzut z klocków (Block Clean)", "olympic", 0.75f),
        Triple("Szarpanie do zarzutu (Clean Pull)", "olympic", 0.45f),
        Triple("Szarpanie do zarzutu z wieszania (Hang Clean Pull)", "olympic", 0.45f),
        // --- Siłowe ---
        Triple("Przysiad (Back Squat)", "strength", 0.30f),
        Triple("Przysiad przedni (Front Squat)", "strength", 0.32f),
        Triple("Martwy ciąg (Deadlift)", "strength", 0.15f),
        Triple("Martwy ciąg rumuński (Romanian Deadlift)", "strength", 0.15f),
        Triple("Wyciskanie leżąc (Bench Press)", "strength", 0.17f),
        Triple("Wyciskanie żołnierskie (Overhead Press)", "strength", 0.22f),
        Triple("Szturm ze sztangą (Push Press)", "strength", 0.50f),
        // --- Balistyczne ---
        Triple("Przysiad skoczny (Jump Squat)", "ballistic", 1.00f),
        // --- Pomocnicze ---
        Triple("Wiosłowanie sztangą (Barbell Row)", "auxiliary", 0.25f),
        Triple("Dobre rano (Good Morning)", "auxiliary", 0.20f)
    )

    private suspend fun seedExercises(dao: ExerciseDao) {
        val exercises = BUILT_IN_EXERCISES.map { (name, category, mvt) ->
            ExerciseDefinitionEntity(
                name = name,
                category = category,
                defaultMinLiftVelocity = mvt * 0.5f,
                defaultEndLiftVelocity = mvt * 0.7f,
                defaultMinRepDistance = 0.15f,
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
