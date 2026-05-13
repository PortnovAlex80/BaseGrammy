# 5. Audio System (TTS & ASR) — Specification

## 5.1 TTS (Text-to-Speech)

### 5.1.1 Architecture

The TTS subsystem provides offline text-to-speech synthesis using the **Sherpa-ONNX** library (AAR `sherpa-onnx-static-link-onnxruntime-1.12.40.aar` in `app/libs/`). The architecture follows the same data-layer pattern used throughout the app:

```
data/TtsModelRegistry.kt   -- pure-data model spec registry (language -> URL, files, type)
data/TtsModelManager.kt    -- file I/O, network download, progress Flow
data/TtsEngine.kt          -- native OfflineTts wrapper, AudioTrack playback, lifecycle
ui/TrainingViewModel.kt    -- holds TtsEngine + TtsModelManager instances, exposes speak()/stop()
ui/GrammarMateApp.kt       -- renders speaker icon, download dialogs, observes TtsState
```

The Sherpa-ONNX AAR is statically linked against ONNX Runtime, so no external ONNX libraries are needed. All inference runs on CPU (`provider = "cpu"`, `numThreads = 4`). Models are downloaded to internal storage (`context.filesDir/tts/`) on first use; they are not bundled in the APK.

The TTS engine uses a **single `OfflineTts` instance at a time**, reinitialized when the active language changes. This keeps memory usage to one model's worth of native ONNX runtime allocations.

### 5.1.2 Model Registry

**File:** `data/TtsModelRegistry.kt`

The registry maps language IDs to their TTS model specifications. It is a pure-data object with no Android dependencies.

#### Data Structures

```kotlin
enum class TtsModelType { KOKORO, VITS_PIPER }

data class TtsModelSpec(
    val languageId: String,          // ISO 639-1 code: "en", "it"
    val displayName: String,          // "English", "Italian"
    val modelType: TtsModelType,      // Architecture: KOKORO or VITS_PIPER
    val downloadUrl: String,          // GitHub releases tar.bz2 URL
    val archivePrefix: String,        // Top-level dir to strip from tar entries
    val modelDirName: String,         // Subdirectory under filesDir/tts/
    val fallbackDownloadSize: Long,   // Bytes, used when Content-Length is unknown
    val minRequiredBytes: Long,       // Min free storage needed for download + extraction
    val requiredFiles: List<String>,  // Relative paths that must exist after extraction
    val requiredDirs: List<String>,   // Relative directory paths that must exist
    val modelFileName: String = "model.onnx"  // Primary ONNX model file name
)
```

#### Registered Models

| Language | ID | Architecture | Model | Download Size | Disk Size | Required Files | Required Dirs |
|----------|-----|-------------|-------|---------------|-----------|----------------|---------------|
| English | `en` | KOKORO | kokoro-en-v0_19 | ~350 MB | ~700 MB | `model.onnx`, `tokens.txt`, `voices.bin` | `espeak-ng-data` |
| Italian | `it` | VITS_PIPER | vits-piper-it_IT-paola-medium | ~65 MB | ~150 MB | `it_IT-paola-medium.onnx`, `tokens.txt` | `espeak-ng-data` |

#### API

```kotlin
object TtsModelRegistry {
    val models: Map<String, TtsModelSpec>  // All registered models
    fun specFor(languageId: String): TtsModelSpec?  // Lookup by language ID
}
```

Adding a new language requires a single entry in the `models` map.

### 5.1.3 Model Manager

**File:** `data/TtsModelManager.kt`

Manages downloading, storing, and verifying TTS models on device internal storage. Follows the same download-on-demand pattern used for all large assets.

#### Storage Layout

```
context.filesDir/tts/
  kokoro-en-v0_19/             -- English (Kokoro)
    model.onnx                  (~330 MB)
    tokens.txt                  (~1.1 KB)
    voices.bin                  (~5.5 MB)
    espeak-ng-data/             (~10 MB)
      ...
  vits-piper-it_IT-paola-medium/  -- Italian (VITS Piper)
    it_IT-paola-medium.onnx     (~60 MB)
    tokens.txt                  (~2 KB)
    espeak-ng-data/             (~5 MB)
      ...
```

All model data is in internal storage, cleaned up on app uninstall.

#### Download State

```kotlin
sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val percent: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Extracting(val percent: Int) : DownloadState()
    object Done : DownloadState()
    data class Error(val message: String) : DownloadState()
}
```

#### Public API

| Method | Description |
|--------|-------------|
| `modelDir(languageId): File` | Absolute path to model directory |
| `isModelReady(languageId): Boolean` | True if all required files are present and non-empty, and all required dirs exist |
| `getDownloadedSize(languageId): Long` | Total size of model files in bytes (recursive walk) |
| `deleteModel(languageId)` | Recursively delete model dir and cached archive |
| `getAvailableStorageBytes(): Long` | Free space on internal storage |
| `isNetworkMetered(): Boolean` | Whether current network is metered (cellular) |
| `download(languageId): Flow<DownloadState>` | Download and extract model, emits progress |
| `downloadMultiple(languageIds): Flow<Map<String, DownloadState>>` | Sequential multi-language download |

#### Download Flow

1. **Check if ready** -- if model files already present, emit `Done` immediately
2. **Storage check** -- compare available bytes against `spec.minRequiredBytes`
3. **Download with retries** -- up to 3 attempts, 60s connect / 120s read timeouts
4. **Redirect handling** -- manual HTTP redirect following (up to 5 hops) for GitHub release URLs
5. **Progress reporting** -- percent-based via `Downloading` state; uses `fallbackDownloadSize` when `Content-Length` is unknown
6. **Cancellation** -- checks `currentCoroutineContext().isActive` each read iteration
7. **Extract** -- `tar.bz2` extraction via Apache Commons Compress (`TarArchiveInputStream` + `BZip2CompressorInputStream`), stripping `archivePrefix` from entry paths
8. **Cleanup** -- delete archive file after successful extraction
9. **Error handling** -- on extraction failure, delete output dir and emit `Error`

`downloadMultiple` downloads languages sequentially (not in parallel) to avoid doubling disk I/O and network usage. It aggregates per-language states into a single map emitted on each change.

#### Dependencies

- `org.apache.commons:commons-compress:1.26.1` (~500 KB) for tar.bz2 extraction

### 5.1.4 TTS Engine

**File:** `data/TtsEngine.kt`

Wraps the Sherpa-ONNX `OfflineTts` native object, manages audio playback via `AudioTrack`, and provides a state machine for the TTS lifecycle.

#### State Machine

```
IDLE ──initialize()──> INITIALIZING ──success──> READY
                                                         │
                                              speak() ───┤
                                                         v
                                                      SPEAKING
                                                         │
                                          stop() / done ──┤
                                                         v
                                                       READY
                                                         │
                                             release() ──┤
                                                         v
                                                       IDLE

Any state ──error──> ERROR
ERROR ──initialize()──> INITIALIZING (recovery path)
```

```kotlin
enum class TtsState {
    IDLE,           // No TTS loaded, no playback
    INITIALIZING,   // OfflineTts is being created (1-3 seconds)
    READY,          // Engine loaded, ready to synthesize
    SPEAKING,       // Audio is playing
    ERROR           // Initialization or playback failed
}
```

#### Public API

| Method | Description |
|--------|-------------|
| `state: StateFlow<TtsState>` | Observable engine state |
| `isReady: Boolean` | True when state == READY |
| `activeLanguageId: String?` | Currently loaded language (read-only) |
| `initialize(languageId)` | Load model for language; no-op if already loaded for same language |
| `speak(text, languageId, speakerId, speed)` | Synthesize and play audio |
| `stop()` | Stop current playback immediately |
| `release()` | Free all native resources, reset to IDLE |

#### Initialization Flow

1. **Fast path**: if already READY for the requested language, return immediately
2. **Language switch**: if a different language is loaded, call `release()` first and await cleanup
3. **Speaking guard**: if currently SPEAKING, stop and await the speak job to finish
4. **Concurrency guard**: if INITIALIZING, spin-wait until complete
5. **CAS transition**: atomically transition IDLE or ERROR to INITIALIZING
6. **Registry lookup**: get `TtsModelSpec` for the language
7. **File validation**: verify all `requiredFiles` exist and are non-empty before loading into native code
8. **Config building**: `buildConfig(spec, modelDir)` dispatches on `TtsModelType`:
   - **KOKORO**: builds `OfflineTtsKokoroModelConfig` with `model`, `voices`, `tokens`, `dataDir`
   - **VITS_PIPER**: builds `OfflineTtsVitsModelConfig` with `model`, `tokens`, `dataDir`, plus noise/length scale parameters (`noiseScale=0.667f`, `noiseScaleW=0.8f`, `lengthScale=1.0f`)
9. **Native instantiation**: `OfflineTts(config)` loads the ONNX model into native memory
10. **Result**: on success sets READY; on failure sets ERROR

#### Config Building (Dual Architecture)

```kotlin
private fun buildConfig(spec: TtsModelSpec, modelDir: File): OfflineTtsConfig {
    val modelConfig = when (spec.modelType) {
        TtsModelType.KOKORO -> OfflineTtsModelConfig(
            kokoro = OfflineTtsKokoroModelConfig(
                model = File(modelDir, "model.onnx").absolutePath,
                voices = File(modelDir, "voices.bin").absolutePath,
                tokens = File(modelDir, "tokens.txt").absolutePath,
                dataDir = File(modelDir, "espeak-ng-data").absolutePath,
            ),
            numThreads = 4, debug = false, provider = "cpu",
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
            numThreads = 4, debug = false, provider = "cpu",
        )
    }
    return OfflineTtsConfig(model = modelConfig)
}
```

Key details:
- Kokoro requires `voices.bin` (multi-speaker voice embeddings); VITS/Piper does not
- Piper uses `espeak-ng-data` for phonemization; `lexicon` and `dictDir` are empty strings
- All paths are absolute filesystem paths (not asset paths) since models live in internal storage

#### Synthesis and Playback Flow

1. **Pre-check**: blank text is a no-op; speed is clamped to `[0.3, 3.0]`
2. **Auto-init**: if engine not ready for requested language, calls `initialize(languageId)` first
3. **Cancel previous**: cancels and joins any running speak job
4. **Generation counter**: increments an `AtomicInteger` to track which generation owns the current AudioTrack
5. **Audio focus**: requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` via `AudioManager`
6. **AudioTrack setup**: creates a streaming `AudioTrack` (MONO, PCM_FLOAT, model's sample rate, `MODE_STREAM`)
7. **Streaming synthesis**: calls `tts.generateWithConfigAndCallback(text, config, callback)` which invokes the callback with chunks of float samples as they are generated
8. **Callback writes**: each chunk is written to AudioTrack with `WRITE_BLOCKING`; if `isStopped` is set, callback returns 0 to abort generation
9. **Cleanup**: in `finally` block, stops and releases AudioTrack, abandons audio focus, transitions state back to READY (only if generation counter matches)

#### Speaker IDs

Kokoro English v0_19 speakers:
- `0` = `af` (default, American female)
- `5` = `am_adam` (American male)

Italian VITS/Piper uses a single speaker (Paola), so `speakerId` is effectively ignored.

#### Audio Focus Management

- **Request**: `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` before playback
- **Abandon**: after playback completes or is stopped
- **API level handling**: uses `AudioFocusRequest` on API 26+, deprecated `requestAudioFocus` on older versions
- **Error tolerance**: focus request/release failures are logged but do not block playback

#### Resource Cleanup

- `stop()`: sets `isStopped` flag, cancels speak job, stops AudioTrack
- `release()`: stops playback, cancels speak job, frees native `OfflineTts` on a coroutine (avoids blocking), resets to IDLE

### 5.1.5 Offline Behavior

- Models are **not bundled** in the APK. They are downloaded on first use from GitHub releases.
- Once downloaded, synthesis is fully offline. No internet connection required.
- The download-on-demand approach avoids inflating APK size (~36 MB from the AAR alone).
- Models are stored in `context.filesDir` (internal storage), which is:
  - Private to the app (not accessible to other apps)
  - Cleaned up automatically on app uninstall
  - Not counted against the user's visible storage in some Android versions

### 5.1.6 Language Support

| Language | Model Architecture | Model Name | Voice | Size (download) |
|----------|-------------------|------------|-------|-----------------|
| English (`en`) | Kokoro v0.19 | kokoro-en-v0_19 | `af` (American female, default) | ~350 MB |
| Italian (`it`) | VITS Piper | vits-piper-it_IT-paola-medium | Paola (female) | ~65 MB |

Languages with no registered TTS model gracefully degrade: `TtsModelRegistry.specFor()` returns null, `TtsEngine.initialize()` sets ERROR state, and the speaker button shows an error icon.

### 5.1.7 ViewModel Integration

`TrainingViewModel` owns both `TtsEngine` and `TtsModelManager` instances:

- **State propagation**: `ttsEngine.state` is collected into `TrainingUiState.ttsState`
- **Model check**: `checkTtsModel()` called on init and language switch, updates `ttsModelReady`
- **Background download**: `startBackgroundTtsDownload()` downloads missing language models on ViewModel init
- **Auto-initialization after download**: When a background or user-initiated download completes, `AudioCoordinator` automatically calls `ttsEngine.initialize()` if the engine is in ERROR or IDLE state. This ensures the TTS speaker icon updates from error/loading to speaker icon without requiring a user action or screen re-entry.
- **Speak**: `onTtsSpeak(text)` initializes engine for current language if needed, then calls `speak()`
- **Download trigger**: `startTtsDownload()` for user-initiated download with dialog progress
- **Language switch**: `selectLanguage()` propagates language to `TtsModelManager.currentLanguageId` and rechecks model readiness
- **Lifecycle**: `onCleared()` cancels background downloads and calls `ttsEngine.release()`

#### TrainingUiState TTS Fields

```kotlin
val ttsState: TtsState = TtsState.IDLE,
val ttsDownloadState: DownloadState = DownloadState.Idle,
val ttsModelReady: Boolean = false,
val ttsMeteredNetwork: Boolean = false,
val bgTtsDownloading: Boolean = false,
val bgTtsDownloadStates: Map<String, DownloadState> = emptyMap()
```

### 5.1.8 UI Integration

#### Speaker Icon Placement

Three locations in the UI have a speaker icon button:

| Location | Trigger | Text Spoken |
|----------|---------|-------------|
| CardPrompt (Russian prompt card) | User taps speaker | First accepted answer (target language) |
| ResultBlock (answer display) | User taps speaker | Answer text (target language) |
| Vocab flashcard (after reveal) | User taps speaker | Target language text |

Only target-language text is ever spoken. Russian prompts are never synthesized.

#### TtsSpeakerButton States

| TTS State | Icon | Behavior |
|-----------|------|----------|
| IDLE / READY | Volume Up | Tap triggers speak |
| SPEAKING | Stop Circle (red) | Tap stops playback |
| INITIALIZING | Circular Progress Indicator | Loading spinner |
| ERROR | Report Problem (red) | Error state, auto-recovers when download completes |

**Icon auto-recovery**: When TTS models finish downloading in the background, the AudioCoordinator automatically re-initializes the TTS engine. The engine transitions from ERROR -> INITIALIZING -> READY, which propagates through `ttsEngine.state` -> `startTtsStateCollection()` -> `uiState.audio.ttsState` -> composable recomposition. The speaker icon updates from red error to VolumeUp within 2 seconds of download completion, without requiring user action or screen re-entry.

**TTS error handling**: TTS `speak()` calls are wrapped in try-catch at both engine level and caller level. If models are not loaded, `speak()` silently skips — no crash. This is expected behavior when a user taps the TTS button before models finish downloading.

**KNOWN ISSUE — Samsung tablet crash during TTS model loading/playback**: On Samsung Galaxy Tab devices, the app crashes when TTS models are being loaded or when the user taps the speaker icon. This crash did NOT occur in older app versions — likely a regression from the TTS engine re-initialization logic or Sherpa-ONNX native layer interaction. Requires investigation: (1) check if crash is in `TtsEngine.initialize()` native call, (2) verify Sherpa-ONNX AAR compatibility with Samsung's AudioTrack implementation, (3) test on Samsung Tab S8/S9 specifically. **Tech debt — priority after current refactoring.**

#### Download Dialog

When the user taps the speaker icon for a language whose model is not downloaded, a dialog appears:
- **Idle**: shows estimated download size and language name, Download/Cancel buttons
- **Downloading**: shows percentage and progress bar, no Cancel during active download
- **Extracting**: shows extraction percentage and progress bar
- **Done**: "Download complete! Initializing..."
- **Error**: shows error message, OK button

#### Background Download Indicator

A thin bar at the top of the screen shows background download progress:
```
EN 45% | IT pending
```
Disappears when all downloads complete.

---

## 5.2 ASR (Automatic Speech Recognition)

### 5.2.1 Architecture

The ASR subsystem provides offline speech-to-text recognition using the **Sherpa-ONNX** library (same AAR as TTS). It uses a **Whisper Small multilingual model** with **Silero VAD** (Voice Activity Detection) for microphone-based endpointing.

```
data/AsrModelRegistry.kt   -- ASR + VAD model specs and download info
data/AsrModelManager.kt    -- Model download/verification (mirrors TTS download flow)
data/AsrEngine.kt          -- ASR engine (OfflineRecognizer + VAD + AudioRecord)
ui/TrainingViewModel.kt    -- holds AsrEngine, processes recognition results
ui/GrammarMateApp.kt       -- triggers recording, shows ASR state
```

The system replaces Android's built-in `RecognizerIntent` (which required internet and Google app) with fully offline recognition.

### 5.2.2 Model Registry

**File:** `data/AsrModelRegistry.kt`

Contains specifications for the ASR model and the separate VAD model.

#### Data Structures

```kotlin
enum class AsrModelType { WHISPER }

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
)
```

#### Registered Models

**ASR Model (Whisper Small Multilingual):**

| Field | Value |
|-------|-------|
| modelId | `whisper-small-multilingual` |
| Model files | `small-encoder.int8.onnx`, `small-decoder.int8.onnx`, `small-tokens.txt` |
| Download URL | `sherpa-onnx-whisper-small.tar.bz2` from GitHub releases |
| Download size | ~375 MB |
| Min required storage | 800 MB |
| Directory | `asr/whisper-small/` |
| Languages | EN, IT, RU (and 90+ others) |

**VAD Model (Silero VAD):**

| Field | Value |
|-------|-------|
| modelId | `silero-vad` |
| Model file | `silero_vad.onnx` |
| Download URL | Direct download (no archive) |
| Download size | ~2 MB |
| Min required storage | 5 MB |
| Directory | `asr/vad/` |

#### API

```kotlin
object AsrModelRegistry {
    val defaultModel: AsrModelSpec       // Whisper Small multilingual
    val vadModel: AsrModelSpec           // Silero VAD
    fun asrModelDir(context): File       // Path to ASR model directory
    fun vadModelDir(context): File       // Path to VAD model directory
    fun isAsrReady(context): Boolean     // ASR model files present and non-empty
    fun isVadReady(context): Boolean     // VAD model file present and non-empty
    fun isReady(context): Boolean        // Both ASR and VAD ready
    fun whisperLanguageCode(languageId): String  // Map app language ID to Whisper code
}
```

#### Language Code Mapping

| App Language ID | Whisper Language Code |
|----------------|----------------------|
| `en` | `en` |
| `it` | `it` |
| `ru` | `ru` |
| Other | Passed through as-is (ISO 639-1) |

### 5.2.3 Model Manager

**File:** `data/AsrModelManager.kt`

Manages downloading ASR and VAD models. Shares the same `DownloadState` sealed class as TTS. Uses Apache Commons Compress for tar.bz2 extraction.

#### Storage Layout

```
context.filesDir/asr/
  whisper-small/                  -- Whisper Small multilingual
    small-encoder.int8.onnx        (~50 MB, int8 quantized)
    small-decoder.int8.onnx        (~180 MB, int8 quantized)
    small-tokens.txt               (~500 KB)
  vad/                            -- Silero VAD
    silero_vad.onnx                (~2 MB)
```

#### Public API

| Method | Description |
|--------|-------------|
| `isReady(): Boolean` | Both ASR and VAD models present |
| `isAsrReady(): Boolean` | ASR model files present and non-empty |
| `isVadReady(): Boolean` | VAD model file present and non-empty |
| `isNetworkMetered(): Boolean` | Whether current network is metered |
| `downloadVad(): Flow<DownloadState>` | Download VAD model (small file, no archive) |
| `downloadAsr(): Flow<DownloadState>` | Download and extract ASR model |

#### Download Flow

**VAD model** (small, direct download):
1. Check if already present
2. Search for pre-placed local file in `Downloads/BaseGrammy/` or app external storage (offline sideloading support)
3. If local file found, copy to target and return
4. Otherwise, download via HTTP with redirect following (up to 5 redirects)
5. Writes directly to target directory (no archive extraction needed)

**ASR model** (large, archive-based):
1. Check if already ready -- if so, emit `Done`
2. Storage check against `spec.minRequiredBytes` (800 MB)
3. Search for pre-placed local archive in known directories:
   - `Downloads/BaseGrammy/whisper-small.tar.bz2`
   - `Downloads/BaseGrammy/sherpa-onnx-whisper-small.tar.bz2`
   - App external storage equivalents
4. If local archive found (must exceed 100 MB minimum size), extract from it
5. Otherwise, download from GitHub releases:
   - Up to 3 retry attempts
   - 60s connect / 120s read timeouts
   - Manual HTTP redirect following (up to 5 hops)
   - Progress reporting with `fallbackDownloadSize` when Content-Length unknown
   - Cancellation support
6. Extract tar.bz2 via Apache Commons Compress
7. On extraction failure, delete output dir and emit `Error`
8. On success, delete archive file

#### Local File Sideload Paths

The manager searches for pre-placed model files to support offline deployment:

| Component | Search Paths |
|-----------|-------------|
| VAD model | `Downloads/BaseGrammy/silero_vad.onnx`, `<external-files>/asr-models/silero_vad.onnx` |
| ASR archive | `Downloads/BaseGrammy/whisper-small.tar.bz2`, `Downloads/BaseGrammy/sherpa-onnx-whisper-small.tar.bz2`, `<external-files>/asr-models/<name>.tar.bz2` |

### 5.2.4 ASR Engine

**File:** `data/AsrEngine.kt`

Wraps the Sherpa-ONNX `OfflineRecognizer` (Whisper model) and `Vad` (Silero VAD) for microphone-based speech recognition with automatic endpointing.

#### State Machine

```
IDLE ──initialize()──> INITIALIZING ──success──> READY
                                                        │
                                    recordAndTranscribe()┤
                                                        v
                                                    RECORDING
                                                        │
                                          (speech detected, then silence)
                                                        v
                                                   RECOGNIZING
                                                        │
                                            (decode complete)──> READY

Any state ──error──> ERROR
ERROR ──initialize()──> INITIALIZING (recovery path)
```

```kotlin
enum class AsrState {
    IDLE,           // No recognizer loaded
    INITIALIZING,   // OfflineRecognizer being created
    READY,          // Engine loaded, ready to record
    RECORDING,      // Microphone active, VAD processing
    RECOGNIZING,    // Decoding speech segment
    ERROR           // Initialization or recognition failed
}
```

#### Public API

| Method | Description |
|--------|-------------|
| `state: StateFlow<AsrState>` | Observable engine state |
| `errorMessage: String?` | Human-readable last error (null if no error) |
| `isReady: Boolean` | True when state == READY |
| `currentLanguage: String` | Active Whisper language code |
| `initialize(language)` | Load Whisper model + VAD; no-op if already ready |
| `setLanguage(language)` | Switch recognition language without reloading model files |
| `recordAndTranscribe(maxDurationMs): String` | Record with VAD endpointing, transcribe, return text |
| `stopRecording()` | Stop ongoing recording immediately |
| `release()` | Free all native resources |

#### Initialization Flow

1. **Guard**: if already READY, return immediately
2. **CAS transition**: atomically transition IDLE or ERROR to INITIALIZING
3. **Language mapping**: `AsrModelRegistry.whisperLanguageCode(language)` maps app language ID to Whisper code
4. **Recognizer creation** (on `Dispatchers.Default`):
   - Build `OfflineWhisperModelConfig` with `encoder`, `decoder`, `language`, `task="transcribe"`, `tailPaddings=-1`
   - Build `OfflineRecognizerConfig` with `FeatureConfig()` defaults (16kHz, 80-dim features), `greedy_search` decoding
   - Create `OfflineRecognizer(null, config)` (null assets, uses filesystem paths)
5. **VAD creation**:
   - Build `SileroVadModelConfig`: `threshold=0.5f`, `minSilenceDuration=0.25f`, `minSpeechDuration=0.25f`, `windowSize=512`, `maxSpeechDuration=30.0f`
   - Create `Vad(null, config)`
6. **Result**: on success sets READY; on failure sets ERROR with `errorMessage`

#### Language Switching

`setLanguage(language)` changes the recognition language at runtime without reloading model files:
- Rebuilds `OfflineRecognizerConfig` with the new Whisper language code
- Calls `recognizer.setConfig(config)` to update the recognizer in place
- Only works when engine is in READY state
- Logs warning if engine not initialized or not ready

#### Recognition Flow (recordAndTranscribe)

1. **Guard**: if recognizer or VAD is null, set ERROR and return empty string
2. **State transition**: RECORDING, clear errorMessage
3. **VAD reset**: `vad.reset()` to clear previous state
4. **AudioRecord setup**: 16kHz mono PCM_16BIT, buffer size >= 3200 (100ms at 16kHz), `VOICE_RECOGNITION` audio source
5. **Recording loop** (on `Dispatchers.Default`):
   - Read chunks of 1600 shorts (100ms at 16kHz)
   - Convert short samples to float (divide by 32768.0)
   - Feed to VAD: `vad.acceptWaveform(floatChunk)`
   - Check speech detection: `vad.isSpeechDetected()`
   - Collect speech segments: while `!vad.empty()`, pop segments and accumulate samples
   - **Endpoint detection**: if speech was detected and VAD no longer detects speech and segments are non-empty, break (speech completed)
   - **Timeout**: if no speech after 3 seconds, break (give up)
   - **Max duration**: break after `maxDurationMs` (default 10 seconds)
6. **Cleanup AudioRecord**: stop and release
7. **Empty check**: if no speech samples collected, set READY, return empty string
8. **Recognition** (state: RECOGNIZING):
   - Create `OfflineStream`, feed all accumulated speech samples
   - `recognizer.decode(stream)` runs Whisper inference
   - `recognizer.getResult(stream).text.trim()` returns recognized text
   - Release stream
9. **Result**: set READY, return recognized text
10. **Error handling**: any exception sets ERROR, releases AudioRecord

#### Audio Parameters

| Parameter | Value |
|-----------|-------|
| Sample rate | 16000 Hz |
| Channels | Mono |
| Encoding | PCM 16-bit (recording), converted to Float for VAD/ASR |
| Audio source | `VOICE_RECOGNITION` (noise suppression enabled) |
| VAD window size | 512 samples (32ms at 16kHz) |
| VAD speech threshold | 0.5 |
| Min speech duration | 0.25 seconds |
| Min silence duration | 0.25 seconds |
| Max speech duration | 30 seconds |
| Max recording duration | 10 seconds (configurable) |
| No-speech timeout | 3 seconds |

#### Resource Cleanup

- `stopRecording()`: stops and releases AudioRecord, transitions RECORDING to READY
- `release()`: stops recording, releases native `OfflineRecognizer` and `Vad`, resets to IDLE

### 5.2.5 Recognition Modes

The ASR system uses **offline (batch) recognition** only:

- **No streaming mode**: the entire speech segment is collected first, then decoded in one pass
- **VAD-based endpointing**: Silero VAD detects speech start and end automatically; the user does not need to press a stop button
- **Language-specific**: the Whisper language parameter is set explicitly (no auto-detection), ensuring correct transcription for the active target language

This is well-suited for language learning where inputs are short phrases (3-8 words) and sub-second latency is achieved with the Whisper Small int8 model.

### 5.2.6 Offline Behavior

- Both ASR and VAD models are downloaded on demand (not bundled in APK)
- Once downloaded, recognition is fully offline; no internet connection required
- The ASR model download is ~375 MB; the VAD model is ~2 MB
- Local file sideloading is supported: users can place model files in `Downloads/BaseGrammy/` to avoid network downloads
- Total ASR storage footprint: ~230 MB on disk (int8 quantized model)

### 5.2.7 Historical Context

The ASR subsystem replaced Android's `RecognizerIntent`-based voice input:
- Previous approach: `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` required internet + Google app
- Language mapping was: `ru` -> `ru-RU`, `it` -> `it-IT`, else `en-US`
- Two separate launchers existed in `GrammarMateApp.kt`
- The new offline ASR uses the same patterns as the TTS subsystem for consistency

---

## 5.3 Integration Points

### 5.3.1 Training Flow Integration

#### TTS in Training

| Context | When TTS Triggers | Text Spoken | Source |
|---------|-------------------|-------------|--------|
| Card prompt screen | User taps speaker icon | Target language answer | `state.answerText ?: state.currentCard?.acceptedAnswers?.firstOrNull()` |
| Result block (after answer) | User taps speaker icon | Answer text | `state.answerText` |
| Vocab flashcard (after reveal) | User taps speaker icon | Target language word | `state.vocabAnswerText` |

TTS is always user-initiated (tap on speaker icon). There is no auto-play behavior.

#### ASR in Training

| Context | When ASR Triggers | Input Mode | Result Processing |
|---------|-------------------|------------|-------------------|
| Card prompt (VOICE mode) | User taps microphone button | Voice answer to translation prompt | Recognized text compared against accepted answers |
| Daily practice translation block | User taps microphone button | Voice answer for daily translation | Same comparison logic |

The recognized text is processed identically to keyboard/word-bank input -- compared against the card's accepted answers list using the same matching logic (case-insensitive, accent-tolerant).

### 5.3.2 Mode-Dependent Audio Behavior

| Training Mode | TTS Used | ASR Used |
|---------------|----------|----------|
| KEYBOARD | Yes (user-initiated) | No |
| VOICE | Yes (user-initiated) | Yes (primary input method) |
| WORD_BANK | Yes (user-initiated) | No |
| Boss battle | Yes (user-initiated) | Optional (VOICE mode in boss) |
| Daily practice | Yes (user-initiated) | Yes (translation block) |

### 5.3.3 Error Handling When Models Not Available

#### TTS Fallback Chain

1. User taps speaker icon
2. Check `ttsModelReady` for active language
3. If model not downloaded:
   - If background download in progress for this language: show download dialog with current progress
   - Otherwise: show download dialog with size estimate and Download/Cancel buttons
4. If model downloaded but engine not initialized: auto-initialize, then speak
5. If engine in ERROR state: attempt re-initialization
6. If no TTS model registered for language: speaker button shows error icon, graceful degradation

#### ASR Fallback Chain

1. User taps microphone / selects VOICE mode
2. Check `AsrModelRegistry.isReady(context)` for both ASR and VAD models
3. If models not downloaded: show download dialog (or offer to switch to KEYBOARD mode)
4. If engine in ERROR state: attempt re-initialization
5. If recognition returns empty string: show "No speech detected" feedback
6. If microphone unavailable: show error, suggest KEYBOARD mode

### 5.3.4 Memory Management

| Component | Native Memory (approximate) |
|-----------|---------------------------|
| TTS engine (Kokoro English) | ~500 MB |
| TTS engine (VITS Italian) | ~80 MB |
| ASR engine (Whisper Small) | ~300-500 MB |
| VAD (Silero) | ~10 MB |

Loading both TTS and ASR simultaneously may use 500-1000 MB of native memory. Mitigations:
- TTS engine is initialized lazily on first speak request
- ASR engine is initialized lazily on first voice input
- Only one TTS model is loaded at a time (language switch releases the previous one)
- ASR and TTS engines can coexist but total memory is significant on low-end devices
- Engines are released in `onCleared()` when the ViewModel is destroyed

### 5.3.5 Threading Model

```
UI thread (Main)
  |
  v
viewModelScope (Main)
  |
  +-- Dispatchers.IO      -- TTS model download, file extraction (TtsModelManager)
  |                        -- ASR model download (AsrModelManager)
  |
  +-- Dispatchers.Default  -- OfflineTts initialization and synthesis (TtsEngine)
  |                        -- OfflineRecognizer initialization and decode (AsrEngine)
  |                        -- AudioRecord loop + VAD processing (AsrEngine)
  |
  +-- ttsScope (Default)   -- AudioTrack playback coroutine (TtsEngine internal)
  |
  +-- Native thread        -- Streaming synthesis callback (AudioTrack.write)
```

### 5.3.6 Gradle Dependencies

```kotlin
dependencies {
    // Sherpa-ONNX (TTS + ASR, statically linked ONNX Runtime)
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.12.40.aar"))

    // Tar.bz2 extraction for model downloads
    implementation("org.apache.commons:commons-compress:1.26.1")
}
```

No other native dependencies are required. The AAR contains ONNX Runtime, Sherpa-ONNX native code, and Kotlin bindings for both TTS and ASR.

### 5.3.7 APK Size Impact

| Component | Size Impact |
|-----------|-------------|
| Sherpa-ONNX AAR | ~36 MB (or ~18 MB per ABI with splits) |
| Apache Commons Compress | ~500 KB |
| **Total additional APK size** | **~36-37 MB** |

Downloaded models are not included in the APK. Total on-device storage for all models (English TTS + Italian TTS + ASR + VAD) is approximately 1.1 GB.
