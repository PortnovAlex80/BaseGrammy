package com.alexpo.grammermate.domain.progress

import com.alexpo.grammermate.domain.TrainingConfig
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.training.ScheduledSubLesson

/**
 * Чистый детектор завершения урока / под-уроков.
 *
 * Перенесено из v1
 * `app/legacy-src/java/com/alexpo/grammermate/feature/progress/ProgressTracker.kt`:
 * метод `checkAndMarkLessonCompleted` (строки 113–134, чистая часть) и
 * `calculateCompletedSubLessons` (строки 171–206). Stateful-обёртка над сторами
 * в v1 НЕ переносится — только чистые функции-предикаты/счётчики.
 *
 * Ноль Android-зависимостей (в v1 были `android.util.Log` — убраны).
 */
object LessonCompletionCalculator {

    /**
     * Завершён ли урок по числу уникальных показов карточек.
     *
     * Перенесено из чистой части v1 `ProgressTracker.checkAndMarkLessonCompleted`,
     * строки 122–129 (без вызова `masteryStore.markLessonCompletedForPack`).
     *
     * Алгоритм:
     *  - effectiveCardCount = totalCardsInLesson − hiddenCardCount;
     *  - threshold = min(effectiveCardCount.coerceAtLeast(0), [TrainingConfig.LESSON_COMPLETION_CARD_THRESHOLD]);
     *  - если threshold > 0 → `uniqueCardShows >= threshold`;
     *  - иначе (нет данных о картах) → false (в v1 тут был фолбэк по числу под-уроков;
     *    в v2 этот фолбэк убран — чистый предикат работает только по карточкам;
     *    вызывающая сторона при необходимости использует отдельный под-урок-фолбэк).
     *
     * @param uniqueCardShows    сколько уникальных карточек показано.
     * @param totalCardsInLesson всего карточек в уроке.
     * @param hiddenCardCount    сколько скрыто/плохих (не доставляются пользователю).
     * @return true, если показано достаточно карточек для завершения урока.
     */
    fun isLessonComplete(uniqueCardShows: Int, totalCardsInLesson: Int, hiddenCardCount: Int): Boolean {
        val effectiveCardCount = totalCardsInLesson - hiddenCardCount
        val threshold = minOf(effectiveCardCount.coerceAtLeast(0), TrainingConfig.LESSON_COMPLETION_CARD_THRESHOLD)
        return if (threshold > 0) {
            uniqueCardShows >= threshold
        } else {
            false
        }
    }

    /**
     * Посчитать, сколько под-уроков полностью завершено по показанным карточкам.
     *
     * Перенесено 1:1 из v1 `ProgressTracker.calculateCompletedSubLessons`,
     * строки 171–206 (адаптировано: чистые множества [CardId] вместо обращения к
     * `mastery.shownCardIds`/`lessons`; убраны `android.util.Log`).
     *
     * Под-урок завершён, если каждая доставляемая карточка (не hidden) показана.
     * Подсчёт ПОСЛЕДОВАТЕЛЬНЫЙ: прерывается на первом незавершённом под-уроке.
     * Под-урок, где ВСЕ карточки скрыты → авто-завершён (continue).
     *
     * Карточка считается показанной, если её нет в [lessonCardIds] (чужая — игнор),
     * либо она есть в [shownCardIds]. Это повторяет v1-логику `!lessonCardIds.contains
     * (card.id) || mastery.shownCardIds.contains(card.id)`.
     *
     * @param subLessons     под-уроки урока (в порядке прохождения).
     * @param shownCardIds   множество показанных карточек урока.
     * @param lessonCardIds  множество всех карточек урока (для фильтра «чужих»).
     * @param hiddenCardIds  множество скрытых/плохих карточек.
     * @return число завершённых под-уроков подряд от начала.
     */
    fun calculateCompletedSubLessons(
        subLessons: List<ScheduledSubLesson>,
        shownCardIds: Set<CardId>,
        lessonCardIds: Set<CardId>,
        hiddenCardIds: Set<CardId>,
    ): Int {
        if (shownCardIds.isEmpty()) return 0

        var completed = 0
        for (subLesson in subLessons) {
            // Скрытые/плохие карты исключаются — они никогда не доставляются.
            val deliverableCards = subLesson.cardIds.filter { it !in hiddenCardIds }
            if (deliverableCards.isEmpty()) {
                completed++ // Все карты скрыты → авто-завершение этого под-урока.
                continue
            }
            val allCardsShown = deliverableCards.all { card ->
                card !in lessonCardIds || card in shownCardIds
            }
            if (allCardsShown) {
                completed++
            } else {
                // Прерываем на первом незавершённом под-уроке.
                break
            }
        }
        return completed
    }

    /**
     * Вычислить монотонно неубывающий `activeSubLessonIndex`.
     *
     * AC-14 Then.3 (gap #3): индекс активного под-урока **никогда не движется
     * назад** — это критично для устойчивости resume (regression-anchored к
     * AC-13 детерминизму и AC-5/AC-6 resume-инвариантам). В v1 курсор мог
     * «прыгнуть» назад, если `completedSubLessons` пересчитывался в меньшую
     * величину (например, после скрытия карт) — пользователь терял прогресс
     * и видел уже пройденный под-урок снова.
     *
     * Контракт: возвращается `maxOf(currentIndex, actualCompletedCount)`,
     * где:
     *  - [currentIndex] — сохранённый индекс активного под-урока (state);
     *  - [actualCompletedCount] — фактически завершённых под-уроков подряд
     *    от начала (результат [calculateCompletedSubLessons], либо
     *    прямой аргумент — см. перегрузку ниже).
     *
     * Итоговый индекс ограничивается сверху размером списка [subLessonCount]
     * (clamping), чтобы никогда не указывать за пределы массива под-уроков.
     *
     * @param currentIndex           сохранённый индекс активного под-урока.
     * @param actualCompletedCount   фактически завершённых под-уроков подряд.
     * @param subLessonCount         всего под-уроков в уроке (для clamping).
     * @return `maxOf(currentIndex, actualCompletedCount)`, ограниченный
     *         размером списка под-уроков (никогда не отрицательный, никогда
     *         не выходит за `[0, subLessonCount]`).
     */
    fun advanceActiveSubLessonIndex(
        currentIndex: Int,
        actualCompletedCount: Int,
        subLessonCount: Int,
    ): Int {
        val max = maxOf(currentIndex.coerceAtLeast(0), actualCompletedCount.coerceAtLeast(0))
        return if (subLessonCount <= 0) 0 else minOf(max, subLessonCount)
    }

    /**
     * Перегрузка [advanceActiveSubLessonIndex]: принимает сами под-уроки и
     * показанные/скрытые карты, самостоятельно считая `actualCompletedCount`
     * через [calculateCompletedSubLessons].
     *
     * Удобно, когда вызывающая сторона уже держит под-уроки урока.
     *
     * @param currentIndex  сохранённый индекс активного под-урока.
     * @param subLessons    под-уроки урока (в порядке прохождения).
     * @param shownCardIds  множество показанных карточек урока.
     * @param lessonCardIds множество всех карточек урока (для фильтра «чужих»).
     * @param hiddenCardIds множество скрытых/плохих карточек.
     * @return монотонно неубывающий индекс активного под-урока.
     */
    fun advanceActiveSubLessonIndex(
        currentIndex: Int,
        subLessons: List<ScheduledSubLesson>,
        shownCardIds: Set<CardId>,
        lessonCardIds: Set<CardId>,
        hiddenCardIds: Set<CardId>,
    ): Int {
        val actual = calculateCompletedSubLessons(subLessons, shownCardIds, lessonCardIds, hiddenCardIds)
        return advanceActiveSubLessonIndex(currentIndex, actual, subLessons.size)
    }
}
