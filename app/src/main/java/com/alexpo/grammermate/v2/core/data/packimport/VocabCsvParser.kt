package com.alexpo.grammermate.v2.core.data.packimport

import java.io.InputStream

/**
 * Парсер Vocab CSV → [VocabRow] (SRS-002 FR-6, AC-9; Фаза 5).
 *
 * Формат строки: минимум 2 колонки `native, target` (native = RU-перевод,
 * target = изучаемое слово); опционально 3-я `hard`.
 * `isHard` = true, если 3-я колонка `"hard"|"1"|"true"` (case-insensitive).
 *
 * Разрешение аудита H-5 (задокументированное отклонение от legacy-контракта):
 * модель [VocabRow] требует `pos`/`rank`, которых в строках нет —
 *  - `pos` берётся из ИМЕНИ файла: `vocab_nouns.csv` → `"nouns"`
 *    (fallback `"misc"`); потому сигнатура принимает [fileName];
 *  - `rank` = порядковый индекс data-строки (файлы словаря уже отсортированы
 *    по частотности — тот же принцип, что и выборка `getVocabWords`);
 *  - `collocations`/`forms` этим форматом не заполняются (их носитель —
 *    BgVocab CSV);
 *  - ID `"${pos}_${rank}_${word}"` остаётся файло-локальным: глобальная
 *    уникальность обеспечивает ADR-003 (pack-scoped PK в `word_mastery`).
 */
object VocabCsvParser {

    /**
     * Распарсить Vocab CSV в список [VocabRow].
     *
     * @param inputStream поток CSV (UTF-8, возможен BOM — снимается).
     * @param fileName    имя файла (`vocab_<pos>.csv`) — источник `pos`.
     * @return [ParseResult] с `List<VocabRow>`; partial — корректные строки + ошибки.
     */
    fun parse(inputStream: InputStream, fileName: String): ParseResult<List<VocabRow>, ParseError> {
        // pos валиден только для файлов с префиксом vocab_ (vocab_nouns.csv →
        // "nouns"); файлы без префикса (words.csv) → "misc", а не имя файла.
        val baseName = fileName.substringAfterLast('/').substringBeforeLast('.')
        val pos = baseName.takeIf { it.startsWith("vocab_") }
            ?.removePrefix("vocab_")
            ?.takeIf { it.isNotBlank() }
            ?: "misc"

        val rows = mutableListOf<VocabRow>()
        val errors = mutableListOf<ParseError>()
        var lineNumber = 0

        inputStream.bufferedReader().useLines { lines ->
            lines.forEach { raw ->
                lineNumber += 1
                val line = raw.trim().trimStart('﻿')
                if (line.isBlank()) return@forEach

                val columns = CsvLineParser.parseLine(line).map { it.trim().trim('"') }
                if (columns.size < 2) {
                    errors += ParseError.MalformedLine(
                        lineNumber = lineNumber,
                        expected = ">= 2 columns (native, target)",
                        actual = "${columns.size} column(s): $line",
                    )
                    return@forEach
                }
                val native = columns[0]
                val target = columns[1]
                if (native.isBlank() || target.isBlank()) {
                    errors += ParseError.MalformedLine(
                        lineNumber = lineNumber,
                        expected = "non-empty native and target",
                        actual = "native='$native', target='$target'",
                    )
                    return@forEach
                }
                val hardRaw = columns.getOrNull(2)?.lowercase()
                val isHard = hardRaw == "hard" || hardRaw == "1" || hardRaw == "true"
                val rank = rows.size

                rows += VocabRow(
                    id = "${pos}_$rank" + "_$target",
                    pos = pos,
                    rank = rank,
                    word = target,
                    meaningRu = native,
                    isHard = isHard,
                )
            }
        }

        return when {
            rows.isEmpty() && errors.isEmpty() ->
                ParseResult.failure(listOf(ParseError.EmptyFile(lineNumber)))
            rows.isEmpty() -> ParseResult.failure(errors)
            errors.isEmpty() -> ParseResult.success(rows)
            else -> ParseResult.partial(rows, errors)
        }
    }
}
