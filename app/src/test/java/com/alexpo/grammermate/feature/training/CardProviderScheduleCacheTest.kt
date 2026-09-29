package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.SentenceCard
import org.junit.Assert.assertEquals
import org.junit.Test

class CardProviderScheduleCacheTest {
    @Test
    fun equalLessonIdsAndCardCountsWithDifferentContentRebuildSchedule() {
        val lessonId = LessonId("shared")
        fun lesson(prompt: String) = Lesson(
            lessonId, LanguageId("it"), "Shared",
            listOf(SentenceCard("card-1", prompt, listOf(prompt)))
        )
        val provider = CardProvider(subLessonSize = 1)
        provider.buildSchedules(listOf(lesson("From pack A")))

        val second = provider.buildSchedules(listOf(lesson("From pack B")))

        assertEquals("From pack B", second.getValue(lessonId).subLessons.first().cards.first().promptRu)
    }

    @Test
    fun mutatedCardListDoesNotKeepAnOldSchedule() {
        val lessonId = LessonId("shared")
        val cards = mutableListOf(SentenceCard("card-1", "Before", listOf("Before")))
        val lesson = Lesson(lessonId, LanguageId("it"), "Shared", cards)
        val provider = CardProvider(subLessonSize = 1)
        provider.buildSchedules(listOf(lesson))

        cards[0] = SentenceCard("card-1", "After", listOf("After"))
        val rebuilt = provider.buildSchedules(listOf(lesson))

        assertEquals("After", rebuilt.getValue(lessonId).subLessons.first().cards.first().promptRu)
    }
}
