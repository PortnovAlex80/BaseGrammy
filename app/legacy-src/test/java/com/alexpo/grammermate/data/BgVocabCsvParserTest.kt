package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BgVocabCsvParser].
 *
 * Plain JVM tests — the parser operates on a [String] / [InputStream] and has
 * no Android dependencies. Robolectric is therefore unnecessary, matching the
 * sibling [VocabCsvParserTest] style.
 */
class BgVocabCsvParserTest {

    private val header =
        "rank,word,ru,collo_it,collo_ru,s1_it,s1_ru,s2_it,s2_ru,s3_it,s3_ru,s4_it,s4_ru,s5_it,s5_ru"

    @Test
    fun parse_threeSentences_rowKeepsFirstThreePairsOnly() {
        val csv = """
            $header
            1,casa,дом / жилище,bella casa,красивый дом,Ogni sera torno a casa.,Каждый вечер я возвращаюсь домой.,La casa è grande.,Дом большой.,Vado a casa.,Я иду домой.,,,,
        """.trimIndent()

        val rows = BgVocabCsvParser.parseString(csv)

        assertEquals(1, rows.size)
        val w = rows[0]
        assertEquals(1, w.rank)
        assertEquals("casa", w.wordIt)
        assertEquals("дом / жилище", w.wordRu)
        assertEquals("bella casa", w.colloIt)
        assertEquals("красивый дом", w.colloRu)
        assertEquals(3, w.sentences.size)
        assertEquals(PhrasePair("Ogni sera torno a casa.", "Каждый вечер я возвращаюсь домой."), w.sentences[0])
        assertEquals(PhrasePair("La casa è grande.", "Дом большой."), w.sentences[1])
        assertEquals(PhrasePair("Vado a casa.", "Я иду домой."), w.sentences[2])
    }

    @Test
    fun parse_fiveSentences_allPairsCaptured() {
        val csv = """
            $header
            2,tempo,время,tanto tempo,много времени,Frase 1 it,Frase 1 ru,Frase 2 it,Frase 2 ru,Frase 3 it,Frase 3 ru,Frase 4 it,Frase 4 ru,Frase 5 it,Frase 5 ru
        """.trimIndent()

        val rows = BgVocabCsvParser.parseString(csv)

        assertEquals(1, rows.size)
        val w = rows[0]
        assertEquals(5, w.sentences.size)
        for (i in 1..5) {
            assertEquals(PhrasePair("Frase $i it", "Frase $i ru"), w.sentences[i - 1])
        }
    }

    @Test
    fun parse_quotedFieldWithInternalComma_keptAsSingleCell() {
        // collo_ru contains a literal comma and is therefore quoted.
        val csv = """
            $header
            3,cane,собака,il cane nero,"чёрная, большая собака",Il cane abbaia.,Собака лает.,,,,,
        """.trimIndent()

        val rows = BgVocabCsvParser.parseString(csv)

        assertEquals(1, rows.size)
        assertEquals("чёрная, большая собака", rows[0].colloRu)
        assertEquals(1, rows[0].sentences.size)
        assertEquals(PhrasePair("Il cane abbaia.", "Собака лает."), rows[0].sentences[0])
    }

    @Test
    fun parse_quotedFieldWithEscapedDoubleQuote_unescaped() {
        // NOTE: built with a normal escaped string (not a raw """ string) because the CSV
        // cell "она сказала ""дом""" ends in a run of three double-quotes (дом"""), which
        // would prematurely terminate a Kotlin raw string literal. \" keeps it unambiguous
        // while preserving the exact CSV bytes the parser must handle.
        val csv = header + "\n" +
            "4,casa,дом,casa,\"она сказала \"\"дом\"\"\",Vado a casa.,Я иду домой.,,,,,"

        val rows = BgVocabCsvParser.parseString(csv)

        assertEquals(1, rows.size)
        assertEquals("она сказала \"дом\"", rows[0].colloRu)
    }

    @Test
    fun parse_multipleRows_preservesOrderAndRank() {
        val csv = """
            $header
            1,casa,дом,bella casa,красивый дом,s1it,s1ru,s2it,s2ru,,,,,
            7,tempo,время,tanto tempo,много времени,s1it,s1ru,s2it,s2ru,s3it,s3ru,s4it,s4ru,s5it,s5ru
        """.trimIndent()

        val rows = BgVocabCsvParser.parseString(csv)

        assertEquals(2, rows.size)
        assertEquals(1, rows[0].rank)
        assertEquals("casa", rows[0].wordIt)
        assertEquals(2, rows[0].sentences.size)
        assertEquals(7, rows[1].rank)
        assertEquals("tempo", rows[1].wordIt)
        assertEquals(5, rows[1].sentences.size)
    }

    @Test
    fun parse_blankRowsAreSkipped() {
        val csv = """
            $header

            1,casa,дом,bella casa,красивый дом,s1it,s1ru,,,,,

            2,tempo,время,tanto tempo,много времени,s1it,s1ru,,,,,
        """.trimIndent()

        val rows = BgVocabCsvParser.parseString(csv)

        assertEquals(2, rows.size)
        assertEquals("casa", rows[0].wordIt)
        assertEquals("tempo", rows[1].wordIt)
    }

    @Test
    fun parse_italianSentenceWithEmptyRussianTranslation_pairKeptWithBlankRu() {
        val csv = """
            $header
            1,casa,дом,bella casa,красивый дом,Sentence with no translation,,s2it,s2ru,,,,,
        """.trimIndent()

        val rows = BgVocabCsvParser.parseString(csv)

        assertEquals(1, rows.size)
        assertEquals(2, rows[0].sentences.size)
        assertEquals(PhrasePair("Sentence with no translation", ""), rows[0].sentences[0])
        assertEquals(PhrasePair("s2it", "s2ru"), rows[0].sentences[1])
    }

    @Test
    fun parse_missingHeader_throws() {
        val csv = "foo,bar,baz\n1,casa,дом"

        val ex = assertThrows(IllegalArgumentException::class.java) {
            BgVocabCsvParser.parseString(csv)
        }
        assertTrue(
            "Error should mention the expected header, was: ${ex.message}",
            ex.message?.contains("header") == true
        )
    }

    @Test
    fun parse_emptyAfterHeader_returnsEmptyList() {
        val csv = header
        val rows = BgVocabCsvParser.parseString(csv)
        assertTrue(rows.isEmpty())
    }

    @Test
    fun parse_outputRoundTripsThroughToMarkup() {
        val csv = """
            $header
            1,casa,дом,bella casa,красивый дом,Ogni sera torno a casa.,Каждый вечер я возвращаюсь домой.,,,,,
        """.trimIndent()

        val markup = BgVocabCsvParser.parseString(csv).single().toMarkup()
        val segments = MultilingualStoryParser.parseSegments(markup, defaultLanguageId = "it")

        // word + wordRu + collo + colloRu + 1 sentence.it + 1 sentence.ru = 6 Text,
        // interleaved with 6 pauses.
        assertEquals(12, segments.size)
        assertEquals("casa", (segments[0] as MultilingualStoryParser.Segment.Text).text)
        assertEquals("дом", (segments[2] as MultilingualStoryParser.Segment.Text).text)
        assertEquals("bella casa", (segments[4] as MultilingualStoryParser.Segment.Text).text)
        assertEquals("красивый дом", (segments[6] as MultilingualStoryParser.Segment.Text).text)
        assertEquals(
            "Ogni sera torno a casa.",
            (segments[8] as MultilingualStoryParser.Segment.Text).text
        )
    }

    private fun <T> List<T>.single(): T =
        if (size == 1) this[0] else error("Expected single element, got $size")
}
