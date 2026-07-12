package com.vbt.app.ui.screen.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vbt.app.ui.theme.VbtError
import com.vbt.app.ui.theme.VbtSurface
import com.vbt.app.ui.screen.workout.formatKg
import com.vbt.app.ui.theme.VbtTeal
import com.vbt.app.ui.theme.VbtTextSecondary

private fun categoryOrder(cat: String?): Int = when (cat) {
    "olympic" -> 0
    "strength" -> 1
    "ballistic" -> 2
    "auxiliary" -> 3
    else -> 4
}

private fun categoryLabel(cat: String?): String = when (cat) {
    "olympic" -> "OLIMPIJSKIE"
    "strength" -> "SIŁOWE"
    "ballistic" -> "BALISTYCZNE"
    "auxiliary" -> "POMOCNICZE"
    else -> "INNE"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditScreen(
    planId: Int?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: PlanEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (planId != null && uiState.planId == null) {
            viewModel.loadPlan(planId)
        }
    }

    var showExercisePicker by remember { mutableStateOf(false) }
    var showNewExerciseDialog by remember { mutableStateOf(false) }
    var pickerForExerciseIdx by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.planId == null) "Nowy Plan" else "Edytuj Plan",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.savePlan { onSaved() }
                        },
                        enabled = uiState.name.isNotBlank() && !uiState.isLoading,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            "Zapisz",
                            color = VbtTeal,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text(
                    "PODSTAWOWE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = VbtTextSecondary
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text("Nazwa planu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VbtTeal,
                        focusedLabelColor = VbtTeal
                    )
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = { viewModel.updateDescription(it) },
                    label = { Text("Opis (opcjonalnie)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VbtTeal,
                        focusedLabelColor = VbtTeal
                    )
                )
            }

            item {
                var athleteDropdownExpanded by remember { mutableStateOf(false) }
                val selectedAthlete = uiState.availableAthletes.find { it.id == uiState.assignedToId }

                ExposedDropdownMenuBox(
                    expanded = athleteDropdownExpanded,
                    onExpandedChange = { athleteDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedAthlete?.username ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Przypisz do zawodnika (opcjonalnie)") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = athleteDropdownExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VbtTeal,
                            focusedLabelColor = VbtTeal
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = athleteDropdownExpanded,
                        onDismissRequest = { athleteDropdownExpanded = false }
                    ) {
                        uiState.availableAthletes.forEach { athlete ->
                            DropdownMenuItem(
                                text = { Text(athlete.username) },
                                onClick = {
                                    viewModel.updateAssignedToId(athlete.id)
                                    athleteDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Szablon")
                    Switch(
                        checked = uiState.isTemplate,
                        onCheckedChange = { viewModel.updateIsTemplate(it) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "ĆWICZENIA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = VbtTextSecondary
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Dodane ćwiczenia (${uiState.exercises.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { showExercisePicker = true },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dodaj ćwiczenie", fontSize = 12.sp)
                    }
                }
            }

            itemsIndexed(uiState.exercises) { idx, exercise ->
                ExerciseEditCard(
                    exercise = exercise,
                    exerciseIdx = idx,
                    availableExercises = uiState.availableExercises,
                    onUpdateExercise = { exerciseId, exerciseName ->
                        viewModel.updateExercise(idx, exerciseId, exerciseName)
                    },
                    onAddSet = { viewModel.addSet(idx) },
                    onRemoveSet = { setIdx -> viewModel.removeSet(idx, setIdx) },
                    onUpdateSet = { setIdx, field, value ->
                        viewModel.updateSet(idx, setIdx, field, value)
                    },
                    onRemoveExercise = { viewModel.removeExercise(idx) },
                    onCreateNewExercise = { showNewExerciseDialog = true }
                )
            }

            if (uiState.exercises.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Dodaj ćwiczenia do tego planu",
                            color = VbtTextSecondary
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.8f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(uiState.error!!)
            }
        }
    }

    if (showExercisePicker) {
        ExercisePickerDialog(
            exercises = uiState.availableExercises,
            onDismiss = { showExercisePicker = false },
            onSelect = { exercise ->
                viewModel.addExercise()
                val lastIdx = uiState.exercises.size
                viewModel.updateExercise(lastIdx, exercise.id, exercise.name)
                showExercisePicker = false
            },
            onCreateNew = {
                showExercisePicker = false
                showNewExerciseDialog = true
            }
        )
    }

    if (showNewExerciseDialog) {
        NewExerciseDialog(
            onDismiss = { showNewExerciseDialog = false },
            onCreateNewExercise = { name, category ->
                viewModel.createNewExercise(name, category)
                showNewExerciseDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditCard(
    exercise: PlanExerciseState,
    exerciseIdx: Int,
    availableExercises: List<com.vbt.app.data.remote.ExerciseDto>,
    onUpdateExercise: (Int, String) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onUpdateSet: (Int, String, Any) -> Unit,
    onRemoveExercise: () -> Unit,
    onCreateNewExercise: () -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = VbtSurface,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = exercise.exerciseName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Wybierz ćwiczenie") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VbtTeal,
                            focusedLabelColor = VbtTeal
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VbtSurface)
                    ) {
                        val grouped = availableExercises
                            .sortedWith(compareBy({ categoryOrder(it.category) }, { it.name }))
                            .groupBy { it.category }
                        grouped.forEach { (category, list) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        categoryLabel(category),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VbtTeal,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                },
                                onClick = {},
                                enabled = false
                            )
                            list.forEach { exerciseDto ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                exerciseDto.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (exerciseDto.mvt != null) {
                                                Text(
                                                    "MVT ${exerciseDto.mvt}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = VbtTextSecondary
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onUpdateExercise(exerciseDto.id, exerciseDto.name)
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onCreateNewExercise,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("+ Nowe", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "SERIE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
                color = VbtTextSecondary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Set", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(30.dp))
                Text("Reps", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(50.dp))
                Text("kg", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(50.dp))
                Text("Rest (s)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("", modifier = Modifier.width(30.dp))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            exercise.sets.forEachIndexed { setIdx, set ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${set.setNumber}",
                        modifier = Modifier.width(30.dp),
                        style = MaterialTheme.typography.bodySmall
                    )

                    // Lokalny stan tekstu: pole musi dać się wyczyścić i przyjmować
                    // stany pośrednie ("", "12.") w trakcie pisania - wcześniej
                    // value było brane wprost z modelu i przy każdym nieparsowalnym
                    // wpisie pole "wracało" do starej wartości, walcząc z użytkownikiem.
                    var repsText by remember(set.setNumber, set.reps) { mutableStateOf(set.reps.toString()) }
                    var loadText by remember(set.setNumber) { mutableStateOf(formatKg(set.loadKg)) }
                    var restText by remember(set.setNumber, set.restSeconds) { mutableStateOf(set.restSeconds.toString()) }

                    OutlinedTextField(
                        value = repsText,
                        onValueChange = { value ->
                            repsText = value.filter { it.isDigit() }.take(3)
                            repsText.toIntOrNull()?.let { onUpdateSet(setIdx, "reps", it) }
                        },
                        modifier = Modifier.width(56.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VbtTeal,
                            focusedLabelColor = VbtTeal
                        )
                    )

                    OutlinedTextField(
                        value = loadText,
                        onValueChange = { value ->
                            // Polska klawiatura dziesiętna daje przecinek - normalizuj do kropki
                            loadText = value.replace(',', '.').filter { it.isDigit() || it == '.' }.take(6)
                            loadText.toFloatOrNull()?.let { onUpdateSet(setIdx, "loadKg", it) }
                        },
                        modifier = Modifier.width(68.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VbtTeal,
                            focusedLabelColor = VbtTeal
                        )
                    )

                    OutlinedTextField(
                        value = restText,
                        onValueChange = { value ->
                            restText = value.filter { it.isDigit() }.take(4)
                            restText.toIntOrNull()?.let { onUpdateSet(setIdx, "restSeconds", it) }
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VbtTeal,
                            focusedLabelColor = VbtTeal
                        )
                    )

                    IconButton(
                        onClick = { onRemoveSet(setIdx) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            "Usuń",
                            modifier = Modifier.size(18.dp),
                            tint = VbtError
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onAddSet,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = VbtTeal
                )
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Dodaj serię")
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRemoveExercise,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = VbtError
                )
            ) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = VbtError)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Usuń ćwiczenie", color = VbtError)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerDialog(
    exercises: List<com.vbt.app.data.remote.ExerciseDto>,
    onDismiss: () -> Unit,
    onSelect: (com.vbt.app.data.remote.ExerciseDto) -> Unit,
    onCreateNew: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Wybierz ćwiczenie", fontWeight = FontWeight.Bold)
                TextButton(onClick = onCreateNew) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nowe")
                }
            }
        },
        text = {
            LazyColumn {
                val grouped = exercises
                    .sortedWith(compareBy({ categoryOrder(it.category) }, { it.name }))
                    .groupBy { it.category }
                grouped.forEach { (category, list) ->
                    item {
                        Text(
                            categoryLabel(category),
                            style = MaterialTheme.typography.labelMedium,
                            color = VbtTeal,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    list.forEach { exercise ->
                        item {
                            TextButton(
                                onClick = { onSelect(exercise) },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        exercise.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (exercise.mvt != null) {
                                        Text(
                                            "MVT ${exercise.mvt}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VbtTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewExerciseDialog(
    onDismiss: () -> Unit,
    onCreateNewExercise: (String, String) -> Unit
) {
    var exerciseName by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("strength") }

    val categories = listOf(
        "olympic" to "Olimpijskie",
        "strength" to "Siłowe",
        "ballistic" to "Balistyczne",
        "auxiliary" to "Pomocnicze"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Utwórz nowe ćwiczenie", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = exerciseName,
                    onValueChange = { exerciseName = it },
                    label = { Text("Nazwa ćwiczenia") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VbtTeal,
                        focusedLabelColor = VbtTeal
                    )
                )

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = categories.find { it.first == selectedCategory }?.second ?: selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategoria") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VbtTeal,
                            focusedLabelColor = VbtTeal
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedCategory = value
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (exerciseName.isNotBlank()) {
                        onCreateNewExercise(exerciseName, selectedCategory)
                    }
                }
            ) {
                Text("Utwórz", color = VbtTeal, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}
