package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.model.ConnectedDeviceInfo
import com.example.model.ConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothTvManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(adapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _tvDeviceName = MutableStateFlow(resolveTvName())
    val tvDeviceName: StateFlow<String> = _tvDeviceName.asStateFlow()

    private val _connectedDevice = MutableStateFlow<ConnectedDeviceInfo?>(null)
    val connectedDevice: StateFlow<ConnectedDeviceInfo?> = _connectedDevice.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<ConnectedDeviceInfo>>(emptyList())
    val pairedDevices: StateFlow<List<ConnectedDeviceInfo>> = _pairedDevices.asStateFlow()

    private val _isDiscoverable = MutableStateFlow(false)
    val isDiscoverable: StateFlow<Boolean> = _isDiscoverable.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
                    refreshDevices()
                }
                BluetoothAdapter.ACTION_SCAN_MODE_CHANGED -> {
                    val scanMode = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_SCAN_MODE,
                        BluetoothAdapter.SCAN_MODE_NONE
                    )
                    val discoverable = scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
                    _isDiscoverable.value = discoverable
                    if (discoverable && _connectionStatus.value != ConnectionStatus.CONNECTED) {
                        _connectionStatus.value = ConnectionStatus.DISCOVERABLE
                    } else if (!discoverable && _connectionStatus.value == ConnectionStatus.DISCOVERABLE) {
                        _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        val deviceName = getSafeDeviceName(device)
                        val info = ConnectedDeviceInfo(
                            name = deviceName,
                            address = device.address ?: "00:11:22:33:44:55",
                            deviceType = if (deviceName.contains("iPhone", ignoreCase = true)) "Apple iPhone" else "Android Telefon"
                        )
                        _connectedDevice.value = info
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    _connectedDevice.value = null
                    _connectionStatus.value = if (_isDiscoverable.value) ConnectionStatus.DISCOVERABLE else ConnectionStatus.DISCONNECTED
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    refreshDevices()
                }
            }
        }
    }

    init {
        registerReceivers()
        refreshDevices()
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (_: Exception) {
            try {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            } catch (_: Exception) {}
        }
    }

    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }

    private fun resolveTvName(): String {
        return try {
            if (hasConnectPermission()) {
                adapter?.name?.takeIf { it.isNotBlank() } ?: "Xspeaker TV (${Build.MODEL})"
            } else {
                "Xspeaker TV (${Build.MODEL})"
            }
        } catch (_: Exception) {
            "Xspeaker TV (${Build.MODEL})"
        }
    }

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun getSafeDeviceName(device: BluetoothDevice): String {
        return try {
            if (hasConnectPermission()) {
                device.name ?: "Akıllı Telefon"
            } else {
                "Akıllı Telefon"
            }
        } catch (_: Exception) {
            "Akıllı Telefon"
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        try {
            if (adapter != null && adapter.isEnabled && hasConnectPermission()) {
                val bonded = adapter.bondedDevices ?: emptySet()
                _pairedDevices.value = bonded.map { dev ->
                    ConnectedDeviceInfo(
                        name = dev.name ?: "Bluetooth Cihazı",
                        address = dev.address ?: "",
                        deviceType = if (dev.name?.contains("iPhone", ignoreCase = true) == true) "Apple iPhone" else "Akıllı Telefon"
                    )
                }
            }
        } catch (_: Exception) {
            // Permission or hardware restriction
        }
    }

    @SuppressLint("MissingPermission")
    fun requestMakeDiscoverable(activity: Activity, durationSeconds: Int = 300) {
        try {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, durationSeconds)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(discoverableIntent)
            _isDiscoverable.value = true
            if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
                _connectionStatus.value = ConnectionStatus.DISCOVERABLE
            }
        } catch (e: Exception) {
            // Fallback to system bluetooth settings if discoverable intent is unsupported on this TV
            openBluetoothSettings()
        }
    }

    fun openBluetoothSettings() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    // Testing simulation for emulator / TV demo experience
    fun simulatePhoneConnection(phoneModel: String = "iPhone 15 Pro Max") {
        if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
            _connectedDevice.value = null
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        } else {
            _connectionStatus.value = ConnectionStatus.CONNECTED
            _connectedDevice.value = ConnectedDeviceInfo(
                name = phoneModel,
                address = "FC:A8:41:8B:20:EE",
                deviceType = if (phoneModel.contains("iPhone")) "Apple iOS Cihazı" else "Android Akıllı Telefon",
                codec = "AAC-HD Hi-Fi (48 kHz / 24-bit)",
                signalStrengthDbm = -42
            )
        }
    }

    fun toggleDiscoverableDemo() {
        if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            _connectedDevice.value = null
            _isDiscoverable.value = false
        } else if (_connectionStatus.value == ConnectionStatus.DISCOVERABLE) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            _isDiscoverable.value = false
        } else {
            _connectionStatus.value = ConnectionStatus.DISCOVERABLE
            _isDiscoverable.value = true
        }
    }
}
