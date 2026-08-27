package com.alexpo.grammermate.v2.core.data.packimport

import java.io.InputStream

/**
 * Парсер Lesson CSV → [SentenceCard] (SRS-002 FR-3, AC-7).
 *
 * Переносится 1:1 из legacy (`com.alexpo.grammermate.data.CsvParser`) как `object`
 * (pure Kotlin, без `android.util.Log`). Контракт:
 * - Строка 1 — заголовок/название урока (`extractTitle`, `.trimStart('\uFEFF')`, `.take(160)`).
 * - Каждая последующая строка — ровно 2 колонки (`CsvLineParser.parseLine`, semicolon-delimited):
 *   `RU | answers`; иначе [ParseError.MalformedLine] с `expected="2 columns"`.
 * - Ответы разделяются `+`: `answersRaw.split("+").map { it.trim().trim('"') }.filter { it.isNotBlank() }`.
 * - ID карточки: `"card_$lineNumber"` (lineNumber после строки заголовка; `card_1` НЕ существует).
 * - `consecutiveEmptyLines > 3` → [ParseError.MalformedLine].
 */
object CsvParser {

    /**
     * Распарсить Lesson CSV в заголовок + список [SentenceCard] с partial-error семантикой.
     *
     * @param inputStream поток CSV (UTF-8, с возможным BOM).
     * @return [ParseResult] с `Pair<title, cards>`; partial — корректные карты + ошибки.
     */
    fun parseLesson(inputStream: InputStream): ParseResult<Pair<String, List<SentenceCard>>, ParseError> {
        val reader = inputStream.bufferedReader()
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
                                actual = "empty line",
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
                            actual = "${columns.size} column(s): $line",
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
                            actual = "RU='$ru', answers='$answersRaw'",
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
                            actual = "answersRaw='$answersRaw'",
                        )
                    )
                    return@forEach
                }

                cards.add(
                    SentenceCard(
                        id = "card_$lineNumber",
                        promptRu = ru,
                        acceptedAnswers = answers,
                    )
                )
            }
        }

        return when {
            cards.isEmpty() && errors.isEmpty() -> ParseResult.failure(
                listOf(ParseError.EmptyFile(lineNumber = 0))
            )
            cards.isEmpty() -> ParseResult.failure(errors)
            errors.isEmpty() -> ParseResult.success(Pair(title ?: "Lesson", cards))
            else -> ParseResult.partial(Pair(title ?: "Lesson", cards), errors)
        }
    }

    /** Заголовок урока из строки 1: trim + снятие кавычек/BOM + ограничение 160 символов. */
    private fun extractTitle(raw: String): String? {
        val trimmed = raw.trim().trim('"').trimStart('\uFEFF')
        return trimmed.take(160).trim().ifBlank { null }
    }
}
