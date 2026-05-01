package com.vbt.app.ui.screen.athletes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vbt.app.data.remote.CalendarEntryDto
import com.vbt.app.data.remote.TrainingPlanDto
import com.vbt.app.data.remote.UserDto
import com.vbt.app.data.remote.WorkoutSessionDto
import com.vbt.app.ui.theme.VbtSuccess
import com.vbt.app.ui.theme.VbtSurface
import com.vbt.app.ui.theme.VbtSurfaceVariant
import com.vbt.app.ui.theme.VbtTeal
import com.vbt.app.ui.theme.VbtError
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthleteProfileScreen(
    athleteId: Int,
    onBack: () -> Unit,
    onSessionDetail: (Int) -> Unit,
    viewModel: AthleteProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.athlete?.username ?: "Profil Zawodnika")
                        val athlete = uiState.athlete
                        if (athlete != null) {
                            Text(
                                text = if (athlete.isActive) "Aktywny" else "Nieaktywny",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: "Błąd",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            uiState.athlete != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Stats row
                    AthleteStatsRow(uiState.athlete!!, uiState.assignedPlans, uiState.sessions)

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // Tabs
                    TabRow(
                        selectedTabIndex = uiState.selectedTab.ordinal
                    ) {
                        AthleteProfileTab.values().forEachIndexed { index, tab ->
                            Tab(
                                selected = uiState.selectedTab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                text = {
                                    Text(
                                        when (tab) {
                                            AthleteProfileTab.CALENDAR -> "Kalendarz"
                                            AthleteProfileTab.PLANS -> "Plany"
                                            AthleteProfileTab.SESSIONS -> "Sesje"
                                        }
                                    )
                                }
                            )
                        }
                    }

                    // Tab content
                    when (uiState.selectedTab) {
                        AthleteProfileTab.CALENDAR -> {
                            CalendarTabContent(
                                calendarEntries = uiState.calendarEntries,
                                allPlans = uiState.allPlans,
                                onOpenAddEntry = { date -> viewModel.openAddEntry(date) },
                                onOpenEditEntry = { entry -> viewModel.openEditEntry(entry) }
                            )
                        }

                        AthleteProfileTab.PLANS -> {
                            PlansTabContent(
                                assignedPlans = uiState.assignedPlans,
                                onShowAssignModal = { viewModel.showAssignModal() }
                            )
                        }

                        AthleteProfileTab.SESSIONS -> {
                            SessionsTabContent(
                                sessions = uiState.sessions,
                                onSessionDetail = onSessionDetail
                            )
                        }
                    }
                }
            }
        }
    }

    // Calendar Entry Modal
    if (uiState.showEntryModal) {
        CalendarEntryModal(
            entry = uiState.editingEntry,
            entryDate = uiState.entryDate,
            entryTitle = uiState.entryTitle,
            entryPlanId = uiState.entryPlanId,
            entryTimeSlot = uiState.entryTimeSlot,
            entryNotes = uiState.entryNotes,
            allPlans = uiState.allPlans,
            onDateChange = { viewModel.updateEntryDate(it) },
            onTitleChange = { viewModel.updateEntryTitle(it) },
            onPlanIdChange = { viewModel.updateEntryPlanId(it) },
            onTimeSlotChange = { viewModel.updateEntryTimeSlot(it) },
            onNotesChange = { viewModel.updateEntryNotes(it) },
            onSave = { viewModel.saveEntry() },
            onDelete = { entryId ->
                viewModel.deleteEntry(entryId)
            },
            onDismiss = { viewModel.closeEntryModal() }
        )
    }

    // Assign Plan Modal
    if (uiState.showAssignModal) {
        AssignPlanModal(
            allPlans = uiState.allPlans,
            assignedPlanIds = uiState.assignedPlans.map { it.id },
            selectedPlanId = uiState.selectedPlanToAssign,
            onSelectPlan = { viewModel.selectPlanToAssign(it) },
            onAssign = { viewModel.assignPlan() },
            onDismiss = { viewModel.hideAssignModal() }
        )
    }
}

@Composable
private fun AthleteStatsRow(
    athlete: UserDto,
    assignedPlans: List<TrainingPlanDto>,
    sessions: List<WorkoutSessionDto>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatBox(
            label = "Przypisane Plany",
            value = assignedPlans.size.toString(),
            modifier = Modifier.weight(1f)
        )
        StatBox(
            label = "Sesje",
            value = sessions.size.toString(),
            modifier = Modifier.weight(1f)
        )
        val todayCount = sessions.count { session ->
            val today = LocalDate.now()
            val sessionDate = try {
                LocalDate.parse(session.startedAt.substring(0, 10))
            } catch (e: Exception) {
                null
            }
            sessionDate == today
        }
        StatBox(
            label = "Dziś",
            value = todayCount.toString(),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = VbtSurfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CalendarTabContent(
    calendarEntries: List<CalendarEntryDto>,
    allPlans: List<TrainingPlanDto>,
    onOpenAddEntry: (String) -> Unit,
    onOpenEditEntry: (CalendarEntryDto) -> Unit
) {
    val today = LocalDate.now()
    val firstDayOfMonth = today.withDayOfMonth(1)
    val daysInMonth = today.lengthOfMonth()

    val days = mutableListOf<LocalDate?>()
    // Fill first week padding
    val startingDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7
    repeat(startingDayOfWeek) { days.add(null) }
    // Add all days of month
    repeat(daysInMonth) { days.add(firstDayOfMonth.plusDays(it.toLong())) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        val weeks = days.chunked(7)
        items(weeks) { week ->
            CalendarWeek(
                week = week,
                entries = calendarEntries,
                onOpenAddEntry = onOpenAddEntry,
                onOpenEditEntry = onOpenEditEntry
            )
        }
    }
}

@Composable
private fun CalendarWeek(
    week: List<LocalDate?>,
    entries: List<CalendarEntryDto>,
    onOpenAddEntry: (String) -> Unit,
    onOpenEditEntry: (CalendarEntryDto) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val dayNames = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "Sb", "Nd")

        week.forEachIndexed { index, date ->
            CalendarDayCell(
                dayName = dayNames.getOrNull(index % 7) ?: "",
                date = date,
                entries = entries,
                onOpenAddEntry = onOpenAddEntry,
                onOpenEditEntry = onOpenEditEntry,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayName: String,
    date: LocalDate?,
    entries: List<CalendarEntryDto>,
    onOpenAddEntry: (String) -> Unit,
    onOpenEditEntry: (CalendarEntryDto) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable(enabled = date != null) {
                date?.let { onOpenAddEntry(it.format(DateTimeFormatter.ISO_DATE)) }
            },
        color = if (date != null) VbtSurface else VbtSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (date != null) {
                Text(
                    text = dayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                // Show entries for this day
                val dayEntries = entries.filter {
                    it.date == date.format(DateTimeFormatter.ISO_DATE)
                }

                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(dayEntries.take(2)) { entry ->
                        CalendarEntryChip(
                            entry = entry,
                            onClick = { onOpenEditEntry(entry) }
                        )
                    }
                    if (dayEntries.size > 2) {
                        item {
                            Text(
                                text = "+${dayEntries.size - 2}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarEntryChip(
    entry: CalendarEntryDto,
    onClick: () -> Unit
) {
    val bgColor = when (entry.status) {
        "scheduled" -> VbtTeal
        "completed" -> VbtSuccess
        else -> VbtSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = bgColor, shape = RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Text(
            text = entry.title.take(8),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            color = androidx.compose.ui.graphics.Color.White
        )
    }
}

@Composable
private fun PlansTabContent(
    assignedPlans: List<TrainingPlanDto>,
    onShowAssignModal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = onShowAssignModal,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Przypisz Plan")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (assignedPlans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Brak przypisanych planów",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(assignedPlans) { plan ->
                    AssignedPlanCard(plan = plan)
                }
            }
        }
    }
}

@Composable
private fun AssignedPlanCard(plan: TrainingPlanDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VbtSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = plan.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Ćwiczenia: ${plan.exercises.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
            ) {
                Text("Zaplanuj w kalendarzu")
            }
        }
    }
}

@Composable
private fun SessionsTabContent(
    sessions: List<WorkoutSessionDto>,
    onSessionDetail: (Int) -> Unit
) {
    if (sessions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Brak sesji treningowych",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        val groupedSessions = sessions.groupBy { session ->
            formatDatePolish(session.startedAt)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            groupedSessions.forEach { (dateLabel, daySessions) ->
                item {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(daySessions) { session ->
                    SessionListCard(
                        session = session,
                        onClick = { onSessionDetail(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionListCard(
    session: WorkoutSessionDto,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = VbtSurface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = formatDateTimePolish(session.startedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val repCount = session.reps?.size ?: 0
                Text(
                    text = "$repCount powtórzeń",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarEntryModal(
    entry: CalendarEntryDto?,
    entryDate: String,
    entryTitle: String,
    entryPlanId: Int?,
    entryTimeSlot: String,
    entryNotes: String,
    allPlans: List<TrainingPlanDto>,
    onDateChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onPlanIdChange: (Int?) -> Unit,
    onTimeSlotChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var planExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry != null) "Edytuj Wpis" else "Dodaj Wpis") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = entryDate,
                    onValueChange = onDateChange,
                    label = { Text("Data") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = entryTitle,
                    onValueChange = onTitleChange,
                    label = { Text("Tytuł") },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = planExpanded,
                    onExpandedChange = { planExpanded = !planExpanded }
                ) {
                    OutlinedTextField(
                        value = allPlans.find { it.id == entryPlanId }?.name ?: "Brak planu",
                        onValueChange = { },
                        label = { Text("Plan") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = planExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = planExpanded,
                        onDismissRequest = { planExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Brak planu") },
                            onClick = {
                                onPlanIdChange(null)
                                planExpanded = false
                            }
                        )
                        allPlans.forEach { plan ->
                            DropdownMenuItem(
                                text = { Text(plan.name) },
                                onClick = {
                                    onPlanIdChange(plan.id)
                                    planExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = entryTimeSlot,
                    onValueChange = onTimeSlotChange,
                    label = { Text("Godzina") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = entryNotes,
                    onValueChange = onNotesChange,
                    label = { Text("Notatki") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
            ) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            if (entry != null) {
                Button(
                    onClick = { onDelete(entry.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = VbtError)
                ) {
                    Text("Usuń")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Anuluj")
                }
            }
        }
    )
}

@Composable
private fun AssignPlanModal(
    allPlans: List<TrainingPlanDto>,
    assignedPlanIds: List<Int>,
    selectedPlanId: Int?,
    onSelectPlan: (Int) -> Unit,
    onAssign: () -> Unit,
    onDismiss: () -> Unit
) {
    val availablePlans = allPlans.filter { it.id !in assignedPlanIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Przypisz Plan") },
        text = {
            if (availablePlans.isEmpty()) {
                Text("Wszystkie dostępne plany są już przypisane")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availablePlans) { plan ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPlan(plan.id) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = { onSelectPlan(plan.id) },
                                label = {
                                    Text(
                                        plan.name,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAssign,
                enabled = selectedPlanId != null,
                colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
            ) {
                Text("Przypisz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}

private fun formatDatePolish(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(dateString) ?: Date()
        val outputFormat = SimpleDateFormat("EEEE, dd MMM yyyy", Locale("pl", "PL"))
        outputFormat.format(date)
    } catch (e: Exception) {
        dateString
    }
}

private fun formatDateTimePolish(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(dateString) ?: Date()
        val outputFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("pl", "PL"))
        outputFormat.format(date)
    } catch (e: Exception) {
        dateString
    }
}
