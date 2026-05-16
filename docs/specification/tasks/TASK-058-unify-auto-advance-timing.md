# TASK-058: Unify Auto-Advance Timing After Correct Voice Answer

**Status:** OPEN
**Created:** 2026-05-17
**Branch:** feature/unify-auto-advance (from main)
**Spec:** 10-verb-drill.md, 12-training-card-session.md
**UC:** New UC needed
**Scenario:** scenario-05-input-modes.md

---

## Problem

Auto-advance timing after correct voice answer varies across modes:
- **TrainingScreen**: No delay — SessionRunner calls `nextCardInternal()` inline (immediate)
- **VerbDrill**: 500ms delay via LaunchedEffect (VerbDrillScreen.kt:199-207)
- **DailyPractice**: 400ms delay via LaunchedEffect (DailyPracticeScreen.kt:364-370)
- **VocabDrill**: 800ms auto-flip delay (VocabDrillScreen.kt:388-393)

Users experience inconsistent feedback timing. Need a configurable `AUTO_ADVANCE_DELAY_MS` parameter.

## Changes

### Fix 1: Add autoAdvanceDelayMs to AppConfigStore
**Discrepancy:** N/A | **UC:** New UC | **Spec:** 06-infrastructure.md

Add `autoAdvanceDelayMs: Long = 500L` to AppConfig. Range [0, 2000]. Default 500ms.

**Files:** `data/AppConfigStore.kt`, `assets/grammarmate/config.yaml`

**Verification:** Config parameter exists with default 500ms.

### Fix 2: VerbDrill uses config delay
**Discrepancy:** N/A | **UC:** New UC | **Spec:** 10-verb-drill.md

Replace hardcoded 500ms in VerbDrillScreen.kt LaunchedEffect with config value.

**Files:** `ui/VerbDrillScreen.kt` — LaunchedEffect auto-advance

**Verification:** VerbDrill auto-advance uses config value.

### Fix 3: DailyPractice uses config delay
**Discrepancy:** N/A | **UC:** New UC | **Spec:** 09-daily-practice.md

Replace hardcoded 400ms in DailyPracticeScreen.kt with config value.

**Files:** `ui/screens/DailyPracticeScreen.kt` — LaunchedEffect auto-advance

**Verification:** DailyPractice auto-advance uses config value.

### Fix 4: Update specs
**Discrepancy:** N/A | **UC:** New UC | **Spec:** 10-verb-drill.md, 09-daily-practice.md, 12-training-card-session.md

Add to spec: "Auto-advance after correct voice answer uses configurable `autoAdvanceDelayMs` (default 500ms). Applied uniformly across all card-based modes."

**Files:** `docs/specification/10-verb-drill.md`, `docs/specification/09-daily-practice.md`, `docs/specification/12-training-card-session.md`

**Verification:** Spec documents unified configurable delay.

---

## Verification Checklist
1. VerbDrill auto-advance delay comes from config
2. DailyPractice auto-advance delay comes from config
3. TrainingScreen behavior unchanged (inline advance, no delay)
4. Changing config.yaml value changes delay in VerbDrill and DailyPractice
5. `assembleDebug` passes

## Scope Boundaries
**Do NOT touch:**
- VocabDrill auto-flip (different paradigm — SRS card flip)
- TrainingScreen SessionRunner inline advance
- Auto-advance cancellation logic (separate concern)

## Regression Plan
1. Build: `assembleDebug`
2. Test auto-advance timing in VerbDrill and DailyPractice
3. Cross-task: verify TrainingScreen, VocabDrill unaffected

## Git
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---
## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
