package com.alexpo.grammermate.v2.core.data.audio

import android.content.Context
import android.os.Environment
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Логгер событий загрузки нативных моделей (TTS/ASR) в общую папку Downloads с
 * memory info в каждой строке.
 *
 * **Архитектурная роль.** GAP C5 / AC-29 (SRS-003 NFR-2): диагностика OOM/
 * native-memory regression. При каждом load event пишется timestamp + level +
 * message + memory snapshot (`MemoryChecker.getMemoryInfo()`) — для post-mortem
 * анализа, почему модель не загрузилась или RSS не вернулся к baseline после
 * load/release.
 *
 * Перенос из legacy `data/ModelLoadLogger.kt` (v1). SRS-003 §2.10 (ModelLoadLogger
 * row), GAP C5.
 *
 * @param context application context (для построения пути Downloads).
 * @param memoryChecker source детальной memory info (NFR-2, shared с [MemoryChecker]).
 */
@Singleton
class ModelLoadLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryChecker: MemoryChecker,
) {

    private val logFile = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "BaseGrammy/model_load_log.txt"
    )

    /**
     * Записать [event] в лог-файл (+ memory snapshot) и в Logcat. Best-effort:
     * IO-ошибки логируются, но не пробрасываются (логирование не должно ронять
     * загрузку модели).
     *
     * TODO(body, AC-29): при необходимости — добавить ротацию/размер-кап; сейчас
     *   append-only как в legacy. Storage-permission требуется для записи в
     *   public Downloads (handle в UI/E13).
     */
    fun log(event: LogEvent) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val memory = memoryChecker.getMemoryInfo()
        val line = "[$timestamp] [${event.level.name}] ${event.message} | Memory: $memory\n"
        try {
            logFile.parentFile?.mkdirs()
            logFile.appendText(line)
            Log.d(TAG, line.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    companion object {
        private const val TAG = "ModelLoadLogger"
    }
}

/**
 * Одно событие лога: severity + сообщение.
 *
 * Перенос из legacy `data/ModelLoadLogger.kt` (LogEvent/LogLevel).
 */
data class LogEvent(
    val level: LogLevel,
    val message: String,
)

/** Severity лога. Перенос из legacy. */
enum class LogLevel {
    INFO,
    WARN,
    ERROR,
}
