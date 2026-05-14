# Training Feature

## What
Core training session engine for GrammarMate. Manages the card-session lifecycle (start/finish/pause), answer submission with hint logic, card selection across all training modes (LESSON, ALL_SEQUENTIAL, ALL_MIXED, boss, elite, drill), word bank generation, and story quiz sessions. All classes are pure Kotlin helpers owned by `TrainingViewModel` via the `TrainingStateAccess` interface.

## API Surface

- **SessionRunner** `(stateAccess, appContext, coroutineScope, answerValidator, wordBankGenerator, cardProvider, streakManager, drillProgressStore, getMastery, getSchedule, calculateCompletedSubLessons, onTimerSaveProgress)` -- Session lifecycle: `startSession()`, `finishSession()`, `submitAnswer()` -> `Pair<SubmitResult, List<SessionEvent>>`, `nextCard()`, `prevCard()`, `showAnswer()`, `selectSubLesson(i)`. Also owns drill sub-mode (`startDrill`, `advanceDrillCard`, `finishDrill`), elite sub-mode (`openEliteStep`, `cancelEliteSession`), and timer (`resumeTimer`, `pauseTimer`).

- **AnswerValidator** `(normalizer: Normalizer = Normalizer)` -- Stateless validation: `validate(input, acceptedAnswers, testMode) -> ValidationResult`, `shouldShowHint(incorrectAttempts)`, `getHintText(acceptedAnswers)`. Handles `+`-separated multi-answer CSV format.

- **CardProvider** `(subLessonSize, subLessonSizeMin, subLessonSizeMax, eliteSizeMultiplier, eliteStepCount, progressTracker?)` -- Card selection: `buildSchedules(lessons, existingSchedules)`, `buildSessionCards(lessons, mode, selectedLessonId, schedules, activeSubLessonIndex, hiddenCardIds, mastery) -> CardSetResult`, `buildMixChallengeCards(lessons, startedLessonIds, count)`, `buildBossCards(lessons, type, selectedLessonId, selectedIndex)`. Wraps `MixedReviewScheduler`; caches schedules by composite key.

- **WordBankGenerator** (singleton `object`) -- Pure, no Android deps: `generateForSentence(targetAnswer, allCards, maxDistractors=3)`, `generateForVerb(answer, allAnswers, maxDistractors=8)`, `isDistractor(candidate, normalizedCorrect)`.

- **StoryRunner** `(stateAccess, lessonStore)` -- Story quiz lifecycle: `openStory(phase) -> StoryResult`, `completeStory(phase, allCorrect) -> StoryResult`, `clearStoryError()`, `resetState()`. Owns its own `StateFlow<StoryState>`.

- **CardSessionStateMachine** `(maxAttempts=3, answerProvider: (SessionCard) -> String)` -- Reusable Compose-state holder for retry/hint logic shared by VerbDrill and DailyPractice providers. Key methods: `onSubmit(isCorrect, card, inputMode) -> OnSubmitResult`, `showAnswer(card)`, `onInputChanged(text)`, `reset()`.

## Sealed types

- **SessionEvent** -- Commands returned from `SessionRunner` for the ViewModel to execute: `SaveProgress`, `RefreshFlowerStates`, `UpdateStreak`, `BuildSessionCards`, `PlaySuccess`, `PlayError`, `RecordCardShow`, `MarkSubLessonCardsShown`, `CheckAndMarkLessonCompleted`, `CalculateCompletedSubLessons`, `GetMastery`, `GetSchedule`, `RebuildSchedules`, `Composite`.
- **StoryResult** -- `SaveAndBackup | None`. Returned by `StoryRunner` methods.
- **SessionFinishResult** -- `Empty | EliteCancelled | Completed(rating)`. Inner class of `SessionRunner`.
- **SubmitResult** -- Data class with `accepted`, `hintShown`, `needsBossFinish`, `needsSubLessonComplete`, `needsEliteFinish`, `needsSaveProgress`, `needsFlowerRefresh`. Inner class of `SessionRunner`.
- **OnSubmitResult** -- `Correct(result) | Wrong(attemptNumber, remaining) | HintShown(answer)`. Inner sealed class of `CardSessionStateMachine`.

## State managed

Reads/writes `TrainingUiState` via `TrainingStateAccess`. Key fields in `cardSession`: `sessionState`, `currentIndex`, `currentCard`, `inputText`, `inputMode`, `correctCount`, `incorrectCount`, `incorrectAttemptsForCard`, `answerText`, `wordBankWords`, `selectedWords`, `voiceTriggerToken`, `voicePromptStartMs`, `activeTimeMs`, `subLessonFinishedToken`. Also writes `boss`, `elite`, `drill`, and `navigation` sub-states.

## Dependencies

- **data stores**: `DrillProgressStore`, `LessonStore` (via constructor)
- **data utilities**: `Normalizer`, `MixedReviewScheduler`, `TrainingConfig`
- **feature/progress**: `ProgressTracker`, `StreakManager`
- **feature/daily**: `TrainingStateAccess` interface (defined in `DailySessionHelper.kt`)
- **Android**: `Application` context, `SystemClock`, `CoroutineScope`

## Edit scope warnings

- **SessionRunner** is the largest file (~750 lines). Answer submission has 6 code paths (boss-last, boss-mid, elite-finish, drill, normal-last, normal-mid) -- edits to `submitAnswer()` must cover all branches.
- **CardProvider** wraps `MixedReviewScheduler` -- schedule cache invalidation uses a composite key; changing card counts or block sizes without clearing `cachedScheduleKey` causes stale schedules.
- **AnswerValidator** is used by `SessionRunner` AND `CardSessionStateMachine` -- signature changes ripple to two callers.
- **WordBankGenerator** is a singleton with no deps -- safest file to edit in isolation.
- **SessionEvent** list must stay in sync with `SessionRunner` callers -- adding an event requires handling in `TrainingViewModel`.
- **Tests to update**: `SessionRunnerTest`, `AnswerValidatorTest`, `CardProviderTest`, `WordBankGeneratorTest` in `app/src/test/`.
