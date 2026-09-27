package com.alexpo.grammermate.ui.features.training.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.ThemeMode
import com.alexpo.grammermate.ui.GrammarMateTheme
import com.alexpo.grammermate.ui.components.ClickableHintAnswerCard
import com.alexpo.grammermate.ui.components.HintAnswerCard
import com.alexpo.grammermate.ui.components.WordBankSection
import com.alexpo.grammermate.ui.components.WordHint
import com.alexpo.grammermate.ui.components.WordInfoPopup

// =====================================================================
// Training answer panel — feature organism (training feature owner)
// =====================================================================

/**
 * Display-only answer input surface for card sessions (P3b extraction of
 * `ui/components/UnifiedInputControlsBar.kt`, which remains the integration
 * boundary adapter with an unchanged signature). Design Principle DP-01:
 * all card-based drill modes use identical input controls.
 *
 * Owns here by contract (plan §4): rendering + one piece of transient visual
 * state — the word-info popup remembers (inventory effect ledger row 25; plain
 * remember, no keys, same composition position as before extraction). The
 * word-bank chip grid is the existing display-only
 * [WordBankSection] raw-parameter overload (tags `word_bank_container`,
 * `word_bank_chip_*`, `word_bank_undo` unchanged).
 *
 * Stays at the boundary adapter (NOT here), per the P3b effect ledger:
 * - both voice recognizer launchers and their click flows (rows 1/2, D-A) —
 *   surfaced as [onMicClick] / [onVoiceModeClick];
 * - the pause-resume-first interplay on voice entry (row 26);
 * - WordInfoCache remember/`LaunchedEffect` load and the `getWordsInfo` read
 *   (row 13, D-B) — this component receives [wordsInfo] as plain data;
 * - auto-submit on exact match + incorrect-feedback clearing, fused into
 *   [onInputChanged] by the adapter (row 12, D-D);
 * - hint calculation for the voice-mode hint (passed resolved as
 *   [voiceModeHint]; `null` hides the line, `""` renders the empty line —
 *   matching the pre-extraction rendering exactly);
 * - Bluetooth setup/cleanup callbacks (row 5, D-C — cleanup stays threaded-but-
 *   unused at the adapter);
 * - input-mode enum reading ([modeLabel] arrives pre-resolved; no `InputMode`
 *   import here).
 *
 * Parity anchors preserved verbatim from the pre-extraction bar:
 * - tags `input_field`, `check_button`, `show_answer_button` (+ word-bank tags
 *   inside [WordBankSection]);
 * - show-answer enabled = `hasCards && hintAnswer == null`; check enabled =
 *   `hasCards && inputText.isNotBlank() && canSubmit`;
 * - all localized strings/content descriptions resolved inside;
 * - the `ClickableHintAnswerCard` vs `HintAnswerCard` selection is driven by
 *   [wordsInfo] != null (adapter maps `clickableWordHints && cache != null`).
 *
 * @param wordsInfo per-word hints for the clickable answer card, or null for
 *   the plain (non-clickable) hint card. May be an empty map (clickable mode
 *   active, no drill data for the words) — parity with the old rendering.
 * @param voiceModeHint resolved hint line for VOICE mode; null = line hidden.
 * @param wordBankVisible whether the word-bank grid section is rendered.
 * @param wordBankModeButtonVisible whether the word-bank mode button is shown.
 * @param modeLabel pre-resolved current input mode label.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingAnswerPanel(
    modifier: Modifier = Modifier,
    inputText: String,
    onInputChanged: (String) -> Unit,
    hasCards: Boolean,
    canSubmit: Boolean,
    canLaunchVoice: Boolean,
    canSelectInputMode: Boolean,
    supportsVoiceInput: Boolean,
    supportsFlagging: Boolean,
    showIncorrectFeedback: Boolean,
    incorrectMessage: String?,
    hintAnswer: String?,
    wordsInfo: Map<String, WordHint>?,
    voiceModeHint: String?,
    wordBankVisible: Boolean,
    wordBankWords: List<String>,
    selectedWords: List<String>,
    onSelectWord: (String) -> Unit,
    onRemoveLastWord: () -> Unit,
    onMicClick: () -> Unit,
    onVoiceModeClick: () -> Unit,
    onKeyboardModeClick: () -> Unit,
    wordBankModeButtonVisible: Boolean,
    onWordBankModeClick: () -> Unit,
    onShowAnswerClick: () -> Unit,
    onShowReportClick: () -> Unit,
    modeLabel: String,
    onSubmitClick: () -> Unit
) {
    // Word info popup — transient visual state (ledger row 25)
    var showWordPopup by remember { mutableStateOf(false) }
    var wordForPopup by remember { mutableStateOf<String?>(null) }
    var hintForPopup by remember { mutableStateOf<WordHint?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hint answer text (eye button)
        if (hintAnswer != null) {
            if (wordsInfo != null) {
                ClickableHintAnswerCard(
                    answerText = hintAnswer,
                    wordsInfo = wordsInfo,
                    onWordClick = { word, hint ->
                        wordForPopup = word
                        hintForPopup = hint
                        showWordPopup = true
                    }
                )
            } else {
                HintAnswerCard(answerText = hintAnswer)
            }
        }

        // Incorrect feedback
        if (showIncorrectFeedback) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.result_incorrect),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (incorrectMessage != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = incorrectMessage,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Input text field
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChanged,
            modifier = Modifier.fillMaxWidth().testTag("input_field"),
            label = { Text(text = stringResource(R.string.card_label_your_translation)) },
            singleLine = true,
            enabled = hasCards,
            trailingIcon = {
                if (supportsVoiceInput) {
                    IconButton(
                        onClick = onMicClick,
                        enabled = canLaunchVoice
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = stringResource(R.string.content_desc_voice_input)
                        )
                    }
                }
            }
        )

        if (!hasCards) {
            Text(
                text = stringResource(R.string.card_no_cards),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Voice mode hint
        if (voiceModeHint != null) {
            Text(
                text = voiceModeHint,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Word Bank UI -- rendered as CONTENT below the bar, not inside it
        if (wordBankVisible) {
            WordBankSection(
                wordBankWords = wordBankWords,
                selectedWords = selectedWords,
                onSelectWord = onSelectWord,
                onRemoveLastWord = onRemoveLastWord
            )
        }

        // Input mode selector row + show answer + report + mode label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Voice mode button -- sets input mode AND launches speech directly
                if (supportsVoiceInput) {
                    FilledTonalIconButton(
                        onClick = onVoiceModeClick,
                        enabled = canLaunchVoice
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = stringResource(R.string.content_desc_voice_mode)
                        )
                    }
                }
                // Keyboard mode button
                FilledTonalIconButton(
                    onClick = onKeyboardModeClick,
                    enabled = canSelectInputMode
                ) {
                    Icon(
                        Icons.Default.Keyboard,
                        contentDescription = stringResource(R.string.content_desc_keyboard_mode)
                    )
                }
                // Word bank mode button
                if (wordBankModeButtonVisible) {
                    FilledTonalIconButton(
                        onClick = onWordBankModeClick,
                        enabled = canSelectInputMode
                    ) {
                        Icon(
                            Icons.Default.LibraryBooks,
                            contentDescription = stringResource(R.string.content_desc_word_bank_mode)
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show answer button
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = {
                        PlainTooltip {
                            Text(text = stringResource(R.string.tooltip_show_answer))
                        }
                    },
                    state = rememberTooltipState()
                ) {
                    IconButton(
                        modifier = Modifier.testTag("show_answer_button"),
                        onClick = onShowAnswerClick,
                        enabled = hasCards && hintAnswer == null
                    ) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.tooltip_show_answer)
                        )
                    }
                }
                // Flag/Report button
                if (supportsFlagging) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(text = stringResource(R.string.tooltip_report_sentence))
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = onShowReportClick,
                            enabled = hasCards
                        ) {
                            Icon(
                                Icons.Default.ReportProblem,
                                contentDescription = stringResource(R.string.tooltip_report_sentence)
                            )
                        }
                    }
                }
                // Current mode label
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        // Check button
        Button(
            onClick = onSubmitClick,
            modifier = Modifier.fillMaxWidth().testTag("check_button"),
            enabled = hasCards && inputText.isNotBlank() && canSubmit
        ) {
            Text(text = stringResource(R.string.button_check))
        }

        // Word info popup
        val word = wordForPopup
        val hint = hintForPopup
        if (showWordPopup && word != null && hint != null) {
            WordInfoPopup(
                word = word,
                hint = hint,
                onDismiss = {
                    showWordPopup = false
                    wordForPopup = null
                    hintForPopup = null
                }
            )
        }
    }
}

// =====================================================================
// Preview fixtures — pure data only: no ViewModel, filesystem, installed
// pack or speech engine (plan §7). Each wraps the real GrammarMateTheme
// facade with an explicit ThemeMode, matching the P1/P2/P3a convention.
// ======================================================================

/**
 * Preview-only fixture wiring the panel's required parameters to a realistic
 * keyboard-mode baseline; individual previews override the values they show.
 * Not production API — pure pass-through to [TrainingAnswerPanel].
 */
@Composable
private fun TrainingAnswerPanelFixture(
    inputText: String = "",
    hasCards: Boolean = true,
    canSubmit: Boolean = true,
    supportsVoiceInput: Boolean = false,
    showIncorrectFeedback: Boolean = false,
    incorrectMessage: String? = null,
    hintAnswer: String? = null,
    wordsInfo: Map<String, WordHint>? = null,
    voiceModeHint: String? = null,
    wordBankVisible: Boolean = false,
    wordBankWords: List<String> = emptyList(),
    selectedWords: List<String> = emptyList(),
    modeLabel: String = "Keyboard"
) {
    TrainingAnswerPanel(
        inputText = inputText,
        onInputChanged = {},
        hasCards = hasCards,
        canSubmit = canSubmit,
        canLaunchVoice = hasCards && canSubmit,
        canSelectInputMode = hasCards && canSubmit,
        supportsVoiceInput = supportsVoiceInput,
        supportsFlagging = true,
        showIncorrectFeedback = showIncorrectFeedback,
        incorrectMessage = incorrectMessage,
        hintAnswer = hintAnswer,
        wordsInfo = wordsInfo,
        voiceModeHint = voiceModeHint,
        wordBankVisible = wordBankVisible,
        wordBankWords = wordBankWords,
        selectedWords = selectedWords,
        onSelectWord = {},
        onRemoveLastWord = {},
        onMicClick = {},
        onVoiceModeClick = {},
        onKeyboardModeClick = {},
        wordBankModeButtonVisible = true,
        onWordBankModeClick = {},
        onShowAnswerClick = {},
        onShowReportClick = {},
        modeLabel = modeLabel,
        onSubmitClick = {}
    )
}

private val PreviewWordBankWords = listOf("vorrei", "prenotare", "un", "appuntamento", "dal", "medico")
private val PreviewSelectedWords = listOf("vorrei", "appuntamento")

@Preview(name = "Answer panel — keyboard mode, light", showBackground = true, widthDp = 360, heightDp = 300)
@Composable
private fun TrainingAnswerPanelKeyboardLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingAnswerPanelFixture()
    }
}

@Preview(name = "Answer panel — keyboard mode, dark", showBackground = true, widthDp = 360, heightDp = 300)
@Composable
private fun TrainingAnswerPanelKeyboardDarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        TrainingAnswerPanelFixture()
    }
}

@Preview(name = "Answer panel — voice mode (mic + hint), light", showBackground = true, widthDp = 360, heightDp = 320)
@Composable
private fun TrainingAnswerPanelVoiceLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingAnswerPanelFixture(
            supportsVoiceInput = true,
            voiceModeHint = "Say the translation: «я хотел бы записаться на приём»",
            modeLabel = "Voice"
        )
    }
}

@Preview(name = "Answer panel — voice mode (mic + hint), dark", showBackground = true, widthDp = 360, heightDp = 320)
@Composable
private fun TrainingAnswerPanelVoiceDarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        TrainingAnswerPanelFixture(
            supportsVoiceInput = true,
            voiceModeHint = "Say the translation: «я хотел бы записаться на приём»",
            modeLabel = "Voice"
        )
    }
}

@Preview(name = "Answer panel — word bank populated, light", showBackground = true, widthDp = 360, heightDp = 380)
@Composable
private fun TrainingAnswerPanelWordBankLightPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingAnswerPanelFixture(
            inputText = "vorrei appuntamento",
            wordBankVisible = true,
            wordBankWords = PreviewWordBankWords,
            selectedWords = PreviewSelectedWords,
            modeLabel = "Word bank"
        )
    }
}

@Preview(name = "Answer panel — word bank populated, dark", showBackground = true, widthDp = 360, heightDp = 380)
@Composable
private fun TrainingAnswerPanelWordBankDarkPreview() {
    GrammarMateTheme(themeMode = ThemeMode.DARK) {
        TrainingAnswerPanelFixture(
            inputText = "vorrei appuntamento",
            wordBankVisible = true,
            wordBankWords = PreviewWordBankWords,
            selectedWords = PreviewSelectedWords,
            modeLabel = "Word bank"
        )
    }
}

@Preview(name = "Answer panel — plain hint card (show answer disabled)", showBackground = true, widthDp = 360, heightDp = 340)
@Composable
private fun TrainingAnswerPanelPlainHintPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingAnswerPanelFixture(
            hintAnswer = "Vorrei prenotare un appuntamento",
            modeLabel = "Keyboard"
        )
    }
}

@Preview(name = "Answer panel — clickable word hints (rank colors)", showBackground = true, widthDp = 360, heightDp = 340)
@Composable
private fun TrainingAnswerPanelClickableHintsPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingAnswerPanelFixture(
            hintAnswer = "Vorrei prenotare un appuntamento",
            wordsInfo = mapOf(
                "vorrei" to WordHint(translation = "я хотел бы", rank = 320, partOfSpeech = "verbs"),
                "prenotare" to WordHint(translation = "записаться, бронировать", rank = 1500, partOfSpeech = "verbs"),
                "appuntamento" to WordHint(translation = "приём, встреча", rank = 3400, partOfSpeech = "nouns")
            )
        )
    }
}

@Preview(name = "Answer panel — incorrect feedback with message", showBackground = true, widthDp = 360, heightDp = 320)
@Composable
private fun TrainingAnswerPanelIncorrectFeedbackPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingAnswerPanelFixture(
            inputText = "wrong answer",
            showIncorrectFeedback = true,
            incorrectMessage = "3 attempts left"
        )
    }
}

@Preview(name = "Answer panel — no cards (disabled + message)", showBackground = true, widthDp = 360, heightDp = 300)
@Composable
private fun TrainingAnswerPanelNoCardsPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingAnswerPanelFixture(hasCards = false, canSubmit = false)
    }
}

@Preview(name = "Answer panel — keyboard mode, system font scale 1.5x", showBackground = true, widthDp = 360, heightDp = 360, fontScale = 1.5f)
@Composable
private fun TrainingAnswerPanelLargeFontPreview() {
    GrammarMateTheme(themeMode = ThemeMode.LIGHT) {
        TrainingAnswerPanelFixture(
            inputText = "vorrei prenotare",
            showIncorrectFeedback = true,
            incorrectMessage = "3 attempts left"
        )
    }
}
