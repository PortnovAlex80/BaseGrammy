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

### 2. Two-Layer Hint System (Difficulty + Scheduler)

**Status:** Design complete, ready to implement

**Definition:** "Hints" in this app refer exclusively to **parenthetical target-language insertions** embedded in the Russian prompt text. For example, in `я говорю (dire) правду (verità)`, the fragments `(dire)` and `(verità)` are hints. Nothing else is a "hint" — Word Bank, tense labels, verb info chips, POS badges, and the "Show Answer" eye button are separate UI features, not hints.

Same cards, same SRS, same mastery. Only the visibility of parenthetical hints and Word Bank availability change.

The system combines two independent layers:

#### 2.1 Layer 1 — Scheduler (encounter count)

The scheduler determines hint availability based on how many times the user has practiced a specific card. Encounter count is persisted across sessions per card.

| Encounter count | Scheduler fraction | Hints shown |
|-----------------|-------------------|-------------|
| 1st encounter | 1.0 (all) | All parenthetical hints visible |
| 2nd encounter | 0.5 (half) | 50% of parenthetical hints visible |
| 3rd+ encounter | 0.0 (none) | No parenthetical hints visible |

#### 2.2 Layer 2 — User Difficulty Setting

The user chooses a difficulty level from Settings. Stored as `HintLevel` enum in `AppConfigStore` (`config.yaml`).

| Level | User fraction | Parenthetical hints | Word Bank | Keyboard |
|-------|--------------|---------------------|-----------|----------|
| EASY | 1.0 (all) | All visible | Available | Available |
| MEDIUM | 0.5 (half) | 50% visible | Removed | Available |
| HARD | 0.0 (none) | None visible | Removed | Available |

**Key principle:** difficulty level does NOT change what's measured. Only changes support level. A correct answer on HARD counts the same as EASY for mastery/SRS.

#### 2.3 Combination rule

The effective hint level is always the **more restrictive** of the two layers:

```
effectiveFraction = min(schedulerFraction, userFraction)
```

Result mapping:
- `1.0` → `FULL_HINTS` (all parenthetical hints + Word Bank if EASY)
- `0.5` → `REDUCED_HINTS` (50% of parenthetical hints, no Word Bank)
- `0.0` → `NO_HINTS` (no parenthetical hints, no Word Bank)

**Examples:**
- User EASY + 1st encounter = min(1.0, 1.0) = 1.0 → FULL_HINTS
- User MEDIUM + 1st encounter = min(0.5, 1.0) = 0.5 → REDUCED_HINTS
- User EASY + 3rd encounter = min(0.0, 1.0) = 0.0 → NO_HINTS
- User HARD + any encounter = min(0.0, any) = 0.0 → NO_HINTS

#### 2.4 MEDIUM 50% algorithm

When the effective fraction is 0.5, exactly half of parenthetical groups are shown:

```
stripHalfOfParentheticals(text, sessionOffset):
    Find all parenthetical groups in text via regex
    Assign sequential index to each group (0-based)
    sessionOffset = Random.nextInt(0, 2) — generated once per session start
    For each group:
        if (index + sessionOffset) % 2 == 0 → keep the hint
        else → strip the hint (and leading space)
```

The random offset ensures different hints are shown/hidden across sessions, preventing users from learning which hints appear rather than the actual content.

#### 2.5 Boss Battle override

Boss battle **forces HARD** regardless of both layers:
- `effectiveFraction` is set to 0.0 unconditionally
- No parenthetical hints, no Word Bank
- Voice + Keyboard only (keyboard always available at all levels)

This is the lesson final exam: "show what you know without support."

**Boss Battle design (v2):**
- 30 sentences from the lesson — no hints, voice only
- + 30 NEW sentences NOT from the lesson (reserve pool) — same grammar patterns, different vocabulary
- Purpose: test PATTERN TRANSFER, not phrase memorization. Can the student apply the grammar to unseen sentences?
- If they pass reserve sentences → the pattern is truly automatized, not just memorized

#### 2.6 Reference data (NOT hints — always visible)

The following UI elements are **reference information**, not hints. They remain visible at all difficulty levels and encounter counts:

- **Tense labels** (e.g., "Presente", "Passato Prossimo") — always shown
- **Verb info chips** (infinitive, group) — always shown
- **POS badges** (part-of-speech indicators) — always shown
- **"Show Answer" eye button** — always available

#### 2.7 Telemetry integration

`CardPracticeEvent` includes `difficultyMode: String` field with values:
- `"FULL_HINTS"` — effective fraction was 1.0
- `"REDUCED_HINTS"` — effective fraction was 0.5
- `"NO_HINTS"` — effective fraction was 0.0

This allows correlating difficulty level with user self-ratings and answer accuracy.

**Implementation:** `HintLevel` enum stored in `AppConfigStore`. Encounter count tracked per card in session state. Calculation function `calculateEffectiveHints()` in algorithm layer (see 03-algorithms#3.7). Thread through TrainingCardSession, VerbDrillScreen, DailyPracticeScreen, VocabDrillScreen.

**Implementation task:** [TASK-058: Two-Layer Hint System](tasks/TASK-058-two-layer-hint-system.md)

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

### 5. Progressive Hint Removal (Per-Card) — Superseded

**Status:** Superseded by the two-layer hint system (see Section 2 above). The scheduler layer in the two-layer system provides per-card progressive hint removal via encounter count tracking, replacing this older concept. The user difficulty setting layer adds global control on top of it.

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
