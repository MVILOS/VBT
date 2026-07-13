package com.vbt.app.ui.screen.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vbt.app.data.local.entity.WorkoutSessionEntity
import com.vbt.app.data.remote.WorkoutSessionDto
import com.vbt.app.data.remote.UserDto
import com.vbt.app.ui.theme.VbtSurface
import com.vbt.app.ui.theme.VbtSurfaceVariant
import com.vbt.app.ui.theme.VbtTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onSessionDetail: (Int) -> Unit,
    onResumeSession: (Long) -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var sessionPendingDelete by remember { mutableStateOf<WorkoutSessionDto?>(null) }

    sessionPendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionPendingDelete = null },
            title = { Text("Usunąć trening?") },
            text = { Text("Ta sesja treningowa (${formatDateTimePolish(session.startedAt)}) zostanie trwale usunięta.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSyncedSession(session.id)
                    sessionPendingDelete = null
                }) {
                    Text("Usuń", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionPendingDelete = null }) { Text("Anuluj") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Historia Treningów") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isCoach && uiState.athletes.isNotEmpty()) {
                AthleteFilterChips(
                    athletes = uiState.athletes,
                    selectedAthleteId = uiState.selectedAthleteId,
                    onFilterChange = { athleteId ->
                        viewModel.filterByAthlete(athleteId)
                    }
                )
            }

            // Show interrupted (active) local sessions at the top
            if (uiState.activeSessions.isNotEmpty()) {
                ActiveSessionsBanner(
                    sessions = uiState.activeSessions,
                    onResume = onResumeSession,
                    onDiscard = { sessionId -> viewModel.deleteActiveSession(sessionId) }
                )
            }

            // Sesje zakończone offline czekające na synchronizację z serwerem
            if (uiState.unsyncedSessions.isNotEmpty()) {
                UnsyncedSessionsBanner(sessions = uiState.unsyncedSessions)
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.error ?: "Nieznany błąd",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                uiState.sessions.isEmpty() && uiState.activeSessions.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Brak sesji treningowych",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.sessions.isNotEmpty() -> {
                    SessionsList(
                        sessions = uiState.sessions,
                        isCoach = uiState.isCoach,
                        onSessionDetail = onSessionDetail
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionsBanner(
    sessions: List<WorkoutSessionEntity>,
    onResume: (Long) -> Unit,
    onDiscard: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Przerywane sesje",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF9800),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        sessions.forEach { session ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatDateTimePolish(session.startedAt),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Niezakończony trening",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF9800)
                        )
                    }
                    Row {
                        Button(
                            onClick = { onResume(session.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = VbtTeal),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Wznów", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { onDiscard(session.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnsyncedSessionsBanner(sessions: List<WorkoutSessionEntity>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Oczekują na synchronizację",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        sessions.forEach { session ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatDateTimePolish(session.startedAt),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Zapisano lokalnie - wyśle się automatycznie",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = "Niezsynchronizowana",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AthleteFilterChips(
    athletes: List<UserDto>,
    selectedAthleteId: Int?,
    onFilterChange: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // All
        AthleteChip(
            label = "Wszyscy",
            isSelected = selectedAthleteId == null,
            onClick = { onFilterChange(null) }
        )

        athletes.forEach { athlete ->
            AthleteChip(
                label = athlete.username,
                isSelected = selectedAthleteId == athlete.id,
                onClick = { onFilterChange(athlete.id) }
            )
        }
    }
}

@Composable
private fun AthleteChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primary else VbtSurfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SessionsList(
    sessions: List<WorkoutSessionDto>,
    isCoach: Boolean,
    onSessionDetail: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupedSessions = sessions.groupBy { session ->
        formatDatePolish(session.startedAt)
    }

    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        state = listState
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
                SessionCard(
                    session = session,
                    isCoach = isCoach,
                    onClick = { onSessionDetail(session.id) }
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: WorkoutSessionDto,
    isCoach: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = VbtSurface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatDateTimePolish(session.startedAt),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    if (isCoach) {
                        Text(
                            text = "Zawodnik: ${session.athleteId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    val repCount = session.reps?.size ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Sesja z serwera = zsynchronizowana
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = "Zsynchronizowana",
                            tint = VbtTeal.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$repCount powtórzeń",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (session.durationSeconds != null) {
                        Text(
                            text = formatDuration(session.durationSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDateTimePolish(timestampMs: Long): String {
    return try {
        val date = java.util.Date(timestampMs)
        val format = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale("pl", "PL"))
        format.format(date)
    } catch (_: Exception) { "" }
}

private fun parseDate(dateStr: String): String {
    return try {
        // Utnij mikrosekundy do milisekund (max 3 cyfry po kropce)
        val normalized = dateStr.replace(Regex("(\\.\\d{3})\\d+"), "$1")
        val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val dt = java.time.LocalDateTime.parse(normalized, formatter)
        val displayFormatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", java.util.Locale("pl", "PL"))
        dt.format(displayFormatter)
    } catch (e: Exception) {
        dateStr.take(10) // fallback: tylko data
    }
}

private fun formatDatePolish(dateString: String): String {
    return parseDate(dateString)
}

private fun formatDateTimePolish(dateString: String): String {
    return try {
        // Utnij mikrosekundy do milisekund (max 3 cyfry po kropce)
        val normalized = dateString.replace(Regex("(\\.\\d{3})\\d+"), "$1")
        val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val dt = java.time.LocalDateTime.parse(normalized, formatter)
        val displayFormatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", java.util.Locale("pl", "PL"))
        dt.format(displayFormatter)
    } catch (e: Exception) {
        dateString
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> String.format("%d h %d min", hours, minutes)
        minutes > 0 -> String.format("%d min", minutes)
        else -> String.format("%d s", seconds)
    }
}
