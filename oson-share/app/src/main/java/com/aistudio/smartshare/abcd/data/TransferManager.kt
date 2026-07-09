package com.aistudio.smartshare.abcd.data

import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

enum class TransferStatus {
    IDLE, CONNECTING, TRANSFERRING, COMPLETED, FAILED, CANCELLED
}

class TransferManager(private val historyManager: HistoryManager) {
    
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

    private var isCancelled = false

    suspend fun startMockSender(code: String, files: List<String>) {
        isCancelled = false
        _status.value = TransferStatus.CONNECTING
        delay(1500)
        
        if (isCancelled) return
        
        _status.value = TransferStatus.TRANSFERRING
        for (filePath in files) {
            if (isCancelled) break
            
            val filename = filePath.substringAfterLast("/")
            _activeFile.value = filename
            _progress.value = 0f
            _speedMB.value = (2..15).random().toFloat() + Math.random().toFloat()
            
            val fileSizeMB = (10..100).random().toFloat()
            val totalBytes = (fileSizeMB * 1024 * 1024).toLong()
            
            var currentBytes = 0L
            while (currentBytes < totalBytes && !isCancelled) {
                delay(200)
                val chunk = (1024 * 1024 * (_speedMB.value / 5)).toLong()
                currentBytes += chunk
                if (currentBytes > totalBytes) currentBytes = totalBytes
                
                _progress.value = currentBytes.toFloat() / totalBytes.toFloat()
                val remainingBytes = totalBytes - currentBytes
                if (_speedMB.value > 0) {
                    _etaSeconds.value = (remainingBytes / (_speedMB.value * 1024 * 1024)).toInt()
                }
            }
            
            if (!isCancelled) {
                historyManager.addHistoryItem(
                    TransferHistoryItem(
                        filename = filename,
                        category = historyManager.getCategoryFolder(filename),
                        size = totalBytes,
                        timestamp = System.currentTimeMillis()
                    )
                )
                delay(500)
            }
        }
        
        if (!isCancelled) {
            _status.value = TransferStatus.COMPLETED
        }
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
}
