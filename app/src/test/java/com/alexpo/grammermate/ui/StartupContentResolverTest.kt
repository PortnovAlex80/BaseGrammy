package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.Language
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonPack
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.PackId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartupContentResolverTest {
    private val en = LanguageId("en")
    private val it = LanguageId("it")
    private val languages = listOf(Language(en, "English"), Language(it, "Italian"))
    private val enPack = LessonPack(PackId("EN"), "1", en, 0L)
    private val itPack = LessonPack(PackId("IT"), "1", it, 0L)
    private val packs = listOf(enPack, itPack)
    private val store = mockk<LessonStore>()
    private val resolver = StartupContentResolver(store)

    @Test
    fun zeroStateDoesNotSelectLanguageOrReadContent() {
        val content = resolver.resolve(null, enPack.packId, null, languages, packs)

        assertNull(content.languageId)
        assertNull(content.packId)
        assertNull(content.lessonIds)
        assertEquals(emptyList<Lesson>(), content.lessons)
        verify(exactly = 0) { store.getLessons(any(), any()) }
    }

    @Test
    fun validPackRestoresOnlyItsOwnLessonAndIds() {
        val first = Lesson(LessonId("shared"), en, "First", emptyList())
        val second = Lesson(LessonId("second"), en, "Second", emptyList())
        every { store.getLessons("EN", "en") } returns listOf(first, second)
        every { store.getLessonIdsForPack("EN") } returns listOf("shared", "second")

        val content = resolver.resolve(en, enPack.packId, "second", languages, packs)

        assertEquals(enPack.packId, content.packId)
        assertEquals(listOf("shared", "second"), content.lessonIds)
        assertEquals(second.id, content.selectedLessonId)
        assertEquals(listOf(first, second), content.lessons)
        verify(exactly = 1) { store.getLessons("EN", "en") }
    }

    @Test
    fun deletedOrForeignLanguagePackCannotSupplyLessons() {
        val deleted = resolver.resolve(en, PackId("DELETED"), null, languages, packs)
        val foreign = resolver.resolve(en, itPack.packId, null, languages, packs)

        assertNull(deleted.packId)
        assertNull(foreign.packId)
        assertEquals(en, foreign.languageId)
        assertNull(foreign.selectedLessonId)
        verify(exactly = 0) { store.getLessons(any(), any()) }
        verify(exactly = 0) { store.getLessonIdsForPack(any()) }
    }

    @Test
    fun drillOnlyPackRemainsSelectedWithoutARequestedLesson() {
        every { store.getLessons("EN", "en") } returns emptyList()
        every { store.getLessonIdsForPack("EN") } returns emptyList()

        val content = resolver.resolve(en, enPack.packId, "missing", languages, packs)

        assertEquals(enPack.packId, content.packId)
        assertEquals(emptyList<String>(), content.lessonIds)
        assertNull(content.selectedLessonId)
    }

    @Test
    fun reloadFallsBackToInstalledLanguageButDropsMismatchedPack() {
        val content = resolver.resolve(
            LanguageId("missing"), itPack.packId, null, languages, packs,
            fallbackLanguageId = en
        )

        assertEquals(en, content.languageId)
        assertNull(content.packId)
        verify(exactly = 0) { store.getLessons(any(), any()) }
    }
}
