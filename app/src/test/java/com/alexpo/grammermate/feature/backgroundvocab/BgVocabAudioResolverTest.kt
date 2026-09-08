package com.alexpo.grammermate.feature.backgroundvocab

import com.alexpo.grammermate.data.SpeakSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Unit tests for [BgVocabAudioResolver].
 *
 * Plain JVM tests — the resolver operates only on [java.io.File] paths and has no
 * Android dependencies, matching the sibling [com.alexpo.grammermate.data.BgVocabCsvParserTest]
 * style. Temp dirs are created via [createTempDirectory] (kotlin.io.path) and cleaned
 * up in `finally`, mirroring `AtomicFileWriterTest`.
 */
class BgVocabAudioResolverTest {

    private val packId = "TEST_PACK"

    @Test
    fun fileFor_wordIt_returnsPaddedPathWhenPresent() {
        val baseDir = createTempDirectory().toFile()
        try {
            createClip(baseDir, "it_000002_word.wav")
            val resolver = BgVocabAudioResolver(baseDir)

            val file = resolver.fileFor(packId, rank = 2, slot = SpeakSlot.WordIt)

            assertEquals(
                File(baseDir, "drills/$packId/bg_vocab/audio/it_000002_word.wav").absolutePath,
                file?.absolutePath
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun fileFor_wordRu_resolvesRussianWordClip() {
        val baseDir = createTempDirectory().toFile()
        try {
            createClip(baseDir, "ru_000002_word.wav")
            val resolver = BgVocabAudioResolver(baseDir)

            val file = resolver.fileFor(packId, rank = 2, slot = SpeakSlot.WordRu)

            assertEquals(
                File(baseDir, "drills/$packId/bg_vocab/audio/ru_000002_word.wav").absolutePath,
                file?.absolutePath
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun fileFor_colloIt_resolvesCollocationClip() {
        val baseDir = createTempDirectory().toFile()
        try {
            createClip(baseDir, "it_000002_collo.wav")
            val resolver = BgVocabAudioResolver(baseDir)

            val file = resolver.fileFor(packId, rank = 2, slot = SpeakSlot.ColloIt)

            assertEquals(
                File(baseDir, "drills/$packId/bg_vocab/audio/it_000002_collo.wav").absolutePath,
                file?.absolutePath
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun fileFor_sentenceRu_indexTwo_mapsToS3() {
        val baseDir = createTempDirectory().toFile()
        try {
            createClip(baseDir, "ru_000002_s3.wav")
            val resolver = BgVocabAudioResolver(baseDir)

            // SentenceRu(2) -> field s{2+1} = s3
            val file = resolver.fileFor(packId, rank = 2, slot = SpeakSlot.SentenceRu(2))

            assertEquals(
                File(baseDir, "drills/$packId/bg_vocab/audio/ru_000002_s3.wav").absolutePath,
                file?.absolutePath
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun fileFor_returnsNullWhenClipAbsent() {
        val baseDir = createTempDirectory().toFile()
        try {
            // No files created — every lookup must fall back to null (TTS).
            val resolver = BgVocabAudioResolver(baseDir)

            val file = resolver.fileFor(packId, rank = 2, slot = SpeakSlot.WordIt)

            assertNull(file)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun fileFor_sentenceIt_indexZero_mapsToS1AndResolves() {
        val baseDir = createTempDirectory().toFile()
        try {
            createClip(baseDir, "it_000042_s1.wav")
            val resolver = BgVocabAudioResolver(baseDir)

            // SentenceIt(0) -> field s1; rank 42 pads to 000042.
            val file = resolver.fileFor(packId, rank = 42, slot = SpeakSlot.SentenceIt(0))

            assertEquals(
                File(baseDir, "drills/$packId/bg_vocab/audio/it_000042_s1.wav").absolutePath,
                file?.absolutePath
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }

    /**
     * Create the expected clip file (and its parent dirs) under [baseDir] for [name]
     * matching the resolver's path convention. Content is irrelevant — the resolver
     * only checks existence.
     */
    private fun createClip(baseDir: File, name: String): File {
        val file = File(baseDir, "drills/$packId/bg_vocab/audio/$name")
        file.parentFile?.mkdirs()
        file.writeText("dummy")
        return file
    }
}
