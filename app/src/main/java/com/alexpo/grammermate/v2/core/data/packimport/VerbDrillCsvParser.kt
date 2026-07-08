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
    fun parse(content: String): ParseResult<List<VerbDrillCard>, ParseError> {
        TODO("AC-8: реализовать VerbDrillCsvParser.parse(content) (legacy 1:1, regression-locked)")
    }

    /**
     * Стриминг-вариант: парсит CSV из [reader] построчно (для больших файлов).
     *
     * @param reader готовый `BufferedReader` над CSV-потоком.
     * @return [ParseResult] с `List<VerbDrillCard>`.
     */
    fun parse(reader: BufferedReader): ParseResult<List<VerbDrillCard>, ParseError> {
        TODO("AC-8: реализовать VerbDrillCsvParser.parse(reader) — стриминг (legacy 1:1)")
    }
}
