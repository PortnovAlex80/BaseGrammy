package com.alexpo.grammermate.v2.core.data.packimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * VerbDrillCsvParser (AC-8, Фаза 5): header-детекция колонок, verb-fallback
 * из скобок, person из первого слова ответа, ID combo+index, partial-семантика.
 */
class VerbDrillCsvParserTest {

    private val csv = """
        Спряжение presente
        ru;it;verb;tense;group;rank
        я говорю;io parlo;parlare;presente;are;1
        ты говоришь;tu parli;parlare;presente;are;2
        я устал (essere stanco);sono stanco;;presente;essere;3
    """.trimIndent()

    @Test
    fun `happy path parses cards with combo fields and ids`() {
        val result = VerbDrillCsvParser.parse(csv)

        val cards = (result as ParseResult.Success).data
        assertThat(cards).hasSize(3)
        val first = cards[0]
        assertThat(first.promptRu).isEqualTo("я говорю")
        assertThat(first.answer).isEqualTo("io parlo")
        assertThat(first.verb).isEqualTo("parlare")
        assertThat(first.tense).isEqualTo("presente")
        assertThat(first.group).isEqualTo("are")
        assertThat(first.rank).isEqualTo(1)
        assertThat(first.person).isEqualTo("Io")
        assertThat(first.id).isEqualTo("are_presente_0")
        assertThat(cards[1].person).isEqualTo("Tu")
    }

    @Test
    fun `verb fallback extracted from parenthesized ru`() {
        val cards = (VerbDrillCsvParser.parse(csv) as ParseResult.Success).data

        assertThat(cards[2].verb).isEqualTo("essere")
        assertThat(cards[2].id).isEqualTo("essere_presente_2")
    }

    @Test
    fun `header without ru and it is invalid format`() {
        val bad = "Title\nfoo;bar\na;b"

        val result = VerbDrillCsvParser.parse(bad)

        val error = (result as ParseResult.Failure).errors.single() as ParseError.InvalidFormat
        assertThat(error.reason).contains("ru")
    }

    @Test
    fun `short row is malformed but rest parses (partial)`() {
        val partial = "T\nru;it\nтолько одна колонка\nя иду;io vado"

        val result = VerbDrillCsvParser.parse(partial)

        val pr = result as ParseResult.Partial
        assertThat(pr.data).hasSize(1)
        assertThat(pr.data.single().answer).isEqualTo("io vado")
        assertThat(pr.errors.single()).isInstanceOf(ParseError.MalformedLine::class.java)
    }

    @Test
    fun `empty file is EmptyFile failure`() {
        assertThat(VerbDrillCsvParser.parse("Title"))
            .isInstanceOf(ParseResult.Failure::class.java)
    }
}
