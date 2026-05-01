package com.vbt.app.ui.screen.athletes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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
import com.vbt.app.data.remote.UserDto
import com.vbt.app.ui.theme.VbtPurple
import com.vbt.app.ui.theme.VbtSuccess
import com.vbt.app.ui.theme.VbtSurface
import com.vbt.app.ui.theme.VbtSurfaceVariant
import com.vbt.app.ui.theme.VbtTeal

private val athleteColors = listOf(
    0xFF4ECDC4, // Teal
    0xFF7C3AED, // Purple
    0xFFFF6B6B, // Red
    0xFF4ECDC4, // Teal
    0xFFFFB13C, // Orange
    0xFF26D0CE, // Cyan
    0xFF5DADE2, // Blue
    0xFFF1A00F  // Gold
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthleteListScreen(
    onAthleteProfile: (Int) -> Unit,
    viewModel: AthleteListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zawodnicy") },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Dodaj")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Utwórz nowe konto") },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = { showMenu = false; viewModel.showCreateDialog() }
                        )
                        DropdownMenuItem(
                            text = { Text("Przypisz istniejące konto") },
                            leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                            onClick = { showMenu = false; viewModel.showAssignDialog() }
                        )
                        if (uiState.userRole == "admin") {
                            DropdownMenuItem(
                                text = { Text("Przypisz do trenera (Admin)") },
                                leadingIcon = { Icon(Icons.Default.AdminPanelSettings, null) },
                                onClick = { showMenu = false; viewModel.showAdminAssignDialog() }
                            )
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
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.athletes.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Brak zawodników. Dodaj pierwszego!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        items(uiState.athletes) { athlete ->
                            AthleteCard(
                                athlete = athlete,
                                onClick = { onAthleteProfile(athlete.id) }
                            )
                        }
                    }
                }
            }

            if (!uiState.error.isNullOrEmpty()) {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = { viewModel.clearMessages() }) { Text("OK") } }
                ) { Text(uiState.error ?: "") }
            }
            if (!uiState.assignSuccess.isNullOrEmpty()) {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    containerColor = VbtSuccess,
                    action = { TextButton(onClick = { viewModel.clearMessages() }) { Text("OK") } }
                ) { Text(uiState.assignSuccess ?: "") }
            }
        }
    }

    if (uiState.showAssignDialog) {
        AssignByUsernameDialog(
            username = uiState.assignUsername,
            onUsernameChange = { viewModel.updateAssignUsername(it) },
            onAssign = { viewModel.assignByUsername() },
            onDismiss = { viewModel.hideAssignDialog() }
        )
    }

    if (uiState.showCreateDialog) {
        CreateAthleteDialog(
            username = uiState.newAthleteUsername,
            email = uiState.newAthleteEmail,
            password = uiState.newAthletePassword,
            onUsernameChange = { viewModel.updateNewAthleteUsername(it) },
            onEmailChange = { viewModel.updateNewAthleteEmail(it) },
            onPasswordChange = { viewModel.updateNewAthletePassword(it) },
            onCreate = { viewModel.createAthlete() },
            onDismiss = { viewModel.hideCreateDialog() }
        )
    }

    if (uiState.showAdminAssignDialog) {
        AdminAssignDialog(
            athleteUsername = uiState.adminAthleteUsername,
            coachUsername = uiState.adminCoachUsername,
            onAthleteUsernameChange = { viewModel.updateAdminAthleteUsername(it) },
            onCoachUsernameChange = { viewModel.updateAdminCoachUsername(it) },
            onAssign = { viewModel.adminAssignToCoach() },
            onDismiss = { viewModel.hideAdminAssignDialog() }
        )
    }
}

@Composable
private fun AthleteCard(
    athlete: UserDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = VbtSurface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with initial
            val colorIndex = athlete.id % athleteColors.size
            val avatarColor = androidx.compose.ui.graphics.Color(athleteColors[colorIndex])
            val initial = athlete.username.firstOrNull()?.uppercase() ?: "A"

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = avatarColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }

            // Athlete info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = athlete.username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = athlete.email ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Role + status chips
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (athlete.role == "coach") {
                    AssistChip(
                        onClick = { },
                        label = { Text("Trener", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = VbtPurple.copy(alpha = 0.2f))
                    )
                }
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            if (athlete.isActive) "Aktywny" else "Nieaktywny",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (athlete.isActive) VbtSuccess else VbtSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun CreateAthleteDialog(
    username: String,
    email: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj Zawodnika") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("Nazwa użytkownika") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Hasło") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onCreate,
                colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
            ) {
                Text("Utwórz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}

@Composable
private fun AdminAssignDialog(
    athleteUsername: String,
    coachUsername: String,
    onAthleteUsernameChange: (String) -> Unit,
    onCoachUsernameChange: (String) -> Unit,
    onAssign: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Przypisz do trenera (Admin)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Przypisz dowolnego użytkownika do listy wybranego trenera.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = athleteUsername,
                    onValueChange = onAthleteUsernameChange,
                    label = { Text("Nazwa zawodnika/trenera") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = coachUsername,
                    onValueChange = onCoachUsernameChange,
                    label = { Text("Nazwa trenera") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAssign,
                enabled = athleteUsername.isNotBlank() && coachUsername.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
            ) { Text("Przypisz") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

@Composable
private fun AssignByUsernameDialog(
    username: String,
    onUsernameChange: (String) -> Unit,
    onAssign: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Przypisz istniejące konto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Wpisz nazwę użytkownika trenera lub zawodnika, który ma być widoczny na Twojej liście.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("Nazwa użytkownika") },
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAssign,
                enabled = username.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = VbtTeal)
            ) { Text("Przypisz") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}
