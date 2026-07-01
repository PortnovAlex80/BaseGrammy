package com.alexpo.grammermate.v2.core.domain.progress

import com.alexpo.grammermate.v2.core.domain.model.LessonMastery
import com.alexpo.grammermate.v2.core.domain.srs.SrsConstants

/**
 * Метрики лесенки интервалов одного урока.
 *
 * Перенесено из v1 `data/LessonLadderCalculator.kt:56` (`data class LessonLadderMetrics`).
 * Вынесено рядом с калькулятором (в v1 было в том же файле).
 *
 * @property uniqueCardShows    сколько уникальных карточек показано (null = нет данных).
 * @property daysSinceLastShow  дней с последнего показа (null = нет данных).
 * @property intervalLabel      метка интервала/просрочки (null = нет данных).
 */
data class LessonLadderMetrics(
    val uniqueCardShows: Int?,
    val daysSinceLastShow: Int?,
    val intervalLabel: String?,
)

/**
 * Чистый калькулятор метрик лесенки интервалов урока.
 *
 * Перенесено из v1
 * `app/legacy-src/java/com/alexpo/grammermate/data/LessonLadderCalculator.kt`.
 *
 * Показывает, в каком интервале повторения находится урок и насколько просрочен.
 *
 * **РАСХОЖДЕНИЕ С v1 (документируем): daysSince С +1.**
 * В v1 `LessonLadderCalculator.calculate` (строка 19) используется
 * `((nowMs - lastShowDateMs) / DAY_MS) + 1`. В v2 СОХРАНЁН +1 — это намеренная
 * семантика лесенки: «1 день» = сегодня показано, и метка интервала считается
 * инклюзивно. Это ОТЛИЧАЕТСЯ от [FlowerCalculator], где daysSince БЕЗ +1 (там
 * кривая здоровья). Два калькулятора намеренно используют разные определения —
 * см. KDoc [FlowerCalculator]. В v2 оба поведения сохранены как в v1, чтобы не
 * сломать label-логику и health-логику независимо.
 */
object LessonLadderCalculator {

    /**
     * Рассчитать метрики лесенки интервалов урока.
     *
     * Перенесено 1:1 из v1 `LessonLadderCalculator.calculate`, строки 6–36
     * (адаптировано: модель [LessonMastery] вместо v1 `LessonMasteryState`;
     * `DAY_MS` берётся из [SrsConstants]).
     *
     * При mastery==null / uniqueCardShows<=0 / lastShowDateMs<=0 → все поля null.
     * Иначе:
     *  - daysSince = `((nowMs - lastShowDateMs) / DAY_MS).coerceAtLeast(0) + 1`;
     *  - expectedInterval = ladder[stepIndex] ?: ladder.lastOrNull();
     *  - если daysSince > expectedInterval → "Просрочка+N" (N = daysSince − expected);
     *  - иначе [buildIntervalLabel].
     *
     * @param mastery данные освоения урока.
     * @param nowMs   текущее epoch-время (injectable).
     * @param ladder  лестница интервалов в днях (по умолчанию [SrsConstants.INTERVAL_LADDER_DAYS]).
     * @return [LessonLadderMetrics].
     */
    fun calculate(
        mastery: LessonMastery?,
        nowMs: Long,
        ladder: List<Int> = SrsConstants.INTERVAL_LADDER_DAYS,
    ): LessonLadderMetrics {
        if (mastery == null || mastery.uniqueCardShows <= 0 || mastery.lastShowDateMs <= 0L) {
            return LessonLadderMetrics(
                uniqueCardShows = null,
                daysSinceLastShow = null,
                intervalLabel = null,
            )
        }

        val daysSince = ((nowMs - mastery.lastShowDateMs) / SrsConstants.DAY_MS).toInt().coerceAtLeast(0) + 1
        val expectedInterval = ladder.getOrNull(mastery.intervalStepIndex) ?: ladder.lastOrNull()

        if (expectedInterval != null && daysSince > expectedInterval) {
            val overdue = daysSince - expectedInterval
            return LessonLadderMetrics(
                uniqueCardShows = mastery.uniqueCardShows,
                daysSinceLastShow = daysSince,
                intervalLabel = "Просрочка+$overdue",
            )
        }

        return LessonLadderMetrics(
            uniqueCardShows = mastery.uniqueCardShows,
            daysSinceLastShow = daysSince,
            intervalLabel = buildIntervalLabel(daysSince, ladder),
        )
    }

    /**
     * Построить метку интервала вида "a-b" по позиции daysSince в [ladder].
     *
     * Перенесено из v1 `LessonLadderCalculator.buildIntervalLabel`, строки 38–53.
     * В v1 был private; в v2 — public (используется в тестах и UI напрямую).
     *
     * Логика:
     *  - пустая лестница → "-";
     *  - один элемент → "n-n";
     *  - daysSince <= ladder[0] → "ladder[0]-ladder[1]";
     *  - иначе ищется первый индекс i, где daysSince <= ladder[i] → "ladder[i-1]-ladder[i]";
     *  - если daysSince больше всех → "ladder[last-1]-ladder[last]".
     *
     * @param daysSince дней с последнего показа (с +1, как в [calculate]).
     * @param ladder    лестница интервалов.
     * @return метка интервала.
     */
    fun buildIntervalLabel(daysSince: Int, ladder: List<Int>): String {
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
