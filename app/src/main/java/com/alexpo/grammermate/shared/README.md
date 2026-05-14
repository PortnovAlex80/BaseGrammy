# Shared Utilities

## What
Cross-cutting utilities shared across features: settings action handling and
result types. These classes coordinate config changes, progress resets, and
backup operations requested from the Settings screen.

## API Surface

- **SettingsActionHandler** — Handles all settings-screen actions.
  Constructor takes `TrainingStateAccess`, `AppConfigStore`, `ProfileStore`,
  `BackupManager`, `CoroutineScope`, and injected `resolveEliteUnlocked` function.
  Returns `List<SettingsResult>` commands for the ViewModel to execute.
  Key methods:
  - Config: `toggleTestMode()`, `updateVocabSprintLimit(limit)`, `setHintLevel(level)`
  - Profile: `updateUserName(newName)` (trims to 50 chars, persists to ProfileStore)
  - Progress: `saveProgressNow()`, `createProgressBackup()`,
    `resetAllProgress(app)`, `resetLanguageProgress(app, languageId, packId)`
  - Screen tracking: `onScreenChanged(screenName)`

- **SettingsResult** — Sealed class returned by SettingsActionHandler:
  `RefreshLessons(selectedLessonId?)`, `ResetStores(app)`,
  `ResetStoresForLanguage(app, languageId)`, `ResetDrillFiles(app)`,
  `ResetDrillFilesForPack(app, packId)`, `ClearWordMastery`, `ResetDailyState`,
  `SetForceBackup`, `SaveProgress`, `None`

## State owned
None. All state writes go through `TrainingStateAccess.updateState {}`.

## Dependencies
- Data stores: AppConfigStore, ProfileStore, BackupManager
- Feature: TrainingStateAccess interface
- Injected function: `resolveEliteUnlocked(lessons, testMode) -> Boolean`

## Edit scope warnings
- Reset methods return a list of SettingsResult commands -- the ViewModel must
  execute each one (e.g., call ProgressTracker.resetStores). Missing a result
  causes partial reset.
- resetAllProgress wipes everything (mastery, drills, daily, training session).
  resetLanguageProgress preserves other languages but still clears daily state.
- configStore.load()/save() is called on every config toggle -- no batching.
- createProgressBackup launches a coroutine on IO dispatcher internally.
