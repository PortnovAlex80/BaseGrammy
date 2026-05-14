# Scenario 09: Boss Battle Flow Trace

Trace of the complete Boss Battle lifecycle: unlock, start, card presentation, answer submission, timer, completion, rewards, failure, retry, and post-boss state changes. Each step includes code paths with file:line references, expected behavior (from specs 08, 18, 19), actual behavior (from code), and any discrepancies.

---

## 1. Boss Tile Unlock Condition

### Code path

- `GrammarMateApp.kt:1202` -- `val bossUnlocked = state.completedSubLessonCount >= 15 || state.testMode`
- `TrainingViewModel.kt:2197` -- Guard in `startBoss()`: `if (type != BossType.ELITE && state.completedSubLessonCount < 15 && !state.testMode)`
- `GrammarMateApp.kt:1196` -- `val hasMegaBoss = lessonIndex > 0` (Mega boss only for lessons beyond the first)

### Expected (spec)

- Spec 08 (8.10.3): "Unlock requirement: 15 completed sub-lessons (except ELITE and test mode)"
- Spec 18 (18.8.2): LESSON boss is "Available after completing all sub-lessons." MEGA boss is "Available after completing each lesson."
- Spec 19 (19.3): "Boss battles unlock when `completedSubLessonCount >= 15` or `testMode` is on"

### Actual (code)

- Boss tiles unlock when `completedSubLessonCount >= 15` or `testMode == true`.
- LESSON boss is always shown when unlocked.
- MEGA boss is shown only for lessons at index > 0.
- `BossType.ELITE` bypasses the 15-sub-lesson check entirely.

### Discrepancy

**Minor.** Spec 18 says "Available after completing all sub-lessons" for LESSON boss, but code unlocks after 15 sub-lessons. Since lessons typically have 15 sub-lessons (150 cards / 10 per sub-lesson), this is usually equivalent. However, for lessons with more than 150 cards, the boss unlocks before all sub-lessons are completed. The code's threshold of 15 is consistent with spec 08 and spec 19, so the wording in spec 18 is slightly imprecise.

---

## 2. LESSON Boss vs MEGA Boss vs ELITE Boss -- Card Selection

### Code path

- `TrainingViewModel.kt:2209-2222` -- `startBoss()` card selection:
  ```kotlin
  BossType.LESSON -> {
      val lessonCards = lessons.firstOrNull { it.id == selectedId }?.cards ?: emptyList()
      lessonCards.shuffled().take(maxBossCards)  // maxBossCards = 300
  }
  BossType.MEGA -> {
      if (selectedIndex <= 0) emptyList()
      else lessons.take(selectedIndex).flatMap { it.cards }.shuffled().take(maxBossCards)
  }
  BossType.ELITE -> {
      val eliteSize = eliteSubLessonSize() * eliteStepCount
      lessons.flatMap { it.cards }.shuffled().take(eliteSize)
  }
  ```

### Expected (spec)

- Spec 08 (8.5.4):
  - LESSON: "Current lesson cards, shuffled" -- max 300
  - MEGA: "All cards from lessons up to current (exclusive), shuffled" -- max 300
  - ELITE: "All cards from all lessons, shuffled" -- `eliteSubLessonSize() * eliteStepCount`
- Spec 18 (18.8.2): LESSON boss "Tests all sentences from one lesson." MEGA boss "Tests sentences from all lessons completed so far."

### Actual (code)

- **LESSON**: Uses `.cards` (which includes all cards, both main pool and reserve pool). Shuffled, capped at 300.
- **MEGA**: Uses `lessons.take(selectedIndex)` -- lessons UP TO but NOT INCLUDING the current lesson. Uses `.cards` (main + reserve). Shuffled, capped at 300.
- **ELITE**: All lessons' `.cards`, shuffled, size determined by config.

### Discrepancies

**MEGA boss scope.** Spec 18 says "all lessons completed so far" but spec 08 says "up to current (exclusive)." The code uses `lessons.take(selectedIndex)` which is exclusive of the current lesson. This is consistent with spec 08. The phrase "completed so far" in spec 18 is ambiguous -- it could mean all lessons up to and including, but the code clearly excludes the current lesson.

**LESSON boss uses `.cards` not `.mainPoolCards`.** The code shuffles ALL cards from the lesson (including reserve pool). Spec 18 (18.8.3) mentions different medal thresholds for "Active set only (main pool)" vs "Active set + Reserve set" -- this distinction is not reflected in the card selection code. All boss types draw from the full card set.

**Minor.** Spec 18 medal thresholds mention "Active set only" for BRONZE/SILVER and "Active set + Reserve set" for GOLD. The code has no pool-aware card selection -- it always uses all cards. See section 7 below for more on this.

---

## 3. Boss Battle Start -- State Initialization

### Code path

- `TrainingViewModel.kt:2193-2267` -- `startBoss(type)`:
  1. `pauseTimer()` -- stops any active timer
  2. Guard checks: unlock, lesson selected
  3. Card selection (see step 2)
  4. `bossCards = cards`, `sessionCards = bossCards`, `subLessonTotal = bossCards.size`
  5. State update:
     - `bossActive = true`, `bossType = type`, `bossTotal = bossCards.size`, `bossProgress = 0`
     - `bossReward = null`, `bossRewardMessage = null`, `bossErrorMessage = null`
     - `currentIndex = 0`, `currentCard = firstCard`
     - `correctCount = 0`, `incorrectCount = 0`, `activeTimeMs = 0L`
     - `sessionState = SessionState.PAUSED`
     - `subLessonCount = 1`, `activeSubLessonIndex = 0`, `completedSubLessonCount = 0`

### Expected (spec)

- Spec 08 (8.5.4): "Sets `bossActive = true`, resets counters, creates single sub-lesson. `sessionCards = bossCards`, first card loaded."

### Actual (code)

- State is fully reset as described. Session starts in PAUSED state (user must press Play/togglePause to begin).
- `completedSubLessonCount` is reset to 0 during boss -- this means the boss session doesn't carry over previous completion counts.

### Discrepancy

**None.** Code matches spec.

---

## 4. Card Presentation in Boss Mode

### Code path

- `GrammarMateApp.kt:2593` -- Boss mode check: `if (state.bossActive) { Text(text = "Review Session", ...) }`
- `GrammarMateApp.kt:2647-2654` -- Progress bar uses `bossProgress`/`bossTotal` when `bossActive`
- `GrammarMateApp.kt:2705` -- Stats header shows "Review" label instead of "Progress"
- `GrammarMateApp.kt:2707` -- Progress text: `"${progressPercent}% (${progressIndex}/${total})"`

### Expected (spec)

- Spec 19 (19.12): "Layout: Identical to Training Screen layout, with 'Review Session' title instead of lesson info."
- Spec 19 (19.4): Training screen layout with card prompt, input controls, navigation row.

### Actual (code)

- Boss mode reuses the exact same Training Screen composable.
- Title changes to "Review Session" (line 2594).
- Stats row shows "Review" label with percentage and progress count.
- Same card prompt, answer input, navigation controls as regular training.
- No visual distinction for boss mode beyond the title/label changes.

### Discrepancy

**None.** Code matches spec.

---

## 5. Answer Submission in Boss Mode -- Mastery Issue

### Code path

- `TrainingViewModel.kt:629-678` -- `submitAnswer()` boss handling:
  - On correct answer: `recordCardShowForMastery(card)` is called **unconditionally** at line 632, BEFORE the boss-specific branch.
  - Boss correct (not last card): line 657-678 -- increments counters, calls `nextCard(triggerVoice)`
  - Boss correct (last card): line 634-656 -- increments counters, calls `updateBossProgress(state.bossTotal)`, then `finishBoss()`
- `TrainingViewModel.kt:2896-2915` -- `recordCardShowForMastery(card)`:
  ```kotlin
  if (_uiState.value.isDrillMode) return  // Drill mode: skip
  // NO check for bossActive
  val isWordBankMode = _uiState.value.inputMode == InputMode.WORD_BANK
  if (isWordBankMode) return  // Word Bank: skip
  masteryStore.recordCardShow(lessonId, languageId, card.id)
  ```
- `TrainingViewModel.kt:886` -- `nextCard()` also calls `recordCardShowForMastery(it)` for the next card.
- `TrainingViewModel.kt:2422` -- `startSession()` also calls `recordCardShowForMastery()`.

### Expected (spec)

- Spec 18 (18.8.4): "**Boss battles are separate from the mastery/flower system. They do not directly affect `uniqueCardShows` or flower state.**"
- Spec 08 (8.7.1): Lists when `recordCardShowForMastery` is called but does not explicitly exclude boss mode.

### Actual (code)

- Boss battles DO count toward mastery. The `recordCardShowForMastery()` function has guards for `isDrillMode` and `WORD_BANK` but **no guard for `bossActive`**.
- Every correct answer during a boss battle (VOICE/KEYBOARD) calls `masteryStore.recordCardShow()`, which increments `uniqueCardShows` for the resolved lesson and advances the SRS interval step.
- Even `nextCard()` during boss mode calls `recordCardShowForMastery()` for the new card (line 886), so mastery is recorded on navigation as well.

### Discrepancy

**CRITICAL DISCREPANCY (confirmed by scenario-03).** This was already identified in `scenario-03-mastery-flower.md` (Test Case 12). The spec explicitly states boss battles should NOT affect mastery or flower state, but the code records mastery for all correct boss answers.

**Impact:**
1. Boss practice inflates `uniqueCardShows`, allowing learners to reach BLOOM flower state without completing sub-lessons.
2. Boss answers advance SRS interval steps (`intervalStepIndex`), changing the review schedule.
3. The spec's characterization of boss battles as "validation" and "diagnostic tool" (spec 18.8.4) is undermined.

**Suggested fix:** Add `if (_uiState.value.bossActive) return` at the top of `recordCardShowForMastery()`, similar to the existing `isDrillMode` check.

---

## 6. Boss Timer

### Code path

- `TrainingViewModel.kt:2432-2450` -- `resumeTimer()`: standard timer coroutine, ticks every 500ms
- `TrainingViewModel.kt:2447-2450` -- `pauseTimer()`: cancels timer job
- `TrainingViewModel.kt:2194` -- `startBoss()` calls `pauseTimer()` at the beginning
- `TrainingViewModel.kt:2261` -- Boss starts in `SessionState.PAUSED`, timer not running

### Expected (spec)

- Spec 08 (8.10.5): Timer ticks every 500ms, accumulates `activeTimeMs`, saves progress on each tick.
- Spec 08 (8.10.3): "Boss sessions are not saved mid-battle (`saveProgress()` skips when `bossActive && bossType != ELITE`)"
- No spec mentions a countdown timer or time limit for boss battles.

### Actual (code)

- Boss uses the same generic session timer as regular training (no dedicated boss timer).
- **No countdown.** There is no time limit for boss battles. The timer only measures elapsed time.
- Timer ticks every 500ms but `saveProgress()` returns early during boss (line 2455: `if (state.bossActive && state.bossType != BossType.ELITE) return`).
- Timer accumulates `activeTimeMs` during boss but the value is not persisted until boss finishes.
- Boss starts in PAUSED state -- user must press Play to start the timer.

### Discrepancy

**None.** The spec does not describe a countdown timer or time pressure mechanism for boss battles. The spec 18 (18.8.1) mentions "pressure" conceptually ("pattern stability under pressure") but does not specify a time limit. The "pressure" comes from the volume of cards, not a timer.

---

## 7. Boss Completion -- Reward Calculation

### Code path

- `TrainingViewModel.kt:2382-2390` -- `resolveBossReward()`:
  ```kotlin
  val percent = (progress.toDouble() / total.toDouble()) * 100.0
  return when {
      percent >= 100.0 -> BossReward.GOLD
      percent > 75.0 -> BossReward.SILVER
      percent > 50.0 -> BossReward.BRONZE
      else -> null
  }
  ```

### Expected (spec)

- Spec 08 (8.5.4):
  - BRONZE: > 50%
  - SILVER: > 75%
  - GOLD: >= 100%
- Spec 18 (18.8.3):
  - BRONZE: >= 30% -- "Active set only (main pool, first 150 cards)"
  - SILVER: >= 60-70% -- "Active set only"
  - GOLD: 100% -- "Active set + Reserve set (all cards)"

### Actual (code)

- Thresholds: BRONZE > 50%, SILVER > 75%, GOLD >= 100%
- `bossProgress` tracks how many cards were answered correctly (it's set to the max of current progress and the current index on each `nextCard()` call, line 849-851).
- There is NO pool-aware card selection. All bosses draw from the full card set (`.cards`), not from separate active/reserve pools.

### Discrepancies

**Threshold mismatch.** Spec 18 states BRONZE >= 30%, SILVER >= 60-70%, GOLD 100%. Code uses BRONZE > 50%, SILVER > 75%, GOLD >= 100%. The code's thresholds are significantly higher than the spec's.

**No pool-based reward tiers.** Spec 18 describes a system where BRONZE/SILVER test only the "Active set" (main pool, first 150 cards) and GOLD tests "Active set + Reserve set." The code has no such pool distinction -- all boss types draw from the full card set and the same thresholds apply regardless.

**Reward is based on progress through cards, not accuracy.** The `bossProgress` field is set to `max(bossProgress, nextIndex)` in `nextCard()` (line 849-851). This means progress advances for every card the user reaches, regardless of whether they answered correctly. However, `submitAnswer()` only increments `bossProgress` on correct answers through `updateBossProgress()`. Looking more carefully:

- On correct answer (not last card): `nextCard()` is called, which sets `bossProgress = max(bossProgress, nextIndex)`. The next index is `currentIndex + 1`.
- On correct answer (last card): `updateBossProgress(bossTotal)` is called, setting progress to the total.
- On incorrect answer: progress does NOT advance. The user stays on the same card.

So `bossProgress` effectively equals the number of correctly answered cards (the highest index reached). The reward is based on what percentage of the total cards were answered correctly. This is correct behavior, just the thresholds differ from spec 18.

---

## 8. Boss Failure -- What Happens?

### Code path

- `TrainingViewModel.kt:634-656` -- On last correct card in boss: `updateBossProgress(state.bossTotal)`, then `finishBoss()`
- `TrainingViewModel.kt:2270-2329` -- `finishBoss()`:
  - Resolves reward (can be `null` if progress < 50%)
  - Records the reward in `bossLessonRewards` or `bossMegaRewards`
  - Clears boss state
  - Restores previous lesson/session state from ProgressStore
  - Navigates back to lesson roadmap

- `GrammarMateApp.kt:518-528` -- Exit dialog during boss:
  ```kotlin
  if (state.bossActive) {
      vm.finishBoss()
      screen = AppScreen.LESSON
  }
  ```

### Expected (spec)

- Spec 08 (8.10.3): "Single attempt: Boss sessions are not saved mid-battle"
- Spec does not describe explicit "failure" -- user can exit at any time or complete the battle.
- No retry mechanism is described in the spec.

### Actual (code)

- **No explicit failure state.** There is no "boss failed" condition. The user either:
  1. Completes all cards (GOLD)
  2. Exits early via the exit dialog (gets BRONZE/SILVER based on how far they got)
  3. Incorrect answers don't end the battle -- user retries until correct (or 3 wrong attempts reveal the answer)
- **Boss can be retried.** After completing/exiting a boss battle, the user is returned to the lesson roadmap where the boss tile is still available. They can start a new boss battle immediately.
- **Reward overwrites.** In `finishBoss()` (line 2279): `state.bossLessonRewards + (lessonId to reward)` -- if the same lesson already has a reward, it's overwritten. Better rewards don't block worse ones -- the latest result is kept.

### Discrepancy

**Minor.** Spec 18 (18.8.3) describes the medal system as aspirational goals, but doesn't specify retry behavior. The code allows unlimited retries and keeps only the latest reward (not the best). This means a learner could get GOLD, then play again and "lose" their reward by getting BRONZE. This seems unintentional -- `bossLessonRewards` should probably keep the best reward, not the latest.

---

## 9. Boss Reward Dialog

### Code path

- `GrammarMateApp.kt:612-636` -- Boss reward dialog:
  ```kotlin
  if (state.bossRewardMessage != null && state.bossReward != null) {
      AlertDialog(
          icon = { /* Trophy icon colored by reward */ },
          title = { Text("Boss Reward") },
          text = { Text(state.bossRewardMessage) },
          confirmButton = { TextButton(onClick = { vm.clearBossRewardMessage() }) { Text("OK") } }
      )
  }
  ```
- `GrammarMateApp.kt:621-625` -- Trophy colors: BRONZE = `#CD7F32`, SILVER = `#C0C0C0`, GOLD = `#FFD700`
- `TrainingViewModel.kt:2332-2352` -- `clearBossRewardMessage()`:
  - If boss is still active and paused, resumes the timer and triggers voice if needed
  - Clears the reward message from state

### Expected (spec)

- Spec 19 (19.23): Trophy icon colored by reward, "Boss Reward" title, reward message text, "OK" button.

### Actual (code)

- Dialog matches spec description exactly.
- Trophy uses `Icons.Default.EmojiEvents` with color tinting.
- Message is generated by `bossRewardMessage()`: "Bronze reached", "Silver reached", "Gold reached".
- Mid-boss reward pauses the session; dismissing resumes it.

### Discrepancy

**None.** Code matches spec.

---

## 10. After Boss Completion -- State Changes

### Code path

- `TrainingViewModel.kt:2270-2329` -- `finishBoss()`:
  1. Resolves final reward via `resolveBossReward()`
  2. Updates `bossLessonRewards` (for LESSON type) or `bossMegaRewards` (for MEGA type)
  3. Clears boss state: `bossActive = false`, `bossType = null`, `bossTotal = 0`, `bossProgress = 0`
  4. Increments `bossFinishedToken`
  5. Restores previous lesson/session state from `progressStore.load()`: `selectedLessonId`, `currentIndex`, `correctCount`, `incorrectCount`, etc.
  6. Calls `buildSessionCards()` -- rebuilds normal session cards
  7. Calls `saveProgress()` -- persists the updated state (including reward maps)
  8. Calls `refreshFlowerStates()` -- recalculates flower visuals for all lessons

- `GrammarMateApp.kt:513-516` -- Token-based navigation:
  ```kotlin
  if (screen == AppScreen.TRAINING && state.bossFinishedToken != lastBossFinishedToken.value) {
      lastBossFinishedToken.value = state.bossFinishedToken
      screen = AppScreen.LESSON
  }
  ```

### Expected (spec)

- Spec 08 (8.5.4): "Clear boss state, restore previous lesson/session state from ProgressStore. Rebuild session cards, save progress, refresh flowers."
- Spec 08 (8.10.3): "State restoration after boss: Previous lesson ID and session state are restored from ProgressStore"

### Actual (code)

- After boss, the user is navigated back to the LESSON roadmap screen (not HOME).
- Previous training state is fully restored from ProgressStore.
- Boss rewards are persisted in `bossLessonRewards` / `bossMegaRewards` maps.
- `refreshFlowerStates()` is called -- this recalculate flower visuals, which will reflect any mastery changes made during the boss battle (see discrepancy in step 5).
- Lesson is NOT marked as completed by boss completion. `checkAndMarkLessonCompleted()` is NOT called in `finishBoss()`.

### Discrepancy

**Flower refresh includes boss mastery inflation.** Because `recordCardShowForMastery()` records mastery during boss battles (step 5 discrepancy), the `refreshFlowerStates()` call after boss will show inflated flower states. This compounds the critical mastery issue.

---

## 11. Multiple Boss Battles Per Lesson

### Code path

- `GrammarMateApp.kt:1196-1197` -- `hasMegaBoss = lessonIndex > 0` -- MEGA boss only for non-first lessons
- `GrammarMateApp.kt:1299-1317` -- Roadmap always shows BossLesson tile, and BossMega tile if `hasMegaBoss`
- `TrainingViewModel.kt:2270-2329` -- `finishBoss()` does NOT disable the boss tile. It only records the reward and clears state.
- `TrainingViewModel.kt:2197` -- `startBoss()` unlock guard checks `completedSubLessonCount >= 15`, which remains true after a boss.

### Expected (spec)

- No explicit mention of whether multiple boss battles are allowed.

### Actual (code)

- Multiple boss battles ARE possible. The user can replay boss battles unlimited times.
- Each replay overwrites the previous reward (not best-of).
- The `bossLessonRewards` and `bossMegaRewards` maps store one reward per lesson ID.

### Discrepancy

**None explicit.** But the reward-overwrite behavior (latest, not best) seems like a design oversight. If boss battles are repeatable challenges, the system should probably keep the best reward.

---

## 12. Boss Battle in ALL_MIXED Mode

### Code path

- `TrainingViewModel.kt:2193-2267` -- `startBoss()` is mode-independent. It uses `state.selectedLessonId` and `state.lessons` regardless of the current `TrainingMode`.
- `TrainingViewModel.kt:2207` -- `val selectedIndex = lessons.indexOfFirst { it.id == selectedId }`
- `TrainingViewModel.kt:2210-2212` -- LESSON boss: `lessons.firstOrNull { it.id == selectedId }?.cards` -- always uses the selected lesson's cards.

### Expected (spec)

- No explicit mention of boss interaction with ALL_MIXED mode.

### Actual (code)

- Boss battles work identically regardless of training mode. `startBoss()` reads the current lessons and selected lesson ID, which are available in any mode.
- After boss finishes, the mode is restored from ProgressStore (line 2311: `mode = progress.mode`).

### Discrepancy

**None.** Boss mode is self-contained and mode-agnostic.

---

## 13. Boss Battle Mastery Issue -- Confirmed

### Summary

This is a full confirmation of the issue identified in scenario-03.

**Three call sites record mastery during boss:**

1. `TrainingViewModel.kt:632` -- `submitAnswer()`, on correct answer: `recordCardShowForMastery(card)` -- called BEFORE the boss-specific branch. This is unconditional.
2. `TrainingViewModel.kt:886` -- `nextCard()`: `nextCard?.let { recordCardShowForMastery(it) }` -- called for every card navigation, including during boss.
3. `TrainingViewModel.kt:2422` -- `startSession()`: `currentCard()?.let { recordCardShowForMastery(it) }` -- called when session resumes, which happens during boss.

**Why it happens:**
- `recordCardShowForMastery()` checks `isDrillMode` and `WORD_BANK` but NOT `bossActive`.
- The spec (18.8.4) explicitly states boss battles should not affect mastery.
- The function should have `if (_uiState.value.bossActive) return` added.

**Impact:**
- Boss answers inflate `uniqueCardShows` for resolved lessons.
- Boss answers advance `intervalStepIndex`, changing SRS review schedules.
- Flowers grow during boss battles, undermining the diagnostic/validation purpose.

---

## 14. Boss Battle SRS Interval -- Does Boss Advance Intervals?

### Code path

- `MasteryStore.kt:105-145` -- `recordCardShow()`:
  ```kotlin
  val daysSinceLastShow = ...
  val wasOnTime = SpacedRepetitionConfig.wasRepetitionOnTime(daysSinceLastShow, currentStep)
  val newStep = if (existing != null && daysSinceLastShow > 0) {
      SpacedRepetitionConfig.nextIntervalStep(currentStep, wasOnTime)
  } else {
      currentStep
  }
  ```
- Since `recordCardShowForMastery()` calls `masteryStore.recordCardShow()` during boss (see step 13), the SRS interval step IS advanced.

### Expected (spec)

- Spec 18 (18.8.4): Boss battles are "separate from the mastery/flower system" and serve as "validation" and "diagnostic tool."

### Actual (code)

- Boss battles DO advance SRS interval steps because they call `masteryStore.recordCardShow()`, which calculates `nextIntervalStep`.
- The `lastShowDateMs` is also updated, which affects health calculations.

### Discrepancy

**CRITICAL (same as step 13).** SRS intervals should not be affected by boss battles per the spec.

---

## 15. Boss Battle with WORD_BANK Mode

### Code path

- `TrainingViewModel.kt:2896-2915` -- `recordCardShowForMastery()`:
  ```kotlin
  val isWordBankMode = _uiState.value.inputMode == InputMode.WORD_BANK
  if (isWordBankMode) return  // Skip mastery
  ```
- The user can switch input modes during boss (the UI shows mode selectors).
- `TrainingViewModel.kt:629-630` -- Correct answer check works the same regardless of mode.
- `GrammarMateApp.kt:2593+` -- Boss mode UI renders the same input controls as regular training.

### Expected (spec)

- WORD_BANK mode should not count for mastery. This applies to boss battles too.

### Actual (code)

- WORD_BANK during boss correctly skips mastery recording due to the early return in `recordCardShowForMastery()`.
- However, for VOICE and KEYBOARD modes during boss, mastery IS recorded (see step 13).
- Boss progress (`bossProgress`) still advances on correct WORD_BANK answers, affecting the reward calculation.

### Discrepancy

**Partial.** WORD_BANK correctly doesn't count for mastery during boss, but VOICE/KEYBOARD incorrectly DO count. The boss progress/reward calculation treats all input modes equally for progress advancement, which is correct -- the reward measures completion, not mastery.

---

## Summary of Discrepancies

| # | Severity | Area | Description |
|---|----------|------|-------------|
| 1 | CRITICAL | Mastery (steps 5, 13, 14) | Boss battles record mastery (`uniqueCardShows`, `intervalStepIndex`) via `recordCardShowForMastery()`, directly contradicting spec 18.8.4 which states boss battles are separate from the mastery/flower system. Three call sites are affected: `submitAnswer()` line 632, `nextCard()` line 886, `startSession()` line 2422. |
| 2 | MAJOR | Reward thresholds (step 7) | Code thresholds (BRONZE > 50%, SILVER > 75%) differ from spec 18 (BRONZE >= 30%, SILVER >= 60-70%). The code's thresholds are higher. |
| 3 | MAJOR | Pool-based rewards (step 7) | Spec 18 describes pool-aware medal tiers (active set only for BRONZE/SILVER, active + reserve for GOLD). Code has no pool distinction. |
| 4 | MINOR | Reward overwrite (step 8, 11) | Replaying a boss overwrites the previous reward with the latest result, not the best. A GOLD could be replaced by BRONZE. |
| 5 | MINOR | Unlock wording (step 1) | Spec 18 says "after completing all sub-lessons" but code unlocks after 15. Usually equivalent but differs for large lessons. |
| 6 | NONE | Timer (step 6) | No countdown timer for boss. Spec does not require one; "pressure" is from card volume, not time. |
| 7 | NONE | Card presentation (step 4) | Boss mode correctly reuses Training Screen with "Review Session" title. |
| 8 | NONE | Completion flow (step 10) | Post-boss state restoration, navigation, and persistence all match spec. |

### Recommended Fix Priority

1. **Add `bossActive` guard to `recordCardShowForMastery()`** -- single-line fix that resolves discrepancies #1 (CRITICAL) by preventing boss mastery inflation.
2. **Align reward thresholds** with spec 18 if the spec values (30%/60-70%) are the intended design, or update the spec to match the code (50%/75%) if the current thresholds are preferred.
3. **Add pool-based card selection** for boss battles to match spec 18's "Active set" vs "Active set + Reserve set" medal tiers, or update the spec to reflect the simplified approach.
4. **Keep best reward** instead of overwriting on replay.
