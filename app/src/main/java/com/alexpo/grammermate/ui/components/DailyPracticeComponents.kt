package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.CardSessionContract

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
