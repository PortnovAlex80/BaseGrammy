package com.alexpo.grammermate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.TrainingScreenMode
import com.alexpo.grammermate.data.VerbDrillCard

/**
 * Renders verb/tense/group info chips below the prompt for VERB_DRILL mode.
 * Tapping verb chip opens VerbReferenceBottomSheet, tense chip opens TenseInfoBottomSheet.
 */
@Composable
internal fun VerbDrillChips(
    card: VerbDrillCard,
    onVerbClick: () -> Unit = {},
    onTenseClick: () -> Unit = {}
) {
    val verbText = card.verb
    if (!verbText.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip(
                onClick = onVerbClick,
                label = {
                    Text(
                        text = if (card.rank != null) "$verbText #${card.rank}" else verbText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                icon = { Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp)) }
            )
            card.tense?.takeIf { it.isNotBlank() }?.let { tenseText ->
                SuggestionChip(
                    onClick = onTenseClick,
                    label = {
                        Text(
                            text = abbreviateTense(tenseText),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                )
            }
            card.group?.takeIf { it.isNotBlank() }?.let { groupText ->
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = groupText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                )
            }
        }
    }
}

/** Abbreviates Italian tense names for compact chip display. */
private fun abbreviateTense(tense: String): String {
    return mapOf(
        "Presente" to "Pres.",
        "Imperfetto" to "Imperf.",
        "Passato Prossimo" to "P. Pross.",
        "Passato Remoto" to "P. Rem.",
        "Trapassato Prossimo" to "Trap. P.",
        "Futuro Semplice" to "Fut. Sempl.",
        "Futuro Anteriore" to "Fut. Ant.",
        "Condizionale Presente" to "Cond. Pres.",
        "Condizionale Passato" to "Cond. Pass.",
        "Congiuntivo Presente" to "Cong. Pres.",
        "Congiuntivo Imperfetto" to "Cong. Imp.",
        "Congiuntivo Passato" to "Cong. Pass."
    )[tense] ?: tense.take(8)
}

/**
 * Completion screen shown when a VerbDrill session finishes.
 * Displays correct/incorrect stats and offers More (next batch) and Exit buttons.
 */
@Composable
internal fun VerbDrillCompletionContent(
    correctCount: Int,
    incorrectCount: Int,
    onMore: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎉",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.verb_completion_excellent),
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.verb_completion_stats, correctCount, incorrectCount),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onMore,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.verb_more))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.verb_exit))
        }
    }
}

/**
 * Universal session completion screen for NORMAL, DRILL, ELITE, BOSS,
 * DAILY_TRANSLATE, DAILY_VERBS modes.
 * Shows stats + OK button. Stays until user presses OK.
 */
@Composable
internal fun SessionCompletionContent(
    modifier: Modifier = Modifier,
    mode: TrainingScreenMode,
    correctCount: Int,
    incorrectCount: Int,
    onDone: () -> Unit
) {
    val title = when (mode) {
        TrainingScreenMode.BOSS, TrainingScreenMode.BOSS_MEGA -> stringResource(R.string.training_review_session)
        TrainingScreenMode.ELITE -> stringResource(R.string.training_refresh_session)
        TrainingScreenMode.VERB_DRILL -> "Verb Drill Complete!"
        else -> stringResource(R.string.verb_completion_excellent)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🎉", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        val total = correctCount + incorrectCount
        val rate = if (total > 0) correctCount * 100 / total else 0
        Text(
            text = "$correctCount correct / $incorrectCount incorrect ($rate%)",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("OK")
        }
    }
}
