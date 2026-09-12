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
    private fun verifyWrite(file: File, context: String = "file", allowEmpty: Boolean = false) {
        if (!file.exists()) {
            Log.e("AtomicFileWriter", "Write verification failed: $context not created at ${file.absolutePath}")
            throw IOException("Write failed: $context not created")
        }
        if (file.length() == 0L && !allowEmpty) {
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

        // Verify temp file was written successfully (allow empty if input is empty)
        verifyWrite(tempFile, "temp file ${tempFile.name}", allowEmpty = text.isEmpty())

        finalizeMove(tempFile, file)

        // Verify final file exists and is not empty (allow empty if input is empty)
        verifyWrite(file, file.name, allowEmpty = text.isEmpty())
    }

    /**
     * Atomically move [tempFile] onto [target], replacing it (TASK-091 item 5b).
     *
     * Layer 1 — [File.renameTo]: on Android/Linux it replaces an existing
     * destination in one atomic syscall, so the target is never absent.
     * Layer 2 — [java.nio.file.Files.move] with ATOMIC_MOVE (real JVMs and
     * Android API 26+): atomic replace on Windows via MoveFileEx, which
     * also never leaves the target missing.
     * Layer 3 (last resort) — delete the target first, legacy Windows
     * semantics with a retry/sleep loop. Only this branch can transiently
     * leave the target absent, so it is reached only when both
     * replace-capable APIs have already failed. It also keeps the up-to-100ms
     * sleep off the caller's hot path on every platform that matters.
     */
    private fun finalizeMove(tempFile: File, target: File) {
        // Layer 1 — File.renameTo: rename(2) semantics, atomically replaces
        // an existing destination on Android/Linux (the production target).
        // Returns false on Windows when the target exists.
        if (tempFile.renameTo(target)) return

        // Layer 2 — Files.move(ATOMIC_MOVE): MoveFileEx(MOVEFILE_REPLACE_EXISTING)
        // on Windows JVMs, rename(2) on POSIX; needs Android API 26+ (caught
        // below). Every attempt is an atomic replace — the target is never
        // absent. Transient AccessDeniedException (e.g. Defender briefly
        // holding the freshly written temp file) is retried, not escalated
        // to the destructive fallback.
        try {
            var attempts = 0
            while (true) {
                try {
                    java.nio.file.Files.move(
                        tempFile.toPath(),
                        target.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                    )
                    return
                } catch (ignore: java.nio.file.AtomicMoveNotSupportedException) {
                    throw ignore
                } catch (e: java.io.IOException) {
                    attempts++
                    if (attempts > 5) throw e
                    Thread.sleep(10)
                }
            }
        } catch (_: Throwable) {
            // API < 26 on Android, or a persistent I/O failure — take the
            // legacy path.
        }

        // Layer 3 (last resort) — delete the target first, legacy Windows
        // semantics with a retry/sleep loop. Only this branch can transiently
        // leave the target absent, so it is reached only when both
        // replace-capable layers above have failed.
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

        // Verify temp file was written successfully (allow empty if source is empty)
        verifyWrite(tempFile, "temp file ${tempFile.name}", allowEmpty = source.length() == 0L)

        finalizeMove(tempFile, target)

        // Verify final file exists and is not empty (allow empty if source is empty)
        verifyWrite(target, target.name, allowEmpty = source.length() == 0L)
    }

    /**
     * Atomically copy [source] to [target], but SKIP the copy entirely when [target]
     * already exists with the same byte length as [source].
     *
     * Pack reload (`forceReloadDefaultPacks` on every app start) re-runs the background-vocab
     * audio import on each launch. Copying tens of thousands of unchanged `.opus` clips through
     * the temp -> fsync -> rename path costs minutes of fsync on flash storage. Size equality
     * is a cheap, sufficient guard here: audio clips are write-once assets that never change
     * byte-length in place, so a matching length means the clip is already current. When the
     * guard does not hold (target missing or stale/different length), this delegates to the
     * full crash-safe [copyAtomic].
     */
    fun copyAtomicIfChanged(source: File, target: File) {
        if (target.exists() && target.length() == source.length()) {
            return
        }
        copyAtomic(source, target)
    }
}
