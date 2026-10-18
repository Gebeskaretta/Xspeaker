package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
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
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothTvManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scope = CoroutineScope(Dispatchers.IO)

    // Standard Bluetooth Audio & Headset Profile UUIDs
    private val A2DP_SINK_UUID = UUID.fromString("0000110B-0000-1000-8000-00805F9B34FB")
    private val HEADSET_UUID = UUID.fromString("00001108-0000-1000-8000-00805F9B34FB")
    private val HANDSFREE_UUID = UUID.fromString("0000111E-0000-1000-8000-00805F9B34FB")
    private val GENERIC_ACCESS_UUID = UUID.fromString("00001800-0000-1000-8000-00805F9B34FB")
    private val APPEARANCE_UUID = UUID.fromString("00002A01-0000-1000-8000-00805F9B34FB")
    private val DEVICE_NAME_UUID = UUID.fromString("00002A00-0000-1000-8000-00805F9B34FB")
    private val VOLUME_CONTROL_UUID = UUID.fromString("00001844-0000-1000-8000-00805F9B34FB")
    private val AUDIO_STREAM_CONTROL_UUID = UUID.fromString("0000184E-0000-1000-8000-00805F9B34FB")

    // Bluetooth SIG Appearance value for Generic Headphones: 0x03C0 (960)
    // Little-endian byte representation: [0xC0, 0x03]
    private val HEADPHONES_APPEARANCE_BYTES = byteArrayOf(0xC0.toByte(), 0x03.toByte())

    private var a2dpSinkProxy: BluetoothProfile? = null
    private var headsetClientProxy: BluetoothProfile? = null

    private var headsetSocket: BluetoothServerSocket? = null
    private var handsfreeSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null

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

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i("XspeakerBT", "Kulaklık BLE yayını devrede (0x03C0 Appearance)")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w("XspeakerBT", "BLE reklam kodu: $errorCode")
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
                        enableHeadphoneMode()
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
                    disconnectDebounceJob?.cancel()
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    if (device != null) {
                        val name = getSafeDeviceName(device)
                        _connectedDevice.value = ConnectedDeviceInfo(
                            name = name,
                            address = device.address ?: "",
                            deviceType = "Bluetooth Kulaklık / Ses Cihazı"
                        )
                        _connectionStatus.value = ConnectionStatus.CONNECTED

                        // Request audio routing
                        connectAudioRouting(device)
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    // Debounce disconnection to eliminate spam and flapping
                    disconnectDebounceJob?.cancel()
                    disconnectDebounceJob = scope.launch {
                        delay(4000)
                        _connectedDevice.value = null
                        _connectionStatus.value = if (_isDiscoverable.value) {
                            ConnectionStatus.DISCOVERABLE
                        } else {
                            ConnectionStatus.DISCONNECTED
                        }
                    }
                }
            }
        }
    }

    init {
        registerReceivers()
        enableHeadphoneMode()
    }

    @SuppressLint("MissingPermission")
    fun enableHeadphoneMode() {
        if (!hasConnectPermission() || adapter == null || !adapter.isEnabled) return

        try {
            // 1. Explicitly set device name to Headphone identity
            val headphoneName = "Xspeaker Kulaklık"
            adapter.name = headphoneName
            _tvDeviceName.value = headphoneName

            // 2. Set Scan mode to Connectable & Discoverable via reflection if possible
            setDiscoverableReflection()

            // 3. Connect A2DP Sink (11) and Headset Client (16) proxies
            connectProfileProxies()

            // 4. Open GATT Server configured with Headphone appearance (0x03C0) & Audio services
            startHeadphoneGattServer()

            // 5. Start LE Advertising within the 31-byte limit
            startLeHeadphoneAdvertising()

            // 6. Listen on RFCOMM with AT command responder for HFP/HSP
            startRfcommAudioGateway()

        } catch (e: Exception) {
            Log.e("XspeakerBT", "Kulaklık modu başlatma hatası: ${e.message}")
        }
    }

    private fun setDiscoverableReflection() {
        try {
            val method = adapter?.javaClass?.getMethod(
                "setScanMode",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method?.invoke(adapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, 300)
            _isDiscoverable.value = true
        } catch (_: Exception) {
            try {
                val method = adapter?.javaClass?.getMethod(
                    "setScanMode",
                    Int::class.javaPrimitiveType
                )
                method?.invoke(adapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
                _isDiscoverable.value = true
            } catch (_: Exception) {}
        }
    }

    private fun startHeadphoneGattServer() {
        try {
            gattServer?.close()
            val callback = object : BluetoothGattServerCallback() {
                override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED && device != null) {
                        disconnectDebounceJob?.cancel()
                        _connectedDevice.value = ConnectedDeviceInfo(
                            name = getSafeDeviceName(device),
                            address = device.address ?: "",
                            deviceType = "Bluetooth Kulaklık"
                        )
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                    }
                }
            }

            gattServer = bluetoothManager?.openGattServer(context, callback)

            // GAP Service with Appearance Characteristic (0x03C0 = Generic Headphones)
            val gapService = BluetoothGattService(
                GENERIC_ACCESS_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            val appearanceChar = BluetoothGattCharacteristic(
                APPEARANCE_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            appearanceChar.value = HEADPHONES_APPEARANCE_BYTES
            gapService.addCharacteristic(appearanceChar)

            val nameChar = BluetoothGattCharacteristic(
                DEVICE_NAME_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            nameChar.value = "Xspeaker Kulaklık".toByteArray(Charsets.UTF_8)
            gapService.addCharacteristic(nameChar)

            // Audio Sink GATT Service
            val audioSinkService = BluetoothGattService(
                A2DP_SINK_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            // Volume Control GATT Service
            val volumeService = BluetoothGattService(
                VOLUME_CONTROL_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            // Audio Stream Control GATT Service
            val ascsService = BluetoothGattService(
                AUDIO_STREAM_CONTROL_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            gattServer?.addService(gapService)
            gattServer?.addService(audioSinkService)
            gattServer?.addService(volumeService)
            gattServer?.addService(ascsService)
        } catch (e: Exception) {
            Log.w("XspeakerBT", "GATT sunucu hatası: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLeHeadphoneAdvertising() {
        try {
            advertiser = adapter?.bluetoothLeAdvertiser ?: return

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()

            // Main Advertisement (kept < 31 bytes to prevent ADVERTISE_FAILED_DATA_TOO_LARGE)
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false) // Placed in scan response to save packet space
                .addServiceUuid(ParcelUuid(A2DP_SINK_UUID))
                .build()

            // Scan Response Data (contains device name and volume service)
            val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(VOLUME_CONTROL_UUID))
                .build()

            advertiser?.stopAdvertising(advertiseCallback)
            advertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
        } catch (e: Exception) {
            Log.w("XspeakerBT", "LE reklam hatası: ${e.message}")
        }
    }

    private fun connectProfileProxies() {
        try {
            // Profile 11: A2DP_SINK
            adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    a2dpSinkProxy = proxy
                }
                override fun onServiceDisconnected(profile: Int) {
                    a2dpSinkProxy = null
                }
            }, 11)

            // Profile 16: HEADSET_CLIENT
            adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    headsetClientProxy = proxy
                }
                override fun onServiceDisconnected(profile: Int) {
                    headsetClientProxy = null
                }
            }, 16)
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun connectAudioRouting(device: BluetoothDevice) {
        scope.launch {
            try {
                a2dpSinkProxy?.let { proxy ->
                    val connectMethod = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                    connectMethod.invoke(proxy, device)
                }
            } catch (_: Exception) {}

            try {
                headsetClientProxy?.let { proxy ->
                    val connectMethod = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                    connectMethod.invoke(proxy, device)
                }
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRfcommAudioGateway() {
        scope.launch {
            try {
                headsetSocket?.close()
                headsetSocket = adapter?.listenUsingInsecureRfcommWithServiceRecord(
                    "Xspeaker Headset",
                    HEADSET_UUID
                )
                launch {
                    while (true) {
                        val sock = headsetSocket?.accept() ?: break
                        activeSocket = sock
                        handleRfcommClient(sock)
                    }
                }
            } catch (_: Exception) {}

            try {
                handsfreeSocket?.close()
                handsfreeSocket = adapter?.listenUsingInsecureRfcommWithServiceRecord(
                    "Xspeaker Handsfree",
                    HANDSFREE_UUID
                )
                launch {
                    while (true) {
                        val sock = handsfreeSocket?.accept() ?: break
                        activeSocket = sock
                        handleRfcommClient(sock)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun handleRfcommClient(socket: BluetoothSocket) {
        scope.launch {
            var input: InputStream? = null
            var output: OutputStream? = null
            try {
                input = socket.inputStream
                output = socket.getOutputStream()
                val buffer = ByteArray(1024)

                while (socket.isConnected) {
                    val count = input.read(buffer)
                    if (count <= 0) break

                    val text = String(buffer, 0, count, Charsets.US_ASCII)

                    // Respond to AT commands so phone does not timeout and disconnect
                    val response = when {
                        text.contains("AT+BRSF") -> "\r\n+BRSF: 1023\r\n\r\nOK\r\n"
                        text.contains("AT+CIND=?") -> "\r\n+CIND: (\"service\",(0,1)),(\"call\",(0,1))\r\n\r\nOK\r\n"
                        text.contains("AT+CIND?") -> "\r\n+CIND: 1,0\r\n\r\nOK\r\n"
                        text.contains("AT+CMER") -> "\r\nOK\r\n"
                        text.startsWith("AT") -> "\r\nOK\r\n"
                        else -> null
                    }

                    if (response != null && output != null) {
                        output.write(response.toByteArray(Charsets.US_ASCII))
                        output.flush()
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
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
            headsetSocket?.close()
            handsfreeSocket?.close()
            activeSocket?.close()
            gattServer?.close()
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Exception) {}
    }

    private fun resolveTvName(): String {
        return "Xspeaker Kulaklık"
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
        enableHeadphoneMode()
        try {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, durationSeconds)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(discoverableIntent)
            _isDiscoverable.value = true
            _connectionStatus.value = ConnectionStatus.DISCOVERABLE
        } catch (_: Exception) {
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
