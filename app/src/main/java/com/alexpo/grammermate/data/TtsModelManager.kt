package com.alexpo.grammermate.data

import android.content.Context
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
import java.net.HttpURLConnection
import java.net.URL

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val percent: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Extracting(val percent: Int) : DownloadState()
    object Done : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class TtsModelManager(private val context: Context) {

    var currentLanguageId: String = "en"

    fun modelDir(languageId: String = currentLanguageId): File {
        val spec = TtsModelRegistry.specFor(languageId) ?: error("Unknown language: $languageId")
        return File(context.filesDir, "tts/${spec.modelDirName}")
    }

    private fun archiveFile(languageId: String): File {
        val spec = TtsModelRegistry.specFor(languageId) ?: error("Unknown language: $languageId")
        return File(context.cacheDir, "${spec.modelDirName}.tar.bz2")
    }

    fun isModelReady(languageId: String = currentLanguageId): Boolean {
        val spec = TtsModelRegistry.specFor(languageId) ?: return false
        val dir = modelDir(languageId)
        if (!dir.exists()) return false
        val filesOk = spec.requiredFiles.all { f ->
            val file = File(dir, f)
            file.exists() && file.length() > 0
        }
        val dirsOk = spec.requiredDirs.all { d ->
            val file = File(dir, d)
            file.exists() && file.isDirectory
        }
        return filesOk && dirsOk
    }

    fun getDownloadedSize(languageId: String = currentLanguageId): Long {
        val dir = modelDir(languageId)
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun deleteModel(languageId: String = currentLanguageId) {
        modelDir(languageId).deleteRecursively()
        archiveFile(languageId).delete()
    }

    fun getAvailableStorageBytes(): Long {
        val stat = StatFs(context.filesDir.absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    fun isNetworkMetered(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        return cm?.isActiveNetworkMetered ?: false
    }

    fun download(languageId: String = currentLanguageId): Flow<DownloadState> = flow {
        if (isModelReady(languageId)) {
            emit(DownloadState.Done)
            return@flow
        }
        val spec = TtsModelRegistry.specFor(languageId)
            ?: throw IllegalArgumentException("Unknown language: $languageId")
        val dir = modelDir(languageId)
        val archive = archiveFile(languageId)

        val availableBytes = getAvailableStorageBytes()
        if (availableBytes < spec.minRequiredBytes) {
            val neededMb = spec.minRequiredBytes / (1024 * 1024)
            val availableMb = availableBytes / (1024 * 1024)
            emit(DownloadState.Error("Insufficient storage. Need ${neededMb}MB, only ${availableMb}MB available."))
            return@flow
        }

        archive.parentFile?.mkdirs()

        var lastException: Exception? = null
        for (attempt in 1..MAX_DOWNLOAD_ATTEMPTS) {
            if (attempt > 1) {
                Log.w(TAG, "Download attempt $attempt of $MAX_DOWNLOAD_ATTEMPTS after failure: ${lastException?.message}")
                emit(DownloadState.Downloading(0, 0L, spec.fallbackDownloadSize))
                delay(RETRY_DELAY_MS)
            }

            try {
                val url = URL(spec.downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = false

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
                    totalBytes = spec.fallbackDownloadSize
                    Log.w(TAG, "Content-Length unknown, using fallback estimate of ${spec.fallbackDownloadSize / (1024 * 1024)}MB for progress")
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

        dir.mkdirs()
        try {
            emit(DownloadState.Extracting(0))
            extractTarBz2(archive, dir, this@flow, spec.archivePrefix)
            emit(DownloadState.Extracting(100))
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed", e)
            dir.deleteRecursively()
            emit(DownloadState.Error("Extraction failed: ${e.message}"))
            return@flow
        }

        archive.delete()
        emit(DownloadState.Done)
    }

    fun downloadMultiple(
        languageIds: List<String>
    ): Flow<Map<String, DownloadState>> = flow {
        val states: MutableMap<String, DownloadState> = languageIds.associateWith { DownloadState.Idle }.toMutableMap()
        emit(states.toMap())

        for (langId in languageIds) {
            if (isModelReady(langId)) {
                states[langId] = DownloadState.Done
                emit(states.toMap())
                continue
            }
            download(langId).collect { state ->
                states[langId] = state
                emit(states.toMap())
            }
        }
    }

    private suspend fun extractTarBz2(
        archive: File,
        destDir: File,
        emitter: kotlinx.coroutines.flow.FlowCollector<DownloadState>,
        archivePrefix: String
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
                            if (!currentCoroutineContext().isActive) return
                            val entryName = entry.name
                            val relativePath = entryName.removePrefix(archivePrefix)
                            if (relativePath.isEmpty()) {
                                entry = tarIn.nextTarEntry
                                continue
                            }
                            val outputFile = File(destDir, relativePath)
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
                                emitter.emit(DownloadState.Extracting(percent))
                            }
                            entry = tarIn.nextTarEntry
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "TtsModelManager"
        private const val CONNECT_TIMEOUT_MS = 60_000
        private const val READ_TIMEOUT_MS = 120_000
        private const val MAX_DOWNLOAD_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2_000L
    }
}
