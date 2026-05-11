package com.alexpo.grammermate.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
                    onShowSentenceAnswer = onShowSentenceAnswer,
                    onShowVerbAnswer = onShowVerbAnswer,
                    onSpeak = onSpeak,
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

@Composable
private fun ColumnScope.CardSessionBlock(
    state: DailySessionState,
    blockProgress: BlockProgress,
    onAdvance: () -> Boolean,
    onComplete: () -> Unit,
    onExit: () -> Unit,
    onSubmitSentence: (String) -> Boolean,
    onSubmitVerb: (String) -> Boolean,
    onShowSentenceAnswer: () -> String?,
    onShowVerbAnswer: () -> String?,
    onSpeak: (String) -> Unit,
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
                // Fire ViewModel-side effects (sounds) via the appropriate callback
                val task = state.tasks.getOrNull(state.taskIndex)
                when (task) {
                    is DailyTask.TranslateSentence -> onSubmitSentence(input)
                    is DailyTask.ConjugateVerb -> onSubmitVerb(input)
                    else -> {}
                }
            }
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

    TrainingCardSession(
        contract = provider,
        onExit = onExit,
        onComplete = { blockComplete = true },
        modifier = Modifier.weight(1f)
    )
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
