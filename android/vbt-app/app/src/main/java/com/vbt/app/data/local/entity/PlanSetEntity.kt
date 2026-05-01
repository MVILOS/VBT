package com.vbt.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "plan_sets",
    foreignKeys = [
        ForeignKey(
            entity = PlanExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["planExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("planExerciseId")]
)
data class PlanSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planExerciseId: Long,
    val setNumber: Int,
    val targetLoadKg: Float,
    val targetReps: Int? = null,
    val targetRpe: Float? = null,
    val notes: String? = null
)
