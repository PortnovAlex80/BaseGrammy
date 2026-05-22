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
                if (columns.size != 2) {
                    errors.add(
                        ParseError.MalformedLine(
                            lineNumber = lineNumber,
                            expected = "2 columns (RU, answers)",
                            actual = "${columns.size} column(s): $line"
                        )
                    )
                    return@forEach
                }

                val ru = columns[0].trim().trim('"')
                val answersRaw = columns[1]
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
                        acceptedAnswers = answers
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

    private fun extractTitle(raw: String): String? {
        val trimmed = raw.trim().trim('"').trimStart('\uFEFF')
        if (trimmed.isBlank()) return null
        val builder = StringBuilder()
        for (ch in trimmed) {
            if (ch.isLetterOrDigit() || ch == ' ' || ch == '-' || ch == '.' || ch == ',') {
                builder.append(ch)
            } else {
                break
            }
            if (builder.length >= 160) break
        }
        return builder.toString().trim().ifBlank { null }
    }
}
