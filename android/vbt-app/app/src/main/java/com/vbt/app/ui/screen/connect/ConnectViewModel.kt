package com.vbt.app.ui.screen.connect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vbt.app.data.ble.BleConnectionState
import com.vbt.app.data.ble.BleConstants
import com.vbt.app.data.ble.VbtBleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ConnectUiState(
    val esp32State: BleConnectionState = BleConnectionState.DISCONNECTED,
    val esp32Devices: List<ScanResult> = emptyList(),
    val hrState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val hrDevices: List<ScanResult> = emptyList(),
    val heartRate: Int? = null,
    val isScanning: Boolean = false
)

@HiltViewModel
class ConnectViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleManager: VbtBleManager
) : ViewModel() {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private val _uiState = MutableStateFlow(ConnectUiState())
    val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()

    private var currentEsp32ScanCallback: ScanCallback? = null
    private var currentHrScanCallback: ScanCallback? = null

    private var hrGatt: BluetoothGatt? = null
    private var hrCharacteristic: BluetoothGattCharacteristic? = null

    private val hrServiceUuid = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val hrMeasurementUuid = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val clientCharacteristicConfigUuid =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    init {
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(esp32State = state)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun scanForEsp32() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        _uiState.value = _uiState.value.copy(esp32Devices = emptyList(), isScanning = true)

        currentEsp32ScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: ""

                if (name.contains("VBT", ignoreCase = true) ||
                    result.scanRecord?.serviceUuids?.any { it.uuid == BleConstants.VBT_SERVICE_UUID } == true
                ) {
                    val existing = _uiState.value.esp32Devices.toMutableList()
                    val index = existing.indexOfFirst { it.device.address == device.address }
                    if (index >= 0) {
                        existing[index] = result
                    } else {
                        existing.add(result)
                    }
                    _uiState.value = _uiState.value.copy(esp32Devices = existing)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _uiState.value = _uiState.value.copy(isScanning = false)
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        currentEsp32ScanCallback?.let {
            adapter.bluetoothLeScanner?.startScan(emptyList(), settings, it)
        }

        viewModelScope.launch {
            delay(10000)
            stopScanEsp32()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanEsp32() {
        val adapter = bluetoothAdapter ?: return
        currentEsp32ScanCallback?.let {
            adapter.bluetoothLeScanner?.stopScan(it)
        }
        _uiState.value = _uiState.value.copy(isScanning = false)
    }

    @SuppressLint("MissingPermission")
    fun scanForHrMonitor() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        _uiState.value = _uiState.value.copy(hrDevices = emptyList(), isScanning = true)

        currentHrScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val hasHrService = result.scanRecord?.serviceUuids?.any {
                    it.uuid == hrServiceUuid
                } == true

                if (hasHrService) {
                    val existing = _uiState.value.hrDevices.toMutableList()
                    val index = existing.indexOfFirst { it.device.address == device.address }
                    if (index >= 0) {
                        existing[index] = result
                    } else {
                        existing.add(result)
                    }
                    _uiState.value = _uiState.value.copy(hrDevices = existing)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _uiState.value = _uiState.value.copy(isScanning = false)
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        currentHrScanCallback?.let {
            adapter.bluetoothLeScanner?.startScan(emptyList(), settings, it)
        }

        viewModelScope.launch {
            delay(10000)
            stopScanHrMonitor()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanHrMonitor() {
        val adapter = bluetoothAdapter ?: return
        currentHrScanCallback?.let {
            adapter.bluetoothLeScanner?.stopScan(it)
        }
        _uiState.value = _uiState.value.copy(isScanning = false)
    }

    @SuppressLint("MissingPermission")
    fun connectEsp32(device: BluetoothDevice) {
        stopScanEsp32()
        bleManager.connectToDevice(device)
    }

    @SuppressLint("MissingPermission")
    fun connectHrMonitor(device: BluetoothDevice) {
        stopScanHrMonitor()
        _uiState.value = _uiState.value.copy(hrState = BleConnectionState.CONNECTING)

        hrGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    _uiState.value = _uiState.value.copy(hrState = BleConnectionState.CONNECTED)
                    gatt.discoverServices()
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    _uiState.value = _uiState.value.copy(hrState = BleConnectionState.DISCONNECTED)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(hrServiceUuid)
                    hrCharacteristic = service?.getCharacteristic(hrMeasurementUuid)
                    hrCharacteristic?.let {
                        gatt.setCharacteristicNotification(it, true)
                        val descriptor = it.getDescriptor(clientCharacteristicConfigUuid)
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
                if (characteristic.uuid == hrMeasurementUuid && value.isNotEmpty()) {
                    val heartRate = value[1].toInt() and 0xFF
                    _uiState.value = _uiState.value.copy(heartRate = heartRate)
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun disconnectEsp32() {
        _uiState.value = _uiState.value.copy(esp32State = BleConnectionState.DISCONNECTING)
        bleManager.disconnectDevice()
    }

    @SuppressLint("MissingPermission")
    fun disconnectHrMonitor() {
        _uiState.value = _uiState.value.copy(hrState = BleConnectionState.DISCONNECTING)
        hrGatt?.disconnect()
        hrGatt?.close()
        hrGatt = null
        hrCharacteristic = null
        _uiState.value = _uiState.value.copy(hrState = BleConnectionState.DISCONNECTED)
    }

    override fun onCleared() {
        super.onCleared()
        stopScanEsp32()
        stopScanHrMonitor()
        disconnectHrMonitor()
    }
}
