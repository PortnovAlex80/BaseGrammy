package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.data.CardSessionContract
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.ui.helpers.DailyPracticeSessionProvider

/**
 * Word bank chip grid with undo control.
 * Extracted from DailyInputControls in DailyPracticeScreen.kt.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyWordBankSection(contract: CardSessionContract) {
    val wordBankWords = contract.getWordBankWords()
    val selectedWords = contract.getSelectedWords()
    if (wordBankWords.isEmpty()) return

    Text(
        text = "Tap words in correct order:",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    Spacer(modifier = Modifier.height(4.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        wordBankWords.forEach { word ->
            val availableCount = wordBankWords.count { it == word }
            val usedCount = selectedWords.count { it == word }
            val isFullyUsed = usedCount >= availableCount
            FilterChip(
                selected = usedCount > 0,
                onClick = { if (!isFullyUsed) contract.selectWordFromBank(word) },
                label = { Text(text = word) },
                enabled = !isFullyUsed
            )
        }
    }
    if (selectedWords.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Selected: ${selectedWords.size} / ${wordBankWords.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = { contract.removeLastSelectedWord() }) { Text("Undo") }
        }
    }
}

/**
 * Input mode selector row with show-answer and report buttons.
 * Extracted from DailyInputControls in DailyPracticeScreen.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyInputModeBar(
    contract: CardSessionContract,
    provider: DailyPracticeSessionProvider,
    canLaunchVoice: Boolean,
    canSelectInputMode: Boolean,
    hasCards: Boolean,
    onClearInput: () -> Unit,
    onShowReport: () -> Unit,
    hintLevel: HintLevel = HintLevel.EASY
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(
                onClick = { if (canLaunchVoice) { onClearInput(); contract.setInputMode(InputMode.VOICE) } },
                enabled = canLaunchVoice
            ) { Icon(Icons.Default.Mic, "Voice mode") }
            if (hintLevel != HintLevel.HARD) {
                FilledTonalIconButton(
                    onClick = { contract.setInputMode(InputMode.KEYBOARD) },
                    enabled = canSelectInputMode
                ) { Icon(Icons.Default.Keyboard, "Keyboard mode") }
            }
            FilledTonalIconButton(
                onClick = { contract.setInputMode(InputMode.WORD_BANK) },
                enabled = canSelectInputMode
            ) { Icon(Icons.Default.LibraryBooks, "Word bank mode") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Show answer") } },
                state = rememberTooltipState()
            ) {
                IconButton(
                    onClick = { if (hasCards) contract.showAnswer() },
                    enabled = hasCards && provider.hintAnswer == null
                ) { Icon(Icons.Default.Visibility, "Show answer") }
            }
            if (contract.supportsFlagging) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("Report sentence") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(
                        onClick = { if (hasCards) onShowReport() },
                        enabled = hasCards
                    ) { Icon(Icons.Default.ReportProblem, "Report sentence") }
                }
            }
            Text(
                text = when (contract.currentInputMode) {
                    InputMode.VOICE -> "Voice"
                    InputMode.KEYBOARD -> "Keyboard"
                    InputMode.WORD_BANK -> "Word Bank"
                },
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * Shared report/flag bottom sheet for daily practice.
 * Used by both card session and vocab flashcard blocks to handle
 * flag/unflag, export, and copy-to-clipboard with export confirmation dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReportSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    subtitle: String,
    copyText: String,
    isFlagged: Boolean,
    onFlag: () -> Unit,
    onUnflag: () -> Unit,
    onHideCard: () -> Unit,
    onExport: () -> Unit,
    exportMessage: String?,
    onExportMessageDismiss: () -> Unit,
    clipboardManager: ClipboardManager
) {
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 8.dp))
            }
            if (isFlagged) {
                TextButton(onClick = { onUnflag(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ReportProblem, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remove from bad sentences list")
                }
            } else {
                TextButton(onClick = { onFlag(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ReportProblem, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add to bad sentences list")
                }
            }
            TextButton(onClick = { onExport(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Download, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export bad sentences to file")
            }
            TextButton(onClick = { onHideCard(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.VisibilityOff, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hide this card from lessons")
            }
            TextButton(
                onClick = { if (copyText.isNotBlank()) clipboardManager.setText(AnnotatedString(copyText)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy text")
            }
        }
    }
    if (exportMessage != null) {
        AlertDialog(
            onDismissRequest = onExportMessageDismiss,
            title = { Text("Export") },
            text = { Text(exportMessage!!) },
            confirmButton = { TextButton(onClick = onExportMessageDismiss) { Text("OK") } }
        )
    }
}
