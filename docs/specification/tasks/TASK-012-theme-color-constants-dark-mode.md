# TASK-012: Theme.kt Color Constants — Dark-Mode Adaptation

**Status:** OPEN
**Created:** 2026-05-15
**Branch:** feature/theme-dark-mode-constants (from main)
**Spec:** 14-theme-and-ui-components.md#14.7.5, 14.7.6, 14.7.7
**UC:** UC-66, UC-68 (AC6, AC7, AC8)
**Scenario:** —

---

## Problem

Theme.kt (lines 16-61) defines ~30 color constants as static `val Color = Color(0xFF...)` values. These are all light-mode-only colors with no dark variants. Screens import and use these constants unconditionally, bypassing the Material theme system entirely. When dark theme is active, light pastel backgrounds appear as blinding bright rectangles, and dark text colors become invisible against dark surfaces.

The comment in Theme.kt (lines 10-13) explicitly acknowledges this: "Replacement in screen files is a separate task."

## Changes

### Fix 1: Create GrammarMateColors data class with light/dark variants

**Discrepancy:** N/A | **UC:** UC-68 AC6 | **Spec:** 14#14.7.6

Create a `GrammarMateColors` data class containing all 25+ semantic color properties with proper light and dark values. Provide via CompositionLocal.

Light values (current):
- DrillBackgroundGreen = 0xFFE8F5E9
- DrillTenseLabelGreen = 0xFF388E3C
- DrillPromptGreen = 0xFF2E7D32
- MixChallengeSurface = 0xFFE3F2FD
- MixChallengeText = 0xFF1565C0
- MasteryGreen = 0xFF2E7D32
- ProgressGreen = 0xFF4CAF50
- ProgressTrackGreen = 0xFFC8E6C9
- ProgressFastGreen = 0xFF43A047
- SpeedSlowRed = 0xFFE53935
- SpeedMediumYellow = 0xFFFDD835
- ProgressLabelWhite = Color.White
- ProgressTrackGray = 0xFFE0E0E0
- CorrectGreen = 0xFF2E7D32
- IncorrectRed = 0xFFC62828
- SrsAgainBackground = 0xFFFFEBEE, SrsAgainText = 0xFFE53935
- SrsHardBackground = 0xFFFFF3E0, SrsHardText = 0xFFFF9800
- SrsGoodBackground = 0xFFE8F5E9, SrsGoodText = 0xFF4CAF50
- SrsEasyBackground = 0xFFE3F2FD, SrsEasyText = 0xFF2196F3
- VocabIntervalOrange = 0xFFE65100
- VocabCorrectBackground = 0xFFE8F5E9
- VocabIncorrectBackground = 0xFFFFEBEE
- BossBronze = 0xFFCD7F32, BossSilver = 0xFFC0C0C0, BossGold = 0xFFFFD700
- DestructiveRed = 0xFFB00020

Dark values:
- DrillBackgroundGreen = 0xFF1B3A1D
- DrillTenseLabelGreen = 0xFF81C784
- DrillPromptGreen = 0xFF66BB6A
- MixChallengeSurface = 0xFF1A2E3A
- MixChallengeText = 0xFF64B5F6
- MasteryGreen = 0xFF66BB6A
- ProgressGreen = 0xFF66BB6A
- ProgressTrackGreen = 0xFF2E4A2F
- ProgressFastGreen = 0xFF66BB6A
- SpeedSlowRed = 0xFFEF5350
- SpeedMediumYellow = 0xFFFFEE58
- ProgressLabelWhite = Color.White
- ProgressTrackGray = 0xFF3A3A3A
- CorrectGreen = 0xFF66BB6A
- IncorrectRed = 0xFFEF5350
- SrsAgainBackground = 0xFF3A1B1B, SrsAgainText = 0xFFEF5350
- SrsHardBackground = 0xFF3A2E1B, SrsHardText = 0xFFFF8A65
- SrsGoodBackground = 0xFF1B3A1D, SrsGoodText = 0xFF66BB6A
- SrsEasyBackground = 0xFF1A2E3A, SrsEasyText = 0xFF64B5F6
- VocabIntervalOrange = 0xFFFF8A65
- VocabCorrectBackground = 0xFF1B3A1D
- VocabIncorrectBackground = 0xFF3A1B1B
- BossBronze/Silver/Gold = keep as-is
- DestructiveRed = 0xFFCF6679

**Files:** `app/src/main/java/com/alexpo/grammermate/ui/Theme.kt` — replace static vals with GrammarMateColors + CompositionLocal

**Verification:** All existing imports of `CorrectGreen`, `DrillBackgroundGreen` etc. still compile (provide @Composable accessor or keep val aliases). Dark theme colors verified visually.

### Fix 2: Provide CompositionLocal in GrammarMateTheme

**Discrepancy:** N/A | **UC:** UC-68 AC6 | **Spec:** 14#14.7.6

Inside GrammarMateTheme, after setting MaterialTheme, also provide GrammarMateColors via CompositionLocal. Use `provides` with light/dark variants based on `useDarkTheme`.

**Files:** `app/src/main/java/com/alexpo/grammermate/ui/Theme.kt`

**Verification:** `LocalGrammarMateColors.current` returns correct light/dark values based on theme.

---

## Verification Checklist
1. `GrammarMateColors` data class exists with all 25+ color properties
2. `LocalGrammarMateColors` CompositionLocal is provided in GrammarMateTheme
3. Light values match existing hardcoded values exactly (no visual regression)
4. Dark values are defined for all properties
5. Existing screen imports still compile (backward-compatible access)
6. Theme.kt compiles without errors
7. No visual change when theme is LIGHT (backward compatibility)

## Scope Boundaries
**Do NOT touch:**
- Screen files (TrainingScreen, VocabDrillScreen, etc.) — those are TASK-013/014
- GrammarMateTheme colorScheme (LightColors/DarkColors) — those are already correct
- AppConfigStore or theme persistence — already working
- SettingsScreen theme selector UI — already working

## Regression Plan
After all fixes implemented, run:
1. **Build:** `assembleDebug` — must pass
2. **Tests:** `test` — must pass, no new failures
3. **Per-task verification:** check each item from Verification Checklist
4. **Cross-task regression:** switch theme to LIGHT, verify no visual changes; switch to DARK, verify colors are dark-appropriate
5. **UC/AC spot-check:** UC-66 (theme switching still works), UC-68 AC6 (constants return dark values)
6. **Spec sync:** update Theme.kt comment (lines 10-13) to reflect completed status

## Git
One commit. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---
## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: GrammarMateColors data class | | |
| | Fix 2: CompositionLocal provider | | |
