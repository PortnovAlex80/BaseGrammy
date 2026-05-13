package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Parsed row from an Italian drill vocab CSV file.
 * Each row represents a word with its collocations (phrases).
 */
data class ItalianDrillRow(
    val rank: Int,
    val word: String,
    val collocations: List<String>,
    val isHard: Boolean,
    val meaningRu: String? = null,
    val forms: Map<String, String> = emptyMap()
)

/**
 * Parser for Italian drill vocab CSV files from external data source.
 *
 * Supported formats:
 * - drill_verbs.csv:   rank,verb,collocations
 * - drill_nouns.csv:   rank,noun,collocations
 * - drill_adjectives.csv: rank,adjective,msg,fsg,mpl,fpl,collocations
 * - drill_adverbs.csv: rank,adverb,comparative,superlative,meaning_ru,collocations
 * - drill_numbers.csv: category,italian,ru,form_m,form_f,notes
 * - drill_pronouns.csv: type,category,person,form_sg_m,form_sg_f,form_pl_m,form_pl_f,notes
 *
 * Collocations are semicolon-separated Italian phrases.
 */
object ItalianDrillVocabParser {

    private const val TAG = "ItalianDrillVocab"

    /**
     * Parse a CSV file from assets for Italian drill vocab.
     * Auto-detects format based on header row.
     */
    fun parse(inputStream: InputStream, fileName: String): List<ItalianDrillRow> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val rows = mutableListOf<ItalianDrillRow>()

        reader.useLines { lines ->
            val lineList = lines.toList()
            if (lineList.isEmpty()) return rows

            val headerLine = lineList.first().trim()
            val headerColumns = headerLine.split(",").map { it.trim().lowercase().trim('"') }
            val dataLines = lineList.drop(1) // Skip header

            // Detect ru column index by header name
            val ruColIndex = headerColumns.indexOfFirst {
                it == "ru" || it == "meaning_ru" || it == "russian"
            }.takeIf { it >= 0 }

            when {
                headerLine.lowercase().contains("rank") && (headerLine.lowercase().contains("verb") || headerLine.lowercase().contains("noun")) -> {
                    parseRankWordCollocations(dataLines, rows, ruColIndex)
                }
                headerLine.lowercase().contains("rank") && headerLine.lowercase().contains("adjective") -> {
                    parseAdjectives(dataLines, rows, headerColumns, ruColIndex)
                }
                headerLine.lowercase().contains("rank") && headerLine.lowercase().contains("adverb") -> {
                    parseAdverbs(dataLines, rows, ruColIndex)
                }
                headerLine.lowercase().contains("category") && headerLine.lowercase().contains("italian") -> {
                    parseNumbers(dataLines, rows, ruColIndex, headerColumns)
                }
                headerLine.lowercase().contains("type") && headerLine.lowercase().contains("person") -> {
                    parsePronouns(dataLines, rows, ruColIndex, headerColumns)
                }
                else -> {
                    Log.w(TAG, "Unknown CSV format in $fileName, header: $headerLine")
                    parseRankWordCollocations(dataLines, rows, ruColIndex)
                }
            }
        }

        Log.d(TAG, "Parsed ${rows.size} rows from $fileName")
        return rows
    }

    /**
     * Parse format: rank,word,collocations[,ru]
     * Used by drill_verbs.csv and drill_nouns.csv
     */
    private fun parseRankWordCollocations(
        lines: List<String>,
        rows: MutableList<ItalianDrillRow>,
        ruColIndex: Int?
    ) {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            val columns = splitCsvLine(trimmed)
            if (columns.size < 2) continue

            val rank = columns[0].trim().toIntOrNull() ?: continue
            val word = columns[1].trim().trim('"')
            if (word.isBlank()) continue

            val collocationsStr = columns.getOrElse(2) { "" }.trim().trim('"')
            val collocations = if (collocationsStr.isNotBlank()) {
                collocationsStr.split(";").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val meaningRu = ruColIndex?.let { idx ->
                columns.getOrNull(idx)?.trim()?.trim('"')?.takeIf { it.isNotBlank() }
            }

            val isHard = rank > 100
            rows.add(ItalianDrillRow(rank, word, collocations, isHard, meaningRu))
        }
    }

    /**
     * Parse format: rank,adjective,msg,fsg,mpl,fpl,collocations[,ru]
     * Used by drill_adjectives.csv
     */
    private fun parseAdjectives(
        lines: List<String>,
        rows: MutableList<ItalianDrillRow>,
        headerColumns: List<String>,
        ruColIndex: Int?
    ) {
        // Detect collocations column index (last before ru, or last overall)
        val collColIndex = headerColumns.indexOf("collocations")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            val columns = splitCsvLine(trimmed)
            if (columns.size < 2) continue

            val rank = columns[0].trim().toIntOrNull() ?: continue
            val word = columns[1].trim().trim('"')
            if (word.isBlank()) continue

            // Extract gender forms if columns exist
            val forms = mutableMapOf<String, String>()
            val msgIdx = headerColumns.indexOf("msg")
            val fsgIdx = headerColumns.indexOf("fsg")
            val mplIdx = headerColumns.indexOf("mpl")
            val fplIdx = headerColumns.indexOf("fpl")
            if (msgIdx >= 0) columns.getOrNull(msgIdx)?.trim()?.trim('"')?.let { if (it.isNotBlank()) forms["msg"] = it }
            if (fsgIdx >= 0) columns.getOrNull(fsgIdx)?.trim()?.trim('"')?.let { if (it.isNotBlank()) forms["fsg"] = it }
            if (mplIdx >= 0) columns.getOrNull(mplIdx)?.trim()?.trim('"')?.let { if (it.isNotBlank()) forms["mpl"] = it }
            if (fplIdx >= 0) columns.getOrNull(fplIdx)?.trim()?.trim('"')?.let { if (it.isNotBlank()) forms["fpl"] = it }

            // collocations column by header index, fallback to index 6
            val effectiveCollIdx = if (collColIndex >= 0) collColIndex else 6
            val collocationsStr = columns.getOrElse(effectiveCollIdx) { "" }.trim().trim('"')
            val collocations = if (collocationsStr.isNotBlank()) {
                collocationsStr.split(";").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val meaningRu = ruColIndex?.let { idx ->
                columns.getOrNull(idx)?.trim()?.trim('"')?.takeIf { it.isNotBlank() }
            }

            val isHard = rank > 100
            rows.add(ItalianDrillRow(rank, word, collocations, isHard, meaningRu, forms))
        }
    }

    /**
     * Parse format: rank,adverb,comparative,superlative,meaning_ru,collocations
     * Used by drill_adverbs.csv
     */
    private fun parseAdverbs(
        lines: List<String>,
        rows: MutableList<ItalianDrillRow>,
        ruColIndex: Int?
    ) {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            val columns = splitCsvLine(trimmed)
            if (columns.size < 2) continue

            val rank = columns[0].trim().toIntOrNull() ?: continue
            val word = columns[1].trim().trim('"')
            if (word.isBlank()) continue

            // collocations is the last column (index 5)
            val collocationsStr = columns.getOrElse(5) { "" }.trim().trim('"')
            val collocations = if (collocationsStr.isNotBlank()) {
                collocationsStr.split(";").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val meaningRu = ruColIndex?.let { idx ->
                columns.getOrNull(idx)?.trim()?.trim('"')?.takeIf { it.isNotBlank() }
            }

            val isHard = rank > 100
            rows.add(ItalianDrillRow(rank, word, collocations, isHard, meaningRu))
        }
    }

    /**
     * Parse format: category,italian,ru,form_m,form_f,notes
     * Used by drill_numbers.csv
     * No rank; assign synthetic rank based on row order.
     */
    private fun parseNumbers(
        lines: List<String>,
        rows: MutableList<ItalianDrillRow>,
        ruColIndex: Int?,
        headerColumns: List<String>
    ) {
        val formMIndex = headerColumns.indexOf("form_m")
        val formFIndex = headerColumns.indexOf("form_f")

        var syntheticRank = 1
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            val columns = splitCsvLine(trimmed)
            if (columns.size < 2) continue

            val word = columns[1].trim().trim('"')
            if (word.isBlank()) continue

            // Extract gender forms if available
            val forms = mutableMapOf<String, String>()
            if (formMIndex >= 0) columns.getOrNull(formMIndex)?.trim()?.trim('"')
                ?.let { if (it.isNotBlank()) forms["form_m"] = it }
            if (formFIndex >= 0) columns.getOrNull(formFIndex)?.trim()?.trim('"')
                ?.let { if (it.isNotBlank()) forms["form_f"] = it }

            val notes = columns.getOrElse(5) { "" }.trim().trim('"')
            val collocations = if (notes.isNotBlank()) {
                notes.split(";").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val meaningRu = ruColIndex?.let { idx ->
                columns.getOrNull(idx)?.trim()?.trim('"')?.takeIf { it.isNotBlank() }
            }

            val rank = syntheticRank
            syntheticRank++
            val isHard = rank > 100
            rows.add(ItalianDrillRow(rank, word, collocations, isHard, meaningRu, forms))
        }
    }

    /**
     * Parse format: type,category,person,form_sg_m,form_sg_f,form_pl_m,form_pl_f,notes
     * Used by drill_pronouns.csv
     * No rank; assign synthetic rank based on row order.
     */
    private fun parsePronouns(
        lines: List<String>,
        rows: MutableList<ItalianDrillRow>,
        ruColIndex: Int?,
        headerColumns: List<String>
    ) {
        val formSgMIndex = headerColumns.indexOf("form_sg_m")
        val formSgFIndex = headerColumns.indexOf("form_sg_f")
        val formPlMIndex = headerColumns.indexOf("form_pl_m")
        val formPlFIndex = headerColumns.indexOf("form_pl_f")

        var syntheticRank = 1
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            val columns = splitCsvLine(trimmed)
            if (columns.size < 4) continue

            val word = columns[3].trim().trim('"')  // form_sg_m as the main form
            if (word.isBlank()) continue

            // Extract forms
            val forms = mutableMapOf<String, String>()
            if (formSgMIndex >= 0) columns.getOrNull(formSgMIndex)?.trim()?.trim('"')
                ?.let { if (it.isNotBlank()) forms["form_sg_m"] = it }
            if (formSgFIndex >= 0) columns.getOrNull(formSgFIndex)?.trim()?.trim('"')
                ?.let { if (it.isNotBlank()) forms["form_sg_f"] = it }
            if (formPlMIndex >= 0) columns.getOrNull(formPlMIndex)?.trim()?.trim('"')
                ?.let { if (it.isNotBlank()) forms["form_pl_m"] = it }
            if (formPlFIndex >= 0) columns.getOrNull(formPlFIndex)?.trim()?.trim('"')
                ?.let { if (it.isNotBlank()) forms["form_pl_f"] = it }

            val notes = columns.getOrElse(7) { "" }.trim().trim('"')
            val collocations = if (notes.isNotBlank()) {
                notes.split(";").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val meaningRu = ruColIndex?.let { idx ->
                columns.getOrNull(idx)?.trim()?.trim('"')?.takeIf { it.isNotBlank() }
            }

            val rank = syntheticRank
            syntheticRank++
            val isHard = rank > 100
            rows.add(ItalianDrillRow(rank, word, collocations, isHard, meaningRu, forms))
        }
    }

    /**
     * Split a CSV line respecting double quotes.
     */
    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when (ch) {
                '"' -> {
                    inQuotes = !inQuotes
                    current.append(ch)
                }
                ',' -> {
                    if (inQuotes) {
                        current.append(ch)
                    } else {
                        result.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }

    /**
     * Load all Italian drill vocab entries from assets as VocabEntry list.
     * Each entry maps: nativeText = Italian word, targetText = collocations joined with "+".
     */
    fun loadAllFromAssets(context: Context, lessonId: String, languageId: String): List<VocabEntry> {
        val assetBase = "grammarmate/vocab/it"
        val files = listOf(
            "drill_adjectives.csv",
            "drill_adverbs.csv",
            "drill_nouns.csv",
            "drill_numbers.csv",
            "drill_pronouns.csv",
            "drill_verbs.csv"
        )

        val entries = mutableListOf<VocabEntry>()
        var globalIndex = 0

        for (fileName in files) {
            try {
                val stream = context.assets.open("$assetBase/$fileName")
                val rows = parse(stream, fileName)
                stream.close()

                for (row in rows) {
                    val targetText = if (row.collocations.isNotEmpty()) {
                        row.collocations.joinToString("+")
                    } else {
                        row.word  // Use the word itself if no collocations
                    }

                    entries.add(VocabEntry(
                        id = "it_drill_${fileName}_${row.rank}",
                        lessonId = LessonId(lessonId),
                        languageId = LanguageId(languageId),
                        nativeText = row.word,
                        targetText = targetText,
                        isHard = row.isHard
                    ))
                    globalIndex++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load $fileName from assets", e)
            }
        }

        Log.d(TAG, "Loaded ${entries.size} Italian drill vocab entries from assets")
        return entries
    }
}
