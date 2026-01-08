package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class AtomicFileWriterTest {
    @Test
    fun writeTextWritesContent() {
        val dir = createTempDirectory().toFile()
        try {
            val file = File(dir, "sample.txt")
            AtomicFileWriter.writeText(file, "hello")
            assertEquals("hello", file.readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}
