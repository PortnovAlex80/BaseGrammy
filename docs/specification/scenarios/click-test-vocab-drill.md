# Vocab Drill Click Test Scenario

## Purpose

This document provides a step-by-step click-test scenario for the Vocab Drill mode. It covers the complete user flow from entry through session completion, including all interactive elements, voice input, rating buttons, and edge cases.

## Preconditions

1. **App State:** GrammarMate app is launched and on HomeScreen
2. **Pack:** A lesson pack with `vocabDrill` section is installed and active
   - Example: `grammarmate/packs/italian-basics/` with `manifest.json` containing `"vocabDrill": { "files": [...] }`
3. **Data:** Vocab drill CSV files exist in `grammarmate/drills/{packId}/vocab_drill/`
4. **Audio:** TTS model downloaded (optional but recommended for full test coverage)
5. **Settings:** Voice auto-start may be ON or OFF (test both states)

---

## Test Scenario

### Phase 1: Entry — Selection Screen

#### Step 1.1: Navigate to Vocab Drill
| Action | Expected Result |
|--------|-----------------|
| Tap "Vocab Drill" tile on HomeScreen | VocabDrillScreen opens with loading indicator briefly, then Selection Screen appears |
| Observe screen title | "Flashcards" shown in top header |
| Observe back arrow | Back arrow visible in top-left corner |

#### Step 1.2: Verify Selection Screen Layout
| Action | Expected Result |
|--------|-----------------|
| Observe direction filter chips | Two chips visible: "IT -> RU" (selected by default) and "RU -> IT" |
| Observe POS filter chips | "All" chip (selected) + individual POS chips (Nouns, Verbs, Adj., Adv., Numbers, Pronouns) |
| Observe frequency chips | "Top 100", "Top 500", "Top 1000", "All" chips visible |
| Observe stats card | "Due: N / M" heading, "Mastered: X words" (if any mastered), per-POS breakdown, progress bar |
| Observe Start button | "Start (N due)" if due words exist, or disabled "No due words" if none |

#### Step 1.3: Test Direction Filter
| Action | Expected Result |
|--------|-----------------|
| Tap "RU -> IT" chip | Chip becomes selected, "IT -> RU" deselects |
| Verify due count updates | Due count recalculates for the new direction (may differ if mastery varies) |
| Tap "IT -> RU" chip | Returns to default direction |

#### Step 1.4: Test POS Filter
| Action | Expected Result |
|--------|-----------------|
| Tap "Nouns" chip | "All" deselects, "Nouns" selects, due count updates to show only noun count |
| Tap "Verbs" chip | "Nouns" deselects, "Verbs" selects, due count shows only verb count |
| Tap "All" chip | Returns to showing all POS (except numbers — see Note below) |

**Note:** When "All" is selected, numbers are excluded. To test numbers, explicitly select the "Numbers" POS chip.

#### Step 1.5: Test Frequency Filter
| Action | Expected Result |
|--------|-----------------|
| Tap "Top 100" chip | Due count updates to show count of words with rank 0-100 |
| Tap "Top 500" chip | Due count updates for rank 0-500 |
| Tap "All" chip | Returns to showing all frequency ranks |

#### Step 1.6: Test Pack Scoping (if multiple packs installed)
| Action | Expected Result |
|--------|-----------------|
| Go to Settings, switch active pack | Return to HomeScreen |
| Tap Vocab Drill tile again | Selection screen shows words from the NEW active pack only |
| Observe due count | Reflects mastery from `drills/{newPackId}/word_mastery.yaml` |

---

### Phase 2: Session Start — Card Selection

#### Step 2.1: Start Session with Due Words
| Action | Expected Result |
|--------|-----------------|
| Ensure filters are set (e.g., "All" POS, "All" frequency, "IT -> RU") | Due count shows N > 0 |
| Tap "Start (N due)" button | Card Screen appears with first card |
| Observe header | "Flashcards" title + "1/10" counter (or lower if fewer due words) |
| Observe progress bar | Linear progress indicator at top shows 1/10 filled |

#### Step 2.2: Attempt Start with No Due Words
| Action | Expected Result |
|--------|-----------------|
| Set filters to exclude all due words (e.g., select a POS with no due words) | Due count shows 0 |
| Observe Start button | Disabled, shows "No due words" |
| Tap Start button (attempt) | No action, button remains disabled |

---

### Phase 3: Card Front — Prompt Display

#### Step 3.1: Verify Card Front Layout (IT -> RU direction)
| Action | Expected Result |
|--------|-----------------|
| Observe POS badge | Colored chip (e.g., "noun" in primaryContainer color) |
| Observe rank badge | "#106" style chip (or appropriate rank) |
| Observe main word | Italian word displayed in large bold text (32sp) |
| Observe TTS button | Volume icon visible (right side of word) |
| Observe voice input area | "Tap to speak" label + large 72dp mic button |
| Tap TTS button | Italian word plays at 0.67x speed, button shows SPEAKING state briefly |

#### Step 3.2: Verify Card Front Layout (RU -> IT direction)
| Action | Expected Result |
|--------|-----------------|
| Go back to Selection Screen, tap "RU -> IT" | Direction chip changes |
| Tap Start button | Session begins with RU -> IT direction |
| Observe main word | Russian meaning displayed (e.g., "дом/жилище/семья") |
| Observe TTS button | **NOT visible** (TTS not shown on RU -> IT front) |
| Observe voice input area | "Tap to speak" label + mic button (recognizer will expect Italian) |

#### Step 3.3: Test Voice Auto-Start (if enabled in Settings)
| Action | Expected Result |
|--------|-----------------|
| Ensure voice auto-start is ON in Settings | |
| Start new session | After 500ms delay, voice recognizer launches automatically |
| Speak answer | Result captured without tapping mic |

#### Step 3.4: Test Manual Voice Input
| Action | Expected Result |
|--------|-----------------|
| Tap large mic button | Voice recognizer launches (ru-RU for IT->RU, it-IT for RU->IT) |
| Speak a wrong answer | Card background shows light red tint, "Try again (1/3)" appears |
| Speak another wrong answer | "Try again (2/3)" appears |
| Speak wrong answer 3rd time | "Moving on..." appears, voiceCompleted = true, auto-flip after 800ms |

#### Step 3.5: Test Skip Button
| Action | Expected Result |
|--------|-----------------|
| On fresh card (voice not completed), tap Skip | "Skipped" appears, card auto-flips after 800ms |
| After flip, go back (via Back arrow), start new session | |
| On fresh card, tap Flip button | Card flips immediately without voice input |

---

### Phase 4: Card Back — Answer Display

#### Step 4.1: Verify Card Back Layout (IT -> RU direction)
| Action | Expected Result |
|--------|-----------------|
| Flip card (via voice correct, skip, or Flip button) | Card back appears with secondary container background |
| Observe POS badge | Colored chip visible |
| Observe Italian word | 20sp semibold text |
| Observe Russian translation | 18sp medium text in primary color |
| Tap TTS button | Italian word plays at 0.67x speed |

#### Step 4.2: Verify Card Back Layout (RU -> IT direction)
| Action | Expected Result |
|--------|-----------------|
| Flip card in RU -> IT session | Card back appears |
| Observe Russian meaning | 16sp medium text with 0.7 alpha |
| Observe Italian word | 24sp bold text in primary color |
| Tap TTS button | Italian word plays at 0.67x speed |

#### Step 4.3: Verify Forms Section (for words with forms)
| Action | Expected Result |
|--------|-----------------|
| Find a card with forms (adjective, number, or pronoun) | "Forms" card visible on back |
| Observe form grid | 4-column grid: m sg, f sg, m pl, f pl (or relevant subset) |
| Verify form values | Actual form values shown (e.g., "solito", "solita", "soliti", "solite") |

#### Step 4.4: Verify Collocations Section
| Action | Expected Result |
|--------|-----------------|
| Find a card with collocations | "Collocations" label visible |
| Observe phrases | Up to 5 Italian phrases shown |
| If more than 5 exist | "+N more" text appears at bottom |

#### Step 4.5: Verify Mastery Indicator
| Action | Expected Result |
|--------|-----------------|
| Observe mastery label on card back | "Step X/9" where X = current step + 1, OR "Learned" if step >= 9 |
| Note the discrepancy | "Learned" label shows at step >= 9, but isLearned flag is set at step >= 3 |

---

### Phase 5: Rating — Answer Buttons

#### Step 5.1: Verify Rating Button Layout
| Action | Expected Result |
|--------|-----------------|
| Observe 4 rating buttons in 2x2 grid | Again (error, outlined), Hard (orange, outlined), Good (primary, filled), Easy (green, filled) |
| Observe interval labels | Again: "<1m", Hard: "X days" (current step), Good: "Y days" (current+1), Easy: "Z days" (current+2) |

#### Step 5.2: Test "Again" Rating
| Action | Expected Result |
|--------|-----------------|
| Tap "Again" button | Card advances to next, word's step resets to 0 |
| Check mastery (via Settings or next session) | Word is due immediately (<1m interval), incorrectCount incremented |

#### Step 5.3: Test "Hard" Rating
| Action | Expected Result |
|--------|-----------------|
| Tap "Hard" button | Card advances, word's step stays same |
| Check mastery | Word's next review = current step interval (e.g., 2 days), correctCount incremented |

#### Step 5.4: Test "Good" Rating
| Action | Expected Result |
|--------|-----------------|
| Tap "Good" button | Card advances, word's step +1 (clamped to max 9) |
| Check mastery | Word's next review = (step+1) interval, isLearned may become true if step >= 3 |

#### Step 5.5: Test "Easy" Rating
| Action | Expected Result |
|--------|-----------------|
| Tap "Easy" button | Card advances, word's step +2 (clamped to max 9) |
| Check mastery | Word's next review = (step+2) interval, likely reaches isLearned threshold |

#### Step 5.6: Verify Step Clamping
| Action | Expected Result |
|--------|-----------------|
| Rate a word at step 8 with "Easy" | Step advances to 9 (max), does not exceed 9 |
| Rate a word at step 9 with "Good" | Step stays at 9, next review is 56 days |

#### Step 5.7: Verify Learned Threshold
| Action | Expected Result |
|--------|-----------------|
| Rate a new word (step 0) with "Good" three times | After 3rd rating, step = 3, isLearned = true |
| Check mastery indicator on card back | Still shows "Step 4/9" (not "Learned" until step 9) |

---

### Phase 6: Session Completion

#### Step 6.1: Complete a Session
| Action | Expected Result |
|--------|-----------------|
| Rate all 10 cards | Completion screen appears after final rating |
| Wait 800ms | Stats animate in |
| Observe title | "Perfect!" if all correct, "Done!" otherwise |
| Observe stats card | Correct count (primary color) + incorrect count (error color) side by side |
| Observe subtitle | "N words reviewed" |

#### Step 6.2: Test Exit Button
| Action | Expected Result |
|--------|-----------------|
| Tap "Exit" button | Returns to Selection Screen |
| Observe due count | Updated to reflect new mastery states from session |

#### Step 6.3: Test Continue Button
| Action | Expected Result |
|--------|-----------------|
| From completion screen, tap "Continue" | New session starts with next batch of due words (up to 10) |
| Verify card selection | New cards are selected from remaining due words, sorted by rank |

---

### Phase 7: Bad Card Reporting

#### Step 7.1: Open Report Sheet
| Action | Expected Result |
|--------|-----------------|
| During active session, tap report icon (top-right) | ModalBottomSheet opens with card details |

#### Step 7.2: Verify Report Sheet Options
| Action | Expected Result |
|--------|-----------------|
| Observe card info at top | "essere -- быть/являться" (or current card) |
| Observe "Add to bad sentences list" option | Visible with ReportProblem icon |
| Observe "Export bad sentences to file" option | Visible with Download icon |
| Observe "Copy text" option | Visible with ContentCopy icon |

#### Step 7.3: Test Flag/Unflag
| Action | Expected Result |
|--------|-----------------|
| Tap "Add to bad sentences list" | Option text changes to "Remove from bad sentences list", report icon tint changes to error color |
| Tap "Remove from bad sentences list" | Option text changes back to "Add", icon tint returns to normal |

#### Step 7.4: Test Export
| Action | Expected Result |
|--------|-----------------|
| Tap "Export bad sentences to file" | File exported to Downloads/BadSentences/ |
| Observe result dialog | Shows success message with file path or error if none flagged |

#### Step 7.5: Test Copy Text
| Action | Expected Result |
|--------|-----------------|
| Tap "Copy text" | Formatted text copied to clipboard: ID, Word, Meaning |

---

## Verification Points

### Entry & Selection
- [ ] VocabDrillScreen opens from HomeScreen
- [ ] Selection screen shows all filter chips (direction, POS, frequency)
- [ ] Due count updates immediately when filters change
- [ ] Start button is disabled when no due words
- [ ] Pack scoping works — switching active pack changes word pool

### Card Selection
- [ ] Session starts with up to 10 cards
- [ ] Cards are due words sorted by rank (ascending)
- [ ] If fewer than 10 due words exist, session size matches due count
- [ ] If no due words, session does not start (session = null)

### Card Front
- [ ] POS badge shows correct part of speech
- [ ] Rank badge shows frequency rank
- [ ] Main word shows Italian (IT->RU) or Russian (RU->IT)
- [ ] TTS button visible only on IT->RU front
- [ ] Voice input area shows "Tap to speak" when not completed
- [ ] Card background changes based on voice result (green/red/neutral)

### Card Back
- [ ] Answer side shows translation (Italian + Russian in correct positions)
- [ ] Forms section appears for words with forms (adjectives, numbers, pronouns)
- [ ] Collocations section appears with up to 5 phrases
- [ ] Mastery indicator shows "Step X/9" or "Learned" (step >= 9)
- [ ] TTS button available on back for both directions

### Voice Input
- [ ] Mic button launches recognizer with correct language tag (ru-RU or it-IT)
- [ ] Correct answer auto-flips after 800ms
- [ ] Wrong answer allows up to 3 attempts with "Try again (N/3)" feedback
- [ ] After 3 wrong attempts, card auto-flips with "Moving on..."
- [ ] Skip button marks voice as SKIPPED and auto-flips

### Rating Buttons
- [ ] Four buttons appear after flip: Again, Hard, Good, Easy
- [ ] Buttons show correct intervals (<1m, X days, Y days, Z days)
- [ ] Again resets step to 0
- [ ] Hard keeps current step
- [ ] Good advances +1 step (clamped to 9)
- [ ] Easy advances +2 steps (clamped to 9)
- [ ] isLearned flag becomes true at step >= 3
- [ ] Mastery indicator shows "Learned" at step >= 9

### Completion
- [ ] Completion screen shows after all cards rated
- [ ] Stats animate in after 800ms delay
- [ ] Title is "Perfect!" (all correct) or "Done!" (mixed)
- [ ] Correct and incorrect counts displayed accurately
- [ ] Exit button returns to Selection Screen
- [ ] Continue button starts new session with remaining due words

### Bad Card Reporting
- [ ] Report icon visible on card screen header
- [ ] Report sheet shows card details
- [ ] Flag/unflag toggles correctly
- [ ] Export creates file in Downloads/BadSentences/
- [ ] Copy text copies formatted string to clipboard

### Pack-Scoped Mastery
- [ ] Mastery stored in `drills/{packId}/word_mastery.yaml`
- [ ] Switching packs loads different mastery data
- [ ] Progress in standalone drill affects daily practice selection (and vice versa)

---

## Edge Cases & Error Handling

### Empty Word Set
| Condition | Expected Behavior |
|-----------|-------------------|
| No CSV files in pack | Selection screen shows "No words loaded", Start button disabled |
| All words mastered (no due) | Due count shows 0, Start button disabled, progress bar at 100% |
| No words match filter (e.g., POS with 0 words) | Due count shows 0, Start button disabled |

### Voice Recognition Errors
| Condition | Expected Behavior |
|-----------|-------------------|
| RecognizerIntent not available | Mic button visible but tapping shows error or does nothing |
| Recognition returns empty result | Counts as wrong attempt, shows "Try again (N/3)" |
| TTS not initialized | TTS button shows INITIALIZING state briefly, then ERROR or IDLE |

### Data Persistence
| Condition | Expected Behavior |
|-----------|-------------------|
| App killed mid-session | Mastery for rated cards persisted; session not restored (user starts new session) |
| Storage write fails | Graceful degradation; in-memory state preserved, error logged |

### Direction-Specific Behavior
| Condition | Expected Behavior |
|-----------|-------------------|
| RU -> IT with null meaningRu | Card front shows "?" |
| Word with no forms | Forms section not shown |
| Word with no collocations | Collocations section not shown |

---

## Known Discrepancies (Documented for Reference)

1. **isLearned threshold:** Data model uses `LEARNED_THRESHOLD = 3` (step >= 3), but card back shows "Learned" only at step >= 9. A word at step 5 is `isLearned = true` but displays "Step 6/9".

2. **Forms display hardcoding:** Card back forms grid hardcodes adjective keys (msg, fsg, mpl, fpl). Numbers (form_m, form_f) and pronouns (form_sg_m, form_sg_f, form_pl_m, form_pl_f) show dashes instead of actual form values.

3. **Spec inconsistency:** Some specs (01, 02, 18) state `isLearned` is true at step 9, but actual code uses step 3. Spec 11 correctly documents the threshold.

---

## Test Data Recommendations

For comprehensive testing, use a pack with:
- At least 6 POS types: nouns, verbs, adjectives, adverbs, numbers, pronouns
- Words with forms (adjectives with msg/fsg/mpl/fpl)
- Words with collocations (5+ phrases to test overflow)
- Words at various mastery steps (0, 3, 9) to verify indicator display
- Pack with `vocabDrill` section in manifest

---

## Related Documents

- `11-vocab-drill.md` — Full Vocab Drill specification
- `scenario-08-vocab-drill.md` — Standalone Vocab Drill session trace
- `22-use-case-registry.md` — User stories US-11.1 through US-11.16
- `23-screen-elements.md` — VocabDrillScreen element invariants
