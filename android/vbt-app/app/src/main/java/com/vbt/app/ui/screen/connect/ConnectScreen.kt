package com.vbt.app.ui.screen.connect

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vbt.app.data.ble.BleConnectionState
import com.vbt.app.ui.theme.VbtSurface
import com.vbt.app.ui.theme.VbtTeal
import com.vbt.app.ui.theme.VbtSuccess
import com.vbt.app.ui.theme.VbtError
import com.vbt.app.ui.theme.VbtTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun ConnectScreen(
    onBack: () -> Unit,
    viewModel: ConnectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var permissionsGranted by remember { mutableStateOf(false) }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissions)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect Devices") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ESP32 Section
            item {
                Esp32Section(
                    uiState = uiState,
                    onScan = { viewModel.scanForEsp32() },
                    onStopScan = { viewModel.stopScanEsp32() },
                    onConnect = { device -> viewModel.connectEsp32(device.device) },
                    onDisconnect = { viewModel.disconnectEsp32() },
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = { permissionLauncher.launch(permissions) }
                )
            }

            // HR Monitor Section
            item {
                HrMonitorSection(
                    uiState = uiState,
                    onScan = { viewModel.scanForHrMonitor() },
                    onStopScan = { viewModel.stopScanHrMonitor() },
                    onConnect = { device -> viewModel.connectHrMonitor(device.device) },
                    onDisconnect = { viewModel.disconnectHrMonitor() },
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = { permissionLauncher.launch(permissions) }
                )
            }
        }
    }
}

@Composable
fun Esp32Section(
    uiState: ConnectUiState,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (device: android.bluetooth.le.ScanResult) -> Unit,
    onDisconnect: () -> Unit,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with status chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "VBT URZĄDZENIE",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            StatusChip(state = uiState.esp32State)
        }

        if (uiState.esp32State == BleConnectionState.CONNECTED) {
            // Connected state - show connected device
            ConnectedDeviceCard(
                deviceName = "VBT Device",
                rssi = -50,
                onDisconnect = onDisconnect
            )
        } else {
            // Disconnected state - show scan button and device list
            Button(
                onClick = {
                    if (uiState.isScanning) onStopScan()
                    else if (permissionsGranted) onScan()
                    else onRequestPermissions()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Scanning")
                } else {
                    Text("Scan for VBT Devices")
                }
            }

            if (uiState.esp32Devices.isNotEmpty()) {
                Text(
                    "Found Devices",
                    style = MaterialTheme.typography.labelSmall,
                    color = VbtTextSecondary
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.esp32Devices.forEach { device ->
                        DeviceCard(
                            name = device.device.name ?: "Unknown",
                            address = device.device.address,
                            rssi = device.rssi,
                            onClick = { onConnect(device) }
                        )
                    }
                }
            } else if (!uiState.isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No devices found.\nPress scan to search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VbtTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun HrMonitorSection(
    uiState: ConnectUiState,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (device: android.bluetooth.le.ScanResult) -> Unit,
    onDisconnect: () -> Unit,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with status chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "MONITOR TĘTNA (OPCJONALNIE)",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            StatusChip(state = uiState.hrState)
        }

        if (uiState.hrState == BleConnectionState.CONNECTED) {
            // Connected state - show heart rate
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, VbtTeal, shape = MaterialTheme.shapes.medium),
                colors = CardDefaults.cardColors(containerColor = VbtSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "BPM",
                        style = MaterialTheme.typography.labelSmall,
                        color = VbtTextSecondary
                    )
                    Text(
                        "${uiState.heartRate ?: "--"}",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 64.sp
                        ),
                        color = VbtTeal
                    )
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VbtError.copy(alpha = 0.2f),
                            contentColor = VbtError
                        )
                    ) {
                        Text("Rozłącz")
                    }
                }
            }
        } else {
            // Disconnected state
            Button(
                onClick = {
                    if (uiState.isScanning) onStopScan()
                    else if (permissionsGranted) onScan()
                    else onRequestPermissions()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Scanning")
                } else {
                    Text("Scan for HR Monitors")
                }
            }

            if (uiState.hrDevices.isNotEmpty()) {
                Text(
                    "Found Devices",
                    style = MaterialTheme.typography.labelSmall,
                    color = VbtTextSecondary
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.hrDevices.forEach { device ->
                        DeviceCard(
                            name = device.device.name ?: "HR Monitor",
                            address = device.device.address,
                            rssi = device.rssi,
                            onClick = { onConnect(device) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(state: BleConnectionState) {
    val (backgroundColor, textColor, label) = when (state) {
        BleConnectionState.CONNECTED -> Triple(VbtSuccess.copy(alpha = 0.2f), VbtSuccess, "Connected")
        BleConnectionState.DISCONNECTED -> Triple(Color.Gray.copy(alpha = 0.2f), Color.Gray, "Disconnected")
        BleConnectionState.CONNECTING -> Triple(Color.Yellow.copy(alpha = 0.2f), Color.Yellow, "Connecting")
        else -> Triple(Color.Gray.copy(alpha = 0.2f), Color.Gray, "Unknown")
    }

    AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.height(32.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = backgroundColor,
            labelColor = textColor
        ),
        enabled = false
    )
}

@Composable
fun DeviceCard(
    name: String,
    address: String,
    rssi: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = VbtSurface),
        border = BorderStroke(1.dp, VbtTeal.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Text(
                    text = address,
                    style = MaterialTheme.typography.labelSmall,
                    color = VbtTextSecondary
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.SignalCellularAlt,
                    contentDescription = "Signal",
                    modifier = Modifier.size(16.dp),
                    tint = VbtTextSecondary
                )
                Text(
                    text = "$rssi dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = VbtTextSecondary
                )
            }
        }
    }
}

@Composable
fun ConnectedDeviceCard(
    deviceName: String,
    rssi: Int,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VbtTeal, shape = MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = VbtSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Text(
                        text = "$rssi dBm",
                        style = MaterialTheme.typography.labelSmall,
                        color = VbtTextSecondary
                    )
                }
                Icon(
                    Icons.Default.BluetoothConnected,
                    contentDescription = "Connected",
                    tint = VbtSuccess,
                    modifier = Modifier.size(24.dp)
                )
            }
            Button(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VbtError.copy(alpha = 0.2f),
                    contentColor = VbtError
                )
            ) {
                Text("Rozłącz")
            }
        }
    }
}
