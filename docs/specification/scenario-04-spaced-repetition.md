# Scenario 04: Spaced Repetition Scheduling -- Code Trace

Trace of spaced repetition scheduling through the GrammarMate codebase against specification documents. Each test case follows the complete code path from user action to persistence and back to display.

---

## Test Case 1: Card completed for first time -- interval set to 1 day

### Code path

1. User answers card via VOICE or KEYBOARD mode.
2. `TrainingViewModel.recordCardShowForMastery()` at `TrainingViewModel.kt:2896`.
   - Checks `_uiState.value.isDrillMode` (returns early if drill mode).
   - Checks `InputMode.WORD_BANK` (returns early -- WORD_BANK never counts).
   - Calls `masteryStore.recordCardShow(lessonId, languageId, card.id)`.
3. `MasteryStore.recordCardShow()` at `MasteryStore.kt:105`.
   - `existing` is null (first time).
   - `isNewCard = true` (card ID not in empty set).
   - `daysSinceLastShow = 0` (existing is null, falls to else branch at line 118).
   - `currentStep = 0` (existing is null, falls to default at line 121).
   - `wasOnTime` not computed in the branch where `existing == null || daysSinceLastShow <= 0` -- goes to else at line 126: `newStep = currentStep` which is `0`.
   - Saves `LessonMasteryState(intervalStepIndex = 0, uniqueCardShows = 1, lastShowDateMs = now)`.

### Expected (from spec)

First card show sets `intervalStepIndex = 0`. The interval at step 0 is `INTERVAL_LADDER_DAYS[0] = 1` day. So the next expected review is in 1 day.

### Actual (from code)

`intervalStepIndex = 0`. The ladder value at index 0 is 1 day. The card is "due" in 1 day for the next review check.

### Discrepancy?

**No.** Code matches spec. First show results in step 0 (1-day interval). Note: `nextIntervalStep` is NOT called on first show (the code at `MasteryStore.kt:123` only calls it when `existing != null && daysSinceLastShow > 0`). The step stays at 0. This is correct -- there is no "previous repetition" to evaluate timeliness for.

---

## Test Case 2: Card reviewed on time -- interval advances to 2 days

### Code path

1. User answers the same lesson's card 2 days later.
2. `MasteryStore.recordCardShow()` at `MasteryStore.kt:105`.
   - `existing` is loaded with `intervalStepIndex = 0`, `lastShowDateMs = 2 days ago`.
   - `daysSinceLastShow = 2`.
   - `currentStep = 0`.
   - `wasRepetitionOnTime(2, 0)` at `SpacedRepetitionConfig.kt:161`:
     - `expectedInterval = INTERVAL_LADDER_DAYS[0] = 1`.
     - Returns `daysSinceLastShow <= expectedInterval` => `2 <= 1` => **false**.
   - Wait -- 2 days after a step-0 interval (expected 1 day) is LATE.

Let me retrace with exactly 1 day:

- `daysSinceLastShow = 1`.
- `wasRepetitionOnTime(1, 0)` => `1 <= 1` => **true**.
- `nextIntervalStep(0, true)` at `SpacedRepetitionConfig.kt:144`:
  - Returns `(0 + 1).coerceAtMost(9) = 1`.
- Saves `intervalStepIndex = 1`. Ladder value at index 1 = **2 days**.

### Expected (from spec)

On-time review at step 0 advances to step 1 (2-day interval). Formula: `nextIntervalStep(0, true) = min(0+1, 9) = 1`.

### Actual (from code)

Step advances to 1. `INTERVAL_LADDER_DAYS[1] = 2`. Correct.

### Discrepancy?

**No.** On-time review advances one step. The stability calculation is:
- `calculateStability(1) = 0.9 * 2.2^1 = 1.98 days`.

At step 1, with `daysSinceLastShow = 2`, `calculateRetention(2, 1) = e^(-2/1.98) = e^(-1.01) ~ 0.36` -- this is the raw retention, used internally but not displayed. Health would be 1.0 since 2 <= expectedInterval(2).

---

## Test Case 3: Card NOT reviewed for 3 days (interval was 2) -- overdue health

### Code path

Scenario: `intervalStepIndex = 1` (expected interval = 2 days), 3 days since last show.

1. `FlowerCalculator.calculate()` calls `SpacedRepetitionConfig.calculateHealthPercent(3, 1)` at `SpacedRepetitionConfig.kt:106`.
2. `daysSinceLastShow = 3` > 0, not >= 90, so continues.
3. `expectedInterval = INTERVAL_LADDER_DAYS[1] = 2`.
4. `daysSinceLastShow (3) > expectedInterval (2)` -- overdue.
5. `overdueDays = 3 - 2 = 1`.
6. `stability = calculateStability(1) = 0.9 * 2.2 = 1.98`.
7. `decay = e^(-1 / 1.98) = e^(-0.505) ~ 0.603`.
8. `health = 0.5 + (1.0 - 0.5) * 0.603 = 0.5 + 0.302 = 0.802`.
9. Clamped to `[0.5, 1.0]` => health = **0.802** (80.2%).

### Expected (from spec)

Health formula: `health = 0.5 + 0.5 * e^(-overdueDays / stability)`. With overdueDays=1, stability=1.98: health ~ 0.80.

### Actual (from code)

health = 0.802. Flower state: WILTING (health < 1.0 but > 0.5).

### Discrepancy?

**No.** Code matches spec. The flower enters WILTING state at `FlowerCalculator.kt:83` when `healthPercent < 1.0`.

---

## Test Case 4: Card overdue by 30 days -- health floor

### Code path

Scenario: `intervalStepIndex = 1` (expected = 2 days), 30 days since last show.

1. `calculateHealthPercent(30, 1)` at `SpacedRepetitionConfig.kt:106`.
2. 30 > 0, 30 < 90 -- continues.
3. `expectedInterval = 2`.
4. `overdueDays = 30 - 2 = 28`.
5. `stability = calculateStability(1) = 1.98`.
6. `decay = e^(-28 / 1.98) = e^(-14.14) ~ 0.00000073`.
7. `health = 0.5 + 0.5 * 0.00000073 ~ 0.500000037`.
8. Clamped to `[0.5, 1.0]` => health = **0.5** (floor).

### Expected (from spec)

Health floor is `WILTED_THRESHOLD = 0.5`. After very long overdue periods, health approaches 0.5 asymptotically. The floor is enforced by `coerceIn(WILTED_THRESHOLD, 1.0f)`.

### Actual (from code)

Health = 0.5 (within floating-point epsilon). Flower state: WILTED (`healthPercent <= 0.5 + 0.01` at `FlowerCalculator.kt:80`).

### Discrepancy?

**No.** The floor is correctly enforced. The flower enters WILTED state due to the epsilon check at `FlowerCalculator.kt:80`.

---

## Test Case 5: Mixed sub-lesson -- review card selection and due-date calculation

### Code path

1. `MixedReviewScheduler.build(lessons)` at `MixedReviewScheduler.kt:28`.
2. For lessons beyond the first (`lessonIndex > 0`), mixed sub-lessons are generated.
3. The `globalMixedIndex` counter starts at 0 and increments by 1 for each mixed sub-lesson (line 73).
4. When a lesson is first encountered, the *previous* lesson's `reviewStartMixedIndex` is recorded (line 40).
5. `dueLessonIds()` at `MixedReviewScheduler.kt:113` checks if `globalMixedIndex - startIndex` matches a value in the intervals list `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]`.
6. Up to 2 due lessons contribute review cards per mixed sub-lesson (line 80: `.take(2)`).
7. Review cards are filled via `fillReviewSlots()` at `MixedReviewScheduler.kt:129`:
   - Priority 1: Reserve pool cards from due lessons (round-robin).
   - Priority 2: Main pool cards from due lessons (round-robin).
   - Priority 3: Fallback from current lesson's mixed queue.

### Expected (from spec)

Review cards are selected when the difference between the current global mixed index and the lesson's start index matches an interval ladder value. Reserve pool cards are preferred. Maximum 2 due lessons per sub-lesson.

### Actual (from code)

The due-date check uses `intervals.contains(step)` (line 122), where `step = globalMixedIndex - startIndex`. This is an exact match against the ladder values, NOT a "days since" comparison. The MixedReviewScheduler uses the same ladder `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` but interprets it as "number of mixed sub-lessons since the lesson became eligible," not calendar days.

### Discrepancy?

**YES -- conceptually.** The spec (Section 18.4.2) says: "This mirrors the same interval ladder used for mastery but applies it to the scheduling of review insertions." The code does exactly this. However, there is a semantic difference:

- **MasteryStore SRS**: Uses **calendar days** for interval tracking (`daysSinceLastShow` computed from `System.currentTimeMillis()`).
- **MixedReviewScheduler**: Uses **sub-lesson counts** (the `globalMixedIndex`) for interval matching.

These are two different time bases. The spec acknowledges this at Section 18.1.2: "the original project specification envisioned intervals measured in 'completed sub-lessons' to accommodate irregular study schedules, the implementation uses calendar days." But the MixedReviewScheduler still uses the sub-lesson-count approach. This is a design choice, not a bug -- the MixedReviewScheduler is a static schedule generator, not a dynamic SRS. It pre-computes the entire lesson structure, not per-card intervals.

---

## Test Case 6: globalMixedIndex -- what it is and how it determines ladder steps

### Code path

1. `globalMixedIndex` is initialized to 0 at `MixedReviewScheduler.kt:35`.
2. It increments by 1 for each MIXED sub-lesson generated across ALL lessons (line 73).
3. For each previous lesson, `reviewStartMixedIndex[lessonId]` records the value of `globalMixedIndex` at the point where that lesson finishes its NEW_ONLY portion and the next lesson begins (line 40).
4. At any given mixed sub-lesson, `dueLessonIds()` computes `step = globalMixedIndex - startIndex` for each lesson. If `step` is in `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]`, that lesson is due for review.

Example: If lesson 1 has `reviewStartMixedIndex = 5`, then:
- At globalMixedIndex 6 (step=1): lesson 1 is due (1 is in intervals).
- At globalMixedIndex 7 (step=2): lesson 1 is due (2 is in intervals).
- At globalMixedIndex 9 (step=4): lesson 1 is due (4 is in intervals).
- At globalMixedIndex 10 (step=5): lesson 1 is NOT due (5 is not in intervals).

### Expected (from spec)

The globalMixedIndex acts as a monotonic counter that determines when review cards from earlier lessons are interleaved. Reviews happen at intervals matching the ladder values.

### Actual (from code)

Matches expected behavior. The counter is monotonically increasing and shared across all lessons. Each lesson records its start index when the NEXT lesson begins processing.

### Discrepancy?

**No.**

---

## Test Case 7: Reserve pool review cards -- selection vs main pool

### Code path

1. `MixedReviewScheduler.build()` at `MixedReviewScheduler.kt:44-51`:
   - `allReviewCards = (lesson.mainPoolCards + lesson.reservePoolCards).shuffled().take(300)`.
   - `mainCount = max(allReviewCards.size / 2, min(lesson.mainPoolCards.size, 150))`.
   - `reviewQueues[lesson.id] = first mainCount cards`.
   - `reserveQueues[lesson.id] = remaining cards`.

2. `fillReviewSlots()` at `MixedReviewScheduler.kt:129`:
   - Priority 1 (lines 140-163): Round-robin from `reserveQueues` of due lessons.
   - Priority 2 (lines 167-191): Round-robin from `reviewQueues` of due lessons.
   - Priority 3 (lines 195-197): Fallback from current lesson's `mixedCurrentQueue`.

### Expected (from spec)

Reserve pool cards (cards beyond the first 150) are preferred for review because they were not part of original mastery measurement, preventing phrase memorization.

### Actual (from code)

Reserve cards are tried first. However, note the initial split: the combined pool is shuffled first, then split. So "reserve" in `reserveQueues` is not necessarily the same as `lesson.reservePoolCards` -- it's the second half of the shuffled combined pool (after taking `mainCount`). For a lesson with exactly 150 cards, `reserveQueues` would contain `max(0, 300-150) = 150`... wait, the lesson only has 150 cards. So `allReviewCards.size = 150`, `mainCount = max(75, min(150, 150)) = 150`, and `reserveQueues` gets 0 cards.

For a lesson with 300 cards: `allReviewCards.size = 300` (capped), `mainCount = max(150, min(150, 150)) = 150`. `reviewQueues` gets 150, `reserveQueues` gets 150.

### Discrepancy?

**Minor.** The 50/50 split of the shuffled pool means that "reserve" cards in the scheduler may include main pool cards (since the pool is shuffled before splitting). The spec's conceptual model ("reserve pool cards first") is implemented, but the actual pool composition is mixed due to the shuffle-then-split approach. This is an intentional design choice for variety, not a bug.

---

## Test Case 8: 300-card review cap

### Code path

1. `MixedReviewScheduler.kt:25`: `MAX_REVIEW_CARDS = 300`.
2. `MixedReviewScheduler.kt:44-46`:
   ```kotlin
   val allReviewCards = (lesson.mainPoolCards + lesson.reservePoolCards)
       .shuffled()
       .take(MAX_REVIEW_CARDS)
   ```
3. The cap is applied per-lesson during the review pool preparation phase. Excess cards (beyond 300) are simply discarded.

### Expected (from spec)

`MAX_REVIEW_CARDS = 300` limits the total review card pool per lesson. The cap is applied by shuffling the combined pool and taking the first 300.

### Actual (from code)

Exactly as described. The cap is applied once during `build()`, not during card selection. After the initial cap, the 300 cards are split into review/reserve queues and consumed as needed.

### Discrepancy?

**No.**

---

## Test Case 9: Vocab drill SRS -- VocabProgressStore ladder vs SpacedRepetitionConfig

### Code path

**VocabProgressStore** at `VocabProgressStore.kt`:

1. Uses its OWN interval ladder: `INTERVALS_DAYS = intArrayOf(1, 3, 7, 14, 30)` (line 42). This is a 5-step ladder, NOT the 10-step ladder from `SpacedRepetitionConfig`.
2. `recordCorrect()` at line 148: advances `intervalStep` by 1, capped at `INTERVALS_DAYS.lastIndex` (4).
3. `recordIncorrect()` at line 173: resets `intervalStep` to 0.
4. `isDueForReview()` at line 196: computes `dueMs = lastCorrectMs + INTERVALS_DAYS[step] * DAY_MS` and checks against `System.currentTimeMillis()`.

**Comparison with SpacedRepetitionConfig:**

| Aspect | SpacedRepetitionConfig | VocabProgressStore |
|--------|----------------------|-------------------|
| Ladder | `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` (10 steps) | `[1, 3, 7, 14, 30]` (5 steps) |
| On correct | +1 step (capped at 9) | +1 step (capped at 4) |
| On incorrect | No change (step stays) | Reset to step 0 |
| Health/decay | Ebbinghaus curve | None (binary: due/not-due) |
| Max interval | 56 days | 30 days |

### Expected (from spec)

The spec (Section 18.10.2 item 6) acknowledges: "The `VocabProgressStore` uses a separate, shorter interval ladder [1, 3, 7, 14, 30 days] for vocabulary." The spec describes this as appropriate.

### Actual (from code)

VocabProgressStore uses a shorter, simpler ladder. Incorrect answers cause a full reset (unlike MasteryStore where the step does not regress). There is no health/decay calculation -- words are either due or not due.

### Discrepancy?

**Yes -- significant design difference in incorrect handling.**
- `MasteryStore` (via `SpacedRepetitionConfig.nextIntervalStep`): Late/incorrect reviews do NOT regress the step. The step stays unchanged.
- `VocabProgressStore.recordIncorrect()`: Resets `intervalStep` to 0 (full regression).

This is an intentional design difference: vocabulary words use a harsher penalty (full reset on incorrect) while lesson mastery uses a gentler approach (no regression). The spec does not explicitly call out this difference as a discrepancy -- it appears to be intentional, with vocab drills treating incorrect answers more strictly.

---

## Test Case 10: Word mastery SRS -- WordMasteryStore step tracking

### Code path

**WordMasteryStore** at `WordMasteryStore.kt`:

1. Tracks per-word mastery with `WordMasteryState` at `VocabWord.kt:21`:
   - `intervalStepIndex: Int = 0` (0-9, maps to `SpacedRepetitionConfig.INTERVAL_LADDER_DAYS`).
   - `nextReviewDateMs` is computed externally.
   - `isLearned: Boolean = false`.

2. **Rating logic in TrainingViewModel** at `TrainingViewModel.kt:1702-1710`:
   - Rating 0 (Again): `newStepIndex = 0` (full reset).
   - Rating 1 (Hard): `newStepIndex = current.intervalStepIndex` (stay).
   - Rating 2 (Good): `newStepIndex = current + 1` (advance 1).
   - Rating 3 (Easy): `newStepIndex = current + 2` (advance 2).
   - `LEARNED_THRESHOLD = 3`: `isLearned = (newStepIndex >= 3)`.

3. **Rating logic in VocabDrillViewModel** at `VocabDrillViewModel.kt:253-259`:
   - Same logic: AGAIN->0, HARD->stay, GOOD->+1, EASY->+2.
   - Same `LEARNED_THRESHOLD = 3`.

4. **Due word check** at `WordMasteryStore.kt:106-110`:
   - `nextReviewDateMs <= now` OR `lastReviewDateMs == 0` (never reviewed).

### Comparison with VocabProgressStore

| Aspect | VocabProgressStore | WordMasteryStore |
|--------|-------------------|-----------------|
| Ladder | `[1, 3, 7, 14, 30]` (own) | `SpacedRepetitionConfig.INTERVAL_LADDER_DAYS` [1,2,4,7,10,14,20,28,42,56] |
| On correct | +1 step | +1 (Good), +2 (Easy), 0 (Hard) |
| On incorrect | Reset to 0 | Reset to 0 (Again) |
| "Learned" threshold | N/A (5-step ladder max) | Step >= 3 (LEARNED_THRESHOLD) |
| Due check | `lastCorrectMs + ladder[step] * DAY_MS <= now` | `nextReviewDateMs <= now` or never reviewed |

### Expected (from spec)

The spec (02-data-stores.md Section 2.6) says `isLearned` is set when `intervalStepIndex` reaches step 9 (the last ladder step). The spec for vocab drill (11-vocab-drill.md Section 11.2.3) says `isLearned` is reached at step >= 3 (`LEARNED_THRESHOLD`).

### Actual (from code)

Both `TrainingViewModel` and `VocabDrillViewModel` use `LEARNED_THRESHOLD = 3`. The `VocabWord.kt` comment says "reached the last interval step (9)" but the actual threshold used is 3.

### Discrepancy?

**YES -- spec contradiction confirmed.**
- `VocabWord.kt` line 28 comment: "reached the last interval step (9)".
- `docs/specification/01-models-and-state.md` line 392: `isLearned` is `true` when `intervalStepIndex == 9`.
- `docs/specification/02-data-stores.md` line 341: "`isLearned` is set to true when the word reaches the last interval step (step 9)."
- `docs/specification/11-vocab-drill.md` line 95: "reached step >= 3 (LEARNED_THRESHOLD)".
- `docs/specification/18-learning-methodology.md` line 451: "`isLearned` is `true` when `intervalStepIndex` reaches 9 (the last step)."
- **Code**: `LEARNED_THRESHOLD = 3` in both `TrainingViewModel.kt:1709` and `VocabDrillViewModel.kt:258`.

The code uses threshold 3, but most spec documents say threshold 9. Only `11-vocab-drill.md` correctly documents the threshold as 3. The code behavior is intentional (words at step 3 have a 7-day interval, meaning they have survived 3 spaced repetitions -- sufficient for "learned" status). The spec documents (01, 02, 18) are stale on this point.

---

## Test Case 11: Lesson ladder display -- do labels match actual intervals?

### Code path

1. `LessonLadderCalculator.calculate()` at `LessonLadderCalculator.kt:6`.
2. Computes `daysSince = floor((nowMs - lastShowDateMs) / DAY_MS) + 1` (note the **+1 offset** at line 19).
3. Gets `expectedInterval = ladder[intervalStepIndex]` (line 20).
4. If `daysSince > expectedInterval`: returns `"Overdue+$overdue"` label (line 27).
5. Otherwise: `buildIntervalLabel(daysSince, ladder)` at line 38:
   - If `daysSince <= ladder[0]` (1): returns `"1-2"`.
   - If `daysSince <= ladder[i]`: returns `"ladder[i-1]-ladder[i]"`.
   - Past all intervals: returns `"42-56"`.

### Expected (from spec)

Labels show the current interval range. For step 0, the expected interval is 1 day. A lesson reviewed today shows `daysSince = 1` (due to +1 offset), which is `<= ladder[0]` (1), so label = "1-2".

### Actual (from code)

| Step | Expected Interval | daysSince today | Label |
|------|-------------------|-----------------|-------|
| 0 | 1 | 1 | "1-2" |
| 1 | 2 | 1 | "1-2" |
| 5 | 14 | 1 | "1-2" |
| 9 | 56 | 1 | "1-2" |

The label is based on how many days have passed, NOT on the current interval step. This means a lesson at step 9 (56-day interval) that was just reviewed today shows "1-2" rather than "42-56". The label represents "where you are in time" not "what your interval is."

### Discrepancy?

**YES -- the label is time-based, not step-based.** The label shows which interval range the current `daysSince` falls into, which is the time elapsed since last review. It does NOT show the assigned interval step. For example:
- A lesson at step 9 (56-day interval) reviewed 30 days ago shows "28-42" because `daysSince = 31` (with +1 offset) falls in the 28-42 range. But the actual expected interval is 56 days, so the lesson is NOT overdue. The label "28-42" could mislead the user into thinking the lesson is almost due, when in fact it has 25 more days.

However, the "Overdue" label IS computed correctly using the actual `intervalStepIndex` at line 20-28. The overdue check uses the correct step-based interval.

---

## Test Case 12: Brand new lesson -- no prior reviews, sub-lesson types

### Code path

1. `MixedReviewScheduler.build()` with `lessonIndex = 0` (first lesson).
2. `allowMixed = (lessonIndex > 0)` => `false` (line 55).
3. `newOnlyTarget = totalCards` (all cards, line 62).
4. No mixed sub-lessons are generated.
5. All cards go into NEW_ONLY sub-lessons of size `subLessonSize`.

For subsequent lessons (`lessonIndex > 0`):
1. `allowMixed = true`.
2. `newOnlyTarget = ceil(totalCards / 2)` (half the cards, line 60).
3. First half -> NEW_ONLY sub-lessons.
4. Second half -> MIXED sub-lessons with review cards from prior lessons.

### Expected (from spec)

First lesson: all NEW_ONLY. Subsequent lessons: first half NEW_ONLY, second half MIXED.

### Actual (from code)

Matches. The first lesson always gets NEW_ONLY only. Subsequent lessons split 50/50.

### Discrepancy?

**No.**

---

## Test Case 13: All cards at interval step 5+ -- what does a MIXED sub-lesson look like?

### Code path

This test case refers to the MasteryStore's `intervalStepIndex` for the lesson, not the MixedReviewScheduler's schedule. The MixedReviewScheduler is a **static** schedule -- it does NOT consider individual card interval steps. It generates the same structure regardless of mastery state.

1. `MixedReviewScheduler.build()` always produces the same sub-lesson structure for a given set of lessons, regardless of their mastery/interval state.
2. The "interval step" concept in `MasteryStore` only affects:
   - Flower health (via `SpacedRepetitionConfig.calculateHealthPercent`).
   - Ladder labels (via `LessonLadderCalculator`).
   - Whether the next review advances the step (via `nextIntervalStep`).
3. It does NOT affect which cards appear in MIXED sub-lessons -- those are determined purely by the static `globalMixedIndex` mechanism.

### Expected (from spec)

The spec says intervals are tracked at the lesson level. Card selection for review is based on the MixedReviewScheduler's interval ladder matching, not individual card mastery.

### Actual (from code)

Confirmed. Mastery/interval tracking is lesson-level. MixedReviewScheduler uses a separate mechanism (globalMixedIndex matching) for review scheduling.

### Discrepancy?

**No.** But worth noting: the spec (Section 18.10.3 item 3) identifies this as a gap: "Per-card intervals: Currently, `intervalStepIndex` is tracked at the lesson level, not the individual card level."

---

## Test Case 14: User switches active pack -- do intervals carry over or reset?

### Code path

1. `MasteryStore` stores data at `grammarmate/mastery.yaml`, keyed by `languageId -> lessonId`.
2. `MasteryStore` is NOT scoped by `packId`. When the user switches packs, the same `MasteryStore` instance is used.
3. If the new pack has the same `languageId` and `lessonId` values, mastery data carries over.
4. `WordMasteryStore` IS scoped by `packId`: `grammarmate/drills/{packId}/word_mastery.yaml`. Switching packs creates a new `WordMasteryStore` with the new `packId`.
5. `VerbDrillStore` IS scoped by `packId`: `grammarmate/drills/{packId}/verb_drill_progress.yaml`.
6. `VocabProgressStore` is NOT scoped by `packId` -- it uses `grammarmate/vocab_progress.yaml`, keyed by `languageId -> lessonId`.

### Expected (from spec)

Lesson mastery (MasteryStore) persists across pack switches because it's keyed by language+lesson. Drill progress (WordMasteryStore, VerbDrillStore) is pack-scoped and resets when switching packs. VocabProgressStore is not pack-scoped.

### Actual (from code)

- MasteryStore: carries over (language+lesson keyed).
- WordMasteryStore: resets (pack-scoped).
- VerbDrillStore: resets (pack-scoped).
- VocabProgressStore: carries over (language+lesson keyed).

### Discrepancy?

**No.** The behavior is consistent with the spec. However, there is an asymmetry: if a user imports two different packs with the same lesson IDs (e.g., two Italian packs both having "lesson_1"), the MasteryStore would share mastery state between them, while the drill stores would be independent.

---

## Test Case 15: Streak tracking -- how does StreakStore relate to SRS intervals?

### Code path

1. `StreakStore` at `StreakStore.kt` tracks:
   - `currentStreak`: Consecutive days of practice.
   - `longestStreak`: All-time best.
   - `totalSubLessonsCompleted`: Cumulative count.
   - `lastCompletionDateMs`: Timestamp of last completion.

2. `recordSubLessonCompletion()` at `StreakStore.kt:62`:
   - First time: streak = 1.
   - Same day: streak unchanged.
   - Consecutive day (was yesterday): streak + 1.
   - Gap of 2+ days: streak reset to 1.

3. `getCurrentStreak()` at `StreakStore.kt:136`:
   - If `daysSinceLastCompletion > 1`: resets streak to 0.

### Relationship to SRS intervals

`StreakStore` has **no direct relationship** to `SpacedRepetitionConfig` or the interval ladder. The streak system is a simple daily-engagement tracker that operates independently of the spaced repetition system. It does not:
- Read or write interval step indices.
- Use the interval ladder values.
- Influence card selection or review scheduling.
- Affect flower health or mastery.

### Expected (from spec)

The spec (Section 18.9.1) describes the streak system as a motivation mechanic independent of SRS. It tracks daily engagement, not interval-based review.

### Actual (from code)

Confirmed. StreakStore is completely independent of SRS. It is keyed by `languageId`, stored in `streak_{languageId}.yaml`.

### Discrepancy?

**No.** However, note a behavioral inconsistency: `recordSubLessonCompletion()` resets the streak to 1 on a gap, while `getCurrentStreak()` resets it to 0. This means:
- After a gap, the next completion sets streak to 1 (via `recordSubLessonCompletion`).
- But if you call `getCurrentStreak()` without first completing a sub-lesson, it returns streak = 0.

This is not a spec discrepancy but is a subtle behavioral difference between the two methods.

---

## Summary of Discrepancies

| # | Test Case | Discrepancy | Severity |
|---|-----------|-------------|----------|
| 5 | Mixed sub-lesson review | MixedReviewScheduler uses sub-lesson counts (globalMixedIndex), not calendar days, for interval matching. MasteryStore uses calendar days. Two different time bases. | **Informational** -- by design, the scheduler is static. |
| 7 | Reserve pool selection | The shuffled-then-split approach means "reserve" cards may include main pool cards. | **Low** -- intentional for variety. |
| 9 | Vocab SRS penalty | VocabProgressStore resets to step 0 on incorrect. MasteryStore does not regress on late review. | **Medium** -- intentional design difference between lesson mastery (gentle) and vocab (harsh). |
| 10 | isLearned threshold | Code uses `LEARNED_THRESHOLD = 3`. Spec documents (01, 02, 18) say step 9. Only `11-vocab-drill.md` correctly documents threshold 3. | **Medium** -- spec stale in multiple places. Code and one spec section agree on 3. |
| 11 | Ladder labels | Labels are time-based (daysSince), not step-based. A lesson at step 9 reviewed today shows "1-2" not "42-56". | **Low** -- by design, but potentially confusing. |
| 15 | Streak reset inconsistency | `recordSubLessonCompletion` resets to 1 on gap. `getCurrentStreak` resets to 0 on gap. | **Low** -- different use cases, but could confuse. |

### Confirmed matches (no discrepancy)

- Test 1: First card show -> step 0 (1-day interval). Correct.
- Test 2: On-time review -> step advances by 1. Correct.
- Test 3: 3-day overdue at step 1 -> health ~0.80 (WILTING). Correct.
- Test 4: 30-day overdue -> health floor at 0.5 (WILTED). Correct.
- Test 6: globalMixedIndex mechanism. Correct.
- Test 8: 300-card review cap. Correct.
- Test 12: New lesson -> all NEW_ONLY. Correct.
- Test 13: MIXED sub-lessons are static, not mastery-dependent. Correct.
- Test 14: Pack switching -- MasteryStore carries over, drill stores reset. Correct.

---

*Files traced:*
- `app/src/main/java/com/alexpo/grammermate/data/SpacedRepetitionConfig.kt`
- `app/src/main/java/com/alexpo/grammermate/data/MixedReviewScheduler.kt`
- `app/src/main/java/com/alexpo/grammermate/data/MasteryStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/LessonLadderCalculator.kt`
- `app/src/main/java/com/alexpo/grammermate/data/FlowerCalculator.kt`
- `app/src/main/java/com/alexpo/grammermate/data/ProgressStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/VocabProgressStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/WordMasteryStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/VocabWord.kt`
- `app/src/main/java/com/alexpo/grammermate/data/StreakStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/Models.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillViewModel.kt`

*Spec documents compared:*
- `docs/specification/03-algorithms-and-calculators.md`
- `docs/specification/02-data-stores.md`
- `docs/specification/18-learning-methodology.md`
- `docs/specification/11-vocab-drill.md`
- `docs/specification/01-models-and-state.md`
