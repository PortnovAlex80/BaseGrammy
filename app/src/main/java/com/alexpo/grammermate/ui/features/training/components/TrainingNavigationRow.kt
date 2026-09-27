package com.alexpo.grammermate.ui.features.training.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.ThemeMode
import com.alexpo.grammermate.ui.GrammarMateTheme
import com.alexpo.grammermate.ui.components.NavIconButton

// =====================================================================
// Training navigation row — feature organism (training feature owner)
// =====================================================================

/**
 * Display-only navigation row for card sessions: [Prev] [Play/Pause] [Stop/Exit] [Next]
 * plus the exit-confirmation dialog presentation (Design Principle DP-01).
 *
 * Extracted in P3b from `ui/components/UnifiedNavigationRow.kt`, which remains as
 * the integration boundary adapter: it keeps the [com.alexpo.grammermate.data.CardSessionStateModel]
 * reading (the enabled derivation `stateModel.hasCurrentCard` for all four buttons and
 * `stateModel.isActive` for the pause/play icon — byte-equivalent with the pre-extraction
 * code) and passes plain display values down here.
 *
 * Owned here by contract (plan §4): transient visual state only — the
 * `showExitDialog` remember (inventory effect ledger row 24; plain remember, no keys,
 * same composition position as before extraction). The exit action itself stays a
 * callback; audit/logging and session mutation stay with the caller.
 *
 * Parity anchors preserved verbatim from the pre-extraction component:
 * - Tags `prev_button`, `pause_button`, `exit_button`, `next_button` and the
 *   `exit_confirm_button` tag on the dialog's confirm button.
 * - All four buttons share one enabled flag (the old `stateModel.hasCurrentCard`).
 * - Pause button renders Pause when [sessionActive], PlayArrow otherwise (Play clears
 *   the hint on the same card or resumes from pause; after 3 incorrect retries the user
 *   advances via the explicit Next button — the card does not auto-advance).
 * - Confirm ordering: dialog state reset BEFORE [onStop] fires.
 *
 * @param buttonsEnabled whether the four row buttons are enabled (old `hasCurrentCard`).
 * @param sessionActive whether the session is active — picks the pause vs play icon.
 * @param supportsPause whether the play/pause button is shown at all.
 * @param onStop exit/stop request; the confirm button invokes it after closing the dialog.
 */
@Composable
fun TrainingNavigationRow(
    modifier: Modifier = Modifier,
    supportsPause: Boolean,
    buttonsEnabled: Boolean,
    sessionActive: Boolean,
    onPrev: () -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit
) {
    var showExitDialog by remember { mutableStateOf(false) }

    if (showExitDialog) {
        TrainingExitConfirmDialog(
            onConfirm = {
                showExitDialog = false
                onStop()
            },
            onDismiss = { showExitDialog = false }
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavIconButton(
            modifier = Modifier.testTag("prev_button"),
            onClick = onPrev,
            enabled = buttonsEnabled
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.content_desc_prev))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (supportsPause) {
                NavIconButton(
                    modifier = Modifier.testTag("pause_button"),
                    onClick = onTogglePause,
                    enabled = buttonsEnabled
                ) {
                    if (sessionActive) {
                        Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.content_desc_pause))
                    } else {
                        // Always show PlayArrow: Play clears hint (same card) or resumes from pause.
                        // After 3 incorrect retries, the hint is shown but the card does NOT auto-advance.
                        // User must press the explicit Next button (ArrowForward) to advance.
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.content_desc_play))
                    }
                }
            }
            NavIconButton(
                modifier = Modifier.testTag("exit_button"),
                onClick = { showExitDialog = true },
                enabled = buttonsEnabled
            ) {
                Icon(Icons.Default.StopCircle, contentDescription = stringResource(R.string.content_desc_exit_session))
            }
            NavIconButton(
                modifier = Modifier.testTag("next_button"),
                onClick = onNext,
                enabled = buttonsEnabled
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = stringResource(R.string.content_desc_next))
            }
        }
    }
}

/**
 * Exit-confirmation dialog rendering, split out of [TrainingNavigationRow] verbatim
 * so previews can show the dialog state without interaction. Confirm closes first,
 * then fires [onConfirm] (the session exit); dismiss/cancel only closes.
 */
@Composable
private fun TrainingExitConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_end_title)) },
        text = { Text(stringResource(R.string.session_end_message)) },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("exit_confirm_button"),
                onClick = onConfirm
            ) {
                Text(stringResource(R.string.button_end))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

// =====================================================================
// Preview fixtures — pure data only: no ViewModel, filesystem, installed
// pack or speech engine (plan §7). Each wraps the real GrammarMateTheme
// facade with an explicit ThemeMode, matching the P1/P2/P3a convention.
// ======================================================================

@Preview(name = "Navigation — all enabled (active session)", showBackground = true, widthDp = 360, heightDp = 72)
@Composable
private fun TrainingNavigationRowAllEnabledPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingNavigationRow(
            supportsPause = true,
            buttonsEnabled = true,
            sessionActive = true,
            onPrev = {}, onTogglePause = {}, onStop = {}, onNext = {}
        )
    }
}

@Preview(name = "Navigation — all enabled, paused (play icon)", showBackground = true, widthDp = 360, heightDp = 72)
@Composable
private fun TrainingNavigationRowPausedPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingNavigationRow(
            supportsPause = true,
            buttonsEnabled = true,
            sessionActive = false,
            onPrev = {}, onTogglePause = {}, onStop = {}, onNext = {}
        )
    }
}

@Preview(name = "Navigation — all disabled (no current card)", showBackground = true, widthDp = 360, heightDp = 72)
@Composable
private fun TrainingNavigationRowAllDisabledPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingNavigationRow(
            supportsPause = true,
            buttonsEnabled = false,
            sessionActive = true,
            onPrev = {}, onTogglePause = {}, onStop = {}, onNext = {}
        )
    }
}

@Preview(name = "Navigation — pause hidden (supportsPause = false)", showBackground = true, widthDp = 360, heightDp = 72)
@Composable
private fun TrainingNavigationRowNoPausePreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingNavigationRow(
            supportsPause = false,
            buttonsEnabled = true,
            sessionActive = true,
            onPrev = {}, onTogglePause = {}, onStop = {}, onNext = {}
        )
    }
}

@Preview(name = "Navigation — dark, all enabled", showBackground = true, widthDp = 360, heightDp = 72)
@Composable
private fun TrainingNavigationRowDarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        TrainingNavigationRow(
            supportsPause = true,
            buttonsEnabled = true,
            sessionActive = true,
            onPrev = {}, onTogglePause = {}, onStop = {}, onNext = {}
        )
    }
}

@Preview(name = "Navigation — exit confirm dialog, light", showBackground = true, widthDp = 360, heightDp = 200)
@Composable
private fun TrainingNavigationRowExitDialogLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingExitConfirmDialogPreview()
    }
}

@Preview(name = "Navigation — exit confirm dialog, dark", showBackground = true, widthDp = 360, heightDp = 200)
@Composable
private fun TrainingNavigationRowExitDialogDarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        TrainingExitConfirmDialogPreview()
    }
}

/** Preview-only host: the real dialog composable is private in this file; previews in the same file may call it. */
@Composable
private fun TrainingExitConfirmDialogPreview() {
    TrainingExitConfirmDialog(onConfirm = {}, onDismiss = {})
}
