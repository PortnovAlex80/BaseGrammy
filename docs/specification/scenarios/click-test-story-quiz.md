# Story Quiz Click Test Scenario

**Document Version:** 1.0
**Date Created:** 2025-05-20
**Status:** DORMANT
**Related Specs:** 04-parsers.md (4.5 StoryQuizParser), 08-training-viewmodel.md (2.11 Story Methods), 19-screen-catalog.md (7.6.5 StoryQuizScreen), 23-screen-elements.md (Section 10: StoryQuizScreen)
**Related Use Cases:** UC-21 (Complete Story Check-in / Check-out), US-74, US-75, US-76

---

## Executive Summary

**Status: DORMANT — Tiles exist but are not rendered**

Story Quiz mode has complete implementation (parser, screen, navigation, ViewModel integration) but is **not accessible to users**. The `StoryCheckIn` and `StoryCheckOut` roadmap entries are intentionally not rendered in `LessonRoadmapScreen.kt` (line 263-265). No story quiz JSON content exists in default lesson packs (`assets/grammarmate/packs/`).

This click-test scenario documents what **should** happen when Story Quiz is activated, serving as a verification checklist for future activation.

---

## Preconditions

### Required Data
1. **Story Quiz JSON file** must exist in the lesson pack with schema:
   ```json
   {
     "storyId": "string (required)",
     "lessonId": "string (required)",
     "phase": "CHECK_IN | CHECK_OUT (required)",
     "text": "string (required)",
     "questions": [
       {
         "qId": "string (required)",
         "prompt": "string (required)",
         "options": ["string (required, non-empty)"],
         "correctIndex": 0,
         "explain": "string (optional)"
       }
     ]
   }
   ```
2. **Lesson must be selected** in `TrainingViewModel.selectedLessonId`
3. **Language must be selected** in `TrainingViewModel.selectedLanguageId`

### App State
- `TrainingViewModel.activeStory` must be non-null
- `TrainingViewModel.storyErrorMessage` must be null
- Current route: `Routes.LESSON` (LessonRoadmapScreen)

---

## Test Steps

### Entry Point

**Method 1: Manual Entry (Requires Code Change)**
1. Navigate to Lesson Roadmap screen
2. **Current behavior:** Story quiz tiles are NOT rendered (code explicitly skips them at lines 263-265 of `LessonRoadmapScreen.kt`)
3. **Expected when activated:** Tap "Story Check-in" tile (before lesson) or "Story Check-out" tile (after lesson)
4. App calls `TrainingViewModel.openStory(phase)` where `phase` is `CHECK_IN` or `CHECK_OUT`
5. Navigation changes to `Routes.STORY`
6. `StoryQuizScreen` composable renders with `story = state.story.activeStory`

**Method 2: Via TrainingViewModel.openStory(phase)**
1. From Lesson Roadmap, `TrainingViewModel.openStory(StoryPhase.CHECK_IN)` or `openStory(StoryPhase.CHECK_OUT)` is called
2. ViewModel loads story via `lessonStore.loadStory(selectedLessonId, selectedLanguageId, phase)`
3. On success: `activeStory` is set, navigation to STORY route
4. On failure: `storyErrorMessage` is set, `StoryErrorDialog` appears

---

### Step 1: Verify Story Loading

**Action:** Story quiz screen appears

**Expected Elements (from 23-screen-elements.md):**
| Element | ID | Type | Expected Content |
|---------|----|------|------------------|
| Phase title | SQ-01 | text | "Story Check-in" or "Story Check-out" (SemiBold) |
| Story text | SQ-02 | text | `story.text` (bodyMedium style) |
| Question counter | SQ-03 | text | "Question 1 / N" where N = total questions |
| Question prompt | SQ-04 | text | First question's `prompt` (SemiBold) |

**Verification Points:**
- [ ] Phase title matches the phase (CHECK_IN vs CHECK_OUT)
- [ ] Story text is fully visible and scrollable if long
- [ ] Question counter shows "1 / N" format
- [ ] At least 2 answer options are displayed

---

### Step 2: Select Answer Options

**Action:** Tap an answer option

**Expected Behavior:**
1. Tapped row shows ">" indicator at start
2. Row is visually marked as selected
3. Other rows remain unmarked
4. No suffixes appear yet (result not shown)
5. `selections.value` state is updated with `{question.qId: selectedIndex}`

**Option Row Display (SQ-05):**
- Unselected: `"  {optionText}"`
- Selected: `"> {optionText}"`
- After check (correct): `"> {optionText} (correct)"`
- After check (incorrect): `"> {optionText} (your choice)"`

**Verification Points:**
- [ ] Exactly one option can be selected per question
- [ ] Selection can be changed before clicking "Check"
- [ ] Changing selection clears any previous result for that question

---

### Step 3: Check Answer

**Action:** Tap "Check" button

**Expected Behavior (Correct Answer):**
1. `showResult` becomes `true`
2. Selected option gets "(correct)" suffix
3. "Correct: {optionText}" appears in primary color (SQ-06)
4. No error message
5. `results.value` updated with `{question.qId: true}`

**Expected Behavior (Incorrect Answer):**
1. `showResult` becomes `true`
2. Selected option gets "(your choice)" suffix
3. Correct option gets "(correct)" suffix
4. "Incorrect" error message appears (SQ-07)
5. `results.value` updated with `{question.qId: false}`

**Expected Behavior (No Selection):**
1. Error message "Select an answer" appears
2. `showResult` remains `false`
3. Question counter does not advance

**Verification Points:**
- [ ] Correct answers show green/primary color feedback
- [ ] Incorrect answers show error message and highlight correct answer
- [ ] Cannot proceed without selecting an answer
- [ ] Result persists when navigating away and back to the question

---

### Step 4: Navigate Questions

**Action 1: Tap "Next" button**

**Expected Behavior (Non-last question):**
1. Validates selection (shows error if none)
2. Records result in `results.value`
3. Increments `questionIndex`
4. Loads next question
5. `showResult` is restored based on whether next question was already answered

**Action 2: Tap "Prev" button**

**Expected Behavior:**
1. `questionIndex` decrements
2. Previous question loads
3. `showResult` is restored if that question was already checked
4. Previous selections/results are preserved

**Action 3: Tap "Finish" button (last question)**

**Expected Behavior:**
1. Validates selection
2. Records final result
3. Calls `onComplete(allCorrect)` where `allCorrect = all answers were correct`
4. `TrainingViewModel.completeStory(phase, allCorrect)` is called
5. Navigation returns to `Routes.LESSON`

**Verification Points:**
- [ ] Can navigate forward and backward through all questions
- [ ] Previous answers are preserved when navigating
- [ ] "Finish" button only appears on last question
- [ ] Completion callback is called with correct `allCorrect` value

---

### Step 5: Completion and State Update

**Action:** Quiz completes (all questions answered)

**Expected ViewModel Changes:**
1. `TrainingViewModel.completeStory(phase, allCorrect)` is called
2. `storyCheckInDone` or `storyCheckOutDone` is set to `true` based on phase
3. `activeStory` is set to `null`
4. `saveProgress()` is called
5. Navigation returns to Lesson Roadmap

**Persistence (from 08-training-viewmodel.md):**
- `storyCheckInDone` and `storyCheckOutDone` are stored in `ProgressStore`
- Flags are restored on app restart via `reloadFromDisk()`

**Mastery Impact:**
- **Story quizzes do NOT affect flower growth or mastery** (per UC-21 business rules)
- Story completion is only tracked for roadmap display purposes

**Verification Points:**
- [ ] After completion, returning to roadmap shows phase as completed
- [ ] Can re-take the same story quiz (flags reset appropriately)
- [ ] Flower state is unchanged by story quiz completion

---

## Edge Cases and Error Handling

### EC-1: Null Story (story == null)

**Expected Behavior (SQ-12):**
1. `LaunchedEffect(Unit)` immediately calls `onClose()`
2. Screen closes without rendering content
3. Returns to previous route

### EC-2: Empty Questions List (story.questions.isEmpty())

**Expected Behavior (SQ-13):**
1. `LaunchedEffect(Unit)` immediately calls `onComplete(true)`
2. Quiz completes with 0/0 accuracy
3. No metrics saved (per EC-19 in 22-use-case-registry.md)

### EC-3: Story Load Failure

**Expected Behavior:**
1. `TrainingViewModel.openStory(phase)` catches exception
2. `storyErrorMessage` is set
3. `StoryErrorDialog` appears with error text
4. Dialog has "OK" button that calls `vm.clearStoryError()`

### EC-4: Long Story Text

**Expected Behavior:**
1. Content is vertically scrollable
2. "Scroll to continue" hint appears (SQ-11) when `scrollState.maxValue > 0`
3. All text remains accessible

### EC-5: Changing Selection After Check

**Expected Behavior:**
1. Clicking a different option after "Check" clears previous result
2. `showResult` becomes `false`
3. `results.value` entry for that question is removed
4. User can re-check with new selection

---

## Navigation Flow Diagram

```
LessonRoadmapScreen
    |
    | (tap StoryCheckIn/StoryCheckOut tile) — [CURRENTLY DISABLED]
    v
TrainingViewModel.openStory(phase)
    |
    | (success)
    v
Routes.STORY → StoryQuizScreen
    |
    | (complete story)
    v
TrainingViewModel.completeStory(phase, allCorrect)
    |
    v
Routes.LESSON → LessonRoadmapScreen
```

**Alternative Path (Error):**
```
TrainingViewModel.openStory(phase)
    |
    | (failure)
    v
StoryErrorDialog → vm.clearStoryError()
    |
    v
LessonRoadmapScreen (error cleared)
```

---

## Mastery Impact Verification

**Per UC-21 Business Rules:**
> "Stories do not affect flower growth or mastery."

**Verification:**
1. Complete a Story Check-in
2. Check `FlowerCalculator` output for the lesson — should be unchanged
3. Complete lesson cards
4. Check flower state — should grow based on card practice only
5. Complete Story Check-out
6. Flower state should remain unchanged after check-out

**Data Store Writes:**
- `ProgressStore`: Updates `storyCheckInDone` / `storyCheckOutDone`
- `MasteryStore`: NOT touched by story completion
- `WordMasteryStore`: NOT touched by story completion

---

## Status: DORMANT

**Reason for Dormant Status:**

1. **Tiles Not Rendered:** `LessonRoadmapScreen.kt` lines 263-265 explicitly skip rendering `StoryCheckIn` and `StoryCheckOut` entries:
   ```kotlin
   // StoryCheckIn/StoryCheckOut kept for backward compat but no longer rendered
   is RoadmapEntry.StoryCheckIn -> { }
   is RoadmapEntry.StoryCheckOut -> { }
   ```

2. **No Content Assets:** No story quiz JSON files exist in `assets/grammarmate/packs/` for default lesson packs.

3. **Backward Compatibility:** The enum values and data structures are maintained ("kept for backward compat") suggesting this feature may be reactivated or was deprecated.

**To Activate:**
1. Create story quiz JSON files following the schema in section 4.6.10
2. Include them in lesson pack ZIPs
3. Update `LessonRoadmapScreen.kt` to render story tiles
4. Define tile appearance and interaction in `RoadmapEntry` rendering

---

## Test Checklist Summary

### Entry Points
- [ ] Story Check-in tile (before lesson) — [DORMANT: not rendered]
- [ ] Story Check-out tile (after lesson) — [DORMANT: not rendered]
- [ ] Direct navigation to STORY route with valid `activeStory`

### Screen Rendering
- [ ] Phase title (CHECK_IN / CHECK_OUT)
- [ ] Story text display
- [ ] Question counter
- [ ] Question prompt
- [ ] Answer options (at least 2)

### Interaction
- [ ] Select option (shows ">")
- [ ] Change selection
- [ ] Check answer (correct path)
- [ ] Check answer (incorrect path)
- [ ] Check without selection (error)
- [ ] Navigate to next question
- [ ] Navigate to previous question
- [ ] Finish quiz

### State Management
- [ ] Results persist across navigation
- [ ] Completion flag saved to ProgressStore
- [ ] activeStory cleared after completion
- [ ] Return to Lesson Roadmap

### Edge Cases
- [ ] Null story auto-close
- [ ] Empty questions auto-complete
- [ ] Long text scrollable
- [ ] Error dialog on load failure

### Mastery Verification
- [ ] Flower state unchanged by story completion
- [ ] Mastery counts unchanged
- [ ] ProgressStore updated correctly

---

## References

- **Spec Files:** 01-models-and-state.md (1.1.6 StoryQuestion, 1.1.7 StoryQuiz, 1.2.1 StoryPhase), 04-parsers.md (4.5 StoryQuizParser), 08-training-viewmodel.md (2.11 Story Methods), 19-screen-catalog.md (7.6.5 StoryQuizScreen), 23-screen-elements.md (Section 10)
- **Source Files:** `data/StoryQuizParser.kt`, `data/Models.kt`, `ui/screens/StoryQuizScreen.kt`, `ui/screens/LessonRoadmapScreen.kt`, `ui/GrammarMateApp.kt`
- **Use Cases:** UC-21 (Complete Story Check-in / Check-out), US-74, US-75, US-76
- **Strings:** `values/strings-screens.xml` lines 131-144
