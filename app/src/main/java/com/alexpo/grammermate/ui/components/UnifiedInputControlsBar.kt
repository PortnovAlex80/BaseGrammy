package com.alexpo.grammermate.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.CardSessionContract
import com.alexpo.grammermate.data.InputMode

/**
 * Unified input controls bar for all card session modes (Training, VerbDrill, DailyPractice).
 *
 * Renders a single container with:
 * - Input field (text field with Mic trailing icon)
 * - Voice mode hint text
 * - Word bank FlowRow (below the bar, as CONTENT)
 * - Input mode selector row: Mic / Keyboard / WordBank buttons
 * - Show Answer (eye icon) button
 * - Report (flag) button
 * - Current input mode label
 * - Check/Submit button (full width)
 *
 * Design Principle DP-01: All card-based drill modes MUST use identical
 * input controls for shared elements.
 *
 * @param contract          The card session contract providing state and actions.
 * @param inputText         Current text in the input field.
 * @param onInputChanged    Callback when input text changes.
 * @param onSubmit          Callback to submit the current answer.
 * @param hasCards          Whether there is a current card available.
 * @param hintAnswer        Currently shown hint answer (null if no hint visible).
 * @param showIncorrectFeedback Whether incorrect feedback is shown above input.
 * @param incorrectMessage  Optional text to display when answer is incorrect (e.g. "3 attempts left").
 * @param onClearIncorrectFeedback Callback to dismiss incorrect feedback.
 * @param onShowReport      Callback to open the report sheet.
 * @param onReportCard      The card to report, or null.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UnifiedInputControlsBar(
    contract: CardSessionContract,
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    hasCards: Boolean,
    hintAnswer: String? = null,
    showIncorrectFeedback: Boolean = false,
    incorrectMessage: String? = null,
    onClearIncorrectFeedback: () -> Unit = {},
    onShowReport: () -> Unit = {},
    reportCard: com.alexpo.grammermate.data.SessionCard? = null
) {
    val canLaunchVoice = hasCards && contract.sessionActive
    val canSelectInputMode = hasCards && contract.sessionActive

    // Voice recognition launcher
    val latestContract by rememberUpdatedState(contract)
    val latestOnInputChanged by rememberUpdatedState(onInputChanged)
    val latestOnSubmit by rememberUpdatedState(onSubmit)
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                latestContract.onVoiceInputResult(spoken)
            }
        }
    }

    // Helper to launch voice recognition with correct language tag
    val launchVoice: () -> Unit = {
        val languageId = contract.languageId
        val languageTag = when (languageId) {
            "it" -> "it-IT"
            else -> "en-US"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the translation")
        }
        speechLauncher.launch(intent)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hint answer text (eye button)
        if (hintAnswer != null) {
            HintAnswerCard(answerText = hintAnswer)
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
            onValueChange = { newText ->
                if (showIncorrectFeedback) onClearIncorrectFeedback()
                onInputChanged(newText)
                // Auto-submit in keyboard mode when typed text matches accepted answer
                if (contract.currentInputMode == InputMode.KEYBOARD &&
                    contract.sessionActive &&
                    hasCards &&
                    contract.currentCard != null &&
                    newText.isNotBlank()
                ) {
                    if (com.alexpo.grammermate.data.Normalizer.isExactMatch(
                            newText,
                            contract.currentCard!!.acceptedAnswers
                        )
                    ) {
                        onSubmit()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.card_label_your_translation)) },
            singleLine = true,
            enabled = hasCards,
            trailingIcon = {
                if (contract.supportsVoiceInput) {
                    IconButton(
                        onClick = {
                            if (canLaunchVoice) {
                                contract.setInputMode(InputMode.VOICE)
                                launchVoice()
                            }
                        },
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
        if (contract.currentInputMode == InputMode.VOICE && contract.sessionActive) {
            Text(
                text = contract.currentCard?.promptRu?.let {
                    stringResource(R.string.voice_say_translation_hint, it)
                } ?: "",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Word Bank UI -- rendered as CONTENT below the bar, not inside it
        if (contract.currentInputMode == InputMode.WORD_BANK && contract.supportsWordBank) {
            WordBankSection(contract = contract)
        }

        // Input mode selector row + show answer + report + mode label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Voice mode button -- sets input mode AND launches speech directly
                if (contract.supportsVoiceInput) {
                    FilledTonalIconButton(
                        onClick = {
                            if (canLaunchVoice) {
                                contract.setInputMode(InputMode.VOICE)
                                launchVoice()
                            }
                        },
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
                    onClick = { contract.setInputMode(InputMode.KEYBOARD) },
                    enabled = canSelectInputMode
                ) {
                    Icon(
                        Icons.Default.Keyboard,
                        contentDescription = stringResource(R.string.content_desc_keyboard_mode)
                    )
                }
                // Word bank mode button
                if (contract.supportsWordBank) {
                    FilledTonalIconButton(
                        onClick = { contract.setInputMode(InputMode.WORD_BANK) },
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
                        onClick = { if (hasCards) contract.showAnswer() },
                        enabled = hasCards && hintAnswer == null
                    ) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.tooltip_show_answer)
                        )
                    }
                }
                // Flag/Report button
                if (contract.supportsFlagging) {
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
                            onClick = { if (hasCards) onShowReport() },
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
                    text = when (contract.currentInputMode) {
                        InputMode.VOICE -> stringResource(R.string.input_mode_voice)
                        InputMode.KEYBOARD -> stringResource(R.string.input_mode_keyboard)
                        InputMode.WORD_BANK -> stringResource(R.string.input_mode_word_bank)
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        // Check button
        Button(
            onClick = { onSubmit() },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasCards && inputText.isNotBlank() && contract.canSubmit
        ) {
            Text(text = stringResource(R.string.button_check))
        }
    }
}
