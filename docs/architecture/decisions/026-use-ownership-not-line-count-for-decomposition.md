# 026. Use ownership, not line count, to close large-file decomposition

- **Status:** Accepted
- **Date:** 2026-09-29
- **Scope:** G6 of the architecture closure contract and the fixed 14-file review inventory
- **Amends:** ADR-009's 800-line acceptance threshold

## Context

ADR-009 used 800 physical lines to identify and close large owners. That number is a local heuristic, not an industry standard. It can reward moving methods into forwarding wrappers while leaving state and effects mixed. The user clarified that the real objective is an architecture agents can understand quickly: clear responsibilities, modular and isolated owners, clean layer direction and correct business rules. The 2,500-line `TrainingViewModel` visibly combines composition, feature commands and persistence, so it still needs focused decomposition even if an arbitrary threshold is removed.

## Decision

Keep the original 14 files as the finite review inventory. For each, document responsibilities, live callers, authoritative state and side effects. Extract independently changing capabilities into named owners while preserving public entrypoints and event order. Review every extracted owner for a coherent reason to change, unambiguous state/effect ownership and useful business regression evidence. A façade may remain large when it transparently composes owners and preserves a useful integration surface; a short file can still fail this review when it hides mixed responsibilities or circular callbacks. Physical line counts remain diagnostic inventory only. G6 passes on the ownership review and G1–G5 evidence, with no numerical line threshold.

For the current slice, `TrainingProgressSnapshotSource` reads authoritative flows and the direct daily cursor; `TrainingProgressWriter` owns write sequencing, mastery flush and pending backup requests. `TrainingViewModel` chooses when to save and retains the existing public actions. The extraction creates no second progress-state store.

## Risks and follow-up

An ownership-only gate could become subjective or endless. The fixed inventory, explicit owner/effect table, live-caller audit and named transition tests make the review finite and falsifiable. Watch for callback cycles, duplicated mutable state, hidden I/O in display owners, and passing tests that exercise a fake path instead of the production entry. Recheck these at G6 closure; do not start a new file merely because its line count is high without a separate scope decision.
