package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.HintLevel

object HintCalculator {

    private val PARENTHETICAL_REGEX = Regex("\\s*\\([^)]+\\)")

    fun calculateEffectiveHints(
        promptRu: String,
        encounterCount: Int,
        hintLevel: HintLevel,
        sessionOffset: Int,
        isBossBattle: Boolean = false,
        isReviewMode: Boolean = false
    ): String {
        if (isBossBattle) return stripAll(promptRu)

        val schedulerFraction = when {
            isReviewMode -> 1.0
            encounterCount <= 1 -> 1.0
            encounterCount == 2 -> 0.5
            else -> 0.0
        }

        val userFraction = when (hintLevel) {
            HintLevel.EASY -> 1.0
            HintLevel.MEDIUM -> 0.5
            HintLevel.HARD -> 0.0
        }

        val effective = minOf(schedulerFraction, userFraction)

        return when {
            effective >= 1.0 -> promptRu
            effective <= 0.0 -> stripAll(promptRu)
            else -> stripHalf(promptRu, sessionOffset)
        }
    }

    private fun stripAll(text: String): String {
        return PARENTHETICAL_REGEX.replace(text, "")
    }

    private fun stripHalf(text: String, offset: Int): String {
        var index = 0
        return PARENTHETICAL_REGEX.replace(text) { matchResult ->
            val show = (index + offset) % 2 == 0
            index++
            if (show) matchResult.value else ""
        }
    }
}
