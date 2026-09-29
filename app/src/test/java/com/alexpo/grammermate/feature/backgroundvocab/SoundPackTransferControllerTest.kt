package com.alexpo.grammermate.feature.backgroundvocab

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.alexpo.grammermate.data.DownloadState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SoundPackTransferControllerTest {
    private val resolver = ApplicationProvider.getApplicationContext<Application>().contentResolver
    private val uri = Uri.parse("content://sound-pack/test.zip")

    private class FakeSource : SoundPackTransferSource {
        val imports = mutableListOf<Triple<Uri, ContentResolver, String>>()
        val downloads = mutableListOf<String>()
        val counted = mutableListOf<String>()
        var importFlow: Flow<DownloadState> = flowOf(DownloadState.Done)
        var downloadFlow: (String) -> Flow<DownloadState> = { flowOf(DownloadState.Done) }

        override fun installedClipCount(packId: String): Int {
            counted += packId
            return if (packId == "pack-a") 3 else 7
        }

        override fun installFromUri(uri: Uri, resolver: ContentResolver, packId: String): Flow<DownloadState> {
            imports += Triple(uri, resolver, packId)
            return importFlow
        }

        override fun downloadAndInstall(packId: String): Flow<DownloadState> {
            downloads += packId
            return downloadFlow(packId)
        }
    }

    @Test
    fun `missing pack emits error without starting transfer or reading files`() = runTest {
        val source = FakeSource()
        val states = mutableListOf<DownloadState>()
        val counts = mutableListOf<Int>()
        val controller = SoundPackTransferController(source, this, states::add, counts::add, StandardTestDispatcher(testScheduler))

        controller.refreshInstalledCount(null)
        controller.import(uri, resolver, null)
        controller.download(null)
        runCurrent()

        assertEquals(2, states.size)
        assertTrue(states.all { it is DownloadState.Error })
        assertTrue(source.imports.isEmpty())
        assertTrue(source.downloads.isEmpty())
        assertTrue(source.counted.isEmpty())
        assertTrue(counts.isEmpty())
    }

    @Test
    fun `import and download forward progress and recount their captured pack`() = runTest {
        val source = FakeSource().apply {
            importFlow = flowOf(DownloadState.Extracting(50), DownloadState.Done)
            downloadFlow = { flowOf(DownloadState.Downloading(25, 1, 4), DownloadState.Done) }
        }
        val states = mutableListOf<DownloadState>()
        val counts = mutableListOf<Int>()
        val controller = SoundPackTransferController(source, this, states::add, counts::add, StandardTestDispatcher(testScheduler))

        controller.refreshInstalledCount("pack-a")
        controller.import(uri, resolver, "pack-a")
        runCurrent()
        controller.download("pack-b")
        runCurrent()

        assertEquals(listOf(3, 3, 7), counts)
        assertEquals(listOf("pack-a", "pack-a", "pack-b"), source.counted)
        assertEquals(listOf(Triple(uri, resolver, "pack-a")), source.imports)
        assertEquals(listOf("pack-b"), source.downloads)
        assertEquals(listOf(DownloadState.Extracting(50), DownloadState.Done, DownloadState.Downloading(25, 1, 4), DownloadState.Done), states)
    }

    @Test
    fun `cancel keeps transfer exclusive until blocked cleanup finishes`() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val source = FakeSource().apply {
            downloadFlow = { pack ->
                if (pack == "pack-a") flow {
                    emit(DownloadState.Downloading(10, 1, 10))
                    try { awaitCancellation() } finally {
                        withContext(NonCancellable) { cleanup.await() }
                    }
                } else flowOf(DownloadState.Done)
            }
        }
        val states = mutableListOf<DownloadState>()
        val controller = SoundPackTransferController(source, this, states::add, {}, StandardTestDispatcher(testScheduler))

        controller.download("pack-a")
        runCurrent()
        controller.cancel()
        controller.download("pack-b")
        runCurrent()
        assertEquals(listOf("pack-a"), source.downloads)
        assertEquals(DownloadState.Idle, states.last())

        cleanup.complete(Unit)
        runCurrent()
        controller.download("pack-b")
        runCurrent()
        assertEquals(listOf("pack-a", "pack-b"), source.downloads)
    }

    @Test
    fun `unsupported pack reports transfer error and allows a later retry`() = runTest {
        val source = FakeSource().apply {
            downloadFlow = { pack ->
                if (pack == "unsupported") throw IllegalArgumentException("No registered sound pack")
                flowOf(DownloadState.Done)
            }
        }
        val states = mutableListOf<DownloadState>()
        val controller = SoundPackTransferController(source, this, states::add, {}, StandardTestDispatcher(testScheduler))

        controller.download("unsupported")
        runCurrent()
        assertTrue(states.last() is DownloadState.Error)
        controller.download("pack-a")
        runCurrent()

        assertEquals(listOf("unsupported", "pack-a"), source.downloads)
        assertEquals(DownloadState.Done, states.last())
    }
}
