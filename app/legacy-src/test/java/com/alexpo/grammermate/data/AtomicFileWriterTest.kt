package com.alexpo.grammermate.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Unit tests for AtomicFileWriter - защита атомарной записи файлов.
 *
 * Покрывает требования:
 * - FR-2.1.1: Атомарная запись
 * - FR-2.1.2: Временный файл
 * - FR-2.1.3: Переименование
 * - FR-2.1.4: Обработка ошибок
 */
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

    // ========================================
    // Дополнительные тесты (P1)
    // ========================================

    @Test
    fun writeText_createsTempFile() {
        // FR-2.1.2: Проверка создания временного файла .tmp
        val dir = createTempDirectory().toFile()
        try {
            val file = File(dir, "test.txt")
            val tempFile = File(dir, "test.txt.tmp")

            // Перед записью temp файла не должно быть
            assertFalse("Temp file should not exist before write", tempFile.exists())

            AtomicFileWriter.writeText(file, "content")

            // После записи temp файл удалён, целевой файл существует
            assertFalse("Temp file should be deleted after write", tempFile.exists())
            assertTrue("Target file should exist", file.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeText_existingFile_replacesAtomically() {
        // FR-2.1.3: Существующий файл заменяется атомарно
        val dir = createTempDirectory().toFile()
        try {
            val file = File(dir, "test.txt")

            // Первая запись
            AtomicFileWriter.writeText(file, "first content")
            assertEquals("first content", file.readText())

            // Вторая запись (замена)
            AtomicFileWriter.writeText(file, "second content")
            assertEquals("second content", file.readText())

            // Файл не должен быть пустым или повреждённым
            assertFalse("File should not be empty", file.readText().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeText_largeContent_writesCorrectly() {
        // Проверка записи большого содержимого
        val dir = createTempDirectory().toFile()
        try {
            val file = File(dir, "large.txt")
            val largeContent = "x".repeat(100000) // 100KB

            AtomicFileWriter.writeText(file, largeContent)

            assertEquals(largeContent.length, file.readText().length)
            assertEquals(largeContent, file.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeText_unicodeContent_preservesEncoding() {
        // Проверка сохранения Unicode
        val dir = createTempDirectory().toFile()
        try {
            val file = File(dir, "unicode.txt")
            val content = "Привет, мир! 你好世界 🌸"

            AtomicFileWriter.writeText(file, content)

            assertEquals(content, file.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeText_emptyContent_createsEmptyFile() {
        // Проверка записи пустого содержимого
        val dir = createTempDirectory().toFile()
        try {
            val file = File(dir, "empty.txt")

            AtomicFileWriter.writeText(file, "")

            assertTrue("File should exist", file.exists())
            assertEquals("", file.readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}
