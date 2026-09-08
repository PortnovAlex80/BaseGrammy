package com.alexpo.grammermate.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

sealed class TtsState {
    object Idle : TtsState()
    object Initializing : TtsState()
    object Ready : TtsState()
    object Speaking : TtsState()
    object Paused : TtsState()
    data class Error(val reason: String? = null) : TtsState()
}

/**
 * Resident LRU cache of Sherpa-ONNX [OfflineTts] models keyed by language id.
 *
 * Why this exists: the Background Vocab Listener plays alternating it↔ru segments.
 * Reloading a ~150 MB VITS_PIPER model on every language switch (1–3 s) makes the
 * vocab script choppy. Keeping the most-recently-used models resident in RAM makes
 * language switching effectively instant.
 *
 * Memory cost: each resident VITS model occupies roughly ~150 MB of native heap.
 * With [maxSize] = 3 the worst case is ~450 MB resident; the default of 3 lets
 * IT + RU + one extra coexist while evicting older models under memory pressure.
 *
 * Thread-safety: callers (TtsEngine) guard access with [Mutex]. This class itself
 * is not internally synchronized — it is a plain data structure with no Android
 * dependencies so it can be unit-tested on a plain JVM.
 *
 * Eviction policy: access-ordered [LinkedHashMap]; when [put] exceeds [maxSize],
 * the least-recently-used entry is removed and its [OfflineTts.free] is invoked
 * to release the native model handle.
 *
 * @param maxSize maximum number of models to keep resident. Must be >= 1.
 * @param freeFn  function used to release a model. Defaults to [OfflineTts.free].
 *                Injected so unit tests can pass a fake without touching native code.
 */
internal class ResidentTtsCache<T : Any>(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
    private val freeFn: (T) -> Unit
) {
    init {
        require(maxSize >= 1) { "maxSize must be >= 1, was $maxSize" }
    }

    /**
     * Access-ordered map: iteration order is from least- to most-recently-used.
     * `removeEldestEntry` is intentionally NOT used — eviction runs [freeFn] which
     * has side effects, so we handle it explicitly in [put] to control ordering.
     */
    private val map: LinkedHashMap<String, T> =
        LinkedHashMap(4, 0.75f, true)

    /**
     * Returns the resident model for [lang], or null if absent.
     * As a side effect of access-order, a successful lookup promotes [lang] to
     * most-recently-used.
     */
    fun get(lang: String): T? = map[lang]

    /** True iff a resident model exists for [lang]. Does not promote recency. */
    fun contains(lang: String): Boolean = map.containsKey(lang)

    /**
     * Stores [tts] for [lang], promoting it to most-recently-used. If inserting
     * exceeds [maxSize], the least-recently-used model is evicted and freed via
     * [freeFn]. If [lang] was already resident, the previous model is freed and
     * replaced (no double-count against [maxSize]).
     */
    fun put(lang: String, tts: T) {
        val existing = map.remove(lang)
        if (existing != null && existing !== tts) {
            safeFree(existing)
        }
        map[lang] = tts
        while (map.size > maxSize) {
            val lru = map.entries.iterator().next()
            map.remove(lru.key)
            safeFree(lru.value)
        }
    }

    /** Removes and frees the model for [lang] if present. No-op otherwise. */
    fun remove(lang: String) {
        val removed = map.remove(lang)
        if (removed != null) safeFree(removed)
    }

    /** Frees and removes ALL resident models. */
    fun clear() {
        val snapshot = ArrayList(map.values)
        map.clear()
        snapshot.forEach { safeFree(it) }
    }

    /** Snapshot of resident language ids (no ordering guarantee for callers). */
    fun keys(): Set<String> = LinkedHashSet(map.keys)

    /** Number of resident models. */
    fun size(): Int = map.size

    private fun safeFree(tts: T) {
        try {
            freeFn(tts)
        } catch (t: Throwable) {
            // Swallow — freeing is best-effort; never let it corrupt the cache.
        }
    }

    companion object {
        const val DEFAULT_MAX_SIZE = 3
    }
}

class TtsEngine(private val context: Context) {

    /**
     * True if running on 32-bit ARM (armeabi-v7a) where Sherpa-ONNX VITS models
     * crash with SIGBUS (unaligned memory access). On these devices we fall back
     * to Android system TTS instead of loading the native model.
     */
    private val is32BitArm: Boolean by lazy {
        try {
            // Build.SUPPORTED_ABIS is available from API 21
            val abis = Build.SUPPORTED_ABIS
            val isArm32 = abis.contains("armeabi-v7a") && !abis.contains("arm64-v8a")
            if (isArm32) {
                Log.w(TAG, "32-bit ARM detected (${abis.joinToString()}) — VITS native TTS disabled to prevent SIGBUS")
            }
            isArm32
        } catch (e: Exception) {
            // Fallback: check CPU ABI fields available on all API levels
            val isArm32 = Build.CPU_ABI == "armeabi-v7a" && Build.CPU_ABI2 != "arm64-v8a"
            if (isArm32) {
                Log.w(TAG, "32-bit ARM detected (legacy check: ${Build.CPU_ABI}) — VITS native TTS disabled")
            }
            isArm32
        }
    }

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    val state: StateFlow<TtsState> = _state

    val isReady: Boolean
        get() = _state.value == TtsState.Ready

    /**
     * Resident LRU cache of native [OfflineTts] models keyed by language id.
     * Replaces the previous single-model `offlineTts` var so that IT + RU can
     * coexist in memory for instant alternating playback. See [ResidentTtsCache]
     * for memory-cost notes (~150 MB per VITS model, default cap 3 ≈ 450 MB).
     */
    private val offlineTtsCache = ResidentTtsCache<OfflineTts> { it.free() }

    /**
     * Track which languages have been initialized via the system-TTS fallback
     * (VITS_PIPER files missing, or 32-bit ARM). The Android [TextToSpeech]
     * instance is shared/single; we keep a set of languages it has been
     * configured for so [speak] can route to it without reloading.
     */
    private val systemTtsLanguages: MutableSet<String> = LinkedHashSet()

    private var systemTts: TextToSpeech? = null
    var activeLanguageId: String? = null
        private set

    var onInitializing: ((InitPhase, Int) -> Unit)? = null
        private set

    fun setInitializingCallback(callback: ((InitPhase, Int) -> Unit)?) {
        onInitializing = callback
    }

    @Volatile
    private var currentTrack: AudioTrack? = null

    @Volatile
    private var systemFinalUtteranceId: String? = null

    private val isStopped = AtomicBoolean(false)

    /**
     * Guard against stale Android system TTS callbacks.
     * Set to true in [stop], cleared when a new speak starts.
     * Prevents [UtteranceProgressListener.onStart] from overriding Ready state
     * after the user pressed stop.
     */
    private val wasStopped = AtomicBoolean(false)

    private val generation = AtomicInteger(0)
    private var speakJob: Job? = null

    private val ttsScope = CoroutineScope(Dispatchers.Default)

    private val mutex = Mutex()

    @Volatile
    private var initFailed = false

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private var focusRequest: AudioFocusRequest? = null

    suspend fun initialize(languageId: String = "en"): Unit = mutex.withLock {
        // Fast path 1: requested language is already resident as a native model.
        // (Most common path during alternating it↔ru playback — no reload.)
        if (offlineTtsCache.contains(languageId)) {
            activeLanguageId = languageId
            if (_state.value != TtsState.Speaking && _state.value != TtsState.Paused) {
                _state.value = TtsState.Ready
            }
            return
        }
        // Fast path 2: requested language was configured via the system-TTS fallback.
        if (systemTtsLanguages.contains(languageId)) {
            activeLanguageId = languageId
            if (_state.value != TtsState.Speaking && _state.value != TtsState.Paused) {
                _state.value = TtsState.Ready
            }
            return
        }

        // Wait if currently speaking — stop first, then proceed.
        // NOTE: unlike the previous single-model design, we do NOT release other
        // languages here. Resident models stay in the LRU cache; only the
        // currently-playing speak job needs to be torn down before init proceeds.
        if (_state.value == TtsState.Speaking) {
            doStop()
            speakJob?.cancel()
            speakJob?.join()
        }

        val current = _state.value
        if (current != TtsState.Idle && current !is TtsState.Error && current != TtsState.Ready) return
        _state.value = TtsState.Initializing

        val spec = TtsModelRegistry.specFor(languageId) ?: run {
            _state.value = TtsState.Error("Model not loaded")
            Log.e(TAG, "No TTS model for language: $languageId")
            return
        }

        withContext(Dispatchers.Default) {
            try {
                // Phase 1: Check files (70-75%) - only for KOKORO models
                emitInitializing(InitPhase.CHECKING_FILES, 70)
                val modelDir = File(context.filesDir, "tts/${spec.modelDirName}")
                val missingFiles = spec.requiredFiles.filter { !File(modelDir, it).exists() || File(modelDir, it).length() == 0L }

                // Only KOKORO requires offline files, VITS_PIPER can use system TTS fallback
                if (missingFiles.isNotEmpty() && spec.modelType == TtsModelType.KOKORO) {
                    throw IllegalStateException("Missing or empty model files: $missingFiles")
                }

                if (missingFiles.isNotEmpty() && spec.modelType == TtsModelType.VITS_PIPER) {
                    Log.d(TAG, "VITS_PIPER model files not found for $languageId, falling back to system TTS")
                }
                emitInitializing(InitPhase.CHECKING_FILES, 75)

                // Phase 2: Load engine (75-95%)
                System.gc()
                emitInitializing(InitPhase.LOADING_MODEL, 75)

                if (missingFiles.isEmpty() && !(spec.modelType == TtsModelType.VITS_PIPER && is32BitArm)) {
                    Log.d(TAG, "Loading offline TTS model for ${spec.displayName}")
                    val config = buildConfig(spec, modelDir)
                    val tts = OfflineTts(null, config)  // null = load from filesystem, not assets
                    // Insert into resident LRU cache. If this pushes the cache over
                    // its cap, the least-recently-used model is freed automatically.
                    offlineTtsCache.put(languageId, tts)
                } else if (spec.modelType == TtsModelType.VITS_PIPER) {
                    // VITS_PIPER files missing OR 32-bit ARM (SIGBUS risk) — fallback to System TTS
                    if (is32BitArm && missingFiles.isEmpty()) {
                        Log.w(TAG, "VITS_PIPER native model skipped on 32-bit ARM to prevent SIGBUS crash")
                    }
                    // VITS_PIPER files missing - fallback to System TTS
                    Log.d(TAG, "VITS_PIPER model files not found for $languageId, falling back to system TTS")
                    val systemReady = initSystemTts(languageId)
                    if (!systemReady) {
                        throw IllegalStateException("System TTS unavailable for $languageId")
                    }
                    systemTtsLanguages.add(languageId)
                } else {
                    // KOKORO always requires offline files
                    throw IllegalStateException("Missing or empty model files: $missingFiles")
                }
                emitInitializing(InitPhase.LOADING_MODEL, 95)

                // Phase 3: Finalize (95-100%)
                emitInitializing(InitPhase.PREPARING_ENGINE, 95)
                initFailed = false
                activeLanguageId = languageId
                _state.value = TtsState.Ready
                Log.d(TAG, "TTS engine initialized for $languageId (${spec.modelType})")
            } catch (e: kotlinx.coroutines.CancellationException) {
                initFailed = true
                offlineTtsCache.remove(languageId)
                systemTtsLanguages.remove(languageId)
                _state.value = TtsState.Error("Cancelled")
                throw e
            } catch (e: Throwable) {
                initFailed = true
                // The just-loaded native model is freed via the cache remove below.
                offlineTtsCache.remove(languageId)
                systemTtsLanguages.remove(languageId)
                if (activeLanguageId == languageId) activeLanguageId = null
                val reason = when (e) {
                    is OutOfMemoryError -> "Not enough memory to load voice model"
                    is kotlinx.coroutines.TimeoutCancellationException -> "Voice engine startup timed out"
                    is UnsatisfiedLinkError -> "Native TTS library error: ${e.message ?: e.javaClass.simpleName}"
                    else -> "Voice engine init failed: ${e.message ?: e.javaClass.simpleName}"
                }
                _state.value = TtsState.Error(reason)
                Log.e(TAG, "TTS initialization failed for $languageId: $reason", e)
            }
        }
    }

    private fun emitInitializing(phase: InitPhase, percent: Int) {
        onInitializing?.invoke(phase, percent)
    }

    private suspend fun initSystemTts(languageId: String): Boolean = withContext(Dispatchers.Main) {
        systemTts?.shutdown()
        systemTts = null
        suspendCancellableCoroutine { continuation ->
            var ttsRef: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status ->
                val tts = ttsRef
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    val locale = when (languageId) {
                        "it" -> Locale.ITALIAN
                        "en" -> Locale.US
                        "ru" -> Locale("ru", "RU")
                        "de" -> Locale.GERMAN
                        "zh" -> Locale.CHINESE
                        else -> Locale(languageId)
                    }
                    val availability = tts.setLanguage(locale)
                    val ready = availability != TextToSpeech.LANG_MISSING_DATA &&
                        availability != TextToSpeech.LANG_NOT_SUPPORTED
                    if (ready) {
                        // Add utterance progress listener to track playback completion
                        val utteranceListener = object : android.speech.tts.UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                Log.d(TAG, "System TTS onStart: utteranceId=$utteranceId, wasStopped=${wasStopped.get()}, state=${_state.value}")
                                // Guard: if stop() was called after system.speak() queued the utterance,
                                // don't let this stale callback override the Ready state.
                                if (wasStopped.get()) {
                                    Log.d(TAG, "System TTS onStart: suppressed — wasStopped=true")
                                    return
                                }
                                _state.value = TtsState.Speaking
                            }

                            override fun onDone(utteranceId: String?) {
                                Log.d(TAG, "System TTS onDone: utteranceId=$utteranceId, expected=$systemFinalUtteranceId, state=${_state.value}")
                                if (_state.value == TtsState.Speaking && utteranceId == systemFinalUtteranceId) {
                                    systemFinalUtteranceId = null
                                    _state.value = TtsState.Ready
                                    abandonAudioFocus()
                                }
                            }

                            override fun onError(utteranceId: String?) {
                                Log.d(TAG, "System TTS onError: utteranceId=$utteranceId, state=${_state.value}")
                                if (_state.value == TtsState.Speaking) {
                                    systemFinalUtteranceId = null
                                    _state.value = TtsState.Error("System TTS playback error")
                                    abandonAudioFocus()
                                }
                            }

                            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                                Log.d(TAG, "System TTS onStop: utteranceId=$utteranceId, interrupted=$interrupted, state=${_state.value}")
                                if (_state.value == TtsState.Speaking) {
                                    systemFinalUtteranceId = null
                                    _state.value = TtsState.Ready
                                    abandonAudioFocus()
                                }
                            }
                        }
                        tts.setOnUtteranceProgressListener(utteranceListener)
                        systemTts = tts
                    } else {
                        tts.shutdown()
                    }
                    if (continuation.isActive) continuation.resume(ready)
                } else {
                    tts?.shutdown()
                    if (continuation.isActive) continuation.resume(false)
                }
            }
            ttsRef = TextToSpeech(context.applicationContext, listener)
            continuation.invokeOnCancellation {
                ttsRef.shutdown()
                if (systemTts === ttsRef) systemTts = null
            }
        }
    }

    private fun buildConfig(spec: TtsModelSpec, modelDir: File): OfflineTtsConfig {
        val modelConfig = when (spec.modelType) {
            TtsModelType.KOKORO -> OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = File(modelDir, "model.onnx").absolutePath,
                    voices = File(modelDir, "voices.bin").absolutePath,
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    dataDir = File(modelDir, "espeak-ng-data").absolutePath,
                ),
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                debug = false,
                provider = "cpu",
            )
            TtsModelType.VITS_PIPER -> OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(modelDir, spec.modelFileName).absolutePath,
                    lexicon = "",
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    dataDir = File(modelDir, "espeak-ng-data").absolutePath,
                    dictDir = "",
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f,
                ),
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                debug = false,
                provider = "cpu",
            )
        }
        return OfflineTtsConfig(model = modelConfig)
    }

    suspend fun speak(text: String, languageId: String = "en", speakerId: Int = 0, speed: Float = 1.0f) {
        if (text.isBlank()) return
        if (initFailed) return
        val safeSpeed = speed.coerceIn(0.3f, 3.0f)

        try {
            // Ensure an engine for the requested language is available. With the
            // resident cache, a previously-loaded language is instantly ready;
            // a never-loaded language triggers a load. `activeLanguageId` only
            // tracks the currently selected language for state-machine purposes,
            // so the real availability check is the cache + system-TTS set.
            if (!offlineTtsCache.contains(languageId) && !systemTtsLanguages.contains(languageId)) {
                initialize(languageId)
            } else if (_state.value !is TtsState.Ready && _state.value != TtsState.Speaking) {
                // Engine exists for the language but state machine needs priming.
                initialize(languageId)
            }

            mutex.withLock {
                val oldJob = speakJob
                if (oldJob != null) {
                    oldJob.cancel()
                    oldJob.join()
                }

                // Engine selection for this language:
                //   1. native resident model (cache) — preferred, instant
                //   2. system-TTS fallback — only if THIS language was
                //      configured for system TTS (not merely because some
                //      other language's system TTS instance happens to exist)
                val system = systemTts?.takeIf { systemTtsLanguages.contains(languageId) }
                if (system != null) {
                    requestAudioFocus()
                    wasStopped.set(false)  // clear stop flag before starting new speak
                    _state.value = TtsState.Speaking

                    // Split long text into sentences for Google TTS (limit ~4000 chars)
                    val maxChunkLength = 4000
                    if (text.length <= maxChunkLength) {
                        // Short text - speak directly
                        val params = android.os.Bundle().apply {
                            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                        }
                        system.setSpeechRate(safeSpeed)
                        val utteranceId = "tts-${generation.incrementAndGet()}"
                        systemFinalUtteranceId = utteranceId
                        Log.d(TAG, "System TTS speaking: text.length=${text.length}, text=\"$text\", speed=$safeSpeed, utteranceId=$utteranceId")
                        val result = system.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                        if (result != TextToSpeech.SUCCESS) {
                            systemFinalUtteranceId = null
                            _state.value = TtsState.Error("System TTS playback failed")
                            abandonAudioFocus()
                        }
                        // Don't set state to Ready here - let OnUtteranceProgressListener handle it
                        return
                    } else {
                        // Long text - split by sentence boundaries and play sequentially
                        val sentences = text.split(Regex("""(?<=[.!?])\s+"""))
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                        if (sentences.isEmpty()) {
                            systemFinalUtteranceId = null
                            _state.value = TtsState.Ready
                            abandonAudioFocus()
                            return
                        }
                        var lastResult = TextToSpeech.SUCCESS
                        for ((index, sentence) in sentences.withIndex()) {
                            val params = android.os.Bundle().apply {
                                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                            }
                            system.setSpeechRate(safeSpeed)
                            val utteranceId = "tts-${generation.incrementAndGet()}-part-$index"
                            if (index == sentences.lastIndex) {
                                systemFinalUtteranceId = utteranceId
                            }
                            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                            val result = system.speak(sentence, queueMode, params, utteranceId)
                            if (result != TextToSpeech.SUCCESS) {
                                lastResult = result
                            }
                        }
                        if (lastResult != TextToSpeech.SUCCESS) {
                            systemFinalUtteranceId = null
                            _state.value = TtsState.Error("System TTS playback failed")
                            abandonAudioFocus()
                        }
                        // Don't set state to Ready here - let OnUtteranceProgressListener handle it
                        return
                    }
                }

                val tts = offlineTtsCache.get(languageId)
                if (tts == null) {
                    Log.w(TAG, "speak() skipped: no resident model for $languageId, state=${_state.value}, activeLang=$activeLanguageId")
                    return
                }
                // Mark this language as the active one for state-machine compatibility.
                activeLanguageId = languageId

                val myGeneration = generation.incrementAndGet()
                isStopped.set(false)
                _state.value = TtsState.Speaking

                speakJob = ttsScope.launch {
                    requestAudioFocus()

                    val sampleRate = tts.sampleRate()
                    val minBufferSize = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_FLOAT
                    )

                    val audioTrack = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .setSampleRate(sampleRate)
                                .build()
                        )
                        .setBufferSizeInBytes(minBufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()

                    currentTrack = audioTrack
                    audioTrack.play()

                    try {
                        val genStart = System.currentTimeMillis()
                        Log.d(TAG, "Starting VITS_PIPER generation: text=\"$text\", speed=$safeSpeed")
                        var callbackCalled = false
                        var totalSamples = 0

                        tts.generateWithConfigAndCallback(
                            text = text,
                            config = GenerationConfig(sid = speakerId, speed = safeSpeed),
                            callback = { samples ->
                                if (!callbackCalled) {
                                    callbackCalled = true
                                    Log.d(TAG, "VITS_PIPER callback FIRST CALL: samples.size=${samples.size}")
                                }
                                totalSamples += samples.size
                                if (!isStopped.get()) {
                                    audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                                    1
                                } else {
                                    0
                                }
                            }
                        )

                        val genDuration = System.currentTimeMillis() - genStart
                        Log.d(TAG, "VITS_PIPER generation FINISHED: duration=${genDuration}ms, callbackCalled=$callbackCalled, totalSamples=$totalSamples")

                        if (!callbackCalled) {
                            Log.e(TAG, "VITS_PIPER WARNING: callback NEVER called! Model returned without generating audio.")
                        }

                        val maxDrainMs = ((totalSamples.toLong() * 1000L) / sampleRate + 750L)
                            .coerceAtLeast(750L)
                            .coerceAtMost(30_000L)
                        val drainStart = System.currentTimeMillis()
                        var pauseAccumulatedMs = 0L
                        var pauseStartMs = 0L
                        while (!isStopped.get() &&
                            audioTrack.playbackHeadPosition < totalSamples
                        ) {
                            if (_state.value == TtsState.Paused) {
                                // While paused, accumulate pause duration to exclude from drain timeout
                                if (pauseStartMs == 0L) {
                                    pauseStartMs = System.currentTimeMillis()
                                    Log.d(TAG, "AudioTrack drain: paused, excluding time from timeout")
                                }
                                delay(100)
                                continue
                            }
                            if (pauseStartMs > 0L) {
                                pauseAccumulatedMs += System.currentTimeMillis() - pauseStartMs
                                pauseStartMs = 0L
                            }
                            if (System.currentTimeMillis() - drainStart - pauseAccumulatedMs > maxDrainMs) break
                            delay(20)
                        }
                        Log.d(TAG, "AudioTrack drain: played=${audioTrack.playbackHeadPosition}, written=$totalSamples")
                    } catch (e: Throwable) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Log.e(TAG, "Playback failed", e)
                    } finally {
                        if (generation.get() == myGeneration) {
                            try {
                                audioTrack.stop()
                            } catch (_: IllegalStateException) {}
                            audioTrack.release()
                            currentTrack = null
                            if (_state.value == TtsState.Speaking || _state.value == TtsState.Paused) {
                                _state.value = TtsState.Ready
                            }
                        } else {
                            try {
                                audioTrack.stop()
                            } catch (_: IllegalStateException) {}
                            audioTrack.release()
                        }
                        abandonAudioFocus()
                    }
                }
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "speak() failed, models may not be loaded", e)
            if (_state.value == TtsState.Speaking) {
                _state.value = if (offlineTtsCache.size() > 0) TtsState.Ready else TtsState.Error("Playback failed")
            }
        }
    }

    fun stop() {
        // Non-blocking stop: set isStopped flag and cancel job without joining.
        // The speak coroutine checks isStopped in its audio callback (returns 0)
        // and drain loop, so it exits quickly. The coroutine's finally block
        // handles AudioTrack cleanup. Previously used runBlocking + join() which
        // blocked the main thread causing ANR (5s timeout).
        Log.d(TAG, "stop() called — state=${_state.value}, hasSystemTts=${systemTts != null}, residentModels=${offlineTtsCache.size()}")
        wasStopped.set(true)
        generation.incrementAndGet()  // invalidate running speak's generation check
        doStop()
        val oldJob = speakJob
        oldJob?.cancel()
        speakJob = null
        if (_state.value == TtsState.Speaking) {
            _state.value = if (offlineTtsCache.size() > 0 || systemTts != null) TtsState.Ready else TtsState.Idle
        }
        Log.d(TAG, "stop() done — state=${_state.value}")
    }

    fun pause() {
        currentTrack?.let { track ->
            if (track.state == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
                _state.value = TtsState.Paused
            }
        }
        // For system TTS: stop and let coordinator handle re-speak
        if (currentTrack == null && _state.value is TtsState.Speaking) {
            systemTts?.stop()
            _state.value = TtsState.Paused
        }
    }

    fun resume() {
        currentTrack?.let { track ->
            if (track.state == AudioTrack.PLAYSTATE_PAUSED) {
                track.play()
                _state.value = TtsState.Speaking
            }
        }
    }

    fun release() {
        // Non-blocking release: same rationale as stop() — avoid runBlocking on main thread.
        doStop()
        val oldJob = speakJob
        oldJob?.cancel()
        speakJob = null
        doRelease()
    }

    /**
     * Frees ALL resident native models, drops the system-TTS fallback, and
     * resets the engine to Idle. Called from [release] (full teardown).
     * Per-language teardown: use [releaseLanguage].
     */
    private fun doRelease() {
        // Free every resident model in the LRU cache.
        offlineTtsCache.clear()
        systemTtsLanguages.clear()

        val systemToFree = systemTts
        systemTts = null
        systemFinalUtteranceId = null
        activeLanguageId = null
        initFailed = false
        _state.value = TtsState.Idle
        speakJob = null
        systemToFree?.shutdown()
    }

    /**
     * Optional per-language teardown: frees the resident native model for [lang]
     * (or drops it from the system-TTS set) without affecting other languages.
     * Currently unused by callers but exposed for future memory management
     * (e.g. evicting a language explicitly when the app is memory-constrained).
     */
    fun releaseLanguage(lang: String) {
        if (offlineTtsCache.contains(lang)) {
            offlineTtsCache.remove(lang)
        }
        systemTtsLanguages.remove(lang)
        if (activeLanguageId == lang) {
            activeLanguageId = if (offlineTtsCache.size() > 0 || systemTtsLanguages.isNotEmpty()) {
                // pick an arbitrary remaining language as active
                offlineTtsCache.keys().firstOrNull() ?: systemTtsLanguages.first()
            } else null
            if (_state.value == TtsState.Ready && activeLanguageId == null) {
                _state.value = TtsState.Idle
            }
        }
    }

    private fun doStop() {
        isStopped.set(true)
        systemFinalUtteranceId = null
        systemTts?.stop()
        currentTrack?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {}
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                focusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to abandon audio focus", e)
        }
    }

    companion object {
        private const val TAG = "TtsEngine"
    }
}
