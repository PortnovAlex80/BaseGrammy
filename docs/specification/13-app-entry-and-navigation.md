# 13. App Entry & Navigation — Specification

## 13.1 MainActivity

### 13.1.1 Class definition

`MainActivity` extends `ComponentActivity`. It is the sole activity in the app, declared in `AndroidManifest.xml` as the launcher activity (`android.intent.action.MAIN` + `android.intent.category.LAUNCHER`). There are no other activities, no deep link intent-filters, and no custom Application class.

**Source:** `app/src/main/java/com/alexpo/grammermate/MainActivity.kt`

### 13.1.2 Lifecycle management

`MainActivity` overrides only two lifecycle methods:

1. **`onCreate(savedInstanceState: Bundle?)`** — the sole entry point. All initialization happens here. There is no `onStart`, `onResume`, `onPause`, `onStop`, or `onDestroy` override. The activity relies entirely on the default `ComponentActivity` lifecycle behavior.

2. **`onRequestPermissionsResult(requestCode, permissions, grantResults)`** — handles the async result of runtime storage permission requests. Filters on `storagePermissionsRequestCode` (constant `101`). If granted, rechecks whether a restore is needed and calls `startLegacyRestore`. If denied, calls `RestoreNotifier.markComplete(false)` so the app proceeds without restoring.

No other lifecycle callbacks are used. The activity does not manage any background services, broadcast receivers, or content providers.

### 13.1.3 ViewModel initialization

`MainActivity` does **not** directly instantiate or reference `TrainingViewModel`. The ViewModel is created lazily by Jetpack Compose's `viewModel()` function inside `GrammarMateApp()` composable. This means:

- The ViewModel is scoped to the activity's `ViewModelStore` (tied to the activity lifecycle).
- It is created the first time `GrammarMateApp()` composable calls `viewModel()`, which happens after `AppRoot` passes the restore check.
- The ViewModel survives configuration changes (see section 13.5).

### 13.1.4 Compose wiring

The Compose UI tree is set up with a single `setContent` call at the end of `onCreate`:

```kotlin
setContent {
    AppRoot()
}
```

This makes `AppRoot` the root composable. There is no `ComposeView`, no `ComposeViewBinding`, and no XML layout. The entire UI is Compose-native.

The call to `setContent` happens **regardless** of whether restore has finished — the restore flow runs concurrently, communicating state to the UI via `RestoreNotifier` (a singleton `StateFlow`).

### 13.1.5 Intent handling

The manifest declares only the standard launcher intent-filter. There are:
- **No deep links** (no `<intent-filter>` with custom schemes, hosts, or paths).
- **No `ACTION_VIEW` handling** for any MIME type.
- **No `onNewIntent` override** — the activity does not handle re-delivery of intents.
- **No `getIntent()` inspection** in `onCreate` — the activity ignores the launching intent entirely.

All data input happens through the in-app UI (lesson pack import via Settings sheet).

### 13.1.6 Backup restore launch logic

`onCreate` contains the primary restore orchestration. It follows a branching decision tree:

#### Data existence check

Three sentinel files are checked in `filesDir/grammarmate/`:
- `mastery.yaml`
- `progress.yaml`
- `profile.yaml`

`hasFullData` is `true` only when **all three** exist. If any is missing, `shouldRestore` becomes `true`.

#### Decision tree

```
IF storedTreeUri != null (SAF URI previously persisted):
    IF shouldRestore:
        -> startRestoreFromUri(Uri.parse(storedTreeUri))
    ELSE:
        -> RestoreNotifier.markComplete(false)
        -> app proceeds normally

ELSE IF SDK >= 29 (Android 10+):
    IF shouldRestore:
        -> RestoreNotifier.requireUser()
        -> openBackupTreeLauncher.launch(null)  // SAF folder picker
    ELSE:
        -> RestoreNotifier.markComplete(false)

ELSE IF hasStoragePermissions():
    -> startLegacyRestore(shouldRestore)

ELSE:
    -> RestoreNotifier.requireUser()
    -> requestStoragePermissions()
```

#### Storage permission strategy

The permission set varies by API level:
- **API 33+** (Android 13+): `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`
- **Below API 33**: `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`

These are requested only on pre-Android-10 devices that lack a persisted SAF URI. Modern devices (API 29+) use SAF exclusively.

#### SAF tree URI persistence

When the user selects a backup folder via the SAF picker:
1. Persistable URI permissions are taken (`FLAG_GRANT_READ_URI_PERMISSION | FLAG_GRANT_WRITE_URI_PERMISSION`).
2. The URI is stored in `SharedPreferences("backup_prefs")` under key `"backup_tree_uri"`.
3. On subsequent launches, the stored URI is reused automatically without prompting the user.

### 13.1.7 Restore execution

Two restore paths exist:

1. **SAF-based** (`startRestoreFromUri(uri)`): Uses `BackupManager.restoreFromBackupUri(uri)` which reads files via `ContentResolver` + `DocumentFile`. Runs on `Dispatchers.IO` via `lifecycleScope.launch`. The method attempts to find a `backup_latest` subfolder within the selected tree, falling back to the tree root.

2. **Legacy file-based** (`startLegacyRestore(shouldRestore)`): Uses `BackupManager` methods operating directly on `java.io.File` paths in `Downloads/BaseGrammy/`. Only runs on pre-Android-10 devices or when `shouldRestore` is false (in which case it short-circuits immediately).

Both paths call `RestoreNotifier.start()` before beginning and `RestoreNotifier.markComplete(restored: Boolean)` when done. Exceptions are caught and logged, always resulting in `markComplete(false)`.

### 13.1.8 Class members summary

| Member | Type | Purpose |
|--------|------|---------|
| `storagePermissionsRequestCode` | `Int = 101` | Request code for runtime permission dialog |
| `backupTreeUriKey` | `String = "backup_tree_uri"` | SharedPreferences key for persisted SAF URI |
| `backupManager` | `Lazy<BackupManager>` | Backup/restore operations (lazy to defer context usage) |
| `openBackupTreeLauncher` | `ActivityResultLauncher<Uri?>` | SAF folder picker registered in `registerForActivityResult` |

---

## 13.2 AppRoot

### 13.2.1 Purpose

`AppRoot` is the gatekeeper composable between `MainActivity.setContent` and the main application UI. Its sole responsibility is to block rendering of `GrammarMateApp` until the restore process (if any) has completed. It wraps the entire app in `GrammarMateTheme`.

**Source:** `app/src/main/java/com/alexpo/grammermate/ui/AppRoot.kt`

### 13.2.2 Backup restore check flow

`AppRoot` collects `RestoreNotifier.restoreState` as Compose state. The logic is:

```kotlin
val restoreState by RestoreNotifier.restoreState.collectAsState()
if (restoreState.status == RestoreStatus.DONE) {
    GrammarMateApp()
} else {
    StartupScreen(status = restoreState.status)
}
```

The check is purely status-based:
- **`DONE`**: render `GrammarMateApp()` — the main app.
- **Any other status** (`IDLE`, `IN_PROGRESS`, `NEEDS_USER`): render `StartupScreen`.

There is no timeout, no retry logic, and no user interaction on the `StartupScreen` beyond waiting.

### 13.2.3 StartupScreen

The `StartupScreen` composable is private to `AppRoot.kt`. It displays:
- A centered `CircularProgressIndicator`.
- A text message that varies by `RestoreStatus`:
  - `IN_PROGRESS` → "Restoring backup..."
  - `NEEDS_USER` → "Waiting for backup folder..."
  - Default (including `IDLE`) → "Preparing..."

The screen fills the entire available space (`fillMaxSize`) and uses `MaterialTheme.typography.bodyMedium`. There is no cancel button and no way to skip the restore.

### 13.2.4 RestoreNotifier integration

`RestoreNotifier` is a Kotlin `object` (singleton) that acts as a bridge between the imperative restore code in `MainActivity` and the reactive Compose UI. It exposes a `StateFlow<RestoreState>`.

**RestoreState data class:**

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| `token` | `Int` | `0` | Monotonically incrementing counter on each state change; enables recomposition triggers |
| `status` | `RestoreStatus` | `IDLE` | Current restore phase |
| `restored` | `Boolean` | `false` | Whether restore actually succeeded (only meaningful when `status == DONE`) |

**RestoreStatus enum:**

| Value | Meaning | Who sets it |
|-------|---------|-------------|
| `IDLE` | No restore has been initiated yet | Default |
| `IN_PROGRESS` | Restore is running on a background coroutine | `RestoreNotifier.start()` |
| `NEEDS_USER` | Waiting for user action (SAF folder picker or permission grant) | `RestoreNotifier.requireUser()` |
| `DONE` | Restore finished (or was skipped/failed) | `RestoreNotifier.markComplete(restored)` |

**Callers in `MainActivity`:**

| Location | Call | Effect |
|----------|------|--------|
| SAF URI available + data exists | `markComplete(false)` | Skip restore, show app |
| SAF URI available + no data | `startRestoreFromUri(uri)` | Restore from URI |
| SAF picker canceled | `markComplete(false)` | Proceed without restore |
| SDK 29+ + data exists | `markComplete(false)` | Skip restore |
| SDK 29+ + no data | `requireUser()` + launch SAF picker | Wait for user |
| Legacy + permissions granted + restore needed | `start()` | Begin restore |
| Legacy + permissions denied | `markComplete(false)` | Proceed without restore |
| Restore success | `markComplete(true)` | Show app |
| Restore exception | `markComplete(false)` | Show app (no data restored) |

The `token` field ensures that even repeated calls with the same status (e.g., `markComplete(false)` called twice) trigger a recomposition because the token increments each time.

---

## 13.3 Navigation Architecture

### 13.3.1 Navigation graph

Navigation is **entirely state-based** using a local `var screen by remember { mutableStateOf(...) }` inside `GrammarMateApp()`. There is no Jetpack Navigation library, no `NavController`, no `NavHost`, and no route-based navigation. All screen transitions happen by mutating this single enum variable.

**AppScreen enum (private to `GrammarMateApp.kt`):**

| Screen | Description | Parent |
|--------|-------------|--------|
| `HOME` | Home screen with lesson tiles, daily practice, and drill tiles | Root |
| `LESSON` | Lesson roadmap showing sub-lessons, boss battles, drills | HOME |
| `TRAINING` | Active training session (sub-lesson or boss) | LESSON |
| `DAILY_PRACTICE` | Daily practice session (3-block: sentences, vocab, verbs) | HOME |
| `STORY` | Story quiz (check-in/check-out phase) | LESSON |
| `LADDER` | Lesson ladder (unlock progression visualization) | Any (via settings) |
| `VERB_DRILL` | Verb conjugation drill | HOME |
| `VOCAB_DRILL` | Vocabulary flashcard drill | HOME |
| `ELITE` | **Deprecated** — redirects to HOME immediately | N/A |
| `VOCAB` | **Deprecated** — redirects to HOME immediately | N/A |

### 13.3.2 Complete transition map

```
HOME ─────────────────────────────────────────────────────┐
  │  selectLesson                                          │
  ├─────────────────────► LESSON                           │
  │                         │  selectSubLesson              │
  │                         ├──────────────► TRAINING       │
  │                         │  startBossLesson              │
  │                         ├──────────────► TRAINING       │
  │                         │  startBossMega                │
  │                         ├──────────────► TRAINING       │
  │                         │  drillStart                   │
  │                         ├──────────────► TRAINING       │
  │                         │  story check-in/out           │
  │                         ├──────────────► STORY          │
  │                         │  back                         │
  │                         ◄────────────── HOME            │
  │                                                        │
  │  openElite (daily practice)                            │
  ├─────────────────────► DAILY_PRACTICE                   │
  │                         │  exit/complete                │
  │                         ◄────────────── HOME            │
  │                                                        │
  │  openVerbDrill                                         │
  ├─────────────────────► VERB_DRILL                       │
  │                         │  back                         │
  │                         ◄────────────── HOME            │
  │                                                        │
  │  openVocabDrill                                        │
  ├─────────────────────► VOCAB_DRILL                      │
  │                         │  back                         │
  │                         ◄────────────── HOME            │
  │                                                        │
  │  settings (any screen)                                 │
  ├─────────────────────► LADDER                           │
  │                         │  back                         │
  │                         ◄──── previousScreen            │
  │                                                        │
  │  primaryAction                                         │
  └─────────────────────► LESSON                           │
                                                           │
  ELITE ────────────────► HOME (immediate redirect)        │
  VOCAB ────────────────► HOME (immediate redirect)        │
                                                           │
  TRAINING:                                               │
    subLessonFinishedToken changes  ──► LESSON             │
    bossFinishedToken changes       ──► LESSON             │
    exitDialog (boss active)        ──► LESSON             │
    exitDialog (drill mode)         ──► LESSON             │
    exitDialog (normal)             ──► LESSON             │
                                                           │
  STORY:                                                  │
    onClose / onComplete   ──► LESSON                     │
                                                           │
  LADDER:                                                 │
    back   ──► previousScreen (returns to caller)         │
                                                           │
  SETTINGS SHEET (overlay, not a screen):                  │
    opened from any screen via onOpenSettings              │
    openLadder   ──► LADDER                                │
    dismiss      ──► returns to caller screen              │
```

### 13.3.3 State-based navigation mechanism

The `screen` variable is a `MutableState<AppScreen>` created inside `GrammarMateApp()`:

```kotlin
var screen by remember { mutableStateOf(parseScreen(state.initialScreen)) }
```

- `state.initialScreen` comes from `TrainingUiState.initialScreen`, which is hardcoded to `"HOME"` in the ViewModel init block (`val restoredScreen = "HOME"`). The `parseScreen` function attempts `AppScreen.valueOf(name)`, falling back to `HOME` on failure. This means the app **always starts on HOME** on fresh launch.
- Screen transitions happen by direct assignment: `screen = AppScreen.LESSON`, etc.
- The `when (screen)` block at the center of `GrammarMateApp()` renders exactly one screen composable at a time.

A companion variable `previousScreen` tracks the screen before settings was opened, enabling return navigation from LADDER back to the settings caller.

### 13.3.4 Screen change tracking

Every time the `screen` variable changes, a `LaunchedEffect` fires:

```kotlin
LaunchedEffect(screen) {
    vm.onScreenChanged(screen.name)
}
```

`onScreenChanged` updates `TrainingUiState.currentScreen` (a separate field from `initialScreen`). This field is persisted to `progress.yaml` via `ProgressStore.save()` under the key `"currentScreen"`. However, as noted above, `restoredScreen` is hardcoded to `"HOME"` — the persisted `currentScreen` is not used for navigation restoration (it may be used for analytics or debugging).

### 13.3.5 Back stack behavior

There is no formal back stack. Navigation is flat — each screen knows its parent and navigates back explicitly. `BackHandler` composables enforce Android system back button behavior:

| Current screen | BackHandler behavior |
|----------------|---------------------|
| `TRAINING` (no settings open) | Show exit confirmation dialog (`showExitDialog = true`) |
| `LESSON` (no settings open) | Navigate to `HOME` |
| `DAILY_PRACTICE` (no settings open) | Navigate to `HOME` |
| `STORY` (no settings open) | Navigate to `LESSON` |
| `LADDER` (no settings open) | Navigate to `previousScreen`; if previous was `TRAINING` and card exists, call `vm.resumeFromSettings()` |
| `VERB_DRILL` (no settings open) | Navigate to `HOME` |
| `VOCAB_DRILL` (no settings open) | Navigate to `HOME` |
| Any screen with settings open | No back handler for screen (settings sheet handles its own dismissal) |

All `BackHandler` registrations check `!showSettings` to avoid conflict with the settings sheet overlay.

### 13.3.6 Settings sheet overlay

The `SettingsSheet` is not a screen — it is an overlay (a `ModalBottomSheet` or similar) that can be opened from any screen. When opened:
1. `previousScreen` is saved to the current `screen` value.
2. `vm.pauseSession()` is called (if coming from `TRAINING`).
3. On dismiss: if the previous screen was `TRAINING` and a card is active, `vm.resumeFromSettings()` is called.
4. The settings sheet has its own `openLadder` callback that sets `screen = AppScreen.LADDER`.

### 13.3.7 Deep link handling

**There are no deep links.** The manifest contains only the standard launcher intent-filter. The app does not register for any URL schemes, custom intents, or App Links. All navigation is internally driven.

### 13.3.8 Deprecated screens

`AppScreen.ELITE` and `AppScreen.VOCAB` are kept in the enum for backward compatibility. If either is somehow restored (e.g., from a stale `initialScreen` value), the `when` block immediately redirects to HOME:

```kotlin
AppScreen.ELITE -> { screen = AppScreen.HOME }
AppScreen.VOCAB -> { screen = AppScreen.HOME }
```

This is a Level A (safety) constraint per CLAUDE.md — removing these enum values would crash users who have stale saved state containing `"ELITE"` or `"VOCAB"` strings.

---

## 13.4 Initialization Sequence

### 13.4.1 Complete launch sequence

```
1. Android OS launches MainActivity
   └─ AndroidManifest launcher intent-filter

2. MainActivity.onCreate(savedInstanceState)
   ├─ super.onCreate(savedInstanceState)
   ├─ Check SharedPreferences for stored SAF URI
   ├─ Check data existence (mastery.yaml, progress.yaml, profile.yaml)
   ├─ Load profile via ProfileStore
   ├─ Determine shouldRestore flag
   ├─ Branch: restore or skip (see section 13.1.6)
   │   ├─ May launch SAF picker (async, user interaction)
   │   ├─ May request storage permissions (async, system dialog)
   │   └─ May start restore coroutine (Dispatchers.IO)
   └─ setContent { AppRoot() }

3. AppRoot composable renders
   ├─ GrammarMateTheme { ... }
   ├─ Collects RestoreNotifier.restoreState
   └─ IF status != DONE: renders StartupScreen (loading/spinner)
   └─ IF status == DONE: renders GrammarMateApp()

4. GrammarMateApp composable renders (first time only)
   ├─ viewModel<TrainingViewModel>() — triggers ViewModel creation
   │
   │   Inside TrainingViewModel.init:
   │   ├─ SoundPool initialization (2 streams)
   │   ├─ lessonStore.ensureSeedData() — copies default packs from assets
   │   ├─ badSentenceStore.migrateIfNeeded(lessonStore)
   │   ├─ progressStore.load() — reads progress.yaml
   │   ├─ configStore.load() — reads config.yaml
   │   ├─ profileStore.load() — reads profile.yaml
   │   ├─ streakStore.getCurrentStreak(languageId)
   │   ├─ Resolve activePackId (saved → derived from lessonId → first pack)
   │   ├─ Build initial _uiState from all loaded data
   │   └─ rebuildSchedules(lessons) — compute sub-lesson schedules
   │
   ├─ screen = parseScreen(state.initialScreen) → always HOME
   └─ Render based on screen value (HOME on fresh launch)
```

### 13.4.2 What gets loaded at each stage

#### Stage 1: `MainActivity.onCreate`

| Resource | Store/Manager | I/O | Notes |
|----------|--------------|-----|-------|
| SAF URI | `SharedPreferences("backup_prefs")` | Read | May be null on first install |
| Profile | `ProfileStore.load()` | Read | `profile.yaml` — may return default |
| Data existence check | `File.exists()` on 3 files | Read-only | `mastery.yaml`, `progress.yaml`, `profile.yaml` |
| Backup restore | `BackupManager.restoreFromBackupUri()` or legacy | Read + Write | Conditional on `shouldRestore` |

#### Stage 2: `TrainingViewModel.init`

| Resource | Store | File | Notes |
|----------|-------|------|-------|
| Seed data | `LessonStore.ensureSeedData()` | Assets → internal | Copies default packs, idempotent |
| Bad sentence migration | `BadSentenceStore.migrateIfNeeded()` | Internal | Pack-scoped migration |
| Training progress | `ProgressStore.load()` | `progress.yaml` | Full session state restoration |
| App config | `AppConfigStore.load()` | `config.yaml` | testMode, eliteSizeMultiplier, etc. |
| User profile | `ProfileStore.load()` | `profile.yaml` | userName |
| Streak data | `StreakStore.getCurrentStreak()` | `streak_<lang>.yaml` | Current and longest streak |
| Lessons | `LessonStore.getLessons(languageId)` | CSV files | Parsed lesson content |
| Languages | `LessonStore.getLanguages()` | Internal | Available language list |
| Packs | `LessonStore.getInstalledPacks()` | Internal | Installed pack list |
| Bad sentence count | `BadSentenceStore.getBadSentenceCount()` | Internal | Per-pack count |
| ASR model ready | `AsrModelManager.isReady()` | Internal | Check if model files exist |
| Vocab mastery count | `WordMasteryStore.getMasteredCount()` | `word_mastery.yaml` | Global count |

#### Stage 3: `GrammarMateApp` composable

| Resource | Source | Notes |
|----------|--------|-------|
| Verb drill availability | `LessonStore.hasVerbDrill(packId, languageId)` | Computed from manifest |
| Vocab drill availability | `LessonStore.hasVocabDrill(packId, languageId)` | Computed from manifest |
| Audio permission launcher | `ActivityResultContracts.RequestPermission()` | Registered lazily |

### 13.4.3 First launch vs subsequent launches

#### First launch (no data files exist)

1. `hasFullData = false` → `shouldRestore = true`
2. **If no backup exists** (no SAF URI, no legacy backup folder):
   - SAF picker may be shown on Android 10+ but the user can cancel it.
   - `RestoreNotifier.markComplete(false)` is called.
   - App proceeds with empty data.
3. `LessonStore.ensureSeedData()` copies default lesson packs from `assets/grammarmate/packs/` to internal storage.
4. `ProgressStore.load()` returns `TrainingProgress()` with all defaults.
5. `ProfileStore.load()` returns `UserProfile()` with `userName = "GrammarMateUser"`.
6. `GrammarMateApp` detects `userName == "GrammarMateUser"` on any non-HOME screen and triggers the `WelcomeDialog` for name entry.
7. All counters (mastery, streak, progress) start at zero.

#### Subsequent launches (data files exist)

1. `hasFullData = true` → `shouldRestore = false`
2. `RestoreNotifier.markComplete(false)` is called immediately — no restore attempted.
3. `AppRoot` passes through to `GrammarMateApp` without showing `StartupScreen` (status jumps to `DONE` on first composition frame).
4. `TrainingViewModel.init` loads all persisted state from YAML files.
5. Previous session state (lesson, mode, position) is restored into `TrainingUiState`.
6. The screen is always initialized to `HOME` regardless of what was persisted.

#### Reinstall + backup available

1. `hasFullData = false` → `shouldRestore = true`
2. If a stored SAF URI exists (from before reinstall, persisted via `takePersistableUriPermission`), restore runs automatically.
3. If no stored URI, user is prompted to select backup folder via SAF picker.
4. After restore, `mastery.yaml`, `progress.yaml`, `profile.yaml`, and streak files are copied from the backup.
5. `TrainingViewModel.init` then loads the restored data as if it were a normal subsequent launch.

### 13.4.4 Default data seeding

`LessonStore.ensureSeedData()` is called during `TrainingViewModel.init`. This method is idempotent — it checks whether each default pack's data already exists before copying from assets. The seeding process:

1. Iterates over the `defaultPacks` list (hardcoded in `LessonStore.kt`).
2. For each default pack, checks if the pack directory exists in internal storage.
3. If not, copies the ZIP from `assets/grammarmate/packs/` and imports it (extracts CSV files, reads manifest).
4. The `defaultPacks` list must be kept in sync with the actual ZIP files in `assets/grammarmate/packs/` — a ZIP in assets without a corresponding `DefaultPack` entry will not be imported.

---

## 13.5 State Restoration

### 13.5.1 Configuration changes (e.g., screen rotation)

`TrainingViewModel` extends `AndroidViewModel` and is created via Compose's `viewModel()` function. This means:

- The ViewModel is scoped to the activity's `ViewModelStore`, which survives configuration changes.
- The `_uiState` `MutableStateFlow` and all private mutable fields (`sessionCards`, `bossCards`, `eliteCards`, etc.) persist across rotations.
- The Compose `remember` blocks in `GrammarMateApp` (for `screen`, `previousScreen`, dialog flags) **do not survive** configuration changes because `setContent` re-creates the composable tree. However, the ViewModel survives and provides `initialScreen` which resets `screen` to `HOME`.

**Effect:** After a rotation, the user returns to the HOME screen, not the screen they were on. This is a known limitation of the current state-based navigation (no SavedStateHandle for `screen`).

### 13.5.2 Process death survival

When the system kills the app process:

**Persisted (survives process death):**

| Data | Store | File | Loaded by |
|------|-------|------|-----------|
| Training progress | `ProgressStore` | `progress.yaml` | `TrainingViewModel.init` |
| Lesson mastery | `MasteryStore` | `mastery.yaml` | `TrainingViewModel.init` (via schedules) |
| User profile | `ProfileStore` | `profile.yaml` | `TrainingViewModel.init` |
| App config | `AppConfigStore` | `config.yaml` | `TrainingViewModel.init` |
| Streak data | `StreakStore` | `streak_<lang>.yaml` | `TrainingViewModel.init` |
| Bad sentences | `BadSentenceStore` | Per-pack files | On demand |
| Hidden cards | `HiddenCardStore` | Per-pack files | On demand |
| Verb drill progress | `VerbDrillStore` | `drills/<pack>/verb_drill_progress.yaml` | `VerbDrillViewModel` |
| Word mastery | `WordMasteryStore` | `drills/<pack>/word_mastery.yaml` | `VocabDrillViewModel` |
| Installed packs | `LessonStore` | Internal dirs | `TrainingViewModel.init` |
| SAF URI | `SharedPreferences` | `backup_prefs.xml` | `MainActivity.onCreate` |
| Daily cursor | `ProgressStore` | `progress.yaml` (nested) | `TrainingViewModel.init` |

**Ephemeral (lost on process death):**

| Data | Location | Notes |
|------|----------|-------|
| Current screen | `GrammarMateApp` composable `remember` | Resets to `HOME` |
| `previousScreen` | `GrammarMateApp` composable `remember` | Resets to `HOME` |
| Dialog states (`showSettings`, `showExitDialog`, etc.) | `GrammarMateApp` composable `remember` | All reset to `false` |
| `sessionCards` (in-memory card list) | `TrainingViewModel` private var | Must be rebuilt from progress |
| `bossCards`, `eliteCards` | `TrainingViewModel` private var | Must be rebuilt |
| `vocabSession` | `TrainingViewModel` private var | Must be rebuilt |
| `subLessonTotal`, `subLessonCount` | `TrainingViewModel` private var | Reset to `0` |
| Active session state (`bossActive`, etc.) | `TrainingUiState` fields | Reset in ViewModel init |
| TTS/ASR state | `TrainingUiState` fields | Reset; engines re-initialized |
| Daily practice in-memory session | `prebuiltDailySession` | Must be rebuilt |
| `loadedSounds` (SoundPool) | `TrainingViewModel` private var | Re-loaded in init |
| Timer jobs | `TrainingViewModel` private var | Cancelled with process death |

### 13.5.3 SavedStateHandle usage

`TrainingViewModel` does **not** use `SavedStateHandle`. It takes only `Application` as a constructor parameter:

```kotlin
class TrainingViewModel(application: Application) : AndroidViewModel(application)
```

There is no `SavedStateHandle` parameter, no `savedState` key-value storage, and no state-restoration contract. All state that needs to survive process death is persisted to YAML files via the various stores.

### 13.5.4 Screen state restoration

The `screen` variable in `GrammarMateApp` is initialized from `state.initialScreen`:

```kotlin
var screen by remember { mutableStateOf(parseScreen(state.initialScreen)) }
```

`initialScreen` in `TrainingUiState` is set to `restoredScreen`, which is hardcoded to `"HOME"`:

```kotlin
val restoredScreen = "HOME"
```

Although `ProgressStore` persists a `currentScreen` field in `progress.yaml` and the ViewModel updates `TrainingUiState.currentScreen` via `onScreenChanged()`, this persisted value is **never used** for screen restoration. The `restoredScreen` variable shadows it with a hardcoded `"HOME"`.

**Implication:** After process death, the app always opens to the HOME screen, regardless of what screen the user was on. This is an intentional design choice (simplification over complexity of mid-session restoration).

### 13.5.5 What state is persisted vs ephemeral — summary

#### Persisted to YAML files

- Selected language and lesson
- Active pack ID
- Session position (current index, correct/incorrect counts)
- Training mode
- Session state (PAUSED, ACTIVE, etc.)
- Boss battle rewards (per-lesson and mega)
- Elite step index and best speeds
- Voice usage statistics
- Hint count
- Current screen name (stored but not used for restoration)
- Daily level and task index
- Daily cursor state (sentence offset, lesson index, session hash, first-session card IDs)
- All mastery data (per-lesson card show tracking)
- All streak data
- User profile (name)
- App configuration flags

#### Ephemeral (in-memory only)

- Currently rendered screen (always resets to HOME)
- In-memory card lists for active sessions
- Boss/elite session active state
- Sub-lesson progress counters
- Story active state
- Vocab session state
- TTS/ASR engine states and download dialogs
- Sound pool loaded samples
- Timer/coroutine jobs
- All dialog visibility flags
- `previousScreen` for navigation context
- Daily practice pre-built session tasks

### 13.5.6 Write safety

All file writes in the persistence layer use `AtomicFileWriter` (temp file -> fsync -> rename). This applies to:
- `ProgressStore.save()`
- `ProfileStore.save()`
- `MasteryStore` operations
- `StreakStore` operations
- `YamlListStore` operations

This ensures that a crash mid-write does not corrupt the YAML files, which is critical since the restore check in `MainActivity` relies on these files existing and being parseable.
