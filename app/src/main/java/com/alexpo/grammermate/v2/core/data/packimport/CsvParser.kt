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
 *
 * SCAFFOLD: контракт зафиксирован SRS-002 FR-3 / §5.5; реализация — TODO (AC-7).
 * Legacy-тест `CsvParserTest` переносится в v2 без изменения утверждений (NFR-6).
 *
 * @see <a href="../../../../../../../../docs/requirements/REQ-002-data-room/02-srs.md">SRS-002 FR-3</a>
 */
object CsvParser {

    /**
     * Распарсить Lesson CSV в заголовок + список [SentenceCard] с partial-error семантикой.
     *
     * @param inputStream поток CSV (UTF-8, с возможным BOM).
     * @return [ParseResult] с `Pair<title, cards>`; partial — корректные карты + ошибки.
     */
    fun parseLesson(inputStream: InputStream): ParseResult<Pair<String, List<SentenceCard>>, ParseError> {
        TODO("AC-7: реализовать CsvParser.parseLesson (legacy 1:1, regression-locked)")
    }
}
