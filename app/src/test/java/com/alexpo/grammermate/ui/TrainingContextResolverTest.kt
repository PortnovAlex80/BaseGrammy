package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Chapter
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonPack
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.NavigationState
import com.alexpo.grammermate.data.PackId
import com.alexpo.grammermate.data.SentenceCard
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingContextResolverTest {
    private val it = LanguageId("it")
    private val store = mockk<LessonStore>()
    private val resolver = TrainingContextResolver(store)
    private val packs = listOf(
        LessonPack(PackId("A"), "1", it, 0L),
        LessonPack(PackId("B"), "1", it, 0L),
        LessonPack(PackId("EMPTY"), "1", it, 0L)
    )

    private fun lesson(id: String, prompt: String) = Lesson(
        LessonId(id), it, id,
        listOf(SentenceCard("card", prompt, listOf(prompt)))
    )

    private fun pack(id: String, ids: List<String>, lessons: List<Lesson>) {
        every { store.getInstalledPacks() } returns packs
        every { store.getLessonIdsForPack(id) } returns ids
        every { store.getLessons(id, "it") } returns lessons
    }

    @Test
    fun sharedLessonIdUsesTargetPackContentAndAtomicNavigation() {
        val old = lesson("shared", "A content")
        val replacement = lesson("shared", "B content")
        pack("B", listOf("shared"), listOf(replacement))

        val target = resolver.forPack("B", LessonId("shared"))!!
        val navigation = target.navigationFrom(
            NavigationState(
                selectedLanguageId = it,
                activePackId = PackId("A"),
                activePackLessonIds = listOf("shared"),
                selectedLessonId = old.id,
                lessons = listOf(old)
            )
        )

        assertEquals(PackId("B"), navigation.activePackId)
        assertEquals(listOf("shared"), navigation.activePackLessonIds)
        assertEquals(replacement, navigation.lessons.single())
        assertEquals("B content", (target.scheduleLessons().single().cards.single() as SentenceCard).promptRu)
    }

    @Test
    fun explicitPackWinsOverAmbiguousLessonLookup() {
        val replacement = lesson("shared", "B content")
        pack("B", listOf("shared"), listOf(replacement))

        val target = resolver.forLesson(
            "shared", "B",
            NavigationState(activePackId = PackId("A"), activePackLessonIds = listOf("shared"))
        )!!

        assertEquals(PackId("B"), target.packId)
        assertEquals(replacement, target.lessons.single())
    }

    @Test
    fun disjointPackSelectsItsFirstExistingLesson() {
        pack("B", listOf("missing", "onlyB"), listOf(lesson("onlyB", "B")))

        val target = resolver.forPack("B", LessonId("onlyA"))!!

        assertEquals(LessonId("onlyB"), target.selectedLessonId)
        assertEquals(listOf("onlyB"), target.scheduleLessons().map { it.id.value })
    }

    @Test
    fun drillOnlyPackAndClearCannotRetainPreviousLesson() {
        pack("EMPTY", emptyList(), emptyList())
        val drill = resolver.forPack("EMPTY")!!
        val navigation = drill.navigationFrom(
            NavigationState(activePackId = PackId("A"), lessons = listOf(lesson("old", "A")))
        )

        assertNull(navigation.selectedLessonId)
        assertEquals(emptyList<Lesson>(), navigation.lessons)
        assertEquals(PackId("EMPTY"), navigation.activePackId)
        val cleared = resolver.withoutPack(navigation).navigationFrom(navigation)
        assertNull(cleared.activePackId)
        assertEquals(emptyList<Lesson>(), cleared.lessons)
        assertEquals(emptyList<String>(), cleared.activePackLessonIds)
    }

    @Test
    fun unavailablePackDoesNotChangeSelection() {
        every { store.getInstalledPacks() } returns packs
        assertNull(resolver.forPack("REMOVED"))
    }

    @Test
    fun chapterSelectionSurvivesWithinPackButClearsAcrossPackBoundary() {
        pack("B", listOf("shared"), listOf(lesson("shared", "B")))
        val chapter = Chapter("old", 1, "Old chapter", lessons = listOf("shared"))
        val oldNavigation = NavigationState(
            activePackId = PackId("A"),
            selectedChapter = chapter
        )

        val switched = resolver.forPack("B")!!.navigationFrom(oldNavigation)
        assertNull(switched.selectedChapter)
        val samePack = resolver.forLesson("shared", "B", switched.copy(selectedChapter = chapter))!!
        assertEquals(chapter, samePack.navigationFrom(switched.copy(selectedChapter = chapter)).selectedChapter)
    }
}
