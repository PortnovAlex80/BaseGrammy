# 011. Scope answer de-duplication to the active card, except for voice races

- **Status:** Accepted
- **Date:** 2026-09-29
- **Decision-maker:** architecture review under ADR-009
- **Scope:** `SessionRunner.submitAnswer()` in the shipping runtime

## Context and drivers

`SessionRunner` used to suppress the same answer text for 300 ms regardless of the card. Two adjacent keyboard cards that both accept `alpha` therefore lost the second valid answer. A deterministic runner test reproduced this before the fix. The text-only guard also protected against duplicate callbacks from the two voice launchers, including a callback arriving just after a correct answer advanced the card. Removing cross-card suppression for every input mode would regress that protection.

| Driver | Weight |
|---|---:|
| Correctness of accepted answers and voice races | 40% |
| Ownership and isolation | 25% |
| Bounded change cost | 20% |
| Testability | 15% |

| Option | Correctness | Isolation | Cost | Testability | Weighted / 5 |
|---|---:|---:|---:|---:|---:|
| A — inline mode-aware condition | 4 | 2 | 5 | 3 | 3.55 |
| B — request/card epoch through both voice launchers and offline ASR | 5 | 4 | 1 | 3 | 3.65 |
| C — small `SubmissionDedupGuard` with deck epoch | 4 | 5 | 4 | 5 | 4.40 |

## Decision

Choose C for this defect. `SubmissionDedupGuard` owns the previous text, timestamp, input mode, card identity and deck epoch. Within 300 ms, keyboard/word-bank submissions are suppressed only when they target the same card in the same deck. If either submission is in voice mode, the old cross-card suppression remains, so duplicate speech callbacks do not grade the next card. Every replacement of `sessionCards` advances the deck epoch, including a replacement that reuses a card ID. `SessionRunner` retains the public `submitAnswer()` entrypoint, validation and audit effects, and injects a monotonic clock for deterministic tests.

The guard remains after the same early checks as before: a blank or missing-card submit can still update its last-attempt record. This preserves existing call order in a targeted correctness fix.

## Pre-mortem and challenge

The main regression risks are suppressing a valid repeated answer after deck replacement, allowing a duplicate voice result to grade the next card, or counting a repeated wrong submit twice. Runner tests cover all three, including a repeated card ID, and the previously failing adjacent-keyboard case now passes. A red-team review found that option C cannot identify a stale voice callback whose *text differs* from the previous callback. Preventing that requires source-card/request provenance through both voice launchers and offline ASR; it is a separate, broader integration change and remains open. The review also suggested moving blank/no-card checks ahead of the guard; that would change prior behavior and is deferred to a separately characterized change.

## Consequences and decision journal

The new guard is the sole owner of duplicate-submission state; `SessionRunner` remains the sole owner of card progression. This fixes the demonstrated keyboard loss without splitting the runner into another orchestrator. The 300 ms policy and voice limitation are explicit, testable contracts. Revisit the decision when voice callbacks carry source-card identity end to end, or if mode-switch behavior reveals a valid voice-to-keyboard repeat being suppressed. This slice does not close the 800-line `SessionRunner` target.
