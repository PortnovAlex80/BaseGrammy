# Wave 1 Agent A7: Data/Infrastructure Architecture Map

**Generated:** 2026-05-22  
**Scope:** Data Layer and Infrastructure (Stores, File IO, Parsers, DI)  
**Method:** Factual code analysis with file:line evidence

---

## Active User Flows

### Data Loading Flows

**App Startup → Lesson Loading**
1. `AppContainer.lessonsStore` lazy initialization → `StoreFactory.getLessonStore()` → `LessonStoreImpl` (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\AppContainer.kt:16)
2. `LessonStoreImpl.ensureSeedData()` → `LanguageManager.ensureSeedData()` (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\LessonStore.kt:151)
3. Default pack import from assets → `PackImporter.importPackFromAssets()` → ZIP extraction to temp → `AtomicFileWriter` for final files (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\PackImporter.kt:98)
4. `LessonStoreImpl.getLessons()` → in-memory cache check → `loadLessonsFromDisk()` → CSV parsing via `CsvParser` (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\LessonStore.kt:272-301)

**Session Start → Progress Load**
1. `TrainingViewModel` init → `ProgressStore.load()` → YAML parse with schema versioning (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt:53-100)
2. `MasteryStore.loadAll()` → in-memory cache population → nested Map structure (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\MasteryStore.kt:58-123)
3. Pack-scoped stores (`VerbDrillStore`, `WordMasteryStore`) → packId-based file resolution (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\VerbDrillStore.kt:56-65)

**Drill Practice → Progress Update**
1. Card completion → `VerbDrillStore.upsertComboProgress()` → load-modify-save pattern (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\VerbDrillStore.kt:194-199)
2. `WordMasteryStore.upsertMastery()` → cache update → `AtomicFileWriter.writeText()` (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\WordMasteryStore.kt:128-134)
3. `MasteryStore.recordCardShow()` → interval step calculation → `persistToFile()` (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\MasteryStore.kt:154-198)

### Data Saving Flows

**Progress Persistence**
1. `ProgressStore.save()` → mutex lock → YAML serialization → `AtomicFileWriter.writeText()` → file verification (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt:102-145)
2. Verb drill session state → `VerbDrillStore.saveLastSession()` → separate YAML file (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\VerbDrillStore.kt:334-363)
3. Pack-scoped lesson progress → `PackLessonProgressStore.savePackProgress()` → per-pack YAML files (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\PackLessonProgressStore.kt:89-122)

**Backup/Restore**
1. `BackupManager.createBackup()` → `BackupFileCollector.collectFiles()` → internal storage copy (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\BackupManager.kt:54-63)
2. SAF backup → user-accessible directory via `DocumentFile` API
3. Restore → `BackupRestorer.restoreFromPath()` → validation → `AtomicFileWriter` for all files

### Migration Flows

**TASK-080: Global to Pack-Scoped Cursor Migration**
1. `ProgressStore.migrateGlobalDailyCursorToPackScoped()` → check existing data → create `PackDailyCursorState` → `PackDailyCursorStore.savePackCursor()` (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt:151-206)
2. Clear global cursor from `progress.yaml` after successful migration

**TASK-081: Global to Pack-Scoped Lesson Progress Migration**
1. `ProgressStore.migrateGlobalLessonProgressToPackScoped()` → read raw YAML → extract legacy fields → create `PackLessonProgressState` (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt:208-307)
2. Save to per-pack file → clear legacy fields by reload + save

**VerbDrillStore Legacy File Migration**
1. Init-time check in `VerbDrillStoreImpl` → `migrateLegacyFiles()` (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\VerbDrillStore.kt:89-134)
2. Global → language-scoped → pack-scoped file migration with cleanup

---

## Main Classes and Responsibilities

### LessonStore (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\LessonStore.kt)

**Interface (Lines 9-86):**
- Seed data management: `ensureSeedData()`, `seedDefaultPacksIfNeeded()`, `updateDefaultPacksIfNeeded()`, `forceReloadDefaultPacks()`
- Language/pack CRUD: `getLanguages()`, `addLanguage()`, `getInstalledPacks()`, `getPackIdForLesson()`
- Pack import/export: `importPackFromUri()`, `importPackFromAssets()`, `removeInstalledPackData()`
- Lesson CRUD: `getLessons()`, `deleteLesson()`, `createEmptyLesson()`, `importFromUri()`
- Drill queries: `getVerbDrillFiles()`, `getVocabDrillFiles()`, `hasVerbDrill()`, `hasVocabDrill()`
- Story/vocab queries: `getStoryQuizzes()`, `getVocabEntries()`

**Implementation (LessonStoreImpl, Lines 98-476):**
- Delegates to `LanguageManager` for language/pack management
- Delegates to `PackImporter` for ZIP/URI import
- Delegates to `DrillFileManager` for drill file queries
- In-memory `lessonsCache` for `getLessons()` results (line 116)
- YAML-backed indexes: `languages.yaml`, `packs.yaml`, `stories.yaml`, `vocab.yaml`
- Direct file system access via `File` API
- Uses `AtomicFileWriter` for all writes (line 350, line 417)

**Data Format:**
- Languages: YAML list via `YamlListStore` (line 103)
- Packs: YAML list via `YamlListStore` (line 107)
- Lesson index: `lessons/{languageId}_index.yaml` with entries: `{id, title, file, drillFile?}` (line 468)
- Lesson content: CSV files parsed by `CsvParser` (line 298)

### Store Pattern Implementations

**ProgressStore** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt:46-308)
- File: `progress.yaml`
- Schema versioning (line 50)
- Thread-safe: `ReentrantLock` mutex (line 51)
- Load-modify-save pattern with verification (lines 134-143)
- Migration methods for TASK-080/TASK-081 (lines 151-307)
- Data: global training progress, daily cursor (being migrated out), app state

**MasteryStore** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\MasteryStore.kt:43-339)
- File: `mastery.yaml`
- In-memory cache: `Map<languageId, Map<lessonId, LessonMasteryState>>` (line 51)
- Lazy loading with `cacheLoaded` flag (line 52)
- Thread-safe: `ReentrantLock` mutex (line 48)
- Spaced repetition logic: `recordCardShow()` calculates interval steps (lines 154-198)
- Immediate write via `persistToFile()` → `AtomicFileWriter` (line 337)
- Data: card show counts, mastery steps, completion timestamps

**VerbDrillStore** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\VerbDrillStore.kt:51-375)
- Files: 
  - Progress: `drills/{packId}/verb_drill_progress.yaml` or `verb_drill_progress_{languageId}.yaml` (line 62-63)
  - Last session: `drills/{packId}/verb_drill_last_session.yaml` or `verb_drill_last_session_{languageId}.yaml` (line 64-65)
- Pack-scoped or language-scoped based on `packId` parameter (line 39)
- Legacy file migration on init (lines 89-134)
- Dual cache: progress cache (invalidated on save) + cards cache (never invalidated) (lines 73-78)
- Data: combo progress, last session state (VD-50)

**WordMasteryStore** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\WordMasteryStore.kt:37-188)
- File: `drills/{packId}/word_mastery.yaml` or `word_mastery.yaml` (lines 44-48)
- In-memory cache: `Map<wordId, WordMasteryState>` (line 53)
- Load-modify-save pattern for `upsertMastery()` (lines 128-134)
- Data: per-word mastery state, interval steps, learned status

**PackDailyCursorStore** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\PackDailyCursorStore.kt:36-172)
- File pattern: `daily_cursor_{packId}.yaml` (line 47)
- In-memory cache: `Map<packId, PackDailyCursorState>` (line 44)
- TASK-080: replaces global daily cursor in `progress.yaml`
- Data: sentence offset, lesson index, session hash, first session tracking

**PackLessonProgressStore** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\PackLessonProgressStore.kt:45-235)
- File pattern: `lesson_progress_{packId}.yaml` (line 56)
- In-memory cache: `Map<packId, PackLessonProgressState>` (line 53)
- TASK-081: replaces global lesson progress in `progress.yaml`
- Data: per-lesson progress (current index, counts, state)

**Other Stores:**
- `StreakStore`: `streak_{languageId}.yaml`, fire streak tracking (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\StreakStore.kt:28-285)
- `AppConfigStore`: `config.yaml`, app settings (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\AppConfigStore.kt:32-111)
- `BadSentenceStore`: flagged bad sentences
- `HiddenCardStore`: hidden cards from drills
- `VocabProgressStore`: vocab drill progress
- `ProfileStore`: user profile data
- `DrillProgressStore`: drill-specific progress

### File IO Infrastructure

**AtomicFileWriter** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\AtomicFileWriter.kt:7-90)

**Pattern (Lines 8-46):**
```kotlin
fun writeText(file: File, text: String, charset: Charset = Charsets.UTF_8) {
    // 1. Create parent dirs if needed (lines 9-12)
    // 2. Create temp file: {file.name}.tmp (lines 13-24)
    // 3. Write to temp + fsync (lines 25-28)
    // 4. Delete target if exists (Windows retry loop) (lines 31-41)
    // 5. Atomic rename temp → target (line 42)
    // 6. Error handling with cleanup (lines 43-45)
}
```

**Key Features:**
- Windows file locking workaround: retry loops with 10ms sleep (lines 18-24, 34-40)
- Crash safety: temp → fsync → rename pattern (line 27)
- Cross-platform: handles Windows rename atomicity limitations (line 29 comment)

**copyAtomic()** (Lines 52-89):
- Same pattern for file-to-file copies
- Used in backup/restore operations

**Usage Throughout Codebase:**
- All store implementations use `AtomicFileWriter.writeText()` for persistence
- `PackImporter` uses it for CSV lesson imports (line 88 in D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\PackImporter.kt)
- `AppConfigStore` seeds default config via `AtomicFileWriter` (line 61 in D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\AppConfigStore.kt)

### YAML Parsers

**YamlListStore** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\YamlListStore.kt:6-53)
- Generic YAML list storage wrapper
- Schema versioning support (line 9)
- Volatile cache with invalidation (lines 11-14)
- Read: supports both `Map<{items: [...]}>` and bare `List<>` formats (lines 16-37)
- Write: wraps in `{schemaVersion, items}` structure (lines 39-47)
- Used by: `LessonStore` for languages, packs, stories, vocab indexes

**SnakeYAML Library:**
- Version: 2.2 (per CLAUDE.md)
- Direct instantiation in stores: `private val yaml = Yaml()`
- Used for all structured data persistence

### CSV Parsers

**CsvLineParser** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\CsvLineParser.kt:8-36)
- Shared semicolon-delimited parser with quote handling
- Extracted from duplicated code in multiple parsers
- State machine: track `inQuotes`, split on `;` only when not in quotes
- Used by: `CsvParser`, `VocabCsvParser`, `VerbDrillCsvParser`

**CsvParser** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\CsvParser.kt:7-65)
- Parses lesson CSV files: `ru;answer1+answer2+answer3`
- Returns: `Pair<String?, List<SentenceCard>>` (title + cards)
- Title extraction: alphanumeric + limited punctuation, max 160 chars (lines 51-64)
- Card ID generation: `"card_$lineNumber"` (line 41)

**VocabCsvParser** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\VocabCsvParser.kt:13-38)
- Format: `nativeText;targetText;[hard]`
- Returns: `List<VocabRow>` with `isHard` flag (lines 7-11)
- Hard column parsing: `"hard"`, `"1"`, `"true"` (case-insensitive) (lines 29-31)

**VerbDrillCsvParser** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\VerbDrillCsvParser.kt:5-201)
- Two overloads: `parse(content: String)` and `parse(reader: BufferedReader)` (lines 14, 105)
- Streaming-safe: `BufferedReader` version avoids OOM (lines 105-185)
- Header parsing: auto-detects column indices for `ru`, `it`, `verb`, `tense`, `group`, `rank` (lines 37-50)
- Verb fallback: extracts from parenthetical hint e.g. `"я устал (essere stanco)"` → `"essere"` (lines 76-80, 165-167)
- Card ID: `"{group}_{tense}_{dataRowIndex}"` (line 82)

### Dependency Injection

**AppContainer** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\AppContainer.kt:13-37)

**Pattern:**
- Centralized DI container with lazy store initialization
- Singleton `StoreFactory` instance (line 14)
- Lazy properties for global stores (lines 16-26)
- Factory functions for pack-scoped stores (lines 29-32)
- Cache eviction hooks (lines 35-36)

**Exposed Dependencies:**
```kotlin
// Global stores
val lessonStore: LessonStore
val progressStore: ProgressStore
val configStore: AppConfigStore
val masteryStore: MasteryStore
val streakStore: StreakStore
// ... other global stores

// Pack-scoped stores (factory functions)
fun wordMasteryStore(packId: String?): WordMasteryStore
fun verbDrillStore(packId: String?): VerbDrillStore
fun packDailyCursorStore(): PackDailyCursorStore
val packLessonProgressStore: PackLessonProgressStore
```

**StoreFactory** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\StoreFactory.kt:12-102)

**Purpose:** Thread-safe singleton that caches store instances to prevent data loss from multiple independent instances (lines 6-8)

**Cache Structure:**
- Pack-scoped: `Map<String?, WordMasteryStoreImpl>`, `Map<String?, VerbDrillStoreImpl>` (lines 15-16)
- Singleton stores: lazy `by lazy` delegates (lines 19-28)

**Access Pattern:**
```kotlin
@Synchronized
fun getWordMasteryStore(packId: String?): WordMasteryStore {
    return wordMasteryCache.getOrPut(packId) {
        WordMasteryStoreImpl(appContext, packId = packId)
    }
}
```

**Cache Management:**
- `evict(packId)`: remove pack-scoped entries after reset (lines 77-81)
- `clearCache()`: full cache wipe (lines 86-90)

### Storage Format and File Organization

**Directory Structure** (relative to `context.filesDir`):
```
grammarmate/
├── config.yaml
├── progress.yaml
├── mastery.yaml
├── streak_{languageId}.yaml
├── seed_v1.done
├── lessons/
│   ├── languages.yaml
│   ├── {languageId}_index.yaml
│   └── {languageId}/
│       ├── lesson_{uuid}.csv
│       └── drill_{uuid}.csv
├── packs/
│   └── {packId}/
│       └── manifest.json
├── drills/
│   └── {packId}/
│       ├── verb_drill/
│       │   ├── {languageId}_{tense}.csv
│       │   └── ...
│       ├── vocab_drill/
│       │   └── {languageId}_vocab.csv
│       ├── verb_drill_progress.yaml
│       ├── verb_drill_last_session.yaml
│       └── word_mastery.yaml
├── stories/
│   ├── stories.yaml
│   └── {storyFile}.yaml
├── vocab/
│   ├── vocab.yaml
│   └── {languageId}/
│       └── {vocabFile}.csv
├── daily_cursor_{packId}.yaml
├── lesson_progress_{packId}.yaml
└── backup/
    └── backup_{timestamp}/
```

**File Format Patterns:**
- YAML: structured data with `schemaVersion` field
- CSV: semicolon-delimited with quote handling
- JSON: pack manifests only (`manifest.json`)

**Schema Versioning:**
- All stores include `schemaVersion: Int` field
- Used for migration detection
- Current version: 1 (most stores)

### Helper Classes

**DrillFileManager** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\DrillFileManager.kt:10-100)
- Extracted from `LessonStore` to manage file complexity
- Story quiz queries via `storiesStore` index (lines 21-35)
- Vocab entry queries via `vocabStore` index (lines 39-71)
- File cleanup for language/pack removal (lines 73-92, 94-109)
- Delegates to parsers: `StoryQuizParser`, `VocabCsvParser`, `ItalianDrillVocabParser`

**PackImporter** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\PackImporter.kt:20-100)
- ZIP stream processing: `extractZipToTemp()` → temp directory → file placement
- Manifest parsing: `LessonPackManifest.fromJson()`
- Lesson file copying with index updates
- Drill file import (stories, vocab)
- SAF URI import support
- **Critical:** All final writes use `AtomicFileWriter` (line 88)

**LanguageManager** (referenced in D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\LessonStore.kt:125-128)
- Seed data management
- Language CRUD operations
- Pack manifest reading/writing
- Default pack asset loading

---

## Business Rules Found

### Data Consistency Rules

**Single Store Instance Rule** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\StoreFactory.kt:6-8)
- All consumers must go through `StoreFactory` to prevent data loss
- Multiple independent instances cause: load-modify-save races, stale caches
- Enforced via `@Synchronized` accessors and singleton pattern

**Pack-Scoped Data Isolation** (TASK-080, TASK-081)
- `PackDailyCursorStore`: each pack maintains independent cursor state
- `PackLessonProgressStore`: each pack maintains independent lesson progress
- Migration logic moves global data to pack-scoped files
- Rationale: prevent cross-pack contamination

**Mastery Calculation Rules** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\MasteryStore.kt:154-198)
- `recordCardShow()` increments `uniqueCardShows` only for new cards (line 181)
- `totalCardShows` increments for all shows (line 185)
- Interval step calculation: `SpacedRepetitionConfig.nextIntervalStep()` based on days since last show and on-time status (lines 171-176)
- Lesson completion: `completedAtMs` set once, immutable thereafter (line 227)

**WORD_BANK ≠ Mastery Rule** (CLAUDE.md)
- Only VOICE and KEYBOARD practice grow flowers
- WORD_BANK practice never counts toward mastery
- Implemented in `MasteryStore.recordCardShow()` callers filter by practice type

**Streak Calculation Rules** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\StreakStore.kt:217-252)
- Day boundary: Calendar day comparison (year + day_of_year) (lines 228-234)
- Same day: no streak increment (line 233)
- Consecutive day: streak +1 (line 247)
- Missed day: streak reset to 1 (line 251)
- Fire streak: track `completedTypesToday` set, reset on new day (lines 142-157)

**VerbDrill Session Rules** (VD-50)
- Last session saved regardless of age (no staleness check) (line 34 comment in D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\VerbDrillStore.kt)
- `sessionCardIds`: ordered list of cards in session
- `currentIndex`: position within session
- `todayShownCardIds`: filtered by date comparison (lines 164-171)
- Repeat replays full saved batch in order
- Continue excludes checked/shown cards

### Migration Logic

**TASK-080: Global Cursor Migration** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt:151-206)
- Check if global cursor has meaningful data (lines 160-166)
- Determine target pack ID: parameter or `activePackId` (lines 173-177)
- Create `PackDailyCursorState` from global cursor (lines 181-190)
- Save to pack-specific file (line 193)
- Clear global cursor from `progress.yaml` (lines 196-199)

**TASK-081: Global Lesson Progress Migration** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt:208-307)
- Read raw YAML to access legacy fields (lines 213-231)
- Check for meaningful lesson progress (lines 243-251)
- Create `PackLessonProgressState` with lesson ID from legacy data (lines 267-290)
- Save to pack-specific file (line 293)
- Clear legacy fields by reload + save (lines 296-300)

**VerbDrillStore Legacy Migration** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\VerbDrillStore.kt:89-134)
- Priority: pack-scoped > language-scoped > global (lines 102-107, 117-122)
- Atomic file copy via `AtomicFileWriter.writeText()` (lines 110, 125)
- Source cleanup: delete only if global or if pack-scoped target (lines 111-113, 126-128)

### Cache Invalidation Rules

**LessonStore Cache** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\LessonStore.kt:286-288)
- Invalidate on pack import: `importPackFromUri()`, `importPackFromAssets()` (lines 219, 225)
- Invalidate on pack removal: `removeInstalledPackData()` (line 253)
- Invalidate on lesson changes: `deleteLesson()`, `createEmptyLesson()`, `importFromUri()` (lines 338, 352, 266)
- Selective eviction: pass `languageId` to clear one entry, or `null` for all (line 287)

**MasteryStore Cache** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\MasteryStore.kt:50-52)
- Lazy load on first access (line 63)
- Persist to disk on every mutation (line 144, 197, 217, 235, 266)
- No explicit invalidation: cache is source of truth after load

**VerbDrillStore Dual Cache** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\VerbDrillStore.kt:73-78)
- Progress cache: invalidated on save (line 186)
- Cards cache: never invalidated (files don't change at runtime) (line 77 comment)

**YamlListStore Cache** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\YamlListStore.kt:11-14)
- Volatile `cachedData` + `cacheValid` flags
- Invalidated on write (line 40)
- Manual invalidation via `invalidateCache()` (line 49)

---

## Infrastructure Dependencies

### External Libraries

**SnakeYAML 2.2**
- YAML parsing and serialization
- Used in all stores for structured data
- Direct instantiation: `Yaml()` constructor
- Load API: `yaml.load<Any>(file.readText())`
- Dump API: `yaml.dump(data)`

**Android Framework**
- `java.io.File`: file system operations
- `android.content.Context`: access to `filesDir`, `assets`
- `android.net.Uri`: SAF URI handling
- `android.util.Log`: error logging
- `java.util.concurrent.locks.ReentrantLock`: thread safety
- `java.util.concurrent.TimeUnit`: streak day calculations

### File System Dependencies

**Internal App-Private Storage**
- Base directory: `context.filesDir/grammarmate/`
- Crash-safe: `AtomicFileWriter` prevents corruption
- Thread-safe: mutex locks prevent concurrent write conflicts
- No external permissions required

**Storage Access Framework (SAF)**
- Used for pack import: `ContentResolver.openInputStream(uri)`
- Used for backup export: `DocumentFile` tree API
- Requires user interaction for directory access

**Assets**
- Default packs: `grammarmate/packs/*.zip`
- Default config: `grammarmate/config.yaml`
- Read-only, seeded to internal storage on first run

### Error Handling Infrastructure

**File Write Verification** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt:134-143)
```kotlin
if (!file.exists()) {
    Log.e("ProgressStore", "Файл не создан после записи: ${file.absolutePath}")
    throw IOException("Failed to create file: ${file.name}")
}
if (file.length() == 0L) {
    Log.e("ProgressStore", "Файл пустой после записи: ${file.absolutePath}")
    throw IOException("File is empty after write: ${file.name}")
}
```

**Try-Catch with Fallback** (D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\MasteryStore.kt:116-119)
```kotlin
} catch (e: Exception) {
    Log.e("MasteryStore", "Failed to parse ${file.name}", e)
    cache = previousCache  // Preserve old data on parse failure
}
```

**Null-Coalescing Defaults** (pervasive pattern)
- `yaml.load<Any>()` returns null on error → `?: return defaultValue`
- Example: `val data = raw as? Map<*, *> ?: return TrainingProgress()` (line 58 in D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt)

**Migration Error Handling**
- Migration failures return `false` but don't throw (lines 204, 305 in D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt)
- Errors logged but don't block app startup
- One-way migration: no rollback on failure

---

## State Mutations

### Write Patterns

**Immediate Write Pattern**
- Most stores: `save()` → `AtomicFileWriter.writeText()` immediately
- Examples: `ProgressStore.save()` (line 102), `MasteryStore.save()` (line 136)
- Rationale: simplicity, no write-behind batching

**Load-Modify-Save Pattern**
- `VerbDrillStore.upsertComboProgress()`: load map → modify → save map (lines 194-199)
- `WordMasteryStore.upsertMastery()`: load map → modify → save map (lines 128-134)
- Rationale: avoid full-file rewrite for single-record updates

**Cache-Then-Persist Pattern**
- `MasteryStore`: update in-memory cache → `persistToFile()` (lines 141-144)
- `YamlListStore`: invalidate cache → write → rebuild cache on next read (lines 40-46)

### Read Patterns

**Lazy Loading**
- `MasteryStore.loadAll()`: check `cacheLoaded` flag (line 63)
- `VerbDrillStore.loadProgress()`: check `progressCache` (line 137)
- Rationale: avoid disk I/O if data never accessed

**Cache-First Reads**
- `LessonStore.getLessons()`: check `lessonsCache[languageId]` (line 275)
- `YamlListStore.read()`: check `cacheValid` (line 17)
- Rationale: performance for repeated reads

**Streaming Reads**
- `VerbDrillCsvParser.parse(reader)`: process line-by-line (lines 117-182)
- Rationale: OOM-safe for large CSV files

### Concurrency Control

**Mutex Locks**
- All store implementations use `ReentrantLock` (e.g., line 51 in D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt)
- Pattern: `mutex.withLock { ... }` for write operations
- Example: `ProgressStore.save()` (line 103)

**Synchronized Factory Access**
- `StoreFactory` accessors are `@Synchronized` (lines 32, 40)
- Prevents race conditions in cache population
- Example: `getWordMasteryStore()` (lines 32-37)

**Volatile Cache Fields**
- `YamlListStore`: `@Volatile private var cachedData` (line 11)
- Ensures visibility across threads
- No lock needed for reads (write invalidates cache)

---

## Test Coverage

### Unit Tests Found

**CsvParserTest** (D:\Development\BaseGrammy\app\src\test\java\com\alexpo\grammermate\data\CsvParserTest.kt)
- Tests lesson CSV parsing
- Covers title extraction, card generation, answer splitting

**VocabCsvParserTest** (D:\Development\BaseGrammy\app\src\test\java\com\alexpo\grammermate\data\VocabCsvParserTest.kt)
- Tests vocab CSV parsing
- Covers hard column parsing, quote handling

**StoryQuizParserTest** (D:\Development\BaseGrammy\app\src\test\java\com\alexpo\grammermate\data\StoryQuizParserTest.kt)
- Tests story quiz YAML parsing
- Covers question/answer parsing

**PomodoroHistoryStoreTest** (D:\Development\BaseGrammy\app\src\test\java\com\alexpo\grammermate\data\PomodoroHistoryStoreTest.kt)
- Tests Pomodoro history persistence
- Likely covers YAML serialization/deserialization

### Coverage Gaps

**No Tests Found For:**
- `AtomicFileWriter` crash safety (critical infrastructure)
- `StoreFactory` singleton/caching behavior
- `ProgressStore` migration logic (TASK-080, TASK-081)
- `MasteryStore` spaced repetition calculations
- `VerbDrillStore` session state persistence
- `WordMasteryStore` due word calculations
- `StreakStore` day boundary logic
- `LessonStore` pack import/export
- `BackupManager` backup/restore operations

**Risky Areas:**
- Migration logic: complex, one-way, no rollback
- File I/O errors: Windows-specific retry loops untested
- Concurrent access: mutex correctness unverified
- Cache invalidation: subtle bugs possible

---

## Risks / Unclear Points

### Architectural Concerns

**1. Mixed Global and Pack-Scoped State**
- **Issue:** `ProgressStore` still holds global state (`activePackId`, `languageId`, `mode`) alongside data being migrated to pack-scoped stores
- **Evidence:** D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\ProgressStore.kt:61-98 (global fields remain after migration)
- **Risk:** Confusion about what lives where, potential for data inconsistency
- **Status:** Partially addressed by TASK-080/TASK-081, but global store remains

**2. Dual Cache Strategies**
- **Issue:** Some stores use in-memory cache (`MasteryStore`, `VerbDrillStore`), others don't (`ProgressStore`, `AppConfigStore`)
- **Evidence:** Compare `MasteryStore.cache` (line 51) vs `ProgressStore` (no cache, direct file reads)
- **Risk:** Inconsistent performance characteristics, potential for stale data
- **Status:** No clear pattern for when to cache vs. when to read directly

**3. Migration Complexity**
- **Issue:** Three-way migration (global → language-scoped → pack-scoped) with fallback logic
- **Evidence:** `VerbDrillStore.migrateLegacyFiles()` priority chains (lines 102-107, 117-122 in D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\VerbDrillStore.kt)
- **Risk:** Edge cases in migration paths, potential data loss if migration fails mid-way
- **Status:** No rollback mechanism, errors return `false` but don't undo partial changes

**4. File Write Verification Inconsistency**
- **Issue:** Only some stores verify writes after `AtomicFileWriter`
- **Evidence:** `ProgressStore` has explicit checks (lines 134-143), `MasteryStore` does not
- **Risk:** Silent data corruption if `AtomicFileWriter` fails unexpectedly
- **Status:** Verification is ad-hoc, not enforced by pattern

**5. Cache Invalidation Timing**
- **Issue:** `LessonStore.lessonsCache` is invalidated on import/delete, but cache is never explicitly refreshed
- **Evidence:** D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\LessonStore.kt:286-288 (invalidate only, no reload)
- **Risk:** Stale data if external process modifies files (though unlikely in Android app-private storage)
- **Status:** Probably safe, but no explicit cache refresh mechanism

**6. Singleton Store Factory**
- **Issue:** `StoreFactory` is a singleton that lives for app lifetime
- **Evidence:** D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\StoreFactory.kt:92-100 (double-checked locking)
- **Risk:** Memory leak if caches grow unbounded (though stores are singleton, so cache size is bounded)
- **Status:** `evict()` and `clearCache()` provided, but not automatically called

**7. Thread Safety Verification**
- **Issue:** Mutex locks used throughout, but no deadlock detection or testing
- **Evidence:** `ReentrantLock` in every store implementation
- **Risk:** Potential deadlocks if lock ordering is inconsistent across stores
- **Status:** No lock order documentation, no tests for concurrent access

### Missing Infrastructure

**1. No Transaction Support**
- **Issue:** Multi-file updates (e.g., pack import) are not atomic
- **Evidence:** `PackImporter` writes multiple files independently
- **Risk:** Partial pack import if app crashes mid-import
- **Status:** No WAL (write-ahead log) or transaction mechanism

**2. No Data Validation Layer**
- **Issue:** Parsers silently skip malformed lines (e.g., `CsvParser` returns empty for bad lines)
- **Evidence:** `CsvParser.parseLesson()` lines 26-27 (`return@forEach` on column mismatch)
- **Risk:** Silent data loss, no user feedback for malformed content
- **Status:** No validation schema, no error reporting

**3. No Backup Versioning**
- **Issue:** `BackupManager` creates timestamped backups, but no format versioning
- **Evidence:** `BackupManager.createBackup()` creates `backup_{timestamp}` directories
- **Risk:** Cannot restore old backup formats after schema changes
- **Status:** No migration path for backup files

**4. No Index Rebuilding**
- **Issue:** If YAML index files (`languages.yaml`, `packs.yaml`) are corrupted, no rebuild mechanism
- **Evidence:** `LessonStore` reads indexes directly, no fallback to disk scan
- **Risk:** Data loss if index files corrupted
- **Status:** No index repair tool

### Unclear Points

**1. VerbDrillStore LanguageId Extraction**
- **Question:** What happens if `packId` doesn't follow `ru-{lang}-v1` pattern?
- **Evidence:** D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\VerbDrillStore.kt:81-87 (simple split)
- **Risk:** Default to `"en"` may be incorrect for non-standard pack IDs
- **Status:** No validation, silent fallback

**2. MasteryStore Interval Step Calculation**
- **Question:** Is `SpacedRepetitionConfig.wasRepetitionOnTime()` correct for all edge cases?
- **Evidence:** D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\MasteryStore.kt:171 (delegates to config)
- **Risk:** Incorrect mastery progression if config has bugs
- **Status:** Config implementation not reviewed in this audit

**3. YamlListStore Schema Versioning**
- **Question:** What happens when `schemaVersion` changes?
- **Evidence:** D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\YamlListStore.kt:9 (version field exists)
- **Risk:** No migration logic, version is written but never checked
- **Status:** Version is inert, no enforcement

**4. AtomicFileWriter Windows Retry Logic**
- **Question:** Is 10 retries with 10ms sleep sufficient for all Windows file locking scenarios?
- **Evidence:** D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\AtomicFileWriter.kt:18-24 (retry loop)
- **Risk:** May still fail under heavy AV scanner load or network drives
- **Status:** No empirical data on failure rates

---

## Evidence

### File References

**Core Data Layer:**
- Stores: `D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\*Store.kt` (17 files)
- Models: `D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\Models.kt`
- DI: `D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\AppContainer.kt`
- Factory: `D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\StoreFactory.kt`

**File IO:**
- AtomicFileWriter: `D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\AtomicFileWriter.kt`
- BackupManager: `D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\BackupManager.kt`
- BackupFileCollector: `D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\BackupFileCollector.kt`
- BackupRestorer: `D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\BackupRestorer.kt`

**Parsers:**
- YAML: `D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\YamlListStore.kt`
- CSV: `D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\CsvParser.kt`
- Vocab CSV: `D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\VocabCsvParser.kt`
- VerbDrill CSV: `D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\VerbDrillCsvParser.kt`
- CSV Line: `D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\CsvLineParser.kt`

**Helpers:**
- DrillFileManager: `D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\DrillFileManager.kt`
- PackImporter: `D:\Development\BaseGrammy\app\src\main\java\com\alexpo\grammermate\data\PackImporter.kt`
- LanguageManager: Referenced in LessonStore, implementation in separate file

**Tests:**
- `D:\Development\BaseGrammy\app\src\test\java\com\alexpo\grammermate\data\CsvParserTest.kt`
- `D:\Development\BaseGrammy\app\src\test\java\com\alexpo\grammermate\data\VocabCsvParserTest.kt`
- `D:\Development\BaseGrammy\app\src\test\java\com\alexpo\grammermate\data\StoryQuizParserTest.kt`
- `D:\Development\BaseGrammy\app\src\test\java\com\alexpo\grammermate\data\PomodoroHistoryStoreTest.kt`

### Line Evidence for Key Claims

**AtomicFileWriter Pattern:**
- Usage: `LessonStoreImpl.createEmptyLesson()` line 350
- Usage: `ProgressStore.save()` line 132
- Usage: `MasteryStore.persistToFile()` line 337
- Usage: `VerbDrillStore.persistProgressToDisk()` line 263

**StoreFactory Singleton:**
- Instance management: lines 92-100
- Pack-scoped caching: lines 15-16
- Singleton caching: lines 19-28
- Synchronized access: lines 32, 40

**Migration Logic:**
- TASK-080: `ProgressStore.migrateGlobalDailyCursorToPackScoped()` lines 151-206
- TASK-081: `ProgressStore.migrateGlobalLessonProgressToPackScoped()` lines 208-307
- VerbDrill legacy: `VerbDrillStore.migrateLegacyFiles()` lines 89-134

**Cache Invalidation:**
- LessonStore: `invalidateLessonsCache()` lines 286-288
- MasteryStore: No explicit invalidation, cache is source of truth
- YamlListStore: `invalidateCache()` line 49

**Thread Safety:**
- Mutex usage: `ProgressStore` line 51, `MasteryStore` line 48, `VerbDrillStore` line 67
- Synchronized factory: `StoreFactory.getWordMasteryStore()` line 32
- Volatile cache: `YamlListStore` line 11

---

## Summary

The BaseGrammy data layer is a **YAML-backed, file-based persistence system** with the following characteristics:

**Strengths:**
- Crash-safe file writes via `AtomicFileWriter` temp → fsync → rename pattern
- Thread-safe store implementations with mutex locks
- Centralized DI via `AppContainer` and `StoreFactory`
- Pack-scoped data isolation (TASK-080, TASK-081)
- Schema versioning foundation for future migrations

**Weaknesses:**
- No transaction support for multi-file operations
- Inconsistent caching strategies across stores
- Complex migration paths with no rollback
- Limited test coverage for critical infrastructure
- Silent error handling in parsers (data loss risk)

**Key Patterns:**
- Load-modify-save for single-record updates
- Immediate writes (no write-behind batching)
- Lazy loading with cache-first reads
- Pack-scoped stores for drill data, global stores for app state

**Technical Debt:**
- Mixed global/pack-scoped state in `ProgressStore`
- Inconsistent write verification
- No data validation layer
- No backup format versioning
- Untested concurrent access scenarios

---

**End of Wave 1 Agent A7 Report**
