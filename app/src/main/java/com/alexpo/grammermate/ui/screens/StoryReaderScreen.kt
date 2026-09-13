package com.alexpo.grammermate.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.platform.LocalContext
import com.alexpo.grammermate.ui.components.SimpleMarkdownParser
import com.alexpo.grammermate.data.MultilingualStoryParser
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.shared.ScreenLogger
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState


/**
 * Screen for reading story content with markdown rendering.
 *
 * Displays the chapter title and markdown story content in a scrollable view.
 */

/** Wraps onClick with a 150 ms delay so the Material ripple animation completes before navigation. */
private fun delayedClick(onClick: () -> Unit): () -> Unit = {
    Handler(Looper.getMainLooper()).postDelayed(onClick, 150)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryReaderScreen(
    chapterTitle: String,
    markdownContent: String,
    textScale: Float = 1.0f,
    onBack: () -> Unit,
    onPlayStory: () -> Unit = {},
    onStopStory: () -> Unit = {},
    onPauseStory: () -> Unit = {},
    onResumeStory: () -> Unit = {},
    isPlaying: Boolean = false,
    isStoryPaused: Boolean = false
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chapterTitle) },
                navigationIcon = {
                    IconButton(onClick = delayedClick {
                        ScreenLogger.tap("story_back")
                        AuditLogger.getInstanceOrNull()?.backPress("story_reader", "back")
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Roadmap")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            ScreenLogger.tap("story_copy")
                            AuditLogger.getInstanceOrNull()?.dialogAction("story_copy", "copy")
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val cleanText = MultilingualStoryParser.stripMarkers(markdownContent)
                            clipboard.setPrimaryClip(ClipData.newPlainText("story", cleanText))
                            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy story to clipboard"
                        )
                    }
                    // 3-state playback controls: idle / playing / paused
                    // Layout order: Stop (left) + Resume/Pause/Play (right)
                    // This keeps Resume in the same tap target as Pause, so a
                    // finger that just tapped Pause lands on Resume (safe), not Stop.
                    if (isPlaying && isStoryPaused) {
                        // Paused state: show Stop + Resume
                        // Stop is on the left (far from the Pause tap target)
                        IconButton(onClick = {
                            ScreenLogger.tap("story_stop")
                            AuditLogger.getInstanceOrNull()?.storyControl("STOP", chapterTitle, null)
                            onStopStory()
                        }) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop story"
                            )
                        }
                        // Resume is on the right (same position as Pause was)
                        IconButton(onClick = {
                            ScreenLogger.tap("story_resume")
                            AuditLogger.getInstanceOrNull()?.storyControl("PLAY", chapterTitle, null)
                            onResumeStory()
                        }) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Resume story"
                            )
                        }
                    } else if (isPlaying) {
                        // Playing state: show Pause
                        IconButton(onClick = {
                            ScreenLogger.tap("story_pause")
                            AuditLogger.getInstanceOrNull()?.storyControl("PAUSE", chapterTitle, null)
                            onPauseStory()
                        }) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = "Pause story"
                            )
                        }
                    } else {
                        // Idle state: show Play
                        IconButton(onClick = {
                            ScreenLogger.tap("story_play")
                            AuditLogger.getInstanceOrNull()?.storyControl("PLAY", chapterTitle, null)
                            onPlayStory()
                        }) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Play story"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            val hasMarkers = com.alexpo.grammermate.data.MultilingualStoryParser.hasMarkers(markdownContent)
            val displayContent = if (hasMarkers) {
                android.util.Log.d("StoryReader", "Stripping markers: ${markdownContent.length} chars")
                com.alexpo.grammermate.data.MultilingualStoryParser.stripMarkers(markdownContent)
            } else {
                markdownContent
            }
            android.util.Log.d("StoryReader", "render: hasMarkers=$hasMarkers, final=${displayContent.length} chars")
            SimpleMarkdownParser.RenderMarkdown(
                markdown = displayContent,
                modifier = Modifier.fillMaxWidth(),
                textScale = textScale
            )
        }
    }
}