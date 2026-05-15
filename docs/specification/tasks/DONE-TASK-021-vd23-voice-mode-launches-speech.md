# TASK-021: VD-23 Voice Mode Button Launches Speech Directly

**Status:** DONE
**Created:** 2026-05-15
**Branch:** feature/verb-drill-voice-button (from main)
**Scenario:** scenario-07-verb-drill.md
**Spec:** 23-screen-elements.md#VD-23
**UC:** UC-29

---

## Problem

Spec VD-23 says: "Sets input mode to VOICE (does NOT directly launch speech)".
Code (VerbDrillInputModeBar lines 817-820) sets mode AND calls `onLaunchVoice()` which launches speech recognition directly.

The voice button in VerbDrillScreen behaves differently from what the spec describes. It both switches mode AND immediately starts recording.

## Solution

### Decision needed: Which behavior is correct?

Option A: Update spec to match code (button sets mode + launches speech)
- This matches the UX intent: tap mic → immediately start speaking

Option B: Update code to match spec (button only sets mode, separate trigger launches speech)
- This matches TrainingScreen behavior where mode is separate from launch

**Recommendation:** Option A — the direct-launch UX is better for verb drill (single-tap to start).

### Fix 1 (Option A): Update spec VD-23
**File:** `docs/specification/23-screen-elements.md`
- Change to: "Sets input mode to VOICE and immediately launches speech recognition"
- Note behavioral difference from TrainingScreen (which separates mode and launch)

### Fix 1 (Option B): Update code
**File:** `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillScreen.kt`
- Remove onLaunchVoice() call from voice mode button
- Keep only setInputMode(InputMode.VOICE)

## Verification Checklist
1. Spec and code agree on voice button behavior
2. VerbDrill voice button works correctly on device
3. Behavioral difference from TrainingScreen is documented (if Option A)

## Scope Boundaries
**Do NOT touch:**
- TrainingScreen voice button
- DailyPractice voice button

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (VerbDrill screen, voice input, TrainingScreen)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Update spec VD-23 or code voice button behavior | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
