package com.vbt.app.data.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VbtBleManager @Inject constructor(
    @ApplicationContext context: Context
) : BleManager(context) {

    companion object {
        private const val TAG = "VbtBleManager"
        private const val RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_DELAY_MS = 1000L
    }

    private var liveVelocityChar: BluetoothGattCharacteristic? = null
    private var repResultChar: BluetoothGattCharacteristic? = null
    private var deviceStatusChar: BluetoothGattCharacteristic? = null
    private var commandChar: BluetoothGattCharacteristic? = null

    private val _liveVelocity = MutableStateFlow(0f)
    val liveVelocity: StateFlow<Float> = _liveVelocity

    private val _isLifting = MutableStateFlow(false)
    val isLifting: StateFlow<Boolean> = _isLifting

    private val _repCount = MutableStateFlow(0)
    val repCount: StateFlow<Int> = _repCount

    private val _repResult = MutableSharedFlow<RepFromDevice>(extraBufferCapacity = 10)
    val repResult: SharedFlow<RepFromDevice> = _repResult

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState

    // Auto-reconnect: rozłączenie zainicjowane przez użytkownika (disconnectDevice())
    // nie powinno uruchamiać automatycznego wznawiania połączenia.
    private var userInitiatedDisconnect = false
    private var lastDevice: BluetoothDevice? = null

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var reconnectJob: Job? = null

    init {
        setConnectionObserver(object : ConnectionObserver {
            override fun onDeviceConnecting(device: BluetoothDevice) {}
            override fun onDeviceConnected(device: BluetoothDevice) {}
            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {}
            override fun onDeviceReady(device: BluetoothDevice) {
                reconnectJob?.cancel()
                _connectionState.value = BleConnectionState.CONNECTED
            }

            override fun onDeviceDisconnecting(device: BluetoothDevice) {}

            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                if (userInitiatedDisconnect) {
                    _connectionState.value = BleConnectionState.DISCONNECTED
                } else {
                    Log.w(TAG, "Niezamierzone rozłączenie BLE (reason=$reason) - próba wznowienia")
                    startAutoReconnect()
                }
            }
        })
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        val service = gatt.getService(BleConstants.VBT_SERVICE_UUID) ?: return false
        liveVelocityChar = service.getCharacteristic(BleConstants.LIVE_VELOCITY_UUID)
        repResultChar = service.getCharacteristic(BleConstants.REP_RESULT_UUID)
        deviceStatusChar = service.getCharacteristic(BleConstants.DEVICE_STATUS_UUID)
        commandChar = service.getCharacteristic(BleConstants.COMMAND_UUID)
        return liveVelocityChar != null && repResultChar != null && commandChar != null
    }

    override fun initialize() {
        requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH).enqueue()
        requestMtu(64).enqueue()

        setNotificationCallback(liveVelocityChar).with { _, data ->
            parseLiveVelocity(data)
        }
        enableNotifications(liveVelocityChar).enqueue()

        setNotificationCallback(repResultChar).with { _, data ->
            parseRepResult(data)
        }
        enableNotifications(repResultChar).enqueue()
    }

    override fun onServicesInvalidated() {
        liveVelocityChar = null
        repResultChar = null
        deviceStatusChar = null
        commandChar = null
    }

    private fun parseLiveVelocity(data: Data) {
        if (data.size() < 6) return
        val bytes = data.value ?: return
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val velocityRaw = buffer.short
        val flags = bytes[2].toInt() and 0xFF
        buffer.position(3)
        val repCountRaw = buffer.short.toInt() and 0xFFFF

        _liveVelocity.value = velocityRaw / 1000f
        _isLifting.value = (flags and 0x01) != 0
        _repCount.value = repCountRaw
    }

    private fun parseRepResult(data: Data) {
        val rep = RepPacketParser.parse(data.value) ?: return
        _repResult.tryEmit(rep)
    }

    fun sendCommand(command: ByteArray) {
        commandChar?.let {
            writeCharacteristic(it, command).enqueue()
        }
    }

    fun setExerciseParams(minLiftVel: Float, endLiftVel: Float, minRepDist: Float) {
        val data = byteArrayOf(
            BleConstants.CMD_SET_EXERCISE_PARAMS,
            (minLiftVel * 100).toInt().toByte(),
            (endLiftVel * 100).toInt().toByte(),
            (minRepDist * 100).toInt().toByte()
        )
        sendCommand(data)
    }

    fun connectToDevice(device: BluetoothDevice) {
        lastDevice = device
        userInitiatedDisconnect = false
        reconnectJob?.cancel()
        _connectionState.value = BleConnectionState.CONNECTING
        connect(device)
            .retry(3, 200)
            .useAutoConnect(true)
            .done {
                _connectionState.value = BleConnectionState.CONNECTED
            }
            .fail { _, status ->
                Log.w(TAG, "Połączenie BLE nieudane (status=$status)")
                _connectionState.value = BleConnectionState.DISCONNECTED
            }
            .enqueue()
    }

    fun reconnect() {
        lastDevice?.let { connectToDevice(it) }
    }

    private fun startAutoReconnect() {
        val device = lastDevice
        if (device == null) {
            _connectionState.value = BleConnectionState.DISCONNECTED
            return
        }
        _connectionState.value = BleConnectionState.RECONNECTING
        reconnectJob?.cancel()
        reconnectJob = managerScope.launch {
            delay(RECONNECT_DELAY_MS)
            if (userInitiatedDisconnect) return@launch
            connect(device)
                .retry(RECONNECT_ATTEMPTS, RECONNECT_DELAY_MS.toInt())
                .useAutoConnect(true)
                .done {
                    _connectionState.value = BleConnectionState.CONNECTED
                }
                .fail { _, status ->
                    Log.w(TAG, "Auto-reconnect BLE nieudany (status=$status)")
                    _connectionState.value = BleConnectionState.DISCONNECTED
                }
                .enqueue()
        }
    }

    fun disconnectDevice() {
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        _connectionState.value = BleConnectionState.DISCONNECTING
        disconnect()
            .done { _connectionState.value = BleConnectionState.DISCONNECTED }
            .fail { _, _ -> _connectionState.value = BleConnectionState.DISCONNECTED }
            .enqueue()
    }
}
