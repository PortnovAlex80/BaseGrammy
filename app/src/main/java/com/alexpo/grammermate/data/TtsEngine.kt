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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    data class Error(val reason: String? = null) : TtsState()
}

class TtsEngine(private val context: Context) {

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    val state: StateFlow<TtsState> = _state

    val isReady: Boolean
        get() = _state.value == TtsState.Ready

    private var offlineTts: OfflineTts? = null
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

    private val isStopped = AtomicBoolean(false)

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
        // Fast path: already initialized for this language
        if (_state.value == TtsState.Ready && activeLanguageId == languageId) return

        // If another language is loaded, release it first and await cleanup
        if (activeLanguageId != null && activeLanguageId != languageId) {
            val oldJob = speakJob
            doStop()
            oldJob?.cancel()
            oldJob?.join()
            doRelease()
        }

        // Wait if currently speaking — stop first, then proceed
        if (_state.value == TtsState.Speaking) {
            doStop()
            speakJob?.cancel()
            speakJob?.join()
        }

        val current = _state.value
        if (current != TtsState.Idle && current !is TtsState.Error) return
        _state.value = TtsState.Initializing

        val spec = TtsModelRegistry.specFor(languageId) ?: run {
            _state.value = TtsState.Error("Model not loaded")
            Log.e(TAG, "No TTS model for language: $languageId")
            return
        }

        withContext(Dispatchers.Default) {
            try {
                // Phase 1: Check files (70-75%) - only for offline Sherpa-ONNX models
                // System TTS doesn't require model files
                emitInitializing(InitPhase.CHECKING_FILES, 70)
                if (spec.modelType == TtsModelType.KOKORO) {
                    val modelDir = File(context.filesDir, "tts/${spec.modelDirName}")
                    val missingFiles = spec.requiredFiles.filter { !File(modelDir, it).exists() || File(modelDir, it).length() == 0L }
                    if (missingFiles.isNotEmpty()) {
                        throw IllegalStateException("Missing or empty model files: $missingFiles")
                    }
                }
                emitInitializing(InitPhase.CHECKING_FILES, 75)

                // Phase 2: Load engine (75-95%). Use Android's system TTS to avoid
                // native Sherpa TTS crashes observed on some tablets during OfflineTts init.
                System.gc()
                emitInitializing(InitPhase.LOADING_MODEL, 75)

                val systemReady = initSystemTts(languageId)
                if (!systemReady) {
                    throw IllegalStateException("Android system TTS engine is unavailable for $languageId")
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
                offlineTts = null
                _state.value = TtsState.Error("Cancelled")
                throw e
            } catch (e: Throwable) {
                initFailed = true
                val partiallyLoaded = offlineTts
                offlineTts = null
                activeLanguageId = null
                try {
                    partiallyLoaded?.free()
                } catch (freeError: Throwable) {
                    Log.w(TAG, "Failed to free partially initialized TTS", freeError)
                }
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
                        else -> Locale(languageId)
                    }
                    val availability = tts.setLanguage(locale)
                    val ready = availability != TextToSpeech.LANG_MISSING_DATA &&
                        availability != TextToSpeech.LANG_NOT_SUPPORTED
                    if (ready) {
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
            // Ensure engine is initialized for the requested language
            if (activeLanguageId != languageId || _state.value !is TtsState.Ready) {
                initialize(languageId)
            }

            mutex.withLock {
                val oldJob = speakJob
                if (oldJob != null) {
                    oldJob.cancel()
                    oldJob.join()
                }

                val system = systemTts
                if (system != null) {
                    _state.value = TtsState.Speaking
                    requestAudioFocus()
                    val params = android.os.Bundle().apply {
                        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    }
                    system.setSpeechRate(safeSpeed)
                    val result = system.speak(text, TextToSpeech.QUEUE_FLUSH, params, "tts-${generation.incrementAndGet()}")
                    _state.value = if (result == TextToSpeech.SUCCESS) TtsState.Ready else TtsState.Error("System TTS playback failed")
                    abandonAudioFocus()
                    return
                }

                val tts = offlineTts
                if (tts == null) {
                    Log.w(TAG, "speak() skipped: engine not ready, state=${_state.value}, lang=$activeLanguageId")
                    return
                }

                val myGeneration = generation.incrementAndGet()
                isStopped.set(false)

                speakJob = ttsScope.launch {
                    _state.value = TtsState.Speaking

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
                        tts.generateWithConfigAndCallback(
                            text = text,
                            config = GenerationConfig(sid = speakerId, speed = safeSpeed),
                            callback = { samples ->
                                if (!isStopped.get()) {
                                    audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                                    1
                                } else {
                                    0
                                }
                            }
                        )
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
                            if (_state.value == TtsState.Speaking) {
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
                _state.value = if (offlineTts != null) TtsState.Ready else TtsState.Error("Playback failed")
            }
        }
    }

    fun stop() = runBlocking {
        mutex.withLock {
            doStop()
            val oldJob = speakJob
            oldJob?.cancel()
            oldJob?.join()
            if (offlineTts != null && _state.value == TtsState.Speaking) {
                _state.value = TtsState.Ready
            }
        }
    }

    fun release() = runBlocking {
        mutex.withLock {
            doStop()
            val oldJob = speakJob
            oldJob?.cancel()
            oldJob?.join()
            doRelease()
        }
    }

    private fun doRelease() {
        val ttsToFree = offlineTts
        val systemToFree = systemTts
        offlineTts = null
        systemTts = null
        activeLanguageId = null
        initFailed = false
        _state.value = TtsState.Idle
        speakJob = null
        ttsToFree?.free()
        systemToFree?.shutdown()
    }

    private fun doStop() {
        isStopped.set(true)
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
