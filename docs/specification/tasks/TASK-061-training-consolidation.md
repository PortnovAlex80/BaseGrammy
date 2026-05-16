# TASK-061: Training Consolidation — One Engine, One Screen

**Status:** OPEN
**Created:** 2026-05-17
**Branch:** feature/training-consolidation (from main)
**Spec:** 12-training-card-session.md §12.1.1, user-journey-models.md DP-04
**UC:** All card training UCs (UC-01 through UC-74)
**Scenario:** scenario-01 through scenario-11

---

## Problem

Three separate card session implementations exist for identical behavior:
- `SessionRunner` — inline retry via `SessionState.HINT_SHOWN` (TrainingScreen)
- `CardSessionStateMachine` in `VerbDrillCardSessionProvider` — retry for VerbDrill
- `CardSessionStateMachine` in `DailyPracticeSessionProvider` — retry for DailyPractice

Per DP-04: one engine, one logic. The core pipeline is identical everywhere:
```
card → prompt → input → validate → correct/wrong → retry 3x → hint → next
```

Only 3 things differ between modes (none of them are logic):
1. Card source — `List<SessionCard>` on input
2. Completion callback — what happens on session end
3. UI slots — chips for verb cards only

VerbDrillScreen session portion and DailyPracticeScreen card blocks must render through TrainingScreen instead of their own TrainingCardSession calls.

## Changes

### Fix 1: Unify retry/submit in SessionRunner
**Discrepancy:** N/A | **UC:** UC-27 AC5, UC-59 | **Spec:** DP-04, 12-training-card-session.md §12.1.1

SessionRunner currently implements retry inline via `SessionState.HINT_SHOWN` transitions. Replace with `CardSessionStateMachine` (already shared by VerbDrill and DailyPractice). This eliminates the duplicate retry implementation.

**What to do:**
1. In `SessionRunner`, add a `CardSessionStateMachine` instance
2. Replace inline `incorrectAttemptsForCard` / `HINT_THRESHOLD` logic with `CardSessionStateMachine.onSubmit()`
3. `submitAnswer()` delegates to state machine, gets `Correct`/`Wrong`/`MaxAttempts` result
4. `togglePause()` delegates to state machine (simple pause/resume — no hint-aware advance)
5. `showAnswer()` delegates to state machine
6. Remove `AnswerValidator.HINT_THRESHOLD` constant (state machine owns max attempts)
7. Ensure `voiceTriggerToken` increments on wrong answer (state machine already does this)

**Files:** `feature/training/SessionRunner.kt` — `submitAnswer()`, `togglePause()`, `showAnswer()`, `nextCardInternal()`

**Verification:** TrainingScreen retry behavior unchanged (3 attempts, auto-hint, voice retry)

### Fix 2: Add mode parameter to TrainingScreen
**Discrepancy:** N/A | **UC:** All | **Spec:** 12-training-card-session.md §12.1.1

Add a `TrainingMode` enum and `mode` parameter to `TrainingScreen`. Mode controls:
- Header subtitle (tense+prompt for NORMAL, "Review Session" for BOSS, etc.)
- Card content slot (chips for VERB_DRILL/DAILY_VERBS)
- Completion screen variant

**What to do:**
1. Define `enum class TrainingMode { NORMAL, BOSS, BOSS_MEGA, ELITE, DRILL, VERB_DRILL, DAILY }`
2. Add `mode: TrainingMode = TrainingMode.NORMAL` parameter to `TrainingScreen`
3. In `TrainingHeaderSlot`, use mode to decide subtitle text
4. In `TrainingCardContentSlot`, detect `VerbDrillCard` and render chips when present
5. In completion rendering, use mode to pick completion screen variant

**Files:** `ui/screens/TrainingScreen.kt`, `data/Models.kt` (add TrainingMode enum)

**Verification:** TrainingScreen renders correctly for all 7 modes via mode parameter

### Fix 3: VerbDrill → TrainingScreen routing
**Discrepancy:** N/A | **UC:** UC-69 | **Spec:** 10-verb-drill.md §10.1.3, 12.1.1

Remove VerbDrillScreen session rendering. After selection, navigate to TrainingScreen(mode=VERB_DRILL) with VerbDrill card source.

**What to do:**
1. In `VerbDrillSelectionScreen`, change "Continue" button to navigate to `Routes.TRAINING` (or new route) with verb drill cards
2. Create thin `VerbDrillCardSource` that provides: `List<VerbDrillCard>`, completion callback (combo progress), chip data
3. Wire TrainingScreen to accept this card source when mode=VERB_DRILL
4. VerbDrillScreen's `VerbDrillSessionWithCardSession` composable → DELETED
5. Keep VerbDrillSelectionScreen unchanged (tense/group picker)
6. Keep VerbDrillViewModel for card selection, progress, batch management

**Files:** `ui/VerbDrillScreen.kt` (remove session portion), `ui/GrammarMateApp.kt` (routing), `ui/screens/TrainingScreen.kt`

**Verification:** VerbDrill selection → TrainingScreen renders with chips → completion shows stats → exit to HOME

### Fix 4: DailyPractice → TrainingScreen for blocks 1&3
**Discrepancy:** N/A | **UC:** UC-21 | **Spec:** 09-daily-practice.md, 12.1.1

DailyPracticeScreen blocks 1 and 3 delegate card rendering to TrainingScreen instead of rendering their own TrainingCardSession.

**What to do:**
1. In `DailyPracticeScreen.CardSessionBlock`, replace direct `TrainingCardSession` call with `TrainingScreen(mode=DAILY, ...)` call
2. Pass block type as parameter (TRANSLATE or VERBS) for header chip
3. Block 3 (VERBS) passes VerbDrillCard data → TrainingScreen renders chips
4. Block 2 (Vocab) — UNCHANGED (Anki paradigm)
5. Block transitions and sparkle overlay remain in DailyPracticeScreen
6. `DailyPracticeSessionProvider` becomes thinner — card source + completion callback only

**Files:** `ui/screens/DailyPracticeScreen.kt`, `feature/daily/DailyPracticeSessionProvider.kt`

**Verification:** Daily Practice block 1 renders in TrainingScreen → block 2 unchanged → block 3 renders with chips → completion

### Fix 5: Delete duplicate code
**Discrepancy:** N/A | **UC:** DP-04 | **Spec:** 12-training-card-session.md §12.1.1

Remove all code that was only needed for separate screen rendering.

**What to do:**
1. Delete `VerbDrillCardSessionProvider.submitAnswerWithInput()` — SessionRunner handles submit
2. Delete `VerbDrillCardSessionProvider.navigateNext()/navigatePrev()` — standard nav
3. Delete `DailyPracticeSessionProvider.navigateNext()/navigatePrev()` — standard nav
4. Delete duplicate voice auto-launch code — shared VoiceAutoLauncher
5. Delete duplicate auto-advance code — shared via SessionRunner/TrainingScreen
6. Remove unused imports

**Files:** `ui/VerbDrillCardSessionProvider.kt`, `feature/daily/DailyPracticeSessionProvider.kt`

**Verification:** Build passes, no dead code warnings for modified files

---

## Verification Checklist
1. TrainingScreen NORMAL mode: sub-lessons, mastery, flowers — unchanged
2. TrainingScreen BOSS mode: review session, boss rewards — unchanged
3. TrainingScreen VERB_DRILL mode: verb/tense chips, combo progress, voice retry
4. TrainingScreen DAILY mode (block 1): translation cards, no chips
5. TrainingScreen DAILY mode (block 3): verb cards with chips, = VerbDrill
6. Word bank visible from session start in ALL modes
7. Voice auto-retry after wrong answer in ALL modes
8. Play = resume, Next = advance in ALL modes
9. Flag/hide/report works in ALL modes
10. Completion screens show correct stats per mode
11. VerbDrill selection → TrainingScreen navigation works
12. DailyPractice block transitions + sparkle overlay work
13. `assembleDebug` passes
14. All existing tests pass

## Scope Boundaries
**Do NOT touch:**
- VocabDrill (excluded — different paradigm)
- VerbDrillSelectionScreen (stays as pre-screen)
- VerbDrillViewModel (card selection logic)
- DailyCoordinator/DailySessionComposer (block composition)
- Card data models (SentenceCard, VerbDrillCard)
- FlowerCalculator, MasteryStore, ProgressStore

## Regression Plan
1. Build: `assembleDebug`
2. Per-mode verification: all 7 modes render through TrainingScreen
3. UC spot-check: UC-01 (start training), UC-27 (verb drill submit), UC-21 (daily practice), UC-59 (voice)
4. Cross-task: VocabDrill, HomeScreen, Settings, LessonRoadmap unaffected
5. Spec sync: verify §12.1.1 ASCII mockups match rendered screens

## Execution Order
1. Fix 1 (SessionRunner retry unification)
2. Fix 2 (TrainingScreen mode parameter)
3. Fix 3 (VerbDrill routing)
4. Fix 4 (DailyPractice routing)
5. Fix 5 (Delete duplicate code)
6. Build + regression

## Git
One commit per fix or combined. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---
## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
