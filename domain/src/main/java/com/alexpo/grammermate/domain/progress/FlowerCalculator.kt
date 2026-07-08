package com.alexpo.grammermate.domain.progress

import com.alexpo.grammermate.domain.model.FlowerState
import com.alexpo.grammermate.domain.model.FlowerVisual
import com.alexpo.grammermate.domain.model.LessonMastery
import com.alexpo.grammermate.domain.srs.SrsConstants
import kotlin.math.exp

/**
 * Чистые формулы интервального повторения на кривой забывания Эббингауза.
 *
 * Перенесено из v1
 * `app/legacy-src/java/com/alexpo/grammermate/data/SpacedRepetitionConfig.kt`
 * (вычислительная часть, строки 65–170). Константы — в [SrsConstants].
 *
 * Формула забывания: `R = e^(-t/S)`, где R — retention, t — дней с последнего
 * повторения, S — стабильность памяти. Источники: Ebbinghaus (1885);
 * Murre & Dros (2015). Ноль Android-зависимостей.
 */
object SpacedRepetitionFormulas {

    /**
     * Стабильность памяти по числу успешных повторений.
     *
     * Перенесено 1:1 из v1 `SpacedRepetitionConfig.calculateStability`, строки 65–75.
     * `S = S0 * multiplier^step`, где S0 = [SrsConstants.BASE_STABILITY_DAYS],
     * multiplier = [SrsConstants.STABILITY_MULTIPLIER]. При step<0 возвращается S0.
     *
     * @param intervalStepIndex индекс в лестнице интервалов (0 = первое повторение).
     * @return стабильность S в днях.
     */
    fun calculateStability(intervalStepIndex: Int): Double {
        if (intervalStepIndex < 0) return SrsConstants.BASE_STABILITY_DAYS

        var stability = SrsConstants.BASE_STABILITY_DAYS
        repeat(intervalStepIndex.coerceAtMost(SrsConstants.INTERVAL_LADDER_DAYS.size - 1)) {
            stability *= SrsConstants.STABILITY_MULTIPLIER
        }
        return stability
    }

    /**
     * Процент удержания (retention) по формуле Эббингауза.
     *
     * Перенесено 1:1 из v1 `SpacedRepetitionConfig.calculateRetention`, строки 86–93.
     * `R = e^(-t/S)`, clamp [0, 1]. При t<=0 → 1.0 (только что показано).
     *
     * @param daysSinceLastShow  дней с последнего показа.
     * @param intervalStepIndex  текущий шаг в лестнице интервалов.
     * @return retention 0..1.
     */
    fun calculateRetention(daysSinceLastShow: Int, intervalStepIndex: Int): Float {
        if (daysSinceLastShow <= 0) return 1.0f

        val stability = calculateStability(intervalStepIndex)
        val retention = exp(-daysSinceLastShow.toDouble() / stability)
        return retention.toFloat().coerceIn(0f, 1f)
    }

    /**
     * «Здоровье» цветка с учётом лестницы интервалов.
     *
     * Перенесено 1:1 из v1 `SpacedRepetitionConfig.calculateHealthPercent`, строки 106–135.
     *
     * Логика:
     *  - дней<=0 → 1.0 (только что показано);
     *  - дней >= [SrsConstants.GONE_THRESHOLD_DAYS] → 0.0 (исчез);
     *  - ожидаемый интервал для текущего шага (последний шаг лестницы — для шагов
     *    за пределами лестницы);
     *  - если дней <= ожидаемого интервала → 1.0 (не просрочен);
     *  - иначе просрочка = дней − ожидаемый; `health = WILTED + (1−WILTED) * e^(-overdue/S)`,
     *    clamp [WILTED, 1.0].
     *
     * @param daysSinceLastShow  дней с последнего показа.
     * @param intervalStepIndex  текущий шаг в лестнице интервалов.
     * @return здоровье [SrsConstants.WILTED_THRESHOLD]..1.0.
     */
    fun calculateHealthPercent(daysSinceLastShow: Int, intervalStepIndex: Int): Float {
        if (daysSinceLastShow <= 0) return 1.0f
        if (daysSinceLastShow >= SrsConstants.GONE_THRESHOLD_DAYS) return 0f

        val expectedInterval = if (intervalStepIndex in SrsConstants.INTERVAL_LADDER_DAYS.indices) {
            SrsConstants.INTERVAL_LADDER_DAYS[intervalStepIndex]
        } else {
            SrsConstants.INTERVAL_LADDER_DAYS.last()
        }

        if (daysSinceLastShow <= expectedInterval) return 1.0f

        val overdueDays = daysSinceLastShow - expectedInterval
        val stability = calculateStability(intervalStepIndex)
        val decay = exp(-overdueDays.toDouble() / stability)
        val health = SrsConstants.WILTED_THRESHOLD + (1f - SrsConstants.WILTED_THRESHOLD) * decay.toFloat()
        return health.coerceIn(SrsConstants.WILTED_THRESHOLD, 1f)
    }

    /**
     * Следующий шаг в лестнице интервалов.
     *
     * Перенесено 1:1 из v1 `SpacedRepetitionConfig.nextIntervalStep`, строки 144–152.
     * При [wasOnTime]=true — шаг вперёд (clamp к последнему индексу); иначе без изменений.
     *
     * @param currentStepIndex текущий индекс.
     * @param wasOnTime        было ли повторение вовремя.
     * @return новый индекс шага.
     */
    fun nextIntervalStep(currentStepIndex: Int, wasOnTime: Boolean): Int {
        return if (wasOnTime) {
            (currentStepIndex + 1).coerceAtMost(SrsConstants.INTERVAL_LADDER_DAYS.size - 1)
        } else {
            currentStepIndex.coerceAtLeast(0)
        }
    }

    /**
     * Было ли повторение вовремя (не просрочено).
     *
     * Перенесено 1:1 из v1 `SpacedRepetitionConfig.wasRepetitionOnTime`, строки 161–170.
     *
     * @param daysSinceLastShow дней с последнего показа.
     * @param intervalStepIndex текущий шаг.
     * @return true, если дней <= ожидаемому интервалу шага.
     */
    fun wasRepetitionOnTime(daysSinceLastShow: Int, intervalStepIndex: Int): Boolean {
        val expectedInterval = if (intervalStepIndex in SrsConstants.INTERVAL_LADDER_DAYS.indices) {
            SrsConstants.INTERVAL_LADDER_DAYS[intervalStepIndex]
        } else {
            SrsConstants.INTERVAL_LADDER_DAYS.last()
        }
        return daysSinceLastShow <= expectedInterval
    }
}

/**
 * Калькулятор визуального состояния цветка урока.
 *
 * Перенесено из v1
 * `app/legacy-src/java/com/alexpo/grammermate/data/FlowerCalculator.kt`.
 *
 * Логика:
 *  - masteryPercent: uniqueCardShows / MASTERY_THRESHOLD, 0..1;
 *  - healthPercent: по кривой забывания ([SpacedRepetitionFormulas.calculateHealthPercent]);
 *  - scaleMultiplier: masteryPercent * healthPercent, clamp [0.5, 1.0].
 *
 * **ДВА СОЗНАТЕЛЬНЫХ РАСХОЖДЕНИЯ С v1 (унификация/исправления):**
 *  1. **daysSince БЕЗ +1.** v1 `FlowerCalculator.calculateDaysSince` (строки 95–100)
 *     считал `(now - last)/DAY_MS` (без +1), а v1 `LessonLadderCalculator` (строка 19)
 *     — `(.../DAY_MS) + 1`. В v2 FlowerCalculator остаётся БЕЗ +1 (как в самом
 *     FlowerCalculator v1), что корректно для кривой здоровья; +1 сохранён только
 *     в [LessonLadderCalculator] (там это part of label-логики). Документируем явно,
 *     чтобы не путать два калькулятора.
 *  2. **WILTED БЕЗ epsilon.** v1 `FlowerCalculator.determineFlowerState` (строка 80)
 *     использовал `healthPercent <= WILTED_THRESHOLD + 0.01f` (эпсилон «т.к. формула
 *     гарантирует health >= WILTED_THRESHOLD»). В v2 УНИФИЦИРОВАНО с
 *     PackProgressCalculator: строго `healthPercent <= WILTED_THRESHOLD` без эпсилон.
 *     Это убирает рассинхрон: урок и пак теперь считают WILTED одинаково.
 */
object FlowerCalculator {

    /**
     * Рассчитать визуальное состояние цветка урока.
     *
     * Перенесено из v1 `FlowerCalculator.calculate`, строки 20–67 (адаптировано:
     * `nowMs` передаётся параметром вместо `System.currentTimeMillis()`, модель —
     * [LessonMastery] вместо v1 `LessonMasteryState`).
     *
     * @param mastery           данные освоения урока (null = урок не начат → SEED).
     * @param totalCardsInLesson общее число карточек в уроке (для контекста; v1-параметр).
     * @param nowMs             текущее epoch-время (injectable для тестов).
     * @return [FlowerVisual] с состоянием и метриками.
     */
    fun calculate(mastery: LessonMastery?, totalCardsInLesson: Int = 0, nowMs: Long): FlowerVisual {
        // Урок не начат — SEED (готов к началу). LOCKED — это UI-концепция заблокированных уроков.
        if (mastery == null || mastery.uniqueCardShows == 0) {
            return FlowerVisual(
                state = FlowerState.SEED,
                masteryPercent = 0f,
                healthPercent = 1f,
                scaleMultiplier = 0.5f,
            )
        }

        // Процент закрепления (0..1, макс [SrsConstants.MASTERY_THRESHOLD] показов).
        val masteryPercent = (mastery.uniqueCardShows.toFloat() / SrsConstants.MASTERY_THRESHOLD)
            .coerceIn(0f, 1f)

        // Дней с последнего показа (БЕЗ +1 — см. KDoc класса, расхождение #1).
        val daysSinceLastShow = calculateDaysSince(mastery.lastShowDateMs, nowMs)

        // Проверка на исчезновение (> [SrsConstants.GONE_THRESHOLD_DAYS] дней).
        if (daysSinceLastShow > SrsConstants.GONE_THRESHOLD_DAYS) {
            return FlowerVisual(
                state = FlowerState.GONE,
                masteryPercent = 0f,
                healthPercent = 0f,
                scaleMultiplier = 0.5f,
            )
        }

        // Здоровье цветка по кривой забывания.
        val healthPercent = SpacedRepetitionFormulas.calculateHealthPercent(
            daysSinceLastShow = daysSinceLastShow,
            intervalStepIndex = mastery.intervalStepIndex,
        )

        // Состояние цветка.
        val state = determineFlowerState(masteryPercent, healthPercent, daysSinceLastShow)

        // Масштаб = masteryPercent * health, минимум 50 %.
        val scale = (masteryPercent * healthPercent).coerceIn(0.5f, 1.0f)

        return FlowerVisual(
            state = state,
            masteryPercent = masteryPercent,
            healthPercent = healthPercent,
            scaleMultiplier = scale,
        )
    }

    /**
     * Определить состояние цветка по метрикам.
     *
     * Перенесено из v1 `FlowerCalculator.determineFlowerState`, строки 72–90,
     * с двумя правками унификации (см. KDoc класса):
     *  1. **GONE добавлен первым** (как в PackProgressCalculator): дней >
     *     [SrsConstants.GONE_THRESHOLD_DAYS] → GONE. В v1 FlowerCalculator GONE
     *     проверялся в calculate(), здесь — в determineFlowerState для консистентности
     *     сигнатуры с [PackProgressCalculator]. Семантически идентично.
     *  2. **WILTED строго `<= WILTED_THRESHOLD` без эпсилон** (расхождение #2).
     *
     * Остальные переходы: `health < 1.0` → WILTING; иначе по masteryPercent
     * (<0.33 → SEED, <0.66 → SPROUT, иначе BLOOM).
     */
    fun determineFlowerState(masteryPercent: Float, healthPercent: Float, daysSince: Int): FlowerState {
        return when {
            // GONE — абсолютный приоритет (унификация с PackProgressCalculator).
            daysSince > SrsConstants.GONE_THRESHOLD_DAYS -> FlowerState.GONE

            // WILTED — здоровье на/ниже порога (СТРОГО, без epsilon; расхождение #2 с v1).
            healthPercent <= SrsConstants.WILTED_THRESHOLD -> FlowerState.WILTED

            // WILTING — здоровье между порогом и 1.0.
            healthPercent < 1.0f -> FlowerState.WILTING

            // Иначе — рост по проценту закрепления.
            masteryPercent < 0.33f -> FlowerState.SEED
            masteryPercent < 0.66f -> FlowerState.SPROUT
            else -> FlowerState.BLOOM
        }
    }

    /**
     * Emoji для состояния цветка.
     *
     * Перенесено 1:1 из v1 `FlowerCalculator.getEmoji`, строки 105–115.
     * LOCKED→🔒, SEED→🌱, SPROUT→🌿, BLOOM→🌸, WILTING→🥀, WILTED→🍂, GONE→⚫.
     *
     * @param state состояние цветка.
     * @return соответствующий emoji.
     */
    fun getEmoji(state: FlowerState): String {
        return when (state) {
            FlowerState.LOCKED -> "\uD83D\uDD12"  // 🔒
            FlowerState.SEED -> "\uD83C\uDF31"    // 🌱
            FlowerState.SPROUT -> "\uD83C\uDF3F"  // 🌿
            FlowerState.BLOOM -> "\uD83C\uDF38"   // 🌸
            FlowerState.WILTING -> "\uD83E\uDD40" // 🥀
            FlowerState.WILTED -> "\uD83C\uDF42"  // 🍂
            FlowerState.GONE -> "\u26AB"          // ⚫
        }
    }

    /**
     * Emoji + масштаб для отображения.
     *
     * Перенесено 1:1 из v1 `FlowerCalculator.getEmojiWithScale`, строки 121–123.
     * Масштаб влияет на размер шрифта иконки в UI.
     *
     * @param flower визуальное представление цветка.
     * @return пара (emoji, scaleMultiplier).
     */
    fun getEmojiWithScale(flower: FlowerVisual): Pair<String, Float> {
        return getEmoji(flower.state) to flower.scaleMultiplier
    }

    /**
     * Рассчитать дней с указанной даты.
     *
     * Перенесено из v1 `FlowerCalculator.calculateDaysSince`, строки 95–100.
     * `(nowMs - lastShowDateMs) / DAY_MS`, coerceAtLeast(0). **БЕЗ +1**
     * (расхождение #1 с LessonLadderCalculator, см. KDoc класса).
     */
    private fun calculateDaysSince(timestampMs: Long, nowMs: Long): Int {
        if (timestampMs <= 0L) return 0
        val diffMs = nowMs - timestampMs
        return (diffMs / SrsConstants.DAY_MS).toInt().coerceAtLeast(0)
    }
}
