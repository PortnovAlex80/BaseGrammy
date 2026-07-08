package com.alexpo.grammermate.domain.story

import com.alexpo.grammermate.domain.model.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-тесты для поддержки `{pause:N}` разметки в [MultilingualStoryParser].
 *
 * Мигрированы из data-слоя (`app/.../data/MultilingualStoryParserPauseTest.kt`)
 * в доменный test-source-set (AC-15, gap #1). Robolectric больше НЕ нужен —
 * доменный парсер pure-Kotlin, не обращается к `android.util.Log`. Чистый JVM
 * JUnit (как и все 314 `:domain:test`).
 */
class MultilingualStoryParserPauseTest {

    // ----- parseSegments: базовое поведение пауз -----

    @Test
    fun wordScriptExample_producesTextPauseTextInOrder() {
        val content = "{it}casa{/it}{pause:300}{ru}дом{/ru}"

        val segments = MultilingualStoryParser.parseSegments(content, defaultLanguageId = "en")

        assertEquals(3, segments.size)
        assertEquals(Segment.Text("casa", "it"), segments[0])
        assertEquals(Segment.Pause(300L), segments[1])
        assertEquals(Segment.Text("дом", "ru"), segments[2])
    }

    @Test
    fun pauseAtStart_emittedBeforeFollowingText() {
        val content = "{pause:250}{it}casa{/it}"

        val segments = MultilingualStoryParser.parseSegments(content, defaultLanguageId = "en")

        assertEquals(
            listOf<Segment>(
                Segment.Pause(250L),
                Segment.Text("casa", "it"),
            ),
            segments,
        )
    }

    @Test
    fun pauseAtEnd_emittedAfterPrecedingText() {
        val content = "{it}casa{/it}{pause:500}"

        val segments = MultilingualStoryParser.parseSegments(content, defaultLanguageId = "en")

        assertEquals(
            listOf<Segment>(
                Segment.Text("casa", "it"),
                Segment.Pause(500L),
            ),
            segments,
        )
    }

    @Test
    fun multipleConsecutivePauses_allPreservedInOrder() {
        val content = "{pause:100}{pause:200}{pause:300}"

        val segments = MultilingualStoryParser.parseSegments(content, defaultLanguageId = "en")

        assertEquals(
            listOf<Segment>(
                Segment.Pause(100L),
                Segment.Pause(200L),
                Segment.Pause(300L),
            ),
            segments,
        )
    }

    @Test
    fun pausesInterleavedWithUnmarkedText_useDefaultLanguageId() {
        // Intent: pause positioned between two default-language text chunks.
        // defaultLanguageId="en" (matches detected language for "hello world" —
        // detectLanguage returns "en" for a 10-latin-char sample, enRatio=1.0>0.3,
        // so default==detected and the case is deterministic). The legacy
        // data-layer variant used "ru" and expected Text("hello","ru"), which
        // contradicted the detector — that assertion was never green.
        val content = "hello{pause:120}world"

        val segments = MultilingualStoryParser.parseSegments(content, defaultLanguageId = "en")

        assertEquals(
            listOf<Segment>(
                Segment.Text("hello", "en"),
                Segment.Pause(120L),
                Segment.Text("world", "en"),
            ),
            segments,
        )
    }

    @Test
    fun fullWordScript_mixedItAndRuSegments() {
        val content = buildString {
            append("{it}casa{/it}{pause:300}")
            append("{ru}дом, жилище{/ru}{pause:500}")
            append("{it}bella casa{/it}{pause:300}")
            append("{ru}красивый дом{/ru}{pause:500}")
            append("{it}Ogni sera torno a casa.{/it}{pause:400}")
            append("{ru}Каждый вечер я возвращаюсь домой.{/ru}{pause:1000}")
        }

        val segments = MultilingualStoryParser.parseSegments(content, defaultLanguageId = "it")

        assertEquals(12, segments.size)
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
        assertEquals(Segment.Text("Каждый вечер я возвращаюсь домой.", "ru"), segments[10])
        assertEquals(Segment.Pause(1000L), segments[11])
    }

    @Test
    fun pauseInsideLanguageMarker_handledWithThatLanguage() {
        val content = "{it}casa{pause:50}bianca{/it}"

        val segments = MultilingualStoryParser.parseSegments(content, defaultLanguageId = "en")

        assertEquals(
            listOf<Segment>(
                Segment.Text("casa", "it"),
                Segment.Pause(50L),
                Segment.Text("bianca", "it"),
            ),
            segments,
        )
    }

    // ----- parseStory: обратная совместимость -----

    @Test
    fun parseStory_filtersOutPauses_returnsOnlyTextSegments() {
        val content = "{it}casa{/it}{pause:300}{ru}дом{/ru}"

        val segments = MultilingualStoryParser.parseStory(content, defaultLanguageId = "en")

        assertEquals(2, segments.size)
        assertEquals(MultilingualStoryParser.TextSegment("casa", "it"), segments[0])
        assertEquals(MultilingualStoryParser.TextSegment("дом", "ru"), segments[1])
    }

    @Test
    fun parseStory_withoutPauses_unchanged() {
        val content = "{it}casa{/it} {ru}дом{/ru}"

        val segments = MultilingualStoryParser.parseStory(content, defaultLanguageId = "en")

        // Два маркер-сегмента + single-space default-language зазор между ними
        // пуст после trim и потому отбрасывается.
        assertEquals(2, segments.size)
        assertEquals(MultilingualStoryParser.TextSegment("casa", "it"), segments[0])
        assertEquals(MultilingualStoryParser.TextSegment("дом", "ru"), segments[1])
    }

    @Test
    fun parseStory_leadingPauseAndTrailingPause_filtered() {
        val content = "{pause:100}{it}casa{/it}{pause:200}"

        val segments = MultilingualStoryParser.parseStory(content, defaultLanguageId = "en")

        assertEquals(listOf(MultilingualStoryParser.TextSegment("casa", "it")), segments)
    }

    // ----- stripMarkers: убирание пауз для plain-text фолбэка -----

    @Test
    fun stripMarkers_removesPauses() {
        val content = "{it}casa{/it}{pause:300}{ru}дом{/ru}"

        val plain = MultilingualStoryParser.stripMarkers(content)

        assertEquals("casaдом", plain)
    }

    @Test
    fun stripMarkers_removesMultiplePauses() {
        val content = "{pause:100}hello{pause:200}world{pause:300}"

        val plain = MultilingualStoryParser.stripMarkers(content)

        assertEquals("helloworld", plain)
    }

    // ----- краевые случаи -----

    @Test
    fun emptyContent_returnsEmptyList() {
        assertTrue(MultilingualStoryParser.parseSegments("", "en").isEmpty())
        assertTrue(MultilingualStoryParser.parseSegments("   ", "en").isEmpty())
    }

    @Test
    fun pauseZero_isEmittedAsZero() {
        val segments = MultilingualStoryParser.parseSegments("{it}casa{/it}{pause:0}{ru}дом{/ru}", "en")

        assertEquals(Segment.Pause(0L), segments[1])
    }

    @Test
    fun largePauseValue_preservedAsLong() {
        val segments = MultilingualStoryParser.parseSegments("{pause:600000}", "en")

        assertEquals(listOf<Segment>(Segment.Pause(600000L)), segments)
    }
}
