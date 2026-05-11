package com.alexpo.grammermate.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onAdvance: () -> Boolean,
    onSpeak: (String) -> Unit,
    onStopTts: () -> Unit,
    ttsState: TtsState,
    onExit: () -> Unit,
    onComplete: () -> Unit,
    languageId: String = "en"
) {
    if (!state.active && state.finishedToken) {
        DailyPracticeCompletionScreen(onExit = onExit)
        return
    }

    if (!state.active || currentTask == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No active session",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
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

        when (currentTask.blockType) {
            DailyBlockType.TRANSLATE,
            DailyBlockType.VERBS -> {
                CardSessionBlock(
                    state = state,
                    blockProgress = blockProgress,
                    onAdvance = onAdvance,
                    onComplete = onComplete,
                    onExit = onExit,
                    onSubmitSentence = onSubmitSentence,
                    onSubmitVerb = onSubmitVerb,
                    onSpeak = onSpeak,
                    onStopTts = onStopTts,
                    ttsState = ttsState,
                    languageId = languageId
                )
            }
            DailyBlockType.VOCAB -> {
                val task = currentTask as DailyTask.VocabFlashcard
                VocabFlashcardBlock(
                    task = task,
                    onFlip = onFlipVocabCard,
                    onRate = onRateVocabCard,
                    onAdvance = onAdvance,
                    onComplete = onComplete,
                    onSpeak = onSpeak
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
    onComplete: () -> Unit,
    onExit: () -> Unit,
    onSubmitSentence: (String) -> Boolean,
    onSubmitVerb: (String) -> Boolean,
    onSpeak: (String) -> Unit,
    onStopTts: () -> Unit,
    ttsState: TtsState,
    languageId: String
) {
    val blockKey = state.blockIndex to state.taskIndex
    var blockComplete by remember { mutableStateOf(false) }

    val provider = remember(blockKey) {
        DailyPracticeSessionProvider(
            tasks = state.tasks,
            startOffset = state.taskIndex,
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
            ttsStateProvider = { ttsState }
        )
    }

    if (blockComplete) {
        val hasMore = onAdvance()
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
        modifier = Modifier.weight(1f)
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
    modifier: Modifier = Modifier
) {
    // Voice recognition launcher -- same pattern as VerbDrillScreen
    val latestProvider by rememberUpdatedState(provider)
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                latestProvider.submitAnswerWithInput(spoken)
            }
        }
    }

    // Auto-voice LaunchedEffect -- mirrors VerbDrillScreen exactly
    val voiceCardId = provider.currentCard?.id
    LaunchedEffect(
        voiceCardId,
        provider.currentInputMode,
        provider.sessionActive
    ) {
        if (provider.currentInputMode == InputMode.VOICE &&
            provider.sessionActive &&
            provider.currentCard != null
        ) {
            kotlinx.coroutines.delay(200)
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

    TrainingCardSession(
        contract = provider,
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

                    // Verb + tense hint chips (Block 3 only)
                    if (!verbText.isNullOrBlank()) {
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
                        }
                    }
                }
            }
        },
        inputControls = {
            DailyInputControls(
                provider = provider,
                scope = this,
                speechLauncher = speechLauncher
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
    speechLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val contract = scope.contract
    val hasCards = scope.currentCard != null
    val canLaunchVoice = hasCards && contract.sessionActive
    val canSelectInputMode = hasCards && contract.sessionActive

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hint answer text -- shown when eye button pressed or 3 wrong attempts
        if (provider.hintAnswer != null) {
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
                    text = "${provider.remainingAttempts} attempts left",
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
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Your translation") },
            enabled = hasCards,
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (canLaunchVoice) {
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

        // Word Bank UI
        if (contract.currentInputMode == InputMode.WORD_BANK && contract.supportsWordBank) {
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
                            contract.setInputMode(InputMode.VOICE)
                        }
                    },
                    enabled = canLaunchVoice
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice mode")
                }
                FilledTonalIconButton(
                    onClick = { contract.setInputMode(InputMode.KEYBOARD) },
                    enabled = canSelectInputMode
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Keyboard mode")
                }
                FilledTonalIconButton(
                    onClick = { contract.setInputMode(InputMode.WORD_BANK) },
                    enabled = canSelectInputMode
                ) {
                    Icon(Icons.Default.LibraryBooks, contentDescription = "Word bank mode")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show answer button -- disabled when hint already shown
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
                    provider.submitAnswerWithInput(input)
                    scope.onInputChanged("")
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

@Composable
private fun ColumnScope.VocabFlashcardBlock(
    task: DailyTask.VocabFlashcard,
    onFlip: () -> Unit,
    onRate: (Int) -> Unit,
    onAdvance: () -> Boolean,
    onComplete: () -> Unit,
    onSpeak: (String) -> Unit
) {
    var isFlipped by remember(task.id) { mutableStateOf(false) }
    var isRated by remember(task.id) { mutableStateOf(false) }

    val promptText = when (task.direction) {
        VocabDrillDirection.IT_TO_RU -> task.word.word
        VocabDrillDirection.RU_TO_IT -> task.word.meaningRu ?: task.word.word
    }
    val answerText = when (task.direction) {
        VocabDrillDirection.IT_TO_RU -> task.word.meaningRu ?: task.word.word
        VocabDrillDirection.RU_TO_IT -> task.word.word
    }
    val directionLabel = when (task.direction) {
        VocabDrillDirection.IT_TO_RU -> "Italian -> Russian"
        VocabDrillDirection.RU_TO_IT -> "Russian -> Italian"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isFlipped) {
                isFlipped = true
                onFlip()
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isFlipped) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = directionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = promptText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (!isFlipped) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Tap to flip",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            AnimatedVisibility(visible = isFlipped) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = answerText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    IconButton(onClick = { onSpeak(answerText) }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Listen")
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.weight(1f))

    if (isFlipped && !isRated) {
        Text(
            text = "How well did you know this?",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
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
                        isRated = true
                        onRate(rating)
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
    }

    if (isRated) {
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val hasMore = onAdvance()
                if (!hasMore) onComplete()
                isFlipped = false
                isRated = false
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
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
