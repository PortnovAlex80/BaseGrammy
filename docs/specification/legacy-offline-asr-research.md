# Offline ASR (Speech-to-Text) Research for GrammarMate

**Date:** 2026-04-26
**Context:** GrammarMate Android app (Kotlin 1.9.22, minSdk 24, Jetpack Compose)
**Requirement:** Offline speech recognition for Russian, English, and Italian
**Current state:** App uses Sherpa-ONNX v1.12.40 AAR for TTS (already contains full ASR support)

---

## Executive Summary

GrammarMate currently uses Android's `RecognizerIntent` for voice input, which requires an internet connection and Google app installed. This research evaluates three offline ASR solutions for on-device speech recognition supporting Russian, English, and Italian.

**Recommended approach: Sherpa-ONNX OfflineRecognizer** using the Dolphin CTC multilingual model. The library is already integrated in the project (same AAR used for TTS), so no new dependency is needed. A single ~170MB model handles all three target languages with acceptable accuracy for language learning exercises.

---

## 1. Current Voice Input Implementation

The app currently uses Android's built-in speech recognizer via `RecognizerIntent`:

- Location: `GrammarMateApp.kt`, lines ~1229 and ~2621 (two separate launchers)
- Intent: `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`
- Language mapping: `"ru" -> "ru-RU"`, `"it" -> "it-IT"`, else `"en-US"`
- Results: extracted via `RecognizerIntent.EXTRA_RESULTS`
- **Problem:** Requires internet connection and Google app; fails on devices without Google services; not suitable for offline language learning

---

## 2. Solution Comparison

### 2.1 Comparison Table

| Criterion | Sherpa-ONNX (Dolphin CTC) | Sherpa-ONNX (Whisper) | Vosk | Whisper.cpp |
|---|---|---|---|---|
| **Already in project** | Yes (same AAR as TTS) | Yes (same AAR as TTS) | No (new dependency) | No (new JNI library) |
| **Languages in 1 model** | RU + EN + IT | RU + EN + IT (multilingual) | Separate model per language | RU + EN + IT (multilingual) |
| **Model size (total)** | ~170 MB (1 multilingual model) | ~75-470 MB (tiny/base/small) | ~130 MB (3 small models) | ~75-470 MB (tiny/base/small) |
| **Accuracy** | Good for short phrases | Very good | Moderate (small models) | Very good |
| **Latency (short phrase)** | Low (~200-500ms) | Medium (~500ms-2s) | Very low (~100-300ms) | Medium (~500ms-2s) |
| **Streaming mode** | No (offline only) | No (offline only) | Yes | No |
| **Kotlin API quality** | Native Kotlin bindings | Native Kotlin bindings | Java wrapper, limited docs | Custom JNI required |
| **Runtime memory** | ~300-500 MB | ~200-800 MB | ~300 MB | ~200-800 MB |
| **License** | Apache 2.0 | Apache 2.0 | Apache 2.0 (models vary) | MIT |
| **Maintenance risk** | Low (already maintained) | Low | Medium (less active) | Medium (separate codebase) |
| **Integration effort** | Minimal | Minimal | Moderate | High |

### 2.2 Scoring (max 10 per criterion)

| Criterion | Sherpa-ONNX Dolphin | Sherpa-ONNX Whisper | Vosk | Whisper.cpp |
|---|---|---|---|---|
| Minimal changes (0-2) | 2 | 2 | 1 | 0 |
| Architecture consistency (0-2) | 2 | 2 | 1 | 0 |
| Simplicity (0-2) | 2 | 2 | 1 | 1 |
| Testability (0-2) | 2 | 2 | 1 | 1 |
| Correctness (0-2) | 2 | 2 | 2 | 2 |
| **Total** | **10** | **10** | **6** | **4** |

Sherpa-ONNX wins by a wide margin because the library is already integrated. Between Dolphin and Whisper, Dolphin is preferred for a single multilingual model that covers all three languages without per-language switching logic.

---

## 3. Solution Details

### 3.1 Sherpa-ONNX ASR (RECOMMENDED)

The existing `sherpa-onnx-static-link-onnxruntime-1.12.40.aar` in `app/libs/` already contains the full ASR API. Verified classes in the AAR:

- `OfflineRecognizer` -- main recognizer class
- `OfflineRecognizerConfig` -- configuration data class
- `OfflineRecognizerResult` -- result with text, timestamps, language
- `OfflineStream` -- audio stream for feeding samples
- `OfflineModelConfig` -- model configuration container
- `OfflineDolphinModelConfig` -- Dolphin CTC model config
- `OfflineWhisperModelConfig` -- Whisper model config
- `OfflineTransducerModelConfig` -- Transducer model config
- `OfflineSenseVoiceModelConfig` -- SenseVoice model config
- `OfflineNemoEncDecCtcModelConfig` -- NeMo CTC model config
- `OfflineOmnilingualAsrCtcModelConfig` -- Omnilingual model config
- `Vad` / `VadModelConfig` / `SileroVadModelConfig` -- Voice Activity Detection
- `WaveReader` / `WaveData` -- WAV file reading utility
- `OnlineRecognizer` / `OnlineStream` -- streaming ASR (alternative approach)
- `SpeechSegment` -- VAD output segment

**No new Gradle dependency is required.**

#### 3.1.1 Available ASR Models

**Option A: Dolphin CTC Multilingual (RECOMMENDED for single-model approach)**

Single model covering multiple European languages including Russian, English, and Italian.

| File | Description |
|---|---|
| `model.onnx` (int8) | Main ASR model |
| `tokens.txt` | Token vocabulary |

- Download URL: `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02.tar.bz2`
- Archive prefix: `sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02/`
- Estimated download size: ~170 MB
- Estimated disk size: ~350 MB
- Languages: Multiple European languages (RU, EN, IT confirmed)

**Option B: Whisper Multilingual (alternative, higher accuracy)**

OpenAI Whisper models available through Sherpa-ONNX. Multilingual (non-.en) variants support all three target languages.

| Model | Encoder | Decoder | Total | Accuracy |
|---|---|---|---|---|
| tiny (int8) | ~12 MB | ~42 MB | ~54 MB | Acceptable for short phrases |
| base (int8) | ~25 MB | ~90 MB | ~115 MB | Good |
| small (int8) | ~50 MB | ~180 MB | ~230 MB | Very good |

- Download URL (tiny int8): `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2`
- Download URL (base): `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.tar.bz2`
- Download URL (small): `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-small.tar.bz2`
- Each archive contains: `encoder.onnx`, `decoder.onnx`, `tokens.txt`

**Option C: Language-Specific Zipformer Transducer (best quality per language, 3 models)**

Highest accuracy but requires separate models per language.

| Language | Model | Estimated Size |
|---|---|---|
| English | `sherpa-onnx-zipformer-en-2023-06-26` | ~200 MB |
| Russian | `sherpa-onnx-zipformer-ru-2023-09-18` | ~200 MB |
| Italian | No dedicated model available | N/A |

**Note:** No Zipformer model exists for Italian, making this approach incomplete. A multilingual model is needed anyway for Italian.

**Option D: NeMo Parakeet TDT (English only, not suitable)**

- `sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8` -- English only
- Not suitable for multilingual requirement

**Option E: Omnilingual ASR CTC (1600 languages)**

- `sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12`
- Supports 1600 languages including RU, EN, IT
- Model size: ~300 MB (int8)
- Overkill for 3 languages but future-proof

#### 3.1.2 Voice Activity Detection (VAD)

For detecting when the user starts and stops speaking, Sherpa-ONNX includes Silero VAD:

- Model: `silero_vad.onnx` (~2 MB)
- Download URL: `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx`
- API: `Vad`, `VadModelConfig`, `SileroVadModelConfig`
- Used to segment microphone input into speech segments before passing to ASR

#### 3.1.3 Kotlin API Code Examples

**Initializing the OfflineRecognizer (Dolphin CTC):**

```kotlin
// See javap output from sherpa-onnx AAR for exact API signatures
val modelDir = File(context.filesDir, "asr/dolphin-base-ctc-multi-lang-int8")

val modelConfig = OfflineModelConfig(
    dolphin = OfflineDolphinModelConfig(
        model = File(modelDir, "model.onnx").absolutePath
    ),
    tokens = File(modelDir, "tokens.txt").absolutePath,
    numThreads = 4,
    debug = false,
    provider = "cpu"
)

val config = OfflineRecognizerConfig(
    featConfig = FeatureConfig(), // defaults: sampleRate=16000, featureDim=80, dither=0.0f
    modelConfig = modelConfig,
    decodingMethod = "greedy_search"
)

val recognizer = OfflineRecognizer(context.assets, config)
```

**Initializing the OfflineRecognizer (Whisper):**

```kotlin
val modelDir = File(context.filesDir, "asr/whisper-tiny")

val modelConfig = OfflineModelConfig(
    whisper = OfflineWhisperModelConfig(
        encoder = File(modelDir, "encoder.onnx").absolutePath,
        decoder = File(modelDir, "decoder.onnx").absolutePath,
        language = "",    // empty = auto-detect, or "ru"/"en"/"it"
        task = "transcribe",
        tailPaddings = -1  // -1 = use default
    ),
    tokens = File(modelDir, "tokens.txt").absolutePath,
    numThreads = 4,
    debug = false,
    provider = "cpu"
)

val config = OfflineRecognizerConfig(
    featConfig = FeatureConfig(),
    modelConfig = modelConfig,
    decodingMethod = "greedy_search"
)

val recognizer = OfflineRecognizer(context.assets, config)
```

**Transcribing audio from a WAV file:**

```kotlin
// Read WAV file
val waveData = WaveReader.readWave(audioFilePath.absolutePath)
// waveData.samples: FloatArray, waveData.sampleRate: Int

// Create stream and feed audio
val stream = recognizer.createStream()
stream.acceptWaveform(waveData.samples, waveData.sampleRate)

// Decode
recognizer.decode(stream)

// Get result
val result = recognizer.getResult(stream)
val recognizedText = result.text  // The transcribed text
val detectedLanguage = result.lang // Detected language (Whisper/Dolphin)

// Cleanup
stream.release()
```

**Transcribing from microphone (with VAD):**

```kotlin
// 1. Setup VAD
val vadConfig = VadModelConfig(
    sileroVadModelConfig = SileroVadModelConfig(
        model = File(vadModelDir, "silero_vad.onnx").absolutePath,
        threshold = 0.5f,
        minSilenceDuration = 0.25f,
        minSpeechDuration = 0.25f,
        windowSize = 512,     // 32ms at 16kHz
        maxSpeechDuration = 30.0f
    ),
    sampleRate = 16000,
    numThreads = 1,
    provider = "cpu",
    debug = false
)

val vad = Vad(context.assets, vadConfig)

// 2. Feed audio chunks from microphone to VAD
// (16kHz mono float samples from AudioRecord)
vad.acceptWaveform(audioChunk)

// 3. When VAD detects end of speech, get the segment
if (!vad.empty()) {
    val segment: SpeechSegment = vad.front()
    vad.pop()

    // 4. Transcribe the speech segment
    val stream = recognizer.createStream()
    stream.acceptWaveform(segment.samples, 16000)
    recognizer.decode(stream)
    val text = recognizer.getResult(stream).text
    stream.release()
}

// Cleanup
vad.release()
```

**Recognizer lifecycle management:**

```kotlin
// Release when done (important -- frees native memory)
recognizer.release()

// Or let it be garbage collected (has autoclose via Companion)
```

---

### 3.2 Vosk

Vosk is an alternative offline ASR toolkit by Alpha Cephei, based on Kaldi.

#### 3.2.1 Available Models for Target Languages

| Model | Language | Size | WER | Notes |
|---|---|---|---|---|
| `vosk-model-small-en-us-0.15` | English | 40 MB | 9.85% (LibriSpeech) | Lightweight, mobile-friendly |
| `vosk-model-small-ru-0.22` | Russian | 45 MB | 22.71% (OpenSTT) | Lightweight, mobile-friendly |
| `vosk-model-small-it-0.22` | Italian | 48 MB | 16.88% (CV test) | Lightweight, mobile-friendly |
| `vosk-model-en-us-0.22` | English | 1.8 GB | 5.69% | Server-grade |
| `vosk-model-ru-0.42` | Russian | 1.8 GB | 4.5% (audiobooks) | Server-grade |
| `vosk-model-it-0.22` | Italian | 1.2 GB | 8.10% (CV test) | Server-grade |

- Download: https://alphacephei.com/vosk/models
- License: Apache 2.0 (models), some models have different licenses

#### 3.2.2 Android Integration

```kotlin
// Vosk Android SDK dependency
dependencies {
    implementation 'com.alphacephei:vosk-android:0.3.47'
}
```

- Requires separate library (`vosk-android` AAR)
- Uses Kaldi-based models with specific directory structure
- Supports streaming recognition (good for real-time use)
- JNI-based, includes `.so` files for arm64-v8a, armeabi-v7a, x86_64

#### 3.2.3 Pros and Cons

**Pros:**
- Very small models (~40-50 MB per language)
- Streaming mode available (real-time partial results)
- Low latency (~100-300ms)
- Mature and battle-tested
- Dynamic vocabulary reconfiguration with small models

**Cons:**
- **Separate dependency** -- adds another AAR/JNI library to the project
- **3 separate models** needed for RU/EN/IT (~130 MB total for small models)
- Lower accuracy than Whisper/Dolphin for short phrases
- Less active maintenance compared to Sherpa-ONNX
- Java API wrapper (not native Kotlin)
- Large models (1+ GB) needed for good accuracy, but those are not mobile-friendly
- No built-in VAD -- would need separate implementation

---

### 3.3 Whisper.cpp

Whisper.cpp is a C++ implementation of OpenAI's Whisper model, optimized for edge devices.

#### 3.3.1 Available Models

| Model | Size (FP16) | Size (Q8) | Parameters | Speed (Android) |
|---|---|---|---|---|
| tiny | ~75 MB | ~42 MB | 39M | Very fast |
| base | ~142 MB | ~80 MB | 74M | Fast |
| small | ~466 MB | ~260 MB | 244M | Moderate |
| medium | ~1.5 GB | ~850 MB | 769M | Slow |

- Download: https://github.com/ggerganov/whisper.cpp/tree/master/models
- Multilingual models (non-.en) support Russian, English, and Italian
- License: MIT

#### 3.3.2 Android Integration

```kotlin
// Requires manual JNI integration:
// 1. Build whisper.cpp for Android (CMake + NDK)
// 2. Create custom JNI bridge (whisper_jni.cpp)
// 3. Load native library: System.loadLibrary("whisper")

// Example JNI functions needed:
// - whisper_init_from_file(path)
// - whisper_full(ctx, params, samples, n_samples)
// - whisper_full_n_segments(ctx)
// - whisper_full_get_segment_text(ctx, i)
// - whisper_free(ctx)
```

The project includes an Android example at `examples/android/` in the whisper.cpp repository.

#### 3.3.3 Pros and Cons

**Pros:**
- Very high accuracy (Whisper-based)
- Single multilingual model for all 3 languages
- Quantized models available (Q4, Q5, Q8) for smaller size
- Active community and maintenance
- MIT license

**Cons:**
- **Significant integration effort** -- requires custom JNI bridge
- **No pre-built Kotlin API** -- must write JNI wrapper from scratch
- **Separate native library** -- adds ~10-15 MB of `.so` files
- Higher memory usage than Dolphin CTC
- No built-in VAD (separate Silero VAD integration needed)
- Slower inference than Vosk for real-time use
- Build complexity (CMake, NDK)

---

## 4. Recommended Approach

### 4.1 Primary: Sherpa-ONNX Dolphin CTC Multilingual

**Rationale:**
1. **Zero new dependencies** -- the same AAR already used for TTS contains all ASR classes
2. **Single model** covers all three target languages
3. **Consistent architecture** -- follows the same patterns as existing TTS engine
4. **Reasonable size** -- ~170 MB download for the int8 multilingual model
5. **Good enough accuracy** for language learning exercises (short phrases, single sentences)
6. **Built-in VAD** via Silero VAD in the same library

### 4.2 Fallback: Sherpa-ONNX Whisper tiny/base

If Dolphin CTC accuracy is insufficient for a specific language, Whisper models can be used as a fallback. The configuration is trivially different (different `OfflineModelConfig` subclass) and uses the same `OfflineRecognizer` pipeline.

### 4.3 Not Recommended

- **Vosk:** Adds a second ASR library when Sherpa-ONNX already has ASR built in. Only justified if streaming recognition with very low latency becomes a hard requirement.
- **Whisper.cpp:** Too much integration effort for no benefit over Sherpa-ONNX's Whisper support. Sherpa-ONNX already provides Whisper models through its own API.

---

## 5. Integration Plan

### 5.1 New Files to Create

Following the existing TTS architecture pattern:

| File | Purpose |
|---|---|
| `data/AsrEngine.kt` | ASR engine (mirrors `TtsEngine.kt` structure) |
| `data/AsrModelRegistry.kt` | ASR model specs and download info (mirrors `TtsModelRegistry.kt`) |
| `data/AsrModelDownloader.kt` | Model download/verification (mirrors TTS download flow) |

### 5.2 Files to Modify

| File | Change |
|---|---|
| `ui/GrammarMateApp.kt` | Replace `RecognizerIntent` launchers with `AsrEngine` calls |
| `ui/TrainingViewModel.kt` | Add ASR result processing method |
| `ui/Models.kt` | Add ASR-related state if needed |

### 5.3 AsrEngine Design (Following TtsEngine Pattern)

```kotlin
class AsrEngine(private val context: Context) {
    private val _state = MutableStateFlow(AsrState.IDLE)
    val state: StateFlow<AsrState> = _state

    var activeLanguageId: String? = null
        private set

    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null

    suspend fun initialize(languageId: String = "en") {
        // Same pattern as TtsEngine.initialize():
        // 1. Check if already initialized for this language
        // 2. Look up model spec from AsrModelRegistry
        // 3. Build config from model files in context.filesDir/asr/
        // 4. Create OfflineRecognizer
        // 5. Also initialize Silero VAD
    }

    suspend fun transcribe(audioFilePath: String, languageId: String = "en"): String {
        // 1. Ensure initialized for languageId
        // 2. Read WAV file with WaveReader
        // 3. Create OfflineStream, acceptWaveform, decode
        // 4. Return result.text
    }

    suspend fun transcribeSamples(
        samples: FloatArray,
        sampleRate: Int = 16000,
        languageId: String = "en"
    ): String {
        // Same but from raw float samples (from AudioRecord)
    }

    fun release() {
        recognizer?.release()
        vad?.release()
        recognizer = null
        vad = null
        activeLanguageId = null
        _state.value = AsrState.IDLE
    }
}

enum class AsrState { IDLE, INITIALIZING, READY, RECOGNIZING, ERROR }
```

### 5.4 AsrModelRegistry Design

```kotlin
enum class AsrModelType { DOLPHIN_CTC, WHISPER }

data class AsrModelSpec(
    val modelId: String,
    val displayName: String,
    val modelType: AsrModelType,
    val downloadUrl: String,
    val archivePrefix: String,
    val modelDirName: String,
    val fallbackDownloadSize: Long,
    val minRequiredBytes: Long,
    val requiredFiles: List<String>,
    val requiredDirs: List<String> = emptyList(),
)

object AsrModelRegistry {
    val models: Map<String, AsrModelSpec> = mapOf(
        "dolphin-multilingual" to AsrModelSpec(
            modelId = "dolphin-multilingual",
            displayName = "Dolphin CTC Multilingual (RU/EN/IT)",
            modelType = AsrModelType.DOLPHIN_CTC,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02.tar.bz2",
            archivePrefix = "sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02/",
            modelDirName = "dolphin-base-ctc-multi-lang-int8",
            fallbackDownloadSize = 170L * 1024 * 1024,
            minRequiredBytes = 350L * 1024 * 1024,
            requiredFiles = listOf("model.onnx", "tokens.txt")
        ),
        "whisper-tiny" to AsrModelSpec(
            modelId = "whisper-tiny",
            displayName = "Whisper Tiny Multilingual",
            modelType = AsrModelType.WHISPER,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2",
            archivePrefix = "sherpa-onnx-whisper-tiny/",
            modelDirName = "whisper-tiny",
            fallbackDownloadSize = 75L * 1024 * 1024,
            minRequiredBytes = 200L * 1024 * 1024,
            requiredFiles = listOf("encoder.onnx", "decoder.onnx", "tokens.txt")
        )
    )

    // VAD model spec (separate, always needed)
    val vadModel = AsrModelSpec(
        modelId = "silero-vad",
        displayName = "Silero VAD",
        modelType = AsrModelType.DOLPHIN_CTC, // not really applicable, but needed for struct
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        archivePrefix = "",
        modelDirName = "vad",
        fallbackDownloadSize = 2L * 1024 * 1024,
        minRequiredBytes = 5L * 1024 * 1024,
        requiredFiles = listOf("silero_vad.onnx")
    )

    fun defaultModel(): AsrModelSpec = models["dolphin-multilingual"]!!
    fun specFor(modelId: String): AsrModelSpec? = models[modelId]
}
```

### 5.5 Integration Points in GrammarMateApp.kt

The two voice input launchers currently using `RecognizerIntent` need to be replaced with:

1. **Microphone recording** -- Use `AudioRecord` to capture 16kHz mono PCM
2. **VAD-based endpointing** -- Feed samples to `Vad`, detect speech segments
3. **ASR transcription** -- Pass speech segments to `AsrEngine.transcribeSamples()`
4. **Result handling** -- Same as current `RecognizerIntent.EXTRA_RESULTS` processing

The existing `TrainingViewModel` already handles voice results. The change is purely in how the audio is captured and transcribed -- the result processing pipeline remains the same.

### 5.6 Audio Recording Flow

```
User taps microphone
  -> AudioRecord starts (16kHz, mono, PCM_FLOAT)
  -> Samples fed to Silero VAD in real-time
  -> VAD detects speech start
  -> Accumulate speech samples
  -> VAD detects silence (end of speech)
  -> Stop recording
  -> Pass speech segment to AsrEngine.transcribeSamples()
  -> Return recognized text to TrainingViewModel
  -> Process as voice answer (same as current flow)
```

### 5.7 Model Download Flow

Follow the exact same download-on-demand pattern as TTS:

1. On first use, check if ASR model exists in `context.filesDir/asr/<modelDirName>/`
2. If not, show download dialog with size estimate
3. Download tar.bz2 from GitHub releases
4. Extract to `context.filesDir/asr/`
5. Verify required files exist
6. Initialize `OfflineRecognizer`

The existing download infrastructure (from TTS model download) can be reused with minor modifications for the ASR model path.

---

## 6. Storage and Performance Considerations

### 6.1 Disk Space

| Component | Size |
|---|---|
| Dolphin CTC int8 model | ~350 MB on disk |
| Silero VAD model | ~2 MB on disk |
| **Total ASR storage** | **~350 MB** |

Combined with existing TTS models:
| Component | Size |
|---|---|
| English TTS (Kokoro) | ~700 MB |
| Italian TTS (VITS Piper) | ~150 MB |
| ASR (Dolphin CTC) | ~350 MB |
| **Total model storage** | **~1.2 GB** |

This is significant for a mobile app. Mitigations:
- Download-on-demand (only download ASR model when user first uses voice input)
- Allow user to delete ASR model when not needed
- Consider offering Whisper tiny (~200 MB) as a smaller alternative

### 6.2 Runtime Memory

- `OfflineRecognizer` with Dolphin CTC: ~300-500 MB RSS
- `Vad` (Silero): ~10 MB RSS
- Total additional memory during recognition: ~300-500 MB

This is within acceptable range for modern Android devices (minSdk 24 = Android 7.0, typically 2+ GB RAM). However, loading both TTS and ASR engines simultaneously may be tight on low-end devices. Consider:
- Initialize ASR only when voice input mode is activated
- Release ASR engine when switching to other input modes
- Do not hold both TTS and ASR loaded if memory pressure is detected

### 6.3 Latency

Expected recognition latency for a typical language-learning phrase (3-8 words):

| Model | Latency |
|---|---|
| Dolphin CTC int8 | ~200-500ms |
| Whisper tiny | ~300-800ms |
| Whisper base | ~500ms-2s |
| Whisper small | ~1-4s |

For voice input in a language learning app, sub-second latency is preferred. Dolphin CTC and Whisper tiny are the best candidates.

---

## 7. Testing Strategy

### 7.1 Unit Tests (data/ layer)

- `AsrModelRegistryTest` -- verify model specs, URL validity, required files
- `AsrEngineConfigTest` -- verify config building for each model type (Dolphin, Whisper)
- These tests do not require actual model files or native library

### 7.2 Integration Tests

- Record test audio samples for each language (RU/EN/IT)
- Verify transcription accuracy for known phrases
- Test model download and extraction flow
- Test VAD with various audio samples (speech, silence, noise)

### 7.3 Manual Testing Checklist

- [ ] Voice input works offline (airplane mode)
- [ ] Recognition accuracy for short Russian phrases
- [ ] Recognition accuracy for short English phrases
- [ ] Recognition accuracy for short Italian phrases
- [ ] Language switching works without re-downloading model
- [ ] Model download cancellation and retry
- [ ] Memory usage with both TTS and ASR loaded
- [ ] Voice input with background noise
- [ ] Graceful fallback when model not downloaded

---

## 8. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Dolphin CTC accuracy insufficient for short phrases | High | Fallback to Whisper models; allow user to choose model |
| Model download size too large for users | Medium | Download-on-demand; show size before download; offer tiny model |
 | Runtime memory pressure (TTS + ASR) | Medium | Lazy-load ASR; release when not in use; warn on low-memory devices |
| No streaming mode (OfflineRecognizer only) | Low | For language learning, short phrases work fine with offline recognition |
| Dolphin language detection confusion (RU vs IT) | Medium | Set language hint when available; use Whisper fallback with explicit language |
| AAR version incompatible with future model formats | Low | Pin to tested model versions; update AAR with Gradle |

---

## 9. References

- Sherpa-ONNX documentation: https://k2-fsa.github.io/sherpa/onnx/
- Sherpa-ONNX GitHub: https://github.com/k2-fsa/sherpa-onnx
- Sherpa-ONNX ASR models: https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models
- Dolphin CTC model: https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02.tar.bz2
- Whisper models via Sherpa-ONNX: https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models
- Vosk models: https://alphacephei.com/vosk/models
- Vosk Android SDK: https://github.com/alphacep/vosk-api
- Whisper.cpp: https://github.com/ggerganov/whisper.cpp
- Silero VAD: https://github.com/snakers4/silero-vad
