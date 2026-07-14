/// Port 1:1 z Android `data/ble/BleConnectionState.kt`.
enum BleConnectionState: Equatable {
    case disconnected
    case scanning
    case connecting
    case connected
    case disconnecting
    /// Niezamierzone rozłączenie — trwa automatyczna próba wznowienia połączenia.
    case reconnecting
}
