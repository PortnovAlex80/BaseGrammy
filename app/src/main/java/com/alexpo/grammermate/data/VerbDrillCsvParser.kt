package com.alexpo.grammermate.data

import java.io.BufferedReader

object VerbDrillCsvParser {

    /** Pre-compiled regex to extract verb from parenthetical hint in promptRu. */
    private val PARENTHETICAL_VERB_REGEX = Regex("\\(([\\w]+)")

    /**
     * Parse verb drill CSV content from a String.
     * Loads the entire content into memory — avoid for large files.
     */
    fun parse(content: String): Pair<String?, List<VerbDrillCard>> {
        val lines = content.lines()
        val cards = mutableListOf<VerbDrillCard>()
        var title: String? = null
        var headerConsumed = false
        var ruIndex = -1
        var itIndex = -1
        var verbIndex = -1
        var tenseIndex = -1
        var groupIndex = -1
        var rankIndex = -1
        var dataRowIndex = 0

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            if (title == null) {
                title = extractTitle(line)
                continue
            }

            if (!headerConsumed) {
                val columns = CsvLineParser.parseLine(line)
                columns.forEachIndexed { index, col ->
                    val trimmed = col.trim().trim('"')
                    when (trimmed.lowercase()) {
                        "ru" -> ruIndex = index
                        "it" -> itIndex = index
                        "verb" -> verbIndex = index
                        "tense" -> tenseIndex = index
                        "group" -> groupIndex = index
                        "rank" -> rankIndex = index
                    }
                }
                headerConsumed = true
                continue
            }

            if (ruIndex < 0 || itIndex < 0) continue

            val columns = CsvLineParser.parseLine(line)
            if (columns.size <= maxOf(ruIndex, itIndex)) continue

            val ru = columns[ruIndex].trim().trim('"')
            val answer = columns[itIndex].trim().trim('"')
            if (ru.isBlank() || answer.isBlank()) continue

            val verb = if (verbIndex >= 0 && columns.size > verbIndex) {
                columns[verbIndex].trim().trim('"').ifBlank { null }
            } else null
            val tense = if (tenseIndex >= 0 && columns.size > tenseIndex) {
                columns[tenseIndex].trim().trim('"').ifBlank { null }
            } else null
            val group = if (groupIndex >= 0 && columns.size > groupIndex) {
                columns[groupIndex].trim().trim('"').ifBlank { null }
            } else null
            val rank = if (rankIndex >= 0 && columns.size > rankIndex) {
                columns[rankIndex].trim().trim('"').toIntOrNull()
            } else null

            // Fallback: extract verb from parenthetical hint in promptRu
            // e.g. "я устал (essere stanco)" → "essere"
            // e.g. "я хочу есть (avere fame)" → "avere"
            val resolvedVerb = if (verb == null && ru.contains("(")) {
                PARENTHETICAL_VERB_REGEX.find(ru)?.groupValues?.get(1)
            } else verb

            val id = "${group ?: ""}_${tense ?: ""}_$dataRowIndex"
            cards.add(
                VerbDrillCard(
                    id = id,
                    promptRu = ru,
                    answer = answer,
                    verb = resolvedVerb,
                    tense = tense,
                    group = group,
                    rank = rank
                )
            )
            dataRowIndex += 1
        }

        return title to cards
    }

    /**
     * Streaming parse from a BufferedReader — reads one line at a time
     * to avoid loading the entire file into a single String (OOM-safe).
     * The caller is responsible for closing the reader (e.g. via .use { }).
     */
    fun parse(reader: BufferedReader): Pair<String?, List<VerbDrillCard>> {
        val cards = mutableListOf<VerbDrillCard>()
        var title: String? = null
        var headerConsumed = false
        var ruIndex = -1
        var itIndex = -1
        var verbIndex = -1
        var tenseIndex = -1
        var groupIndex = -1
        var rankIndex = -1
        var dataRowIndex = 0

        reader.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEachLine

            if (title == null) {
                title = extractTitle(line)
                return@forEachLine
            }

            if (!headerConsumed) {
                val columns = CsvLineParser.parseLine(line)
                columns.forEachIndexed { index, col ->
                    val trimmed = col.trim().trim('"')
                    when (trimmed.lowercase()) {
                        "ru" -> ruIndex = index
                        "it" -> itIndex = index
                        "verb" -> verbIndex = index
                        "tense" -> tenseIndex = index
                        "group" -> groupIndex = index
                        "rank" -> rankIndex = index
                    }
                }
                headerConsumed = true
                return@forEachLine
            }

            if (ruIndex < 0 || itIndex < 0) return@forEachLine

            val columns = CsvLineParser.parseLine(line)
            if (columns.size <= maxOf(ruIndex, itIndex)) return@forEachLine

            val ru = columns[ruIndex].trim().trim('"')
            val answer = columns[itIndex].trim().trim('"')
            if (ru.isBlank() || answer.isBlank()) return@forEachLine

            val verb = if (verbIndex >= 0 && columns.size > verbIndex) {
                columns[verbIndex].trim().trim('"').ifBlank { null }
            } else null
            val tense = if (tenseIndex >= 0 && columns.size > tenseIndex) {
                columns[tenseIndex].trim().trim('"').ifBlank { null }
            } else null
            val group = if (groupIndex >= 0 && columns.size > groupIndex) {
                columns[groupIndex].trim().trim('"').ifBlank { null }
            } else null
            val rank = if (rankIndex >= 0 && columns.size > rankIndex) {
                columns[rankIndex].trim().trim('"').toIntOrNull()
            } else null

            val resolvedVerb = if (verb == null && ru.contains("(")) {
                PARENTHETICAL_VERB_REGEX.find(ru)?.groupValues?.get(1)
            } else verb

            val id = "${group ?: ""}_${tense ?: ""}_$dataRowIndex"
            cards.add(
                VerbDrillCard(
                    id = id,
                    promptRu = ru,
                    answer = answer,
                    verb = resolvedVerb,
                    tense = tense,
                    group = group,
                    rank = rank
                )
            )
            dataRowIndex += 1
        }

        return title to cards
    }

    private fun extractTitle(raw: String): String? {
        val trimmed = raw.trim().trim('"').trimStart('﻿')
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
