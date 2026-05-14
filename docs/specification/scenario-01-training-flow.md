# Scenario 01: Normal Lesson Training Flow

Traced against specification docs `08-training-viewmodel.md` and `03-algorithms-and-calculators.md`.

---

## Step 1: User Taps Lesson Tile

### Code Path

1. `GrammarMateApp.kt:318-321` -- HomeScreen `onSelectLesson` callback fires
2. Calls `vm.selectLesson(lessonId)` which navigates `screen = AppScreen.LESSON`
3. From LESSON screen, user navigates to roadmap and taps "Start" on a sub-lesson:
   `GrammarMateApp.kt:347-349` -- `vm.selectSubLesson(index)`, then `screen = AppScreen.TRAINING`
4. Alternatively, tapping a lesson tile from Home directly calls `selectLesson()` then user navigates to TRAINING screen

### Expected Behavior (Spec 8.5.1)

> Trigger: `selectLesson(lessonId)` or `selectMode(TrainingMode.LESSON)`

### Actual Behavior (Code: `TrainingViewModel.kt:469-543`)

`selectLesson(lessonId)`:
1. Pauses timer, clears `sessionCards`, `bossCards`, `eliteCards`, `vocabSession`
2. Resolves pack for lesson (`lessonStore.getPackIdForLesson`)
3. Rebuilds schedules via `rebuildSchedules(lessons)` -- uses `MixedReviewScheduler` with `subLessonSize` (default 10)
4. Calculates completed sub-lessons from mastery data via `calculateCompletedSubLessons()`
5. Sets `activeSubLessonIndex = completedCount.coerceAtMost(subLessons.size - 1)` -- resumes at first incomplete sub-lesson
6. Massive `copy()` resetting session state: `currentIndex=0`, `sessionState=PAUSED`, `inputMode=VOICE`, clears all boss/elite/drill/vocab state
7. Calls `buildSessionCards()` to load cards for the active sub-lesson
8. Calls `refreshFlowerStates()` to update flower visuals
9. Calls `saveProgress()`

### Discrepancy: NO

The implementation matches the spec. The lesson is selected, schedules rebuilt, and session initialized in PAUSED state awaiting user to start.

---

## Step 2: startSession() -- Session Activation

### Code Path

User taps play/resume button -> `GrammarMateApp.kt:433` calls `vm.togglePause()` -> `TrainingViewModel.kt:917-924` -> since state is PAUSED, calls `startSession()`

`startSession()` at `TrainingViewModel.kt:2401-2424`:
1. If NOT boss/elite: calls `buildSessionCards()` (rebuilds cards for current sub-lesson)
2. If `sessionCards` empty or `currentCard == null`: pauses and returns
3. Pauses existing timer, then starts new timer via `resumeTimer()`
4. Sets `sessionState = ACTIVE`, clears `inputText`, triggers voice if VOICE mode
5. Records mastery for current card via `recordCardShowForMastery(card)`
6. Calls `saveProgress()`

### Expected Behavior (Spec 8.3.2)

> 1. If not in boss or elite mode, rebuilds session cards
> 2. If no cards or no current card, pauses and returns
> 3. Pauses any existing timer, then starts a new one
> 4. Sets sessionState = ACTIVE, clears input, triggers voice if in VOICE mode
> 5. Records mastery for the current card
> 6. Saves progress

### Actual Behavior

Matches exactly. All 6 steps are present in order.

### Discrepancy: NO

---

## Step 3: First Card Shown -- Data Flow to UI

### Code Path

After `startSession()`, the UI reads `_uiState.value` via `stateFlow`:

`GrammarMateApp.kt:2557` -- `val hasCards = state.currentCard != null`
The TrainingScreen composable receives `state: TrainingUiState` and renders:
- `state.currentCard` -- the `SentenceCard` with `promptRu` and `acceptedAnswers`
- `state.currentIndex` -- 0 for first card
- `state.subLessonTotal` -- number of cards in this sub-lesson (typically 10)
- `state.inputMode` -- defaults to `InputMode.VOICE`
- `state.sessionState` -- `ACTIVE`
- `state.activeSubLessonIndex` -- which sub-lesson (0-based)
- `state.subLessonTypes` -- list of `SubLessonType` for all sub-lessons

Card data originates from `buildSessionCards()` (`TrainingViewModel.kt:1296-1368`):
- Gets schedule for `selectedLessonId` from `lessonSchedules`
- Gets sub-lesson at `activeSubLessonIndex`
- Filters out hidden cards (`hiddenCardStore.getHiddenCardIds()`)
- Sets `sessionCards = filtered list`
- Sets `currentCard = sessionCards[0]`

### Which Sub-lesson Type Is Chosen?

For a fresh lesson (lessonIndex > 0), the schedule from `MixedReviewScheduler` has:
- **NEW_ONLY sub-lessons first** -- containing the first half of cards from the lesson
- **MIXED sub-lessons after** -- containing the second half of lesson cards plus review cards from prior lessons

For the very first lesson (lessonIndex == 0), all sub-lessons are NEW_ONLY.

### Expected Behavior (Spec 8.10.2)

> LESSON mode: Get schedule for selected lesson, get sub-lessons list, select sub-lesson at activeSubLessonIndex, filter out hidden cards, cards maintain their original order from the CSV.

### Actual Behavior

The code at `TrainingViewModel.kt:1301-1333` does exactly this. Cards maintain their original order (the scheduler doesn't reorder NEW_ONLY cards).

### Discrepancy: NO

---

## Step 4: User Types Answer -> submitAnswer() -> Normalization -> Comparison

### Code Path

User types answer -> `vm.onInputChanged(text)` (`TrainingViewModel.kt:349-358`) -> user taps submit -> `vm.submitAnswer()` (`TrainingViewModel.kt:617-842`)

`submitAnswer()` flow:
1. Guard: returns `SubmitResult(false, false)` if `sessionState != ACTIVE`, `inputText.isBlank()` (unless testMode), or no current card
2. **Normalization**: `Normalizer.normalize(state.inputText)` -- `Normalization.kt:4-21`
   - Trim + collapse whitespace
   - Remove minutes from time expressions (`"8:30"` -> `"8"`)
   - Lowercase
   - Remove punctuation (except hyphens)
   - Final whitespace cleanup
3. **Comparison**: `state.testMode || card.acceptedAnswers.any { Normalizer.normalize(it) == normalizedInput }`
   - In test mode: always accepted
   - Otherwise: normalized input must exactly match at least one normalized accepted answer

### All Possible Outcomes

**ACCEPTED (correct):**
- Branch at line 629-812
- Multiple sub-paths depending on session mode (boss, elite, drill, last-card, mid-session)

**REJECTED (incorrect):**
- Branch at line 813-834
- Increments `incorrectCount` and `incorrectAttemptsForCard`
- After 3 attempts: shows hint (all accepted answers joined with " / "), pauses timer, resets attempts
- Before 3 attempts: allows retry, auto-triggers voice if in VOICE mode

### Expected Behavior (Spec 8.4.1)

> `val normalizedInput = Normalizer.normalize(state.inputText)`
> `val accepted = card.acceptedAnswers.any { Normalizer.normalize(it) == normalizedInput }`
> In test mode, all answers are accepted regardless of input.

### Actual Behavior

Matches exactly. The normalization pipeline matches the spec algorithm step-for-step.

### Discrepancy: NO

---

## Step 5: Correct Answer -- Updates

### Code Path (Non-Last Card)

`TrainingViewModel.kt:791-812`:

```
correctCount++
lastResult = true
incorrectAttemptsForCard = 0
answerText = null
voiceActiveMs += duration (if VOICE)
voiceWordCount += words (if VOICE)
voicePromptStartMs = null
```

Then calls `nextCard(triggerVoice = state.inputMode == InputMode.VOICE)`.

### Mastery Update

At line 632: `recordCardShowForMastery(card)` is called immediately when answer is accepted.

`recordCardShowForMastery()` at `TrainingViewModel.kt:2896-2915`:
1. Returns early if `isDrillMode`
2. Resolves card-to-lesson mapping via `resolveCardLessonId()`
3. Returns early if `inputMode == WORD_BANK` (critical business rule)
4. Calls `masteryStore.recordCardShow(lessonId, languageId, card.id)`

`MasteryStore.recordCardShow()` at `MasteryStore.kt:105-145`:
1. Gets or creates `LessonMasteryState`
2. Checks if card is new (`isNewCard = !shownCardIds.contains(cardId)`)
3. If new: `uniqueCardShows++`, adds to `shownCardIds`
4. Always: `totalCardShows++`, updates `lastShowDateMs = now`
5. Calculates `wasOnTime` using `SpacedRepetitionConfig.wasRepetitionOnTime()`
6. Advances interval step if `wasOnTime` via `SpacedRepetitionConfig.nextIntervalStep()`
7. Persists to YAML file atomically

### Card Index Update

`nextCard()` at `TrainingViewModel.kt:844-897`:
1. `nextIndex = (currentIndex + 1).coerceAtMost(sessionCards.lastIndex)`
2. Updates `currentIndex`, `currentCard`, clears `inputText`, `lastResult`, `answerText`, `incorrectAttemptsForCard`
3. Calls `recordCardShowForMastery(newCard)` -- records mastery for the NEXT card on load
4. Updates word bank if in WORD_BANK mode
5. Resumes timer if was showing hint
6. Calls `saveProgress()`

### Flower Refresh

At line 837-838: `if (accepted) { refreshFlowerStates() }` -- flowers update after each correct answer.

### Expected Behavior (Spec 8.4.2)

> 1. Play success tone
> 2. Record card show for mastery tracking
> 3. Update counters: correctCount++, incorrectAttemptsForCard = 0
> 4. Clear answer display state
> 5. Track voice metrics if applicable
> 6. Navigate based on session mode
> 7. Save progress
> 8. Refresh flower states

### Actual Behavior

All 8 steps are present. Order differs slightly (flower refresh at end of method rather than inline), but logically equivalent.

### Discrepancy: NO

---

## Step 6: Incorrect Answer -- 3-Attempt Flow / Hint Reveal

### Code Path

`TrainingViewModel.kt:813-834`:

```kotlin
playErrorTone()
val nextIncorrect = state.incorrectAttemptsForCard + 1
val hint = if (nextIncorrect >= 3) card.acceptedAnswers.joinToString(" / ") else null
hintShown = hint != null
val shouldTriggerVoice = !hintShown && state.inputMode == InputMode.VOICE

_uiState.update {
    it.copy(
        incorrectCount = it.incorrectCount + 1,
        incorrectAttemptsForCard = if (hintShown) 0 else nextIncorrect,
        lastResult = false,
        answerText = hint,
        inputText = if (state.inputMode == InputMode.VOICE) "" else it.inputText,
        sessionState = if (hint != null) SessionState.HINT_SHOWN else it.sessionState,
        voiceTriggerToken = if (shouldTriggerVoice) it.voiceTriggerToken + 1 else it.voiceTriggerToken,
        voicePromptStartMs = null
    )
}
if (hintShown) {
    pauseTimer()
}
```

### Detailed Flow

**Attempt 1 (incorrectAttemptsForCard was 0):**
- `nextIncorrect = 1`
- `hint = null` (1 < 3)
- `incorrectCount++`, `incorrectAttemptsForCard = 1`
- `lastResult = false`
- `answerText = null`
- If VOICE mode: clear input, trigger voice prompt again
- Timer keeps running

**Attempt 2 (incorrectAttemptsForCard was 1):**
- `nextIncorrect = 2`
- `hint = null` (2 < 3)
- Same as attempt 1 with `incorrectAttemptsForCard = 2`

**Attempt 3 (incorrectAttemptsForCard was 2):**
- `nextIncorrect = 3`
- `hint = card.acceptedAnswers.joinToString(" / ")` (3 >= 3)
- `hintShown = true`
- `incorrectCount++`, `incorrectAttemptsForCard = 0` (reset!)
- `answerText = hint` (all accepted answers shown)
- `sessionState = HINT_SHOWN`
- `pauseTimer()` called
- User sees the answer, must advance manually

After hint shown, the user taps "Next" -> `nextCard()` is called from UI, which loads the next card, resumes timer (line 893-894: `if (wasHintShown && !pauseForReward) { resumeTimer() }`).

### Expected Behavior (Spec 8.4.3)

> 1. Play error tone
> 2. incorrectCount++, incorrectAttemptsForCard++
> 3. lastResult = false
> 4. After 3 attempts: show all accepted answers as hint, pause timer, reset incorrectAttemptsForCard to 0
> 5. Before 3 attempts: allow retry, auto-trigger voice if in VOICE mode

### Actual Behavior

Matches. One nuance: when hint is shown, `incorrectAttemptsForCard` is reset to 0 (not kept at 3). This means after viewing the hint and advancing, the next card starts fresh at 0 attempts. The `onInputChanged()` method at line 349-358 also has a reset: if `answerText != null` or `incorrectAttemptsForCard >= 3`, it resets attempts and clears `answerText`.

### Discrepancy: NO

---

## Step 7: Last Card in Sub-Lesson -- Sub-Lesson Completion

### Code Path

`TrainingViewModel.kt:741-812` (the `isLastCard` branch within `submitAnswer()`):

```kotlin
} else if (isLastCard) {
    pauseTimer()
    // ... state update ...
    markSubLessonCardsShown(sessionCards)
    buildSessionCards()
    checkAndMarkLessonCompleted()
    refreshFlowerStates()
    updateStreak()
    forceBackupOnSave = true
}
```

**Detailed state update (lines 745-783):**

1. `pauseTimer()` -- timer stops
2. Recalculates `completedSubLessonCount`:
   - Gets latest mastery from `masteryStore`
   - Calls `calculateCompletedSubLessons(subLessons, mastery, lessonId)`
   - This walks sub-lessons in order and counts how many have ALL their cards in `mastery.shownCardIds`
   - Stops at the first incomplete sub-lesson
3. `activeSubLessonIndex`:
   - `preservedActiveIndex = maxOf(activeSubLessonIndex, actualCompletedCount)`
   - Ensures index never moves backward (user repeating an old sub-lesson doesn't regress)
   - Clamped to `subLessonCount - 1`
4. `completedSubLessonCount = maxOf(nextCompleted, actualCompletedCount)` -- uses maximum of increment-based and data-based counts
5. `subLessonFinishedToken++` -- triggers UI event
6. `sessionState = PAUSED`, `currentIndex = 0`

**After state update:**
7. `markSubLessonCardsShown(sessionCards)` -- records cards for WORD_BANK progress tracking
8. `buildSessionCards()` -- rebuilds cards for the NEXT sub-lesson (at the new `activeSubLessonIndex`)
9. `checkAndMarkLessonCompleted()` -- checks if 15 sub-lessons completed
10. `refreshFlowerStates()` -- updates all flower visuals
11. `updateStreak()` -- records sub-lesson completion for streak
12. `forceBackupOnSave = true` -- triggers backup on next `saveProgress()`

### markSubLessonCardsShown (line 2917-2928)

```kotlin
private fun markSubLessonCardsShown(cards: List<SentenceCard>) {
    if (_uiState.value.inputMode != InputMode.WORD_BANK || cards.isEmpty()) return
    // ... marks cards via masteryStore.markCardsShownForProgress
}
```

**IMPORTANT:** This method only operates when `inputMode == WORD_BANK`. For VOICE/KEYBOARD modes, cards were already individually recorded via `recordCardShowForMastery()` on each correct answer. This method is a catch-up for WORD_BANK cards that don't count for mastery but still need to be tracked for sub-lesson progress.

### calculateCompletedSubLessons (line 2945-2972)

Walks sub-lessons sequentially. A sub-lesson is "completed" when ALL its cards are in `mastery.shownCardIds`. Stops counting at the first incomplete sub-lesson (does not skip ahead).

### Expected Behavior (Spec 8.3.3)

> 1. Pause timer
> 2. Calculate completed sub-lessons from mastery data
> 3. Advance completedSubLessonCount and activeSubLessonIndex
> 4. Increment subLessonFinishedToken
> 5. Mark shown cards via markSubLessonCardsShown()
> 6. Rebuild session cards for the next sub-lesson
> 7. Check lesson completion (15 sub-lessons)
> 8. Refresh flower states
> 9. Update streak
> 10. Trigger backup on next save

### Actual Behavior

All 10 steps present in order. The index preservation logic (`maxOf`) prevents regression.

### Discrepancy: NO

---

## Step 8: Next Sub-Lesson -- NEW_ONLY or MIXED Card Selection

### Code Path

After sub-lesson completion, `buildSessionCards()` at line 1296 is called with the new `activeSubLessonIndex`.

For `TrainingMode.LESSON` (line 1301-1333):
1. Gets schedule from `lessonSchedules[selectedLessonId]`
2. Gets sub-lesson at the new `activeSubLessonIndex`
3. Filters hidden cards
4. Sets `sessionCards` and `currentCard`

The sub-lesson types come from `MixedReviewScheduler.build()` at `MixedReviewScheduler.kt:28-111`.

### Sub-Lesson Ordering

From `MixedReviewScheduler.kt:99-105`:
```
NEW_ONLY sub-lessons are built first (lines 99-104)
MIXED sub-lessons are appended after (lines 70-97, added via subLessons.addAll(mixedSubLessons) at line 105)
```

So the schedule order is: `[NEW_ONLY, NEW_ONLY, ..., MIXED, MIXED, ...]`

For a lesson with N cards and sub-lesson size 10:
- First half of cards -> NEW_ONLY sub-lessons
- Second half of cards -> MIXED sub-lessons (each with ~5 current + ~5 review cards)

### MIXED Card Selection

`MixedReviewScheduler.kt:71-97`:
- Takes `currentSlotsInMixed = subLessonSize - (subLessonSize / 2)` = 5 current cards
- Needs `reviewSlotsNeeded = subLessonSize - currentHalf.size` = 5 review slots
- Calls `dueLessonIds()` to find lessons due for review at current global mixed index
- Takes up to 2 due lessons
- Fills review slots with priority: Reserve pool -> Main pool -> Fallback (current lesson cards)

### Review Due Logic

`MixedReviewScheduler.kt:113-127`:
```
For each previously studied lesson:
  step = globalMixedIndex - reviewStartMixedIndex[lessonId]
  if step is in [1, 2, 4, 7, 10, 14, 20, 28, 42, 56]:
    lesson is due for review
```

### Expected Behavior (Spec 3.3)

> The first lesson is always NEW_ONLY. Cards are split 50/50 between NEW_ONLY and MIXED for lessons beyond the first. Sub-lesson ordering: NEW_ONLY sub-lessons come first, then MIXED.

### Actual Behavior

Matches. The first lesson gets all NEW_ONLY. Subsequent lessons get NEW_ONLY first, then MIXED.

### Discrepancy: NO

---

## Step 9: All Sub-Lessons Complete -- Session End

### Code Path

When the 15th sub-lesson is completed:

`checkAndMarkLessonCompleted()` at `TrainingViewModel.kt:2933-2940`:
```kotlin
val completedFirstCycle = state.completedSubLessonCount >= 15
if (completedFirstCycle && state.selectedLessonId != null) {
    masteryStore.markLessonCompleted(state.selectedLessonId, state.selectedLanguageId)
}
```

`MasteryStore.markLessonCompleted()` at `MasteryStore.kt:165-172`:
- Sets `completedAtMs = System.currentTimeMillis()` on the lesson's mastery state
- Only sets it once (returns early if already completed)

**IMPORTANT:** This does NOT end the session. It only marks the lesson as completed. The user can continue practicing beyond 15 sub-lessons. The session continues with more sub-lessons if the schedule has them.

### Session End (Explicit)

`finishSession()` at `TrainingViewModel.kt:933-964`:
1. Returns early if `sessionCards.isEmpty()`
2. If elite active: delegates to `cancelEliteSession()`
3. If boss active: delegates to `finishBoss()`
4. Pauses timer
5. Calculates rating: `correctCount / (activeTimeMs / 60000.0)`
6. Resets to PAUSED with first card
7. Saves progress

### What Is Persisted

`saveProgress()` at `TrainingViewModel.kt:2453-2487`:
- Skips if `bossActive && bossType != ELITE`
- Saves `TrainingProgress` to YAML via `ProgressStore`:
  - `languageId`, `mode`, `lessonId`, `currentIndex`
  - `correctCount`, `incorrectCount`, `incorrectAttemptsForCard`
  - `activeTimeMs`, `sessionState`
  - `bossLessonRewards`, `bossMegaRewards` (as string maps)
  - `voiceActiveMs`, `voiceWordCount`, `hintCount`
  - `eliteStepIndex`, `eliteBestSpeeds`
  - `currentScreen`, `activePackId`
  - `dailyLevel`, `dailyTaskIndex`, `dailyCursor`
- If `forceBackupOnSave == true`: calls `createProgressBackup()` and resets flag

Mastery data is persisted immediately on each `recordCardShow()` call via `MasteryStore.persistToFile()` which uses `AtomicFileWriter.writeText()`.

### Expected Behavior (Spec 8.3.4, 8.7.2)

> After 15 completed sub-lessons, the lesson is marked as completed via masteryStore.markLessonCompleted().
> Mastery is persisted immediately on each recordCardShow() call.

### Actual Behavior

Matches. Lesson completion marks `completedAtMs` but does not force session end. The session continues until user explicitly finishes or the user completes all scheduled sub-lessons.

### Discrepancy: NO

---

## Step 10: Flower State Update -- When and How

### When Flowers Update

`refreshFlowerStates()` is called:
1. `selectLesson()` -- line 541 (on lesson change)
2. `submitAnswer()` -- line 838 (after correct answer)
3. Sub-lesson last-card branch -- line 788 (after sub-lesson completion)
4. `finishSession()` -- line 962 (on session end)
5. Init block -- during app startup (noted in spec 8.3.1)
6. Various other state changes (pack reload, lesson import, etc.)

### How Flowers Are Calculated

`refreshFlowerStates()` at `TrainingViewModel.kt:3113-3151`:

For each lesson:
1. Gets `LessonMasteryState` from `masteryStore.get(lessonId, languageId)`
2. Calls `FlowerCalculator.calculate(mastery, lesson.cards.size)`
3. Stores in `lessonFlowers` map

`FlowerCalculator.calculate()` at `FlowerCalculator.kt:20-67`:
1. If `mastery == null` or `uniqueCardShows == 0`: returns `SEED` (scale=0.5, health=1.0)
2. Calculates `masteryPercent = uniqueCardShows / 150` (clamped to 0..1)
3. Calculates `daysSinceLastShow` (current time - `lastShowDateMs`, in days)
4. If `daysSinceLastShow > 90`: returns `GONE` (all metrics zeroed, scale=0.5)
5. Calculates `healthPercent` via `SpacedRepetitionConfig.calculateHealthPercent()`
6. Determines state via `determineFlowerState()`:
   - `healthPercent <= 0.51` -> WILTED
   - `healthPercent < 1.0` -> WILTING
   - `masteryPercent < 0.33` -> SEED
   - `masteryPercent < 0.66` -> SPROUT
   - else -> BLOOM
7. `scaleMultiplier = (masteryPercent * healthPercent).coerceIn(0.5, 1.0)`

Also updates ladder rows via `LessonLadderCalculator.calculate()`.

### Expected Behavior (Spec 3.2)

> Health takes priority over mastery. When health is below 1.0, the state is WILTING or WILTED regardless of mastery level.

### Actual Behavior

Matches. The `determineFlowerState` function checks health first (WILTED, WILTING), then mastery (SEED, SPROUT, BLOOM).

### Discrepancy: NO

---

## Summary of Findings

### Discrepancies Found: NONE

The code implementation matches the specification with high fidelity across all 10 scenario steps. Every method, data flow, and state transition documented in the spec (`08-training-viewmodel.md` and `03-algorithms-and-calculators.md`) has a corresponding implementation that follows the documented behavior.

### Notable Implementation Details (Not Discrepancies, But Worth Tracking)

1. **`markSubLessonCardsShown()` only fires for WORD_BANK mode** (`TrainingViewModel.kt:2918`). This is correct -- VOICE/KEYBOARD cards are individually tracked via `recordCardShowForMastery()` on each correct answer. The WORD_BANK catch-up ensures sub-lesson progress tracking works even when mastery doesn't count.

2. **`calculateCompletedSubLessons()` stops at the first incomplete sub-lesson** (`TrainingViewModel.kt:2960-2970`). It does NOT skip ahead to count non-contiguous completed sub-lessons. This means the completion count is always a prefix count.

3. **`activeSubLessonIndex` uses `maxOf(current, actual)` to prevent regression** (`TrainingViewModel.kt:758`). If a user revisits an old sub-lesson, their position does not move backward after completing it.

4. **Timer saves progress every 500ms** (`TrainingViewModel.kt:2437-2442`). This creates a steady stream of progress saves during active sessions.

5. **Flower refresh happens after EVERY correct answer** (`TrainingViewModel.kt:837-838`), not just at sub-lesson boundaries. This gives real-time visual feedback.

6. **`saveProgress()` skips during boss battles** (`TrainingViewModel.kt:2454-2455`) except ELITE type. Boss sessions are intentionally not persisted mid-battle.

7. **The `onInputChanged()` method resets attempts and clears `answerText`** when the previous answer was shown or 3+ attempts were made (`TrainingViewModel.kt:351`). This creates a clean state for re-entry after hint display.

### Code-File Reference Map

| Component | File | Key Lines |
|-----------|------|-----------|
| `selectLesson()` | `ui/TrainingViewModel.kt` | 469-543 |
| `startSession()` | `ui/TrainingViewModel.kt` | 2401-2424 |
| `buildSessionCards()` | `ui/TrainingViewModel.kt` | 1296-1368 |
| `submitAnswer()` | `ui/TrainingViewModel.kt` | 617-842 |
| `nextCard()` | `ui/TrainingViewModel.kt` | 844-897 |
| `prevCard()` | `ui/TrainingViewModel.kt` | 899-915 |
| `togglePause()` | `ui/TrainingViewModel.kt` | 917-925 |
| `finishSession()` | `ui/TrainingViewModel.kt` | 933-964 |
| `showAnswer()` | `ui/TrainingViewModel.kt` | 966-979 |
| `saveProgress()` | `ui/TrainingViewModel.kt` | 2453-2487 |
| `rebuildSchedules()` | `ui/TrainingViewModel.kt` | 1287-1294 |
| `recordCardShowForMastery()` | `ui/TrainingViewModel.kt` | 2896-2915 |
| `markSubLessonCardsShown()` | `ui/TrainingViewModel.kt` | 2917-2928 |
| `checkAndMarkLessonCompleted()` | `ui/TrainingViewModel.kt` | 2933-2940 |
| `calculateCompletedSubLessons()` | `ui/TrainingViewModel.kt` | 2945-2972 |
| `refreshFlowerStates()` | `ui/TrainingViewModel.kt` | 3113-3151 |
| `updateStreak()` | `ui/TrainingViewModel.kt` | 3165-3199 |
| `Normalizer.normalize()` | `data/Normalization.kt` | 4-21 |
| `MixedReviewScheduler.build()` | `data/MixedReviewScheduler.kt` | 28-111 |
| `MasteryStore.recordCardShow()` | `data/MasteryStore.kt` | 105-145 |
| `MasteryStore.markLessonCompleted()` | `data/MasteryStore.kt` | 165-172 |
| `ProgressStore.save()` | `data/ProgressStore.kt` | 78-115 |
| `FlowerCalculator.calculate()` | `data/FlowerCalculator.kt` | 20-67 |
| `SpacedRepetitionConfig.*` | `data/SpacedRepetitionConfig.kt` | 19-171 |
| UI dispatch (HomeScreen) | `ui/GrammarMateApp.kt` | 318-321 |
| UI dispatch (TrainingScreen) | `ui/GrammarMateApp.kt` | 427-454 |
