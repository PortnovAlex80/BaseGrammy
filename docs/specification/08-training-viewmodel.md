# 8. TrainingViewModel — Specification

## 8.1 Architecture Overview

TrainingViewModel is the sole AndroidViewModel in GrammarMate, following a strict single-ViewModel pattern (Level B architectural constraint). It is the brain of the entire application: every piece of training business logic, session management, answer validation, mastery tracking, boss battles, daily practice, vocabulary sprints, TTS/ASR orchestration, and progress persistence flows through this class.

**Why single ViewModel:** The app deliberately avoids multi-ViewModel architectures. All UI screens (`GrammarMateApp.kt` and per-screen composables) are stateless renderers that collect `StateFlow<TrainingUiState>` and dispatch actions via public methods. This eliminates cross-ViewModel synchronization bugs and ensures a single source of truth.

**Class hierarchy:**
```
AndroidViewModel (Jetpack)
  └── TrainingViewModel(application: Application)
        ├── StateFlow<TrainingUiState>  (sole state holder)
        ├── ~15 data store instances    (data layer)
        ├── DailySessionHelper           (domain helper)
        └── private mutable state vars   (session buffers)
```

**Data layer connections (stores initialized in constructor):**

| Store | Purpose |
|-------|---------|
| `LessonStore` | Lesson pack import, seed data, language management, pack-scoped drill file queries |
| `ProgressStore` | Training session position/state persistence (YAML) |
| `AppConfigStore` | Runtime config flags (testMode, eliteSizeMultiplier, vocabSprintLimit) |
| `MasteryStore` | Per-lesson card show tracking (uniqueCardShows, shownCardIds) |
| `StreakStore` | Daily streak tracking per language |
| `BadSentenceStore` | User-flagged bad sentences per pack |
| `HiddenCardStore` | User-hidden cards |
| `DrillProgressStore` | Drill mode card position |
| `VocabProgressStore` | Vocab sprint SRS tracking |
| `WordMasteryStore` | Vocab drill word mastery (pack-scoped) |
| `BackupManager` | Backup/restore to Downloads/BaseGrammy/ |
| `ProfileStore` | User profile (userName) |
| `TtsModelManager` / `TtsEngine` | Sherpa-ONNX TTS (offline) |
| `AsrModelManager` / `AsrEngine` | Sherpa-ONNX ASR (offline speech recognition) |

**Sound system:** A `SoundPool` with two loaded sounds (`voicy_correct_answer`, `voicy_bad_answer`) for answer feedback. Loaded asynchronously; sounds only play after `onLoadCompleteListener` confirms readiness.

---

## 8.2 State Management

### 8.2.1 TrainingUiState Structure

`TrainingUiState` is a data class defined at the bottom of TrainingViewModel.kt (line ~3490). It contains 70+ fields organized by domain:

| Domain | Key Fields |
|--------|-----------|
| **Language & Packs** | `languages`, `installedPacks`, `selectedLanguageId`, `activePackId`, `activePackLessonIds` |
| **Lessons** | `lessons`, `selectedLessonId` |
| **Session Core** | `mode`, `sessionState`, `currentIndex`, `currentCard`, `inputText`, `correctCount`, `incorrectCount`, `incorrectAttemptsForCard` |
| **Timer & Metrics** | `activeTimeMs`, `voiceActiveMs`, `voiceWordCount`, `hintCount`, `voicePromptStartMs`, `lastRating` |
| **Answer** | `answerText`, `lastResult`, `inputMode`, `voiceTriggerToken` |
| **Sub-lessons** | `subLessonTotal`, `subLessonCount`, `subLessonTypes`, `activeSubLessonIndex`, `completedSubLessonCount`, `subLessonFinishedToken` |
| **Story** | `storyCheckInDone`, `storyCheckOutDone`, `activeStory`, `storyErrorMessage` |
| **Vocab Sprint** | `currentVocab`, `vocabInputText`, `vocabAttempts`, `vocabAnswerText`, `vocabIndex`, `vocabTotal`, `vocabWordBankWords`, `vocabFinishedToken`, `vocabErrorMessage`, `vocabInputMode`, `vocabVoiceTriggerToken` |
| **Boss** | `bossActive`, `bossType`, `bossTotal`, `bossProgress`, `bossReward`, `bossRewardMessage`, `bossFinishedToken`, `bossLastType`, `bossErrorMessage`, `bossLessonRewards`, `bossMegaRewards` |
| **Elite** | `eliteActive`, `eliteStepIndex`, `eliteBestSpeeds`, `eliteFinishedToken`, `eliteUnlocked`, `eliteSizeMultiplier`, `vocabSprintLimit` |
| **Flowers & Mastery** | `lessonFlowers`, `currentLessonFlower`, `currentLessonShownCount` |
| **Word Bank** | `wordBankWords`, `selectedWords` |
| **Streak** | `currentStreak`, `longestStreak`, `streakMessage`, `streakCelebrationToken` |
| **User Profile** | `userName` |
| **Ladder** | `ladderRows` (list of `LessonLadderRow`) |
| **TTS** | `ttsState`, `ttsDownloadState`, `ttsModelReady`, `ttsMeteredNetwork`, `bgTtsDownloading`, `bgTtsDownloadStates`, `ttsModelsReady`, `ttsSpeed`, `ruTextScale` |
| **ASR** | `useOfflineAsr`, `asrState`, `asrModelReady`, `asrDownloadState`, `asrMeteredNetwork`, `asrErrorMessage`, `audioPermissionDenied` |
| **Bad Sentences** | `badSentenceCount` |
| **Drill** | `isDrillMode`, `drillCardIndex`, `drillTotalCards`, `drillShowStartDialog`, `drillHasProgress` |
| **Navigation** | `initialScreen`, `currentScreen`, `testMode` |
| **Vocab Mastery** | `vocabMasteredCount` |
| **Daily Practice** | `dailySession: DailySessionState`, `dailyCursor: DailyCursorState` |

### 8.2.2 State Update Pattern

All state mutations go through `_uiState.update { it.copy(...) }`. This is an atomic snapshot update on `MutableStateFlow`. The ViewModel never exposes `_uiState` directly; the public `uiState: StateFlow<TrainingUiState>` is read-only.

**Token-based events:** Many UI events use incrementing integer tokens rather than booleans to avoid stale-event problems:
- `subLessonFinishedToken` — incremented when a sub-lesson completes
- `vocabFinishedToken` — incremented when vocab sprint finishes
- `bossFinishedToken` — incremented when boss battle ends
- `eliteFinishedToken` — incremented when elite step completes
- `voiceTriggerToken` — incremented to trigger voice input prompt
- `vocabVoiceTriggerToken` — voice trigger for vocab mode
- `streakCelebrationToken` — incremented for streak celebration dialog

The UI observes these tokens and fires one-shot side effects (dialogs, animations) when they change.

### 8.2.3 State Persistence (`saveProgress()`)

The private `saveProgress()` method serializes a `TrainingProgress` data class to disk via `ProgressStore`:

```kotlin
progressStore.save(TrainingProgress(
    languageId, mode, lessonId, currentIndex, correctCount, incorrectCount,
    incorrectAttemptsForCard, activeTimeMs, state, bossLessonRewards,
    bossMegaRewards, voiceActiveMs, voiceWordCount, hintCount,
    eliteStepIndex, eliteBestSpeeds, currentScreen, activePackId,
    dailyLevel, dailyTaskIndex, dailyCursor
))
```

**When progress is saved:**
- After every answer submission (`submitAnswer()`)
- After every card navigation (`nextCard()`, `prevCard()`)
- After session state changes (`togglePause()`, `startSession()`)
- After lesson/language/pack selection
- After boss/elite mode transitions
- Timer saves every 500ms tick

**Skip conditions:** `saveProgress()` is skipped when `bossActive == true && bossType != BossType.ELITE` (boss sessions are not persisted mid-battle).

**Force backup:** A `forceBackupOnSave` flag triggers `createProgressBackup()` on the next save after sub-lesson completion, story completion, or vocab sprint completion.

---

## 8.3 Session Lifecycle

### 8.3.1 Initialization (`init` block)

The init block runs these steps in order:

1. **Sound pool setup** — loads success/error sounds, sets load-complete listener
2. **Seed data** — `lessonStore.ensureSeedData()`
3. **Migration** — `badSentenceStore.migrateIfNeeded(lessonStore)`
4. **State restoration** — loads `ProgressStore`, `AppConfigStore`, `ProfileStore`
5. **Boss reward parsing** — converts stored string values to `BossReward` enum
6. **Language/lesson resolution** — validates saved IDs against available data, falls back to first available
7. **Pack resolution** — resolves `activePackId` from saved value, lesson derivation, or first pack for language
8. **UI state initialization** — massive `copy()` call setting all fields
9. **Schedule rebuild** — `rebuildSchedules(lessons)`
10. **Session card build** — `buildSessionCards()`
11. **Flower refresh** — `refreshFlowerStates()`
12. **Active session resume** — if state was `ACTIVE` with a current card, resumes timer and records mastery
13. **Background pack reload** — `viewModelScope.launch(Dispatchers.IO)` to force-reload default packs, then updates UI on Main thread
14. **TTS/ASR initialization** — checks model readiness, starts background TTS downloads
15. **TTS state collection** — collects `ttsEngine.state` flow into UI state
16. **Daily session pre-build** — on IO thread, builds daily practice session for faster start

### 8.3.2 Session Start (`startSession()`)

Called from `togglePause()` when resuming, or from `resumeFromSettings()`.

1. If not in boss or elite mode, rebuilds session cards
2. If no cards or no current card, pauses and returns
3. Pauses any existing timer, then starts a new one
4. Sets `sessionState = ACTIVE`, clears input, triggers voice if in VOICE mode
5. Records mastery for the current card
6. Saves progress

### 8.3.3 Session Progression

**Card advancement flow:**

```
submitAnswer()
  |-- accepted?
  |     |-- YES + boss + last card --> updateBossProgress(), finishBoss()
  |     |-- YES + elite + last card --> calculate speed, save best, next step, eliteFinishedToken++
  |     |-- YES + drill mode --> increment counters, advanceDrillCard()
  |     |-- YES + normal last card --> pause timer, advance sub-lesson, buildSessionCards(),
  |     |                           checkAndMarkLessonCompleted(), refreshFlowerStates(),
  |     |                           updateStreak(), forceBackupOnSave
  |     |-- YES + not last card --> increment counters, nextCard(triggerVoice)
  |     |-- NO --> playErrorTone, increment incorrect
  |           |-- attempts >= 3? --> show hint (all accepted answers), pause timer
  |           |-- otherwise --> allow retry
```

**Sub-lesson transition (last card of sub-lesson):**
1. Pause timer
2. Calculate completed sub-lessons from mastery data
3. Advance `completedSubLessonCount` and `activeSubLessonIndex`
4. Increment `subLessonFinishedToken`
5. Mark shown cards via `markSubLessonCardsShown()`
6. Rebuild session cards for the next sub-lesson
7. Check lesson completion (15 sub-lessons)
8. Refresh flower states
9. Update streak
10. Trigger backup on next save

### 8.3.4 Session End

**Normal end (`finishSession()`):**
- For elite mode: delegates to `cancelEliteSession()`
- For boss mode: delegates to `finishBoss()`
- Otherwise: pauses timer, calculates `lastRating` (correctCount / minutes), resets to PAUSED with first card, saves progress

**Pause (`pauseSession()` / `togglePause()`):**
- Pauses timer
- Sets `sessionState = PAUSED`
- Clears `voicePromptStartMs`
- Saves progress

---

## 8.4 Answer Validation

### 8.4.1 Normalization Pipeline

All answer comparison uses `Normalizer.normalize()`:
```kotlin
val normalizedInput = Normalizer.normalize(state.inputText)
val accepted = card.acceptedAnswers.any { Normalizer.normalize(it) == normalizedInput }
```

In test mode, all answers are accepted regardless of input.

### 8.4.2 Correct Answer Handling

On correct answer:
1. Play success tone
2. Record card show for mastery tracking
3. Update counters: `correctCount++`, `incorrectAttemptsForCard = 0`
4. Clear answer display state
5. Track voice metrics if applicable (duration, word count)
6. Navigate based on session mode (see Session Progression above)
7. Save progress
8. Refresh flower states

### 8.4.3 Incorrect Answer Handling

On incorrect answer:
1. Play error tone
2. `incorrectCount++`, `incorrectAttemptsForCard++`
3. `lastResult = false`
4. **After 3 attempts:** show all accepted answers as hint (`answerText`), pause timer, reset `incorrectAttemptsForCard` to 0
5. **Before 3 attempts:** allow retry, auto-trigger voice if in VOICE mode

### 8.4.4 Multiple Accepted Answers

Cards have an `acceptedAnswers: List<String>` field. During validation, the input is compared against each accepted answer after normalization. When showing hints (3 incorrect attempts), all accepted answers are displayed joined with " / ".

### 8.4.5 SubmitResult

`submitAnswer()` returns a `SubmitResult(accepted: Boolean, hintShown: Boolean)` data class, allowing the UI to react to the answer outcome.

---

## 8.5 Training Modes

### 8.5.1 LESSON Mode (Normal Training)

**Purpose:** The primary training mode. Users work through a selected lesson's cards in sub-lessons of ~10 cards each.

**Trigger:** `selectLesson(lessonId)` or `selectMode(TrainingMode.LESSON)`

**Session flow:**
1. `rebuildSchedules()` creates sub-lesson schedule via `MixedReviewScheduler`
2. `buildSessionCards()` selects cards for the current sub-lesson based on `activeSubLessonIndex`
3. Hidden cards are filtered out via `HiddenCardStore`
4. Sub-lesson types are stored in `subLessonTypes: List<SubLessonType>` (NEW_ONLY or MIXED)
5. User answers each card; on last card, sub-lesson completion is triggered

**Sub-lesson index calculation:**
- `activeSubLessonIndex` starts at the first incomplete sub-lesson based on mastery data
- `calculateCompletedSubLessons()` walks sub-lessons in order and counts how many have ALL cards shown
- The active index never moves backward (uses `maxOf` to preserve progress)

**Lesson completion condition:** After 15 completed sub-lessons, the lesson is marked as completed via `masteryStore.markLessonCompleted()`.

### 8.5.2 ALL_SEQUENTIAL Mode

**Purpose:** Train through all cards from all lessons in sequence.

**Session flow:**
- Cards from all lessons are concatenated, hidden cards filtered
- Split into sub-lesson blocks of `subLessonSize` (default ~10)
- Navigate through blocks sequentially

### 8.5.3 ALL_MIXED Mode (Review)

**Purpose:** Random review across all cards.

**Session flow:**
- All cards from all lessons are collected, hidden cards filtered, then shuffled
- Limited to 300 cards max
- Split into sub-lesson blocks

### 8.5.4 BOSS Mode

**Purpose:** End-of-lesson challenge testing pattern stability under pressure. Three boss types exist.

**Trigger:** `startBossLesson()`, `startBossMega()`, `startBossElite()`

**Unlock guard:** Requires at least 15 completed sub-lessons (unless test mode or ELITE type).

**Card selection by type:**
| Boss Type | Card Source | Max Cards |
|-----------|-------------|-----------|
| LESSON | Current lesson cards, shuffled | 300 |
| MEGA | All cards from lessons up to current (exclusive), shuffled | 300 |
| ELITE | All cards from all lessons, shuffled | `eliteSubLessonSize() * eliteStepCount` |

**Boss session setup:**
- Sets `bossActive = true`, resets counters, creates single sub-lesson
- `sessionCards = bossCards`, first card loaded

**Boss progression:**
- `bossProgress` tracks how far through the battle the user has gotten
- On each correct answer (not last card): `nextCard()` with boss progress update
- On last correct answer: `updateBossProgress()`, `finishBoss()`

**Reward system:**
```kotlin
fun resolveBossReward(progress: Int, total: Int): BossReward? {
    val percent = (progress.toDouble() / total) * 100.0
    return when {
        percent >= 100.0 -> GOLD
        percent > 75.0   -> SILVER
        percent > 50.0   -> BRONZE
        else             -> null
    }
}
```

**Mid-boss reward milestones:** When boss progress crosses a reward threshold (50%, 75%, 100%), the session pauses, shows a reward message, and resumes after user dismissal.

**Boss finish (`finishBoss()`):**
1. Resolve final reward
2. Update `bossLessonRewards` (LESSON type) or `bossMegaRewards` (MEGA type) maps
3. Clear boss state, restore previous lesson/session state from ProgressStore
4. Rebuild session cards, save progress, refresh flowers

### 8.5.5 ELITE Mode

**Purpose:** Speed challenge measuring words-per-minute across multiple steps.

**Trigger:** `openEliteStep(index)`

**Unlock condition:** `eliteUnlocked = testMode || lessons.size >= 12`

**Session flow:**
1. `buildEliteCards()` — all lesson cards shuffled, take `ceil(subLessonSize * 1.25)` cards
2. Elite uses `eliteStepCount` steps (from `TrainingConfig.ELITE_STEP_COUNT`)
3. User answers all cards in the step
4. On completion: calculates speed (`voiceWordCount / voiceActiveMs * 60000`)
5. Compares to `eliteBestSpeeds[stepIndex]`, stores if better
6. Advances to next step (`(stepIndex + 1) % eliteStepCount`)
7. Sets `eliteFinishedToken++`

**Cancel:** `cancelEliteSession()` resets to PAUSED, clears elite state.

### 8.5.6 DAILY PRACTICE Mode

**Purpose:** Unified daily session with 3 blocks (10 translations, 10 vocab flashcards, 10 verb conjugations). Replaces former Elite mode and Vocab Sprint.

**Session composition (by `DailySessionComposer`):**
- **Block 1 (TRANSLATE):** Cursor-driven sentence selection from current lesson. Takes next 10 cards in order (no shuffle). Input modes rotate: VOICE, KEYBOARD, WORD_BANK (repeating).
- **Block 2 (VOCAB):** Pure SRS selection across full word list. Most-overdue first, then new words, then least-recently-reviewed fallback. Direction alternates IT_TO_RU / RU_TO_IT.
- **Block 3 (VERBS):** Weak-first verb drill. Excludes previously shown cards. Scores by weakness (1 - shownInCombo/totalInCombo). Alternates KEYBOARD / WORD_BANK input modes.

**Cursor management:**
- `DailyCursorState` tracks: `sentenceOffset`, `currentLessonIndex`, `firstSessionDate`, first-session card IDs
- Cursor advances ONLY when full session completes with all VOICE/KEYBOARD answers
- WORD_BANK answers do NOT count toward cursor advancement
- First session of the day stores card IDs for Repeat functionality

**Start flow:**
1. `startDailyPractice(lessonLevel)` — tries pre-built session first, falls back to synchronous build
2. Saves cursor at session start for rollback on cancel
3. Resets per-block answered counters
4. Delegates to `dailySessionHelper.startDailySession(tasks, lessonLevel)`

**Repeat flow (`repeatDailyPractice()`):**
1. Try in-memory `lastDailyTasks` cache (fastest)
2. Try reconstructing from `firstSessionSentenceCardIds` / `firstSessionVerbCardIds`
3. Last resort: build fresh with cursor at offset 0
4. Does NOT advance cursor

**Task/block advancement:**
- `advanceDailyTask()` — moves to next task within session
- `advanceDailyBlock()` — skips to first task of next block type
- `repeatDailyBlock()` — rebuilds and replaces current block with fresh cards

**Session completion (`cancelDailySession()`):**
- If `finishedToken == true` and all TRANSLATE + VERBS cards were answered via VOICE/KEYBOARD: advance cursor
- Otherwise: cursor stays, enabling re-entry with same cards

**Verb progress persistence:** `persistDailyVerbProgress()` records each verb card as shown in `VerbDrillStore` when advancing.

**Vocab SRS rating:** `rateVocabCard(rating)` applies spaced repetition:
- 0 (Again): reset to step 0
- 1 (Hard): stay at current step
- 2 (Good): step +1
- 3 (Easy): step +2
- Learned threshold: step index >= 3

### 8.5.7 DRILL Mode

**Purpose:** Seamless card training without mastery/flower progress. All drill cards in one continuous stream. Position is saved on exit.

**Trigger:** `startDrill(resume: Boolean)`

**Session flow:**
1. Get drill cards from selected lesson
2. If resuming, start from saved position; otherwise start at 0
3. Each card loaded individually via `loadDrillCard(index)`
4. On correct answer: seamless advance via `advanceDrillCard()`
5. On completion: `finishDrill()` clears progress and returns to normal mode

**Key difference from normal mode:** `recordCardShowForMastery()` returns early when `isDrillMode == true`. No mastery tracking, no flower growth.

### 8.5.8 VOCAB SPRINT Mode

**Purpose:** Vocabulary sprint through lesson vocab entries with SRS prioritization.

**Trigger:** `openVocabSprint(resume: Boolean)`

**Card selection:**
1. Load all vocab entries for lesson + language
2. Sort via `vocabProgressStore.sortEntriesForSprint()` (SRS prioritization: overdue, new, not due)
3. Apply `vocabSprintLimit` cap
4. If resuming, filter out completed indices; if all done, restart fresh

**Answer handling:**
- Correct: `vocabProgressStore.recordCorrect()`, `addCompletedIndex()`, move to next
- Incorrect after 3 attempts: show answer, reset
- Incorrect before 3: allow retry, auto-trigger voice

**Completion:** When all entries answered, clear sprint progress, increment `vocabFinishedToken`, force backup.

---

## 8.6 Word Bank

### 8.6.1 Word Bank Generation Algorithm

**Sentence training word bank (`updateWordBank()`):**

1. Take `card.acceptedAnswers.first()` as the correct answer
2. Split into individual words, filter blanks, normalize
3. Build distractor pool from all lesson cards:
   - Filter out the current card
   - Split all other cards' accepted answers into words
   - Filter: length >= 3, not matching any normalized correct word
   - Take distinct values
4. Select 3 random distractors from the pool
5. Call `generateWordBank(correctAnswer, extraWords)`:
   - Split correct answer into words
   - Add extra words (if not already in correct words)
   - Shuffle the combined list

**Vocab sprint word bank (`buildVocabWordBank()`):**

1. Extract first accepted variant from `entry.targetText` (split by "+")
2. Build distractor pool from current vocab session entries (split by "+", distinct)
3. Take up to 4 distractors from session pool
4. If not enough, supplement from all lessons' vocab entries
5. Result: 1 correct + up to 4 distractors, shuffled

### 8.6.2 Target Word Selection and Distractors

For sentence training:
- Target words = all words in `acceptedAnswers[0]`
- Distractors = 3 random words (length >= 3) from other cards' accepted answers, excluding words matching the correct answer (normalized)

For vocab:
- Target = first variant of `targetText` (split by "+")
- Distractors = up to 4 entries from session pool, supplemented from all lessons if needed

### 8.6.3 Word Bank UI Interaction Model

- `selectWordFromBank(word)` — appends word to `selectedWords` list and builds `inputText` by joining with spaces
- `removeLastSelectedWord()` — removes last word from `selectedWords`, rebuilds `inputText`
- `inputText` is the space-joined representation of `selectedWords`, used by `submitAnswer()`

**Mode switching:** When user switches to `InputMode.WORD_BANK`, `updateWordBank()` is called automatically. Switching away preserves the word bank state.

---

## 8.7 Mastery System Integration

### 8.7.1 How Mastery Is Tracked During Training

The `recordCardShowForMastery(card)` method is the sole entry point for mastery tracking:

```kotlin
private fun recordCardShowForMastery(card: SentenceCard) {
    if (_uiState.value.isDrillMode) return           // No mastery in drill mode
    val lessonId = resolveCardLessonId(card)
    val languageId = _uiState.value.selectedLanguageId
    if (_uiState.value.inputMode == InputMode.WORD_BANK) return  // WORD_BANK doesn't count
    masteryStore.recordCardShow(lessonId, languageId, card.id)
}
```

**When it is called:**
- On `submitAnswer()` when answer is correct (line 633)
- On `nextCard()` when a new card is loaded (line 886)
- On `prevCard()` when navigating back (line 913)
- On `startSession()` when resuming (line 2422)
- On init when restoring an active session (line 265)

**Card-to-lesson resolution (`resolveCardLessonId()`):**
- First checks if the current `selectedLessonId` contains the card
- Falls back to searching all lessons for the card
- Returns "unknown" if not found anywhere

### 8.7.2 When Mastery Updates Are Persisted

Mastery is persisted immediately on each `masteryStore.recordCardShow()` call. The store writes to YAML files atomically.

**Additional mastery operations:**
- `markSubLessonCardsShown()` — after sub-lesson completion, marks all cards as shown for progress tracking (WORD_BANK mode path)
- `checkAndMarkLessonCompleted()` — after 15 sub-lessons, marks lesson as completed in MasteryStore

### 8.7.3 WORD_BANK vs VOICE vs KEYBOARD Distinction

This is a critical business rule (Level A):

| Input Mode | Counts for Mastery | Grows Flowers | Advances Daily Cursor |
|------------|-------------------|---------------|----------------------|
| VOICE | YES | YES | YES |
| KEYBOARD | YES | YES | YES |
| WORD_BANK | NO | NO | NO |

The distinction is enforced in:
- `recordCardShowForMastery()` — early return for WORD_BANK
- `recordDailyCardPracticed()` — only called for non-WORD_BANK modes
- `cancelDailySession()` — cursor advances only if all TRANSLATE and VERBS cards were practiced via VOICE/KEYBOARD

---

## 8.8 Public API

### 8.8.1 Session Management

| Method | Signature | Purpose |
|--------|-----------|---------|
| `togglePause()` | `fun togglePause()` | Toggle between ACTIVE and PAUSED session states |
| `pauseSession()` | `fun pauseSession()` | Force pause the active session |
| `finishSession()` | `fun finishSession()` | End current session, calculate rating |
| `resumeFromSettings()` | `fun resumeFromSettings()` | Resume session when returning from Settings |
| `startSession()` | `private fun startSession()` | Internal: activate session, start timer |

### 8.8.2 Answer & Input

| Method | Signature | Purpose |
|--------|-----------|---------|
| `onInputChanged(text)` | `fun onInputChanged(text: String)` | Update input text; resets attempts if previous answer was shown |
| `submitAnswer()` | `fun submitAnswer(): SubmitResult` | Validate and process current answer |
| `showAnswer()` | `fun showAnswer()` | Manually reveal the answer (hint) |
| `nextCard(triggerVoice)` | `fun nextCard(triggerVoice: Boolean)` | Advance to next card |
| `prevCard()` | `fun prevCard()` | Go back to previous card |
| `setInputMode(mode)` | `fun setInputMode(mode: InputMode)` | Switch input mode; auto-updates word bank |
| `onVoicePromptStarted()` | `fun onVoicePromptStarted()` | Record voice prompt start timestamp |

### 8.8.3 Language, Lesson, Pack Selection

| Method | Signature | Purpose |
|--------|-----------|---------|
| `selectLanguage(languageId)` | `fun selectLanguage(languageId: String)` | Switch active language; full state reset |
| `selectLesson(lessonId)` | `fun selectLesson(lessonId: String)` | Switch active lesson; resolves pack, rebuilds schedule |
| `selectPack(packId)` | `fun selectPack(packId: String)` | Switch active pack; delegates to selectLesson or sets pack directly for drill-only packs |
| `selectMode(mode)` | `fun selectMode(mode: TrainingMode)` | Switch training mode; partial state reset |
| `selectSubLesson(index)` | `fun selectSubLesson(index: Int)` | Jump to a specific sub-lesson |

### 8.8.4 Lesson Content Management

| Method | Signature | Purpose |
|--------|-----------|---------|
| `importLesson(uri)` | `fun importLesson(uri: Uri)` | Import a single lesson file |
| `importLessonPack(uri)` | `fun importLessonPack(uri: Uri)` | Import a lesson pack ZIP |
| `resetAndImportLesson(uri)` | `fun resetAndImportLesson(uri: Uri)` | Delete all lessons, then import |
| `deleteLesson(lessonId)` | `fun deleteLesson(lessonId: String)` | Delete a single lesson |
| `createEmptyLesson(title)` | `fun createEmptyLesson(title: String)` | Create a new empty lesson |
| `addLanguage(name)` | `fun addLanguage(name: String)` | Add a new language |
| `deleteAllLessons()` | `fun deleteAllLessons()` | Delete all lessons for current language |
| `deletePack(packId)` | `fun deletePack(packId: String)` | Delete an entire pack and its data |
| `resetAllProgress()` | `fun resetAllProgress()` | Clear all progress: mastery, daily, verb drill, vocab, training |

### 8.8.5 Boss Battles

| Method | Signature | Purpose |
|--------|-----------|---------|
| `startBossLesson()` | `fun startBossLesson()` | Start LESSON boss |
| `startBossMega()` | `fun startBossMega()` | Start MEGA boss |
| `startBossElite()` | `fun startBossElite()` | Start ELITE boss |
| `finishBoss()` | `fun finishBoss()` | End boss session, record rewards |
| `clearBossRewardMessage()` | `fun clearBossRewardMessage()` | Dismiss reward popup, resume session |
| `clearBossError()` | `fun clearBossError()` | Dismiss boss error message |

### 8.8.6 Elite Mode

| Method | Signature | Purpose |
|--------|-----------|---------|
| `openEliteStep(index)` | `fun openEliteStep(index: Int)` | Start an elite step |
| `cancelEliteSession()` | `fun cancelEliteSession()` | Cancel active elite session |

### 8.8.7 Daily Practice

| Method | Signature | Purpose |
|--------|-----------|---------|
| `hasResumableDailySession()` | `fun hasResumableDailySession(): Boolean` | Check if today's first session can be repeated |
| `startDailyPractice(lessonLevel)` | `fun startDailyPractice(lessonLevel: Int): Boolean` | Start new daily session |
| `repeatDailyPractice(lessonLevel)` | `fun repeatDailyPractice(lessonLevel: Int): Boolean` | Repeat today's first session |
| `advanceDailyTask()` | `fun advanceDailyTask(): Boolean` | Advance to next task in daily session |
| `advanceDailyBlock()` | `fun advanceDailyBlock(): Boolean` | Skip to next block in daily session |
| `repeatDailyBlock()` | `fun repeatDailyBlock(): Boolean` | Rebuild and restart current block |
| `cancelDailySession()` | `fun cancelDailySession()` | End daily session; conditionally advance cursor |
| `recordDailyCardPracticed(blockType)` | `fun recordDailyCardPracticed(blockType: DailyBlockType)` | Track VOICE/KEYBOARD practice per block |
| `rateVocabCard(rating)` | `fun rateVocabCard(rating: Int)` | Rate a daily vocab card (0=Again, 1=Hard, 2=Good, 3=Easy) |
| `persistDailyVerbProgress(card)` | `fun persistDailyVerbProgress(card: VerbDrillCard)` | Persist verb card as shown |
| `submitDailySentenceAnswer(input)` | `fun submitDailySentenceAnswer(input: String): Boolean` | Validate daily sentence answer |
| `submitDailyVerbAnswer(input)` | `fun submitDailyVerbAnswer(input: String): Boolean` | Validate daily verb answer |
| `getDailyCurrentTask()` | `fun getDailyCurrentTask(): DailyTask?` | Get current daily task |
| `getDailyBlockProgress()` | `fun getDailyBlockProgress(): BlockProgress` | Get progress within current block |
| `getDailySentenceAnswer()` | `fun getDailySentenceAnswer(): String?` | Get accepted answer for current sentence task |
| `getDailyVerbAnswer()` | `fun getDailyVerbAnswer(): String?` | Get accepted answer for current verb task |

### 8.8.8 Drill Mode

| Method | Signature | Purpose |
|--------|-----------|---------|
| `showDrillStartDialog(lessonId)` | `fun showDrillStartDialog(lessonId: String)` | Show drill start dialog |
| `startDrill(resume)` | `fun startDrill(resume: Boolean)` | Start or resume drill |
| `dismissDrillDialog()` | `fun dismissDrillDialog()` | Close drill start dialog |
| `advanceDrillCard()` | `fun advanceDrillCard()` | Move to next drill card |
| `exitDrillMode()` | `fun exitDrillMode()` | Exit drill, saving position |

### 8.8.9 Vocab Sprint

| Method | Signature | Purpose |
|--------|-----------|---------|
| `hasVocabProgress()` | `fun hasVocabProgress(): Boolean` | Check if sprint has in-progress data |
| `openVocabSprint(resume)` | `fun openVocabSprint(resume: Boolean)` | Start or resume vocab sprint |
| `onVocabInputChanged(text)` | `fun onVocabInputChanged(text: String)` | Update vocab input text |
| `setVocabInputMode(mode)` | `fun setVocabInputMode(mode: InputMode)` | Switch vocab input mode |
| `requestVocabVoice()` | `fun requestVocabVoice()` | Trigger voice input for vocab |
| `submitVocabAnswer(inputOverride)` | `fun submitVocabAnswer(inputOverride: String? = null)` | Validate vocab answer |
| `showVocabAnswer()` | `fun showVocabAnswer()` | Reveal vocab answer |
| `clearVocabError()` | `fun clearVocabError()` | Dismiss vocab error |

### 8.8.10 Story

| Method | Signature | Purpose |
|--------|-----------|---------|
| `openStory(phase)` | `fun openStory(phase: StoryPhase)` | Load and display a story quiz |
| `completeStory(phase, allCorrect)` | `fun completeStory(phase: StoryPhase, allCorrect: Boolean)` | Mark story phase complete |
| `clearStoryError()` | `fun clearStoryError()` | Dismiss story error |

### 8.8.11 Word Bank

| Method | Signature | Purpose |
|--------|-----------|---------|
| `selectWordFromBank(word)` | `fun selectWordFromBank(word: String)` | Append word to selected words |
| `removeLastSelectedWord()` | `fun removeLastSelectedWord()` | Remove last selected word |

### 8.8.12 TTS & ASR

| Method | Signature | Purpose |
|--------|-----------|---------|
| `onTtsSpeak(text, speed)` | `fun onTtsSpeak(text: String, speed: Float? = null)` | Speak text via TTS |
| `setTtsSpeed(speed)` | `fun setTtsSpeed(speed: Float)` | Set TTS speed (0.5-1.5) |
| `setRuTextScale(scale)` | `fun setRuTextScale(scale: Float)` | Set Russian text scale (1.0-2.0) |
| `startTtsDownload()` | `fun startTtsDownload()` | Start TTS model download (with metered check) |
| `confirmTtsDownloadOnMetered()` | `fun confirmTtsDownloadOnMetered()` | Proceed with download on metered network |
| `dismissMeteredWarning()` | `fun dismissMeteredWarning()` | Dismiss metered network warning |
| `dismissTtsDownloadDialog()` | `fun dismissTtsDownloadDialog()` | Close download dialog after Done/Error |
| `startTtsDownloadForLanguage(id)` | `fun startTtsDownloadForLanguage(languageId: String)` | Download TTS for specific language |
| `stopTts()` | `fun stopTts()` | Stop current TTS playback |
| `startOfflineRecognition()` | `fun startOfflineRecognition()` | Start ASR recording + transcription |
| `stopAsr()` | `fun stopAsr()` | Stop ASR recording |
| `setUseOfflineAsr(enabled)` | `fun setUseOfflineAsr(enabled: Boolean)` | Toggle offline ASR, save config |
| `startAsrDownload()` | `fun startAsrDownload()` | Download ASR model (with metered check) |
| `confirmAsrDownloadOnMetered()` | `fun confirmAsrDownloadOnMetered()` | Proceed with ASR download on metered |
| `dismissAsrMeteredWarning()` | `fun dismissAsrMeteredWarning()` | Dismiss ASR metered warning |
| `dismissAsrDownloadDialog()` | `fun dismissAsrDownloadDialog()` | Close ASR download dialog |
| `checkAsrModel()` | `fun checkAsrModel()` | Check ASR model readiness |
| `setTtsDownloadStateFromBackground(bgState)` | `fun setTtsDownloadStateFromBackground(bgState: DownloadState)` | Mirror background download to dialog state |

### 8.8.13 Configuration & Profile

| Method | Signature | Purpose |
|--------|-----------|---------|
| `toggleTestMode()` | `fun toggleTestMode()` | Toggle test mode (accept all answers) |
| `updateVocabSprintLimit(limit)` | `fun updateVocabSprintLimit(limit: Int)` | Set vocab sprint size limit |
| `updateUserName(newName)` | `fun updateUserName(newName: String)` | Update user profile name (max 50 chars) |
| `saveProgressNow()` | `fun saveProgressNow()` | Manual save + trigger backup |
| `onScreenChanged(screenName)` | `fun onScreenChanged(screenName: String)` | Track current screen for state restoration |

### 8.8.14 Bad Sentences & Hidden Cards

| Method | Signature | Purpose |
|--------|-----------|---------|
| `flagBadSentence()` | `fun flagBadSentence()` | Flag current card as bad sentence |
| `unflagBadSentence()` | `fun unflagBadSentence()` | Unflag current card |
| `isBadSentence()` | `fun isBadSentence(): Boolean` | Check if current card is flagged |
| `exportBadSentences()` | `fun exportBadSentences(): String?` | Export bad sentences to text file |
| `hideCurrentCard()` | `fun hideCurrentCard()` | Hide current card and skip to next |
| `unhideCurrentCard()` | `fun unhideCurrentCard()` | Unhide current card |
| `isCurrentCardHidden()` | `fun isCurrentCardHidden(): Boolean` | Check if current card is hidden |
| `flagDailyBadSentence(...)` | `fun flagDailyBadSentence(cardId, languageId, sentence, translation, mode)` | Flag a daily practice card as bad |
| `unflagDailyBadSentence(...)` | `fun unflagDailyBadSentence(cardId)` | Unflag a daily practice card |
| `isDailyBadSentence(...)` | `fun isDailyBadSentence(cardId): Boolean` | Check if a daily practice card is flagged |
| `exportDailyBadSentences()` | `fun exportDailyBadSentences(): String?` | Export daily practice bad sentences |

---

## 8.12 Bad Card Reporting

### 8.12.1 Overview

Bad card reporting allows users to flag cards with errors, unnatural phrasing, or incorrect translations. The feature is available in all training modes: regular lesson training, daily practice (all 3 blocks), verb drill, and vocab drill.

All flagged cards are stored in a single `BadSentenceStore` and can be exported to a text file for review by content creators.

### 8.12.2 Reporting in Training Mode

In regular lesson training, flagging is accessed via the report button (flag icon) in the `AnswerBox` component, which opens a `ModalBottomSheet` with report options.

**Methods:**

- `flagBadSentence()`: Adds the current card to `BadSentenceStore` with `mode = "training"`. The `sentence` field is set to `card.promptRu`, and `translation` is set to `card.acceptedAnswers.joinToString(" / ")`. After flagging in drill mode, the card is automatically skipped via `advanceDrillCard()`.
- `unflagBadSentence()`: Removes the current card from `BadSentenceStore`.
- `isBadSentence()`: Checks if the current card is flagged.
- `exportBadSentences()`: Calls `BadSentenceStore.exportUnified()` which generates a single text file with all flagged cards grouped by language, pack, and mode. Returns the file path or `null` if no bad sentences exist.

**Mode field:** `"training"` -- used in the `BadSentenceEntry.mode` field to identify the source training mode.

### 8.12.3 Reporting in Daily Practice

Daily practice supports bad card reporting across all 3 blocks. The flagging is handled through the `DailyPracticeSessionProvider` (for Blocks 1 and 3) and the `VocabFlashcardBlock` composable (for Block 2).

**Daily-specific methods in TrainingViewModel:**

- `flagDailyBadSentence(cardId, languageId, sentence, translation, mode)`: Adds the entry to `BadSentenceStore` and also tracks the card ID in an in-memory `dailyBadCardIds` set for fast lookup during the session.
- `unflagDailyBadSentence(cardId)`: Removes the entry from `BadSentenceStore` and the in-memory set.
- `isDailyBadSentence(cardId)`: Checks both the in-memory set and the persistent store.
- `exportDailyBadSentences()`: Calls `BadSentenceStore.exportUnified()`.

**Mode fields by block:**

| Block | Mode Field | Description |
|-------|-----------|-------------|
| Block 1 (Translation) | `"daily_translate"` | Sentence translation cards |
| Block 2 (Vocab Flashcard) | `"daily_vocab"` | Vocabulary flashcard words |
| Block 3 (Verb Conjugation) | `"daily_verb"` | Verb conjugation cards |

The `DailyPracticeSessionProvider` determines the mode from `blockType` when calling `onFlagCard`:

```kotlin
val mode = when (blockType) {
    DailyBlockType.TRANSLATE -> "daily_translate"
    DailyBlockType.VERBS -> "daily_verb"
    DailyBlockType.VOCAB -> "daily_vocab"
}
```

**UI integration:**

- **Blocks 1 and 3**: Report button is in `DailyInputControls`, alongside input mode buttons and the show-answer button. Tapping it opens a `ModalBottomSheet` with flag/unflag/export options.
- **Block 2**: Report button is in `VocabFlashcardBlock`, shown as a flag icon next to the flashcard content. Tapping it opens a separate report bottom sheet.
- **`supportsFlagging = true`** on `DailyPracticeSessionProvider` enables the flagging capability.

**Hidden cards in daily practice**: `hideCurrentCard()` is a no-op in `DailyPracticeSessionProvider` -- flagging is the only action available. Hidden cards do NOT affect session progress.

### 8.12.4 Export Format

The unified export (`BadSentenceStore.exportUnified()`) produces a text file at `Downloads/BaseGrammy/bad_sentences_all.txt` with entries grouped by language, then pack, then mode:

```
=== Bad Sentences Report ===
Generated: 2026-05-12 14:30
Total entries: 5

## Language: it

  ### Pack: italian_basics

    #### Mode: training (2 entries)

    - ID: lesson1_42
      Source: Он работает в офисе
      Target: Lui lavora in ufficio
      Reported: 2026-05-12 14:25

    #### Mode: daily_verb (1 entries)

    - ID: regular_are_Presente_5
      Source: я готов
      Target: Io sono pronto.
      Reported: 2026-05-12 14:28
```

Per-pack export (`exportToTextFile(packId)`) produces a simpler format at `Downloads/BaseGrammy/bad_sentences_{packId}.txt`:

```
ID: lesson1_42
Source: Он работает в офисе
Target: Lui lavora in ufficio
Language: it
Mode: training
---
```

### 8.8.15 Backup & Restore

| Method | Signature | Purpose |
|--------|-----------|---------|
| `createProgressBackup()` | `fun createProgressBackup()` | Create backup in Downloads/BaseGrammy/ |
| `restoreBackup(backupUri)` | `fun restoreBackup(backupUri: Uri)` | Restore progress from backup |
| `reloadFromDisk()` | `fun reloadFromDisk()` | Reload all data from disk (IO thread) |

### 8.8.16 Streak

| Method | Signature | Purpose |
|--------|-----------|---------|
| `dismissStreakMessage()` | `fun dismissStreakMessage()` | Dismiss streak celebration message |

### 8.8.17 Misc

| Method | Signature | Purpose |
|--------|-----------|---------|
| `refreshVocabMasteryCount()` | `fun refreshVocabMasteryCount()` | Update vocab mastered count from store |

---

## 8.9 Helper Integration

### 8.9.1 DailySessionHelper

The primary domain helper, managing daily practice session state within `DailySessionState`.

**Initialization:**
```kotlin
private val dailySessionHelper = DailySessionHelper(object : TrainingStateAccess {
    override val uiState: StateFlow<TrainingUiState> = _uiState
    override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
        _uiState.update(transform)
    }
    override fun saveProgress() = this@TrainingViewModel.saveProgress()
})
```

**Helper methods used by ViewModel:**
| Method | Purpose |
|--------|---------|
| `startDailySession(tasks, level)` | Initialize DailySessionState with tasks |
| `getCurrentTask()` | Get current DailyTask |
| `getCurrentBlockType()` | Get current block type |
| `nextTask()` | Advance to next task; ends session if last |
| `advanceToNextBlock()` | Skip to first task of next block |
| `replaceCurrentBlock(newTasks)` | Swap current block's tasks (for repeat) |
| `endSession()` | Set active=false, finishedToken=true |
| `fastForwardTo(taskIndex)` | Jump to specific task (for resume) |
| `getBlockProgress()` | Calculate BlockProgress for current position |
| `isSessionComplete()` | Check if all tasks done |

### 8.9.2 DailySessionComposer

A pure builder class (no state) that constructs `List<DailyTask>` sessions. Created fresh each time it is needed:

```kotlin
val composer = DailySessionComposer(lessonStore, verbDrillStore, packWordMasteryStore)
```

**Key methods:**
| Method | Purpose |
|--------|---------|
| `buildSession(...)` | Build full 3-block session with cursor |
| `buildRepeatSession(...)` | Rebuild session from stored card IDs |
| `rebuildBlock(...)` | Rebuild a single block by type |

### 8.9.3 TrainingStateAccess Interface

Defined in `DailySessionHelper.kt`:
```kotlin
interface TrainingStateAccess {
    val uiState: StateFlow<TrainingUiState>
    fun updateState(transform: (TrainingUiState) -> TrainingUiState)
    fun saveProgress()
}
```

This interface is the contract between helpers and the ViewModel. Helpers receive state access but have no lifecycle awareness and cannot call other helpers directly. All coordination flows through the ViewModel.

---

## 8.10 Critical Business Logic

### 8.10.1 Sub-lesson Type Selection (NEW_ONLY vs MIXED)

Sub-lessons are built by `MixedReviewScheduler` (external to ViewModel). The ViewModel consumes the result:

```kotlin
private fun rebuildSchedules(lessons: List<Lesson>) {
    val lessonKey = lessons.joinToString("|") { "${it.id}:${it.cards.size}" }
    val blockSize = subLessonSize.coerceIn(subLessonSizeMin, subLessonSizeMax)
    val key = "${lessonKey}|${blockSize}"
    if (key == scheduleKey) return  // Skip if unchanged
    scheduleKey = key
    lessonSchedules = MixedReviewScheduler(blockSize).build(lessons)
}
```

The schedule is keyed by lesson IDs, card counts, and block size. It is rebuilt only when the key changes. Sub-lesson types are stored in `subLessonTypes: List<SubLessonType>`.

### 8.10.2 Card Selection and Ordering

**LESSON mode:**
1. Get schedule for selected lesson
2. Get sub-lessons list
3. Select sub-lesson at `activeSubLessonIndex`
4. Filter out hidden cards
5. Cards maintain their original order from the CSV

**ALL_SEQUENTIAL mode:**
1. Concatenate all lesson cards in order
2. Filter hidden cards
3. Split into blocks by `subLessonSize`

**ALL_MIXED mode:**
1. Collect all cards from all lessons (using `allCards` which includes reserve pool)
2. Filter hidden cards
3. Shuffle
4. Take up to 300 cards
5. Split into blocks

**Boss mode:**
- LESSON: shuffle current lesson cards, take up to 300
- MEGA: shuffle all cards from previous lessons, take up to 300
- ELITE: shuffle all cards, take `eliteSubLessonSize() * eliteStepCount`

**Daily Practice:**
- TRANSLATE: cursor-driven, sequential (no shuffle), next 10 from current lesson offset
- VOCAB: SRS-prioritized (overdue > new > least-recent)
- VERBS: weak-first, excluding previously shown

### 8.10.3 Boss Battle Rules

1. **Unlock requirement:** 15 completed sub-lessons (except ELITE and test mode)
2. **Single attempt:** Boss sessions are not saved mid-battle (`saveProgress()` skips when `bossActive && bossType != ELITE`)
3. **No skipping:** User must answer each card; no skip mechanism
4. **Progress tracking:** `bossProgress` = max of current index and previous progress (never decreases within a battle)
5. **Reward milestones at 50% (BRONZE), 75% (SILVER), 100% (GOLD):** Session pauses, message displayed, user must dismiss
6. **Reward persistence:** LESSON rewards stored per lesson in `bossLessonRewards`, MEGA rewards per lesson in `bossMegaRewards`
7. **State restoration after boss:** Previous lesson ID and session state are restored from ProgressStore

### 8.10.4 Spaced Repetition Integration Within Training

**Flower state calculation (`refreshFlowerStates()`):**
1. For each lesson, get `LessonMasteryState` from MasteryStore
2. Calculate flower via `FlowerCalculator.calculate(mastery, lesson.cards.size)`
3. Result: `FlowerVisual` with state, masteryPercent, healthPercent, scaleMultiplier
4. Store in `lessonFlowers` map and `currentLessonFlower`

**Ladder row calculation:**
1. For each lesson, get mastery and calculate metrics via `LessonLadderCalculator.calculate(mastery, nowMs)`
2. Produce `LessonLadderRow` with index, lessonId, title, uniqueCardShows, daysSinceLastShow, intervalLabel

**Vocab SRS (daily practice):**
- Words tracked in `WordMasteryStore` with `intervalStepIndex`, `nextReviewDateMs`, `correctCount`, `incorrectCount`
- `rateVocabCard(rating)` applies step changes based on rating
- Next review date computed from interval ladder: `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` days
- Learned threshold: `intervalStepIndex >= 3`

**Verb drill SRS:**
- Cards tracked per tense combo in `VerbDrillStore`
- `everShownCardIds` / `todayShownCardIds` sets track exposure
- Weakness scoring: `1 - (shownInCombo / totalInCombo)` per tense
- Selection: weak-first, excluding previously shown cards

### 8.10.5 Timer Mechanics

The active timer runs as a coroutine:
```kotlin
private fun resumeTimer() {
    activeStartMs = SystemClock.elapsedRealtime()
    timerJob = viewModelScope.launch {
        while (true) {
            delay(500)
            val elapsed = SystemClock.elapsedRealtime() - start
            _uiState.update { it.copy(activeTimeMs = it.activeTimeMs + elapsed) }
            activeStartMs = SystemClock.elapsedRealtime()
            saveProgress()
        }
    }
}
```

- Ticks every 500ms
- Accumulates `activeTimeMs`
- Saves progress on each tick
- Cancelled on `pauseTimer()`
- Only one timer can run (checked via `timerJob?.isActive`)

### 8.10.6 Voice Metrics Tracking

When a correct answer is submitted via VOICE mode:
- `voicePromptStartMs` is set when voice prompt starts
- `voiceActiveMs` accumulates the duration: `SystemClock.elapsedRealtime() - voicePromptStartMs`
- `voiceWordCount` accumulates metric words (normalized words with length >= 3)
- These metrics are used for elite speed calculation: `words / (activeMs / 60000.0)`

### 8.10.7 Background TTS Download

On init, the ViewModel starts background downloads for all missing TTS models:
```kotlin
private fun startBackgroundTtsDownload() {
    val missingLanguages = languages.map { it.id }
        .filter { !ttsModelManager.isModelReady(it) }
    // Downloads all missing models concurrently
    // Updates bgTtsDownloadStates and ttsModelsReady in UI state
}
```

The download state is mirrored to `ttsDownloadState` so the download dialog shows live progress if opened while background download runs.

### 8.10.8 Pack Reload on App Start

The init block launches a background coroutine to force-reload default packs:
```kotlin
viewModelScope.launch(Dispatchers.IO) {
    val reloaded = lessonStore.forceReloadDefaultPacks()
    if (!reloaded) return@launch
    // Rebuild everything on Main thread
}
```

This ensures the latest bundled content is always available. State is read inside the `update` lambda to avoid TOCTOU races.

### 8.10.9 Lifecycle

```kotlin
override fun onCleared() {
    saveProgress()
    bgDownloadJob?.cancel()
    ttsEngine.release()
    asrEngine?.release()
    soundPool.release()
    super.onCleared()
}
```

The ViewModel saves progress on destruction and releases all native resources (TTS engine, ASR engine, sound pool).

---

## 8.11 Supporting Data Classes

### SubmitResult
```kotlin
data class SubmitResult(val accepted: Boolean, val hintShown: Boolean)
```

### TrainingUiState
70+ field data class (defined in TrainingViewModel.kt, lines 3490-3605). See section 8.2.1 for field breakdown.

### LessonLadderRow
```kotlin
data class LessonLadderRow(
    val index: Int,
    val lessonId: String,
    val title: String,
    val uniqueCardShows: Int?,
    val daysSinceLastShow: Int?,
    val intervalLabel: String?
)
```

### Private Mutable State

| Variable | Type | Purpose |
|----------|------|---------|
| `sessionCards` | `List<SentenceCard>` | Cards for current sub-lesson (mutated by buildSessionCards) |
| `bossCards` | `List<SentenceCard>` | Cards for current boss battle |
| `eliteCards` | `List<SentenceCard>` | Cards for current elite step |
| `vocabSession` | `List<VocabEntry>` | Entries for current vocab sprint |
| `subLessonTotal` | `Int` | Cards in current sub-lesson |
| `subLessonCount` | `Int` | Total sub-lessons for current schedule |
| `lessonSchedules` | `Map<String, LessonSchedule>` | Cached schedules per lesson |
| `scheduleKey` | `String` | Cache key for schedule validity |
| `timerJob` | `Job?` | Active timer coroutine |
| `activeStartMs` | `Long?` | Timestamp for timer accumulation |
| `forceBackupOnSave` | `Boolean` | Trigger backup on next saveProgress |
| `prebuiltDailySession` | `List<DailyTask>?` | Pre-computed daily session for fast start |
| `lastDailyTasks` | `List<DailyTask>?` | In-memory cache for repeat |
| `dailyPracticeAnsweredCounts` | `MutableMap<DailyBlockType, Int>` | Per-block VOICE/KEYBOARD answer tracking |
| `dailyCursorAtSessionStart` | `DailyCursorState` | Snapshot for cancel rollback |
| `eliteSizeMultiplier` | `Double` | From config, default 1.25 |
