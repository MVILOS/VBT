package com.vbt.app.ui.screen.plans

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vbt.app.data.remote.TrainingPlanDto
import com.vbt.app.data.remote.UserDto
import com.vbt.app.ui.theme.VbtSurface
import com.vbt.app.ui.theme.VbtTeal
import com.vbt.app.ui.theme.VbtError
import com.vbt.app.ui.theme.VbtPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanListScreen(
    onCreatePlan: () -> Unit,
    onEditPlan: (Int) -> Unit,
    onStartWorkout: (TrainingPlanDto) -> Unit,
    viewModel: PlanListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<TrainingPlanDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plany Treningowe") },
                actions = {
                    if (uiState.isCoach) {
                        IconButton(onClick = onCreatePlan) {
                            Icon(Icons.Default.Add, contentDescription = "Utwórz plan")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.plans.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Brak planów treningowych",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (uiState.isCoach) {
                        Text(
                            "Naciśnij + aby utworzyć plan",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(uiState.plans) { plan ->
                        PlanCard(
                            plan = plan,
                            athletes = uiState.athletes,
                            isCoach = uiState.isCoach,
                            onStart = { onStartWorkout(plan) },
                            onEdit = { onEditPlan(plan.id) },
                            onDelete = { showDeleteDialog = plan }
                        )
                    }
                }
            }

            if (!uiState.error.isNullOrEmpty()) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    containerColor = VbtError
                ) {
                    Text(uiState.error ?: "")
                }
            }
        }
    }

    showDeleteDialog?.let { plan ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Usuń plan") },
            text = { Text("Czy chcesz usunąć plan \"${plan.name}\"? Tej akcji nie można cofnąć.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlan(plan.id)
                    showDeleteDialog = null
                }) {
                    Text("Usuń", color = VbtError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Anuluj")
                }
            }
        )
    }
}

@Composable
fun PlanCard(
    plan: TrainingPlanDto,
    athletes: List<UserDto>,
    isCoach: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        color = VbtSurface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (!plan.description.isNullOrEmpty()) {
                        Text(
                            text = plan.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            if (plan.isTemplate) "Szablon" else "Plan",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (plan.isTemplate) VbtPurple else VbtTeal
                    )
                )

                if (isCoach && plan.assignedTo != null) {
                    val athleteName = athletes.find { it.id == plan.assignedTo }?.username ?: "Nieznany"
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                "Przypisany do: $athleteName",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Ćwiczenia: ${plan.exercises.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalArrangement = if (isCoach) Arrangement.spacedBy(8.dp) else Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCoach) {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start")
                    }

                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edytuj")
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Usuń",
                            tint = VbtError,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start")
                    }
                }
            }
        }
    }
}
