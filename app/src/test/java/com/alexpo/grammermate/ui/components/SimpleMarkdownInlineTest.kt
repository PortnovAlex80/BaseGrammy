package com.alexpo.grammermate.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Строчная разметка в чипах и историях.
 *
 * Раньше парсер только вырезал символы разметки и возвращал плоский String:
 * `**текст**` терял звёздочки, но жирным не становился, а обратные кавычки не
 * обрабатывались вовсе — в грамматических чипах они были видны глазом.
 */
class SimpleMarkdownInlineTest {

    private val code = Color(0xFF00AA88)

    private fun render(src: String) = SimpleMarkdownParser.buildInline(src, code)

    @Test
    fun boldMarkersRemovedAndTextIsBold() {
        val out = render("это **важно** здесь")
        assertThat(out.text).isEqualTo("это важно здесь")
        val span = out.spanStyles.single()
        assertThat(span.item.fontWeight).isEqualTo(FontWeight.Bold)
        assertThat(out.text.substring(span.start, span.end)).isEqualTo("важно")
    }

    @Test
    fun inlineCodeGetsMonospaceAndAccentColour() {
        val out = render("форма `avessi comprato` здесь")
        assertThat(out.text).isEqualTo("форма avessi comprato здесь")
        val span = out.spanStyles.single()
        assertThat(span.item.fontFamily).isEqualTo(FontFamily.Monospace)
        assertThat(span.item.color).isEqualTo(code)
        assertThat(out.text.substring(span.start, span.end)).isEqualTo("avessi comprato")
    }

    @Test
    fun italicIsRecognisedButDoesNotSwallowBold() {
        val out = render("**жирный** и *курсив*")
        assertThat(out.text).isEqualTo("жирный и курсив")
        val weights = out.spanStyles.map { it.item.fontWeight to it.item.fontStyle }
        assertThat(weights).containsExactly(
            FontWeight.Bold to null,
            null to FontStyle.Italic
        )
    }

    @Test
    fun severalCodeSpansOnOneLine() {
        val out = render("`uno` и `due` и `tre`")
        assertThat(out.text).isEqualTo("uno и due и tre")
        assertThat(out.spanStyles).hasSize(3)
        assertThat(out.spanStyles.map { out.text.substring(it.start, it.end) })
            .containsExactly("uno", "due", "tre").inOrder()
    }

    @Test
    fun plainTextIsUntouched() {
        val src = "Обычная строка без разметки"
        val out = render(src)
        assertThat(out.text).isEqualTo(src)
        assertThat(out.spanStyles).isEmpty()
    }

    @Test
    fun unpairedMarkerStaysLiteral() {
        // одиночная звёздочка не должна съедать остаток строки
        val out = render("2 * 3 = 6")
        assertThat(out.text).isEqualTo("2 * 3 = 6")
        assertThat(out.spanStyles).isEmpty()
    }
}
