package com.alexpo.grammermate.data

import com.alexpo.grammermate.data.MultilingualStoryParser.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WordScript.toMarkup].
 *
 * These are plain JVM tests (no Robolectric): [WordScript] has no Android
 * dependencies. A round-trip through [MultilingualStoryParser.parseSegments]
 * is included to prove the rendered markup is well-formed and consumable by
 * the Wave 1 playback path.
 */
class WordScriptToMarkupTest {

    private fun script(
        sentences: List<PhrasePair>,
        pauses: ScriptPauses = ScriptPauses()
    ) = WordScript(
        rank = 1,
        wordIt = "casa",
        wordRu = "дом, жилище",
        colloIt = "bella casa",
        colloRu = "красивый дом",
        sentences = sentences,
        pauses = pauses
    )

    @Test
    fun twoSentences_matchesExactExpectedMarkup() {
        val ws = script(
            sentences = listOf(
                PhrasePair("Ogni sera torno a casa.", "Каждый вечер я возвращаюсь домой."),
                PhrasePair("La casa è grande.", "Дом большой.")
            )
        )

        val expected =
            "{it}casa{/it}{pause:300}" +
            "{ru}дом, жилище{/ru}{pause:500}" +
            "{it}bella casa{/it}{pause:300}" +
            "{ru}красивый дом{/ru}{pause:500}" +
            "{it}Ogni sera torno a casa.{/it}{pause:400}" +
            "{ru}Каждый вечер я возвращаюсь домой.{/ru}{pause:400}" +
            "{it}La casa è grande.{/it}{pause:400}" +
            "{ru}Дом большой.{/ru}{pause:400}"

        assertEquals(expected, ws.toMarkup())
    }

    @Test
    fun threeSentences_emitsAllPairsInOrderWithAfterSentencePause() {
        val ws = script(
            sentences = listOf(
                PhrasePair("s1_it", "s1_ru"),
                PhrasePair("s2_it", "s2_ru"),
                PhrasePair("s3_it", "s3_ru")
            )
        )

        val markup = ws.toMarkup()

        // Fixed prefix: word, translation, collocation, collocation-translation.
        val prefix =
            "{it}casa{/it}{pause:300}" +
            "{ru}дом, жилище{/ru}{pause:500}" +
            "{it}bella casa{/it}{pause:300}" +
            "{ru}красивый дом{/ru}{pause:500}"

        val suffix =
            "{it}s1_it{/it}{pause:400}{ru}s1_ru{/ru}{pause:400}" +
            "{it}s2_it{/it}{pause:400}{ru}s2_ru{/ru}{pause:400}" +
            "{it}s3_it{/it}{pause:400}{ru}s3_ru{/ru}{pause:400}"

        assertEquals(prefix + suffix, markup)
    }

    @Test
    fun zeroSentences_endsWithCollocationTranslationPause() {
        val ws = script(sentences = emptyList())

        val expected =
            "{it}casa{/it}{pause:300}" +
            "{ru}дом, жилище{/ru}{pause:500}" +
            "{it}bella casa{/it}{pause:300}" +
            "{ru}красивый дом{/ru}{pause:500}"

        assertEquals(expected, ws.toMarkup())
    }

    @Test
    fun oneSentence_singlePairWithAfterSentencePause() {
        val ws = script(sentences = listOf(PhrasePair("only it", "only ru")))

        assertEquals(
            "{it}casa{/it}{pause:300}" +
            "{ru}дом, жилище{/ru}{pause:500}" +
            "{it}bella casa{/it}{pause:300}" +
            "{ru}красивый дом{/ru}{pause:500}" +
            "{it}only it{/it}{pause:400}{ru}only ru{/ru}{pause:400}",
            ws.toMarkup()
        )
    }

    @Test
    fun customPauses_substitutedIntoMarkup() {
        val ws = script(
            sentences = listOf(PhrasePair("a", "b")),
            pauses = ScriptPauses(
                afterWord = 10,
                afterTranslation = 20,
                afterCollocation = 30,
                afterSentence = 40
            )
        )

        assertEquals(
            "{it}casa{/it}{pause:10}" +
            "{ru}дом, жилище{/ru}{pause:20}" +
            "{it}bella casa{/it}{pause:30}" +
            "{ru}красивый дом{/ru}{pause:20}" +
            "{it}a{/it}{pause:40}{ru}b{/ru}{pause:40}",
            ws.toMarkup()
        )
    }

    @Test
    fun bracesInFieldValue_areEscapedAndDoNotCorruptParser() {
        // A stray "{" must not be able to synthesize a fake {/it} or {pause:..}.
        val ws = WordScript(
            rank = 2,
            wordIt = "foo{bar}baz",
            wordRu = "ru",
            colloIt = "collo",
            colloRu = "colloRu",
            sentences = listOf(PhrasePair("s", "t"))
        )

        val markup = ws.toMarkup()

        // The literal braces must not appear unescaped inside the tagged run.
        assertTrue(
            "Expected no literal '{bar}' in markup, got: $markup",
            !markup.contains("foo{bar}baz")
        )

        // And the parser must still produce exactly the expected segment count:
        // word + wordRu + collo + colloRu + sentence.it + sentence.ru = 6 Text
        // segments, with 6 interleaved pauses (afterWord, afterWordRu,
        // afterCollo, afterColloRu, afterSentenceIt, afterSentenceRu).
        val segments = MultilingualStoryParser.parseSegments(markup, defaultLanguageId = "it")
        assertEquals(12, segments.size)
        assertTrue(segments[0] is Segment.Text)
        assertEquals("it", (segments[0] as Segment.Text).languageId)
        assertTrue(segments[1] is Segment.Pause)
        assertEquals(300L, (segments[1] as Segment.Pause).ms)
    }

    @Test
    fun roundTrip_parsesToExpectedItRuAlternation() {
        val ws = script(
            sentences = listOf(
                PhrasePair("Ogni sera torno a casa.", "Каждый вечер я возвращаюсь домой.")
            )
        )

        val segments = MultilingualStoryParser.parseSegments(ws.toMarkup(), defaultLanguageId = "it")

        // 5 it/ru text runs + 5 pauses = 10 segments.
        assertEquals(10, segments.size)
        assertEquals(Segment.Text("casa", "it"), segments[0])
        assertEquals(Segment.Pause(300L), segments[1])
        assertEquals(Segment.Text("дом, жилище", "ru"), segments[2])
        assertEquals(Segment.Pause(500L), segments[3])
        assertEquals(Segment.Text("bella casa", "it"), segments[4])
        assertEquals(Segment.Pause(300L), segments[5])
        assertEquals(Segment.Text("красивый дом", "ru"), segments[6])
        assertEquals(Segment.Pause(500L), segments[7])
        assertEquals(Segment.Text("Ogni sera torno a casa.", "it"), segments[8])
        assertEquals(Segment.Pause(400L), segments[9])
    }
}
