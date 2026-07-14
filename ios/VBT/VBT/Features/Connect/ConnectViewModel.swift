import Foundation
import Observation
import CoreBluetooth

/// Port 1:1 z Android `ConnectViewModel.kt`. Na iOS filtrowanie po UUID usługi robi już
/// `CBCentralManager.scanForPeripherals(withServices:)` w `VbtBleManager`/`HeartRateManager`
/// (Android robił to ręcznie w callbacku skanu), więc ten VM tylko dokłada auto-stop po 10s.
@Observable
final class ConnectViewModel {
    private(set) var isScanningEsp32 = false
    private(set) var isScanningHr = false

    let bleManager: VbtBleManager
    let heartRateManager: HeartRateManager

    private var esp32ScanTask: Task<Void, Never>?
    private var hrScanTask: Task<Void, Never>?

    init(bleManager: VbtBleManager, heartRateManager: HeartRateManager) {
        self.bleManager = bleManager
        self.heartRateManager = heartRateManager
    }

    func scanForEsp32() {
        isScanningEsp32 = true
        bleManager.startScan()
        esp32ScanTask?.cancel()
        esp32ScanTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(10))
            guard !Task.isCancelled else { return }
            self?.stopScanEsp32()
        }
    }

    func stopScanEsp32() {
        esp32ScanTask?.cancel()
        bleManager.stopScan()
        isScanningEsp32 = false
    }

    func scanForHrMonitor() {
        isScanningHr = true
        heartRateManager.startScan()
        hrScanTask?.cancel()
        hrScanTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(10))
            guard !Task.isCancelled else { return }
            self?.stopScanHrMonitor()
        }
    }

    func stopScanHrMonitor() {
        hrScanTask?.cancel()
        heartRateManager.stopScan()
        isScanningHr = false
    }

    func connectEsp32(_ peripheral: CBPeripheral) {
        stopScanEsp32()
        bleManager.connect(to: peripheral)
    }

    func connectHrMonitor(_ peripheral: CBPeripheral) {
        stopScanHrMonitor()
        heartRateManager.connect(to: peripheral)
    }

    func disconnectEsp32() {
        bleManager.disconnect()
    }

    // Celowo NIE rozłączamy pasa HR przy zejściu z ekranu - połączenie żyje w
    // współdzielonym (environment) `HeartRateManager`, żeby tętno było dostępne
    // podczas treningu (WorkoutScreen), tak jak w Androidzie.
    func disconnectHrMonitor() {
        heartRateManager.disconnect()
    }

    func stopScanning() {
        stopScanEsp32()
        stopScanHrMonitor()
    }
}
