package com.alexpo.grammermate.testharness

import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillComboProgress
import com.alexpo.grammermate.data.VerbDrillStore

/**
 * In-memory fake implementation of VerbDrillStore for testing.
 * No file I/O, no YAML parsing, no Android dependencies.
 */
class FakeVerbDrillStore : VerbDrillStore {

    private val progressData = mutableMapOf<String, VerbDrillComboProgress>()
    private val cardsByPackAndLanguage = mutableMapOf<String, List<VerbDrillCard>>()

    override fun loadProgress(): Map<String, VerbDrillComboProgress> = progressData

    override fun saveProgress(progress: Map<String, VerbDrillComboProgress>) {
        progressData.clear()
        progressData.putAll(progress)
    }

    override fun getComboProgress(key: String): VerbDrillComboProgress? = progressData[key]

    override fun upsertComboProgress(key: String, progress: VerbDrillComboProgress) {
        progressData[key] = progress
    }

    override fun loadAllCardsForPack(targetPackId: String, languageId: String): List<VerbDrillCard> {
        val key = "$targetPackId:$languageId"
        return cardsByPackAndLanguage[key] ?: emptyList()
    }

    override fun getCardsForTenses(packId: String, languageId: String, tenses: List<String>): List<VerbDrillCard> {
        if (tenses.isEmpty()) return emptyList()
        val allCards = loadAllCardsForPack(packId, languageId)
        val tenseSet = tenses.toSet()
        return allCards.filter { it.tense != null && it.tense in tenseSet }
    }

    override fun flush() {
        // No-op for tests
    }

    /**
     * Test helper: Set cards for a specific pack and language.
     */
    fun setCards(packId: String, languageId: String, cards: List<VerbDrillCard>) {
        val key = "$packId:$languageId"
        cardsByPackAndLanguage[key] = cards
    }

    /**
     * Test helper: Clear all data.
     */
    fun clear() {
        progressData.clear()
        cardsByPackAndLanguage.clear()
    }
}
