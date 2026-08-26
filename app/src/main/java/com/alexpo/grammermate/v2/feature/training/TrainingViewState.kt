package com.alexpo.grammermate.v2.feature.training

import com.alexpo.grammermate.domain.model.Card

/**
 * State экрана тренировки — конечный автомат (Фаза 1 плана стабилизации
 * 2026-08-26, §3.3): невозможные сочетания состояния не компилируются.
 *
 * Фазы однозначны: в любой момент экран находится ровно в одной из них.
 * Переходы публикуются ТОЛЬКО после успешного commit (правило плана §3.1.5);
 * `Feedback`/`Completed` недостижимы при упавшем persist (см.
 * `TrainingViewModelRegressionTest.submitAnswer_persistenceFailure_…`).
 *
 * Асинхронные результаты ViewModel публикует через
 * [com.alexpo.grammermate.v2.core.ui.MviViewModel.updateState] — каждая
 * публикация является целой фазой, а не набором независимых полей.
 */
sealed interface TrainingViewState {

    /** Начальная загрузка/возобновление сессии. */
    data object Loading : TrainingViewState

    /**
     * У урока нет пула карточек (пустой урок либо все карты скрыты).
     * Явный результат — без fallback-карточки (правило плана §3.1.4).
     */
    data class Empty(val message: String) : TrainingViewState

    /** Карточка показана, ждём ответ. [draft] восстанавливается из SavedStateHandle. */
    data class Active(
        val card: Card,
        val draft: String,
        val showHint: Boolean,
        val answeredCards: Int,
        val totalCards: Int,
    ) : TrainingViewState

    /** Ответ отправлен, идёт commit (ввод заблокирован — повторный Submit невозможен). */
    data class Checking(
        val card: Card,
        val answeredCards: Int,
        val totalCards: Int,
    ) : TrainingViewState

    /** Ответ зафиксирован в БД: ✓/✗ + правильный ответ + следующий шаг. */
    data class Feedback(
        val card: Card,
        val result: AnswerResult,
        val correctAnswer: String?,
        val answeredCards: Int,
        val totalCards: Int,
        /** Текущая карта — последняя в пуле: следующий Next завершает урок. */
        val isLastCard: Boolean,
    ) : TrainingViewState

    /** Урок пройден полностью (терминальное состояние для этого входа). */
    data class Completed(
        val correctCount: Int,
        val incorrectCount: Int,
        val totalCards: Int,
    ) : TrainingViewState

    /** Recoverable-ошибка (persist/load). Retry повторяет операцию, Back выходит. */
    data class Error(val message: String) : TrainingViewState
}

/**
 * Результат проверки ответа пользователя на карточку.
 *
 * Чистый value-объект внутри фазы [TrainingViewState.Feedback] (не effect):
 * его безопасно «пере-отрисовать» при recomposition/restore без повторного
 * срабатывания.
 *
 * @property correct true — ответ принят; false — неверный.
 */
data class AnswerResult(val correct: Boolean)
