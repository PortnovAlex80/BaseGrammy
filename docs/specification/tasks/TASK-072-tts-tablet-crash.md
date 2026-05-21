# TASK-072: TTS Tablet Crash — Shared Singleton Sherpa-ONNX Race Condition

**Status:** OPEN
**Created:** 2026-05-21
**Branch:** fix/tts-tablet-crash (from main)
**Priority:** HIGH
**Complexity:** COMPLEX
**UC:** UC-03 (Voice input), UC-04 (Keyboard input), UC-22 (Verb drill)
**Scenario:** scenario-04-training-card-session.md, scenario-08-verb-drill.md

---

## Problem Statement

### Bug: App crashes on tablets during TTS operations with SIGSEGV

**Affected Devices:** Primarily tablets (especially Samsung Galaxy Tab)
**Crash Type:** Native SIGSEGV (segmentation fault)
**Trigger Conditions:** Rapid navigation between screens, concurrent TTS access

### Current Broken Behavior

1. User opens TrainingCardSession and taps TTS speaker icon
2. User rapidly navigates to VerbDrill screen
3. VerbDrill attempts to use TTS while TrainingCardSession is releasing
4. **CRASH:** SIGSEGV in native Sherpa-ONNX code
5. App terminates with no graceful error handling

### Expected Behavior

- Multiple screens can safely use TTS without crashing
- TTS lifecycle transitions (init/release) are atomic
- Native resource access is synchronized
- Failed TTS operations degrade gracefully, not crash

---

## Root Cause Analysis (Multi-Agent Perspective)

### 1. Architecture Agent Findings

**Root Cause:** Shared singleton TTS pattern with concurrent access

**Current Implementation:**
```kotlin
// TtsProvider.kt - Singleton pattern
class TtsProvider private constructor(application: Application) {
    val ttsEngine: TtsEngine = TtsEngine(application)

    companion object {
        @Volatile
        private var instance: TtsProvider? = null

        fun getInstance(application: Application): TtsProvider {
            return instance ?: synchronized(this) {
                instance ?: TtsProvider(application).also { instance = it }
            }
        }
    }
}
```

**Problem:** Multiple screens share ONE `TtsEngine` instance:
- `TrainingCardSession` → `AudioCoordinator.ttsEngine` → `TtsProvider.getInstance()`
- `VerbDrillViewModel` → `AudioCoordinator.ttsEngine` → `TtsProvider.getInstance()`
- `VocabDrillViewModel` → `AudioCoordinator.ttsEngine` → `TtsProvider.getInstance()`

**Native Resource:** Sherpa-ONNX `VoiceOfflineEmail` holds C++ resources that are freed in `doRelease()`:
```kotlin
// TtsEngine.kt
private var offlineTts: OfflineTts? = null

private fun doRelease() {
    val ttsToFree = offlineTts
    offlineTts = null
    activeLanguageId = null
    initFailed = false
    _state.value = TtsState.Idle
    speakJob?.cancel()
    ttsToFree?.free()  // ← Native C++ resource freed here
}
```

**Race Condition:**
- Screen 1 calls `release()` → `doRelease()` → `ttsToFree?.free()` (async)
- Screen 2 calls `initialize()` → `doInitialize()` → accesses `offlineTts`
- **Use-after-free:** Screen 2 accesses freed native resources → SIGSEGV

### 2. Threading Agent Findings

**Threading Model:** Coroutine-based with `Dispatchers.IO` for file I/O

**Synchronization Gaps:**

1. **TtsEngine has internal Mutex BUT:**
```kotlin
// TtsEngine.kt
private val mutex = Mutex()

suspend fun initialize(languageId: String = "en"): Unit = mutex.withLock {
    // Protected initialization
}

suspend fun speak(text: String, languageId: String = "en", ...): Unit = mutex.withLock {
    // Protected speaking
}
```

2. **doRelease() is NOT protected by Mutex:**
```kotlin
private fun doRelease() {
    val ttsToFree = offlineTts
    offlineTts = null
    activeLanguageId = null
    initFailed = false
    _state.value = TtsState.Idle
    speakJob?.cancel()
    ttsToFree?.free()  // ← NOT protected by mutex!
}
```

3. **AudioCoordinator has ttsMutex BUT:**
```kotlin
// AudioCoordinator.kt
private val ttsMutex = Mutex()

fun onTtsSpeak(text: String, speed: Float? = null) {
    coroutineScope.launch {
        ttsMutex.withLock {
            // Protected speak
        }
    }
}

fun stopTts() {
    ttsEngine.stop()  // ← NOT protected by ttsMutex!
}

fun release() {
    ttsEngine.release()  // ← NOT protected by ttsMutex!
}
```

**Concurrent Scenarios:**

| Scenario | Thread 1 | Thread 2 | Result |
|----------|----------|----------|--------|
| Rapid navigation | Screen 1: `release()` → `doRelease()` (async free) | Screen 2: `initialize()` → `doInitialize()` | Use-after-free |
| Language switch | Coroutine 1: `doRelease()` → `free()` | Coroutine 2: `speak()` → `generateWithConfig()` | SIGSEGV |
| Screen transition | `AudioCoordinator.stopTts()` (no mutex) | `AudioCoordinator.onTtsSpeak()` (mutex protected) | Race condition |

### 3. Error Handling Agent Findings

**Exception Gaps:**

1. **ViewModels catch `Exception` only (not `Throwable`):**
```kotlin
// VerbDrillViewModel.kt (line ~613)
try {
    ttsEngine.speak(text, languageId, speed = speed)
} catch (e: Exception) {  // ← Doesn't catch Throwable!
    Log.e(TAG, "TTS failed", e)
}
```

2. **Native crashes bypass Kotlin exception handling:**
- `SIGSEGV` (segmentation fault) → NOT caught by `catch (e: Exception)`
- `OutOfMemoryError` → NOT caught (extends `Error`, not `Exception`)
- `UnsatisfiedLinkError` → NOT caught (extends `Error`, not `Exception`)

3. **TtsEngine catches Throwable internally but:**
```kotlin
// TtsEngine.kt
suspend fun initialize(languageId: String = "en"): Unit = mutex.withLock {
    withContext(Dispatchers.Default) {
        try {
            // ...
        } catch (e: Throwable) {  // ← Caught here
            initFailed = true
            offlineTts = null
            _state.value = TtsState.Error(reason)
        }
    }
}
```

**Problem:** Native crashes during `speak()` or `free()` happen OUTSIDE try-catch:
```kotlin
speakJob = ttsScope.launch {
    tts.generateWithConfigAndCallback(  // ← Native crash here, not caught
        text = text,
        config = GenerationConfig(...),
        callback = { ... }
    )
}
```

**Error State Gaps:**

1. **Failed initialization leaves state undefined:**
```kotlin
// TtsEngine.kt
@Volatile
private var initFailed = false

suspend fun speak(text: String, ...) {
    if (initFailed) return  // ← Silently fails!
    // ...
}
```

2. **No recovery mechanism for ERROR state:**
```kotlin
sealed class TtsState {
    object Idle : TtsState()
    object Initializing : TtsState()
    object Ready : TtsState()
    object Speaking : TtsState()
    data class Error(val reason: String? = null) : TtsState()  // ← Dead end
}
```

**User Impact:**
- No graceful degradation
- App terminates immediately
- No error message to user
- User progress lost

---

## Acceptance Criteria

### AC1: TTS lifecycle is thread-safe
**Given:** Multiple screens access TTS concurrently
**When:** Screen 1 releases TTS while Screen 2 initializes
**Then:** No SIGSEGV crash
**And:** Lifecycle transitions are atomic (mutex-protected)
**And:** Native resources are never accessed while being freed

### AC2: Per-screen TTS instances prevent cross-screen interference
**Given:** TrainingCardSession and VerbDrill are both active
**When:** TrainingCardSession uses TTS then VerbDrill uses TTS
**Then:** Each screen manages its own TTS instance
**And:** Releasing one screen's TTS doesn't affect other screens
**And:** No shared singleton contention

### AC3: Comprehensive error handling catches all Throwables
**Given:** TTS operation fails with native error (OutOfMemoryError, SIGSEGV)
**When:** Error occurs during `speak()`, `initialize()`, or `release()`
**Then:** Error is caught by `catch (e: Throwable)`
**And:** UI shows graceful error message
**And:** App continues running (no crash)

### AC4: Fallback UI for TTS failures
**Given:** TTS fails to initialize or crashes during playback
**When:** User attempts to use TTS speaker icon
**Then:** Speaker icon shows error state (e.g., grayed out with tooltip)
**And:** User can continue using app without TTS
**And:** Error is logged for debugging

---

## Implementation Plan

### Phase 1: Thread-safe TTS lifecycle (Mutex protection)

**Objective:** Prevent concurrent native resource access

**Files:**
- `app/src/main/java/com/alexpo/grammermate/data/TtsEngine.kt`

**Changes:**

1. **Make `doRelease()` mutex-protected:**
```kotlin
private fun doRelease() {
    // ← REMOVE this method (inline into release())
}

fun release() {
    mutex.withLock {  // ← Add mutex protection
        val ttsToFree = offlineTts
        offlineTts = null
        activeLanguageId = null
        initFailed = false
        _state.value = TtsState.Idle

        val oldJob = speakJob
        speakJob?.cancel()
        oldJob?.join()  // ← Wait for speak to complete

        ttsToFree?.free()  // ← Safe: no other coroutine can access
    }
}
```

2. **Add `doStop()` mutex protection:**
```kotlin
private fun doStop() {
    mutex.withLock {  // ← Add mutex protection
        isStopped.set(true)
        currentTrack?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {}
        }
    }
}
```

3. **Verify `initialize()` and `speak()` are mutex-protected:**
```kotlin
// Already protected, but verify:
suspend fun initialize(languageId: String = "en"): Unit = mutex.withLock { ... }
suspend fun speak(...): Unit = mutex.withLock { ... }
```

**Verification:**
- Unit test: Concurrent `initialize()` + `release()` calls
- Unit test: Concurrent `speak()` + `release()` calls
- Manual test: Rapid language switching on tablet

### Phase 2: Per-screen TTS instances (remove singleton)

**Objective:** Eliminate cross-screen interference

**Files:**
- `app/src/main/java/com/alexpo/grammermate/shared/audio/AudioCoordinator.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingCardSession.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillViewModel.kt`

**Changes:**

1. **Remove singleton pattern from TtsProvider:**
```kotlin
// BEFORE (singleton):
class TtsProvider private constructor(application: Application) {
    val ttsEngine: TtsEngine = TtsEngine(application)

    companion object {
        @Volatile
        private var instance: TtsProvider? = null

        fun getInstance(application: Application): TtsProvider {
            return instance ?: synchronized(this) {
                instance ?: TtsProvider(application).also { instance = it }
            }
        }
    }
}

// AFTER (factory):
class TtsProvider private constructor(
    private val application: Application
) {
    fun createEngine(): TtsEngine = TtsEngine(application)

    companion object {
        @Volatile
        private var instance: TtsProvider? = null

        fun getInstance(application: Application): TtsProvider {
            return instance ?: synchronized(this) {
                instance ?: TtsProvider(application).also { instance = it }
            }
        }
    }
}
```

2. **Create per-screen TTS engines in AudioCoordinator:**
```kotlin
// AudioCoordinator.kt
class AudioCoordinator(
    private val stateAccess: TrainingStateAccess,
    private val appContext: Application,
    private val coroutineScope: CoroutineScope,
    private val configStore: AppConfigStore,
    // ...
) {
    // BEFORE: Shared singleton
    val ttsEngine = ttsEngineProvider(appContext)

    // AFTER: Per-screen engines
    private val ttsProvider = TtsProvider.getInstance(appContext)
    val trainingTtsEngine = ttsProvider.createEngine()  // For TrainingCardSession
    val verbDrillTtsEngine = ttsProvider.createEngine()  // For VerbDrill
    val vocabDrillTtsEngine = ttsProvider.createEngine()  // For VocabDrill
}
```

3. **Route TTS calls to correct engine:**
```kotlin
fun onTtsSpeak(text: String, screen: Screen, speed: Float? = null) {
    val engine = when (screen) {
        Screen.TRAINING -> trainingTtsEngine
        Screen.VERB_DRILL -> verbDrillTtsEngine
        Screen.VOCAB_DRILL -> vocabDrillTtsEngine
    }

    coroutineScope.launch {
        ttsMutex.withLock {
            try {
                if (engine.state.value != TtsState.Ready
                    || engine.activeLanguageId != langId.value
                ) {
                    engine.initialize(langId.value)
                }
                if (engine.state.value == TtsState.Ready) {
                    engine.speak(text, languageId = langId.value, speed = effectiveSpeed)
                }
            } catch (e: Exception) {
                Log.e(TAG, "onTtsSpeak failed", e)
            }
        }
    }
}
```

**Verification:**
- Manual test: TrainingCardSession TTS → VerbDrill TTS (no interference)
- Manual test: Rapid screen switching (no crashes)
- Memory test: Verify engines are released when screens are destroyed

### Phase 3: Comprehensive error handling (catch Throwable)

**Objective:** Catch all errors including native crashes

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillViewModel.kt`
- `app/src/main/java/com/alexpo/grammermate/shared/audio/AudioCoordinator.kt`

**Changes:**

1. **Change `catch Exception` to `catch Throwable` in ViewModels:**
```kotlin
// VerbDrillViewModel.kt (line ~613)
fun playTts() {
    viewModelScope.launch {
        try {
            ttsEngine.speak(text, languageId, speed = speed)
        } catch (e: Throwable) {  // ← Changed from Exception to Throwable
            Log.e(TAG, "TTS failed", e)
            _uiState.update { it.copy(
                ttsError = e.message ?: "TTS playback failed"
            )}
        }
    }
}
```

2. **Add try-catch in AudioCoordinator:**
```kotlin
fun onTtsSpeak(text: String, speed: Float? = null) {
    if (text.isBlank()) return
    if (ttsEngine.state.value !in listOf(TtsState.Ready, TtsState.Idle)) return

    val langId = stateAccess.uiState.value.navigation.selectedLanguageId
    val effectiveSpeed = speed ?: _audioState.value.ttsSpeed

    coroutineScope.launch {
        ttsMutex.withLock {
            try {
                if (ttsEngine.state.value != TtsState.Ready
                    || ttsEngine.activeLanguageId != langId.value
                ) {
                    ttsEngine.initialize(langId.value)
                }
                if (ttsEngine.state.value == TtsState.Ready) {
                    ttsEngine.speak(text, languageId = langId.value, speed = effectiveSpeed)
                }
            } catch (e: Throwable) {  // ← Changed from Exception to Throwable
                Log.e(TAG, "onTtsSpeak failed", e)
                _audioState.update { it.copy(
                    ttsError = e.message ?: "TTS playback failed"
                )}
            }
        }
    }
}
```

3. **Add try-catch in TtsEngine native callbacks:**
```kotlin
// TtsEngine.kt
speakJob = ttsScope.launch {
    _state.value = TtsState.Speaking
    requestAudioFocus()

    try {
        tts.generateWithConfigAndCallback(  // ← Wrap in try-catch
            text = text,
            config = GenerationConfig(sid = speakerId, speed = safeSpeed),
            callback = { samples ->
                try {  // ← Inner try-catch for callback
                    if (!isStopped.get()) {
                        audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                        1
                    } else {
                        0
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "AudioTrack write failed", e)
                    0
                }
            }
        )
    } catch (e: Throwable) {  // ← Catch native crashes
        if (e is kotlinx.coroutines.CancellationException) throw e
        Log.e(TAG, "TTS generation failed", e)
        _state.value = TtsState.Error(e.message ?: "Playback failed")
    } finally {
        // ... cleanup ...
    }
}
```

**Verification:**
- Unit test: `OutOfMemoryError` during TTS init → graceful error
- Unit test: `UnsatisfiedLinkError` during TTS speak → graceful error
- Manual test: Low-memory tablet TTS → error message, no crash

### Phase 4: Fallback UI for TTS failures

**Objective:** Provide graceful degradation when TTS fails

**Files:**
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingCardSession.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillScreen.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillScreen.kt`
- `app/src/main/java/com/alexpo/grammermate/data/AudioState.kt`

**Changes:**

1. **Add TTS error state to AudioState:**
```kotlin
// AudioState.kt
data class AudioState(
    // ... existing fields ...
    val ttsError: String? = null,  // ← Add error message
    val ttsAvailable: Boolean = true  // ← Add availability flag
)
```

2. **Update UI to show error state:**
```kotlin
// TrainingCardSession.kt
@Composable
fun TrainingCardSession(...) {
    val ttsError = audioState.ttsError
    val ttsAvailable = audioState.ttsAvailable

    Row {
        if (ttsAvailable) {
            IconButton(onClick = { onTtsSpeak(card.sentence) }) {
                Icon(
                    imageVector = Icons.Outlined.VolumeUp,
                    contentDescription = "Speak",
                    tint = if (ttsError != null) Color.Gray else MaterialTheme.colorScheme.primary
                )
            }
        } else {
            // TTS unavailable - show alternative
            Text(
                text = "TTS unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        if (ttsError != null) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip {
                        Text(ttsError)
                    }
                },
                state = rememberTooltipState()
            ) {
                // Show warning icon
            }
        }
    }
}
```

3. **Add recovery mechanism:**
```kotlin
// AudioCoordinator.kt
fun clearTtsError() {
    _audioState.update { it.copy(
        ttsError = null,
        ttsAvailable = true
    )}
}

fun retryTtsInitialization() {
    coroutineScope.launch {
        try {
            ttsEngine.initialize(stateAccess.uiState.value.navigation.selectedLanguageId.value)
            if (ttsEngine.state.value == TtsState.Ready) {
                clearTtsError()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "TTS retry failed", e)
        }
    }
}
```

**Verification:**
- Manual test: TTS init failure → speaker icon grayed out + tooltip
- Manual test: TTS crash during playback → error message + app continues
- Manual test: TTS unavailable → user can still complete training

---

## Verification Checklist

Before marking task complete, verify:

**Phase 1: Thread-safe TTS lifecycle**
- [ ] `doRelease()` is mutex-protected
- [ ] `doStop()` is mutex-protected
- [ ] `initialize()` and `speak()` are mutex-protected
- [ ] Unit test: Concurrent `initialize()` + `release()` passes
- [ ] Unit test: Concurrent `speak()` + `release()` passes
- [ ] Manual test: Rapid language switching on tablet (no crash)

**Phase 2: Per-screen TTS instances**
- [ ] TtsProvider singleton removed
- [ ] AudioCoordinator has per-screen TTS engines
- [ ] TTS calls routed to correct engine per screen
- [ ] Manual test: TrainingCardSession TTS → VerbDrill TTS (no interference)
- [ ] Manual test: Rapid screen switching (no crashes)
- [ ] Memory test: Engines released when screens destroyed

**Phase 3: Comprehensive error handling**
- [ ] VerbDrillViewModel catches `Throwable` (not just `Exception`)
- [ ] VocabDrillViewModel catches `Throwable` (not just `Exception`)
- [ ] AudioCoordinator catches `Throwable` (not just `Exception`)
- [ ] TtsEngine native callbacks wrapped in try-catch
- [ ] Unit test: `OutOfMemoryError` → graceful error
- [ ] Unit test: `UnsatisfiedLinkError` → graceful error
- [ ] Manual test: Low-memory tablet TTS → error message, no crash

**Phase 4: Fallback UI**
- [ ] `AudioState` has `ttsError` and `ttsAvailable` fields
- [ ] TrainingCardSession shows error state (grayed icon + tooltip)
- [ ] VerbDrillScreen shows error state
- [ ] VocabDrillScreen shows error state
- [ ] Manual test: TTS init failure → speaker icon grayed out
- [ ] Manual test: TTS crash during playback → error message + app continues
- [ ] Manual test: TTS unavailable → user can complete training

**Cross-cutting concerns**
- [ ] All 4 phases complete
- [ ] Build: `assembleDebug` passes
- [ ] Tests: `test` passes (no regressions)
- [ ] Manual test: Tablet rapid navigation (no crashes)
- [ ] Manual test: Phone TTS still works (no regression)

---

## Scope Boundaries

**DO NOT touch:**
- ASR (speech recognition) — separate engine
- Daily practice logic
- SessionRunner or card flow
- UI composables (except for TTS error state display)
- TTS model downloading or management

**Focus ONLY on:**
- Thread-safe TTS lifecycle in TtsEngine
- Per-screen TTS instances in AudioCoordinator
- Error handling in ViewModels and AudioCoordinator
- Fallback UI for TTS failures

---

## Regression Plan

After all fixes are implemented, run:

1. **Build:** `assembleDebug` - must pass with no errors
2. **Tests:** `test` - must pass, no new failures
3. **Per-phase verification:** check each item from the Verification Checklist above
4. **Cross-screen regression:** verify TTS works on all screens (Training, VerbDrill, VocabDrill)
5. **Device regression:** test on phone and tablet (if available)
6. **UC/AC spot-check:** read UC-03, UC-04, UC-22 from `22-use-case-registry.md`, confirm ACs hold
7. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

---

## References

**Source Files:**
- `app/src/main/java/com/alexpo/grammermate/data/TtsEngine.kt` - TTS engine with native resources
- `app/src/main/java/com/alexpo/grammermate/data/TtsProvider.kt` - Singleton provider (to be removed)
- `app/src/main/java/com/alexpo/grammermate/shared/audio/AudioCoordinator.kt` - TTS coordinator
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt` - Verb drill TTS usage
- `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillViewModel.kt` - Vocab drill TTS usage
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingCardSession.kt` - Training card TTS usage

**Documentation:**
- `docs/specification/05-audio-tts-asr.md` - TTS architecture spec
- `docs/specification/22-use-case-registry.md` - UC-03 (Voice input), UC-04 (Keyboard input), UC-22 (Verb drill)
- `docs/specification/scenario-04-training-card-session.md` - Training card flows
- `docs/specification/scenario-08-verb-drill.md` - Verb drill flows

**Related Tasks:**
- **DONE-TASK-003:** TTS thread safety - added internal mutex to TtsEngine
- **DONE-TASK-005:** TTS error icon memory - fixed OOM during TTS init

---

## Complexity Assessment

**COMPLEX** - Multi-layer concurrency refactor with native resource management

**Change Scope:**
- Phase 1: Thread safety - ~30 lines (mutex protection)
- Phase 2: Per-screen instances - ~50 lines (factory pattern, routing)
- Phase 3: Error handling - ~40 lines (catch Throwable, error state)
- Phase 4: Fallback UI - ~60 lines (error state, UI updates)
- Total: ~180 lines of code

**Risk Level:** HIGH
- Changes affect native resource lifecycle (critical for stability)
- Requires deep understanding of Kotlin coroutines and mutex synchronization
- Native crashes are hard to debug and test
- Tablet-specific issue (hard to reproduce without device)
- Memory impact from multiple TTS engines

**Why COMPLEX:**
- Requires coordination across 4 layers (TtsEngine, TtsProvider, AudioCoordinator, ViewModels)
- Involves native code (Sherpa-ONNX) with limited debugging options
- Concurrency bugs are non-deterministic and hard to reproduce
- Requires testing on actual tablets (not easily automated)
- Changes architectural pattern (singleton → per-screen instances)

**Why not MODERATE:**
- Not just adding synchronization (already partially present)
- Not just error handling (requires UI changes)
- Architectural change (singleton removal)
- Native resource management (critical for app stability)

---

## Notes

**Why this wasn't caught earlier:**
- Development mostly on phones (tablets have stricter audio HAL)
- Concurrency bugs are non-deterministic (depend on timing)
- Native crashes bypass Kotlin exception handling
- No integration tests for concurrent TTS usage

**Prevention for future:**
- Add integration test: `testConcurrentTtsAccess()`
- Add integration test: `testRapidScreenSwitching()`
- Add tablet to test devices (if possible)
- Document TTS lifecycle pattern in `05-audio-tts-asr.md`
- Add strict mode for concurrent access detection

**Thread Safety Pattern:**
This task establishes the pattern for native resource lifecycle:
1. **Mutex protection** for ALL native resource access (init/release/use)
2. **Per-instance resources** (not shared singletons) when concurrency is needed
3. **Catch Throwable** (not just Exception) for native errors
4. **Graceful degradation** (fallback UI) when resources fail

Future features using native resources should follow this pattern.

**Memory Considerations:**
- Per-screen TTS engines increase memory usage
- Each Sherpa-ONNX instance is ~50-100MB
- Mitigation: Release engines when screens are destroyed
- Mitigation: Lazy initialization (only create when needed)
- Trade-off: Stability > memory (crashes are worse than memory usage)

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Phase 1: Thread-safe TTS lifecycle | | |
| | Phase 2: Per-screen TTS instances | | |
| | Phase 3: Comprehensive error handling | | |
| | Phase 4: Fallback UI | | |
