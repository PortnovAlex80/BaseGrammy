package com.alexpo.grammermate.shared.audio

import android.util.Log
import com.alexpo.grammermate.data.AudioState
import com.alexpo.grammermate.data.DownloadState
import com.alexpo.grammermate.data.TtsModelManager
import com.alexpo.grammermate.data.TtsModelRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal interface TtsModelSource {
    fun isReady(languageId: String): Boolean
    fun isMetered(): Boolean
    fun download(languageId: String): Flow<DownloadState>
    fun downloadMultiple(languageIds: List<String>): Flow<Map<String, DownloadState>>
}

internal class TtsModelManagerSource(private val manager: TtsModelManager) : TtsModelSource {
    override fun isReady(languageId: String) = manager.isModelReady(languageId)
    override fun isMetered() = manager.isNetworkMetered()
    override fun download(languageId: String) = manager.download(languageId)
    override fun downloadMultiple(languageIds: List<String>) = manager.downloadMultiple(languageIds)
}

/** Serializes model-file writers and projects their progress into AudioCoordinator's state. */
internal class TtsModelDownloadController(
    private val source: TtsModelSource,
    private val scope: CoroutineScope,
    private val readState: () -> AudioState,
    private val updateState: ((AudioState) -> AudioState) -> Unit,
    private val selectedLanguageId: () -> String?,
    private val configuredLanguageIds: () -> List<String>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private sealed interface Request {
        data class Selected(val languageId: String) : Request
        data class Batch(val languageIds: List<String>) : Request
    }

    private var foregroundJob: Job? = null
    private var backgroundJob: Job? = null
    private var pendingMeteredRequest: Request? = null

    fun startSelected() {
        val languageId = selectedLanguageId() ?: return
        requestManual(Request.Selected(languageId))
    }

    fun downloadModels(languageIds: List<String>) {
        if (languageIds.isEmpty()) return
        refreshPresent(languageIds)
        if (languageIds.all(source::isReady)) {
            pendingMeteredRequest = null
            updateState { it.copy(ttsDownloadState = DownloadState.Done) }
            return
        }
        requestManual(Request.Batch(languageIds.toList()))
    }

    fun confirmMetered() {
        updateState { it.copy(ttsMeteredNetwork = false) }
        val request = pendingMeteredRequest ?: return
        pendingMeteredRequest = null
        launchManual(request)
    }

    fun dismissMetered() {
        pendingMeteredRequest = null
        updateState { it.copy(ttsMeteredNetwork = false) }
    }

    fun dismissDownloadDialog() {
        val state = readState().ttsDownloadState
        if (state is DownloadState.Done || state is DownloadState.Error) {
            updateState { it.copy(ttsDownloadState = DownloadState.Idle) }
        }
    }

    /** Automatic per-language request; it shares the foreground writer slot. */
    fun downloadForLanguage(languageId: String) {
        if (source.isReady(languageId)) {
            updateState {
                it.copy(
                    ttsModelsReady = it.ttsModelsReady + (languageId to true),
                    bgTtsDownloadStates = it.bgTtsDownloadStates + (languageId to DownloadState.Done)
                )
            }
            return
        }
        launchForeground { downloadSingle(languageId, selectedRequest = false) }
    }

    fun startBackground() {
        val languages = configuredLanguageIds()
        Log.d(TAG, "startBackgroundTtsDownload: languages count = ${languages.size}")
        if (languages.isEmpty()) {
            Log.w(TAG, "startBackgroundTtsDownload: no languages configured")
            return
        }
        val missing = languages.filterNot(source::isReady)
        Log.d(TAG, "startBackgroundTtsDownload: missingLanguages = $missing")
        if (missing.isEmpty()) return
        if (backgroundJob?.isCompleted == false) return
        val job = scope.launch(dispatcher, start = CoroutineStart.LAZY) {
            if (foregroundJob?.isCompleted == false) return@launch
            source.downloadMultiple(missing).collect { states -> projectBackground(states) }
        }
        backgroundJob = job
        job.start()
    }

    fun refreshPresent(languageIds: List<String> = TtsModelRegistry.models.keys.toList()) {
        if (languageIds.isEmpty()) return
        val probe = languageIds.associateWith(source::isReady)
        updateState { current ->
            current.copy(ttsModelsReady = current.ttsModelsReady + probe)
        }
    }

    fun checkAllModels() {
        val ready = TtsModelRegistry.models.keys.associateWith(source::isReady)
        updateState { it.copy(ttsModelsReady = ready) }
    }

    fun cancel() {
        foregroundJob?.cancel()
        backgroundJob?.cancel()
        pendingMeteredRequest = null
    }

    private fun requestManual(request: Request) {
        if (source.isMetered()) {
            pendingMeteredRequest = request
            updateState { it.copy(ttsMeteredNetwork = true) }
            return
        }
        pendingMeteredRequest = null
        launchManual(request)
    }

    private fun launchManual(request: Request) {
        when (request) {
            is Request.Selected -> {
                if (source.isReady(request.languageId)) {
                    updateState {
                        it.copy(
                            ttsModelReady = if (selectedLanguageId() == request.languageId) true else it.ttsModelReady,
                            ttsDownloadState = if (selectedLanguageId() == request.languageId) DownloadState.Done else it.ttsDownloadState,
                            ttsModelsReady = it.ttsModelsReady + (request.languageId to true)
                        )
                    }
                } else {
                    launchForeground { downloadSingle(request.languageId, selectedRequest = true) }
                }
            }
            is Request.Batch -> {
                val missing = request.languageIds.filterNot(source::isReady)
                if (missing.isEmpty()) {
                    updateState { it.copy(ttsDownloadState = DownloadState.Done) }
                    refreshPresent(request.languageIds)
                } else {
                    launchForeground { downloadBatch(missing, request.languageIds) }
                }
            }
        }
    }

    private fun launchForeground(download: suspend () -> Unit) {
        if (foregroundJob?.isCompleted == false) {
            Log.d(TAG, "TTS download already in progress, ignoring duplicate request")
            return
        }
        val priorBackground = backgroundJob
        val job = scope.launch(dispatcher, start = CoroutineStart.LAZY) {
            // Cancellation alone is insufficient: TtsModelManager uses blocking file I/O.
            priorBackground?.cancelAndJoin()
            updateState { it.copy(bgTtsDownloading = false) }
            download()
        }
        foregroundJob = job
        job.start()
    }

    private suspend fun downloadSingle(languageId: String, selectedRequest: Boolean) {
        source.download(languageId).collect { state ->
            if (selectedRequest) {
                updateState { current ->
                    val isSelected = selectedLanguageId() == languageId
                    if (state is DownloadState.Done) {
                        current.copy(
                            ttsDownloadState = if (isSelected) DownloadState.Done else current.ttsDownloadState,
                            ttsModelReady = if (isSelected) true else current.ttsModelReady,
                            ttsModelsReady = current.ttsModelsReady + (languageId to true)
                        )
                    } else if (isSelected) current.copy(ttsDownloadState = state) else current
                }
            } else {
                updateState { current ->
                    val selected = selectedLanguageId()
                    val primary = if (languageId == selected && state !is DownloadState.Idle &&
                        current.ttsDownloadState !is DownloadState.Done
                    ) state else current.ttsDownloadState
                    current.copy(
                        bgTtsDownloadStates = current.bgTtsDownloadStates + (languageId to state),
                        ttsDownloadState = primary,
                        ttsModelsReady = if (state is DownloadState.Done)
                            current.ttsModelsReady + (languageId to true) else current.ttsModelsReady,
                        ttsModelReady = if (state is DownloadState.Done && languageId == selected)
                            true else current.ttsModelReady
                    )
                }
            }
            if (state is DownloadState.Done) {
                Log.d(TAG, "TTS model ready for $languageId; engine will initialize on first speak")
            }
        }
    }

    private suspend fun downloadBatch(missing: List<String>, requested: List<String>) {
        updateState { it.copy(ttsDownloadState = DownloadState.Downloading(0, 0L, 0L)) }
        source.downloadMultiple(missing).collect { states ->
            val anyActive = states.values.any { it is DownloadState.Downloading ||
                it is DownloadState.Extracting || it is DownloadState.Initializing }
            val error = states.values.filterIsInstance<DownloadState.Error>().firstOrNull()
            val allDone = states.values.all { it is DownloadState.Done }
            updateState { current ->
                val aggregate: DownloadState = when {
                    error != null -> error
                    anyActive -> states.values.filterIsInstance<DownloadState.Downloading>().firstOrNull()
                        ?: states.values.filterIsInstance<DownloadState.Extracting>().firstOrNull()
                        ?: states.values.filterIsInstance<DownloadState.Initializing>().firstOrNull()
                        ?: current.ttsDownloadState
                    allDone -> DownloadState.Done
                    else -> current.ttsDownloadState
                }
                val completed = states.filterValues { it is DownloadState.Done }.keys
                current.copy(
                    bgTtsDownloadStates = states,
                    ttsDownloadState = aggregate,
                    ttsModelsReady = current.ttsModelsReady + completed.associateWith { true }
                )
            }
            if (allDone) {
                refreshPresent(requested)
                updateState { it.copy(ttsDownloadState = DownloadState.Done) }
            }
        }
    }

    private fun projectBackground(states: Map<String, DownloadState>) {
        val allDone = states.values.all { it is DownloadState.Done }
        val anyActive = states.values.any { it is DownloadState.Downloading ||
            it is DownloadState.Extracting || it is DownloadState.Initializing }
        val alreadyReady = readState().ttsModelsReady
        val newlyCompleted = states.filter { (languageId, state) ->
            state is DownloadState.Done && alreadyReady[languageId] != true
        }.keys
        updateState { current ->
            val selectedState = states[selectedLanguageId()]
            val primary = if (selectedState != null && selectedState !is DownloadState.Idle &&
                current.ttsDownloadState !is DownloadState.Done
            ) selectedState else current.ttsDownloadState
            current.copy(
                bgTtsDownloadStates = states,
                bgTtsDownloading = anyActive,
                ttsDownloadState = primary,
                ttsModelsReady = current.ttsModelsReady +
                    states.filterValues { it is DownloadState.Done }.keys.associateWith { true }
            )
        }
        if (selectedLanguageId() in newlyCompleted) {
            updateState { it.copy(ttsModelReady = true) }
        }
        if (allDone) updateState { it.copy(bgTtsDownloading = false) }
    }

    companion object { private const val TAG = "AudioCoordinator" }
}
