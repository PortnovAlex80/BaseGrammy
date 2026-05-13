package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared 4-option report bottom sheet: flag/unflag bad sentence, hide card,
 * export bad sentences, copy text. Used by TrainingScreen and VerbDrillScreen.
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
    exportResult: ((String?) -> Unit)? = null
) {
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
                text = "Card options",
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
                    Text("Remove from bad sentences list")
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
                    Text("Add to bad sentences list")
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
                Text("Hide this card from lessons")
            }
            TextButton(
                onClick = {
                    val path = onExportBadSentences()
                    exportResult?.invoke(path)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export bad sentences to file")
            }
            TextButton(
                onClick = onCopyText,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy text")
            }
        }
    }
}
