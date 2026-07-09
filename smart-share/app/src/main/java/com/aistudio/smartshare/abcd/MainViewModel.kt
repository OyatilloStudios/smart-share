package com.aistudio.smartshare.abcd

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.smartshare.abcd.data.HistoryManager
import com.aistudio.smartshare.abcd.data.SelectedFile
import com.aistudio.smartshare.abcd.data.TransferManager
import com.aistudio.smartshare.abcd.data.TransferStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    val historyManager = HistoryManager(application)
    val transferManager = TransferManager(application, historyManager)

    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFile>> = _selectedFiles.asStateFlow()

    private val _targetCode = MutableStateFlow("")
    val targetCode: StateFlow<String> = _targetCode.asStateFlow()

    override fun onCleared() {
        super.onCleared()
        transferManager.onDestroy()
    }

    fun setTargetCode(code: String) {
        _targetCode.value = code
    }

    fun addFiles(uris: List<Uri>) {
        val context = getApplication<Application>()
        val newFiles = uris.mapNotNull { uri ->
            var name = "unknown"
            var size = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) name = cursor.getString(nameIndex)
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
            if (size > 0) {
                SelectedFile(uri.toString(), name, size)
            } else {
                null
            }
        }
        val current = _selectedFiles.value.toMutableList()
        current.addAll(newFiles)
        _selectedFiles.value = current
    }

    fun removeFile(file: SelectedFile) {
        val current = _selectedFiles.value.toMutableList()
        current.remove(file)
        _selectedFiles.value = current
    }

    fun clearFiles() {
        _selectedFiles.value = emptyList()
    }

    fun startSending() {
        val code = _targetCode.value
        val files = _selectedFiles.value
        if (code.isNotEmpty() && files.isNotEmpty()) {
            viewModelScope.launch {
                transferManager.startRealSender(code, files)
            }
        }
    }

    fun acceptIncoming(accept: Boolean) {
        val request = transferManager.incomingRequest.value
        request?.onResponse?.invoke(accept)
    }

    fun cancelTransfer() {
        transferManager.cancelTransfer()
    }

    fun resetTransfer() {
        transferManager.reset()
        _targetCode.value = ""
        clearFiles()
    }
}
