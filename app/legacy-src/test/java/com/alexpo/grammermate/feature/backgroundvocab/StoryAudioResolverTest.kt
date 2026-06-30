package com.alexpo.grammermate.feature.backgroundvocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Unit tests for [StoryAudioResolver].
 *
 * Plain JVM tests — the resolver operates only on [java.io.File] paths and has no
 * Android dependencies, matching the sibling [BgVocabAudioResolverTest] style. Temp
 * dirs are created via [createTempDirectory] (kotlin.io.path) and cleaned up in
 * `finally`.
 */
class StoryAudioResolverTest {

    private val packId = "ITALIAN_SHORT"

    @Test
    fun chapterNumberFromStoryFile_parsesTwoDigitNumber() {
        val baseDir = createTempDirectory().toFile()
        try {
            val resolver = StoryAudioResolver(baseDir)

            assertEquals("00", resolver.chapterNumberFromStoryFile("chapter_00.md"))
            assertEquals("03", resolver.chapterNumberFromStoryFile("chapter_03.md"))
            assertEquals("06", resolver.chapterNumberFromStoryFile("chapter_06.md"))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun chapterNumberFromStoryFile_padsSingleDigitToTwo() {
        val baseDir = createTempDirectory().toFile()
        try {
            val resolver = StoryAudioResolver(baseDir)

            // Single-digit chapter numbers are zero-padded to 2 digits.
            assertEquals("03", resolver.chapterNumberFromStoryFile("chapter_3.md"))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun chapterNumberFromStoryFile_toleratesSuffixAndPath() {
        val baseDir = createTempDirectory().toFile()
        try {
            val resolver = StoryAudioResolver(baseDir)

            // Tolerates language suffix variants and a leading path segment.
            assertEquals("02", resolver.chapterNumberFromStoryFile("chapter_02_original.md"))
            assertEquals("05", resolver.chapterNumberFromStoryFile("stories/chapter_05.md"))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun chapterNumberFromStoryFile_returnsNullForGarbageInput() {
        val baseDir = createTempDirectory().toFile()
        try {
            val resolver = StoryAudioResolver(baseDir)

            assertNull(resolver.chapterNumberFromStoryFile(null))
            assertNull(resolver.chapterNumberFromStoryFile(""))
            assertNull(resolver.chapterNumberFromStoryFile("intro.md"))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun resolveChapterAudio_returnsOpusWhenPresent() {
        val baseDir = createTempDirectory().toFile()
        try {
            createClip(baseDir, "book3_ch03.opus")
            val resolver = StoryAudioResolver(baseDir)

            val file = resolver.resolveChapterAudio(packId, "chapter_03.md")

            assertEquals(
                File(baseDir, "packs/$packId/stories/audio/book3_ch03.opus").absolutePath,
                file?.absolutePath
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun resolveChapterAudio_padsSingleDigitChapterNumber() {
        val baseDir = createTempDirectory().toFile()
        try {
            // The opus bank uses 2-digit zero-padded names (book3_ch00.opus … ch06.opus).
            createClip(baseDir, "book3_ch02.opus")
            val resolver = StoryAudioResolver(baseDir)

            val file = resolver.resolveChapterAudio(packId, "chapter_2.md")

            assertEquals(
                File(baseDir, "packs/$packId/stories/audio/book3_ch02.opus").absolutePath,
                file?.absolutePath
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun resolveChapterAudio_returnsNullWhenClipAbsent() {
        val baseDir = createTempDirectory().toFile()
        try {
            // No clip on disk — caller must fall back to TTS.
            val resolver = StoryAudioResolver(baseDir)

            val file = resolver.resolveChapterAudio(packId, "chapter_04.md")

            assertNull(file)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun resolveChapterAudio_returnsNullForNullStoryFile() {
        val baseDir = createTempDirectory().toFile()
        try {
            val resolver = StoryAudioResolver(baseDir)

            assertNull(resolver.resolveChapterAudio(packId, null))
            assertNull(resolver.resolveChapterAudio(packId, ""))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    /**
     * Create the expected opus clip (and its parent dirs) under [baseDir] for [name]
     * matching the resolver's path convention. Content is irrelevant — the resolver
     * only checks existence/readability.
     */
    private fun createClip(baseDir: File, name: String): File {
        val file = File(baseDir, "packs/$packId/stories/audio/$name")
        file.parentFile?.mkdirs()
        file.writeText("dummy-opus")
        return file
    }
}
