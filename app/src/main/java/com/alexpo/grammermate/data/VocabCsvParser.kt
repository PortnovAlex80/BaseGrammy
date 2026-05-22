package com.alexpo.grammermate.data

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class VocabRow(
    val nativeText: String,
    val targetText: String,
    val isHard: Boolean
)

object VocabCsvParser {
    fun parse(inputStream: InputStream): ParseResult<List<VocabRow>, ParseError> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val rows = mutableListOf<VocabRow>()
        val errors = mutableListOf<ParseError>()
        var lineNumber = 0
        var consecutiveEmptyLines = 0

        reader.useLines { lines ->
            lines.forEach { rawLine ->
                lineNumber += 1
                val line = rawLine.trim()

                if (line.isBlank()) {
                    consecutiveEmptyLines++
                    if (consecutiveEmptyLines > 3) {
                        errors.add(
                            ParseError.MalformedLine(
                                lineNumber = lineNumber,
                                expected = "non-empty line or data",
                                actual = "empty line"
                            )
                        )
                    }
                    return@forEach
                }
                consecutiveEmptyLines = 0

                val columns = CsvLineParser.parseLine(line)
                if (columns.size < 2) {
                    errors.add(
                        ParseError.MalformedLine(
                            lineNumber = lineNumber,
                            expected = "at least 2 columns (native, target)",
                            actual = "${columns.size} column(s): $line"
                        )
                    )
                    return@forEach
                }

                val nativeText = columns[0].trim().trim('"')
                val targetText = columns[1].trim().trim('"')
                if (nativeText.isBlank() || targetText.isBlank()) {
                    errors.add(
                        ParseError.MalformedLine(
                            lineNumber = lineNumber,
                            expected = "non-empty native and target text",
                            actual = "native='${nativeText}', target='${targetText}'"
                        )
                    )
                    return@forEach
                }

                val hardRaw = columns.getOrNull(2)?.trim()?.trim('"').orEmpty()
                val isHard = hardRaw.equals("hard", ignoreCase = true) ||
                    hardRaw.equals("1", ignoreCase = true) ||
                    hardRaw.equals("true", ignoreCase = true)

                rows.add(VocabRow(nativeText, targetText, isHard))
            }
        }

        return when {
            rows.isEmpty() && errors.isEmpty() -> ParseResult.failure(
                listOf(ParseError.EmptyFile(lineNumber = 0))
            )
            rows.isEmpty() -> ParseResult.failure(errors)
            errors.isEmpty() -> ParseResult.success(rows)
            else -> ParseResult.partial(rows, errors)
        }
    }

}
