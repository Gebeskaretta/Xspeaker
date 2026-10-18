package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.ConnectionStatus
import com.example.service.AudioAcousticEngine
import com.example.service.BluetoothTvManager
import com.example.service.LocalAudioStreamServer
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.SurfaceCard

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothManager: BluetoothTvManager
    private lateinit var acousticEngine: AudioAcousticEngine
    private lateinit var audioServer: LocalAudioStreamServer
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep TV display and hardware active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Xspeaker:WakeLock")
            wakeLock?.acquire(12 * 60 * 60 * 1000L)
        } catch (_: Exception) {}

        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Xspeaker:WifiLock")
            wifiLock?.acquire()
        } catch (_: Exception) {}

        bluetoothManager = BluetoothTvManager(this)
        acousticEngine = AudioAcousticEngine(this)
        audioServer = LocalAudioStreamServer(this).apply {
            startServer()
        }

        setContent {
            MyApplicationTheme {
                XspeakerScreen(
                    activity = this,
                    bluetoothManager = bluetoothManager,
                    acousticEngine = acousticEngine,
                    audioServer = audioServer
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
                audioServer.togglePlayPause()
                acousticEngine.togglePlayPause()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.unregister()
        audioServer.stopServer()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
    }
}

@Composable
fun XspeakerScreen(
    activity: ComponentActivity,
    bluetoothManager: BluetoothTvManager,
    acousticEngine: AudioAcousticEngine,
    audioServer: LocalAudioStreamServer
) {
    val context = LocalContext.current

    val connectionStatus by bluetoothManager.connectionStatus.collectAsState()
    val tvDeviceName by bluetoothManager.tvDeviceName.collectAsState()
    val connectedDevice by bluetoothManager.connectedDevice.collectAsState()

    val volumePercent by acousticEngine.tvVolume.collectAsState()
    val isMuted by acousticEngine.isMuted.collectAsState()
    val bassPulse by acousticEngine.bassPulse.collectAsState()

    val serverUrl by audioServer.serverUrl.collectAsState()
    val isStreaming by audioServer.isStreaming.collectAsState()
    val currentTrackTitle by audioServer.currentTrackTitle.collectAsState()

    val isAudioActive = isStreaming || connectionStatus == ConnectionStatus.CONNECTED

    // Request Bluetooth permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        bluetoothManager.enableHeadphoneMode()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (permissions.isNotEmpty()) {
                permissionLauncher.launch(permissions.toTypedArray())
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_xspeaker"),
        containerColor = BackgroundDark
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isLandscape = maxWidth > 650.dp

                if (isLandscape) {
                    // Landscape TV 2-Pane Layout
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Speaker Cover Photo
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(24.dp))
                                .border(1.5.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val pulseScale by animateFloatAsState(
                                targetValue = if (isAudioActive) 1f + (bassPulse * 0.05f) else 1f,
                                animationSpec = tween(durationMillis = 80),
                                label = "speakerPulse"
                            )

                            Image(
                                painter = painterResource(id = R.drawable.img_speaker_cover),
                                contentDescription = "Xspeaker Hoparlör Kapağı",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(pulseScale),
                                contentScale = ContentScale.Crop
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                BackgroundDark.copy(alpha = 0.85f)
                                            )
                                        )
                                    )
                            )

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(20.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Speaker,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Xspeaker",
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Text(
                                    text = "TV Bluetooth Kulaklık & Hoparlör Alıcısı",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Right: Connection Panels & Controls
                        Column(
                            modifier = Modifier
                                .weight(1.3f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Status & Test Chime Card
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(
                                        1.dp,
                                        if (isAudioActive) StatusGreen else CyanAccent,
                                        RoundedCornerShape(20.dp)
                                    ),
                                color = SurfaceCard
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .background(
                                                        if (isAudioActive) StatusGreen else CyanAccent,
                                                        CircleShape
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = when {
                                                        isStreaming -> "SES AKTARILIYOR (TV HOPARLÖRÜ)"
                                                        connectionStatus == ConnectionStatus.CONNECTED -> "BAĞLANDI: ${connectedDevice?.name ?: "Telefon"}"
                                                        else -> "KULAKLIK MODU AKTİF • EŞLEŞMEYE HAZIR"
                                                    },
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Cihaz Adı: $tvDeviceName",
                                                    color = CyanAccent,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = { audioServer.playAcousticChime() },
                                            modifier = Modifier.height(36.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.NotificationsActive,
                                                contentDescription = null,
                                                tint = CyanAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Hoparlör Test Sesi", fontSize = 12.sp, color = CyanAccent)
                                        }
                                    }

                                    if (isStreaming && currentTrackTitle != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "🎵 $currentTrackTitle",
                                            color = StatusGreen,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Connection Options
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Bluetooth Headphone Mode Card
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                                    color = SurfaceCard
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .background(CyanAccent.copy(alpha = 0.2f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Headphones,
                                                    contentDescription = null,
                                                    tint = CyanAccent,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(14.dp))

                                            Column {
                                                Text(
                                                    text = "BLUETOOTH KULAKLIK BAĞLANTISI",
                                                    color = CyanAccent,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Xspeaker Kulaklık",
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Telefonunuzun Bluetooth menüsünden bu cihaza bağlanın.",
                                                    color = Color.LightGray,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = { bluetoothManager.requestMakeDiscoverable(activity) },
                                            modifier = Modifier
                                                .height(44.dp)
                                                .testTag("btn_bt_pair"),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = CyanAccent,
                                                contentColor = BackgroundDark
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Headphones,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Kulaklık Olarak Eşleş",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                // Wi-Fi Stream Card
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                                    color = SurfaceCard
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .background(Color(0xFF7C4DFF).copy(alpha = 0.2f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Wifi,
                                                contentDescription = null,
                                                tint = Color(0xFF7C4DFF),
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Wİ-Fİ İLE SES AKTARIMI",
                                                color = Color(0xFF7C4DFF),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = serverUrl,
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            Text(
                                                text = "Telefon tarayıcınızdan bu adresi açarak da ses aktarabilirsiniz.",
                                                color = Color.LightGray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // TV Volume Slider
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                                color = SurfaceCard
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { acousticEngine.toggleMute() },
                                        modifier = Modifier.testTag("btn_mute")
                                    ) {
                                        Icon(
                                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = "Sesi Kapat",
                                            tint = if (isMuted) StatusRed else CyanAccent
                                        )
                                    }

                                    Text(
                                        text = if (isMuted) "SESSİZ" else "TV Sesi: %$volumePercent",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(110.dp)
                                    )

                                    Slider(
                                        value = if (isMuted) 0f else volumePercent.toFloat(),
                                        onValueChange = { acousticEngine.setTvVolume(it.toInt()) },
                                        valueRange = 0f..100f,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                            .testTag("slider_vol"),
                                        colors = SliderDefaults.colors(
                                            thumbColor = CyanAccent,
                                            activeTrackColor = CyanAccent
                                        )
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FilledIconButton(
                                            onClick = { acousticEngine.adjustVolume(-5) },
                                            modifier = Modifier.size(36.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = BackgroundDark)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Azalt", tint = Color.White)
                                        }
                                        FilledIconButton(
                                            onClick = { acousticEngine.adjustVolume(5) },
                                            modifier = Modifier.size(36.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = CyanAccent.copy(alpha = 0.3f))
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Arttır", tint = CyanAccent)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Mobile / Portrait view
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_speaker_cover),
                                contentDescription = "Xspeaker Hoparlör Kapağı",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, BackgroundDark.copy(alpha = 0.8f))
                                        )
                                    )
                            )
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Xspeaker",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "TV Bluetooth Kulaklık Alıcısı",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Bluetooth Headphone Card
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            color = SurfaceCard
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "BLUETOOTH KULAKLIK BAĞLANTISI",
                                    color = CyanAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Xspeaker Kulaklık",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Telefonunuzun Bluetooth menüsünden bu cihaza bağlanın.",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { bluetoothManager.requestMakeDiscoverable(activity) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = BackgroundDark)
                                ) {
                                    Text("Kulaklık Olarak Eşleş", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Wi-Fi Stream Card
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            color = SurfaceCard
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Wİ-Fİ İLE SES AKTARIMI",
                                    color = Color(0xFF7C4DFF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = serverUrl,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Telefon tarayıcınızdan bu adrese girerek ses aktarabilirsiniz.",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Sound test & Volume
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            color = SurfaceCard
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Button(
                                    onClick = { audioServer.playAcousticChime() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = CyanAccent)
                                ) {
                                    Text("🔊 TV Hoparlör Ses Testi")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("TV Sesi: %$volumePercent", color = Color.White, fontSize = 12.sp)
                                Slider(
                                    value = volumePercent.toFloat(),
                                    onValueChange = { acousticEngine.setTvVolume(it.toInt()) },
                                    valueRange = 0f..100f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
