package com.alexpo.grammermate.v2.core.domain.progress

import com.alexpo.grammermate.v2.core.domain.model.StreakData
import com.alexpo.grammermate.v2.core.domain.srs.SrsConstants

/**
 * Чистый калькулятор обновления серии дней (streak) после завершения активности.
 *
 * Перенесено из v1
 * `app/legacy-src/java/com/alexpo/grammermate/data/StreakStore.kt`,
 * метод `checkAndUpdateStreak` (строки 220–255) — только чистая логика, БЕЗ
 * stateful-обёртки над сторами/IO. Ноль Android-зависимостей (в v1 сравнение
 * дней делалось через `java.util.Calendar`; в v2 — детерминированно через
 * `epochMs / DAY_MS`, что тестируемо на чистой JVM).
 *
 * **ВАЖНОЕ РАСХОЖДЕНИЕ С v1 (унификация): reset на 1, не на 0.**
 * В v1 было ДВЕ разные семантики сброса:
 *  - `recordSubLessonCompletion` (через `checkAndUpdateStreak`) при пропуске ≥1 дня
 *    ставил `currentStreak = 1` (ветка `else → 1`, строка 117);
 *  - `getCurrentStreak` при `daysSinceLastCompletion > 1` сбрасывал в `0`
 *    (строка 269: `current.copy(currentStreak = 0)`).
 * Это противоречие (1 vs 0) — баг v1. В v2 УНИФИЦИРОВАНО: **при разрыве ≥2 дней
 * серия сбрасывается на 1** (новая серия началась с этого завершения). День
 * окончания = `epochMs / DAY_MS`.
 */
object StreakCalculator {

    /**
     * Вычислить следующее состояние streak после завершения активности в [nowMs].
     *
     * Чистая функция (перенесена из v1 `checkAndUpdateStreak` + применения полей
     * из `recordSubLessonCompletion`, строки 108–129). Сравнение дней —
     * `epochMs / [SrsConstants.DAY_MS]` (детерминированно, без Calendar).
     *
     * Правила:
     *  - lastCompletionDateMs == null → currentStreak = 1 (первое завершение);
     *  - та же день (diff == 0) → currentStreak без изменений;
     *  - вчерашний день (diff == 1) → currentStreak + 1 (продолжение серии);
     *  - разрыв ≥ 2 дней (diff >= 2) → currentStreak = 1 (сброс, см. KDoc класса);
     *  - longestStreak = max(longestStreak, newCurrentStreak);
     *  - lastCompletionDateMs = nowMs; totalSubLessonsCompleted += 1.
     *
     * Поля `completedTypesToday`/`todayFireCount`/`lastFireDateMs` сохраняются
     * как есть (fire-логика — отдельная ответственность, не здесь).
     *
     * @param current текущее состояние streak.
     * @param nowMs   epoch-мс момента завершения активности.
     * @return обновлённое [StreakData].
     */
    fun computeNextStreak(current: StreakData, nowMs: Long): StreakData {
        val lastCompletionMs = current.lastCompletionDateMs

        val newCurrentStreak = if (lastCompletionMs == null) {
            // Первый раз — начинаем серию.
            1
        } else {
            val lastDay = lastCompletionMs / SrsConstants.DAY_MS
            val nowDay = nowMs / SrsConstants.DAY_MS
            val diff = (nowDay - lastDay).toInt()
            when {
                diff <= 0 -> current.currentStreak // та же день — без изменений.
                diff == 1 -> current.currentStreak + 1 // вчера — продолжаем серию.
                else -> 1 // разрыв ≥ 2 дней — сброс на 1 (унификация v1-бага).
            }
        }

        return current.copy(
            currentStreak = newCurrentStreak,
            longestStreak = maxOf(current.longestStreak, newCurrentStreak),
            lastCompletionDateMs = nowMs,
            totalSubLessonsCompleted = current.totalSubLessonsCompleted + 1,
        )
    }
}
