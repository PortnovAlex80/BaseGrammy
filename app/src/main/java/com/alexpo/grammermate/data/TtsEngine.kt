package com.alexpo.grammermate.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
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
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class TtsState {
    IDLE,
    INITIALIZING,
    READY,
    SPEAKING,
    ERROR
}

class TtsEngine(private val context: Context) {

    private val _state = MutableStateFlow(TtsState.IDLE)
    val state: StateFlow<TtsState> = _state

    val isReady: Boolean
        get() = _state.value == TtsState.READY

    private var offlineTts: OfflineTts? = null
    var activeLanguageId: String? = null
        private set

    @Volatile
    private var currentTrack: AudioTrack? = null

    private val isStopped = AtomicBoolean(false)

    private val generation = AtomicInteger(0)
    private var speakJob: Job? = null

    private val ttsScope = CoroutineScope(Dispatchers.Default)

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private var focusRequest: AudioFocusRequest? = null

    suspend fun initialize(languageId: String = "en") {
        // Fast path: already initialized for this language
        if (_state.value == TtsState.READY && activeLanguageId == languageId) return

        // If another language is loaded, release it first and await cleanup
        if (activeLanguageId != null && activeLanguageId != languageId) {
            val oldJob = speakJob
            release()
            oldJob?.join()  // Await the speakJob to prevent state races
        }

        // Wait if currently speaking — stop first, then proceed
        if (_state.value == TtsState.SPEAKING) {
            stop()
            speakJob?.join()
        }

        // Wait if another coroutine is already initializing for the same language
        if (_state.value == TtsState.INITIALIZING) {
            // Spin-wait until initialization completes
            while (_state.value == TtsState.INITIALIZING) {
                delay(50)
            }
            // After waiting, check if it succeeded
            return
        }

        if (!_state.compareAndSet(TtsState.IDLE, TtsState.INITIALIZING)
            && !_state.compareAndSet(TtsState.ERROR, TtsState.INITIALIZING)
        ) return

        val spec = TtsModelRegistry.specFor(languageId) ?: run {
            _state.value = TtsState.ERROR
            Log.e(TAG, "No TTS model for language: $languageId")
            return
        }

        withContext(Dispatchers.Default) {
            try {
                val modelDir = File(context.filesDir, "tts/${spec.modelDirName}")
                // Validate model files before loading into native code
                val missingFiles = spec.requiredFiles.filter { !File(modelDir, it).exists() || File(modelDir, it).length() == 0L }
                if (missingFiles.isNotEmpty()) {
                    throw IllegalStateException("Missing or empty model files: $missingFiles")
                }
                val config = buildConfig(spec, modelDir)
                offlineTts = OfflineTts(config = config)
                activeLanguageId = languageId
                _state.value = TtsState.READY
                Log.d(TAG, "TTS engine initialized for $languageId (${spec.modelType})")
            } catch (e: Exception) {
                _state.value = TtsState.ERROR
                Log.e(TAG, "Initialization failed for $languageId", e)
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
                numThreads = 4,
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
                numThreads = 4,
                debug = false,
                provider = "cpu",
            )
        }
        return OfflineTtsConfig(model = modelConfig)
    }

    suspend fun speak(text: String, languageId: String = "en", speakerId: Int = 0, speed: Float = 1.0f) {
        if (text.isBlank()) return
        val safeSpeed = speed.coerceIn(0.3f, 3.0f)

        try {
            // Ensure engine is initialized for the requested language
            if (activeLanguageId != languageId || _state.value != TtsState.READY) {
                initialize(languageId)
            }

            val tts = offlineTts
            if (tts == null) {
                Log.w(TAG, "speak() skipped: engine not ready, state=${_state.value}, lang=$activeLanguageId")
                return
            }

            val oldJob = speakJob
            if (oldJob != null) {
                oldJob.cancel()
                oldJob.join()
            }

            val myGeneration = generation.incrementAndGet()
            isStopped.set(false)

            speakJob = ttsScope.launch {
                _state.value = TtsState.SPEAKING

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
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e(TAG, "Playback failed", e)
                } finally {
                    if (generation.get() == myGeneration) {
                        try {
                            audioTrack.stop()
                        } catch (_: IllegalStateException) {}
                        audioTrack.release()
                        currentTrack = null
                        if (_state.value == TtsState.SPEAKING) {
                            _state.value = TtsState.READY
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
        } catch (e: Exception) {
            Log.e(TAG, "speak() failed, models may not be loaded", e)
            if (_state.value == TtsState.SPEAKING) {
                _state.value = if (offlineTts != null) TtsState.READY else TtsState.ERROR
            }
        }
    }

    fun stop() {
        isStopped.set(true)
        speakJob?.cancel()
        currentTrack?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {}
        }
    }

    /**
     * Release TTS engine resources. Safe to call from any context.
     * When called from a suspend function (e.g., initialize()), the caller
     * should use the suspend overload `releaseAndAwait()` instead.
     */
    fun release() {
        stop()
        speakJob?.cancel()
        val ttsToFree = offlineTts
        offlineTts = null
        activeLanguageId = null
        _state.value = TtsState.IDLE
        ttsScope.launch {
            speakJob?.join()
            ttsToFree?.free()
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
