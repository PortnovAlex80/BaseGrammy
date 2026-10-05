package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.*
import org.junit.Assert.*
import org.junit.Test

class HiddenCardOriginRegressionTest {
    private fun lesson(pack: String, id: String) = Lesson(
        LessonId(id), LanguageId("en"), id,
        (1..4).map { index -> SentenceCard("card_$index", "$id $index", listOf("answer $index"), sourcePackId = pack, sourceLessonId = id) }
    )

    @Test fun mixedDeckKeepsEqualRowIdsFromOtherLessonsAndPacks() {
        val first = lesson("pack-a", "first")
        val second = lesson("pack-a", "second")
        val otherPack = lesson("pack-b", "first")
        val hidden = setOf(HiddenCardKeys.forCard(first.cards.first())!!)
        val provider = CardProvider(subLessonSize = 20)
        for (mode in listOf(TrainingMode.ALL_SEQUENTIAL, TrainingMode.ALL_MIXED)) {
            val result = provider.buildSessionCards(listOf(first, second, otherPack), mode, null, emptyMap(), 0, hidden)
            assertFalse(result.cards.contains(first.cards.first()))
            assertTrue(result.cards.contains(second.cards.first()))
            assertTrue(result.cards.contains(otherPack.cards.first()))
        }
        val reviewed = provider.buildReviewCards(listOf(first, second), second.id, hidden)
        assertEquals(second.cards.toSet(), reviewed.toSet())
    }

    @Test fun selectedLessonAndDynamicReviewSlotsUseEachCardsOwnOrigin() {
        val first = lesson("pack-a", "first")
        val second = lesson("pack-a", "second")
        val hidden = first.cards.map { HiddenCardKeys.forCard(it)!! }.toSet()
        val provider = CardProvider(subLessonSize = 2)
        val lessons = listOf(first, second)
        val schedules = provider.buildSchedules(lessons)
        val result = provider.buildSessionCards(lessons, TrainingMode.LESSON, second.id, schedules, 1, hidden,
            masteryOf = { LessonMasteryState(LessonId(it), LanguageId("en"), lastReviewMs = 1L) }, nowMs = 86_400_000L)
        assertTrue(result.cards.isNotEmpty())
        assertTrue(result.cards.all { it.sourceLessonId == "second" })
        val selectedReview = ReviewSelector.selectReviewCards(
            candidates = lessons, masteryOf = { LessonMasteryState(LessonId(it), LanguageId("en"), lastReviewMs = 1L) },
            totalEffortCards = 0, nowMs = 86_400_000L, slots = 2, activeSubLessonIndex = 0, hiddenCardIds = hidden)
        assertEquals(2, selectedReview.size)
        assertTrue(selectedReview.all { it.sourceLessonId == "second" })
    }

    @Test fun unresolvedLegacyRowIdsNeverHideEveryMatchingCatalogCard() {
        val first = lesson("pack-a", "first")
        val provider = CardProvider()
        val result = provider.buildReviewCards(listOf(first), first.id, setOf("card_1"))
        assertEquals(first.cards.toSet(), result.toSet())
    }
}
