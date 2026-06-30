package com.alexpo.grammermate.v2.core.ui

/**
 * Стандартная обёртка для асинхронных данных экрана: Loading / Success / Error.
 *
 * Применяется, когда конкретное поле state — это результат фоновой операции
 * (загрузка сессии, fetch контента). Встраивается в data class state экрана:
 * ```kotlin
 * data class TrainingViewState(
 *     val currentCard: UiState<Card> = UiState.Loading,
 *     // ...
 * )
 * ```
 *
 * `sealed interface` (а не `sealed class`) — ковариантный по `out T`, что позволяет
 * типобезопасно сводить `UiState<Card>` и `UiState.Loading` в одном выражении через
 * exhaustive `when`.
 *
 * `data object Loading` (Kotlin ≥ 1.9) — идиоматичный единственный синглтон:
 * потокобезопасно инициализируется, корректно сравнивается по ссылке и
 * сериализуется без параметров.
 *
 * @param T тип полезной нагрузки в состоянии успеха.
 */
sealed interface UiState<out T> {

    /** Загрузка в процессе — данных ещё нет. */
    data object Loading : UiState<Nothing>

    /**
     * Успех — данные готовы.
     *
     * @property data полезная нагрузка.
     */
    data class Success<T>(val data: T) : UiState<T>

    /**
     * Ошибка загрузки/обработки.
     *
     * @property message человекочитаемое сообщение (для UI).
     * @property cause   первопричина (опционально; для логирования/краш-отчётов).
     */
    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>
}
