package com.alexpo.grammermate.v2.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Helper-функции для связывания MVI-ViewModel с Jetpack Compose.
 *
 * Две обязанности, по одному хелперу на каждую:
 *  * **State** (re-render) — [collectState]: подписка на [StateFlow], перерисовка
 *    на каждое новое значение.
 *  * **Effects** (one-shot) — [collectEffects]: подписка на [Flow] эффектов,
 *    обработчик вызывается один раз на каждое событие.
 *
 * 2026 best practices:
 *  * `collectAsStateWithLifecycle` (а не голый `collectAsState`) — collect
 *    приостанавливается в `ON_STOP`, экономя батарею и CPU на фоне.
 *  * [LocalLifecycleOwner] берётся из `androidx.lifecycle.compose` (с lifecycle 2.8.0
 *    `androidx.compose.ui.platform.LocalLifecycleOwner` deprecated).
 *  * Эффекты — [Flow] (Channel.receiveAsFlow), НЕ StateFlow: один выстрел на событие.
 */

/**
 * Collect [StateFlow] в Compose с учётом lifecycle — безопасно для фона.
 *
 * Возвращает текущее значение state; при изменении потока композиция перерисовывается.
 * Collect автоматически приостанавливается, когда host уходит в `ON_STOP`.
 *
 * Пример:
 * ```kotlin
 * val state by collectState(viewModel.state)
 * ```
 *
 * @param S тип state.
 * @param state hot-поток state экрана.
 * @return текущее значение state (подписка активна, пока композиция жива).
 */
@Composable
fun <S> collectState(state: StateFlow<S>): S =
    state.collectAsStateWithLifecycle().value

/**
 * Collect effects (one-shot) в Compose — вызывает [handler] на каждый эффект.
 *
 * Эффекты — недетерминированные одноразовые действия (навигация, snackbar, TTS).
 * Подписка живёт в [LaunchedEffect], ключами которого выступают сам поток
 * [effects] и текущий [LocalLifecycleOwner]: при смене хоста (например, навигации
 * между destination'ами NavHost) подписка корректно пересоздаётся.
 *
 * **Важно:** так как под капотом Channel → Flow, каждый эффект доставляется ровно
 * одному активному подписчику и ровно один раз (не пере-вызывается при recomposition).
 *
 * Пример:
 * ```kotlin
 * collectEffects(viewModel.effects) { effect ->
 *     when (effect) {
 *         is TrainingEffect.NavigateBack -> navController.popBackStack()
 *         is TrainingEffect.ShowToast -> /* ... */
 *     }
 * }
 * ```
 *
 * @param E тип эффекта.
 * @param effects поток one-shot эффектов.
 * @param handler обработчик эффекта (например, навигация/показ snackbar).
 */
@Composable
fun <E> collectEffects(
    effects: Flow<E>,
    handler: (E) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(effects, lifecycleOwner) {
        effects.collect { handler(it) }
    }
}
