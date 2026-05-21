package com.alexpo.grammermate.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import com.alexpo.grammermate.ui.MasteryGreen
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.FlowerCalculator
import com.alexpo.grammermate.data.FlowerVisual
import com.alexpo.grammermate.data.Language
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonPack
import com.alexpo.grammermate.data.PomodoroHistoryEntry
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.ui.components.PomodoroSelectorSheet
import androidx.compose.ui.res.painterResource

enum class LessonTileState {
    SEED,
    SPROUT,
    FLOWER,
    LOCKED,
    UNLOCKED,  // Available but not started yet (open lock)
    EMPTY,     // No lesson in this slot (pack has fewer than 12 lessons)
    VERB_DRILL
}

data class LessonTileUi(
    val index: Int,
    val lessonId: String?,
    val state: LessonTileState
)

/**
 * Generate initials from user name (first letter of first two words, max 2 chars)
 */
fun getUserInitials(name: String): String {
    return name.trim()
        .split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercase() }
        .joinToString("")
        .ifEmpty { "GM" } // Fallback: GrammarMate
}

@Composable
fun HomeScreen(
    state: TrainingUiState,
    onSelectLanguage: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onPrimaryAction: () -> Unit,
    onSelectLesson: (String) -> Unit,
    onOpenElite: () -> Unit,
    hasVerbDrill: Boolean = false,
    hasVocabDrill: Boolean = false,
    onOpenVerbDrill: () -> Unit = {},
    onOpenVocabDrill: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onStartPomodoro: (Int) -> Unit = {},
    pomodoroLastDuration: Int = 20,
    pomodoroHistory: List<PomodoroHistoryEntry> = emptyList(),
    onSelectPack: (String) -> Unit = {}
) {
    val tiles = remember(state.navigation.selectedLanguageId, state.navigation.lessons, state.cardSession.testMode, state.flowerDisplay.lessonFlowers, state.navigation.selectedLessonId, state.navigation.activePackId, state.navigation.activePackLessonIds) {
        buildLessonTiles(state.navigation.lessons, state.cardSession.testMode, state.flowerDisplay.lessonFlowers, state.navigation.selectedLessonId?.value, state.navigation.activePackLessonIds)
    }
    var showMethod by remember { mutableStateOf(false) }
    var showLockedLessonHint by remember { mutableStateOf(false) }
    var earlyStartLessonId by remember { mutableStateOf<String?>(null) }
    var showPomodoroSheet by remember { mutableStateOf(false) }
    var showPackageList by remember { mutableStateOf(false) }
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
    val primaryLabel = when {
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
                com.alexpo.grammermate.ui.components.InitialsAvatar(
                    name = state.navigation.userName,
                    size = 40.dp,
                    onClick = onProfileClick
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
                    selectedLanguageId = state.navigation.selectedLanguageId.value,
                    onSelect = onSelectLanguage
                )
                IconButton(onClick = { showPomodoroSheet = true }) {
                    Icon(
                        painter = painterResource(com.alexpo.grammermate.R.drawable.ic_tomato),
                        contentDescription = "Pomodoro Timer",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_settings))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { showPackageList = true })
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = activePackDisplayName ?: "Select Pack",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }

        if (showPackageList) {
            // Filter packs by selected language to avoid mixing languages
            val languagePacks = state.navigation.installedPacks.filter {
                it.languageId == state.navigation.selectedLanguageId
            }
            PackageSelectorList(
                packs = languagePacks,
                currentPackId = state.navigation.activePackId?.value,
                onPackSelected = { packId ->
                    onSelectPack(packId)
                    showPackageList = false
                },
                onDismiss = { showPackageList = false }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(tiles) { _, tile ->
                val flower = tile.lessonId?.let { state.flowerDisplay.lessonFlowers[it] }
                LessonTile(
                    tile = tile,
                    flower = flower,
                    onSelect = {
                        val lessonId = tile.lessonId ?: return@LessonTile
                        onSelectLesson(lessonId)
                    },
                    onLockedClick = {
                        // For locked tiles with a lesson, offer early start
                        if (tile.lessonId != null) {
                            earlyStartLessonId = tile.lessonId
                        } else {
                            showLockedLessonHint = true
                        }
                    }
                )
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
                        onClick = onOpenVerbDrill
                    )
                }
                if (hasVocabDrill) {
                    VocabDrillEntryTile(
                        modifier = if (hasVerbDrill) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                        onClick = onOpenVocabDrill,
                        masteredCount = state.vocabSprint.vocabMasteredCount
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        DailyPracticeEntryTile(
            onClick = onOpenElite
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(R.string.home_legend), fontWeight = FontWeight.SemiBold)
        Text(text = "🌱 ${stringResource(R.string.home_legend_seed_growing_bloom)}")
        Text(text = "🥀 ${stringResource(R.string.home_legend_wilting_wilted_forgotten)}")
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { showMethod = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.home_how_training_works))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onPrimaryAction,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.home_continue_learning))
        }
    }

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
                TextButton(onClick = { showLockedLessonHint = false }) {
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
                        onSelectLesson(lessonId)
                    }
                }) {
                    Text(text = stringResource(R.string.home_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { earlyStartLessonId = null }) {
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

@Composable
fun PackageSelectorList(
    packs: List<LessonPack>,
    currentPackId: String?,
    onPackSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Select Package",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        packs.forEach { pack ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onPackSelected(pack.packId.value)
                        onDismiss()
                    }
                    .background(
                        if (pack.packId.value == currentPackId)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            Color.Transparent
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pack.displayName ?: pack.packId.value,
                    fontWeight = if (pack.packId.value == currentPackId) FontWeight.Bold else FontWeight.Normal
                )
                if (pack.packId.value == currentPackId) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text("✓", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun VerbDrillEntryTile(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(64.dp)
            .testTag("verb_drill_entry_tile")
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = stringResource(R.string.home_verb_drill), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun VocabDrillEntryTile(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    masteredCount: Int = 0
) {
    Card(
        modifier = modifier
            .height(64.dp)
            .testTag("vocab_drill_entry_tile")
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = stringResource(R.string.home_flashcards), fontWeight = FontWeight.SemiBold)
            }
            if (masteredCount > 0) {
                Text(
                    text = stringResource(R.string.format_mastered, masteredCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MasteryGreen
                )
            }
        }
    }
}

@Composable
fun DailyPracticeEntryTile(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.home_daily_practice),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.home_practice_all_sublessons),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.home_start),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun LessonTile(
    tile: LessonTileUi,
    flower: FlowerVisual?,
    onSelect: () -> Unit,
    onLockedClick: (() -> Unit)? = null
) {
    val isEmpty = tile.state == LessonTileState.EMPTY

    if (tile.state == LessonTileState.VERB_DRILL) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clickable(onClick = onSelect)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = stringResource(R.string.home_verb), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = stringResource(R.string.home_verb_drill),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        return
    }

    // Determine emoji and scale based on flower state
    val (emoji, scale) = when {
        isEmpty -> "●" to 1.0f  // gray dot for empty slots
        tile.state == LessonTileState.LOCKED -> "🔒" to 1.0f  // locked
        tile.state == LessonTileState.UNLOCKED -> "🔓" to 1.0f  // unlocked
        flower == null -> "🌱" to 1.0f  // seed default
        else -> FlowerCalculator.getEmoji(flower.state) to flower.scaleMultiplier
    }

    val masteryPercent = flower?.masteryPercent ?: 0f

    // Empty tiles use a faded container
    val containerColor = if (isEmpty) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isEmpty) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .then(
                if (isEmpty) Modifier
                else Modifier.clickable {
                    if (tile.state == LessonTileState.LOCKED) {
                        onLockedClick?.invoke()
                    } else {
                        onSelect()
                    }
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "${tile.index + 1}", fontWeight = FontWeight.SemiBold)
            Text(
                text = emoji,
                fontSize = (18 * scale).sp
            )
            // Show mastery percentage if > 0 (but not for locked/unlocked/empty states)
            if (masteryPercent > 0f && tile.state != LessonTileState.LOCKED && tile.state != LessonTileState.UNLOCKED && !isEmpty) {
                Text(
                    text = "${(masteryPercent * 100).toInt()}%",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun LanguageSelector(
    label: String,
    languages: List<Language>,
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
                    if (language.id.value != selectedLanguageId) onSelect(language.id.value)
                }
            )
        }
    }
}

fun buildLessonTiles(
    lessons: List<Lesson>,
    testMode: Boolean,
    lessonFlowers: Map<String, FlowerVisual>,
    selectedLessonId: String?,
    activePackLessonIds: List<String>?
): List<LessonTileUi> {
    // Filter lessons to only those in the active pack
    val packLessons = if (activePackLessonIds != null) {
        // Preserve the order from the pack's lesson list
        activePackLessonIds.mapNotNull { id -> lessons.firstOrNull { it.id.value == id } }
    } else {
        lessons
    }
    val total = 12
    val tiles = mutableListOf<LessonTileUi>()

    // Find the highest lesson with any progress (masteryPercent > 0)
    var lastLessonWithProgress = -1
    for (i in packLessons.indices) {
        val flower = lessonFlowers[packLessons[i].id.value]
        android.util.Log.d("GrammarMate", "buildLessonTiles: lesson $i (${packLessons[i].id}) -> masteryPercent=${flower?.masteryPercent}")
        if (flower != null && flower.masteryPercent > 0f) {
            lastLessonWithProgress = i
        }
    }
    android.util.Log.d("GrammarMate", "buildLessonTiles: lastLessonWithProgress=$lastLessonWithProgress, next UNLOCKED will be at index ${lastLessonWithProgress + 1}")

    for (i in 0 until total) {
        val lesson = packLessons.getOrNull(i)
        val state = when {
            // No lesson exists at this index in the pack - show empty slot
            lesson == null -> LessonTileState.EMPTY
            testMode -> LessonTileState.SEED
            i == 0 -> LessonTileState.SPROUT
            else -> {
                val currentFlower = lessonFlowers[lesson.id.value]

                when {
                    // Current lesson has progress - show flower
                    currentFlower != null && currentFlower.masteryPercent > 0f -> {
                        LessonTileState.SEED
                    }
                    // This is the lesson right after the last one with progress - UNLOCKED (open lock)
                    i == lastLessonWithProgress + 1 -> {
                        LessonTileState.UNLOCKED
                    }
                    // This lesson is before the last with progress - check if previous has progress
                    i < lastLessonWithProgress + 1 -> {
                        val prevLesson = packLessons.getOrNull(i - 1)
                        val prevFlower = prevLesson?.let { lessonFlowers[it.id.value] }
                        if (prevFlower != null && prevFlower.masteryPercent > 0f) {
                            LessonTileState.UNLOCKED
                        } else {
                            LessonTileState.LOCKED
                        }
                    }
                    // All other lessons are locked
                    else -> {
                        LessonTileState.LOCKED
                    }
                }
            }
        }
        tiles.add(LessonTileUi(i, lesson?.id?.value, state))
    }
    return tiles
}
