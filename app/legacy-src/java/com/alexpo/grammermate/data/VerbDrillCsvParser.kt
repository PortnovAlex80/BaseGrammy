package com.alexpo.grammermate.data

import java.io.BufferedReader

object VerbDrillCsvParser {

    /** Pre-compiled regex to extract verb from parenthetical hint in promptRu. */
    private val PARENTHETICAL_VERB_REGEX = Regex("\\(([\\w]+)")

    /** Italian subject pronouns recognized at the start of the IT (answer) column. */
    private val PERSON_PRONOUNS = setOf("Io", "Tu", "Lui", "Lei", "Noi", "Voi", "Loro")

    /** Ordered list of persons for consistent dropdown display. */
    val PERSON_ORDER = listOf("Io", "Tu", "Lui", "Lei", "Noi", "Voi", "Loro")

    /** Extract Italian person pronoun from the first word of the answer string. */
    private fun extractPerson(answer: String): String? {
        val firstWord = answer.split(' ', '\t').firstOrNull()?.trim() ?: return null
        return if (firstWord in PERSON_PRONOUNS) firstWord else null
    }

    /**
     * Parse verb drill CSV content from a String.
     * Loads the entire content into memory — avoid for large files.
     */
    fun parse(content: String): ParseResult<List<VerbDrillCard>, ParseError> {
        val lines = content.lines()
        val cards = mutableListOf<VerbDrillCard>()
        val errors = mutableListOf<ParseError>()
        var title: String? = null
        var headerConsumed = false
        var ruIndex = -1
        var itIndex = -1
        var verbIndex = -1
        var tenseIndex = -1
        var groupIndex = -1
        var rankIndex = -1
        var dataRowIndex = 0
        var consecutiveEmptyLines = 0

        for ((lineIndex, rawLine) in lines.withIndex()) {
            val lineNumber = lineIndex + 1
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
                continue
            }
            consecutiveEmptyLines = 0

            if (title == null) {
                title = extractTitle(line)
                continue
            }

            if (!headerConsumed) {
                val columns = CsvLineParser.parseLine(line)
                columns.forEachIndexed { index, col ->
                    val trimmed = col.trim().trim('"')
                    when (trimmed.lowercase()) {
                        "ru" -> ruIndex = index
                        "it" -> itIndex = index
                        "verb" -> verbIndex = index
                        "tense" -> tenseIndex = index
                        "group" -> groupIndex = index
                        "rank" -> rankIndex = index
                    }
                }
                headerConsumed = true
                continue
            }

            if (ruIndex < 0 || itIndex < 0) {
                errors.add(
                    ParseError.MalformedLine(
                        lineNumber = lineNumber,
                        expected = "CSV header with 'ru' and 'it' columns",
                        actual = "missing required columns"
                    )
                )
                continue
            }

            val columns = CsvLineParser.parseLine(line)
            if (columns.size <= maxOf(ruIndex, itIndex)) {
                errors.add(
                    ParseError.MalformedLine(
                        lineNumber = lineNumber,
                        expected = "at least ${maxOf(ruIndex, itIndex) + 1} columns",
                        actual = "${columns.size} column(s): $line"
                    )
                )
                continue
            }

            val ru = columns[ruIndex].trim().trim('"')
            val answer = columns[itIndex].trim().trim('"')
            if (ru.isBlank() || answer.isBlank()) {
                errors.add(
                    ParseError.MalformedLine(
                        lineNumber = lineNumber,
                        expected = "non-empty RU and IT columns",
                        actual = "RU='${ru}', IT='${answer}'"
                    )
                )
                continue
            }

            val verb = if (verbIndex >= 0 && columns.size > verbIndex) {
                columns[verbIndex].trim().trim('"').ifBlank { null }
            } else null
            val tense = if (tenseIndex >= 0 && columns.size > tenseIndex) {
                columns[tenseIndex].trim().trim('"').ifBlank { null }
            } else null
            val group = if (groupIndex >= 0 && columns.size > groupIndex) {
                columns[groupIndex].trim().trim('"').ifBlank { null }
            } else null
            val rank = if (rankIndex >= 0 && columns.size > rankIndex) {
                columns[rankIndex].trim().trim('"').toIntOrNull()
            } else null

            // Fallback: extract verb from parenthetical hint in promptRu
            // e.g. "я устал (essere stanco)" → "essere"
            // e.g. "я хочу есть (avere fame)" → "avere"
            val resolvedVerb = if (verb == null && ru.contains("(")) {
                PARENTHETICAL_VERB_REGEX.find(ru)?.groupValues?.get(1)
            } else verb

            val id = "${group ?: ""}_${tense ?: ""}_$dataRowIndex"
            cards.add(
                VerbDrillCard(
                    id = id,
                    promptRu = ru,
                    answer = answer,
                    verb = resolvedVerb,
                    tense = tense,
                    group = group,
                    person = extractPerson(answer),
                    rank = rank
                )
            )
            dataRowIndex += 1
        }

        return when {
            cards.isEmpty() && errors.isEmpty() -> ParseResult.failure(
                listOf(ParseError.EmptyFile(lineNumber = 0))
            )
            cards.isEmpty() -> ParseResult.failure(errors)
            errors.isEmpty() -> ParseResult.success(cards)
            else -> ParseResult.partial(cards, errors)
        }
    }

    /**
     * Streaming parse from a BufferedReader — reads one line at a time
     * to avoid loading the entire file into a single String (OOM-safe).
     * The caller is responsible for closing the reader (e.g. via .use { }).
     */
    fun parse(reader: BufferedReader): ParseResult<List<VerbDrillCard>, ParseError> {
        val cards = mutableListOf<VerbDrillCard>()
        val errors = mutableListOf<ParseError>()
        var title: String? = null
        var headerConsumed = false
        var ruIndex = -1
        var itIndex = -1
        var verbIndex = -1
        var tenseIndex = -1
        var groupIndex = -1
        var rankIndex = -1
        var dataRowIndex = 0
        var lineNumber = 0
        var consecutiveEmptyLines = 0

        reader.forEachLine { rawLine ->
            lineNumber++
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
                return@forEachLine
            }
            consecutiveEmptyLines = 0

            if (title == null) {
                title = extractTitle(line)
                return@forEachLine
            }

            if (!headerConsumed) {
                val columns = CsvLineParser.parseLine(line)
                columns.forEachIndexed { index, col ->
                    val trimmed = col.trim().trim('"')
                    when (trimmed.lowercase()) {
                        "ru" -> ruIndex = index
                        "it" -> itIndex = index
                        "verb" -> verbIndex = index
                        "tense" -> tenseIndex = index
                        "group" -> groupIndex = index
                        "rank" -> rankIndex = index
                    }
                }
                headerConsumed = true
                return@forEachLine
            }

            if (ruIndex < 0 || itIndex < 0) {
                errors.add(
                    ParseError.MalformedLine(
                        lineNumber = lineNumber,
                        expected = "CSV header with 'ru' and 'it' columns",
                        actual = "missing required columns"
                    )
                )
                return@forEachLine
            }

            val columns = CsvLineParser.parseLine(line)
            if (columns.size <= maxOf(ruIndex, itIndex)) {
                errors.add(
                    ParseError.MalformedLine(
                        lineNumber = lineNumber,
                        expected = "at least ${maxOf(ruIndex, itIndex) + 1} columns",
                        actual = "${columns.size} column(s): $line"
                    )
                )
                return@forEachLine
            }

            val ru = columns[ruIndex].trim().trim('"')
            val answer = columns[itIndex].trim().trim('"')
            if (ru.isBlank() || answer.isBlank()) {
                errors.add(
                    ParseError.MalformedLine(
                        lineNumber = lineNumber,
                        expected = "non-empty RU and IT columns",
                        actual = "RU='${ru}', IT='${answer}'"
                    )
                )
                return@forEachLine
            }

            val verb = if (verbIndex >= 0 && columns.size > verbIndex) {
                columns[verbIndex].trim().trim('"').ifBlank { null }
            } else null
            val tense = if (tenseIndex >= 0 && columns.size > tenseIndex) {
                columns[tenseIndex].trim().trim('"').ifBlank { null }
            } else null
            val group = if (groupIndex >= 0 && columns.size > groupIndex) {
                columns[groupIndex].trim().trim('"').ifBlank { null }
            } else null
            val rank = if (rankIndex >= 0 && columns.size > rankIndex) {
                columns[rankIndex].trim().trim('"').toIntOrNull()
            } else null

            val resolvedVerb = if (verb == null && ru.contains("(")) {
                PARENTHETICAL_VERB_REGEX.find(ru)?.groupValues?.get(1)
            } else verb

            val id = "${group ?: ""}_${tense ?: ""}_$dataRowIndex"
            cards.add(
                VerbDrillCard(
                    id = id,
                    promptRu = ru,
                    answer = answer,
                    verb = resolvedVerb,
                    tense = tense,
                    group = group,
                    person = extractPerson(answer),
                    rank = rank
                )
            )
            dataRowIndex += 1
        }

        return when {
            cards.isEmpty() && errors.isEmpty() -> ParseResult.failure(
                listOf(ParseError.EmptyFile(lineNumber = 0))
            )
            cards.isEmpty() -> ParseResult.failure(errors)
            errors.isEmpty() -> ParseResult.success(cards)
            else -> ParseResult.partial(cards, errors)
        }
    }

    private fun extractTitle(raw: String): String? {
        val trimmed = raw.trim().trim('"').trimStart('\uFEFF')
        if (trimmed.isBlank()) return null
        val builder = StringBuilder()
        for (ch in trimmed) {
            if (ch.isLetterOrDigit() || ch == ' ') {
                builder.append(ch)
            } else {
                break
            }
            if (builder.length >= 160) break
        }
        return builder.toString().trim().ifBlank { null }
    }
}
