package com.alexpo.grammermate.v2.core.data.packimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Регрессия CsvParser (Фаза 1 плана стабилизации 2026-08-26): порт legacy
 * 1:1 — утверждения переносятся без изменения бизнес-смысла (SRS-002 NFR-6).
 * Чистый JVM, без Android.
 */
class CsvParserTest {

    private fun parse(text: String) =
        CsvParser.parseLesson(text.byteInputStream(Charsets.UTF_8))

    @Test
    fun happyPath_titleAndCards() {
        val result = parse("Урок 1\nПривет;ciao+salve\nДо встречи;arrivederci\n")

        val (title, cards) = (result as ParseResult.Success).data
        assertThat(title).isEqualTo("Урок 1")
        assertThat(cards).hasSize(2)
        assertThat(cards[0].id).isEqualTo("card_2")
        assertThat(cards[0].promptRu).isEqualTo("Привет")
        assertThat(cards[0].acceptedAnswers).containsExactly("ciao", "salve").inOrder()
        assertThat(cards[1].id).isEqualTo("card_3")
        assertThat(cards[1].acceptedAnswers).containsExactly("arrivederci")
    }

    @Test
    fun malformedLine_partialSuccess() {
        val result = parse(
            "Урок\nПривет;ciao\nсто;колонка;лишняя\nПока;arrivederci\n"
        )

        val partial = result as ParseResult.Partial
        assertThat(partial.data.second).hasSize(2)
        assertThat(partial.errors).hasSize(1)
        assertThat(partial.errors.first()).isInstanceOf(ParseError.MalformedLine::class.java)
    }

    @Test
    fun emptyFile_failure() {
        val result = parse("")

        val failure = result as ParseResult.Failure
        assertThat(failure.errors).containsExactly(ParseError.EmptyFile(lineNumber = 0))
    }

    @Test
    fun blankRuOrAnswers_error() {
        val result = parse("Урок\n  ;ciao\n")

        val failure = result as ParseResult.Failure
        assertThat(failure.errors).hasSize(1)
        assertThat(failure.errors.first()).isInstanceOf(ParseError.MalformedLine::class.java)
    }

    @Test
    fun moreThanThreeEmptyLines_error() {
        val result = parse("Урок\nПривет;ciao\n\n\n\n\n")

        val partial = result as ParseResult.Partial
        assertThat(partial.data.second).hasSize(1)
        assertThat(partial.errors).isNotEmpty()
    }

    @Test
    fun quotedSeparator_staysSingleAnswer() {
        // `;` внутри кавычек не делит колонки (CsvLineParser).
        val result = parse("Урок\nПривет;\"ciao;salve\"\n")

        val (title, cards) = (result as ParseResult.Success).data
        assertThat(title).isEqualTo("Урок")
        assertThat(cards.single().acceptedAnswers).containsExactly("ciao;salve")
    }

    @Test
    fun title_trimmedQuotedAndBomStripped() {
        // BOM перед чистым заголовком снимается.
        val bom = "﻿"
        val bomResult = parse("${bom}Урок 1\nПривет;ciao\n")
        assertThat((bomResult as ParseResult.Success).data.first).isEqualTo("Урок 1")

        // Кавычки вокруг заголовка снимаются с обеих сторон.
        val quoted = parse("\"Урок: первая лекция\"\nПривет;ciao\n")
        assertThat((quoted as ParseResult.Success).data.first).isEqualTo("Урок: первая лекция")
    }
}
