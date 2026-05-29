---
name: validate-lesson
description: Use when given lesson CSV files to validate against the Italian course curriculum and methodology. Triggers on lesson validation, curriculum check, lesson review, or verifying lesson content matches methodology rules.
---

# Validate Lesson — Curriculum & Methodology Check

Validates lesson CSV files against the 63-lesson Italian course curriculum defined in `docs/lesson-methodology/`.

## Activation

User invokes `/validate-lesson` or asks "validate lesson", "check lesson against curriculum", "review lesson content", or provides lesson CSV files for review.

## Input

User provides one or more lesson CSV file paths (e.g., `docs/lesson-methodology/new_lessons/lesson_01_A01.csv`).

If no paths given, scan `docs/lesson-methodology/new_lessons/` for `lesson_*.csv` files and validate all.

## Lesson CSV Format

```
A01 - Presente Indicativo
Russian prompt (verb hints);Italian answer
Russian prompt (verb hints);Italian answer
...
```

- Line 1: Lesson ID + topic title
- Lines 2+: `Russian hint;Italian sentence` (semicolon-separated)
- Verb hints in parentheses guide the student but don't appear in the answer

## Source Documents

| Document | Role |
|----------|------|
| `italian-course-program.csv` | Source of truth for lesson scope, topic, sentence count, restrictions |
| `corpo.txt` | Narrative arc — what grammar is possible at each chapter |
| `methodology-technical-summary.md` | Quick recovery — block structure, layered restrictions, principles |

## Validation Rules

### R1: Lesson Identity Match

Extract lesson ID from line 1 (e.g., `A01`, `B10`, `C05`).

Check `italian-course-program.csv` column **Тема** for the matching code.

Report:
- PASS: Lesson ID found in curriculum, topic matches
- FAIL: Lesson ID not found in curriculum
- WARN: Lesson ID found but topic title diverges from curriculum description

### R2: Sentence Count

Count data lines (excluding header line 1).

Check `italian-course-program.csv` column **Предложений** for the expected range (e.g., `60–70`).

Report:
- PASS: Count within expected range
- WARN: Count below range (may be incomplete lesson)
- FAIL: Count significantly above range (information overload)

### R3: Grammar Scope Compliance

This is the **critical validation**. Read the curriculum row's **Описание** and **Комментарий_по_развитию** columns for the lesson.

Check each Italian sentence against these rules:

#### R3a: Only allowed grammar constructions

| Lesson Block | Allowed | Forbidden |
|-------------|---------|-----------|
| A01 | Presente Indicativo (all 3 conjugations), essere/avere/andare/fare, basic `non` negation, simple questions | Past tenses, future, conditional, Congiuntivo, pronouns (lo/la/gli), compound tenses |
| A02 | Articles (det/indet) | Tenses beyond Presente |
| A03 | Noun gender/number | Constructions beyond A01-A03 scope |
| A04 | Adjective agreement | Constructions beyond A01-A04 scope |
| A05 | Lei forms in **Indicativo ONLY** | Imperative Lei (Congiuntivo forms), no `Parli!/Venga!` |
| A06 | Essere vs Avere for states | Constructions beyond scope |
| A07 | W-questions | Constructions beyond scope |
| A08 | C'è / Ci sono | Constructions beyond scope |
| A09 | Prepositions simple + articulated | Constructions beyond scope |
| A10 | Possessives | Constructions beyond scope |
| A11 | Numbers, time, dates | Constructions beyond scope |
| A12 | Frequency adverbs, `non...mai`, `non...più` | `niente/nessuno/nemmeno/né...né` (those are B21) |
| A13 | Comparatives/superlatives | Constructions beyond scope |
| A14 | Modals + **CLEAN infinitive ONLY** | `devo alzarmi` (B13), `devo farlo` (B16), pronouns with modals |
| A15 | Condizionale di cortesia (vorrei/potrei/dovrei as formulas) | Full Condizionale paradigm (that's B09) |
| A16 | Imperativo tu/voi/noi/Lei | Pronouns with imperativo (dimmi/prendilo) — those are B16 |
| B01–B27 | Per-lesson scope from curriculum | Check each lesson's specific restrictions |
| C01–C20 | Per-lesson scope from curriculum | Check each lesson's specific restrictions |

#### R3b: Hard restrictions from curriculum

For each lesson, read the curriculum's **Комментарий_по_развитию** and extract **ОГРАНИЧЕНИЕ** / **ЖЁСТКОЕ ОГРАНИЧЕНИЕ** entries. Flag any sentence that violates these.

Key hard restrictions:
- A05: Lei forms ONLY in Indicativo — NO imperative Lei
- A12: Only `non...mai` / `non...più` — no `niente/nessuno/nemmeno`
- A14: Modals + clean infinitive ONLY — no reflexive or pronoun infinitives
- A15: Only `vorrei/potrei/dovrei/mi piacerebbe` as formulas — no full paradigm
- A16: Imperative without pronouns — `dimmi/prendilo` are B16
- B13: Reflexives + modals with reflexive infinitive OK, but NOT modals with direct pronouns (`devo farlo` = B16)
- B16: Final assembly of pronouns with modals/gerund/imperative — ONLY here

#### R3c: Progressive scope (cumulative)

Lessons are cumulative — lesson A03 may use grammar from A01-A02 but not from A04+.

Check: sentences in lesson N may use constructions from lessons 1 through N, but NOT from lessons N+1 onward.

### R4: Italian Sentence Correctness

For each Italian sentence, check:
1. **Grammar correctness**: Subject-verb agreement, article-noun agreement, adjective placement
2. **Spelling**: Correct Italian spelling
3. **Capitalization**: Sentence-start caps only (no mid-sentence caps unless proper noun)
4. **Punctuation**: Correct use of `?`, `!`, `.`

Report each error with:
```
Line N: "Italian sentence"
  ERROR: [description of what's wrong]
  FIX: [suggested correction]
```

### R5: Russian Prompt Consistency

For each sentence pair:
1. Russian prompt meaning must match Italian answer
2. Verb hints in parentheses should guide to the correct Italian verb
3. No contradictions between prompt and answer

### R6: Vocabulary Appropriateness

- Vocabulary should be A1-level for Block A, progressively more complex for B and C
- No slang or dialect forms unless the lesson explicitly covers them (C17)
- No archaic forms unless the lesson explicitly covers them (C07/C08)

## Execution

### Step 1: Load Curriculum

Spawn ONE Explore agent to:
1. Read `docs/lesson-methodology/italian-course-program.csv`
2. Read `docs/lesson-methodology/methodology-technical-summary.md`
3. Build a lookup: lesson ID → { topic, expected sentence range, description, restrictions }

### Step 2: Load Lesson File(s)

Read each provided lesson CSV file.

### Step 3: Run Validation

For each lesson file, apply rules R1–R6. Use a general-purpose agent per lesson file for the heavy checking.

### Step 4: Report

Present validation report:

```
LESSON VALIDATION REPORT
========================

File: lesson_01_A01.csv
Lesson ID: A01
Topic: Presente Indicativo

[R1] Identity: PASS — A01 found in curriculum
[R2] Sentence count: 66 (expected 60–70) — PASS
[R3] Grammar scope:
  ✅ All sentences use Presente Indicativo
  ✅ No past/future/conditional constructions found
  ✅ Hard restriction "clean infinitive only" — N/A (no modals in A01)
[R4] Italian correctness:
  ✅ All sentences grammatically correct
[R5] Russian-Italian consistency:
  ✅ All prompt-answer pairs consistent
[R6] Vocabulary:
  ✅ A1-level vocabulary throughout

WARNINGS:
- (none)

ERRORS:
- (none)

VERDICT: PASS
```

For failures:
```
ERRORS:
- Line 42: "Lui va al lavoro" — uses articulated preposition "al" (a+il) which is A09 scope
  → A01 should use simple prepositions or restructure: "Lui va a lavoro" is NOT correct Italian
  → SUGGESTION: Move sentence to A09 lesson, or use "Lui lavora" (intransitive)

VERDICT: FAIL (1 scope violation)
```

## Rules

- This is a READ-ONLY skill. Do NOT modify any files.
- Use Explore agents for curriculum loading, general-purpose agents for validation.
- Max 3 lesson files per validation wave.
- Always cite the specific curriculum row and restriction when flagging a violation.
- If uncertain whether a construction is in scope, flag as WARN (not FAIL).
- Report Italian grammar errors with confidence: high (certain) / medium (likely) / low (possible).
- When a lesson passes all checks, say "VERDICT: PASS" explicitly.
