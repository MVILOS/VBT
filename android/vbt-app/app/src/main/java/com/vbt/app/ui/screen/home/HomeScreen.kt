package com.vbt.app.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vbt.app.ui.theme.VbtBackground
import com.vbt.app.ui.theme.VbtPurple
import com.vbt.app.ui.theme.VbtSurface
import com.vbt.app.ui.theme.VbtSurfaceVariant
import com.vbt.app.ui.theme.VbtTeal
import com.vbt.app.ui.theme.VbtTextPrimary
import com.vbt.app.ui.theme.VbtTextSecondary

@Composable
fun HomeScreen(
    onStartWorkout: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToPlans: () -> Unit,
    onNavigateToConnect: () -> Unit,
    onNavigateToAthletes: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VbtBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Header: Greeting + Role Chip
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cześć, ${uiState.username}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = VbtTextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = if (viewModel.isCoach) "COACH" else "ATHLETE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (viewModel.isCoach) VbtPurple else VbtTeal,
                        labelColor = VbtTextPrimary
                    ),
                    modifier = Modifier.height(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // BLE Connection Status
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bleColor = if (uiState.isBleConnected) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(bleColor, shape = RoundedCornerShape(50))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isBleConnected) "ESP32 Connected" else "Disconnected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VbtTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Dashboard Stats Cards
        uiState.dashboardStats?.let { stats ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Sesje w tygodniu",
                        value = stats.sessionsThisWeek.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Aktywne plany",
                        value = stats.activePlans.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    if (viewModel.isCoach) {
                        StatCard(
                            label = "Zawodnicy",
                            value = stats.totalAthletes.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Recent Sessions
        if (uiState.recentSessions.isNotEmpty()) {
            item {
                Text(
                    text = "OSTATNIE SESJE",
                    style = MaterialTheme.typography.titleMedium,
                    color = VbtTextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }
            items(uiState.recentSessions) { session ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = VbtSurfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = session.athleteName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = VbtTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = session.startedAt.take(10),
                                style = MaterialTheme.typography.bodySmall,
                                color = VbtTextSecondary
                            )
                        }
                        Text(
                            text = "${session.repsCount} reps",
                            style = MaterialTheme.typography.bodySmall,
                            color = VbtTeal,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Big "NOWY TRENING" Button
        item {
            Button(
                onClick = onStartWorkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .border(2.dp, VbtTeal, RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VbtSurface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = VbtTeal,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "NOWY TRENING",
                        style = MaterialTheme.typography.headlineLarge,
                        color = VbtTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // "Dzisiaj" Section
        if (uiState.todayEntries.isNotEmpty()) {
            item {
                Text(
                    text = "ZAPLANOWANE DZIŚ",
                    style = MaterialTheme.typography.titleMedium,
                    color = VbtTextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            items(uiState.todayEntries) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = VbtSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = VbtTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (!entry.timeSlot.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Godzina: ${entry.timeSlot}",
                                style = MaterialTheme.typography.bodySmall,
                                color = VbtTextSecondary
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Bottom Tiles (LazyRow or Grid)
        item {
            val tiles = mutableListOf(
                Triple("Historia", Icons.Default.History) { onNavigateToHistory() },
                Triple("Plany", Icons.Default.FitnessCenter) { onNavigateToPlans() },
                Triple("Analityka", Icons.Default.Analytics) { onNavigateToAnalytics() },
                Triple("Urządzenie", Icons.Default.Bluetooth) { onNavigateToConnect() }
            )

            if (viewModel.isCoach) {
                tiles.add(Triple("Zawodnicy", Icons.Default.Logout) { onNavigateToAthletes() })
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tiles.size) { index ->
                    val (label, icon, onClick) = tiles[index]
                    HomeMenuTile(
                        label = label,
                        icon = icon,
                        onClick = onClick,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Logout Button
        item {
            OutlinedButton(
                onClick = {
                    viewModel.logout {
                        onLogout()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = VbtTeal
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.foundation.BorderStroke(1.dp, VbtTeal).brush
                )
            ) {
                Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Wyloguj się")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = VbtSurfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = VbtTeal,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = VbtTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun HomeMenuTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(VbtSurfaceVariant)
            .border(1.dp, VbtTeal, RoundedCornerShape(8.dp)),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = VbtSurfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VbtTeal,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = VbtTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
