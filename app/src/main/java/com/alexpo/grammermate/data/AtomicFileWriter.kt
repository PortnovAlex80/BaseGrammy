package com.alexpo.grammermate.data

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset

object AtomicFileWriter {

    /**
     * Verify that a file was written successfully.
     * Checks that the file exists and is not empty.
     * Throws IOException if verification fails.
     */
    private fun verifyWrite(file: File, context: String = "file") {
        if (!file.exists()) {
            Log.e("AtomicFileWriter", "Write verification failed: $context not created at ${file.absolutePath}")
            throw IOException("Write failed: $context not created")
        }
        if (file.length() == 0L) {
            Log.e("AtomicFileWriter", "Write verification failed: $context is empty at ${file.absolutePath}")
            file.delete()
            throw IOException("Write failed: $context is empty")
        }
        Log.d("AtomicFileWriter", "Write verification passed: $context (${file.length()} bytes)")
    }

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

        // Verify temp file was written successfully
        verifyWrite(tempFile, "temp file ${tempFile.name}")

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

        // Verify final file exists and is not empty
        verifyWrite(file, file.name)
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

        // Verify temp file was written successfully
        verifyWrite(tempFile, "temp file ${tempFile.name}")

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

        // Verify final file exists and is not empty
        verifyWrite(target, target.name)
    }
}
