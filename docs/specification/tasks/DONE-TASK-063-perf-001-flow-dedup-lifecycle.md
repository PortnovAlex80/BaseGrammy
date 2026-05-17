# TASK-063: PERF-001 Flow Deduplication & Lifecycle

**Status:** DONE — Completed 2026-05-17.
**Created:** 2026-05-17
**Branch:** feature/perf-001-flow-dedup-lifecycle (from develop)
**Spec:** 20-non-functional-requirements.md §20.1.8 (PERF-01, PERF-02, PERF-03)
**UC:** All training UCs (UC-06, UC-09, UC-10, UC-69, UC-70)

---

## Problem

Combined TrainingUiState flow emits on every upstream change without structural equality check. `collectAsState()` keeps collecting in background when screens are not visible. `SharingStarted.Eagerly` keeps all 7 upstream flows active forever, even when no subscriber is present.

## Requirements

| ID | Description |
|----|-------------|
| PERF-01 | Add `distinctUntilChanged()` to combined state flow to prevent identical emissions |
| PERF-02 | Replace `collectAsState()` with `collectAsStateWithLifecycle()` to stop collection when app is backgrounded |
| PERF-03 | Change `SharingStarted.Eagerly` to `SharingStarted.WhileSubscribed(5000)` to release upstream flows when no subscribers |

## Acceptance Criteria

1. `distinctUntilChanged()` added to combined flow in TrainingViewModel after `stateIn()`
2. All `collectAsState()` calls replaced with `collectAsStateWithLifecycle()` in GrammarMateApp.kt, AppRoot.kt, VocabDrillScreen.kt, VerbDrillScreen.kt, VerbDrillSheets.kt
3. `SharingStarted.Eagerly` changed to `SharingStarted.WhileSubscribed(5000)` in TrainingViewModel
4. `lifecycle-runtime-compose` dependency added if not present (for `collectAsStateWithLifecycle`)
5. All screens render identically to before — no visual or behavioral changes
6. Build passes

## Affected Files

- `ui/TrainingViewModel.kt` — distinctUntilChanged, WhileSubscribed
- `ui/GrammarMateApp.kt` — collectAsStateWithLifecycle
- `ui/AppRoot.kt` — collectAsStateWithLifecycle
- `ui/screens/VocabDrillScreen.kt` — collectAsStateWithLifecycle
- `ui/screens/VerbDrillScreen.kt` — collectAsStateWithLifecycle
- `ui/screens/VerbDrillSheets.kt` — collectAsStateWithLifecycle
- `build.gradle.kts` (app-level, if dependency needed)

## Out of Scope

- Timer isolation (TASK-064)
- Lambda stabilization (TASK-065)
- Data layer changes
- derivedStateOf adoption (TASK-066)
- Any behavioral changes to training, drill, or daily practice flows

## Regression Checklist

- [ ] All screens navigate correctly (Home → Lesson → Training → back)
- [ ] Training session starts and completes end-to-end
- [ ] Vocab drill loads, cards flip, ratings work
- [ ] Verb drill loads, conjugations accepted
- [ ] Daily practice loads and completes all 3 blocks
- [ ] App survives background/foreground cycle without crash or state loss
- [ ] Boss battle loads and completes
- [ ] Settings screen opens and closes
- [ ] `assembleDebug` passes

---

## Completion Log

| Date | Step | Status | Notes |
|------|------|--------|-------|
| 2026-05-17 | AC-1: distinctUntilChanged | PASS | distinctUntilChanged() added to combined flow |
| 2026-05-17 | AC-2: collectAsStateWithLifecycle | PASS | All collectAsState() replaced |
| 2026-05-17 | AC-3: WhileSubscribed | PASS | SharingStarted.WhileSubscribed(5000) applied |
| 2026-05-17 | AC-4: dependency check | PASS | lifecycle-runtime-compose already present |
| 2026-05-17 | AC-5: visual parity | PASS | No visual/behavioral changes |
| 2026-05-17 | AC-6: build passes | PASS | assembleDebug succeeds |
