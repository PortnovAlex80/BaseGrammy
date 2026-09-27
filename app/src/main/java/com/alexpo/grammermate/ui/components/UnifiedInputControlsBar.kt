package com.alexpo.grammermate.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.CardSessionContract
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.feature.training.HintCalculator
import com.alexpo.grammermate.ui.features.training.components.TrainingAnswerPanel

/**
 * Unified input controls bar for all card session modes (Training, VerbDrill, DailyPractice).
 *
 * Integration boundary adapter (P3b): the pure display moved to the feature organism
 * [TrainingAnswerPanel]; this adapter keeps every effectful or contract-reading concern
 * the display must not own (inventory effect ledger rows 1/2, 5, 12, 13, 25, 26):
 * - both voice recognizer launchers and their click flows (rows 1–2, integrator
 *   decision D-A — the AnswerBox 200ms auto-launch on the same screen is a separate
 *   launcher with different semantics and is NOT unified with this one);
 * - the pause-resume-first interplay on voice entry (row 26, byte-identical order:
 *   resume → set VOICE mode → manual launch only when auto-start is off);
 * - [WordInfoCache] remember + `LaunchedEffect` load + the `getWordsInfo` read
 *   (row 13, decision D-B — the organism receives the resulting map as plain data);
 * - auto-submit on exact match + incorrect-feedback clearing, fused into the
 *   `onInputChanged` handler below (row 12, decision D-D — the dedicated
 *   `vm.inputText` flow upstream keeps its single collector and per-keystroke cadence);
 * - Bluetooth setup/cleanup callbacks (row 5, decision D-C — [onBluetoothCleanup]
 *   stays threaded-but-unused exactly as before; pre-existing defect, separate work item);
 * - the [InputMode] enum reading (mode-conditional visibility + label resolution).
 *
 * Design Principle DP-01: All card-based drill modes MUST use identical
 * input controls for shared elements.
 *
 * Signature note: [reportCard] was already unused before P3b (the report sheet lives
 * in the AnswerBox adapter) and [onBluetoothCleanup] is the D-C defect — both are kept
 * so the AnswerBox and dead-file call sites stay untouched.
 *
 * @param contract          The card session contract providing state and actions.
 * @param inputText         Current text in the input field.
 * @param onInputChanged    Callback when input text changes.
 * @param onSubmit          Callback to submit the current answer.
 * @param hasCards          Whether there is a current card available.
 * @param hintAnswer        Currently shown hint answer (null if no hint visible).
 * @param showIncorrectFeedback Whether incorrect feedback is shown above input.
 * @param incorrectMessage  Optional text to display when answer is incorrect (e.g. "3 attempts left").
 * @param onClearIncorrectFeedback Callback to dismiss incorrect feedback.
 * @param onShowReport      Callback to open the report sheet.
 * @param onReportCard      The card to report, or null.
 * @param clickableWordHints Whether to enable clickable word hints (default: false).
 * @param baseDir           Base directory for drill data loading (required for clickable hints).
 */
@Composable
fun UnifiedInputControlsBar(
    contract: CardSessionContract,
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    hasCards: Boolean,
    hintAnswer: String? = null,
    showIncorrectFeedback: Boolean = false,
    incorrectMessage: String? = null,
    onClearIncorrectFeedback: () -> Unit = {},
    onShowReport: () -> Unit = {},
    reportCard: com.alexpo.grammermate.data.SessionCard? = null,
    hintLevel: HintLevel = HintLevel.EASY,
    clickableWordHints: Boolean = false,
    baseDir: java.io.File? = null,
    onBluetoothSetup: suspend () -> Boolean = { true },
    onBluetoothCleanup: () -> Unit = {}
) {
    val canLaunchVoice = hasCards && contract.canSubmit
    val canSelectInputMode = hasCards && contract.canSubmit
    val scope = rememberCoroutineScope()

    // Word info cache for clickable hints (only when feature is enabled)
    val wordInfoCache = remember(clickableWordHints, baseDir) {
        if (clickableWordHints && baseDir != null) {
            WordInfoCache.getInstance(baseDir)
        } else {
            null
        }
    }

    // Load drill data when cache is created
    LaunchedEffect(wordInfoCache) {
        wordInfoCache?.loadDrillData()
    }

    // Voice recognition launcher
    val latestContract by rememberUpdatedState(contract)
    val latestOnInputChanged by rememberUpdatedState(onInputChanged)
    val latestOnSubmit by rememberUpdatedState(onSubmit)
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                latestContract.onVoiceInputResult(spoken)
            }
        }
    }

    // Helper to launch voice recognition with correct language tag
    val launchVoice: () -> Unit = {
        val languageId = contract.languageId
        val languageTag = when (languageId) {
            "it" -> "it-IT"
            "el" -> "el-GR"
            "ru" -> "ru-RU"
            else -> "en-US"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the translation")
        }
        scope.launch { onBluetoothSetup() }
        speechLauncher.launch(intent)
    }

    // Clickable-hint data: the cache read (which can lazily trigger file I/O) stays at
    // this boundary (D-B); the organism renders the map it receives. Same call condition
    // as before extraction: only when a hint is visible AND clickable hints are active.
    val wordsInfo = if (hintAnswer != null && clickableWordHints && wordInfoCache != null) {
        wordInfoCache.getWordsInfo(hintAnswer)
    } else {
        null
    }

    // Voice-mode hint line: business hint calculation + string resolution stay here;
    // null hides the line, "" renders the empty line (parity with the pre-extraction bar).
    val voiceModeHint = if (contract.currentInputMode == InputMode.VOICE && contract.canSubmit) {
        contract.currentCard?.promptRu?.let {
            val cleanPrompt = HintCalculator.calculateEffectiveHints(
                promptRu = it,
                encounterCount = 0,
                hintLevel = hintLevel,
                sessionOffset = 0
            )
            stringResource(R.string.voice_say_translation_hint, cleanPrompt)
        } ?: ""
    } else {
        null
    }

    val wordBankVisible = contract.currentInputMode == InputMode.WORD_BANK &&
        contract.supportsWordBank && hintLevel == HintLevel.EASY
    val wordBankModeButtonVisible = contract.supportsWordBank && hintLevel == HintLevel.EASY

    val modeLabel = when (contract.currentInputMode) {
        InputMode.VOICE -> stringResource(R.string.input_mode_voice)
        InputMode.KEYBOARD -> stringResource(R.string.input_mode_keyboard)
        InputMode.WORD_BANK -> stringResource(R.string.input_mode_word_bank)
    }

    TrainingAnswerPanel(
        inputText = inputText,
        onInputChanged = { newText ->
            if (showIncorrectFeedback) onClearIncorrectFeedback()
            onInputChanged(newText)
            // Auto-submit when typed text exactly matches an accepted answer
            if (contract.currentInputMode != InputMode.WORD_BANK &&
                contract.canSubmit &&
                hasCards &&
                contract.currentCard != null &&
                newText.isNotBlank()
            ) {
                if (com.alexpo.grammermate.data.Normalizer.isExactMatch(
                        newText,
                        contract.currentCard!!.acceptedAnswers
                    )
                ) {
                    onSubmit()
                }
            }
        },
        hasCards = hasCards,
        canSubmit = contract.canSubmit,
        canLaunchVoice = canLaunchVoice,
        canSelectInputMode = canSelectInputMode,
        supportsVoiceInput = contract.supportsVoiceInput,
        supportsFlagging = contract.supportsFlagging,
        showIncorrectFeedback = showIncorrectFeedback,
        incorrectMessage = incorrectMessage,
        hintAnswer = hintAnswer,
        wordsInfo = wordsInfo,
        voiceModeHint = voiceModeHint,
        wordBankVisible = wordBankVisible,
        wordBankWords = contract.getWordBankWords(),
        selectedWords = contract.getSelectedWords(),
        onSelectWord = { contract.selectWordFromBank(it) },
        onRemoveLastWord = { contract.removeLastSelectedWord() },
        onMicClick = {
            if (canLaunchVoice) {
                if (!contract.isActive) {
                    contract.togglePause()
                }
                contract.setInputMode(InputMode.VOICE)
                if (!contract.voiceAutoStart) {
                    launchVoice()
                }
            }
        },
        onVoiceModeClick = {
            if (canLaunchVoice) {
                if (!contract.isActive) {
                    contract.togglePause()
                }
                contract.setInputMode(InputMode.VOICE)
                if (!contract.voiceAutoStart) {
                    launchVoice()
                }
            }
        },
        onKeyboardModeClick = { contract.setInputMode(InputMode.KEYBOARD) },
        wordBankModeButtonVisible = wordBankModeButtonVisible,
        onWordBankModeClick = { contract.setInputMode(InputMode.WORD_BANK) },
        onShowAnswerClick = { if (hasCards) contract.showAnswer() },
        onShowReportClick = { if (hasCards) onShowReport() },
        modeLabel = modeLabel,
        onSubmitClick = { onSubmit() }
    )
}
