package com.aistudio.smartshare.abcd.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TransferHistoryItem(
    val filename: String,
    val category: String,
    val size: Long,
    val timestamp: Long
)

class HistoryManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("smart_share_history", Context.MODE_PRIVATE)
    
    private val _history = MutableStateFlow<List<TransferHistoryItem>>(emptyList())
    val history: StateFlow<List<TransferHistoryItem>> = _history.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        val historyJson = prefs.getString("history_items", "[]") ?: "[]"
        try {
            val items = Json.decodeFromString<List<TransferHistoryItem>>(historyJson)
            _history.value = items
        } catch (e: Exception) {
            _history.value = emptyList()
        }
    }

    fun addHistoryItem(item: TransferHistoryItem) {
        val currentList = _history.value.toMutableList()
        currentList.add(0, item) // Add to top
        _history.value = currentList
        saveHistory(currentList)
    }

    fun clearHistory() {
        _history.value = emptyList()
        saveHistory(emptyList())
    }

    private fun saveHistory(list: List<TransferHistoryItem>) {
        val json = Json.encodeToString(list)
        prefs.edit().putString("history_items", json).apply()
    }

    fun getCategoryFolder(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> "Images"
            "mp4", "mkv", "avi", "mov", "webm" -> "Videos"
            "mp3", "wav", "ogg", "flac", "m4a" -> "Music"
            "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx" -> "Documents"
            "zip", "rar", "7z", "tar", "gz" -> "Archives"
            "apk", "aab" -> "Apps"
            else -> "Other"
        }
    }
}
