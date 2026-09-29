# 032. Auto-submit only a complete literal keyboard answer

- **Status:** Accepted
- **Date:** 2026-09-30
- **Scope:** Training keyboard input; `Normalizer.isExactMatch` auto-submit predicate

## Context and decision

A production TrainingScreen click regression typed `ONE, one two!` for a card whose accepted answer was `one one two`. Keyboard input arrives in edits. The old auto-submit predicate removed punctuation before comparing each edit, so `ONE, one two` already looked complete before the final `!`. The first grading advanced the deck while the final edit was still arriving; with the same accepted answer on the next card, one typing action credited two cards. The explicit Check path correctly accepts the final normalized answer and should remain distinct from auto-submit.

Weights: transition correctness 40%, ownership 20%, existing exact-answer UX 25%, simplicity 15%. Scores are 1–5.

| Option | Correctness | Ownership | UX | Simplicity | Weighted / 5 |
|---|---:|---:|---:|---:|---:|
| Keep normalized match on every edit | 1 | 3 | 5 | 5 | 3.00 |
| Debounce normalized edits | 4 | 3 | 4 | 2 | 3.50 |
| Require literal complete match for auto-submit | 5 | 5 | 4 | 5 | 4.75 |
| Remove auto-submit entirely | 5 | 4 | 1 | 5 | 3.80 |

Use a case-insensitive, outer-whitespace-trimmed **literal** comparison for the auto-submit predicate. It preserves punctuation and internal spacing so a normalized prefix cannot grade early. Leave `Normalizer.normalize` and `AnswerValidator` unchanged: explicit Check still accepts case, spacing and punctuation variants. Exact answers still auto-submit, including the boss click path. Word bank remains an optional compatibility input mode and does not gain mastery credit.

## Pre-mortem and challenge

Users who type a punctuation or spacing variant now need Check rather than immediate auto-submit. The Check control is already part of the production screen and uses the tolerant validator; the regression exercises that path. A debounce could still fire before a slow final edit and creates a timer/cancellation owner in the UI. A cross-card latch could suppress a legitimate identical answer on the next card. The literal rule avoids both stateful gates and makes the complete-input condition observable at every edit.

The Red Team challenged whether this silently removes all auto-submit behavior. `Normalizer.isExactMatch` has one production caller, the keyboard input bar; its exact-answer call remains true, and the existing boss UI regression is run after the change. Typed variants are covered by the real Check click with exactly one grading and persisted mastery transition.

## Consequences and decision journal

This changes only when the keyboard UI triggers grading; the validator's accepted answers and mastery rules are unchanged. Any future input mechanism that submits on each edit must prove it cannot grade a still-growing answer across a card transition. The B2 click regression keeps adjacent cards with identical accepted answers to detect that failure.
