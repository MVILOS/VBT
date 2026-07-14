import CoreBluetooth

/// Port 1:1 z Android `data/ble/BleConstants.kt` — protokół urządzenia (firmware ESP32,
/// `src/ble_server.cpp`) jest współdzielony między platformami i nie zmienia się tutaj.
enum BleConstants {
    static let vbtServiceUUID = CBUUID(string: "0000FF00-1234-5678-9ABC-DEF012345678")
    static let liveVelocityUUID = CBUUID(string: "0000FF01-1234-5678-9ABC-DEF012345678")
    static let repResultUUID = CBUUID(string: "0000FF02-1234-5678-9ABC-DEF012345678")
    static let deviceStatusUUID = CBUUID(string: "0000FF03-1234-5678-9ABC-DEF012345678")
    static let commandUUID = CBUUID(string: "0000FF04-1234-5678-9ABC-DEF012345678")

    enum Command: UInt8 {
        case resetReps = 0x01
        case clearHistory = 0x02
        case setExerciseParams = 0x03
        case ping = 0x04
    }

    static let deviceName = "VBT-Vector"
}
