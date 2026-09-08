package com.alexpo.grammermate.data

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Parser for the background-vocab word-script CSV.
 *
 * Schema (comma-separated, RFC-4180-style quoted fields):
 *
 * ```
 * rank,word,ru,collo_it,collo_ru,s1_it,s1_ru,s2_it,s2_ru,s3_it,s3_ru,s4_it,s4_ru,s5_it,s5_ru
 * ```
 *
 * - `rank`     — Int, presentation/frequency rank.
 * - `word`     — Italian word (e.g. "casa").
 * - `ru`       — Russian translation(s); may contain "/" alternatives, kept verbatim.
 * - `collo_it` — one Italian collocation phrase.
 * - `collo_ru` — Russian translation of the collocation.
 * - `s1_it..s5_it` — Italian example sentences (3-5 filled; trailing ones may be empty).
 * - `s1_ru..s5_ru` — parallel Russian translations.
 *
 * Each data row becomes a [WordScript]. The `sentences` list contains one
 * [PhrasePair] per non-empty `s{n}_it` cell (a present Italian sentence with an
 * empty Russian cell still yields a pair with `ru = ""`).
 *
 * Tolerances:
 *  - Blank rows are skipped.
 *  - A missing or unrecognized header throws [IllegalArgumentException] (the
 *    file is treated as corrupt rather than silently mis-parsed).
 *  - Fields may be wrapped in double quotes; a quoted field may contain commas
 *    and escaped quotes (`""`).
 */
object BgVocabCsvParser {

    /** The exact, ordered header this parser expects. */
    val EXPECTED_HEADER: List<String> = listOf(
        "rank", "word", "ru",
        "collo_it", "collo_ru",
        "s1_it", "s1_ru",
        "s2_it", "s2_ru",
        "s3_it", "s3_ru",
        "s4_it", "s4_ru",
        "s5_it", "s5_ru"
    )

    private const val SENTENCE_SLOTS = 5

    /**
     * Parse a CSV document from [input] (UTF-8). The stream is consumed and
     * closed. Throws [IllegalArgumentException] if the header is missing or
     * does not match [EXPECTED_HEADER].
     */
    fun parse(input: InputStream): List<WordScript> {
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
        val lines = reader.use { it.readLines() }
        return parseLines(lines)
    }

    /**
     * Parse a CSV document from a raw [String]. Mainly intended for tests;
     * production code should use [parse] with an asset/input stream.
     */
    fun parseString(csv: String): List<WordScript> =
        parseLines(csv.split("\n", "\r\n", "\r"))

    private fun parseLines(lines: List<String>): List<WordScript> {
        val dataRows = skipHeader(lines)
        val out = mutableListOf<WordScript>()
        for (rawLine in dataRows) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val cols = splitCsvLine(line)
            val script = rowToScript(cols) ?: continue
            out.add(script)
        }
        return out
    }

    /**
     * Locate and validate the header row, then return every subsequent line
     * (including blank ones, which are filtered later).
     */
    private fun skipHeader(lines: List<String>): List<String> {
        for ((idx, raw) in lines.withIndex()) {
            val headerCols = splitCsvLine(raw.trim())
            if (headerCols.size == EXPECTED_HEADER.size &&
                headerCols.map { it.trim().lowercase() } == EXPECTED_HEADER
            ) {
                return lines.subList(idx + 1, lines.size)
            }
        }
        throw IllegalArgumentException(
            "bg_vocab CSV header not found. Expected: ${EXPECTED_HEADER.joinToString(",")}"
        )
    }

    private fun rowToScript(cols: List<String>): WordScript? {
        // Require the five leading fields at minimum. Trailing sentence columns
        // may be omitted entirely by a curator (a row with 3 sentences need not
        // carry the empty s4/s5 cells); missing columns read as empty below.
        if (cols.size < 5) return null

        val rank = cols[0].trim().toIntOrNull() ?: return null
        val word = cols[1].trim()
        if (word.isEmpty()) return null

        val ru = cols[2].trim()
        val colloIt = cols[3].trim()
        val colloRu = cols[4].trim()

        // Columns 5..: alternating s{n}_it / s{n}_ru, read in order s1..s5.
        // The spec calls for 3-5 contiguous filled sentences, so the first empty
        // Italian sentence ends the list (later slots, if any, are ignored).
        val sentences = ArrayList<PhrasePair>(SENTENCE_SLOTS)
        for (i in 1..SENTENCE_SLOTS) {
            val itCell = cols.getOrNull(4 + i * 2 - 1)?.trim().orEmpty()
            if (itCell.isEmpty()) break
            val ruCell = cols.getOrNull(4 + i * 2)?.trim().orEmpty()
            sentences.add(PhrasePair(it = itCell, ru = ruCell))
        }

        return WordScript(
            rank = rank,
            wordIt = word,
            wordRu = ru,
            colloIt = colloIt,
            colloRu = colloRu,
            sentences = sentences
        )
    }

    /**
     * Split a single CSV record on commas, honoring double-quoted fields.
     *
     * Rules (RFC-4180 subset):
     *  - A field may be wrapped in double quotes.
     *  - Inside quotes, commas do not split the field.
     *  - Inside quotes, a doubled quote `""` is a literal `"`.
     *  - Outside quotes, quotes are not special beyond opening/closing a field.
     *
     * This is a self-contained implementation (the project's shared
     * [CsvLineParser] is semicolon-delimited and therefore not reusable here).
     */
    internal fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                inQuotes -> {
                    when (ch) {
                        '"' -> {
                            // Doubled quote inside a quoted field → literal '"'.
                            if (i + 1 < line.length && line[i + 1] == '"') {
                                current.append('"')
                                i += 2
                                continue
                            }
                            inQuotes = false
                        }
                        else -> current.append(ch)
                    }
                }
                else -> {
                    when (ch) {
                        '"' -> inQuotes = true
                        ',' -> {
                            result.add(current.toString())
                            current.clear()
                        }
                        else -> current.append(ch)
                    }
                }
            }
            i += 1
        }
        result.add(current.toString())
        return result
    }
}
