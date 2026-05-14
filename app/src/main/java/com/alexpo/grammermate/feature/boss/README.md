# Boss Feature

## What
End-of-lesson boss battle challenge testing pattern stability under time pressure.
Supports three boss types (LESSON, MEGA, ELITE) and a Mix Challenge mode.
Reward tiers: Bronze (30%), Silver (60%), Gold (90%) of cards completed.

## API Surface

- **BossBattleRunner** -- Pure-logic class, no Android dependencies, no TrainingStateAccess.
  Testable with plain JUnit. Key methods:
  - `startBoss(type, cards, selectedLessonId, completedSubLessonCount, testMode): BossStartResult`
  - `updateBossProgress(progress, currentTotal, currentReward): BossProgressUpdate`
  - `finishBoss(bossType, bossProgress, bossTotal, selectedLessonId, ...): BossFinishResult`
  - `resolveBossReward(progress, total): BossReward?`
  - `clearBossRewardMessage(bossActive, sessionState, currentCard, inputMode): BossRewardClearResult`

- **BossOrchestrator** -- Wires BossBattleRunner + CardProvider + SessionRunner together.
  Holds `TrainingStateAccess` and an owned `StateFlow<BossState>`.
  Methods return `List<BossCommand>` for the ViewModel to execute side effects:
  - `startBossLesson() / startBossMega() / startBossElite(): List<BossCommand>`
  - `startMixChallenge(): Pair<Boolean, List<BossCommand>>`
  - `finishBoss(): List<BossCommand>`
  - `updateBossProgress(progress): List<BossCommand>`
  - `advanceBossProgressOnNextCard(nextIndex, totalCards): Pair<BossAdvanceResult, List<BossCommand>>`
  - `clearBossRewardMessage(): List<BossCommand>`
  - `initRewards(lessonRewards, megaRewards)`, `resetState()`, `resetStateKeepRewards()`

- **BossResult** -- Sealed class `BossCommand` with variants:
  `PauseTimer`, `ResumeTimer`, `SaveProgress`, `BuildSessionCards`,
  `RefreshFlowerStates`, `ResetBoss`, `ResetDailySession`, `ResetStory`,
  `ResetVocabSprint`, `Composite(commands)`.

## State owned
Owns `BossState` via internal `MutableStateFlow` (exposed as `stateFlow`).
Fields: `bossActive`, `bossType`, `bossTotal`, `bossProgress`, `bossReward`,
`bossRewardMessage`, `bossFinishedToken`, `bossLastType`, `bossErrorMessage`,
`bossLessonRewards`, `bossMegaRewards`.
Also writes to `TrainingUiState.cardSession` and `navigation` via `stateAccess.updateState{}`.

## Dependencies
- `feature.daily.TrainingStateAccess` -- state read/write interface
- `feature.training.CardProvider` -- builds boss card pools
- `feature.training.SessionRunner` -- sets/clears session cards
- `data.ProgressStore` -- loads progress for session restoration after finish
- `data.MasteryStore` -- checks started lessons for Mix Challenge
- `data.Models` -- BossState, BossType, BossReward, SentenceCard, SessionState, TrainingConfig

## Edit scope warnings
- BossOrchestrator updates BOTH its own BossState AND TrainingUiState.cardSession.
  Any refactor to cardSession fields will likely require changes here.
- BossCommand list is the ONLY communication channel to the ViewModel for side effects.
  Do not add callbacks or direct ViewModel method calls.
- BossBattleRunner is pure logic -- keep it free of Android and TrainingStateAccess.
