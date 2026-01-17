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

    // ========================================
    // Дополнительные тесты (P1)
    // ========================================

    @Test
    fun parseLesson_multipleAcceptedAnswers_splitsByPlus() {
        // FR-3.2.3.3: Множественные правильные ответы через +
        val csv = """
Simple tenses
Он работает;He works+He is working+He does work
""".trimIndent()
        val (_, cards) = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertEquals(1, cards.size)
        assertEquals(3, cards[0].acceptedAnswers.size)
        assertEquals("He works", cards[0].acceptedAnswers[0])
        assertEquals("He is working", cards[0].acceptedAnswers[1])
        assertEquals("He does work", cards[0].acceptedAnswers[2])
    }

    @Test
    fun parseLesson_emptyLines_ignored() {
        // FR-3.2.3.4: Пустые строки игнорируются
        val csv = """
Simple tenses

Он работает;He works

Она учится;She studies
""".trimIndent()
        val (_, cards) = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertEquals(2, cards.size)
        assertEquals("Он работает", cards[0].promptRu)
        assertEquals("Она учится", cards[1].promptRu)
    }

    @Test
    fun parseLesson_lineWithoutSeparator_ignored() {
        // FR-3.2.3.4: Строка без разделителя игнорируется
        val csv = """
Simple tenses
Он работает;He works
Invalid line without separator
Она учится;She studies
""".trimIndent()
        val (_, cards) = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertEquals(2, cards.size)
        assertEquals("Он работает", cards[0].promptRu)
        assertEquals("Она учится", cards[1].promptRu)
    }

    @Test
    fun parseLesson_extraFields_ignored() {
        // Строки с более чем 2 полями игнорируются (размер != 2)
        val csv = """
Simple tenses
Он работает;He works;extra;data
Она учится;She studies
""".trimIndent()
        val (_, cards) = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertEquals(1, cards.size)
        assertEquals("Она учится", cards[0].promptRu)
        assertEquals("She studies", cards[0].acceptedAnswers[0])
    }

    @Test
    fun parseLesson_whitespaceAroundFields_trimmed() {
        // Пробелы вокруг полей обрезаются
        val csv = """
Simple tenses
  Он работает  ;  He works
""".trimIndent()
        val (_, cards) = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertEquals(1, cards.size)
        assertEquals("Он работает", cards[0].promptRu)
        assertEquals("He works", cards[0].acceptedAnswers[0])
    }
}
