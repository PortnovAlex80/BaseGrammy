package com.alexpo.grammermate.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
import com.alexpo.grammermate.data.AnswerResult
import com.alexpo.grammermate.data.CardSessionContract
import com.alexpo.grammermate.data.SessionCard

/**
 * Scope object passed to customization slots inside [TrainingCardSession].
 * Provides access to the contract and local UI state.
 */
@Stable
class TrainingCardSessionScope(
    val contract: CardSessionContract,
    val currentCard: SessionCard?,
    val isShowingResult: Boolean,
    val lastResult: AnswerResult?,
    val progressText: String,
    val inputText: String,
    val onInputChanged: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onNext: () -> Unit,
    val onExit: () -> Unit
)

/**
 * Reusable composable for card-based training sessions.
 *
 * Result tracking is delegated to the [CardSessionContract] adapter. The
 * adapter's [CardSessionContract.lastResult] and [CardSessionContract.currentCard]
 * drive whether the result or input UI is shown. Adapters that need custom
 * submit logic (e.g. [VerbDrillCardSessionProvider]) can bypass the default
 * [CardSessionContract.submitAnswer] flow and set their own result state.
 *
 * @param contract Adapter that provides cards, state, and actions.
 * @param header Optional slot for the top header area. Default: back button + title.
 * @param cardContent Optional slot for the card display. Default: Card with Russian prompt + TTS.
 * @param inputControls Optional slot for the input area. Default: text field + submit button.
 * @param resultContent Optional slot for answer feedback. Default: correct/incorrect label + answer + TTS replay.
 * @param navigationControls Optional slot for bottom navigation. Default: prev/pause/exit/next buttons.
 * @param completionScreen Optional slot for the completed state. Default: congratulations + stats.
 * @param progressIndicator Optional slot for progress display. Default: DrillProgressRow-style bar.
 * @param onExit Called when the user requests to exit.
 * @param onComplete Called when the session is completed.
 * @param modifier Modifier for the root layout.
 */
@Composable
fun TrainingCardSession(
    contract: CardSessionContract,
    header: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    cardContent: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    inputControls: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    resultContent: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    navigationControls: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    completionScreen: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    progressIndicator: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    onExit: () -> Unit,
    onComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Local input text state managed by the composable
    var localInputText by remember { mutableStateOf("") }

    // Read result state from the contract/adapter
    val lastResult = contract.lastResult
    val isShowingResult = lastResult != null
    val currentCard = contract.currentCard
    val progress = contract.progress

    // Create scope for customization slots
    val scope = remember(contract, currentCard, isShowingResult, lastResult, localInputText) {
        TrainingCardSessionScope(
            contract = contract,
            currentCard = currentCard,
            isShowingResult = isShowingResult,
            lastResult = lastResult,
            progressText = "${progress.current} / ${progress.total}",
            inputText = localInputText,
            onInputChanged = { localInputText = it },
            onSubmit = {
                if (localInputText.isNotBlank()) {
                    contract.onInputChanged(localInputText)
                    contract.submitAnswer()
                    // If submitAnswer returned null, the adapter manages its own result state
                    localInputText = ""
                }
            },
            onNext = {
                contract.nextCard()
                localInputText = ""
                if (contract.isComplete) {
                    onComplete()
                }
            },
            onExit = onExit
        )
    }

    // Completion screen
    if (contract.isComplete && !isShowingResult) {
        if (completionScreen != null) {
            scope.completionScreen()
        } else {
            DefaultCompletionScreen(scope)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        if (header != null) {
            scope.header()
        } else {
            DefaultHeader(scope)
        }

        // Progress indicator
        if (progressIndicator != null) {
            scope.progressIndicator()
        } else {
            DefaultProgressIndicator(scope)
        }

        // Card content
        if (cardContent != null) {
            scope.cardContent()
        } else {
            DefaultCardContent(scope)
        }

        // Result or input
        if (isShowingResult) {
            if (resultContent != null) {
                scope.resultContent()
            } else {
                DefaultResultContent(scope)
            }
        } else if (currentCard != null) {
            if (inputControls != null) {
                scope.inputControls()
            } else {
                DefaultInputControls(scope)
            }
        }

        // Navigation controls
        if (navigationControls != null) {
            scope.navigationControls()
        } else {
            DefaultNavigationControls(scope)
        }
    }
}

// --- Default implementations (matching GrammarMateApp visual style) ---

@Composable
private fun DefaultHeader(scope: TrainingCardSessionScope) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = scope.onExit) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "Training", fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Progress indicator matching GrammarMateApp's DrillProgressRow style:
 * rounded green progress bar with progress text overlaid.
 */
@Composable
private fun DefaultProgressIndicator(scope: TrainingCardSessionScope) {
    val progress = scope.contract.progress
    val progressFraction = if (progress.total > 0) {
        progress.current.toFloat() / progress.total.toFloat()
    } else 0f
    val barColor = Color(0xFF4CAF50)
    val trackColor = Color(0xFFC8E6C9)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                    .fillMaxWidth(progressFraction)
                    .background(barColor, RoundedCornerShape(12.dp))
            )
            Text(
                text = "${progress.current} / ${progress.total}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (progressFraction < 0.12f) Color(0xFF2E7D32) else Color.White,
                modifier = Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Card content matching GrammarMateApp's CardPrompt:
 * Card with "RU" label, Russian prompt text, and TTS speaker button.
 */
@Composable
private fun DefaultCardContent(scope: TrainingCardSessionScope) {
    val card = scope.currentCard ?: return
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
                    text = card.promptRu,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (scope.contract.supportsTts) {
                IconButton(
                    onClick = { scope.contract.speakTts() },
                    enabled = card.promptRu.isNotBlank()
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Listen",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Input controls matching GrammarMateApp's AnswerBox style:
 * text field with "Your translation" label, mic trailing icon, submit button.
 */
@Composable
private fun DefaultInputControls(scope: TrainingCardSessionScope) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.OutlinedTextField(
            value = scope.inputText,
            onValueChange = scope.onInputChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Your translation") },
            singleLine = true,
            enabled = scope.currentCard != null,
            trailingIcon = {
                if (scope.contract.supportsVoiceInput) {
                    IconButton(
                        onClick = { /* voice input handled by adapter */ },
                        enabled = scope.currentCard != null
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Voice input"
                        )
                    }
                }
            }
        )
        if (!scope.contract.supportsVoiceInput && !scope.contract.supportsWordBank) {
            // Show "Show answer" button for keyboard-only mode (matching GrammarMateApp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Show answer button
                    if (scope.currentCard != null) {
                        IconButton(
                            onClick = { scope.contract.showAnswer() },
                            enabled = true
                        ) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = "Show answer"
                            )
                        }
                    }
                    // Flag button
                    if (scope.contract.supportsFlagging && scope.currentCard != null) {
                        IconButton(
                            onClick = { scope.contract.flagCurrentCard() },
                            enabled = true
                        ) {
                            Icon(
                                Icons.Default.ReportProblem,
                                contentDescription = "Report sentence"
                            )
                        }
                    }
                }
            }
        }
        Button(
            onClick = scope.onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = scope.inputText.isNotBlank() && scope.currentCard != null
        ) {
            Text(text = "Check")
        }
    }
}

/**
 * Result content matching GrammarMateApp's ResultBlock style:
 * Correct/Incorrect text with answer display and TTS replay button.
 */
@Composable
private fun DefaultResultContent(scope: TrainingCardSessionScope) {
    val result = scope.lastResult ?: return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (result.correct) {
                Text(
                    text = "Correct",
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "Incorrect",
                    color = Color(0xFFC62828),
                    fontWeight = FontWeight.Bold
                )
            }
            if (scope.contract.supportsTts && result.displayAnswer.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { scope.contract.speakTts() },
                    enabled = true
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Listen",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        if (result.displayAnswer.isNotBlank()) {
            Text(text = "Answer: ${result.displayAnswer}")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = scope.onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Next")
        }
    }
}

/**
 * Navigation controls matching GrammarMateApp's NavigationRow style:
 * Prev, Pause/Play, Exit, Next buttons with NavIconButton styling.
 */
@Composable
private fun DefaultNavigationControls(scope: TrainingCardSessionScope) {
    if (!scope.contract.supportsNavigation) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavIconButton(
            onClick = { scope.contract.prevCard() },
            enabled = scope.currentCard != null
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Prev")
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (scope.contract.supportsPause) {
                NavIconButton(
                    onClick = { scope.contract.togglePause() },
                    enabled = scope.currentCard != null
                ) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                }
            }
            NavIconButton(
                onClick = { scope.contract.requestExit() },
                enabled = scope.currentCard != null
            ) {
                Icon(Icons.Default.StopCircle, contentDescription = "Exit session")
            }
            NavIconButton(
                onClick = { scope.onNext() },
                enabled = scope.currentCard != null
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next")
            }
        }
    }
}

/**
 * Navigation icon button matching GrammarMateApp's NavIconButton style:
 * surfaceVariant background with primary accent bottom bar.
 */
@Composable
private fun NavIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) surface else surface.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .background(if (enabled) accent else accent.copy(alpha = 0.3f))
        )
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}

@Composable
private fun DefaultCompletionScreen(scope: TrainingCardSessionScope) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎉", // party popper
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Well done!",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = scope.progressText,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = scope.onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Done")
        }
    }
}
