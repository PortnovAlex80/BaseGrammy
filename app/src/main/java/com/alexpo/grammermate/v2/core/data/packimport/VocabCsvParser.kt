package com.alexpo.grammermate.v2.core.data.packimport

import java.io.InputStream

/**
 * Парсер Vocab CSV → [VocabRow] (SRS-002 FR-6, AC-9).
 *
 * Переносится 1:1 из legacy (`com.alexpo.grammermate.data.VocabCsvParser`).
 * Контракт:
 * - Каждая строка — минимум 2 колонки (`native, target`); опционально 3-я `hard`.
 * - `isHard` = true, если 3-я колонка `"hard"|"1"|"true"` (case-insensitive).
 * - ID: `"${pos}_${rank}_${word}"` (совпадает с `VocabWordEntity.id`).
 *
 * Файлы с префиксом `vocab_` (например, `vocab_lessonA.csv`) обрабатываются при
 * импорте пака; `lessonId = file.nameWithoutExtension.removePrefix("vocab_")`.
 *
 * SCAFFOLD: контракт зафиксирован SRS-002 FR-6 / §5.5; реализация — TODO (AC-9).
 * Legacy-тест `VocabCsvParserTest` переносится в v2 без изменения утверждений (NFR-6).
 *
 * @see <a href="../../../../../../../../docs/requirements/REQ-002-data-room/02-srs.md">SRS-002 FR-6</a>
 */
object VocabCsvParser {

    /**
     * Распарсить Vocab CSV в список [VocabRow].
     *
     * @param inputStream поток CSV (UTF-8).
     * @return [ParseResult] с `List<VocabRow>`; partial — корректные строки + ошибки.
     */
    fun parse(inputStream: InputStream): ParseResult<List<VocabRow>, ParseError> {
        TODO("AC-9: реализовать VocabCsvParser.parse (legacy 1:1, regression-locked)")
    }
}
