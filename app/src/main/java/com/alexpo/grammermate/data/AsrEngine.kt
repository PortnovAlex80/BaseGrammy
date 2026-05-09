package com.alexpo.grammermate.data

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

enum class AsrState {
    IDLE, INITIALIZING, READY, LISTENING, RECOGNIZING, ERROR
}

class AsrEngine(private val context: Context) {

    private val _state = MutableStateFlow(AsrState.IDLE)
    val state: StateFlow<AsrState> = _state

    val isReady: Boolean
        get() = _state.value == AsrState.READY

    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null
    private val isStopped = AtomicBoolean(false)
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var currentRecorder: AudioRecord? = null

    suspend fun initialize() {
        if (_state.value == AsrState.READY) return

        if (_state.value == AsrState.LISTENING || _state.value == AsrState.RECOGNIZING) {
            stop()
        }

        if (!_state.compareAndSet(AsrState.IDLE, AsrState.INITIALIZING)
            && !_state.compareAndSet(AsrState.ERROR, AsrState.INITIALIZING)
        ) return

        withContext(Dispatchers.Default) {
            try {
                val asrDir = AsrModelRegistry.asrModelDir(context)
                val vadDir = AsrModelRegistry.vadModelDir(context)

                // Validate files
                val asrSpec = AsrModelRegistry.asrSpec
                val missing = asrSpec.requiredFiles.filter { !File(asrDir, it).exists() || File(asrDir, it).length() == 0L }
                if (missing.isNotEmpty()) {
                    throw IllegalStateException("Missing ASR files: $missing")
                }
                val vadFile = File(vadDir, AsrModelRegistry.VAD_FILE_NAME)
                if (!vadFile.exists() || vadFile.length() == 0L) {
                    throw IllegalStateException("Missing VAD model")
                }

                // Build recognizer config
                val recognizerConfig = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(
                        sampleRate = 16000,
                        featureDim = 80
                    ),
                    modelConfig = OfflineModelConfig(
                        dolphin = OfflineDolphinModelConfig(
                            model = File(asrDir, asrSpec.requiredFiles[0]).absolutePath
                        ),
                        tokens = File(asrDir, "tokens.txt").absolutePath,
                        numThreads = 4,
                        debug = false,
                        provider = "cpu"
                    ),
                    decodingMethod = "greedy_search"
                )
                recognizer = OfflineRecognizer(config = recognizerConfig)

                // Build VAD config
                val vadConfig = VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = vadFile.absolutePath,
                        threshold = 0.5f,
                        minSilenceDuration = 0.3f,
                        minSpeechDuration = 0.25f,
                        windowSize = 512,
                        maxSpeechDuration = 30.0f
                    ),
                    sampleRate = 16000,
                    numThreads = 1,
                    provider = "cpu",
                    debug = false
                )
                vad = Vad(config = vadConfig)

                _state.value = AsrState.READY
                Log.d(TAG, "ASR engine initialized (Dolphin CTC + Silero VAD)")
            } catch (e: Exception) {
                _state.value = AsrState.ERROR
                Log.e(TAG, "ASR initialization failed", e)
            }
        }
    }

    suspend fun recognizeFromMic(): String? {
        if (_state.value != AsrState.READY) {
            initialize()
        }
        val rec = recognizer ?: return null
        val voiceActivityDetector = vad ?: return null
        if (_state.value != AsrState.READY) return null

        isStopped.set(false)
        _state.value = AsrState.LISTENING

        return withContext(Dispatchers.Default) {
            try {
                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                    .coerceAtLeast(1024)

                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                currentRecorder = recorder
                recorder.startRecording()

                val chunkSize = 512 // matches VAD window size
                val buffer = ShortArray(chunkSize)
                val speechSamples = mutableListOf<Float>()
                var isSpeaking = false
                val startTime = System.currentTimeMillis()
                val maxDurationMs = 15_000L

                voiceActivityDetector.flush()

                while (!isStopped.get()) {
                    val read = recorder.read(buffer, 0, chunkSize)
                    if (read <= 0) break

                    // Convert short to float (-1.0 .. 1.0)
                    val floatChunk = FloatArray(read) { buffer[it].toFloat() / 32768.0f }
                    voiceActivityDetector.acceptWaveform(floatChunk)

                    if (voiceActivityDetector.isSpeechDetected()) {
                        isSpeaking = true
                        _state.value = AsrState.LISTENING
                        speechSamples.addAll(floatChunk.toList())
                    } else if (isSpeaking) {
                        // Speech ended
                        break
                    }

                    // Timeout after max duration
                    if (System.currentTimeMillis() - startTime > maxDurationMs) {
                        if (speechSamples.isNotEmpty()) break
                    }

                    // If no speech detected after 5s, give up
                    if (!isSpeaking && System.currentTimeMillis() - startTime > 5000) {
                        break
                    }
                }

                recorder.stop()
                recorder.release()
                currentRecorder = null

                if (speechSamples.isEmpty()) {
                    _state.value = AsrState.READY
                    return@withContext null
                }

                _state.value = AsrState.RECOGNIZING

                // Transcribe
                val stream = rec.createStream()
                stream.acceptWaveform(speechSamples.toFloatArray(), sampleRate)
                rec.decode(stream)
                val result = rec.getResult(stream).text?.trim()
                stream.release()

                _state.value = AsrState.READY
                Log.d(TAG, "ASR result: '$result'")
                result?.takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                Log.e(TAG, "Recognition failed", e)
                try { currentRecorder?.release() } catch (_: Exception) {}
                currentRecorder = null
                _state.value = AsrState.READY
                null
            }
        }
    }

    fun stop() {
        isStopped.set(true)
        try { currentRecorder?.stop() } catch (_: Exception) {}
    }

    fun release() {
        stop()
        recordJob?.cancel()
        val recToFree = recognizer
        val vadToFree = vad
        recognizer = null
        vad = null
        _state.value = AsrState.IDLE
        scope.launch {
            recToFree?.release()
            vadToFree?.release()
        }
    }

    companion object {
        private const val TAG = "AsrEngine"
    }
}
