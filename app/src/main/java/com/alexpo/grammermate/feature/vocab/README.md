# Vocab Feature

## What
In-lesson vocab sprint mode: Anki-style SRS flashcard practice where the user
translates vocabulary entries. Manages session setup, answer validation (up to 3
attempts with answer reveal), word bank generation with distractors, and SRS
progress recording (correct/incorrect tracking per entry).

## API Surface

- **VocabSprintRunner** -- Stateful session runner owning
  `StateFlow<VocabSprintState>`. All state changes via TrainingStateAccess:
  - `openSprint(resume: Boolean = false): VocabResult` -- starts a new or resumed sprint
  - `submitAnswer(inputOverride: String? = null): VocabSubmitResult` -- validates, records SRS, advances
  - `showAnswer()` -- reveals the correct answer after 3 failed attempts
  - `onInputChanged(text: String)` -- handles typing; resets attempts if user retypes after reveal
  - `setInputMode(mode: InputMode)` -- switches input mode; regenerates word bank for WORD_BANK
  - `requestVoice()` -- triggers voice recognition, increments voiceTriggerToken
  - `clearError()`, `resetState()`, `updateMasteredCount(count: Int)`
  - Constructor: `VocabSprintRunner(stateAccess, lessonStore, vocabProgressStore, answerValidator)`

- **VocabResult** -- Sealed result types returned by sprint methods:
  - `SaveAndBackup` -- sprint finished, caller should persist and backup
  - `ResetBoss` -- cross-module signal to reset boss state (sprint takes priority)
  - `None` -- no action needed

- **VocabSoundResult** -- Sealed sound feedback:
  - `PlaySuccess`, `PlayError`, `None`

- **VocabSubmitResult** -- Combines both: `data class VocabSubmitResult(sound: VocabSoundResult, action: VocabResult)`

## State owned
`VocabSprintState` via internal MutableStateFlow. Fields:
`currentVocab`, `vocabInputText`, `vocabAttempts`, `vocabAnswerText`,
`vocabIndex`, `vocabTotal`, `vocabWordBankWords`, `vocabFinishedToken`,
`vocabErrorMessage`, `vocabInputMode`, `vocabVoiceTriggerToken`, `vocabMasteredCount`.
Also holds `vocabSession: List<VocabEntry>` as a public mutable var (set by openSprint).

## Dependencies
- `feature.daily.TrainingStateAccess` -- state read/write interface
- `feature.training.AnswerValidator` -- answer normalization and matching
- `data.LessonStore` -- loads vocab entries per lesson/language
- `data.VocabProgressStore` -- SRS progress: correct/incorrect records, completed indices, sprint sorting
- `data.VocabEntry` -- lesson vocab entry model
- `data.Normalizer` -- text normalization for answer comparison

## Edit scope warnings
- `openSprint` returns `VocabResult.ResetBoss` on success -- the ViewModel must
  handle this cross-module signal to clear boss state. Do not remove this.
- Word bank generation falls back to all lessons when the session pool has fewer
  than 4 distractors. This cross-lesson lookup can be slow for large packs.
- `vocabSession` is a public mutable var, not part of the StateFlow. It must be
  set before any submitAnswer/moveToNextVocab calls. Do not add StateFlow
  indirection -- it would break the openSprint -> submit flow.
- 3-attempt limit is hardcoded. After 3 wrong attempts the answer is revealed
  and the user must retype. Changing this requires updating both the runner
  and the UI (VocabSprintScreen).
