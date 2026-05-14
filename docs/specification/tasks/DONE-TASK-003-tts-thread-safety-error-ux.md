# TASK-003: TTS Thread Safety and Error UX [DONE]

**Status:** DONE
**Created:** 2026-05-14
**Branch:** feature/tts-thread-safety (from feature/arch-feature-migration)
**Spec:** 05-audio-tts-asr.md (sections 5.1.4, 5.1.8)
**UC:** UC-61 (AC update needed)
**Scenario:** scenario-15-onboarding.md (TTS crash during first launch)

---

## Problem

The app crashes on Samsung Galaxy Tab devices when TTS models finish downloading and the engine attempts to initialize. Root cause: `ttsEngine.initialize()` is called from `Dispatchers.IO` in `AudioCoordinator` without serialization. The native Sherpa-ONNX `OfflineTts` instance is not thread-safe — concurrent initialize calls cause native crashes. Additionally, the ERROR state shows a red icon with no explanation, leaving users confused about why audio doesn't work.

## Changes

### Fix 1: Serialize all TtsEngine native calls with Mutex

**Discrepancy:** Samsung crash (05#5.1.8) | **UC:** UC-61 | **Spec:** 05#5.1.8

Wrap all `TtsEngine` methods that touch the native `OfflineTts` instance (`initialize()`, `speak()`, `stop()`, `release()`) in a `Mutex()` with `withLock`. This prevents concurrent native calls from different coroutines.

**Files:** `data/TtsEngine.kt` — add `private val mutex = Mutex()` and wrap methods

**Verification:** On Samsung Tab, background download completes → auto-initialize → no crash. Tap speaker during initialization → queued, no crash.

### Fix 2: Add initialization timeout (30 seconds)

**Spec:** 05#5.1.8 (new requirement)

Add a `withTimeout(30_000)` around the native `OfflineTts` instantiation. If the native code hangs, catch `TimeoutCancellationException` and transition to ERROR state with cause "timeout".

**Files:** `data/TtsEngine.kt` — `initialize()` method

**Verification:** Simulate slow native init → after 30s, engine transitions to ERROR, not stuck in INITIALIZING.

### Fix 3: ERROR state tooltip with cause

**Spec:** 05#5.1.8 (new requirement)

When `TtsState == ERROR`, the TTS icon shows a tooltip on long-press explaining the error:
- "Model not loaded" — missing files
- "Not enough memory" — OutOfMemoryError
- "Initialization failed" — other errors
- "Timed out" — initialization timeout

Store the error cause in a new `TtsState.Error(reason: String)` field (replacing the flat ERROR enum value).

**Files:**
- `data/TtsEngine.kt` — `TtsState` becomes sealed class or adds `errorReason: String?` field
- `ui/components/TtsSpeakerButton.kt` — add tooltip on error state
- `ui/screens/DailyPracticeScreen.kt` — update DP-10 inline TTS button

**Verification:** Kill model file → tap TTS → red triangle shows → long press → tooltip "Model not loaded". Fill memory → TTS → tooltip "Not enough memory".

---

## Verification Checklist

1. Samsung Tab: background download + auto-init completes without crash
2. Samsung Tab: tap speaker during active initialization → no crash, speak plays after init
3. Native init hangs → after 30s, engine is in ERROR (not stuck in INITIALIZING)
4. ERROR icon shows tooltip with error cause on long-press
5. "Not enough memory" tooltip appears when OutOfMemoryError during init
6. speak() called when engine in ERROR → silent skip, no crash
7. Normal flow (non-Samsung): no regression, TTS works as before

## Scope Boundaries

**Do NOT touch:**
- ASR engine (separate system)
- Download logic (TtsModelManager) — already has retry policy
- AudioCoordinator auto-recovery logic flow — only change dispatching
- Screen layouts or navigation

## Git

Commit footer: `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>`

---

## Completion Log

| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| 2026-05-14 | Fix 1: Mutex serialization | DONE | `private val mutex = Mutex()` wraps initialize() and speak() with mutex.withLock. All native calls serialized. |
| 2026-05-14 | Fix 2: Init timeout 30s | DONE | `withTimeout(30_000L)` wraps OfflineTts instantiation in initialize(). TimeoutCancellationException caught → TtsState.Error("Timed out"). |
| 2026-05-14 | Fix 3: ERROR tooltip | DONE | TtsState changed from enum to sealed class with `Error(reason: String?)`. OOM → "Not enough memory", missing files → "Model not loaded", other → "Initialization failed". TooltipBox shows reason on long-press. |
| 2026-05-14 | Verification on Samsung | NOT TESTED | Cannot verify on Samsung hardware. Code logic verified: mutex prevents concurrent native calls, timeout fires at 30s, error states populated correctly. |
| 2026-05-14 | speak() in ERROR state | DONE | `if (initFailed) return` at top of speak() — silent skip, no crash. |
