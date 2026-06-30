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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import android.widget.Toast
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import android.os.Handler
import android.os.Looper
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.Chapter
import com.alexpo.grammermate.data.ChapterProgress
import com.alexpo.grammermate.ui.ChapterCardUi
import com.alexpo.grammermate.ui.ChapterStatus
import com.alexpo.grammermate.ui.MasteryGreen
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.shared.ScreenLogger

/** Wraps onClick with a 150 ms delay so the Material ripple animation completes before navigation. */
private fun delayedClick(onClick: () -> Unit): () -> Unit = {
    Handler(Looper.getMainLooper()).postDelayed(onClick, 150)
}

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
    onPlayChapterStory: (Chapter) -> Unit = {},
    onVerbPractice: () -> Unit,
    onAuxDrill: () -> Unit = {},
    onFlashcards: () -> Unit,
    onDailyPractice: () -> Unit,
    onBackgroundVocab: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    hasVerbDrill: Boolean = false,
    hasVocabDrill: Boolean = false,
    showBackButton: Boolean = false,  // Default to false - shown on HOME route
    isStoryPlaying: Boolean = false,
    isStoryPaused: Boolean = false,
    onStopStory: () -> Unit = {},
    onPauseStory: () -> Unit = {},
    onResumeStory: () -> Unit = {}
) {
    @OptIn(ExperimentalMaterial3Api::class)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grammar Story Roadmap") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = delayedClick {
                            AuditLogger.getInstanceOrNull()?.backPress("story_roadmap")
                            onBack()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    // Story playback controls in top bar
                    if (isStoryPlaying && isStoryPaused) {
                        // Paused: show Resume + Stop
                        IconButton(onClick = {
                            ScreenLogger.tap("story_resume")
                            onResumeStory()
                        }) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Resume story")
                        }
                        IconButton(onClick = {
                            ScreenLogger.tap("story_stop")
                            onStopStory()
                        }) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop story")
                        }
                    } else if (isStoryPlaying) {
                        // Playing: show Pause + Stop
                        IconButton(onClick = {
                            ScreenLogger.tap("story_pause")
                            onPauseStory()
                        }) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause story")
                        }
                        IconButton(onClick = {
                            ScreenLogger.tap("story_stop")
                            onStopStory()
                        }) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop story")
                        }
                    }
                    IconButton(onClick = delayedClick {
                        AuditLogger.getInstanceOrNull()?.settingsOpen("story_roadmap")
                        onOpenSettings()
                    }) {
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
                                    onClick = {
                                        ScreenLogger.tap("drill_start", details = "type=verb")
                                        onVerbPractice()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Verb Practice")
                                }
                            }
                            if (hasVerbDrill) {
                                OutlinedButton(
                                    onClick = {
                                        ScreenLogger.tap("drill_start", details = "type=aux")
                                        onAuxDrill()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.aux_drill_entry))
                                }
                            }
                            if (hasVocabDrill) {
                                OutlinedButton(
                                    onClick = {
                                        ScreenLogger.tap("drill_start", details = "type=vocab")
                                        onFlashcards()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Flashcards")
                                }
                            }
                        }
                    }

                    val dailyContext = LocalContext.current
                    OutlinedButton(
                        onClick = {
                            ScreenLogger.tap("daily_practice")
                            Toast.makeText(dailyContext, "Временно не доступно", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().alpha(0.5f) // temporarily disabled
                    ) {
                        Text("Daily Practice")
                    }

                    // Background Vocab Listener entry — passive listening deck.
                    // Visible only when a pack is selected (this whole screen is only shown
                    // when activePack != null and the pack has chapters), per CLAUDE.md
                    // conditional-visibility rule.
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
            }

            // Chapter list
            items(chapters) { chapterUi ->
                ChapterCard(
                    chapterUi = chapterUi,
                    onReadStory = {
                        ScreenLogger.tap("read_story", details = "chapter=${chapterUi.chapter.title}")
                        AuditLogger.getInstanceOrNull()?.storyRead(chapterUi.chapter.chapterId, chapterUi.chapter.title)
                        onReadStory(chapterUi.chapter)
                    },
                    onContinue = {
                        ScreenLogger.tap("continue_lesson", details = "chapter=${chapterUi.chapter.chapterId}")
                        val nextLessonId = chapterUi.chapter.lessons.firstOrNull() ?: ""
                        AuditLogger.getInstanceOrNull()?.lessonSelect(nextLessonId, chapterUi.chapter.chapterId)
                        onContinue(chapterUi.chapter)
                    },
                    onPlayStory = {
                        ScreenLogger.tap("story_play", details = "chapter=${chapterUi.chapter.chapterId}")
                        onPlayChapterStory(chapterUi.chapter)
                    }
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
    onContinue: () -> Unit,
    onPlayStory: () -> Unit = {}
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                // Play story button
                if (chapterUi.chapter.storyFile != null) {
                    IconButton(onClick = onPlayStory) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Play story",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
                        onClick = delayedClick(onReadStory),
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