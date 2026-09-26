# GrammarMate Atomic Design — independent review prompt

Give this prompt to a different model after an implementation slice. Supply the assigned phase, baseline revision, diff and implementation report. Repository access is preferred.

---

Act as an independent senior Android architecture and regression reviewer for GrammarMate's Atomic Design migration. Review the supplied phase against `docs/plans/2026-09-26-atomic-design-migration.md` and the current repository. Do not edit files unless separately instructed.

Establish what actually changed and whether the new components are called by the production UI. Trace callers; a catalog or preview is not production integration. The known baseline training path is `GrammarMateApp -> TrainingScreenContent -> TrainingScreen`; `TrainingCardSession` had no production call site and must not be assumed active.

Review these dimensions:

1. **Scope:** Is this the assigned phase? Were visual redesign, dependency changes, domain refactoring, new runtime/module, or broad package moves introduced without scope?
2. **Architecture:** Are atomic levels useful responsibilities? Are foundations independent of components? Do generic controls avoid ViewModels, business models/contracts, storage, navigation and services? Are feature mappings and effectful adapters explicit? Do abstractions represent actual shared semantics?
3. **Behavior:** Trace changed callbacks, state ownership, effect keys/cancellation, launchers, input preservation, focus/IME, hints, pause/retry/advance, completion, audio cleanup and navigation. Preserve the separate input and timer flows. Focus on the changed slice; do not claim that a prompt-only pilot proves voice/session lifecycle.
4. **Product rules:** Check affected pack/language scoping, WORD_BANK/mastery separation, Verb Repeat/Continue/Reset, navigation-only visits, daily/vocabulary/boss differences and empty/unattempted exits against existing tests and code.
5. **Presentation:** Check actual theme resolution, typography, app/system text scale, context visibility, inset ownership, relevant layouts and audio states. Compare available before/after evidence. Verify semantics/test tags at the rendered node, not just source string existence.
6. **Verification:** Inspect test assertions and reported commands/results. Identify disabled/weakened tests, direct-state shortcuts in click regressions, unexecuted checks labelled as passed, and unsupported visual/performance claims. Rerun only checks justified by the change or unresolved evidence and available tools.
7. **Maintenance:** Look for duplicate implementations, unused components, unjustified wrappers, excessive boolean configuration, stale docs and compatibility adapters without removal criteria.

Report findings in severity order. Each finding must include a file/symbol or line, a concrete trigger, expected versus actual behavior, impact, and a bounded fix. Separate confirmed defects from plausible risks and missing evidence. If no actionable defects are found, say so while listing any unverified acceptance gates.

Conclude with `PASS`, `PASS WITH FOLLOW-UPS`, or `CHANGES REQUIRED` for this slice, backed by the checks actually performed. A missing essential behavior/visual gate prevents unconditional PASS. State the smallest next action; do not propose a wholesale rewrite.
