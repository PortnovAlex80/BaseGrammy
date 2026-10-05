package com.alexpo.grammermate.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexpo.grammermate.data.BadSentenceStore
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.ProgressStore
import com.alexpo.grammermate.data.VerbDrillStore
import io.mockk.every
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
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
    fun no_pack_has_no_drill_content_or_cross_pack_catalog_reads() {
        every { drillStore.loadProgress() } returns emptyMap()

        val snapshot = loader.load("it", null, drillStore)

        assertTrue(snapshot.cards.isEmpty())
        assertTrue(snapshot.cardToPack.isEmpty())
        assertTrue(snapshot.packIds.isEmpty())
        assertEquals(0, snapshot.badCount)
        verify { lessons wasNot Called }
        verify { badSentences wasNot Called }
    }

    private fun file(name: String, group: String, answer: String) = temp.newFile(name).apply {
        writeText("Verb Drill\nRU;IT;Verb;Tense;Group;Rank\nprompt;$answer;verb;Presente;$group;1")
    }
}
