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
        // On Android/Linux renameTo() atomically replaces the destination.
        // On Windows, we need to delete the target first if it exists.
        if (file.exists()) {
            if (!file.delete()) {
                // If delete fails, wait a bit and retry (Windows file locking)
                var attempts = 0
                while (file.exists() && attempts < 10) {
                    Thread.sleep(10)
                    file.delete()
                    attempts++
                }
            }
        }
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
        // On Windows, we need to delete the target first if it exists.
        if (target.exists()) {
            if (!target.delete()) {
                // If delete fails, wait a bit and retry (Windows file locking)
                var attempts = 0
                while (target.exists() && attempts < 10) {
                    Thread.sleep(10)
                    target.delete()
                    attempts++
                }
            }
        }
        if (!tempFile.renameTo(target)) {
            tempFile.delete()
            error("Failed to finalize ${target.absolutePath}")
        }
    }
}
