package com.alexpo.grammermate.data

/**
 * Shared semicolon-delimited CSV line parser with quote handling.
 * Extracted from CsvParser, VocabCsvParser, and VerbDrillCsvParser
 * which all contained identical implementations.
 */
object CsvLineParser {
    fun parseLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when (ch) {
                '"' -> {
                    inQuotes = !inQuotes
                    current.append(ch)
                }
                ';' -> {
                    if (inQuotes) {
                        current.append(ch)
                    } else {
                        result.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
            i += 1
        }
        result.add(current.toString())
        return result
    }
}
