# Backup restore consistency follow-up

Date: 2026-09-29. Found while reviewing the `TrainingViewModel` startup split. This is a separate correctness defect, not completed by ADR-027.

## Reproducible risks

- `MasteryStoreImpl` can retain a loaded snapshot and a delayed write while backup restore copies `mastery.yaml`. A later write can overwrite the restored file.
- `HiddenCardStoreImpl`, `BadSentenceStoreImpl`, and cached pack-scoped word/verb/vocab stores can keep old data after same-process restore. `StoreFactory.clearCache()` only drops some factory map entries; existing references remain live.
- `AppRoot` creates `TrainingViewModel` before `RestoreNotifier.DONE`, so its startup read and mastery recalculation can overlap cold-start restore despite the UI waiting.
- Backup creation reuses `backup_latest` without pruning files that disappeared. Restore copies files present in the backup without deleting absent target files, so the result can be a merge rather than the expected snapshot. Pack-scoped vocab and aux drill files are not collected.

## Acceptance for a separate repair

1. Define a restore barrier that prevents startup reads/writes and active-session saves from overlapping file replacement.
2. Drain delayed mastery writes **before** copying, then invalidate every live cache whose file was replaced; rebuilding cards, flowers, visibility and drill counts must occur only afterward.
3. Test a same-pack restore from dataset A to B with real stores: prime caches and a delayed mastery write, restore B, verify UI/domain reads and disk still contain B after the writer delay.
4. Define and test whether restore means full snapshot replacement or merge. Make the backup file list match that contract, including pack-scoped drill data.

Until this is done, a successful file-copy result alone does not prove that the running app uses the restored progress.
