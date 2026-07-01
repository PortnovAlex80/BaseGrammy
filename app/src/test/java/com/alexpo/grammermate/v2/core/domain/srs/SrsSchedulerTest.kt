package com.alexpo.grammermate.v2.core.domain.srs

import com.alexpo.grammermate.v2.core.domain.model.SrsRating
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Регрессионные тесты корректности FSRS v6 — доказывают, что [SrsScheduler]
 * реализует спецификацию (open-spaced-repetition, py-fsrs) без искажений.
 *
 * Все ключевые инварианты FSRS проверены численно (см. KDoc у каждого теста):
 *  - power forgetting curve R(t,S) построена так, что `R(S) ≈ 0.9`;
 *  - при `requestRetention = 0.9` следующий интервал в днях ≈ S;
 *  - stability монотонно растёт на успешных отзывах и падает на lapse;
 *  - порядок интервалов AGAIN < HARD < GOOD < EASY.
 *
 * Чистая JVM: [SrsScheduler] детерминирован (`now` — параметр), поэтому
 * никаких Robolectric / Android-зависимостей. Точность сравнений — 0.01..0.02,
 * достаточная для того, чтобы поймать искажение формулы, но не страдать от
 * ошибок float-округления между Kotlin и эталоном.
 */
class SrsSchedulerTest {

    private val scheduler = SrsScheduler()
    private val params = SrsParams() // дефолтные веса FSRS v6, retention=0.9

    /** 1 день в ms — для сдвига `now` при симуляции прошедшего времени. */
    private val dayMs: Long = 24L * 60L * 60L * 1000L

    // Эталонные значения, посчитанные независимо по формулам FSRS v6 (см. SrsParams DEFAULT_WEIGHTS).
    private val s0Again = 0.212
    private val s0Hard = 1.2931
    private val s0Good = 2.3065
    private val s0Easy = 8.2956

    // ===================================================================================
    //  retrievability — power forgetting curve
    // ===================================================================================

    /**
     * Ключевой инвариант формы кривой забывания FSRS:
     * при `t == S` (прошёл ровно один «период стабильности») `R = 0.9`.
     *
     * Это встроено в форму кривой `R = (1 + factor·t/S)^decay`, где
     * `factor = 0.9^(1/decay) − 1`, и НЕ зависит от requestRetention.
     * Доказательство: подставив `t = S`, получаем `(1 + factor)^decay = 0.9`.
     */
    @Test
    fun `retrievability at t equals S is 0_9 (curve construction invariant)`() {
        val now = 1_000_000_000L
        val s = 10.0
        val card = reviewCard(stability = s, lastReviewMs = now)

        // прошёл ровно S дней → R ≈ 0.9
        val r = scheduler.retrievability(card, now = now + (s.toLong() * dayMs), params)
        assertThat(r).isWithin(0.001).of(0.9)
    }

    /** Только что показанная карта помнится полностью: `R(t=0) = 1.0`. */
    @Test
    fun `retrievability at now equals lastReview is 1_0`() {
        val now = 1_000_000_000L
        val card = reviewCard(stability = 10.0, lastReviewMs = now)

        val r = scheduler.retrievability(card, now = now, params)
        assertThat(r).isWithin(0.001).of(1.0)
    }

    /** Кратко после показа память почти полная (1 день при S=10 → R≈0.986). */
    @Test
    fun `retrievability shortly after review is near 1`() {
        val now = 1_000_000_000L
        val card = reviewCard(stability = 10.0, lastReviewMs = now)

        val r = scheduler.retrievability(card, now = now + dayMs, params)
        // Эталон по формуле: (1 + 0.98035·1/10)^(-0.1542) ≈ 0.9857
        assertThat(r).isWithin(0.02).of(0.986)
    }

    /**
     * Retrievability монотонно убывает со временем: чем больше прошло дней,
     * тем меньше вероятность вспомнить.
     */
    @Test
    fun `retrievability monotonically decreases over time`() {
        val now = 1_000_000_000L
        val card = reviewCard(stability = 10.0, lastReviewMs = now)

        val rs = (0..50 step 5).map { days ->
            scheduler.retrievability(card, now = now + days.toLong() * dayMs, params)
        }
        // каждый следующий <= предыдущего
        for (i in 1 until rs.size) {
            assertThat(rs[i]).isLessThan(rs[i - 1])
        }
    }

    /** Карточка без истории (lastReviewMs == 0) → R = 0 (нечего вспоминать). */
    @Test
    fun `retrievability returns 0 for card with no review history`() {
        val card = reviewCard(stability = 10.0, lastReviewMs = 0L)
        val r = scheduler.retrievability(card, now = 1_000_000_000L, params)
        assertThat(r).isEqualTo(0.0)
    }

    /** Карточка с нулевой/отрицательной стабильностью → R = 0 (защита). */
    @Test
    fun `retrievability returns 0 for non-positive stability`() {
        val now = 1_000_000_000L
        val zero = reviewCard(stability = 0.0, lastReviewMs = now)
        val neg = reviewCard(stability = -1.0, lastReviewMs = now)
        assertThat(scheduler.retrievability(zero, now, params)).isEqualTo(0.0)
        assertThat(scheduler.retrievability(neg, now, params)).isEqualTo(0.0)
    }

    /** `now` раньше последнего review (просмотр «в прошлое») → elapsedDays clamp'ится в 0, R≈1. */
    @Test
    fun `retrievability before lastReview clamps elapsed to zero`() {
        val lastReview = 1_000_000_000L
        val card = reviewCard(stability = 10.0, lastReviewMs = lastReview)
        // now на 5 дней в прошлом
        val r = scheduler.retrievability(card, now = lastReview - 5 * dayMs, params)
        assertThat(r).isWithin(0.001).of(1.0)
    }

    // ===================================================================================
    //  nextInterval — I(S) при requestRetention = 0.9
    // ===================================================================================

    /**
     * Ключевое свойство FSRS для миграции: при `requestRetention = 0.9`
     * интервал в днях (с округлением) равен S.
     */
    @Test
    fun `nextInterval equals stability when requestRetention is 0_9`() {
        for (s in listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0)) {
            val interval = scheduler.nextInterval(s, params)
            assertThat(interval).isEqualTo(s.toInt())
        }
    }

    /** Интервал всегда >= 1 день (clamp снизу). */
    @Test
    fun `nextInterval is at least 1 day`() {
        assertThat(scheduler.nextInterval(0.0, params)).isAtLeast(1)
        assertThat(scheduler.nextInterval(0.001, params)).isAtLeast(1)
        assertThat(scheduler.nextInterval(-5.0, params)).isAtLeast(1)
    }

    /** Интервал не превосходит maximumInterval. */
    @Test
    fun `nextInterval is clamped to maximumInterval`() {
        val huge = scheduler.nextInterval(1_000_000.0, params) // S = миллион дней
        assertThat(huge).isEqualTo(params.maximumInterval)
    }

    /** Более высокая целевая retention → более короткий интервал (повторяем чаще). */
    @Test
    fun `nextInterval shrinks as requestRetention rises`() {
        val s = 20.0
        val i90 = scheduler.nextInterval(s, params.copy(requestRetention = 0.9))
        val i95 = scheduler.nextInterval(s, params.copy(requestRetention = 0.95))
        assertThat(i95).isLessThan(i90)
    }

    // ===================================================================================
    //  schedule — первый review новой карточки (NEW → LEARNING/REVIEW)
    // ===================================================================================

    /** `schedule(null, GOOD, …)` создаёт карточку: NEW → LEARNING с S0=w[2]. */
    @Test
    fun `schedule new card with GOOD initializes learning state and S0`() {
        val now = 1_000_000_000L
        val card = scheduler.schedule(state = null, rating = SrsRating.GOOD, now = now, params = params)

        assertThat(card.state).isEqualTo(SrsMemoryState.LEARNING)
        assertThat(card.stability).isWithin(0.0001).of(s0Good)
        // D0(GOOD) = w[4] - e^(w[5]·(3-1)) + 1 ≈ 2.1181
        assertThat(card.difficulty).isWithin(0.001).of(2.1181)
        assertThat(card.reps).isEqualTo(1)
        assertThat(card.lapses).isEqualTo(0)
        assertThat(card.lastReviewMs).isEqualTo(now)
        assertThat(card.dueAtMs).isEqualTo(now + SrsScheduler.DEFAULT_LEARNING_STEPS_MS[0])
    }

    /** `schedule(null, EASY, …)` — EASY на новой карте сразу выпускает в REVIEW. */
    @Test
    fun `schedule new card with EASY graduates immediately to REVIEW`() {
        val now = 1_000_000_000L
        val card = scheduler.schedule(state = null, rating = SrsRating.EASY, now = now, params = params)

        assertThat(card.state).isEqualTo(SrsMemoryState.REVIEW)
        assertThat(card.stability).isWithin(0.0001).of(s0Easy)
        // due = now + nextInterval(S0_EASY) дней ≈ 8 дней
        assertThat(card.dueAtMs).isEqualTo(now + 8L * dayMs)
    }

    /** AGAIN на новой карте: остаёмся в LEARNING, reps не инкрементируется. */
    @Test
    fun `schedule new card with AGAIN stays in learning with zero reps`() {
        val now = 1_000_000_000L
        val card = scheduler.schedule(state = null, rating = SrsRating.AGAIN, now = now, params = params)

        assertThat(card.state).isEqualTo(SrsMemoryState.LEARNING)
        assertThat(card.stability).isWithin(0.0001).of(s0Again)
        assertThat(card.reps).isEqualTo(0) // AGAIN не считается успешным отзывом
        assertThat(card.dueAtMs).isEqualTo(now + SrsScheduler.DEFAULT_LEARNING_STEPS_MS[0])
    }

    /** HARD на новой карте → LEARNING, S0=w[1]. */
    @Test
    fun `schedule new card with HARD enters learning`() {
        val now = 1_000_000_000L
        val card = scheduler.schedule(state = null, rating = SrsRating.HARD, now = now, params = params)

        assertThat(card.state).isEqualTo(SrsMemoryState.LEARNING)
        assertThat(card.stability).isWithin(0.0001).of(s0Hard)
        assertThat(card.reps).isEqualTo(1)
    }

    /** Карточка в состоянии NEW (не null) обрабатывается как первый review. */
    @Test
    fun `schedule with NEW state card behaves like first review`() {
        val now = 1_000_000_000L
        val fresh = SrsCardState(
            stability = 0.0, difficulty = 0.0, lastReviewMs = 0L,
            reps = 0, lapses = 0, state = SrsMemoryState.NEW, dueAtMs = 0L,
        )
        val card = scheduler.schedule(state = fresh, rating = SrsRating.GOOD, now = now, params = params)
        assertThat(card.state).isEqualTo(SrsMemoryState.LEARNING)
        assertThat(card.stability).isWithin(0.0001).of(s0Good)
    }

    // ===================================================================================
    //  schedule — рост stability на последовательных GOOD
    // ===================================================================================

    /**
     * Три последовательных GOOD-ответа в состоянии REVIEW (с продвижением `now`
     * на расписанный интервал в днях) → stability монотонно растёт: S1 < S2 < S3.
     *
     * Полная DSR-формула роста S применяется только в REVIEW при `daysSince >= 1`
     * (в LEARNING/RELEARNING шаги — минуты, всегда sameDay → shortTermStability).
     * Поэтому проверку монотонного роста stability корректно проводить на
     * REVIEW-карточке, продвигая время на величину scheduled-интервала.
     *
     * Эталон по формулам (S=10, D=5): 10 → ~32 → ещё больше.
     */
    @Test
    fun `consecutive GOOD answers in REVIEW grow stability monotonically`() {
        val now = 1_000_000_000L
        var card = reviewCard(stability = 10.0, difficulty = 5.0, lastReviewMs = now, state = SrsMemoryState.REVIEW)
        val s1 = card.stability

        // 1-й GOOD: отвечаем через nextInterval(10)=10 дней
        val interval1 = scheduler.nextInterval(card.stability, params)
        card = scheduler.schedule(card, SrsRating.GOOD, now + interval1.toLong() * dayMs, params)
        val s2 = card.stability

        // 2-й GOOD: отвечаем через новый scheduled интервал
        val interval2 = scheduler.nextInterval(card.stability, params)
        card = scheduler.schedule(card, SrsRating.GOOD, now + (interval1 + interval2).toLong() * dayMs, params)
        val s3 = card.stability

        assertThat(s1).isLessThan(s2)
        assertThat(s2).isLessThan(s3)
    }

    /**
     * В LEARNING short-term формула: GOOD не уменьшает S (clamp `max(increase,1.0)`),
     * но и не обязана растить его в тот же день (шаги в минутах → всегда sameDay).
     * Проверяем, что S остаётся стабильным/не падает при same-day GOOD в LEARNING.
     */
    @Test
    fun `same-day GOOD in LEARNING does not decrease stability`() {
        val now = 1_000_000_000L
        val first = scheduler.schedule(null, SrsRating.GOOD, now, params) // NEW → LEARNING
        val s1 = first.stability
        val second = scheduler.schedule(first, SrsRating.GOOD, now, params) // same day
        assertThat(second.stability).isAtLeast(s1 * 0.999) // не упало
    }

    // ===================================================================================
    //  schedule — AGAIN в REVIEW = lapse
    // ===================================================================================

    /**
     * AGAIN в состоянии REVIEW → переход в RELEARNING, lapses+1, reps не растёт,
     * stability падает (S_after < S_before).
     */
    @Test
    fun `AGAIN in REVIEW causes lapse to RELEARNING with stability drop`() {
        val now = 1_000_000_000L
        val before = reviewCard(
            stability = 20.0,
            difficulty = 5.0,
            lastReviewMs = now,
            reps = 5,
            lapses = 0,
            state = SrsMemoryState.REVIEW,
        )
        // прошёл интервал в один «период стабильности»
        val after = scheduler.schedule(
            state = before,
            rating = SrsRating.AGAIN,
            now = now + 20L * dayMs,
            params = params,
        )

        assertThat(after.state).isEqualTo(SrsMemoryState.RELEARNING)
        assertThat(after.lapses).isEqualTo(1)
        assertThat(after.reps).isEqualTo(5) // AGAIN не инкрементирует reps
        assertThat(after.stability).isLessThan(before.stability)
    }

    // ===================================================================================
    //  schedule — порядок интервалов EASY > GOOD > HARD > AGAIN
    // ===================================================================================

    /**
     * Из одного и того же REVIEW-состояния интервалы упорядочены:
     * AGAIN < HARD < GOOD < EASY. Это фундаментальное свойство SRS —
     * лучший ответ → больший следующий интервал.
     */
    @Test
    fun `intervals ordered AGAIN less than HARD less than GOOD less than EASY from REVIEW`() {
        val now = 1_000_000_000L
        val base = reviewCard(
            stability = 10.0, difficulty = 5.0,
            lastReviewMs = now, state = SrsMemoryState.REVIEW,
        )
        val t = 10L * dayMs // прошло 10 дней

        fun dueDays(rating: SrsRating): Long {
            val next = scheduler.schedule(base, rating, now + t, params)
            return (next.dueAtMs - now - t) / dayMs
        }

        val iAgain = dueDays(SrsRating.AGAIN)
        val iHard = dueDays(SrsRating.HARD)
        val iGood = dueDays(SrsRating.GOOD)
        val iEasy = dueDays(SrsRating.EASY)

        assertThat(iAgain).isLessThan(iHard)
        assertThat(iHard).isLessThan(iGood)
        assertThat(iGood).isLessThan(iEasy)
    }

    // ===================================================================================
    //  difficulty clamp + поведение D
    // ===================================================================================

    /** D всегда остаётся в диапазоне [1.0, 10.0] после любого ответа. */
    @Test
    fun `difficulty stays within 1 to 10 after answers`() {
        val now = 1_000_000_000L
        for (rating in SrsRating.values()) {
            val card = scheduler.schedule(null, rating, now, params)
            assertThat(card.difficulty).isAtLeast(1.0)
            assertThat(card.difficulty).isAtMost(10.0)
        }
        // много AGAIN подряд не должно вылететь за 10
        var card = reviewCard(stability = 5.0, difficulty = 9.9, lastReviewMs = now, state = SrsMemoryState.REVIEW)
        repeat(10) {
            card = scheduler.schedule(card, SrsRating.AGAIN, card.dueAtMs, params)
            assertThat(card.difficulty).isAtMost(10.0)
            assertThat(card.difficulty).isAtLeast(1.0)
        }
    }

    /** AGAIN повышает сложность, EASY понижает (от общего центра). */
    @Test
    fun `AGAIN increases difficulty and EASY decreases difficulty from neutral`() {
        val now = 1_000_000_000L
        val neutral = reviewCard(stability = 10.0, difficulty = 5.0, lastReviewMs = now, state = SrsMemoryState.REVIEW)
        val afterAgain = scheduler.schedule(neutral, SrsRating.AGAIN, now + 10 * dayMs, params)
        val afterEasy = scheduler.schedule(neutral, SrsRating.EASY, now + 10 * dayMs, params)
        assertThat(afterAgain.difficulty).isGreaterThan(5.0)
        assertThat(afterEasy.difficulty).isLessThan(5.0)
    }

    // ===================================================================================
    //  reps / dueAtMs инварианты
    // ===================================================================================

    /** reps инкрементируется на любом ответе, кроме AGAIN. */
    @Test
    fun `reps increments on non-AGAIN answers`() {
        val now = 1_000_000_000L
        for (rating in listOf(SrsRating.HARD, SrsRating.GOOD, SrsRating.EASY)) {
            val first = scheduler.schedule(null, rating, now, params)
            assertThat(first.reps).isEqualTo(1)
        }
        assertThat(scheduler.schedule(null, SrsRating.AGAIN, now, params).reps).isEqualTo(0)
    }

    /** dueAtMs = lastReviewMs + nextInterval·DAY_MS для REVIEW-карточек. */
    @Test
    fun `dueAtMs equals lastReviewMs plus interval days for REVIEW`() {
        val now = 1_000_000_000L
        val card = scheduler.schedule(null, SrsRating.EASY, now, params) // EASY → сразу REVIEW
        assertThat(card.state).isEqualTo(SrsMemoryState.REVIEW)
        val expectedIntervalDays = scheduler.nextInterval(card.stability, params).toLong()
        assertThat(card.dueAtMs).isEqualTo(card.lastReviewMs + expectedIntervalDays * dayMs)
    }

    // ===================================================================================
    //  EDGE CASES
    // ===================================================================================

    /** Очень большой reps / stability — никаких NaN/Infinity. */
    @Test
    fun `schedule with very high reps does not produce NaN or Infinity`() {
        val now = 1_000_000_000L
        val veteran = reviewCard(
            stability = 500.0,
            difficulty = 5.0,
            lastReviewMs = now,
            reps = 100,
            lapses = 5,
            state = SrsMemoryState.REVIEW,
        )
        val after = scheduler.schedule(veteran, SrsRating.GOOD, now + 100 * dayMs, params)
        assertThat(after.stability).isNotNaN()
        assertThat(after.stability).isLessThan(Double.POSITIVE_INFINITY)
        assertThat(after.difficulty).isNotNaN()
        assertThat(after.reps).isEqualTo(101)
    }

    /** Очень большая stability (10000 дней) тоже не ломает математику. */
    @Test
    fun `schedule with very large stability stays finite`() {
        val now = 1_000_000_000L
        val huge = reviewCard(stability = 10_000.0, difficulty = 5.0, lastReviewMs = now, state = SrsMemoryState.REVIEW)
        val after = scheduler.schedule(huge, SrsRating.GOOD, now + 365 * dayMs, params)
        assertThat(after.stability).isNotNaN()
        assertThat(after.dueAtMs).isGreaterThan(now)
    }

    /** now == lastReviewMs (повтор в ту же секунду) — не падает, stability в разумных пределах. */
    @Test
    fun `schedule same-day review does not crash`() {
        val now = 1_000_000_000L
        val card = reviewCard(stability = 5.0, difficulty = 5.0, lastReviewMs = now, state = SrsMemoryState.REVIEW)
        val after = scheduler.schedule(card, SrsRating.GOOD, now, params) // same day
        assertThat(after.stability).isNotNaN()
        assertThat(after.stability).isGreaterThan(0.0)
    }

    // ===================================================================================
    //  Покрытие всех 4 рейтингов на каждом состоянии
    // ===================================================================================

    /** Матрица: каждый рейтинг на каждом состоянии — не падает, даёт валидный SrsCardState. */
    @Test
    fun `all ratings on all states produce valid card state`() {
        val now = 1_000_000_000L
        val states = listOf(
            SrsMemoryState.LEARNING to reviewCard(stability = 2.0, difficulty = 5.0, lastReviewMs = now, state = SrsMemoryState.LEARNING),
            SrsMemoryState.REVIEW to reviewCard(stability = 10.0, difficulty = 5.0, lastReviewMs = now, state = SrsMemoryState.REVIEW),
            SrsMemoryState.RELEARNING to reviewCard(stability = 2.0, difficulty = 7.0, lastReviewMs = now, state = SrsMemoryState.RELEARNING),
        )
        for ((_, card) in states) {
            for (rating in SrsRating.values()) {
                val after = scheduler.schedule(card, rating, now + 5 * dayMs, params)
                assertThat(after.stability).isGreaterThan(0.0)
                assertThat(after.stability).isNotNaN()
                assertThat(after.difficulty).isAtLeast(1.0)
                assertThat(after.difficulty).isAtMost(10.0)
                assertThat(after.dueAtMs).isAtLeast(now) // due не в прошлом относительно старого now
                assertThat(after.lastReviewMs).isEqualTo(now + 5 * dayMs)
            }
        }
    }

    /**
     * Поведение GOOD в LEARNING: карточка остаётся в LEARNING.
     *
     * В [SrsScheduler] индекс шага в Learning всегда 0 (см. KDoc `learningStep()`),
     * поэтому GOOD (`step+1=1 != steps.size=2`) НЕ выпускает карточку в REVIEW —
     * продвижение по шагам здесь упрощено. Выпуск в REVIEW происходит только по
     * EASY. Это задокументированное дизайнерское упрощение GrammarMate.
     */
    @Test
    fun `GOOD in LEARNING stays in LEARNING (step always 0)`() {
        val now = 1_000_000_000L
        val card = scheduler.schedule(null, SrsRating.GOOD, now, params) // NEW → LEARNING
        val afterGood = scheduler.schedule(card, SrsRating.GOOD, now, params)
        assertThat(afterGood.state).isEqualTo(SrsMemoryState.LEARNING)
        // только EASY выпускает (см. отдельный тест EASY graduates)
    }

    /** EASY в любом обучающем состоянии выпускает карточку в REVIEW немедленно. */
    @Test
    fun `EASY in LEARNING graduates to REVIEW immediately`() {
        val now = 1_000_000_000L
        val learning = scheduler.schedule(null, SrsRating.GOOD, now, params) // LEARNING
        val after = scheduler.schedule(learning, SrsRating.EASY, now, params)
        assertThat(after.state).isEqualTo(SrsMemoryState.REVIEW)
    }

    /** GOOD в RELEARNING выпускает обратно в REVIEW. */
    @Test
    fun `GOOD in RELEARNING returns card to REVIEW`() {
        val now = 1_000_000_000L
        // получим RELEARNING через lapse: REVIEW → AGAIN
        val review = reviewCard(stability = 10.0, difficulty = 5.0, lastReviewMs = now, state = SrsMemoryState.REVIEW)
        val relearning = scheduler.schedule(review, SrsRating.AGAIN, now + 10 * dayMs, params)
        assertThat(relearning.state).isEqualTo(SrsMemoryState.RELEARNING)
        // теперь GOOD продвигает (relearningSteps.size==1 → step0+1==1 → выпуск)
        val graduated = scheduler.schedule(relearning, SrsRating.GOOD, relearning.dueAtMs, params)
        assertThat(graduated.state).isEqualTo(SrsMemoryState.REVIEW)
    }

    // ===================================================================================
    //  Дублирующая проверка числовых эталонов (initStability / initDifficulty)
    // ===================================================================================

    /** initStability даёт ровно веса w[0..3] для соответствующих рейтингов. */
    @Test
    fun `initStability returns weights w0_w3 for each rating`() {
        assertThat(scheduler.initStability(SrsRating.AGAIN, params)).isWithin(1e-9).of(s0Again)
        assertThat(scheduler.initStability(SrsRating.HARD, params)).isWithin(1e-9).of(s0Hard)
        assertThat(scheduler.initStability(SrsRating.GOOD, params)).isWithin(1e-9).of(s0Good)
        assertThat(scheduler.initStability(SrsRating.EASY, params)).isWithin(1e-9).of(s0Easy)
    }

    /** initDifficulty: AGAIN самый сложный, EASY самый лёгкий; все в [1,10]. */
    @Test
    fun `initDifficulty is monotonic by rating and within range`() {
        val dAgain = scheduler.initDifficulty(SrsRating.AGAIN, params)
        val dHard = scheduler.initDifficulty(SrsRating.HARD, params)
        val dGood = scheduler.initDifficulty(SrsRating.GOOD, params)
        val dEasy = scheduler.initDifficulty(SrsRating.EASY, params)
        // Эталоны: 6.4133 / 5.1122 / 2.1181 / 1.0
        assertThat(dAgain).isWithin(0.001).of(6.4133)
        assertThat(dHard).isWithin(0.001).of(5.1122)
        assertThat(dGood).isWithin(0.001).of(2.1181)
        assertThat(dEasy).isWithin(0.001).of(1.0)
        assertThat(dAgain).isGreaterThan(dHard)
        assertThat(dHard).isGreaterThan(dGood)
        assertThat(dGood).isGreaterThan(dEasy)
    }

    // ===================================================================================
    //  Хелперы
    // ===================================================================================

    /** Сборка REVIEW/учебной карточки с заданными полями для тестов. */
    private fun reviewCard(
        stability: Double,
        difficulty: Double = 5.0,
        lastReviewMs: Long = 1_000_000_000L,
        reps: Int = 3,
        lapses: Int = 0,
        state: SrsMemoryState = SrsMemoryState.REVIEW,
        dueAtMs: Long = lastReviewMs + dayMs,
    ): SrsCardState = SrsCardState(
        stability = stability,
        difficulty = difficulty,
        lastReviewMs = lastReviewMs,
        reps = reps,
        lapses = lapses,
        state = state,
        dueAtMs = dueAtMs,
    )
}
