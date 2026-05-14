package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R

/**
 * Shared 4-option report bottom sheet: flag/unflag bad sentence, hide card,
 * export bad sentences, copy text. Used by TrainingScreen, VerbDrillScreen,
 * DailyPracticeScreen, and TrainingCardSession.
 *
 * @param onDismiss Called when the sheet should be dismissed.
 * @param cardPromptText Optional subtitle text shown below the title (e.g. card prompt).
 * @param isFlagged Whether the current card is already flagged as a bad sentence.
 * @param onFlag Called when the user flags the card as a bad sentence.
 * @param onUnflag Called when the user removes the card from bad sentences.
 * @param onHideCard Called when the user hides the card from lessons.
 * @param onExportBadSentences Called to export bad sentences; returns the file path or null.
 * @param onCopyText Called when the user taps "Copy text".
 * @param exportResult Optional callback invoked with the export path (or null). Callers can
 *   use this to show their own export confirmation. If null and [showExportConfirmation] is
 *   true, the sheet shows its own built-in confirmation dialog.
 * @param title The sheet title text. Defaults to "Card options".
 * @param showExportConfirmation When true and [exportResult] is null, shows a built-in
 *   AlertDialog after export with the result message. Defaults to true so callers that
 *   don't provide their own [exportResult] callback still get confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedReportSheet(
    onDismiss: () -> Unit,
    cardPromptText: String?,
    isFlagged: Boolean,
    onFlag: () -> Unit,
    onUnflag: () -> Unit,
    onHideCard: () -> Unit,
    onExportBadSentences: () -> String?,
    onCopyText: () -> Unit,
    exportResult: ((String?) -> Unit)? = null,
    title: String = "Card options",
    showExportConfirmation: Boolean = true,
    shareText: String? = null,
    onShareQr: (() -> Unit)? = null
) {
    var exportMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (cardPromptText != null) {
                Text(
                    text = cardPromptText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (isFlagged) {
                TextButton(
                    onClick = {
                        onUnflag()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ReportProblem, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.report_unflag))
                }
            } else {
                TextButton(
                    onClick = {
                        onFlag()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ReportProblem, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.report_flag))
                }
            }
            TextButton(
                onClick = {
                    onHideCard()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.VisibilityOff, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.report_hide_card))
            }
            TextButton(
                onClick = {
                    val path = onExportBadSentences()
                    if (exportResult != null) {
                        exportResult(path)
                    } else if (showExportConfirmation) {
                        exportMessage = if (path != null) "Exported to $path" else "No bad sentences to export"
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.report_export))
            }
            TextButton(
                onClick = onCopyText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.report_copy_text))
            }
            if (shareText != null && onShareQr != null) {
                TextButton(
                    onClick = {
                        onShareQr()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.report_share_translation))
                }
            }
        }
    }

    // Built-in export confirmation dialog, shown only when no external exportResult callback
    // is provided and showExportConfirmation is true.
    if (exportMessage != null) {
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text(stringResource(R.string.report_export_dialog_title)) },
            text = { Text(exportMessage!!) },
            confirmButton = {
                TextButton(onClick = { exportMessage = null }) { Text(stringResource(R.string.button_ok)) }
            }
        )
    }
}
