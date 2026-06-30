package com.alexpo.grammermate.v2.core.domain.srs

/**
 * Миграция с legacy-модели интервального повторения (GrammarMate v1) на FSRS v6.
 *
 * В v1 «прогресс» карточки описывался дискретным `intervalStepIndex` (0..9)
 * по фиксированной лестнице дней `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]`
 * (`SpacedRepetitionConfig.INTERVAL_LADDER_DAYS`), а retention считался
 * экспонентой `R = e^(-t/S)`. В v2 заменяем это непрерывной моделью DSR из FSRS.
 *
 * Ключевая идея миграции: при `requestRetention = 0.9` следующий интервал FSRS
 * почти точно равен stability S (см. [SrsScheduler.nextInterval]). Поэтому
 * старый «ожидаемый интервал в днях» можно напрямую трактовать как
 * приближённое значение S, а саму карточку поместить в состояние REVIEW.
 * Это сохраняет субъективный темп повторений пользователя при переходе.
 *
 * Все функции чистые (pure) и детерминированные — пригодны для тестов на JVM.
 */
object SrsMigration {

    /**
     * Лестница интервалов v1 в днях (`SpacedRepetitionConfig.INTERVAL_LADDER_DAYS`).
     * Дублировано здесь, чтобы модуль `srs` v2 не зависел от legacy-пакета
     * `com.alexpo.grammermate.data`.
     */
    val LEGACY_INTERVAL_LADDER_DAYS: List<Int> =
        listOf(1, 2, 4, 7, 10, 14, 20, 28, 42, 56)

    /**
     * Картирует legacy [intervalStepIndex] (0..9) в приближённое FSRS-состояние.
     *
     * Правила маппинга:
     *  - `stepIndex < 0`  → NEW (карточка не показывалась): нулевые счётчики, S/D = 0.
     *  - `stepIndex == 0` → LEARNING (только начали): S = лестница[0], D = среднее.
     *  - `stepIndex >= 1` → REVIEW: S ≈ лестница[stepIndex] (clamp по размеру лестницы),
     *    D = нейтральное среднее (5.0), reps ≈ stepIndex, due = «сейчас».
     *
     * `dueAtMs` и `lastReviewMs` = [now]: миграция не знает реальной даты прошлого
     * показа, поэтому считаем карточку «только что показанной». Первый же
     * ответ пользователя через [SrsScheduler.schedule] пересчитает S/D корректно.
     *
     * @param stepIndex Legacy `intervalStepIndex` (0..9, либо < 0 для новой).
     * @param now Текущий момент, epoch ms (для lastReview/due).
     * @return Приближённое FSRS-состояние для бесшовного продолжения повторений.
     */
    fun fromLegacyIntervalStep(stepIndex: Int, now: Long): SrsCardState {
        // <0 — новая карточка, не показывалась.
        if (stepIndex < 0) {
            return SrsCardState(
                stability = 0.0,
                difficulty = 0.0,
                lastReviewMs = 0L,
                reps = 0,
                lapses = 0,
                state = SrsMemoryState.NEW,
                dueAtMs = now,
            )
        }

        val clamped = stepIndex.coerceAtMost(LEGACY_INTERVAL_LADDER_DAYS.lastIndex)
        val intervalDays = LEGACY_INTERVAL_LADDER_DAYS[clamped].toDouble()

        // step 0 → ещё в первичном заучивании; >=1 → уже на длинных интервалах.
        val state = if (clamped == 0) SrsMemoryState.LEARNING else SrsMemoryState.REVIEW

        return SrsCardState(
            stability = intervalDays,           // S ≈ старый интервал (т.к. retention=0.9 → I≈S)
            difficulty = 5.0,                   // нейтральная сложность (1..10)
            lastReviewMs = now,
            reps = clamped,                     // приближённо: шаг ≈ число успешных повторений
            lapses = 0,                         // legacy не отслеживал провалы
            state = state,
            dueAtMs = now,                      // пересчитается первым review
        )
    }

    /**
     * Удобная перегрузка: миграция с дефолтным `now = 0` (карточка считается
     * «просроченной с начала эпохи» → её сразу покажут). Удобно для разовых
     * bulk-миграций в DataStore/БД без передачи времени.
     */
    fun fromLegacyIntervalStep(stepIndex: Int): SrsCardState =
        fromLegacyIntervalStep(stepIndex, now = 0L)
}
