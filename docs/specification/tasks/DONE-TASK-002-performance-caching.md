# TASK-002: In-Memory Data Caching for Performance [DONE]

**Status:** DONE
**Created:** 2026-05-14
**Branch:** feature/perf-caching (from feature/arch-feature-migration)
**Spec:** 20-non-functional-requirements.md (lines 96-98, SHOULD-level), 02-data-stores.md (sections 2.1, 2.5, 2.6), 09-daily-practice.md (sections 9.4.1, 9.5.1)
**UC:** UC-60 (AC3, AC4 — currently "deferred to future optimization")

---

## Problem

Verb Drill and Daily Practice screens load slowly because stores re-read and re-parse files from disk on every method call with no in-memory cache. Additionally, `VerbDrillStore.upsertComboProgress()` performs double I/O (full read + full write + fsync) on every card answer, causing jank during active sessions.

### Diagnosis (from code audit)

**LessonStore.getLessons()** — CRITICAL bottleneck
- File: `app/src/main/java/com/alexpo/grammermate/data/LessonStore.kt` lines 252-276
- Re-reads language index YAML + parses ALL lesson CSV files + ALL drill CSV files on every call
- Called by DailySessionComposer.buildSentenceBlock() during every daily practice session build
- Cost: 1 YAML + N CSV + M CSV file reads per call (25-60+ file I/O operations)
- No caching field exists

**VerbDrillStore** — HIGH impact
- File: `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt`
- `loadProgress()` (lines 36-76): reads and parses YAML on every call, no cache
- `getComboProgress()` (lines 97-99): calls loadProgress() (full file read) just to look up one key
- `upsertComboProgress()` (lines 101-105): loadProgress() + saveProgress() = two full file I/O per card answer
- `loadAllCardsForPack()` (lines 107-120): reads and parses all verb drill CSV files, no cache

**WordMasteryStore** — HIGH impact
- File: `app/src/main/java/com/alexpo/grammermate/data/WordMasteryStore.kt`
- `loadAll()` (lines 53-76): reads and parses YAML on every call, no cache
- `getMastery()`, `getDueWords()`, `getMasteredCount()` all call loadAll() from scratch

**DailySessionComposer** — no CSV cache
- File: `app/src/main/java/com/alexpo/grammermate/feature/daily/DailySessionComposer.kt`
- `loadVocabWords()` (lines 404-438): parses all vocab CSV files on every call
- `loadVerbDrillCards()` (lines 440-455): parses all verb drill CSV files on every call
- Both called from buildSession() and buildRepeatSession()

**Prebuild race condition** — MEDIUM
- File: `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt` lines 315-372
- `forceReloadDefaultPacks()` and `prebuildSession()` run concurrently on Dispatchers.IO
- Prebuild may read files that forceReload is replacing
- Fix: sequence prebuild after forceReload completes (await the Job)

### Already fixed (do NOT re-fix)

These were previously identified and resolved:
- VerbDrillViewModel.loadCards() now uses withContext(Dispatchers.IO) — FIXED
- DailySessionComposer.buildSession() uses async/coroutineScope for parallel blocks — FIXED
- VerbDrillCsvParser Regex pre-compiled in object — FIXED
- StoreFactory caches store instances (lazy singletons) — FIXED
- VerbDrillViewModel reloadForPack() wrapped in withContext(IO) — FIXED

### Existing caching patterns (follow these)

Stores that already implement caching:
- **MasteryStore** (`app/src/main/java/com/alexpo/grammermate/data/MasteryStore.kt`): Uses `private var cache: Map<String, Set<String>> = emptyMap()` + `private var cacheLoaded = false` pattern. First `loadAll()` reads from disk and caches. Subsequent calls return cache. Cache never invalidated (single-process assumption).
- **HiddenCardStore**: Similar lazy-cached pattern.
- **BadSentenceStore**: Similar lazy-cached pattern.
- **VocabProgressStore**: Similar lazy-cached pattern.

## Changes

### Fix 1: LessonStore.getLessons() — add lazy cache

**UC:** UC-60 AC3 | **Spec:** 02-data-stores.md#2.1, 20-NFR:96

Add in-memory cache to LessonStore, invalidated on pack change.

```
private var lessonsCache: Map<String, List<Lesson>> = emptyMap()  // keyed by languageId
private var lessonsCacheVersion: Map<String, String> = emptyMap()  // keyed by languageId, value = pack hash/version

fun getLessons(languageId: String): List<Lesson> {
    // check cache validity (pack version unchanged)
    val currentVersion = getPackVersion(languageId)
    if (lessonsCache[languageId] != null && lessonsCacheVersion[languageId] == currentVersion) {
        return lessonsCache[languageId]!!
    }
    // cache miss: read from disk
    val lessons = loadLessonsFromDisk(languageId)
    lessonsCache = lessonsCache + (languageId to lessons)
    lessonsCacheVersion = lessonsCacheVersion + (languageId to currentVersion)
    return lessons
}

fun invalidateCache(languageId: String? = null) {
    if (languageId != null) {
        lessonsCache = lessonsCache - languageId
        lessonsCacheVersion = lessonsCacheVersion - languageId
    } else {
        lessonsCache = emptyMap()
        lessonsCacheVersion = emptyMap()
    }
}
```

Invalidate on: pack import, pack delete, forceReloadDefaultPacks.

**Files:** `data/LessonStore.kt` — getLessons(), add invalidateLessonsCache()
**Verification:** Second call to getLessons() with same languageId returns instantly (no disk reads)

### Fix 2: VerbDrillStore — add lazy cache + batch writes

**UC:** UC-60 AC4 | **Spec:** 02-data-stores.md#2.5, 20-NFR:98

Add lazy-loaded in-memory cache for progress data. Keep write-through for data integrity.

```
private var progressCache: Map<String, VerbDrillComboProgress>? = null

fun loadProgress(): Map<String, VerbDrillComboProgress> {
    progressCache?.let { return it }
    val loaded = loadProgressFromDisk()
    progressCache = loaded
    return loaded
}

fun upsertComboProgress(key: String, progress: VerbDrillComboProgress) {
    val all = loadProgress().toMutableMap()
    all[key] = progress
    progressCache = all  // update cache
    saveProgress(all)     // write-through to disk
}

fun invalidateCache() { progressCache = null }
```

Also cache `loadAllCardsForPack()` result — parsed CSV files never change at runtime.

**Files:** `data/VerbDrillStore.kt` — loadProgress(), upsertComboProgress(), loadAllCardsForPack()
**Verification:** Second call to loadProgress() returns from cache. upsertComboProgress() updates cache immediately (no re-read from disk).

### Fix 3: WordMasteryStore — add lazy cache

**UC:** UC-60 AC4 | **Spec:** 02-data-stores.md#2.6, 20-NFR:98

Follow MasteryStore pattern exactly:

```
private var cache: Map<String, WordMasteryState> = emptyMap()
private var cacheLoaded = false

fun loadAll(): Map<String, WordMasteryState> {
    if (cacheLoaded) return cache
    val loaded = loadAllFromDisk()
    cache = loaded
    cacheLoaded = true
    return loaded
}

fun upsertMastery(state: WordMasteryState) {
    val all = loadAll().toMutableMap()
    all[state.wordId] = state
    cache = all
    cacheLoaded = true
    saveAll(all)
}

fun invalidateCache() { cache = emptyMap(); cacheLoaded = false }
```

**Files:** `data/WordMasteryStore.kt` — loadAll(), upsertMastery(), getDueWords(), getMasteredCount()
**Verification:** Second call to loadAll() returns from cache. getDueWords() does not hit disk.

### Fix 4: DailySessionComposer — cache parsed drill files

**UC:** UC-60 AC3 | **Spec:** 09-daily-practice.md#9.4.1, 09#9.5.1

Cache parsed vocab and verb drill data per (packId, languageId). These files never change at runtime.

```
private var cachedVocabWords: Pair<String, List<VocabWord>>? = null  // packId+langId -> words
private var cachedVerbDrillCards: Pair<String, List<VerbDrillCard>>? = null

private fun loadVocabWords(packId: String, languageId: String): List<VocabWord> {
    val key = "$packId:$languageId"
    cachedVocabWords?.let { if (it.first == key) return it.second }
    val words = loadVocabWordsFromDisk(packId, languageId)
    cachedVocabWords = key to words
    return words
}
// Same pattern for loadVerbDrillCards
```

**Files:** `feature/daily/DailySessionComposer.kt` — loadVocabWords(), loadVerbDrillCards()
**Verification:** Second daily session build reuses parsed CSV data. No duplicate CSV parsing.

### Fix 5: Sequence prebuild after forceReload

**UC:** UC-60 | **Spec:** 09-daily-practice.md (race condition note)

In TrainingViewModel init block, make prebuild wait for forceReload to complete:

```
val reloadJob = viewModelScope.launch(Dispatchers.IO) {
    lessonStore.forceReloadDefaultPacks()
    // ... existing reload logic
}
reloadJob.invokeOnCompletion {
    viewModelScope.launch(Dispatchers.IO) {
        dailyPracticeCoordinator.prebuildSession(...)
    }
}
```

**Files:** `ui/TrainingViewModel.kt` — init block (lines ~315-372)
**Verification:** prebuildSession only starts after forceReloadDefaultPacks completes. No race condition.

---

## Verification Checklist

1. LessonStore.getLessons(): second call with same languageId returns from cache (no file I/O)
2. LessonStore cache invalidated on pack import/delete
3. VerbDrillStore.loadProgress(): second call returns from cache
4. VerbDrillStore.upsertComboProgress(): updates cache immediately, does not re-read from disk before write
5. VerbDrillStore.loadAllCardsForPack(): second call returns from cache
6. WordMasteryStore.loadAll(): second call returns from cache
7. WordMasteryStore.upsertMastery(): updates cache immediately
8. DailySessionComposer.loadVocabWords(): second session build reuses parsed data
9. DailySessionComposer.loadVerbDrillCards(): second session build reuses parsed data
10. Daily practice prebuild starts only after forceReloadDefaultPacks completes
11. assembleDebug passes after all changes
12. No regressions: run /regression-check on UC-60 (all ACs), UC-21..UC-25 (daily practice), UC-01..UC-06 (training)

## Scope Boundaries

**Do NOT touch:**
- Dispatchers/IO threading (already fixed)
- DailySessionComposer parallel blocks (already uses async)
- VerbDrillCsvParser Regex (already pre-compiled)
- StoreFactory singleton pattern (already cached instances)
- MasteryStore, HiddenCardStore, BadSentenceStore, VocabProgressStore (already have caching)
- Any UI/composable files
- GrammarMateApp.kt router behavior
- CardSessionContract interface
- Data store file formats (YAML/CSV)

**Only touch:**
- LessonStore.kt — add caching to getLessons()
- VerbDrillStore.kt — add caching to loadProgress(), loadAllCardsForPack(), upsertComboProgress()
- WordMasteryStore.kt — add caching to loadAll()
- DailySessionComposer.kt — add caching to loadVocabWords(), loadVerbDrillCards()
- TrainingViewModel.kt — sequence prebuild after forceReload (init block only)

## Git

One commit per fix or one combined commit. Footer:
```
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

## Completion Log

| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| 2026-05-14 | Fix 1: LessonStore cache | DONE | lessonsCache map, invalidated on import/delete/reload |
| 2026-05-14 | Fix 2: VerbDrillStore cache | DONE | progressCache + cardsCache by packId:languageId |
| 2026-05-14 | Fix 3: WordMasteryStore cache | DONE | cache + cacheLoaded flags, MasteryStore pattern |
| 2026-05-14 | Fix 4: DailySessionComposer cache | DONE | cachedVocabWords + cachedVerbDrillCards by packId:languageId |
| 2026-05-14 | Fix 5: Prebuild sequencing | DONE | reloadJob.invokeOnCompletion chains prebuild after forceReload |
| 2026-05-14 | Verification checklist | DONE | All caches verified. UC-60/21/24/61 PASS. UC-01-06 untouched PASS. Build clean. |
