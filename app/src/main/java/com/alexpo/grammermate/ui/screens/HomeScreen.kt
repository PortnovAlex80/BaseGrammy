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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.data.FlowerCalculator
import com.alexpo.grammermate.data.FlowerVisual
import com.alexpo.grammermate.data.Language
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingUiState

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
    onOpenMixChallenge: () -> Unit = {}
) {
    val tiles = remember(state.navigation.selectedLanguageId, state.navigation.lessons, state.cardSession.testMode, state.flowerDisplay.lessonFlowers, state.navigation.selectedLessonId, state.navigation.activePackId, state.navigation.activePackLessonIds) {
        buildLessonTiles(state.navigation.lessons, state.cardSession.testMode, state.flowerDisplay.lessonFlowers, state.navigation.selectedLessonId, state.navigation.activePackLessonIds)
    }
    var showMethod by remember { mutableStateOf(false) }
    var showLockedLessonHint by remember { mutableStateOf(false) }
    var earlyStartLessonId by remember { mutableStateOf<String?>(null) }
    val languageCode = state.navigation.languages
        .firstOrNull { it.id == state.navigation.selectedLanguageId }
        ?.id
        ?.uppercase()
        ?: "--"
    val isFirstLaunch = state.cardSession.correctCount == 0 &&
        state.cardSession.incorrectCount == 0 &&
        state.cardSession.activeTimeMs == 0L
    val activePackDisplayName = state.navigation.installedPacks
        .firstOrNull { it.packId == state.navigation.activePackId }
        ?.displayName
    val primaryLabel = when {
        state.cardSession.sessionState == SessionState.ACTIVE -> activePackDisplayName ?: "Continue Learning"
        isFirstLaunch -> activePackDisplayName ?: "Start learning"
        else -> activePackDisplayName ?: "Start learning"
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

    val nextHint = if (state.navigation.lessons.isNotEmpty()) {
        "Lesson ${currentLessonIndex + 1}. Exercise $currentLessonProgress"
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
                        text = getUserInitials(state.navigation.userName),
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = state.navigation.userName, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LanguageSelector(
                    label = languageCode,
                    languages = state.navigation.languages,
                    selectedLanguageId = state.navigation.selectedLanguageId,
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
        Text(text = "Grammar Roadmap", fontWeight = FontWeight.SemiBold)
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
        MixChallengeEntryTile(
            onClick = onOpenMixChallenge
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Legend:", fontWeight = FontWeight.SemiBold)
        Text(text = "🌱 seed • 🌿 growing • 🌸 bloom")
        Text(text = "🥀 wilting • 🍂 wilted • ⚫ forgotten")
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { showMethod = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "How This Training Works")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onPrimaryAction,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Continue Learning")
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
    if (showLockedLessonHint) {
        AlertDialog(
            onDismissRequest = { showLockedLessonHint = false },
            confirmButton = {
                TextButton(onClick = { showLockedLessonHint = false }) {
                    Text(text = "OK")
                }
            },
            title = { Text(text = "Lesson locked") },
            text = { Text(text = "Please complete the previous lesson first.") }
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
                    Text(text = "Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { earlyStartLessonId = null }) {
                    Text(text = "No")
                }
            },
            title = { Text(text = "Start early?") },
            text = { Text(text = "Start this lesson early? You can always come back to review previous lessons.") }
        )
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
                Text(text = "Verb Drill", fontWeight = FontWeight.SemiBold)
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
                Text(text = "Flashcards", fontWeight = FontWeight.SemiBold)
            }
            if (masteredCount > 0) {
                Text(
                    text = "$masteredCount mastered",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32)
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
                    text = "Daily Practice",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Practice all sub-lessons",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Start",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun MixChallengeEntryTile(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE3F2FD)
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
                    text = "Mix Challenge",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1565C0)
                )
                Text(
                    text = "Interleaved practice across tenses",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF1565C0).copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Default.SwapHoriz,
                contentDescription = "Start",
                tint = Color(0xFF1565C0)
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
                Text(text = "Verb", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = "Verb Drill",
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
                    if (language.id != selectedLanguageId) onSelect(language.id)
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
        activePackLessonIds.mapNotNull { id -> lessons.firstOrNull { it.id == id } }
    } else {
        lessons
    }
    val total = 12
    val tiles = mutableListOf<LessonTileUi>()

    // Find the highest lesson with any progress (masteryPercent > 0)
    var lastLessonWithProgress = -1
    for (i in packLessons.indices) {
        val flower = lessonFlowers[packLessons[i].id]
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
                val currentFlower = lessonFlowers[lesson.id]

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
                        val prevFlower = prevLesson?.let { lessonFlowers[it.id] }
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
        tiles.add(LessonTileUi(i, lesson?.id, state))
    }
    return tiles
}
