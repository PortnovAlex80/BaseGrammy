package com.alexpo.grammermate.v2.core.data.packimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * VocabCsvParser (AC-9, Фаза 5): H-5 решён — pos из имени файла, rank =
 * индекс строки; native→meaningRu, target→word; hard-флаги; partial.
 */
class VocabCsvParserTest {

    private fun csv(vararg rows: String) = rows.joinToString("\n")

    @Test
    fun `pos from file name, rank is row index, id composed`() {
        val result = VocabCsvParser.parse(
            csv("дом;casa", "книга;libro;hard").byteInputStream(),
            fileName = "packs/it/vocab_nouns.csv",
        )

        val rows = (result as ParseResult.Success).data
        assertThat(rows).hasSize(2)
        assertThat(rows[0].pos).isEqualTo("nouns")
        assertThat(rows[0].rank).isEqualTo(0)
        assertThat(rows[0].word).isEqualTo("casa")
        assertThat(rows[0].meaningRu).isEqualTo("дом")
        assertThat(rows[0].isHard).isFalse()
        assertThat(rows[0].id).isEqualTo("nouns_0_casa")
        assertThat(rows[1].rank).isEqualTo(1)
        assertThat(rows[1].isHard).isTrue()
    }

    @Test
    fun `hard flag variants are case-insensitive`() {
        val rows = (VocabCsvParser.parse(
            csv("a;b;HARD", "c;d;1", "e;f;True", "g;h;false").byteInputStream(),
            "vocab_verbs.csv",
        ) as ParseResult.Success).data

        assertThat(rows.map { it.isHard }).containsExactly(true, true, true, false).inOrder()
    }

    @Test
    fun `file without vocab prefix falls back to misc pos`() {
        val rows = (VocabCsvParser.parse(
            "дом;casa".byteInputStream(), "words.csv",
        ) as ParseResult.Success).data

        assertThat(rows.single().pos).isEqualTo("misc")
        assertThat(rows.single().id).isEqualTo("misc_0_casa")
    }

    @Test
    fun `single column row is malformed, rest parses (partial)`() {
        val result = VocabCsvParser.parse(
            csv("только-одна-колонка", "дом;casa").byteInputStream(),
            "vocab_nouns.csv",
        )

        val partial = result as ParseResult.Partial
        assertThat(partial.data).hasSize(1)
        assertThat(partial.data.single().word).isEqualTo("casa")
        assertThat(partial.errors.single()).isInstanceOf(ParseError.MalformedLine::class.java)
    }

    @Test
    fun `empty stream is EmptyFile`() {
        val result = VocabCsvParser.parse("".byteInputStream(), "vocab_nouns.csv")

        assertThat(result).isInstanceOf(ParseResult.Failure::class.java)
        assertThat((result as ParseResult.Failure).errors.single())
            .isInstanceOf(ParseError.EmptyFile::class.java)
    }
}
