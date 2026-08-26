package com.alexpo.grammermate.domain.story

import com.alexpo.grammermate.domain.model.Segment
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Фиксы аудита 2026-08-26 в story-парсере (срез 6 Фазы 4):
 *  - C-2: {pause:N} overflow/верхняя граница;
 *  - H-1: detectLanguage матчит целые слова, не подстроки;
 *  - M-9: cleanMarkdown MULTILINE + list-маркеры только в начале строки;
 *  - M-11: висячие языковые маркеры вырезаются из чистого текста.
 */
class MultilingualStoryParserAuditFixTest {

    // ── C-2 ───────────────────────────────────────────────────────────────────

    @Test
    fun `pause overflow does not throw and is capped`() {
        val segments = MultilingualStoryParser.parseSegments(
            "a {pause:99999999999999999999} b {pause:9223372036854775807} c {pause:250} d",
            defaultLanguageId = "it",
        )
        val pauses = segments.filterIsInstance<Segment.Pause>()
        // 20-значное значение не парсится в Long → маркер пропущен (без краха);
        // Long.MAX_VALUE парсится, но ограничено cap=600000.
        assertThat(pauses.map { it.ms }).containsExactly(600_000L, 250L).inOrder()
        assertThat(segments.filterIsInstance<Segment.Text>().map { it.text })
            .containsExactly("a", "b", "c", "d")
            .inOrder()
    }

    @Test
    fun `invalid pause marker is skipped silently`() {
        // Гипотетический невалидный маркер (не-цифры) не матчится regex вовсе;
        // документируем контракт: битые маркеры не создают Pause-сегментов.
        val segments = MultilingualStoryParser.parseSegments(
            "x {pause:abc} y {pause:100} z",
            defaultLanguageId = "en",
        )
        assertThat(segments.filterIsInstance<Segment.Pause>().map { it.ms }).containsExactly(100L)
    }

    // ── H-1 ───────────────────────────────────────────────────────────────────

    @Test
    fun `english with italian substring traps detects english not italian`() {
        // "dialog" содержит "di", "family" — "il", "under" — "un": до фикса
        // italianWordMatches >= 5 давало ложное "it".
        val english =
            "The family dialogue continued under the old bridge. " +
                "They would perform a different program together."
        assertThat(MultilingualStoryParser.detectLanguage(english, defaultLanguageId = "ru"))
            .isEqualTo("en")
    }

    @Test
    fun `real italian words still detect italian`() {
        val italian = "Questo cosa possiamo fare per la casa? Anche parlare con lei."
        assertThat(MultilingualStoryParser.detectLanguage(italian, defaultLanguageId = "ru"))
            .isEqualTo("it")
    }

    // ── M-9 ───────────────────────────────────────────────────────────────────

    @Test
    fun `cleanMarkdown removes midDocument headers`() {
        val md = "Intro text.\n\n## Chapter Two\n\nBody after header."
        val cleaned = MultilingualStoryParser.cleanMarkdown(md)
        assertThat(cleaned).doesNotContain("Chapter Two")
        assertThat(cleaned).contains("Intro text.")
        assertThat(cleaned).contains("Body after header.")
    }

    @Test
    fun `cleanMarkdown keeps inline hyphens and strips list markers`() {
        val md = "well-known -suffix stays\n- item one\n* item two"
        val cleaned = MultilingualStoryParser.cleanMarkdown(md)
        assertThat(cleaned).contains("well-known -suffix stays")
        assertThat(cleaned).doesNotContain("- item")
        assertThat(cleaned).doesNotContain("* item")
        assertThat(cleaned).contains("item one")
    }

    // ── M-11 ──────────────────────────────────────────────────────────────────

    @Test
    fun `stripMarkers removes orphan unclosed markers`() {
        val content = "{it}Ciao {ru}Привет{/ru} resto senza chiusura"
        val stripped = MultilingualStoryParser.stripMarkers(content)
        // Незакрытый {it} вырезан; парный {ru}..{/ru} дал чистый контент.
        assertThat(stripped).doesNotContain("{it}")
        assertThat(stripped).doesNotContain("{ru}")
        assertThat(stripped).contains("Привет")
        assertThat(stripped).contains("resto senza chiusura")
    }
}
