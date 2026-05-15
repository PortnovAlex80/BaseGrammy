# TASK-045: Unify Card Session State Management (Architectural)

**Status:** OPEN
**Created:** 2026-05-16
**Branch:** feature/unify-card-session-state (from main)
**Spec:** 08-training-viewmodel.md §5, 10-verb-drill.md §5, 12-training-card-session.md
**UC:** UC-06, UC-25, UC-20
**Scenario:** scenario-01, scenario-07, scenario-06
**User Journey:** user-journey-models.md Structural Issues STRUCT-001, STRUCT-004
**Priority:** Architectural — schedule after TASK-039 through TASK-044 are complete

---

## Problem

Three different state management mechanisms exist for the same concept (card session state):

1. **TrainingScreen** uses `SessionState` enum: `ACTIVE`, `PAUSED`, `HINT_SHOWN`, `AFTER_CHECK` (dead)
   - State stored in `CardSessionState` inside `TrainingUiState`
   - Transitions managed by `SessionRunner`

2. **VerbDrillScreen** uses `CardSessionStateMachine` with separate `isPaused` (MutableStateFlow) + `hintAnswer` + `showIncorrectFeedback`
   - State managed by `VerbDrillCardSessionProvider`
   - `sessionActive` is a computed property from 3 sources

3. **DailyPracticeScreen** uses `DailyPracticeSessionProvider` with its own local state
   - No shared state model with Training or VerbDrill

This fragmentation causes:
- **Inconsistent behavior across modes** — same button (Play) does different things in different modes
- **Bug duplication** — fixing a state bug in Training doesn't fix it in VerbDrill or Daily
- **Maintenance burden** — every state change must be implemented 3 times
- **Verification complexity** — regression checks must verify 3 separate implementations

Additionally, inputText clearing strategy is inconsistent (STRUCT-004): VOICE mode clears on wrong answer, KEYBOARD mode keeps text. This is a UX inconsistency, not a bug, but should be documented as intentional or unified.

## Proposed Solution

Define a unified `CardSessionStateModel` interface that all three providers implement:

```kotlin
interface CardSessionStateModel {
    val isActive: Boolean
    val isPaused: Boolean
    val isHintShown: Boolean
    val canSubmit: Boolean
    val canAdvance: Boolean

    fun submitAnswer(input: String): SubmitResult
    fun togglePause()
    fun showAnswer()
    fun nextCard()
}
```

Each mode (Training, VerbDrill, Daily) provides its own implementation but the UI layer (TrainingCardSession, buttons) only depends on the interface.

**This is a multi-step architectural change. Do NOT implement in a single wave.**

## Implementation Phases

### Phase 1: Define interface (data layer)
- Create `CardSessionStateModel` interface in `data/Models.kt` or a new file
- Define `SubmitResult` sealed class

### Phase 2: Adapt SessionRunner (training)
- Make SessionRunner implement the interface
- Map SessionState enum to interface properties
- Keep SessionState enum internal to SessionRunner

### Phase 3: Adapt VerbDrillCardSessionProvider
- Make it implement the interface
- Map CardSessionStateMachine to interface properties
- Unify `sessionActive` computation

### Phase 4: Adapt DailyPracticeSessionProvider
- Make it implement the interface
- Map daily-specific state to interface properties

### Phase 5: Update TrainingCardSession UI
- Change composable to depend on CardSessionStateModel
- Remove mode-specific conditionals from shared UI code

### Phase 6: Remove dead state
- Remove `AFTER_CHECK` from SessionState (covered by TASK-039, but ensure here too)
- Remove any other unused state values

## Risks

- **High blast radius:** Changes affect TrainingScreen, VerbDrillScreen, and DailyPracticeScreen
- **Behavioral regression:** Each adapter must perfectly replicate current behavior
- **Context budget:** Large change spanning multiple files
- **Mitigation:** Implement one phase per wave, full regression after each phase

## Verification Checklist
1. TrainingScreen play/pause/submit/retry works identically to current behavior
2. VerbDrillScreen play/pause/submit/retry works identically to current behavior
3. DailyPracticeScreen submit works identically to current behavior
4. All three modes share the same button rendering code (TrainingCardSession)
5. No mode-specific conditionals in shared UI components
6. `assembleDebug` passes
7. All existing tests pass
8. Full end-to-end regression: training, boss, daily, verb drill, vocab drill

## Scope Boundaries
**Do NOT touch during this task:**
- VocabDrillScreen (uses completely different UI paradigm — flashcard flip, no submit)
- GrammarMateApp.kt navigation
- Data persistence (stores, YAML files)
- Mastery calculation logic

## Regression Plan
1. **Build:** `assembleDebug` after each phase
2. **Tests:** `test` after each phase
3. **Full regression:** run all scenarios (scenario-01 through scenario-10) after Phase 6
4. **Manual verification:** test each mode end-to-end on device

## Git
One commit per phase. Footer:
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>

---

## Completion Log
| Date | Phase | Status | Notes |
|------|-------|--------|-------|
| | Phase 1: Define interface | | |
| | Phase 2: Adapt SessionRunner | | |
| | Phase 3: Adapt VerbDrill | | |
| | Phase 4: Adapt DailyPractice | | |
| | Phase 5: Update TrainingCardSession UI | | |
| | Phase 6: Remove dead state | | |
