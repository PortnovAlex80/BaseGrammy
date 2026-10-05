package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AtomicFileWriterPlatformTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test @Config(sdk = [24])
    fun api24UsesThePlatformRenameWithoutNioTypes() {
        assertFalse(AtomicFileWriter.supportsNioMove())
        val file = File(temporary.root, "progress.yaml")
        AtomicFileWriter.writeText(file, "saved progress")
        assertEquals("saved progress", file.readText())
    }

    @Test @Config(sdk = [25])
    fun api25DoesNotEnterTheNioFallback() {
        assertFalse(AtomicFileWriter.supportsNioMove())
    }

    @Test @Config(sdk = [26])
    fun api26CanUseAtomicNioReplacement() {
        assertTrue(AtomicFileWriter.supportsNioMove())
    }
}
