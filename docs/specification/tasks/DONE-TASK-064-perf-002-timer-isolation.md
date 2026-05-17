# TASK-064: PERF-002 Timer Isolation

**Status:** DONE — Completed 2026-05-17.
**Created:** 2026-05-17
**Branch:** feature/perf-002-timer-isolation (from develop)
**Spec:** 20-non-functional-requirements.md §20.1.8 (PERF-04, PERF-05, PERF-06)
**UC:** UC-06 (training flow), UC-73 (pomodoro timer)

---

## Problem

Session timer (`SessionRunner.resumeTimer`) emits full `TrainingUiState` every 500ms. `PomodoroHelper` emits full state every 1s. `saveProgress()` runs YAML serialization + fsync on every tick. `PomodoroHelper` uses non-atomic `.value =` setter instead of `.update { }`.

## Requirements

| ID | Description |
|----|-------------|
| PERF-04 | Isolate timer ticks into dedicated StateFlow — no full-state emission on every tick |
| PERF-05 | Debounce saveProgress() during active timer to max once per 10 seconds |
| PERF-06 | Fix PomodoroHelper to use atomic `.update { }` instead of non-atomic `.value =` |

## Acceptance Criteria

1. New `StateFlow<Long>` (e.g., `_sessionTimerMs` / `sessionTimerMs`) added to TrainingViewModel for live session timer display, ticking every 500ms
2. New `StateFlow<Int>` (e.g., `_pomodoroRemainingSeconds` / `pomodoroRemainingSeconds`) added to TrainingViewModel for live pomodoro countdown
3. `TrainingUiState.cardSession.activeTimeMs` is still updated but ONLY on card transitions, not every 500ms tick
4. `TrainingUiState.pomodoro.remainingSeconds` is still updated but ONLY on pomodoro state changes (start, pause, complete), not every tick
5. `saveProgress()` called max once per 10 seconds during active timer (debounce counter or elapsed time check)
6. `PomodoroHelper.onUpdateState` changed from `_coreState.value = newState` to `_coreState.update { newState }`
7. Timer display in TrainingScreen/PomodoroTimerBanner collects the new separate flows, not the main uiState
8. All timer functionality works identically — session timer counts up, pomodoro counts down, pomodoro state transitions fire correctly
9. Build passes

## Affected Files

- `ui/TrainingViewModel.kt` — new timer StateFlows, saveProgress debounce
- `feature/SessionRunner.kt` — remove full-state emission on timer tick
- `feature/PomodoroHelper.kt` — atomic update, remove full-state emission on tick
- `ui/screens/TrainingScreen.kt` — timer display collects new flows
- `ui/components/PomodoroTimerBanner.kt` (if separate file) — collect new flow

## Out of Scope

- Lambda stabilization (TASK-065)
- YamlListStore caching (TASK-067)
- ViewModel init changes (TASK-069)
- Flow deduplication (TASK-063 — this task assumes it is already done)

## Regression Checklist

- [ ] Session timer displays correctly and counts up in real-time
- [ ] Pomodoro timer counts down and fires break/work transitions
- [ ] Pomodoro pause/resume works
- [ ] Progress saves on session end (no data loss)
- [ ] No state loss during concurrent timer + audio updates
- [ ] Card transitions still update activeTimeMs in state
- [ ] Pomodoro state changes (start, pause, complete) still update remainingSeconds in state
- [ ] Background/foreground cycle preserves timer state
- [ ] `assembleDebug` passes

---

## Completion Log

| Date | Step | Status | Notes |
|------|------|--------|-------|
| 2026-05-17 | AC-1: sessionTimerMs StateFlow | PASS | Dedicated timer StateFlow added |
| 2026-05-17 | AC-2: pomodoroRemainingSeconds StateFlow | PASS | Dedicated pomodoro StateFlow added |
| 2026-05-17 | AC-3: activeTimeMs only on card transitions | PASS | No longer emitted on timer ticks |
| 2026-05-17 | AC-4: remainingSeconds only on state changes | PASS | No longer emitted on timer ticks |
| 2026-05-17 | AC-5: saveProgress debounce | PASS | Max once per 10 seconds |
| 2026-05-17 | AC-6: PomodoroHelper atomic update | PASS | Changed to .update {} |
| 2026-05-17 | AC-7: UI collects new flows | PASS | Timer UI uses dedicated flows |
| 2026-05-17 | AC-8: timer parity | PASS | All timer functionality preserved |
| 2026-05-17 | AC-9: build passes | PASS | assembleDebug succeeds |
