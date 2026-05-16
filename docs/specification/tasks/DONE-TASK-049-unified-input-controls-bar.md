# TASK-049: Unified Input Controls Bar for All Card Modes

**Status:** DONE
**Created:** 2026-05-16
**Branch:** feature/unified-input-bar (from main)
**Spec:** 12-training-card-session.md, 05-audio-tts-asr.md, scenario-05-input-modes.md
**UC:** UC-07 (input mode switching), UC-08 (answer submission)
**Scenario:** scenario-05-input-modes.md
**User Journey:** user-journey-models.md Design Principle DP-01
**Depends on:** TASK-048 (UnifiedNavigationRow), TASK-045 (CardSessionStateModel)

---

## Problem

The input controls bar — containing Mic/Keyboard/WordBank mode buttons, Show Answer (eye), Report (flag), and input type indicator — has different implementations across card modes:

1. **TrainingScreen** has inline input mode buttons + AnswerBox + report sheet trigger
2. **VerbDrillScreen** has its own input controls layout within TrainingCardSession
3. **DailyPracticeScreen** has DailyPracticeSessionProvider-driven input controls

Each mode renders the same logical elements but with different code paths, layout order, and behavioral details. Per DP-01: all card-based modes must use shared components for shared elements.

## Required Unified Elements

The input controls bar is a single container with these slots:

| Element | Purpose | Present in |
|---------|---------|-----------|
| Input mode selector | Mic / Keyboard / WordBank buttons | Training, VerbDrill, DailyPractice (blocks 1&3) |
| Show Answer (eye) | Reveal correct answer, pause session | Training, VerbDrill, DailyPractice (blocks 1&3) |
| Report (flag) | Open report sheet (bad sentence, hide, etc.) | Training, VerbDrill, DailyPractice |
| Input type indicator | Visual label showing current mode (e.g., microphone icon, keyboard icon) | Training, VerbDrill, DailyPractice |
| Submit button (Check) | Submit answer for validation | Training, VerbDrill, DailyPractice |

NOT included (mode-specific):
- Word bank FlowRow (only in WORD_BANK mode, rendered below the bar)
- Voice auto-launch logic (triggered by state, not a UI element)
- Hint answer card (rendered separately as content)

## Changes

### Fix 1: Create UnifiedInputControlsBar composable
**Discrepancy:** N/A | **UC:** UC-07, UC-08 | **Spec:** 12-training-card-session.md

Create a single `UnifiedInputControlsBar` composable that renders the input mode selector + answer field + action buttons as ONE container. It accepts `CardSessionStateModel` (from TASK-045) for state queries and callbacks for actions.

Layout:
```
[Mic] [Keyboard] [WordBank] | [Input field or voice status] | [Eye] [Check] [Report]
```

The bar handles:
- Input mode switching (Mic → Voice, Keyboard → text, Book → WordBank)
- Show answer on eye press
- Submit on Check press
- Report sheet trigger
- Input type indicator (which mode is active)

**Files:** New file `ui/components/UnifiedInputControlsBar.kt`

**Verification:** Component renders identically regardless of which mode provides the state

### Fix 2: Replace TrainingScreen input controls
**Discrepancy:** N/A | **UC:** UC-07 | **Spec:** scenario-05

Replace the inline AnswerBox + input mode buttons in TrainingScreen with UnifiedInputControlsBar. Pass SessionRunner as the state source.

**Files:** `ui/screens/TrainingScreen.kt` — remove inline AnswerBox/input mode logic

**Verification:** Training mode input controls look and behave as before

### Fix 3: Replace VerbDrill input controls
**Discrepancy:** N/A | **UC:** UC-25 | **Spec:** 10-verb-drill.md

Replace VerbDrill's custom input controls with UnifiedInputControlsBar. Pass VerbDrillCardSessionProvider as the state source.

**Files:** `ui/screens/VerbDrillScreen.kt` — remove custom input layout

**Verification:** VerbDrill input controls match Training controls

### Fix 4: Replace DailyPractice input controls
**Discrepancy:** N/A | **UC:** UC-20 | **Spec:** 09-daily-practice.md

Replace DailyPractice card block input controls with UnifiedInputControlsBar. Pass DailyPracticeSessionProvider as the state source.

**Files:** `ui/screens/DailyPracticeScreen.kt`, `feature/daily/DailyPracticeSessionProvider.kt`

**Verification:** DailyPractice (blocks 1&3) input controls match Training controls

### Fix 5: Remove old input control implementations
**Discrepancy:** N/A | **UC:** N/A

After all modes use UnifiedInputControlsBar:
- Delete old AnswerBox from TrainingScreen
- Delete custom input layouts from VerbDrillScreen
- Delete DailyPractice input overrides
- Clean up dead code in TrainingCardSession if applicable

**Files:** TrainingScreen.kt, VerbDrillScreen.kt, DailyPracticeScreen.kt, TrainingCardSession.kt

**Verification:** Grep for old input component names → zero results

---

## Verification Checklist
1. UnifiedInputControlsBar renders identically in Training, VerbDrill, DailyPractice
2. Input mode switching (Mic/Keyboard/WordBank) works in all modes
3. Show Answer (eye) works in all modes — reveals answer, pauses session
4. Check button enabled/disabled logic identical across modes
5. Report sheet opens with same 5 options in all modes
6. Input type indicator shows correct mode icon
7. Word Bank mode: FlowRow appears below bar in all modes
8. Voice mode: auto-launch logic triggers correctly after Play
9. No mode-specific conditionals in UnifiedInputControlsBar code
10. Old implementations fully removed

## Scope Boundaries
**Do NOT touch:**
- VocabDrillScreen (flip-based, no input bar)
- GrammarMateApp.kt
- SessionRunner / ViewModel business logic
- Audio engine (TTS/ASR)
- Mastery/flower/streak

## Regression Plan
1. **Build:** `assembleDebug` after each fix
2. **Tests:** `test` after each fix
3. **Cross-task regression:** full session in all 3 modes, voice input, keyboard input, word bank input
4. **UC/AC spot-check:** UC-07, UC-08

## Git
One commit per fix. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| | Fix 1: Create UnifiedInputControlsBar | | |
| | Fix 2: Replace Training input controls | | |
| | Fix 3: Replace VerbDrill input controls | | |
| | Fix 4: Replace DailyPractice input controls | | |
| | Fix 5: Remove old implementations | | |
