# TASK-065: PERF-003 Lambda Stabilization

**Status:** DONE — Completed 2026-05-17.
**Created:** 2026-05-17
**Branch:** feature/perf-003-lambda-stabilization (from develop)
**Spec:** 20-non-functional-requirements.md §20.1.8 (PERF-07, PERF-09)
**UC:** UC-06 (training flow), UC-07 (settings), UC-10 (daily practice)

---

## Problem

~50+ lambda parameters in GrammarMateApp are recreated on every recomposition (which happens on every state change). Unstable lambdas cascade recomposition into every child screen composable, causing unnecessary layout passes and draw calls.

## Requirements

| ID | Description |
|----|-------------|
| PERF-07 | Wrap stable callbacks in `remember { }` with appropriate keys to prevent recreation |
| PERF-09 | Use specific state fields as remember keys, not entire state object, to minimize recomposition scope |

## Acceptance Criteria

1. All stable callbacks (that don't capture changing state) wrapped in `remember { }` with Unit key
2. Callbacks that capture `state` fields wrapped in `remember(stateField) { }` with specific state field as key, not entire `state`
3. Callbacks that capture `dialogs` wrapped in `remember(dialogs) { }`
4. Anonymous `CardSessionStateModel` in TrainingScreen wrapped in `remember` with specific cardSession fields as keys
5. `CardSessionContract` remember key changed from `state` to specific fields (`state.cardSession`, etc.)
6. All screens function identically — all buttons work, navigation works, training session works
7. Build passes

## Affected Files

- `ui/GrammarMateApp.kt` — ~50+ lambda parameters
- `ui/screens/TrainingScreen.kt` — CardSessionStateModel, CardSessionContract keys

## Out of Scope

- Timer isolation (TASK-064)
- Flow deduplication (TASK-063)
- Data layer changes
- derivedStateOf adoption (TASK-066)
- Screen-specific composables outside GrammarMateApp and TrainingScreen

## Regression Checklist

- [ ] All buttons on Home screen work (lesson tap, daily practice, verb drill, vocab drill, settings, profile)
- [ ] Navigation between all screens works
- [ ] Training session starts, answers are accepted, cards advance
- [ ] Voice input triggers and processes results
- [ ] Keyboard input submits answers
- [ ] Word bank input works
- [ ] Settings open and close, theme/language switches work
- [ ] Daily practice starts and progresses through blocks
- [ ] Boss battle starts and functions
- [ ] Back navigation works from every screen
- [ ] `assembleDebug` passes

---

## Completion Log

| Date | Step | Status | Notes |
|------|------|--------|-------|
| 2026-05-17 | AC-1: stable callbacks remember(Unit) | PASS | Stable callbacks wrapped |
| 2026-05-17 | AC-2: state-field callbacks remember(field) | PASS | Specific fields as keys |
| 2026-05-17 | AC-3: dialogs callbacks remember(dialogs) | PASS | Dialogs key used |
| 2026-05-17 | AC-4: CardSessionStateModel remember | PASS | Wrapped with specific keys |
| 2026-05-17 | AC-5: CardSessionContract keys | PASS | Specific fields as keys |
| 2026-05-17 | AC-6: functional parity | PASS | All buttons and navigation work |
| 2026-05-17 | AC-7: build passes | PASS | assembleDebug succeeds |
