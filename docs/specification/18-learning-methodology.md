# 18. Learning Methodology -- Specification

## 18.1 Pedagogical Foundation

### 18.1.1 Ebbinghaus Forgetting Curve

GrammarMate implements spaced repetition based on Hermann Ebbinghaus's forgetting curve research (1885, *Memory: A Contribution to Experimental Psychology*), informed by the replication and analysis performed by Murre & Dros (2015).

The core formula governing memory decay is:

```
R = e^(-t/S)
```

Where:
- **R** = retention probability (0.0 to 1.0) -- the likelihood the learner can still recall the material.
- **t** = time elapsed since the last review, measured in days.
- **S** = memory stability in days, a value that grows with each successful review.

**Memory stability** is computed as an exponential function of the number of successful review cycles:

```
S = S0 * multiplier^step
```

Where:
- `S0 = 0.9` days -- the base stability for brand-new material. Research shows that without review, roughly 33% of information remains after 1 day, which yields S approximately equal to 0.9.
- `multiplier = 2.2` -- each successful review roughly doubles the stability of the memory trace.
- `step` = the learner's current position on the interval ladder (0-indexed).

This means a learner on step 0 has S = 0.9 days; on step 5, S = 0.9 * 2.2^5 = ~19 days; on step 9, S = 0.9 * 2.2^9 = ~877 days. Stability grows rapidly, modeling how well-learned material becomes increasingly resistant to forgetting.

**Source files:** `SpacedRepetitionConfig.kt` (pure functions, no state), `FlowerCalculator.kt`, `MasteryStore.kt`.

### 18.1.2 Interval Ladder

The app uses a fixed 10-rung interval ladder with values in days:

```
[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]
```

**Why these specific values:**

1. **Early density (1, 2, 4):** New material is most vulnerable in the first few days. Three reviews within the first week combat the steepest part of the forgetting curve. Research on Ebbinghaus's original data shows retention drops to ~33% within 24 hours without review.

2. **Consolidation phase (7, 10, 14):** The second and third weeks transition the learner from short-term to longer-term retention. The 7-day interval aligns with the well-documented "7-day dip" where unreinforced memories show a secondary decay.

3. **Long-term maintenance (20, 28, 42, 56):** After approximately two weeks of successful review, the memory trace stabilizes. The intervals widen to 3, 4, 6, and 8 weeks. The 56-day cap (~8 weeks) means a fully mastered pattern requires review roughly every two months to maintain near-perfect retention.

4. **Approximately geometric growth:** Each interval is roughly 1.4-2.0x the previous one. This mirrors the spacing effect documented in cognitive psychology: expanding intervals are more effective than equal intervals for long-term retention (Cepeda et al., 2006).

5. **Day-based, not step-based:** Although the original project specification envisioned intervals measured in "completed sub-lessons" to accommodate irregular study schedules, the implementation uses calendar days. This simplification was chosen because: (a) the flower health system already accounts for irregular practice through its decay mechanism; (b) daily practice sessions provide natural cadence; (c) the streak system motivates daily engagement, making day-based intervals practical.

**Advancement rule:** When a review occurs on time (within the expected interval), the step advances by 1. When a review is late, the step does not advance but does not regress either. The step is capped at 9 (index of the 56-day rung).

### 18.1.3 Retention Calculation

The `calculateRetention()` function in `SpacedRepetitionConfig` computes the predicted retention at any point:

```kotlin
fun calculateRetention(daysSinceLastShow: Int, intervalStepIndex: Int): Float
```

- Returns 1.0 if `daysSinceLastShow <= 0` (reviewed today or in the future).
- Computes `stability = S0 * multiplier^step`.
- Returns `e^(-daysSince/stability)` clamped to [0.0, 1.0].

This value is not directly displayed to the user but informs the health calculation that drives the flower metaphor.

---

## 18.2 Mastery System

### 18.2.1 How Mastery Is Measured

Mastery for a lesson is defined as the count of **unique cards** the learner has practiced. The core metric is:

```
masteryPercent = uniqueCardShows / 150
```

Where `uniqueCardShows` is the count of distinct card IDs the learner has been exposed to (tracked via the `shownCardIds` set in `LessonMasteryState`), and 150 is the fixed `MASTERY_THRESHOLD`.

Each card can only increment `uniqueCardShows` once -- seeing the same card again does not increase the mastery count, though it does update the `lastShowDateMs` timestamp and may advance the `intervalStepIndex`.

### 18.2.2 Why WORD_BANK Does Not Count

Three input modes exist: `VOICE`, `KEYBOARD`, and `WORD_BANK`.

**Only VOICE and KEYBOARD contribute to mastery.** WORD_BANK is excluded because:

1. **Retrieval effort:** WORD_BANK mode presents word tiles that the learner arranges into a sentence. This is a recognition task (selecting from visible options) rather than a recall task (producing the answer from memory). Cognitive science consistently shows that retrieval practice -- actively recalling information -- produces stronger learning than recognition (Karpicke & Roediger, 2008).

2. **Automaticism formation:** The app's mission is to build *automatic* grammar pattern production. WORD_BANK tests whether the learner can assemble a correct sentence from pieces, not whether they can generate it spontaneously. Only spontaneous production (voice or keyboard) demonstrates that the grammatical pattern has been internalized.

3. **Progress inflation prevention:** If WORD_BANK counted toward mastery, a learner could achieve high mastery scores (and flower states) by relying on word recognition rather than actual recall. This would undermine the pedagogical value of the mastery system and give a false sense of progress.

The implementation enforces this in `DailyPracticeSessionProvider.nextCard()` and `TrainingViewModel.recordCardShowForMastery()`, which check `InputMode != WORD_BANK` before recording a card show for mastery.

### 18.2.3 Main Pool (150 Cards) vs. Reserve Pool

Each lesson in a pack contains a variable number of sentence cards. The first 150 cards form the **main pool**; any cards beyond 150 form the **reserve pool**.

| Pool | Cards | Purpose |
|------|-------|---------|
| Main pool | First 150 cards (cards[0..149]) | Primary learning material. Mastery is measured against this pool. |
| Reserve pool | Cards 151+ (cards[150..]) | Source for review material. Prevents phrase memorization. |

**Why 150 cards?** The threshold represents a balance between sufficient exposure for pattern internalization and practical lesson size. At 10 cards per sub-lesson, 150 cards translates to 15 sub-lessons -- a reasonable scope for a single grammatical pattern.

**Reserve pool rationale:** A key risk in pattern learning is that the learner memorizes specific phrases rather than internalizing the underlying grammatical pattern. The reserve pool mitigates this by providing alternative sentences that use the same pattern but with different vocabulary. When review cards are needed (in MIXED sub-lessons and daily practice), the system draws from the reserve pool first, ensuring the learner encounters novel sentences that test the pattern, not rote memory.

### 18.2.4 Active Set / Reserve Set Rotation

The `MixedReviewScheduler` implements the rotation logic:

1. **NEW_ONLY sub-lessons:** Only main pool cards of the current lesson are used. The learner focuses purely on the new pattern without interference from old material.

2. **MIXED sub-lessons:** The current lesson's cards are combined with review cards from previous lessons. Review cards are drawn **first from reserve pools** of earlier lessons, then from main pools if reserves are exhausted.

3. **Priority order for review cards:**
   - Reserve pools of due lessons (highest priority -- novel sentences, same pattern).
   - Main pools of due lessons (secondary -- previously seen sentences).
   - Current lesson fallback (if no review material is available).

4. **300-card review cap:** `MAX_REVIEW_CARDS = 300` limits the total review card pool per lesson to prevent overwhelming the learner and to keep sessions focused.

### 18.2.5 Mastery Thresholds for Flower Transitions

The flower state is determined by two independent metrics: mastery percentage and health percentage.

| Mastery Range | Flower State | Psychological Meaning |
|---------------|-------------|----------------------|
| 0% (no cards shown) | SEED | Ready to begin, potential not yet realized |
| 0% -- 32.9% | SEED | Early exposure, pattern recognition forming |
| 33% -- 65.9% | SPROUT | Growing competence, pattern becoming familiar |
| 66% -- 100% | BLOOM | Strong mastery, pattern internalized |

These thresholds (33% and 66%) were chosen to create three roughly equal bands of progress. The one-third and two-thirds marks provide meaningful psychological milestones: crossing 33% represents "I've seen enough examples to recognize the pattern," while crossing 66% represents "I can produce the pattern reliably."

---

## 18.3 Flower Metaphor

### 18.3.1 Complete Flower State Machine

```
LOCKED --> SEED --> SPROUT --> BLOOM --> WILTING --> WILTED --> GONE
 ^          ^                                        ^          ^
 |          |                                        |          |
 |          +--- (lesson started, 0 cards shown) ----+          |
 |                                                          (90+ days
 |                                                          neglect)
 +--- (prerequisite lessons not completed)
```

**Transition rules:**

| From | To | Condition |
|------|----|-----------|
| (prerequisites unmet) | LOCKED | Lesson is locked by lesson ladder progression |
| (lesson started, 0 cards) | SEED | `mastery == null` or `uniqueCardShows == 0` |
| SEED | SEED | `masteryPercent < 0.33` AND `health == 1.0` |
| SEED | SPROUT | `masteryPercent >= 0.33` AND `health == 1.0` |
| SPROUT | BLOOM | `masteryPercent >= 0.66` AND `health == 1.0` |
| BLOOM | WILTING | `health < 1.0` AND `health > 0.5` (overdue review) |
| any non-GONE | WILTED | `health <= 0.5 + epsilon` (severely overdue) |
| any non-GONE | GONE | `daysSinceLastShow > 90` (complete neglect) |

### 18.3.2 What Each State Represents Psychologically

| State | Emoji | Meaning |
|-------|-------|---------|
| LOCKED | padlock | The lesson is not yet available. The learner has not reached this point in the curriculum. No interaction possible. |
| SEED | seedling | The lesson has been started but mastery is below 33%. The grammatical pattern is being introduced. The seed represents potential -- it has been planted but needs nurturing. |
| SPROUT | herb | Mastery is between 33% and 66%. The pattern is growing in the learner's mind. They have been exposed to many examples and are starting to internalize the structure. |
| BLOOM | cherry blossom | Mastery is 66% or above. The pattern has been internalized. The learner can reliably produce correct constructions using this pattern. |
| WILTING | wilted flower | Health has dropped below 100%. The learner has missed a scheduled review. The pattern is still present but weakening. Urgency: review soon to prevent further decay. |
| WILTED | fallen leaf | Health has dropped to 50% (the minimum non-zero health). The pattern is significantly degraded. Recovery requires active practice. |
| GONE | black circle | More than 90 days have passed since the last review. The pattern is considered lost. Mastery resets to 0. The learner must start this lesson over. |

### 18.3.3 Health Decay Mechanics

Health (`healthPercent`) is calculated by `SpacedRepetitionConfig.calculateHealthPercent()`:

1. **Within interval** (`daysSinceLastShow <= expectedInterval`): Health = 100%. The flower is healthy and vibrant.

2. **Slightly overdue** (1-5 days past interval): Health decays exponentially from 100% toward 50%. The formula:
   ```
   health = 0.5 + 0.5 * e^(-overdueDays / stability)
   ```
   Where `overdueDays = daysSinceLastShow - expectedInterval`.

3. **Floor at 50%**: Health never drops below 0.5 (WILTED_THRESHOLD) unless the flower enters the GONE state. This ensures the learner always has a recoverable flower unless they completely abandon it for 90+ days.

4. **GONE state** (90+ days without review): Health = 0%. The flower disappears entirely.

**Key design decision:** Health decays smoothly but is restored to 100% instantly upon any card show. There is no "partial recovery" -- a single practice session fully restores the flower. This was a deliberate choice to avoid penalizing learners who practice irregularly. The decay serves as a motivational signal (wilting flowers look sad) rather than a permanent penalty.

### 18.3.4 Recovery from WILTING/WILTED

To recover a wilting or wilted flower, the learner simply needs to practice the lesson again. Any card show within the lesson:
- Resets `lastShowDateMs` to the current time.
- Recalculates `daysSinceLastShow` as 0 (or keeps it at 0).
- `calculateHealthPercent()` returns 1.0 for `daysSinceLastShow <= 0`.
- The flower returns to its mastery-appropriate state (SEED, SPROUT, or BLOOM).

Additionally, if the review was on time, the interval step advances, making the next review due later and reducing the likelihood of future wilting.

### 18.3.5 Scale Multiplier

The visual size of the flower emoji on screen is determined by:

```
scaleMultiplier = max(0.5, masteryPercent * healthPercent)
```

This product means:
- A fully bloomed flower (100% mastery, 100% health) has scale 1.0 (full size).
- A sprout (50% mastery, 100% health) has scale 0.5 (minimum visible size).
- A wilted bloom (100% mastery, 50% health) has scale 0.5.
- The minimum is clamped at 0.5 to ensure all flowers remain visible.

This creates a visual language where the flower's size simultaneously communicates both how much the learner has mastered and how well-maintained that knowledge is.

### 18.3.6 Visual Representation (Emoji Mapping)

| FlowerState | Emoji | Unicode |
|-------------|-------|---------|
| LOCKED | padlock | U+1F512 |
| SEED | seedling | U+1F331 |
| SPROUT | herb | U+1F33F |
| BLOOM | cherry blossom | U+1F338 |
| WILTING | wilted flower | U+1F940 |
| WILTED | fallen leaf | U+1F342 |
| GONE | black circle | U+26AB |

---

## 18.4 Lesson Scheduling

### 18.4.1 Sub-Lesson Types

The `MixedReviewScheduler` produces two types of sub-lessons:

**NEW_ONLY (`SubLessonType.NEW_ONLY`):**
- Contains only fresh cards from the current lesson.
- Used for the first sub-lessons of a lesson, before any mixing with old material.
- Rationale: The learner needs focused exposure to the new grammatical pattern without interference from previously learned patterns. This aligns with the "desirable difficulties" principle -- the difficulty of pure new material is optimal for initial encoding, and adding review material would create undesirable interference during the critical early learning phase.

**MIXED (`SubLessonType.MIXED`):**
- Contains approximately 50% new cards from the current lesson and 50% review cards from previous lessons.
- Used after the learner has completed the initial NEW_ONLY sub-lessons.
- Rationale: Interleaving new and old material forces the learner to discriminate between patterns, strengthening retrieval pathways. Research on interleaved practice (Rohrer et al., 2015) shows that mixed practice, though it feels harder, produces better long-term retention than blocked practice.

### 18.4.2 Review Card Selection (Due Date Calculation)

Review cards are selected based on the interval ladder. The `MixedReviewScheduler` tracks a `globalMixedIndex` that increments with each MIXED sub-lesson. For each previous lesson, it records the `reviewStartMixedIndex` -- the point at which that lesson's cards become eligible for review.

A lesson is considered "due for review" when:

```
globalMixedIndex - reviewStartMixedIndex matches a value in [1, 2, 4, 7, 10, 14, 20, 28, 42, 56]
```

This mirrors the same interval ladder used for mastery but applies it to the scheduling of review insertions. Lessons that are most overdue are selected first, with a maximum of 2 due lessons contributing review cards to any single MIXED sub-lesson.

### 18.4.3 Reserve Pool Usage to Prevent Memorization

The `fillReviewSlots()` method in `MixedReviewScheduler` uses a three-tier priority system:

1. **Reserve pool cards first:** Cards from the reserve pool (beyond the first 150) of due lessons. These are novel sentences using the same grammatical pattern the learner has studied, preventing memorization of specific phrases.

2. **Main pool cards second:** If the reserve pool is exhausted, cards from the main pool (first 150) of due lessons. These may be sentences the learner has seen before, but they still provide pattern reinforcement.

3. **Fallback to current lesson:** If no review cards are available from previous lessons, additional cards from the current lesson are used to fill the remaining slots.

Within each tier, cards are drawn in a round-robin fashion across multiple due lessons to ensure variety.

### 18.4.4 300-Card Review Cap

`MAX_REVIEW_CARDS = 300` limits the total number of review cards available per lesson. This cap:
- Prevents sessions from becoming excessively long.
- Ensures the review pool remains manageable.
- Forces prioritization of the most important review cards.

The cap is applied by shuffling the combined main+reserve pool and taking the first 300 cards, then splitting those into the main and reserve portions for the round-robin selection.

### 18.4.5 Sub-Lesson Progression Order

Within a lesson, sub-lessons are generated in this order:

1. NEW_ONLY sub-lessons are generated first (from the first half of the lesson's cards).
2. MIXED sub-lessons follow (from the second half of the lesson's cards, combined with review material).

The first lesson in a pack has no previous lessons to review, so it generates only NEW_ONLY sub-lessons. All subsequent lessons generate a mix of both types.

The split point is approximately 50%: the first half of the lesson's cards go into NEW_ONLY sub-lessons, and the second half provides the "current" portion of MIXED sub-lessons. This ensures the learner gets a solid foundation of pure new material before encountering mixed practice.

---

## 18.5 Daily Practice Methodology

### 18.5.1 Three-Block Structure Rationale

Daily Practice sessions consist of three distinct blocks, each targeting a different aspect of language competence:

| Block | Type | Cards | Focus |
|-------|------|-------|-------|
| 1 | TRANSLATE | 10 | Sentence translation (RU to target language) |
| 2 | VOCAB | 10 | Anki-style vocabulary flashcards |
| 3 | VERBS | 10 | Verb conjugation drill |

**Why this combination:**

1. **Translation (Block 1):** Tests the core skill -- grammatical pattern production in context. This is the primary learning mechanism of the app. Translating complete sentences forces the learner to apply grammar patterns, not just recognize them.

2. **Vocabulary (Block 2):** Vocabulary knowledge is a prerequisite for pattern production. If the learner knows the grammar but lacks the words, production fails. Flashcard review maintains and expands the learner's vocabulary independently from grammar lessons. The Anki-style SRS ensures words are reviewed at optimal intervals.

3. **Verb conjugation (Block 3):** For languages with rich verb morphology (especially Italian), conjugation is a distinct skill from sentence construction. Learners may be able to choose the correct tense but conjugate the verb incorrectly. Dedicated conjugation practice addresses this gap.

**Ordering rationale:** Translation comes first because it is the highest-cognitive-load activity and the primary skill the app develops. Vocabulary comes second as a lighter, complementary activity. Verbs come last because they are a focused drill that benefits from the warm-up effect of the previous blocks.

### 18.5.2 Why 10+10+10 Cards Per Session

The total of 30 cards per daily session is calibrated for:

1. **Session duration:** At typical practice rates (~1-2 minutes per card including feedback), a full session takes 30-60 minutes. The 10-card blocks allow natural pause points -- the learner can complete a single block in 10-20 minutes.

2. **Cognitive load:** Research on learning sessions suggests that shorter, focused blocks are more effective than long, undifferentiated sessions. The 10-card block size is large enough to provide meaningful practice but small enough to maintain attention.

3. **Spaced repetition coverage:** Ten cards per block ensures that the SRS algorithms have enough data to work with. For vocabulary, 10 cards per day with proper spacing can maintain hundreds of words in active memory.

4. **Motivation:** Completing a full session of 30 cards provides a sense of accomplishment. Each completed block provides a micro-accomplishment that reinforces the habit loop.

### 18.5.3 How Daily Practice Differs from Regular Lessons

| Aspect | Regular Lessons | Daily Practice |
|--------|----------------|----------------|
| Card selection | Sequential within lesson | Cursor-based across lessons |
| Content type | Sentences only | Mixed (sentences + vocab + verbs) |
| Input mode | User-selected | Alternating (VOICE, KEYBOARD, WORD_BANK rotate per card) |
| Progress tracking | Lesson mastery + flower | Streak tracking + session completion |
| SRS integration | Interval ladder per lesson | Independent SRS per block |
| Pool | Lesson-specific | Pack-wide (all installed lessons) |
| Repeat capability | No | Yes (replays first session's card IDs) |

Daily Practice uses a **cursor-based progression** (`DailyCursorState`) that tracks:
- `sentenceOffset`: How many cards have been shown in the current lesson.
- `currentLessonIndex`: Which lesson in the pack is currently active.
- `firstSessionSentenceCardIds` / `firstSessionVerbCardIds`: Card IDs from the first session of the day, enabling the "Repeat" feature.

When a lesson is exhausted (no more cards available at the current offset), the cursor advances to the next lesson in the pack. This ensures the learner encounters fresh material each day while systematically progressing through the curriculum.

### 18.5.4 Streak Tracking and Motivation

The `StreakStore` tracks daily engagement:

- **currentStreak**: Number of consecutive days the learner has completed at least one sub-lesson.
- **longestStreak**: The all-time best streak.
- **totalSubLessonsCompleted**: Cumulative count of completed sub-lessons.

**Streak rules:**
1. First ever completion: streak = 1.
2. Same-day completion: streak unchanged (no double-counting).
3. Consecutive day (was yesterday): streak increments by 1.
4. Gap of 2+ days: streak resets to 1.

The streak counter updates only when a sub-lesson is completed -- the learner must actually finish a block, not just open the app. This ensures the streak reflects genuine practice, not passive engagement.

---

## 18.6 Verb Conjugation Drill Methodology

### 18.6.1 How Verb Conjugation Is Taught

Verb drills present the learner with a Russian prompt (e.g., "I am tired") and expect the corresponding Italian conjugated form (e.g., "sono stanco"). Each drill card is tagged with:

- **verb**: The infinitive form (e.g., "essere").
- **tense**: The grammatical tense (e.g., "Presente", "Imperfetto").
- **group**: The conjugation group (e.g., regular -are, -ere, -ire, or irregular).
- **rank**: An optional frequency ranking for prioritization.

The learner produces the conjugated form via keyboard or word bank. Input mode alternates between KEYBOARD (even-indexed cards) and WORD_BANK (odd-indexed cards).

### 18.6.2 Tense Progression (Tense Ladder)

Verb drills follow a cumulative tense progression tied to lesson level:

| Lesson Level | Active Tenses |
|--------------|--------------|
| 1 | Presente |
| 2 | + Imperfetto |
| 3 | + Passato Prossimo |
| 4 | + Futuro Semplice |
| 5 | + Condizionale Presente |
| 6 | + Passato Remoto |
| 7 | + Congiuntivo Presente |
| 8 | + Trapassato Prossimo |
| 9 | + Futuro Anteriore |
| 10 | + Congiuntivo Imperfetto |
| 11 | + Condizionale Passato |
| 12 | + Congiuntivo Passato |

This progression ensures the learner masters basic tenses before encountering advanced ones. Each new level adds one tense to the pool rather than replacing the previous ones, providing cumulative reinforcement.

### 18.6.3 Group/Tense Progression

Progress is tracked per combination of group and tense via `VerbDrillComboProgress`. Each combo records:
- `everShownCardIds`: All cards ever shown for this group/tense combination.
- `todayShownCardIds`: Cards shown today (reset at day boundary).
- `totalCards`: Total cards available for this combo.

This granular tracking allows the system to:
- Determine which combos have been fully practiced.
- Identify weak areas (combos with few shown cards relative to total).
- Prevent re-showing cards that have already been encountered.

### 18.6.4 Weak-Area Targeting in Daily Practice

The `DailySessionComposer` selects verb drill cards using a **weak-first** algorithm:

1. Load all verb cards matching the learner's active tenses.
2. Exclude cards that have been previously shown (via `everShownCardIds`).
3. Score each remaining card by weakness:
   ```
   weakness = 1 - (shownInTense / totalInTense)
   ```
   A tense with few shown cards relative to its total is considered "weak."
4. Sort by weakness descending (weakest first), with stable secondary ordering by original index.
5. Take the top 10 cards.

This ensures the learner's weakest areas receive priority attention. If all tenses are equally practiced, the ordering falls back to the original card sequence.

---

## 18.7 Vocabulary Drill Methodology

### 18.7.1 Anki-Style Flashcard Approach

The vocab drill uses a spaced repetition system inspired by Anki, with per-word tracking via `WordMasteryStore`. Each word has its own SRS state:

| Field | Description |
|-------|-------------|
| `intervalStepIndex` | Current position on the interval ladder (0-9) |
| `correctCount` | Total number of correct answers |
| `incorrectCount` | Total number of wrong answers |
| `lastReviewDateMs` | Timestamp of the most recent review |
| `nextReviewDateMs` | Computed: `lastReviewDateMs + ladder[step] * DAY_MS` |
| `isLearned` | `true` when `intervalStepIndex` reaches 9 (the last step) |

A word is considered **learned** when it has been successfully reviewed through all 10 interval steps. This typically takes several weeks of practice.

### 18.7.2 Spaced Repetition for Individual Words

Each word follows its own independent SRS schedule. When a word is answered correctly:
- `intervalStepIndex` advances by 1 (capped at 9).
- `nextReviewDateMs` is computed based on the new interval.
- `correctCount` increments.

When answered incorrectly:
- `intervalStepIndex` resets to 0 (full reset to the beginning of the ladder).
- `incorrectCount` increments.
- The word will be due for review again in 1 day.

This asymmetric penalty (correct = advance 1 step; incorrect = reset to step 0) ensures that only consistently known words reach the "learned" state. A single incorrect answer after weeks of correct answers sends the word back to daily review.

### 18.7.3 Mastery Tracking Per Word

`WordMasteryStore` provides several query methods:

- `getDueWords()`: Returns the set of word IDs where `nextReviewDateMs <= now` or `lastReviewDateMs == 0` (never reviewed). These are the words eligible for practice.
- `getMasteredCount(pos)`: Counts words where `isLearned == true`, optionally filtered by part of speech (nouns, verbs, adjectives, etc.).
- `getMasteredByPos()`: Returns a breakdown of mastered words by part of speech, enabling progress visualization.

### 18.7.4 Bidirectional Testing

Vocabulary flashcards in daily practice alternate between two directions:

| Direction | Prompt | Expected Answer |
|-----------|--------|-----------------|
| `IT_TO_RU` | Italian word | Russian translation |
| `RU_TO_IT` | Russian meaning | Italian word |

Even-indexed cards use `IT_TO_RU`; odd-indexed cards use `RU_TO_IT`. Bidirectional testing ensures the learner can both recognize foreign words (passive vocabulary) and produce them from native-language prompts (active vocabulary).

### 18.7.5 SRS-Based Selection in Daily Practice

The `buildVocabBlock()` method in `DailySessionComposer` uses a three-tier selection:

1. **Due words (most overdue first):** Words whose `nextReviewDateMs` has passed. Sorted by `overdueMs` descending -- the most overdue words are shown first.
2. **New words (never reviewed):** Sorted by rank (frequency order). High-frequency words are introduced before low-frequency ones.
3. **Fallback (least recently reviewed):** If there are not enough due or new words to fill 10 slots, the least recently reviewed words are selected to provide additional practice.

This ensures that:
- Overdue reviews always take priority (preventing knowledge decay).
- New words are introduced in frequency order (most useful words first).
- No session is empty as long as there are any words in the pack.

---

## 18.8 Boss Battle Design

### 18.8.1 What Boss Battles Test

Boss battles are optional end-of-lesson challenges designed to test **pattern stability under pressure**. Unlike regular sub-lessons where the learner practices a few cards at a time, boss battles present a large volume of sentences in a single session, testing:

1. **Endurance:** Can the learner maintain accuracy across many consecutive sentences?
2. **Interference resistance:** Can the learner correctly apply the pattern when fatigue sets in and competing patterns interfere?
3. **Automaticism:** Has the pattern been internalized to the point where it can be produced reliably without conscious effort?

### 18.8.2 Boss Types

| Type | Scope | Description |
|------|-------|-------------|
| LESSON boss | Single lesson | Tests all sentences from one lesson. Available after completing all sub-lessons. |
| MEGA boss | All completed lessons | Tests sentences from all lessons completed so far. Available after completing each lesson. |

### 18.8.3 Medal System

Boss performance is measured by the percentage of correct answers:

| Medal | Threshold | Pool |
|-------|-----------|------|
| BRONZE | >= 30% | Active set only (main pool, first 150 cards) |
| SILVER | >= 60-70% | Active set only |
| GOLD | 100% | Active set + Reserve set (all cards) |

The medal system provides aspirational goals:
- Bronze is achievable with moderate mastery -- it validates that the learner has basic familiarity with the pattern.
- Silver requires strong mastery -- the learner must consistently produce correct constructions.
- Gold requires perfect accuracy across a larger pool including reserve sentences. This tests whether the learner has truly internalized the pattern rather than memorized specific phrases.

### 18.8.4 How Boss Battles Relate to Lesson Mastery

Boss battles are separate from the mastery/flower system. They do not directly affect `uniqueCardShows` or flower state. Instead, they serve as:

1. **Validation:** A high boss score confirms that the flower state accurately reflects the learner's ability. A BLOOM flower with a Gold boss medal is a strong signal of true mastery.

2. **Motivation:** The medal system provides extrinsic motivation beyond the intrinsic reward of watching flowers grow.

3. **Diagnostic tool:** Low boss performance despite a high flower state may indicate that the learner has been practicing irregularly (the flower is sustained by periodic reviews without deep encoding) or that the pattern has been learned for specific phrases but not generalized.

Boss rewards are tracked separately in `TrainingProgress`:
- `bossLessonRewards`: Map of lessonId to medal type for lesson bosses.
- `bossMegaRewards`: Map of lessonId to medal type for mega bosses.

---

## 18.9 Motivation and Engagement Mechanics

### 18.9.1 Streak System

The streak system (`StreakStore`) provides a simple but powerful motivation mechanic:

- **Daily commitment:** The streak increments only when the learner completes at least one sub-lesson in a calendar day. Opening the app without practicing does not count.
- **Loss aversion:** The fear of losing a streak is a well-documented motivator (behavioral economics: loss aversion). The streak counter is visible and prominent, making the cost of skipping a day psychologically salient.
- **Recovery is gentle:** Missing one day resets the streak to 0, but the next practice immediately starts a new streak of 1. This avoids the demotivating "I already broke it, why bother?" trap of systems with harsher penalties.
- **Longest streak tracking:** The `longestStreak` field provides a personal best to beat, encouraging the learner to surpass their previous record.

### 18.9.2 Flower Visualization as Progress Indicator

The flower metaphor serves multiple motivational functions:

1. **Growth narrative:** SEED -> SPROUT -> BLOOM tells a story of growth that maps naturally to learning. This is more emotionally engaging than a percentage counter.

2. **Neglect visibility:** WILTING -> WILTED -> GONE provides a guilt mechanism. Seeing a wilted flower creates an emotional response that motivates the learner to practice ("save the flower").

3. **At-a-glance status:** The skill matrix on the home screen displays all lesson flowers simultaneously, giving the learner an immediate visual overview of their overall progress.

4. **Multi-dimensional:** Unlike a simple progress bar, the flower conveys two dimensions simultaneously -- mastery (the flower type) and maintenance (the flower's health/size).

### 18.9.3 Sound Feedback

The app provides audio feedback for correct and incorrect answers:
- **Correct answer:** Positive sound reinforcement.
- **Incorrect answer:** Error sound that signals the mistake without being punitive.
- **TTS playback:** After showing the correct answer, the learner can hear the target-language sentence via offline TTS (Sherpa-ONNX), reinforcing the auditory channel.

Sound feedback serves dual purposes:
- Immediate reinforcement (operant conditioning: correct answer -> positive stimulus).
- Multimodal learning: combining visual, auditory, and production channels strengthens memory encoding.

### 18.9.4 Daily Practice as Habit Formation Tool

Daily Practice is specifically designed as a habit formation mechanism:

1. **Consistent structure:** The same 3-block format every day creates a predictable routine. Habit research (Wood & Neal, 2007) shows that consistency is key to habit formation.

2. **Low barrier to start:** A single block takes 10-20 minutes. The learner can commit to "just one block" and still make progress.

3. **Streak integration:** Daily Practice contributes to the streak, tying it to the broader motivation system.

4. **Fresh content:** The cursor-based system ensures the learner encounters new material each day, preventing boredom.

5. **Repeat option:** After completing a session, the learner can repeat the same cards for reinforcement. The first session's card IDs are cached in `DailyCursorState`, enabling exact replay.

---

## 18.10 Critique and Improvement Opportunities

### 18.10.1 What the Current Methodology Does Well

1. **Strong retrieval practice foundation:** The WORD_BANK exclusion from mastery ensures that the mastery metric reflects genuine recall, not recognition. This is a significant advantage over apps that count all interactions equally.

2. **Dual-metric flower system:** Separating mastery (knowledge) from health (maintenance) is pedagogically sound. It correctly distinguishes between "I don't know this" and "I knew this but haven't reviewed it."

3. **Reserve pool design:** Using separate card pools for learning and review is a sophisticated approach to the phrase-memorization problem that many language apps do not address.

4. **Interval ladder simplicity:** The fixed 10-rung ladder is easy to understand and implement. It avoids the complexity of adaptive algorithms (like SuperMemo SM-2 or Anki's variants) while still providing effective spacing.

5. **Daily Practice integration:** The 3-block daily session combining grammar, vocabulary, and verbs is a comprehensive approach that addresses multiple skill dimensions in a single session.

6. **Weak-first targeting:** The verb drill's weakness-based card selection ensures that the learner's weakest areas receive priority attention, making practice time more efficient.

### 18.10.2 Potential Improvements Based on Learning Science

1. **Interval regression on late review:** Currently, late reviews do not regress the interval step -- they simply do not advance it. Research on the spacing effect suggests that significantly overdue material should have its interval reduced. A proportional regression (e.g., reduce step by 1 if overdue by 2x, by 2 if overdue by 5x) would better reflect the actual memory state.

2. **Answer confidence weighting:** The interval advances regardless of how many incorrect attempts preceded the correct answer. A learner who answers correctly on the first try should advance faster than one who needs three attempts. Tracking `incorrectAttemptsForCard` and using it to modulate interval advancement would improve accuracy.

3. **Per-card intervals:** Currently, `intervalStepIndex` is tracked at the lesson level, not the individual card level. All cards in a lesson share the same interval, even if the learner consistently struggles with specific sentences. Per-card tracking (as used in Anki) would enable more precise spacing.

4. **Adaptive sub-lesson sizing:** All sub-lessons use the same size (10 cards by default). Research on desirable difficulties suggests that sub-lesson size could be adapted based on the learner's performance -- smaller sub-lessons for difficult material, larger for easy material.

5. **Interleaving optimization:** The current MIXED sub-lessons use a fixed 50/50 split. Research on interleaving (Rohrer, 2012) suggests that the optimal mix depends on the learner's stage -- more review when patterns are fragile, less when they are robust.

6. **Spaced repetition for vocabulary within lessons:** The `VocabProgressStore` uses a separate, shorter interval ladder [1, 3, 7, 14, 30 days] for vocabulary, which is appropriate. However, integrating vocabulary review into grammar sessions (rather than keeping them in separate blocks) could strengthen the word-pattern associations.

7. **Mastery threshold adaptivity:** The fixed 150-card threshold applies uniformly regardless of lesson size. A lesson with 50 cards requires 3 full cycles to reach mastery, while a lesson with 300 cards reaches mastery before completing a single cycle. Adapting the threshold to lesson size or using a cycle-based metric could be more equitable.

### 18.10.3 Gaps in the Current Approach

1. **No productive vocabulary testing in grammar blocks:** Grammar translation blocks use VOICE and KEYBOARD modes, which test productive recall of sentence patterns. However, vocabulary knowledge is assumed -- if the learner doesn't know a word in the sentence, the grammar test becomes a vocabulary test instead. Better separation or scaffolding (e.g., pre-teaching vocabulary used in upcoming grammar sentences) would reduce frustration.

2. **No explicit error correction:** When the learner answers incorrectly, the app shows the correct answer but does not explain *why* the learner's answer was wrong or *what pattern* they should have used. Adding targeted feedback (e.g., "You used Present Simple, but this sentence requires Present Perfect because of the 'already' keyword") would improve the learning loop.

3. **No difficulty calibration:** The app does not track or adapt to individual card difficulty. Some sentences are inherently harder (longer, less common vocabulary, irregular forms) than others. A difficulty rating per card (based on population-level accuracy or individual performance) could improve session composition.

4. **No long-term retention analytics:** The app tracks mastery and health but does not provide the learner with insights into their long-term retention trends. A retention curve visualization (showing how patterns decay over time across lessons) would help the learner understand their own learning patterns and adjust their practice accordingly.

5. **Limited metacognitive support:** The app does not help the learner develop metacognitive awareness -- the ability to judge what they know and don't know. Features like confidence ratings (e.g., "How sure are you of this answer?") before revealing correctness would strengthen self-assessment skills.

6. **No communicative context:** All practice is decontextualized sentence translation. While this is effective for pattern drilling, it does not prepare the learner for real communication where sentences are embedded in conversational context. The Story Quiz feature partially addresses this but is limited to reading comprehension rather than production.

7. **No pronunciation feedback:** For VOICE mode, the app only checks whether the recognized text matches the expected answer. It does not provide feedback on pronunciation quality. A learner could produce grammatically correct but poorly pronounced output and receive no correction.

8. **Warm-up removal:** The original specification included a 3-sentence warm-up before each sub-lesson to reduce the barrier to starting. This was removed during development (only `NEW_ONLY` and `MIXED` sub-lesson types exist; `WARMUP` was explicitly removed). Reintroducing a warm-up phase could improve session-start engagement, particularly for learners who experience "starting friction."

---

*Source files referenced:*
- `SpacedRepetitionConfig.kt` -- forgetting curve formula, interval ladder, stability and health calculations
- `FlowerCalculator.kt` -- flower state machine, mastery-to-state mapping, health-based transitions
- `MasteryStore.kt` -- lesson mastery persistence, card show recording, interval advancement
- `ProgressStore.kt` -- training session state persistence including daily cursor
- `MixedReviewScheduler.kt` -- sub-lesson generation, review card selection, pool management
- `StreakStore.kt` -- streak tracking and daily engagement
- `WordMasteryStore.kt` -- per-word SRS state, due word queries, mastery counts
- `VocabProgressStore.kt` -- vocab sprint progress, Anki-like SRS per entry
- `VerbDrillStore.kt` -- verb drill progress, combo tracking, card loading
- `DailySessionComposer.kt` -- daily practice session construction, block builders
- `DailySessionHelper.kt` -- daily session state management, block progression
- `DailyPracticeSessionProvider.kt` -- card session contract for daily practice blocks
- `Models.kt` -- data classes for all domain objects, enums, state types
- `LessonLadderCalculator.kt` -- lesson unlock order and interval display
- `GrammarMate_project_idea_v1_5.md` -- original project specification
- `docs/FORGETTING_CURVE_ANALYSIS.md` -- detailed analysis of forgetting curve implementation
