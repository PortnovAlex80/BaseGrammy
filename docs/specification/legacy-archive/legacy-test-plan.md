# Test Plan for BaseGrammy -- Full Feature Coverage

**Version:** 2.0
**Date:** 2026-01-16 (updated 2026-05-13)
**Goal:** Regression protection during agent-driven feature development

---

## Test Structure: UC-Based + Unit-Based

Tests are organized in two complementary structures:

### Layer 1: UC Acceptance Tests (behavioral)

Each acceptance criterion (AC) from `22-use-case-registry.md` maps to one test.
Tests verify BEHAVIOR: "given preconditions, when user does X, then Y happens".

**Priority assignment:** P0 = training core flow (Domains 1-4), P1 = drills/daily practice (Domains 5-7), P2 = navigation/packs/persistence/hints (Domains 8-11).

#### Domain 1: Training Session (Start, Answer, Next, Finish)

| Test ID | UC-ID | AC | Description | Priority | Status |
|---------|-------|----|-------------|----------|--------|
| T-UC01-AC1 | UC-01 | AC1 | Session state transitions from PAUSED to ACTIVE on user action | P0 | TODO |
| T-UC01-AC2 | UC-01 | AC2 | First card's promptRu is displayed | P0 | TODO |
| T-UC01-AC3 | UC-01 | AC3 | inputMode is set to VOICE | P0 | TODO |
| T-UC01-AC4 | UC-01 | AC4 | activeTimeMs begins incrementing | P0 | TODO |
| T-UC01-AC5 | UC-01 | AC5 | currentIndex is 0 | P0 | TODO |
| T-UC02-AC1 | UC-02 | AC1 | Normalizer.normalize() trims whitespace, lowercases, removes punctuation (except hyphens), collapses spaces | P0 | TODO |
| T-UC02-AC2 | UC-02 | AC2 | Comparison checks ALL acceptedAnswers using .any {} | P0 | TODO |
| T-UC02-AC3 | UC-02 | AC3 | correctCount increments by 1 on correct answer | P0 | TODO |
| T-UC02-AC4 | UC-02 | AC4 | recordCardShowForMastery() is called (unless WORD_BANK or drill mode) | P0 | TODO |
| T-UC02-AC5 | UC-02 | AC5 | lastResult is set to true | P0 | TODO |
| T-UC03-AC1 | UC-03 | AC1 | incorrectCount increments on every wrong answer | P0 | TODO |
| T-UC03-AC2 | UC-03 | AC2 | After 3 wrong attempts, answerText contains all accepted answers joined by " / " | P0 | TODO |
| T-UC03-AC3 | UC-03 | AC3 | sessionState is set to HINT_SHOWN after 3 attempts | P0 | TODO |
| T-UC03-AC4 | UC-03 | AC4 | Timer is paused when hint is shown | P0 | TODO |
| T-UC03-AC5 | UC-03 | AC5 | incorrectAttemptsForCard resets to 0 when hint is shown | P0 | TODO |
| T-UC03-AC6 | UC-03 | AC6 | User must manually advance after hint (nextCard) | P0 | TODO |
| T-UC04-AC1 | UC-04 | AC1 | currentIndex advances by 1 (clamped to last index) | P0 | TODO |
| T-UC04-AC2 | UC-04 | AC2 | currentCard is updated to the next card | P0 | TODO |
| T-UC04-AC3 | UC-04 | AC3 | inputText is cleared | P0 | TODO |
| T-UC04-AC4 | UC-04 | AC4 | recordCardShowForMastery() is called for the new card | P0 | TODO |
| T-UC04-AC5 | UC-04 | AC5 | saveProgress() is called | P0 | TODO |
| T-UC05-AC1 | UC-05 | AC1 | completedSubLessonCount is recalculated from MasteryStore data | P0 | TODO |
| T-UC05-AC2 | UC-05 | AC2 | activeSubLessonIndex uses maxOf(current, actual) to prevent regression | P0 | TODO |
| T-UC05-AC3 | UC-05 | AC3 | subLessonFinishedToken increments, triggering navigation to LESSON screen | P0 | TODO |
| T-UC05-AC4 | UC-05 | AC4 | refreshFlowerStates() is called | P0 | TODO |
| T-UC05-AC5 | UC-05 | AC5 | forceBackupOnSave is set to true | P0 | TODO |
| T-UC05-AC6 | UC-05 | AC6 | checkAndMarkLessonCompleted() is called (marks lesson complete at 15 sub-lessons) | P0 | TODO |
| T-UC06-AC1 | UC-06 | AC1 | Exit confirmation dialog is shown before ending session | P0 | TODO |
| T-UC06-AC2 | UC-06 | AC2 | finishSession() sets sessionState to PAUSED | P0 | TODO |
| T-UC06-AC3 | UC-06 | AC3 | saveProgress() persists the session state | P0 | TODO |
| T-UC06-AC4 | UC-06 | AC4 | Navigation goes to AppScreen.LESSON, not HOME | P0 | TODO |

#### Domain 2: Mastery & Flowers (Progress, SRS, Bloom)

| Test ID | UC-ID | AC | Description | Priority | Status |
|---------|-------|----|-------------|----------|--------|
| T-UC07-AC1 | UC-07 | AC1 | uniqueCardShows goes from 0 to 1 | P0 | TODO |
| T-UC07-AC2 | UC-07 | AC2 | Card ID is added to shownCardIds | P0 | TODO |
| T-UC07-AC3 | UC-07 | AC3 | intervalStepIndex is set to 0 (1-day expected interval) | P0 | TODO |
| T-UC07-AC4 | UC-07 | AC4 | FlowerCalculator returns FlowerState.SEED for 1/150 mastery | P0 | TODO |
| T-UC07-AC5 | UC-07 | AC5 | Mastery data is persisted immediately to mastery.yaml | P0 | TODO |
| T-UC08-AC1 | UC-08 | AC1 | Flower is SEED when masteryPercent < 0.33 | P0 | TODO |
| T-UC08-AC2 | UC-08 | AC2 | Flower is SPROUT when 0.33 <= masteryPercent < 0.66 | P0 | TODO |
| T-UC08-AC3 | UC-08 | AC3 | Flower is BLOOM when masteryPercent >= 0.66 | P0 | TODO |
| T-UC08-AC4 | UC-08 | AC4 | Practicing the same card twice does NOT increment uniqueCardShows | P0 | TODO |
| T-UC08-AC5 | UC-08 | AC5 | scaleMultiplier = (masteryPercent * healthPercent).coerceIn(0.5, 1.0) | P0 | TODO |
| T-UC09-AC1 | UC-09 | AC1 | Health is 1.0 when daysSinceLastShow <= expectedInterval | P0 | TODO |
| T-UC09-AC2 | UC-09 | AC2 | Health floor is 0.5 (WILTED_THRESHOLD) | P0 | TODO |
| T-UC09-AC3 | UC-09 | AC3 | Flower is WILTING when 0.51 < healthPercent < 1.0 | P0 | TODO |
| T-UC09-AC4 | UC-09 | AC4 | Flower is WILTED when healthPercent <= 0.51 | P0 | TODO |
| T-UC09-AC5 | UC-09 | AC5 | Flower is GONE when daysSinceLastShow > 90 | P0 | TODO |
| T-UC09-AC6 | UC-09 | AC6 | Health is restored to 1.0 instantly on any new card show | P0 | TODO |
| T-UC09-AC7 | UC-09 | AC7 | After wilted recovery, interval step does NOT advance (late review penalty) | P0 | TODO |
| T-UC10-AC1 | UC-10 | AC1 | On-time review at step 0 advances to step 1 (2-day interval) | P0 | TODO |
| T-UC10-AC2 | UC-10 | AC2 | Late review does NOT change intervalStepIndex | P0 | TODO |
| T-UC10-AC3 | UC-10 | AC3 | Step is capped at 9 (last ladder index) | P0 | TODO |
| T-UC10-AC4 | UC-10 | AC4 | Stability grows with each step: stability = 0.9 * 2.2^stepIndex | P0 | TODO |
| T-UC11-AC1 | UC-11 | AC1 | First lesson has zero MIXED sub-lessons | P0 | TODO |
| T-UC11-AC2 | UC-11 | AC2 | MIXED sub-lessons contain both current and review cards | P0 | TODO |
| T-UC11-AC3 | UC-11 | AC3 | Reserve pool cards are preferred over main pool for review | P0 | TODO |
| T-UC11-AC4 | UC-11 | AC4 | Maximum 2 due lessons contribute review cards per MIXED sub-lesson | P0 | TODO |
| T-UC11-AC5 | UC-11 | AC5 | Review pool is capped at 300 cards per lesson | P0 | TODO |

#### Domain 3: Input Modes (Voice, Keyboard, Word Bank)

| Test ID | UC-ID | AC | Description | Priority | Status |
|---------|-------|----|-------------|----------|--------|
| T-UC12-AC1 | UC-12 | AC1 | Voice recognition auto-triggers when inputMode == VOICE and session is ACTIVE | P0 | TODO |
| T-UC12-AC2 | UC-12 | AC2 | Recognized text populates the input field | P0 | TODO |
| T-UC12-AC3 | UC-12 | AC3 | recordCardShowForMastery() records mastery for VOICE input | P0 | TODO |
| T-UC12-AC4 | UC-12 | AC4 | Empty or null ASR result is treated as no input (no crash, no incorrect submission) | P0 | TODO |
| T-UC13-AC1 | UC-13 | AC1 | recordCardShowForMastery() records mastery for KEYBOARD input | P0 | TODO |
| T-UC13-AC2 | UC-13 | AC2 | Empty input is rejected before validation (early return) | P0 | TODO |
| T-UC13-AC3 | UC-13 | AC3 | IME action is "Done" (single-line entry) | P0 | TODO |
| T-UC14-AC1 | UC-14 | AC1 | Word bank contains answer words + up to 3 distractors (regular) or up to 8-answerWords (verb drill/daily) | P0 | TODO |
| T-UC14-AC2 | UC-14 | AC2 | Distractors are filtered to not match correct answer words | P0 | TODO |
| T-UC14-AC3 | UC-14 | AC3 | recordCardShowForMastery() returns early (WORD_BANK does NOT count for mastery) | P0 | TODO |
| T-UC14-AC4 | UC-14 | AC4 | Tapping the last selected word removes it from the selection | P0 | TODO |
| T-UC14-AC5 | UC-14 | AC5 | WORD_BANK cards are tracked via markSubLessonCardsShown() for sub-lesson progress only | P0 | TODO |
| T-UC15-AC1 | UC-15 | AC1 | Switching to KEYBOARD mid-card from WORD_BANK allows mastery recording | P0 | TODO |
| T-UC15-AC2 | UC-15 | AC2 | Word bank chips regenerate when switching to WORD_BANK | P0 | TODO |
| T-UC15-AC3 | UC-15 | AC3 | inputText is not cleared when switching away from WORD_BANK | P0 | TODO |
| T-UC15-AC4 | UC-15 | AC4 | Input mode is NOT persisted between sessions (always resets to VOICE) | P0 | TODO |
| T-UC16-AC1 | UC-16 | AC1 | TRANSLATE block assigns input modes via index % 3 rotation | P1 | TODO |
| T-UC16-AC2 | UC-16 | AC2 | VOCAB block uses Anki-style flip+rate, not TrainingCardSession | P1 | TODO |
| T-UC16-AC3 | UC-16 | AC3 | VERB block alternates KEYBOARD and WORD_BANK | P1 | TODO |
| T-UC16-AC4 | UC-16 | AC4 | WORD_BANK cards in daily practice do NOT advance the cursor on session completion | P1 | TODO |

#### Domain 4: Boss Battle

| Test ID | UC-ID | AC | Description | Priority | Status |
|---------|-------|----|-------------|----------|--------|
| T-UC17-AC1 | UC-17 | AC1 | Boss tile is visible only when completedSubLessonCount >= 15 or testMode == true | P0 | TODO |
| T-UC17-AC2 | UC-17 | AC2 | MEGA boss is shown only for lessons at index > 0 | P0 | TODO |
| T-UC17-AC3 | UC-17 | AC3 | Card selection uses .cards (all cards including reserve pool), shuffled, capped at 300 | P0 | TODO |
| T-UC17-AC4 | UC-17 | AC4 | bossActive is set to true, bossType is set | P0 | TODO |
| T-UC17-AC5 | UC-17 | AC5 | Session starts in PAUSED state (user must press Play) | P0 | TODO |
| T-UC18-AC1 | UC-18 | AC1 | BRONZE is awarded for progress > 50% | P0 | TODO |
| T-UC18-AC2 | UC-18 | AC2 | SILVER is awarded for progress > 75% | P0 | TODO |
| T-UC18-AC3 | UC-18 | AC3 | GOLD is awarded for progress >= 100% | P0 | TODO |
| T-UC18-AC4 | UC-18 | AC4 | Reward dialog shows trophy colored by medal type | P0 | TODO |
| T-UC18-AC5 | UC-18 | AC5 | Screen returns to LESSON after boss | P0 | TODO |
| T-UC18-AC6 | UC-18 | AC6 | Pre-boss session state is restored from ProgressStore | P0 | TODO |
| T-UC19-AC1 | UC-19 | AC1 | Partial reward is calculated based on progress at exit time | P0 | TODO |
| T-UC19-AC2 | UC-19 | AC2 | Boss can be retried after exiting (no lockout) | P0 | TODO |
| T-UC19-AC3 | UC-19 | AC3 | Latest reward overwrites previous (not best-of) | P0 | TODO |
| T-UC20-AC1 | UC-20 | AC1 | Timer is an elapsed-time counter, not a countdown | P0 | TODO |
| T-UC20-AC2 | UC-20 | AC2 | saveProgress() returns early when bossActive && bossType != ELITE | P0 | TODO |
| T-UC20-AC3 | UC-20 | AC3 | After process death, bossActive resets to false and pre-boss state is restored from progress.yaml | P0 | TODO |

#### Domain 5: Daily Practice (3 Blocks, Cursor)

| Test ID | UC-ID | AC | Description | Priority | Status |
|---------|-------|----|-------------|----------|--------|
| T-UC21-AC1 | UC-21 | AC1 | startDailyPractice() saves dailyCursorAtSessionStart for rollback on cancel | P1 | TODO |
| T-UC21-AC2 | UC-21 | AC2 | Session is composed with 3 block types: TRANSLATE (10 cards), VOCAB (10 flashcards), VERBS (10 conjugations) | P1 | TODO |
| T-UC21-AC3 | UC-21 | AC3 | If first session today, card IDs are stored in cursor for Repeat | P1 | TODO |
| T-UC21-AC4 | UC-21 | AC4 | Screen transitions to AppScreen.DAILY_PRACTICE | P1 | TODO |
| T-UC22-AC1 | UC-22 | AC1 | Block sparkle overlay shows "Next: Vocabulary" after TRANSLATE block | P1 | TODO |
| T-UC22-AC2 | UC-22 | AC2 | Block sparkle overlay shows "Next: Verbs" after VOCAB block | P1 | TODO |
| T-UC22-AC3 | UC-22 | AC3 | advanceToNextBlock() correctly skips past cards of the completed block type | P1 | TODO |
| T-UC22-AC4 | UC-22 | AC4 | Each block uses its own provider (DailyPracticeSessionProvider for TRANSLATE/VERBS, VocabFlashcardBlock for VOCAB) | P1 | TODO |
| T-UC23-AC1 | UC-23 | AC1 | "Repeat" uses firstSessionSentenceCardIds and firstSessionVerbCardIds from cursor | P1 | TODO |
| T-UC23-AC2 | UC-23 | AC2 | "Repeat" does NOT advance sentenceOffset | P1 | TODO |
| T-UC23-AC3 | UC-23 | AC3 | "Continue" calls startDailyPractice() which builds new cards from cursor | P1 | TODO |
| T-UC23-AC4 | UC-23 | AC4 | Cursor advances only if all TRANSLATE + VERB cards were answered via VOICE or KEYBOARD | P1 | TODO |
| T-UC24-AC1 | UC-24 | AC1 | sentenceOffset advances by the count of practiced TRANSLATE cards | P1 | TODO |
| T-UC24-AC2 | UC-24 | AC2 | currentLessonIndex is never advanced by advanceCursor() | P1 | TODO |
| T-UC24-AC3 | UC-24 | AC3 | Cursor state survives process death (persisted in progress.yaml) | P1 | TODO |
| T-UC24-AC4 | UC-24 | AC4 | DailySessionState (in-memory) does NOT survive process death | P1 | TODO |
| T-UC25-AC1 | UC-25 | AC1 | Selection prioritizes overdue words over new words | P1 | TODO |
| T-UC25-AC2 | UC-25 | AC2 | New words fill remaining slots sorted by rank | P1 | TODO |
| T-UC25-AC3 | UC-25 | AC3 | Fallback uses least recently reviewed words | P1 | TODO |
| T-UC25-AC4 | UC-25 | AC4 | Direction alternates: even index = IT_TO_RU, odd = RU_TO_IT | P1 | TODO |
| T-UC25-AC5 | UC-25 | AC5 | Vocab block uses Anki-style flip + 4 rating buttons, not TrainingCardSession | P1 | TODO |

#### Domain 6: Verb Drill

| Test ID | UC-ID | AC | Description | Priority | Status |
|---------|-------|----|-------------|----------|--------|
| T-UC26-AC1 | UC-26 | AC1 | Verb Drill tile is visible only when lessonStore.hasVerbDrill(activePackId, languageId) returns true | P1 | TODO |
| T-UC26-AC2 | UC-26 | AC2 | reloadForPack(packId) creates pack-scoped VerbDrillStore and reloads cards | P1 | TODO |
| T-UC26-AC3 | UC-26 | AC3 | Card selection excludes cards already shown today for the combo | P1 | TODO |
| T-UC26-AC4 | UC-26 | AC4 | Batch size is 10 cards | P1 | TODO |
| T-UC26-AC5 | UC-26 | AC5 | Frequency sort orders by rank ascending | P1 | TODO |
| T-UC27-AC1 | UC-27 | AC1 | Validation uses Normalizer.normalize() on both sides | P1 | TODO |
| T-UC27-AC2 | UC-27 | AC2 | Verb drill uses single card.answer comparison, not multiple accepted answers | P1 | TODO |
| T-UC27-AC3 | UC-27 | AC3 | Voice mode auto-submits immediately on ASR result | P1 | TODO |
| T-UC27-AC4 | UC-27 | AC4 | Voice mode auto-advances after 500ms on correct answer | P1 | TODO |
| T-UC27-AC5 | UC-27 | AC5 | 3 wrong attempts auto-show hint and pause | P1 | TODO |
| T-UC27-AC6 | UC-27 | AC6 | Manual "Show Answer" (eye button) also shows hint and pauses | P1 | TODO |
| T-UC28-AC1 | UC-28 | AC1 | Progress is persisted immediately per card, not batched | P1 | TODO |
| T-UC28-AC2 | UC-28 | AC2 | everShownCardIds accumulates permanently (never resets) | P1 | TODO |
| T-UC28-AC3 | UC-28 | AC3 | todayShownCardIds resets on date change | P1 | TODO |
| T-UC28-AC4 | UC-28 | AC4 | File path is drills/{packId}/verb_drill_progress.yaml | P1 | TODO |
| T-UC29-AC1 | UC-29 | AC1 | reloadForPack() returns early if currentPackId == packId && allCards.isNotEmpty() | P1 | TODO |
| T-UC29-AC2 | UC-29 | AC2 | New VerbDrillStore instance is created scoped to new packId | P1 | TODO |
| T-UC29-AC3 | UC-29 | AC3 | allCards, progressMap, packIdForCardId are fully rebuilt | P1 | TODO |
| T-UC29-AC4 | UC-29 | AC4 | Speed tracking state is NOT reset on pack switch (minor issue) | P1 | TODO |

#### Domain 7: Vocab Drill

| Test ID | UC-ID | AC | Description | Priority | Status |
|---------|-------|----|-------------|----------|--------|
| T-UC30-AC1 | UC-30 | AC1 | Vocab Drill tile shows mastered word count | P1 | TODO |
| T-UC30-AC2 | UC-30 | AC2 | startSession() filters by selectedPos and rankMin/rankMax | P1 | TODO |
| T-UC30-AC3 | UC-30 | AC3 | Cards are sorted by word.rank ascending (most frequent first) | P1 | TODO |
| T-UC30-AC4 | UC-30 | AC4 | Batch size is up to 10 cards | P1 | TODO |
| T-UC30-AC5 | UC-30 | AC5 | If no due words: session is null, Start button shows "No due words" | P1 | TODO |
| T-UC31-AC1 | UC-31 | AC1 | Front shows Italian word for IT_TO_RU, Russian meaning for RU_TO_IT | P1 | TODO |
| T-UC31-AC2 | UC-31 | AC2 | TTS button is shown only for IT_TO_RU direction on front | P1 | TODO |
| T-UC31-AC3 | UC-31 | AC3 | Auto-flip triggers 800ms after voiceCompleted becomes true | P1 | TODO |
| T-UC31-AC4 | UC-31 | AC4 | Back shows forms (msg/fsg/mpl/fpl) for adjectives; dashes for numbers/pronouns (known bug) | P1 | TODO |
| T-UC32-AC1 | UC-32 | AC1 | Again resets intervalStepIndex to 0 | P1 | TODO |
| T-UC32-AC2 | UC-32 | AC2 | Hard keeps step unchanged | P1 | TODO |
| T-UC32-AC3 | UC-32 | AC3 | Good advances step by 1 | P1 | TODO |
| T-UC32-AC4 | UC-32 | AC4 | Easy advances step by 2 | P1 | TODO |
| T-UC32-AC5 | UC-32 | AC5 | isLearned is set to true at step >= 3 (LEARNED_THRESHOLD) | P1 | TODO |
| T-UC32-AC6 | UC-32 | AC6 | Button labels show the next interval from the ladder | P1 | TODO |
| T-UC33-AC1 | UC-33 | AC1 | normalizeForMatch() is lighter than Normalizer.normalize() (trailing punctuation only) | P1 | TODO |
| T-UC33-AC2 | UC-33 | AC2 | Match uses both exact equality and contains() | P1 | TODO |
| T-UC33-AC3 | UC-33 | AC3 | Max 3 voice attempts before auto-skip | P1 | TODO |
| T-UC33-AC4 | UC-33 | AC4 | Voice recognition language tag: IT_TO_RU = "ru-RU", RU_TO_IT = "it-IT" | P1 | TODO |
| T-UC34-AC1 | UC-34 | AC1 | WordMasteryStore is recreated with new packId | P1 | TODO |
| T-UC34-AC2 | UC-34 | AC2 | allWords and masteryMap are fully rebuilt | P1 | TODO |
| T-UC34-AC3 | UC-34 | AC3 | User filter selections (selectedPos, rankMin/rankMax) persist on pack switch | P1 | TODO |
| T-UC34-AC4 | UC-34 | AC4 | Drill direction and voice mode persist on pack switch | P1 | TODO |

#### Domain 8: Navigation & Screens

| Test ID | UC-ID | AC | Description | Priority | Status |
|---------|-------|----|-------------|----------|--------|
| T-UC35-AC1 | UC-35 | AC1 | selectLesson() rebuilds schedules and resets session state | P2 | TODO |
| T-UC35-AC2 | UC-35 | AC2 | selectSubLesson() loads cards for the chosen sub-lesson | P2 | TODO |
| T-UC35-AC3 | UC-35 | AC3 | Screen transitions via AppScreen enum | P2 | TODO |
| T-UC36-AC1 | UC-36 | AC1 | TRAINING Back shows exit dialog with "End session?" prompt | P2 | TODO |
| T-UC36-AC2 | UC-36 | AC2 | LESSON Back navigates directly to HOME | P2 | TODO |
| T-UC36-AC3 | UC-36 | AC3 | DAILY_PRACTICE system Back navigates to HOME without confirmation (in-screen arrow shows dialog) | P2 | TODO |
| T-UC36-AC4 | UC-36 | AC4 | VOCAB_DRILL system Back navigates to HOME without calling refreshVocabMasteryCount() (known bug) | P2 | TODO |
| T-UC37-AC1 | UC-37 | AC1 | Token-based navigation triggers automatically on sub-lesson completion | P2 | TODO |
| T-UC37-AC2 | UC-37 | AC2 | Navigation goes to LESSON (Lesson Roadmap), not HOME | P2 | TODO |
| T-UC37-AC3 | UC-37 | AC3 | Boss completion uses a separate bossFinishedToken with same pattern | P2 | TODO |
| T-UC38-AC1 | UC-38 | AC1 | hasVerbDrill checks grammarmate/drills/{packId}/verb_drill/ for CSV files | P2 | TODO |
| T-UC38-AC2 | UC-38 | AC2 | hasVocabDrill checks grammarmate/drills/{packId}/vocab_drill/ for CSV files | P2 | TODO |
| T-UC38-AC3 | UC-38 | AC3 | If activePackId is null, both default to false | P2 | TODO |
| T-UC38-AC4 | UC-38 | AC4 | Computation is keyed on state.activePackId and state.selectedLanguageId | P2 | TODO |

#### Domain 9: Lesson Packs (Import, Manifest)

| Test ID | UC-ID | AC | Description | Priority | Status |
|---------|-------|----|-------------|----------|--------|
| T-UC39-AC1 | UC-39 | AC1 | Path traversal entries in ZIP are rejected with error("Invalid zip entry") | P2 | TODO |
| T-UC39-AC2 | UC-39 | AC2 | Lessons with type: "verb_drill" in manifest are filtered (not parsed as standard lessons) | P2 | TODO |
| T-UC39-AC3 | UC-39 | AC3 | Verb drill files copied to drills/{packId}/verb_drill/ | P2 | TODO |
| T-UC39-AC4 | UC-39 | AC4 | Vocab drill files copied to drills/{packId}/vocab_drill/ | P2 | TODO |
| T-UC39-AC5 | UC-39 | AC5 | Re-import of same packId replaces existing content | P2 | TODO |
| T-UC39-AC6 | UC-39 | AC6 | Invalid CSV rows are silently skipped | P2 | TODO |
| T-UC40-AC1 | UC-40 | AC1 | Missing manifest results in error with temp directory cleanup | P2 | TODO |
| T-UC40-AC2 | UC-40 | AC2 | Malformed JSON throws JSONException (temp dir NOT cleaned up -- known gap) | P2 | TODO |
| T-UC40-AC3 | UC-40 | AC3 | User is returned to the app without partial import | P2 | TODO |
| T-UC41-AC1 | UC-41 | AC1 | Both default packs are bundled in assets/grammarmate/packs/ | P2 | TODO |
| T-UC41-AC2 | UC-41 | AC2 | Both DefaultPack entries exist in LessonStore.defaultPacks | P2 | TODO |
| T-UC41-AC3 | UC-41 | AC3 | Seed marker at grammarmate/seed_v1.done prevents re-seeding | P2 | TODO |
| T-UC41-AC4 | UC-41 | AC4 | forceReloadDefaultPacks() runs on every app start to ensure updated content | P2 | TODO |
| T-UC42-AC1 | UC-42 | AC1 | All lesson CSVs and index entries are removed | P2 | TODO |
| T-UC42-AC2 | UC-42 | AC2 | Pack directory is deleted | P2 | TODO |
| T-UC42-AC3 | UC-42 | AC3 | drills/{packId}/ is deleted recursively (CSVs + progress YAMLs) | P2 | TODO |
| T-UC42-AC4 | UC-42 | AC4 | Pack registry in packs.yaml is updated | P2 | TODO |

#### Domain 10: State Persistence (Backup, Restore, Onboarding)

| Test ID | UC-ID | AC | Description | Priority | Status |
|---------|-------|----|-------------|----------|--------|
| T-UC43-AC1 | UC-43 | AC1 | Backup directory is Downloads/BaseGrammy/backup_latest/ | P2 | TODO |
| T-UC43-AC2 | UC-43 | AC2 | Exactly 4 data file types + metadata.txt are included | P2 | TODO |
| T-UC43-AC3 | UC-43 | AC3 | Drill progress files (verb_drill_progress.yaml, word_mastery.yaml) are NOT included (known gap) | P2 | TODO |
| T-UC43-AC4 | UC-43 | AC4 | hidden_cards.yaml, bad_sentences.yaml are NOT included | P2 | TODO |
| T-UC43-AC5 | UC-43 | AC5 | Backup is a flat directory, not a ZIP archive | P2 | TODO |
| T-UC44-AC1 | UC-44 | AC1 | Restore copies mastery.yaml, progress.yaml, profile.yaml, streak files | P2 | TODO |
| T-UC44-AC2 | UC-44 | AC2 | Corrupt YAML files are still copied; corruption surfaces when stores parse them | P2 | TODO |
| T-UC44-AC3 | UC-44 | AC3 | Missing files in backup are silently skipped | P2 | TODO |
| T-UC44-AC4 | UC-44 | AC4 | Pre-existing files NOT in backup are left untouched (additive restore) | P2 | TODO |
| T-UC44-AC5 | UC-44 | AC5 | Streak migration from old streak.yaml to streak_{lang}.yaml is handled | P2 | TODO |
| T-UC45-AC1 | UC-45 | AC1 | restoredScreen is hardcoded to "HOME" | P2 | TODO |
| T-UC45-AC2 | UC-45 | AC2 | Card index and counts are restored from progress.yaml | P2 | TODO |
| T-UC45-AC3 | UC-45 | AC3 | Active session is restored if sessionState == ACTIVE && currentCard != null | P2 | TODO |
| T-UC45-AC4 | UC-45 | AC4 | Daily session state (DailySessionState) is NOT restored (ephemeral) | P2 | TODO |
| T-UC45-AC5 | UC-45 | AC5 | Boss battle state is NOT restored (intentional) | P2 | TODO |
| T-UC45-AC6 | UC-45 | AC6 | currentScreen is persisted but never used for restoration | P2 | TODO |
| T-UC46-AC1 | UC-46 | AC1 | Default packs are seeded from assets on first launch | P2 | TODO |
| T-UC46-AC2 | UC-46 | AC2 | First lesson tile is SPROUT; all others are LOCKED | P2 | TODO |
| T-UC46-AC3 | UC-46 | AC3 | WelcomeDialog appears when user first navigates away from HOME (not on HOME -- known UX issue) | P2 | TODO |
| T-UC46-AC4 | UC-46 | AC4 | userName defaults to "GrammarMateUser" until set | P2 | TODO |
| T-UC46-AC5 | UC-46 | AC5 | Config is seeded from assets/grammarmate/config.yaml | P2 | TODO |
| T-UC47-AC1 | UC-47 | AC1 | Maximum data loss window is ~500ms (timer interval) | P2 | TODO |
| T-UC47-AC2 | UC-47 | AC2 | Each saveProgress() writes full TrainingProgress to progress.yaml | P2 | TODO |
| T-UC47-AC3 | UC-47 | AC3 | Mastery writes are per-card and immediate (separate file) | P2 | TODO |
| T-UC47-AC4 | UC-47 | AC4 | Verb drill and word mastery writes are per-card and immediate (pack-scoped files) | P2 | TODO |
| T-UC47-AC5 | UC-47 | AC5 | AtomicFileWriter uses temp -> fsync -> rename pattern | P2 | TODO |

#### Domain 11: Difficulty & Hints (HintLevel, Parenthetical Stripping)

| Test ID | UC-ID | AC | Description | Priority | Status |
|---------|-------|----|-------------|----------|--------|
| T-UC48-AC1 | UC-48 | AC1 | Apostrophes within words are preserved (e.g. l'albero stays l'albero) | P0 | TODO |
| T-UC48-AC2 | UC-48 | AC2 | Accented/diacritic characters are kept as-is (no NFD stripping) | P0 | TODO |
| T-UC48-AC3 | UC-48 | AC3 | Hyphens are preserved | P0 | TODO |
| T-UC48-AC4 | UC-48 | AC4 | Time normalization strips minutes only ("3:15" -> "3") | P0 | TODO |
| T-UC48-AC5 | UC-48 | AC5 | Empty/whitespace-only input normalizes to "" | P0 | TODO |
| T-UC48-AC6 | UC-48 | AC6 | Partial string matches do NOT count (exact equality required) | P0 | TODO |
| T-UC49-AC1 | UC-49 | AC1 | Error tone plays on each incorrect answer | P0 | TODO |
| T-UC49-AC2 | UC-49 | AC2 | After 3 wrong attempts, answerText shows all accepted answers joined by " / " | P0 | TODO |
| T-UC49-AC3 | UC-49 | AC3 | sessionState transitions to HINT_SHOWN after 3 attempts | P0 | TODO |
| T-UC49-AC4 | UC-49 | AC4 | Timer is paused when hint is shown | P0 | TODO |
| T-UC49-AC5 | UC-49 | AC5 | incorrectAttemptsForCard resets to 0 on hint display | P0 | TODO |
| T-UC49-AC6 | UC-49 | AC6 | User cannot submit further answers while HINT_SHOWN | P0 | TODO |
| T-UC50-AC1 | UC-50 | AC1 | Eye button is always available during a card | P0 | TODO |
| T-UC50-AC2 | UC-50 | AC2 | Tapping it reveals the accepted answer(s) without counting as an incorrect attempt | P0 | TODO |
| T-UC50-AC3 | UC-50 | AC3 | In verb drill, manual show sets incorrectAttempts = 3 and hintAnswer = card.answer | P0 | TODO |
| T-UC50-AC4 | UC-50 | AC4 | TTS auto-plays the answer when revealed (if TTS is available) | P0 | TODO |

### Layer 2: Unit Tests (structural)

Existing unit tests organized by data store / component. See sections below.

### Mapping: UC to Unit Tests

| UC-ID | Covered by unit tests |
|-------|----------------------|
| UC-01 | ProgressStoreTest, TrainingViewModelTest |
| UC-02 | NormalizerTest, ProgressStoreTest, MasteryStoreTest |
| UC-03 | NormalizerTest, ProgressStoreTest |
| UC-04 | MasteryStoreTest, ProgressStoreTest |
| UC-05 | MasteryStoreTest, FlowerCalculatorTest, ProgressStoreTest |
| UC-06 | ProgressStoreTest |
| UC-07 | MasteryStoreTest, FlowerCalculatorTest |
| UC-08 | FlowerCalculatorTest, MasteryStoreTest |
| UC-09 | FlowerCalculatorTest, SpacedRepetitionConfigTest |
| UC-10 | SpacedRepetitionConfigTest, MasteryStoreTest |
| UC-11 | MixedReviewSchedulerTest, LessonStoreTest |
| UC-12 | MasteryStoreTest |
| UC-13 | MasteryStoreTest |
| UC-14 | MasteryStoreTest |
| UC-15 | MasteryStoreTest |
| UC-16 | DailySessionHelperTest |
| UC-17 | BossHelperTest, MasteryStoreTest, ProgressStoreTest |
| UC-18 | BossHelperTest, ProgressStoreTest |
| UC-19 | BossHelperTest |
| UC-20 | ProgressStoreTest |
| UC-21 | DailySessionHelperTest, DailySessionComposerTest |
| UC-22 | DailySessionHelperTest |
| UC-23 | DailySessionHelperTest, ProgressStoreTest |
| UC-24 | ProgressStoreTest |
| UC-25 | WordMasteryStoreTest, DailySessionComposerTest |
| UC-26 | VerbDrillStoreTest, LessonStoreTest |
| UC-27 | NormalizerTest, VerbDrillStoreTest |
| UC-28 | VerbDrillStoreTest, AtomicFileWriterTest |
| UC-29 | VerbDrillStoreTest, LessonStoreTest |
| UC-30 | WordMasteryStoreTest, LessonStoreTest |
| UC-31 | WordMasteryStoreTest |
| UC-32 | WordMasteryStoreTest, SpacedRepetitionConfigTest |
| UC-33 | NormalizerTest |
| UC-34 | WordMasteryStoreTest, LessonStoreTest |
| UC-35 | (integration -- GrammarMateApp routing) |
| UC-36 | (integration -- GrammarMateApp routing) |
| UC-37 | (integration -- GrammarMateApp routing) |
| UC-38 | LessonStoreTest |
| UC-39 | LessonStoreTest, CsvParserTest |
| UC-40 | LessonStoreTest |
| UC-41 | LessonStoreTest |
| UC-42 | LessonStoreTest |
| UC-43 | BackupManagerTest, AtomicFileWriterTest |
| UC-44 | BackupManagerTest |
| UC-45 | ProgressStoreTest |
| UC-46 | LessonStoreTest, ProfileStoreTest |
| UC-47 | ProgressStoreTest, MasteryStoreTest, AtomicFileWriterTest |
| UC-48 | NormalizerTest |
| UC-49 | NormalizerTest, ProgressStoreTest |
| UC-50 | NormalizerTest, VerbDrillStoreTest |

---

## STRATEGY

### Priorities:
1. **P0 (Critical)** - IMMEDIATELY (blocks release)
2. **P1 (Important)** - Within sprint
3. **P2 (Desirable)** - Next sprint

### Test types:
- **Unit tests** - isolated class testing
- **Integration tests** - component chain testing
- **Property-based tests** - property and invariant testing

---

## P0: CRITICAL FUNCTIONALITY

### 1. SpacedRepetitionConfigTest.kt [NEW]
**Priority:** P0 - CRITICAL
**File:** `app/src/test/java/com/alexpo/grammermate/data/SpacedRepetitionConfigTest.kt`
**Goal:** Protect Ebbinghaus forgetting curve algorithm

#### Tests to write:

##### 1.1 Memory stability calculation
```kotlin
@Test fun calculateStability_firstStep_returnsBaseStability()
@Test fun calculateStability_negativeIndex_returnsBaseStability()
@Test fun calculateStability_secondStep_returnsMultipliedStability()
@Test fun calculateStability_maxStep_returnsCorrectStability()
```
**Covered requirements:** FR-9.1.2, FR-9.1.3, FR-9.1.4

##### 1.2 Retention calculation
```kotlin
@Test fun calculateRetention_zeroDays_returns100Percent()
@Test fun calculateRetention_oneDayFirstStep_returnsExpectedRetention()
@Test fun calculateRetention_longTime_approachesZero()
@Test fun calculateRetention_neverExceedsOne()
```
**Covered requirements:** FR-9.1.1

##### 1.3 Flower health calculation
```kotlin
@Test fun calculateHealthPercent_zeroDays_returns100Percent()
@Test fun calculateHealthPercent_withinInterval_returns100Percent()
@Test fun calculateHealthPercent_overdueDecays_from100to50Percent()
@Test fun calculateHealthPercent_ninetyDays_returnsZero()
@Test fun calculateHealthPercent_neverBelowWiltedThreshold()
@Test fun calculateHealthPercent_exponentialDecayFormula()
```
**Covered requirements:** FR-9.3.1, FR-9.3.2, FR-9.3.3, FR-9.3.4, FR-9.3.5

##### 1.4 Interval ladder
```kotlin
@Test fun nextIntervalStep_onTime_advancesStep()
@Test fun nextIntervalStep_late_keepsCurrentStep()
@Test fun nextIntervalStep_maxStep_staysAtMax()
@Test fun wasRepetitionOnTime_withinInterval_returnsTrue()
@Test fun wasRepetitionOnTime_overdue_returnsFalse()
@Test fun intervalLadderDays_hasCorrectValues()
```
**Covered requirements:** FR-9.2.1, FR-9.2.2, FR-9.2.3, FR-9.2.4

##### 1.5 Constants and thresholds
```kotlin
@Test fun constants_masteryThreshold_equals150()
@Test fun constants_wiltedThreshold_equals50Percent()
@Test fun constants_goneThresholdDays_equals90()
@Test fun constants_baseStability_isPositive()
@Test fun constants_stabilityMultiplier_greaterThanOne()
```
**Covered requirements:** FR-9.1.2, FR-9.1.3, FR-9.3.4, FR-9.3.5

**Success metrics:**
- All constants covered
- All formulas tested with boundary values
- Property-based tests for monotonic decay

---

### 2. FlowerCalculatorTest.kt [NEW]
**Priority:** P0 - CRITICAL
**File:** `app/src/test/java/com/alexpo/grammermate/data/FlowerCalculatorTest.kt`
**Goal:** Protect flower state calculation (progress visualization)

#### Tests to write:

##### 2.1 Basic states
```kotlin
@Test fun calculate_nullMastery_returnsSeedState()
@Test fun calculate_zeroShows_returnsSeedState()
@Test fun calculate_moreThan90Days_returnsGoneState()
```
**Covered requirements:** FR-8.3.5, FR-8.1.2, FR-8.1.7

##### 2.2 State determination by mastery percent
```kotlin
@Test fun calculate_0to33PercentMastery_returnsSeed()
@Test fun calculate_33to66PercentMastery_returnsSprout()
@Test fun calculate_66to100PercentMastery_returnsBloom()
```
**Covered requirements:** FR-8.1.2, FR-8.1.3, FR-8.1.4

##### 2.3 Wilting by health
```kotlin
@Test fun calculate_healthBelow100Percent_returnsWilting()
@Test fun calculate_healthBelowWiltedThreshold_returnsWilted()
@Test fun calculate_wiltingOverridesBloomState()
```
**Covered requirements:** FR-8.1.5, FR-8.1.6

##### 2.4 Mastery percent calculation
```kotlin
@Test fun calculate_50Shows_returns33PercentMastery()
@Test fun calculate_150Shows_returns100PercentMastery()
@Test fun calculate_200Shows_capsAt100PercentMastery()
```
**Covered requirements:** FR-8.3.1, FR-8.3.2

##### 2.5 Flower scale
```kotlin
@Test fun calculate_scaleMultiplier_neverBelow50Percent()
@Test fun calculate_scaleMultiplier_maxIs100Percent()
@Test fun calculate_scaleMultiplier_isMasteryTimesHealth()
```
**Covered requirements:** FR-8.3.4

##### 2.6 Emoji representation
```kotlin
@Test fun getEmoji_returnsCorrectEmojiForEachState()
@Test fun getEmojiWithScale_returnsPairWithScale()
```
**Covered requirements:** (visualization)

##### 2.7 Edge cases
```kotlin
@Test fun calculate_exactly150Shows_returns100PercentMastery()
@Test fun calculate_exactly90Days_beforeGone()
@Test fun calculate_exactly91Days_isGone()
@Test fun calculate_negativeTimestamp_treatedAsZeroDays()
```

**Success metrics:**
- All 7 flower states tested
- Boundary values (0, 50, 100, 150, 90 days)
- Correct scale calculation

---

### 3. MasteryStoreTest.kt [NEW]
**Priority:** P0 - CRITICAL
**File:** `app/src/test/java/com/alexpo/grammermate/data/MasteryStoreTest.kt`
**Goal:** Protect mastery progress persistence

#### Tests to write:

##### 3.1 Save and load
```kotlin
@Test fun saveMastery_newState_writesToFile()
@Test fun loadMastery_existingFile_returnsCorrectState()
@Test fun loadMastery_missingFile_returnsEmptyList()
@Test fun loadMastery_corruptedFile_returnsEmptyList()
```
**Covered requirements:** FR-8.5.1, FR-8.5.3, FR-8.5.4

##### 3.2 Caching
```kotlin
@Test fun loadMastery_calledTwice_usesCache()
@Test fun saveMastery_updatesCache()
@Test fun invalidateCache_forcesReload()
```
**Covered requirements:** FR-8.5.2

##### 3.3 Card show recording
```kotlin
@Test fun recordCardShow_firstTime_increasesUniqueShows()
@Test fun recordCardShow_secondTime_doesNotIncreaseUniqueShows()
@Test fun recordCardShow_alwaysIncreasesTotalShows()
@Test fun recordCardShow_updatesLastShowDate()
@Test fun recordCardShow_addsCardIdToSet()
@Test fun recordCardShow_updatesIntervalStepOnTime()
@Test fun recordCardShow_keepsIntervalStepWhenLate()
```
**Covered requirements:** FR-8.4.1, FR-8.4.2, FR-8.4.3, FR-8.4.4, FR-8.4.5, FR-8.4.6

##### 3.4 Multiple lessons
```kotlin
@Test fun saveMastery_multipleLanguages_separatesCorrectly()
@Test fun loadMastery_specificLesson_returnsOnlyThatLesson()
@Test fun saveMastery_preservesOtherLessons()
```
**Covered requirements:** FR-8.5.3

##### 3.5 Schema versioning
```kotlin
@Test fun saveMastery_includesSchemaVersion()
@Test fun loadMastery_oldSchemaVersion_migrates()
```
**Covered requirements:** FR-2.2.1, FR-2.2.2

##### 3.6 Atomic writes
```kotlin
@Test fun saveMastery_usesAtomicWrite()
@Test fun saveMastery_failureDoesNotCorruptFile()
```
**Covered requirements:** FR-2.1.1

**Success metrics:**
- Save/load works correctly
- Cache works and invalidates properly
- All metrics (uniqueCardShows, totalCardShows, lastShowDateMs) update

---

### 4. ProgressStoreTest.kt [NEW]
**Priority:** P0 - CRITICAL
**File:** `app/src/test/java/com/alexpo/grammermate/data/ProgressStoreTest.kt`
**Goal:** Protect training progress persistence

#### Tests to write:

##### 4.1 Persist all progress fields
```kotlin
@Test fun saveProgress_languageId_persists()
@Test fun saveProgress_trainingMode_persists()
@Test fun saveProgress_lessonId_persists()
@Test fun saveProgress_currentIndex_persists()
@Test fun saveProgress_correctWrongCounts_persist()
@Test fun saveProgress_activeTimeMs_persists()
@Test fun saveProgress_sessionState_persists()
@Test fun saveProgress_bossRewards_persist()
@Test fun saveProgress_voiceMetrics_persist()
@Test fun saveProgress_eliteProgress_persists()
```
**Covered requirements:** FR-7.1.1 - FR-7.1.10

##### 4.2 Load progress
```kotlin
@Test fun loadProgress_existingFile_returnsCorrectProgress()
@Test fun loadProgress_missingFile_returnsDefaultProgress()
@Test fun loadProgress_corruptedFile_returnsDefaultProgress()
```
**Covered requirements:** FR-7.2.1, FR-7.2.2

##### 4.3 Autosave
```kotlin
@Test fun saveProgress_writesImmediately()
@Test fun saveProgress_multipleCallsConcurrent_lastWins()
```
**Covered requirements:** FR-7.3.1, FR-7.3.2

##### 4.4 Training modes
```kotlin
@Test fun saveProgress_lessonMode_persists()
@Test fun saveProgress_allSequentialMode_persists()
@Test fun saveProgress_allMixedMode_persists()
```
**Covered requirements:** FR-5.1.1, FR-5.1.2, FR-5.1.3

##### 4.4.1 ALL_MIXED mode - 300 card limit
```kotlin
@Test fun allMixedMode_moreThan300Cards_selects300Random()
@Test fun allMixedMode_exactly300Cards_selectsAll()
@Test fun allMixedMode_lessThan300Cards_selectsAll()
@Test fun allMixedMode_randomSelection_isDifferentEachTime()
@Test fun allMixedMode_neverExceeds300Cards()
```
**Covered requirements:** FR-5.1.4, FR-5.1.5, FR-5.1.6

##### 4.5 Session states
```kotlin
@Test fun saveProgress_activeState_persists()
@Test fun saveProgress_pausedState_persists()
@Test fun saveProgress_afterCheckState_persists()
@Test fun saveProgress_hintShownState_persists()
```
**Covered requirements:** FR-5.2.1, FR-5.2.2, FR-5.2.3, FR-5.2.4

##### 4.6 Boss rewards
```kotlin
@Test fun saveProgress_lessonBossRewards_persist()
@Test fun saveProgress_megaBossReward_persists()
@Test fun saveProgress_multipleLessonRewards_persist()
```
**Covered requirements:** FR-10.3.2, FR-10.4.2

##### 4.7 Elite mode
```kotlin
@Test fun saveProgress_eliteStepIndex_persists()
@Test fun saveProgress_eliteBestSpeeds_persist()
@Test fun saveProgress_eliteBestSpeeds_multipleSteps()
```
**Covered requirements:** FR-10.5.5

**Success metrics:**
- All 10 progress fields correctly persist and load
- Default values are correct
- Error handling does not lose data

---

### 5. NormalizerTest.kt [NEW]
**Priority:** P0 - CRITICAL
**File:** `app/src/test/java/com/alexpo/grammermate/data/NormalizerTest.kt`
**Goal:** Protect user answer validation

#### Tests to write:

##### 5.1 Whitespace removal
```kotlin
@Test fun normalize_multipleSpaces_becomesOne()
@Test fun normalize_leadingTrailingSpaces_removed()
@Test fun normalize_tabsAndNewlines_becomeSpaces()
```
**Covered requirements:** FR-6.1.1, FR-6.1.6

##### 5.2 Case
```kotlin
@Test fun normalize_upperCase_becomesLowerCase()
@Test fun normalize_mixedCase_becomesLowerCase()
```
**Covered requirements:** FR-6.1.2

##### 5.3 Punctuation
```kotlin
@Test fun normalize_period_removed()
@Test fun normalize_comma_removed()
@Test fun normalize_questionMark_removed()
@Test fun normalize_exclamationMark_removed()
@Test fun normalize_colon_removed()
@Test fun normalize_semicolon_removed()
@Test fun normalize_quotes_removed()
@Test fun normalize_brackets_removed()
@Test fun normalize_hyphen_preserved()
```
**Covered requirements:** FR-6.1.3, FR-6.1.4

##### 5.4 Time
```kotlin
@Test fun normalize_timeThreeColon00_becomesThree()
@Test fun normalize_timeTwelveColon30_becomesTwelve()
@Test fun normalize_timeSingleDigit_preserved()
```
**Covered requirements:** FR-6.1.5

##### 5.5 Complex cases
```kotlin
@Test fun normalize_realUserAnswer_matchesExpected()
@Test fun normalize_multipleTransformations_appliedCorrectly()
@Test fun normalize_emptyString_returnsEmpty()
@Test fun normalize_onlyPunctuation_returnsEmpty()
```
**Covered requirements:** FR-6.2.1

##### 5.6 Edge cases
```kotlin
@Test fun normalize_unicodeCharacters_preserved()
@Test fun normalize_apostropheInContraction_handled()
@Test fun normalize_multipleDashes_preserved()
```

**Success metrics:**
- All punctuation types tested
- Boundary cases (empty string, punctuation only)
- Real user answer examples

---

### 6. ProfileStoreTest.kt [NEW]
**Priority:** P0 - CRITICAL
**File:** `app/src/test/java/com/alexpo/grammermate/data/ProfileStoreTest.kt`
**Goal:** Protect user profile persistence

#### Tests to write:

##### 6.1 Save and load
```kotlin
@Test fun saveProfile_userName_persists()
@Test fun loadProfile_existingFile_returnsCorrectName()
@Test fun loadProfile_missingFile_returnsDefaultName()
```
**Covered requirements:** FR-1.1.1, FR-1.1.2, FR-1.1.4

##### 6.2 YAML format
```kotlin
@Test fun saveProfile_createsYamlFile()
@Test fun loadProfile_readsYamlFile()
@Test fun saveProfile_includesSchemaVersion()
```
**Covered requirements:** FR-1.1.3, FR-2.2.1

##### 6.3 Edge cases
```kotlin
@Test fun saveProfile_emptyName_handled()
@Test fun saveProfile_specialCharacters_handled()
@Test fun saveProfile_unicodeCharacters_handled()
```

**Success metrics:**
- Save/load works
- Default name is correct
- Edge case handling

---

## P1: IMPORTANT FUNCTIONALITY

### 7. LessonStoreTest.kt [NEW]
**Priority:** P1 - IMPORTANT
**File:** `app/src/test/java/com/alexpo/grammermate/data/LessonStoreTest.kt`
**Goal:** Protect lesson management

#### Tests to write:

##### 7.1 Language management
```kotlin
@Test fun getLanguages_returnsAllLanguages()
@Test fun addLanguage_addsToList()
@Test fun removeLanguage_removesFromList()
```
**Covered requirements:** FR-3.1.1, FR-3.1.2, FR-3.1.3

##### 7.2 ZIP pack import
```kotlin
@Test fun importPackFromUri_validZip_extractsLessons()
@Test fun importPackFromUri_withManifest_readsManifest()
@Test fun importPackFromAssets_defaultPacks_imports()
@Test fun updateDefaultPacksIfNeeded_newVersion_updates()
@Test fun updateDefaultPacksIfNeeded_sameVersion_skips()
```
**Covered requirements:** FR-3.2.1.1, FR-3.2.1.2, FR-3.2.1.3, FR-3.2.1.4

##### 7.3 CSV import
```kotlin
@Test fun importFromUri_validCsv_createsLesson()
@Test fun importFromUri_withBom_handlesBom()
@Test fun importFromUri_invalidCsv_handlesError()
```
**Covered requirements:** FR-3.2.2.1, FR-3.2.2.2, FR-3.2.2.3, FR-3.2.2.4

##### 7.4 Lesson deletion
```kotlin
@Test fun deleteLesson_removesLesson()
@Test fun deleteLesson_removesRelatedFiles()
@Test fun deleteAllLessons_removesAllForLanguage()
@Test fun deletePack_removesPack()
```
**Covered requirements:** FR-3.3.1, FR-3.3.2, FR-3.3.3, FR-3.3.4

##### 7.5 Lesson structure
```kotlin
@Test fun lesson_first150Cards_isMainPool()
@Test fun lesson_after150Cards_isReservePool()
@Test fun lesson_mainPoolCards_returns150()
@Test fun lesson_reservePoolCards_returnsRest()
```
**Covered requirements:** FR-3.4.1, FR-3.4.2, FR-3.4.3, FR-3.4.4

##### 7.6 Reading lessons
```kotlin
@Test fun getLessons_returnsAllLessonsForLanguage()
@Test fun getLesson_returnsSpecificLesson()
@Test fun getLesson_missingLesson_returnsNull()
```

**Success metrics:**
- ZIP and CSV import works
- Deletion does not break data structure
- Main/reserve pools are correct

---

### 8. StreakStoreTest.kt [NEW]
**Priority:** P1 - IMPORTANT
**File:** `app/src/test/java/com/alexpo/grammermate/data/StreakStoreTest.kt`
**Goal:** Protect streak system

#### Tests to write:

##### 8.1 Save and load
```kotlin
@Test fun saveStreak_persists()
@Test fun loadStreak_returnsCorrectData()
@Test fun loadStreak_missingFile_returnsDefault()
```
**Covered requirements:** FR-13.4.1, FR-13.4.2

##### 8.2 Streak update
```kotlin
@Test fun updateStreak_sameDay_doesNotIncrease()
@Test fun updateStreak_nextDay_increasesStreak()
@Test fun updateStreak_skippedDay_resetsStreak()
@Test fun updateStreak_updatesLongestStreak()
@Test fun updateStreak_incrementsTotalDays()
```
**Covered requirements:** FR-13.2.1, FR-13.2.2, FR-13.2.3, FR-13.2.4

##### 8.3 Edge cases
```kotlin
@Test fun updateStreak_midnight_handlesCorrectly()
@Test fun updateStreak_timezone_handlesCorrectly()
@Test fun updateStreak_firstEverActivity_setsStreak1()
```

**Success metrics:**
- Streak counting logic is correct
- Edge cases (midnight, first day) handled
- Longest streak updates properly

---

### 9. CsvParserTest.kt [IMPROVEMENT]
**Priority:** P1 - IMPORTANT
**File:** `app/src/test/java/com/alexpo/grammermate/data/CsvParserTest.kt`
**Goal:** Supplement existing tests

#### Tests to add:

```kotlin
@Test fun parseLesson_multipleAcceptedAnswers_splitsByPlus()
@Test fun parseLesson_emptyLines_ignored()
@Test fun parseLesson_lineWithoutSeparator_ignored()
@Test fun parseLesson_extraFields_ignored()
@Test fun parseLesson_missingFields_handlesError()
```
**Covered requirements:** FR-3.2.3.3, FR-3.2.3.4

**Success metrics:**
- 90% -> 100% CsvParser coverage
- All edge cases handled

---

### 10. AtomicFileWriterTest.kt [IMPROVEMENT]
**Priority:** P1 - IMPORTANT
**File:** `app/src/test/java/com/alexpo/grammermate/data/AtomicFileWriterTest.kt`
**Goal:** Supplement existing tests

#### Tests to add:

```kotlin
@Test fun writeText_createsTempFile()
@Test fun writeText_renamesTempToTarget()
@Test fun writeText_onError_deletesTempFile()
@Test fun writeText_concurrent_handlesCorrectly()
@Test fun writeText_existingFile_replacesAtomically()
```
**Covered requirements:** FR-2.1.2, FR-2.1.3, FR-2.1.4

**Success metrics:**
- 40% -> 100% AtomicFileWriter coverage
- Error handling tested
- Concurrent writes tested

---

## P2: DESIRABLE FUNCTIONALITY

### 11. BackupManagerTest.kt [NEW]
**Priority:** P2 - DESIRABLE
**File:** `app/src/test/java/com/alexpo/grammermate/data/BackupManagerTest.kt`
**Goal:** Protect backups

#### Tests to write:

```kotlin
@Test fun createBackup_writesToBackupLatestWithProgressAndProfile()
@Test fun getAvailableBackups_includesBackupLatest()
@Test fun restoreFromBackup_createsInternalDirAndCopiesFiles()
```
**Covered requirements:** FR-2.3.1, FR-2.3.2, FR-2.3.3, FR-2.3.4

---

### 12. AppConfigStoreTest.kt [NEW]
**Priority:** P2 - DESIRABLE
**File:** `app/src/test/java/com/alexpo/grammermate/data/AppConfigStoreTest.kt`
**Goal:** Protect app settings

#### Tests to write:

```kotlin
@Test fun saveConfig_subLessonSize_persists()
@Test fun loadConfig_returnsCorrectConfig()
@Test fun saveConfig_voiceSettings_persist()
```
**Covered requirements:** FR-14.1.1, FR-14.1.2, FR-14.1.3, FR-14.1.4

---

### 13. VocabCsvParserTest.kt [IMPROVEMENT]
**Priority:** P2 - DESIRABLE
**File:** `app/src/test/java/com/alexpo/grammermate/data/VocabCsvParserTest.kt`
**Goal:** Supplement existing tests

#### Tests to add:

```kotlin
@Test fun parse_emptyLines_ignored()
@Test fun parse_invalidFormat_handlesError()
@Test fun parse_missingHardFlag_defaultsFalse()
```
**Covered requirements:** FR-11.3.3

---

## INTEGRATION TESTS

### 14. MasteryIntegrationTest.kt [NEW]
**Priority:** P0 - CRITICAL
**File:** `app/src/test/java/com/alexpo/grammermate/integration/MasteryIntegrationTest.kt`
**Goal:** Test MasteryStore + FlowerCalculator + SpacedRepetitionConfig chain

#### Tests to write:

```kotlin
@Test fun userCompletesLesson_masteryGrows_flowerBlooms()
@Test fun userSkipsDays_flowerWilts()
@Test fun userReturnsAfter90Days_flowerGone()
@Test fun userRepeatsOnTime_intervalAdvances()
@Test fun userRepeatsLate_intervalStaysn()
@Test fun userReaches150Shows_achieves100PercentMastery()
```

**Success metrics:**
- Full flower lifecycle tested
- Intervals work correctly
- Save/load does not break state

---

### 15. ProgressIntegrationTest.kt [NEW]
**Priority:** P1 - IMPORTANT
**File:** `app/src/test/java/com/alexpo/grammermate/integration/ProgressIntegrationTest.kt`
**Goal:** Test ProgressStore + TrainingViewModel chain

#### Tests to write:

```kotlin
@Test fun userStartsLesson_progressSaves()
@Test fun userPausesLesson_stateSaves()
@Test fun userCompletesLesson_statisticsSave()
@Test fun userSwitchesMode_progressTransitions()
```

#### Tests for ALL_MIXED mode:

```kotlin
@Test fun userStartsAllMixed_with500Cards_shows300Only()
@Test fun userStartsAllMixed_with200Cards_showsAll200()
@Test fun userStartsAllMixed_with300Cards_showsAll300()
@Test fun userRestartsAllMixed_getsNewRandomSelection()
```

**Success metrics:**
- Progress saves at every stage
- Mode switching does not break state
- 300 card limit works correctly in real scenarios

---

## PROPERTY-BASED TESTS

### 16. SpacedRepetitionPropertyTest.kt [NEW]
**Priority:** P1 - IMPORTANT
**File:** `app/src/test/java/com/alexpo/grammermate/property/SpacedRepetitionPropertyTest.kt`
**Goal:** Test algorithm invariants

#### Properties to test:

```kotlin
@Test fun property_healthNeverExceedsOne()
@Test fun property_healthNeverBelowWiltedThreshold()
@Test fun property_stabilityAlwaysIncreases()
@Test fun property_retentionMonotonicallyDecreases()
@Test fun property_intervalStepNeverDecreases()
```

**Success metrics:**
- All invariants hold for random inputs
- Boundary values do not break invariants

---

## SUMMARY TABLE

| # | Test file | Priority | New/Improvement | Requirements | Status |
|---|-----------|-----------|-----------------|------------|--------|
| 1 | SpacedRepetitionConfigTest | P0 | NEW | FR-9.* | TODO |
| 2 | FlowerCalculatorTest | P0 | NEW | FR-8.1.*, FR-8.3.* | TODO |
| 3 | MasteryStoreTest | P0 | NEW | FR-8.2.*, FR-8.4.*, FR-8.5.* | TODO |
| 4 | ProgressStoreTest | P0 | NEW | FR-7.* | TODO |
| 5 | NormalizerTest | P0 | NEW | FR-6.1.*, FR-6.2.* | TODO |
| 6 | ProfileStoreTest | P0 | NEW | FR-1.1.* | TODO |
| 7 | LessonStoreTest | P1 | NEW | FR-3.* | TODO |
| 8 | StreakStoreTest | P1 | NEW | FR-13.* | TODO |
| 9 | CsvParserTest | P1 | IMPROVEMENT | FR-3.2.3.* | Partial |
| 10 | AtomicFileWriterTest | P1 | IMPROVEMENT | FR-2.1.* | Partial |
| 11 | BackupManagerTest | P2 | NEW | FR-2.3.* | TODO |
| 12 | AppConfigStoreTest | P2 | NEW | FR-14.1.* | TODO |
| 13 | VocabCsvParserTest | P2 | IMPROVEMENT | FR-11.3.* | Partial |
| 14 | MasteryIntegrationTest | P0 | NEW | Integration | TODO |
| 15 | ProgressIntegrationTest | P1 | NEW | Integration | TODO |
| 16 | SpacedRepetitionPropertyTest | P1 | NEW | Properties | TODO |

---

## EXECUTION ROADMAP

### Sprint 1 (P0 - CRITICAL)
**Goal:** Protect critical functionality

1. Week 1: SpacedRepetitionConfigTest + FlowerCalculatorTest
2. Week 2: MasteryStoreTest + ProgressStoreTest
3. Week 3: NormalizerTest + ProfileStoreTest
4. Week 4: MasteryIntegrationTest

**Expected coverage after sprint 1:** 50-60%

### Sprint 2 (P1 - IMPORTANT)
**Goal:** Cover important functionality

1. Week 1: LessonStoreTest
2. Week 2: StreakStoreTest
3. Week 3: CsvParserTest, AtomicFileWriterTest improvements
4. Week 4: ProgressIntegrationTest + SpacedRepetitionPropertyTest

**Expected coverage after sprint 2:** 75-80%

### Sprint 3 (P2 - DESIRABLE)
**Goal:** Cover remaining functionality

1. Week 1: BackupManagerTest
2. Week 2: AppConfigStoreTest
3. Week 3: VocabCsvParserTest improvement
4. Week 4: Additional integration tests

**Expected coverage after sprint 3:** 90%+

---

## SUCCESS METRICS

### Quantitative metrics:
- **Code coverage:** 80%+ for critical classes
- **Test count:** 200+ unit tests
- **Integration tests:** 10+ scenarios
- **Property-based tests:** 5+ properties

### Qualitative metrics:
- **Zero regressions** in critical functionality during feature development
- **Fast feedback** - tests run < 30 seconds
- **Clear errors** - tests give clear understanding of what broke
- **Maintainable tests** - easy to read and modify

---

## TEST INFRASTRUCTURE

### Required dependencies:
```gradle
testImplementation 'junit:junit:4.13.2'
testImplementation 'org.mockito:mockito-core:5.3.1'
testImplementation 'org.yaml:snakeyaml:2.0'
testImplementation 'io.kotest:kotest-property:5.6.2' // for property-based tests
```

### CI/CD integration:
- All tests run on every PR
- P0 tests block merge on failure
- Coverage reports generated automatically

### Agent rules:
1. **NEVER** modify existing tests without approval
2. **ALWAYS** run tests before committing changes
3. **MANDATORY** add tests for new functionality
4. On test failure - **FIRST** fix tests, **THEN** continue

---

## CONCLUSION

This test plan provides:
1. **Regression protection** - 90%+ critical functionality coverage
2. **Confidence in changes** - agents can safely add features
3. **Fast feedback** - tests catch issues before release
4. **Documentation** - tests show how code works

---

## NEW: Cyclic Sublessons (Cyclic Sublessons)

### MixedReviewSchedulerTest - Cyclic Sublessons [ADDED]
**Priority:** P0 - CRITICAL
**File:** `app/src/test/java/com/alexpo/grammermate/data/MixedReviewSchedulerTest.kt`
**Date added:** 2026-01-18
**Goal:** Test usage of all cards and cyclic display

#### New tests:

##### 1. Using all cards
```kotlin
@Test fun build_usesAllCardsNotJustFirst150()
```
**Description:** Verifies system uses all 300 cards, not just first 150
**Covered requirements:** FR-3.4.5
**Expected result:** With a lesson of 300 cards, all cards are used in sub-lessons

##### 2. Creating cyclic sub-lessons
```kotlin
@Test fun build_createsCyclicSublessons()
```
**Description:** Verifies sub-lessons are created in cycles of 15
**Covered requirements:** FR-3.5.1, FR-3.5.2
**Expected result:** 200 cards create 20 sub-lessons (15 in first cycle, 5 in second)

##### 3. Handling fewer than 15 sub-lessons
```kotlin
@Test fun build_handlesLessThan15Sublessons()
```
**Description:** Verifies correct handling of lessons with few cards
**Covered requirements:** FR-3.5.7
**Expected result:** 80 cards create 8 sub-lessons (not padded to 15)

**Success metrics:**
- All cards are used (not just first 150)
- Sub-lessons created correctly regardless of card count
- Cycles of 15 form correctly

---

**Next steps:**
1. Start with P0 tests (SpacedRepetitionConfigTest, FlowerCalculatorTest)
2. Gradually cover remaining components
3. Integrate tests into CI/CD pipeline
4. Train agents to run tests before commits
5. Run new tests for cyclic sub-lessons
