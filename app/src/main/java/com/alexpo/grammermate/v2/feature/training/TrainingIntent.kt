package com.alexpo.grammermate.v2.feature.training

import com.alexpo.grammermate.v2.core.domain.model.PackId
import com.alexpo.grammermate.v2.core.domain.model.LessonId
import com.alexpo.grammermate.v2.core.domain.model.SrsRating
import com.alexpo.grammermate.v2.core.ui.MviIntent

/**
 * Намерения пользователя/системы для экрана тренировки.
 *
 * Sealed-иерархия: exhaustive `when` в [trainingReducer] гарантирует обработку
 * каждого intent'а на этапе компиляции. Реализует маркер [MviIntent], поэтому
 * проходит в [com.alexpo.grammermate.v2.core.ui.MviViewModel.onIntent].
 */
sealed interface TrainingIntent : MviIntent {

    /**
     * Старт новой сессии (или resume существующей) по контексту пак/урок.
     *
     * @property packId  пак тренировки.
     * @property lessonId урок (null для drill/daily/помодоро).
     */
    data class StartSession(val packId: PackId, val lessonId: LessonId?) : TrainingIntent

    /**
     * Пользователь отправил ответ. Сам ответ НЕ мутирует SRS напрямую — reducer
     * лишь фиксирует UI-состояние (например, lastRating); persist проходит через
     * ViewModel в репозиторий.
     *
     * @property answer введённый/распознанный ответ.
     */
    data class SubmitAnswer(val answer: String) : TrainingIntent

    /** Запросить/скрыть подсказку на текущей карточке. */
    data object RequestHint : TrainingIntent

    /**
     * Перейти к следующей карточке сессии.
     *
     * Чистая UI-проекция: сбрасывает подсказку и мгновенную обратную связь;
     * реальный advance по пулу (через SessionEngine) делает ViewModel.
     */
    data object NextCard : TrainingIntent

    /** Скрыть мгновенную обратную связь (✓/✗) — после того как пользователь увидел. */
    data object DismissResult : TrainingIntent

    /** Пометить текущую карточку флажком (например, «сложная»). */
    data object FlagCard : TrainingIntent

    /** Возобновить приостановленную сессию. */
    data object Resume : TrainingIntent

    /**
     * Пользователь выставил SRS-рейтинг по карточке (FSRS-стиль).
     *
     * @property rating оценка сложности.
     */
    data class RateCard(val rating: SrsRating) : TrainingIntent

    /** Пользователь закрыл экран/нажал back. */
    data object NavigateBack : TrainingIntent
}
