package com.alexpo.grammermate.ui.designsystem.atoms

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R

// =====================================================================
// TTS speaker button — generic audio atom
// =====================================================================

/**
 * Display states for [TtsSpeakerButton]. UI-owned presentation values only:
 * integration boundaries map their engine state (e.g. `data.TtsState`) onto
 * these before rendering; the atom never imports data-layer types.
 */
sealed class TtsButtonState {
    /** Idle/ready/paused engine — play affordance. */
    object Idle : TtsButtonState()

    /** Engine/model is warming up — indeterminate spinner. */
    object Initializing : TtsButtonState()

    /** Currently speaking — stop affordance. */
    object Speaking : TtsButtonState()

    /** Engine failure; [reason] (if any) is shown in an error tooltip. */
    data class Error(val reason: String? = null) : TtsButtonState()
}

/**
 * TTS speaker button with 4 visual states: speaking (stop icon, red), initializing
 * (spinner), error (warning/report icon, error tooltip with [TtsButtonState.Error.reason]),
 * idle (VolumeUp icon).
 *
 * Moved verbatim from `ui/components/SharedComponents.kt` in the P2 prompt pilot
 * (P0 inventory §2 proposed destination: `designsystem/atoms`) and re-typed from
 * `data.TtsState` to [TtsButtonState] so UI layers can render audio without a
 * data-layer dependency.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSpeakerButton(
    state: TtsButtonState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        when (state) {
            is TtsButtonState.Speaking -> Icon(
                Icons.Default.StopCircle,
                contentDescription = stringResource(R.string.content_desc_stop),
                tint = MaterialTheme.colorScheme.error
            )
            is TtsButtonState.Initializing -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            is TtsButtonState.Error -> {
                val reason = state.reason
                val isOom = reason?.contains("memory", ignoreCase = true) == true
                val errorIcon = if (isOom) Icons.Default.Warning else Icons.Default.ReportProblem
                if (reason != null) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(reason) } },
                        state = rememberTooltipState()
                    ) {
                        Icon(
                            errorIcon,
                            contentDescription = stringResource(R.string.content_desc_tts_error),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Icon(
                        errorIcon,
                        contentDescription = stringResource(R.string.content_desc_tts_error),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            is TtsButtonState.Idle -> Icon(
                Icons.Default.VolumeUp,
                contentDescription = stringResource(R.string.content_desc_listen),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
