# TASK-077: Verb Drill Progress Lost on APK Update - Deep Investigation

## Problem Statement

**Current behavior:** When APK is updated (new version installed over old version), verb drill progress is completely lost:
- `everShownCardIds` resets (all cards shown again)
- `lastSessionState` is lost (no "Повторить"/"Продолжить" options)
- User must start practice from scratch

**Expected behavior:** Files in `context.filesDir/grammarmate/` should persist across APK updates. This is standard Android behavior — `filesDir` is NOT cleared on app update.

**Previous fix attempt:** Updated `reloadForLanguage()` to use `currentPackId` when creating VerbDrillStore. **DID NOT WORK** - progress still lost on APK update.

**Key observation:** Other stores work correctly:
- **StreakStore:** Daily streak persists across APK updates ✓
- **MasteryStore:** Read cards (mastery) persists across APK updates ✓
- **ProgressStore:** Training progress persists across APK updates ✓

**Question:** What do these working stores do differently that VerbDrillStore doesn't?

---

## Investigation Tasks for Agents

### Task 1: Compare File Persistence Patterns

**Compare how these stores write files:**

1. **StreakStore** (`app/src/main/java/com/alexpo/grammermate/data/StreakStore.kt`)
   - File path construction
   - Write method (immediate vs batched)
   - Flush mechanism
   - AtomicFileWriter usage

2. **MasteryStore** (`app/src/main/java/com/alexpo/grammermate/data/MasteryStore.kt`)
   - File path construction
   - Write method
   - Flush mechanism
   - Cache invalidation

3. **ProgressStore** (`app/src/main/java/com/alexpo/grammermate/data/ProgressStore.kt`)
   - File path construction
   - Write method
   - Flush mechanism

4. **VerbDrillStore** (`app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt`)
   - File path construction (lines 54-64)
   - Write method (`persistProgressToDisk()`, lines 185-203)
   - Flush mechanism (line 177)

**Output:** Table comparing:
- File path (relative to filesDir)
- Write trigger (when write happens)
- Write method (immediate/batched)
- Flush usage (yes/no)
- Cache behavior

---

### Task 2: Trace Write Execution Path

**For VerbDrillStore, trace the COMPLETE path from user action to disk write:**

1. User completes a card in VerbDrillViewModel
2. `submitCorrectAnswer()` is called
3. `persistCardProgress()` is called (line ~564)
4. `verbDrillStore.upsertComboProgress()` is called (line 582)
5. `saveProgress()` → `persistProgressToDisk()` is called
6. `AtomicFileWriter.writeText()` is called

**Check each step:**
- Is the method actually called?
- Is `mutex.withLock` blocking the write?
- Is `progressCache` being updated?
- Is the file actually created on disk?

**Compare with MasteryStore:**
- Trace `recordCardShow()` → `save()` → disk write
- What's different?

**Output:** Call stack trace with line numbers for both stores. Highlight ANY differences.

---

### Task 3: Verify File System Behavior

**Check if files are actually created:**

1. Add logging to `VerbDrillStore.persistProgressToDisk()`:
   ```kotlin
   Log.d("VerbDrillStore", "Writing progress to: ${file.absolutePath}")
   Log.d("VerbDrillStore", "File exists before: ${file.exists()}")
   Log.d("VerbDrillStore", "Parent dir exists: ${file.parentFile?.exists()}")
   ```

2. Add logging to `AtomicFileWriter.writeText()`:
   ```kotlin
   Log.d("AtomicFileWriter", "Writing to: ${file.absolutePath}")
   Log.d("AtomicFileWriter", "Temp file: ${tempFile.absolutePath}")
   Log.d("AtomicFileWriter", "Write successful, renaming...")
   Log.d("AtomicFileWriter", "File exists after: ${file.exists()}")
   ```

3. Compare with MasteryStore logging (if any exists)

**Output:** Log statements to add, locations to add them.

---

### Task 4: Check Timing Issues

**Question:** Is the write happening AFTER the app is killed/backgrounded?

**Check:**
1. When does `flush()` get called? (Line 177 says "No-op: writes are immediate now")
2. Is there any async/coroutine delay between `upsertComboProgress()` and actual disk write?
3. Does `VerbDrillViewModel` call `flush()` on exit?
4. Compare with `MasteryStore` - when does it flush?

**In VerbDrillViewModel:**
- `exitSession()` (find this method)
- `persistSessionState()` (find this method)
- Do they call `flush()`?

**In TrainingViewModel:**
- How does it persist mastery on exit?
- Does it call `masteryStore.save()` or `flush()`?

**Output:** Timing diagram showing when writes happen vs when app is killed.

---

### Task 5: Check Android Backup/Restore Behavior

**Question:** Is Android backup/restore interfering?

**Check:**
1. Does the app have `android:allowBackup="true"` in manifest?
2. Is there `android:fullBackupContent` specified?
3. Are verb drill files excluded from backup?

**Compare:**
- Does MasteryStore have special backup handling?
- Does StreakStore have special backup handling?

**Output:** Manifest backup rules, any special backup handling in working stores.

---

## Expected Deliverables

1. **Comparison table** of working vs broken stores
2. **Root cause** identified (why VerbDrillStore doesn't persist but others do)
3. **Proposed fix** with code changes
4. **Verification plan** to test the fix

---

## Files to Investigate

**Working stores:**
- `app/src/main/java/com/alexpo/grammermate/data/StreakStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/MasteryStore.kt`
- `app/src/main/java/com/alexpo/grammermate/data/ProgressStore.kt`

**Broken store:**
- `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt`

**ViewModels:**
- `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt`
- `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt`

**Utilities:**
- `app/src/main/java/com/alexpo/grammermate/data/AtomicFileWriter.kt`

**Manifest:**
- `app/src/main/AndroidManifest.xml`

---

## Success Criteria

- Root cause identified with code evidence
- Fix proposed that matches the pattern of working stores
- Fix can be implemented in < 50 lines of code changes
- Fix addresses the SPECIFIC issue of APK update data loss
