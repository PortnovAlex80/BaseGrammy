# Progress Feature

## What
Tracks mastery, flower states, streaks, and progress persistence for lesson training.
Manages card-show recording, lesson completion detection, daily cursor advancement,
bad-sentence flagging, and backup restore pipelines.

## API Surface

- **ProgressTracker** — Records card shows for mastery (skips WORD_BANK/boss/drill),
  checks lesson completion, resolves progress-based lesson info, persists state to
  ProgressStore, advances daily cursor, and handles store resets.
  Key methods: `recordCardShowForMastery()`, `saveProgress()`, `checkAndMarkLessonCompleted()`,
  `resolveProgressLessonInfo()`, `advanceCursor()`, `resetStores()`

- **ProgressRestorer** — Restoration pipeline: loads persisted TrainingProgress,
  resolves valid lesson/language references, applies to UI state, returns
  `List<ProgressResult>` commands for the ViewModel to execute.
  Key methods: `applyRestoredProgress()`, `restoreBackup()`, `reloadFromDisk()`
  Injected functions: `normalizeEliteSpeeds`, `resolveEliteUnlocked`, `parseBossRewards`

- **FlowerRefresher** — Recomputes flower visuals for all lessons via FlowerCalculator
  and builds ladder rows via LessonLadderCalculator. Owns its own
  `StateFlow<FlowerDisplayState>` and writes `ladderRows` into core state.
  Key method: `refreshFlowerStates()`

- **StreakManager** — Wraps StreakStore with milestone celebration messages.
  Key method: `recordSubLessonCompletion()` returns `(StreakData, isWorthyEvent)`

- **BadSentenceHelper** — Manages bad-sentence flagging and card hiding for
  training and daily-practice sessions. Tracks in-memory `dailyBadCardIds` for
  daily session scope. Returns `BadSentenceResult` (AdvanceDrillCard, SkipToNextCard, None).
  Key methods: `flagBadSentence()`, `hideCurrentCard()`, `exportBadSentences()`

- **ProgressResult** — Sealed class: `RebuildSchedules`, `BuildSessionCards`,
  `RefreshFlowerStates`, `NormalizeEliteSpeeds(callback)`, `ResolveEliteUnlocked(callback)`,
  `ParseBossRewards(callback)`

- **BadSentenceResult** — Sealed class: `AdvanceDrillCard`, `SkipToNextCard`, `None`

## State owned
- FlowerRefresher owns `StateFlow<FlowerDisplayState>` (lesson flower visuals)
- BadSentenceHelper owns in-memory `dailyBadCardIds` set (daily session scope)
- All other state is written through `TrainingStateAccess` into core TrainingUiState

## Dependencies
- Data stores: MasteryStore, ProgressStore, LessonStore, StreakStore,
  BadSentenceStore, HiddenCardStore, ProfileStore, BackupManager
- Calculators: FlowerCalculator, LessonLadderCalculator
- Shared: TrainingStateAccess interface

## Edit scope warnings
- WORD_BANK never counts for mastery -- do not relax the guard in recordCardShowForMastery
- ProgressTracker is a reader/writer split: reads state as params, writes to stores,
  does NOT directly update TrainingUiState (except via returned results)
- ProgressRestorer uses injected function params for query-style operations --
  changing signatures requires updating the ViewModel wiring
- FlowerRefresher owns its own StateFlow but also writes ladderRows to core state
