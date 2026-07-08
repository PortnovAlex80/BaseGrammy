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
 * Ноль Android-зависимостей (в v1 был Android-логгер — убран).
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
     * `mastery.shownCardIds`/`lessons`; убран Android-логгер).
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
}
