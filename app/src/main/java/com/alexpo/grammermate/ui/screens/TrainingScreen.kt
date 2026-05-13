package com.alexpo.grammermate.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
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
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.Normalizer
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.SubmitResult
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.ui.components.AsrStatusIndicator
import com.alexpo.grammermate.ui.components.HintAnswerCard
import com.alexpo.grammermate.ui.components.NavIconButton
import com.alexpo.grammermate.ui.components.SharedReportSheet
import com.alexpo.grammermate.ui.components.TtsSpeakerButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    state: TrainingUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> SubmitResult,
    onPrev: () -> Unit,
    onNext: (Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onRequestExit: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowSettings: () -> Unit,
    onSelectLesson: (String) -> Unit,
    onSelectMode: (TrainingMode) -> Unit,
    onSetInputMode: (InputMode) -> Unit,
    onShowAnswer: () -> Unit,
    onVoicePromptStarted: () -> Unit,
    onSelectWordFromBank: (String) -> Unit,
    onRemoveLastWord: () -> Unit,
    onTtsSpeak: () -> Unit,
    onFlagBadSentence: () -> Unit = {},
    onUnflagBadSentence: () -> Unit = {},
    onHideCard: () -> Unit = {},
    onExportBadSentences: () -> String? = { null },
    isBadSentence: () -> Boolean = { false },
    onStartOfflineRecognition: () -> Unit = {},
    hintLevel: HintLevel = HintLevel.EASY
) {
    val hasCards = state.cardSession.currentCard != null
    val scrollState = rememberScrollState()
    val drillGreen = Color(0xFFE8F5E9)

    Scaffold(
        containerColor = if (state.drill.isDrillMode) drillGreen else MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "GrammarMate",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    onOpenSettings()
                    onShowSettings()
                }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.boss.bossActive) {
                Text(text = "Review Session", fontWeight = FontWeight.SemiBold)
            } else if (state.elite.eliteActive) {
                Text(text = "Refresh Session", fontWeight = FontWeight.SemiBold)
            } else if (state.drill.isDrillMode) {
                // Drill: prompt without hints + progress bar + speedometer
                val cardTense = state.cardSession.currentCard?.tense
                if (!cardTense.isNullOrBlank()) {
                    Text(
                        text = cardTense,
                        fontSize = 13.sp,
                        color = Color(0xFF388E3C),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val rawPrompt = state.cardSession.currentCard?.promptRu ?: ""
                val cleanPrompt = rawPrompt.replace(Regex("\\s*\\([^)]+\\)"), "")
                if (cleanPrompt.isNotBlank()) {
                    Text(
                        text = cleanPrompt,
                        fontSize = (18f * state.audio.ruTextScale).sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                DrillProgressRow(
                    current = state.drill.drillCardIndex + 1,
                    total = state.drill.drillTotalCards,
                    speed = state.cardSession.voiceActiveMs,
                    wordCount = state.cardSession.voiceWordCount
                )
            } else {
                val cardTense = state.cardSession.currentCard?.tense
                val isMixChallenge = state.navigation.mode == TrainingMode.MIX_CHALLENGE
                if (!cardTense.isNullOrBlank()) {
                    if (isMixChallenge) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = Color(0xFFE3F2FD)
                        ) {
                            Text(
                                text = cardTense,
                                fontSize = 14.sp,
                                color = Color(0xFF1565C0),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        Text(
                            text = cardTense,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                val rawPrompt = state.cardSession.currentCard?.promptRu ?: ""
                val cleanPrompt = rawPrompt.replace(Regex("\\s*\\([^)]+\\)"), "")
                if (cleanPrompt.isNotBlank()) {
                    Text(
                        text = cleanPrompt,
                        fontSize = (18f * state.audio.ruTextScale).sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val total = if (state.boss.bossActive) state.boss.bossTotal else state.cardSession.subLessonTotal
                val current = if (state.boss.bossActive) state.boss.bossProgress else state.cardSession.currentIndex
                DrillProgressRow(
                    current = (current + 1).coerceAtMost(total.coerceAtLeast(1)),
                    total = total.coerceAtLeast(1),
                    speed = state.cardSession.voiceActiveMs,
                    wordCount = state.cardSession.voiceWordCount
                )
            }
            CardPrompt(state, onSpeak = onTtsSpeak)
            AnswerBox(
                state,
                onInputChange,
                onSubmit,
                onSetInputMode,
                onShowAnswer,
                onVoicePromptStarted,
                onSelectWordFromBank,
                onRemoveLastWord,
                hasCards,
                onFlagBadSentence,
                onUnflagBadSentence,
                onHideCard,
                onExportBadSentences,
                isBadSentence,
                onStartOfflineRecognition,
                hintLevel
            )
            ResultBlock(state, onSpeak = onTtsSpeak)
            NavigationRow(onPrev, onNext, onTogglePause, onRequestExit, state.cardSession.sessionState, hasCards)
        }
    }
}

@Composable
fun HeaderStats(state: TrainingUiState, isDrillMode: Boolean = false) {
    val total = if (state.boss.bossActive) state.boss.bossTotal else state.cardSession.subLessonTotal
    val progressIndex = if (total > 0) {
        if (state.boss.bossActive) {
            state.boss.bossProgress.coerceIn(0, total)
        } else {
            state.cardSession.currentIndex.coerceIn(0, total)
        }
    } else {
        0
    }
    val progressPercent = if (total > 0) {
        ((progressIndex.toDouble() / total.toDouble()) * 100).toInt()
    } else {
        0
    }
    val speed = speedPerMinute(state.cardSession.voiceActiveMs, state.cardSession.voiceWordCount)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isDrillMode) {
            Column {
                Text(text = if (state.boss.bossActive) "Review" else "Progress")
                val progressText = when {
                    state.boss.bossActive -> "${progressPercent}% (${progressIndex}/${total})"
                    state.navigation.mode == TrainingMode.ALL_MIXED -> "${progressPercent}% (${progressIndex}/${total})"
                    else -> "${progressPercent}%"
                }
                Text(
                    text = progressText,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (!isDrillMode) {
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Time")
                Text(text = formatTime(state.cardSession.activeTimeMs), fontWeight = FontWeight.SemiBold)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "Speed")
            Text(text = speed, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ModeSelector(
    state: TrainingUiState,
    onSelectMode: (TrainingMode) -> Unit,
    onSelectLesson: (String) -> Unit
) {
    var lessonExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ModeIconButton(
                selected = state.navigation.mode == TrainingMode.LESSON,
                icon = Icons.Default.MenuBook,
                contentDescription = "Lesson"
            ) {
                onSelectMode(TrainingMode.LESSON)
                lessonExpanded = true
            }
            DropdownMenu(
                expanded = lessonExpanded,
                onDismissRequest = { lessonExpanded = false }
            ) {
                if (state.navigation.lessons.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(text = "No lessons") },
                        onClick = { lessonExpanded = false }
                    )
                } else {
                    state.navigation.lessons.forEach { lesson ->
                        DropdownMenuItem(
                            text = { Text(text = lesson.title) },
                            onClick = {
                                lessonExpanded = false
                                onSelectLesson(lesson.id.value)
                            }
                        )
                    }
                }
            }
        }
        ModeIconButton(
            selected = state.navigation.mode == TrainingMode.ALL_SEQUENTIAL,
            icon = Icons.Default.LibraryBooks,
            contentDescription = "All lessons"
        ) { onSelectMode(TrainingMode.ALL_SEQUENTIAL) }
        ModeIconButton(
            selected = state.navigation.mode == TrainingMode.ALL_MIXED,
            icon = Icons.Default.SwapHoriz,
            contentDescription = "Mixed"
        ) { onSelectMode(TrainingMode.ALL_MIXED) }
    }
}

@Composable
fun ModeIconButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    if (selected) {
        FilledTonalIconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription)
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

@Composable
fun CardPrompt(state: TrainingUiState, onSpeak: () -> Unit) {
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
                    text = state.cardSession.currentCard?.promptRu ?: "No cards",
                    fontSize = (20f * state.audio.ruTextScale).sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TtsSpeakerButton(
                ttsState = state.audio.ttsState,
                enabled = state.cardSession.currentCard != null,
                onClick = onSpeak
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun AnswerBox(
    state: TrainingUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> SubmitResult,
    onSetInputMode: (InputMode) -> Unit,
    onShowAnswer: () -> Unit,
    onVoicePromptStarted: () -> Unit,
    onSelectWordFromBank: (String) -> Unit,
    onRemoveLastWord: () -> Unit,
    hasCards: Boolean,
    onFlagBadSentence: () -> Unit = {},
    onUnflagBadSentence: () -> Unit = {},
    onHideCard: () -> Unit = {},
    onExportBadSentences: () -> String? = { null },
    isBadSentence: () -> Boolean = { false },
    onStartOfflineRecognition: () -> Unit = {},
    hintLevel: HintLevel = HintLevel.EASY
) {
    val latestState by rememberUpdatedState(state)
    val canLaunchVoice = hasCards && state.cardSession.sessionState == SessionState.ACTIVE
    val clipboardManager = LocalClipboardManager.current
    var showReportSheet by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val reportCard = state.cardSession.currentCard
    val reportText = if (reportCard != null) {
        val targetText = reportCard.acceptedAnswers.joinToString(" / ")
        "ID: ${reportCard.id}\nSource: ${reportCard.promptRu}\nTarget: $targetText"
    } else {
        ""
    }
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                if (latestState.cardSession.sessionState == SessionState.PAUSED) return@rememberLauncherForActivityResult
                onInputChange(spoken)
                onSubmit()
            }
        }
    }
    LaunchedEffect(
        state.cardSession.currentCard?.id,
        state.cardSession.inputMode,
        state.cardSession.sessionState,
        state.cardSession.voiceTriggerToken
    ) {
        if (state.cardSession.inputMode == InputMode.VOICE &&
            state.cardSession.sessionState == SessionState.ACTIVE &&
            state.cardSession.currentCard != null
        ) {
            kotlinx.coroutines.delay(200)
            onVoicePromptStarted()
            if (state.audio.useOfflineAsr && state.audio.asrModelReady) {
                onStartOfflineRecognition()
            } else {
                launchVoiceRecognition(state.navigation.selectedLanguageId.value, state.cardSession.currentCard?.promptRu, speechLauncher)
            }
        }
    }
    if (showReportSheet) {
        val cardIsBad = isBadSentence()
        SharedReportSheet(
            onDismiss = { showReportSheet = false },
            cardPromptText = reportCard?.promptRu,
            isFlagged = cardIsBad,
            onFlag = onFlagBadSentence,
            onUnflag = onUnflagBadSentence,
            onHideCard = onHideCard,
            onExportBadSentences = onExportBadSentences,
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
            confirmButton = {
                TextButton(onClick = { exportMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.cardSession.inputText,
            onValueChange = { newText ->
                onInputChange(newText)
                // Auto-submit in keyboard mode when the typed text matches an accepted answer
                if (state.cardSession.inputMode == InputMode.KEYBOARD &&
                    state.cardSession.sessionState == SessionState.ACTIVE &&
                    state.cardSession.currentCard != null &&
                    newText.isNotBlank()
                ) {
                    if (Normalizer.isExactMatch(newText, state.cardSession.currentCard!!.acceptedAnswers)) {
                        onSubmit()
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
                            onSetInputMode(InputMode.VOICE)
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
        if (state.cardSession.inputMode == InputMode.VOICE && state.cardSession.sessionState == SessionState.ACTIVE) {
            Text(
                text = state.cardSession.currentCard?.promptRu?.let { "Say translation: $it" } ?: "",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (state.audio.useOfflineAsr) {
            AsrStatusIndicator(state.audio.asrState)
        }

        // Word Bank UI
        if (state.cardSession.inputMode == InputMode.WORD_BANK && state.cardSession.wordBankWords.isNotEmpty()) {
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
                state.cardSession.wordBankWords.forEach { word ->
                    // Count how many times this word appears in the word bank
                    val availableCount = state.cardSession.wordBankWords.count { it == word }
                    // Count how many times this word has been selected
                    val usedCount = state.cardSession.selectedWords.count { it == word }
                    // Word is fully used only when all instances are selected
                    val isFullyUsed = usedCount >= availableCount

                    FilterChip(
                        selected = usedCount > 0,
                        onClick = {
                            if (!isFullyUsed) {
                                onSelectWordFromBank(word)
                            }
                        },
                        label = { Text(text = word) },
                        enabled = !isFullyUsed
                    )
                }
            }
            if (state.cardSession.selectedWords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selected: ${state.cardSession.selectedWords.size} / ${state.cardSession.wordBankWords.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = onRemoveLastWord) {
                        Text(text = "Undo")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val canSelectInputMode = hasCards && state.cardSession.sessionState == SessionState.ACTIVE
                FilledTonalIconButton(
                    onClick = {
                        if (canLaunchVoice) {
                            onSetInputMode(InputMode.VOICE)
                        }
                    },
                    enabled = canLaunchVoice
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice mode")
                }
                FilledTonalIconButton(
                    onClick = { onSetInputMode(InputMode.KEYBOARD) },
                    enabled = canSelectInputMode
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Keyboard mode")
                }
                FilledTonalIconButton(
                    onClick = { onSetInputMode(InputMode.WORD_BANK) },
                    enabled = canSelectInputMode
                ) {
                    Icon(Icons.Default.LibraryBooks, contentDescription = "Word bank mode")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(text = "Show answer") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(
                        onClick = { if (hasCards) onShowAnswer() },
                        enabled = hasCards
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "Show answer")
                    }
                }
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
                Text(
                    text = when (state.cardSession.inputMode) {
                        InputMode.VOICE -> "Voice"
                        InputMode.KEYBOARD -> "Keyboard"
                        InputMode.WORD_BANK -> "Word Bank"
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Button(
            onClick = { onSubmit() },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasCards &&
                state.cardSession.inputText.isNotBlank() &&
                state.cardSession.sessionState == SessionState.ACTIVE &&
                state.cardSession.currentCard != null
        ) {
            Text(text = "Check")
        }
    }
}

@Composable
fun ResultBlock(state: TrainingUiState, onSpeak: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (state.cardSession.lastResult) {
                true -> Text(text = "Correct", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                false -> Text(text = "Incorrect", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                null -> Text(text = "")
            }
        }
        if (!state.cardSession.answerText.isNullOrBlank()) {
            HintAnswerCard(
                answerText = state.cardSession.answerText!!,
                showTtsButton = true,
                onSpeakTts = onSpeak
            )
        }
    }
}

@Composable
fun NavigationRow(
    onPrev: () -> Unit,
    onNext: (Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onRequestExit: () -> Unit,
    state: SessionState,
    hasCards: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavIconButton(onClick = onPrev, enabled = hasCards) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Prev")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NavIconButton(onClick = onTogglePause, enabled = hasCards) {
                if (state == SessionState.ACTIVE) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
            }
            NavIconButton(onClick = onRequestExit, enabled = hasCards) {
                Icon(Icons.Default.StopCircle, contentDescription = "Exit session")
            }
            NavIconButton(onClick = { onNext(false) }, enabled = hasCards) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next")
            }
        }
    }
}

@Composable
fun DrillProgressRow(current: Int, total: Int, speed: Long, wordCount: Int) {
    val progress = if (total > 0) current.toFloat() / total else 0f
    val barColor = Color(0xFF4CAF50)
    val trackColor = Color(0xFFC8E6C9)
    val speedVal = if (speed > 0) (wordCount / (speed / 60000.0)).toInt() else 0
    val speedColor = when {
        speedVal <= 20 -> Color(0xFFE53935)
        speedVal <= 40 -> Color(0xFFFDD835)
        else -> Color(0xFF43A047)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Progress bar — 70% width, fill grows left to right
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
                    .fillMaxWidth(progress)
                    .background(barColor, RoundedCornerShape(12.dp))
            )
            Text(
                text = "$current / $total",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (progress < 0.12f) Color(0xFF2E7D32) else Color.White,
                modifier = Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Speedometer circle — 30% width, constrained to square
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

private fun formatTime(activeMs: Long): String {
    val totalSeconds = activeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun speedPerMinute(activeMs: Long, correct: Int): String {
    val minutes = activeMs / 60000.0
    if (minutes <= 0.0) return "-"
    return String.format("%.1f", correct / minutes)
}

private fun launchVoiceRecognition(
    languageId: String,
    prompt: String?,
    launcher: ActivityResultLauncher<Intent>
) {
    val languageTag = when (languageId) {
        "it" -> "it-IT"
        else -> "en-US"
    }
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt ?: "Say the translation")
    }
    launcher.launch(intent)
}
