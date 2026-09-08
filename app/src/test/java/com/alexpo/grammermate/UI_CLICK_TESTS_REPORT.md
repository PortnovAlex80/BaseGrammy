# UI-Click Tests Analysis and Implementation Report

## Date: 2025-01-20

## Summary

Analysis of existing "ClickTest" files reveals that **NONE of them are true UI-click tests**. All existing tests in `scenario/` directory directly call business logic methods (`sessionRunner.startSession()`, `coordinator.onBlockComplete()`, etc.) instead of simulating user interactions through Compose UI API.

## Existing Tests Classification

### ❌ NOT UI-Click Tests (call methods directly)

| File | Type | Why it's NOT a UI-click test |
|------|------|------------------------------|
| `scenario/BossBattleClickTest.kt` | Unit/Scenario | Calls `bossOrchestrator.startBossLesson()`, `sessionRunner.submitAnswer()`, `bossOrchestrator.finishBoss()` |
| `scenario/RegularLessonClickTest.kt` | Unit/Scenario | Calls `sessionRunner.setSessionCards()`, `sessionRunner.startSession()`, `sessionRunner.submitAnswer()`, `sessionRunner.navigateNext()` |
| `scenario/DailyPracticeClickTest.kt` | Unit/Scenario | Calls `coordinator.startDailySession()`, `coordinator.onBlockComplete()`, `coordinator.rateVocabCard()` |
| `scenario/LessonDrillClickTest.kt` | Unit/Scenario | Calls `sessionRunner.startSession()`, `sessionRunner.submitDrillAnswer()` |
| `scenario/VerbDrillClickTest.kt` | Unit/Scenario | Calls store methods directly, no UI rendering |
| `scenario/VocabDrillClickTest.kt` | Unit/Scenario | Calls store methods directly, no UI rendering |
| `scenario/VerbPracticeButtonClickTest.kt` | Unit/Scenario | Calls ViewModel methods directly |
| `scenario/EliteModeScenarioTest.kt` | Scenario | Direct business logic calls |
| `scenario/MasteryProgressionScenarioTest.kt` | Scenario | Direct business logic calls |
| `scenario/MixedSessionScenarioTest.kt` | Scenario | Direct business logic calls |
| `scenario/NewOnlySessionScenarioTest.kt` | Scenario | Direct business logic calls |
| `scenario/BossBattleScenarioTest.kt` | Scenario | Direct business logic calls |
| `scenario/DailyPracticeScenarioTest.kt` | Scenario | Direct business logic calls |
| `scenario/FullCourseJourneyTest.kt` | Scenario | Direct business logic calls |

### ⚠️ Trivial UI Tests (not real app screens)

| File | Type | Why it's inadequate |
|------|------|---------------------|
| `ui/SmokeClickTest.kt` | Compose Test | Tests trivial button/text, not real app screens |
| `ui/NavigationClickTest.kt` | Compose Test | Tests trivial icon with click callback, not real navigation |
| `ui/SubmitAnswerTest.kt` | Compose Test | Tests trivial button, not real submit flow |
| `ui/InputModeSwitchTest.kt` | Compose Test | Not analyzed yet |
| `ui/WordBankToggleTest.kt` | Compose Test | Not analyzed yet |
| `MinimalComposeRobolectricTest.kt` | Smoke Test | Just proves Compose testing works |

## New UI-Click Tests Created

### ✅ `ui/RegularLessonClickUiTest.kt`

**User path tested:**
1. Start lesson → first card displayed
2. Type correct answer → Click Check → verify card advances
3. Three wrong answers → verify hint shown
4. Complete all cards → verify completion screen
5. Pause → type answer → Check → Resume (same card)
6. Word Bank mode → click words → Submit
7. Show Answer button → reveals answer
8. Previous/Next navigation
9. Input mode switching (Voice → Keyboard → Word Bank)
10. Incorrect feedback display
11. Progress indicator updates

**Key differences from scenario tests:**
- ✅ Renders actual `TrainingScreen` composable
- ✅ Uses Compose testing API: `onNodeWithText()`, `performClick()`, `performTextInput()`
- ✅ Verifies what's visible on screen (text, buttons, state)
- ✅ NO direct calls to `sessionRunner.startSession()`, `submitAnswer()`, etc.
- ❌ Still uses callbacks to simulate state changes (acceptable for test isolation)

## Test Tag Requirements

### Elements that need testTag/contentDescription:

1. **Submit Button** (`UnifiedInputControlsBar.kt:342`)
   - Current: Uses `R.string.button_check` text
   - Already testable via `onNodeWithText("Check")`
   - Recommendation: Add `testTag = "submit_button"` for robustness

2. **Input Field** (`UnifiedInputControlsBar.kt:169`)
   - Current: Uses label text `R.string.card_label_your_translation`
   - Already testable via `onNodeWithText("Your translation")`
   - Recommendation: Add `testTag = "answer_input"`

3. **Show Answer Button** (`UnifiedInputControlsBar.kt:297`)
   - Current: Uses contentDescription from strings
   - Already testable via `onNodeWithText("Show answer")`
   - Recommendation: Add `testTag = "show_answer_button"`

4. **Pause/Play Button** (`UnifiedNavigationRow`)
   - Needs verification of current testability

5. **Previous/Next Buttons** (`UnifiedNavigationRow`)
   - Already testable via text "Previous" and "Next"

## Recommendations

1. **Keep existing scenario tests** - they are valuable unit/integration tests for business logic
2. **Rename them** to remove "ClickTest" suffix:
   - `BossBattleClickTest.kt` → `BossBattleScenarioTest.kt`
   - `RegularLessonClickTest.kt` → `RegularLessonScenarioTest.kt`
   - etc.
3. **Add testTags** to key UI elements for robustness
4. **Create more UI-click tests** for:
   - Daily Practice (3-block flow)
   - Verb Drill (selection screen, session, completion)
   - Vocab Drill (flashcard flip, rating buttons)
   - Pomodoro (timer banner, summary screen)
   - Sub-drill (tile entry, session, exit to roadmap)

## Next Steps

1. ✅ Created `RegularLessonClickUiTest.kt`
2. ⏳ Create `PauseCascadeClickUiTest.kt`
3. ⏳ Create `WordBankEasyClickUiTest.kt`
4. ⏳ Create `DailyPracticeClickUiTest.kt`
5. ⏳ Create `VerbPracticeClickUiTest.kt`
6. ⏳ Create `PomodoroClickUiTest.kt`
7. ⏳ Create `SubDrillClickUiTest.kt`
8. ⏳ Run tests to verify they pass
9. ⏳ Add testTags to production UI for better testability
