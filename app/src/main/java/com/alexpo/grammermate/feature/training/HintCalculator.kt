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

    /**
     * Средний уровень: показать ЧАСТЬ лемм внутри подсказки.
     *
     * Раньше здесь пряталась каждая вторая скобка целиком. Это работало бы для
     * промптов с несколькими подсказками, но в реальном контенте скобка ровно
     * одна у 99% карточек, а [offset] задаётся один раз на сессию
     * (TrainingViewModel: hintSessionOffset = Random.nextInt(0, 100)). Поэтому
     * "половина" вырождалась в чётность offset: вся сессия шла либо как EASY,
     * либо как HARD, и среднего уровня фактически не существовало.
     *
     * Теперь режется содержимое скобки: из списка лемм показывается половина
     * (с округлением вверх), непоказанное обозначается многоточием. [offset]
     * сдвигает окно между сессиями, чтобы не заучивалась позиция, но внутри
     * сессии окно стабильно. Подсказка из одной леммы остаётся целиком —
     * резать там нечего.
     */
    private fun stripHalf(text: String, offset: Int): String {
        return PARENTHETICAL_REGEX.replace(text) { matchResult ->
            val raw = matchResult.value
            val open = raw.indexOf('(')
            val lead = raw.substring(0, open)
            val parts = raw.substring(open + 1, raw.length - 1)
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (parts.size < 2) {
                raw
            } else {
                val keep = (parts.size + 1) / 2
                // окно всегда непрерывное и в исходном порядке, без заворота
                val start = ((offset % (parts.size - keep + 1)) + (parts.size - keep + 1)) %
                    (parts.size - keep + 1)
                val shown = parts.subList(start, start + keep).joinToString(", ")
                val prefix = if (start > 0) "…, " else ""
                val suffix = if (start + keep < parts.size) ", …" else ""
                "$lead($prefix$shown$suffix)"
            }
        }
    }
}
