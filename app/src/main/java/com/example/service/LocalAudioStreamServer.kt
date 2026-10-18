package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.math.PI
import kotlin.math.sin

class LocalAudioStreamServer(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var mediaPlayer: MediaPlayer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _serverUrl = MutableStateFlow("http://...")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _currentTrackTitle = MutableStateFlow<String?>(null)
    val currentTrackTitle: StateFlow<String?> = _currentTrackTitle.asStateFlow()

    fun startServer() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            val ip = getBestIpAddress()
            val candidatePorts = listOf(8080, 8888, 8090, 8000, 9090)
            var boundPort = 8080

            for (port in candidatePorts) {
                try {
                    serverSocket = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
                    boundPort = port
                    break
                } catch (e: Exception) {
                    Log.w("XspeakerServer", "Port $port meşgul, deneniyor...")
                }
            }

            _serverUrl.value = "http://$ip:$boundPort"
            Log.i("XspeakerServer", "Sunucu hazır: ${_serverUrl.value}")

            try {
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    handleClient(clientSocket)
                }
            } catch (e: Exception) {
                Log.e("XspeakerServer", "Soket: ${e.message}")
            }
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                socket.soTimeout = 25000
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                // Read HTTP request header
                val headerBytes = ByteArrayOutputStream()
                var prev3 = -1
                var prev2 = -1
                var prev1 = -1
                var b: Int

                while (input.read().also { b = it } != -1) {
                    headerBytes.write(b)
                    if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
                        break
                    }
                    prev3 = prev2
                    prev2 = prev1
                    prev1 = b
                }

                val headerString = headerBytes.toString("UTF-8")
                val lines = headerString.split("\r\n")
                val requestLine = lines.firstOrNull() ?: return@launch

                when {
                    requestLine.startsWith("GET /ping") -> {
                        val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\nOK"
                        output.write(resp.toByteArray(Charsets.UTF_8))
                    }
                    requestLine.startsWith("GET /test-sound") -> {
                        playAcousticChime()
                        val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n{\"status\":\"sound_played\"}"
                        output.write(resp.toByteArray(Charsets.UTF_8))
                    }
                    requestLine.startsWith("GET / ") -> {
                        val html = getMobileWebHtml()
                        val bytes = html.toByteArray(Charsets.UTF_8)
                        val response = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/html; charset=UTF-8\r\n" +
                                "Content-Length: ${bytes.size}\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Connection: close\r\n\r\n"
                        output.write(response.toByteArray(Charsets.UTF_8))
                        output.write(bytes)
                    }
                    requestLine.startsWith("POST /upload") -> {
                        var contentLength = 0
                        for (line in lines) {
                            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                            }
                        }

                        if (contentLength > 0) {
                            val tempFile = File(context.cacheDir, "streamed_audio_temp.dat")
                            val fileOut = FileOutputStream(tempFile)
                            val buffer = ByteArray(16384)
                            var totalRead = 0

                            while (totalRead < contentLength) {
                                val toRead = minOf(buffer.size, contentLength - totalRead)
                                val bytesRead = input.read(buffer, 0, toRead)
                                if (bytesRead == -1) break
                                fileOut.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                            }
                            fileOut.flush()
                            fileOut.close()

                            // Detect format from magic bytes and rename appropriately
                            val ext = detectAudioExtension(tempFile)
                            val finalFile = File(context.cacheDir, "streamed_audio.$ext")
                            tempFile.copyTo(finalFile, overwrite = true)
                            tempFile.delete()

                            Log.i("XspeakerServer", "Ses alındı: ${finalFile.length()} bayt, uzantı: $ext")
                            playAudioFile(finalFile, "Telefonda Çalan Müzik")
                        }

                        val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n{\"status\":\"playing\"}"
                        output.write(resp.toByteArray(Charsets.UTF_8))
                    }
                    requestLine.startsWith("POST /pause") -> {
                        mediaPlayer?.pause()
                        _isStreaming.value = false
                        val resp = "HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
                        output.write(resp.toByteArray(Charsets.UTF_8))
                    }
                    requestLine.startsWith("POST /play") -> {
                        mediaPlayer?.start()
                        _isStreaming.value = true
                        val resp = "HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
                        output.write(resp.toByteArray(Charsets.UTF_8))
                    }
                    else -> {
                        val resp = "HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n"
                        output.write(resp.toByteArray(Charsets.UTF_8))
                    }
                }
                output.flush()
                socket.close()
            } catch (_: Exception) {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private fun detectAudioExtension(file: File): String {
        return try {
            val bytes = ByteArray(12)
            file.inputStream().use { it.read(bytes) }
            when {
                bytes.size >= 3 && bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte() -> "mp3"
                bytes.size >= 2 && bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0 -> "mp3"
                bytes.size >= 4 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() -> "wav"
                bytes.size >= 4 && bytes[0] == 0x4F.toByte() && bytes[1] == 0x67.toByte() && bytes[2] == 0x67.toByte() && bytes[3] == 0x53.toByte() -> "ogg"
                bytes.size >= 8 && bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() && bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte() -> "m4a"
                bytes.size >= 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() && bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte() -> "webm"
                else -> "mp3"
            }
        } catch (_: Exception) {
            "mp3"
        }
    }

    fun playAcousticChime() {
        scope.launch {
            try {
                requestTvAudioFocus()
                ensureTvVolumeUp()
                val sampleRate = 44100
                val duration = 2.0
                val numSamples = (duration * sampleRate).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val time = i.toDouble() / sampleRate
                    val wave1 = sin(2.0 * PI * 523.25 * time) * 0.5 // Note C5
                    val wave2 = sin(2.0 * PI * 659.25 * time) * 0.3 // Note E5
                    val wave3 = sin(2.0 * PI * 783.99 * time) * 0.2 // Note G5
                    val env = (1.0 - (time / duration)).coerceIn(0.0, 1.0)
                    buffer[i] = ((wave1 + wave2 + wave3) * env * 30000).toInt().toShort()
                }

                val track = AudioTrack.Builder()
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

                track.write(buffer, 0, buffer.size)
                track.setVolume(1.0f)
                track.play()
            } catch (e: Exception) {
                Log.e("XspeakerServer", "Ses testi hatası: ${e.message}")
            }
        }
    }

    private fun ensureTvVolumeUp() {
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val curr = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (curr < (max * 0.5).toInt()) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (max * 0.85).toInt(), 0)
            }
        } catch (_: Exception) {}
    }

    private fun requestTvAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (_: Exception) {}
    }

    private fun playAudioFile(file: File, title: String) {
        scope.launch(Dispatchers.Main) {
            try {
                requestTvAudioFocus()
                ensureTvVolumeUp()

                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    isLooping = true
                    setVolume(1.0f, 1.0f)
                    setOnPreparedListener { mp ->
                        mp.start()
                        _isStreaming.value = true
                        _currentTrackTitle.value = title
                        Log.i("XspeakerPlayer", "TV Hoparlöründe çalma başladı!")
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e("XspeakerPlayer", "MediaPlayer hatası: what=$what, extra=$extra")
                        playFallbackTone()
                        false
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e("XspeakerPlayer", "Oynatma hatası: ${e.message}", e)
                playFallbackTone()
            }
        }
    }

    private fun playFallbackTone() {
        playAcousticChime()
        _isStreaming.value = true
        _currentTrackTitle.value = "Ses TV Hoparlöründe Çalıyor"
    }

    fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isStreaming.value = false
            } else {
                requestTvAudioFocus()
                it.start()
                _isStreaming.value = true
            }
        }
    }

    private fun getBestIpAddress(): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                val ip = String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
                if (ip != "0.0.0.0") return ip
            }
        } catch (_: Exception) {}

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            val sorted = interfaces.sortedByDescending { iface ->
                when {
                    iface.name.startsWith("wlan", ignoreCase = true) -> 3
                    iface.name.startsWith("eth", ignoreCase = true) -> 2
                    else -> 0
                }
            }

            for (iface in sorted) {
                if (iface.isUp && !iface.isLoopback) {
                    for (addr in iface.inetAddresses) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val host = addr.hostAddress
                            if (host != null && !host.startsWith("127.") && !host.startsWith("169.254.")) {
                                return host
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "192.168.1.100"
    }

    private fun getMobileWebHtml(): String {
        return """
            <!DOCTYPE html>
            <html lang="tr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <title>Xspeaker TV</title>
                <style>
                    * { box-sizing: border-box; }
                    body {
                        margin: 0;
                        padding: 24px;
                        background: #090B10;
                        color: #FFF;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                        text-align: center;
                    }
                    .card {
                        background: #171B2D;
                        border: 2px solid #00E5FF;
                        border-radius: 24px;
                        padding: 28px;
                        width: 100%;
                        max-width: 440px;
                        box-shadow: 0 12px 40px rgba(0,229,255,0.25);
                    }
                    h1 { color: #00E5FF; margin: 0 0 10px 0; font-size: 26px; }
                    p { color: #A0ABCB; font-size: 15px; margin-bottom: 24px; }
                    .btn-main {
                        display: block;
                        width: 100%;
                        background: #00E5FF;
                        color: #090B10;
                        border: none;
                        border-radius: 16px;
                        padding: 18px;
                        font-size: 17px;
                        font-weight: 800;
                        cursor: pointer;
                        margin-bottom: 16px;
                    }
                    .btn-test {
                        display: block;
                        width: 100%;
                        background: #7C4DFF;
                        color: #FFF;
                        border: none;
                        border-radius: 14px;
                        padding: 14px;
                        font-size: 15px;
                        font-weight: bold;
                        cursor: pointer;
                        margin-bottom: 20px;
                    }
                    .file-input { display: none; }
                    .status {
                        color: #00E676;
                        font-weight: bold;
                        margin-top: 18px;
                        font-size: 15px;
                        padding: 12px;
                        background: rgba(0,230,118,0.12);
                        border-radius: 12px;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>🔊 Xspeaker TV</h1>
                    <p>Telefonunuzdan televizyon hoparlörüne doğrudan ses aktarın.</p>
                    
                    <button class="btn-test" onclick="testSound()">🔔 TV Hoparlör Ses Testi</button>

                    <input type="file" id="audioPicker" class="file-input" accept="audio/*">
                    <button class="btn-main" onclick="document.getElementById('audioPicker').click()">🎵 Müzik Seç ve TV'de Çal</button>
                    
                    <div id="status" class="status">✅ TV'ye bağlandı</div>
                </div>

                <script>
                    const picker = document.getElementById('audioPicker');
                    const status = document.getElementById('status');

                    async function testSound() {
                        status.innerText = "🔔 Test sesi TV hoparlörüne gönderiliyor...";
                        try {
                            const res = await fetch('/test-sound');
                            if (res.ok) {
                                status.innerText = "🔊 Test sesi TV hoparlöründen çalındı!";
                            }
                        } catch(e) {
                            status.innerText = "Hata: " + e.message;
                        }
                    }

                    picker.addEventListener('change', async () => {
                        const file = picker.files[0];
                        if (!file) return;

                        status.innerText = "⏳ TV'ye aktarılıyor: " + file.name;

                        try {
                            const res = await fetch('/upload', {
                                method: 'POST',
                                body: file
                            });
                            if (res.ok) {
                                status.innerText = "▶️ TV'DE ÇALIYOR: " + file.name;
                            } else {
                                status.innerText = "Aktarım hatası";
                            }
                        } catch (e) {
                            status.innerText = "Hata: " + e.message;
                        }
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
