package com.alexpo.grammermate.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
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
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.ui.CorrectGreen
import com.alexpo.grammermate.ui.DrillBackgroundGreen
import com.alexpo.grammermate.ui.DrillPromptGreen
import com.alexpo.grammermate.ui.DrillTenseLabelGreen
import com.alexpo.grammermate.ui.IncorrectRed
import com.alexpo.grammermate.ui.MixChallengeSurface
import com.alexpo.grammermate.ui.MixChallengeText
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.SubmitResult
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.ui.components.AsrStatusIndicator
import com.alexpo.grammermate.ui.components.HintAnswerCard
import com.alexpo.grammermate.ui.components.QrShareDialog
import com.alexpo.grammermate.ui.components.UnifiedNavigationRow
import com.alexpo.grammermate.ui.components.SessionProgressIndicator
import com.alexpo.grammermate.ui.components.SharedReportSheet
import com.alexpo.grammermate.ui.components.TtsSpeakerButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    state: TrainingUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> SubmitResult,
    onPrev: () -> Unit,
    onNext: () -> Unit,
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
    val drillGreen = DrillBackgroundGreen

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
                    text = stringResource(R.string.training_grammarmate),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    onOpenSettings()
                    onShowSettings()
                }) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.training_settings))
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
                Text(text = stringResource(R.string.training_review_session), fontWeight = FontWeight.SemiBold)
            } else if (state.elite.eliteActive) {
                Text(text = stringResource(R.string.training_refresh_session), fontWeight = FontWeight.SemiBold)
            } else if (state.drill.isDrillMode) {
                // Drill: prompt without hints + progress bar + speedometer
                val cardTense = state.cardSession.currentCard?.tense
                if (!cardTense.isNullOrBlank()) {
                    Text(
                        text = cardTense,
                        fontSize = 13.sp,
                        color = DrillTenseLabelGreen,
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
                        color = DrillPromptGreen,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SessionProgressIndicator(
                    current = state.drill.drillCardIndex + 1,
                    total = state.drill.drillTotalCards,
                    speedWpm = if (state.cardSession.voiceActiveMs > 0) (state.cardSession.voiceWordCount / (state.cardSession.voiceActiveMs / 60000.0)).toInt() else 0
                )
            } else {
                val cardTense = state.cardSession.currentCard?.tense
                val isMixChallenge = state.navigation.mode == TrainingMode.MIX_CHALLENGE
                if (!cardTense.isNullOrBlank()) {
                    if (isMixChallenge) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MixChallengeSurface
                        ) {
                            Text(
                                text = cardTense,
                                fontSize = 14.sp,
                                color = MixChallengeText,
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
                SessionProgressIndicator(
                    current = (current + 1).coerceAtMost(total.coerceAtLeast(1)),
                    total = total.coerceAtLeast(1),
                    speedWpm = if (state.cardSession.voiceActiveMs > 0) (state.cardSession.voiceWordCount / (state.cardSession.voiceActiveMs / 60000.0)).toInt() else 0
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
            ResultBlock(state)
            UnifiedNavigationRow(
                stateModel = object : com.alexpo.grammermate.data.CardSessionStateModel {
                    override val isActive = state.cardSession.sessionState == SessionState.ACTIVE
                    override val isPaused = state.cardSession.sessionState == SessionState.PAUSED
                    override val isHintShown = state.cardSession.sessionState == SessionState.HINT_SHOWN
                    override val canSubmit = state.cardSession.canSubmit
                    override val hasCurrentCard = hasCards
                    override val isComplete = state.cardSession.sessionState == SessionState.PAUSED && state.cardSession.currentCard == null
                    override val progress = com.alexpo.grammermate.data.SessionProgress(
                        current = (state.cardSession.currentIndex + 1).coerceAtMost(state.cardSession.subLessonTotal.coerceAtLeast(1)),
                        total = state.cardSession.subLessonTotal.coerceAtLeast(1)
                    )
                },
                supportsPause = true,
                supportsNavigation = true,
                onPrev = onPrev,
                onTogglePause = onTogglePause,
                onStop = onRequestExit,
                onNext = onNext
            )
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
                Text(text = if (state.boss.bossActive) stringResource(R.string.training_review) else stringResource(R.string.training_progress))
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
                Text(text = stringResource(R.string.training_time))
                Text(text = formatTime(state.cardSession.activeTimeMs), fontWeight = FontWeight.SemiBold)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = stringResource(R.string.training_speed))
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
                contentDescription = stringResource(R.string.training_lesson)
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
                        text = { Text(text = stringResource(R.string.training_no_lessons)) },
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
            contentDescription = stringResource(R.string.training_all_lessons)
        ) { onSelectMode(TrainingMode.ALL_SEQUENTIAL) }
        ModeIconButton(
            selected = state.navigation.mode == TrainingMode.ALL_MIXED,
            icon = Icons.Default.SwapHoriz,
            contentDescription = stringResource(R.string.training_mixed)
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
                Text(text = stringResource(R.string.training_ru), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.cardSession.currentCard?.promptRu ?: stringResource(R.string.training_no_cards),
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
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showReportSheet by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
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
        if (state.audio.voiceAutoStart &&
            state.cardSession.inputMode == InputMode.VOICE &&
            state.cardSession.sessionState == SessionState.ACTIVE &&
            state.cardSession.currentCard != null
        ) {
            kotlinx.coroutines.delay(200)
            onVoicePromptStarted()
            if (state.audio.useOfflineAsr && state.audio.asrModelReady) {
                onStartOfflineRecognition()
            } else {
                launchVoiceRecognition(state.navigation.selectedLanguageId.value, state.cardSession.currentCard?.promptRu, speechLauncher, context)
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
                exportMessage = if (path != null) context.getString(R.string.training_exported_to, path) else context.getString(R.string.training_no_bad_sentences)
            },
            shareText = reportCard?.let { "${it.promptRu} — ${it.acceptedAnswers.joinToString(" / ")}" },
            onShareQr = { showQrDialog = true }
        )
    }
    if (showQrDialog && reportCard != null) {
        QrShareDialog(
            promptRu = reportCard.promptRu,
            answerText = reportCard.acceptedAnswers.firstOrNull() ?: "",
            targetLanguage = state.navigation.selectedLanguageId.value,
            onDismiss = { showQrDialog = false }
        )
    }
    if (exportMessage != null) {
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text(stringResource(R.string.training_export)) },
            text = { Text(exportMessage!!) },
            confirmButton = {
                TextButton(onClick = { exportMessage = null }) {
                    Text(stringResource(R.string.home_ok))
                }
            }
        )
    }

    // Thin CardSessionContract adapter for TrainingScreen's TrainingUiState
    val contractAdapter = remember(state, onSetInputMode, onInputChange, onSelectWordFromBank, onRemoveLastWord) {
        object : com.alexpo.grammermate.data.CardSessionContract {
            override val currentCard: com.alexpo.grammermate.data.SessionCard?
                get() = state.cardSession.currentCard
            override val inputText: String
                get() = state.cardSession.inputText
            override val lastResult: com.alexpo.grammermate.data.AnswerResult?
                get() = state.cardSession.lastResult?.let { com.alexpo.grammermate.data.AnswerResult(it, state.cardSession.answerText ?: "", it == null) }
            override val sessionActive: Boolean
                get() = state.cardSession.sessionState == SessionState.ACTIVE
            override val currentInputMode: InputMode
                get() = state.cardSession.inputMode
            override val languageId: String
                get() = state.navigation.selectedLanguageId.value
            override val inputModeConfig: com.alexpo.grammermate.data.InputModeConfig
                get() = com.alexpo.grammermate.data.InputModeConfig(
                    availableModes = setOf(InputMode.VOICE, InputMode.KEYBOARD, InputMode.WORD_BANK),
                    defaultMode = state.cardSession.inputMode,
                    showInputModeButtons = true
                )
            override val supportsVoiceInput: Boolean get() = true
            override val supportsWordBank: Boolean get() = state.cardSession.wordBankWords.isNotEmpty()
            override val supportsFlagging: Boolean get() = true
            override val supportsNavigation: Boolean get() = true
            override val supportsPause: Boolean get() = true
            override val isComplete: Boolean
                get() = state.cardSession.sessionState == SessionState.PAUSED && state.cardSession.currentCard == null
            override val progress: com.alexpo.grammermate.data.SessionProgress
                get() = com.alexpo.grammermate.data.SessionProgress(
                    current = (state.cardSession.currentIndex + 1).coerceAtMost(state.cardSession.subLessonTotal.coerceAtLeast(1)),
                    total = state.cardSession.subLessonTotal.coerceAtLeast(1)
                )

            override fun onInputChanged(text: String) = onInputChange(text)
            override fun submitAnswer(): com.alexpo.grammermate.data.AnswerResult? {
                onSubmit()
                return null
            }
            override fun showAnswer(): String? { onShowAnswer(); return null }
            override fun nextCard() {}
            override fun prevCard() {}
            override fun onVoiceInputResult(text: String) { onInputChange(text); onSubmit() }
            override fun setInputMode(mode: InputMode) = onSetInputMode(mode)
            override fun getSelectedWords(): List<String> = state.cardSession.selectedWords
            override fun getWordBankWords(): List<String> = state.cardSession.wordBankWords
            override fun selectWordFromBank(word: String) = onSelectWordFromBank(word)
            override fun removeLastSelectedWord() = onRemoveLastWord()
            override fun flagCurrentCard() = onFlagBadSentence()
            override fun unflagCurrentCard() = onUnflagBadSentence()
            override fun isCurrentCardFlagged(): Boolean = isBadSentence()
            override fun hideCurrentCard() = onHideCard()
            override fun exportFlaggedCards(): String? = onExportBadSentences()
            override fun togglePause() {}
            override fun requestExit() {}
        }
    }

    com.alexpo.grammermate.ui.components.UnifiedInputControlsBar(
        contract = contractAdapter,
        inputText = state.cardSession.inputText,
        onInputChanged = onInputChange,
        onSubmit = { onSubmit() },
        hasCards = hasCards,
        hintAnswer = if (state.cardSession.answerText != null && state.cardSession.lastResult != null) state.cardSession.answerText else null,
        onShowReport = { showReportSheet = true },
        reportCard = state.cardSession.currentCard
    )

    // Offline ASR indicator (Training-specific, not in UnifiedInputControlsBar)
    if (state.audio.useOfflineAsr) {
        AsrStatusIndicator(state.audio.asrState)
    }
}

@Composable
fun ResultBlock(state: TrainingUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (state.cardSession.lastResult) {
                true -> Text(text = stringResource(R.string.training_correct), color = CorrectGreen, fontWeight = FontWeight.Bold)
                false -> Text(text = stringResource(R.string.training_incorrect), color = IncorrectRed, fontWeight = FontWeight.Bold)
                null -> Text(text = "")
            }
        }
        if (!state.cardSession.answerText.isNullOrBlank()) {
            HintAnswerCard(
                answerText = state.cardSession.answerText!!
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
    launcher: ActivityResultLauncher<Intent>,
    context: android.content.Context
) {
    val languageTag = when (languageId) {
        "it" -> "it-IT"
        else -> "en-US"
    }
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt ?: context.getString(R.string.training_say_the_translation))
    }
    launcher.launch(intent)
}
