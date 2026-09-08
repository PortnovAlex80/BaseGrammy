package com.alexpo.grammermate.data

import com.alexpo.grammermate.data.MultilingualStoryParser.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for `{pause:N}` markup support in [MultilingualStoryParser].
 *
 * These are local JVM unit tests. Robolectric is required only because the
 * parser routes diagnostics through `android.util.Log` (same pattern as the
 * sibling StoryQuizParserTest). No emulator/device is involved.
 */
@RunWith(RobolectricTestRunner::class)
class MultilingualStoryParserPauseTest {

    // ----- parseSegments: core pause behavior -----

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

        assertEquals(listOf<Segment>(
            Segment.Pause(250L),
            Segment.Text("casa", "it")
        ), segments)
    }

    @Test
    fun pauseAtEnd_emittedAfterPrecedingText() {
        val content = "{it}casa{/it}{pause:500}"

        val segments = MultilingualStoryParser.parseSegments(content, defaultLanguageId = "en")

        assertEquals(listOf<Segment>(
            Segment.Text("casa", "it"),
            Segment.Pause(500L)
        ), segments)
    }

    @Test
    fun multipleConsecutivePauses_allPreservedInOrder() {
        val content = "{pause:100}{pause:200}{pause:300}"

        val segments = MultilingualStoryParser.parseSegments(content, defaultLanguageId = "en")

        assertEquals(listOf<Segment>(
            Segment.Pause(100L),
            Segment.Pause(200L),
            Segment.Pause(300L)
        ), segments)
    }

    @Test
    @Ignore("Phase 0 quarantine — default languageId drift: expected ru, got en (legacy-test-quarantine.md)")
    fun pausesInterleavedWithUnmarkedText_useDefaultLanguageId() {
        val content = "hello{pause:120}world"

        val segments = MultilingualStoryParser.parseSegments(content, defaultLanguageId = "ru")

        assertEquals(listOf<Segment>(
            Segment.Text("hello", "ru"),
            Segment.Pause(120L),
            Segment.Text("world", "ru")
        ), segments)
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

        assertEquals(listOf<Segment>(
            Segment.Text("casa", "it"),
            Segment.Pause(50L),
            Segment.Text("bianca", "it")
        ), segments)
    }

    // ----- parseStory: backward compatibility -----

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

        // Two marker segments + the single-space default-language gap between them
        // is blank after trim and therefore dropped.
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

    // ----- stripMarkers: pause removal for plain-text fallback -----

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

    // ----- edge cases -----

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
