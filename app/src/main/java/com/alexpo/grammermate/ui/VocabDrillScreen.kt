package com.alexpo.grammermate.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.VocabDrillCard
import com.alexpo.grammermate.data.VocabDrillDirection
import com.alexpo.grammermate.data.VocabDrillSessionState
import com.alexpo.grammermate.data.VocabDrillUiState
import com.alexpo.grammermate.data.VoiceResult
import kotlinx.coroutines.delay

@Composable
fun VocabDrillScreen(
    viewModel: VocabDrillViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

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
                    text = "Loading...",
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
                voiceModeEnabled = state.voiceModeEnabled,
                isVoiceActive = isVoiceActive,
                ttsState = viewModel.ttsState.collectAsState().value,
                onFlip = viewModel::flipCard,
                onAnswer = viewModel::answerRating,
                onSpeak = viewModel::speakTts,
                onStartVoice = onStartVoice,
                onAutoStartVoice = onStartVoice,
                onSkipVoice = viewModel::skipVoice,
                onExit = viewModel::exitSession
            )
        }
    } else {
        VocabDrillSelectionScreen(
            state = state,
            onSelectPos = viewModel::selectPos,
            onSetRankRange = viewModel::setRankRange,
            onSetDirection = viewModel::setDirection,
            onSetVoiceMode = viewModel::setVoiceMode,
            onStart = viewModel::startSession,
            onBack = onBack
        )
    }
}

// -- Selection Screen --

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VocabDrillSelectionScreen(
    state: VocabDrillUiState,
    onSelectPos: (String?) -> Unit,
    onSetRankRange: (Int, Int) -> Unit,
    onSetDirection: (VocabDrillDirection) -> Unit,
    onSetVoiceMode: (Boolean) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Flashcards", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Direction filter chips
        Text(text = "Direction", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = state.drillDirection == VocabDrillDirection.IT_TO_RU,
                onClick = { onSetDirection(VocabDrillDirection.IT_TO_RU) },
                label = { Text("IT → RU") }
            )
            FilterChip(
                selected = state.drillDirection == VocabDrillDirection.RU_TO_IT,
                onClick = { onSetDirection(VocabDrillDirection.RU_TO_IT) },
                label = { Text("RU → IT") }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Voice input toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (state.voiceModeEnabled) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Voice input (auto)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(
                checked = state.voiceModeEnabled,
                onCheckedChange = onSetVoiceMode
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // POS filter chips
        Text(text = "Part of speech", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = state.selectedPos == null,
                onClick = { onSelectPos(null) },
                label = { Text("All") }
            )
            state.availablePos.forEach { pos ->
                val label = when (pos) {
                    "nouns" -> "Nouns"
                    "verbs" -> "Verbs"
                    "adjectives" -> "Adj."
                    "adverbs" -> "Adv."
                    else -> pos.replaceFirstChar { it.uppercase() }
                }
                FilterChip(
                    selected = state.selectedPos == pos,
                    onClick = { onSelectPos(pos) },
                    label = { Text(label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Rank range filter
        Text(text = "Word frequency", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val rankOptions = listOf(
                "Top 100" to (0 to 100),
                "Top 500" to (0 to 500),
                "Top 1000" to (0 to 1000),
                "All" to (0 to Int.MAX_VALUE)
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
                        text = "Due: ${state.dueCount} / ${state.totalCount}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    if (state.masteredCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Mastered: ${state.masteredCount} words",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF2E7D32)
                        )
                        // Per-POS breakdown
                        val posBreakdown = state.masteredByPos.entries
                            .sortedByDescending { it.value }
                            .map { (pos, count) ->
                                val label = when (pos) {
                                    "nouns" -> "Nouns"
                                    "verbs" -> "Verbs"
                                    "adjectives" -> "Adj."
                                    "adverbs" -> "Adv."
                                    else -> pos.replaceFirstChar { it.uppercase() }
                                }
                                "$label: $count"
                            }
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
                text = "No words loaded",
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
            Text(text = if (state.dueCount > 0) "Start (${state.dueCount} due)" else "No due words")
        }
    }
}

// -- Card Screen --

@Composable
private fun VocabDrillCardScreen(
    session: VocabDrillSessionState,
    voiceModeEnabled: Boolean,
    isVoiceActive: Boolean,
    ttsState: TtsState,
    onFlip: () -> Unit,
    onAnswer: (VocabDrillViewModel.AnswerRating) -> Unit,
    onSpeak: (String) -> Unit,
    onStartVoice: () -> Unit,
    onAutoStartVoice: () -> Unit,
    onSkipVoice: () -> Unit,
    onExit: () -> Unit
) {
    val card = session.cards.getOrElse(session.currentIndex) { return }
    val totalCards = session.cards.size
    val currentIndex = session.currentIndex + 1 // 1-based for display

    // Auto-flip when voice is completed with a result
    LaunchedEffect(session.voiceCompleted, session.voiceResult) {
        if (session.voiceCompleted && session.voiceResult != null && !session.isFlipped) {
            delay(800L)
            onFlip()
        }
    }

    // Auto-launch voice input when voice mode is on and a new card appears
    LaunchedEffect(session.currentIndex, voiceModeEnabled) {
        if (voiceModeEnabled && !session.isFlipped && !session.voiceCompleted && !isVoiceActive) {
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
        // Header with back + progress
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Flashcards", fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = "$currentIndex/$totalCards",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
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
                onSpeak = onSpeak
            )
        } else {
            VocabDrillCardFront(
                card = card,
                direction = session.direction,
                ttsState = ttsState,
                voiceCompleted = session.voiceCompleted,
                voiceResult = session.voiceResult,
                voiceRecognizedText = session.voiceRecognizedText,
                voiceAttempts = session.voiceAttempts,
                onSpeak = onSpeak,
                onStartVoice = onStartVoice
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom action buttons
        if (session.isFlipped) {
            // Anki-style 4 rating buttons
            val currentStep = session.cards.getOrElse(session.currentIndex) { null }
                ?.mastery?.intervalStepIndex ?: 0
            val ladder = listOf(1, 2, 4, 7, 10, 14, 20, 28, 42, 56)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Again
                    OutlinedButton(
                        onClick = { onAnswer(VocabDrillViewModel.AnswerRating.AGAIN) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Again", fontWeight = FontWeight.Bold)
                            Text("<1m", fontSize = 11.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                        }
                    }
                    // Hard
                    OutlinedButton(
                        onClick = { onAnswer(VocabDrillViewModel.AnswerRating.HARD) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFE65100)
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Hard", fontWeight = FontWeight.Bold)
                            Text("${ladder[currentStep]}d", fontSize = 11.sp, color = Color(0xFFE65100).copy(alpha = 0.7f))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Good
                    Button(
                        onClick = { onAnswer(VocabDrillViewModel.AnswerRating.GOOD) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Good", fontWeight = FontWeight.Bold)
                            val goodStep = (currentStep + 1).coerceAtMost(ladder.size - 1)
                            Text("${ladder[goodStep]}d", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                    // Easy
                    Button(
                        onClick = { onAnswer(VocabDrillViewModel.AnswerRating.EASY) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32)
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Easy", fontWeight = FontWeight.Bold)
                            val easyStep = (currentStep + 2).coerceAtMost(ladder.size - 1)
                            Text("${ladder[easyStep]}d", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }
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
                    Text("Skip")
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
                    Text("Flip")
                }
            }
        }
    }
}

// -- Card Front --

@Composable
private fun VocabDrillCardFront(
    card: VocabDrillCard,
    direction: VocabDrillDirection,
    ttsState: TtsState,
    voiceCompleted: Boolean,
    voiceResult: VoiceResult?,
    voiceRecognizedText: String?,
    voiceAttempts: Int,
    onSpeak: (String) -> Unit,
    onStartVoice: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                voiceCompleted && voiceResult == VoiceResult.CORRECT ->
                    Color(0xFFE8F5E9).copy(alpha = 0.7f) // light green tint
                voiceCompleted && voiceResult == VoiceResult.WRONG ->
                    Color(0xFFFFEBEE).copy(alpha = 0.7f) // light red tint
                else ->
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // POS badge + rank
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PosBadge(pos = card.word.pos)
                RankBadge(rank = card.word.rank)
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Main word display based on direction
            val displayText = when (direction) {
                VocabDrillDirection.IT_TO_RU -> card.word.word
                VocabDrillDirection.RU_TO_IT -> card.word.meaningRu ?: "?"
            }
            Text(
                text = displayText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // TTS button (only for IT_TO_RU, speak the Italian word)
            if (direction == VocabDrillDirection.IT_TO_RU) {
                Spacer(modifier = Modifier.height(8.dp))
                IconButton(
                    onClick = { onSpeak(card.word.word) },
                    enabled = ttsState != TtsState.INITIALIZING
                ) {
                    when (ttsState) {
                        TtsState.SPEAKING -> Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Speaking",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        TtsState.INITIALIZING -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        TtsState.ERROR -> Icon(
                            Icons.Default.Close,
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

            Spacer(modifier = Modifier.height(24.dp))

            // ── Centered microphone button ──
            if (!voiceCompleted) {
                // Prompt text
                Text(
                    text = "Tap to speak",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                FilledTonalIconButton(
                    onClick = onStartVoice,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Voice input",
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else {
                // Voice completed — show result feedback
                VoiceResultFeedback(
                    voiceResult = voiceResult,
                    voiceAttempts = voiceAttempts,
                    voiceRecognizedText = voiceRecognizedText,
                    voiceCompleted = voiceCompleted
                )
            }
        }
    }
}

// -- Card Back --

@Composable
private fun VocabDrillCardBack(
    card: VocabDrillCard,
    direction: VocabDrillDirection,
    ttsState: TtsState,
    onSpeak: (String) -> Unit
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
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(
                        onClick = { onSpeak(card.word.word) },
                        enabled = ttsState != TtsState.INITIALIZING
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Listen")
                    }
                }

                // Russian translation
                val meaningRu = card.word.meaningRu
                if (!meaningRu.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = meaningRu,
                        fontSize = 18.sp,
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
                        fontSize = 16.sp,
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
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = { onSpeak(card.word.word) },
                        enabled = ttsState != TtsState.INITIALIZING
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Listen")
                    }
                }
            }

            // Forms (for adjectives: msg, fsg, mpl, fpl)
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
                            text = "Forms",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FormItem(label = "m sg", value = forms["msg"])
                            FormItem(label = "f sg", value = forms["fsg"])
                            FormItem(label = "m pl", value = forms["mpl"])
                            FormItem(label = "f pl", value = forms["fpl"])
                        }
                    }
                }
            }

            // Collocations
            val collocations = card.word.collocations
            if (collocations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Collocations",
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
                            text = "... +${collocations.size - 5} more",
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
            val stepLabel = if (step >= maxStep) "Learned" else "Step ${step + 1}/$maxStep"
            Text(
                text = stepLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

// -- Voice Result Feedback --

@Composable
private fun VoiceResultFeedback(
    voiceResult: VoiceResult?,
    voiceAttempts: Int,
    voiceRecognizedText: String?,
    voiceCompleted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (voiceResult) {
                VoiceResult.CORRECT -> Color(0xFFE8F5E9) // light green
                VoiceResult.WRONG -> Color(0xFFFFEBEE)   // light red
                VoiceResult.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant
                null -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Recognized text
            if (!voiceRecognizedText.isNullOrBlank()) {
                Text(
                    text = "\"$voiceRecognizedText\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Result indicator
            when (voiceResult) {
                VoiceResult.CORRECT -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Correct!",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
                VoiceResult.WRONG -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        if (voiceCompleted) {
                            Text(
                                text = "Moving on...",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                text = "Try again (${voiceAttempts}/3)",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                VoiceResult.SKIPPED -> {
                    Text(
                        text = "Skipped",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                null -> { /* no result yet */ }
            }
        }
    }
}

// -- Badges & Helpers --

@Composable
private fun PosBadge(pos: String) {
    val (label, color) = when (pos) {
        "nouns" -> "noun" to MaterialTheme.colorScheme.primaryContainer
        "verbs" -> "verb" to MaterialTheme.colorScheme.secondaryContainer
        "adjectives" -> "adj." to MaterialTheme.colorScheme.tertiaryContainer
        "adverbs" -> "adv." to MaterialTheme.colorScheme.errorContainer
        else -> pos to MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RankBadge(rank: Int) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = "#$rank",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
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
            text = if (session.correctCount == session.cards.size) "Perfect!" else "Done!",
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
                                text = "Correct",
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
                                text = "Wrong",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${session.cards.size} words reviewed",
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
                    Text("Exit")
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Continue")
                }
            }
        }
    }
}
