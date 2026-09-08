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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.FlowerCalculator
import com.alexpo.grammermate.data.FlowerVisual
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.SubLessonType
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.shared.AuditLogger

sealed class RoadmapEntry {
    data class Training(val index: Int, val type: SubLessonType) : RoadmapEntry()
    object StoryCheckIn : RoadmapEntry()
    object StoryCheckOut : RoadmapEntry()
    object BossLesson : RoadmapEntry()
    object BossMega : RoadmapEntry()
}

fun buildRoadmapEntries(
    trainingTypes: List<SubLessonType>,
    hasMegaBoss: Boolean,
    cycleStart: Int = 0
): List<RoadmapEntry> {
    val entries = mutableListOf<RoadmapEntry>()
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
    onReview: (HintLevel) -> Unit = {},
    onNextLesson: () -> Unit = {}
) {
    val lessonTitle = state.navigation.lessons
        .firstOrNull { it.id == state.navigation.selectedLessonId }
        ?.title
        ?: stringResource(R.string.roadmap_lesson_fallback)
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
    val bossLessonReward = state.navigation.selectedLessonId?.let { state.boss.bossLessonRewards[it.value] }
    val bossMegaReward = state.navigation.selectedLessonId?.let { state.boss.bossMegaRewards[it.value] }
    val noOp: () -> Unit = { }
    val entries = buildRoadmapEntries(visibleTrainingTypes, hasMegaBoss, cycleStart)
    val isLessonComplete = completed >= total
    val bossUnlocked = isLessonComplete || state.cardSession.testMode
    var showDifficultyDialog by remember { mutableStateOf(false) }

    // Next lesson info
    val nextLessonExists = lessonIndex >= 0 && lessonIndex < state.navigation.lessons.lastIndex
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
            IconButton(onClick = { AuditLogger.getInstanceOrNull()?.backPress("lesson_roadmap"); onBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.roadmap_back))
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
            Text(text = stringResource(R.string.roadmap_exercise_of, displayIndex, displayTotal), textAlign = TextAlign.Center)
            Text(
                text = stringResource(R.string.roadmap_cards_of, shownCards, totalCards),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (isLessonComplete) {
            // ── CompletionCard ──
            CompletionCard(
                flower = state.flowerDisplay.currentLessonFlower,
                onNextLesson = if (nextLessonExists) ({ AuditLogger.getInstanceOrNull()?.lessonSelect(state.navigation.lessons.getOrNull(lessonIndex + 1)?.id?.value ?: "", state.activeChapterId ?: ""); onNextLesson() }) else null,
                onReview = { AuditLogger.getInstanceOrNull()?.dialogAction("repeat_lesson", "open"); showDifficultyDialog = true }
            )
        } else {
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
                                SubLessonType.NEW_ONLY -> stringResource(R.string.roadmap_new)
                                SubLessonType.MIXED -> stringResource(R.string.roadmap_mix)
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
                                            AuditLogger.getInstanceOrNull()?.lessonSelect(
                                                state.navigation.selectedLessonId?.value ?: "",
                                                state.activeChapterId ?: ""
                                            )
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
                        is RoadmapEntry.BossLesson -> {
                            BossTile(
                                label = stringResource(R.string.roadmap_review),
                                enabled = bossUnlocked,
                                reward = if (bossUnlocked) bossLessonReward else null,
                                locked = !bossUnlocked,
                                onClick = if (bossUnlocked) ({ AuditLogger.getInstanceOrNull()?.bossStart("lesson", state.navigation.selectedLessonId?.value ?: ""); onStartBossLesson() }) else noOp
                            )
                        }
                        is RoadmapEntry.BossMega -> {
                            BossTile(
                                label = stringResource(R.string.roadmap_mega),
                                enabled = bossUnlocked,
                                reward = if (bossUnlocked) bossMegaReward else null,
                                locked = !bossUnlocked,
                                onClick = if (bossUnlocked) ({ AuditLogger.getInstanceOrNull()?.bossStart("mega", state.navigation.selectedLessonId?.value ?: ""); onStartBossMega() }) else noOp
                            )
                        }
                        // StoryCheckIn/StoryCheckOut kept for backward compat but no longer rendered
                        is RoadmapEntry.StoryCheckIn -> { }
                        is RoadmapEntry.StoryCheckOut -> { }
                    }
                }
            }
        }

        // Boss and Drill tiles always visible below CompletionCard or grid
        if (isLessonComplete) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BossTile(
                    label = stringResource(R.string.roadmap_review),
                    enabled = bossUnlocked,
                    reward = if (bossUnlocked) bossLessonReward else null,
                    locked = !bossUnlocked,
                    onClick = if (bossUnlocked) ({ AuditLogger.getInstanceOrNull()?.bossStart("lesson", state.navigation.selectedLessonId?.value ?: ""); onStartBossLesson() }) else noOp
                )
                if (hasMegaBoss) {
                    BossTile(
                        label = stringResource(R.string.roadmap_mega),
                        enabled = bossUnlocked,
                        reward = if (bossUnlocked) bossMegaReward else null,
                        locked = !bossUnlocked,
                        onClick = if (bossUnlocked) ({ AuditLogger.getInstanceOrNull()?.bossStart("mega", state.navigation.selectedLessonId?.value ?: ""); onStartBossMega() }) else noOp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (!isLessonComplete) {
            Button(
                onClick = { AuditLogger.getInstanceOrNull()?.lessonSelect(state.navigation.selectedLessonId?.value ?: "", state.activeChapterId ?: ""); onStartSubLesson(currentIndex) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (completed == 0) stringResource(R.string.roadmap_start_lesson) else stringResource(R.string.roadmap_continue_lesson))
            }
        }
    }

    if (earlyStartSubLessonIndex != null) {
        val idx = earlyStartSubLessonIndex!!
        AlertDialog(
            onDismissRequest = { earlyStartSubLessonIndex = null },
            confirmButton = {
                TextButton(onClick = {
                    AuditLogger.getInstanceOrNull()?.dialogAction("early_start", "yes")
                    earlyStartSubLessonIndex = null
                    onStartSubLesson(idx)
                }) {
                    Text(text = stringResource(R.string.home_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { AuditLogger.getInstanceOrNull()?.dialogAction("early_start", "no"); earlyStartSubLessonIndex = null }) {
                    Text(text = stringResource(R.string.home_no))
                }
            },
            title = { Text(text = stringResource(R.string.roadmap_start_early_title)) },
            text = { Text(text = stringResource(R.string.roadmap_start_early_message, idx + 1)) }
        )
    }

    if (showDifficultyDialog) {
        DifficultySelectionDialog(
            onConfirm = { hintLevel ->
                AuditLogger.getInstanceOrNull()?.dialogAction("difficulty_select", hintLevel.name)
                showDifficultyDialog = false
                onReview(hintLevel)
            },
            onDismiss = { showDifficultyDialog = false }
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
private fun CompletionCard(
    flower: FlowerVisual?,
    onNextLesson: (() -> Unit)?,
    onReview: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val (emoji, scale) = if (flower != null) {
                FlowerCalculator.getEmoji(flower.state) to flower.scaleMultiplier
            } else {
                "🌸" to 1.0f // 🌸
            }
            Text(text = emoji, fontSize = (18 * scale).sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.roadmap_all_complete),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (onNextLesson != null) {
                FilledTonalButton(
                    onClick = onNextLesson,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.roadmap_next_lesson))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = onReview,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.roadmap_repeat))
            }
        }
    }
}

@Composable
private fun DifficultySelectionDialog(
    onConfirm: (HintLevel) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.roadmap_difficulty_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DifficultyRow(
                    label = stringResource(R.string.settings_easy),
                    description = stringResource(R.string.settings_easy_description),
                    onClick = { onConfirm(HintLevel.EASY) }
                )
                DifficultyRow(
                    label = stringResource(R.string.settings_medium),
                    description = stringResource(R.string.settings_medium_description),
                    onClick = { onConfirm(HintLevel.MEDIUM) }
                )
                DifficultyRow(
                    label = stringResource(R.string.settings_hard),
                    description = stringResource(R.string.settings_hard_description),
                    onClick = { onConfirm(HintLevel.HARD) }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.settings_cancel))
            }
        }
    )
}

@Composable
private fun DifficultyRow(
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(text = label, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
