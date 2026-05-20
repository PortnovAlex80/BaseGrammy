# Daily Practice Click Test Scenario

**Date:** 2026-05-20
**Branch:** develop
**Spec:** docs/specification/09-daily-practice.md, docs/specification/scenario-06-daily-practice.md
**Purpose:** Comprehensive step-by-step click test covering all Daily Practice flows

---

## Test Scope

This scenario covers the full Daily Practice user journey from entry through all three blocks (TRANSLATE, VOCAB, VERBS) to completion, including:
- Session initialization and state management
- Block 1: 10 translation cards with input mode rotation
- Block 2: 10 vocabulary flashcards with SRS rating
- Block 3: 10 verb conjugations with weak-first ordering
- Block transitions with sparkle overlays
- Streak/fire recording
- Bad sentence reporting
- All UI elements (buttons, TTS, font scaling)
- Exit and completion screens

---

## Preconditions

1. **App installed** on device/emulator with:
   - At least one lesson pack imported (e.g., Italian pack)
   - Pack has lesson content (sentences), vocab drill files, and verb drill files
   - Daily Practice tile visible on HomeScreen
2. **User state:**
   - Not currently in a session
   - `DailyCursorState.sentenceOffset` at a known position (e.g., 0 for first session)
   - `DailyCursorState.currentLessonIndex` at a known value (e.g., 0 for first lesson)
3. **Settings (for full coverage):**
   - TTS model downloaded (or test with download flow)
   - ASR model downloaded (or test with Google fallback)
   - Font scale at default (1.0x) - test scaling separately

---

## Test Steps

### Part 1: Entry and Session Initialization

#### Step 1.1: Launch app and navigate to HomeScreen
1. Launch GrammarMate app
2. **Verify:** App opens on HomeScreen (not stuck in Daily Practice from previous session)
3. **Verify:** Daily Practice tile is visible with label "Daily Practice" (`home_daily_practice`)

#### Step 1.2: Tap Daily Practice tile
1. Tap the Daily Practice tile
2. **Verify:** Loading overlay shows with text "Loading session..." (`daily_loading_session`)
3. **Verify:** After 200-500ms, loading dismisses and Daily Practice screen appears

#### Step 1.3: Verify initial screen state
1. **Verify:** Header shows back arrow and "Daily Practice" title (`daily_header`)
2. **Verify:** Block label badge shows "Translation" (`block_translate`)
3. **Verify:** Progress bar shows "1/30" (or X/30 where X = total cards)
4. **Verify:** Card content area displays first sentence card with:
   - "RU" label
   - Russian prompt text (20sp at 1.0x scale)
   - TTS speaker button (VolumeUp icon)
5. **Verify:** Input controls show Voice mode active (mic icon highlighted)

---

### Part 2: Block 1 - Sentence Translations (10 cards)

#### Step 2.1: Card 1 - Voice mode (auto-start)
1. **Verify:** Microphone icon is active
2. **Verify:** Voice recognition auto-starts after 200ms delay
3. **Verify:** Hint text shows "Say translation: {promptRu}"
4. **Action:** Speak correct translation aloud
5. **Verify:** Recognition processes (1200ms max)
6. **Verify:** If correct: auto-advance after 400ms with "Correct" feedback
7. **Verify:** If incorrect: "Incorrect" (red) shows with remaining attempts count
8. **Action:** If incorrect, ASR auto-restarts after 1200ms

#### Step 2.2: Card 2 - Keyboard mode
1. **Verify:** Progress bar shows "2/30"
2. **Verify:** Input mode indicator shows "Keyboard"
3. **Verify:** Text field shows "Your translation" label
4. **Action:** Type correct translation
5. **Verify:** If exact match: auto-submit occurs (text field clears, card advances)
6. **Verify:** "Correct" feedback shows briefly
7. **Verify:** Next card appears

#### Step 2.3: Card 3 - Word Bank mode
1. **Verify:** Input mode indicator shows "Word Bank"
2. **Verify:** FlowRow of FilterChip words appears (8-12 words)
3. **Verify:** "Tap words in correct order:" hint shows
4. **Action:** Tap words in correct order to form the translation
5. **Verify:** Selected count updates ("Selected: N / M")
6. **Verify:** "Undo" button removes last selected word
7. **Action:** Tap "Check" button after selecting all words
8. **Verify:** "Correct" feedback shows

#### Step 2.4: Incorrect answer flow (3 attempts)
1. **Action:** On a new card, submit an incorrect answer (voice or keyboard)
2. **Verify:** "Incorrect" (red) text shows with "Attempts remaining: 2"
3. **Action:** Submit another incorrect answer
4. **Verify:** "Attempts remaining: 1"
5. **Action:** Submit third incorrect answer
6. **Verify:** Hint answer card appears with pink background
7. **Verify:** Answer text is in red with inline TTS button
8. **Verify:** Input controls remain visible (session continues)

#### Step 2.5: Manual "Show Answer" button
1. **Action:** On a fresh card, tap the Eye icon (Show Answer)
2. **Verify:** Hint answer card appears immediately
3. **Verify:** Attempt counters reset
4. **Action:** Type any text and submit
5. **Verify:** Card advances to next

#### Step 2.6: TTS button functionality
1. **Action:** Tap TTS speaker button (VolumeUp icon)
2. **Verify:** Icon changes to StopCircle (red) while speaking
3. **Verify:** Audio plays target language pronunciation
4. **Action:** Tap StopCircle to stop playback
5. **Verify:** Icon returns to VolumeUp

#### Step 2.7: Complete remaining Block 1 cards
1. **Action:** Complete cards 4-10 (modes rotate: 4=Voice, 5=Keyboard, 6=WordBank, etc.)
2. **Verify:** Progress bar updates each card (4/30, 5/30, ..., 10/30)
3. **Verify:** After card 10: block transition triggers

#### Step 2.8: Block 1 -> Block 2 transition
1. **Verify:** BlockSparkleOverlay appears with semi-transparent black background
2. **Verify:** Sparkle emoji (48sp) shows
3. **Verify:** Message shows "Next: Vocabulary"
4. **Verify:** Overlay auto-dismisses after 800ms
5. **Verify:** Block label badge changes to "Vocabulary" (`block_vocab`)
6. **Verify:** New block content (vocab flashcard) appears

---

### Part 3: Block 2 - Vocabulary Flashcards (10 cards)

#### Step 3.1: Vocab card 1 - Verify initial state
1. **Verify:** Flashcard Card displays with `surfaceVariant` background
2. **Verify:** Prompt text shows (28sp, bold, centered)
   - IT_TO_RU: Italian word
   - RU_TO_IT: Russian word
3. **Verify:** Answer text shows (18sp, medium, primary color, centered)
4. **Verify:** Both prompt and answer are VISIBLE (no flip required)
5. **Verify:** TTS button present
6. **Verify:** Four rating buttons at bottom:
   - "Again" (light red bg, red text)
   - "Hard" (light orange bg, orange text)
   - "Good" (light green bg, green text)
   - "Easy" (light blue bg, blue text)

#### Step 3.2: Voice input on vocab card
1. **Action:** Tap large microphone button (64dp)
2. **Verify:** ASR starts with language matching answer direction
   - IT_TO_RU: ru-RU (speak Russian)
   - RU_TO_IT: it-IT (speak Italian)
3. **Action:** Speak correct answer
4. **Verify:** If correct: auto-rate as "Good" (2) and auto-advance
5. **Verify:** If incorrect: "You said: ..." text shows, user stays on card

#### Step 3.3: Rating buttons - SRS step changes
1. **Action:** Tap "Again" button
2. **Verify:** Card advances immediately to next
3. **Verify:** (Behind the scenes) Word mastery step resets to 0
4. **Action:** On next card, tap "Hard"
5. **Verify:** Card advances, step stays same
6. **Action:** On next card, tap "Good"
7. **Verify:** Card advances, step increments by 1
8. **Action:** On next card, tap "Easy"
9. **Verify:** Card advances, step increments by 2

#### Step 3.4: Report functionality on vocab card
1. **Action:** Tap report/flag icon on flashcard
2. **Verify:** ModalBottomSheet opens with:
   - Card prompt text at top
   - "Add to bad sentences list" (or "Remove..." if already flagged)
   - "Export bad sentences to file"
   - "Copy text"
3. **Action:** Tap "Add to bad sentences list"
4. **Verify:** Icon turns red (error color)
5. **Action:** Tap export option
6. **Verify:** File export message shows path or "No bad sentences to export"

#### Step 3.5: Complete remaining Block 2 cards
1. **Action:** Complete cards 2-10, testing different ratings
2. **Verify:** Progress bar updates (11/30 through 20/30)
3. **Verify:** After card 10: block transition triggers

#### Step 3.6: Block 2 -> Block 3 transition
1. **Verify:** BlockSparkleOverlay appears
2. **Verify:** Message shows "Next: Verbs"
3. **Verify:** Overlay dismisses after 800ms
4. **Verify:** Block label badge changes to "Verbs" (`block_verbs`)

---

### Part 4: Block 3 - Verb Conjugations (10 cards)

#### Step 4.1: Verb card 1 - Verify initial state
1. **Verify:** Card shows Russian prompt with "RU" label (20sp)
2. **Verify:** Three SuggestionChip hint chips:
   - Verb infinitive + rank (e.g., "essere #1")
   - Tense abbreviation (e.g., "Pres.", "Imperf.")
   - Group (e.g., "io", "tu", "lui/lei")
3. **Verify:** Input mode is KEYBOARD (even index) or WORD_BANK (odd index)
4. **Note:** VOICE mode is NOT used for verb cards

#### Step 4.2: Verb chip interaction
1. **Action:** Tap verb infinitive chip (e.g., "essere #1")
2. **Verify:** VerbReferenceBottomSheet opens
3. **Verify:** Sheet shows conjugation table for verb+tense
4. **Verify:** TTS button speaks verb infinitive
5. **Action:** Tap outside or back to dismiss

#### Step 4.3: Tense chip interaction
1. **Action:** Tap tense abbreviation chip (e.g., "Pres.")
2. **Verify:** TenseInfoBottomSheet opens
3. **Verify:** Sheet shows:
   - Full tense name
   - Formula
   - When to use (in Russian)
   - Examples
4. **Action:** Tap outside or back to dismiss

#### Step 4.4: Weak-first ordering verification
1. **Note:** Cards should appear in weakness order (least practiced tenses first)
2. **Verify:** If this is first session at level 1: only Presente verbs show
3. **Verify:** Collocation groups appear together (all forms of same verb+tense consecutively)

#### Step 4.5: Complete Block 3 cards
1. **Action:** Complete cards 1-10 using KEYBOARD and WORD_BANK modes
2. **Verify:** Progress bar updates (21/30 through 30/30)
3. **Verify:** After card 10: session completion triggers

---

### Part 5: Session Completion

#### Step 5.1: Completion sparkle and screen
1. **Verify:** BlockSparkleOverlay appears
2. **Verify:** Message shows "Daily practice complete!"
3. **Verify:** Subtitle shows "Great job today!"
4. **Verify:** Overlay dismisses after 800ms
5. **Verify:** DailyPracticeCompletionScreen shows:
   - Title: "Session Complete!"
   - Body: "Great job! You practiced translations, vocabulary, and verb conjugations."
   - Button: "Back to Home"

#### Step 5.2: Cursor advancement check
1. **Note:** Behind the scenes, if all TRANSLATE and VERBS cards were answered via VOICE/KEYBOARD:
   - `DailyCursorState.sentenceOffset` increments by 10
   - If offset >= lesson size: `currentLessonIndex` advances, offset resets to 0
   - Fire streak records for TRANSLATION, VOCAB, VERBS types
2. **Action:** Tap "Back to Home" button
3. **Verify:** Navigation returns to HomeScreen

---

### Part 6: Exit and Resume Flows

#### Step 6.1: Mid-session exit
1. **Action:** Start a new Daily Practice session
2. **Action:** Complete 1-2 cards
3. **Action:** Tap back arrow in header
4. **Verify:** Exit confirmation dialog appears:
   - Title: "Exit practice?"
   - Message: "Your progress in this session will be lost."
   - Buttons: "Exit" and "Stay"
5. **Action:** Tap "Stay"
6. **Verify:** Dialog dismisses, session continues
7. **Action:** Tap back arrow again
8. **Action:** Tap "Exit"
9. **Verify:** Navigation returns to HomeScreen
10. **Verify:** Session state is cleared (in-memory session lost)

#### Step 6.2: Resume dialog (same day)
1. **Note:** If first session completed today, `firstSessionDate` == today
2. **Action:** Tap Daily Practice tile again on same day
3. **Verify:** Resume dialog appears with options:
   - "Repeat" - replay first session's cards
   - "Continue" - new cards from cursor position
4. **Action:** Tap "Repeat"
5. **Verify:** Session starts with same cards as first session
6. **Verify:** Mastery/SRS progress preserved from first session

---

### Part 7: Edge Cases and Special Flows

#### Step 7.1: TTS model not downloaded
1. **Precondition:** TTS model not installed
2. **Action:** Tap TTS speaker button
3. **Verify:** Download dialog appears:
   - Title: "Download pronunciation model?"
   - Message: Model size (~346 MB)
   - Buttons: "Download", "Continue in background", "Cancel"
4. **Action:** Tap "Download"
5. **Verify:** Progress indicator shows during download
6. **Verify:** "Pronunciation model ready!" message when complete

#### Step 7.2: Metered network warning
1. **Precondition:** Device on cellular/metered connection
2. **Action:** Trigger TTS or ASR model download
3. **Verify:** Metered network dialog appears:
   - Title: "Metered network detected"
   - Warning about data usage
   - Button: "Download anyway"

#### Step 7.3: Empty block handling
1. **Precondition:** Pack has no vocab drill files
2. **Action:** Start Daily Practice
3. **Verify:** Session starts with Block 1 (TRANSLATE)
4. **Verify:** After Block 1 completion, transition to Block 3 (VERBS) - Block 2 skipped
5. **Verify:** No crash or stuck loading state

#### Step 7.4: Font scaling
1. **Precondition:** Font scale set to 1.5x in Settings
2. **Action:** Start Daily Practice
3. **Verify:** Block 1 prompt text is 30sp (20sp * 1.5)
4. **Verify:** Block 2 prompt text is 42sp (28sp * 1.5)
5. **Verify:** Block 3 prompt text is 30sp (20sp * 1.5)
6. **Verify:** Hint chips NOT scaled (remain default size)
7. **Verify:** All text still fits on screen without overflow

#### Step 7.5: Pack without verb drill
1. **Precondition:** Pack manifest has no `verbDrill` section
2. **Action:** Start Daily Practice
3. **Verify:** Only TRANSLATE and VOCAB blocks appear
4. **Verify:** VERBS block is skipped (no crash)
5. **Verify:** Completion screen shows after 2 blocks

---

## Verification Points Summary

### Session Initialization
- [ ] Daily Practice tile visible and tappable on HomeScreen
- [ ] Loading overlay appears during session build
- [ ] Header displays correctly with back arrow and title
- [ ] Block label badge shows correct block type
- [ ] Progress bar shows correct position (X/30)
- [ ] First card renders with all elements

### Block 1 (TRANSLATE)
- [ ] 10 cards generated from cursor position
- [ ] Input mode rotates: VOICE -> KEYBOARD -> WORD_BANK
- [ ] Voice recognition auto-starts on VOICE cards
- [ ] Keyboard auto-submits on exact match
- [ ] Word bank shows correct words with undo
- [ ] Incorrect feedback shows with remaining attempts
- [ ] Hint answer appears after 3 wrong attempts
- [ ] Manual "Show Answer" works
- [ ] TTS button plays audio
- [ ] Report sheet opens and functions
- [ ] Only VOICE/KEYBOARD answers count for mastery

### Block 2 (VOCAB)
- [ ] 10 cards generated via SRS selection
- [ ] Flashcard shows both prompt and answer simultaneously
- [ ] Direction alternates: IT_TO_RU -> RU_TO_IT
- [ ] TTS button speaks prompt word
- [ ] Voice input recognizes answer language correctly
- [ ] Rating buttons show correct colors
- [ ] Rating affects SRS step correctly (Again=0, Hard=same, Good=+1, Easy=+2)
- [ ] Report icon toggles red when flagged
- [ ] Export generates file

### Block 3 (VERBS)
- [ ] 10 cards generated via weak-first selection
- [ ] Verb/tense/group chips display correctly
- [ ] Tense abbreviation matches mapping
- [ ] Verb chip opens conjugation sheet
- [ ] Tense chip opens tense info sheet
- [ ] Only KEYBOARD/WORD_BANK modes used (no VOICE)
- [ ] Weak-first ordering prioritizes least practiced tenses
- [ ] Collocation groups appear together

### Block Transitions
- [ ] Sparkle overlay appears with correct message
- [ ] "Next: Vocabulary" after Block 1
- [ ] "Next: Verbs" after Block 2
- [ ] "Daily practice complete!" after Block 3
- [ ] Overlay dismisses after 800ms
- [ ] Next block content appears

### Session Completion
- [ ] Completion screen displays with correct message
- [ ] "Back to Home" button navigates correctly
- [ ] Cursor advances if all blocks completed with VOICE/KEYBOARD
- [ ] Fire streak records for all three practice types
- [ ] Progress saved to stores (mastery, vocab, verbs)

### Exit and Resume
- [ ] Exit confirmation dialog appears on back press
- [ ] "Stay" keeps session active
- [ ] "Exit" returns to Home and clears session
- [ ] Resume dialog appears on same-day retry
- [ ] "Repeat" reconstructs from first session card IDs
- [ ] "Continue" builds from current cursor position

### UI Elements
- [ ] All buttons tappable and responsive
- [ ] TTS states update (IDLE -> SPEAKING -> IDLE)
- [ ] Error states handled (TTS error icon shows)
- [ ] Font scaling applies to prompts but not chips
- [ ] Progress bar updates smoothly
- [ ] Loading state shows for empty currentTask

### Edge Cases
- [ ] TTS download dialog appears when model missing
- [ ] Metered network warning shows correctly
- [ ] Empty blocks are skipped without crash
- [ ] Packs without vocab/verb drills handle gracefully
- [ ] Maximum font scale (2.0x) fits on screen

---

## Test Data Requirements

### Minimum pack content for full coverage:
- **Lesson data:** At least 1 lesson with 15+ sentence cards
- **Vocab drill:** At least one drill file (nouns, verbs, etc.) with 10+ words
- **Verb drill:** CSV with Presente tense verbs, including verb/tense/group columns

### Optional for extended testing:
- Multiple lessons (test cursor advancement)
- All vocab POS types (nouns, verbs, adjectives, adverbs, numbers, pronouns)
- All 12 Italian tenses in verb drill
- Pack without vocab drill (test empty VOCAB block)
- Pack without verb drill (test empty VERBS block)

---

## Known Issues and Limitations

### Completion screen race condition (from scenario-06)
The completion sparkle and screen may not be visible because `onComplete` navigates to HOME immediately after `endSession()`. The screen navigation may win over the sparkle rendering.

### Mid-session resume not supported
In-session position (`taskIndex`, `blockIndex`) is NOT persisted. After app restart, user can only Repeat or Continue, not resume mid-block.

### `dailyPracticeAnsweredCounts` leak (minor)
The counter map is not reset in `repeatDailyPractice()`, potentially causing accumulation across repeat sessions.

---

## Automation Notes

This scenario is designed for both manual testing and automated E2E testing via:
- **mobile-mcp:** Direct device interaction for screenshots and element inspection
- **Maestro:** YAML flows for regression testing

Key element selectors for automation:
- Daily Practice tile: text="Daily Practice"
- Block label badge: contains "Translation" | "Vocabulary" | "Verbs"
- Check button: text="Check"
- Rating buttons: text="Again" | "Hard" | "Good" | "Easy"
- Back button: contentDescription="Back"
- Exit dialog: contains "Exit practice?"

---

## References

- Spec: docs/specification/09-daily-practice.md
- Scenario: docs/specification/scenario-06-daily-practice.md
- UI Strings: app/src/main/res/values/strings-screens.xml, strings-components.xml
- Code: ui/DailyPracticeScreen.kt, feature/daily/*.kt
