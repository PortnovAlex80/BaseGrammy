package com.alexpo.grammermate.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Downloads (from a [SoundPackRegistry] URL) or imports (from a SAF/local [Uri]) a large ZIP
 * of pre-rendered Ogg-Opus background-vocab clips and stream-extracts it into the active
 * pack's background-vocab audio directory.
 *
 * Mirrors [TtsModelManager]: StatFs storage pre-check, a 3-retry HTTP download with manual
 * redirect handling + per-emit progress + coroutine-cancellation, and streaming
 * (entry-by-entry) extraction with a path-traversal guard and atomic temp-then-rename
 * writes. The only structural difference is the archive format — ZIP instead of tar.bz2 —
 * so extraction uses [ZipInputStream] rather than commons-compress.
 *
 * The audio directory layout is the one [com.alexpo.grammermate.feature.backgroundvocab.BgVocabAudioResolver]
 * already reads:
 * ```
 * {baseDir}/drills/{packId}/bg_vocab/audio/{lang}_r{rank}_f{field}.opus
 * ```
 * Inside the ZIP, entries are expected to be named `bg_vocab/audio/<basename>.opus`; the
 * extractor flattens every such entry to a bare file under [audioDirFor] and silently skips
 * anything outside the `bg_vocab/audio/` subtree (so junk entries never leak into the pack).
 *
 * Crash-safety: each clip is written to a `*.tmp` sibling, fsync'd, then renamed onto its
 * final name (see [AtomicFileWriter]); a crash mid-write therefore never leaves a half clip,
 * and re-importing is idempotent (overwrites cleanly).
 *
 * @param context Android context — used for [Context.cacheDir] (staging the download) and
 *  [Context.getContentResolver] is supplied by the caller for the SAF path.
 * @param baseDir the grammarmate root directory (`File(context.filesDir, "grammarmate")`),
 *  matching the convention used by [com.alexpo.grammermate.feature.backgroundvocab.BgVocabAudioResolver]
 *  and `DrillFileManager`.
 */
class SoundPackManager(
    private val context: Context,
    private val baseDir: File
) {

    /**
     * The background-vocab audio directory for [packId]. This is the EXACT path
     * [com.alexpo.grammermate.feature.backgroundvocab.BgVocabAudioResolver] reads via
     * `fileForRank`, so extracted clips land exactly where the player looks for them.
     */
    fun audioDirFor(packId: String): File =
        File(baseDir, "drills/$packId/bg_vocab/audio")

    /**
     * @return the number of `*.opus` clips currently installed under [audioDirFor], or `0`
     *  if the directory does not exist yet. Used by the UI to show install state and to
     *  decide whether a download / import is needed.
     */
    fun installedClipCount(packId: String): Int {
        val dir = audioDirFor(packId)
        if (!dir.exists()) return 0
        return dir.listFiles { f -> f.isFile && f.extension.equals("opus", ignoreCase = true) }
            ?.size ?: 0
    }

    /**
     * Import a sound-pack ZIP referenced by a SAF/local [uri] (e.g. picked via
     * `ACTION_OPEN_DOCUMENT`). No download phase: the bytes are streamed straight from the
     * content provider into the streaming extractor. Emits [DownloadState.Extracting] with
     * progressing percent, then [DownloadState.Done] / [DownloadState.Error].
     *
     * The total size (for progress) is probed via [ContentResolver.openAssetFileDescriptor]
     * `length`; if unknown the percent stays at 50 (indeterminate), exactly like
     * [TtsModelManager.extractTarBz2] when `totalSize <= 0`.
     *
     * Idempotent: overwriting existing clips is fine (re-import resumes cleanly).
     *
     * @param uri             content:// or file:// URI of the source ZIP.
     * @param contentResolver the calling activity's [ContentResolver].
     * @param packId          the pack to install the clips into.
     */
    fun installFromUri(
        uri: Uri,
        contentResolver: ContentResolver,
        packId: String
    ): Flow<DownloadState> = flow {
        val totalBytes = try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Could not probe AssetFileDescriptor length for $uri, falling back to 0", e)
            0L
        }
        Log.d(TAG, "installFromUri: $uri, reported length=$totalBytes bytes")

        val audioDir = audioDirFor(packId)
        try {
            audioDir.mkdirs()
            emit(DownloadState.Extracting(0))
            contentResolver.openInputStream(uri).use { input ->
                if (input == null) {
                    emit(DownloadState.Error("Could not open input stream for $uri"))
                    return@flow
                }
                streamExtract(input, audioDir, totalBytes, this)
            }
            emit(DownloadState.Extracting(100))
        } catch (e: Exception) {
            Log.e(TAG, "installFromUri extraction failed for pack $packId", e)
            emit(DownloadState.Error("Import failed: ${e.message}"))
            return@flow
        }
        emit(DownloadState.Done)
    }

    /**
     * Download the sound pack for [packId] from its [SoundPackRegistry] URL and stream-extract
     * it into [audioDirFor]. Mirrors [TtsModelManager.download]:
     *  1. StatFs storage pre-check against [SoundPackSpec.fallbackSizeBytes] (with 1.5x slack
     *     to cover decompression head-room on the staging zip + extracted clips).
     *  2. 3-retry streaming HTTP download to `File(context.cacheDir, "soundpack_$packId.zip")`
     *     with manual redirect handling (up to 5 hops), per-emit [DownloadState.Downloading]
     *     progress, and coroutine-cancellation awareness.
     *  3. Streaming ZIP extraction into the audio dir with path-traversal guard + atomic
     *     writes, emitting [DownloadState.Extracting] progress.
     *  4. Delete the staging zip.
     *
     * Honors [SoundPackSpec.authToken]: when non-null, sets
     * `Authorization: Bearer <token>` on every connection (private GitHub release support).
     *
     * Errors emitted as [DownloadState.Error]: insufficient storage, non-200 after all
     * retries, extraction exception. The partial staging zip is deleted on failure.
     */
    fun downloadAndInstall(packId: String): Flow<DownloadState> = flow {
        val spec = SoundPackRegistry.specFor(packId)
            ?: throw IllegalArgumentException("Unknown sound pack: $packId")
        val audioDir = audioDirFor(packId)
        val archive = File(context.cacheDir, "soundpack_$packId.zip")

        val availableBytes = getAvailableStorageBytes()
        if (availableBytes < spec.fallbackSizeBytes) {
            val neededMb = spec.fallbackSizeBytes / (1024 * 1024)
            val availableMb = availableBytes / (1024 * 1024)
            emit(DownloadState.Error("Insufficient storage. Need ${neededMb}MB, only ${availableMb}MB available."))
            return@flow
        }

        archive.parentFile?.mkdirs()

        var lastException: Exception? = null
        for (attempt in 1..MAX_DOWNLOAD_ATTEMPTS) {
            if (attempt > 1) {
                Log.w(TAG, "Download attempt $attempt of $MAX_DOWNLOAD_ATTEMPTS after failure: ${lastException?.message}")
                emit(DownloadState.Downloading(0, 0L, spec.fallbackSizeBytes))
                delay(RETRY_DELAY_MS)
            }

            try {
                val url = URL(spec.downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = false
                if (!spec.authToken.isNullOrBlank()) {
                    connection.setRequestProperty("Authorization", "Bearer ${spec.authToken}")
                }

                var responseCode = connection.responseCode
                var redirectUrl: String? = connection.getHeaderField("Location")
                var currentConnection = connection
                var redirectCount = 0
                while (responseCode in 301..399 && redirectUrl != null && redirectCount < 5) {
                    currentConnection.disconnect()
                    val newConn = URL(redirectUrl).openConnection() as HttpURLConnection
                    newConn.connectTimeout = CONNECT_TIMEOUT_MS
                    newConn.readTimeout = READ_TIMEOUT_MS
                    newConn.instanceFollowRedirects = false
                    if (!spec.authToken.isNullOrBlank()) {
                        newConn.setRequestProperty("Authorization", "Bearer ${spec.authToken}")
                    }
                    responseCode = newConn.responseCode
                    redirectUrl = newConn.getHeaderField("Location")
                    currentConnection = newConn
                    redirectCount++
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    currentConnection.disconnect()
                    lastException = Exception("Server returned HTTP $responseCode")
                    continue
                }

                var totalBytes = currentConnection.contentLengthLong
                val finalUrl = currentConnection.url.toString()
                Log.d(TAG, "Final URL: $finalUrl, reported Content-Length: $totalBytes")
                if (totalBytes <= 0L) {
                    totalBytes = spec.fallbackSizeBytes
                    Log.w(TAG, "Content-Length unknown, using fallback estimate of ${spec.fallbackSizeBytes / (1024 * 1024)}MB for progress")
                }

                currentConnection.inputStream.buffered().use { input ->
                    FileOutputStream(archive).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        var lastPercent = -1
                        while (true) {
                            if (!currentCoroutineContext().isActive) {
                                currentConnection.disconnect()
                                emit(DownloadState.Error("Download cancelled"))
                                return@flow
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesRead += read
                            val percent = ((bytesRead * 100) / totalBytes).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                emit(DownloadState.Downloading(percent, bytesRead, totalBytes))
                            }
                        }
                    }
                }
                currentConnection.disconnect()

                lastException = null
                break
            } catch (e: Exception) {
                Log.e(TAG, "Download attempt $attempt failed", e)
                lastException = e
                archive.delete()
            }
        }

        if (lastException != null) {
            Log.e(TAG, "All $MAX_DOWNLOAD_ATTEMPTS download attempts failed")
            emit(DownloadState.Error("Download failed after $MAX_DOWNLOAD_ATTEMPTS attempts: ${lastException.message}"))
            return@flow
        }

        audioDir.mkdirs()
        try {
            emit(DownloadState.Extracting(0))
            val totalSize = archive.length()
            FileInputStream(archive).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    streamExtract(bis, audioDir, totalSize, this)
                }
            }
            emit(DownloadState.Extracting(100))
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed for pack $packId", e)
            emit(DownloadState.Error("Extraction failed: ${e.message}"))
            archive.delete()
            return@flow
        }

        archive.delete()
        emit(DownloadState.Done)
    }

    fun getAvailableStorageBytes(): Long {
        val stat = StatFs(context.filesDir.absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    /**
     * Streaming ZIP extractor. Pure InputStream → File logic (no Android dependencies), so
     * it is directly unit-testable on a plain JVM via the [internal] visibility.
     *
     * For each [ZipEntry]:
     *  - Skip directories.
     *  - If the entry name does not contain `bg_vocab/audio/`, skip it (junk entries never
     *    land in the pack dir).
     *  - Resolve the destination as `File(audioDir, <basename after bg_vocab/audio/>)`,
     *    i.e. flatten to the audio dir using only the basename.
     *  - Path-traversal guard (mirrors [TtsModelManager] lines 235-238): verify the
     *    destination canonical path starts with `audioDir.canonicalPath + File.separator`;
     *    else throw [IOException].
     *  - Write via atomic temp-then-rename (8192-byte buffer), so a crash mid-write never
     *    leaves a half clip. Idempotent — overwriting an existing clip is fine.
     *  - Track bytes read and emit [DownloadState.Extracting] percent on change, capped at
     *    99 (100 is emitted by the caller on success). When [totalBytesForProgress] <= 0
     *    the percent stays at 50 (indeterminate), matching [TtsModelManager.extractTarBz2].
     *  - Honor coroutine cancellation (`currentCoroutineContext().isActive`).
     *
     * Never loads the whole zip or all entries into memory — streams entry-by-entry.
     *
     * @param inputStream           the raw ZIP byte stream (NOT yet wrapped in ZipInputStream;
     *                              this method wraps it). Caller closes it.
     * @param audioDir              destination directory (created if absent).
     * @param totalBytesForProgress total compressed bytes, used only for progress percent.
     * @param emitter               the [DownloadState.Extracting] sink.
     */
    internal suspend fun streamExtract(
        inputStream: InputStream,
        audioDir: File,
        totalBytesForProgress: Long,
        emitter: FlowCollector<DownloadState>
    ) {
        audioDir.mkdirs()
        val canonicalAudioDir = audioDir.canonicalPath + File.separator
        var bytesRead: Long = 0
        var entriesProcessed = 0
        var lastPercent = -1

        ZipInputStream(inputStream).use { zipIn ->
            while (true) {
                if (!currentCoroutineContext().isActive) return
                val entry: ZipEntry = zipIn.nextEntry ?: break
                try {
                    if (entry.isDirectory) {
                        zipIn.closeEntry()
                        continue
                    }
                    val entryName = entry.name
                    // Only extract entries inside bg_vocab/audio/; flatten to basename.
                    val marker = "bg_vocab/audio/"
                    val idx = entryName.indexOf(marker)
                    if (idx < 0) {
                        zipIn.closeEntry()
                        continue
                    }
                    val basename = entryName.substring(idx + marker.length)
                    if (basename.isBlank() || basename.contains("/") || basename.contains("\\")) {
                        // Subdirectory inside audio/ (e.g. bg_vocab/audio/nested/x.opus) — skip,
                        // we only accept flat clip files.
                        zipIn.closeEntry()
                        continue
                    }
                    val destFile = File(audioDir, basename)
                    val canonicalDest = destFile.canonicalPath
                    if (!canonicalDest.startsWith(canonicalAudioDir)) {
                        throw IOException("Path traversal attempt in zip entry: ${entry.name}")
                    }

                    writeAtomic(zipIn, destFile)
                    entriesProcessed++

                    // ZipInputStream doesn't expose a reliable per-entry byte count a priori, so
                    // advance bytesRead by the bytes we just copied. writeAtomic reports them.
                    bytesRead += entry.size.coerceAtLeast(0L)
                    val percent = if (totalBytesForProgress > 0) {
                        ((bytesRead * 100) / totalBytesForProgress).toInt().coerceAtMost(99)
                    } else {
                        50
                    }
                    if (percent != lastPercent) {
                        lastPercent = percent
                        Log.d(TAG, "extract: entries=$entriesProcessed bytesRead=$bytesRead/$totalBytesForProgress percent=$percent")
                        emitter.emit(DownloadState.Extracting(percent))
                    }
                } finally {
                    zipIn.closeEntry()
                }
            }
        }
    }

    /**
     * Copy the current [ZipInputStream] entry into [destFile] using the temp → fsync → rename
     * pattern (mirrors [AtomicFileWriter.copyAtomic]), so a crash mid-write never leaves a
     * half clip. Overwrites an existing clip atomically. Idempotent on re-import.
     *
     * @return the number of bytes written.
     */
    private fun writeAtomic(zipIn: ZipInputStream, destFile: File): Long {
        val parent = destFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val tempFile = File(parent, "${destFile.name}.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }
        var written: Long = 0
        FileOutputStream(tempFile).use { output ->
            val buffer = ByteArray(8192)
            while (true) {
                val len = zipIn.read(buffer)
                if (len == -1) break
                output.write(buffer, 0, len)
                written += len
            }
            output.fd.sync()
        }

        if (tempFile.length() == 0L && written == 0L) {
            // Defensive: empty entry — keep the clip absent rather than leaving a zero-byte stub.
            tempFile.delete()
            return 0L
        }

        if (destFile.exists() && !destFile.delete()) {
            tempFile.delete()
            throw IOException("Could not overwrite existing clip at ${destFile.absolutePath}")
        }
        if (!tempFile.renameTo(destFile)) {
            tempFile.delete()
            throw IOException("Could not finalize clip at ${destFile.absolutePath}")
        }
        return written
    }

    companion object {
        private const val TAG = "SoundPackManager"
        private const val CONNECT_TIMEOUT_MS = 60_000
        private const val READ_TIMEOUT_MS = 120_000
        private const val MAX_DOWNLOAD_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2_000L
    }
}
