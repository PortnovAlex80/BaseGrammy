# Daily Feature

## What
Unified 3-block daily practice session: sentence translation (Block 1),
vocab flashcards with SRS (Block 2), verb conjugation drills (Block 3).
Each block has 10 tasks. Replaces former Elite mode and Vocab Sprint.

## API Surface

- **TrainingStateAccess** -- Interface for reading/writing shared training state.
  `val uiState: StateFlow<TrainingUiState>`, `updateState{}`, `saveProgress()`.
  Implemented by TrainingViewModel. Also used by boss and vocab modules.

- **BlockProgress** -- Data class for per-block UI progress:
  `blockType`, `positionInBlock`, `blockSize`, `totalTasks`, `globalPosition`.

- **DailySessionComposer** -- Pure builder, no state access. Constructs
  `List<DailyTask>` for a given lesson level and pack.
  - `buildSession(lessonLevel, packId, languageId, lessonId, cumulativeTenses, cursor): List<DailyTask>`
  - `buildRepeatSession(..., sentenceCardIds, verbCardIds): List<DailyTask>`
  - `rebuildBlock(blockType, ...): List<DailyTask>`
  - `TENSE_LADDER: Map<Int, List<String>>` -- cumulative tense progression per level
  - Selection strategies: Block 1 cursor-driven, Block 2 SRS overdue-first,
    Block 3 weak-first with verb+tense collocation grouping

- **DailyPracticeCoordinator** -- Stateful orchestrator owning
  `StateFlow<DailyPracticeState>`. Manages full session lifecycle:
  - `startDailyPractice(resolveProgressLessonInfo, onStoreFirstSessionCardIds): Boolean`
    -- the callback receives the actual pack-cursor lesson ID with the card IDs
  - `repeatDailyPractice(lessonLevel, resolveProgressLessonInfo): Boolean`
  - `advanceDailyTask(onPersistVerbProgress): Boolean`
  - `recordDailyCardPracticed(blockType, sentenceCard, resolveCardLessonId)`
  - `advanceDailyBlock(): Boolean` / `repeatDailyBlock(): Boolean`
  - `cancelDailySession(): Int?` -- returns sentence count for cursor advancement
  - `rateVocabCard(rating: SrsRating)` -- Anki-style AGAIN/HARD/GOOD/EASY
  - `submitDailySentenceAnswer(input): Boolean` / `submitDailyVerbAnswer(input): Boolean`
  - `prebuildSession(packId, langId, lessonId, lessonLevel, cursor)` -- background init
  - `updateCursor(cursor)`, `getCursor(): DailyCursorState`, `resetState()`
  - Constructor takes `verbDrillStoreFactory: (String?) -> VerbDrillStore` and
    `wordMasteryStoreFactory: (String?) -> WordMasteryStore` for pack-scoped access

## State owned
`DailyPracticeState` via internal MutableStateFlow:
- `dailySession: DailySessionState` (active, tasks, taskIndex, blockIndex, level, finishedToken)
- `dailyCursor: DailyCursorState` (sentenceOffset, currentLessonIndex, firstSessionDate, firstSessionCardIds)
Private mutable: `dailyPracticeAnsweredCounts`. `DailySessionPlanCache` owns
prebuilt and first-session block snapshots under exact pack/lesson/date/input keys.
`DailySessionIdentityResolver` selects the pack-cursor lesson for start and
recovers the saved lesson for same-day sentence/verb replay after a restart.

`DailyCursorController` owns pack-specific cursor persistence and advancement.
`DailyTaskProgressRecorder` writes sentence, verb, and vocab progress for the answered card.
`DailyStreakCompletion` records completed block fires and updates the shared streak snapshot.

## Dependencies
- `feature.training.AnswerValidator`, `feature.training.CardSessionStateMachine`
- `data.LessonStore`, `data.MasteryStore`, `data.VerbDrillStore`, `data.WordMasteryStore`
- `data.SpacedRepetitionConfig`, `data.TrainingConfig`
- `data.DailyTask` sealed class (TranslateSentence, VocabFlashcard, ConjugateVerb)

## Edit scope warnings
- DailyPracticeCoordinator owns block tracking and answered-card counts. Reset
  clears those counts and the plan cache; the cursor controller owns persistence.
- DailySessionComposer builds blocks in parallel (coroutineScope + async).
  Block builders are suspend functions but internally synchronous -- do not
  add shared mutable state to the composer.
