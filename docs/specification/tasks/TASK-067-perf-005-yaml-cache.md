# TASK-067: PERF-005 YamlListStore Caching

**Status:** BACKLOG — Task created for tracking. Not scheduled for current sprint.
**Created:** 2026-05-17
**Spec:** 20-non-functional-requirements.md §20.1.3 (I/O performance), §20.1.7 (threading)
**UC:** All UCs that read YAML data

---

## Problem

YamlListStore reads from disk on every call — no caching. Used by 6+ stores. During ViewModel init, `languagesStore.read()` is called 2-3 times, `packsStore.read()` 2-3 times, each hitting the filesystem.

## Requirements

| ID | Description |
|----|-------------|
| NFR §20.1.3 | Reduce file I/O by caching frequently-read YAML data in memory |
| NFR §20.1.7 | Ensure thread safety of cache access |

## Acceptance Criteria

1. In-memory cache with `cacheLoaded` flag added to YamlListStore
2. `read()` returns cached data if `cacheLoaded == true`, otherwise reads from disk and caches
3. Cache invalidated on `write()` (set `cacheLoaded = false`)
4. Pattern matches MasteryStore's existing cache implementation
5. Thread-safe (mutex-protected or using atomic flag)
6. All stores using YamlListStore function identically
7. Build passes

## Affected Files

- `data/YamlListStore.kt`

## Out of Scope

- MasteryStore (already has caching)
- VerbDrillStore caching
- Batch write I/O (TASK-068)
- ViewModel init changes (TASK-069)
- Timer isolation (TASK-064)

## Regression Checklist

- [ ] All data stores that extend YamlListStore function correctly
- [ ] Lesson packs import and load correctly
- [ ] Language switching works
- [ ] Progress data reads correctly after writes
- [ ] No stale data after write operations (cache invalidated)
- [ ] Thread safety: concurrent read/write does not corrupt data
- [ ] `assembleDebug` passes

---

## Completion Log

| Date | Step | Status | Notes |
|------|------|--------|-------|
| | | | Not started — backlog |
