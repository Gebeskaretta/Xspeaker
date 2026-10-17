package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector

enum class ConnectionStatus {
    DISCONNECTED,
    DISCOVERABLE,
    CONNECTING,
    CONNECTED
}

data class ConnectedDeviceInfo(
    val name: String,
    val address: String,
    val deviceType: String = "Akıllı Telefon",
    val codec: String = "SBC / AAC HD (48 kHz / 24-bit)",
    val signalStrengthDbm: Int = -52,
    val connectedAtMillis: Long = System.currentTimeMillis()
)

enum class SoundPreset(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val bassBoostDb: Int,
    val trebleBoostDb: Int,
    val surroundEnabled: Boolean
) {
    BASS_PUNCH(
        title = "X-Bass Punch",
        subtitle = "Derin bass ve subwoofer akustik rezonansı",
        icon = Icons.Default.GraphicEq,
        bassBoostDb = 12,
        trebleBoostDb = 2,
        surroundEnabled = false
    ),
    CINEMA_SURROUND(
        title = "Sinematik 3D",
        subtitle = "Genişletilmiş oda dolusu TV ses sahnesi",
        icon = Icons.Default.Movie,
        bassBoostDb = 6,
        trebleBoostDb = 6,
        surroundEnabled = true
    ),
    VOCAL_CLARITY(
        title = "Net Vokal & Söz",
        subtitle = "Öne çıkan kristal netliğinde vokal ve diyalog",
        icon = Icons.Default.RecordVoiceOver,
        bassBoostDb = 0,
        trebleBoostDb = 8,
        surroundEnabled = false
    ),
    NIGHT_QUIET(
        title = "Gece Modu",
        subtitle = "Dengeli ve başkalarını rahatsız etmeyen dinamik",
        icon = Icons.Default.NightsStay,
        bassBoostDb = -4,
        trebleBoostDb = -2,
        surroundEnabled = false
    ),
    STUDIO_DIRECT(
        title = "Doğal Stüdyo Hi-Fi",
        subtitle = "Kaynaktan saf ve dengeli frekans iletimi",
        icon = Icons.Default.Equalizer,
        bassBoostDb = 0,
        trebleBoostDb = 0,
        surroundEnabled = false
    )
}

enum class VisualizerMode(val label: String) {
    SPECTRUM_BARS("Neon Spektrum"),
    RADIAL_WAVE("Akustik Dalga"),
    CYBER_GLOW("Nabız Işıltısı")
}
