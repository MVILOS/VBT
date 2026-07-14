import CoreBluetooth
import Foundation
import Observation

/// Port `data/ble/VbtBleManager.kt` z Nordic Android BLE na CoreBluetooth.
///
/// NIEZWERYFIKOWANE NA SPRZĘCIE: ten plik był napisany bez dostępu do Xcode/fizycznego
/// iPhone'a. Zachowanie CoreBluetooth przy częstych notify (~co 0.1s z FF01) i przy
/// reconnект w tle wymaga testu na iPhone 15 Pro + realnym ESP32 przed dalszą rozbudową
/// (patrz IOS_PORT_PLAN.md, Faza 0 / punkt "spike ryzyka").
@Observable
final class VbtBleManager: NSObject {
    private static let reconnectAttempts = 5
    private static let reconnectDelay: TimeInterval = 1.0

    private(set) var liveVelocity: Float = 0
    private(set) var isLifting = false
    private(set) var repCount = 0
    private(set) var connectionState: BleConnectionState = .disconnected
    private(set) var discoveredPeripherals: [CBPeripheral] = []

    /// Konsumowane przez WorkoutViewModel — jedna wartość na powtórzenie odebrane z FF02.
    var onRepResult: ((RepFromDevice) -> Void)?

    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var liveVelocityChar: CBCharacteristic?
    private var repResultChar: CBCharacteristic?
    private var deviceStatusChar: CBCharacteristic?
    private var commandChar: CBCharacteristic?

    // Auto-reconnect: rozłączenie zainicjowane przez użytkownika (disconnect())
    // nie powinno uruchamiać automatycznego wznawiania połączenia.
    private var userInitiatedDisconnect = false
    private var reconnectTask: Task<Void, Never>?

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: .main)
    }

    func startScan() {
        guard central.state == .poweredOn else { return }
        connectionState = .scanning
        discoveredPeripherals = []
        central.scanForPeripherals(withServices: [BleConstants.vbtServiceUUID])
    }

    func stopScan() {
        central.stopScan()
    }

    func connect(to device: CBPeripheral) {
        stopScan()
        userInitiatedDisconnect = false
        reconnectTask?.cancel()
        connectionState = .connecting
        peripheral = device
        device.delegate = self
        central.connect(device)
    }

    /// Ponowna próba połączenia z ostatnio używanym urządzeniem (np. przycisk "Reconnect"
    /// na WorkoutScreen po tym, jak auto-reconnect wyczerpał limit prób).
    func reconnect() {
        guard let peripheral else { return }
        connect(to: peripheral)
    }

    func disconnect() {
        userInitiatedDisconnect = true
        reconnectTask?.cancel()
        guard let peripheral else {
            connectionState = .disconnected
            return
        }
        connectionState = .disconnecting
        central.cancelPeripheralConnection(peripheral)
    }

    func sendCommand(_ bytes: [UInt8]) {
        guard let peripheral, let commandChar else { return }
        peripheral.writeValue(Data(bytes), for: commandChar, type: .withResponse)
    }

    func setExerciseParams(minLiftVel: Float, endLiftVel: Float, minRepDist: Float) {
        let bytes: [UInt8] = [
            BleConstants.Command.setExerciseParams.rawValue,
            UInt8(clamping: Int(minLiftVel * 100)),
            UInt8(clamping: Int(endLiftVel * 100)),
            UInt8(clamping: Int(minRepDist * 100))
        ]
        sendCommand(bytes)
    }

    private func startAutoReconnect() {
        guard let peripheral, !userInitiatedDisconnect else {
            connectionState = .disconnected
            return
        }
        connectionState = .reconnecting
        reconnectTask?.cancel()
        reconnectTask = Task { [weak self] in
            guard let self else { return }
            for attempt in 1...Self.reconnectAttempts {
                try? await Task.sleep(for: .seconds(Self.reconnectDelay))
                if Task.isCancelled || self.userInitiatedDisconnect { return }
                self.central.connect(peripheral)
                // Czekamy na wynik przez connectionState ustawiany w delegate; jeśli po
                // kolejnej próbie nadal nie jesteśmy połączeni, próbujemy dalej.
                try? await Task.sleep(for: .milliseconds(500))
                if self.connectionState == .connected { return }
                if attempt == Self.reconnectAttempts {
                    self.connectionState = .disconnected
                }
            }
        }
    }

    private func parseLiveVelocity(_ data: Data) {
        guard data.count >= 6 else { return }
        let velocityRaw = Int16(data[data.startIndex]) | (Int16(data[data.startIndex + 1]) << 8)
        let flags = data[data.startIndex + 2]
        let repCountRaw = UInt16(data[data.startIndex + 3]) | (UInt16(data[data.startIndex + 4]) << 8)

        liveVelocity = Float(velocityRaw) / 1000
        isLifting = (flags & 0x01) != 0
        repCount = Int(repCountRaw)
    }

    private func parseRepResult(_ data: Data) {
        guard let rep = RepPacketParser.parse(data) else { return }
        onRepResult?(rep)
    }
}

extension VbtBleManager: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        // Nic nie robimy automatycznie — UI (ConnectScreen) decyduje kiedy zacząć skan,
        // pokazując stan `central.state` (np. poproszenie o włączenie Bluetootha).
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        guard !discoveredPeripherals.contains(where: { $0.identifier == peripheral.identifier }) else { return }
        discoveredPeripherals.append(peripheral)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        peripheral.discoverServices([BleConstants.vbtServiceUUID])
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        connectionState = .disconnected
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        liveVelocityChar = nil
        repResultChar = nil
        deviceStatusChar = nil
        commandChar = nil

        if userInitiatedDisconnect {
            connectionState = .disconnected
        } else {
            startAutoReconnect()
        }
    }
}

extension VbtBleManager: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let service = peripheral.services?.first(where: { $0.uuid == BleConstants.vbtServiceUUID }) else { return }
        peripheral.discoverCharacteristics(
            [BleConstants.liveVelocityUUID, BleConstants.repResultUUID, BleConstants.deviceStatusUUID, BleConstants.commandUUID],
            for: service
        )
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard let characteristics = service.characteristics else { return }
        for characteristic in characteristics {
            switch characteristic.uuid {
            case BleConstants.liveVelocityUUID:
                liveVelocityChar = characteristic
                peripheral.setNotifyValue(true, for: characteristic)
            case BleConstants.repResultUUID:
                repResultChar = characteristic
                peripheral.setNotifyValue(true, for: characteristic)
            case BleConstants.deviceStatusUUID:
                deviceStatusChar = characteristic
            case BleConstants.commandUUID:
                commandChar = characteristic
            default:
                break
            }
        }

        // "Ready" = mamy wymagane charakterystyki (odpowiednik `isRequiredServiceSupported`
        // z Nordic BleManager, które warunkowało `onDeviceReady`).
        if liveVelocityChar != nil, repResultChar != nil, commandChar != nil {
            reconnectTask?.cancel()
            connectionState = .connected
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard let data = characteristic.value else { return }
        switch characteristic.uuid {
        case BleConstants.liveVelocityUUID:
            parseLiveVelocity(data)
        case BleConstants.repResultUUID:
            parseRepResult(data)
        default:
            break
        }
    }
}
