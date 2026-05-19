package com.alexpo.grammermate.testharness

import com.alexpo.grammermate.data.WordMasteryState
import com.alexpo.grammermate.data.WordMasteryStore

/**
 * In-memory fake implementation of WordMasteryStore for testing.
 * No file I/O, no YAML parsing, no Android dependencies.
 */
class FakeWordMasteryStore : WordMasteryStore {

    private val masteryData = mutableMapOf<String, WordMasteryState>()

    override fun loadAll(): Map<String, WordMasteryState> = masteryData

    override fun saveAll(mastery: Map<String, WordMasteryState>) {
        masteryData.clear()
        masteryData.putAll(mastery)
    }

    override fun getMastery(wordId: String): WordMasteryState? = masteryData[wordId]

    override fun upsertMastery(state: WordMasteryState) {
        masteryData[state.wordId] = state
    }

    override fun getDueWords(): Set<String> {
        val now = System.currentTimeMillis()
        return masteryData.filter { (_, state) ->
            state.nextReviewDateMs <= now || state.lastReviewDateMs == 0L
        }.keys
    }

    override fun getMasteredCount(pos: String?): Int {
        val learned = masteryData.filter { (_, state) -> state.isLearned }
        return if (pos != null) {
            learned.count { (wordId, _) -> wordId.startsWith("${pos}_") }
        } else {
            learned.size
        }
    }

    override fun getMasteredByPos(): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        for ((wordId, state) in masteryData) {
            if (!state.isLearned) continue
            val pos = wordId.indexOf('_').let { idx ->
                if (idx > 0) wordId.substring(0, idx) else "unknown"
            }
            result[pos] = (result[pos] ?: 0) + 1
        }
        return result
    }

    /**
     * Test helper: Clear all data.
     */
    fun clear() {
        masteryData.clear()
    }

    /**
     * Test helper: Set mastery for a word directly.
     */
    fun setMastery(state: WordMasteryState) {
        masteryData[state.wordId] = state
    }
}
