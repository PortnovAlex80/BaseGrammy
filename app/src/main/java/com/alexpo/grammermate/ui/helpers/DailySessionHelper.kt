package com.alexpo.grammermate.ui.helpers

import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.DailySessionState
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.ui.TrainingUiState
import kotlinx.coroutines.flow.StateFlow

interface TrainingStateAccess {
    val uiState: StateFlow<TrainingUiState>
    fun updateState(transform: (TrainingUiState) -> TrainingUiState)
    fun saveProgress()
}

class DailySessionHelper(
    private val stateAccess: TrainingStateAccess
) {

    fun startDailySession(tasks: List<DailyTask>, lessonLevel: Int) {
        if (tasks.isEmpty()) return
        stateAccess.updateState { state ->
            state.copy(
                dailySession = DailySessionState(
                    active = true,
                    tasks = tasks,
                    taskIndex = 0,
                    blockIndex = 0,
                    level = lessonLevel,
                    finishedToken = false
                )
            )
        }
        stateAccess.saveProgress()
    }

    fun getCurrentTask(): DailyTask? {
        val ds = stateAccess.uiState.value.dailySession
        if (!ds.active) return null
        return ds.tasks.getOrNull(ds.taskIndex)
    }

    fun getCurrentBlockType(): DailyBlockType? {
        return getCurrentTask()?.blockType
    }

    fun nextTask(): Boolean {
        val ds = stateAccess.uiState.value.dailySession
        if (!ds.active) return false

        val nextIndex = ds.taskIndex + 1
        if (nextIndex >= ds.tasks.size) {
            endSession()
            return false
        }

        val currentBlock = getCurrentBlockType()
        val nextBlock = ds.tasks.getOrNull(nextIndex)?.blockType
        val nextBlockIndex = if (nextBlock != currentBlock) ds.blockIndex + 1 else ds.blockIndex

        stateAccess.updateState {
            it.copy(
                dailySession = it.dailySession.copy(
                    taskIndex = nextIndex,
                    blockIndex = nextBlockIndex
                )
            )
        }
        stateAccess.saveProgress()
        return true
    }

    fun endSession() {
        stateAccess.updateState { state ->
            state.copy(
                dailySession = state.dailySession.copy(
                    active = false,
                    finishedToken = true
                )
            )
        }
        stateAccess.saveProgress()
    }

    fun getBlockProgress(): BlockProgress {
        val ds = stateAccess.uiState.value.dailySession
        if (!ds.active) return BlockProgress.Empty

        val tasks = ds.tasks
        val currentBlock = getCurrentBlockType() ?: return BlockProgress.Empty

        var blockStart = 0
        for (i in tasks.indices) {
            if (tasks[i].blockType == currentBlock) {
                blockStart = i
                break
            }
        }

        var blockEnd = blockStart
        for (i in blockStart until tasks.size) {
            if (tasks[i].blockType != currentBlock) break
            blockEnd = i
        }
        val blockSize = blockEnd - blockStart + 1
        val positionInBlock = ds.taskIndex - blockStart + 1

        return BlockProgress(
            blockType = currentBlock,
            positionInBlock = positionInBlock.coerceIn(1, blockSize),
            blockSize = blockSize,
            totalTasks = tasks.size,
            globalPosition = ds.taskIndex + 1
        )
    }

    fun isSessionComplete(): Boolean {
        val ds = stateAccess.uiState.value.dailySession
        return !ds.active || ds.taskIndex >= ds.tasks.size
    }
}

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
