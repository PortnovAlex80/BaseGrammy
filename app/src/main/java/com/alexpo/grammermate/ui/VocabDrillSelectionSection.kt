package com.alexpo.grammermate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.VocabDrillDirection
import com.alexpo.grammermate.data.VocabDrillUiState
import com.alexpo.grammermate.shared.AuditLogger

// -- Selection Screen --

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun VocabDrillSelectionScreen(
    state: VocabDrillUiState,
    onSelectPos: (String?) -> Unit,
    onSetRankRange: (Int, Int) -> Unit,
    onSetDirection: (VocabDrillDirection) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                AuditLogger.getInstanceOrNull()?.backPress("vocab_drill")
                onBack()
            }) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.vocab_content_desc_back)) }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.vocab_title), fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Direction filter chips
        Text(text = stringResource(R.string.vocab_direction), style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = state.drillDirection == VocabDrillDirection.IT_TO_RU,
                onClick = {
                    AuditLogger.getInstanceOrNull()?.settingsChange("vocab_direction", "IT_TO_RU")
                    onSetDirection(VocabDrillDirection.IT_TO_RU)
                },
                label = { Text(stringResource(R.string.vocab_direction_it_ru)) }
            )
            FilterChip(
                selected = state.drillDirection == VocabDrillDirection.RU_TO_IT,
                onClick = {
                    AuditLogger.getInstanceOrNull()?.settingsChange("vocab_direction", "RU_TO_IT")
                    onSetDirection(VocabDrillDirection.RU_TO_IT)
                },
                label = { Text(stringResource(R.string.vocab_direction_ru_it)) }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // POS filter chips
        Text(text = stringResource(R.string.vocab_part_of_speech), style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = state.selectedPos == null,
                onClick = {
                    AuditLogger.getInstanceOrNull()?.settingsChange("vocab_pos", "all")
                    onSelectPos(null)
                },
                label = { Text(stringResource(R.string.vocab_filter_all)) }
            )
            state.availablePos.forEach { pos ->
                FilterChip(
                    selected = state.selectedPos == pos,
                    onClick = {
                        AuditLogger.getInstanceOrNull()?.settingsChange("vocab_pos", pos)
                        onSelectPos(pos)
                    },
                    label = { Text(posLabel(pos)) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Rank range filter
        Text(text = stringResource(R.string.vocab_word_frequency), style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val rankOptions = listOf(
                stringResource(R.string.vocab_rank_top100) to (0 to 100),
                stringResource(R.string.vocab_rank_top500) to (0 to 500),
                stringResource(R.string.vocab_rank_top1000) to (0 to 1000),
                stringResource(R.string.vocab_rank_all) to (0 to Int.MAX_VALUE)
            )
            rankOptions.forEach { (label, range) ->
                val isSelected = state.rankMin == range.first && state.rankMax == range.second
                FilterChip(
                    selected = isSelected,
                    onClick = { onSetRankRange(range.first, range.second) },
                    label = { Text(label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Stats
        if (state.totalCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.vocab_due_count, state.dueCount, state.totalCount),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    if (state.masteredCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.vocab_mastered_count, state.masteredCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MasteryGreen
                        )
                        // Per-POS breakdown
                        val posBreakdown = state.masteredByPos.entries
                            .sortedByDescending { it.value }
                            .map { (pos, count) -> "${posLabel(pos)}: $count" }
                        if (posBreakdown.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = posBreakdown.joinToString(" | "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = if (state.totalCount > 0) {
                            val learned = state.totalCount - state.dueCount
                            learned.toFloat() / state.totalCount.toFloat()
                        } else 0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.vocab_no_words_loaded),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.weight(1f))

        // Start button
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.dueCount > 0
        ) {
            Text(text = if (state.dueCount > 0) stringResource(R.string.vocab_start, state.dueCount) else stringResource(R.string.vocab_no_due_words))
        }
    }
}

// -- Helpers --

/**
 * Localized label for a raw POS key (chip labels on the selection screen and
 * the per-POS mastery breakdown). One owner for the previously duplicated
 * `when` mapping.
 */
@Composable
private fun posLabel(pos: String): String = when (pos) {
    "nouns" -> stringResource(R.string.vocab_pos_nouns)
    "verbs" -> stringResource(R.string.vocab_pos_verbs)
    "adjectives" -> stringResource(R.string.vocab_pos_adjectives)
    "adverbs" -> stringResource(R.string.vocab_pos_adverbs)
    "numbers" -> stringResource(R.string.vocab_pos_numbers)
    "pronouns" -> stringResource(R.string.vocab_pos_pronouns)
    else -> pos.replaceFirstChar { it.uppercase() }
}
