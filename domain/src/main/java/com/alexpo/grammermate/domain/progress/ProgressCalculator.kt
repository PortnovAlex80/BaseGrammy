package com.alexpo.grammermate.domain.progress

import com.alexpo.grammermate.domain.model.Chapter
import com.alexpo.grammermate.domain.model.ChapterProgress
import com.alexpo.grammermate.domain.model.FlowerState
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.LessonMastery
import com.alexpo.grammermate.domain.model.PackFlowerVisual
import com.alexpo.grammermate.domain.srs.SrsConstants

/**
 * Чистый калькулятор прогресса по главе.
 *
 * Перенесено из v1
 * `app/legacy-src/java/com/alexpo/grammermate/feature/progress/ChapterProgressCalculator.kt`.
 *
 * Считает метрики главы (started/completed/lastAccessed) по mastery-состояниям её
 * уроков. Прогресс независим между главами. Ноль Android-зависимостей.
 */
object ChapterProgressCalculator {

    /**
     * Рассчитать прогресс главы по mastery-состояниям её уроков.
     *
     * Перенесено 1:1 из v1 `ChapterProgressCalculator.calculateChapterProgress`,
     * строки 38–88 (адаптировано: ключи карты — [LessonId] вместо v1 `String`;
     * [Chapter.lessonIds] — `List<LessonId>` вместо v1 `List<String>`; убран
     * вызов Android-логгера).
     *
     * Правила (перенесены из КОДА v1, не из KDoc — см. ниже):
     *  - started: `uniqueCardShows > 0`;
     *  - **completed: `completedAtMs != null`** (НЕ `intervalStepIndex >= 3` —
     *    в v1 KDoc строки 18–19 ошибочно описывал порог по intervalStepIndex;
     *    реальный код строки 66 использует `completedAtMs != null`. В v2 берём КОД);
     *  - lastAccessedMs: максимум lastShowDateMs по всем урокам главы.
     *
     * Краевые случаи: пустая глава → нули (lastAccessedMs=0); уроки без mastery
     * считаются не начатыми.
     *
     * @param chapter       глава.
     * @param masteryStates карта lessonId → [LessonMastery] для уроков пака.
     * @return [ChapterProgress] с метриками (totalLessons нужно выставить отдельно).
     */
    fun calculate(
        chapter: Chapter,
        masteryStates: Map<LessonId, LessonMastery>,
    ): ChapterProgress {
        if (chapter.lessonIds.isEmpty()) {
            return ChapterProgress(
                packId = chapter.packId,
                chapterId = chapter.id,
                lessonsStarted = 0,
                lessonsCompleted = 0,
                lastAccessedMs = 0L,
            )
        }

        var startedCount = 0
        var completedCount = 0
        var lastAccessedMs = 0L

        for (lessonId in chapter.lessonIds) {
            val mastery = masteryStates[lessonId]

            // started: показана хотя бы одна карточка.
            if (mastery != null && mastery.uniqueCardShows > 0) {
                startedCount++
            }

            // completed: ЯВНАЯ отметка завершения (КОД v1, не KDoc).
            val isCompleted = mastery?.completedAtMs != null
            if (isCompleted) {
                completedCount++
            }

            // Последняя активность в главе.
            if (mastery != null && mastery.lastShowDateMs > lastAccessedMs) {
                lastAccessedMs = mastery.lastShowDateMs
            }
        }

        return ChapterProgress(
            packId = chapter.packId,
            chapterId = chapter.id,
            lessonsStarted = startedCount,
            lessonsCompleted = completedCount,
            lastAccessedMs = lastAccessedMs,
        ).withTotalLessons(chapter.lessonIds.size)
    }
}

/**
 * Чистый калькулятор flower-состояния пака (агрегат по урокам).
 *
 * Перенесено из v1
 * `app/legacy-src/java/com/alexpo/grammermate/feature/progress/PackProgressCalculator.kt`.
 *
 * Агрегирует mastery уроков в метрики пака для тайла выбора пака. Ноль Android.
 * `nowMs` инъектируется (в v1 — wall-clock).
 */
object PackProgressCalculator {

    /**
     * Рассчитать flower-визуал пака из mastery-состояний уроков.
     *
     * Перенесено 1:1 из v1 `PackProgressCalculator.calculatePackFlower`, строки 60–150
     * (адаптировано: ключи — [LessonId] вместо `String`; `nowMs` параметром вместо
     * wall-clock; убран Android-логгер).
     *
     * Правила:
     *  - depth: среднее `(uniqueCardShows / MASTERY_THRESHOLD).coerceAtMost(1)` по
     *    начатым урокам;
     *  - health: [SpacedRepetitionFormulas.calculateHealthPercent] по worst-case
     *    recency (max lastShowDateMs) и среднему intervalStepIndex;
     *  - scale: `depth * health`, clamp [0.5, 1.0];
     *  - flowerState: [determineFlowerState].
     *  - completedLessons: `completedAtMs != null`.
     *
     * Краевые случаи: пустой пак / нет начатых уроков → SEED, depth=0, health=1.0.
     *
     * @param lessonIds      все ID уроков пака.
     * @param masteryStates  карта lessonId → [LessonMastery].
     * @param nowMs          текущее epoch-время (injectable).
     * @return [PackFlowerVisual] с агрегированными метриками.
     */
    fun calculatePackFlower(
        lessonIds: List<LessonId>,
        masteryStates: Map<LessonId, LessonMastery>,
        nowMs: Long,
    ): PackFlowerVisual {
        if (lessonIds.isEmpty()) {
            return PackFlowerVisual(
                flowerState = FlowerState.SEED,
                depth = 0f,
                healthPercent = 1.0f,
                scaleMultiplier = 0.5f,
                completedLessons = 0,
                totalLessons = 0,
            )
        }

        var startedLessonCount = 0
        var depthSum = 0.0
        var completedLessons = 0
        var maxLastShowDateMs = 0L
        var intervalStepSum = 0

        for (lessonId in lessonIds) {
            val mastery = masteryStates[lessonId]

            // completed: явная отметка.
            if (mastery?.completedAtMs != null) {
                completedLessons++
            }

            // Только начатые уроки дают вклад в depth/health.
            if (mastery != null && mastery.uniqueCardShows > 0) {
                startedLessonCount++
                val lessonDepth = mastery.uniqueCardShows.toFloat() /
                        SrsConstants.MASTERY_THRESHOLD.toFloat()
                depthSum += lessonDepth.coerceAtMost(1.0f)
                intervalStepSum += mastery.intervalStepIndex

                if (mastery.lastShowDateMs > maxLastShowDateMs) {
                    maxLastShowDateMs = mastery.lastShowDateMs
                }
            }
        }

        val totalLessons = lessonIds.size

        // Нет начатых уроков — SEED с полным здоровьем.
        if (startedLessonCount == 0) {
            return PackFlowerVisual(
                flowerState = FlowerState.SEED,
                depth = 0f,
                healthPercent = 1.0f,
                scaleMultiplier = 0.5f,
                completedLessons = completedLessons,
                totalLessons = totalLessons,
            )
        }

        // depth: средняя mastery по начатым урокам.
        val depth = (depthSum / startedLessonCount).toFloat().coerceIn(0f, 1f)

        // health: кривая Эббингауза по worst-case recency и среднему шагу.
        val daysSinceLastShow = daysSince(maxLastShowDateMs, nowMs)
        val avgIntervalStep = intervalStepSum.toFloat() / startedLessonCount
        val healthPercent = SpacedRepetitionFormulas.calculateHealthPercent(
            daysSinceLastShow,
            avgIntervalStep.toInt(),
        )

        val scaleMultiplier = (depth * healthPercent).coerceIn(0.5f, 1.0f)
        val flowerState = determineFlowerState(depth, healthPercent, daysSinceLastShow)

        return PackFlowerVisual(
            flowerState = flowerState,
            depth = depth,
            healthPercent = healthPercent,
            scaleMultiplier = scaleMultiplier,
            completedLessons = completedLessons,
            totalLessons = totalLessons,
        )
    }

    /**
     * Определить flower-state пака из depth, health и recency.
     *
     * Перенесено 1:1 из v1 `PackProgressCalculator.determineFlowerState`, строки 163–187.
     * Приоритет (первое совпадение): GONE → WILTED → WILTING → SEED/SPROUT/BLOOM по depth.
     * Внимание: здесь `healthPercent <= WILTED_THRESHOLD` (СТРОГО, без epsilon) —
     * это эталон, с которым унифицирован [FlowerCalculator.determineFlowerState] в v2.
     */
    private fun determineFlowerState(
        depth: Float,
        healthPercent: Float,
        daysSinceLastShow: Int,
    ): FlowerState {
        if (daysSinceLastShow > SrsConstants.GONE_THRESHOLD_DAYS) {
            return FlowerState.GONE
        }
        if (healthPercent <= SrsConstants.WILTED_THRESHOLD) {
            return FlowerState.WILTED
        }
        if (healthPercent < 1.0f) {
            return FlowerState.WILTING
        }
        return when {
            depth < 0.33f -> FlowerState.SEED
            depth < 0.66f -> FlowerState.SPROUT
            else -> FlowerState.BLOOM
        }
    }

    /** Дней с [dateMs] (БЕЗ +1, как в v1 PackProgressCalculator.daysSince, строки 195–199). */
    private fun daysSince(dateMs: Long, nowMs: Long): Int {
        if (dateMs <= 0L) return 0
        val elapsed = nowMs - dateMs
        return (elapsed / SrsConstants.DAY_MS).toInt().coerceAtLeast(0)
    }
}
