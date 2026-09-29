package com.alexpo.grammermate.feature.daily

import android.app.Application
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.data.NavigationState
import com.alexpo.grammermate.data.PackId
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SrsRating
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillComboProgress
import com.alexpo.grammermate.data.VerbDrillStore
import com.alexpo.grammermate.data.WordMasteryState
import com.alexpo.grammermate.data.WordMasteryStore
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.PackDailyCursorStore
import com.alexpo.grammermate.data.StreakStore
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.feature.training.AnswerValidator
import com.alexpo.grammermate.shared.TrainingStateAccess
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyTaskProgressRecorderTest {
    private val mastery = mockk<MasteryStore>(relaxed = true)
    private val verbs = mockk<VerbDrillStore>(relaxed = true)
    private val words = mockk<WordMasteryStore>(relaxed = true)
    private val recorder = DailyTaskProgressRecorder(
        mastery, { verbs }, { words }, nowMs = { 1_000L }, today = { "2026-09-29" }
    )

    @Test
    fun accepted_second_sentence_records_the_answered_card_not_the_first_task() {
        val first = SentenceCard("first", "one", listOf("uno"))
        val second = SentenceCard("second", "two", listOf("due"))
        val stateAccess = mockk<TrainingStateAccess>()
        every { stateAccess.currentState } returns TrainingUiState(
            navigation = NavigationState(activePackId = PackId("pack-a"))
        )
        val coordinator = DailyPracticeCoordinator(
            stateAccess, mockk<Application>(), mockk<AnswerValidator>(),
            mockk<LessonStore>(), mastery, { verbs }, { words },
            mockk<StreakStore>(), mockk<StreakManager>(), mockk<PackDailyCursorStore>()
        )

        coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE, second, { "lesson-2" })

        verify(exactly = 1) { mastery.recordCardShowForPack("pack-a", "lesson-2", second.id) }
        verify(exactly = 0) { mastery.recordCardShowForPack(any(), any(), first.id) }
    }

    @Test
    fun verb_show_preserves_previous_ids_and_pack_scope() {
        val previous = VerbDrillComboProgress(
            group = "are", tense = "Presente", totalCards = 12,
            everShownCardIds = setOf("old"), todayShownCardIds = setOf("old")
        )
        every { verbs.loadProgress() } returns mapOf("are|Presente" to previous)
        val card = VerbDrillCard("new", "prompt", "risposta", tense = "Presente", group = "are")

        recorder.recordVerbShow("pack-a", card)

        verify { verbs.upsertComboProgress("are|Presente", match {
            it.totalCards == 12 && it.everShownCardIds == setOf("old", "new") &&
                it.todayShownCardIds == setOf("old", "new") && it.lastDate == "2026-09-29"
        }) }
    }

    @Test
    fun vocab_rating_preserves_srs_steps_and_counts() {
        val initial = WordMasteryState(wordId = "word", intervalStepIndex = 2, correctCount = 3)
        every { words.getMastery("word") } returns initial

        assertEquals(3, recorder.rateVocab("pack-a", "word", SrsRating.GOOD))
        verify { words.upsertMastery(match {
            it.wordId == "word" && it.intervalStepIndex == 3 && it.correctCount == 4 &&
                it.incorrectCount == 0 && it.lastReviewDateMs == 1_000L && it.isLearned
        }) }
        assertEquals(0, recorder.rateVocab("pack-a", "word", SrsRating.AGAIN))
        verify { words.upsertMastery(match {
            it.intervalStepIndex == 0 && it.correctCount == 3 &&
                it.incorrectCount == 1 && !it.isLearned
        }) }
    }
}
