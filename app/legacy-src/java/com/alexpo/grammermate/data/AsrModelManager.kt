package com.alexpo.grammermate.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AsrModelManager(private val context: Context) {

    fun isReady(): Boolean = AsrModelRegistry.isReady(context)

    fun isAsrReady(): Boolean = AsrModelRegistry.isAsrReady(context)

    fun isVadReady(): Boolean = AsrModelRegistry.isVadReady(context)

    fun isNetworkMetered(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        return cm?.isActiveNetworkMetered ?: false
    }

    private fun getAvailableStorageBytes(): Long {
        val stat = StatFs(context.filesDir.absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    /**
     * Search for a pre-placed VAD model file in known local directories.
     * Returns the first file found that is non-empty, or null.
     */
    private fun findLocalVadFile(): File? {
        val candidates = buildList {
            // Public Downloads/BaseGrammy/
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            add(File(publicDownloads, "BaseGrammy/${AsrModelRegistry.VAD_FILE_NAME}"))
            // App-specific external storage
            context.getExternalFilesDir(null)?.let { extDir ->
                add(File(extDir, "asr-models/${AsrModelRegistry.VAD_FILE_NAME}"))
            }
        }
        return candidates.firstOrNull { it.exists() && it.length() > 0 }
    }

    /**
     * Search for a pre-placed ASR archive file in known local directories.
     * Returns the first file found that exceeds the minimum size threshold, or null.
     */
    private fun findLocalAsrArchive(spec: AsrModelSpec): File? {
        val archiveName = "${spec.modelDirName}.tar.bz2"
        val minSize = 100L * 1024 * 1024 // 100 MB minimum reasonable size
        val candidates = buildList {
            // Public Downloads/BaseGrammy/
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            add(File(publicDownloads, "BaseGrammy/$archiveName"))
            // Also check with the full archive prefix name
            val prefixName = spec.archivePrefix.trimEnd('/').substringAfterLast('/')
            if (prefixName != spec.modelDirName) {
                add(File(publicDownloads, "BaseGrammy/$prefixName.tar.bz2"))
            }
            // App-specific external storage
            context.getExternalFilesDir(null)?.let { extDir ->
                add(File(extDir, "asr-models/$archiveName"))
                if (prefixName != spec.modelDirName) {
                    add(File(extDir, "asr-models/$prefixName.tar.bz2"))
                }
            }
        }
        return candidates.firstOrNull { it.exists() && it.length() > minSize }
    }

    fun downloadVad(): Flow<DownloadState> = flow {
        val dir = AsrModelRegistry.vadModelDir(context)
        val targetFile = File(dir, AsrModelRegistry.VAD_FILE_NAME)
        if (targetFile.exists() && targetFile.length() > 0) {
            emit(DownloadState.Done)
            return@flow
        }
        dir.mkdirs()

        // Try local file fallback first
        val localVad = findLocalVadFile()
        if (localVad != null) {
            Log.i(TAG, "Found local VAD model file: ${localVad.absolutePath} (${localVad.length()} bytes)")
            try {
                localVad.copyTo(targetFile, overwrite = true)
                if (targetFile.exists() && targetFile.length() > 0) {
                    Log.i(TAG, "VAD model copied from local file successfully")
                    emit(DownloadState.Done)
                    return@flow
                } else {
                    Log.w(TAG, "Local VAD copy produced empty file, falling back to download")
                    targetFile.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to copy local VAD file: ${e.message}, falling back to download")
                targetFile.delete()
            }
        }

        // Internet download fallback
        try {
            val url = URL(AsrModelRegistry.VAD_MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 60_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false

            var responseCode = connection.responseCode
            var redirectUrl: String? = connection.getHeaderField("Location")
            var currentConnection = connection
            var redirectCount = 0
            while (responseCode in 301..399 && redirectUrl != null && redirectCount < 5) {
                currentConnection.disconnect()
                val newConn = URL(redirectUrl).openConnection() as HttpURLConnection
                newConn.connectTimeout = 60_000
                newConn.readTimeout = 60_000
                newConn.instanceFollowRedirects = false
                responseCode = newConn.responseCode
                redirectUrl = newConn.getHeaderField("Location")
                currentConnection = newConn
                redirectCount++
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                currentConnection.disconnect()
                emit(DownloadState.Error("VAD download failed: HTTP $responseCode"))
                return@flow
            }

            currentConnection.inputStream.buffered().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        if (!currentCoroutineContext().isActive) {
                            currentConnection.disconnect()
                            emit(DownloadState.Error("Cancelled"))
                            return@flow
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            currentConnection.disconnect()
            emit(DownloadState.Done)
        } catch (e: Exception) {
            Log.e(TAG, "VAD download failed", e)
            targetFile.delete()
            emit(DownloadState.Error("VAD download failed: ${e.message}"))
        }
    }

    fun downloadAsr(): Flow<DownloadState> = flow {
        val spec = AsrModelRegistry.asrSpec
        val dir = AsrModelRegistry.asrModelDir(context)

        if (AsrModelRegistry.isAsrReady(context)) {
            emit(DownloadState.Done)
            return@flow
        }

        val availableBytes = getAvailableStorageBytes()
        if (availableBytes < spec.minRequiredBytes) {
            val neededMb = spec.minRequiredBytes / (1024 * 1024)
            val availableMb = availableBytes / (1024 * 1024)
            emit(DownloadState.Error("Need ${neededMb}MB, only ${availableMb}MB free"))
            return@flow
        }

        // Try local file fallback first
        val localArchive = findLocalAsrArchive(spec)
        if (localArchive != null) {
            Log.i(TAG, "Found local ASR archive: ${localArchive.absolutePath} (${localArchive.length()} bytes)")
            dir.mkdirs()
            try {
                emit(DownloadState.Extracting(0))
                extractTarBz2(localArchive, dir, spec.archivePrefix) { percent ->
                    emit(DownloadState.Extracting(percent))
                }
                emit(DownloadState.Extracting(100))
                if (isAsrReady()) {
                    Log.i(TAG, "ASR model extracted from local archive successfully")
                    emit(DownloadState.Done)
                    return@flow
                } else {
                    Log.w(TAG, "Local archive extraction did not produce required files, falling back to download")
                    dir.deleteRecursively()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract local ASR archive: ${e.message}, falling back to download")
                dir.deleteRecursively()
            }
        }

        // Internet download fallback
        val archive = File(context.cacheDir, "${spec.modelDirName}.tar.bz2")
        archive.parentFile?.mkdirs()

        // Download
        var lastException: Exception? = null
        for (attempt in 1..3) {
            if (attempt > 1) {
                emit(DownloadState.Downloading(0, 0L, spec.fallbackDownloadSize))
                delay(2000)
            }
            try {
                val url = URL(spec.downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 60_000
                connection.readTimeout = 120_000
                connection.instanceFollowRedirects = false

                var responseCode = connection.responseCode
                var redirectUrl: String? = connection.getHeaderField("Location")
                var currentConnection = connection
                var redirectCount = 0
                while (responseCode in 301..399 && redirectUrl != null && redirectCount < 5) {
                    currentConnection.disconnect()
                    val newConn = URL(redirectUrl).openConnection() as HttpURLConnection
                    newConn.connectTimeout = 60_000
                    newConn.readTimeout = 120_000
                    newConn.instanceFollowRedirects = false
                    responseCode = newConn.responseCode
                    redirectUrl = newConn.getHeaderField("Location")
                    currentConnection = newConn
                    redirectCount++
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    currentConnection.disconnect()
                    lastException = Exception("HTTP $responseCode")
                    continue
                }

                var totalBytes = currentConnection.contentLengthLong
                if (totalBytes <= 0L) totalBytes = spec.fallbackDownloadSize

                currentConnection.inputStream.buffered().use { input ->
                    FileOutputStream(archive).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        var lastPercent = -1
                        while (true) {
                            if (!currentCoroutineContext().isActive) {
                                currentConnection.disconnect()
                                emit(DownloadState.Error("Cancelled"))
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
                Log.e(TAG, "ASR download attempt $attempt failed", e)
                lastException = e
                archive.delete()
            }
        }

        if (lastException != null) {
            emit(DownloadState.Error("Download failed: ${lastException.message}"))
            return@flow
        }

        // Extract
        dir.mkdirs()
        try {
            emit(DownloadState.Extracting(0))
            extractTarBz2(archive, dir, spec.archivePrefix) { percent ->
                emit(DownloadState.Extracting(percent))
            }
            emit(DownloadState.Extracting(100))
        } catch (e: Exception) {
            Log.e(TAG, "ASR extraction failed", e)
            dir.deleteRecursively()
            emit(DownloadState.Error("Extraction failed: ${e.message}"))
            return@flow
        }

        archive.delete()
        emit(DownloadState.Done)
    }

    private suspend fun extractTarBz2(
        archive: File,
        destDir: File,
        archivePrefix: String,
        onProgress: suspend (Int) -> Unit
    ) {
        val totalSize = archive.length()
        var bytesRead: Long = 0
        var lastPercent = 0

        FileInputStream(archive).use { fis ->
            BufferedInputStream(fis).use { bis ->
                BZip2CompressorInputStream(bis).use { bzIn ->
                    TarArchiveInputStream(bzIn).use { tarIn ->
                        var entry = tarIn.nextTarEntry
                        while (entry != null) {
                            val entryName = entry.name
                            val relativePath = entryName.removePrefix(archivePrefix)
                            if (relativePath.isEmpty()) {
                                entry = tarIn.nextTarEntry
                                continue
                            }
                            val outputFile = File(destDir, relativePath)
                            val canonicalDest = outputFile.canonicalPath
                            if (!canonicalDest.startsWith(destDir.canonicalPath + File.separator)) {
                                throw IOException("Path traversal attempt in tar entry: ${entry.name}")
                            }
                            if (entry.isDirectory) {
                                outputFile.mkdirs()
                            } else {
                                outputFile.parentFile?.mkdirs()
                                FileOutputStream(outputFile).use { out ->
                                    val buffer = ByteArray(8192)
                                    var len: Int
                                    while (tarIn.read(buffer).also { len = it } != -1) {
                                        out.write(buffer, 0, len)
                                        bytesRead += len
                                    }
                                }
                            }
                            val percent = if (totalSize > 0) {
                                ((bytesRead * 100) / totalSize).toInt().coerceAtMost(99)
                            } else 50
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                            entry = tarIn.nextTarEntry
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "AsrModelManager"
    }
}
