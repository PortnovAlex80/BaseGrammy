# La Casa — Experimental Single-Word Progression Package

## Concept

Take **one word** — the first clear noun in the Italian frequency dictionary (`casa`, position #106) — and trace it through all 16 lessons of the A-level curriculum.

**Goal:** Show how a single lexical item accumulates grammatical complexity lesson by lesson, proving that the "one new concept on familiar vocabulary" methodology works at micro-level.

**Why "casa"?**
- First noun in the frequency dictionary after function words
- Feminine singular (-a → plural -e: case)
- Common, concrete, universally understood
- Works with most grammatical constructions (articles, adjectives, prepositions, verbs)

## Methodology Rules

Each lesson file follows the same CSV format as regular lessons:
```
Russian prompt (Italian hint);Italian answer
```

Rules inherited from main curriculum:
- `тот/та/то` → definite article (il/lo/l'/la)
- `те` → definite plural (i/gli/le)
- bare → indefinite/zero article
- `этот/эта/эти` → predicate demonstrative
- No `(есть)`, `(иметь)`, `(говорить)` markers
- No mechanical question duplicates
- Hints: Italian vocabulary only

## Lesson Map

| # | Topic | What happens to "casa" |
|---|-------|----------------------|
| 01 | Present verbs | Vado a casa, torno a casa, abito in casa |
| 02 | Singular articles | la casa / una casa / la mia casa |
| 03 | Plural nouns | le case / delle case |
| 04 | Adjectives | la casa grande / la casa è piccola |
| 05 | Possessives | la mia casa / la tua casa |
| 06 | Preposizioni articolate | alla casa / nella casa / dalla casa |
| 07 | Formal you (Lei) | Lei va a casa / Lei torna a casa |
| 08 | Essere/Avere states | La casa è grande / Ho una casa |
| 09 | Questo/Quello | questa casa / quella casa |
| 10 | Passato Prossimo (essere) | Sono andato a casa / Sono tornato a casa |
| 11 | Passato Prossimo (avere) | Ho pulito la casa / Ho lasciato la casa |
| 12 | Imperfetto | Andavo a casa / Abitavo in una casa grande |
| 13 | Futuro Semplice | Andrò a casa / La casa sarà grande |
| 14 | Pronomi diretti | La vedo / La pulisco / L'ho comprata |
| 15 | Condizionale | Vorrei una casa / Potrei andare a casa |
| 16 | Imperativo | Vai a casa! / Pulisci la casa! |

## Progression Principle

The word **casa** is the anchor. Each lesson adds exactly ONE grammatical layer on top of it:

```
L01: casa (bare noun with verbs)
  + L02: la casa / una casa (articles)
    + L03: le case (plurals)
      + L04: la casa grande (adjectives)
        + L05: la mia casa (possessives)
          + L06: alla casa (preposizioni articolate)
            + L07: Lei va a casa (formal register)
              + L08: La casa è grande (states/qualities)
                + L09: questa casa (demonstratives)
                  + L10: Sono andato a casa (past: essere)
                    + L11: Ho pulito la casa (past: avere)
                      + L12: Andavo a casa (imperfect)
                        + L13: Andrò a casa (future)
                          + L14: La vedo (object pronouns)
                            + L15: Vorrei una casa (conditional)
                              + L16: Vai a casa! (imperative)
```

This demonstrates that cognitive load per lesson is exactly 1 unit — the vocabulary is constant, only the grammatical frame changes.

## Files

Each file: `casa_XX_AXX.csv` — mirrors the main lesson numbering for easy cross-reference.
