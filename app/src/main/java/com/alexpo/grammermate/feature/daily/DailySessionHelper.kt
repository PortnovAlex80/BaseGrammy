package com.alexpo.grammermate.feature.daily

import com.alexpo.grammermate.data.DailyBlockType

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
