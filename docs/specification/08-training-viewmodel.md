# 8. TrainingViewModel -- Migration Playbook & Complete Inventory

> **Purpose:** This document is the authoritative migration playbook for decomposing the 3625-line TrainingViewModel into 11 modules as defined in `arch-module-decomposition.md`. Every method, field, private var, and store access is inventoried here. If anything is missed, the migration will introduce bugs. **Completeness is everything.**

---

## 1. Overview

### 1.1 File Location & Size

| Property | Value |
|----------|-------|
| File path | `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` |
| Total lines | 3756 (including TrainingUiState, SubmitResult, LessonLadderRow data classes) |
| Class lines | 3624 (class body: line 80 through line 3624) |
| Data class lines | 132 (TrainingUiState lines 3631-3746, SubmitResult lines 3626-3629, LessonLadderRow lines 3748-3755) |

### 1.2 Class Signature

```kotlin
class TrainingViewModel(application: Application) : AndroidViewModel(application)
```

### 1.3 Constructor Dependencies (Stores, Engines, Managers)

All initialized as private vals/vars at the top of the class body (lines 81-136):

| Instance | Line | Type | Category | Target Module |
|----------|------|------|----------|---------------|
| `soundPool` | 81 | `SoundPool` | Audio | AudioCoordinator |
| `successSoundId` | 91 | `Int` | Audio | AudioCoordinator |
| `errorSoundId` | 92 | `Int` | Audio | AudioCoordinator |
| `loadedSounds` | 93 | `MutableSet<Int>` | Audio | AudioCoordinator |
| `lessonStore` | 94 | `LessonStore` | Data | ViewModel (stays) / CardProvider / DailyCoordinator |
| `progressStore` | 95 | `ProgressStore` | Data | ProgressTracker |
| `configStore` | 96 | `AppConfigStore` | Data | ViewModel (stays) |
| `masteryStore` | 97 | `MasteryStore` | Data | ProgressTracker |
| `streakStore` | 98 | `StreakStore` | Data | StreakManager |
| `badSentenceStore` | 99 | `BadSentenceStore` | Data | ViewModel (stays) |
| `hiddenCardStore` | 100 | `HiddenCardStore` | Data | CardProvider |
| `drillProgressStore` | 101 | `DrillProgressStore` | Data | SessionRunner (drill sub-mode) |
| `vocabProgressStore` | 102 | `VocabProgressStore` | Data | VocabSprintRunner |
| `wordMasteryStore` | 103 | `WordMasteryStore` (var) | Data | DailyCoordinator / VocabSprintRunner |
| `backupManager` | 104 | `BackupManager` | Data | ViewModel (stays) |
| `profileStore` | 105 | `ProfileStore` | Data | ViewModel (stays) |
| `ttsModelManager` | 106 | `TtsModelManager` | Audio | AudioCoordinator |
| `ttsEngine` | 107 | `TtsEngine` | Audio | AudioCoordinator |
| `asrModelManager` | 108 | `AsrModelManager` | Audio | AudioCoordinator |
| `asrEngine` | 109 | `AsrEngine?` | Audio | AudioCoordinator |

### 1.4 Helper Dependencies

| Instance | Line | Type | Target Module |
|----------|------|------|---------------|
| `dailySessionHelper` | 141 | `DailySessionHelper` | DailyPracticeCoordinator |

### 1.5 Private Mutable State Variables

| Variable | Line | Type | Purpose | Used By | Target Module |
|----------|------|------|---------|---------|---------------|
| `sessionCards` | 115 | `List<SentenceCard>` | Cards for current sub-lesson/boss/elite | `buildSessionCards()`, `submitAnswer()`, `nextCard()`, `prevCard()`, `startSession()`, `currentCard()`, `startBoss()`, `openEliteStep()`, `startDrill()`, `loadDrillCard()`, `finishSession()`, `markSubLessonCardsShown()`, `skipToNextCard()` | SessionRunner |
| `bossCards` | 116 | `List<SentenceCard>` | Cards for current boss battle | `startBoss()`, `finishBoss()` | BossBattleRunner |
| `eliteCards` | 117 | `List<SentenceCard>` | Cards for current elite step | `openEliteStep()` | SessionRunner (elite sub-mode) |
| `vocabSession` | 118 | `List<VocabEntry>` | Entries for current vocab sprint | `openVocabSprint()`, `moveToNextVocab()`, `buildVocabWordBank()`, `updateVocabWordBank()` | VocabSprintRunner |
| `subLessonTotal` | 119 | `Int` | Cards in current sub-lesson | `buildSessionCards()`, `startBoss()`, `startDrill()`, `loadDrillCard()` | SessionRunner |
| `subLessonCount` | 120 | `Int` | Total sub-lessons for schedule | `buildSessionCards()`, `startBoss()` | SessionRunner |
| `lessonSchedules` | 121 | `Map<String, LessonSchedule>` | Cached schedules per lesson | `rebuildSchedules()`, `buildSessionCards()`, `selectLesson()`, `submitAnswer()` | CardProvider |
| `scheduleKey` | 122 | `String` | Cache key for schedule validity | `rebuildSchedules()` | CardProvider |
| `timerJob` | 123 | `Job?` | Active timer coroutine | `resumeTimer()`, `pauseTimer()` | SessionRunner |
| `activeStartMs` | 124 | `Long?` | Timestamp for timer accumulation | `resumeTimer()`, `pauseTimer()` | SessionRunner |
| `forceBackupOnSave` | 125 | `Boolean` | Trigger backup on next save | `saveProgress()`, `submitAnswer()`, `completeStory()`, `moveToNextVocab()`, `saveProgressNow()` | ViewModel (stays) |
| `prebuiltDailySession` | 126 | `List<DailyTask>?` | Pre-computed daily session | `init`, `startDailyPractice()`, `resetAllProgress()` | DailyCoordinator |
| `lastDailyTasks` | 127 | `List<DailyTask>?` | In-memory cache for repeat | `startDailyPractice()`, `repeatDailyPractice()`, `resetAllProgress()` | DailyCoordinator |
| `dailyPracticeAnsweredCounts` | 129 | `MutableMap<DailyBlockType, Int>` | Per-block VOICE/KEYBOARD tracking | `recordDailyCardPracticed()`, `cancelDailySession()`, `startDailyPractice()` | DailyCoordinator |
| `dailyCursorAtSessionStart` | 131 | `DailyCursorState` | Snapshot for cancel rollback | `startDailyPractice()` | DailyCoordinator |
| `eliteSizeMultiplier` | 136 | `Double` | Config: elite card count multiplier | `buildEliteCards()`, `eliteSubLessonSize()`, `init` | SessionRunner (elite sub-mode) |
| `dailyBadCardIds` | 3548 | `MutableSet<String>` | Flagged daily cards | `flagDailyBadSentence()`, `unflagDailyBadSentence()`, `isDailyBadSentence()` | ViewModel (stays) |
| `ttsDownloadJob` | 2671 | `Job?` | Active TTS download coroutine | `beginTtsDownload()`, `startTtsDownloadForLanguage()` | AudioCoordinator |
| `asrDownloadJob` | 2749 | `Job?` | Active ASR download coroutine | `beginAsrDownload()` | AudioCoordinator |
| `bgDownloadJob` | 2886 | `Job?` | Background TTS download coroutine | `startBackgroundTtsDownload()` | AudioCoordinator |

---

## 2. Complete Method Inventory

### Legend

- **Visibility:** `pub` = public, `pri` = private
- **Category:** Domain grouping
- **Target Module:** Where this method should live after decomposition (per `arch-module-decomposition.md`)
- **Reads Fields:** Which `TrainingUiState` fields the method reads (non-exhaustive for trivial reads)
- **Writes Fields:** Which `TrainingUiState` fields the method writes via `_uiState.update`
- **Stores Touched:** Which data stores are accessed

### 2.1 Session Management Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 1 | `init {}` | - | 150-351 | Initialization | ViewModel (stays) | All | All | lessonStore, progressStore, configStore, profileStore, masteryStore, streakStore, badSentenceStore, wordMasteryStore, ttsModelManager, asrModelManager, ttsEngine |
| 2 | `onInputChanged(text)` | pub | 353-362 | Input | SessionRunner | inputText, answerText, incorrectAttemptsForCard | inputText, incorrectAttemptsForCard, answerText | none |
| 3 | `onVoicePromptStarted()` | pub | 364-368 | Session | SessionRunner | voicePromptStartMs | voicePromptStartMs | none |
| 4 | `setInputMode(mode)` | pub | 370-391 | Input | SessionRunner | inputMode, sessionState, answerText, incorrectAttemptsForCard | inputMode, incorrectAttemptsForCard, answerText, voiceTriggerToken, voicePromptStartMs | none |
| 5 | `togglePause()` | pub | 924-932 | Session | SessionRunner | sessionState | sessionState, voicePromptStartMs | progressStore (via saveProgress) |
| 6 | `pauseSession()` | pub | 934-938 | Session | SessionRunner | sessionState, voicePromptStartMs | sessionState, voicePromptStartMs | progressStore (via saveProgress) |
| 7 | `finishSession()` | pub | 940-971 | Session | SessionRunner | eliteActive, bossActive, activeTimeMs, correctCount, voicePromptStartMs | sessionState, lastRating, incorrectAttemptsForCard, lastResult, answerText, currentIndex, currentCard, inputText, voicePromptStartMs | progressStore (via saveProgress) |
| 8 | `showAnswer()` | pub | 973-986 | Session | SessionRunner | currentCard, inputMode | answerText, sessionState, inputText, hintCount, voicePromptStartMs | progressStore (via saveProgress) |
| 9 | `resumeFromSettings()` | pub | 1289-1292 | Session | SessionRunner | sessionState | (delegates to startSession) | (delegates) |
| 10 | `startSession()` | pri | 2436-2459 | Session | SessionRunner | bossActive, eliteActive, currentCard, inputMode | sessionState, inputText, voiceTriggerToken, voicePromptStartMs | progressStore (via saveProgress) |
| 11 | `currentCard()` | pri | 2461-2465 | Session | SessionRunner | currentIndex | (returns SentenceCard?) | none |
| 12 | `resumeTimer()` | pri | 2467-2480 | Timer | SessionRunner | activeTimeMs | activeTimeMs | progressStore (via saveProgress) |
| 13 | `pauseTimer()` | pri | 2482-2486 | Timer | SessionRunner | (none) | (none) | none |

### 2.2 Answer Submission & Validation Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 14 | `submitAnswer()` | pub | 624-849 | Answer | SessionRunner + ProgressTracker + BossBattleRunner + FlowerProgressRenderer + StreakManager | sessionState, inputText, testMode, currentIndex, inputMode, voicePromptStartMs, bossActive, eliteActive, isDrillMode, completedSubLessonCount, subLessonCount, activeSubLessonIndex, eliteStepIndex, eliteBestSpeeds, activeTimeMs, voiceActiveMs, voiceWordCount | correctCount, incorrectCount, incorrectAttemptsForCard, lastResult, answerText, inputText, sessionState, voiceTriggerToken, voicePromptStartMs, voiceActiveMs, voiceWordCount, currentIndex, activeSubLessonIndex, completedSubLessonCount, subLessonFinishedToken, eliteActive, eliteStepIndex, eliteBestSpeeds, eliteFinishedToken | masteryStore, progressStore, streakStore (indirect via helpers) |

### 2.3 Card Navigation Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 15 | `nextCard(triggerVoice)` | pub | 851-904 | Navigation | SessionRunner | sessionState, currentIndex, inputMode, bossActive, bossProgress, bossTotal, bossReward, bossRewardMessage | currentIndex, currentCard, inputText, lastResult, answerText, incorrectAttemptsForCard, sessionState, voiceTriggerToken, voicePromptStartMs, bossProgress, bossReward, bossRewardMessage | masteryStore (via recordCardShowForMastery), progressStore (via saveProgress) |
| 16 | `prevCard()` | pub | 906-922 | Navigation | SessionRunner | currentIndex | currentIndex, currentCard, inputText, lastResult, answerText, incorrectAttemptsForCard, voicePromptStartMs | masteryStore (via recordCardShowForMastery), progressStore (via saveProgress) |

### 2.4 Language / Lesson / Pack Selection Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 17 | `selectLanguage(languageId)` | pub | 393-472 | Selection | ViewModel (stays) | All session fields | selectedLanguageId, lessons, selectedLessonId, activePackId, activePackLessonIds + full reset | lessonStore, progressStore, wordMasteryStore, masteryStore (via helpers) |
| 18 | `selectLesson(lessonId)` | pub | 474-549 | Selection | ViewModel (stays) | selectedLanguageId, lessons, selectedLessonId | selectedLessonId, activePackId, activePackLessonIds, mode + full reset | lessonStore, progressStore, masteryStore (via helpers) |
| 19 | `selectPack(packId)` | pub | 551-569 | Selection | ViewModel (stays) | selectedLessonId, activePackId, activePackLessonIds | (delegates to selectLesson or sets packId directly) | lessonStore, progressStore, wordMasteryStore |
| 20 | `selectMode(mode)` | pub | 571-622 | Selection | ViewModel (stays) | All session fields | mode + full reset | progressStore (via saveProgress) |
| 21 | `selectSubLesson(index)` | pub | 1377-1391 | Selection | SessionRunner | activeSubLessonIndex, currentIndex | activeSubLessonIndex, currentIndex, inputText, lastResult, answerText, sessionState | progressStore (via saveProgress) |

### 2.5 Lesson Content Management Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 22 | `importLesson(uri)` | pub | 988-992 | Content | ViewModel (stays) | selectedLanguageId | (delegates to refreshLessons) | lessonStore |
| 23 | `importLessonPack(uri)` | pub | 994-1060 | Content | ViewModel (stays) | selectedLanguageId | All selection + session fields | lessonStore, progressStore (via helpers) |
| 24 | `resetAndImportLesson(uri)` | pub | 1061-1066 | Content | ViewModel (stays) | selectedLanguageId | (delegates to refreshLessons) | lessonStore |
| 25 | `deleteLesson(lessonId)` | pub | 1068-1073 | Content | ViewModel (stays) | selectedLanguageId, selectedLessonId | (delegates to refreshLessons) | lessonStore |
| 26 | `createEmptyLesson(title)` | pub | 1075-1079 | Content | ViewModel (stays) | selectedLanguageId | (delegates to refreshLessons) | lessonStore |
| 27 | `addLanguage(name)` | pub | 1081-1143 | Content | ViewModel (stays) | All session fields | languages, installedPacks, selectedLanguageId, lessons, selectedLessonId + full reset | lessonStore, progressStore (via helpers) |
| 28 | `deleteAllLessons()` | pub | 1145-1150 | Content | ViewModel (stays) | selectedLanguageId | installedPacks | lessonStore, progressStore (via helpers) |
| 29 | `resetAllProgress()` | pub | 1155-1200 | Content | ViewModel (stays) | dailySession, dailyCursor, currentIndex, correctCount, incorrectCount, sessionState | dailySession, dailyCursor, currentIndex, correctCount, incorrectCount, sessionState, inputText, lastResult, answerText, incorrectAttemptsForCard | progressStore, masteryStore, wordMasteryStore, VerbDrillStore (via file ops) |
| 30 | `deletePack(packId)` | pub | 1202-1210 | Content | ViewModel (stays) | selectedLanguageId, installedPacks | installedPacks | lessonStore |
| 31 | `refreshLessons(selectedLessonId)` | pri | 1234-1287 | Content | ViewModel (stays) | selectedLanguageId | lessons, selectedLessonId + full reset | lessonStore, progressStore (via helpers), masteryStore (via helpers) |

### 2.6 Schedule & Card Building Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 32 | `rebuildSchedules(lessons)` | pri | 1294-1301 | Scheduling | CardProvider | (uses private vars) | (updates lessonSchedules, scheduleKey) | none (delegates to MixedReviewScheduler) |
| 33 | `buildSessionCards()` | pri | 1303-1375 | Scheduling | CardProvider | bossActive, eliteActive, isDrillMode, mode, selectedLessonId, activeSubLessonIndex, lessons, currentIndex | currentIndex, currentCard, sessionState, subLessonTotal, subLessonCount, activeSubLessonIndex, completedSubLessonCount, subLessonTypes | masteryStore, hiddenCardStore |
| 34 | `buildEliteCards()` | pri | 2604-2608 | Scheduling | CardProvider | lessons | (returns List<SentenceCard>) | none |

### 2.7 Boss Battle Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 35 | `startBossLesson()` | pub | 2216-2218 | Boss | BossBattleRunner | (delegates to startBoss) | (delegates) | (delegates) |
| 36 | `startBossMega()` | pub | 2220-2222 | Boss | BossBattleRunner | (delegates to startBoss) | (delegates) | (delegates) |
| 37 | `startBossElite()` | pub | 2224-2226 | Boss | BossBattleRunner | (delegates to startBoss) | (delegates) | (delegates) |
| 38 | `startBoss(type)` | pri | 2228-2303 | Boss | BossBattleRunner | completedSubLessonCount, testMode, selectedLessonId, lessons | bossActive, bossType, bossTotal, bossProgress, bossReward, bossRewardMessage, bossErrorMessage, currentIndex, currentCard + all session counters | none |
| 39 | `finishBoss()` | pub | 2305-2365 | Boss | BossBattleRunner | bossActive, bossType, bossProgress, bossTotal, bossReward, bossLessonRewards, bossMegaRewards, selectedLessonId | bossActive, bossType, bossTotal, bossProgress, bossReward, bossRewardMessage, bossFinishedToken, bossLastType, bossErrorMessage, bossLessonRewards, bossMegaRewards, selectedLessonId, mode + session fields | progressStore (via saveProgress), masteryStore (via helpers) |
| 40 | `clearBossRewardMessage()` | pub | 2367-2388 | Boss | BossBattleRunner | bossActive, sessionState, currentCard, inputMode | bossRewardMessage, sessionState, voiceTriggerToken, inputText | none |
| 41 | `clearBossError()` | pub | 2390-2392 | Boss | BossBattleRunner | bossErrorMessage | bossErrorMessage | none |
| 42 | `updateBossProgress(progress)` | pri | 2394-2415 | Boss | BossBattleRunner | bossTotal, bossReward, bossRewardMessage, sessionState | bossProgress, bossReward, bossRewardMessage, sessionState | none |
| 43 | `resolveBossReward(progress, total)` | pri | 2417-2426 | Boss | BossBattleRunner | (pure function) | (returns BossReward?) | none |
| 44 | `bossRewardMessage(reward)` | pri | 2428-2434 | Boss | BossBattleRunner | (pure function) | (returns String) | none |

### 2.8 Elite Mode Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 45 | `openEliteStep(index)` | pub | 1393-1425 | Elite | SessionRunner (elite sub-mode) | eliteStepIndex, eliteBestSpeeds | eliteActive, eliteStepIndex, currentIndex, currentCard + all session counters | progressStore (via saveProgress) |
| 46 | `cancelEliteSession()` | pub | 1427-1444 | Elite | SessionRunner (elite sub-mode) | eliteActive | eliteActive, sessionState, currentIndex, inputText, lastResult, answerText, incorrectAttemptsForCard, voicePromptStartMs | progressStore (via saveProgress), masteryStore (via helpers) |
| 47 | `resolveEliteUnlocked(lessons, testMode)` | pri | 2582-2584 | Elite | SessionRunner (elite sub-mode) | (pure function) | (returns Boolean) | none |
| 48 | `normalizeEliteSpeeds(speeds)` | pri | 2586-2592 | Elite | SessionRunner (elite sub-mode) | (pure function) | (returns List<Double>) | none |
| 49 | `eliteSubLessonSize()` | pri | 2594-2596 | Elite | SessionRunner (elite sub-mode) | (uses private var) | (returns Int) | none |
| 50 | `calculateSpeedPerMinute(activeMs, words)` | pri | 2598-2602 | Elite | SessionRunner (elite sub-mode) | (pure function) | (returns Double) | none |

### 2.9 Daily Practice Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 51 | `hasResumableDailySession()` | pub | 1448-1455 | Daily | DailyCoordinator | dailyCursor | (returns Boolean) | progressStore |
| 52 | `startDailyPractice(lessonLevel)` | pub | 1457-1521 | Daily | DailyCoordinator | dailyCursor, activePackId, selectedLanguageId, dailySession | dailyCursor (via storeFirstSessionCardIds) | VerbDrillStore, WordMasteryStore, lessonStore, progressStore (via saveProgress) |
| 53 | `storeFirstSessionCardIds(sentenceIds, verbIds)` | pri | 1527-1539 | Daily | DailyCoordinator | dailyCursor | dailyCursor | progressStore (via saveProgress) |
| 54 | `advanceCursor(sentenceCount)` | pri | 1546-1564 | Daily | DailyCoordinator / ProgressTracker | dailyCursor | dailyCursor | lessonStore |
| 55 | `repeatDailyPractice(lessonLevel)` | pub | 1566-1619 | Daily | DailyCoordinator | dailyCursor, activePackId, selectedLanguageId | (delegates to dailySessionHelper) | VerbDrillStore, WordMasteryStore, lessonStore |
| 56 | `advanceDailyTask()` | pub | 1621-1628 | Daily | DailyCoordinator | dailySession | (delegates to dailySessionHelper + persistDailyVerbProgress) | VerbDrillStore |
| 57 | `recordDailyCardPracticed(blockType)` | pub | 1635-1650 | Daily | DailyCoordinator / ProgressTracker | dailySession, activePackId, selectedLanguageId | (none via updateState) | masteryStore |
| 58 | `advanceDailyBlock()` | pub | 1652-1657 | Daily | DailyCoordinator | dailySession | (delegates to dailySessionHelper) | none |
| 59 | `persistDailyVerbProgress(card)` | pub | 1659-1675 | Daily | DailyCoordinator | activePackId | (none via updateState) | VerbDrillStore |
| 60 | `repeatDailyBlock()` | pub | 1677-1699 | Daily | DailyCoordinator | dailySession, activePackId, selectedLanguageId | (delegates to dailySessionHelper) | VerbDrillStore, WordMasteryStore, lessonStore |
| 61 | `cancelDailySession()` | pub | 1701-1722 | Daily | DailyCoordinator | dailySession, dailyPracticeAnsweredCounts | (delegates to dailySessionHelper) | progressStore (via advanceCursor) |
| 62 | `rateVocabCard(rating)` | pub | 1728-1755 | Daily | DailyCoordinator | activePackId | (none via updateState) | WordMasteryStore |
| 63 | `getDailyCurrentTask()` | pub | 1757-1759 | Daily | DailyCoordinator | dailySession | (delegates to dailySessionHelper) | none |
| 64 | `getDailyBlockProgress()` | pub | 1761-1763 | Daily | DailyCoordinator | dailySession | (delegates to dailySessionHelper) | none |
| 65 | `submitDailySentenceAnswer(input)` | pub | 1765-1776 | Daily | DailyCoordinator + AnswerValidator | dailySession | (none via updateState) | none |
| 66 | `submitDailyVerbAnswer(input)` | pub | 1778-1789 | Daily | DailyCoordinator + AnswerValidator | dailySession | (none via updateState) | none |
| 67 | `getDailySentenceAnswer()` | pub | 1791-1794 | Daily | DailyCoordinator | dailySession | (returns String?) | none |
| 68 | `getDailyVerbAnswer()` | pub | 1796-1799 | Daily | DailyCoordinator | dailySession | (returns String?) | none |

### 2.10 Drill Mode Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 69 | `showDrillStartDialog(lessonId)` | pub | 1817-1827 | Drill | SessionRunner (drill sub-mode) | lessons, selectedLessonId | drillShowStartDialog, drillHasProgress | drillProgressStore |
| 70 | `startDrill(resume)` | pub | 1829-1880 | Drill | SessionRunner (drill sub-mode) | selectedLessonId, lessons, drillCardIndex, activePackId | isDrillMode, drillCardIndex, drillTotalCards, drillShowStartDialog, drillHasProgress + all session + boss + elite reset | drillProgressStore, badSentenceStore, progressStore (via saveProgress) |
| 71 | `dismissDrillDialog()` | pub | 1882-1884 | Drill | SessionRunner (drill sub-mode) | drillShowStartDialog | drillShowStartDialog | none |
| 72 | `loadDrillCard(cardIndex, activate)` | pri | 1886-1914 | Drill | SessionRunner (drill sub-mode) | selectedLessonId, lessons, inputMode | currentIndex, currentCard, subLessonTotal, drillCardIndex, sessionState, inputText, lastResult, answerText, incorrectAttemptsForCard, voiceTriggerToken | none |
| 73 | `advanceDrillCard()` | pub | 1916-1929 | Drill | SessionRunner (drill sub-mode) | isDrillMode, selectedLessonId, drillCardIndex, drillTotalCards | (delegates to loadDrillCard or finishDrill) | drillProgressStore |
| 74 | `finishDrill(lessonId)` | pri | 1931-1947 | Drill | SessionRunner (drill sub-mode) | isDrillMode | isDrillMode, drillCardIndex, drillTotalCards, sessionState, currentIndex, currentCard, subLessonFinishedToken | drillProgressStore, masteryStore (via helpers), progressStore (via saveProgress) |
| 75 | `exitDrillMode()` | pub | 1949-1975 | Drill | SessionRunner (drill sub-mode) | isDrillMode, selectedLessonId, drillCardIndex, activePackId | isDrillMode, drillCardIndex, drillTotalCards, sessionState, currentIndex, inputText, lastResult, answerText, incorrectAttemptsForCard, voicePromptStartMs, badSentenceCount | drillProgressStore, badSentenceStore, masteryStore (via helpers), progressStore (via saveProgress) |

### 2.11 Story Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 76 | `openStory(phase)` | pub | 1979-1988 | Story | ViewModel (stays) | selectedLessonId, selectedLanguageId | activeStory, storyErrorMessage | lessonStore |
| 77 | `completeStory(phase, allCorrect)` | pub | 2064-2079 | Story | ViewModel (stays) | testMode | storyCheckInDone, storyCheckOutDone, activeStory | progressStore (via saveProgress) |
| 78 | `clearStoryError()` | pub | 2081-2083 | Story | ViewModel (stays) | storyErrorMessage | storyErrorMessage | none |

### 2.12 Vocab Sprint Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 79 | `hasVocabProgress()` | pub | 1990-1995 | Vocab | VocabSprintRunner | selectedLessonId, selectedLanguageId | (returns Boolean) | vocabProgressStore |
| 80 | `openVocabSprint(resume)` | pub | 1997-2062 | Vocab | VocabSprintRunner | selectedLessonId, selectedLanguageId, vocabSprintLimit | currentVocab, vocabInputText, vocabAttempts, vocabAnswerText, vocabIndex, vocabTotal, vocabWordBankWords, vocabErrorMessage, vocabInputMode, vocabVoiceTriggerToken + boss reset | lessonStore, vocabProgressStore |
| 81 | `clearVocabError()` | pub | 2085-2087 | Vocab | VocabSprintRunner | vocabErrorMessage | vocabErrorMessage | none |
| 82 | `onVocabInputChanged(text)` | pub | 2089-2098 | Vocab | VocabSprintRunner | vocabInputText, vocabAttempts, vocabAnswerText | vocabInputText, vocabAttempts, vocabAnswerText | none |
| 83 | `setVocabInputMode(mode)` | pub | 2100-2107 | Vocab | VocabSprintRunner | vocabInputMode, vocabWordBankWords | vocabInputMode, vocabWordBankWords | none |
| 84 | `requestVocabVoice()` | pub | 2109-2116 | Vocab | VocabSprintRunner | vocabInputMode, vocabVoiceTriggerToken | vocabInputMode, vocabVoiceTriggerToken | none |
| 85 | `submitVocabAnswer(inputOverride)` | pub | 2118-2163 | Vocab | VocabSprintRunner + AnswerValidator | currentVocab, vocabInputText, vocabAttempts, vocabIndex, testMode, vocabInputMode, selectedLessonId, selectedLanguageId | vocabAttempts, vocabAnswerText, vocabInputText, vocabVoiceTriggerToken | vocabProgressStore |
| 86 | `showVocabAnswer()` | pub | 2165-2174 | Vocab | VocabSprintRunner | currentVocab | vocabAnswerText, vocabInputText, vocabAttempts | none |
| 87 | `moveToNextVocab()` | pri | 2176-2214 | Vocab | VocabSprintRunner | currentVocab, vocabIndex, vocabSession | currentVocab, vocabInputText, vocabAttempts, vocabAnswerText, vocabIndex, vocabTotal, vocabWordBankWords, vocabFinishedToken | vocabProgressStore, progressStore (via saveProgress) |

### 2.13 Word Bank Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 88 | `generateWordBank(correctAnswer, extraWords)` | pri | 3071-3075 | WordBank | WordBankGenerator | (pure function) | (returns List<String>) | none |
| 89 | `updateWordBank()` | pri | 3080-3114 | WordBank | WordBankGenerator | currentCard, lessons | wordBankWords, selectedWords, inputText | none |
| 90 | `buildVocabWordBank(entry, pool)` | pri | 3116-3163 | WordBank | WordBankGenerator | selectedLanguageId, lessons | (returns List<String>) | lessonStore |
| 91 | `updateVocabWordBank()` | pri | 3165-3169 | WordBank | WordBankGenerator | currentVocab, vocabWordBankWords | vocabWordBankWords | none |
| 92 | `selectWordFromBank(word)` | pub | 3174-3185 | WordBank | SessionRunner | selectedWords, inputText | selectedWords, inputText | none |
| 93 | `removeLastSelectedWord()` | pub | 3190-3203 | WordBank | SessionRunner | selectedWords, inputText | selectedWords, inputText | none |

### 2.14 TTS Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 94 | `onTtsSpeak(text, speed)` | pub | 2616-2631 | TTS | AudioCoordinator | selectedLanguageId, ttsSpeed | (delegates to ttsEngine) | ttsEngine, ttsModelManager |
| 95 | `setTtsSpeed(speed)` | pub | 2633-2635 | TTS | AudioCoordinator | ttsSpeed | ttsSpeed | none |
| 96 | `setRuTextScale(scale)` | pub | 2637-2639 | TTS | AudioCoordinator | ruTextScale | ruTextScale | none |
| 97 | `startTtsDownload()` | pub | 2641-2648 | TTS | AudioCoordinator | ttsMeteredNetwork | ttsMeteredNetwork | ttsModelManager |
| 98 | `confirmTtsDownloadOnMetered()` | pub | 2650-2653 | TTS | AudioCoordinator | ttsMeteredNetwork | ttsMeteredNetwork | none |
| 99 | `dismissMeteredWarning()` | pub | 2655-2657 | TTS | AudioCoordinator | ttsMeteredNetwork | ttsMeteredNetwork | none |
| 100 | `dismissTtsDownloadDialog()` | pub | 2663-2669 | TTS | AudioCoordinator | ttsDownloadState | ttsDownloadState | none |
| 101 | `beginTtsDownload()` | pri | 2673-2691 | TTS | AudioCoordinator | selectedLanguageId, ttsModelReady, ttsDownloadState | ttsModelReady, ttsDownloadState | ttsModelManager |
| 102 | `checkTtsModel()` | pri | 2693-2697 | TTS | AudioCoordinator | selectedLanguageId, ttsModelReady | ttsModelReady | ttsModelManager |
| 103 | `checkAllTtsModels()` | pri | 2699-2704 | TTS | AudioCoordinator | ttsModelsReady | ttsModelsReady | ttsModelManager |
| 104 | `startTtsDownloadForLanguage(languageId)` | pub | 2706-2741 | TTS | AudioCoordinator | ttsModelsReady, bgTtsDownloadStates, ttsDownloadState, ttsModelReady | ttsModelsReady, bgTtsDownloadStates, ttsDownloadState, ttsModelReady | ttsModelManager |
| 105 | `stopTts()` | pub | 2743-2745 | TTS | AudioCoordinator | (delegates to ttsEngine) | (none) | ttsEngine |
| 106 | `startBackgroundTtsDownload()` | pri | 2888-2937 | TTS | AudioCoordinator | languages, selectedLanguageId, ttsModelReady, ttsDownloadState, bgTtsDownloading, ttsModelsReady | bgTtsDownloadStates, bgTtsDownloading, ttsModelReady, ttsDownloadState, ttsModelsReady | ttsModelManager |
| 107 | `setTtsDownloadStateFromBackground(bgState)` | pub | 2939-2941 | TTS | AudioCoordinator | ttsDownloadState | ttsDownloadState | none |
| 108 | `playSuccessTone()` | pri | 2952-2956 | Audio | AudioCoordinator | (none) | (none) | soundPool |
| 109 | `playErrorTone()` | pri | 2958-2962 | Audio | AudioCoordinator | (none) | (none) | soundPool |
| 110 | `playSuccessSound()` | pri | 1801-1805 | Audio | AudioCoordinator | (none) | (none) | soundPool |
| 111 | `playErrorSound()` | pri | 1807-1811 | Audio | AudioCoordinator | (none) | (none) | soundPool |

> **Note on dual sound methods:** `playSuccessTone()` and `playSuccessSound()` are duplicates (same implementation). Similarly `playErrorTone()` / `playErrorSound()`. They exist because one set was introduced for session mode and the other for daily practice mode. During migration, these should be unified into a single method.

### 2.15 ASR Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 112 | `checkAsrModel()` | pub | 2751-2754 | ASR | AudioCoordinator | asrModelReady | asrModelReady | asrModelManager |
| 113 | `dismissAsrDownloadDialog()` | pub | 2756-2758 | ASR | AudioCoordinator | asrDownloadState | asrDownloadState | none |
| 114 | `startOfflineRecognition()` | pub | 2760-2772 | ASR | AudioCoordinator | asrState, asrErrorMessage | (delegates to transcribeWithOfflineAsr + onInputChanged) | asrEngine |
| 115 | `stopAsr()` | pub | 2774-2776 | ASR | AudioCoordinator | (delegates to asrEngine) | (none) | asrEngine |
| 116 | `setUseOfflineAsr(enabled)` | pub | 2778-2788 | ASR | AudioCoordinator | useOfflineAsr, asrState, asrModelReady, asrErrorMessage | useOfflineAsr, asrState, asrModelReady, asrErrorMessage | configStore, asrEngine |
| 117 | `startAsrDownload()` | pub | 2790-2796 | ASR | AudioCoordinator | asrMeteredNetwork | asrMeteredNetwork | asrModelManager |
| 118 | `confirmAsrDownloadOnMetered()` | pub | 2798-2801 | ASR | AudioCoordinator | asrMeteredNetwork | asrMeteredNetwork | none |
| 119 | `dismissAsrMeteredWarning()` | pub | 2803-2805 | ASR | AudioCoordinator | asrMeteredNetwork | asrMeteredNetwork | none |
| 120 | `beginAsrDownload()` | pri | 2807-2826 | ASR | AudioCoordinator | asrDownloadState, asrModelReady | asrDownloadState, asrModelReady | asrModelManager |
| 121 | `transcribeWithOfflineAsr()` | pri | 2832-2882 | ASR | AudioCoordinator | selectedLanguageId, asrState, asrErrorMessage | asrState, asrErrorMessage | asrEngine |

### 2.16 Progress & Mastery Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 122 | `saveProgress()` | pri | 2488-2522 | Persistence | ProgressTracker | selectedLanguageId, mode, selectedLessonId, currentIndex, correctCount, incorrectCount, incorrectAttemptsForCard, activeTimeMs, sessionState, bossLessonRewards, bossMegaRewards, voiceActiveMs, voiceWordCount, hintCount, eliteStepIndex, eliteBestSpeeds, currentScreen, activePackId, dailySession | (none via updateState) | progressStore, backupManager |
| 123 | `recordCardShowForMastery(card)` | pri | 2989-3010 | Mastery | ProgressTracker | bossActive, isDrillMode, inputMode, selectedLanguageId | (none via updateState) | masteryStore |
| 124 | `markSubLessonCardsShown(cards)` | pri | 3012-3023 | Mastery | ProgressTracker | inputMode, selectedLessonId, lessons | (none via updateState) | masteryStore |
| 125 | `checkAndMarkLessonCompleted()` | pri | 3028-3035 | Mastery | ProgressTracker | completedSubLessonCount, selectedLessonId | (none via updateState) | masteryStore |
| 126 | `calculateCompletedSubLessons(subLessons, mastery, lessonId)` | pri | 3040-3067 | Mastery | ProgressTracker | lessons | (returns Int) | none |
| 127 | `resolveCardLessonId(card)` | pri | 2968-2982 | Mastery | ProgressTracker | selectedLessonId, lessons | (returns String) | none |
| 128 | `resolveProgressLessonInfo()` | pri | 2533-2572 | Mastery | ProgressTracker | activePackId, selectedLanguageId, activePackLessonIds, lessons, dailyCursor | (returns Pair?) | masteryStore |
| 129 | `getProgressLessonLevel()` | pub | 2578-2580 | Mastery | ProgressTracker | (delegates to resolveProgressLessonInfo) | (returns Int) | none |
| 130 | `refreshFlowerStates()` | pri | 3208-3246 | Flowers | FlowerProgressRenderer | selectedLanguageId, lessons, selectedLessonId | lessonFlowers, currentLessonFlower, currentLessonShownCount, ladderRows | masteryStore |
| 131 | `rebindWordMasteryStore(packId)` | pri | 3253-3255 | Stores | ViewModel (stays) | (none) | (updates private var) | WordMasteryStore (constructor) |
| 132 | `countMetricWords(text)` | pri | 2610-2614 | Utility | AnswerValidator | (pure function) | (returns Int) | none |

### 2.17 Streak Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 133 | `updateStreak()` | pri | 3269-3303 | Streak | StreakManager | selectedLanguageId, currentStreak, longestStreak, streakMessage, streakCelebrationToken | currentStreak, longestStreak, streakMessage, streakCelebrationToken | streakStore |
| 134 | `dismissStreakMessage()` | pub | 3308-3312 | Streak | StreakManager | streakMessage | streakMessage | none |

### 2.18 Configuration & Profile Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 135 | `toggleTestMode()` | pub | 1212-1224 | Config | ViewModel (stays) | testMode, lessons | testMode, eliteUnlocked | configStore |
| 136 | `updateVocabSprintLimit(limit)` | pub | 2226-2232 | Config | ViewModel (stays) | vocabSprintLimit | vocabSprintLimit | configStore |
| 137 | `updateUserName(newName)` | pub | 3332-3342 | Config | ViewModel (stays) | userName | userName | profileStore |
| 138 | `saveProgressNow()` | pub | 3344-3348 | Config | ViewModel (stays) | (none) | (none) | progressStore (via saveProgress) |
| 139 | `onScreenChanged(screenName)` | pub | 3350-3352 | Config | ViewModel (stays) | currentScreen | currentScreen | none |

### 2.19 Bad Sentences & Hidden Cards Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 140 | `flagBadSentence()` | pub | 3507-3523 | BadSentence | ViewModel (stays) | currentCard, activePackId, selectedLanguageId, isDrillMode, badSentenceCount | badSentenceCount | badSentenceStore |
| 141 | `unflagBadSentence()` | pub | 3525-3530 | BadSentence | ViewModel (stays) | currentCard, activePackId, badSentenceCount | badSentenceCount | badSentenceStore |
| 142 | `isBadSentence()` | pub | 3532-3536 | BadSentence | ViewModel (stays) | currentCard, activePackId | (returns Boolean) | badSentenceStore |
| 143 | `exportBadSentences()` | pub | 3538-3544 | BadSentence | ViewModel (stays) | activePackId | (returns String?) | badSentenceStore |
| 144 | `flagDailyBadSentence(cardId, languageId, sentence, translation, mode)` | pub | 3550-3561 | BadSentence | ViewModel (stays) | activePackId | (none via updateState) | badSentenceStore |
| 145 | `unflagDailyBadSentence(cardId)` | pub | 3563-3567 | BadSentence | ViewModel (stays) | activePackId | (none via updateState) | badSentenceStore |
| 146 | `isDailyBadSentence(cardId)` | pub | 3569-3572 | BadSentence | ViewModel (stays) | activePackId | (returns Boolean) | badSentenceStore |
| 147 | `exportDailyBadSentences()` | pub | 3574-3580 | BadSentence | ViewModel (stays) | activePackId | (returns String?) | badSentenceStore |
| 148 | `hideCurrentCard()` | pub | 3582-3586 | HiddenCards | ViewModel (stays) | currentCard | (delegates to skipToNextCard) | hiddenCardStore |
| 149 | `unhideCurrentCard()` | pub | 3588-3591 | HiddenCards | ViewModel (stays) | currentCard | (none via updateState) | hiddenCardStore |
| 150 | `isCurrentCardHidden()` | pub | 3593-3596 | HiddenCards | ViewModel (stays) | currentCard | (returns Boolean) | hiddenCardStore |
| 151 | `skipToNextCard()` | pri | 3598-3623 | HiddenCards | SessionRunner | currentIndex, sessionCards, sessionState | currentIndex, currentCard, inputText, lastResult, answerText, incorrectAttemptsForCard, sessionState | none |

### 2.20 Backup & Restore Methods

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 152 | `createProgressBackup()` | pub | 3318-3327 | Backup | ViewModel (stays) | (none) | (none) | backupManager |
| 153 | `restoreBackup(backupUri)` | pub | 3357-3445 | Backup | ViewModel (stays) | selectedLanguageId, all session fields | selectedLanguageId, lessons, selectedLessonId, mode, sessionState, currentIndex, correctCount, incorrectCount, incorrectAttemptsForCard, activeTimeMs, voiceActiveMs, voiceWordCount, hintCount, currentStreak, longestStreak, bossLessonRewards, bossMegaRewards, userName, eliteStepIndex, eliteBestSpeeds | backupManager, progressStore, profileStore, lessonStore, streakStore, masteryStore (via helpers) |
| 154 | `reloadFromDisk()` | pub | 3447-3505 | Backup | ViewModel (stays) | selectedLanguageId, all session fields | languages, installedPacks, selectedLanguageId, lessons, selectedLessonId, mode, sessionState + many others | progressStore, profileStore, lessonStore, streakStore, masteryStore (via helpers) |

### 2.21 Lifecycle & Misc

| # | Method | Vis | Lines | Category | Target Module | Reads Fields | Writes Fields | Stores Touched |
|---|--------|-----|-------|----------|---------------|-------------|---------------|----------------|
| 155 | `onCleared()` | pub | 2943-2950 | Lifecycle | ViewModel (stays) | (none) | (none) | progressStore (via saveProgress), ttsEngine, asrEngine, soundPool |
| 156 | `refreshVocabMasteryCount()` | pub | 3261-3264 | Misc | ViewModel (stays) | vocabMasteredCount | vocabMasteredCount | wordMasteryStore |

**Total public methods: 108**
**Total private methods: 48**
**Grand total: 156 methods (including `init {}`)**

> Note: The `init {}` block is counted as 1 entry. Sound methods `playSuccessSound()`/`playErrorSound()` (lines 1801-1811) are separate from `playSuccessTone()`/`playErrorTone()` (lines 2952-2962) and are listed separately.

---

## 3. Complete Field Inventory (TrainingUiState)

All fields from `TrainingUiState` data class (lines 3631-3746):

| # | Field | Type | Default | Category | Target Module Owner |
|---|-------|------|---------|----------|---------------------|
| 1 | `languages` | `List<Language>` | `emptyList()` | Language & Packs | ViewModel |
| 2 | `installedPacks` | `List<LessonPack>` | `emptyList()` | Language & Packs | ViewModel |
| 3 | `selectedLanguageId` | `String` | `"en"` | Language & Packs | ViewModel |
| 4 | `activePackId` | `String?` | `null` | Language & Packs | ViewModel |
| 5 | `activePackLessonIds` | `List<String>?` | `null` | Language & Packs | ViewModel |
| 6 | `lessons` | `List<Lesson>` | `emptyList()` | Lessons | ViewModel |
| 7 | `selectedLessonId` | `String?` | `null` | Lessons | ViewModel |
| 8 | `mode` | `TrainingMode` | `TrainingMode.LESSON` | Session Core | SessionRunner |
| 9 | `sessionState` | `SessionState` | `SessionState.ACTIVE` | Session Core | SessionRunner |
| 10 | `currentIndex` | `Int` | `0` | Session Core | SessionRunner |
| 11 | `currentCard` | `SentenceCard?` | `null` | Session Core | SessionRunner |
| 12 | `inputText` | `String` | `""` | Session Core | SessionRunner |
| 13 | `correctCount` | `Int` | `0` | Session Core | SessionRunner |
| 14 | `incorrectCount` | `Int` | `0` | Session Core | SessionRunner |
| 15 | `incorrectAttemptsForCard` | `Int` | `0` | Session Core | SessionRunner |
| 16 | `activeTimeMs` | `Long` | `0L` | Timer & Metrics | SessionRunner |
| 17 | `voiceActiveMs` | `Long` | `0L` | Timer & Metrics | SessionRunner |
| 18 | `voiceWordCount` | `Int` | `0` | Timer & Metrics | SessionRunner |
| 19 | `hintCount` | `Int` | `0` | Timer & Metrics | SessionRunner |
| 20 | `voicePromptStartMs` | `Long?` | `null` | Timer & Metrics | SessionRunner |
| 21 | `answerText` | `String?` | `null` | Answer | SessionRunner |
| 22 | `lastResult` | `Boolean?` | `null` | Answer | SessionRunner |
| 23 | `lastRating` | `Double?` | `null` | Answer | SessionRunner |
| 24 | `inputMode` | `InputMode` | `InputMode.VOICE` | Answer | SessionRunner |
| 25 | `voiceTriggerToken` | `Int` | `0` | Answer | SessionRunner |
| 26 | `subLessonTotal` | `Int` | `0` | Sub-lessons | SessionRunner |
| 27 | `subLessonCount` | `Int` | `0` | Sub-lessons | SessionRunner |
| 28 | `subLessonTypes` | `List<SubLessonType>` | `emptyList()` | Sub-lessons | SessionRunner |
| 29 | `activeSubLessonIndex` | `Int` | `0` | Sub-lessons | SessionRunner |
| 30 | `completedSubLessonCount` | `Int` | `0` | Sub-lessons | SessionRunner |
| 31 | `subLessonFinishedToken` | `Int` | `0` | Sub-lessons | SessionRunner |
| 32 | `storyCheckInDone` | `Boolean` | `false` | Story | ViewModel |
| 33 | `storyCheckOutDone` | `Boolean` | `false` | Story | ViewModel |
| 34 | `activeStory` | `StoryQuiz?` | `null` | Story | ViewModel |
| 35 | `storyErrorMessage` | `String?` | `null` | Story | ViewModel |
| 36 | `currentVocab` | `VocabEntry?` | `null` | Vocab Sprint | VocabSprintRunner |
| 37 | `vocabInputText` | `String` | `""` | Vocab Sprint | VocabSprintRunner |
| 38 | `vocabAttempts` | `Int` | `0` | Vocab Sprint | VocabSprintRunner |
| 39 | `vocabAnswerText` | `String?` | `null` | Vocab Sprint | VocabSprintRunner |
| 40 | `vocabIndex` | `Int` | `0` | Vocab Sprint | VocabSprintRunner |
| 41 | `vocabTotal` | `Int` | `0` | Vocab Sprint | VocabSprintRunner |
| 42 | `vocabWordBankWords` | `List<String>` | `emptyList()` | Vocab Sprint | VocabSprintRunner |
| 43 | `vocabFinishedToken` | `Int` | `0` | Vocab Sprint | VocabSprintRunner |
| 44 | `vocabErrorMessage` | `String?` | `null` | Vocab Sprint | VocabSprintRunner |
| 45 | `vocabInputMode` | `InputMode` | `InputMode.VOICE` | Vocab Sprint | VocabSprintRunner |
| 46 | `vocabVoiceTriggerToken` | `Int` | `0` | Vocab Sprint | VocabSprintRunner |
| 47 | `bossActive` | `Boolean` | `false` | Boss | BossBattleRunner |
| 48 | `bossType` | `BossType?` | `null` | Boss | BossBattleRunner |
| 49 | `bossTotal` | `Int` | `0` | Boss | BossBattleRunner |
| 50 | `bossProgress` | `Int` | `0` | Boss | BossBattleRunner |
| 51 | `bossReward` | `BossReward?` | `null` | Boss | BossBattleRunner |
| 52 | `bossRewardMessage` | `String?` | `null` | Boss | BossBattleRunner |
| 53 | `bossFinishedToken` | `Int` | `0` | Boss | BossBattleRunner |
| 54 | `bossLastType` | `BossType?` | `null` | Boss | BossBattleRunner |
| 55 | `bossErrorMessage` | `String?` | `null` | Boss | BossBattleRunner |
| 56 | `bossLessonRewards` | `Map<String, BossReward>` | `emptyMap()` | Boss | BossBattleRunner |
| 57 | `bossMegaRewards` | `Map<String, BossReward>` | `emptyMap()` | Boss | BossBattleRunner |
| 58 | `testMode` | `Boolean` | `false` | Config | ViewModel |
| 59 | `eliteActive` | `Boolean` | `false` | Elite | SessionRunner (elite sub-mode) |
| 60 | `eliteStepIndex` | `Int` | `0` | Elite | SessionRunner (elite sub-mode) |
| 61 | `eliteBestSpeeds` | `List<Double>` | `emptyList()` | Elite | SessionRunner (elite sub-mode) |
| 62 | `eliteFinishedToken` | `Int` | `0` | Elite | SessionRunner (elite sub-mode) |
| 63 | `eliteUnlocked` | `Boolean` | `false` | Elite | SessionRunner (elite sub-mode) |
| 64 | `eliteSizeMultiplier` | `Double` | `1.25` | Elite | SessionRunner (elite sub-mode) |
| 65 | `vocabSprintLimit` | `Int` | `20` | Config | ViewModel |
| 66 | `lessonFlowers` | `Map<String, FlowerVisual>` | `emptyMap()` | Flowers | FlowerProgressRenderer |
| 67 | `currentLessonFlower` | `FlowerVisual?` | `null` | Flowers | FlowerProgressRenderer |
| 68 | `currentLessonShownCount` | `Int` | `0` | Flowers | FlowerProgressRenderer |
| 69 | `wordBankWords` | `List<String>` | `emptyList()` | Word Bank | SessionRunner / WordBankGenerator |
| 70 | `selectedWords` | `List<String>` | `emptyList()` | Word Bank | SessionRunner |
| 71 | `currentStreak` | `Int` | `0` | Streak | StreakManager |
| 72 | `longestStreak` | `Int` | `0` | Streak | StreakManager |
| 73 | `streakMessage` | `String?` | `null` | Streak | StreakManager |
| 74 | `streakCelebrationToken` | `Int` | `0` | Streak | StreakManager |
| 75 | `userName` | `String` | `"GrammarMateUser"` | Profile | ViewModel |
| 76 | `ladderRows` | `List<LessonLadderRow>` | `emptyList()` | Ladder | FlowerProgressRenderer |
| 77 | `ttsState` | `TtsState` | `TtsState.IDLE` | TTS | AudioCoordinator |
| 78 | `ttsDownloadState` | `DownloadState` | `DownloadState.Idle` | TTS | AudioCoordinator |
| 79 | `ttsModelReady` | `Boolean` | `false` | TTS | AudioCoordinator |
| 80 | `ttsMeteredNetwork` | `Boolean` | `false` | TTS | AudioCoordinator |
| 81 | `bgTtsDownloading` | `Boolean` | `false` | TTS | AudioCoordinator |
| 82 | `bgTtsDownloadStates` | `Map<String, DownloadState>` | `emptyMap()` | TTS | AudioCoordinator |
| 83 | `ttsModelsReady` | `Map<String, Boolean>` | `emptyMap()` | TTS | AudioCoordinator |
| 84 | `ttsSpeed` | `Float` | `1.0f` | TTS | AudioCoordinator |
| 85 | `ruTextScale` | `Float` | `1.0f` | TTS | AudioCoordinator |
| 86 | `badSentenceCount` | `Int` | `0` | Bad Sentences | ViewModel |
| 87 | `isDrillMode` | `Boolean` | `false` | Drill | SessionRunner (drill sub-mode) |
| 88 | `drillCardIndex` | `Int` | `0` | Drill | SessionRunner (drill sub-mode) |
| 89 | `drillTotalCards` | `Int` | `0` | Drill | SessionRunner (drill sub-mode) |
| 90 | `drillShowStartDialog` | `Boolean` | `false` | Drill | SessionRunner (drill sub-mode) |
| 91 | `drillHasProgress` | `Boolean` | `false` | Drill | SessionRunner (drill sub-mode) |
| 92 | `useOfflineAsr` | `Boolean` | `false` | ASR | AudioCoordinator |
| 93 | `asrState` | `AsrState` | `AsrState.IDLE` | ASR | AudioCoordinator |
| 94 | `asrModelReady` | `Boolean` | `false` | ASR | AudioCoordinator |
| 95 | `asrDownloadState` | `DownloadState` | `DownloadState.Idle` | ASR | AudioCoordinator |
| 96 | `asrMeteredNetwork` | `Boolean` | `false` | ASR | AudioCoordinator |
| 97 | `asrErrorMessage` | `String?` | `null` | ASR | AudioCoordinator |
| 98 | `audioPermissionDenied` | `Boolean` | `false` | ASR | AudioCoordinator |
| 99 | `initialScreen` | `String` | `"HOME"` | Navigation | ViewModel |
| 100 | `currentScreen` | `String` | `"HOME"` | Navigation | ViewModel |
| 101 | `vocabMasteredCount` | `Int` | `0` | Vocab Mastery | ViewModel |
| 102 | `dailySession` | `DailySessionState` | `DailySessionState()` | Daily Practice | DailyCoordinator |
| 103 | `dailyCursor` | `DailyCursorState` | `DailyCursorState()` | Daily Practice | DailyCoordinator |

**Total: 103 fields**

---

## 4. Store Access Map

### 4.1 Read Access

| Store | Methods that read (non-exhaustive list of key callers) |
|-------|------------------------------------------------------|
| `lessonStore` | `init`, `selectLanguage`, `selectLesson`, `selectPack`, `importLesson`, `importLessonPack`, `addLanguage`, `deleteLesson`, `deletePack`, `buildSessionCards`, `openStory`, `openVocabSprint`, `buildVocabWordBank`, `startDailyPractice`, `repeatDailyPractice`, `repeatDailyBlock`, `advanceCursor`, `resolveProgressLessonInfo`, `restoreBackup`, `reloadFromDisk`, `rebuildSchedules` |
| `progressStore` | `init`, `saveProgress`, `startDailyPractice` (via hasResumableDailySession), `finishBoss`, `resetAllProgress`, `restoreBackup`, `reloadFromDisk` |
| `configStore` | `init`, `toggleTestMode`, `updateVocabSprintLimit`, `setUseOfflineAsr` |
| `masteryStore` | `recordCardShowForMastery`, `markSubLessonCardsShown`, `checkAndMarkLessonCompleted`, `calculateCompletedSubLessons`, `buildSessionCards`, `selectLesson`, `submitAnswer`, `refreshFlowerStates`, `resolveProgressLessonInfo`, `recordDailyCardPracticed`, `resetAllProgress` |
| `streakStore` | `init`, `updateStreak`, `restoreBackup`, `reloadFromDisk` |
| `badSentenceStore` | `init`, `startDrill`, `exitDrillMode`, `flagBadSentence`, `unflagBadSentence`, `isBadSentence`, `exportBadSentences`, `flagDailyBadSentence`, `unflagDailyBadSentence`, `isDailyBadSentence`, `exportDailyBadSentences` |
| `hiddenCardStore` | `buildSessionCards`, `hideCurrentCard`, `unhideCurrentCard`, `isCurrentCardHidden` |
| `drillProgressStore` | `showDrillStartDialog`, `startDrill`, `advanceDrillCard`, `finishDrill`, `exitDrillMode` |
| `vocabProgressStore` | `hasVocabProgress`, `openVocabSprint`, `submitVocabAnswer`, `moveToNextVocab` |
| `wordMasteryStore` | `init`, `rebindWordMasteryStore`, `refreshVocabMasteryCount`, `rateVocabCard`, `startDailyPractice`, `repeatDailyPractice`, `repeatDailyBlock`, `resetAllProgress` |
| `backupManager` | `createProgressBackup`, `restoreBackup`, `saveProgress` (conditional) |
| `profileStore` | `init`, `updateUserName`, `restoreBackup`, `reloadFromDisk` |
| `ttsModelManager` | `onTtsSpeak`, `checkTtsModel`, `checkAllTtsModels`, `startTtsDownload`, `beginTtsDownload`, `startTtsDownloadForLanguage`, `startBackgroundTtsDownload` |
| `ttsEngine` | `onTtsSpeak`, `stopTts`, `init` (state collection), `onCleared` |
| `asrModelManager` | `init`, `checkAsrModel`, `startAsrDownload`, `beginAsrDownload`, `setUseOfflineAsr` |
| `asrEngine` | `startOfflineRecognition`, `stopAsr`, `setUseOfflineAsr`, `transcribeWithOfflineAsr`, `selectLanguage`, `onCleared` |

### 4.2 Write Access

| Store | Methods that write |
|-------|-------------------|
| `lessonStore` | `importLesson`, `importLessonPack`, `resetAndImportLesson`, `deleteLesson`, `createEmptyLesson`, `addLanguage`, `deleteAllLessons`, `deletePack`, `init` (ensureSeedData, forceReloadDefaultPacks) |
| `progressStore` | `saveProgress` (called from ~30+ methods), `resetAllProgress` (clear) |
| `configStore` | `toggleTestMode`, `updateVocabSprintLimit`, `setUseOfflineAsr` |
| `masteryStore` | `recordCardShowForMastery`, `markSubLessonCardsShown`, `checkAndMarkLessonCompleted`, `resetAllProgress` (clear) |
| `streakStore` | `updateStreak` |
| `badSentenceStore` | `flagBadSentence`, `unflagBadSentence`, `flagDailyBadSentence`, `unflagDailyBadSentence` |
| `hiddenCardStore` | `hideCurrentCard`, `unhideCurrentCard` |
| `drillProgressStore` | `advanceDrillCard` (saveDrillProgress), `finishDrill` (clearDrillProgress), `exitDrillMode` (saveDrillProgress) |
| `vocabProgressStore` | `submitVocabAnswer` (recordCorrect/recordIncorrect), `openVocabSprint` (clearSprintProgress), `moveToNextVocab` (clearSprintProgress) |
| `wordMasteryStore` | `rateVocabCard` (upsertMastery), `resetAllProgress` (saveAll empty) |
| `backupManager` | `createProgressBackup`, `restoreBackup` |
| `profileStore` | `updateUserName` |
| `ttsEngine` | `onTtsSpeak` (speak), `stopTts` (stop), `onCleared` (release) |
| `ttsModelManager` | `beginTtsDownload`, `startTtsDownloadForLanguage`, `startBackgroundTtsDownload` |
| `asrEngine` | `startOfflineRecognition`, `stopAsr` (stopRecording), `setUseOfflineAsr` (release), `selectLanguage` (setLanguage), `onCleared` (release) |
| `asrModelManager` | `beginAsrDownload` |
| `soundPool` | `playSuccessTone`, `playErrorTone`, `playSuccessSound`, `playErrorSound`, `onCleared` (release) |

---

## 5. Reset Blocks

Six methods contain large `copy()` reset blocks that reset nearly all session state. These are the primary source of bugs when a new field is added to `TrainingUiState` but forgotten in one of these blocks.

### 5.1 Field Reset Matrix

| Field | selectLanguage | selectLesson | selectMode | importLessonPack | addLanguage | refreshLessons |
|-------|:-:|:-:|:-:|:-:|:-:|:-:|
| `selectedLanguageId` | SET | - | - | SET | SET | - |
| `lessons` | SET | - | - | SET | SET | SET |
| `selectedLessonId` | SET | SET | - | SET | SET | SET |
| `activePackId` | SET | SET | - | - | - | - |
| `activePackLessonIds` | SET | SET | - | - | - | - |
| `eliteActive` | `false` | - | - | `false` | `false` | `false` |
| `eliteUnlocked` | CALC | - | - | CALC | CALC | CALC |
| `currentIndex` | `0` | `0` | `0` | `0` | `0` | `0` |
| `correctCount` | `0` | - | - | `0` | `0` | - |
| `incorrectCount` | `0` | - | - | `0` | `0` | - |
| `incorrectAttemptsForCard` | `0` | `0` | `0` | `0` | `0` | `0` |
| `inputText` | `""` | `""` | `""` | `""` | `""` | `""` |
| `lastResult` | `null` | `null` | `null` | `null` | `null` | `null` |
| `answerText` | `null` | `null` | `null` | `null` | `null` | `null` |
| `inputMode` | VOICE | VOICE | VOICE | VOICE | VOICE | VOICE |
| `sessionState` | PAUSED | PAUSED | PAUSED | PAUSED | PAUSED | PAUSED |
| `voicePromptStartMs` | `null` | `null` | `null` | `null` | `null` | `null` |
| `activeSubLessonIndex` | `0` | CALC | `0` | `0` | `0` | `0` |
| `completedSubLessonCount` | `0` | CALC | `0` | `0` | `0` | `0` |
| `subLessonFinishedToken` | `0` | `0` | `0` | `0` | `0` | `0` |
| `storyCheckInDone` | `false` | `false` | `false` | `false` | `false` | `false` |
| `storyCheckOutDone` | `false` | `false` | `false` | `false` | `false` | `false` |
| `activeStory` | `null` | `null` | `null` | `null` | `null` | `null` |
| `storyErrorMessage` | `null` | `null` | `null` | `null` | `null` | `null` |
| `currentVocab` | `null` | - | `null` | `null` | `null` | `null` |
| `vocabInputText` | `""` | - | `""` | `""` | `""` | `""` |
| `vocabAttempts` | `0` | - | `0` | `0` | `0` | `0` |
| `vocabAnswerText` | `null` | - | `null` | `null` | `null` | `null` |
| `vocabIndex` | `0` | - | `0` | `0` | `0` | `0` |
| `vocabTotal` | `0` | - | `0` | `0` | `0` | `0` |
| `vocabWordBankWords` | `emptyList()` | - | `emptyList()` | `emptyList()` | `emptyList()` | `emptyList()` |
| `vocabFinishedToken` | `0` | - | `0` | `0` | `0` | `0` |
| `vocabErrorMessage` | `null` | - | `null` | `null` | `null` | `null` |
| `vocabInputMode` | VOICE | - | VOICE | VOICE | VOICE | VOICE |
| `vocabVoiceTriggerToken` | `0` | - | `0` | `0` | `0` | `0` |
| `bossActive` | `false` | - | `false` | `false` | `false` | `false` |
| `bossType` | `null` | - | `null` | `null` | `null` | `null` |
| `bossTotal` | `0` | - | `0` | `0` | `0` | `0` |
| `bossProgress` | `0` | - | `0` | `0` | `0` | `0` |
| `bossReward` | `null` | - | `null` | `null` | `null` | `null` |
| `bossRewardMessage` | `null` | - | `null` | `null` | `null` | `null` |
| `bossFinishedToken` | `0` | - | `0` | `0` | `0` | `0` |
| `bossErrorMessage` | `null` | - | `null` | `null` | `null` | `null` |
| `bossLessonRewards` | `emptyMap()` | - | - | - | - | - |
| `bossMegaRewards` | `emptyMap()` | - | - | - | - | - |
| `lessonFlowers` | `emptyMap()` | - | - | - | - | - |
| `currentLessonFlower` | `null` | - | - | - | - | - |
| `wordBankWords` | `emptyList()` | `emptyList()` | `emptyList()` | `emptyList()` | `emptyList()` | `emptyList()` |
| `selectedWords` | `emptyList()` | `emptyList()` | `emptyList()` | `emptyList()` | `emptyList()` | `emptyList()` |
| `isDrillMode` | - | `false` | `false` | - | - | - |
| `drillCardIndex` | - | `0` | `0` | - | - | - |
| `drillTotalCards` | - | `0` | `0` | - | - | - |
| `drillShowStartDialog` | - | `false` | `false` | - | - | - |
| `drillHasProgress` | - | `false` | `false` | - | - | - |
| `currentCard` | - | `null` | - | - | - | - |
| `activeTimeMs` | - | - | - | `0L` | `0L` | - |
| `voiceActiveMs` | - | - | - | `0L` | `0L` | - |
| `voiceWordCount` | - | - | - | `0` | `0` | - |
| `hintCount` | - | - | - | `0` | `0` | - |
| `mode` | - | LESSON | - | LESSON | LESSON | - |
| `vocabMasteredCount` | CALC | - | - | - | - | - |

**Key:**
- `-` = field is NOT reset in this method
- `SET` = field is explicitly set to a new value
- `CALC` = field is computed from data
- `false`/`0`/`null`/`""`/`emptyList()`/`emptyMap()` = reset to default

### 5.2 Inconsistencies Found

1. **`selectLanguage` clears `bossLessonRewards` and `bossMegaRewards`** but `selectLesson`, `selectMode`, `refreshLessons`, `importLessonPack`, `addLanguage` do NOT. This is intentional -- switching language invalidates rewards, but switching lessons within a language preserves them.

2. **`selectLanguage` resets `activeTimeMs`, `voiceActiveMs`, `voiceWordCount`, `hintCount` implicitly** (they are not in the copy block, so they retain their previous values). However `importLessonPack` and `addLanguage` explicitly reset them to 0. This is a potential inconsistency -- `selectLanguage` should probably also reset these.

3. **`selectLesson` resets drill state (`isDrillMode`, `drillCardIndex`, etc.)** but `selectLanguage` does NOT. After switching languages, drill mode state could leak. This is likely a bug -- the language switch re-runs `buildSessionCards()` which returns early if `isDrillMode == true`.

4. **`correctCount`/`incorrectCount` are NOT reset in `selectLesson` or `refreshLessons`** but ARE reset in `selectLanguage`, `importLessonPack`, `addLanguage`. This means switching lessons preserves the running score, which may or may not be intentional.

---

## 6. Method-to-Module Completeness Checklist

Every method in TrainingViewModel.kt is listed below with its target module assignment. Methods marked "ViewModel (stays)" will remain in the thin orchestrator after decomposition.

### SessionRunner (lesson flow + elite/drill sub-modes + word bank interaction)

| # | Method | Target Module | Source Lines | Checklist |
|---|--------|---------------|-------------|-----------|
| 1 | `init {}` | ViewModel (stays) | 150-351 | [ ] |
| 2 | `onInputChanged(text)` | SessionRunner | 353-362 | [ ] |
| 3 | `onVoicePromptStarted()` | SessionRunner | 364-368 | [ ] |
| 4 | `setInputMode(mode)` | SessionRunner | 370-391 | [ ] |
| 5 | `togglePause()` | SessionRunner | 924-932 | [ ] |
| 6 | `pauseSession()` | SessionRunner | 934-938 | [ ] |
| 7 | `finishSession()` | SessionRunner | 940-971 | [ ] |
| 8 | `showAnswer()` | SessionRunner | 973-986 | [ ] |
| 9 | `resumeFromSettings()` | SessionRunner | 1289-1292 | [ ] |
| 10 | `submitAnswer()` | SessionRunner | 624-849 | [ ] |
| 11 | `nextCard(triggerVoice)` | SessionRunner | 851-904 | [ ] |
| 12 | `prevCard()` | SessionRunner | 906-922 | [ ] |
| 13 | `startSession()` | SessionRunner | 2436-2459 | [ ] |
| 14 | `currentCard()` | SessionRunner | 2461-2465 | [ ] |
| 15 | `resumeTimer()` | SessionRunner | 2467-2480 | [ ] |
| 16 | `pauseTimer()` | SessionRunner | 2482-2486 | [ ] |
| 17 | `selectSubLesson(index)` | SessionRunner | 1377-1391 | [ ] |
| 18 | `openEliteStep(index)` | SessionRunner | 1393-1425 | [ ] |
| 19 | `cancelEliteSession()` | SessionRunner | 1427-1444 | [ ] |
| 20 | `resolveEliteUnlocked(lessons, testMode)` | SessionRunner | 2582-2584 | [ ] |
| 21 | `normalizeEliteSpeeds(speeds)` | SessionRunner | 2586-2592 | [ ] |
| 22 | `eliteSubLessonSize()` | SessionRunner | 2594-2596 | [ ] |
| 23 | `calculateSpeedPerMinute(activeMs, words)` | SessionRunner | 2598-2602 | [ ] |
| 24 | `showDrillStartDialog(lessonId)` | SessionRunner | 1817-1827 | [ ] |
| 25 | `startDrill(resume)` | SessionRunner | 1829-1880 | [ ] |
| 26 | `dismissDrillDialog()` | SessionRunner | 1882-1884 | [ ] |
| 27 | `loadDrillCard(cardIndex, activate)` | SessionRunner | 1886-1914 | [ ] |
| 28 | `advanceDrillCard()` | SessionRunner | 1916-1929 | [ ] |
| 29 | `finishDrill(lessonId)` | SessionRunner | 1931-1947 | [ ] |
| 30 | `exitDrillMode()` | SessionRunner | 1949-1975 | [ ] |
| 31 | `selectWordFromBank(word)` | SessionRunner | 3174-3185 | [ ] |
| 32 | `removeLastSelectedWord()` | SessionRunner | 3190-3203 | [ ] |
| 33 | `skipToNextCard()` | SessionRunner | 3598-3623 | [ ] |

### CardProvider (card selection algorithms)

| # | Method | Target Module | Source Lines | Checklist |
|---|--------|---------------|-------------|-----------|
| 34 | `rebuildSchedules(lessons)` | CardProvider | 1294-1301 | [ ] |
| 35 | `buildSessionCards()` | CardProvider | 1303-1375 | [ ] |
| 36 | `buildEliteCards()` | CardProvider | 2604-2608 | [ ] |

### AnswerValidator (normalization + comparison)

| # | Method | Target Module | Source Lines | Checklist |
|---|--------|---------------|-------------|-----------|
| 37 | `countMetricWords(text)` | AnswerValidator | 2610-2614 | [ ] |

> Note: Core validation logic is inline in `submitAnswer()`, `submitDailySentenceAnswer()`, `submitDailyVerbAnswer()`, `submitVocabAnswer()`. These inline patterns must be extracted into `AnswerValidator` during migration.

### WordBankGenerator (word bank + distractors)

| # | Method | Target Module | Source Lines | Checklist |
|---|--------|---------------|-------------|-----------|
| 38 | `generateWordBank(correctAnswer, extraWords)` | WordBankGenerator | 3071-3075 | [ ] |
| 39 | `updateWordBank()` | WordBankGenerator | 3080-3114 | [ ] |
| 40 | `buildVocabWordBank(entry, pool)` | WordBankGenerator | 3116-3163 | [ ] |
| 41 | `updateVocabWordBank()` | WordBankGenerator | 3165-3169 | [ ] |

### ProgressTracker (mastery + persistence)

| # | Method | Target Module | Source Lines | Checklist |
|---|--------|---------------|-------------|-----------|
| 42 | `saveProgress()` | ProgressTracker | 2488-2522 | [ ] |
| 43 | `recordCardShowForMastery(card)` | ProgressTracker | 2989-3010 | [ ] |
| 44 | `markSubLessonCardsShown(cards)` | ProgressTracker | 3012-3023 | [ ] |
| 45 | `checkAndMarkLessonCompleted()` | ProgressTracker | 3028-3035 | [ ] |
| 46 | `calculateCompletedSubLessons(subLessons, mastery, lessonId)` | ProgressTracker | 3040-3067 | [ ] |
| 47 | `resolveCardLessonId(card)` | ProgressTracker | 2968-2982 | [ ] |
| 48 | `resolveProgressLessonInfo()` | ProgressTracker | 2533-2572 | [ ] |
| 49 | `getProgressLessonLevel()` | ProgressTracker | 2578-2580 | [ ] |

### FlowerProgressRenderer (flower state computation)

| # | Method | Target Module | Source Lines | Checklist |
|---|--------|---------------|-------------|-----------|
| 50 | `refreshFlowerStates()` | FlowerProgressRenderer | 3208-3246 | [ ] |

### BossBattleRunner (boss session lifecycle)

| # | Method | Target Module | Source Lines | Checklist |
|---|--------|---------------|-------------|-----------|
| 51 | `startBossLesson()` | BossBattleRunner | 2216-2218 | [ ] |
| 52 | `startBossMega()` | BossBattleRunner | 2220-2222 | [ ] |
| 53 | `startBossElite()` | BossBattleRunner | 2224-2226 | [ ] |
| 54 | `startBoss(type)` | BossBattleRunner | 2228-2303 | [ ] |
| 55 | `finishBoss()` | BossBattleRunner | 2305-2365 | [ ] |
| 56 | `clearBossRewardMessage()` | BossBattleRunner | 2367-2388 | [ ] |
| 57 | `clearBossError()` | BossBattleRunner | 2390-2392 | [ ] |
| 58 | `updateBossProgress(progress)` | BossBattleRunner | 2394-2415 | [ ] |
| 59 | `resolveBossReward(progress, total)` | BossBattleRunner | 2417-2426 | [ ] |
| 60 | `bossRewardMessage(reward)` | BossBattleRunner | 2428-2434 | [ ] |

### DailyPracticeCoordinator (3-block session)

| # | Method | Target Module | Source Lines | Checklist |
|---|--------|---------------|-------------|-----------|
| 61 | `hasResumableDailySession()` | DailyCoordinator | 1448-1455 | [ ] |
| 62 | `startDailyPractice(lessonLevel)` | DailyCoordinator | 1457-1521 | [ ] |
| 63 | `storeFirstSessionCardIds(sentenceIds, verbIds)` | DailyCoordinator | 1527-1539 | [ ] |
| 64 | `advanceCursor(sentenceCount)` | DailyCoordinator | 1546-1564 | [ ] |
| 65 | `repeatDailyPractice(lessonLevel)` | DailyCoordinator | 1566-1619 | [ ] |
| 66 | `advanceDailyTask()` | DailyCoordinator | 1621-1628 | [ ] |
| 67 | `recordDailyCardPracticed(blockType)` | DailyCoordinator | 1635-1650 | [ ] |
| 68 | `advanceDailyBlock()` | DailyCoordinator | 1652-1657 | [ ] |
| 69 | `persistDailyVerbProgress(card)` | DailyCoordinator | 1659-1675 | [ ] |
| 70 | `repeatDailyBlock()` | DailyCoordinator | 1677-1699 | [ ] |
| 71 | `cancelDailySession()` | DailyCoordinator | 1701-1722 | [ ] |
| 72 | `rateVocabCard(rating)` | DailyCoordinator | 1728-1755 | [ ] |
| 73 | `getDailyCurrentTask()` | DailyCoordinator | 1757-1759 | [ ] |
| 74 | `getDailyBlockProgress()` | DailyCoordinator | 1761-1763 | [ ] |
| 75 | `submitDailySentenceAnswer(input)` | DailyCoordinator | 1765-1776 | [ ] |
| 76 | `submitDailyVerbAnswer(input)` | DailyCoordinator | 1778-1789 | [ ] |
| 77 | `getDailySentenceAnswer()` | DailyCoordinator | 1791-1794 | [ ] |
| 78 | `getDailyVerbAnswer()` | DailyCoordinator | 1796-1799 | [ ] |

### VocabSprintRunner (vocab flashcard session)

| # | Method | Target Module | Source Lines | Checklist |
|---|--------|---------------|-------------|-----------|
| 79 | `hasVocabProgress()` | VocabSprintRunner | 1990-1995 | [ ] |
| 80 | `openVocabSprint(resume)` | VocabSprintRunner | 1997-2062 | [ ] |
| 81 | `clearVocabError()` | VocabSprintRunner | 2085-2087 | [ ] |
| 82 | `onVocabInputChanged(text)` | VocabSprintRunner | 2089-2098 | [ ] |
| 83 | `setVocabInputMode(mode)` | VocabSprintRunner | 2100-2107 | [ ] |
| 84 | `requestVocabVoice()` | VocabSprintRunner | 2109-2116 | [ ] |
| 85 | `submitVocabAnswer(inputOverride)` | VocabSprintRunner | 2118-2163 | [ ] |
| 86 | `showVocabAnswer()` | VocabSprintRunner | 2165-2174 | [ ] |
| 87 | `moveToNextVocab()` | VocabSprintRunner | 2176-2214 | [ ] |

### AudioCoordinator (TTS + ASR + SoundPool)

| # | Method | Target Module | Source Lines | Checklist |
|---|--------|---------------|-------------|-----------|
| 88 | `onTtsSpeak(text, speed)` | AudioCoordinator | 2616-2631 | [ ] |
| 89 | `setTtsSpeed(speed)` | AudioCoordinator | 2633-2635 | [ ] |
| 90 | `setRuTextScale(scale)` | AudioCoordinator | 2637-2639 | [ ] |
| 91 | `startTtsDownload()` | AudioCoordinator | 2641-2648 | [ ] |
| 92 | `confirmTtsDownloadOnMetered()` | AudioCoordinator | 2650-2653 | [ ] |
| 93 | `dismissMeteredWarning()` | AudioCoordinator | 2655-2657 | [ ] |
| 94 | `dismissTtsDownloadDialog()` | AudioCoordinator | 2663-2669 | [ ] |
| 95 | `beginTtsDownload()` | AudioCoordinator | 2673-2691 | [ ] |
| 96 | `checkTtsModel()` | AudioCoordinator | 2693-2697 | [ ] |
| 97 | `checkAllTtsModels()` | AudioCoordinator | 2699-2704 | [ ] |
| 98 | `startTtsDownloadForLanguage(languageId)` | AudioCoordinator | 2706-2741 | [ ] |
| 99 | `stopTts()` | AudioCoordinator | 2743-2745 | [ ] |
| 100 | `startBackgroundTtsDownload()` | AudioCoordinator | 2888-2937 | [ ] |
| 101 | `setTtsDownloadStateFromBackground(bgState)` | AudioCoordinator | 2939-2941 | [ ] |
| 102 | `playSuccessTone()` | AudioCoordinator | 2952-2956 | [ ] |
| 103 | `playErrorTone()` | AudioCoordinator | 2958-2962 | [ ] |
| 104 | `playSuccessSound()` | AudioCoordinator | 1801-1805 | [ ] |
| 105 | `playErrorSound()` | AudioCoordinator | 1807-1811 | [ ] |
| 106 | `checkAsrModel()` | AudioCoordinator | 2751-2754 | [ ] |
| 107 | `dismissAsrDownloadDialog()` | AudioCoordinator | 2756-2758 | [ ] |
| 108 | `startOfflineRecognition()` | AudioCoordinator | 2760-2772 | [ ] |
| 109 | `stopAsr()` | AudioCoordinator | 2774-2776 | [ ] |
| 110 | `setUseOfflineAsr(enabled)` | AudioCoordinator | 2778-2788 | [ ] |
| 111 | `startAsrDownload()` | AudioCoordinator | 2790-2796 | [ ] |
| 112 | `confirmAsrDownloadOnMetered()` | AudioCoordinator | 2798-2801 | [ ] |
| 113 | `dismissAsrMeteredWarning()` | AudioCoordinator | 2803-2805 | [ ] |
| 114 | `beginAsrDownload()` | AudioCoordinator | 2807-2826 | [ ] |
| 115 | `transcribeWithOfflineAsr()` | AudioCoordinator | 2832-2882 | [ ] |

### StreakManager (streak tracking)

| # | Method | Target Module | Source Lines | Checklist |
|---|--------|---------------|-------------|-----------|
| 116 | `updateStreak()` | StreakManager | 3269-3303 | [ ] |
| 117 | `dismissStreakMessage()` | StreakManager | 3308-3312 | [ ] |

### ViewModel (stays in thin orchestrator)

| # | Method | Target Module | Source Lines | Checklist |
|---|--------|---------------|-------------|-----------|
| 118 | `selectLanguage(languageId)` | ViewModel | 393-472 | [ ] |
| 119 | `selectLesson(lessonId)` | ViewModel | 474-549 | [ ] |
| 120 | `selectPack(packId)` | ViewModel | 551-569 | [ ] |
| 121 | `selectMode(mode)` | ViewModel | 571-622 | [ ] |
| 122 | `importLesson(uri)` | ViewModel | 988-992 | [ ] |
| 123 | `importLessonPack(uri)` | ViewModel | 994-1060 | [ ] |
| 124 | `resetAndImportLesson(uri)` | ViewModel | 1061-1066 | [ ] |
| 125 | `deleteLesson(lessonId)` | ViewModel | 1068-1073 | [ ] |
| 126 | `createEmptyLesson(title)` | ViewModel | 1075-1079 | [ ] |
| 127 | `addLanguage(name)` | ViewModel | 1081-1143 | [ ] |
| 128 | `deleteAllLessons()` | ViewModel | 1145-1150 | [ ] |
| 129 | `resetAllProgress()` | ViewModel | 1155-1200 | [ ] |
| 130 | `deletePack(packId)` | ViewModel | 1202-1210 | [ ] |
| 131 | `refreshLessons(selectedLessonId)` | ViewModel | 1234-1287 | [ ] |
| 132 | `toggleTestMode()` | ViewModel | 1212-1224 | [ ] |
| 133 | `updateVocabSprintLimit(limit)` | ViewModel | 2226-2232 | [ ] |
| 134 | `updateUserName(newName)` | ViewModel | 3332-3342 | [ ] |
| 135 | `saveProgressNow()` | ViewModel | 3344-3348 | [ ] |
| 136 | `onScreenChanged(screenName)` | ViewModel | 3350-3352 | [ ] |
| 137 | `openStory(phase)` | ViewModel | 1979-1988 | [ ] |
| 138 | `completeStory(phase, allCorrect)` | ViewModel | 2064-2079 | [ ] |
| 139 | `clearStoryError()` | ViewModel | 2081-2083 | [ ] |
| 140 | `flagBadSentence()` | ViewModel | 3507-3523 | [ ] |
| 141 | `unflagBadSentence()` | ViewModel | 3525-3530 | [ ] |
| 142 | `isBadSentence()` | ViewModel | 3532-3536 | [ ] |
| 143 | `exportBadSentences()` | ViewModel | 3538-3544 | [ ] |
| 144 | `flagDailyBadSentence(...)` | ViewModel | 3550-3561 | [ ] |
| 145 | `unflagDailyBadSentence(cardId)` | ViewModel | 3563-3567 | [ ] |
| 146 | `isDailyBadSentence(cardId)` | ViewModel | 3569-3572 | [ ] |
| 147 | `exportDailyBadSentences()` | ViewModel | 3574-3580 | [ ] |
| 148 | `hideCurrentCard()` | ViewModel | 3582-3586 | [ ] |
| 149 | `unhideCurrentCard()` | ViewModel | 3588-3591 | [ ] |
| 150 | `isCurrentCardHidden()` | ViewModel | 3593-3596 | [ ] |
| 151 | `createProgressBackup()` | ViewModel | 3318-3327 | [ ] |
| 152 | `restoreBackup(backupUri)` | ViewModel | 3357-3445 | [ ] |
| 153 | `reloadFromDisk()` | ViewModel | 3447-3505 | [ ] |
| 154 | `onCleared()` | ViewModel | 2943-2950 | [ ] |
| 155 | `refreshVocabMasteryCount()` | ViewModel | 3261-3264 | [ ] |
| 156 | `rebindWordMasteryStore(packId)` | ViewModel | 3253-3255 | [ ] |

### Summary by Module

| Module | Method Count | Est. Lines |
|--------|:---:|:---:|
| SessionRunner | 33 | ~450 |
| CardProvider | 3 | ~250 |
| AnswerValidator | 1 (pure) + inline logic | ~80 |
| WordBankGenerator | 4 | ~120 |
| ProgressTracker | 8 | ~300 |
| FlowerProgressRenderer | 1 | ~60 |
| BossBattleRunner | 10 | ~200 |
| DailyPracticeCoordinator | 18 | ~350 |
| VocabSprintRunner | 9 | ~250 |
| AudioCoordinator | 28 | ~300 |
| StreakManager | 2 | ~80 |
| ViewModel (stays) | 39 | ~400 |
| **Total** | **156** | **~2840** |

---

## 7. Cross-Cutting Concerns for Migration

### 7.1 Methods That Span Multiple Modules

These methods touch multiple module domains and require careful decomposition:

| Method | Modules Touched | Migration Strategy |
|--------|----------------|-------------------|
| `submitAnswer()` | SessionRunner + ProgressTracker + BossBattleRunner + FlowerProgressRenderer + StreakManager | Split into: SessionRunner handles card flow, delegates mastery to ProgressTracker, boss branch to BossBattleRunner, elite branch stays in SessionRunner, end-of-sublesson triggers ProgressTracker + StreakManager + FlowerRenderer |
| `nextCard(triggerVoice)` | SessionRunner + BossBattleRunner + ProgressTracker | Boss progress tracking moves to BossBattleRunner, mastery recording to ProgressTracker, card navigation stays in SessionRunner |
| `startBoss(type)` | BossBattleRunner + CardProvider + SessionRunner | Card building moves to CardProvider, session setup moves to BossBattleRunner |
| `startDailyPractice(lessonLevel)` | DailyCoordinator + ProgressTracker + CardProvider (via DailySessionComposer) | Stays in DailyCoordinator, delegates cursor ops to ProgressTracker |
| `cancelDailySession()` | DailyCoordinator + ProgressTracker | Cursor advancement delegated to ProgressTracker |
| `init {}` | All modules | Must be decomposed: ViewModel keeps selection logic, delegates audio init to AudioCoordinator, daily pre-build to DailyCoordinator, etc. |

### 7.2 Shared Private State That Must Be Threaded

| Private Var | Accessing Modules | Migration Strategy |
|-------------|-------------------|-------------------|
| `sessionCards` | SessionRunner, BossBattleRunner (temporarily during startBoss) | SessionRunner owns. BossBattleRunner returns cards from `startBoss()`, SessionRunner assigns them. |
| `bossCards` | BossBattleRunner, SessionRunner (via `sessionCards`) | BossBattleRunner owns. Returns card list to SessionRunner for assignment to `sessionCards`. |
| `lessonSchedules` | CardProvider (build), SessionRunner (consume in buildSessionCards) | CardProvider owns. SessionRunner reads via getter. |
| `timerJob` / `activeStartMs` | SessionRunner | SessionRunner owns exclusively. |
| `dailyPracticeAnsweredCounts` | DailyCoordinator | DailyCoordinator owns exclusively. |
| `dailyCursorAtSessionStart` | DailyCoordinator | DailyCoordinator owns exclusively. |

### 7.3 Answer Validation Deduplication

Answer normalization + comparison is duplicated across 4 sites:

| Location | Current Code | Target |
|----------|-------------|--------|
| `submitAnswer()` line 629-630 | `Normalizer.normalize(input)` + comparison | AnswerValidator |
| `submitDailySentenceAnswer()` line 1768-1769 | Same pattern | AnswerValidator |
| `submitDailyVerbAnswer()` line 1781-1782 | Same pattern | AnswerValidator |
| `submitVocabAnswer()` line 2123-2126 | Same pattern (with `+` split) | AnswerValidator |

After migration, all 4 call `AnswerValidator.validate()`.

### 7.4 Sound Method Deduplication

| Duplicate Set | Method A | Method B | Merge Target |
|---------------|----------|----------|-------------|
| Success | `playSuccessTone()` (line 2952) | `playSuccessSound()` (line 1801) | `AudioCoordinator.playSuccessSound()` |
| Error | `playErrorTone()` (line 2958) | `playErrorSound()` (line 1807) | `AudioCoordinator.playErrorSound()` |

### 7.5 Reset Block Consolidation

The 6 nearly-identical reset blocks should be replaced with a single `resetSessionState()` helper that resets all session-related fields. The ViewModel calls this and then applies its domain-specific overrides (e.g., `selectLanguage` also sets `languages`, `selectedLanguageId`, etc.).

```kotlin
// Proposed: single reset helper
private fun TrainingUiState.resetSessionState() = copy(
    currentIndex = 0,
    correctCount = 0,
    // ... all 30+ reset fields
)
```

Each reset site then becomes:
```kotlin
_uiState.update { it.resetSessionState().copy(selectedLanguageId = languageId, lessons = lessons) }
```
