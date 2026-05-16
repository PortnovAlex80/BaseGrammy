package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.data.CardDifficultyRating
import com.alexpo.grammermate.data.PomodoroSessionStats
import com.alexpo.grammermate.data.PomodoroState

private val RingFillColor = Color(0xFF66BB6A)
private val DifficultyColors = mapOf(
    CardDifficultyRating.AGAIN to Color(0xFFEF9A9A),
    CardDifficultyRating.HARD to Color(0xFFFFCC80),
    CardDifficultyRating.GOOD to Color(0xFFA5D6A7),
    CardDifficultyRating.EASY to Color(0xFF90CAF9)
)

@Composable
fun PomodoroSummaryScreen(
    pomodoro: PomodoroState,
    currentStreak: Int,
    todayFireCount: Int,
    onDone: () -> Unit
) {
    val stats = pomodoro.stats
    val timeCompletedPercent = if (pomodoro.totalSeconds > 0)
        ((pomodoro.totalSeconds - pomodoro.remainingSeconds).toFloat() / pomodoro.totalSeconds * 100).toInt()
    else 100
    val isEarlyCompletion = pomodoro.remainingSeconds > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🎉", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Session Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        if (isEarlyCompletion) {
            Text(
                text = "Early completion!",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        CircularTimeRing(
            percent = timeCompletedPercent,
            remainingText = formatDuration(pomodoro.selectedDurationMinutes),
            modifier = Modifier.size(120.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        StatsGrid(stats, todayFireCount)
        Spacer(modifier = Modifier.height(16.dp))

        if (stats.difficultyRatings.isNotEmpty()) {
            DifficultyBreakdown(stats.difficultyRatings)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (currentStreak > 0 || todayFireCount > 0) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (currentStreak >= 7)
                        MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val fireEmoji = buildString {
                        repeat(todayFireCount.coerceAtMost(4)) { append("🔥") }
                    }
                    Text(text = fireEmoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$currentStreak day streak!",
                        fontWeight = FontWeight.Bold,
                        color = if (currentStreak >= 7) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun CircularTimeRing(
    percent: Int,
    remainingText: String,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.outline
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = RingFillColor,
                startAngle = -90f,
                sweepAngle = 360f * percent / 100f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = remainingText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(text = "$percent%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatsGrid(stats: PomodoroSessionStats, todayFireCount: Int) {
    val successRate = if (stats.cardsShown > 0)
        (stats.cardsCorrect * 100 / stats.cardsShown) else 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard("📊", "${stats.cardsShown}", "cards", Modifier.weight(1f))
        StatCard("✅", "$successRate%", "correct", Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard("🗣️", String.format("%.0f", stats.wordsPerMinute), "WPM", Modifier.weight(1f))
        StatCard("🔥", "$todayFireCount", "fires", Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(emoji: String, value: String, label: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = emoji, fontSize = 20.sp)
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DifficultyBreakdown(ratings: Map<CardDifficultyRating, Int>) {
    val total = ratings.values.sum().coerceAtLeast(1)

    Column {
        Text("Difficulty breakdown:", fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        CardDifficultyRating.entries.forEach { rating ->
            val count = ratings[rating] ?: 0
            if (count > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        text = rating.name.lowercase().replaceFirstChar { it.uppercase() },
                        modifier = Modifier.width(48.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(
                        progress = { count.toFloat() / total },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp),
                        color = DifficultyColors[rating]!!,
                        trackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = " $count",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(24.dp)
                    )
                }
            }
        }
    }
}

private fun formatDuration(minutes: Int): String {
    return String.format("%02d:00", minutes)
}
