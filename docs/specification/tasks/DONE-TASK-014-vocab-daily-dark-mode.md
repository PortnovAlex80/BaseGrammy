# TASK-014: VocabDrillScreen + DailyPracticeScreen Dark-Mode Fix

**Status:** DONE
**Created:** 2026-05-15
**Branch:** feature/vocab-daily-dark-mode (from main)
**Spec:** 14-theme-and-ui-components.md#14.7.5, 14.7.7
**UC:** UC-68 (AC3, AC4, AC7, AC8, AC9)
**Scenario:** scenario-08-vocab-drill.md, scenario-06-daily-practice.md

---

## Problem

VocabDrillScreen uses hardcoded inline Color() literals for card backgrounds (correct/incorrect), interval labels, and mastery text. In dark mode, the light pastel green/pink card backgrounds appear as bright rectangles. DailyPracticeScreen has 8 hardcoded SRS rating button colors (4 backgrounds + 4 text colors) that are all light pastels. Additionally, HomeScreen has a hardcoded mastered count label color.

All inline Color(0xFF...) literals in these files must be replaced with theme-aware constants from GrammarMateColors (created in TASK-012).

## Changes

### Fix 1: VocabDrillScreen correct/incorrect card backgrounds

**Discrepancy:** N/A | **UC:** UC-68 AC3 | **Spec:** 14#14.7.7 (VocabDrillScreen: BROKEN)

Replace inline `Color(0xFFE8F5E9).copy(alpha = 0.7f)` and `Color(0xFFFFEBEE).copy(alpha = 0.7f)` at lines 727, 729, 1043, 1044 with theme-aware equivalents from GrammarMateColors.

**Files:** `app/src/main/java/com/alexpo/grammermate/ui/screens/VocabDrillScreen.kt` — lines 727, 729, 1043, 1044

**Verification:** Card backgrounds are dark muted green/red in dark mode, not bright pastels.

### Fix 2: VocabDrillScreen interval and mastery labels

**Discrepancy:** N/A | **UC:** UC-68 AC8 | **Spec:** 14#14.7.5

Replace inline `Color(0xFFE65100)` at lines 506, 511 and `Color(0xFF2E7D32)` at lines 292, 538, 1077, 1083 with theme-aware equivalents.

**Files:** `app/src/main/java/com/alexpo/grammermate/ui/screens/VocabDrillScreen.kt` — lines 292, 506, 511, 538, 1077, 1083

**Verification:** Interval labels and mastery text readable in dark theme.

### Fix 3: VocabDrillScreen button text colors

**Discrepancy:** N/A | **UC:** UC-68 AC8 | **Spec:** 14#14.7.5

Replace `Color.White.copy(alpha = 0.8f)` at lines 530, 544 with appropriate theme-aware color for text on colored buttons.

**Files:** `app/src/main/java/com/alexpo/grammermate/ui/screens/VocabDrillScreen.kt` — lines 530, 544

**Verification:** Button text readable in both themes.

### Fix 4: DailyPracticeScreen SRS rating button backgrounds

**Discrepancy:** N/A | **UC:** UC-68 AC4 | **Spec:** 14#14.7.7 (DailyPracticeScreen: BROKEN)

Replace all 8 hardcoded colors at lines 782-785 (4 backgrounds + 4 text colors) with theme-aware equivalents from GrammarMateColors.

Current (light only):
- AGAIN: bg 0xFFFFEBEE, text 0xFFE53935
- HARD: bg 0xFFFFF3E0, text 0xFFFF9800
- GOOD: bg 0xFFE8F5E9, text 0xFF4CAF50
- EASY: bg 0xFFE3F2FD, text 0xFF2196F3

**Files:** `app/src/main/java/com/alexpo/grammermate/ui/screens/DailyPracticeScreen.kt` — lines 782-785

**Verification:** SRS buttons have dark-mode appropriate backgrounds in dark theme.

### Fix 5: HomeScreen mastered count text

**Discrepancy:** N/A | **UC:** UC-68 AC9 | **Spec:** 14#14.7.5

Replace `Color(0xFF2E7D32)` at line 393 with theme-aware equivalent.

**Files:** `app/src/main/java/com/alexpo/grammermate/ui/screens/HomeScreen.kt` — line 393

**Verification:** Mastered count text readable in dark theme.

---

## Verification Checklist
1. VocabDrill correct/incorrect card backgrounds are dark muted in dark theme
2. VocabDrill interval labels readable in dark theme
3. VocabDrill mastery text readable in dark theme
4. VocabDrill button text readable in both themes
5. DailyPractice SRS Again button has dark background in dark theme
6. DailyPractice SRS Hard button has dark background in dark theme
7. DailyPractice SRS Good button has dark background in dark theme
8. DailyPractice SRS Easy button has dark background in dark theme
9. HomeScreen mastered count readable in dark theme
10. No visual regression in light theme
11. No inline Color(0xFF...) literals remain in VocabDrillScreen.kt or DailyPracticeScreen.kt

## Scope Boundaries
**Do NOT touch:**
- TrainingScreen, SessionProgressIndicator, TrainingCardSession — TASK-013
- Theme.kt GrammarMateColors infrastructure — TASK-012
- VerbDrillScreen (already theme-compliant)
- GrammarMateApp, LessonRoadmapScreen (boss colors are thematic, not theme-dependent)
- Business logic, ViewModel, data layer

## Regression Plan
After all fixes implemented, run:
1. **Build:** `assembleDebug` — must pass
2. **Tests:** `test` — must pass
3. **Per-task verification:** check each item above in both light and dark themes
4. **Cross-task regression:** verify VocabDrill voice mode still shows correct/incorrect feedback, DailyPractice SRS block still functional
5. **UC/AC spot-check:** UC-68 AC3, AC4, AC7, AC8, AC9
6. **Spec sync:** update 14#14.7.7 VocabDrillScreen and DailyPracticeScreen status from BROKEN to OK

## Git
One commit. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---
## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: VocabDrill card backgrounds | | |
| | Fix 2: VocabDrill labels | | |
| | Fix 3: VocabDrill button text | | |
| | Fix 4: DailyPractice SRS buttons | | |
| | Fix 5: HomeScreen mastery label | | |
