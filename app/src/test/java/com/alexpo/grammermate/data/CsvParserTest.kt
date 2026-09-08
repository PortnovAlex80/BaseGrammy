package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
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
        val result = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertTrue(result.isSuccess)
        assertEquals(0, result.errors.size)
        val (title, cards) = result.data!!
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
        val result = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertTrue(result.isSuccess)
        assertEquals(0, result.errors.size)
        val (title, cards) = result.data!!
        assertEquals(1, cards.size)
        assertEquals("Он работает из дома", cards[0].promptRu)
    }

    @Test
    fun parseLesson_titleStripsUtf8Bom() {
        // Агенту запрещено изменять тесты без согласования с пользователем.
        val csv = "﻿Simple tenses\nОн работает из дома;He works from home"
        val result = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertTrue(result.isSuccess)
        assertEquals(0, result.errors.size)
        val (title, cards) = result.data!!
        assertEquals(1, cards.size)
        assertEquals("Он работает из дома", cards[0].promptRu)
    }

    @Test
    fun parseLesson_emptyTitleBecomesNull() {
        // Агенту запрещено изменять тесты без согласования с пользователем.
        val csv = "\"\"\nОн работает из дома;He works from home"
        val result = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertTrue(result.isSuccess)
        assertEquals(0, result.errors.size)
        val (_, cards) = result.data!!
        assertEquals(1, cards.size)
        assertEquals("Он работает из дома", cards[0].promptRu)
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
        val result = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertTrue(result.isSuccess)
        assertEquals(0, result.errors.size)
        val (_, cards) = result.data!!
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
        val result = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertTrue(result.isSuccess)
        val (_, cards) = result.data!!
        assertEquals(2, cards.size)
        assertEquals("Он работает", cards[0].promptRu)
        assertEquals("Она учится", cards[1].promptRu)
    }

    @Test
    @Ignore("Phase 0 quarantine — malformed-line expectation drift (legacy-test-quarantine.md)")
    fun parseLesson_lineWithoutSeparator_ignored() {
        // FR-3.2.3.4: Строка без разделителя игнорируется
        val csv = """
Simple tenses
Он работает;He works
Invalid line without separator
Она учится;She studies
""".trimIndent()
        val result = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertTrue(result.isSuccess)
        val (_, cards) = result.data!!
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
        val result = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertTrue(result.isPartial)
        assertEquals(1, result.errors.size)
        val (_, cards) = result.data!!
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
        val result = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertTrue(result.isSuccess)
        val (_, cards) = result.data!!
        assertEquals(1, cards.size)
        assertEquals("Он работает", cards[0].promptRu)
        assertEquals("He works", cards[0].acceptedAnswers[0])
    }

    // ========================================
    // Wave 4: ParseResult error handling tests
    // ========================================

    @Test
    fun parseLesson_emptyFile_returnsFailure() {
        val csv = ""
        val result = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertTrue(!result.isSuccess)
        assertTrue(!result.isPartial)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.first() is ParseError.EmptyFile)
    }

    @Test
    fun parseLesson_malformedLine_returnsPartial() {
        val csv = """
Simple tenses
Он работает;He works
Invalid line without separator
Она учится;She studies
""".trimIndent()
        val result = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertTrue(result.isPartial)
        assertEquals(1, result.errors.size)
        val error = result.errors.first()
        assertTrue(error is ParseError.MalformedLine)
        assertEquals(3, error.lineNumber)
    }

    @Test
    fun parseLesson_specialCharacters_handlesCorrectly() {
        val csv = """
Special chars
Привет 🌟;Hello 🌟
Как дела?;How are you?
""".trimIndent()
        val result = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        assertTrue(result.isSuccess)
        val (_, cards) = result.data!!
        assertEquals(2, cards.size)
        assertEquals("Привет 🌟", cards[0].promptRu)
        assertEquals("Hello 🌟", cards[0].acceptedAnswers[0])
    }

    @Test
    fun parseLesson_userMessage_formatsErrors() {
        val csv = """
Test
Он работает;He works
Invalid line
Она учится;She studies
""".trimIndent()
        val result = CsvParser.parseLesson(ByteArrayInputStream(csv.toByteArray()))
        val message = result.getUserMessage { it.toUserMessage() }
        assertTrue(message.contains("1 error(s)"))
        assertTrue(message.contains("Line 3"))
    }
}
