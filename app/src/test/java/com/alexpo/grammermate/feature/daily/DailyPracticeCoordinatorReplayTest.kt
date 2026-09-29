package com.alexpo.grammermate.feature.daily

import android.app.Application
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.DailyBlock
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.NavigationState
import com.alexpo.grammermate.data.PackDailyCursorState
import com.alexpo.grammermate.data.PackDailyCursorStore
import com.alexpo.grammermate.data.PackId
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.VerbDrillStore
import com.alexpo.grammermate.data.WordMasteryStore
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.feature.training.AnswerValidator
import com.alexpo.grammermate.testharness.FakeStreakStore
import com.alexpo.grammermate.testharness.FakeTrainingStateAccess
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class DailyPracticeCoordinatorReplayTest {
    private val lessons = mockk<LessonStore>()
    private val cursorStore = mockk<PackDailyCursorStore>(relaxed = true)
    private val stateAccess = FakeTrainingStateAccess(TrainingUiState(navigation = NavigationState(
        activePackId = PackId("pack"), selectedLanguageId = LanguageId("it"), selectedLessonId = LessonId("l3")
    )))
    private var cursor = PackDailyCursorState.forPack("pack").copy(currentLessonIndex = 1)
    private val packLessons = listOf("l1", "l2", "l3").map { id ->
        val cards = listOf(SentenceCard("$id-card", id, listOf("answer"))) +
            if (id == "l2") listOf(SentenceCard("l2-card-2", id, listOf("answer"))) else emptyList()
        Lesson(LessonId(id), LanguageId("it"), id, cards)
    }

    private fun coordinator(sessionSize: Int = 1): DailyPracticeCoordinator {
        every { cursorStore.loadPackCursor("pack") } answers { cursor }
        every { lessons.getLessonIdsForPack("pack") } returns listOf("l1", "l2", "l3")
        every { lessons.getLessons("pack", "it") } returns packLessons
        every { lessons.getCumulativeTenses("pack", any()) } returns emptyList()
        every { lessons.getVocabDrillFiles("pack", "it") } returns emptyList()
        every { lessons.getVerbDrillFiles("pack", "it") } returns emptyList()
        return DailyPracticeCoordinator(
            stateAccess = stateAccess,
            appContext = mockk<Application>(),
            answerValidator = AnswerValidator(),
            lessonStore = lessons,
            masteryStore = mockk(relaxed = true),
            verbDrillStoreFactory = { mockk<VerbDrillStore>(relaxed = true) },
            wordMasteryStoreFactory = { mockk<WordMasteryStore>(relaxed = true) },
            streakStore = FakeStreakStore(),
            streakManager = StreakManager(FakeStreakStore()),
            packDailyCursorStore = cursorStore,
            sessionSize = sessionSize
        )
    }

    @Test
    fun firstSessionPersistsCursorLessonAndRestartRepeatsItsCardsAfterCursorAdvances() = runBlocking {
        val first = coordinator()
        var savedLesson = ""
        assertTrue(first.startDailyPractice(
            resolveProgressLessonInfo = { "l3" to 3 },
            onStoreFirstSessionCardIds = { lessonId, sentenceIds, verbIds ->
                savedLesson = lessonId
                cursor = cursor.copy(
                    firstSessionDate = LocalDate.now().toString(),
                    firstSessionLessonId = lessonId,
                    firstSessionSentenceCardIds = sentenceIds,
                    firstSessionVerbCardIds = verbIds
                )
            }
        ))
        assertEquals("l2", savedLesson)
        assertEquals(listOf("l2-card"), cursor.firstSessionSentenceCardIds)
        assertEquals(2, first.getDailyState().dailySession.level)

        // Simulate an older cursor: the ViewModel used to persist the browsed l3.
        cursor = cursor.copy(currentLessonIndex = 2, firstSessionLessonId = "l3")
        val restarted = coordinator()
        assertTrue(restarted.hasResumableDailySession())
        assertTrue(restarted.repeatDailyPractice(3) { "l3" to 3 })
        val replay = restarted.getDailyState().dailySession
        assertEquals(2, replay.level)
        assertEquals(listOf("l2-card"), replay.blocks.first { it.type == DailyBlockType.TRANSLATE }
            .tasks.map { (it as com.alexpo.grammermate.data.DailyTask.TranslateSentence).card.id })
    }

    @Test
    fun repeatCurrentBlockRestartsTheSameTasks() {
        val coordinator = coordinator()
        val task = DailyTask.TranslateSentence("sent-l2", packLessons[1].cards.first(), InputMode.KEYBOARD)
        coordinator.startDailySession(listOf(DailyBlock(
            DailyBlockType.TRANSLATE, listOf(task), taskIndex = 1, isComplete = true
        )), 2, "pack")

        assertTrue(coordinator.repeatDailyBlock())
        val repeated = coordinator.getDailyState().dailySession.currentBlock!!
        assertEquals(listOf(task), repeated.tasks)
        assertEquals(0, repeated.taskIndex)
        assertEquals(false, repeated.isComplete)
    }

    @Test
    fun restartReplaysAllStoredCardsWhenSessionSizeShrinks() = runBlocking {
        cursor = cursor.copy(
            firstSessionDate = LocalDate.now().toString(),
            firstSessionLessonId = "l2",
            firstSessionSentenceCardIds = listOf("l2-card", "l2-card-2")
        )

        val restarted = coordinator(sessionSize = 1)
        assertTrue(restarted.repeatDailyPractice(3) { "l3" to 3 })
        val replay = restarted.getDailyState().dailySession
        assertEquals(listOf("l2-card", "l2-card-2"), replay.blocks
            .first { it.type == DailyBlockType.TRANSLATE }
            .tasks.map { (it as DailyTask.TranslateSentence).card.id })
    }

    @Test
    fun staleStoredIdsAreNotAdvertisedAndFreshFallbackUsesCursorLesson() = runBlocking {
        cursor = cursor.copy(
            firstSessionDate = LocalDate.now().toString(),
            firstSessionLessonId = "l1",
            firstSessionSentenceCardIds = listOf("missing-card")
        )
        val coordinator = coordinator()

        assertFalse(coordinator.hasResumableDailySession())
        assertTrue(coordinator.repeatDailyPractice(3) { "l3" to 3 })
        val fallback = coordinator.getDailyState().dailySession
        assertEquals(2, fallback.level)
        assertEquals("l2-card", (fallback.blocks.first { it.type == DailyBlockType.TRANSLATE }
            .tasks.first() as DailyTask.TranslateSentence).card.id)
    }
}
