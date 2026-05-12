# Scenario 03: Mastery Tracking & Flower State Progression

Verification of the mastery and flower progression pipeline through the actual codebase,
compared against the specification documents.

---

## Test Case 1: First card practiced with VOICE -> mastery 0->1 -> flower LOCKED->SEED?

### Code path

1. `TrainingViewModel.submitAnswer()` (line 632): on correct answer, calls `recordCardShowForMastery(card)`
2. `recordCardShowForMastery()` (line 2896-2915): checks `isDrillMode` (no), checks `InputMode.WORD_BANK` (no, it's VOICE), then calls `masteryStore.recordCardShow(lessonId, languageId, card.id)`
3. `MasteryStore.recordCardShow()` (line 105-145): `existing` is null (first card ever). `isNewCard = true`. Creates `LessonMasteryState(uniqueCardShows=1, totalCardShows=1, lastShowDateMs=now, intervalStepIndex=0, shownCardIds={cardId})`
4. `refreshFlowerStates()` (line 3113): calls `FlowerCalculator.calculate(mastery, lesson.cards.size)` for each lesson
5. `FlowerCalculator.calculate()` (line 20-67): `mastery.uniqueCardShows == 1` (not 0), so it does NOT hit the early SEED return. `masteryPercent = 1/150 = 0.0067`. `daysSinceLastShow = 0`. `healthPercent = 1.0`. Calls `determineFlowerState(0.0067, 1.0, mastery)`.
6. `determineFlowerState()` (line 72-89): `healthPercent == 1.0` (not WILTED, not WILTING). `masteryPercent < 0.33` -> returns `FlowerState.SEED`

### Expected (from spec)

- Spec section 18.2.5: "0% -- 32.9% -> SEED"
- Spec section 3.2: "masteryPercent < 0.33 -> SEED"

### Actual (from code)

- `uniqueCardShows` goes from 0 to 1. Correct.
- Flower state: SEED. Correct.
- Note: The flower does NOT go LOCKED->SEED. LOCKED is a UI-only state never produced by `FlowerCalculator`. The initial state for an unstarted lesson is already SEED (line 23-29 of FlowerCalculator: `mastery == null || uniqueCardShows == 0` returns SEED).

### Discrepancy

**None.** The test case title mentions "LOCKED->SEED" but LOCKED is never produced by the calculator. The transition is SEED (unstarted) -> SEED (started, 1 card), which is the same visual state. The spec correctly documents LOCKED as UI-only (section 3.2: "LOCKED is never produced by FlowerCalculator").

---

## Test Case 2: First card practiced with KEYBOARD -> same result as VOICE?

### Code path

Same as Test Case 1, but input mode is KEYBOARD. The `recordCardShowForMastery()` method checks `InputMode.WORD_BANK` only (line 2905). KEYBOARD passes through and calls `masteryStore.recordCardShow()`. The rest is identical.

### Expected (from spec)

- Spec section 8.7.3: "VOICE: YES, KEYBOARD: YES, WORD_BANK: NO" for mastery counting.

### Actual (from code)

- KEYBOARD triggers `masteryStore.recordCardShow()` identically to VOICE.
- `uniqueCardShows` increments by 1. Flower state = SEED.

### Discrepancy

**None.** KEYBOARD and VOICE are treated identically for mastery purposes.

---

## Test Case 3: Card practiced with WORD_BANK -> mastery should NOT increment

### Code path

1. `TrainingViewModel.submitAnswer()` (line 632): calls `recordCardShowForMastery(card)` on correct answer regardless of mode.
2. `recordCardShowForMastery()` (line 2905-2908): checks `isWordBankMode = _uiState.value.inputMode == InputMode.WORD_BANK`. If true, logs "Skipping card show record for Word Bank mode" and returns early. Does NOT call `masteryStore.recordCardShow()`.

### Expected (from spec)

- Spec section 8.7.3: "WORD_BANK: NO" for mastery.
- CLAUDE.md: "WORD_BANK mode never counts for mastery -- only VOICE and KEYBOARD grow flowers."
- Spec section 18.2.2: detailed rationale for WORD_BANK exclusion.

### Actual (from code)

- WORD_BANK mode returns early. `masteryStore.recordCardShow()` is never called. `uniqueCardShows` does NOT increment.

### Discrepancy

**None.** WORD_BANK is correctly excluded.

**Additional note:** There is also `markSubLessonCardsShown()` (line 2917-2928) which marks cards as shown in `shownCardIds` for WORD_BANK mode at sub-lesson completion. This uses `markCardsShownForProgress()` which only updates `shownCardIds` but does NOT increment `uniqueCardShows`. This is correct -- it tracks which cards were seen (for progress tracking/sub-lesson completion) without counting them toward mastery.

---

## Test Case 4: 50th unique card (33% of 150) -> flower goes to SPROUT

### Code path

1. After 50 unique cards: `uniqueCardShows = 50`.
2. `FlowerCalculator.calculate()`: `masteryPercent = 50/150 = 0.3333...`
3. `determineFlowerState()`: `healthPercent == 1.0` (assumes fresh practice). `masteryPercent < 0.33` -> checks `0.3333 < 0.33` which is FALSE. Falls to next check: `masteryPercent < 0.66` -> TRUE. Returns `FlowerState.SPROUT`.

### Expected (from spec)

- Spec section 18.2.5: "33% -- 65.9% -> SPROUT"
- Spec section 3.2: "masteryPercent < 0.66 -> SPROUT"

### Actual (from code)

- `masteryPercent = 0.3333` is NOT `< 0.33`, so it passes the SEED threshold.
- `masteryPercent = 0.3333` IS `< 0.66`, so it returns SPROUT.
- SPROUT is correctly produced at exactly 50 unique cards (33.33%).

### Discrepancy

**None.** However, note a subtle threshold behavior: the SEED threshold uses strict less-than (`< 0.33`), meaning at exactly 33.00% (49.5 cards, impossible in practice), it would still be SEED. At 50 cards (33.33%), it crosses to SPROUT. This is consistent with the spec's "33% -- 65.9% -> SPROUT" range.

---

## Test Case 5: 100th unique card (66% of 150) -> flower goes to BLOOM

### Code path

1. After 100 unique cards: `uniqueCardShows = 100`.
2. `FlowerCalculator.calculate()`: `masteryPercent = 100/150 = 0.6667`.
3. `determineFlowerState()`: `healthPercent == 1.0`. `masteryPercent < 0.33` -> FALSE. `masteryPercent < 0.66` -> `0.6667 < 0.66` is FALSE. Falls to else: returns `FlowerState.BLOOM`.

### Expected (from spec)

- Spec section 18.2.5: "66% -- 100% -> BLOOM"
- Spec section 3.2: "else -> BLOOM"

### Actual (from code)

- `masteryPercent = 0.6667` is NOT `< 0.66`, so it correctly falls through to BLOOM.
- BLOOM at exactly 100 unique cards (66.67%).

### Discrepancy

**None.** 100 unique cards correctly produces BLOOM.

**Edge case note:** At 99 unique cards, `masteryPercent = 99/150 = 0.66`, which is NOT `< 0.66` (it is equal), so 99 cards would also produce BLOOM. This means the actual BLOOM threshold is 99 cards (66.0%), not 100 cards (66.67%). The spec says ">= 66%" which is consistent.

---

## Test Case 6: User practices same card twice -> does mastery count twice?

### Code path

1. First time: `MasteryStore.recordCardShow(lessonId, langId, cardId)`. `existing.shownCardIds` does NOT contain `cardId`. `isNewCard = true`. `uniqueCardShows` incremented by 1. `cardId` added to `shownCardIds`.
2. Second time: `existing.shownCardIds` now CONTAINS `cardId`. `isNewCard = false`. `uniqueCardShows` is NOT incremented (line 132-136). Only `totalCardShows` increments. `lastShowDateMs` is updated. `intervalStepIndex` may advance.

### Expected (from spec)

- Spec section 3.1 business rule 1: "Mastery requires 150 unique card shows."
- Spec section 3.6 invariant 2: "Mastery is measured in unique cards, not repetitions."
- Spec section 18.2.1: "Each card can only increment uniqueCardShows once."

### Actual (from code)

- Second practice of same card: `uniqueCardShows` does NOT change. `totalCardShows` increments. Correct.

### Discrepancy

**None.** Unique counting works correctly via `shownCardIds` set.

---

## Test Case 7: Health decay after 7 days of no practice

### Code path

Assumptions: `intervalStepIndex = 0` (learner just started), `lastShowDateMs` = 7 days ago.

1. `FlowerCalculator.calculate()`: `daysSinceLastShow = 7`.
2. `daysSinceLastShow > GONE_THRESHOLD_DAYS (90)` -> FALSE.
3. `calculateHealthPercent(7, 0)`:
   - `daysSinceLastShow > 0`, not >= 90.
   - `expectedInterval = INTERVAL_LADDER_DAYS[0] = 1`.
   - `7 > 1` -> overdue.
   - `overdueDays = 7 - 1 = 6`.
   - `stability = 0.9` (step 0).
   - `decay = e^(-6/0.9) = e^(-6.667) = 0.00127`.
   - `health = 0.5 + 0.5 * 0.00127 = 0.5006`.
4. `determineFlowerState()`: `healthPercent <= 0.5 + 0.01 = 0.51` -> `0.5006 <= 0.51` -> TRUE. Returns `FlowerState.WILTED`.

### Expected (from spec)

- Spec section 18.3.3: Health decays from 100% toward 50% over overdue period.
- At step 0, expected interval is 1 day. After 7 days, 6 days overdue with stability 0.9. Health should be very close to 50%.

### Actual (from code)

- `healthPercent = 0.5006` (essentially at the floor).
- Flower state: WILTED (because `0.5006 <= 0.51`).

### Discrepancy

**None.** After 7 days at step 0, the flower is WILTED. This is expected because the learner was at the very beginning of the interval ladder (1-day expected interval) and has been overdue for 6 days.

**For higher step indices:** At step 4 (expected interval = 10 days), 7 days would still be within interval -> `healthPercent = 1.0` -> flower state based purely on mastery. The health decay depends heavily on the current interval step.

---

## Test Case 8: Health decay after 30 days of no practice

### Code path

Assumptions: `intervalStepIndex = 0`, `lastShowDateMs` = 30 days ago.

1. `calculateHealthPercent(30, 0)`:
   - `expectedInterval = 1`.
   - `overdueDays = 29`.
   - `stability = 0.9`.
   - `decay = e^(-29/0.9)` -> effectively 0.
   - `health = 0.5 + 0.5 * ~0 = 0.5`.
2. `daysSinceLastShow = 30`, which is NOT > 90. Not GONE.
3. `determineFlowerState()`: `0.5 <= 0.51` -> WILTED.

At `intervalStepIndex = 4` (expected 10 days, stability 21.1):
- `overdueDays = 20`.
- `decay = e^(-20/21.1) = 0.389`.
- `health = 0.5 + 0.5 * 0.389 = 0.695`.
- `0.695 < 1.0` and `0.695 > 0.51` -> WILTING.

At `intervalStepIndex = 9` (expected 56 days, stability 1087):
- `30 <= 56` -> within interval. `healthPercent = 1.0`.
- Flower state based on mastery only.

### Expected (from spec)

- Spec section 3.1.4: health decays from 1.0 to 0.5 over overdue period, clamped at WILTED_THRESHOLD.

### Actual (from code)

- At step 0: WILTED (health at floor).
- At step 4: WILTING (health = 69.5%).
- At step 9: full health (within interval, 30 < 56 days).

### Discrepancy

**None.** The decay behavior matches the spec. The key insight is that higher step indices provide more protection against decay because they have longer expected intervals and higher stability.

---

## Test Case 9: User returns after wilting -> practices once -> instant recovery to BLOOM?

### Code path

1. User practices a card in the wilted lesson (VOICE or KEYBOARD).
2. `recordCardShowForMastery()` -> `masteryStore.recordCardShow()`.
3. `MasteryStore.recordCardShow()` (line 129-143): updates `lastShowDateMs = now`.
4. `refreshFlowerStates()` calls `FlowerCalculator.calculate()`:
   - `daysSinceLastShow = 0` (just practiced).
   - `calculateHealthPercent(0, intervalStepIndex)` returns `1.0` (line 107: `daysSinceLastShow <= 0 -> return 1.0f`).
   - `determineFlowerState()`: health is 1.0. Mastery determines state. If mastery >= 66%, state = BLOOM.
5. Interval step: `daysSinceLastShow` before this practice was large. `wasRepetitionOnTime` checks if it was within expected interval. If overdue (likely), step does NOT advance (line 149: returns `currentStepIndex.coerceAtLeast(0)` -- no regression, but no advancement either).

### Expected (from spec)

- Spec section 18.3.4: "Health is restored to 100% instantly upon any card show."
- Spec section 18.3.4: "The flower returns to its mastery-appropriate state."
- Spec section 3.1.5: "Late review: step does NOT change."

### Actual (from code)

- One practice: `lastShowDateMs` updated, `daysSinceLastShow` becomes 0, health = 100%.
- Flower state determined by mastery alone: SEED/SPROUT/BLOOM.
- If mastery >= 66% -> instant BLOOM recovery.
- Interval step does NOT advance (late review penalty).

### Discrepancy

**None.** Instant recovery to BLOOM if mastery warrants it. However, the interval step does not advance, so the flower will wilt again sooner (shorter expected interval). This is the intended "motivational signal" behavior from spec section 18.3.3.

---

## Test Case 10: Lesson with 200 cards -> scale multiplier applied? How?

### Code path

1. `Lesson.mainPoolCards` (Models.kt line 22-23): `cards.take(MAIN_POOL_SIZE)` where `MAIN_POOL_SIZE = 150`. Main pool is always first 150 cards.
2. `Lesson.reservePoolCards` (Models.kt line 28-29): `cards.drop(MAIN_POOL_SIZE)`. Reserve pool is cards 151-200 (50 cards).
3. `FlowerCalculator.calculate(mastery, totalCardsInLesson)` (line 20): The `totalCardsInLesson` parameter is accepted but **never used** in the algorithm. The mastery calculation always uses `MASTERY_THRESHOLD = 150` as the denominator.
4. `masteryPercent = uniqueCardShows / 150` (line 33-34). This is always 150, regardless of lesson size.

### Expected (from spec)

- Spec section 18.2.3: "Main pool: First 150 cards. Mastery is measured against this pool."
- Spec section 18.2.1: "masteryPercent = uniqueCardShows / 150"
- The 200-card lesson has 150 in main pool and 50 in reserve. Mastery uses 150 as denominator.

### Actual (from code)

- `totalCardsInLesson` parameter is unused (dead parameter). Mastery always divides by 150.
- The extra 50 cards go to reserve pool and are used for review, not mastery counting.
- `scaleMultiplier = max(0.5, masteryPercent * healthPercent)` -- the scale is purely based on the 150-card mastery percentage and health, NOT adjusted for total lesson size.

### Discrepancy

**Minor discrepancy with the FlowerCalculator API.** The `totalCardsInLesson` parameter is accepted but completely ignored. It is dead code. This is not a behavioral discrepancy (the spec says mastery is always out of 150), but the API is misleading -- a caller might expect lesson size to affect the calculation. The spec in section 18.10.2 item 7 notes: "A lesson with 50 cards requires 3 full cycles to reach mastery, while a lesson with 300 cards reaches mastery before completing a single cycle."

---

## Test Case 11: Mastery in ALL_MIXED mode -> does it track across all lessons?

### Code path

1. `buildSessionCards()` for ALL_MIXED (line 1339-1342): collects `lessons.flatMap { it.allCards }`, filters hidden, shuffles, takes up to 300. Cards come from ALL lessons.
2. On correct answer, `recordCardShowForMastery(card)` is called (line 632).
3. `resolveCardLessonId(card)` (line 2875-2889): First checks if `selectedLessonId` contains the card. In ALL_MIXED mode, `selectedLessonId` may or may not be set. If the selected lesson doesn't contain the card, it searches ALL lessons (line 2884-2887). If found, returns that lesson's ID. If not found anywhere, falls back to `selectedLessonId` or "unknown".
4. `masteryStore.recordCardShow(lessonId, languageId, card.id)` records mastery against the resolved lesson.

### Expected (from spec)

- Spec section 8.5.3: "All cards from all lessons are collected, shuffled."
- Each card's mastery should be attributed to its source lesson.

### Actual (from code)

- Mastery IS tracked per-source-lesson via `resolveCardLessonId()`. A card from lesson 3 practiced in ALL_MIXED mode increments lesson 3's `uniqueCardShows`.
- The flower for each individual lesson reflects the mastery of its own cards, regardless of the mode they were practiced in.

### Discrepancy

**None.** Mastery tracks per-lesson even in ALL_MIXED mode. The `resolveCardLessonId()` function correctly attributes cards to their source lessons.

---

## Test Case 12: Boss battle completion -> does it affect mastery?

### Code path

1. `startBoss()` (line 2193): Sets `bossActive = true`, loads boss cards.
2. On correct answer during boss: `submitAnswer()` calls `recordCardShowForMastery(card)` (line 632).
3. `recordCardShowForMastery()` (line 2896-2915): checks `isDrillMode` -- boss mode is NOT drill mode. Checks `WORD_BANK` -- depends on user's input mode. If not WORD_BANK, calls `masteryStore.recordCardShow()`.
4. **Boss battles DO count toward mastery.** Each correct answer in a boss battle (with VOICE or KEYBOARD) increments `uniqueCardShows` for the resolved lesson.

### Expected (from spec)

- Spec section 18.8.4: "Boss battles are separate from the mastery/flower system. They do not directly affect uniqueCardShows or flower state."
- Quote: "Boss battles are separate from the mastery/flower system. They do not directly affect uniqueCardShows or flower state."

### Actual (from code)

- Boss battles DO call `recordCardShowForMastery()` on correct answers (line 632 in `submitAnswer()` is unconditional -- it does not check `bossActive`).
- Boss answers in VOICE/KEYBOARD mode increment `uniqueCardShows` and grow flowers.

### Discrepancy

**CRITICAL DISCREPANCY.** The spec (section 18.8.4) explicitly states that boss battles should NOT affect `uniqueCardShows` or flower state. However, the code at line 632 calls `recordCardShowForMastery()` unconditionally for all correct answers, including during boss battles. This means:

1. Boss battle practice inflates mastery counts beyond what the spec intends.
2. A learner could reach BLOOM flower state purely through boss battles without completing sub-lessons.
3. The spec's statement that "Boss rewards are tracked separately" and boss battles serve as "validation" and "diagnostic tool" is undermined because boss practice directly grows flowers.

**The fix would be** to add `if (_uiState.value.bossActive) return` at the top of `recordCardShowForMastery()`, similar to the existing `isDrillMode` check.

---

## Test Case 13: Daily practice block 1 -> does mastery track like normal training?

### Code path

1. `recordDailyCardPracticed(DailyBlockType.TRANSLATE)` (line 1603-1618): Called when a TRANSLATE block card is answered via VOICE or KEYBOARD (not WORD_BANK).
2. Inside, for `DailyBlockType.TRANSLATE` only (line 1609): gets the current `TranslateSentence` task, resolves `lessonId` via `resolveCardLessonId(card)`, calls `masteryStore.recordCardShow(lessonId, languageId, card.id)`.
3. For `DailyBlockType.VERBS` and `DailyBlockType.VOCAB` (line 1608 comment): "VERBS and VOCAB blocks do NOT count toward flower growth." The if-block only executes for TRANSLATE.

### Expected (from spec)

- Spec section 9 (Daily Practice): TRANSLATE block cards count toward mastery.
- Spec section 18.5.3: "Progress tracking: Lesson mastery + flower" for regular lessons vs "Streak tracking + session completion" for daily practice -- this comparison table is somewhat ambiguous but the code is clear.

### Actual (from code)

- Daily practice TRANSLATE block: mastery IS tracked. Cards from block 1 increment `uniqueCardShows` for their source lesson.
- Daily practice VOCAB block: mastery NOT tracked. No `masteryStore.recordCardShow()` call.
- Daily practice VERBS block: mastery NOT tracked. Only `persistDailyVerbProgress()` is called for VerbDrillStore tracking.

### Discrepancy

**None.** TRANSLATE block correctly tracks mastery. VOCAB and VERBS do not, which is the intended behavior.

---

## Test Case 14: Active/Reserve set rotation -> how does this affect mastery counting?

### Code path

1. `Lesson.mainPoolCards` = first 150 cards. `Lesson.reservePoolCards` = cards beyond 150.
2. `MixedReviewScheduler`: review cards in MIXED sub-lessons are drawn from reserve pools first, then main pools (spec section 3.3, "Review card filling priority").
3. When a reserve pool card is practiced and answered correctly in a MIXED sub-lesson, `recordCardShowForMastery(card)` is called.
4. `resolveCardLessonId(card)` searches all lessons for the card. Since the card belongs to a specific lesson (even if from its reserve pool), it resolves correctly.
5. `masteryStore.recordCardShow()`: The card ID is added to `shownCardIds`. If the card is from the reserve pool (card #151+), it still increments `uniqueCardShows`.

### Expected (from spec)

- Spec section 18.2.3: "Main pool: Primary learning material. Mastery is measured against this pool."
- Spec section 18.2.1: "masteryPercent = uniqueCardShows / 150"

### Actual (from code)

- Reserve pool cards (cards 151+) DO increment `uniqueCardShows` when practiced in MIXED sub-lessons or ALL_MIXED mode.
- There is no check in `recordCardShow()` or `recordCardShowForMastery()` that restricts mastery counting to main pool cards only.
- This means a lesson with 200 cards could have `uniqueCardShows` reach 200 (beyond the 150 threshold), even though `masteryPercent` is clamped at 1.0. The mastery calculation divides by 150 and clamps, so visually it doesn't exceed 100%.

### Discrepancy

**Potential discrepancy.** The spec says "Mastery is measured against this pool" (main pool, 150 cards), implying only main pool cards should count. However, the code counts ALL cards from the lesson, including reserve pool cards, toward `uniqueCardShows`. In practice, this rarely matters because:
- Reserve pool cards are only shown in MIXED/review sessions, not NEW_ONLY sessions.
- `masteryPercent` is clamped at 1.0, so going above 150 has no visual effect.
- `shownCardIds` correctly tracks uniqueness regardless of pool.

But the principle is violated: a card from the reserve pool (#151) contributes to the same `uniqueCardShows` counter as a main pool card (#1). If the spec intent is that only the first 150 cards should count, a filter is needed.

---

## Test Case 15: Multiple packs for same language -> is mastery per-pack or global?

### Code path

1. `MasteryStore` stores data as `Map<languageId, Map<lessonId, LessonMasteryState>>` (line 18, cache structure).
2. Key is `languageId` + `lessonId`. There is NO `packId` in the key.
3. `recordCardShow(lessonId, languageId, cardId)`: indexed by language and lesson only.
4. `FlowerCalculator.calculate()`: takes `LessonMasteryState` which has `languageId` and `lessonId` only.
5. `refreshFlowerStates()` (line 3118): iterates `_uiState.value.lessons` and calls `masteryStore.get(lesson.id, languageId)` for each. The `lessons` list comes from the active pack only (via `LessonStore.getLessons(languageId)` which returns lessons from all packs or the active pack).

### Expected (from spec)

- CLAUDE.md: "Drill tiles on HomeScreen are visible only when the active pack declares the corresponding drill section." -- pack-scoping applies to drills.
- MasteryStore does not mention pack-scoping in the spec.

### Actual (from code)

- Mastery is keyed by `languageId` + `lessonId` only. No `packId` dimension.
- If two packs for the same language have lessons with the same `lessonId`, they would share mastery data (potential collision).
- In practice, lesson IDs are typically unique (e.g., "lesson_1_it_pack1" vs "lesson_1_it_pack2"), but this is not enforced by the store.
- When switching packs, the mastery data persists because it's per-language, not per-pack.

### Discrepancy

**Architectural observation, not a spec discrepancy.** Mastery is per-language+lesson, not per-pack. This means:
- Switching packs for the same language preserves mastery (could be desirable or undesirable depending on perspective).
- If two packs share lesson IDs (unlikely but possible), mastery data would collide.
- The spec does not explicitly state whether mastery should be per-pack or per-language.

---

## Summary of Findings

### No Discrepancy (10 of 15 test cases)

Test cases 1, 2, 3, 4, 5, 6, 7, 8, 9, 11 pass correctly. The core mastery tracking and flower state machine work as specified.

### Critical Discrepancy (1 of 15)

**Test Case 12 -- Boss battles incorrectly count toward mastery.**
- Spec (section 18.8.4): "Boss battles are separate from the mastery/flower system. They do not directly affect uniqueCardShows or flower state."
- Code: `recordCardShowForMastery()` is called unconditionally for all correct answers, including boss battles.
- Impact: Boss practice inflates mastery, allowing learners to reach BLOOM without completing sub-lessons.
- Location: `TrainingViewModel.kt` line 632.
- Suggested fix: Add `if (_uiState.value.bossActive) return` at the top of `recordCardShowForMastery()`.

### Minor/Potential Discrepancies (2 of 15)

**Test Case 10 -- Dead parameter `totalCardsInLesson`.**
- `FlowerCalculator.calculate()` accepts `totalCardsInLesson` but never uses it. The API is misleading.
- No behavioral impact since mastery always divides by 150.

**Test Case 14 -- Reserve pool cards count toward mastery.**
- Spec implies mastery is measured against the main pool (150 cards) only.
- Code counts ALL cards including reserve pool cards (151+).
- Minimal practical impact (masteryPercent is clamped at 1.0).

### Architectural Observations (2 of 15)

**Test Case 13 -- Daily practice mastery.** Works correctly. TRANSLATE block tracks mastery, VOCAB and VERBS do not.

**Test Case 15 -- Mastery scoping.** Mastery is per-language+lesson, not per-pack. No explicit spec requirement for per-pack isolation. Potential ID collision risk across packs.
