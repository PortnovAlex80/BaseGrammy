package com.alexpo.grammermate.feature.daily

import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.PracticeType
import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.StreakStore
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.shared.TrainingStateAccess
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyStreakCompletionTest {
    private class StateAccess : TrainingStateAccess {
        private val state = MutableStateFlow(TrainingUiState())
        val uiState: StateFlow<TrainingUiState> = state
        override val currentState: TrainingUiState get() = state.value
        override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
            state.value = transform(state.value)
        }
        override fun saveProgress() = Unit
    }

    private val state = StateAccess()
    private val store = mockk<StreakStore>()
    private val manager = mockk<StreakManager>()
    private val completion = DailyStreakCompletion(state, store, manager)

    @Test
    fun completed_block_types_update_all_fires_and_publish_the_final_snapshot() {
        val first = StreakData(LanguageId("it"), currentStreak = 2, todayFireCount = 1)
        val last = first.copy(longestStreak = 2, todayFireCount = 2)
        every { store.recordPracticeTypeCompletion("it", PracticeType.TRANSLATION) } returns (first to true)
        every { store.recordPracticeTypeCompletion("it", PracticeType.VOCAB) } returns (last to false)
        every { manager.getCelebrationMessage(2, 2) } returns "two fires"

        assertTrue(completion.record("it", setOf(DailyBlockType.TRANSLATE, DailyBlockType.VOCAB)))

        val card = state.uiState.value.cardSession
        assertEquals(2, card.currentStreak)
        assertEquals(2, card.longestStreak)
        assertEquals(2, card.todayFireCount)
        assertEquals("two fires", card.streakMessage)
        assertEquals(1, card.streakCelebrationToken)
        verify(exactly = 1) { store.recordPracticeTypeCompletion("it", PracticeType.TRANSLATION) }
        verify(exactly = 1) { store.recordPracticeTypeCompletion("it", PracticeType.VOCAB) }
    }

    @Test
    fun existing_fires_refresh_streak_without_celebration() {
        val snapshot = StreakData(LanguageId("it"), currentStreak = 3, longestStreak = 4, todayFireCount = 1)
        every { store.recordPracticeTypeCompletion("it", PracticeType.VERB) } returns (snapshot to false)

        assertFalse(completion.record("it", setOf(DailyBlockType.VERBS)))

        val card = state.uiState.value.cardSession
        assertEquals(3, card.currentStreak)
        assertEquals(4, card.longestStreak)
        assertEquals(0, card.streakCelebrationToken)
        verify(exactly = 0) { manager.getCelebrationMessage(any(), any()) }
    }
}
