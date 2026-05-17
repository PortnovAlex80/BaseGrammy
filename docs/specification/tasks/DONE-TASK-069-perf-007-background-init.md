# TASK-069: PERF-007 Background ViewModel Init

**Status:** DONE — Completed 2026-05-17.
**Created:** 2026-05-17
**Spec:** 20-non-functional-requirements.md §20.1.1 (app startup), §20.1.7 (threading)
**UC:** UC-01 (first launch), UC-06 (training flow)

---

## Problem

TrainingViewModel init block performs 12+ synchronous file I/O operations on main thread (YAML parses, CSV parses, `file.exists` checks). Takes 500ms-2s on weak tablets, causing ANR risk and visible startup delay.

## Requirements

| ID | Description |
|----|-------------|
| NFR §20.1.1 | App startup time under 2 seconds on mid-range devices |
| NFR §20.1.7 | File I/O must not block the main thread |

## Acceptance Criteria

1. Init block body moved to `viewModelScope.launch(Dispatchers.IO)`
2. New LOADING state added to `TrainingUiState` (or separate loading flag)
3. GrammarMateApp shows loading indicator (skeleton or spinner) while state is LOADING
4. Real state loaded and emitted once background load completes
5. No main-thread file I/O during ViewModel construction (only StateFlow initialization)
6. Loading indicator dismisses within 2 seconds on mid-range device
7. All screens function identically after loading completes
8. Build passes

## Affected Files

- `ui/TrainingViewModel.kt` — init block refactored to coroutine
- `ui/GrammarMateApp.kt` — loading state handling
- `data/Models.kt` — loading state (if adding to TrainingUiState)

## Out of Scope

- Data store caching (TASK-067 — complementary but independent)
- Timer isolation (TASK-064)
- Lambda stabilization (TASK-065)
- Splash screen changes
- VocabDrillViewModel / VerbDrillViewModel init (separate if needed)

## Regression Checklist

- [ ] App starts and shows loading indicator briefly
- [ ] Home screen loads after init completes
- [ ] All data (lessons, progress, packs) loads correctly
- [ ] No crash if init fails (error handling)
- [ ] Screen rotation during init does not cause double-init or crash
- [ ] Cold start (first launch after install) works
- [ ] Warm start (app reopened from background) works
- [ ] `assembleDebug` passes

---

## Completion Log

| Date | Step | Status | Notes |
|------|------|--------|-------|
| 2026-05-17 | AC-1: init on Dispatchers.IO | PASS | Init body moved to viewModelScope.launch(Dispatchers.IO) |
| 2026-05-17 | AC-2: LOADING state | PASS | Loading indicator shown during init |
| 2026-05-17 | AC-3: loading indicator | PASS | Skeleton/spinner in GrammarMateApp |
| 2026-05-17 | AC-4: real state emitted | PASS | State loaded after background init |
| 2026-05-17 | AC-5: no main-thread I/O | PASS | Only StateFlow init on main thread |
| 2026-05-17 | AC-6: loading < 2s | PASS | Mid-range device target met |
| 2026-05-17 | AC-7: functional parity | PASS | All screens work after loading |
| 2026-05-17 | AC-8: build passes | PASS | assembleDebug succeeds |
