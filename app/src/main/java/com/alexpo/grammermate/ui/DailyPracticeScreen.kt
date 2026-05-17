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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ReportProblem
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.alexpo.grammermate.data.DailyBlock
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.DailySessionState
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.SrsRating
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.VocabDrillDirection
import com.alexpo.grammermate.ui.components.QrShareDialog
import com.alexpo.grammermate.ui.components.SharedReportSheet
import com.alexpo.grammermate.feature.daily.BlockProgress

@Composable
fun DailyPracticeScreen(
    state: DailySessionState,
    blockProgress: BlockProgress,
    currentBlock: DailyBlock?,
    currentTask: DailyTask?,
    onShowSentenceAnswer: () -> String?,
    onShowVerbAnswer: () -> String?,
    onRateVocabCard: (SrsRating) -> Unit,
    onSpeak: (String) -> Unit,
    onStopTts: () -> Unit,
    ttsState: TtsState,
    onExit: () -> Unit,
    onComplete: () -> Unit,
    onStartCardBlock: (DailyBlockType, List<com.alexpo.grammermate.data.SessionCard>) -> Unit = { _, _ -> },
    languageId: String = "en",
    onFlagDailyBadSentence: (cardId: String, languageId: String, sentence: String, translation: String, mode: String) -> Unit = { _, _, _, _, _ -> },
    onUnflagDailyBadSentence: (cardId: String) -> Unit = {},
    isDailyBadSentence: (cardId: String) -> Boolean = { false },
    onExportDailyBadSentences: () -> String? = { null },
    hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY,
    textScale: Float = 1.0f,
    voiceAutoStart: Boolean = true
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

    if (!state.active || currentBlock == null || currentTask == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.daily_loading_session), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
        val currentBlockType = currentBlock.type

        LaunchedEffect(currentBlockType) {
            if (previousBlockType != null && currentBlockType != null && previousBlockType != currentBlockType) {
                showBlockTransition = true
            }
            previousBlockType = currentBlockType
        }

        if (showBlockTransition) {
            BlockSparkleOverlay(
                blockType = currentBlockType,
                isLastBlock = currentBlockType == DailyBlockType.VERBS && state.blockIndex >= state.blocks.size - 1,
                onDismiss = { showBlockTransition = false }
            )
        }

        when (currentBlockType) {
            DailyBlockType.TRANSLATE, DailyBlockType.VERBS -> {
                // Navigate to TrainingScreen for card rendering
                LaunchedEffect(state.blockIndex) {
                    val cards: List<com.alexpo.grammermate.data.SessionCard> = when (currentBlockType) {
                        DailyBlockType.TRANSLATE -> currentBlock.tasks
                            .filterIsInstance<DailyTask.TranslateSentence>()
                            .map { it.card }
                        DailyBlockType.VERBS -> currentBlock.tasks
                            .filterIsInstance<DailyTask.ConjugateVerb>()
                            .map { it.card }
                        else -> emptyList()
                    }
                    onStartCardBlock(currentBlockType, cards)
                }
                // Show loading while navigation happens
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            DailyBlockType.VOCAB -> {
                val task = currentTask as DailyTask.VocabFlashcard
                VocabFlashcardBlock(
                    task = task, onFlip = { /* no-op */ }, onRate = onRateVocabCard,
                    onSpeak = onSpeak,
                    onFlagDailyBadSentence = onFlagDailyBadSentence,
                    onUnflagDailyBadSentence = onUnflagDailyBadSentence,
                    isDailyBadSentence = isDailyBadSentence,
                    onExportDailyBadSentences = onExportDailyBadSentences,
                    languageId = languageId,
                    textScale = textScale,
                    onComplete = onComplete
                )
            }
        }
    }
}

@Composable
private fun DailyPracticeHeader(blockProgress: BlockProgress, onExit: () -> Unit) {
    var showExitDialog by remember { mutableStateOf(false) }
    val blockLabel = when (blockProgress.blockType) {
        DailyBlockType.TRANSLATE -> stringResource(R.string.block_translate)
        DailyBlockType.VOCAB -> stringResource(R.string.block_vocab)
        DailyBlockType.VERBS -> stringResource(R.string.block_verbs)
    }
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.daily_exit_title)) },
            text = { Text(stringResource(R.string.daily_exit_message)) },
            confirmButton = { TextButton(onClick = { showExitDialog = false; onExit() }) { Text(stringResource(R.string.button_exit)) } },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text(stringResource(R.string.button_stay)) } }
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { showExitDialog = true }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.content_desc_back)) }
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.daily_header), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
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
    onSpeak: (String) -> Unit,
    onFlagDailyBadSentence: (cardId: String, languageId: String, sentence: String, translation: String, mode: String) -> Unit = { _, _, _, _, _ -> },
    onUnflagDailyBadSentence: (cardId: String) -> Unit = {},
    isDailyBadSentence: (cardId: String) -> Boolean = { false },
    onExportDailyBadSentences: () -> String? = { null },
    languageId: String = "en",
    hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY,
    textScale: Float = 1.0f,
    onComplete: () -> Unit = {}
) {
    var isRated by remember(task.id) { mutableStateOf(false) }
    var isVoiceActive by remember { mutableStateOf(false) }
    var voiceRecognizedText by remember { mutableStateOf<String?>(null) }
    var showReportSheet by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
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
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(promptText, fontSize = (28f * textScale).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSpeak(promptText) }) { Icon(Icons.Default.VolumeUp, stringResource(R.string.content_desc_listen)) }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { showReportSheet = true }) {
                    Icon(Icons.Default.ReportProblem, stringResource(R.string.content_desc_report_word), tint = if (isDailyBadSentence(task.word.id)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Answer text -- always visible (reference data, not a hint)
            Spacer(modifier = Modifier.height(8.dp))
            Text(answerText, fontSize = (18f * textScale).sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
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
    ) { Icon(Icons.Default.Mic, stringResource(R.string.content_desc_voice_input), modifier = Modifier.size(32.dp)) }

    Spacer(modifier = Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(stringResource(R.string.srs_again) to SrsRating.AGAIN, stringResource(R.string.srs_hard) to SrsRating.HARD, stringResource(R.string.srs_good) to SrsRating.GOOD, stringResource(R.string.srs_easy) to SrsRating.EASY).forEach { (label, rating) ->
            val colors = when (rating) {
                SrsRating.AGAIN -> Pair(SrsAgainBackground, SrsAgainText)
                SrsRating.HARD -> Pair(SrsHardBackground, SrsHardText)
                SrsRating.GOOD -> Pair(SrsGoodBackground, SrsGoodText)
                SrsRating.EASY -> Pair(SrsEasyBackground, SrsEasyText)
            }
            OutlinedButton(
                onClick = { onRate(rating) },
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
            },
            shareText = "${word.meaningRu ?: word.word} — ${word.word}",
            onShareQr = { showQrDialog = true }
        )
    }
    if (showQrDialog) {
        QrShareDialog(
            promptRu = word.meaningRu ?: word.word,
            answerText = word.word,
            targetLanguage = languageId,
            onDismiss = { showQrDialog = false }
        )
    }
    if (exportMessage != null) {
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text(stringResource(R.string.report_export_dialog_title)) },
            text = { Text(exportMessage!!) },
            confirmButton = { TextButton(onClick = { exportMessage = null }) { Text(stringResource(R.string.button_ok)) } }
        )
    }
}

@Composable
private fun BlockSparkleOverlay(blockType: DailyBlockType, isLastBlock: Boolean, onDismiss: () -> Unit) {
    val blockLabel = when (blockType) {
        DailyBlockType.TRANSLATE -> stringResource(R.string.block_translate)
        DailyBlockType.VOCAB -> stringResource(R.string.block_vocab)
        DailyBlockType.VERBS -> stringResource(R.string.block_verbs)
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(800); onDismiss() }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✨", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(if (isLastBlock) stringResource(R.string.daily_practice_complete) else "Next: $blockLabel", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (isLastBlock) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.daily_great_job_today), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun DailyPracticeCompletionScreen(onExit: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.daily_session_complete), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.daily_complete_message), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.daily_back_to_home)) }
    }
}
