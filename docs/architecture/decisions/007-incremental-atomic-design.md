# 007. Incremental Atomic Design with feature-owned presentation

- Status: Proposed; architecture recommendation for a future migration
- Date: 2026-09-26
- Baseline: `c2717a075`
- Supersedes: none; respects ADR-006's single-runtime decision
- Decision-maker: architecture review using the autonomous-decision workflow
- Implementation: not started by this decision record

## Context

The user requested an architectural migration plan and an English prompt for other implementation models. GrammarMate already has Compose/Material themes and shared controls, but some shared components combine presentation with session contracts, platform effects and storage access. A folder-only reorganization would not establish useful boundaries.

Evidence includes `ui/Theme.kt`, `ui/components/Glass.kt`, `UnifiedInputControlsBar.kt`, `UnifiedNavigationRow.kt`, and the active `GrammarMateApp -> TrainingScreenContent -> TrainingScreen` path. `TrainingCardSession` is not evidence of an adopted template: no production call was found in the baseline source search.

Relevant project constraints: ADR-006 retires the alternate runtime; current CI gates the shipping application; `CLAUDE.md` requires real click paths for Verb SessionCard regressions and preserving Repeat/Continue/Reset semantics. Existing tests and app behavior constrain the migration more strongly than outdated component documentation.

## Decision drivers and options

This is a complicated, reversible architecture choice with an uncertain payoff from abstraction. Three independent read-only briefs examined:

- **A — Conservative existing structure:** keep `ui/components` and `ui/screens`, document atomic responsibilities, extract components incrementally. Lowest relocation cost; ownership/discovery remain less explicit.
- **B — Global atomic hierarchy:** globally shared foundations/atoms/molecules/organisms/templates. Clear taxonomy and catalog; risks global feature coupling and configurable mega-components.
- **C — Hybrid:** generic foundations/controls plus feature-owned compositions, with shared patterns promoted only when supported by actual callers. Better ownership boundaries; additional mapping and migration discipline are required.

Weights follow the current single-runtime regression constraints and the user's goal of a maintainable UI foundation. Scores are subjective 1–5 assessments, with higher being preferable, not measured results.

| Driver | Weight | Reason |
|---|---:|---|
| Behavioral safety | 35% | Preserve learning/session rules and established UI journeys |
| Clear dependency/ownership boundaries | 25% | Existing rendering, contracts and platform effects are mixed |
| Useful design reuse | 20% | Support consistent future frontend work |
| Implementation economy | 10% | Avoid a parallel rewrite and needless relocation |
| Reversibility | 10% | Ship/revert bounded slices in one runtime |

| Option | Safety | Boundaries | Reuse | Economy | Reversibility | Weighted score |
|---|---:|---:|---:|---:|---:|---:|
| A | 5 | 3 | 3 | 5 | 5 | 4.10 |
| B | 3 | 4 | 5 | 2 | 3 | 3.55 |
| C, initial estimate | 4 | 5 | 5 | 3 | 5 | 4.45 |
| C, reuse unproven | 4 | 5 | 3 | 3 | 5 | 4.05 |

The sensitivity row is deliberate: reducing C's reuse estimate to 3 reverses the initial ranking. The matrix cannot justify imposing a physical hierarchy across the repository now.

## Pre-mortem

Assume the migration failed six months later:

1. **Universal organisms hide mode differences** — medium likelihood; detected by growing option flags and changed drill tests. Keep organisms feature-owned; share only compatible meaning and behavior.
2. **Effects run twice or input is lost** — medium likelihood, high impact; detected by changed launcher counts, click regressions and lifecycle testing. Characterize ownership before moving effects; retain adapters initially.
3. **Theme/inset/text defaults drift** — medium likelihood; detected by representative before/after rendering. Preserve effective values; keep app-level background/inset wrapping separate from foundational declarations.
4. **Package churn and permanent adapters outweigh gains** — high likelihood without discipline; detected in caller inventory and duplicate implementations. Create destinations on demand, keep public entrypoints, assign cleanup conditions.
5. **A polished catalog is disconnected from the app** — medium likelihood; detected by absent production call sites. Require a reachable production caller for every adopted extraction.

## Independent challenge and response

The strongest Red Team objection was that the pilot can prove state separation and token usage, but cannot prove a global Atomic folder hierarchy or broad reuse. It also identified the sensitivity of the optimistic reuse score and the risk of introducing UI dependencies into existing business `feature/` packages.

Accepted mitigations:

- Choose the hybrid **boundary policy**, with conservative physical adoption.
- Preserve existing entrypoints; create only directories containing production-used extractions.
- Default new feature presentation destinations to `ui/features/<owner>/`, keeping business helpers separate. Existing local placement may remain when clearer.
- Do not extract templates until repeated layout and semantics are demonstrated.
- Separate foundational tokens from the theme facade's background/inset behavior.
- Limit the first pilot to the active prompt card. Voice/input/session guarantees require their own subsequent gates.
- If the pilot adds only indirection, keep conservative packaging and revise the abstraction before expanding.

## Decision

Recommend C's ownership/dependency rules implemented through A's small reversible slices. The numerical comparison is too sensitive to unknown reuse to choose package relocation on its own. Both approaches can be equally reversible when new destinations are demand-driven and existing production entrypoints remain stable.

The first probe is `TrainingScreen::CardPrompt`: extract display-ready prompt/context/audio presentation while leaving hint calculation, identity and audit behavior in its integration boundary. Use this to evaluate whether the proposed boundary provides observable benefits. Do not introduce the apparently unused `TrainingCardSession` into production as part of the probe.

## Consequences

Positive: a concrete migration target, previewable display components, clear ownership and bounded regression risk. Existing Compose/Material tooling and one runtime remain sufficient.

Costs: temporary adapters, explicit mapping code, and an inventory are required. Some similar-looking feature components will intentionally remain separate. Folder taxonomy will not be perfectly uniform during migration.

Follow-up: execute P0 before runtime edits, then foundations and the prompt pilot; evaluate reuse and lifecycle risks before expanding to input/session templates. Implementation is governed by the linked plan and phase handoffs, not a blanket repository rewrite.

## Decision Journal

Expectations are conditional on implementation actually starting:

- Within 30 days of pilot adoption: the prompt component has a real production caller and pure preview fixtures; generic UI has no business/session/store imports; relevant click tests and observed visual parity are recorded.
- Within 90 days: migrated shared components have one rendering implementation, temporary wrappers are removed or have concrete remaining callers/removal criteria, and shared patterns are justified by compatible production consumers.
- Review triggers: P2 closure, first cross-feature reuse, and P6 consolidation.
- Reconsider if extraction produces only forwarding layers, mappings mirror the entire app state, option flags grow to encode mode logic, or parity/lifecycle regressions recur.

## References

- [Migration plan](../../plans/2026-09-26-atomic-design-migration.md)
- [ADR-006](006-drop-v2-single-runtime.md)
- [Atomic Design](https://atomicdesign.bradfrost.com/chapter-2/)
- [Compose custom design systems](https://developer.android.com/develop/ui/compose/designsystems/custom)
- [Compose state hoisting](https://developer.android.com/develop/ui/compose/state-hoisting)
