# Forgetting-Curve Review Scheduling

**Status:** specified 2026-09-10, implemented in the same change.
**Supersedes:** the review half of `MixedReviewScheduler` (positional `globalMixedIndex` model).
**Context:** `docs/architecture/ARCHITECTURE_REVIEW_2026-09-08.md` §"кривая забывания" findings.

---

## 1. Intent

With 63 lessons in a course, cards from **older lessons** are mixed into each newer lesson according to the Ebbinghaus interval ladder. "Elapsed time" is measured on two axes at once — calendar days **and** study effort — because a learner who does five lessons in one evening must still get review, and a learner who vanishes for three months must come back to a review-heavy course.

## 2. What was wrong with the previous model

| Problem | Cause |
|---|---|
| The plan could not react to when the user actually studied | Review cards were **baked into the static schedule** at build time; the schedule is cached by content key (`"${lessonId}:${cards.size}\|${blockSize}"`) and never sees `MasteryStore` |
| A "day" was proportional to lesson size | `globalMixedIndex` counted only MIXED blocks, and their number scales with card count — a 40-card lesson advanced the clock by 4, `lesson_04_A04` (117 cards) by 12 |
| Reviews were silently lost forever | `intervals.contains(step)` is an exact-match event; combined with `.take(2)` sorted **newest-first**, a lesson that missed its exact step never came back |
| Late ladder steps degraded into "more new cards" | `reviewQueues` were drained destructively and never refilled; steps 28/42/56 fell through to `fallbackQueue`, i.e. the *current* lesson's own cards |

The data needed to fix this already exists and is already correct: `ProgressTracker.resolveCardLessonId` attributes a review card to **its own** lesson, so showing lesson 5's card inside lesson 12 already updates lesson 5's mastery. Nothing was reading it.

## 3. Model

### 3.1 Two clocks, combined with `max`

```
calendarDays(L) = (now - lastReviewMs[L]) / 86_400_000
effortDays(L)   = (totalEffortCards - effortAtLastReview[L]) / CARDS_PER_DAY
effectiveDays(L)= max(calendarDays(L), effortDays(L))
overdueRatio(L) = effectiveDays(L) / INTERVAL_LADDER_DAYS[step[L]]
```

`max`, not sum — no double counting. Either the calendar caught up with you, or you caught up by volume.

`totalEffortCards` is the sum of `totalCardShows` over all lessons of the active pack. It needs **no new global counter and no new event hook**: it is computed from the mastery map that is already loaded. Word-bank shows never enter it, because `recordCardShowForMastery` returns early for `WORD_BANK` — consistent with the rule that only self-produced input counts.

**`CARDS_PER_DAY = 60`** — calibrated from the real pack: the median `IT_EXPRESS` lesson is 40–46 cards, which at block size 10 is ~6 blocks ≈ 60 card shows including mixed-in review. So **one lesson ≈ one day**, which is the intended reading.

### 3.2 Due is a ranking, not an event

A lesson is never "missed". Candidates are sorted by `overdueRatio` descending and drawn greedily. A lesson that does not fit into this block stays overdue and ranks higher next time. This removes the exact-match match, the `.take(2)` cap, and the newest-first bias in one move.

If nothing has reached `ratio >= 1`, the closest-to-due lessons are still used, so MIXED blocks keep their full size. Block size must stay stable — `subLessonTotal` and the completion math depend on it.

### 3.3 Review draws a contiguous run

Cards inside a lesson are authored in coherent runs and often in **adjacent contrastive pairs**:

```
Это мужчина (uomo);Questo è un uomo.
Это мужчины (uomini);Questi sono uomini.
Это подарок (regalo);Questo è un regalo.
Это подарки (regali);Questi sono regali.
```

Scattering cards by an "encounter count" heuristic would split these pairs across sessions and destroy the contrast they were written for. A review draw therefore takes **`slots` consecutive cards from a single lesson**, wrapping at the end:

```
runStart = ((step[L] * 7 + activeSubLessonIndex) * slots) mod lessonSize
```

Deterministic — the same block always yields the same cards, so rebuilding the session (navigation, restore) never shuffles what the user is looking at. It rotates across blocks (`activeSubLessonIndex`) and across ladder advances (`step[L]`); `7` is an arbitrary stride chosen to spread successive visits across the lesson. No cursor field, no mutation during card building.

**One lesson per MIXED block.** Splitting 5 slots across two lessons would break runs again; different blocks review different lessons instead. With ~4 mixed blocks per lesson, up to 4 older lessons are touched per lesson studied.

### 3.4 Ladder advances only on self-produced reproduction (option B)

Answer **correctness cannot be used**: voice input goes through Google/offline ASR whose error rate is high, so a failed match usually means the recognizer failed, not that the learner forgot. The only trustworthy signal is binary — *the learner produced the card, or did not*.

Therefore:

- `recordCardShowForPack` records **exposure only** (`uniqueCardShows`, `totalCardShows`, `lastShowDateMs`, `shownCardIds`). It no longer advances the ladder.
- `recordSelfProducedForPack` advances the ladder, at most once per calendar day per lesson, and stamps `lastReviewMs` / `effortAtLastReview`.
- It is called when an answer is **accepted**, the input mode **counts toward mastery** (i.e. not `WORD_BANK`), and the answer was **not revealed** via show-answer (`SessionState.HINT_SHOWN`).

A lesson pushed through entirely with "показать ответ" still accrues exposure, but does not earn a longer interval. This is not demotion — the ladder stays monotonic — it is simply not rewarding what the learner did not reproduce.

`lastReviewMs` is deliberately separate from `lastShowDateMs`: the clock must measure time since the last *review*, not since the last *exposure*, otherwise showing a card would reset the lesson's own due date.

## 4. Storage

Two new fields on `LessonMasteryState`, both defaulted, both optional on read:

| Field | Meaning |
|---|---|
| `lastReviewMs: Long = 0L` | when the lesson last had a self-produced study/review event |
| `effortAtLastReview: Int = 0` | `totalEffortCards` snapshot at that moment |

`lastShowDateMs` is untouched and still drives flower health in `FlowerCalculator` / `PackProgressCalculator`.

A lesson with `lastReviewMs == 0` has never been self-produced and is **excluded** from the review pool.

## 5. Blast radius

**Unchanged:** block layout, block count and size, `NEW_ONLY` blocks, `calculateCompletedSubLessons`, progress percentages, the 150-card completion rule, flower state, chapter progress, pack tiles.

**Changed:**

- `MixedReviewScheduler` — stops baking review cards, emits `reviewSlots: Int` per MIXED block. `globalMixedIndex`, `reviewStartMixedIndex`, `dueLessonIds`, `reviewQueues`, `reserveQueues`, `fillReviewSlots` are removed.
- `ReviewSelector` — new pure object.
- `CardProvider` — fills review slots at session-build time.
- `SpacedRepetitionConfig` — adds `CARDS_PER_DAY`, `effectiveDays`, `overdueRatio`.
- `MasteryStore` — ladder advance moves out of `recordCardShowForPack` into `recordSelfProducedForPack`; persists the two new fields.
- `TrainingViewModel` — passes a mastery provider into `buildSessionCards`; hooks the self-produced signal in `submitAnswer`.

The schedule cache stops being a defect: the plan is now genuinely a function of content, while everything time-dependent is computed per session.

The reserve-pool path (`mainPoolCards` / `reservePoolCards`, dead for every real lesson because all are under 150 cards) is left in place but is no longer referenced by the scheduler. Out of scope by decision.

## 6. Test pins

- `effectiveDays` takes the max of the two axes; effort alone can make a lesson due on the same calendar day.
- 60 card shows ≈ 1 effort day.
- A lesson overdue by ratio 3.0 ranks ahead of one that just reached 1.0.
- Nothing due → the closest-to-due lesson still fills the block; block size is preserved.
- A review draw returns `slots` **consecutive** cards, and the same block rebuilt twice returns the identical draw.
- The draw wraps at the end of the lesson.
- A lesson with `lastReviewMs == 0` is never selected.
- `recordCardShowForPack` does **not** advance `intervalStepIndex`.
- `recordSelfProducedForPack` advances it at most once per calendar day.
- `WORD_BANK` input never triggers `recordSelfProducedForPack`.
- A card completed via show-answer does not advance the ladder.
