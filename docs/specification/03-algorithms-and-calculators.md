# 3. Algorithms & Calculators -- Specification

This section documents the core learning algorithms of GrammarMate: spaced repetition, visual progress (flower state machine), lesson scheduling, ladder metrics, and answer normalization. These modules implement the Ebbinghaus forgetting curve, interval scheduling, and answer comparison logic that drive the entire learning experience.

---

## 3.1 SpacedRepetitionConfig

**Source file:** `app/src/main/java/com/alexpo/grammermate/data/SpacedRepetitionConfig.kt`
**Test file:** `app/src/test/java/com/alexpo/grammermate/data/SpacedRepetitionConfigTest.kt`

### Purpose

Calculates memory retention, flower health decay, and interval progression using the Ebbinghaus forgetting curve. This is the mathematical foundation of the entire spaced repetition system.

### Input data classes

`SpacedRepetitionConfig` is a Kotlin `object` (singleton). It operates on these implicit inputs per function:

| Function | Parameters | Types |
|---|---|---|
| `calculateStability` | `intervalStepIndex` | `Int` -- index into the interval ladder (0-based) |
| `calculateRetention` | `daysSinceLastShow`, `intervalStepIndex` | `Int`, `Int` |
| `calculateHealthPercent` | `daysSinceLastShow`, `intervalStepIndex` | `Int`, `Int` |
| `nextIntervalStep` | `currentStepIndex`, `wasOnTime` | `Int`, `Boolean` |
| `wasRepetitionOnTime` | `daysSinceLastShow`, `intervalStepIndex` | `Int`, `Int` |

### Constants

| Constant | Value | Semantics |
|---|---|---|
| `INTERVAL_LADDER_DAYS` | `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` | Expected days between repetitions at each step |
| `MASTERY_THRESHOLD` | `150` | Number of unique card shows for 100% mastery |
| `WILTED_THRESHOLD` | `0.5f` | Health floor; below this the flower is considered WILTED |
| `GONE_THRESHOLD_DAYS` | `90` | Days without review after which the flower disappears entirely |
| `BASE_STABILITY_DAYS` | `0.9` (private) | Initial memory stability S0 in days; derives from ~33% retention after 1 day |
| `STABILITY_MULTIPLIER` | `2.2` (private) | Exponential growth factor per successful repetition |

### Output

All functions return `Float` or `Int`:
- `calculateStability` returns `Double` -- memory stability in days
- `calculateRetention` returns `Float` in range `[0.0, 1.0]`
- `calculateHealthPercent` returns `Float` in range `[0.0, 1.0]` (but normally `[WILTED_THRESHOLD, 1.0]`, or `0.0` for GONE)
- `nextIntervalStep` returns `Int` -- new ladder index
- `wasRepetitionOnTime` returns `Boolean`

### Algorithm

#### 3.1.1 Forgetting curve formula

The Ebbinghaus forgetting curve is:

```
R = e^(-t / S)
```

Where:
- `R` = retention probability (0.0 to 1.0)
- `t` = days since last review
- `S` = memory stability in days

#### 3.1.2 Memory stability calculation

```
calculateStability(intervalStepIndex):
    if intervalStepIndex < 0:
        return BASE_STABILITY_DAYS  // 0.9 days

    stability = BASE_STABILITY_DAYS  // 0.9
    effectiveStep = min(intervalStepIndex, INTERVAL_LADDER_DAYS.size - 1)
    repeat effectiveStep times:
        stability = stability * STABILITY_MULTIPLIER  // * 2.2
    return stability
```

Computed stability values per step (approximate):

| Step | Ladder day | Stability (days) |
|------|-----------|-------------------|
| 0 | 1 | 0.9 |
| 1 | 2 | 1.98 |
| 2 | 4 | 4.36 |
| 3 | 7 | 9.59 |
| 4 | 10 | 21.10 |
| 5 | 14 | 46.41 |
| 6 | 20 | 102.11 |
| 7 | 28 | 224.64 |
| 8 | 42 | 494.21 |
| 9 | 56 | 1087.26 |

#### 3.1.3 Retention calculation

```
calculateRetention(daysSinceLastShow, intervalStepIndex):
    if daysSinceLastShow <= 0:
        return 1.0  // just reviewed, full retention

    stability = calculateStability(intervalStepIndex)
    retention = e^(-daysSinceLastShow / stability)
    return clamp(retention, 0.0, 1.0)
```

#### 3.1.4 Health percent calculation

```
calculateHealthPercent(daysSinceLastShow, intervalStepIndex):
    if daysSinceLastShow <= 0:
        return 1.0  // full health
    if daysSinceLastShow >= GONE_THRESHOLD_DAYS (90):
        return 0.0  // flower is gone

    expectedInterval = INTERVAL_LADDER_DAYS[intervalStepIndex]
        ?? INTERVAL_LADDER_DAYS.last()  // fallback for out-of-range

    if daysSinceLastShow <= expectedInterval:
        return 1.0  // on schedule, full health

    overdueDays = daysSinceLastShow - expectedInterval
    stability = calculateStability(intervalStepIndex)
    decay = e^(-overdueDays / stability)
    health = WILTED_THRESHOLD + (1.0 - WILTED_THRESHOLD) * decay
    return clamp(health, WILTED_THRESHOLD, 1.0)
```

Health asymptotically approaches `WILTED_THRESHOLD` (0.5) but never drops below it -- unless the flower is GONE (>90 days), in which case health is 0.0.

#### 3.1.5 Interval step progression

```
nextIntervalStep(currentStepIndex, wasOnTime):
    if wasOnTime:
        return min(currentStepIndex + 1, INTERVAL_LADDER_DAYS.size - 1)
    else:
        return max(currentStepIndex, 0)
```

- On-time review: advance one step up the ladder (capped at step 9 / 56 days).
- Late review: step does NOT change (no regression, but no advancement either).

#### 3.1.6 On-time check

```
wasRepetitionOnTime(daysSinceLastShow, intervalStepIndex):
    expectedInterval = INTERVAL_LADDER_DAYS[intervalStepIndex]
        ?? INTERVAL_LADDER_DAYS.last()
    return daysSinceLastShow <= expectedInterval
```

### Edge cases

| Condition | Behavior |
|---|---|
| `intervalStepIndex < 0` | `calculateStability` returns `BASE_STABILITY_DAYS` (0.9) |
| `intervalStepIndex >= 10` | Capped at last ladder step (9); uses 56-day interval |
| `daysSinceLastShow <= 0` | Retention = 1.0, health = 1.0 (just reviewed) |
| `daysSinceLastShow >= 90` | Health = 0.0 (flower GONE) |
| `daysSinceLastShow > expectedInterval` but `< 90` | Health decays from 1.0 toward 0.5, never below 0.5 |
| Out-of-range index in `wasRepetitionOnTime` | Falls back to last ladder interval (56 days) |

### Business rules

1. **Mastery requires 150 unique card shows** -- this is the `MASTERY_THRESHOLD`. Only VOICE and KEYBOARD modes count; WORD_BANK does NOT.
2. **Health never drops below 50% (WILTED_THRESHOLD)** for non-GONE flowers. This prevents the visual from becoming too discouraging.
3. **90 days of inactivity = GONE** -- the flower disappears entirely, resetting visual state to a black circle.
4. **Late reviews do not regress the interval step** -- the learner does not lose progress, they just do not advance.
5. **The interval ladder doubles approximately every 1.5-2 steps**, with ratios: 2.0, 2.0, 1.75, 1.43, 1.4, 1.43, 1.4, 1.5, 1.33.

---

## 3.2 FlowerCalculator

**Source file:** `app/src/main/java/com/alexpo/grammermate/data/FlowerCalculator.kt`
**Test file:** `app/src/test/java/com/alexpo/grammermate/data/FlowerCalculatorTest.kt`

### Purpose

Maps lesson mastery data to a visual flower representation. The flower metaphor provides an intuitive, non-gamified progress indicator: mastery grows the flower, neglect causes it to wilt.

### Input

| Parameter | Type | Constraints |
|---|---|---|
| `mastery` | `LessonMasteryState?` | `null` = lesson not started |
| `totalCardsInLesson` | `Int` | default `0`; currently unused in the algorithm |

`LessonMasteryState` fields:

| Field | Type | Semantics |
|---|---|---|
| `lessonId` | `String` | Lesson identifier |
| `languageId` | `String` | Language pack identifier |
| `uniqueCardShows` | `Int` | Number of unique cards practiced (0..MASTERY_THRESHOLD) |
| `totalCardShows` | `Int` | Total card shows including repeats |
| `lastShowDateMs` | `Long` | Timestamp of last card show (epoch ms), 0 = never |
| `intervalStepIndex` | `Int` | Current position in the interval ladder |
| `completedAtMs` | `Long?` | When the lesson was fully completed, if ever |
| `shownCardIds` | `Set<String>` | IDs of cards that have been shown at least once |

### Output

`FlowerVisual` data class:

| Field | Type | Semantics |
|---|---|---|
| `state` | `FlowerState` | Current flower visual state (enum) |
| `masteryPercent` | `Float` | Mastery as fraction of 150 unique shows, range `[0.0, 1.0]` |
| `healthPercent` | `Float` | Health from Ebbinghaus curve, range `[0.0, 1.0]` |
| `scaleMultiplier` | `Float` | UI scale factor = `masteryPercent * healthPercent`, clamped to `[0.5, 1.0]` |

### Flower state machine

The `FlowerState` enum has 7 states:

```
LOCKED  -- UI-only state. Never produced by FlowerCalculator.
           Used by UI for lessons that are not yet unlocked on the roadmap.
SEED    -- Initial state for started or not-yet-started lessons.
           Mastery = 0% OR mastery data is null.
SPROUT  -- Lesson in progress. Mastery >= 1% and < 33%.
BLOOM   -- Lesson substantially mastered. Mastery >= 33% (actually >= 66%).
           Full health, high mastery.
WILTING -- Health has decayed below 100% but above WILTED_THRESHOLD (50%).
           The flower is overdue for review.
WILTED  -- Health at or below WILTED_THRESHOLD + 0.01 epsilon (approximately 50%).
           Severe neglect.
GONE    -- More than 90 days since last review.
           The flower has disappeared. All metrics reset to 0.
```

### State transition diagram

```
                    +---------+
                    | LOCKED  |  (UI-only, never from calculator)
                    +---------+
                         |
                    (lesson unlocked)
                         v
    +---------+    +---------+
    |  GONE   |<---|  SEED   |<--- (null mastery / uniqueCardShows == 0)
    +---------+    +---------+
         ^              |
         |         (first card shown)
         |              v
         |         +---------+
         |         | SPROUT  |  mastery [1%, 33%)
         |         +---------+
         |              |
         |         (mastery >= 33%)
         |              v
         |         +---------+
         +---------|  BLOOM  |  mastery >= 66%, health = 100%
         |         +---------+
         |              |
         |     (overdue for review)
         |              v
         |         +---------+
         +---------| WILTING |  health (50%, 100%)
         |         +---------+
         |              |
         |     (severe neglect)
         |              v
         |         +---------+
         +---------| WILTED  |  health <= 50%
                   +---------+
                        |
                   (>90 days neglect)
                        v
                   +---------+
                   |  GONE   |  health = 0, mastery = 0
                   +---------+
```

### Algorithm

```
calculate(mastery, totalCardsInLesson):

    // CASE 1: Not started or zero shows
    if mastery == null OR mastery.uniqueCardShows == 0:
        return FlowerVisual(SEED, masteryPercent=0, healthPercent=1.0, scaleMultiplier=0.5)

    // Mastery percentage (capped at 1.0)
    masteryPercent = clamp(mastery.uniqueCardShows / 150, 0.0, 1.0)

    // Days since last review
    daysSince = daysBetween(mastery.lastShowDateMs, now)

    // CASE 2: Gone (>90 days)
    if daysSince > 90:
        return FlowerVisual(GONE, masteryPercent=0, healthPercent=0, scaleMultiplier=0.5)

    // Health from spaced repetition
    healthPercent = SpacedRepetitionConfig.calculateHealthPercent(
        daysSince, mastery.intervalStepIndex)

    // CASE 3: Determine state from health and mastery
    state = determineFlowerState(masteryPercent, healthPercent, mastery)

    // Scale = mastery * health, floored at 0.5
    scale = clamp(masteryPercent * healthPercent, 0.5, 1.0)

    return FlowerVisual(state, masteryPercent, healthPercent, scale)
```

```
determineFlowerState(masteryPercent, healthPercent, mastery):
    if healthPercent <= WILTED_THRESHOLD + 0.01:   // <= ~50.01%
        return WILTED
    if healthPercent < 1.0:                         // (50.01%, 100%)
        return WILTING
    // healthPercent == 1.0, determine by mastery
    if masteryPercent < 0.33:                       // [0%, 33%)
        return SEED
    if masteryPercent < 0.66:                       // [33%, 66%)
        return SPROUT
    return BLOOM                                    // [66%, 100%]
```

### Important behavioral details

1. **The LOCKED state is never produced by FlowerCalculator.** It is a UI-only concept set by the roadmap/lesson ladder when prerequisites are not met. The calculator only produces SEED through GONE.

2. **Null mastery produces SEED, not LOCKED.** Even unstarted lessons show a seed icon, indicating readiness.

3. **The epsilon comparison (0.01)** in the WILTED check accounts for the mathematical guarantee from `calculateHealthPercent` that health >= WILTED_THRESHOLD. Without the epsilon, floating-point imprecision could prevent the WILTED state from ever triggering.

4. **Health takes priority over mastery.** When health is below 1.0, the state is WILTING or WILTED regardless of mastery level. A lesson with 100% mastery but overdue review shows a wilting flower, not a bloom.

5. **The mastery thresholds (33% / 66%)** map to 50 and 99 unique card shows respectively (out of 150 MASTERY_THRESHOLD).

### Emoji mapping

| State | Emoji | Unicode |
|---|---|---|
| LOCKED | lock | U+1F512 |
| SEED | seedling | U+1F331 |
| SPROUT | herb | U+1F33F |
| BLOOM | cherry blossom | U+1F338 |
| WILTING | wilted flower | U+1F940 |
| WILTED | fallen leaf | U+1F342 |
| GONE | black circle | U+26AB |

### Edge cases

| Condition | Result |
|---|---|
| `mastery = null` | SEED, scale=0.5, health=1.0 |
| `uniqueCardShows = 0` | SEED, scale=0.5, health=1.0 |
| `lastShowDateMs = 0` | `daysSince = 0`, full health |
| `daysSince > 90` | GONE, all metrics zeroed, scale=0.5 |
| `uniqueCardShows > 150` | `masteryPercent` clamped to 1.0 |
| `healthPercent` exactly at `WILTED_THRESHOLD` | WILTED (due to `<=` with epsilon) |
| `healthPercent = 1.0, masteryPercent = 0.0` | SEED (e.g., just completed first card today) |

---

## 3.3 MixedReviewScheduler

**Source file:** `app/src/main/java/com/alexpo/grammermate/data/MixedReviewScheduler.kt`
**Test file:** `app/src/test/java/com/alexpo/grammermate/data/MixedReviewSchedulerTest.kt`

### Purpose

Generates a schedule of sub-lessons for a sequence of lessons, mixing new content cards with spaced-review cards from previously studied lessons. The scheduler determines when review cards from earlier lessons appear in later lessons, based on the interval ladder.

### Input

| Parameter | Type | Constraints |
|---|---|---|
| `subLessonSize` | `Int` (constructor) | Number of cards per sub-lesson (typically 10) |
| `intervals` | `List<Int>` (constructor) | Default = `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` |
| `lessons` | `List<Lesson>` | Ordered list of lessons; order determines review scheduling |

`Lesson` data class relevant fields:

| Field | Type | Semantics |
|---|---|---|
| `id` | `String` | Unique lesson identifier |
| `cards` | `List<SentenceCard>` | All cards in the lesson |
| `mainPoolCards` | `List<SentenceCard>` | First 150 cards (computed getter) |
| `reservePoolCards` | `List<SentenceCard>` | Cards beyond first 150 (computed getter) |
| `allCards` | `List<SentenceCard>` | Same as `cards` (computed getter) |

### Output

`Map<String, LessonSchedule>` -- maps lesson ID to its schedule.

`LessonSchedule`:
| Field | Type |
|---|---|
| `lessonId` | `String` |
| `subLessons` | `List<ScheduledSubLesson>` |

`ScheduledSubLesson`:
| Field | Type |
|---|---|
| `type` | `SubLessonType` (`NEW_ONLY` or `MIXED`) |
| `cards` | `List<SentenceCard>` |

### Constants

| Constant | Value | Semantics |
|---|---|---|
| `MAX_REVIEW_CARDS` | 300 | Maximum total review cards drawn from a lesson's combined pools |

### Sub-lesson types

- **`NEW_ONLY`**: Contains only new (unseen) cards from the current lesson. Used for the first lesson (no prior lessons to review) and for the first half of cards in subsequent lessons.
- **`MIXED`**: Contains a mix of current lesson cards and review cards from previously studied lessons. The split is approximately half current, half review.

### Algorithm

```
build(lessons):
    if lessons is empty: return empty map

    schedules = {}
    reviewQueues = {}        // lessonId -> card queue for main pool review
    reserveQueues = {}       // lessonId -> card queue for reserve pool review
    reviewStartMixedIndex = {}  // lessonId -> global mixed index when review starts
    lessonIndexById = map of lesson.id -> position in lessons list
    globalMixedIndex = 0

    for each lesson (lessonIndex, lesson):
        // Record when previous lesson's cards become eligible for review
        if lessonIndex > 0:
            previousId = lessons[lessonIndex - 1].id
            reviewStartMixedIndex.putIfAbsent(previousId, globalMixedIndex)

        // Prepare review cards from this lesson for future lessons
        allReviewCards = shuffle(lesson.mainPoolCards + lesson.reservePoolCards)
                            .take(MAX_REVIEW_CARDS)
        mainCount = max(allReviewCards.size / 2,
                        min(lesson.mainPoolCards.size, 150))
        reviewQueues[lesson.id] = first mainCount cards of allReviewCards
        reserveQueues[lesson.id] = remaining cards of allReviewCards

        // Split current lesson cards for new-only vs mixed sub-lessons
        currentCards = lesson.allCards
        allowMixed = (lessonIndex > 0)  // first lesson is always NEW_ONLY
        reviewSlots = subLessonSize / 2         // slots for review cards
        currentSlotsInMixed = subLessonSize - reviewSlots  // slots for new cards

        if allowMixed:
            newOnlyTarget = ceil(totalCards / 2)
        else:
            newOnlyTarget = totalCards  // all cards go to NEW_ONLY

        newOnlyQueue = first newOnlyTarget cards of currentCards
        mixedCurrentQueue = remaining cards of currentCards
        newOnlyCount = ceil(newOnlyQueue.size / subLessonSize)

        subLessons = []

        // Build MIXED sub-lessons
        mixedSubLessons = []
        if allowMixed:
            while mixedCurrentQueue is not empty:
                globalMixedIndex += 1

                // Take current lesson cards for this mixed sub-lesson
                currentHalf = takeUpTo(mixedCurrentQueue, currentSlotsInMixed)
                reviewSlotsNeeded = subLessonSize - currentHalf.size

                // Find lessons due for review at this global index
                dueLessons = dueLessonIds(reviewStartMixedIndex,
                                          lessonIndexById,
                                          globalMixedIndex).take(2)

                // Fill review slots from due lessons
                reviewCards = fillReviewSlots(dueLessons, reviewQueues,
                                              reserveQueues,
                                              reviewSlotsNeeded,
                                              mixedCurrentQueue)
                mixedCards = currentHalf + reviewCards
                if mixedCards is not empty:
                    mixedSubLessons.add(ScheduledSubLesson(MIXED, mixedCards))

        // Build NEW_ONLY sub-lessons
        repeat newOnlyCount times:
            cards = takeUpTo(newOnlyQueue, subLessonSize)
            if cards is not empty:
                subLessons.add(ScheduledSubLesson(NEW_ONLY, cards))

        subLessons.addAll(mixedSubLessons)
        schedules[lesson.id] = LessonSchedule(lesson.id, subLessons)

    return schedules
```

### Review due-date determination

```
dueLessonIds(reviewStartMixedIndex, lessonIndexById, globalMixedIndex):
    due = []
    for each (lessonId, startIndex) in reviewStartMixedIndex:
        step = globalMixedIndex - startIndex
        if step is in intervals list [1, 2, 4, 7, 10, 14, 20, 28, 42, 56]:
            due.add(lessonId)
    return due sorted by lesson index descending (most recent first)
```

A lesson's review cards become "due" when the difference between the current global mixed index and the lesson's start index matches one of the interval ladder values. This means review happens at spaced intervals: after 1 mixed sub-lesson, then 2, then 4, then 7, etc.

### Review card filling priority

```
fillReviewSlots(dueLessons, reviewQueues, reserveQueues, slots, fallbackQueue):

    // PRIORITY 1: Reserve pool cards from due lessons
    // Round-robin across available reserve pools to ensure variety
    while result.size < slots AND reserve pools available:
        take cards round-robin from reserve queues of due lessons

    // PRIORITY 2: Main pool cards from due lessons
    // Round-robin across available main pools
    while result.size < slots AND main pools available:
        take cards round-robin from review queues of due lessons

    // PRIORITY 3: Fallback -- current lesson's mixed queue
    if result.size < slots:
        take remaining cards from the fallback (mixedCurrentQueue)
```

Reserve pool cards are preferred for review because they were not part of the original mastery measurement, preventing learners from simply memorizing specific phrases.

### Business rules

1. **The first lesson (lessonIndex == 0) is always NEW_ONLY** -- there are no prior lessons to review from.
2. **Cards are split 50/50 between NEW_ONLY and MIXED** for lessons beyond the first. The first half of cards goes to NEW_ONLY sub-lessons; the second half to MIXED.
3. **Review cards come from up to 2 due lessons** per mixed sub-lesson, ensuring review variety.
4. **Maximum 300 review cards per lesson** -- prevents excessive review card accumulation.
5. **Sub-lesson ordering**: NEW_ONLY sub-lessons come first, then MIXED. Learners encounter new content before mixed review.
6. **Card shuffling**: Review card pools are shuffled once at preparation time, ensuring random but consistent order within a session.

### Edge cases

| Condition | Behavior |
|---|---|
| Empty lessons list | Returns empty map |
| Single lesson | All sub-lessons are NEW_ONLY; no review possible |
| Lesson with fewer cards than `subLessonSize` | One sub-lesson with all available cards |
| `mixedCurrentQueue` exhausted before filling slots | Fallback uses mixed current queue cards, or fewer cards in the sub-lesson |
| All review queues exhausted | Sub-lesson may have fewer than `subLessonSize` cards |
| `lessonIndex` out of interval ladder range | `dueLessonIds` returns empty list; no review cards inserted |
| Reserve pool empty for a lesson | Falls through to main pool, then fallback |

---

## 3.4 LessonLadderCalculator

**Source file:** `app/src/main/java/com/alexpo/grammermate/data/LessonLadderCalculator.kt`
**Test file:** `app/src/test/java/com/alexpo/grammermate/data/LessonLadderCalculatorTest.kt`

### Purpose

Calculates metrics about where a lesson sits on the spaced repetition ladder: how many days since last review, whether the lesson is overdue, and a human-readable label showing the current interval range. Used to display ladder status in the UI.

### Input

| Parameter | Type | Constraints |
|---|---|---|
| `mastery` | `LessonMasteryState?` | `null` = no progress data |
| `nowMs` | `Long` | Current time in epoch milliseconds |
| `ladder` | `List<Int>` | Default = `SpacedRepetitionConfig.INTERVAL_LADDER_DAYS` |

### Output

`LessonLadderMetrics` data class:

| Field | Type | Semantics |
|---|---|---|
| `uniqueCardShows` | `Int?` | Number of unique cards shown; `null` if no data |
| `daysSinceLastShow` | `Int?` | Days since last review (+1 offset); `null` if no data |
| `intervalLabel` | `String?` | Human-readable interval status; `null` if no data |

### Algorithm

```
calculate(mastery, nowMs, ladder):
    // No data case
    if mastery == null OR uniqueCardShows <= 0 OR lastShowDateMs <= 0:
        return LessonLadderMetrics(null, null, null)

    // Days since last show (with +1 offset so day-of counts as day 1)
    daysSince = floor((nowMs - lastShowDateMs) / DAY_MS) + 1

    // Expected interval for current ladder step
    expectedInterval = ladder[mastery.intervalStepIndex]
        ?? ladder.lastOrNull()

    // Overdue case
    if expectedInterval != null AND daysSince > expectedInterval:
        overdue = daysSince - expectedInterval
        return LessonLadderMetrics(uniqueCardShows, daysSince, "Overdue+$overdue")
        // (Label is in Russian: "Просрочка+$overdue")

    // On-schedule case
    label = buildIntervalLabel(daysSince, ladder)
    return LessonLadderMetrics(uniqueCardShows, daysSince, label)
```

```
buildIntervalLabel(daysSince, ladder):
    if ladder is empty: return "-"
    if ladder has 1 element: return "X-X" (same value)

    // Within first interval
    if daysSince <= ladder[0]:
        return "ladder[0]-ladder[1]"

    // Find which interval range we are in
    for index in 1 until ladder.size:
        if daysSince <= ladder[index]:
            return "ladder[index-1]-ladder[index]"

    // Past the last interval
    return "ladder[last-1]-ladder[last]"
```

### Interval label examples

Using the default ladder `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]`:

| `daysSince` | Label | Meaning |
|---|---|---|
| 1 | "1-2" | Within first interval |
| 2 | "2-4" | Within second interval |
| 3 | "2-4" | Within second interval |
| 5 | "4-7" | Within third interval |
| 10 | "10-14" | Within fifth interval |
| 56 | "42-56" | Within last interval |
| 57 | "42-56" | Past last interval (but not overdue yet) |
| 60 (overdue at step 9) | "Overdue+4" | Past expected 56-day interval by 4 days |

### Edge cases

| Condition | Result |
|---|---|
| `mastery = null` | All metrics `null` |
| `uniqueCardShows = 0` | All metrics `null` |
| `lastShowDateMs = 0` | All metrics `null` |
| `intervalStepIndex` out of ladder range | Uses last ladder value |
| `nowMs < lastShowDateMs` (clock skew) | `daysSince` clamped to 0, then +1 = 1 |
| Empty ladder | Label = "-" |

### Important detail: +1 day offset

`LessonLadderCalculator` adds 1 to the computed `daysSince`. This means:
- A lesson reviewed today reports `daysSince = 1`, not `0`.
- A lesson reviewed yesterday reports `daysSince = 2`.
- This differs from `SpacedRepetitionConfig.calculateHealthPercent` which uses raw days (0 = today).

This offset ensures the label always shows at least "1-2" for recently reviewed lessons, making the UI more informative.

---

## 3.5 Normalizer

**Source file:** `app/src/main/java/com/alexpo/grammermate/data/Normalization.kt`
**Test file:** `app/src/test/java/com/alexpo/grammermate/data/NormalizerTest.kt`

### Purpose

Normalizes user input and expected answers for comparison. Removes formatting differences so that minor variations in punctuation, whitespace, casing, and time notation do not cause a correct answer to be marked wrong.

### Input

| Parameter | Type | Semantics |
|---|---|---|
| `input` | `String` | Raw user input or expected answer |

### Output

`String` -- normalized version suitable for exact equality comparison.

### Algorithm

```
normalize(input):
    // Step 1: Trim and collapse whitespace
    trimmed = input.trim().replace(Regex("\\s+"), " ")

    // Step 2: Remove minutes from time expressions
    // Converts "8:30" -> "8", "12:00" -> "12"
    timeFixed = trimmed.replace(Regex("\\b(\\d{1,2}):\\d{2}\\b"), "$1")

    // Step 3: Lowercase
    lower = timeFixed.lowercase()

    // Step 4: Remove all punctuation EXCEPT hyphens
    builder = StringBuilder()
    for each character ch in lower:
        if ch is in ['.', ',', '?', '!', ':', ';', '"', '<', '>', '(', ')', '[', ']', '{', '}']:
            skip  // remove punctuation
        else:
            append ch  // keep hyphens, letters, digits, spaces

    // Step 5: Final whitespace cleanup
    return builder.toString().replace(Regex("\\s+"), " ").trim()
```

### Normalization rules in detail

| Rule | Input example | Output | Rationale |
|---|---|---|---|
| Trim whitespace | `"  hello  "` | `"hello"` | Users may accidentally add spaces |
| Collapse multiple spaces | `"a  b   c"` | `"a b c"` | Typing extra spaces should not fail |
| Remove time minutes | `"8:30"` | `"8"` | Language answers may express time differently |
| Remove time minutes | `"12:00"` | `"12"` | Same for 24-hour format |
| Lowercase | `"HELLO"` | `"hello"` | Case should not matter in language learning |
| Remove periods | `"hello."` | `"hello"` | Sentence-final period is not meaningful |
| Remove commas | `"a, b"` | `"a b"` | Pause markers in language answers |
| Remove question marks | `"what?"` | `"what"` | Question punctuation varies by context |
| Remove exclamation marks | `"yes!"` | `"yes"` | Emotion markers not relevant to correctness |
| Remove colons | `"8:30"` already handled in step 2 | `"8"` | Double-handled: step 2 removes minutes, step 4 removes colon |
| Remove semicolons | `"a;b"` | `"ab"` | Separator not meaningful in answers |
| Remove quotes | `'"hello"'` | `"hello"` | Quotation marks are formatting |
| Remove angle brackets | `"<test>"` | `"test"` | Rare but possible in formatted content |
| Remove parentheses | `"(hello)"` | `"hello"` | Parenthetical content is part of answer |
| Remove brackets | `"[hello]"` | `"hello"` | Same for square brackets |
| Remove braces | `"{hello}"` | `"hello"` | Same for curly braces |
| **Keep hyphens** | `"well-known"` | `"well-known"` | Hyphenated words are linguistically meaningful |
| Keep digits | `"42"` | `"42"` | Numbers in answers |
| Keep letters | `"abc"` | `"abc"` | Core content |

### What is NOT normalized

The normalizer does NOT handle:

1. **Accent marks / diacritics** -- accented characters (e.g., `e` vs `e` in Italian/French) are treated as distinct. "pera" and "pera" would be different strings. This is intentional: correct accent usage is part of language mastery.
2. **Apostrophes** -- not in the punctuation removal list. `"l'albero"` keeps the apostrophe.
3. **Special Unicode characters** -- em-dash, en-dash, etc. are NOT removed (only `-` hyphen-minus is explicitly kept).
4. **Synonyms / alternative answers** -- the normalizer only produces one canonical form. The caller must compare against all accepted answers separately.
5. **Typographical variants** -- smart quotes, non-breaking spaces, etc. are not normalized.

### Business rules

1. **Hyphens are preserved** because compound words and prefixes (e.g., "well-known", "re-do") are linguistically significant in English and other target languages.
2. **Time normalization removes minutes** because language exercises about time typically accept just the hour ("eight" vs "eight thirty" may both be correct in context).
3. **Two-pass whitespace cleanup** ensures that removing punctuation does not leave double spaces (e.g., `"a, b"` -> `"a  b"` -> `"a b"`).

### Edge cases

| Input | Normalized output | Notes |
|---|---|---|
| `""` | `""` | Empty stays empty |
| `"   "` | `""` | Whitespace-only becomes empty |
| `"Hello, World!"` | `"hello world"` | All punctuation removed, lowercased |
| `"8:30"` | `"8"` | Time normalized |
| `"well-known"` | `"well-known"` | Hyphen preserved |
| `"l'albero"` | `"l'albero"` | Apostrophe preserved |
| `"(test) [hello] {world}"` | `"test hello world"` | All brackets removed |
| `"A  B   C"` | `"a b c"` | Whitespace collapsed |
| `"12:00 AM"` | `"12 am"` | Time normalized, lowercase |

---

## 3.6 Cross-Module Interactions

This section describes how the algorithm modules interact with each other and with the rest of the application.

### Data flow

```
User completes a card (VOICE/KEYBOARD mode)
    |
    v
MasteryStore records: uniqueCardShows++, shownCardIds.add(cardId),
                      lastShowDateMs = now(), totalCardShows++
    |
    v
SpacedRepetitionConfig.nextIntervalStep() advances the ladder step
    |
    v
On UI render:
    FlowerCalculator.calculate(mastery) calls:
        SpacedRepetitionConfig.calculateHealthPercent() -> health
        Determines FlowerState from mastery + health
    LessonLadderCalculator.calculate(mastery) -> interval label
    |
    v
MixedReviewScheduler.build(lessons) uses:
    Interval ladder to determine review due dates
    Card pools (main/reserve) to compose sub-lessons
```

### Answer validation flow

```
User types answer
    |
    v
Normalizer.normalize(userInput) -> normalizedUser
For each acceptedAnswer in card.acceptedAnswers:
    Normalizer.normalize(acceptedAnswer) -> normalizedExpected
    if normalizedUser == normalizedExpected:
        CORRECT
If no match:
    INCORRECT
```

### Dependency graph

```
SpacedRepetitionConfig  (no dependencies -- pure math)
    ^
    |--- FlowerCalculator (depends on calculateHealthPercent, constants)
    |--- LessonLadderCalculator (depends on INTERVAL_LADDER_DAYS)
    |--- MixedReviewScheduler (depends on interval ladder values)

Normalizer (no dependencies -- pure string transform)
```

### Key invariants

1. **WORD_BANK mode never writes to MasteryStore.** Only VOICE and KEYBOARD modes trigger `uniqueCardShows++`. This is enforced at the ViewModel layer, not in these calculator modules.

2. **Mastery is measured in unique cards, not repetitions.** The `shownCardIds` set ensures that practicing the same card multiple times only counts once toward mastery.

3. **The interval ladder is the single source of truth** for all timing decisions. Both `MixedReviewScheduler` and `SpacedRepetitionConfig` use the same ladder `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]`.

4. **FlowerCalculator is a pure function.** It reads current time (`System.currentTimeMillis()`) internally, but has no side effects. It can be called repeatedly with the same mastery data to get the current visual state.

5. **Normalizer is stateless and deterministic.** The same input always produces the same output, regardless of app state.
