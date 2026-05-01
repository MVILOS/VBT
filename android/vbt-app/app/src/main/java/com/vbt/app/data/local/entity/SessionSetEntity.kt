package com.vbt.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"]
        ),
        ForeignKey(
            entity = PlanSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["planSetId"]
        )
    ],
    indices = [Index("sessionId"), Index("exerciseId"), Index("planSetId")]
)
data class SessionSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val planSetId: Long? = null,
    val setNumber: Int,
    val actualLoadKg: Float,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val isCompleted: Boolean = false
)
