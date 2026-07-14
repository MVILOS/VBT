import CoreBluetooth
import Foundation

/// Port 1:1 z Android `data/ble/HeartRateManager.kt` — niezależny od `VbtBleManager`
/// manager standardowego BLE Heart Rate Service (opaska/pas HR, opcjonalny).
@Observable
final class HeartRateManager: NSObject {
    private static let hrServiceUUID = CBUUID(string: "180D")
    private static let hrMeasurementUUID = CBUUID(string: "2A37")

    private(set) var heartRate: Int?
    private(set) var connectionState: BleConnectionState = .disconnected
    private(set) var discoveredPeripherals: [CBPeripheral] = []

    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: .main)
    }

    func startScan() {
        guard central.state == .poweredOn else { return }
        connectionState = .scanning
        discoveredPeripherals = []
        central.scanForPeripherals(withServices: [Self.hrServiceUUID])
    }

    func stopScan() {
        central.stopScan()
    }

    func connect(to device: CBPeripheral) {
        stopScan()
        connectionState = .connecting
        peripheral = device
        device.delegate = self
        central.connect(device)
    }

    func disconnect() {
        guard let peripheral else {
            connectionState = .disconnected
            return
        }
        central.cancelPeripheralConnection(peripheral)
        heartRate = nil
        connectionState = .disconnected
    }

    /// Standardowy format Heart Rate Measurement: bajt 0 = flagi; bit 0 flag określa,
    /// czy wartość HR jest uint8 (bajt 1) czy uint16 (bajty 1-2 LE).
    private func parseHeartRate(_ value: Data) -> Int? {
        guard value.count >= 2 else { return nil }
        let flags = value[value.startIndex]
        if flags & 0x01 != 0 {
            guard value.count >= 3 else { return nil }
            return Int(value[value.startIndex + 1]) | (Int(value[value.startIndex + 2]) << 8)
        } else {
            return Int(value[value.startIndex + 1])
        }
    }
}

extension HeartRateManager: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {}

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        guard !discoveredPeripherals.contains(where: { $0.identifier == peripheral.identifier }) else { return }
        discoveredPeripherals.append(peripheral)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        connectionState = .connected
        peripheral.discoverServices([Self.hrServiceUUID])
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        connectionState = .disconnected
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        heartRate = nil
        connectionState = .disconnected
    }
}

extension HeartRateManager: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let service = peripheral.services?.first(where: { $0.uuid == Self.hrServiceUUID }) else { return }
        peripheral.discoverCharacteristics([Self.hrMeasurementUUID], for: service)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard let characteristic = service.characteristics?.first(where: { $0.uuid == Self.hrMeasurementUUID }) else { return }
        peripheral.setNotifyValue(true, for: characteristic)
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard characteristic.uuid == Self.hrMeasurementUUID, let data = characteristic.value else { return }
        if let hr = parseHeartRate(data) {
            heartRate = hr
        }
    }
}
