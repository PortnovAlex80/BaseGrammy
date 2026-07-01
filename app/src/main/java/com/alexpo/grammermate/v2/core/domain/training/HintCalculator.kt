package com.alexpo.grammermate.v2.core.domain.training

import com.alexpo.grammermate.v2.core.domain.model.HintLevel

/**
 * Чистый расчёт видимости подсказок в скобках для промпта урока.
 *
 * Перенесено из v1
 * `app/legacy-src/java/com/alexpo/grammermate/feature/training/HintCalculator.kt`.
 *
 * Подсказки — это текст в круглых скобках в `promptRu`. Их видимость зависит от
 * двух факторов, берётся минимум (effective-фракция):
 *  - **schedulerFraction** — прогресс по уроку (encounterCount / review):
 *    больше встреч → меньше подсказок;
 *  - **userFraction** — настройка пользователя [HintLevel]:
 *    EASY → все, MEDIUM → половина, HARD → никаких.
 *
 * Ноль Android-зависимостей, чистые функции. Результат детерминирован для
 * заданных аргументов (кроме shuffle — его здесь нет).
 */
object HintCalculator {

    /** `\s*\([^)]+\)` — содержимое круглых скобок с опциональным лидирующим пробелом. */
    private val PARENTHETICAL_REGEX = Regex("\\s*\\([^)]+\\)")

    /**
     * Рассчитать промпт с эффективным уровнем подсказок.
     *
     * Перенесено 1:1 из v1 `HintCalculator.calculateEffectiveHints`, строки 9–39.
     *
     * schedulerFraction:
     *  - isReviewMode → 1.0 (в review подсказок нет, всё видится);
     *  - encounterCount <= 1 → 1.0 (первая встреча — все подсказки);
     *  - encounterCount == 2 → 0.5 (половина);
     *  - иначе → 0.0 (подсказок нет).
     *
     * userFraction: EASY=1.0, MEDIUM=0.5, HARD=0.0.
     *
     * effective = min(schedulerFraction, userFraction):
     *  - >= 1.0 → вернуть [promptRu] без изменений (все подсказки видны);
     *  - <= 0.0 → [stripAll] (никаких подсказок);
     *  - иначе → [stripHalf] по [sessionOffset].
     *
     * В [isBossBattle] всегда [stripAll] (боссы — без подсказок).
     *
     * @param promptRu       промпт урока на русском (с подсказками в скобках).
     * @param encounterCount сколько раз уже встречалась карточка.
     * @param hintLevel      настройка подсказок пользователем.
     * @param sessionOffset  смещение для чередования скобок в [stripHalf].
     * @param isBossBattle   режим битвы с боссом (force stripAll).
     * @param isReviewMode   режим повторения (schedulerFraction=1.0).
     * @return промпт с применённым уровнем подсказок.
     */
    fun calculateEffectiveHints(
        promptRu: String,
        encounterCount: Int,
        hintLevel: HintLevel,
        sessionOffset: Int,
        isBossBattle: Boolean = false,
        isReviewMode: Boolean = false,
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

    /**
     * Убрать ВСЕ подсказки в скобках.
     *
     * Перенесено из v1 `HintCalculator.stripAll`, строки 41–43. В v1 был private;
     * в v2 — public (используется напрямую, напр. для boss-режима в тестах).
     *
     * @param text исходный промпт.
     * @return промпт без всех `(...)`-блоков.
     */
    fun stripAll(text: String): String {
        return PARENTHETICAL_REGEX.replace(text, "")
    }

    /**
     * Убрать ПОЛОВИНУ подсказок, чередуя скобки.
     *
     * Перенесено 1:1 из v1 `HintCalculator.stripHalf`, строки 45–52.
     * Локальный индекс скобок [index] стартует с 0; скобка видна, если
     * `(index + offset) % 2 == 0`, иначе удаляется. [offset] (=[sessionOffset])
     * сдвигает паттерн чередования между карточками/сессиями.
     *
     * @param text   исходный промпт.
     * @param offset смещение чередования (0/1).
     * @return промпт, где видна примерно каждая вторая скобка.
     */
    fun stripHalf(text: String, offset: Int): String {
        var index = 0
        return PARENTHETICAL_REGEX.replace(text) { matchResult ->
            val show = (index + offset) % 2 == 0
            index++
            if (show) matchResult.value else ""
        }
    }
}
