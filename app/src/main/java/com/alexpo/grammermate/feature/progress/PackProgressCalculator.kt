package com.alexpo.grammermate.feature.progress

import android.util.Log
import com.alexpo.grammermate.data.FlowerState
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.SpacedRepetitionConfig
import kotlin.math.max

/**
 * Visual representation of a pack's flower state.
 *
 * Aggregates lesson-level mastery and health into pack-level metrics
 * used for the pack selection tile flower.
 *
 * @property flowerState The overall flower state (SEED/SPROUT/BLOOM/WILTING/WILTED/GONE)
 * @property depth Average mastery across started lessons, 0-1 (uniqueCardShows / 150)
 * @property healthPercent Health from Ebbinghaus curve, 0-1
 * @property scaleMultiplier depth * health clamped to [0.5, 1.0]
 * @property completedLessons Count of lessons where completedAtMs != null
 * @property totalLessons Total number of lessons in the pack
 */
data class PackFlowerVisual(
    val flowerState: FlowerState,
    val depth: Float,
    val healthPercent: Float,
    val scaleMultiplier: Float,
    val completedLessons: Int,
    val totalLessons: Int
)

/**
 * Calculator for pack-level flower state from lesson mastery data.
 *
 * Follows the same pattern as [ChapterProgressCalculator]: takes a list of lesson IDs
 * and a map of mastery states, computes aggregated metrics for pack selection tiles.
 *
 * Computation rules:
 * - depth: average of (uniqueCardShows / MASTERY_THRESHOLD) across started lessons
 * - health: [SpacedRepetitionConfig.calculateHealthPercent] using the worst-case
 *   days since last show and average interval step across started lessons
 * - scale: depth * health clamped to [0.5f, 1.0f]
 * - Flower state: derived from depth thresholds and health thresholds
 *
 * Edge cases:
 * - Empty pack (no lessons): Returns SEED with zero metrics
 * - No started lessons: depth = 0, health = 1.0, state = SEED
 * - All lessons completed: depth based on mastery depth, not completion
 */
object PackProgressCalculator {

    private const val TAG = "PackProgressCalc"

    /**
     * Calculate pack-level flower visual from lesson mastery states.
     *
     * @param lessonIds All lesson IDs belonging to the pack
     * @param masteryStates Map of lessonId to [LessonMasteryState] for all lessons in the pack
     * @return [PackFlowerVisual] with aggregated metrics
     */
    fun calculatePackFlower(
        lessonIds: List<String>,
        masteryStates: Map<String, LessonMasteryState>
    ): PackFlowerVisual {
        if (lessonIds.isEmpty()) {
            Log.d(TAG, "calculate: empty pack, returning SEED defaults")
            return PackFlowerVisual(
                flowerState = FlowerState.SEED,
                depth = 0f,
                healthPercent = 1.0f,
                scaleMultiplier = 0.5f,
                completedLessons = 0,
                totalLessons = 0
            )
        }

        var startedLessonCount = 0
        var depthSum = 0.0
        var completedLessons = 0
        var maxLastShowDateMs = 0L
        var intervalStepSum = 0

        for (lessonId in lessonIds) {
            val mastery = masteryStates[lessonId]

            // Count completed lessons (explicit completion marker)
            if (mastery?.completedAtMs != null) {
                completedLessons++
            }

            // Only started lessons contribute to depth and health
            if (mastery != null && mastery.uniqueCardShows > 0) {
                startedLessonCount++
                val lessonDepth = mastery.uniqueCardShows.toFloat() /
                        SpacedRepetitionConfig.MASTERY_THRESHOLD
                depthSum += lessonDepth.coerceAtMost(1.0f)
                intervalStepSum += mastery.intervalStepIndex

                if (mastery.lastShowDateMs > maxLastShowDateMs) {
                    maxLastShowDateMs = mastery.lastShowDateMs
                }
            }
        }

        val totalLessons = lessonIds.size

        // No lessons started: seed state with full health
        if (startedLessonCount == 0) {
            Log.d(TAG, "calculate: no started lessons, returning SEED")
            return PackFlowerVisual(
                flowerState = FlowerState.SEED,
                depth = 0f,
                healthPercent = 1.0f,
                scaleMultiplier = 0.5f,
                completedLessons = completedLessons,
                totalLessons = totalLessons
            )
        }

        // Depth: average mastery across started lessons
        val depth = (depthSum / startedLessonCount).toFloat().coerceIn(0f, 1f)

        // Health: Ebbinghaus curve based on worst-case recency and average interval step
        val daysSinceLastShow = daysSince(maxLastShowDateMs)
        val avgIntervalStep = intervalStepSum.toFloat() / startedLessonCount
        val healthPercent = SpacedRepetitionConfig.calculateHealthPercent(
            daysSinceLastShow,
            avgIntervalStep.toInt()
        )

        // Scale: depth * health clamped to [0.5, 1.0]
        val scaleMultiplier = (depth * healthPercent).coerceIn(0.5f, 1.0f)

        // Determine flower state
        val flowerState = determineFlowerState(depth, healthPercent, daysSinceLastShow)

        Log.d(
            TAG, "calculate: depth=$depth health=$healthPercent scale=$scaleMultiplier " +
                    "state=$flowerState started=$startedLessonCount " +
                    "completed=$completedLessons total=$totalLessons"
        )

        return PackFlowerVisual(
            flowerState = flowerState,
            depth = depth,
            healthPercent = healthPercent,
            scaleMultiplier = scaleMultiplier,
            completedLessons = completedLessons,
            totalLessons = totalLessons
        )
    }

    /**
     * Determine the flower state from depth, health, and recency.
     *
     * Priority order (first match wins):
     * 1. GONE — no show in over [SpacedRepetitionConfig.GONE_THRESHOLD_DAYS] days
     * 2. WILTED — health at or below [SpacedRepetitionConfig.WILTED_THRESHOLD]
     * 3. WILTING — health below 1.0 but above wilted threshold
     * 4. SEED — depth below 0.33
     * 5. SPROUT — depth below 0.66
     * 6. BLOOM — depth at or above 0.66
     */
    private fun determineFlowerState(
        depth: Float,
        healthPercent: Float,
        daysSinceLastShow: Int
    ): FlowerState {
        // GONE takes absolute priority
        if (daysSinceLastShow > SpacedRepetitionConfig.GONE_THRESHOLD_DAYS) {
            return FlowerState.GONE
        }

        // Health-based states
        if (healthPercent <= SpacedRepetitionConfig.WILTED_THRESHOLD) {
            return FlowerState.WILTED
        }
        if (healthPercent < 1.0f) {
            return FlowerState.WILTING
        }

        // Growth-based states (only when healthy)
        return when {
            depth < 0.33f -> FlowerState.SEED
            depth < 0.66f -> FlowerState.SPROUT
            else -> FlowerState.BLOOM
        }
    }

    /**
     * Convert a timestamp in milliseconds to days elapsed since that timestamp.
     *
     * @param dateMs timestamp in milliseconds, or 0 if never accessed
     * @return number of full days since the given timestamp, or 0 if dateMs is 0
     */
    private fun daysSince(dateMs: Long): Int {
        if (dateMs <= 0L) return 0
        val elapsed = System.currentTimeMillis() - dateMs
        return (elapsed / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
    }
}
