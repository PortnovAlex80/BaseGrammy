# Sherpa-ONNX TTS Integration Research

**Date:** 2025-04-25
**Context:** GrammarMate Android app (Kotlin 1.9.22, minSdk 24, Jetpack Compose)
**Library:** sherpa-onnx v1.12.40 (latest stable)
**Model:** Kokoro TTS (offline, ONNX-based)

---

## 1. Gradle Dependency

**No Maven Central artifact exists.** Sherpa-ONNX is NOT published to Maven Central, JCenter, or JitPack.

### Option A: Pre-built AAR from GitHub Releases (recommended)

Download the AAR from GitHub releases and place it in `app/libs/`:

```groovy
// app/build.gradle
dependencies {
    implementation files('libs/sherpa-onnx-1.12.40.aar')
}
```

Download URL:
- **Standard:** `https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.40/sherpa-onnx-1.12.40.aar` (53.9 MB)
- **Static-linked ONNX Runtime:** `https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.40/sherpa-onnx-static-link-onnxruntime-1.12.40.aar` (36.4 MB, preferred -- no external ONNX libs needed)

Check latest version at: https://github.com/k2-fsa/sherpa-onnx/releases

### Option B: Manual JNI integration (what the official example does)

1. Copy the Kotlin API source files from `sherpa-onnx/kotlin-api/Tts.kt` into your project
2. Build native `.so` libraries from source for each ABI (arm64-v8a, armeabi-v7a, x86_64, x86)
3. Place them under `app/src/main/jniLibs/<abi>/`
4. The library auto-loads via `System.loadLibrary("sherpa-onnx-jni")`

**Option A is strongly recommended** -- building from source requires Android NDK and CMake.

### Kotlin API source

The AAR bundles the Kotlin API, but the source is at:
`https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/Tts.kt`

The package is `com.k2fsa.sherpa.onnx`.

---

## 2. Kotlin API Code Examples

### 2a. Kokoro model configuration (English-only)

For Kokoro English v0_19 (11 speakers, English only):

```kotlin
val modelDir = "kokoro-en-v0_19"   // path relative to assets

val config = OfflineTtsConfig(
    model = OfflineTtsModelConfig(
        kokoro = OfflineTtsKokoroModelConfig(
            model = "$modelDir/model.onnx",
            voices = "$modelDir/voices.bin",
            tokens = "$modelDir/tokens.txt",
            dataDir = "$modelDir/espeak-ng-data",
        ),
        numThreads = 4,
        debug = false,
        provider = "cpu",
    ),
)
```

### 2b. Kokoro model configuration (multi-language)

For Kokoro multi-lang v1_0 or v1_1 (53-103 speakers, English + Chinese + more):

```kotlin
val modelDir = "kokoro-multi-lang-v1_0"

val config = OfflineTtsConfig(
    model = OfflineTtsModelConfig(
        kokoro = OfflineTtsKokoroModelConfig(
            model = "$modelDir/model.onnx",
            voices = "$modelDir/voices.bin",
            tokens = "$modelDir/tokens.txt",
            dataDir = "$modelDir/espeak-ng-data",
            lexicon = "$modelDir/lexicon-us-en.txt,$modelDir/lexicon-zh.txt",
        ),
        numThreads = 4,
        debug = false,
        provider = "cpu",
    ),
    ruleFsts = "$modelDir/phone-zh.fst,$modelDir/date-zh.fst,$modelDir/number-zh.fst",
)
```

### 2c. Initialization

```kotlin
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig

// Initialize from assets (model files must be in app/src/main/assets/)
val tts = OfflineTts(assetManager = context.assets, config = config)

// Get audio properties
val sampleRate = tts.sampleRate()      // 24000 for Kokoro
val numSpeakers = tts.numSpeakers()    // 11 for en-v0_19, 53+ for multi-lang
```

### 2d. Synthesize (blocking, full audio)

```kotlin
val audio = tts.generate(
    text = "Hello, how are you?",
    sid = 0,       // speaker ID (0 = "af" for Kokoro English)
    speed = 1.0f,  // speech speed multiplier
)

// audio.samples: FloatArray -- PCM float samples in [-1.0, 1.0]
// audio.sampleRate: Int -- 24000
```

### 2e. Synthesize with streaming callback (low-latency playback)

```kotlin
// Set up AudioTrack
val attr = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

val format = AudioFormat.Builder()
    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
    .setSampleRate(tts.sampleRate())
    .build()

val bufLength = AudioTrack.getMinBufferSize(
    tts.sampleRate(),
    AudioFormat.CHANNEL_OUT_MONO,
    AudioFormat.ENCODING_PCM_FLOAT,
)

val track = AudioTrack(attr, format, bufLength, AudioTrack.MODE_STREAM,
    AudioManager.AUDIO_SESSION_ID_GENERATE)
track.play()

// Generate with callback -- audio plays as it's generated
val audio = tts.generateWithConfigAndCallback(
    text = "Hello, how are you?",
    config = GenerationConfig(sid = 0, speed = 1.0f),
    callback = { samples: FloatArray ->
        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        1  // return 1 to continue, 0 to stop
    },
)

track.stop()
track.release()
```

### 2f. Save to WAV file

```kotlin
val filename = context.filesDir.absolutePath + "/tts_output.wav"
audio.save(filename)  // returns Boolean
```

### 2g. Cleanup

```kotlin
tts.free()    // release native resources
// or
tts.release() // same as free()
```

### 2h. Convenience helper function

The official example includes a `getOfflineTtsConfig()` helper that simplifies setup:

```kotlin
val config = getOfflineTtsConfig(
    modelDir = "kokoro-en-v0_19",
    modelName = "model.onnx",
    acousticModelName = "",  // Matcha only
    vocoder = "",             // Matcha only
    voices = "voices.bin",   // Kokoro
    lexicon = "",
    dataDir = "kokoro-en-v0_19/espeak-ng-data",
    dictDir = "",
    ruleFsts = "",
    ruleFars = "",
    numThreads = 4,
    isKitten = false,
)
```

### Key API classes (from Tts.kt source)

| Class | Purpose |
|-------|---------|
| `OfflineTtsConfig` | Top-level config: model config + rule FSTs + silence scale |
| `OfflineTtsModelConfig` | Model-specific config (kokoro, vits, matcha, etc.) + threads + provider |
| `OfflineTtsKokoroModelConfig` | Kokoro model paths: model, voices, tokens, dataDir, lexicon |
| `GenerationConfig` | Per-request params: sid (speaker), speed, silenceScale |
| `GeneratedAudio` | Output: samples (FloatArray) + sampleRate + save() |
| `OfflineTts` | Main class: init, generate, generateWithCallback, sampleRate, numSpeakers |

---

## 3. Model Files

### Kokoro English v0_19 (recommended for English-only)

| File | Size | Description |
|------|------|-------------|
| `model.onnx` | 330 MB | Main TTS model |
| `tokens.txt` | 1.1 KB | Token vocabulary |
| `voices.bin` | 5.5 MB | Speaker embeddings (11 speakers) |
| `espeak-ng-data/` | ~10 MB | Phonemization data directory |

**Total: ~346 MB**

**Download:** `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2`

**Speaker IDs:** af(0), af_bella(1), af_nicole(2), af_sarah(3), af_sky(4), am_adam(5), am_michael(6), bf_emma(7), bf_isabella(8), bm_george(9), bm_lewis(10)

### Kokoro multi-lang v1_0 (English + Chinese + more)

| File | Size | Description |
|------|------|-------------|
| `model.onnx` | 310 MB | Main TTS model |
| `tokens.txt` | 687 B | Token vocabulary |
| `voices.bin` | 26 MB | Speaker embeddings (53 speakers) |
| `espeak-ng-data/` | ~10 MB | Phonemization data |
| `lexicon-us-en.txt` | -- | English lexicon |
| `lexicon-zh.txt` | -- | Chinese lexicon |
| `phone-zh.fst` | -- | Chinese phone FST |
| `date-zh.fst` | -- | Chinese date FST |
| `number-zh.fst` | -- | Chinese number FST |

**Total: ~350+ MB**

**Download:** `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2`

### Kokoro multi-lang v1_1 (latest, 103 speakers)

Same structure as v1_0 with more speakers. Also available as int8 quantized (~50% smaller model).

**Download:**
- Full: `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_1.tar.bz2`
- Int8: `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2`

### Model placement in Android project

```
app/src/main/assets/
  kokoro-en-v0_19/
    model.onnx
    tokens.txt
    voices.bin
    espeak-ng-data/
      (phonemization data files)
```

**Important:** The `espeak-ng-data/` directory must be copied from assets to device external storage before use. The official example does this with a helper that recursively copies the assets directory:

```kotlin
private fun copyDataDir(context: Context, srcDir: String, destDir: String) {
    val assetManager = context.assets
    // Recursively copy srcDir from assets to destDir on filesystem
    // ...
}
```

When using asset-based initialization (`OfflineTts(assetManager, config)`), paths like `dataDir = "$modelDir/espeak-ng-data"` work directly from assets without copying.

---

## 4. Gotchas and Limitations

### Asset size impact
- Kokoro English model is ~346 MB. This adds directly to APK size if bundled in assets.
- **Mitigation:** Use app bundles (AAB) with asset packs, or download models on first use.
- Int8 quantized models are ~50% smaller with minimal quality loss.

### No Maven publication
- The library is distributed as AAR from GitHub releases only. No Maven Central/JitPack.
- Must manually download and place in `libs/` directory.
- Version updates require manual AAR replacement.

### espeak-ng-data handling
- The `dataDir` parameter in Kokoro config points to the espeak-ng-data directory.
- When using `OfflineTts(assetManager, config)`, paths are resolved from assets.
- When using `OfflineTts(config)` (no assetManager), paths must be absolute filesystem paths, and espeak-ng-data must be pre-copied to storage.

### Threading
- TTS synthesis is a **blocking CPU-intensive operation**. Never call on the main thread.
- The official example uses 4 threads for Kokoro models (`numThreads = 4`).
- Use Kotlin coroutines with `Dispatchers.Default` or a dedicated thread pool.

### Audio output format
- Output is PCM float samples in [-1.0, 1.0], mono, 24000 Hz sample rate.
- For AudioTrack playback, use `ENCODING_PCM_FLOAT` (requires API 21+, so minSdk 24 is fine).
- For saving to file, use `GeneratedAudio.save()` which writes WAV format.

### Memory usage
- The ONNX runtime allocates significant memory at initialization.
- The `OfflineTts` instance should be created once and reused, not created per request.
- Call `tts.free()` / `tts.release()` when done to release native memory.

### Re-initialization support
- After calling `free()`, you can call `allocate(assetManager)` to reinitialize without creating a new object.

### Speaker ID validation
- Using an invalid `sid` (speaker ID) will crash or produce silence.
- Always check `tts.numSpeakers()` and ensure `sid` is within range.

### Text length limits
- The `maxNumSentences` parameter in `OfflineTtsConfig` (default: 1) controls sentence batching.
- For long texts, the library splits by sentences internally.
- Very long texts may cause memory issues; consider chunking.

### Streaming callback behavior
- The callback receives chunks of audio as they are generated.
- Return `1` from the callback to continue generation, `0` to stop.
- The callback is called from a native thread -- do not update UI directly.

### ABI considerations
- The standard AAR includes native libraries for: arm64-v8a, armeabi-v7a, x86_64, x86.
- This increases app size. Use ABI splits in Gradle to reduce:
  ```groovy
  android {
      splits {
          abi {
              enable true
              reset()
              include 'arm64-v8a', 'armeabi-v7a'
              universalApk false
          }
      }
  }
  ```

### Int8 quantized models
- `kokoro-int8-multi-lang-v1_1` uses int8 quantization for smaller model size and faster inference.
- Quality is slightly lower but often acceptable.
- Recommended for mobile deployment due to reduced memory and compute requirements.

### Official example reference
- The canonical Android TTS example is at:
  `https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxTts`
- Kotlin API source:
  `https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/Tts.kt`
- Pretrained model documentation:
  `https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html`

### Performance expectations
- Kokoro on Raspberry Pi 4: RTF 0.15-0.37 (2.7-6.6x real-time depending on threads).
- On modern Android phones (ARM Cortex-A7x class): expect significantly faster, likely 10-20x real-time.
- First inference may be slower due to ONNX runtime warm-up.
