# Scenario 12: State Persistence & Recovery

App killed mid-session → user reopens → what state survives?

---

## System Under Test

| Store | File | Purpose |
|-------|------|---------|
| `ProgressStore` | `grammarmate/progress.yaml` | Session position, mode, counters, daily cursor, currentScreen |
| `MasteryStore` | `grammarmate/mastery.yaml` | Per-lesson unique card shows, shownCardIds, interval steps |
| `VerbDrillStore` | `grammarmate/drills/{pack}/verb_drill_progress.yaml` | Verb drill everShown/todayShown per tense combo |
| `WordMasteryStore` | `grammarmate/drills/{pack}/word_mastery.yaml` | Per-word SRS state (intervalStep, isLearned, nextReview) |
| `StreakStore` | `grammarmate/streak_{lang}.yaml` | Current/longest streak, lastCompletionDateMs |
| `DrillProgressStore` | `grammarmate/drill_progress_{lesson}.yaml` | Drill card index per lesson |
| `VocabProgressStore` | `grammarmate/vocab_progress.yaml` | Vocab sprint SRS (completedIndices, entryStates) |
| `AppConfigStore` | `grammarmate/config.yaml` | Runtime flags |
| `ProfileStore` | `grammarmate/profile.yaml` | userName |
| `AtomicFileWriter` | (utility) | temp → fsync → rename for all writes |

---

## Test Case 1: Kill during normal training card

**Code path:**
- `TrainingViewModel.saveProgress()` at line 2453 writes to `ProgressStore`
- Called from: `submitAnswer()` (line 614), `nextCard()` (line 896), `prevCard()` (line 914), `togglePause()` (line 542), timer tick every 500ms (line 2442)
- `recordCardShowForMastery()` writes to `MasteryStore` immediately per card
- Init restores: `currentIndex`, `sessionState`, `mode`, `selectedLessonId` from `progress.yaml`
- Init at line 263: if `sessionState == ACTIVE && currentCard != null`, calls `buildSessionCards()` then `resumeTimer()`

**What is saved:** languageId, lessonId, currentIndex, correctCount, incorrectCount, activeTimeMs, sessionState, inputMode, mode, activePackId, all mastery for cards answered so far.

**What is NOT saved:** `sessionCards` list (private var, rebuilt from progress), `inputText` (cleared on restore), `answerText`/`lastResult` (UI display state), `subLessonTotal`/`subLessonCount` (reset to 0 in init).

**Expected behavior:**
- User can resume at the same card index within the same lesson/mode.
- The card is reconstructed from lesson data at the saved `currentIndex`.
- `buildSessionCards()` rebuilds the card list deterministically from the sub-lesson schedule.
- Counts (correct/incorrect) are preserved.
- Mastery for all cards answered before the kill is preserved.

**Actual behavior:** MATCHES EXPECTED. The card list is rebuilt deterministically from the same schedule key and lesson content. `currentIndex` points to the correct card.

**Discrepancy:** None. This is the best-supported recovery path.

---

## Test Case 2: Kill during daily practice block 1 (TRANSLATE)

**Code path:**
- `startDailyPractice()` at line 1450 sets `dailySession.active = true`, stores tasks in `DailySessionState`
- `saveProgress()` at line 2477 persists `dailyLevel` and `dailyTaskIndex` (from `DailySessionState.taskIndex`)
- `recordDailyCardPracticed()` at line 1603 updates `dailyPracticeAnsweredCounts` (in-memory only)
- Mastery is written per card via `masteryStore.recordCardShow()` for TRANSLATE block cards

**What is saved:** `dailyLevel`, `dailyTaskIndex` (task position within daily session), `dailyCursor` (sentenceOffset, currentLessonIndex, firstSessionDate, firstSessionSentenceCardIds), mastery for answered cards.

**What is NOT saved:** `DailySessionState` itself (`tasks`, `active`, `blockIndex`, `finishedToken` are NOT in `TrainingProgress`), `dailyPracticeAnsweredCounts` (in-memory), `prebuiltDailySession` (in-memory), `lastDailyTasks` (in-memory).

**Expected behavior:**
- User should resume daily practice at the same card position within block 1.
- Cards already answered should have mastery preserved.

**Actual behavior:** DISCREPANCY. On restore:
1. `restoredScreen = "HOME"` — user lands on HOME, not DAILY_PRACTICE.
2. `DailySessionState` resets to default (`active = false`, `tasks = emptyList()`, `taskIndex = 0`).
3. The daily session tasks list is lost (in-memory only).
4. `dailyTaskIndex` is persisted but `DailySessionState` is not reconstructed from it during init.
5. The init block does NOT restore a daily session — it only restores `dailyCursor` for cursor tracking.

**Severity:** MEDIUM. The user must manually restart daily practice. Cards already answered have mastery preserved (flower growth retained), but the session itself is lost. The cursor does NOT advance because `cancelDailySession()` (with cursor advancement logic) is never called on kill — the session simply disappears.

**Edge case:** If the first session of the day had cards stored via `storeFirstSessionCardIds()`, the user CAN repeat that session via `repeatDailyPractice()`. But they must manually navigate to daily practice and tap "Repeat".

---

## Test Case 3: Kill during daily practice between blocks

**Code path:** Same as Test Case 2, but user has completed block 1 (TRANSLATE) and is in block 2 (VOCAB) or block 3 (VERBS).

**What is saved:** `dailyCursor` with first-session card IDs (if first session today), mastery for TRANSLATE cards, word mastery for VOCAB cards rated so far, verb drill progress for VERBS cards answered so far.

**What is NOT saved:** Which block the user was in (`blockIndex` is in `DailySessionState`, not persisted), task position within that block beyond `dailyTaskIndex`.

**Actual behavior:** DISCREPANCY. Same as Test Case 2 — the entire `DailySessionState` is ephemeral. User lands on HOME with no active daily session. The `dailyTaskIndex` IS saved (from `DailySessionState.taskIndex`) but there is no code in init that reconstructs the session at that task index. The user must start a fresh session.

**Severity:** MEDIUM. Block-level progress is lost, but per-card SRS data (word mastery, verb progress) was written atomically for each card answered before the kill.

---

## Test Case 4: Kill during verb drill (VerbDrillViewModel)

**Code path:**
- `VerbDrillViewModel.persistCardProgress()` at line ~394 calls `verbDrillStore.upsertComboProgress()` on every card advance
- `VerbDrillStore.upsertComboProgress()` does load-modify-save via `AtomicFileWriter`
- `VerbDrillViewModel.onCleared()` at line 570 does NOT call any save method — only releases TTS

**What is saved:** `everShownCardIds` and `todayShownCardIds` for every card that was advanced past (via `nextCardManual()` or correct answer). Each card is persisted individually and atomically.

**What is NOT saved:** The current batch/session state (`VerbDrillSession` with card list, currentIndex) — this is in-memory only. `VerbDrillViewModel` has no `progress.yaml` equivalent for session state.

**Expected behavior:** Card-level progress (shown sets) survives. Session position is lost.

**Actual behavior:** MATCHES EXPECTED (with caveat). On reopen:
1. User must navigate to verb drill again.
2. A new session starts, but `VerbDrillStore.loadProgress()` reads the updated `everShownCardIds` and `todayShownCardIds`, so the new batch excludes already-shown cards.
3. Progress is preserved at the card level even though the session restarts.

**Discrepancy:** Minor. The session restarts from the beginning of a new batch rather than at the exact card where the kill happened. But SRS progress is correct.

---

## Test Case 5: Kill during vocab drill (VocabDrillViewModel)

**Code path:**
- `VocabDrillViewModel` calls `masteryStore.upsertMastery()` at line 271 for each rated card
- `WordMasteryStore.upsertMastery()` does load-modify-save via `AtomicFileWriter`
- `VocabDrillViewModel.onCleared()` at line 451 does NOT call any save method

**What is saved:** Per-word SRS state (`intervalStepIndex`, `correctCount`, `incorrectCount`, `lastReviewDateMs`, `nextReviewDateMs`, `isLearned`) for every card that was rated before the kill.

**What is NOT saved:** Current vocab drill session position, which cards were in the current batch.

**Expected behavior:** Word mastery survives. Session position is lost.

**Actual behavior:** MATCHES EXPECTED. On reopen, `masteryStore.loadAll()` reads all persisted mastery states. New sessions will prioritize overdue/new words correctly based on persisted `nextReviewDateMs`.

**Discrepancy:** None. The per-card atomic writes guarantee no partial state.

---

## Test Case 6: Kill during boss battle

**Code path:**
- `saveProgress()` at line 2453-2455: **early return** when `bossActive == true && bossType != BossType.ELITE`
- This means boss session state is NEVER persisted mid-battle
- `bossCards` is a private `var` in TrainingViewModel, never persisted
- `bossActive`, `bossType`, `bossTotal`, `bossProgress` are all in `TrainingUiState` but not in `TrainingProgress`

**What is saved:** The state that existed BEFORE the boss battle started (whatever was in `progress.yaml` from the last non-boss save). Boss rewards from previously completed boss battles (in `bossLessonRewards` and `bossMegaRewards` maps) are preserved.

**What is NOT saved:** Any progress made during the current boss battle (cards answered, current position within the boss).

**Expected behavior:** Boss battle is a single-attempt challenge. On kill, the battle is lost but pre-boss state is preserved.

**Actual behavior:** MATCHES EXPECTED. On restore:
1. `bossActive` is reset to `false` in init (line 231).
2. The session state from before the boss (saved to `progress.yaml` before `startBoss()` was called) is restored.
3. User lands on HOME and must re-enter the lesson and restart the boss.

**Discrepancy:** None. This is intentional — boss battles are designed as single-attempt, no-save challenges (spec section 8.10.3).

---

## Test Case 7: Config change (rotation)

**Code path:**
- `TrainingViewModel` extends `AndroidViewModel`, created via `viewModel()` in Compose
- ViewModel is scoped to activity's `ViewModelStore`, which survives config changes
- `_uiState` MutableStateFlow and all private vars (`sessionCards`, `bossCards`, etc.) survive
- `GrammarMateApp` composable's `remember` blocks do NOT survive (Compose recomposition on config change)
- `var screen by remember { mutableStateOf(parseScreen(state.initialScreen)) }` resets to `parseScreen(state.initialScreen)`

**What survives:** ALL ViewModel state (training session, boss state, daily session, timer, card lists, etc.).

**What is lost:** Screen navigation state (`screen` resets to `HOME`), dialog states (`showSettings`, `showExitDialog`, etc.), `previousScreen`.

**Expected behavior:** Training session survives rotation, but user returns to HOME screen.

**Actual behavior:** DISCREPANCY. The ViewModel state is fully intact, but the screen resets to HOME. The user must manually navigate back to the screen they were on. This is a known limitation documented in spec 13.5.1.

**Severity:** LOW. The user's work is preserved (ViewModel state is intact), but navigation is lost. For training screens, the user can re-enter from HOME and the session continues. For daily practice, `dailySession.active` is still `true` in the ViewModel, but the UI is on HOME.

**Note:** There is no `SavedStateHandle` usage (`TrainingViewModel` takes only `Application`). Screen state could be preserved with `rememberSaveable` instead of `remember`, but the current implementation uses plain `remember`.

---

## Test Case 8: Process death → restoredScreen is hardcoded to HOME

**Code path:**
- `TrainingViewModel.init` line 181: `val restoredScreen = "HOME"` (hardcoded)
- Line 255: `initialScreen = restoredScreen`
- `GrammarMateApp` line 145: `var screen by remember { mutableStateOf(parseScreen(state.initialScreen)) }`
- `parseScreen()` line 702: `AppScreen.valueOf(name)` with fallback to `HOME`

**Expected:** User always returns to HOME after process death.

**Actual:** MATCHES. `restoredScreen` is hardcoded, never reads from persisted `currentScreen`.

**Discrepancy:** By design (spec 13.5.4). The persisted `currentScreen` in `progress.yaml` is updated via `onScreenChanged()` but never read for restoration. It may serve analytics or debugging purposes.

---

## Test Case 9: currentScreen persisted but restoredScreen ignores it

**Code path:**
- `GrammarMateApp` line 159: `LaunchedEffect(screen) { vm.onScreenChanged(screen.name) }`
- `TrainingViewModel.onScreenChanged()` line 3246-3248: updates `currentScreen` in UiState
- `saveProgress()` line 2475: persists `currentScreen` to `progress.yaml`
- `ProgressStore.save()` line 97: writes `"currentScreen" to progress.currentScreen`
- `ProgressStore.load()` line 49: reads `payload["currentScreen"]`
- `TrainingViewModel.init` line 181: `val restoredScreen = "HOME"` — ignores the loaded value

**Confirmed:** `currentScreen` is written to and read from `progress.yaml`, but the init block hardcodes `"HOME"` instead of using `progress.currentScreen`. The loaded value is discarded.

**Discrepancy:** The `currentScreen` field in `TrainingProgress` is dead code for restoration purposes. It is persisted but never used. This wastes a write on every screen change and creates a misleading expectation that screen restoration works.

---

## Test Case 10: Streak across midnight

**Code path:**
- `StreakStore.recordSubLessonCompletion()` line 62-83
- `checkAndUpdateStreak()` line 96-131 uses `Calendar.YEAR` and `Calendar.DAY_OF_YEAR` for date comparison
- `getCurrentStreak()` line 136-153 checks `TimeUnit.MILLISECONDS.toDays(now - lastCompletionMs) > 1` for streak reset

**Day boundary handling:**
- `checkAndUpdateStreak()` compares calendar dates (year + dayOfYear), not millisecond thresholds
- Same-day check: `lastDate.year == today.year && lastDate.dayOfYear == today.dayOfYear` → streak unchanged
- Yesterday check: subtracts 1 day from today and compares → consecutive, streak +1
- Otherwise: streak resets to 1

**Midnight scenario:** If the user completes a sub-lesson at 23:59 and another at 00:01, the calendar comparison correctly identifies these as consecutive days.

**Edge case:** Timezone changes are NOT handled. `Calendar.getInstance()` uses the device's default timezone. If the user changes timezone between sessions, the day comparison could be off.

**`getCurrentStreak()` discrepancy:** This method uses `TimeUnit.MILLISECONDS.toDays()` (a raw millisecond division) rather than calendar comparison. This means:
- If the user completed a session at 23:00 yesterday and opens the app at 01:00 today (2 hours later), `toDays()` returns 0 (less than 1 day), so the streak is preserved. CORRECT.
- If the user completed at 01:00 yesterday and opens at 02:00 today (25 hours later), `toDays()` returns 1, so streak is preserved. CORRECT.
- If the user completed at 01:00 two days ago and opens at 02:00 today (49 hours later), `toDays()` returns 2, streak resets to 0. CORRECT.

**Actual behavior:** MATCHES EXPECTED for normal usage. Streak resets are handled correctly at day boundaries.

**Minor issue:** `getCurrentStreak()` uses a simpler calculation than `checkAndUpdateStreak()`. If there's a precision edge case, `getCurrentStreak()` might give a slightly different result than `checkAndUpdateStreak()`. Both use `> 1` for the "missed" threshold, so they should agree.

---

## Test Case 11: Save frequency — how often is progress written?

**Code path:** `saveProgress()` is called at 30+ locations in TrainingViewModel:
- Every answer submission (`submitAnswer()` — line 614)
- Every card navigation (`nextCard()` — line 896, `prevCard()` — line 914)
- Session state changes (`togglePause()` — line 542, `startSession()` — line 2423)
- Lesson/language/pack selection (`selectLanguage()` — line 459, `selectLesson()` — line 560, etc.)
- Boss/elite transitions (`finishBoss()` — line 2328)
- Timer: every 500ms tick (line 2442)
- Sub-lesson completion (line 716, 835)
- Daily session operations (line 1524)
- `onCleared()` — final save on ViewModel destruction (line 2851)

**Frequency:** Near-continuous. The timer alone writes every 500ms. Every user action triggers a save.

**Skip conditions:** Boss battles (`bossActive && bossType != ELITE`) skip save. This is intentional.

**Write cost:** Each `saveProgress()` creates a YAML string in memory and writes via `AtomicFileWriter` (temp → fsync → rename). The YAML includes all persisted fields. On a modern device, this takes <5ms. The 500ms timer writes are the highest frequency.

**Actual behavior:** The app effectively maintains near-real-time persistence. The maximum data loss window is ~500ms (timer interval) for session counters, or one card action for card-level state.

---

## Test Case 12: Atomic write — kill mid-write

**Code path:** `AtomicFileWriter.writeText()` at line 8-37:
1. Write to temp file (`{name}.tmp`)
2. `output.fd.sync()` — forces OS buffer flush
3. Delete original file
4. `tempFile.renameTo(file)` — atomic on most filesystems

**Windows-specific concern:** The app runs on Android (Linux), but development is on Windows (Cyrillic path). On Android's ext4/f2fs:
- `renameTo()` is atomic
- If kill happens after step 2 but before step 4: original file is intact, temp file has new data
- If kill happens after step 3 but before step 4: neither file exists — DATA LOSS

**The gap between delete and rename (lines 29-36):**
```kotlin
if (file.exists() && !file.delete()) {
    tempFile.delete()
    error("Failed to replace ${file.absolutePath}")
}
if (!tempFile.renameTo(file)) {
    tempFile.delete()
    error("Failed to finalize ${file.absolutePath}")
}
```

**Vulnerability:** There is a brief window between `file.delete()` (line 29) and `tempFile.renameTo(file)` (line 33) where NO valid file exists. A process kill in this window results in both files being absent — the original is deleted and the temp hasn't been renamed.

**Actual risk:** LOW. This window is extremely short (microseconds). But it exists, and on a device with aggressive process killing under memory pressure, it could theoretically happen.

**Mitigation:** The delete-then-rename pattern should ideally be rename-then-delete (rename temp to target, which atomically replaces). However, `File.renameTo()` on Android/Linux atomically replaces the target, making the explicit `delete()` unnecessary. The safer pattern would be:
```kotlin
// Atomic on Linux: replaces target if exists
if (!tempFile.renameTo(file)) { ... }
```

**Discrepancy:** The explicit `delete()` before `renameTo()` creates an unnecessary vulnerability window. On Android/Linux, `renameTo()` atomically replaces the destination, making the `delete()` redundant and harmful.

---

## Test Case 13: Multiple stores writing simultaneously

**Code path:** Multiple stores can write simultaneously:
- `MasteryStore.recordCardShow()` → `AtomicFileWriter.writeText(mastery.yaml)`
- `ProgressStore.save()` → `AtomicFileWriter.writeText(progress.yaml)`
- `VerbDrillStore.upsertComboProgress()` → `AtomicFileWriter.writeText(verb_drill_progress.yaml)`
- `WordMasteryStore.upsertMastery()` → `AtomicFileWriter.writeText(word_mastery.yaml)`

**Observation:** Each store writes to a DIFFERENT file. There are no cross-file transactions. If the process is killed between writes to two different stores, they can be inconsistent.

**Example scenario:**
1. User answers a daily TRANSLATE card correctly
2. `recordDailyCardPracticed()` calls `masteryStore.recordCardShow()` — writes mastery
3. Kill happens before `saveProgress()` writes the updated `dailyTaskIndex`
4. Result: Mastery shows the card was answered, but `dailyTaskIndex` is stale

**Impact:** LOW for the current app design. Mastery is the source of truth for flower calculations. `dailyTaskIndex` is only used if a daily session were to be restored (which it isn't — see Test Case 2). The mastery and verb/word drill stores are self-consistent.

**MasteryStore internal consistency:** `MasteryStore` uses an in-memory cache (`cache: MutableMap`) with a `cacheLoaded` flag. `loadAll()` reads from file once, then uses cache. If two threads call `recordCardShow()` simultaneously, the second call could overwrite the first's changes (classic read-modify-write race). However, all training actions run on the main thread (ViewModel methods), so this is not an issue in practice.

---

## Test Case 14: First launch after install (no progress.yaml)

**Code path:**
- `ProgressStore.load()` line 14: `if (!file.exists()) return TrainingProgress()`
- `TrainingProgress()` defaults: `languageId = "en"`, `mode = LESSON`, `currentIndex = 0`, `sessionState = PAUSED`
- `TrainingViewModel.init` line 159: `val progress = progressStore.load()` — gets defaults
- Line 173: `selectedLanguageId = languages.firstOrNull { it.id == progress.languageId }?.id ?: "en"`
- Line 177: `selectedLessonId = progress.lessonId?.let { id -> lessons.firstOrNull { it.id == id }?.id } ?: lessons.firstOrNull()?.id`
- Line 181: `restoredScreen = "HOME"`

**Expected behavior:** App starts fresh with default values.

**Actual behavior:** MATCHES.
1. No `progress.yaml` → all defaults.
2. First language defaults to "en" (or first available).
3. First lesson selected automatically.
4. Session state = PAUSED (not ACTIVE).
5. `buildSessionCards()` builds cards for the first sub-lesson of the first lesson.
6. User sees HOME screen with lesson tiles.

**Discrepancy:** None.

---

## Test Case 15: First launch after reinstall with backup

**Code path:**
1. `MainActivity.onCreate` checks for `mastery.yaml`, `progress.yaml`, `profile.yaml` (all three)
2. After reinstall, none exist → `shouldRestore = true`
3. If SAF URI was persisted (via `takePersistableUriPermission`), restore runs automatically
4. `BackupManager.restoreFromBackupUri()` copies files from backup to internal storage
5. `RestoreNotifier.markComplete(true)` → AppRoot shows GrammarMateApp
6. `TrainingViewModel.init` loads restored files

**Expected behavior:** Full state restored from backup.

**Actual behavior:** MATCHES (with caveats):
- `progress.yaml`, `mastery.yaml`, `profile.yaml` are restored
- Streak files (`streak_{lang}.yaml`) are restored
- Verb drill progress and word mastery are restored (if backed up)
- Daily cursor state within `progress.yaml` is restored

**Caveat:** The backup restore path depends on `BackupManager` copying all relevant files. The restore is file-level, not transactional — if restore fails partway through, some files may be newer and others older. This is mitigated by restoring from a single backup snapshot.

**Discrepancy:** None significant.

---

## Summary of Discrepancies

### HIGH Severity

None found.

### MEDIUM Severity

| # | Issue | Files | Impact |
|---|-------|-------|--------|
| 1 | Daily session state is entirely ephemeral | `TrainingViewModel.kt:193-258`, `Models.kt:219-226` | Kill during daily practice loses the active session. User must restart. Per-card SRS data IS preserved. |
| 2 | `DailySessionState` not persisted or reconstructed | `TrainingViewModel.kt:2477` (saves `dailyLevel`/`dailyTaskIndex` but not the session itself) | `dailyLevel` and `dailyTaskIndex` are persisted but never used for restoration — dead writes. |
| 3 | Rotation resets screen to HOME while ViewModel retains active session | `GrammarMateApp.kt:145` | User on DAILY_PRACTICE or TRAINING screen returns to HOME after rotation. Session continues if user navigates back. |

### LOW Severity

| # | Issue | Files | Impact |
|---|-------|-------|--------|
| 4 | `currentScreen` persisted but never used for restoration | `ProgressStore.kt:49,97`, `TrainingViewModel.kt:181` | Dead field in progress.yaml. Wastes writes, creates misleading expectation. |
| 5 | `AtomicFileWriter` delete-then-rename creates vulnerability window | `AtomicFileWriter.kt:29-36` | On Android/Linux, the explicit `delete()` before `renameTo()` is unnecessary and creates a window where no file exists. |
| 6 | No cross-store transaction consistency | Multiple stores | Kill between mastery write and progress write can cause minor inconsistency (mastery shows card done, taskIndex doesn't). |
| 7 | `VerbDrillViewModel.onCleared()` does not save progress | `VerbDrillViewModel.kt:570` | Session position lost on ViewModel destruction. Per-card progress was saved incrementally, so impact is minimal. |
| 8 | Streak timezone handling uses default device timezone | `StreakStore.kt:103-121` | Timezone change mid-streak could cause incorrect day boundary calculation. |

### Spec vs Code Alignment

| Spec claim (13-app-entry-and-navigation.md) | Code reality | Match? |
|----------------------------------------------|-------------|--------|
| "Screen is always initialized to HOME" | `restoredScreen = "HOME"` at line 181 | YES |
| "currentScreen persisted but not used for restoration" | Confirmed: `onScreenChanged()` writes it, init ignores it | YES |
| "Boss sessions not saved mid-battle" | `saveProgress()` returns early for non-ELITE boss | YES |
| "All file writes use AtomicFileWriter" | 14 files confirmed using it | YES |
| "No SavedStateHandle usage" | Constructor takes only `Application` | YES |
| "Daily cursor survives process death" | Stored in `progress.yaml`, restored in init | YES |
| "Daily session tasks are ephemeral" | `DailySessionState` not in `TrainingProgress` | YES |

### What Survives Process Death (Comprehensive)

| Data | Survives? | Mechanism |
|------|-----------|-----------|
| Selected language/pack/lesson | YES | `progress.yaml` |
| Card position index | YES | `progress.yaml` |
| Correct/incorrect counts | YES | `progress.yaml` |
| Active time | YES | `progress.yaml` |
| Session state (ACTIVE/PAUSED) | YES | `progress.yaml` |
| Boss rewards | YES | `progress.yaml` |
| Elite step + speeds | YES | `progress.yaml` |
| Daily cursor | YES | `progress.yaml` |
| Daily level + task index | YES (dead write) | `progress.yaml` |
| Current screen | YES (dead write) | `progress.yaml` |
| Per-lesson mastery | YES | `mastery.yaml` |
| Per-word vocab mastery | YES | `word_mastery.yaml` |
| Verb drill card progress | YES | `verb_drill_progress.yaml` |
| Streak data | YES | `streak_{lang}.yaml` |
| User profile | YES | `profile.yaml` |
| **Active daily session tasks** | **NO** | In-memory only |
| **Boss battle state** | **NO** | In-memory only, intentional |
| **Screen navigation** | **NO** | `remember` blocks, hardcoded HOME |
| **In-memory card lists** | **NO** | Rebuilt from progress |
| **Timer state** | **NO** | Rebuilt if session was ACTIVE |
| **Dialog visibility** | **NO** | Compose `remember` state |
