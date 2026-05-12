package com.alexpo.grammermate.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.data.AnswerResult
import com.alexpo.grammermate.data.CardSessionContract
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.TtsState

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
    val hintLevel: HintLevel = HintLevel.EASY
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
 * @param progressIndicator Optional slot for progress display. Default: DrillProgressRow-style bar + speedometer.
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
            hintLevel = hintLevel
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
            DefaultProgressIndicator(scope)
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
    // Tense label -- hidden on HARD
    if (scope.hintLevel != HintLevel.HARD) {
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
    }
    // Clean prompt (strip parenthetical hints)
    val rawPrompt = card?.promptRu ?: ""
    val cleanPrompt = rawPrompt.replace(Regex("\\s*\\([^)]+\\)"), "")
    if (cleanPrompt.isNotBlank()) {
        Text(
            text = cleanPrompt,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Progress indicator matching GrammarMateApp's DrillProgressRow:
 * rounded green progress bar (70%) with text overlay + circular speedometer (30%) with wpm arc.
 */
@Composable
private fun DefaultProgressIndicator(scope: TrainingCardSessionScope) {
    val progress = scope.contract.progress
    val progressFraction = if (progress.total > 0) {
        progress.current.toFloat() / progress.total.toFloat()
    } else 0f
    val barColor = Color(0xFF4CAF50)
    val trackColor = Color(0xFFC8E6C9)
    val speedVal = scope.contract.currentSpeedWpm
    val speedColor = when {
        speedVal <= 20 -> Color(0xFFE53935)
        speedVal <= 40 -> Color(0xFFFDD835)
        else -> Color(0xFF43A047)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Progress bar - 70% width
        Box(
            modifier = Modifier
                .weight(0.7f)
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(trackColor)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .height(24.dp)
                    .fillMaxWidth(progressFraction)
                    .background(barColor, RoundedCornerShape(12.dp))
            )
            Text(
                text = "${progress.current} / ${progress.total}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (progressFraction < 0.12f) Color(0xFF2E7D32) else Color.White,
                modifier = Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Speedometer circle - 30% width
        Box(
            modifier = Modifier
                .weight(0.3f)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            val sizeModifier = Modifier.size(44.dp)
            Canvas(modifier = sizeModifier) {
                val strokeWidth = 4.dp.toPx()
                drawArc(
                    color = Color(0xFFE0E0E0),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth)
                )
                val sweep = 360f * (speedVal.coerceAtMost(100) / 100f)
                drawArc(
                    color = speedColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Text(
                text = "$speedVal",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = speedColor
            )
        }
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
                Text(text = "RU", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = card.promptRu,
                    fontSize = 20.sp,
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
 * TTS speaker button with 4 states: speaking (stop icon, red), initializing (spinner),
 * error (warning, red), idle (VolumeUp icon).
 * Matches GrammarMateApp's TtsSpeakerButton exactly.
 */
@Composable
private fun TtsSpeakerButton(
    ttsState: TtsState,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled
    ) {
        when (ttsState) {
            TtsState.SPEAKING -> Icon(
                Icons.Default.StopCircle,
                contentDescription = "Stop",
                tint = MaterialTheme.colorScheme.error
            )
            TtsState.INITIALIZING -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            TtsState.ERROR -> Icon(
                Icons.Default.ReportProblem,
                contentDescription = "TTS error",
                tint = MaterialTheme.colorScheme.error
            )
            else -> Icon(
                Icons.Default.VolumeUp,
                contentDescription = "Listen",
                modifier = Modifier.size(24.dp)
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
 * - Report/Flag button (Warning icon with tooltip) -- opens ModalBottomSheet
 * - "Check" button (full width)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DefaultInputControls(scope: TrainingCardSessionScope) {
    val contract = scope.contract
    val hasCards = scope.currentCard != null
    val clipboardManager = LocalClipboardManager.current
    var showReportSheet by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
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
        val cardIsBad = contract.isCurrentCardFlagged()
        ModalBottomSheet(
            onDismissRequest = { showReportSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Card options",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (reportCard != null) {
                    Text(
                        text = reportCard.promptRu,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (cardIsBad) {
                    TextButton(
                        onClick = {
                            contract.unflagCurrentCard()
                            showReportSheet = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ReportProblem, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove from bad sentences list")
                    }
                } else {
                    TextButton(
                        onClick = {
                            contract.flagCurrentCard()
                            showReportSheet = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ReportProblem, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to bad sentences list")
                    }
                }
                TextButton(
                    onClick = {
                        contract.hideCurrentCard()
                        showReportSheet = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hide this card from lessons")
                }
                TextButton(
                    onClick = {
                        val path = contract.exportFlaggedCards()
                        exportMessage = if (path != null) "Exported to $path" else "No bad sentences to export"
                        showReportSheet = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export bad sentences to file")
                }
                TextButton(
                    onClick = {
                        if (reportText.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(reportText))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy text")
                }
            }
        }
    }
    if (exportMessage != null) {
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text("Export") },
            text = { Text(exportMessage!!) },
            confirmButton = {
                TextButton(onClick = { exportMessage = null }) {
                    Text("OK")
                }
            }
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
            label = { Text(text = "Your translation") },
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
                        Icon(Icons.Default.Mic, contentDescription = "Voice input")
                    }
                }
            }
        )

        if (!hasCards) {
            Text(
                text = "No cards",
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

        // Word Bank UI -- only on EASY
        if (contract.currentInputMode == InputMode.WORD_BANK && contract.supportsWordBank && scope.hintLevel == HintLevel.EASY) {
            val wordBankWords = contract.getWordBankWords()
            val selectedWords = contract.getSelectedWords()
            if (wordBankWords.isNotEmpty()) {
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
                            onClick = {
                                if (!isFullyUsed) {
                                    contract.selectWordFromBank(word)
                                }
                            },
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
                        TextButton(onClick = { contract.removeLastSelectedWord() }) {
                            Text(text = "Undo")
                        }
                    }
                }
            }
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
                            Icon(Icons.Default.Mic, contentDescription = "Voice mode")
                        }
                    }
                    // Keyboard button: hidden on HARD (voice only)
                    if (InputMode.KEYBOARD in modeConfig.availableModes && scope.hintLevel != HintLevel.HARD) {
                        FilledTonalIconButton(
                            onClick = { contract.setInputMode(InputMode.KEYBOARD) },
                            enabled = hasCards
                        ) {
                            Icon(Icons.Default.Keyboard, contentDescription = "Keyboard mode")
                        }
                    }
                    // Word bank button: only on EASY
                    if (InputMode.WORD_BANK in modeConfig.availableModes && contract.supportsWordBank && scope.hintLevel == HintLevel.EASY) {
                        FilledTonalIconButton(
                            onClick = { contract.setInputMode(InputMode.WORD_BANK) },
                            enabled = hasCards
                        ) {
                            Icon(Icons.Default.LibraryBooks, contentDescription = "Word bank mode")
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show answer button -- only on EASY
                if (scope.hintLevel == HintLevel.EASY) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(text = "Show answer") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = { if (hasCards) contract.showAnswer() },
                            enabled = hasCards
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = "Show answer")
                        }
                    }
                }
                // Flag/Report button
                if (contract.supportsFlagging) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(text = "Report sentence") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = { if (hasCards) showReportSheet = true },
                            enabled = hasCards
                        ) {
                            Icon(Icons.Default.ReportProblem, contentDescription = "Report sentence")
                        }
                    }
                }
                // Current mode label
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

        // Check button
        Button(
            onClick = scope.onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = scope.inputText.isNotBlank() && hasCards
        ) {
            Text(text = "Check")
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
                    text = "Correct",
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "Incorrect",
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
            Text(text = "Answer: ${result.displayAnswer}")
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
            title = { Text("End session?") },
            text = { Text("Your progress will be saved.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    scope.contract.requestExit()
                }) {
                    Text("End")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel")
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
            Icon(Icons.Default.ArrowBack, contentDescription = "Prev")
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
                        Icon(Icons.Default.Pause, contentDescription = "Pause")
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                    }
                }
            }
            NavIconButton(
                onClick = { showExitDialog = true },
                enabled = scope.currentCard != null
            ) {
                Icon(Icons.Default.StopCircle, contentDescription = "Exit session")
            }
            NavIconButton(
                onClick = scope.onNext,
                enabled = scope.currentCard != null
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next")
            }
        }
    }
}

/**
 * Navigation icon button matching GrammarMateApp's NavIconButton:
 * surfaceVariant background with primary accent bottom bar.
 */
@Composable
private fun NavIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) surface else surface.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .background(if (enabled) accent else accent.copy(alpha = 0.3f))
        )
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
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
            text = "Well done!",
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
            Text(text = "Done")
        }
    }
}
