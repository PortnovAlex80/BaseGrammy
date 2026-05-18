# TASK-072: TTS Tablet Crash — Shared Singleton Race + Native Free

**Status:** OPEN
**Created:** 2026-05-18
**Branch:** feature/tts-tablet-crash (from develop)
**Spec:** 05-audio-tts-asr.md
**UC:** N/A (crash fix)
**Scenario:** N/A

---

## Problem

App crashes on tablets (especially Samsung Galaxy Tab) when loading/using TTS. The spec explicitly documents this as "KNOWN ISSUE — Samsung tablet crash during TTS model loading/playback" (05-audio-tts-asr.md lines 336-343).

### Root Causes

**1. Shared TtsEngine singleton with no external mutex**

Three consumers share the same `TtsEngine` instance via `TtsProvider.getInstance()`:
- `AudioCoordinator` (line 53)
- `VocabDrillViewModel` (line 40)
- `VerbDrillViewModel` (line 56)

While `TtsEngine.initialize()` has an internal Mutex, `AudioCoordinator` calls `ttsEngine.initialize()` from 4 concurrent code paths with no external synchronization. On tablets with stricter audio HAL, concurrent native calls cause SIGSEGV.

**2. `doRelease()` frees native object asynchronously**

`TtsEngine.doRelease()` (lines 288-298) launches `ttsToFree?.free()` on a separate coroutine (`ttsScope`). Between setting `offlineTts = null` and the actual `free()`, another coroutine's `speak()` can trigger a use-after-free on the native OfflineTts object.

**3. `catch Exception` instead of `catch Throwable` in drill ViewModels**

`VocabDrillViewModel.kt` (line 367) and `VerbDrillViewModel.kt` (line 613) catch `Exception` but not `Throwable`. Native errors (OutOfMemoryError, UnsatisfiedLinkError) propagate up and crash the app. This was fixed in `TtsEngine` itself (commit 29304f7) but not at these call sites.

**4. `checkTtsModel()` sets `ttsModelReady=true` without engine verification**

`AudioCoordinator.checkTtsModel()` (line 272) sets `ttsModelReady = true` based on file existence only, without checking engine state. If engine is in ERROR state, UI allows speak attempts that fail.

---

## Changes

### Fix 1: Add external mutex in AudioCoordinator for TTS operations

Add a `Mutex` in `AudioCoordinator` that wraps all `ttsEngine.initialize()` and `ttsEngine.speak()` calls. This prevents concurrent native calls from the 4 code paths.

**Files:** `shared/audio/AudioCoordinator.kt`

**Verification:** Rapidly tap speaker icon while TTS model is downloading → no crash

### Fix 2: Make `doRelease()` synchronous

Change `doRelease()` to synchronously join speakJob and free the native object before returning, instead of launching on a separate coroutine. This eliminates the use-after-free window.

**Files:** `data/TtsEngine.kt`

**Verification:** Switch languages rapidly → no crash

### Fix 3: `catch Throwable` in drill ViewModels

Change `catch (e: Exception)` to `catch (e: Throwable)` in `VocabDrillViewModel` and `VerbDrillViewModel` TTS calls.

**Files:** `ui/VocabDrillViewModel.kt`, `ui/VerbDrillViewModel.kt`

**Verification:** Load TTS on low-memory tablet → graceful error, no crash

### Fix 4: Verify engine state in `checkTtsModel()`

Set `ttsModelReady` only when both files exist AND engine state is Ready/Idle.

**Files:** `shared/audio/AudioCoordinator.kt`

**Verification:** TTS files exist but engine in ERROR state → ttsModelReady stays false

---

## Verification Checklist
1. [ ] TTS model download on tablet completes without crash
2. [ ] Speaker icon tap during download doesn't crash
3. [ ] Rapid language switching doesn't crash
4. [ ] VocabDrill speak on tablet doesn't crash
5. [ ] VerbDrill speak on tablet doesn't crash
6. [ ] Low-memory device: TTS fails gracefully with error, not crash
7. [ ] Regular training TTS still works on phone

## Scope Boundaries
**Do NOT touch:**
- ASR (speech recognition) — separate engine
- Daily practice logic
- SessionRunner or card flow
- UI composables

## Regression Plan
1. **Build:** `assembleDebug` — must pass
2. **Test on phone:** TTS play, download, language switch all work
3. **Test on tablet (if available):** no crashes during TTS operations
4. **Cross-check:** VocabDrill and VerbDrill speak still work

## Git
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: AudioCoordinator mutex | | |
| | Fix 2: Synchronous doRelease | | |
| | Fix 3: catch Throwable in drill VMs | | |
| | Fix 4: checkTtsModel engine state | | |
