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
                val columns = CsvLineParser.parseLine(line)
                if (columns.size < 2) {
                    return@forEach
                }
                val ru = columns[0].trim().trim('"')
                val answersRaw = columns[1]
                if (ru.isBlank() || answersRaw.isBlank()) {
                    return@forEach
                }
                val answers = answersRaw.split("+")
                    .map { it.trim().trim('"') }
                    .filter { it.isNotBlank() }
                if (answers.isEmpty()) {
                    return@forEach
                }
                val tense = if (columns.size >= 3) {
                    columns[2].trim().trim('"').ifBlank { null }
                } else {
                    null
                }
                cards.add(
                    SentenceCard(
                        id = "card_$lineNumber",
                        promptRu = ru,
                        acceptedAnswers = answers,
                        tense = tense
                    )
                )
            }
        }
        return title to cards
    }

    private fun extractTitle(raw: String): String? {
        val trimmed = raw.trim().trim('"').trimStart('\uFEFF')
        if (trimmed.isBlank()) return null
        val builder = StringBuilder()
        for (ch in trimmed) {
            if (ch.isLetterOrDigit() || ch == ' ' || ch == '-' || ch == '.' || ch == ',') {
                builder.append(ch)
            } else {
                break
            }
            if (builder.length >= 160) break
        }
        return builder.toString().trim().ifBlank { null }
    }
}
