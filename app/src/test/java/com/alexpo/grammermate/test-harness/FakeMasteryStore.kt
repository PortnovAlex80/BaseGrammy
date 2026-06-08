package com.alexpo.grammermate.testharness

import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.MasteryStore

/**
 * In-memory fake implementation of MasteryStore for testing.
 * No file I/O, no YAML parsing, no Android dependencies.
 * Only implements current pack-scoped interface methods.
 */
class FakeMasteryStore : MasteryStore {

    private val data = mutableMapOf<String, MutableMap<String, LessonMasteryState>>()

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

    override fun getOrCreateForPack(packId: String, lessonId: String): LessonMasteryState {
        return getForPack(packId, lessonId) ?: LessonMasteryState(
            lessonId = LessonId(lessonId),
            languageId = LanguageId("")
        )
    }

    override fun clear() {
        data.clear()
    }

    override fun clearPack(packId: String) {
        data.remove("pack:$packId")
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

    override fun flush() {
        // No-op for tests
    }

    override fun loadAll(): Map<String, Map<String, LessonMasteryState>> = data

    // --- Test helpers ---

    /**
     * Simulate completion of a pack-scoped lesson by recording card shows.
     */
    fun simulatePackLessonCompletion(
        packId: String,
        lessonId: String,
        cardsToShow: Int = 15,
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
     * Reset all mastery data for testing.
     */
    fun resetAll() {
        data.clear()
    }

    /**
     * Get the number of packs with mastery data.
     */
    fun getPackCount(): Int = data.size

    /**
     * Get all lesson IDs that have mastery data for a pack.
     */
    fun getLessonIdsForPack(packId: String): Set<String> {
        return data["pack:$packId"]?.keys ?: emptySet()
    }
}
