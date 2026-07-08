package com.alexpo.grammermate.v2.core.data.packimport

import com.alexpo.grammermate.domain.model.WordScript
import java.io.InputStream

/**
 * Парсер BgVocab CSV → [WordScript] (SRS-002 FR-7, AC-11).
 *
 * Переносится 1:1 из legacy (`com.alexpo.grammermate.data.BgVocabCsvParser`).
 * Контракт:
 * - Header (обязательный, ровно 15 колонок):
 *   `rank, word, ru, collo_it, collo_ru, s1_it, s1_ru, …, s5_it, s5_ru`.
 *   Несоответствие header → `IllegalArgumentException` (файл считается повреждённым).
 * - Каждая data-row: минимум 5 полей; `rank` — Int; `word` непуст.
 * - Sentences: 3–5 `PhrasePair(it, ru)`, первый пустой `s{n}_it` заканчивает список.
 * - RFC-4180 quoted fields (comma inside quotes, `""` → `"`).
 *
 * SCAFFOLD: контракт зафиксирован SRS-002 FR-7 / §5.5; реализация — TODO (AC-11).
 * Legacy-тест `BgVocabCsvParserTest` переносится в v2 без изменения утверждений (NFR-6).
 *
 * @see <a href="../../../../../../../../docs/requirements/REQ-002-data-room/02-srs.md">SRS-002 FR-7</a>
 */
object BgVocabCsvParser {

    /**
     * Распарсить BgVocab CSV в список [WordScript].
     *
     * @param input поток CSV (UTF-8, с заголовком из 15 колонок).
     * @return список скриптов слов (полный успех; при невалидном header — `IllegalArgumentException`).
     */
    fun parse(input: InputStream): List<WordScript> {
        TODO("AC-11: реализовать BgVocabCsvParser.parse (header 15 columns RFC-4180, legacy 1:1)")
    }
}
