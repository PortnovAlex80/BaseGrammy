# 030. Preserve imported packs during seed checks

- **Status:** Accepted
- **Date:** 2026-09-29
- **Scope:** `LanguageManager.ensureSeedData` and installed pack catalog

## Context and decision

`ensureSeedData` ran `cleanupStalePacks` on every call. That helper treated any pack ID absent from the current bundled defaults as obsolete for the built-in languages and deleted its catalog entry, pack directory and drill data. Importing a user pack for Italian followed by another import or lesson read could therefore erase the first pack. The behavior broke pack-switch UI fixtures before navigation began and represents real user-content loss.

Weights: content safety 45%, business correctness 30%, migration compatibility 15%, complexity 10%. Scores are 1–5.

| Option | Safety | Correctness | Compatibility | Simplicity | Weighted / 5 |
|---|---:|---:|---:|---:|---:|
| Keep deletion by language and default ID list | 1 | 1 | 2 | 4 | 1.45 |
| Guess bundled provenance from names or paths | 2 | 3 | 2 | 2 | 2.35 |
| Stop automatic deletion; migrate known bundled IDs only with provenance | 5 | 5 | 4 | 4 | 4.75 |

Remove the unconditional cleanup from `ensureSeedData` and its unused helper. `updateDefaultPacksIfNeeded` still handles versioned updates for currently bundled packs. Any future removal of an obsolete bundled pack requires a separate one-time migration keyed to a known former bundled ID or recorded provenance; language alone is insufficient.

## Pre-mortem and challenge

This could leave an old bundled pack installed after a release stops shipping it. That is less severe than deleting user-imported lessons and drills, and is visible in the installed-pack catalog for a targeted migration later. The Red Team rejected a name-prefix heuristic because users can import any pack ID and a false positive is irreversible. A real-store regression imports multiple user packs for the same built-in language, runs seed and lesson reads, and verifies both catalog entries and content remain.

## Consequences and decision journal

Seed checks now add missing default language entries and update current bundled pack versions without treating unknown packs as disposable. Imported pack identity survives ordinary reads and app restarts. Recheck this decision only when pack provenance is explicitly represented in persisted metadata or a bounded migration lists exact obsolete bundled IDs.
