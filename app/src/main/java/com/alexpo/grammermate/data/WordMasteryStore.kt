package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File

interface WordMasteryStore {

    fun loadAll(): Map<String, WordMasteryState>

    fun saveAll(mastery: Map<String, WordMasteryState>)

    fun getMastery(wordId: String): WordMasteryState?

    fun upsertMastery(state: WordMasteryState)

    fun getDueWords(): Set<String>

    fun getMasteredCount(pos: String? = null): Int

    fun getMasteredByPos(): Map<String, Int>
}

/**
 * YAML-backed store for per-word mastery state in the Anki-like vocab drill.
 *
 * Persistence pattern follows VerbDrillStore:
 *   - loadAll() reads the full YAML file into a Map
 *   - saveAll() writes the full map via AtomicFileWriter
 *   - upsertMastery() does load-modify-save
 *
 * File location: grammarmate/drills/{packId}/word_mastery.yaml (pack-scoped)
 *                or grammarmate/word_mastery.yaml (legacy, when packId is null)
 */
class WordMasteryStoreImpl(
    context: Context,
    private val packId: String? = null
) : WordMasteryStore {

    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file: File = if (packId != null) {
        File(baseDir, "drills/$packId/word_mastery.yaml")
    } else {
        File(baseDir, "word_mastery.yaml")
    }
    private val schemaVersion = 1

    // In-memory cache — follows MasteryStore pattern
    private var cache: Map<String, WordMasteryState> = emptyMap()
    private var cacheLoaded = false

    /**
     * Load all mastery records. Returns cached result if available.
     */
    override fun loadAll(): Map<String, WordMasteryState> {
        if (cacheLoaded) return cache
        val loaded = loadAllFromDisk()
        cache = loaded
        cacheLoaded = true
        return loaded
    }

    private fun loadAllFromDisk(): Map<String, WordMasteryState> {
        if (!file.exists() || file.length() == 0L) return emptyMap()
        val raw = try { yaml.load<Any>(file.readText()) } catch (_: Exception) { null } ?: return emptyMap()
        val data = when (raw) {
            is Map<*, *> -> raw
            else -> return emptyMap()
        }
        val payload = (data["data"] as? Map<*, *>) ?: data
        val result = mutableMapOf<String, WordMasteryState>()
        for ((key, value) in payload) {
            val wordId = key as? String ?: continue
            val entry = value as? Map<*, *> ?: continue
            result[wordId] = WordMasteryState(
                wordId = wordId,
                intervalStepIndex = (entry["intervalStepIndex"] as? Number)?.toInt() ?: 0,
                correctCount = (entry["correctCount"] as? Number)?.toInt() ?: 0,
                incorrectCount = (entry["incorrectCount"] as? Number)?.toInt() ?: 0,
                lastReviewDateMs = (entry["lastReviewDateMs"] as? Number)?.toLong() ?: 0L,
                nextReviewDateMs = (entry["nextReviewDateMs"] as? Number)?.toLong() ?: 0L,
                isLearned = entry["isLearned"] as? Boolean ?: false
            )
        }
        return result
    }

    /**
     * Save all mastery records to the YAML file via AtomicFileWriter.
     * Also updates the in-memory cache.
     */
    override fun saveAll(mastery: Map<String, WordMasteryState>) {
        cache = mastery
        cacheLoaded = true
        val payload = linkedMapOf<String, Any>()
        for ((wordId, state) in mastery) {
            payload[wordId] = linkedMapOf(
                "intervalStepIndex" to state.intervalStepIndex,
                "correctCount" to state.correctCount,
                "incorrectCount" to state.incorrectCount,
                "lastReviewDateMs" to state.lastReviewDateMs,
                "nextReviewDateMs" to state.nextReviewDateMs,
                "isLearned" to state.isLearned
            )
        }
        val data = linkedMapOf(
            "schemaVersion" to schemaVersion,
            "data" to payload
        )
        AtomicFileWriter.writeText(file, yaml.dump(data))
    }

    /**
     * Get the mastery state for a single word, or null if never reviewed.
     */
    override fun getMastery(wordId: String): WordMasteryState? {
        return loadAll()[wordId]
    }

    /**
     * Insert or update the mastery state for a single word.
     * Performs load-modify-save with cache update.
     */
    override fun upsertMastery(state: WordMasteryState) {
        val all = loadAll().toMutableMap()
        all[state.wordId] = state
        cache = all
        cacheLoaded = true
        saveAll(all)
    }

    /**
     * Return the set of word IDs that are currently due for review.
     * A word is due if:
     *   - nextReviewDateMs <= now (scheduled review time has passed), OR
     *   - lastReviewDateMs == 0 (never reviewed, so due immediately)
     */
    override fun getDueWords(): Set<String> {
        val now = System.currentTimeMillis()
        return loadAll().filter { (_, state) ->
            state.nextReviewDateMs <= now || state.lastReviewDateMs == 0L
        }.keys
    }

    /**
     * Count words that have been mastered (isLearned == true).
     * Optionally filter by part of speech extracted from the word ID prefix
     * (e.g. "nouns_casa" -> "nouns").
     */
    override fun getMasteredCount(pos: String?): Int {
        val all = loadAll()
        val learned = all.filter { (_, state) -> state.isLearned }
        return if (pos != null) {
            learned.count { (wordId, _) -> wordId.startsWith("${pos}_") }
        } else {
            learned.size
        }
    }

    /**
     * Return a map of POS -> count of mastered words.
     * POS is extracted from the word ID prefix (e.g. "nouns_casa" -> "nouns").
     */
    override fun getMasteredByPos(): Map<String, Int> {
        val all = loadAll()
        val result = mutableMapOf<String, Int>()
        for ((wordId, state) in all) {
            if (!state.isLearned) continue
            val pos = wordId.indexOf('_').let { idx ->
                if (idx > 0) wordId.substring(0, idx) else "unknown"
            }
            result[pos] = (result[pos] ?: 0) + 1
        }
        return result
    }

    /**
     * Invalidate the in-memory cache. Called when data is externally reset.
     */
    fun invalidateCache() {
        cache = emptyMap()
        cacheLoaded = false
    }
}
