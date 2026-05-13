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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.alexpo.grammermate.data.CardSessionContract
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.DailySessionState
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SrsRating
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.VocabDrillDirection
import com.alexpo.grammermate.ui.components.DailyWordBankSection
import com.alexpo.grammermate.ui.components.DailyInputModeBar
import com.alexpo.grammermate.ui.components.HintAnswerCard
import com.alexpo.grammermate.ui.components.SharedReportSheet
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
    onRateVocabCard: (SrsRating) -> Unit,
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
                onDismiss = { hasShownCompletionSparkle = true }
            )
        } else {
            DailyPracticeCompletionScreen(onExit = onExit)
        }
        return
    }

    if (!state.active || currentTask == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading session...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        DailyPracticeHeader(blockProgress = blockProgress, onExit = onExit)
        Spacer(modifier = Modifier.height(12.dp))
        BlockProgressBar(blockProgress = blockProgress)
        Spacer(modifier = Modifier.height(16.dp))

        // Block-transition sparkle overlay
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
                onDismiss = { showBlockTransition = false }
            )
        }

        when (currentTask.blockType) {
            DailyBlockType.TRANSLATE, DailyBlockType.VERBS -> {
                CardSessionBlock(
                    state = state, blockProgress = blockProgress,
                    onAdvance = onAdvance, onAdvanceBlock = onAdvanceBlock, onRepeatBlock = onRepeatBlock,
                    onComplete = onComplete, onExit = onExit,
                    onSubmitSentence = onSubmitSentence, onSubmitVerb = onSubmitVerb,
                    onSpeak = onSpeak, onStopTts = onStopTts, ttsState = ttsState,
                    languageId = languageId, onPersistVerbProgress = onPersistVerbProgress,
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
                    task = task, onFlip = onFlipVocabCard, onRate = onRateVocabCard,
                    onAdvance = onAdvance,
                    onComplete = {
                        val hasMore = onAdvanceBlock()
                        if (!hasMore) onComplete()
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

/** Card session block for TRANSLATE and VERBS. */
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
            tasks = state.tasks, blockType = currentBlockType,
            onBlockComplete = { blockComplete = true }, languageId = languageId,
            onAnswerChecked = { input, _ ->
                when (state.tasks.getOrNull(state.taskIndex)) {
                    is DailyTask.TranslateSentence -> onSubmitSentence(input)
                    is DailyTask.ConjugateVerb -> onSubmitVerb(input)
                    else -> {}
                }
            },
            onSpeakTts = onSpeak, onStopTts = onStopTts, ttsStateProvider = { ttsState },
            onExit = onExit,
            onCardAdvanced = { task ->
                if (task is DailyTask.ConjugateVerb) onPersistVerbProgress(task.card)
                onCardPracticed(task.blockType)
            },
            onFlagCard = { card, blockType ->
                val mode = when (blockType) {
                    DailyBlockType.TRANSLATE -> "daily_translate"
                    DailyBlockType.VERBS -> "daily_verb"
                    DailyBlockType.VOCAB -> "daily_vocab"
                }
                onFlagDailyBadSentence(card.id, languageId, card.promptRu, card.acceptedAnswers.joinToString(" / "), mode)
            },
            onUnflagCard = { card, _ -> onUnflagDailyBadSentence(card.id) },
            isCardFlagged = { card -> isDailyBadSentence(card.id) },
            onExportFlagged = { onExportDailyBadSentences() }
        )
    }

    if (blockComplete) {
        val hasMore = onAdvanceBlock()
        blockComplete = false
        if (!hasMore) { onComplete(); return }
    }

    DailyTrainingCardSession(
        provider = provider, onExit = onExit,
        onComplete = { blockComplete = true },
        modifier = Modifier.weight(1f), hintLevel = hintLevel
    )
}

/** TrainingCardSession wrapper with custom cardContent for verb chips and custom inputControls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyTrainingCardSession(
    provider: DailyPracticeSessionProvider,
    onExit: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY
) {
    val latestProvider by rememberUpdatedState(provider)
    var voiceInputText by remember { mutableStateOf<String?>(null) }
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                val submitResult = latestProvider.submitAnswerWithInput(spoken)
                if (submitResult == null || !submitResult.correct) voiceInputText = spoken
            }
        }
    }

    // Auto-voice LaunchedEffect
    LaunchedEffect(provider.currentCard?.id, provider.currentInputMode, provider.sessionActive, provider.voiceTriggerToken) {
        if (provider.currentInputMode == InputMode.VOICE && provider.sessionActive && provider.currentCard != null) {
            kotlinx.coroutines.delay(if (provider.showIncorrectFeedback) 1200 else 200)
            val languageTag = if (provider.languageId == "it") "it-IT" else "en-US"
            speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the translation")
            })
        }
    }

    // Auto-advance after correct voice answer
    LaunchedEffect(provider.pendingAnswerResult, provider.currentInputMode) {
        val result = latestProvider.pendingAnswerResult
        if (result != null && result.correct && latestProvider.currentInputMode == InputMode.VOICE) {
            kotlinx.coroutines.delay(400)
            latestProvider.nextCard()
        }
    }

    TrainingCardSession(
        contract = provider, hintLevel = hintLevel,
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
                            Text("RU", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(card.promptRu, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(
                            onClick = { contract.speakTts() },
                            enabled = card.promptRu.isNotBlank()
                        ) {
                            when (provider.ttsState) {
                                TtsState.SPEAKING -> Icon(Icons.Default.StopCircle, "Stop", tint = MaterialTheme.colorScheme.error)
                                TtsState.INITIALIZING -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                TtsState.ERROR -> Icon(Icons.Default.ReportProblem, "TTS error", tint = MaterialTheme.colorScheme.error)
                                else -> Icon(Icons.Default.VolumeUp, "Listen")
                            }
                        }
                    }

                    // Verb + tense + group hint chips (Block 3 only) -- hidden on HARD
                    if (!verbText.isNullOrBlank() && hintLevel != com.alexpo.grammermate.data.HintLevel.HARD) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text(if (verbRank != null) "$verbText #$verbRank" else verbText, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                                icon = { Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp)) }
                            )
                            drillCard?.tense?.takeIf { it.isNotBlank() }?.let { tenseText ->
                                SuggestionChip(onClick = {}, label = { Text(abbreviateTense(tenseText), fontSize = 14.sp, fontWeight = FontWeight.Medium) })
                            }
                            drillCard?.group?.takeIf { it.isNotBlank() && hintLevel == com.alexpo.grammermate.data.HintLevel.EASY }?.let { groupText ->
                                SuggestionChip(onClick = {}, label = { Text(groupText, fontSize = 14.sp, fontWeight = FontWeight.Medium) })
                            }
                        }
                    }
                }
            }
        },
        inputControls = {
            DailyInputControls(
                provider = provider, scope = this,
                speechLauncher = speechLauncher,
                voiceInputText = voiceInputText,
                onVoiceInputConsumed = { voiceInputText = null },
                hintLevel = hintLevel
            )
        },
        onExit = onExit, onComplete = onComplete, modifier = modifier
    )
}

/** Input controls: hint, text field, word bank, mode selector, check button. */
@OptIn(ExperimentalMaterial3Api::class)
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
        "ID: ${reportCard.id}\nSource: ${reportCard.promptRu}\nTarget: ${reportCard.acceptedAnswers.joinToString(" / ")}"
    } else ""

    LaunchedEffect(voiceInputText) {
        if (voiceInputText != null) { scope.onInputChanged(voiceInputText); onVoiceInputConsumed() }
    }

    val contract = scope.contract
    val hasCards = scope.currentCard != null
    val canLaunchVoice = hasCards && contract.sessionActive
    val canSelectInputMode = hasCards && contract.sessionActive

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hint answer text -- only on EASY
        if (provider.hintAnswer != null && hintLevel == com.alexpo.grammermate.data.HintLevel.EASY) {
            HintAnswerCard(
                answerText = provider.hintAnswer!!,
                showTtsButton = contract.supportsTts,
                onSpeakTts = { contract.speakTts() }
            )
        }

        // Incorrect feedback
        if (provider.showIncorrectFeedback) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Incorrect", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.width(8.dp))
                Text("${provider.remainingAttempts} ${if (provider.remainingAttempts == 1) "attempt" else "attempts"} left", color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedTextField(
            value = scope.inputText,
            onValueChange = { newText ->
                if (provider.showIncorrectFeedback) provider.clearIncorrectFeedback()
                scope.onInputChanged(newText)
                if (contract.currentInputMode == InputMode.KEYBOARD && contract.sessionActive && scope.currentCard != null && newText.isNotBlank()) {
                    if (com.alexpo.grammermate.data.Normalizer.isExactMatch(newText, scope.currentCard!!.acceptedAnswers)) {
                        val result = provider.submitAnswerWithInput(newText)
                        if (result != null && result.correct) scope.onInputChanged("")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Your translation") },
            enabled = hasCards,
            trailingIcon = {
                IconButton(
                    onClick = { if (canLaunchVoice) { scope.onInputChanged(""); contract.setInputMode(InputMode.VOICE) } },
                    enabled = canLaunchVoice
                ) { Icon(Icons.Default.Mic, "Voice input") }
            }
        )

        if (!hasCards) Text("No cards", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

        if (contract.currentInputMode == InputMode.VOICE && contract.sessionActive) {
            Text(scope.currentCard?.promptRu?.let { "Say translation: $it" } ?: "", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), style = MaterialTheme.typography.bodySmall)
        }

        // Word Bank UI -- only on EASY
        if (contract.currentInputMode == InputMode.WORD_BANK && contract.supportsWordBank && hintLevel == com.alexpo.grammermate.data.HintLevel.EASY) {
            DailyWordBankSection(contract = contract)
        }

        // Input mode selector + show answer + report
        DailyInputModeBar(
            contract = contract, provider = provider,
            canLaunchVoice = canLaunchVoice, canSelectInputMode = canSelectInputMode, hasCards = hasCards,
            onClearInput = { scope.onInputChanged("") },
            onShowReport = { showReportSheet = true },
            hintLevel = hintLevel
        )

        Button(
            onClick = {
                val input = scope.inputText
                if (input.isNotBlank()) {
                    val result = provider.submitAnswerWithInput(input)
                    if (result != null && result.correct) scope.onInputChanged("")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasCards && scope.inputText.isNotBlank() && contract.sessionActive && scope.currentCard != null
        ) { Text("Check") }
    }

    if (showReportSheet) {
        SharedReportSheet(
            onDismiss = { showReportSheet = false },
            cardPromptText = reportCard?.promptRu,
            isFlagged = if (reportCard != null) contract.isCurrentCardFlagged() else false,
            onFlag = { contract.flagCurrentCard() },
            onUnflag = { contract.unflagCurrentCard() },
            onHideCard = { /* no-op for daily practice */ },
            onExportBadSentences = { contract.exportFlaggedCards() },
            onCopyText = {
                if (reportText.isNotBlank()) {
                    clipboardManager.setText(AnnotatedString(reportText))
                }
            },
            exportResult = { path ->
                exportMessage = if (path != null) "Exported to $path" else "No bad sentences to export"
            }
        )
    }
    if (exportMessage != null) {
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text("Export") },
            text = { Text(exportMessage!!) },
            confirmButton = { TextButton(onClick = { exportMessage = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun DailyPracticeHeader(blockProgress: BlockProgress, onExit: () -> Unit) {
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
            confirmButton = { TextButton(onClick = { showExitDialog = false; onExit() }) { Text("Exit") } },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Stay") } }
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { showExitDialog = true }) { Icon(Icons.Default.ArrowBack, "Back") }
        Spacer(modifier = Modifier.width(8.dp))
        Text("Daily Practice", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(modifier = Modifier.weight(1f))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Text(blockLabel, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun BlockProgressBar(blockProgress: BlockProgress) {
    if (blockProgress.totalTasks == 0) return
    val overallProgress = blockProgress.globalPosition.toFloat() / blockProgress.totalTasks.toFloat()
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(progress = { overallProgress }, modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.width(8.dp))
        Text("${blockProgress.globalPosition}/${blockProgress.totalTasks}", style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.VocabFlashcardBlock(
    task: DailyTask.VocabFlashcard,
    onFlip: () -> Unit,
    onRate: (SrsRating) -> Unit,
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

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        isVoiceActive = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                voiceRecognizedText = spoken
                val isCorrect = com.alexpo.grammermate.data.Normalizer.normalize(spoken) == com.alexpo.grammermate.data.Normalizer.normalize(answerText)
                if (isCorrect && !isRated) {
                    isRated = true
                    onRate(SrsRating.GOOD)
                    if (!onAdvance()) onComplete()
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(promptText, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSpeak(promptText) }) { Icon(Icons.Default.VolumeUp, "Listen") }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { showReportSheet = true }) {
                    Icon(Icons.Default.ReportProblem, "Report word", tint = if (isDailyBadSentence(task.word.id)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (hintLevel != com.alexpo.grammermate.data.HintLevel.HARD) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(answerText, fontSize = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    Spacer(modifier = Modifier.weight(1f))

    if (voiceRecognizedText != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("You said: \"$voiceRecognizedText\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }

    Spacer(modifier = Modifier.height(8.dp))
    FilledTonalIconButton(
        onClick = {
            if (!isVoiceActive) {
                isVoiceActive = true
                val langTag = when (task.direction) { VocabDrillDirection.IT_TO_RU -> "ru-RU"; VocabDrillDirection.RU_TO_IT -> "it-IT" }
                speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the translation")
                })
            }
        },
        modifier = Modifier.align(Alignment.CenterHorizontally).size(64.dp)
    ) { Icon(Icons.Default.Mic, "Voice input", modifier = Modifier.size(32.dp)) }

    Spacer(modifier = Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Again" to SrsRating.AGAIN, "Hard" to SrsRating.HARD, "Good" to SrsRating.GOOD, "Easy" to SrsRating.EASY).forEach { (label, rating) ->
            val colors = when (rating) {
                SrsRating.AGAIN -> Pair(Color(0xFFFFEBEE), Color(0xFFE53935))
                SrsRating.HARD -> Pair(Color(0xFFFFF3E0), Color(0xFFFF9800))
                SrsRating.GOOD -> Pair(Color(0xFFE8F5E9), Color(0xFF4CAF50))
                SrsRating.EASY -> Pair(Color(0xFFE3F2FD), Color(0xFF2196F3))
            }
            OutlinedButton(
                onClick = { onRate(rating); if (!onAdvance()) onComplete() },
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = colors.first, contentColor = colors.second)
            ) { Text(label, fontSize = 12.sp) }
        }
    }

    val word = task.word
    if (showReportSheet) {
        SharedReportSheet(
            onDismiss = { showReportSheet = false },
            cardPromptText = "${word.word} — ${word.meaningRu ?: ""}",
            isFlagged = isDailyBadSentence(word.id),
            onFlag = { onFlagDailyBadSentence(word.id, languageId, word.meaningRu ?: word.word, word.word, "daily_vocab") },
            onUnflag = { onUnflagDailyBadSentence(word.id) },
            onHideCard = { /* no-op for vocab flashcards */ },
            onExportBadSentences = { onExportDailyBadSentences() },
            onCopyText = {
                val copyText = "Word: ${word.word}\nMeaning: ${word.meaningRu}"
                if (copyText.isNotBlank()) {
                    clipboardManager.setText(AnnotatedString(copyText))
                }
            },
            exportResult = { path ->
                exportMessage = if (path != null) "Exported to $path" else "No bad sentences to export"
            }
        )
    }
    if (exportMessage != null) {
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text("Export") },
            text = { Text(exportMessage!!) },
            confirmButton = { TextButton(onClick = { exportMessage = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun BlockSparkleOverlay(blockType: DailyBlockType, isLastBlock: Boolean, onDismiss: () -> Unit) {
    val blockLabel = when (blockType) {
        DailyBlockType.TRANSLATE -> "Translation"
        DailyBlockType.VOCAB -> "Vocabulary"
        DailyBlockType.VERBS -> "Verbs"
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(800); onDismiss() }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✨", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(if (isLastBlock) "Daily practice complete!" else "Next: $blockLabel", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (isLastBlock) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Great job today!", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun DailyPracticeCompletionScreen(onExit: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Session Complete!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Great job! You practiced translations, vocabulary, and verb conjugations.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Back to Home") }
    }
}

private fun abbreviateTense(tense: String): String {
    return mapOf(
        "Presente" to "Pres.", "Imperfetto" to "Imperf.", "Passato Prossimo" to "P. Pross.",
        "Passato Remoto" to "P. Rem.", "Trapassato Prossimo" to "Trap. P.",
        "Futuro Semplice" to "Fut. Sempl.", "Futuro Anteriore" to "Fut. Ant.",
        "Condizionale Presente" to "Cond. Pres.", "Condizionale Passato" to "Cond. Pass.",
        "Congiuntivo Presente" to "Cong. Pres.", "Congiuntivo Imperfetto" to "Cong. Imp.",
        "Congiuntivo Passato" to "Cong. Pass."
    )[tense] ?: tense.take(8)
}
