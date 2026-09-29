package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.LessonPack
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.data.NavigationState
import com.alexpo.grammermate.data.PackId
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackTileSourceTest {
    @Test
    fun tilesKeepPackScopedMasteryAndActivePackIdentity() {
        val language = LanguageId("it")
        val first = LessonPack(PackId("pack-a"), "1", language, 0L, "A Pack")
        val second = LessonPack(PackId("pack-b"), "1", language, 0L, "B Pack")
        val lesson = Lesson(LessonId("same-lesson"), language, "Lesson", emptyList())
        val lessons = mockk<LessonStore>()
        val mastery = mockk<MasteryStore>()
        every { lessons.getLessons(any(), "it") } returns listOf(lesson)
        every { mastery.loadAll() } returns mapOf(
            "pack:pack-a" to mapOf("same-lesson" to LessonMasteryState(
                lesson.id, language, uniqueCardShows = 5
            ))
        )

        val tiles = PackTileSource(lessons, mastery).getTiles(NavigationState(
            installedPacks = listOf(second, first),
            selectedLanguageId = language,
            activePackId = second.packId
        ))

        assertEquals(listOf("pack-a", "pack-b"), tiles.map { it.packId })
        assertEquals(listOf(5, 0), tiles.map { it.totalCards })
        assertFalse(tiles[0].isActive)
        assertTrue(tiles[1].isActive)
        assertEquals("", tiles[1].flowerEmoji)
    }

    @Test
    fun selectedLanguageFiltersOtherPackTiles() {
        val it = LanguageId("it")
        val en = LanguageId("en")
        val lessons = mockk<LessonStore>()
        val mastery = mockk<MasteryStore>()
        every { lessons.getLessons("it-pack", "it") } returns emptyList()
        every { mastery.loadAll() } returns emptyMap()

        val tiles = PackTileSource(lessons, mastery).getTiles(NavigationState(
            installedPacks = listOf(
                LessonPack(PackId("en-pack"), "1", en, 0L),
                LessonPack(PackId("it-pack"), "1", it, 0L)
            ),
            selectedLanguageId = it
        ))

        assertEquals(listOf("it-pack"), tiles.map { it.packId })
    }
}
