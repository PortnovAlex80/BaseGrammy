package com.alexpo.grammermate.testharness

import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillComboProgress
import com.alexpo.grammermate.data.VerbDrillLastSessionState
import com.alexpo.grammermate.data.VerbDrillStore

/**
 * In-memory fake implementation of VerbDrillStore for testing.
 * No file I/O, no YAML parsing, no Android dependencies.
 *
 * **UPDATED BEHAVIOR:**
 * - NO auto-deletion of stale sessions (removed 24-hour limit)
 * - All sessions are returned as-is from loadLastSession()
 * - User decides whether to resume or start fresh via dialog
 */
class FakeVerbDrillStore : VerbDrillStore {

    private val progressData = mutableMapOf<String, VerbDrillComboProgress>()
    private val cardsByPackAndLanguage = mutableMapOf<String, List<VerbDrillCard>>()

    // ── Last Session Persistence (VD-50) ────────────────────────────────────────
    private var lastSession: VerbDrillLastSessionState? = null

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

    // ── Last Session Persistence (VD-50) ────────────────────────────────────────

    /**
     * Load the last session state.
     *
     * **NEW BEHAVIOR:** Returns the session as-is, regardless of age.
     * NO auto-deletion of "stale" sessions — the user decides via dialog.
     */
    override fun loadLastSession(): VerbDrillLastSessionState? {
        // Simply return the stored session, no freshness check
        return lastSession
    }

    override fun saveLastSession(session: VerbDrillLastSessionState) {
        lastSession = session
    }

    override fun deleteLastSession() {
        lastSession = null
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
        lastSession = null
    }

    /**
     * Load verb drill test data from CourseTestDataFactory.
     *
     * @param packId Pack identifier
     * @param languageId Language code
     * @param verbDrillData VerbDrillCourseData from CourseTestDataFactory
     */
    fun loadVerbDrillData(
        packId: String,
        languageId: String,
        verbDrillData: com.alexpo.grammermate.testharness.CourseTestDataFactory.VerbDrillCourseData
    ) {
        val key = "$packId:$languageId"
        cardsByPackAndLanguage[key] = verbDrillData.allCards

        // Initialize progress for each tense-group combo
        for (tenseGroup in verbDrillData.tenseGroups) {
            val comboKey = "${packId}:${tenseGroup.tense}:${tenseGroup.group}"
            progressData[comboKey] = VerbDrillComboProgress(
                group = tenseGroup.group,
                tense = tenseGroup.tense,
                totalCards = tenseGroup.cards.size,
                everShownCardIds = emptySet(),
                todayShownCardIds = emptySet(),
                lastDate = ""
            )
        }
    }

    /**
     * Simulate showing cards for a specific tense-group combo.
     *
     * @param packId Pack identifier
     * @param tense Tense name
     * @param group Verb group
     * @param cardsShown Number of cards shown
     */
    fun simulateCardsShown(
        packId: String,
        tense: String,
        group: String,
        cardsShown: Int
    ) {
        val comboKey = "$packId:$tense:$group"
        val existing = progressData[comboKey] ?: return

        val allCards = loadAllCardsForPack(packId, "it")
        val comboCards = allCards.filter { it.tense == tense && it.group == group }
        val shownIds = comboCards.take(cardsShown).map { it.id }.toSet()

        progressData[comboKey] = existing.copy(
            everShownCardIds = existing.everShownCardIds + shownIds,
            todayShownCardIds = shownIds,
            lastDate = java.time.LocalDate.now().toString()
        )
    }

    /**
     * Get all available tenses for a pack.
     */
    fun getAvailableTenses(packId: String): Set<String> {
        val allCards = loadAllCardsForPack(packId, "it")
        return allCards.mapNotNull { it.tense }.toSet()
    }

    /**
     * Get all available groups for a pack.
     */
    fun getAvailableGroups(packId: String): Set<String> {
        val allCards = loadAllCardsForPack(packId, "it")
        return allCards.mapNotNull { it.group }.toSet()
    }
}
