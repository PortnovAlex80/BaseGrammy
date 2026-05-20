package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.PomodoroHistoryEntry
import com.alexpo.grammermate.data.PomodoroPreset
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroSelectorSheet(
    showSheet: Boolean,
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit,
    lastDuration: Int,
    history: List<PomodoroHistoryEntry> = emptyList()
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedMinutes by remember(lastDuration) { mutableIntStateOf(lastDuration) }
    var customMinutes by remember { mutableIntStateOf(25) }
    var showStats by remember { mutableStateOf(false) }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = { showStats = !showStats },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = if (showStats) Icons.Default.ArrowBack else Icons.Default.Insights,
                            contentDescription = if (showStats) "Back to timer" else "Pomodoro stats"
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_tomato),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (showStats) "Pomodoro Stats" else "Pomodoro Training",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (showStats) "Last 7 days" else "Focus. Practice. Grow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (showStats) {
                    PomodoroWeeklyStats(history = history)
                    Spacer(modifier = Modifier.height(16.dp))
                    return@Column
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PomodoroPreset.entries.forEach { preset ->
                        val isSelected = selectedMinutes == preset.minutes
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedMinutes = preset.minutes }
                                .then(
                                    if (isSelected) Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(12.dp)
                                    ) else Modifier
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${preset.minutes} min",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Custom:", style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { customMinutes = (customMinutes - 1).coerceAtLeast(1) }) {
                        Text("−", fontSize = 20.sp)
                    }
                    Text(
                        text = "$customMinutes",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { customMinutes = (customMinutes + 1).coerceAtMost(60) }) {
                        Text("+", fontSize = 20.sp)
                    }
                    Text("min", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { selectedMinutes = customMinutes }) {
                        Text("Set")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onStart(selectedMinutes) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tomato),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private data class PomodoroDayStat(
    val label: String,
    val sessions: Int,
    val cards: Int,
    val correct: Int,
    val minutes: Int
)

@Composable
private fun PomodoroWeeklyStats(history: List<PomodoroHistoryEntry>) {
    val stats = remember(history) { buildWeeklyStats(history) }
    val totalSessions = stats.sumOf { it.sessions }
    val totalCards = stats.sumOf { it.cards }
    val totalCorrect = stats.sumOf { it.correct }
    val totalMinutes = stats.sumOf { it.minutes }
    val accuracy = if (totalCards > 0) totalCorrect * 100 / totalCards else 0

    if (totalSessions == 0) {
        Text(
            text = "No Pomodoro sessions yet for this language.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }

    PomodoroBarChart(stats)
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatChip("Sessions", totalSessions.toString(), Modifier.weight(1f))
        StatChip("Cards", totalCards.toString(), Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatChip("Accuracy", "$accuracy%", Modifier.weight(1f))
        StatChip("Focus", "${totalMinutes}m", Modifier.weight(1f))
    }
}

@Composable
private fun PomodoroBarChart(stats: List<PomodoroDayStat>) {
    if (stats.isEmpty()) return

    val maxCards = stats.maxOfOrNull { it.cards }?.coerceAtLeast(1) ?: 1
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val gap = 8.dp.toPx()
            val labelHeight = 20.dp.toPx()
            val chartHeight = size.height - labelHeight
            val availableWidth = size.width - gap * (stats.size - 1)
            if (chartHeight <= 0f || availableWidth <= 0f) return@Canvas
            val barWidth = availableWidth / stats.size
            if (barWidth <= 0f) return@Canvas
            stats.forEachIndexed { index, day ->
                val left = index * (barWidth + gap)
                if (left >= 0f && left <= size.width) {
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, chartHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    val fillHeight = (chartHeight * day.cards / maxCards).coerceIn(0f, chartHeight)
                    drawRoundRect(
                        color = if (day.cards > 0) barColor else Color.Transparent,
                        topLeft = Offset(left, chartHeight - fillHeight),
                        size = Size(barWidth, fillHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            stats.forEach { day ->
                Text(
                    text = day.label,
                    color = labelColor,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun buildWeeklyStats(history: List<PomodoroHistoryEntry>): List<PomodoroDayStat> {
    val today = Calendar.getInstance()
    val days = (6 downTo 0).map { offset ->
        Calendar.getInstance().apply {
            timeInMillis = today.timeInMillis
            add(Calendar.DAY_OF_YEAR, -offset)
        }
    }
    val grouped = history.groupBy {
        val cal = Calendar.getInstance().apply { timeInMillis = it.completedAtMs }
        String.format("%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH))
    }
    return days.mapIndexed { idx, day ->
        val cal = day
        val key = String.format("%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH))
        val entries = grouped[key].orEmpty()
        val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val dayOfWeekIndex = (cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, dayNames.lastIndex)
        PomodoroDayStat(
            label = dayNames[dayOfWeekIndex],
            sessions = entries.size,
            cards = entries.sumOf { it.cardsShown.coerceAtLeast(0) },
            correct = entries.sumOf { it.cardsCorrect.coerceAtLeast(0) },
            minutes = entries.sumOf { ((it.totalSeconds - it.remainingSeconds).coerceAtLeast(0) + 59) / 60 }
        )
    }
}
