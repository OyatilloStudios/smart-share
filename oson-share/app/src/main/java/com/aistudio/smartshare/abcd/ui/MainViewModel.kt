package com.aistudio.smartshare.abcd.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.smartshare.abcd.data.HistoryManager
import com.aistudio.smartshare.abcd.data.TransferHistoryItem
import com.aistudio.smartshare.abcd.data.TransferManager
import com.aistudio.smartshare.abcd.data.TransferStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val historyManager = HistoryManager(application)
    private val transferManager = TransferManager(historyManager)

    val deviceCode = transferManager.getDeviceCode()
    val deviceName = transferManager.getDeviceName()

    private val _history = MutableStateFlow<List<TransferHistoryItem>>(emptyList())
    val history: StateFlow<List<TransferHistoryItem>> = _history.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<String>>(emptyList())
    val selectedFiles: StateFlow<List<String>> = _selectedFiles.asStateFlow()

    private val _showHistory = MutableStateFlow(false)
    val showHistory: StateFlow<Boolean> = _showHistory.asStateFlow()

    val transferStatus = transferManager.status
    val progress = transferManager.progress
    val speedMB = transferManager.speedMB
    val etaSeconds = transferManager.etaSeconds
    val activeFile = transferManager.activeFile

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

    fun addSelectedFile(filePath: String) {
        val current = _selectedFiles.value.toMutableList()
        current.add(filePath)
        _selectedFiles.value = current
    }

    fun removeSelectedFile(filePath: String) {
        val current = _selectedFiles.value.toMutableList()
        current.remove(filePath)
        _selectedFiles.value = current
    }
    
    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }

    fun startTransfer(code: String) {
        if (code.length == 5 && _selectedFiles.value.isNotEmpty()) {
            viewModelScope.launch {
                transferManager.startMockSender(code, _selectedFiles.value)
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
}
