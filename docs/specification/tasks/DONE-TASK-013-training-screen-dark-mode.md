# TASK-013: TrainingScreen Dark-Mode Fix (Drill + Mix + Progress)

**Status:** DONE
**Created:** 2026-05-15
**Branch:** feature/training-screen-dark-mode (from main)
**Spec:** 14-theme-and-ui-components.md#14.7.5, 14.7.7
**UC:** UC-68 (AC1, AC2, AC5, AC8)
**Scenario:** scenario-01-training-flow.md, scenario-05-input-modes.md

---

## Problem

TrainingScreen (the primary training/drill screen) uses hardcoded light-mode-only colors for drill mode background, mix challenge surface, tense labels, prompt text, and result text. In dark mode, these produce a blinding light-green background (drill mode) or invisible dark-green text. SessionProgressIndicator (used in this screen) also has hardcoded light-only progress bar track colors.

Additionally, inline Color() literals in TrainingCardSession (correct/incorrect result text) bypass theme entirely.

## Changes

### Fix 1: TrainingScreen drill mode background

**Discrepancy:** N/A | **UC:** UC-68 AC1 | **Spec:** 14#14.7.7 (TrainingScreen: BROKEN)

Replace `DrillBackgroundGreen` constant with `LocalGrammarMateColors.current.drillBackgroundGreen` (or equivalent theme-aware lookup) at line 132-135. The Scaffold containerColor should use the theme-aware color when isDrillMode is true.

**Files:** `app/src/main/java/com/alexpo/grammermate/ui/screens/TrainingScreen.kt` — lines 132, 135

**Verification:** In dark mode + drill mode, background is dark muted green (0xFF1B3A1D), not light pastel green.

### Fix 2: TrainingScreen drill tense label and prompt text

**Discrepancy:** N/A | **UC:** UC-68 AC8 | **Spec:** 14#14.7.5

Replace `DrillTenseLabelGreen` and `DrillPromptGreen` at lines 177, 189 with theme-aware equivalents.

**Files:** `app/src/main/java/com/alexpo/grammermate/ui/screens/TrainingScreen.kt` — lines 177, 189

**Verification:** Tense label and prompt text readable in dark mode with >=4.5:1 contrast.

### Fix 3: TrainingScreen mix challenge surface and text

**Discrepancy:** N/A | **UC:** UC-68 AC2 | **Spec:** 14#14.7.5

Replace `MixChallengeSurface` and `MixChallengeText` at lines 206-213 with theme-aware equivalents.

**Files:** `app/src/main/java/com/alexpo/grammermate/ui/screens/TrainingScreen.kt` — lines 206-213

**Verification:** Mix challenge card has dark-mode appropriate background and readable text in dark theme.

### Fix 4: TrainingScreen result text (correct/incorrect)

**Discrepancy:** N/A | **UC:** UC-68 AC8 | **Spec:** 14#14.7.5

Replace `CorrectGreen` and `IncorrectRed` at lines 684-686 with theme-aware equivalents.

**Files:** `app/src/main/java/com/alexpo/grammermate/ui/screens/TrainingScreen.kt` — lines 684-686

**Verification:** Correct/incorrect text maintain >=4.5:1 contrast in both themes.

### Fix 5: SessionProgressIndicator progress bar colors

**Discrepancy:** N/A | **UC:** UC-68 AC5 | **Spec:** 14#14.7.7 (SessionProgressIndicator: BROKEN)

Replace hardcoded color constants in SessionProgressIndicator with theme-aware equivalents from GrammarMateColors. Specifically: ProgressTrackGreen, ProgressTrackGray, MasteryGreen, ProgressGreen, SpeedSlowRed, SpeedMediumYellow, ProgressFastGreen.

**Files:** `app/src/main/java/com/alexpo/grammermate/ui/components/SessionProgressIndicator.kt` — all color references

**Verification:** Progress bar track visible in dark mode. Speedometer arc visible in dark mode.

### Fix 6: TrainingCardSession result text

**Discrepancy:** N/A | **UC:** UC-68 AC8 | **Spec:** 14#14.7.5

Replace inline `Color(0xFF2E7D32)` and `Color(0xFFC62828)` at lines 575, 581 with theme-aware equivalents.

**Files:** `app/src/main/java/com/alexpo/grammermate/ui/TrainingCardSession.kt` — lines 575, 581

**Verification:** Correct/incorrect result text readable in both themes.

---

## Verification Checklist
1. TrainingScreen drill mode background is dark green in dark theme
2. TrainingScreen drill tense label and prompt text readable in dark theme
3. Mix challenge card surface is dark blue-grey in dark theme, text is light blue
4. Correct/incorrect result text readable in dark theme (>=4.5:1 contrast)
5. SessionProgressIndicator progress bar track visible in dark theme
6. SessionProgressIndicator speedometer visible in dark theme
7. TrainingCardSession result text readable in dark theme
8. No visual regression in light theme (colors unchanged)

## Scope Boundaries
**Do NOT touch:**
- VocabDrillScreen, DailyPracticeScreen — TASK-014
- Theme.kt GrammarMateColors infrastructure — TASK-012
- HomeScreen, LessonRoadmapScreen, other screens — separate tasks
- Business logic, ViewModel, data layer

## Regression Plan
After all fixes implemented, run:
1. **Build:** `assembleDebug` — must pass
2. **Tests:** `test` — must pass
3. **Per-task verification:** check each item above in both light and dark themes
4. **Cross-task regression:** verify TrainingScreen non-drill mode still uses MaterialTheme.colorScheme.background
5. **UC/AC spot-check:** UC-68 AC1, AC2, AC5, AC8
6. **Spec sync:** update 14#14.7.7 TrainingScreen status from BROKEN to OK

## Git
One commit. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---
## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Drill background | | |
| | Fix 2: Tense label + prompt | | |
| | Fix 3: Mix challenge | | |
| | Fix 4: Result text | | |
| | Fix 5: Progress indicator | | |
| | Fix 6: Card session result | | |
