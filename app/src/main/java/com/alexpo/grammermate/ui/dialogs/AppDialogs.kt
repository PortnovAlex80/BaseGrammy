package com.alexpo.grammermate.ui.dialogs

import androidx.activity.compose.BackHandler
import com.alexpo.grammermate.ui.components.AsrMeteredNetworkDialog
import com.alexpo.grammermate.ui.components.MeteredNetworkDialog
import com.alexpo.grammermate.ui.components.TtsDownloadDialog
import com.alexpo.grammermate.ui.components.ProfileStatsPopup
import com.alexpo.grammermate.ui.TrainingViewModel
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.shared.ScreenLogger
import com.alexpo.grammermate.ui.navigation.Routes
import android.util.Log
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

internal data class DialogState(
    val showSettings: Boolean = false,
    val showExitDialog: Boolean = false,
    val showWelcomeDialog: Boolean = false,
    val showDailyResumeDialog: Boolean = false,
    val showTtsDownloadDialog: Boolean = false,
    val showProfileStats: Boolean = false,
    val pendingDailyLevel: Int = 0,
    val isLoadingDaily: Boolean = false
)

@Composable
internal fun NavDialogs(
    dialogs: DialogState,
    state: TrainingUiState,
    currentRoute: String?,
    vm: TrainingViewModel,
    dailyScope: kotlinx.coroutines.CoroutineScope,
    lastFinishedToken: androidx.compose.runtime.MutableState<Int>,
    completionNextAction: androidx.compose.runtime.MutableState<CompletionNextAction>,
    onDialogsChange: (DialogState) -> Unit,
    onNavigate: (String) -> Unit,
    onNavigateResetStack: (String) -> Unit,
    onNavigatePopTo: (String) -> Unit
) {
    // Welcome dialog trigger — only on HOME screen, only if attempts < 3.
    // Guard: languages must be non-empty to ensure init block has loaded the real
    // profile from disk. Without this, the combined uiState flow may still carry
    // the default TrainingUiState (userName="GrammarMateUser", attempts=0) on the
    // first composition frame, causing a false-positive dialog trigger that
    // overwrites the name the user previously saved.
    LaunchedEffect(currentRoute, state.navigation.userName, state.navigation.languages.size) {
        if (currentRoute == Routes.HOME
            && state.navigation.userName == "GrammarMateUser"
            && state.navigation.welcomeDialogAttempts < 3
            && state.navigation.languages.isNotEmpty()
        ) {
            onDialogsChange(dialogs.copy(showWelcomeDialog = true))
        }
    }

    // Welcome dialog
    if (dialogs.showWelcomeDialog) {
        WelcomeDialog(
            onNameSet = { name ->
                if (name == "GrammarMateUser") {
                    vm.settings.incrementWelcomeDialogAttempts()
                } else {
                    vm.settings.updateUserName(name)
                }
                onDialogsChange(dialogs.copy(showWelcomeDialog = false))
            }
        )
    }

    // Profile stats popup
    if (dialogs.showProfileStats) {
        // VM state (Phase 1) — refreshed on pack/language changes; the old
        // unkeyed remember froze the first-ever snapshot forever.
        val profileStats by vm.profileStats.collectAsStateWithLifecycle()
        ProfileStatsPopup(
            userName = state.navigation.userName,
            cardsCompleted = profileStats.cardsCompleted,
            wordsLearned = profileStats.wordsLearned,
            cefrLevel = profileStats.cefrLevel,
            onDismiss = { onDialogsChange(dialogs.copy(showProfileStats = false)) }
        )
    }

    // TTS download dialog
    if (dialogs.showTtsDownloadDialog) {
        // Effect, not composition: level-triggered on dialog-open + download
        // Done — runs on key change AND on first composition with the keys
        // already set (dialog opened after Done), then starts playback once.
        LaunchedEffect(dialogs.showTtsDownloadDialog, state.audio.ttsDownloadState) {
            if (dialogs.showTtsDownloadDialog && state.audio.ttsDownloadState is DownloadState.Done) {
                onDialogsChange(dialogs.copy(showTtsDownloadDialog = false))
                vm.audio.dismissTtsDownloadDialog()
                val text = state.cardSession.answerText ?: state.cardSession.currentCard?.acceptedAnswers?.firstOrNull()
                if (text != null) vm.audio.onTtsSpeak(text)
            }
        }
        if (dialogs.showTtsDownloadDialog) {
            TtsDownloadDialog(
                downloadState = state.audio.ttsDownloadState,
                languageId = state.navigation.selectedLanguageId?.value ?: "en",
                onConfirm = { vm.audio.startTtsDownload() },
                onDismiss = {
                    vm.audio.dismissTtsDownloadDialog()
                    onDialogsChange(dialogs.copy(showTtsDownloadDialog = false))
                }
            )
        }
    }

    // Metered network warnings
    if (state.audio.ttsMeteredNetwork) {
        MeteredNetworkDialog(
            onConfirm = { vm.audio.confirmTtsDownloadOnMetered() },
            onDismiss = {
                vm.audio.dismissMeteredWarning()
                vm.audio.dismissTtsDownloadDialog()
                onDialogsChange(dialogs.copy(showTtsDownloadDialog = false))
            }
        )
    }
    if (state.audio.asrMeteredNetwork) {
        AsrMeteredNetworkDialog(
            onConfirm = { vm.audio.confirmAsrDownloadOnMetered() },
            onDismiss = { vm.audio.dismissAsrMeteredWarning(); vm.audio.dismissAsrDownloadDialog() }
        )
    }

    // Token-based navigation, as an EFFECT keyed on the token (Phase 2, items
    // 2.1/2.2): the key IS the edge detector (each distinct token fires once,
    // resets resync the baseline); all decisions live in the ViewModel and
    // travel to the UI only as one-shot NavigationEvents.
    LaunchedEffect(state.cardSession.subLessonFinishedToken, currentRoute) {
        val token = state.cardSession.subLessonFinishedToken
        if (token < lastFinishedToken.value) {
            // Session reset (token dropped) — resync the remembered baseline.
            lastFinishedToken.value = token
            return@LaunchedEffect
        }
        if (token == lastFinishedToken.value) return@LaunchedEffect
        lastFinishedToken.value = token
        if (currentRoute != Routes.TRAINING) return@LaunchedEffect
        vm.handleSubLessonFinished()
    }

    // Sub-lesson completion dialog
    if (completionNextAction.value != CompletionNextAction.NONE) {
        val correctCount = state.cardSession.correctCount
        val incorrectCount = state.cardSession.incorrectCount
        val total = correctCount + incorrectCount
        val rate = if (total > 0) correctCount * 100 / total else 0

        val continueLabel = when (completionNextAction.value) {
            CompletionNextAction.NEXT_SUB_LESSON -> "Следующее упражнение"
            CompletionNextAction.NEXT_LESSON -> "Следующий урок"
            CompletionNextAction.NONE -> ""
        }

        val returnTo = state.cardSession.returnTo

        AlertDialog(
            onDismissRequest = {
                completionNextAction.value = CompletionNextAction.NONE
                // Exit: navigate back to lesson list
                val hasChapters = state.navigation.activePackHasChapters
                if (returnTo == Routes.CHAPTER_LESSONS || hasChapters) {
                    onNavigatePopTo(Routes.CHAPTER_LESSONS)
                } else {
                    onNavigatePopTo(Routes.LESSON)
                }
            },
            title = {
                Text(
                    text = "Упражнение завершено! 🎉",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "$correctCount правильно / $incorrectCount ошибок ($rate%)",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    if (completionNextAction.value == CompletionNextAction.NEXT_LESSON) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Урок пройден! Доступен следующий.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val action = completionNextAction.value
                    completionNextAction.value = CompletionNextAction.NONE
                    ScreenLogger.tap("completion_continue", "action=$action")
                    AuditLogger.getInstanceOrNull()?.dialogClose("completion", "continue_$action")
                    when (action) {
                        CompletionNextAction.NEXT_SUB_LESSON -> {
                            // Start next sub-lesson in current lesson
                            val nextIdx = state.cardSession.activeSubLessonIndex + 1
                            vm.selectSubLesson(nextIdx)
                            // Stay on TRAINING — cards will be rebuilt by ViewModel
                        }
                        CompletionNextAction.NEXT_LESSON -> {
                            // Find and start next lesson in chapter/pack
                            val activePackId = state.navigation.activePackId?.value
                            val hasChapters = state.navigation.activePackHasChapters
                            val currentLessonId = state.navigation.selectedLessonId

                            if (hasChapters) {
                                val chapter = state.navigation.selectedChapter
                                if (chapter != null) {
                                    val currentIdx = chapter.lessons.indexOf(currentLessonId?.value)
                                    val nextLessonId = chapter.lessons.getOrNull(currentIdx + 1)
                                    if (nextLessonId != null) {
                                        vm.selectLesson(nextLessonId, activePackId)
                                        // Rebuild session for new lesson and stay on TRAINING
                                        vm.selectSubLesson(0)
                                    }
                                }
                            } else {
                                val lessons = state.navigation.lessons
                                val currentIdx = lessons.indexOfFirst { it.id == currentLessonId }
                                val nextLesson = lessons.getOrNull(currentIdx + 1)
                                if (nextLesson != null) {
                                    vm.selectLesson(nextLesson.id.value, activePackId)
                                    vm.selectSubLesson(0)
                                }
                            }
                            // Stay on TRAINING — cards will be rebuilt
                        }
                        CompletionNextAction.NONE -> { /* no-op */ }
                    }
                }) {
                    Text(continueLabel)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    completionNextAction.value = CompletionNextAction.NONE
                    ScreenLogger.tap("completion_exit")
                    AuditLogger.getInstanceOrNull()?.dialogClose("completion", "exit")
                    // Navigate back to lesson list
                    val hasChapters = state.navigation.activePackHasChapters
                    if (returnTo == Routes.CHAPTER_LESSONS || hasChapters) {
                        onNavigatePopTo(Routes.CHAPTER_LESSONS)
                    } else {
                        onNavigatePopTo(Routes.LESSON)
                    }
                }) {
                    Text("Выход")
                }
            }
        )
    }

    // Boss-finished navigation now arrives as a NavigationEvent (see the
    // single collector in GrammarMateApp) — no UI-side token bookkeeping.

    // Daily practice loading overlay
    if (dialogs.isLoadingDaily) {
        DailyLoadingOverlay()
    }

    // Exit confirmation dialog
    if (dialogs.showExitDialog) {
        ExitConfirmDialog(
            currentRoute = currentRoute,
            state = state,
            vm = vm,
            onDismiss = { onDialogsChange(dialogs.copy(showExitDialog = false)) },
            onNavigate = onNavigate,
            onNavigateResetStack = onNavigateResetStack,
            onNavigatePopTo = onNavigatePopTo
        )
    }

    // Daily resume dialog
    if (dialogs.showDailyResumeDialog) {
        DailyResumeDialog(
            pendingDailyLevel = dialogs.pendingDailyLevel,
            vm = vm,
            dailyScope = dailyScope,
            onDialogsChange = { onDialogsChange(dialogs.copy(showDailyResumeDialog = false, isLoadingDaily = it.isLoadingDaily)) },
            onNavigate = onNavigate
        )
    }

    // Story error dialog
    if (state.story.storyErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { vm.story.clearStoryError() },
            confirmButton = {
                TextButton(onClick = { vm.story.clearStoryError() }) {
                    Text(text = stringResource(R.string.dialog_ok))
                }
            },
            title = { Text(text = stringResource(R.string.dialog_story_title)) },
            text = { Text(text = state.story.storyErrorMessage ?: "") }
        )
    }

    // Boss error dialog
    if (state.boss.bossErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { vm.boss.clearBossError() },
            confirmButton = {
                TextButton(onClick = { vm.boss.clearBossError() }) {
                    Text(text = stringResource(R.string.dialog_ok))
                }
            },
            title = { Text(text = stringResource(R.string.dialog_boss_title)) },
            text = { Text(text = state.boss.bossErrorMessage ?: "") }
        )
    }

    // Boss reward dialog
    if (state.boss.bossRewardMessage != null && state.boss.bossReward != null) {
        AlertDialog(
            onDismissRequest = { vm.clearBossRewardMessage() },
            confirmButton = {
                TextButton(onClick = { vm.clearBossRewardMessage() }) {
                    Text(text = stringResource(R.string.dialog_ok))
                }
            },
            icon = {
                val tint = when (state.boss.bossReward) {
                    BossReward.BRONZE -> Color(0xFFCD7F32)
                    BossReward.SILVER -> Color(0xFFC0C0C0)
                    BossReward.GOLD -> Color(0xFFFFD700)
                    else -> MaterialTheme.colorScheme.primary
                }
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = tint
                )
            },
            title = { Text(text = stringResource(R.string.dialog_boss_reward_title)) },
            text = { Text(text = state.boss.bossRewardMessage ?: "") }
        )
    }

    // Streak celebration dialog
    if (state.cardSession.streakMessage != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissStreakMessage() },
            confirmButton = {
                TextButton(onClick = { vm.dismissStreakMessage() }) {
                    Text(text = stringResource(R.string.dialog_streak_continue))
                }
            },
            icon = {
                Text(text = "??", fontSize = 48.sp)
            },
            title = { Text(text = stringResource(R.string.dialog_streak_title)) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = state.cardSession.streakMessage ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    if (state.cardSession.longestStreak > state.cardSession.currentStreak) {
                        Text(
                            text = stringResource(R.string.dialog_streak_longest, state.cardSession.longestStreak),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        )
    }
}

// ── Individual dialog composables ────────────────────────────────────────────

@Composable
internal fun DailyLoadingOverlay() {
    androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.dialog_daily_loading),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
internal fun ExitConfirmDialog(
    currentRoute: String?,
    state: TrainingUiState,
    vm: TrainingViewModel,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onNavigateResetStack: (String) -> Unit = onNavigate,
    onNavigatePopTo: (String) -> Unit = onNavigate
) {
    val isPomodoroActive = state.pomodoro.isActive
    val dialogTitle = when {
        isPomodoroActive -> stringResource(R.string.dialog_exit_title_pomodoro)
        currentRoute == Routes.DAILY_PRACTICE -> stringResource(R.string.dialog_exit_title_daily)
        else -> stringResource(R.string.dialog_exit_title_training)
    }
    val dialogText = when {
        isPomodoroActive -> stringResource(R.string.dialog_exit_text_pomodoro)
        currentRoute == Routes.DAILY_PRACTICE -> stringResource(R.string.dialog_exit_text_daily)
        else -> stringResource(R.string.dialog_exit_text_training)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                AuditLogger.getInstanceOrNull()?.dialogAction("exit_confirm", "yes")
                if (isPomodoroActive) {
                    vm.confirmPomodoroExit()
                    onNavigateResetStack(Routes.HOME)
                    return@TextButton
                }
                if (currentRoute == Routes.DAILY_PRACTICE) {
                    vm.cancelDailySession()
                    onNavigateResetStack(Routes.HOME)
                    return@TextButton
                }
                if (state.boss.bossActive) {
                    // finishBoss() emits BossSessionFinished through the
                    // navigation-event channel — the single navigation owner.
                    vm.finishBoss()
                    return@TextButton
                }
                vm.finishSession()
                Log.d("NavDebug", "EXIT_DIALOG: confirm exit → LESSON")
                onNavigatePopTo(Routes.LESSON)
            }) {
                Text(text = stringResource(R.string.dialog_exit_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                AuditLogger.getInstanceOrNull()?.dialogAction("exit_confirm", "no")
                onDismiss()
            }) {
                Text(text = stringResource(R.string.dialog_cancel))
            }
        },
        title = { Text(text = dialogTitle) },
        text = { Text(text = dialogText) }
    )
}

@Composable
internal fun DailyResumeDialog(
    pendingDailyLevel: Int,
    vm: TrainingViewModel,
    dailyScope: kotlinx.coroutines.CoroutineScope,
    onDialogsChange: (DialogState) -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { onDialogsChange(DialogState()) },
        confirmButton = {
            TextButton(onClick = {
                onDialogsChange(DialogState(isLoadingDaily = true))
                dailyScope.launch {
                    try {
                        val started = withContext(Dispatchers.IO) {
                            vm.startDailyPractice(pendingDailyLevel)
                        }
                        onDialogsChange(DialogState())
                        if (started) onNavigate(Routes.DAILY_PRACTICE)
                    } catch (e: Exception) {
                        onDialogsChange(DialogState())
                        Toast.makeText(context, context.getString(R.string.dialog_daily_loading), Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Text(text = stringResource(R.string.dialog_daily_resume_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDialogsChange(DialogState(isLoadingDaily = true))
                dailyScope.launch {
                    try {
                        val started = withContext(Dispatchers.IO) {
                            vm.repeatDailyPractice(pendingDailyLevel)
                        }
                        onDialogsChange(DialogState())
                        if (started) onNavigate(Routes.DAILY_PRACTICE)
                    } catch (e: Exception) {
                        onDialogsChange(DialogState())
                        Toast.makeText(context, context.getString(R.string.dialog_daily_loading), Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Text(text = stringResource(R.string.dialog_daily_resume_repeat))
            }
        },
        title = { Text(text = stringResource(R.string.dialog_daily_resume_title)) },
        text = { Text(text = stringResource(R.string.dialog_daily_resume_text)) }
    )
}

// ── Enum & helpers ───────────────────────────────────────────────────────────

@Composable
internal fun WelcomeDialog(
    onNameSet: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                text = stringResource(R.string.dialog_welcome_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_welcome_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(50) },
                    label = { Text(stringResource(R.string.dialog_welcome_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_welcome_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onNameSet(if (nameInput.isBlank()) "GrammarMateUser" else nameInput.trim())
                        }
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onNameSet(if (nameInput.isBlank()) "GrammarMateUser" else nameInput.trim())
                }
            ) {
                Text(stringResource(R.string.dialog_welcome_continue))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onNameSet("GrammarMateUser")
                }
            ) {
                Text(stringResource(R.string.dialog_welcome_skip))
            }
        }
    )
}
