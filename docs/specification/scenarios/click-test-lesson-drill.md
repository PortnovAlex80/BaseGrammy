# Lesson Drill Click Test Scenario

## Overview

This click test scenario verifies the complete user journey for the Lesson Drill sub-mode feature. Drill sub-mode provides additional practice on lesson-specific sentence cards within TrainingScreen. Unlike standalone VerbDrill, drill sub-mode has no selection screen — the lesson pack author pre-curates `lesson.drillCards` for this lesson's theme and tense.

**Test Scope:** Entry → Start Dialog → Card Practice Loop → Completion → Exit

**Related Specifications:**
- `scenario-16-drill-sublesson.md` — Full behavioral specification
- `08-training-viewmodel.md` — Session management
- `23-screen-elements.md` — UI element invariants

---

## Preconditions

### Device State
- App installed and launched
- User logged in (profile exists)
- TTS/ASR models downloaded (if using VOICE mode)

### Data State
- At least one lesson pack imported with `drillFile` in manifest
- Lesson has non-empty `lesson.drillCards` array
- `hasDrill == true` for the lesson
- Lesson is visible on LessonRoadmapScreen

### Starting Screen
- User is on `HomeScreen`
- Can navigate to `LessonRoadmapScreen` for a lesson with drill cards

---

## Test Data

### Sample Lesson with Drill
```json
{
  "packId": "italian-lesson-01",
  "lessons": [
    {
      "id": "lesson-01",
      "title": "Present Tense Basics",
      "drillFile": "drill_lesson01.csv",
      "allCards": [...], // 150+ cards
      "drillCards": [
        {
          "id": "drill-001",
          "promptRu": "я говорю (dire) правду (verità)",
          "answers": "dico la verità",
          "tense": "Presente",
          "group": "io"
        },
        // ... 10-20 drill cards total
      ]
    }
  ]
}
```

**Key Test Cases:**
- Parenthetical hints: `я говорю (dire) правду (verità)` → should display as `я говорю правду`
- Multiple drill cards: session continues through ALL cards (not just 10)
- Progress persistence: exit mid-session → resume at saved position

---

## Test Steps

### Step 1: Navigate to Lesson Roadmap

| Action | Expected Result |
|--------|-----------------|
| 1.1 Tap lesson card on HomeScreen | LessonRoadmapScreen opens |
| 1.2 Verify lesson title displayed | Correct lesson name shown |
| 1.3 Verify Drill tile visible | FitnessCenter icon + "Drill" label visible |
| 1.4 Verify Drill tile enabled | Tile is clickable (not grayed out) |

**Verification Points:**
- Drill tile uses `primaryContainer` background color when enabled
- FitnessCenter icon uses `primary` tint color
- Tile positioned first in grid (before training sub-lessons)

---

### Step 2: Tap Drill Tile — Show Start Dialog (First Time)

| Action | Expected Result |
|--------|-----------------|
| 2.1 Tap Drill tile | `DrillStartDialog` appears |
| 2.2 Verify dialog title | "Drill Practice" or equivalent |
| 2.3 Verify dialog prompt | "Practice lesson-specific sentences" or equivalent |
| 2.4 Verify "Start" button | Primary action button visible |
| 2.5 Verify "Cancel" button | Dismiss button visible |
| 2.6 Verify "Continue" button | NOT visible (no progress yet) |
| 2.7 Verify "Start Fresh" button | NOT visible (no progress yet) |

**Verification Points:**
- `drillShowStartDialog == true` in state
- `drillHasProgress == false` (first-time entry)
- Dialog blocks interaction with underlying screen

---

### Step 3: Start Drill Session (Fresh Start)

| Action | Expected Result |
|--------|-----------------|
| 3.1 Tap "Start" button | Dialog dismisses |
| 3.2 TrainingScreen opens | Screen in drill mode |
| 3.3 Verify first card loaded | Russian prompt displayed |
| 3.4 Verify parenthetical hints stripped | Prompt shows `я говорю правду` NOT `я говорю (dire) правду (verità)` |
| 3.5 Verify green visual indicators | Prompt text in `DrillPromptGreen` color |
| 3.6 Verify tense label in green | Tense label (if present) in `DrillTenseLabelGreen` color |
| 3.7 Verify session paused | Play button visible, card not advancing automatically |
| 3.8 Verify progress indicator | Shows "1 / [total drill cards]" |

**Verification Points:**
- `isDrillMode == true` in state
- `drillCardIndex == 0`
- `drillTotalCards == lesson.drillCards.size` (NOT capped at 10)
- `sessionState == PAUSED`
- `currentCard` is first drill card
- Parenthetical regex applied: `\s*\([^)]+\)` stripped from prompt

---

### Step 4: Answer Card — Correct Answer

| Action | Expected Result |
|--------|-----------------|
| 4.1 Tap Play button (if paused) | Session becomes ACTIVE |
| 4.2 Type correct answer: "dico la verità" | Answer submitted |
| 4.3 Verify "Correct" feedback | Green checkmark / success message |
| 4.4 Verify Next button appears | Can advance to next card |
| 4.5 (VOICE mode) Auto-advance after 200ms | Moves to next card automatically |

**Verification Points:**
- Answer validation via `AnswerValidator`
- `lastResult == CORRECT` in state
- `incorrectAttemptsForCard` reset to 0
- Mastery NOT counted (flowers unchanged)

---

### Step 5: Answer Card — Incorrect Answer (Retry)

| Action | Expected Result |
|--------|-----------------|
| 5.1 Type incorrect answer: "dico vero" | Answer submitted |
| 5.2 Verify "Incorrect" feedback | Error message shown |
| 5.3 Verify retry allowed | Can attempt again |
| 5.4 Type correct answer: "dico la verità" | Answer submitted |
| 5.5 Verify "Correct" feedback | Success message |
| 5.6 Verify Next button | Can advance |

**Verification Points:**
- `incorrectAttemptsForCard` incremented
- After 3 incorrect attempts: hint shown in pink card
- Session pauses after hint shown

---

### Step 6: Navigate Through All Drill Cards

| Action | Expected Result |
|--------|-----------------|
| 6.1 Tap Next button | Moves to card 2 |
| 6.2 Verify progress updates | Shows "2 / [total]" |
| 6.3 Repeat for ALL drill cards | Continue until last card |
| 6.4 Verify progress saved after each card | `drillProgressStore.saveDrillProgress()` called |

**Verification Points:**
- `drillCardIndex` increments with each card
- Progress indicator: `drillCardIndex + 1 / drillTotalCards`
- All drill cards loaded (NOT capped at 10 like normal sub-lessons)
- Progress persists: can exit and resume at current position

---

### Step 7: Drill Completion

| Action | Expected Result |
|--------|-----------------|
| 7.1 Answer last drill card correctly | Final card completed |
| 7.2 Tap Next on last card | Session detects completion |
| 7.3 Verify return to LessonRoadmapScreen | Back on lesson roadmap |
| 7.4 Verify drill progress cleared | `drillProgressStore` cleared for this lesson |
| 7.5 Verify flower states refreshed | Flowers recalculated |
| 7.6 Verify mastery NOT increased | Flower states unchanged |

**Verification Points:**
- `isDrillMode == false` in state
- `drillCardIndex == 0`, `drillTotalCards == 0`
- `subLessonFinishedToken` incremented
- Drill completion does NOT affect mastery/flowers
- `PracticeType.SUB_DRILL` fire streak awarded (if English pack)

---

### Step 8: Exit Drill Mid-Session (Resume Test)

| Action | Expected Result |
|--------|-----------------|
| 8.1 Start drill fresh (repeat Step 3) | Drill session starts |
| 8.2 Answer 3 cards correctly | Progress at card 4 |
| 8.3 Tap Exit (StopCircle icon) | ExitConfirmationDialog appears |
| 8.4 Confirm exit | Session ends |
| 8.5 Verify back on LessonRoadmapScreen | Returned to lesson |
| 8.6 Tap Drill tile again | Start dialog shows |
| 8.7 Verify "Continue" button visible | Can resume from card 4 |
| 8.8 Verify "Start Fresh" button visible | Can restart from beginning |

**Verification Points:**
- `drillProgressStore.saveDrillProgress(lessonId, currentIndex)` called on exit
- `drillCardIndex == 3` saved (0-indexed, after 3 cards)
- Resume starts at saved position
- "Start Fresh" resets `drillCardIndex == 0`

---

### Step 9: Resume Drill Session

| Action | Expected Result |
|--------|-----------------|
| 9.1 Tap "Continue" button | Resumes from card 4 |
| 9.2 Verify correct card loaded | 4th drill card displayed |
| 9.3 Verify progress indicator | Shows "4 / [total]" |
| 9.4 Complete remaining cards | Finish drill normally |

**Verification Points:**
- `startDrill(resume=true)` called
- `drillProgressStore.getDrillProgress(lessonId)` used for start index
- Session state restored correctly

---

### Step 10: Input Mode Switching (Drill Mode)

| Action | Expected Result |
|--------|-----------------|
| 10.1 Start drill session | Drill mode active |
| 10.2 Tap input mode toggle | Switches VOICE → KEYBOARD → WORD_BANK |
| 10.3 Verify mode works | Each input mode functional |
| 10.4 Verify visual consistency | Green indicators remain in all modes |
| 10.5 Verify answer validation | Works in all input modes |

**Verification Points:**
- Input mode switching works identically to normal training
- Voice auto-trigger: 200ms delay after card load
- Word bank generated from drill card answers
- No mode-specific restrictions in drill

---

### Step 11: Parenthetical Hint Stripping Verification

| Action | Expected Result |
|--------|-----------------|
| 11.1 Load drill card with hints | Card: `я говорю (dire) правду (verità)` |
| 11.2 Verify displayed prompt | Shows: `я говорю правду` |
| 11.3 Verify no parentheses in prompt | No `(dire)` or `(verità)` visible |
| 11.4 Test multiple hint formats | All parenthetical content stripped |

**Test Cases:**
- Single hint: `word (translation)`
- Multiple hints: `word1 (trans1) word2 (trans2)`
- Nested parentheses (if any)
- Whitespace handling: `word (trans)` vs `word(trans)`

**Verification Points:**
- Regex `\s*\([^)]+\)` applied correctly
- Hints stripped regardless of difficulty setting
- Original card data unchanged (only display modified)

---

## Verification Points Summary

### State Verification

| State Property | Expected Value | Check Method |
|----------------|----------------|--------------|
| `isDrillMode` | `true` when in drill, `false` otherwise | State inspection |
| `drillCardIndex` | Current card index (0-based) | Progress indicator |
| `drillTotalCards` | Total drill cards (uncapped) | Progress indicator |
| `drillShowStartDialog` | `true` when dialog visible | UI inspection |
| `drillHasProgress` | `true` if resume available | Dialog button visibility |
| `sessionState` | `PAUSED` on start, `ACTIVE` after play | Play/Pause button state |

### Visual Verification

| Visual Element | Expected Appearance | Check Method |
|----------------|-------------------|--------------|
| Drill tile | FitnessCenter icon, "Drill" label, primaryContainer background | UI inspection |
| Tense label | `DrillTenseLabelGreen` color (green) | Color inspection |
| Prompt text | `DrillPromptGreen` color (green), no parentheses | Text inspection |
| Progress indicator | "X / Y" format, Y = total drill cards | Text inspection |
| Start dialog | Title, prompt, Start/Continue/Start Fresh/Cancel buttons | UI inspection |

### Behavioral Verification

| Behavior | Expected Result | Check Method |
|----------|----------------|--------------|
| Card count | ALL drill cards (not capped at 10) | Count cards in session |
| Mastery | NOT counted (flowers unchanged) | Compare flower states |
| Progress persistence | Saved on each advance/exit | Exit → resume test |
| Parenthetical stripping | All `(...)` removed from prompt | Text inspection |
| Completion | Returns to LessonRoadmapScreen | Navigation check |
| Fire streak | `PracticeType.SUB_DRILL` awarded | Streak inspection |

---

## Edge Cases to Test

### Empty Drill Cards
| Action | Expected Result |
|--------|-----------------|
| Start drill with empty `drillCards` | `startDrill()` returns early, no session started |
| Tap drill tile with no cards | Toast message or no action |

### Single Card Drill
| Action | Expected Result |
|--------|-----------------|
| Start drill with 1 card | Session starts normally |
| Answer correctly | Completion triggered on first advance |

### Voice Mode Auto-Trigger
| Action | Expected Result |
|--------|-----------------|
| Switch to VOICE mode | RecognizerIntent launches after 200ms |
| Complete voice input | Auto-advance to next card |

### Word Bank Generation
| Action | Expected Result |
|--------|-----------------|
| Switch to WORD_BANK mode | Words generated from drill card answers |
| Select words | Answer submitted as word-separated string |

### Report/Flag in Drill Mode
| Action | Expected Result |
|--------|-----------------|
| Tap Report/Flag button | Card flagged, advances to next card |
| Flagged card handling | Skipped in current session |

---

## Test Automation Notes

### Maestro Flow Structure

```yaml
# click-test-lesson-drill.yaml
appId: com.alexpo.grammermate
---
- launchApp
- assertVisible: "GrammarMate"
- tapOn: "Lesson 01"  # Navigate to roadmap
- assertVisible: "Present Tense Basics"
- tapOn: "Drill"  # Drill tile
- assertVisible: "Drill Practice"
- assertVisible: "Start"
- tapOn: "Start"
- assertVisible: "я говорю правду"  # Parenthetical hints stripped
- assertVisible: "Presente"  # Tense label in green
- tapOn: "Play"  # Start session
- tapOn: "Keyboard input mode"  # Switch to keyboard
- inputText: "dico la verità"
- pressKey: Enter
- assertVisible: "Correct"
- tapOn: "Next"
- assertVisible: "2 / [total]"  # Progress updated
# Continue for all drill cards...
- assertVisible: "All complete"  # Back on roadmap
```

### mobile-mcp Commands

```bash
# Take screenshot for visual verification
mobile_take_screenshot

# List elements to find drill tile
mobile_list_elements_on_screen

# Click drill tile
mobile_click_on_screen_at_coordinates(x, y)
```

---

## Related Use Cases

From `22-use-case-registry.md`:
- **UC-DRILL-001:** User starts drill practice from lesson roadmap
- **UC-DRILL-002:** System loads all drill cards (not capped)
- **UC-DRILL-003:** System strips parenthetical hints from prompts
- **UC-DRILL-004:** System saves drill progress for resumption
- **UC-DRILL-005:** System completes drill and returns to roadmap
- **UC-DRILL-006:** Mastery NOT counted for drill answers

---

## Sign-Off Criteria

### Must Pass
- [ ] Drill tile visible and clickable on LessonRoadmapScreen
- [ ] Start dialog appears with correct buttons (Start vs Continue)
- [ ] All drill cards loaded (not capped at 10)
- [ ] Parenthetical hints stripped from prompts
- [ ] Green visual indicators applied (tense label, prompt text)
- [ ] Progress saved and resumed correctly
- [ ] Completion returns to LessonRoadmapScreen
- [ ] Mastery NOT counted (flowers unchanged)

### Should Pass
- [ ] Input mode switching works in drill
- [ ] Voice auto-trigger functions correctly
- [ ] Word bank generated from drill answers
- [ ] Exit mid-session saves progress
- [ ] Resume starts at saved position

### Nice to Have
- [ ] Fire streak awarded for drill completion
- [ ] Report/flag works in drill mode
- [ ] Empty drill cards handled gracefully

---

## Revision History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2025-05-20 | Initial click test scenario | Claude Code |
