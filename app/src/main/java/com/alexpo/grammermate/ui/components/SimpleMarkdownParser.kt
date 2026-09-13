package com.alexpo.grammermate.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        // маркер добавляем внутри AnnotatedString: интерполяция в строку
        // сбросила бы стили обратно в плоский текст
        val body = parseInlineMarkdown(text)
        Text(
            text = buildAnnotatedString {
                append("• ")
                append(body)
            },
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

    // Порядок важен: **жирный** проверяется раньше *курсива*, иначе двойная
    // звёздочка разберётся как две одинарные.
    private val INLINE_RULES = listOf(
        Regex("""\*\*(.+?)\*\*""") to InlineStyle.BOLD,
        Regex("""__(.+?)__""") to InlineStyle.BOLD,
        Regex("""`([^`]+)`""") to InlineStyle.CODE,
        Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""") to InlineStyle.ITALIC,
        Regex("""(?<!_)_(?!_)(.+?)(?<!_)_(?!_)""") to InlineStyle.ITALIC,
    )

    private enum class InlineStyle { BOLD, ITALIC, CODE }

    /**
     * Разбирает строчную разметку в [AnnotatedString].
     *
     * Раньше эта функция просто вырезала символы разметки и возвращала обычный
     * String: `**текст**` терял звёздочки, но жирным не становился, а обратные
     * кавычки не обрабатывались вовсе и показывались как есть. Грамматические
     * чипы используют `код` в 114 строках, поэтому разметка была видна глазом.
     */
    @Composable
    private fun parseInlineMarkdown(text: String): AnnotatedString {
        val codeColor = MaterialTheme.colorScheme.primary
        return buildInline(text, codeColor)
    }

    /** internal, а не private, ради модульного теста — как ReviewSelector.drawRun. */
    internal fun buildInline(text: String, codeColor: Color): AnnotatedString =
        buildAnnotatedString {
            var rest = text
            while (rest.isNotEmpty()) {
                val hit = INLINE_RULES
                    .mapNotNull { (re, style) -> re.find(rest)?.let { it to style } }
                    .minByOrNull { it.first.range.first }
                if (hit == null) {
                    append(rest)
                    return@buildAnnotatedString
                }
                val (match, style) = hit
                append(rest.substring(0, match.range.first))
                val inner = match.groupValues[1]
                when (style) {
                    InlineStyle.BOLD ->
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inner) }
                    InlineStyle.ITALIC ->
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inner) }
                    InlineStyle.CODE ->
                        withStyle(
                            SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor)
                        ) { append(inner) }
                }
                rest = rest.substring(match.range.last + 1)
            }
        }
}
