package com.vbt.app.ui.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vbt.app.data.remote.CalendarEntryDto
import com.vbt.app.ui.theme.VbtSurface
import com.vbt.app.ui.theme.VbtSurfaceVariant
import com.vbt.app.ui.theme.VbtTeal
import com.vbt.app.ui.theme.VbtTextPrimary
import com.vbt.app.ui.theme.VbtTextSecondary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val ATHLETE_COLORS = listOf(
    Color(0xFF7C3AED), Color(0xFF06B6D4), Color(0xFFEC4899),
    Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFF6366F1), Color(0xFFF97316)
)

fun getAthleteColor(athleteId: Int) = ATHLETE_COLORS[athleteId % ATHLETE_COLORS.size]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onStartWorkout: (planId: Int?, calendarEntryId: Int, athleteId: Int) -> Unit = { _, _, _ -> },
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan Treningowy") },
                actions = {
                    IconButton(onClick = { viewModel.openAddModal(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }) {
                        Icon(Icons.Default.Add, "Dodaj trening", tint = VbtTeal)
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
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadData() }) { Text("Spróbuj ponownie") }
                }
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    // Week navigation
                    WeekNavigationBar(
                        weekStart = state.weekStart,
                        onPrevWeek = { viewModel.prevWeek() },
                        onNextWeek = { viewModel.nextWeek() },
                        onGoToday = { viewModel.goToToday() }
                    )

                    // Athlete filter chips (coach only)
                    if (state.isCoach && state.athletes.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = state.filterAthleteId == null,
                                    onClick = { viewModel.setAthleteFilter(null) },
                                    label = { Text("Wszyscy") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VbtTeal,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                            items(state.athletes) { athlete ->
                                FilterChip(
                                    selected = state.filterAthleteId == athlete.id,
                                    onClick = { viewModel.setAthleteFilter(athlete.id) },
                                    label = { Text(athlete.username) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VbtTeal,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // List of days with entries
                    val today = LocalDate.now()
                    val weekDays = (0..6).map { state.weekStart.plusDays(it.toLong()) }
                    val filteredEntries = state.entries.let { list ->
                        if (state.filterAthleteId != null) list.filter { it.athleteId == state.filterAthleteId }
                        else list
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        weekDays.forEach { day ->
                            val dateStr = day.format(DateTimeFormatter.ISO_DATE)
                            val dayEntries = filteredEntries.filter { it.date == dateStr }
                            val isToday = day == today

                            item(key = dateStr) {
                                DaySection(
                                    day = day,
                                    entries = dayEntries,
                                    athletes = state.athletes,
                                    isCoach = state.isCoach,
                                    isToday = isToday,
                                    onAddClick = { viewModel.openAddModal(dateStr) },
                                    onEditClick = { viewModel.openEditModal(it) }
                                )
                            }
                        }
                    }
                }
            }

            // Add/Edit Modal
            if (state.showAddModal) {
                AddEditEntryModal(
                    state = state,
                    onClose = { viewModel.closeModal() },
                    onSave = { viewModel.saveEntry() },
                    onDelete = { viewModel.deleteEntry(state.editingEntryId!!) },
                    onStartWorkout = { planId ->
                        viewModel.closeModal()
                        onStartWorkout(planId, state.editingEntryId ?: -1, state.formAthleteId ?: -1)
                    },
                    onDateChange = { viewModel.updateFormDate(it) },
                    onAthleteChange = { viewModel.updateFormAthleteId(it) },
                    onPlanChange = { viewModel.updateFormPlanId(it) },
                    onTitleChange = { viewModel.updateFormTitle(it) },
                    onTimeSlotChange = { viewModel.updateFormTimeSlot(it) },
                    onNotesChange = { viewModel.updateFormNotes(it) },
                    onStatusChange = { viewModel.updateFormStatus(it) }
                )
            }
        }
    }
}

@Composable
private fun WeekNavigationBar(
    weekStart: LocalDate,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onGoToday: () -> Unit
) {
    val weekEnd = weekStart.plusDays(6)
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale("pl"))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevWeek) {
            Icon(Icons.Default.KeyboardArrowLeft, "Poprzedni tydzień", tint = VbtTeal)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${weekStart.format(fmt)} – ${weekEnd.format(fmt)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VbtTextPrimary
            )
            TextButton(onClick = onGoToday, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("Dziś", color = VbtTeal, fontSize = 12.sp)
            }
        }

        IconButton(onClick = onNextWeek) {
            Icon(Icons.Default.KeyboardArrowRight, "Następny tydzień", tint = VbtTeal)
        }
    }
}

@Composable
private fun DaySection(
    day: LocalDate,
    entries: List<CalendarEntryDto>,
    athletes: List<com.vbt.app.data.remote.UserDto>,
    isCoach: Boolean,
    isToday: Boolean,
    onAddClick: () -> Unit,
    onEditClick: (CalendarEntryDto) -> Unit
) {
    val polishDays = listOf("Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota", "Niedziela")
    val dayName = polishDays[(day.dayOfWeek.value - 1) % 7]
    val dateFmt = DateTimeFormatter.ofPattern("d MMMM", Locale("pl"))

    Column(modifier = Modifier.fillMaxWidth()) {
        // Day header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isToday) VbtTeal.copy(alpha = 0.15f) else Color.Transparent,
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isToday) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(VbtTeal, RoundedCornerShape(50))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column {
                    Text(
                        dayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) VbtTeal else VbtTextPrimary
                    )
                    Text(
                        day.format(dateFmt),
                        style = MaterialTheme.typography.bodySmall,
                        color = VbtTextSecondary
                    )
                }
            }

            IconButton(
                onClick = onAddClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Add, "Dodaj", tint = VbtTeal, modifier = Modifier.size(18.dp))
            }
        }

        if (entries.isEmpty()) {
            // Empty day placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .background(VbtSurface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Brak zaplanowanych treningów",
                    style = MaterialTheme.typography.bodySmall,
                    color = VbtTextSecondary.copy(alpha = 0.5f)
                )
            }
        } else {
            entries.forEach { entry ->
                Spacer(modifier = Modifier.height(4.dp))
                EntryListCard(
                    entry = entry,
                    athleteName = athletes.find { it.id == entry.athleteId }?.username,
                    isCoach = isCoach,
                    onClick = { onEditClick(entry) }
                )
            }
        }
    }
}

@Composable
private fun EntryListCard(
    entry: CalendarEntryDto,
    athleteName: String?,
    isCoach: Boolean,
    onClick: () -> Unit
) {
    val statusColor = when (entry.status) {
        "completed" -> Color(0xFF10B981)
        "skipped" -> Color(0xFF6B7280)
        else -> VbtTeal
    }
    val statusLabel = when (entry.status) {
        "completed" -> "Wykonany"
        "skipped" -> "Pominięty"
        else -> "Zaplanowany"
    }
    val athleteColor = getAthleteColor(entry.athleteId)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = VbtSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left color bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .background(statusColor, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = VbtTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            statusLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCoach && athleteName != null) {
                        Text(
                            athleteName,
                            style = MaterialTheme.typography.bodySmall,
                            color = athleteColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (entry.timeSlot != null) {
                        Text(
                            entry.timeSlot,
                            style = MaterialTheme.typography.bodySmall,
                            color = VbtTextSecondary
                        )
                    }
                    if (entry.notes != null) {
                        Text(
                            entry.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = VbtTextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEntryModal(
    state: ScheduleUiState,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onStartWorkout: (planId: Int?) -> Unit = {},
    onDateChange: (String) -> Unit,
    onAthleteChange: (Int?) -> Unit,
    onPlanChange: (Int?) -> Unit,
    onTitleChange: (String) -> Unit,
    onTimeSlotChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onStatusChange: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                if (state.editingEntryId != null) "Edytuj trening" else "Dodaj trening",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.isCoach) {
                    item {
                        Text("Zawodnik *", style = MaterialTheme.typography.labelSmall)
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                            TextField(
                                value = state.athletes.find { it.id == state.formAthleteId }?.username ?: "Wybierz zawodnika",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                colors = ExposedDropdownMenuDefaults.textFieldColors()
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                state.athletes.forEach { athlete ->
                                    DropdownMenuItem(
                                        text = { Text(athlete.username) },
                                        onClick = { onAthleteChange(athlete.id); expanded = false }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Plan (opcjonalnie)", style = MaterialTheme.typography.labelSmall)
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        TextField(
                            value = state.plans.find { it.id == state.formPlanId }?.name ?: "Brak",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = ExposedDropdownMenuDefaults.textFieldColors()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("Brak") }, onClick = { onPlanChange(null); expanded = false })
                            state.plans.forEach { plan ->
                                DropdownMenuItem(
                                    text = { Text(plan.name) },
                                    onClick = { onPlanChange(plan.id); expanded = false }
                                )
                            }
                        }
                    }
                }

                item {
                    Text("Tytuł *", style = MaterialTheme.typography.labelSmall)
                    TextField(
                        value = state.formTitle,
                        onValueChange = onTitleChange,
                        placeholder = { Text("np. Siłownia A") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    Text("Data", style = MaterialTheme.typography.labelSmall)
                    TextField(
                        value = state.formDate,
                        onValueChange = onDateChange,
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    Text("Godzina", style = MaterialTheme.typography.labelSmall)
                    TextField(
                        value = state.formTimeSlot,
                        onValueChange = onTimeSlotChange,
                        placeholder = { Text("np. 18:00") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    Text("Notatki", style = MaterialTheme.typography.labelSmall)
                    TextField(
                        value = state.formNotes,
                        onValueChange = onNotesChange,
                        placeholder = { Text("Opcjonalne notatki...") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                        maxLines = 3
                    )
                }

                if (state.editingEntryId != null) {
                    item {
                        Text("Status", style = MaterialTheme.typography.labelSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("scheduled" to "Zaplanowany", "completed" to "Wykonany", "skipped" to "Pominięty").forEach { (status, label) ->
                                Button(
                                    onClick = { onStatusChange(status) },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (state.formStatus == status) VbtTeal else VbtSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(label, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.editingEntryId != null) {
                    Button(
                        onClick = { onStartWorkout(state.formPlanId) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rozpocznij trening")
                    }
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
                ) {
                    Text("Zapisz")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.editingEntryId != null) {
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)).brush
                            )
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Usuń")
                        }
                    }
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Anuluj")
                    }
                }
            }
        },
        dismissButton = {}
    )
}
