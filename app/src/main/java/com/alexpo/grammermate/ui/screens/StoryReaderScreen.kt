package com.alexpo.grammermate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.alexpo.grammermate.data.MultilingualStoryParser
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState

/**
 * Simple markdown parser for story content.
 *
 * Supports basic markdown syntax:
 * - Headers: # H1, ## H2, ### H3
 * - Bold: **text**
 * - Italic: *text*
 * - Lists: * item or - item
 * - Code blocks: ```code```
 * - Line breaks: Empty lines
 */
object SimpleMarkdownParser {

    @Composable
    fun RenderMarkdown(
        markdown: String,
        modifier: Modifier = Modifier
    ) {
        val lines = markdown.split("\n")
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            var inCodeBlock = false
            var codeContent = StringBuilder()

            for (line in lines) {
                when {
                    // Code block handling
                    line.trim().startsWith("```") -> {
                        if (inCodeBlock) {
                            // End code block
                            CodeBlock(text = codeContent.toString())
                            codeContent.clear()
                            inCodeBlock = false
                        } else {
                            // Start code block
                            inCodeBlock = true
                        }
                    }
                    inCodeBlock -> {
                        codeContent.append(line).append("\n")
                    }
                    // Headers
                    line.startsWith("# ") -> {
                        Header1(text = line.substring(2).trim())
                    }
                    line.startsWith("## ") -> {
                        Header2(text = line.substring(3).trim())
                    }
                    line.startsWith("### ") -> {
                        Header3(text = line.substring(4).trim())
                    }
                    // Lists
                    line.trim().startsWith("* ") || line.trim().startsWith("- ") -> {
                        ListItem(text = line.trim().substring(2).trim())
                    }
                    // Empty line
                    line.isBlank() -> {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    // Regular text
                    else -> {
                        Paragraph(text = line)
                    }
                }
            }

            // Handle unclosed code block
            if (inCodeBlock && codeContent.isNotEmpty()) {
                CodeBlock(text = codeContent.toString())
            }
        }
    }

    @Composable
    private fun Header1(text: String) {
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }

    @Composable
    private fun Header2(text: String) {
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 6.dp)
        )
    }

    @Composable
    private fun Header3(text: String) {
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }

    @Composable
    private fun Paragraph(text: String) {
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }

    @Composable
    private fun ListItem(text: String) {
        Text(
            text = "• ${parseInlineMarkdown(text)}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 2.dp, horizontal = 16.dp)
        )
    }

    @Composable
    private fun CodeBlock(text: String) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = text.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    @Composable
    private fun parseInlineMarkdown(text: String): String {
        var result = text

        // Bold: **text** or __text__
        result = result.replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        result = result.replace(Regex("""__(.+?)__"""), "$1")

        // Italic: *text* or _text_ (but not if part of **)
        result = result.replace(Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)"""), "$1")
        result = result.replace(Regex("""(?<!_)_(?!_)(.+?)(?<!_)_(?!_)"""), "$1")

        return result
    }
}

/**
 * Screen for reading story content with markdown rendering.
 *
 * Displays the chapter title and markdown story content in a scrollable view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryReaderScreen(
    chapterTitle: String,
    markdownContent: String,
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Roadmap")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
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
                    if (isPlaying && isStoryPaused) {
                        // Paused state: show Resume + Stop
                        IconButton(onClick = onResumeStory) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Resume story"
                            )
                        }
                        IconButton(onClick = onStopStory) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop story"
                            )
                        }
                    } else if (isPlaying) {
                        // Playing state: show Pause
                        IconButton(onClick = onPauseStory) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = "Pause story"
                            )
                        }
                    } else {
                        // Idle state: show Play
                        IconButton(onClick = onPlayStory) {
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
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}