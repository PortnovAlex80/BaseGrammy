package com.alexpo.grammermate.v2.feature.training

import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.SrsRating

/**
 * State экрана тренировки — single source of truth для UI.
 *
 * Формируется чистой функцией [trainingReducer] из [TrainingIntent] (а также
 * прямым [com.alexpo.grammermate.v2.core.ui.MviViewModel.updateState] для
 * асинхронных результатов из репозиториев). View читает ровно один
 * [TrainingViewState] и перерисовывается детерминированно.
 *
 * Поля намеренно плоские и явные (а не `UiState<Card>`), чтобы UI разветвлялся по
 * простым булевым флагам `isLoading`/`error` — так проще на старте Фазы 6.
 *
 * @property currentCard      текущая карточка сессии (null — пока не загружена).
 * @property isLoading        идёт ли фоновая операция (показ spinner'а).
 * @property error            человекочитаемое сообщение об ошибке (null — ошибок нет).
 * @property showHint         показана ли подсказка на текущем шаге.
 * @property lastRating       последний выставленный SRS-рейтинг (мгновенная обратная связь).
 * @property answeredCards    сколько карточек пройдено в текущей сессии.
 * @property totalCards       размер пула сессии (для progress indicator).
 * @property lastResult       результат последней проверки ответа (для мгновенной
 *                            обратной связи ✓/✗); сбрасывается при следующей карточке.
 */
data class TrainingViewState(
    val currentCard: Card? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showHint: Boolean = false,
    val lastRating: SrsRating? = null,
    val answeredCards: Int = 0,
    val totalCards: Int = 0,
    val lastResult: AnswerResult? = null,
)

/**
 * Результат проверки ответа пользователя на карточку.
 *
 * Чистый value-объект в state (не effect): его можно безопасно «пере-отрисовать»
 * при recomposition/restore без повторного срабатывания.
 *
 * @property correct true — ответ принят; false — неверный.
 */
data class AnswerResult(val correct: Boolean)
