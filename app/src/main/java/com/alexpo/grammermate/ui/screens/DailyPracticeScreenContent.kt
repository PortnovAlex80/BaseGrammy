package com.alexpo.grammermate.ui.screens

import android.util.Log
import com.alexpo.grammermate.ui.navigation.Routes
import com.alexpo.grammermate.ui.DailyPracticeScreen
import androidx.activity.compose.BackHandler
import com.alexpo.grammermate.ui.TrainingViewModel
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
internal fun DailyPracticeScreenContent(
    state: TrainingUiState,
    vm: TrainingViewModel,
    onNavigate: (String) -> Unit,
    onNavigateResetStack: (String) -> Unit
) {
    val dailyState = state.daily.dailySession
    val currentBlock = vm.daily.getCurrentBlock()
    val dailyTask = vm.daily.getDailyCurrentTask()
    val dailyProgress = vm.daily.getDailyBlockProgress()
    Log.d("GrammarMate", "DailyPractice: DailyPracticeScreenContent active=${dailyState.active}, finishedToken=${dailyState.finishedToken}, blockType=${currentBlock?.type}, blockIndex=${dailyState.blockIndex}")
    DailyPracticeScreen(
        state = dailyState,
        blockProgress = dailyProgress,
        currentBlock = currentBlock,
        currentTask = dailyTask,
        languageId = state.navigation.selectedLanguageId?.value ?: "en",
        onShowSentenceAnswer = vm.daily::getDailySentenceAnswer,
        onShowVerbAnswer = vm.daily::getDailyVerbAnswer,
        onRateVocabCard = remember { { rating: com.alexpo.grammermate.data.SrsRating -> vm.daily.rateVocabCard(rating) } },
        onStartCardBlock = remember(onNavigate) { { blockType: DailyBlockType, cards: List<com.alexpo.grammermate.data.SessionCard> ->
            Log.d("GrammarMate", "DailyPractice: startCardBlock type=$blockType, cards=${cards.size}, returnTo=DAILY_PRACTICE")
            when (blockType) {
                DailyBlockType.TRANSLATE -> vm.startDailyTranslateSession(cards)
                DailyBlockType.VERBS -> vm.startDailyVerbsSession(cards)
                else -> {}
            }
            vm.setReturnTo(Routes.DAILY_PRACTICE)
            onNavigate(Routes.TRAINING)
        } },
        onSpeak = remember(state.audio.ttsModelReady) { { text: String ->
            if (state.audio.ttsModelReady) {
                vm.audio.onTtsSpeak(text)
            }
        } },
        onStopTts = remember { { vm.audio.stopTts() } },
        ttsState = state.audio.ttsState,
        onExit = remember {
            {
                Log.d("GrammarMate", "DailyPractice: session exit, navigating HOME")
                vm.cancelDailySession()
                onNavigateResetStack(Routes.HOME)
            }
        },
        onComplete = remember { {
            // VOCAB block completed — signal coordinator to advance to next block
            val nextBlock = vm.daily.onBlockComplete()
            if (nextBlock == null) {
                // All blocks done — coordinator already called endSession()
                // The finishedToken will be set and DailyPracticeScreen shows completion
                Log.d("GrammarMate", "DailyPractice: session finished, all blocks done")
            }
            // If nextBlock is non-null, the UI will re-render with the new block
        } },
        onFlagDailyBadSentence = remember { { cardId: String, langId: String, sentence: String, translation: String, mode: String ->
            vm.reports.flagDailyBadSentence(cardId, langId, sentence, translation, mode)
        } },
        onUnflagDailyBadSentence = remember { { cardId: String ->
            vm.reports.unflagDailyBadSentence(cardId)
        } },
        isDailyBadSentence = remember { { cardId: String ->
            vm.reports.isDailyBadSentence(cardId)
        } },
        onExportDailyBadSentences = remember { {
            vm.reports.exportDailyBadSentences()
        } },
        hintLevel = state.cardSession.hintLevel,
        textScale = state.audio.ruTextScale,
        voiceAutoStart = state.audio.voiceAutoStart,
        onBluetoothSetup = vm.audio::startBluetoothMicIfNeeded,
        onBluetoothCleanup = vm.audio::stopBluetoothMicIfNeeded,
        onClearBadSentences = vm::clearPackBadSentences,
        badSentenceCount = state.cardSession.badSentenceCount
    )
}

// ── Dialogs ──────────────────────────────────────────────────────────────────

