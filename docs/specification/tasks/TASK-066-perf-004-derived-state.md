# TASK-066: PERF-004 DerivedStateOf Adoption

**Status:** BACKLOG — Task created for tracking. Not scheduled for current sprint.
**Created:** 2026-05-17
**Spec:** 20-non-functional-requirements.md §20.1.8 (PERF-08)
**UC:** UC-06 (training flow), UC-09 (daily practice), UC-10 (home)

---

## Problem

Zero use of `derivedStateOf` in the entire UI layer. Every derived value (hasCards, mode, isVerbDrillMode, progress fractions, visibility flags) is recomputed on every recomposition instead of only when the underlying inputs change.

## Requirements

| ID | Description |
|----|-------------|
| PERF-08 | Wrap derived computed values in `derivedStateOf` to limit recomputation to only when inputs change |

## Acceptance Criteria

1. All boolean visibility flags in TrainingScreen (hasCards, isComplete, isPaused, etc.) wrapped in `derivedStateOf`
2. All progress fraction calculations wrapped in `derivedStateOf`
3. All mode checks (isVerbDrillMode, isBossMode, etc.) wrapped in `derivedStateOf`
4. HomeScreen progress indicators wrapped in `derivedStateOf`
5. DailyPracticeScreen block progress wrapped in `derivedStateOf`
6. GrammarMateApp dialog visibility flags wrapped in `derivedStateOf`
7. All screens render and function identically
8. Build passes

## Affected Files

- `ui/screens/TrainingScreen.kt`
- `ui/screens/HomeScreen.kt`
- `ui/screens/DailyPracticeScreen.kt`
- `ui/GrammarMateApp.kt`

## Out of Scope

- Lambda stabilization (TASK-065)
- Timer isolation (TASK-064)
- Flow deduplication (TASK-063)
- Data layer changes
- Screen files not listed above

## Regression Checklist

- [ ] Training screen renders correctly in all modes
- [ ] Home screen progress indicators display correctly
- [ ] Daily practice block progress displays correctly
- [ ] All dialog visibility logic works (show/hide on correct conditions)
- [ ] No visual or behavioral changes compared to pre-change behavior
- [ ] `assembleDebug` passes

---

## Completion Log

| Date | Step | Status | Notes |
|------|------|--------|-------|
| | | | Not started — backlog |
