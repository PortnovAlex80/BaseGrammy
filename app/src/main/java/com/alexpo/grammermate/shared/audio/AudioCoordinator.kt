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
import com.alexpo.grammermate.data.TtsEngine
import com.alexpo.grammermate.data.TtsProvider
import com.alexpo.grammermate.data.TtsModelManager
import com.alexpo.grammermate.data.TtsModelRegistry
import com.alexpo.grammermate.data.TtsState
import com.alexpo.grammermate.feature.daily.TrainingStateAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val configStore: AppConfigStore,
    private val soundPoolProvider: (android.content.Context) -> SoundPool = { ctx ->
        SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .build()
    },
    private val ttsEngineProvider: (Application) -> TtsEngine = { app ->
        TtsProvider.getInstance(app).ttsEngine
    },
    private val ttsModelManagerProvider: (android.content.Context) -> TtsModelManager = { ctx ->
        TtsModelManager(ctx)
    },
    private val asrModelManagerProvider: (android.content.Context) -> AsrModelManager = { ctx ->
        AsrModelManager(ctx)
    },
    private val asrEngineProvider: (android.content.Context) -> AsrEngine? = { ctx ->
        try { AsrEngine(ctx) } catch (e: Exception) {
            Log.e(TAG, "ASR engine creation failed", e)
            null
        }
    }
) {
    companion object {
        private const val TAG = "AudioCoordinator"
    }

    // ── Audio state (owned by this coordinator) ────────────────────────────

    private val _audioState = MutableStateFlow(AudioState())
    val audioState: StateFlow<AudioState> = _audioState

    // ── SoundPool ──────────────────────────────────────────────────────────

    private val soundPool = soundPoolProvider(appContext)

    private val successSoundId = soundPool.load(appContext, R.raw.voicy_correct_answer, 1)
    private val errorSoundId = soundPool.load(appContext, R.raw.voicy_bad_answer, 1)
    private val loadedSounds = mutableSetOf<Int>()

    // ── Engines ────────────────────────────────────────────────────────────

    val ttsEngine = ttsEngineProvider(appContext)
    val ttsModelManager = ttsModelManagerProvider(appContext)
    val asrModelManager = asrModelManagerProvider(appContext)
    val asrEngine: AsrEngine? = asrEngineProvider(appContext)

    // ── Download jobs ──────────────────────────────────────────────────────

    private var ttsDownloadJob: Job? = null
    private var asrDownloadJob: Job? = null
    private var bgDownloadJob: Job? = null
    private var storyPlaybackJob: Job? = null

    // ── TTS serialization mutex ────────────────────────────────────────────
    // Prevents concurrent TTS initialize/speak sequences from colliding at
    // the AudioCoordinator level. TtsEngine has its own internal mutex but
    // doRelease() (language switch) can free native resources while another
    // coroutine is using them. Serializing here closes that window.
    private val ttsMutex = Mutex()

    // ── Init ───────────────────────────────────────────────────────────────

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSounds.add(sampleId)
            }
        }

        // Set up TTS init callback for progress reporting
        ttsEngine.setInitializingCallback { phase, percent ->
            _audioState.update {
                it.copy(ttsDownloadState = DownloadState.Initializing(phase, percent))
            }
        }

        // Set up ASR init callback for progress reporting
        asrEngine?.setInitializingCallback { phase, percent ->
            _audioState.update {
                it.copy(asrDownloadState = DownloadState.Initializing(phase, percent))
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

    fun onTtsSpeak(text: String, languageId: String? = null, speed: Float? = null) {
        if (text.isBlank()) return
        // Defense in depth: never attempt speak if engine is not initialized.
        // Prevents native crash from calling speak() on an uninitialized Sherpa-ONNX engine.
        if (ttsEngine.state.value != TtsState.Ready &&
            ttsEngine.state.value != TtsState.Idle &&
            ttsEngine.state.value !is TtsState.Error
        ) return
        val langId = languageId ?: stateAccess.uiState.value.navigation.selectedLanguageId.value
        val effectiveSpeed = speed ?: _audioState.value.ttsSpeed
        coroutineScope.launch {
            ttsMutex.withLock {
                try {
                    if (ttsEngine.state.value != TtsState.Ready
                        || ttsEngine.activeLanguageId != langId
                    ) {
                        ttsEngine.initialize(langId)
                    }
                    if (ttsEngine.state.value == TtsState.Ready) {
                        ttsEngine.speak(text, languageId = langId, speed = effectiveSpeed)
                    } else {
                        Log.w(TAG, "TTS not ready after initialize, state=${ttsEngine.state.value}")
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "onTtsSpeak failed", e)
                }
            }
        }
    }

    fun stopTts() {
        storyPlaybackJob?.cancel()
        storyPlaybackJob = null
        _audioState.update { it.copy(isStoryPlaybackActive = false) }
        ttsEngine.stop()
    }

    /**
     * Play multilingual story content with language switching.
     * Parses text segments and plays them sequentially with appropriate language.
     *
     * @param content Story content with {it}...{/it} markers
     * @param defaultLanguageId Default language for unmarked text
     */
    fun playMultilingualStory(content: String, defaultLanguageId: String = "en") {
        storyPlaybackJob?.cancel()
        _audioState.update { it.copy(isStoryPlaybackActive = true) }
        storyPlaybackJob = coroutineScope.launch {
            ttsMutex.withLock {
                try {
                    val segments = com.alexpo.grammermate.data.MultilingualStoryParser.parseStory(
                        content,
                        defaultLanguageId
                    )

                    Log.d(TAG, "Playing ${segments.size} segments with languages: ${segments.map { it.languageId }}")

                    for ((index, segment) in segments.withIndex()) {
                        val cleanText = com.alexpo.grammermate.data.MultilingualStoryParser.cleanMarkdown(segment.text)
                        val previewText = cleanText.take(50).replace("\n", "\\n")

                        Log.d(TAG, "════════════════════════════════════════")
                        Log.d(TAG, "Segment $index/${segments.size} | Language: ${segment.languageId.uppercase()}")
                        Log.d(TAG, "Text preview: \"$previewText...\"")

                        // Initialize TTS for this segment's language if needed
                        if (ttsEngine.state.value != TtsState.Ready
                            || ttsEngine.activeLanguageId != segment.languageId
                        ) {
                            Log.d(TAG, "→ Initializing TTS for: ${segment.languageId}")
                            ttsEngine.initialize(segment.languageId)
                        }

                        // Wait for TTS to be ready (increased timeout for VITS_PIPER)
                        var retries = 0
                        while (ttsEngine.state.value != TtsState.Ready && retries < 50) {
                            delay(100)
                            retries++
                            if (retries % 10 == 0) {
                                Log.d(TAG, "→ Still waiting for TTS initialization... (${retries * 100}ms)")
                            }
                        }

                        if (ttsEngine.state.value == TtsState.Ready) {
                            Log.d(TAG, "→ Speaking segment $index...")
                            Log.d(TAG, "→ Text length: ${cleanText.length}, full text: \"$cleanText\"")
                            ttsEngine.speak(cleanText, languageId = segment.languageId)

                            // Wait for this segment to finish playing
                            // TTS uses UtteranceProgressListener which sets state back to Ready when done
                            var waitRetries = 0
                            while (ttsEngine.state.value == TtsState.Speaking && waitRetries < 300) {
                                delay(100)
                                waitRetries++
                            }

                            Log.d(TAG, "✓ Segment $index finished (${segment.languageId.uppercase()})")
                        } else {
                            Log.w(TAG, "⚠ TTS not ready for segment $index, skipping")
                        }
                    }

                    Log.d(TAG, "All segments played successfully")
                } catch (e: Throwable) {
                    Log.e(TAG, "Multilingual story playback failed", e)
                } finally {
                    _audioState.update { it.copy(isStoryPlaybackActive = false) }
                }
            }
        }
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
                        ttsDownloadState = downloadStateOverride
                    )
                }

                if (downloadState is DownloadState.Done) {
                    val selectedLangId = stateAccess.uiState.value.navigation.selectedLanguageId.value
                    _audioState.update {
                        it.copy(
                            ttsModelsReady = it.ttsModelsReady + (languageId to true),
                            ttsModelReady = languageId == selectedLangId || it.ttsModelReady,
                            ttsDownloadState = if (languageId == selectedLangId) DownloadState.Done else it.ttsDownloadState
                        )
                    }
                    Log.d(TAG, "TTS model ready for $languageId; engine will initialize on first speak")
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
        val filesReady = ttsModelManager.isModelReady(langId.value)
        val engineState = ttsEngine.state.value
        val engineReady = engineState == TtsState.Ready
            || engineState == TtsState.Idle
            || engineState == TtsState.Initializing
            || engineState == TtsState.Speaking
        _audioState.update { it.copy(ttsModelReady = filesReady && engineReady) }
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
        Log.d(TAG, "startBackgroundTtsDownload: languages count = ${languages.size}")

        if (languages.isEmpty()) {
            Log.w(TAG, "startBackgroundTtsDownload: no languages configured")
            return
        }

        val missingLanguages = languages.map { it.id.value }
            .filter { !ttsModelManager.isModelReady(it) }

        Log.d(TAG, "startBackgroundTtsDownload: missingLanguages = $missingLanguages")

        if (missingLanguages.isEmpty()) {
            Log.d(TAG, "startBackgroundTtsDownload: all models ready")
            return
        }
        if (bgDownloadJob?.isActive == true) {
            Log.d(TAG, "startBackgroundTtsDownload: background download already active")
            return
        }

        bgDownloadJob = coroutineScope.launch(Dispatchers.IO) {
            if (ttsDownloadJob?.isActive == true) return@launch // Manual download takes priority
            ttsModelManager.downloadMultiple(missingLanguages).collect { stateMap ->
                val allDone = stateMap.values.all { it is DownloadState.Done }
                val anyActive = stateMap.values.any {
                    it is DownloadState.Downloading ||
                        it is DownloadState.Extracting ||
                        it is DownloadState.Initializing
                }

                // Track newly completed downloads so UI can mark model files ready.
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
                        ttsDownloadState = downloadStateOverride,
                        ttsModelsReady = updatedReady
                    )
                }

                if (newlyCompleted.isNotEmpty()) {
                    val selectedLang = stateAccess.uiState.value.navigation.selectedLanguageId.value
                    if (newlyCompleted.contains(selectedLang)) {
                        _audioState.update { it.copy(ttsModelReady = true) }
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
                _audioState.update { current ->
                    // During story playback, suppress intermediate TTS state updates to prevent UI flicker
                    if (current.isStoryPlaybackActive && ttsState !is TtsState.Speaking && ttsState !is TtsState.Error) {
                        current // no-op: don't update UI during model switches
                    } else {
                        current.copy(
                            ttsState = ttsState,
                            ttsDownloadState = when (ttsState) {
                                is TtsState.Error -> DownloadState.Error(ttsState.reason ?: "Voice engine error")
                                is TtsState.Ready -> if (current.ttsDownloadState is DownloadState.Initializing) DownloadState.Done else current.ttsDownloadState
                                else -> current.ttsDownloadState
                            }
                        )
                    }
                }
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
        bgDownloadJob?.cancel() // Cancel any competing background download
        val langId = stateAccess.uiState.value.navigation.selectedLanguageId
        if (ttsModelManager.isModelReady(langId.value)) {
            _audioState.update { it.copy(ttsModelReady = true, ttsDownloadState = DownloadState.Done) }
            return
        }
        ttsDownloadJob = coroutineScope.launch(Dispatchers.IO) {
            ttsModelManager.download(langId.value).collect { downloadState ->
                when (downloadState) {
                    is DownloadState.Initializing -> {
                        _audioState.update { it.copy(ttsDownloadState = downloadState) }
                    }
                    is DownloadState.Done -> {
                        _audioState.update {
                            it.copy(
                                ttsDownloadState = DownloadState.Done,
                                ttsModelReady = true,
                                ttsModelsReady = it.ttsModelsReady + (langId.value to true)
                            )
                        }
                        Log.d(TAG, "TTS model ready for ${langId.value}; engine will initialize on first speak")
                    }
                    else -> {
                        _audioState.update { it.copy(ttsDownloadState = downloadState) }
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
                    when (state) {
                        is DownloadState.Initializing -> {
                            _audioState.update { it.copy(asrDownloadState = state) }
                        }
                        is DownloadState.Done -> {
                            // Extract complete, now initialize engine
                            try {
                                delay(500) // Let filesystem buffers flush
                                asrEngine?.initialize(stateAccess.uiState.value.navigation.selectedLanguageId.value)
                                Log.d(TAG, "Auto-initialized ASR engine after download")
                                _audioState.update {
                                    it.copy(asrDownloadState = DownloadState.Done, asrModelReady = true)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to auto-initialize ASR after download", e)
                                _audioState.update {
                                    it.copy(asrDownloadState = DownloadState.Error(e.message ?: "Init failed"))
                                }
                            }
                        }
                        else -> {
                            _audioState.update { it.copy(asrDownloadState = state) }
                        }
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
