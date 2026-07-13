package com.vbt.app.ui.screen.history

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vbt.app.data.remote.WorkoutSessionDto
import com.vbt.app.data.remote.RepResultDto
import com.vbt.app.ui.theme.VbtSurface
import com.vbt.app.ui.theme.VbtSurfaceVariant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val session = uiState.session

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Szczegóły Sesji")
                        session?.let {
                            Text(
                                text = formatDateTimePolish(it.startedAt),
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
                        text = uiState.error ?: "Nieznany błąd",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            session != null -> {
                SessionDetailContent(
                    session = session,
                    onEditSetWeight = viewModel::updateSetWeight,
                    onDeleteRep = viewModel::deleteRep,
                    onMergeSet = viewModel::mergeSetWithPrevious,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }
    }
}

@Composable
private fun SessionDetailContent(
    session: WorkoutSessionDto,
    onEditSetWeight: (Int, Double) -> Unit,
    onDeleteRep: (Int) -> Unit,
    onMergeSet: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var setPendingWeightEdit by remember { mutableStateOf<Int?>(null) }
    var repPendingDelete by remember { mutableStateOf<RepResultDto?>(null) }
    var setPendingMerge by remember { mutableStateOf<Int?>(null) }

    setPendingWeightEdit?.let { setNumber ->
        val currentLoad = session.reps?.firstOrNull { it.setNumber == setNumber }?.loadKg ?: 0.0
        EditWeightDialog(
            currentLoadKg = currentLoad,
            onConfirm = { newLoad ->
                onEditSetWeight(setNumber, newLoad)
                setPendingWeightEdit = null
            },
            onDismiss = { setPendingWeightEdit = null }
        )
    }

    repPendingDelete?.let { rep ->
        AlertDialog(
            onDismissRequest = { repPendingDelete = null },
            title = { Text("Usunąć powtórzenie?") },
            text = { Text("Seria ${rep.setNumber}, powtórzenie ${rep.repNumber} zostanie trwale usunięte (np. fałszywy rep od pociągnięcia linki).") },
            confirmButton = {
                TextButton(onClick = {
                    rep.id?.let(onDeleteRep)
                    repPendingDelete = null
                }) {
                    Text("Usuń", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { repPendingDelete = null } ) { Text("Anuluj") }
            }
        )
    }

    setPendingMerge?.let { setNumber ->
        AlertDialog(
            onDismissRequest = { setPendingMerge = null },
            title = { Text("Scalić serie?") },
            text = { Text("Powtórzenia z serii $setNumber zostaną dopisane do serii ${setNumber - 1} (np. gdy zapomniano nacisnąć \"kolejna seria\").") },
            confirmButton = {
                TextButton(onClick = {
                    onMergeSet(setNumber)
                    setPendingMerge = null
                }) { Text("Scal") }
            },
            dismissButton = {
                TextButton(onClick = { setPendingMerge = null }) { Text("Anuluj") }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            SummarySection(session)
        }

        if (session.reps != null && session.reps.isNotEmpty()) {
            item {
                RepByRepSection(
                    reps = session.reps,
                    onEditSetWeight = { setNumber -> setPendingWeightEdit = setNumber },
                    onDeleteRep = { rep -> repPendingDelete = rep },
                    onMergeSet = { setNumber -> setPendingMerge = setNumber }
                )
            }

            item {
                AnalyticsSection(session.reps)
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EditWeightDialog(
    currentLoadKg: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(String.format(Locale.US, "%.1f", currentLoadKg)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Popraw ciężar serii") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Ciężar (kg)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = {
                text.replace(',', '.').toDoubleOrNull()?.let(onConfirm)
            }) { Text("Zapisz") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

@Composable
private fun SummarySection(session: WorkoutSessionDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VbtSurface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "PODSUMOWANIE",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val totalReps = session.reps?.size ?: 0
                StatTile(
                    label = "Całkowite\npowtórzenia",
                    value = totalReps.toString()
                )

                if (session.durationSeconds != null) {
                    StatTile(
                        label = "Czas",
                        value = formatDuration(session.durationSeconds)
                    )
                }

                if (session.reps != null && session.reps.isNotEmpty()) {
                    val totalVolume = session.reps.sumOf { it.loadKg * it.repNumber }
                    StatTile(
                        label = "Objętość",
                        value = String.format("%.1f kg", totalVolume)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun RepByRepSection(
    reps: List<RepResultDto>,
    onEditSetWeight: (Int) -> Unit,
    onDeleteRep: (RepResultDto) -> Unit,
    onMergeSet: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VbtSurface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "REP BY REP",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val bySet = reps.sortedWith(compareBy({ it.setNumber }, { it.repNumber }))
                .groupBy { it.setNumber }

            bySet.entries.sortedBy { it.key }.forEach { (setNumber, setReps) ->
                SetHeader(
                    setNumber = setNumber,
                    canMerge = setNumber > 1,
                    onEditWeight = { onEditSetWeight(setNumber) },
                    onMerge = { onMergeSet(setNumber) }
                )
                RepTableHeader()
                setReps.forEachIndexed { index, rep ->
                    RepTableRow(
                        rep = rep,
                        isAlternate = index % 2 == 1,
                        onDelete = { onDeleteRep(rep) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SetHeader(
    setNumber: Int,
    canMerge: Boolean,
    onEditWeight: () -> Unit,
    onMerge: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Seria $setNumber",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canMerge) {
                IconButton(onClick = onMerge, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.CallMerge,
                        contentDescription = "Scal z poprzednią serią",
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            IconButton(onClick = onEditWeight, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Popraw ciężar serii",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun RepTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VbtSurfaceVariant)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Seria",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.8f)
        )
        Text(
            text = "Rep",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.7f),
            textAlign = TextAlign.Center
        )
        Text(
            text = "Kg",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.7f),
            textAlign = TextAlign.Center
        )
        Text(
            text = "Mean V",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.8f),
            textAlign = TextAlign.Center
        )
        Text(
            text = "Peak V",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.8f),
            textAlign = TextAlign.Center
        )
        Text(
            text = "1RM",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.weight(0.5f))
    }
}

@Composable
private fun RepTableRow(
    rep: RepResultDto,
    isAlternate: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isAlternate) VbtSurfaceVariant else VbtSurface
    val meanVelocityColor = when {
        rep.meanVelocity > 1.0 -> Color(0xFF4CAF50)
        rep.meanVelocity < 0.5 -> Color(0xFFFF5252)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rep.setNumber.toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.8f)
        )
        Text(
            text = rep.repNumber.toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.7f),
            textAlign = TextAlign.Center
        )
        Text(
            text = String.format("%.1f", rep.loadKg),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.7f),
            textAlign = TextAlign.Center
        )
        Text(
            text = String.format("%.2f", rep.meanVelocity),
            style = MaterialTheme.typography.bodySmall,
            color = meanVelocityColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.8f),
            textAlign = TextAlign.Center
        )
        Text(
            text = String.format("%.2f", rep.peakVelocity),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.8f),
            textAlign = TextAlign.Center
        )
        Text(
            text = if (rep.estimated1rm != null) String.format("%.1f", rep.estimated1rm) else "—",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AnalyticsSection(reps: List<RepResultDto>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VbtSurface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "ANALITYKA",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val avgVelocity = reps.map { it.meanVelocity }.average()
            val bestORM = reps.mapNotNull { it.estimated1rm }.maxOrNull() ?: 0.0

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyticsRow(
                    label = "Średnia prędkość:",
                    value = String.format("%.3f m/s", avgVelocity)
                )
                AnalyticsRow(
                    label = "Najlepszy 1RM:",
                    value = if (bestORM > 0) String.format("%.1f kg", bestORM) else "—"
                )
                AnalyticsRow(
                    label = "Liczba repów:",
                    value = reps.size.toString()
                )
            }
        }
    }
}

@Composable
private fun AnalyticsRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
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

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> String.format("%d h %d min", hours, minutes)
        minutes > 0 -> String.format("%d min", minutes)
        else -> String.format("%d s", seconds)
    }
}
