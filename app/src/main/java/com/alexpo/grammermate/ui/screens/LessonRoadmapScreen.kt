package com.alexpo.grammermate.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.FlowerCalculator
import com.alexpo.grammermate.data.SubLessonType
import com.alexpo.grammermate.data.TrainingUiState

sealed class RoadmapEntry {
    data class Training(val index: Int, val type: SubLessonType) : RoadmapEntry()
    object Drill : RoadmapEntry()
    object StoryCheckIn : RoadmapEntry()
    object StoryCheckOut : RoadmapEntry()
    object BossLesson : RoadmapEntry()
    object BossMega : RoadmapEntry()
}

fun buildRoadmapEntries(
    trainingTypes: List<SubLessonType>,
    hasMegaBoss: Boolean,
    cycleStart: Int = 0,
    hasDrill: Boolean = false
): List<RoadmapEntry> {
    val entries = mutableListOf<RoadmapEntry>()
    if (hasDrill) {
        entries.add(RoadmapEntry.Drill)
    }
    trainingTypes.forEachIndexed { index, type ->
        // Use absolute index for proper tracking
        entries.add(RoadmapEntry.Training(cycleStart + index, type))
    }
    entries.add(RoadmapEntry.BossLesson)
    if (hasMegaBoss) {
        entries.add(RoadmapEntry.BossMega)
    }
    return entries
}

@Composable
fun LessonRoadmapScreen(
    state: TrainingUiState,
    onBack: () -> Unit,
    onStartSubLesson: (Int) -> Unit,
    onStartBossLesson: () -> Unit,
    onStartBossMega: () -> Unit,
    onDrillStart: () -> Unit = {}
) {
    val lessonTitle = state.navigation.lessons
        .firstOrNull { it.id == state.navigation.selectedLessonId }
        ?.title
        ?: "Lesson"
    val fallbackTotal = state.cardSession.subLessonCount.coerceAtLeast(1)
    val fallbackNewOnlyCount = fallbackTotal.coerceAtMost(3)
    val trainingTypes = if (state.cardSession.subLessonTypes.isNotEmpty()) {
        state.cardSession.subLessonTypes
    } else {
        List(fallbackTotal) { index ->
            if (index < fallbackNewOnlyCount) SubLessonType.NEW_ONLY else SubLessonType.MIXED
        }
    }
    val total = trainingTypes.size.coerceAtLeast(1)
    val completed = state.cardSession.completedSubLessonCount.coerceIn(0, total)
    val currentIndex = completed.coerceIn(0, total - 1)
    var earlyStartSubLessonIndex by remember { mutableStateOf<Int?>(null) }

    // Calculate current cycle (block of 15)
    val currentCycle = completed / 15
    val cycleStart = currentCycle * 15
    val cycleEnd = minOf(cycleStart + 15, total)

    // Show only current cycle's sublessons (max 15 at a time)
    // If all sublessons are completed, show empty list
    val visibleTrainingTypes = if (cycleStart < total) {
        trainingTypes.subList(cycleStart, cycleEnd)
    } else {
        emptyList()
    }

    val lessonIndex = state.navigation.lessons.indexOfFirst { it.id == state.navigation.selectedLessonId }
    val hasMegaBoss = lessonIndex > 0
    val currentLesson = state.navigation.lessons.firstOrNull { it.id == state.navigation.selectedLessonId }
    val totalCards = currentLesson?.allCards?.size ?: 0
    val shownCards = state.flowerDisplay.currentLessonShownCount.coerceAtMost(totalCards)
    val bossLessonReward = state.navigation.selectedLessonId?.let { state.boss.bossLessonRewards[it] }
    val bossMegaReward = state.navigation.selectedLessonId?.let { state.boss.bossMegaRewards[it] }
    val bossUnlocked = state.cardSession.completedSubLessonCount >= 15 || state.cardSession.testMode
    var bossLockedMessage by remember { mutableStateOf<String?>(null) }
    val hasDrill = currentLesson?.drillCards?.isNotEmpty() == true
    val entries = buildRoadmapEntries(visibleTrainingTypes, hasMegaBoss, cycleStart, hasDrill)
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Show progress within current block of 15
            val displayIndex = (completed % 15) + 1
            val displayTotal = minOf(15, total - (completed / 15) * 15)
            Text(text = "Exercise $displayIndex of $displayTotal", textAlign = TextAlign.Center)
            Text(
                text = "Cards: $shownCards of $totalCards",
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
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
                        val canEnter = state.cardSession.testMode || isCompleted || isActive
                        val kindLabel = when (entry.type) {
                            SubLessonType.NEW_ONLY -> "NEW"
                            SubLessonType.MIXED -> "MIX"
                        }
                        // Use lesson flower for exercise tiles (they copy lesson state)
                        val flower = state.flowerDisplay.currentLessonFlower
                        val (emoji, scale) = when {
                            !isCompleted && !state.cardSession.testMode -> {
                                (if (isActive) "🔓" else "🔒") to 1.0f
                            }
                            flower == null -> "🌸" to 1.0f  // blossom
                            else -> FlowerCalculator.getEmoji(flower.state) to flower.scaleMultiplier
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clickable {
                                    if (canEnter) {
                                        onStartSubLesson(index)
                                    } else {
                                        earlyStartSubLessonIndex = index
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = "${index + 1}", fontWeight = FontWeight.SemiBold)
                                Text(text = emoji, fontSize = (18 * scale).sp)
                                Text(text = kindLabel, fontSize = 10.sp)
                            }
                        }
                    }
                    is RoadmapEntry.Drill -> {
                        DrillTile(onClick = onDrillStart, enabled = true)
                    }
                    is RoadmapEntry.BossLesson -> {
                        BossTile(
                            label = "Review",
                            enabled = bossUnlocked,
                            reward = if (bossUnlocked) bossLessonReward else null,
                            locked = !bossUnlocked,
                            onClick = if (bossUnlocked) onStartBossLesson else {
                                { bossLockedMessage = "Complete at least 15 exercises first" }
                            }
                        )
                    }
                    is RoadmapEntry.BossMega -> {
                        BossTile(
                            label = "Mega",
                            enabled = bossUnlocked,
                            reward = if (bossUnlocked) bossMegaReward else null,
                            locked = !bossUnlocked,
                            onClick = if (bossUnlocked) onStartBossMega else {
                                { bossLockedMessage = "Complete at least 15 exercises first" }
                            }
                        )
                    }
                    // StoryCheckIn/StoryCheckOut kept for backward compat but no longer rendered
                    is RoadmapEntry.StoryCheckIn -> { }
                    is RoadmapEntry.StoryCheckOut -> { }
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

    if (earlyStartSubLessonIndex != null) {
        val idx = earlyStartSubLessonIndex!!
        AlertDialog(
            onDismissRequest = { earlyStartSubLessonIndex = null },
            confirmButton = {
                TextButton(onClick = {
                    earlyStartSubLessonIndex = null
                    onStartSubLesson(idx)
                }) {
                    Text(text = "Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { earlyStartSubLessonIndex = null }) {
                    Text(text = "No")
                }
            },
            title = { Text(text = "Start early?") },
            text = { Text(text = "Start exercise ${idx + 1} early? You can always come back to review.") }
        )
    }

    if (bossLockedMessage != null) {
        AlertDialog(
            onDismissRequest = { bossLockedMessage = null },
            confirmButton = {
                TextButton(onClick = { bossLockedMessage = null }) {
                    Text(text = "OK")
                }
            },
            title = { Text(text = "Locked") },
            text = { Text(text = "Complete at least 15 exercises first.") }
        )
    }
}

@Composable
fun BossTile(label: String, enabled: Boolean, reward: BossReward?, locked: Boolean = false, onClick: () -> Unit) {
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
            if (locked) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = tint
                )
            }
        }
    }
}

@Composable
fun DrillTile(
    onClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.fillMaxWidth().height(72.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = "Drill",
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(4.dp))
            Text("Drill", fontSize = 12.sp)
        }
    }
}
