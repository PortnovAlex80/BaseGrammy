package com.alexpo.grammermate.shared.audio

import android.app.Application
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.AsrEngine
import com.alexpo.grammermate.data.AsrModelManager
import com.alexpo.grammermate.data.AsrState
import com.alexpo.grammermate.data.AppConfigStore
import com.alexpo.grammermate.data.AudioState
import com.alexpo.grammermate.data.DownloadState
import com.alexpo.grammermate.data.TtsProvider
import com.alexpo.grammermate.data.TtsModelManager
import com.alexpo.grammermate.data.TtsModelRegistry
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.feature.daily.TrainingStateAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Stateful module managing all audio: TTS playback, ASR recognition, SoundPool effects.
 *
 * Owns the lifecycle of SoundPool, TtsEngine, AsrEngine, and all download jobs.
 * Owns the [audioState] StateFlow which is the single source of truth for all audio state.
 * This flow is combined with core state in TrainingViewModel to produce the final TrainingUiState.
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

    // ── Audio state (owned by this coordinator) ────────────────────────────

    private val _audioState = MutableStateFlow(AudioState())
    val audioState: StateFlow<AudioState> = _audioState

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

        // Seed audio state from config (replaces TrainingViewModel init seeding)
        val config = configStore.load()
        _audioState.update {
            it.copy(
                useOfflineAsr = config.useOfflineAsr,
                ruTextScale = config.ruTextScale,
                voiceAutoStart = config.voiceAutoStart,
                asrModelReady = asrModelManager.isReady()
            )
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
        val effectiveSpeed = speed ?: _audioState.value.ttsSpeed
        coroutineScope.launch {
            try {
                if (ttsEngine.state.value != TtsState.Ready
                    || ttsEngine.activeLanguageId != langId.value
                ) {
                    ttsEngine.initialize(langId.value)
                }
                if (ttsEngine.state.value == TtsState.Ready) {
                    ttsEngine.speak(text, languageId = langId.value, speed = effectiveSpeed)
                } else {
                    Log.w(TAG, "TTS not ready after initialize, state=${ttsEngine.state.value}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "onTtsSpeak failed", e)
            }
        }
    }

    fun stopTts() {
        ttsEngine.stop()
    }

    fun setTtsSpeed(speed: Float) {
        _audioState.update { it.copy(ttsSpeed = speed.coerceIn(0.5f, 1.5f)) }
    }

    fun setRuTextScale(scale: Float) {
        _audioState.update { it.copy(ruTextScale = scale.coerceIn(1.0f, 2.0f)) }
    }

    /**
     * Update voiceAutoStart and persist to config.
     * Called from TrainingViewModel.setVoiceAutoStart.
     */
    fun setVoiceAutoStart(enabled: Boolean) {
        _audioState.update { it.copy(voiceAutoStart = enabled) }
        val config = configStore.load()
        configStore.save(config.copy(voiceAutoStart = enabled))
    }

    // ── TTS downloads ──────────────────────────────────────────────────────

    fun startTtsDownload() {
        if (ttsModelManager.isNetworkMetered()) {
            _audioState.update { it.copy(ttsMeteredNetwork = true) }
            return
        }
        beginTtsDownload()
    }

    fun confirmTtsDownloadOnMetered() {
        _audioState.update { it.copy(ttsMeteredNetwork = false) }
        beginTtsDownload()
    }

    fun dismissMeteredWarning() {
        _audioState.update { it.copy(ttsMeteredNetwork = false) }
    }

    fun dismissTtsDownloadDialog() {
        val state = _audioState.value.ttsDownloadState
        if (state is DownloadState.Done || state is DownloadState.Error) {
            _audioState.update { it.copy(ttsDownloadState = DownloadState.Idle) }
        }
    }

    fun startTtsDownloadForLanguage(languageId: String) {
        if (ttsModelManager.isModelReady(languageId)) {
            _audioState.update {
                it.copy(ttsModelsReady = it.ttsModelsReady + (languageId to true), bgTtsDownloadStates = it.bgTtsDownloadStates + (languageId to DownloadState.Done))
            }
            return
        }
        if (ttsDownloadJob?.isActive == true) {
            Log.d(TAG, "TTS download already in progress, ignoring request for $languageId")
            return
        }
        ttsDownloadJob = coroutineScope.launch(Dispatchers.IO) {
            ttsModelManager.download(languageId).collect { downloadState ->
                _audioState.update { current ->
                    val updatedBgStates = current.bgTtsDownloadStates + (languageId to downloadState)
                    val updatedReady = current.ttsModelsReady + (languageId to (downloadState is DownloadState.Done))
                    // For the selected language, also update the primary ttsDownloadState
                    val selectedLangId = stateAccess.uiState.value.navigation.selectedLanguageId.value
                    val downloadStateOverride = if (languageId == selectedLangId
                        && downloadState !is DownloadState.Idle
                        && current.ttsDownloadState !is DownloadState.Done
                    ) {
                        downloadState
                    } else {
                        current.ttsDownloadState
                    }
                    current.copy(
                        bgTtsDownloadStates = updatedBgStates,
                        ttsModelsReady = updatedReady,
                        ttsDownloadState = downloadStateOverride,
                        ttsModelReady = if (languageId == selectedLangId && downloadState is DownloadState.Done) true else current.ttsModelReady
                    )
                }
                // Recover TTS engine from ERROR/IDLE after per-language download completes.
                if (downloadState is DownloadState.Done) {
                    val engineState = ttsEngine.state.value
                    if (engineState is TtsState.Error || engineState == TtsState.Idle) {
                        try {
                            ttsEngine.initialize(languageId)
                            Log.d(TAG, "Auto-initialized TTS engine for $languageId after language download")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to auto-initialize TTS for $languageId after download", e)
                        }
                    }
                }
            }
        }
    }

    fun setTtsDownloadStateFromBackground(bgState: DownloadState) {
        _audioState.update { it.copy(ttsDownloadState = bgState) }
    }

    // ── TTS model checks ──────────────────────────────────────────────────

    fun checkTtsModel() {
        val langId = stateAccess.uiState.value.navigation.selectedLanguageId
        val ready = ttsModelManager.isModelReady(langId.value)
        _audioState.update { it.copy(ttsModelReady = ready) }
    }

    fun checkAllTtsModels() {
        val readyMap = TtsModelRegistry.models.keys.associateWith { langId ->
            ttsModelManager.isModelReady(langId)
        }
        _audioState.update { it.copy(ttsModelsReady = readyMap) }
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
        _audioState.update { it.copy(useOfflineAsr = enabled) }
        val config = configStore.load()
        configStore.save(config.copy(useOfflineAsr = enabled))
        if (enabled) {
            checkAsrModel()
        } else {
            asrEngine?.release()
            _audioState.update {
                it.copy(asrState = AsrState.IDLE, asrModelReady = false, asrErrorMessage = null)
            }
        }
    }

    fun checkAsrModel() {
        val ready = asrModelManager.isReady()
        _audioState.update { it.copy(asrModelReady = ready) }
    }

    fun dismissAsrDownloadDialog() {
        _audioState.update { it.copy(asrDownloadState = DownloadState.Idle) }
    }

    // ── ASR downloads ──────────────────────────────────────────────────────

    fun startAsrDownload() {
        if (asrModelManager.isNetworkMetered()) {
            _audioState.update { it.copy(asrMeteredNetwork = true) }
            return
        }
        beginAsrDownload()
    }

    fun confirmAsrDownloadOnMetered() {
        _audioState.update { it.copy(asrMeteredNetwork = false) }
        beginAsrDownload()
    }

    fun dismissAsrMeteredWarning() {
        _audioState.update { it.copy(asrMeteredNetwork = false) }
    }

    // ── Background TTS download ────────────────────────────────────────────

    fun startBackgroundTtsDownload() {
        val languages = stateAccess.uiState.value.navigation.languages
        if (languages.isEmpty()) return

        val missingLanguages = languages.map { it.id.value }
            .filter { !ttsModelManager.isModelReady(it) }

        if (missingLanguages.isEmpty()) return
        if (bgDownloadJob?.isActive == true) return

        bgDownloadJob = coroutineScope.launch(Dispatchers.IO) {
            ttsModelManager.downloadMultiple(missingLanguages).collect { stateMap ->
                val allDone = stateMap.values.all { it is DownloadState.Done }
                val anyActive = stateMap.values.any {
                    it is DownloadState.Downloading || it is DownloadState.Extracting
                }

                // Track newly completed downloads to trigger engine recovery
                val currentReadyMap = _audioState.value.ttsModelsReady
                val newlyCompleted = stateMap.filter { (langId, dlState) ->
                    dlState is DownloadState.Done && currentReadyMap[langId] != true
                }.keys

                _audioState.update { current ->
                    val selectedLangId = stateAccess.uiState.value.navigation.selectedLanguageId.value
                    val selectedBgState = stateMap[selectedLangId]
                    val downloadStateOverride = if (selectedBgState != null
                        && selectedBgState !is DownloadState.Idle
                        && current.ttsDownloadState !is DownloadState.Done
                    ) {
                        selectedBgState
                    } else {
                        current.ttsDownloadState
                    }
                    val updatedReady = current.ttsModelsReady.toMutableMap().apply {
                        stateMap.forEach { (langId, dlState) ->
                            if (dlState is DownloadState.Done) {
                                this[langId] = true
                            }
                        }
                    }
                    current.copy(
                        bgTtsDownloadStates = stateMap,
                        bgTtsDownloading = anyActive,
                        ttsModelReady = ttsModelManager.isModelReady(selectedLangId),
                        ttsDownloadState = downloadStateOverride,
                        ttsModelsReady = updatedReady
                    )
                }

                // Recover TTS engine from ERROR state when models become available.
                if (newlyCompleted.isNotEmpty()) {
                    val engineState = ttsEngine.state.value
                    if (engineState is TtsState.Error || engineState == TtsState.Idle) {
                        val selectedLang = stateAccess.uiState.value.navigation.selectedLanguageId.value
                        val langToInit = if (newlyCompleted.contains(selectedLang)) selectedLang
                            else newlyCompleted.first()
                        try {
                            ttsEngine.initialize(langToInit)
                            Log.d(TAG, "Auto-initialized TTS engine for $langToInit after background download")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to auto-initialize TTS for $langToInit after download", e)
                        }
                    }
                }

                if (allDone) {
                    _audioState.update { it.copy(bgTtsDownloading = false) }
                }
            }
        }
    }

    // ── TTS state collection ───────────────────────────────────────────────

    /**
     * Start collecting TTS engine state into the audio state flow.
     * Must be called once during initialization.
     */
    fun startTtsStateCollection() {
        coroutineScope.launch {
            ttsEngine.state.collect { ttsState ->
                _audioState.update { it.copy(ttsState = ttsState) }
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
        if (ttsModelManager.isModelReady(langId.value)) {
            _audioState.update { it.copy(ttsModelReady = true, ttsDownloadState = DownloadState.Done) }
            return
        }
        ttsDownloadJob = coroutineScope.launch(Dispatchers.IO) {
            ttsModelManager.download(langId.value).collect { downloadState ->
                _audioState.update { it.copy(ttsDownloadState = downloadState) }
                if (downloadState is DownloadState.Done) {
                    _audioState.update { it.copy(ttsModelReady = true) }
                    // Recover TTS engine from ERROR/IDLE after user-initiated download.
                    val engineState = ttsEngine.state.value
                    if (engineState is TtsState.Error || engineState == TtsState.Idle) {
                        try {
                            ttsEngine.initialize(langId.value)
                            Log.d(TAG, "Auto-initialized TTS engine for ${langId.value} after user download")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to auto-initialize TTS for ${langId.value} after download", e)
                        }
                    }
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
                    _audioState.update { it.copy(asrDownloadState = state) }
                }
            }
            // Then ASR model
            if (!asrModelManager.isAsrReady()) {
                asrModelManager.downloadAsr().collect { state ->
                    _audioState.update { it.copy(asrDownloadState = state) }
                    if (state is DownloadState.Done) {
                        _audioState.update { it.copy(asrModelReady = true) }
                    }
                }
            }
        }
    }

    private suspend fun transcribeWithOfflineAsr(): String {
        val engine = asrEngine
        if (engine == null) {
            _audioState.update {
                it.copy(asrState = AsrState.ERROR, asrErrorMessage = "ASR engine unavailable on this device")
            }
            return ""
        }
        if (!engine.isReady) {
            engine.initialize(stateAccess.uiState.value.navigation.selectedLanguageId.value)
        }

        // Check if initialization failed
        if (engine.state.value == AsrState.ERROR) {
            _audioState.update {
                it.copy(asrState = AsrState.ERROR, asrErrorMessage = engine.errorMessage ?: "ASR initialization failed")
            }
            return ""
        }

        _audioState.update { it.copy(asrState = engine.state.value, asrErrorMessage = null) }

        // Collect state updates from ASR engine
        val stateJob = coroutineScope.launch {
            engine.state.collect { asrState ->
                _audioState.update { it.copy(asrState = asrState) }
            }
        }

        val result = engine.recordAndTranscribe()
        stateJob.cancel()

        // Check for errors after recording/transcription
        val finalState = engine.state.value
        val errorMsg = engine.errorMessage
        _audioState.update {
            it.copy(asrState = finalState, asrErrorMessage = if (result.isBlank() && errorMsg != null) errorMsg
                else if (result.isBlank() && finalState == AsrState.ERROR) "ASR recognition failed"
                else null)
        }
        return result
    }
}
