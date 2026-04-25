# TTS Module Design — Sherpa-ONNX + Kokoro

**Date:** 2026-04-25
**Branch:** feature/tts-sherpa-onnx
**Status:** Design phase

---

## 1. Overview

Add offline text-to-speech to GrammarMate so users can hear the correct English pronunciation of sentences and vocabulary. Uses Sherpa-ONNX AAR with Kokoro TTS model, downloaded on first use.

---

## 2. Architecture

### 2.1 File layout (new files only)

```
app/libs/
  sherpa-onnx-static-link-onnxruntime-1.12.40.aar

app/src/main/java/com/alexpo/grammermate/data/
  TtsEngine.kt          -- wraps OfflineTts, AudioTrack playback, lifecycle
  TtsModelManager.kt    -- downloads model, checks existence, progress reporting
```

No new files in `ui/`. Changes to existing `TrainingViewModel.kt` and `GrammarMateApp.kt` are described below.

### 2.2 Layer responsibilities

```
data/TtsModelManager.kt    -- file I/O, network download, progress Flow
data/TtsEngine.kt          -- native TTS wrapper, AudioTrack playback
ui/TrainingViewModel.kt    -- holds TtsEngine, exposes speak()/stop(), owns TtsState
ui/GrammarMateApp.kt       -- renders speaker icon, observes TtsState
```

Both new data classes follow the existing pattern: constructor takes `Context`, instantiated inside `TrainingViewModel` alongside `MasteryStore`, `ProgressStore`, etc.

---

## 3. TtsModelManager

**File:** `data/TtsModelManager.kt`

Manages downloading and storing the Kokoro model on device internal storage.

### 3.1 Model storage location

```
context.filesDir/tts/kokoro-en-v0_19/
  model.onnx          (330 MB)
  tokens.txt          (1.1 KB)
  voices.bin          (5.5 MB)
  espeak-ng-data/     (~10 MB)
    ...
```

Total on disk: ~346 MB. This is internal storage, cleaned up on uninstall.

### 3.2 Public API

```kotlin
class TtsModelManager(private val context: Context) {

    /** Absolute path to model directory on device storage. */
    val modelDir: File
        get() = File(context.filesDir, "tts/kokoro-en-v0_19")

    /** True if all required model files are present and non-empty. */
    fun isModelReady(): Boolean

    /** Total size of model files in bytes, or 0 if not downloaded. */
    fun getDownloadedSize(): Long

    /** Delete all model files to free space. */
    fun deleteModel()

    /**
     * Download and extract the Kokoro model.
     * Emits progress as DownloadState values.
     * Cancelling the coroutine cancels the download.
     *
     * Uses tar.bz2 from GitHub releases. Streams download, extracts on the fly
     * to avoid needing 2x disk space.
     */
    fun download(): Flow<DownloadState>
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val percent: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Extracting(val percent: Int) : DownloadState()
    object Done : DownloadState()
    data class Error(val message: String) : DownloadState()
}
```

### 3.3 Download implementation notes

- Source URL: `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2`
- Use `OkHttp` or `java.net.HttpURLConnection` for download (no new dependency needed -- use HttpURLConnection).
- Download to a temp file in `context.cacheDir`, then extract using `org.apache.commons.compress` or manual tar+bz2 parsing. Since we want zero new dependencies, use a two-step approach:
  1. Stream-download to `cacheDir/kokoro.tar.bz2`
  2. Use Android's `Bzip2CompressorInputStream` (not available by default) -- **simpler approach**: download the `.tar.bz2`, decompress to `.tar`, then parse tar manually with `TarArchiveInputStream` from Apache Commons Compress.
- **Recommended minimal approach**: Add `implementation("org.apache.commons:commons-compress:1.26.1")` to handle tar.bz2 extraction. This is a ~500 KB dependency. Alternatively, extract the tar.bz2 on first run using a simple custom bzip2+tar parser, but this is fragile.
- **Decision**: Use Apache Commons Compress. It handles tar.bz2 in one pass, is small, and widely used on Android.
- Support download resume: check if partial `.tar.bz2` exists in cache, compare size with HTTP Content-Length, resume from byte offset using `Range` header. Nice-to-have; if GitHub releases don't support Range, just re-download.

### 3.4 isModelReady() check

Must verify these files exist and are non-empty:
- `model.onnx`
- `tokens.txt`
- `voices.bin`
- `espeak-ng-data/` directory exists

A simple checksum is not needed -- file existence + non-zero size is sufficient.

---

## 4. TtsEngine

**File:** `data/TtsEngine.kt`

Wraps Sherpa-ONNX `OfflineTts`, manages native lifecycle and AudioTrack playback.

### 4.1 Public API

```kotlin
enum class TtsState {
    IDLE,               // no TTS loaded, no playback
    INITIALIZING,       // OfflineTts is being created (1-3 seconds)
    READY,              // engine loaded, ready to speak
    SPEAKING,           // audio is playing
    ERROR               // initialization or playback failed
}

class TtsEngine(private val context: Context) {

    /** Current state, observable. */
    val state: StateFlow<TtsState>

    /** True if engine is loaded and ready to synthesize. */
    val isReady: Boolean

    /**
     * Initialize the TTS engine. Loads OfflineTts with model from device storage.
     * Must be called after model download is complete.
     * Safe to call multiple times -- no-op if already initialized.
     * Runs on Dispatchers.Default internally.
     */
    suspend fun initialize()

    /**
     * Synthesize text and play audio. Uses streaming callback for low latency.
     * If already speaking, stops current playback first.
     * @param text English text to speak
     * @param speakerId Kokoro speaker ID (0 = "af" default)
     * @param speed Speech speed multiplier (1.0 = normal)
     */
    suspend fun speak(text: String, speakerId: Int = 0, speed: Float = 1.0f)

    /** Stop current playback immediately. No-op if not speaking. */
    fun stop()

    /** Release native resources. Call when ViewModel is cleared. */
    fun release()
}
```

### 4.2 Initialization flow (pseudocode)

```kotlin
suspend fun initialize() {
    if (_state.value == TtsState.READY) return
    if (!_state.compareAndSet(TtsState.IDLE, TtsState.INITIALIZING)) return

    withContext(Dispatchers.Default) {
        try {
            val modelDir = File(context.filesDir, "tts/kokoro-en-v0_19")
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = File(modelDir, "model.onnx").absolutePath,
                        voices = File(modelDir, "voices.bin").absolutePath,
                        tokens = File(modelDir, "tokens.txt").absolutePath,
                        dataDir = File(modelDir, "espeak-ng-data").absolutePath,
                        // lexicon not needed for English-only Kokoro v0_19
                    ),
                    numThreads = 4,
                    debug = false,
                    provider = "cpu",
                ),
            )
            // NOTE: Using filesystem paths, NOT assetManager, since model
            // is downloaded to internal storage, not bundled in assets.
            offlineTts = OfflineTts(config = config)
            _state.value = TtsState.READY
        } catch (e: Exception) {
            _state.value = TtsState.ERROR
            Log.e("TtsEngine", "Initialization failed", e)
        }
    }
}
```

Key point: `OfflineTts(config)` (without assetManager) uses absolute filesystem paths. This works because the model is on internal storage, not in assets.

### 4.3 Playback with streaming callback (pseudocode)

```kotlin
suspend fun speak(text: String, speakerId: Int = 0, speed: Float = 1.0f) {
    val tts = offlineTts ?: return
    stop() // stop any current playback

    withContext(Dispatchers.Default) {
        _state.value = TtsState.SPEAKING

        val sampleRate = tts.sampleRate()
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
            .setBufferSizeInBytes(
                AudioTrack.getMinBufferSize(sampleRate, CHANNEL_OUT_MONO, ENCODING_PCM_FLOAT)
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        currentTrack = audioTrack
        audioTrack.play()

        try {
            tts.generateWithConfigAndCallback(
                text = text,
                config = GenerationConfig(sid = speakerId, speed = speed),
                callback = { samples ->
                    if (!isStopped) {
                        audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                        1 // continue
                    } else {
                        0 // stop
                    }
                }
            )
        } finally {
            audioTrack.stop()
            audioTrack.release()
            currentTrack = null
            if (_state.value == TtsState.SPEAKING) {
                _state.value = TtsState.READY
            }
        }
    }
}
```

### 4.4 Stop and release

```kotlin
private var currentTrack: AudioTrack? = null
private var isStopped = false
private var offlineTts: OfflineTts? = null

fun stop() {
    isStopped = true
    currentTrack?.stop()
    // state will be set to READY by the finally block in speak()
}

fun release() {
    stop()
    offlineTts?.free()
    offlineTts = null
    _state.value = TtsState.IDLE
}
```

### 4.5 Speaker ID selection

Kokoro English v0_19 speakers:
- 0 = `af` (default, American female)
- 5 = `am_adam` (American male)

Use speaker 0 as default. A future enhancement can add a setting to pick preferred speaker.

---

## 5. ViewModel integration

### 5.1 TrainingViewModel changes

Add TTS state to `TrainingUiState`:

```kotlin
data class TrainingUiState(
    // ... existing fields ...

    // TTS
    val ttsState: TtsState = TtsState.IDLE,
    val ttsDownloadState: DownloadState = DownloadState.Idle,
    val ttsModelReady: Boolean = false,
)
```

Add TTS members and methods to `TrainingViewModel`:

```kotlin
class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    // ... existing members ...
    private val ttsModelManager = TtsModelManager(application)
    private val ttsEngine = TtsEngine(application)

    // Expose TTS state to UI by collecting into _uiState
    init {
        // ... existing init code ...

        viewModelScope.launch {
            ttsEngine.state.collect { ttsState ->
                _uiState.update { it.copy(ttsState = ttsState) }
            }
        }
    }

    /** Called by UI when user taps speaker icon. */
    fun onTtsSpeak(text: String) {
        viewModelScope.launch {
            if (!ttsModelManager.isModelReady()) {
                // trigger download -- UI will show progress dialog
                return@launch
            }
            if (ttsEngine.state.value != TtsState.READY) {
                ttsEngine.initialize()
            }
            ttsEngine.speak(text)
        }
    }

    /** Start model download. UI collects ttsDownloadState from uiState. */
    fun startTtsDownload() {
        viewModelScope.launch {
            ttsModelManager.download().collect { downloadState ->
                _uiState.update { it.copy(ttsDownloadState = downloadState) }
                if (downloadState is DownloadState.Done) {
                    _uiState.update { it.copy(ttsModelReady = true) }
                    ttsEngine.initialize()
                }
            }
        }
    }

    /** Check if TTS model is downloaded. Call on app start. */
    fun checkTtsModel() {
        val ready = ttsModelManager.isModelReady()
        _uiState.update { it.copy(ttsModelReady = ready) }
        if (ready) {
            viewModelScope.launch { ttsEngine.initialize() }
        }
    }

    fun stopTts() {
        ttsEngine.stop()
    }

    override fun onCleared() {
        super.onCleared()
        ttsEngine.release()
        soundPool.release()
    }
}
```

### 5.2 What text to speak

In different contexts:

| Context | Text to speak | Source |
|---------|--------------|--------|
| CardPrompt (after answer) | `card.acceptedAnswers.first()` | `state.answerText` or `state.currentCard?.acceptedAnswers?.first()` |
| ResultBlock | `state.answerText` | Already displayed |
| VocabSprintScreen (after reveal) | `vocab.targetText` | `state.vocabAnswerText` |

Only speak English text (the target language). Never speak the Russian prompt.

---

## 6. UI integration

### 6.1 Speaker icon placement

Three locations get a speaker icon button:

**A. CardPrompt** (line ~2448) -- next to the Russian prompt card, speaks the English answer:

```kotlin
@Composable
private fun CardPrompt(state: TrainingUiState, onSpeak: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "RU", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.currentCard?.promptRu ?: "No cards",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TtsSpeakerButton(
                ttsState = state.ttsState,
                enabled = state.answerText != null, // only after answer revealed
                onClick = onSpeak
            )
        }
    }
}
```

**B. ResultBlock** (line ~2718) -- speaks the answer text:

```kotlin
@Composable
private fun ResultBlock(state: TrainingUiState, onSpeak: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (state.lastResult) {
                true -> Text(text = "Correct", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                false -> Text(text = "Incorrect", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                null -> Text(text = "")
            }
            if (!state.answerText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                TtsSpeakerButton(
                    ttsState = state.ttsState,
                    enabled = true,
                    onClick = onSpeak
                )
            }
        }
        if (!state.answerText.isNullOrBlank()) {
            Text(text = "Answer: ${state.answerText}")
        }
    }
}
```

**C. VocabSprintScreen** (line ~1253) -- speaks the target text after reveal:

```kotlin
// After the answer text display (line ~1253)
state.vocabAnswerText?.let { answer ->
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Answer: $answer", color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        TtsSpeakerButton(
            ttsState = state.ttsState,
            enabled = true,
            onClick = { onSpeak(answer) }
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
}
```

### 6.2 TtsSpeakerButton composable

A reusable button that shows the correct icon based on TTS state:

```kotlin
@Composable
private fun TtsSpeakerButton(
    ttsState: TtsState,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = {
            if (ttsState == TtsState.SPEAKING) {
                // tap while speaking = stop
                // (handled by parent calling stopTts)
            }
            onClick()
        },
        enabled = enabled
    ) {
        when (ttsState) {
            TtsState.SPEAKING -> Icon(
                Icons.Default.StopCircle,
                contentDescription = "Stop",
                tint = MaterialTheme.colorScheme.error
            )
            TtsState.INITIALIZING -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            TtsState.ERROR -> Icon(
                Icons.Default.ReportProblem,
                contentDescription = "TTS error",
                tint = MaterialTheme.colorScheme.error
            )
            else -> Icon(
                Icons.Default.VolumeUp,
                contentDescription = "Listen"
            )
        }
    }
}
```

### 6.3 Download dialog

When user taps speaker for the first time and model is not downloaded, show a dialog:

```kotlin
@Composable
private fun TtsDownloadDialog(
    downloadState: DownloadState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download pronunciation model?") },
        text = {
            when (downloadState) {
                is DownloadState.Idle -> {
                    Text("This will download ~346 MB for offline pronunciation. Uses internal storage.")
                }
                is DownloadState.Downloading -> {
                    Column {
                        Text("Downloading... ${downloadState.percent}%")
                        LinearProgressIndicator(
                            progress = downloadState.percent / 100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is DownloadState.Extracting -> {
                    Column {
                        Text("Extracting... ${downloadState.percent}%")
                        LinearProgressIndicator(
                            progress = downloadState.percent / 100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is DownloadState.Done -> {
                    Text("Download complete! Initializing...")
                }
                is DownloadState.Error -> {
                    Text("Download failed: ${downloadState.message}")
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                is DownloadState.Idle -> TextButton(onClick = onConfirm) { Text("Download") }
                is DownloadState.Done, is DownloadState.Error -> TextButton(onClick = onDismiss) { Text("OK") }
                else -> {} // downloading/extracting -- no action
            }
        },
        dismissButton = {
            if (downloadState !is DownloadState.Downloading && downloadState !is DownloadState.Extracting) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
```

### 6.4 Wiring in GrammarMateApp

The top-level `GrammarMateApp` composable needs to:
1. Track `showTtsDownloadDialog` state
2. Pass speak callbacks down to child composables

```kotlin
// In GrammarMateApp(), add:
var showTtsDownloadDialog by remember { mutableStateOf(false) }

// In TrainingScreen composable call, add speak callback:
CardPrompt(
    state = state,
    onSpeak = {
        if (!state.ttsModelReady) {
            showTtsDownloadDialog = true
        } else {
            val text = state.answerText
                ?: state.currentCard?.acceptedAnswers?.firstOrNull()
            if (text != null) vm.onTtsSpeak(text)
        }
    }
)

// Download dialog:
if (showTtsDownloadDialog) {
    TtsDownloadDialog(
        downloadState = state.ttsDownloadState,
        onConfirm = { vm.startTtsDownload() },
        onDismiss = { showTtsDownloadDialog = false }
    )
}
```

---

## 7. Gradle changes

### 7.1 app/build.gradle.kts additions

```kotlin
dependencies {
    // ... existing dependencies ...

    // Sherpa-ONNX TTS (static-linked ONNX Runtime, no external ONNX libs needed)
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.12.40.aar"))

    // For tar.bz2 extraction during model download
    implementation("org.apache.commons:commons-compress:1.26.1")
}
```

The AAR must be placed at `app/libs/sherpa-onnx-static-link-onnxruntime-1.12.40.aar`.

---

## 8. Lifecycle and threading

### 8.1 Thread model

```
UI thread (Main)        -- button taps, state observation
  |
  v
viewModelScope          -- onTtsSpeak(), startTtsDownload()
  |
  +-- Dispatchers.IO    -- network download, file extraction (TtsModelManager)
  |
  +-- Dispatchers.Default -- OfflineTts.init(), tts.generate() (TtsEngine)
  |
  +-- Native thread     -- streaming callback (AudioTrack.write)
```

### 8.2 Lifecycle rules

- `TtsEngine` is created in `TrainingViewModel.init` and released in `onCleared()`.
- `TtsModelManager` is stateless (no native resources), only file I/O.
- If user leaves the screen while speaking, `stop()` is called from `onCleared()`.
- `OfflineTts` instance is created once and reused. Re-creation only on error recovery.
- AudioTrack is created per `speak()` call and released in the `finally` block.

### 8.3 Cancellation

- `speak()` is a suspend function. Cancelling the coroutine stops audio playback.
- Download is a `Flow<DownloadState>`. Cancelling the collector cancels the download coroutine.
- The streaming callback checks `isStopped` flag to abort generation early.

---

## 9. Error handling

| Error | Handling |
|-------|----------|
| Model download fails (network) | Show error in dialog, allow retry |
| Extraction fails (corrupt file) | Delete partial files, show error |
| Not enough disk space | Check available space before download, show message |
| TTS init fails (corrupt model) | Set state to ERROR, offer re-download |
| Playback fails | Log error, set state to READY (recoverable) |
| Invalid speaker ID | Use default 0, log warning |
| Text is empty | No-op, don't call speak() |

---

## 10. Performance considerations

- **Cold start**: OfflineTts initialization takes 1-3 seconds on mid-range devices. Do it lazily on first speak request, or in background after model download completes.
- **First inference**: May be slower due to ONNX warm-up. Subsequent calls are faster.
- **Memory**: OfflineTts allocates ~500 MB of native memory for the model. This is the main cost. On low-memory devices, consider not initializing until explicitly requested.
- **Lazy init strategy**: Do NOT auto-initialize on app start. Initialize only when:
  1. Model is already downloaded AND
  2. User taps speaker icon for the first time in the session.
- **APK size impact**: The AAR adds ~36 MB to APK (or ~18 MB per ABI with splits). Consider ABI splits for release builds.

---

## 11. Future enhancements (out of scope for initial implementation)

- Speaker selection setting (pick from 11 Kokoro voices)
- Speed control setting
- Auto-play pronunciation after correct answer (optional setting)
- Int8 quantized model for smaller download (~170 MB instead of ~346 MB)
- Multi-language model support (Kokoro multi-lang v1_1)
- Pre-cache common phrases
- Download resume support (partial file + HTTP Range)

---

## 12. Implementation order

1. Place AAR in `app/libs/`, add Gradle dependency + commons-compress
2. Implement `TtsModelManager.kt` (download, check, delete)
3. Implement `TtsEngine.kt` (init, speak, stop, release)
4. Add TTS fields to `TrainingUiState`
5. Add TTS methods to `TrainingViewModel`
6. Add `TtsSpeakerButton` composable to `GrammarMateApp.kt`
7. Wire speaker icon into `CardPrompt`, `ResultBlock`, `VocabSprintScreen`
8. Add `TtsDownloadDialog` composable
9. Test manually on device
10. Add unit tests for `TtsModelManager.isModelReady()` logic
