package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.AudioState
import com.alexpo.grammermate.data.BossState
import com.alexpo.grammermate.data.DailyCursorState
import com.alexpo.grammermate.data.DailyPracticeState
import com.alexpo.grammermate.data.FlowerDisplayState
import com.alexpo.grammermate.data.StoryState
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.VocabSprintState
import kotlinx.coroutines.flow.StateFlow

/** Reads authoritative feature flows directly; the combined UI flow may lag them. */
internal class TrainingProgressSnapshotSource(
    private val core: StateFlow<TrainingUiState>,
    private val audio: StateFlow<AudioState>,
    private val story: StateFlow<StoryState>,
    private val vocab: StateFlow<VocabSprintState>,
    private val daily: StateFlow<DailyPracticeState>,
    private val flower: StateFlow<FlowerDisplayState>,
    private val boss: StateFlow<BossState>,
    private val currentCursor: () -> DailyCursorState
) {
    fun capture(): TrainingUiState {
        val state = core.value.copy(
            audio = audio.value,
            story = story.value,
            vocabSprint = vocab.value,
            daily = daily.value,
            flowerDisplay = flower.value,
            boss = boss.value
        )
        val cursor = currentCursor()
        return if (state.daily.dailyCursor != cursor) {
            state.copy(daily = state.daily.copy(dailyCursor = cursor))
        } else {
            state
        }
    }
}
