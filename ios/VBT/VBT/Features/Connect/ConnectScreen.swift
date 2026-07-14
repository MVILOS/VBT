import SwiftUI
import CoreBluetooth

/// Port 1:1 z Android `ConnectScreen.kt`: sekcja ESP32 (skan/lista/połącz) + sekcja
/// opcjonalnego pasa HR, obie niezależne. Uprawnienia BLE na iOS to jeden prompt
/// systemowy przy pierwszym `CBCentralManager` (nie dwa jak na Android <12), więc nie ma
/// tu odpowiednika ręcznego `rememberLauncherForActivityResult`.
struct ConnectScreen: View {
    @Environment(VbtBleManager.self) private var bleManager
    @Environment(HeartRateManager.self) private var heartRateManager
    @State private var viewModel: ConnectViewModel?

    var body: some View {
        Group {
            if let viewModel {
                ConnectContent(viewModel: viewModel)
            } else {
                VbtColor.background.ignoresSafeArea()
            }
        }
        .onAppear {
            if viewModel == nil {
                viewModel = ConnectViewModel(bleManager: bleManager, heartRateManager: heartRateManager)
            }
        }
        .onDisappear {
            viewModel?.stopScanning()
        }
        .navigationTitle("Connect Devices")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct ConnectContent: View {
    let viewModel: ConnectViewModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Esp32Section(viewModel: viewModel)
                Divider().background(VbtColor.textSecondary.opacity(0.3))
                HrMonitorSection(viewModel: viewModel)
            }
            .padding(16)
        }
        .background(VbtColor.background)
    }
}

private struct Esp32Section: View {
    @Bindable var bleManager: VbtBleManager
    let viewModel: ConnectViewModel

    init(viewModel: ConnectViewModel) {
        self.viewModel = viewModel
        self._bleManager = Bindable(viewModel.bleManager)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("VBT URZĄDZENIE")
                    .font(VbtFont.title)
                    .foregroundStyle(VbtColor.textPrimary)
                Spacer()
                StatusChip(state: bleManager.connectionState)
            }

            if bleManager.connectionState == .connected {
                ConnectedDeviceCard(deviceName: BleConstants.deviceName, onDisconnect: viewModel.disconnectEsp32)
            } else {
                ScanButton(isScanning: viewModel.isScanningEsp32) {
                    if viewModel.isScanningEsp32 {
                        viewModel.stopScanEsp32()
                    } else {
                        viewModel.scanForEsp32()
                    }
                }

                if !bleManager.discoveredPeripherals.isEmpty {
                    Text("Found Devices")
                        .font(VbtFont.caption)
                        .foregroundStyle(VbtColor.textSecondary)
                    ForEach(bleManager.discoveredPeripherals, id: \.identifier) { peripheral in
                        DeviceCard(name: peripheral.name ?? "Unknown") {
                            viewModel.connectEsp32(peripheral)
                        }
                    }
                } else if !viewModel.isScanningEsp32 {
                    Text("No devices found.\nPress scan to search.")
                        .font(VbtFont.body)
                        .foregroundStyle(VbtColor.textSecondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 32)
                }
            }
        }
    }
}

private struct HrMonitorSection: View {
    @Bindable var heartRateManager: HeartRateManager
    let viewModel: ConnectViewModel

    init(viewModel: ConnectViewModel) {
        self.viewModel = viewModel
        self._heartRateManager = Bindable(viewModel.heartRateManager)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("MONITOR TĘTNA (OPCJONALNIE)")
                    .font(VbtFont.title)
                    .foregroundStyle(VbtColor.textPrimary)
                Spacer()
                StatusChip(state: heartRateManager.connectionState)
            }

            if heartRateManager.connectionState == .connected {
                VStack(spacing: 16) {
                    Text("BPM")
                        .font(VbtFont.caption)
                        .foregroundStyle(VbtColor.textSecondary)
                    Text(heartRateManager.heartRate.map(String.init) ?? "--")
                        .font(.system(size: 64, weight: .bold, design: .rounded))
                        .foregroundStyle(VbtColor.teal)
                    Button("Rozłącz", role: .destructive) {
                        viewModel.disconnectHrMonitor()
                    }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
                }
                .frame(maxWidth: .infinity)
                .padding(24)
                .background(VbtColor.surface)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(VbtColor.teal, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            } else {
                ScanButton(isScanning: viewModel.isScanningHr) {
                    if viewModel.isScanningHr {
                        viewModel.stopScanHrMonitor()
                    } else {
                        viewModel.scanForHrMonitor()
                    }
                }

                if !heartRateManager.discoveredPeripherals.isEmpty {
                    Text("Found Devices")
                        .font(VbtFont.caption)
                        .foregroundStyle(VbtColor.textSecondary)
                    ForEach(heartRateManager.discoveredPeripherals, id: \.identifier) { peripheral in
                        DeviceCard(name: peripheral.name ?? "HR Monitor") {
                            viewModel.connectHrMonitor(peripheral)
                        }
                    }
                }
            }
        }
    }
}

private struct ScanButton: View {
    let isScanning: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                if isScanning {
                    ProgressView().tint(VbtColor.background)
                    Text("Stop Scanning")
                } else {
                    Text("Scan for Devices")
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 48)
        }
        .buttonStyle(.borderedProminent)
        .tint(VbtColor.teal)
        .foregroundStyle(VbtColor.background)
    }
}

private struct StatusChip: View {
    let state: BleConnectionState

    private var label: String {
        switch state {
        case .connected: return "Connected"
        case .connecting: return "Connecting"
        case .reconnecting: return "Reconnecting"
        case .disconnecting: return "Disconnecting"
        case .scanning: return "Scanning"
        case .disconnected: return "Disconnected"
        }
    }

    private var color: Color {
        switch state {
        case .connected: return VbtColor.success
        case .connecting, .reconnecting, .scanning: return Color(hex: 0xFFD600)
        case .disconnected, .disconnecting: return VbtColor.textSecondary
        }
    }

    var body: some View {
        Text(label)
            .font(VbtFont.caption)
            .foregroundStyle(color)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(color.opacity(0.2))
            .clipShape(Capsule())
    }
}

private struct DeviceCard: View {
    let name: String
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack {
                Text(name)
                    .font(VbtFont.body)
                    .foregroundStyle(VbtColor.textPrimary)
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(VbtColor.textSecondary)
            }
            .padding(12)
            .background(VbtColor.surface)
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(VbtColor.teal.opacity(0.3), lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
    }
}

private struct ConnectedDeviceCard: View {
    let deviceName: String
    let onDisconnect: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(deviceName)
                    .font(VbtFont.body)
                    .foregroundStyle(VbtColor.textPrimary)
                Spacer()
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(VbtColor.success)
            }
            Button("Rozłącz", role: .destructive) {
                onDisconnect()
            }
            .buttonStyle(.bordered)
            .frame(maxWidth: .infinity)
        }
        .padding(16)
        .background(VbtColor.surface)
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(VbtColor.teal, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
