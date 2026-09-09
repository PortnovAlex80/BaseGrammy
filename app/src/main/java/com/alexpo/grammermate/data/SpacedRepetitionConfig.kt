package com.alexpo.grammermate.data

import kotlin.math.exp
import kotlin.math.ln

/**
 * Конфигурация интервального повторения на основе кривой забывания Эббингауза.
 *
 * Формула забывания: R = e^(-t/S)
 * где R = вероятность вспоминания (retention)
 *     t = время с последнего повторения
 *     S = стабильность памяти (зависит от количества повторений)
 *
 * Источники:
 * - Ebbinghaus, H. (1885). Memory: A Contribution to Experimental Psychology
 * - Murre & Dros (2015). Replication and Analysis of Ebbinghaus' Forgetting Curve
 * - SuperMemo algorithm documentation
 */
object SpacedRepetitionConfig {

    /**
     * Лестница интервальных повторов (в днях).
     * Определяет ожидаемые интервалы между повторениями для поддержания памяти.
     * Каждый следующий интервал увеличивается примерно в 1.5-2 раза.
     */
    val INTERVAL_LADDER_DAYS = listOf(1, 2, 4, 7, 10, 14, 20, 28, 42, 56)

    /**
     * Минимальное количество уникальных показов карточек для 100% закрепления паттерна.
     */
    const val MASTERY_THRESHOLD = 150

    /**
     * Сколько показов карточек считается за один «день учёбы» на шкале усилий.
     *
     * Калибровка по реальному паку: медианный урок IT_EXPRESS — 40–46 карточек,
     * что при размере блока 10 даёт ~6 блоков ≈ 60 показов (включая подмешанное
     * повторение). То есть «пройденный урок ≈ один день» — ровно то прочтение,
     * которое заложено в механику.
     */
    const val CARDS_PER_DAY = 60.0

    /**
     * Порог здоровья, ниже которого цветок считается увядшим.
     */
    const val WILTED_THRESHOLD = 0.5f

    /**
     * Дней без повторения до полного исчезновения цветка.
     */
    const val GONE_THRESHOLD_DAYS = 90

    /**
     * Базовая стабильность памяти (в днях) для первого повторения.
     * Это значение S в формуле R = e^(-t/S) для нового материала.
     *
     * Исследования показывают, что без повторения через 1 день
     * остаётся около 33% информации, что даёт S ≈ 0.9 дней.
     */
    private const val BASE_STABILITY_DAYS = 0.9

    /**
     * Множитель увеличения стабильности с каждым успешным повторением.
     * После каждого повторения в правильный интервал, стабильность
     * увеличивается примерно в 2-2.5 раза.
     */
    private const val STABILITY_MULTIPLIER = 2.2

    /**
     * Рассчитать стабильность памяти на основе количества успешных повторений.
     *
     * @param intervalStepIndex индекс в лестнице интервалов (0 = первое повторение)
     * @return стабильность S в днях
     */
    fun calculateStability(intervalStepIndex: Int): Double {
        if (intervalStepIndex < 0) return BASE_STABILITY_DAYS

        // S = S0 * multiplier^step
        // С каждым шагом стабильность растёт экспоненциально
        var stability = BASE_STABILITY_DAYS
        repeat(intervalStepIndex.coerceAtMost(INTERVAL_LADDER_DAYS.size - 1)) {
            stability *= STABILITY_MULTIPLIER
        }
        return stability
    }

    /**
     * Рассчитать процент удержания (retention) по формуле Эббингауза.
     *
     * R = e^(-t/S)
     *
     * @param daysSinceLastShow дней с последнего показа
     * @param intervalStepIndex текущий шаг в лестнице интервалов
     * @return процент удержания от 0.0 до 1.0
     */
    fun calculateRetention(daysSinceLastShow: Int, intervalStepIndex: Int): Float {
        if (daysSinceLastShow <= 0) return 1.0f

        val stability = calculateStability(intervalStepIndex)
        val retention = exp(-daysSinceLastShow.toDouble() / stability)

        return retention.toFloat().coerceIn(0f, 1f)
    }

    /**
     * Рассчитать "здоровье" цветка с учётом лестницы интервалов.
     *
     * Если повторение происходит в пределах ожидаемого интервала - здоровье 100%.
     * Если превышает интервал - здоровье падает по кривой забывания,
     * но не ниже WILTED_THRESHOLD (50%).
     *
     * @param daysSinceLastShow дней с последнего показа
     * @param intervalStepIndex текущий шаг в лестнице интервалов
     * @return здоровье от WILTED_THRESHOLD до 1.0
     */
    fun calculateHealthPercent(daysSinceLastShow: Int, intervalStepIndex: Int): Float {
        if (daysSinceLastShow <= 0) return 1.0f

        // Если прошло больше 90 дней - цветок исчез
        if (daysSinceLastShow >= GONE_THRESHOLD_DAYS) return 0f

        // Получаем ожидаемый интервал для текущего шага
        val expectedInterval = expectedIntervalDays(intervalStepIndex)

        // Если в пределах интервала - здоровье 100%
        if (daysSinceLastShow <= expectedInterval) return 1.0f

        // Просрочка относительно ожидаемого интервала
        val overdueDays = daysSinceLastShow - expectedInterval

        // Стабильность для расчёта затухания
        val stability = calculateStability(intervalStepIndex)

        // Экспоненциальное затухание от 100% до 50%
        // health = 0.5 + 0.5 * e^(-overdue/stability)
        val decay = exp(-overdueDays.toDouble() / stability)
        val health = WILTED_THRESHOLD + (1f - WILTED_THRESHOLD) * decay.toFloat()

        return health.coerceIn(WILTED_THRESHOLD, 1f)
    }

    /**
     * Ожидаемый интервал (в днях) для текущего шага лестницы.
     * За пределами лестницы — последний (самый длинный) интервал.
     */
    fun expectedIntervalDays(intervalStepIndex: Int): Int =
        if (intervalStepIndex in INTERVAL_LADDER_DAYS.indices) {
            INTERVAL_LADDER_DAYS[intervalStepIndex]
        } else {
            INTERVAL_LADDER_DAYS.last()
        }

    /**
     * Эффективный «возраст» урока по двум шкалам сразу, взятым по максимуму.
     *
     * Календарная шкала ловит того, кто пропал на месяц; шкала усилий — того,
     * кто прошёл пять уроков за вечер (календарь в этом случае показывает 0).
     * Максимум, а не сумма: нет двойного счёта, и поведение читается однозначно.
     *
     * @param nowMs текущее время
     * @param lastReviewMs время последнего самостоятельного воспроизведения урока
     * @param totalEffortCards суммарные показы карточек по паку
     * @param effortAtLastReview снимок [totalEffortCards] на момент последнего повторения
     */
    fun effectiveDays(
        nowMs: Long,
        lastReviewMs: Long,
        totalEffortCards: Int,
        effortAtLastReview: Int
    ): Double {
        if (lastReviewMs <= 0L) return 0.0
        val calendarDays = (nowMs - lastReviewMs).coerceAtLeast(0L) / 86_400_000.0
        val effortDays = (totalEffortCards - effortAtLastReview).coerceAtLeast(0) / CARDS_PER_DAY
        return maxOf(calendarDays, effortDays)
    }

    /**
     * Степень просроченности: 1.0 — урок ровно созрел, 3.0 — просрочен втрое.
     *
     * Используется как **ранг**, а не как булев признак «созрел». Благодаря этому
     * повторение невозможно потерять: не попавший в текущий блок урок остаётся
     * просроченным и в следующий раз оказывается выше в очереди.
     */
    fun overdueRatio(effectiveDays: Double, intervalStepIndex: Int): Double {
        val expected = expectedIntervalDays(intervalStepIndex).coerceAtLeast(1)
        return effectiveDays / expected
    }

    /**
     * Определить следующий шаг в лестнице интервалов.
     *
     * @param currentStepIndex текущий индекс
     * @param wasOnTime true если повторение было вовремя
     * @return новый индекс шага
     */
    fun nextIntervalStep(currentStepIndex: Int, wasOnTime: Boolean): Int {
        return if (wasOnTime) {
            // Продвигаемся вперёд по лестнице
            (currentStepIndex + 1).coerceAtMost(INTERVAL_LADDER_DAYS.size - 1)
        } else {
            // Оставляем шаг без изменений
            currentStepIndex.coerceAtLeast(0)
        }
    }

    /**
     * Проверить, было ли повторение вовремя.
     *
     * @param daysSinceLastShow дней с последнего показа
     * @param intervalStepIndex текущий шаг
     * @return true если повторение в пределах допустимого интервала
     */
    fun wasRepetitionOnTime(daysSinceLastShow: Int, intervalStepIndex: Int): Boolean {
        // Повторение считается вовремя, если не просрочен ожидаемый интервал
        return daysSinceLastShow <= expectedIntervalDays(intervalStepIndex)
    }
}
