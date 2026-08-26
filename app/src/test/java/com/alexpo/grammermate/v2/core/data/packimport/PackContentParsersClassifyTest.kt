package com.alexpo.grammermate.v2.core.data.packimport

import com.alexpo.grammermate.v2.core.data.packimport.PackContentParsers.ContentType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * PackContentParsers.classify (AC-1/AC-4, Фаза 5): диспетчеризация файлов
 * пака; `.md`-стори НЕ игнорируются (фикс аудита M-3 — STORY_MD).
 */
class PackContentParsersClassifyTest {

    @Test
    fun `classification by name and extension`() {
        assertThat(PackContentParsers.classify("manifest.json")).isEqualTo(ContentType.MANIFEST)
        assertThat(PackContentParsers.classify("vocab_nouns.csv")).isEqualTo(ContentType.VOCAB_CSV)
        assertThat(PackContentParsers.classify("story_ch1.json")).isEqualTo(ContentType.STORY_JSON)
        assertThat(PackContentParsers.classify("lesson_01_A01.csv")).isEqualTo(ContentType.LESSON_CSV)
        assertThat(PackContentParsers.classify("intro.mp3")).isEqualTo(ContentType.IGNORED)
        assertThat(PackContentParsers.classify("voice.opus")).isEqualTo(ContentType.IGNORED)
    }

    @Test
    fun `markdown stories are STORY_MD not ignored (M-3)`() {
        assertThat(PackContentParsers.classify("chapter_00_original.md"))
            .isEqualTo(ContentType.STORY_MD)
        assertThat(PackContentParsers.classify("stories/ru/chapter_01.md"))
            .isEqualTo(ContentType.STORY_MD)
    }

    @Test
    fun `nested paths and case-insensitive extensions`() {
        assertThat(PackContentParsers.classify("lessons/lesson_02.CSV"))
            .isEqualTo(ContentType.LESSON_CSV)
        assertThat(PackContentParsers.classify("deep/nested/manifest.json"))
            .isEqualTo(ContentType.MANIFEST)
    }
}
