package com.alexpo.grammermate.v2.core.data.packimport

import java.io.BufferedReader

/**
 * Парсер Verb CSV → [VerbDrillCard] (SRS-002 FR-4, AC-8).
 *
 * Переносится 1:1 из legacy (`com.alexpo.grammermate.data.VerbDrillCsvParser`).
 * Контракт:
 * - Строка 1 — title (`extractTitle`, буквенно-цифровой префикс до 160 символов).
 * - Строка 2 — header с колонками `ru`, `it` (обязательны), опционально `verb`, `tense`,
 *   `group`, `rank`. Индексы колонок детектируются по header.
 * - Каждая data-row: минимум `maxOf(ruIndex, itIndex)+1` колонок; `ru`/`it` непусты.
 * - `verb` fallback: если `verb` колонка пуста и `ru.contains("(")`, извлекается через
 *   `Regex("\\(([\\w]+)")` (например, `"я устал (essere stanco)"` → `"essere"`).
 * - `person`: извлекается из первого слова answer (`Io/Tu/Lui/Lei/Noi/Voi/Loro` из `PERSON_ORDER`).
 * - ID: `"${group ?: ""}_${tense ?: ""}_$dataRowIndex"`.
 *
 * SCAFFOLD: контракт зафиксирован SRS-002 FR-4 / §5.5; реализация — TODO (AC-8).
 * Legacy-тест `VerbDrillCsvParserTest` переносится в v2 без изменения утверждений (NFR-6).
 *
 * @see <a href="../../../../../../../../docs/requirements/REQ-002-data-room/02-srs.md">SRS-002 FR-4</a>
 */
object VerbDrillCsvParser {

    /** Порядок лиц для извлечения `person` из первого слова answer. */
    val PERSON_ORDER: List<String> = listOf("Io", "Tu", "Lui", "Lei", "Noi", "Voi", "Loro")

    /**
     * Распарсить Verb CSV в список [VerbDrillCard].
     *
     * @param content текст CSV (UTF-8, без BOM ожидается после extractTitle).
     * @return [ParseResult] с `List<VerbDrillCard>`; partial — корректные карты + ошибки.
     */
    fun parse(content: String): ParseResult<List<VerbDrillCard>, ParseError> =
        parse(content.reader().buffered())

    /**
     * Стриминг-вариант: парсит CSV из [reader] построчно (для больших файлов).
     */
    fun parse(reader: BufferedReader): ParseResult<List<VerbDrillCard>, ParseError> {
        val cards = mutableListOf<VerbDrillCard>()
        val errors = mutableListOf<ParseError>()
        var lineNumber = 0

        reader.use { r ->
            val titleLine = r.readLine()?.trim()
            lineNumber = 1
            val headerLine = r.readLine()?.trim()
            lineNumber = 2
            if (titleLine == null || headerLine == null) {
                return ParseResult.failure(listOf(ParseError.EmptyFile(lineNumber)))
            }

            val header = CsvLineParser.parseLine(headerLine).map { it.trim().lowercase() }
            val ruIdx = header.indexOf("ru")
            val itIdx = header.indexOf("it")
            if (ruIdx < 0 || itIdx < 0) {
                return ParseResult.failure(
                    listOf(
                        ParseError.InvalidFormat(
                            lineNumber = 2,
                            reason = "header обязан содержать колонки 'ru' и 'it', получено: $header",
                        )
                    )
                )
            }
            val verbIdx = header.indexOf("verb")
            val tenseIdx = header.indexOf("tense")
            val groupIdx = header.indexOf("group")
            val rankIdx = header.indexOf("rank")
            val minColumns = maxOf(ruIdx, itIdx) + 1

            var dataRowIndex = 0
            r.lineSequence().forEach { rawLine ->
                lineNumber += 1
                val line = rawLine.trim()
                if (line.isBlank()) return@forEach

                val columns = CsvLineParser.parseLine(line)
                if (columns.size < minColumns) {
                    errors += ParseError.MalformedLine(
                        lineNumber = lineNumber,
                        expected = ">= $minColumns columns (ru, it)",
                        actual = "${columns.size} column(s): $line",
                    )
                    return@forEach
                }
                val ru = columns[ruIdx].trim().trim('"')
                val it = columns[itIdx].trim().trim('"')
                if (ru.isBlank() || it.isBlank()) {
                    errors += ParseError.MalformedLine(
                        lineNumber = lineNumber,
                        expected = "non-empty ru and it",
                        actual = "ru='$ru', it='$it'",
                    )
                    return@forEach
                }

                var verb = columnAt(columns, verbIdx)
                if (verb.isNullOrBlank() && ru.contains("(")) {
                    verb = verbFallbackRegex.find(ru)?.groupValues?.get(1)
                }
                val tense = columnAt(columns, tenseIdx)
                val group = columnAt(columns, groupIdx)
                val rank = columnAt(columns, rankIdx)?.toIntOrNull()
                val person = PERSON_ORDER.firstOrNull { p ->
                    it.trim().startsWith(p, ignoreCase = true)
                }
                val id = "${group.orEmpty()}_${tense.orEmpty()}_$dataRowIndex"

                cards += VerbDrillCard(
                    id = id,
                    promptRu = ru,
                    answer = it,
                    verb = verb,
                    tense = tense,
                    group = group,
                    person = person,
                    rank = rank,
                )
                dataRowIndex += 1
            }
        }

        return when {
            cards.isEmpty() && errors.isEmpty() ->
                ParseResult.failure(listOf(ParseError.EmptyFile(lineNumber)))
            cards.isEmpty() -> ParseResult.failure(errors)
            errors.isEmpty() -> ParseResult.success(cards)
            else -> ParseResult.partial(cards, errors)
        }
    }

    /** Значение колонки по индексу (null — колонки нет в файле или пусто). */
    private fun columnAt(columns: List<String>, index: Int): String? =
        columns.getOrNull(index)?.trim()?.trim('"')?.takeIf { it.isNotBlank() }

    /** `verb` fallback: первое слово в скобках ("я устал (essere stanco)" → "essere"). */
    private val verbFallbackRegex = Regex("""\(([\w]+)""")
}
