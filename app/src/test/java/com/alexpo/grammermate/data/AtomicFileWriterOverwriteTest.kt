package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * TASK-091 item 5b: replacing an existing target must never leave it absent.
 *
 * The old writeText/copyAtomic unconditionally deleted the target before
 * renameTo — a crash in that window is exactly the data loss AtomicFileWriter
 * exists to prevent, and the delete+sleep loop also cost up to 100ms on the
 * caller thread. The layered finalize (renameTo first, Files.move
 * REPLACE_EXISTING second, delete only as a last resort) must keep the target
 * present at every observable moment.
 *
 * Pure-JVM test (File only, no Android).
 */
class AtomicFileWriterOverwriteTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `overwrite replaces content`() {
        val file = tmp.newFile("target.yaml")

        AtomicFileWriter.writeText(file, "first")
        AtomicFileWriter.writeText(file, "second")

        assertEquals("second", file.readText())
    }

    @Test
    fun `overwrite never leaves target absent`() {
        val file = tmp.newFile("target.yaml")
        AtomicFileWriter.writeText(file, "initial")

        val running = AtomicBoolean(true)
        val sawAbsent = AtomicBoolean(false)
        val samples = AtomicLong(0)
        val watcher = Thread {
            while (running.get()) {
                if (!file.exists()) sawAbsent.set(true)
                samples.incrementAndGet()
            }
        }.apply {
            isDaemon = true
            start()
        }

        try {
            repeat(200) { AtomicFileWriter.writeText(file, "content-$it") }
        } finally {
            running.set(false)
            watcher.join(5000)
        }

        assertTrue("watcher must have sampled the file", samples.get() > 0)
        assertFalse(
            "target file disappeared during overwrite (absence observed after ${samples.get()} samples)",
            sawAbsent.get()
        )
        assertTrue(file.length() > 0)
    }

    @Test
    fun `copyAtomic over existing target never leaves it absent`() {
        val sourceFile = tmp.newFile("source.yaml")
        val targetFile = tmp.newFile("target.yaml")
        AtomicFileWriter.writeText(targetFile, "initial")

        val running = AtomicBoolean(true)
        val sawAbsent = AtomicBoolean(false)
        val watcher = Thread {
            while (running.get()) {
                if (!targetFile.exists()) sawAbsent.set(true)
            }
        }.apply {
            isDaemon = true
            start()
        }

        try {
            repeat(100) {
                AtomicFileWriter.writeText(sourceFile, "source-$it")
                AtomicFileWriter.copyAtomic(sourceFile, targetFile)
            }
        } finally {
            running.set(false)
            watcher.join(5000)
        }

        assertFalse("target file disappeared during atomic copy", sawAbsent.get())
        assertEquals("source-99", targetFile.readText())
    }
}
