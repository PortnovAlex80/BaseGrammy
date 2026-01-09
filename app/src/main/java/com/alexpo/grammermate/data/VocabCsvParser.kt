package com.alexpo.grammermate.data

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class VocabRow(
    val nativeText: String,
    val targetText: String,
    val isHard: Boolean
)

object VocabCsvParser {
    fun parse(inputStream: InputStream): List<VocabRow> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val rows = mutableListOf<VocabRow>()
        var lineNumber = 0
        reader.useLines { lines ->
            lines.forEach { rawLine ->
                lineNumber += 1
                val line = rawLine.trim()
                if (line.isBlank()) return@forEach
                val columns = parseLine(line)
                if (columns.size < 2) return@forEach
                val nativeText = columns[0].trim().trim('"')
                val targetText = columns[1].trim().trim('"')
                if (nativeText.isBlank() || targetText.isBlank()) return@forEach
                val hardRaw = columns.getOrNull(2)?.trim()?.trim('"').orEmpty()
                val isHard = hardRaw.equals("hard", ignoreCase = true) ||
                    hardRaw.equals("1", ignoreCase = true) ||
                    hardRaw.equals("true", ignoreCase = true)
                rows.add(VocabRow(nativeText, targetText, isHard))
            }
        }
        return rows
    }

    private fun parseLine(line: String): List<String> {
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
