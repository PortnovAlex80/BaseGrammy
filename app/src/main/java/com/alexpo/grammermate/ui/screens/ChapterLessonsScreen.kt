package com.alexpo.grammermate.ui.screens

import com.alexpo.grammermate.data.ChapterCardUi
import com.alexpo.grammermate.data.ChapterStatus
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.data.ChapterProgress
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.ui.MasteryGreen

/**
 * Screen displaying lessons within a chapter as a grid of cards.
 *
 * Shows all lessons in a chapter with progress indicators and completion status.
 * Each lesson card shows title, progress, and mastery status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterLessonsScreen(
    chapterTitle: String,
    chapterSubtitle: String,
    chapterId: String = "",
    lessons: List<Lesson>,
    chapterProgress: ChapterProgress?,
    completedLessonIds: Set<String> = emptySet(),
    onLessonClick: (Lesson) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chapterTitle) },
                navigationIcon = {
                    IconButton(onClick = {
                        AuditLogger.getInstanceOrNull()?.backPress("chapter_lessons")
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Chapter header
            Column {
                Text(
                    text = chapterSubtitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Story-only chapters (totalLessons == 0) show no progress row.
                if (chapterProgress != null && chapterProgress.totalLessons > 0) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { chapterProgress.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Progress text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${chapterProgress.lessonsCompleted}/${chapterProgress.totalLessons} lessons completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${(chapterProgress.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MasteryGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Lessons grid — rows of 2 with equal height per row
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(lessons.chunked(2)) { rowLessons ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        rowLessons.forEach { lesson ->
                            LessonGridCard(
                                lesson = lesson,
                                isCompleted = lesson.id.value in completedLessonIds,
                                onClick = {
                                    AuditLogger.getInstanceOrNull()?.lessonSelect(lesson.id.value, chapterId)
                                    onLessonClick(lesson)
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        if (rowLessons.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonGridCard(
    lesson: Lesson,
    isCompleted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                // Lesson number from ID (e.g., "lesson_01_A01" -> "A01")
                val lessonCode = lesson.id.value.substringAfterLast("_").take(3)

                Text(
                    text = lessonCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = lesson.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Completion status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isCompleted) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Circle
                        },
                        contentDescription = null,
                        tint = if (isCompleted) {
                            MasteryGreen
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        },
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = if (isCompleted) "Completed" else "In progress",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCompleted) {
                            MasteryGreen
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                }
            }
        }
    }
}