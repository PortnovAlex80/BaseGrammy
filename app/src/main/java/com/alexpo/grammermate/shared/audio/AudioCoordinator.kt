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
import com.alexpo.grammermate.shared.audio.BluetoothAudioRouter

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
    val bluetoothRouter = BluetoothAudioRouter(appContext, coroutineScope)

    // ── Download jobs ──────────────────────────────────────────────────────

    private var ttsDownloadJob: Job? = null
    private var asrDownloadJob: Job? = null
    private var bgDownloadJob: Job? = null
    private var storyPlaybackJob: Job? = null
    private var asrRecognitionJob: Job? = null

    // ── TTS serialization mutex ────────────────────────────────────────────
    // Prevents concurrent TTS initialize/speak sequences from colliding at
    // the AudioCoordinator level. TtsEngine has its own internal mutex but
    // doRelease() (language switch) can free native resources while another
    // coroutine is using them. Serializing here closes that window.
    private val ttsMutex = Mutex()

    // ── Segment player (shared playback engine) ────────────────────────────
    // Factored out of the former private playSegments(...) so the background-vocab
    // DeckPlayer can reuse the exact same loop without depending on this class.
    // SegmentPlayer self-serializes via its own internal mutex.
    private val segmentPlayer = SegmentPlayer(ttsEngine)

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
                useBluetoothMic = config.useBluetoothMic,
                ruTextScale = config.ruTextScale,
                voiceAutoStart = config.voiceAutoStart,
                ttsSpeed = config.ttsSpeed,
                asrModelReady = asrModelManager.isReady()
            )
        }

        // Probe disk for present TTS models so the Settings section shows correct
        // "✓ Загружено" / "Не загружено" status on first open.
        refreshTtsModelsPresent()
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
        val langId = languageId ?: stateAccess.uiState.value.navigation.selectedLanguageId?.value ?: "en"
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
        Log.d(TAG, "stopTts() called — storyPlaybackJob=${storyPlaybackJob?.isActive}, isStoryPlaybackActive=${_audioState.value.isStoryPlaybackActive}")
        storyPlaybackJob?.cancel()
        storyPlaybackJob = null
        _audioState.update { it.copy(isStoryPlaybackActive = false, isStoryPlaybackPaused = false) }
        ttsEngine.stop()
    }

    /**
     * Pause story playback. Immediately stops audio output via ttsEngine.stop().
     * The coordinator loop detects the paused state and waits for resume,
     * then re-speaks the current segment from the beginning.
     */
    fun pauseStoryPlayback() {
        Log.d(TAG, "pauseStoryPlayback() called — job=${storyPlaybackJob?.isActive}, active=${audioState.value.isStoryPlaybackActive}")
        if (storyPlaybackJob?.isActive == true && audioState.value.isStoryPlaybackActive) {
            _audioState.update { it.copy(isStoryPlaybackPaused = true) }
            ttsEngine.stop()  // Immediately stop all audio — reliable for both offline and system TTS
            Log.d(TAG, "Story playback paused — audio stopped, waiting for resume")
        } else {
            Log.w(TAG, "pauseStoryPlayback() ignored — job not active or playback not active")
        }
    }

    /**
     * Resume story playback after a pause.
     * Clears the paused flag; the coordinator loop will re-speak the current segment.
     */
    fun resumeStoryPlayback() {
        if (audioState.value.isStoryPlaybackPaused) {
            _audioState.update { it.copy(isStoryPlaybackPaused = false) }
            Log.d(TAG, "Story playback resumed — coordinator will re-speak current segment")
        }
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
        _audioState.update { it.copy(isStoryPlaybackActive = true, isStoryPlaybackPaused = false) }
        storyPlaybackJob = coroutineScope.launch {
            ttsMutex.withLock {
                try {
                    // Stories contain no {pause:N} markers, so parseSegments returns an
                    // all-Text list — behavior is identical to the old parseStory path.
                    val segments = com.alexpo.grammermate.data.MultilingualStoryParser.parseSegments(
                        content,
                        defaultLanguageId
                    )

                    Log.d(
                        TAG,
                        "Playing ${segments.size} segments with languages: " +
                            segments.map {
                                when (it) {
                                    is com.alexpo.grammermate.data.MultilingualStoryParser.Segment.Text -> it.languageId
                                    is com.alexpo.grammermate.data.MultilingualStoryParser.Segment.Pause -> "pause"
                                    is com.alexpo.grammermate.data.MultilingualStoryParser.Segment.Audio -> "audio:${it.languageId}"
                                }
                            }
                    )

                    playSegmentsViaSharedEngine(
                        segments = segments,
                        speed = _audioState.value.ttsSpeed
                    )

                    Log.d(TAG, "All segments played successfully")
                } catch (e: Throwable) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        Log.e(TAG, "Multilingual story playback failed", e)
                    }
                } finally {
                    _audioState.update { it.copy(isStoryPlaybackActive = false) }
                }
            }
        }
    }

    /**
     * Thin delegation to the shared [segmentPlayer].
     *
     * Story playback already ran inside [ttsMutex] (see [playMultilingualStory]),
     * and SegmentPlayer has its own internal mutex — the two are independent and
     * never nest (SegmentPlayer does not call back into AudioCoordinator), so
     * there is no deadlock risk. The story pause flag is read directly off
     * [_audioState] exactly as before, preserving identical pause/resume behavior.
     *
     * Behavior is identical to the former private [playSegments]: same init-if-needed,
     * speak, poll-until-not-Speaking, and re-speak-on-resume semantics.
     */
    private suspend fun playSegmentsViaSharedEngine(
        segments: List<com.alexpo.grammermate.data.MultilingualStoryParser.Segment>,
        speed: Float
    ) {
        segmentPlayer.playSegments(
            segments = segments,
            speed = speed,
            isPaused = { _audioState.value.isStoryPlaybackPaused }
        )
    }

    fun setTtsSpeed(speed: Float) {
        val coerced = speed.coerceIn(0.5f, 1.5f)
        _audioState.update { it.copy(ttsSpeed = coerced) }
        val config = configStore.load()
        configStore.save(config.copy(ttsSpeed = coerced))
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
                    val selectedLangId = stateAccess.uiState.value.navigation.selectedLanguageId?.value
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
                    val selectedLangId = stateAccess.uiState.value.navigation.selectedLanguageId?.value
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

    /**
     * Public entry point for the Settings "Голосовые модели (TTS)" section.
     * Downloads the given TTS models sequentially via [TtsModelManager.downloadMultiple],
     * updating [_audioState.ttsDownloadState] (aggregate progress) and
     * [_audioState.ttsModelsReady] (per-language presence map) as each completes.
     *
     * Defaults to the background-vocab languages (Italian + Russian). Callers may
     * override [languageIds] for other combinations.
     *
     * Mirrors the [startAsrDownload] / [beginAsrDownload] pattern: guards against
     * a duplicate in-flight job and against a metered network, then delegates to
     * [downloadTtsModelsInternal].
     */
    fun downloadTtsModels(languageIds: List<String> = listOf("it", "ru")) {
        if (languageIds.isEmpty()) return
        // Refresh presence map first so UI status reflects current disk state.
        refreshTtsModelsPresent(languageIds)
        // If every requested model is already on disk, surface Done and skip network.
        if (languageIds.all { ttsModelManager.isModelReady(it) }) {
            _audioState.update { it.copy(ttsDownloadState = DownloadState.Done) }
            return
        }
        if (ttsModelManager.isNetworkMetered()) {
            _audioState.update { it.copy(ttsMeteredNetwork = true) }
            return
        }
        downloadTtsModelsInternal(languageIds)
    }

    private fun downloadTtsModelsInternal(languageIds: List<String>) {
        // A foreground multi-language download cancels any competing background batch.
        bgDownloadJob?.cancel()
        if (ttsDownloadJob?.isActive == true) {
            Log.d(TAG, "downloadTtsModels: a TTS download is already in progress, ignoring")
            return
        }
        val missing = languageIds.filter { !ttsModelManager.isModelReady(it) }
        if (missing.isEmpty()) {
            _audioState.update { it.copy(ttsDownloadState = DownloadState.Done) }
            refreshTtsModelsPresent(languageIds)
            return
        }
        ttsDownloadJob = coroutineScope.launch(Dispatchers.IO) {
            _audioState.update { it.copy(ttsDownloadState = DownloadState.Downloading(0, 0L, 0L)) }
            ttsModelManager.downloadMultiple(missing).collect { stateMap ->
                val anyActive = stateMap.values.any {
                    it is DownloadState.Downloading ||
                        it is DownloadState.Extracting ||
                        it is DownloadState.Initializing
                }
                val anyError = stateMap.values.filterIsInstance<DownloadState.Error>().firstOrNull()
                val allDone = stateMap.values.all { it is DownloadState.Done }

                _audioState.update { current ->
                    // Aggregate the per-language states into a single representative
                    // DownloadState for the shared ttsDownloadState field consumed by the
                    // Settings UI. Prefer the first in-flight sub-state, else Error, else Done.
                    val aggregate: DownloadState = when {
                        anyError != null -> anyError
                        anyActive -> {
                            // Pick the most informative active sub-state (prefer Downloading with bytes).
                            val downloading = stateMap.values.filterIsInstance<DownloadState.Downloading>().firstOrNull()
                            val extracting = stateMap.values.filterIsInstance<DownloadState.Extracting>().firstOrNull()
                            val initializing = stateMap.values.filterIsInstance<DownloadState.Initializing>().firstOrNull()
                            downloading ?: extracting ?: initializing ?: current.ttsDownloadState
                        }
                        allDone -> DownloadState.Done
                        else -> current.ttsDownloadState
                    }
                    val updatedReady = current.ttsModelsReady.toMutableMap().apply {
                        stateMap.forEach { (langId, dlState) ->
                            if (dlState is DownloadState.Done) this[langId] = true
                        }
                    }
                    current.copy(
                        bgTtsDownloadStates = stateMap,
                        ttsDownloadState = aggregate,
                        ttsModelsReady = updatedReady
                    )
                }

                if (allDone) {
                    refreshTtsModelsPresent(languageIds)
                    _audioState.update { it.copy(ttsDownloadState = DownloadState.Done) }
                }
            }
        }
    }

    /**
     * Refresh [_audioState.ttsModelsReady] for the given languages by probing disk
     * via [TtsModelManager.isModelReady]. Called on init, before a manual download,
     * and after a download completes.
     */
    fun refreshTtsModelsPresent(languageIds: List<String> = TtsModelRegistry.models.keys.toList()) {
        if (languageIds.isEmpty()) return
        val probe = languageIds.associateWith { ttsModelManager.isModelReady(it) }
        _audioState.update { current ->
            val merged = current.ttsModelsReady.toMutableMap().apply { putAll(probe) }
            current.copy(ttsModelsReady = merged)
        }
    }

    // ── TTS model checks ──────────────────────────────────────────────────

    fun checkTtsModel() {
        val langId = stateAccess.uiState.value.navigation.selectedLanguageId ?: return
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
        // Cancel any previous recognition before starting new one
        asrRecognitionJob?.cancel()
        asrRecognitionJob = coroutineScope.launch {
            try {
                val result = transcribeWithOfflineAsr()
                if (result.isNotBlank()) {
                    onResult(result)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Offline recognition failed", e)
                }
            } finally {
                asrRecognitionJob = null
            }
        }
    }

    fun stopAsr() {
        asrRecognitionJob?.cancel()
        asrRecognitionJob = null
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

    fun setUseBluetoothMic(enabled: Boolean) {
        _audioState.update { it.copy(useBluetoothMic = enabled) }
        val config = configStore.load()
        configStore.save(config.copy(useBluetoothMic = enabled))
    }

    suspend fun startBluetoothMicIfNeeded(): Boolean {
        if (!_audioState.value.useBluetoothMic) return true
        val connected = bluetoothRouter.startBluetoothAudio()
        _audioState.update { it.copy(bluetoothMicConnected = connected) }
        return connected
    }

    fun stopBluetoothMicIfNeeded() {
        if (_audioState.value.useBluetoothMic) {
            bluetoothRouter.stopBluetoothAudio()
            _audioState.update { it.copy(bluetoothMicConnected = false) }
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
                    val selectedLangId = stateAccess.uiState.value.navigation.selectedLanguageId?.value
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
                    val selectedLang = stateAccess.uiState.value.navigation.selectedLanguageId?.value
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
        asrRecognitionJob?.cancel()
        asrRecognitionJob = null
        ttsEngine.release()
        asrEngine?.release()
        bluetoothRouter.release()
        soundPool.release()
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private fun beginTtsDownload() {
        if (ttsDownloadJob?.isActive == true) {
            Log.d(TAG, "TTS download already in progress, ignoring duplicate request")
            return
        }
        bgDownloadJob?.cancel() // Cancel any competing background download
        val langId = stateAccess.uiState.value.navigation.selectedLanguageId ?: return
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
                                stateAccess.uiState.value.navigation.selectedLanguageId?.value?.let { asrEngine?.initialize(it) }
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
            stateAccess.uiState.value.navigation.selectedLanguageId?.value?.let { engine.initialize(it) }
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

        // Route to Bluetooth microphone if enabled
        try {
            startBluetoothMicIfNeeded()
        } catch (_: Exception) {}

        val result = engine.recordAndTranscribe()
        stateJob.cancel()

        stopBluetoothMicIfNeeded()

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
