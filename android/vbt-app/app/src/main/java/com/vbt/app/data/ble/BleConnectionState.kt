package com.vbt.app.data.ble

enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    // Niezamierzone rozłączenie - trwa automatyczna próba wznowienia połączenia
    RECONNECTING
}
