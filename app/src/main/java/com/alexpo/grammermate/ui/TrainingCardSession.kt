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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.AnswerResult
import com.alexpo.grammermate.data.CardSessionContract
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.feature.training.HintCalculator
import com.alexpo.grammermate.ui.CorrectGreen
import com.alexpo.grammermate.ui.IncorrectRed
import com.alexpo.grammermate.ui.components.HintAnswerCard
import com.alexpo.grammermate.ui.components.QrShareDialog
import com.alexpo.grammermate.ui.components.SessionProgressIndicator
import com.alexpo.grammermate.ui.components.SharedReportSheet
import com.alexpo.grammermate.ui.components.TtsSpeakerButton
import com.alexpo.grammermate.ui.components.UnifiedInputControlsBar
import com.alexpo.grammermate.ui.components.UnifiedNavigationRow

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
    val onPrev: () -> Unit,
    val onNext: () -> Unit,
    val onExit: () -> Unit,
    val hintLevel: HintLevel = HintLevel.EASY,
    val textScale: Float = 1.0f,
    val encounterCount: Int = 0,
    val sessionOffset: Int = 0,
    val isBossBattle: Boolean = false
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
 * @param header Optional slot for the top header area. Default: tense label + clean prompt.
 * @param cardContent Optional slot for the card display. Default: Card with Russian prompt + TTS.
 * @param inputControls Optional slot for the input area. Default: text field + word bank + voice + submit.
 * @param resultContent Optional slot for answer feedback. Default: correct/incorrect label + answer + TTS replay.
 * @param navigationControls Optional slot for bottom navigation. Default: prev/pause/exit/next buttons.
 * @param completionScreen Optional slot for the completed state. Default: congratulations + stats.
 * @param progressIndicator Optional slot for progress display. Default: [SessionProgressIndicator].
 * @param onExit Called when the user requests to exit.
 * @param onComplete Called when the session is completed.
 * @param modifier Modifier for the root layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    modifier: Modifier = Modifier,
    hintLevel: HintLevel = HintLevel.EASY
) {
    // Local input text state managed by the composable
    var localInputText by remember { mutableStateOf("") }

    // In WORD_BANK mode, input text is derived from selected words, not manual typing
    val effectiveInputText = if (contract.currentInputMode == InputMode.WORD_BANK) {
        contract.getSelectedWords().joinToString(" ")
    } else {
        localInputText
    }

    // Read result state from the contract/adapter
    val lastResult = contract.lastResult
    val isShowingResult = lastResult != null
    val currentCard = contract.currentCard
    val progress = contract.progress

    // Create scope for customization slots
    val scope = remember(contract, currentCard, isShowingResult, lastResult, effectiveInputText, hintLevel) {
        TrainingCardSessionScope(
            contract = contract,
            currentCard = currentCard,
            isShowingResult = isShowingResult,
            lastResult = lastResult,
            progressText = "${progress.current} / ${progress.total}",
            inputText = effectiveInputText,
            onInputChanged = { localInputText = it },
            onSubmit = {
                val text = if (contract.currentInputMode == InputMode.WORD_BANK) {
                    contract.getSelectedWords().joinToString(" ")
                } else {
                    localInputText
                }
                if (text.isNotBlank()) {
                    contract.onInputChanged(text)
                    contract.submitAnswer()
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
            onPrev = {
                contract.prevCard()
                localInputText = ""
            },
            onExit = onExit,
            hintLevel = hintLevel,
            textScale = contract.textScale
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
            SessionProgressIndicator(
                current = scope.contract.progress.current,
                total = scope.contract.progress.total,
                speedWpm = scope.contract.currentSpeedWpm
            )
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

        // Navigation controls — use UnifiedNavigationRow for consistent behavior
        if (navigationControls != null) {
            scope.navigationControls()
        } else {
            UnifiedNavigationRow(
                stateModel = contract,
                supportsPause = contract.supportsPause,
                supportsNavigation = contract.supportsNavigation,
                onPrev = { scope.onPrev() },
                onTogglePause = { contract.togglePause() },
                onStop = { contract.requestExit() },
                onNext = { scope.onNext() }
            )
        }
    }
}

// --- Default implementations (matching GrammarMateApp visual style) ---

/**
 * Header matching GrammarMateApp's TrainingScreen header:
 * tense label (green/primary, 13sp, semi-bold) + clean prompt text (parenthetical hints stripped).
 */
@Composable
private fun DefaultHeader(scope: TrainingCardSessionScope) {
    val card = scope.currentCard
    // Tense label -- always visible (reference data, not a hint)
    val cardTense = card?.let {
        // VerbDrillCard has a tense field; SentenceCard also has tense
        if (it is com.alexpo.grammermate.data.VerbDrillCard) it.tense else null
    }
    if (!cardTense.isNullOrBlank()) {
        Text(
            text = cardTense,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
    }
    // Clean prompt (strip parenthetical hints based on encounter count + hint level)
    val rawPrompt = card?.promptRu ?: ""
    val cleanPrompt = HintCalculator.calculateEffectiveHints(
        promptRu = rawPrompt,
        encounterCount = scope.encounterCount,
        hintLevel = scope.hintLevel,
        sessionOffset = scope.sessionOffset,
        isBossBattle = scope.isBossBattle
    )
    if (cleanPrompt.isNotBlank()) {
        Text(
            text = cleanPrompt,
            fontSize = (18f * scope.textScale).sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Card content matching GrammarMateApp's CardPrompt:
 * Material Card with "RU" label + prompt text (20sp, semi-bold) + TtsSpeakerButton on right.
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
                Text(text = stringResource(R.string.card_label_ru), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = card.promptRu,
                    fontSize = (20f * scope.textScale).sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TtsSpeakerButton(
                ttsState = scope.contract.ttsState,
                enabled = card.promptRu.isNotBlank(),
                onClick = { scope.contract.speakTts() }
            )
        }
    }
}

/**
 * Input controls delegating to [UnifiedInputControlsBar] for identical rendering
 * across all card session modes (Training, VerbDrill, DailyPractice).
 */
@Composable
private fun DefaultInputControls(scope: TrainingCardSessionScope) {
    val contract = scope.contract
    val hasCards = scope.currentCard != null
    val clipboardManager = LocalClipboardManager.current
    var showReportSheet by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    val reportCard = scope.currentCard
    val reportText = if (reportCard != null) {
        val targetText = reportCard.acceptedAnswers.joinToString(" / ")
        "ID: ${reportCard.id}\nSource: ${reportCard.promptRu}\nTarget: $targetText"
    } else {
        ""
    }

    // Report sheet
    if (showReportSheet) {
        SharedReportSheet(
            onDismiss = { showReportSheet = false },
            cardPromptText = reportCard?.promptRu,
            isFlagged = contract.isCurrentCardFlagged(),
            onFlag = { contract.flagCurrentCard() },
            onUnflag = { contract.unflagCurrentCard() },
            onHideCard = { contract.hideCurrentCard() },
            onExportBadSentences = { contract.exportFlaggedCards() },
            onCopyText = {
                if (reportText.isNotBlank()) {
                    clipboardManager.setText(AnnotatedString(reportText))
                }
            },
            shareText = reportCard?.let { "${it.promptRu} — ${it.acceptedAnswers.joinToString(" / ")}" },
            onShareQr = { showQrDialog = true }
        )
    }
    if (showQrDialog && reportCard != null) {
        QrShareDialog(
            promptRu = reportCard.promptRu,
            answerText = reportCard.acceptedAnswers.firstOrNull() ?: "",
            targetLanguage = contract.languageId,
            onDismiss = { showQrDialog = false }
        )
    }

    UnifiedInputControlsBar(
        contract = contract,
        inputText = scope.inputText,
        onInputChanged = scope.onInputChanged,
        onSubmit = scope.onSubmit,
        hasCards = hasCards,
        hintAnswer = contract.lastResult?.displayAnswer?.takeIf { contract.lastResult?.hintShown == true },
        onShowReport = { showReportSheet = true },
        reportCard = scope.currentCard,
        hintLevel = scope.hintLevel
    )
}

/**
 * Result content matching GrammarMateApp's ResultBlock:
 * Correct/Incorrect label (green/red) + TTS replay button (4 states) + "Answer: ..." text.
 * NO "Next" button -- navigation handles that.
 */
@Composable
private fun DefaultResultContent(scope: TrainingCardSessionScope) {
    val result = scope.lastResult ?: return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (result.correct) {
                Text(
                    text = stringResource(R.string.result_correct),
                    color = CorrectGreen,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = stringResource(R.string.result_incorrect),
                    color = IncorrectRed,
                    fontWeight = FontWeight.Bold
                )
            }
            if (scope.contract.supportsTts && result.displayAnswer.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                TtsSpeakerButton(
                    ttsState = scope.contract.ttsState,
                    enabled = true,
                    onClick = { scope.contract.speakTts() }
                )
            }
        }
        if (result.displayAnswer.isNotBlank()) {
            HintAnswerCard(
                answerText = result.displayAnswer
            )
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
            text = stringResource(R.string.completion_well_done),
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
            Text(text = stringResource(R.string.button_done))
        }
    }
}
