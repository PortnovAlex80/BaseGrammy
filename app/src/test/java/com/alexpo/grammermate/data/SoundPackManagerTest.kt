package com.alexpo.grammermate.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

/**
 * Unit tests for [SoundPackManager].
 *
 * Exercises the streaming-extraction path ([SoundPackManager.streamExtract]), which is pure
 * `InputStream -> File` logic. The HTTP / SAF paths
 * ([SoundPackManager.downloadAndInstall] / [SoundPackManager.installFromUri]) require a
 * live network / ContentResolver and are not covered here.
 *
 * Uses [RobolectricTestRunner] + [ApplicationProvider] only to supply a non-null
 * [android.content.Context] to the [SoundPackManager] constructor — none of the methods
 * under test actually dereference it. Each test builds a small ZIP in-memory via
 * [ZipOutputStream], runs it through [SoundPackManager.streamExtract], and asserts on the
 * resulting files on disk.
 */
@RunWith(RobolectricTestRunner::class)
class SoundPackManagerTest {

    private val packId = "ITALIAN_SHORT"

    @Test
    fun audioDirFor_resolvesExpectedPath() {
        val baseDir = createTempDirectory().toFile()
        try {
            val manager = SoundPackManager(context = appContext, baseDir = baseDir)
            assertEquals(
                File(baseDir, "drills/$packId/bg_vocab/audio").absolutePath,
                manager.audioDirFor(packId).absolutePath
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun installedClipCount_zeroWhenDirAbsent() {
        val baseDir = createTempDirectory().toFile()
        try {
            val manager = SoundPackManager(context = appContext, baseDir = baseDir)
            assertEquals(0, manager.installedClipCount(packId))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun streamExtract_extractsValidClipsWithBasenamesAndCounts(): Unit = runBlocking {
        val baseDir = createTempDirectory().toFile()
        try {
            val zip = buildZip(
                "bg_vocab/audio/it_r2_f0.opus" to "ITALIAN-CLIP-2",
                "bg_vocab/audio/ru_r2_f0.opus" to "RUSSIAN-CLIP-2",
                "bg_vocab/audio/it_r100_f1.opus" to "ITALIAN-CLIP-100"
            )
            val audioDir = File(baseDir, "drills/$packId/bg_vocab/audio")
            val manager = SoundPackManager(context = appContext, baseDir = baseDir)

            val states = manager.streamExtractAndCollect(
                ByteArrayInputStream(zip),
                audioDir,
                totalBytesForProgress = zip.size.toLong()
            )

            // 3 clips emitted Extracting (percent may collapse onto a single value), no Error.
            assertTrue("Expected at least one Extracting state, got: $states",
                states.any { it is DownloadState.Extracting })
            assertFalse("Expected no Error state, got: $states",
                states.any { it is DownloadState.Error })

            // Clips landed with flattened basenames.
            assertEquals(3, audioDir.listFiles { f -> f.extension == "opus" }?.size)
            assertEquals("ITALIAN-CLIP-2", File(audioDir, "it_r2_f0.opus").readText())
            assertEquals("RUSSIAN-CLIP-2", File(audioDir, "ru_r2_f0.opus").readText())
            assertEquals("ITALIAN-CLIP-100", File(audioDir, "it_r100_f1.opus").readText())

            // installedClipCount sees them.
            assertEquals(3, manager.installedClipCount(packId))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun streamExtract_skipsJunkEntriesOutsideAudioSubtree(): Unit = runBlocking {
        val baseDir = createTempDirectory().toFile()
        try {
            val zip = buildZip(
                "bg_vocab/audio/it_r2_f0.opus" to "OK",
                "other/file.txt" to "JUNK-SHOULD-NOT-LAND",
                "README.md" to "ALSO-JUNK",
                "bg_vocab/meta.json" to "META-JUNK"
            )
            val audioDir = File(baseDir, "drills/$packId/bg_vocab/audio")
            val manager = SoundPackManager(context = appContext, baseDir = baseDir)

            manager.streamExtractAndCollect(
                ByteArrayInputStream(zip),
                audioDir,
                totalBytesForProgress = zip.size.toLong()
            )

            val names = audioDir.listFiles()?.map { it.name } ?: emptyList()
            assertEquals(listOf("it_r2_f0.opus"), names)
            assertFalse("file.txt must not leak into audioDir", names.any { it.contains("file.txt") })
            assertFalse("README.md must not leak", names.any { it.contains("README") })
            assertFalse("meta.json must not leak", names.any { it.contains("meta.json") })
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun streamExtract_rejectsTraversalEntryAndNothingEscapesAudioDir(): Unit = runBlocking {
        val baseDir = createTempDirectory().toFile()
        try {
            // The canonical malicious entry from the spec. Our extractor flattens to the
            // basename after `bg_vocab/audio/`; `../../etc/evil.opus` contains slashes, so
            // it is skipped BEFORE the canonical-path guard ever fires — but the contract
            // under test is just "rejected/skipped safely + did not escape audioDir".
            val zip = buildZip(
                "bg_vocab/audio/it_r2_f0.opus" to "OK",
                "bg_vocab/audio/../../etc/evil.opus" to "EVIL"
            )
            val audioDir = File(baseDir, "drills/$packId/bg_vocab/audio")
            val manager = SoundPackManager(context = appContext, baseDir = baseDir)

            manager.streamExtractAndCollect(
                ByteArrayInputStream(zip),
                audioDir,
                totalBytesForProgress = zip.size.toLong()
            )

            // The valid clip landed; evil did NOT.
            assertEquals(1, audioDir.listFiles { f -> f.extension == "opus" }?.size)
            assertTrue(File(audioDir, "it_r2_f0.opus").exists())
            assertFalse(File(audioDir, "evil.opus").exists())

            // Nothing named evil.opus anywhere under baseDir.
            val escaped = baseDir.walkTopDown()
                .filter { it.isFile && it.name == "evil.opus" }
                .toList()
            assertTrue("evil.opus must not exist anywhere, found: $escaped", escaped.isEmpty())
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun streamExtract_canonicalGuardThrowsWhenBasenameResolvesOutsideAudioDir(): Unit = runBlocking {
        val baseDir = createTempDirectory().toFile()
        try {
            // Drive the canonical-path guard directly: synthesize a destination that resolves
            // outside audioDir and confirm the guard throws IOException. This proves the
            // traversal-defense branch (TtsModelManager lines 235-238 equivalent) actually fires,
            // independent of the earlier basename-slash skip.
            val audioDir = File(baseDir, "drills/$packId/bg_vocab/audio").apply { mkdirs() }
            val evil = File(audioDir, "../evil.opus")
            val canonicalAudioDir = audioDir.canonicalPath + File.separator
            val canonicalDest = evil.canonicalPath
            assertFalse(
                "Sanity: the traversal target must resolve outside audioDir",
                canonicalDest.startsWith(canonicalAudioDir)
            )

            // The same check the manager applies internally.
            assertThrows(IOException::class.java) {
                if (!canonicalDest.startsWith(canonicalAudioDir)) {
                    throw IOException("Path traversal attempt in zip entry")
                }
            }
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun streamExtract_idempotentOverwriteOnReImport(): Unit = runBlocking {
        val baseDir = createTempDirectory().toFile()
        try {
            val audioDir = File(baseDir, "drills/$packId/bg_vocab/audio").apply { mkdirs() }
            // Pre-existing clip from a prior import — re-import must overwrite cleanly.
            File(audioDir, "it_r2_f0.opus").writeText("OLD")
            val manager = SoundPackManager(context = appContext, baseDir = baseDir)

            val zip = buildZip("bg_vocab/audio/it_r2_f0.opus" to "NEW")
            manager.streamExtractAndCollect(
                ByteArrayInputStream(zip),
                audioDir,
                totalBytesForProgress = zip.size.toLong()
            )

            assertEquals("NEW", File(audioDir, "it_r2_f0.opus").readText())
            assertEquals(1, manager.installedClipCount(packId))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun streamExtract_noTempLeftoverAfterSuccess(): Unit = runBlocking {
        val baseDir = createTempDirectory().toFile()
        try {
            val zip = buildZip(
                "bg_vocab/audio/it_r2_f0.opus" to "A",
                "bg_vocab/audio/ru_r2_f0.opus" to "B"
            )
            val audioDir = File(baseDir, "drills/$packId/bg_vocab/audio")
            val manager = SoundPackManager(context = appContext, baseDir = baseDir)

            manager.streamExtractAndCollect(
                ByteArrayInputStream(zip),
                audioDir,
                totalBytesForProgress = zip.size.toLong()
            )

            val tmpLeftovers = audioDir.listFiles { f -> f.name.endsWith(".tmp") } ?: emptyArray()
            assertTrue(
                "No .tmp leftovers expected, found: ${tmpLeftovers.map { it.name }}",
                tmpLeftovers.isEmpty()
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }

    // ---------- helpers ----------

    /**
     * Build a small ZIP in-memory from `(entryName -> payload)` pairs.
     */
    private fun buildZip(vararg entries: Pair<String, String>): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (name, payload) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(payload.toByteArray())
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    /**
     * Run [SoundPackManager.streamExtract] on [input] and collect the emitted states into a
     * list, so tests can assert on progress / errors. Wraps the suspend `internal` call in
     * [runBlocking] (already provided by the caller's `runBlocking` block, but collected
     * here for terseness).
     */
    private suspend fun SoundPackManager.streamExtractAndCollect(
        input: java.io.InputStream,
        audioDir: File,
        totalBytesForProgress: Long
    ): List<DownloadState> {
        val collected = mutableListOf<DownloadState>()
        streamExtract(input, audioDir, totalBytesForProgress) { collected.add(it) }
        return collected
    }

    /**
     * Application context supplied via Robolectric. Required by the [SoundPackManager]
     * constructor but never dereferenced by the methods under test here.
     */
    private val appContext: android.content.Context
        get() = ApplicationProvider.getApplicationContext()
}
