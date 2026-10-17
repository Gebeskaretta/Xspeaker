package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.VisualizerMode
import com.example.service.AudioAcousticEngine
import com.example.service.BluetoothTvManager
import com.example.ui.components.AmbientOledScreenSaver
import com.example.ui.components.AudioVisualizerView
import com.example.ui.components.ConnectedDeviceCard
import com.example.ui.components.ConnectionGuideDialog
import com.example.ui.components.SoundPresetSelector
import com.example.ui.components.SpeakerHeroCard
import com.example.ui.components.TvVolumeControl
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothManager: BluetoothTvManager
    private lateinit var acousticEngine: AudioAcousticEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bluetoothManager = BluetoothTvManager(this)
        acousticEngine = AudioAcousticEngine(this)

        setContent {
            MyApplicationTheme {
                MainTvSpeakerScreen(
                    activity = this,
                    bluetoothManager = bluetoothManager,
                    acousticEngine = acousticEngine
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                acousticEngine.adjustVolume(5)
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                acousticEngine.adjustVolume(-5)
                true
            }
            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                acousticEngine.toggleMute()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                acousticEngine.togglePlayPause()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.unregister()
    }
}

@Composable
fun MainTvSpeakerScreen(
    activity: ComponentActivity,
    bluetoothManager: BluetoothTvManager,
    acousticEngine: AudioAcousticEngine
) {
    val context = LocalContext.current

    val connectionStatus by bluetoothManager.connectionStatus.collectAsState()
    val tvDeviceName by bluetoothManager.tvDeviceName.collectAsState()
    val connectedDevice by bluetoothManager.connectedDevice.collectAsState()

    val volumePercent by acousticEngine.tvVolume.collectAsState()
    val isMuted by acousticEngine.isMuted.collectAsState()
    val currentPreset by acousticEngine.currentPreset.collectAsState()
    val isSoundTesting by acousticEngine.isSoundTesting.collectAsState()
    val frequencyBands by acousticEngine.frequencyBands.collectAsState()
    val bassPulse by acousticEngine.bassPulse.collectAsState()

    var selectedVizMode by remember { mutableStateOf(VisualizerMode.SPECTRUM_BARS) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var isAmbientScreenSaverActive by remember { mutableStateOf(false) }

    // Bluetooth permission launcher for Android 12+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        bluetoothManager.refreshDevices()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissionsToRequest = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (permissionsToRequest.isNotEmpty()) {
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        }
    }

    // Back handling when ambient screen or dialog is open
    BackHandler(enabled = isAmbientScreenSaverActive || showGuideDialog) {
        if (isAmbientScreenSaverActive) {
            isAmbientScreenSaverActive = false
        } else if (showGuideDialog) {
            showGuideDialog = false
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_main_tv"),
        containerColor = BackgroundDark
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth > 680.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    // Top App Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(CyanAccent.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speaker,
                                    contentDescription = "Xspeaker Logo",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Xspeaker",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(CyanAccent.copy(alpha = 0.2f), CircleShape)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "TV ALICISI",
                                            color = CyanAccent,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = "Telefon Seslerini TV Ses Sistemine Aktarın",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Right Device Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tvDeviceName,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Main Layout (Adaptive: 2-Column on TV / Landscape, 1-Column on Phone)
                    if (isWideScreen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            // Left Column: Speaker Hero Card (Cover Photo, Status, Quick Actions)
                            SpeakerHeroCard(
                                tvDeviceName = tvDeviceName,
                                connectionStatus = connectionStatus,
                                bassPulse = bassPulse,
                                isSoundTesting = isSoundTesting,
                                onMakeDiscoverable = { bluetoothManager.requestMakeDiscoverable(activity) },
                                onOpenSettings = { bluetoothManager.openBluetoothSettings() },
                                onSoundCheck = { acousticEngine.playAcousticSoundCheck() },
                                onOpenAmbient = { isAmbientScreenSaverActive = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )

                            // Right Column: Connected Device + Visualizer + Presets + Volume
                            Column(
                                modifier = Modifier
                                    .weight(1.35f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Active Connected Phone Card
                                ConnectedDeviceCard(
                                    connectionStatus = connectionStatus,
                                    connectedDevice = connectedDevice,
                                    onSimulateTestConnection = { bluetoothManager.simulatePhoneConnection() },
                                    onShowGuide = { showGuideDialog = true },
                                    onDisconnect = { bluetoothManager.simulatePhoneConnection() },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Live 32-band Frequency Visualizer
                                AudioVisualizerView(
                                    frequencyBands = frequencyBands,
                                    bassPulse = bassPulse,
                                    selectedMode = selectedVizMode,
                                    onSelectMode = { selectedVizMode = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                )

                                // DSP Sound Presets
                                SoundPresetSelector(
                                    selectedPreset = currentPreset,
                                    onSelectPreset = { acousticEngine.selectPreset(it) },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // TV Volume Slider & Step Controls
                                TvVolumeControl(
                                    volumePercent = volumePercent,
                                    isMuted = isMuted,
                                    onVolumeChange = { acousticEngine.setTvVolume(it) },
                                    onAdjustVolume = { acousticEngine.adjustVolume(it) },
                                    onToggleMute = { acousticEngine.toggleMute() },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        // Portrait / Narrow Viewport Scrollable
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SpeakerHeroCard(
                                tvDeviceName = tvDeviceName,
                                connectionStatus = connectionStatus,
                                bassPulse = bassPulse,
                                isSoundTesting = isSoundTesting,
                                onMakeDiscoverable = { bluetoothManager.requestMakeDiscoverable(activity) },
                                onOpenSettings = { bluetoothManager.openBluetoothSettings() },
                                onSoundCheck = { acousticEngine.playAcousticSoundCheck() },
                                onOpenAmbient = { isAmbientScreenSaverActive = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(340.dp)
                            )

                            ConnectedDeviceCard(
                                connectionStatus = connectionStatus,
                                connectedDevice = connectedDevice,
                                onSimulateTestConnection = { bluetoothManager.simulatePhoneConnection() },
                                onShowGuide = { showGuideDialog = true },
                                onDisconnect = { bluetoothManager.simulatePhoneConnection() },
                                modifier = Modifier.fillMaxWidth()
                            )

                            AudioVisualizerView(
                                frequencyBands = frequencyBands,
                                bassPulse = bassPulse,
                                selectedMode = selectedVizMode,
                                onSelectMode = { selectedVizMode = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )

                            SoundPresetSelector(
                                selectedPreset = currentPreset,
                                onSelectPreset = { acousticEngine.selectPreset(it) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            TvVolumeControl(
                                volumePercent = volumePercent,
                                isMuted = isMuted,
                                onVolumeChange = { acousticEngine.setTvVolume(it) },
                                onAdjustVolume = { acousticEngine.adjustVolume(it) },
                                onToggleMute = { acousticEngine.toggleMute() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Connection Guide Dialog
            if (showGuideDialog) {
                ConnectionGuideDialog(
                    tvDeviceName = tvDeviceName,
                    onDismiss = { showGuideDialog = false }
                )
            }

            // OLED Screen Saver Overlay
            if (isAmbientScreenSaverActive) {
                AmbientOledScreenSaver(
                    connectedDevice = connectedDevice,
                    bassPulse = bassPulse,
                    onDismiss = { isAmbientScreenSaverActive = false }
                )
            }
        }
    }
}
