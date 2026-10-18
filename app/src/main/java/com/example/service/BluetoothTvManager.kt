package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.provider.Settings
import android.util.Log
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

    // Standard Audio Sink and Headset UUIDs
    private val A2DP_SINK_UUID = UUID.fromString("0000110B-0000-1000-8000-00805F9B34FB")
    private val HEADSET_UUID = UUID.fromString("00001108-0000-1000-8000-00805F9B34FB")

    private var advertiser: BluetoothLeAdvertiser? = null

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

    private var connectStabilityJob: Job? = null
    private var disconnectDebounceJob: Job? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i("XspeakerBT", "Hoparlör / Audio Sink BLE yayını başarıyla başladı!")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.w("XspeakerBT", "BLE yayın hatası: $errorCode")
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
                    if (state == BluetoothAdapter.STATE_ON) {
                        configureSpeakerIdentity()
                    }
                }
                BluetoothAdapter.ACTION_SCAN_MODE_CHANGED -> {
                    val scanMode = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_SCAN_MODE,
                        BluetoothAdapter.SCAN_MODE_NONE
                    )
                    _isDiscoverable.value = (scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
                    if (_isDiscoverable.value && _connectionStatus.value != ConnectionStatus.CONNECTED) {
                        _connectionStatus.value = ConnectionStatus.DISCOVERABLE
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    connectStabilityJob?.cancel()
                    disconnectDebounceJob?.cancel()

                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    if (device != null) {
                        connectStabilityJob = scope.launch {
                            delay(2000)
                            val deviceName = getSafeDeviceName(device)
                            _connectedDevice.value = ConnectedDeviceInfo(
                                name = deviceName,
                                address = device.address ?: "",
                                deviceType = "Telefon (Bluetooth)"
                            )
                            _connectionStatus.value = ConnectionStatus.CONNECTED
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    connectStabilityJob?.cancel()
                    disconnectDebounceJob?.cancel()
                    disconnectDebounceJob = scope.launch {
                        delay(2000)
                        _connectedDevice.value = null
                        _connectionStatus.value = if (_isDiscoverable.value) ConnectionStatus.DISCOVERABLE else ConnectionStatus.DISCONNECTED
                    }
                }
            }
        }
    }

    init {
        registerReceivers()
        configureSpeakerIdentity()
        connectA2dpSinkProfile()
    }

    @SuppressLint("MissingPermission")
    fun configureSpeakerIdentity() {
        if (!hasConnectPermission() || adapter == null || !adapter.isEnabled) return
        try {
            // Set device name so phone's Bluetooth UI recognizes it as a Speaker / Audio Device!
            val desiredName = "Xspeaker Hoparlör (TV)"
            adapter.name = desiredName
            _tvDeviceName.value = desiredName

            // Start BLE advertising with Audio Sink & Headset UUIDs
            startSpeakerAdvertising()
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun startSpeakerAdvertising() {
        try {
            advertiser = adapter?.bluetoothLeAdvertiser ?: return

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(A2DP_SINK_UUID))
                .addServiceUuid(ParcelUuid(HEADSET_UUID))
                .build()

            advertiser?.stopAdvertising(advertiseCallback)
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.w("XspeakerBT", "Yayın başlatılamadı: ${e.message}")
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
            connectStabilityJob?.cancel()
            disconnectDebounceJob?.cancel()
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Exception) {}
    }

    private fun resolveTvName(): String {
        return try {
            if (hasConnectPermission()) {
                adapter?.name?.takeIf { it.isNotBlank() } ?: "Xspeaker Hoparlör (TV)"
            } else {
                "Xspeaker Hoparlör (TV)"
            }
        } catch (_: Exception) {
            "Xspeaker Hoparlör (TV)"
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
        configureSpeakerIdentity()
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
