# 029. Restore managed progress as one snapshot

- **Status:** Accepted
- **Date:** 2026-09-29
- **Scope:** Progress backup creation and restore, startup and live ViewModel barriers

## Context and decision

Backup creation reused `backup_latest` without removing deleted files. Restore copied available files over live data without removing files absent from the backup. Delayed mastery and timer saves, cached store values and early ViewModel construction could overwrite or display the wrong restored data.

Weights: progress correctness 40%, ownership/isolation 25%, migration compatibility 20%, testability 15%. Scores are 1–5.

| Option | Correctness | Ownership | Compatibility | Testability | Weighted / 5 |
|---|---:|---:|---:|---:|---:|
| Preserve merge semantics and document them | 1 | 2 | 4 | 2 | 2.10 |
| Replace the whole app data directory | 3 | 2 | 1 | 3 | 2.35 |
| Replace only a declared progress snapshot | 5 | 5 | 4 | 5 | 4.80 |

Choose a declared set of managed progress files. `BackupSnapshotFiles` identifies them; `BackupManager` stages backups and restores the managed set as a snapshot while leaving installed content and app configuration untouched. Legacy backup names are mapped. `MasteryStore` drains pending writes before file replacement, and factory-managed live caches are invalidated after success. `AppRoot` constructs `TrainingViewModel` only after startup restore reaches `DONE`. Live restore pauses its session clock, drains timer checkpoints, drops transient answer/deck state, invalidates ViewModel-held pack progress/cursor caches and rebuilds derived UI after replacement.

## Pre-mortem and challenge

This could lose custom content if restore deleted the entire app directory, lose newer progress if a late writer ran after replacement, or display stale data if an existing store reference kept its cache. The allowlist excludes lessons and configuration; writer drains precede copying; cache invalidation targets live instances rather than merely dropping factory maps. A failed restore must leave the prior files and active timer intact. Real-store tests compare dataset A against restored B and check removed-file semantics; timer-writer and session-state regressions cover the surrounding barriers. A focused regression also verifies legacy filename mapping.

The Red Team challenged treating a successful copy as a complete restore. The application must also publish the restored pack identity, select a valid lesson, rebind pack-scoped stores and refresh derived state. The ViewModel performs those effects after the data layer reports success. Device route evidence remains a separate G5 requirement.

## Consequences and decision journal

Restore is a replacement for managed learning progress, not a merge. New persisted progress files must be added to the managed-file list and tested on create and restore. Installed packs, imported lesson content and app configuration remain outside this snapshot by design. Recheck when introducing another progress store, asynchronous save lane or startup read.
