package com.vbt.app.ui.screen.exercises

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vbt.app.data.local.entity.ExerciseDefinitionEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseListScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExerciseListViewModel = hiltViewModel()
) {
    val exercises by viewModel.exercises.collectAsState()
    val showDialog by viewModel.showAddDialog.collectAsState()
    val editingExercise by viewModel.editingExercise.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exercise Catalog") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, "Add Exercise")
            }
        }
    ) { padding ->
        if (exercises.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No exercises defined yet")
            }
        } else {
            val grouped = exercises.groupBy { it.category }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                grouped.forEach { (category, exerciseList) ->
                    item {
                        Text(
                            text = category.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(exerciseList) { exercise ->
                        ExerciseCard(
                            exercise = exercise,
                            onEdit = { viewModel.startEditing(exercise) },
                            onDelete = { viewModel.deleteExercise(exercise) }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        ExerciseEditDialog(
            exercise = editingExercise,
            onDismiss = { viewModel.hideAddDialog() },
            onSave = { name, category, minVel, endVel, minDist ->
                viewModel.saveExercise(name, category, minVel, endVel, minDist)
            }
        )
    }
}

@Composable
fun ExerciseCard(
    exercise: ExerciseDefinitionEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Min vel: ${exercise.defaultMinLiftVelocity} m/s | ROM: ${exercise.defaultMinRepDistance} m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (exercise.isBuiltIn) {
                    Text(
                        text = "Built-in",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(20.dp))
            }
            if (!exercise.isBuiltIn) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete, "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Exercise") },
            text = { Text("Are you sure you want to delete \"${exercise.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ExerciseEditDialog(
    exercise: ExerciseDefinitionEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, Float, Float, Float) -> Unit
) {
    var name by remember { mutableStateOf(exercise?.name ?: "") }
    var category by remember { mutableStateOf(exercise?.category ?: "custom") }
    var minLiftVelocity by remember { mutableStateOf(exercise?.defaultMinLiftVelocity?.toString() ?: "0.10") }
    var endLiftVelocity by remember { mutableStateOf(exercise?.defaultEndLiftVelocity?.toString() ?: "0.05") }
    var minRepDistance by remember { mutableStateOf(exercise?.defaultMinRepDistance?.toString() ?: "0.10") }

    val categories = listOf("squat", "bench", "deadlift", "ohp", "custom")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exercise != null) "Edit Exercise" else "Add Exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Exercise Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category selector
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categories.take(3).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categories.drop(3).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                OutlinedTextField(
                    value = minLiftVelocity,
                    onValueChange = { minLiftVelocity = it },
                    label = { Text("Min Lift Velocity (m/s)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endLiftVelocity,
                    onValueChange = { endLiftVelocity = it },
                    label = { Text("End Lift Velocity (m/s)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = minRepDistance,
                    onValueChange = { minRepDistance = it },
                    label = { Text("Min Rep Distance (m)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minVel = minLiftVelocity.toFloatOrNull() ?: 0.10f
                    val endVel = endLiftVelocity.toFloatOrNull() ?: 0.05f
                    val minDist = minRepDistance.toFloatOrNull() ?: 0.10f
                    if (name.isNotBlank()) {
                        onSave(name.trim(), category, minVel, endVel, minDist)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
