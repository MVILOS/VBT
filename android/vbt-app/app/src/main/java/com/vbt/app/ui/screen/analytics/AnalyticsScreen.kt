package com.vbt.app.ui.screen.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vbt.app.data.remote.*
import com.vbt.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
//  TOP-LEVEL SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Prędkość", "1RM", "Zmęczenie", "Tygodnie")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analityka") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(VbtBackground)
                .padding(padding)
        ) {
            // Athlete picker (coach/admin only)
            if (uiState.isCoach && uiState.athletes.isNotEmpty()) {
                AthletePicker(
                    athletes = uiState.athletes,
                    selectedId = uiState.selectedAthleteId,
                    onSelect = viewModel::selectAthlete
                )
            }

            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = VbtSurface,
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.labelMedium) },
                        selectedContentColor = VbtTeal,
                        unselectedContentColor = VbtTextSecondary
                    )
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VbtTeal)
                }
            } else {
                when (selectedTab) {
                    0 -> VelocityTab(uiState, viewModel)
                    1 -> OneRmTab(uiState, viewModel)
                    2 -> FatigueTab(uiState, viewModel)
                    3 -> WeeksTab(uiState, viewModel)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TAB 0 — PRĘDKOŚĆ (velocity trend)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VelocityTab(uiState: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { ExercisePicker(uiState.exercises, uiState.selectedExerciseId, viewModel::selectExercise) }

        if (uiState.selectedExerciseId != null) {
            if (uiState.velocityTrend.isEmpty()) {
                item { EmptyState("Brak danych o prędkości dla tego ćwiczenia") }
            } else {
                item {
                    ChartCard(title = "TREND PRĘDKOŚCI (90 dni)") {
                        val values = uiState.velocityTrend.map { it.meanVelocity.toFloat() }
                        LineChart(values, VbtTeal, Modifier.fillMaxWidth().height(180.dp))
                        DateAxisLabels(uiState.velocityTrend.map { it.date })
                    }
                }
                item {
                    val vels = uiState.velocityTrend.map { it.meanVelocity }
                    StatsRow(
                        "Śr." to String.format("%.3f m/s", vels.average()),
                        "Max" to String.format("%.3f m/s", vels.max()),
                        "Min" to String.format("%.3f m/s", vels.min()),
                        "Sesje" to uiState.velocityTrend.size.toString()
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TAB 1 — 1RM PROGRESS
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneRmTab(uiState: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { ExercisePicker(uiState.exercises, uiState.selectedExerciseId, viewModel::selectExercise) }

        if (uiState.selectedExerciseId != null) {
            if (uiState.oneRmProgress.isEmpty()) {
                item { EmptyState("Brak danych 1RM dla tego ćwiczenia") }
            } else {
                item {
                    ChartCard(title = "POSTĘP 1RM") {
                        val values = uiState.oneRmProgress.map { it.estimated1rm.toFloat() }
                        LineChart(values, Color(0xFFFF9800), Modifier.fillMaxWidth().height(180.dp))
                        DateAxisLabels(uiState.oneRmProgress.map { it.date })
                    }
                }
                item {
                    val orms = uiState.oneRmProgress.map { it.estimated1rm }
                    val progress = orms.last() - orms.first()
                    StatsRow(
                        "Obecny" to String.format("%.1f kg", orms.last()),
                        "Max" to String.format("%.1f kg", orms.max()),
                        "Postęp" to (if (progress >= 0) "+" else "") + String.format("%.1f kg", progress)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TAB 2 — ZMĘCZENIE (fatigue index per session)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FatigueTab(uiState: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text(
                "Indeks zmęczenia wewnątrztreningowego",
                style = MaterialTheme.typography.titleMedium,
                color = VbtTextPrimary, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "FI = spadek prędkości od 1. do ostatniej serii (%). " +
                "<5% optymalnie · 5-10% umiarkowanie · 10-20% wysoko · >20% przekroczono próg",
                style = MaterialTheme.typography.bodySmall, color = VbtTextSecondary
            )
        }

        item { SessionPicker(uiState.recentSessions, uiState.selectedSessionId, viewModel::selectSession) }

        if (uiState.selectedSessionId != null) {
            if (uiState.fatigueData.isEmpty()) {
                item { EmptyState("Brak danych o seriach dla tej sesji") }
            } else {
                items(uiState.fatigueData) { exerciseFatigue ->
                    FatigueExerciseCard(exerciseFatigue)
                }
            }
        }
    }
}

@Composable
private fun FatigueExerciseCard(data: FatigueIndexDto) {
    val zoneColor = when (data.readinessZone) {
        "optimal"    -> Color(0xFF4CAF50)
        "moderate"   -> Color(0xFFFFEB3B)
        "high"       -> Color(0xFFFF9800)
        else         -> Color(0xFFFF5252)  // overreached
    }
    val zoneLabel = when (data.readinessZone) {
        "optimal"    -> "Optymalnie"
        "moderate"   -> "Umiarkowanie"
        "high"       -> "Wysoko"
        else         -> "Przekroczono próg!"
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VbtSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(data.exerciseName, style = MaterialTheme.typography.titleSmall, color = VbtTextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(color = zoneColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        "FI: ${data.fatigueIndexPct}%  $zoneLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = zoneColor, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Bar chart: prędkość per seria
            val maxV = data.sets.maxOf { it.meanVelocity }.coerceAtLeast(0.01)
            Row(
                Modifier.fillMaxWidth().height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                data.sets.forEach { set ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        val ratio = (set.meanVelocity / maxV).coerceIn(0.05, 1.0)
                        val barColor = when {
                            set.setNumber == data.bestSet -> VbtTeal
                            set.meanVelocity / data.sets.first().meanVelocity > 0.90 -> Color(0xFF4CAF50)
                            set.meanVelocity / data.sets.first().meanVelocity > 0.80 -> Color(0xFFFF9800)
                            else -> Color(0xFFFF5252)
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(ratio.toFloat())
                                .background(barColor, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        )
                        Text("S${set.setNumber}", style = MaterialTheme.typography.labelSmall, color = VbtTextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "V1: ${String.format("%.2f", data.sets.firstOrNull()?.meanVelocity ?: 0.0)} → " +
                    "V${data.sets.size}: ${String.format("%.2f", data.sets.lastOrNull()?.meanVelocity ?: 0.0)} m/s",
                    style = MaterialTheme.typography.bodySmall, color = VbtTextSecondary
                )
                Text(
                    "Δ ${String.format("%.3f", data.velocityDropMs)} m/s",
                    style = MaterialTheme.typography.bodySmall, color = zoneColor, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TAB 3 — TYGODNIE (week comparison + weekly load)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeeksTab(uiState: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Weekly load — niezależne od ćwiczenia
        item {
            Text(
                "OBCIĄŻENIE TYGODNIOWE", style = MaterialTheme.typography.titleMedium,
                color = VbtTextPrimary, fontWeight = FontWeight.Bold
            )
        }

        if (uiState.weeklyLoad.isEmpty()) {
            item { EmptyState("Brak danych z ostatnich 8 tygodni") }
        } else {
            item { WeeklyLoadChart(uiState.weeklyLoad) }
            item { WeeklyLoadTable(uiState.weeklyLoad) }
        }

        // Week comparison — wymaga wyboru ćwiczenia
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "PORÓWNANIE TYGODNI — ĆWICZENIE",
                style = MaterialTheme.typography.titleMedium,
                color = VbtTextPrimary, fontWeight = FontWeight.Bold
            )
        }
        item { ExercisePicker(uiState.exercises, uiState.selectedExerciseId, viewModel::selectExercise) }

        if (uiState.selectedExerciseId != null) {
            if (uiState.weekComparison.isEmpty()) {
                item { EmptyState("Brak danych dla wybranego ćwiczenia") }
            } else {
                item { WeekComparisonChart(uiState.weekComparison) }
                item { WeekComparisonTable(uiState.weekComparison) }
            }
        }
    }
}

@Composable
private fun WeeklyLoadChart(data: List<WeeklyLoadDto>) {
    ChartCard(title = "Śr. prędkość & zmęczenie tygodniowe (8 tyg.)") {
        val velocities = data.map { it.weekMeanVelocity.toFloat() }
        LineChart(velocities, VbtTeal, Modifier.fillMaxWidth().height(140.dp))
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            data.forEach { w ->
                val fi = w.weeklyFatiguePct
                val color = when {
                    fi < 5 -> Color(0xFF4CAF50)
                    fi < 10 -> Color(0xFFFF9800)
                    fi < 20 -> Color(0xFFFF9800)
                    else -> Color(0xFFFF5252)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(w.week.takeLast(3), style = MaterialTheme.typography.labelSmall, color = VbtTextSecondary, textAlign = TextAlign.Center)
                    Text(
                        "${fi.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = color, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(VbtTeal, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(4.dp))
            Text("śr. prędkość", style = MaterialTheme.typography.labelSmall, color = VbtTextSecondary)
            Spacer(Modifier.width(12.dp))
            Text("FI% — zmęczenie tygodniowe (kolor słupka)", style = MaterialTheme.typography.labelSmall, color = VbtTextSecondary)
        }
    }
}

@Composable
private fun WeeklyLoadTable(data: List<WeeklyLoadDto>) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VbtSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            // Header
            Row(Modifier.fillMaxWidth().background(VbtSurfaceVariant).padding(6.dp)) {
                TableCell("Tydzień", Modifier.weight(1.8f), bold = true)
                TableCell("Dni", Modifier.weight(0.7f), bold = true)
                TableCell("Repy", Modifier.weight(0.7f), bold = true)
                TableCell("Śr.V m/s", Modifier.weight(1.1f), bold = true)
                TableCell("Obj. kg", Modifier.weight(1.1f), bold = true)
                TableCell("FI%", Modifier.weight(0.8f), bold = true)
            }
            data.forEach { w ->
                val fiColor = when {
                    w.weeklyFatiguePct < 5 -> Color(0xFF4CAF50)
                    w.weeklyFatiguePct < 15 -> Color(0xFFFF9800)
                    else -> Color(0xFFFF5252)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 6.dp)) {
                    TableCell(w.week, Modifier.weight(1.8f))
                    TableCell(w.trainingDays.toString(), Modifier.weight(0.7f))
                    TableCell(w.weekTotalReps.toString(), Modifier.weight(0.7f))
                    TableCell(String.format("%.3f", w.weekMeanVelocity), Modifier.weight(1.1f), color = VbtTeal)
                    TableCell(String.format("%.0f", w.weekTotalVolumeKg), Modifier.weight(1.1f))
                    TableCell(String.format("%.1f", w.weeklyFatiguePct), Modifier.weight(0.8f), color = fiColor)
                }
                HorizontalDivider(color = VbtSurfaceVariant, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun WeekComparisonChart(data: List<WeekComparisonDto>) {
    ChartCard(title = "PRĘDKOŚĆ TYGODNIOWA — WYNIK ĆWICZENIA") {
        val meanValues = data.map { it.meanVelocity.toFloat() }
        val maxValues = data.map { it.maxVelocity.toFloat() }
        DualLineChart(
            primary = meanValues,
            secondary = maxValues,
            primaryColor = VbtTeal,
            secondaryColor = Color(0xFFFF9800),
            modifier = Modifier.fillMaxWidth().height(160.dp)
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(VbtTeal, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(4.dp))
            Text("Śr. prędkość", style = MaterialTheme.typography.labelSmall, color = VbtTextSecondary)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(8.dp).background(Color(0xFFFF9800), RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(4.dp))
            Text("Max prędkość", style = MaterialTheme.typography.labelSmall, color = VbtTextSecondary)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            data.forEach { w ->
                Text(w.week.takeLast(3), style = MaterialTheme.typography.labelSmall, color = VbtTextSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WeekComparisonTable(data: List<WeekComparisonDto>) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VbtSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth().background(VbtSurfaceVariant).padding(6.dp)) {
                TableCell("Tydzień", Modifier.weight(1.8f), bold = true)
                TableCell("Śr.V", Modifier.weight(0.9f), bold = true)
                TableCell("MaxV", Modifier.weight(0.9f), bold = true)
                TableCell("Obcią.", Modifier.weight(0.9f), bold = true)
                TableCell("Repy", Modifier.weight(0.7f), bold = true)
                TableCell("1RM", Modifier.weight(0.9f), bold = true)
            }
            data.forEach { w ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 6.dp)) {
                    TableCell(w.week, Modifier.weight(1.8f))
                    TableCell(String.format("%.3f", w.meanVelocity), Modifier.weight(0.9f), color = VbtTeal)
                    TableCell(String.format("%.3f", w.maxVelocity), Modifier.weight(0.9f))
                    TableCell(String.format("%.0f kg", w.meanLoadKg), Modifier.weight(0.9f))
                    TableCell(w.totalReps.toString(), Modifier.weight(0.7f))
                    TableCell(w.bestEstimated1rm?.let { String.format("%.1f", it) } ?: "—", Modifier.weight(0.9f))
                }
                HorizontalDivider(color = VbtSurfaceVariant, thickness = 0.5.dp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SHARED COMPOSABLES
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AthletePicker(
    athletes: List<UserDto>,
    selectedId: Int?,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = athletes.find { it.id == selectedId }

    Surface(color = VbtSurface) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = selected?.username ?: "Wybierz zawodnika...",
                onValueChange = {},
                readOnly = true,
                label = { Text("Zawodnik") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VbtTeal,
                    unfocusedBorderColor = VbtTextSecondary
                )
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                athletes.forEach { athlete ->
                    DropdownMenuItem(
                        text = { Text(athlete.username, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { onSelect(athlete.id); expanded = false }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePicker(
    exercises: List<ExerciseDto>,
    selectedId: Int?,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = exercises.find { it.id == selectedId }
    val label = selected?.name ?: "Wybierz ćwiczenie..."

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VbtTeal,
                unfocusedBorderColor = VbtTextSecondary
            )
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // Grupuj po kategorii
            val grouped = exercises.groupBy { it.category ?: "other" }
            val order = listOf("olympic", "strength", "ballistic", "auxiliary", "other")
            val catNames = mapOf(
                "olympic" to "OLIMPIJSKIE",
                "strength" to "SIŁOWE",
                "ballistic" to "BALISTYCZNE",
                "auxiliary" to "POMOCNICZE"
            )
            order.forEach { cat ->
                grouped[cat]?.let { group ->
                    DropdownMenuItem(
                        text = { Text(catNames[cat] ?: cat.uppercase(), style = MaterialTheme.typography.labelSmall, color = VbtTeal, fontWeight = FontWeight.Bold) },
                        onClick = {}, enabled = false
                    )
                    group.forEach { ex ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ex.mvt?.let { mvt ->
                                        Text(
                                            String.format("%.2f", mvt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VbtTextSecondary,
                                            modifier = Modifier.width(36.dp)
                                        )
                                    }
                                    Text(ex.name, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = { onSelect(ex.id); expanded = false }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionPicker(
    sessions: List<WorkoutSessionDto>,
    selectedId: Int?,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = sessions.find { it.id == selectedId }
    val label = selected?.let { "${it.startedAt.take(10)} (${it.reps?.size ?: 0} repów)" } ?: "Wybierz sesję..."

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Sesja treningowa") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VbtTeal,
                unfocusedBorderColor = VbtTextSecondary
            )
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sessions.forEach { s ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(s.startedAt.take(10), style = MaterialTheme.typography.bodySmall)
                            Text("${s.reps?.size ?: 0} repów · ID ${s.id}", style = MaterialTheme.typography.labelSmall, color = VbtTextSecondary)
                        }
                    },
                    onClick = { onSelect(s.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VbtSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = VbtTeal, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun StatsRow(vararg items: Pair<String, String>) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VbtSurfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            items.forEach { (label, value) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, style = MaterialTheme.typography.titleSmall, color = VbtTeal, fontWeight = FontWeight.Bold)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = VbtTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(msg: String) {
    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
        Text(msg, color = VbtTextSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TableCell(text: String, modifier: Modifier, bold: Boolean = false, color: Color = VbtTextPrimary) {
    Text(
        text, modifier = modifier, style = MaterialTheme.typography.labelSmall,
        color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun DateAxisLabels(dates: List<String>) {
    if (dates.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(dates.first().take(5), style = MaterialTheme.typography.labelSmall, color = VbtTextSecondary)
        if (dates.size > 2) Text(dates[dates.size / 2].take(5), style = MaterialTheme.typography.labelSmall, color = VbtTextSecondary)
        Text(dates.last().take(5), style = MaterialTheme.typography.labelSmall, color = VbtTextSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  CHART PRIMITIVES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LineChart(values: List<Float>, lineColor: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Za mało danych", color = VbtTextSecondary)
        }
        return
    }
    val gridColor = Color.White.copy(alpha = 0.07f)
    Canvas(modifier) {
        val min = values.min()
        val max = values.max()
        val range = (max - min).coerceAtLeast(0.001f)
        val pH = 12.dp.toPx(); val pV = 10.dp.toPx()
        val w = size.width - pH * 2; val h = size.height - pV * 2

        // Grid
        repeat(4) { i ->
            val y = pV + h * i / 3f
            drawLine(gridColor, Offset(pH, y), Offset(size.width - pH, y), 1.dp.toPx())
        }

        // Points
        val pts = values.mapIndexed { i, v ->
            Offset(pH + w * i / (values.size - 1f), pV + h * (1f - (v - min) / range))
        }

        // Fill
        val fill = Path().apply {
            moveTo(pts.first().x, size.height - pV)
            lineTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, size.height - pV)
            close()
        }
        drawPath(fill, lineColor.copy(alpha = 0.12f))

        // Line
        val line = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(line, lineColor, style = Stroke(2.dp.toPx()))

        // Dots (only few points)
        if (values.size <= 30) pts.forEach { drawCircle(lineColor, 3.dp.toPx(), it) }
    }
}

@Composable
fun DualLineChart(primary: List<Float>, secondary: List<Float>, primaryColor: Color, secondaryColor: Color, modifier: Modifier = Modifier) {
    if (primary.size < 2) return
    val gridColor = Color.White.copy(alpha = 0.07f)
    Canvas(modifier) {
        val allVals = primary + secondary
        val min = allVals.min()
        val max = allVals.max()
        val range = (max - min).coerceAtLeast(0.001f)
        val pH = 12.dp.toPx(); val pV = 10.dp.toPx()
        val w = size.width - pH * 2; val h = size.height - pV * 2

        repeat(4) { i -> drawLine(gridColor, Offset(pH, pV + h * i / 3f), Offset(size.width - pH, pV + h * i / 3f), 1.dp.toPx()) }

        fun buildPts(vals: List<Float>) = vals.mapIndexed { i, v ->
            Offset(pH + w * i / (vals.size - 1f).coerceAtLeast(1f), pV + h * (1f - (v - min) / range))
        }

        listOf(primary to primaryColor, secondary to secondaryColor).forEach { (vals, color) ->
            if (vals.size < 2) return@forEach
            val pts = buildPts(vals)
            val line = Path().apply { moveTo(pts.first().x, pts.first().y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(line, color, style = Stroke(2.dp.toPx()))
            if (vals.size <= 20) pts.forEach { drawCircle(color, 3.dp.toPx(), it) }
        }
    }
}
