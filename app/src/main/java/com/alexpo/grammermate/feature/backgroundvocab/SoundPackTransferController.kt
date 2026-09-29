package com.alexpo.grammermate.feature.backgroundvocab

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.alexpo.grammermate.data.DownloadState
import com.alexpo.grammermate.data.SoundPackManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Transport boundary for sound-pack transfers; UI state remains in AudioCoordinator. */
internal interface SoundPackTransferSource {
    fun installedClipCount(packId: String): Int
    fun installFromUri(uri: Uri, resolver: ContentResolver, packId: String): Flow<DownloadState>
    fun downloadAndInstall(packId: String): Flow<DownloadState>
}

internal class SoundPackManagerSource(private val manager: SoundPackManager) : SoundPackTransferSource {
    override fun installedClipCount(packId: String) = manager.installedClipCount(packId)
    override fun installFromUri(uri: Uri, resolver: ContentResolver, packId: String) =
        manager.installFromUri(uri, resolver, packId)
    override fun downloadAndInstall(packId: String) = manager.downloadAndInstall(packId)
}

/** Owns the one active import/download job and publishes its progress to the audio state owner. */
internal class SoundPackTransferController(
    private val source: SoundPackTransferSource,
    private val scope: CoroutineScope,
    private val publishState: (DownloadState) -> Unit,
    private val publishCount: (Int) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var transferJob: Job? = null

    fun refreshInstalledCount(packId: String?) {
        if (packId != null) publishCount(source.installedClipCount(packId))
    }

    fun import(uri: Uri, resolver: ContentResolver, packId: String?) {
        start(packId, "import") { id -> source.installFromUri(uri, resolver, id) }
    }

    fun download(packId: String?) {
        start(packId, "download") { id -> source.downloadAndInstall(id) }
    }

    fun cancel() {
        // Keep the cancelled job until it finishes. ZIP extraction can be inside a
        // blocking file write; starting another transfer now would reuse its temp path.
        transferJob?.cancel()
        publishState(DownloadState.Idle)
    }

    private fun start(packId: String?, operation: String, transfer: (String) -> Flow<DownloadState>) {
        if (packId == null) {
            publishState(DownloadState.Error("Сначала выберите языковой пакет"))
            return
        }
        if (transferJob?.isCompleted == false) {
            Log.d("SoundPackTransfer", "Sound-pack $operation already in progress, ignoring request")
            return
        }
        transferJob = scope.launch(dispatcher) {
            try {
                transfer(packId).collect { state ->
                    publishState(state)
                    if (state is DownloadState.Done) {
                        publishCount(source.installedClipCount(packId))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("SoundPackTransfer", "Sound-pack $operation failed for $packId", error)
                publishState(DownloadState.Error(error.message ?: "Sound-pack transfer failed"))
            }
        }
    }
}
