# TASK-068: PERF-006 Batch Write I/O

**Status:** BACKLOG — Task created for tracking. Not scheduled for current sprint.
**Created:** 2026-05-17
**Spec:** 20-non-functional-requirements.md §20.1.3 (file I/O performance)
**UC:** UC-06 (training flow), UC-09 (daily practice), UC-69 (drill)

---

## Problem

MasteryStore performs full YAML serialization + AtomicFileWriter (fsync) on every single card show. VerbDrillStore does the same per combo. 10 cards = 10 fsyncs. 30 daily cards = 30 fsyncs. Each fsync blocks the calling thread for 5-50ms.

## Requirements

| ID | Description |
|----|-------------|
| NFR §20.1.3 | Reduce file I/O frequency by batching writes |

## Acceptance Criteria

1. In-memory cache updated immediately on every mutation (no behavioral change from caller perspective)
2. Disk write debounced via coroutine (2-5 second window)
3. Explicit flush on session end (user exits training/drill screen)
4. Explicit flush on screen transition
5. Explicit flush on app backgrounding (via Lifecycle observer or `onCleared()`)
6. No data loss if app crashes between debounced writes (acceptable: last 2-5 seconds of progress)
7. Build passes

## Affected Files

- `data/MasteryStore.kt`
- `data/VerbDrillStore.kt`

## Out of Scope

- YamlListStore caching (TASK-067)
- AtomicFileWriter changes (keep the temp-fsync-rename pattern)
- Timer isolation (TASK-064)
- ViewModel init changes (TASK-069)
- WordMasteryStore (uses different write pattern, separate task if needed)

## Regression Checklist

- [ ] Training session: progress saves correctly on session end
- [ ] Daily practice: all 30 card shows recorded
- [ ] Verb drill: progress saves after session
- [ ] App crash during session: at most 5 seconds of progress lost
- [ ] Background/foreground: progress flushed on background
- [ ] No duplicate entries in mastery/progress files
- [ ] `assembleDebug` passes

---

## Completion Log

| Date | Step | Status | Notes |
|------|------|--------|-------|
| | | | Not started — backlog |
