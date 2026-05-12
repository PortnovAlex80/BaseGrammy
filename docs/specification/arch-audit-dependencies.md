# Architecture Audit -- Dependency Map & State Duplication

**Date:** 2026-05-12
**Branch:** feature/daily-cursors
**Auditor:** Claude Code (automated analysis)

---

## 1. Component Inventory

| Component | File | Lines | Layer | Responsibilities |
|-----------|------|-------|-------|-----------------|
| `TrainingViewModel` | `ui/TrainingViewModel.kt` | 3615 | ViewModel | All training session logic, daily practice orchestration, boss mode, drill mode, vocab sprint, TTS/ASR, flower states, mastery tracking, streak, backup, word bank, story, bad sentences, elite mode |
| `TrainingUiState` | `ui/TrainingViewModel.kt` (bottom) | ~115 | State | God-state data class with 60+ fields covering every feature |
| `VerbDrillViewModel` | `ui/VerbDrillViewModel.kt` | 575 | ViewModel | Standalone verb drill: card loading, session management, progress persistence, TTS, bad sentences, speed tracking |
| `VocabDrillViewModel` | `ui/VocabDrillViewModel.kt` | 456 | ViewModel | Standalone vocab drill: word loading, SRS session, rating, TTS, voice input |
| `VerbDrillCardSessionProvider` | `ui/VerbDrillCardSessionProvider.kt` | 389 | Adapter | `CardSessionContract` adapter wrapping `VerbDrillViewModel`; manages retry/hint flow, word bank, voice triggers in Compose state |
| `DailyPracticeSessionProvider` | `ui/helpers/DailyPracticeSessionProvider.kt` | 381 | Adapter | `CardSessionContract` adapter for daily practice blocks 1 & 3; self-contained retry/hint/word-bank logic mirroring `VerbDrillCardSessionProvider` |
| `DailySessionHelper` | `ui/helpers/DailySessionHelper.kt` | 238 | Helper | Navigates `DailySessionState` (task/block index); start/end/fast-forward/replace blocks |
| `DailySessionComposer` | `ui/helpers/DailySessionComposer.kt` | 437 | Builder | Builds `List<DailyTask>` for a daily session: sentence block (cursor), vocab block (SRS), verb block (weak-first) |
| `CardSessionContract` | `data/CardSessionContract.kt` | 98 | Interface | Contract for card session providers: currentCard, progress, submitAnswer, word bank, TTS, flagging |
| `Models.kt` | `data/Models.kt` | 236 | Data | Core domain models: Lesson, SentenceCard, DailyTask, DailySessionState, DailyCursorState, TrainingProgress, LessonMasteryState |
| `MasteryStore` | `data/MasteryStore.kt` | 232 | Store | YAML-backed per-lesson card show tracking (uniqueCardShows, shownCardIds); mastery/flower calculation input |
| `ProgressStore` | `data/ProgressStore.kt` | 121 | Store | YAML-backed training session progress (TrainingProgress); language/lesson/mode/boss rewards/daily cursor |
| `VerbDrillStore` | `data/VerbDrillStore.kt` | 114 | Store | YAML-backed per-combo verb drill progress (everShownCardIds, todayShownCardIds); pack-scoped |
| `WordMasteryStore` | `data/WordMasteryStore.kt` | 145 | Store | YAML-backed per-word SRS mastery (intervalStepIndex, isLearned); pack-scoped |
| `VocabProgressStore` | `data/VocabProgressStore.kt` | 283 | Store | YAML-backed vocab sprint progress (completedIndices, SRS entry states); NOT pack-scoped |
| `DrillProgressStore` | `data/DrillProgressStore.kt` | 43 | Store | YAML-backed drill card index (simple key-value); NOT pack-scoped |

---

## 2. Dependency Graph

### 2.1 Per-Component Dependencies

#### TrainingViewModel
- **Depends on (stores):** LessonStore, ProgressStore, AppConfigStore, MasteryStore, StreakStore, BadSentenceStore, HiddenCardStore, DrillProgressStore, VocabProgressStore, WordMasteryStore, BackupManager, ProfileStore, TtsModelManager, TtsEngine, AsrModelManager, AsrEngine
- **Depends on (helpers):** DailySessionHelper, DailySessionComposer (created inline in methods)
- **Depends on (data classes):** 40+ data classes from Models.kt, VerbDrillCard, VerbDrillComboProgress, WordMasteryState, etc.
- **Who depends on it:** GrammarMateApp.kt (collects uiState, calls action methods), DailyPracticeScreen.kt
- **How it receives dependencies:** Constructor injection (creates all stores directly via `application`)

#### VerbDrillViewModel
- **Depends on (stores):** VerbDrillStore (recreated on pack change), LessonStore, ProgressStore, BadSentenceStore, TtsEngine
- **Depends on (data classes):** VerbDrillCard, VerbDrillComboProgress, VerbDrillSessionState, VerbDrillUiState
- **Who depends on it:** VerbDrillCardSessionProvider (wraps it)
- **How it receives dependencies:** Constructor injection (creates stores directly)

#### VocabDrillViewModel
- **Depends on (stores):** LessonStore, WordMasteryStore (recreated on pack change), TtsEngine
- **Depends on (data classes):** VocabWord, WordMasteryState, VocabDrillUiState, VocabDrillSessionState
- **Who depends on it:** VocabDrillScreen composable directly
- **How it receives dependencies:** Constructor injection

#### VerbDrillCardSessionProvider
- **Depends on:** VerbDrillViewModel (direct reference)
- **Who depends on it:** VerbDrillScreen composable
- **How it receives dependencies:** Constructor injection of `VerbDrillViewModel`

#### DailyPracticeSessionProvider
- **Depends on:** None (self-contained; receives lambdas for all external actions)
- **Who depends on it:** DailyPracticeScreen composable
- **How it receives dependencies:** Constructor injection of `List<DailyTask>` + callback lambdas

#### DailySessionHelper
- **Depends on:** TrainingStateAccess interface (provided by TrainingViewModel)
- **Who depends on it:** TrainingViewModel (owns the instance)
- **How it receives dependencies:** Constructor injection of TrainingStateAccess

#### DailySessionComposer
- **Depends on:** LessonStore, VerbDrillStore, WordMasteryStore (all constructor-injected)
- **Who depends on it:** TrainingViewModel (creates instances in methods)
- **How it receives dependencies:** Constructor injection

### 2.2 ASCII Dependency Graph

```
                          GrammarMateApp.kt
                          (screen router, UI)
                                 |
                    +------------+------------+
                    |            |            |
                    v            v            v
           TrainingVM    VerbDrillVM   VocabDrillVM
           (3615 lines)  (575 lines)   (456 lines)
                |              |              |
       +--------+--------+    |              |
       |        |        |    v              |
       v        v        v   VerbDrill       |
  DailySession DailyPractice CardSession     |
  Helper       Session      Provider        |
       |       Provider     (389 lines)     |
       |       (381 lines)                   |
       |                                    |
       v                                    v
  DailySession                      VocabDrillScreen
  Composer                          (direct VM calls)
  (437 lines)


  === STORE LAYER ===

  TrainingVM owns:
  +--LessonStore       (lesson content, packs)
  +--ProgressStore     (session position, cursor)
  +--AppConfigStore    (flags)
  +--MasteryStore      (card shows, flower data)
  +--StreakStore       (daily streak)
  +--BadSentenceStore  (flagged cards)
  +--HiddenCardStore   (hidden cards)
  +--DrillProgressStore(drill position)
  +--VocabProgressStore(vocab sprint SRS)
  +--WordMasteryStore  (vocab SRS mastery)  <-- ALSO in VocabDrillVM, DailySessionComposer
  +--BackupManager     (backup/restore)
  +--ProfileStore      (user name)
  +--TtsModelManager   (model downloads)
  +--TtsEngine         (speech synthesis)
  +--AsrModelManager   (ASR downloads)
  +--AsrEngine         (speech recognition)

  VerbDrillVM owns:
  +--VerbDrillStore    (verb progress)       <-- ALSO in TrainingVM, DailySessionComposer
  +--LessonStore       (verb drill files)
  +--ProgressStore     (language ID)
  +--BadSentenceStore  (flagged verbs)
  +--TtsEngine         (speech synthesis)

  VocabDrillVM owns:
  +--LessonStore       (vocab drill files)
  +--WordMasteryStore  (word mastery)        <-- ALSO in TrainingVM, DailySessionComposer
  +--TtsEngine         (speech synthesis)

  DailySessionComposer receives:
  +--LessonStore       (from TrainingVM)
  +--VerbDrillStore    (created fresh)       <-- duplicate instance
  +--WordMasteryStore  (created fresh)       <-- duplicate instance
```

### 2.3 Cross-Cutting Dependencies

| Store | Used By | Instances |
|-------|---------|-----------|
| `VerbDrillStore` | TrainingVM (3 methods), VerbDrillVM, DailySessionComposer | 3+ independent instances per session |
| `WordMasteryStore` | TrainingVM (init + rateVocabCard), VocabDrillVM, DailySessionComposer | 3+ independent instances per session |
| `LessonStore` | TrainingVM, VerbDrillVM, VocabDrillVM, DailySessionComposer | 4 instances (shared singleton not enforced) |
| `TtsEngine` | TrainingVM, VerbDrillVM, VocabDrillVM | 3 separate engine instances |
| `BadSentenceStore` | TrainingVM, VerbDrillVM | 2 instances |
| `ProgressStore` | TrainingVM, VerbDrillVM | 2 instances |

---

## 3. State Ownership Map

### 3.1 Mutable State by Location

#### TrainingViewModel (`_uiState: MutableStateFlow<TrainingUiState>`)
The single largest state holder. `TrainingUiState` has ~60 fields:

| State | Written By | Read By | Single Owner? |
|-------|-----------|---------|---------------|
| `sessionState` | TrainingVM (start/pause/finish) | GrammarMateApp, screens | YES |
| `currentCard` | TrainingVM (nextCard/buildSession) | GrammarMateApp | YES |
| `inputText` | TrainingVM, DailyPracticeScreen | GrammarMateApp | SHARED (daily provider also tracks) |
| `inputMode` | TrainingVM | GrammarMateApp, screens | YES |
| `incorrectAttemptsForCard` | TrainingVM | GrammarMateApp | SHARED (daily provider also tracks) |
| `dailySession` | DailySessionHelper | DailyPracticeScreen, GrammarMateApp | YES (via helper) |
| `dailyCursor` | TrainingVM (advanceCursor, storeFirstSessionCardIds) | TrainingVM | YES |
| `bossActive/bossType/bossTotal/bossProgress` | TrainingVM | GrammarMateApp | YES |
| `eliteActive/eliteStepIndex` | TrainingVM | GrammarMateApp | YES |
| `isDrillMode/drillCardIndex` | TrainingVM | GrammarMateApp | YES |
| `vocabInputText/vocabAttempts/vocabAnswerText` | TrainingVM | GrammarMateApp | YES |
| `lessonFlowers` | TrainingVM (refreshFlowerStates) | HomeScreen | YES |
| `wordBankWords/selectedWords` | TrainingVM | GrammarMateApp | YES |
| `ttsState` | TrainingVM (TTS collect) | GrammarMateApp | YES |
| `badSentenceCount` | TrainingVM | GrammarMateApp | YES |

#### VerbDrillViewModel (`_uiState: MutableStateFlow<VerbDrillUiState>`)

| State | Written By | Read By | Single Owner? |
|-------|-----------|---------|---------------|
| `session` (VerbDrillSessionState) | VerbDrillVM | VerbDrillCardSessionProvider, VerbDrillScreen | YES |
| `loadedLanguageId` | VerbDrillVM | VerbDrillCardSessionProvider | YES |
| `isLoading` | VerbDrillVM | VerbDrillScreen | YES |
| `currentCardIsBad` | VerbDrillVM | VerbDrillScreen | YES |
| `allDoneToday` | VerbDrillVM | VerbDrillScreen | YES |

#### VocabDrillViewModel (`_uiState: MutableStateFlow<VocabDrillUiState>`)

| State | Written By | Read By | Single Owner? |
|-------|-----------|---------|---------------|
| `session` (VocabDrillSessionState) | VocabDrillVM | VocabDrillScreen | YES |
| `loadedLanguageId` | VocabDrillVM | VocabDrillScreen | YES |
| `selectedPos/rankMin/rankMax` | VocabDrillVM | VocabDrillScreen | YES |
| `masteredCount/masteredByPos` | VocabDrillVM | VocabDrillScreen | YES |

#### VerbDrillCardSessionProvider (Compose mutableStateOf fields)

| State | Written By | Read By | Single Owner? |
|-------|-----------|---------|---------------|
| `pendingCard` | Provider (submit) | VerbDrillScreen | YES (adapter-local) |
| `pendingAnswerResult` | Provider (submit) | VerbDrillScreen | YES |
| `hintAnswer` | Provider (wrong/showAnswer) | VerbDrillScreen | YES |
| `showIncorrectFeedback` | Provider | VerbDrillScreen | YES |
| `remainingAttempts` | Provider | VerbDrillScreen | YES |
| `incorrectAttempts` | Provider | VerbDrillScreen | YES |
| `voiceTriggerToken` | Provider | VerbDrillScreen | YES |
| `_inputMode` | Provider | VerbDrillScreen | YES |
| `_selectedWords` | Provider | VerbDrillScreen | YES |

#### DailyPracticeSessionProvider (Compose mutableStateOf fields)

| State | Written By | Read By | Single Owner? |
|-------|-----------|---------|---------------|
| `currentIndex` | Provider (next/prev) | DailyPracticeScreen | YES (adapter-local) |
| `pendingCard` | Provider (submit) | DailyPracticeScreen | YES |
| `pendingAnswerResult` | Provider (submit) | DailyPracticeScreen | YES |
| `hintAnswer` | Provider (wrong/showAnswer) | DailyPracticeScreen | YES |
| `showIncorrectFeedback` | Provider | DailyPracticeScreen | YES |
| `remainingAttempts` | Provider | DailyPracticeScreen | YES |
| `incorrectAttempts` | Provider | DailyPracticeScreen | YES |
| `voiceTriggerToken` | Provider | DailyPracticeScreen | YES |
| `_inputMode` | Provider | DailyPracticeScreen | YES |
| `_selectedWords` | Provider | DailyPracticeScreen | YES |

#### Private vars in TrainingViewModel

| State | Written By | Read By | Single Owner? |
|-------|-----------|---------|---------------|
| `sessionCards` | TrainingVM (buildSessionCards) | TrainingVM | YES |
| `bossCards` | TrainingVM (startBoss) | TrainingVM | YES |
| `eliteCards` | TrainingVM (openEliteStep) | TrainingVM | YES |
| `vocabSession` | TrainingVM (openVocabSprint) | TrainingVM | YES |
| `prebuiltDailySession` | TrainingVM init, startDailyPractice | TrainingVM | YES |
| `lastDailyTasks` | TrainingVM (startDailyPractice, repeatDailyPractice) | TrainingVM | YES |
| `dailyPracticeAnsweredCounts` | TrainingVM (recordDailyCardPracticed, cancelDailySession) | TrainingVM | YES |
| `dailyCursorAtSessionStart` | TrainingVM (startDailyPractice, cancelDailySession) | TrainingVM | YES |
| `lessonSchedules` | TrainingVM (rebuildSchedules) | TrainingVM | YES |
| `allCards` (VerbDrillVM) | VerbDrillVM.loadCards | VerbDrillVM | YES |
| `progressMap` (VerbDrillVM) | VerbDrillVM.loadCards + persistCardProgress | VerbDrillVM | YES |
| `allWords` (VocabDrillVM) | VocabDrillVM.loadWords | VocabDrillVM | YES |
| `masteryMap` (VocabDrillVM) | VocabDrillVM.loadWords + answerRating | VocabDrillVM | YES |

### 3.2 Store-Persisted State

| Store | File | Data | Who Writes | Who Reads |
|-------|------|------|-----------|-----------|
| MasteryStore | `mastery.yaml` | `LessonMasteryState` per lesson | TrainingVM | TrainingVM (flower calc) |
| ProgressStore | `progress.yaml` | `TrainingProgress` (full session state) | TrainingVM | TrainingVM (init, restore) |
| VerbDrillStore | `drills/{packId}/verb_drill_progress.yaml` | `Map<String, VerbDrillComboProgress>` | VerbDrillVM, TrainingVM (persistDailyVerbProgress), DailySessionComposer (reads) | VerbDrillVM, DailySessionComposer |
| WordMasteryStore | `drills/{packId}/word_mastery.yaml` | `Map<String, WordMasteryState>` | VocabDrillVM, TrainingVM (rateVocabCard), DailySessionComposer (reads) | VocabDrillVM, DailySessionComposer |
| VocabProgressStore | `vocab_progress.yaml` | `LessonVocabProgress` per lesson | TrainingVM | TrainingVM |
| DrillProgressStore | `drill_progress_{lessonId}.yaml` | Card index per lesson | TrainingVM | TrainingVM |

---

## 4. State Duplication Analysis

### 4.1 CRITICAL: VerbDrillStore -- 3 Independent Instances

**Locations:**
1. `VerbDrillViewModel.verbDrillStore` (line 44) -- standalone drill
2. `TrainingViewModel.persistDailyVerbProgress()` (line 1629) -- creates fresh `VerbDrillStore(getApplication(), packId = packId)` per call
3. `DailySessionComposer` constructor -- receives `VerbDrillStore` from TrainingVM init/methods

**Risk:** `TrainingVM.persistDailyVerbProgress()` creates a NEW `VerbDrillStore` instance and reads/writes to the same file. If `VerbDrillViewModel` has its own in-memory `progressMap` (line 54), and TrainingVM writes to the file, the ViewModel's in-memory map becomes stale. The file-level YAML is the source of truth, but the ViewModel caches it in `progressMap` and does not reload.

**Sync gap:** VerbDrillVM caches `progressMap` in memory (loaded at `loadCards()`, updated at `persistCardProgress()`). TrainingVM writes directly to the file via a new VerbDrillStore instance. If the user does daily practice (which persists verb progress via TrainingVM) and then navigates to VerbDrill standalone screen, the ViewModel's cached `progressMap` is stale until `reloadForLanguage()` or `reloadForPack()` is called.

### 4.2 CRITICAL: WordMasteryStore -- 3 Independent Instances

**Locations:**
1. `VocabDrillViewModel.masteryStore` (line 29, recreated on pack change)
2. `TrainingViewModel.wordMasteryStore` (line 103, global, no packId)
3. `DailySessionComposer.wordMasteryStore` (created fresh in TrainingVM methods)
4. `TrainingViewModel.rateVocabCard()` (line 1698, creates fresh `WordMasteryStore(getApplication(), packId = packId)` per call)

**Risk:** The `TrainingViewModel.wordMasteryStore` (line 103) is created WITHOUT a packId (global mode), while the `DailySessionComposer` and `rateVocabCard()` create pack-scoped instances. The global instance reads/writes to `grammarmate/word_mastery.yaml` while pack-scoped instances read/write to `grammarmate/drills/{packId}/word_mastery.yaml`. These are DIFFERENT files. This means:
- `TrainingVM.init` reads `vocabMasteredCount` from the GLOBAL store (line 256)
- `VocabDrillVM` writes to the PACK-SCOPED store
- The count displayed on HomeScreen may never update correctly

**Sync gap:** `refreshVocabMasteryCount()` (line 3157) reads from the global `wordMasteryStore` (no packId), but actual mastery is written to pack-scoped stores by VocabDrillVM and DailySessionComposer.

### 4.3 CRITICAL: TtsEngine -- 3 Separate Instances

**Locations:**
1. `TrainingViewModel.ttsEngine` (line 107)
2. `VerbDrillViewModel.ttsEngine` (line 48)
3. `VocabDrillViewModel.ttsEngine` (line 30)

**Risk:** Each ViewModel creates its own TTS engine. TTS engines typically hold native resources (Sherpa-ONNX). Three simultaneous engines may:
- Compete for audio focus
- Waste memory loading the same model 3 times
- Cause conflicts if multiple ViewModels try to speak simultaneously

### 4.4 MODERATE: Incorrect Attempts / Hint Flow -- Duplicated in 3 Places

The retry/hint/word-bank flow is independently implemented in:
1. `TrainingViewModel.submitAnswer()` (lines 617-842) -- inline in the ViewModel for regular training
2. `VerbDrillCardSessionProvider.submitAnswerWithInput()` (lines 185-227) -- Compose state adapter
3. `DailyPracticeSessionProvider.submitAnswer()` (lines 159-206) -- self-contained Compose state

All three implement the same pattern:
- Correct -> set pending result
- Wrong -> increment `incorrectAttempts`
- 3 wrong -> auto-show hint
- Manual showAnswer -> show hint

**State tracked per implementation:**
- `incorrectAttempts`, `remainingAttempts`, `hintAnswer`, `showIncorrectFeedback`, `voiceTriggerToken` -- all duplicated across 3 locations with slightly different behaviors.

### 4.5 MODERATE: ProgressStore -- Read VerbDrillVM but Owned by TrainingVM

`VerbDrillViewModel` owns a `ProgressStore` (line 46) but only uses it to read `languageId` in `loadCards()` (line 124). TrainingVM is the primary writer. This is a read-only dependency, not true duplication, but it creates an unnecessary coupling.

### 4.6 MODERATE: Verb Drill Progress in Two Shapes

`VerbDrillComboProgress` tracks `todayShownCardIds` and `everShownCardIds`. This same data is used by:
1. `VerbDrillViewModel.startSession()` -- reads `todayShownCardIds` to filter out already-shown cards
2. `DailySessionComposer.buildVerbBlock()` -- reads `everShownCardIds` to exclude previously shown cards (different exclusion logic!)
3. `TrainingViewModel.persistDailyVerbProgress()` -- writes to the same store

The two consumers use DIFFERENT fields for filtering: VerbDrillVM uses `todayShownCardIds` while DailySessionComposer uses `everShownCardIds`. This means the same verb card could appear in a standalone drill session but be excluded from daily practice (or vice versa).

### 4.7 LOW: Vocab Progress in Two Stores

Vocab words have SRS state tracked in TWO different stores:
1. `WordMasteryStore` -- interval-step-based SRS (used by VocabDrillVM, DailySessionComposer, TrainingVM.rateVocabCard)
2. `VocabProgressStore` -- interval-step-based SRS with `completedIndices` (used by TrainingVM for vocab sprint)

These track DIFFERENT vocab features (drill vs. sprint) but use similar SRS logic. Not a sync bug but an unnecessary duplication of SRS logic.

### 4.8 LOW: `prebuiltDailySession` and `lastDailyTasks` Caches

TrainingVM caches daily tasks in two private vars:
- `prebuiltDailySession` (built in init background thread)
- `lastDailyTasks` (stored on session start for Repeat)

These are in-memory duplicates of data that could be reconstructed from the cursor state. They are not a sync risk (same VM, no external writers) but add complexity.

---

## 5. Responsibility Violations

### 5.1 God Object: TrainingViewModel (3615 lines)

TrainingViewModel holds responsibility for:
1. **Regular training** (lesson, mixed, sequential modes)
2. **Boss mode** (lesson, mega, elite bosses)
3. **Elite mode** (step-based speed challenge)
4. **Drill mode** (seamless card training)
5. **Vocab sprint** (Anki-like vocab practice)
6. **Daily practice orchestration** (session building, cursor management, block navigation)
7. **Flower state computation** (refreshFlowerStates)
8. **Streak tracking** (updateStreak)
9. **Bad sentence management** (flag/unflag/export)
10. **Hidden card management** (hide/unhide)
11. **TTS management** (speak, download, speed)
12. **ASR management** (offline recognition, download)
13. **Sound playback** (success/error tones)
14. **Backup/restore** (createProgressBackup, restoreBackup)
15. **User profile** (updateUserName)
16. **Word bank generation** (generateWordBank, updateWordBank, buildVocabWordBank)
17. **Mastery recording** (recordCardShowForMastery)
18. **Lesson/import management** (importLesson, importLessonPack, createEmptyLesson, etc.)

This is at least 18 distinct responsibilities in a single class, far exceeding the project's own 1200-line decomposition guideline (currently at 3615 lines, 3x the limit).

### 5.2 Missing Abstraction: Verb Drill Progress Persistence

Verb drill progress is persisted in three different places with slightly different logic:
- `VerbDrillViewModel.persistCardProgress()` -- uses `selectedGroup|selectedTense` combo key
- `TrainingViewModel.persistDailyVerbProgress()` -- uses `|{tense}` combo key (empty group)
- `DailySessionComposer.buildVerbBlock()` -- reads `everShownCardIds` for filtering

The combo key format differs between the two writers (`group|tense` vs `|tense`), meaning progress recorded during daily practice (empty group) lands in a different key than progress from the standalone VerbDrill screen (which has a selected group). This is technically correct (different scopes) but creates fragmented progress data.

### 5.3 DailyPracticeSessionProvider -- Self-Contained State Machine

The `DailyPracticeSessionProvider` is essentially a mini-ViewModel: it holds mutable Compose state (`currentIndex`, `pendingCard`, `hintAnswer`, `incorrectAttempts`, `voiceTriggerToken`, etc.) and implements the full retry/hint flow. This duplicates the retry logic from both `TrainingViewModel.submitAnswer()` and `VerbDrillCardSessionProvider`. The provider pattern is correct for the `CardSessionContract` interface, but the retry/hint state machine should be extracted to a shared component.

### 5.4 TrainingStateAccess Interface Limited to DailySessionHelper

The `TrainingStateAccess` interface (defined in DailySessionHelper.kt) is only used by `DailySessionHelper`. Other helpers (DailySessionComposer, DailyPracticeSessionProvider) do not use it:
- `DailySessionComposer` receives stores directly (not through TrainingStateAccess)
- `DailyPracticeSessionProvider` receives lambdas (not through TrainingStateAccess)

This means the helper dependency pattern described in CLAUDE.md is only partially implemented.

### 5.5 VocabDrillViewModel Incompatible with CardSessionContract

`VocabDrillViewModel` does NOT implement `CardSessionContract` and does NOT have a corresponding adapter/provider. Its flashcard UI is handled differently (Anki-style with flip/rating buttons) rather than the text-input/word-bank pattern. This is correct behavior but creates a gap in the adapter pattern -- the daily practice vocab block (Block 2) switches to a completely different composable rather than using CardSessionContract.

---

## 6. Interface Boundaries

### 6.1 Clean Interfaces

| Interface | Purpose | Clean? |
|-----------|---------|--------|
| `CardSessionContract` | Unified card session API for UI | YES -- well-defined contract with capabilities |
| `SessionCard` | Common card interface for SentenceCard/VerbDrillCard | YES -- minimal, focused |
| `TrainingStateAccess` | ViewModel state access for helpers | PARTIAL -- only used by DailySessionHelper |
| `CardSessionCapabilities` | Feature flags for card sessions | YES -- default implementations, clean mixin |

### 6.2 Tight Coupling (No Interface)

| Coupling | Impact |
|----------|--------|
| TrainingVM -> 16+ stores (direct construction) | Cannot test without full Android context; cannot mock stores |
| VerbDrillVM -> VerbDrillStore, LessonStore (direct construction) | Same as above |
| VocabDrillVM -> LessonStore, WordMasteryStore (direct construction) | Same as above |
| VerbDrillCardSessionProvider -> VerbDrillViewModel (direct reference) | Provider cannot be tested without the ViewModel |
| DailySessionComposer -> LessonStore, VerbDrillStore, WordMasteryStore (constructor) | Relatively clean but stores are concrete classes, not interfaces |
| TrainingVM -> DailySessionComposer (inline construction) | Composer is recreated multiple times per session (each method call creates a new instance with new stores) |

### 6.3 What Should Be Behind an Interface But Isn't

1. **Store layer:** All stores are concrete classes. None implement interfaces. This makes unit testing ViewModels impossible without full Android context.

2. **TtsEngine:** Shared by 3 ViewModels but not wrapped in an interface or shared via dependency injection.

3. **VerbDrillStore factory:** TrainingVM creates new instances on every method call (`VerbDrillStore(getApplication(), packId = packId)`). Should be a factory/singleton.

4. **WordMasteryStore factory:** Same issue -- created fresh in multiple places with different pack scoping.

5. **Sound playback:** `SoundPool` is managed directly in TrainingVM. Should be behind an interface for testability and to share with other ViewModels.

---

## 7. Recommendations

Priority-ordered by risk reduction and architectural improvement:

### P0 -- Fix State Synchronization Bugs

**R1: Fix WordMasteryStore pack scoping in TrainingVM**
`TrainingVM.wordMasteryStore` is created without packId (line 103), reading from the global file. But `VocabDrillVM` writes to pack-scoped files. The `vocabMasteredCount` shown on HomeScreen will be wrong. Fix: create the store with the active packId, or update `refreshVocabMasteryCount()` to use a pack-scoped instance.

**R2: Prevent VerbDrillVM stale progress after daily practice**
After daily practice persists verb progress via TrainingVM, the VerbDrillViewModel's cached `progressMap` is stale. Fix: call `verbDrillStore.loadProgress()` at the start of `startSession()` or when `reloadForLanguage()`/`reloadForPack()` is invoked, or add a `refreshProgress()` method.

### P1 -- Reduce Duplication

**R3: Extract retry/hint state machine**
The incorrect-attempts/hint/word-bank flow is duplicated 3 times (TrainingVM, VerbDrillCardSessionProvider, DailyPracticeSessionProvider). Extract to a shared `CardRetryStateMachine` class that both providers and the ViewModel can use. Estimated savings: ~200 lines per adapter.

**R4: Share TtsEngine across ViewModels**
Create a singleton `TtsEngine` managed at the Application level, or pass it via dependency. Currently 3 engines are created, each holding native resources.

**R5: Share store instances via Application-level singletons or DI**
`LessonStore`, `VerbDrillStore`, `WordMasteryStore` are instantiated 2-4 times independently. Use a simple ServiceLocator or Hilt to ensure single instances.

### P2 -- Decompose TrainingViewModel

**R6: Extract DailyPracticeHelper from TrainingVM**
Move daily practice orchestration (lines 1439-1776, ~340 lines) into a dedicated `DailyPracticeOrchestrator` helper class that receives `TrainingStateAccess`. This would include `startDailyPractice`, `repeatDailyPractice`, `advanceDailyTask`, `cancelDailySession`, `persistDailyVerbProgress`, `rateVocabCard`, and `repeatDailyBlock`.

**R7: Extract BossHelper from TrainingVM**
Move boss mode logic (lines 2181-2400, ~220 lines) into an existing or new helper in `ui/helpers/`.

**R8: Extract DrillHelper from TrainingVM**
Move drill mode logic (lines 1778-1941, ~160 lines) into a helper.

**R9: Extract VocabSprintHelper from TrainingVM**
Move vocab sprint logic (lines 1955-2179, ~220 lines) into a helper.

**R10: Extract AudioHelper from TrainingVM**
Move TTS/ASR management (lines 2523-2856, ~330 lines) and sound playback into a helper.

Combined, these extractions would reduce TrainingViewModel from 3615 lines to approximately 2000 lines, still above the 1200-line guideline but significantly more manageable.

### P3 -- Improve Abstractions

**R11: Define store interfaces**
Create interfaces for `MasteryStore`, `VerbDrillStore`, `WordMasteryStore`, etc. to enable unit testing of helpers without Android context.

**R12: Create VerbDrillProgressWriter interface**
Abstract the verb drill progress writing so that TrainingVM and VerbDrillVM write through the same interface, eliminating the stale-cache issue.

**R13: Unify DailySessionComposer construction**
TrainingVM creates new `DailySessionComposer` instances (and new stores for them) in at least 4 methods. Store the composer as a class member and update it when packId changes.

### P4 -- Minor Cleanups

**R14: Remove VocabProgressStore duplication with WordMasteryStore**
These two stores track similar SRS state for different vocab contexts. Consider unifying them or at least sharing the interval calculation logic.

**R15: Move TrainingUiState to its own file**
The 115-line data class is buried at the bottom of TrainingViewModel.kt. It should be in a separate file for readability.

**R16: Extract resetState() methods**
The pattern of resetting 20+ fields in a `.copy()` call appears at least 8 times in TrainingVM (selectLanguage, selectLesson, selectMode, refreshLessons, etc.). Extract named reset functions like `resetTrainingFields()` and `resetVocabFields()`.

---

## Appendix A: File Size Summary

| File | Lines | Exceeds Guideline? |
|------|-------|--------------------|
| TrainingViewModel.kt | 3615 | YES (3x the 1200 limit) |
| DailySessionComposer.kt | 437 | No |
| VerbDrillCardSessionProvider.kt | 389 | No |
| DailyPracticeSessionProvider.kt | 381 | No |
| VerbDrillViewModel.kt | 575 | No |
| VocabDrillViewModel.kt | 456 | No |
| DailySessionHelper.kt | 238 | No |
| VocabProgressStore.kt | 283 | No |
| MasteryStore.kt | 232 | No |
| Models.kt | 236 | No |
| WordMasteryStore.kt | 145 | No |
| VerbDrillStore.kt | 114 | No |
| ProgressStore.kt | 121 | No |
| CardSessionContract.kt | 98 | No |
| DrillProgressStore.kt | 43 | No |

## Appendix B: Data Flow for Daily Practice Verb Block

```
User answers verb card in DailyPracticeScreen
    |
    v
DailyPracticeSessionProvider.nextCard()
    |  calls onCardAdvanced(blockCards[currentIndex])
    v
TrainingVM.recordDailyCardPracticed(DailyBlockType.VERBS)
    |  increments dailyPracticeAnsweredCounts[VERBS]
    v
DailyPracticeScreen also calls TrainingVM.advanceDailyTask()
    |  calls persistDailyVerbProgress(task.card)
    |     creates NEW VerbDrillStore(packId)
    |     writes to drills/{packId}/verb_drill_progress.yaml
    v
DailySessionHelper.nextTask()
    |  advances taskIndex in DailySessionState
    v
TrainingVM.saveProgress()
    |  writes progress.yaml (including dailyCursor, dailyLevel, dailyTaskIndex)
```

Note: The VerbDrillViewModel is NOT involved in daily practice verb flow at all. If the user later opens standalone VerbDrill, its cached `progressMap` may be stale.
