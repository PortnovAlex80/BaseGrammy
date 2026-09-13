package com.alexpo.grammermate.data

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object CsvParser {
    fun parseLesson(inputStream: InputStream): ParseResult<Pair<String, List<SentenceCard>>, ParseError> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val cards = mutableListOf<SentenceCard>()
        val errors = mutableListOf<ParseError>()
        var lineNumber = 0
        var title: String? = null
        var titleConsumed = false
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

                if (!titleConsumed) {
                    title = extractTitle(line)
                    titleConsumed = true
                    return@forEach
                }

                val columns = CsvLineParser.parseLine(line)
                if (columns.size !in 2..3) {
                    errors.add(
                        ParseError.MalformedLine(
                            lineNumber = lineNumber,
                            expected = "2 or 3 columns (RU, answers[, context])",
                            actual = "${columns.size} column(s): $line"
                        )
                    )
                    return@forEach
                }

                val ru = columns[0].trim().trim('"')
                val answersRaw = columns[1]
                // Optional 3rd column: situation context shown with the prompt
                val contextRu = columns.getOrNull(2)?.trim()?.trim('"')?.takeIf { it.isNotBlank() }
                if (ru.isBlank() || answersRaw.isBlank()) {
                    errors.add(
                        ParseError.MalformedLine(
                            lineNumber = lineNumber,
                            expected = "non-empty RU and answers",
                            actual = "RU='${ru}', answers='${answersRaw}'"
                        )
                    )
                    return@forEach
                }

                val answers = answersRaw.split("+")
                    .map { it.trim().trim('"') }
                    .filter { it.isNotBlank() }
                if (answers.isEmpty()) {
                    errors.add(
                        ParseError.MalformedLine(
                            lineNumber = lineNumber,
                            expected = "at least one valid answer after splitting by '+'",
                            actual = "answersRaw='${answersRaw}'"
                        )
                    )
                    return@forEach
                }

                cards.add(
                    SentenceCard(
                        id = "card_$lineNumber",
                        promptRu = ru,
                        acceptedAnswers = answers,
                        contextRu = contextRu
                    )
                )
            }
        }

        val result: ParseResult<Pair<String, List<SentenceCard>>, ParseError> = when {
            cards.isEmpty() && errors.isEmpty() -> ParseResult.failure(
                listOf(ParseError.EmptyFile(lineNumber = 0))
            )
            cards.isEmpty() -> ParseResult.failure(errors)
            errors.isEmpty() -> ParseResult.success(Pair(title ?: "Lesson", cards))
            else -> ParseResult.partial(Pair(title ?: "Lesson", cards), errors)
        }
        return result
    }

    /**
     * Parse ONLY the lesson title from line 1 of CSV.
     * Does NOT read or parse any card data - for lazy loading.
     * Returns null if file is empty or title is blank.
     */
    fun parseLessonTitle(inputStream: InputStream): String? {
        inputStream.bufferedReader().useLines { lines ->
            val firstLine = lines.firstOrNull()?.trim() ?: return null
            if (firstLine.isBlank()) return null
            return extractTitle(firstLine)
        }
    }

    private fun extractTitle(raw: String): String? {
        val trimmed = raw.trim().trim('"').trimStart('\uFEFF')
        return trimmed.take(160).trim().ifBlank { null }
    }
}
