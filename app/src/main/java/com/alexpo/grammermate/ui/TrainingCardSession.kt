package com.alexpo.grammermate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.alexpo.grammermate.data.AnswerResult
import com.alexpo.grammermate.data.CardSessionContract
import com.alexpo.grammermate.data.InputMode
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
 * @param cardContent Optional slot for the card display. Default: Card with Russian prompt.
 * @param inputControls Optional slot for the input area. Default: text field + submit button.
 * @param resultContent Optional slot for answer feedback. Default: correct/incorrect label + answer.
 * @param navigationControls Optional slot for bottom navigation. Default: none.
 * @param completionScreen Optional slot for the completed state. Default: congratulations + stats.
 * @param progressIndicator Optional slot for progress display. Default: text + linear bar.
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
        }
    }
}

// --- Default implementations ---

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

@Composable
private fun DefaultProgressIndicator(scope: TrainingCardSessionScope) {
    val progress = scope.contract.progress
    Text(
        text = scope.progressText,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    LinearProgressIndicator(
        progress = if (progress.total > 0) {
            progress.current.toFloat() / progress.total.toFloat()
        } else 0f,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DefaultCardContent(scope: TrainingCardSessionScope) {
    val card = scope.currentCard ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "RU",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = card.promptRu,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DefaultInputControls(scope: TrainingCardSessionScope) {
    OutlinedTextField(
        value = scope.inputText,
        onValueChange = scope.onInputChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = "Answer") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { scope.onSubmit() }
        )
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = scope.onSubmit,
        modifier = Modifier.fillMaxWidth(),
        enabled = scope.inputText.isNotBlank()
    ) {
        Text(text = "Ответ")
    }
}

@Composable
private fun DefaultResultContent(scope: TrainingCardSessionScope) {
    val result = scope.lastResult ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (result.correct) {
            Text(
                text = "Правильно",
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                text = "Неправильно",
                color = Color(0xFFC62828),
                fontWeight = FontWeight.Bold
            )
        }
    }
    Text(
        text = "Ответ: ${result.displayAnswer}",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = scope.onNext,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = "Дальше")
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
            text = "Отлично!",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = scope.onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Выход")
        }
    }
}
