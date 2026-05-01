package com.vbt.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_definitions")
data class ExerciseDefinitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val defaultMinLiftVelocity: Float,
    val defaultEndLiftVelocity: Float,
    val defaultMinRepDistance: Float,
    val isBuiltIn: Boolean = false
)
