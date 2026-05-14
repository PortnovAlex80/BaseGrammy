package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.ui.TenseExample
import com.alexpo.grammermate.ui.TenseInfo
import com.alexpo.grammermate.ui.VerbDrillViewModel

// ── ViewModel-based overloads (VerbDrillScreen) ────────────────────────────

/**
 * Bottom sheet showing verb conjugation reference for the current verb+tense.
 * ViewModel-based overload for VerbDrillScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VerbReferenceBottomSheet(
    verb: String,
    tense: String?,
    viewModel: VerbDrillViewModel,
    onDismiss: () -> Unit
) {
    val conjugation = remember(verb, tense) {
        viewModel.getConjugationForVerb(verb, tense ?: "")
    }
    val ttsState by viewModel.ttsState.collectAsState()

    VerbReferenceBottomSheet(
        verb = verb,
        tense = tense,
        conjugationCards = conjugation,
        ttsState = ttsState,
        onSpeakVerb = { viewModel.speakVerbInfinitive(verb) },
        onDismiss = onDismiss
    )
}

/**
 * Bottom sheet showing tense reference info: formula, usage in Russian, examples.
 * ViewModel-based overload for VerbDrillScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TenseInfoBottomSheet(
    tenseName: String,
    viewModel: VerbDrillViewModel,
    onDismiss: () -> Unit
) {
    val tenseInfo = remember(tenseName) { viewModel.getTenseInfo(tenseName) }
    TenseInfoBottomSheet(
        tenseName = tenseName,
        tenseInfo = tenseInfo,
        onDismiss = onDismiss
    )
}

// ── Parameter-based overloads (DailyPracticeScreen, etc.) ──────────────────

/**
 * Bottom sheet showing verb conjugation reference — parameter-based version.
 * Use from screens that don't have access to VerbDrillViewModel.
 *
 * @param verb The verb infinitive to display.
 * @param tense The current tense (may be null for cards without tense).
 * @param conjugationCards All VerbDrillCards with matching verb+tense for the conjugation table.
 * @param ttsState Current TTS playback state.
 * @param onSpeakVerb Callback to speak the verb infinitive aloud.
 * @param onDismiss Callback to close the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VerbReferenceBottomSheet(
    verb: String,
    tense: String?,
    conjugationCards: List<VerbDrillCard>,
    ttsState: TtsState,
    onSpeakVerb: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Verb name + TTS button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = verb,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onSpeakVerb,
                    enabled = ttsState != TtsState.INITIALIZING
                ) {
                    when (ttsState) {
                        TtsState.SPEAKING -> Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = stringResource(R.string.content_desc_speaking),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        TtsState.INITIALIZING -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        TtsState.ERROR -> Icon(
                            Icons.Default.ReportProblem,
                            contentDescription = stringResource(R.string.content_desc_tts_error),
                            tint = MaterialTheme.colorScheme.error
                        )
                        else -> Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = stringResource(R.string.content_desc_listen)
                        )
                    }
                }
            }

            // Group label
            val group = conjugationCards.firstOrNull()?.group
            if (!group.isNullOrBlank()) {
                Text(
                    text = "Группа: $group",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Tense label
            if (!tense.isNullOrBlank()) {
                Text(
                    text = "Время: $tense",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Conjugation table
            if (conjugationCards.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.drill_conjugation_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    conjugationCards.forEach { card ->
                        Text(
                            text = card.answer,
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom sheet showing tense reference info — parameter-based version.
 * Use from screens that don't have access to VerbDrillViewModel.
 *
 * @param tenseName The tense name to display (used as fallback header).
 * @param tenseInfo Pre-loaded tense info (may be null if not available).
 * @param onDismiss Callback to close the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TenseInfoBottomSheet(
    tenseName: String,
    tenseInfo: TenseInfo?,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tenseInfo == null) {
                // Fallback: show abbreviated name only
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tenseName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = stringResource(R.string.drill_tense_info_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                // Tense name header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tenseInfo.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${tenseInfo.short})",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                // Formula card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.drill_formula_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tenseInfo.formula,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Usage explanation
                Text(
                    text = stringResource(R.string.drill_when_to_use_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = tenseInfo.usageRu,
                    style = MaterialTheme.typography.bodyMedium
                )

                // Examples
                if (tenseInfo.examples.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.drill_examples_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tenseInfo.examples.forEach { example ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = example.it,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = example.ru,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    if (example.note.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = example.note,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
