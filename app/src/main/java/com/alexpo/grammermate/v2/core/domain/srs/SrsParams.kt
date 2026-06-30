package com.alexpo.grammermate.v2.core.domain.srs

import kotlin.math.pow

/**
 * Параметры планировщика FSRS v6 (Free Spaced Repetition Scheduler, Jarrett Ye).
 *
 * Чистый Kotlin: ноль Android-импортов, ноль внешних зависимостей.
 * Идеально тестируется на чистой JVM (все функции детерминированные).
 *
 * FSRS v6 описывается three-component model of memory (D, S, R):
 *  - D — Difficulty (сложность карточки, 1.0–10.0);
 *  - S — Stability (как долго держится память, в днях);
 *  - R — Retrievability (вероятность вспомнить в момент времени t).
 *
 * В отличие от ранних версий, FSRS v6 использует **обучаемый decay** —
 * 21-й вес `w[20]`, который задаёт форму power forgetting curve, а не
 * фиксированную константу, как в FSRS v5.
 *
 * Спецификация: https://github.com/open-spaced-repetition (py-fsrs / ts-fsrs).
 *
 * @property requestRetention Целевая retention — вероятность вспоминания,
 *  под которую подбирается следующий интервал. По умолчанию 0.9 (90 %).
 *  При `requestRetention == 0.9` следующий интервал в днях почти равен S
 *  (см. [SrsScheduler.nextInterval]).
 * @property maximumInterval Максимальный интервал между повторениями в днях.
 *  Интервалы ограничиваются сверху этим значением. По умолчанию 36500 (~100 лет).
 * @property w 21 вес модели FSRS v6. Индексы:
 *   - `w[0..3]`  — начальная стабильность S0 для рейтингов AGAIN/HARD/GOOD/EASY;
 *   - `w[4..5]`  — начальная сложность D0;
 *   - `w[6..7]`  — обновление сложности (delta + mean reversion);
 *   - `w[8..10]` — множитель стабильности при успешном отзыве;
 *   - `w[11..14]`— новая стабильность после забывания (lapse);
 *   - `w[15..16]`— штраф за HARD и бонус за EASY;
 *   - `w[17..19]`— short-term stability (повторения в тот же день);
 *   - `w[20]`    — обучаемый decay power forgetting curve (новинка v6).
 */
data class SrsParams(
    val requestRetention: Double = DEFAULT_REQUEST_RETENTION,
    val maximumInterval: Int = DEFAULT_MAXIMUM_INTERVAL,
    val w: List<Double> = DEFAULT_WEIGHTS,
) {
    init {
        require(requestRetention in 0.0..1.0) {
            "requestRetention must be in [0.0, 1.0], got $requestRetention"
        }
        require(maximumInterval >= 1) {
            "maximumInterval must be >= 1 day, got $maximumInterval"
        }
        require(w.size == WEIGHT_COUNT) {
            "FSRS v6 expects exactly $WEIGHT_COUNT weights, got ${w.size}"
        }
    }

    /**
     * Decay power forgetting curve.
     *
     * В FSRS v6 decay — обучаемый параметр (вес `w[20]`), хранится как
     * положительное число; в формулах используется со знаком минус:
     * `decay = -w[20]`. Таким образом decay < 0 (типичное значение −0.1542),
     * что даёт убывающую кривую забывания.
     */
    val decay: Double
        get() = -w[20]

    /**
     * Фактор формы кривой забывания: `FACTOR = 0.9^(1/decay) − 1`.
     *
     * Константа 0.9 здесь — НЕ целевая retention, а опорная точка модели:
     * кривая построена так, что при `t == S` (прошёл ровно один «период
     * стабильности») retrievability R = 0.9. Это встроено в форму кривой и
     * не зависит от [requestRetention] (последняя учитывается только при
     * выборе следующего интервала — см. [SrsScheduler.nextInterval]).
     */
    val factor: Double
        get() = 0.9.pow(1.0 / decay) - 1.0

    companion object {
        /** Целевая retention по умолчанию — 90 %. */
        const val DEFAULT_REQUEST_RETENTION: Double = 0.9

        /** Максимальный интервал по умолчанию, дней (~100 лет). */
        const val DEFAULT_MAXIMUM_INTERVAL: Int = 36500

        /** Количество весов модели FSRS v6. */
        const val WEIGHT_COUNT: Int = 21

        /**
         * Стандартное значение decay FSRS (положительная константа из спецификации).
         * В формулах используется как `-decay`, см. [decay].
         */
        const val DEFAULT_DECAY: Double = 0.1542

        /**
         * Дефолтные веса FSRS v6 из официальной спецификации open-spaced-repetition
         * (py-fsrs `DEFAULT_PARAMETERS`, ts-fsrs). 21 значение.
         *
         * Источник: https://github.com/open-spaced-repetition/py-fsrs (`fsrs/scheduler.py`).
         */
        val DEFAULT_WEIGHTS: List<Double> = listOf(
            0.212,   // w[0]  S0 (AGAIN)
            1.2931,  // w[1]  S0 (HARD)
            2.3065,  // w[2]  S0 (GOOD)
            8.2956,  // w[3]  S0 (EASY)
            6.4133,  // w[4]  базовая сложность D0
            0.8334,  // w[5]  наклон D0 по рейтингу
            3.0194,  // w[6]  delta difficulty
            0.001,   // w[7]  mean reversion к D0(EASY)
            1.8722,  // w[8]  множитель S при отзыве (база)
            0.1666,  // w[9]  затухание множителя по S
            0.796,   // w[10] влияние (1−R) на рост S
            1.4835,  // w[11] S после lapse (база)
            0.0614,  // w[12] влияние D на S после lapse
            0.2629,  // w[13] влияние S на S после lapse
            1.6483,  // w[14] влияние (1−R) на S после lapse
            0.6014,  // w[15] hard penalty
            1.8729,  // w[16] easy bonus
            0.5425,  // w[17] short-term stability (база)
            0.0912,  // w[18] short-term stability (сдвиг по рейтингу)
            0.0658,  // w[19] short-term stability (степень по S)
            DEFAULT_DECAY, // w[20] decay (обучаемый, новинка v6)
        )
    }
}
