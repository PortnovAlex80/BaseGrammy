package com.alexpo.grammermate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.VocabDrillCard
import com.alexpo.grammermate.data.VocabDrillDirection
import com.alexpo.grammermate.data.VocabDrillSessionState
import com.alexpo.grammermate.ui.designsystem.foundation.scaledSp
import com.alexpo.grammermate.ui.features.vocab.components.PosBadge
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// -- Card Back --

@Composable
internal fun VocabDrillCardBack(
    card: VocabDrillCard,
    direction: VocabDrillDirection,
    ttsState: TtsState,
    onSpeak: (String) -> Unit,
    hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY,
    textScale: Float = 1.0f
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (direction == VocabDrillDirection.IT_TO_RU) {
                // IT_TO_RU: back shows Russian meaning + Italian word with TTS
                // Italian word (smaller, with TTS)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PosBadge(pos = card.word.pos)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = card.word.word,
                            fontSize = scaledSp(20f, textScale),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(
                        onClick = { onSpeak(card.word.word) },
                        enabled = ttsState != TtsState.Initializing
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = stringResource(R.string.vocab_content_desc_listen))
                    }
                }

                // Russian translation
                val meaningRu = card.word.meaningRu
                if (!meaningRu.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = meaningRu,
                        fontSize = scaledSp(18f, textScale),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                // RU_TO_IT: back shows Italian word + collocations
                // Russian meaning (smaller)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PosBadge(pos = card.word.pos)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = card.word.meaningRu ?: "?",
                        fontSize = scaledSp(16f, textScale),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Italian word (large) with TTS
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = card.word.word,
                        fontSize = scaledSp(24f, textScale),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = { onSpeak(card.word.word) },
                        enabled = ttsState != TtsState.Initializing
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = stringResource(R.string.vocab_content_desc_listen))
                    }
                }
            }

            // Forms -- always visible (reference data, not hints)
            val forms = card.word.forms
            if (forms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.vocab_forms),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val formItems: List<Pair<String, String?>> = when (card.word.pos) {
                            "numbers" -> listOf(
                                "m" to forms["form_m"],
                                "f" to forms["form_f"]
                            )
                            "pronouns" -> listOf(
                                "sg m" to forms["form_sg_m"],
                                "sg f" to forms["form_sg_f"],
                                "pl m" to forms["form_pl_m"],
                                "pl f" to forms["form_pl_f"]
                            )
                            else -> listOf(
                                "m sg" to forms["msg"],
                                "f sg" to forms["fsg"],
                                "m pl" to forms["mpl"],
                                "f pl" to forms["fpl"]
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            formItems.forEach { (label, value) ->
                                FormItem(label = label, value = value)
                            }
                        }
                    }
                }
            }

            // Collocations -- always visible (reference data, not hints)
            val collocations = card.word.collocations
            if (collocations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.vocab_collocations),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    collocations.take(5).forEach { coll ->
                        Text(
                            text = coll,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                    if (collocations.size > 5) {
                        Text(
                            text = stringResource(R.string.vocab_more_count, collocations.size - 5),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Mastery indicator
            Spacer(modifier = Modifier.height(12.dp))
            val step = card.mastery.intervalStepIndex
            val maxStep = 9
            when {
                step >= maxStep -> {
                    // Mastered — gold accent
                    Text(
                        text = stringResource(R.string.vocab_mastered),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB8860B) // dark goldenrod
                    )
                }
                step >= TrainingConfig.LEARNED_THRESHOLD -> {
                    // Learned — green accent
                    Text(
                        text = stringResource(R.string.vocab_learned),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MasteryGreen
                    )
                }
                else -> {
                    // Learning — neutral with step counter
                    Text(
                        text = "${stringResource(R.string.vocab_learning)} — ${stringResource(R.string.vocab_step, step + 1, maxStep)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FormItem(label: String, value: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = value ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// -- Completion Screen --

@Composable
internal fun VocabDrillCompletionScreen(
    session: VocabDrillSessionState,
    onExit: () -> Unit,
    onContinue: () -> Unit
) {
    var showStats by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(800L)
        showStats = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (session.correctCount == session.cards.size) stringResource(R.string.vocab_completion_perfect) else stringResource(R.string.vocab_completion_done),
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (showStats) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${session.correctCount}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.vocab_completion_correct),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${session.incorrectCount}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = stringResource(R.string.vocab_completion_wrong),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.vocab_completion_words_reviewed, session.cards.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.vocab_completion_exit))
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.vocab_completion_continue))
                }
            }
        }
    }
}
