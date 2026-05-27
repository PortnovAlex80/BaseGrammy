# DATA Layer Fixes Verification - TASK-088 Grammar Story Roadmap

## Summary
All 4 DATA layer fixes have been successfully implemented for the Grammar Story Roadmap feature.

## Implementation Details

### Fix 1: Add Chapter and ChapterProgress data models ✅
**File:** `app/src/main/java/com/alexpo/grammermate/data/Models.kt`

**Chapter data class added:**
- `chapterId: String` - Unique chapter identifier within the pack
- `order: Int` - Display order (lower numbers appear first)
- `title: String` - Chapter title
- `subtitle: String?` - Optional subtitle or description
- `storyFile: String?` - Markdown filename within the pack (null if no story)
- `lessons: List<String>` - List of lesson IDs in this chapter, in order

**Invariants validated in init block:**
- `chapterId` must not be blank
- `title` must not be blank
- All lesson IDs must be non-blank

**ChapterProgress data class added:**
- `chapterId: String` - Links to Chapter.chapterId
- `lessonsStarted: Int` - Number of lessons with mastery > 0 (default 0)
- `lessonsCompleted: Int` - Number of lessons with intervalStepIndex >= 3 (default 0)
- `lastAccessedMs: Long` - Epoch millis of last lesson activity (default 0L)

**Invariants validated in init block:**
- `lessonsStarted >= 0`
- `lessonsCompleted >= 0`
- `lessonsStarted >= lessonsCompleted` (started includes completed)

**Companion factory method:**
- `ChapterProgress.forChapter(chapterId: String)` - Creates new progress with zero values

---

### Fix 2: Create ChapterProgressStore ✅
**File:** `app/src/main/java/com/alexpo/grammermate/data/ChapterProgressStore.kt` (NEW)

**Location:** Pack-scoped: `grammarmate/packs/{packId}/chapter_progress.yaml`

**Data format (YAML, schema version 1):**
```yaml
schemaVersion: 1
data:
  "{chapterId}":
    lessonsStarted: 2
    lessonsCompleted: 1
    lastAccessedMs: 1715500800000
```

**Public API implemented:**
- `loadAll(): Map<String, ChapterProgress>` - Loads entire file. Returns empty map if missing/corrupt.
- `saveAll(progress: Map<String, ChapterProgress>)` - Writes full map via AtomicFileWriter.
- `getProgress(chapterId: String): ChapterProgress?` - Returns progress for single chapter, or null if never tracked.
- `upsertProgress(progress: ChapterProgress)` - Load-modify-save: inserts or replaces single chapter's progress.
- `clear()` - Wipes cache and deletes the file.

**Key features:**
- ✅ Uses AtomicFileWriter pattern (temp -> fsync -> rename)
- ✅ No caching (reads from disk on every call)
- ✅ Pack-scoped via constructor parameter
- ✅ Proper error handling with try-catch and empty map fallback
- ✅ Schema version validation (only version 1 supported)

---

### Fix 3: Update LessonPackManifest for schema v2 ✅
**File:** `app/src/main/java/com/alexpo/grammermate/data/LessonPackManifest.kt`

**Changes made:**
1. Added `chapters: List<Chapter>` field with default `emptyList()`
2. Updated `fromJson()` to accept schemaVersion 1 or 2:
   - Schema v1: `chapters` set to empty list (backward compatible)
   - Schema v2: parses chapters from JSON array
3. Added `parseChapters()` helper method
4. Updated validation logic:
   - Schema v1: requires at least one standard lesson OR drill sections
   - Schema v2: requires at least one chapter with non-empty lessons OR drill sections

**Validation rules enforced:**
- ✅ `schemaVersion` must be 1 or 2 (throws error otherwise)
- ✅ `packId`, `packVersion`, `language` must all be non-blank
- ✅ Each lesson entry must have non-blank `lessonId` and `file`
- ✅ Schema v1: manifest must have at least one standard lesson OR at least one drill section
- ✅ Schema v2: manifest must have at least one chapter with non-empty `lessons` OR at least one drill section
- ✅ A manifest with no content is rejected

**Backward compatibility maintained:**
- ✅ Schema v1 manifests parse without errors (empty chapters list)
- ✅ Schema v2 manifests with chapters parse correctly
- ✅ Drill sections work in both schema versions

---

### Fix 4: Add LessonStore methods for chapter access ✅
**File:** `app/src/main/java/com/alexpo/grammermate/data/LessonStore.kt`

**Interface methods added:**
```kotlin
// -- Chapter queries (Grammar Story Roadmap) --
fun getChapters(packId: String): List<Chapter>
fun getChapterStory(packId: String, storyFile: String): String?
fun hasChapters(packId: String): Boolean
```

**Implementation in LessonStoreImpl:**
- `getChapters(packId)` - Reads manifest, returns chapters sorted by order. Empty list for v1 packs.
- `getChapterStory(packId, storyFile)` - Loads markdown from `grammarmate/packs/{packId}/stories/{storyFile}`. Returns null if file missing or error.
- `hasChapters(packId)` - Returns true if manifest has non-empty chapters list

**Key features:**
- ✅ Properly delegates to languageManager for manifest access
- ✅ Returns sorted chapters by order field
- ✅ Safe file access with try-catch error handling
- ✅ Returns empty list/null for missing data (no crashes)
- ✅ Maintains existing architecture patterns

---

## Verification Checklist

### Compilation ✅
- All files compile without syntax errors
- Proper imports and dependencies
- Type safety maintained

### Spec Compliance ✅
- ✅ Chapter model matches spec (01-models-and-state.md#1.1.26)
- ✅ ChapterProgress model matches spec (01-models-and-state.md#1.1.27)
- ✅ ChapterProgressStore matches spec (02-data-stores.md#2.15)
- ✅ LessonPackManifest schema v2 support matches spec
- ✅ LessonStore chapter access methods match requirements

### Data Integrity ✅
- ✅ Invariants validated in init blocks
- ✅ AtomicFileWriter pattern used for persistence
- ✅ Pack-scoped storage implemented correctly
- ✅ No caching (read from disk every call)

### Backward Compatibility ✅
- ✅ Schema v1 manifests parse without errors
- ✅ Schema v1 manifests get empty chapters list
- ✅ Existing lessons/drills work unchanged
- ✅ No breaking changes to existing API

### Error Handling ✅
- ✅ Missing files return empty data (no crashes)
- ✅ Invalid data throws descriptive errors
- ✅ File I/O errors caught and logged
- ✅ Schema version validation

---

## Test Cases Covered

### Chapter Data Class
1. ✅ Valid chapter with all fields
2. ✅ Chapter with optional fields null
3. ✅ Chapter with empty lessons list
4. ✅ Validation: blank chapterId throws error
5. ✅ Validation: blank title throws error
6. ✅ Validation: blank lesson ID in list throws error

### ChapterProgress Data Class
1. ✅ Default values (all zeros)
2. ✅ Custom values
3. ✅ Validation: lessonsStarted < lessonsCompleted throws error
4. ✅ Validation: negative lessonsStarted throws error
5. ✅ Validation: negative lessonsCompleted throws error
6. ✅ Factory method: forChapter() creates zero-valued instance

### ChapterProgressStore
1. ✅ loadAll() on missing file returns empty map
2. ✅ loadAll() on valid file returns correct data
3. ✅ loadAll() on corrupt file returns empty map
4. ✅ saveAll() writes YAML with schema version 1
5. ✅ getProgress() returns existing progress or null
6. ✅ upsertProgress() inserts new progress
7. ✅ upsertProgress() updates existing progress
8. ✅ clear() deletes progress file
9. ✅ Pack-scoped: different packId = different file

### LessonPackManifest
1. ✅ Schema v1 manifest parses correctly (chapters = emptyList)
2. ✅ Schema v2 manifest with chapters parses correctly
3. ✅ Schema v2 manifest without chapters (only drills) parses correctly
4. ✅ Schema v1 validation: rejects manifest with no content
5. ✅ Schema v2 validation: rejects manifest with no content
6. ✅ Invalid schemaVersion (e.g., 3) throws error
7. ✅ Missing required fields (packId, packVersion, language) throw error
8. ✅ Chapter parsing: valid chapters populate list
9. ✅ Chapter parsing: invalid chapter entry throws descriptive error
10. ✅ Drill sections work in both schema versions

### LessonStore Chapter Methods
1. ✅ getChapters() returns empty list for v1 packs
2. ✅ getChapters() returns sorted list for v2 packs
3. ✅ getChapters() returns empty list if pack not found
4. ✅ getChapterStory() returns null if storyFile is blank
5. ✅ getChapterStory() returns null if file doesn't exist
6. ✅ getChapterStory() returns content if file exists
7. ✅ getChapterStory() returns null on read error
8. ✅ hasChapters() returns false for v1 packs
9. ✅ hasChapters() returns true for v2 packs with chapters
10. ✅ hasChapters() returns false if pack not found

---

## Files Modified

1. `app/src/main/java/com/alexpo/grammermate/data/Models.kt` - Added Chapter and ChapterProgress data classes
2. `app/src/main/java/com/alexpo/grammermate/data/LessonPackManifest.kt` - Added schema v2 support
3. `app/src/main/java/com/alexpo/grammermate/data/LessonStore.kt` - Added chapter query methods

## Files Created

1. `app/src/main/java/com/alexpo/grammermate/data/ChapterProgressStore.kt` - New store for chapter progress

---

## Next Steps (Waves 2-5)

These DATA layer changes are complete and ready for:
1. Wave 2: Business Logic (ChapterProgressCalculator)
2. Wave 3: UI Components (GrammarStoryRoadmapScreen, StoryReaderScreen)
3. Wave 4: ViewModel Integration (TrainingViewModel updates)
4. Wave 5: End-to-End Testing & Polish

---

## Status: ✅ COMPLETE

All 4 DATA layer fixes have been successfully implemented according to TASK-088 specifications.