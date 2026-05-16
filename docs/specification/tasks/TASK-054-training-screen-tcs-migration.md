# TASK-054: Migrate TrainingScreen to TrainingCardSession

**Status:** OPEN
**Created:** 2026-05-16
**Branch:** feature/training-screen-tcs (from main)
**Spec:** 12-training-card-session.md §12.5.3
**UC:** UC-06 (training flow), UC-69 (drill), UC-70 (drill nav)
**Scenario:** scenario-01-training-flow.md, scenario-09-boss-battle.md, scenario-16-drill-sublesson.md

---

## Problem

TrainingScreen is the only card-based training screen that does NOT use the TrainingCardSession component. It has ~270 lines of inline UI code (card rendering, input controls, result display, navigation) that duplicates what TrainingCardSession provides. This violates DP-04 (Universal Card Engine) and makes it harder to maintain consistent behavior across modes. The spec §12.5.3 explicitly marks this as "migration target".

## Changes

### Fix 1: Create TrainingCardSessionProvider adapter
**Discrepancy:** N/A (new) | **UC:** UC-06 | **Spec:** 12-training-card-session.md §12.5.3

Create `TrainingCardSessionProvider` implementing `CardSessionContract`. The adapter wraps `SessionRunner` and reads from `TrainingUiState` via `TrainingStateAccess`. Must handle all 5 sub-modes (NORMAL, BOSS, BOSS_MEGA, DRILL, ELITE) transparently.

Key adapter responsibilities:
- Map `TrainingUiState.cardSession` fields to `CardSessionContract` properties
- Map `TrainingUiState.drill` fields for drill sub-mode
- Map `TrainingUiState.boss` fields for boss mode
- Implement `submitAnswer()`, `nextCard()`, `prevCard()`, `togglePause()`, `showAnswer()`
- Implement word bank operations (delegate to SessionRunner)
- Implement TTS operations (delegate to TtsEngine)
- Implement flagging/reporting (delegate to BadSentenceHelper)
- Handle `supportsDrillTheme` capability for green theme in drill mode

**Files:** New `ui/TrainingCardSessionProvider.kt`

**Verification:** Provider implements all CardSessionContract methods, compiles without errors

### Fix 2: Rewrite TrainingScreen to use TrainingCardSession
**Discrepancy:** Inline UI duplicates TCS | **UC:** UC-06 | **Spec:** 12-training-card-session.md §12.5.3

Replace TrainingScreen's inline card rendering with a `TrainingCardSession` call using the new provider. The composable becomes:
- Header (title + settings gear)
- TrainingCardSession(provider) { ... custom slots ... }
- DrillStartDialog, ExitConfirmationDialog remain as overlays

Remove all inline: CardPrompt, AnswerBox, ResultBlock, InputModeSelector, HeaderStats, ModeSelector, NavigationRow. These are now provided by TrainingCardSession's default slots.

**Files:** `ui/screens/TrainingScreen.kt` — major rewrite, keep only the composable shell

**Verification:** TrainingScreen renders identically to current behavior (visual regression)

### Fix 3: Update GrammarMateApp wiring
**Discrepancy:** N/A | **UC:** UC-06 | **Spec:** 07-app-router.md

Update `GrammarMateApp.kt` `TrainingScreenContent()` to create `TrainingCardSessionProvider` and pass it to the new TrainingScreen. Remove any now-unused state extraction code.

**Files:** `ui/GrammarMateApp.kt` — TrainingScreenContent function

**Verification:** App navigates to Training screen without crash, all sub-modes accessible

### Fix 4: Update screen element registry
**Discrepancy:** N/A | **UC:** N/A | **Spec:** 23-screen-elements.md

Update TrainingScreen section in 23-screen-elements.md to reference TrainingCardSession elements (TCS-*) instead of inline elements (TS-*). Mark TS elements as "now provided by TrainingCardSession default slots". Add trace entries to trace-index.md.

**Files:** `docs/specification/23-screen-elements.md`, `docs/specification/trace-index.md`

**Verification:** Trace-index updated with new symbol references

---

## Verification Checklist
1. TrainingScreen renders identically to current behavior
2. All 5 sub-modes work (NORMAL, BOSS, BOSS_MEGA, DRILL, ELITE)
3. All input modes work (VOICE, KEYBOARD, WORD_BANK)
4. Navigation (Next/Prev/Pause/Play/Exit) works in all sub-modes
5. Boss battle progress display works
6. Drill green theme works
7. Report sheet works (flag, hide, export, copy, QR)
8. TTS playback works
9. Voice recognition auto-trigger works
10. `assembleDebug` passes
11. All existing tests pass

## Scope Boundaries
**Do NOT touch:**
- VerbDrillScreen or VocabDrillScreen (already migrated)
- DailyPracticeScreen (already migrated)
- SessionRunner business logic
- TrainingViewModel business logic
- Data stores (MasteryStore, ProgressStore, etc.)
- Navigation/routing logic in GrammarMateApp

## Regression Plan
1. **Build:** `assembleDebug` after each fix
2. **Tests:** `test` after each fix
3. **Per-task verification:** each item from the Verification Checklist
4. **Cross-task regression:** verify boss battle, drill sub-mode, daily practice still work
5. **Visual regression:** compare TrainingScreen appearance before/after migration
6. **UC/AC spot-check:** UC-06, UC-69, UC-70 ACs hold
7. **Spec sync:** update CHANGELOG + trace-index

## Dependency
Should run after TASK-053 (drill standard navigation) to avoid merge conflicts in SessionRunner.

## Git
One commit per fix or one combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: TrainingCardSessionProvider | | |
| | Fix 2: Rewrite TrainingScreen | | |
| | Fix 3: GrammarMateApp wiring | | |
| | Fix 4: Screen element registry | | |
