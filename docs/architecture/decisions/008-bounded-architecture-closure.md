# 008. Bounded architecture closure for the shipping app

- **Status:** Accepted
- **Date:** 2026-09-29
- **Baseline:** `c18bbacbd`
- **Supersedes:** No dependency rule in ADR-007; narrows the finish line for the user's architecture-hardening goal
- **Decision-maker:** architecture review using `autonomous-decision`
- **Amended by:** [ADR-009](009-decompose-large-owners.md) — the user's large-file/god-object scope correction adds G6; its exclusion and five-gate stop rule below are historical.

## Context

The user wants modularity, isolation, clean layers and correct business logic, with a finish line that cannot expand indefinitely. The existing [Atomic Design plan](../../plans/2026-09-26-atomic-design-migration.md) leaves 24 P5 UI dispatch items and P6 consolidation open. Treating every remaining visual surface as mandatory for business architecture would conflate two goals. `settings.gradle.kts` has one shipping `:app` module. Current `data/` and business `feature/` source imports no `ui/`; feature orchestrators are not wholly platform-free, and `TrainingStateAccess` is defined under `feature/daily` even though training, progress, boss, vocabulary and shared services depend on it. ADR-007 already selected feature-owned UI and dependency direction over a global atomic hierarchy.

The latest source-cleanup commit built and passed 551 unit tests, but [the inventory](../../atomic-design/P0-baseline-inventory.md) documents placeholder UI suites, untested runtime paths and known reachable defects. Passing counts and package names alone are insufficient evidence of business correctness.

## Decision drivers

Weights come from the user's four stated outcomes and the repository's single-runtime, click-path regression requirements in `CLAUDE.md` and ADR-007. Scores are estimates from 1 to 5, not measured quality.

| Driver | Weight | Reason |
|---|---:|---|
| Correctness of named learning rules | 35% | Pack/progress, answer, SRS and session decisions affect user data |
| Enforceable layer and ownership boundaries | 25% | Imports, duplicate state and effects need inspectable owners |
| Finite closure | 20% | A moving P5 backlog must not define the finish line |
| Regression safety | 15% | Changes must preserve real UI clicks and persisted outcomes |
| Implementation economy | 5% | Avoid a parallel rewrite or new module without evidence of need |

## Considered options

### A — Conservative guard and documentation only

Record dependency arrows and current owners, add a small import check, run existing tests, then stop. This is cheap and reversible but would leave placeholder tests and known reachable defects outside the claim of business correctness.

### B — Exhaustive structural migration

Migrate all P5 surfaces, split Gradle modules and large ViewModels, and make all feature code Android-free before closure. This offers stronger compile-time isolation but imposes large changes on a working single runtime; file placement and purity targets could keep moving while user-visible defects remain.

### C — Fixed architecture and behavior contract

Keep one `:app` module and ADR-007's feature-owned presentation. Freeze a small matrix of dependency rules, state/effect owners, five business scenarios and five known-defect dispositions; implement a guard and real behavior tests, then perform one final route/build/test gate. Leave visual migration and disabled routes as separately named work. This has more validation cost than A but gives a falsifiable stop rule without B's rewrite.

## MCDA matrix

| Option | Correctness (35%) | Boundaries (25%) | Finite (20%) | Safety (15%) | Economy (5%) | Weighted score / 5 |
|---|---:|---:|---:|---:|---:|---:|
| A | 2 | 3 | 5 | 3 | 5 | 3.15 |
| B | 4 | 5 | 2 | 2 | 1 | 3.40 |
| C | 5 | 4 | 4 | 4 | 3 | 4.30 |

C leads because it directly tests the user's correctness requirement while making completion finite. B's stronger package isolation does not compensate for its change surface and weak reversibility; A is too weak to support a correctness claim.

## Pre-mortem

Assume C failed six months after implementation:

1. **The dependency guard passed while side effects still crossed boundaries.** Mitigation: G2 records state/effect owners and caller paths; the guard is never used as sole proof.
2. **The test count grew through placeholder harnesses.** Mitigation: G3 names production UI actions and persisted outcomes; inspect assertions, not suite totals.
3. **“Triage” became permanent deferral of live defects.** Mitigation: G4 freezes reachable defects with required fixes; only the disabled Daily path has an explicit conditional deferral.
4. **Unreachable routes were reported as green.** Mitigation: G5 uses distinct `PASS`, `FAIL`, `UNTESTED`, `BLOCKED_BY_DESIGN` and `UNREACHABLE_LEGACY` statuses.
5. **The acceptance matrix expanded with each review.** Mitigation: add only a failure of B1–B5/G1–G5 or an introduced regression causing a crash, saved-progress loss/corruption, or blocking a named reachable route; unrelated pre-existing discoveries enter a separate backlog.

## Red Team challenge

The strongest objection is that words such as “meaningful tests”, “high impact” and “route coverage” are elastic. A static import scan cannot prove lifecycle behavior, and placeholder tests can be green while the app is broken. The challenge is accepted: the [closure contract](../../plans/2026-09-29-architecture-closure-contract.md) freezes scenario IDs, required assertions, known defects and route status meanings before implementation. A missing runtime gate remains open; it is not inferred from an earlier revision's run. The contract distinguishes disabled Daily behavior from a working user route.

## Decision

Choose **C**. Finish this architecture refactoring when gates G1–G5 in the closure contract pass on the same final revision. Keep one Gradle module, enforce package direction, make authoritative state/effects explicit, test selected learning transitions through real boundaries, resolve the frozen reachable defects, and record residual risks. Stop then, even if P5 visual surfaces, file-size targets or cosmetic debt remain. A later visual redesign or broader domain split requires its own objective and acceptance criteria.

## Consequences

The work now has a finite, reviewable definition of done and can improve both isolation and business confidence. It also accepts that package checks are weaker than physical Gradle modules and that a bounded set of scenarios cannot prove every possible app behavior. Android-backed persistence and orchestrators remain where they are unless a named gate demonstrates a need to move them. The existing Atomic Design backlog stays visible but no longer silently expands this goal.

## Decision journal

- **Date:** 2026-09-29.
- **Decision:** Close the current refactor through G1–G5, not full P5 migration or a global purity rewrite.
- **Expected at closure:** zero unlisted forbidden imports, one documented authoritative source for each selected state/effect, named scenario assertions passing, and no unresolved reachable G4 defect.
- **Review triggers:** after the dependency guard lands, after any new cross-feature state owner appears, and at the final G5 evidence run. Reconsider the boundary if a reproducible critical regression falls outside the frozen matrix.
