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

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                renameLegacyExercises(db)
            }
        })
        .build()
    }

    // Stare, dziwne nazwy ("Szturm ze sztangą...", "Szarpanie do rwania...",
    // "Dobre rano...") zostają w lokalnej bazie po aktualizacji aplikacji, bo
    // seed wykonuje się tylko przy pierwszej instalacji. Idempotentna zmiana
    // przy każdym otwarciu bazy, tą samą mapą co migracja serwera
    // (server/backend/app/main.py) - nazwa jest kluczem dopasowania ćwiczeń
    // lokalnych do serwerowych (resolveServerExercise), więc obie strony muszą
    // przejść na format "English (Polski)" jednocześnie.
    private val LEGACY_RENAMES = mapOf(
        "Rwanie (Snatch)" to "Snatch (Rwanie)",
        "Zarzut (Clean)" to "Clean (Zarzut)",
        "Wyrwanie (Jerk)" to "Jerk (Wyrzut)",
        "Wyrwanie z rozkroku (Split Jerk)" to "Split Jerk (Wyrzut nożycowy)",
        "Szturm (Push Jerk)" to "Push Jerk (Wyrzut siłowy)",
        "Podrzut - Zarzut i Wyrwanie (Clean & Jerk)" to "Clean & Jerk (Podrzut)",
        "Rwanie siłowe (Power Snatch)" to "Power Snatch (Rwanie siłowe)",
        "Rwanie z wieszania (Hang Snatch)" to "Hang Snatch (Rwanie z zawieszenia)",
        "Rwanie z klocków (Block Snatch)" to "Block Snatch (Rwanie z bloków)",
        "Szarpanie do rwania (Snatch Pull)" to "Snatch Pull (Ciąg rwaniowy)",
        "Szarpanie do rwania z wieszania (Hang Snatch Pull)" to "Hang Snatch Pull (Ciąg rwaniowy z zawieszenia)",
        "Zarzut siłowy (Power Clean)" to "Power Clean (Zarzut siłowy)",
        "Zarzut z wieszania (Hang Clean)" to "Hang Clean (Zarzut z zawieszenia)",
        "Zarzut z klocków (Block Clean)" to "Block Clean (Zarzut z bloków)",
        "Szarpanie do zarzutu (Clean Pull)" to "Clean Pull (Ciąg zarzutowy)",
        "Szarpanie do zarzutu z wieszania (Hang Clean Pull)" to "Hang Clean Pull (Ciąg zarzutowy z zawieszenia)",
        "Przysiad (Back Squat)" to "Back Squat (Przysiad tylny)",
        "Przysiad przedni (Front Squat)" to "Front Squat (Przysiad przedni)",
        "Martwy ciąg (Deadlift)" to "Deadlift (Martwy ciąg)",
        "Martwy ciąg rumuński (Romanian Deadlift)" to "Romanian Deadlift (Martwy ciąg rumuński)",
        "Wyciskanie leżąc (Bench Press)" to "Bench Press (Wyciskanie leżąc)",
        "Wyciskanie żołnierskie (Overhead Press)" to "Overhead Press (Wyciskanie nad głowę)",
        "Szturm ze sztangą (Push Press)" to "Push Press (Wyciskanie siłowe)",
        "Przysiad skoczny (Jump Squat)" to "Jump Squat (Przysiad ze skokiem)",
        "Wiosłowanie sztangą (Barbell Row)" to "Barbell Row (Wiosłowanie sztangą)",
        "Dobre rano (Good Morning)" to "Good Morning (Skłon dzień dobry)"
    )

    private fun renameLegacyExercises(db: SupportSQLiteDatabase) {
        LEGACY_RENAMES.forEach { (old, new) ->
            // Guard na wypadek, gdyby nowa nazwa już istniała (np. dodana
            // ręcznie) - UPDATE stworzyłby wtedy duplikat nazwy.
            db.execSQL(
                "UPDATE exercise_definitions SET name = ? " +
                    "WHERE LOWER(name) = LOWER(?) " +
                    "AND NOT EXISTS (SELECT 1 FROM exercise_definitions WHERE LOWER(name) = LOWER(?))",
                arrayOf(new, old, new)
            )
        }
    }

    // Podstawowa baza ćwiczeń MVT (Minimum Velocity Threshold), replika listy
    // EXERCISES z server/backend/app/main.py - wgrywana lokalnie przy pierwszym
    // uruchomieniu apki, żeby trening (freestyle) działał całkowicie offline,
    // nawet bez wcześniejszej synchronizacji z serwerem.
    // Triple = (nazwa, kategoria, mvt [m/s])
    // Format nazwy "English (Polski)" - musi być identyczny z EXERCISES na
    // serwerze (server/backend/app/main.py), bo nazwa jest kluczem dopasowania
    // przy synchronizacji (resolveServerExercise dopasowuje po nazwie).
    private val BUILT_IN_EXERCISES: List<Triple<String, String, Float>> = listOf(
        // --- Olympic ---
        Triple("Snatch (Rwanie)", "olympic", 0.80f),
        Triple("Clean (Zarzut)", "olympic", 0.75f),
        Triple("Jerk (Wyrzut)", "olympic", 0.65f),
        Triple("Split Jerk (Wyrzut nożycowy)", "olympic", 0.65f),
        Triple("Push Jerk (Wyrzut siłowy)", "olympic", 0.60f),
        Triple("Clean & Jerk (Podrzut)", "olympic", 0.75f),
        Triple("Power Snatch (Rwanie siłowe)", "olympic", 1.00f),
        Triple("Hang Snatch (Rwanie z zawieszenia)", "olympic", 0.80f),
        Triple("Block Snatch (Rwanie z bloków)", "olympic", 0.80f),
        Triple("Snatch Pull (Ciąg rwaniowy)", "olympic", 0.50f),
        Triple("Hang Snatch Pull (Ciąg rwaniowy z zawieszenia)", "olympic", 0.50f),
        Triple("Power Clean (Zarzut siłowy)", "olympic", 0.85f),
        Triple("Hang Clean (Zarzut z zawieszenia)", "olympic", 0.75f),
        Triple("Block Clean (Zarzut z bloków)", "olympic", 0.75f),
        Triple("Clean Pull (Ciąg zarzutowy)", "olympic", 0.45f),
        Triple("Hang Clean Pull (Ciąg zarzutowy z zawieszenia)", "olympic", 0.45f),
        // --- Strength ---
        Triple("Back Squat (Przysiad tylny)", "strength", 0.30f),
        Triple("Front Squat (Przysiad przedni)", "strength", 0.32f),
        Triple("Deadlift (Martwy ciąg)", "strength", 0.15f),
        Triple("Romanian Deadlift (Martwy ciąg rumuński)", "strength", 0.15f),
        Triple("Bench Press (Wyciskanie leżąc)", "strength", 0.17f),
        Triple("Overhead Press (Wyciskanie nad głowę)", "strength", 0.22f),
        Triple("Push Press (Wyciskanie siłowe)", "strength", 0.50f),
        // --- Ballistic ---
        Triple("Jump Squat (Przysiad ze skokiem)", "ballistic", 1.00f),
        // --- Auxiliary ---
        Triple("Barbell Row (Wiosłowanie sztangą)", "auxiliary", 0.25f),
        Triple("Good Morning (Skłon dzień dobry)", "auxiliary", 0.20f)
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
