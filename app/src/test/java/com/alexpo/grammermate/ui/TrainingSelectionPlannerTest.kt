package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.NavigationState
import com.alexpo.grammermate.data.PackId
import com.alexpo.grammermate.data.SentenceCard
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingSelectionPlannerTest {
    private fun lesson(id: String, prompt: String) = Lesson(
        LessonId(id), LanguageId("it"), id,
        listOf(SentenceCard("card", prompt, listOf(prompt)))
    )

    @Test
    fun disjointPackPublishesIdsWithLessonsBeforeScheduleFiltering() {
        val previous = NavigationState(
            activePackId = PackId("A"),
            activePackLessonIds = listOf("a"),
            lessons = listOf(lesson("a", "A"))
        )
        val next = PackSelectionPlan(PackId("B"), listOf(lesson("b", "B")), listOf("b"))

        val navigation = next.navigationFrom(previous)
        assertEquals(PackId("B"), navigation.activePackId)
        assertEquals(listOf("b"), navigation.activePackLessonIds)
        assertEquals(listOf("b"), next.scheduleLessons().map { it.id.value })
    }

    @Test
    fun sharedLessonIdUsesTargetContentAndFiltersUnlistedLessons() {
        val target = PackSelectionPlan(
            PackId("B"),
            listOf(lesson("shared", "B content"), lesson("other", "Other")),
            listOf("shared")
        )

        assertEquals(listOf("B content"), target.scheduleLessons().flatMap { it.cards }.map { it.promptRu })
    }
}
