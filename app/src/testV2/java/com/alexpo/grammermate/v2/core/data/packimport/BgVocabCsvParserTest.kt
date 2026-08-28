package com.alexpo.grammermate.v2.core.data.packimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * BgVocabCsvParser (AC-11, Фаза 5): фикс H-6 — невалидный header даёт
 * Failure(InvalidFormat), а не IllegalArgumentException; RFC-4180-цитирование;
 * стоп пустого s{n}_it; partial по битым строкам.
 */
class BgVocabCsvParserTest {

    private val header =
        "rank,word,ru,collo_it,collo_ru,s1_it,s1_ru,s2_it,s2_ru,s3_it,s3_ru,s4_it,s4_ru,s5_it,s5_ru"

    private fun row(
        rank: String = "1",
        word: String = "casa",
        ru: String = "дом",
        colloIt: String = "la casa bianca",
        colloRu: String = "белый дом",
        s1: Pair<String, String> = "la casa è grande" to "дом большой",
        s2: Pair<String, String> = "a casa" to "дома",
        s3: Pair<String, String>? = null,
    ): String {
        val pairs = listOf(s1, s2, s3, null, null).take(5)
        val cells = mutableListOf(rank, word, ru, colloIt, colloRu)
        var stopped = false
        for (i in 1..5) {
            val p = pairs.getOrNull(i - 1)
            if (p == null || stopped) {
                cells += ""; cells += ""
                stopped = true
            } else {
                cells += p.first
                cells += p.second
            }
        }
        return cells.joinToString(",")
    }

    @Test
    fun `happy path parses script with sentences and stop-on-empty`() {
        val result = BgVocabCsvParser.parse(
            (header + "\n" + row(s3 = "in casa" to "в доме")).byteInputStream()
        )

        val scripts = (result as ParseResult.Success).data
        assertThat(scripts).hasSize(1)
        val s = scripts.single()
        assertThat(s.rank).isEqualTo(1)
        assertThat(s.wordIt).isEqualTo("casa")
        assertThat(s.wordRu).isEqualTo("дом")
        assertThat(s.colloIt).isEqualTo("la casa bianca")
        assertThat(s.sentences.map { it.target })
            .containsExactly("la casa è grande", "a casa", "in casa")
            .inOrder()
        assertThat(s.sentences[1].translation).isEqualTo("дома")
    }

    @Test
    fun `bad header is InvalidFormat not IllegalArgumentException (H-6)`() {
        val bad = "rank,word,ru\n1,casa,дом"

        val result = BgVocabCsvParser.parse(bad.byteInputStream())

        val error = (result as ParseResult.Failure).errors.single() as ParseError.InvalidFormat
        assertThat(error.reason).contains("15")
    }

    @Test
    fun `rfc4180 quoted commas and escaped quotes survive`() {
        val quoted = "2,\"parola, con virgola\",слово,\"frase, con\",перевод" +
            ",\"Он сказал \"\"ciao\"\"\",перевод1,,,,,,,,,"
        val result = BgVocabCsvParser.parse((header + "\n" + quoted).byteInputStream())

        val s = (result as ParseResult.Success).data.single()
        assertThat(s.wordIt).isEqualTo("parola, con virgola")
        assertThat(s.colloIt).isEqualTo("frase, con")
        assertThat(s.sentences.single().target).isEqualTo("Он сказал \"ciao\"")
    }

    @Test
    fun `non-integer rank is malformed and rest parses (partial)`() {
        val bad = "x,casa,дом,c1,c1ru,s1,s1ru,,,,,,,,,"
        val result = BgVocabCsvParser.parse((header + "\n" + bad + "\n" + row(rank = "7")).byteInputStream())

        val partial = result as ParseResult.Partial
        assertThat(partial.data).hasSize(1)
        assertThat(partial.data.single().rank).isEqualTo(7)
        assertThat(partial.errors.single()).isInstanceOf(ParseError.MalformedLine::class.java)
    }

    @Test
    fun `header-only file is EmptyFile`() {
        val result = BgVocabCsvParser.parse(header.byteInputStream())

        assertThat((result as ParseResult.Failure).errors.single())
            .isInstanceOf(ParseError.EmptyFile::class.java)
    }
}
