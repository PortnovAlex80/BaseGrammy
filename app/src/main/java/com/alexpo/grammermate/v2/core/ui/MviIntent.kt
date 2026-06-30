package com.alexpo.grammermate.v2.core.ui

/**
 * Маркеры контрактов MVI (Model — View — Intent) для presentation-слоя.
 *
 * Контракт ровно из двух ролей:
 *  * **[MviIntent]** — намерение пользователя/системы. Меняет [MviViewModel.state]
 *    через чистый [MviReducer]. Sealed по экранам: один экран — один иерархии.
 *  * **[MviEffect]** — one-shot side-effect (навигация, snackbar, TTS). Не часть
 *    [MviViewModel.state]; летит во View один раз через [MviViewModel.effects].
 *
 * Важное разделение ответственности:
 *  * [MviIntent] → детерминированно мутирует state (через pure reducer).
 *  * [MviEffect] → недетерминированное одноразовое действие (его нельзя «пере-отрисовать»
 *    при recomposition/restore, поэтому оно живёт в [kotlinx.coroutines.flow.Flow], а не в State).
 *
 * Все реализации — sealed-иерархии на конкретных экранах (например, `TrainingIntent`).
 */

/**
 * Маркер для всех intents (намерений пользователя / системы).
 *
 * Sealed по экранам: одна иерархия sealed-классов/объектов на экран. Пример:
 * ```kotlin
 * sealed interface TrainingIntent : MviIntent {
 *     data object StartSession : TrainingIntent
 *     data class SubmitAnswer(val answer: String) : TrainingIntent
 * }
 * ```
 */
interface MviIntent

/**
 * One-shot эффекты (навигация, snackbars, TTS) — не часть state.
 *
 * Эффекты доставляются подписчику один раз через [MviViewModel.effects] (на базе
 * [kotlinx.coroutines.channels.Channel]), а НЕ через StateFlow — иначе при
 * рекомпозиции/повороте эффект сработал бы повторно. Реализации — sealed-иерархия
 * на конкретном экране.
 */
interface MviEffect
