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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexpo.grammermate.data.Chapter
import com.alexpo.grammermate.data.ChapterProgress
import com.alexpo.grammermate.ui.ChapterCardUi
import com.alexpo.grammermate.ui.ChapterStatus
import com.alexpo.grammermate.ui.MasteryGreen

/**
 * Screen displaying the grammar story roadmap with chapters.
 *
 * Shows a list of chapters with progress bars, status icons, and action buttons.
 * Chapters are displayed in order with appropriate status indicators.
 */
@Composable
fun GrammarStoryRoadmapScreen(
    chapters: List<ChapterCardUi>,
    onBack: () -> Unit,
    onReadStory: (Chapter) -> Unit,
    onContinue: (Chapter) -> Unit,
    onVerbPractice: () -> Unit,
    onFlashcards: () -> Unit,
    onDailyPractice: () -> Unit,
    onOpenSettings: () -> Unit = {},
    hasVerbDrill: Boolean = false,
    hasVocabDrill: Boolean = false,
    showBackButton: Boolean = false  // Default to false - shown on HOME route
) {
    @OptIn(ExperimentalMaterial3Api::class)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grammar Story Roadmap") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Global practice buttons
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Practice Options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Show drill buttons only if drills are available
                    if (hasVerbDrill || hasVocabDrill) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (hasVerbDrill) {
                                OutlinedButton(
                                    onClick = onVerbPractice,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Verb Practice")
                                }
                            }
                            if (hasVocabDrill) {
                                OutlinedButton(
                                    onClick = onFlashcards,
                                    modifier = if (hasVerbDrill) Modifier.weight(1f) else Modifier.fillMaxWidth()
                                ) {
                                    Text("Flashcards")
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onDailyPractice,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Daily Practice")
                    }
                }
            }

            // Chapter list
            items(chapters) { chapterUi ->
                ChapterCard(
                    chapterUi = chapterUi,
                    onReadStory = { onReadStory(chapterUi.chapter) },
                    onContinue = { onContinue(chapterUi.chapter) }
                )
            }
        }
    }
}

/**
 * Card displaying a single chapter with its progress and actions.
 */
@Composable
private fun ChapterCard(
    chapterUi: ChapterCardUi,
    onReadStory: () -> Unit,
    onContinue: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (chapterUi.status) {
                ChapterStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                ChapterStatus.DONE -> MaterialTheme.colorScheme.tertiaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with title and subtitle
            Column {
                Text(
                    text = chapterUi.chapter.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                chapterUi.chapter.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            // Progress section
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Progress",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${chapterUi.progress.lessonsCompleted}/${chapterUi.chapter.lessons.size} lessons",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearProgressIndicator(
                        progress = {
                            if (chapterUi.chapter.lessons.isNotEmpty()) {
                                chapterUi.progress.lessonsCompleted.toFloat() / chapterUi.chapter.lessons.size
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = when (chapterUi.status) {
                            ChapterStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                            ChapterStatus.DONE -> MasteryGreen
                        }
                    )
                }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "Read Story" button (only if story file exists)
                if (chapterUi.chapter.storyFile != null) {
                    OutlinedButton(
                        onClick = onReadStory,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Read Story")
                    }
                }

                // "Continue" button (only show if chapter has lessons)
                if (chapterUi.chapter.lessons.isNotEmpty()) {
                    when (chapterUi.status) {
                        ChapterStatus.ACTIVE -> {
                            OutlinedButton(
                                onClick = onContinue,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Continue")
                            }
                        }
                        ChapterStatus.DONE -> {
                            OutlinedButton(
                                onClick = onContinue,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Review")
                            }
                        }
                    }
                }
            }
        }
    }
}