package com.alexpo.grammermate.v2.feature.training

import com.alexpo.grammermate.v2.core.domain.model.Card
import com.alexpo.grammermate.v2.core.domain.model.SrsRating

/**
 * State экрана тренировки — single source of truth для UI.
 *
 * Формируется ТОЛЬКО чистой функцией [trainingReducer] из [TrainingIntent] (а также
 * прямым [com.alexpo.grammermate.v2.core.ui.MviViewModel.updateState] для асинхронных
 * результатов из репозиториев). View читает ровно один [TrainingViewState] и
 * перерисовывается детерминированно.
 *
 * Поля намеренно плоские и явные (а не `UiState<Card>`), чтобы UI разветвлялся по
 * простым булевым флагам `isLoading`/`error` — так проще на старте Фазы 6.
 *
 * @property currentCard  текущая карточка сессии (null — пока не загружена).
 * @property isLoading    идёт ли фоновая операция (показ spinner'а/скелетона).
 * @property error        человекочитаемое сообщение об ошибке (null — ошибок нет).
 * @property showHint     показана ли подсказка на текущем шаге.
 * @property lastRating   последний выставленный SRS-рейтинг (для мгновенной обратной связи).
 */
data class TrainingViewState(
    val currentCard: Card? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showHint: Boolean = false,
    val lastRating: SrsRating? = null,
)
