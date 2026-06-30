package com.alexpo.grammermate.v2.feature.training

import com.alexpo.grammermate.v2.core.ui.MviEffect

/**
 * One-shot эффекты экрана тренировки.
 *
 * Не часть [TrainingViewState] — летят во View один раз через
 * [com.alexpo.grammermate.v2.core.ui.MviViewModel.effects] (Channel → Flow),
 * чтобы не сработать повторно при recomposition/process restore. Реализует
 * маркер [MviEffect].
 */
sealed interface TrainingEffect : MviEffect {

    /** Навигация назад (выход из тренировки). */
    data object NavigateBack : TrainingEffect

    /**
     * Показать toast/snackbar.
     *
     * @property message текст.
     */
    data class ShowToast(val message: String) : TrainingEffect

    /**
     * Озвучить текст карточки через TTS.
     *
     * @property text строка для синтеза речи.
     */
    data class PlayTts(val text: String) : TrainingEffect
}
