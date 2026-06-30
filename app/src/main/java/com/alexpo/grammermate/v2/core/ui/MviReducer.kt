package com.alexpo.grammermate.v2.core.ui

/**
 * Чистый редюсер: `(state, intent) → state`. БЕЗ side-effects.
 *
 * Контракт:
 *  * **Deterministic & pure** — одинаковые `(state, intent)` всегда дают одинаковый
 *    `state`. Не трогает репозитории/БД/логгеры/`System.currentTimeMillis()` и т.п.
 *  * **Lifted as `fun interface`** — реализуется лямбдой:
 *    `MviReducer { state, intent -> /* new state */ }`, что удобно для top-level
 *    pure-функций экрана (`fun trainingReducer(state, intent): state`).
 *  * **Тестируется на чистом JVM** — без Android-зависимостей, Robolectric или эмулятора.
 *
 * Разделение ответственности:
 *  * Здесь — только трансформация state по intent.
 *  * Side-effects (сохранение сессии, навигация, TTS) — в [MviViewModel] / UseCase,
 *    НЕ здесь. Редюсер ничего не знает о внешнем мире.
 *
 * Тип-параметры:
 *  * `S` — state экрана (не ковариантный: редюсер читает и пишет состояние).
 *  * `I : [MviIntent]` — ковариантный (`in`): редюсер только потребляет intents.
 *
 * @param S тип state экрана.
 * @param I тип intent экрана.
 */
fun interface MviReducer<S, in I : MviIntent> {

    /**
     * Вычислить новый state из [state] и [intent].
     *
     * Реализация ДОЛЖНА быть чистой: без I/O, без мутации [state] (возвращай новую
     * копию через `copy()`), без зависимостей от времени/случайности.
     *
     * @param state  текущий state.
     * @param intent намерение, которое нужно «редюснуть».
     * @return новый state.
     */
    fun reduce(state: S, intent: I): S
}
