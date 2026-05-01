package com.vbt.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = TrainingPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"]
        )
    ],
    indices = [Index("planId")]
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long? = null,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val athleteName: String? = null,
    val notes: String? = null,
    // "active" = in progress, "finished" = saved, "discarded" = deleted
    val status: String = "active",
    // Server-assigned session ID after live sync
    val serverSessionId: Int? = null,
    // Athlete server ID (for sync)
    val athleteServerId: Int? = null
)
