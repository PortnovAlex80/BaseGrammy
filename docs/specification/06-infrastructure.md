# 6. Infrastructure -- Specification

This section covers the foundational infrastructure components of GrammarMate's data layer: the safe file-write mechanism, generic YAML storage, backup/restore system, runtime configuration, and the restore notification bus.

---

## 6.1 AtomicFileWriter

### 6.1.1 Purpose

`AtomicFileWriter` is a singleton (`object`) that provides a crash-safe file write primitive. Every persistent store in GrammarMate must route file writes through this mechanism to prevent data corruption from interrupted writes (app crash, device shutdown, battery pull).

### 6.1.2 Algorithm (step by step)

```
writeText(file, text, charset=UTF-8)
    1. Ensure parent directory exists (mkdirs if absent)
    2. Create temp file reference: <parent>/<name>.tmp
    3. If temp file already exists:
       a. Delete it
       b. Retry deletion up to 10 times with 10 ms sleep between attempts
          (Windows file-locking workaround -- .tmp may be held by AV or OS)
    4. Write text bytes to temp file via FileOutputStream
    5. Call fd.sync() (fsync) -- forces OS to flush buffers to disk
    6. Close stream (via .use {})
    7. If target file exists:
       a. Delete target file
       b. If delete fails: delete temp file, throw IllegalStateException
    8. Rename temp file to target file
    9. If rename fails: delete temp file, throw IllegalStateException
```

### 6.1.3 Error handling at each stage

| Stage | Failure mode | Behavior |
|-------|-------------|----------|
| Parent mkdirs | Cannot create directory | Proceeds regardless; FileOutputStream will throw if directory is invalid |
| Temp file cleanup | Temp file locked on Windows | Retries up to 10x with 10 ms pauses (up to ~100 ms total). If still locked after retries, write proceeds -- FileOutputStream will overwrite if possible |
| FileOutputStream write | Disk full / IO error | Exception propagates to caller; temp file remains on disk (harmless) |
| fd.sync() | IO error during flush | Exception propagates; target file unchanged |
| Target delete | File locked / permission denied | Temp file is deleted, `IllegalStateException("Failed to replace ...")` thrown |
| Rename (temp -> target) | Cross-mount rename failure | Temp file is deleted, `IllegalStateException("Failed to finalize ...")` thrown |

### 6.1.4 Why this exists

On Android, a direct `File.writeText()` is not atomic. If the app crashes mid-write, the target file is left in a corrupted partial state. The temp-write-fsync-rename pattern guarantees that the target file either contains the complete previous content or the complete new content -- never a truncated mix.

The Windows retry loop (step 3b) addresses a specific issue where antivirus scanners or the OS index lock the `.tmp` file immediately after deletion on Windows development machines.

### 6.1.5 Design notes

- **Singleton**: `object AtomicFileWriter` -- stateless, safe to call from any thread.
- **No internal synchronization**: callers are responsible for not issuing concurrent writes to the same file. In practice, all stores are called from the main thread via ViewModel actions.
- **Charset parameter**: defaults to UTF-8, overridable for edge cases.
- **The `.tmp` extension** is hardcoded; there is no configurable temp-file suffix.

---

## 6.2 YamlListStore

### 6.2.1 Purpose

`YamlListStore` is a generic read/write wrapper that stores a list of homogeneous YAML maps in a single file. It handles serialization, schema versioning, and delegates persistence to `AtomicFileWriter`.

### 6.2.2 Public API

```kotlin
class YamlListStore(
    yaml: Yaml,              // SnakeYAML instance (shared, not created internally)
    file: File,              // Target file path
    schemaVersion: Int = 1   // Written into file header for future migrations
)
```

| Method | Signature | Returns | Description |
|--------|-----------|---------|-------------|
| `read` | `fun read(): List<Map<String, Any>>` | List of string-keyed maps, or empty list | Reads and deserializes the YAML file. Returns `emptyList()` if file does not exist, is empty, or has unexpected structure. |
| `write` | `fun write(items: List<Map<String, Any>>)` | Unit | Serializes items and writes via `AtomicFileWriter`. |

### 6.2.3 Serialization format

**Written format** (via `write`):

```yaml
schemaVersion: 1
items:
  - key1: value1
    key2: value2
  - key1: value3
    key2: value4
```

The root is always a `LinkedHashMap` with two keys: `schemaVersion` (Int) and `items` (List).

**Read format** (via `read`) -- supports two legacy shapes:

1. **Current format** (Map root with `items` key): reads `root["items"]` as a list of maps.
2. **Legacy format** (List root): treats the entire root as the list of maps directly. This provides backward compatibility if a file was written without the `schemaVersion`/`items` wrapper.

If the file content is neither a Map nor a List (e.g., a scalar), `read()` returns `emptyList()`.

### 6.2.4 Thread safety

`YamlListStore` has **no internal synchronization**. It is not thread-safe. All read/write operations should be called from the same thread. In practice, LessonStore creates instances on the main thread and uses them sequentially.

### 6.2.5 Usage pattern -- stores that use YamlListStore as a base

`LessonStore` instantiates multiple `YamlListStore` instances for different indices:

| Instance field | File path | Content |
|---------------|-----------|---------|
| `languagesStore` | `grammarmate/languages.yaml` | Registered languages |
| `packsStore` | `grammarmate/packs.yaml` | Installed lesson packs |
| `storiesStore` | `grammarmate/stories_index.yaml` | Story content index |
| `vocabStore` | `grammarmate/vocab_index.yaml` | Vocab drill index |

Additionally, `LessonStore` creates ad-hoc `YamlListStore` instances inline for lesson-specific index files (e.g., per-lesson sentence indices).

### 6.2.6 Schema versioning

The `schemaVersion` field is written on every `write()` but is **not read or interpreted** by `YamlListStore.read()`. It exists as a forward-compatibility marker for future migration logic that consumers may implement.

---

## 6.3 BackupManager

### 6.3.1 Purpose

`BackupManager` handles exporting and importing user progress data to/from external storage. It supports both legacy file-path access (pre-Android 10) and scoped MediaStore access (Android 10+).

### 6.3.2 What gets backed up

The following files from `context.filesDir/grammarmate/` are included in every backup:

| File | Content | Required |
|------|---------|----------|
| `mastery.yaml` | Per-lesson card mastery data (uniqueCardShows, totalCardShows, intervalStepIndex, lastShowDateMs) | Copied if exists |
| `progress.yaml` | Training session state (current lesson, sub-lesson, card index) | Copied if exists |
| `profile.yaml` | User name and settings | Copied if exists |
| `streak_<languageId>.yaml` | Per-language daily streak data (one file per active language) | Copied if exists (glob: `streak_*.yaml`) |

**Explicitly excluded**: lesson pack ZIP contents, drill CSV data, TTS/ASR model files, config.yaml.

### 6.3.3 Backup format

Backups are stored as a flat directory of YAML files -- **not** a ZIP archive.

**Location**: `Downloads/BaseGrammy/backup_latest/`

**Directory structure**:
```
Downloads/
  BaseGrammy/
    backup_latest/
      mastery.yaml
      progress.yaml
      profile.yaml
      streak_en.yaml
      streak_it.yaml
      metadata.txt
```

**metadata.txt format** (plain text):
```
Backup created: 2025-05-12_14-30-00
App version: 1.0
Data format: YAML
Contents:
- mastery.yaml (flower levels and progress)
- progress.yaml (training session progress)
- streak.yaml (daily streak data)
- profile.yaml (user name and settings)
```

The backup directory name is always `backup_latest`. Previous backups are overwritten, not accumulated.

### 6.3.4 Backup creation flow

The entry point is `createBackup(): Boolean`.

**Branching by Android version**:

```
if (SDK >= 29):
    if (isExternalStorageLegacy):
        createBackupLegacy()
    else:
        createBackupScoped()
else:
    createBackupLegacy()
```

#### createBackupLegacy() -- pre-Android 10 or legacy storage mode

1. Ensure `Downloads/BaseGrammy/backup_latest/` directory exists.
2. Copy each data file (mastery, progress, profile) to backup dir with `overwrite = true`.
3. Glob all `streak_*.yaml` files from internal dir and copy each.
4. Delete old `streak.yaml` if it exists in backup dir (migration cleanup).
5. Create `metadata.txt` with timestamp.
6. Return `true`.

#### createBackupScoped() -- Android 10+ with scoped storage

Uses `MediaStore.Downloads` content provider instead of direct file I/O.

For each data file:
1. Delete duplicate MediaStore entries with names matching `"name (%"` pattern in the backup path (cleans up Windows-style "(1)" duplicates).
2. Delete legacy filesystem duplicates if accessible.
3. Look up existing MediaStore entry by `RELATIVE_PATH` and `DISPLAY_NAME`.
4. If found: overwrite via `openOutputStream(uri, "wt")`.
5. If not found: delete any legacy filesystem file with the same name to prevent "(1)" duplicates, then insert new MediaStore entry.
6. Copy bytes from internal file to content provider output stream.

Streak files are handled the same way, iterated via `internalDir.listFiles` glob.

Returns `true` if at least one file was written (`wroteAny`).

### 6.3.5 Restore flows

There are two restore entry points:

#### restoreFromBackup(backupPath: String): Boolean

Legacy file-path restore. Used when the backup directory is accessible via direct path.

1. Verify backup directory exists.
2. Ensure internal `grammarmate/` directory exists.
3. Copy `mastery.yaml`, `progress.yaml` from backup to internal dir (each only if present in backup).
4. Copy all `streak_*.yaml` files from backup to internal dir.
5. **Old format migration**: if `streak.yaml` (no language suffix) exists in backup, parse it to extract `languageId` field, then copy to `streak_<languageId>.yaml`. Falls back to `"en"` if languageId is missing or parsing fails.
6. Copy `profile.yaml` if present.
7. Return `true` on success, `false` on any exception.

#### restoreFromBackupUri(backupUri: Uri): Boolean

SAF (Storage Access Framework) restore. Used on Android 10+ with scoped storage.

1. Resolve `DocumentFile` from URI (tries tree URI first, then single URI).
2. Look for `backup_latest` subfolder; fall back to root if absent.
3. Create internal `grammarmate/` directory if needed.
4. Restore main files (`mastery.yaml`, `progress.yaml`, `profile.yaml`) via `ContentResolver.openInputStream`. Each file logs OK/MISSING/ERROR.
5. Restore streak files by iterating backup directory for `streak_*.yaml` pattern.
6. Migrate old `streak.yaml` format (same logic as path-based restore).
7. Parse and log mastery data analysis (language breakdown with unique/total shows, interval steps, days since last show).
8. Write a detailed restore log to `restore_log.txt` in the backup directory.
9. Return `true` if at least one file was copied.

**Restore log contents**:
- Timestamp and backup URI
- Per-file status (OK with byte count, MISSING, ERROR with message)
- Streak file count
- Old format migration result
- Mastery data analysis (per-language, per-lesson breakdown)
- Summary with restored/missing counts
- Final result: SUCCESS or FAILED

### 6.3.6 Backup discovery

| Method | API | Description |
|--------|-----|-------------|
| `getAvailableBackups(): List<BackupInfo>` | Legacy (file path) | Scans `Downloads/BaseGrammy/` for directories starting with `backup_` or named `backup_latest`. Sorted by modification time, newest first. |
| `getAvailableBackups(treeUri: Uri): List<BackupInfo>` | SAF (URI) | Scans a DocumentFile tree for backup directories. Same naming filter. |
| `hasBackup(): Boolean` | Legacy | Returns `true` if `getAvailableBackups()` is non-empty. |
| `deleteBackup(backupPath: String): Boolean` | Legacy | Recursively deletes the backup directory. |

**BackupInfo data class**:

```kotlin
data class BackupInfo(
    val name: String,          // Directory name (e.g., "backup_latest")
    val path: String,          // Absolute path (legacy) or empty
    val uri: String? = null,   // Content URI (SAF) or null
    val timestamp: String,     // "latest" for backup_latest, or extracted date string
    val dataSize: Long,        // Total size of all files in directory (0 on error)
    val metadata: String       // Contents of metadata.txt, or empty string
)
```

### 6.3.7 Error handling

| Scenario | Behavior |
|----------|----------|
| `createBackup()` throws any exception | Caught, logged via `Log.e`, returns `false` |
| `restoreFromBackup()` throws any exception | Caught, `printStackTrace()`, returns `false` |
| `restoreFromBackupUri()` throws any exception | Caught, logged to restore log file, `printStackTrace()`, returns `false` |
| Corrupt YAML in backup | File is still copied; corruption surfaces when the consuming store tries to parse it |
| Partial backup (missing files) | Restore proceeds with available files; missing files are logged |
| Backup directory inaccessible | `getAvailableBackups()` returns empty list; restore returns `false` |
| Scoped storage: MediaStore insert fails | Method returns `false` for that file, continues with others |

### 6.3.8 Known limitations

- Backups are not encrypted or compressed.
- There is no incremental or differential backup -- every backup is a full snapshot.
- The `metadata.txt` creation timestamp is formatted as `yyyy-MM-dd_HH-mm-ss` (minute precision, not seconds despite the format pattern).
- Metadata hardcodes "App version: 1.0" rather than reading the actual version from BuildConfig.
- The `createBackupScoped()` method may create "(1)" duplicate files in edge cases where both a MediaStore entry and a legacy filesystem file exist simultaneously -- the code attempts to clean these but the race condition cannot be fully eliminated.

---

## 6.4 AppConfigStore

### 6.4.1 Purpose

`AppConfigStore` manages runtime configuration flags stored in `grammarmate/config.yaml`. It provides typed access to configuration values with sensible defaults.

### 6.4.2 Config data class

```kotlin
data class AppConfig(
    val testMode: Boolean = false,
    val eliteSizeMultiplier: Double = 1.25,
    val vocabSprintLimit: Int = 20,
    val useOfflineAsr: Boolean = false
)
```

### 6.4.3 Config keys

| Key | Type | Default | Range / Constraints | Purpose |
|-----|------|---------|---------------------|---------|
| `testMode` | Boolean | `false` | `true` / `false` | Enables test/debug mode. When true, typically reduces card counts and relaxes constraints for faster testing cycles. |
| `eliteSizeMultiplier` | Double | `1.25` | Positive number | Multiplier for elite/daily practice session size. A value of 1.0 means standard size; higher values increase the number of cards per session. |
| `vocabSprintLimit` | Int | `20` | Positive integer | Maximum number of vocab flashcards in a daily practice session's vocab block. |
| `useOfflineAsr` | Boolean | `false` | `true` / `false` | When true, uses Sherpa-ONNX offline ASR instead of Android's built-in speech recognizer. |

### 6.4.4 config.yaml format

**Asset-shipped default** (`assets/grammarmate/config.yaml`):

```yaml
testMode: false
eliteSizeMultiplier: 1.25
vocabSprintLimit: 20
```

Note: the shipped default does **not** include `useOfflineAsr` -- that key was added later and relies on the default value in `AppConfig` and the fallback logic in `load()`.

**Full schema** (after first `save()` call):

```yaml
testMode: false
eliteSizeMultiplier: 1.25
vocabSprintLimit: 20
useOfflineAsr: false
```

The file is a flat YAML map (no nesting, no schema version header).

### 6.4.5 How config is loaded

`AppConfigStore` does **not** cache the config in memory. Each `load()` call reads from disk.

**Load algorithm** (`fun load(): AppConfig`):

1. Check if `grammarmate/config.yaml` exists.
2. **If file does not exist (first launch)**:
   a. Attempt to copy from `assets/grammarmate/config.yaml` via `AtomicFileWriter.writeText()`.
   b. If asset copy fails (e.g., asset missing): create a minimal config with `testMode=false, vocabSprintLimit=20` (note: this minimal fallback does not include `eliteSizeMultiplier` or `useOfflineAsr`, so their `AppConfig` defaults will be used).
3. Read file content with `file.readText()`.
4. Parse with SnakeYAML into `Map<*, *>`.
5. Extract each field with safe casts:
   - `testMode`: `as? Boolean ?: false`
   - `eliteSizeMultiplier`: `as? Number` then `.toDouble() ?: 1.25`
   - `vocabSprintLimit`: `as? Number` then `.toInt() ?: 20`
   - `useOfflineAsr`: `as? Boolean ?: false`
6. Return constructed `AppConfig`.

**Save algorithm** (`fun save(config: AppConfig)`):

1. Ensure `grammarmate/` directory exists.
2. Construct a `Map<String, Any>` from all four config fields.
3. Serialize via `yaml.dump()`.
4. Write via `AtomicFileWriter.writeText()`.

### 6.4.6 Design notes

- The store creates its own `Yaml()` instance (not shared).
- Config is not backed by `YamlListStore` because it is a flat map, not a list of items.
- There is no file watcher or reactive update mechanism. Config changes take effect only after the next `load()` call.
- `load()` performs a file-existence check on every invocation (no in-memory flag for "already seeded").

---

## 6.5 RestoreNotifier

### 6.5.1 Purpose

`RestoreNotifier` is a process-wide singleton that communicates backup restore state from background initialization code (in `MainActivity`) to the UI layer (`AppRoot` composable). It bridges the gap between imperative restore logic and Compose's reactive state model.

### 6.5.2 Public API

```kotlin
object RestoreNotifier {
    val restoreState: StateFlow<RestoreState>
    fun start()
    fun requireUser()
    fun markComplete(restored: Boolean)
}
```

| Method | Effect on RestoreState |
|--------|----------------------|
| `start()` | Sets `status = IN_PROGRESS`, increments `token` |
| `requireUser()` | Sets `status = NEEDS_USER`, increments `token` |
| `markComplete(restored)` | Sets `status = DONE`, sets `restored` flag, increments `token` |

`restoreState` is a `StateFlow<RestoreState>` that observers collect for reactive updates.

### 6.5.3 Data model

```kotlin
data class RestoreState(
    val token: Int = 0,           // Monotonically increasing change counter
    val status: RestoreStatus = RestoreStatus.IDLE,
    val restored: Boolean = false  // true only if restore actually restored data
)

enum class RestoreStatus {
    IDLE,          // No restore in progress (initial state)
    IN_PROGRESS,   // Restore operation is running
    NEEDS_USER,    // Restore needs user to select backup folder (SAF picker)
    DONE           // Restore finished (check `restored` for success)
}
```

### 6.5.4 State machine

```
IDLE ──start()──> IN_PROGRESS ──markComplete()──> DONE
                     │
                     └──requireUser()──> NEEDS_USER ──markComplete()──> DONE
```

The `token` field increments on every state transition. This allows UI observers to detect changes even if the status cycles back to the same value (e.g., two consecutive `DONE` states).

### 6.5.5 How UI consumes restore events

**AppRoot.kt** collects `RestoreNotifier.restoreState` as Compose state:

```kotlin
val restoreState by RestoreNotifier.restoreState.collectAsState()
if (restoreState.status == RestoreStatus.DONE) {
    // Show main app content
} else {
    StartupScreen(status = restoreState.status)
}
```

**StartupScreen** displays status text:
- `IN_PROGRESS` -> "Restoring backup..."
- `NEEDS_USER` -> "Waiting for backup folder..."
- `IDLE` -> "" (empty)

**MainActivity** is the producer. It calls:
- `RestoreNotifier.start()` before beginning restore checks.
- `RestoreNotifier.requireUser()` when it needs to launch the SAF folder picker.
- `RestoreNotifier.markComplete(restored)` when restore finishes (with `false` if no backup was found or restore failed).

### 6.5.6 Thread safety

`RestoreNotifier` uses `MutableStateFlow` which is thread-safe for concurrent reads and writes. State updates are atomic -- each `copy()` produces a new immutable `RestoreState` instance. The `token` increment is not atomic with the status change, but since all state transitions flow through a single `MutableStateFlow.value =` assignment, the published state is always consistent.

### 6.5.7 Design notes

- `RestoreNotifier` is a standalone singleton, not owned by any ViewModel. This is intentional: it must survive ViewModel recreation and communicate across the Activity/Compose boundary before the ViewModel is initialized.
- The `restored` boolean in `RestoreState` indicates whether data was actually restored (as opposed to "restore completed but no backup was found").
- There is no `reset()` method. Once `DONE`, the state persists until the process is recreated.
