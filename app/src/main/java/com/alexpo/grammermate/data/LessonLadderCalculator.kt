package com.alexpo.grammermate.data

object LessonLadderCalculator {
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun calculate(
        mastery: LessonMasteryState?,
        nowMs: Long,
        ladder: List<Int> = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS
    ): LessonLadderMetrics {
        if (mastery == null || mastery.uniqueCardShows <= 0 || mastery.lastShowDateMs <= 0L) {
            return LessonLadderMetrics(
                uniqueCardShows = null,
                daysSinceLastShow = null,
                intervalLabel = null
            )
        }

        val daysSince = ((nowMs - mastery.lastShowDateMs) / DAY_MS).toInt().coerceAtLeast(0) + 1
        val expectedInterval = ladder.getOrNull(mastery.intervalStepIndex) ?: ladder.lastOrNull()

        if (expectedInterval != null && daysSince > expectedInterval) {
            val overdue = daysSince - expectedInterval
            return LessonLadderMetrics(
                uniqueCardShows = mastery.uniqueCardShows,
                daysSinceLastShow = daysSince,
                intervalLabel = "Просрочка+$overdue"
            )
        }

        return LessonLadderMetrics(
            uniqueCardShows = mastery.uniqueCardShows,
            daysSinceLastShow = daysSince,
            intervalLabel = buildIntervalLabel(daysSince, ladder)
        )
    }

    private fun buildIntervalLabel(daysSince: Int, ladder: List<Int>): String {
        if (ladder.isEmpty()) return "-"
        if (ladder.size == 1) return "${ladder[0]}-${ladder[0]}"

        if (daysSince <= ladder[0]) {
            return "${ladder[0]}-${ladder[1]}"
        }

        for (index in 1 until ladder.size) {
            if (daysSince <= ladder[index]) {
                return "${ladder[index - 1]}-${ladder[index]}"
            }
        }

        return "${ladder[ladder.size - 2]}-${ladder.last()}"
    }
}

data class LessonLadderMetrics(
    val uniqueCardShows: Int?,
    val daysSinceLastShow: Int?,
    val intervalLabel: String?
)
