package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Regression test for the slow startup caused by `forceReloadDefaultPacks` re-importing
 * every background-vocab audio clip on every launch via [AtomicFileWriter.copyAtomic].
 *
 * On-device measurement: 20817 audio files re-written per restart, ~2min54s of fsync,
 * because the import never skipped clips that were already on disk with identical size.
 *
 * Fix contract: [AtomicFileWriter.copyAtomicIfChanged] copies only when the target is
 * absent OR differs in size from the source; otherwise it is a no-op (no temp, no fsync,
 * no rename). This makes re-import idempotent and cheap when nothing changed.
 *
 * Pure-JVM test (File only, no Android).
 */
class AtomicFileWriterSkipIfSameTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `copyAtomicIfChanged writes when target absent`() {
        val src = tmp.newFile("src.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val dst = File(tmp.root, "dst.bin")

        AtomicFileWriter.copyAtomicIfChanged(src, dst)

        assertTrue("target must exist after copy", dst.exists())
        assertEquals("content must match source", src.length(), dst.length())
    }

    @Test
    fun `copyAtomicIfChanged skips when target exists with same size`() {
        val src = tmp.newFile("src.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val dst = File(tmp.root, "dst.bin").apply { writeBytes(byteArrayOf(9, 9, 9, 9)) } // same size, different bytes

        AtomicFileWriter.copyAtomicIfChanged(src, dst)

        assertTrue("target must still exist", dst.exists())
        assertEquals("size unchanged", 4L, dst.length())
        // Bytes should be PRESERVED (not overwritten by src) — proving the copy was skipped.
        assertEquals("existing content must be untouched when size matches", 9.toByte(), dst.readBytes()[0])
    }

    @Test
    fun `copyAtomicIfChanged overwrites when target exists with different size`() {
        val src = tmp.newFile("src.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6)) } // 6 bytes
        val dst = File(tmp.root, "dst.bin").apply { writeBytes(byteArrayOf(9, 9)) }          // 2 bytes (stale)

        AtomicFileWriter.copyAtomicIfChanged(src, dst)

        assertTrue("target must exist", dst.exists())
        assertEquals("size must be updated to source size", src.length(), dst.length())
        assertEquals("content must be replaced by source", 1.toByte(), dst.readBytes()[0])
        assertNotEquals("must not equal stale byte", 9.toByte(), dst.readBytes()[0])
    }
}
