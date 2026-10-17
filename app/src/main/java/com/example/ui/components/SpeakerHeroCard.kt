package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.SettingsBluetooth
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ConnectionStatus
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanAccentGlow
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.SurfaceCard

@Composable
fun SpeakerHeroCard(
    tvDeviceName: String,
    connectionStatus: ConnectionStatus,
    bassPulse: Float,
    isSoundTesting: Boolean,
    onMakeDiscoverable: () -> Unit,
    onOpenSettings: () -> Unit,
    onSoundCheck: () -> Unit,
    onOpenAmbient: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedScale by animateFloatAsState(
        targetValue = 1f + (bassPulse * 0.08f),
        animationSpec = tween(durationMillis = 80),
        label = "bassScale"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
            .shadow(16.dp, RoundedCornerShape(24.dp)),
        color = SurfaceCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Speaker Cover Photo Frame with dynamic acoustic pulse
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, CyanAccent.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Speaker photo (Generated luxury audio monitor asset)
                Image(
                    painter = painterResource(id = R.drawable.img_speaker_cover),
                    contentDescription = "Xspeaker Hoparlör Kapağı",
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(animatedScale),
                    contentScale = ContentScale.Crop
                )

                // Cinematic gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    BackgroundDark.copy(alpha = 0.5f),
                                    BackgroundDark.copy(alpha = 0.92f)
                                )
                            )
                        )
                )

                // Top Badge: Status Pill
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .background(BackgroundDark.copy(alpha = 0.85f), RoundedCornerShape(30.dp))
                        .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(30.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusColor = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> StatusGreen
                        ConnectionStatus.DISCOVERABLE, ConnectionStatus.CONNECTING -> StatusAmber
                        ConnectionStatus.DISCONNECTED -> CyanAccent
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> "SES AKTARILIYOR"
                            ConnectionStatus.DISCOVERABLE -> "EŞLEŞME BEKLENİYOR"
                            ConnectionStatus.CONNECTING -> "BAĞLANILIYOR..."
                            ConnectionStatus.DISCONNECTED -> "YAYINA HAZIR"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                // Center Acoustic Bass Pulse Visualizer Ring
                Box(
                    modifier = Modifier
                        .size(100.dp + (45.dp * bassPulse))
                        .border(
                            width = (1.5f + bassPulse * 3f).dp,
                            color = if (connectionStatus == ConnectionStatus.CONNECTED) CyanAccent.copy(alpha = 0.8f) else CyanAccentGlow,
                            shape = CircleShape
                        )
                )

                // Bottom Speaker Info
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Xspeaker Hi-Fi Studio",
                        color = CyanAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = tvDeviceName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "TV Hoparlör Akustik Alıcısı • 48 kHz / 24-bit",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Row (TV Remote / Touch friendly)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary: Make TV Discoverable for phone pairing
                Button(
                    onClick = onMakeDiscoverable,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("btn_discoverable"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (connectionStatus == ConnectionStatus.DISCOVERABLE) StatusAmber else CyanAccent,
                        contentColor = BackgroundDark
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (connectionStatus == ConnectionStatus.DISCOVERABLE) "Eşleşme Açık" else "Eşleşme Modu",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Sound Check Test Button
                FilledTonalButton(
                    onClick = onSoundCheck,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_sound_check"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = PurpleAccent.copy(alpha = 0.25f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = CyanAccent
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSoundTesting) "Çalıyor..." else "Ses Testi",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Ambient OLED Dimmer Button
                OutlinedButton(
                    onClick = onOpenAmbient,
                    modifier = Modifier
                        .weight(0.9f)
                        .height(48.dp)
                        .testTag("btn_ambient"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Nightlight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = CyanAccent
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "OLED Mod",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
