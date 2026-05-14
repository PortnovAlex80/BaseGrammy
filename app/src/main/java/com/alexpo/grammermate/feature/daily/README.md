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
  - `repeatDailyPractice(lessonLevel, resolveProgressLessonInfo): Boolean`
  - `advanceDailyTask(onPersistVerbProgress): Boolean`
  - `recordDailyCardPracticed(blockType, resolveCardLessonId)`
  - `advanceDailyBlock(): Boolean` / `repeatDailyBlock(...): Boolean`
  - `cancelDailySession(): Int?` -- returns sentence count for cursor advancement
  - `rateVocabCard(rating: SrsRating)` -- Anki-style AGAIN/HARD/GOOD/EASY
  - `submitDailySentenceAnswer(input): Boolean` / `submitDailyVerbAnswer(input): Boolean`
  - `prebuildSession(packId, langId, lessonId, lessonLevel, cursor)` -- background init
  - `updateCursor(cursor)`, `getCursor(): DailyCursorState`, `resetState()`
  - Constructor takes `verbDrillStoreFactory: (String?) -> VerbDrillStore` and
    `wordMasteryStoreFactory: (String?) -> WordMasteryStore` for pack-scoped access

- **DailyPracticeSessionProvider** -- CardSessionContract adapter for Blocks 1 & 3.
  Block 2 (Vocab) does NOT use this -- the UI switches composables at block boundaries.
  - Constructor: `DailyPracticeSessionProvider(tasks, blockType, onBlockComplete, ...)`
  - Implements `CardSessionContract` with TTS, voice input, word bank, flagging support
  - Delegates retry/hint logic to `CardSessionStateMachine`
  - `submitAnswer()`, `nextCard()`, `prevCard()`, `showAnswer()`, `setInputMode(mode)`
  - `currentVerbDrillCard(): VerbDrillCard?`, `getConjugationCards(verb, tense)`

## State owned
`DailyPracticeState` via internal MutableStateFlow:
- `dailySession: DailySessionState` (active, tasks, taskIndex, blockIndex, level, finishedToken)
- `dailyCursor: DailyCursorState` (sentenceOffset, currentLessonIndex, firstSessionDate, firstSessionCardIds)
Private mutable: `prebuiltDailySession`, `lastDailyTasks`, `dailyPracticeAnsweredCounts`, `dailyCursorAtSessionStart`

## Dependencies
- `feature.training.AnswerValidator`, `feature.training.CardSessionStateMachine`
- `data.LessonStore`, `data.MasteryStore`, `data.VerbDrillStore`, `data.WordMasteryStore`
- `data.SpacedRepetitionConfig`, `data.TrainingConfig`
- `data.DailyTask` sealed class (TranslateSentence, VocabFlashcard, ConjugateVerb)

## Edit scope warnings
- DailyPracticeCoordinator holds significant private mutable state for cursor
  rollback and block tracking. Reset logic must clear all of it.
- DailySessionComposer builds blocks in parallel (coroutineScope + async).
  Block builders are suspend functions but internally synchronous -- do not
  add shared mutable state to the composer.
- DailyPracticeSessionProvider is created fresh per block transition -- do not
  cache it across block boundaries.
