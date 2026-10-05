package com.alexpo.grammermate.data

import android.app.Application
import com.alexpo.grammermate.feature.progress.ProgressTracker
import com.alexpo.grammermate.shared.TrainingStateAccess
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ProgressPersistenceOwnershipTest {
    private fun tracker(app: Application, lessons: LessonStore) = ProgressTracker(
        stateAccess = mockk<TrainingStateAccess>(relaxed = true),
        masteryStore = mockk(relaxed = true),
        progressStore = ProgressStoreImpl(app), lessonStore = lessons,
        packDailyCursorStore = PackDailyCursorStoreImpl(app),
        packLessonProgressStore = PackLessonProgressStoreImpl(app)
    )

    @Test fun linearReviewAndExitStateCannotOverwriteTheSelectedRegularLessonCheckpoint() {
        val app: Application = RuntimeEnvironment.getApplication()
        val store = PackLessonProgressStoreImpl(app)
        val saved = PackLessonProgressState.LessonProgress(currentIndex = 3, correctCount = 7, activeTimeMs = 900, activeSubLessonIndex = 1)
        store.savePackProgress(PackLessonProgressState("checkpoint", mapOf("lesson" to saved)))
        val tracker = tracker(app, mockk(relaxed = true))
        val regular = TrainingUiState().let { base -> base.copy(
            navigation = base.navigation.copy(activePackId = PackId("checkpoint"), selectedLessonId = LessonId("lesson"), selectedLanguageId = LanguageId("en"), mode = TrainingMode.LESSON),
            cardSession = base.cardSession.copy(currentCard = SentenceCard("card", "q", listOf("a")), currentIndex = 1, activeSubLessonIndex = 2, correctCount = 9)
        ) }
        val others = listOf(
            regular.copy(cardSession = regular.cardSession.copy(screenMode = TrainingScreenMode.VERB_DRILL)),
            regular.copy(cardSession = regular.cardSession.copy(isReviewMode = true)),
            regular.copy(cardSession = regular.cardSession.copy(currentCard = null, currentIndex = 0, correctCount = 0)),
            regular.copy(elite = regular.elite.copy(eliteActive = true)),
            regular.copy(cardSession = regular.cardSession.copy(currentCard = SentenceCard("card", "q", listOf("a"), sourcePackId = "other", sourceLessonId = "lesson"))),
            regular.copy(navigation = regular.navigation.copy(mode = TrainingMode.ALL_MIXED))
        )
        others.forEach { state ->
            tracker.saveProgress(state, false, emptyList())
            assertEquals(saved, PackLessonProgressStoreImpl(app).loadPackProgress("checkpoint")!!.lessonProgress["lesson"])
        }
        tracker.saveProgress(regular, false, emptyList())
        val actual = PackLessonProgressStoreImpl(app).loadPackProgress("checkpoint")!!.lessonProgress["lesson"]!!
        assertEquals(9, actual.correctCount)
        assertEquals(2, actual.activeSubLessonIndex)
        assertEquals("lesson", ProgressStoreImpl(app).load().lessonId)
    }

    @Test fun packResetInvalidatesHeldDrillStoresAndPreservesTheOtherPackThenGlobalResetClearsBoth() {
        val app: Application = RuntimeEnvironment.getApplication()
        val factory = StoreFactory.getInstance(app)
        val lessons = mockk<LessonStore>()
        every { lessons.getInstalledPacks() } returns listOf("resetA", "resetB").map {
            LessonPack(PackId(it), "1", LanguageId("en"), 0L)
        }
        val tracker = tracker(app, lessons)
        val wordsA = factory.getWordMasteryStore("resetA")
        val verbsA = factory.getVerbDrillStore("resetA")
        val auxA = factory.getAuxDrillStore("resetA")
        val vocabA = factory.getVocabProgressStore("resetA")
        val auxB = factory.getAuxDrillStore("resetB")
        wordsA.upsertMastery(WordMasteryState("word", correctCount = 2))
        verbsA.upsertComboProgress("combo", VerbDrillComboProgress("g", "t", totalCards = 2))
        auxA.upsertComboProgress("combo", AuxDrillComboProgress("v", "t", totalCards = 2))
        vocabA.addCompletedIndex("lesson", "en", 2)
        auxB.upsertComboProgress("combo", AuxDrillComboProgress("v", "t", totalCards = 5))
        tracker.resetDrillFilesForPack(app, "resetA")
        factory.evict("resetA")
        assertTrue(wordsA.loadAll().isEmpty())
        assertTrue(verbsA.loadProgress().isEmpty())
        assertTrue(auxA.loadProgress().isEmpty())
        assertTrue(vocabA.get("lesson", "en").completedIndices.isEmpty())
        assertEquals(5, auxB.loadProgress()["combo"]!!.totalCards)
        tracker.resetDrillFiles(app)
        factory.clearCache()
        assertTrue(auxB.loadProgress().isEmpty())
    }
}
