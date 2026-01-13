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
        val rows = VocabCsvParser.parse(csv.byteInputStream())
        assertEquals(2, rows.size)
        assertEquals("hello", rows[0].nativeText)
        assertEquals("привет", rows[0].targetText)
        assertTrue(rows[1].isHard)
    }
}
