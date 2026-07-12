package com.vbt.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rep_results",
    foreignKeys = [
        ForeignKey(
            entity = SessionSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionSetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionSetId")]
)
data class RepResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionSetId: Long,
    val repNumber: Int,
    // Historycznie: średnia prędkość koncentryczna (mean velocity) - nazwa
    // kolumny zachowana dla kompatybilności istniejących baz.
    val maxVelocityMs: Float,
    // Prędkość szczytowa (peak); 0 dla rekordów sprzed migracji / starego
    // firmware - wtedy przy synchronizacji używany jest fallback maxVelocityMs.
    val peakVelocityMs: Float = 0f,
    val distanceM: Float,
    val durationMs: Int,
    val powerW: Float,
    val deviceRepIndex: Int,
    val deviceTimestamp: Long,
    val recordedAt: Long,
    val isDeleted: Boolean = false
)
