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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.CardSessionContract
import com.alexpo.grammermate.data.VerbDrillUiState
import com.alexpo.grammermate.ui.components.VerbReferenceBottomSheet
import com.alexpo.grammermate.ui.components.TenseInfoBottomSheet
import com.alexpo.grammermate.ui.components.SharedReportSheet
import com.alexpo.grammermate.ui.components.VoiceAutoLauncher
import com.alexpo.grammermate.ui.components.HintAnswerCard

@Composable
fun VerbDrillScreen(
    viewModel: VerbDrillViewModel,
    onBack: () -> Unit,
    hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY
) {
    val state by viewModel.uiState.collectAsState()

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

    if (state.session != null) {
        val provider = remember { VerbDrillCardSessionProvider(viewModel) }
        VerbDrillSessionWithCardSession(
            provider = provider,
            viewModel = viewModel,
            onExit = viewModel::exitSession,
            hintLevel = hintLevel
        )
    } else {
        VerbDrillSelectionScreen(
            state = state,
            onSelectTense = viewModel::selectTense,
            onSelectGroup = viewModel::selectGroup,
            onToggleSortByFrequency = viewModel::toggleSortByFrequency,
            onToggleVoiceMode = viewModel::toggleVoiceAutoMode,
            onStart = viewModel::startSession,
            onBack = onBack
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerbDrillSessionWithCardSession(
    provider: VerbDrillCardSessionProvider,
    viewModel: VerbDrillViewModel,
    onExit: () -> Unit,
    hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY
) {
    var showVerbSheet by remember { mutableStateOf(false) }
    var sheetVerb by remember { mutableStateOf<String?>(null) }
    var sheetTense by remember { mutableStateOf<String?>(null) }
    var showTenseSheet by remember { mutableStateOf(false) }
    var tenseSheetTense by remember { mutableStateOf<String?>(null) }

    val uiState by viewModel.uiState.collectAsState()

    // Auto-launch voice when voice mode is on and a new card appears
    VoiceAutoLauncher(
        cardIndex = when (val c = provider.currentCard) {
            null -> -1
            else -> (c as? VerbDrillCard)?.let { uiState.session?.cards?.indexOf(it) } ?: -1
        },
        voiceModeEnabled = uiState.voiceModeEnabled,
        isFlipped = provider.hintAnswer != null,
        voiceCompleted = provider.pendingAnswerResult != null,
        isVoiceActive = false,
        onAutoStartVoice = { provider.setInputMode(InputMode.VOICE) }
    )

    // Auto-advance after correct voice answer — no manual "Next" tap needed
    LaunchedEffect(provider.pendingAnswerResult, provider.currentInputMode) {
        val result = provider.pendingAnswerResult
        if (result != null && result.correct && provider.currentInputMode == InputMode.VOICE) {
            delay(500)
            provider.nextCard()
        }
    }

    // Auto-advance after hint shown in voice mode — Play button behavior
    // (When hint is shown via eye or 3 wrong attempts, user presses Play to advance)
    // This is handled by togglePause() calling nextCard() directly

    TrainingCardSession(
        contract = provider,
        hintLevel = hintLevel,
        header = {
            // Custom header: back arrow + "Verb Drill" title (no settings gear)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onExit) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Verb Drill", fontWeight = FontWeight.SemiBold)
            }
        },
        cardContent = {
            val card = currentCard ?: return@TrainingCardSession
            val drillCard = card as? VerbDrillCard
            val verbText = drillCard?.verb
            val verbRank = drillCard?.rank

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                        IconButton(
                            onClick = { contract.speakTts() },
                            enabled = card.promptRu.isNotBlank()
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Listen")
                        }
                    }

                    // Verb + tense hint chips -- hidden on HARD
                    if (!verbText.isNullOrBlank() && hintLevel != com.alexpo.grammermate.data.HintLevel.HARD) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SuggestionChip(
                                onClick = {
                                    sheetVerb = verbText
                                    sheetTense = drillCard.tense
                                    showVerbSheet = true
                                },
                                label = {
                                    Text(
                                        text = if (verbRank != null) "$verbText #$verbRank" else verbText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                icon = {
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            val tenseText = drillCard?.tense
                            if (!tenseText.isNullOrBlank()) {
                                SuggestionChip(
                                    onClick = {
                                        tenseSheetTense = tenseText
                                        showTenseSheet = true
                                    },
                                    label = {
                                        Text(
                                            text = abbreviateTense(tenseText),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        inputControls = {
            DefaultVerbDrillInputControls(
                provider = provider,
                scope = this,
                hintLevel = hintLevel
            )
        },
        completionScreen = {
            VerbDrillCompletionScreen(
                viewModel = viewModel,
                onExit = onExit
            )
        },
        onExit = onExit
    )

    // Verb reference bottom sheet
    if (showVerbSheet && sheetVerb != null) {
        VerbReferenceBottomSheet(
            verb = sheetVerb!!,
            tense = sheetTense,
            viewModel = viewModel,
            onDismiss = {
                showVerbSheet = false
                sheetVerb = null
                sheetTense = null
            }
        )
    }

    // Tense info bottom sheet
    if (showTenseSheet && tenseSheetTense != null) {
        TenseInfoBottomSheet(
            tenseName = tenseSheetTense!!,
            viewModel = viewModel,
            onDismiss = {
                showTenseSheet = false
                tenseSheetTense = null
            }
        )
    }
}

/**
 * Input controls for VerbDrill that mirrors AnswerBox logic exactly.
 * Delegates submit to provider.submitAnswerWithInput for drill-specific retry/hint flow.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DefaultVerbDrillInputControls(
    provider: VerbDrillCardSessionProvider,
    scope: TrainingCardSessionScope,
    hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY
) {
    val contract = scope.contract
    val hasCards = scope.currentCard != null
    val canLaunchVoice = hasCards && contract.sessionActive
    val canSelectInputMode = hasCards && contract.sessionActive
    val clipboardManager = LocalClipboardManager.current
    var showReportSheet by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val reportCard = scope.currentCard
    val reportText = if (reportCard != null) {
        val targetText = reportCard.acceptedAnswers.joinToString(" / ")
        "ID: ${reportCard.id}\nSource: ${reportCard.promptRu}\nTarget: $targetText"
    } else {
        ""
    }

    // Voice recognition launcher — same pattern as AnswerBox
    val latestProvider by rememberUpdatedState(provider)
    val latestOnInputChanged by rememberUpdatedState(scope.onInputChanged)
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                latestOnInputChanged(spoken)
                latestProvider.submitAnswerWithInput(spoken)
                latestOnInputChanged("")
            }
        }
    }

    // Auto-voice LaunchedEffect — mirrors AnswerBox exactly:
    // triggers when voiceTriggerToken changes, inputMode is VOICE, card exists, session is active
    val voiceToken = provider.voiceTriggerToken
    LaunchedEffect(
        scope.currentCard?.id,
        contract.currentInputMode,
        contract.sessionActive,
        voiceToken
    ) {
        if (contract.currentInputMode == InputMode.VOICE &&
            contract.sessionActive &&
            scope.currentCard != null
        ) {
            kotlinx.coroutines.delay(200)
            val languageId = contract.languageId
            val languageTag = when (languageId) {
                "it" -> "it-IT"
                else -> "en-US"
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the translation")
            }
            speechLauncher.launch(intent)
        }
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
            exportResult = { path ->
                exportMessage = if (path != null) "Exported to $path" else "No bad sentences to export"
            }
        )
    }
    if (exportMessage != null) {
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text("Export") },
            text = { Text(exportMessage!!) },
            confirmButton = {
                TextButton(onClick = { exportMessage = null }) {
                    Text("OK")
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hint answer text -- only on EASY
        // Input controls remain visible below this text
        if (provider.hintAnswer != null && hintLevel == com.alexpo.grammermate.data.HintLevel.EASY) {
            HintAnswerCard(
                answerText = provider.hintAnswer ?: "",
                showTtsButton = contract.supportsTts,
                onSpeakTts = { contract.speakTts() }
            )
        }

        // Incorrect feedback — red text with remaining attempts, shown above input
        if (provider.showIncorrectFeedback) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Incorrect",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${provider.remainingAttempts} attempts left",
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        OutlinedTextField(
            value = scope.inputText,
            onValueChange = { newText ->
                if (provider.showIncorrectFeedback) {
                    provider.clearIncorrectFeedback()
                }
                scope.onInputChanged(newText)
                // Auto-submit in keyboard mode when the typed text matches an accepted answer
                if (contract.currentInputMode == InputMode.KEYBOARD &&
                    contract.sessionActive &&
                    scope.currentCard != null &&
                    newText.isNotBlank()
                ) {
                    if (com.alexpo.grammermate.data.Normalizer.isExactMatch(newText, scope.currentCard!!.acceptedAnswers)) {
                        provider.submitAnswerWithInput(newText)
                        scope.onInputChanged("")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Your translation") },
            enabled = hasCards,
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (canLaunchVoice) {
                            contract.setInputMode(InputMode.VOICE)
                        }
                    },
                    enabled = canLaunchVoice
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice input")
                }
            }
        )

        if (!hasCards) {
            Text(
                text = "No cards",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Voice mode hint — same guard as AnswerBox
        if (contract.currentInputMode == InputMode.VOICE && contract.sessionActive) {
            Text(
                text = scope.currentCard?.promptRu?.let { "Say translation: $it" } ?: "",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Word Bank UI -- only on EASY
        if (contract.currentInputMode == InputMode.WORD_BANK && contract.supportsWordBank && hintLevel == com.alexpo.grammermate.data.HintLevel.EASY) {
            VerbDrillWordBankSection(contract = contract)
        }

        // Input mode selector + show answer + flag — mirrors AnswerBox exactly
        VerbDrillInputModeBar(
            contract = contract,
            provider = provider,
            hintLevel = hintLevel,
            hasCards = hasCards,
            canLaunchVoice = canLaunchVoice,
            canSelectInputMode = canSelectInputMode,
            onReport = { showReportSheet = true }
        )

        // Check button — uses provider.submitAnswerWithInput for drill-specific flow
        Button(
            onClick = {
                val input = scope.inputText
                if (input.isNotBlank()) {
                    provider.submitAnswerWithInput(input)
                    scope.onInputChanged("")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasCards &&
                scope.inputText.isNotBlank() &&
                contract.sessionActive &&
                scope.currentCard != null
        ) {
            Text(text = "Check")
        }
    }
}

@Composable
private fun VerbDrillSelectionScreen(
    state: VerbDrillUiState,
    onSelectTense: (String?) -> Unit,
    onSelectGroup: (String?) -> Unit,
    onToggleSortByFrequency: () -> Unit,
    onToggleVoiceMode: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Verb Drill", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (state.availableTenses.isNotEmpty()) {
            VerbDrillDropdown(
                label = "Время",
                allLabel = "Все времена",
                selected = state.selectedTense,
                items = state.availableTenses,
                onSelect = onSelectTense
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.availableGroups.isNotEmpty()) {
            VerbDrillDropdown(
                label = "Группа",
                allLabel = "Все группы",
                selected = state.selectedGroup,
                items = state.availableGroups,
                onSelect = onSelectGroup
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.sortByFrequency,
                onCheckedChange = { onToggleSortByFrequency() }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "По частотности")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Voice input auto-launch toggle — matches VocabDrill style
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
                onCheckedChange = { onToggleVoiceMode() }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.totalCards > 0) {
            Text(
                text = "Прогресс: ${state.everShownCount} / ${state.totalCards}"
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Сегодня: ${state.todayShownCount}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = if (state.totalCards > 0) {
                    state.everShownCount.toFloat() / state.totalCards.toFloat()
                } else 0f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (state.allDoneToday) {
            Text(
                text = "На сегодня всё!",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else if (state.totalCards > 0 || state.availableTenses.isNotEmpty() || state.availableGroups.isNotEmpty()) {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (state.todayShownCount > 0) "Продолжить" else "Старт"
                )
            }
        }
    }
}

@Composable
private fun VerbDrillDropdown(
    label: String,
    allLabel: String,
    selected: String?,
    items: List<String>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(text = "$label:", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = { expanded = true }) {
            Text(text = selected ?: allLabel)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = allLabel) },
                onClick = { expanded = false; onSelect(null) }
            )
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(text = item) },
                    onClick = { expanded = false; onSelect(item) }
                )
            }
        }
    }
}

// --- VerbDrill-specific composable pieces ---

@Composable
private fun VerbDrillCompletionScreen(
    viewModel: VerbDrillViewModel,
    onExit: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val session = state.session ?: return

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
            text = "Отлично!",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Правильных: ${session.correctCount}  |  Ошибок: ${session.incorrectCount}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (!state.allDoneToday) {
            Button(
                onClick = { viewModel.nextBatch() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Ещё")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Выход")
        }
    }
}

// --- Extracted sub-composables for DefaultVerbDrillInputControls ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerbDrillWordBankSection(
    contract: CardSessionContract
) {
    val wordBankWords = contract.getWordBankWords()
    val selectedWords = contract.getSelectedWords()
    if (wordBankWords.isNotEmpty()) {
        Text(
            text = "Tap words in correct order:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            wordBankWords.forEach { word ->
                val availableCount = wordBankWords.count { it == word }
                val usedCount = selectedWords.count { it == word }
                val isFullyUsed = usedCount >= availableCount

                FilterChip(
                    selected = usedCount > 0,
                    onClick = {
                        if (!isFullyUsed) {
                            contract.selectWordFromBank(word)
                        }
                    },
                    label = { Text(text = word) },
                    enabled = !isFullyUsed
                )
            }
        }
        if (selectedWords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected: ${selectedWords.size} / ${wordBankWords.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { contract.removeLastSelectedWord() }) {
                    Text(text = "Undo")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerbDrillInputModeBar(
    contract: CardSessionContract,
    provider: VerbDrillCardSessionProvider,
    hintLevel: com.alexpo.grammermate.data.HintLevel,
    hasCards: Boolean,
    canLaunchVoice: Boolean,
    canSelectInputMode: Boolean,
    onReport: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Voice mode button — ONLY sets input mode, does NOT launch speech
            FilledTonalIconButton(
                onClick = {
                    if (canLaunchVoice) {
                        contract.setInputMode(InputMode.VOICE)
                    }
                },
                enabled = canLaunchVoice
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Voice mode")
            }
            // Keyboard mode button
            FilledTonalIconButton(
                onClick = { contract.setInputMode(InputMode.KEYBOARD) },
                enabled = canSelectInputMode
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = "Keyboard mode")
            }
            // Word bank mode button -- only on EASY
            if (hintLevel == com.alexpo.grammermate.data.HintLevel.EASY) {
                FilledTonalIconButton(
                    onClick = { contract.setInputMode(InputMode.WORD_BANK) },
                    enabled = canSelectInputMode
                ) {
                    Icon(Icons.Default.LibraryBooks, contentDescription = "Word bank mode")
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show answer button -- always visible, disabled when hint already shown
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(text = "Show answer") } },
                state = rememberTooltipState()
            ) {
                IconButton(
                    onClick = { if (hasCards) contract.showAnswer() },
                    enabled = hasCards && provider.hintAnswer == null
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = "Show answer")
                }
            }
            if (contract.supportsFlagging) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(text = "Report sentence") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(
                        onClick = { if (hasCards) onReport() },
                        enabled = hasCards
                    ) {
                        Icon(Icons.Default.ReportProblem, contentDescription = "Report sentence")
                    }
                }
            }
            Text(
                text = when (contract.currentInputMode) {
                    InputMode.VOICE -> "Voice"
                    InputMode.KEYBOARD -> "Keyboard"
                    InputMode.WORD_BANK -> "Word Bank"
                },
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private fun abbreviateTense(tense: String): String {
    val abbreviations = mapOf(
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
        "Congiuntivo Passato" to "Cong. Pass.",
    )
    return abbreviations[tense] ?: tense.take(8)
}