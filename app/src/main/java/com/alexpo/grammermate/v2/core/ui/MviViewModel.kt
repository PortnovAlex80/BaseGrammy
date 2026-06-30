package com.alexpo.grammermate.v2.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Базовый класс MVI-ViewModel для экрана — единая точка координации state и effects.
 *
 * Архитектура потока данных:
 * ```
 *   UI ──onIntent(i)──► reducer ──► _state ──(StateFlow)──► UI
 *                                       ▲
 *   UseCase (async) ──updateState{}──────┘
 *
 *   ViewModel/UseCase ──emitEffect(e)──► _effects ──(Flow)──► UI (one-shot)
 * ```
 *
 * Контракты:
 *  * **State — single source of truth.** [state] — это [StateFlow]; View читает
 *    ровно один поток и перерисовывается детерминированно. Один экран = один state.
 *  * **Reducer — чистая функция** ([reducer]): `(state, intent) → state`, без I/O.
 *    Любой [MviIntent], пришедший из UI, проходит через [onIntent].
 *  * **Effects — one-shot** ([effects], [Channel.BUFFERED]): навигация/snackbar/TTS
 *    летят во View один раз и не накапливаются в state (иначе срабатывали бы
 *    повторно при recomposition/process restore).
 *  * **Async-результаты** из UseCase идут минуя reducer — через [updateState],
 *    т.к. reducer намеренно не имеет доступа к репозиториям.
 *
 * Тип-параметры:
 *  * `S` — state экрана.
 *  * `I : [MviIntent]` — намерения.
 *  * `E : [MviEffect]` — one-shot эффекты.
 *
 * @param initialState начальное значение state.
 * @param reducer      pure-редюсер, сводящий `(state, intent) → state`.
 */
abstract class MviViewModel<S, I : MviIntent, E : MviEffect>(
    initialState: S,
    private val reducer: MviReducer<S, I>,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)

    /** State экрана как горячий поток — single source of truth для View. */
    val state: StateFlow<S> = _state.asStateFlow()

    /** BUFFERED: эффекты не теряются, если подписчик кратко отстал. */
    private val _effects = Channel<E>(Channel.BUFFERED)

    /** One-shot эффекты (навигация, snackbar, TTS) — один выстрел на подписчика. */
    val effects: Flow<E> = _effects.receiveAsFlow()

    /**
     * UI шлёт intent → reducer детерминированно обновляет state.
     *
     * Синхронно (через [MutableStateFlow.update]): потокобезопасно, без гонок
     * с конкурентными [updateState]. Side-effects здесь быть не должно.
     *
     * @param intent намерение пользователя/системы.
     */
    fun onIntent(intent: I) {
        _state.update { reducer.reduce(it, intent) }
    }

    /**
     * Подкласс шлёт side-effect (навигация и т.п.). Не часть state — один выстрел.
     *
     * Запускается в [viewModelScope], поэтому автоматически отменяется вместе
     * с очисткой ViewModel (не переживает `onCleared`).
     *
     * @param effect эффект для View.
     */
    protected fun emitEffect(effect: E) {
        viewModelScope.launch { _effects.send(effect) }
    }

    /**
     * Подкласс обновляет state напрямую — для асинхронных результатов из UseCase.
     *
     * Потокобезопасно атомарно (CAS через [MutableStateFlow.update]), безопасно
     * при конкурентных intent'ах. Используется, когда результат не приходит из
     * UI-intent (например, `repository.loadSession()` в фоне).
     *
     * @param transform чистая функция `текущий state → новый state`.
     */
    protected fun updateState(transform: (S) -> S) {
        _state.update(transform)
    }

    /** Снимок текущего state — для чтения в side-effect-логике подкласса. */
    protected val currentState: S get() = _state.value
}
