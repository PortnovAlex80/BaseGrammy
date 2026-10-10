package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.data.VocabWord
import com.alexpo.grammermate.data.WordMasteryStore
import com.alexpo.grammermate.data.WordMasteryState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Direct tests of the profile-stats projection owner: pack-scoped card counts,
 * learned-word count and the CEFR rank derivation (see CefrCalculator: empty
 * ranks -> "—", fewer than 10 -> "A1", otherwise the learned-rank median maps
 * to A1/A2/B1/B2/C1/C2 at 500/1000/2000/4000/8000).
 */
class ProfileStatsSourceTest {

    private fun source(
        lessons: LessonStore,
        mastery: MasteryStore,
        learnedWordIds: Set<String>,
        masteredCount: Int,
        holder: Array<WordMasteryStore> = arrayOf(mockk(relaxed = true))
    ): ProfileStatsSource {
        val words = mockk<WordMasteryStore>()
        every { words.getMasteredCount() } returns masteredCount
        every { words.loadAll() } returns learnedWordIds.associateWith {
            WordMasteryState(wordId = it, isLearned = true)
        }
        holder[0] = words
        return ProfileStatsSource(
            scope = TestScope(),
            lessonStore = lessons,
            masteryStore = mastery,
            wordMasteryStoreProvider = { holder[0] }
        )
    }

    private fun vocabWords(vararg ranks: Int): List<VocabWord> =
        ranks.mapIndexed { i, rank -> VocabWord(id = "w$i", word = "w$i", pos = "nouns", rank = rank) }

    @Test
    fun cardsCompletedSumsUniqueCardShowsForTheSelectedPackOnly() {
        val lessons = mockk<LessonStore>()
        every { lessons.getVocabWordsByRankRange("pack-a", "it", 0, Int.MAX_VALUE) } returns emptyList()
        val mastery = mockk<MasteryStore>()
        val italian = LanguageId("it")
        every { mastery.loadAll() } returns mapOf(
            "pack:pack-a" to mapOf(
                "l1" to LessonMasteryState(LessonId("l1"), italian, uniqueCardShows = 3),
                "l2" to LessonMasteryState(LessonId("l2"), italian, uniqueCardShows = 4)
            ),
            "pack:pack-b" to mapOf(
                "l1" to LessonMasteryState(LessonId("l1"), italian, uniqueCardShows = 100)
            )
        )

        val stats = source(lessons, mastery, learnedWordIds = emptySet(), masteredCount = 0)
            .compute("it", "pack-a")

        assertEquals(7, stats.cardsCompleted)
        assertEquals(0, stats.wordsLearned)
    }

    @Test
    fun wordsLearnedComesFromTheCurrentlyBoundPackScopedWordMasteryStore() {
        val lessons = mockk<LessonStore>()
        every { lessons.getVocabWordsByRankRange(any(), any(), any(), any()) } returns emptyList()
        val mastery = mockk<MasteryStore>()
        every { mastery.loadAll() } returns emptyMap()
        val holder = arrayOf<WordMasteryStore>(mockk(relaxed = true))
        val statsSource = source(lessons, mastery, emptySet(), masteredCount = 5, holder = holder)
        assertEquals(5, statsSource.compute("it", "pack-a").wordsLearned)

        // Rebinding the provider (pack switch) is picked up by the next compute.
        val rebound = mockk<WordMasteryStore>()
        every { rebound.getMasteredCount() } returns 9
        every { rebound.loadAll() } returns emptyMap()
        holder[0] = rebound
        assertEquals(9, statsSource.compute("it", "pack-b").wordsLearned)
    }

    @Test
    fun cefrLevelUsesMedianRankOfLearnedVocabWordsOnly() {
        // 12 learned words, sorted median index 5/6 -> mean of ranks 600 and 800 = 700 -> "A2".
        val learned = vocabWords(
            100, 200, 300, 400, 500, 600, 800, 900, 1200, 1500, 3000, 7000
        )
        // Unlearned high-rank outlier words must not drag the median to C1/C2.
        val unlearnedOutlier = vocabWords(50000)
        val lessons = mockk<LessonStore>()
        every { lessons.getVocabWordsByRankRange("pack-a", "it", 0, Int.MAX_VALUE) } returns learned + unlearnedOutlier
        val mastery = mockk<MasteryStore>()
        every { mastery.loadAll() } returns emptyMap()

        val stats = source(
            lessons, mastery,
            learnedWordIds = learned.map { it.id }.toSet(),
            masteredCount = 12
        ).compute("it", "pack-a")

        assertEquals("A2", stats.cefrLevel)
        assertEquals(12, stats.wordsLearned)
    }

    @Test
    fun cefrLevelStaysAtA1WhileFewerThanTenWordsAreLearned() {
        val words = vocabWords(1500, 1600, 1700, 1800) // high ranks, but only 4 learned
        val lessons = mockk<LessonStore>()
        every { lessons.getVocabWordsByRankRange("pack-a", "it", 0, Int.MAX_VALUE) } returns words
        val mastery = mockk<MasteryStore>()
        every { mastery.loadAll() } returns emptyMap()

        val stats = source(
            lessons, mastery,
            learnedWordIds = words.map { it.id }.toSet(),
            masteredCount = 4
        ).compute("it", "pack-a")

        assertEquals("A1", stats.cefrLevel)
    }

    @Test
    fun emptySelectionYieldsZeroedStats() {
        val stats = source(mockk(relaxed = true), mockk(relaxed = true), emptySet(), 0)
            .compute(languageId = null, packId = null)

        assertEquals(ProfileStats(0, 0, ""), stats)
    }
}
