package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class CsvParserLazyLoadingTest {

    @Test
    fun parseLessonTitle_extractsTitleFromFirstLine() {
        // Заголовок из первой строки CSV
        val csv = "A01 - Presente Indicativo\nЯ покупаю дом;Compro una casa\nОна идёт домой;Lei va a casa"
        val title = CsvParser.parseLessonTitle(ByteArrayInputStream(csv.toByteArray()))
        assertEquals("A01 - Presente Indicativo", title)
    }

    @Test
    fun parseLessonTitle_returnsNullForEmptyFile() {
        // Пустой файл → null
        val csv = ""
        val title = CsvParser.parseLessonTitle(ByteArrayInputStream(csv.toByteArray()))
        assertNull(title)
    }

    @Test
    fun parseLessonTitle_returnsNullForBlankLine() {
        // Первая строка пустая → null
        val csv = "\nЯ покупаю дом;Compro una casa"
        val title = CsvParser.parseLessonTitle(ByteArrayInputStream(csv.toByteArray()))
        assertNull(title)
    }

    @Test
    fun parseLessonTitle_stripsUtf8Bom() {
        // BOM префикс убирается
        val csv = "﻿A01 - Presente Indicativo\nЯ покупаю дом;Compro una casa"
        val title = CsvParser.parseLessonTitle(ByteArrayInputStream(csv.toByteArray()))
        assertEquals("A01 - Presente Indicativo", title)
    }

    @Test
    fun parseLessonTitle_handlesCyrillicTitle() {
        // Кириллический заголовок
        val csv = "Простые времена и порядок слов\nОн работает;He works"
        val title = CsvParser.parseLessonTitle(ByteArrayInputStream(csv.toByteArray()))
        assertEquals("Простые времена и порядок слов", title)
    }

    @Test
    fun parseLessonTitle_handlesTitleWithColon() {
        // Заголовок с двоеточием
        val csv = "B05 - Verbi: presente indicativo\nЯ покупаю;Io compro"
        val title = CsvParser.parseLessonTitle(ByteArrayInputStream(csv.toByteArray()))
        assertEquals("B05 - Verbi: presente indicativo", title)
    }

    @Test
    fun parseLessonTitle_handlesTitleWithHyphen() {
        // Заголовок с дефисом (формат: "A01 - Topic")
        val csv = "C18 - Idiomatica e collocazioni\nпокупаю дом;compro una casa"
        val title = CsvParser.parseLessonTitle(ByteArrayInputStream(csv.toByteArray()))
        assertEquals("C18 - Idiomatica e collocazioni", title)
    }

    @Test
    fun parseLessonTitle_titleOnlyNoCardParsing() {
        // Метод читает ТОЛЬКО строку 1, карточки не парсятся
        // Даже если в строке 2 есть данные карточки — они не возвращаются
        val csv = "My Lesson Title\nCard 1 Russian;Card 1 Answer\nCard 2 Russian;Card 2 Answer"
        val title = CsvParser.parseLessonTitle(ByteArrayInputStream(csv.toByteArray()))
        assertEquals("My Lesson Title", title)
        // Невозможно вернуть карточки из этого метода — сигнатура возвращает String?
    }

    @Test
    fun parseLessonTitle_longTitle_truncatedTo160() {
        // Длинный заголовок обрезается до 160 символов (как extractTitle)
        val longTitle = "A".repeat(200)
        val csv = "$longTitle\nCard;Answer"
        val title = CsvParser.parseLessonTitle(ByteArrayInputStream(csv.toByteArray()))
        assertEquals(160, title!!.length)
    }

    @Test
    fun parseLessonTitle_quotedTitle_stripsQuotes() {
        // Заголовок в кавычках — кавычки убираются
        val csv = "\"My Lesson Title\"\nCard;Answer"
        val title = CsvParser.parseLessonTitle(ByteArrayInputStream(csv.toByteArray()))
        assertEquals("My Lesson Title", title)
    }

    @Test
    fun parseLessonTitle_emptyTitle_returnsNull() {
        // Пустой заголовок (просто кавычки) → null
        val csv = "\"\"\nCard;Answer"
        val title = CsvParser.parseLessonTitle(ByteArrayInputStream(csv.toByteArray()))
        assertNull(title)
    }
}
