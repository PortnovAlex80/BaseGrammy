# TASK-006: Verb Drill Play Button -- Resume After Manual Pause + TTS State Icons

**Status:** OPEN
**Created:** 2026-05-14
**Branch:** feature/verb-drill-play-fix (from feature/perf-and-cursor-fixes)
**Spec:** 10-verb-drill.md#10.4.3, 12-training-card-session.md#12.7.1
**UC:** UC-63, UC-27 (AC5-AC6), UC-50 (AC3)
**Scenario:** scenario-07-verb-drill.md

---

## Problem

Two related bugs in Verb Drill mode:

1. **Play button advances card after manual pause.** `VerbDrillCardSessionProvider.togglePause()` unconditionally calls `nextCard()` whenever `isPaused==true`, even when the pause was user-initiated (not hint-related). The user expects Play to resume the current card, not skip it.

2. **TTS button shows static icon.** VerbDrillScreen uses a plain `IconButton` with `VolumeUp` instead of `TtsSpeakerButton`, so users can't stop playback, see loading state, or see errors. TrainingScreen uses `TtsSpeakerButton` correctly.

## Changes

### Fix 1: Distinguish pause reasons in togglePause()
**Discrepancy:** N/A | **UC:** UC-63 | **Spec:** 10-verb-drill.md#10.4.3, 12-training-card-session.md#12.7.1

In `VerbDrillCardSessionProvider.togglePause()` (line 138-145), check `hintAnswer`:
- If `hintAnswer != null` --> call `nextCard()` (existing correct behavior for hint pause)
- If `hintAnswer == null` --> call `sm.resume()` or equivalent to unpause without advancing

Add a `resume()` method to `CardSessionStateMachine` that sets `isPaused = false` without resetting other state.

**Pseudocode:**
```kotlin
// CardSessionStateMachine.kt
fun resume() {
    isPaused = false
}

// VerbDrillCardSessionProvider.kt
override fun togglePause() {
    if (sm.isPaused) {
        if (sm.hintAnswer != null) {
            nextCard()  // hint was shown -> advance
        } else {
            sm.resume()  // manual pause -> just resume
        }
    } else {
        sm.pause()
    }
}
```

**Files:**
- `feature/training/CardSessionStateMachine.kt` -- add `resume()` method
- `ui/VerbDrillCardSessionProvider.kt` -- update `togglePause()`

**Verification:**
- Start verb drill --> press Pause --> press Play --> card should RESUME (same card, input preserved)
- Get 3 wrong answers --> hint appears --> press Play --> card should ADVANCE to next card
- Press eye/hint button --> hint appears --> press Play --> card should ADVANCE

### Fix 2: Replace static IconButton with TtsSpeakerButton in VerbDrillScreen
**Discrepancy:** N/A | **UC:** UC-27 | **Spec:** 05-audio-tts-asr.md#5.1.8

In `VerbDrillScreen.kt` (line 248-253), replace the plain `IconButton` with `TtsSpeakerButton`:
- Pass `ttsState = contract.ttsState` for 4-state rendering
- Pass `onClick = { contract.speakTts() }` for speak trigger
- This gives the user: stop during playback, loading spinner, error indicator

**Files:**
- `ui/screens/VerbDrillScreen.kt` -- replace `IconButton` with `TtsSpeakerButton` for card-level TTS

**Verification:**
- TTS not initialized --> button shows spinner during init
- TTS ready --> button shows VolumeUp --> tap speaks answer --> button shows StopCircle (red) --> tap stops
- TTS error --> button shows ReportProblem (red) with tooltip
- Compare visually with TrainingScreen TTS button -- must match behavior

---

## Verification Checklist
1. Manual pause --> Play resumes current card (input preserved, same card)
2. Hint pause (3 wrong answers) --> Play advances to next card
3. Hint pause (eye button) --> Play advances to next card
4. TTS button shows 4 states: idle/speaking/initializing/error
5. TTS tap during speaking stops playback (StopCircle --> VolumeUp)
6. Card advancement via correct answer still works (auto-advance)
7. Next/Prev navigation buttons still work independently
8. Build: `assembleDebug` passes with no errors

## Scope Boundaries
**Do NOT touch:**
- TrainingScreen or its TTS button (already correct)
- DailyPracticeScreen TTS behavior
- CardSessionStateMachine state machine logic beyond adding resume()
- Any data stores or ViewModel code

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` -- must pass with no errors
2. **Tests:** `test` -- must pass, no new failures
3. **Per-fix verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify these unaffected features still work:
   - Regular training flow (TrainingScreen) -- card advancement, TTS
   - Daily Practice verb block -- uses same VerbDrillCardSessionProvider
   - Vocab Drill -- separate flow, should be unaffected
   - Boss battle -- separate flow, should be unaffected
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold:
   - UC-63 AC1-AC5 (pause/resume behavior)
   - UC-27 AC5-AC6 (hint auto-show and pause)
   - UC-50 AC3 (manual show sets hintAnswer)
6. **Spec sync:** specs were updated before this task -- verify code matches updated specs

## Git
One commit. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---
## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: togglePause pause reason | | |
| | Fix 2: TTS state icons | | |
