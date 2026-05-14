package com.alexpo.grammermate.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.AnswerResult
import com.alexpo.grammermate.data.CardSessionContract
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.ui.components.HintAnswerCard
import com.alexpo.grammermate.ui.components.NavIconButton
import com.alexpo.grammermate.ui.components.QrShareDialog
import com.alexpo.grammermate.ui.components.SessionProgressIndicator
import com.alexpo.grammermate.ui.components.SharedReportSheet
import com.alexpo.grammermate.ui.components.TtsSpeakerButton
import com.alexpo.grammermate.ui.components.WordBankSection

/**
 * Scope object passed to customization slots inside [TrainingCardSession].
 * Provides access to the contract and local UI state.
 */
@Stable
class TrainingCardSessionScope(
    val contract: CardSessionContract,
    val currentCard: SessionCard?,
    val isShowingResult: Boolean,
    val lastResult: AnswerResult?,
    val progressText: String,
    val inputText: String,
    val onInputChanged: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onPrev: () -> Unit,
    val onNext: () -> Unit,
    val onExit: () -> Unit,
    val hintLevel: HintLevel = HintLevel.EASY,
    val textScale: Float = 1.0f
)

/**
 * Reusable composable for card-based training sessions.
 *
 * Result tracking is delegated to the [CardSessionContract] adapter. The
 * adapter's [CardSessionContract.lastResult] and [CardSessionContract.currentCard]
 * drive whether the result or input UI is shown. Adapters that need custom
 * submit logic (e.g. [VerbDrillCardSessionProvider]) can bypass the default
 * [CardSessionContract.submitAnswer] flow and set their own result state.
 *
 * @param contract Adapter that provides cards, state, and actions.
 * @param header Optional slot for the top header area. Default: tense label + clean prompt.
 * @param cardContent Optional slot for the card display. Default: Card with Russian prompt + TTS.
 * @param inputControls Optional slot for the input area. Default: text field + word bank + voice + submit.
 * @param resultContent Optional slot for answer feedback. Default: correct/incorrect label + answer + TTS replay.
 * @param navigationControls Optional slot for bottom navigation. Default: prev/pause/exit/next buttons.
 * @param completionScreen Optional slot for the completed state. Default: congratulations + stats.
 * @param progressIndicator Optional slot for progress display. Default: [SessionProgressIndicator].
 * @param onExit Called when the user requests to exit.
 * @param onComplete Called when the session is completed.
 * @param modifier Modifier for the root layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingCardSession(
    contract: CardSessionContract,
    header: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    cardContent: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    inputControls: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    resultContent: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    navigationControls: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    completionScreen: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    progressIndicator: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    onExit: () -> Unit,
    onComplete: () -> Unit = {},
    modifier: Modifier = Modifier,
    hintLevel: HintLevel = HintLevel.EASY
) {
    // Local input text state managed by the composable
    var localInputText by remember { mutableStateOf("") }

    // Read result state from the contract/adapter
    val lastResult = contract.lastResult
    val isShowingResult = lastResult != null
    val currentCard = contract.currentCard
    val progress = contract.progress

    // Create scope for customization slots
    val scope = remember(contract, currentCard, isShowingResult, lastResult, localInputText, hintLevel) {
        TrainingCardSessionScope(
            contract = contract,
            currentCard = currentCard,
            isShowingResult = isShowingResult,
            lastResult = lastResult,
            progressText = "${progress.current} / ${progress.total}",
            inputText = localInputText,
            onInputChanged = { localInputText = it },
            onSubmit = {
                if (localInputText.isNotBlank()) {
                    contract.onInputChanged(localInputText)
                    contract.submitAnswer()
                    // If submitAnswer returned null, the adapter manages its own result state
                    localInputText = ""
                }
            },
            onNext = {
                contract.nextCard()
                localInputText = ""
                if (contract.isComplete) {
                    onComplete()
                }
            },
            onPrev = {
                contract.prevCard()
                localInputText = ""
            },
            onExit = onExit,
            hintLevel = hintLevel,
            textScale = contract.textScale
        )
    }

    // Completion screen
    if (contract.isComplete && !isShowingResult) {
        if (completionScreen != null) {
            scope.completionScreen()
        } else {
            DefaultCompletionScreen(scope)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        if (header != null) {
            scope.header()
        } else {
            DefaultHeader(scope)
        }

        // Progress indicator
        if (progressIndicator != null) {
            scope.progressIndicator()
        } else {
            SessionProgressIndicator(
                current = scope.contract.progress.current,
                total = scope.contract.progress.total,
                speedWpm = scope.contract.currentSpeedWpm
            )
        }

        // Card content
        if (cardContent != null) {
            scope.cardContent()
        } else {
            DefaultCardContent(scope)
        }

        // Result or input
        if (isShowingResult) {
            if (resultContent != null) {
                scope.resultContent()
            } else {
                DefaultResultContent(scope)
            }
        } else if (currentCard != null) {
            if (inputControls != null) {
                scope.inputControls()
            } else {
                DefaultInputControls(scope)
            }
        }

        // Navigation controls
        if (navigationControls != null) {
            scope.navigationControls()
        } else {
            DefaultNavigationControls(scope)
        }
    }
}

// --- Default implementations (matching GrammarMateApp visual style) ---

/**
 * Header matching GrammarMateApp's TrainingScreen header:
 * tense label (green/primary, 13sp, semi-bold) + clean prompt text (parenthetical hints stripped).
 */
@Composable
private fun DefaultHeader(scope: TrainingCardSessionScope) {
    val card = scope.currentCard
    // Tense label -- always visible (reference data, not a hint)
    val cardTense = card?.let {
        // VerbDrillCard has a tense field; SentenceCard also has tense
        if (it is com.alexpo.grammermate.data.VerbDrillCard) it.tense else null
    }
    if (!cardTense.isNullOrBlank()) {
        Text(
            text = cardTense,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
    }
    // Clean prompt (strip parenthetical hints)
    val rawPrompt = card?.promptRu ?: ""
    val cleanPrompt = rawPrompt.replace(Regex("\\s*\\([^)]+\\)"), "")
    if (cleanPrompt.isNotBlank()) {
        Text(
            text = cleanPrompt,
            fontSize = (18f * scope.textScale).sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Card content matching GrammarMateApp's CardPrompt:
 * Material Card with "RU" label + prompt text (20sp, semi-bold) + TtsSpeakerButton on right.
 */
@Composable
private fun DefaultCardContent(scope: TrainingCardSessionScope) {
    val card = scope.currentCard ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.card_label_ru), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = card.promptRu,
                    fontSize = (20f * scope.textScale).sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TtsSpeakerButton(
                ttsState = scope.contract.ttsState,
                enabled = card.promptRu.isNotBlank(),
                onClick = { scope.contract.speakTts() }
            )
        }
    }
}

/**
 * Input controls matching GrammarMateApp's AnswerBox:
 * - OutlinedTextField with "Your translation" label + Mic trailing icon
 * - Voice mode hint text
 * - Word bank FlowRow with FilterChips + Undo button
 * - Input mode selector row: Mic, Keyboard, Book FilledTonalIconButtons
 * - Show answer button (Eye icon with tooltip)
 * - Report/Flag button (Warning icon with tooltip) -- opens SharedReportSheet
 * - "Check" button (full width)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DefaultInputControls(scope: TrainingCardSessionScope) {
    val contract = scope.contract
    val hasCards = scope.currentCard != null
    val clipboardManager = LocalClipboardManager.current
    var showReportSheet by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    val reportCard = scope.currentCard
    val reportText = if (reportCard != null) {
        val targetText = reportCard.acceptedAnswers.joinToString(" / ")
        "ID: ${reportCard.id}\nSource: ${reportCard.promptRu}\nTarget: $targetText"
    } else {
        ""
    }

    // Voice recognition launcher
    val latestContract by rememberUpdatedState(contract)
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                latestContract.onVoiceInputResult(spoken)
            }
        }
    }

    // Report sheet
    if (showReportSheet) {
        SharedReportSheet(
            onDismiss = { showReportSheet = false },
            cardPromptText = reportCard?.promptRu,
            isFlagged = contract.isCurrentCardFlagged(),
            onFlag = { contract.flagCurrentCard() },
            onUnflag = { contract.unflagCurrentCard() },
            onHideCard = { contract.hideCurrentCard() },
            onExportBadSentences = { contract.exportFlaggedCards() },
            onCopyText = {
                if (reportText.isNotBlank()) {
                    clipboardManager.setText(AnnotatedString(reportText))
                }
            },
            shareText = reportCard?.let { "${it.promptRu} — ${it.acceptedAnswers.joinToString(" / ")}" },
            onShareQr = { showQrDialog = true }
        )
    }
    if (showQrDialog && reportCard != null) {
        QrShareDialog(
            promptRu = reportCard.promptRu,
            answerText = reportCard.acceptedAnswers.firstOrNull() ?: "",
            targetLanguage = contract.languageId,
            onDismiss = { showQrDialog = false }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = scope.inputText,
            onValueChange = { newText ->
                scope.onInputChanged(newText)
                // Auto-submit in keyboard mode when the typed text matches an accepted answer
                if (contract.currentInputMode == InputMode.KEYBOARD &&
                    contract.sessionActive &&
                    scope.currentCard != null &&
                    newText.isNotBlank()
                ) {
                    if (com.alexpo.grammermate.data.Normalizer.isExactMatch(newText, scope.currentCard!!.acceptedAnswers)) {
                        scope.onSubmit()
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
                            if (hasCards) {
                                contract.setInputMode(InputMode.VOICE)
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
                        },
                        enabled = hasCards
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.content_desc_voice_input))
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
        if (contract.currentInputMode == InputMode.VOICE) {
            Text(
                text = scope.currentCard?.promptRu?.let { "Say translation: $it" } ?: "",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Word Bank UI
        if (contract.currentInputMode == InputMode.WORD_BANK && contract.supportsWordBank) {
            WordBankSection(contract = contract)
        }

        // Input mode selector row + show answer + flag buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val modeConfig = contract.inputModeConfig
            val showModeButtons = modeConfig.showInputModeButtons || modeConfig.availableModes.size > 1

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showModeButtons) {
                    if (InputMode.VOICE in modeConfig.availableModes && contract.supportsVoiceInput) {
                        FilledTonalIconButton(
                            onClick = {
                                contract.setInputMode(InputMode.VOICE)
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
                            },
                            enabled = hasCards
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.content_desc_voice_mode))
                        }
                    }
                    // Keyboard button: always available when in availableModes
                    if (InputMode.KEYBOARD in modeConfig.availableModes) {
                        FilledTonalIconButton(
                            onClick = { contract.setInputMode(InputMode.KEYBOARD) },
                            enabled = hasCards
                        ) {
                            Icon(Icons.Default.Keyboard, contentDescription = stringResource(R.string.content_desc_keyboard_mode))
                        }
                    }
                    // Word bank button: only on EASY
                    if (InputMode.WORD_BANK in modeConfig.availableModes && contract.supportsWordBank) {
                        FilledTonalIconButton(
                            onClick = { contract.setInputMode(InputMode.WORD_BANK) },
                            enabled = hasCards
                        ) {
                            Icon(Icons.Default.LibraryBooks, contentDescription = stringResource(R.string.content_desc_word_bank_mode))
                        }
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
                    tooltip = { PlainTooltip { Text(text = stringResource(R.string.tooltip_show_answer)) } },
                    state = rememberTooltipState()
                ) {
                    IconButton(
                        onClick = { if (hasCards) contract.showAnswer() },
                        enabled = hasCards
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = stringResource(R.string.tooltip_show_answer))
                    }
                }
                // Flag/Report button
                if (contract.supportsFlagging) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(text = stringResource(R.string.tooltip_report_sentence)) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = { if (hasCards) showReportSheet = true },
                            enabled = hasCards
                        ) {
                            Icon(Icons.Default.ReportProblem, contentDescription = stringResource(R.string.tooltip_report_sentence))
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
            onClick = scope.onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = scope.inputText.isNotBlank() && hasCards
        ) {
            Text(text = stringResource(R.string.button_check))
        }
    }
}

/**
 * Result content matching GrammarMateApp's ResultBlock:
 * Correct/Incorrect label (green/red) + TTS replay button (4 states) + "Answer: ..." text.
 * NO "Next" button -- navigation handles that.
 */
@Composable
private fun DefaultResultContent(scope: TrainingCardSessionScope) {
    val result = scope.lastResult ?: return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (result.correct) {
                Text(
                    text = stringResource(R.string.result_correct),
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = stringResource(R.string.result_incorrect),
                    color = Color(0xFFC62828),
                    fontWeight = FontWeight.Bold
                )
            }
            if (scope.contract.supportsTts && result.displayAnswer.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                TtsSpeakerButton(
                    ttsState = scope.contract.ttsState,
                    enabled = true,
                    onClick = { scope.contract.speakTts() }
                )
            }
        }
        if (result.displayAnswer.isNotBlank()) {
            HintAnswerCard(
                answerText = result.displayAnswer,
                showTtsButton = scope.contract.supportsTts,
                onSpeakTts = { scope.contract.speakTts() }
            )
        }
    }
}

/**
 * Navigation controls matching GrammarMateApp's NavigationRow:
 * styled NavIconButtons (44dp, surfaceVariant background, 3dp primary accent bar)
 * for Prev, Pause/Play (when supportsPause), Exit (with confirmation), Next.
 */
@Composable
private fun DefaultNavigationControls(scope: TrainingCardSessionScope) {
    if (!scope.contract.supportsNavigation) return

    var showExitDialog by remember { mutableStateOf(false) }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.session_end_title)) },
            text = { Text(stringResource(R.string.session_end_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    scope.contract.requestExit()
                }) {
                    Text(stringResource(R.string.button_end))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavIconButton(
            onClick = scope.onPrev,
            enabled = scope.currentCard != null
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.content_desc_prev))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (scope.contract.supportsPause) {
                NavIconButton(
                    onClick = { scope.contract.togglePause() },
                    enabled = scope.currentCard != null
                ) {
                    if (scope.contract.sessionActive) {
                        Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.content_desc_pause))
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.content_desc_play))
                    }
                }
            }
            NavIconButton(
                onClick = { showExitDialog = true },
                enabled = scope.currentCard != null
            ) {
                Icon(Icons.Default.StopCircle, contentDescription = stringResource(R.string.content_desc_exit_session))
            }
            NavIconButton(
                onClick = scope.onNext,
                enabled = scope.currentCard != null
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = stringResource(R.string.content_desc_next))
            }
        }
    }
}

@Composable
private fun DefaultCompletionScreen(scope: TrainingCardSessionScope) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎉", // party popper
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.completion_well_done),
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = scope.progressText,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = scope.onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.button_done))
        }
    }
}
