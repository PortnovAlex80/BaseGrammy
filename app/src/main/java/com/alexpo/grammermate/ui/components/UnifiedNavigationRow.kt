package com.alexpo.grammermate.ui.components

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
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.CardSessionStateModel

/**
 * Unified navigation row for all card session modes (Training, VerbDrill, DailyPractice).
 *
 * Renders: [Prev] [Play/Pause] [Stop/Exit] [Next]
 *
 * Design Principle DP-01: All card-based drill modes MUST use identical
 * play/pause/submit/retry/navigation behavior.
 *
 * @param stateModel   Unified state source (SessionRunner or
 *                     DailyPracticeSessionProvider — both implement [CardSessionStateModel]).
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

    var showExitDialog by remember { mutableStateOf(false) }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.session_end_title)) },
            text = { Text(stringResource(R.string.session_end_message)) },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("exit_confirm_button"),
                    onClick = {
                        showExitDialog = false
                        onStop()
                    }
                ) {
                    Text(stringResource(R.string.button_end))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavIconButton(
            modifier = Modifier.testTag("prev_button"),
            onClick = onPrev,
            enabled = stateModel.hasCurrentCard
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
                    enabled = stateModel.hasCurrentCard
                ) {
                    if (stateModel.isActive) {
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
                enabled = stateModel.hasCurrentCard
            ) {
                Icon(Icons.Default.StopCircle, contentDescription = stringResource(R.string.content_desc_exit_session))
            }
            NavIconButton(
                modifier = Modifier.testTag("next_button"),
                onClick = onNext,
                enabled = stateModel.hasCurrentCard
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = stringResource(R.string.content_desc_next))
            }
        }
    }
}
