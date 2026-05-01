package com.vbt.app.data.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VbtBleManager @Inject constructor(
    @ApplicationContext context: Context
) : BleManager(context) {

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
        _connectionState.value = BleConnectionState.DISCONNECTED
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
        if (data.size() < 16) return
        val bytes = data.value ?: return
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val velocity = (buffer.short.toInt() and 0xFFFF) / 1000f
        val distance = (buffer.short.toInt() and 0xFFFF) / 1000f
        val duration = buffer.short.toInt() and 0xFFFF
        val timestamp = buffer.int.toLong() and 0xFFFFFFFFL
        val repIndex = buffer.short.toInt() and 0xFFFF

        _repResult.tryEmit(
            RepFromDevice(
                maxVelocityMs = velocity,
                distanceM = distance,
                durationMs = duration,
                deviceTimestamp = timestamp,
                repIndex = repIndex
            )
        )
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

    private var lastDevice: BluetoothDevice? = null

    fun connectToDevice(device: BluetoothDevice) {
        lastDevice = device
        _connectionState.value = BleConnectionState.CONNECTING
        connect(device)
            .retry(3, 200)
            .useAutoConnect(true)
            .done {
                _connectionState.value = BleConnectionState.CONNECTED
            }
            .fail { _, _ ->
                _connectionState.value = BleConnectionState.DISCONNECTED
            }
            .enqueue()
    }

    fun reconnect() {
        lastDevice?.let { connectToDevice(it) }
    }

    fun disconnectDevice() {
        _connectionState.value = BleConnectionState.DISCONNECTING
        disconnect().enqueue()
    }
}
