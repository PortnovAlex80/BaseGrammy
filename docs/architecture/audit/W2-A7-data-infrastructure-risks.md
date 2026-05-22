# Wave 2 Agent A7: Data/Infrastructure Architecture Risk Analysis

**Generated:** 2026-05-22  
**Scope:** Data Layer and Infrastructure Risk Classification  
**Method:** Evidence-based risk analysis with file:line references  
**Input:** W1-A7-data-infrastructure-current-map.md

---

## Executive Summary

**Total Risks Found:** 12  
**HIGH Risk:** 4  
**MEDIUM Risk:** 6  
**LOW Risk:** 2

**Critical Issues:**
1. No transaction support for multi-file operations
2. Silent parser error handling causes data loss
3. Mixed global/pack-scoped state creates confusion
4. Complex migration paths with no rollback

**Most Dangerous Risk:** Silent parser error handling (Risk #1) - users lose data without notification

---

## Risk #1: Silent Parser Error Handling (Data Loss)

**Title:** Parsers silently skip malformed data without user feedback  

**Current Behavior:**
- `CsvParser.parseLesson()` skips lines with column count mismatches (lines 25-27)
- `CsvParser` returns empty title/card list on parse errors (line 48)
- No error reporting to user or logging of skipped lines
- Malformed CSV files silently produce incomplete lesson data

**Evidence:**
```kotlin
// D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\CsvParser.kt:25-27
if (columns.size != 2) {
    return@forEach  // Silent skip - no logging, no error
}
```

**Why This Is A Problem:**
- Users import lesson packs but don't realize some content failed to parse
- No way to detect data corruption until exercises are incomplete
- Silent data loss violates user expectations
- No audit trail for debugging import failures

**Blast Radius:**
- `CsvParser` - lesson content parsing
- `VocabCsvParser` - vocabulary drills  
- `VerbDrillCsvParser` - verb drills
- `StoryQuizParser` - story quizzes
- All pack import operations via `PackImporter`

**Proposed Direction:**
- Collect parsing errors in a `ParseResult` class
- Return partial success with error list
- Log all skipped lines with line numbers
- UI should show warnings for incomplete imports

**Risk Level:** HIGH

**Must-Have Tests Before Refactor:**
1. Test parser with malformed CSV (wrong column count)
2. Test parser with empty lines
3. Test parser with special characters
4. Verify error collection and reporting
5. Test partial import behavior

---

## Risk #2: No Transaction Support (Data Corruption)

**Title:** Multi-file operations lack atomicity guarantees  

**Current Behavior:**
- `PackImporter.importPackFromStream()` writes multiple files independently
- No transaction mechanism for cross-file operations
- App crash during import leaves partial state
- No rollback mechanism for failed operations

**Evidence:**
```kotlin
// D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\PackImporter.kt:98-99
private fun importPackFromStream(input: InputStream): LessonPack {
    // Multiple independent file writes with no transaction wrapper
    // Each AtomicFileWriter call is atomic, but the sequence is not
}
```

**Why This Is A Problem:**
- Pack import writes: manifest.json, lesson CSVs, drill CSVs, index updates
- If app crashes after lesson files but before index update, data is orphaned
- No way to recover from partial imports
- Users may need to reinstall app to fix corrupted state

**Blast Radius:**
- `PackImporter` - all pack import operations
- `BackupManager` - multi-file backup creation
- `ProgressStore` migrations - cross-file data moves
- Any operation touching >1 file

**Proposed Direction:**
- Implement Write-Ahead Log (WAL) for critical operations
- Use two-phase commit for multi-file writes
- Add transaction API: `beginTransaction()`, `commit()`, `rollback()`
- Store transaction state in recovery file

**Risk Level:** HIGH

**Must-Have Tests Before Refactor:**
1. Test pack import crash recovery
2. Test transaction rollback on error
3. Test WAL replay after crash
4. Test concurrent transaction conflicts
5. Verify no orphaned files after failed operations

---

## Risk #3: Mixed Global/Pack-Scoped State (Architectural Confusion)

**Title:** ProgressStore holds both global and migrated pack-scoped data  

**Current Behavior:**
- `ProgressStore` contains `activePackId`, `languageId`, `mode` (global state)
- `ProgressStore` also contains `dailyCursor` being migrated to pack-scoped (TASK-080)
- `PackDailyCursorStore` and `PackLessonProgressStore` created for isolation
- Migration methods temporarily duplicate data in both locations

**Evidence:**
```kotlin
// D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt:61-98
data class TrainingProgress(
    val languageId: LanguageId,
    val mode: TrainingMode,
    val activePackId: PackId?,  // Global state
    val dailyCursor: DailyCursorState?,  // Being migrated out
    // ... other global fields
)
```

**Why This Is A Problem:**
- Unclear ownership: where does each piece of data live?
- Developers don't know whether to check `ProgressStore` or pack-scoped stores
- Migration complexity increases with each mixed-state field
- Risk of reading stale data from wrong location

**Blast Radius:**
- `ProgressStore` - all global training progress
- `PackDailyCursorStore` - daily cursor per pack
- `PackLessonProgressStore` - lesson progress per pack
- All code reading training progress (ViewModels, UI)

**Proposed Direction:**
- Complete TASK-080/TASK-081 migrations
- Remove migrated fields from `ProgressStore`
- Document clear ownership: global = app settings, pack-scoped = progress
- Add deprecation warnings for global field access

**Risk Level:** HIGH

**Must-Have Tests Before Refactor:**
1. Test migration from global to pack-scoped
2. Verify no data loss during migration
3. Test reading from both old/new locations during transition
4. Test rollback if migration fails
5. Verify all consumers use correct data source

---

## Risk #4: Complex Migration Paths (Data Loss Risk)

**Title:** Three-way file migration with no rollback mechanism  

**Current Behavior:**
- `VerbDrillStore.migrateLegacyFiles()` checks pack-scoped → language-scoped → global files
- Migration copies files with priority chains but no atomic swap
- Source files deleted after copy, but failure leaves partial state
- No transaction or recovery mechanism

**Evidence:**
```kotlin
// D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\VerbDrillStore.kt:102-113
// Priority 1: Pack-scoped
val packScopedFile = File(baseDir, "drills/$packId/verb_drill_progress.yaml")
if (packScopedFile.exists()) {
    AtomicFileWriter.writeText(targetFile, packScopedFile.readText())
    if (packScopedFile.absolutePath != targetFile.absolutePath) {
        packScopedFile.delete()  // Delete after copy - no rollback
    }
    return true
}
// Priority 2: Language-scoped...
// Priority 3: Global...
```

**Why This Is A Problem:**
- If `writeText()` succeeds but `delete()` fails, data is duplicated
- If app crashes between copy and delete, users see stale data
- Migration errors return `false` but don't undo partial changes
- No way to retry failed migrations safely

**Blast Radius:**
- `VerbDrillStore` - verb drill progress and sessions
- `WordMasteryStore` - similar migration pattern
- All users upgrading from app versions with legacy file formats

**Proposed Direction:**
- Use atomic rename instead of copy + delete
- Implement migration transaction with rollback
- Add migration state tracking (completed/failed/pending)
- Support retry of failed migrations

**Risk Level:** HIGH

**Must-Have Tests Before Refactor:**
1. Test migration from all three legacy formats
2. Test migration failure scenarios
3. Test rollback on partial migration
4. Test retry of failed migrations
5. Verify no data loss in any scenario

---

## Risk #5: Inconsistent Write Verification (Silent Corruption)

**Title:** Only some stores verify file writes after AtomicFileWriter  

**Current Behavior:**
- `ProgressStore.save()` has explicit verification (lines 134-143)
- `VerbDrillStore.persistProgressToDisk()` has verification (lines 266-274)
- `MasteryStore.persistToFile()` has no verification
- `WordMasteryStore.upsertMastery()` has no verification

**Evidence:**
```kotlin
// D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt:134-143
// Has verification:
if (!file.exists()) {
    throw IOException("Failed to create file: ${file.name}")
}
if (file.length() == 0L) {
    throw IOException("File is empty after write: ${file.name}")
}

// D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\MasteryStore.kt:337
// No verification:
AtomicFileWriter.writeText(file, yaml.dump(data))
```

**Why This Is A Problem:**
- `AtomicFileWriter` can fail silently (disk full, permissions, Windows file locking)
- Without verification, stores return success but data is not persisted
- Users lose progress without any error indication
- Inconsistent verification suggests no clear pattern for when to verify

**Blast Radius:**
- `MasteryStore` - card show counts, mastery steps (critical user data)
- `WordMasteryStore` - word mastery state
- `PackDailyCursorStore` - daily cursor
- `PackLessonProgressStore` - lesson progress
- Any store without verification

**Proposed Direction:**
- Add verification to all `AtomicFileWriter.writeText()` calls
- Extract verification to helper function in `AtomicFileWriter`
- Make verification mandatory (part of the pattern)
- Log all write failures for debugging

**Risk Level:** MEDIUM

**Must-Have Tests Before Refactor:**
1. Test write failure scenarios (disk full, permissions)
2. Verify all stores check file existence after write
3. Verify all stores check file length after write
4. Test error handling when verification fails
5. Ensure no data loss on write failure

---

## Risk #6: Direct File IO in Business Logic (Leaky Abstraction)

**Title:** PackImporter contains complex file IO logic  

**Current Behavior:**
- `PackImporter` directly manages ZIP extraction, temp directories, file placement
- Business logic (pack import) tightly coupled to file system operations
- No abstraction layer between import logic and file IO
- Difficult to test business logic without real file system

**Evidence:**
```kotlin
// D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\PackImporter.kt:50-63
fun readPackManifestFromAssets(assetPath: String): LessonPackManifest {
    val input = context.assets.open(assetPath)
    input.use { stream ->
        val tempDir = extractZipToTemp(stream)  // File IO
        val manifestFile = File(tempDir, "manifest.json")  // File IO
        if (!manifestFile.exists()) {
            tempDir.deleteRecursively()  // File IO
            error("Manifest not found")
        }
        val manifest = LessonPackManifest.fromJson(manifestFile.readText())  // File IO
        tempDir.deleteRecursively()  // File IO
        return manifest
    }
}
```

**Why This Is A Problem:**
- Cannot test pack import logic without real file system
- File IO errors mixed with business logic errors
- Difficult to mock file system for unit tests
- Changing file system structure requires changing business logic

**Blast Radius:**
- `PackImporter` - all pack import/export operations
- `BackupManager` - similar file IO patterns
- `LanguageManager` - seed data management
- All code that directly manipulates files

**Proposed Direction:**
- Create `FileSystem` interface with `readFile()`, `writeFile()`, `deleteFile()`
- Implement `RealFileSystem` for production, `MockFileSystem` for tests
- Move file IO complexity to infrastructure layer
- Business logic should work with abstractions, not files

**Risk Level:** MEDIUM

**Must-Have Tests Before Refactor:**
1. Test pack import with mock file system
2. Test pack import with real file system (integration test)
3. Verify business logic works with both implementations
4. Test error handling in file system abstraction
5. Ensure no regression in import functionality

---

## Risk #7: Inconsistent Caching Strategies (Performance & Correctness)

**Title:** Some stores cache in memory, others read directly from disk  

**Current Behavior:**
- `MasteryStore` uses in-memory cache (`Map<languageId, Map<lessonId, LessonMasteryState>>`)
- `ProgressStore` has no cache, reads file directly on every `load()`
- `VerbDrillStore` has dual cache (progress cache + cards cache)
- `YamlListStore` uses volatile cache with manual invalidation

**Evidence:**
```kotlin
// D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\MasteryStore.kt:51
private val cache: MutableMap<LanguageId, MutableMap<LessonId, LessonMasteryState>>

// D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt:53-54
override fun load(): TrainingProgress = mutex.withLock {
    if (!file.exists() || file.length() == 0L) return TrainingProgress()
    val raw = try { yaml.load<Any>(file.readText()) } catch (_: Exception) { null }
    // No cache - reads file every time
}
```

**Why This Is A Problem:**
- No clear pattern for when to cache vs. when to read directly
- Performance characteristics unpredictable across stores
- Risk of stale data if caches not invalidated correctly
- `VerbDrillStore` cards cache never invalidated (assumes files don't change)

**Blast Radius:**
- All store implementations
- Code depending on store performance characteristics
- Memory usage (caches grow unbounded in some stores)

**Proposed Direction:**
- Define caching policy: when to cache, cache size limits, invalidation rules
- Implement consistent caching layer (e.g., LRU cache with size limits)
- Document cache behavior for each store
- Add cache statistics for debugging

**Risk Level:** MEDIUM

**Must-Have Tests Before Refactor:**
1. Test cache hit/miss behavior
2. Test cache invalidation on writes
3. Test cache size limits
4. Verify performance improvements with caching
5. Test memory usage with large datasets

---

## Risk #8: Ambiguous Error Handling (Silent Failures)

**Title:** YAML parsing errors return default values without logging  

**Current Behavior:**
- `ProgressStore.load()` catches all exceptions and returns `TrainingProgress()` (default)
- `MasteryStore.loadAllInternal()` preserves old cache on parse error (line 118)
- No distinction between "file doesn't exist" and "file is corrupted"
- Users don't know their data failed to load

**Evidence:**
```kotlin
// D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt:55
val raw = try { yaml.load<Any>(file.readText()) } catch (_: Exception) { null }
?: return TrainingProgress()  // Silent default - no logging

// D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\MasteryStore.kt:116-119
} catch (e: Exception) {
    Log.e("MasteryStore", "Failed to parse ${file.name}", e)
    cache = previousCache  // Preserve old data - but no user notification
}
```

**Why This Is A Problem:**
- Corrupted YAML files silently replaced with empty data
- Users lose progress without any error message
- No way to recover from corrupted files
- Difficult to debug data corruption issues

**Blast Radius:**
- All YAML-based stores (`ProgressStore`, `MasteryStore`, `VerbDrillStore`, etc.)
- Users upgrading from older app versions with schema changes
- Any code that relies on persistent data

**Proposed Direction:**
- Distinguish error types: file not found vs. parse error vs. schema mismatch
- Log all data loading errors with details
- Show user-facing error for corrupted data
- Add data recovery mechanism (backup file, automatic repair)

**Risk Level:** MEDIUM

**Must-Have Tests Before Refactor:**
1. Test loading corrupted YAML files
2. Test loading missing files
3. Test loading files with wrong schema version
4. Verify error logging and user notifications
5. Test data recovery from backups

---

## Risk #9: No Data Validation Layer (Corruption Propagation)

**Title:** No validation of data loaded from files  

**Current Behavior:**
- Parsers and stores load data directly without validation
- No schema enforcement beyond null coalescing
- Invalid data propagates to business logic
- No sanitization of user input in CSV files

**Evidence:**
```kotlin
// D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt:61-98
// No validation of field values:
return TrainingProgress(
    languageId = LanguageId(payload["languageId"] as? String ?: "en"),  // No validation
    mode = TrainingMode.valueOf(payload["mode"] as? String ?: TrainingMode.LESSON.name),  // Can throw
    // ... more fields without validation
)
```

**Why This Is A Problem:**
- Invalid enum values cause crashes
- Negative numbers where positive expected (e.g., counts, steps)
- Invalid IDs break referential integrity
- No way to detect corrupted data early

**Blast Radius:**
- All data loading operations
- Business logic depending on data validity
- UI rendering invalid data
- Database/file writes storing corrupted data

**Proposed Direction:**
- Implement data validation layer with schema definitions
- Validate all loaded data before using it
- Use safe defaults for invalid data with warnings
- Add data sanitization for user input

**Risk Level:** MEDIUM

**Must-Have Tests Before Refactor:**
1. Test loading valid data
2. Test loading invalid data (wrong types, out of range)
3. Test validation error handling
4. Test data sanitization
5. Verify no crashes with invalid data

---

## Risk #10: Singleton Store Factory (Memory Leak Risk)

**Title:** StoreFactory caches pack-scoped stores indefinitely  

**Current Behavior:**
- `StoreFactory` maintains `Map<String?, WordMasteryStoreImpl>` cache
- Cache evicted only on explicit `evict(packId)` call
- No automatic eviction based on usage or memory pressure
- Stores hold large in-memory caches

**Evidence:**
```kotlin
// D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\StoreFactory.kt:15-16
private val wordMasteryCache = mutableMapOf<String?, WordMasteryStoreImpl>()
private val verbDrillCache = mutableMapOf<String?, VerbDrillStoreImpl>()

// Eviction only on explicit call:
fun evict(packId: String?) {
    wordMasteryCache.remove(packId)
    verbDrillCache.remove(packId)
}
```

**Why This Is A Problem:**
- Users with many packs accumulate stores in memory
- Each store holds large in-memory caches (cards, progress)
- No automatic memory management
- Potential OutOfMemoryError on low-memory devices

**Blast Radius:**
- `StoreFactory` - all store instances
- Users with many lesson packs installed
- Low-memory Android devices

**Proposed Direction:**
- Implement LRU cache with size limit
- Automatic eviction based on last access time
- Weak references for rarely used stores
- Monitor memory usage and evict under pressure

**Risk Level:** LOW

**Must-Have Tests Before Refactor:**
1. Test cache eviction on memory pressure
2. Test LRU behavior with many packs
3. Verify no data loss when store evicted
4. Test memory usage with large datasets
5. Verify store recreation after eviction

---

## Risk #11: Thread Safety Verification Gap (Deadlock Risk)

**Title:** Mutex locks used throughout but no deadlock detection  

**Current Behavior:**
- All store implementations use `ReentrantLock` for thread safety
- No documented lock ordering
- No tests for concurrent access scenarios
- Risk of deadlocks if locks acquired in inconsistent order

**Evidence:**
```kotlin
// D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt:51
private val mutex = ReentrantLock()

// D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\MasteryStore.kt:48
private val mutex = ReentrantLock()

// No documented lock order - what if ProgressStore calls MasteryStore while holding lock?
```

**Why This Is A Problem:**
- Deadlocks possible if stores call each other while holding locks
- No timeout on lock acquisition
- No deadlock detection or recovery
- Difficult to reproduce and debug deadlocks

**Blast Radius:**
- All store implementations with mutex locks
- Code calling multiple stores in sequence
- Background threads accessing stores

**Proposed Direction:**
- Document lock ordering rules
- Use `tryLock()` with timeout instead of `withLock()`
- Add deadlock detection (e.g., lock ordering validation)
- Test concurrent access scenarios

**Risk Level:** LOW

**Must-Have Tests Before Refactor:**
1. Test concurrent reads from multiple threads
2. Test concurrent writes from multiple threads
3. Test mixed read/write scenarios
4. Verify no deadlocks with stress tests
5. Test lock timeout behavior

---

## Risk #12: No Index Rebuilding Mechanism (Data Recovery)

**Title:** Corrupted index files cannot be rebuilt  

**Current Behavior:**
- `LessonStore` reads index files (`languages.yaml`, `packs.yaml`) directly
- No fallback to scan disk if index corrupted
- No index repair or rebuild mechanism
- Index corruption = data loss

**Evidence:**
```kotlin
// D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\LessonStore.kt:103-107
private val languagesStore = YamlListStore<FileLanguage>(
    file = File(baseDir, "lessons/languages.yaml"),
    schemaVersion = 1
)
// No rebuild mechanism if file corrupted
```

**Why This Is A Problem:**
- Index files can be corrupted (app crash, disk error)
- No way to recover from corrupted index
- Users lose access to lessons/packs
- Requires app reinstall to fix

**Blast Radius:**
- `LessonStore` - all lesson/pack data
- `YamlListStore` - all YAML-based indexes
- Users with corrupted index files

**Proposed Direction:**
- Add index rebuild from disk scan
- Detect corrupted index and trigger rebuild
- Add index validation on load
- Create backup index files

**Risk Level:** LOW

**Must-Have Tests Before Refactor:**
1. Test index rebuild from disk
2. Test corrupted index detection
3. Test index validation
4. Verify no data loss during rebuild
5. Test rebuild performance

---

## Summary by Risk Category

### Business Logic in Infrastructure Layer
- **Risk #6:** Direct File IO in Business Logic (MEDIUM)

### Infrastructure Decisions Leaking into Business Logic
- **Risk #3:** Mixed Global/Pack-Scoped State (HIGH)
- **Risk #6:** Direct File IO in Business Logic (MEDIUM)

### Duplicated Rules Across Stores/Parsers
- **Risk #5:** Inconsistent Write Verification (MEDIUM)
- **Risk #7:** Inconsistent Caching Strategies (MEDIUM)
- **Risk #8:** Ambiguous Error Handling (MEDIUM)

### Direct File Writes Scattered
- **Risk #2:** No Transaction Support (HIGH)

### Hidden Coupling Through Shared File Access
- **Risk #3:** Mixed Global/Pack-Scoped State (HIGH)
- **Risk #10:** Singleton Store Factory (LOW)

### Ambiguous Ownership of Data Consistency
- **Risk #3:** Mixed Global/Pack-Scoped State (HIGH)
- **Risk #9:** No Data Validation Layer (MEDIUM)

### Test-Only Workarounds Leaking into Production
- None found

### No Transaction Support
- **Risk #2:** No Transaction Support (HIGH)

### Silent Parser Error Handling
- **Risk #1:** Silent Parser Error Handling (HIGH)

### Complex Migration Paths
- **Risk #4:** Complex Migration Paths (HIGH)

### Inconsistent Caching Strategies
- **Risk #7:** Inconsistent Caching Strategies (MEDIUM)

### Limited Test Coverage
- **Risk #11:** Thread Safety Verification Gap (LOW)
- **Risk #12:** No Index Rebuilding Mechanism (LOW)

---

## Recommended Fix Priority

### Phase 1: Critical Data Loss Risks (Immediate)
1. **Risk #1:** Silent Parser Error Handling
2. **Risk #2:** No Transaction Support
3. **Risk #8:** Ambiguous Error Handling

### Phase 2: Architectural Cleanup (Short-term)
4. **Risk #3:** Mixed Global/Pack-Scoped State
5. **Risk #4:** Complex Migration Paths
6. **Risk #5:** Inconsistent Write Verification

### Phase 3: Infrastructure Improvements (Medium-term)
7. **Risk #6:** Direct File IO in Business Logic
8. **Risk #7:** Inconsistent Caching Strategies
9. **Risk #9:** No Data Validation Layer

### Phase 4: Reliability Enhancements (Long-term)
10. **Risk #10:** Singleton Store Factory
11. **Risk #11:** Thread Safety Verification Gap
12. **Risk #12:** No Index Rebuilding Mechanism

---

## Testing Strategy Before Refactor

### Critical Tests Required
1. **Parser Error Handling Test Suite**
   - Malformed CSV files
   - Missing columns
   - Empty files
   - Special characters

2. **Transaction Test Suite**
   - Multi-file operations
   - Crash recovery
   - Rollback scenarios
   - Concurrent transactions

3. **Migration Test Suite**
   - All legacy formats
   - Partial migration failure
   - Rollback mechanisms
   - Data integrity verification

4. **Error Handling Test Suite**
   - Corrupted YAML files
   - Missing files
   - Invalid data types
   - Schema mismatches

### Coverage Targets
- Parser code: 95%+ coverage
- Transaction logic: 90%+ coverage
- Migration paths: 100% coverage
- Error handling: 90%+ coverage

---

**End of Wave 2 Agent A7 Report**
