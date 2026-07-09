package com.aistudio.smartshare.abcd.data

import android.content.Context
import android.content.SharedPreferences
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

class HistoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("smartshare_history", Context.MODE_PRIVATE)

    fun addHistoryItem(item: TransferHistoryItem) {
        val currentList = getHistory().toMutableList()
        currentList.add(0, item)
        if (currentList.size > 100) {
            currentList.removeAt(currentList.size - 1)
        }
        val json = Json.encodeToString(currentList)
        prefs.edit().putString("history_list", json).apply()
    }

    fun getHistory(): List<TransferHistoryItem> {
        val json = prefs.getString("history_list", null) ?: return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearHistory() {
        prefs.edit().remove("history_list").apply()
    }

    fun getCategoryFolder(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "ico" -> "Images"
            "mp4", "mkv", "avi", "mov", "webm", "flv", "wmv", "3gp" -> "Videos"
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "epub", "html" -> "Documents"
            "mp3", "wav", "m4a", "ogg", "flac", "aac", "wma", "amr" -> "Music"
            else -> "Others"
        }
    }
}
