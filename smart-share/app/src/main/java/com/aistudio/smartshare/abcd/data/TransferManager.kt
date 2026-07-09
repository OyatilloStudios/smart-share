package com.aistudio.smartshare.abcd.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

enum class TransferStatus {
    IDLE, CONNECTING, TRANSFERRING, COMPLETED, FAILED, CANCELLED
}

data class SelectedFile(
    val uri: String,
    val name: String,
    val size: Long
)

data class IncomingRequest(
    val filename: String,
    val size: Long,
    val peerIp: String,
    val onResponse: (Boolean) -> Unit
)

object CryptoHelper {
    private val SALT = "OsonShareFixedSalt123".toByteArray(Charsets.UTF_8)
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256

    private fun deriveKey(password: String): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), SALT, ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    fun getEncryptCipher(password: String, iv: ByteArray): Cipher {
        val key = deriveKey(password)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        return cipher
    }

    fun getDecryptCipher(password: String, iv: ByteArray): Cipher {
        val key = deriveKey(password)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        return cipher
    }
}

class TransferManager(private val context: Context, private val historyManager: HistoryManager) {

    private val _status = MutableStateFlow(TransferStatus.IDLE)
    val status: StateFlow<TransferStatus> = _status.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _speedMB = MutableStateFlow(0f)
    val speedMB: StateFlow<Float> = _speedMB.asStateFlow()

    private val _etaSeconds = MutableStateFlow(0)
    val etaSeconds: StateFlow<Int> = _etaSeconds.asStateFlow()

    private val _activeFile = MutableStateFlow("")
    val activeFile: StateFlow<String> = _activeFile.asStateFlow()

    private val _incomingRequest = MutableStateFlow<IncomingRequest?>(null)
    val incomingRequest: StateFlow<IncomingRequest?> = _incomingRequest.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var serverSocket: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null
    private var isCancelled = false
    private val signalingUrl = "http://oson-share-signaling.glitch.me"

    // Optional encryption password state
    var encryptionPassword = ""

    init {
        startServer()
        registerOnSignalingBackground()
    }

    private fun startServer() {
        scope.launch(Dispatchers.IO) {
            runTcpServer()
        }
        scope.launch(Dispatchers.IO) {
            runUdpServer()
        }
    }

    private fun registerOnSignalingBackground() {
        scope.launch(Dispatchers.IO) {
            val ip = getLocalIpAddress() ?: return@launch
            val code = getDeviceCode()
            try {
                val url = java.net.URL("$signalingUrl/register?code=$code&ip=$ip")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 3000
                conn.requestMethod = "GET"
                conn.responseCode
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun runTcpServer() {
        try {
            serverSocket = ServerSocket(8989).apply { reuseAddress = true }
            while (true) {
                val socket = serverSocket?.accept() ?: break
                scope.launch(Dispatchers.IO) {
                    handleClientConnection(socket)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun runUdpServer() {
        var lock: WifiManager.MulticastLock? = null
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            lock = wifiManager.createMulticastLock("SmartShareMulticastLock").apply {
                setReferenceCounted(true)
                acquire()
            }

            udpSocket = DatagramSocket(8990).apply { reuseAddress = true }
            val buffer = ByteArray(2048)

            while (true) {
                val packet = DatagramPacket(buffer, buffer.size)
                udpSocket?.receive(packet)

                val messageStr = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                try {
                    val json = JSONObject(messageStr)
                    if (json.optString("type") == "query" && json.optString("code") == getDeviceCode()) {
                        val replyJson = JSONObject().apply {
                            put("type", "reply")
                            put("code", getDeviceCode())
                            put("name", getDeviceName())
                        }
                        val replyBytes = replyJson.toString().toByteArray(Charsets.UTF_8)
                        val replyPacket = DatagramPacket(replyBytes, replyBytes.size, packet.address, packet.port)
                        udpSocket?.send(replyPacket)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { lock?.release() } catch (e: Exception) {}
            try { udpSocket?.close() } catch (e: Exception) {}
        }
    }

    private suspend fun handleClientConnection(socket: Socket) {
        val peerIp = socket.inetAddress.hostAddress ?: "unknown"
        var tempFile: java.io.File? = null
        try {
            socket.soTimeout = 30000
            val inputStream = socket.getInputStream()
            val outputStream = socket.getOutputStream()

            val buffer = ByteArray(1024)
            val readBytes = inputStream.read(buffer)
            if (readBytes <= 0) return

            val metaStr = String(buffer, 0, readBytes, Charsets.UTF_8).trim()
            val meta = JSONObject(metaStr)
            if (meta.optString("type") != "meta") return

            val filename = meta.getString("name")
            val totalSize = meta.getLong("size")
            val isEncrypted = meta.optBoolean("encrypted", false)

            _status.value = TransferStatus.CONNECTING
            _activeFile.value = filename

            var allowed = false
            val latch = CountDownLatch(1)
            _incomingRequest.value = IncomingRequest(filename, totalSize, peerIp) { accept ->
                allowed = accept
                latch.countDown()
            }
            latch.await()
            _incomingRequest.value = null

            if (!allowed) {
                val rejectObj = JSONObject().apply { put("type", "reject") }
                outputStream.write(rejectObj.toString().toByteArray(Charsets.UTF_8))
                outputStream.flush()
                _status.value = TransferStatus.CANCELLED
                socket.close()
                return
            }

            val acceptObj = JSONObject().apply { put("type", "accept") }
            outputStream.write(acceptObj.toString().toByteArray(Charsets.UTF_8))
            outputStream.flush()

            _status.value = TransferStatus.TRANSFERRING
            _progress.value = 0f

            tempFile = java.io.File(context.cacheDir, "$filename.part")
            if (tempFile.exists()) tempFile.delete()

            var cipher: Cipher? = null
            if (isEncrypted) {
                val iv = ByteArray(16)
                var ivRead = 0
                while (ivRead < 16) {
                    val r = inputStream.read(iv, ivRead, 16 - ivRead)
                    if (r == -1) throw Exception("Connection closed while reading IV")
                    ivRead += r
                }
                val pwd = encryptionPassword.ifEmpty { getDeviceCode() }
                cipher = CryptoHelper.getDecryptCipher(pwd, iv)
            }

            var received = 0L
            val fileOut = java.io.FileOutputStream(tempFile)
            val chunkBuffer = ByteArray(65536)
            var lastCheckTime = System.currentTimeMillis()
            var lastCheckBytes = 0L

            fileOut.use { out ->
                while (received < totalSize) {
                    val bytesRead = inputStream.read(chunkBuffer)
                    if (bytesRead == -1) break
                    
                    if (cipher != null) {
                        val decrypted = cipher.update(chunkBuffer, 0, bytesRead)
                        if (decrypted != null) {
                            out.write(decrypted)
                        }
                    } else {
                        out.write(chunkBuffer, 0, bytesRead)
                    }
                    received += bytesRead

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastCheckTime
                    if (elapsed >= 1000 || received == totalSize) {
                        val bytesDiff = received - lastCheckBytes
                        val speed = (bytesDiff / (1024.0 * 1024.0)) / (elapsed / 1000.0)
                        val eta = if (speed > 0) ((totalSize - received) / (speed * 1024 * 1024)).toInt() else 9999

                        _progress.value = received.toFloat() / (if (totalSize > 0) totalSize.toFloat() else 1.0f)
                        _speedMB.value = speed.toFloat()
                        _etaSeconds.value = eta

                        lastCheckTime = now
                        lastCheckBytes = received
                    }
                }
                if (cipher != null) {
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null) {
                        out.write(finalBytes)
                    }
                }
            }

            if (received >= totalSize - 64) {
                saveReceivedFile(tempFile, filename, tempFile.length())
                _status.value = TransferStatus.COMPLETED
            } else {
                tempFile.delete()
                _status.value = TransferStatus.FAILED
            }

        } catch (e: Exception) {
            e.printStackTrace()
            tempFile?.delete()
            _status.value = TransferStatus.FAILED
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun saveReceivedFile(tempFile: java.io.File, filename: String, size: Long) {
        try {
            val resolver = context.contentResolver
            val category = historyManager.getCategoryFolder(filename)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(filename))
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OsonShare/$category")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        java.io.FileInputStream(tempFile).use { input ->
                            input.copyTo(out)
                        }
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destDir = java.io.File(downloadsDir, "OsonShare/$category")
                if (!destDir.exists()) destDir.mkdirs()
                
                var targetFile = java.io.File(destDir, filename)
                var counter = 1
                val nameWithoutExt = filename.substringBeforeLast('.')
                val ext = filename.substringAfterLast('.', "")
                while (targetFile.exists()) {
                    val newName = if (ext.isNotEmpty()) "$nameWithoutExt($counter).$ext" else "$nameWithoutExt($counter)"
                    targetFile = java.io.File(destDir, newName)
                    counter++
                }
                tempFile.renameTo(targetFile)
            }

            historyManager.addHistoryItem(
                TransferHistoryItem(
                    filename = filename,
                    category = category,
                    size = size,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            tempFile.delete()
        }
    }

    private fun getMimeType(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            "mp3" -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }

    suspend fun startRealSender(code: String, files: List<SelectedFile>) = withContext(Dispatchers.IO) {
        isCancelled = false
        _status.value = TransferStatus.CONNECTING
        _progress.value = 0f
        _speedMB.value = 0f
        _etaSeconds.value = 0

        var targetIp = resolveIp(code)
        var isRelay = false

        if (targetIp == null) {
            if (code.contains('.') || code.contains(':')) {
                targetIp = code
            } else {
                isRelay = true
            }
        }

        var socket: Socket? = null
        try {
            for (file in files) {
                if (isCancelled) break
                val uri = Uri.parse(file.uri)
                _activeFile.value = file.name

                socket = Socket()
                if (isRelay) {
                    val relayHost = "oson-share-signaling.glitch.me"
                    socket.connect(java.net.InetSocketAddress(relayHost, 8991), 15000)
                } else {
                    socket.connect(java.net.InetSocketAddress(targetIp, 8989), 10000)
                }
                socket.soTimeout = 30000

                val out = socket.getOutputStream()
                val input = socket.getInputStream()

                if (isRelay) {
                    val relayRegister = JSONObject().apply {
                        put("mode", "send")
                        put("code", code)
                    }
                    out.write((relayRegister.toString() + "\n").toByteArray(Charsets.UTF_8))
                    out.flush()
                }

                val useEncryption = true
                val pwd = encryptionPassword.ifEmpty { code }

                val metaJson = JSONObject().apply {
                    put("type", "meta")
                    put("name", file.name)
                    put("size", file.size)
                    put("encrypted", useEncryption)
                }
                out.write(metaJson.toString().toByteArray(Charsets.UTF_8))
                out.flush()

                val responseBuffer = ByteArray(1024)
                val readLen = input.read(responseBuffer)
                if (readLen <= 0) {
                    _status.value = TransferStatus.FAILED
                    socket.close()
                    return@withContext
                }

                val respStr = String(responseBuffer, 0, readLen, Charsets.UTF_8).trim()
                val respJson = JSONObject(respStr)
                if (respJson.optString("type") == "reject") {
                    _status.value = TransferStatus.CANCELLED
                    socket.close()
                    return@withContext
                }

                _status.value = TransferStatus.TRANSFERRING

                val fileIn = context.contentResolver.openInputStream(uri) ?: throw Exception("Cannot open stream")
                val iv = ByteArray(16)
                java.security.SecureRandom().nextBytes(iv)
                
                if (useEncryption) {
                    out.write(iv)
                    out.flush()
                }

                val cipher = if (useEncryption) CryptoHelper.getEncryptCipher(pwd, iv) else null
                val chunkBuffer = ByteArray(65536)
                var sent = 0L
                var lastCheckTime = System.currentTimeMillis()
                var lastCheckBytes = 0L

                fileIn.use { fileStream ->
                    while (sent < file.size && !isCancelled) {
                        val bytesRead = fileStream.read(chunkBuffer)
                        if (bytesRead == -1) break

                        if (cipher != null) {
                            val encrypted = cipher.update(chunkBuffer, 0, bytesRead)
                            if (encrypted != null) {
                                out.write(encrypted)
                            }
                        } else {
                            out.write(chunkBuffer, 0, bytesRead)
                        }
                        sent += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastCheckTime
                        if (elapsed >= 1000 || sent == file.size) {
                            val bytesDiff = sent - lastCheckBytes
                            val speed = (bytesDiff / (1024.0 * 1024.0)) / (elapsed / 1000.0)
                            val eta = if (speed > 0) ((file.size - sent) / (speed * 1024 * 1024)).toInt() else 9999

                            _progress.value = sent.toFloat() / (if (file.size > 0L) file.size.toFloat() else 1.0f)
                            _speedMB.value = speed.toFloat()
                            _etaSeconds.value = eta

                            lastCheckTime = now
                            lastCheckBytes = sent
                        }
                    }
                    if (cipher != null && !isCancelled) {
                        val finalBytes = cipher.doFinal()
                        if (finalBytes != null) {
                            out.write(finalBytes)
                        }
                    }
                }
                out.flush()
                socket.close()
                socket = null

                if (!isCancelled) {
                    historyManager.addHistoryItem(
                        TransferHistoryItem(
                            filename = file.name,
                            category = historyManager.getCategoryFolder(file.name),
                            size = file.size,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }

            if (!isCancelled) {
                _status.value = TransferStatus.COMPLETED
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _status.value = TransferStatus.FAILED
        } finally {
            try { socket?.close() } catch (e: Exception) {}
        }
    }

    private suspend fun resolveIp(code: String): String? = withContext(Dispatchers.IO) {
        try {
            val s = DatagramSocket().apply {
                broadcast = true
                soTimeout = 1200
            }
            val queryObj = JSONObject().apply {
                put("type", "query")
                put("code", code)
            }
            val queryBytes = queryObj.toString().toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(queryBytes, queryBytes.size, InetAddress.getByName("255.255.255.255"), 8990)
            s.send(packet)

            val buffer = ByteArray(1024)
            val replyPacket = DatagramPacket(buffer, buffer.size)
            s.receive(replyPacket)

            val replyStr = String(replyPacket.data, 0, replyPacket.length, Charsets.UTF_8).trim()
            val replyJson = JSONObject(replyStr)
            if (replyJson.optString("type") == "reply" && replyJson.optString("code") == code) {
                s.close()
                return@withContext replyPacket.address.hostAddress
            }
        } catch (e: Exception) {
        }

        try {
            val url = java.net.URL("$signalingUrl/get?code=$code")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val ip = json.optString("ip", "")
                return@withContext if (ip.isNotEmpty()) ip else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext null
    }

    fun cancelTransfer() {
        isCancelled = true
        _status.value = TransferStatus.CANCELLED
    }

    fun reset() {
        isCancelled = false
        _status.value = TransferStatus.IDLE
        _progress.value = 0f
        _speedMB.value = 0f
        _etaSeconds.value = 0
        _activeFile.value = ""
    }

    fun getDeviceCode(): String {
        val model = Build.MODEL ?: "Device"
        val hash = abs(model.hashCode())
        return ((hash % 90000) + 10000).toString()
    }

    fun getDeviceName(): String {
        return Build.MODEL ?: "Android Device"
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    fun onDestroy() {
        scope.cancel()
        try { serverSocket?.close() } catch (e: Exception) {}
        try { udpSocket?.close() } catch (e: Exception) {}
    }
}
