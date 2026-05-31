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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.feature.training.HintCalculator
import com.alexpo.grammermate.ui.CorrectGreen
import com.alexpo.grammermate.ui.IncorrectRed
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.SubmitResult
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.data.TrainingScreenMode
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.ui.components.AsrStatusIndicator
import com.alexpo.grammermate.ui.components.HintAnswerCard
import com.alexpo.grammermate.ui.components.QrShareDialog
import com.alexpo.grammermate.ui.components.UnifiedNavigationRow
import com.alexpo.grammermate.ui.components.SessionProgressIndicator
import com.alexpo.grammermate.ui.components.SharedReportSheet
import com.alexpo.grammermate.ui.components.TtsSpeakerButton
import com.alexpo.grammermate.ui.components.PomodoroTimerBanner
import com.alexpo.grammermate.ui.components.PomodoroSummaryScreen
import com.alexpo.grammermate.ui.components.VerbReferenceBottomSheet
import com.alexpo.grammermate.ui.components.TenseInfoBottomSheet
import com.alexpo.grammermate.ui.components.GrammarInfoBottomSheet

/** Pre-compiled regex to strip parenthetical hints from card prompts. */
private val ParentheticalRegex = Regex("\\s*\\([^)]+\\)")


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
    hintLevel: HintLevel = HintLevel.EASY,
    onPausePomodoro: () -> Unit = {},
    onResumePomodoro: () -> Unit = {},
    onCancelPomodoro: () -> Unit = {},
    onRateCardDifficulty: (com.alexpo.grammermate.data.CardDifficultyRating) -> Unit = {},
    onVerbDrillMore: () -> Unit = {},
    onSessionDone: () -> Unit = {},
    getTenseInfo: (String) -> com.alexpo.grammermate.ui.TenseInfo? = { null },
    pomodoroRemainingSeconds: Int = 0,
    clickableWordHints: Boolean = false,
    baseDir: java.io.File? = null,
    grammarChip: com.alexpo.grammermate.data.GrammarChip? = null,
    lessonTitle: String? = null
) {
    val hasCards = state.cardSession.currentCard != null
    val scrollState = rememberScrollState()
    val mode = state.cardSession.screenMode

    // Derive verb drill flag from screenMode instead of boolean param
    val isVerbDrillMode = mode == TrainingScreenMode.VERB_DRILL

    // Bottom sheet state for verb/tense chip taps
    var showVerbSheet by remember { mutableStateOf(false) }
    var showTenseSheet by remember { mutableStateOf(false) }
    var showGrammarSheet by remember { mutableStateOf(false) }
    val drillCard = state.cardSession.currentCard as? VerbDrillCard

    // VERB_DRILL completion: show stats + More/Exit buttons instead of card session
    val isVerbDrillComplete = isVerbDrillMode && !hasCards && mode == TrainingScreenMode.VERB_DRILL
    if (isVerbDrillComplete) {
        VerbDrillCompletionContent(
            correctCount = state.cardSession.correctCount,
            incorrectCount = state.cardSession.incorrectCount,
            onMore = onVerbDrillMore,
            onExit = onRequestExit
        )
        return
    }

    // Pomodoro completion: render summary OUTSIDE the scrolling Column to avoid nested verticalScroll crash
    // Takes priority over session completion — pomodoro stats are more important
    if (state.pomodoro.isComplete) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                PomodoroSummaryScreen(
                    pomodoro = state.pomodoro,
                    currentStreak = state.cardSession.currentStreak,
                    todayFireCount = state.cardSession.todayFireCount,
                    onDone = { onCancelPomodoro() }
                )
            }
        }
        return
    }

    // Universal session completion for ALL modes when no cards remain
    if (!hasCards) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            SessionCompletionContent(
                modifier = Modifier.padding(padding),
                mode = mode,
                correctCount = state.cardSession.correctCount,
                incorrectCount = state.cardSession.incorrectCount,
                onDone = onSessionDone
            )
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = lessonTitle ?: stringResource(R.string.training_grammarmate),
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
            // Pomodoro timer banner
            if (state.pomodoro.isActive && !state.pomodoro.isComplete) {
                val sessionCorrect = (state.cardSession.correctCount - state.pomodoro.baselineCorrect).coerceAtLeast(0)
                val sessionIncorrect = (state.cardSession.incorrectCount - state.pomodoro.baselineIncorrect).coerceAtLeast(0)
                val totalCards = sessionCorrect + sessionIncorrect
                val successRate = if (totalCards > 0) sessionCorrect * 100 / totalCards else 0
                PomodoroTimerBanner(
                    remainingSeconds = if (pomodoroRemainingSeconds > 0) pomodoroRemainingSeconds else state.pomodoro.remainingSeconds,
                    totalSeconds = state.pomodoro.totalSeconds,
                    cardsShown = totalCards,
                    successRate = successRate,
                    isPaused = state.pomodoro.isPaused,
                    onPauseResume = {
                        if (state.pomodoro.isPaused) onResumePomodoro() else onPausePomodoro()
                    }
                )
            }

            // ── Header subtitle: varies by mode ────────────────────────
            when (mode) {
                TrainingScreenMode.BOSS, TrainingScreenMode.BOSS_MEGA -> {
                    Text(text = stringResource(R.string.training_review_session), fontWeight = FontWeight.SemiBold)
                }
                TrainingScreenMode.ELITE -> {
                    Text(text = stringResource(R.string.training_refresh_session), fontWeight = FontWeight.SemiBold)
                }
                else -> {
                    // NORMAL, DRILL, VERB_DRILL — tense label + prompt
                    val sentenceCard = state.cardSession.currentCard as? SentenceCard
                    val cardTense = sentenceCard?.tense
                    val isDrillStyle = mode == TrainingScreenMode.VERB_DRILL || mode == TrainingScreenMode.DAILY_VERBS
                    if (!cardTense.isNullOrBlank()) {
                        if (isDrillStyle) {
                            Text(
                                text = cardTense,
                                fontSize = 13.sp,
                                color = CorrectGreen,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth()
                            )
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
                }
            }

            // ── Grammar chip for NORMAL mode ────────────────────────────
            if (mode == TrainingScreenMode.NORMAL && grammarChip != null) {
                Spacer(modifier = Modifier.height(4.dp))
                SuggestionChip(
                    onClick = { showGrammarSheet = true },
                    label = {
                        Text(
                            text = "📖 ${grammarChip.key}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    icon = { Icon(Icons.Default.MenuBook, null, modifier = Modifier.size(16.dp)) }
                )
            }

            // ── Prompt text ────────────────────────────────────────────
            val rawPrompt = state.cardSession.currentCard?.promptRu ?: ""
            val cleanPrompt = rawPrompt.replace(ParentheticalRegex, "")
            val isDrillStyle = mode == TrainingScreenMode.VERB_DRILL || mode == TrainingScreenMode.DAILY_VERBS
            if (cleanPrompt.isNotBlank()) {
                Text(
                    text = cleanPrompt,
                    fontSize = (18f * state.audio.ruTextScale).sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDrillStyle) CorrectGreen else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Chips for VERB_DRILL / DAILY_VERBS ──────────────────────
            if ((mode == TrainingScreenMode.VERB_DRILL || mode == TrainingScreenMode.DAILY_VERBS) && drillCard != null) {
                VerbDrillChips(
                    card = drillCard,
                    onVerbClick = { showVerbSheet = true },
                    onTenseClick = { showTenseSheet = true }
                )
            }

            // ── Progress indicator ─────────────────────────────────────
            if (mode == TrainingScreenMode.VERB_DRILL) {
                val drillCurrent = state.cardSession.currentIndex
                val drillTotal = state.cardSession.subLessonTotal
                SessionProgressIndicator(
                    current = drillCurrent + 1,
                    total = drillTotal,
                    speedWpm = if (state.cardSession.voiceActiveMs > 0) (state.cardSession.voiceWordCount / (state.cardSession.voiceActiveMs / 60000.0)).toInt() else 0
                )
            } else {
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
                hintLevel,
                clickableWordHints,
                baseDir
            )
            ResultBlock(state)
            UnifiedNavigationRow(
                stateModel = remember(
                    state.cardSession.sessionState,
                    state.cardSession.currentCard,
                    state.cardSession.inputMode,
                    state.boss?.bossActive
                ) {
                    object : com.alexpo.grammermate.data.CardSessionStateModel {
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
                    }
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

    // Verb/tense bottom sheets for VERB_DRILL and DAILY_VERBS modes
    val conjugationCards = state.cardSession.verbConjugationCards
        .filter { it.verb == drillCard?.verb }
    if (showVerbSheet && drillCard != null && !drillCard.verb.isNullOrBlank()) {
        VerbReferenceBottomSheet(
            verb = drillCard.verb,
            tense = drillCard.tense,
            conjugationCards = conjugationCards,
            ttsState = state.audio.ttsState,
            onSpeakVerb = { onTtsSpeak() },
            onDismiss = { showVerbSheet = false }
        )
    }
    if (showTenseSheet && drillCard != null && !drillCard.tense.isNullOrBlank()) {
        val tenseInfo = remember(drillCard.tense) { getTenseInfo(drillCard.tense) }
        TenseInfoBottomSheet(
            tenseName = drillCard.tense,
            tenseInfo = tenseInfo,
            onDismiss = { showTenseSheet = false }
        )
    }
    if (showGrammarSheet && grammarChip != null) {
        GrammarInfoBottomSheet(
            chip = grammarChip,
            onDismiss = { showGrammarSheet = false }
        )
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
                    modifier = Modifier.testTag("card_prompt_text"),
                    text = state.cardSession.currentCard?.promptRu?.let {
                        HintCalculator.calculateEffectiveHints(
                            promptRu = it,
                            encounterCount = state.cardSession.encounterCount,
                            hintLevel = state.cardSession.hintLevel,
                            sessionOffset = state.cardSession.hintSessionOffset,
                            isBossBattle = state.boss?.bossActive == true,
                            isReviewMode = state.cardSession.isReviewMode
                        )
                    } ?: stringResource(R.string.training_no_cards),
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
    hintLevel: HintLevel = HintLevel.EASY,
    clickableWordHints: Boolean = false,
    baseDir: java.io.File? = null
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
    val contractAdapter = remember(state.cardSession, onSetInputMode, onInputChange, onSelectWordFromBank, onRemoveLastWord) {
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
        hintAnswer = state.cardSession.answerText,
        onShowReport = { showReportSheet = true },
        reportCard = state.cardSession.currentCard,
        hintLevel = hintLevel,
        clickableWordHints = clickableWordHints,
        baseDir = baseDir
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
        "el" -> "el-GR"
        "ru" -> "ru-RU"
        else -> "en-US"
    }
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt ?: context.getString(R.string.training_say_the_translation))
    }
    launcher.launch(intent)
}

/**
 * Renders verb/tense/group info chips below the prompt for VERB_DRILL mode.
 * Tapping verb chip opens VerbReferenceBottomSheet, tense chip opens TenseInfoBottomSheet.
 */
@Composable
private fun VerbDrillChips(
    card: VerbDrillCard,
    onVerbClick: () -> Unit = {},
    onTenseClick: () -> Unit = {}
) {
    val verbText = card.verb
    if (!verbText.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip(
                onClick = onVerbClick,
                label = {
                    Text(
                        text = if (card.rank != null) "$verbText #${card.rank}" else verbText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                icon = { Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp)) }
            )
            card.tense?.takeIf { it.isNotBlank() }?.let { tenseText ->
                SuggestionChip(
                    onClick = onTenseClick,
                    label = {
                        Text(
                            text = abbreviateTense(tenseText),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                )
            }
            card.group?.takeIf { it.isNotBlank() }?.let { groupText ->
                SuggestionChip(
                    onClick = {},
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

/** Abbreviates Italian tense names for compact chip display. */
private fun abbreviateTense(tense: String): String {
    return mapOf(
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
        "Congiuntivo Passato" to "Cong. Pass."
    )[tense] ?: tense.take(8)
}

/**
 * Completion screen shown when a VerbDrill session finishes.
 * Displays correct/incorrect stats and offers More (next batch) and Exit buttons.
 */
@Composable
private fun VerbDrillCompletionContent(
    correctCount: Int,
    incorrectCount: Int,
    onMore: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎉",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.verb_completion_excellent),
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.verb_completion_stats, correctCount, incorrectCount),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onMore,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.verb_more))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.verb_exit))
        }
    }
}

/**
 * Universal session completion screen for NORMAL, DRILL, ELITE, BOSS,
 * DAILY_TRANSLATE, DAILY_VERBS modes.
 * Shows stats + OK button. Stays until user presses OK.
 */
@Composable
private fun SessionCompletionContent(
    modifier: Modifier = Modifier,
    mode: TrainingScreenMode,
    correctCount: Int,
    incorrectCount: Int,
    onDone: () -> Unit
) {
    val title = when (mode) {
        TrainingScreenMode.BOSS, TrainingScreenMode.BOSS_MEGA -> stringResource(R.string.training_review_session)
        TrainingScreenMode.ELITE -> stringResource(R.string.training_refresh_session)
        TrainingScreenMode.VERB_DRILL -> "Verb Drill Complete!"
        else -> stringResource(R.string.verb_completion_excellent)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🎉", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        val total = correctCount + incorrectCount
        val rate = if (total > 0) correctCount * 100 / total else 0
        Text(
            text = "$correctCount correct / $incorrectCount incorrect ($rate%)",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("OK")
        }
    }
}
