package com.alexpo.grammermate.ui.screens

import com.alexpo.grammermate.ui.TenseInfo
import com.alexpo.grammermate.ui.navigation.Routes
import androidx.activity.compose.BackHandler
import com.alexpo.grammermate.ui.TrainingViewModel
import com.alexpo.grammermate.ui.navigation.isVerbDrillLikeReturn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.AppScreen
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.CompletionNextAction
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.DownloadState
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.GrammarChipStore
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun TrainingScreenContent(
    state: TrainingUiState,
    vm: TrainingViewModel,
    onSubmit: () -> com.alexpo.grammermate.data.SubmitResult = vm::submitAnswer,
    onNext: () -> Unit = vm::navigateNext,
    onPrev: () -> Unit = vm::navigatePrev,
    onShowExitDialog: () -> Unit,
    onShowSettings: () -> Unit,
    onTtsSpeak: () -> Unit,
    hintLevel: HintLevel = HintLevel.EASY,
    onVerbDrillMore: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onSessionDone: () -> Unit = {},
    getTenseInfo: (String) -> TenseInfo? = { null }
) {
    val pomodoroRemainingSeconds by vm.pomodoroRemainingSeconds.collectAsStateWithLifecycle()
    // Typed answer text: collected from the dedicated flow (TASK-091 item 4),
    // NOT from the combined uiState — a keystroke must not recompute the
    // 7-flow combine or recompose the whole NavHost.
    val inputText by vm.inputText.collectAsStateWithLifecycle()

    // Get lesson title for header — suppress during daily practice and verb drill modes
    val lessonTitle = if (state.cardSession.returnTo == Routes.DAILY_PRACTICE) {
        null  // Daily practice: don't show lesson title
    } else if (isVerbDrillLikeReturn(state.cardSession.returnTo)) {
        null  // Verb drill / aux drill: don't show lesson title (drill CSV titles are technical)
    } else {
        state.navigation.lessons
            .firstOrNull { it.id == state.navigation.selectedLessonId }?.title
    }

    // Load grammar chip for current lesson (pack-scoped to avoid wrong-pack data)
    val activePackId = state.navigation.activePackId?.value
    val grammarChip = state.navigation.selectedLessonId?.let { lessonId ->
        GrammarChipStore.getChipForLesson(lessonId.value, packId = activePackId)
    }

    TrainingScreen(
        state = state,
        inputText = inputText,
        onInputChange = vm.training::onInputChanged,
        onSubmit = onSubmit,
        onPrev = onPrev,
        onNext = onNext,
        onTogglePause = vm::togglePause,
        onRequestExit = onShowExitDialog,
        onOpenSettings = { vm.pauseSession() },
        onShowSettings = onShowSettings,
        onSelectLesson = vm::selectLesson,
        onSelectMode = vm::selectMode,
        onSetInputMode = vm.training::setInputMode,
        onShowAnswer = vm::showAnswer,
        onVoicePromptStarted = vm.training::onVoicePromptStarted,
        onSelectWordFromBank = vm.training::selectWordFromBank,
        onRemoveLastWord = vm.training::removeLastSelectedWord,
        onTtsSpeak = onTtsSpeak,
        onFlagBadSentence = vm::flagBadSentence,
        onUnflagBadSentence = vm.reports::unflagBadSentence,
        onHideCard = vm::hideCurrentCard,
        onExportBadSentences = vm.reports::exportBadSentences,
        isBadSentence = vm.reports::isBadSentence,
        onClearBadSentences = vm::clearPackBadSentences,
        badSentenceCount = state.cardSession.badSentenceCount,
        onStartOfflineRecognition = vm::startOfflineRecognition,
        hintLevel = hintLevel,
        onPausePomodoro = vm::pausePomodoro,
        onResumePomodoro = vm::resumePomodoro,
        onCancelPomodoro = remember(onNavigate) {
            vm::cancelPomodoro
        },
        onRateCardDifficulty = vm::rateCardDifficulty,
        onVerbDrillMore = onVerbDrillMore,
        onSessionDone = onSessionDone,
        getTenseInfo = getTenseInfo,
        pomodoroRemainingSeconds = pomodoroRemainingSeconds,
        clickableWordHints = state.navigation.clickableWordHints,
        baseDir = LocalContext.current.filesDir,
        grammarChip = grammarChip,
        lessonTitle = lessonTitle,
        onBluetoothSetup = vm.audio::startBluetoothMicIfNeeded,
        onBluetoothCleanup = vm.audio::stopBluetoothMicIfNeeded
    )
}

// ── Daily Practice screen content ────────────────────────────────────────────

