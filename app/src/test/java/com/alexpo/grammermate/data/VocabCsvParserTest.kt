package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabCsvParserTest {
    @Test
    fun parseRows() {
        val csv = """
            hello;привет
            bye;пока;hard
        """.trimIndent()
        val result = VocabCsvParser.parse(csv.byteInputStream())
        assertTrue(result.isSuccess)
        assertEquals(0, result.errors.size)
        val rows = result.data!!
        assertEquals(2, rows.size)
        assertEquals("hello", rows[0].nativeText)
        assertEquals("привет", rows[0].targetText)
        assertTrue(rows[1].isHard)
    }

    @Test
    fun parse_emptyFile_returnsFailure() {
        val csv = ""
        val result = VocabCsvParser.parse(csv.byteInputStream())
        assertTrue(!result.isSuccess)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun parse_specialCharacters_handlesCorrectly() {
        val csv = "café;кафе;hard"
        val result = VocabCsvParser.parse(csv.byteInputStream())
        assertTrue(result.isSuccess)
        val rows = result.data!!
        assertEquals(1, rows.size)
        assertEquals("café", rows[0].nativeText)
        assertEquals("кафе", rows[0].targetText)
    }

    @Test
    fun parse_malformedLine_returnsPartial() {
        val csv = """
hello;привет
invalid line without separator
bye;пока
        """.trimIndent()
        val result = VocabCsvParser.parse(csv.byteInputStream())
        assertTrue(result.isPartial)
        assertTrue(result.errors.isNotEmpty())
        val rows = result.data!!
        assertEquals(2, rows.size)
    }
}
