package com.alexpo.grammermate.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.SrsRating
import com.alexpo.grammermate.data.SpacedRepetitionConfig
import com.alexpo.grammermate.feature.training.HintCalculator
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.VocabDrillCard
import com.alexpo.grammermate.data.VocabDrillDirection
import com.alexpo.grammermate.data.VocabDrillSessionState
import com.alexpo.grammermate.data.VocabDrillUiState
import com.alexpo.grammermate.data.VoiceResult
import com.alexpo.grammermate.ui.components.QrShareDialog
import com.alexpo.grammermate.ui.components.SharedReportSheet
import com.alexpo.grammermate.ui.designsystem.foundation.scaledSp
import com.alexpo.grammermate.ui.features.vocab.components.PosBadge
import com.alexpo.grammermate.ui.features.vocab.components.VocabDrillCardFront
import com.alexpo.grammermate.ui.features.vocab.components.VocabRatingLadder
import com.alexpo.grammermate.ui.features.vocab.components.VocabRatingOption
import com.alexpo.grammermate.ui.features.vocab.components.VocabSpeakerState
import com.alexpo.grammermate.ui.features.vocab.components.VocabVoiceOutcome
import com.alexpo.grammermate.shared.AuditLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope

@Composable
fun VocabDrillScreen(
    viewModel: VocabDrillViewModel,
    onBack: () -> Unit,
    hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY,
    textScale: Float = 1.0f,
    voiceAutoStart: Boolean = true,
    onBluetoothSetup: suspend () -> Boolean = { true },
    onBluetoothCleanup: () -> Unit = {},
    onClearBadSentences: (() -> Unit)? = null,
    badSentenceCount: Int = 0
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Track whether RecognizerIntent is currently active (to prevent double-launch)
    var isVoiceActive by remember { mutableStateOf(false) }

    // Voice recognition launcher — same pattern as VerbDrill
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isVoiceActive = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.handleVoiceResult(spoken)
            }
        }
    }

    // Helper to launch voice recognition with the correct language tag
    val onStartVoice: () -> Unit = {
        if (!isVoiceActive) {
            isVoiceActive = true
            val direction = state.session?.direction ?: state.drillDirection
            val langTag = when (direction) {
                VocabDrillDirection.IT_TO_RU -> "ru-RU"
                VocabDrillDirection.RU_TO_IT -> "it-IT"
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the translation")
            }
            scope.launch { onBluetoothSetup() }
            speechLauncher.launch(intent)
        }
    }

    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.vocab_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        return
    }

    val session = state.session
    if (session != null) {
        if (session.isComplete) {
            VocabDrillCompletionScreen(
                session = session,
                onExit = viewModel::exitSession,
                onContinue = viewModel::startSession
            )
        } else {
            VocabDrillCardScreen(
                session = session,
                voiceAutoStart = voiceAutoStart,
                isVoiceActive = isVoiceActive,
                ttsState = viewModel.ttsState.collectAsStateWithLifecycle().value,
                onFlip = {
                    val currentCard = session.cards.getOrNull(session.currentIndex)
                    if (currentCard != null) {
                        AuditLogger.getInstanceOrNull()?.vocabCardFlip(
                            currentCard.word.id,
                            session.direction.name
                        )
                    }
                    viewModel.flipCard()
                },
                onAnswer = { rating ->
                    val currentCard = session.cards.getOrNull(session.currentIndex)
                    if (currentCard != null) {
                        AuditLogger.getInstanceOrNull()?.vocabCardRate(currentCard.word.id, rating.ordinal + 1)
                    }
                    viewModel.answerRating(rating)
                },
                onSpeak = { text ->
                    val currentCard = session.cards.getOrNull(session.currentIndex)
                    if (currentCard != null) {
                        AuditLogger.getInstanceOrNull()?.ttsSpeak(currentCard.word.id, text, "vocab")
                    }
                    viewModel.speakTts(text)
                },
                onStartVoice = {
                    val lang = when (session.direction) {
                        VocabDrillDirection.IT_TO_RU -> "ru-RU"
                        VocabDrillDirection.RU_TO_IT -> "it-IT"
                    }
                    AuditLogger.getInstanceOrNull()?.voiceStart(lang, false)
                    onStartVoice()
                },
                onAutoStartVoice = onStartVoice,
                onSkipVoice = viewModel::skipVoice,
                onExit = {
                    AuditLogger.getInstanceOrNull()?.backPress("vocab_drill")
                    viewModel.exitSession()
                },
                onFlagBadSentence = viewModel::flagBadSentence,
                onUnflagBadSentence = viewModel::unflagBadSentence,
                isBadSentence = viewModel::isBadSentence,
                onExportBadSentences = viewModel::exportBadSentences,
                hintLevel = hintLevel,
                textScale = textScale,
                onClearBadSentences = onClearBadSentences,
                badSentenceCount = badSentenceCount
            )
        }
    } else {
        VocabDrillSelectionScreen(
            state = state,
            onSelectPos = viewModel::selectPos,
            onSetRankRange = viewModel::setRankRange,
            onSetDirection = viewModel::setDirection,
            onStart = {
                AuditLogger.getInstanceOrNull()?.vocabDrillStart(
                    state.drillDirection.name,
                    state.selectedPos ?: "",
                    "${state.rankMin}-${state.rankMax}"
                )
                viewModel.startSession()
            },
            onBack = onBack
        )
    }
}

// -- Selection Screen --

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun VocabDrillSelectionScreen(
    state: VocabDrillUiState,
    onSelectPos: (String?) -> Unit,
    onSetRankRange: (Int, Int) -> Unit,
    onSetDirection: (VocabDrillDirection) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                AuditLogger.getInstanceOrNull()?.backPress("vocab_drill")
                onBack()
            }) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.vocab_content_desc_back)) }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.vocab_title), fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Direction filter chips
        Text(text = stringResource(R.string.vocab_direction), style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = state.drillDirection == VocabDrillDirection.IT_TO_RU,
                onClick = {
                    AuditLogger.getInstanceOrNull()?.settingsChange("vocab_direction", "IT_TO_RU")
                    onSetDirection(VocabDrillDirection.IT_TO_RU)
                },
                label = { Text(stringResource(R.string.vocab_direction_it_ru)) }
            )
            FilterChip(
                selected = state.drillDirection == VocabDrillDirection.RU_TO_IT,
                onClick = {
                    AuditLogger.getInstanceOrNull()?.settingsChange("vocab_direction", "RU_TO_IT")
                    onSetDirection(VocabDrillDirection.RU_TO_IT)
                },
                label = { Text(stringResource(R.string.vocab_direction_ru_it)) }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // POS filter chips
        Text(text = stringResource(R.string.vocab_part_of_speech), style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = state.selectedPos == null,
                onClick = {
                    AuditLogger.getInstanceOrNull()?.settingsChange("vocab_pos", "all")
                    onSelectPos(null)
                },
                label = { Text(stringResource(R.string.vocab_filter_all)) }
            )
            state.availablePos.forEach { pos ->
                FilterChip(
                    selected = state.selectedPos == pos,
                    onClick = {
                        AuditLogger.getInstanceOrNull()?.settingsChange("vocab_pos", pos)
                        onSelectPos(pos)
                    },
                    label = { Text(posLabel(pos)) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Rank range filter
        Text(text = stringResource(R.string.vocab_word_frequency), style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val rankOptions = listOf(
                stringResource(R.string.vocab_rank_top100) to (0 to 100),
                stringResource(R.string.vocab_rank_top500) to (0 to 500),
                stringResource(R.string.vocab_rank_top1000) to (0 to 1000),
                stringResource(R.string.vocab_rank_all) to (0 to Int.MAX_VALUE)
            )
            rankOptions.forEach { (label, range) ->
                val isSelected = state.rankMin == range.first && state.rankMax == range.second
                FilterChip(
                    selected = isSelected,
                    onClick = { onSetRankRange(range.first, range.second) },
                    label = { Text(label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Stats
        if (state.totalCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.vocab_due_count, state.dueCount, state.totalCount),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    if (state.masteredCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.vocab_mastered_count, state.masteredCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MasteryGreen
                        )
                        // Per-POS breakdown
                        val posBreakdown = state.masteredByPos.entries
                            .sortedByDescending { it.value }
                            .map { (pos, count) -> "${posLabel(pos)}: $count" }
                        if (posBreakdown.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = posBreakdown.joinToString(" | "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = if (state.totalCount > 0) {
                            val learned = state.totalCount - state.dueCount
                            learned.toFloat() / state.totalCount.toFloat()
                        } else 0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.vocab_no_words_loaded),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.weight(1f))

        // Start button
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.dueCount > 0
        ) {
            Text(text = if (state.dueCount > 0) stringResource(R.string.vocab_start, state.dueCount) else stringResource(R.string.vocab_no_due_words))
        }
    }
}

// -- Card Screen --

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VocabDrillCardScreen(
    session: VocabDrillSessionState,
    voiceAutoStart: Boolean,
    isVoiceActive: Boolean,
    ttsState: TtsState,
    onFlip: () -> Unit,
    onAnswer: (SrsRating) -> Unit,
    onSpeak: (String) -> Unit,
    onStartVoice: () -> Unit,
    onAutoStartVoice: () -> Unit,
    onSkipVoice: () -> Unit,
    onExit: () -> Unit,
    onFlagBadSentence: () -> Unit = {},
    onUnflagBadSentence: () -> Unit = {},
    isBadSentence: () -> Boolean = { false },
    onExportBadSentences: () -> String? = { null },
    hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY,
    textScale: Float = 1.0f,
    onClearBadSentences: (() -> Unit)? = null,
    badSentenceCount: Int = 0
) {
    val card = session.cards.getOrElse(session.currentIndex) { return }
    val totalCards = session.cards.size
    val currentIndex = session.currentIndex + 1 // 1-based for display

    var showReportSheet by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val reportText = buildString {
        appendLine("ID: ${card.word.id}")
        appendLine("Word: ${card.word.word}")
        appendLine("Meaning: ${card.word.meaningRu}")
    }

    // Auto-flip when voice is completed with a result
    LaunchedEffect(session.voiceCompleted, session.voiceResult) {
        if (session.voiceCompleted && session.voiceResult != null && !session.isFlipped) {
            delay(800L)
            onFlip()
        }
    }

    // Auto-launch voice input when voice auto-start is on and a new card appears
    LaunchedEffect(session.currentIndex, voiceAutoStart) {
        if (voiceAutoStart && !session.isFlipped && !session.voiceCompleted && !isVoiceActive) {
            delay(500L) // Let the card animate in before launching
            // Re-check conditions after delay (state may have changed)
            if (!session.voiceCompleted && !session.isFlipped) {
                onAutoStartVoice()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with back + progress + report
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.vocab_content_desc_back))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.vocab_title), fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$currentIndex/$totalCards",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { showReportSheet = true }) {
                    Icon(
                        Icons.Default.ReportProblem,
                        contentDescription = stringResource(R.string.vocab_content_desc_report_word),
                        tint = if (isBadSentence()) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = currentIndex.toFloat() / totalCards.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Card
        if (session.isFlipped) {
            VocabDrillCardBack(
                card = card,
                direction = session.direction,
                ttsState = ttsState,
                onSpeak = onSpeak,
                hintLevel = hintLevel,
                textScale = textScale
            )
        } else {
            VocabDrillCardFront(
                pos = card.word.pos,
                rank = card.word.rank,
                displayText = when (session.direction) {
                    VocabDrillDirection.IT_TO_RU -> card.word.word
                    VocabDrillDirection.RU_TO_IT -> HintCalculator.calculateEffectiveHints(
                        promptRu = card.word.meaningRu ?: "",
                        encounterCount = 0,
                        hintLevel = hintLevel,
                        sessionOffset = 0
                    )
                },
                speakerState = if (session.direction == VocabDrillDirection.IT_TO_RU) {
                    when (ttsState) {
                        is TtsState.Speaking -> VocabSpeakerState.Speaking
                        is TtsState.Initializing -> VocabSpeakerState.Initializing
                        is TtsState.Error -> VocabSpeakerState.Error(ttsState.reason)
                        else -> VocabSpeakerState.Idle
                    }
                } else null,
                onSpeak = { onSpeak(card.word.word) },
                voiceCompleted = session.voiceCompleted,
                onStartVoice = onStartVoice,
                voiceOutcome = when (session.voiceResult) {
                    VoiceResult.CORRECT -> VocabVoiceOutcome.Correct
                    VoiceResult.WRONG -> VocabVoiceOutcome.Wrong
                    VoiceResult.SKIPPED -> VocabVoiceOutcome.Skipped
                    null -> null
                },
                voiceAttempts = session.voiceAttempts,
                voiceRecognizedText = session.voiceRecognizedText,
                textScale = textScale
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom action buttons
        if (session.isFlipped) {
            // Anki-style 4 rating buttons
            val currentStep = session.cards.getOrElse(session.currentIndex) { null }
                ?.mastery?.intervalStepIndex ?: 0
            // Authoritative ladder (byte-identical to the inline literal this
            // code previously duplicated). Preview math kept verbatim: HARD
            // shows the current rung (delta 0), GOOD/EASY preview +1/+2
            // coerced to the last rung — matching the VM's own step deltas.
            val ladder = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS
            VocabRatingLadder(
                againSubtitle = stringResource(R.string.vocab_rating_again_interval),
                hardSubtitle = "${ladder[currentStep]}d",
                goodSubtitle = "${ladder[(currentStep + 1).coerceAtMost(ladder.size - 1)]}d",
                easySubtitle = "${ladder[(currentStep + 2).coerceAtMost(ladder.size - 1)]}d",
                onAnswer = { onAnswer(it.toSrsRating()) }
            )
        } else {
            // Flip/Skip row (mic is on the card front)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Skip button — always active, skips voice input and auto-flips
                OutlinedButton(
                    onClick = {
                        if (!session.voiceCompleted) {
                            onSkipVoice()
                        } else {
                            onFlip()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.vocab_skip))
                }

                // Flip button — always active, user can flip at any time
                Button(
                    onClick = onFlip,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Flip,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.vocab_flip))
                }
            }
        }
    }

    // Report sheet
    if (showReportSheet) {
        SharedReportSheet(
            onDismiss = { showReportSheet = false },
            cardPromptText = "${card.word.word} — ${card.word.meaningRu ?: ""}",
            cardId = card.word.id,
            isFlagged = isBadSentence(),
            onFlag = onFlagBadSentence,
            onUnflag = onUnflagBadSentence,
            onHideCard = { showReportSheet = false },
            onExportBadSentences = onExportBadSentences,
            onCopyText = {
                if (reportText.isNotBlank()) {
                    clipboardManager.setText(AnnotatedString(reportText))
                }
            },
            exportResult = { path ->
                exportMessage = if (path != null)
                    "Exported to $path"
                else
                    "No bad sentences to export"
            },
            title = stringResource(R.string.vocab_word_options),
            shareText = reportText,
            onShareQr = { showQrDialog = true },
            onClearBadSentences = onClearBadSentences,
            badSentenceCount = badSentenceCount
        )
    }
    if (exportMessage != null) {
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text(stringResource(R.string.vocab_export_title)) },
            text = { Text(exportMessage!!) },
            confirmButton = {
                TextButton(onClick = { exportMessage = null }) {
                    Text(stringResource(R.string.vocab_ok))
                }
            }
        )
    }
    if (showQrDialog) {
        val promptRu = card.word.meaningRu ?: card.word.word
        val answerText = card.word.word
        QrShareDialog(
            promptRu = promptRu,
            answerText = answerText,
            targetLanguage = "it",
            onDismiss = { showQrDialog = false }
        )
    }
}

// -- Card Back --

@Composable
private fun VocabDrillCardBack(
    card: VocabDrillCard,
    direction: VocabDrillDirection,
    ttsState: TtsState,
    onSpeak: (String) -> Unit,
    hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY,
    textScale: Float = 1.0f
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (direction == VocabDrillDirection.IT_TO_RU) {
                // IT_TO_RU: back shows Russian meaning + Italian word with TTS
                // Italian word (smaller, with TTS)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PosBadge(pos = card.word.pos)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = card.word.word,
                            fontSize = scaledSp(20f, textScale),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(
                        onClick = { onSpeak(card.word.word) },
                        enabled = ttsState != TtsState.Initializing
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = stringResource(R.string.vocab_content_desc_listen))
                    }
                }

                // Russian translation
                val meaningRu = card.word.meaningRu
                if (!meaningRu.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = meaningRu,
                        fontSize = scaledSp(18f, textScale),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                // RU_TO_IT: back shows Italian word + collocations
                // Russian meaning (smaller)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PosBadge(pos = card.word.pos)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = card.word.meaningRu ?: "?",
                        fontSize = scaledSp(16f, textScale),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Italian word (large) with TTS
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = card.word.word,
                        fontSize = scaledSp(24f, textScale),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = { onSpeak(card.word.word) },
                        enabled = ttsState != TtsState.Initializing
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = stringResource(R.string.vocab_content_desc_listen))
                    }
                }
            }

            // Forms -- always visible (reference data, not hints)
            val forms = card.word.forms
            if (forms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.vocab_forms),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val formItems: List<Pair<String, String?>> = when (card.word.pos) {
                            "numbers" -> listOf(
                                "m" to forms["form_m"],
                                "f" to forms["form_f"]
                            )
                            "pronouns" -> listOf(
                                "sg m" to forms["form_sg_m"],
                                "sg f" to forms["form_sg_f"],
                                "pl m" to forms["form_pl_m"],
                                "pl f" to forms["form_pl_f"]
                            )
                            else -> listOf(
                                "m sg" to forms["msg"],
                                "f sg" to forms["fsg"],
                                "m pl" to forms["mpl"],
                                "f pl" to forms["fpl"]
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            formItems.forEach { (label, value) ->
                                FormItem(label = label, value = value)
                            }
                        }
                    }
                }
            }

            // Collocations -- always visible (reference data, not hints)
            val collocations = card.word.collocations
            if (collocations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.vocab_collocations),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    collocations.take(5).forEach { coll ->
                        Text(
                            text = coll,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                    if (collocations.size > 5) {
                        Text(
                            text = stringResource(R.string.vocab_more_count, collocations.size - 5),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Mastery indicator
            Spacer(modifier = Modifier.height(12.dp))
            val step = card.mastery.intervalStepIndex
            val maxStep = 9
            when {
                step >= maxStep -> {
                    // Mastered — gold accent
                    Text(
                        text = stringResource(R.string.vocab_mastered),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB8860B) // dark goldenrod
                    )
                }
                step >= TrainingConfig.LEARNED_THRESHOLD -> {
                    // Learned — green accent
                    Text(
                        text = stringResource(R.string.vocab_learned),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MasteryGreen
                    )
                }
                else -> {
                    // Learning — neutral with step counter
                    Text(
                        text = "${stringResource(R.string.vocab_learning)} — ${stringResource(R.string.vocab_step, step + 1, maxStep)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// -- Helpers --

/**
 * Localized label for a raw POS key (chip labels on the selection screen and
 * the per-POS mastery breakdown). One owner for the previously duplicated
 * `when` mapping.
 */
@Composable
private fun posLabel(pos: String): String = when (pos) {
    "nouns" -> stringResource(R.string.vocab_pos_nouns)
    "verbs" -> stringResource(R.string.vocab_pos_verbs)
    "adjectives" -> stringResource(R.string.vocab_pos_adjectives)
    "adverbs" -> stringResource(R.string.vocab_pos_adverbs)
    "numbers" -> stringResource(R.string.vocab_pos_numbers)
    "pronouns" -> stringResource(R.string.vocab_pos_pronouns)
    else -> pos.replaceFirstChar { it.uppercase() }
}

/**
 * Name- and ordinal-preserving map from the ladder organism's UI-owned
 * option to the scheduling-layer rating. The `vocabCardRate` audit payload
 * (`rating.ordinal + 1`, `VocabDrillScreen.onAnswer`) stays byte-identical
 * because both enums declare AGAIN/HARD/GOOD/EASY in the same order.
 */
private fun VocabRatingOption.toSrsRating(): SrsRating = when (this) {
    VocabRatingOption.AGAIN -> SrsRating.AGAIN
    VocabRatingOption.HARD -> SrsRating.HARD
    VocabRatingOption.GOOD -> SrsRating.GOOD
    VocabRatingOption.EASY -> SrsRating.EASY
}

@Composable
private fun FormItem(label: String, value: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = value ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// -- Completion Screen --

@Composable
private fun VocabDrillCompletionScreen(
    session: VocabDrillSessionState,
    onExit: () -> Unit,
    onContinue: () -> Unit
) {
    var showStats by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(800L)
        showStats = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (session.correctCount == session.cards.size) stringResource(R.string.vocab_completion_perfect) else stringResource(R.string.vocab_completion_done),
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (showStats) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${session.correctCount}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.vocab_completion_correct),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${session.incorrectCount}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = stringResource(R.string.vocab_completion_wrong),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.vocab_completion_words_reviewed, session.cards.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.vocab_completion_exit))
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.vocab_completion_continue))
                }
            }
        }
    }
}
