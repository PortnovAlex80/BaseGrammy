package com.alexpo.grammermate.v2.feature.training

import com.alexpo.grammermate.v2.core.ui.MviIntent

/**
 * Намерения пользователя/системы для экрана тренировки.
 *
 * Sealed-иерархия: exhaustive `when` в [trainingReducer] гарантирует обработку
 * каждого intent'а на этапе компиляции. Реализует маркер [MviIntent], поэтому
 * проходит в [com.alexpo.grammermate.v2.core.ui.MviViewModel.onIntent].
 *
 * Фаза 1 плана стабилизации 2026-08-26: контракт ужат до фактически
 * существующих действий экрана (неиспользуемые `Resume`/`RateCard`/
 * `DismissResult` удалены — они вернутся вместе со своими фичами, а не раньше).
 */
sealed interface TrainingIntent : MviIntent {

    /** (Пере)загрузка/возобновление сессии — вход на экран или Retry после ошибки. */
    data object StartSession : TrainingIntent

    /** Изменение черновика ответа (переживает rotation/process death в SavedStateHandle). */
    data class DraftChanged(val text: String) : TrainingIntent

    /** Отправить ответ (валидно только в [TrainingViewState.Active]). */
    data object SubmitAnswer : TrainingIntent

    /** Запросить подсказку на текущей карточке (эфемерный UI; persist — Фаза 2). */
    data object RequestHint : TrainingIntent

    /**
     * Продолжить незавершённый урок с сохранённой карточки
     * (в [TrainingViewState.ResumeGate]; Фаза 3 slice 2).
     */
    data object ResumeAccepted : TrainingIntent

    /**
     * Начать незавершённый урок заново — сброс контекста сессии
     * (в [TrainingViewState.ResumeGate]; mastery сохраняется).
     */
    data object RestartRequested : TrainingIntent
}
