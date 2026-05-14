# GrammarMate — Unified Spec (DO NOT COMMIT)

---

# 22. Use Case Registry with Acceptance Criteria

Structured registry of all verified use cases extracted from scenario traces and user stories.

---

## Summary

| Metric | Value |
|--------|-------|
| Total Use Cases | 62 |
| Total Acceptance Criteria | 309 |
| Domains | 18 |

### Per-Domain Counts

| # | Domain | UCs | ACs |
|---|--------|-----|-----|
| 1 | Training session (start, answer, next, finish) | 6 | 31 |
| 2 | Mastery & flowers (progress, SRS, bloom) | 5 | 26 |
| 3 | Input modes (voice, keyboard, word bank) | 5 | 21 |
| 4 | Boss battle | 4 | 17 |
| 5 | Daily Practice (3 blocks, cursor) | 5 | 21 |
| 6 | Verb Drill | 5 | 25 |
| 7 | Vocab Drill | 5 | 23 |
| 8 | Navigation & screens | 4 | 14 |
| 9 | Lesson packs (import, manifest) | 4 | 17 |
| 10 | State persistence (backup, restore, onboarding) | 5 | 26 |
| 11 | Difficulty & hints (HintLevel, parenthetical stripping) | 3 | 16 |
| 12 | [UI-CONSISTENCY-2025] UI consistency (eye mode, voice auto, report sheet, mix challenge, shared components) | 5 | 30 |
| 13 | Font size scaling (ruTextScale across training screens) | 1 | 10 |
| 14 | Voice auto-start toggle (global setting for all training screens) | 1 | 10 |
| 15 | Verb/tense info sheets (interactive chips on VerbDrillScreen and DailyPracticeScreen) | 1 | 5 |
| 16 | Performance (IO dispatchers, caching, parallel loading) | 1 | 5 |
| 17 | TTS icon auto-recovery (speaker icon updates after model download) | 1 | 4 |
| 18 | Progress reset | 1 | 8 |

---

## Domain 1: Training Session (Start, Answer, Next, Finish)

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-01 | Start a training sub-lesson | At least one lesson is unlocked; user is on Lesson Roadmap | 1. User taps a sub-lesson circle on the roadmap. 2. App loads sub-lesson cards (NEW_ONLY or MIXED). 3. Screen transitions to Training. 4. First card displays Russian prompt. 5. Input mode defaults to VOICE. 6. Session state is set to ACTIVE; timer begins. | AC1: Session state transitions from PAUSED to ACTIVE on user action. AC2: First card's `promptRu` is displayed. AC3: `inputMode` is set to VOICE. AC4: `activeTimeMs` begins incrementing. AC5: `currentIndex` is 0. | TrainingScreen (ui/screens/TrainingScreen.kt) | `feature/training/SessionRunner.kt`, `ui/TrainingViewModel.kt` | scenario-01, US-10 |
| UC-02 | Submit a correct answer | Training session is ACTIVE; a card is displayed | 1. User types or speaks the target-language translation. 2. Answer is normalized via Normalizer pipeline. 3. Normalized input is compared against each normalized accepted answer. 4. If any match, answer is accepted. 5. Correct count increments, mastery is recorded. | AC1: `Normalizer.normalize()` trims whitespace, lowercases, removes punctuation (except hyphens), collapses spaces. AC2: Comparison checks ALL `acceptedAnswers` using `.any {}`. AC3: `correctCount` increments by 1 on correct answer. AC4: `recordCardShowForMastery()` is called (unless WORD_BANK or drill mode). AC5: `lastResult` is set to `true`. | TrainingScreen (ui/screens/TrainingScreen.kt) | `feature/training/AnswerValidator.kt`, `data/Normalization.kt`, `ui/TrainingViewModel.kt` | scenario-01, scenario-02 |
| UC-03 | Submit an incorrect answer (retry/hint flow) | Training session is ACTIVE; a card is displayed | 1. User submits a wrong answer. 2. Error tone plays, `incorrectCount` and `incorrectAttemptsForCard` increment. 3. If attempts < 3: retry allowed, voice re-triggered if VOICE mode. 4. If attempts >= 3: all accepted answers shown as hint, timer paused, `incorrectAttemptsForCard` reset to 0. | AC1: `incorrectCount` increments on every wrong answer. AC2: After 3 wrong attempts, `answerText` contains all accepted answers joined by " / ". AC3: `sessionState` is set to `HINT_SHOWN` after 3 attempts. AC4: Timer is paused when hint is shown. AC5: `incorrectAttemptsForCard` resets to 0 when hint is shown. AC6: User must manually advance after hint (nextCard). | TrainingScreen (ui/screens/TrainingScreen.kt) | `feature/training/AnswerValidator.kt`, `data/Normalization.kt`, `ui/TrainingViewModel.kt` | scenario-01 |
| UC-04 | Navigate to next card | User has answered a card (correct or after hint) | 1. `nextCard()` increments `currentIndex`. 2. Next card is loaded from `sessionCards`. 3. Input text, last result, and answer text are cleared. 4. Mastery is recorded for the new card. 5. Timer resumes if hint was shown. 6. Progress is saved. | AC1: `currentIndex` advances by 1 (clamped to last index). AC2: `currentCard` is updated to the next card. AC3: `inputText` is cleared. AC4: `recordCardShowForMastery()` is called for the new card. AC5: `saveProgress()` is called. | TrainingScreen (ui/screens/TrainingScreen.kt) | `feature/training/SessionRunner.kt`, `ui/TrainingViewModel.kt` | scenario-01 |
| UC-05 | Complete a sub-lesson (last card) | User is on the last card of a sub-lesson | 1. User answers the final card correctly. 2. Timer pauses. 3. Completed sub-lesson count is recalculated from mastery data. 4. `activeSubLessonIndex` advances (never moves backward). 5. `subLessonFinishedToken` increments. 6. Session cards are rebuilt for the next sub-lesson. 7. Flower states are refreshed. 8. Backup is triggered on next save. | AC1: `completedSubLessonCount` is recalculated from MasteryStore data. AC2: `activeSubLessonIndex` uses `maxOf(current, actual)` to prevent regression. AC3: `subLessonFinishedToken` increments, triggering navigation to LESSON screen. AC4: `refreshFlowerStates()` is called. AC5: `forceBackupOnSave` is set to `true`. AC6: `checkAndMarkLessonCompleted()` is called (marks lesson complete at 15 sub-lessons). | TrainingScreen (ui/screens/TrainingScreen.kt) | `feature/training/SessionRunner.kt`, `feature/progress/ProgressTracker.kt`, `ui/TrainingViewModel.kt` | scenario-01, US-16 |
| UC-06 | End session manually | User is in an active training session | 1. User presses back or taps exit. 2. Exit confirmation dialog appears. 3. User confirms. 4. `finishSession()` pauses timer, calculates rating, resets to PAUSED. 5. Progress is saved. 6. Screen navigates to LESSON. | AC1: Exit confirmation dialog is shown before ending session. AC2: `finishSession()` sets `sessionState` to PAUSED. AC3: `saveProgress()` persists the session state. AC4: Navigation goes to `AppScreen.LESSON`, not HOME. | GrammarMateApp (ui/GrammarMateApp.kt) | `feature/training/SessionRunner.kt`, `ui/TrainingViewModel.kt` | scenario-11 |

---

## Domain 2: Mastery & Flowers (Progress, SRS, Bloom)

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-07 | First card mastery recording | Lesson is selected; training session is ACTIVE; input mode is VOICE or KEYBOARD | 1. User answers a card correctly. 2. `recordCardShowForMastery()` checks: not drill mode, not WORD_BANK. 3. `masteryStore.recordCardShow()` creates a new `LessonMasteryState` with `uniqueCardShows=1`, `shownCardIds={cardId}`, `intervalStepIndex=0`. 4. Flower state becomes SEED. | AC1: `uniqueCardShows` goes from 0 to 1. AC2: Card ID is added to `shownCardIds`. AC3: `intervalStepIndex` is set to 0 (1-day expected interval). AC4: `FlowerCalculator` returns `FlowerState.SEED` for 1/150 mastery. AC5: Mastery data is persisted immediately to `mastery.yaml`. | TrainingScreen (ui/screens/TrainingScreen.kt) | `data/MasteryStore.kt`, `data/FlowerCalculator.kt`, `feature/progress/ProgressTracker.kt` | scenario-03 |
| UC-08 | Flower state progression (SEED to SPROUT to BLOOM) | User has practiced cards with VOICE or KEYBOARD | 1. At 0-32.9% mastery (0-49 unique cards): SEED. 2. At 33-65.9% (50-99 unique cards): SPROUT. 3. At 66-100% (99+ unique cards): BLOOM. 4. Mastery percentage is `uniqueCardShows / 150`, clamped to [0, 1]. | AC1: Flower is SEED when `masteryPercent < 0.33`. AC2: Flower is SPROUT when `0.33 <= masteryPercent < 0.66`. AC3: Flower is BLOOM when `masteryPercent >= 0.66`. AC4: Practicing the same card twice does NOT increment `uniqueCardShows`. AC5: `scaleMultiplier = (masteryPercent * healthPercent).coerceIn(0.5, 1.0)`. | HomeScreen (ui/screens/HomeScreen.kt) | `data/MasteryStore.kt`, `data/FlowerCalculator.kt` | scenario-03, US-26 |
| UC-09 | Health decay and wilt cycle | Lesson has mastery data; user has not practiced recently | 1. `daysSinceLastShow` is computed from `lastShowDateMs`. 2. If within expected interval: health = 1.0. 3. If overdue: health decays via `e^(-overdueDays/stability)` toward floor of 0.5. 4. If health < 1.0: WILTING. 5. If health <= 0.51: WILTED. 6. If no practice for > 90 days: GONE. | AC1: Health is 1.0 when `daysSinceLastShow <= expectedInterval`. AC2: Health floor is 0.5 (WILTED_THRESHOLD). AC3: Flower is WILTING when `0.51 < healthPercent < 1.0`. AC4: Flower is WILTED when `healthPercent <= 0.51`. AC5: Flower is GONE when `daysSinceLastShow > 90`. AC6: Health is restored to 1.0 instantly on any new card show. AC7: After wilted recovery, interval step does NOT advance (late review penalty). | HomeScreen (ui/screens/HomeScreen.kt) | `data/FlowerCalculator.kt`, `data/SpacedRepetitionConfig.kt` | scenario-03, scenario-04, US-27, US-28 |
| UC-10 | Spaced repetition interval advancement | Lesson has mastery data; user reviews on time | 1. On-time review: `nextIntervalStep(currentStep, true)` returns `currentStep + 1`. 2. Interval ladder: [1, 2, 4, 7, 10, 14, 20, 28, 42, 56] days. 3. Late review: step does NOT regress, but does not advance either. 4. First show: step stays at 0 (no previous repetition to evaluate). | AC1: On-time review at step 0 advances to step 1 (2-day interval). AC2: Late review does NOT change `intervalStepIndex`. AC3: Step is capped at 9 (last ladder index). AC4: Stability grows with each step: `stability = 0.9 * 2.2^stepIndex`. | LessonRoadmapScreen (ui/screens/LessonRoadmapScreen.kt) | `data/SpacedRepetitionConfig.kt`, `data/MixedReviewScheduler.kt` | scenario-04, US-24 |
| UC-11 | MIXED sub-lesson review card selection | User is in a lesson beyond the first; MIXED sub-lesson is active | 1. First lesson: all sub-lessons are NEW_ONLY. 2. Subsequent lessons: first half of cards -> NEW_ONLY, second half -> MIXED. 3. MIXED sub-lessons interleave ~5 current cards with ~5 review cards from prior lessons. 4. Review card priority: reserve pool -> main pool -> fallback (current lesson). 5. `globalMixedIndex` determines when prior lessons are due for review using the interval ladder. | AC1: First lesson has zero MIXED sub-lessons. AC2: MIXED sub-lessons contain both current and review cards. AC3: Reserve pool cards are preferred over main pool for review. AC4: Maximum 2 due lessons contribute review cards per MIXED sub-lesson. AC5: Review pool is capped at 300 cards per lesson. | TrainingScreen (ui/screens/TrainingScreen.kt) | `data/SpacedRepetitionConfig.kt`, `data/MixedReviewScheduler.kt` | scenario-01, scenario-04, US-22, US-23 |

---

## Domain 3: Input Modes (Voice, Keyboard, Word Bank)

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-12 | Voice input answer submission | Training session is ACTIVE; input mode is VOICE; mic permission granted | 1. Voice recognition auto-starts. 2. User speaks the target-language translation. 3. Recognized text populates `inputText`. 4. Answer is submitted and compared. 5. Mastery IS recorded (VOICE counts). | AC1: Voice recognition auto-triggers when `inputMode == VOICE` and session is ACTIVE. AC2: Recognized text populates the input field. AC3: `recordCardShowForMastery()` records mastery for VOICE input. AC4: Empty or null ASR result is treated as no input (no crash, no incorrect submission). | TrainingScreen (ui/screens/TrainingScreen.kt) | `ui/TrainingCardSession.kt`, `ui/TrainingViewModel.kt` | scenario-05, US-17 |
| UC-13 | Keyboard input answer submission | Training session is ACTIVE; input mode is KEYBOARD | 1. User types target-language translation. 2. User presses submit. 3. Answer is normalized and compared. 4. Mastery IS recorded (KEYBOARD counts). | AC1: `recordCardShowForMastery()` records mastery for KEYBOARD input. AC2: Empty input is rejected before validation (early return). AC3: IME action is "Done" (single-line entry). AC4: Keyboard input mode button is available on ALL training screens (TrainingScreen, DailyPracticeScreen, TrainingCardSession default controls) regardless of HintLevel setting (not gated by HARD difficulty). | TrainingScreen (ui/screens/TrainingScreen.kt) | `ui/TrainingCardSession.kt`, `ui/TrainingViewModel.kt` | scenario-05, US-18 |
| UC-14 | Word bank answer submission | Training session is ACTIVE; input mode is WORD_BANK | 1. Word chips (answer words + distractors) are displayed shuffled. 2. User taps chips sequentially to build sentence. 3. Tapping the last selected word removes it (undo). 4. User submits constructed sentence. 5. Answer is compared. 6. Mastery is NOT recorded. | AC1: Word bank contains answer words + up to 3 distractors (regular training) or up to `8-answerWords` distractors (verb drill/daily). AC2: Distractors are filtered to not match correct answer words. AC3: `recordCardShowForMastery()` returns early (WORD_BANK does NOT count for mastery). AC4: Tapping the last selected word removes it from the selection. AC5: WORD_BANK cards are tracked via `markSubLessonCardsShown()` for sub-lesson progress only (no mastery increment). | TrainingScreen (ui/screens/TrainingScreen.kt) | `feature/training/WordBankGenerator.kt`, `ui/TrainingCardSession.kt` | scenario-05, US-19, US-20 |
| UC-15 | Switch input mode mid-card | Training session is ACTIVE; a card is displayed | 1. User taps keyboard, mic, or word bank icon. 2. `setInputMode()` updates the mode. 3. If switching to WORD_BANK: `updateWordBank()` regenerates chip list. 4. If switching from WORD_BANK: `inputText` from word selection is NOT cleared. 5. Mastery is determined by the mode at submission time. | AC1: Switching to KEYBOARD mid-card from WORD_BANK allows mastery recording. AC2: Word bank chips regenerate when switching to WORD_BANK. AC3: `inputText` is not cleared when switching away from WORD_BANK. AC4: Input mode is NOT persisted between sessions (always resets to VOICE). | TrainingScreen (ui/screens/TrainingScreen.kt) | `feature/training/WordBankGenerator.kt`, `ui/TrainingCardSession.kt`, `ui/TrainingViewModel.kt` | scenario-05 |
| UC-16 | Input mode in daily practice blocks | Daily practice session is active | 1. Block 1 (TRANSLATE): rotates VOICE, KEYBOARD, WORD_BANK per card (index % 3). 2. Block 2 (VOCAB): flashcard mode only -- no word bank, optional voice. 3. Block 3 (VERBS): alternates KEYBOARD, WORD_BANK (no VOICE in default rotation). | AC1: TRANSLATE block assigns input modes via `index % 3` rotation. AC2: VOCAB block uses Anki-style flip+rate, not TrainingCardSession. AC3: VERB block alternates KEYBOARD and WORD_BANK. AC4: WORD_BANK cards in daily practice do NOT advance the cursor on session completion. | DailyPracticeScreen (ui/DailyPracticeScreen.kt) | `feature/daily/DailyPracticeCoordinator.kt`, `feature/daily/DailySessionComposer.kt` | scenario-05, scenario-06, US-39 |

---

## Domain 4: Boss Battle

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-17 | Start a boss battle | `completedSubLessonCount >= 15` or `testMode` is true; user is on Lesson Roadmap | 1. User taps "Start Boss" button. 2. `startBoss(type)` checks unlock condition. 3. Cards are loaded: LESSON = current lesson cards, MEGA = all prior lessons, ELITE = all lessons. 4. State is set: `bossActive=true`, counters reset, session in PAUSED. 5. Screen shows Training view with "Review Session" title. | AC1: Boss tile is visible only when `completedSubLessonCount >= 15` or `testMode == true`. AC2: MEGA boss is shown only for lessons at index > 0. AC3: Card selection uses `.cards` (all cards including reserve pool), shuffled, capped at 300. AC4: `bossActive` is set to `true`, `bossType` is set. AC5: Session starts in PAUSED state (user must press Play). | LessonRoadmapScreen (ui/screens/LessonRoadmapScreen.kt) | `feature/boss/BossBattleRunner.kt`, `feature/boss/BossOrchestrator.kt` | scenario-09, US-31 |
| UC-18 | Complete a boss battle and earn a reward | Boss battle is active; user has answered cards | 1. User completes or exits the boss. 2. `finishBoss()` resolves reward: GOLD (>=100%), SILVER (>75%), BRONZE (>50%), or null. 3. Reward is persisted in `bossLessonRewards` or `bossMegaRewards`. 4. Boss state is cleared. 5. Previous session state is restored from ProgressStore. 6. Reward dialog displays colored trophy. | AC1: BRONZE is awarded for progress > 50%. AC2: SILVER is awarded for progress > 75%. AC3: GOLD is awarded for progress >= 100%. AC4: Reward dialog shows trophy colored by medal type. AC5: Screen returns to LESSON after boss. AC6: Pre-boss session state is restored from ProgressStore. | GrammarMateApp (ui/GrammarMateApp.kt) | `feature/boss/BossBattleRunner.kt`, `feature/boss/BossOrchestrator.kt`, `data/ProgressStore.kt` | scenario-09, US-32 |
| UC-19 | Exit boss battle early | Boss battle is active; user presses back or exit | 1. Exit dialog appears. 2. User confirms exit. 3. `finishBoss()` calculates partial reward. 4. Boss state is cleared. 5. Screen returns to LESSON. | AC1: Partial reward is calculated based on progress at exit time. AC2: Boss can be retried after exiting (no lockout). AC3: Latest reward overwrites previous (not best-of). | GrammarMateApp (ui/GrammarMateApp.kt) | `feature/boss/BossBattleRunner.kt`, `feature/boss/BossOrchestrator.kt` | scenario-09 |
| UC-20 | Boss battle timer and state persistence | Boss battle is active | 1. Timer measures elapsed time (no countdown). 2. `saveProgress()` skips during boss (except ELITE type). 3. On app kill: boss state is lost, pre-boss state is restored. | AC1: Timer is an elapsed-time counter, not a countdown. AC2: `saveProgress()` returns early when `bossActive && bossType != ELITE`. AC3: After process death, `bossActive` resets to `false` and pre-boss state is restored from `progress.yaml`. | TrainingScreen (ui/screens/TrainingScreen.kt) | `feature/boss/BossBattleRunner.kt`, `data/ProgressStore.kt` | scenario-09, scenario-12 |

---

## Domain 5: Daily Practice (3 Blocks, Cursor)

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-21 | Start a daily practice session | At least one lesson pack is installed; user is on Home screen | 1. User taps Daily Practice tile. 2. App checks for resumable session. 3. If not resumable: `DailySessionComposer` builds 3 blocks. 4. Loading overlay shown. 5. Screen transitions to DailyPracticeScreen. | AC1: `startDailyPractice()` saves `dailyCursorAtSessionStart` for rollback on cancel. AC2: Session is composed with 3 block types: TRANSLATE (10 cards), VOCAB (10 flashcards), VERBS (10 conjugations). AC3: If first session today, card IDs are stored in cursor for Repeat. AC4: Screen transitions to `AppScreen.DAILY_PRACTICE`. | DailyPracticeScreen (ui/DailyPracticeScreen.kt) | `feature/daily/DailyPracticeCoordinator.kt`, `feature/daily/DailySessionComposer.kt` | scenario-06, US-35 |
| UC-22 | Complete a daily practice block and transition | Daily practice session is active; user finishes all cards in a block | 1. Last card in block is answered. 2. `onBlockComplete()` fires. 3. Block sparkle overlay shows "Next: {BlockLabel}" for 800ms. 4. `advanceToNextBlock()` scans to next block type. 5. New block's provider is created. | AC1: Block sparkle overlay shows "Next: Vocabulary" after TRANSLATE block. AC2: Block sparkle overlay shows "Next: Verbs" after VOCAB block. AC3: `advanceToNextBlock()` correctly skips past cards of the completed block type. AC4: Each block uses its own provider (DailyPracticeSessionProvider for TRANSLATE/VERBS, VocabFlashcardBlock for VOCAB). | DailyPracticeScreen (ui/DailyPracticeScreen.kt) | `feature/daily/DailyPracticeCoordinator.kt`, `feature/daily/DailySessionComposer.kt` | scenario-06, US-40 |
| UC-23 | Repeat vs Continue a daily session | User has completed a daily session today; taps Daily Practice again | 1. `hasResumableDailySession()` returns true. 2. Dialog offers Repeat or Continue. 3. Repeat: rebuilds from stored first-session card IDs; does NOT advance cursor. 4. Continue: builds fresh session from current cursor position; advances cursor if prior session completed with VOICE/KEYBOARD. | AC1: "Repeat" uses `firstSessionSentenceCardIds` and `firstSessionVerbCardIds` from cursor. AC2: "Repeat" does NOT advance `sentenceOffset`. AC3: "Continue" calls `startDailyPractice()` which builds new cards from cursor. AC4: Cursor advances only if all TRANSLATE + VERB cards were answered via VOICE or KEYBOARD. | GrammarMateApp (ui/GrammarMateApp.kt) | `feature/daily/DailyPracticeCoordinator.kt`, `feature/daily/DailySessionComposer.kt` | scenario-06, US-37, US-38 |
| UC-24 | Daily practice cursor tracking | Daily practice session is in progress | 1. `DailyCursorState` persists `sentenceOffset`, `currentLessonIndex`, `firstSessionDate`, first-session card IDs. 2. Cursor is saved in `progress.yaml`. 3. On session completion: `advanceCursor(sentenceCount)` increments `sentenceOffset`. | AC1: `sentenceOffset` advances by the count of practiced TRANSLATE cards. AC2: `currentLessonIndex` is never advanced by `advanceCursor()`. AC3: Cursor state survives process death (persisted in `progress.yaml`). AC4: `DailySessionState` (in-memory) does NOT survive process death. | DailyPracticeScreen (ui/DailyPracticeScreen.kt) | `feature/daily/DailyPracticeCoordinator.kt`, `data/ProgressStore.kt` | scenario-06, scenario-12, US-36 |
| UC-25 | Daily practice vocab block SRS selection | Daily practice session is being composed | 1. All vocab words loaded from pack's drill files. 2. Mastery states loaded from WordMasteryStore. 3. Words categorized: due (overdue/never reviewed), new, scheduled. 4. Due words sorted by most overdue first. 5. Takes up to 10 due words. 6. Fills with new words sorted by rank if < 10. 7. Fallback: least recently reviewed. 8. Direction alternates IT_TO_RU / RU_TO_IT. | AC1: Selection prioritizes overdue words over new words. AC2: New words fill remaining slots sorted by rank. AC3: Fallback uses least recently reviewed words. AC4: Direction alternates: even index = IT_TO_RU, odd = RU_TO_IT. AC5: Vocab block uses Anki-style flip + 4 rating buttons, not TrainingCardSession. | DailyPracticeScreen (ui/DailyPracticeScreen.kt) | `feature/daily/DailyPracticeCoordinator.kt`, `data/WordMasteryStore.kt` | scenario-06, US-48 |

---

## Domain 6: Verb Drill

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-26 | Start a standalone verb drill session | Active pack has `verbDrill` section; user taps Verb Drill tile | 1. `VerbDrillViewModel` loads cards from pack's verb drill CSVs. 2. User selects tense and group filters. 3. User taps Start. 4. Cards filtered, excluding `todayShownCardIds`. 5. 10-card batch created (random or frequency-sorted). | AC1: Verb Drill tile is visible only when `lessonStore.hasVerbDrill(activePackId, languageId)` returns true. AC2: `reloadForPack(packId)` creates pack-scoped `VerbDrillStore` and reloads cards. AC3: Card selection excludes cards already shown today for the combo. AC4: Batch size is 10 cards. AC5: Frequency sort orders by `rank` ascending. | VerbDrillScreen (ui/VerbDrillScreen.kt) | `ui/VerbDrillScreen.kt`, `data/VerbDrillStore.kt` | scenario-07, US-42, US-44 |
| UC-27 | Submit verb conjugation answer | Verb drill session is active; a card is displayed | 1. User types conjugation and taps Check. 2. `Normalizer.normalize()` applied to both input and answer. 3. Single-answer comparison (not `acceptedAnswers.any`). 4. Correct: auto-advances after 500ms if VOICE mode. 5. Wrong: increments `incorrectAttempts`, shows feedback. 6. After 3 wrong: auto-shows hint, pauses. | AC1: Validation uses `Normalizer.normalize()` on both sides. AC2: Verb drill uses single `card.answer` comparison, not multiple accepted answers. AC3: Voice mode auto-submits immediately on ASR result. AC4: Voice mode auto-advances after 500ms on correct answer. AC5: 3 wrong attempts auto-show hint and pause. AC6: Manual "Show Answer" (eye button) also shows hint and pauses. | VerbDrillScreen (ui/VerbDrillScreen.kt) | `ui/VerbDrillScreen.kt`, `data/Normalization.kt` | scenario-07, scenario-02 |
| UC-28 | Persist verb drill progress per card | Verb drill session is active; a card is advanced | 1. `persistCardProgress()` builds combo key `{group}\|{tense}`. 2. Adds card ID to `everShownCardIds` and `todayShownCardIds`. 3. Calls `verbDrillStore.upsertComboProgress()`. 4. Progress is persisted immediately via AtomicFileWriter. | AC1: Progress is persisted immediately per card, not batched. AC2: `everShownCardIds` accumulates permanently (never resets). AC3: `todayShownCardIds` resets on date change. AC4: File path is `drills/{packId}/verb_drill_progress.yaml`. | VerbDrillScreen (ui/VerbDrillScreen.kt) | `data/VerbDrillStore.kt` | scenario-07, US-45 |
| UC-29 | Reload verb drill on pack switch | User switches active pack while on Verb Drill screen | 1. `reloadForPack(newPackId)` checks idempotency guard. 2. If same pack and cards loaded: early return. 3. Otherwise: creates new `VerbDrillStore`, reloads cards and progress from new pack. | AC1: `reloadForPack()` returns early if `currentPackId == packId && allCards.isNotEmpty()`. AC2: New `VerbDrillStore` instance is created scoped to new `packId`. AC3: `allCards`, `progressMap`, `packIdForCardId` are fully rebuilt. AC4: Speed tracking state is NOT reset on pack switch (minor issue). | VerbDrillScreen (ui/VerbDrillScreen.kt) | `ui/VerbDrillScreen.kt`, `data/VerbDrillStore.kt` | scenario-07, scenario-10 |
| UC-59 | VerbDrill voice auto-launch respects global voiceAutoStart flag | VerbDrill session is active; global voice auto-start toggle is in Settings | 1. `voiceAutoStart` is read from `AudioState.voiceAutoStart` (global setting). 2. On card change, both `VoiceAutoLauncher` and `DefaultVerbDrillInputControls` LaunchedEffect check `voiceAutoStart` before launching ASR. 3. When `voiceAutoStart == false`, no ASR intent is launched automatically (but mic button still works on manual click). 4. When `voiceAutoStart == true`, ASR launches within 500ms. | AC1 [BEHAVIORAL]: When voiceAutoStart=false + Continue pressed, no voice auto-launch occurs. AC2 [BEHAVIORAL]: When voiceAutoStart=false + new card appears, no voice auto-launch occurs. AC3 [BEHAVIORAL]: When voiceAutoStart=false, mic button on input mode bar still works and launches speech recognition on manual click. AC4 [BEHAVIORAL]: When voiceAutoStart=true + Continue pressed, voice auto-launches within 500ms. AC5 [BEHAVIORAL]: Both `VoiceAutoLauncher` and `DefaultVerbDrillInputControls` LaunchedEffect respect the global `voiceAutoStart` flag. AC6 [STRUCTURAL]: No per-drill voice toggle exists on VerbDrillSelectionScreen. | VerbDrillScreen (ui/VerbDrillScreen.kt) | `ui/VerbDrillScreen.kt`, `ui/components/VoiceAutoLauncher.kt`, `ui/GrammarMateApp.kt` | voice-auto-start |

---

## Domain 7: Vocab Drill

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-30 | Start a standalone vocab drill session | Active pack has `vocabDrill` section; user taps Vocab Drill tile | 1. `VocabDrillViewModel` loads words from pack's vocab drill CSVs. 2. User selects POS and rank range filters. 3. Due words selected (overdue or never reviewed). 4. Sorted by rank ascending. 5. Takes up to 10 cards. | AC1: Vocab Drill tile shows mastered word count. AC2: `startSession()` filters by `selectedPos` and `rankMin`/`rankMax`. AC3: Cards are sorted by `word.rank` ascending (most frequent first). AC4: Batch size is up to 10 cards. AC5: If no due words: session is null, Start button shows "No due words". | VocabDrillScreen (ui/VocabDrillScreen.kt) | `ui/VocabDrillScreen.kt`, `data/WordMasteryStore.kt` | scenario-08, US-46, US-49 |
| UC-31 | Vocab flashcard display and flip | Vocab drill session is active; a card is displayed | 1. Card front shows: POS badge, rank badge, main word, TTS button (IT_TO_RU only), voice input area. 2. User taps or voice triggers flip after 800ms. 3. Card back shows: translation, forms grid, collocations, mastery indicator. | AC1: Front shows Italian word for IT_TO_RU, Russian meaning for RU_TO_IT. AC2: TTS button is shown only for IT_TO_RU direction on front. AC3: Auto-flip triggers 800ms after `voiceCompleted` becomes true. AC4: Back shows forms (msg/fsg/mpl/fpl) for adjectives; dashes for numbers/pronouns (known bug). | VocabDrillScreen (ui/VocabDrillScreen.kt) | `ui/VocabDrillScreen.kt`, `data/WordMasteryStore.kt` | scenario-08, US-47 |
| UC-32 | Anki-style rating (Again/Hard/Good/Easy) | Vocab flashcard is flipped; rating buttons are displayed | 1. Four buttons: Again (delta -100, reset to 0), Hard (delta 0, stay), Good (delta +1), Easy (delta +2). 2. New step is clamped to [0, 9]. 3. `isLearned = true` when `newStepIndex >= LEARNED_THRESHOLD (3)`. 4. `nextReviewDateMs` computed from ladder. | AC1: Again resets `intervalStepIndex` to 0. AC2: Hard keeps step unchanged. AC3: Good advances step by 1. AC4: Easy advances step by 2. AC5: `isLearned` is set to `true` at step >= 3 (LEARNED_THRESHOLD). AC6: Button labels show the next interval from the ladder. | VocabDrillScreen (ui/VocabDrillScreen.kt) | `ui/VocabDrillScreen.kt`, `data/WordMasteryStore.kt`, `data/SpacedRepetitionConfig.kt` | scenario-08 |
| UC-33 | Vocab voice input matching | Vocab drill session is active; voice mode is enabled | 1. Voice recognition auto-launches after 500ms. 2. Expected answer determined by direction (IT_TO_RU = Russian, RU_TO_IT = Italian). 3. Expected split by "/" for synonyms. 4. Custom `normalizeForMatch()`: trim, lowercase, strip trailing punctuation only. 5. Match if recognized text equals OR contains any valid answer. 6. Max 3 voice attempts. | AC1: `normalizeForMatch()` is lighter than `Normalizer.normalize()` (trailing punctuation only). AC2: Match uses both exact equality and `contains()`. AC3: Max 3 voice attempts before auto-skip. AC4: Voice recognition language tag: IT_TO_RU = "ru-RU", RU_TO_IT = "it-IT". | VocabDrillScreen (ui/VocabDrillScreen.kt) | `ui/VocabDrillScreen.kt` | scenario-08 |
| UC-34 | Vocab drill pack switching | User switches active pack while on Vocab Drill screen | 1. `reloadForPack(newPackId)` recreates `WordMasteryStore` scoped to new pack. 2. Rebuilds `allWords` from new pack's CSV files. 3. Reloads `masteryMap` from `drills/{packId}/word_mastery.yaml`. | AC1: `WordMasteryStore` is recreated with new `packId`. AC2: `allWords` and `masteryMap` are fully rebuilt. AC3: User filter selections (`selectedPos`, `rankMin`/`rankMax`) persist on pack switch. AC4: Drill direction and voice mode persist on pack switch. | VocabDrillScreen (ui/VocabDrillScreen.kt) | `ui/VocabDrillScreen.kt`, `data/WordMasteryStore.kt` | scenario-08, scenario-10 |

---

## Domain 8: Navigation & Screens

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-35 | Navigate Home to Training | User is on Home screen; at least one lesson exists | 1. User taps lesson tile -> `selectLesson(lessonId)` -> LESSON screen. 2. User taps sub-lesson on roadmap -> `selectSubLesson(index)` -> TRAINING screen. 3. Alternative: "Continue Learning" button -> LESSON screen. | AC1: `selectLesson()` rebuilds schedules and resets session state. AC2: `selectSubLesson()` loads cards for the chosen sub-lesson. AC3: Screen transitions via `AppScreen` enum. | GrammarMateApp (ui/GrammarMateApp.kt) | `ui/GrammarMateApp.kt` | scenario-01, scenario-11 |
| UC-36 | Back navigation per screen | User is on any screen | 1. TRAINING: Back shows exit confirmation dialog -> LESSON. 2. LESSON: Back -> HOME. 3. DAILY_PRACTICE: Back -> HOME (no confirmation on system back). 4. VERB_DRILL / VOCAB_DRILL: Back -> HOME. 5. STORY: Back -> LESSON. | AC1: TRAINING Back shows exit dialog with "End session?" prompt. AC2: LESSON Back navigates directly to HOME. AC3: DAILY_PRACTICE system Back navigates to HOME without confirmation (in-screen arrow shows dialog). AC4: VOCAB_DRILL system Back navigates to HOME without calling `refreshVocabMasteryCount()` (known bug). | GrammarMateApp (ui/GrammarMateApp.kt) | `ui/GrammarMateApp.kt` | scenario-11 |
| UC-37 | Sub-lesson completion auto-navigation | User completes the last card of a sub-lesson | 1. `subLessonFinishedToken` increments in ViewModel state. 2. `LaunchedEffect` in GrammarMateApp detects token change. 3. Screen is set to `AppScreen.LESSON`. | AC1: Token-based navigation triggers automatically on sub-lesson completion. AC2: Navigation goes to LESSON (Lesson Roadmap), not HOME. AC3: Boss completion uses a separate `bossFinishedToken` with same pattern. | GrammarMateApp (ui/GrammarMateApp.kt) | `ui/GrammarMateApp.kt` | scenario-01, scenario-11 |
| UC-38 | Drill tile visibility by pack | User is on Home screen | 1. `hasVerbDrill` and `hasVocabDrill` computed from active pack's drill files. 2. Drill tiles Row rendered only when either is true. 3. Checks only the active pack, not all installed packs. | AC1: `hasVerbDrill` checks `grammarmate/drills/{packId}/verb_drill/` for CSV files. AC2: `hasVocabDrill` checks `grammarmate/drills/{packId}/vocab_drill/` for CSV files. AC3: If `activePackId` is null, both default to `false`. AC4: Computation is keyed on `state.activePackId` and `state.selectedLanguageId`. | HomeScreen (ui/screens/HomeScreen.kt) | `ui/screens/HomeScreen.kt`, `data/LessonStore.kt` | scenario-10, scenario-11, US-44 |

---

## Domain 9: Lesson Packs (Import, Manifest)

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-39 | Import a valid lesson pack ZIP | User has a valid ZIP file with `manifest.json` and CSV lessons | 1. ZIP is extracted to temp directory. 2. `manifest.json` is parsed and validated. 3. `ensureLanguage()` registers the language. 4. Old pack with same `packId` is removed. 5. Temp dir moves to `packs/{packId}/`. 6. Lessons are imported from CSVs. 7. Drill files are copied to `drills/{packId}/`. 8. Pack is registered in `packs.yaml`. | AC1: Path traversal entries in ZIP are rejected with `error("Invalid zip entry")`. AC2: Lessons with `type: "verb_drill"` in manifest are filtered (not parsed as standard lessons). AC3: Verb drill files copied to `drills/{packId}/verb_drill/`. AC4: Vocab drill files copied to `drills/{packId}/vocab_drill/`. AC5: Re-import of same `packId` replaces existing content. AC6: Invalid CSV rows are silently skipped. | GrammarMateApp (ui/GrammarMateApp.kt) | `data/LessonStore.kt`, `data/PackImporter.kt` | scenario-13, US-56 |
| UC-40 | Import a pack without manifest or with malformed manifest | User selects a ZIP file | 1. If `manifest.json` missing: temp dir deleted, `error("Manifest not found")` thrown. 2. If `manifest.json` malformed: JSON parse exception propagates. 3. Temp directory may NOT be cleaned up on parse failure. | AC1: Missing manifest results in error with temp directory cleanup. AC2: Malformed JSON throws `JSONException` (temp dir NOT cleaned up -- known gap). AC3: User is returned to the app without partial import. | GrammarMateApp (ui/GrammarMateApp.kt) | `data/LessonStore.kt`, `data/PackImporter.kt` | scenario-13 |
| UC-41 | Default pack seeding on first launch | App is launched for the first time; no lesson content exists | 1. `seedDefaultPacksIfNeeded()` checks seed marker. 2. If no content and no marker: imports `EN_WORD_ORDER_A1` and `IT_VERB_GROUPS_ALL` from assets. 3. Seed marker written atomically. | AC1: Both default packs are bundled in `assets/grammarmate/packs/`. AC2: Both `DefaultPack` entries exist in `LessonStore.defaultPacks`. AC3: Seed marker at `grammarmate/seed_v1.done` prevents re-seeding. AC4: `forceReloadDefaultPacks()` runs on every app start to ensure updated content. | N/A (background) | `data/LessonStore.kt` | scenario-13, scenario-15, US-02 |
| UC-42 | Pack removal | User deletes a pack from Settings | 1. `removeInstalledPackData(packId)` reads manifest for lesson IDs. 2. Each lesson is deleted (CSV + index entry). 3. Pack entry removed from `packs.yaml`. 4. Pack directory deleted. 5. Drill directory `drills/{packId}/` deleted recursively (including progress files). | AC1: All lesson CSVs and index entries are removed. AC2: Pack directory is deleted. AC3: `drills/{packId}/` is deleted recursively (CSVs + progress YAMLs). AC4: Pack registry in `packs.yaml` is updated. | GrammarMateApp (ui/GrammarMateApp.kt) | `data/LessonStore.kt` | scenario-13, US-58 |

---

## Domain 10: State Persistence (Backup, Restore, Onboarding)

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-43 | Create a backup | User has progress data; user taps "Save Progress" in Settings | 1. `BackupManager.createBackup()` copies state files to `Downloads/BaseGrammy/backup_latest/`. 2. Included: `mastery.yaml`, `progress.yaml`, `profile.yaml`, `streak_{lang}.yaml`, `metadata.txt`. 3. Uses flat directory format (not ZIP). | AC1: Backup directory is `Downloads/BaseGrammy/backup_latest/`. AC2: Exactly 4 data file types + `metadata.txt` are included. AC3: Drill progress files (`verb_drill_progress.yaml`, `word_mastery.yaml`) are NOT included (known gap). AC4: `hidden_cards.yaml`, `bad_sentences.yaml` are NOT included. AC5: Backup is a flat directory, not a ZIP archive. | GrammarMateApp (ui/GrammarMateApp.kt) | `data/BackupManager.kt` | scenario-14, US-61 |
| UC-44 | Restore from backup | User has a backup directory; user taps "Restore Backup" | 1. SAF picker or file-path restore selects backup directory. 2. Files are copied to `grammarmate/` internal storage. 3. Individual file failures are logged and skipped (SAF path). 4. App reloads all data. | AC1: Restore copies `mastery.yaml`, `progress.yaml`, `profile.yaml`, streak files. AC2: Corrupt YAML files are still copied; corruption surfaces when stores parse them. AC3: Missing files in backup are silently skipped. AC4: Pre-existing files NOT in backup are left untouched (additive restore). AC5: Streak migration from old `streak.yaml` to `streak_{lang}.yaml` is handled. | AppRoot (ui/AppRoot.kt) | `data/BackupRestorer.kt`, `data/BackupManager.kt` | scenario-14, US-62 |
| UC-45 | Resume app after process death | App was killed while user had an active session | 1. `AppRoot` checks `RestoreNotifier` status. 2. If DONE: renders `GrammarMateApp`. 3. `TrainingViewModel.init` loads `TrainingProgress` from `progress.yaml`. 4. Restores: language, lesson, card index, counts, session state, daily cursor. 5. Screen always initializes to HOME. | AC1: `restoredScreen` is hardcoded to `"HOME"`. AC2: Card index and counts are restored from `progress.yaml`. AC3: Active session is restored if `sessionState == ACTIVE && currentCard != null`. AC4: Daily session state (`DailySessionState`) is NOT restored (ephemeral). AC5: Boss battle state is NOT restored (intentional). AC6: `currentScreen` is persisted but never used for restoration. | AppRoot, GrammarMateApp (ui/AppRoot.kt, ui/GrammarMateApp.kt) | `data/ProgressStore.kt`, `data/BackupManager.kt`, `data/BackupRestorer.kt` | scenario-12, US-03 |
| UC-46 | First launch onboarding | App is freshly installed; no data exists | 1. `MainActivity` checks for restore data -> none found. 2. On Android 10+: SAF picker shown (user can cancel). 3. `AppRoot` renders `GrammarMateApp` after restore check. 4. Default packs seeded. 5. WelcomeDialog triggers when user navigates away from HOME (not on HOME itself). | AC1: Default packs are seeded from assets on first launch. AC2: First lesson tile is SPROUT; all others are LOCKED. AC3: WelcomeDialog appears when user first navigates away from HOME (not on HOME -- known UX issue). AC4: `userName` defaults to "GrammarMateUser" until set. AC5: Config is seeded from `assets/grammarmate/config.yaml`. | HomeScreen (ui/screens/HomeScreen.kt) | `data/LessonStore.kt`, `data/BackupRestorer.kt` | scenario-15, US-01, US-02 |
| UC-47 | Save progress frequency | User is in an active training session | 1. `saveProgress()` called on every answer, card navigation, mode change, pause. 2. Timer tick calls `saveProgress()` every 500ms. 3. Boss battles skip save (except ELITE). 4. Writes use `AtomicFileWriter` (temp -> fsync -> rename). | AC1: Maximum data loss window is ~500ms (timer interval). AC2: Each `saveProgress()` writes full `TrainingProgress` to `progress.yaml`. AC3: Mastery writes are per-card and immediate (separate file). AC4: Verb drill and word mastery writes are per-card and immediate (pack-scoped files). AC5: `AtomicFileWriter` uses temp -> fsync -> rename pattern. | N/A (background) | `data/ProgressStore.kt`, `data/MasteryStore.kt` | scenario-12 |

---

## Domain 11: Difficulty & Hints (HintLevel, Parenthetical Stripping)

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-48 | Answer normalization pipeline | User submits an answer in any training context | 1. Trim and collapse whitespace to single spaces. 2. Remove minutes from time expressions (`"8:30"` -> `"8"`). 3. Lowercase. 4. Iterate characters, strip `.,?!"<>;:()[]{}` but keep hyphens and apostrophes. 5. Collapse whitespace again and trim. | AC1: Apostrophes within words are preserved (e.g. `l'albero` stays `l'albero`). AC2: Accented/diacritic characters are kept as-is (no NFD stripping). AC3: Hyphens are preserved. AC4: Time normalization strips minutes only (`"3:15"` -> `"3"`). AC5: Empty/whitespace-only input normalizes to `""`. AC6: Partial string matches do NOT count (exact equality required). | TrainingScreen (ui/screens/TrainingScreen.kt) | `data/Normalization.kt`, `feature/training/AnswerValidator.kt` | scenario-02 |
| UC-49 | 3-attempt retry with automatic hint reveal | User submits wrong answers on the same card | 1. Attempt 1: `incorrectAttemptsForCard` = 1, retry allowed, voice re-triggered. 2. Attempt 2: `incorrectAttemptsForCard` = 2, retry allowed. 3. Attempt 3: hint auto-shown (all accepted answers), timer paused, `incorrectAttemptsForCard` reset to 0. 4. After hint: user must press Next/Play to advance. | AC1: Error tone plays on each incorrect answer. AC2: After 3 wrong attempts, `answerText` shows all accepted answers joined by " / ". AC3: `sessionState` transitions to `HINT_SHOWN` after 3 attempts. AC4: Timer is paused when hint is shown. AC5: `incorrectAttemptsForCard` resets to 0 on hint display. AC6: User cannot submit further answers while `HINT_SHOWN`. | TrainingScreen (ui/screens/TrainingScreen.kt) | `shared/SettingsActionHandler.kt`, `data/AppConfigStore.kt`, `ui/TrainingViewModel.kt` | scenario-01, scenario-02, US-13 |
| UC-50 | Manual "Show Answer" (eye button) | User is on a card in any training context | 1. User taps the eye/show-answer button. 2. Answer is revealed immediately. 3. Session enters hint/paused state. 4. User can advance to next card. | AC1: Eye button is always available during a card. AC2: Tapping it reveals the accepted answer(s) without counting as an incorrect attempt. AC3: In verb drill, manual show sets `incorrectAttempts = 3` and `hintAnswer = card.answer`. AC4: TTS auto-plays the answer when revealed (if TTS is available). | TrainingScreen (ui/screens/TrainingScreen.kt) | `ui/TrainingCardSession.kt`, `shared/SettingsActionHandler.kt` | scenario-01, scenario-07, US-14 |

---

## Domain 12: [UI-CONSISTENCY-2025] UI Consistency (Eye Mode, Voice Auto, Report Sheet, Mix Challenge, Shared Components)

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-51 | Eye mode shows styled hint on all screens | Any training screen (lessons, daily practice, verb drill); user is on a card | 1. User taps eye/show-answer icon. 2. Answer is revealed in a pink `Card(errorContainer.copy(alpha = 0.3f))`. 3. Answer text displayed in red (`MaterialTheme.colorScheme.error`). 4. Inline TTS replay button is shown. | AC1: Hint card uses pink Card background matching VerbDrill style. AC2: Answer text is in error color. AC3: TTS replay button is inline (not separate row). AC4: Visual is consistent across TrainingScreen, VerbDrillScreen, DailyPracticeScreen. AC5 [BEHAVIORAL]: When eye button clicked at HintLevel=MEDIUM, HintAnswerCard renders with correct answer text. AC6 [BEHAVIORAL]: When eye button clicked at HintLevel=HARD, HintAnswerCard renders with correct answer text. AC7 [BEHAVIORAL]: Session transitions to paused state after eye button click (`isPaused = true`, `sessionActive = false`). | TrainingScreen, VerbDrillScreen, DailyPracticeScreen | `ui/screens/TrainingScreen.kt`, `ui/screens/VerbDrillScreen.kt`, `ui/DailyPracticeScreen.kt` | UI-CONSISTENCY-2025, VerbDrillScreen.kt:392-425 |
| UC-52 | Voice auto mode available in VerbDrill | VerbDrill selection screen is displayed | 1. User enables "Voice input (auto)" toggle on selection screen. 2. Session starts. 3. Mic auto-launches on each new card. 4. After correct voice answer, card auto-advances. | AC1: Voice auto toggle is visible on VerbDrill selection screen. AC2: Mic auto-launches on each new card when enabled. AC3: Auto-advance occurs after correct voice answer. AC4: Behavior matches VocabDrill voice auto pattern. AC5 [BEHAVIORAL]: onAutoStartVoice callback invokes speechLauncher.launch() directly (not just setInputMode). AC6 [BEHAVIORAL]: Speech recognition dialog appears within 1 second of new card display when voiceMode enabled. | VerbDrillScreen | `ui/screens/VerbDrillScreen.kt`, `ui/components/VoiceAutoLauncher.kt` | UI-CONSISTENCY-2025, VocabDrillScreen.kt:218-241, :409-417 |
| UC-53 | Report sheet has 4 options on all screens | Any training screen; user taps report/triangle icon | 1. Report bottom sheet opens. 2. Sheet shows exactly 4 options: Flag/Unflag, Hide card, Export, Copy. 3. All options function identically across screens. | AC1: Report sheet shows Flag/Unflag toggle. AC2: Report sheet shows "Hide card" option. AC3: Report sheet shows "Export bad sentences" option. AC4: Report sheet shows "Copy text" option. AC5: Visual layout is identical across TrainingScreen, VerbDrillScreen, DailyPracticeScreen. AC6 [BEHAVIORAL]: Flag action toggles isFlagged state on current card (adds/removes from BadSentenceStore). AC7 [BEHAVIORAL]: Hide card action removes card from session (except Daily Practice where it is a documented no-op). AC8 [BEHAVIORAL]: Export returns non-null string with flagged sentences when at least one card is flagged. AC9 [BEHAVIORAL]: Copy places card text (ID, source, target) in system clipboard. | TrainingScreen, VerbDrillScreen, DailyPracticeScreen | `ui/screens/TrainingScreen.kt`, `ui/screens/VerbDrillScreen.kt`, `ui/components/SharedReportSheet.kt` | UI-CONSISTENCY-2025, TrainingScreen.kt:479-563 |
| UC-54 | Mix Challenge tile not visible | HomeScreen is displayed | 1. User views lesson grid and drill tiles. 2. No Mix Challenge tile is shown anywhere on the screen. | AC1: HS-14 (Mix Challenge entry tile) is not rendered. AC2: No navigation to MIX_CHALLENGE screen is possible from UI. AC3: AppScreen.MIX_CHALLENGE enum value still exists (backward compat). | HomeScreen | `ui/screens/HomeScreen.kt` | UI-CONSISTENCY-2025 |
| UC-55 | Shared UI components exist for cross-screen consistency | N/A (build/structure requirement) | N/A (structural) | AC1: `ui/components/VoiceAutoLauncher.kt` exists as shared voice auto-launch composable. AC2: `ui/components/SharedReportSheet.kt` exists as shared 4-option report sheet. AC3: `ui/components/SharedInputModeBar.kt` exists as shared input mode bar with eye/mic/keyboard/report buttons. AC4 [BEHAVIORAL]: VoiceAutoLauncher callback chain matches VocabDrill reference: directly launches speechRecognizer, does NOT just switch inputMode. AC5 [BEHAVIORAL]: HintAnswerCard renders whenever hintAnswer != null, regardless of hintLevel setting. | N/A (shared components) | `ui/components/VoiceAutoLauncher.kt`, `ui/components/SharedReportSheet.kt`, `ui/components/SharedInputModeBar.kt` | UI-CONSISTENCY-2025 |

---

## Domain 13: Font Size Scaling

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-56 | Font size scaling applies to all training screens | App is running; user is on Settings screen | 1. User adjusts "Translation text size" slider in Settings (1.0x-2.0x range). 2. `ruTextScale` is persisted to `config.yaml` via `AppConfigStore.save()`. 3. User navigates to any training screen. 4. Prompt text renders at `(baseFontSize * ruTextScale).sp`. | AC1 [STRUCTURAL]: `ruTextScale` persists across app restarts (saved to `config.yaml`). AC2 [STRUCTURAL]: Slider range is 1.0x-2.0x with 5 steps (1.0, 1.25, 1.5, 1.75, 2.0). AC3 [BEHAVIORAL]: TrainingScreen prompt text scales by `ruTextScale` multiplier (no regression from current behavior). AC4 [BEHAVIORAL]: VerbDrillScreen `promptRu` scales by `ruTextScale` at all hint levels. AC5 [BEHAVIORAL]: VocabDrillScreen main word on front card scales by `ruTextScale`. AC6 [BEHAVIORAL]: DailyPracticeScreen applies `textScale` to all 3 block types (TRANSLATE, VOCAB, VERB). AC7 [BEHAVIORAL]: TrainingCardSession default slots use `textScale` from `CardSessionContract`. AC8 [BEHAVIORAL]: HomeScreen layout is NOT affected by `ruTextScale` changes (fixed sizes). AC9 [BEHAVIORAL]: Small labels (RU badge, POS badge, hint chips, attempt counter) are NOT scaled. AC10 [BEHAVIORAL]: At `textScale=2.0x`, all training screen prompts fit on screen without overflow or clipping. | TrainingScreen, VerbDrillScreen, VocabDrillScreen, DailyPracticeScreen | `ui/screens/TrainingScreen.kt`, `ui/screens/VerbDrillScreen.kt`, `ui/screens/VocabDrillScreen.kt`, `ui/DailyPracticeScreen.kt`, `data/AppConfigStore.kt`, `data/Models.kt` | font-scaling |

---

## Domain 14: Voice Auto-Start Toggle

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-57 | Voice auto-start toggle applies to all training screens | App is running; user is on Settings screen | 1. User toggles "Auto-start voice input" switch in Settings. 2. `voiceAutoStart` is persisted to `config.yaml` via `AppConfigStore.save()`. 3. User navigates to any training screen. 4. On new card entry with VOICE input mode, behavior follows toggle state. | AC1 [STRUCTURAL]: SettingsScreen has voice auto-start toggle (Switch). AC2 [BEHAVIORAL]: When toggle OFF + TrainingScreen new card in VOICE mode, no voice auto-launch occurs but mic button still works on manual click. AC3 [BEHAVIORAL]: When toggle OFF + DailyPracticeScreen new card in VOICE mode, no voice auto-launch occurs but mic button still works on manual click. AC4 [BEHAVIORAL]: When toggle ON + TrainingScreen new card in VOICE mode, voice auto-launches within 500ms. AC5 [BEHAVIORAL]: When toggle ON + DailyPracticeScreen new card in VOICE mode, voice auto-launches within 500ms. AC6 [BEHAVIORAL]: VerbDrillScreen uses global voiceAutoStart (no per-drill toggle). AC7 [BEHAVIORAL]: VocabDrillScreen uses global voiceAutoStart (no per-drill toggle). AC8 [BEHAVIORAL]: Auto-submit timing (keyboard exact match, voice auto-advance) is not affected by voice auto-start toggle. AC9 [BEHAVIORAL]: When voice auto-start OFF, mic button on any input mode bar still works and launches speech recognition on manual click. AC10 [BEHAVIORAL]: Mic button onClick directly launches speechLauncher.launch() — does NOT rely on voiceAutoStart-gated LaunchedEffect. Manual mic click works regardless of flag state. | TrainingScreen, DailyPracticeScreen, VerbDrillScreen, VocabDrillScreen, SettingsScreen | `data/Models.kt`, `data/AppConfigStore.kt`, `ui/TrainingViewModel.kt`, `ui/screens/TrainingScreen.kt`, `ui/DailyPracticeScreen.kt`, `ui/VerbDrillScreen.kt`, `ui/VocabDrillScreen.kt`, `ui/screens/SettingsScreen.kt`, `ui/GrammarMateApp.kt` | voice-auto-start |

---

## Domain 15: Verb/Tense Info Sheets

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-58 | Verb and tense info sheets accessible from all screens showing verb/tense chips | User is on a screen that displays verb/tense SuggestionChips (VerbDrillScreen or DailyPracticeScreen verbs block); hintLevel is not HARD | 1. User taps verb SuggestionChip. 2. VerbReferenceBottomSheet opens showing conjugation table for the current verb+tense, with TTS button to speak verb infinitive. 3. User taps tense SuggestionChip. 4. TenseInfoBottomSheet opens showing formula, usage (in Russian), and examples. 5. User taps outside sheet or presses back. 6. Sheet dismisses. | AC1 [STRUCTURAL]: VerbDrillScreen verb/tense chips are clickable (existing behavior, no regression). AC2 [BEHAVIORAL]: DailyPracticeScreen verb chip is clickable and opens VerbReferenceBottomSheet with conjugation data. AC3 [BEHAVIORAL]: DailyPracticeScreen tense chip is clickable and opens TenseInfoBottomSheet with formula/usage/examples. AC4 [BEHAVIORAL]: VerbReferenceBottomSheet shows conjugation forms for matching verb+tense cards. AC5 [BEHAVIORAL]: TenseInfoBottomSheet shows tense name, abbreviation, formula, usage in Russian, and examples when data is available; shows fallback message when data is unavailable. | VerbDrillScreen, DailyPracticeScreen | `ui/VerbDrillScreen.kt`, `ui/DailyPracticeScreen.kt`, `ui/components/VerbDrillSheets.kt`, `feature/daily/DailyPracticeSessionProvider.kt` | verb-tense-sheets |

---

## Domain 16: Performance (IO Dispatchers, Caching, Parallel Loading)

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-60 | Performance — training screens load without UI jank | User opens VerbDrill, DailyPractice, or any screen that reads CSV/YAML data | 1. VerbDrillViewModel.loadCards() runs file I/O on Dispatchers.IO. 2. DailySessionComposer builds 3 blocks in parallel via coroutineScope + async. 3. Lesson CSV data is parsed once per pack load. 4. Regex patterns are pre-compiled in companion/top-level objects. 5. State updates flow back to main thread for Compose. | AC1 [BEHAVIORAL]: VerbDrill loadCards() file I/O (CSV reading, YAML loading, asset reads) runs inside `withContext(Dispatchers.IO)`, never on main thread. AC2 [BEHAVIORAL]: DailyPractice loads 3 blocks (Translate, Vocab, Verbs) in parallel using `coroutineScope` + `async` in DailySessionComposer.buildSession(). AC3 [BEHAVIORAL]: Lesson CSV data is parsed only when needed (no re-read on subsequent calls within same session — caching deferred to future optimization). AC4 [BEHAVIORAL]: Store data is loaded on first access (no in-memory cache yet — deferred to future optimization). AC5 [STRUCTURAL]: Regex patterns (Normalizer: WHITESPACE_REGEX, DIACRITICAL_MARKS_REGEX, TIME_MINUTES_REGEX; VerbDrillCsvParser: PARENTHETICAL_VERB_REGEX) are compiled once at class load time, not per-call. | All training screens | `ui/VerbDrillViewModel.kt`, `feature/daily/DailySessionComposer.kt`, `data/Normalization.kt`, `data/VerbDrillCsvParser.kt` | performance |

---

## Domain 17: TTS Icon Auto-Recovery (Speaker Icon Updates After Model Download)

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-61 | TTS speaker icon updates automatically after model download completes | App is open; TTS models are downloading in background; TTS engine is in ERROR or IDLE state (because models were not yet available) | 1. Background download starts for missing TTS models. 2. User may tap TTS button before download completes, causing engine to enter ERROR state. 3. Download completes (DownloadState.Done). 4. AudioCoordinator detects newly completed download. 5. AudioCoordinator calls ttsEngine.initialize() for the downloaded language. 6. Engine transitions ERROR -> INITIALIZING -> READY. 7. startTtsStateCollection() propagates state to uiState.audio.ttsState. 8. TtsSpeakerButton recomposes with VolumeUp icon. | AC1 [BEHAVIORAL]: Speaker icon renders as VolumeUp (speaker) when TTS model is downloaded and engine is READY. AC2 [BEHAVIORAL]: Icon transitions from error/loading state to speaker icon within 2 seconds of model download completion, without requiring user action or screen re-entry. AC3 [BEHAVIORAL]: Both English and Italian models download in background on first launch; each completed download triggers engine initialization for its language. AC4 [BEHAVIORAL]: TTS speak() wrapped in try-catch. No crash when models not loaded. Silent skip if engine not ready. | All screens with TTS buttons (TrainingScreen, DailyPracticeScreen, VerbDrillScreen) | `shared/audio/AudioCoordinator.kt`, `data/TtsEngine.kt`, `ui/components/SharedComponents.kt` | tts-icon-fix |

---

## Domain 18: Progress Reset

| UC-ID | Use Case | Preconditions | Steps | Acceptance Criteria | Screen | Source files | Source |
|-------|----------|---------------|-------|---------------------|--------|--------------|--------|
| UC-62 | Reset progress for current language with confirmation | User is on Settings screen; at least one language pack is installed | 1. User taps "Reset progress" button (labeled with current language name). 2. Confirmation dialog appears showing language name and listing what will be cleared. 3. User confirms. 4. `resetLanguageProgress()` clears: mastery for selected language, drill progress for active pack, word mastery, daily session state, training session state. 5. Lessons are refreshed. | AC1 [BEHAVIORAL]: Reset only affects the currently selected language/pack. AC2 [STRUCTURAL]: Confirmation dialog shown before reset with language name. AC3 [BEHAVIORAL]: After reset, mastery counts return to 0 for all lessons in the pack. AC4 [BEHAVIORAL]: After reset, spaced repetition intervals cleared. AC5 [BEHAVIORAL]: After reset, flower states return to LOCKED/SEED for all lessons. AC6 [BEHAVIORAL]: After reset, verb drill progress cleared for the pack. AC7 [BEHAVIORAL]: After reset, vocab drill mastery cleared for the pack. AC8 [BEHAVIORAL]: Other language packs are NOT affected. | SettingsScreen | `ui/screens/SettingsScreen.kt`, `shared/SettingsActionHandler.kt`, `feature/progress/ProgressTracker.kt`, `ui/TrainingViewModel.kt` | progress-reset |

---

## Cross-Reference: Source to Use Case Mapping

| Source | UCs |
|--------|-----|
| scenario-01 (training flow) | UC-01, UC-02, UC-03, UC-04, UC-05, UC-06 |
| scenario-02 (answer validation) | UC-02, UC-03, UC-27, UC-48, UC-49 |
| scenario-03 (mastery & flower) | UC-07, UC-08, UC-09 |
| scenario-04 (spaced repetition) | UC-10, UC-11 |
| scenario-05 (input modes) | UC-12, UC-13, UC-14, UC-15, UC-16 |
| scenario-06 (daily practice) | UC-21, UC-22, UC-23, UC-24, UC-25 |
| scenario-07 (verb drill) | UC-26, UC-27, UC-28, UC-29, UC-50, UC-58, UC-59 |
| scenario-08 (vocab drill) | UC-30, UC-31, UC-32, UC-33, UC-34 |
| scenario-09 (boss battle) | UC-17, UC-18, UC-19, UC-20 |
| scenario-10 (pack drills) | UC-29, UC-34, UC-38, UC-39 |
| scenario-11 (navigation) | UC-35, UC-36, UC-37, UC-38 |
| scenario-12 (state persistence) | UC-20, UC-24, UC-45, UC-46, UC-47 |
| scenario-13 (pack import) | UC-39, UC-40, UC-41, UC-42 |
| scenario-14 (backup/restore) | UC-43, UC-44 |
| scenario-15 (onboarding) | UC-46 |
| 17-user-stories | All US-01 through US-79 referenced in UCs |
| performance | UC-60 |
| tts-icon-fix | UC-61 |
| progress-reset | UC-62 |


---


# 23. Screen Element Registry

## Summary

| Screen | Prefix | Element Count |
|--------|--------|---------------|
| HomeScreen | HS | 22 |
| TrainingScreen | TS | 37 |
| TrainingCardSession | TCS | 29 |
| DailyPracticeScreen | DP | 30 |
| VerbDrillScreen | VD | 41 |
| VocabDrillScreen | VOC | 48 |
| LessonRoadmapScreen | LR | 13 |
| LadderScreen | LS | 11 |
| SettingsScreen (SettingsSheet) | SS | 46 |
| StoryQuizScreen | SQ | 13 |
| GrammarMateApp Dialogs | DG | 17 |
| [UI-CONSISTENCY-2025] Shared Components | SH | 6 |
| **Total** | | **313** |

---

## 1. HomeScreen (ui/screens/HomeScreen.kt)

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Avatar circle | HS-01 | image | Always | 40dp CircleShape, primary background, shows user initials (max 2 chars, fallback "GM"). Clickable has no action (decorative). | ? |
| User name text | HS-02 | text | Always | SemiBold weight. Displays `state.navigation.userName`. | ? |
| Language selector | HS-03 | button | Always | TextButton showing uppercase language code (e.g. "IT"). Opens DropdownMenu with all available languages. Selecting a different language calls `onSelectLanguage(id)`. | ? |
| Settings gear icon | HS-04 | button | Always | IconButton with Settings icon. Calls `onOpenSettings()`. | ? |
| Primary action card | HS-05 | card | Always | Clickable Card showing active pack display name ("Continue Learning" / "Start learning") and lesson progress hint ("Lesson N. Exercise X/Y"). Calls `onPrimaryAction()`. | ? |
| "Grammar Roadmap" header | HS-06 | text | Always | SemiBold section header above the lesson tile grid. | ? |
| Lesson tile grid | HS-07 | card | Always (12 tiles) | 4-column LazyVerticalGrid of LessonTile cards (72dp height). Always shows 12 tiles; empty slots for packs with fewer lessons. | ? |
| Lesson tile index number | HS-08 | text | Per tile | SemiBold tile number (1-12). | ? |
| Lesson tile emoji | HS-09 | text | Per tile | Shows flower emoji based on state: LOCKED = lock, UNLOCKED = open lock, SEED/SPROUT/BLOOM = FlowerCalculator emoji, EMPTY = gray dot. | ? |
| Lesson tile mastery percent | HS-10 | text | `masteryPercent > 0` and not LOCKED/UNLOCKED/EMPTY | Shows "N%" in 10sp, 60% alpha. | ? |
| Verb Drill entry tile | HS-11 | card | `hasVerbDrill == true` | Card with FitnessCenter icon + "Verb Drill" label. 64dp height. Calls `onOpenVerbDrill()`. | ? |
| Vocab Drill entry tile | HS-12 | card | `hasVocabDrill == true` | Card with MenuBook icon + "Flashcards" label + mastered count badge ("N mastered" in green). Calls `onOpenVocabDrill()`. | ? |
| Daily Practice entry tile | HS-13 | card | Always | primaryContainer Card with "Daily Practice" title + "Practice all sub-lessons" subtitle + PlayArrow icon. Calls `onOpenElite()`. | ? |
| Mix Challenge entry tile | HS-14 | card | **HIDDEN** [UI-CONSISTENCY-2025] | DORMANT: tile is no longer rendered on HomeScreen. Blue-tinted Card (0xFFE3F2FD) with "Mix Challenge" title + "Interleaved practice across tenses" subtitle + SwapHoriz icon. Retained in registry for backward compat. | ? |
| Legend text | HS-15 | text | Always | Shows "Legend:" header + emoji meanings: seed, growing, bloom, wilting, wilted, forgotten. | ? |
| "How This Training Works" button | HS-16 | button | Always | OutlinedButton, full-width. Shows HowThisTrainingWorksDialog on tap. | ? |
| "Continue Learning" button | HS-17 | button | Always | Filled Button, full-width. Calls `onPrimaryAction()`. | ? |
| HowThisTrainingWorksDialog | HS-18 | dialog | `showMethod == true` | AlertDialog with title "How This Training Works", explanation text, "OK" button. | ? |
| LessonLockedDialog | HS-19 | dialog | `showLockedLessonHint == true` (tap EMPTY tile) | AlertDialog: "Lesson locked" title, "Please complete the previous lesson first.", "OK". | ? |
| EarlyStartDialog (lesson) | HS-20 | dialog | `earlyStartLessonId != null` (tap LOCKED tile with lessonId) | AlertDialog: "Start early?" title, "Yes"/"No" buttons. "Yes" calls `onSelectLesson(lessonId)`. | ? |
| Drill tiles row container | HS-21 | card | `hasVerbDrill \|\| hasVocabDrill` | Row containing VerbDrillEntryTile and VocabDrillEntryTile side by side (each weighted 1f). | ? |
| Locked tile clickable | HS-22 | button | `tile.state == LOCKED` and `tile.lessonId != null` | Opens EarlyStartDialog (HS-20). If `lessonId == null`, opens LessonLockedDialog (HS-19). | ? |

---

## 2. TrainingScreen (ui/screens/TrainingScreen.kt)

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Scaffold TopBar title | TS-01 | text | Always | "GrammarMate" in titleLarge, Bold. | ? |
| Settings gear (top bar) | TS-02 | button | Always | IconButton with Settings icon. Calls `onOpenSettings()` + `onShowSettings()`. | ? |
| Session header label | TS-03 | text | Conditionally | "Review Session" when `bossActive`, "Refresh Session" when `eliteActive`, green-tinted text in drill mode. | ? |
| Tense label | TS-04 | text | `card.tense` is not null/blank | 13sp SemiBold, primary color (or blue Surface for Mix Challenge). In drill mode: green (0xFF388E3C). | ? |
| Prompt text (header) | TS-05 | text | `currentCard != null` | Stripped prompt (parenthetical hints removed via regex), `(18f * ruTextScale).sp`, Medium weight. Green tint in drill mode. | UC-56 |
| DrillProgressRow (progress bar) | TS-06 | progress-bar | Always | Rounded green bar (70% width, #4CAF50 on #C8E6C9 track). "N / Total" text overlay. Text color flips dark-green-to-white at 12% fill. | ? |
| Speedometer (progress arc) | TS-07 | progress-bar | Always | Canvas arc (30% width, 44dp). Color: red (<=20 wpm), yellow (<=40 wpm), green (>40 wpm). Center shows numeric wpm. | ? |
| CardPrompt card | TS-08 | card | `currentCard != null` | Material Card with "RU" label + prompt text (`(20f * ruTextScale).sp`, SemiBold) + TtsSpeakerButton. | UC-56 |
| CardPrompt TTS button | TS-09 | button | `currentCard != null` | TtsSpeakerButton: 4 states (SPEAKING=StopCircle red, INITIALIZING=spinner, ERROR=ReportProblem red, IDLE=VolumeUp). Calls `onTtsSpeak()`. | ? |
| Answer text field | TS-10 | input-field | `hasCards == true` | OutlinedTextField "Your translation". Enabled only when `hasCards`. Auto-submits on exact match in KEYBOARD mode via Normalizer.isExactMatch. | ? |
| Mic trailing icon (text field) | TS-11 | button | `canLaunchVoice` | IconButton inside trailingIcon. Switches to VOICE mode + launches speech recognition. | ? |
| "No cards" error text | TS-12 | text | `hasCards == false` | Red error text. | ? |
| Voice mode hint | TS-13 | text | `inputMode == VOICE` and `sessionState == ACTIVE` | Muted text "Say translation: {promptRu}". | ? |
| ASR status indicator | TS-14 | text | `useOfflineAsr == true` | AsrStatusIndicator showing offline ASR state. | ? |
| Word bank instruction | TS-15 | text | `inputMode == WORD_BANK` and `wordBankWords.isNotEmpty()` | "Tap words in correct order:" label text. | ? |
| Word bank chips | TS-16 | chip | `inputMode == WORD_BANK` and `wordBankWords.isNotEmpty()` | FlowRow of FilterChips. Tracks duplicate counts per word. Fully-used words disabled. Calls `onSelectWordFromBank(word)`. | ? |
| Word bank "Selected" counter | TS-17 | text | `inputMode == WORD_BANK` and `selectedWords.isNotEmpty()` | "Selected: N / M" in primary color. | ? |
| Word bank "Undo" button | TS-18 | button | `inputMode == WORD_BANK` and `selectedWords.isNotEmpty()` | TextButton "Undo". Calls `onRemoveLastWord()`. | ? |
| Voice mode selector | TS-19 | button | `canLaunchVoice` | FilledTonalIconButton with Mic icon. Switches to VOICE mode. | ? |
| Keyboard mode selector | TS-20 | button | `canSelectInputMode` | FilledTonalIconButton with Keyboard icon. Switches to KEYBOARD mode. | ? |
| Word bank mode selector | TS-21 | button | `canSelectInputMode` | FilledTonalIconButton with LibraryBooks icon. Switches to WORD_BANK mode. | ? |
| Show answer button | TS-22 | button | `hasCards` | IconButton with Visibility icon + "Show answer" tooltip. Calls `onShowAnswer()`. | ? |
| Report button | TS-23 | button | `hasCards` | IconButton with ReportProblem icon + "Report sentence" tooltip. Opens report ModalBottomSheet. | ? |
| Current mode label | TS-24 | text | Always | "Voice" / "Keyboard" / "Word Bank" label text. | ? |
| Check button | TS-25 | button | `hasCards && inputText.isNotBlank() && sessionState == ACTIVE && currentCard != null` | Full-width Button "Check". Calls `onSubmit()`. | ? |
| Result label | TS-26 | text | `lastResult != null` | "Correct" (green #2E7D32) or "Incorrect" (red #C62828), Bold. | ? |
| Result TTS replay | TS-27 | button | `lastResult != null` and `answerText` not blank | TtsSpeakerButton. Replays answer TTS. | ? |
| Answer text | TS-28 | text | `answerText` not blank | "Answer: {answerText}" text. | ? |
| Navigation Prev button | TS-29 | button | `hasCards` | NavIconButton with ArrowBack. Calls `onPrev()`. | ? |
| Navigation Pause/Play | TS-30 | button | `hasCards` | NavIconButton: Pause icon when ACTIVE, Play icon otherwise. Calls `onTogglePause()`. | ? |
| Navigation Exit button | TS-31 | button | `hasCards` | NavIconButton with StopCircle icon. Calls `onRequestExit()` (triggers exit dialog). | ? |
| Navigation Next button | TS-32 | button | `hasCards` | NavIconButton with ArrowForward. Calls `onNext(false)`. | ? |
| Report bottom sheet | TS-33 | bottom-sheet | `showReportSheet == true` | ModalBottomSheet: card prompt text + flag/unflag bad sentence + hide card + export bad sentences + copy text. | ? |
| Export result dialog | TS-34 | dialog | `exportMessage != null` | AlertDialog showing export file path or "No bad sentences to export". | ? |
| Auto-voice LaunchedEffect | TS-35 | (system) | `inputMode == VOICE && sessionState == ACTIVE && currentCard != null` | Auto-launches speech recognition 200ms after card/mode change. | ? |
| Drill mode background | TS-36 | (visual) | `isDrillMode` | Scaffold containerColor set to green (0xFFE8F5E9). | ? |
| Mix Challenge tense chip | TS-37 | card | `isMixChallenge && card.tense` not blank | Blue Surface (0xFFE3F2FD) with bold tense text (14sp, #01565C0). | ? |

---

## 3. TrainingCardSession (ui/TrainingCardSession.kt) -- Reusable Component

These elements are the default slot implementations. Screens that use TrainingCardSession with custom slots override specific elements.

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Tense label (header) | TCS-01 | text | `currentCard` is VerbDrillCard with non-blank tense | 13sp SemiBold, primary color. Always visible regardless of HintLevel. | ? |
| Clean prompt text (header) | TCS-02 | text | `currentCard != null` | Stripped prompt (parentheticals removed), `(18f * textScale).sp`, Medium weight. | UC-56 |
| Progress bar | TCS-03 | progress-bar | Always | Rounded green bar (70% width). "N / Total" overlay. Text color flips at 12% fill. | ? |
| Speedometer arc | TCS-04 | progress-bar | Always | Canvas arc (30% width, 44dp). Red/yellow/green by wpm. Center shows numeric value. | ? |
| Card content | TCS-05 | card | `currentCard != null` | Material Card: "RU" label + prompt text (`(20f * textScale).sp` SemiBold) + TtsSpeakerButton. | UC-56 |
| Card TTS button | TCS-06 | button | `currentCard != null` | TtsSpeakerButton. Calls `contract.speakTts()`. | ? |
| Answer text field | TCS-07 | input-field | `currentCard != null` and not showing result | OutlinedTextField "Your translation". Auto-submits on exact match in KEYBOARD mode. | ? |
| Mic trailing icon | TCS-08 | button | `contract.supportsVoiceInput && hasCards` | IconButton. Switches to VOICE mode + launches speech recognition. | ? |
| "No cards" error | TCS-09 | text | `currentCard == null` and not complete | Red error text "No cards". | ? |
| Voice mode hint | TCS-10 | text | `currentInputMode == VOICE` | "Say translation: {prompt}" in muted text. | ? |
| Word bank chips | TCS-11 | chip | `currentInputMode == WORD_BANK && supportsWordBank` | FlowRow of FilterChips with duplicate tracking. Fully-used words disabled. | ? |
| Word bank counter | TCS-12 | text | Word bank has selected words | "Selected: N / M" in primary color. | ? |
| Word bank Undo | TCS-13 | button | Word bank has selected words | TextButton "Undo". Calls `contract.removeLastSelectedWord()`. | ? |
| Voice mode button | TCS-14 | button | `supportsVoiceInput && hasCards` | FilledTonalIconButton Mic. Switches to VOICE mode + launches recognition. | ? |
| Keyboard mode button | TCS-15 | button | `InputMode.KEYBOARD in availableModes && hasCards` | FilledTonalIconButton Keyboard. Switches to KEYBOARD mode. ALWAYS visible regardless of HintLevel. | ? |
| Word bank mode button | TCS-16 | button | `supportsWordBank && hasCards` | FilledTonalIconButton LibraryBooks. Switches to WORD_BANK mode. | ? |
| Show answer button | TCS-17 | button | `hasCards` | IconButton Visibility + "Show answer" tooltip. Calls `contract.showAnswer()`. | ? |
| Report button | TCS-18 | button | `supportsFlagging && hasCards` | IconButton ReportProblem + "Report sentence" tooltip. Opens report sheet. | ? |
| Current mode label | TCS-19 | text | Always | "Voice" / "Keyboard" / "Word Bank" label. | ? |
| Check button | TCS-20 | button | `inputText.isNotBlank() && hasCards` | Full-width "Check" button. Calls `scope.onSubmit()`. | ? |
| Correct/Incorrect result | TCS-21 | text | `isShowingResult` | "Correct" (green) or "Incorrect" (red) Bold text + TTS replay + "Answer: {displayAnswer}". | ? |
| Report bottom sheet | TCS-22 | bottom-sheet | `showReportSheet == true` | ModalBottomSheet: card prompt + flag/unflag + hide card + export + copy text. | ? |
| Navigation row | TCS-23 | button | `supportsNavigation` | Prev + Pause/Play (if `supportsPause`) + Exit + Next NavIconButtons. | ? |
| Exit confirmation dialog | TCS-24 | dialog | Exit button tapped | "End session? Your progress will be saved." with "End"/"Cancel". | ? |
| Completion screen | TCS-25 | card | `isComplete && !isShowingResult` | Party popper emoji (48sp) + "Well done!" (24sp Bold) + progress text + "Done" button. | ? |
| Progress text overlay | TCS-26 | text | Inside progress bar | "N / Total" in 12sp Bold. Color switches at 12% fill. | ? |
| Speed value text | TCS-27 | text | Inside speedometer | Numeric wpm value in 13sp Bold, colored by speed range. | ? |
| Result TTS replay button | TCS-28 | button | `isShowingResult && supportsTts` | TtsSpeakerButton in result section. Speaks `displayAnswer`. | ? |
| Export result dialog | TCS-29 | dialog | `exportMessage != null` | AlertDialog with export path or "No bad sentences to export". | ? |

---

## 4. DailyPracticeScreen (ui/DailyPracticeScreen.kt)

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Loading spinner | DP-01 | card | `!state.active \|\| currentTask == null` | CircularProgressIndicator + "Loading session..." text. | ? |
| Back button (header) | DP-02 | button | Always | IconButton ArrowBack. Opens exit confirmation dialog. | ? |
| "Daily Practice" title | DP-03 | text | Always | SemiBold, 18sp. | ? |
| Block type badge chip | DP-04 | card | Always | primaryContainer Card showing "Translation" / "Vocabulary" / "Verbs". | ? |
| Block progress bar | DP-05 | progress-bar | `totalTasks > 0` | LinearProgressIndicator (8dp height, rounded) + "N/M" label. Shows overall session position. | ? |
| Block sparkle overlay | DP-06 | card | `showBlockTransition == true` or session complete | Semi-transparent black overlay + sparkle emoji + "Next: {BlockType}" or "Daily practice complete!". Auto-dismisses after 800ms. | ? |
| TRANSLATE/VERBS card session | DP-07 | card | `currentTask.blockType == TRANSLATE \|\| VERBS` | Wraps TrainingCardSession via DailyPracticeSessionProvider. Includes card content, input controls, navigation. | ? |
| Card prompt (translate/verbs) | DP-08 | card | Card session active | Card with "RU" label + prompt (`(20f * ruTextScale).sp` SemiBold) + TTS button. | UC-56 |
| Verb/tense/group chips (verbs) | DP-09 | chip | `verbText` not blank | SuggestionChips for verb (with rank), tense (abbreviated), group. All chips are ALWAYS visible regardless of HintLevel -- they are reference data, not hints. Verb chip tap opens VerbReferenceBottomSheet with conjugation table. Tense chip tap opens TenseInfoBottomSheet with formula/usage/examples. Group chip is non-interactive (display only). | UC-58 |
| Card TTS button (translate/verbs) | DP-10 | button | Card session active | Inline TTS button (not TtsSpeakerButton): 4 states (SPEAKING/INITIALIZING/ERROR/IDLE). | ? |
| Hint answer card (translate/verbs) | DP-11 | card | `provider.hintAnswer != null && hintLevel == EASY` | Error-tinted Card showing "Answer: {hint}". Includes TTS replay button. | ? |
| Incorrect feedback (translate/verbs) | DP-12 | text | `provider.showIncorrectFeedback` | Red "Incorrect" + "N attempts left" text. | ? |
| Answer text field (translate/verbs) | DP-13 | input-field | Card session active | OutlinedTextField "Your translation". Auto-submits on exact match. | ? |
| Mic trailing icon (translate/verbs) | DP-14 | button | `canLaunchVoice` | Switches to VOICE mode. | ? |
| Word bank section (translate/verbs) | DP-15 | chip | `WORD_BANK mode` | DailyWordBankSection: chips + counter + Undo. | ? |
| Input mode bar (translate/verbs) | DP-16 | button | Card session active | DailyInputModeBar: Voice/Keyboard/WordBank buttons + Show answer + Report. Keyboard button is ALWAYS visible (not gated by HintLevel). | ? |
| Check button (translate/verbs) | DP-17 | button | `hasCards && inputText.isNotBlank() && sessionActive` | "Check" button. Submits via `provider.submitAnswerWithInput()`. | ? |
| Report sheet (translate/verbs) | DP-18 | bottom-sheet | `showReportSheet == true` | DailyReportSheet: flag/unflag + export + copy. | ? |
| VOCAB flashcard card | DP-19 | card | `currentTask.blockType == VOCAB` | surfaceVariant Card with prompt word (28sp Bold) + translation (18sp Medium, primary) + TTS button + report button. | ? |
| Vocab prompt text | DP-20 | text | VOCAB block active | Word text in `(28f * ruTextScale).sp` Bold, centered. Direction-dependent: IT_TO_RU shows Italian word, RU_TO_IT shows Russian meaning. | UC-56 |
| Vocab TTS button | DP-21 | button | VOCAB block active | IconButton VolumeUp. Calls `onSpeak(promptText)`. | ? |
| Vocab report button | DP-22 | button | VOCAB block active | IconButton ReportProblem. Opens DailyReportSheet. Tinted red if word is flagged. | ? |
| Vocab translation text | DP-23 | text | VOCAB block active | Translation text in `(18f * ruTextScale).sp` Medium, primary color. Always visible regardless of HintLevel. | UC-56 |
| Vocab "You said" text | DP-24 | text | `voiceRecognizedText != null` | "You said: \"{text}\"" in muted style. | ? |
| Vocab mic button | DP-25 | button | VOCAB block active | 64dp FilledTonalIconButton. Launches voice recognition with direction-appropriate language tag. | ? |
| Vocab rating buttons | DP-26 | button | VOCAB block active | 4 OutlinedButtons: Again (red), Hard (orange), Good (primary), Easy (green). Auto-advances on tap. | ? |
| Auto-voice effect (translate/verbs) | DP-27 | (system) | `inputMode == VOICE && sessionActive && currentCard != null` | LaunchedEffect triggers speech recognition after 200ms (1200ms after incorrect feedback). | ? |
| Auto-advance effect | DP-28 | (system) | `pendingAnswerResult.correct && inputMode == VOICE` | Auto-advances to next card after 400ms on correct voice answer. | ? |
| Completion screen | DP-29 | card | `state.finishedToken && !hasShownCompletionSparkle` | "Session Complete!" heading + description text + "Back to Home" button. | ? |
| Exit confirmation dialog | DP-30 | dialog | Back button tapped | "Exit practice?" + "Your progress in this session will be lost." + "Stay"/"Exit" buttons. | ? |

---

## 5. VerbDrillScreen (ui/VerbDrillScreen.kt)

### 5a. Selection Screen

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Back button (selection) | VD-01 | button | Always | IconButton ArrowBack. Calls `onBack()`. | ? |
| "Verb Drill" title (selection) | VD-02 | text | Always | SemiBold weight. | ? |
| Loading spinner | VD-03 | card | `state.isLoading` | CircularProgressIndicator + "Loading..." text. | ? |
| Tense dropdown | VD-04 | button | `availableTenses.isNotEmpty()` | TextButton showing selected tense or "All tenses". Opens DropdownMenu with "All" + individual tenses. Calls `onSelectTense(value)`. | ? |
| Group dropdown | VD-05 | button | `availableGroups.isNotEmpty()` | TextButton showing selected group or "All groups". Opens DropdownMenu with "All" + individual groups. Calls `onSelectGroup(value)`. | ? |
| "Sort by frequency" checkbox | VD-06 | toggle | Always | Checkbox + "Sort by frequency" text. Toggles `sortByFrequency` state. | ? |
| Progress stats | VD-07 | text | `totalCards > 0` | "Progress: X / Y" + "Today: N" (muted). | ? |
| Progress bar (selection) | VD-08 | progress-bar | `totalCards > 0` | LinearProgressIndicator showing everShownCount / totalCards. | ? |
| "All done today" text | VD-09 | text | `allDoneToday` | SemiBold 18sp centered text. | ? |
| Start/Continue button | VD-10 | button | `!allDoneToday && (totalCards > 0 \|\| tenses/groups available)` | "Start" if todayShownCount == 0, "Continue" otherwise. Calls `onStart()`. | ? |

### 5b. Active Session

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Back button (session) | VD-11 | button | Session active | IconButton ArrowBack. Calls `onExit()`. | ? |
| "Verb Drill" title (session) | VD-12 | text | Session active | SemiBold weight. | ? |
| Progress bar + speedometer | VD-13 | progress-bar | Session active | Reuses DefaultProgressIndicator from TrainingCardSession. | ? |
| Card prompt | VD-14 | card | `currentCard != null` | Card: "RU" label + prompt text (`(20f * ruTextScale).sp` SemiBold) + TTS VolumeUp button. | UC-56 |
| Verb SuggestionChip | VD-15 | chip | `verbText` not blank | Shows verb infinitive + "#rank". ChevronRight icon. Tap opens VerbReferenceBottomSheet. Always visible regardless of HintLevel. | UC-58 |
| Tense SuggestionChip | VD-16 | chip | `tense` not blank | Shows abbreviated tense name (e.g. "Pres."). Tap opens TenseInfoBottomSheet. Always visible regardless of HintLevel. | UC-58 |
| Hint answer card | VD-17 | card | `provider.hintAnswer != null && hintLevel == EASY` | Error-tinted Card "Answer: {hint}" + TTS replay (red-tinted). | ? |
| Incorrect feedback | VD-18 | text | `provider.showIncorrectFeedback` | Red "Incorrect" + "N attempts left" text. | ? |
| Answer text field | VD-19 | input-field | `hasCards` | OutlinedTextField "Your translation". Auto-submits on exact match. Typing clears incorrect feedback. | ? |
| Mic trailing icon | VD-20 | button | `canLaunchVoice` | Switches to VOICE mode. | ? |
| Voice mode hint | VD-21 | text | `inputMode == VOICE && sessionActive` | "Say translation: {prompt}" muted text. | ? |
| Word bank section | VD-22 | chip | `WORD_BANK mode` | VerbDrillWordBankSection: chips + counter + Undo. | ? |
| Voice mode button | VD-23 | button | `canLaunchVoice` | Sets input mode to VOICE (does NOT directly launch speech). | ? |
| Keyboard mode button | VD-24 | button | `canSelectInputMode` | Sets input mode to KEYBOARD. | ? |
| Word bank mode button | VD-25 | button | `canSelectInputMode` | Sets input mode to WORD_BANK. | ? |
| Show answer button | VD-26 | button | `hasCards && hintAnswer == null` | Visibility icon. Calls `contract.showAnswer()`. Disabled when hint already shown. Always visible regardless of HintLevel. | ? |
| Report button | VD-27 | button | `supportsFlagging && hasCards` | ReportProblem icon. Opens VerbDrillReportSheet. | ? |
| Mode label | VD-28 | text | Always | "Voice" / "Keyboard" / "Word Bank". | ? |
| Check button | VD-29 | button | `hasCards && inputText.isNotBlank() && sessionActive` | "Check". Uses `provider.submitAnswerWithInput()` for retry/hint flow. | ? |
| Auto-voice effect | VD-30 | (system) | `inputMode == VOICE && sessionActive && currentCard != null` | LaunchedEffect triggers speech recognition after 200ms. | ? |
| Auto-advance after voice correct | VD-31 | (system) | `pendingAnswerResult.correct && inputMode == VOICE` | Auto-advances after 500ms. | ? |
| Report bottom sheet | VD-32 | bottom-sheet | `showReportSheet == true` | VerbDrillReportSheet: flag/unflag + export + copy (no hide card option). | ? |
| VerbReferenceBottomSheet | VD-33 | bottom-sheet | `showVerbSheet == true` | Shows verb infinitive + TTS button + group + tense + conjugation table. | ? |
| TenseInfoBottomSheet | VD-34 | bottom-sheet | `showTenseSheet == true` | Shows tense name + formula Card + usage explanation + example cards. | ? |
| Export result dialog | VD-35 | dialog | `exportMessage != null` | AlertDialog with export path or "No bad sentences to export". | ? |
| Navigation row | VD-36 | button | Session active | DefaultNavigationControls from TrainingCardSession: Prev + Pause/Play + Exit + Next. | ? |

### 5c. Completion Screen

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Sparkle emoji | VD-37 | text | Session complete | 48sp party popper emoji. | ? |
| "Otlichno!" title | VD-38 | text | Session complete | Bold 24sp. | ? |
| Stats text | VD-39 | text | Session complete | "Pravilnykh: X \| Oshibok: Y" in muted color. | ? |
| "More" button | VD-40 | button | `!allDoneToday` | "Eshche" Button. Calls `viewModel.nextBatch()` to load 10 more cards. | ? |
| "Exit" button | VD-41 | button | Session complete | OutlinedButton "Vykhod". Calls `onExit()`. | ? |

---

## 6. VocabDrillScreen (ui/VocabDrillScreen.kt)

### 6a. Selection Screen

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Back button (selection) | VOC-01 | button | Always | IconButton ArrowBack. Calls `onBack()`. | ? |
| "Flashcards" title | VOC-02 | text | Always | SemiBold weight. | ? |
| Loading spinner | VOC-03 | card | `state.isLoading` | CircularProgressIndicator + "Loading..." text. | ? |
| "Direction" label | VOC-04 | text | Always | labelMedium "Direction". | ? |
| Direction filter "IT -> RU" | VOC-05 | chip | Always | FilterChip. Selected when `drillDirection == IT_TO_RU`. | ? |
| Direction filter "RU -> IT" | VOC-06 | chip | Always | FilterChip. Selected when `drillDirection == RU_TO_IT`. | ? |
| ~~Voice input toggle~~ | VOC-07 | (removed) | N/A | REMOVED. Voice auto-start is now controlled by the global Settings toggle (`AudioState.voiceAutoStart`). No per-drill toggle exists. | UC-57 |
| "Part of speech" label | VOC-08 | text | Always | labelMedium "Part of speech". | ? |
| POS "All" chip | VOC-09 | chip | Always | FilterChip. Selected when `selectedPos == null`. | ? |
| POS chips (per category) | VOC-10 | chip | Always (one per `availablePos`) | FilterChips: Nouns, Verbs, Adj., Adv., Numbers, etc. Selected when matching `selectedPos`. | ? |
| "Word frequency" label | VOC-11 | text | Always | labelMedium "Word frequency". | ? |
| Frequency chips | VOC-12 | chip | Always | FilterChips: Top 100, Top 500, Top 1000, All. Each maps to a rank range. | ? |
| Stats card | VOC-13 | card | `totalCount > 0` | primaryContainer Card: "Due: X / Y" + "Mastered: N words" + per-POS breakdown + progress bar. | ? |
| "No words loaded" text | VOC-14 | text | `totalCount == 0` | Muted centered text. | ? |
| Start button | VOC-15 | button | `dueCount > 0` | "Start (N due)". Disabled when no due words. | ? |

### 6b. Card Screen

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Back button (card) | VOC-16 | button | Session active | IconButton ArrowBack. Calls `onExit()`. | ? |
| "Flashcards" title (card) | VOC-17 | text | Session active | SemiBold weight. | ? |
| Card progress counter | VOC-18 | text | Session active | "N/M" in labelLarge, primary color. | ? |
| Report button (card header) | VOC-19 | button | Session active | IconButton ReportProblem. Tinted red if word is flagged. Opens report sheet. | ? |
| Card progress bar | VOC-20 | progress-bar | Session active | LinearProgressIndicator showing current/total. | ? |
| Card front container | VOC-21 | card | `!session.isFlipped` | RoundedCornerShape(16dp) Card. Background tint changes: green on correct voice, red on wrong, surfaceVariant default. | ? |
| POS badge | VOC-22 | card | Always (when card has POS) | Small rounded Card showing "noun"/"verb"/"adj." etc. Color-coded by POS. Always visible regardless of HintLevel. | ? |
| Rank badge | VOC-23 | card | Always (when card has rank) | Small rounded Card showing "#N" rank. Always visible regardless of HintLevel. | ? |
| Word text (front) | VOC-24 | text | `!session.isFlipped` | `(32f * ruTextScale).sp` Bold centered. Direction-dependent: IT_TO_RU shows Italian, RU_TO_IT shows Russian meaning. | UC-56 |
| TTS button (front) | VOC-25 | button | `direction == IT_TO_RU` | 4-state icon (SPEAKING/INITIALIZING/ERROR/IDLE). Calls `onSpeak(word)`. | ? |
| "Tap to speak" text | VOC-26 | text | `!voiceCompleted` | labelMedium muted text. | ? |
| Mic button (front, 72dp) | VOC-27 | button | `!voiceCompleted` | 72dp FilledTonalIconButton with 36dp Mic icon. Launches voice recognition. | ? |
| Voice result feedback card | VOC-28 | card | `voiceCompleted` | Shows recognized text (italic) + result: "Correct!" (green check), "Moving on..." (red, after max attempts), "Try again (N/3)" (red), "Skipped" (muted). | ? |
| Card back container | VOC-29 | card | `session.isFlipped` | secondaryContainer Card with POS badge + word + meaning + forms + collocations + mastery step. | ? |
| Word + TTS (back) | VOC-30 | button | `session.isFlipped` | Shows word text + TTS VolumeUp button. Layout differs by direction. | ? |
| Translation text (back) | VOC-31 | text | `session.isFlipped` | Direction-dependent: shows the "answer" side of the card. | ? |
| Forms table | VOC-32 | card | `session.isFlipped && forms.isNotEmpty()` | tertiaryContainer Card: "Forms" label + form items (m sg, f sg, m pl, f pl). POS-dependent form keys. Always visible regardless of HintLevel. | ? |
| Collocations list | VOC-33 | text | `session.isFlipped && collocations.isNotEmpty()` | Max 5 collocations shown. "+N more" if overflow. Always visible regardless of HintLevel. | ? |
| Mastery step indicator | VOC-34 | text | `session.isFlipped` | "Step X/9" or "Learned" (when step >= 3). Muted labelSmall. | ? |
| Skip button | VOC-35 | button | `!session.isFlipped` | OutlinedButton with SkipNext icon. Skips voice or flips card. | ? |
| Flip button | VOC-36 | button | `!session.isFlipped` | Filled Button with Flip icon. Calls `onFlip()`. | ? |
| "Again" rating button | VOC-37 | button | `session.isFlipped` | Error-colored OutlinedButton. Shows "Again" + "<1m" interval. Resets to step 0. | ? |
| "Hard" rating button | VOC-38 | button | `session.isFlipped` | Orange-colored OutlinedButton. Shows "Hard" + current step interval. Stays at same step. | ? |
| "Good" rating button | VOC-39 | button | `session.isFlipped` | Primary-colored Filled Button. Shows "Good" + next step interval. Advances +1 step. | ? |
| "Easy" rating button | VOC-40 | button | `session.isFlipped` | Green-colored Filled Button. Shows "Easy" + +2 step interval. Advances +2 steps. | ? |
| Report bottom sheet | VOC-41 | bottom-sheet | `showReportSheet == true` | ModalBottomSheet: "Word options" title + word text + flag/unflag + export + copy. | ? |
| Export result dialog | VOC-42 | dialog | `exportMessage != null` | AlertDialog with export result. | ? |
| Auto-flip on voice correct | VOC-43 | (system) | `voiceCompleted && voiceResult == CORRECT && !isFlipped` | Auto-flips card after 800ms delay. | ? |
| Auto-launch voice | VOC-44 | (system) | `voiceAutoStart (global) && !isFlipped && !voiceCompleted && !isVoiceActive` | Auto-launches voice recognition after 500ms delay. Uses global `voiceAutoStart` from Settings, NOT per-drill toggle. Mic button still works on manual click when voiceAutoStart is OFF. | UC-57 |

### 6c. Completion Screen

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| "Perfect!" / "Done!" title | VOC-45 | text | Session complete | Bold 28sp, primary color. "Perfect!" when all correct, "Done!" otherwise. | ? |
| Stats card | VOC-46 | card | `showStats == true` (800ms fade-in) | Correct count (primary, 32sp Bold) + Wrong count (error, 32sp Bold) + "N words reviewed". | ? |
| Exit button (completion) | VOC-47 | button | `showStats == true` | OutlinedButton "Exit". Returns to selection. | ? |
| Continue button (completion) | VOC-48 | button | `showStats == true` | Filled Button "Continue". Starts new session. | ? |

---

## 7. LessonRoadmapScreen (ui/screens/LessonRoadmapScreen.kt)

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Back button | LR-01 | button | Always | IconButton ArrowBack. Calls `onBack()`. | ? |
| Lesson title | LR-02 | text | Always | SemiBold weight. Shows selected lesson title or "Lesson" fallback. | ? |
| Progress bar | LR-03 | progress-bar | Always | LinearProgressIndicator showing completed/total. | ? |
| "Exercise X of Y" text | LR-04 | text | Always | Centered text showing current cycle position. Paginated in blocks of 15. | ? |
| "Cards: X of Y" text | LR-05 | text | Always | 12sp, 70% alpha. Shows shownCards/totalCards for current lesson. | ? |
| Sub-lesson grid | LR-06 | card | Always | 4-column LazyVerticalGrid with entries from `buildRoadmapEntries()`. userScrollEnabled=false. | ? |
| Training tile (exercise) | LR-07 | card | Per entry | Card (72dp) showing: index number, flower emoji (LOCKED/UNLOCKED/completed flower), type label ("NEW"/"MIX"). Clickable when `canEnter`. | ? |
| Drill tile | LR-08 | card | `hasDrill == true` | Card with FitnessCenter icon + "Drill" label (12sp). primaryContainer when enabled. Calls `onDrillStart()`. | ? |
| Boss "Review" tile | LR-09 | card | Always (per cycle) | Card showing "Review" label + trophy icon (colored by reward) or lock icon. Clickable when `bossUnlocked`. | ? |
| Boss "Mega" tile | LR-10 | card | `lessonIndex > 0` (per cycle) | Card showing "Mega" label + trophy icon (colored by reward) or lock icon. Clickable when `bossUnlocked`. | ? |
| "Start Lesson" / "Continue Lesson" button | LR-11 | button | Always | Full-width Button. "Start Lesson" when completed==0, "Continue Lesson" otherwise. Calls `onStartSubLesson(currentIndex)`. | ? |
| Early start dialog (sub-lesson) | LR-12 | dialog | `earlyStartSubLessonIndex != null` | "Start exercise N early?" with "Yes"/"No". | ? |
| Boss locked dialog | LR-13 | dialog | `bossLockedMessage != null` | "Locked" title + "Complete at least 15 exercises first." + "OK". | ? |

---

## 8. LadderScreen (ui/screens/LadderScreen.kt)

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Back button | LS-01 | button | Always | IconButton ArrowBack. Calls `onBack()`. Returns to caller screen. | ? |
| Title "Lestnitsa intervalov" | LS-02 | text | Always | titleLarge SemiBold. | ? |
| Subtitle "Vse uroki tekushchego paketa" | LS-03 | text | Always | bodySmall, 65% alpha. | ? |
| Empty state text | LS-04 | text | `ladderRows.isEmpty()` | "Net dannykh po urokam" muted text. Early return. | ? |
| Header row | LS-05 | text | `ladderRows.isNotEmpty()` | Column headers: #, Urok, Karty, Dney, Interval in labelMedium. | ? |
| Ladder row card | LS-06 | card | Per row in `ladderRows` | RoundedCornerShape(14dp) Card. Shows: index, title (ellipsized), uniqueCardShows, daysSinceLastShow, intervalLabel. Overdue rows use errorContainer background. | ? |
| Row index text | LS-07 | text | Per row | titleSmall weight. | ? |
| Row title text | LS-08 | text | Per row | bodyMedium, single line, ellipsized. | ? |
| Row cards count | LS-09 | text | Per row | "-" if null, otherwise numeric. | ? |
| Row days count | LS-10 | text | Per row | "-" if null, otherwise numeric. | ? |
| Row interval label | LS-11 | text | Per row | "-" if null. Overdue starts with "Prosrochka". | ? |

---

## 9. SettingsScreen / SettingsSheet (ui/screens/SettingsScreen.kt)

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| ModalBottomSheet container | SS-01 | bottom-sheet | `show == true` | ModalBottomSheet with dismiss callback. On dismiss from TRAINING: calls `resumeFromSettings()` if card is active. | ? |
| "Service Mode" section header | SS-02 | text | Always | titleMedium SemiBold. | ? |
| Test Mode switch | SS-03 | toggle | Always | Switch. Toggles test mode. | ? |
| Test Mode description | SS-04 | text | Always | "Enables all lessons, accepts all answers, unlocks Elite mode" in bodySmall, 60% alpha. | ? |
| "Show Ladder" button | SS-05 | button | Always | OutlinedButton with Insights icon. Closes sheet + navigates to LADDER. | ? |
| "Difficulty" section header | SS-06 | text | Always | titleMedium SemiBold. | ? |
| Hint level chips (Easy/Medium/Hard) | SS-07 | chip | Always | 3 FilterChips. EASY: "All hints visible", MEDIUM: "Partial hints", HARD: "No hints". Each weighted 1f. | ? |
| Hint level description | SS-08 | text | Always | Dynamic description based on current hintLevel. bodySmall, 60% alpha. | ? |
| Vocab Sprint limit field | SS-09 | input-field | Always | OutlinedTextField "Vocabulary Sprint limit". Digits only. "0 = all words". | ? |
| Vocab limit description | SS-10 | text | Always | "Set how many words to show (0 = all words)" bodySmall, 60% alpha. | ? |
| "Pronunciation speed" header | SS-11 | text | Always | titleMedium SemiBold. | ? |
| TTS speed slider | SS-12 | toggle | Always | Slider 0.5x-1.5x, 3 steps. Calls `onSetTtsSpeed`. | ? |
| TTS speed value display | SS-13 | text | Always | "0.XXx" formatted, centered. | ? |
| "Voice recognition" header | SS-14 | text | Always | titleMedium SemiBold. | ? |
| Offline ASR switch | SS-15 | toggle | Always | Switch. When enabled without model, triggers ASR download. | ? |
| ASR download progress bar | SS-16 | progress-bar | `asrDownloadState == Downloading \|\| Extracting` | LinearProgressIndicator + percentage text. | ? |
| ASR download error text | SS-17 | text | `asrDownloadState == Error` | Error message in error color. | ? |
| ASR status text | SS-18 | text | `asrDownloadState == Idle/Ready/Done` | "Using on-device recognition" / "Model not downloaded" / "Using Google speech recognition". | ? |
| "Translation text size" header | SS-19 | text | Always | titleMedium SemiBold. | ? |
| Text scale slider | SS-20 | toggle | Always | Slider 1.0x-2.0x, 3 steps. Calls `onSetRuTextScale`. | ? |
| Text scale value display | SS-21 | text | Always | "0.0x" formatted, centered. | ? |
| Language dropdown | SS-22 | button | Always | DropdownSelector "Language". Shows current language. Changing reloads lessons and resets active pack. | ? |
| Pack dropdown | SS-23 | button | Always | DropdownSelector "Pack". Shows current pack. Filters packs by selected language. | ? |
| "New language" text field | SS-24 | input-field | Always | OutlinedTextField "New language". | ? |
| "Add language" button | SS-25 | button | Always | OutlinedButton. Adds language if non-empty. Clears field. | ? |
| "Import lesson pack (ZIP)" button | SS-26 | button | Always | OutlinedButton with Upload icon. Opens file picker for ZIP files. | ? |
| "Import lesson (CSV)" button | SS-27 | button | Always | OutlinedButton with Upload icon. Opens file picker for CSV files. | ? |
| "Reset/Reload" button | SS-28 | button | Always | OutlinedButton with Upload icon. Opens file picker, clears + imports. | ? |
| "Empty lesson" text field | SS-29 | input-field | Always | OutlinedTextField "Empty lesson (title)". | ? |
| "Create empty lesson" button | SS-30 | button | Always | OutlinedButton. Creates lesson if title non-empty. Clears field. | ? |
| "Delete all lessons" button | SS-31 | button | Always | Red OutlinedButton with Delete icon. Calls `onDeleteAllLessons()`. | ? |
| "Reset progress" button | SS-32 | button | Always | Red OutlinedButton with Refresh icon. Label shows current language name: "Сбросить прогресс ({languageName})". Tapping opens confirmation dialog. | UC-62 |
| Reset progress confirmation dialog | SS-32a | dialog | User tapped SS-32 | AlertDialog with title "Сбросить прогресс", body listing what will be cleared, "Сбросить" confirm button (red), "Отмена" dismiss button. Calls `onResetAllProgress()` on confirm. | UC-62 |
| CSV format text | SS-33 | text | Always | "CSV format" header + format explanation (UTF-8, semicolon delimiter, example). | ? |
| Instructions text | SS-34 | text | Always | "Instructions" header + usage instructions. | ? |
| "Packs" header | SS-35 | text | Always | labelLarge "Packs". | ? |
| Pack list row | SS-36 | card | Per installed pack | Row: pack name (weighted) + Delete IconButton. | ? |
| "No installed packs" text | SS-37 | text | `installedPacks.isEmpty()` | bodySmall placeholder. | ? |
| "Profile" header | SS-38 | text | Always | titleMedium SemiBold. | ? |
| User name field | SS-39 | input-field | Always | OutlinedTextField "Your Name", 50 char limit, single line. | ? |
| "Save Name" button | SS-40 | button | `trimmed name != current name && trimmed.isNotEmpty()` | Filled Button. Calls `onUpdateUserName()`. | ? |
| "Backup & Restore" header | SS-41 | text | Always | titleMedium SemiBold. | ? |
| Restore description | SS-42 | text | Always | "Restore progress from backup folder (Downloads/BaseGrammy)". | ? |
| "Save progress now" button | SS-43 | button | Always | OutlinedButton with Upload icon. Calls `onSaveProgress()`. | ? |
| "Restore from backup" button | SS-44 | button | Always | OutlinedButton with Download icon. Opens folder picker. | ? |
| App info footer | SS-45 | text | Always | "GrammarMate" + version + tagline + description. Centered. | ? |

---

## 10. StoryQuizScreen (ui/screens/StoryQuizScreen.kt)

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| Phase title | SQ-01 | text | `story != null` | "Story Check-in" or "Story Check-out" (SemiBold). | ? |
| Story text | SQ-02 | text | `story != null` | bodyMedium. Shows `story.text`. | ? |
| Question counter | SQ-03 | text | `story != null` | "Question X / Y". | ? |
| Question prompt | SQ-04 | text | `story != null` | SemiBold. Shows `question.prompt`. | ? |
| Option row | SQ-05 | button | Per option | Clickable Row with ">" indicator when selected. Shows option text + "(correct)" / "(your choice)" suffixes when result shown. | ? |
| Correct answer text | SQ-06 | text | `showResult` | Primary color. "Correct: {optionText}". | ? |
| Error message | SQ-07 | text | `errorMessage != null` | Red text. "Select an answer" or "Incorrect". | ? |
| Prev button | SQ-08 | button | `questionIndex > 0` | OutlinedButton "Prev". Goes to previous question. | ? |
| Check button | SQ-09 | button | Always | Filled Button "Check". Validates selection. Shows error if none selected. Records correct/incorrect. | ? |
| Next/Finish button | SQ-10 | button | Always | Filled Button. Shows "Finish" on last question, "Next" otherwise. Validates first. On last: calls `onComplete()`. | ? |
| "Scroll to continue" hint | SQ-11 | text | `scrollState.maxValue > 0` | bodySmall muted centered text. | ? |
| Auto-close (null story) | SQ-12 | (system) | `story == null` | Immediately calls `onClose()`. | ? |
| Auto-complete (empty questions) | SQ-13 | (system) | `story.questions.isEmpty()` | Immediately calls `onComplete(true)`. | ? |

---

## 11. GrammarMateApp Dialogs (ui/GrammarMateApp.kt + ui/components/)

These dialogs are rendered as persistent overlays and can appear on any screen.

| Element | ID | Type | Visible when | Behavior / Invariant | Related UC |
|---------|----|------|-------------|----------------------|------------|
| TTS background progress bar | DG-01 | progress-bar | `bgTtsDownloading == true` | 2dp LinearProgressIndicator at top of all content. Aggregates progress from all language downloads. Persists across screen changes. | ? |
| WelcomeDialog | DG-02 | dialog | `userName == "GrammarMateUser"` (first launch) | "Welcome to GrammarMate!" + "What's your name?" + OutlinedTextField (50 char, single line, Done IME) + "Skip" + "Continue". Cannot dismiss by tapping outside. Blank input treated as "GrammarMateUser". | ? |
| StreakDialog | DG-03 | dialog | `streakMessage != null` | "??" icon text (48sp) + "Streak!" title + streakMessage (titleMedium, centered) + "Longest streak: N days" (only when longestStreak > currentStreak) + "Continue" button. | ? |
| BossRewardDialog | DG-04 | dialog | `bossRewardMessage != null && bossReward != null` | Trophy icon (EmojiEvents) colored by reward type (bronze #CD7F32, silver #C0C0C0, gold #FFD700) + "Boss Reward" title + rewardMessage + "OK" button. | ? |
| BossErrorDialog | DG-05 | dialog | `bossErrorMessage != null` | "Boss" title + error message text + "OK" button. | ? |
| StoryErrorDialog | DG-06 | dialog | `storyErrorMessage != null` | "Story" title + error message text + "OK" button. | ? |
| DrillStartDialog | DG-07 | dialog | `drillShowStartDialog == true` (tap DrillTile) | "Drill Mode" title + resume/fresh message. If progress exists: "Continue" (resume) + "Start Fresh". If no progress: "Start" only. Always has "Cancel". | ? |
| ExitConfirmationDialog | DG-08 | dialog | Back gesture or Stop during TRAINING/DAILY_PRACTICE | Title "End session?" / "Exit practice?". DAILY_PRACTICE: calls `cancelDailySession()`, navigates HOME. Boss: calls `finishBoss()`, navigates LESSON. Drill: calls `exitDrillMode()`, navigates LESSON. Normal: calls `finishSession()`, navigates LESSON. | ? |
| TtsDownloadDialog | DG-09 | dialog | `showTtsDownloadDialog == true` (TTS tap without model) | "Download pronunciation model?" title. Dynamic content: Idle shows size, Downloading/Extracting shows progress bar + %, Done shows "ready!", Error shows failure. "Download"/"OK"/"Cancel" buttons. Auto-closes on completion and auto-plays TTS. | ? |
| MeteredNetworkDialog (TTS) | DG-10 | dialog | `ttsMeteredNetwork == true` (TTS download on metered connection) | "Metered network detected" title + "~346 MB" warning + "Download anyway" + "Cancel". | ? |
| AsrMeteredNetworkDialog | DG-11 | dialog | `asrMeteredNetwork == true` (ASR download on metered connection) | "Metered network detected" title + "~375 MB" warning + "Download anyway" + "Cancel". | ? |
| DailyResumeDialog | DG-12 | dialog | Tap Daily Practice with resumable session | "Ezhednevnaya praktika" title + "Repeat = same cards, Continue = new cards" + "Repeat" (dismiss) + "Continue" (confirm). | ? |
| DailyPracticeLoadingOverlay | DG-13 | dialog | `isLoadingDaily == true` | Card with CircularProgressIndicator + "Loading session..." text. Blocking, auto-dismissed when initialization completes. | ? |
| HowThisTrainingWorksDialog | DG-14 | dialog | Tap "How This Training Works" on HomeScreen | Title + "GrammarMate builds automatic grammar patterns with repeated retrieval..." + "OK". | ? |
| LessonLockedDialog | DG-15 | dialog | Tap EMPTY lesson tile on HomeScreen | "Lesson locked" + "Please complete the previous lesson first." + "OK". | ? |
| EarlyStartDialog (Home/Lesson) | DG-16 | dialog | Tap locked lesson or sub-lesson tile | "Start early?" + "Start this lesson/exercise early? You can always come back..." + "Yes" + "No". | ? |
| ExportBadSentencesResultDialog | DG-17 | dialog | After exporting bad sentences from report sheet | "Export" title + file path or "No bad sentences to export" + "OK". | ? |

---

## 12. [UI-CONSISTENCY-2025] Shared Components (ui/components/)

These shared composables enforce cross-screen UI consistency. Each is used by 2+ screens.

| Element | ID | Type | Used by | Behavior / Invariant | Related UC |
|---------|----|------|---------|----------------------|------------|
| SharedReportSheet | SH-01 | bottom-sheet | TrainingScreen, VerbDrillScreen, DailyPracticeScreen | ModalBottomSheet with exactly 4 options: Flag/Unflag, Hide card, Export bad sentences, Copy text. Card prompt text shown at top for context. | UC-53 |

**Behavior:** Each option triggers its corresponding callback. Flag toggles card.isFlagged (adds/removes from BadSentenceStore). Hide removes card from session (except Daily Practice where it is a documented no-op). Export returns formatted string via BadSentenceStore.exportUnified() -- non-null when at least one card is flagged. Copy writes card text (ID, source, target) to system clipboard.

| VoiceAutoLauncher | SH-02 | (system) | VerbDrillScreen, VocabDrillScreen | LaunchedEffect composable that auto-launches voice recognition after configurable delay (200ms for new card, 1200ms after incorrect feedback). | UC-52 |

**Behavior:** When enabled and card changes, fires onAutoStartVoice after delay. Callback MUST call speechLauncher.launch(intent) directly -- switching InputMode alone is insufficient and causes the voice-not-launching bug. On correct voice answer, auto-advance triggers after 400-500ms.

| SharedInputModeBar | SH-03 | button | TrainingScreen, VerbDrillScreen, DailyPracticeScreen | Row of FilledTonalIconButtons: Mic, Keyboard, WordBank + Eye (show answer) + Report. Active mode highlighted. Mode label displayed below. | UC-51, UC-53 |

**Behavior:** Mode buttons switch input method via onModeChange callback. Eye button calls onShowHint() which sets hintAnswer on the provider and pauses the session. Report button opens SharedReportSheet. Eye button is disabled when hintShown == true.

| HintAnswerCard | SH-04 | card | TrainingScreen, VerbDrillScreen, DailyPracticeScreen | Pink Card with `errorContainer.copy(alpha = 0.3f)` background, red error-colored answer text, inline TTS replay button. Reference: VerbDrillScreen.kt:392-425. | UC-51 |

**Behavior:** Renders whenever hintAnswer != null. NOT gated by HintLevel -- eye mode shows answer at ALL difficulty levels (EASY, MEDIUM, HARD). HintLevel only controls parenthetical hints in prompt text, not the show-answer mechanism.

| TextScaleProvider | SH-05 | (system) | TrainingScreen, VerbDrillScreen, VocabDrillScreen, DailyPracticeScreen, TrainingCardSession | N/A (no visual element -- scales existing text). Multiplies base font sizes by textScale value (1.0-2.0). Applied to: prompt text, word displays, answer text. NOT applied to: navigation, badges, buttons, small labels. | UC-56 |

**Behavior:** Propagated via `CardSessionContract.textScale` or composable parameter. Reads `ruTextScale` from `AudioState` / `AppConfigStore`. Applied as `fontSize = (baseSize * textScale).sp`. Elements excluded from scaling: RU badge, POS badge, hint chips, attempt counter, tense labels, navigation buttons, progress bar text, rating button text, block label badges.

| VoiceAutoStartToggle | SH-06 | switch | SettingsScreen | Switch with label "Auto-start voice input". When ON, shows description "Voice recognition starts automatically when a new card appears". When OFF, shows "Voice recognition starts only when you tap the microphone". Bound to `state.audio.voiceAutoStart` via `onSetVoiceAutoStart` callback. | UC-57 |


---


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

### 7.6 Voice Auto-Start Behavioral Contract

**Field:** `AudioState.voiceAutoStart: Boolean` (default `true`)

**Persistence:** Saved to `config.yaml` via `AppConfig.voiceAutoStart`. Loaded on init.

**Semantics:** The flag controls ONLY automatic launch of speech recognition on new cards. It does NOT disable the microphone button. When OFF, the mic button on the input mode bar still works and launches speech recognition on manual click. When ON, speech recognition auto-launches on new cards.

**Behavioral rules:**

| Screen | When `voiceAutoStart = true` | When `voiceAutoStart = false` |
|--------|------------------------------|-------------------------------|
| TrainingScreen | LaunchedEffect fires on new card + VOICE mode + ACTIVE state | LaunchedEffect does NOT fire `speechLauncher.launch()` |
| DailyPracticeScreen | LaunchedEffect fires on new card + VOICE mode + sessionActive | LaunchedEffect does NOT fire `speechLauncher.launch()` |
| VerbDrillScreen | Uses global `voiceAutoStart` (passed as parameter) | Uses global `voiceAutoStart` (passed as parameter) |
| VocabDrillScreen | Uses global `voiceAutoStart` (passed as parameter) | Uses global `voiceAutoStart` (passed as parameter) |

**No per-drill overrides:** VerbDrillScreen and VocabDrillScreen no longer have their own `voiceModeEnabled` toggle. The global Settings toggle is the sole control.

**Invariant:** Auto-submit timing (keyboard exact match, voice auto-advance) is NOT affected by this flag. The flag only controls whether the speech recognition intent is launched automatically on card entry. The mic button in the input mode bar ALWAYS works regardless of this flag.

**Manual mic button click:** Mic button onClick handlers directly call `speechLauncher.launch()` and do NOT depend on the LaunchedEffect. The LaunchedEffect handles ONLY auto-launch on new card.

**Guard pattern in LaunchedEffect:**
```kotlin
if (state.audio.voiceAutoStart &&
    state.cardSession.inputMode == InputMode.VOICE &&
    state.cardSession.sessionState == SessionState.ACTIVE &&
    state.cardSession.currentCard != null
) {
    // auto-launch voice recognition
}
```

**Method:** `setVoiceAutoStart(enabled: Boolean)` in TrainingViewModel updates `AudioState.voiceAutoStart` and persists to `AppConfigStore`.


---


# 10. Verb Drill System -- Specification

This section covers the Verb Drill system: a dedicated conjugation practice mode that drills Italian verb forms by group and tense, independent from the standard lesson/sub-lesson training flow.

---

## 10.1 Overview

### 10.1.1 What Verb Drill Is

Verb Drill is a focused conjugation practice mode that presents the user with Russian prompts and expects Italian verb conjugation answers. Unlike regular lesson training, which practices full sentence translations across mixed grammar patterns, Verb Drill isolates verb morphology: each card targets a specific verb, tense, and conjugation group.

The system supports:

- **Filtering by tense** (Presente, Imperfetto, Passato Prossimo, etc.)
- **Filtering by conjugation group** (regular -are, irregular unique, etc.)
- **Three input modes**: Voice recognition, keyboard typing, and word bank
- **Retry/hint flow**: 3 attempts before auto-showing the answer
- **Verb reference sheets**: conjugation tables available per card via bottom sheets
- **Tense reference info**: formula, usage description, and examples via bottom sheets
- **Bad sentence flagging**: mark problematic cards for review/export

### 10.1.2 How It Differs from Regular Lesson Training

| Aspect | Regular Lesson Training | Verb Drill |
|--------|------------------------|------------|
| ViewModel | `TrainingViewModel` (single, all business logic) | `VerbDrillViewModel` (separate, isolated) |
| Card source | Lesson CSV files (2-column: `ru;answers`) | Verb drill CSV files (5+ column: `RU;IT;Verb;Tense;Group;Rank`) |
| Session composition | Sub-lessons with `NEW_ONLY` / `MIXED` types | 10-card batches filtered by tense/group |
| Progress tracking | Per-lesson mastery (unique card shows), flower state | Per-combo (group+tense) card IDs shown, ever and today |
| Screen | `TrainingScreen` via `GrammarMateApp` routing | `VerbDrillScreen` (own selection + session screens) |
| Navigation | Roadmap lesson tiles | Dedicated `VERB_DRILL` tile on roadmap + HomeScreen drill tile |
| Daily integration | Block 1 of Daily Practice | Block 3 of Daily Practice (via `DailySessionComposer`) |

### 10.1.3 Architectural Isolation

Verb Drill was designed as an **isolated feature** (Approach B from the design spec): it has its own ViewModel, screen, data classes, store, and parser. It does not modify `TrainingViewModel` at all. Integration with the rest of the app occurs at two points:

1. **Roadmap/HomeScreen**: a dedicated tile that navigates to `VerbDrillScreen`
2. **Daily Practice**: Block 3 reuses `VerbDrillCard` data and the retry/hint flow pattern via `DailyPracticeSessionProvider`

---

## 10.2 Verb Drill Data Model

### 10.2.1 VerbDrillCard

**File:** `app/src/main/java/com/alexpo/grammermate/data/VerbDrillCard.kt`

```kotlin
data class VerbDrillCard(
    override val id: String,
    override val promptRu: String,
    val answer: String,
    val verb: String? = null,
    val tense: String? = null,
    val group: String? = null,
    val rank: Int? = null
) : SessionCard {
    override val acceptedAnswers: List<String> get() = listOf(answer)
}
```

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | `String` | Yes | Deterministic identifier: `"{group}_{tense}_{rowIndex}"` (empty strings substituted for null group/tense). Example: `"regular_are_Presente_0"`, `"irregular_unique_Imperfetto_5"` |
| `promptRu` | `String` | Yes | Russian-language prompt shown to the user. May contain parenthetical verb hints like `"я готов (essere stanco)"` |
| `answer` | `String` | Yes | The expected Italian target answer. Treated as a single accepted answer (no `+` separator like regular lesson CSVs) |
| `verb` | `String?` | No | The infinitive form of the verb being tested (e.g., `"essere"`, `"avere"`). Falls back to extraction from parenthetical hints in `promptRu` when the CSV column is absent |
| `tense` | `String?` | No | Grammatical tense name (e.g., `"Presente"`, `"Imperfetto"`, `"Passato Prossimo"`) |
| `group` | `String?` | No | Conjugation group classification (e.g., `"regular_are"`, `"irregular_unique"`, `"regular_isc"`) |
| `rank` | `Int?` | No | Frequency rank for sorting by frequency. Lower rank = more common verb |

**SessionCard interface implementation:**

`VerbDrillCard` implements `SessionCard`, making it usable in both `VerbDrillCardSessionProvider` (standalone drill) and `DailyPracticeSessionProvider` (daily practice block 3). The `acceptedAnswers` property returns a single-element list containing `answer`.

### 10.2.2 Verb, Tense, Group Classification

**Verb Groups** (Italian conjugation classification):

| Group ID | Name | Type | Endings | Approximate Card Count |
|----------|------|------|---------|----------------------|
| `regular_are` | Правильные -are | regular | -o, -i, -a, -iamo, -ate, -ano | 412 |
| `regular_ere` | Правильные -ere | regular | -o, -i, -e, -iamo, -ete, -ono | 163 |
| `regular_ire` | Правильные -ire | regular | -o, -i, -e, -iamo, -ite, -ono | 32 |
| `regular_isc` | Правильные -isc (ire) | regular | -isco, -isci, -isce, -iamo, -ite, -iscono | 57 |
| `regular_ciare_giare` | -ciare/-giare (i-drop) | regular | -o, -i, -a... (no doubling) | 69 |
| `regular_care_gare` | -care/-gare (h-insert) | regular | -co to -chi, -go to -ghi | 64 |
| `irregular_porre` | Неправильные (porre) | irregular | pon- stem | 9 |
| `irregular_tenere` | Неправильные (tenere) | irregular | ten-/tin- stem | 9 |
| `irregular_venire` | Неправильные (venire) | irregular | ven-/venn- stem | 7 |
| `irregular_cogliere` | Неправильные (cogliere) | irregular | cogl-/colg- stem | 6 |
| `irregular_dire` | Неправильные (dire) | irregular | dic- stem | 5 |
| `irregular_trarre` | Неправильные (trarre) | irregular | tragg- stem | 4 |
| `irregular_unique` | Уникальные/мелкие группы | irregular | mixed | 34 |

**Tenses** (Italian grammatical tenses used in the drill):

Presente, Imperfetto, Passato Prossimo, Passato Remoto, Trapassato Prossimo, Futuro Semplice, Futuro Anteriore, Condizionale Presente, Condizionale Passato, Congiuntivo Presente, Congiuntivo Imperfetto, Congiuntivo Passato.

The tense ladder maps lesson levels (1-12) to cumulative tenses unlocked:

| Lesson Level | Tenses Unlocked |
|-------------|-----------------|
| 1 | Presente |
| 2 | + Imperfetto |
| 3 | + Passato Prossimo |
| 4 | + Futuro Semplice |
| 5 | + Condizionale Presente |
| 6 | + Passato Remoto |
| 7 | + Congiuntivo Presente |
| 8 | + Trapassato Prossimo |
| 9 | + Futuro Anteriore |
| 10 | + Congiuntivo Imperfetto |
| 11 | + Condizionale Passato |
| 12 | + Congiuntivo Passato |

### 10.2.3 VerbDrillComboProgress

Tracks cumulative progress for a specific group+tense combination.

```kotlin
data class VerbDrillComboProgress(
    val group: String,
    val tense: String,
    val totalCards: Int,
    val everShownCardIds: Set<String> = emptySet(),
    val todayShownCardIds: Set<String> = emptySet(),
    val lastDate: String = ""
)
```

| Field | Description |
|-------|-------------|
| `group` | Group filter (empty string means "all groups") |
| `tense` | Tense filter (empty string means "all tenses") |
| `totalCards` | Total number of cards matching this combo |
| `everShownCardIds` | Set of card IDs that have ever been shown. Permanent -- never resets |
| `todayShownCardIds` | Set of card IDs shown today. Resets when `lastDate` differs from current date |
| `lastDate` | ISO date string (e.g., `"2026-05-09"`) for daily reset detection |

### 10.2.4 VerbDrillSessionState

Represents an active drill session.

```kotlin
data class VerbDrillSessionState(
    val cards: List<VerbDrillCard>,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val isComplete: Boolean = false
)
```

| Field | Description |
|-------|-------------|
| `cards` | The 10 cards selected for this session batch |
| `currentIndex` | Index of the current card being shown (0-based) |
| `correctCount` | Number of cards answered correctly |
| `incorrectCount` | Number of cards where hint was shown (3 wrong attempts or manual eye) |
| `isComplete` | `true` when `currentIndex` reaches `cards.size` |

### 10.2.5 VerbDrillUiState

The full UI state exposed by `VerbDrillViewModel` as a `StateFlow`.

```kotlin
data class VerbDrillUiState(
    val availableTenses: List<String> = emptyList(),
    val availableGroups: List<String> = emptyList(),
    val selectedTense: String? = null,
    val selectedGroup: String? = null,
    val totalCards: Int = 0,
    val everShownCount: Int = 0,
    val todayShownCount: Int = 0,
    val session: VerbDrillSessionState? = null,
    val allDoneToday: Boolean = false,
    val isLoading: Boolean = true,
    val loadedLanguageId: String? = null,
    val badSentenceCount: Int = 0,
    val currentCardIsBad: Boolean = false,
    val sortByFrequency: Boolean = false
)
```

### 10.2.6 VerbDrillCsvParser

**File:** `app/src/main/java/com/alexpo/grammermate/data/VerbDrillCsvParser.kt`

**Object:** `VerbDrillCsvParser` (singleton)

**Public method:** `fun parse(content: String): Pair<String?, List<VerbDrillCard>>`

Returns a pair of (optional title string, list of parsed cards).

### 10.2.7 CSV Format

Verb drill CSV files use a semicolon-delimited format with an optional title line:

```
Verb Conjugation Drill B1 Italian
RU;IT;Verb;Tense;Group;Rank
я готов;Io sono pronto.;essere;Presente;irregular_unique;1
ты был готов;Tu eri pronto.;essere;Imperfetto;irregular_unique;2
```

**Parsing rules:**

1. **Title line**: The first non-blank line is treated as a title and skipped for card parsing. A title consists of letters, digits, and spaces only, up to 160 characters. If the first line contains semicolons, it is treated as a header instead.
2. **Header line**: The first non-blank, non-title line. Column indices are mapped by matching lowercase column names: `ru`, `it`, `verb`, `tense`, `group`, `rank`.
3. **Required columns**: `RU` and `IT` are mandatory. If either column index is not found in the header, all data rows are skipped.
4. **Optional columns**: `Verb`, `Tense`, `Group`, `Rank`. If absent, the corresponding `VerbDrillCard` fields are `null`.
5. **Data rows**: Each subsequent non-blank line is parsed as a data row. Semicolons inside quoted fields are preserved (not treated as delimiters). Fields are trimmed of whitespace and surrounding quotes.
6. **Blank fields**: Rows where `RU` or `IT` is blank after trimming are skipped.
7. **Verb fallback**: If the `Verb` column is absent or empty, the parser attempts to extract the verb from a parenthetical hint in `promptRu`. Pattern: `\((\w+)` -- captures the first word inside parentheses. Example: `"я устал (essere stanco)"` yields verb `"essere"`.
8. **Deterministic IDs**: Generated as `"{group}_{tense}_{rowIndex}"` where null values are replaced with empty strings. Example: `"regular_are_Presente_0"`, `"_Presente_3"` (no group), `"__5"` (no group or tense).
9. **Rank parsing**: The `Rank` column is parsed as an integer. If parsing fails, `rank` is `null`.

**Semicolon parsing algorithm:**

The parser implements its own semicolon-splitting logic (not reusing `CsvParser.parseLine`):
- Iterates character by character
- Tracks quote state (toggled by `"`)
- Semicolons inside quotes are treated as literal characters
- All other semicolons are delimiters

### 10.2.8 SessionCard Interface

**File:** `app/src/main/java/com/alexpo/grammermate/data/CardSessionContract.kt`

Both `VerbDrillCard` and `SentenceCard` implement the `SessionCard` interface:

```kotlin
interface SessionCard {
    val id: String
    val promptRu: String
    val acceptedAnswers: List<String>
}
```

This allows both card types to be used interchangeably in `CardSessionContract` implementations, enabling code reuse between Verb Drill and Daily Practice.

---

## 10.3 Verb Drill Store

### 10.3.1 Purpose

`VerbDrillStore` persists verb drill progress to a YAML file, tracking which cards have been shown (ever and today) for each group+tense combination.

**File:** `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt`

### 10.3.2 File Location

Progress files are **pack-scoped**:

| Scenario | File Path |
|----------|-----------|
| Pack-scoped (preferred) | `context.filesDir/grammarmate/drills/{packId}/verb_drill_progress.yaml` |
| Legacy global (no packId) | `context.filesDir/grammarmate/verb_drill_progress.yaml` |

The constructor takes an optional `packId` parameter. When provided, all progress is scoped to that pack. When `null`, a global file is used for backward compatibility.

### 10.3.3 YAML Schema

```yaml
schemaVersion: 1
data:
  regular_are|Presente:
    group: regular_are
    tense: Presente
    totalCards: 412
    everShownCardIds:
      - regular_are_Presente_0
      - regular_are_Presente_1
      - regular_are_Presente_15
    todayShownCardIds:
      - regular_are_Presente_0
      - regular_are_Presente_1
    lastDate: "2026-05-09"
  "|Presente":
    group: ""
    tense: Presente
    totalCards: 247
    everShownCardIds: []
    todayShownCardIds: []
    lastDate: "2026-05-09"
```

**Schema details:**

- `schemaVersion`: integer, currently `1`
- `data`: map keyed by `"{group}|{tense}"` combo string
  - Empty group or tense is represented by empty string (e.g., `"|Presente"`, `"|"`)
  - `everShownCardIds`: list of card ID strings (permanent progress)
  - `todayShownCardIds`: list of card ID strings (daily pool)
  - `lastDate`: ISO date string for daily reset detection

### 10.3.4 Daily Reset

On `loadProgress()`, the store compares each entry's `lastDate` with the current date (`java.time.LocalDate.now().toString()`):

- If `lastDate != today`: clears `todayShownCardIds` to `emptySet()` and updates `lastDate` to today
- If `lastDate == today`: preserves `todayShownCardIds` as-is

This provides a **lazy daily reset** without any alarm, timer, or background process.

### 10.3.5 Public API

| Method | Signature | Description |
|--------|-----------|-------------|
| `loadProgress` | `fun loadProgress(): Map<String, VerbDrillComboProgress>` | Reads the YAML file and returns all combo progress entries. Performs daily reset on load. Returns empty map if file does not exist. |
| `saveProgress` | `fun saveProgress(progress: Map<String, VerbDrillComboProgress>)` | Serializes the full progress map to YAML and writes via `AtomicFileWriter`. |
| `getComboProgress` | `fun getComboProgress(key: String): VerbDrillComboProgress?` | Convenience getter for a single combo. Delegates to `loadProgress()`. |
| `upsertComboProgress` | `fun upsertComboProgress(key: String, progress: VerbDrillComboProgress)` | Updates a single combo entry. Loads all progress, updates the entry, saves via `AtomicFileWriter`. |
| `loadAllCardsForPack` | `fun loadAllCardsForPack(targetPackId: String, languageId: String): List<VerbDrillCard>` | Reads all verb drill CSV files for a given pack and language from `grammarmate/drills/{packId}/verb_drill/`. Filters files by `{languageId}_` prefix and `.csv` extension. Returns parsed cards. |
| `getCardsForTenses` | `fun getCardsForTenses(packId: String, languageId: String, tenses: List<String>): List<VerbDrillCard>` | Returns all cards from a pack filtered by the given tense list. Returns empty if tenses is empty. |

### 10.3.6 Atomic Writes

All file writes go through `AtomicFileWriter.writeText(file, yaml.dump(data))`, following the project-wide rule that no store may write files directly.

### 10.3.7 Pack-Scoped Drill File Storage

After lesson pack import, verb drill CSV files are stored at:

```
context.filesDir/grammarmate/drills/{packId}/verb_drill/{languageId}_{lessonId}.csv
```

Files are filtered by `{languageId}_` prefix and `.csv` extension when loading.

---

## 10.4 VerbDrillCardSessionProvider

### 10.4.1 Purpose

`VerbDrillCardSessionProvider` is an adapter that wraps `VerbDrillViewModel` to implement the `CardSessionContract` interface. This allows the Verb Drill session to use the shared `TrainingCardSession` composable, which expects a `CardSessionContract` provider.

**File:** `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillCardSessionProvider.kt`

### 10.4.2 Design Rationale

The contract expects `submitAnswer()` to return a result while keeping the card visible for feedback, then `nextCard()` to advance to the next card. This adapter tracks the submitted card locally in Compose `mutableStateOf` fields so the UI recomposes when the result changes.

A fresh instance is created each time a session starts (via Compose `remember`).

### 10.4.3 Retry/Hint Flow

The provider implements a 3-attempt retry mechanism:

```
Card displayed
    |
    v
User submits answer
    |
    +---> Correct? --> Set pendingCard + pendingAnswerResult = CORRECT
    |                      (auto-advance in VOICE mode after 500ms delay)
    |
    +---> Wrong? --> Attempt counter++
                      |
                      +---> Attempts < 3?
                      |       |
                      |       +---> Yes: show "Incorrect" inline feedback
                      |              (auto-re-trigger voice recognition in VOICE mode)
                      |              User can retry
                      |
                      +---> No: auto-show answer as hint
                              (hintAnswer set, input controls stay visible, isPaused = true)
                              User presses Play/Continue to advance
```

**Manual "Show Answer" (eye button):** Immediately shows the answer as a hint, sets `incorrectAttempts = 3`, and pauses the session. The input controls remain visible.

**After hint is shown:** The user presses the Play button, which calls `togglePause()`. Since `isPaused == true`, this calls `nextCard()` to advance to the next card.

### 10.4.4 State Fields (Compose Observable)

| Field | Type | Description |
|-------|------|-------------|
| `pendingCard` | `VerbDrillCard?` | The card that was submitted and is showing feedback. `null` when no feedback is displayed. |
| `pendingAnswerResult` | `AnswerResult?` | The result from the last correct submission. `null` until a correct answer or hint. |
| `hintAnswer` | `String?` | When non-null, the answer is being shown as a hint. Input controls remain visible below the hint text. |
| `showIncorrectFeedback` | `Boolean` | When `true`, shows red "Incorrect" text with remaining attempts. |
| `remainingAttempts` | `Int` | Number of attempts remaining before auto-hint (starts at 3, decrements on wrong answers). |
| `incorrectAttempts` | `Int` | Consecutive wrong attempts for the current card. |
| `voiceTriggerToken` | `Int` | Incremented to trigger automatic voice recognition. Observed by `LaunchedEffect` in the UI. |

### 10.4.5 Card Session Contract Implementation

**Capabilities:**

| Capability | Value | Notes |
|------------|-------|-------|
| `supportsTts` | `true` | Uses Sherpa-ONNX TTS engine |
| `supportsVoiceInput` | `true` | Android speech recognition |
| `supportsWordBank` | `true` | Generates word bank from answer + distractors |
| `supportsFlagging` | `true` | Bad sentence flagging per card |
| `supportsNavigation` | `true` | Previous/next card navigation |
| `supportsPause` | `true` | Pause/play for hint-to-advance flow |

**Key contract properties:**

- `currentCard`: Returns `pendingCard` if feedback is showing, otherwise `session.cards[currentIndex]`, or `null` if session is complete.
- `progress`: Returns `SessionProgress(current = currentIndex + 1, total = cards.size)`.
- `isComplete`: `true` when `session.isComplete && pendingCard == null`.
- `sessionActive`: `false` when paused, hint is showing, or session is complete.
- `inputModeConfig`: All three modes available (VOICE, KEYBOARD, WORD_BANK), default VOICE, no input mode buttons shown in default config.

### 10.4.6 Word Bank Generation

Word bank words are generated per card and cached:

1. Split the card's answer into words by whitespace
2. Collect distractor words from other cards in the session (words not present in the current answer)
3. Take up to `max(0, 8 - answerWords.size)` distractors
4. Shuffle the combined list (answer words + distractors)
5. Cache by card ID to avoid regeneration on recomposition

### 10.4.7 Key Methods

| Method | Description |
|--------|-------------|
| `submitAnswerWithInput(input: String)` | Primary submission method. Normalizes input via `Normalizer.normalize()`, compares against `card.answer`. Handles retry/hint flow. |
| `nextCard()` | Clears all pending state, advances ViewModel index. Delegates to `viewModel.submitCorrectAnswer()` (correct), `viewModel.markCardCompleted()` (hint), or `viewModel.nextCardManual()` (skip). Auto-triggers voice recognition in VOICE mode. |
| `prevCard()` | Navigates to previous card, clears all pending state. |
| `showAnswer()` | Shows answer as hint (eye button). Sets `isPaused = true`. |
| `togglePause()` | If paused: calls `nextCard()` to advance. If active: pauses the session. |
| `clearIncorrectFeedback()` | Called when user starts typing a new attempt after incorrect feedback. |
| `setInputMode(mode)` | Switches input mode, clears selected words, auto-triggers voice in VOICE mode. |

---

## 10.5 VerbDrillViewModel

### 10.5.1 Purpose

`VerbDrillViewModel` is an `AndroidViewModel` that owns all verb drill business logic. It is completely separate from `TrainingViewModel` (architectural isolation -- does not modify the single-ViewModel pattern because it is a different ViewModel).

**File:** `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt`

### 10.5.2 State Management

The ViewModel exposes a single `StateFlow<VerbDrillUiState>`:

```kotlin
private val _uiState = MutableStateFlow(VerbDrillUiState())
val uiState: StateFlow<VerbDrillUiState> = _uiState
```

Internal state:

| Field | Type | Description |
|-------|------|-------------|
| `allCards` | `List<VerbDrillCard>` | All parsed cards from all verb drill CSV files (loaded once, kept in memory) |
| `progressMap` | `Map<String, VerbDrillComboProgress>` | In-memory copy of progress, keyed by combo string |
| `currentPackId` | `String?` | Active pack ID for pack-scoped loading. `null` for legacy global mode. |
| `packIdForCardId` | `Map<String, String>` | Maps each card ID to its source pack ID (for bad sentence scoping) |
| `activePackIds` | `Set<String>` | All pack IDs that have verb drill cards loaded |
| `tenseInfoMap` | `Map<String, TenseInfo>` | Tense reference information loaded from YAML assets |

### 10.5.3 Initialization and Loading

**`init` block:**

Sets `isLoading = true` and launches a coroutine to call `loadCards()`.

**`loadCards(languageId?)` flow:**

1. Resolve language ID from parameter or `ProgressStore.load().languageId`
2. Get verb drill CSV files from `LessonStore.getVerbDrillFiles(packId, languageId)` or legacy `getVerbDrillFiles(languageId)`
3. For each file: read content, parse with `VerbDrillCsvParser.parse()`, map each card to its pack ID
4. Accumulate all cards, build `packIdForCardId` mapping, collect `activePackIds`
5. Extract distinct `tense` values (sorted) and `group` values (sorted) for dropdown population
6. Load progress from `VerbDrillStore.loadProgress()`
7. Sum bad sentence counts across all active packs
8. Update UI state with available tenses/groups, loading complete
9. Load tense reference info from `grammarmate/tenses/{languageId}_tenses.yaml`

**`reloadForLanguage(languageId)`**: Skips reload if language unchanged and cards already loaded.

**`reloadForPack(packId)`**: Creates a new `VerbDrillStore` scoped to the pack, then reloads cards. Skips if pack unchanged and cards already loaded.

### 10.5.4 Card Advancement

**`submitCorrectAnswer()`**:

1. Validates session is active and index is in bounds
2. Gets current card at `session.currentIndex`
3. Increments `correctCount`, advances `currentIndex` by 1
4. Sets `isComplete = true` if next index exceeds cards size
5. Updates UI state with new session, checks next card's bad sentence status
6. Persists card progress via `persistCardProgress(card)`
7. Updates speed tracking timestamp for next card
8. Updates progress display

**`markCardCompleted()`**:

Same flow as `submitCorrectAnswer()` but increments `incorrectCount` instead of `correctCount`. Used when a card is "done" but not answered correctly (hint shown or skip).

**`nextCardManual()`**:

Advances to the next card without marking correct or incorrect. Persists the current card as shown (adds to today shown IDs). If at the last card, persists it and starts a new session batch.

**`prevCard()`**:

Moves to the previous card, sets `isComplete = false`. Does not persist progress.

### 10.5.5 Answer Validation

Answer checking is performed in `VerbDrillCardSessionProvider.submitAnswerWithInput()`, not in the ViewModel directly:

```kotlin
val isCorrect = Normalizer.normalize(input) == Normalizer.normalize(card.answer)
```

**Normalization algorithm** (from `Normalizer.normalize()`):

1. Trim whitespace, collapse multiple spaces to single
2. Remove time suffixes (`HH:MM` becomes `H`)
3. Lowercase
4. Strip all punctuation except hyphens
5. Collapse whitespace again, trim

Each `VerbDrillCard` has exactly one accepted answer (`acceptedAnswers` returns `listOf(answer)`), unlike regular lesson cards which may have multiple accepted answers separated by `+`.

### 10.5.6 Input Mode Handling

Three input modes are supported:

| Mode | Behavior |
|------|----------|
| `VOICE` | Default mode. Auto-launches speech recognition when card appears. On correct answer, auto-advances after 500ms delay. On wrong answer, auto-re-triggers speech recognition. |
| `KEYBOARD` | Manual text input with "Check" button. No auto-advance. |
| `WORD_BANK` | Tap words in correct order. Selected words shown as chips. "Check" button validates the joined word string. |

### 10.5.7 Progress Persistence

**`persistCardProgress(card)`**:

1. Constructs combo key from selected group and tense: `"{group}|{tense}"`
2. Gets existing progress for this combo
3. Adds card ID to `everShownCardIds` (union)
4. Adds card ID to `todayShownCardIds` (union)
5. Calculates total cards for the current filter
6. Creates updated `VerbDrillComboProgress`
7. Updates in-memory `progressMap`
8. Calls `verbDrillStore.upsertComboProgress(comboKey, updatedProgress)`

Progress is persisted **immediately** after each card is completed, not batched at end of session.

### 10.5.8 Speed Tracking

The ViewModel tracks typing/speaking speed:

| Field | Description |
|-------|-------------|
| `cardShownTimestamp` | `System.currentTimeMillis()` when card is displayed |
| `totalAnswerTimeMs` | Cumulative milliseconds spent answering |
| `totalAnswersForSpeed` | Number of answers timed |
| `currentSpeedWpm` | Computed: `(totalAnswersForSpeed / (totalAnswerTimeMs / 60000.0)).toInt()` |

**`markCardShown()`**: Records `cardShownTimestamp`.

**`recordAnswerTime()`**: Adds elapsed time since `cardShownTimestamp` to `totalAnswerTimeMs`, increments `totalAnswersForSpeed`, resets timestamp.

### 10.5.9 Bad Sentence Support

Verb drill has full flag/unflag/export support for bad cards. Flagged cards are persisted in `BadSentenceStore` pack-scoped, with `mode = "verb_drill"`.

**Methods:**

| Method | Description |
|--------|-------------|
| `flagBadSentence()` | Adds current card to `BadSentenceStore` for its pack (resolved via `packIdForCardId` map). Sets `mode = "verb_drill"`, `sentence = card.promptRu`, `translation = card.answer`. Updates `badSentenceCount` and `currentCardIsBad` in UI state. |
| `unflagBadSentence()` | Removes current card from `BadSentenceStore`. Updates `badSentenceCount` and sets `currentCardIsBad = false`. |
| `isBadSentence()` | Returns `currentCardIsBad` from UI state (tracked per card advancement). |
| `exportBadSentences()` | Calls `BadSentenceStore.exportUnified()` which exports all flagged cards across all packs. Returns file path or `null` if no bad sentences exist. |
| `isCardBad(card)` | Private. Checks `BadSentenceStore.isBadSentence(packId, cardId)` for the card's pack-scoped entry. Called on card advancement to update `currentCardIsBad`. |

**Mode field:** `"verb_drill"` -- stored in `BadSentenceEntry.mode` to identify verb drill as the source.

**Report sheet options (from VerbDrillSession UI):**

- **"Add to bad sentences list"** (or "Remove from bad sentences list" if already flagged)
- **"Export bad sentences to file"** -- triggers `exportBadSentences()` and shows the exported file path
- **"Copy text"** -- copies card ID, source, and target to clipboard

The "Hide this card from lessons" option is visible but is a no-op for verb drill (not supported -- see discrepancy report R5).

### 10.5.10 Tense Reference Information

**Data classes:**

```kotlin
data class TenseInfo(
    val name: String,       // Full tense name: "Passato Prossimo"
    val short: String,      // Abbreviation: "P. Pross."
    val formula: String,    // Formation formula
    val usageRu: String,    // Usage explanation in Russian
    val examples: List<TenseExample>
)

data class TenseExample(
    val it: String,   // Italian example sentence
    val ru: String,   // Russian translation
    val note: String  // Usage note
)
```

**Source**: `assets/grammarmate/tenses/{languageId}_tenses.yaml`

**`loadTenseInfo(languageId)`**: Reads YAML from assets, parses into `Map<String, TenseInfo>` keyed by full tense name. Falls back to empty map on error.

**`getTenseInfo(tenseName)`**: Returns `TenseInfo?` for the given tense name.

### 10.5.11 Verb Reference

**`getConjugationForVerb(verb, tense)`**: Returns all cards from the current session matching the given verb+tense pair, sorted by original order in the session. Used to populate the verb reference bottom sheet with a conjugation table.

**`speakVerbInfinitive(verb)`**: Speaks the verb infinitive via TTS at 0.8x speed (slower than normal for clarity).

### 10.5.12 TTS Support

| Method | Description |
|--------|-------------|
| `speakTts(text, speed)` | Speaks text via Sherpa-ONNX TTS. Initializes engine if needed. Default speed 0.67. |
| `stopTts()` | Stops current TTS playback. |
| `ttsState` | `StateFlow<TtsState>` from the TTS engine. |

TTS is initialized lazily on first use, checking if the active language matches the current `languageId`. Language mapping: `"it"` -> `"it-IT"`.

---

## 10.6 VerbDrillScreen (UI)

### 10.6.1 Screen Architecture

**File:** `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillScreen.kt`

The `VerbDrillScreen` composable serves as the entry point and switches between two sub-screens:

1. **Selection screen** (`VerbDrillSelectionScreen`): shown when `state.session == null`
2. **Session screen** (`VerbDrillSessionWithCardSession`): shown when `state.session != null`

```kotlin
@Composable
fun VerbDrillScreen(
    viewModel: VerbDrillViewModel,
    onBack: () -> Unit
)
```

**Loading state**: When `state.isLoading == true`, shows a centered `CircularProgressIndicator` with "Loading..." text.

### 10.6.2 Selection Screen (VerbDrillSelectionScreen)

**Layout:**

```
+----------------------------------+
|  [<-]  Verb Drill                |
|                                  |
|  Время:                          |
|  [Presente            ]          |  <-- TenseDropdown (only if tenses exist)
|                                  |
|  Группа:                         |
|  [regular_are         ]          |  <-- GroupDropdown (only if groups exist)
|                                  |
|  [x] По частотности             |  <-- Checkbox toggle
|                                  |
|  Прогресс: 247 / 412            |  <-- everShownCount / totalCards
|  Сегодня: 30                     |  <-- todayShownCount
|  ████████░░░░░░░░░ 60%          |  <-- LinearProgressIndicator
|                                  |
|  [       Старт / Продолжить  ]   |  <-- Start or Continue button
|                                  |
+----------------------------------+
```

**Components:**

- **Back arrow**: navigates to previous screen
- **TenseDropdown**: shown only when `state.availableTenses` is non-empty. Options: "Все времена" (all tenses, value `null`) + each available tense. Selected value stored in `state.selectedTense`.
- **GroupDropdown**: shown only when `state.availableGroups` is non-empty. Options: "Все группы" (all groups, value `null`) + each available group. Selected value stored in `state.selectedGroup`.
- **Sort by frequency checkbox**: toggles `state.sortByFrequency`. When enabled, session cards are sorted by `rank` (ascending) instead of shuffled randomly.
- **Progress display**: shows `everShownCount / totalCards` and today count for the selected combo.
- **Start/Continue button**: text is "Продолжить" if `todayShownCount > 0`, otherwise "Старт". Calls `viewModel.startSession()`.
- **All done message**: when `state.allDoneToday == true`, shows "На сегодня всё!" instead of the start button.

### 10.6.3 Session Screen (VerbDrillSessionWithCardSession)

The session screen uses the shared `TrainingCardSession` composable with VerbDrill-specific customizations.

**Card display (cardContent lambda):**

```
+----------------------------------+
|  RU                    [Volume] |
|  я готов                         |
|                                  |
|  [essere #1 >]  [Pres.]         |  <-- Verb chip + Tense chip (if present)
+----------------------------------+
```

- **Russian prompt**: shown in 20sp semi-bold
- **Volume button**: TTS speaks the prompt text

#### Font Size Scaling Behavioral Contract

VerbDrillScreen applies `ruTextScale` (from `AudioState` / Settings) to the card prompt text:

| User Action | System Response | User Outcome |
|-------------|----------------|--------------|
| User changes text scale in Settings | VerbDrillScreen re-reads `ruTextScale` from ViewModel state | Prompt text resizes proportionally |
| `textScale = 1.5x` | `promptRu` renders at `(20f * 1.5f).sp = 30sp` | Card prompt is 50% larger |
| `textScale = 2.0x` | `promptRu` renders at `(20f * 2.0f).sp = 40sp` | Card prompt is doubled in size |

**Scaling rules:**
- Card prompt text (`promptRu`): `(20f * ruTextScale).sp`
- Hint chips (verb, tense, group): NOT scaled
- Verb group badges: NOT scaled
- Conjugation table text in reference sheets: NOT scaled
- Navigation buttons, check button: NOT scaled
- **Verb chip** (`SuggestionChip`): shows verb name (with rank if present, e.g., "essere #1"). Tappable -- opens `VerbReferenceBottomSheet`. **Always visible regardless of HintLevel.** These are reference data about the verb form, NOT hints.
- **Tense chip** (`SuggestionChip`): shows abbreviated tense name. Tappable -- opens `TenseInfoBottomSheet`. **Always visible regardless of HintLevel.** These are reference data about the tense, NOT hints.

### 10.6.4 Input Controls (DefaultVerbDrillInputControls)

The input controls mirror the `AnswerBox` component used in regular training:

**Layout:**

```
+----------------------------------+
| Hint answer (if shown):          |
|  Answer: Io sono pronto.  [Vol] |
+----------------------------------+
| Incorrect  2 attempts left       |  <-- Shown on wrong answer < 3
+----------------------------------+
| Your translation            [Mic]|
| [________________________________]|
+----------------------------------+
| Say translation: я готов         |  <-- Voice mode hint
+----------------------------------+
| Tap words in correct order:      |  <-- Word bank mode
| [io] [sono] [pronto] [loro]     |
| Selected: 2 / 4      [Undo]     |
+----------------------------------+
| [Mic] [Kbd] [Book]   [Eye][Flag]|
|                       Voice      |
+----------------------------------+
| [          Check            ]    |
+----------------------------------+
```

**Components:**

- **Hint answer card**: appears when `provider.hintAnswer != null` (after 3 wrong attempts or manual eye). Shows the correct answer in red on an error-tinted card with a TTS button.
- **Incorrect feedback**: red "Incorrect" text with remaining attempts count. Shown when `provider.showIncorrectFeedback == true`.
- **Text field**: `OutlinedTextField` with "Your translation" label. Trailing icon is a microphone that switches to VOICE mode.
- **Voice mode hint**: shown when in VOICE mode, displays "Say translation: {promptRu}".
- **Word bank**: `FlowRow` of `FilterChip` words. Selected words tracked with count. "Undo" button removes last selected word.
- **Input mode buttons**: Mic (VOICE), Keyboard (KEYBOARD), Book (WORD_BANK).
- **Show answer button** (eye icon): disabled when hint already shown.
- **Flag button** (report icon): opens report bottom sheet.
- **Check button**: submits the input via `provider.submitAnswerWithInput()`. Enabled only when input is non-blank and session is active.

### 10.6.5 Voice Recognition

Voice recognition follows the same pattern as `AnswerBox`:

1. `LaunchedEffect` observes `currentCard.id`, `currentInputMode`, `sessionActive`, and `voiceTriggerToken`
2. When conditions are met (VOICE mode, session active, card exists), launches Android `RecognizerIntent` with a 200ms delay
3. Language tag mapping: `"it"` -> `"it-IT"`, others -> `"en-US"`
4. On result: sets input text, calls `submitAnswerWithInput()`, clears input
5. Auto-advance after correct voice answer: `LaunchedEffect` waits 500ms then calls `provider.nextCard()`

### 10.6.6 Verb Reference Bottom Sheet

**Triggered by**: tapping the verb chip on a card.

**Content:**

```
+----------------------------------+
|  essere                 [Volume] |
|  Группа: irregular_unique        |
|  Время: Presente                 |
|  ─────────────────────           |
|  Спряжение:                      |
|  Io sono pronto.                 |
|  Tu sei pronto.                  |
|  Lui/Lei è pronto.              |
|  ...                             |
+----------------------------------+
```

- **Verb name** with TTS button (speaks infinitive at 0.8x speed)
- **Group label** (if present)
- **Tense label** (if present)
- **Conjugation table**: all cards from the current session matching the verb+tense, showing each card's answer

### 10.6.7 Tense Info Bottom Sheet

**Triggered by**: tapping the tense chip on a card.

**Content:**

```
+----------------------------------+
|  [i] Passato Prossimo (P.Pross.) |
|                                  |
|  +-- Формула --+                 |
|  | avere/essere + participio     |
|  +--------------+                 |
|                                  |
|  Когда использовать              |
|  Действие, завершенное в прошлом |
|                                  |
|  ─────────────────────           |
|  Примеры                         |
|  +-- Card --+                    |
|  | Ho mangiato la pasta.        |
|  | Я съел пасту.                |
|  | С обычными глаголами         |
|  +-----------+                   |
+----------------------------------+
```

- **Tense name header** with Info icon and short abbreviation
- **Formula card** in a primary-tinted card
- **Usage explanation** in Russian
- **Examples**: list of cards with Italian text, Russian translation, and optional note

When `TenseInfo` is unavailable for a tense, shows a fallback with just the tense name and "Справочная информация для этого времени недоступна."

### 10.6.8 Report Bottom Sheet

**Triggered by**: tapping the flag/report button.

**Options:**

- **Add to bad sentences list** (or "Remove from bad sentences list" if already flagged)
- **Hide this card from lessons** (not yet supported for VerbDrill)
- **Export bad sentences to file**
- **Copy text** (copies card ID, source, and target to clipboard)

### 10.6.9 Completion Screen (VerbDrillCompletionScreen)

Shown when the session is complete (all 10 cards done).

```
+----------------------------------+
|                                  |
|            🎉                    |
|                                  |
|          Отлично!                |
|                                  |
|  Правильных: 7  |  Ошибок: 3    |
|                                  |
|  [         Ещё            ]      |  <-- "More" (hidden if allDoneToday)
|  [         Выход         ]       |  <-- "Exit"
|                                  |
+----------------------------------+
```

- **Stats**: shows `correctCount` and `incorrectCount`
- **"Ещё" button**: calls `viewModel.nextBatch()` to start a new 10-card batch (hidden when `allDoneToday == true`)
- **"Выход" button**: exits to selection screen

### 10.6.10 Tense Abbreviation Map

Used in tense chips to keep them compact:

| Full Name | Abbreviation |
|-----------|-------------|
| Presente | Pres. |
| Imperfetto | Imperf. |
| Passato Prossimo | P. Pross. |
| Passato Remoto | P. Rem. |
| Trapassato Prossimo | Trap. P. |
| Futuro Semplice | Fut. Sempl. |
| Futuro Anteriore | Fut. Ant. |
| Condizionale Presente | Cond. Pres. |
| Condizionale Passato | Cond. Pass. |
| Congiuntivo Presente | Cong. Pres. |
| Congiuntivo Imperfetto | Cong. Imp. |
| Congiuntivo Passato | Cong. Pass. |

Unknown tenses are truncated to 8 characters.

---

## 10.7 Integration with Daily Practice

### 10.7.1 Overview

Verb drill cards appear as **Block 3** (VERBS) of the Daily Practice session, alongside Block 1 (Translation) and Block 2 (Vocab Flashcards). The Daily Practice system is managed by `DailySessionComposer` and rendered in `DailyPracticeScreen`.

### 10.7.2 DailySessionComposer -- Verb Block Construction

**File:** `app/src/main/java/com/alexpo/grammermate/feature/daily/DailySessionComposer.kt`

The `buildVerbBlock()` method constructs Block 3:

1. **Load cards**: reads all verb drill CSV files for the pack via `LessonStore.getVerbDrillFiles()`
2. **Filter by tenses**: only cards matching `activeTenses` (derived from the tense ladder based on lesson level) are included
3. **Exclude previously shown**: collects all `everShownCardIds` across all combos from `VerbDrillStore` progress, filters them out
4. **Weak-first scoring**: each remaining card is scored by weakness:
   - For each card's tense: `weakness = 1 - (shownInCombo / comboTotal)`
   - Higher weakness = more unshown cards in that tense = that tense needs more practice
5. **Sort**: weak-first (descending by weakness), stable within same weakness (by original index)
6. **Take 10**: selects the top `VERB_COUNT` (10) cards
7. **Assign input modes**: alternating KEYBOARD and WORD_BANK (no VOICE for daily verb block)

### 10.7.3 DailyTask.ConjugateVerb

```kotlin
class ConjugateVerb(
    override val id: String,
    val card: VerbDrillCard,
    val inputMode: InputMode
) : DailyTask() {
    override val blockType = DailyBlockType.VERBS
}
```

Each verb drill task wraps a `VerbDrillCard` with a predetermined input mode and a `DailyTask` ID prefixed with `"verb_"`.

### 10.7.4 DailyPracticeSessionProvider -- Verb Block Handling

**File:** `app/src/main/java/com/alexpo/grammermate/feature/daily/DailyPracticeSessionProvider.kt`

`DailyPracticeSessionProvider` implements `CardSessionContract` and handles all three block types. For the VERBS block:

- **`currentCard`**: converts `DailyTask.ConjugateVerb` to `SessionCard` via `taskToSessionCard()` (returns the wrapped `VerbDrillCard`)
- **`currentVerbDrillCard()`**: returns the raw `VerbDrillCard` for verb/tense chip display in the UI
- **`submitAnswer()`**: same normalization and retry/hint flow as `VerbDrillCardSessionProvider`
- **`nextCard()`**: calls `onCardAdvanced(blockCards[currentIndex])` for progress persistence, then advances index. Only VOICE and KEYBOARD modes count as "practiced" (WORD_BANK does not trigger the callback).

### 10.7.5 Shared vs Separate State

| Aspect | Standalone Verb Drill | Daily Practice Block 3 |
|--------|----------------------|----------------------|
| ViewModel | `VerbDrillViewModel` | `TrainingViewModel` (delegates to `DailySessionComposer`) |
| Session provider | `VerbDrillCardSessionProvider` | `DailyPracticeSessionProvider` |
| Card selection | Random/shuffled (or frequency-sorted), filtered by user-selected tense/group | Weak-first ordering, filtered by tense ladder |
| Progress tracking | `VerbDrillStore` (combo-scoped) | `VerbDrillStore` (via `persistDailyVerbProgress` in `DailySessionHelper`) |
| Input modes | VOICE (default), KEYBOARD, WORD_BANK | KEYBOARD, WORD_BANK (no VOICE default) |
| Verb/tense chips | Yes (with bottom sheets) | Yes (mirrors VerbDrillScreen) |
| Bad sentence flagging | Supported (`mode = "verb_drill"`) | Supported (`mode = "daily_verb"`) via `DailyPracticeSessionProvider` |

### 10.7.6 Progress Tracking Across Both Contexts

Both standalone Verb Drill and Daily Practice Block 3 write to the same `VerbDrillStore` progress file (pack-scoped). This means:

- Cards shown in Daily Practice are marked as shown and will be excluded from future standalone Verb Drill sessions
- Cards shown in standalone Verb Drill are excluded from future Daily Practice verb blocks
- The `everShownCardIds` set grows cumulatively across both contexts
- The `todayShownCardIds` set tracks daily progress across both contexts

### 10.7.7 Repeat Session Support

The `DailySessionComposer.buildRepeatSession()` method reconstructs Block 3 from stored card IDs:

- `buildVerbBlockFromIds()` looks up cards by ID from verb drill files
- Preserves original ID order
- Uses same input mode assignment (alternating KEYBOARD/WORD_BANK)

This allows the daily practice "Repeat" function to replay the exact same verb cards from the first session of the day.

---

## 10.8 User Stories

### US-10.1: Practice Verb Conjugations

**As a user**, I want to practice Italian verb conjugations grouped by tense and conjugation type, so that I can build automatic recall of verb forms separately from sentence translation practice.

### US-10.2: Filter by Tense

**As a user**, I want to select a specific tense (e.g., Passato Prossimo) from a dropdown and practice only cards for that tense, so that I can focus on tenses I find difficult.

### US-10.3: Filter by Conjugation Group

**As a user**, I want to select a specific conjugation group (e.g., irregular verbs, -are verbs) from a dropdown, so that I can target practice at specific verb patterns.

### US-10.4: Track Progress Per Combo

**As a user**, I want to see how many cards I have practiced for a given tense+group combination (both ever and today), so that I can measure my coverage of conjugation forms.

### US-10.5: Daily Card Pool

**As a user**, I want the system to track which cards I have already practiced today and exclude them from new sessions, so that each session gives me fresh cards. When I have exhausted all cards for a combo, I want to see "На сегодня всё!" and come back tomorrow.

### US-10.6: Retry Wrong Answers

**As a user**, when I answer incorrectly, I want to see "Incorrect" feedback with remaining attempts, and have up to 3 attempts before the answer is shown, so that I can learn from my mistakes without getting stuck.

### US-10.7: Show Answer on Demand

**As a user**, I want to press an eye button to reveal the answer immediately when I am stuck, so that I can learn the correct form without wasting time.

### US-10.8: Voice Input

**As a user**, I want to practice verb conjugations by speaking the answer aloud, so that I build production skills (not just recognition). I want voice recognition to auto-start when a new card appears and auto-retry when my answer is wrong.

### US-10.9: Keyboard Input

**As a user**, I want to type conjugation answers using the keyboard, so that I can practice in quiet environments where voice input is not practical.

### US-10.10: Word Bank Mode

**As a user**, I want to compose answers by tapping word tiles in the correct order, so that I can practice when I am unsure of exact spelling but know the word order.

### US-10.11: Verb Reference Sheet

**As a user**, when practicing a card for the verb "essere", I want to tap the verb chip and see the full conjugation table for that verb in the current tense, so that I can review the pattern while practicing.

### US-10.12: Tense Reference Info

**As a user**, when practicing a card in Passato Prossimo, I want to tap the tense chip and see the formation formula, usage rules in Russian, and example sentences, so that I can understand when and how to use that tense.

### US-10.13: Sort by Frequency

**As a user**, I want to toggle a "По частотности" option that sorts cards by verb frequency (most common first), so that I can prioritize learning the most useful verbs.

### US-10.14: Flag Bad Sentences

**As a user**, when I encounter a card with an error or unnatural phrasing, I want to flag it as a "bad sentence" and later export all flagged cards to a text file, so that I can report issues to content creators.

### US-10.15: Session Completion

**As a user**, after completing a 10-card batch, I want to see my score (correct vs. incorrect) and choose to practice more cards or exit, so that I have a sense of accomplishment and control over my practice session.

### US-10.16: Verb Drill in Daily Practice

**As a user**, I want verb conjugation cards to appear as the third block of my Daily Practice session, selected automatically based on my lesson level and weak areas, so that conjugation practice is integrated into my daily routine without requiring separate navigation.

### US-10.17: Pack-Scoped Progress

**As a user**, I want my verb drill progress to be tracked per lesson pack, so that switching between different language packs (e.g., Italian B1, Italian B2) keeps progress independent.

### US-10.18: TTS for Verb Drill

**As a user**, I want to hear the prompt spoken aloud via TTS, and also hear the correct answer after revealing it, so that I can associate sounds with written forms. I also want to hear the verb infinitive pronounced slowly when viewing the verb reference sheet.

### US-10.19: Navigate Between Cards

**As a user**, I want to go back to previous cards in the current session, so that I can review cards I already answered or check something I missed.

---

## 10.9 UI Consistency Requirements [UI-CONSISTENCY-2025]

This section documents the UI consistency requirements for VerbDrill, established by cross-screen audit. VerbDrill is the REFERENCE implementation for eye/show-answer mode and the ADOPTER of voice auto mode (from VocabDrill) and the 4-option report sheet (from TrainingScreen).

---

### 10.9.1 Voice Input Auto-Start Mode

VerbDrill uses the global `voiceAutoStart` setting from SettingsScreen (NOT a per-drill toggle).

**IMPORTANT:** Voice auto-start controls ONLY whether speech recognition launches automatically on new cards. It does NOT disable the microphone. The mic button in the input mode bar ALWAYS works when clicked manually.

**Behavior:**

1. **Auto-launch on new card:** When global `voiceAutoStart == true`, mic auto-launches on each new card with a **500ms delay**. When `voiceAutoStart == false`, no automatic launch occurs, but the mic button still works on manual click.
   - Class path: `ui/components/VoiceAutoLauncher.kt` — shared auto-launch composable

2. **Auto-advance on correct voice answer:** After a correct voice answer, auto-advance to the next card after **500ms delay**.
   - Class path: `ui/VerbDrillScreen.kt` — `LaunchedEffect` observer

3. **No per-drill toggle:** VerbDrillSelectionScreen no longer has a "Voice input (auto)" Switch. The global Settings toggle (`AudioState.voiceAutoStart`) is the sole control.
   - The `voiceModeEnabled` field was removed from `VerbDrillUiState`.
   - VerbDrillViewModel.toggleVoiceAutoMode() was removed.
   - `VerbDrillScreen` receives `voiceAutoStart: Boolean` as a parameter from GrammarMateApp.

4. **Shared component:** Voice auto-launch logic is in `ui/components/VoiceAutoLauncher.kt`, used by VerbDrill, VocabDrill, and TrainingScreen.

**Regression class paths:**
- `ui/VerbDrillScreen.kt` — VoiceAutoLauncher usage + DefaultVerbDrillInputControls auto-voice
- `ui/VerbDrillCardSessionProvider.kt` — auto-launch token and auto-advance timing
- `ui/components/VoiceAutoLauncher.kt` — shared auto-launch composable
- `ui/GrammarMateApp.kt` — passes `voiceAutoStart` to VerbDrillScreen

---

### 10.9.2 Eye / Show Answer Mode — Reference Implementation [UI-CONSISTENCY-2025]

VerbDrill's eye/show-answer mode is the **REFERENCE implementation** that all other screens (TrainingScreen, DailyPractice) must match.

**IMPORTANT:** Eye mode works at ALL HintLevel settings. The hint card renders whenever `provider.hintAnswer != null`, regardless of whether hintLevel is EASY, MEDIUM, or HARD. HintLevel controls parenthetical hints in prompt text (e.g., stripping `(dire)` and `(verità)` from the card body), NOT the eye/show-answer button or the hint answer card visibility. Any guard that checks `hintLevel == EASY` before rendering the HintAnswerCard is a bug.

**Reference implementation:** VerbDrillScreen.kt lines 392-425 (hint answer card with errorContainer tint, red answer text, inline TTS replay button).

**Visual specification:**

1. **Hint answer card:** When `provider.hintAnswer != null`, the answer is shown in a pink `Card` with `MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)` background tint.
   - Class path: `ui/screens/VerbDrillScreen.kt:392-400` — hint card composable

2. **Red answer text:** The answer text is rendered with `MaterialTheme.colorScheme.error` color in the format "Answer: {answer}".
   - Class path: `ui/screens/VerbDrillScreen.kt:401-405` — answer text styling

3. **Inline TTS replay button:** A red-tinted `VolumeUp` icon button is placed inline next to the answer text for TTS replay. The icon uses `MaterialTheme.colorScheme.error` tint.
   - Class path: `ui/screens/VerbDrillScreen.kt:406-415` — TTS replay button

4. **Eye button disabled guard:** The eye/show-answer button is disabled while a hint is already shown, guarded by `provider.hintAnswer == null`.
   - Class path: `ui/screens/VerbDrillScreen.kt` — eye button enabled state

**Other screens adopting this pattern:**
- `ui/screens/TrainingScreen.kt` — AnswerBox hint card
- `ui/DailyPracticeScreen.kt` — DailyInputControls hint card

**Regression class paths:**
- `ui/screens/VerbDrillScreen.kt:392-425` — reference implementation (DO NOT DEVIATE)
- `ui/screens/TrainingScreen.kt:681-748` — adopter (must match)
- `ui/DailyPracticeScreen.kt` — adopter (must match)

---

### 10.9.3 Report Sheet — 4-Option Standard [UI-CONSISTENCY-2025]

The VerbDrill report sheet must adopt the 4-option standard from TrainingScreen, adding the missing "Hide this card from lessons" option.

**Current state:** `VerbDrillReportSheet` has 3 options (flag/unflag, export, copy).
**Required state:** 4 options matching TrainingScreen (flag/unflag, HIDE CARD, export, copy).

**4 standard options (in order):**

| # | Option | Icon | Behavior |
|---|--------|------|----------|
| 1 | Flag/Unflag bad sentence | `ReportProblem` (toggles) | Calls `flagBadSentence()` or `unflagBadSentence()`. Red tint when flagged. |
| 2 | **Hide this card from lessons** | `VisibilityOff` | Calls `hideCurrentCard()`. Closes sheet. |
| 3 | Export bad sentences | `Download` | Calls `exportBadSentences()`. Shows file path in AlertDialog. |
| 4 | Copy text | `ContentCopy` | Copies card ID, source, target to clipboard. Closes sheet. |

**Shared component:** Extract into `ui/components/SharedReportSheet.kt` used by VerbDrill, TrainingScreen, and VocabDrill.

**Implementation notes:**
- Option 2 (Hide) is currently a no-op for VerbDrill (no hidden-card infrastructure). The option must still appear in the sheet for UI consistency. A toast or snackbar should indicate "Feature not yet available for verb drills."
- The shared component must accept a `HideOptionConfig` parameter to control whether hide is functional or informational.

**Regression class paths:**
- `ui/screens/VerbDrillScreen.kt` — `VerbDrillReportSheet` composable (update to 4 options)
- `ui/components/SharedReportSheet.kt` — shared report sheet (NEW)
- `ui/screens/TrainingScreen.kt` — `AnswerBox` report sheet (reference for 4-option layout)
- `ui/screens/VocabDrillScreen.kt` — `VocabDrillReportSheet` (adopter)


---


# 11. Vocab Drill System — Specification

## 11.1 Overview

The Vocab Drill system is an Anki-style spaced repetition flashcard module that lets users practice vocabulary from their installed lesson pack. It is one of the three drill types available in GrammarMate (alongside verb conjugation drills and sentence translation).

**Purpose:** Build passive and active vocabulary recall for the target language (Italian) through repeated exposure and self-assessment, using the same interval ladder `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` days shared with the rest of the app's spaced repetition engine.

**Supported word categories** (parts of speech):

| POS key      | Display label | Source CSV file            |
|--------------|---------------|---------------------------|
| `nouns`      | Nouns         | `drill_nouns.csv`          |
| `verbs`      | Verbs         | `drill_verbs.csv`          |
| `adjectives` | Adj.          | `drill_adjectives.csv`     |
| `adverbs`    | Adv.          | `drill_adverbs.csv`        |
| `numbers`    | Numbers       | `drill_numbers.csv`        |
| `pronouns`   | Pronouns      | `drill_pronouns.csv`       |

All CSV files are located in the pack's vocab drill directory (e.g. `grammarmate/drills/{packId}/vocab_drill/`) after import, or under `assets/grammarmate/vocab/it/` for default data.

**Two access points:**

1. **Standalone drill** — user navigates to the VocabDrillScreen from HomeScreen, configures filters, and starts a session. Managed by `VocabDrillViewModel`.
2. **Daily Practice block 2** — 5 vocab flashcards automatically selected by SRS priority, interleaved into the daily practice session. Managed by `DailySessionComposer` + `TrainingViewModel`.

---

## 11.2 Data Model

### 11.2.1 VocabWord

Source file: `data/VocabWord.kt`

```kotlin
data class VocabWord(
    val id: String,                              // e.g. "nouns_106_casa", "verbs_1_essere"
    val word: String,                            // Italian word (the canonical form)
    val pos: String,                             // "nouns", "verbs", "adjectives", "adverbs", "numbers", "pronouns"
    val rank: Int,                               // Frequency rank (from CSV, or synthetic for numbers/pronouns)
    val meaningRu: String? = null,               // Russian translation, may contain "/" for synonyms
    val collocations: List<String> = emptyList(), // Italian example phrases (semicolon-separated in CSV)
    val forms: Map<String, String> = emptyMap()  // Gender/number forms for adjectives, numbers, pronouns
)
```

**ID format:** `{pos}_{rank}_{word}` (e.g. `nouns_106_casa`, `adjectives_776_solito`).

**Forms map keys** (present only for adjectives, numbers, and pronouns):

| Key         | Meaning                              | Used for              |
|-------------|--------------------------------------|-----------------------|
| `msg`       | Masculine singular                   | Adjectives            |
| `fsg`       | Feminine singular                    | Adjectives            |
| `mpl`       | Masculine plural                     | Adjectives            |
| `fpl`       | Feminine plural                      | Adjectives            |
| `form_m`    | Masculine form                       | Numbers               |
| `form_f`    | Feminine form                        | Numbers               |
| `form_sg_m` | Singular masculine form              | Pronouns              |
| `form_sg_f` | Singular feminine form               | Pronouns              |
| `form_pl_m` | Plural masculine form                | Pronouns              |
| `form_pl_f` | Plural feminine form                 | Pronouns              |

**Hard word classification:** Words with `rank > 100` are flagged as `isHard` during CSV parsing. This flag is carried by `ItalianDrillRow.isHard` but not currently exposed on `VocabWord` itself.

### 11.2.2 ItalianDrillRow (parser intermediate)

Source file: `data/ItalianDrillVocabParser.kt`

```kotlin
data class ItalianDrillRow(
    val rank: Int,
    val word: String,
    val collocations: List<String>,
    val isHard: Boolean,              // rank > 100
    val meaningRu: String? = null,
    val forms: Map<String, String> = emptyMap()
)
```

This is the raw parsed row before conversion to `VocabWord`. The parser returns `ItalianDrillRow` objects; the ViewModel/composer converts them to `VocabWord` by constructing the compound ID.

### 11.2.3 VocabEntry (legacy asset format)

Source file: `data/Models.kt`

```kotlin
data class VocabEntry(
    val id: String,                  // e.g. "it_drill_drill_nouns.csv_106"
    val lessonId: String,
    val languageId: String,
    val nativeText: String,          // Italian word
    val targetText: String,          // Collocations joined with "+" or the word itself
    val isHard: Boolean = false
)
```

This model is used only by `ItalianDrillVocabParser.loadAllFromAssets()`, a legacy method that loads from bundled asset files. The current pack-scoped loading path does not use `VocabEntry`; it uses `ItalianDrillRow` -> `VocabWord` directly.

### 11.2.4 WordMasteryState

Source file: `data/VocabWord.kt`

```kotlin
data class WordMasteryState(
    val wordId: String,
    val intervalStepIndex: Int = 0,    // 0-9, index into SpacedRepetitionConfig.INTERVAL_LADDER_DAYS
    val correctCount: Int = 0,         // Total lifetime correct answers
    val incorrectCount: Int = 0,       // Total lifetime incorrect answers
    val lastReviewDateMs: Long = 0L,   // Epoch millis of last review
    val nextReviewDateMs: Long = 0L,   // Computed: lastReviewDateMs + ladder[step] * DAY_MS
    val isLearned: Boolean = false     // Reached step >= 3 (LEARNED_THRESHOLD)
)
```

**Key behaviors:**

- **New word:** `intervalStepIndex = 0`, `lastReviewDateMs = 0`, `nextReviewDateMs = 0`. A word is "due immediately" when `lastReviewDateMs == 0`.
- **Learned threshold:** A word is considered "learned" when `intervalStepIndex >= 3` (i.e. it has advanced past the 3rd rung on the interval ladder). The interval at step 3 is 7 days.
- **Day constant:** `DAY_MS = 86_400_000L` (milliseconds in one day).
- **Next review computation:** `lastReviewMs + INTERVAL_LADDER_DAYS[step] * DAY_MS`.

### 11.2.5 VocabDrillCard

```kotlin
data class VocabDrillCard(
    val word: VocabWord,
    val mastery: WordMasteryState
)
```

Combines a vocab word with its current mastery state for use in a drill session.

### 11.2.6 VocabDrillDirection

```kotlin
enum class VocabDrillDirection { IT_TO_RU, RU_TO_IT }
```

| Value       | Prompt language | Expected answer |
|-------------|-----------------|-----------------|
| `IT_TO_RU`  | Italian word shown | User says/writes Russian meaning |
| `RU_TO_IT`  | Russian meaning shown | User says/writes Italian word |

### 11.2.7 VoiceResult

```kotlin
enum class VoiceResult { CORRECT, WRONG, SKIPPED }
```

Outcome of a voice recognition attempt on a flashcard.

### 11.2.8 VocabDrillUiState

```kotlin
data class VocabDrillUiState(
    val isLoading: Boolean = true,
    val availablePos: List<String> = emptyList(),
    val selectedPos: String? = null,              // null = "All"
    val rankMin: Int = 0,
    val rankMax: Int = Int.MAX_VALUE,
    val dueCount: Int = 0,
    val totalCount: Int = 0,
    val session: VocabDrillSessionState? = null,
    val loadedLanguageId: String? = null,
    val drillDirection: VocabDrillDirection = VocabDrillDirection.IT_TO_RU,
    val masteredCount: Int = 0,
    val masteredByPos: Map<String, Int> = emptyMap()
)
```

Top-level state for the standalone VocabDrillScreen. `session` is non-null when a drill session is active.

**Note:** `voiceModeEnabled` was removed. Voice auto-start is now controlled by the global `AudioState.voiceAutoStart` setting, passed to VocabDrillScreen as a `voiceAutoStart` parameter.

### 11.2.9 VocabDrillSessionState

```kotlin
data class VocabDrillSessionState(
    val cards: List<VocabDrillCard>,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val isComplete: Boolean = false,
    val isFlipped: Boolean = false,               // Card flipped to reveal answer
    val direction: VocabDrillDirection = VocabDrillDirection.IT_TO_RU,
    val voiceAttempts: Int = 0,                   // 0-3 per card
    val voiceRecognizedText: String? = null,
    val voiceResult: VoiceResult? = null,         // CORRECT, WRONG, SKIPPED
    val voiceCompleted: Boolean = false          // True after correct, 3 wrongs, or skip
)
```

**Note:** `voiceModeEnabled` was removed from `VocabDrillSessionState`. Voice auto-start is now controlled by the global `AudioState.voiceAutoStart` setting, passed to `VocabDrillScreen` as a `voiceAutoStart` parameter.

### 11.2.10 DailyTask.VocabFlashcard (Daily Practice integration)

Source file: `data/Models.kt`

```kotlin
data class VocabFlashcard(
    override val id: String,          // "voc_{pos}_{rank}_{word}"
    val word: VocabWord,
    val direction: VocabDrillDirection
) : DailyTask() {
    override val blockType = DailyBlockType.VOCAB
}
```

---

## 11.3 Word Mastery Store

Source file: `data/WordMasteryStore.kt`

### 11.3.1 Purpose

Persists per-word spaced repetition state to YAML. Used by both `VocabDrillViewModel` (standalone drill) and `DailySessionComposer` (daily practice block 2).

### 11.3.2 File Location

| Mode         | Path                                                  |
|--------------|-------------------------------------------------------|
| Pack-scoped  | `grammarmate/drills/{packId}/word_mastery.yaml`       |
| Legacy       | `grammarmate/word_mastery.yaml` (when packId is null) |

### 11.3.3 YAML Schema

```yaml
schemaVersion: 1
data:
  nouns_106_casa:
    intervalStepIndex: 3
    correctCount: 7
    incorrectCount: 1
    lastReviewDateMs: 1715500800000
    nextReviewDateMs: 1716105600000
    isLearned: true
  verbs_1_essere:
    intervalStepIndex: 0
    correctCount: 0
    incorrectCount: 0
    lastReviewDateMs: 0
    nextReviewDateMs: 0
    isLearned: false
```

All fields are required for each word entry.

### 11.3.4 Key Methods

| Method                       | Description                                                                 |
|------------------------------|-----------------------------------------------------------------------------|
| `loadAll()`                  | Reads full YAML file, returns `Map<String, WordMasteryState>`               |
| `saveAll(mastery)`           | Writes full map via `AtomicFileWriter`                                      |
| `getMastery(wordId)`         | Returns state for a single word, or null if never reviewed                  |
| `upsertMastery(state)`       | Load-modify-save for a single word                                          |
| `getDueWords()`              | Returns set of word IDs where `nextReviewDateMs <= now` or `lastReviewDateMs == 0` |
| `getMasteredCount(pos?)`     | Count of words where `isLearned == true`, optionally filtered by POS prefix  |
| `getMasteredByPos()`         | Map of POS -> count of mastered words                                       |

### 11.3.5 Due Word Criteria

A word is considered **due** when:
- `nextReviewDateMs <= System.currentTimeMillis()` (scheduled review has elapsed), OR
- `lastReviewDateMs == 0` (never reviewed, due immediately)

### 11.3.6 Learned Criteria

A word is considered **learned** when `isLearned == true`, which is set when `intervalStepIndex >= 3`. The interval at step 3 is 7 days, meaning the word has survived at least three spaced repetitions.

---

## 11.4 VocabProgressStore (Legacy)

Source file: `data/VocabProgressStore.kt`

### 11.4.1 Purpose

Legacy store for per-lesson vocab sprint progress and a separate SRS system. This store uses a different interval ladder `[1, 3, 7, 14, 30]` and a lesson-scoped progress model. It is **not used** by the current `VocabDrillViewModel` or `DailySessionComposer`, which use `WordMasteryStore` instead.

### 11.4.2 File Location

`grammarmate/vocab_progress.yaml`

### 11.4.3 YAML Schema

```yaml
schemaVersion: 1
data:
  {languageId}:
    {lessonId}:
      completedIndices: [0, 1, 5]
      entries:
        {entryId}:
          lastCorrectMs: 1715500800000
          lastIncorrectMs: 0
          intervalStep: 2
```

### 11.4.4 Interval Ladder

`[1, 3, 7, 14, 30]` days (5 steps, indexed 0-4).

### 11.4.5 Key Methods

| Method                        | Description                                             |
|-------------------------------|---------------------------------------------------------|
| `get(lessonId, languageId)`   | Get sprint progress for a lesson                        |
| `saveCompletedIndices(...)`   | Save set of completed entry indices                     |
| `addCompletedIndex(...)`      | Add one completed index                                 |
| `clearSprintProgress(...)`    | Clear all completed indices for a lesson                |
| `recordCorrect(...)`          | Advance interval step, update lastCorrectMs             |
| `recordIncorrect(...)`        | Reset interval step to 0, update lastIncorrectMs        |
| `isDueForReview(...)`         | Check if entry is past its review interval              |
| `sortEntriesForSprint(...)`   | Anki-like ordering: overdue > new > not due             |

**Note:** This store's `sortEntriesForSprint()` orders words as: overdue (shuffled) first, then new words (shuffled), then not-due words (shuffled).

---

## 11.4a ItalianDrillVocabParser Additional Methods

Source file: `data/ItalianDrillVocabParser.kt`

### `parse(inputStream, fileName) -> List<ItalianDrillRow>`

Auto-detects the CSV format from the header row:

| Header contains                       | Parser method                | POS type    |
|---------------------------------------|------------------------------|-------------|
| `rank` + (`verb` or `noun`)           | `parseRankWordCollocations`  | verbs/nouns |
| `rank` + `adjective`                  | `parseAdjectives`            | adjectives  |
| `rank` + `adverb`                     | `parseAdverbs`               | adverbs     |
| `category` + `italian`                | `parseNumbers`               | numbers     |
| `type` + `person`                     | `parsePronouns`              | pronouns    |
| (fallback: unrecognized header)       | `parseRankWordCollocations`  | verbs/nouns |

The `ru` column is auto-detected by scanning for column headers `ru`, `meaning_ru`, or `russian` (case-insensitive).

### `loadAllFromAssets(context, lessonId, languageId) -> List<VocabEntry>`

Legacy method that loads from hardcoded asset paths (`grammarmate/vocab/it/`). Loads all six CSV files in order: adjectives, adverbs, nouns, numbers, pronouns, verbs. Produces `VocabEntry` objects (not `VocabWord`). **Not used by the pack-scoped loading path.**

### `splitCsvLine(line) -> List<String>`

CSV line splitter that respects double-quoted fields. Commas inside quotes are treated as part of the field value.

---

## 11.5 VocabDrillViewModel

Source file: `ui/VocabDrillViewModel.kt`

### 11.5.1 Architecture

`VocabDrillViewModel` is an `AndroidViewModel` that manages the standalone vocab drill screen. It is a **separate ViewModel** from `TrainingViewModel` (exempt from the single-ViewModel rule because it manages its own isolated domain).

Dependencies:
- `LessonStore` -- for accessing vocab drill CSV files from installed packs
- `WordMasteryStore` -- for per-word SRS state (recreated when pack changes)
- `BadSentenceStore` -- for flagging/unflagging bad vocab cards
- `TtsEngine` -- for pronouncing Italian words

### 11.5.2 Initialization Flow

1. `init` block sets `isLoading = false`. Does **not** auto-load data.
2. Caller must invoke either:
   - `reloadForPack(packId, languageId)` — pack-scoped loading (preferred)
   - `reloadForLanguage(languageId)` — loads from all installed packs (legacy fallback)
3. Both methods are idempotent: if the same pack+language is already loaded, they return immediately.

### 11.5.3 reloadForPack(packId, languageId)

1. Checks idempotency (same packId + languageId + words already loaded).
2. Sets `activePackId`.
3. Recreates `WordMasteryStore` scoped to the pack: `WordMasteryStore(application, packId = packId)`.
4. Launches coroutine to call `loadWords(languageId)`.

### 11.5.4 loadWords(languageId)

1. Determines source files:
   - Pack-scoped: `lessonStore.getVocabDrillFiles(pack, lang)`
   - No pack: scans all installed packs with `flatMap`
2. Parses each file via `ItalianDrillVocabParser.parse()`.
3. Derives POS from filename: removes `{lang}_` prefix, `drill_` prefix, `.csv` suffix (e.g. `drill_nouns.csv` -> `"nouns"`).
4. Constructs `VocabWord` for each row with ID `{pos}_{rank}_{word}`.
5. Sorts all words by `rank`.
6. Loads mastery map from `WordMasteryStore.loadAll()`.
7. Extracts distinct POS list for the filter chips.
8. Calls `updateCounts()`.

### 11.5.5 Card Selection Algorithm (startSession)

When the user taps "Start":

1. Filter words by current `selectedPos` and `rankMin`/`rankMax`.
2. For each filtered word, check if it is **due**:
   - `mastery.nextReviewDateMs <= now` OR `mastery.lastReviewDateMs == 0`
3. Create `VocabDrillCard` for each due word.
4. Sort due cards by `word.rank` (ascending — lowest rank first).
5. Take up to **10** cards.
6. If no due cards exist, set `session = null` (no session started).
7. Create `VocabDrillSessionState` with the selected cards, current direction, and voice mode.

### 11.5.6 Filtering

Users can filter the word pool before starting a session:

| Filter         | Values                                              | Default       |
|----------------|-----------------------------------------------------|---------------|
| Part of speech | `null` (All), `nouns`, `verbs`, `adjectives`, `adverbs`, `numbers`, `pronouns` | `null` (All)  |
| Rank range     | Top 100 (0-100), Top 500 (0-500), Top 1000 (0-1000), All (0-MAX) | All           |

**Note:** When "All" POS is selected (`selectedPos == null`), words with `pos == "numbers"` are excluded from the filtered set. Numbers only appear when explicitly selected via the POS filter.

`updateCounts()` recomputes `dueCount`, `totalCount`, `masteredCount`, and `masteredByPos` after any filter change.

### 11.5.7 Flashcard Flip Behavior

- **flipCard()** — toggles `session.isFlipped`.
- When flipped, the card back shows the answer (translation, forms, collocations).
- The front shows the prompt (Italian word for IT_TO_RU, Russian meaning for RU_TO_IT).
- Auto-flip occurs 800ms after voice input completes (correct, wrong after 3 attempts, or skip).

### 11.5.8 Answer Rating System (Anki-style)

After the card is flipped, the user sees four rating buttons:

| Rating | Behavior                                    | Step Delta | Displayed interval |
|--------|---------------------------------------------|------------|--------------------|
| Again  | Resets step to 0                            | -100 (clamped to 0) | "<1m"        |
| Hard   | Stays at current step                       | 0          | `ladder[currentStep]`d |
| Good   | Advances 1 step                             | +1         | `ladder[currentStep+1]`d |
| Easy   | Advances 2 steps                            | +2         | `ladder[currentStep+2]`d |

The interval ladder is: `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` days (from `SpacedRepetitionConfig.INTERVAL_LADDER_DAYS`).

**Rating logic in `answerRating()`:**

1. Gets current card and its mastery state.
2. Computes new step index:
   - Again: always resets to 0.
   - Others: `currentStep + delta`, clamped to `[0, maxStep]` where maxStep = 9.
3. Computes `nextReviewDateMs` via `WordMasteryState.computeNextReview(now, newStepIndex)`.
4. Sets `isLearned = (newStepIndex >= 3)`.
5. Updates `correctCount` / `incorrectCount`:
   - Again: `incorrectCount++`
   - Others: `correctCount++`
6. Writes updated mastery to in-memory map and persists via `masteryStore.upsertMastery()`.
7. Calls `advanceCard()` to move to next card.

### 11.5.9 Voice Input

**Voice mode toggle:** When enabled, the mic launches automatically on each new card (after a 500ms delay for animation).

**Voice matching logic (`handleVoiceResult()`):**

1. Determines expected answer based on direction:
   - `IT_TO_RU`: expected = `meaningRu` (e.g. "быть/являться")
   - `RU_TO_IT`: expected = `word` (Italian, e.g. "essere")
2. Splits expected answer by "/" to get multiple valid answers.
3. Normalizes both recognized text and expected answers: trim, lowercase, strip trailing punctuation `[.!?,;:]`.
4. Match is correct if normalized recognized text equals or contains any valid answer.
5. On correct: sets `voiceResult = CORRECT`, `voiceCompleted = true`, auto-flips after 800ms.
6. On wrong: increments `voiceAttempts`. If attempts >= 3, sets `voiceCompleted = true` with `WRONG`. Otherwise allows retry.
7. Skip: sets `voiceResult = SKIPPED`, `voiceCompleted = true`.

**Normalization method (`normalizeForMatch`):**
```kotlin
fun normalizeForMatch(text: String): String {
    return text.trim().lowercase().replace(Regex("[.!?,;:]$"), "")
}
```
Strips leading/trailing whitespace, converts to lowercase, and removes trailing punctuation characters.

**Language tag for voice recognition:**
- `IT_TO_RU` direction: `ru-RU` (user speaks Russian)
- `RU_TO_IT` direction: `it-IT` (user speaks Italian)

**Auto-voice check:** The UI checks the global `voiceAutoStart` flag (passed as parameter) together with `!isFlipped && !voiceCompleted` to decide whether to auto-launch RecognizerIntent on a new card. The mic button ALWAYS works on manual click regardless of this flag.

### 11.5.10 Session Completion

- When `currentIndex >= cards.size`, `isComplete = true`.
- Completion screen shows: correct count, incorrect count, total words reviewed.
- "Perfect!" displayed if all correct, "Done!" otherwise.
- Options: "Exit" (returns to selection screen) or "Continue" (starts a new session with the next batch of due words).

### 11.5.11 TTS Support

- `speakTts(text, speed = 0.67f)` — pronounces the Italian word via Sherpa-ONNX TTS.
- TTS button is shown on both card front (IT_TO_RU direction only) and card back.
- Auto-initializes TTS engine on first use with the active language ID.

---

## 11.6 VocabDrillScreen (UI)

Source file: `ui/VocabDrillScreen.kt`

### 11.6.1 Screen Structure

The screen has three states, each rendered by a separate composable:

| State                | Composable                    | Condition                 |
|----------------------|-------------------------------|---------------------------|
| Loading              | `CircularProgressIndicator`   | `state.isLoading == true` |
| Selection (no session)| `VocabDrillSelectionScreen`   | `session == null`         |
| Card practice        | `VocabDrillCardScreen`        | `session != null && !isComplete` |
| Completion           | `VocabDrillCompletionScreen`  | `session.isComplete == true` |

### 11.6.2 Selection Screen (VocabDrillSelectionScreen)

Layout (top to bottom):
1. **Header:** Back arrow + "Flashcards" title.
2. **Direction filter chips:** "IT -> RU" and "RU -> IT". Default: IT -> RU.
3. ~~**Voice input toggle:**~~ Removed. Voice auto-start is now controlled by the global Settings toggle only (`AudioState.voiceAutoStart`).
4. **Part of speech filter chips:** "All" + one chip per available POS. Labels: Nouns, Verbs, Adj., Adv., Numbers (with capitalize-first-char fallback for unknown POS like "pronouns").
5. **Word frequency filter chips:** "Top 100", "Top 500", "Top 1000", "All".
6. **Stats card** (shown when `totalCount > 0`):
   - "Due: {dueCount} / {totalCount}" heading.
   - "Mastered: {masteredCount} words" in green (if any mastered).
   - Per-POS mastered breakdown (e.g. "Nouns: 15 | Verbs: 8").
   - Progress bar: `(totalCount - dueCount) / totalCount`.
7. **Start button:** "Start ({dueCount} due)" or disabled "No due words".

### 11.6.3 Card Screen (VocabDrillCardScreen)

Layout:
1. **Header bar:** Back arrow + "Flashcards" title + card counter "{current}/{total}".
2. **Progress bar:** Linear, showing `currentIndex / totalCards`.
3. **Card** (front or back, depending on `isFlipped`).
4. **Bottom action buttons** (below the card):
   - **Not flipped:** Skip button + Flip button.
   - **Flipped:** 4 Anki-style rating buttons (Again, Hard, Good, Easy) with interval previews.

### 11.6.4 Card Front (VocabDrillCardFront)

- POS badge (colored chip): nouns=`primaryContainer` (label "noun"), verbs=`secondaryContainer` (label "verb"), adjectives=`tertiaryContainer` (label "adj."), adverbs=`errorContainer` (label "adv."), numbers=`surfaceVariant` (label "num."), pronouns=`surfaceVariant` (label "pronouns" with capitalize-first-char fallback). **Always visible regardless of HintLevel.** POS is reference data about the word, not a hint.
- Rank badge ("#106" style). **Always visible regardless of HintLevel.** Rank is frequency data about the word, not a hint.
- **Main word** (32sp, bold):
  - IT_TO_RU: Italian word (e.g. "casa").
  - RU_TO_IT: Russian meaning (e.g. "дом/жилище/семья").
- **TTS button** (IT_TO_RU only): VolumeUp icon with states (SPEAKING, INITIALIZING, ERROR, IDLE).
- **Voice input area:**
  - If voice not completed: "Tap to speak" label + large 72dp mic button.
  - If voice completed: `VoiceResultFeedback` card showing result.

**Card background color** changes based on voice result:
- Correct: light green tint (`#E8F5E9` at 0.7 alpha).
- Wrong: light red tint (`#FFEBEE` at 0.7 alpha).
- Default: surface variant at 0.7 alpha.

#### Font Size Scaling Behavioral Contract (VocabDrill)

VocabDrillScreen applies `ruTextScale` (from `AudioState` / Settings) to the main word text on both card sides:

| User Action | System Response | User Outcome |
|-------------|----------------|--------------|
| User changes text scale in Settings | VocabDrillScreen re-reads `ruTextScale` | Word text resizes on both card sides |
| `textScale = 2.0x` | Main word on front renders at `(32f * 2.0f).sp = 64sp` | Card fits on screen without overflow |
| `textScale = 1.5x` | Back card translation renders at `(18f-24f * 1.5f).sp` | Translation text scales proportionally |

**Scaling rules:**
- Card front main word: `(32f * ruTextScale).sp`
- Card back word text (IT_TO_RU direction): `(20f * ruTextScale).sp` semibold
- Card back translation text (IT_TO_RU): `(18f * ruTextScale).sp` medium
- Card back Italian word (RU_TO_IT direction): `(24f * ruTextScale).sp` bold
- Card back Russian meaning (RU_TO_IT): `(16f * ruTextScale).sp` medium
- POS badge, rank badge: NOT scaled
- Forms table, collocations: NOT scaled
- Mastery indicator ("Step X/9"): NOT scaled
- Correct: light green tint (`#E8F5E9` at 0.7 alpha).
- Wrong: light red tint (`#FFEBEE` at 0.7 alpha).
- Default: surface variant at 0.7 alpha.

### 11.6.5 Card Back (VocabDrillCardBack)

Background: secondary container at 0.5 alpha.

**IT_TO_RU direction:**
1. POS badge + Italian word (20sp, semibold) + TTS button.
2. Russian translation (18sp, medium, primary color).

**RU_TO_IT direction:**
1. POS badge + Russian meaning (16sp, medium, 0.7 alpha).
2. Italian word (24sp, bold, primary color) + TTS button.

**Both directions:**
3. **Forms card** (if `forms` is non-empty): "Forms" label inside a tertiary-container-tinted card. **Always visible regardless of HintLevel.** Forms are reference data about the word, not hints. The grid columns vary by POS:
   - **Adjectives:** 4-column grid: m sg (`forms["msg"]`), f sg (`forms["fsg"]`), m pl (`forms["mpl"]`), f pl (`forms["fpl"]`).
   - **Numbers:** 2-column grid: m (`forms["form_m"]`), f (`forms["form_f"]`).
   - **Pronouns:** 4-column grid: sg m (`forms["form_sg_m"]`), sg f (`forms["form_sg_f"]`), pl m (`forms["form_pl_m"]`), pl f (`forms["form_pl_f"]`).
   - Each form item shows a label and the value (or "-" if null).
4. **Collocations** (if non-empty): "Collocations" label + up to 5 phrases + "+N more" overflow. **Always visible regardless of HintLevel.** Collocations are reference data about word usage, not hints.
5. **Mastery indicator:** "Step {step+1}/9" or "Learned" (when step >= LEARNED_THRESHOLD = 3). Displayed in label-small text at 0.5 alpha.

### 11.6.6 Voice Result Feedback (VoiceResultFeedback)

Shown in the card front area after voice input completes:
- Displayed in a rounded card.
- Shows recognized text in italics with quotes.
- **CORRECT:** Green check icon + "Correct!" in green.
- **WRONG:** Red X icon + "Try again ({attempts}/3)" or "Moving on..." if completed.
- **SKIPPED:** Gray "Skipped" text.

### 11.6.7 Answer Rating Buttons

Shown on the card back after flipping. Four buttons in a 2x2 grid:

| Button | Color     | Style          | Content                           |
|--------|-----------|----------------|-----------------------------------|
| Again  | Error     | Outlined       | "Again" + "<1m"                   |
| Hard   | Orange    | Outlined       | "Hard" + "{current interval}d"    |
| Good   | Primary   | Filled Button  | "Good" + "{next interval}d"       |
| Easy   | Green     | Filled Button  | "Easy" + "{+2 interval}d"         |

### 11.6.8 Completion Screen (VocabDrillCompletionScreen)

- Animated reveal: stats appear after 800ms delay.
- Title: "Perfect!" (all correct) or "Done!".
- Stats card showing correct count (primary color) and wrong count (error color) side by side.
- "{N} words reviewed" subtitle.
- Two buttons: "Exit" (outlined) and "Continue" (filled).

---

## 11.7 Integration with Daily Practice

### 11.7.1 Daily Practice Block 2 Overview

The `DailySessionComposer` builds block 2 of the daily practice session as vocab flashcards. This block produces `DailyTask.VocabFlashcard` tasks, which are rendered in `DailyPracticeScreen.kt` alongside translation and verb drill blocks.

**Block size:** 10 cards (`VOCAB_COUNT = 10`), though the daily practice UI may show only 5 depending on session configuration.

### 11.7.2 Card Selection Algorithm (Daily Practice)

The daily practice vocab selection is a **pure SRS-based** algorithm, independent of lesson level. It does not use the rank range filter from the standalone drill.

**Algorithm (`buildVocabBlock()`):**

1. Load all vocab words from the pack via `loadVocabWords()`.
2. Load all mastery states from `WordMasteryStore` (pack-scoped).
3. Categorize words into three buckets:
   - **Due words:** `nextReviewDateMs <= now` (past their scheduled review). Paired with `overdueMs = now - nextReviewDateMs`.
   - **New words:** No mastery record exists (never reviewed).
   - **Scheduled words:** `nextReviewDateMs > now` (not yet due). Paired with `lastReviewDateMs` for fallback.
4. Sort due words by **most overdue first** (descending `overdueMs`).
5. Select cards in priority order:
   - Take due words, most overdue first (up to 10).
   - If fewer than 10, fill with new words sorted by rank ascending.
   - If still fewer than 10, fill with least-recently-reviewed scheduled words.
6. Assign alternating directions: even index = `IT_TO_RU`, odd index = `RU_TO_IT`.
7. Return `DailyTask.VocabFlashcard` list.

### 11.7.3 Repeat Session Behavior

When the user selects "Repeat" in daily practice:
- Block 1 (translation): repeats the exact same card IDs.
- **Block 2 (vocab): always rebuilds from SRS.** The repeat session calls `buildVocabBlock()` again independently, which may return different cards if SRS state has changed. Vocab flashcards are not repeated from a stored ID list.
- Block 3 (verb drill): repeats the exact same card IDs.

### 11.7.4 Shared vs Separate State

| Aspect                    | Standalone Drill              | Daily Practice Block 2            |
|---------------------------|-------------------------------|-----------------------------------|
| ViewModel                 | `VocabDrillViewModel`         | `TrainingViewModel` + helpers     |
| Mastery store             | `WordMasteryStore` (pack-scoped) | `WordMasteryStore` (pack-scoped) |
| Persistence file          | Same `word_mastery.yaml`      | Same `word_mastery.yaml`          |
| Word loading              | Same `ItalianDrillVocabParser`| Same parser                       |
| Card selection            | Due-by-rank, user filters     | SRS-priority (overdue > new > fallback) |
| Direction assignment      | User-chosen                   | Alternating even/odd              |
| Answer rating             | 4-button Anki (Again/Hard/Good/Easy) | Simple correct/incorrect     |
| Voice input               | Optional, with mic button     | Follows daily practice voice mode |
| Session size              | Up to 10 cards                | 10 cards (may be truncated to 5)  |

**Key point:** Both contexts share the same `WordMasteryStore` persistence file, so progress made in standalone drill affects daily practice card selection and vice versa.

### 11.7.5 Progress Across Both Contexts

Since both the standalone drill and daily practice write to the same `word_mastery.yaml` file via `WordMasteryStore.upsertMastery()`:

- A word answered "Good" in standalone drill will have its `intervalStepIndex` incremented and `nextReviewDateMs` advanced, making it less likely to appear in the next daily practice session.
- A word marked "Again" in either context resets to step 0 and becomes due the next day.
- The `isLearned` flag is shared: a word marked learned in one context appears as learned in the other.

---

## 11.8 CSV Format Specifications

All vocab drill CSV files are located in the pack's vocab drill directory after import. Default files are under `assets/grammarmate/vocab/it/`.

### 11.8.1 Universal Parsing Rules

- **Encoding:** UTF-8.
- **Header row:** Required. The parser auto-detects format from header column names.
- **Quote handling:** Double-quoted fields are supported (comma inside quotes is treated as part of the field value).
- **Blank lines:** Skipped.
- **Collocation separator:** Semicolon (`;`) within the collocations column.
- **Russian meaning:** May contain forward slash (`/`) to separate synonyms (e.g. "быть/являться").
- **Russian column detection:** The parser looks for columns named `ru`, `meaning_ru`, or `russian`.

### 11.8.2 Verbs CSV (`drill_verbs.csv`)

**Header:** `rank,verb,collocations,ru`

| Column        | Type   | Required | Description                                          |
|---------------|--------|----------|------------------------------------------------------|
| `rank`        | Int    | Yes      | Frequency rank                                       |
| `verb`        | String | Yes      | Italian infinitive form (e.g. "essere", "avere")     |
| `collocations`| String | No       | Semicolon-separated Italian phrases                  |
| `ru`          | String | No       | Russian translation, `/`-separated synonyms          |

**Example:**
```csv
rank,verb,collocations,ru
1,essere,eri consapevole;sarai capace;sono stanco,быть/являться/существовать
15,avere,avere caldo;avere fame;avere fretta,иметь/обладать
79,volere,,хотеть/желать
```

### 11.8.3 Nouns CSV (`drill_nouns.csv`)

**Header:** `rank,noun,collocations,ru`

| Column        | Type   | Required | Description                                          |
|---------------|--------|----------|------------------------------------------------------|
| `rank`        | Int    | Yes      | Frequency rank                                       |
| `noun`        | String | Yes      | Italian noun (e.g. "casa", "tempo")                  |
| `collocations`| String | No       | Semicolon-separated Italian phrases                  |
| `ru`          | String | No       | Russian translation, `/`-separated synonyms          |

**Example:**
```csv
rank,noun,collocations,ru
106,casa,bella casa;casa di famiglia;casa di moda,дом/жилище/семья
132,tempo,passare il tempo;perdere tempo;tempo libero,время/погода
```

### 11.8.4 Adjectives CSV (`drill_adjectives.csv`)

**Header:** `rank,adjective,msg,fsg,mpl,fpl,collocations,ru`

| Column        | Type   | Required | Description                              |
|---------------|--------|----------|------------------------------------------|
| `rank`        | Int    | Yes      | Frequency rank                           |
| `adjective`   | String | Yes      | Italian adjective lemma                  |
| `msg`         | String | No       | Masculine singular form                  |
| `fsg`         | String | No       | Feminine singular form                   |
| `mpl`         | String | No       | Masculine plural form                    |
| `fpl`         | String | No       | Feminine plural form                     |
| `collocations`| String | No       | Semicolon-separated Italian phrases      |
| `ru`          | String | No       | Russian translation, `/`-separated       |

**Example:**
```csv
rank,adjective,msg,fsg,mpl,fpl,collocations,ru
776,solito,solito,solita,soliti,solite,,обычный/привычный
874,pazzo,pazzo,pazza,pazzi,pazze,,сумасшедший/безумный
1566,giovane,giovane,giovane,giovani,giovani,,молодой
```

Forms are stored in the `forms` map with keys: `msg`, `fsg`, `mpl`, `fpl`.

### 11.8.5 Adverbs CSV (`drill_adverbs.csv`)

**Header:** `rank,adverb,comparative,superlative,ru,collocations`

| Column        | Type   | Required | Description                              |
|---------------|--------|----------|------------------------------------------|
| `rank`        | Int    | Yes      | Frequency rank                           |
| `adverb`      | String | Yes      | Italian adverb                           |
| `comparative` | String | No       | Comparative form (e.g. "meglio")         |
| `superlative` | String | No       | Superlative form (e.g. "ottimamente")    |
| `ru`          | String | No       | Russian translation, `/`-separated       |
| `collocations`| String | No       | Semicolon-separated Italian phrases      |

**Example:**
```csv
rank,adverb,comparative,superlative,ru,collocations
2,non,,,не,
34,bene,meglio,ottimamente,хорошо,
55,ora,,,сейчас / час,
```

**Note:** The parser currently reads collocations from column index 5 (the last column). Comparative and superlative forms are parsed but not currently stored on `ItalianDrillRow` or `VocabWord`.

### 11.8.6 Numbers CSV (`drill_numbers.csv`)

**Header:** `category,italian,ru,form_m,form_f,notes`

| Column     | Type   | Required | Description                                |
|------------|--------|----------|--------------------------------------------|
| `category` | String | Yes      | Number category (e.g. "cardinal")          |
| `italian`  | String | Yes      | Italian word for the number                |
| `ru`       | String | No       | Russian translation                        |
| `form_m`   | String | No       | Masculine form (e.g. "uno")                |
| `form_f`   | String | No       | Feminine form (e.g. "una")                 |
| `notes`    | String | No       | Usage notes; parsed as collocations if present |

**No rank column.** Synthetic rank is assigned based on row order (1, 2, 3, ...).

**Example:**
```csv
category,italian,ru,form_m,form_f,notes
cardinal,zero,ноль,,,invariable
cardinal,uno,один,uno,una,changes gender; before consonant uno/una
cardinal,due,два,,,invariable
```

Forms are stored with keys `form_m` and `form_f`. Notes are treated as collocations (semicolon-split).

### 11.8.7 Pronouns CSV (`drill_pronouns.csv`)

**Header:** `type,category,person,form_sg_m,form_sg_f,form_pl_m,form_pl_f,notes,ru`

| Column      | Type   | Required | Description                                |
|-------------|--------|----------|--------------------------------------------|
| `type`      | String | Yes      | Pronoun type (e.g. "personale")            |
| `category`  | String | Yes      | Subcategory (e.g. "soggetto", "oggetto_diretto") |
| `person`    | String | Yes      | Person code (e.g. "1s", "2s", "3sm")       |
| `form_sg_m` | String | Yes      | Singular masculine form (used as main word) |
| `form_sg_f` | String | No       | Singular feminine form                      |
| `form_pl_m` | String | No       | Plural masculine form                       |
| `form_pl_f` | String | No       | Plural feminine form                        |
| `notes`     | String | No       | Usage notes; parsed as collocations         |
| `ru`        | String | No       | Russian translation                         |

**No rank column.** Synthetic rank is assigned based on row order.

**Example:**
```csv
type,category,person,form_sg_m,form_sg_f,form_pl_m,form_pl_f,notes,ru
personale,soggetto,1s,io,io,io,io,first person singular subject,я
personale,soggetto,3sf,lei,lei,lei,lei,"feminine singular third person subject; formal 'you' singular",она/Вы
```

The main `word` for a pronoun row is `form_sg_m` (column index 3). Forms are stored with keys `form_sg_m`, `form_sg_f`, `form_pl_m`, `form_pl_f`.

### 11.8.8 Filename-to-POS Derivation

The POS key is derived from the CSV filename by stripping prefixes and suffixes:

```
drill_nouns.csv      -> "nouns"
drill_verbs.csv      -> "verbs"
drill_adjectives.csv -> "adjectives"
drill_adverbs.csv    -> "adverbs"
drill_numbers.csv    -> "numbers"
drill_pronouns.csv   -> "pronouns"
it_drill_nouns.csv   -> "nouns"  (language prefix also stripped)
```

---

## 11.9 User Stories

### US-11.1: Start a Flashcard Session
**As a** language learner,
**I want to** open the vocab drill screen and start a flashcard session,
**So that** I can practice vocabulary words that are due for review.

**Acceptance criteria:**
- The screen shows available POS categories as filter chips.
- It shows the number of due words out of total words for the current filters.
- The "Start" button shows the due count and is disabled when no words are due.
- Tapping "Start" begins a session with up to 10 due words, sorted by frequency rank.

### US-11.2: Filter Words by Part of Speech
**As a** learner who wants to focus,
**I want to** filter flashcards to a specific part of speech,
**So that** I can practice only nouns, only verbs, etc.

**Acceptance criteria:**
- Filter chips show "All" plus one chip per available POS.
- Selecting a chip immediately updates the due count and total count.
- Only one POS can be selected at a time (mutually exclusive).
- Selecting "All" shows words from all categories.

### US-11.3: Filter Words by Frequency Rank
**As a** learner at a specific level,
**I want to** limit flashcards to the most frequent words,
**So that** I focus on the most useful vocabulary first.

**Acceptance criteria:**
- Rank range filter chips: "Top 100", "Top 500", "Top 1000", "All".
- Selecting a range immediately updates the due count.
- Only one range can be active at a time.

### US-11.4: Flip a Flashcard
**As a** learner,
**I want to** tap to flip a flashcard and see the answer,
**So that** I can check if I know the word before rating myself.

**Acceptance criteria:**
- Card front shows the prompt (Italian word or Russian meaning depending on direction).
- Tapping "Flip" reveals the card back with the answer.
- The flip toggles the card state between front and back.

### US-11.5: Rate My Recall (Anki-style)
**As a** learner using spaced repetition,
**I want to** rate my recall as Again, Hard, Good, or Easy after seeing the answer,
**So that** the system adjusts the review interval appropriately.

**Acceptance criteria:**
- Four buttons appear after flipping: Again, Hard, Good, Easy.
- Each button shows the resulting review interval (e.g. "<1m", "4d", "7d").
- "Again" resets the word to step 0 (due <1m).
- "Hard" keeps the current step.
- "Good" advances one step on the interval ladder.
- "Easy" advances two steps.
- The word's mastery is persisted immediately after rating.

### US-11.6: Practice in Both Directions
**As a** learner building bidirectional recall,
**I want to** choose between IT->RU and RU->IT directions,
**So that** I can practice both recognizing Italian words and producing them from Russian.

**Acceptance criteria:**
- Direction filter chips are shown on the selection screen.
- IT->RU shows Italian word on front, Russian meaning on back.
- RU->IT shows Russian meaning on front, Italian word on back.
- Direction can be changed before starting a session.
- In daily practice, directions alternate (even index = IT->RU, odd = RU->IT).

### US-11.7: Use Voice Input
**As a** learner practicing pronunciation,
**I want to** speak my answer using voice recognition,
**So that** I can practice without typing.

**Acceptance criteria:**
- Voice auto-start is controlled by the global Settings toggle (`AudioState.voiceAutoStart`), NOT a per-drill toggle.
- When voiceAutoStart is ON, the mic launches automatically on each new card (after 500ms delay).
- When voiceAutoStart is OFF, the mic button is still available for manual tap on the card front — it always works.
- Correct pronunciation auto-flips the card after 800ms.
- Wrong attempts show "Try again (N/3)" and allow up to 3 attempts.
- After 3 wrong attempts or tapping Skip, the card auto-flips.
- Language tag for voice recognition matches the expected answer language (ru-RU or it-IT).

### US-11.8: Hear Word Pronunciation (TTS)
**As a** learner,
**I want to** tap a button to hear the Italian word pronounced,
**So that** I can learn the correct pronunciation.

**Acceptance criteria:**
- TTS button (volume icon) appears on the card front in IT->RU direction.
- TTS button appears on the card back for both directions.
- TTS plays the Italian word at 0.67x speed for clarity.
- TTS states: IDLE, INITIALIZING (spinner), SPEAKING (highlighted), ERROR (red X).

### US-11.9: See Word Forms
**As a** learner studying adjectives,
**I want to** see all gender/number forms of a word on the card back,
**So that** I learn masculine, feminine, singular, and plural forms.

**Acceptance criteria:**
- For words with forms (adjectives, numbers, pronouns), a "Forms" card is displayed on the back.
- Shows a 4-column grid: m sg, f sg, m pl, f pl (or relevant subset).
- Words without forms do not show the forms card.

### US-11.10: See Collocations
**As a** learner building contextual knowledge,
**I want to** see common phrases using the word on the card back,
**So that** I understand how the word is used in context.

**Acceptance criteria:**
- Up to 5 collocations are shown on the card back.
- If more than 5 exist, "+N more" text is displayed.
- Words without collocations do not show the section.

### US-11.11: View Session Results
**As a** learner,
**I want to** see a summary after completing a flashcard session,
**So that** I know how well I performed.

**Acceptance criteria:**
- Completion screen shows correct count (blue) and wrong count (red).
- "Perfect!" is shown when all answers are correct.
- "Done!" is shown otherwise.
- Total words reviewed is displayed.
- Stats animate in after 800ms delay.
- "Exit" returns to the selection screen.
- "Continue" starts a new session with the next batch of due words.

### US-11.12: Track Mastery Progress
**As a** learner,
**I want to** see how many words I have mastered and my overall progress,
**So that** I stay motivated and understand my learning trajectory.

**Acceptance criteria:**
- Selection screen shows "Mastered: N words" in green when any words are mastered.
- Per-POS breakdown is shown (e.g. "Nouns: 15 | Verbs: 8").
- Progress bar shows the ratio of learned words to total words.
- "Step N/9" or "Learned" is shown on each card back.
- Words at step 3+ are considered learned.

### US-11.13: Practice Vocab in Daily Practice
**As a** daily practice user,
**I want to** have vocab flashcards appear as block 2 of my daily practice,
**So that** vocab review is integrated into my daily routine.

**Acceptance criteria:**
- Daily practice automatically includes a vocab block with up to 10 flashcards.
- Card selection is SRS-based: most overdue first, then new words, then least-recently-reviewed.
- Directions alternate between IT->RU and RU->IT.
- Progress made in daily practice updates the same mastery store as standalone drill.
- When repeating a daily session, vocab cards are re-selected from SRS (not repeated from stored IDs).

### US-11.14: Pack-Scoped Vocab Data
**As a** user with multiple lesson packs,
**I want to** practice vocab from the currently active pack only,
**So that** I focus on relevant vocabulary for my current course.

**Acceptance criteria:**
- Vocab data is scoped to the active pack ID.
- Mastery data is stored per-pack in `drills/{packId}/word_mastery.yaml`.
- Switching packs reloads words and mastery from the new pack.
- The HomeScreen shows the vocab drill tile only when the active pack declares a `vocabDrill` section in its manifest.

### US-11.15: Skip a Card Without Rating
**As a** learner,
**I want to** skip a card without providing a voice answer,
**So that** I can move on if I am unsure or do not want to use voice input.

**Acceptance criteria:**
- "Skip" button is visible on the card front (before flipping).
- If voice is not yet completed, Skip marks voice as SKIPPED and auto-flips the card.
- If voice is already completed, Skip triggers a flip.
- The user can still rate the card after skipping voice input.

### US-11.16: Resume from Saved State
**As a** user who left the app mid-session,
**I want to** find my vocab drill progress preserved,
**So that** I do not lose my spaced repetition history.

**Acceptance criteria:**
- Word mastery state is persisted to YAML after every answer.
- When returning to the drill screen, due counts reflect previously saved mastery.
- The session itself is not restored (user starts a new session), but all per-word progress is intact.

---

## 11.10 Bad Card Reporting

### 11.10.1 Overview

Vocab drill supports full bad card flagging, unflagging, and export. Flagged cards are persisted in `BadSentenceStore` pack-scoped, with `mode = "vocab_drill"`. The report button is available on the card screen during a session.

### 11.10.2 Methods

`VocabDrillViewModel` provides the following bad sentence methods:

| Method | Description |
|--------|-------------|
| `flagBadSentence()` | Adds current vocab card to `BadSentenceStore`. Uses `activePackId` if available, otherwise falls back to `"__vocab_drill__"`. Sets `mode = "vocab_drill"`, `sentence = word.meaningRu ?: word.word`, `translation = word.word`. |
| `unflagBadSentence()` | Removes current vocab card from `BadSentenceStore`. |
| `isBadSentence()` | Checks if the current vocab card is flagged. |
| `exportBadSentences()` | Exports bad sentences for the active pack via `BadSentenceStore.exportToTextFile(packId)`. Returns file path or `null` if no bad sentences exist. |

### 11.10.3 Mode Field

The `mode` field in `BadSentenceEntry` is set to `"vocab_drill"` for all vocab drill flagged cards. This distinguishes vocab drill reports from training (`"training"`), verb drill (`"verb_drill"`), and daily practice reports.

### 11.10.4 Pack Scoping

When `activePackId` is set (via `reloadForPack()`), bad sentences are scoped to that pack. When no pack is active, the fallback pack ID `"__vocab_drill__"` is used. This ensures flagged cards are correctly grouped during export.

### 11.10.5 Report Button Location

The report button is available on the vocab drill card screen header row (right side), rendered as a `ReportProblem` icon. When the current card is already flagged, the icon tint changes to `colorScheme.error`. Tapping it opens a `ModalBottomSheet` with:

- Card word and meaning displayed at the top (e.g. "essere -- быть/являться").
- **"Add to bad sentences list"** (or "Remove from bad sentences list" if already flagged) with `ReportProblem` icon.
- **"Export bad sentences to file"** with `Download` icon -- exports via `BadSentenceStore.exportToTextFile(packId)`, shows result in an `AlertDialog`.
- **"Copy text"** with `ContentCopy` icon -- copies a formatted string to clipboard:
  ```
  ID: {card.word.id}
  Word: {card.word.word}
  Meaning: {card.word.meaningRu}
  ```

---

## 11.11 UI Consistency — Voice Input (Auto) Reference Implementation [UI-CONSISTENCY-2025]

This section documents VocabDrill's voice auto mode as the **SOURCE OF TRUTH** for adoption by VerbDrill. All other screens implementing voice auto mode must match this implementation.

---

### 11.11.1 Voice Auto Mode — Reference Implementation [UI-CONSISTENCY-2025]

VocabDrillScreen's voice auto mode uses the global `voiceAutoStart` setting. The per-drill `voiceModeEnabled` toggle has been removed.

**Key implementation elements:**

1. **~~Selection screen Switch toggle~~ REMOVED:** Voice auto-start is controlled by the global Settings toggle (`AudioState.voiceAutoStart`) only. No per-drill toggle exists.

2. **Auto-launch LaunchedEffect:** Watches `voiceAutoStart` (passed as parameter from GrammarMateApp), `isFlipped`, `voiceCompleted`, and current card index. When voice auto-start is on and card is not flipped/complete, auto-launches RecognizerIntent after **500ms delay**. When voice auto-start is OFF, mic button still works on manual click.

3. **Voice mode state:** `VocabDrillUiState.voiceModeEnabled` was REMOVED. The `voiceAutoStart` flag is received as a parameter to `VocabDrillScreen` from `GrammarMateApp`, which reads it from `AudioState.voiceAutoStart`.

4. **Auto-advance after correct answer:** When voice result is `CORRECT`, auto-flips the card after **800ms delay** (via `delay(800)` coroutine).
   - Class path: `ui/screens/VocabDrillScreen.kt:401-406` — auto-flip after correct

5. **Card front Mic button:** Large 72dp mic button on card front for manual voice trigger. Shows "Tap to speak" label when voice is not completed.
   - Class path: `ui/screens/VocabDrillScreen.kt:797-800` — Mic button composable

**Shared component to extract:**

The voice auto-launch logic should be extracted into `ui/components/VoiceAutoLauncher.kt` — a single reusable composable that both VocabDrill and VerbDrill use.

Parameters for the shared component:
- `enabled: Boolean` — whether voice auto mode is on
- `triggerToken: Int` — incremented to trigger launch
- `delayMs: Long = 500` — delay before launching recognizer
- `onLaunch: () -> Unit` — callback to launch RecognizerIntent
- `languageTag: String` — language for recognizer (e.g. "it-IT", "ru-RU")

**Regression class paths:**
- `ui/VocabDrillScreen.kt` — receives `voiceAutoStart` parameter, auto-launch LaunchedEffect
- `ui/components/VoiceAutoLauncher.kt` — shared component
- `ui/GrammarMateApp.kt` — passes `voiceAutoStart` to VocabDrillScreen
- `data/VocabWord.kt` — `VocabDrillUiState` (voiceModeEnabled removed)
- `ui/VerbDrillScreen.kt` — same pattern, uses global voiceAutoStart

---

### 11.11.2 Report Sheet — 4-Option Standard Adoption [UI-CONSISTENCY-2025]

VocabDrill's report sheet must adopt the 4-option standard from TrainingScreen, adding the missing "Hide this card from lessons" option.

**Current state:** 3 options (flag/unflag, export, copy).
**Required state:** 4 options matching TrainingScreen (flag/unflag, HIDE CARD, export, copy).

See section 10.9.3 in `docs/specification/10-verb-drill.md` for the full 4-option specification.

**Shared component:** `ui/components/SharedReportSheet.kt` (NEW).

**Regression class paths:**
- `ui/screens/VocabDrillScreen.kt` — VocabDrill report sheet (update to 4 options)
- `ui/components/SharedReportSheet.kt` — shared report sheet (NEW)
- `ui/screens/TrainingScreen.kt` — reference 4-option layout


---


# 12. Training Card Session -- Specification

## 12.1 Overview

TrainingCardSession is a reusable Compose composable that provides the entire card-based training UI for GrammarMate. It abstracts card presentation, answer input, validation, feedback display, navigation, and session completion into a single parameterized component driven by the `CardSessionContract` interface.

The component is used by three distinct training modes:

1. **Standard lesson training** (via `TrainingCardSessionProvider` -- not yet migrated, uses the default slots in GrammarMateApp.kt).
2. **Verb Drill** (via `VerbDrillCardSessionProvider` wrapping `VerbDrillViewModel`).
3. **Daily Practice Blocks 1 and 3** -- translation and verb conjugation (via `DailyPracticeSessionProvider`).

Block 2 (Vocab Flashcard) does **not** use TrainingCardSession -- it renders an Anki-style flashcard UI instead.

The key design principle: the only difference between training modes is **card selection and scoring logic**, not the card training UI. All modes share the same visual layout, input methods, feedback animations, and navigation pattern.

### Files

| File | Purpose |
|------|---------|
| `data/CardSessionContract.kt` | Interface definitions: `SessionCard`, `CardSessionContract`, `CardSessionCapabilities`, `SessionProgress`, `AnswerResult`, `InputModeConfig` |
| `ui/TrainingCardSession.kt` | `TrainingCardSession` composable, `TrainingCardSessionScope`, all default slot implementations |
| `ui/VerbDrillCardSessionProvider.kt` | Adapter wrapping `VerbDrillViewModel` to implement `CardSessionContract` |
| `feature/daily/DailyPracticeSessionProvider.kt` | Adapter for Daily Practice translation and verb blocks |
| `ui/VerbDrillScreen.kt` | Verb Drill screen: selection screen + `TrainingCardSession` with custom slots + reference sheets |
| `ui/DailyPracticeScreen.kt` | Daily Practice screen: 3-block session with `TrainingCardSession` for blocks 1 and 3 |

---

## 12.2 CardSessionContract

### 12.2.1 SessionCard

The polymorphic card interface that both `SentenceCard` and `VerbDrillCard` implement:

```kotlin
interface SessionCard {
    val id: String
    val promptRu: String
    val acceptedAnswers: List<String>
}
```

Implementors:

- **`SentenceCard`** (in `data/Models.kt`): fields `id`, `promptRu`, `acceptedAnswers: List<String>`, optional `tense: String?`. Maps 1:1 with lesson CSV rows.
- **`VerbDrillCard`** (in `data/VerbDrillCard.kt`): fields `id`, `promptRu`, `answer: String`, optional `verb`, `tense`, `group`, `rank`. Implements `acceptedAnswers` as `listOf(answer)`.

### 12.2.2 CardSessionCapabilities

Optional capability flags that adapters declare. All default to `false`:

| Capability | Meaning |
|------------|---------|
| `supportsTts` | Shows TTS speaker button on card and in result feedback |
| `supportsVoiceInput` | Shows mic trailing icon on text field, voice mode selector button |
| `supportsWordBank` | Shows word bank FlowRow and word bank mode button |
| `supportsFlagging` | Shows report/flag button that opens bottom sheet |
| `supportsNavigation` | Shows bottom navigation row (prev/pause/exit/next) |
| `supportsPause` | Shows pause/play toggle button in navigation |

### 12.2.3 Supporting Data Classes

**`SessionProgress(current: Int, total: Int)`** -- 1-based position counter for display.

**`AnswerResult(correct: Boolean, displayAnswer: String, hintShown: Boolean = false)`** -- result of checking an answer. `displayAnswer` is the canonical form shown to the user.

**`InputModeConfig(availableModes: Set<InputMode>, defaultMode: InputMode, showInputModeButtons: Boolean)`** -- declares which input modes are available and which is the default for the current session.

### 12.2.4 CardSessionContract Interface

Full interface definition with all properties and actions:

**Properties (read by the composable):**

| Property | Type | Description |
|----------|------|-------------|
| `currentCard` | `SessionCard?` | The card currently displayed. Null when session is complete or loading. |
| `progress` | `SessionProgress` | 1-based `(current, total)` for progress display |
| `isComplete` | `Boolean` | True when all cards have been processed |
| `inputText` | `String` | Current input text (adapters may return "" if input is composable-managed) |
| `inputModeConfig` | `InputModeConfig` | Available input modes and default |
| `lastResult` | `AnswerResult?` | Non-null when result feedback should be shown instead of input controls |
| `sessionActive` | `Boolean` | False when paused or waiting for hint acknowledgment |
| `ttsState` | `TtsState` | Current TTS playback state (default: `IDLE`) |
| `currentInputMode` | `InputMode` | Currently selected input mode (default: `inputModeConfig.defaultMode`) |
| `languageId` | `String` | Language ID for voice recognition locale resolution (default: `"en"`) |
| `currentSpeedWpm` | `Int` | Current typing speed in words per minute (default: `0`) |
| `textScale` | `Float` | Font size multiplier for prompt text in default slots (default: `1.0f`). Range [1.0, 2.0]. Propagated from `ruTextScale` in Settings. |

**Actions (called by the composable):**

| Method | Description |
|--------|-------------|
| `onInputChanged(text)` | User typed or modified input text |
| `submitAnswer(): AnswerResult?` | Submit current input for validation. Returns null if adapter manages its own result state. |
| `showAnswer(): String?` | Reveal the correct answer (eye button) |
| `nextCard()` | Advance to the next card |
| `prevCard()` | Go back to the previous card |
| `onVoiceInputResult(text)` | Called when voice recognition returns a result (default: delegates to `onInputChanged`) |
| `setInputMode(mode)` | Switch input mode (VOICE/KEYBOARD/WORD_BANK) |
| `getSelectedWords(): List<String>` | Get currently selected word bank words in order |
| `getWordBankWords(): List<String>` | Get shuffled word bank for current card |
| `selectWordFromBank(word)` | Add a word to the word bank selection |
| `removeLastSelectedWord()` | Remove the last selected word bank word (undo) |
| `speakTts()` | Start TTS playback for current card |
| `stopTts()` | Stop TTS playback |
| `flagCurrentCard()` | Flag current card as bad |
| `unflagCurrentCard()` | Remove bad flag from current card |
| `isCurrentCardFlagged(): Boolean` | Check if current card is flagged |
| `hideCurrentCard()` | Hide current card from future lessons |
| `exportFlaggedCards(): String?` | Export all flagged cards to file, return path |
| `togglePause()` | Toggle session pause state |
| `requestExit()` | Exit the session (called after confirmation dialog) |
| `requestNextBatch()` | Request the next batch of cards (used by Verb Drill completion) |

---

## 12.3 Card Lifecycle

### 12.3.1 State Machine

Each card progresses through these states:

```
  PRESENTING --> SUBMITTED (correct) --> RESULT_SHOWN --> NEXT
       |                                               ^
       |           (wrong, attempts < 3)                |
       +--> INCORRECT_FEEDBACK --> PRESENTING (retry) -+
       |
       |           (wrong, attempts >= 3)
       +--> HINT_SHOWN --> NEXT (via play/pause or next)
       |
       |           (manual eye button)
       +--> HINT_SHOWN --> NEXT
```

**State determination:** The composable determines which state to render based on adapter properties:

- `currentCard != null && lastResult == null` --> PRESENTING (show input controls)
- `currentCard != null && lastResult != null` --> RESULT_SHOWN (show result content)
- `currentCard == null && isComplete` --> COMPLETION (show completion screen)

Adapters that implement the retry/hint flow (`VerbDrillCardSessionProvider`, `DailyPracticeSessionProvider`) add an intermediate state where `lastResult` is NOT set but a hint is displayed. This is tracked via adapter-specific fields (`hintAnswer`, `showIncorrectFeedback`) that the custom `inputControls` slot reads.

### 12.3.2 Submit and Validate Flow

The answer validation pipeline:

1. User enters text (keyboard), speaks (voice), or selects words (word bank).
2. `submitAnswer()` or adapter-specific `submitAnswerWithInput(input)` is called.
3. The adapter normalizes both user input and accepted answers using `Normalizer.normalize()`.
4. Normalization: trim, collapse whitespace, remove time colons, lowercase, strip diacritics, strip punctuation.
5. Comparison: normalized input is compared against each normalized accepted answer.
6. Result: `AnswerResult(correct, displayAnswer)` is set, or for wrong answers the retry/hint flow activates.

### 12.3.3 Retry and Hint Flow

Both `VerbDrillCardSessionProvider` and `DailyPracticeSessionProvider` implement an identical retry/hint flow:

1. **Correct answer:** Set `pendingCard` and `pendingAnswerResult`. The card stays visible for feedback. ViewModel index is NOT advanced yet.
2. **Wrong answer (attempt < 3):** Show inline "Incorrect" feedback with remaining attempt count. Input stays visible. If in VOICE mode, auto-trigger voice recognition after a 1200ms delay.
3. **Wrong answer (attempt >= 3):** Auto-show answer as hint in an error-colored card. Set `isPaused = true`. Input controls stay visible below the hint card.
4. **Manual "Show Answer" (eye button):** Same as attempt >= 3: show hint card, pause session.
5. **Advancement:** `nextCard()` clears all pending state and advances the index. If a hint was shown, the card is marked as completed (no credit). If a correct answer was pending, the ViewModel records it.

### 12.3.4 Batch Management

Cards are presented in batches. The adapter manages a fixed-size batch:

- **Verb Drill:** `VerbDrillViewModel` loads 10 cards per session. `nextBatch()` loads 10 more.
- **Daily Practice:** `DailySessionComposer.CARDS_PER_BLOCK = 10`. Translation block = 10 cards, Verb block = 10 cards. When all cards in a block are done, `onBlockComplete()` is called.

### 12.3.5 Word Bank Generation

When input mode is WORD_BANK, the adapter generates a shuffled word bank:

1. Split the correct answer into individual words.
2. Collect distractor words from other cards in the session (words not in the correct answer).
3. Take up to `max(0, 8 - answerWordCount)` distractors.
4. Shuffle answer words + distractors together.
5. Cache per card ID to avoid regeneration on recomposition.

Word bank selection enforces duplicate counts: if the answer contains "io" twice, the user can select "io" up to twice. Fully-used words are disabled in the FlowRow.

---

## 12.4 UI Components

### 12.4.1 TrainingCardSession Composable Signature

```kotlin
@Composable
fun TrainingCardSession(
    contract: CardSessionContract,
    header: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    cardContent: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    inputControls: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    resultContent: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    navigationControls: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    completionScreen: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    progressIndicator: (@Composable TrainingCardSessionScope.() -> Unit)? = null,
    onExit: () -> Unit,
    onComplete: () -> Unit = {},
    modifier: Modifier = Modifier
)
```

All customization slots receive a `TrainingCardSessionScope` that provides access to the contract, current card, result state, input text, and action callbacks.

### 12.4.2 TrainingCardSessionScope

```kotlin
@Stable
class TrainingCardSessionScope(
    val contract: CardSessionContract,
    val currentCard: SessionCard?,
    val isShowingResult: Boolean,
    val lastResult: AnswerResult?,
    val progressText: String,       // "N / Total"
    val inputText: String,
    val onInputChanged: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onPrev: () -> Unit,
    val onNext: () -> Unit,
    val onExit: () -> Unit
)
```

The scope is recreated when any of its inputs change, ensuring composables always read fresh values.

### 12.4.3 Default Header

When no custom `header` slot is provided:

1. **Tense label** (conditional): shown only when the current card is a `VerbDrillCard` with a non-blank `tense` field. Rendered in `primary` color, 13sp, `SemiBold`. **Always visible regardless of HintLevel.** The tense label is reference information about the verb form, NOT a hint. HintLevel controls parenthetical hints in prompt text, not this label.
2. **Clean prompt text**: `promptRu` with parenthetical hints stripped via regex `\s*\([^)]+\)`. Rendered at 18sp, `Medium` weight.

Custom overrides: Verb Drill uses a custom header with back arrow + "Verb Drill" title (no settings gear). Daily Practice uses its own header with back arrow + "Daily Practice" title + block label chip.

### 12.4.4 Default Progress Indicator

A horizontal row split 70/30:

- **Left (70% weight):** Rounded progress bar. Green fill (`#4CAF50`) on light green track (`#C8E6C9`). Text overlay `"N / Total"` in 12sp bold. Text color switches from dark green to white when the bar passes 12% fill (readability).
- **Right (30% weight):** Circular speedometer arc (44dp). Background arc in light gray. Foreground arc sweep angle = `360 * min(speedWpm, 100) / 100`. Color: red (`#E53935`) for 0-20 wpm, yellow (`#FDD835`) for 21-40 wpm, green (`#43A047`) for 41+ wpm. Center shows numeric wpm value.

### 12.4.5 Default Card Content

Material `Card` with full-width layout:

- Left column (weighted): "RU" label + prompt text (20sp, `SemiBold`) showing `promptRu` — parenthetical hints included on EASY, stripped on MEDIUM/HARD based on `HintLevel`.
- Right: `TtsSpeakerButton` (see 12.4.9).

Custom overrides: Verb Drill and Daily Practice both add SuggestionChip rows below the prompt showing verb, tense, and group info when the card is a `VerbDrillCard`.

#### Font Size Scaling Behavioral Contract

When `textScale` is set on the contract, all default slot implementations apply it to prompt and content text:

| User Action | System Response | User Outcome |
|-------------|----------------|--------------|
| User changes text scale in Settings | `textScale` prop updates on `CardSessionContract` | All prompt text in card resizes proportionally |
| `textScale = 1.5x` | `DefaultCardContent` applies `(20f * 1.5f).sp = 30sp` to `promptRu` | Card prompt text is 50% larger |
| `textScale = 2.0x` | `DefaultHeader` applies `(18f * 2.0f).sp = 36sp` to `cleanPrompt` | Header text is doubled |

**Scaling rules:**
- Prompt text (`promptRu`) in `DefaultCardContent`: `(20f * textScale).sp`
- Clean prompt text in `DefaultHeader`: `(18f * textScale).sp`
- Tense label in `DefaultHeader`: NOT scaled (fixed 13sp)
- "RU" badge, POS badge, attempt counter: NOT scaled
- Navigation buttons, check button, mode labels: NOT scaled

### 12.4.6 Default Input Controls

When no custom `inputControls` slot is provided. Includes:

1. **OutlinedTextField** -- "Your translation" label, single line. Trailing icon: mic button (when `supportsVoiceInput`). Mic button launches system speech recognition intent with the appropriate language tag.
2. **"No cards" error text** -- shown when `currentCard` is null.
3. **Voice mode hint** -- when `currentInputMode == VOICE`, shows "Say translation: {promptRu}" in muted text.
4. **Word bank FlowRow** -- when `currentInputMode == WORD_BANK` and `supportsWordBank`. FilterChips for each word, with duplicate tracking. "Selected: N / M" label and "Undo" TextButton.
5. **Input mode selector row** -- three FilledTonalIconButtons (Mic, Keyboard, LibraryBooks) on the left. Shown when `showInputModeButtons` is true or `availableModes.size > 1`.
6. **Show answer + Flag/Report buttons** -- Eye icon with "Show answer" tooltip on the right. Warning icon with "Report sentence" tooltip (when `supportsFlagging`).
7. **Current mode label** -- "Voice" / "Keyboard" / "Word Bank" text on the right.
8. **Check button** -- full-width, enabled only when input is not blank and card exists.

### 12.4.7 Default Result Content

When `isShowingResult == true`:

- **Correct:** Green "Correct" label (`#2E7D32`, bold). TTS replay button (when `supportsTts`). "Answer: {displayAnswer}" text.
- **Incorrect:** Red "Incorrect" label (`#C62828`, bold). TTS replay button. "Answer: {displayAnswer}" text.

### 12.4.8 Default Navigation Controls

Shown only when `supportsNavigation == true`. A horizontal row:

- **Left:** Prev button (ArrowBack icon).
- **Center group:** Pause/Play button (when `supportsPause`), Exit button (StopCircle icon), Next button (ArrowForward icon).

All navigation buttons use `NavIconButton` styling: 44dp square, `surfaceVariant` background with rounded corners (12dp), 3dp `primary` accent bar at the bottom.

**Exit button** opens an `AlertDialog` confirmation: "End session? Your progress will be saved." with "End" and "Cancel" buttons.

**Pause/Play button** toggles session active state via `togglePause()`.

### 12.4.9 TtsSpeakerButton

A reusable internal composable with 4 visual states based on `TtsState`:

| TtsState | Icon | Tint |
|----------|------|------|
| `SPEAKING` | `StopCircle` | `error` (red) |
| `INITIALIZING` | `CircularProgressIndicator` (24dp, 2dp stroke) | default |
| `ERROR` | `ReportProblem` | `error` (red) |
| `IDLE` / `READY` | `VolumeUp` (24dp) | default |

### 12.4.10 Default Completion Screen

Shown when `isComplete && !isShowingResult`:

- Party popper emoji at 48sp.
- "Well done!" text at 24sp, bold.
- Progress text (e.g. "10 / 10") in muted color.
- "Done" button (full width).

Custom overrides: Verb Drill shows stats (correct/incorrect counts) + "More" button + "Exit" outlined button.

---

## 12.5 Integration Points

### 12.5.1 Verb Drill Integration

**File:** `ui/VerbDrillScreen.kt`

Verb Drill uses `TrainingCardSession` with four custom slots:

| Slot | Custom Behavior |
|------|----------------|
| `header` | Back arrow + "Verb Drill" title (no settings gear) |
| `cardContent` | Default card + SuggestionChip row (verb with rank, tense abbreviated, group). Chips open `VerbReferenceBottomSheet` or `TenseInfoBottomSheet`. |
| `inputControls` | `DefaultVerbDrillInputControls`: adds hint answer card (error-colored), incorrect feedback with attempt count, auto-voice LaunchedEffect, word bank, report sheet, uses `submitAnswerWithInput()` for retry/hint flow. |
| `completionScreen` | Stats (correct/incorrect), "More" button (`requestNextBatch()`), "Exit" outlined button. |

**Auto-voice behavior:** A `LaunchedEffect` watches `voiceTriggerToken`, `currentInputMode`, `sessionActive`, and `currentCard.id`. When in VOICE mode with an active session and a card present, it auto-launches speech recognition after a 200ms delay (1200ms delay if `showIncorrectFeedback` is true -- retry after wrong answer).

**Auto-advance after correct voice answer:** A separate `LaunchedEffect` watches `pendingAnswerResult` and `currentInputMode`. When result is correct and mode is VOICE, it auto-advances to the next card after a 500ms delay.

**Adapter:** `VerbDrillCardSessionProvider` wraps `VerbDrillViewModel`. Capabilities: all true (TTS, voice, word bank, flagging, navigation, pause).

### 12.5.2 Daily Practice Integration

**File:** `ui/DailyPracticeScreen.kt`

Daily Practice uses `TrainingCardSession` for blocks 1 (Translation) and 3 (Verb Conjugation). Block 2 (Vocab Flashcard) uses a completely separate composable (`VocabFlashcardBlock`).

**Session flow:**

1. `DailyPracticeScreen` dispatches to `CardSessionBlock` for TRANSLATE and VERBS blocks.
2. `CardSessionBlock` creates a `DailyPracticeSessionProvider` scoped by `(blockIndex, taskIndex, tasks.size)`.
3. `DailyTrainingCardSession` wraps `TrainingCardSession` with custom `cardContent` (adds verb/tense chips like Verb Drill) and custom `inputControls` (`DailyInputControls`).
4. On block completion, `onAdvanceBlock()` moves to the next block. A `BlockSparkleOverlay` shows the transition animation.

**Customizations:**
- Custom header with back arrow, "Daily Practice" title, and block label chip (primaryContainer card).
- `BlockProgressBar` (separate from TrainingCardSession's progress indicator) shows overall session progress.
- Block transition sparkle overlay between blocks.
- `DailyPracticeCompletionScreen` at the end of all 3 blocks.

**Adapter:** `DailyPracticeSessionProvider`. Capabilities: TTS true, voice true, word bank true, flagging false (no report button in daily practice), navigation true, pause true.

**Progress persistence:** `onCardAdvanced` callback is called for each card advanced (only for non-WORD_BANK modes). For `ConjugateVerb` tasks, it calls `onPersistVerbProgress`. For all tasks, it calls `onCardPracticed` for cursor advancement tracking.

### 12.5.3 Standard Lesson Training

Standard training in `GrammarMateApp.kt` does NOT currently use the `TrainingCardSession` composable. It renders its own inline UI with equivalent functionality. The design spec envisions a `TrainingCardSessionProvider` adapter that would wrap `TrainingViewModel` to satisfy the contract, but this migration has not yet been performed.

### 12.5.4 Boss Battle

Boss Battle does NOT use `TrainingCardSession`. It has its own dedicated UI with timer, pressure mechanics, and scoring that do not fit the card session pattern.

---

## 12.6 State Management

### 12.6.1 Local UI State (Composable-owned)

The `TrainingCardSession` composable manages these local state variables:

| State | Type | Purpose |
|-------|------|---------|
| `localInputText` | `String` | Current text in the input field. Reset to "" on submit and on card advance. |
| `showExitDialog` | `Boolean` | Controls exit confirmation dialog visibility. |
| `showReportSheet` | `Boolean` | Controls report bottom sheet visibility. |
| `exportMessage` | `String?` | Shows export result in an AlertDialog. |

### 12.6.2 Adapter State (Compose-observable)

Adapters store their state in `mutableStateOf` fields for direct Compose recomposition:

**VerbDrillCardSessionProvider state:**

| Field | Type | Purpose |
|-------|------|---------|
| `pendingCard` | `VerbDrillCard?` | Card currently showing feedback |
| `pendingAnswerResult` | `AnswerResult?` | Result of the last correct submission |
| `isPaused` | `Boolean` | Whether session is paused (hint shown) |
| `incorrectAttempts` | `Int` | Consecutive wrong attempts for current card |
| `hintAnswer` | `String?` | Auto-shown answer after 3 wrong attempts or eye button |
| `showIncorrectFeedback` | `Boolean` | Whether inline "Incorrect" text is shown |
| `remainingAttempts` | `Int` | Attempts left before hint (starts at 3) |
| `_inputMode` | `InputMode` | Currently selected input mode |
| `voiceTriggerToken` | `Int` | Incremented to trigger auto-voice |
| `_selectedWords` | `List<String>` | Words selected in word bank |

**DailyPracticeSessionProvider state:**

Identical fields to VerbDrillCardSessionProvider, plus:
| Field | Type | Purpose |
|-------|------|---------|
| `currentIndex` | `Int` | Position within the block's card list |
| `pendingInput` | `String` | Text being composed before submit |

### 12.6.3 ViewModel State (Flow-owned)

Adapters read from their backing ViewModel's `StateFlow` for source-of-truth data:

- `VerbDrillViewModel.uiState` provides `VerbDrillSessionState` (cards list, currentIndex, correctCount, incorrectCount, isComplete).
- `VerbDrillViewModel.ttsState` provides the current TTS playback state.
- Daily Practice reads from `DailySessionState` via callbacks rather than a direct ViewModel reference.

### 12.6.4 Card Counter and Progress

Progress is always 1-based for display: `SessionProgress(current = index + 1, total = cards.size)`.

In the Verb Drill adapter, the index is advanced by `nextCard()` (not by `submitAnswer()`), so the displayed progress always matches the card being shown or answered.

In the Daily Practice adapter, `currentIndex` is incremented directly in `nextCard()`. The provider caps display at `blockCards.size` via `coerceAtMost`.

### 12.6.5 Adapter Lifecycle

Adapters are created via `remember` with a key:

- **Verb Drill:** `remember { VerbDrillCardSessionProvider(viewModel) }` -- persists across recompositions for the entire session.
- **Daily Practice:** `remember(blockKey)` where `blockKey = Triple(blockIndex, taskIndex, tasks.size)` -- recreated when the block changes.

---

## 12.7 User Interactions

### 12.7.1 Touch/Tap Interactions

| Target | Action |
|--------|--------|
| TTS speaker button (card) | Calls `contract.speakTts()`. Starts TTS playback of the card prompt. If already speaking, stops playback. |
| TTS speaker button (result) | Calls `contract.speakTts()`. Speaks the display answer. |
| Input text field | Standard text editing. Updates `localInputText` via `scope.onInputChanged`. |
| Mic trailing icon (text field) | Switches to VOICE mode and launches system speech recognition intent. |
| Word bank FilterChip | Calls `contract.selectWordFromBank(word)`. Adds word to selection if not fully used. |
| "Undo" text button | Calls `contract.removeLastSelectedWord()`. Removes last selected word. |
| Voice mode button (selector) | Sets input mode to VOICE. In Verb Drill and Daily Practice, this also triggers `voiceTriggerToken++` which auto-launches speech recognition. |
| Keyboard mode button | Sets input mode to KEYBOARD. Clears word bank selection. |
| Word bank mode button | Sets input mode to WORD_BANK. Clears any text input. |
| Show answer button (eye) | Calls `contract.showAnswer()`. Reveals the correct answer. Disabled when hint is already shown. |
| Report button (warning) | Opens the report bottom sheet. |
| "Add to bad sentences list" | Calls `contract.flagCurrentCard()`. Closes sheet. |
| "Remove from bad sentences list" | Calls `contract.unflagCurrentCard()`. Closes sheet. |
| "Hide this card from lessons" | Calls `contract.hideCurrentCard()`. Closes sheet. |
| "Export bad sentences to file" | Calls `contract.exportFlaggedCards()`. Shows result in AlertDialog. |
| "Copy text" | Copies card info (ID, source, target) to clipboard. |
| "Check" button | Calls `scope.onSubmit()`. Validates the answer. Resets input text. |
| Prev button (nav) | Calls `scope.onPrev()`. Goes to previous card. Resets input text. |
| Pause button (nav) | Calls `contract.togglePause()`. Toggles session pause. If currently paused with hint shown, advances to next card (Verb Drill adapter behavior). |
| Exit button (nav) | Shows exit confirmation dialog. On confirm: calls `contract.requestExit()`. |
| "End" (exit dialog) | Confirms exit. Calls `contract.requestExit()`. |
| "Cancel" (exit dialog) | Dismisses exit dialog. |
| Next button (nav) | Calls `scope.onNext()`. Advances to next card. Resets input text. If session is complete, calls `onComplete()`. |
| "Done" button (completion) | Calls `scope.onExit()`. Returns to the calling screen. |
| "More" button (Verb Drill completion) | Calls `contract.requestNextBatch()`. Loads 10 more cards. |
| SuggestionChip (verb) | Opens Verb Reference Bottom Sheet showing conjugation table. |
| SuggestionChip (tense) | Opens Tense Info Bottom Sheet showing formula, usage, and examples. |
| Verb Reference TTS button | Speaks the verb infinitive via `viewModel.speakVerbInfinitive(verb)`. |

### 12.7.2 Voice Input Flow

The voice input flow differs between the default input controls and the custom adapters:

**Default input controls (TrainingCardSession):**

1. User taps mic trailing icon or voice mode button.
2. System speech recognition intent is launched with the appropriate language tag (`en-US` or `it-IT`).
3. `ActivityResultContracts.StartActivityForResult()` receives the spoken text.
4. `contract.onVoiceInputResult(spoken)` is called, which defaults to `contract.onInputChanged(spoken)`.
5. Text appears in the input field. User must tap "Check" to submit.

**Verb Drill and Daily Practice (custom inputControls):**

1. Switching to VOICE mode increments `voiceTriggerToken`.
2. `LaunchedEffect` detects the token change and auto-launches speech recognition after a 200ms delay (1200ms if retrying after incorrect answer).
3. When speech result returns, `provider.submitAnswerWithInput(spoken)` is called immediately -- no manual "Check" needed.
4. If correct, input is cleared. A `LaunchedEffect` detects the correct result and auto-advances to the next card after 400-500ms.
5. If incorrect, the spoken text is placed in the input field for manual editing.

### 12.7.3 Keyboard Interactions

- **Typing:** Standard Android text input. `onValueChange` callback updates `localInputText`.
- **Clearing incorrect feedback:** In custom adapters, typing after an incorrect answer clears the inline "Incorrect" feedback via `provider.clearIncorrectFeedback()`.
- **Clearing hint:** In custom adapters, typing after a hint is shown clears the hint, unpauses the session, and resets the attempt counter.
- **Submit:** The "Check" button is the primary submit mechanism. There is no IME action (Enter key) submission -- the user must tap the button.

### 12.7.4 Swipe Gestures

TrainingCardSession does not implement any swipe gestures. Card navigation is exclusively via the prev/next navigation buttons.

### 12.7.5 Answer Normalization

All answer comparisons go through `Normalizer.normalize()`:

1. Trim whitespace, collapse multiple spaces to single.
2. Remove time colons (`\b(\d{1,2}):\d{2}\b` to `$1`).
3. Lowercase the entire string.
4. Strip diacritical marks (NFD decomposition, remove combining characters).
5. Strip all remaining punctuation.

This ensures answers are matched regardless of capitalization, accent usage, trailing punctuation, or minor whitespace differences.

### 12.7.6 Card Presentation Order

Cards are presented in the order provided by the adapter. The composable itself does not shuffle or reorder cards. Ordering is determined by:

- **Verb Drill:** `VerbDrillViewModel` selects and orders cards based on selected tense/group filters and frequency sorting preference.
- **Daily Practice:** `DailySessionComposer` builds the task list with a fixed block order (TRANSLATE, VOCAB, VERBS). Within each block, cards are ordered by the composer's selection algorithm.

### 12.7.7 Session Completion

A session is complete when `contract.isComplete == true` and `lastResult == null` (no pending result to display).

The completion behavior varies by mode:

- **Default:** Shows "Well done!" + progress text + "Done" button.
- **Verb Drill:** Shows stats (correct/incorrect counts). If not all cards are done for today, shows "More" button to load the next batch. Always shows "Exit" outlined button.
- **Daily Practice:** Block completion triggers `onAdvanceBlock()`. If there are more blocks, a `BlockSparkleOverlay` appears briefly, then the next block starts. If all blocks are done, a final `DailyPracticeCompletionScreen` shows "Session Complete!" with a "Back to Home" button.

### 12.7.8 Error States

| Condition | UI Response |
|-----------|-------------|
| `currentCard == null` (not complete) | "No cards" error text in red. Input field disabled. Check button disabled. |
| `currentCard == null && isComplete` | Completion screen shown. |
| TTS error | Warning icon in red on speaker button. |
| Speech recognition failure | No explicit error shown. User can retry or switch to keyboard. |
| Export failure | AlertDialog showing "No bad sentences to export". |

---

## 12.8 UI Consistency — Shared Components [UI-CONSISTENCY-2025]

This section documents the shared UI components to be extracted from existing screen-specific implementations for cross-screen consistency. The goal is to unify input mode bars, report sheets, and voice auto-launch behavior across VerbDrill, VocabDrill, TrainingScreen, and DailyPractice.

---

### 12.8.1 Shared InputModeBar Component [UI-CONSISTENCY-2025]

A shared `SharedInputModeBar` composable must be extracted to `ui/components/SharedInputModeBar.kt`, unifying three current implementations.

**Current implementations to unify:**

| Screen | Component | Class path |
|--------|-----------|------------|
| VerbDrill | `VerbDrillInputModeBar` | `ui/screens/VerbDrillScreen.kt:867-955` |
| DailyPractice | `DailyInputModeBar` | `ui/DailyPracticeComponents.kt:104-173` |
| TrainingScreen | Inline in `AnswerBox` | `ui/screens/TrainingScreen.kt:681-748` |

**Shared component specification:**

```kotlin
@Composable
fun SharedInputModeBar(
    currentMode: InputMode,
    onModeChange: (InputMode) -> Unit,
    hintAvailable: Boolean,          // Whether eye button is active
    wordBankAvailable: Boolean,      // Whether word bank mode is offered
    voiceAvailable: Boolean,         // Whether mic mode is offered
    reportAvailable: Boolean,        // Whether report button is shown
    onShowHint: () -> Unit,          // Eye button callback
    onReport: () -> Unit,            // Report button callback
    hintShown: Boolean = false,      // Disable eye when hint already shown
    modifier: Modifier = Modifier
)
```

**Required buttons (left-to-right order):**

| Position | Button | Icon | Condition | Behavior |
|----------|--------|------|-----------|----------|
| 1 | Mic (VOICE) | `Mic` | `voiceAvailable` | Switches to VOICE mode, triggers `onModeChange(VOICE)` |
| 2 | Keyboard | `Keyboard` | Always | Switches to KEYBOARD mode, triggers `onModeChange(KEYBOARD)` |
| 3 | Word Bank | `MenuBook` | `wordBankAvailable` | Switches to WORD_BANK mode, triggers `onModeChange(WORD_BANK)` |
| 4 | Eye (Show Answer) | `Visibility` | `hintAvailable` | Calls `onShowHint()`. Disabled when `hintShown == true` |
| 5 | Report | `Warning` / `ChangeHistory` | `reportAvailable` | Calls `onReport()` |

**Consistency requirements:**
- All screens must position the input mode bar at the **bottom of the card area, above the Check button**.
- Button sizing: `FilledTonalIconButton` with 40dp default size.
- Active mode indication: the current mode's button uses `FilledTonal` style; inactive buttons use standard icon button style.
- Current mode label text ("Voice" / "Keyboard" / "Word Bank") appears below the button row, right-aligned.

**Regression class paths:**
- `ui/components/SharedInputModeBar.kt` — NEW shared component
- `ui/screens/VerbDrillScreen.kt:867-955` — replace `VerbDrillInputModeBar` with shared
- `ui/DailyPracticeComponents.kt:104-173` — replace `DailyInputModeBar` with shared
- `ui/screens/TrainingScreen.kt:681-748` — replace inline bar with shared

---

### 12.8.2 Shared ReportSheet Component [UI-CONSISTENCY-2025]

#### Behavioral Contract

| Action | System Response | User Outcome |
|--------|----------------|--------------|
| User taps "Add to bad sentences list" | `onFlagToggle()` -> `BadSentenceStore.add(packId, cardId, ...)` -> card.isFlagged = true | Sheet closes; next time sheet opens, option shows "Remove from bad sentences list" with red icon tint |
| User taps "Remove from bad sentences list" | `onFlagToggle()` -> `BadSentenceStore.remove(packId, cardId)` -> card.isFlagged = false | Sheet closes; next time sheet opens, option reverts to "Add to bad sentences list" |
| User taps "Hide this card" | `onHideCard()` -> `hideCurrentCard()` removes card from session (except Daily where it is a no-op) | Sheet closes; card is removed from the current session. In VerbDrill a toast says "Feature not yet available for verb drills" |
| User taps "Export bad sentences" | `onExport()` -> `BadSentenceStore.exportUnified()` -> returns file path or null | Sheet closes; AlertDialog shows file path or "No bad sentences to export" |
| User taps "Copy text" | `onCopy()` -> clipboardManager.setClip(cardId + source + target) | Sheet closes; card text is in system clipboard |

A shared `SharedReportSheet` composable must be extracted to `ui/components/SharedReportSheet.kt`, providing a consistent 4-option report bottom sheet across all screens.

**Reference implementation:** TrainingScreen AnswerBox report sheet (`ui/screens/TrainingScreen.kt`).

**4 standard options (in order):**

| # | Option | Icon | Behavior | Notes |
|---|--------|------|----------|-------|
| 1 | Flag/Unflag bad sentence | `ReportProblem` | Toggle. Red tint when card is flagged. | Text changes: "Add to bad sentences list" / "Remove from bad sentences list" |
| 2 | Hide this card from lessons | `VisibilityOff` | Hides card from future lessons. Closes sheet. | May be a no-op for some modes (verb drill); show informational toast |
| 3 | Export bad sentences | `Download` | Exports flagged cards to text file. Shows path in AlertDialog. | Returns file path or null |
| 4 | Copy text | `ContentCopy` | Copies card info to clipboard. Closes sheet. | Format varies by card type |

**Shared component specification:**

```kotlin
@Composable
fun SharedReportSheet(
    showSheet: Boolean,
    onDismiss: () -> Unit,
    isFlagged: Boolean,
    onFlagToggle: () -> Unit,
    onHideCard: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
    hideEnabled: Boolean = true,      // When false, hide option shows informational toast
    cardSummaryText: String,           // Card info displayed at top of sheet
    modifier: Modifier = Modifier
)
```

**Screens adopting this component:**

| Screen | Current implementation | Class path |
|--------|----------------------|------------|
| VerbDrill | `VerbDrillReportSheet` | `ui/screens/VerbDrillScreen.kt` |
| VocabDrill | `VocabDrillReportSheet` | `ui/screens/VocabDrillScreen.kt` |
| TrainingScreen | Inline in `AnswerBox` | `ui/screens/TrainingScreen.kt` |
| DailyPractice | N/A (no report button currently) | `ui/DailyPracticeScreen.kt` |

**Regression class paths:**
- `ui/components/SharedReportSheet.kt` — NEW shared component
- `ui/components/SharedInputModeBar.kt` — NEW shared component
- `ui/components/TrainingCardSession.kt` — uses shared components in default slots
- `ui/screens/VerbDrillScreen.kt` — adopter of SharedReportSheet
- `ui/screens/TrainingScreen.kt` — reference implementation, adopter of SharedReportSheet
- `ui/screens/VocabDrillScreen.kt` — adopter of SharedReportSheet
- `ui/DailyPracticeScreen.kt` — adopter of SharedInputModeBar

---

#### Behavioral Contract -- VoiceAutoLauncher

| Action | System Response | User Outcome |
|--------|----------------|--------------|
| New card shown + voiceMode enabled | `onAutoStartVoice` callback fires -> `speechLauncher.launch(intent)` | Speech recognition dialog appears after configurable delay (500ms for new card, 1200ms after incorrect feedback) |
| Voice answer correct | `pendingAnswerResult.correct == true` && `currentInputMode == VOICE` -> auto-advance after 400-500ms delay | Next card shown automatically |
| Voice answer incorrect (attempt < 3) | `showIncorrectFeedback = true` -> `voiceTriggerToken++` -> re-launch speech after 1200ms | Speech recognition re-prompts; user sees "Incorrect" with remaining attempts |
| Callback only switches InputMode (BUG) | `setInputMode(VOICE)` called but `speechLauncher.launch()` NOT called | Speech dialog does NOT appear -- user must manually tap mic. This is the VoiceAutoLauncher bug pattern to guard against |

**CRITICAL:** The `onAutoStartVoice` callback MUST call `speechLauncher.launch(intent)` directly. Switching `InputMode` alone is INSUFFICIENT and causes the voice-not-launching bug.

---

#### Behavioral Contract -- HintAnswerCard

| Action | System Response | User Outcome |
|--------|----------------|--------------|
| Eye button clicked at HintLevel=EASY | `provider.showAnswer()` -> `hintAnswer = card.answer`, `isPaused = true` | Pink card with answer text appears below prompt card |
| Eye button clicked at HintLevel=MEDIUM | `provider.showAnswer()` -> `hintAnswer = card.answer`, `isPaused = true` | Pink card with answer text appears below prompt card (same as EASY) |
| Eye button clicked at HintLevel=HARD | `provider.showAnswer()` -> `hintAnswer = card.answer`, `isPaused = true` | Pink card with answer text appears below prompt card (same as EASY) |
| 3 wrong attempts on same card | Auto-sets `hintAnswer = card.answer`, `incorrectAttempts = 3`, `isPaused = true` | Pink card appears automatically; eye button becomes disabled |
| `hintAnswer != null` but HintLevel gate blocks render (BUG) | Pink card composable checks `hintLevel == EASY` before rendering | Answer NOT shown at MEDIUM/HARD -- this is the HintAnswerCard bug pattern to guard against |

**CRITICAL:** HintAnswerCard MUST render whenever `hintAnswer != null`, regardless of `HintLevel` setting. HintLevel controls parenthetical hints in prompt text, NOT the eye/show-answer button or the hint card.

---

#### Behavioral Contract -- SharedInputModeBar

| Action | System Response | User Outcome |
|--------|----------------|--------------|
| User taps Mic button | `onModeChange(VOICE)` -> switches input mode, triggers `voiceTriggerToken++` in adapters | Mode label shows "Voice"; auto-voice launches if VoiceAutoLauncher is active |
| User taps Keyboard button | `onModeChange(KEYBOARD)` -> switches input mode, clears word bank selection | Mode label shows "Keyboard"; text field becomes active |
| User taps Word Bank button | `onModeChange(WORD_BANK)` -> switches input mode, clears text input | Mode label shows "Word Bank"; word bank chips appear |
| User taps Eye button | `onShowHint()` -> `provider.showAnswer()` -> sets `hintAnswer`, pauses session | Eye button becomes disabled; HintAnswerCard appears |
| User taps Report button | `onReport()` -> sets `showReportSheet = true` | SharedReportSheet bottom sheet opens |
| Eye button already shown (hint active) | `hintShown == true` -> Eye button is `enabled = false` | Tapping eye button has no effect; hint card remains visible |

---

### 12.8.3 Cross-Screen Consistency Matrix [UI-CONSISTENCY-2025]

Summary of which component is the reference and which are adopters:

| UI Element | Reference Screen | Adopters | Shared Component |
|------------|-----------------|----------|------------------|
| Eye / Show Answer hint card | VerbDrill (`VerbDrillScreen.kt:392-425`) | TrainingScreen, DailyPractice | Inline (no shared component; styling guide only) |
| Voice auto mode toggle + launch | VocabDrill (`VocabDrillScreen.kt:218-241`, `:409-417`) | VerbDrill | `ui/components/VoiceAutoLauncher.kt` (NEW) |
| Report sheet (4 options) | TrainingScreen (`TrainingScreen.kt`) | VerbDrill, VocabDrill | `ui/components/SharedReportSheet.kt` (NEW) |
| Input mode bar | VerbDrill (`VerbDrillScreen.kt:867-955`) | TrainingScreen, DailyPractice | `ui/components/SharedInputModeBar.kt` (NEW) |


---


# 9. Daily Practice System -- Specification

**Sources:**
- `app/src/main/java/com/alexpo/grammermate/feature/daily/DailySessionComposer.kt`
- `app/src/main/java/com/alexpo/grammermate/feature/daily/DailySessionHelper.kt`
- `app/src/main/java/com/alexpo/grammermate/feature/daily/DailyPracticeSessionProvider.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/DailyPracticeScreen.kt`
- `app/src/main/java/com/alexpo/grammermate/data/Models.kt` (DailyTask, DailySessionState, DailyCursorState, DailyBlockType)
- `app/src/main/java/com/alexpo/grammermate/data/VerbDrillCard.kt` (VerbDrillComboProgress)
- `app/src/main/java/com/alexpo/grammermate/data/VocabWord.kt` (VocabWord, WordMasteryState, VocabDrillDirection)
- `app/src/main/java/com/alexpo/grammermate/data/CardSessionContract.kt` (SessionCard, CardSessionContract)
- `app/src/main/java/com/alexpo/grammermate/data/SpacedRepetitionConfig.kt` (INTERVAL_LADDER_DAYS)
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` (startDailyPractice, cancelDailySession, rateVocabCard, etc.)

---

## 9.1 Overview

Daily Practice is GrammarMate's unified daily training session, combining three distinct exercise blocks into a single flow:

1. **Block 1 -- Sentence Translations** (`DailyBlockType.TRANSLATE`, 10 cards): Translate sentences from Russian to the target language using voice, keyboard, or word bank input. Cursor-driven sequential card selection.
2. **Block 2 -- Vocabulary Flashcards** (`DailyBlockType.VOCAB`, 10 cards, Anki-style): Review vocabulary words with a show-and-rate mechanism integrating spaced repetition scheduling. Pure SRS-based selection from full pack word list.
3. **Block 3 -- Verb Conjugations** (`DailyBlockType.VERBS`, 10 cards): Conjugate verbs in context, with verb metadata (infinitive, tense, group) displayed as hint chips. Weak-first ordering with previously-shown exclusion.

Total session size: up to 30 cards across 3 blocks. Blocks are served sequentially; the session ends when all blocks are complete or the user exits.

**Constants** (all in `DailySessionComposer.Companion`):
```kotlin
const val CARDS_PER_BLOCK = 10
const val SENTENCE_COUNT = 10    // alias for CARDS_PER_BLOCK
const val VOCAB_COUNT = 10       // alias for CARDS_PER_BLOCK
const val VERB_COUNT = 10        // alias for CARDS_PER_BLOCK
```

### Historical Context

Daily Practice replaces two former modes:
- **Elite mode** (high-speed translation drill) -- removed; `AppScreen.ELITE` enum value kept for backward compatibility (redirects to HOME if restored from saved state).
- **Vocab Sprint** (rapid vocabulary quiz) -- removed; `AppScreen.VOCAB` enum value kept for backward compatibility (same redirect behavior).

These enum values MUST NOT be deleted because existing saved state may reference them. Removing them would crash users who have `"ELITE"` or `"VOCAB"` as their persisted `currentScreen`.

---

## 9.2 Session Architecture

### 9.2.1 Component Overview

The Daily Practice system consists of four primary components:

| Component | File | Responsibility |
|-----------|------|----------------|
| `DailySessionComposer` | `feature/daily/DailySessionComposer.kt` | Pure builder: constructs `List<DailyTask>` for all three blocks from lesson, vocab, and verb drill data using cursor-based card selection. |
| `DailySessionHelper` | `feature/daily/DailySessionHelper.kt` | State manager: tracks `taskIndex` and `blockIndex` within the task list, provides block-level and session-level progress, handles block transitions and session lifecycle. |
| `DailyPracticeSessionProvider` | `feature/daily/DailyPracticeSessionProvider.kt` | `CardSessionContract` adapter for Blocks 1 and 3. Manages per-card state (current index, input mode, retry/hint flow, word bank). Block 2 does NOT use this provider. |
| `DailyPracticeScreen` | `ui/screens/DailyPracticeScreen.kt` (currently in `ui/DailyPracticeScreen.kt`) | Composable UI: renders the current block's content, handles block transitions with sparkle overlays, displays session completion screen. |

### 9.2.2 Data Flow

```
TrainingViewModel.startDailyPractice()
  -> DailySessionComposer.buildSession(lessonLevel, packId, languageId, lessonId, tenses, cursor)
     -> buildSentenceBlock() -> List<DailyTask.TranslateSentence>
     -> buildVocabBlock()    -> List<DailyTask.VocabFlashcard>
     -> buildVerbBlock()     -> List<DailyTask.ConjugateVerb>
  -> DailySessionHelper.startDailySession(tasks, lessonLevel)
     -> sets DailySessionState(active=true, tasks=..., taskIndex=0, blockIndex=0)

UI reads: state.dailySession -> DailyPracticeScreen
  -> DailySessionHelper.getCurrentTask() -> current DailyTask
  -> DailySessionHelper.getBlockProgress() -> BlockProgress(position, size, total)
  -> CardSessionBlock or VocabFlashcardBlock rendered based on blockType

On block completion:
  -> DailySessionHelper.advanceToNextBlock()
  -> BlockSparkleOverlay shown for 800ms
  -> Next block's tasks become current

On session completion:
  -> DailySessionHelper.endSession()
  -> DailyPracticeCompletionScreen shown
```

### 9.2.3 DailyTask Sealed Class

All session content is modeled as `DailyTask` subtypes:

```kotlin
sealed class DailyTask {
    abstract val id: String
    abstract val blockType: DailyBlockType

    data class TranslateSentence(...) : DailyTask()  // blockType = TRANSLATE
    data class VocabFlashcard(...)    : DailyTask()  // blockType = VOCAB
    data class ConjugateVerb(...)     : DailyTask()  // blockType = VERBS
}
```

### 9.2.4 DailyBlockType Enum

```kotlin
enum class DailyBlockType { TRANSLATE, VOCAB, VERBS }
```

Tasks are ordered in the task list by block type: all TRANSLATE tasks first, then all VOCAB tasks, then all VERBS tasks. Block transitions are detected by comparing `blockType` of consecutive tasks.

### 9.2.5 DailySessionState

```kotlin
data class DailySessionState(
    val active: Boolean = false,
    val tasks: List<DailyTask> = emptyList(),
    val taskIndex: Int = 0,
    val blockIndex: Int = 0,
    val level: Int = 0,
    val finishedToken: Boolean = false
)
```

| Field | Meaning |
|-------|---------|
| `active` | Session is in progress. |
| `tasks` | Flat list of all tasks across all 3 blocks, in TRANSLATE/VOCAB/VERBS order. |
| `taskIndex` | Index into `tasks` of the current card being practiced. |
| `blockIndex` | Current block number (0, 1, or 2). Incremented when `blockType` changes between consecutive tasks. |
| `level` | Lesson level at session creation time. |
| `finishedToken` | Set to `true` when `endSession()` is called. Triggers completion UI. |

### 9.2.6 DailyCursorState

Cursor-based state for tracking progress between daily sessions (persisted in `progress.yaml`):

```kotlin
data class DailyCursorState(
    val sentenceOffset: Int = 0,        // cards shown in current lesson (0, 10, 20, ...)
    val currentLessonIndex: Int = 0,    // which lesson in the pack (0-based)
    val lastSessionHash: Int = 0,       // hash of last completed session
    val firstSessionDate: String = "",  // ISO date (yyyy-MM-dd) of the first session of the day
    val firstSessionSentenceCardIds: List<String> = emptyList(),  // card IDs from first session's block 1
    val firstSessionVerbCardIds: List<String> = emptyList()       // card IDs from first session's block 3
)
```

| Field | Purpose |
|-------|---------|
| `sentenceOffset` | How many sentence cards have been completed in the current lesson. Advances by the count of VOICE/KEYBOARD-practiced sentence cards per completed session. When the offset exceeds the lesson's card count, `currentLessonIndex` advances and offset resets to 0. |
| `currentLessonIndex` | Index into the lesson list for the pack. Advances when `sentenceOffset` exceeds the current lesson's card count (handled in `TrainingViewModel.advanceCursor()`). |
| `lastSessionHash` | Defined in model but currently unused (dead field). Reserved for future "repeat" cache optimization. |
| `firstSessionDate` | ISO date string (`yyyy-MM-dd`). Used to determine if the "first session of the day" card cache is still valid. Compared against today's date; if different, a new first session is started. |
| `firstSessionSentenceCardIds` | Stores the card IDs from the first session's Block 1, enabling the "Repeat" feature to replay the exact same cards. |
| `firstSessionVerbCardIds` | Stores the card IDs from the first session's Block 3, enabling the "Repeat" feature for verb cards. |

Persistence: `DailyCursorState` is nested inside `TrainingProgress.dailyCursor` in `ProgressStore` and written to `grammarmate/progress.yaml`.

---

## 9.3 Block 1: Sentence Translations (10 cards)

### 9.3.1 Card Selection Algorithm

Block 1 uses **cursor-driven sequential selection** -- no shuffling. The algorithm is in `DailySessionComposer.buildSentenceBlock()`:

1. Load all lessons for the target language via `LessonStore.getLessons(languageId)`.
2. Select the lesson at `cursor.currentLessonIndex`. If the index is out of range, fall back to matching by `lessonId`.
3. Take cards starting at `cursor.sentenceOffset`, in order, up to `SENTENCE_COUNT` (10).
4. If the lesson is exhausted (offset >= cards.size), return an empty list, signaling the caller to advance the lesson index.

Cards per block: `CARDS_PER_BLOCK = 10` (constant in `DailySessionComposer`).

### 9.3.2 Input Mode Rotation

Input modes are assigned deterministically based on card index within the block:

| Index modulo 3 | Input Mode |
|----------------|------------|
| 0 | `InputMode.VOICE` |
| 1 | `InputMode.KEYBOARD` |
| 2 | `InputMode.WORD_BANK` |

This rotation ensures a balanced mix of input types across the 10 cards.

### 9.3.3 Task Construction

Each selected card is wrapped in `DailyTask.TranslateSentence`:

```kotlin
DailyTask.TranslateSentence(
    id = "sent_${card.id}",
    card = card,          // SentenceCard with promptRu and acceptedAnswers
    inputMode = mode      // VOICE, KEYBOARD, or WORD_BANK
)
```

### 9.3.4 Scoring and Progression

Answer validation is handled by `DailyPracticeSessionProvider`, which implements `CardSessionContract`.

**Constructor:**
```kotlin
class DailyPracticeSessionProvider(
    private val tasks: List<DailyTask>,
    blockType: DailyBlockType,
    private val onBlockComplete: () -> Unit,
    override val languageId: String = "en",
    private val onAnswerChecked: (input: String, correct: Boolean) -> Unit = { _, _ -> },
    private val onSpeakTts: (String) -> Unit = {},
    private val onStopTts: () -> Unit = {},
    private val ttsStateProvider: () -> TtsState = { TtsState.IDLE },
    private val onExit: () -> Unit = {},
    private val onCardAdvanced: (DailyTask) -> Unit = {},
    private val onFlagCard: ((SessionCard, DailyBlockType) -> Unit)? = null,
    private val onUnflagCard: ((SessionCard, DailyBlockType) -> Unit)? = null,
    private val isCardFlagged: ((SessionCard) -> Boolean)? = null,
    private val onExportFlagged: (() -> String?)? = null
) : CardSessionContract
```

**Key internal state (Compose mutableStateOf):**
```kotlin
private var currentIndex: Int             // 0-based index into blockCards
private var _pendingCard: SessionCard?    // set on correct answer or hint
private var pendingAnswerResult: AnswerResult?  // exposed to UI for auto-advance
private var _isPaused: Boolean            // true when hint shown
private var _inputMode: InputMode         // current input mode (starts VOICE)
private var _selectedWords: List<String>  // word bank selection
private var incorrectAttempts: Int        // consecutive wrong attempts (0-3)
private var hintAnswer: String?           // non-null when answer shown as hint
private var voiceTriggerToken: Int        // incremented to trigger ASR
private var showIncorrectFeedback: Boolean // inline "Incorrect" display
private var remainingAttempts: Int        // 3 - incorrectAttempts
```

**Capabilities (all true):**
```kotlin
override val supportsTts: Boolean get() = true
override val supportsVoiceInput: Boolean get() = true
override val supportsWordBank: Boolean get() = true
override val supportsFlagging: Boolean get() = true
override val supportsNavigation: Boolean get() = true
override val supportsPause: Boolean get() = true
```

**sessionActive property:**
```kotlin
override val sessionActive: Boolean
    get() {
        if (_isPaused) return false
        if (hintAnswer != null) return false
        return currentIndex < blockCards.size
    }
```

Answer validation flow:

1. User submits an answer (via voice, keyboard, or word bank).
2. The input is normalized using `Normalizer.normalize()` and compared against each accepted answer (also normalized).
3. **Correct**: `pendingCard` and `pendingResult` are set. In VOICE mode, auto-advance after 400ms delay.
4. **Incorrect (attempt < 3)**: `showIncorrectFeedback` is set to `true`, `incorrectAttempts` increments, `remainingAttempts` decrements. In VOICE mode, voice recognition is auto-re-triggered after 1200ms.
5. **Incorrect (attempt = 3)**: Answer is auto-shown as a hint via `hintAnswer`. Input controls remain visible. `sessionActive` becomes `false` until the user types again (which clears the hint and resets attempts) or calls `nextCard()`.
6. **Manual "Show Answer"**: Sets `hintAnswer`, pauses session, resets attempt counters. Same behavior as 3 wrong attempts.

### 9.3.5 Mastery Counting

Only VOICE and KEYBOARD answers count as "practiced" for the purpose of cursor advancement. The `onCardAdvanced` callback in `DailyPracticeSessionProvider.nextCard()` is invoked only when the current input mode is NOT `WORD_BANK`:

```kotlin
if (currentIndex < blockCards.size && _inputMode != InputMode.WORD_BANK) {
    onCardAdvanced(blockCards[currentIndex])
}
```

WORD_BANK answers and simple forward navigation (no answer submitted) do NOT count.

The `onCardAdvanced` callback triggers two actions in the ViewModel (via `CardSessionBlock`'s `onCardAdvanced` lambda):
1. For `ConjugateVerb` tasks: calls `onPersistVerbProgress(task.card)`.
2. For all tasks: calls `onCardPracticed(task.blockType)` which increments `dailyPracticeAnsweredCounts[blockType]` and, for TRANSLATE block, records card mastery via `MasteryStore.recordCardShow()` (which grows flowers).

```kotlin
// TrainingViewModel.recordDailyCardPracticed()
fun recordDailyCardPracticed(blockType: DailyBlockType) {
    val count = dailyPracticeAnsweredCounts[blockType] ?: 0
    dailyPracticeAnsweredCounts[blockType] = count + 1
    // Record mastery for TRANSLATE block sentence cards (grows flowers).
    // VERBS and VOCAB blocks do NOT count toward flower growth.
    if (blockType == DailyBlockType.TRANSLATE) {
        val task = dailySessionHelper.getCurrentTask() as? DailyTask.TranslateSentence
        if (task != null) {
            masteryStore.recordCardShow(lessonId, languageId, card.id)
        }
    }
}
```

### 9.3.6 Repeat Session for Block 1

`DailySessionComposer.buildSentenceBlockFromIds()` reconstructs the block from stored card IDs (from `DailyCursorState.firstSessionSentenceCardIds`). Cards are looked up across all lessons via `lessons.flatMap { it.cards }.associateBy { it.id }`, preserving the original ID order. Input mode rotation follows the same index-based pattern.

```kotlin
private fun buildSentenceBlockFromIds(
    lessonLevel: Int, packId: String, languageId: String, lessonId: String,
    cardIds: List<String>
): List<DailyTask.TranslateSentence>
```

---

## 9.4 Block 2: Vocabulary Flashcards (10 cards, Anki-style)

### 9.4.1 Flashcard Model

Each vocab flashcard is a `DailyTask.VocabFlashcard`:

```kotlin
DailyTask.VocabFlashcard(
    id = "voc_${word.id}",
    word = word,                    // VocabWord (id, word, pos, rank, meaningRu, collocations, forms)
    direction = direction           // VocabDrillDirection (IT_TO_RU or RU_TO_IT)
)
```

The `VocabWord.id` is generated in `DailySessionComposer.loadVocabWords()` as `"{pos}_{rank}_{word}"` where `pos` is derived from the filename (e.g., `"nouns"`, `"verbs"`, `"adjectives"`). Words are sorted by `rank` (frequency) after loading.

### 9.4.2 Card Selection Algorithm

Block 2 uses **pure SRS (Spaced Repetition System) selection** from the full pack word list. It is NOT tied to lesson level or cursor position. The algorithm is in `DailySessionComposer.buildVocabBlock()`:

1. Load all vocab words from the pack's vocab drill files via `LessonStore.getVocabDrillFiles()` and `ItalianDrillVocabParser`. **Numbers are excluded** (`pos != "numbers"`) -- they should only appear in standalone Vocab Drill when the user explicitly selects the Numbers filter.
2. Load all mastery states from `WordMasteryStore.loadAll()`.
3. Categorize every word into one of three buckets:
   - **Due words**: `mastery.nextReviewDateMs <= now` or `mastery.lastReviewDateMs == 0`.
   - **New words**: No mastery record exists (never reviewed).
   - **Scheduled words**: Not due, already reviewed.
4. Sort due words by **most overdue first** (descending `now - nextReviewDateMs`).
5. Select up to `VOCAB_COUNT` (10) words, priority order:
   - First: due words, most overdue first.
   - Second: new words, sorted by rank (frequency).
   - Third (fallback): least recently reviewed scheduled words.

### 9.4.3 Direction Assignment

Flashcard direction alternates by index:

| Index modulo 2 | Direction |
|----------------|-----------|
| 0 | `VocabDrillDirection.IT_TO_RU` (prompt in Italian, answer in Russian) |
| 1 | `VocabDrillDirection.RU_TO_IT` (prompt in Russian, answer in Italian) |

### 9.4.4 Show/Hide Answer Flow

Unlike Blocks 1 and 3, Block 2 uses a **show-and-rate** model rather than submit-and-check:

1. The flashcard displays the **prompt text** (large, 28sp) and the **answer text** (smaller, 18sp) simultaneously. Both are always visible -- the card is not "flipped."
2. A TTS button allows listening to the prompt word.
3. A microphone button allows voice recognition. The recognized speech is normalized and compared to the answer text. If correct, the card is auto-rated as "Good" (rating 2) and the session auto-advances.
4. If voice recognition returns an incorrect result, the user stays on the card and can retry or tap a rating button.

### 9.4.5 Rating Buttons

Four Anki-style rating buttons are displayed at the bottom:

| Button | Rating | Background | Text Color |
|--------|--------|------------|------------|
| Again | 0 | Light red (#FFEBEE) | Red (#E53935) |
| Hard | 1 | Light orange (#FFF3E0) | Orange (#FF9800) |
| Good | 2 | Light green (#E8F5E9) | Green (#4CAF50) |
| Easy | 3 | Light blue (#E3F2FD) | Blue (#2196F3) |

Tapping any rating button:
1. Calls `onRate(rating)` which updates `WordMasteryStore` (interval step adjustment based on rating).
2. Calls `onAdvance()` which moves to the next card.
3. If no more cards remain, calls `onComplete()` to advance to the next block or finish the session.

### 9.4.6 Spaced Repetition Integration

Word mastery is persisted in `WordMasteryStore` (pack-scoped at `grammarmate/drills/{packId}/word_mastery.yaml`):

```kotlin
data class WordMasteryState(
    val wordId: String,
    val intervalStepIndex: Int = 0,         // 0-9, index into INTERVAL_LADDER_DAYS
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val lastReviewDateMs: Long = 0L,
    val nextReviewDateMs: Long = 0L,        // computed: lastReviewDate + ladder[step] * DAY_MS
    val isLearned: Boolean = false           // true when intervalStepIndex >= LEARNED_THRESHOLD (3)
)
```

The interval ladder is `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` days (from `SpacedRepetitionConfig.INTERVAL_LADDER_DAYS`, size = 10, max index = 9).

Rating affects the interval step (computed in `TrainingViewModel.rateVocabCard()`):

```kotlin
val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1  // 9
val newStepIndex = when (rating) {
    0 -> 0                                                    // Again: reset to 0
    1 -> current.intervalStepIndex                            // Hard: stay the same
    2 -> (current.intervalStepIndex + 1).coerceIn(0, maxStep) // Good: +1
    else -> (current.intervalStepIndex + 2).coerceIn(0, maxStep) // Easy: +2
}
```

The `LEARNED_THRESHOLD` is `3` (local constant in `rateVocabCard()`). A word is marked `isLearned = true` when `newStepIndex >= 3`.

The `nextReviewDateMs` is recalculated via `WordMasteryState.computeNextReview(now, newStepIndex)`:
```kotlin
fun computeNextReview(lastReviewMs: Long, stepIndex: Int): Long {
    val ladder = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS
    val days = if (stepIndex in ladder.indices) ladder[stepIndex] else ladder.last()
    return lastReviewMs + days * DAY_MS  // DAY_MS = 86_400_000L
}
```

Side effect counters:
```kotlin
correctCount + (if (rating != 0) 1 else 0)
incorrectCount + (if (rating == 0) 1 else 0)
```

### 9.4.7 Vocab Block Independence

Block 2 vocab selection is completely independent of Blocks 1 and 3:
- It does not use `DailyCursorState`.
- It does not use lesson level for rank filtering.
- It follows its own SRS scheduling across the full pack word list.
- In "Repeat" sessions (`buildRepeatSession`), Block 2 still generates fresh SRS-based cards rather than replaying the first session's vocab cards.

---

## 9.5 Block 3: Verb Conjugations (10 cards)

### 9.5.1 Verb Card Selection

Block 3 uses **weak-first ordering**, excluding previously shown cards. The algorithm is in `DailySessionComposer.buildVerbBlock()`:

1. Load all verb drill cards from the pack's verb drill files via `LessonStore.getVerbDrillFiles()` and `VerbDrillCsvParser`.
2. Filter cards by **active tenses**:
   - If `cumulativeTenses` is provided (from manifest lessons 1..lessonLevel), use those.
   - Otherwise, fall back to `TENSE_LADDER[lessonLevel]`.
   - If no tenses are active, return an empty block.
3. Load progress from `VerbDrillStore.loadProgress()`.
4. Collect IDs of all previously shown cards across all combos (`everShownCardIds`).
5. **Exclude** previously shown cards to produce the `unshown` set.
6. Score unshown cards by **weakness**:

```kotlin
// For each unshown card:
val shownInCombo = progressMap.values
    .filter { it.tense == card.tense }
    .sumOf { it.everShownCardIds.size }
val comboTotal = filtered.count { it.tense == card.tense }
val weakness = if (comboTotal == 0) 0f else 1f - (shownInCombo.toFloat() / comboTotal)
```

   Weakness = 1.0 means no cards in that tense have been shown (weakest). Weakness = 0.0 means all cards shown (strongest).

7. Sort by weakness descending, stable by original index (preserves CSV order for ties):

```kotlin
scored.sortedWith(
    compareByDescending<Triple<VerbDrillCard, Float, Int>> { it.second }  // weakness descending
        .thenBy { it.third }                                                // original index ascending
)
```

8. Take the top `VERB_COUNT` (10) cards.

### 9.5.2 Tense Ladder

The tense ladder maps lesson levels (1-12) to cumulative sets of active tenses. This is defined as `DailySessionComposer.TENSE_LADDER`:

```kotlin
val TENSE_LADDER: Map<Int, List<String>> = mapOf(
    1  to listOf("Presente"),
    2  to listOf("Presente", "Imperfetto"),
    3  to listOf("Presente", "Imperfetto", "Passato Prossimo"),
    4  to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice"),
    5  to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice", "Condizionale Presente"),
    6  to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice", "Condizionale Presente", "Passato Remoto"),
    7  to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice", "Condizionale Presente", "Passato Remoto", "Congiuntivo Presente"),
    8  to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice", "Condizionale Presente", "Passato Remoto", "Congiuntivo Presente", "Trapassato Prossimo"),
    9  to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice", "Condizionale Presente", "Passato Remoto", "Congiuntivo Presente", "Trapassato Prossimo", "Futuro Anteriore"),
    10 to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice", "Condizionale Presente", "Passato Remoto", "Congiuntivo Presente", "Trapassato Prossimo", "Futuro Anteriore", "Congiuntivo Imperfetto"),
    11 to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice", "Condizionale Presente", "Passato Remoto", "Congiuntivo Presente", "Trapassato Prossimo", "Futuro Anteriore", "Congiuntivo Imperfetto", "Condizionale Passato"),
    12 to listOf("Presente", "Imperfetto", "Passato Prossimo", "Futuro Semplice", "Condizionale Presente", "Passato Remoto", "Congiuntivo Presente", "Trapassato Prossimo", "Futuro Anteriore", "Congiuntivo Imperfetto", "Condizionale Passato", "Congiuntivo Passato")
)
```

**Fallback behavior:** If `cumulativeTenses` is provided (from `LessonStore.getCumulativeTenses()`), it takes precedence over `TENSE_LADDER`. If neither provides tenses, the verb block returns empty.

Summarized:

| Level | Tenses |
|-------|--------|
| 1 | Presente |
| 2 | + Imperfetto |
| 3 | + Passato Prossimo |
| 4 | + Futuro Semplice |
| 5 | + Condizionale Presente |
| 6 | + Passato Remoto |
| 7 | + Congiuntivo Presente |
| 8 | + Trapassato Prossimo |
| 9 | + Futuro Anteriore |
| 10 | + Congiuntivo Imperfetto |
| 11 | + Condizionale Passato |
| 12 | + Congiuntivo Passato |

Each level adds one tense to the previous level's set. This ensures progressive difficulty as the user advances through lessons.

### 9.5.3 Verb Block Card Selection and Ordering Requirements

In addition to the weak-first ordering described in section 9.5.1, the following sub-ordering and grouping requirements apply within each weakness tier:

**Frequency sub-sorting:** Within each weakness tier (same weakness score), cards are sorted by `rank` ascending. Lower rank values indicate more common verbs, so the most frequent verbs are presented first. This ensures that within a given weakness level, high-frequency verb forms are practiced before rare ones.

**Verb collocation grouping:** Cards are grouped by verb: all collocations (different person/number forms) of the same `verb + tense` pair are presented together before moving to the next verb. This creates a natural conjugation practice flow where the user practices "essere + Presente" across multiple persons before switching to "avere + Presente".

**Tense matching:** Selected cards use tenses matching the current daily practice lesson level, as determined by `TENSE_LADDER[lessonLevel]` or `cumulativeTenses` from the manifest. No cards with tenses beyond the user's current level are included.

**Behavioral contract for verb ordering:**

| Condition | Expected Behavior |
|-----------|-------------------|
| Multiple cards share the same weakness score | Sorted by `rank` ascending (most common verbs first) |
| Multiple cards share the same `verb + tense` pair | Presented consecutively (grouped together) |
| All cards in a weakness tier have the same verb | Sub-sort by original CSV index (stable) |
| Tense is beyond the user's lesson level | Card is excluded from selection entirely |
| `sortByFrequency` is disabled | Frequency sub-sort still applies within weakness tiers (weak-first always uses frequency as secondary sort) |

**Note:** These ordering requirements describe the desired target behavior. The current implementation in `DailySessionComposer.buildVerbBlock()` uses weak-first ordering with original-index stability. The frequency sub-sort and verb grouping additions are planned enhancements.

### 9.5.4 Task Construction

Each selected card is wrapped in `DailyTask.ConjugateVerb`:

```kotlin
DailyTask.ConjugateVerb(
    id = "verb_${card.id}",
    card = card,          // VerbDrillCard (promptRu, answer, verb, tense, group, rank)
    inputMode = mode      // KEYBOARD or WORD_BANK (alternating)
)
```

Input mode alternates: even indices get `KEYBOARD`, odd indices get `WORD_BANK`. Note: `VOICE` is NOT used for verb conjugation cards.

The `VerbDrillCard` data class:
```kotlin
data class VerbDrillCard(
    override val promptRu: String,
    val answer: String,
    val verb: String? = null,
    val tense: String? = null,
    val group: String? = null,
    val rank: Int? = null
) : SessionCard {
    override val acceptedAnswers: List<String> get() = listOf(answer)
}
```

Progress is keyed by `comboKey = "${group ?: ""}|${tense ?: ""}"` in `VerbDrillStore`.

### 9.5.5 Verb Info Display (Hint Chips)

When the current task is a `ConjugateVerb`, the card content renders three `SuggestionChip` components:

| Chip | Source Field | Display | Fallback |
|------|-------------|---------|----------|
| Verb infinitive | `VerbDrillCard.verb` | `"{verb} #{rank}"` (or just verb if rank is null) | Hidden if blank |
| Tense | `VerbDrillCard.tense` | Abbreviated (e.g., "Pres.", "Imperf.", "P. Pross.") | Hidden if blank |
| Group | `VerbDrillCard.group` | As-is | Hidden if blank |

**Verb, tense, and group chips are ALWAYS visible regardless of HintLevel.** HintLevel controls parenthetical hints in prompt text (e.g., stripping `(dire)` and `(verità)`), NOT the info chips. These chips are reference information about the verb form, tense, and conjugation group.

Tense abbreviation is handled by `abbreviateTense()` which maps full Italian tense names to short forms. Full mapping:

| Full Name | Abbreviation |
|-----------|-------------|
| Presente | Pres. |
| Imperfetto | Imperf. |
| Passato Prossimo | P. Pross. |
| Passato Remoto | P. Rem. |
| Trapassato Prossimo | Trap. P. |
| Futuro Semplice | Fut. Sempl. |
| Futuro Anteriore | Fut. Ant. |
| Condizionale Presente | Cond. Pres. |
| Condizionale Passato | Cond. Pass. |
| Congiuntivo Presente | Cong. Pres. |
| Congiuntivo Imperfetto | Cong. Imp. |
| Congiuntivo Passato | Cong. Pass. |

Unrecognized tenses are truncated to 8 characters.

**Chip interactivity:**
- **Verb chip** (`SuggestionChip`): Tap opens `VerbReferenceBottomSheet` showing conjugation table for matching verb+tense cards from the current block. Sheet includes a TTS button to speak the verb infinitive. Conjugation data comes from `DailyPracticeSessionProvider.getConjugationCards(verb, tense)`.
- **Tense chip** (`SuggestionChip`): Tap opens `TenseInfoBottomSheet` showing tense name, abbreviation, formula, usage explanation (in Russian), and examples. Tense info is loaded lazily from `grammarmate/tenses/{languageId}_tenses.yaml` via `loadTenseInfoFromAssets()`.
- **Group chip**: Display only, non-interactive (no bottom sheet).

The sheets use the parameter-based overloads of `VerbReferenceBottomSheet` and `TenseInfoBottomSheet` (from `ui/components/VerbDrillSheets.kt`), which accept data parameters instead of `VerbDrillViewModel`. Both sheets dismiss on back press or tap outside.

### 9.5.6 Progress Tracking

Verb drill progress is persisted per-combination in `VerbDrillStore` (pack-scoped at `grammarmate/drills/{packId}/verb_drill_progress.yaml`):

```kotlin
data class VerbDrillComboProgress(
    val group: String,
    val tense: String,
    val totalCards: Int,
    val everShownCardIds: Set<String> = emptySet(),
    val todayShownCardIds: Set<String> = emptySet(),
    val lastDate: String = ""
)
```

When a verb card is advanced (via `onCardAdvanced`), the card's ID is added to both `everShownCardIds` and `todayShownCardIds`. The `todayShownCardIds` set is cleared on a new day (when `lastDate != today`).

The exact persistence logic in `TrainingViewModel.persistDailyVerbProgress()`:
```kotlin
fun persistDailyVerbProgress(card: VerbDrillCard) {
    val packId = _uiState.value.activePackId ?: return
    val store = VerbDrillStore(getApplication(), packId = packId)
    val comboKey = "${card.group ?: ""}|${card.tense ?: ""}"
    val existing = store.loadProgress()[comboKey]
    val everShown = (existing?.everShownCardIds ?: emptySet()) + card.id
    val todayShown = (existing?.todayShownCardIds ?: emptySet()) + card.id
    val updated = VerbDrillComboProgress(
        group = card.group ?: "",
        tense = card.tense ?: "",
        totalCards = existing?.totalCards ?: 0,
        everShownCardIds = everShown,
        todayShownCardIds = todayShown,
        lastDate = java.time.LocalDate.now().toString()
    )
    store.upsertComboProgress(comboKey, updated)
}
```

Only VOICE and KEYBOARD modes trigger `onCardAdvanced` (WORD_BANK does not), matching the mastery counting rule from Block 1.

### 9.5.7 Repeat Session for Block 3

`DailySessionComposer.buildVerbBlockFromIds()` reconstructs the block from stored card IDs (from `DailyCursorState.firstSessionVerbCardIds`). Cards are looked up from verb drill files via `loadVerbDrillCards().associateBy { it.id }`, preserving original order. Input mode rotation follows the same KEYBOARD/WORD_BANK alternation.

```kotlin
private fun buildVerbBlockFromIds(
    packId: String, languageId: String,
    cardIds: List<String>
): List<DailyTask.ConjugateVerb>
```

---

## 9.6 Session Flow

### 9.6.1 Start Sequence

1. User taps the Daily Practice tile on HomeScreen.
2. `TrainingViewModel.startDailyPractice(lessonLevel)` is called.
3. The ViewModel saves `dailyCursorAtSessionStart` for rollback on cancel.
4. `dailyPracticeAnsweredCounts` is reset to an empty map.
5. The ViewModel reads `activePackId` and `selectedLanguageId` from current UI state.
6. **IMPORTANT**: The ViewModel uses `resolveProgressLessonInfo()` (NOT `selectedLessonId`) to determine the current lesson. `selectedLessonId` can change when the user browses locked lessons, but daily practice always follows the main learning path. `resolveProgressLessonInfo()` returns `(lessonId, effectiveLevel)`.
7. Reads current `dailyCursor` from state.
8. Determines `isFirstSessionToday` by comparing `cursor.firstSessionDate` to today's ISO date.
9. For the first session of the day, tries the pre-built session cache (`prebuiltDailySession`, built at init time for faster startup). If valid, uses it and clears the cache.
10. Falls back to synchronous build via `DailySessionComposer.buildSession(effectiveLevel, packId, langId, lessonId, cumulativeTenses, cursor)`.
11. `DailySessionComposer` creates all three blocks:
    - `buildSentenceBlock()` -- cursor-driven sequential selection from lesson cards.
    - `buildVocabBlock()` -- pure SRS selection from pack vocab drill files.
    - `buildVerbBlock()` -- weak-first selection from pack verb drill files, filtered by cumulative tenses.
12. If all blocks return empty, session start fails (returns `false`).
13. `DailySessionHelper.startDailySession(tasks, effectiveLevel)` initializes the `DailySessionState` with `active=true, tasks=..., taskIndex=0, blockIndex=0`.
14. If first session today, stores first-session card IDs via `storeFirstSessionCardIds(sentenceIds, verbIds)`. This updates `DailyCursorState.firstSessionDate`, `firstSessionSentenceCardIds`, and `firstSessionVerbCardIds`.
15. Navigation transitions to `AppScreen.DAILY_PRACTICE`.
16. `DailyPracticeScreen` renders the first task of Block 1 (TRANSLATE).

**Key method signatures:**
```kotlin
// TrainingViewModel
fun startDailyPractice(lessonLevel: Int): Boolean
fun hasResumableDailySession(): Boolean
private fun storeFirstSessionCardIds(sentenceIds: List<String>, verbIds: List<String>)
private fun advanceCursor(sentenceCount: Int)

// DailySessionComposer
fun buildSession(
    lessonLevel: Int, packId: String, languageId: String, lessonId: String,
    cumulativeTenses: List<String> = emptyList(),
    cursor: DailyCursorState = DailyCursorState()
): List<DailyTask>

// DailySessionHelper
fun startDailySession(tasks: List<DailyTask>, lessonLevel: Int)
```

### 9.6.2 Block Transition Sequence

When a block completes (all cards in the block finished):

1. `DailyPracticeSessionProvider.nextCard()` increments `currentIndex`.
2. Before incrementing, calls `onCardAdvanced(blockCards[currentIndex])` if current input mode is NOT `WORD_BANK`. This fires `onPersistVerbProgress` for verb cards and `onCardPracticed` for cursor tracking.
3. When `currentIndex >= blockCards.size`, calls `onBlockComplete()`.
4. In `CardSessionBlock`, `blockComplete = true`.
5. `onAdvanceBlock()` is called, which invokes `DailySessionHelper.advanceToNextBlock()`.
6. `advanceToNextBlock()` scans forward in the task list, skipping all tasks of the current `blockType`, and sets `taskIndex` to the first task of the next block type.
7. `blockIndex` is incremented.
8. If no more blocks exist (scanned past end of task list), `endSession()` is called and `false` is returned.
9. A `BlockSparkleOverlay` is displayed for 800ms, showing "Next: {BlockLabel}" (or "Daily practice complete!" for the last block).
10. After the sparkle dismisses, the new block's content is rendered.

**Key method signatures:**
```kotlin
// DailySessionHelper
fun advanceToNextBlock(): Boolean   // scans forward, updates taskIndex/blockIndex, calls endSession() if no more blocks
fun nextTask(): Boolean             // increments taskIndex by 1, computes blockIndex, calls endSession() if past end

// DailyPracticeSessionProvider
fun nextCard()                      // increments currentIndex, calls onCardAdvanced (non-WORD_BANK), calls onBlockComplete at end
```

### 9.6.3 Block Labels

| Block Type | Label in Header | Label in Sparkle |
|------------|----------------|------------------|
| `TRANSLATE` | "Translation" | "Next: Vocabulary" |
| `VOCAB` | "Vocabulary" | "Next: Verbs" |
| `VERBS` | "Verbs" | "Daily practice complete!" |

### 9.6.4 Session Completion and Cursor Advancement

After all blocks are complete:

1. `DailySessionHelper.endSession()` sets `active=false`, `finishedToken=true`.
2. `cancelDailySession()` is called (from `onComplete` callback in GrammarMateApp).
3. Inside `cancelDailySession()`:
   - Checks `ds.finishedToken == true`.
   - Reads `dailyPracticeAnsweredCounts[TRANSLATE]` and `[VERBS]`.
   - Compares against expected counts from the task list.
   - Only advances cursor if ALL TRANSLATE cards were practiced via VOICE/KEYBOARD AND ALL VERB cards were practiced via VOICE/KEYBOARD.
   - If conditions met, calls `advanceCursor(sentenceCount)`.
   - `advanceCursor()` increments `sentenceOffset` by `sentenceCount`. If the new offset >= lesson card count, advances `currentLessonIndex` and resets `sentenceOffset` to 0.
   - Clears `dailyPracticeAnsweredCounts`.
   - Calls `dailySessionHelper.endSession()` (idempotent).

```kotlin
// TrainingViewModel.cancelDailySession()
fun cancelDailySession() {
    val ds = _uiState.value.dailySession
    if (ds.finishedToken) {
        val sentenceCount = dailyPracticeAnsweredCounts[DailyBlockType.TRANSLATE] ?: 0
        val verbCount = dailyPracticeAnsweredCounts[DailyBlockType.VERBS] ?: 0
        val expectedSentenceCount = ds.tasks.count { it is DailyTask.TranslateSentence }
        val expectedVerbCount = ds.tasks.count { it is DailyTask.ConjugateVerb }
        val allSentencePracticed = sentenceCount >= expectedSentenceCount
        val allVerbsPracticed = verbCount >= expectedVerbCount
        if (allSentencePracticed && allVerbsPracticed) {
            advanceCursor(sentenceCount)
        }
    }
    dailyPracticeAnsweredCounts.clear()
    dailySessionHelper.endSession()
}
```

4. A `BlockSparkleOverlay` is shown for 800ms with the message "Daily practice complete!" and subtitle "Great job today!".
5. After the sparkle, `DailyPracticeCompletionScreen` is displayed with:
   - Title: "Session Complete!"
   - Message: "Great job! You practiced translations, vocabulary, and verb conjugations."
   - Button: "Back to Home" which calls `onExit()`.

**Note:** There is a known race condition -- `onComplete` navigates to HOME immediately while the sparkle/completion screen should be visible. In practice, the navigation may win, causing the user to return to Home without seeing the completion UI.

### 9.6.5 Navigation Between Sessions: Repeat vs. Continue

The daily practice system supports two navigation modes for subsequent sessions on the same day. These are presented via a resume dialog in `GrammarMateApp.kt` when `hasResumableDailySession()` returns true.

**`hasResumableDailySession()`**: Returns `true` when `cursor.firstSessionDate == today` AND (`firstSessionSentenceCardIds` is non-empty OR `firstSessionVerbCardIds` is non-empty).

**Repeat (same cards) -- `repeatDailyPractice(lessonLevel)`**:
- Tries, in order:
  1. In-memory cache (`lastDailyTasks`) -- fastest path, same app run.
  2. Reconstruct from stored first-session card IDs via `DailySessionComposer.buildRepeatSession()`. Block 1 rebuilt from `firstSessionSentenceCardIds`, Block 3 from `firstSessionVerbCardIds`. Block 2 always generates fresh SRS-based cards.
  3. Last resort: build fresh with cursor at `sentenceOffset = 0`.
- Does NOT advance cursor -- this is a replay of the first session's cards.
- Mastery progress is NOT reset -- previously answered cards remain answered.
- First session card IDs are valid for the same day (tracked by `firstSessionDate`).

```kotlin
// DailySessionComposer
fun buildRepeatSession(
    lessonLevel: Int, packId: String, languageId: String, lessonId: String,
    cumulativeTenses: List<String> = emptyList(),
    sentenceCardIds: List<String> = emptyList(),
    verbCardIds: List<String> = emptyList()
): List<DailyTask>
```

**Continue (new cards) -- `startDailyPractice(lessonLevel)`**:
- If the cursor has advanced (previous session completed with full VOICE/KEYBOARD), generates a new batch of cards starting from the new cursor position.
- If the cursor has NOT advanced, builds from the current cursor position (which may produce overlapping or same cards).
- Block 2 always follows its independent SRS process.
- Does NOT store first-session card IDs again (only stored for the actual first session of the day).

### 9.6.6 In-Session Card Advancement

Within a block, card advancement is managed by `DailyPracticeSessionProvider`:

```kotlin
// DailyPracticeSessionProvider
override fun nextCard()  // advances currentIndex, calls onCardAdvanced if non-WORD_BANK, calls onBlockComplete at end
override fun prevCard()  // decrements currentIndex if > 0, resets all card state
fun submitAnswerWithInput(input: String): AnswerResult?  // sets pendingInput then calls submitAnswer()
```

Between blocks, `TrainingViewModel` manages transitions:

```kotlin
// TrainingViewModel
fun advanceDailyTask(): Boolean   // persists verb progress, then calls dailySessionHelper.nextTask()
fun advanceDailyBlock(): Boolean  // calls dailySessionHelper.advanceToNextBlock()
fun repeatDailyBlock(): Boolean   // rebuilds current block via DailySessionComposer.rebuildBlock(), replaces tasks in session
fun recordDailyCardPracticed(blockType: DailyBlockType)  // increments answeredCounts, records mastery for TRANSLATE
fun persistDailyVerbProgress(card: VerbDrillCard)  // upserts VerbDrillComboProgress
```

The `DailySessionComposer.rebuildBlock()` method builds a fresh set of tasks for a single block type:

```kotlin
fun rebuildBlock(
    blockType: DailyBlockType,
    lessonLevel: Int, packId: String, languageId: String, lessonId: String,
    cumulativeTenses: List<String> = emptyList(),
    cursor: DailyCursorState = DailyCursorState()
): List<DailyTask>
```

### 9.6.7 Mic/Start Behavior on First Card

On the first card of Block 1:
- TTS does NOT auto-play (this is correct behavior for the first card of a new session).
- The Start button (if present in the UI flow) is active and functional.
- The microphone icon is active and launches ASR when tapped.
- These elements are controlled by `inputMode` and `isListening` state, which are NOT blocked by the absence of auto-play.

### 9.6.8 Card Counting and Batch Management

- Each block targets exactly 10 cards (`CARDS_PER_BLOCK` constant).
- Cards within a block are consumed sequentially (no wrap-around within a single session).
- The `DailyPracticeSessionProvider` tracks progress via `currentIndex` (Compose mutable state) which is separate from `DailySessionHelper.taskIndex` (StateFlow).
- When `currentIndex >= blockCards.size`, the provider calls `onBlockComplete()`.
- Block progress is reported via `SessionProgress(current, total)` where `current = (currentIndex + 1).coerceAtMost(blockCards.size)` and `total = blockCards.size`.

---

## 9.7 UI Components (DailyPracticeScreen)

### 9.7.1 Screen Structure

`DailyPracticeScreen` is a top-level composable receiving the following callbacks:

| Parameter | Type | Purpose |
|-----------|------|---------|
| `state` | `DailySessionState` | Current session state (active, tasks, finishedToken) |
| `blockProgress` | `BlockProgress` | Position within current block and total session |
| `currentTask` | `DailyTask?` | The task currently being practiced |
| `onSubmitSentence` | `(String) -> Boolean` | Submit answer for Block 1 card |
| `onSubmitVerb` | `(String) -> Boolean` | Submit answer for Block 3 card |
| `onShowSentenceAnswer` | `() -> String?` | Show answer for Block 1 card |
| `onShowVerbAnswer` | `() -> String?` | Show answer for Block 3 card |
| `onFlipVocabCard` | `() -> Unit` | Flip Block 2 flashcard |
| `onRateVocabCard` | `(Int) -> Unit` | Rate Block 2 flashcard (0-3) |
| `onAdvance` | `() -> Boolean` | Advance to next card within a block |
| `onAdvanceBlock` | `() -> Boolean` | Advance to next block |
| `onRepeatBlock` | `() -> Boolean` | Repeat current block |
| `onSpeak` | `(String) -> Unit` | TTS speak |
| `onStopTts` | `() -> Unit` | Stop TTS playback |
| `ttsState` | `TtsState` | Current TTS state (IDLE, SPEAKING, INITIALIZING, ERROR) |
| `onExit` | `() -> Unit` | Exit session |
| `onComplete` | `() -> Unit` | Session completed |
| `onPersistVerbProgress` | `(VerbDrillCard) -> Unit` | Persist verb drill card progress |
| `onCardPracticed` | `(DailyBlockType) -> Unit` | Track practiced card for cursor advancement |

### 9.7.2 Header (DailyPracticeHeader)

- Back arrow button (triggers exit confirmation dialog).
- Title: "Daily Practice" (semi-bold, 18sp).
- Block label badge (right-aligned): "Translation", "Vocabulary", or "Verbs" in a `Card` with `primaryContainer` background.

Exit confirmation dialog:
- Title: "Exit practice?"
- Body: "Your progress in this session will be lost."
- Buttons: "Exit" (confirms) and "Stay" (dismisses).

### 9.7.3 Progress Bar (BlockProgressBar)

- `LinearProgressIndicator` showing overall session progress: `globalPosition / totalTasks`.
- Text label: `"{globalPosition}/{totalTasks}"` (e.g., "5/30").
- Progress bar is 8dp tall with 4dp rounded corners.

### 9.7.4 Card Session Block (Blocks 1 and 3)

Renders a `TrainingCardSession` composable with custom `cardContent` and `inputControls`:

**Card content**:
- Russian prompt (labeled "RU", 20sp semi-bold).
- TTS speaker button (shows different icons based on `TtsState`):
  - IDLE: VolumeUp icon.
  - SPEAKING: StopCircle icon (red).
  - INITIALIZING: CircularProgressIndicator.
  - ERROR: ReportProblem icon (red).
- Verb info chips (Block 3 only): verb + rank, tense (abbreviated), group.

#### Font Size Scaling Behavioral Contract (Daily Practice)

DailyPracticeScreen applies `ruTextScale` (from `AudioState` / Settings) to prompt and word text across all 3 blocks:

| User Action | System Response | User Outcome |
|-------------|----------------|--------------|
| User changes text scale in Settings | All 3 daily blocks apply `ruTextScale` | Prompt text resizes in all blocks |
| `textScale = 1.5x` | TRANSLATE prompt = `(20f * 1.5f).sp = 30sp`, VOCAB prompt = `(28f * 1.5f).sp = 42sp`, VERB prompt = `(20f * 1.5f).sp = 30sp` | All blocks scale uniformly |
| `textScale = 2.0x` | TRANSLATE prompt = 40sp, VOCAB prompt = 56sp, VERB prompt = 40sp | All prompts at maximum size still fit on screen |

**Scaling rules per block:**

| Block | Element | Base Size | Scaled Formula |
|-------|---------|-----------|----------------|
| TRANSLATE (Block 1) | Card prompt (`promptRu`) | 20sp | `(20f * ruTextScale).sp` |
| VOCAB (Block 2) | Flashcard prompt word | 28sp | `(28f * ruTextScale).sp` |
| VOCAB (Block 2) | Translation/answer text | 18sp | `(18f * ruTextScale).sp` |
| VERBS (Block 3) | Card prompt (`promptRu`) | 20sp | `(20f * ruTextScale).sp` |

**NOT scaled in daily practice:**
- Verb info chips (verb infinitive, tense, group)
- Block label badge ("Translation", "Vocabulary", "Verbs")
- Rating buttons text
- Navigation buttons
- Progress bar text

**Input controls** (`DailyInputControls`):
- Hint answer card: displayed when `hintAnswer` is non-null (after 3 wrong attempts or manual "Show Answer"). Shows the correct answer in an `errorContainer`-colored card with a TTS button.
- Incorrect feedback row: "Incorrect" text (red) + remaining attempts count.
- Text field: "Your translation" label, with a microphone trailing icon. **Auto-submit in KEYBOARD mode**: when the typed text exactly matches an accepted answer (via `Normalizer.isExactMatch()`), the answer is auto-submitted and the input field is cleared, providing a seamless typing experience without requiring the Check button.
- Voice mode hint: "Say translation: {promptRu}" when in VOICE mode and session is active.
- Word bank: `FlowRow` of `FilterChip` words. The word bank is constructed as follows:
  1. Split the first accepted answer into words (by whitespace).
  2. Collect distractor words from other cards' first accepted answers (excluding words already in the answer).
  3. Shuffle distractors and take `max(0, 8 - answerWords.size)`.
  4. Combine answer words + distractor words and shuffle the final bank.
  5. The bank is cached per card ID to avoid re-computation.
  - Selected count shown ("Selected: N / M"). "Undo" button removes last selected word. `selectWordFromBank` respects duplicate words (tracks usage count per word).
- Input mode selector: three `FilledTonalIconButton` buttons for Mic, Keyboard, and Word Bank. Active mode label displayed as text.
- Show answer button: Eye icon in a `TooltipBox`. Disabled when hint is already shown.
- Check button: Full-width `Button`. Enabled only when input text is not blank, a card exists, and the session is active. Calls `provider.submitAnswerWithInput(input)`.

**Auto-voice behavior**:
- When input mode is VOICE, a card exists, and the session is active:
  - On new card: 200ms delay, then launch ASR intent.
  - After incorrect feedback: 1200ms delay, then launch ASR intent.
- After correct voice answer: 400ms delay, then auto-advance via `nextCard()`.

### 9.7.5 Vocab Flashcard Block (Block 2)

A separate composable (`VocabFlashcardBlock`) that does NOT use `CardSessionContract`:

**Card display**:
- `Card` with `surfaceVariant` background.
- Prompt text: 28sp bold, centered. Language depends on direction:
  - IT_TO_RU: shows `word.word` (Italian).
  - RU_TO_IT: shows `word.meaningRu` (Russian).
- TTS button: speaks the prompt text.
- Answer text: 18sp medium, `primary` color, centered. Always visible.
  - IT_TO_RU: shows `word.meaningRu`.
  - RU_TO_IT: shows `word.word`.

**Voice input**:
- Large (64dp) microphone button.
- ASR language tag matches the answer direction:
  - IT_TO_RU: `ru-RU` (user speaks Russian).
  - RU_TO_IT: `it-IT` (user speaks Italian).
- On correct voice match: auto-rate as "Good" (2) and auto-advance.
- On incorrect: display "You said: ..." text, user stays on card.

**Rating buttons**: Four `OutlinedButton` components in a row, each with distinct colors. Tapping any button calls `onRate(rating)` and `onAdvance()`. If no more cards, calls `onComplete()`.

### 9.7.6 Block Sparkle Overlay (BlockSparkleOverlay)

Displayed during block transitions:
- Semi-transparent black background overlay.
- `primaryContainer`-colored card with sparkle emoji (48sp).
- Message text: "Next: {BlockLabel}" or "Daily practice complete!" (20sp bold).
- Subtitle for last block: "Great job today!".
- Auto-dismisses after 800ms via `LaunchedEffect`.

### 9.7.7 Completion Screen (DailyPracticeCompletionScreen)

Full-screen centered layout:
- Title: "Session Complete!" (28sp bold, primary color).
- Body text: "Great job! You practiced translations, vocabulary, and verb conjugations."
- "Back to Home" button (full-width).

### 9.7.8 Loading State

When the session is active but `currentTask` is null:
- Full-screen centered `CircularProgressIndicator`.
- "Loading session..." text in muted color.

---

## 9.8 State Management

### 9.8.1 TrainingStateAccess Interface

`DailySessionHelper` and other helpers access the ViewModel's state through a narrow interface (defined in `DailySessionHelper.kt`):

```kotlin
interface TrainingStateAccess {
    val uiState: StateFlow<TrainingUiState>
    fun updateState(transform: (TrainingUiState) -> TrainingUiState)
    fun saveProgress()
}
```

`TrainingViewModel` provides this via an anonymous object implementation. Helpers never reference the ViewModel directly -- they call `updateState { }` and `saveProgress()` through the interface.

**DailySessionHelper public API:**

```kotlin
class DailySessionHelper(private val stateAccess: TrainingStateAccess) {
    fun startDailySession(tasks: List<DailyTask>, lessonLevel: Int)
    fun getCurrentTask(): DailyTask?
    fun getCurrentBlockType(): DailyBlockType?
    fun nextTask(): Boolean
    fun advanceToNextBlock(): Boolean
    fun replaceCurrentBlock(newTasks: List<DailyTask>)
    fun endSession()
    fun fastForwardTo(taskIndex: Int)
    fun getBlockProgress(): BlockProgress
    fun isSessionComplete(): Boolean
}
```

**Key in-memory fields on TrainingViewModel for Daily Practice:**

```kotlin
private var prebuiltDailySession: List<DailyTask>? = null     // pre-built at init for first session
private var lastDailyTasks: List<DailyTask>? = null            // cached for fast Repeat
private var dailyPracticeAnsweredCounts: MutableMap<DailyBlockType, Int> = mutableMapOf()  // VOICE/KEYBOARD per block
private var dailyCursorAtSessionStart: DailyCursorState = DailyCursorState()  // for rollback on cancel
private val dailyBadCardIds = mutableSetOf<String>()           // flagged card IDs during session
```

### 9.8.2 Dual State Tracking

The Daily Practice system has two parallel state tracking mechanisms:

1. **Compose state** (`DailyPracticeSessionProvider`): Tracks per-card position (`currentIndex`), input mode, pending results, hint state, and attempt counters. This is the authoritative state for UI rendering during a block.

2. **StateFlow state** (`DailySessionHelper` / `DailySessionState`): Tracks `taskIndex` and `blockIndex` for session-level navigation. This is the authoritative state for block transitions and session lifecycle.

These two systems are not directly synchronized. The `DailyPracticeSessionProvider` is created fresh for each block (via `remember(blockKey)`) using the current `state.taskIndex` to determine the block type. Block transitions are triggered by the provider's `onBlockComplete` callback, which calls `DailySessionHelper.advanceToNextBlock()` to update the StateFlow state.

### 9.8.3 BlockProgress Data Class

```kotlin
data class BlockProgress(
    val blockType: DailyBlockType,    // TRANSLATE, VOCAB, or VERBS
    val positionInBlock: Int,         // 1-based position within the current block
    val blockSize: Int,               // total cards in the current block
    val totalTasks: Int,              // total tasks across all blocks
    val globalPosition: Int           // 1-based position across all blocks
) {
    companion object {
        val Empty = BlockProgress(
            blockType = DailyBlockType.TRANSLATE,
            positionInBlock = 0, blockSize = 0,
            totalTasks = 0, globalPosition = 0
        )
    }
}
```

Computed by `DailySessionHelper.getBlockProgress()`:
1. Finds the start and end indices of the current block type in the task list.
2. Calculates `blockSize = blockEnd - blockStart + 1`.
3. Calculates `positionInBlock = taskIndex - blockStart + 1` (clamped to [1, blockSize]).
4. `globalPosition = taskIndex + 1`.

### 9.8.4 Persistence of Daily Progress

| What | Where | Format | Persists Across Restart? |
|------|-------|--------|--------------------------|
| Session position (`DailySessionState`) | `TrainingUiState` (in-memory `StateFlow`) | Not persisted | No (session is lost) |
| `DailyCursorState` | `TrainingProgress.dailyCursor` in `ProgressStore` | YAML in `grammarmate/progress.yaml` | Yes |
| Verb drill progress | `VerbDrillStore` | YAML in `grammarmate/drills/{packId}/verb_drill_progress.yaml` | Yes |
| Word mastery | `WordMasteryStore` | YAML in `grammarmate/drills/{packId}/word_mastery.yaml` | Yes |
| Sentence card mastery | `MasteryStore` | Per-lesson YAML in `grammarmate/` | Yes |
| `prebuiltDailySession` | In-memory field on `TrainingViewModel` | N/A | No |
| `lastDailyTasks` | In-memory field on `TrainingViewModel` | N/A | No |
| `dailyPracticeAnsweredCounts` | In-memory `MutableMap<DailyBlockType, Int>` on `TrainingViewModel` | N/A | No |
| `dailyCursorAtSessionStart` | In-memory `DailyCursorState` on `TrainingViewModel` | N/A | No |
| `dailyBadCardIds` | In-memory `MutableSet<String>` on `TrainingViewModel` | N/A | No |

All file writes use `AtomicFileWriter` (temp -> fsync -> rename) to prevent data corruption.

### 9.8.5 Session Resume

`DailySessionHelper.fastForwardTo(taskIndex)` allows jumping to a specific task index when resuming a saved session. It computes the correct `blockIndex` by counting `blockType` transitions from index 1 to `taskIndex`.

```kotlin
fun fastForwardTo(taskIndex: Int) {
    // Computes blockIndex by counting blockType changes from 1..taskIndex
    var blockIndex = 0
    for (i in 1..taskIndex) {
        if (ds.tasks[i].blockType != ds.tasks[i - 1].blockType) {
            blockIndex++
        }
    }
    // Updates taskIndex and blockIndex
}
```

**Note:** This method is defined but currently unused in the codebase. In-session position (`taskIndex`, `blockIndex`) is NOT persisted across app restarts, so full mid-session resume is not supported. After restart, the user can only Repeat (same cards from first session) or Continue (new cards from cursor position).

### 9.8.6 Block Replacement

`DailySessionHelper.replaceCurrentBlock(newTasks)` replaces the current block's tasks in the task list with new tasks (used for the Repeat Block feature):

```kotlin
fun replaceCurrentBlock(newTasks: List<DailyTask>)
```

It:
1. Finds the start index of the current block type in the task list (first task matching `currentBlockType`).
2. Finds the end index (last consecutive task of the same `blockType`).
3. Replaces the tasks in that range with the new tasks: `tasks[0..blockStart) + newTasks + tasks[blockEnd+1..size)`.
4. Resets `taskIndex` to `blockStart`.
5. Calls `saveProgress()`.

---

## 9.9 User Stories

### Session Initiation

- **As a user**, I want to start a daily practice session from the Home screen so that I can practice all three exercise types in one flow.
- **As a user**, I want the session to automatically select appropriate cards based on my current lesson level so that I practice relevant content.
- **As a user**, I want the session to resume where I left off if I start a new session later in the day so that I see new cards.

### Block 1: Sentence Translations

- **As a user**, I want to translate 10 sentences from Russian to my target language using voice, keyboard, or word bank so that I build translation automaticity.
- **As a user**, I want input modes to rotate automatically (voice, keyboard, word bank) so that I practice with varied input methods.
- **As a user**, I want to see "Incorrect" feedback with remaining attempts when I answer wrongly so that I know I can try again.
- **As a user**, I want the correct answer to be shown after 3 wrong attempts so that I can learn from my mistakes.
- **As a user**, I want to manually reveal the answer via the "Show Answer" button so that I can skip difficult cards without guessing.
- **As a user**, I want voice recognition to auto-restart after a wrong answer in voice mode so that I can retry without manual interaction.
- **As a user**, I want voice recognition to auto-advance after a correct answer so that the practice flow is smooth.
- **As a user**, I want to hear the target language pronunciation via TTS so that I improve my listening comprehension.

### Block 2: Vocabulary Flashcards

- **As a user**, I want to review vocabulary words in an Anki-style flashcard format so that I build long-term vocabulary retention.
- **As a user**, I want to see both the prompt and the answer simultaneously so that I can quickly self-assess.
- **As a user**, I want to rate my recall (Again, Hard, Good, Easy) so that the spaced repetition system schedules future reviews appropriately.
- **As a user**, I want the flashcard direction to alternate between target-to-native and native-to-target so that I practice bidirectional recall.
- **As a user**, I want to practice via voice recognition on flashcards so that I can test my pronunciation.
- **As a user**, I want overdue words to be prioritized so that I review what I am most at risk of forgetting.
- **As a user**, I want new words to be introduced when I have completed all due reviews so that my vocabulary grows progressively.

### Block 3: Verb Conjugations

- **As a user**, I want to conjugate verbs in context so that I build grammatical accuracy.
- **As a user**, I want to see the verb infinitive, tense, and group as hint chips so that I know which form is expected.
- **As a user**, I want verb cards to be ordered by weakness (weakest tenses first) so that I focus on my weakest areas.
- **As a user**, I want previously shown verb cards to be excluded from future sessions so that I see new conjugation challenges.
- **As a user**, I want tenses to be introduced progressively (matching my lesson level) so that I am not overwhelmed with advanced conjugations too early.

### Navigation and Flow

- **As a user**, I want to see a sparkle transition between blocks so that I feel a sense of progression.
- **As a user**, I want to see which block I am currently in (Translation, Vocabulary, Verbs) so that I know what I am practicing.
- **As a user**, I want an overall progress bar showing my position across all 30 cards so that I can estimate how much remains.
- **As a user**, I want to exit mid-session with a confirmation dialog so that I do not accidentally lose progress.
- **As a user**, I want to see a completion screen after finishing all three blocks so that I feel a sense of accomplishment.

### Repeat and Continue

- **As a user**, I want a "Repeat" option that replays the same cards from my first daily session so that I can reinforce what I learned today.
- **As a user**, I want a "Continue" option that gives me new cards if I have completed the previous batch so that I can practice more.
- **As a user**, I want "Continue" to behave like "Repeat" if my cursor has not advanced so that I am not stuck with no content.

### Mastery and Progression

- **As a user**, I want only VOICE and KEYBOARD answers to count as "practiced" so that my mastery accurately reflects active recall.
- **As a user**, I want WORD_BANK answers to NOT count toward mastery so that passive recognition does not inflate my progress.
- **As a user**, I want the cursor to advance to the next batch of cards only when I have actively answered all cards so that I do not skip content.
- **As a user**, I want my progress to persist across days via cursor state so that I always see new content each session.

---

## 9.10 Bad Card Reporting in Daily Practice

### 9.10.1 Overview

All three daily practice blocks support bad card reporting. Users can flag cards with errors or unnatural phrasing for later review and export. Flagged cards are stored in `BadSentenceStore` with mode-specific identifiers so they can be traced back to the originating block.

### 9.10.2 Flagging Support by Block

| Block | Block Type | Mode Field | Flag UI Location | Provider |
|-------|-----------|-----------|-----------------|----------|
| Block 1 (Translation) | `TRANSLATE` | `"daily_translate"` | Report button in `DailyInputControls` | `DailyPracticeSessionProvider` |
| Block 2 (Vocab Flashcard) | `VOCAB` | `"daily_vocab"` | Report button in `VocabFlashcardBlock` | Direct `TrainingViewModel` calls |
| Block 3 (Verb Conjugation) | `VERBS` | `"daily_verb"` | Report button in `DailyInputControls` | `DailyPracticeSessionProvider` |

### 9.10.3 DailyPracticeSessionProvider Flagging

`DailyPracticeSessionProvider` declares `supportsFlagging = true` and accepts the following callback parameters:

| Parameter | Type | Purpose |
|-----------|------|---------|
| `onFlagCard` | `((SessionCard, DailyBlockType) -> Unit)?` | Flags the current card with the block type |
| `onUnflagCard` | `((SessionCard, DailyBlockType) -> Unit)?` | Unflags the current card |
| `isCardFlagged` | `((SessionCard) -> Boolean)?` | Checks if the current card is flagged |
| `onExportFlagged` | `(() -> String?)?` | Exports all flagged cards |

The provider maps `blockType` to the correct mode string before calling the flag callback:

```kotlin
val mode = when (blockType) {
    DailyBlockType.TRANSLATE -> "daily_translate"
    DailyBlockType.VERBS -> "daily_verb"
    DailyBlockType.VOCAB -> "daily_vocab"
}
```

### 9.10.4 TrainingViewModel Daily Flagging Methods

The ViewModel maintains an in-memory `dailyBadCardIds` set for fast lookup during the session:

- `flagDailyBadSentence(cardId, languageId, sentence, translation, mode)`: Adds to `BadSentenceStore` with the active pack ID and the given mode. Also adds `cardId` to `dailyBadCardIds`.
- `unflagDailyBadSentence(cardId)`: Removes from `BadSentenceStore` and `dailyBadCardIds`.
- `isDailyBadSentence(cardId)`: Checks `dailyBadCardIds` first (fast path), then falls back to `BadSentenceStore.isBadSentence(packId, cardId)`.
- `exportDailyBadSentences()`: Calls `BadSentenceStore.exportUnified()` which produces a text file with all flagged cards grouped by language, pack, and mode.

### 9.10.5 Vocab Flashcard Block Flagging

Block 2 (Vocab Flashcard) uses a separate composable (`VocabFlashcardBlock`) that does NOT use `CardSessionContract`. It receives flagging callbacks directly:

| Parameter | Purpose |
|-----------|---------|
| `onFlagDailyBadSentence(cardId, languageId, sentence, translation, mode)` | Flags the vocab word. Sentence = `word.meaningRu ?: word.word`, translation = `word.word`, mode = `"daily_vocab"`. |
| `onUnflagDailyBadSentence(cardId)` | Unflags the vocab word. |
| `isDailyBadSentence(cardId)` | Checks if the vocab word is flagged. |
| `onExportDailyBadSentences()` | Exports all flagged cards. |

A flag/report icon button is displayed on the flashcard. When flagged, the icon turns red (`MaterialTheme.colorScheme.error`). Tapping the report icon opens a `ModalBottomSheet` with flag/unflag, export, and copy options.

### 9.10.6 Hidden Cards Behavior

`DailyPracticeSessionProvider.hideCurrentCard()` is a **no-op** -- flagging is the only report action in daily practice. Cards cannot be hidden during daily practice sessions. Flagging a card does NOT remove it from the session or affect progress tracking; it only records the report for later export.

### 9.10.7 Report Sheet UI

Both `DailyInputControls` (Blocks 1 and 3) and `VocabFlashcardBlock` (Block 2) render a `ModalBottomSheet` when the report button is tapped. The sheet contains:

1. **Card prompt text** (displayed at the top for context)
2. **"Add to bad sentences list"** (or "Remove from bad sentences list" if already flagged) with a warning icon
3. **"Export bad sentences to file"** with a download icon
4. **"Copy text"** with a copy icon -- copies `ID: ...\nSource: ...\nTarget: ...` to clipboard

After export, an inline message shows the exported file path or "No bad sentences to export".

### Edge Cases

- **As a user**, I want the session to gracefully handle packs without vocab or verb drill content so that I can still practice translations.
- **As a user**, I want the session to fail gracefully (return to Home) if no cards are available so that I am not stuck on a loading screen.
- **As a user**, I want the app to always start on the Home screen after a restart so that I am not stuck in a daily practice loading state.

---

## 9.11 [UI-CONSISTENCY-2025] Eye Mode in Daily Practice

Daily practice blocks (TRANSLATE and VERBS) MUST show the hint answer in a pink Card matching the VerbDrill reference style:

- Pink `Card(errorContainer.copy(alpha = 0.3f))` background
- Red answer text (`MaterialTheme.colorScheme.error`)
- Inline TTS replay button

**IMPORTANT:** Eye mode works at ALL HintLevel settings. The hint card renders whenever `provider.hintAnswer != null`, regardless of whether hintLevel is EASY, MEDIUM, or HARD. HintLevel controls parenthetical hints in prompt text (e.g., stripping `(dire)` and `(verità)` from the card body), NOT the eye/show-answer button or the hint answer card visibility. Any guard that checks `hintLevel == EASY` before rendering the HintAnswerCard is a bug.

This replaces the current `errorContainer`-colored card (described in section 9.7.4, element "Hint answer card") with the unified VerbDrill-style pink Card.

Class paths:
- `ui/DailyPracticeScreen.kt` -- DailyInputControls hint card rendering
- `ui/components/DailyPracticeComponents.kt` -- DailyInputControls composable

Reference implementation: `ui/screens/VerbDrillScreen.kt:392-425`.

### Report Sheet Alignment

The report bottom sheet in all daily practice blocks MUST have 4 options matching the TrainingScreen reference:
1. Flag / Unflag bad sentence
2. Hide card
3. Export bad sentences
4. Copy text

Class paths:
- `ui/DailyPracticeScreen.kt` -- report sheet in TRANSLATE/VERBS and VOCAB blocks
- `ui/components/DailyPracticeComponents.kt` -- DailyReportSheet composable
- `ui/components/SharedReportSheet.kt` (NEW) -- shared 4-option report sheet

Reference implementation: `ui/screens/TrainingScreen.kt:479-563`.


---


# 5. Audio System (TTS & ASR) — Specification

## 5.1 TTS (Text-to-Speech)

### 5.1.1 Architecture

The TTS subsystem provides offline text-to-speech synthesis using the **Sherpa-ONNX** library (AAR `sherpa-onnx-static-link-onnxruntime-1.12.40.aar` in `app/libs/`). The architecture follows the same data-layer pattern used throughout the app:

```
data/TtsModelRegistry.kt   -- pure-data model spec registry (language -> URL, files, type)
data/TtsModelManager.kt    -- file I/O, network download, progress Flow
data/TtsEngine.kt          -- native OfflineTts wrapper, AudioTrack playback, lifecycle
ui/TrainingViewModel.kt    -- holds TtsEngine + TtsModelManager instances, exposes speak()/stop()
ui/GrammarMateApp.kt       -- renders speaker icon, download dialogs, observes TtsState
```

The Sherpa-ONNX AAR is statically linked against ONNX Runtime, so no external ONNX libraries are needed. All inference runs on CPU (`provider = "cpu"`, `numThreads = 4`). Models are downloaded to internal storage (`context.filesDir/tts/`) on first use; they are not bundled in the APK.

The TTS engine uses a **single `OfflineTts` instance at a time**, reinitialized when the active language changes. This keeps memory usage to one model's worth of native ONNX runtime allocations.

### 5.1.2 Model Registry

**File:** `data/TtsModelRegistry.kt`

The registry maps language IDs to their TTS model specifications. It is a pure-data object with no Android dependencies.

#### Data Structures

```kotlin
enum class TtsModelType { KOKORO, VITS_PIPER }

data class TtsModelSpec(
    val languageId: String,          // ISO 639-1 code: "en", "it"
    val displayName: String,          // "English", "Italian"
    val modelType: TtsModelType,      // Architecture: KOKORO or VITS_PIPER
    val downloadUrl: String,          // GitHub releases tar.bz2 URL
    val archivePrefix: String,        // Top-level dir to strip from tar entries
    val modelDirName: String,         // Subdirectory under filesDir/tts/
    val fallbackDownloadSize: Long,   // Bytes, used when Content-Length is unknown
    val minRequiredBytes: Long,       // Min free storage needed for download + extraction
    val requiredFiles: List<String>,  // Relative paths that must exist after extraction
    val requiredDirs: List<String>,   // Relative directory paths that must exist
    val modelFileName: String = "model.onnx"  // Primary ONNX model file name
)
```

#### Registered Models

| Language | ID | Architecture | Model | Download Size | Disk Size | Required Files | Required Dirs |
|----------|-----|-------------|-------|---------------|-----------|----------------|---------------|
| English | `en` | KOKORO | kokoro-en-v0_19 | ~350 MB | ~700 MB | `model.onnx`, `tokens.txt`, `voices.bin` | `espeak-ng-data` |
| Italian | `it` | VITS_PIPER | vits-piper-it_IT-paola-medium | ~65 MB | ~150 MB | `it_IT-paola-medium.onnx`, `tokens.txt` | `espeak-ng-data` |

#### API

```kotlin
object TtsModelRegistry {
    val models: Map<String, TtsModelSpec>  // All registered models
    fun specFor(languageId: String): TtsModelSpec?  // Lookup by language ID
}
```

Adding a new language requires a single entry in the `models` map.

### 5.1.3 Model Manager

**File:** `data/TtsModelManager.kt`

Manages downloading, storing, and verifying TTS models on device internal storage. Follows the same download-on-demand pattern used for all large assets.

#### Storage Layout

```
context.filesDir/tts/
  kokoro-en-v0_19/             -- English (Kokoro)
    model.onnx                  (~330 MB)
    tokens.txt                  (~1.1 KB)
    voices.bin                  (~5.5 MB)
    espeak-ng-data/             (~10 MB)
      ...
  vits-piper-it_IT-paola-medium/  -- Italian (VITS Piper)
    it_IT-paola-medium.onnx     (~60 MB)
    tokens.txt                  (~2 KB)
    espeak-ng-data/             (~5 MB)
      ...
```

All model data is in internal storage, cleaned up on app uninstall.

#### Download State

```kotlin
sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val percent: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Extracting(val percent: Int) : DownloadState()
    object Done : DownloadState()
    data class Error(val message: String) : DownloadState()
}
```

#### Public API

| Method | Description |
|--------|-------------|
| `modelDir(languageId): File` | Absolute path to model directory |
| `isModelReady(languageId): Boolean` | True if all required files are present and non-empty, and all required dirs exist |
| `getDownloadedSize(languageId): Long` | Total size of model files in bytes (recursive walk) |
| `deleteModel(languageId)` | Recursively delete model dir and cached archive |
| `getAvailableStorageBytes(): Long` | Free space on internal storage |
| `isNetworkMetered(): Boolean` | Whether current network is metered (cellular) |
| `download(languageId): Flow<DownloadState>` | Download and extract model, emits progress |
| `downloadMultiple(languageIds): Flow<Map<String, DownloadState>>` | Sequential multi-language download |

#### Download Flow

1. **Check if ready** -- if model files already present, emit `Done` immediately
2. **Storage check** -- compare available bytes against `spec.minRequiredBytes`
3. **Download with retries** -- up to 3 attempts, 60s connect / 120s read timeouts
4. **Redirect handling** -- manual HTTP redirect following (up to 5 hops) for GitHub release URLs
5. **Progress reporting** -- percent-based via `Downloading` state; uses `fallbackDownloadSize` when `Content-Length` is unknown
6. **Cancellation** -- checks `currentCoroutineContext().isActive` each read iteration
7. **Extract** -- `tar.bz2` extraction via Apache Commons Compress (`TarArchiveInputStream` + `BZip2CompressorInputStream`), stripping `archivePrefix` from entry paths
8. **Cleanup** -- delete archive file after successful extraction
9. **Error handling** -- on extraction failure, delete output dir and emit `Error`

`downloadMultiple` downloads languages sequentially (not in parallel) to avoid doubling disk I/O and network usage. It aggregates per-language states into a single map emitted on each change.

#### Dependencies

- `org.apache.commons:commons-compress:1.26.1` (~500 KB) for tar.bz2 extraction

### 5.1.4 TTS Engine

**File:** `data/TtsEngine.kt`

Wraps the Sherpa-ONNX `OfflineTts` native object, manages audio playback via `AudioTrack`, and provides a state machine for the TTS lifecycle.

#### State Machine

```
IDLE ──initialize()──> INITIALIZING ──success──> READY
                                                         │
                                              speak() ───┤
                                                         v
                                                      SPEAKING
                                                         │
                                          stop() / done ──┤
                                                         v
                                                       READY
                                                         │
                                             release() ──┤
                                                         v
                                                       IDLE

Any state ──error──> ERROR
ERROR ──initialize()──> INITIALIZING (recovery path)
```

```kotlin
enum class TtsState {
    IDLE,           // No TTS loaded, no playback
    INITIALIZING,   // OfflineTts is being created (1-3 seconds)
    READY,          // Engine loaded, ready to synthesize
    SPEAKING,       // Audio is playing
    ERROR           // Initialization or playback failed
}
```

#### Public API

| Method | Description |
|--------|-------------|
| `state: StateFlow<TtsState>` | Observable engine state |
| `isReady: Boolean` | True when state == READY |
| `activeLanguageId: String?` | Currently loaded language (read-only) |
| `initialize(languageId)` | Load model for language; no-op if already loaded for same language |
| `speak(text, languageId, speakerId, speed)` | Synthesize and play audio |
| `stop()` | Stop current playback immediately |
| `release()` | Free all native resources, reset to IDLE |

#### Initialization Flow

1. **Fast path**: if already READY for the requested language, return immediately
2. **Language switch**: if a different language is loaded, call `release()` first and await cleanup
3. **Speaking guard**: if currently SPEAKING, stop and await the speak job to finish
4. **Concurrency guard**: if INITIALIZING, spin-wait until complete
5. **CAS transition**: atomically transition IDLE or ERROR to INITIALIZING
6. **Registry lookup**: get `TtsModelSpec` for the language
7. **File validation**: verify all `requiredFiles` exist and are non-empty before loading into native code
8. **Config building**: `buildConfig(spec, modelDir)` dispatches on `TtsModelType`:
   - **KOKORO**: builds `OfflineTtsKokoroModelConfig` with `model`, `voices`, `tokens`, `dataDir`
   - **VITS_PIPER**: builds `OfflineTtsVitsModelConfig` with `model`, `tokens`, `dataDir`, plus noise/length scale parameters (`noiseScale=0.667f`, `noiseScaleW=0.8f`, `lengthScale=1.0f`)
9. **Native instantiation**: `OfflineTts(config)` loads the ONNX model into native memory
10. **Result**: on success sets READY; on failure sets ERROR

#### Config Building (Dual Architecture)

```kotlin
private fun buildConfig(spec: TtsModelSpec, modelDir: File): OfflineTtsConfig {
    val modelConfig = when (spec.modelType) {
        TtsModelType.KOKORO -> OfflineTtsModelConfig(
            kokoro = OfflineTtsKokoroModelConfig(
                model = File(modelDir, "model.onnx").absolutePath,
                voices = File(modelDir, "voices.bin").absolutePath,
                tokens = File(modelDir, "tokens.txt").absolutePath,
                dataDir = File(modelDir, "espeak-ng-data").absolutePath,
            ),
            numThreads = 4, debug = false, provider = "cpu",
        )
        TtsModelType.VITS_PIPER -> OfflineTtsModelConfig(
            vits = OfflineTtsVitsModelConfig(
                model = File(modelDir, spec.modelFileName).absolutePath,
                lexicon = "",
                tokens = File(modelDir, "tokens.txt").absolutePath,
                dataDir = File(modelDir, "espeak-ng-data").absolutePath,
                dictDir = "",
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f,
            ),
            numThreads = 4, debug = false, provider = "cpu",
        )
    }
    return OfflineTtsConfig(model = modelConfig)
}
```

Key details:
- Kokoro requires `voices.bin` (multi-speaker voice embeddings); VITS/Piper does not
- Piper uses `espeak-ng-data` for phonemization; `lexicon` and `dictDir` are empty strings
- All paths are absolute filesystem paths (not asset paths) since models live in internal storage

#### Synthesis and Playback Flow

1. **Pre-check**: blank text is a no-op; speed is clamped to `[0.3, 3.0]`
2. **Auto-init**: if engine not ready for requested language, calls `initialize(languageId)` first
3. **Cancel previous**: cancels and joins any running speak job
4. **Generation counter**: increments an `AtomicInteger` to track which generation owns the current AudioTrack
5. **Audio focus**: requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` via `AudioManager`
6. **AudioTrack setup**: creates a streaming `AudioTrack` (MONO, PCM_FLOAT, model's sample rate, `MODE_STREAM`)
7. **Streaming synthesis**: calls `tts.generateWithConfigAndCallback(text, config, callback)` which invokes the callback with chunks of float samples as they are generated
8. **Callback writes**: each chunk is written to AudioTrack with `WRITE_BLOCKING`; if `isStopped` is set, callback returns 0 to abort generation
9. **Cleanup**: in `finally` block, stops and releases AudioTrack, abandons audio focus, transitions state back to READY (only if generation counter matches)

#### Speaker IDs

Kokoro English v0_19 speakers:
- `0` = `af` (default, American female)
- `5` = `am_adam` (American male)

Italian VITS/Piper uses a single speaker (Paola), so `speakerId` is effectively ignored.

#### Audio Focus Management

- **Request**: `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` before playback
- **Abandon**: after playback completes or is stopped
- **API level handling**: uses `AudioFocusRequest` on API 26+, deprecated `requestAudioFocus` on older versions
- **Error tolerance**: focus request/release failures are logged but do not block playback

#### Resource Cleanup

- `stop()`: sets `isStopped` flag, cancels speak job, stops AudioTrack
- `release()`: stops playback, cancels speak job, frees native `OfflineTts` on a coroutine (avoids blocking), resets to IDLE

### 5.1.5 Offline Behavior

- Models are **not bundled** in the APK. They are downloaded on first use from GitHub releases.
- Once downloaded, synthesis is fully offline. No internet connection required.
- The download-on-demand approach avoids inflating APK size (~36 MB from the AAR alone).
- Models are stored in `context.filesDir` (internal storage), which is:
  - Private to the app (not accessible to other apps)
  - Cleaned up automatically on app uninstall
  - Not counted against the user's visible storage in some Android versions

### 5.1.6 Language Support

| Language | Model Architecture | Model Name | Voice | Size (download) |
|----------|-------------------|------------|-------|-----------------|
| English (`en`) | Kokoro v0.19 | kokoro-en-v0_19 | `af` (American female, default) | ~350 MB |
| Italian (`it`) | VITS Piper | vits-piper-it_IT-paola-medium | Paola (female) | ~65 MB |

Languages with no registered TTS model gracefully degrade: `TtsModelRegistry.specFor()` returns null, `TtsEngine.initialize()` sets ERROR state, and the speaker button shows an error icon.

### 5.1.7 ViewModel Integration

`TrainingViewModel` owns both `TtsEngine` and `TtsModelManager` instances:

- **State propagation**: `ttsEngine.state` is collected into `TrainingUiState.ttsState`
- **Model check**: `checkTtsModel()` called on init and language switch, updates `ttsModelReady`
- **Background download**: `startBackgroundTtsDownload()` downloads missing language models on ViewModel init
- **Auto-initialization after download**: When a background or user-initiated download completes, `AudioCoordinator` automatically calls `ttsEngine.initialize()` if the engine is in ERROR or IDLE state. This ensures the TTS speaker icon updates from error/loading to speaker icon without requiring a user action or screen re-entry.
- **Speak**: `onTtsSpeak(text)` initializes engine for current language if needed, then calls `speak()`
- **Download trigger**: `startTtsDownload()` for user-initiated download with dialog progress
- **Language switch**: `selectLanguage()` propagates language to `TtsModelManager.currentLanguageId` and rechecks model readiness
- **Lifecycle**: `onCleared()` cancels background downloads and calls `ttsEngine.release()`

#### TrainingUiState TTS Fields

```kotlin
val ttsState: TtsState = TtsState.IDLE,
val ttsDownloadState: DownloadState = DownloadState.Idle,
val ttsModelReady: Boolean = false,
val ttsMeteredNetwork: Boolean = false,
val bgTtsDownloading: Boolean = false,
val bgTtsDownloadStates: Map<String, DownloadState> = emptyMap()
```

### 5.1.8 UI Integration

#### Speaker Icon Placement

Three locations in the UI have a speaker icon button:

| Location | Trigger | Text Spoken |
|----------|---------|-------------|
| CardPrompt (Russian prompt card) | User taps speaker | First accepted answer (target language) |
| ResultBlock (answer display) | User taps speaker | Answer text (target language) |
| Vocab flashcard (after reveal) | User taps speaker | Target language text |

Only target-language text is ever spoken. Russian prompts are never synthesized.

#### TtsSpeakerButton States

| TTS State | Icon | Behavior |
|-----------|------|----------|
| IDLE / READY | Volume Up | Tap triggers speak |
| SPEAKING | Stop Circle (red) | Tap stops playback |
| INITIALIZING | Circular Progress Indicator | Loading spinner |
| ERROR | Report Problem (red) | Error state, auto-recovers when download completes |

**Icon auto-recovery**: When TTS models finish downloading in the background, the AudioCoordinator automatically re-initializes the TTS engine. The engine transitions from ERROR -> INITIALIZING -> READY, which propagates through `ttsEngine.state` -> `startTtsStateCollection()` -> `uiState.audio.ttsState` -> composable recomposition. The speaker icon updates from red error to VolumeUp within 2 seconds of download completion, without requiring user action or screen re-entry.

**TTS error handling**: TTS `speak()` calls are wrapped in try-catch at both engine level and caller level. If models are not loaded, `speak()` silently skips — no crash. This is expected behavior when a user taps the TTS button before models finish downloading.

#### Download Dialog

When the user taps the speaker icon for a language whose model is not downloaded, a dialog appears:
- **Idle**: shows estimated download size and language name, Download/Cancel buttons
- **Downloading**: shows percentage and progress bar, no Cancel during active download
- **Extracting**: shows extraction percentage and progress bar
- **Done**: "Download complete! Initializing..."
- **Error**: shows error message, OK button

#### Background Download Indicator

A thin bar at the top of the screen shows background download progress:
```
EN 45% | IT pending
```
Disappears when all downloads complete.

---

## 5.2 ASR (Automatic Speech Recognition)

### 5.2.1 Architecture

The ASR subsystem provides offline speech-to-text recognition using the **Sherpa-ONNX** library (same AAR as TTS). It uses a **Whisper Small multilingual model** with **Silero VAD** (Voice Activity Detection) for microphone-based endpointing.

```
data/AsrModelRegistry.kt   -- ASR + VAD model specs and download info
data/AsrModelManager.kt    -- Model download/verification (mirrors TTS download flow)
data/AsrEngine.kt          -- ASR engine (OfflineRecognizer + VAD + AudioRecord)
ui/TrainingViewModel.kt    -- holds AsrEngine, processes recognition results
ui/GrammarMateApp.kt       -- triggers recording, shows ASR state
```

The system replaces Android's built-in `RecognizerIntent` (which required internet and Google app) with fully offline recognition.

### 5.2.2 Model Registry

**File:** `data/AsrModelRegistry.kt`

Contains specifications for the ASR model and the separate VAD model.

#### Data Structures

```kotlin
enum class AsrModelType { WHISPER }

data class AsrModelSpec(
    val modelId: String,
    val displayName: String,
    val modelType: AsrModelType,
    val downloadUrl: String,
    val archivePrefix: String,
    val modelDirName: String,
    val fallbackDownloadSize: Long,
    val minRequiredBytes: Long,
    val requiredFiles: List<String>,
)
```

#### Registered Models

**ASR Model (Whisper Small Multilingual):**

| Field | Value |
|-------|-------|
| modelId | `whisper-small-multilingual` |
| Model files | `small-encoder.int8.onnx`, `small-decoder.int8.onnx`, `small-tokens.txt` |
| Download URL | `sherpa-onnx-whisper-small.tar.bz2` from GitHub releases |
| Download size | ~375 MB |
| Min required storage | 800 MB |
| Directory | `asr/whisper-small/` |
| Languages | EN, IT, RU (and 90+ others) |

**VAD Model (Silero VAD):**

| Field | Value |
|-------|-------|
| modelId | `silero-vad` |
| Model file | `silero_vad.onnx` |
| Download URL | Direct download (no archive) |
| Download size | ~2 MB |
| Min required storage | 5 MB |
| Directory | `asr/vad/` |

#### API

```kotlin
object AsrModelRegistry {
    val defaultModel: AsrModelSpec       // Whisper Small multilingual
    val vadModel: AsrModelSpec           // Silero VAD
    fun asrModelDir(context): File       // Path to ASR model directory
    fun vadModelDir(context): File       // Path to VAD model directory
    fun isAsrReady(context): Boolean     // ASR model files present and non-empty
    fun isVadReady(context): Boolean     // VAD model file present and non-empty
    fun isReady(context): Boolean        // Both ASR and VAD ready
    fun whisperLanguageCode(languageId): String  // Map app language ID to Whisper code
}
```

#### Language Code Mapping

| App Language ID | Whisper Language Code |
|----------------|----------------------|
| `en` | `en` |
| `it` | `it` |
| `ru` | `ru` |
| Other | Passed through as-is (ISO 639-1) |

### 5.2.3 Model Manager

**File:** `data/AsrModelManager.kt`

Manages downloading ASR and VAD models. Shares the same `DownloadState` sealed class as TTS. Uses Apache Commons Compress for tar.bz2 extraction.

#### Storage Layout

```
context.filesDir/asr/
  whisper-small/                  -- Whisper Small multilingual
    small-encoder.int8.onnx        (~50 MB, int8 quantized)
    small-decoder.int8.onnx        (~180 MB, int8 quantized)
    small-tokens.txt               (~500 KB)
  vad/                            -- Silero VAD
    silero_vad.onnx                (~2 MB)
```

#### Public API

| Method | Description |
|--------|-------------|
| `isReady(): Boolean` | Both ASR and VAD models present |
| `isAsrReady(): Boolean` | ASR model files present and non-empty |
| `isVadReady(): Boolean` | VAD model file present and non-empty |
| `isNetworkMetered(): Boolean` | Whether current network is metered |
| `downloadVad(): Flow<DownloadState>` | Download VAD model (small file, no archive) |
| `downloadAsr(): Flow<DownloadState>` | Download and extract ASR model |

#### Download Flow

**VAD model** (small, direct download):
1. Check if already present
2. Search for pre-placed local file in `Downloads/BaseGrammy/` or app external storage (offline sideloading support)
3. If local file found, copy to target and return
4. Otherwise, download via HTTP with redirect following (up to 5 redirects)
5. Writes directly to target directory (no archive extraction needed)

**ASR model** (large, archive-based):
1. Check if already ready -- if so, emit `Done`
2. Storage check against `spec.minRequiredBytes` (800 MB)
3. Search for pre-placed local archive in known directories:
   - `Downloads/BaseGrammy/whisper-small.tar.bz2`
   - `Downloads/BaseGrammy/sherpa-onnx-whisper-small.tar.bz2`
   - App external storage equivalents
4. If local archive found (must exceed 100 MB minimum size), extract from it
5. Otherwise, download from GitHub releases:
   - Up to 3 retry attempts
   - 60s connect / 120s read timeouts
   - Manual HTTP redirect following (up to 5 hops)
   - Progress reporting with `fallbackDownloadSize` when Content-Length unknown
   - Cancellation support
6. Extract tar.bz2 via Apache Commons Compress
7. On extraction failure, delete output dir and emit `Error`
8. On success, delete archive file

#### Local File Sideload Paths

The manager searches for pre-placed model files to support offline deployment:

| Component | Search Paths |
|-----------|-------------|
| VAD model | `Downloads/BaseGrammy/silero_vad.onnx`, `<external-files>/asr-models/silero_vad.onnx` |
| ASR archive | `Downloads/BaseGrammy/whisper-small.tar.bz2`, `Downloads/BaseGrammy/sherpa-onnx-whisper-small.tar.bz2`, `<external-files>/asr-models/<name>.tar.bz2` |

### 5.2.4 ASR Engine

**File:** `data/AsrEngine.kt`

Wraps the Sherpa-ONNX `OfflineRecognizer` (Whisper model) and `Vad` (Silero VAD) for microphone-based speech recognition with automatic endpointing.

#### State Machine

```
IDLE ──initialize()──> INITIALIZING ──success──> READY
                                                        │
                                    recordAndTranscribe()┤
                                                        v
                                                    RECORDING
                                                        │
                                          (speech detected, then silence)
                                                        v
                                                   RECOGNIZING
                                                        │
                                            (decode complete)──> READY

Any state ──error──> ERROR
ERROR ──initialize()──> INITIALIZING (recovery path)
```

```kotlin
enum class AsrState {
    IDLE,           // No recognizer loaded
    INITIALIZING,   // OfflineRecognizer being created
    READY,          // Engine loaded, ready to record
    RECORDING,      // Microphone active, VAD processing
    RECOGNIZING,    // Decoding speech segment
    ERROR           // Initialization or recognition failed
}
```

#### Public API

| Method | Description |
|--------|-------------|
| `state: StateFlow<AsrState>` | Observable engine state |
| `errorMessage: String?` | Human-readable last error (null if no error) |
| `isReady: Boolean` | True when state == READY |
| `currentLanguage: String` | Active Whisper language code |
| `initialize(language)` | Load Whisper model + VAD; no-op if already ready |
| `setLanguage(language)` | Switch recognition language without reloading model files |
| `recordAndTranscribe(maxDurationMs): String` | Record with VAD endpointing, transcribe, return text |
| `stopRecording()` | Stop ongoing recording immediately |
| `release()` | Free all native resources |

#### Initialization Flow

1. **Guard**: if already READY, return immediately
2. **CAS transition**: atomically transition IDLE or ERROR to INITIALIZING
3. **Language mapping**: `AsrModelRegistry.whisperLanguageCode(language)` maps app language ID to Whisper code
4. **Recognizer creation** (on `Dispatchers.Default`):
   - Build `OfflineWhisperModelConfig` with `encoder`, `decoder`, `language`, `task="transcribe"`, `tailPaddings=-1`
   - Build `OfflineRecognizerConfig` with `FeatureConfig()` defaults (16kHz, 80-dim features), `greedy_search` decoding
   - Create `OfflineRecognizer(null, config)` (null assets, uses filesystem paths)
5. **VAD creation**:
   - Build `SileroVadModelConfig`: `threshold=0.5f`, `minSilenceDuration=0.25f`, `minSpeechDuration=0.25f`, `windowSize=512`, `maxSpeechDuration=30.0f`
   - Create `Vad(null, config)`
6. **Result**: on success sets READY; on failure sets ERROR with `errorMessage`

#### Language Switching

`setLanguage(language)` changes the recognition language at runtime without reloading model files:
- Rebuilds `OfflineRecognizerConfig` with the new Whisper language code
- Calls `recognizer.setConfig(config)` to update the recognizer in place
- Only works when engine is in READY state
- Logs warning if engine not initialized or not ready

#### Recognition Flow (recordAndTranscribe)

1. **Guard**: if recognizer or VAD is null, set ERROR and return empty string
2. **State transition**: RECORDING, clear errorMessage
3. **VAD reset**: `vad.reset()` to clear previous state
4. **AudioRecord setup**: 16kHz mono PCM_16BIT, buffer size >= 3200 (100ms at 16kHz), `VOICE_RECOGNITION` audio source
5. **Recording loop** (on `Dispatchers.Default`):
   - Read chunks of 1600 shorts (100ms at 16kHz)
   - Convert short samples to float (divide by 32768.0)
   - Feed to VAD: `vad.acceptWaveform(floatChunk)`
   - Check speech detection: `vad.isSpeechDetected()`
   - Collect speech segments: while `!vad.empty()`, pop segments and accumulate samples
   - **Endpoint detection**: if speech was detected and VAD no longer detects speech and segments are non-empty, break (speech completed)
   - **Timeout**: if no speech after 3 seconds, break (give up)
   - **Max duration**: break after `maxDurationMs` (default 10 seconds)
6. **Cleanup AudioRecord**: stop and release
7. **Empty check**: if no speech samples collected, set READY, return empty string
8. **Recognition** (state: RECOGNIZING):
   - Create `OfflineStream`, feed all accumulated speech samples
   - `recognizer.decode(stream)` runs Whisper inference
   - `recognizer.getResult(stream).text.trim()` returns recognized text
   - Release stream
9. **Result**: set READY, return recognized text
10. **Error handling**: any exception sets ERROR, releases AudioRecord

#### Audio Parameters

| Parameter | Value |
|-----------|-------|
| Sample rate | 16000 Hz |
| Channels | Mono |
| Encoding | PCM 16-bit (recording), converted to Float for VAD/ASR |
| Audio source | `VOICE_RECOGNITION` (noise suppression enabled) |
| VAD window size | 512 samples (32ms at 16kHz) |
| VAD speech threshold | 0.5 |
| Min speech duration | 0.25 seconds |
| Min silence duration | 0.25 seconds |
| Max speech duration | 30 seconds |
| Max recording duration | 10 seconds (configurable) |
| No-speech timeout | 3 seconds |

#### Resource Cleanup

- `stopRecording()`: stops and releases AudioRecord, transitions RECORDING to READY
- `release()`: stops recording, releases native `OfflineRecognizer` and `Vad`, resets to IDLE

### 5.2.5 Recognition Modes

The ASR system uses **offline (batch) recognition** only:

- **No streaming mode**: the entire speech segment is collected first, then decoded in one pass
- **VAD-based endpointing**: Silero VAD detects speech start and end automatically; the user does not need to press a stop button
- **Language-specific**: the Whisper language parameter is set explicitly (no auto-detection), ensuring correct transcription for the active target language

This is well-suited for language learning where inputs are short phrases (3-8 words) and sub-second latency is achieved with the Whisper Small int8 model.

### 5.2.6 Offline Behavior

- Both ASR and VAD models are downloaded on demand (not bundled in APK)
- Once downloaded, recognition is fully offline; no internet connection required
- The ASR model download is ~375 MB; the VAD model is ~2 MB
- Local file sideloading is supported: users can place model files in `Downloads/BaseGrammy/` to avoid network downloads
- Total ASR storage footprint: ~230 MB on disk (int8 quantized model)

### 5.2.7 Historical Context

The ASR subsystem replaced Android's `RecognizerIntent`-based voice input:
- Previous approach: `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` required internet + Google app
- Language mapping was: `ru` -> `ru-RU`, `it` -> `it-IT`, else `en-US`
- Two separate launchers existed in `GrammarMateApp.kt`
- The new offline ASR uses the same patterns as the TTS subsystem for consistency

---

## 5.3 Integration Points

### 5.3.1 Training Flow Integration

#### TTS in Training

| Context | When TTS Triggers | Text Spoken | Source |
|---------|-------------------|-------------|--------|
| Card prompt screen | User taps speaker icon | Target language answer | `state.answerText ?: state.currentCard?.acceptedAnswers?.firstOrNull()` |
| Result block (after answer) | User taps speaker icon | Answer text | `state.answerText` |
| Vocab flashcard (after reveal) | User taps speaker icon | Target language word | `state.vocabAnswerText` |

TTS is always user-initiated (tap on speaker icon). There is no auto-play behavior.

#### ASR in Training

| Context | When ASR Triggers | Input Mode | Result Processing |
|---------|-------------------|------------|-------------------|
| Card prompt (VOICE mode) | User taps microphone button | Voice answer to translation prompt | Recognized text compared against accepted answers |
| Daily practice translation block | User taps microphone button | Voice answer for daily translation | Same comparison logic |

The recognized text is processed identically to keyboard/word-bank input -- compared against the card's accepted answers list using the same matching logic (case-insensitive, accent-tolerant).

### 5.3.2 Mode-Dependent Audio Behavior

| Training Mode | TTS Used | ASR Used |
|---------------|----------|----------|
| KEYBOARD | Yes (user-initiated) | No |
| VOICE | Yes (user-initiated) | Yes (primary input method) |
| WORD_BANK | Yes (user-initiated) | No |
| Boss battle | Yes (user-initiated) | Optional (VOICE mode in boss) |
| Daily practice | Yes (user-initiated) | Yes (translation block) |

### 5.3.3 Error Handling When Models Not Available

#### TTS Fallback Chain

1. User taps speaker icon
2. Check `ttsModelReady` for active language
3. If model not downloaded:
   - If background download in progress for this language: show download dialog with current progress
   - Otherwise: show download dialog with size estimate and Download/Cancel buttons
4. If model downloaded but engine not initialized: auto-initialize, then speak
5. If engine in ERROR state: attempt re-initialization
6. If no TTS model registered for language: speaker button shows error icon, graceful degradation

#### ASR Fallback Chain

1. User taps microphone / selects VOICE mode
2. Check `AsrModelRegistry.isReady(context)` for both ASR and VAD models
3. If models not downloaded: show download dialog (or offer to switch to KEYBOARD mode)
4. If engine in ERROR state: attempt re-initialization
5. If recognition returns empty string: show "No speech detected" feedback
6. If microphone unavailable: show error, suggest KEYBOARD mode

### 5.3.4 Memory Management

| Component | Native Memory (approximate) |
|-----------|---------------------------|
| TTS engine (Kokoro English) | ~500 MB |
| TTS engine (VITS Italian) | ~80 MB |
| ASR engine (Whisper Small) | ~300-500 MB |
| VAD (Silero) | ~10 MB |

Loading both TTS and ASR simultaneously may use 500-1000 MB of native memory. Mitigations:
- TTS engine is initialized lazily on first speak request
- ASR engine is initialized lazily on first voice input
- Only one TTS model is loaded at a time (language switch releases the previous one)
- ASR and TTS engines can coexist but total memory is significant on low-end devices
- Engines are released in `onCleared()` when the ViewModel is destroyed

### 5.3.5 Threading Model

```
UI thread (Main)
  |
  v
viewModelScope (Main)
  |
  +-- Dispatchers.IO      -- TTS model download, file extraction (TtsModelManager)
  |                        -- ASR model download (AsrModelManager)
  |
  +-- Dispatchers.Default  -- OfflineTts initialization and synthesis (TtsEngine)
  |                        -- OfflineRecognizer initialization and decode (AsrEngine)
  |                        -- AudioRecord loop + VAD processing (AsrEngine)
  |
  +-- ttsScope (Default)   -- AudioTrack playback coroutine (TtsEngine internal)
  |
  +-- Native thread        -- Streaming synthesis callback (AudioTrack.write)
```

### 5.3.6 Gradle Dependencies

```kotlin
dependencies {
    // Sherpa-ONNX (TTS + ASR, statically linked ONNX Runtime)
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.12.40.aar"))

    // Tar.bz2 extraction for model downloads
    implementation("org.apache.commons:commons-compress:1.26.1")
}
```

No other native dependencies are required. The AAR contains ONNX Runtime, Sherpa-ONNX native code, and Kotlin bindings for both TTS and ASR.

### 5.3.7 APK Size Impact

| Component | Size Impact |
|-----------|-------------|
| Sherpa-ONNX AAR | ~36 MB (or ~18 MB per ABI with splits) |
| Apache Commons Compress | ~500 KB |
| **Total additional APK size** | **~36-37 MB** |

Downloaded models are not included in the APK. Total on-device storage for all models (English TTS + Italian TTS + ASR + VAD) is approximately 1.1 GB.
