# TASK-077: Verb Drill Progress Lost on APK Update - Complete Investigation Prompt

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

## ALL FIXES ATTEMPTED (None Worked - Final Fix: Flat Paths)

### Fix 1: mkdirs() before file write
**File:** `VerbDrillStore.kt`
**Change:** Added `file.parentFile?.mkdirs()` before `AtomicFileWriter.writeText()` in both `persistProgressToDisk()` and `persistLastSessionToDisk()`
**Reason:** Thought subdirectories might not exist
**Result:** Did NOT fix the problem

### Fix 2: ensureProgressLoaded() with Mutex
**File:** `VerbDrillViewModel.kt`
**Change:** Added `ensureProgressLoaded()` method with `Mutex` and `@Volatile progressLoaded` flag
**Reason:** Thought progress wasn't being loaded after restart
**Result:** Did NOT fix the problem

### Fix 3: refreshLastSessionContext() guard
**File:** `VerbDrillViewModel.kt`
**Change:** Added `if (!reloadForPackCalled) return` check to prevent reading from wrong store
**Reason:** Thought early reads were using wrong store
**Result:** Did NOT fix the problem

### Fix 4: Load progress in init() block
**File:** `VerbDrillViewModel.kt`
**Change:** Added coroutine in `init()` that loads progress immediately (like Daily Practice pattern)
**Reason:** Daily Practice loads in init, thought VerbDrill should too
**Result:** Did NOT fix the problem

### Fix 5: lateinit var instead of null store
**File:** `VerbDrillViewModel.kt`
**Change:** Changed `private var verbDrillStore = container.verbDrillStore(null)` to `private lateinit var verbDrillStore`
**Added:** `::verbDrillStore.isInitialized` checks throughout the file
**Reason:** Thought null store was causing issues
**Result:** Did NOT fix the problem

### Fix 6: Comprehensive logging
**File:** `VerbDrillStore.kt`
**Change:** Added detailed logging to all read/write operations
**Reason:** Needed to see what's actually happening
**Result:** Logs added but problem still exists

### Fix 7: Debug dialog
**Files:** `VerbDrillViewModel.kt`, `VerbDrillScreen.kt`, `VerbDrillUiState.kt`
**Change:** Added floating action button with bug icon that shows debug info
**Reason:** To see actual file paths and progress state on device
**Result:** Can now inspect state but problem still exists

### Fix 8: Remove duplicate store initialization
**File:** `VerbDrillViewModel.kt`
**Change:** Removed store creation from init block, left only reloadForPack() as single source
**Root cause identified:** TWO initialization paths creating different store instances
**Reason:** Init created store_A, LaunchedEffect created store_B which replaced store_A
**Result:** Did NOT fix the problem

### Fix 9: FLAT PATHS (Final working fix)
**Files:** `VerbDrillStore.kt`, `ProgressTracker.kt`, `BackupManager.kt`, `BackupFileCollector.kt`, `BackupRestorer.kt`
**Change:** Removed pack-scoped subdirectories entirely
- Before: `grammarmate/drills/{packId}/verb_drill_progress.yaml`
- After: `grammarmate/verb_drill_progress.yaml`
- Before: `grammarmate/drills/{packId}/verb_drill_last_session.yaml`
- After: `grammarmate/verb_drill_last_session.yaml`
**Reason:** Subdirectory structure was causing issues; flat paths work for all other stores
**Result:** **TO BE VERIFIED** - This is the current fix

---

## Key Findings from Agent Research

### Finding 1: File Path Structure (Original - Pack-Scoped)
**VerbDrillStore (BEFORE Fix 9):**
- Progress file: `grammarmate/drills/{packId}/verb_drill_progress.yaml`
- Session file: `grammarmate/drills/{packId}/verb_drill_last_session.yaml`
- Uses PACK-SCOPED paths with subdirectories

**Daily Practice (ProgressStore) - Always worked:**
- Progress file: `grammarmate/progress.yaml` (FLAT path, no subdirectories)
- All data embedded in single TrainingProgress object

### Finding 2: Dual Store Initialization (Fix 8 addressed this)
**VerbDrillViewModel had TWO paths creating stores:**

Path 1 - Init block:
```kotlin
init {
    viewModelScope.launch(Dispatchers.IO) {
        val progress = progressStore.load()
        val packId = progress.activePackId?.value
        if (packId != null) {
            verbDrillStore = container.verbDrillStore(packId)  // store_A
            progressMap = verbDrillStore.loadProgress()
        }
    }
}
```

Path 2 - LaunchedEffect in GrammarMateApp:
```kotlin
LaunchedEffect(verbDrillActivePackId, selectedLanguageId) {
    verbDrillVm.reloadForPack(verbDrillActivePackId.value)
}
```

Which calls:
```kotlin
fun reloadForPack(packId: String) {
    verbDrillStore = container.verbDrillStore(packId)  // store_B REPLACES store_A!
    // ...
}
```

**Race condition:** store_A created and loaded, then store_B created and replaced store_A.

### Finding 3: Why Flat Paths Work for Others
**StreakStore, MasteryStore, ProgressStore** all use flat paths:
- `grammarmate/streak_{languageId}.yaml`
- `grammarmate/mastery.yaml`
- `grammarmate/progress.yaml`

No subdirectories, no packId in path. Just simple file names.

### Finding 4: Why Pack-Scoped Paths Might Fail
**Hypothesis:** The pack-scoped subdirectory structure `drills/{packId}/` could cause issues:
1. Directory creation timing (mkdirs() might not work in all cases)
2. File system sync issues with nested directories
3. Android backup/restore behavior differs for nested vs flat paths
4. Path resolution issues between write and read operations

---

## Current Code State (After Fix 9 - Flat Paths)

### VerbDrillStore.kt
```kotlin
private val file: File = File(baseDir, "verb_drill_progress.yaml")  // FLAT!
private val lastSessionFile: File = File(baseDir, "verb_drill_last_session.yaml")  // FLAT!
```

No more `drills/{packId}/` subdirectories for progress files.

### VerbDrillViewModel.kt (After Fix 8)
```kotlin
init {
    sessionSize = container.configStore.load().sessionSize
    // Don't load cards or progress in init — wait for reloadForPack()
    // reloadForPack() is the SINGLE source of truth for store initialization
}
```

No store creation in init block. Only reloadForPack() creates the store.

---

## Why Fix 9 (Flat Paths) Should Work

1. **Same as working stores:** MasteryStore, StreakStore, ProgressStore all use flat paths
2. **No subdirectory issues:** No mkdirs() timing, no nested path resolution
3. **Simple file resolution:** Always `grammarmate/verb_drill_*.yaml`
4. **Proven pattern:** Every other store uses this pattern successfully

---

## Verification Steps for Fix 9

1. Install new APK
2. Complete 2-3 cards in VerbDrill
3. Exit app
4. Install NEW APK over old one
5. Open VerbDrill
6. **Expected:** Should see "Повторить/Продолжить" options, progress preserved
7. **Actual:** Report result

---

## Debug Tools Available

### Debug Dialog
- On VerbDrill screen, press floating action button with 🐛 icon
- Shows:
  - Current packId being used
  - Progress map size and contents
  - Last session state details
  - File paths and existence

### Logcat Commands
```bash
# See VerbDrillStore operations
adb logcat -d | grep "VerbDrillStore"

# See VerbDrillViewModel operations
adb logcat -d | grep "VerbDrillVM"

# Check if files exist
adb shell run-as com.alexpo.grammermate ls -la files/grammarmate/
adb shell run-as com.alexpo.grammermate cat files/grammarmate/verb_drill_progress.yaml
```

---

## Files Modified in This Saga

### VerbDrillStore.kt
- Added mkdirs() (Fix 1)
- Added logging (Fix 6)
- Changed to flat paths (Fix 9)

### VerbDrillViewModel.kt
- Added ensureProgressLoaded() (Fix 2)
- Added refreshLastSessionContext() guard (Fix 3)
- Added init loading (Fix 4)
- Changed to lateinit var (Fix 5)
- Removed init store creation (Fix 8)
- Added debug dialog (Fix 7)

### VerbDrillScreen.kt
- Added debug FAB and dialog (Fix 7)

### VerbDrillUiState.kt (in VerbDrillCard.kt)
- Added showDebugInfo and debugInfo fields (Fix 7)

### ProgressTracker.kt
- Updated to delete flat path files (Fix 9)

### BackupManager.kt
- Updated backup logic for flat paths (Fix 9)

### BackupFileCollector.kt
- Updated documentation (Fix 9)

### BackupRestorer.kt
- Updated restore logic with legacy migration (Fix 9)

---

## Success Criteria

Fix 9 is successful if:
1. After APK update, VerbDrill progress is preserved
2. "Повторить/Продолжить" options appear
3. everShownCardIds is maintained
4. No regression in other features

---

## If Fix 9 Doesn't Work

Next steps to investigate:
1. Check if files are actually created at all
2. Check if Android is deleting files for some reason
3. Check if there's a timing issue with file writes
4. Consider using database instead of YAML files
5. Consider using SharedPreferences for critical flags

---

## Commits

- `d8dad74`: Debug dialog addition
- Subsequent commit: Flat path implementation (Fix 9)
