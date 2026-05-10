package com.alexpo.grammermate.data

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.HomophoneReplacerConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

enum class AsrState {
    IDLE,
    INITIALIZING,
    READY,
    RECORDING,
    RECOGNIZING,
    ERROR
}

class AsrEngine(private val context: Context) {

    private val _state = MutableStateFlow(AsrState.IDLE)
    val state: StateFlow<AsrState> = _state

    /** Human-readable description of the last error, or null if no error. */
    var errorMessage: String? = null
        private set

    val isReady: Boolean
        get() = _state.value == AsrState.READY

    /** Current language code used for recognition (Whisper language code). */
    var currentLanguage: String = "en"
        private set

    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null
    private var audioRecord: AudioRecord? = null

    /**
     * Initialize the ASR engine with the Whisper Small multilingual model.
     * Must be called before transcribe() or recordAndTranscribe().
     * @param language ISO 639-1 language code (default "en"). Will be mapped to Whisper code.
     */
    suspend fun initialize(language: String = "en") {
        if (_state.value == AsrState.READY) return

        if (!_state.compareAndSet(AsrState.IDLE, AsrState.INITIALIZING)
            && !_state.compareAndSet(AsrState.ERROR, AsrState.INITIALIZING)
        ) return

        currentLanguage = AsrModelRegistry.whisperLanguageCode(language)

        withContext(Dispatchers.Default) {
            try {
                val spec = AsrModelRegistry.defaultModel
                val modelDir = File(context.filesDir, "asr/${spec.modelDirName}")

                val modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = File(modelDir, "small-encoder.int8.onnx").absolutePath,
                        decoder = File(modelDir, "small-decoder.int8.onnx").absolutePath,
                        language = currentLanguage,
                        task = "transcribe",
                        tailPaddings = -1
                    ),
                    tokens = File(modelDir, "small-tokens.txt").absolutePath,
                    numThreads = 4,
                    debug = false,
                    provider = "cpu"
                )

                val config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(),
                    modelConfig = modelConfig,
                    hr = HomophoneReplacerConfig(),
                    decodingMethod = "greedy_search"
                )

                recognizer = OfflineRecognizer(null, config)

                // Initialize VAD
                val vadSpec = AsrModelRegistry.vadModel
                val vadDir = File(context.filesDir, "asr/${vadSpec.modelDirName}")
                val vadConfig = VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = File(vadDir, "silero_vad.onnx").absolutePath,
                        threshold = 0.5f,
                        minSilenceDuration = 0.25f,
                        minSpeechDuration = 0.25f,
                        windowSize = 512,
                        maxSpeechDuration = 30.0f
                    ),
                    sampleRate = SAMPLE_RATE,
                    numThreads = 1,
                    provider = "cpu",
                    debug = false
                )
                vad = Vad(null, vadConfig)

                _state.value = AsrState.READY
                Log.d(TAG, "ASR engine initialized (Whisper Small, language=$currentLanguage)")
            } catch (e: Exception) {
                _state.value = AsrState.ERROR
                errorMessage = "ASR initialization failed: ${e.message}"
                Log.e(TAG, "ASR initialization failed", e)
            }
        }
    }

    /**
     * Change the recognition language at runtime without re-loading model files.
     * Uses OfflineRecognizer.setConfig() which only updates the language parameter.
     * @param language ISO 639-1 language code (e.g. "en", "it", "ru")
     */
    fun setLanguage(language: String) {
        val rec = recognizer
        if (rec == null) {
            Log.w(TAG, "Cannot set language: recognizer not initialized")
            return
        }
        if (_state.value != AsrState.READY) {
            Log.w(TAG, "Cannot set language: engine not in READY state (state=${_state.value})")
            return
        }

        val whisperLang = AsrModelRegistry.whisperLanguageCode(language)
        if (whisperLang == currentLanguage) return

        currentLanguage = whisperLang
        Log.d(TAG, "Switching ASR language to: $whisperLang")

        try {
            val spec = AsrModelRegistry.defaultModel
            val modelDir = File(context.filesDir, "asr/${spec.modelDirName}")

            val modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = File(modelDir, "small-encoder.int8.onnx").absolutePath,
                    decoder = File(modelDir, "small-decoder.int8.onnx").absolutePath,
                    language = whisperLang,
                    task = "transcribe",
                    tailPaddings = -1
                ),
                tokens = File(modelDir, "small-tokens.txt").absolutePath,
                numThreads = 4,
                debug = false,
                provider = "cpu"
            )

            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(),
                modelConfig = modelConfig,
                hr = HomophoneReplacerConfig(),
                decodingMethod = "greedy_search"
            )

            rec.setConfig(config)
            Log.d(TAG, "ASR language switched to: $whisperLang")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch ASR language", e)
            errorMessage = "Failed to switch language: ${e.message}"
        }
    }

    /**
     * Record audio from microphone with VAD-based endpointing and transcribe.
     * Records until speech is detected and then silence is detected, or until maxDurationMs.
     * @return Recognized text, or empty string on failure/no speech
     */
    suspend fun recordAndTranscribe(maxDurationMs: Long = 10_000): String {
        val rec = recognizer
        val vadInstance = vad
        if (rec == null || vadInstance == null) {
            Log.e(TAG, "ASR engine not initialized")
            _state.value = AsrState.ERROR
            errorMessage = "ASR engine not initialized"
            return ""
        }

        _state.value = AsrState.RECORDING
        errorMessage = null

        return withContext(Dispatchers.Default) {
            try {
                vadInstance.reset()

                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val recordBufferSize = maxOf(bufferSize, 3200) // at least 100ms at 16kHz

                val record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    recordBufferSize
                )
                audioRecord = record
                record.startRecording()

                val speechSamples = mutableListOf<Float>()
                val readBuffer = ShortArray(1600) // 100ms at 16kHz
                var hasSpeech = false
                val startTime = System.currentTimeMillis()

                while (isActive) {
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed > maxDurationMs) break

                    val read = record.read(readBuffer, 0, readBuffer.size)
                    if (read <= 0) continue

                    // Convert short samples to float
                    val floatChunk = FloatArray(read) { i ->
                        readBuffer[i] / 32768.0f
                    }

                    vadInstance.acceptWaveform(floatChunk)

                    if (vadInstance.isSpeechDetected()) {
                        hasSpeech = true
                    }

                    // Collect speech segments from VAD
                    while (!vadInstance.empty()) {
                        val segment = vadInstance.front()
                        vadInstance.pop()
                        speechSamples.addAll(segment.samples.toList())
                    }

                    // If we had speech and it's no longer detected, we're done
                    if (hasSpeech && !vadInstance.isSpeechDetected() && speechSamples.isNotEmpty()) {
                        break
                    }

                    // If no speech after 3 seconds, give up
                    if (!hasSpeech && elapsed > 3000) break
                }

                record.stop()
                record.release()
                audioRecord = null

                if (speechSamples.isEmpty()) {
                    Log.d(TAG, "No speech detected")
                    errorMessage = "No speech detected"
                    _state.value = AsrState.READY
                    return@withContext ""
                }

                _state.value = AsrState.RECOGNIZING

                val allSamples = speechSamples.toFloatArray()
                val stream = rec.createStream()
                stream.acceptWaveform(allSamples, SAMPLE_RATE)
                rec.decode(stream)
                val result = rec.getResult(stream)
                val text = result.text.trim()
                stream.release()

                Log.d(TAG, "ASR result (lang=$currentLanguage): '$text'")
                _state.value = AsrState.READY
                text
            } catch (e: Exception) {
                Log.e(TAG, "Recording/transcription failed", e)
                errorMessage = "Recording/transcription failed: ${e.message}"
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
                audioRecord = null
                _state.value = AsrState.ERROR
                ""
            }
        }
    }

    /**
     * Stop any ongoing recording.
     */
    fun stopRecording() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        if (_state.value == AsrState.RECORDING) {
            _state.value = AsrState.READY
        }
    }

    /**
     * Release all native resources.
     */
    fun release() {
        stopRecording()
        recognizer?.release()
        recognizer = null
        vad?.release()
        vad = null
        _state.value = AsrState.IDLE
    }

    companion object {
        private const val TAG = "AsrEngine"
        const val SAMPLE_RATE = 16000
    }
}
