# Module Dependency Map

Generated from actual import statements in `feature/`, `shared/`, and `ui/TrainingViewModel.kt`.

---

## feature/training

| File | Imports from |
|------|-------------|
| `SessionRunner.kt` | `data` (BossType, DrillProgressStore, InputMode, Lesson, LessonMasteryState, LessonSchedule, Normalizer, ScheduledSubLesson, SentenceCard, SessionState, TrainingConfig, TrainingUiState), `feature.daily` (TrainingStateAccess), `feature.progress` (StreakManager) |
| `CardProvider.kt` | `data` (BossType, LanguageId, Lesson, LessonId, LessonMasteryState, LessonSchedule, MixedReviewScheduler, PackId, ScheduledSubLesson, SentenceCard, SubLessonType, TrainingConfig, TrainingMode), `feature.progress` (ProgressTracker) |
| `AnswerValidator.kt` | `data` (Normalizer, TrainingConfig) |
| `WordBankGenerator.kt` | `data` (Normalizer, SentenceCard) |
| `CardSessionStateMachine.kt` | `data` (AnswerResult, InputMode, SessionCard), `androidx.compose.runtime` |
| `StoryRunner.kt` | `data` (LessonStore, StoryPhase, StoryQuiz, StoryState), `feature.daily` (TrainingStateAccess) |
| `StoryResult.kt` | (none — pure Kotlin sealed class) |
| `SessionEvent.kt` | `data` (Lesson, LessonMasteryState, LessonSchedule, ScheduledSubLesson, SentenceCard) |

### Summary

```
feature/training
  -> data (MasteryStore, ProgressStore, LessonStore, DrillProgressStore, MixedReviewScheduler,
           Normalizer, TrainingConfig, SentenceCard, SessionCard, SessionState, BossType,
           LessonSchedule, LessonMasteryState, TrainingUiState, InputMode, TrainingMode, etc.)
  -> feature.daily (TrainingStateAccess)
  -> feature.progress (ProgressTracker, StreakManager)
```

---

## feature/boss

| File | Imports from |
|------|-------------|
| `BossBattleRunner.kt` | `data` (BossReward, BossType, SentenceCard, TrainingConfig) |
| `BossOrchestrator.kt` | `data` (BossReward, BossState, BossType, DailySessionState, Lesson, LessonId, MasteryStore, ProgressStore, SessionState, TrainingMode, TrainingProgress, TrainingUiState), `feature.daily` (TrainingStateAccess), `feature.training` (CardProvider, SessionRunner) |
| `BossResult.kt` | (none — pure Kotlin sealed class) |

### Summary

```
feature/boss
  -> data (BossReward, BossType, BossState, SentenceCard, TrainingConfig, DailySessionState,
           Lesson, LessonId, MasteryStore, ProgressStore, SessionState, TrainingMode,
           TrainingProgress, TrainingUiState)
  -> feature.daily (TrainingStateAccess)
  -> feature.training (CardProvider, SessionRunner)
```

---

## feature/daily

| File | Imports from |
|------|-------------|
| `DailySessionHelper.kt` | `data` (DailyBlockType, TrainingUiState) — defines `TrainingStateAccess` interface |
| `DailySessionComposer.kt` | `data` (DailyBlockType, DailyCursorState, DailyTask, InputMode, LessonStore, SentenceCard, VerbDrillCard, VerbDrillCsvParser, VerbDrillStore, VocabDrillDirection, VocabWord, WordMasteryStore) |
| `DailyPracticeSessionProvider.kt` | `data` (AnswerResult, CardSessionContract, DailyBlockType, DailyTask, InputMode, InputModeConfig, Normalizer, SessionCard, SessionProgress, TtsState, VerbDrillCard), `feature.training` (WordBankGenerator, CardSessionStateMachine) |
| `DailyPracticeCoordinator.kt` | `data` (DailyBlockType, DailyCursorState, DailyPracticeState, DailySessionState, DailyTask, LanguageId, LessonStore, MasteryStore, PackId, SrsRating, SpacedRepetitionConfig, TrainingConfig, VerbDrillCard, VerbDrillComboProgress, VerbDrillStore, WordMasteryState, WordMasteryStore, TrainingUiState), `feature.training` (AnswerValidator) |

### Summary

```
feature/daily
  -> data (DailyBlockType, DailyCursorState, DailyPracticeState, DailySessionState, DailyTask,
           LessonStore, MasteryStore, VerbDrillStore, WordMasteryStore, VerbDrillCard,
           VerbDrillComboProgress, SentenceCard, TrainingUiState, CardSessionContract,
           SpacedRepetitionConfig, etc.)
  -> feature.training (AnswerValidator, WordBankGenerator, CardSessionStateMachine)
```

**Note:** `TrainingStateAccess` is defined in `feature/daily/DailySessionHelper.kt` and is used by ALL feature modules and `shared/`.

---

## feature/progress

| File | Imports from |
|------|-------------|
| `ProgressTracker.kt` | `data` (DailyCursorState, FlowerCalculator, InputMode, LanguageId, Lesson, LessonId, LessonMasteryState, LessonStore, MasteryStore, PackId, ProgressStore, ScheduledSubLesson, SentenceCard, TrainingConfig, TrainingProgress, TrainingUiState), `feature.daily` (TrainingStateAccess) |
| `FlowerRefresher.kt` | `data` (FlowerCalculator, FlowerDisplayState, LessonLadderCalculator, MasteryStore), `feature.daily` (TrainingStateAccess) |
| `BadSentenceHelper.kt` | `data` (BadSentenceStore, HiddenCardStore, TrainingUiState), `feature.daily` (TrainingStateAccess) |
| `StreakManager.kt` | `data` (StreakData, StreakStore) |
| `ProgressRestorer.kt` | `data` (BackupManager, BossReward, LanguageId, Lesson, LessonId, LessonPack, Language, LessonStore, PackId, ProfileStore, ProgressStore, StreakData, StreakStore, TrainingProgress, TrainingUiState, UserProfile), `feature.daily` (TrainingStateAccess) |
| `ProgressResult.kt` | `data` (BossReward, Lesson, LessonMasteryState, LessonSchedule, ScheduledSubLesson) |
| `BadSentenceResult.kt` | (none — pure Kotlin sealed class) |

### Summary

```
feature/progress
  -> data (MasteryStore, ProgressStore, LessonStore, StreakStore, BackupManager,
           FlowerCalculator, LessonLadderCalculator, BadSentenceStore, HiddenCardStore,
           ProfileStore, TrainingUiState, TrainingProgress, etc.)
  -> feature.daily (TrainingStateAccess)
```

---

## feature/vocab

| File | Imports from |
|------|-------------|
| `VocabSprintRunner.kt` | `data` (InputMode, LessonStore, Normalizer, VocabEntry, VocabProgressStore, VocabSprintState, TrainingUiState), `feature.daily` (TrainingStateAccess), `feature.training` (AnswerValidator) |
| `VocabResult.kt` | (none — pure Kotlin sealed class) |

### Summary

```
feature/vocab
  -> data (LessonStore, VocabProgressStore, VocabEntry, VocabSprintState, TrainingUiState,
           InputMode, Normalizer)
  -> feature.daily (TrainingStateAccess)
  -> feature.training (AnswerValidator)
```

---

## shared/audio

| File | Imports from |
|------|-------------|
| `AudioCoordinator.kt` | `data` (AsrEngine, AsrModelManager, AsrState, AppConfigStore, AudioState, DownloadState, TtsProvider, TtsModelManager, TtsModelRegistry, TtsState), `feature.daily` (TrainingStateAccess), `android.media` (SoundPool) |

### Summary

```
shared/audio
  -> data (TtsProvider, TtsModelManager, TtsModelRegistry, TtsState,
           AsrEngine, AsrModelManager, AsrState, AppConfigStore, AudioState, DownloadState)
  -> feature.daily (TrainingStateAccess)
```

---

## shared (top-level)

| File | Imports from |
|------|-------------|
| `SettingsActionHandler.kt` | `data` (AppConfigStore, BackupManager, DailyCursorState, DailySessionState, HintLevel, Lesson, ProfileStore, SessionState, UserProfile, WordMasteryStore), `feature.daily` (TrainingStateAccess) |
| `SettingsResult.kt` | (none — pure Kotlin sealed class with Android Application reference in data class) |

### Summary

```
shared
  -> data (AppConfigStore, BackupManager, ProfileStore, Lesson, WordMasteryStore, etc.)
  -> feature.daily (TrainingStateAccess)
```

**Note:** `SettingsActionHandler` does NOT import `feature.progress` directly. It receives `resolveEliteUnlocked` as a lambda injected by TrainingViewModel.

---

## ViewModel (TrainingViewModel)

```
TrainingViewModel
  -> AppContainer (DI — lessonStore, progressStore, configStore, masteryStore, streakStore,
                   badSentenceStore, hiddenCardStore, vocabProgressStore, profileStore,
                   drillProgressStore, backupManager, ttsEngine, wordMasteryStore(packId),
                   verbDrillStore(packId))
  -> feature.boss   (BossBattleRunner, BossCommand, BossOrchestrator)
  -> feature.daily   (DailyPracticeCoordinator, TrainingStateAccess)
  -> feature.progress (BadSentenceHelper, BadSentenceResult, FlowerRefresher,
                       ProgressResult, ProgressRestorer, ProgressTracker, StreakManager)
  -> feature.training (AnswerValidator, CardProvider, SessionEvent, SessionRunner,
                       StoryResult, StoryRunner, WordBankGenerator)
  -> feature.vocab    (VocabResult, VocabSoundResult, VocabSprintRunner)
  -> shared           (SettingsActionHandler, SettingsResult)
  -> shared.audio     (AudioCoordinator)
  -> data             (TrainingUiState, Lesson, LessonSchedule, InputMode, SessionState,
                       SentenceCard, BossReward, StoryPhase, TrainingConfig, TrainingMode,
                       VerbDrillCard, VocabEntry, LessonMasteryState, StreakData,
                       DailyBlockType, DailyTask, BackupManager, SubmitResult, etc.)
```

TrainingViewModel owns the `stateAccess` object (anonymous implementation of `TrainingStateAccess`) and passes it to ALL feature modules. It combines their individual StateFlows into the final `uiState`:

```kotlin
combine(_coreState, audioCoordinator.audioState, storyRunner.stateFlow,
        vocabSprintRunner.vocabState, dailyPracticeCoordinator.dailyState)
```

---

## Inter-Module Dependency Graph

```
feature.daily (TrainingStateAccess interface)
    ^   ^   ^   ^   ^   ^   ^
    |   |   |   |   |   |   |
    |   |   |   |   |   |   +-- shared/audio/AudioCoordinator
    |   |   |   |   |   +------- shared/SettingsActionHandler
    |   |   |   |   +----------- feature/vocab/VocabSprintRunner
    |   |   |   +--------------- feature/progress/* (ProgressTracker, FlowerRefresher,
    |   |   |                                     BadSentenceHelper, ProgressRestorer)
    |   |   +-------------------- feature/training/* (SessionRunner, CardProvider,
    |   |                                          StoryRunner)
    |   +------------------------ feature/boss/BossOrchestrator
    +---------------------------- feature/daily/* (DailyPracticeCoordinator,
                                          DailySessionComposer, DailySessionHelper)

feature.training -> feature.progress (CardProvider -> ProgressTracker)
feature.training -> feature.daily (SessionRunner, StoryRunner -> TrainingStateAccess)

feature.boss -> feature.training (BossOrchestrator -> CardProvider, SessionRunner)

feature.daily -> feature.training (DailyPracticeCoordinator -> AnswerValidator;
                     DailyPracticeSessionProvider -> WordBankGenerator, CardSessionStateMachine)

feature.vocab -> feature.training (VocabSprintRunner -> AnswerValidator)

feature.progress -> (no feature cross-deps, only data + TrainingStateAccess)
shared/audio   -> (no feature cross-deps, only data + TrainingStateAccess)
shared         -> (no feature cross-deps, only data + TrainingStateAccess)
```

---

## Edit Blast Radius

| Change | Must verify |
|--------|-------------|
| `data/Models.kt` or `TrainingUiState` | ALL feature modules, TrainingViewModel, GrammarMateApp |
| `feature/daily/TrainingStateAccess` | ALL feature modules, shared/audio, shared/SettingsActionHandler (interface change cascades everywhere) |
| `feature/training/SessionRunner` | TrainingViewModel, BossOrchestrator (holds reference), TrainingScreen |
| `feature/training/CardProvider` | TrainingViewModel, BossOrchestrator (holds reference), SessionRunner |
| `feature/training/AnswerValidator` | TrainingViewModel, SessionRunner, VocabSprintRunner, DailyPracticeCoordinator |
| `feature/training/WordBankGenerator` | SessionRunner, DailyPracticeSessionProvider |
| `feature/training/CardSessionStateMachine` | DailyPracticeSessionProvider |
| `feature/training/SessionEvent` | TrainingViewModel (processes events), SessionRunner (emits events) |
| `feature/boss/BossOrchestrator` | TrainingViewModel (constructs + dispatches BossCommands), GrammarMateApp (boss UI state) |
| `feature/boss/BossBattleRunner` | BossOrchestrator, TrainingViewModel |
| `feature/boss/BossCommand` | TrainingViewModel (processes commands), BossOrchestrator (emits commands) |
| `feature/daily/DailyPracticeCoordinator` | TrainingViewModel, DailyPracticeScreen |
| `feature/daily/DailySessionComposer` | DailyPracticeCoordinator, DailyPracticeSessionProvider (references CARDS_PER_BLOCK) |
| `feature/daily/DailyPracticeSessionProvider` | DailyPracticeScreen (CardSessionContract adapter) |
| `feature/progress/ProgressTracker` | TrainingViewModel, CardProvider (holds reference), SessionRunner (via injected lambdas) |
| `feature/progress/FlowerRefresher` | TrainingViewModel, HomeScreen (flower display) |
| `feature/progress/ProgressRestorer` | TrainingViewModel (init/restore path) |
| `feature/progress/BadSentenceHelper` | TrainingViewModel |
| `feature/progress/StreakManager` | TrainingViewModel, SessionRunner (holds reference) |
| `feature/vocab/VocabSprintRunner` | TrainingViewModel, GrammarMateApp (vocab sprint UI state) |
| `shared/audio/AudioCoordinator` | TrainingViewModel (owns lifecycle), GrammarMateApp (TTS/ASR UI wiring) |
| `shared/SettingsActionHandler` | TrainingViewModel |
| `AppContainer` | TrainingViewModel (all store construction) |

### Highest-risk changes (widest blast radius)

1. **`TrainingStateAccess` interface** — used by 8+ classes across all modules. Any signature change touches everything.
2. **`data/TrainingUiState`** — the god-state data class. Field changes ripple to every feature module that reads state.
3. **`feature/training/AnswerValidator`** — shared by SessionRunner, VocabSprintRunner, DailyPracticeCoordinator.
4. **`feature/training/SessionEvent` sealed class** — adding/removing variants requires changes in SessionRunner (emitter) and TrainingViewModel (consumer).
5. **`AppContainer`** — all store construction flows through here. New stores or changed constructors cascade to TrainingViewModel.

---

## Store Instance Summary

| Store | Constructed by | Used by (feature modules) |
|-------|---------------|--------------------------|
| `LessonStore` | AppContainer | ProgressTracker, ProgressRestorer, DailyPracticeCoordinator, DailySessionComposer, StoryRunner, VocabSprintRunner, SettingsActionHandler |
| `MasteryStore` | AppContainer | ProgressTracker, FlowerRefresher, CardProvider (via ProgressTracker), DailyPracticeCoordinator, BossOrchestrator |
| `ProgressStore` | AppContainer | ProgressTracker, ProgressRestorer, BossOrchestrator |
| `StreakStore` | AppContainer | StreakManager, ProgressRestorer |
| `DrillProgressStore` | AppContainer | SessionRunner |
| `VerbDrillStore` | AppContainer (pack-scoped) | DailyPracticeCoordinator, DailySessionComposer |
| `WordMasteryStore` | AppContainer (pack-scoped) | DailyPracticeCoordinator, DailySessionComposer, SettingsActionHandler |
| `VocabProgressStore` | AppContainer | VocabSprintRunner |
| `BadSentenceStore` | AppContainer | BadSentenceHelper |
| `HiddenCardStore` | AppContainer | BadSentenceHelper |
| `AppConfigStore` | AppContainer | AudioCoordinator, SettingsActionHandler |
| `ProfileStore` | AppContainer | ProgressRestorer, SettingsActionHandler |
| `BackupManager` | AppContainer | ProgressRestorer, SettingsActionHandler |
| `TtsEngine` | AppContainer | AudioCoordinator |

All stores are singletons via `StoreFactory` caching (enforced by `AppContainer`). The pack-scoped stores (`VerbDrillStore`, `WordMasteryStore`) use factory methods and are evicted on pack change.
