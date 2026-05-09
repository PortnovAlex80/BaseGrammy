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
    val isHard: Boolean
)

/**
 * Parser for Italian drill vocab CSV files from external data source.
 *
 * Supported formats:
 * - drill_verbs.csv:   rank,verb,collocations
 * - drill_nouns.csv:   rank,noun,collocations
 * - drill_adjectives.csv: rank,adjective,msg,fsg,mpl,fpl,collocations
 * - drill_adverbs.csv: rank,adverb,comparative,superlative,meaning_ru,collocations
 * - drill_numbers.csv: category,italian,russian,form_m,form_f,notes
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

            val header = lineList.first().trim().lowercase()
            val dataLines = lineList.drop(1) // Skip header

            when {
                header.contains("rank") && (header.contains("verb") || header.contains("noun")) -> {
                    // Format: rank,word,collocations
                    parseRankWordCollocations(dataLines, rows)
                }
                header.contains("rank") && header.contains("adjective") -> {
                    // Format: rank,adjective,msg,fsg,mpl,fpl,collocations
                    parseAdjectives(dataLines, rows)
                }
                header.contains("rank") && header.contains("adverb") -> {
                    // Format: rank,adverb,comparative,superlative,meaning_ru,collocations
                    parseAdverbs(dataLines, rows)
                }
                header.contains("category") && header.contains("italian") -> {
                    // Format: category,italian,russian,form_m,form_f,notes (numbers)
                    parseNumbers(dataLines, rows)
                }
                header.contains("type") && header.contains("person") -> {
                    // Format: type,category,person,form_sg_m,...,notes (pronouns)
                    parsePronouns(dataLines, rows)
                }
                else -> {
                    Log.w(TAG, "Unknown CSV format in $fileName, header: $header")
                    // Try generic rank,word,collocations as fallback
                    parseRankWordCollocations(dataLines, rows)
                }
            }
        }

        Log.d(TAG, "Parsed ${rows.size} rows from $fileName")
        return rows
    }

    /**
     * Parse format: rank,word,collocations
     * Used by drill_verbs.csv and drill_nouns.csv
     */
    private fun parseRankWordCollocations(lines: List<String>, rows: MutableList<ItalianDrillRow>) {
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

            val isHard = rank > 100
            rows.add(ItalianDrillRow(rank, word, collocations, isHard))
        }
    }

    /**
     * Parse format: rank,adjective,msg,fsg,mpl,fpl,collocations
     * Used by drill_adjectives.csv
     */
    private fun parseAdjectives(lines: List<String>, rows: MutableList<ItalianDrillRow>) {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            val columns = splitCsvLine(trimmed)
            if (columns.size < 2) continue

            val rank = columns[0].trim().toIntOrNull() ?: continue
            val word = columns[1].trim().trim('"')
            if (word.isBlank()) continue

            // collocations is the last column (index 6)
            val collocationsStr = columns.getOrElse(6) { "" }.trim().trim('"')
            val collocations = if (collocationsStr.isNotBlank()) {
                collocationsStr.split(";").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val isHard = rank > 100
            rows.add(ItalianDrillRow(rank, word, collocations, isHard))
        }
    }

    /**
     * Parse format: rank,adverb,comparative,superlative,meaning_ru,collocations
     * Used by drill_adverbs.csv
     */
    private fun parseAdverbs(lines: List<String>, rows: MutableList<ItalianDrillRow>) {
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

            val isHard = rank > 100
            rows.add(ItalianDrillRow(rank, word, collocations, isHard))
        }
    }

    /**
     * Parse format: category,italian,russian,form_m,form_f,notes
     * Used by drill_numbers.csv
     * No rank; assign synthetic rank based on row order.
     */
    private fun parseNumbers(lines: List<String>, rows: MutableList<ItalianDrillRow>) {
        var syntheticRank = 1
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            val columns = splitCsvLine(trimmed)
            if (columns.size < 2) continue

            val word = columns[1].trim().trim('"')
            if (word.isBlank()) continue

            val notes = columns.getOrElse(5) { "" }.trim().trim('"')
            val collocations = if (notes.isNotBlank()) {
                notes.split(";").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val rank = syntheticRank
            syntheticRank++
            val isHard = rank > 100
            rows.add(ItalianDrillRow(rank, word, collocations, isHard))
        }
    }

    /**
     * Parse format: type,category,person,form_sg_m,form_sg_f,form_pl_m,form_pl_f,notes
     * Used by drill_pronouns.csv
     * No rank; assign synthetic rank based on row order.
     */
    private fun parsePronouns(lines: List<String>, rows: MutableList<ItalianDrillRow>) {
        var syntheticRank = 1
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            val columns = splitCsvLine(trimmed)
            if (columns.size < 4) continue

            val word = columns[3].trim().trim('"')  // form_sg_m as the main form
            if (word.isBlank()) continue

            val notes = columns.getOrElse(7) { "" }.trim().trim('"')
            val collocations = if (notes.isNotBlank()) {
                notes.split(";").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val rank = syntheticRank
            syntheticRank++
            val isHard = rank > 100
            rows.add(ItalianDrillRow(rank, word, collocations, isHard))
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
                        lessonId = lessonId,
                        languageId = languageId,
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
