package com.alexpo.grammermate.data

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset

object AtomicFileWriter {
    fun writeText(file: File, text: String, charset: Charset = Charsets.UTF_8) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val tempFile = File(parent, "${file.name}.tmp")
        // Удаляем temp file если он существует от предыдущей операции
        if (tempFile.exists()) {
            tempFile.delete()
            // Ждём, пока файл точно удалится (для Windows)
            var attempts = 0
            while (tempFile.exists() && attempts < 10) {
                Thread.sleep(10)
                tempFile.delete()
                attempts++
            }
        }
        FileOutputStream(tempFile).use { output ->
            output.write(text.toByteArray(charset))
            output.fd.sync()
        }
        // On Android/Linux renameTo() atomically replaces the destination,
        // so no explicit file.delete() is needed before the rename.
        if (!tempFile.renameTo(file)) {
            tempFile.delete()
            error("Failed to finalize ${file.absolutePath}")
        }
    }

    /**
     * Atomically copy [source] file to [target] using the temp -> fsync -> rename pattern.
     * Equivalent to `source.copyTo(target)` but crash-safe.
     */
    fun copyAtomic(source: File, target: File) {
        val parent = target.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val tempFile = File(parent, "${target.name}.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
            var attempts = 0
            while (tempFile.exists() && attempts < 10) {
                Thread.sleep(10)
                tempFile.delete()
                attempts++
            }
        }
        source.inputStream().use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        if (!tempFile.renameTo(target)) {
            tempFile.delete()
            error("Failed to finalize ${target.absolutePath}")
        }
    }
}
