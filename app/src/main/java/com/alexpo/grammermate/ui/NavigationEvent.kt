package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.CompletionNextAction

/**
 * One-shot navigation decisions emitted by [TrainingViewModel] and consumed
 * by a single LaunchedEffect collector in GrammarMateApp (Phase 2, item 2.2).
 *
 * The sub-lesson/boss completion TOKENS remain in state as edge-detection
 * keys, but every navigation decision itself travels through this channel —
 * a consumable stream, never compared against remembered UI values.
 */
sealed interface NavigationEvent {
    /** Navigate to [route] (drill return, daily block, boss exit). */
    data class Navigate(val route: String) : NavigationEvent

    /** Open the sub-lesson completion dialog pre-computed with [action]. */
    data class CompletionDialog(val action: CompletionNextAction) : NavigationEvent

    /** A boss session finished — return to the lesson list. */
    object BossSessionFinished : NavigationEvent
}
