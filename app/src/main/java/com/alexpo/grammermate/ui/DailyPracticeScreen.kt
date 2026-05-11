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
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
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
import com.alexpo.grammermate.data.DailySessionState
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.ui.helpers.DailyPipelineProgress
import com.alexpo.grammermate.ui.helpers.DailyPracticeSessionProvider

@Composable
fun DailyPracticeScreen(
    state: DailySessionState,
    cards: List<SentenceCard>,
    lessonTitle: String,
    subLessonLabel: String,
    progress: DailyPipelineProgress,
    onSubmitAnswer: (input: String, correct: Boolean, inputMode: InputMode) -> Unit,
    onAdvanceSubLesson: () -> Boolean,
    onSpeak: (String) -> Unit,
    onStopTts: () -> Unit,
    ttsState: TtsState,
    onExit: () -> Unit,
    onComplete: () -> Unit,
    languageId: String = "en"
) {
    if (!state.active && state.finishedToken) {
        var hasShownSparkle by remember { mutableStateOf(false) }
        if (!hasShownSparkle) {
            CompletionSparkle(
                onDismiss = { hasShownSparkle = true }
            )
        } else {
            DailyPracticeCompletionScreen(onExit = onExit)
        }
        return
    }

    if (!state.active || cards.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Loading...",
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
            lessonTitle = lessonTitle,
            subLessonLabel = subLessonLabel,
            onExit = onExit
        )
        Spacer(modifier = Modifier.height(12.dp))
        DailyProgressBar(progress = progress)
        Spacer(modifier = Modifier.height(16.dp))

        SubLessonCardSession(
            cards = cards,
            onAdvanceSubLesson = onAdvanceSubLesson,
            onComplete = onComplete,
            onExit = onExit,
            onSubmitAnswer = onSubmitAnswer,
            onSpeak = onSpeak,
            onStopTts = onStopTts,
            ttsState = ttsState,
            languageId = languageId
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.SubLessonCardSession(
    cards: List<SentenceCard>,
    onAdvanceSubLesson: () -> Boolean,
    onComplete: () -> Unit,
    onExit: () -> Unit,
    onSubmitAnswer: (String, Boolean, InputMode) -> Unit,
    onSpeak: (String) -> Unit,
    onStopTts: () -> Unit,
    ttsState: TtsState,
    languageId: String
) {
    var blockComplete by remember { mutableStateOf(false) }

    val provider = remember(cards) {
        DailyPracticeSessionProvider(
            cards = cards,
            onBlockComplete = { blockComplete = true },
            languageId = languageId,
            onAnswerChecked = { input, correct, mode -> onSubmitAnswer(input, correct, mode) },
            onSpeakTts = onSpeak,
            onStopTts = onStopTts,
            ttsStateProvider = { ttsState },
            onExit = onExit
        )
    }

    if (blockComplete) {
        val hasMore = onAdvanceSubLesson()
        blockComplete = false
        if (!hasMore) {
            onComplete()
            return
        }
    }

    // Voice recognition
    val latestProvider by rememberUpdatedState(provider)
    var voiceInputText by remember { mutableStateOf<String?>(null) }
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                val submitResult = latestProvider.submitAnswerWithInput(spoken)
                if (submitResult == null || !submitResult.correct) {
                    voiceInputText = spoken
                }
            }
        }
    }

    // Auto-voice trigger
    val voiceToken = provider.voiceTriggerToken
    val voiceCardId = provider.currentCard?.id
    LaunchedEffect(voiceCardId, provider.currentInputMode, provider.sessionActive, voiceToken) {
        if (provider.currentInputMode == InputMode.VOICE && provider.sessionActive && provider.currentCard != null) {
            kotlinx.coroutines.delay(if (provider.showIncorrectFeedback) 1200L else 200L)
            val langTag = if (languageId == "it") "it-IT" else "en-US"
            speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
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
        contract = provider,
        inputControls = {
            DailyInputControls(
                provider = provider,
                scope = this,
                voiceInputText = voiceInputText,
                onVoiceInputConsumed = { voiceInputText = null }
            )
        },
        onExit = onExit,
        onComplete = { blockComplete = true },
        modifier = Modifier.weight(1f)
    )
}

/**
 * Input controls for Daily Practice v2.
 * Handles: hint card, incorrect feedback, text field with voice, word bank, input mode selector,
 * show answer button, check button with submitAnswerWithInput.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DailyInputControls(
    provider: DailyPracticeSessionProvider,
    scope: TrainingCardSessionScope,
    voiceInputText: String?,
    onVoiceInputConsumed: () -> Unit
) {
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

        // Input mode selector + show answer
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

        // Check button
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
}

@Composable
private fun DailyPracticeHeader(
    lessonTitle: String,
    subLessonLabel: String,
    onExit: () -> Unit
) {
    var showExitDialog by remember { mutableStateOf(false) }

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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Daily Practice",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Text(
                text = "$lessonTitle - $subLessonLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun DailyProgressBar(progress: DailyPipelineProgress) {
    if (progress.totalSubLessons == 0) return
    val overallProgress = progress.globalPosition.toFloat() / progress.totalSubLessons.toFloat()
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
            text = "${progress.globalPosition}/${progress.totalSubLessons}",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun CompletionSparkle(onDismiss: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
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
                    text = "All sub-lessons complete!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Great job today!",
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
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
            text = "Great job! You practiced all available sub-lessons.",
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
