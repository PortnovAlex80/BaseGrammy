package com.alexpo.grammermate.testharness

import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.MasteryStore

/**
 * In-memory fake implementation of MasteryStore for testing.
 * No file I/O, no YAML parsing, no Android dependencies.
 */
class FakeMasteryStore : MasteryStore {

    private val data = mutableMapOf<String, MutableMap<String, LessonMasteryState>>()

    override fun loadAll(): Map<String, Map<String, LessonMasteryState>> = data

    override fun get(lessonId: String, languageId: String): LessonMasteryState? =
        data[languageId]?.get(lessonId)

    override fun save(state: LessonMasteryState) {
        val langId = state.languageId.value
        val lessonId = state.lessonId.value
        if (!data.containsKey(langId)) {
            data[langId] = mutableMapOf()
        }
        data[langId]!![lessonId] = state
    }

    override fun recordCardShow(lessonId: String, languageId: String, cardId: String) {
        val existing = get(lessonId, languageId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId(languageId)
        )
        val isNewCard = !existing.shownCardIds.contains(cardId)
        val newShownCardIds = existing.shownCardIds + cardId

        val updated = existing.copy(
            uniqueCardShows = if (isNewCard) existing.uniqueCardShows + 1 else existing.uniqueCardShows,
            totalCardShows = existing.totalCardShows + 1,
            lastShowDateMs = System.currentTimeMillis(),
            shownCardIds = newShownCardIds
        )
        save(updated)
    }

    override fun markCardsShownForProgress(lessonId: String, languageId: String, cardIds: Collection<String>) {
        if (cardIds.isEmpty()) return
        val existing = get(lessonId, languageId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId(languageId)
        )
        val updated = existing.copy(shownCardIds = existing.shownCardIds + cardIds)
        save(updated)
    }

    override fun markLessonCompleted(lessonId: String, languageId: String) {
        val existing = get(lessonId, languageId) ?: return
        if (existing.completedAtMs != null) return // Already completed

        val updated = existing.copy(completedAtMs = System.currentTimeMillis())
        save(updated)
    }

    override fun getOrCreate(lessonId: String, languageId: String): LessonMasteryState {
        return get(lessonId, languageId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId(languageId)
        )
    }

    override fun clear() {
        data.clear()
    }

    override fun clearLanguage(languageId: String) {
        data.remove(languageId)
    }

    override fun recordCardEncounter(lessonId: String, languageId: String, cardId: String): Int {
        val existing = get(lessonId, languageId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId(languageId)
        )
        val currentCount = existing.cardEncounterCounts[cardId] ?: 0
        val newCount = currentCount + 1
        val updated = existing.copy(
            cardEncounterCounts = existing.cardEncounterCounts + (cardId to newCount)
        )
        save(updated)
        return newCount
    }

    override fun getCardEncounterCount(lessonId: String, languageId: String, cardId: String): Int {
        return get(lessonId, languageId)?.cardEncounterCounts?.get(cardId) ?: 0
    }

    override fun flush() {
        // No-op for tests
    }

    // --- Pack-scoped methods ---

    override fun getForPack(packId: String, lessonId: String): LessonMasteryState? {
        val packKey = "pack:$packId"
        return data[packKey]?.get(lessonId)
    }

    override fun saveForPack(state: LessonMasteryState, packId: String) {
        val packKey = "pack:$packId"
        if (!data.containsKey(packKey)) {
            data[packKey] = mutableMapOf()
        }
        data[packKey]!![state.lessonId.value] = state
    }

    override fun recordCardShowForPack(packId: String, lessonId: String, cardId: String) {
        val packKey = "pack:$packId"
        val existing = data[packKey]?.get(lessonId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId("")
        )
        val isNewCard = !existing.shownCardIds.contains(cardId)
        val newShownCardIds = existing.shownCardIds + cardId

        val updated = existing.copy(
            uniqueCardShows = if (isNewCard) existing.uniqueCardShows + 1 else existing.uniqueCardShows,
            totalCardShows = existing.totalCardShows + 1,
            lastShowDateMs = System.currentTimeMillis(),
            shownCardIds = newShownCardIds
        )
        saveForPack(updated, packId)
    }

    override fun getOrCreateForPack(packId: String, lessonId: String): LessonMasteryState {
        return getForPack(packId, lessonId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId("")
        )
    }

    override fun markCardsShownForProgressForPack(packId: String, lessonId: String, cardIds: Collection<String>) {
        if (cardIds.isEmpty()) return
        val packKey = "pack:$packId"
        val existing = data[packKey]?.get(lessonId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId("")
        )
        val updated = existing.copy(shownCardIds = existing.shownCardIds + cardIds)
        saveForPack(updated, packId)
    }

    override fun markLessonCompletedForPack(packId: String, lessonId: String) {
        val packKey = "pack:$packId"
        val existing = data[packKey]?.get(lessonId) ?: return
        if (existing.completedAtMs != null) return // Already completed

        val updated = existing.copy(completedAtMs = System.currentTimeMillis())
        saveForPack(updated, packId)
    }

    override fun recordCardEncounterForPack(packId: String, lessonId: String, cardId: String): Int {
        val packKey = "pack:$packId"
        val existing = data[packKey]?.get(lessonId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId("")
        )
        val currentCount = existing.cardEncounterCounts[cardId] ?: 0
        val newCount = currentCount + 1
        val updated = existing.copy(
            cardEncounterCounts = existing.cardEncounterCounts + (cardId to newCount)
        )
        saveForPack(updated, packId)
        return newCount
    }

    override fun getCardEncounterCountForPack(packId: String, lessonId: String, cardId: String): Int {
        val packKey = "pack:$packId"
        return data[packKey]?.get(lessonId)?.cardEncounterCounts?.get(cardId) ?: 0
    }

    override fun clearPack(packId: String) {
        data.remove("pack:$packId")
    }

    // --- Test helpers ---

    /**
     * Simulate completion of a pack-scoped lesson by recording card shows.
     *
     * @param packId The pack ID
     * @param lessonId The lesson ID
     * @param cardsToShow Number of unique cards to mark as shown
     * @param markCompleted Whether to mark the lesson as completed
     */
    fun simulatePackLessonCompletion(
        packId: String,
        lessonId: String,
        cardsToShow: Int = 150,
        markCompleted: Boolean = true
    ) {
        for (i in 1..cardsToShow) {
            val cardId = "${lessonId}_card_$i"
            recordCardShowForPack(packId, lessonId, cardId)
        }

        if (markCompleted) {
            markLessonCompletedForPack(packId, lessonId)
        }
    }

    /**
     * Simulate completion of a lesson by recording card shows.
     *
     * @param lessonId The lesson ID
     * @param languageId The language ID
     * @param cardsToShow Number of unique cards to mark as shown
     * @param markCompleted Whether to mark the lesson as completed
     */
    fun simulateLessonCompletion(
        lessonId: String,
        languageId: String,
        cardsToShow: Int = 150,
        markCompleted: Boolean = true
    ) {
        // Record card shows for the specified number of cards
        for (i in 1..cardsToShow) {
            val cardId = "${lessonId}_card_$i"
            recordCardShow(lessonId, languageId, cardId)
        }

        // Mark lesson as completed if requested
        if (markCompleted) {
            markLessonCompleted(lessonId, languageId)
        }
    }

    /**
     * Simulate partial completion of a lesson.
     *
     * @param lessonId The lesson ID
     * @param languageId The language ID
     * @param cardsShown Number of unique cards already shown
     * @param isCompleted Whether the lesson is marked completed
     */
    fun simulatePartialProgress(
        lessonId: String,
        languageId: String,
        cardsShown: Int = 50,
        isCompleted: Boolean = false
    ) {
        val state = getOrCreate(lessonId, languageId)
        val shownCardIds = (1..cardsShown).map { "${lessonId}_card_$it" }.toSet()

        val updated = state.copy(
            uniqueCardShows = cardsShown,
            totalCardShows = cardsShown + 5,
            lastShowDateMs = System.currentTimeMillis() - (cardsShown * 60_000L),
            shownCardIds = shownCardIds,
            completedAtMs = if (isCompleted) System.currentTimeMillis() - 86_400_000L else null
        )
        save(updated)
    }

    /**
     * Reset all mastery data for testing.
     */
    fun resetAll() {
        data.clear()
    }

    /**
     * Get the number of lessons with mastery data.
     */
    fun getLessonCount(): Int = data.size

    /**
     * Get all lesson IDs that have mastery data.
     */
    fun getAllLessonIds(languageId: String? = null): Set<String> {
        return if (languageId != null) {
            data[languageId]?.keys ?: emptySet()
        } else {
            data.values.flatMap { it.keys }.toSet()
        }
    }
}
