package com.alexpo.grammermate.ui.helpers

import com.alexpo.grammermate.data.FlowerCalculator
import com.alexpo.grammermate.data.FlowerState
import com.alexpo.grammermate.data.FlowerVisual
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.SpacedRepetitionConfig

/**
 * Higher-level wrapper around [FlowerCalculator] and [SpacedRepetitionConfig]
 * that produces UI-ready flower state and lesson tile data.
 *
 * Pure Kotlin class with no Android dependencies.
 */
class FlowerProgressRenderer {

    /**
     * Compute the visual flower state for a lesson.
     *
     * Wraps [FlowerCalculator.calculate], exposing parameters individually
     * so callers do not need to assemble a [LessonMasteryState] just to
     * query the flower state.
     *
     * @param mastery        number of unique cards practiced
     * @param totalCards     total cards in the lesson (unused by FlowerCalculator
     *                       but kept for API completeness)
     * @param lastPracticeDate epoch millis of the last practice session, or null
     * @param intervalStep   current step on the spaced-repetition ladder
     * @return visual representation of the flower
     */
    fun computeFlowerState(
        mastery: Int,
        totalCards: Int,
        lastPracticeDate: Long?,
        intervalStep: Int
    ): FlowerVisual {
        val state = if (mastery <= 0) {
            null
        } else {
            LessonMasteryState(
                lessonId = "",
                languageId = "",
                uniqueCardShows = mastery,
                totalCardShows = mastery,
                lastShowDateMs = lastPracticeDate ?: 0L,
                intervalStepIndex = intervalStep
            )
        }
        return FlowerCalculator.calculate(state, totalCards)
    }

    /**
     * Compute the health percentage for a flower.
     *
     * Wraps [SpacedRepetitionConfig.calculateHealthPercent].
     *
     * @param lastPracticeDate epoch millis of the last practice session, or null
     * @param intervalStep     current step on the spaced-repetition ladder
     * @return health in the range [WILTED_THRESHOLD .. 1.0]
     */
    fun computeHealth(lastPracticeDate: Long?, intervalStep: Int): Double {
        if (lastPracticeDate == null || lastPracticeDate <= 0L) return 1.0
        val daysSince = daysSince(lastPracticeDate)
        return SpacedRepetitionConfig.calculateHealthPercent(daysSince, intervalStep).toDouble()
    }

    /**
     * Build a UI-ready tile state for a single lesson.
     *
     * Combines flower visual data with mastery information into a
     * [LessonTileInfo] that can be consumed directly by the home screen.
     *
     * @param lesson       the lesson to render
     * @param masteryState current mastery state for the lesson (null = not started)
     * @return tile info with flower state, emoji, and mastery metrics
     */
    fun buildLessonTileState(
        lesson: Lesson,
        masteryState: LessonMasteryState?
    ): LessonTileInfo {
        val flower = FlowerCalculator.calculate(masteryState, lesson.cards.size)
        return LessonTileInfo(
            lessonId = lesson.id,
            title = lesson.title,
            flower = flower,
            emoji = flowerEmoji(flower.state),
            masteryCount = masteryState?.uniqueCardShows ?: 0,
            shownCardCount = masteryState?.shownCardIds?.size ?: 0,
            isCompleted = masteryState?.completedAtMs != null
        )
    }

    /**
     * Build UI-ready tile states for a list of lessons.
     *
     * @param lessons    all lessons for the current language
     * @param masteryMap per-lesson mastery states keyed by lesson ID
     * @return list of tile infos in the same order as [lessons]
     */
    fun buildLessonTiles(
        lessons: List<Lesson>,
        masteryMap: Map<String, LessonMasteryState>
    ): List<LessonTileInfo> {
        return lessons.map { lesson ->
            buildLessonTileState(lesson, masteryMap[lesson.id])
        }
    }

    /**
     * Map a [FlowerState] to its display emoji.
     */
    fun flowerEmoji(state: FlowerState): String {
        return FlowerCalculator.getEmoji(state)
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private fun daysSince(timestampMs: Long): Int {
        if (timestampMs <= 0L) return 0
        val diffMs = System.currentTimeMillis() - timestampMs
        return (diffMs / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
    }
}

/**
 * UI-ready summary of a single lesson tile for the home screen.
 *
 * Produced by [FlowerProgressRenderer.buildLessonTileState].
 */
data class LessonTileInfo(
    val lessonId: String,
    val title: String,
    val flower: FlowerVisual,
    val emoji: String,
    val masteryCount: Int,
    val shownCardCount: Int,
    val isCompleted: Boolean
)
