package com.alexpo.grammermate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.SubLessonType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent

@Composable
fun GrammarMateApp() {
    GrammarMateTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val vm: TrainingViewModel = viewModel()
            val state by vm.uiState.collectAsState()
            var screen by remember { mutableStateOf(AppScreen.HOME) }
            var showSettings by remember { mutableStateOf(false) }
            var showExitDialog by remember { mutableStateOf(false) }
            val lastFinishedToken = remember { mutableStateOf(state.subLessonFinishedToken) }
            val lastVocabFinishedToken = remember { mutableStateOf(state.vocabFinishedToken) }
            val lastBossFinishedToken = remember { mutableStateOf(state.bossFinishedToken) }
            val lastEliteFinishedToken = remember { mutableStateOf(state.eliteFinishedToken) }

            BackHandler(enabled = screen == AppScreen.TRAINING && !showSettings) {
                showExitDialog = true
            }
            BackHandler(enabled = screen == AppScreen.LESSON && !showSettings) {
                screen = AppScreen.HOME
            }
            BackHandler(enabled = screen == AppScreen.ELITE && !showSettings) {
                screen = AppScreen.HOME
            }
            BackHandler(enabled = screen == AppScreen.STORY && !showSettings) {
                screen = AppScreen.LESSON
            }
            BackHandler(enabled = screen == AppScreen.VOCAB && !showSettings) {
                screen = AppScreen.LESSON
            }

            SettingsSheet(
                show = showSettings,
                state = state,
                onDismiss = {
                    showSettings = false
                    if (screen == AppScreen.TRAINING && state.currentCard != null) {
                        vm.resumeFromSettings()
                    }
                },
                onSelectLanguage = vm::selectLanguage,
                onSelectLesson = vm::selectLesson,
                onDeleteLesson = vm::deleteLesson,
                onAddLanguage = vm::addLanguage,
                onImportLessonPack = vm::importLessonPack,
                onImportLesson = vm::importLesson,
                onResetReload = vm::resetAndImportLesson,
                onCreateEmptyLesson = vm::createEmptyLesson,
                onDeleteAllLessons = vm::deleteAllLessons,
                onDeletePack = vm::deletePack
            )

            when (screen) {
                AppScreen.HOME -> HomeScreen(
                    state = state,
                    onSelectLanguage = vm::selectLanguage,
                    onOpenSettings = {
                        vm.pauseSession()
                        showSettings = true
                    },
                    onPrimaryAction = { screen = AppScreen.LESSON },
                    onSelectLesson = { lessonId ->
                        vm.selectLesson(lessonId)
                        screen = AppScreen.LESSON
                    },
                    onOpenElite = { if (state.eliteUnlocked) screen = AppScreen.ELITE }
                )
                AppScreen.LESSON -> LessonRoadmapScreen(
                    state = state,
                    onBack = { screen = AppScreen.HOME },
                    onStartSubLesson = { index ->
                        vm.selectSubLesson(index)
                        screen = AppScreen.TRAINING
                    },
                    onOpenVocab = {
                        vm.openVocabSprint()
                        screen = AppScreen.VOCAB
                    },
                    onStartBossLesson = {
                        vm.startBossLesson()
                        screen = AppScreen.TRAINING
                    },
                    onStartBossMega = {
                        vm.startBossMega()
                        screen = AppScreen.TRAINING
                    },
                    onOpenStory = { phase ->
                        vm.openStory(phase)
                        screen = AppScreen.STORY
                    }
                )
                AppScreen.ELITE -> EliteRoadmapScreen(
                    state = state,
                    onBack = { screen = AppScreen.HOME },
                    onStartStep = { index ->
                        vm.openEliteStep(index)
                        screen = AppScreen.TRAINING
                    },
                    onStartBoss = {
                        vm.startBossElite()
                        screen = AppScreen.TRAINING
                    }
                )
                AppScreen.STORY -> StoryQuizScreen(
                    story = state.activeStory,
                    testMode = state.testMode,
                    onClose = {
                        state.activeStory?.phase?.let { phase ->
                            vm.completeStory(phase, false)
                        }
                        screen = AppScreen.LESSON
                    },
                    onComplete = { allCorrect ->
                        state.activeStory?.phase?.let { phase ->
                            vm.completeStory(phase, allCorrect)
                        }
                        screen = AppScreen.LESSON
                    }
                )
                AppScreen.VOCAB -> VocabSprintScreen(
                    state = state,
                    onInputChange = vm::onVocabInputChanged,
                    onSubmit = { input -> vm.submitVocabAnswer(input) },
                    onSetInputMode = vm::setVocabInputMode,
                    onRequestVoice = vm::requestVocabVoice,
                    onClose = { screen = AppScreen.LESSON }
                )
                AppScreen.TRAINING -> TrainingScreen(
                    state = state,
                    onInputChange = vm::onInputChanged,
                    onSubmit = vm::submitAnswer,
                    onPrev = vm::prevCard,
                    onNext = vm::nextCard,
                    onTogglePause = vm::togglePause,
                    onRequestExit = { showExitDialog = true },
                    onOpenSettings = vm::pauseSession,
                    onShowSettings = { showSettings = true },
                    onSelectLesson = vm::selectLesson,
                    onSelectMode = vm::selectMode,
                    onSetInputMode = vm::setInputMode,
                    onShowAnswer = vm::showAnswer,
                    onVoicePromptStarted = vm::onVoicePromptStarted
                )
            }

            if (screen == AppScreen.TRAINING && state.subLessonFinishedToken != lastFinishedToken.value) {
                lastFinishedToken.value = state.subLessonFinishedToken
                screen = AppScreen.LESSON
            }
            if (screen == AppScreen.VOCAB && state.vocabFinishedToken != lastVocabFinishedToken.value) {
                lastVocabFinishedToken.value = state.vocabFinishedToken
                screen = AppScreen.LESSON
            }
            if (screen == AppScreen.TRAINING && state.bossFinishedToken != lastBossFinishedToken.value) {
                lastBossFinishedToken.value = state.bossFinishedToken
                screen = if (state.bossLastType == com.alexpo.grammermate.data.BossType.ELITE) {
                    AppScreen.ELITE
                } else {
                    AppScreen.LESSON
                }
            }
            if (screen == AppScreen.TRAINING && state.eliteFinishedToken != lastEliteFinishedToken.value) {
                lastEliteFinishedToken.value = state.eliteFinishedToken
                screen = AppScreen.ELITE
            }

            if (showExitDialog) {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showExitDialog = false
                            if (state.bossActive) {
                                vm.finishBoss()
                                screen = if (state.bossType == com.alexpo.grammermate.data.BossType.ELITE) {
                                    AppScreen.ELITE
                                } else {
                                    AppScreen.LESSON
                                }
                                return@TextButton
                            }
                            if (state.eliteActive) {
                                vm.cancelEliteSession()
                                screen = AppScreen.ELITE
                                return@TextButton
                            }
                            vm.finishSession()
                            screen = AppScreen.LESSON
                        }) {
                            Text(text = "Выйти")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitDialog = false }) {
                            Text(text = "Отмена")
                        }
                    },
                    title = { Text(text = "Завершить сессию?") },
                    text = { Text(text = "Текущая сессия будет завершена.") }
                )
            }

            if (state.storyErrorMessage != null) {
                AlertDialog(
                    onDismissRequest = { vm.clearStoryError() },
                    confirmButton = {
                        TextButton(onClick = { vm.clearStoryError() }) {
                            Text(text = "OK")
                        }
                    },
                    title = { Text(text = "История") },
                    text = { Text(text = state.storyErrorMessage ?: "") }
                )
            }
            if (state.vocabErrorMessage != null) {
                AlertDialog(
                    onDismissRequest = { vm.clearVocabError() },
                    confirmButton = {
                        TextButton(onClick = { vm.clearVocabError() }) {
                            Text(text = "OK")
                        }
                    },
                    title = { Text(text = "Vocabulary") },
                    text = { Text(text = state.vocabErrorMessage ?: "") }
                )
            }
            if (state.bossErrorMessage != null) {
                AlertDialog(
                    onDismissRequest = { vm.clearBossError() },
                    confirmButton = {
                        TextButton(onClick = { vm.clearBossError() }) {
                            Text(text = "OK")
                        }
                    },
                    title = { Text(text = "Boss") },
                    text = { Text(text = state.bossErrorMessage ?: "") }
                )
            }
            if (state.bossRewardMessage != null && state.bossReward != null) {
                AlertDialog(
                    onDismissRequest = { vm.clearBossRewardMessage() },
                    confirmButton = {
                        TextButton(onClick = { vm.clearBossRewardMessage() }) {
                            Text(text = "OK")
                        }
                    },
                    icon = {
                        val tint = when (state.bossReward) {
                            com.alexpo.grammermate.data.BossReward.BRONZE -> Color(0xFFCD7F32)
                            com.alexpo.grammermate.data.BossReward.SILVER -> Color(0xFFC0C0C0)
                            com.alexpo.grammermate.data.BossReward.GOLD -> Color(0xFFFFD700)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = tint
                        )
                    },
                    title = { Text(text = "Boss Reward") },
                    text = { Text(text = state.bossRewardMessage ?: "") }
                )
            }
        }
    }
}

private enum class AppScreen {
    HOME,
    LESSON,
    ELITE,
    VOCAB,
    STORY,
    TRAINING
}

private enum class LessonTileState {
    SEED,
    SPROUT,
    FLOWER,
    LOCKED
}

private data class LessonTileUi(
    val index: Int,
    val lessonId: String?,
    val state: LessonTileState
)

@Composable
private fun HomeScreen(
    state: TrainingUiState,
    onSelectLanguage: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onPrimaryAction: () -> Unit,
    onSelectLesson: (String) -> Unit,
    onOpenElite: () -> Unit
) {
    val tiles = remember(state.lessons, state.testMode) { buildLessonTiles(state.lessons, state.testMode) }
    var showMethod by remember { mutableStateOf(false) }
    val languageCode = state.languages
        .firstOrNull { it.id == state.selectedLanguageId }
        ?.id
        ?.uppercase()
        ?: "--"
    val isFirstLaunch = state.correctCount == 0 &&
        state.incorrectCount == 0 &&
        state.activeTimeMs == 0L
    val primaryLabel = when {
        isFirstLaunch -> "Start Grammar Engine"
        state.sessionState == SessionState.ACTIVE -> "Continue Learning"
        else -> "Resume Program"
    }
    val nextLessonIndex = state.lessons.indexOfFirst { it.id == state.selectedLessonId }
        .takeIf { it >= 0 }
        ?: 0
    val nextHint = if (state.lessons.isNotEmpty()) {
        "Next: Lesson ${nextLessonIndex + 1} • Block 1/10"
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "AP",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "Alex Po", fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LanguageSelector(
                    label = languageCode,
                    languages = state.languages,
                    selectedLanguageId = state.selectedLanguageId,
                    onSelect = onSelectLanguage
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPrimaryAction)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = primaryLabel,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                if (nextHint != null) {
                    Text(
                        text = nextHint,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Grammar Engine", fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(tiles) { _, tile ->
                LessonTile(
                    tile = tile,
                    onSelect = {
                        val lessonId = tile.lessonId ?: return@LessonTile
                        onSelectLesson(lessonId)
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        EliteEntryTile(enabled = state.eliteUnlocked, onClick = onOpenElite)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Legend:", fontWeight = FontWeight.SemiBold)
        Text(text = "🌱 forming pattern • 🌸 automated skill")
        Text(text = "🕸️ needs refresh")
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { showMethod = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "How This Training Works")
        }
    }

    if (showMethod) {
        AlertDialog(
            onDismissRequest = { showMethod = false },
            confirmButton = {
                TextButton(onClick = { showMethod = false }) {
                    Text(text = "OK")
                }
            },
            title = { Text(text = "How This Training Works") },
            text = {
                Text(
                    text = "GrammarMate builds automatic grammar patterns with repeated retrieval. " +
                        "States show how stable each pattern is and when it needs refresh."
                )
            }
        )
    }
}

@Composable
private fun EliteEntryTile(enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "ELITE", fontWeight = FontWeight.SemiBold)
            Icon(Icons.Default.Lock, contentDescription = "Elite locked")
        }
    }
}

@Composable
private fun LessonRoadmapScreen(
    state: TrainingUiState,
    onBack: () -> Unit,
    onStartSubLesson: (Int) -> Unit,
    onOpenVocab: () -> Unit,
    onStartBossLesson: () -> Unit,
    onStartBossMega: () -> Unit,
    onOpenStory: (com.alexpo.grammermate.data.StoryPhase) -> Unit
) {
    val lessonTitle = state.lessons
        .firstOrNull { it.id == state.selectedLessonId }
        ?.title
        ?: "Lesson"
    val fallbackTotal = state.subLessonCount.coerceAtLeast(1)
    val fallbackNewOnlyCount = fallbackTotal.coerceAtMost(3)
    val trainingTypes = if (state.subLessonTypes.isNotEmpty()) {
        state.subLessonTypes
    } else {
        List(fallbackTotal) { index ->
            if (index < fallbackNewOnlyCount) SubLessonType.NEW_ONLY else SubLessonType.MIXED
        }
    }
    val total = trainingTypes.size.coerceAtLeast(1)
    val completed = state.completedSubLessonCount.coerceIn(0, total)
    val currentIndex = state.activeSubLessonIndex.coerceIn(0, total - 1)
    val lessonIndex = state.lessons.indexOfFirst { it.id == state.selectedLessonId }
    val hasMegaBoss = lessonIndex > 0
    val bossLessonReward = state.selectedLessonId?.let { state.bossLessonRewards[it] }
    val bossMegaReward = state.bossMegaReward
    val entries = buildRoadmapEntries(trainingTypes, hasMegaBoss)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(text = lessonTitle, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(40.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = completed.toFloat() / total.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Подурок ${currentIndex + 1} из $total", textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(entries) { _, entry ->
                when (entry) {
                    is RoadmapEntry.Training -> {
                        val index = entry.index
                        val isCompleted = index < completed
                        val isActive = index == currentIndex
                        val canEnter = state.testMode || isCompleted || isActive
                        val kindLabel = when (entry.type) {
                            SubLessonType.WARMUP -> "WARM"
                            SubLessonType.NEW_ONLY -> "NEW"
                            SubLessonType.MIXED -> "MIX"
                        }
                        val emoji = if (isCompleted) "🌸" else "🔒"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clickable(enabled = canEnter) { onStartSubLesson(index) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = "${index + 1}", fontWeight = FontWeight.SemiBold)
                                Text(text = emoji, fontSize = 18.sp)
                                Text(text = kindLabel, fontSize = 10.sp)
                            }
                        }
                    }
                    is RoadmapEntry.Vocab -> {
                        VocabTile(label = "Vocab", onClick = onOpenVocab)
                    }
                    is RoadmapEntry.StoryCheckIn -> {
                        StoryTile(
                            label = "IN",
                            completed = state.storyCheckInDone,
                            onClick = { onOpenStory(com.alexpo.grammermate.data.StoryPhase.CHECK_IN) }
                        )
                    }
                    is RoadmapEntry.StoryCheckOut -> {
                        StoryTile(
                            label = "OUT",
                            completed = state.storyCheckOutDone,
                            onClick = { onOpenStory(com.alexpo.grammermate.data.StoryPhase.CHECK_OUT) }
                        )
                    }
                    is RoadmapEntry.BossLesson -> {
                        BossTile(
                            label = "Boss",
                            enabled = true,
                            reward = bossLessonReward,
                            onClick = onStartBossLesson
                        )
                    }
                    is RoadmapEntry.BossMega -> {
                        BossTile(
                            label = "Mega",
                            enabled = true,
                            reward = bossMegaReward,
                            onClick = onStartBossMega
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onStartSubLesson(currentIndex) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (completed == 0) "Start Lesson" else "Continue Lesson")
        }
    }
}

@Composable
private fun EliteRoadmapScreen(
    state: TrainingUiState,
    onBack: () -> Unit,
    onStartStep: (Int) -> Unit,
    onStartBoss: () -> Unit
) {
    val stepCount = com.alexpo.grammermate.data.TrainingConfig.ELITE_STEP_COUNT
    val currentIndex = state.eliteStepIndex.coerceIn(0, stepCount - 1)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(text = "ELITE", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(40.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(List(stepCount) { it }) { _, index ->
                EliteStepTile(
                    index = index,
                    isActive = index == currentIndex,
                    onClick = { onStartStep(index) }
                )
            }
            item {
                BossTile(label = "Boss", enabled = true, reward = null, onClick = onStartBoss)
            }
        }
    }
}

@Composable
private fun EliteStepTile(
    index: Int,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val icon = if (isActive) Icons.Default.PlayArrow else Icons.Default.Lock
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(enabled = isActive, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Step ${index + 1}", fontWeight = FontWeight.SemiBold)
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}
private sealed class RoadmapEntry {
    data class Training(val index: Int, val type: SubLessonType) : RoadmapEntry()
    object Vocab : RoadmapEntry()
    object StoryCheckIn : RoadmapEntry()
    object StoryCheckOut : RoadmapEntry()
    object BossLesson : RoadmapEntry()
    object BossMega : RoadmapEntry()
}

private fun buildRoadmapEntries(trainingTypes: List<SubLessonType>, hasMegaBoss: Boolean): List<RoadmapEntry> {
    val entries = mutableListOf<RoadmapEntry>()
    entries.add(RoadmapEntry.Vocab)
    entries.add(RoadmapEntry.StoryCheckIn)
    trainingTypes.forEachIndexed { index, type ->
        entries.add(RoadmapEntry.Training(index, type))
    }
    entries.add(RoadmapEntry.Vocab)
    entries.add(RoadmapEntry.StoryCheckOut)
    entries.add(RoadmapEntry.BossLesson)
    if (hasMegaBoss) {
        entries.add(RoadmapEntry.BossMega)
    }
    return entries
}

@Composable
private fun VocabTile(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = label, fontWeight = FontWeight.SemiBold)
            Icon(
                imageVector = Icons.Default.LibraryBooks,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun BossTile(label: String, enabled: Boolean, reward: BossReward?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        val tint = when (reward) {
            BossReward.BRONZE -> Color(0xFFCD7F32)
            BossReward.SILVER -> Color(0xFFC0C0C0)
            BossReward.GOLD -> Color(0xFFFFD700)
            null -> MaterialTheme.colorScheme.onSurface
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = label, fontWeight = FontWeight.SemiBold)
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = tint
            )
        }
    }
}

@Composable
private fun VocabSprintScreen(
    state: TrainingUiState,
    onInputChange: (String) -> Unit,
    onSubmit: (String?) -> Unit,
    onSetInputMode: (InputMode) -> Unit,
    onRequestVoice: () -> Unit,
    onClose: () -> Unit
) {
    val vocab = state.currentVocab
    val latestState by rememberUpdatedState(state)
    val canLaunchVoice = vocab != null
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank() && latestState.currentVocab != null) {
                onInputChange(spoken)
                onSubmit(spoken)
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(
        state.vocabVoiceTriggerToken,
        state.vocabInputMode,
        vocab?.id
    ) {
        if (state.vocabInputMode == InputMode.VOICE && vocab != null) {
            kotlinx.coroutines.delay(200)
            launchVoiceRecognition(state.selectedLanguageId, vocab.nativeText, speechLauncher)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Vocabulary Sprint", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        val progressText = if (state.vocabTotal > 0) {
            "${state.vocabIndex + 1} / ${state.vocabTotal}"
        } else {
            "0 / 0"
        }
        Text(text = progressText)
        Spacer(modifier = Modifier.height(16.dp))
        if (vocab != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = vocab.nativeText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    }
                }
                Card(
                    modifier = Modifier
                        .width(96.dp)
                        .height(72.dp)
                ) {
                    val isVoice = state.vocabInputMode == InputMode.VOICE
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val micClick = { onRequestVoice() }
                        if (isVoice) {
                            FilledTonalIconButton(onClick = micClick, enabled = canLaunchVoice) {
                                Icon(Icons.Default.Mic, contentDescription = "Voice")
                            }
                        } else {
                            IconButton(onClick = micClick, enabled = canLaunchVoice) {
                                Icon(Icons.Default.Mic, contentDescription = "Voice")
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (isVoice) {
                            IconButton(onClick = { onSetInputMode(InputMode.KEYBOARD) }) {
                                Icon(Icons.Default.Keyboard, contentDescription = "Keyboard")
                            }
                        } else {
                            FilledTonalIconButton(onClick = { onSetInputMode(InputMode.KEYBOARD) }) {
                                Icon(Icons.Default.Keyboard, contentDescription = "Keyboard")
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.vocabInputText,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Answer") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            state.vocabAnswerText?.let { answer ->
                Text(text = "Answer: $answer", color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(onClick = { onSubmit(state.vocabInputText) }, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Check")
            }
        } else {
            Text(text = "No words")
        }
    }
}

@Composable
private fun StoryTile(label: String, completed: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = label, fontWeight = FontWeight.SemiBold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                if (completed) {
                    Icon(
                        imageVector = Icons.Default.LocalFlorist,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
@Composable
private fun StoryQuizScreen(
    story: com.alexpo.grammermate.data.StoryQuiz?,
    testMode: Boolean,
    onClose: () -> Unit,
    onComplete: (Boolean) -> Unit
) {
    if (story == null) {
        onClose()
        return
    }
    val selections = remember(story.storyId) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val results = remember(story.storyId) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var questionIndex by remember(story.storyId) { mutableStateOf(0) }
    var showResult by remember(story.storyId) { mutableStateOf(false) }
    var errorMessage by remember(story.storyId) { mutableStateOf<String?>(null) }
    if (story.questions.isEmpty()) {
        onComplete(true)
        return
    }
    val question = story.questions.getOrNull(questionIndex) ?: run {
        val allCorrect = results.value.size == story.questions.size &&
            results.value.values.all { it }
        onComplete(allCorrect)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = if (story.phase == com.alexpo.grammermate.data.StoryPhase.CHECK_IN) {
                "Story Check-in"
            } else {
                "Story Check-out"
            },
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = story.text, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Question ${questionIndex + 1} / ${story.questions.size}")
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = question.prompt, fontWeight = FontWeight.SemiBold)
        question.options.forEachIndexed { index, option ->
            val selected = selections.value[question.qId] == index
            val isCorrectOption = index == question.correctIndex
            val optionSuffix = when {
                showResult && isCorrectOption -> " (correct)"
                showResult && selected && !isCorrectOption -> " (your choice)"
                else -> ""
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selections.value = selections.value + (question.qId to index)
                        errorMessage = null
                        if (showResult) {
                            results.value = results.value - question.qId
                            showResult = false
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = if (selected) ">" else " ")
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = option + optionSuffix)
            }
        }
        if (showResult) {
            Spacer(modifier = Modifier.height(8.dp))
            val correctText = question.options.getOrNull(question.correctIndex).orEmpty()
            Text(text = "Correct: $correctText", color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val prevIndex = (questionIndex - 1).coerceAtLeast(0)
                    if (prevIndex != questionIndex) {
                        questionIndex = prevIndex
                        val prevId = story.questions[questionIndex].qId
                        showResult = results.value.containsKey(prevId)
                        errorMessage = null
                    }
                },
                enabled = questionIndex > 0,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Prev")
            }
            Button(
                onClick = {
                    val selected = selections.value[question.qId]
                    if (selected == null) {
                        errorMessage = "Select an answer"
                        return@Button
                    }
                    val correct = selected == question.correctIndex
                    results.value = results.value + (question.qId to correct)
                    showResult = true
                    errorMessage = if (correct) null else "Incorrect"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Check")
            }
            Button(
                onClick = {
                    val selected = selections.value[question.qId]
                    if (selected == null) {
                        errorMessage = "Select an answer"
                        return@Button
                    }
                    val correct = selected == question.correctIndex
                    results.value = results.value + (question.qId to correct)
                    val isLast = questionIndex >= story.questions.lastIndex
                    if (isLast) {
                        val allCorrect = results.value.size == story.questions.size &&
                            results.value.values.all { it }
                        onComplete(testMode || allCorrect)
                        return@Button
                    }
                    questionIndex += 1
                    val nextId = story.questions[questionIndex].qId
                    showResult = results.value.containsKey(nextId)
                    errorMessage = null
                },
                modifier = Modifier.weight(1f)
            ) {
                val isLast = questionIndex >= story.questions.lastIndex
                Text(text = if (isLast) "Finish" else "Next")
            }
        }
    }
}
@Composable
private fun LessonTile(tile: LessonTileUi, onSelect: () -> Unit) {
    val emoji = when (tile.state) {
        LessonTileState.SEED -> "🌱"
        LessonTileState.SPROUT -> "🌿"
        LessonTileState.FLOWER -> "🌸"
        LessonTileState.LOCKED -> "🔒"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(enabled = tile.state != LessonTileState.LOCKED, onClick = onSelect)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "${tile.index + 1}", fontWeight = FontWeight.SemiBold)
            Text(text = emoji, fontSize = 18.sp)
        }
    }
}

@Composable
private fun LanguageSelector(
    label: String,
    languages: List<com.alexpo.grammermate.data.Language>,
    selectedLanguageId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) {
        Text(text = label)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        languages.forEach { language ->
            DropdownMenuItem(
                text = { Text(text = language.displayName) },
                onClick = {
                    expanded = false
                    if (language.id != selectedLanguageId) onSelect(language.id)
                }
            )
        }
    }
}

private fun buildLessonTiles(lessons: List<Lesson>, testMode: Boolean): List<LessonTileUi> {
    val total = 12
    val tiles = mutableListOf<LessonTileUi>()
    for (i in 0 until total) {
        val lesson = lessons.getOrNull(i)
        val state = when {
            lesson == null -> LessonTileState.LOCKED
            testMode -> LessonTileState.SEED
            i == 0 -> LessonTileState.SPROUT
            else -> LessonTileState.SEED
        }
        tiles.add(LessonTileUi(i, lesson?.id, state))
    }
    return tiles
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    show: Boolean,
    state: TrainingUiState,
    onDismiss: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSelectLesson: (String) -> Unit,
    onDeleteLesson: (String) -> Unit,
    onAddLanguage: (String) -> Unit,
    onImportLessonPack: (android.net.Uri) -> Unit,
    onImportLesson: (android.net.Uri) -> Unit,
    onResetReload: (android.net.Uri) -> Unit,
    onCreateEmptyLesson: (String) -> Unit,
    onDeleteAllLessons: () -> Unit,
    onDeletePack: (String) -> Unit
) {
    if (!show) return
    val sheetState = rememberModalBottomSheetState()
    var newLessonTitle by remember { mutableStateOf("") }
    var newLanguageName by remember { mutableStateOf("") }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportLesson(uri)
    }
    val packImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportLessonPack(uri)
    }
    val resetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onResetReload(uri)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Служебный режим",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            LanguageLessonColumn(state, onSelectLanguage, onSelectLesson, onDeleteLesson)
            OutlinedTextField(
                value = newLanguageName,
                onValueChange = { newLanguageName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Новый язык (название)") }
            )
            OutlinedButton(
                onClick = {
                    val name = newLanguageName.trim()
                    if (name.isNotEmpty()) {
                        onAddLanguage(name)
                        newLanguageName = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Добавить язык")
            }
            OutlinedButton(
                onClick = {
                    packImportLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Импорт пакета уроков (ZIP)")
            }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("text/*", "text/csv")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Импорт урока (CSV)")
            }
            OutlinedButton(
                onClick = { resetLauncher.launch(arrayOf("text/*", "text/csv")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Reset/Reload (очистить + импорт)")
            }
            OutlinedTextField(
                value = newLessonTitle,
                onValueChange = { newLessonTitle = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Пустой урок (название)") }
            )
            OutlinedButton(
                onClick = {
                    val title = newLessonTitle.trim()
                    if (title.isNotEmpty()) {
                        onCreateEmptyLesson(title)
                        newLessonTitle = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Создать пустой урок")
            }
            OutlinedButton(
                onClick = { onDeleteAllLessons() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB00020))
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Удалить все уроки")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "CSV формат", style = MaterialTheme.typography.labelLarge)
            Text(
                text = "UTF-8, разделитель ';'.\n" +
                    "Колонка 1: RU предложение.\n" +
                    "Колонка 2: перевод(ы), варианты через '+'.\n" +
                    "Пример: Он не работает из дома;He doesn't work from home+He does not work from home",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Инструкция", style = MaterialTheme.typography.labelLarge)
            Text(
                text = "Play - старт/продолжение, Pause - пауза, Stop - сброс прогресса без обнуления рейтинга.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Уроки", style = MaterialTheme.typography.labelLarge)
            LazyColumn(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                items(state.lessons) { lesson ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = lesson.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (lesson.id == state.selectedLessonId) {
                            Text(
                                text = "Выбран",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Пакеты", style = MaterialTheme.typography.labelLarge)
            if (state.installedPacks.isEmpty()) {
                Text(
                    text = "Нет установленных пакетов",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                state.installedPacks.forEach { pack ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${pack.packId} (${pack.packVersion})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onDeletePack(pack.packId) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete pack")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainingScreen(
    state: TrainingUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> SubmitResult,
    onPrev: () -> Unit,
    onNext: (Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onRequestExit: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowSettings: () -> Unit,
    onSelectLesson: (String) -> Unit,
    onSelectMode: (TrainingMode) -> Unit,
    onSetInputMode: (InputMode) -> Unit,
    onShowAnswer: () -> Unit,
    onVoicePromptStarted: () -> Unit
) {
    val hasCards = state.currentCard != null

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "GrammarMate",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    onOpenSettings()
                    onShowSettings()
                }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderStats(state)
            if (state.bossActive) {
                Text(text = "Boss Session", fontWeight = FontWeight.SemiBold)
            } else if (state.eliteActive) {
                Text(text = "Elite Session", fontWeight = FontWeight.SemiBold)
            } else {
                ModeSelector(state, onSelectMode, onSelectLesson)
            }
            CardPrompt(state)
            AnswerBox(
                state,
                onInputChange,
                onSubmit,
                onSetInputMode,
                onShowAnswer,
                onVoicePromptStarted,
                hasCards
            )
            ResultBlock(state)
            NavigationRow(onPrev, onNext, onTogglePause, onRequestExit, state.sessionState, hasCards)
        }
    }
}

@Composable
private fun HeaderStats(state: TrainingUiState) {
    val total = if (state.bossActive) state.bossTotal else state.subLessonTotal
    val progressIndex = if (total > 0) {
        if (state.bossActive) {
            state.bossProgress.coerceIn(0, total)
        } else {
            (state.currentIndex - state.warmupCount).coerceIn(0, total)
        }
    } else {
        0
    }
    val progressPercent = if (total > 0) {
        ((progressIndex.toDouble() / total.toDouble()) * 100).toInt()
    } else {
        0
    }
    val speed = speedPerMinute(state.voiceActiveMs, state.voiceWordCount)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = if (state.bossActive) "Boss" else "Progress")
            Text(
                text = if (!state.bossActive && state.currentIndex < state.warmupCount) "Warm-up" else "${progressPercent}%",
                fontWeight = FontWeight.SemiBold
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "Time")
            Text(text = formatTime(state.activeTimeMs), fontWeight = FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "Speed")
            Text(text = speed, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ModeSelector(
    state: TrainingUiState,
    onSelectMode: (TrainingMode) -> Unit,
    onSelectLesson: (String) -> Unit
) {
    var lessonExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ModeIconButton(
                selected = state.mode == TrainingMode.LESSON,
                icon = Icons.Default.MenuBook,
                contentDescription = "Урок"
            ) {
                onSelectMode(TrainingMode.LESSON)
                lessonExpanded = true
            }
            DropdownMenu(
                expanded = lessonExpanded,
                onDismissRequest = { lessonExpanded = false }
            ) {
                if (state.lessons.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(text = "Нет уроков") },
                        onClick = { lessonExpanded = false }
                    )
                } else {
                    state.lessons.forEach { lesson ->
                        DropdownMenuItem(
                            text = { Text(text = lesson.title) },
                            onClick = {
                                lessonExpanded = false
                                onSelectLesson(lesson.id)
                            }
                        )
                    }
                }
            }
        }
        ModeIconButton(
            selected = state.mode == TrainingMode.ALL_SEQUENTIAL,
            icon = Icons.Default.LibraryBooks,
            contentDescription = "Все уроки"
        ) { onSelectMode(TrainingMode.ALL_SEQUENTIAL) }
        ModeIconButton(
            selected = state.mode == TrainingMode.ALL_MIXED,
            icon = Icons.Default.SwapHoriz,
            contentDescription = "Mixed"
        ) { onSelectMode(TrainingMode.ALL_MIXED) }
    }
}

@Composable
private fun ModeIconButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    if (selected) {
        FilledTonalIconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription)
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

@Composable
private fun LanguageLessonColumn(
    state: TrainingUiState,
    onSelectLanguage: (String) -> Unit,
    onSelectLesson: (String) -> Unit,
    onDeleteLesson: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DropdownSelector(
            title = "Язык",
            selected = state.languages.firstOrNull { it.id == state.selectedLanguageId }?.displayName
                ?: "-",
            items = state.languages.map { it.displayName to it.id },
            onSelect = onSelectLanguage
        )
        DropdownSelector(
            title = "Урок",
            selected = state.lessons.firstOrNull { it.id == state.selectedLessonId }?.title ?: "-",
            items = state.lessons.map { it.title to it.id },
            onSelect = onSelectLesson
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "Удалить урок", style = MaterialTheme.typography.labelMedium)
            state.lessons.forEach { lesson ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = lesson.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onDeleteLesson(lesson.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete lesson")
                    }
                }
            }
        }
    }
}

@Composable
private fun DropdownSelector(
    title: String,
    selected: String,
    items: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(text = selected, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (label, id) ->
                DropdownMenuItem(
                    text = { Text(text = label) },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    }
                )
            }
        }
    }
}

@Composable
private fun CardPrompt(state: TrainingUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "RU", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.currentCard?.promptRu ?: "Нет карточек",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AnswerBox(
    state: TrainingUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> SubmitResult,
    onSetInputMode: (InputMode) -> Unit,
    onShowAnswer: () -> Unit,
    onVoicePromptStarted: () -> Unit,
    hasCards: Boolean
) {
    val latestState by rememberUpdatedState(state)
    val canLaunchVoice = hasCards && state.sessionState == SessionState.ACTIVE
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                if (latestState.sessionState == SessionState.PAUSED) return@rememberLauncherForActivityResult
                onInputChange(spoken)
                onSubmit()
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(
        state.currentCard?.id,
        state.inputMode,
        state.sessionState,
        state.voiceTriggerToken
    ) {
        if (state.inputMode == InputMode.VOICE &&
            state.sessionState == SessionState.ACTIVE &&
            state.currentCard != null
        ) {
            kotlinx.coroutines.delay(200)
            onVoicePromptStarted()
            launchVoiceRecognition(state.selectedLanguageId, state.currentCard?.promptRu, speechLauncher)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.inputText,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Ваш перевод") },
            enabled = hasCards,
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (canLaunchVoice) {
                            onSetInputMode(InputMode.VOICE)
                            onVoicePromptStarted()
                            launchVoiceRecognition(state.selectedLanguageId, state.currentCard?.promptRu, speechLauncher)
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
                text = "Карточек нет",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (state.inputMode == InputMode.VOICE && state.sessionState == SessionState.ACTIVE) {
            Text(
                text = state.currentCard?.promptRu?.let { "Скажите перевод: $it" } ?: "",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                    onClick = {
                        if (canLaunchVoice) {
                            onSetInputMode(InputMode.VOICE)
                            onVoicePromptStarted()
                            launchVoiceRecognition(state.selectedLanguageId, state.currentCard?.promptRu, speechLauncher)
                        }
                    },
                    enabled = canLaunchVoice
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice mode")
                }
                FilledTonalIconButton(onClick = { onSetInputMode(InputMode.KEYBOARD) }) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Keyboard mode")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(text = "Показать ответ") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(
                        onClick = { if (hasCards) onShowAnswer() },
                        enabled = hasCards
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "Показать ответ")
                    }
                }
                Text(
                    text = if (state.inputMode == InputMode.VOICE) "Голос" else "Клавиатура",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Button(
            onClick = { onSubmit() },
            modifier = Modifier.fillMaxWidth(),
            enabled = hasCards &&
                state.inputText.isNotBlank() &&
                state.sessionState == SessionState.ACTIVE &&
                state.currentCard != null
        ) {
            Text(text = "Проверить")
        }
    }
}

@Composable
private fun ResultBlock(state: TrainingUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (state.lastResult) {
            true -> Text(text = "Верно", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
            false -> Text(text = "Ошибка", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
            null -> Text(text = "")
        }
        if (!state.answerText.isNullOrBlank()) {
            Text(text = "Ответ: ${state.answerText}")
        }
    }
}

@Composable
private fun NavigationRow(
    onPrev: () -> Unit,
    onNext: (Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onRequestExit: () -> Unit,
    state: SessionState,
    hasCards: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev, enabled = hasCards) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Prev")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onTogglePause, enabled = hasCards) {
                if (state == SessionState.ACTIVE) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
            }
            IconButton(onClick = onRequestExit, enabled = hasCards) {
                Icon(Icons.Default.StopCircle, contentDescription = "Exit session")
            }
            IconButton(onClick = { onNext(false) }, enabled = hasCards) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next")
            }
        }
    }
}

private fun formatTime(activeMs: Long): String {
    val totalSeconds = activeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun speedPerMinute(activeMs: Long, correct: Int): String {
    val minutes = activeMs / 60000.0
    if (minutes <= 0.0) return "-"
    return String.format("%.1f", correct / minutes)
}

private fun launchVoiceRecognition(
    languageId: String,
    prompt: String?,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val languageTag = when (languageId) {
        "it" -> "it-IT"
        else -> "en-US"
    }
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt ?: "Говорите перевод")
    }
    launcher.launch(intent)
}
