# 20. Non-Functional Requirements -- Specification

This section specifies the non-functional qualities, constraints, and operational characteristics of GrammarMate. These requirements define how the system should behave rather than what it should do.

---

## 20.1 Performance Requirements

### 20.1.1 App Startup

| Metric | Target | Rationale |
|--------|--------|-----------|
| Cold start to interactive | < 2 seconds | Users open the app for quick daily practice sessions. |
| Warm start to interactive | < 500 ms | Resuming from background should feel instant. |
| First lesson pack seed (first launch) | < 3 seconds | Default packs are extracted from assets, not downloaded. |

**Contributing factors:**
- Default lesson packs ship as ZIP in `assets/grammarmate/packs/` and are extracted on first launch via `seedDefaultPacksIfNeeded()`.
- Seed is gated by a marker file `seed_v1.done` -- subsequent launches skip the seed entirely.
- YAML configuration is loaded from `assets/grammarmate/config.yaml` on first access via `AppConfigStore.load()`.
- No network calls occur at startup. TTS/ASR models are not loaded until the user enters a training session.

### 20.1.2 Screen Transitions and UI Responsiveness

| Metric | Target |
|--------|--------|
| Screen navigation (Compose recomposition) | 60 fps target; no frame drops > 16 ms during navigation |
| Card flip / state transitions | < 100 ms from action to visual feedback |
| Flower state rendering | < 16 ms per flower (up to ~30 flowers on roadmap screen) |

**Constraints:**
- `GrammarMateApp.kt` is a stateless renderer. All UI state flows from `StateFlow<TrainingUiState>`. Recomposition scope is scoped to changed fields.
- Jetpack Compose BOM 2024.02.00 with Material 3 provides smart recomposition. Derived state (e.g., flower calculations) should use `remember` + `derivedStateOf` to avoid recalculation on every frame.

### 20.1.3 File I/O Performance

| Operation | Expected Time | Notes |
|-----------|--------------|-------|
| Read lesson index (YAML, ~50 lessons) | < 5 ms | Parsed by SnakeYAML from internal storage |
| Read mastery data (YAML) | < 10 ms | Single file per language, cached in memory |
| Write mastery/progress (atomic) | < 20 ms | AtomicFileWriter: write temp, fsync, rename |
| Import lesson pack ZIP (50 KB - 5 MB) | < 1 second | Extract + parse + write index |
| Import lesson pack ZIP (large, 50+ MB) | < 10 seconds | Depends on device I/O; progress is not currently shown |

**Atomic write overhead:**
- `fsync()` adds ~5-15 ms per write depending on device. This is the cost of data integrity.
- All stores use `AtomicFileWriter.writeText()` which performs: write to `.tmp` -> `fd.sync()` -> rename to final file.
- Writes are synchronous and blocking on the calling coroutine. No write batching exists.

### 20.1.4 Audio Synthesis Latency (TTS)

| Metric | Target | Notes |
|--------|--------|-------|
| Engine initialization (model loaded) | < 3 seconds | Sherpa-ONNX OfflineTts loads model files from internal storage |
| First audio output after `speak()` | < 200 ms | Streaming callback architecture: audio plays as samples are generated |
| Continuous speech generation | Real-time or faster | Kokoro and VITS models use 4 CPU threads |
| Language switch (re-initialization) | < 3 seconds | Full release + reinitialize cycle |

**Architecture:**
- TTS uses streaming playback via `generateWithConfigAndCallback()`. Audio samples are written to `AudioTrack` in `MODE_STREAM` as they are generated. The user hears audio before the full sentence is synthesized.
- Speed is clamped to range `[0.3, 3.0]` via `safeSpeed.coerceIn()`.
- Audio focus is requested as `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` to allow brief audio ducking of other apps.

### 20.1.5 Speech Recognition Response Time (ASR)

| Metric | Target | Notes |
|--------|--------|-------|
| Engine initialization (Whisper Small) | < 5 seconds | Loads `small-encoder.int8.onnx` + `small-decoder.int8.onnx` + VAD model |
| Recording + recognition cycle | < 5 seconds total | VAD-based endpointing: records until silence after speech |
| Language switch (no reload) | < 100 ms | Uses `OfflineRecognizer.setConfig()` to change language parameter only |
| Max recording duration | 10 seconds (default) | Configurable via `maxDurationMs` parameter |
| No-speech timeout | 3 seconds | If no speech detected after 3 seconds, returns empty result |

**Architecture:**
- ASR uses Sherpa-ONNX Whisper Small (int8 quantized) for offline transcription.
- Voice Activity Detection (VAD) with Silero VAD handles endpointing: records until speech is detected and then silence follows.
- Recording at 16 kHz mono, 16-bit PCM. Minimum buffer size is 3200 samples (100 ms at 16 kHz).
- Recognition runs on `Dispatchers.Default` thread pool with 4 threads.
- `AsrState` state machine: IDLE -> INITIALIZING -> READY -> RECORDING -> RECOGNIZING -> READY (or ERROR).

### 20.1.6 Large Lesson Handling

| Scenario | Constraint | Behavior |
|----------|-----------|----------|
| Lesson with 300 cards (main + reserve pool) | No performance degradation | Main pool is first 150, reserve pool is remainder. Loading is a single CSV parse. |
| Lesson with 1000+ cards | Must not crash; may show slower parsing | CSV parsing is linear. Lesson index stores only metadata, not card data. Cards are parsed on demand when `getLessons()` is called. |
| ALL_MIXED mode with 500+ cards | Capped at 300 cards | Random selection of 300 cards from the full pool to prevent session overload. |
| Roadmap with 30+ lessons and flowers | Must render within 16 ms per frame | Flower calculations are lightweight (arithmetic only). Compose lazy column handles viewport recycling. |

---

## 20.2 Offline Capability

### 20.2.1 Fully Offline Operations

The following features work with zero network connectivity after initial model download:

| Feature | Offline? | Notes |
|---------|----------|-------|
| Lesson browsing and training | Fully offline | All lesson content is in local CSV/YAML files |
| Practice sessions (WORD_BANK, KEYBOARD) | Fully offline | No network dependency |
| Practice sessions (VOICE input) | Offline after ASR model download | Sherpa-ONNX Whisper runs on-device |
| TTS pronunciation playback | Offline after TTS model download | Sherpa-ONNX Kokoro/VITS runs on-device |
| Progress tracking and mastery | Fully offline | All state stored in YAML files locally |
| Flower state calculation | Fully offline | Pure arithmetic, no network |
| Backup creation | Fully offline | Writes to local Downloads/BaseGrammy/ |
| Settings management | Fully offline | Local config.yaml |
| Daily practice sessions | Fully offline | Combines translations, vocab, verbs from local data |
| Streak tracking | Fully offline | Date-based logic, no server sync |

### 20.2.2 Initial Download Requirements

The following components require a one-time network download before first use:

| Component | Download Size (approx.) | Storage Location | When Required |
|-----------|------------------------|-----------------|---------------|
| TTS model (per language) | Varies by model (Kokoro ~60-100 MB, VITS ~30-60 MB) | `context.filesDir/tts/<modelDir>/` | First time user taps "Listen" or enters VOICE mode |
| ASR model (Whisper Small) | ~150-200 MB | `context.filesDir/asr/<modelDir>/` | First time user enters VOICE input mode |
| VAD model (Silero) | ~2 MB | `context.filesDir/asr/<vadDir>/` | Bundled with ASR download |

**Download architecture:**
- Downloads use `HttpURLConnection` with redirect following (up to 5 redirects).
- Retry: up to 3 attempts with 2-second delay between retries.
- Timeout: 60 seconds connect, 120 seconds read.
- Storage check: download is refused if available storage is below `minRequiredBytes`.
- Metered network detection: `isNetworkMetered()` is checked but not enforced -- the UI may warn the user.
- Local file fallback: both `TtsModelManager` and `AsrModelManager` search `Downloads/BaseGrammy/` and app external storage for pre-placed model archives before attempting network download. This supports air-gapped deployment via sideloaded model files.
- Archives are `.tar.bz2`, extracted using Apache Commons Compress library.
- After successful extraction, the archive file in cache is deleted.

### 20.2.3 Graceful Degradation Without Network

| Condition | Behavior |
|-----------|----------|
| No network, TTS model not downloaded | TTS buttons are hidden or disabled. KEYBOARD and WORD_BANK modes work normally. |
| No network, ASR model not downloaded | VOICE input mode is unavailable. KEYBOARD and WORD_BANK modes work normally. |
| Network lost during model download | Download fails with `DownloadState.Error`. Partial archive is deleted. User can retry. |
| Network lost during app usage | No impact. All core functionality is offline. |

### 20.2.4 Data Storage Limits

| Data Type | Location | Typical Size | Growth Rate |
|-----------|----------|-------------|-------------|
| Lesson content (CSV) | `filesDir/grammarmate/lessons/<lang>/` | 50 KB - 5 MB per pack | Grows with imported packs |
| Lesson index (YAML) | `filesDir/grammarmate/lessons/<lang>_index.yaml` | 1-10 KB | Grows with number of lessons |
| Mastery data (YAML) | `filesDir/grammarmate/mastery.yaml` | 5-50 KB | Grows with number of lessons practiced |
| Progress data (YAML) | `filesDir/grammarmate/progress.yaml` | 1-5 KB | Fixed size (single session state) |
| Config (YAML) | `filesDir/grammarmate/config.yaml` | < 1 KB | Fixed |
| Profile (YAML) | `filesDir/grammarmate/profile.yaml` | < 1 KB | Fixed |
| Streak data (YAML) | `filesDir/grammarmate/streak_<lang>.yaml` | < 1 KB per language | Fixed per language |
| Pack manifests | `filesDir/grammarmate/packs/<packId>/` | 50 KB - 5 MB per pack | Grows with imported packs |
| Drill data | `filesDir/grammarmate/drills/<packId>/` | 10 KB - 1 MB per pack | Grows with drill content |
| TTS models | `filesDir/tts/<modelDir>/` | 30-100 MB per language | Fixed after download |
| ASR model | `filesDir/asr/<modelDir>/` | 150-200 MB | Fixed after download |
| Download cache | `cacheDir/*.tar.bz2` | 0 bytes after successful download | Temporary, deleted after extraction |

**Estimated total storage for 2 languages with TTS + ASR:** ~400-600 MB.

---

## 20.3 Data Integrity and Safety

### 20.3.1 AtomicFileWriter Guarantees

`AtomicFileWriter` provides the following guarantees for every write operation:

| Guarantee | Mechanism |
|-----------|-----------|
| No partial writes visible to readers | Content is written to a `.tmp` file first, then atomically renamed to the target file |
| Crash during write does not corrupt target | If the app crashes after step 4 (write to temp) but before step 8 (rename), the target file retains its previous complete content |
| `fsync` ensures durability | `FileDescriptor.sync()` is called before rename, forcing OS buffers to disk |
| Orphaned `.tmp` files are harmless | On next write, any existing `.tmp` file is deleted before use |

**The guarantee does NOT extend to:**
- Two concurrent writers to the same file. There is no locking mechanism. Concurrent writes to the same file may produce a lost update (last writer wins, but file is never corrupted).
- Atomicity across multiple files. Writing mastery.yaml and progress.yaml in sequence is not a single atomic transaction.

### 20.3.2 Crash Recovery Scenarios

| Crash Point | State After Recovery | Mechanism |
|-------------|---------------------|-----------|
| Mid-write (temp file written, not renamed) | Target file has previous complete content. Temp file is orphaned. | Next write cleans up orphaned `.tmp` file |
| Mid-rename (OS-level atomic rename interrupted) | Either target has old content or new content. Never a mix. | Rename is an atomic filesystem operation on ext4/f2fs |
| Mid-session (progress not saved) | Progress reverts to last saved state (typically last card transition) | `ProgressStore` saves on each card advance |
| Mid-pack import (ZIP partially extracted) | Incomplete data in temp directory. Pack index not updated. | Import writes to `tmp_<UUID>` directory first. Index is updated only after all files are successfully copied. On next launch, the orphaned temp dir is harmless. |
| Mid-backup restore | Partial files in internal storage. May have some files from new backup, some from old. | Backup restore copies files individually. If interrupted, the state is partially restored. User can retry restore. A restore log (`restore_log.txt`) is written for diagnostics. |
| App killed during TTS/ASR engine use | Native resources may leak. Audio record may be released by OS. | `release()` is called in ViewModel `onCleared()`. `AudioRecord` is released in `stopRecording()`. If force-killed, the OS reclaims resources. |

### 20.3.3 Backup Reliability

| Aspect | Behavior |
|--------|----------|
| Backup scope | Mastery data, progress data, streak files (per language), profile data. Does NOT back up lesson content (re-importable), TTS/ASR models (re-downloadable), or config (re-seeded from assets). |
| Backup location | `Downloads/BaseGrammy/backup_latest/` on shared storage |
| Android 10+ (scoped storage) | Uses `MediaStore.Downloads` API to write to `Downloads/BaseGrammy/backup_latest/`. Handles duplicate entries by querying and overwriting existing entries instead of inserting new ones. |
| Android 9 and below (legacy storage) | Uses direct `File` operations on `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)` |
| Restore source | User selects backup directory via SAF (Storage Access Framework) document picker |
| Restore overwrite | Restored files overwrite internal data files. Missing backup files are skipped (not an error). |
| Metadata | `metadata.txt` is written with timestamp, app version, and content listing |
| Restore log | `restore_log.txt` is written to the backup directory with detailed per-file results |
| Streak migration | Old single `streak.yaml` format is automatically migrated to `streak_<languageId>.yaml` format during restore |

### 20.3.4 Data Migration Between Versions

| Migration | Mechanism |
|-----------|-----------|
| Old YAML list format (bare list) to schema-wrapped format | `YamlListStore.read()` detects bare `List<*>` vs `Map<*,*>` with `schemaVersion` key and handles both transparently |
| Old `streak.yaml` to `streak_<languageId>.yaml` | `BackupManager.restoreFromBackup()` and `restoreFromBackupUri()` detect old format and migrate |
| Pack version update | `updateDefaultPacksIfNeeded()` compares `packVersion` in manifest. If different, re-imports the pack from assets |
| App reinstall | `forceReloadDefaultPacks()` removes old pack data and re-imports from assets |
| Missing config.yaml | `AppConfigStore.load()` seeds from `assets/grammarmate/config.yaml` on first access |
| AppScreen enum changes | `AppScreen.ELITE` and `AppScreen.VOCAB` enum values are retained for backward compatibility with saved state. They redirect to HOME if restored. |

### 20.3.5 No-Data-Loss Guarantees

**Guaranteed:**
- A completed practice session's mastery and progress data is persisted before the UI shows the result of the next action.
- File writes are atomic: a file is never in a partially-written state visible to readers.
- Backup contains all user progress (mastery, progress, streaks, profile).

**NOT guaranteed:**
- If the app is force-killed between card transitions, the last card's answer may not be persisted. The user loses at most one card's worth of progress.
- There is no undo mechanism for progress or mastery changes.
- There is no cloud sync or cross-device transfer mechanism.

---

## 20.4 Security Requirements

### 20.4.1 Local-Only Data Architecture

| Property | Implementation |
|----------|---------------|
| No server backend | The app has no backend API. All data is stored on-device in `context.filesDir/grammarmate/`. |
| No user accounts | No authentication, no user IDs, no cloud storage. |
| No analytics | No telemetry, crash reporting, or usage tracking services are integrated. |
| No third-party SDKs with network access | Dependencies are limited to: Jetpack Compose, SnakeYAML (parser only), Sherpa-ONNX (local inference), Apache Commons Compress (archive extraction). None make network calls from app code. |

### 20.4.2 Network Usage

| Use Case | Network Required | URLs |
|----------|-----------------|------|
| TTS model download | Yes (one-time) | URLs defined in `TtsModelRegistry` |
| ASR model download | Yes (one-time) | URLs defined in `AsrModelRegistry` |
| VAD model download | Yes (one-time) | URL defined in `AsrModelRegistry.VAD_MODEL_URL` |
| Lesson content | No | Bundled in assets or imported from local ZIP |
| Practice sessions | No | All processing is on-device |
| All other features | No | No network dependency |

The `INTERNET` and `ACCESS_NETWORK_STATE` permissions in the manifest exist solely for model downloads. If these downloads are done via pre-placed local files (sideload), the app functions with no network permission at all.

### 20.4.3 File Permission Model

| File Location | Visibility | Protection |
|---------------|-----------|------------|
| `context.filesDir/grammarmate/` (internal storage) | App-private. Not accessible to other apps or users without root. | Android sandbox default |
| `Downloads/BaseGrammy/` (backup location) | Public. Accessible to any app with storage permission. | User-initiated. Backup data is YAML plaintext, not encrypted. |
| `context.cacheDir/` (download archives) | App-private. May be cleared by OS when storage is low. | Android sandbox default |
| `assets/grammarmate/` (bundled content) | Read-only. Packaged in APK. | APK signature verification |

### 20.4.4 Backup File Security

| Property | Status |
|----------|--------|
| Encryption | Not implemented. Backup files are plaintext YAML. |
| Integrity verification | Not implemented. No checksums or signatures on backup data. |
| Tamper detection | Not implemented. A modified backup file will be restored as-is. |

**Risk assessment:** Since backup files contain only learning progress (no passwords, no PII beyond user-chosen display name), the lack of encryption is acceptable for the threat model of a single-user local device. The backup is in a public directory solely because Android scoped storage limits where the app can write recoverable data.

### 20.4.5 Input Sanitization (Imported Lesson Packs)

| Attack Vector | Mitigation |
|---------------|------------|
| ZIP path traversal | `extractZipToTemp()` canonicalizes both temp directory and output file paths. If the canonical target path does not start with the canonical parent path, the entry is rejected with an error. |
| Missing manifest | `importPackFromTempDir()` checks for `manifest.json` before proceeding. If absent, temp directory is deleted and an error is thrown. |
| Malformed CSV | `CsvParser.parseLesson()` processes line by line. Malformed lines are silently skipped (no crash). Empty files produce an empty card list. |
| Malformed JSON (manifest) | `LessonPackManifest.fromJson()` is wrapped in `runCatching` during reads. Parsing failures produce null results, not crashes. |
| Malformed YAML | SnakeYAML `load()` may return unexpected types. All stores use `as?` safe casts with null fallbacks. |
| Large ZIP bombs | No explicit size limit. A very large ZIP will cause `OutOfMemoryError` during extraction. This is a known limitation. |
| Malicious model files | TTS/ASR model files are validated for existence and non-zero size only (`file.length() == 0L` check). Content integrity (e.g., hash verification) is not implemented. Models are loaded from trusted URLs or user-placed files. |

---

## 20.5 Compatibility

### 20.5.1 Android SDK Support

| Property | Value |
|----------|-------|
| Minimum SDK (minSdk) | 24 (Android 7.0 Nougat) |
| Target SDK (targetSdk) | 34 (Android 14) |
| Compile SDK (compileSdk) | 34 |
| Java compatibility | Java 17 source and target |
| Kotlin JVM target | 17 |
| Kotlin version | 1.9.22 |
| Compose compiler extension | 1.5.8 |

**API level considerations:**
- SDK 24 covers ~97% of active Android devices as of 2026.
- SDK 34 is required for the latest permission model and scoped storage APIs.
- `Build.VERSION.SDK_INT >= 29` checks are used for scoped storage vs legacy storage fallbacks in `BackupManager`.
- `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O` (SDK 26) checks for `AudioFocusRequest` vs deprecated audio focus API in `TtsEngine`.

### 20.5.2 Device Requirements

| Requirement | Reason | Permission |
|-------------|--------|-----------|
| Internal storage | All app data stored in `context.filesDir` | Default app sandbox |
| Microphone | VOICE input mode (ASR) | `RECORD_AUDIO` (requested in manifest, runtime permission required) |
| Speaker / audio output | TTS playback | No special permission |
| External storage (read/write) | Backup to/from Downloads/BaseGrammy/ | `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` (legacy), `READ_MEDIA_*` (SDK 33+) |
| Network access | TTS/ASR model download | `INTERNET`, `ACCESS_NETWORK_STATE` |

**Devices without a microphone:** The app functions normally with KEYBOARD and WORD_BANK modes. VOICE mode is unavailable.

### 20.5.3 Screen Size Support

| Property | Implementation |
|----------|---------------|
| Layout system | Jetpack Compose with `Material3` adaptive layouts |
| Primary target | Phone form factor (portrait) |
| Orientation | Portrait-only is the primary design target. No explicit lock; landscape may work but is not tested. |
| Screen density | Vector drawables via `useSupportLibrary = true`. Compose handles DP scaling automatically. |
| Text scaling | Compose `MaterialTheme.typography` respects user font size settings. No explicit maximum text scale clamp (may cause layout overflow at extreme scales). |

### 20.5.4 Language and Locale Support

| Aspect | Status |
|--------|--------|
| App UI language | Russian (primary). No localization framework. UI strings are hardcoded in Russian. |
| Learning content languages | English (`en`) and Italian (`it`) are the currently supported target languages. |
| Source language for prompts | Russian (`ru`). All prompts are in Russian. |
| Runtime language addition | `LessonStore.addLanguage()` supports adding arbitrary languages by ID. |
| TTS model availability | Depends on `TtsModelRegistry` entries. New languages require a corresponding model registration. |
| ASR language switching | Whisper Small multilingual model supports multiple languages via `setLanguage()`. Language is mapped through `AsrModelRegistry.whisperLanguageCode()`. |
| CSV content encoding | UTF-8. `CsvParser` handles UTF-8 BOM (`﻿`) stripping. |

---

## 20.6 Reliability

### 20.6.1 Error Handling Philosophy

GrammarMate follows a **fail-safe, degrade-gracefully** pattern:

| Principle | Implementation |
|-----------|---------------|
| Never crash on bad data | All YAML/JSON/CSV parsing uses safe casts (`as?`), `runCatching`, and null fallbacks. Corrupted files produce empty/default data, not exceptions. |
| Never lose existing data on write failure | `AtomicFileWriter` ensures the target file always contains either the old or new complete content. |
| Silent fallback for non-critical operations | If TTS/ASR initialization fails, the engine enters ERROR state with a human-readable `errorMessage`. The app continues without audio. |
| Surface errors for user-initiated operations | Pack import, backup restore, and model download show error messages to the user via `DownloadState.Error` and UI toasts/dialogs. |
| No global error handler | There is no `Thread.setDefaultUncaughtExceptionHandler`. Unhandled exceptions crash the app and are logged to logcat only. |

### 20.6.2 Fallback Behaviors

| Component | Primary Behavior | Fallback |
|-----------|-----------------|----------|
| TTS engine | Speak text via Sherpa-ONNX | If initialization fails, TTS state = ERROR. Audio playback is skipped silently. |
| ASR engine | Record + transcribe via Whisper + VAD | If initialization fails, ASR state = ERROR. VOICE input mode is unavailable. |
| TTS model download | Download from URL | Retry up to 3 times. If all fail, show error. Also checks local `Downloads/BaseGrammy/` for pre-placed archive. |
| ASR model download | Download from URL | Same retry + local fallback strategy as TTS. |
| Lesson content | Parse from CSV | If CSV is malformed, return empty card list. Lesson appears but has no cards. |
| Mastery data | Load from YAML | If file is missing, return default empty mastery. If corrupted, return default. |
| Config | Load from YAML | If missing, seed from `assets/grammarmate/config.yaml`. If that also fails, create with hardcoded defaults. |
| Backup restore | Copy files from selected backup | If a file is missing in backup, skip it. Log the skip in `restore_log.txt`. |
| Audio focus | Request via AudioManager | If focus request fails (exception caught), proceed with playback anyway. Logged as warning. |

### 20.6.3 Logging Strategy

| Component | Log Level | Tag | Content |
|-----------|----------|-----|---------|
| TTS engine | `Log.d` (init, speak), `Log.e` (errors), `Log.w` (focus) | `TtsEngine` | Language changes, initialization status, playback failures |
| ASR engine | `Log.d` (init, result), `Log.e` (errors), `Log.w` (state) | `AsrEngine` | Recognition results, state transitions, initialization failures |
| Backup manager | `Log.d` (success), `Log.e` (failure) | `BackupManager` | Backup creation, file overwrites, duplicate cleanup |
| Lesson store | No logging | - | Silent failure with `runCatching` |
| Model managers | `Log.d` (progress), `Log.w` (retry), `Log.e` (failure), `Log.i` (local fallback) | `TtsModelManager`, `AsrModelManager` | Download attempts, extraction progress, local file discovery |

**Logging limitations:**
- No persistent log file. Logs go to Android logcat only (cleared on reboot).
- No remote crash reporting. Developers must reproduce issues locally.
- Verbose logging is always on (`debug = false` in Sherpa-ONNX config suppresses native logging, but Kotlin-level logging is not gated by BuildConfig).

### 20.6.4 Crash Prevention Measures

| Measure | Implementation |
|---------|---------------|
| Null safety | Kotlin null-safe types throughout. `as?` safe casts for all YAML/JSON deserialization. |
| Safe collection access | `firstOrNull()`, `getOrNull()`, `?.let {}` patterns used throughout stores. |
| Exception swallowing in non-critical paths | `try { ... } catch (_: Exception) {}` in audio release, focus abandon, recording stop. These are cleanup operations where exceptions are harmless. |
| State machine guards | TTS (`TtsState`) and ASR (`AsrState`) use `compareAndSet` to prevent double-initialization. ASR records only in READY state. |
| Coroutine cancellation | `isActive` checks in recording loops. `CancellationException` is re-thrown (never swallowed). |
| Audio resource cleanup | `AudioTrack.release()` and `AudioRecord.release()` in `finally` blocks. `currentTrack` is nulled after release. |
| File canonicalization | ZIP extraction canonicalizes paths to prevent traversal attacks that could cause unexpected file operations. |

---

## 20.7 Maintainability

### 20.7.1 Code Organization (MVVM Pattern)

```
GrammarMate
├── data/                          # Data layer: stores, parsers, calculators
│   ├── Models.kt                  # Data classes, enums, state types
│   ├── AtomicFileWriter.kt        # Safe file write primitive
│   ├── YamlListStore.kt           # Generic YAML-backed list storage
│   ├── LessonStore.kt             # Lesson pack import/export
│   ├── MasteryStore.kt            # Card mastery tracking
│   ├── ProgressStore.kt           # Training session state
│   ├── AppConfigStore.kt          # Runtime configuration
│   ├── BackupManager.kt           # Backup/restore
│   ├── SpacedRepetitionConfig.kt  # Ebbinghaus algorithm
│   ├── FlowerCalculator.kt        # Flower state calculation
│   ├── MixedReviewScheduler.kt    # Sub-lesson scheduling
│   ├── LessonLadderCalculator.kt  # Lesson unlock order
│   ├── CsvParser.kt               # Lesson CSV parsing
│   ├── VocabCsvParser.kt          # Vocab CSV parsing
│   ├── TtsEngine.kt               # Text-to-speech
│   ├── TtsModelManager.kt         # TTS model download
│   ├── AsrEngine.kt               # Speech recognition
│   ├── AsrModelManager.kt         # ASR model download
│   └── ...                        # Other data files
├── ui/
│   ├── GrammarMateApp.kt          # Screen router, dialog orchestration
│   ├── TrainingViewModel.kt       # Single ViewModel (all business logic)
│   ├── DailyPracticeScreen.kt     # Daily practice UI
│   ├── AppRoot.kt                 # Entry point
│   ├── Theme.kt                   # Material 3 theme
│   ├── screens/                   # Per-screen composables
│   ├── components/                # Shared composables
│   └── helpers/                   # ViewModel domain helpers
└── MainActivity.kt                # Android activity
```

**Architectural constraints:**
- Single ViewModel: `TrainingViewModel` is the only ViewModel. It holds all training state in `StateFlow<TrainingUiState>`.
- Stateless UI: `GrammarMateApp.kt` is a pure stateless renderer that collects the state flow and dispatches actions.
- Helper pattern: Domain helpers in `ui/helpers/` are plain Kotlin classes implementing `TrainingStateAccess` interface. They are owned by the ViewModel and call `updateState { }` and `saveProgress()` through it.
- No helper-to-helper calls: All coordination flows through TrainingViewModel.

### 20.7.2 Test Coverage

**Current state (as of 2026-01-16):**

| Category | Coverage | Critical Gaps |
|----------|----------|---------------|
| CSV/Vocab parsing | 75-85% | Missing: multiple accepted answers, empty lines |
| Sub-lesson scheduler | 85% | Missing: warmup size |
| YAML list store | 70% | Missing: UTF-8 encoding |
| Atomic file writer | 40% | Missing: concurrent writes, error cleanup, temp file lifecycle |
| Profile store | 0% | All functionality untested |
| Progress store | 0% | All functionality untested |
| Mastery store | 0% | All functionality untested |
| Flower calculator | 0% | All functionality untested |
| Spaced repetition config | 0% | All functionality untested |
| Answer normalizer | 0% | All functionality untested |
| Lesson store | 0% | All functionality untested |
| Streak store | 0% | All functionality untested |
| Training viewmodel | 0% | All functionality untested |
| Backup manager | 0% | All functionality untested |
| App config store | 0% | All functionality untested |
| UI rendering | 0% | All functionality untested |

**Overall estimated coverage: ~12% of ~200 functional requirements.**

**Test infrastructure:**
- Testing framework: JUnit 4.13.2
- Robolectric 4.11.1 for Android context in unit tests
- `org.json:json:20231013` for JSON test assertions
- `androidx.test:core:1.5.0` for AndroidX test support
- No UI testing framework (Espresso, Compose Testing) is configured
- No mocking framework (Mockito) is currently in dependencies
- No property-based testing framework (Kotest) is currently in dependencies
- All tests are local JVM unit tests (no instrumented tests)

### 20.7.3 File Size Limits and Decomposition Rules

| Layer | Max Lines | Action When Exceeded |
|-------|-----------|---------------------|
| Screen file (Compose) | 1000 lines | Extract sub-composables to `ui/components/` |
| ViewModel | 1200 lines | Extract domain helpers to `ui/helpers/` |
| Data store | 500 lines | Extract parsers or calculators to separate files in `data/` |
| Data class (single class) | 30 fields | Group related fields into nested data classes |

**Current status:**
- `TrainingViewModel` is documented as 3000+ lines (significantly over the 1200-line limit). Decomposition into helpers is ongoing.
- `LessonStore` is ~950 lines (approaching the 500-line data store limit).

### 20.7.4 Dependency Management

| Dependency | Version | Purpose |
|-----------|---------|---------|
| Kotlin | 1.9.22 | Language |
| Jetpack Compose BOM | 2024.02.00 | UI framework |
| Material 3 | (via BOM) | Design system |
| Navigation Compose | 2.7.7 | Screen navigation |
| SnakeYAML | 2.2 | YAML serialization |
| Sherpa-ONNX | 1.12.40 (AAR) | TTS/ASR inference |
| Apache Commons Compress | 1.26.1 | tar.bz2 extraction |
| Activity Compose | 1.8.2 | Activity integration |
| Lifecycle Runtime KTX | 2.7.0 | Lifecycle-aware components |
| Core KTX | 1.12.0 | Android extensions |

**Dependency policy:**
- Sherpa-ONNX is included as a local AAR in `libs/` (not from Maven). Updates require manual replacement.
- No dependency injection framework. All dependencies are constructed manually.
- No build variant management beyond debug/release. No flavor dimensions.

---

## 20.8 Accessibility

### 20.8.1 Text Scale Support

| Aspect | Status |
|--------|--------|
| Compose typography | Uses `MaterialTheme.typography` which respects user font scale settings |
| Maximum text scale | No explicit clamp. Extremely large text scales may cause layout overflow, particularly in card layouts with fixed padding |
| Dynamic font size | Supported via Compose default behavior |

### 20.8.2 Color Contrast

| Aspect | Status |
|--------|--------|
| Color scheme | Material 3 dynamic color theming |
| Dark mode | Not explicitly supported. App uses light theme only. `Theme.kt` does not check `isSystemInDarkTheme()`. |
| Color-only indicators | Flower states are represented by both emoji AND color, providing non-color-dependent differentiation |

### 20.8.3 Screen Reader Support

| Aspect | Status |
|--------|--------|
| Content descriptions | Not systematically applied. Compose components use default semantics. |
| Semantic tree | Not explicitly customized. Standard Compose semantics apply. |
| Focus navigation | Default Compose focus order. No custom accessibility focus ordering. |

### 20.8.4 Voice Input as Accessibility Feature

Voice input mode (ASR) serves a dual purpose as an accessibility feature:
- Users who cannot type can speak their answers.
- The ASR engine supports multiple languages via Whisper's multilingual model.
- Voice input mode contributes to mastery tracking (VOICE mode counts toward flower growth, unlike WORD_BANK).

---

## 20.9 Build and Deployment

### 20.9.1 Build Process

| Step | Command | Notes |
|------|---------|-------|
| Build debug APK | `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug` | Windows multi-JAR workaround required |
| Build release APK | Same with `assembleRelease` | No minification (`isMinifyEnabled = false`) |
| Run unit tests | Same with `test` | Local JVM only, no instrumented tests |
| Run single test | Same with `--tests "com.alexpo.grammermate.data.ClassName.methodName"` | |
| Validate lesson pack | `python tools/pack_validator/pack_validator.py path/to/pack.zip` | External Python tool |
| Clean | Same with `clean` | |

**Windows Gradle wrapper workaround:**
- Gradle 8.9 distributes as 3 JAR files: `gradle-wrapper.jar`, `gradle-wrapper-shared.jar`, `gradle-cli.jar`.
- The standard `gradlew.bat` script only loads `gradle-wrapper.jar`, causing `NoClassDefFoundError: org/gradle/wrapper/IDownload`.
- Workaround: invoke Java directly with all 3 JARs in the classpath.
- A convenience `build.bat` can be created: `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain %*`

**Cyrillic path handling:**
- Project path may contain Cyrillic characters (e.g., `D:\Разработка\BaseGrammy`).
- `android.overridePathCheck=true` in `gradle.properties` disables Gradle's non-ASCII path check.

### 20.9.2 APK Details

| Property | Value |
|----------|-------|
| Application ID | `com.alexpo.grammermate` |
| Version code | 4 |
| Version name | 1.4.1 |
| Debug APK output | `app/build/outputs/apk/debug/grammermate.apk` (renamed from `app-debug.apk` via build task) |
| Release APK output | `app/build/outputs/apk/release/app-release.apk` |
| Minification | Disabled (`isMinifyEnabled = false`) |
| ProGuard | Configured but inactive (only runs when minification is enabled) |
| Native libraries | Sherpa-ONNX AAR contains static-linked ONNX Runtime (included in APK) |
| Packaged assets | Default lesson packs in `assets/grammarmate/packs/`, config in `assets/grammarmate/config.yaml` |

**APK size considerations:**
- Sherpa-ONNX AAR adds significant size (ONNX Runtime + native libs for arm64-v8a, armeabi-v7a, x86_64).
- TTS and ASR models are NOT bundled in the APK. They are downloaded on demand after installation.
- Default lesson packs (ZIP files in assets) add minimal size (typically < 5 MB total).
- No code shrinking or resource shrinking is applied.

### 20.9.3 Signing

| Property | Status |
|----------|--------|
| Debug signing | Default Android debug keystore |
| Release signing | Not configured in `build.gradle.kts`. Release APK is signed with debug key unless a signing config is provided. |
| Signing config | No `signingConfigs` block in build file. |

### 20.9.4 Version Management

| Convention | Implementation |
|------------|---------------|
| Version code | Manual integer in `defaultConfig`. Incremented for each release. |
| Version name | SemVer-like string (e.g., `1.4.1`). Manual update. |
| No CI/CD | All builds are local. No automated version bumping. |
| Git tags | Not used for versioning in the current workflow. |

---

## 20.10 Scalability

### 20.10.1 Maximum Number of Lesson Packs

| Metric | Constraint | Rationale |
|--------|-----------|-----------|
| Installed packs | No hard limit. Practical limit is device storage. | Each pack is stored in `filesDir/grammarmate/packs/<packId>/`. Pack registry is `packs.yaml` loaded entirely into memory. |
| Default packs | 2 currently (`EN_WORD_ORDER_A1`, `IT_VERB_GROUPS_ALL`) | Defined in `LessonStore.defaultPacks`. Adding more requires code change + asset file. |
| Pack registry in memory | All pack entries loaded as a list of maps | Negligible memory for dozens of packs. May become a concern for hundreds (untested). |

### 20.10.2 Maximum Cards Per Lesson

| Metric | Constraint | Rationale |
|--------|-----------|-----------|
| Cards per lesson | No hard limit. Practical limit is device memory for CSV parsing. | `CsvParser.parseLesson()` reads entire CSV into memory. A 10,000-card CSV at ~100 bytes/card = ~1 MB. |
| Main pool | First 150 cards (hardcoded as `MASTERY_THRESHOLD`) | Used for mastery calculation and flower growth |
| Reserve pool | All cards beyond 150 | Used in MIXED review to prevent phrase memorization |
| ALL_MIXED mode | Capped at 300 cards | Random selection from all available cards |
| Sub-lesson size | ~10 cards per sub-lesson | Determined by `MixedReviewScheduler` |
| Card ID uniqueness | Guaranteed via `${lessonId}_${index}` prefixing during import | Prevents ID collisions across lessons |

### 20.10.3 Maximum Languages Supported

| Metric | Constraint | Rationale |
|--------|-----------|-----------|
| Learning languages | No hard limit in data model | `Language` entries are stored in `languages.yaml`. `addLanguage()` generates unique IDs. |
| TTS model availability | Limited by `TtsModelRegistry` entries | Each language needs a registered TTS model spec with download URL. Currently: English (Kokoro), Italian (VITS). |
| ASR language support | Limited by Whisper multilingual vocabulary | `AsrModelRegistry.whisperLanguageCode()` maps ISO 639-1 codes to Whisper codes. The Whisper Small model supports many languages inherently. |
| UI language | Russian only | No localization framework. Adding UI languages requires string resource extraction. |

### 20.10.4 Storage Growth Over Time

| Data Type | Growth Model | Steady-State Size |
|-----------|-------------|------------------|
| Lesson content | Fixed after import | ~5-50 MB for a typical multi-pack installation |
| Mastery data | Grows with number of lessons practiced, ~100 bytes per lesson | ~10 KB for 100 lessons |
| Progress data | Fixed size (single session) | ~2 KB |
| Streak data | Fixed per language | ~500 bytes per language |
| Config | Fixed | ~200 bytes |
| Profile | Fixed | ~100 bytes |
| TTS models | Fixed per language after download | ~60-100 MB per language |
| ASR model | Fixed after download | ~150-200 MB (shared across languages) |
| Backup files | Grows with backup frequency (user-controlled) | Each backup is ~20 KB |
| Drill progress | Grows with drill usage, ~50 bytes per verb/word | ~50 KB for extensive use |

**Expected storage after 1 year of regular use with 2 languages:** ~600 MB (dominated by TTS + ASR models). App-specific data (excluding models) is typically under 100 MB.

### 20.10.5 Memory Usage at Runtime

| Component | Approximate Memory | Notes |
|-----------|-------------------|-------|
| TTS engine (active) | ~100-200 MB | Whisper Small model loaded in memory |
| ASR engine (active) | ~100-200 MB | Kokoro/VITS model loaded in memory |
| Both engines simultaneously | ~200-400 MB | Both engines can be active during VOICE practice |
| Lesson cards in memory | ~10-500 KB | Depends on lesson size. Cards are simple data classes with strings. |
| UI state | ~1-5 MB | `TrainingUiState` with all session data |
| YAML data files | ~100-500 KB | Loaded into memory as `Map<String, Any>` structures |

**Concern:** Loading both TTS and ASR engines simultaneously may be memory-intensive on low-end devices (2 GB RAM). The engines are initialized lazily (only when needed) and can be released via `release()`. There is no automatic memory pressure handler.
