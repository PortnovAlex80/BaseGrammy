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

class AsrModelManager(private val context: Context) {

    private val spec: AsrModelSpec
        get() = AsrModelRegistry.defaultModel

    private val vadSpec: AsrModelSpec
        get() = AsrModelRegistry.vadModel

    fun asrModelDir(): File = File(context.filesDir, "asr/${spec.modelDirName}")
    fun vadModelDir(): File = File(context.filesDir, "asr/${vadSpec.modelDirName}")

    private fun archiveFile(): File = File(context.cacheDir, "${spec.modelDirName}.tar.bz2")

    fun isAsrModelReady(): Boolean {
        val dir = asrModelDir()
        if (!dir.exists()) return false
        return spec.requiredFiles.all { f ->
            val file = File(dir, f)
            file.exists() && file.length() > 0
        }
    }

    fun isVadModelReady(): Boolean {
        val dir = vadModelDir()
        if (!dir.exists()) return false
        return vadSpec.requiredFiles.all { f ->
            val file = File(dir, f)
            file.exists() && file.length() > 0
        }
    }

    fun isFullyReady(): Boolean = isAsrModelReady() && isVadModelReady()

    fun getDownloadedSize(): Long {
        val asrSize = asrModelDir().let { dir ->
            if (dir.exists()) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
        }
        val vadSize = vadModelDir().let { dir ->
            if (dir.exists()) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
        }
        return asrSize + vadSize
    }

    fun deleteModels() {
        asrModelDir().deleteRecursively()
        vadModelDir().deleteRecursively()
        archiveFile().delete()
    }

    fun getAvailableStorageBytes(): Long {
        val stat = StatFs(context.filesDir.absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    fun isNetworkMetered(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        return cm?.isActiveNetworkMetered ?: false
    }

    /**
     * Downloads the VAD model (small single file, no archive extraction needed).
     */
    private fun downloadVadModel(): Flow<DownloadState> = flow {
        val dir = vadModelDir()
        val targetFile = File(dir, "silero_vad.onnx")

        if (targetFile.exists() && targetFile.length() > 0) {
            emit(DownloadState.Done)
            return@flow
        }

        dir.mkdirs()
        try {
            val url = URL(vadSpec.downloadUrl)
            val connection = followRedirects(url)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                emit(DownloadState.Error("Server returned HTTP ${connection.responseCode}"))
                return@flow
            }

            connection.inputStream.buffered().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0
                    while (true) {
                        if (!currentCoroutineContext().isActive) {
                            connection.disconnect()
                            emit(DownloadState.Error("Download cancelled"))
                            return@flow
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                    }
                }
            }
            connection.disconnect()
            emit(DownloadState.Done)
        } catch (e: Exception) {
            Log.e(TAG, "VAD model download failed", e)
            targetFile.delete()
            emit(DownloadState.Error("VAD download failed: ${e.message}"))
        }
    }

    /**
     * Downloads both VAD and ASR models sequentially.
     */
    fun download(): Flow<DownloadState> = flow {
        val currentSpec = spec
        val availableBytes = getAvailableStorageBytes()
        if (availableBytes < currentSpec.minRequiredBytes) {
            val neededMb = currentSpec.minRequiredBytes / (1024 * 1024)
            val availableMb = availableBytes / (1024 * 1024)
            emit(DownloadState.Error("Insufficient storage. Need ${neededMb}MB, only ${availableMb}MB available."))
            return@flow
        }

        // Step 1: Download VAD model first (small)
        var vadFailed = false
        if (!isVadModelReady()) {
            downloadVadModel().collect { state ->
                when (state) {
                    is DownloadState.Done -> { /* continue to ASR download */ }
                    is DownloadState.Error -> {
                        emit(state)
                        vadFailed = true
                    }
                    else -> emit(DownloadState.Downloading(0, 0L, currentSpec.fallbackDownloadSize))
                }
            }
        }
        if (vadFailed) return@flow

        // Step 2: Download ASR model
        val dir = asrModelDir()
        val archive = archiveFile()

        archive.parentFile?.mkdirs()

        var lastException: Exception? = null
        for (attempt in 1..MAX_DOWNLOAD_ATTEMPTS) {
            if (attempt > 1) {
                Log.w(TAG, "Download attempt $attempt of $MAX_DOWNLOAD_ATTEMPTS after failure: ${lastException?.message}")
                emit(DownloadState.Downloading(0, 0L, currentSpec.fallbackDownloadSize))
                delay(RETRY_DELAY_MS)
            }

            try {
                val url = URL(currentSpec.downloadUrl)
                val connection = followRedirects(url)

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    connection.disconnect()
                    lastException = Exception("Server returned HTTP ${connection.responseCode}")
                    continue
                }

                var totalBytes = connection.contentLengthLong
                if (totalBytes <= 0L) {
                    totalBytes = currentSpec.fallbackDownloadSize
                    Log.w(TAG, "Content-Length unknown, using fallback estimate of ${currentSpec.fallbackDownloadSize / (1024 * 1024)}MB")
                }

                connection.inputStream.buffered().use { input ->
                    FileOutputStream(archive).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        var lastPercent = -1
                        while (true) {
                            if (!currentCoroutineContext().isActive) {
                                connection.disconnect()
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
                connection.disconnect()

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
            extractTarBz2(archive, dir, this@flow, currentSpec.archivePrefix)
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

    private fun followRedirects(startUrl: URL): HttpURLConnection {
        var connection = startUrl.openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = false

        var responseCode = connection.responseCode
        var redirectUrl: String? = connection.getHeaderField("Location")
        var redirectCount = 0
        while (responseCode in 301..399 && redirectUrl != null && redirectCount < 5) {
            connection.disconnect()
            val newConn = URL(redirectUrl).openConnection() as HttpURLConnection
            newConn.connectTimeout = CONNECT_TIMEOUT_MS
            newConn.readTimeout = READ_TIMEOUT_MS
            newConn.instanceFollowRedirects = false
            responseCode = newConn.responseCode
            redirectUrl = newConn.getHeaderField("Location")
            connection = newConn
            redirectCount++
        }
        return connection
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
        private const val TAG = "AsrModelManager"
        private const val CONNECT_TIMEOUT_MS = 60_000
        private const val READ_TIMEOUT_MS = 120_000
        private const val MAX_DOWNLOAD_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2_000L
    }
}
