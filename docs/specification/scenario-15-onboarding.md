# Scenario 15: First Launch & Onboarding

Trace of fresh-install through all initialization layers, compared against specs 13 and 19.

---

## Test Case 1: Fresh install -> first open -> what screen? Welcome dialog?

**Expected (spec 19.16, 13.4.3):** App opens, restore check runs (no data files -> shouldRestore=true), no backup exists -> markComplete(false), StartupScreen briefly visible, then GrammarMateApp renders HOME. WelcomeDialog should be triggered because userName defaults to "GrammarMateUser".

**Actual code path:**

1. `MainActivity.onCreate` (line 49):
   - `storedTreeUri = null` (fresh install, no SharedPrefs)
   - `hasFullData = false` (no mastery.yaml, progress.yaml, profile.yaml)
   - `shouldRestore = true`
   - Branches to `else if (SDK >= 29)` (line 71): `shouldRestore=true` -> calls `RestoreNotifier.requireUser()` then launches SAF picker

2. **DISCREPANCY:** On Android 10+ (API 29+), fresh install ALWAYS shows the SAF folder picker toast "Select BaseGrammy or backup_latest folder to restore" (line 73-80). The user MUST interact with the SAF picker before proceeding. If the user cancels the picker, `markComplete(false)` is called (line 40-46).

   On pre-Android-10 without permissions, the app requests storage permissions (line 89-92).

   The spec (13.4.3) says "SAF picker may be shown on Android 10+ but the user can cancel it" -- this is correct, but the UX is that a fresh-install user is immediately asked to select a backup folder, which is confusing for a brand new user with no backup.

3. After `markComplete(false)`, `RestoreNotifier.restoreState.status == DONE`, so `AppRoot` renders `GrammarMateApp()`.

4. `GrammarMateApp` (line 141): `viewModel<TrainingViewModel>()` triggers `init` block.

5. `TrainingViewModel.init` (line 150-347): loads all stores, builds `_uiState`. Key: `userName` defaults to `"GrammarMateUser"` (from `ProfileStore.load()` returning `UserProfile()`).

6. Screen initialized to HOME (`val restoredScreen = "HOME"` at line 181).

7. **WelcomeDialog trigger:** `GrammarMateApp` line 302-305:
   ```kotlin
   LaunchedEffect(screen, state.userName) {
       if (screen != AppScreen.HOME && state.userName == "GrammarMateUser") {
           showWelcomeDialog = true
       }
   }
   ```
   The `LaunchedEffect` only triggers when `screen != AppScreen.HOME`. Since the app starts on HOME, the WelcomeDialog is **NOT shown on first launch**.

**DISCREPANCY (SIGNIFICANT):** The spec says "User name is 'GrammarMateUser': triggers WelcomeDialog if not on HOME screen (but auto-triggered on first launch via LaunchedEffect)" (spec 19.2). However, the code only shows WelcomeDialog when `screen != AppScreen.HOME`. Since the app starts on HOME, the WelcomeDialog is never shown on first launch. It will only appear later when the user navigates to LESSON or any other screen.

**FINDING:** WelcomeDialog is NOT shown on first launch at the HOME screen. It is shown when the user first navigates away from HOME (e.g., taps "Continue Learning" -> LESSON screen). This is a UX issue -- the user sees "GrammarMateUser" as their name on the HOME screen until they navigate elsewhere.

---

## Test Case 2: Welcome dialog -> user enters name -> where is it saved?

**Expected (spec 19.16):** Name saved via `ProfileStore.save()` to `grammarmate/profile.yaml`.

**Actual code path:**

1. `WelcomeDialog` (GrammarMateApp.kt line 737-794): User types name, taps "Continue" or "Skip".
2. `onNameSet` callback calls `vm.updateUserName(name)` (line 264-266).
3. `TrainingViewModel.updateUserName` (line 3228-3238):
   - Trims and caps at 50 chars
   - Creates `UserProfile(userName = trimmed)`
   - Calls `profileStore.save(profile)` -> writes to `grammarmate/profile.yaml` via `AtomicFileWriter`
   - Updates `_uiState.userName`

4. If user taps "Skip", name is set to `"GrammarMateUser"` (line 787-788).

**Result:** MATCHES spec. Name persisted to `grammarmate/profile.yaml`.

---

## Test Case 3: After welcome -> Home screen -> which tiles visible? Which lessons?

**Expected (spec 19.2, 13.4.3):** Grammar Roadmap shows 12 tiles. First lesson SPROUT, rest LOCKED. Default packs seeded.

**Actual code path:**

1. `TrainingViewModel.init` calls `lessonStore.ensureSeedData()` (line 157) then later `forceReloadDefaultPacks()` on IO thread (line 274-315).

2. `LessonStore.seedDefaultPacksIfNeeded()` (line 53-67): checks `seed_v1.done` marker. If no lesson content, imports from `defaultPacks` list:
   - `EN_WORD_ORDER_A1` from `grammarmate/packs/EN_WORD_ORDER_A1.zip`
   - `IT_VERB_GROUPS_ALL` from `grammarmate/packs/IT_VERB_GROUPS_ALL.zip`

3. Then `forceReloadDefaultPacks()` (line 89-98) removes and re-imports all default packs. This runs on `Dispatchers.IO` and updates `_uiState` on main thread after completion.

4. `buildLessonTiles` (GrammarMateApp.kt line 1858-1925): Always renders 12 tiles.
   - `i == 0` -> `LessonTileState.SPROUT` (line 1892)
   - `lastLessonWithProgress == -1` (fresh install, no mastery) -> tile at index 1 becomes `UNLOCKED` (line 1902)
   - All others -> `LOCKED` (line 1916)

**Result:** First tile SPROUT, second tile UNLOCKED, tiles 3-12 LOCKED. This means 2 lessons are accessible from the start, which differs from spec statement "first lesson always SPROUT; subsequent lessons UNLOCKED only after the previous lesson has masteryPercent > 0". The second lesson is UNLOCKED even though lesson 1 has no progress, because `lastLessonWithProgress = -1` and `i == lastLessonWithProgress + 1` resolves to `i == 0` being UNLOCKED, not `i == 1`. Actually, re-reading the code: `i == lastLessonWithProgress + 1` where `lastLessonWithProgress = -1` means `i == 0` is handled by `i == 0 -> SPROUT` first, then for `i == 1`, it falls to `else` block where `lastLessonWithProgress + 1 = 0`, so `i == 1` does NOT match `i == lastLessonWithProgress + 1`. It goes to `i < lastLessonWithProgress + 1` which is `1 < 0` -> false. Then falls to `else -> LOCKED`.

**CORRECTION:** Only the first lesson (index 0) is SPROUT. All others (1-11) are LOCKED on fresh install. This matches the spec.

**DISCREPANCY:** None. First lesson SPROUT, all others LOCKED on fresh install.

---

## Test Case 4: Default packs seeded -> both EN and IT packs? Where in filesystem?

**Expected (spec 13.4.4):** Two default packs seeded from assets.

**Actual code path:**

1. `LessonStore.defaultPacks` (line 30-33):
   - `DefaultPack("EN_WORD_ORDER_A1", "grammarmate/packs/EN_WORD_ORDER_A1.zip")`
   - `DefaultPack("IT_VERB_GROUPS_ALL", "grammarmate/packs/IT_VERB_GROUPS_ALL.zip")`

2. Assets exist at `app/src/main/assets/grammarmate/packs/EN_WORD_ORDER_A1.zip` and `IT_VERB_GROUPS_ALL.zip`.

3. After import, files are stored at:
   - `context.filesDir/grammarmate/packs/EN_WORD_ORDER_A1/` (extracted pack dir with manifest.json + CSVs)
   - `context.filesDir/grammarmate/packs/IT_VERB_GROUPS_ALL/`
   - `context.filesDir/grammarmate/lessons/en/` (EN lesson CSVs)
   - `context.filesDir/grammarmate/lessons/it/` (IT lesson CSVs)
   - `context.filesDir/grammarmate/lessons/languages.yaml` (language list)
   - `context.filesDir/grammarmate/packs.yaml` (installed packs registry)
   - `context.filesDir/grammarmate/drills/IT_VERB_GROUPS_ALL/verb_drill/` (IT verb drill CSVs, if pack has verbDrill section)
   - `context.filesDir/grammarmate/drills/IT_VERB_GROUPS_ALL/vocab_drill/` (IT vocab drill CSVs, if pack has vocabDrill section)

4. `seedMarker` at `grammarmate/seed_v1.done` prevents re-seeding on subsequent launches. But `forceReloadDefaultPacks()` (called every app start from ViewModel init) overrides this by removing and re-importing.

**Result:** MATCHES spec. Both EN and IT packs seeded.

---

## Test Case 5: No TTS model downloaded -> TTS button visible? Download prompt?

**Expected (spec 19.4):** TTS speaker button visible. Tapping it opens TtsDownloadDialog if model not ready.

**Actual code path:**

1. `TrainingViewModel.init` (line 318-319): calls `checkTtsModel()` and `startBackgroundTtsDownload()`.

2. `checkTtsModel()` (line 2600-2604): checks `ttsModelManager.isModelReady(langId)` -> returns false on fresh install -> `_uiState.ttsModelReady = false`.

3. `startBackgroundTtsDownload()` (line 2795-2833): checks for missing language TTS models and starts background download automatically.

4. In `GrammarMateApp` `onTtsSpeak` (line 175-191): if `!state.ttsModelReady`, shows `showTtsDownloadDialog = true`.

5. **IMPORTANT:** `startBackgroundTtsDownload()` runs automatically on every app start. This means TTS models start downloading in the background on first launch without user interaction. The persistent progress bar at the top of GrammarMateApp (line 195-200) shows download progress.

6. TTS speaker button is always rendered on training cards (`TtsSpeakerButton`). Tapping it when model not ready opens the download dialog.

**Result:** MATCHES spec. TTS button visible, download prompt shown on tap. Additional behavior: auto-download starts in background on every launch.

---

## Test Case 6: No ASR model downloaded -> voice input available? Download prompt?

**Expected:** Voice input uses system speech recognition by default (`RecognizerIntent`). Offline ASR is opt-in via settings.

**Actual code path:**

1. `TrainingViewModel.init` (line 109-113): `asrEngine` is created with try/catch. `asrModelManager.isReady()` called at line 320.

2. `TrainingUiState.useOfflineAsr` defaults to `false` (line 3590). Config seeded from assets has no `useOfflineAsr` key, defaults to false.

3. When `useOfflineAsr == false`, voice input uses Android's built-in `RecognizerIntent` (system speech recognition). No download needed.

4. When user enables offline ASR in settings, `setUseOfflineAsr(true)` (line 2685) calls `checkAsrModel()` which checks model readiness.

5. ASR download prompt only appears when offline ASR is enabled and model is not downloaded.

**Result:** On first launch with default settings, voice input uses system speech recognition. No download required. Offline ASR is an opt-in feature. MATCHES expected behavior.

---

## Test Case 7: No backup exists -> AppRoot passes through immediately?

**Expected (spec 13.2, 13.4.3):** If no backup data, app proceeds after restore check.

**Actual code path:**

1. `MainActivity.onCreate`: `hasFullData = false` on fresh install -> `shouldRestore = true`.

2. On Android 10+ (SDK 29+): No stored SAF URI, `shouldRestore=true` -> `RestoreNotifier.requireUser()` then SAF picker launched (line 73-80). User must interact with picker (select or cancel).

3. If user cancels SAF picker -> `markComplete(false)` (line 44-46) -> `RestoreNotifier.status = DONE` -> AppRoot renders GrammarMateApp.

4. On pre-Android-10: Similar flow with storage permission request.

**DISCREPANCY:** AppRoot does NOT pass through immediately on fresh install on Android 10+. The user is forced through the SAF folder picker (which they can cancel). The `RestoreNotifier` starts in `IDLE` status, then transitions to `NEEDS_USER` (line 79), so `StartupScreen` with "Waiting for backup folder..." is shown until the SAF picker is resolved.

This means on first launch, the user sees a brief "Waiting for backup folder..." screen, then the SAF folder picker dialog, and only after canceling/completing that does the main app load. This is a suboptimal first-launch experience.

---

## Test Case 8: config.yaml -> seeded from assets? Default values?

**Expected (spec 13.4.2):** Config seeded from `assets/grammarmate/config.yaml`.

**Actual code path:**

1. `AppConfigStore.load()` (line 30-59): If `config.yaml` doesn't exist:
   - Tries to copy from `context.assets.open("grammarmate/config.yaml")` to `grammarmate/config.yaml`
   - Falls back to `mapOf("testMode" to false, "vocabSprintLimit" to 20)` if assets read fails

2. Assets file at `app/src/main/assets/grammarmate/config.yaml`:
   ```yaml
   testMode: false
   eliteSizeMultiplier: 1.25
   vocabSprintLimit: 20
   ```

3. Loaded config defaults:
   - `testMode = false`
   - `eliteSizeMultiplier = 1.25`
   - `vocabSprintLimit = 20`
   - `useOfflineAsr = false`

**Result:** MATCHES spec. Config seeded from assets with correct defaults.

---

## Test Case 9: All stores initialized empty -> first lesson starts fresh?

**Expected:** No progress, no mastery, no streak. Lesson index 0, card index 0.

**Actual code path:**

1. `ProgressStore.load()` (line 13-76): File doesn't exist -> returns `TrainingProgress()` with all defaults:
   - `languageId = "en"`, `mode = LESSON`, `lessonId = null`, `currentIndex = 0`, `correctCount = 0`, `incorrectCount = 0`, `state = PAUSED`

2. `MasteryStore.loadAll()` (line 25-74): File doesn't exist -> returns empty cache.

3. `StreakStore.getCurrentStreak("en")` (line 136-153): File doesn't exist -> returns `StreakData(languageId="en", currentStreak=0)`.

4. `ProfileStore.load()` (line 15-29): File doesn't exist -> returns `UserProfile(userName="GrammarMateUser")`.

5. `TrainingViewModel.init` builds `_uiState`:
   - `selectedLanguageId = "en"` (line 173)
   - `selectedLessonId = lessons.firstOrNull()?.id` (line 176-177) -- first lesson from default pack
   - `sessionState = progress.state = SessionState.PAUSED` (line 203)
   - All counters at 0

**Result:** MATCHES expected. All stores empty, first lesson selected, no progress.

---

## Test Case 10: Flower states on first launch -> all LOCKED?

**Expected:** First lesson SPROUT, others LOCKED.

**Actual code path:**

1. `FlowerCalculator.calculate(mastery, totalCards)` (line 20-67): When `mastery == null` (fresh install), returns `FlowerVisual(state=SEED, masteryPercent=0f, healthPercent=1f, scaleMultiplier=0.5f)`.

2. `refreshFlowerStates()` (TrainingViewModel line 3113-3151): Iterates all lessons, calls `FlowerCalculator.calculate` for each. All return `SEED` state (since no mastery data).

3. `buildLessonTiles()` (GrammarMateApp.kt line 1858-1925): Tile state logic:
   - `i == 0` -> `LessonTileState.SPROUT` (always, regardless of flower)
   - Others -> `LOCKED` (no progress on fresh install)

4. Note: `FlowerState.SEED` is the flower data state, but `LessonTileState.SPROUT` is the tile UI state. The naming is confusing but the tile rendering logic at index 0 is hardcoded to SPROUT.

**DISCREPANCY (NAMING):** `FlowerCalculator` returns `FlowerState.SEED` for unstarted lessons, but `buildLessonTiles` hardcodes tile 0 as `LessonTileState.SPROUT`. These are different enum hierarchies. The flower visual (data) says SEED, but the tile UI state says SPROUT. The LessonTile composable likely renders based on `LessonTileState`, so tile 0 shows as accessible. The spec says "first lesson always SPROUT" which matches the UI behavior.

**Result:** Functionally correct. First lesson accessible (SPROUT tile), others locked. Flower data shows SEED for all.

---

## Test Case 11: Streak on first launch -> 0?

**Expected:** `currentStreak = 0`, `longestStreak = 0`.

**Actual code path:**

1. `StreakStore.getCurrentStreak("en")` (line 136-153): `load("en")` returns `StreakData(languageId="en")` with `currentStreak=0`, `longestStreak=0`, `lastCompletionDateMs=null`.

2. Since `lastCompletionDateMs == null`, early return at line 138 -> returns the zero-streak data.

3. `TrainingViewModel.init` line 249: `_uiState.currentStreak = streakData.currentStreak = 0`, `_uiState.longestStreak = streakData.longestStreak = 0`.

**Result:** MATCHES expected. Streak is 0 on first launch.

---

## Test Case 12: Language selector -> which languages available? Default?

**Expected (spec 13.4.3):** English ("en") and Italian ("it") available. Default language is "en".

**Actual code path:**

1. `LessonStore.ensureSeedData()` (line 35-46): Creates `languages.yaml` with:
   ```yaml
   - id: en, name: English
   - id: it, name: Italian
   ```

2. During pack import (`ensureLanguage`), languages are ensured. Both default packs import for their respective languages.

3. `TrainingViewModel.init` line 173: `selectedLanguageId = languages.firstOrNull { it.id == progress.languageId }?.id ?: "en"`. `progress.languageId` defaults to `"en"` from `TrainingProgress()`.

4. `LanguageSelector` in `HomeScreen` (line 882-888): Shows dropdown with all languages, currently selected shown as uppercase code (e.g., "EN").

**Result:** MATCHES expected. Two languages (EN, IT), default is EN.

---

## Test Case 13: Drill tiles -> visible only if pack has drill sections?

**Expected (spec 19.2, CLAUDE.md):** Drill tiles visible only when active pack's manifest declares verbDrill/vocabDrill.

**Actual code path:**

1. `GrammarMateApp` line 168-173:
   ```kotlin
   val hasVerbDrill = remember(state.activePackId, state.selectedLanguageId) {
       state.activePackId?.let { lessonStore.hasVerbDrill(it, state.selectedLanguageId) } ?: false
   }
   val hasVocabDrill = remember(state.activePackId, state.selectedLanguageId) {
       state.activePackId?.let { lessonStore.hasVocabDrill(it, state.selectedLanguageId) } ?: false
   ```

2. `LessonStore.hasVerbDrill(packId, languageId)` (line 903-905): Checks for CSV files in `grammarmate/drills/{packId}/verb_drill/`.

3. `LessonStore.hasVocabDrill(packId, languageId)` (line 907-909): Checks for CSV files in `grammarmate/drills/{packId}/vocab_drill/`.

4. `HomeScreen` line 945-964: Drill tiles Row only rendered if `hasVerbDrill || hasVocabDrill`.

5. On fresh install with default packs:
   - `EN_WORD_ORDER_A1` pack: likely has no drill sections in manifest -> no drill tiles when EN pack is active
   - `IT_VERB_GROUPS_ALL` pack: likely has verbDrill and/or vocabDrill sections -> drill tiles visible when IT pack is active

6. First launch: default language is "en", active pack depends on `getPackIdForLesson` for the first EN lesson. If EN pack is active, drill tiles may be hidden.

**Result:** MATCHES spec. Pack-scoped drill visibility.

---

## Test Case 14: Second launch -> no welcome dialog? Profile remembered?

**Expected:** Profile saved, no WelcomeDialog on subsequent launches.

**Actual code path:**

1. After first launch, user entered name via WelcomeDialog -> saved to `grammarmate/profile.yaml`.

2. On second launch: `ProfileStore.load()` reads `profile.yaml` -> returns `UserProfile(userName="ActualUserName")`.

3. `TrainingViewModel.init` line 251: `userName = profile.userName` -> not "GrammarMateUser".

4. `GrammarMateApp` LaunchedEffect (line 302-305): condition `state.userName == "GrammarMateUser"` is false -> `showWelcomeDialog` remains false.

5. `MainActivity.onCreate`: `hasFullData = true` (all three files exist) -> `shouldRestore = false` -> `RestoreNotifier.markComplete(false)` immediately -> no SAF picker, no StartupScreen delay.

**Result:** MATCHES expected. Profile remembered, no WelcomeDialog on second launch. App passes through AppRoot immediately.

---

## Test Case 15: App update -> data migration? Any version checks?

**Expected:** No explicit migration framework. Data format is YAML with schema versions.

**Actual code path:**

1. `ProgressStore` has `schemaVersion = 1` (line 11). Contains migration for `bossMegaReward` -> `bossMegaRewards` (line 67-75).

2. `MasteryStore` has `schemaVersion = 1` (line 15). No migration logic.

3. `LessonStore.forceReloadDefaultPacks()` (line 89-98): Called on every app start from `TrainingViewModel.init` (line 274-275). Removes and re-imports all default packs from assets. This ensures updated lesson content is applied on app update.

4. `LessonStore.updateDefaultPacksIfNeeded()` (line 69-83): Alternative method that checks pack version before updating. Currently not called in the init flow (superseded by `forceReloadDefaultPacks`).

5. `badSentenceStore.migrateIfNeeded(lessonStore)` (line 158): Pack-scoped migration for bad sentence data.

6. No global version check or coordinated migration system. Each store handles its own format.

**Result:** No formal migration framework. `forceReloadDefaultPacks()` ensures updated lesson content. `ProgressStore` has one inline migration for boss rewards. This is adequate for current needs but could become fragile with more complex schema changes.

---

## Summary of Discrepancies

### SIGNIFICANT

1. **WelcomeDialog not shown on HOME screen (TC1):** The `LaunchedEffect` that triggers `showWelcomeDialog = true` only fires when `screen != AppScreen.HOME`. Since the app starts on HOME, new users see "GrammarMateUser" as their name until they navigate to another screen. The spec says it should be "auto-triggered on first launch via LaunchedEffect" (19.2), but the code condition prevents this on HOME.

   **Fix:** Add an additional condition to show the WelcomeDialog on HOME when `userName == "GrammarMateUser"`, or change the LaunchedEffect condition to also trigger on HOME when the name is default.

2. **SAF folder picker shown on fresh install Android 10+ (TC7):** New users without backups are shown a toast saying "Select BaseGrammy or backup_latest folder to restore" and forced to interact with the SAF folder picker before the app loads. This is confusing for first-time users who have no backup.

   **Fix:** Check if it's truly a first install (no seed marker, no lesson content) and skip the restore flow entirely. Alternatively, add a "No backup / Start fresh" option to the SAF picker flow.

### MINOR / NAMING

3. **FlowerState.SEED vs LessonTileState.SPROUT (TC10):** Different enum hierarchies for the same concept. `FlowerCalculator` returns SEED for unstarted lessons, but `buildLessonTiles` hardcodes index 0 as SPROUT. Not a bug, but confusing naming.

### MATCHING

All other test cases (2, 3, 4, 5, 6, 8, 9, 11, 12, 13, 14, 15) match their expected behavior as described in specs 13 and 19.
