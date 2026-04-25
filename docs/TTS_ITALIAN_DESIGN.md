# Italian TTS Implementation Design

## Overview

Add Italian TTS using the Piper VITS model (`it_IT-paola-medium`, ~65 MB) alongside the existing English Kokoro TTS. Both architectures run on the same sherpa-onnx library already present in `app/libs/`.

The key architectural decision: **single `OfflineTts` instance, reinitialized when the active language changes** (Option A from the research doc). This keeps memory usage to one model at a time (~80-350 MB of ONNX runtime) and avoids the complexity of an abstraction layer.

---

## 1. Model Registry (New File: `data/TtsModelRegistry.kt`)

A pure-data object that maps language IDs to their TTS model specifications. Lives in the `data/` layer with no Android dependencies beyond `java.io.File`.

```kotlin
enum class TtsModelType { KOKORO, VITS_PIPER }

data class TtsModelSpec(
    val languageId: String,
    val displayName: String,          // "English", "Italian"
    val modelType: TtsModelType,
    val downloadUrl: String,
    val archivePrefix: String,        // top-level dir to strip from tar entries
    val modelDirName: String,         // subdirectory under filesDir/tts/
    val fallbackDownloadSize: Long,   // bytes, used when Content-Length is unknown
    val minRequiredBytes: Long,       // min free storage needed
    val requiredFiles: List<String>,  // relative paths that must exist after extraction
    val requiredDirs: List<String>    // relative directory paths that must exist
)

object TtsModelRegistry {

    val models: Map<String, TtsModelSpec> = mapOf(
        "en" to TtsModelSpec(
            languageId = "en",
            displayName = "English",
            modelType = TtsModelType.KOKORO,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2",
            archivePrefix = "kokoro-en-v0_19/",
            modelDirName = "kokoro-en-v0_19",
            fallbackDownloadSize = 350L * 1024 * 1024,
            minRequiredBytes = 700L * 1024 * 1024,
            requiredFiles = listOf("model.onnx", "tokens.txt", "voices.bin"),
            requiredDirs = listOf("espeak-ng-data")
        ),
        "it" to TtsModelSpec(
            languageId = "it",
            displayName = "Italian",
            modelType = TtsModelType.VITS_PIPER,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-it_IT-paola-medium.tar.bz2",
            archivePrefix = "vits-piper-it_IT-paola-medium/",
            modelDirName = "vits-piper-it_IT-paola-medium",
            fallbackDownloadSize = 65L * 1024 * 1024,
            minRequiredBytes = 150L * 1024 * 1024,
            requiredFiles = listOf("model.onnx", "tokens.txt"),
            requiredDirs = listOf("espeak-ng-data")
        )
    )

    fun specFor(languageId: String): TtsModelSpec? = models[languageId]
}
```

### Why a separate file?
- `TtsModelManager` is already 260 lines. Adding a registry doubles its size.
- The registry is pure configuration with zero logic -- it belongs in `data/`.
- Adding a new language later is a single entry in this map.

---

## 2. Changes to `TtsModelManager`

The current manager is hardcoded to the English Kokoro model. Changes:

### 2a. Replace fixed fields with registry lookups

```
BEFORE:
    val modelDir: File = File(context.filesDir, "tts/kokoro-en-v0_19")
    private val archiveFile: File = File(context.cacheDir, "kokoro-en-v0_19.tar.bz2")

AFTER:
    fun modelDir(languageId: String): File {
        val spec = TtsModelRegistry.specFor(languageId) ?: error("Unknown language: $languageId")
        return File(context.filesDir, "tts/${spec.modelDirName}")
    }

    private fun archiveFile(languageId: String): File {
        val spec = TtsModelRegistry.specFor(languageId) ?: error("Unknown language: $languageId")
        return File(context.cacheDir, "${spec.modelDirName}.tar.bz2")
    }
```

### 2b. Make `isModelReady` language-aware

```
BEFORE:
    fun isModelReady(): Boolean { ... checks fixed Kokoro files ... }

AFTER:
    fun isModelReady(languageId: String): Boolean {
        val spec = TtsModelRegistry.specFor(languageId) ?: return false
        val dir = modelDir(languageId)
        if (!dir.exists()) return false
        val filesOk = spec.requiredFiles.all { f ->
            val file = File(dir, f)
            file.exists() && file.length() > 0
        }
        val dirsOk = spec.requiredDirs.all { d ->
            val file = File(dir, d)
            file.exists() && file.isDirectory
        }
        return filesOk && dirsOk
    }

    /** Backward-compat: checks current language */
    fun isModelReady(): Boolean = isModelReady(currentLanguageId)
```

### 2c. Make `download` language-aware

```
BEFORE:
    fun download(): Flow<DownloadState> = flow { ... uses MODEL_URL constant ... }

AFTER:
    var currentLanguageId: String = "en"

    fun download(languageId: String): Flow<DownloadState> = flow {
        val spec = TtsModelRegistry.specFor(languageId)
            ?: throw IllegalArgumentException("Unknown language: $languageId")
        val dir = modelDir(languageId)
        val archive = archiveFile(languageId)

        // Disk space check using spec.minRequiredBytes
        val availableBytes = getAvailableStorageBytes()
        if (availableBytes < spec.minRequiredBytes) { ... emit Error ... }

        // Download from spec.downloadUrl with redirect handling (same logic)
        // Extract tar.bz2 stripping spec.archivePrefix (parameterized)
        ...
    }

    /** Backward-compat: downloads current language */
    fun download(): Flow<DownloadState> = download(currentLanguageId)
```

### 2d. Parameterize `extractTarBz2`

The archive prefix to strip changes per model:
- English: `"kokoro-en-v0_19/"`
- Italian: `"vits-piper-it_IT-paola-medium/"`

```
BEFORE:
    val relativePath = entryName.removePrefix("kokoro-en-v0_19/")

AFTER:
    val relativePath = entryName.removePrefix(spec.archivePrefix)
```

### 2e. Add `downloadMultiple` for background downloads

```
fun downloadMultiple(
    languageIds: List<String>,
    progressCallback: (Map<String, DownloadState>) -> Unit
): Flow<Map<String, DownloadState>> = flow {
    val states = languageIds.associateWith { DownloadState.Idle }.toMutableMap()
    progressCallback(states.toMap())

    for (langId in languageIds) {
        if (isModelReady(langId)) {
            states[langId] = DownloadState.Done
            emit(states.toMap())
            continue
        }
        download(langId).collect { state ->
            states[langId] = state
            emit(states.toMap())
        }
    }
}
```

Note: This downloads sequentially (not in parallel) to avoid doubling disk I/O and network usage. The Italian model is only ~65 MB, so the total sequential time is dominated by the English model.

### 2f. Remove hardcoded companion object constants

Remove `MODEL_URL`, `MIN_REQUIRED_BYTES`, `FALLBACK_DOWNLOAD_SIZE` -- these are now in `TtsModelSpec`. Keep `CONNECT_TIMEOUT_MS`, `READ_TIMEOUT_MS`, `MAX_DOWNLOAD_ATTEMPTS`, `RETRY_DELAY_MS` as they are shared infrastructure.

---

## 3. Changes to `TtsEngine`

### 3a. Track active language, reinitialize on change

```
BEFORE:
    private var offlineTts: OfflineTts? = null

AFTER:
    private var offlineTts: OfflineTts? = null
    private var activeLanguageId: String? = null
```

### 3b. Make `initialize` language-aware

```kotlin
suspend fun initialize(languageId: String = "en") {
    if (_state.value == TtsState.READY && activeLanguageId == languageId) return

    // Release previous engine if loaded for a different language
    if (activeLanguageId != null && activeLanguageId != languageId) {
        release()
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
```

### 3c. Config builder -- the core dual-architecture support

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
            numThreads = 4,
            debug = false,
            provider = "cpu",
        )
        TtsModelType.VITS_PIPER -> OfflineTtsModelConfig(
            vits = OfflineTtsVitsModelConfig(
                model = File(modelDir, "model.onnx").absolutePath,
                tokens = File(modelDir, "tokens.txt").absolutePath,
                dataDir = File(modelDir, "espeak-ng-data").absolutePath,
                // lexicon and dictDir: empty strings (Piper uses espeak-ng-data for phonemization)
                // noiseScale, noiseScaleW, lengthScale: use defaults (0.667, 0.8, 1.0)
            ),
            numThreads = 4,
            debug = false,
            provider = "cpu",
        )
    }
    return OfflineTtsConfig(model = modelConfig)
}
```

**Key detail:** `OfflineTtsVitsModelConfig` constructor signature is:
```
(model, lexicon, tokens, dataDir, dictDir, noiseScale, noiseScaleW, lengthScale)
```
For Piper models, `lexicon` and `dictDir` are empty strings (phonemization goes through `espeak-ng-data`). The noise/length scale defaults work well for medium quality.

### 3d. Make `speak` language-aware

```kotlin
suspend fun speak(text: String, languageId: String = "en", speakerId: Int = 0, speed: Float = 1.0f) {
    if (text.isBlank()) return

    // Auto-initialize or switch language if needed
    if (activeLanguageId != languageId || _state.value != TtsState.READY) {
        initialize(languageId)
    }

    val tts = offlineTts ?: return
    // ... rest of speak logic unchanged (audio track, streaming callback) ...
}
```

The audio playback code (AudioTrack, streaming callback, generation counter, audio focus) is **identical for both architectures**. The only difference is config construction, which is handled by `buildConfig`.

### 3e. Update `release`

```kotlin
fun release() {
    stop()
    speakJob?.cancel()
    offlineTts?.free()
    offlineTts = null
    activeLanguageId = null
    _state.value = TtsState.IDLE
}
```

---

## 4. Changes to `TrainingViewModel`

### 4a. Background download on startup

Add a new method called during ViewModel init (after `checkTtsModel()`):

```kotlin
private var bgDownloadJob: Job? = null

private fun startBackgroundTtsDownload() {
    val languages = _uiState.value.languages
    if (languages.isEmpty()) return

    // Find languages whose models are not yet downloaded
    val missingLanguages = languages.map { it.id }
        .filter { !ttsModelManager.isModelReady(it) }

    if (missingLanguages.isEmpty()) return
    if (bgDownloadJob?.isActive == true) return  // already downloading

    bgDownloadJob = viewModelScope.launch(Dispatchers.IO) {
        ttsModelManager.downloadMultiple(missingLanguages).collect { stateMap ->
            val aggregateStates = stateMap.values
            val allDone = aggregateStates.all { it is DownloadState.Done }
            val anyActive = aggregateStates.any {
                it is DownloadState.Downloading || it is DownloadState.Extracting
            }

            _uiState.update { current ->
                current.copy(
                    bgTtsDownloadStates = stateMap,
                    bgTtsDownloading = anyActive,
                    ttsModelReady = ttsModelManager.isModelReady(current.selectedLanguageId)
                )
            }

            if (allDone) {
                _uiState.update { it.copy(bgTtsDownloading = false) }
            }
        }
    }
}
```

Call site in `init`:
```kotlin
checkTtsModel()
startBackgroundTtsDownload()  // <-- new
viewModelScope.launch {
    ttsEngine.state.collect { ... }
}
```

### 4b. Language-aware `onTtsSpeak`

```kotlin
BEFORE:
    fun onTtsSpeak(text: String) {
        ...
        ttsEngine.speak(text)
    }

AFTER:
    fun onTtsSpeak(text: String) {
        if (text.isBlank()) return
        val langId = _uiState.value.selectedLanguageId
        viewModelScope.launch {
            if (ttsEngine.state.value != TtsState.READY
                || ttsEngine.activeLanguageId != langId) {
                ttsEngine.initialize(langId)
            }
            ttsEngine.speak(text, languageId = langId)
        }
    }
```

### 4c. Language-aware `checkTtsModel`

```kotlin
BEFORE:
    private fun checkTtsModel() {
        val ready = ttsModelManager.isModelReady()
        _uiState.update { it.copy(ttsModelReady = ready) }
    }

AFTER:
    private fun checkTtsModel() {
        val langId = _uiState.value.selectedLanguageId
        val ready = ttsModelManager.isModelReady(langId)
        _uiState.update { it.copy(ttsModelReady = ready) }
    }
```

### 4d. Language-aware download dialog flow

```kotlin
BEFORE:
    private fun beginTtsDownload() {
        ...
        ttsModelManager.download().collect { ... }
    }

AFTER:
    private fun beginTtsDownload() {
        if (ttsDownloadJob?.isActive == true) return
        val langId = _uiState.value.selectedLanguageId
        ttsDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            ttsModelManager.download(langId).collect { downloadState ->
                _uiState.update { it.copy(ttsDownloadState = downloadState) }
                if (downloadState is DownloadState.Done) {
                    _uiState.update { it.copy(ttsModelReady = true) }
                }
            }
        }
    }
```

### 4e. Propagate `currentLanguageId` to `TtsModelManager`

When `selectLanguage()` is called, update the model manager:

```kotlin
fun selectLanguage(languageId: String) {
    ttsModelManager.currentLanguageId = languageId
    // ... existing language switch logic ...
    checkTtsModel()  // refresh ttsModelReady for the new language
}
```

### 4f. Cancel background download on `onCleared`

```kotlin
override fun onCleared() {
    bgDownloadJob?.cancel()
    ttsEngine.release()
    soundPool.release()
    super.onCleared()
}
```

---

## 5. Changes to `TrainingUiState`

Add fields for background download tracking:

```kotlin
data class TrainingUiState(
    // ... existing fields ...
    val ttsState: TtsState = TtsState.IDLE,
    val ttsDownloadState: DownloadState = DownloadState.Idle,
    val ttsModelReady: Boolean = false,
    val ttsMeteredNetwork: Boolean = false,
    // NEW: background download state
    val bgTtsDownloading: Boolean = false,
    val bgTtsDownloadStates: Map<String, DownloadState> = emptyMap()
)
```

---

## 6. Changes to `GrammarMateApp.kt` (UI)

### 6a. Background download indicator

Add a subtle top bar or snackbar when `state.bgTtsDownloading` is true. Show per-language progress:

```kotlin
// In GrammarMateApp(), after the Surface:
AnimatedVisibility(visible = state.bgTtsDownloading) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatBgDownloadText(state.bgTtsDownloadStates),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
```

Helper:
```kotlin
private fun formatBgDownloadText(states: Map<String, DownloadState>): String {
    return states.entries.joinToString(" | ") { (langId, state) ->
        val name = when (langId) { "en" -> "EN"; "it" -> "IT"; else -> langId }
        when (state) {
            is DownloadState.Downloading -> "$name ${state.percent}%"
            is DownloadState.Extracting -> "$name extracting ${state.percent}%"
            is DownloadState.Done -> "$name ready"
            else -> "$name pending"
        }
    }
}
```

This is a single thin bar at the top of the screen. It disappears when all downloads complete.

### 6b. Language-aware TTS speak lambda

Update `onTtsSpeak` to handle the case where the model for the *current* language is not ready (even if the English one is):

```kotlin
val onTtsSpeak: () -> Unit = {
    if (state.ttsState == TtsState.SPEAKING) {
        vm.stopTts()
    } else if (!state.ttsModelReady) {
        // Current language's model not ready -- show download dialog
        showTtsDownloadDialog = true
    } else {
        val text = state.answerText
            ?: state.currentCard?.acceptedAnswers?.firstOrNull()
        if (text != null) vm.onTtsSpeak(text)
    }
}
```

This is unchanged in logic but now `ttsModelReady` reflects the *active language*, not just English.

### 6c. Update download dialog text

The current dialog says "~346 MB". This should now show the size for the active language:

```kotlin
is DownloadState.Idle -> {
    val langName = when (state.selectedLanguageId) {
        "en" -> "English"
        "it" -> "Italian"
        else -> state.selectedLanguageId
    }
    val size = TtsModelRegistry.specFor(state.selectedLanguageId)
        ?.let { "${it.fallbackDownloadSize / (1024 * 1024)} MB" } ?: "model"
    Text("This will download ~$size ($langName pronunciation). Uses internal storage.")
}
```

### 6d. Tapping speaker during background download

If the user taps the speaker icon for a language that is currently being downloaded in the background, show the download dialog with the current progress (reflect `state.bgTtsDownloadStates[selectedLanguageId]`):

```kotlin
val onTtsSpeak: () -> Unit = {
    if (state.ttsState == TtsState.SPEAKING) {
        vm.stopTts()
    } else if (!state.ttsModelReady) {
        // If background download is in progress for this language, show its progress
        val bgState = state.bgTtsDownloadStates[state.selectedLanguageId]
        if (bgState != null && bgState !is DownloadState.Idle) {
            // Copy background download state to dialog state so user sees progress
            vm.setTtsDownloadStateFromBackground(bgState)
        }
        showTtsDownloadDialog = true
    } else {
        val text = state.answerText ?: state.currentCard?.acceptedAnswers?.firstOrNull()
        if (text != null) vm.onTtsSpeak(text)
    }
}
```

---

## 7. File Summary

| File | Change Type | Description |
|------|-------------|-------------|
| `data/TtsModelRegistry.kt` | **NEW** | Model spec registry (language -> URL, files, type) |
| `data/TtsModelManager.kt` | **MODIFY** | Language-aware `isModelReady(lang)`, `download(lang)`, `downloadMultiple()`, parameterized extraction |
| `data/TtsEngine.kt` | **MODIFY** | Language-aware `initialize(lang)`, `speak(lang)`, `buildConfig()` for Kokoro+VITS, track `activeLanguageId` |
| `ui/TrainingViewModel.kt` | **MODIFY** | Background download on init, language-aware speak/check, propagate language to manager |
| `ui/GrammarMateApp.kt` | **MODIFY** | Background download indicator bar, language-aware dialog text, speaker tap during bg download |
| `data/Models.kt` (`TrainingUiState`) | **MODIFY** | Add `bgTtsDownloading`, `bgTtsDownloadStates` fields |

**No new dependencies.** The existing `sherpa-onnx-static-link-onnxruntime-1.12.40.aar` already contains `OfflineTtsVitsModelConfig`.

---

## 8. Key Flows (Pseudocode)

### Flow A: First app launch, user studies Italian

```
1. ViewModel init
   -> checkTtsModel("en") -> false
   -> checkTtsModel("it") -> false
   -> startBackgroundTtsDownload()
   -> bgDownloadJob starts downloading EN first (missing), then IT (missing)
   -> UI shows indicator: "EN 0% | IT pending"

2. User navigates to training, sees Italian lesson
   -> taps speaker icon
   -> ttsModelReady for "it" is false
   -> showTtsDownloadDialog = true
   -> dialog shows current bg download state for IT (e.g., "Downloading... 30%")
   OR if not started yet: "This will download ~65 MB (Italian pronunciation)"
   -> user can dismiss (bg continues) or confirm

3. Background download completes for Italian
   -> ttsModelReady("it") -> true
   -> indicator disappears (or shows "EN 45% | IT ready")

4. User taps speaker with IT model ready
   -> onTtsSpeak(text)
   -> ttsEngine.initialize("it")  // builds VITS config
   -> speak() streams Italian audio
```

### Flow B: Language switch mid-session

```
1. User switches from EN to IT via selectLanguage("it")
   -> ttsModelManager.currentLanguageId = "it"
   -> checkTtsModel() -> checks Italian model
   -> If ready: ttsModelReady = true
   -> If not: ttsModelReady = false (background may still be downloading)

2. User taps speaker
   -> onTtsSpeak(text)
   -> ttsEngine.initialize("it")  // releases EN engine, loads IT engine
   -> speak() with Italian text
```

### Flow C: Background download already running, user triggers dialog

```
1. Background download is at "EN done | IT downloading 50%"
2. User taps speaker for Italian lesson
3. ttsModelReady is false
4. bgTtsDownloadStates["it"] = Downloading(50, ...)
5. ViewModel copies bg state to ttsDownloadState
6. Dialog shows: "Downloading... 50%" with progress bar
7. No duplicate download starts (bgDownloadJob is already active)
```

---

## 9. Edge Cases

| Case | Handling |
|------|----------|
| User switches language while TTS is speaking | `speak()` checks `activeLanguageId` before each call. If different, calls `initialize(newLang)` which releases the old engine first. The current speak job is cancelled via the generation counter. |
| Background download fails for one language | That language's state becomes `Error`. Others continue. User sees "EN ready \| IT failed". Tapping speaker shows error and offers retry. |
| Both models already downloaded | `startBackgroundTtsDownload()` finds no missing languages, returns immediately. No indicator shown. |
| Metered network + background download | Background download skips the metered check (best-effort, non-blocking). Only the user-initiated dialog download checks for metered network. Rationale: the user did not explicitly trigger the background download, so we do not block them with a dialog. If on metered, the download simply proceeds (both models total ~415 MB). This can be revisited if users complain. |
| App killed during extraction | Partial extraction is cleaned up on next launch: `isModelReady()` checks for all required files, returns false. Next `download()` call re-downloads and overwrites. |
| Piper model does not have `voices.bin` | `isModelReady("it")` checks Italian's `requiredFiles` list, which does not include `voices.bin`. English's list does. |
| Language with no registered TTS model | `TtsModelRegistry.specFor()` returns null. `isModelReady()` returns false. `initialize()` logs error and sets ERROR state. Speaker button shows error icon. Graceful degradation. |

---

## 10. Implementation Order

1. **`TtsModelRegistry.kt`** -- new file, pure data, no dependencies
2. **`TtsModelManager.kt`** -- refactor to use registry, add language params
3. **`TtsEngine.kt`** -- add `activeLanguageId`, `buildConfig()`, language-aware `initialize`/`speak`
4. **`TrainingUiState`** in `Models.kt` -- add `bgTtsDownloading`, `bgTtsDownloadStates`
5. **`TrainingViewModel.kt`** -- background download, language-aware methods
6. **`GrammarMateApp.kt`** -- indicator bar, updated dialog, language-aware speak flow
7. **Test** -- `TtsModelRegistry` unit test, `TtsModelManager.isModelReady()` with Italian spec
