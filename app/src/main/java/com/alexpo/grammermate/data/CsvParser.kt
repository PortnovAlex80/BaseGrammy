package com.alexpo.grammermate.data

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object CsvParser {
    fun parseLesson(inputStream: InputStream): Pair<String?, List<SentenceCard>> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val cards = mutableListOf<SentenceCard>()
        var lineNumber = 0
        var title: String? = null
        var titleConsumed = false
        reader.useLines { lines ->
            lines.forEach { rawLine ->
                lineNumber += 1
                val line = rawLine.trim()
                if (line.isBlank()) return@forEach
                if (!titleConsumed) {
                    title = extractTitle(line)
                    titleConsumed = true
                    return@forEach
                }
                val columns = parseLine(line)
                if (columns.size != 2) {
                    throw IllegalArgumentException("Invalid CSV at line $lineNumber")
                }
                val ru = columns[0].trim().trim('"')
                val answersRaw = columns[1]
                if (ru.isBlank() || answersRaw.isBlank()) {
                    throw IllegalArgumentException("Empty fields at line $lineNumber")
                }
                val answers = answersRaw.split("+")
                    .map { it.trim().trim('"') }
                    .filter { it.isNotBlank() }
                if (answers.isEmpty()) {
                    throw IllegalArgumentException("Empty answers at line $lineNumber")
                }
                cards.add(
                    SentenceCard(
                        id = "card_$lineNumber",
                        promptRu = ru,
                        acceptedAnswers = answers
                    )
                )
            }
        }
        return title to cards
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

    private fun extractTitle(raw: String): String? {
        val trimmed = raw.trim().trim('"').trimStart('\uFEFF')
        if (trimmed.isBlank()) return null
        val builder = StringBuilder()
        for (ch in trimmed) {
            if (ch.isLetterOrDigit() || ch == ' ') {
                builder.append(ch)
            } else {
                break
            }
            if (builder.length >= 160) break
        }
        return builder.toString().trim().ifBlank { null }
    }
}
