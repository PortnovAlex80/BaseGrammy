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
import com.alexpo.grammermate.data.MultilingualStoryParser
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
 * Simple markdown parser for story content.
 *
 * Supports basic markdown syntax:
 * - Headers: # H1, ## H2, ### H3
 * - Bold: **text**
 * - Italic: *text*
 * - Lists: * item or - item
 * - Code blocks: ```code```
 * - Tables: | Header | Header | with |---|---| separator
 * - Line breaks: Empty lines
 */
object SimpleMarkdownParser {

    @Composable
    fun RenderMarkdown(
        markdown: String,
        modifier: Modifier = Modifier,
        textScale: Float = 1.0f
    ) {
        val lines = markdown.split("\n")
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            var inCodeBlock = false
            var codeContent = StringBuilder()
            var i = 0

            while (i < lines.size) {
                val line = lines[i]

                when {
                    // Code block handling
                    line.trim().startsWith("```") -> {
                        if (inCodeBlock) {
                            CodeBlock(text = codeContent.toString(), textScale = textScale)
                            codeContent.clear()
                            inCodeBlock = false
                        } else {
                            inCodeBlock = true
                        }
                        i++
                    }
                    inCodeBlock -> {
                        codeContent.append(line).append("\n")
                        i++
                    }
                    // Table detection: line starts with '|' and next line is separator
                    line.trim().startsWith("|") && isTableSeparatorLine(lines.getOrElse(i + 1) { "" }) -> {
                        val tableLines = mutableListOf<String>()
                        var j = i
                        while (j < lines.size && lines[j].trim().startsWith("|")) {
                            tableLines.add(lines[j])
                            j++
                        }
                        val headerRow = parseTableRow(tableLines.getOrElse(0) { "" })
                        val dataRows = tableLines.drop(2)
                            .filter { it.trim().isNotEmpty() }
                            .map { parseTableRow(it) }

                        MarkdownTable(
                            header = headerRow,
                            rows = dataRows,
                            textScale = textScale
                        )
                        i = j
                    }
                    // Headers
                    line.startsWith("# ") -> {
                        Header1(text = line.substring(2).trim(), textScale = textScale)
                        i++
                    }
                    line.startsWith("## ") -> {
                        Header2(text = line.substring(3).trim(), textScale = textScale)
                        i++
                    }
                    line.startsWith("### ") -> {
                        Header3(text = line.substring(4).trim(), textScale = textScale)
                        i++
                    }
                    // Lists
                    line.trim().startsWith("* ") || line.trim().startsWith("- ") -> {
                        ListItem(text = line.trim().substring(2).trim(), textScale = textScale)
                        i++
                    }
                    // Empty line
                    line.isBlank() -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        i++
                    }
                    // Regular text
                    else -> {
                        Paragraph(text = line, textScale = textScale)
                        i++
                    }
                }
            }

            // Handle unclosed code block
            if (inCodeBlock && codeContent.isNotEmpty()) {
                CodeBlock(text = codeContent.toString(), textScale = textScale)
            }
        }
    }

    @Composable
    private fun Header1(text: String, textScale: Float = 1.0f) {
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = (24f * textScale).sp),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }

    @Composable
    private fun Header2(text: String, textScale: Float = 1.0f) {
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = (20f * textScale).sp),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 6.dp)
        )
    }

    @Composable
    private fun Header3(text: String, textScale: Float = 1.0f) {
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = (18f * textScale).sp),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }

    @Composable
    private fun Paragraph(text: String, textScale: Float = 1.0f) {
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = (16f * textScale).sp),
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }

    @Composable
    private fun ListItem(text: String, textScale: Float = 1.0f) {
        Text(
            text = "• ${parseInlineMarkdown(text)}",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = (16f * textScale).sp),
            modifier = Modifier.padding(vertical = 2.dp, horizontal = 16.dp)
        )
    }

    @Composable
    private fun CodeBlock(text: String, textScale: Float = 1.0f) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = text.trim(),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = (14f * textScale).sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    // --- Table support ---

    /**
     * Checks if a line is a markdown table separator (e.g. |---|---|---|).
     * Supports alignment markers: :---, :---:, ---:
     */
    private fun isTableSeparatorLine(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.startsWith("|")) return false
        // Split and check each cell is only dashes/colons
        val cells = trimmed.trim('|').split("|")
        return cells.isNotEmpty() && cells.all { cell ->
            val cleaned = cell.trim()
            cleaned.all { it == '-' || it == ':' } && cleaned.count { it == '-' } >= 1
        }
    }

    /**
     * Parses a single table row like "| Cell 1 | Cell 2 | Cell 3 |" into a list of cell strings.
     */
    private fun parseTableRow(line: String): List<String> {
        return line.trim()
            .trim('|')
            .split("|")
            .map { it.trim() }
    }

    @Composable
    private fun MarkdownTable(
        header: List<String>,
        rows: List<List<String>>,
        textScale: Float = 1.0f
    ) {
        val borderColor = MaterialTheme.colorScheme.outline
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(
                    BorderStroke(1.dp, borderColor),
                    shape = MaterialTheme.shapes.small
                )
        ) {
            // Header row
            if (header.isNotEmpty()) {
                TableRow(
                    cells = header,
                    isHeader = true,
                    textScale = textScale
                )
                HorizontalDivider(color = borderColor, thickness = 1.dp)
            }
            // Data rows
            rows.forEachIndexed { index, row ->
                TableRow(
                    cells = row,
                    isHeader = false,
                    textScale = textScale
                )
                if (index < rows.size - 1) {
                    HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }
        }
    }

    @Composable
    private fun TableRow(
        cells: List<String>,
        isHeader: Boolean = false,
        textScale: Float = 1.0f
    ) {
        val bgColor = if (isHeader) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            cells.forEachIndexed { index, cell ->
                TableCell(
                    text = cell,
                    isHeader = isHeader,
                    textScale = textScale,
                    bgColor = bgColor,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (index < cells.size - 1) {
                                Modifier.border(
                                    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                )
                            } else {
                                Modifier
                            }
                        )
                )
            }
        }
    }

    @Composable
    private fun TableCell(
        text: String,
        isHeader: Boolean = false,
        textScale: Float = 1.0f,
        bgColor: Color = MaterialTheme.colorScheme.surface,
        modifier: Modifier = Modifier
    ) {
        Surface(
            color = bgColor,
            modifier = modifier
        ) {
            Text(
                text = parseInlineMarkdown(text),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = (14f * textScale).sp),
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
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
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Roadmap")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            ScreenLogger.tap("story_copy")
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