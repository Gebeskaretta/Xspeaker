package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VisualizerMode
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CoralAccent
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.SurfaceCard

@Composable
fun AudioVisualizerView(
    frequencyBands: List<Float>,
    bassPulse: Float,
    selectedMode: VisualizerMode,
    onSelectMode: (VisualizerMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
        color = SurfaceCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with visualizer title & mode selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.padding(start = 6.dp))
                    Text(
                        text = "CANLI FREKANS SPEKTRUMU (32-BANT)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Modes toggle pills
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VisualizerMode.values().forEach { mode ->
                        val isSelected = mode == selectedMode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) CyanAccent else BackgroundDark.copy(alpha = 0.6f)
                                )
                                .clickable { onSelectMode(mode) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("btn_viz_${mode.name}")
                        ) {
                            Text(
                                text = mode.label,
                                color = if (isSelected) BackgroundDark else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas for rendering audio spectrum
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BackgroundDark.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val barCount = frequencyBands.size.coerceAtLeast(1)
                    val spacing = 3.dp.toPx()
                    val totalSpacing = spacing * (barCount - 1)
                    val barWidth = ((width - totalSpacing) / barCount).coerceAtLeast(2f)

                    when (selectedMode) {
                        VisualizerMode.SPECTRUM_BARS -> {
                            val barBrush = Brush.verticalGradient(
                                colors = listOf(CoralAccent, PurpleAccent, CyanAccent),
                                startY = 0f,
                                endY = height
                            )

                            for (i in 0 until barCount) {
                                val normalizedHeight = frequencyBands.getOrElse(i) { 0.1f }.coerceIn(0.05f, 1f)
                                val barHeight = height * normalizedHeight
                                val left = i * (barWidth + spacing)
                                val top = height - barHeight

                                // Bar with rounded top
                                drawRoundRect(
                                    brush = barBrush,
                                    topLeft = Offset(left, top),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                                )

                                // Floating peak dot
                                drawCircle(
                                    color = Color.White,
                                    radius = (barWidth / 2.5f).coerceAtMost(3.dp.toPx()),
                                    center = Offset(left + barWidth / 2f, (top - 3.dp.toPx()).coerceAtLeast(2.dp.toPx()))
                                )
                            }
                        }

                        VisualizerMode.RADIAL_WAVE -> {
                            // Centered acoustic waveform
                            val midY = height / 2f
                            for (i in 0 until barCount) {
                                val normalizedHeight = frequencyBands.getOrElse(i) { 0.1f }.coerceIn(0.05f, 1f)
                                val halfBarHeight = (height * 0.45f) * normalizedHeight
                                val left = i * (barWidth + spacing)

                                val waveColor = if (i < 10) CyanAccent else if (i < 22) PurpleAccent else CoralAccent

                                drawRoundRect(
                                    color = waveColor,
                                    topLeft = Offset(left, midY - halfBarHeight),
                                    size = Size(barWidth, halfBarHeight * 2f),
                                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                                )
                            }
                        }

                        VisualizerMode.CYBER_GLOW -> {
                            // Pulsing gradient horizon
                            val midY = height / 2f
                            val pulseRadius = (height * 0.4f) * (0.5f + bassPulse * 0.5f)

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(CyanAccent.copy(alpha = 0.5f), PurpleAccent.copy(alpha = 0.2f), Color.Transparent),
                                    center = Offset(width / 2f, midY),
                                    radius = pulseRadius
                                ),
                                center = Offset(width / 2f, midY),
                                radius = pulseRadius
                            )

                            // Overlay waveform line
                            for (i in 0 until barCount - 1) {
                                val left1 = i * (barWidth + spacing) + barWidth / 2f
                                val left2 = (i + 1) * (barWidth + spacing) + barWidth / 2f
                                val y1 = height - (height * frequencyBands.getOrElse(i) { 0.1f })
                                val y2 = height - (height * frequencyBands.getOrElse(i + 1) { 0.1f })

                                drawLine(
                                    color = CyanAccent,
                                    start = Offset(left1, y1),
                                    end = Offset(left2, y2),
                                    strokeWidth = 2.5.dp.toPx()
                                )
                            }
                        }
                    }

                    // Baseline subtle glow
                    drawLine(
                        color = CyanAccent.copy(alpha = 0.4f),
                        start = Offset(0f, height - 1.dp.toPx()),
                        end = Offset(width, height - 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }
    }
}
