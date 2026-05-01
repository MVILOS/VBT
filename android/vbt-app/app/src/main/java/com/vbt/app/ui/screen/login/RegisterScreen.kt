package com.vbt.app.ui.screen.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vbt.app.ui.theme.VbtBackground
import com.vbt.app.ui.theme.VbtTeal
import com.vbt.app.ui.theme.VbtError
import com.vbt.app.ui.theme.VbtTextSecondary

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VbtBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "VBT",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = VbtTeal
            )
            Text(
                text = "Rejestracja zawodnika",
                style = MaterialTheme.typography.titleMedium,
                color = VbtTextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = state.username,
                onValueChange = { viewModel.onUsernameChange(it) },
                label = { Text("Nazwa użytkownika") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = VbtTextSecondary.copy(alpha = 0.3f),
                    focusedBorderColor = VbtTeal,
                    unfocusedLabelColor = VbtTextSecondary,
                    focusedLabelColor = VbtTeal,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = { Text("Hasło") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = VbtTextSecondary.copy(alpha = 0.3f),
                    focusedBorderColor = VbtTeal,
                    unfocusedLabelColor = VbtTextSecondary,
                    focusedLabelColor = VbtTeal,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = { viewModel.onConfirmPasswordChange(it) },
                label = { Text("Powtórz hasło") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = VbtTextSecondary.copy(alpha = 0.3f),
                    focusedBorderColor = VbtTeal,
                    unfocusedLabelColor = VbtTextSecondary,
                    focusedLabelColor = VbtTeal,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            if (state.error != null) {
                Text(
                    text = state.error!!,
                    color = VbtError,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.register(onRegisterSuccess) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !state.isLoading && state.username.isNotBlank() && state.password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VbtTeal,
                    disabledContainerColor = VbtTeal.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black
                    )
                } else {
                    Text(
                        text = "ZAREJESTRUJ SIĘ",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            TextButton(onClick = onBack) {
                Text(
                    text = "Masz już konto? Zaloguj się",
                    color = VbtTeal,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
