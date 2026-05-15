# TASK-025: SH-02 VoiceAutoLauncher Fixed vs Variable Delay

**Status:** DONE
**Created:** 2026-05-15
**Branch:** feature/voice-auto-launcher-delay (from main)
**Scenario:** scenario-07-verb-drill.md
**Spec:** 23-screen-elements.md#SH-02

---

## Problem

Spec says VoiceAutoLauncher uses variable delay: "200ms for new card, 1200ms after incorrect feedback".
Code uses fixed 500ms delay for all cases (VoiceAutoLauncher.kt).

The composable does not support the dual-delay pattern described in spec.

## Solution

### Option A: Update code to support variable delay
**File:** `app/src/main/java/com/alexpo/grammermate/ui/components/VoiceAutoLauncher.kt`
- Add `delayAfterIncorrect` parameter
- Default to 1200ms, use 200ms for new-card triggers

### Option B: Update spec to match code
**File:** `docs/specification/23-screen-elements.md`
- Change delay description to "500ms fixed delay"

**Recommendation:** Option A — variable delay provides better UX (slower retry after incorrect gives user time to process). Check callers in TrainingScreen and DailyPracticeScreen for how they handle post-incorrect timing.

## Verification Checklist
1. VoiceAutoLauncher delay matches spec
2. New-card trigger uses short delay (200ms)
3. Post-incorrect trigger uses longer delay (1200ms)
4. VerbDrillScreen, TrainingScreen, DailyPracticeScreen all work correctly

## Scope Boundaries
**Do NOT touch:**
- Other LaunchedEffect timing
- VoiceAutoLauncher triggering logic

## Regression Plan
After all fixes are implemented, run:
1. **Build:** `assembleDebug` — must pass with no errors
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that unrelated features still work (VerbDrill, TrainingScreen, DailyPractice voice launch)
5. **UC/AC spot-check:** read affected UC entries from `22-use-case-registry.md`, confirm ACs hold
6. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Update VoiceAutoLauncher delay or spec | | |

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
