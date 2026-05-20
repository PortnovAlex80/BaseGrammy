# TASK-077: Verb Drill Progress Lost on APK Update - Full Investigation Prompt

## Problem Statement

**Current behavior:** When APK is updated (new version installed over old version), verb drill progress is completely lost:
- `everShownCardIds` resets (all cards shown again)
- `lastSessionState` is lost (no "Повторить"/"Продолжить" options)
- User must start practice from scratch

**Expected behavior:** Files in `context.filesDir/grammarmate/` should persist across APK updates. This is standard Android behavior — `filesDir` is NOT cleared on app update.

**Working comparison:** Other stores work correctly:
- **StreakStore:** Daily streak persists across APK updates ✓
- **MasteryStore:** Read cards (mastery) persists across APK updates ✓
- **ProgressStore:** Training progress persists across APK updates ✓
- **Daily Practice:** Session state persists across APK updates ✓

---

## All Fixes Attempted (None Worked)

### Fix 1: mkdirs() before file write
**File:** `VerbDrillStore.kt`
**Change:** Added `file.parentFile?.mkdirs()` before `AtomicFileWriter.writeText()` in both `persistProgressToDisk()` and `persistLastSessionToDisk()`
**Result:** Did NOT fix the problem

### Fix 2: ensureProgressLoaded() with Mutex
**File:** `VerbDrillViewModel.kt`
**Change:** Added `ensureProgressLoaded()` method with `Mutex` and `@Volatile progressLoaded` flag
**Result:** Did NOT fix the problem

### Fix 3: refreshLastSessionContext() guard
**File:** `VerbDrillViewModel.kt`
**Change:** Added `if (!reloadForPackCalled) return` check to prevent reading from wrong store
**Result:** Did NOT fix the problem

### Fix 4: Load progress in init() block
**File:** `VerbDrillViewModel.kt`
**Change:** Added coroutine in `init()` that loads progress immediately (like Daily Practice pattern)
**Result:** Did NOT fix the problem

### Fix 5: lateinit var instead of null store
**File:** `VerbDrillViewModel.kt`
**Change:** Changed `private var verbDrillStore = container.verbDrillStore(null)` to `private lateinit var verbDrillStore`
**Added:** `::verbDrillStore.isInitialized` checks throughout the file
**Result:** Did NOT fix the problem

### Fix 6: Comprehensive logging
**File:** `VerbDrillStore.kt`
**Change:** Added detailed logging to all read/write operations
**Result:** Logs added but user reports problem still exists

---

## Key Findings from Agent Research

### Finding 1: File Path Structure
**VerbDrillStore:**
- Progress file: `grammarmate/drills/{packId}/verb_drill_progress.yaml`
- Session file: `grammarmate/drills/{packId}/verb_drill_last_session.yaml`
- Uses PACK-SCOPED paths with subdirectories

**Daily Practice (ProgressStore):**
- Progress file: `grammarmate/progress.yaml` (FLAT path, no subdirectories)
- All data embedded in single TrainingProgress object

### Finding 2: Initialization Timing
**VerbDrillViewModel:**
```kotlin
// Line 54 (before Fix 5):
private var verbDrillStore: VerbDrillStore = container.verbDrillStore(null)  // NULL store!

// Line 146-163 (after Fix 4):
init {
    // Load progress immediately
    viewModelScope.launch(Dispatchers.IO) {
        val progress = progressStore.load()
        val packId = progress.activePackId?.value
        if (packId != null) {
            verbDrillStore = container.verbDrillStore(packId)  // Creates NEW store
            progressMap = verbDrillStore.loadProgress()
        }
    }
}
```

**Race condition identified:** ViewModel initialized with NULL store, then init coroutine loads progress. But reloadForPack() can create ANOTHER store instance concurrently.

### Finding 3: Daily Practice Loading Pattern
**TrainingViewModel:**
```kotlin
// Line 395 (in init block):
val progress = progressStore.load()  // Loads immediately, synchronously
dailyCursor = progress.dailyCursor    // Cursor embedded in progress
```

Daily Practice loads progress SYNCHRONOUSLY in init, not in a coroutine.

### Finding 4: VerbDrillScreen LaunchedEffect Race
**VerbDrillScreen.kt:**
```kotlin
LaunchedEffect(Unit) {
    viewModel.refreshLastSessionContext()  // Runs IMMEDIATELY on screen entry
}
```

This runs BEFORE `reloadForPack()` completes, causing read from wrong (global) store.

---

## Current Code State (After All Fixes)

### VerbDrillStore.kt
```kotlin
private lateinit var verbDrillStore: VerbDrillStore  // lateinit, no null initialization

init {
    viewModelScope.launch(Dispatchers.IO) {
        val progress = progressStore.load()
        val packId = progress.activePackId?.value
        if (packId != null && !usingTestStore) {
            currentPackId = packId
            verbDrillStore = container.verbDrillStore(packId)
            reloadForPackCalled = true
            progressMap = verbDrillStore.loadProgress()
            progressLoaded = true
        } else if (!usingTestStore) {
            verbDrillStore = container.verbDrillStore(null)  // Fallback
            reloadForPackCalled = true
        }
    }
}

private suspend fun ensureProgressLoaded() {
    if (!progressLoaded && reloadForPackCalled && ::verbDrillStore.isInitialized) {
        // ... loads progress
    }
}

fun refreshLastSessionContext() {
    if (!reloadForPackCalled || !::verbDrillStore.isInitialized) {
        return  // Guard check
    }
    val lastSession = verbDrillStore.loadLastSession()
    // ...
}
```

### VerbDrillStore.kt - Persistence
```kotlin
private fun persistProgressToDisk() {
    val progress = progressCache ?: return
    // ... logging ...
    file.parentFile?.mkdirs()  // Fix 1: mkdirs
    AtomicFileWriter.writeText(file, yaml.dump(data))
    // ... logging ...
}
```

---

## Critical Questions for New Investigation

### Question 1: Store Instance Mismatch
**Hypothesis:** Progress is written to ONE VerbDrillStore instance but read from ANOTHER after app restart.

**What to check:**
- How many VerbDrillStore instances exist during app lifecycle?
- Does VerbDrillViewModel hold a reference to the SAME store instance as VerbDrillStore factory creates?
- Can reloadForPack() create a new store while another is still being used?

### Question 2: File Path Consistency
**Hypothesis:** Files are written to one path but read from another path.

**What to check:**
- What is the EXACT file path when persistProgressToDisk() is called?
- What is the EXACT file path when loadProgressFromDisk() is called after restart?
- Does {packId} change between write and read?
- Is there any legacy path fallback that could cause confusion?

### Question 3: activePackId Persistence
**Hypothesis:** activePackId is not correctly persisted or retrieved, causing wrong store initialization.

**What to check:**
- Where is activePackId stored? (in ProgressStore's TrainingProgress?)
- When is it loaded on app restart?
- Can VerbDrillViewModel.init() run BEFORE activePackId is available?
- Is there a timing issue where VerbDrill tries to load before ProgressStore has loaded?

### Question 4: AtomicFileWriter Behavior
**Hypothesis:** AtomicFileWriter might not be writing to the expected location.

**What to check:**
- Does AtomicFileWriter use the correct file path?
- Is the temp file created in the same directory?
- Does the rename operation succeed?
- Are there any silent failures?

### Question 5: Android File System Behavior
**Hypothesis:** Android might be deleting files for some reason.

**What to check:**
- Is `android:allowBackup` set correctly in manifest?
- Are there any backup rules that might interfere?
- Is the `drills/{packId}/` directory in a location Android clears?

---

## Investigation Tasks for Agents

### Task 1: Trace Complete Write Path
**Goal:** Follow the EXACT path from user action to disk write.

**Steps:**
1. User completes a card in VerbDrillViewModel
2. Find which method is called (submitCorrectAnswer, submitWrongAnswer, etc.)
3. Trace through persistCardProgress()
4. Trace through verbDrillStore.upsertComboProgress()
5. Trace through persistProgressToDisk()
6. Trace through AtomicFileWriter.writeText()
7. Log the EXACT file path at each step

**Output:** Call stack with line numbers and exact file paths.

### Task 2: Trace Complete Read Path
**Goal:** Follow the EXACT path from app restart to data loading.

**Steps:**
1. App restarts after APK update
2. VerbDrillViewModel is created
3. init block runs
4. coroutine launches
5. progressStore.load() is called
6. activePackId is extracted
7. verbDrillStore is created with packId
8. verbDrillStore.loadProgress() is called
9. loadProgressFromDisk() is called
10. File is read

**Output:** Call stack with line numbers and exact file paths.

### Task 3: Compare Write Path vs Read Path
**Goal:** Find ANY difference between write and read paths.

**What to compare:**
- File path construction
- Directory structure
- packId values
- Store instances
- Timing

**Output:** Table showing write vs read for each aspect.

### Task 4: Verify Files Actually Exist on Disk
**Goal:** Confirm files are actually created and contain data.

**Method:** Add logging or use adb to check:
```bash
adb shell run-as com.alexpo.grammermate ls -la files/grammarmate/drills/
adb shell run-as com.alexpo.grammermate cat files/grammarmate/drills/*/verb_drill_progress.yaml
```

**Output:** Actual file contents before and after APK update.

### Task 5: Compare with Working Stores
**Goal:** Find what StreakStore/MasteryStore/ProgressStore do differently.

**What to compare:**
- File path structure (flat vs nested)
- Initialization timing
- Loading mechanism
- Store lifecycle

**Output:** Detailed comparison table.

---

## Expected Deliverables

1. **Root cause** identified with code evidence
2. **Exact reason** why all previous fixes failed
3. **Proposed fix** that addresses the ACTUAL problem
4. **Verification plan** to confirm the fix works

---

## Files to Investigate

**Stores:**
- `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/StreakStore.kt` (working)
- `app/src/main/java/com/alexpo/grammermate/data/MasteryStore.kt` (working)
- `app/src/main/java/com/alexpo/grammermate/data/ProgressStore.kt` (working)
- `app/src/main/java/com/alexpo/grammermate/data/AtomicFileWriter.kt`

**ViewModels:**
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt`

**Screens:**
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillScreen.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt`

**Manifest:**
- `app/src/main/AndroidManifest.xml`

---

## Success Criteria

- Root cause identified with SPECIFIC code evidence (not speculation)
- Fix proposed that addresses the ACTUAL root cause (not symptoms)
- Fix can be implemented in < 100 lines of code
- Fix has clear verification steps
