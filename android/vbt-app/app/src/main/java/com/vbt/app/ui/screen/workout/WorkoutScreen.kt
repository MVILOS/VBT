package com.vbt.app.ui.screen.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vbt.app.data.ble.BleConnectionState
import com.vbt.app.data.local.entity.RepResultEntity
import com.vbt.app.data.remote.ExerciseDto
import com.vbt.app.data.remote.UserDto
import com.vbt.app.domain.model.VelocityZone
import com.vbt.app.ui.theme.VbtBackground
import com.vbt.app.ui.theme.VbtTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    onNavigateBack: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showWeightNumpad by remember { mutableStateOf(false) }
    var showFinishConfirm by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = VbtBackground
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                when (state.mode) {
                                    WorkoutMode.IDLE -> "VBT Trening"
                                    WorkoutMode.SESSION_SELECT -> "Wybierz zawodnika"
                                    WorkoutMode.EXERCISE_PICKER -> "Wybierz ćwiczenie"
                                    WorkoutMode.ACTIVE -> state.currentExerciseName
                                    WorkoutMode.FINISHED -> "Trening ukończony"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (state.sessionAthleteName != null && state.mode != WorkoutMode.IDLE) {
                                Text(
                                    "dla ${state.sessionAthleteName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                when (state.mode) {
                                    WorkoutMode.IDLE, WorkoutMode.EXERCISE_PICKER, WorkoutMode.SESSION_SELECT -> {
                                        onNavigateBack()
                                    }
                                    WorkoutMode.ACTIVE -> {
                                        showFinishConfirm = true
                                    }
                                    WorkoutMode.FINISHED -> {
                                        onNavigateBack()
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz")
                        }
                    },
                    actions = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    when (state.connectionState) {
                                        BleConnectionState.CONNECTED -> Color(0xFF4CAF50)
                                        BleConnectionState.CONNECTING -> Color(0xFFFF9800)
                                        else -> Color(0xFFF44336)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                )
            },
            bottomBar = {
                if (state.mode == WorkoutMode.ACTIVE) {
                    BottomAppBar(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 4.dp,
                        modifier = Modifier.height(72.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.togglePause() },
                                modifier = Modifier.weight(0.9f),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                colors = if (state.isPaused)
                                    ButtonDefaults.outlinedButtonColors(contentColor = VbtTeal)
                                else
                                    ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Icon(
                                    if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(if (state.isPaused) "Wznów" else "Pauza", maxLines = 1, fontSize = 10.sp)
                            }

                            OutlinedButton(
                                onClick = { showWeightNumpad = true },
                                modifier = Modifier.weight(0.55f),
                                contentPadding = PaddingValues(horizontal = 3.dp)
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("kg", maxLines = 1, fontSize = 10.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.requestExerciseChange() },
                                modifier = Modifier.weight(0.8f),
                                contentPadding = PaddingValues(horizontal = 3.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VbtTeal)
                            ) {
                                Icon(Icons.Default.FitnessCenter, null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Ćw.", maxLines = 1, fontSize = 10.sp)
                            }

                            Button(
                                onClick = { viewModel.finishSet() },
                                modifier = Modifier.weight(0.9f),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
                            ) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Seria+", maxLines = 1, fontSize = 10.sp)
                            }

                            Button(
                                onClick = { showFinishConfirm = true },
                                modifier = Modifier.weight(0.9f),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                            ) {
                                Icon(Icons.Default.Stop, null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Koniec", maxLines = 1, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Reconnecting banner
                if (state.connectionState != BleConnectionState.CONNECTED && state.mode == WorkoutMode.ACTIVE) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFFF9800)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (state.connectionState == BleConnectionState.CONNECTING ||
                                    state.connectionState == BleConnectionState.RECONNECTING
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (state.connectionState == BleConnectionState.RECONNECTING)
                                            "Wznawianie połączenia..."
                                        else
                                            "Łączenie...",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Warning,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Brak połączenia ESP32", color = Color.White, fontSize = 12.sp)
                                }
                            }
                            if (state.connectionState == BleConnectionState.DISCONNECTED) {
                                TextButton(
                                    onClick = { viewModel.reconnectBle() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "Połącz ponownie",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Paused banner
                if (state.isPaused && state.mode == WorkoutMode.ACTIVE) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF9C27B0)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Pause, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Pauza - zmień obciążenie",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Auto-finish countdown banner (cel serii osiągnięty)
                if (state.autoFinishCountdown != null && state.mode == WorkoutMode.ACTIVE) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = VbtTeal
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Cel osiągnięty - koniec serii za ${state.autoFinishCountdown} s",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(
                                onClick = { viewModel.cancelAutoFinish() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "Anuluj",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Content based on mode
                when (state.mode) {
                    WorkoutMode.IDLE -> {
                        if (state.isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = VbtTeal)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Ładowanie planu...", color = VbtTeal)
                                }
                            }
                        } else {
                            WorkoutIdleContent(
                                onStartSession = { viewModel.startSession() }
                            )
                        }
                    }

                    WorkoutMode.SESSION_SELECT -> {
                        AthleteSelectPanel(
                            athletes = state.availableAthletes,
                            onSelectAthlete = { athlete ->
                                viewModel.selectSessionAthlete(athlete)
                            },
                            onSelectSelf = {
                                viewModel.selectSessionAthlete(null)
                            }
                        )
                    }

                    WorkoutMode.EXERCISE_PICKER -> {
                        ExercisePickerPanel(
                            exercises = state.availableExercises,
                            availablePlans = state.availablePlans,
                            isLoading = state.isLoading,
                            onSelectFreestyle = { exercise, load ->
                                viewModel.startFreestyle(exercise, load)
                            },
                            onLoadPlans = {
                                viewModel.loadAvailablePlans()
                            },
                            onSelectPlan = { plan ->
                                viewModel.startPlanWorkout(plan)
                            }
                        )
                    }

                    WorkoutMode.ACTIVE -> {
                        WorkoutActiveContent(
                            state = state
                        )
                    }

                    WorkoutMode.FINISHED -> {
                        WorkoutFinishedContent(
                            state = state
                        )
                    }
                }
            } // end Column

            // Change exercise overlay (full-screen, on top of Column)
            if (state.showChangeExercise) {
                ChangeExerciseOverlay(
                    exercises = state.availableExercises,
                    onConfirm = { exercise, load -> viewModel.changeExercise(exercise, load) },
                    onCancel = { viewModel.cancelExerciseChange() }
                )
            }
            } // end Box
        }
    }

    // Weight Numpad Bottom Sheet
    if (showWeightNumpad) {
        WeightNumpad(
            currentValue = state.currentLoadKg,
            onDismiss = { showWeightNumpad = false },
            onConfirm = { newKg ->
                viewModel.editCurrentLoad(newKg)
                showWeightNumpad = false
            }
        )
    }

    // Finish Workout Dialog
    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text("Zakończyć trening?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Co chcesz zrobić z tym treningiem?")
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            viewModel.finishWorkout()
                            showFinishConfirm = false
                            onNavigateBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Zapisz trening")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.discardWorkout()
                            showFinishConfirm = false
                            onNavigateBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Odrzuć (nie zapisuj)")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }) { Text("Anuluj") }
            }
        )
    }
}

// ==================== Content Screens ====================

@Composable
private fun WorkoutIdleContent(onStartSession: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Icon(
                Icons.Default.FitnessCenter,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = VbtTeal
            )
            Text(
                "VBT Trening",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = onStartSession,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rozpocznij trening")
            }
        }
    }
}

@Composable
private fun AthleteSelectPanel(
    athletes: List<UserDto>,
    onSelectAthlete: (UserDto) -> Unit,
    onSelectSelf: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Dla kogo trening?",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = onSelectSelf,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
        ) {
            Text("Dla siebie")
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            "Zawodnicy",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(athletes) { athlete ->
                OutlinedButton(
                    onClick = { onSelectAthlete(athlete) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(athlete.username)
                }
            }
        }
    }
}

@Composable
private fun ExercisePickerPanel(
    exercises: List<ExerciseDto>,
    availablePlans: List<com.vbt.app.data.remote.TrainingPlanDto>,
    isLoading: Boolean,
    onSelectFreestyle: (ExerciseDto, Float) -> Unit,
    onLoadPlans: () -> Unit,
    onSelectPlan: (com.vbt.app.data.remote.TrainingPlanDto) -> Unit
) {
    var selectedExercise by remember { mutableStateOf<ExerciseDto?>(null) }
    var loadInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showNumpad by remember { mutableStateOf(false) }
    var freestyleMode by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val planButtonColor by animateColorAsState(
                if (!freestyleMode) VbtTeal else MaterialTheme.colorScheme.outline,
                label = "planButtonColor"
            )
            val freestyleButtonColor by animateColorAsState(
                if (freestyleMode) VbtTeal else MaterialTheme.colorScheme.outline,
                label = "freestyleButtonColor"
            )

            Button(
                onClick = { freestyleMode = false; if (availablePlans.isEmpty()) onLoadPlans() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!freestyleMode) VbtTeal else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(Icons.Default.Menu, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Z PLANU")
            }
            Button(
                onClick = { freestyleMode = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (freestyleMode) VbtTeal else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text("FREESTYLE")
            }
        }

        if (freestyleMode && selectedExercise == null) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Wyszukaj ćwiczenie") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )

            val filtered = exercises.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered) { exercise ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedExercise = exercise },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    exercise.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (exercise.category != null) {
                                    Text(
                                        exercise.category,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VbtTeal
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = VbtTeal)
                        }
                    }
                }
            }
        } else if (freestyleMode && selectedExercise != null) {
            Text(
                selectedExercise!!.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                "Obciążenie (kg)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clickable { showNumpad = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (loadInput.isEmpty()) "Dotknij, aby wybrać" else "$loadInput kg",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = VbtTeal
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { selectedExercise = null },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Wstecz")
                }
                Button(
                    onClick = {
                        loadInput.toFloatOrNull()?.let {
                            onSelectFreestyle(selectedExercise!!, it)
                        }
                    },
                    enabled = loadInput.toFloatOrNull() != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
                ) {
                    Text("Rozpocznij")
                }
            }
        } else if (!freestyleMode) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VbtTeal)
                }
            } else if (availablePlans.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Brak dostępnych planów",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onLoadPlans, colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)) {
                            Text("Odśwież")
                        }
                    }
                }
            } else {
                Text(
                    "Wybierz plan",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(availablePlans) { plan ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPlan(plan) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        plan.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (!plan.description.isNullOrBlank()) {
                                        Text(
                                            plan.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "${plan.exercises.size} ćwiczeń",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VbtTeal
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = VbtTeal)
                            }
                        }
                    }
                }
            }
        }

        if (showNumpad) {
            WeightNumpad(
                currentValue = loadInput.toFloatOrNull() ?: 0f,
                onDismiss = { showNumpad = false },
                onConfirm = { newKg ->
                    loadInput = String.format("%.1f", newKg)
                    showNumpad = false
                }
            )
        }
    }
}

@Composable
private fun WorkoutActiveContent(
    state: WorkoutUiState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Load display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Obciążenie",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${String.format("%.1f", state.currentLoadKg)} kg",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = VbtTeal
                    )
                }

                if (state.heartRate != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "HR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${state.heartRate} bpm",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                }
            }
        }

        // Velocity display (Large)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = state.velocityZone.color.copy(alpha = 0.15f)
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    state.velocityZone.color
                ).brush
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "%.2f".format(state.currentVelocity),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = state.velocityZone.color
                )
                Text(
                    "m/s",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = state.velocityZone.color,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        state.velocityZone.label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Set info
        if (state.targetReps > 0) {
            Text(
                "Seria ${state.currentSetIndex + 1} - cel: ${state.completedRepsInSet.size} / ${state.targetReps} powtórzeń × ${String.format("%.1f", state.currentLoadKg)} kg",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 12.dp),
                color = if (state.completedRepsInSet.size >= state.targetReps) VbtTeal else Color.White
            )
        }

        // Reps history
        if (state.completedRepsInSet.isNotEmpty()) {
            Text(
                "Historia powtórzeń",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                items(state.completedRepsInSet) { rep ->
                    CompactRepCard(rep = rep)
                }
            }
        }

        // Plan exercises panel (if plan mode)
        if (state.selectedPlan != null && state.selectedPlan.exercises.isNotEmpty()) {
            Text(
                "Plan ćwiczeń",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                items(state.selectedPlan.exercises.size) { index ->
                    val exercise = state.selectedPlan.exercises[index]
                    val isActive = index == state.currentExerciseIndex
                    Card(
                        modifier = Modifier
                            .width(120.dp)
                            .height(80.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive)
                                VbtTeal.copy(alpha = 0.2f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (isActive) CardDefaults.outlinedCardBorder() else null
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                exercise.exercise?.name ?: "Ćwiczenie",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${exercise.sets.size} serii",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CompactRepCard(rep: RepResultEntity) {
    val zone = VelocityZone.fromVelocity(rep.maxVelocityMs)
    Card(
        modifier = Modifier.size(width = 80.dp, height = 70.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Rep ${rep.repNumber}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "%.2f".format(rep.maxVelocityMs),
                style = MaterialTheme.typography.labelMedium,
                color = zone.color,
                fontWeight = FontWeight.Bold
            )
            Text(
                "m/s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WorkoutFinishedContent(
    state: WorkoutUiState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = VbtTeal
        )

        Text(
            "TRENING UKOŃCZONY",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = VbtTeal
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryRow("Powtórzeń", state.allReps.size.toString())
                SummaryRow("Ćwiczenie", state.currentExerciseName)
                SummaryRow("Obciążenie", "%.1f kg".format(state.currentLoadKg))
                if (state.peakVelocity > 0) {
                    SummaryRow("Szczytowa prędkość", "%.2f m/s".format(state.peakVelocity))
                }
                val durationSeconds = (System.currentTimeMillis() - state.startTime) / 1000
                val durationMinutes = durationSeconds / 60
                SummaryRow("Czas trwania", "${durationMinutes} min")
                val totalVolume = state.allReps.size * state.currentLoadKg
                SummaryRow("Objętość", String.format("%.1f kg", totalVolume))
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            fontWeight = FontWeight.Bold,
            color = VbtTeal
        )
    }
}

// ==================== Change Exercise Overlay ====================

@Composable
private fun ChangeExerciseOverlay(
    exercises: List<ExerciseDto>,
    onConfirm: (ExerciseDto, Float) -> Unit,
    onCancel: () -> Unit
) {
    var selectedExercise by remember { mutableStateOf<ExerciseDto?>(null) }
    var loadInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showNumpad by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VbtBackground)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (selectedExercise == null) "Zmień ćwiczenie" else selectedExercise!!.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Anuluj")
            }
        }

        if (selectedExercise == null) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Wyszukaj ćwiczenie") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            val filtered = exercises.filter { it.name.contains(searchQuery, ignoreCase = true) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered) { exercise ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedExercise = exercise },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exercise.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                if (exercise.category != null) {
                                    Text(exercise.category, style = MaterialTheme.typography.bodySmall, color = VbtTeal)
                                }
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = VbtTeal)
                        }
                    }
                }
            }
        } else {
            Text("Obciążenie (kg)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clickable { showNumpad = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (loadInput.isEmpty()) "Dotknij, aby wybrać" else "$loadInput kg",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = VbtTeal
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selectedExercise = null }, modifier = Modifier.weight(1f)) {
                    Text("Wstecz")
                }
                Button(
                    onClick = { loadInput.toFloatOrNull()?.let { onConfirm(selectedExercise!!, it) } },
                    enabled = loadInput.toFloatOrNull() != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
                ) {
                    Text("Rozpocznij")
                }
            }
        }
    }

    if (showNumpad) {
        WeightNumpad(
            currentValue = loadInput.toFloatOrNull() ?: 0f,
            onDismiss = { showNumpad = false },
            onConfirm = { newKg ->
                loadInput = String.format("%.1f", newKg)
                showNumpad = false
            }
        )
    }
}

// ==================== Weight Numpad Bottom Sheet ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightNumpad(
    currentValue: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var input by remember { mutableStateOf(String.format("%.1f", currentValue)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VbtBackground,
        scrimColor = Color.Black.copy(alpha = 0.32f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                "Ustaw obciążenie (kg)",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    input,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = VbtTeal
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick add buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("+2.5", "+5", "+10", "+20", "+25").forEach { increment ->
                    Button(
                        onClick = {
                            val current = input.toFloatOrNull() ?: 0f
                            input = String.format("%.1f", current + increment.drop(1).toFloat())
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
                    ) {
                        Text(increment, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Numpad 3x4
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: 7 8 9
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { i ->
                        NumpadButton(
                            label = (7 + i).toString(),
                            onClick = { input += (7 + i).toString() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Row 2: 4 5 6
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { i ->
                        NumpadButton(
                            label = (4 + i).toString(),
                            onClick = { input += (4 + i).toString() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Row 3: 1 2 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { i ->
                        NumpadButton(
                            label = (1 + i).toString(),
                            onClick = { input += (1 + i).toString() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Row 4: . 0 C
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumpadButton(
                        label = ".",
                        onClick = { if (!input.contains(".")) input += "." },
                        modifier = Modifier.weight(1f)
                    )
                    NumpadButton(
                        label = "0",
                        onClick = { input += "0" },
                        modifier = Modifier.weight(1f)
                    )
                    NumpadButton(
                        label = "C",
                        onClick = { input = "" },
                        modifier = Modifier.weight(1f),
                        isDelete = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // OK Button
            Button(
                onClick = {
                    input.toFloatOrNull()?.let { onConfirm(it) }
                },
                enabled = input.toFloatOrNull() != null && input.toFloatOrNull()!! > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
            ) {
                Text("OK", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NumpadButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDelete: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isDelete)
                Color(0xFFD32F2F)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            label,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDelete) Color.White else Color.White
        )
    }
}
