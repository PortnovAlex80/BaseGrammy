package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Persistence store for vocab sprint progress and spaced repetition data.
 * Tracks which vocab entries have been completed for a lesson and when.
 *
 * Schema:
 *   languageId ->
 *     lessonId ->
 *       "completedIndices" -> [0, 1, 5, ...]  (entry indices completed in current sprint)
 *       "entries" ->
 *         entryId ->
 *           "lastCorrectMs" -> timestamp
 *           "lastIncorrectMs" -> timestamp
 *           "intervalStep" -> 0..4
 */
class VocabProgressStore(private val context: Context) {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "vocab_progress.yaml")
    private val mutex = ReentrantLock()

    private var cache: MutableMap<String, MutableMap<String, LessonVocabProgress>> = mutableMapOf()
    private var cacheLoaded = false

    data class EntrySrsState(
        val lastCorrectMs: Long = 0L,
        val lastIncorrectMs: Long = 0L,
        val intervalStep: Int = 0
    )

    data class LessonVocabProgress(
        val completedIndices: Set<Int> = emptySet(),
        val entryStates: Map<String, EntrySrsState> = emptyMap()
    )

    // Spaced repetition intervals in days: [1, 3, 7, 14, 30]
    companion object {
        val INTERVALS_DAYS = intArrayOf(1, 3, 7, 14, 30)
    }

    fun loadAll(): Map<String, Map<String, LessonVocabProgress>> = mutex.withLock {
        loadAllInternal()
    }

    private fun loadAllInternal(): Map<String, Map<String, LessonVocabProgress>> {
        if (cacheLoaded) return cache

        if (!file.exists()) {
            cacheLoaded = true
            return cache
        }

        val previousCache = cache

        try {
            val raw = yaml.load<Any>(file.readText()) ?: return cache
            val data = (raw as? Map<*, *>) ?: return cache
            val payload = (data["data"] as? Map<*, *>) ?: data

            for ((langKey, langValue) in payload) {
                val languageId = langKey as? String ?: continue
                val lessonMap = langValue as? Map<*, *> ?: continue

                cache[languageId] = mutableMapOf()

                for ((lessonKey, lessonValue) in lessonMap) {
                    val lessonId = lessonKey as? String ?: continue
                    val lessonData = lessonValue as? Map<*, *> ?: continue

                    val completedIndices = (lessonData["completedIndices"] as? List<*>)
                        ?.mapNotNull { (it as? Number)?.toInt() }
                        ?.toSet()
                        ?: emptySet()

                    val entryStates = mutableMapOf<String, EntrySrsState>()
                    val entriesRaw = lessonData["entries"] as? Map<*, *>
                    if (entriesRaw != null) {
                        for ((entryKey, entryValue) in entriesRaw) {
                            val entryId = entryKey as? String ?: continue
                            val entryMap = entryValue as? Map<*, *> ?: continue
                            entryStates[entryId] = EntrySrsState(
                                lastCorrectMs = (entryMap["lastCorrectMs"] as? Number)?.toLong() ?: 0L,
                                lastIncorrectMs = (entryMap["lastIncorrectMs"] as? Number)?.toLong() ?: 0L,
                                intervalStep = (entryMap["intervalStep"] as? Number)?.toInt() ?: 0
                            )
                        }
                    }

                    cache[languageId]!![lessonId] = LessonVocabProgress(
                        completedIndices = completedIndices,
                        entryStates = entryStates
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("VocabProgressStore", "Failed to parse ${file.name}", e)
            cache = previousCache
        }

        cacheLoaded = true
        return cache
    }

    fun get(lessonId: String, languageId: String): LessonVocabProgress {
        loadAll()
        return cache[languageId]?.get(lessonId) ?: LessonVocabProgress()
    }

    /**
     * Save completed entry indices for a lesson (sprint progress).
     */
    fun saveCompletedIndices(lessonId: String, languageId: String, indices: Set<Int>) = mutex.withLock {
        loadAllInternal()
        if (!cache.containsKey(languageId)) {
            cache[languageId] = mutableMapOf()
        }
        val existing = cache[languageId]!![lessonId] ?: LessonVocabProgress()
        cache[languageId]!![lessonId] = existing.copy(completedIndices = indices)
        persistToFile()
    }

    /**
     * Add a completed index to the current sprint progress.
     */
    fun addCompletedIndex(lessonId: String, languageId: String, index: Int) = mutex.withLock {
        loadAllInternal()
        if (!cache.containsKey(languageId)) {
            cache[languageId] = mutableMapOf()
        }
        val existing = cache[languageId]!![lessonId] ?: LessonVocabProgress()
        cache[languageId]!![lessonId] = existing.copy(
            completedIndices = existing.completedIndices + index
        )
        persistToFile()
    }

    /**
     * Clear sprint progress (all completed indices) for a lesson.
     */
    fun clearSprintProgress(lessonId: String, languageId: String) = mutex.withLock {
        loadAllInternal()
        val existing = cache[languageId]?.get(lessonId) ?: return
        cache[languageId]!![lessonId] = existing.copy(completedIndices = emptySet())
        persistToFile()
    }

    /**
     * Record that a vocab entry was answered correctly.
     * Updates the SRS state: moves to next interval step.
     */
    fun recordCorrect(entryId: String, lessonId: String, languageId: String) = mutex.withLock {
        loadAllInternal()
        if (!cache.containsKey(languageId)) {
            cache[languageId] = mutableMapOf()
        }
        if (!cache[languageId]!!.containsKey(lessonId)) {
            cache[languageId]!![lessonId] = LessonVocabProgress()
        }
        val progress = cache[languageId]!![lessonId]!!
        val existing = progress.entryStates[entryId] ?: EntrySrsState()
        val now = System.currentTimeMillis()
        val nextStep = (existing.intervalStep + 1).coerceAtMost(INTERVALS_DAYS.lastIndex)
        val updatedState = existing.copy(
            lastCorrectMs = now,
            intervalStep = nextStep
        )
        val updatedEntries = progress.entryStates + (entryId to updatedState)
        cache[languageId]!![lessonId] = progress.copy(entryStates = updatedEntries)
        persistToFile()
    }

    /**
     * Record that a vocab entry was answered incorrectly.
     * Resets the SRS interval step back to 0.
     */
    fun recordIncorrect(entryId: String, lessonId: String, languageId: String) = mutex.withLock {
        loadAllInternal()
        if (!cache.containsKey(languageId)) {
            cache[languageId] = mutableMapOf()
        }
        if (!cache[languageId]!!.containsKey(lessonId)) {
            cache[languageId]!![lessonId] = LessonVocabProgress()
        }
        val progress = cache[languageId]!![lessonId]!!
        val existing = progress.entryStates[entryId] ?: EntrySrsState()
        val now = System.currentTimeMillis()
        val updatedState = existing.copy(
            lastIncorrectMs = now,
            intervalStep = 0  // Reset on incorrect
        )
        val updatedEntries = progress.entryStates + (entryId to updatedState)
        cache[languageId]!![lessonId] = progress.copy(entryStates = updatedEntries)
        persistToFile()
    }

    /**
     * Check if a vocab entry is due for review (past its review interval).
     */
    fun isDueForReview(entryId: String, lessonId: String, languageId: String): Boolean {
        loadAll()
        val state = cache[languageId]?.get(lessonId)?.entryStates?.get(entryId) ?: return false
        if (state.lastCorrectMs <= 0L) return false
        val intervalDays = INTERVALS_DAYS[state.intervalStep.coerceIn(0, INTERVALS_DAYS.lastIndex)]
        val dueMs = state.lastCorrectMs + (intervalDays.toLong() * 24 * 60 * 60 * 1000)
        return System.currentTimeMillis() >= dueMs
    }

    /**
     * Sort vocab entries for a sprint using Anki-like prioritization:
     * 1. Overdue words (past their review interval)
     * 2. New words (never answered correctly)
     * 3. Words not yet due
     */
    fun sortEntriesForSprint(
        entries: List<VocabEntry>,
        lessonId: String,
        languageId: String
    ): List<VocabEntry> {
        loadAll()
        val progress = cache[languageId]?.get(lessonId) ?: return entries.shuffled()

        val overdue = mutableListOf<VocabEntry>()
        val newWords = mutableListOf<VocabEntry>()
        val notDue = mutableListOf<VocabEntry>()

        for (entry in entries) {
            val state = progress.entryStates[entry.id]
            if (state == null || state.lastCorrectMs <= 0L) {
                newWords.add(entry)
            } else {
                val intervalDays = INTERVALS_DAYS[state.intervalStep.coerceIn(0, INTERVALS_DAYS.lastIndex)]
                val dueMs = state.lastCorrectMs + (intervalDays.toLong() * 24 * 60 * 60 * 1000)
                if (System.currentTimeMillis() >= dueMs) {
                    overdue.add(entry)
                } else {
                    notDue.add(entry)
                }
            }
        }

        // Shuffle within each group for variety
        return overdue.shuffled() + newWords.shuffled() + notDue.shuffled()
    }

    fun clear() {
        cache.clear()
        cacheLoaded = true
        if (file.exists()) {
            file.delete()
        }
    }

    private fun persistToFile() {
        val payload = linkedMapOf<String, Any>()

        for ((languageId, lessonMap) in cache) {
            val lessonsPayload = linkedMapOf<String, Any>()

            for ((lessonId, progress) in lessonMap) {
                val entriesPayload = linkedMapOf<String, Any>()
                for ((entryId, state) in progress.entryStates) {
                    entriesPayload[entryId] = linkedMapOf(
                        "lastCorrectMs" to state.lastCorrectMs,
                        "lastIncorrectMs" to state.lastIncorrectMs,
                        "intervalStep" to state.intervalStep
                    )
                }

                lessonsPayload[lessonId] = linkedMapOf(
                    "completedIndices" to progress.completedIndices.toList(),
                    "entries" to entriesPayload
                )
            }

            payload[languageId] = lessonsPayload
        }

        val data = linkedMapOf(
            "schemaVersion" to 1,
            "data" to payload
        )

        AtomicFileWriter.writeText(file, yaml.dump(data))
    }
}
