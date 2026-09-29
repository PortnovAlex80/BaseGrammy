package com.alexpo.grammermate.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.data.BadSentenceStore
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.ProgressStore
import com.alexpo.grammermate.data.VerbDrillStore
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class VerbDrillCardCatalogLoaderTest {
    @get:Rule val temp = TemporaryFolder()

    private val lessons = mockk<LessonStore>()
    private val progressStore = mockk<ProgressStore>()
    private val badSentences = mockk<BadSentenceStore>()
    private val drillStore = mockk<VerbDrillStore>()
    private val tenseInfo = VerbTenseInfoRepository(RuntimeEnvironment.getApplication().assets)
    private val loader = VerbDrillCardCatalogLoader(lessons, progressStore, badSentences, tenseInfo)

    @Test
    fun pack_catalog_keeps_source_mapping_facets_progress_and_bad_count() {
        val csv = file("it_lesson_1.csv", "are", "Io parlo")
        every { lessons.getVerbDrillFiles("pack-1", "it") } returns listOf(csv)
        every { drillStore.loadProgress() } returns emptyMap()
        every { badSentences.getBadSentenceCount("pack-1") } returns 3

        val snapshot = loader.load("it", "pack-1", drillStore)

        assertEquals(listOf("are_Presente_0"), snapshot.cards.map { it.id })
        assertEquals(mapOf("are_Presente_0" to "pack-1"), snapshot.cardToPack)
        assertEquals(setOf("pack-1"), snapshot.packIds)
        assertEquals(listOf("Presente"), snapshot.tenses)
        assertEquals(listOf("are"), snapshot.groups)
        assertEquals(listOf("Io"), snapshot.persons)
        assertEquals(3, snapshot.badCount)
        assertEquals("it", snapshot.lang)
        assertTrue(snapshot.tenseInfo.isNotEmpty())
    }

    @Test
    fun global_catalog_resolves_each_file_to_its_pack_and_skips_invalid_csv() {
        val first = file("it_lesson_1.csv", "are", "Io parlo")
        val second = file("it_lesson_2.csv", "ere", "Tu vedi")
        val invalid = temp.newFile("it_broken.csv").apply { writeText("Title\ninvalid\nrow") }
        @Suppress("DEPRECATION")
        every { lessons.getVerbDrillFiles("it") } returns listOf(first, invalid, second)
        every { lessons.getPackIdForLesson("lesson_1") } returns "pack-1"
        every { lessons.getPackIdForLesson("lesson_2") } returns null
        every { drillStore.loadProgress() } returns emptyMap()
        every { badSentences.getBadSentenceCount("pack-1") } returns 1
        every { badSentences.getBadSentenceCount("lesson_2") } returns 2

        val snapshot = loader.load("it", null, drillStore)

        assertEquals(listOf("are_Presente_0", "ere_Presente_0"), snapshot.cards.map { it.id })
        assertEquals("pack-1", snapshot.cardToPack["are_Presente_0"])
        assertEquals("lesson_2", snapshot.cardToPack["ere_Presente_0"])
        assertEquals(setOf("pack-1", "lesson_2"), snapshot.packIds)
        assertEquals(listOf("Io", "Tu"), snapshot.persons)
        assertEquals(3, snapshot.badCount)
    }

    private fun file(name: String, group: String, answer: String) = temp.newFile(name).apply {
        writeText("Verb Drill\nRU;IT;Verb;Tense;Group;Rank\nprompt;$answer;verb;Presente;$group;1")
    }
}
