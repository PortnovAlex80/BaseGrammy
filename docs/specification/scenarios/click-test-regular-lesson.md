# Regular Lesson Click Test Scenario

> **Purpose:** Comprehensive step-by-step click test for normal lesson training flow
> **Coverage:** Entry through Session, Sub-lesson types, Input modes, Answer validation, Completion, Boss trigger, Bad sentences, Mastery counting, UI elements, Exit
> **Test Environment:** Android emulator or physical device with APK installed

---

## Preconditions

| Item | Requirement |
|------|-------------|
| App State | Fresh installation or progress reset for the lesson |
| Lesson | At least one lesson unlocked (not LOCKED state) |
| Content | Lesson has >= 10 cards (default sub-lesson size) |
| Audio | TTS model downloaded for test language (optional, for voice mode) |
| Network | Not required (offline capable after initial setup) |

---

## Test Steps

### Phase 1: Entry (Home -> Lesson Roadmap -> Sub-lesson Selection)

#### Step 1.1: Launch App
1. Launch GrammarMate app
2. **Verify:** HomeScreen renders with lesson tiles visible
3. **Verify:** Flower states shown (SEED/SPROUT/BLOOM) for each lesson

#### Step 1.2: Tap Lesson Tile
1. Locate an unlocked lesson tile (not LOCKED state)
2. Tap the lesson tile
3. **Verify:** Navigation to Lesson Roadmap screen
4. **Verify:** Roadmap shows sub-lesson tiles (typically 15+ tiles)
5. **Verify:** Sub-lesson states visible (LOCKED/UNLOCKED/COMPLETED)

#### Step 1.3: Select First Sub-lesson
1. Tap "Start" on the first UNLOCKED sub-lesson (or first available if starting fresh)
2. **Verify:** Navigation to TrainingScreen
3. **Verify:** Session state is PAUSED (not ACTIVE yet)
4. **Verify:** Play button visible and enabled

---

### Phase 2: Session Start and Card Display

#### Step 2.1: Start Session
1. Tap Play button
2. **Verify:** Session state changes to ACTIVE
3. **Verify:** First card displays with Russian prompt text
4. **Verify:** Counter shows "1 / [subLessonTotal]" (typically "1 / 10")
5. **Verify:** Input mode indicator shows VOICE (default)

#### Step 2.2: Card Elements Verification
1. **Verify:** Russian prompt text is visible
2. **Verify:** TTS speaker icon is visible (tap to hear pronunciation)
3. **Verify:** Input field is visible and empty
4. **Verify:** Submit button is visible
5. **Verify:** Pause button is visible (replaces Play during ACTIVE)

---

### Phase 3: Input Modes Testing

#### Step 3.1: Voice Mode (Default)
1. Verify microphone icon is active in input mode bar
2. **Verify:** If `voiceAutoStart` is enabled, speech recognition should launch automatically
3. Speak answer clearly
4. **Verify:** Recognized text appears in input field
5. **Verify:** Submit button becomes enabled

#### Step 3.2: Keyboard Mode
1. Tap keyboard icon in input mode bar
2. **Verify:** Input mode changes to KEYBOARD
3. **Verify:** Microphone icon becomes inactive
4. Type answer using keyboard
5. **Verify:** Text appears in input field
6. **Verify:** Submit button enabled

#### Step 3.3: Word Bank Mode
1. Tap word bank icon in input mode bar
2. **Verify:** Input mode changes to WORD_BANK
3. **Verify:** Word bank appears below input area with 8-12 words
4. **Verify:** Words include correct answer + distractors
5. Tap words to build answer
6. **Verify:** Selected words appear in input field in order
7. Tap "X" or backspace to remove last word
8. **Verify:** Last word removed from input

---

### Phase 4: Answer Validation - Correct Answer

#### Step 4.1: Submit Correct Answer
1. Using any input mode, enter a correct answer
2. Tap Submit
3. **Verify:** Success sound plays
4. **Verify:** Green checkmark or success indicator appears
5. **Verify:** Card advances to next card automatically (after brief delay)
6. **Verify:** Counter increments ("2 / 10")
7. **Verify:** Correct count increments

#### Step 4.2: Continue Through Cards
1. Submit correct answers for cards 2-9
2. **Verify:** Each correct answer advances to next card
3. **Verify:** Counter increments with each card
4. **Verify:** No errors or crashes during rapid-fire correct answers

---

### Phase 5: Answer Validation - Incorrect Answer (3-Attempt Flow)

#### Step 5.1: Submit Incorrect Answer (Attempt 1)
1. Enter an incorrect answer
2. Tap Submit
3. **Verify:** Error sound plays
4. **Verify:** Red X or error indicator appears
5. **Verify:** incorrectCount increments
6. **Verify:** incorrectAttemptsForCard = 1
7. **Verify:** Hint is NOT shown yet
8. **Verify:** If VOICE mode, speech recognition re-triggers

#### Step 5.2: Submit Incorrect Answer (Attempt 2)
1. Enter another incorrect answer
2. Tap Submit
3. **Verify:** Error sound plays again
4. **Verify:** incorrectAttemptsForCard = 2
5. **Verify:** Hint is still NOT shown

#### Step 5.3: Submit Incorrect Answer (Attempt 3 - Trigger Hint)
1. Enter a third incorrect answer
2. Tap Submit
3. **Verify:** Error sound plays
4. **Verify:** incorrectAttemptsForCard resets to 0
5. **Verify:** Hint appears showing all accepted answers (separated by " / ")
6. **Verify:** Session state changes to HINT_SHOWN
7. **Verify:** Timer pauses
8. **Verify:** Card does NOT auto-advance

#### Step 5.4: Advance After Hint
1. Tap Next button (manual advance required after hint)
2. **Verify:** Next card loads
3. **Verify:** Timer resumes
4. **Verify:** Session state returns to ACTIVE

---

### Phase 6: Sub-Lesson Completion

#### Step 6.1: Complete Last Card in Sub-lesson
1. Navigate to the last card (card 10 of 10)
2. Submit correct answer
3. **Verify:** Sub-lesson completion UI appears
4. **Verify:** Summary shows correct/incorrect counts
5. **Verify:** Flower state update animation (if applicable)
6. **Verify:** Session returns to PAUSED state
7. **Verify:** Next sub-lesson cards are pre-loaded

#### Step 6.2: Verify Sub-Lesson Progress
1. Tap Back to return to Lesson Roadmap
2. **Verify:** Completed sub-lesson tile shows COMPLETED state
3. **Verify:** Next sub-lesson is now UNLOCKED (was previously LOCKED)
4. **Verify:** completedSubLessonCount incremented

---

### Phase 7: NEW_ONLY vs MIXED Sub-lesson Types

#### Step 7.1: Test NEW_ONLY Sub-lesson
1. Select a NEW_ONLY sub-lesson (early sub-lessons)
2. Start session
3. **Verify:** All cards are from the current lesson
4. **Verify:** No review cards from previous lessons appear
5. Complete at least 5 cards

#### Step 7.2: Test MIXED Sub-lesson (if available)
1. Select a MIXED sub-lesson (later sub-lessons)
2. Start session
3. **Verify:** Cards include current lesson content
4. **Verify:** Cards include review content from previous lessons
5. **Verify:** Distribution is approximately 50/50 (5 current, 5 review)

---

### Phase 8: Boss Battle Trigger

#### Step 8.1: Complete 15 Sub-lessons
1. Complete sub-lessons 1-15 (can use test mode to accelerate)
2. After completing 15th sub-lesson:
3. **Verify:** Boss battle becomes available
4. **Verify:** Boss challenge UI appears or is accessible from roadmap
5. **Verify:** Lesson marked as completed (completedAtMs set)

#### Step 8.2: Enter Boss Battle
1. Tap Boss battle entry
2. **Verify:** Boss battle session starts
3. **Verify:** Special boss UI is visible (progress bar, reward indicators)
4. **Verify:** No hints available (HARD difficulty enforced)
5. Complete boss battle or exit

---

### Phase 9: Bad Sentences Reporting

#### Step 9.1: Flag Bad Sentence
1. During any training session, navigate to a card
2. Tap "Report" or "Flag" button (if available in UI)
3. **Verify:** Confirmation dialog appears
4. Confirm the flag
5. **Verify:** badSentenceCount increments
6. **Verify:** Card marked in badSentenceStore

#### Step 9.2: Unflag Bad Sentence
1. Navigate to same flagged card
2. Tap "Unflag" option
3. **Verify:** badSentenceCount decrements
4. **Verify:** Card removed from badSentenceStore

---

### Phase 10: Mastery Counting (uniqueCardShows)

#### Step 10.1: Test VOICE Mode Mastery
1. Ensure input mode is VOICE
2. Complete 3 cards with correct answers via voice input
3. Exit session
4. Navigate to same lesson again
5. **Verify:** uniqueCardShows incremented for those 3 cards
6. **Verify:** shownCardIds includes those card IDs

#### Step 10.2: Test WORD_BANK Mode (No Mastery)
1. Change input mode to WORD_BANK
2. Complete 3 cards with correct answers via word bank
3. Exit session
4. Check mastery data
5. **Verify:** uniqueCardShows NOT incremented (WORD_BANK doesn't count)
6. **Verify:** Cards marked via markSubLessonCardsShown for progress tracking only

---

### Phase 11: UI Elements Interaction

#### Step 11.1: Play/Pause Toggle
1. Start session (ACTIVE state)
2. Tap Pause button
3. **Verify:** Session state changes to PAUSED
4. **Verify:** Timer stops
5. **Verify:** Play button replaces Pause button
6. Tap Play button
7. **Verify:** Session state returns to ACTIVE
8. **Verify:** Timer resumes

#### Step 11.2: Next/Prev Navigation
1. Navigate to card 5 of 10
2. Tap Next button
3. **Verify:** Advances to card 6
4. **Verify:** Session changes to PAUSED (per DP-02)
5. Tap Prev button
6. **Verify:** Returns to card 5
7. **Verify:** Session remains PAUSED

#### Step 11.3: TTS (Text-to-Speech)
1. Tap speaker icon on card
2. **Verify:** Target language text is spoken aloud
3. **Verify:** TTS state changes to SPEAKING then back to IDLE
4. Tap speaker icon again while speaking
5. **Verify:** Speech stops (stopTts called)

#### Step 11.4: Font Scaling
1. Navigate to Settings
2. Adjust Russian text scale (ruTextScale)
3. Return to training
4. **Verify:** Russian prompt text reflects new scale

---

### Phase 12: Exit and Completion

#### Step 12.1: Exit Mid-Session
1. Start a session and complete 3 cards
2. Tap Back or Exit button
3. **Verify:** Confirmation dialog appears ("Exit session?")
4. Confirm exit
5. **Verify:** Progress is saved (correctCount, currentIndex)
6. **Verify:** Return to Home or Roadmap

#### Step 12.2: Resume Session
1. Navigate back to the same lesson
2. Tap the sub-lesson tile
3. **Verify:** Session resumes at saved position (card 4)
4. **Verify:** correctCount preserved
5. **Verify:** Session state is PAUSED (user must tap Play)

#### Step 12.3: Complete Full Session
1. Complete all cards in a sub-lesson (10/10)
2. **Verify:** Sub-lesson completion summary shown
3. Tap "Continue" or "Next Lesson"
4. **Verify:** Next sub-lesson loads automatically
5. **Verify:** Session state is PAUSED ready for next block

---

## Verification Points Summary

| Category | Check | Expected Result |
|----------|-------|-----------------|
| **Entry** | Home -> Lesson Roadmap navigation | Successful navigation, tiles render correctly |
| **Selection** | Sub-lesson selection | Session starts in PAUSED state |
| **Display** | Card elements render | Russian text, TTS icon, input field, submit button |
| **Input Modes** | Voice auto-start | Speech recognition launches on new card (if enabled) |
| **Input Modes** | Keyboard input | Text entry works, submit enables |
| **Input Modes** | Word bank | Words selectable, X removes last, answer builds |
| **Validation** | Correct answer | Success sound, green check, auto-advance |
| **Validation** | Incorrect attempt 1 | Error sound, red X, no hint, retry allowed |
| **Validation** | Incorrect attempt 2 | Error sound, red X, no hint, retry allowed |
| **Validation** | Incorrect attempt 3 | Error sound, hint shown, timer pause, manual advance |
| **Progress** | Card counter | Increments with each card (1/10, 2/10, etc.) |
| **Progress** | Correct/Incorrect counts | Track accurately throughout session |
| **Completion** | Sub-lesson end | Summary screen, next sub-lesson unlocks |
| **Types** | NEW_ONLY sub-lesson | Only current lesson cards, no review |
| **Types** | MIXED sub-lesson | 50/50 current + review cards |
| **Boss** | After 15 sub-lessons | Boss battle unlocks, lesson marked complete |
| **Bad Sentences** | Flag card | badSentenceCount increments, stored |
| **Bad Sentences** | Unflag card | badSentenceCount decrements, removed |
| **Mastery** | VOICE/KEYBOARD | uniqueCardShows increments, counts for mastery |
| **Mastery** | WORD_BANK | uniqueCardShows NOT incremented (correct behavior) |
| **UI** | Play/Pause | Toggles ACTIVE/PAUSED, timer starts/stops |
| **UI** | Next/Prev | Advances/retreats card, changes to PAUSED |
| **UI** | TTS speaker | Speaks text, state changes SPEAKING->IDLE |
| **UI** | Font scale | Russian text reflects scale setting |
| **Exit** | Mid-session exit | Progress saved, return to roadmap |
| **Resume** | Re-enter lesson | Restores at saved position |
| **Persistence** | Progress save | YAML files updated atomically |

---

## Edge Cases to Test

| Edge Case | Test Steps | Expected Result |
|-----------|------------|-----------------|
| **Empty input** | Tap Submit with empty input field | Submit disabled or no-op (unless test mode) |
| **No cards** | Try to start lesson with 0 cards | Error message or graceful handling |
| **All cards hidden** | Hide all cards in sub-lesson | Session ends gracefully or shows message |
| **Network loss during TTS** | Disconnect network during TTS download | Download pauses, resumes on reconnect |
| **Rapid card skipping** | Tap Next rapidly without answering | Each card loads, PAUSED state maintained |
| **Voice timeout** | Don't speak in voice mode | Timeout handling, return to input |
| **Special characters** | Enter answer with punctuation | Normalization strips punctuation correctly |
| **Long answer** | Enter very long answer text | Truncation or scrolling, no crash |
| **Rotation** | Rotate device during session | State preserved, UI reflows correctly |
| **Backgrounding** | Press Home during session | Progress saved, resume on return |

---

## Test Data Requirements

| Data Type | Example | Purpose |
|-----------|---------|---------|
| Russian prompt | "я говорю по-английски" | Display to user |
| Accepted answers | "i speak english+speak english" | Normalized for validation |
| Review cards | From previous lessons | For MIXED sub-lessons |
| Bad sentence | Grammatically incorrect card | For reporting feature |
| Distractor words | Random words from lesson | For word bank generation |

---

## Success Criteria

A successful click test run should:
1. Complete all 12 phases without crashes
2. All verification points pass
3. No ANR (Application Not Responding) errors
4. Progress persists correctly across app restarts
5. Mastery counts reflect actual practice (VOICE/KEYBOARD only)
6. Boss battle triggers exactly after 15 sub-lessons
7. Bad sentence reporting works bidirectionally (flag/unflag)

---

## Related Specifications

- `08-training-viewmodel.md` - Complete TrainingViewModel method inventory
- `scenario-01-training-flow.md` - Normal lesson training flow code traces
- `03-algorithms-and-calculators.md` - Spaced repetition and flower calculation
- `22-use-case-registry.md` - UC-01 through UC-15 for training flows
- `23-screen-elements.md` - TrainingScreen element invariants

---

## Test Execution Notes

- **Automation:** This scenario can be automated via Maestro flows or mobile-mcp
- **Manual:** Recommended for first-time validation of new features
- **Frequency:** Run after any changes to TrainingViewModel, MasteryStore, or session flow
- **Duration:** Approximately 15-20 minutes for full manual execution
