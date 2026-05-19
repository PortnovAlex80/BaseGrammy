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
}
