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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.DailySessionState
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.VocabDrillDirection
import com.alexpo.grammermate.ui.helpers.BlockProgress
import com.alexpo.grammermate.ui.helpers.DailyPracticeSessionProvider

@Composable
fun DailyPracticeScreen(
    state: DailySessionState,
    blockProgress: BlockProgress,
    currentTask: DailyTask?,
    onSubmitSentence: (String) -> Boolean,
    onSubmitVerb: (String) -> Boolean,
    onShowSentenceAnswer: () -> String?,
    onShowVerbAnswer: () -> String?,
    onFlipVocabCard: () -> Unit,
    onRateVocabCard: (Int) -> Unit,
    onPersistVerbProgress: (com.alexpo.grammermate.data.VerbDrillCard) -> Unit = {},
    onCardPracticed: (DailyBlockType) -> Unit = {},
    onAdvance: () -> Boolean,
    onAdvanceBlock: () -> Boolean,
    onRepeatBlock: () -> Boolean,
    onSpeak: (String) -> Unit,
    onStopTts: () -> Unit,
    ttsState: TtsState,
    onExit: () -> Unit,
    onComplete: () -> Unit,
    languageId: String = "en",
    onFlagDailyBadSentence: (cardId: String, languageId: String, sentence: String, translation: String, mode: String) -> Unit = { _, _, _, _, _ -> },
    onUnflagDailyBadSentence: (cardId: String) -> Unit = {},
    isDailyBadSentence: (cardId: String) -> Boolean = { false },
    onExportDailyBadSentences: () -> String? = { null },
    hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY
) {
    var hasShownCompletionSparkle by remember { mutableStateOf(false) }
    val showCompletionSparkle = state.finishedToken && !hasShownCompletionSparkle

    if (!state.active && state.finishedToken) {
        if (showCompletionSparkle) {
            BlockSparkleOverlay(
                blockType = DailyBlockType.VERBS,
                isLastBlock = true,
                onDismiss = {
                    hasShownCompletionSparkle = true
                }
            )
        } else {
            DailyPracticeCompletionScreen(onExit = onExit)
        }
        return
    }

    if (!state.active || currentTask == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Loading session...",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        DailyPracticeHeader(
            blockProgress = blockProgress,
            onExit = onExit
        )

        Spacer(modifier = Modifier.height(12.dp))

        BlockProgressBar(blockProgress = blockProgress)

        Spacer(modifier = Modifier.height(16.dp))

        // Block-transition sparkle overlay — detects when block type changes
        var previousBlockType by remember { mutableStateOf<DailyBlockType?>(null) }
        var showBlockTransition by remember { mutableStateOf(false) }
        val currentBlockType = currentTask.blockType

        LaunchedEffect(currentBlockType) {
            if (previousBlockType != null && currentBlockType != null && previousBlockType != currentBlockType) {
                showBlockTransition = true
            }
            previousBlockType = currentBlockType
        }

        if (showBlockTransition) {
            BlockSparkleOverlay(
                blockType = currentBlockType ?: DailyBlockType.TRANSLATE,
                isLastBlock = currentBlockType == DailyBlockType.VERBS && blockProgress.globalPosition >= blockProgress.totalTasks,
                onDismiss = {
                    showBlockTransition = false
                }
            )
        }

        when (currentTask.blockType) {
            DailyBlockType.TRANSLATE,
            DailyBlockType.VERBS -> {
                CardSessionBlock(
                    state = state,
                    blockProgress = blockProgress,
                    onAdvance = onAdvance,
                    onAdvanceBlock = onAdvanceBlock,
                    onRepeatBlock = onRepeatBlock,
                    onComplete = onComplete,
                    onExit = onExit,
                    onSubmitSentence = onSubmitSentence,
                    onSubmitVerb = onSubmitVerb,
                    onSpeak = onSpeak,
                    onStopTts = onStopTts,
                    ttsState = ttsState,
                    languageId = languageId,
                    onPersistVerbProgress = onPersistVerbProgress,
                    onCardPracticed = onCardPracticed,
                    onFlagDailyBadSentence = onFlagDailyBadSentence,
                    onUnflagDailyBadSentence = onUnflagDailyBadSentence,
                    isDailyBadSentence = isDailyBadSentence,
                    onExportDailyBadSentences = onExportDailyBadSentences,
                    hintLevel = hintLevel
                )
            }
            DailyBlockType.VOCAB -> {
                val task = currentTask as DailyTask.VocabFlashcard

                VocabFlashcardBlock(
                    task = task,
                    onFlip = onFlipVocabCard,
                    onRate = onRateVocabCard,
                    onAdvance = onAdvance,
                    onComplete = {
                        val hasMore = onAdvanceBlock()
                        if (!hasMore) {
                            onComplete()
                        }
                    },
                    onSpeak = onSpeak,
                    onFlagDailyBadSentence = onFlagDailyBadSentence,
                    onUnflagDailyBadSentence = onUnflagDailyBadSentence,
                    isDailyBadSentence = isDailyBadSentence,
                    onExportDailyBadSentences = onExportDailyBadSentences,
                    languageId = languageId
                )
            }
        }
    }
}

/**
 * Card session block for TRANSLATE and VERBS.
 * Mirrors VerbDrillSessionWithCardSession from VerbDrillScreen.kt exactly.
 */
@Composable
private fun ColumnScope.CardSessionBlock(
    state: DailySessionState,
    blockProgress: BlockProgress,
    onAdvance: () -> Boolean,
    onAdvanceBlock: () -> Boolean,
    onRepeatBlock: () -> Boolean,
    onComplete: () -> Unit,
    onExit: () -> Unit,
    onSubmitSentence: (String) -> Boolean,
    onSubmitVerb: (String) -> Boolean,
    onSpeak: (String) -> Unit,
    onStopTts: () -> Unit,
    ttsState: TtsState,
    languageId: String,
    onPersistVerbProgress: (com.alexpo.grammermate.data.VerbDrillCard) -> Unit = {},
    onCardPracticed: (DailyBlockType) -> Unit = {},
    onFlagDailyBadSentence: (cardId: String, languageId: String, sentence: String, translation: String, mode: String) -> Unit = { _, _, _, _, _ -> },
    onUnflagDailyBadSentence: (cardId: String) -> Unit = {},
    isDailyBadSentence: (cardId: String) -> Boolean = { false },
    onExportDailyBadSentences: () -> String? = { null },
    hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY
) {
    val blockKey = Triple(state.blockIndex, state.taskIndex, state.tasks.size)
    var blockComplete by remember { mutableStateOf(false) }

    val provider = remember(blockKey) {
        val currentBlockType = when (state.tasks.getOrNull(state.taskIndex)) {
            is DailyTask.TranslateSentence -> DailyBlockType.TRANSLATE
            is DailyTask.ConjugateVerb -> DailyBlockType.VERBS
            else -> DailyBlockType.TRANSLATE
        }
        DailyPracticeSessionProvider(
            tasks = state.tasks,
            blockType = currentBlockType,
            onBlockComplete = { blockComplete = true },
            languageId = languageId,
            onAnswerChecked = { input, correct ->
                val task = state.tasks.getOrNull(state.taskIndex)
                when (task) {
                    is DailyTask.TranslateSentence -> onSubmitSentence(input)
                    is DailyTask.ConjugateVerb -> onSubmitVerb(input)
                    else -> {}
                }
            },
            onSpeakTts = onSpeak,
            onStopTts = onStopTts,
            ttsStateProvider = { ttsState },
            onExit = onExit,
            onCardAdvanced = { task ->
                if (task is DailyTask.ConjugateVerb) {
                    onPersistVerbProgress(task.card)
                }
                // Track VOICE/KEYBOARD practiced cards for cursor advancement.
                // The provider only calls onCardAdvanced for non-WORD_BANK modes.
                onCardPracticed(task.blockType)
            },
            onFlagCard = { card, blockType ->
                val mode = when (blockType) {
                    DailyBlockType.TRANSLATE -> "daily_translate"
                    DailyBlockType.VERBS -> "daily_verb"
                    DailyBlockType.VOCAB -> "daily_vocab"
                }
                onFlagDailyBadSentence(
                    card.id,
                    languageId,
                    card.promptRu,
                    card.acceptedAnswers.joinToString(" / "),
                    mode
                )
            },
            onUnflagCard = { card, _ ->
                onUnflagDailyBadSentence(card.id)
            },
            isCardFlagged = { card ->
                isDailyBadSentence(card.id)
            },
            onExportFlagged = {
                onExportDailyBadSentences()
            }
        )
    }

    // Auto-advance when block completes (sparkle is handled at DailyPracticeScreen level)
    if (blockComplete) {
        val hasMore = onAdvanceBlock()
        blockComplete = false
        if (!hasMore) {
            onComplete()
            return
        }
    }

    // Custom input controls mirroring VerbDrillScreen's DefaultVerbDrillInputControls
    DailyTrainingCardSession(
        provider = provider,
        onExit = onExit,
        onComplete = { blockComplete = true },
        modifier = Modifier.weight(1f),
        hintLevel = hintLevel
    )
}

/**
 * TrainingCardSession wrapper that mirrors VerbDrillSessionWithCardSession:
 * custom cardContent for verb chips, custom inputControls for hint/incorrect/retry flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyTrainingCardSession(
    provider: DailyPracticeSessionProvider,
    onExit: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY
) {
    // Voice recognition launcher -- same pattern as VerbDrillScreen
    val latestProvider by rememberUpdatedState(provider)
    var voiceInputText by remember { mutableStateOf<String?>(null) }
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                // Submit the answer
                val submitResult = latestProvider.submitAnswerWithInput(spoken)
                // If wrong, put text into input field for manual editing
                if (submitResult == null || !submitResult.correct) {
                    voiceInputText = spoken
                }
            }
        }
    }

    // Auto-voice LaunchedEffect -- mirrors VerbDrillScreen exactly:
    // triggers when voiceTriggerToken changes, inputMode is VOICE, card exists, session is active
    val voiceToken = provider.voiceTriggerToken
    val voiceCardId = provider.currentCard?.id
    LaunchedEffect(
        voiceCardId,
        provider.currentInputMode,
        provider.sessionActive,
        voiceToken
    ) {
        if (provider.currentInputMode == InputMode.VOICE &&
            provider.sessionActive &&
            provider.currentCard != null
        ) {
            if (provider.showIncorrectFeedback) {
                kotlinx.coroutines.delay(1200)
            } else {
                kotlinx.coroutines.delay(200)
            }
            val languageTag = when (provider.languageId) {
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
    }

    // Auto-advance after correct voice answer — mirrors VerbDrillScreen
    val latestProviderForAdvance by rememberUpdatedState(provider)
    LaunchedEffect(provider.pendingAnswerResult, provider.currentInputMode) {
        val result = latestProviderForAdvance.pendingAnswerResult
        if (result != null && result.correct && latestProviderForAdvance.currentInputMode == InputMode.VOICE) {
            kotlinx.coroutines.delay(400)
            latestProviderForAdvance.nextCard()
        }
    }

    TrainingCardSession(
        contract = provider,
        hintLevel = hintLevel,
        cardContent = {
            val card = currentCard ?: return@TrainingCardSession
            val drillCard = provider.currentVerbDrillCard()
            val verbText = drillCard?.verb
            val verbRank = drillCard?.rank

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                        IconButton(
                            onClick = { contract.speakTts() },
                            enabled = card.promptRu.isNotBlank()
                        ) {
                            when (provider.ttsState) {
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
                                    contentDescription = "Listen"
                                )
                            }
                        }
                    }

                    // Verb + tense + group hint chips (Block 3 only) -- hidden on HARD
                    if (!verbText.isNullOrBlank() && hintLevel != com.alexpo.grammermate.data.HintLevel.HARD) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SuggestionChip(
                                onClick = { /* no bottom sheet for daily practice */ },
                                label = {
                                    Text(
                                        text = if (verbRank != null) "$verbText #$verbRank" else verbText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                icon = {
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            val tenseText = drillCard?.tense
                            if (!tenseText.isNullOrBlank()) {
                                SuggestionChip(
                                    onClick = { /* no bottom sheet for daily practice */ },
                                    label = {
                                        Text(
                                            text = abbreviateTense(tenseText),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                )
                            }
                            // Group chip -- only on EASY
                            val groupText = drillCard?.group
                            if (!groupText.isNullOrBlank() && hintLevel == com.alexpo.grammermate.data.HintLevel.EASY) {
                                SuggestionChip(
                                    onClick = { /* no bottom sheet for daily practice */ },
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
            }
        },
        inputControls = {
            DailyInputControls(
                provider = provider,
                scope = this,
                speechLauncher = speechLauncher,
                voiceInputText = voiceInputText,
                onVoiceInputConsumed = { voiceInputText = null },
                hintLevel = hintLevel
            )
        },
        onExit = onExit,
        onComplete = onComplete,
        modifier = modifier
    )
}

/**
 * Input controls for Daily Practice, mirroring VerbDrillScreen's DefaultVerbDrillInputControls.
 * Handles: hint card, incorrect feedback, text field with voice, word bank, input mode selector,
 * show answer button, check button with submitAnswerWithInput.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DailyInputControls(
    provider: DailyPracticeSessionProvider,
    scope: TrainingCardSessionScope,
    speechLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    voiceInputText: String?,
    onVoiceInputConsumed: () -> Unit,
    hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY
) {
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
    // Apply voice input text to the text field when available
    LaunchedEffect(voiceInputText) {
        if (voiceInputText != null) {
            scope.onInputChanged(voiceInputText)
            onVoiceInputConsumed()
        }
    }

    val contract = scope.contract
    val hasCards = scope.currentCard != null
    val canLaunchVoice = hasCards && contract.sessionActive
    val canSelectInputMode = hasCards && contract.sessionActive

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hint answer text -- only on EASY
        if (provider.hintAnswer != null && hintLevel == com.alexpo.grammermate.data.HintLevel.EASY) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Answer: ${provider.hintAnswer}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (contract.supportsTts) {
                        IconButton(
                            onClick = { contract.speakTts() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Listen",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // Incorrect feedback -- red text with remaining attempts
        if (provider.showIncorrectFeedback) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Incorrect",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${provider.remainingAttempts} ${if (provider.remainingAttempts == 1) "attempt" else "attempts"} left",
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        OutlinedTextField(
            value = scope.inputText,
            onValueChange = { newText ->
                if (provider.showIncorrectFeedback) {
                    provider.clearIncorrectFeedback()
                }
                scope.onInputChanged(newText)
                // Auto-submit in keyboard mode when the typed text matches an accepted answer
                if (contract.currentInputMode == InputMode.KEYBOARD &&
                    contract.sessionActive &&
                    scope.currentCard != null &&
                    newText.isNotBlank()
                ) {
                    if (com.alexpo.grammermate.data.Normalizer.isExactMatch(newText, scope.currentCard!!.acceptedAnswers)) {
                        val result = provider.submitAnswerWithInput(newText)
                        if (result != null && result.correct) {
                            scope.onInputChanged("")
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Your translation") },
            enabled = hasCards,
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (canLaunchVoice) {
                            scope.onInputChanged("")
                            contract.setInputMode(InputMode.VOICE)
                        }
                    },
                    enabled = canLaunchVoice
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice input")
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
        if (contract.currentInputMode == InputMode.VOICE && contract.sessionActive) {
            Text(
                text = scope.currentCard?.promptRu?.let { "Say translation: $it" } ?: "",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Word Bank UI -- only on EASY
        if (contract.currentInputMode == InputMode.WORD_BANK && contract.supportsWordBank && hintLevel == com.alexpo.grammermate.data.HintLevel.EASY) {
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

        // Input mode selector + show answer -- mirrors VerbDrillScreen exactly
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                    onClick = {
                        if (canLaunchVoice) {
                            scope.onInputChanged("")
                            contract.setInputMode(InputMode.VOICE)
                        }
                    },
                    enabled = canLaunchVoice
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice mode")
                }
                // Keyboard button -- hidden on HARD
                if (hintLevel != com.alexpo.grammermate.data.HintLevel.HARD) {
                    FilledTonalIconButton(
                        onClick = { contract.setInputMode(InputMode.KEYBOARD) },
                        enabled = canSelectInputMode
                    ) {
                        Icon(Icons.Default.Keyboard, contentDescription = "Keyboard mode")
                    }
                }
                // Word bank button -- only on EASY
                if (hintLevel == com.alexpo.grammermate.data.HintLevel.EASY) {
                    FilledTonalIconButton(
                        onClick = { contract.setInputMode(InputMode.WORD_BANK) },
                        enabled = canSelectInputMode
                    ) {
                        Icon(Icons.Default.LibraryBooks, contentDescription = "Word bank mode")
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show answer button -- only on EASY
                if (hintLevel == com.alexpo.grammermate.data.HintLevel.EASY) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(text = "Show answer") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = { if (hasCards) contract.showAnswer() },
                            enabled = hasCards && provider.hintAnswer == null
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = "Show answer")
                        }
                    }
                }
                // Report/Flag button
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

        // Check button -- uses provider.submitAnswerWithInput for retry/hint flow
        Button(
            onClick = {
                val input = scope.inputText
                if (input.isNotBlank()) {
                    val result = provider.submitAnswerWithInput(input)
                    if (result != null && result.correct) {
                        scope.onInputChanged("")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasCards &&
                scope.inputText.isNotBlank() &&
                contract.sessionActive &&
                scope.currentCard != null
        ) {
            Text(text = "Check")
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
}

@Composable
private fun DailyPracticeHeader(
    blockProgress: BlockProgress,
    onExit: () -> Unit
) {
    var showExitDialog by remember { mutableStateOf(false) }
    val blockLabel = when (blockProgress.blockType) {
        DailyBlockType.TRANSLATE -> "Translation"
        DailyBlockType.VOCAB -> "Vocabulary"
        DailyBlockType.VERBS -> "Verbs"
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit practice?") },
            text = { Text("Your progress in this session will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onExit()
                }) { Text("Exit") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Stay") }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { showExitDialog = true }) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Daily Practice",
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.weight(1f))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(
                text = blockLabel,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun BlockProgressBar(blockProgress: BlockProgress) {
    if (blockProgress.totalTasks == 0) return
    val overallProgress = blockProgress.globalPosition.toFloat() / blockProgress.totalTasks.toFloat()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LinearProgressIndicator(
            progress = { overallProgress },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${blockProgress.globalPosition}/${blockProgress.totalTasks}",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.VocabFlashcardBlock(
    task: DailyTask.VocabFlashcard,
    onFlip: () -> Unit,
    onRate: (Int) -> Unit,
    onAdvance: () -> Boolean,
    onComplete: () -> Unit,
    onSpeak: (String) -> Unit,
    onFlagDailyBadSentence: (cardId: String, languageId: String, sentence: String, translation: String, mode: String) -> Unit = { _, _, _, _, _ -> },
    onUnflagDailyBadSentence: (cardId: String) -> Unit = {},
    isDailyBadSentence: (cardId: String) -> Boolean = { false },
    onExportDailyBadSentences: () -> String? = { null },
    languageId: String = "en",
    hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY
) {
    var isRated by remember(task.id) { mutableStateOf(false) }
    var isVoiceActive by remember { mutableStateOf(false) }
    var voiceRecognizedText by remember { mutableStateOf<String?>(null) }
    var showReportSheet by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    val promptText = when (task.direction) {
        VocabDrillDirection.IT_TO_RU -> task.word.word
        VocabDrillDirection.RU_TO_IT -> task.word.meaningRu ?: task.word.word
    }
    val answerText = when (task.direction) {
        VocabDrillDirection.IT_TO_RU -> task.word.meaningRu ?: task.word.word
        VocabDrillDirection.RU_TO_IT -> task.word.word
    }

    // Voice recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isVoiceActive = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                voiceRecognizedText = spoken
                val normalizedSpoken = com.alexpo.grammermate.data.Normalizer.normalize(spoken)
                val normalizedAnswer = com.alexpo.grammermate.data.Normalizer.normalize(answerText)
                val isCorrect = normalizedSpoken == normalizedAnswer
                if (isCorrect && !isRated) {
                    isRated = true
                    onRate(2) // Good
                    // Auto-advance after correct voice answer
                    val hasMore = onAdvance()
                    if (!hasMore) onComplete()
                }
                // On incorrect: stay, user can retry voice or tap a rating button
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main word (large)
            Text(
                text = promptText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // TTS button for the word
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSpeak(promptText) }) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Listen")
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { showReportSheet = true }) {
                    Icon(
                        Icons.Default.ReportProblem,
                        contentDescription = "Report word",
                        tint = if (isDailyBadSentence(task.word.id)) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Answer text -- hidden on HARD
            if (hintLevel != com.alexpo.grammermate.data.HintLevel.HARD) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = answerText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    Spacer(modifier = Modifier.weight(1f))

    // Voice recognized feedback
    if (voiceRecognizedText != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "You said: \"$voiceRecognizedText\"",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }

    // Microphone button
    Spacer(modifier = Modifier.height(8.dp))
    FilledTonalIconButton(
        onClick = {
            if (!isVoiceActive) {
                isVoiceActive = true
                val langTag = when (task.direction) {
                    VocabDrillDirection.IT_TO_RU -> "ru-RU"
                    VocabDrillDirection.RU_TO_IT -> "it-IT"
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the translation")
                }
                speechLauncher.launch(intent)
            }
        },
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .size(64.dp)
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = "Voice input",
            modifier = Modifier.size(32.dp)
        )
    }

    // Rating buttons -- auto-advance on tap
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("Again" to 0, "Hard" to 1, "Good" to 2, "Easy" to 3).forEach { (label, rating) ->
            val colors = when (rating) {
                0 -> Pair(Color(0xFFFFEBEE), Color(0xFFE53935))
                1 -> Pair(Color(0xFFFFF3E0), Color(0xFFFF9800))
                2 -> Pair(Color(0xFFE8F5E9), Color(0xFF4CAF50))
                else -> Pair(Color(0xFFE3F2FD), Color(0xFF2196F3))
            }
            OutlinedButton(
                onClick = {
                    onRate(rating)
                    val hasMore = onAdvance()
                    if (!hasMore) onComplete()
                },
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.first,
                    contentColor = colors.second
                )
            ) {
                Text(label, fontSize = 12.sp)
            }
        }
    }

    // Report sheet for vocab flashcard
    if (showReportSheet) {
        val word = task.word
        val cardIsBad = isDailyBadSentence(word.id)
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
                    text = "Word options",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "${word.word} — ${word.meaningRu ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (cardIsBad) {
                    TextButton(
                        onClick = {
                            onUnflagDailyBadSentence(word.id)
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
                            onFlagDailyBadSentence(
                                word.id,
                                languageId,
                                word.meaningRu ?: word.word,
                                word.word,
                                "daily_vocab"
                            )
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
                        val path = onExportDailyBadSentences()
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
                        clipboardManager.setText(AnnotatedString("Word: ${word.word}\nMeaning: ${word.meaningRu}"))
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
}

@Composable
private fun BlockSparkleOverlay(
    blockType: DailyBlockType,
    isLastBlock: Boolean,
    onDismiss: () -> Unit
) {
    val blockLabel = when (blockType) {
        DailyBlockType.TRANSLATE -> "Translation"
        DailyBlockType.VOCAB -> "Vocabulary"
        DailyBlockType.VERBS -> "Verbs"
    }
    val message = if (isLastBlock) "Daily practice complete!" else "Next: $blockLabel"

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "✨",
                    fontSize = 48.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (isLastBlock) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Great job today!",
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyPracticeCompletionScreen(onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Session Complete!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Great job! You practiced translations, vocabulary, and verb conjugations.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Home")
        }
    }
}

@Composable
private fun HorizontalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

private fun abbreviateTense(tense: String): String {
    val abbreviations = mapOf(
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
        "Congiuntivo Passato" to "Cong. Pass.",
    )
    return abbreviations[tense] ?: tense.take(8)
}
