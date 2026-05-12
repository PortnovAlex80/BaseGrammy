# Scenario 14: Backup & Restore — Code Trace Analysis

Date: 2026-05-12

## Files Traced

| File | Path |
|------|------|
| BackupManager | `app/src/main/java/com/alexpo/grammermate/data/BackupManager.kt` |
| RestoreNotifier | `app/src/main/java/com/alexpo/grammermate/data/RestoreNotifier.kt` |
| AppRoot | `app/src/main/java/com/alexpo/grammermate/ui/AppRoot.kt` |
| MainActivity | `app/src/main/java/com/alexpo/grammermate/MainActivity.kt` |
| ProfileStore | `app/src/main/java/com/alexpo/grammermate/data/ProfileStore.kt` |
| StreakStore | `app/src/main/java/com/alexpo/grammermate/data/StreakStore.kt` |
| MasteryStore | `app/src/main/java/com/alexpo/grammermate/data/MasteryStore.kt` |
| ProgressStore | `app/src/main/java/com/alexpo/grammermate/data/ProgressStore.kt` |
| VerbDrillStore | `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt` |
| WordMasteryStore | `app/src/main/java/com/alexpo/grammermate/data/WordMasteryStore.kt` |
| DrillProgressStore | `app/src/main/java/com/alexpo/grammermate/data/DrillProgressStore.kt` |
| VocabProgressStore | `app/src/main/java/com/alexpo/grammermate/data/VocabProgressStore.kt` |
| HiddenCardStore | `app/src/main/java/com/alexpo/grammermate/data/HiddenCardStore.kt` |
| BadSentenceStore | `app/src/main/java/com/alexpo/grammermate/data/BadSentenceStore.kt` |

## Specs Compared

- `docs/specification/06-infrastructure.md` (sections 6.3 BackupManager, 6.5 RestoreNotifier)
- `docs/specification/13-app-entry-and-navigation.md` (sections 13.1-13.4)

---

## Test Case Results

### TC-1: Create backup — what files are included? Complete list.

**Code path:** `BackupManager.kt:60-108` (legacy), `BackupManager.kt:111-244` (scoped)

**Files included in backup:**

| File | Backup condition | Source |
|------|-----------------|--------|
| `mastery.yaml` | If exists in `grammarmate/` | `MasteryStore` |
| `progress.yaml` | If exists in `grammarmate/` | `ProgressStore` |
| `profile.yaml` | If exists in `grammarmate/` | `ProfileStore` |
| `streak_<languageId>.yaml` | All files matching `streak_*.yaml` glob | `StreakStore` |
| `metadata.txt` | Always created | Generated timestamp |

**Files NOT included in backup (data loss on restore):**

| File | Store | Impact if lost |
|------|-------|----------------|
| `drills/<packId>/verb_drill_progress.yaml` | `VerbDrillStore` | Verb drill progress lost |
| `drills/<packId>/word_mastery.yaml` | `WordMasteryStore` | Vocab mastery lost |
| `drill_progress_<lessonId>.yaml` | `DrillProgressStore` | Per-lesson drill position lost |
| `vocab_progress.yaml` | `VocabProgressStore` | Vocab sprint/SRS progress lost |
| `hidden_cards.yaml` | `HiddenCardStore` | Hidden card preferences lost |
| `bad_sentences.yaml` | `BadSentenceStore` | Reported bad sentences lost |
| `config.yaml` | `AppConfigStore` | Runtime config resets to defaults |
| `languages.yaml` | `LessonStore` | Language registration resets |
| `packs.yaml` | `LessonStore` | Pack registration resets |
| `stories_index.yaml` | `LessonStore` | Story index resets |
| `vocab_index.yaml` | `LessonStore` | Vocab index resets |
| Per-lesson CSV indices | `LessonStore` | Lesson data indices reset |

**Discrepancy with spec:** Spec section 6.3.2 accurately documents the backup contents and explicitly notes "lesson pack ZIP contents, drill CSV data, TTS/ASR model files, config.yaml" as excluded. However, the spec does not mention `hidden_cards.yaml`, `bad_sentences.yaml`, `drill_progress_*.yaml`, `vocab_progress.yaml`, or the pack-scoped drill progress files under `drills/`. The actual code also excludes these. The spec's exclusion list is incomplete -- it should enumerate all excluded user-data files that represent user progress.

**Verdict:** The backup is significantly incomplete. Of ~14 data stores that persist user state, only 4 are backed up. Drill progress (verb conjugation, vocab mastery, vocab SRS), hidden cards, bad sentences, and app config are all lost on reinstall+restore.

---

### TC-2: Backup format — ZIP? What internal structure?

**Code path:** `BackupManager.kt:63` (legacy), `BackupManager.kt:113` (scoped)

**Expected (spec 6.3.3):** "Backups are stored as a flat directory of YAML files — not a ZIP archive."

**Actual:** Confirmed. The backup is a flat directory `Downloads/BaseGrammy/backup_latest/` containing individual YAML files plus a `metadata.txt`. No ZIP, no compression, no encryption.

**Internal structure:**
```
Downloads/
  BaseGrammy/
    backup_latest/
      mastery.yaml
      progress.yaml
      profile.yaml
      streak_en.yaml       (one per active language)
      streak_it.yaml
      metadata.txt
```

**Discrepancy:** None. Code matches spec.

---

### TC-3: Backup location — Downloads/BaseGrammy/ — verify path

**Code path:** `BackupManager.kt:26-30`

```kotlin
private val backupDir: File? by lazy {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    File(downloadsDir, "BaseGrammy").apply { if (!exists()) mkdirs() }
}
```

**Scoped path:** `BackupManager.kt:113`
```kotlin
val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/BaseGrammy/backup_latest/"
```

**Expected:** `Downloads/BaseGrammy/backup_latest/`

**Actual:** Confirmed. Both legacy and scoped paths resolve to `Downloads/BaseGrammy/backup_latest/`.

**Discrepancy:** None.

---

### TC-4: Restore from backup — AppRoot flow — what's shown during restore?

**Code path:** `AppRoot.kt:21-30`, `RestoreNotifier.kt:1-44`, `MainActivity.kt:49-97`

**Flow:**

1. `MainActivity.onCreate()` runs restore logic (branches by SAF URI / Android version / permissions).
2. `setContent { AppRoot() }` is called immediately (line 94-96) -- UI renders before restore completes.
3. `AppRoot` collects `RestoreNotifier.restoreState` (line 23).
4. If `status != DONE`, `StartupScreen` is shown:
   - `IDLE` -> "Preparing..."
   - `IN_PROGRESS` -> "Restoring backup..."
   - `NEEDS_USER` -> "Waiting for backup folder..."
5. When `status == DONE`, `GrammarMateApp()` renders.

**Expected (spec 13.2.2):** "There is no timeout, no retry logic, and no user interaction on the StartupScreen beyond waiting."

**Actual:** Confirmed. The StartupScreen shows only a spinner and status text. No cancel button, no skip, no timeout.

**Discrepancy:** None. Code matches spec.

---

### TC-5: Restore completes — what data is recovered? What's lost?

**Code path:** `BackupManager.kt:251-305` (legacy restore), `BackupManager.kt:337-507` (SAF restore)

**Recovered:**

| Data | Source file |
|------|------------|
| Lesson mastery (uniqueCardShows, totalCardShows, intervalStep, shownCardIds) | `mastery.yaml` |
| Training session state (lesson, mode, position, boss rewards, daily cursor) | `progress.yaml` |
| User name | `profile.yaml` |
| Daily streak (per language) | `streak_<lang>.yaml` |

**Lost (not in backup):**

| Data | Store | File |
|------|-------|------|
| Verb drill progress (per-pack combo tracking) | `VerbDrillStore` | `drills/<pack>/verb_drill_progress.yaml` |
| Vocab word mastery (Anki-like SRS state) | `WordMasteryStore` | `drills/<pack>/word_mastery.yaml` |
| Lesson drill progress | `DrillProgressStore` | `drill_progress_<lesson>.yaml` |
| Vocab sprint/SRS progress | `VocabProgressStore` | `vocab_progress.yaml` |
| Hidden cards | `HiddenCardStore` | `hidden_cards.yaml` |
| Bad sentences | `BadSentenceStore` | `bad_sentences.yaml` |
| App config (testMode, multipliers) | `AppConfigStore` | `config.yaml` |
| Installed pack/language registrations | `LessonStore` | Various index YAMLs |

**Note:** Lesson pack content (CSVs) and TTS/ASR models are not expected to be in backup -- they are re-seeded from bundled assets on fresh install via `LessonStore.ensureSeedData()`. However, any imported custom packs (not in assets) would be lost.

**Verdict:** 4 of ~14 stores are backed up. Drill progress, vocab SRS, hidden cards, and bad sentences are silently lost after restore.

---

### TC-6: Corrupt backup file — error handling?

**Code path:** `BackupManager.kt:300-304` (legacy), `BackupManager.kt:498-506` (SAF)

**Legacy restore (`restoreFromBackup`):**
- Wrap entire operation in try-catch.
- Any exception -> `e.printStackTrace()` -> return `false`.
- Corrupt YAML file is still **copied** to internal storage (the copy is byte-level, not parse-level).
- Corruption surfaces later when the consuming store (MasteryStore, ProgressStore) tries to parse the file.
- MasteryStore.loadAll() (line 32-73) catches parse exceptions and returns empty cache.
- ProgressStore.load() (line 14-76) catches via `when` type checks and returns defaults.

**SAF restore (`restoreFromBackupUri`):**
- Same overall try-catch.
- Per-file try-catch: if an individual file fails to copy, it logs "ERROR: <name> - <message>" and continues to next file.
- Detailed restore log written to `restore_log.txt` in the backup directory.
- Returns `true` if at least one file was copied, even if others failed.

**Spec alignment (6.3.7):** "Corrupt YAML in backup -- File is still copied; corruption surfaces when the consuming store tries to parse it."

**Discrepancy:** None. Code matches spec.

---

### TC-7: Partial backup (missing some files) — what happens?

**Code path:** `BackupManager.kt:258-298` (legacy), `BackupManager.kt:376-398` (SAF)

**Legacy restore:**
- Each file is checked individually: `if (backupMasteryFile.exists()) { ... }`.
- Missing files are silently skipped.
- Return `true` as long as no exception thrown (even if zero files were copied).

**SAF restore:**
- Missing files are logged as "MISSING: <name>" in the restore log.
- `missingFiles` list is tracked and included in the summary.
- Returns `true` only if at least one file was actually copied (`copied` flag).

**Edge case:** Legacy restore returns `true` even when no files exist. This means `RestoreNotifier.markComplete(true)` is called but no data was actually restored. SAF restore handles this correctly (returns `false` if nothing copied).

**Discrepancy:** Legacy `restoreFromBackup()` returns `true` even when zero files were copied. This is a minor bug -- the user sees "restore succeeded" but no data was actually restored. The SAF path correctly returns `false` in this case.

---

### TC-8: Backup from different app version — forward compatibility?

**Code path:** `metadata.txt` in `BackupManager.kt:586-599`

**Actual behavior:**
- `metadata.txt` hardcodes `"App version: 1.0"` regardless of actual version.
- There is no version checking on restore.
- The restore is purely a file copy operation -- no schema migration is performed during restore itself.
- Schema migrations happen in the individual stores when they `load()` the data (e.g., `ProgressStore` migrates `bossMegaReward` to `bossMegaRewards`).
- `VocabProgressStore`, `MasteryStore`, etc. use `(data["data"] as? Map<*, *>) ?: data` fallback to handle both wrapped and unwrapped YAML formats.

**Spec alignment (6.3.8):** "Metadata hardcodes 'App version: 1.0' rather than reading the actual version from BuildConfig."

**Verdict:** Forward compatibility works by accident -- stores are lenient in parsing and have fallback paths. But there is no explicit versioning or migration framework for backup format changes.

**Discrepancy:** None (this is documented as a known limitation).

---

### TC-9: Restore overwrites existing data — is old data cleared first?

**Code path:** `BackupManager.kt:255-258` (legacy), `BackupManager.kt:365-369` (SAF)

**Actual behavior:**
- `internalDir.mkdirs()` is called to ensure the directory exists.
- Individual files are copied with `overwrite = true` (legacy) or via `outputStream` (SAF).
- **No bulk deletion of existing data occurs before restore.**
- Only files that exist in the backup are overwritten.
- Any pre-existing files NOT in the backup are left untouched.

**Implications:**
- If a user has `hidden_cards.yaml` and restores from a backup that doesn't include it, the old `hidden_cards.yaml` remains.
- If a user restores over data from a different language, old mastery data for that language gets merged (backup overwrites specific files, but other files remain).
- Stale `drill_progress_*.yaml` files from old lessons would persist after restore.

**Verdict:** Restore is additive/overwriting, not destructive. Old data not covered by the backup survives alongside restored data. This can lead to inconsistent state.

**Discrepancy:** The spec (6.3.5) does not mention this behavior explicitly. It describes restore as copying files but does not specify whether existing files outside the backup set are retained.

---

### TC-10: Streak after restore — does it migrate correctly?

**Code path:** `BackupManager.kt:85-96` (legacy backup), `BackupManager.kt:279-291` (legacy restore), `BackupManager.kt:428-447` (SAF restore)

**Backup:**
- Old `streak.yaml` (single file, no language suffix) is deleted from backup dir on creation.
- New format `streak_<languageId>.yaml` files are copied.

**Restore:**
- All `streak_*.yaml` files are copied from backup.
- Old `streak.yaml` migration: if found in backup, parse YAML, extract `languageId` field (defaults to `"en"`), copy to `streak_<languageId>.yaml`.

**StreakStore.load()** (`StreakStore.kt:36-56`):
- Loads `streak_<languageId>.yaml` for the requested language.
- Returns `StreakData(languageId)` default if file doesn't exist.
- Fields: `currentStreak`, `longestStreak`, `lastCompletionDateMs`, `totalSubLessonsCompleted`.

**StreakStore.getCurrentStreak()** (`StreakStore.kt:136-153`):
- Checks days since last completion.
- If more than 1 day has passed, resets `currentStreak` to 0 and saves.
- This means if a backup is restored days later, the streak will show 0 even if it was non-zero when backed up.

**Verdict:** Streak migration works correctly for the old-to-new format. However, the streak is recalculated at load time -- if restore happens after the streak should have expired, it will be reset to 0. This is correct behavior (streaks should expire), but the user may be surprised if they restore immediately and the streak is already stale.

**Discrepancy:** None.

---

### TC-11: Drill progress after restore — pack-scoped files restored?

**Code path:** Not present in BackupManager.

**Actual behavior:**
- `VerbDrillStore` stores progress at `grammarmate/drills/<packId>/verb_drill_progress.yaml`.
- `WordMasteryStore` stores mastery at `grammarmate/drills/<packId>/word_mastery.yaml`.
- `BackupManager.createBackup()` does NOT iterate the `drills/` subdirectory.
- `BackupManager.restoreFromBackup()` does NOT restore any files from `drills/`.
- `BackupManager.restoreFromBackupUri()` does NOT restore any files from `drills/`.

**Verdict:** Pack-scoped drill progress files are NOT restored. After reinstall+restore, all verb drill progress and vocab mastery is lost. The user starts from scratch on drills.

**Discrepancy:** The spec (6.3.2) says "Explicitly excluded: lesson pack ZIP contents, drill CSV data" but does not explicitly mention drill **progress** files. Drill CSV data (source content) is different from drill progress (user state). The exclusion of drill progress is a significant data loss that the spec does not call out clearly.

---

### TC-12: TTS/ASR models — included in backup?

**Code path:** Not present in BackupManager.

**Actual behavior:**
- TTS models are managed by `TtsModelManager` and stored in the app's internal files directory.
- ASR models are managed by `AsrModelManager`.
- Neither model files nor their download state are included in backup.

**Spec alignment (6.3.2):** "Explicitly excluded: ... TTS/ASR model files."

**After restore:**
- `AsrModelManager.isReady()` returns `false` (model files don't exist).
- User must re-download models via the settings UI.

**Discrepancy:** None. This is documented and expected behavior.

---

### TC-13: Backup on Android 10+ vs older — scoped storage vs legacy path

**Code path:** `BackupManager.kt:43-57` (branch), `BackupManager.kt:60-108` (legacy), `BackupManager.kt:111-244` (scoped)

**Android 10+ (API 29+):**
- `createBackup()` checks `Environment.isExternalStorageLegacy()`.
- If legacy storage mode: uses `createBackupLegacy()` (direct File I/O).
- If scoped storage: uses `createBackupScoped()` (MediaStore API).
- Scoped storage writes via `ContentResolver` + `MediaStore.Downloads.EXTERNAL_CONTENT_URI`.
- Handles duplicate "(1)" files from MediaStore collisions.
- Cleans up legacy filesystem duplicates.

**Pre-Android 10:**
- Always uses `createBackupLegacy()`.
- Direct file copy with `File.copyTo()`.

**Restore path selection in `MainActivity.kt:49-97`:**
- If stored SAF URI exists -> `restoreFromBackupUri()` (SAF path).
- If API 29+ -> SAF picker (`OpenDocumentTree`).
- If pre-29 with permissions -> `restoreFromBackup()` (legacy file path).
- If pre-29 without permissions -> request permissions first.

**Verdict:** Two completely different code paths for backup and restore, with different characteristics. The scoped path has more sophisticated duplicate handling but both produce the same output files.

**Discrepancy:** None.

---

### TC-14: SAF URI-based restore vs file-path restore — how do they differ?

**Code paths:**
- File-path: `BackupManager.kt:251-305` (`restoreFromBackup`)
- SAF URI: `BackupManager.kt:337-507` (`restoreFromBackupUri`)

| Aspect | File-path restore | SAF URI restore |
|--------|-------------------|-----------------|
| API | `java.io.File` | `DocumentFile` + `ContentResolver` |
| Error logging | `printStackTrace()` only | Detailed `restore_log.txt` written to backup dir |
| Missing files | Silently skipped | Logged as "MISSING" in restore log |
| Mastery analysis | None | Parses mastery.yaml and logs per-language/lesson breakdown |
| Old streak migration | Yes (lines 279-291) | Yes (lines 428-447) |
| Return on no files | `true` (bug) | `false` (correct) |
| Backup discovery | Looks for `backup_*` dirs | Looks for `backup_latest` subfolder, falls back to root |

**Discrepancy:** The SAF restore is significantly more robust than the file-path restore. It provides detailed logging, correct "no files" handling, and mastery data analysis. The legacy path lacks all of these. This asymmetry is not documented in the spec.

---

### TC-15: Multiple backups — can user choose which to restore?

**Code path:** `BackupManager.kt:539-561` (`getAvailableBackups`), `MainActivity.kt:169-191` (`startLegacyRestore`)

**Backup discovery:**
- `getAvailableBackups()` scans `Downloads/BaseGrammy/` for directories starting with `backup_` or named `backup_latest`.
- Returns sorted by modification time (newest first).
- Each `BackupInfo` has name, path, timestamp, dataSize, metadata.

**Legacy restore in MainActivity:**
- `startLegacyRestore()` calls `backupManager.getAvailableBackups()`.
- Takes `backups.firstOrNull()` -- **always the newest backup**.
- No UI is presented to let the user choose.

**SAF restore in MainActivity:**
- User selects a folder via the SAF picker.
- The selected folder is used directly.
- `restoreFromBackupUri()` looks for `backup_latest` subfolder first, then falls back to root.
- This gives the user indirect choice by selecting which folder to pick.

**SAF getAvailableBackups(Uri):**
- Takes a `treeUri` parameter.
- Scans the tree for `backup_*` directories.
- Returns list of `BackupInfo` with URI references.
- But this method is **never called from MainActivity** -- the restore goes straight to `restoreFromBackupUri()` without listing backups.

**Verdict:** On legacy (pre-Android 10), the user cannot choose -- the newest backup is always used. On SAF (Android 10+), the user can choose by selecting a different folder in the SAF picker, but there is no in-app UI showing available backups with metadata.

**Discrepancy:** The `getAvailableBackups(treeUri)` method exists but is not wired to any UI. The spec (6.3.6) documents the method but does not mention that it is unused.

---

## Summary of Discrepancies

### Critical

| ID | Issue | Severity | Files |
|----|-------|----------|-------|
| D1 | **Backup omits 10+ user-data stores**: Verb drill progress, word mastery, drill progress, vocab SRS progress, hidden cards, bad sentences, app config, and pack/language registrations are NOT backed up. After reinstall+restore, users lose all drill progress, vocab SRS state, hidden card preferences, and bad sentence reports. | HIGH | BackupManager.kt:60-108, 111-244 |
| D2 | **Legacy restore returns `true` on zero files**: `restoreFromBackup()` returns `true` even when no files were copied (all backup files missing). This causes `RestoreNotifier.markComplete(true)` to report success when nothing was restored. | MEDIUM | BackupManager.kt:251-305 |
| D3 | **Restore does not clear stale data**: Pre-existing files not in the backup (e.g., drill progress from a different state) are left untouched, potentially causing inconsistent state after restore. | MEDIUM | BackupManager.kt:251-305, 337-507 |

### Minor

| ID | Issue | Severity | Files |
|----|-------|----------|-------|
| D4 | **`getAvailableBackups(treeUri)` is never called**: The SAF backup listing method exists but is not wired to any UI or restore flow. | LOW | BackupManager.kt:307-335 |
| D5 | **Metadata hardcodes version "1.0"**: `metadata.txt` always writes "App version: 1.0" instead of reading `BuildConfig.VERSION_NAME`. | LOW | BackupManager.kt:586-599 |
| D6 | **No user choice on legacy restore**: `startLegacyRestore()` always picks the newest backup without user input. | LOW | MainActivity.kt:178-184 |
| D7 | **Spec exclusion list incomplete**: Section 6.3.2 does not enumerate drill progress files, hidden cards, bad sentences, or vocab SRS as excluded from backup. | LOW | 06-infrastructure.md |

### Spec Conformance (No Discrepancy)

| Test Case | Result |
|-----------|--------|
| TC-2: Backup format | Flat directory, not ZIP -- matches spec |
| TC-3: Backup location | Downloads/BaseGrammy/backup_latest/ -- matches spec |
| TC-4: AppRoot restore flow | StartupScreen gate with spinner -- matches spec |
| TC-6: Corrupt backup handling | File copied, corruption surfaces at parse time -- matches spec |
| TC-8: Forward compatibility | Lenient parsing, no version gate -- matches spec (documented limitation) |
| TC-10: Streak migration | Old streak.yaml -> streak_<lang>.yaml -- matches spec |
| TC-12: TTS/ASR excluded | Models not in backup -- matches spec |
| TC-13: Scoped vs legacy | Both paths produce same output -- matches spec |
