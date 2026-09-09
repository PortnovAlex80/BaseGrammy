package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class VerbDrillCsvParserTest {
    @Test

    fun parseValidVerbDrill() {
        val csv = """
Italian Verb Drill
ru;it;verb;tense;group;rank
io lavoro;I work;lavorare;Presente;io;1
tu lavori;you work;lavorare;Presente;io;2
""".trimIndent()
        val result = VerbDrillCsvParser.parse(csv)
        assertTrue(result.isSuccess)
        assertEquals(0, result.errors.size)
        val cards = result.data!!
        assertEquals(2, cards.size)
        assertEquals("io lavoro", cards[0].promptRu)
        assertEquals("I work", cards[0].acceptedAnswers[0])
        assertEquals("lavorare", cards[0].verb)
        assertEquals("Presente", cards[0].tense)
        assertEquals("io", cards[0].group)
        assertEquals(1, cards[0].rank)
    }

    @Test
    fun parse_emptyFile_returnsFailure() {
        val csv = ""
        val result = VerbDrillCsvParser.parse(csv)
        assertTrue(!result.isSuccess)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test

    fun parse_malformedCsv_returnsPartial() {
        val csv = """
Italian Verb Drill
ru;it;verb;tense;group;rank
io lavoro;I work;lavorare;Presente;io;1
invalid line without enough columns
tu lavori;you work;lavorare;Presente;io;2
""".trimIndent()
        val result = VerbDrillCsvParser.parse(csv)
        assertTrue(result.isPartial)
        assertTrue(result.errors.isNotEmpty())
        val cards = result.data!!
        assertEquals(2, cards.size)
    }

    @Test

    fun parse_specialCharacters_handlesCorrectly() {
        val csv = """
Italian Verb Drill
ru;it;verb;tense;group;rank
io sono 👍;I am 👍;essere;Presente;io;1
""".trimIndent()
        val result = VerbDrillCsvParser.parse(csv)
        assertTrue(result.isSuccess)
        val cards = result.data!!
        assertEquals(1, cards.size)
        assertEquals("io sono 👍", cards[0].promptRu)
        assertEquals("I am 👍", cards[0].acceptedAnswers[0])
    }

    @Test
    fun parse_missingRequiredColumns_returnsErrors() {
        val csv = """
Italian Verb Drill
ru,verb
io lavoro;lavorare
""".trimIndent()
        val result = VerbDrillCsvParser.parse(csv)
        assertTrue(!result.isSuccess || result.isPartial)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test

    fun parseLineNumbers_includedInErrors() {
        val csv = """
Italian Verb Drill
ru;it;verb;tense;group;rank
io lavoro;I work;lavorare;Presente;io;1
bad line
tu lavori;you work;lavorare;Presente;io;2
""".trimIndent()
        val result = VerbDrillCsvParser.parse(csv)
        assertTrue(result.isPartial)
        val error = result.errors.first()
        assertTrue(error.lineNumber > 0)
    }
}
