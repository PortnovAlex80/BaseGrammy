package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.CardSessionContract
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.feature.daily.DailyPracticeSessionProvider

/**
 * Word bank chip grid with undo control.
 * Shared across TrainingScreen, TrainingCardSession, DailyPracticeScreen, and VerbDrillScreen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordBankSection(contract: CardSessionContract) {
    WordBankSection(
        wordBankWords = contract.getWordBankWords(),
        selectedWords = contract.getSelectedWords(),
        onSelectWord = { contract.selectWordFromBank(it) },
        onRemoveLastWord = { contract.removeLastSelectedWord() }
    )
}

/**
 * Word bank chip grid with undo control — raw parameter overload.
 * Use this when the caller does not have a [CardSessionContract] instance.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordBankSection(
    wordBankWords: List<String>,
    selectedWords: List<String>,
    onSelectWord: (String) -> Unit,
    onRemoveLastWord: () -> Unit
) {
    if (wordBankWords.isEmpty()) return

    Text(
        text = stringResource(R.string.word_bank_tap_order),
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
                onClick = { if (!isFullyUsed) onSelectWord(word) },
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
            TextButton(onClick = onRemoveLastWord) { Text(stringResource(R.string.word_bank_undo)) }
        }
    }
}

/** Backward-compatible alias. Prefer [WordBankSection]. */
@Composable
fun DailyWordBankSection(contract: CardSessionContract) {
    WordBankSection(contract)
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
    hintLevel: HintLevel = HintLevel.EASY,
    onLaunchVoice: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(
                onClick = {
                    if (canLaunchVoice) {
                        onClearInput()
                        contract.setInputMode(InputMode.VOICE)
                        onLaunchVoice()
                    }
                },
                enabled = canLaunchVoice
            ) { Icon(Icons.Default.Mic, stringResource(R.string.content_desc_voice_mode)) }
            FilledTonalIconButton(
                onClick = { contract.setInputMode(InputMode.KEYBOARD) },
                enabled = canSelectInputMode
            ) { Icon(Icons.Default.Keyboard, stringResource(R.string.content_desc_keyboard_mode)) }
            FilledTonalIconButton(
                onClick = { contract.setInputMode(InputMode.WORD_BANK) },
                enabled = canSelectInputMode
            ) { Icon(Icons.Default.LibraryBooks, stringResource(R.string.content_desc_word_bank_mode)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(stringResource(R.string.tooltip_show_answer)) } },
                state = rememberTooltipState()
            ) {
                IconButton(
                    onClick = { if (hasCards) contract.showAnswer() },
                    enabled = hasCards && provider.hintAnswer == null
                ) { Icon(Icons.Default.Visibility, stringResource(R.string.tooltip_show_answer)) }
            }
            if (contract.supportsFlagging) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.tooltip_report_sentence)) } },
                    state = rememberTooltipState()
                ) {
                    IconButton(
                        onClick = { if (hasCards) onShowReport() },
                        enabled = hasCards
                    ) { Icon(Icons.Default.ReportProblem, stringResource(R.string.tooltip_report_sentence)) }
                }
            }
            Text(
                text = when (contract.currentInputMode) {
                    InputMode.VOICE -> stringResource(R.string.input_mode_voice)
                    InputMode.KEYBOARD -> stringResource(R.string.input_mode_keyboard)
                    InputMode.WORD_BANK -> stringResource(R.string.input_mode_word_bank)
                },
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
