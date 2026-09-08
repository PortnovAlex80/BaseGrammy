package com.alexpo.grammermate.data

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ModelLoadLogger(private val context: Context) {

    private val logFile = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "BaseGrammy/model_load_log.txt"
    )

    fun log(event: LogEvent) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val memory = getMemoryInfo()
        val line = "[$timestamp] [${event.level.name}] ${event.message} | Memory: $memory\n"
        try {
            logFile.parentFile?.mkdirs()
            logFile.appendText(line)
            Log.d(TAG, line.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    private fun getMemoryInfo(): String {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val max = rt.maxMemory() / (1024 * 1024)
        return "${used}MB/${max}MB"
    }

    companion object {
        private const val TAG = "ModelLoadLogger"
    }
}

data class LogEvent(
    val level: LogLevel,
    val message: String
)

enum class LogLevel {
    INFO,
    WARN,
    ERROR
}
