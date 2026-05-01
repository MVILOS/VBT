package com.vbt.app.ui.screen.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.local.entity.ExerciseDefinitionEntity
import com.vbt.app.data.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseListViewModel @Inject constructor(
    private val repository: ExerciseRepository
) : ViewModel() {

    private val _exercises = MutableStateFlow<List<ExerciseDefinitionEntity>>(emptyList())
    val exercises: StateFlow<List<ExerciseDefinitionEntity>> = _exercises.asStateFlow()

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _editingExercise = MutableStateFlow<ExerciseDefinitionEntity?>(null)
    val editingExercise: StateFlow<ExerciseDefinitionEntity?> = _editingExercise.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllExercises().collect { list ->
                _exercises.value = list
            }
        }
    }

    fun showAddDialog() { _showAddDialog.value = true }
    fun hideAddDialog() {
        _showAddDialog.value = false
        _editingExercise.value = null
    }

    fun startEditing(exercise: ExerciseDefinitionEntity) {
        _editingExercise.value = exercise
        _showAddDialog.value = true
    }

    fun saveExercise(
        name: String,
        category: String,
        minLiftVelocity: Float,
        endLiftVelocity: Float,
        minRepDistance: Float
    ) {
        viewModelScope.launch {
            val editing = _editingExercise.value
            if (editing != null) {
                repository.updateExercise(
                    editing.copy(
                        name = name,
                        category = category,
                        defaultMinLiftVelocity = minLiftVelocity,
                        defaultEndLiftVelocity = endLiftVelocity,
                        defaultMinRepDistance = minRepDistance
                    )
                )
            } else {
                repository.addExercise(
                    ExerciseDefinitionEntity(
                        name = name,
                        category = category,
                        defaultMinLiftVelocity = minLiftVelocity,
                        defaultEndLiftVelocity = endLiftVelocity,
                        defaultMinRepDistance = minRepDistance,
                        isBuiltIn = false
                    )
                )
            }
            hideAddDialog()
        }
    }

    fun deleteExercise(exercise: ExerciseDefinitionEntity) {
        if (exercise.isBuiltIn) return // Cannot delete built-in exercises
        viewModelScope.launch {
            repository.deleteExercise(exercise)
        }
    }
}
