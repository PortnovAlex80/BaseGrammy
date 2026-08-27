package com.alexpo.grammermate.v2.core.data.packimport

import com.alexpo.grammermate.domain.model.PhrasePair
import com.alexpo.grammermate.domain.model.WordScript
import java.io.InputStream

/**
 * Парсер BgVocab CSV → [WordScript] (SRS-002 FR-7, AC-11; Фаза 5).
 *
 * Контракт:
 * - Header (обязательный, ровно 15 колонок):
 *   `rank, word, ru, collo_it, collo_ru, s1_it, s1_ru, …, s5_it, s5_ru`.
 *   Несоответствие header → [ParseResult.Failure] с [ParseError.InvalidFormat]
 *   (фикс аудита H-6: прежде — `IllegalArgumentException`, валивший весь пак
 *   вместо одного отвергнутого файла в partial-диалоге).
 * - Каждая data-row: минимум 5 полей; `rank` — Int (иначе MalformedLine);
 *   `word` непуст.
 * - Sentences: пары s1..s5; первый пустой `s{n}_it` завершает список
 *   (контракт WordScript допускает и меньше пар — валидность 3–5 проверяет
 *   потребитель, парсер честно передаёт сколько есть).
 * - RFC-4180: comma-разделитель, quoted fields (запятые внутри кавычек),
 *   `""` → `"`; BOM в первой строке снимается.
 * - Partial-семантика: битые строки → MalformedLine, корректные парсятся.
 */
object BgVocabCsvParser {

    private val HEADER = listOf(
        "rank", "word", "ru", "collo_it", "collo_ru",
        "s1_it", "s1_ru", "s2_it", "s2_ru", "s3_it", "s3_ru",
        "s4_it", "s4_ru", "s5_it", "s5_ru",
    )

    /**
     * Распарсить BgVocab CSV в список [WordScript].
     *
     * @param input поток CSV (UTF-8, с заголовком из 15 колонок, возможен BOM).
     * @return [ParseResult]: Success/Partial со скриптами; Failure(InvalidFormat)
     *         при несоответствии header (фикс H-6 — без исключений).
     */
    fun parse(input: InputStream): ParseResult<List<WordScript>, ParseError> {
        val scripts = mutableListOf<WordScript>()
        val errors = mutableListOf<ParseError>()
        var lineNumber = 0
        var headerChecked = false

        input.bufferedReader().useLines { lines ->
            lines.forEach { raw ->
                lineNumber += 1
                val line = raw.trim().let { if (lineNumber == 1) it.trimStart('\uFEFF') else it }
                if (line.isBlank()) return@forEach

                val fields = rfc4180Line(line)
                if (!headerChecked) {
                    if (fields.map { it.trim().lowercase() } != HEADER) {
                        return ParseResult.failure(
                            listOf(
                                ParseError.InvalidFormat(
                                    lineNumber = 1,
                                    reason = "bg-vocab header обязан быть ровно 15 колонок " +
                                        "(${HEADER.joinToString(",")}), получено: ${fields.size} колонок",
                                )
                            )
                        )
                    }
                    headerChecked = true
                    return@forEach
                }

                if (fields.size < 5) {
                    errors += ParseError.MalformedLine(
                        lineNumber = lineNumber,
                        expected = ">= 5 fields (rank, word, ru, collo_it, collo_ru)",
                        actual = "${fields.size} field(s)",
                    )
                    return@forEach
                }
                val rank = fields[0].trim().toIntOrNull()
                val word = fields[1].trim()
                if (rank == null || word.isBlank()) {
                    errors += ParseError.MalformedLine(
                        lineNumber = lineNumber,
                        expected = "rank: Int и непустой word",
                        actual = "rank='${fields[0]}', word='$word'",
                    )
                    return@forEach
                }
                val ru = fields[2].trim()
                val colloIt = fields[3].trim()
                val colloRu = fields[4].trim()

                val sentences = mutableListOf<PhrasePair>()
                for (n in 1..5) {
                    val it_ = fields.getOrNull(4 + 2 * n - 1)?.trim().orEmpty()
                    if (it_.isBlank()) break // первый пустой s{n}_it завершает список
                    val ru_ = fields.getOrNull(4 + 2 * n)?.trim().orEmpty()
                    sentences += PhrasePair(target = it_, translation = ru_)
                }

                scripts += WordScript(
                    rank = rank,
                    wordIt = word,
                    wordRu = ru,
                    colloIt = colloIt,
                    colloRu = colloRu,
                    sentences = sentences,
                )
            }
        }

        return when {
            !headerChecked -> ParseResult.failure(
                listOf(ParseError.EmptyFile(lineNumber))
            )
            scripts.isEmpty() && errors.isEmpty() -> ParseResult.failure(
                listOf(ParseError.EmptyFile(lineNumber))
            )
            scripts.isEmpty() -> ParseResult.failure(errors)
            errors.isEmpty() -> ParseResult.success(scripts)
            else -> ParseResult.partial(scripts, errors)
        }
    }

    /**
     * RFC-4180-сплиттер одной строки: comma-разделитель, quoted fields
     * (запятые/переносы внутри кавычек не разделяют), `""` внутри кавычек → `"`;.
     */
    internal fun rfc4180Line(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i += 2
                }
                c == '"' -> {
                    inQuotes = !inQuotes; i += 1
                }
                c == ',' && !inQuotes -> {
                    out += sb.toString(); sb.clear(); i += 1
                }
                else -> {
                    sb.append(c); i += 1
                }
            }
        }
        out += sb.toString()
        return out
    }
}
