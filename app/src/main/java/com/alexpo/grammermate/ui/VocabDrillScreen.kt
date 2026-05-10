package com.alexpo.grammermate.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.VocabDrillCard
import com.alexpo.grammermate.data.VocabDrillSessionState
import com.alexpo.grammermate.data.VocabDrillUiState
import kotlinx.coroutines.delay

@Composable
fun VocabDrillScreen(
    viewModel: VocabDrillViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
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
                ttsState = viewModel.ttsState.collectAsState().value,
                onFlip = viewModel::flipCard,
                onCorrect = viewModel::markCorrect,
                onWrong = viewModel::markWrong,
                onSpeak = viewModel::speakTts,
                onExit = viewModel::exitSession
            )
        }
    } else {
        VocabDrillSelectionScreen(
            state = state,
            onSelectPos = viewModel::selectPos,
            onSetRankRange = viewModel::setRankRange,
            onStart = viewModel::startSession,
            onBack = onBack
        )
    }
}

// ── Selection Screen ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VocabDrillSelectionScreen(
    state: VocabDrillUiState,
    onSelectPos: (String?) -> Unit,
    onSetRankRange: (Int, Int) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Vocab Drill", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(16.dp))

        // POS filter chips
        Text(
            text = "Part of speech",
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
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
        Text(
            text = "Word frequency",
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
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

// ── Card Screen ──────────────────────────────────────────────────────

@Composable
private fun VocabDrillCardScreen(
    session: VocabDrillSessionState,
    ttsState: TtsState,
    onFlip: () -> Unit,
    onCorrect: () -> Unit,
    onWrong: () -> Unit,
    onSpeak: (String) -> Unit,
    onExit: () -> Unit
) {
    val card = session.cards.getOrElse(session.currentIndex) { return }
    val totalCards = session.cards.size
    val currentIndex = session.currentIndex + 1 // 1-based for display

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
                Text(text = "Vocab Drill", fontWeight = FontWeight.SemiBold)
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
                ttsState = ttsState,
                onSpeak = onSpeak
            )
        } else {
            VocabDrillCardFront(
                card = card,
                ttsState = ttsState,
                onSpeak = onSpeak
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom action buttons
        if (session.isFlipped) {
            // Answer buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // "Didn't know" button
                OutlinedButton(
                    onClick = onWrong,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Didn't know")
                }
                // "Knew it" button
                Button(
                    onClick = onCorrect,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Knew it")
                }
            }
        } else {
            // Flip button
            Button(
                onClick = onFlip,
                modifier = Modifier.fillMaxWidth()
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

@Composable
private fun VocabDrillCardFront(
    card: VocabDrillCard,
    ttsState: TtsState,
    onSpeak: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // POS badge
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PosBadge(pos = card.word.pos)
                RankBadge(rank = card.word.rank)
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Italian word (large)
            Text(
                text = card.word.word,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            // TTS button
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
    }
}

@Composable
private fun VocabDrillCardBack(
    card: VocabDrillCard,
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

// ── Completion Screen ──────────────────────────────────────────────────

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
