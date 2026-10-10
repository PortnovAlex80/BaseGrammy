package com.alexpo.grammermate.feature.progress

import com.alexpo.grammermate.data.HiddenCardKeys
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.PackId
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.shared.TrainingStateAccess
import com.alexpo.grammermate.testharness.FakeMasteryStore
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused tests for the two mastery rules owned by [ProgressTracker]:
 * the self-produced ladder rule (which answers advance a lesson's interval
 * ladder) and the pack-scoped hidden-card count excluded from the completion
 * threshold.
 */
class ProgressTrackerMasteryRulesTest {

    private val mastery = FakeMasteryStore()
    private val stateAccess = mockk<TrainingStateAccess>()

    private fun tracker(selectedLessonId: String? = "lesson"): ProgressTracker {
        val base = TrainingUiState()
        every { stateAccess.currentState } returns base.copy(
            navigation = base.navigation.copy(
                activePackId = PackId("pack"),
                selectedLessonId = selectedLessonId?.let(::LessonId)
            )
        )
        return ProgressTracker(
            stateAccess = stateAccess,
            masteryStore = mastery,
            progressStore = mockk(relaxed = true),
            lessonStore = mockk(relaxed = true),
            packDailyCursorStore = mockk(relaxed = true),
            packLessonProgressStore = mockk(relaxed = true)
        )
    }

    private val card = SentenceCard(
        "card-1", "q", listOf("a"), sourcePackId = "pack", sourceLessonId = "lesson"
    )

    private fun earned(
        tracker: ProgressTracker,
        accepted: Boolean = true,
        card: SentenceCard? = this.card,
        inputMode: InputMode = InputMode.KEYBOARD,
        revealed: Boolean = false,
        bossActive: Boolean = false,
        dailyActive: Boolean = false
    ) = tracker.recordSelfProducedIfEarned(
        accepted = accepted,
        card = card,
        inputMode = inputMode,
        answerWasRevealed = revealed,
        bossActive = bossActive,
        dailySessionActive = dailyActive
    )

    @Test
    fun acceptedUnrevealedKeyboardAnswerWritesThePackScopedLadder() {
        // Exposure from another card/lesson first, so effort is non-zero on the ladder write.
        mastery.recordCardShowForPack("pack", "other", "warmup")

        earned(tracker())

        val state = mastery.getForPack("pack", "lesson")
        assertTrue("ladder review must be recorded", state!!.lastReviewMs > 0)
        assertEquals(mastery.totalEffortCardsForPack("pack"), state.effortAtLastReview)
        // No other pack's ladder is touched.
        assertNull(mastery.getForPack("other-pack", "lesson"))
    }

    @Test
    fun rejectedWordBankRevealedBossAndDailyAnswersNeverWriteTheLadder() {
        val t = tracker()

        earned(t, accepted = false)
        earned(t, inputMode = InputMode.WORD_BANK)
        earned(t, revealed = true)
        earned(t, bossActive = true)
        earned(t, dailyActive = true)

        assertNull(mastery.getForPack("pack", "lesson"))
    }

    @Test
    fun cardFromAnotherPackOrWithoutLessonCreditIsIgnored() {
        val foreign = SentenceCard(
            "card-2", "q", listOf("a"), sourcePackId = "other-pack", sourceLessonId = "lesson"
        )
        val noLesson = SentenceCard("card-3", "q", listOf("a"), sourcePackId = "pack", sourceLessonId = null)

        val t = tracker()
        earned(t, card = foreign)
        earned(t, card = noLesson)

        assertNull(mastery.getForPack("pack", "lesson"))
    }

    @Test
    fun hiddenCardCountIsScopedToThePackAndLessonOfTheCards() {
        val hidden = setOf(
            HiddenCardKeys.scoped("pack", "lesson", "card-1"),
            HiddenCardKeys.scoped("pack", "lesson", "card-2"),
            // Same raw card ID under another pack/lesson scope must not match.
            HiddenCardKeys.scoped("pack", "other-lesson", "card-1"),
            HiddenCardKeys.scoped("other-pack", "lesson", "card-1")
        )
        val lessonCards = listOf(
            SentenceCard("card-1", "q", listOf("a"), sourcePackId = "pack", sourceLessonId = "lesson"),
            SentenceCard("card-2", "q", listOf("a"), sourcePackId = "pack", sourceLessonId = "lesson"),
            SentenceCard("card-3", "q", listOf("a"), sourcePackId = "pack", sourceLessonId = "lesson")
        )

        val t = tracker()

        assertEquals(2, t.hiddenCardCountInLesson(lessonCards, "lesson", hidden, "pack"))
        // No pack context: only the card's own pack/lesson source decides.
        assertEquals(2, t.hiddenCardCountInLesson(lessonCards, "lesson", hidden, null))
        assertEquals(0, t.hiddenCardCountInLesson(lessonCards, "lesson", emptySet(), "pack"))
    }
}
