package com.alexpo.grammermate.feature.daily

import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.TrainingUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface providing read/write access to shared training state.
 * Implemented by TrainingViewModel via an anonymous object and passed to
 * helper modules so they can read and update [TrainingUiState].
 */
interface TrainingStateAccess {
    val uiState: StateFlow<TrainingUiState>
    fun updateState(transform: (TrainingUiState) -> TrainingUiState)
    fun saveProgress()
}

/**
 * Progress information for the current block within a daily practice session.
 * Used by DailyPracticeScreen and DailyPracticeCoordinator.
 */
data class BlockProgress(
    val blockType: DailyBlockType,
    val positionInBlock: Int,
    val blockSize: Int,
    val totalTasks: Int,
    val globalPosition: Int
) {
    companion object {
        val Empty = BlockProgress(
            blockType = DailyBlockType.TRANSLATE,
            positionInBlock = 0,
            blockSize = 0,
            totalTasks = 0,
            globalPosition = 0
        )
    }
}
