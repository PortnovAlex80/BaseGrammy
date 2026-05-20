package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PomodoroHistoryStore(context: Context) {
    private val yaml = Yaml()
    private val file = File(context.filesDir, "grammarmate/pomodoro_history.yaml")
    private val lock = ReentrantLock()

    fun append(entry: PomodoroHistoryEntry) {
        lock.withLock {
            val sessions = loadAllInternal().toMutableList()
            sessions += entry
            val data = mapOf(KEY_SESSIONS to sessions.map { it.toMap() })
            AtomicFileWriter.writeText(file, yaml.dump(data))
        }
    }

    fun loadAll(languageId: String? = null): List<PomodoroHistoryEntry> {
        return lock.withLock {
            loadAllInternal()
                .filter { languageId == null || it.languageId == languageId }
                .sortedByDescending { it.completedAtMs }
        }
    }

    private fun loadAllInternal(): List<PomodoroHistoryEntry> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        return try {
            val raw = yaml.load<Any>(file.readText())
            val map = raw as? Map<*, *> ?: return emptyList()
            val sessions = map[KEY_SESSIONS] as? List<*> ?: return emptyList()
            sessions.mapNotNull { (it as? Map<*, *>)?.toEntry() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun PomodoroHistoryEntry.toMap(): Map<String, Any?> {
        return linkedMapOf(
            KEY_ID to id,
            KEY_LANGUAGE_ID to languageId,
            KEY_PACK_ID to packId,
            KEY_LESSON_ID to lessonId,
            KEY_COMPLETED_AT_MS to completedAtMs,
            KEY_DURATION_MINUTES to durationMinutes,
            KEY_TOTAL_SECONDS to totalSeconds,
            KEY_REMAINING_SECONDS to remainingSeconds,
            KEY_CARDS_SHOWN to cardsShown,
            KEY_CARDS_CORRECT to cardsCorrect,
            KEY_CARDS_INCORRECT to cardsIncorrect,
            KEY_WORDS_PER_MINUTE to wordsPerMinute
        )
    }

    private fun Map<*, *>.toEntry(): PomodoroHistoryEntry? {
        val languageId = this[KEY_LANGUAGE_ID] as? String ?: return null
        val completedAtMs = (this[KEY_COMPLETED_AT_MS] as? Number)?.toLong() ?: return null
        return PomodoroHistoryEntry(
            id = this[KEY_ID] as? String ?: "${languageId}_$completedAtMs",
            languageId = languageId,
            packId = this[KEY_PACK_ID] as? String,
            lessonId = this[KEY_LESSON_ID] as? String,
            completedAtMs = completedAtMs,
            durationMinutes = (this[KEY_DURATION_MINUTES] as? Number)?.toInt() ?: 0,
            totalSeconds = (this[KEY_TOTAL_SECONDS] as? Number)?.toInt() ?: 0,
            remainingSeconds = (this[KEY_REMAINING_SECONDS] as? Number)?.toInt() ?: 0,
            cardsShown = (this[KEY_CARDS_SHOWN] as? Number)?.toInt() ?: 0,
            cardsCorrect = (this[KEY_CARDS_CORRECT] as? Number)?.toInt() ?: 0,
            cardsIncorrect = (this[KEY_CARDS_INCORRECT] as? Number)?.toInt() ?: 0,
            wordsPerMinute = (this[KEY_WORDS_PER_MINUTE] as? Number)?.toDouble() ?: 0.0
        )
    }

    companion object {
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_ID = "id"
        private const val KEY_LANGUAGE_ID = "languageId"
        private const val KEY_PACK_ID = "packId"
        private const val KEY_LESSON_ID = "lessonId"
        private const val KEY_COMPLETED_AT_MS = "completedAtMs"
        private const val KEY_DURATION_MINUTES = "durationMinutes"
        private const val KEY_TOTAL_SECONDS = "totalSeconds"
        private const val KEY_REMAINING_SECONDS = "remainingSeconds"
        private const val KEY_CARDS_SHOWN = "cardsShown"
        private const val KEY_CARDS_CORRECT = "cardsCorrect"
        private const val KEY_CARDS_INCORRECT = "cardsIncorrect"
        private const val KEY_WORDS_PER_MINUTE = "wordsPerMinute"
    }
}
