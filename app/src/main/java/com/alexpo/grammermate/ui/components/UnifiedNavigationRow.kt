package com.alexpo.grammermate.ui.components

import androidx.compose.runtime.Composable
import com.alexpo.grammermate.data.CardSessionStateModel
import com.alexpo.grammermate.ui.features.training.components.TrainingNavigationRow

/**
 * Unified navigation row for all card session modes (Training, VerbDrill, DailyPractice).
 *
 * Integration boundary adapter (P3b): the rendering and the exit-confirmation dialog
 * moved to the display organism [TrainingNavigationRow]. This adapter keeps everything
 * the display must not own — the [CardSessionStateModel] contract reading. The enabled
 * derivation is byte-equivalent with the pre-extraction component: all four buttons
 * read [CardSessionStateModel.hasCurrentCard]; the pause/play icon reads
 * [CardSessionStateModel.isActive] (inventory effect ledger row 24 for the dialog state).
 *
 * Design Principle DP-01: All card-based drill modes MUST use identical
 * play/pause/submit/retry/navigation behavior.
 *
 * @param stateModel   Unified state source (SessionRunner or
 *                     the daily session flow — both implement [CardSessionStateModel]).
 * @param supportsPause Whether the play/pause button should be shown.
 * @param supportsNavigation Whether the navigation row should be shown at all.
 * @param onPrev       Navigate to the previous card (pauses-first).
 * @param onTogglePause Toggle between play and pause states.
 * @param onStop       Exit/stop the session (shows confirmation dialog).
 * @param onNext       Navigate to the next card (pauses-first).
 */
@Composable
fun UnifiedNavigationRow(
    stateModel: CardSessionStateModel,
    supportsPause: Boolean,
    supportsNavigation: Boolean,
    onPrev: () -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit
) {
    if (!supportsNavigation) return

    TrainingNavigationRow(
        supportsPause = supportsPause,
        buttonsEnabled = stateModel.hasCurrentCard,
        sessionActive = stateModel.isActive,
        onPrev = onPrev,
        onTogglePause = onTogglePause,
        onStop = onStop,
        onNext = onNext
    )
}
