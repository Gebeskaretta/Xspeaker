package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bluetoothManager = BluetoothTvManager(this)
        acousticEngine = AudioAcousticEngine(this)
        audioServer = LocalAudioStreamServer(this).apply {
            startServer(8080)
        }

        setContent {
            MyApplicationTheme {
                CleanXspeakerScreen(
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
    }
}

@Composable
fun CleanXspeakerScreen(
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

    // Permission launcher for Android 12+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Permissions handled */ }

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
            .testTag("screen_clean_xspeaker"),
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
                    // Landscape TV 2-Pane Layout (Clear & Focused)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: High Quality Speaker Cover Photo
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(28.dp))
                                .border(1.5.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(28.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val pulseScale by animateFloatAsState(
                                targetValue = if (isStreaming || connectionStatus == ConnectionStatus.CONNECTED) 1f + (bassPulse * 0.06f) else 1f,
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

                            // Gradient overlay
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

                            // Bottom title over speaker photo
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
                                    text = "TV Bluetooth & Kablosuz Hoparlör Alıcısı",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Right: Direct Audio Connection Controls
                        Column(
                            modifier = Modifier
                                .weight(1.3f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top Active Audio Status Card
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(
                                        1.dp,
                                        if (isStreaming || connectionStatus == ConnectionStatus.CONNECTED) StatusGreen else CyanAccent,
                                        RoundedCornerShape(20.dp)
                                    ),
                                color = SurfaceCard
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
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
                                                        if (isStreaming || connectionStatus == ConnectionStatus.CONNECTED) StatusGreen else CyanAccent,
                                                        CircleShape
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isStreaming) "SES TELEVİZYONDAN ÇALIYOR" else if (connectionStatus == ConnectionStatus.CONNECTED) "TELEFON BAĞLANDI" else "BAĞLANTIYA HAZIR",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Text(
                                            text = tvDeviceName,
                                            color = CyanAccent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    if (isStreaming) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "🎵 ${currentTrackTitle ?: "Telefon Ses Akışı Aktif"}",
                                            color = StatusGreen,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Connection Methods (Clear & Direct)
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Method 1: Instant Web Streamer (100% Guaranteed to work on every TV & Phone!)
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
                                                .background(CyanAccent.copy(alpha = 0.2f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Wifi,
                                                contentDescription = null,
                                                tint = CyanAccent,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "1. YÖNTEM: TELEFON TARAYICISINDAN AÇIN",
                                                color = CyanAccent,
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
                                                text = "Telefonunuzun tarayıcısından (Chrome/Safari) bu adresi açıp şarkınızı seçin; anında TV'den çalar!",
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                // Method 2: Bluetooth Speaker Mode
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
                                                    .background(Color(0xFF7C4DFF).copy(alpha = 0.2f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Bluetooth,
                                                    contentDescription = null,
                                                    tint = Color(0xFF7C4DFF),
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(14.dp))

                                            Column {
                                                Text(
                                                    text = "2. YÖNTEM: BLUETOOTH İLE EŞLEŞTİRME",
                                                    color = Color(0xFF7C4DFF),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Telefonda \"Profilo Smart TV\"yi Unutun",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Telefonunuzda önceki eşleşmeyi silip aşağıdaki butona basarak tekrar eşleştirin.",
                                                    color = Color.LightGray,
                                                    fontSize = 11.sp
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
                                                imageVector = Icons.Default.BluetoothSearching,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Eşleşme Modunu Aç",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // TV Volume Bar
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
                                        modifier = Modifier.testTag("btn_clean_mute")
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
                                            .testTag("slider_clean_vol"),
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
                    // Portrait Mode (Mobile / Phone preview)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Speaker Cover Photo
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
                            Text(
                                text = "Xspeaker • TV Hoparlör Alıcısı",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        // Method 1: Direct link
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            color = SurfaceCard
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "1. YÖNTEM: TARAYICIDAN AÇIN",
                                    color = CyanAccent,
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
                                    text = "Telefonunuzdan bu adrese girip istediğiniz müziği seçin, anında TV'den çalsın!",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Method 2: Bluetooth
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            color = SurfaceCard
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "2. YÖNTEM: BLUETOOTH İLE BAĞLANTI",
                                    color = Color(0xFF7C4DFF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Telefonda Profilo Smart TV cihazını 'Unut' deyin, ardından butona basıp yeniden eşleştirin.",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { bluetoothManager.requestMakeDiscoverable(activity) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = BackgroundDark)
                                ) {
                                    Text("Eşleşme Modunu Aç", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Volume Control
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            color = SurfaceCard
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("TV Sesi: %$volumePercent", color = Color.White, fontSize = 12.sp)
                                Slider(
                                    value = volumePercent.toFloat(),
                                    onValueChange = { acousticEngine.setTvVolume(it.toInt()) },
                                    valueRange = 0f..100f,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
