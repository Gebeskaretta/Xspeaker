package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.wifi.WifiManager
import android.text.format.Formatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

class LocalAudioStreamServer(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var mediaPlayer: MediaPlayer? = null

    private val _serverUrl = MutableStateFlow("http://...")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _currentTrackTitle = MutableStateFlow<String?>(null)
    val currentTrackTitle: StateFlow<String?> = _currentTrackTitle.asStateFlow()

    fun startServer(port: Int = 8080) {
        if (isRunning) return
        isRunning = true

        val ip = getIpAddress()
        _serverUrl.value = "http://$ip:$port"

        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    handleClient(clientSocket)
                }
            } catch (_: Exception) {
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
                val input = socket.getInputStream()
                val reader = BufferedReader(InputStreamReader(input))
                val requestLine = reader.readLine() ?: return@launch

                if (requestLine.startsWith("GET / ")) {
                    // Send Mobile Web UI for audio streaming
                    val html = getMobileWebHtml()
                    val response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=UTF-8\r\n" +
                            "Content-Length: ${html.toByteArray(Charsets.UTF_8).size}\r\n" +
                            "Connection: close\r\n\r\n" + html
                    socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                } else if (requestLine.startsWith("POST /upload")) {
                    // Receive uploaded audio file from phone and play on TV
                    var contentLength = 0
                    var line: String?
                    while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                        if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = line!!.substring(15).trim().toIntOrNull() ?: 0
                        }
                    }

                    if (contentLength > 0) {
                        val tempFile = File(context.cacheDir, "streamed_audio.mp3")
                        val fileOut = FileOutputStream(tempFile)
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0

                        while (totalRead < contentLength) {
                            val toRead = minOf(buffer.size, contentLength - totalRead)
                            bytesRead = input.read(buffer, 0, toRead)
                            if (bytesRead == -1) break
                            fileOut.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                        }
                        fileOut.flush()
                        fileOut.close()

                        // Play audio on TV
                        playAudioFile(tempFile, "Telefonda Çalan Müzik")
                    }

                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"status\":\"ok\"}"
                    socket.getOutputStream().write(resp.toByteArray(Charsets.UTF_8))
                } else if (requestLine.startsWith("POST /pause")) {
                    mediaPlayer?.pause()
                    _isStreaming.value = false
                    val resp = "HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().write(resp.toByteArray(Charsets.UTF_8))
                } else if (requestLine.startsWith("POST /play")) {
                    mediaPlayer?.start()
                    _isStreaming.value = true
                    val resp = "HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().write(resp.toByteArray(Charsets.UTF_8))
                } else {
                    val resp = "HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().write(resp.toByteArray(Charsets.UTF_8))
                }
                socket.close()
            } catch (_: Exception) {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private fun playAudioFile(file: File, title: String) {
        scope.launch(Dispatchers.Main) {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        _isStreaming.value = false
                    }
                }
                _isStreaming.value = true
                _currentTrackTitle.value = title
            } catch (e: Exception) {
                _isStreaming.value = false
            }
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isStreaming.value = false
            } else {
                it.start()
                _isStreaming.value = true
            }
        }
    }

    private fun getIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "192.168.1.100"
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
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Xspeaker • TV Ses Aktarımı</title>
                <style>
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
                        min-height: 90vh;
                        text-align: center;
                    }
                    .card {
                        background: #171B2B;
                        border: 1px solid #00E5FF;
                        border-radius: 20px;
                        padding: 24px;
                        width: 90%;
                        max-width: 400px;
                        box-shadow: 0 10px 30px rgba(0,229,255,0.15);
                    }
                    h1 { color: #00E5FF; margin: 0 0 8px 0; font-size: 24px; }
                    p { color: #A0ABCB; font-size: 14px; margin-bottom: 24px; }
                    .btn {
                        display: block;
                        width: 100%;
                        background: #00E5FF;
                        color: #090B10;
                        border: none;
                        border-radius: 14px;
                        padding: 16px;
                        font-size: 16px;
                        font-weight: bold;
                        cursor: pointer;
                        margin-bottom: 16px;
                        box-sizing: border-box;
                    }
                    .file-input { display: none; }
                    .status { color: #00E676; font-weight: bold; margin-top: 16px; font-size: 14px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>🔊 Xspeaker TV</h1>
                    <p>Telefonunuzdaki müziği veya sesi seçin, doğrudan televizyonun ses sisteminden çalsın!</p>
                    
                    <input type="file" id="audioPicker" class="file-input" accept="audio/*">
                    <button class="btn" onclick="document.getElementById('audioPicker').click()">🎵 Telefonda Müzik / Ses Seç ve TV'ye Aktar</button>
                    
                    <div id="status" class="status">TV'ye bağlı • Çalmaya hazır</div>
                </div>

                <script>
                    const picker = document.getElementById('audioPicker');
                    const status = document.getElementById('status');

                    picker.addEventListener('change', async () => {
                        const file = picker.files[0];
                        if (!file) return;

                        status.innerText = "Televizyona yükleniyor ve çalınıyor...";
                        status.style.color = "#FFAB00";

                        try {
                            const res = await fetch('/upload', {
                                method: 'POST',
                                body: file
                            });
                            if (res.ok) {
                                status.innerText = "Şu anda TV Hoparlöründe çalıyor: " + file.name;
                                status.style.color = "#00E676";
                            } else {
                                status.innerText = "Aktarım hatası!";
                                status.style.color = "#FF1744";
                            }
                        } catch (e) {
                            status.innerText = "Bağlantı hatası: " + e.message;
                            status.style.color = "#FF1744";
                        }
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
