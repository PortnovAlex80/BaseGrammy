package com.alexpo.grammermate.ui.helpers

import android.app.Application
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.AsrEngine
import com.alexpo.grammermate.data.AsrModelManager
import com.alexpo.grammermate.data.AsrState
import com.alexpo.grammermate.data.AppConfigStore
import com.alexpo.grammermate.data.DownloadState
import com.alexpo.grammermate.data.TtsEngine
import com.alexpo.grammermate.data.TtsProvider
import com.alexpo.grammermate.data.TtsModelManager
import com.alexpo.grammermate.data.TtsModelRegistry
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.data.TrainingUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Stateful module managing all audio: TTS playback, ASR recognition, SoundPool effects.
 *
 * Owns the lifecycle of SoundPool, TtsEngine, AsrEngine, and all download jobs.
 * All audio state fields in TrainingUiState are written exclusively by this module.
 * Helpers never call other helpers directly; all coordination through TrainingViewModel.
 */
class AudioCoordinator(
    private val stateAccess: TrainingStateAccess,
    private val appContext: Application,
    private val coroutineScope: CoroutineScope,
    private val configStore: AppConfigStore
) {
    companion object {
        private const val TAG = "AudioCoordinator"
    }

    // ── SoundPool ──────────────────────────────────────────────────────────

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .build()

    private val successSoundId = soundPool.load(appContext, R.raw.voicy_correct_answer, 1)
    private val errorSoundId = soundPool.load(appContext, R.raw.voicy_bad_answer, 1)
    private val loadedSounds = mutableSetOf<Int>()

    // ── Engines ────────────────────────────────────────────────────────────

    val ttsEngine = TtsProvider.getInstance(appContext).ttsEngine
    val ttsModelManager = TtsModelManager(appContext)
    val asrModelManager = AsrModelManager(appContext)
    val asrEngine: AsrEngine? = try {
        AsrEngine(appContext)
    } catch (e: Exception) {
        Log.e(TAG, "ASR engine creation failed", e)
        null
    }

    // ── Download jobs ──────────────────────────────────────────────────────

    private var ttsDownloadJob: Job? = null
    private var asrDownloadJob: Job? = null
    private var bgDownloadJob: Job? = null

    // ── Init ───────────────────────────────────────────────────────────────

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSounds.add(sampleId)
            }
        }
    }

    // ── Sound effects (deduplicated) ───────────────────────────────────────

    /**
     * Play the success sound effect. Unifies the former playSuccessTone/playSuccessSound pair.
     */
    fun playSuccessSound() {
        if (successSoundId in loadedSounds) {
            soundPool.play(successSoundId, 1f, 1f, 0, 0, 1f)
        }
    }

    /**
     * Play the error sound effect. Unifies the former playErrorTone/playErrorSound pair.
     */
    fun playErrorSound() {
        if (errorSoundId in loadedSounds) {
            soundPool.play(errorSoundId, 1f, 1f, 0, 0, 1f)
        }
    }

    // ── TTS playback ───────────────────────────────────────────────────────

    fun onTtsSpeak(text: String, speed: Float? = null) {
        if (text.isBlank()) return
        val langId = stateAccess.uiState.value.navigation.selectedLanguageId
        val effectiveSpeed = speed ?: stateAccess.uiState.value.audio.ttsSpeed
        coroutineScope.launch {
            if (ttsEngine.state.value != TtsState.READY
                || ttsEngine.activeLanguageId != langId
            ) {
                ttsEngine.initialize(langId)
            }
            if (ttsEngine.state.value == TtsState.READY) {
                ttsEngine.speak(text, languageId = langId, speed = effectiveSpeed)
            } else {
                Log.w(TAG, "TTS not ready after initialize, state=${ttsEngine.state.value}")
            }
        }
    }

    fun stopTts() {
        ttsEngine.stop()
    }

    fun setTtsSpeed(speed: Float) {
        stateAccess.updateState { it.copy(audio = it.audio.copy(ttsSpeed = speed.coerceIn(0.5f, 1.5f))) }
    }

    fun setRuTextScale(scale: Float) {
        stateAccess.updateState { it.copy(audio = it.audio.copy(ruTextScale = scale.coerceIn(1.0f, 2.0f))) }
    }

    // ── TTS downloads ──────────────────────────────────────────────────────

    fun startTtsDownload() {
        if (ttsModelManager.isNetworkMetered()) {
            stateAccess.updateState { it.copy(audio = it.audio.copy(ttsMeteredNetwork = true)) }
            return
        }
        beginTtsDownload()
    }

    fun confirmTtsDownloadOnMetered() {
        stateAccess.updateState { it.copy(audio = it.audio.copy(ttsMeteredNetwork = false)) }
        beginTtsDownload()
    }

    fun dismissMeteredWarning() {
        stateAccess.updateState { it.copy(audio = it.audio.copy(ttsMeteredNetwork = false)) }
    }

    fun dismissTtsDownloadDialog() {
        val state = stateAccess.uiState.value.audio.ttsDownloadState
        if (state is DownloadState.Done || state is DownloadState.Error) {
            stateAccess.updateState { it.copy(audio = it.audio.copy(ttsDownloadState = DownloadState.Idle)) }
        }
    }

    fun startTtsDownloadForLanguage(languageId: String) {
        if (ttsModelManager.isModelReady(languageId)) {
            stateAccess.updateState {
                it.copy(audio = it.audio.copy(ttsModelsReady = it.audio.ttsModelsReady + (languageId to true), bgTtsDownloadStates = it.audio.bgTtsDownloadStates + (languageId to DownloadState.Done)))
            }
            return
        }
        if (ttsDownloadJob?.isActive == true) {
            Log.d(TAG, "TTS download already in progress, ignoring request for $languageId")
            return
        }
        ttsDownloadJob = coroutineScope.launch(Dispatchers.IO) {
            ttsModelManager.download(languageId).collect { downloadState ->
                stateAccess.updateState { current ->
                    val updatedBgStates = current.audio.bgTtsDownloadStates + (languageId to downloadState)
                    val updatedReady = current.audio.ttsModelsReady + (languageId to (downloadState is DownloadState.Done))
                    val downloadStateOverride = if (languageId == current.navigation.selectedLanguageId
                        && downloadState !is DownloadState.Idle
                        && current.audio.ttsDownloadState !is DownloadState.Done
                    ) {
                        downloadState
                    } else {
                        current.audio.ttsDownloadState
                    }
                    current.copy(audio = current.audio.copy(bgTtsDownloadStates = updatedBgStates, ttsModelsReady = updatedReady, ttsDownloadState = downloadStateOverride, ttsModelReady = if (languageId == current.navigation.selectedLanguageId && downloadState is DownloadState.Done) true else current.audio.ttsModelReady))
                }
            }
        }
    }

    fun setTtsDownloadStateFromBackground(bgState: DownloadState) {
        stateAccess.updateState { it.copy(audio = it.audio.copy(ttsDownloadState = bgState)) }
    }

    // ── TTS model checks ──────────────────────────────────────────────────

    fun checkTtsModel() {
        val langId = stateAccess.uiState.value.navigation.selectedLanguageId
        val ready = ttsModelManager.isModelReady(langId)
        stateAccess.updateState { it.copy(audio = it.audio.copy(ttsModelReady = ready)) }
    }

    fun checkAllTtsModels() {
        val readyMap = TtsModelRegistry.models.keys.associateWith { langId ->
            ttsModelManager.isModelReady(langId)
        }
        stateAccess.updateState { it.copy(audio = it.audio.copy(ttsModelsReady = readyMap)) }
    }

    // ── ASR ────────────────────────────────────────────────────────────────

    fun startOfflineRecognition(onResult: (String) -> Unit) {
        coroutineScope.launch {
            try {
                val result = transcribeWithOfflineAsr()
                if (result.isNotBlank()) {
                    onResult(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Offline recognition failed", e)
            }
        }
    }

    fun stopAsr() {
        asrEngine?.stopRecording()
    }

    fun setUseOfflineAsr(enabled: Boolean) {
        stateAccess.updateState { it.copy(audio = it.audio.copy(useOfflineAsr = enabled)) }
        val config = configStore.load()
        configStore.save(config.copy(useOfflineAsr = enabled))
        if (enabled) {
            checkAsrModel()
        } else {
            asrEngine?.release()
            stateAccess.updateState {
                it.copy(audio = it.audio.copy(asrState = AsrState.IDLE, asrModelReady = false, asrErrorMessage = null))
            }
        }
    }

    fun checkAsrModel() {
        val ready = asrModelManager.isReady()
        stateAccess.updateState { it.copy(audio = it.audio.copy(asrModelReady = ready)) }
    }

    fun dismissAsrDownloadDialog() {
        stateAccess.updateState { it.copy(audio = it.audio.copy(asrDownloadState = DownloadState.Idle)) }
    }

    // ── ASR downloads ──────────────────────────────────────────────────────

    fun startAsrDownload() {
        if (asrModelManager.isNetworkMetered()) {
            stateAccess.updateState { it.copy(audio = it.audio.copy(asrMeteredNetwork = true)) }
            return
        }
        beginAsrDownload()
    }

    fun confirmAsrDownloadOnMetered() {
        stateAccess.updateState { it.copy(audio = it.audio.copy(asrMeteredNetwork = false)) }
        beginAsrDownload()
    }

    fun dismissAsrMeteredWarning() {
        stateAccess.updateState { it.copy(audio = it.audio.copy(asrMeteredNetwork = false)) }
    }

    // ── Background TTS download ────────────────────────────────────────────

    fun startBackgroundTtsDownload() {
        val languages = stateAccess.uiState.value.navigation.languages
        if (languages.isEmpty()) return

        val missingLanguages = languages.map { it.id }
            .filter { !ttsModelManager.isModelReady(it) }

        if (missingLanguages.isEmpty()) return
        if (bgDownloadJob?.isActive == true) return

        bgDownloadJob = coroutineScope.launch(Dispatchers.IO) {
            ttsModelManager.downloadMultiple(missingLanguages).collect { stateMap ->
                val allDone = stateMap.values.all { it is DownloadState.Done }
                val anyActive = stateMap.values.any {
                    it is DownloadState.Downloading || it is DownloadState.Extracting
                }

                stateAccess.updateState { current ->
                    val selectedBgState = stateMap[current.navigation.selectedLanguageId]
                    val downloadStateOverride = if (selectedBgState != null
                        && selectedBgState !is DownloadState.Idle
                        && current.audio.ttsDownloadState !is DownloadState.Done
                    ) {
                        selectedBgState
                    } else {
                        current.audio.ttsDownloadState
                    }
                    val updatedReady = current.audio.ttsModelsReady.toMutableMap().apply {
                        stateMap.forEach { (langId, dlState) ->
                            if (dlState is DownloadState.Done) {
                                this[langId] = true
                            }
                        }
                    }
                    current.copy(audio = current.audio.copy(bgTtsDownloadStates = stateMap, bgTtsDownloading = anyActive, ttsModelReady = ttsModelManager.isModelReady(current.navigation.selectedLanguageId), ttsDownloadState = downloadStateOverride, ttsModelsReady = updatedReady))
                }

                if (allDone) {
                    stateAccess.updateState { it.copy(audio = it.audio.copy(bgTtsDownloading = false)) }
                }
            }
        }
    }

    // ── TTS state collection ───────────────────────────────────────────────

    /**
     * Start collecting TTS engine state into the UI state.
     * Must be called once during initialization.
     */
    fun startTtsStateCollection() {
        coroutineScope.launch {
            ttsEngine.state.collect { ttsState ->
                stateAccess.updateState { it.copy(audio = it.audio.copy(ttsState = ttsState)) }
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    fun release() {
        bgDownloadJob?.cancel()
        ttsEngine.release()
        asrEngine?.release()
        soundPool.release()
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private fun beginTtsDownload() {
        if (ttsDownloadJob?.isActive == true) {
            Log.d(TAG, "TTS download already in progress, ignoring duplicate request")
            return
        }
        val langId = stateAccess.uiState.value.navigation.selectedLanguageId
        if (ttsModelManager.isModelReady(langId)) {
            stateAccess.updateState { it.copy(audio = it.audio.copy(ttsModelReady = true, ttsDownloadState = DownloadState.Done)) }
            return
        }
        ttsDownloadJob = coroutineScope.launch(Dispatchers.IO) {
            ttsModelManager.download(langId).collect { downloadState ->
                stateAccess.updateState { it.copy(audio = it.audio.copy(ttsDownloadState = downloadState)) }
                if (downloadState is DownloadState.Done) {
                    stateAccess.updateState { it.copy(audio = it.audio.copy(ttsModelReady = true)) }
                }
            }
        }
    }

    private fun beginAsrDownload() {
        if (asrDownloadJob?.isActive == true) return
        asrDownloadJob = coroutineScope.launch(Dispatchers.IO) {
            // Download VAD first (small ~2MB)
            if (!asrModelManager.isVadReady()) {
                asrModelManager.downloadVad().collect { state ->
                    stateAccess.updateState { it.copy(audio = it.audio.copy(asrDownloadState = state)) }
                }
            }
            // Then ASR model
            if (!asrModelManager.isAsrReady()) {
                asrModelManager.downloadAsr().collect { state ->
                    stateAccess.updateState { it.copy(audio = it.audio.copy(asrDownloadState = state)) }
                    if (state is DownloadState.Done) {
                        stateAccess.updateState { it.copy(audio = it.audio.copy(asrModelReady = true)) }
                    }
                }
            }
        }
    }

    private suspend fun transcribeWithOfflineAsr(): String {
        val engine = asrEngine
        if (engine == null) {
            stateAccess.updateState {
                it.copy(audio = it.audio.copy(asrState = AsrState.ERROR, asrErrorMessage = "ASR engine unavailable on this device"))
            }
            return ""
        }
        if (!engine.isReady) {
            engine.initialize(stateAccess.uiState.value.navigation.selectedLanguageId)
        }

        // Check if initialization failed
        if (engine.state.value == AsrState.ERROR) {
            stateAccess.updateState {
                it.copy(audio = it.audio.copy(asrState = AsrState.ERROR, asrErrorMessage = engine.errorMessage ?: "ASR initialization failed"))
            }
            return ""
        }

        stateAccess.updateState { it.copy(audio = it.audio.copy(asrState = engine.state.value, asrErrorMessage = null)) }

        // Collect state updates from ASR engine
        val stateJob = coroutineScope.launch {
            engine.state.collect { asrState ->
                stateAccess.updateState { it.copy(audio = it.audio.copy(asrState = asrState)) }
            }
        }

        val result = engine.recordAndTranscribe()
        stateJob.cancel()

        // Check for errors after recording/transcription
        val finalState = engine.state.value
        val errorMsg = engine.errorMessage
        stateAccess.updateState {
            it.copy(audio = it.audio.copy(asrState = finalState, asrErrorMessage = if (result.isBlank() && errorMsg != null) errorMsg
                else if (result.isBlank() && finalState == AsrState.ERROR) "ASR recognition failed"
                else null))
        }
        return result
    }
}
