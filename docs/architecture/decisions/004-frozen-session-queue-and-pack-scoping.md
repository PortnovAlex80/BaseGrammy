# ADR-004: Frozen session queue and pack-scoped state

- Status: Accepted
- Date: 2026-08-27

## Context

Lesson sessions persisted only the current sub-lesson. At a boundary the engine read
content and hidden cards again and rebuilt the remaining sub-lessons with a hard-coded
size. A content update, hide action, process restart, or a non-default session size could
therefore skip, repeat, or reorder cards. Several content and user-state tables also used
globally unique card IDs even though imported packs only guarantee uniqueness inside a
pack.

## Decision

At session creation the engine freezes the ordered visible card sequence. The active
chunk is stored in `session_cards`; the remaining sequence is stored, in order, in
`session_pending_cards`. `sessions.sessionSize` records the chunk size. Advancing a
boundary only promotes the next persisted chunk and never rereads pack content.

Hidden cards and drill content are addressed by `(packId, id)`. Hiding a card removes it
from both the active pool and the pending queue in the same commit boundary as the
persisted hidden marker. Active lesson sessions from schema v5 are discarded during the
v6 migration because their missing tail cannot be reconstructed without changing user
visible order; completed sessions and learning progress remain intact.

## Options considered

Weighted criteria: correctness 35%, resume determinism 20%, architectural alignment
15%, implementation complexity 15%, testability 10%, reversibility 5%.

| Option | Weighted score | Notes |
|---|---:|---|
| Persist nested sub-lesson chunks | 4.35 | Explicit boundaries, more schema and mapping machinery |
| Persist a flat pending queue plus session size | 4.55 | Same behavior with a smaller durable contract |
| Rebuild fixed-size chunks at every boundary | 3.60 | Simple storage, nondeterministic after state/content changes |

The flat queue was selected because sub-lesson boundaries currently have no independent
identity, reward, or business metadata.

## Pre-mortem and safeguards

- A v5 active session silently loses its tail: migration explicitly invalidates only
  active lesson sessions.
- Hide state and the snapshot diverge: both writes use `SessionCommitCoordinator`.
- A reimport removes a queued ID: resume preserves queue order; presentation handles a
  missing content record as an explicit content error instead of rebuilding the queue.
- Domain/entity mappings drift: repository contract and Room migration tests cover the
  active pool, pending queue, session size, and pack scope.

## Consequences

Resume and boundary transitions are deterministic and do not perform content queries.
The schema gains one small ordered child table and a session-size column. A future feature
that assigns semantics to sub-lesson boundaries will require a new persisted boundary
model rather than inferring it from this queue.

## Decision journal

Expected result: hiding, reimporting, or restarting the process during a lesson cannot
change the order or number of the remaining cards; identical IDs in different packs do
not overwrite or hide one another. Revisit this ADR if chunks acquire their own rewards,
deadlines, or server-side identifiers.
