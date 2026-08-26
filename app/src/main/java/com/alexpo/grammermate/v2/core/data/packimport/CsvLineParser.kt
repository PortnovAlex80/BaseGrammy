package com.alexpo.grammermate.v2.core.data.packimport

/**
 * Строчный CSV-парсер с разделителем `;` и обработкой кавычек (RFC-4180-подмножество).
 *
 * Переносится 1:1 из legacy `com.alexpo.grammermate.data.CsvLineParser` (общий
 * хелпер CsvParser/VocabCsvParser/VerbDrillCsvParser). Чистый Kotlin — кавычки
 * сохраняются в выводе (снятие делает вызывающий парсер), `;` внутри кавычек
 * не разделяет колонки.
 */
object CsvLineParser {

    /**
     * Разбить [line] на колонки по `;` вне кавычек.
     *
     * @return колонки строки (всегда ≥1: пустая строка → `[""]`).
     */
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
