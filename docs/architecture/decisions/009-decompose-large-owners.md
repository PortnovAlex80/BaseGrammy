# 009. Decompose large owners as part of architecture closure

The 800-line acceptance threshold in this historical decision is superseded by [ADR-026](026-use-ownership-not-line-count-for-decomposition.md). Its responsibility-based decomposition scope remains in force.

- **Status:** Accepted
- **Date:** 2026-09-29
- **Baseline:** `d8d228d67`
- **Amends:** ADR-008's exclusion of large-file decomposition and its stop rule
- **Decision-maker:** user's explicit scope correction, with architecture review

## Context

ADR-008 made the architecture goal finite but excluded restructuring large files. The user corrected that scope: large files and god objects must become modular too. The shipping `:app` currently has 14 production Kotlin files over 800 physical lines, led by `TrainingViewModel` (2918) and `SessionRunner` (1449). The [earlier architecture review](../ARCHITECTURE_REVIEW_2026-09-08.md) had deferred its <800-line target because a blind split would destabilize callers. The user's correction authorizes staged, responsibility-based decomposition; correctness and dependency direction still constrain it.

## Drivers and options

| Driver | Weight | Why |
|---|---:|---|
| Behavior preservation | 35% | Session, persistence and UI routing have real regression risk |
| Real ownership isolation | 30% | A renamed god object or extension-file split is not modularity |
| Finite completion | 20% | The target must not expand to every file in the repo |
| Implementation economy | 15% | Keep one shipping runtime and reversible slices |

| Option | Safety | Isolation | Finite | Economy | Weighted / 5 |
|---|---:|---:|---:|---:|---:|
| A — Keep ADR-008 exclusion | 5 | 1 | 5 | 5 | 3.80 |
| B — Split every file above a line limit immediately | 2 | 2 | 3 | 2 | 2.20 |
| C — Fixed 14-file, responsibility-based waves | 4 | 5 | 4 | 3 | 4.15 |

A is inconsistent with the user's explicit goal. B optimizes a metric and risks moving business decisions without tests. C names a finite target while requiring real state/effect ownership and preserving behavior in small commits. The line threshold is a guard, not the architectural outcome.

## Pre-mortem and challenge

Assume C failed: files became short through forwarding wrappers; extracted classes gained the same mixed responsibilities; tests passed through fake/no-op UI; or a timer/navigation move changed callback order. Mitigations are respectively a live-caller/owner review, the one-capability rule, real transition/click assertions, and a focused regression check for each move before the full gate. A strong objection is that 800 lines is arbitrary and can reward fragmentation. We accept it only as a frozen inventory trigger and require a named independent responsibility for every split. The previous review's deferred <800 target supplies a repo-local precedent; it does not override the ownership test.

## Decision

Add G6 to the [closure contract](../../plans/2026-09-29-architecture-closure-contract.md). Track the 14 baseline files in the [decomposition workplan](../../plans/2026-09-29-large-file-decomposition.md). Each must become an at-most-800-line cohesive owner, without replacing one god object with several new ones; G1–G5 remain mandatory. Start with SessionRunner's active-time timer because it has private state, a clear callback boundary and a deterministic test surface. The larger ViewModel, data/audio and UI owners follow in bounded waves. No new Gradle module or broad framework rewrite is required by this decision.

## Consequences and decision journal

This adds substantial but finite work and more intermediate files. It also supplies a hard stop: after the fixed 14 files and G1–G5 pass, do not continue moving code for aesthetic symmetry. Review at each wave closure for duplicate state/effect owners; reconsider a boundary if it only forwards calls or produces another large class. At 30/90-day review, expect no tracked file or newly extracted owner to exceed 800 physical lines and all named business tests to remain green. A newly discovered unrelated large file requires a separate scope decision.
