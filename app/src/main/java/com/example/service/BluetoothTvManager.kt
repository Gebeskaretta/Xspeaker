package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class BluetoothTvManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scope = CoroutineScope(Dispatchers.IO)

    // Standard Bluetooth A2DP Audio Sink Service UUIDs
    private val A2DP_SINK_UUID = UUID.fromString("0000110B-0000-1000-8000-00805F9B34FB")

    private var serverSocket: BluetoothServerSocket? = null
    private var activeClientSocket: BluetoothSocket? = null
    private var clientReaderJob: Job? = null
    private var disconnectDebounceJob: Job? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(adapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _tvDeviceName = MutableStateFlow(resolveTvName())
    val tvDeviceName: StateFlow<String> = _tvDeviceName.asStateFlow()

    private val _connectedDevice = MutableStateFlow<ConnectedDeviceInfo?>(null)
    val connectedDevice: StateFlow<ConnectedDeviceInfo?> = _connectedDevice.asStateFlow()

    private val _isDiscoverable = MutableStateFlow(false)
    val isDiscoverable: StateFlow<Boolean> = _isDiscoverable.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
                    if (state == BluetoothAdapter.STATE_ON) {
                        startAudioSinkService()
                    }
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
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    disconnectDebounceJob?.cancel()
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        val deviceName = getSafeDeviceName(device)
                        _connectedDevice.value = ConnectedDeviceInfo(
                            name = deviceName,
                            address = device.address ?: "",
                            deviceType = if (deviceName.contains("iPhone", ignoreCase = true)) "Apple iPhone" else "Akıllı Telefon"
                        )
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    // Debounce disconnect so brief packet losses or profile handshakes don't drop the connection
                    disconnectDebounceJob?.cancel()
                    disconnectDebounceJob = scope.launch {
                        delay(2500)
                        if (activeClientSocket == null || activeClientSocket?.isConnected != true) {
                            _connectedDevice.value = null
                            _connectionStatus.value = if (_isDiscoverable.value) ConnectionStatus.DISCOVERABLE else ConnectionStatus.DISCONNECTED
                        }
                    }
                }
            }
        }
    }

    init {
        registerReceivers()
        startAudioSinkService()
        connectA2dpSinkProfile()
    }

    @SuppressLint("MissingPermission")
    private fun startAudioSinkService() {
        if (!hasConnectPermission() || adapter == null || !adapter.isEnabled) return
        scope.launch {
            try {
                serverSocket?.close()
                // Register SDP record as Audio Sink on the Bluetooth daemon
                serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord(
                    "Xspeaker Audio Sink",
                    A2DP_SINK_UUID
                )
                // Listen for incoming connection
                while (true) {
                    val socket = serverSocket?.accept() ?: break
                    activeClientSocket?.close()
                    activeClientSocket = socket

                    val remoteDevice = socket.remoteDevice
                    if (remoteDevice != null) {
                        val name = getSafeDeviceName(remoteDevice)
                        _connectedDevice.value = ConnectedDeviceInfo(
                            name = name,
                            address = remoteDevice.address ?: "",
                            deviceType = "Telefon (Bluetooth Hoparlör)"
                        )
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                    }

                    // Keep socket active with a persistent reader loop to prevent timeouts
                    clientReaderJob?.cancel()
                    clientReaderJob = scope.launch {
                        try {
                            val inStream = socket.inputStream
                            val buf = ByteArray(2048)
                            while (socket.isConnected) {
                                val bytes = inStream.read(buf)
                                if (bytes == -1) break
                            }
                        } catch (_: Exception) {
                        } finally {
                            if (activeClientSocket == socket) {
                                activeClientSocket = null
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun connectA2dpSinkProfile() {
        try {
            val A2DP_SINK_PROFILE = 11
            adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {}
                override fun onServiceDisconnected(profile: Int) {}
            }, A2DP_SINK_PROFILE)
        } catch (_: Exception) {}
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
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
            clientReaderJob?.cancel()
            activeClientSocket?.close()
            serverSocket?.close()
        } catch (_: Exception) {}
    }

    private fun resolveTvName(): String {
        return try {
            if (hasConnectPermission()) {
                adapter?.name?.takeIf { it.isNotBlank() } ?: "Profilo Smart TV"
            } else {
                "Profilo Smart TV"
            }
        } catch (_: Exception) {
            "Profilo Smart TV"
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
    fun requestMakeDiscoverable(activity: Activity, durationSeconds: Int = 300) {
        startAudioSinkService()
        try {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, durationSeconds)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(discoverableIntent)
            _isDiscoverable.value = true
            _connectionStatus.value = ConnectionStatus.DISCOVERABLE
        } catch (e: Exception) {
            openBluetoothSettings()
        }
    }

    fun openBluetoothSettings() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
