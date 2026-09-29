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
import com.alexpo.grammermate.shared.TrainingStateAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
 * Owns SoundPool, TtsEngine, AsrEngine and ASR jobs; TTS transfer jobs live in
 * [TtsModelDownloadController].
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
    },
    /**
     * Optional resolver for pre-rendered chapter narration (Opus). When set, and a clip
     * resolves for the chapter being played, [playMultilingualStory] plays the clip via
     * [SegmentPlayer] (MediaPlayer) instead of synthesizing TTS. Null by default — the
     * no-resolver path stays all-TTS, preserving pre-story-audio behavior for packs/tests
     * that don't ship narration.
     */
    private val storyAudioResolver: StoryClipResolver? = null
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
    private val ttsDownloads = TtsModelDownloadController(
        source = TtsModelManagerSource(ttsModelManager),
        scope = coroutineScope,
        readState = { _audioState.value },
        updateState = { transform -> _audioState.update(transform) },
        selectedLanguageId = { stateAccess.uiState.value.navigation.selectedLanguageId?.value },
        configuredLanguageIds = { stateAccess.uiState.value.navigation.languages.map { it.id.value } }
    )
    val asrModelManager = asrModelManagerProvider(appContext)
    val asrEngine: AsrEngine? = asrEngineProvider(appContext)
    private val bluetoothRouter = BluetoothAudioRouter(appContext, coroutineScope)
    private val speechRoutes = SpeechRouteOwner(
        connect = bluetoothRouter::startBluetoothAudio,
        disconnect = bluetoothRouter::stopBluetoothAudio,
        publishConnected = { connected ->
            _audioState.update { it.copy(bluetoothMicConnected = connected) }
        }
    )

    // ── Download jobs ──────────────────────────────────────────────────────

    private var asrDownloadJob: Job? = null
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
        speechRoutes.setBluetoothEnabled(config.useBluetoothMic)
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
     * If [storyAudioResolver] is set and a pre-rendered Opus narration clip resolves for
     * `(packId, storyFile)`, the clip is played via [SegmentPlayer] (MediaPlayer) as a
     * single [MultilingualStoryParser.Segment.Audio] — bypassing TTS entirely. This is
     * the real-voice narration path. If the clip is missing (or no resolver/packId),
     * playback falls back to TTS synthesis over the parsed [content]. The fallback is
     * automatic and per-call, so a pack can ship narration for some chapters and TTS for
     * others.
     *
     * @param content Story content with {it}...{/it} markers
     * @param defaultLanguageId Default language for unmarked text
     * @param storyFile The chapter's story filename (e.g. "chapter_03.md"), used to
     *  resolve the Opus clip. Null/blank disables narration lookup (TTS only).
     * @param packId Active pack id scoping the narration lookup. Null disables lookup.
     */
    fun playMultilingualStory(
        content: String,
        defaultLanguageId: String = "en",
        storyFile: String? = null,
        packId: String? = null
    ) {
        storyPlaybackJob?.cancel()
        _audioState.update { it.copy(isStoryPlaybackActive = true, isStoryPlaybackPaused = false) }
        storyPlaybackJob = coroutineScope.launch {
            ttsMutex.withLock {
                try {
                    // Prefer a pre-rendered Opus narration clip when one resolves for this
                    // chapter. The clip already contains both languages and pacing, so we
                    // play it whole via SegmentPlayer's Audio branch (MediaPlayer).
                    val narrationClip = storyAudioResolver?.let { resolver ->
                        packId?.let { pid ->
                            resolver.resolveChapterAudio(pid, storyFile)
                        }
                    }
                    if (narrationClip != null) {
                        Log.d(TAG, "Playing pre-rendered narration: ${narrationClip.name} (packId=$packId, storyFile=$storyFile)")
                        playSegmentsViaSharedEngine(
                            segments = listOf(
                                com.alexpo.grammermate.data.MultilingualStoryParser.Segment.Audio(
                                    narrationClip,
                                    defaultLanguageId
                                )
                            ),
                            speed = _audioState.value.ttsSpeed
                        )
                        Log.d(TAG, "Narration clip played successfully")
                    } else {
                        // No narration clip on disk (or no resolver/packId) → TTS fallback.
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
                    }
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

    fun startTtsDownload() = ttsDownloads.startSelected()

    fun confirmTtsDownloadOnMetered() = ttsDownloads.confirmMetered()

    fun dismissMeteredWarning() = ttsDownloads.dismissMetered()

    fun dismissTtsDownloadDialog() = ttsDownloads.dismissDownloadDialog()

    fun startTtsDownloadForLanguage(languageId: String) = ttsDownloads.downloadForLanguage(languageId)

    fun setTtsDownloadStateFromBackground(bgState: DownloadState) {
        _audioState.update { it.copy(ttsDownloadState = bgState) }
    }

    /**
     * Update [AudioState.soundPackDownloadState] (sound-pack import/download progress).
     * Sound-pack state lives in this coordinator's [_audioState] — the same flow combined
     * into the UI state in TrainingViewModel — so it survives the `combine` that
     * overwrites `_coreState.audio`.
     */
    fun updateSoundPackDownloadState(state: DownloadState) {
        _audioState.update { it.copy(soundPackDownloadState = state) }
    }

    /**
     * Update [AudioState.soundPackInstalledCount] (live count of rendered clips on disk).
     */
    fun updateSoundPackInstalledCount(count: Int) {
        _audioState.update { it.copy(soundPackInstalledCount = count) }
    }

    /** Settings entry point for sequential downloads of the requested language models. */
    fun downloadTtsModels(languageIds: List<String> = listOf("it", "ru")) =
        ttsDownloads.downloadModels(languageIds)

    fun refreshTtsModelsPresent(languageIds: List<String> = TtsModelRegistry.models.keys.toList()) =
        ttsDownloads.refreshPresent(languageIds)

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

    fun checkAllTtsModels() = ttsDownloads.checkAllModels()

    fun startOfflineRecognition(onResult: (String) -> Unit) {
        // Cancel any previous recognition before starting new one
        val previous = asrRecognitionJob
        previous?.cancel()
        if (previous != null) asrEngine?.stopRecording()
        val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
            try {
                previous?.join()
                val result = transcribeWithOfflineAsr()
                if (result.isNotBlank()) {
                    onResult(result)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Offline recognition failed", e)
                }
            } finally {
                if (asrRecognitionJob === currentCoroutineContext()[Job]) asrRecognitionJob = null
            }
        }
        asrRecognitionJob = job
        job.start()
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
        speechRoutes.setBluetoothEnabled(enabled)
        _audioState.update { it.copy(useBluetoothMic = enabled) }
        val config = configStore.load()
        configStore.save(config.copy(useBluetoothMic = enabled))
    }

    suspend fun acquireSpeechRoute(): SpeechRouteLease? = speechRoutes.acquire()

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

    fun startBackgroundTtsDownload() = ttsDownloads.startBackground()

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
        ttsDownloads.cancel()
        asrRecognitionJob?.cancel()
        asrRecognitionJob = null
        speechRoutes.shutdown()
        ttsEngine.release()
        asrEngine?.release()
        bluetoothRouter.release()
        soundPool.release()
    }

    // ── Private helpers ────────────────────────────────────────────────────

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

        val route = acquireSpeechRoute() ?: return ""
        // Collect state updates only while this recording owns the route.
        val stateJob = coroutineScope.launch {
            engine.state.collect { asrState ->
                _audioState.update { it.copy(asrState = asrState) }
            }
        }
        val result = try {
            engine.recordAndTranscribe()
        } finally {
            stateJob.cancel()
            route.close()
        }

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
