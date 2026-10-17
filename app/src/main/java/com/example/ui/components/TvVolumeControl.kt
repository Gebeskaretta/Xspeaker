package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.StatusRed
import com.example.ui.theme.SurfaceCard

@Composable
fun TvVolumeControl(
    volumePercent: Int,
    isMuted: Boolean,
    onVolumeChange: (Int) -> Unit,
    onAdjustVolume: (Int) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
        color = SurfaceCard
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Mute Icon button
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isMuted) StatusRed.copy(alpha = 0.2f) else BackgroundDark.copy(alpha = 0.6f),
                        CircleShape
                    )
                    .testTag("btn_mute")
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else if (volumePercent > 50) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                    contentDescription = "Sesi Kapat / Aç",
                    tint = if (isMuted) StatusRed else CyanAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Volume Label & Value
            Column(modifier = Modifier.width(90.dp)) {
                Text(
                    text = "TV SES DÜZEYİ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = if (isMuted) "SESSİZ" else "%$volumePercent",
                    color = if (isMuted) StatusRed else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Slider
            Slider(
                value = if (isMuted) 0f else volumePercent.toFloat(),
                onValueChange = { onVolumeChange(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .testTag("slider_volume"),
                colors = SliderDefaults.colors(
                    thumbColor = CyanAccent,
                    activeTrackColor = CyanAccent,
                    inactiveTrackColor = BackgroundDark
                )
            )

            // Step Buttons: Minus and Plus
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledIconButton(
                    onClick = { onAdjustVolume(-5) },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_vol_down"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = BackgroundDark.copy(alpha = 0.6f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Sesi Azalt",
                        modifier = Modifier.size(16.dp)
                    )
                }

                FilledIconButton(
                    onClick = { onAdjustVolume(5) },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_vol_up"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CyanAccent.copy(alpha = 0.2f),
                        contentColor = CyanAccent
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Sesi Artır",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
