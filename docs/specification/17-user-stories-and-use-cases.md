# 17. User Stories & Use Cases

## 17.1 Actors

| Actor | Description |
|-------|-------------|
| **New User** | First-time user who has never opened the app. No lesson packs installed, no progress data, no profile name set. |
| **Beginner Learner** | User in early lessons (1--4). Working through NEW_ONLY sub-lessons. Building initial grammar pattern foundations. |
| **Intermediate Learner** | User in lessons 5--9. Encountering MIXED sub-lessons with spaced review from prior lessons. Has active streak data and multiple flowers in SPROUT or BLOOM state. |
| **Advanced Learner** | User in lessons 10--12 or post-course. Engages in Boss Battles, Daily Practice at higher levels, and maintenance review. Flowers at risk of WILTING without regular practice. |
| **Returning User** | User who has been absent for days or weeks. Flowers may be WILTING, WILTED, or GONE. Needs to restore forgotten patterns before advancing. |
| **Content Creator** | User who creates and imports custom lesson packs (ZIP archives with manifest.json + CSV files). May import packs for languages beyond the bundled defaults. |
| **Multi-Language Learner** | User studying more than one target language (e.g., both English and Italian). Switches between language packs and maintains separate progress per language. |

---

## 17.2 Core User Stories

### Onboarding & Profile

**US-01** As a **New User**, I want to be greeted by a welcome dialog on first launch so that I can set my display name and personalize the experience.

**US-02** As a **New User**, I want default lesson packs to be pre-installed so that I can start practicing immediately without manual import.

**US-03** As a **Returning User**, I want my previous session state (screen, selected lesson, progress) to be restored automatically so that I can continue exactly where I left off.

**US-04** As a **New User**, I want to see a "How This Training Works" explanation on the Home screen so that I understand the flower metaphor and spaced repetition methodology.

### Lesson Navigation & Selection

**US-05** As a **Beginner Learner**, I want to see a 4x3 grid of lesson tiles with flower state indicators so that I can understand my overall progress at a glance.

**US-06** As a **Beginner Learner**, I want locked lessons to display a lock icon and prevent access until the prerequisite lesson is completed so that I learn patterns in the correct sequence.

**US-07** As an **Intermediate Learner**, I want to start a locked lesson early (with a confirmation dialog) so that I can preview upcoming content without being blocked.

**US-08** As a **Multi-Language Learner**, I want to switch the active language from the Home screen so that I can alternate between English and Italian practice in the same session.

**US-09** As a **Beginner Learner**, I want to tap a lesson tile and see its sub-lesson roadmap with completion indicators so that I know which sub-lessons remain.

### Training Session (Normal Mode)

**US-10** As a **Beginner Learner**, I want to practice translating Russian sentences into the target language one card at a time so that I build grammar pattern automaticity through retrieval practice.

**US-11** As a **Beginner Learner**, I want to see my progress as a percentage and tempo (cards per active minute) during a session so that I can gauge my performance without distraction.

**US-12** As an **Intermediate Learner**, I want to navigate backward and forward through cards in a session so that I can review previous sentences or skip ahead.

**US-13** As a **Beginner Learner**, I want to see the correct answer after multiple failed attempts so that I can learn from my mistakes without frustration.

**US-14** As a **Beginner Learner**, I want a "Show Answer" button always available so that I can voluntarily reveal the translation when I am stuck.

**US-15** As a **Beginner Learner**, I want to hear the correct pronunciation via TTS after revealing the answer so that I can learn the auditory form of the target sentence.

**US-16** As a **Beginner Learner**, I want sub-lessons to auto-complete and return me to the roadmap when all cards are done so that the session flow feels seamless.

### Input Modes (Voice, Keyboard, Word Bank)

**US-17** As a **Beginner Learner**, I want to answer using voice input (microphone) so that I practice spoken production, not just written recall.

**US-18** As a **Beginner Learner**, I want to switch to keyboard input mode so that I can type answers when voice recognition is impractical (noisy environment, privacy).

**US-19** As a **Beginner Learner**, I want to use word bank mode (tap word chips to form a sentence) so that I can practice sentence construction even when I cannot recall the full answer from memory.

**US-20** As a **Beginner Learner**, I want word bank selections NOT to count toward mastery/flower growth so that the system does not inflate my actual recall ability.

**US-21** As a **Beginner Learner**, I want to remove the last selected word from the word bank with a single tap so that I can correct mistakes quickly.

### Spaced Repetition & Review

**US-22** As an **Intermediate Learner**, I want MIXED sub-lessons to include review cards from prior lessons so that I maintain earlier patterns while learning new ones.

**US-23** As an **Intermediate Learner**, I want review cards to be drawn from reserve pools of prior lessons so that I do not memorize specific phrases but instead reinforce the underlying pattern.

**US-24** As a **Returning User**, I want the spaced repetition interval ladder (1, 2, 4, 7, 10, 14, 20, 28, 42, 56 days) to determine when review cards reappear so that forgotten patterns are automatically flagged for practice.

**US-25** As a **Returning User**, I want my flowers to visually wilt and eventually disappear if I neglect review for extended periods so that I am motivated to maintain regular practice.

### Flower Progress Visualization

**US-26** As a **Beginner Learner**, I want to see my lesson flowers progress through SEED, SPROUT, and BLOOM states as I practice more unique cards so that I have a clear visual metaphor for pattern mastery.

**US-27** As a **Returning User**, I want flowers to show WILTING or WILTED states when review is overdue so that I can identify which lessons need attention.

**US-28** As a **Returning User**, I want flowers that have been neglected for over 90 days to show as GONE so that I understand the consequences of extended absence.

**US-29** As a **Beginner Learner**, I want the flower size (scale multiplier) to reflect both mastery percentage and health so that a small flower signals incomplete or decaying knowledge.

**US-30** As a **Beginner Learner**, I want only VOICE and KEYBOARD input to grow my flower so that I am incentivized to practice active recall rather than passive recognition.

### Boss Battle

**US-31** As an **Advanced Learner**, I want to attempt a Final Boss challenge for each lesson so that I can test my pattern stability under pressure.

**US-32** As an **Advanced Learner**, I want to earn Bronze (>=30%), Silver (>=60%), or Gold (100%) medals based on my Boss accuracy so that I have a tangible measure of mastery.

**US-33** As an **Advanced Learner**, I want to attempt a Mega Boss that mixes sentences from all completed lessons so that I can test cross-pattern discrimination.

**US-34** As an **Advanced Learner**, I want the Gold medal Boss to draw from both Active and Reserve pools so that the highest achievement tests truly generalized knowledge.

### Daily Practice (3 Blocks)

**US-35** As an **Intermediate Learner**, I want a unified Daily Practice session with 3 blocks (10 sentence translations, 5 vocab flashcards, 10 verb conjugations) so that I can practice all skills in one sitting.

**US-36** As an **Intermediate Learner**, I want Daily Practice to use a cursor that tracks my position across lessons so that each session presents fresh cards rather than repeating the same sentences.

**US-37** As a **Returning User**, I want a "Repeat" option when resuming a daily session so that I can practice the same cards from the first session of the day.

**US-38** As a **Returning User**, I want a "Continue" option when resuming a daily session so that I get a new set of cards advancing the cursor forward.

**US-39** As an **Intermediate Learner**, I want the sentence translation block to cycle through voice, keyboard, and word bank input modes so that all production skills are exercised.

**US-40** As an **Intermediate Learner**, I want to see block-level progress (e.g., "3/10 sentences completed") so that I know how far I am within each daily practice block.

**US-41** As an **Advanced Learner**, I want the verb drill block in Daily Practice to increase in tense complexity as my level rises (Presente only at level 1, adding Imperfetto at level 2, etc.) so that conjugation practice scales with my ability.

### Verb Drill

**US-42** As an **Intermediate Learner**, I want a dedicated Verb Drill screen where I practice conjugating verbs in specific tenses so that I build automaticity in inflection patterns.

**US-43** As an **Intermediate Learner**, I want to filter verb drills by tense and group so that I can focus on areas where I am weakest.

**US-44** As an **Intermediate Learner**, I want the Verb Drill tile to appear on the Home screen only when the active pack includes verb drill content so that I am not shown features I cannot use.

**US-45** As an **Intermediate Learner**, I want verb drill progress to be scoped per-pack so that switching packs does not interfere with my conjugation progress.

### Vocab Drill

**US-46** As a **Beginner Learner**, I want an Anki-style vocab flashcard drill where I see a word, flip to reveal the translation, and rate my recall so that I build vocabulary alongside grammar.

**US-47** As a **Beginner Learner**, I want vocab flashcards to alternate between IT->RU and RU->IT directions so that I practice bidirectional recall.

**US-48** As an **Intermediate Learner**, I want vocab drill word selection to follow SRS (most overdue first, then new words, then least-recently-reviewed fallback) so that I review words at the optimal interval.

**US-49** As an **Intermediate Learner**, I want to see my mastered word count on the Vocab Drill tile on the Home screen so that I can track vocabulary growth.

**US-50** As a **Beginner Learner**, I want to filter vocab drills by part of speech (nouns, verbs, adjectives, adverbs) so that I can focus on specific word categories.

### Settings & Configuration

**US-51** As a **Content Creator**, I want to access a Service Mode settings sheet from any screen so that I can manage packs, progress, and configuration without leaving my current context.

**US-52** As a **Beginner Learner**, I want to adjust TTS pronunciation speed (0.5x to 1.5x) so that I can hear target-language sentences at a pace I can follow.

**US-53** As a **Beginner Learner**, I want to toggle offline speech recognition on or off so that I can choose between Google online STT and Sherpa-ONNX offline ASR.

**US-54** As a **Beginner Learner**, I want to adjust the Russian prompt text scale so that the source sentence is displayed at a comfortable reading size.

**US-55** As a **New User**, I want to enable Test Mode to unlock all lessons and accept all answers so that I can preview content without affecting my real progress.

### Lesson Pack Import/Export

**US-56** As a **Content Creator**, I want to import a lesson pack ZIP file containing a manifest.json and CSV lesson files so that I can add new courses or languages to the app.

**US-57** As a **Content Creator**, I want to import individual lesson CSV files so that I can update or replace a single lesson without reimporting the entire pack.

**US-58** As a **Content Creator**, I want to delete an entire lesson pack so that I can remove content I no longer need.

**US-59** As a **Multi-Language Learner**, I want to add a new target language from Settings so that I can begin studying a language not initially installed.

**US-60** As a **Content Creator**, I want to create an empty lesson from Settings so that I can build custom content from scratch within the app.

### Backup & Restore

**US-61** As a **Returning User**, I want to create a backup of all my progress and settings to the Downloads/BaseGrammy/ directory so that I do not lose my learning history when switching devices.

**US-62** As a **Returning User**, I want to restore a backup from a directory so that I can recover my progress on a new device or after reinstalling.

**US-63** As a **Returning User**, I want to reset all progress from Settings so that I can start over completely if desired.

### Offline Usage

**US-64** As a **Beginner Learner**, I want the entire app to function without internet so that I can practice during commutes, flights, or in areas with poor connectivity.

**US-65** As a **Beginner Learner**, I want to download TTS and ASR models to my device so that audio features work fully offline.

**US-66** As a **Beginner Learner**, I want to be warned before downloading large audio models on a metered (mobile data) connection so that I can avoid unexpected data charges.

### Audio (TTS/ASR)

**US-67** As a **Beginner Learner**, I want to hear target-language sentences pronounced by an offline TTS engine (Sherpa-ONNX) so that I learn correct pronunciation.

**US-68** As a **Beginner Learner**, I want a TTS download dialog with progress indication so that I know when the voice model is ready for use.

**US-69** As a **Beginner Learner**, I want background TTS model downloads to show a thin progress bar at the top of every screen so that I am aware of ongoing downloads without disruption.

**US-70** As a **Beginner Learner**, I want TTS to auto-play immediately after a model finishes downloading so that I hear the sentence I requested without needing to press play again.

**US-71** As a **Beginner Learner**, I want offline speech recognition via Sherpa-ONNX so that I can practice voice answers without an internet connection.

### Streak & Motivation

**US-72** As a **Beginner Learner**, I want to see my current daily streak and longest streak after completing a daily practice session so that I am motivated to maintain consistent study habits.

**US-73** As a **Returning User**, I want a streak celebration dialog when I maintain a multi-day streak so that I feel rewarded for my consistency.

### Story Quiz

**US-74** As a **Beginner Learner**, I want to take a Story Check-in quiz before starting a lesson so that the app can measure my baseline comprehension of the target grammar pattern.

**US-75** As a **Beginner Learner**, I want to take a Story Check-out quiz after completing a lesson so that I can see how much my comprehension improved.

**US-76** As a **Beginner Learner**, I want to skip a Story quiz if I am not interested so that I am not forced into reading exercises when I want to focus on translation practice.

### Bad Sentence Management

**US-77** As a **Content Creator**, I want to flag a sentence as bad (incorrect translation, unnatural phrasing) during training so that problematic content is tracked for later correction.

**US-78** As a **Content Creator**, I want to export the list of flagged bad sentences so that I can review and fix content issues in the source CSV files.

**US-79** As a **Content Creator**, I want to hide a specific card from my training sessions so that I am not repeatedly tested on a sentence I consider incorrect.

---

## 17.3 Use Cases

### UC-01: First Launch and Onboarding

- **Name:** First Launch and Onboarding
- **Actor:** New User
- **Preconditions:** App installed on device. No prior data in app storage.
- **Main Flow:**
  1. User launches GrammarMate.
  2. App checks for backup restore status via AppRoot.
  3. No backup found. App loads default lesson packs from assets.
  4. App displays Home screen with default pack tiles (all lessons locked except first).
  5. Welcome dialog appears prompting for display name.
  6. User enters name or taps "Skip."
  7. Profile is saved. User sees Home screen with 4x3 lesson grid and "Start learning" button.
- **Alternative Flows:**
  - 5a. User dismisses keyboard without entering a name. Default name "GrammarMateUser" is assigned. Welcome dialog will reappear if they navigate away from Home.
- **Postconditions:** User has a profile name, default lesson packs are installed, and the first lesson is unlocked.
- **Business Rules:** Default packs must be listed in `defaultPacks` in `LessonStore.kt`. Both the ZIP in assets and the `DefaultPack` entry are required.

---

### UC-02: Start a Training Sub-Lesson

- **Name:** Start a Training Sub-Lesson
- **Actor:** Beginner Learner
- **Preconditions:** At least one lesson is unlocked. User is on Lesson Roadmap screen.
- **Main Flow:**
  1. User taps a completed or available sub-lesson circle on the roadmap.
  2. App loads the sub-lesson cards (NEW_ONLY or MIXED type).
  3. Screen transitions to Training screen.
  4. First card displays Russian prompt.
  5. Input mode is set to the user's default (VOICE).
  6. Session state changes to ACTIVE. Timer begins tracking active time.
- **Alternative Flows:**
  - 2a. Sub-lesson has no cards (empty lesson). Display empty state message.
- **Postconditions:** Training session is active. Progress is being tracked in `TrainingProgress`.
- **Business Rules:** NEW_ONLY sub-lessons use only cards from the current lesson's main pool. MIXED sub-lessons interleave current lesson cards with review cards from prior lessons (reserve pool priority).

---

### UC-03: Submit Answer via Voice Input

- **Name:** Submit Answer via Voice Input
- **Actor:** Beginner Learner
- **Preconditions:** Training session is active. Input mode is VOICE. Microphone permission granted.
- **Main Flow:**
  1. Voice prompt auto-starts (microphone activates).
  2. User speaks the target-language translation.
  3. Speech is recognized (online Google STT or offline Sherpa-ONNX ASR).
  4. Recognized text is auto-submitted for comparison.
  5. Answer is normalized (trim, lowercase, collapse spaces, preserve intra-word apostrophes).
  6. System compares against all accepted answers for the current card.
  7. If correct: green indicator, correct count incremented, session advances.
  8. If incorrect: red indicator, incorrect count incremented. User may retry or show answer.
- **Alternative Flows:**
  - 3a. Recognition fails or returns empty. User is prompted to try again.
  - 6a. Multiple accepted answers exist (separated by `+` in CSV). Any match counts as correct.
  - 8a. After 3 incorrect attempts, correct answer is automatically revealed.
- **Postconditions:** Card result is recorded. If input mode is VOICE or KEYBOARD, unique card show is tracked for mastery.
- **Business Rules:** WORD_BANK mode never counts toward mastery. Answer normalization must preserve apostrophes within words (don't != dont).

---

### UC-04: Submit Answer via Keyboard Input

- **Name:** Submit Answer via Keyboard Input
- **Actor:** Beginner Learner
- **Preconditions:** Training session is active. Input mode is KEYBOARD.
- **Main Flow:**
  1. User sees Russian prompt and text input field.
  2. User types target-language translation.
  3. User presses submit (Enter key or submit button).
  4. Answer is normalized and compared against accepted answers.
  5. Correct/incorrect feedback is displayed as per UC-03.
- **Alternative Flows:**
  - 2a. User taps "Show Answer" before typing. Answer is revealed. Session state moves to AFTER_CHECK. Auto-voice is disabled.
- **Postconditions:** Same as UC-03. Keyboard input counts toward mastery.
- **Business Rules:** Text input is limited to single-line entry. IME action is "Done."

---

### UC-05: Submit Answer via Word Bank

- **Name:** Submit Answer via Word Bank
- **Actor:** Beginner Learner
- **Preconditions:** Training session is active. Input mode is WORD_BANK.
- **Main Flow:**
  1. Russian prompt is displayed.
  2. A set of word chips (shuffled fragments of the correct answer + distractors) appears.
  3. User taps word chips to build the sentence sequentially.
  4. Selected words appear in order above the chip tray.
  5. User taps the last selected word to remove it (undo).
  6. When the user is satisfied, they submit the constructed sentence.
  7. Answer is compared. Correct/incorrect feedback is shown.
- **Alternative Flows:**
  - 3a. User taps a word already selected. No duplicate selection occurs.
- **Postconditions:** Card result is recorded. Word bank answers do NOT count toward mastery (flower growth).
- **Business Rules:** Only VOICE and KEYBOARD modes grow flowers. WORD_BANK is a scaffolded practice mode for building confidence.

---

### UC-06: Complete a Sub-Lesson

- **Name:** Complete a Sub-Lesson
- **Actor:** Beginner Learner
- **Preconditions:** Training session is active. User is on the last card of the sub-lesson.
- **Main Flow:**
  1. User answers the final card (correct or incorrect).
  2. Sub-lesson finished token is incremented.
  3. App automatically navigates back to the Lesson Roadmap screen.
  4. Completed sub-lesson circle updates its visual state (filled/highlighted).
  5. Completed sub-lesson count increments.
- **Alternative Flows:**
  - 1a. User exits mid-session via back button. Exit confirmation dialog appears. If confirmed, session ends and partial progress is saved.
- **Postconditions:** Sub-lesson progress is persisted. Lesson flower state may update based on accumulated mastery.
- **Business Rules:** Partial session progress is saved on exit. The `subLessonFinishedToken` is used as a one-shot trigger to prevent double navigation.

---

### UC-07: View Flower Progress and Health

- **Name:** View Flower Progress and Health
- **Actor:** Beginner Learner
- **Preconditions:** User has completed at least one training card.
- **Main Flow:**
  1. User views Home screen or Lesson Roadmap.
  2. Each lesson tile displays a flower emoji with size reflecting `scaleMultiplier`.
  3. Flower state is determined: SEED (0--33% mastery), SPROUT (33--66%), BLOOM (66--100%), or decay states.
  4. Health is calculated via Ebbinghaus forgetting curve: R = e^(-t/S) where S grows with each on-time repetition.
  5. If health drops below 100%, flower shows WILTING.
  6. If health drops to 50% (WILTED_THRESHOLD), flower shows WILTED.
  7. If no practice for 90+ days, flower shows GONE.
- **Alternative Flows:**
  - 2a. No mastery data exists for a lesson. Flower displays as SEED (not LOCKED).
- **Postconditions:** User understands which lessons need attention.
- **Business Rules:** Mastery threshold is 150 unique card shows. Interval ladder: [1, 2, 4, 7, 10, 14, 20, 28, 42, 56] days. Health floor is 50% (WILTED_THRESHOLD). GONE after 90 days. Only VOICE and KEYBOARD inputs count toward unique card shows.

---

### UC-08: Boss Battle - Lesson Boss

- **Name:** Boss Battle - Lesson Boss
- **Actor:** Advanced Learner
- **Preconditions:** User has completed all sub-lessons in a lesson. User is on Lesson Roadmap.
- **Main Flow:**
  1. User taps "Start Boss" button on the roadmap.
  2. App loads all cards from the lesson's main pool (150 cards).
  3. Training screen enters boss mode. Session is not pausable.
  4. User progresses through cards one at a time.
  5. After all cards are attempted, boss finishes.
  6. Accuracy is calculated: correctCount / totalCards.
  7. Medal is awarded: Bronze (>=30%), Silver (>=60%), Gold (100%).
  8. Medal dialog displays with color-coded trophy icon.
  9. Reward is persisted in `bossLessonRewards` map.
  10. Screen returns to Lesson Roadmap.
- **Alternative Flows:**
  - 1a. User exits boss mid-session. Boss finishes immediately. No medal is awarded.
  - 7a. Accuracy below 30%. No medal is awarded.
- **Postconditions:** Boss reward is saved. Flower state may update.
- **Business Rules:** Bronze/Silver use only the Active pool (first 150 cards). Gold requires Active + Reserve pools. Boss rewards are per-lesson.

---

### UC-09: Boss Battle - Mega Boss

- **Name:** Boss Battle - Mega Boss
- **Actor:** Advanced Learner
- **Preconditions:** User has completed at least 2 lessons. Mega Boss option is available on roadmap.
- **Main Flow:**
  1. User taps "Start Mega Boss" on the roadmap.
  2. App loads cards from all completed lessons (Active + Reserve).
  3. Cards are shuffled across all lessons for maximum interference.
  4. User progresses through the combined deck.
  5. Upon completion, medal is awarded based on overall accuracy.
  6. Per-lesson rewards are tracked in `bossMegaRewards`.
- **Alternative Flows:**
  - 2a. Insufficient cards to form a meaningful session. Warning displayed.
- **Postconditions:** Mega boss reward is persisted per lesson.
- **Business Rules:** Mega Boss tests cross-pattern discrimination under high interference.

---

### UC-10: Start Daily Practice Session

- **Name:** Start Daily Practice Session
- **Actor:** Intermediate Learner
- **Preconditions:** At least one lesson pack is installed. User is on Home screen.
- **Main Flow:**
  1. User taps "Daily Practice" tile on Home screen.
  2. App checks for resumable daily session (same day, same level).
  3. If no resumable session: app composes a new session using DailySessionComposer.
     a. Block 1: 10 sentence translations drawn from current lesson using cursor offset.
     b. Block 2: 5 vocab flashcards selected by SRS priority (overdue first, then new, then fallback).
     c. Block 3: 10 verb conjugation cards filtered by cumulative tenses for the current level, weak-first ordering.
  4. Loading dialog appears while session is composed.
  5. Screen transitions to DailyPracticeScreen.
  6. User works through blocks sequentially.
- **Alternative Flows:**
  - 2a. Resumable session exists. Dialog offers "Continue" (new cards) or "Repeat" (same cards from first session).
  - 3a. Verb drill content not available in pack. Block 3 is skipped.
  - 3b. Vocab drill content not available in pack. Block 2 is skipped.
- **Postconditions:** Daily session is active. Cursor state tracks progress for next session.
- **Business Rules:** Each block has exactly 10 cards (configurable via `CARDS_PER_BLOCK`). Sentence block cycles input modes: voice, keyboard, word bank. Vocab block alternates IT->RU and RU->IT directions. Verb block alternates keyboard and word bank. Level (1--12) determines which tenses are active in block 3 via TENSE_LADDER.

---

### UC-11: Complete a Daily Practice Block

- **Name:** Complete a Daily Practice Block
- **Actor:** Intermediate Learner
- **Preconditions:** Daily practice session is active. User has completed all cards in the current block.
- **Main Flow:**
  1. User answers the last card in the current block.
  2. Block completion is detected. Block progress shows 10/10 (or 5/5 for vocab).
  3. User is offered "Repeat Block" or "Continue" options.
  4. If "Continue": app advances to next block (TRANSLATE -> VOCAB -> VERBS).
  5. If "Repeat": current block is regenerated with the same card IDs.
  6. On completing all 3 blocks, session ends.
  7. Streak data is updated. Streak celebration dialog may appear.
- **Alternative Flows:**
  - 4a. Next block has no content (e.g., no verb drill data). Block is skipped.
  - 6a. User exits mid-session via back button or exit button. Session is cancelled. Partial progress is not saved for resumption of individual blocks (only full-session resume is supported).
- **Postconditions:** Daily cursor advances. Verb progress is persisted. Vocab mastery is updated.
- **Business Rules:** Block progress is tracked per block type. Verb card progress is persisted via `persistDailyVerbProgress`. Vocab card ratings update `WordMasteryState`.

---

### UC-12: Practice Verb Drill (Standalone)

- **Name:** Practice Verb Drill (Standalone)
- **Actor:** Intermediate Learner
- **Preconditions:** Active pack has `verbDrill` section in manifest. Verb drill CSV files are imported.
- **Main Flow:**
  1. User taps "Verb Drill" tile on Home screen.
  2. VerbDrillViewModel loads available tenses and groups from the active pack.
  3. Verb Drill screen displays filter chips for tense and group selection.
  4. User selects a tense (e.g., "Presente") and optionally a group.
  5. User taps "Start" to begin a drill session.
  6. Cards are loaded for the selected filters, sorted by weakness (fewest previously shown first).
  7. User sees Russian prompt and enters the conjugated form.
  8. Answer is compared to the accepted form.
  9. Correct/incorrect feedback is shown.
  10. After all cards, session summary displays accuracy.
- **Alternative Flows:**
  - 2a. No verb drill data for pack. Tile is not shown on Home screen.
  - 5a. User has already completed all cards for today. "All done today" message displayed.
  - 6a. Drill has previous progress. Dialog offers "Resume" or "Start Fresh."
- **Postconditions:** Verb drill progress is persisted per group+tense combo in `VerbDrillComboProgress`.
- **Business Rules:** Drill visibility is pack-scoped. Progress stored at `drills/{packId}/verb_drill_progress.yaml`. Card selection excludes previously shown cards within the same day.

---

### UC-13: Practice Vocab Drill (Standalone)

- **Name:** Practice Vocab Drill (Standalone)
- **Actor:** Beginner Learner
- **Preconditions:** Active pack has `vocabDrill` section in manifest. Vocab drill CSV files are imported.
- **Main Flow:**
  1. User taps "Vocab Drill" tile on Home screen (shows mastered word count).
  2. VocabDrillViewModel loads available parts of speech and word counts.
  3. Screen displays POS filter chips (nouns, verbs, adjectives, adverbs).
  4. User selects a POS filter (or all).
  5. Due words are selected by SRS priority: overdue first, then new words, then least-recently-reviewed.
  6. First card displays: Italian word (for IT->RU direction) or Russian meaning (for RU->IT direction).
  7. User mentally recalls the translation.
  8. User taps to flip the card, revealing the answer, collocations, and word forms.
  9. User rates their recall (1--5 scale).
  10. Rating updates `WordMasteryState`: interval step advances on good ratings, resets on poor ratings.
  11. Next card is presented. Repeat until all due cards are reviewed.
  12. Session summary shows correct/incorrect counts.
- **Alternative Flows:**
  - 5a. No due words. "No words due for review" message displayed.
  - 9a. User enables voice mode. They speak the translation instead of self-rating. Three attempts allowed before auto-skip.
- **Postconditions:** Word mastery states are updated in `drills/{packId}/word_mastery.yaml`. Mastered count on Home tile refreshes.
- **Business Rules:** A word is considered "learned" when `intervalStepIndex` reaches the last step (9). `nextReviewDateMs` is computed as `lastReviewDateMs + INTERVAL_LADDER_DAYS[step] * DAY_MS`.

---

### UC-14: Import a Lesson Pack

- **Name:** Import a Lesson Pack
- **Actor:** Content Creator
- **Preconditions:** User has a valid lesson pack ZIP file accessible on the device.
- **Main Flow:**
  1. User opens Settings sheet from any screen.
  2. User taps "Import Lesson Pack" button.
  3. System file picker opens (filter: ZIP files).
  4. User selects a ZIP file.
  5. App extracts `manifest.json` and validates `schemaVersion`, `packId`, `language`.
  6. CSV lesson files are parsed and stored in `grammarmate/` directory.
  7. If manifest contains `verbDrill` section, verb drill CSVs are copied to `grammarmate/drills/{packId}/verb_drill/`.
  8. If manifest contains `vocabDrill` section, vocab drill CSVs are copied to `grammarmate/drills/{packId}/vocab_drill/`.
  9. Pack is registered in installed packs list with import timestamp.
  10. Home screen refreshes to show new lesson tiles.
- **Alternative Flows:**
  - 5a. Invalid manifest (missing fields, wrong schema version). Error message displayed.
  - 6a. CSV parsing error. Partial import proceeds. Error is logged.
  - 5b. Pack with same `packId` already exists. Existing pack is replaced (re-import).
- **Postconditions:** Lesson pack is installed and available for practice. Drill tiles appear if applicable.
- **Business Rules:** Lessons with `type: "verb_drill"` in manifest are filtered during import (not parsed as standard lessons). All file writes use `AtomicFileWriter` (temp -> fsync -> rename).

---

### UC-15: Switch Active Language

- **Name:** Switch Active Language
- **Actor:** Multi-Language Learner
- **Preconditions:** More than one language is installed.
- **Main Flow:**
  1. User taps language selector on Home screen (shows current language code).
  2. Dropdown displays all installed languages.
  3. User selects a different language.
  4. App reloads lessons, packs, flowers, and drill data for the selected language.
  5. Home screen updates with the new language's lesson grid and flower states.
  6. Active pack is switched to the first pack for the selected language.
- **Alternative Flows:**
  - 4a. No packs installed for the selected language. Lesson grid is empty. Import prompt displayed.
- **Postconditions:** All UI reflects the newly selected language. Progress for the previous language is preserved.
- **Business Rules:** Language switching does not lose progress. Each language maintains independent flower states, mastery data, and drill progress.

---

### UC-16: Backup and Restore Progress

- **Name:** Backup and Restore Progress
- **Actor:** Returning User
- **Preconditions:** App has progress data to back up, or a backup directory exists for restore.
- **Main Flow (Backup):**
  1. User opens Settings sheet.
  2. User taps "Save Progress" button.
  3. BackupManager copies all YAML/CSV state files to `Downloads/BaseGrammy/` directory.
  4. Confirmation toast is displayed.
- **Main Flow (Restore):**
  1. User opens Settings sheet.
  2. User taps "Restore Backup" button.
  3. System directory picker opens.
  4. User selects the backup directory.
  5. App reads backup files and overwrites current state.
  6. App reloads all data. UI refreshes.
- **Alternative Flows:**
  - 3a. (Backup) External storage not writable. Error message displayed.
  - 4a. (Restore) Selected directory has no valid backup files. Error message displayed.
  - 5a. (Restore) Backup schema version is older. Migration is applied.
- **Postconditions:** Progress data is safely stored externally or restored from external storage.
- **Business Rules:** Backup uses atomic file writes. Restore validates file integrity before overwriting. Schema versioning supports forward-compatible migration.

---

### UC-17: Download and Use TTS Voice Model

- **Name:** Download and Use TTS Voice Model
- **Actor:** Beginner Learner
- **Preconditions:** TTS model for the target language is not yet downloaded. Internet connection is available (or user is on metered network).
- **Main Flow:**
  1. User taps the speaker icon on the Training screen.
  2. App detects no TTS model for the current language.
  3. Background download state is checked. If already downloading, dialog shows current progress.
  4. TTS download dialog appears with download button.
  5. User taps "Download."
  6. If on metered network: metered network warning dialog appears. User confirms or cancels.
  7. Download begins. Progress bar updates in dialog and as thin bar at top of all screens.
  8. When download completes: model is extracted and initialized.
  9. Dialog auto-closes. TTS auto-plays the requested sentence.
- **Alternative Flows:**
  - 6a. User cancels metered network warning. Download is aborted.
  - 7a. Download fails (network error, corrupted file). Error state is displayed in dialog.
  - 8a. Background download was already in progress from a prior session. Dialog shows current progress without restarting.
- **Postconditions:** TTS model is ready for the target language. Future TTS requests play immediately.
- **Business Rules:** TTS speed is configurable (0.5x--1.5x). Multiple language models can be downloaded simultaneously. Models are stored in app-private storage.

---

### UC-18: Download and Use Offline ASR Model

- **Name:** Download and Use Offline ASR Model
- **Actor:** Beginner Learner
- **Preconditions:** Offline ASR toggle is off. Device has a microphone.
- **Main Flow:**
  1. User opens Settings sheet.
  2. User toggles "Offline speech recognition" switch to ON.
  3. App checks if ASR model is ready.
  4. If not ready, download begins automatically.
  5. Download progress is shown inline in Settings.
  6. When complete, offline ASR is active for voice input.
  7. Future voice answers use Sherpa-ONNX instead of Google STT.
- **Alternative Flows:**
  - 4a. On metered network. ASR metered network warning dialog appears. User confirms or cancels.
  - 6a. Download fails. Error message shown. Toggle remains off.
- **Postconditions:** Offline ASR model is downloaded and active. Voice input works without internet.
- **Business Rules:** Offline ASR toggle persists across sessions. If toggle is turned off, Google online STT is used instead.

---

### UC-19: Flag and Export Bad Sentences

- **Name:** Flag and Export Bad Sentences
- **Actor:** Content Creator
- **Preconditions:** Training session is active. A card is displayed.
- **Main Flow:**
  1. User identifies a sentence with incorrect or unnatural translation.
  2. User taps the flag/warning icon on the card.
  3. Card is marked as "bad" in the bad sentences store.
  4. Card visual indicator changes to show flagged status.
  5. User continues training. Flagged cards can optionally be hidden.
- **Alternative Flows:**
  - 5a. User taps "Hide Card" instead. Card is removed from future sessions.
  - 2a. User accidentally flags a good card. User taps unflag icon to remove the flag.
- **Postconditions (Export):**
  1. User opens Settings and taps "Export Bad Sentences."
  2. List of flagged cards is exported as text to clipboard.
  3. User can paste the list into a document for review.
- **Business Rules:** Bad sentence flags persist across sessions. Hidden cards are excluded from future session card pools. Export includes card ID, Russian prompt, accepted answers, and flag reason if provided.

---

### UC-20: View Lesson Ladder (Progress Overview)

- **Name:** View Lesson Ladder (Progress Overview)
- **Actor:** Intermediate Learner
- **Preconditions:** User has progress in at least one lesson.
- **Main Flow:**
  1. User opens Settings sheet.
  2. User taps "Show Ladder" button.
  3. Screen transitions to Ladder screen.
  4. Ladder displays all lessons in order with:
     - Lesson number and title
     - Flower state (emoji)
     - Mastery count (unique card shows / 150)
     - Last practiced date
     - Interval step index
  5. User reviews the ladder to identify lessons needing attention.
  6. User presses back to return to the previous screen.
- **Alternative Flows:**
  - 4a. No lessons loaded. Empty state message displayed.
- **Postconditions:** No state changes. This is a read-only view.
- **Business Rules:** Lesson ladder is computed by `LessonLadderCalculator`. Lessons are ordered by their pack-defined sequence.

---

### UC-21: Complete Story Check-in / Check-out

- **Name:** Complete Story Check-in / Check-out
- **Actor:** Beginner Learner
- **Preconditions:** Story quiz data exists for the current lesson. User is on Lesson Roadmap.
- **Main Flow:**
  1. User taps the Story quiz node on the roadmap (Check-in before lesson, Check-out after).
  2. Story text is displayed for reading.
  3. User reads the story and taps to begin the quiz.
  4. Questions appear one at a time (multiple choice).
  5. User selects an answer for each question.
  6. After all questions, quiz results are displayed (accuracy, time).
  7. Metrics are saved: accuracy, time-to-answer, hint rate.
  8. For Check-out: comparison with Check-in metrics is shown ("Before: X%, After: Y%").
  9. Screen returns to Lesson Roadmap.
- **Alternative Flows:**
  - 1a. User taps "Skip" on the story node. Story is marked as skipped. No metrics saved.
  - 2a. Story fails to load. Error dialog is shown.
- **Postconditions:** Story completion is persisted. Check-in/check-out flags are updated on the roadmap.
- **Business Rules:** Check-in is available before starting the lesson. Check-out is available after completing all sub-lessons. Stories do not affect flower growth or mastery.

---

### UC-22: Resume App After Kill (State Restoration)

- **Name:** Resume App After Kill
- **Actor:** Returning User
- **Preconditions:** User previously had an active session. App was killed by user or system.
- **Main Flow:**
  1. User relaunches GrammarMate.
  2. AppRoot checks for backup restore status.
  3. App loads persisted `TrainingProgress` from YAML.
  4. `currentScreen` field determines which screen to show.
  5. If screen was TRAINING: session is restored with card index, counts, and timer.
  6. If screen was ELITE or VOCAB (legacy enum values): redirected to HOME.
  7. If screen was DAILY_PRACTICE: daily session state is restored from `DailySessionState`.
- **Alternative Flows:**
  - 4a. `currentScreen` value is unrecognized. Default to HOME.
  - 5a. No progress file exists. Fresh start.
- **Postconditions:** User returns to approximately the same state as before the kill.
- **Business Rules:** `AppScreen.ELITE` and `AppScreen.VOCAB` enum values are retained for backward compatibility. They redirect to HOME when restored. These must not be removed from the enum.

---

### UC-23: Daily Practice Repeat vs Continue

- **Name:** Daily Practice Repeat vs Continue
- **Actor:** Returning User
- **Preconditions:** User has already completed a daily practice session today at the same level. User taps Daily Practice again.
- **Main Flow:**
  1. App detects a resumable daily session (`hasResumableDailySession()` returns true).
  2. Dialog appears: "Repeat -- same cards from start" / "Continue -- new card set."
  3. If "Repeat": app calls `repeatDailyPractice()` which rebuilds the session using stored card IDs from the first session (`firstSessionSentenceCardIds`, `firstSessionVerbCardIds`).
  4. If "Continue": app calls `startDailyPractice()` which advances the cursor and draws new cards.
  5. Session loads and DailyPracticeScreen appears.
- **Alternative Flows:**
  - 1a. No prior session today. Normal session creation flow (no dialog).
- **Postconditions:** User practices either repeated or fresh cards. Cursor state updates accordingly.
- **Business Rules:** First session card IDs are stored in `DailyCursorState` (`firstSessionDate`, `firstSessionSentenceCardIds`, `firstSessionVerbCardIds`). Vocab block always follows independent SRS regardless of Repeat/Continue choice.

---

### UC-24: Early Start a Locked Lesson

- **Name:** Early Start a Locked Lesson
- **Actor:** Intermediate Learner
- **Preconditions:** User taps a locked lesson tile that has a lesson ID assigned.
- **Main Flow:**
  1. User taps locked lesson tile on Home screen.
  2. Instead of "Lesson locked" message, a confirmation dialog appears: "Start early? You can always come back to review previous lessons."
  3. User taps "Yes."
  4. Lesson is selected and Lesson Roadmap opens for the locked lesson.
  5. User can start sub-lessons in the locked lesson.
- **Alternative Flows:**
  - 2a. Locked tile has no lesson ID (empty slot in pack with fewer than 12 lessons). Generic "Lesson locked" dialog shown.
  - 3a. User taps "No." Dialog closes. No action taken.
- **Postconditions:** User gains access to the locked lesson's content.
- **Business Rules:** Early start does not unlock the lesson permanently in the sequence. It is a temporary access grant. The lesson ladder still shows the lesson in its correct position.

---

### UC-25: Drill Start with Progress Resume

- **Name:** Drill Start with Progress Resume
- **Actor:** Intermediate Learner
- **Preconditions:** User is on Lesson Roadmap. Drill content is available for the lesson.
- **Main Flow:**
  1. User taps the drill start node on the roadmap.
  2. App checks if drill progress exists (`drillHasProgress`).
  3. If progress exists: `DrillStartDialog` appears with "Start Fresh" and "Resume" options.
  4. If "Resume": drill loads from saved card index and counts.
  5. If "Start Fresh": drill resets to the beginning.
  6. Screen transitions to Training screen in drill mode.
- **Alternative Flows:**
  - 2a. No prior drill progress. Drill starts immediately without dialog.
- **Postconditions:** Drill session is active. Progress is tracked independently from normal training sessions.
- **Business Rules:** Drill mode is tracked via `isDrillMode` flag in `TrainingUiState`. Exiting drill mode returns to Lesson Roadmap, not Home.

---

## 17.4 Edge Cases & Error Scenarios

### Storage and Data

**EC-01: Storage is full**
- When the device runs out of storage space, file writes via `AtomicFileWriter` will fail.
- The app should catch `IOException` during any write operation (progress save, pack import, backup) and display a user-facing error: "Storage full. Free up space and try again."
- Partial writes are prevented by the atomic write pattern (temp -> fsync -> rename). If the temp file write fails, the original file remains intact.
- TTS/ASR model downloads will fail mid-download. The partial download should be cleaned up on retry.

**EC-02: Corrupted YAML/CSV state files**
- If a progress YAML file is corrupted (malformed, truncated), the parser will throw an exception.
- The app should catch the exception, log it, and treat the state as default (no progress for that entity).
- This means the user may lose progress for a single lesson or drill, but not all progress.
- `schemaVersion` field allows forward-compatible migration. If the version is newer than the app supports, the file should be skipped with a warning.

**EC-03: No audio model downloaded**
- TTS: If the user taps the speaker icon without a downloaded TTS model, the download dialog appears automatically. The sentence is queued and auto-played once the download completes.
- ASR: If voice input is attempted without ASR model (and offline ASR is enabled), the app falls back to online Google STT if internet is available. If neither is available, voice input is disabled and the user is prompted to switch to keyboard or word bank mode.

**EC-04: All cards mastered (150+ unique shows)**
- When `uniqueCardShows >= MASTERY_THRESHOLD` (150), the flower reaches 100% mastery (BLOOM).
- The lesson is considered fully mastered. Sub-lessons can still be replayed for review.
- Boss Battle for the lesson unlocks (if not already attempted).
- In MIXED sub-lessons, this lesson's cards become available as review material for subsequent lessons.
- The flower may still wilt if not reviewed, transitioning BLOOM -> WILTING -> WILTED -> GONE over the decay schedule.

**EC-05: App killed mid-session**
- `TrainingProgress` is saved periodically during sessions (on card advance, mode change, pause).
- On relaunch, `AppRoot` loads the persisted state. `currentScreen` determines which screen to restore.
- If the session was in ACTIVE state, it is restored with the card index, correct/incorrect counts, and timer state.
- If the app was killed during a TTS/ASR download, the download state is lost. The user must re-trigger the download. Background download progress bar disappears.
- Legacy screen values (`ELITE`, `VOCAB`) redirect safely to HOME.

**EC-06: First launch with no packs**
- Default packs are bundled in `assets/grammarmate/packs/` and auto-imported on first launch.
- If the assets are somehow missing (corrupted APK), the Home screen shows an empty lesson grid.
- The user is prompted to import a lesson pack via Settings.
- All drill tiles (Verb Drill, Vocab Drill) are hidden since no pack declares them.

**EC-07: Device has no microphone**
- When voice input is attempted, the system reports no audio input device.
- The app should detect `audioPermissionDenied` flag and show a message: "Microphone not available. Please switch to keyboard or word bank input."
- Input mode automatically switches to KEYBOARD if voice is unavailable.
- Offline ASR model download should still be allowed (the model may be used with an external microphone).

**EC-08: Lesson pack with zero cards**
- If a lesson CSV file contains no data rows, `Lesson.cards` is empty.
- Attempting to start a sub-lesson from this lesson results in an empty session. The sub-lesson immediately finishes (0/0 cards).
- The flower for this lesson remains at SEED since `uniqueCardShows` stays at 0.
- The lesson should be skipped in Daily Practice sentence block selection (returns empty list).

**EC-09: All verb drill cards already shown today**
- `VerbDrillComboProgress.todayShownCardIds` tracks cards shown in the current day.
- When all cards for the selected tense/group have been shown, `allDoneToday` becomes true.
- The verb drill screen displays "All done today!" message.
- The user can still practice by selecting a different tense or group filter.
- The next day, `todayShownCardIds` resets and cards become available again.

**EC-10: All vocab words mastered**
- When a word's `intervalStepIndex` reaches the last step (9), `isLearned` becomes true.
- The mastered count on the Vocab Drill Home tile increments.
- Mastered words are deprioritized in SRS selection (they have distant `nextReviewDateMs` values).
- Eventually all words may be mastered. The Vocab Drill screen shows a completion message.
- Mastered words remain in the pool for long-term review and may reappear when their next review date arrives.

**EC-11: Multiple packs for same language**
- The user may import multiple packs targeting the same language (e.g., "Italian Core A1" and "Italian Core A2").
- The `activePackId` determines which pack's lessons and drill content are displayed.
- Switching packs via Settings changes the active pack without losing progress for the other.
- Progress is scoped per-pack: `drills/{packId}/verb_drill_progress.yaml` and `drills/{packId}/word_mastery.yaml`.
- Drill tile visibility is pack-scoped: `hasVerbDrill` and `hasVocabDrill` check only the active pack's manifest.

**EC-12: Export/import cycle corruption**
- When exporting bad sentences, the list is copied to clipboard as text. If the clipboard is cleared before pasting, the data is lost.
- Backup files written to `Downloads/BaseGrammy/` may be modified or deleted by the user. Restore validates file integrity before applying.
- Importing a pack with the same `packId` as an existing pack overwrites the old pack's lesson data but preserves progress (unless progress files are incompatible with the new schema).

**EC-13: Language switch mid-session**
- If the user switches language while a training session is active, the session should be paused and progress saved first.
- The Settings sheet pauses the session when opened. Switching language reloads lessons for the new language.
- The previous language's session state is preserved and can be resumed when the user switches back.

**EC-14: TTS model download interrupted**
- If the download is interrupted (network loss, app kill), the partial file remains in cache.
- On next attempt, the download restarts from the beginning (no resume support in current implementation).
- The progress bar resets to 0%. The background download indicator disappears.
- If extraction fails after download, the error state is shown and the user must retry.

**EC-15: Empty word bank (no chips generated)**
- If the correct answer is empty or the word bank word splitter produces no chips, the word bank mode falls back to keyboard input for that card.
- This prevents the user from being stuck on a card with no way to answer.

**EC-16: Simultaneous TTS model downloads for multiple languages**
- Background TTS downloads are tracked per language in `bgTtsDownloadStates` map.
- Multiple downloads can run concurrently.
- The thin progress bar at the top of the screen shows the aggregate progress.
- Each language's completion is tracked independently. Auto-play triggers only for the language currently in use.

**EC-17: Streak calculation across time zones**
- Streak is calculated based on calendar days (UTC or device local time). If the user practices at 11:59 PM and then at 12:01 AM, it counts as two consecutive days.
- `lastCompletionDateMs` in `StreakData` is compared against the current date.
- If the user's device clock changes (time zone travel, manual adjustment), the streak calculation may produce unexpected results. This is a known limitation.

**EC-18: Daily Practice cursor exhaustion**
- When the cursor advances through all cards in a lesson (`sentenceOffset + 10 > totalCards`), the sentence block returns empty.
- The composer should advance `currentLessonIndex` to the next lesson and reset `sentenceOffset` to 0.
- If all lessons are exhausted, the cursor wraps around to the first lesson.

**EC-19: Story quiz with no questions**
- If a `StoryQuiz` object has an empty `questions` list, the quiz completes immediately with 0/0 accuracy.
- Metrics are not saved for empty quizzes. The story node is marked as completed.
- This prevents blocking the user's lesson progression on missing story content.

**EC-20: Pack import with lessons declaring `type: "verb_drill"`**
- During import, lessons with `type: "verb_drill"` in the manifest are filtered out and NOT parsed as standard grammar lessons.
- Their CSV content is instead treated as verb drill data and copied to `drills/{packId}/verb_drill/`.
- If a pack declares `verbDrill.files` in the manifest but the referenced files are missing from the ZIP, the import succeeds but verb drill data is empty. No error is thrown; the Verb Drill tile simply does not appear.

**EC-21: TrainingViewModel exceeds line budget**
- The `TrainingViewModel` is explicitly acknowledged as a high-blast-radius file (3000+ lines).
- All new domain logic must be decomposed into helpers in `ui/helpers/` rather than added directly to the ViewModel.
- Helpers use the `TrainingStateAccess` interface and never call other helpers directly. Coordination flows through the ViewModel.
- Violation of this rule creates maintenance hazards and must be caught in code review.

**EC-22: Cyrillic path in project directory**
- The project path contains Cyrillic characters (`Разработка`).
- `android.overridePathCheck=true` in `gradle.properties` is required for the Gradle build to function.
- The Windows Gradle wrapper is broken and must use the multi-JAR classpath workaround: `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain`.
- This is a build-time concern and does not affect end users.

**EC-23: AtomicFileWriter failure during backup**
- If the atomic write fails (temp file creation, fsync, or rename), the original file is preserved.
- The backup operation should be retried. If it fails again, the user is shown an error with the specific file path.
- Backup integrity is validated by checking that all expected state files are present in the backup directory before committing the restore.

**EC-24: User name not set**
- If the user skips the welcome dialog without entering a name, the default "GrammarMateUser" is assigned.
- The welcome dialog reappears whenever the user navigates away from Home before setting a real name (triggered by `LaunchedEffect` checking `userName == "GrammarMateUser"` and `screen != AppScreen.HOME`).
- This nudges the user to personalize without blocking access.

**EC-25: Back navigation in daily practice**
- Pressing the system back button during Daily Practice navigates to Home (not to the previous block or card).
- The daily session is cancelled (not paused). Partial progress within blocks is lost.
- The cursor state is preserved, so the next daily session will resume from the saved cursor position.
