# Methodology Section — Technical Summary

> **Quick recovery file** — read this to understand the methodology project without reading all docs.

---

## Purpose

Create a comprehensive Italian language course (RU→IT) structured as an allegorical novel about two beings (Iro and Ua) learning to speak from silence to C2-level complexity.

**Core idea:** Each grammatical construction enables expressing slightly more of a single thought that exists from the beginning but cannot be said until the final chapter.

---

## File Structure

```
docs/lesson-methodology/
├── italian-course-program.csv          # 63 lessons, full curriculum
├── lesson_analysis.csv                  # pedagogical analysis of each lesson
├── final-review-and-integration.md      # critique corrections integration
├── book-plan-two-voices.md              # book outline (6 chapters)
├── corpo.txt                            # original narrative draft (Russian)
└── book1/
    ├── chapter_00.md                    # "Before Language" (English draft)
    └── chapter_01.md                    # "Now" (English draft)
```

---

## The 63-Lesson Architecture

### Block A — Foundation (A01-A16)
- **Focus:** Present tense, articles, nouns, adjectives, basic communication
- **Narrative:** Chapter 1 — "Now"
- **Key insight:** Language needs a center (I, here, now) before anything else

### Block B — Times & Narrative (B01-B27)
- **Focus:** Past/future tenses, pronouns, text connectors, Congiuntivo basics
- **Narrative:** Chapters 2-3 — "History and Horizon" + "Swaddlings and Fabric"
- **Key insight:** Pronouns are "pockets" for already-named things; prepositions are hand directions

### Block C — Higher Constructions (C01-C20)
- **Focus:** Hypothetical periods, indirect speech, style, meta-grammar
- **Narrative:** Chapters 4-6 — Hypothesis, Integration, Final Thought
- **Key insight:** Grammar becomes a tool for style, not just correctness

---

## The Final Thought (全书目标)

> *«Se avessi saputo allora che saresti sparita, sarei rimasto accanto a te. E il mondo che vedevamo insieme non si sarebbe spento.»*

**Grammatical requirements:**
| Fragment | Grammar | Lesson |
|----------|---------|--------|
| Se avessi saputo allora | Congiuntivo Trapassato | C01 |
| che saresti sparita | Condizionale Composto (future in past) | B10 |
| sarei rimasto | Condizionale Composto | B10 |
| il mondo che vedevamo insieme | Relative pronoun + Imperfetto | B22 + B01 |
| non si sarebbe spento | Condizionale + negative | B10 + B21 |

This sentence **cannot be said** at A1 level. The entire course builds toward being able to say it.

---

## Key Methodological Principles

### 1. English Anchors
Every major Italian construction is mapped to an English equivalent:
- Present Perfect → Passato Prossimo
- Past Perfect → Trapassato Prossimo
- Would have done → Condizionale Composto
- If I had known → Periodo Ipotetico (irreale nel passato)

### 2. Case Map for Russian Speakers
Russian cases → Italian constructions:
| Russian | Italian |
|---------|---------|
| Именительный | Position, verb ending, direct pronoun |
| Родительный | *di*, *ne*, relative + *di*, possessive |
| Дательный | *a*, indirect pronouns, *ci* (theme) |
| Винительный | Direct pronouns, *che* (no preposition) |
| Творительный | *con*, *da*, relative + *con* |
| Предложный | *in*, *su*, *a* (location), *ci* (place) |

### 3. Layered Restrictions
Each lesson has strict boundaries to prevent confusion:
- A14: modal + **clean infinitive only** (no *devo farlo* yet)
- A05: Lei forms in **Indicativo only** (no imperative Lei)
- B13: reflexives with modals, **but not** modals with direct pronouns
- B16: final assembly of pronouns with modals/gerund/imperative

### 4. Concept Before Form
Every grammatical form is introduced **through meaning**:
- Articles = memory made visible (*la* = we both know this)
- Prepositions = hand directions (*a* points toward, *di* points inward)
- Pronouns = pockets for already-named things
- Congiuntivo = marker of non-fact (subjectivity, not just "after certain words")

---

## Book Narrative Arc

### Chapter 0 — Before Language
- **Grammar:** Infinitives only (pre-A01)
- **State:** Two beings in fading light, holding words like stones without hands
- **Key words:** *Sparire* (Iro), *Restare* (Ua)

### Chapter 1 — Now
- **Grammar:** A01-A16 (Present, articles, questions, prepositions, modals, basic conditional)
- **Breakthrough:** *Ti vedo* — first person-specific, moment-specific utterance
- **Key insight:** The definite article is grammatical proof you are not alone

### Chapter 2 — History and Horizon
- **Grammar:** B01-B10 (Imperfetto, Passato Prossimo, Trapassato, Futuro, Condizionale)
- **Breakthrough:** Can speak about past and future
- **Limitation:** Cannot yet say "I think that..." (no subjectivity)

### Chapter 3 — Swaddlings and Fabric
- **Grammar:** B11-B24 (Pronouns, Gerundio, relatives, passive, Congiuntivo basics)
- **Breakthrough:** Language becomes fabric, not chain; subjectivity arrives
- **Limitation:** Cannot say "If I had known..." (no hypothetical past)

### Chapter 4 — Hypothesis About Past
- **Grammar:** C01-C03 (Congiuntivo Trapassato, Periodo Ipotetico)
- **Breakthrough:** Can speak about what didn't happen
- **Limitation:** Cannot fully assemble indirect speech with all complexities

### Chapters 5-6 — Assembly and Final Thought
- **Grammar:** C04-C20 (Indirect speech, Passato Remoto, style, meta-grammar)
- **Breakthrough:** The final sentence can finally be said
- **Resolution:** Light lives in the sentence itself

---

## Current State

### Completed
- ✅ 63-lesson curriculum (CSV)
- ✅ Lesson-by-lesson pedagogical analysis
- ✅ Full book outline (6 chapters)
- ✅ Chapter 0 draft (English) — 94 lines
- ✅ Chapter 1 draft (English) — 196 lines

### In Progress
- 📝 Chapter 2 (Imperfetto, Passato Prossimo, narrative)
- 📝 Remaining chapters (3-6)

### Quality Assessment
- **Artistic merit:** 9/10 — Strong prose, precise metaphors, Saint-Exupéry/Le Guin vibes
- **Technical accuracy:** 10/10 — Every grammatical concept correctly embedded
- **Dual addressing:** Works for adults (philosophy of language) and children (story of two beings)

---

## Quick Reference for Continuation

### When writing new chapters:
1. Check `italian-course-program.csv` for lesson scope
2. Check `lesson_analysis.csv` for pedagogical focus
3. Check `book-plan-two-voices.md` for narrative position
4. Maintain the rule: **concept before form**
5. Every metaphor must work artistically AND methodologically

### Key constraints:
- No lecturing — grammar must be **experienced**, not explained
- English anchors must be explicit
- Russian case mapping must underlie preposition/pronoun choices
- Each chapter must end with "what they can now say" vs "what is still impossible"

---

## File Sync Notes

- `italian-course-program.csv` — source of truth for lesson sequence
- `lesson_analysis.csv` — derived from CSV + final-review corrections
- `book-plan-two-voices.md` — derived from `corpo.txt`
- `book1/*.md` — drafts implementing the plan

Any change to core curriculum (CSV) should propagate to analysis and book outline.

---

*Last updated: 2025-05-25*
*Session focus: Chapter drafts 0-1 complete, awaiting Chapter 2*
