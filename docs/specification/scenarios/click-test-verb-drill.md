# Verb Drill Click Test Scenario

## Purpose
Comprehensive click-test scenario for Verb Drill mode covering entry, session flow, answer validation, retry/hint flow, progress tracking, reference sheets, bad sentence reporting, pack-scoped progress, and UI elements.

## Preconditions

1. **App state:** App is launched, user is on Home Screen
2. **Content:** Active lesson pack has `verbDrill` section in manifest.json
3. **Progress:** No verb drill progress exists (fresh state) OR existing progress to verify persistence
4. **Settings:** Voice input enabled, TTS models downloaded
5. **Device:** Android emulator or physical device with ADB connection

---

## Test Sections

### SECTION 1: Entry to Verb Drill (Selection Screen)

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 1.1 | Launch app, wait for Home Screen | Home Screen renders with drill tiles | Verb Drill tile visible (if pack has verbDrill) |
| 1.2 | Tap Verb Drill tile | Navigation to `VERB_DRILL` screen | Selection screen appears, loading indicator briefly |
| 1.3 | Wait for loading to complete | `isLoading = false`, UI renders | Tense dropdown visible (if tenses exist), Group dropdown visible (if groups exist) |
| 1.4 | Check progress display | Shows `everShownCount / totalCards` and `todayShownCount` | Example: "Прогресс: 247 / 412", "Сегодня: 30" |
| 1.5 | Tap Tense dropdown | Dropdown expands with options | "Все времена" + each available tense listed |
| 1.6 | Select "Presente" | Dropdown closes, `selectedTense = "Presente"` | Progress updates to Presente-only counts |
| 1.7 | Tap Group dropdown | Dropdown expands with options | "Все группы" + each available group listed |
| 1.8 | Select "regular_are" | Dropdown closes, `selectedGroup = "regular_are"` | Progress updates to regular_are + Presente combo counts |
| 1.9 | Tap "По частотности" checkbox | `sortByFrequency` toggles to `true` | Checkbox checked |
| 1.10 | Tap "По частотности" checkbox again | `sortByFrequency` toggles to `false` | Checkbox unchecked |
| 1.11 | Tap Back arrow | Navigation to previous screen (HOME) | Returns to Home Screen |

### SECTION 2: Session Start (10 Card Batch)

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 2.1 | Navigate to Verb Drill (repeat 1.2-1.3) | Selection screen loaded | Ready to start session |
| 2.2 | Verify Start button text | "Старт" if `todayShownCount == 0`, "Продолжить" if > 0 | Button text matches today count |
| 2.3 | Tap "Старт" / "Продолжить" button | `startSession()` called, session created | Session screen appears, first card shown |
| 2.4 | Verify card display | Russian prompt visible, verb/tense chips shown | Card content renders correctly |
| 2.5 | Verify progress indicator | Shows "1 / 10" or similar | `currentIndex + 1 / cards.size` |
| 2.6 | Verify navigation bar | Previous, Next, Pause/Play, Exit buttons visible | All nav controls present |

### SECTION 3: Card Display Elements

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 3.1 | Observe Russian prompt | Text in 20sp semi-bold, RU label | "я готов" or similar shown |
| 3.2 | Tap volume button (TTS) | TTS speaks the Italian answer | Audio plays correct pronunciation |
| 3.3 | Tap verb chip (e.g., "essere #1") | Verb reference bottom sheet opens | Shows conjugation table for essere |
| 3.4 | Swipe down to close verb sheet | Bottom sheet closes | Returns to card view |
| 3.5 | Tap tense chip (e.g., "Pres.") | Tense info bottom sheet opens | Shows formula, usage, examples |
| 3.6 | Swipe down to close tense sheet | Bottom sheet closes | Returns to card view |
| 3.7 | Verify font scaling (if enabled) | Prompt text scales with `ruTextScale` | Text size matches settings (1.0x, 1.5x, 2.0x) |

### SECTION 4: Answer Validation (Correct Answer)

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 4.1 | Type correct answer in text field | Input text appears | Text field shows typed answer |
| 4.2 | Tap "Check" button | `submitAnswerWithInput()` called | Validation occurs |
| 4.3 | Correct answer submitted | Green "Correct" feedback shown | `pendingAnswerResult.correct = true` |
| 4.4 | Wait 500ms (if VOICE mode) | Auto-advance to next card | `currentIndex` increments |
| 4.5 | Tap "Next" button (manual advance) | Moves to next card | Card 2 shown |
| 4.6 | Verify progress | Shows "2 / 10" | Progress indicator updated |

### SECTION 5: Answer Validation (Wrong Answer - Retry Flow)

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 5.1 | Type incorrect answer | Input text appears | Text field shows wrong answer |
| 5.2 | Tap "Check" button | Validation fails, wrong answer | Red "Incorrect" feedback shown |
| 5.3 | Verify attempts counter | Shows "2 attempts left" | `remainingAttempts = 2` |
| 5.4 | Type another wrong answer | Input text appears | Text field updated |
| 5.5 | Tap "Check" button | Second wrong answer | Shows "1 attempts left" |
| 5.6 | Type third wrong answer | Input text appears | Text field updated |
| 5.7 | Tap "Check" button | Third wrong answer triggers hint | Answer shown as red-tinted card |

### SECTION 6: Auto-Hint After 3 Wrong Attempts

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 6.1 | After 3rd wrong answer | Hint card appears with red background | `hintAnswer` set to correct answer |
| 6.2 | Verify hint card content | Shows "Answer: {correct answer}" in red | Answer text visible |
| 6.3 | Verify TTS button on hint | Red-tinted volume button | Can replay answer audio |
| 6.4 | Verify input controls | Still visible below hint card | Can still type/interact |
| 6.5 | Verify pause/play button | Shows Play icon | Session paused (`isPaused = true`) |
| 6.6 | Tap Play button | Advances to next card | `nextCard()` called |
| 6.7 | Verify progress | `incorrectCount` incremented | Stats updated |

### SECTION 7: Manual Show Answer (Eye Button)

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 7.1 | Navigate to new card | Fresh card shown | No hint visible |
| 7.2 | Tap eye (show answer) button | `showAnswer()` called | Hint card appears immediately |
| 7.3 | Verify incorrect attempts | Set to 3 (bypasses retry) | `incorrectAttempts = 3` |
| 7.4 | Verify session paused | `isPaused = true` | Play button shown |
| 7.5 | Tap Play button | Advances to next card | Moves forward |
| 7.6 | Verify eye button state | Disabled when hint already shown | Button grayed out during hint |

### SECTION 8: Input Mode Switching

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 8.1 | Verify default mode | VOICE mode active | Mic icon highlighted |
| 8.2 | Tap Keyboard button | Switches to KEYBOARD mode | Keyboard icon highlighted |
| 8.3 | Type answer and submit | Works in KEYBOARD mode | No auto-advance |
| 8.4 | Tap Word Bank (Book) button | Switches to WORD_BANK mode | Word chips appear |
| 8.5 | Tap words in order | Words selected as chips | Selected count updates |
| 8.6 | Tap "Undo" button | Last selected word removed | Selection count decreases |
| 8.7 | Submit word bank answer | Validates joined words | Works correctly |
| 8.8 | Tap Mic button | Returns to VOICE mode | Mic icon highlighted |

### SECTION 9: Voice Input Auto-Start

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 9.1 | Ensure VOICE mode active | Mic icon highlighted | Ready for voice input |
| 9.2 | Navigate to new card | Auto-launch after 500ms delay | Speech recognition dialog appears |
| 9.3 | Speak correct answer | Voice recognition processes | Answer submitted |
| 9.4 | Correct voice answer | Auto-advance after 500ms | Moves to next card automatically |
| 9.5 | Wrong voice answer | Auto-retry voice recognition | Dialog re-appears |

### SECTION 10: Navigation (Previous/Next/Exit)

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 10.1 | On card 3, tap "Previous" button | Returns to card 2 | `currentIndex` decrements |
| 10.2 | Verify pending state cleared | No feedback shown | Card appears fresh |
| 10.3 | Tap "Next" button | Advances to card 3 | `currentIndex` increments |
| 10.4 | Tap Exit button (nav bar) | Confirmation dialog appears | "Exit session?" prompt |
| 10.5 | Confirm exit | Session cleared, navigate to HOME | Returns to Home Screen |

### SECTION 11: Progress Persistence (Per-Combo)

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 11.1 | Complete 3 cards in Presente + regular_are | Progress persisted | `everShownCardIds` updated |
| 11.2 | Exit session, return to selection | Progress shows today count | `todayShownCount = 3` |
| 11.3 | Change filter to Imperfetto | Progress resets for new combo | Counts reflect Imperfetto combo |
| 11.4 | Return to Presente + regular_are | Previous progress preserved | `todayShownCount = 3` still |
| 11.5 | Start new session | Cards shown today excluded | Fresh cards from remaining pool |

### SECTION 12: Session Completion

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 12.1 | Complete all 10 cards | Completion screen appears | Stats shown |
| 12.2 | Verify stats display | "Правильных: X, Ошибок: Y" | Counts match session |
| 12.3 | Verify "Ещё" button | Visible if not `allDoneToday` | Can start next batch |
| 12.4 | Tap "Ещё" button | New 10-card batch starts | Fresh cards loaded |
| 12.5 | Complete second batch | Completion screen appears | Stats updated |
| 12.6 | Tap "Выход" button | Session cleared, navigate to HOME | Returns to Home Screen |

### SECTION 13: All Done Today State

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 13.1 | Practice all cards for a combo | Pool exhausted | `allDoneToday = true` |
| 13.2 | Return to selection screen | "На сегодня всё!" message shown | Start button hidden |
| 13.3 | Change to different combo | Cards available | Start button visible |
| 13.4 | Return to exhausted combo | Still shows "all done" | State persists |

### SECTION 14: Bad Sentence Reporting

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 14.1 | On a card, tap flag button | Report bottom sheet opens | 4 options shown |
| 14.2 | Verify options list | Flag/unflag, Hide, Export, Copy | All 4 options present |
| 14.3 | Tap "Add to bad sentences list" | Card flagged | Flag button shows red tint |
| 14.4 | Tap flag button again | Sheet shows "Remove from list" | State updated |
| 14.5 | Tap "Remove from list" | Card unflagged | Flag button normal tint |
| 14.6 | Tap "Export bad sentences" | File export confirmation | File path shown |
| 14.7 | Tap "Copy text" | Card text copied to clipboard | Clipboard has content |
| 14.8 | Tap "Hide this card from lessons" | Toast/snackbar shown | "Feature not yet available" |

### SECTION 15: Pack-Scoped Progress

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 15.1 | Practice cards in Pack A | Progress saved to Pack A store | `drills/{packA}/verb_drill_progress.yaml` |
| 15.2 | Switch to Pack B (different pack) | Progress independent | Pack B has zero progress |
| 15.3 | Practice cards in Pack B | Progress saved to Pack B store | Separate progress file |
| 15.4 | Switch back to Pack A | Original progress preserved | Counts match Pack A practice |

### SECTION 16: TTS Functionality

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 16.1 | Tap volume button on card | TTS speaks Italian answer | Audio plays |
| 16.2 | Tap volume button on hint card | TTS replays answer | Audio plays |
| 16.3 | Open verb reference sheet | TTS button speaks infinitive | Slower speed (0.8x) |
| 16.4 | Tap TTS button multiple times | Audio restarts each time | No overlapping audio |

### SECTION 17: Sort by Frequency

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 17.1 | Enable "По частотности" | Cards sorted by rank ascending | Low rank (common verbs) first |
| 17.2 | Start session | First 10 cards are lowest rank | Verify rank values |
| 17.3 | Disable "По частотности" | Cards shuffled randomly | Different order each session |

### SECTION 18: Weak-First Ordering (Daily Practice)

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 18.1 | Start Daily Practice | Block 3 contains verb cards | 10 conjugation cards |
| 18.2 | Verify weak-first selection | Cards from weak tenses prioritized | Based on `shownInCombo / comboTotal` |
| 18.3 | Complete Daily Practice | Progress persisted to VerbDrillStore | Same file as standalone drill |

### SECTION 19: Font Scaling

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 19.1 | Go to Settings, set text scale to 1.5x | `ruTextScale = 1.5f` | Setting saved |
| 19.2 | Return to Verb Drill session | Prompt text larger | 20sp * 1.5 = 30sp |
| 19.3 | Verify chips not scaled | Verb/tense chips same size | Only prompt scaled |
| 19.4 | Set text scale to 2.0x | Prompt text very large | 20sp * 2.0 = 40sp |

### SECTION 20: Exit Navigation (All Paths)

| Step | Action | Expected Result | Verification |
|------|--------|----------------|--------------|
| 20.1 | Tap back arrow (session screen) | Clears session, navigates to HOME | On HOME screen |
| 20.2 | Start session, tap nav bar Exit | Confirmation dialog | "Exit session?" |
| 20.3 | Confirm | Clears session, navigates to HOME | On HOME screen |
| 20.4 | Complete session, tap Exit button | Clears session, navigates to HOME | On HOME screen |
| 20.5 | Press system back button | Native back handling | Returns to HOME |

---

## Verification Points Summary

### Functionality Verification

| Feature | Pass Criteria | Test Section |
|---------|--------------|--------------|
| Entry navigation | Verb Drill tile navigates to selection screen | 1 |
| Filter selection | Tense and group dropdowns work | 1 |
| Progress display | Counts update per combo selection | 1, 11 |
| Session start | 10-card batch created | 2 |
| Card display | Prompt, chips, TTS render correctly | 3 |
| Answer validation | Normalization works, correct/wrong detected | 4, 5 |
| Retry flow | 3 attempts, then auto-hint | 5, 6 |
| Manual hint | Eye button shows answer immediately | 7 |
| Input modes | VOICE/KEYBOARD/WORD_BANK switch | 8 |
| Voice auto-start | Launches on new card (500ms delay) | 9 |
| Voice auto-advance | Correct answer advances (500ms delay) | 9 |
| Navigation | Prev/Next/Exit work | 10 |
| Progress persistence | Per-combo tracking, ever/today IDs | 11 |
| Session completion | Stats shown, "More" starts new batch | 12 |
| All done state | "На сегодня всё!" when exhausted | 13 |
| Bad sentences | Flag/unflag/export/copy work | 14 |
| Pack-scoped | Independent progress per pack | 15 |
| TTS | Plays audio for cards and hints | 16 |
| Sort by frequency | Low-rank verbs first when enabled | 17 |
| Font scaling | Prompt text scales, chips don't | 19 |
| Exit navigation | All paths navigate to HOME | 20 |

### UI Element Verification

| Element | Expected Behavior | Test Section |
|---------|------------------|--------------|
| TenseDropdown | Shows all tenses + "Все времена" | 1 |
| GroupDropdown | Shows all groups + "Все группы" | 1 |
| Frequency checkbox | Toggles sortByFrequency | 1 |
| Start button | "Старт" or "Продолжить" based on today count | 2 |
| Verb chip | Opens verb reference sheet | 3 |
| Tense chip | Opens tense info sheet | 3 |
| Volume button | Plays TTS (Italian answer) | 3, 16 |
| Text field | Accepts keyboard input | 4 |
| Check button | Validates input | 4 |
| Eye button | Shows answer hint | 7 |
| Flag button | Opens report sheet | 14 |
| Nav bar | Previous, Next, Pause/Play, Exit | 10 |
| Progress indicator | Shows current/total | 2 |
| Hint card | Red-tinted, shows answer | 6 |

### Data Verification

| Data Point | Expected Value | Verification Method |
|------------|----------------|-------------------|
| `everShownCardIds` | Grows monotonically, never resets | Check progress file after sessions |
| `todayShownCardIds` | Resets when date changes | Compare progress across days |
| `correctCount` | Increments on correct answers | Session stats |
| `incorrectCount` | Increments on hints/skips | Session stats |
| `sortByFrequency` | Toggles boolean | Checkbox state |
| `allDoneToday` | True when combo exhausted | Selection screen message |
| Pack ID | Correctly scopes progress | File path check |

---

## Known Issues (from spec audit)

| Issue | Severity | Description |
|-------|----------|-------------|
| TTS speaks Italian not Russian | Low | Spec says TTS speaks prompt, code speaks answer |
| Hide card button is no-op | Low | Button visible but not functional for VerbDrill |
| Exit navigation | Medium | In-app exits don't navigate to HOME (TASK-007) |

---

## Test Execution Notes

1. **Automation:** Can be automated via mobile-mcp (Maestro flows in `.maestro/flows/05-verb-drill.yaml`)
2. **Manual testing:** Use emulator/device with screen recording for evidence
3. **Regression:** Re-run after any changes to VerbDrillViewModel, VerbDrillScreen, or VerbDrillCardSessionProvider
4. **Cross-pack testing:** Test with multiple lesson packs to verify pack-scoped isolation
5. **Daily reset testing:** Change device date to verify `todayShownCardIds` reset

---

## References

- Spec: `docs/specification/10-verb-drill.md`
- Scenario trace: `docs/specification/scenario-07-verb-drill.md`
- UI elements: `docs/specification/23-screen-elements.md`
- Use cases: `docs/specification/22-use-case-registry.md` (US-10.1 through US-10.19)
