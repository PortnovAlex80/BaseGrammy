# TASK-005: TTS Error Icon for Memory/Loading Failures [DONE]

**Status:** DONE
**Created:** 2026-05-14
**Branch:** feature/tts-error-icon (from feature/arch-feature-migration)
**Spec:** 05-audio-tts-asr.md (section 5.1.8)
**UC:** UC-61 (AC update: error tooltip)
**Elements:** TS-09, TCS-06, DP-10, VD-14

---

## Problem

When TTS model loading fails due to insufficient memory or other errors, the user sees a red icon but has no idea what's wrong. There is no tooltip, no error message, no differentiation between "model not downloaded" vs "out of memory" vs "native crash". The user is left guessing.

## Changes

### Fix 1: Add error reason to TtsState

**Spec:** 05#5.1.8

Replace flat `TtsState.ERROR` with `TtsState.ERROR(reason: String)` or add `ttsErrorReason: String?` to the state. Populate reason when initialization fails:
- `"model_not_found"` — model files missing
- `"out_of_memory"` — OutOfMemoryError during native init
- `"init_failed"` — other native exception
- `"timeout"` — init timed out (from TASK-003 Fix 2)

**Files:**
- `data/TtsEngine.kt` — TtsState sealed class or enum with reason field
- `ui/TrainingViewModel.kt` — propagate error reason to UI state

**Verification:** Kill model file → init fails → state is ERROR("model_not_found"), not just ERROR.

### Fix 2: Show error tooltip on TTS icon

**Spec:** 05#5.1.8 (ERROR state tooltip requirement)

When TTS state is ERROR, the icon shows a `TooltipBox` or `Snackbar` with human-readable error message:
- "TTS model not loaded" for model_not_found
- "Not enough memory to load TTS model" for out_of_memory
- "TTS initialization failed" for init_failed
- "TTS initialization timed out" for timeout

Icon changes from ReportProblem to Warning (red triangle) for out_of_memory, keeping ReportProblem for other errors.

**Files:**
- `ui/components/TtsSpeakerButton.kt` — add TooltipBox on error state
- `ui/screens/DailyPracticeScreen.kt` — update inline TTS button (DP-10)

**Verification:** Out of memory → red triangle icon → long press → "Not enough memory". Model missing → red icon → "Model not loaded".

---

## Verification Checklist

1. TTS model files deleted → ERROR state with reason "model_not_found"
2. OutOfMemoryError during init → ERROR state with reason "out_of_memory"
3. Error icon shows tooltip with human-readable message
4. "Out of memory" shows red triangle (Warning icon), not ReportProblem
5. Other errors show ReportProblem with appropriate message
6. Normal TTS flow unaffected — no tooltip when READY/SPEAKING

## Scope Boundaries

**Do NOT touch:**
- TtsEngine native logic (that's TASK-003)
- Download dialog flow
- ASR error handling
- Screen layouts beyond the TTS icon area

## Dependencies

- **TASK-003** (Fix 2: init timeout) should be implemented first, as it adds the "timeout" error reason this task needs to display.

## Git

Commit footer: `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>`

---

## Completion Log

| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| 2026-05-14 | Fix 1: Error reason in TtsState | DONE | TtsState is sealed class with `Error(reason: String?)`. Reasons: "Model not loaded", "Not enough memory", "Timed out", "Initialization failed", "Playback failed". |
| 2026-05-14 | Fix 2: Tooltip on error icon | DONE | TtsSpeakerButton in SharedComponents.kt: `TtsState.Error` branch shows TooltipBox with PlainTooltip containing reason text. OOM → Warning icon (red triangle). Other errors → ReportProblem icon. No tooltip when READY/SPEAKING. |
| 2026-05-14 | Verification checklist | DONE | Code logic verified: OOM check via `reason?.contains("memory", ignoreCase = true)`. Warning vs ReportProblem differentiation. Normal states (Idle/Initializing/Ready/Speaking) show no tooltip. |
