package com.vbt.app.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Współdzielony (singleton Hilt) manager pasa/opaski tętna korzystający ze
 * standardowego BLE Heart Rate Service. ConnectViewModel łączy/rozłącza,
 * a WorkoutViewModel subskrybuje [heartRate], żeby tętno płynęło do treningu
 * niezależnie od cyklu życia ekranu Connect.
 */
@Singleton
class HeartRateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HR_MEASUREMENT_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var hrGatt: BluetoothGatt? = null

    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate: StateFlow<Int?> = _heartRate

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        disconnect()
        _connectionState.value = BleConnectionState.CONNECTING

        hrGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    _connectionState.value = BleConnectionState.CONNECTED
                    gatt.discoverServices()
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    _connectionState.value = BleConnectionState.DISCONNECTED
                    _heartRate.value = null
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(HR_SERVICE_UUID)
                    val characteristic = service?.getCharacteristic(HR_MEASUREMENT_UUID)
                    characteristic?.let {
                        gatt.setCharacteristicNotification(it, true)
                        val descriptor = it.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                    parseHeartRate(value)?.let { _heartRate.value = it }
                }
            }
        })
    }

    // Standardowy format Heart Rate Measurement: bajt 0 = flagi;
    // bit 0 flag określa, czy wartość HR jest uint8 (bajt 1) czy uint16 (bajty 1-2 LE).
    private fun parseHeartRate(value: ByteArray): Int? {
        if (value.size < 2) return null
        val flags = value[0].toInt() and 0xFF
        return if (flags and 0x01 != 0) {
            if (value.size < 3) null
            else (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        } else {
            value[1].toInt() and 0xFF
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        hrGatt?.disconnect()
        hrGatt?.close()
        hrGatt = null
        _heartRate.value = null
        _connectionState.value = BleConnectionState.DISCONNECTED
    }
}
