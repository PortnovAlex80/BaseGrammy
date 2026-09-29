package com.alexpo.grammermate.ui.screens

import androidx.compose.ui.draw.alpha
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.alexpo.grammermate.R
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.shared.ScreenLogger
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.PomodoroHistoryEntry
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.ui.components.PomodoroSelectorSheet
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.alexpo.grammermate.ui.PackTileUi

@Composable
fun HomeScreen(
    state: TrainingUiState,
    onSelectLanguage: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onPrimaryAction: () -> Unit,
    onSelectLesson: (String) -> Unit,
    onDailyPractice: () -> Unit,
    hasVerbDrill: Boolean = false,
    hasVocabDrill: Boolean = false,
    onOpenVerbDrill: () -> Unit = {},
    onOpenAuxDrill: () -> Unit = {},
    onOpenVocabDrill: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onStartPomodoro: (Int) -> Unit = {},
    pomodoroLastDuration: Int = 20,
    pomodoroHistory: List<PomodoroHistoryEntry> = emptyList(),
    onSelectPack: (String) -> Unit = {},
    packTiles: List<PackTileUi> = emptyList(),
    onBackgroundVocab: () -> Unit = {}
) {
    val tiles = remember(state.navigation.selectedLanguageId, state.navigation.lessons, state.cardSession.testMode, state.flowerDisplay.lessonFlowers, state.navigation.selectedLessonId, state.navigation.activePackId, state.navigation.activePackLessonIds) {
        buildLessonTiles(state.navigation.lessons, state.cardSession.testMode, state.flowerDisplay.lessonFlowers, state.navigation.selectedLessonId?.value, state.navigation.activePackLessonIds)
    }
    var showMethod by remember { mutableStateOf(false) }
    var showLockedLessonHint by remember { mutableStateOf(false) }
    var earlyStartLessonId by remember { mutableStateOf<String?>(null) }
    var showPomodoroSheet by remember { mutableStateOf(false) }
    val languageCode = state.navigation.languages
        .firstOrNull { it.id == state.navigation.selectedLanguageId }
        ?.id?.value
        ?.uppercase()
        ?: "--"
    val isFirstLaunch = state.cardSession.correctCount == 0 &&
        state.cardSession.incorrectCount == 0 &&
        state.cardSession.activeTimeMs == 0L
    val activePackDisplayName = state.navigation.installedPacks
        .firstOrNull { it.packId == state.navigation.activePackId }
        ?.displayName
    val continueLearningText = stringResource(R.string.home_continue_learning)
    val startLearningText = stringResource(R.string.home_start_learning)
    val hasActivePack = state.navigation.activePackId != null
    val primaryLabel = when {
        !hasActivePack -> stringResource(R.string.home_choose_pack)
        state.cardSession.sessionState == SessionState.ACTIVE -> activePackDisplayName ?: continueLearningText
        isFirstLaunch -> activePackDisplayName ?: startLearningText
        else -> activePackDisplayName ?: startLearningText
    }
    // Calculate the actual current lesson (first incomplete or first with most recent activity)
    val currentLessonIndex = state.navigation.lessons.indexOfFirst { it.id == state.navigation.selectedLessonId }
        .takeIf { it >= 0 }
        ?: 0

    // Get actual sub-lesson progress for the current lesson
    val currentLessonProgress = if (state.navigation.lessons.isNotEmpty() && state.navigation.selectedLessonId != null) {
        val currentLesson = state.navigation.lessons.getOrNull(currentLessonIndex)
        if (currentLesson != null && currentLesson.id == state.navigation.selectedLessonId) {
            "${state.cardSession.completedSubLessonCount}/${state.cardSession.subLessonCount}"
        } else {
            "1/10"  // Default if lesson not loaded yet
        }
    } else {
        "1/10"
    }

    val lessonExerciseFormat = stringResource(R.string.format_lesson_exercise)
    val nextHint = if (state.navigation.lessons.isNotEmpty()) {
        lessonExerciseFormat.format(currentLessonIndex + 1, currentLessonProgress)
    } else {
        null
    }

    // Zero state: no language and no pack selected yet — show full-screen language picker
    val isZeroState = state.navigation.activePackId == null &&
        state.navigation.selectedLanguageId == null

    if (isZeroState) {
        ZeroStateLanguageSelector(
            languages = state.navigation.languages,
            onLanguageSelected = { langId ->
                ScreenLogger.tap("language_select", details = "lang=$langId zero_state=true")
                AuditLogger.getInstanceOrNull()?.langSelect(langId, "", zeroState = true)
                onSelectLanguage(langId)
            }
        )
    } else {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.alexpo.grammermate.ui.components.InitialsAvatar(
                    name = state.navigation.userName,
                    size = 40.dp,
                    onClick = {
                        AuditLogger.getInstanceOrNull()?.profileOpen()
                        onProfileClick()
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = state.navigation.userName.take(6),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 48.dp)
                )
                // Inline streak indicator (HS-23)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    val todayFires = state.cardSession.todayFireCount.coerceIn(0, 3)
                    val streakDays = state.cardSession.currentStreak
                    val isActive = todayFires > 0 || streakDays > 0
                    val fireColor = when {
                        streakDays >= 7 -> Color(0xFFFF8F00)
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }

                    Text(
                        text = if (isActive) "🔥".repeat(todayFires.coerceIn(1, 3)) else "🔥",
                        fontSize = 14.sp,
                        color = fireColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${streakDays}d",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = fireColor
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LanguageSelector(
                    label = languageCode,
                    languages = state.navigation.languages,
                    selectedLanguageId = state.navigation.selectedLanguageId?.value ?: "",
                    onSelect = onSelectLanguage
                )
                IconButton(onClick = { showPomodoroSheet = true }) {
                    Image(
                        painter = painterResource(R.drawable.ic_pomodoro_timer),
                        contentDescription = "Pomodoro Timer",
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = {
                    AuditLogger.getInstanceOrNull()?.settingsOpen("home")
                    onOpenSettings()
                }) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_settings))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Pack tiles — always visible
        if (packTiles.isNotEmpty()) {
            PackTileGrid(
                tiles = packTiles,
                onSelectPack = { packId ->
                    ScreenLogger.tap("pack_select", details = "pack=$packId")
                    val packName = packTiles.firstOrNull { it.packId == packId }?.displayName ?: ""
                    AuditLogger.getInstanceOrNull()?.packSelect(packId, packName)
                    onSelectPack(packId)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(text = stringResource(R.string.home_grammar_roadmap), fontWeight = FontWeight.SemiBold)
        if (state.navigation.selectedLessonId != null) {
            val lessonIndex = state.navigation.lessons.indexOfFirst { it.id == state.navigation.selectedLessonId }
                .takeIf { it >= 0 } ?: 0
            val exerciseCount = "${state.cardSession.completedSubLessonCount}/${state.cardSession.subLessonCount}"
            Text(
                text = "Lesson ${lessonIndex + 1}/ Exercise $exerciseCount",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (state.navigation.activePackId != null) {
            // There are exactly twelve lesson slots. Use one screen scroll owner;
            // a nested lazy grid would receive unbounded height here.
            tiles.chunked(4).forEach { rowTiles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowTiles.forEach { tile ->
                        Box(modifier = Modifier.weight(1f)) {
                            val flower = tile.lessonId?.let { state.flowerDisplay.lessonFlowers[it] }
                            LessonTile(
                                tile = tile,
                                flower = flower,
                                onSelect = {
                                    val lessonId = tile.lessonId ?: return@LessonTile
                                    ScreenLogger.tap("lesson_tap", details = "lesson=$lessonId")
                                    AuditLogger.getInstanceOrNull()?.lessonSelect(lessonId, "")
                                    onSelectLesson(lessonId)
                                },
                                onLockedClick = {
                                    if (tile.lessonId != null) {
                                        earlyStartLessonId = tile.lessonId
                                    } else {
                                        showLockedLessonHint = true
                                    }
                                }
                            )
                        }
                    }
                    repeat(4 - rowTiles.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (hasVerbDrill || hasVocabDrill) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hasVerbDrill) {
                    VerbDrillEntryTile(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ScreenLogger.tap("drill_start", details = "type=verb")
                            AuditLogger.getInstanceOrNull()?.verbDrillStart(0)
                            onOpenVerbDrill()
                        }
                    )
                }
                if (hasVerbDrill) {
                    AuxDrillEntryTile(
                        modifier = Modifier.weight(1f),
                        onClick = onOpenAuxDrill
                    )
                }
                if (hasVocabDrill) {
                    VocabDrillEntryTile(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ScreenLogger.tap("drill_start", details = "type=vocab")
                            AuditLogger.getInstanceOrNull()?.vocabDrillStart("")
                            onOpenVocabDrill()
                        },
                        masteredCount = state.vocabSprint.vocabMasteredCount
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (state.navigation.activePackId != null) {
            DailyPracticeEntryTile(
                onClick = {
                    ScreenLogger.tap("daily_practice")
                    onDailyPractice()
                },
                enabled = false // temporarily disabled — tap shows "Временно не доступно"
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Background Vocab Listener — passive listening deck.
            // Guarded by activePackId != null per CLAUDE.md conditional-visibility rule.
            OutlinedButton(
                onClick = {
                    ScreenLogger.tap("bg_vocab_open")
                    onBackgroundVocab()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Фоновое слушание")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(R.string.home_legend), fontWeight = FontWeight.SemiBold)
        Text(text = "🌱 ${stringResource(R.string.home_legend_seed_growing_bloom)}")
        Text(text = "🥀 ${stringResource(R.string.home_legend_wilting_wilted_forgotten)}")
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                AuditLogger.getInstanceOrNull()?.methodInfo()
                showMethod = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.home_how_training_works))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (hasActivePack) {
                    onPrimaryAction()
                } else {
                    packTiles.firstOrNull()?.let { onSelectPack(it.packId) }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = primaryLabel)
        }
    }
    } // end else (normal home content)

    if (showMethod) {
        AlertDialog(
            onDismissRequest = { showMethod = false },
            confirmButton = {
                TextButton(onClick = { showMethod = false }) {
                    Text(text = stringResource(R.string.home_ok))
                }
            },
            title = { Text(text = stringResource(R.string.home_how_training_works)) },
            text = {
                Text(
                    text = stringResource(R.string.home_method_description)
                )
            }
        )
    }
    if (showLockedLessonHint) {
        AlertDialog(
            onDismissRequest = { showLockedLessonHint = false },
            confirmButton = {
                TextButton(onClick = {
                    AuditLogger.getInstanceOrNull()?.dialogAction("locked_lesson", "ok")
                    showLockedLessonHint = false
                }) {
                    Text(text = stringResource(R.string.home_ok))
                }
            },
            title = { Text(text = stringResource(R.string.home_lesson_locked)) },
            text = { Text(text = stringResource(R.string.home_complete_previous_lesson)) }
        )
    }
    if (earlyStartLessonId != null) {
        AlertDialog(
            onDismissRequest = { earlyStartLessonId = null },
            confirmButton = {
                TextButton(onClick = {
                    val lessonId = earlyStartLessonId
                    earlyStartLessonId = null
                    if (lessonId != null) {
                        AuditLogger.getInstanceOrNull()?.dialogAction("early_start", "yes")
                        onSelectLesson(lessonId)
                    }
                }) {
                    Text(text = stringResource(R.string.home_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    AuditLogger.getInstanceOrNull()?.dialogAction("early_start", "no")
                    earlyStartLessonId = null
                }) {
                    Text(text = stringResource(R.string.home_no))
                }
            },
            title = { Text(text = stringResource(R.string.home_start_early_title)) },
            text = { Text(text = stringResource(R.string.home_start_early_message)) }
        )
    }

    PomodoroSelectorSheet(
        showSheet = showPomodoroSheet,
        onDismiss = { showPomodoroSheet = false },
        onStart = { duration ->
            showPomodoroSheet = false
            onStartPomodoro(duration)
        },
        lastDuration = pomodoroLastDuration,
        history = pomodoroHistory
    )
}
