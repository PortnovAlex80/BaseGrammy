# Product Roadmap & Feature Decisions

Last updated: 2026-05-13
Status: Discussion → Ready for implementation

---

## Agreed Features (Next Sprint)

### 1. Card Feel Rating (Telemetry Collection)

**Status:** Design complete, ready to implement

**Not "grade yourself" — "help the app understand how this card felt."**

After answer is revealed:
```
Io sono un buon amico.

Как ощущалось?
[Не смог] [Сложно] [Нормально] [Легко]
```

If user doesn't press anything → move on, default = null (not "Нормально"). No rating is also valid data.

**Rules:**
- Optional — auto-dismiss after 3 seconds, no default
- Does NOT affect SRS, mastery, intervals, or flower progress
- Pure telemetry: collect clean ASR-independent signal
- NOT for smart adaptation now — just accumulate statistics

#### Telemetry event structure

```kotlin
data class CardPracticeEvent(
    val cardId: String,
    val lessonId: String,
    val tense: String?,
    val rank: Int?,
    val inputMode: String,           // VOICE / KEYBOARD / WORD_BANK

    val difficultyMode: String,        // FULL_HINTS / REDUCED_HINTS / NO_HINTS
    val userRating: String?,           // CANT / HARD / NORMAL / EASY / null

    val promptShown: String,
    val acceptedAnswer: String,
    val asrText: String?,              // raw ASR output (if voice)
    val asrMatched: Boolean?,          // did ASR match accepted answer?

    val shownAtMs: Long,
    val answeredAtMs: Long?,
    val usedShowAnswer: Boolean,       // did user press "show answer"?
    val attemptsCount: Int             // how many attempts before correct/give up
)
```

#### What we can analyze from this data (future)

Send batch to AI model and ask for patterns:
- Which tenses are consistently harder (userRating = HARD/CANT)
- Which rank ranges slow down learners (latency + rating correlation)
- Which hints help most (difficultyMode vs rating correlation)
- Which cards are systematically rated "Hard" → corpus problem
- Which grammar patterns are the user's weak spots → personalization
- Where ASR mismatch rate is high but userRating = EASY → ASR noise
- Where ASR matched but userRating = HARD → pattern difficulty, not recognition issue

**Value:** Ground truth data. User knows better than ASR whether they knew the answer or guessed. This is the foundation for ALL future adaptive features.

---

### 2. Three Difficulty Levels (Progressive Hint Hiding)

**Status:** Design complete, ready to implement

**Definition:** "Hints" in this app refer exclusively to **parenthetical target-language insertions** embedded in the Russian prompt text. For example, in `я говорю (dire) правду (verità)`, the fragments `(dire)` and `(verità)` are hints. Nothing else is a "hint" — Word Bank, tense labels, verb info chips, and the "Show Answer" eye button are separate UI features, not hints.

Same cards, same SRS, same mastery. Only the visibility of parenthetical hints changes.

| Level | Parenthetical hints | Input mode | Use case |
|-------|---------------------|------------|----------|
| EASY | Visible in card body — `promptRu` displayed with parenthetical content intact | Voice/Keyboard/Word Bank | Beginner, learning new patterns |
| MEDIUM | Hidden — parenthetical content stripped from both header AND card body | Voice/Keyboard | Practicing recall |
| HARD | Hidden (same as MEDIUM) — parenthetical content stripped | Voice only | Testing automatic production |

**Key principle:** difficulty level does NOT change what's measured. Only changes support level. A correct answer on HARD counts the same as EASY for mastery/SRS.

**Implementation:** `HintLevel` enum stored in AppConfigStore. The enum controls whether `promptRu` is displayed with or without parenthetical content. Thread through TrainingCardSession, VerbDrillScreen, DailyPracticeScreen, VocabDrillScreen.

**Boss Battle = HARD mode:** Boss battle always runs without hints (voice only, no word bank, no verb info). This is the same as HARD difficulty — the lesson final exam is "show what you know without support." No separate boss mechanics needed, just force HintLevel.HARD for the boss session.

**Boss Battle design (v2):**
- 30 sentences from the lesson — no hints, voice only
- + 30 NEW sentences NOT from the lesson (reserve pool) — same grammar patterns, different vocabulary
- Purpose: test PATTERN TRANSFER, not phrase memorization. Can the student apply the grammar to unseen sentences?
- If they pass reserve sentences → the pattern is truly automatized, not just memorized

**Progressive hint removal for review cards:**
- When spaced repetition brings back cards from earlier lessons (review), remove hints automatically
- Logic: if the student is on lesson L5 and gets a review card from L1, that pattern should already be automatized — no word bank, no verb info
- Implementation: compare card's lesson index with current lesson progress. Review cards from completed lessons → force HintLevel.HARD (or at least MEDIUM)
- This creates natural progressive difficulty: new cards get full support, old cards demand clean production

**Intra-lesson progressive hints (per-card repetition):**
- First lesson: 100 unique sentences, but each sentence appears 3 times with different hint levels:
  - 1st encounter: EASY (full hints)
  - 2nd encounter: MEDIUM (partial hints)
  - 3rd encounter: HARD (no hints)
- Scheduler distributes encounters so that HARD versions cluster toward the end of the lesson
- Result: 300 card interactions per lesson, naturally progressing from supported to unsupported production
- Same principle applies to all lessons: repeat cards with decreasing hints within the lesson itself

---

## Decided: NOT Implementing

### Mix Challenge (Interleaved Practice across lessons)

**Decision:** NOT needed as a separate mode.

**Why:** The lesson tree is already cumulative. Lesson L3 includes Presente + Imperfetto + Passato Prossimo — that's already 3-pattern interleaving. By L5, five patterns mix in one lesson. A separate "Mix Challenge" mode would duplicate what the spiral curriculum already provides.

**Alternative considered:** Contrast Drill (see Future Features below).

---

## Future Features (Not This Sprint)

### 3. Contrast Drill — Paired Similar Sentences

**Concept:** Instead of random interleaving, deliberately place pairs of similar-tense sentences back-to-back to force the brain to notice differences.

**Example:**
```
🇷🇺 Я ел яблоко    → Io mangiavo la mela    (Imperfetto — длительное)
🇷🇺 Я съел яблоко  → Io ho mangiato la mela  (Passato Prossimo — завершённое)
```

**Most confusing pairs in Italian:**
- Imperfetto ↔ Passato Prossimo (both past, different aspect)
- Condizionale ↔ Congiuntivo (both "unreal")
- Passato Remoto ↔ Passato Prossimo (both completed)

**Implementation:** "Contrast Drill" button in roadmap. Takes 5 sentence pairs from lesson, presents as 10 cards with alternating tenses.

**Science:** This is where the +43% retention from Bjork's interleaving research actually applies — not random mixing, but deliberate contrasting of similar patterns.

---

### 4. Speed-Based SRS (Response Latency)

**Concept:** Use time between pressing mic and starting to speak as a proxy for automaticity.

| Latency | Meaning | SRS action |
|---------|---------|-----------|
| < 1 sec | Automatic | Advance interval ×2.5 |
| 1-3 sec | Fluent | Advance interval ×2.0 |
| 3-5 sec | Thinking | Advance interval ×1.2 |
| > 5 sec | Doesn't know | Keep short interval |

**Status:** Infrastructure exists (speedometer in VerbDrillViewModel). Not yet wired to SRS decisions.

**Depends on:** Self-rating data to validate that latency correlates with actual difficulty.

---

### 5. Progressive Hint Removal (Per-Card)

**Concept:** Instead of a global difficulty level, each card independently reduces hints as the user masters it.

| Level | Hints shown |
|-------|------------|
| 0 (new) | Full: infinitive + tense + group + word bank + first word |
| 1 | Partial: infinitive + tense + group |
| 2 | Minimal: infinitive only |
| 3 | Topic: just the grammar topic name |
| 4 | Clean: only Russian prompt |

**Progression:** card advances one level after 2-3 successful productions at current level.

**Status:** Deferred in favor of global 3-level system. May add per-card tracking on top later.

---

### 6. AI-Powered Weak Spot Analysis

**Concept:** Send aggregated self-rating + speed data to an AI model that identifies:
- Which grammar patterns the user consistently struggles with
- Which tense confusions are most common for this user
- Recommended focus areas for next session

**Status:** Data collection first (self-rating). AI analysis is a separate project.

**Depends on:** Self-rating feature collecting enough data for meaningful patterns.

---

## Key Insight from Analysis (2026-05-13)

The app is a **speaking drill trainer**, not a knowledge quiz. The goal is automatization through repetition — "learning to open your mouth." This means:

1. **ASR accuracy is unreliable** — can't use error rate as primary metric
2. **Hints are part of the method** — they guide production, not cheat it
3. **Self-reported difficulty is the cleanest signal** — user knows better than ASR
4. **Speed of speech production** — proxy for automaticity, already partially tracked
5. **The spiral curriculum IS the interleaving** — cumulative lessons mix patterns naturally
