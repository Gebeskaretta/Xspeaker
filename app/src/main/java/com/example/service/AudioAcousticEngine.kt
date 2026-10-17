package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.example.model.SoundPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class AudioAcousticEngine(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _tvVolume = MutableStateFlow(getTvVolumePercent())
    val tvVolume: StateFlow<Int> = _tvVolume.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _currentPreset = MutableStateFlow(SoundPreset.BASS_PUNCH)
    val currentPreset: StateFlow<SoundPreset> = _currentPreset.asStateFlow()

    private val _isSoundTesting = MutableStateFlow(false)
    val isSoundTesting: StateFlow<Boolean> = _isSoundTesting.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // 32-band visualizer frequencies
    private val _frequencyBands = MutableStateFlow(List(32) { 0.15f })
    val frequencyBands: StateFlow<List<Float>> = _frequencyBands.asStateFlow()

    // Bass pulse level (0f - 1f) for speaker woofer animation
    private val _bassPulse = MutableStateFlow(0f)
    val bassPulse: StateFlow<Float> = _bassPulse.asStateFlow()

    private var visualizerJob: Job? = null
    private var testTrackJob: Job? = null

    init {
        startVisualizerLoop()
    }

    private fun getTvVolumePercent(): Int {
        return try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (max > 0) ((current.toFloat() / max.toFloat()) * 100).toInt() else 70
        } catch (_: Exception) {
            70
        }
    }

    fun setTvVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _tvVolume.value = clamped
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val index = ((clamped / 100f) * max).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
        } catch (_: Exception) {}
    }

    fun adjustVolume(delta: Int) {
        setTvVolume(_tvVolume.value + delta)
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (_isMuted.value) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            } else {
                val index = ((_tvVolume.value / 100f) * max).toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
            }
        } catch (_: Exception) {}
    }

    fun selectPreset(preset: SoundPreset) {
        _currentPreset.value = preset
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
    }

    fun playAcousticSoundCheck() {
        if (_isSoundTesting.value) return
        testTrackJob?.cancel()
        testTrackJob = scope.launch {
            _isSoundTesting.value = true
            try {
                val sampleRate = 44100
                val durationSeconds = 3.5
                val numSamples = (durationSeconds * sampleRate).toInt()
                val buffer = ShortArray(numSamples)

                // Generate rich stereo acoustic chime with deep sub-bass sweep & harmonics
                for (i in 0 until numSamples) {
                    val time = i.toDouble() / sampleRate
                    // Sub bass 60Hz rising to 120Hz
                    val freqSweep = 55.0 + 80.0 * (time / durationSeconds)
                    val bassWave = sin(2.0 * PI * freqSweep * time)

                    // Warm melodic acoustic chime harmonics at 440Hz, 880Hz, 1320Hz with decay
                    val envelope = (1.0 - (time / durationSeconds)).coerceIn(0.0, 1.0)
                    val harmonic1 = sin(2.0 * PI * 440.0 * time) * 0.4
                    val harmonic2 = sin(2.0 * PI * 659.25 * time) * 0.3
                    val harmonic3 = sin(2.0 * PI * 880.0 * time) * 0.2

                    val sampleValue = ((bassWave * 0.5 + (harmonic1 + harmonic2 + harmonic3) * envelope) * 30000.0)
                    buffer[i] = sampleValue.toInt().coerceIn(-32768, 32767).toShort()
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()
                delay((durationSeconds * 1000).toLong())
                audioTrack.stop()
                audioTrack.release()
            } catch (_: Exception) {
                delay(3000)
            } finally {
                _isSoundTesting.value = false
            }
        }
    }

    private fun startVisualizerLoop() {
        visualizerJob?.cancel()
        visualizerJob = scope.launch {
            var step = 0f
            val currentHeights = FloatArray(32) { 0.1f }

            while (isActive) {
                step += 0.15f
                val active = (_isPlaying.value || _isSoundTesting.value) && !_isMuted.value
                val preset = _currentPreset.value

                // Compute bass intensity
                val bassBoostFactor = (preset.bassBoostDb + 10) / 15f
                val baseBass = if (active) {
                    (0.35f + 0.55f * sin(step * 1.8f).coerceAtLeast(0f) * (0.8f + Random.nextFloat() * 0.2f)) * bassBoostFactor
                } else {
                    0.05f
                }
                _bassPulse.value = baseBass.coerceIn(0f, 1f)

                // Compute 32-band spectrum
                for (i in 0 until 32) {
                    val target = if (active) {
                        val freqFactor = if (i < 8) {
                            // Sub-bass & Bass
                            (sin(step * 2f + i * 0.4f) * 0.4f + 0.6f) * (1f + preset.bassBoostDb / 20f)
                        } else if (i < 20) {
                            // Midrange / Vocals
                            (sin(step * 3.2f + i * 0.6f) * 0.35f + 0.5f) * (1f + preset.trebleBoostDb / 25f)
                        } else {
                            // High frequencies / Treble
                            (sin(step * 4.5f + i * 0.8f) * 0.3f + 0.45f) * (1f + preset.trebleBoostDb / 20f)
                        }
                        (freqFactor * (0.7f + Random.nextFloat() * 0.3f)).coerceIn(0.12f, 0.98f)
                    } else {
                        0.05f + (Random.nextFloat() * 0.04f)
                    }

                    // Smooth attack and decay
                    if (target > currentHeights[i]) {
                        currentHeights[i] = currentHeights[i] + (target - currentHeights[i]) * 0.65f // fast attack
                    } else {
                        currentHeights[i] = currentHeights[i] - (currentHeights[i] - target) * 0.22f // smooth decay
                    }
                }

                _frequencyBands.value = currentHeights.toList()
                delay(33) // ~30 fps fluid audio visualizer update
            }
        }
    }
}
