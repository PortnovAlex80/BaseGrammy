# TTS Implementation Code Review

**Branch:** feature/tts-sherpa-onnx
**Reviewer:** Claude Code
**Date:** 2026-04-25
**Files reviewed:**
- `app/src/main/java/com/alexpo/grammermate/data/TtsModelManager.kt`
- `app/src/main/java/com/alexpo/grammermate/data/TtsEngine.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` (TTS changes)
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` (TTS changes)
- `app/build.gradle.kts`
- `docs/TTS_DESIGN.md` (for comparison)

---

## CRITICAL Issues (must fix before merge)

### C1. Thread safety: `currentTrack` read/write race in TtsEngine

`TtsEngine.stop()` is called from the main thread. `TtsEngine.speak()` writes `currentTrack` on `Dispatchers.Default`. The `currentTrack` field is a plain `var` with no synchronization.

```kotlin
// speak() writes on Dispatchers.Default:
currentTrack = audioTrack       // line 108

// stop() reads on main thread:
currentTrack?.let {             // line 141
    try { it.stop() } catch (_: IllegalStateException) {}
}
```

There is a race window: `stop()` reads `currentTrack` while `speak()` is between creating the `AudioTrack` and assigning it to `currentTrack`. Worse, `stop()` could call `AudioTrack.stop()` on a track that `speak()` is about to write to on another thread, producing undefined behavior in the native audio layer.

**Fix:** Make `currentTrack` `@Volatile` or use an `AtomicReference<AudioTrack>`. Also consider wrapping the `stop()` call in a try/catch more defensively, or use a `Mutex` to serialize access.

### C2. `speak()` calls `stop()` then resets `isStopped` without waiting for previous coroutine to finish

In `TtsEngine.speak()`:

```kotlin
stop()                           // line 77
isStopped.set(false)             // line 78
```

If a previous `speak()` coroutine is still running on `Dispatchers.Default`, the `isStopped.set(false)` unblocks it immediately after `stop()` signals it to stop. The old coroutine's `finally` block will then try to `release()` the `AudioTrack` that the new `speak()` may have already created and assigned to `currentTrack`.

This is a classic cancel-then-start race. The old coroutine's finally block:

```kotlin
finally {
    audioTrack.stop()
    audioTrack.release()
    currentTrack = null          // NULIFIES the new speak()'s track!
}
```

The old coroutine can set `currentTrack = null` after the new speak() has assigned its own track, causing a null reference in the new speak's callback.

**Fix:** Use a `Job` reference to track the active speak coroutine, cancel it properly with `join()`, or use a `Mutex` to ensure only one `speak()` runs at a time. Alternatively, give each `speak()` its own local `AudioTrack` reference (not shared via `currentTrack`) and use a generation counter or job cancellation to prevent stale cleanup.

### C3. No cleanup of partial model files on download failure

When the download fails partway through (network error, user cancels), the archive file in `cacheDir` is not deleted. On the next attempt, the code creates a new connection and overwrites it, but if extraction starts on a partial download, it would fail.

More critically, in `download()`:

```kotlin
val dir = modelDir
dir.mkdirs()                     // line 59 -- creates output dir BEFORE download starts
```

If the download fails, `modelDir` exists but is empty. `isModelReady()` would return false (correct), but the empty directory wastes no space. However, on extraction failure, the code does `dir.deleteRecursively()` (line 129) which is correct. The archive file itself is only cleaned up on success (line 135) or extraction failure, but NOT on download failure.

**Fix:** In the download catch block, delete the partial archive file. Also move `dir.mkdirs()` to after successful download, before extraction starts.

---

## MAJOR Issues (should fix)

### M1. Audio focus not managed -- TTS and SoundPool play simultaneously

The app uses `SoundPool` with `USAGE_MEDIA` for correct/incorrect sounds. TTS uses `AudioTrack` with `USAGE_MEDIA` + `CONTENT_TYPE_SPEECH`. Neither requests audio focus.

If the user taps the speaker button right after answering a question, the TTS audio will overlap with the SoundPool feedback tone. This sounds bad and confuses the user.

**Fix:** Request `AudioManager.requestAudioFocus()` before TTS playback, or add a short delay (e.g., 500ms) after the feedback sound before TTS can start. At minimum, stop any playing SoundPool sounds before starting TTS.

### M2. TTS engine eagerly initialized on app start if model exists

In `TrainingViewModel.init`:

```kotlin
checkTtsModel()                  // line 227
```

And `checkTtsModel()` sets `ttsModelReady = true` but does NOT initialize the engine (good). However, when user taps speak in `onTtsSpeak()`:

```kotlin
if (ttsEngine.state.value != TtsState.READY) {
    ttsEngine.initialize()       // initializes on first tap
}
```

This is correct per the design doc. BUT `startTtsDownload()` auto-initializes after download completes (line 1732). On a fresh install, the user downloads the model, and then initialization starts automatically even if they dismiss the dialog. This is fine UX but adds ~500MB native memory pressure immediately.

The design doc says "Do NOT auto-initialize on app start" but the download flow auto-initializes after download. This is acceptable but should be documented.

### M3. No disk space check before downloading ~346MB model

`TtsModelManager.download()` does not check available storage before starting. On devices with <500MB free internal storage, the download could succeed but extraction could fail due to insufficient space (tar.bz2 + extracted files need ~700MB temporarily).

**Fix:** Before starting download, check `StatFs` on `context.filesDir` for available bytes. Show an error if less than, say, 700MB free.

### M4. Model download runs on unspecified dispatcher

In `TrainingViewModel.startTtsDownload()`:

```kotlin
viewModelScope.launch {                                     // Main dispatcher
    ttsModelManager.download().collect { ... }
}
```

`TtsModelManager.download()` uses `flow { ... }` which runs on the collector's dispatcher (Main). The heavy work (HTTP download, tar.bz2 extraction) blocks the main thread.

The flow builder performs network I/O and file I/O on whatever dispatcher collects it. Since `viewModelScope.launch` defaults to `Dispatchers.Main`, this is main-thread blocking work.

**Fix:** Change to `viewModelScope.launch(Dispatchers.IO)` or use `flowOn(Dispatchers.IO)` inside `TtsModelManager.download()`.

### M5. TtsSpeakerButton always calls onClick() even when speaking

In `TtsSpeakerButton`:

```kotlin
IconButton(
    onClick = {
        if (ttsState == TtsState.SPEAKING) {
            // Handled by parent calling stopTts
            // BUT onClick() is still called after this!
        }
        onClick()               // Always called!
    },
```

When `ttsState == TtsState.SPEAKING`, the comment says "handled by parent" but `onClick()` is called unconditionally afterward. In the TrainingScreen context, `onTtsSpeak` lambda handles this correctly (checks `state.ttsState == TtsState.SPEAKING` and calls `vm.stopTts()`), but the button itself does not short-circuit. This is redundant but not harmful. Still, it is misleading.

**Fix:** Add `return@IconButton` after the speaking check, or restructure to `if (speaking) stopTts() else onClick()`.

### M6. No metered network warning before 346MB download

The download dialog shows the size but does not check if the user is on a metered (cellular) connection. Downloading 346MB on cellular data without warning is a poor user experience and may cost the user money.

**Fix:** Check `ConnectivityManager.isActiveNetworkMetered()` before starting download. If metered, show an additional warning in the dialog.

---

## MINOR Issues (nice to fix)

### m1. Hardcoded model path and URL in multiple places

The model directory path `"tts/kokoro-en-v0_19"` is hardcoded in:
- `TtsModelManager.modelDir` (line 28)
- `TtsModelManager.isModelReady()` (line 49 via `modelDir`)
- `TtsEngine.initialize()` (line 49)
- `TtsModelManager.extractTarBz2()` prefix strip (line 153)

If the model version changes, all must be updated. The URL is in `TtsModelManager.companion` (good) but the path string should be a constant too.

**Fix:** Extract to a shared constant, or pass the model directory to `TtsEngine` as a parameter.

### m2. `numThreads = 4` hardcoded in TtsEngine

```kotlin
numThreads = 4,                  // line 58
```

On low-end devices with 2-4 cores, using 4 threads for TTS may starve the UI. On high-end devices with 8+ cores, 4 may be suboptimal.

**Fix:** Use `Runtime.getRuntime().availableProcessors()` and cap at a reasonable number (e.g., `minOf(availableProcessors, 4)`).

### m3. Download progress emits on every 1% change -- potential over-emission

The download loop emits `DownloadState.Downloading` on every 1% change. For a 346MB file, that is at most 100 emissions during download -- acceptable. But the extraction loop also emits on every 1%, based on compressed bytes read against total archive size. Since extraction ratio varies, the percentage may not be smooth.

This is minor -- the UX is acceptable.

### m4. `TtsEngine` takes `Context` but only uses `context.filesDir`

`TtsEngine` constructor takes `Context` but only uses it to build the model directory path. It could instead take a `File` (the model directory) as a parameter, making it more testable and removing the Android dependency.

### m5. `TtsState` and `DownloadState` defined in `data/` but are UI-adjacent

`TtsState` and `DownloadState` are in `data/` package which is fine per the architecture rules (they are state models, not UI). However, `DownloadState` is a sealed class used exclusively in UI state (`TrainingUiState.ttsDownloadState`). This is acceptable since the data layer emits it and UI consumes it.

### m6. No ProGuard rules for sherpa-onnx native methods

The AAR contains native code accessed via JNI. If ProGuard/R8 is enabled for release builds (currently `isMinifyEnabled = false`), the native method names could be obfuscated. Since minification is off, this is not currently a problem, but should be addressed before enabling minification.

### m7. VocabSprintScreen TTS speaker uses `state.ttsModelReady` for enabled but does not handle download trigger

In `VocabSprintScreen` (GrammarMateApp.kt line 1292-1296):

```kotlin
TtsSpeakerButton(
    ttsState = state.ttsState,
    enabled = state.ttsModelReady,      // disabled if model not downloaded
    onClick = { onSpeak(answer) }
)
```

When the model is not downloaded, the button is disabled (grayed out). The user has no way to know WHY it is disabled or to trigger the download from here. In contrast, the TrainingScreen's speaker button shows the download dialog.

**Fix:** Either make the VocabSprintScreen speaker trigger the download dialog like TrainingScreen does, or add a tooltip explaining why it is disabled.

### m8. Screen rotation during TTS playback

Since `TtsEngine` lives in `TrainingViewModel` which survives configuration changes, playback continues through rotation. The `AudioTrack` reference is preserved. However, the UI recomposes and `TtsSpeakerButton` will show the SPEAKING state correctly since it observes `ttsState` from the ViewModel. This is handled correctly -- no issue here.

---

## APPROVED Items (what looks good)

### A1. Layer boundaries are respected

`TtsEngine` and `TtsModelManager` are in `data/` with no Android UI dependencies (only `Context` for file paths). The ViewModel owns the instances and exposes state to UI. This matches the existing pattern (`MasteryStore`, `ProgressStore`, etc. all take `Context` and live in `data/`).

### A2. ViewModel integration is clean and minimal

The TTS additions to `TrainingViewModel` are well-contained:
- `checkTtsModel()` (line 1738) -- simple synchronous check
- `onTtsSpeak()` (line 1716) -- clean launch + init + speak
- `startTtsDownload()` (line 1726) -- collects download flow
- `stopTts()` (line 1743) -- simple delegation
- `onCleared()` (line 1747) -- releases engine

These ~40 lines of TTS code do not bloat the ViewModel's conceptual surface area. Each method does one thing.

### A3. Native resource lifecycle is properly managed

`TtsEngine.release()` calls `offlineTts?.free()` which frees the native ONNX model. `onCleared()` calls `release()`. Each `speak()` creates a fresh `AudioTrack` and releases it in `finally`. This is correct.

### A4. Download cancellation works via coroutine cancellation

`TtsModelManager.download()` checks `isActive` in both the download loop (line 98) and extraction loop (line 150). Cancelling the collecting coroutine cancels the download.

### A5. Redirect handling for GitHub CDN

The manual redirect following (lines 70-84) handles GitHub's CDN redirects correctly with a redirect limit of 5.

### A6. `isStopped` uses AtomicBoolean correctly

The `AtomicBoolean` for `isStopped` provides the right memory visibility for the cross-thread communication between `stop()` (main thread) and the streaming callback (native thread).

### A7. Error handling in download flow is solid

- Network errors caught and emitted as `DownloadState.Error`
- Extraction errors caught, partial files cleaned up (`dir.deleteRecursively()`)
- Both error states are shown in the download dialog with retry opportunity

### A8. Design doc accurately reflects implementation

The implementation matches the design document (`docs/TTS_DESIGN.md`) closely. The public API, file layout, and integration points all align. Minor deviations (auto-init after download, no resume support) are acceptable.

### A9. Gradle dependency is minimal

Only two new dependencies:
- `sherpa-onnx` AAR (local, static-linked ONNX Runtime)
- `commons-compress` for tar.bz2 extraction (~500KB)

No transitive dependency chain. This is a clean addition.

### A10. minSdk 24 compatibility confirmed

`ENCODING_PCM_FLOAT` is API 21+. `AudioTrack.Builder` is API 21+. `HttpURLConnection` is available from API 1. All APIs used are compatible with minSdk 24.

---

## Summary

| Category | Count |
|----------|-------|
| CRITICAL | 3 |
| MAJOR | 6 |
| MINOR | 8 |
| APPROVED | 10 |

**Verdict: Changes Requested.** The three critical issues (thread safety race in `currentTrack`, speak/stop race in `TtsEngine`, and main-thread blocking download) must be fixed before merge. The major issues (audio focus, disk space check, metered network warning) are important for user experience but could be addressed in a follow-up if needed.

The overall architecture is sound and follows existing project patterns. The code quality is good. With the critical fixes, this will be a solid addition.
