package com.alexpo.grammermate.shared.audio

import com.alexpo.grammermate.data.AudioState
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TtsModelDownloadControllerTest {
    private class FakeSource : TtsModelSource {
        var metered = false
        val ready = mutableSetOf<String>()
        val singles = mutableListOf<String>()
        val batches = mutableListOf<List<String>>()
        var singleFlow: (String) -> Flow<DownloadState> = { flowOf(DownloadState.Done) }
        var batchFlow: (List<String>) -> Flow<Map<String, DownloadState>> = { ids ->
            flowOf(ids.associateWith { DownloadState.Done })
        }

        override fun isReady(languageId: String) = languageId in ready
        override fun isMetered() = metered
        override fun download(languageId: String): Flow<DownloadState> {
            singles += languageId
            return singleFlow(languageId)
        }
        override fun downloadMultiple(languageIds: List<String>): Flow<Map<String, DownloadState>> {
            batches += languageIds
            return batchFlow(languageIds)
        }
    }

    private class Harness(source: FakeSource, scope: kotlinx.coroutines.CoroutineScope,
                          dispatcher: kotlinx.coroutines.CoroutineDispatcher) {
        var state = AudioState()
        var selected = "it"
        var languages = listOf("it", "ru")
        val controller = TtsModelDownloadController(
            source, scope, { state }, { update -> state = update(state) },
            { selected }, { languages }, dispatcher
        )
    }

    @Test
    fun metered_confirmation_replays_the_full_original_settings_batch() = runTest {
        val source = FakeSource().apply {
            metered = true
            batchFlow = { ids -> flow {
                ready.addAll(ids)
                emit(ids.associateWith { DownloadState.Done })
            } }
        }
        val harness = Harness(source, this, StandardTestDispatcher(testScheduler))

        harness.controller.downloadModels(listOf("it", "ru"))
        assertTrue(harness.state.ttsMeteredNetwork)
        assertTrue(source.batches.isEmpty())
        harness.selected = "de"
        harness.controller.confirmMetered()
        runCurrent()

        assertEquals(listOf(listOf("it", "ru")), source.batches)
        assertTrue(source.singles.isEmpty())
        assertEquals(DownloadState.Done, harness.state.ttsDownloadState)
        assertEquals(mapOf("it" to true, "ru" to true), harness.state.ttsModelsReady)
    }

    @Test
    fun foreground_waits_for_cancelled_background_writer_to_finish_cleanup() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val source = FakeSource().apply {
            batchFlow = { ids -> flow {
                emit(ids.associateWith { DownloadState.Downloading(10, 1, 10) })
                try { awaitCancellation() }
                finally { withContext(NonCancellable) { cleanup.await() } }
            } }
        }
        val harness = Harness(source, this, StandardTestDispatcher(testScheduler))

        harness.controller.startBackground()
        runCurrent()
        assertTrue(harness.state.bgTtsDownloading)

        harness.controller.downloadForLanguage("ru")
        runCurrent()
        assertTrue(source.singles.isEmpty())
        cleanup.complete(Unit)
        runCurrent()

        assertEquals(listOf("ru"), source.singles)
        assertFalse(harness.state.bgTtsDownloading)
    }

    @Test
    fun startup_background_and_single_requests_never_start_two_writers() = runTest {
        val source = FakeSource()
        val harness = Harness(source, this, StandardTestDispatcher(testScheduler))

        harness.controller.startBackground()
        harness.controller.downloadForLanguage("ru")
        runCurrent()

        assertTrue(source.batches.isEmpty())
        assertEquals(listOf("ru"), source.singles)
    }

    @Test
    fun background_completion_marks_the_currently_selected_language_ready() = runTest {
        val source = FakeSource().apply {
            batchFlow = { ids -> flow {
                emit(ids.associateWith { DownloadState.Idle })
                emit(mapOf("it" to DownloadState.Done, "ru" to DownloadState.Downloading(25, 1, 4)))
                emit(ids.associateWith { DownloadState.Done })
            } }
        }
        val harness = Harness(source, this, StandardTestDispatcher(testScheduler))
        harness.selected = "ru"

        harness.controller.startBackground()
        runCurrent()

        assertEquals(mapOf("it" to true, "ru" to true), harness.state.ttsModelsReady)
        assertTrue(harness.state.ttsModelReady)
        assertFalse(harness.state.bgTtsDownloading)
    }

    @Test
    fun background_completion_uses_selection_at_completion_time() = runTest {
        val continueDownload = CompletableDeferred<Unit>()
        val source = FakeSource().apply {
            batchFlow = { ids -> flow {
                emit(mapOf("it" to DownloadState.Downloading(25, 1, 4), "ru" to DownloadState.Idle))
                continueDownload.await()
                emit(ids.associateWith { DownloadState.Done })
            } }
        }
        val harness = Harness(source, this, StandardTestDispatcher(testScheduler))
        harness.controller.startBackground()
        runCurrent()

        harness.selected = "ru"
        continueDownload.complete(Unit)
        runCurrent()

        assertTrue(harness.state.ttsModelReady)
        assertEquals(DownloadState.Done, harness.state.ttsDownloadState)
    }

    @Test
    fun all_ready_batch_skips_metered_prompt_and_network() = runTest {
        val source = FakeSource().apply { metered = true; ready += listOf("it", "ru") }
        val harness = Harness(source, this, StandardTestDispatcher(testScheduler))

        harness.controller.downloadModels(listOf("it", "ru"))
        runCurrent()

        assertEquals(DownloadState.Done, harness.state.ttsDownloadState)
        assertFalse(harness.state.ttsMeteredNetwork)
        assertTrue(source.batches.isEmpty())
    }

    @Test
    fun old_selected_download_does_not_mark_new_language_ready() = runTest {
        val finishDownload = CompletableDeferred<Unit>()
        val source = FakeSource().apply {
            singleFlow = { language -> flow {
                emit(DownloadState.Downloading(10, 1, 10))
                finishDownload.await()
                ready += language
                emit(DownloadState.Done)
            } }
        }
        val harness = Harness(source, this, StandardTestDispatcher(testScheduler))
        harness.controller.startSelected()
        runCurrent()
        harness.selected = "en"
        harness.state = harness.state.copy(ttsModelReady = false, ttsDownloadState = DownloadState.Idle)
        finishDownload.complete(Unit)
        runCurrent()
        assertTrue(source.isReady("it"))
        assertFalse(source.isReady("en"))
        assertTrue(harness.state.ttsModelsReady["it"] == true)
        assertFalse(harness.state.ttsModelReady)
        assertEquals(DownloadState.Idle, harness.state.ttsDownloadState)
    }

    @Test
    fun metered_confirmation_for_an_old_ready_language_keeps_current_readiness() = runTest {
        val source = FakeSource().apply { metered = true }
        val harness = Harness(source, this, StandardTestDispatcher(testScheduler))
        harness.controller.startSelected()
        harness.selected = "en"
        source.ready += "it"
        harness.controller.confirmMetered()
        runCurrent()
        assertTrue(source.singles.isEmpty())
        assertTrue(harness.state.ttsModelsReady["it"] == true)
        assertFalse(harness.state.ttsModelReady)
        assertEquals(DownloadState.Idle, harness.state.ttsDownloadState)
    }
}
