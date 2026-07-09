package com.aistudio.smartshare.abcd.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.smartshare.abcd.data.HistoryManager
import com.aistudio.smartshare.abcd.data.SelectedFile
import com.aistudio.smartshare.abcd.data.TransferHistoryItem
import com.aistudio.smartshare.abcd.data.TransferManager
import com.aistudio.smartshare.abcd.data.TransferStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val historyManager = HistoryManager(application)
    private val transferManager = TransferManager(application, historyManager)

    val deviceCode = transferManager.getDeviceCode()
    val deviceName = transferManager.getDeviceName()

    private val _history = MutableStateFlow<List<TransferHistoryItem>>(emptyList())
    val history: StateFlow<List<TransferHistoryItem>> = _history.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFile>> = _selectedFiles.asStateFlow()

    private val _showHistory = MutableStateFlow(false)
    val showHistory: StateFlow<Boolean> = _showHistory.asStateFlow()

    val transferStatus = transferManager.status
    val progress = transferManager.progress
    val speedMB = transferManager.speedMB
    val etaSeconds = transferManager.etaSeconds
    val activeFile = transferManager.activeFile
    val incomingRequest = transferManager.incomingRequest

    init {
        loadHistory()
    }

    fun loadHistory() {
        _history.value = historyManager.getHistory()
    }

    fun toggleHistory() {
        _showHistory.value = !_showHistory.value
        if (_showHistory.value) {
            loadHistory()
        }
    }

    fun clearHistory() {
        historyManager.clearHistory()
        loadHistory()
    }

    fun addSelectedFile(uriStr: String) {
        val uri = Uri.parse(uriStr)
        val (name, size) = getUriFileInfo(uri)
        val current = _selectedFiles.value.toMutableList()
        current.add(SelectedFile(uriStr, name, size))
        _selectedFiles.value = current
    }

    fun removeSelectedFile(file: SelectedFile) {
        val current = _selectedFiles.value.toMutableList()
        current.remove(file)
        _selectedFiles.value = current
    }
    
    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }

    fun setEncryptionPassword(password: String) {
        transferManager.encryptionPassword = password
    }

    fun getEncryptionPassword(): String {
        return transferManager.encryptionPassword
    }

    fun startTransfer(code: String) {
        if (code.length >= 5 && _selectedFiles.value.isNotEmpty()) {
            viewModelScope.launch {
                transferManager.startRealSender(code, _selectedFiles.value)
                loadHistory()
            }
        }
    }

    fun cancelTransfer() {
        transferManager.cancelTransfer()
    }
    
    fun resetTransfer() {
        transferManager.reset()
    }
    
    fun getCategoryFolder(filename: String): String {
        return historyManager.getCategoryFolder(filename)
    }

    private fun getUriFileInfo(uri: Uri): Pair<String, Long> {
        var name = "unknown"
        var size = 0L
        try {
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) name = cursor.getString(nameIndex)
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (name == "unknown") {
            name = uri.lastPathSegment ?: "unknown"
        }
        return Pair(name, size)
    }

    override fun onCleared() {
        super.onCleared()
        transferManager.onDestroy()
    }
}
