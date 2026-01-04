package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class CsvParserTest {
    @Test
    fun parseLesson_readsTitleAndCards() {
        // Агенту запрещено изменять тесты без согласования с пользователем.
        val csv = """
Simple tenses and word order
Он не работает из дома;He doesn't work from home
""".trimIndent()
        val (title, cards) = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertEquals("Simple tenses and word order", title)
        assertEquals(1, cards.size)
        assertEquals("Он не работает из дома", cards[0].promptRu)
        assertEquals("He doesn't work from home", cards[0].acceptedAnswers.first())
    }

    @Test
    fun parseLesson_titleStopsOnPunctuation() {
        // Агенту запрещено изменять тесты без согласования с пользователем.
        val csv = """
Simple tenses: basics
Он работает из дома;He works from home
""".trimIndent()
        val (title, _) = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertEquals("Simple tenses", title)
    }

    @Test
    fun parseLesson_titleStripsUtf8Bom() {
        // Агенту запрещено изменять тесты без согласования с пользователем.
        val csv = "\uFEFFSimple tenses\nОн работает из дома;He works from home"
        val (title, _) = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertEquals("Simple tenses", title)
    }

    @Test
    fun parseLesson_emptyTitleBecomesNull() {
        // Агенту запрещено изменять тесты без согласования с пользователем.
        val csv = "\"\"\nОн работает из дома;He works from home"
        val (title, _) = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertNull(title)
    }
}
