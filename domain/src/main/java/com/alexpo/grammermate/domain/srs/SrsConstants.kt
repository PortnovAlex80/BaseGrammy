package com.alexpo.grammermate.domain.srs

/**
 * Единый источник правды для констант интервального повторения (SRS) и
 * связанных с ним порогов мастерства/выученности.
 *
 * Чистый Kotlin (object), без Android-зависимостей.
 *
 * В v1 «лестница» интервалов дней была продублирована в двух местах:
 * `SpacedRepetitionConfig.INTERVAL_LADDER_DAYS` и `MixedReviewScheduler`.
 * В v2 эти значения определены ОДИН раз — здесь, в [INTERVAL_LADDER_DAYS].
 *
 * ПРИМЕЧАНИЕ: в [SrsParams] лестницы нет — там только веса FSRS v6 и
 * retention/maximumInterval. Единственное наследие v1 — константа
 * `LEGACY_INTERVAL_LADDER_DAYS` в [SrsMigration], которая используется
 * ТОЛЬКО для миграции старых step-индексов в новую модель FSRS и не должна
 * применяться для планирования новых повторений. Для текущей логики
 * всегда ссылайтесь на [INTERVAL_LADDER_DAYS] отсюда.
 */
object SrsConstants {

    /**
     * Лестница дней для интервального повторения (единая для lesson- и vocab-SRS).
     *
     * Каждый элемент — количество дней до следующего показа на данном шаге
     * лестницы (по мере роста `stepIndex`). Перенесена без изменений из v1
     * `SpacedRepetitionConfig.INTERVAL_LADDER_DAYS` (единственный канонический
     * источник в v2).
     */
    val INTERVAL_LADDER_DAYS: List<Int> = listOf(1, 2, 4, 7, 10, 14, 20, 28, 42, 56)

    /**
     * Порог мастерства — количество показов/встреч, после которых карточка/урок
     * считаются освоенными. Совпадает с
     * [com.alexpo.grammermate.domain.TrainingConfig.MASTERY_THRESHOLD].
     */
    const val MASTERY_THRESHOLD = 150

    /**
     * Порог «увядания» (wilted): если retrievability R падает ниже этого значения,
     * материал считается нуждающимся в скором повторении.
     */
    const val WILTED_THRESHOLD = 0.5f

    /**
     * Порог «пропажи» (gone): если с последнего показа прошло более указанного
     * числа дней, материал считается забытым.
     */
    const val GONE_THRESHOLD_DAYS = 90

    /** Базовая стабильность памяти (в днях) при первичном расчёте. */
    const val BASE_STABILITY_DAYS = 0.9

    /** Множитель роста стабильности при успешном отзыве (экспоненциальный рост). */
    const val STABILITY_MULTIPLIER = 2.2

    /** Количество миллисекунд в одних сутках (24 часа). */
    const val DAY_MS = 86_400_000L

    /**
     * Минимальный step index, при котором vocab-слово считается «выученным».
     * Дублирует [com.alexpo.grammermate.domain.TrainingConfig.LEARNED_THRESHOLD]
     * — SRS-логике нужен собственный порог, не завязанный на слой тренировки.
     */
    const val LEARNED_THRESHOLD = 3
}
