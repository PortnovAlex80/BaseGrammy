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
     * matching the resolver's path convention, inside the [pack]'s audio bank (the
     * test pack by default). Content is irrelevant — the resolver only checks existence.
     */
    private fun createClip(baseDir: File, name: String, pack: String = packId): File {
        val file = File(baseDir, "drills/$pack/bg_vocab/audio/$name")
        file.parentFile?.mkdirs()
        file.writeText("dummy")
        return file
    }

    // ── Corpus-scoped bank fallback (ADR-034) ─────────────────────────────────

    @Test
    fun fileForRank_fallsBackToSiblingPackBank_whenActivePackHasNoClips() {
        val baseDir = createTempDirectory().toFile()
        try {
            // Bank installed for ITALIAN_SHORT; the listener is active on ITALIAN_IMPERIAL
            // (whose manifest declares no backgroundVocab at all — the bundled deck plays).
            createClip(baseDir, "it_r7_f0.opus", pack = "ITALIAN_SHORT")
            val resolver = BgVocabAudioResolver(baseDir)

            val file = resolver.fileForRank("ITALIAN_IMPERIAL", rank = 7, slot = SpeakSlot.WordIt)

            assertEquals(
                File(baseDir, "drills/ITALIAN_SHORT/bg_vocab/audio/it_r7_f0.opus").absolutePath,
                file?.absolutePath
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun fileForRank_resolvesFromSiblingBanks_whenPackIdNull() {
        val baseDir = createTempDirectory().toFile()
        try {
            // No active pack (user on Home, bundled deck) — sibling banks still resolve.
            createClip(baseDir, "ru_r7_f0.opus", pack = "ITALIAN_SHORT")
            val resolver = BgVocabAudioResolver(baseDir)

            val file = resolver.fileForRank(null, rank = 7, slot = SpeakSlot.WordRu)

            assertEquals(
                File(baseDir, "drills/ITALIAN_SHORT/bg_vocab/audio/ru_r7_f0.opus").absolutePath,
                file?.absolutePath
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun fileForRank_activePackBankWinsOverSiblingBanks() {
        val baseDir = createTempDirectory().toFile()
        try {
            createClip(baseDir, "it_r7_f0.opus", pack = "TEST_PACK")
            createClip(baseDir, "it_r7_f0.opus", pack = "ITALIAN_SHORT")
            val resolver = BgVocabAudioResolver(baseDir)

            val file = resolver.fileForRank(packId, rank = 7, slot = SpeakSlot.WordIt)

            assertEquals(
                File(baseDir, "drills/$packId/bg_vocab/audio/it_r7_f0.opus").absolutePath,
                file?.absolutePath
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun fileForRank_siblingOrderDeterministic_lowestPackIdWins() {
        val baseDir = createTempDirectory().toFile()
        try {
            createClip(baseDir, "it_r7_f0.opus", pack = "ZETA_PACK")
            createClip(baseDir, "it_r7_f0.opus", pack = "ALPHA_PACK")
            val resolver = BgVocabAudioResolver(baseDir)

            val file = resolver.fileForRank(null, rank = 7, slot = SpeakSlot.WordIt)

            assertEquals(
                "Sibling banks resolve in ascending pack-id order",
                File(baseDir, "drills/ALPHA_PACK/bg_vocab/audio/it_r7_f0.opus").absolutePath,
                file?.absolutePath
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun fileFor_wavLookupUsesSameSiblingFallback() {
        val baseDir = createTempDirectory().toFile()
        try {
            createClip(baseDir, "it_000002_word.wav", pack = "ITALIAN_SHORT")
            val resolver = BgVocabAudioResolver(baseDir)

            val file = resolver.fileFor(null, rank = 2, slot = SpeakSlot.WordIt)

            assertEquals(
                File(baseDir, "drills/ITALIAN_SHORT/bg_vocab/audio/it_000002_word.wav").absolutePath,
                file?.absolutePath
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun fileForRank_returnsNullWhenNoBankAnywhere() {
        val baseDir = createTempDirectory().toFile()
        try {
            // Pack dirs exist but no bg_vocab/audio bank is installed for anyone.
            File(baseDir, "drills/ITALIAN_SHORT").mkdirs()
            File(baseDir, "drills/ITALIAN_IMPERIAL").mkdirs()
            val resolver = BgVocabAudioResolver(baseDir)

            assertNull(resolver.fileForRank(null, rank = 7, slot = SpeakSlot.WordIt))
            assertNull(resolver.fileForRank("ITALIAN_IMPERIAL", rank = 7, slot = SpeakSlot.WordIt))
        } finally {
            baseDir.deleteRecursively()
        }
    }
}
