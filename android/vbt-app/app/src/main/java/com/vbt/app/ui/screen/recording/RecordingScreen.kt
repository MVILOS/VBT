package com.vbt.app.ui.screen.recording

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.vbt.app.data.ble.BleConnectionState
import com.vbt.app.domain.model.VelocityZone

@UnstableApi
@Composable
fun RecordingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Ekran nie może gasnąć podczas nagrywania/podglądu.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Do zapisu w galerii na Androidzie <10 (API 29) potrzebny jest jeszcze
    // WRITE_EXTERNAL_STORAGE; od API 29 MediaStore go nie wymaga.
    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasCameraPermission = result[Manifest.permission.CAMERA] == true }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(requiredPermissions)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            CameraPreview(viewModel)
            LiveOverlay(state, Modifier.align(Alignment.BottomCenter))
            TopInfo(state, Modifier.align(Alignment.TopStart))
        } else {
            PermissionPrompt(
                onRequest = { permissionLauncher.launch(requiredPermissions) },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Pasek górny: powrót + status połączenia czujnika
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            if (state.connectionState != BleConnectionState.CONNECTED) {
                AssistChip(
                    onClick = { viewModel.reconnectBle() },
                    label = { Text("Czujnik: brak") },
                    colors = AssistChipDefaults.assistChipColors(labelColor = Color.White)
                )
            }
            // Ustawienia parametrów nakładki - dostęp wprost z ekranu nagrywania.
            // Wyłączone w trakcie nagrywania (zestaw metryk jest utrwalany na starcie).
            IconButton(onClick = onNavigateToSettings, enabled = !state.isRecording) {
                Icon(Icons.Filled.Tune, "Parametry nakładki", tint = Color.White)
            }
        }

        // Sterowanie / postęp na dole
        Box(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)) {
            when (val phase = state.phase) {
                is RecordingPhase.Processing -> ProcessingCard(phase.progress)
                is RecordingPhase.Saved -> SavedCard(onDone = onNavigateBack, onAgain = viewModel::resetAfterSave)
                is RecordingPhase.Error -> ErrorCard(phase.message, onDismiss = viewModel::resetAfterSave)
                else -> if (hasCameraPermission) {
                    RecordButton(
                        isRecording = state.isRecording,
                        onClick = { if (state.isRecording) viewModel.stopRecording() else viewModel.startRecording() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(viewModel: RecordingViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    viewModel.recorder.videoCapture
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

@Composable
private fun TopInfo(state: RecordingUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.statusBarsPadding().padding(top = 56.dp, start = 16.dp)) {
        Surface(color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(10.dp)) {
            Text(
                buildString {
                    append(state.exerciseName)
                    if (state.loadKg > 0f) append("  •  ${fmtKg(state.loadKg)} kg")
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        state.athleteName?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun LiveOverlay(state: RecordingUiState, modifier: Modifier = Modifier) {
    val zone = VelocityZone.fromVelocity(state.liveVelocity)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                String.format("%.2f", state.liveVelocity),
                color = zone.color,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold
            )
            Text(zone.label.uppercase(), color = zone.color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text("REP ${state.repCount}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            state.heartRate?.let { Text("♥ $it", color = Color(0xFFFF5252), fontSize = 15.sp) }
        }
    }
}

@Composable
private fun RecordButton(isRecording: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(76.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (isRecording) Color(0xFFFF5252) else Color.White
        )
    ) {
        Icon(
            if (isRecording) Icons.Filled.Stop else Icons.Filled.Videocam,
            contentDescription = if (isRecording) "Zatrzymaj" else "Nagraj",
            tint = if (isRecording) Color.White else Color.Black,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun ProcessingCard(progress: Float) {
    Surface(color = Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth(0.8f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Wypalanie parametrów w wideo…", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text("${(progress * 100).toInt()}%", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun SavedCard(onDone: () -> Unit, onAgain: () -> Unit) {
    Surface(color = Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Zapisano w galerii (Movies/VBT)", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row {
                OutlinedButton(onClick = onAgain) { Text("Nagraj kolejne") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = onDone) { Text("Gotowe") }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Surface(color = Color(0xFF3A1010), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Aby nagrać podejście z parametrami, przyznaj dostęp do aparatu.",
            color = Color.White
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Przyznaj dostęp") }
    }
}

private fun fmtKg(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else String.format("%.1f", v)
