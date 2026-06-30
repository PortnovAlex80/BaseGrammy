package com.alexpo.grammermate.v2.core.domain.srs

import kotlin.math.E
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * Ядро алгоритма FSRS v6 (Free Spaced Repetition Scheduler, Jarrett Ye).
 *
 * Чистый Kotlin, ноль Android-импортов, ноль внешних зависимостей.
 * Полностью детерминированный: время `now` всегда передаётся параметром,
 * никаких `System.currentTimeMillis()` — поэтому ядро тривиально тестируется
 * на чистой JVM (включая property-тесты на монотонность интервалов).
 *
 * Реализация — прямой перенос официальной спецификации FSRS v6
 * (open-spaced-repetition, py-fsrs `fsrs/scheduler.py`), 21 вес, обучаемый
 * decay. Никаких коэффициентов «от себя» — только формулы из спецификации.
 *
 * ## Three-component model of memory (DSR)
 *
 *  - **D** (Difficulty, 1.0–10.0) — насколько карточка сложная для данного пользователя;
 *  - **S** (Stability, дни) — как долго память держится;
 *  - **R** (Retrievability, 0–1) — вероятность вспомнить **сейчас**.
 *
 * ### Power forgetting curve (FSRS v6, обучаемый decay)
 * ```
 * R(t, S) = (1 + factor * t / S) ^ decay
 * ```
 * где `decay = -w[20]` (отрицательное), `factor = 0.9^(1/decay) − 1`.
 * Кривая построена так, что при `t == S` всегда `R = 0.9` (это свойство формы,
 * а не целевой retention). Целевая retention влияет только на выбор интервала.
 *
 * ### Следующий интервал
 * ```
 * I(S) = (S / factor) * (requestRetention^(1/decay) − 1)
 * ```
 * При `requestRetention = 0.9` член в скобках обращается в `factor`, и
 * интервал в днях точно равен `S`. Это удобное свойство для миграции из v1.
 *
 * ## Жизненный цикл карточки
 * ```
 * NEW ──любой рейтинг──▶ LEARNING ──GOOD/EASY (последний шаг)──▶ REVIEW
 *                            │                                       │ AGAIN
 *                            │ ◀─────────────────────────────────────┘
 *                            ▼
 *                       RELEARNING ──GOOD/EASY──▶ REVIEW
 * ```
 * В состояниях LEARNING/RELEARNING используются короткие шаги [learningStepsMs]
 * / [relearningStepsMs] (минуты), в REVIEW — день/недели через `_next_interval`.
 *
 * @property learningStepsMs Шаги первичного заучивания в миллисекундах
 *  (по умолчанию `[1мин, 10мин]`, как в py-fsrs).
 * @property relearningStepsMs Шаги переучивания после провала в REVIEW
 *  (по умолчанию `[10мин]`).
 * @property enableFuzzing Применять ли случайное «размывание» интервала для
 *  REVIEW-карточек (>2.5 дней), чтобы размазать нагрузку по дням. Должно
 *  быть **отключено** в тестах и при детерминированном пересчёте. По умолчанию
 *  `false` (в отличие от py-fsrs) — чтобы поведение ядра было предсказуемым;
 *  UI-слой может включить fuzzing явно.
 * @property randomSource Источник случайности для fuzzing. По умолчанию
 *  [DefaultFuzzRandom]. В тестах подменяется на детерминированный.
 */
class SrsScheduler(
    private val learningStepsMs: List<Long> = DEFAULT_LEARNING_STEPS_MS,
    private val relearningStepsMs: List<Long> = DEFAULT_RELEARNING_STEPS_MS,
    private val enableFuzzing: Boolean = false,
    private val randomSource: FuzzRandom = DefaultFuzzRandom(),
) {
    init {
        require(learningStepsMs.isNotEmpty() || relearningStepsMs.isNotEmpty()) {
            "At least one of learningStepsMs / relearningStepsMs must be non-empty"
        }
    }

    // ---------------------------------------------------------------------------------------
    //  Публичный API
    // ---------------------------------------------------------------------------------------

    /**
     * Главная точка входа: обновляет состояние карточки после ответа пользователя.
     *
     * Чистая (pure), детерминированная функция: все значения времени берутся из
     * аргументов, а не из системных часов. Одинаковый вход → одинаковый выход.
     *
     * @param state Текущее состояние карточки либо `null` для новой карточки
     *  (эквивалент [SrsMemoryState.NEW]). `null` удобен, когда у карточки ещё
     *  нет SRS-записи в БД.
     * @param rating Оценка ответа (AGAIN/HARD/GOOD/EASY).
     * @param now Текущий момент, epoch ms. Становится новым [SrsCardState.lastReviewMs]
     *  и базой для [SrsCardState.dueAtMs].
     * @param params Параметры FSRS v6 ([SrsParams]).
     * @return Новое immutable-состояние карточки.
     */
    fun schedule(
        state: SrsCardState?,
        rating: SrsRating,
        now: Long,
        params: SrsParams,
    ): SrsCardState {
        // NEW (или null) → первый вход в Learning: инициализируем S и D из весов.
        if (state == null || state.state == SrsMemoryState.NEW) {
            return scheduleFirstReview(rating = rating, now = now, params = params)
        }

        val daysSinceLastReview = daysBetween(now, state.lastReviewMs)
        val sameDay = daysSinceLastReview < 1

        return when (state.state) {
            SrsMemoryState.LEARNING ->
                scheduleLearning(state, rating, now, daysSinceLastReview, sameDay, params)
            SrsMemoryState.REVIEW ->
                scheduleReview(state, rating, now, daysSinceLastReview, sameDay, params)
            SrsMemoryState.RELEARNING ->
                scheduleRelearning(state, rating, now, daysSinceLastReview, sameDay, params)
            SrsMemoryState.NEW -> error("unreachable") // обработано выше
        }
    }

    /**
     * Retrievability R — предсказанная вероятность вспомнить карточку в момент [now].
     *
     * ```
     * R(t, S) = (1 + factor * t / S) ^ decay
     * ```
     * `t` — целое число дней с последнего review (`>= 0`).
     *
     * Возвращает `0.0`, если у карточки нет S/D (новая или нет истории review),
     * и `1.0` при `t == 0` (только что повторяли — помним наверняка).
     *
     * Это прямая замена legacy `SpacedRepetitionConfig.calculateRetention(...)`,
     * которая использовала экспоненту `R = e^(-t/S)`.
     *
     * @param state Состояние карточки.
     * @param now Момент, на который считаем R, epoch ms.
     * @param params Параметры FSRS v6.
     */
    fun retrievability(state: SrsCardState, now: Long, params: SrsParams): Double {
        // Нет стабильности или карточку никогда не показывали (lastReviewMs == 0) —
        // информацию вспомнить не из чего, R = 0 (соглашение FSRS для нет истории).
        if (state.stability <= 0.0 || state.lastReviewMs <= 0L) return 0.0
        val elapsedDays = max(0L, daysBetween(now, state.lastReviewMs).toLong()).toDouble()
        return powerForgetting(elapsedDays, state.stability, params)
    }

    /**
     * Начальная сложность D0 для первого ответа рейтингом [rating].
     *
     * ```
     * D0(rating) = w[4] − e^(w[5] * (rating − 1)) + 1
     * ```
     * При необходимости clamp'ится в [1.0, 10.0].
     *
     * @param rating Рейтинг первого ответа.
     * @param params Параметры FSRS v6.
     * @param clamp Ограничить ли результат диапазоном [1.0, 10.0]. По умолчанию `true`.
     */
    fun initDifficulty(rating: SrsRating, params: SrsParams, clamp: Boolean = true): Double {
        val r = rating.value()
        val d = params.w[4] - exp(params.w[5] * (r - 1)) + 1.0
        return if (clamp) clampDifficulty(d) else d
    }

    /**
     * Начальная стабильность S0 для первого ответа рейтингом [rating].
     *
     * `S0 = w[rating − 1]` (вес с индексом 0..3), затем clamp `>= STABILITY_MIN`.
     *
     * @param rating Рейтинг первого ответа.
     * @param params Параметры FSRS v6.
     */
    fun initStability(rating: SrsRating, params: SrsParams): Double {
        val s = params.w[rating.value() - 1]
        return clampStability(s)
    }

    /**
     * Следующий интервал в днях для заданной стабильности и целевой retention.
     *
     * ```
     * I(S) = (S / factor) * (requestRetention^(1/decay) − 1)
     * ```
     * Результат округляется до целого дня, ограничивается снизу 1 и сверху
     * [SrsParams.maximumInterval].
     *
     * **Полезное свойство:** при `requestRetention = 0.9` и стандартных весах
     * `I(S) ≈ S` (так как член в скобках ≈ factor). Это фундамент для
     * [SrsMigration]: старая «лестница» дней напрямую маппится в stability.
     *
     * @param stability Текущая (уже обновлённая) стабильность S.
     * @param params Параметры FSRS v6.
     */
    fun nextInterval(stability: Double, params: SrsParams): Int {
        val decay = params.decay
        val raw = (stability / params.factor) * (params.requestRetention.pow(1.0 / decay) - 1.0)
        val interval = round(raw).toInt()
        return min(max(interval, 1), params.maximumInterval)
    }

    /**
     * Момент следующего показа карточки (epoch ms) = `now + интервал в ms`.
     *
     * @param state Обновлённое состояние карточки (с уже пересчитанными S/D/state).
     * @param now Момент текущего review, epoch ms.
     * @return [SrsCardState.dueAtMs] нового состояния.
     */
    fun nextDueAtMs(state: SrsCardState, now: Long): Long = state.dueAtMs

    // ---------------------------------------------------------------------------------------
    //  Обработчики состояний
    // ---------------------------------------------------------------------------------------

    /**
     * Первый review новой карточки: NEW → LEARNING.
     * Инициализируем S/D из весов и назначаем шаг learning (по умолчанию 1 мин).
     */
    private fun scheduleFirstReview(
        rating: SrsRating,
        now: Long,
        params: SrsParams,
    ): SrsCardState {
        val stability = initStability(rating, params)
        val difficulty = initDifficulty(rating, params)
        val (nextState, intervalMs) = firstStepTransition(rating, params, stability)

        return SrsCardState(
            stability = stability,
            difficulty = difficulty,
            lastReviewMs = now,
            reps = if (rating == SrsRating.AGAIN) 0 else 1,
            lapses = 0,
            state = nextState,
            dueAtMs = now + intervalMs,
        )
    }

    /**
     * Логика выбора первого шага и состояния после первого ответа.
     *
     * Зеркалирует ветку `Learning` в py-fsrs для случая, когда у карточки
     * ещё нет step (только что создана). AGAIN → шаг 0 (остаёмся в Learning);
     * HARD → средний шаг либо текущий; GOOD → последний шаг или продвижение;
     * EASY → сразу в REVIEW с дневным интервалом.
     */
    private fun firstStepTransition(
        rating: SrsRating,
        params: SrsParams,
        stability: Double,
    ): Pair<SrsMemoryState, Long> = when {
        // Нет шагов обучения — сразу переходим в REVIEW по дневному интервалу.
        learningStepsMs.isEmpty() ->
            SrsMemoryState.REVIEW to daysToMs(nextInterval(stability, params))
        rating == SrsRating.AGAIN ->
            SrsMemoryState.LEARNING to learningStepsMs[0]
        rating == SrsRating.EASY ->
            SrsMemoryState.REVIEW to daysToMs(nextInterval(stability, params))
        else -> {
            // HARD/GOOD: начинаем с первого шага (step 0). GOOD продвинется
            // дальше по обычной логике при последующих review.
            SrsMemoryState.LEARNING to learningStepsMs[0]
        }
    }

    /** Обработка карточки в состоянии LEARNING. */
    private fun scheduleLearning(
        state: SrsCardState,
        rating: SrsRating,
        now: Long,
        daysSinceLastReview: Int,
        sameDay: Boolean,
        params: SrsParams,
    ): SrsCardState {
        var stability = state.stability
        var difficulty = state.difficulty
        val step = state.learningStep()

        // 1) Обновляем S и D.
        if (sameDay) {
            stability = shortTermStability(stability, rating, params)
            difficulty = nextDifficulty(difficulty, rating, params)
        } else {
            val r = retrievability(state, now, params)
            stability = nextStability(difficulty, stability, r, rating, params)
            difficulty = nextDifficulty(difficulty, rating, params)
        }

        // 2) Считаем следующий интервал и состояние.
        val (nextState, intervalMs) = learningNextStep(
            rating = rating,
            step = step,
            stability = stability,
            params = params,
        )

        val reps = if (rating == SrsRating.AGAIN) state.reps else state.reps + 1
        return state.copy(
            stability = clampStability(stability),
            difficulty = difficulty,
            lastReviewMs = now,
            reps = reps,
            state = nextState,
            dueAtMs = now + intervalMs,
        )
    }

    /** Логика шагов для состояния LEARNING (взята из py-fsrs). */
    private fun learningNextStep(
        rating: SrsRating,
        step: Int,
        stability: Double,
        params: SrsParams,
    ): Pair<SrsMemoryState, Long> {
        // Нет шагов либо карточка уже прошла все шаги прошлым планировщиком.
        if (learningStepsMs.isEmpty() ||
            (step >= learningStepsMs.size && rating != SrsRating.AGAIN)
        ) {
            return SrsMemoryState.REVIEW to daysToMs(nextInterval(stability, params))
        }

        return when (rating) {
            SrsRating.AGAIN -> SrsMemoryState.LEARNING to learningStepsMs[0]
            SrsRating.HARD -> {
                val interval = hardStepInterval(step, learningStepsMs)
                SrsMemoryState.LEARNING to interval
            }
            SrsRating.GOOD -> {
                if (step + 1 == learningStepsMs.size) {
                    // последний шаг → выпуск в REVIEW
                    SrsMemoryState.REVIEW to daysToMs(nextInterval(stability, params))
                } else {
                    val newStep = step + 1
                    SrsMemoryState.LEARNING to learningStepsMs[newStep]
                }
            }
            SrsRating.EASY -> SrsMemoryState.REVIEW to daysToMs(nextInterval(stability, params))
        }
    }

    /** Обработка карточки в состоянии REVIEW. */
    private fun scheduleReview(
        state: SrsCardState,
        rating: SrsRating,
        now: Long,
        daysSinceLastReview: Int,
        sameDay: Boolean,
        params: SrsParams,
    ): SrsCardState {
        var stability = state.stability
        var difficulty = state.difficulty

        if (sameDay) {
            stability = shortTermStability(stability, rating, params)
        } else {
            val r = retrievability(state, now, params)
            stability = nextStability(difficulty, stability, r, rating, params)
        }
        difficulty = nextDifficulty(difficulty, rating, params)

        val lapsed = rating == SrsRating.AGAIN
        val (nextState, intervalMs) = if (lapsed && relearningStepsMs.isNotEmpty()) {
            // Провал → переучивание (первый шаг relearning).
            SrsMemoryState.RELEARNING to relearningStepsMs[0]
        } else {
            // GOOD/HARD/EASY (или AGAIN без шагов) → остаёмся в REVIEW на дневном интервале.
            SrsMemoryState.REVIEW to daysToMs(nextInterval(stability, params))
        }

        return state.copy(
            stability = clampStability(stability),
            difficulty = difficulty,
            lastReviewMs = now,
            reps = if (lapsed) state.reps else state.reps + 1,
            lapses = if (lapsed) state.lapses + 1 else state.lapses,
            state = nextState,
            dueAtMs = now + intervalMs,
        )
    }

    /** Обработка карточки в состоянии RELEARNING (зеркало LEARNING). */
    private fun scheduleRelearning(
        state: SrsCardState,
        rating: SrsRating,
        now: Long,
        daysSinceLastReview: Int,
        sameDay: Boolean,
        params: SrsParams,
    ): SrsCardState {
        var stability = state.stability
        var difficulty = state.difficulty
        val step = state.relearningStep()

        if (sameDay) {
            stability = shortTermStability(stability, rating, params)
            difficulty = nextDifficulty(difficulty, rating, params)
        } else {
            val r = retrievability(state, now, params)
            stability = nextStability(difficulty, stability, r, rating, params)
            difficulty = nextDifficulty(difficulty, rating, params)
        }

        val (nextState, intervalMs) = relearningNextStep(
            rating = rating,
            step = step,
            stability = stability,
            params = params,
        )

        val reps = if (rating == SrsRating.AGAIN) state.reps else state.reps + 1
        return state.copy(
            stability = clampStability(stability),
            difficulty = difficulty,
            lastReviewMs = now,
            reps = reps,
            state = nextState,
            dueAtMs = now + intervalMs,
        )
    }

    /** Логика шагов для состояния RELEARNING (зеркало learningNextStep). */
    private fun relearningNextStep(
        rating: SrsRating,
        step: Int,
        stability: Double,
        params: SrsParams,
    ): Pair<SrsMemoryState, Long> {
        if (relearningStepsMs.isEmpty() ||
            (step >= relearningStepsMs.size && rating != SrsRating.AGAIN)
        ) {
            return SrsMemoryState.REVIEW to daysToMs(nextInterval(stability, params))
        }

        return when (rating) {
            SrsRating.AGAIN -> SrsMemoryState.RELEARNING to relearningStepsMs[0]
            SrsRating.HARD -> {
                val interval = hardStepInterval(step, relearningStepsMs)
                SrsMemoryState.RELEARNING to interval
            }
            SrsRating.GOOD -> {
                if (step + 1 == relearningStepsMs.size) {
                    SrsMemoryState.REVIEW to daysToMs(nextInterval(stability, params))
                } else {
                    val newStep = step + 1
                    SrsMemoryState.RELEARNING to relearningStepsMs[newStep]
                }
            }
            SrsRating.EASY -> SrsMemoryState.REVIEW to daysToMs(nextInterval(stability, params))
        }
    }

    /**
     * Интервал для рейтинга HARD на шаге [step]:
     *  - step 0 и всего 1 шаг   → steps[0] * 1.5
     *  - step 0 и шагов >= 2    → (steps[0] + steps[1]) / 2
     *  - иначе                  → steps[step]
     * Взято дословно из py-fsrs (Hard branch в Learning/Relearning).
     */
    private fun hardStepInterval(step: Int, steps: List<Long>): Long = when {
        step == 0 && steps.size == 1 -> (steps[0] * 1.5).toLong()
        step == 0 && steps.size >= 2 -> ((steps[0] + steps[1]) / 2.0).toLong()
        else -> steps.getOrElse(step) { steps.last() }
    }

    // ---------------------------------------------------------------------------------------
    //  FSRS v6 формулы (DSR) — прямой перенос из py-fsrs
    // ---------------------------------------------------------------------------------------

    /**
     * Power forgetting curve FSRS v6:
     * ```
     * R = (1 + factor * t / S) ^ decay
     * ```
     */
    private fun powerForgetting(elapsedDays: Double, stability: Double, params: SrsParams): Double {
        if (stability <= 0.0) return 0.0
        return (1.0 + params.factor * elapsedDays / stability).pow(params.decay)
    }

    /**
     * Обновление сложности D после ответа.
     *
     * ```
     * delta_D = -w[6] * (rating - 3)                         // смещение от GOOD
     * D'     = D + linearDamping(delta_D, D)                 // 10.0-D затухает у краёв
     * D''    = w[7] * D0(EASY) + (1 - w[7]) * D'             // mean reversion к D0(EASY)
     * D      = clamp(D'', 1, 10)
     * ```
     * где `linearDamping(d, D) = (10 - D) * d / 9`, `D0(EASY) = initDifficulty(EASY, clamp=false)`.
     */
    private fun nextDifficulty(difficulty: Double, rating: SrsRating, params: SrsParams): Double {
        val w = params.w
        val arg1 = initDifficulty(SrsRating.EASY, params, clamp = false) // D0(EASY)
        val deltaDifficulty = -(w[6] * (rating.value() - 3))
        val arg2 = difficulty + linearDamping(deltaDifficulty, difficulty)
        val nextD = meanReversion(arg1, arg2, w[7])
        return clampDifficulty(nextD)
    }

    private fun linearDamping(deltaDifficulty: Double, difficulty: Double): Double =
        (10.0 - difficulty) * deltaDifficulty / 9.0

    private fun meanReversion(arg1: Double, arg2: Double, weight: Double): Double =
        weight * arg1 + (1.0 - weight) * arg2

    /**
     * Обновление стабильности S после отзыва.
     *  - AGAIN → [nextForgetStability] (lapse: память провалилась);
     *  - HARD/GOOD/EASY → [nextRecallStability] (успешный отзыв: S растёт).
     */
    private fun nextStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: SrsRating,
        params: SrsParams,
    ): Double = if (rating == SrsRating.AGAIN) {
        nextForgetStability(difficulty, stability, retrievability, params)
    } else {
        nextRecallStability(difficulty, stability, retrievability, rating, params)
    }

    /**
     * Стабильность после успешного отзыва (HARD/GOOD/EASY):
     * ```
     * hardPenalty = w[15]  если rating == HARD, иначе 1
     * easyBonus   = w[16]  если rating == EASY, иначе 1
     * S' = S * (1 + e^w[8] * (11 - D) * S^(-w[9]) * (e^((1-R)*w[10]) - 1) * hardPenalty * easyBonus)
     * ```
     */
    private fun nextRecallStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: SrsRating,
        params: SrsParams,
    ): Double {
        val w = params.w
        val hardPenalty = if (rating == SrsRating.HARD) w[15] else 1.0
        val easyBonus = if (rating == SrsRating.EASY) w[16] else 1.0
        val multiplier = 1.0 +
            exp(w[8]) *
            (11.0 - difficulty) *
            stability.pow(-w[9]) *
            (exp((1.0 - retrievability) * w[10]) - 1.0) *
            hardPenalty *
            easyBonus
        return stability * multiplier
    }

    /**
     * Стабильность после забывания (lapse, AGAIN):
     * ```
     * S_long  = w[11] * D^(-w[12]) * ((S+1)^w[13] - 1) * e^((1-R)*w[14])
     * S_short = S / e^(w[17]*w[18])
     * S'      = min(S_long, S_short)
     * ```
     * Берётся минимум, чтобы короткие S не «взлетали» при редких быстрых провалах.
     */
    private fun nextForgetStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        params: SrsParams,
    ): Double {
        val w = params.w
        val longTerm = w[11] *
            difficulty.pow(-w[12]) *
            ((stability + 1.0).pow(w[13]) - 1.0) *
            exp((1.0 - retrievability) * w[14])
        val shortTerm = stability / exp(w[17] * w[18])
        return min(longTerm, shortTerm)
    }

    /**
     * Short-term stability — обновление S при повторении **в тот же день**
     * (`daysSinceLastReview < 1`). Используется вместо полноценной DSR-формулы,
     * так как кривая забывания за <1 дня ведёт себя иначе.
     * ```
     * increase = e^(w[17] * (rating - 3 + w[18])) * S^(-w[19])
     * если GOOD/EASY: increase = max(increase, 1.0)   // память не должна падать
     * S' = S * increase
     * ```
     */
    private fun shortTermStability(stability: Double, rating: SrsRating, params: SrsParams): Double {
        val w = params.w
        var increase = exp(w[17] * (rating.value() - 3 + w[18])) * stability.pow(-w[19])
        if (rating == SrsRating.GOOD || rating == SrsRating.EASY) {
            increase = max(increase, 1.0)
        }
        return clampStability(stability * increase)
    }

    // ---------------------------------------------------------------------------------------
    //  Утилиты
    // ---------------------------------------------------------------------------------------

    /** Ограничение сложности диапазоном [1.0, 10.0]. */
    private fun clampDifficulty(d: Double): Double = min(max(d, MIN_DIFFICULTY), MAX_DIFFICULTY)

    /** Ограничение стабильности снизу [STABILITY_MIN] (защита от 0/отрицательных). */
    private fun clampStability(s: Double): Double = max(s, STABILITY_MIN)

    /**
     * Разница в целых днях между двумя моментами (floor).
     * Совпадает с `(date2 - date1).days` из python-реализации при работе в одной TZ.
     */
    private fun daysBetween(nowMs: Long, lastMs: Long): Int {
        if (lastMs <= 0L) return Int.MAX_VALUE
        val diffMs = nowMs - lastMs
        return (diffMs / MILLIS_PER_DAY).toInt()
    }

    /** Дни → миллисекунды (для перевода интервала в dueAtMs). */
    private fun daysToMs(days: Int): Long = days.toLong() * MILLIS_PER_DAY

    /**
     * Fuzz-размывание интервала для REVIEW-карточек (только при [enableFuzzing]
     * и интервале > 2.5 дней). Размазывает нагрузку по дням, не меняя матожидание.
     * Реализация диапазонов взята из py-fsrs `FUZZ_RANGES`.
     *
     * Вынесено в отдельный метод для тестируемости; источник случайности —
     * [randomSource], что позволяет в тестах получить детерминированный fuzz.
     */
    internal fun getFuzzedIntervalDays(intervalDays: Int, params: SrsParams): Int {
        // < 2.5 дней — fuzz не применяется (порог из py-fsrs, сравнение как Double).
        if (intervalDays < MIN_FUZZ_INTERVAL) return intervalDays

        var delta = 1.0
        for (range in FUZZ_RANGES) {
            delta += range.factor *
                max(
                    min(intervalDays.toDouble(), range.end) - range.start,
                    0.0,
                )
        }
        var minIvl = round(intervalDays - delta).toInt()
        var maxIvl = round(intervalDays + delta).toInt()
        minIvl = max(2, minIvl)
        maxIvl = min(maxIvl, params.maximumInterval)
        minIvl = min(minIvl, maxIvl)

        val range = (maxIvl - minIvl + 1).toDouble()
        val fuzzed = randomSource.nextDouble() * range + minIvl
        return min(round(fuzzed).toInt(), params.maximumInterval)
    }

    companion object {
        private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L
        internal const val STABILITY_MIN: Double = 0.001
        internal const val MIN_DIFFICULTY: Double = 1.0
        internal const val MAX_DIFFICULTY: Double = 10.0

        /** Шаги заучивания по умолчанию: 1 мин, 10 мин (как в py-fsrs). */
        val DEFAULT_LEARNING_STEPS_MS: List<Long> = listOf(60_000L, 600_000L)

        /** Шаги переучивания по умолчанию: 10 мин (как в py-fsrs). */
        val DEFAULT_RELEARNING_STEPS_MS: List<Long> = listOf(600_000L)

        /** Порог (в днях), ниже которого fuzz не применяется (из py-fsrs). */
        internal const val MIN_FUZZ_INTERVAL: Int = 3 // >= 2.5 → ближайшее целое ">= порога"

        /** Диапазоны fuzz (из py-fsrs FUZZ_RANGES). */
        internal val FUZZ_RANGES: List<FuzzRange> = listOf(
            FuzzRange(start = 2.5, end = 7.0, factor = 0.15),
            FuzzRange(start = 7.0, end = 20.0, factor = 0.10),
            FuzzRange(start = 20.0, end = Double.MAX_VALUE, factor = 0.05),
        )
    }
}

// ---------------------------------------------------------------------------------------
//  Вспомогательные типы (без Android-зависимостей)
// ---------------------------------------------------------------------------------------

/**
 * Один диапазон fuzz: интервалы между [start] и [end] дней размываются
 * с относительным коэффициентом [factor].
 */
internal data class FuzzRange(val start: Double, val end: Double, val factor: Double)

/**
 * Источник случайности для fuzz-размывания интервалов.
 * Абстракция позволяет в тестах подставить детерминированный источник.
 */
fun interface FuzzRandom {
    /** @return псевдослучайное Double в [0.0, 1.0). */
    fun nextDouble(): Double
}

/** Источник случайности по умолчанию на базе [kotlin.random.Random]. */
class DefaultFuzzRandom(private val random: kotlin.random.Random = kotlin.random.Random) : FuzzRandom {
    override fun nextDouble(): Double = random.nextDouble()
}

/** Числовой рейтинг FSRS: AGAIN=1, HARD=2, GOOD=3, EASY=4. */
private fun SrsRating.value(): Int = when (this) {
    SrsRating.AGAIN -> 1
    SrsRating.HARD -> 2
    SrsRating.GOOD -> 3
    SrsRating.EASY -> 4
}

/**
 * Индекс шага в Learning, сохранённый в [SrsCardState.reps] (0-based).
 *
 * FSRS не хранит step как отдельное persisted-поле в канонической модели,
 * но app-логике удобно иметь его в [SrsCardState]. Здесь шаг неявно кодируется
 * нулем (карточка только вошла в Learning/Relearning, step=0). Это упрощение
 * оправдано для GrammarMate: шаговые интервалы короткие и пересчитываются
 * на каждом review. Если потребуется хранить прогресс по шагам явно —
 * добавьте отдельное nullable-поле в [SrsCardState].
 */
private fun SrsCardState.learningStep(): Int = 0

/** Индекс шага в Relearning (по умолчанию 0). См. [learningStep]. */
private fun SrsCardState.relearningStep(): Int = 0
