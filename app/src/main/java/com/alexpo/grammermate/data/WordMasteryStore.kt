package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * YAML-backed store for per-word mastery state in the Anki-like vocab drill.
 *
 * Persistence pattern follows VerbDrillStore:
 *   - loadAll() reads the full YAML file into a Map
 *   - saveAll() writes the full map via AtomicFileWriter
 *   - upsertMastery() does load-modify-save
 *
 * File location: grammarmate/word_mastery.yaml
 */
class WordMasteryStore(context: Context) {

    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "word_mastery.yaml")
    private val schemaVersion = 1

    /**
     * Load all mastery records from the YAML file.
     * Returns an empty map if the file does not exist or is empty.
     */
    fun loadAll(): Map<String, WordMasteryState> {
        if (!file.exists()) return emptyMap()
        val raw = yaml.load<Any>(file.readText()) ?: return emptyMap()
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
     */
    fun saveAll(mastery: Map<String, WordMasteryState>) {
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
    fun getMastery(wordId: String): WordMasteryState? {
        return loadAll()[wordId]
    }

    /**
     * Insert or update the mastery state for a single word.
     * Performs load-modify-save (same pattern as VerbDrillStore.upsertComboProgress).
     */
    fun upsertMastery(state: WordMasteryState) {
        val all = loadAll().toMutableMap()
        all[state.wordId] = state
        saveAll(all)
    }

    /**
     * Return the set of word IDs that are currently due for review.
     * A word is due if:
     *   - nextReviewDateMs <= now (scheduled review time has passed), OR
     *   - lastReviewDateMs == 0 (never reviewed, so due immediately)
     */
    fun getDueWords(): Set<String> {
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
    fun getMasteredCount(pos: String? = null): Int {
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
    fun getMasteredByPos(): Map<String, Int> {
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
}
