# TASK-080: State Isolation Bug - Pack-Scoped Daily Cursor

**Status:** NEW
**Created:** 2026-05-21
**Branch:** feature/state-isolation-bug (from main)
**Priority:** HIGH
**Complexity:** MODERATE
**UC:** UC-21, UC-24
**Scenario:** scenario-06-daily-practice.md

---

## Problem Statement

### Bug: Daily cursor state shared across ALL packs

Daily practice "Continue" reads **global** cursor state from `progress.yaml`, causing cross-pack contamination. When a user switches between lesson packs (e.g., English → Italian), the daily cursor position from the previous pack is incorrectly used for the new pack.

**Root Cause:**
- `DailyCursorState` is stored **globally** in `progress.yaml` (single instance)
- Should be: `daily_cursor_{packId}.yaml` per pack (like verb drill)
- Same issue affects regular lesson progress state

**Current State Architecture (BROKEN):**
```
progress.yaml
├── TrainingProgress
│   ├── dailyCursor: DailyCursorState  ← GLOBAL - shared across all packs
│   ├── selectedLessonId               ← GLOBAL - shared across all packs
│   └── activeSubLessonIndex           ← GLOBAL - shared across all packs
```

**Desired State Architecture (CORRECT):**
```
progress.yaml
├── TrainingProgress
│   ├── activePackId: String?          ← Global pack selector only
│   └── (no dailyCursor, no lesson progress)

daily_cursor_{packId}.yaml  ← Per-pack cursor state
├── sentenceOffset: Int
├── currentLessonIndex: Int
├── lastSessionHash: Int
├── firstSessionDate: String
├── firstSessionSentenceCardIds: List<String>
└── firstSessionVerbCardIds: List<String>
```

---

## Research Findings

### Verb Drill: Already Pack-Scoped by Language

**Good Reference:** `VerbDrillStore` already implements pack-scoped state correctly:

```kotlin
// VerbDrillStoreImpl.kt (lines 53-62)
class VerbDrillStoreImpl(
    context: Context,
    private val packId: String? = null
) : VerbDrillStore {
    private val languageId: String = extractLanguageId()

    private val file: File = File(baseDir, "verb_drill_progress_${languageId}.yaml")
    private val lastSessionFile: File = File(baseDir, "verb_drill_last_session_${languageId}.yaml")

    private fun extractLanguageId(): String {
        return packId?.let {
            val parts = it.split("-")
            if (parts.size >= 2) parts[1] else "en"
        } ?: "en"
    }
}
```

**Key Pattern:**
- Files scoped by `languageId` (extracted from `packId`)
- Migration logic: `migrateLegacyFiles()` moves `verb_drill_progress.yaml` → `verb_drill_progress_{languageId}.yaml`
- Each `VerbDrillStore` instance is pack-scoped

### Daily Practice: Global State (BROKEN)

**Current Implementation (ProgressStore.kt lines 67-78):**
```kotlin
dailyCursor = run {
    val cursorPayload = payload["dailyCursor"] as? Map<*, *>
    DailyCursorState(
        sentenceOffset = (cursorPayload?.get("sentenceOffset") as? Number)?.toInt() ?: 0,
        currentLessonIndex = (cursorPayload?.get("currentLessonIndex") as? Number)?.toInt() ?: 0,
        // ... all stored in SINGLE progress.yaml
    )
}
```

**Problem:** No `packId` parameter in `ProgressStore` → single global cursor.

### User Impact

**Scenario:** User has 2 packs installed:
1. **EN_WORD_ORDER_A1** - Daily practice at Lesson 5, card 23
2. **IT_VERB_GROUPS_ALL** - Daily practice at Lesson 2, card 7

**Broken Flow:**
1. User completes daily practice in EN pack → cursor advances to (5, 33)
2. User switches to IT pack via HomeScreen package selector
3. User taps Daily Practice tile
4. **BUG:** Session starts from IT Lesson 5, card 33 (WRONG - uses EN cursor)
5. User practices IT pack → cursor advances to (5, 43)
6. User switches back to EN pack
7. **BUG:** Session starts from EN Lesson 5, card 43 (WRONG - cursor corrupted)

**Expected Flow:**
1. EN pack cursor: (5, 33), IT pack cursor: (2, 7) - stored separately
2. Switch to IT pack → daily session uses IT cursor (2, 7)
3. Switch to EN pack → daily session uses EN cursor (5, 33)

---

## Solution Architecture

### Phase 1: Create Pack-Scoped Data Classes

**1.1 Create `PackDailyCursorState` data class**

```kotlin
// data/Models.kt
data class PackDailyCursorState(
    val packId: PackId,
    val sentenceOffset: Int = 0,
    val currentLessonIndex: Int = 0,
    val lastSessionHash: Int = 0,
    val firstSessionDate: String = "",
    val firstSessionSentenceCardIds: List<String> = emptyList(),
    val firstSessionVerbCardIds: List<String> = emptyList()
)
```

**Rationale:** Wrapper that includes `packId` for validation and prevents cross-pack loading.

### Phase 2: Create Pack-Scoped Store

**2.1 Create `PackDailyCursorStore` interface**

```kotlin
// data/PackDailyCursorStore.kt
interface PackDailyCursorStore {
    fun getCursor(packId: PackId): PackDailyCursorState
    fun saveCursor(cursor: PackDailyCursorState)
    fun migrateGlobalToPackScoped(globalCursor: DailyCursorState, packId: PackId)
    fun deleteCursor(packId: PackId)
}
```

**2.2 Implement `PackDailyCursorStoreImpl`**

```kotlin
// data/PackDailyCursorStore.kt
class PackDailyCursorStoreImpl(
    context: Context,
    private val packId: PackId
) : PackDailyCursorStore {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "daily_cursor_${packId.value}.yaml")
    private val mutex = ReentrantLock()

    override fun getCursor(packId: PackId): PackDailyCursorState {
        mutex.lock()
        return try {
            if (!file.exists()) return PackDailyCursorState(packId = packId)
            val data = yaml.load<File>(file.inputStream())
            // ... parse YAML
        } finally {
            mutex.unlock()
        }
    }

    override fun saveCursor(cursor: PackDailyCursorState) {
        mutex.lock()
        try {
            AtomicFileWriter.writeText(file, yaml.dump(cursor))
        } finally {
            mutex.unlock()
        }
    }
}
```

**Pattern:** Copy `VerbDrillStoreImpl` structure (mutex, AtomicFileWriter, YAML parsing).

### Phase 3: Migrate Existing Global State

**3.1 Add migration to `ProgressStore`**

```kotlin
// data/ProgressStore.kt
fun migrateGlobalDailyCursorToPackScoped() {
    val progress = loadProgress()
    val globalCursor = progress.dailyCursor

    // Only migrate if global cursor has non-default values
    if (globalCursor.sentenceOffset == 0 &&
        globalCursor.currentLessonIndex == 0 &&
        globalCursor.firstSessionDate.isEmpty()) {
        return  // Nothing to migrate
    }

    // Use activePackId if available, otherwise use first installed pack
    val targetPackId = progress.activePackId ?: lessonStore.getFirstInstalledPackId()
    if (targetPackId == null) {
        Log.w("ProgressStore", "No pack available for cursor migration")
        return
    }

    val packStore = PackDailyCursorStoreImpl(context, targetPackId)
    packStore.migrateGlobalToPackScoped(globalCursor, targetPackId)

    // Clear global cursor after successful migration
    saveProgress(progress.copy(dailyCursor = DailyCursorState()))
}
```

**3.2 Call migration on app upgrade**

```kotlin
// AppRoot.kt
fun checkAndMigrate() {
    val lastVersion = AppConfigStore.getLastVersion()
    if (lastVersion < VERSION_080_STATE_ISOLATION) {
        ProgressStore(context).migrateGlobalDailyCursorToPackScoped()
        AppConfigStore.setLastVersion(VERSION_080_STATE_ISOLATION)
    }
}
```

### Phase 4: Update DailyPracticeCoordinator

**4.1 Add pack-scoped cursor loading**

```kotlin
// feature/daily/DailyPracticeCoordinator.kt
class DailyPracticeCoordinator(
    private val packDailyCursorStore: PackDailyCursorStore,
    private val activePackId: PackId
) {
    fun getCursor(): DailyCursorState {
        val packCursor = packDailyCursorStore.getCursor(activePackId)
        return DailyCursorState(
            sentenceOffset = packCursor.sentenceOffset,
            currentLessonIndex = packCursor.currentLessonIndex,
            // ... map fields
        )
    }

    fun advanceCursor(sentenceCount: Int) {
        val current = getCursor()
        val newOffset = current.sentenceOffset + sentenceCount
        // ... calculate next lesson index

        packDailyCursorStore.saveCursor(
            PackDailyCursorState(
                packId = activePackId,
                sentenceOffset = newOffset,
                currentLessonIndex = newLessonIndex,
                // ...
            )
        )
    }
}
```

**4.2 Update session invalidation check**

```kotlin
// feature/daily/DailySessionComposer.kt
fun buildSession(packId: PackId): DailySession {
    val currentSession = state.dailySession

    // Check if session's packId matches active pack
    if (currentSession.packId != packId) {
        Log.i("DailySessionComposer", "Pack mismatch: ${currentSession.packId} != $packId")
        throw SessionInvalidatedException("Pack switched, rebuild required")
    }

    // ... continue building session
}
```

**Rationale:** `DailySessionState` already has `packId` field (from TASK-079). Use it for validation.

### Phase 5: Update ProgressStore

**5.1 Remove daily cursor from TrainingProgress**

```kotlin
// data/Models.kt - TrainingProgress
data class TrainingProgress(
    // ... other fields
    // REMOVED: val dailyCursor: DailyCursorState = DailyCursorState()
    // Keep: activePackId for global pack selector
)
```

**5.2 Update ProgressStore serialization**

```kotlin
// data/ProgressStore.kt
fun loadProgress(): TrainingProgress {
    // REMOVED: dailyCursor parsing from YAML
}

fun saveProgress(progress: TrainingProgress) {
    // REMOVED: dailyCursor serialization to YAML
}
```

**Rationale:** `DailyCursorState` is now pack-scoped, not part of global training progress.

---

## Changes

### File List

1. **app/src/main/java/com/alexpo/grammermate/data/Models.kt**
   - Add `PackDailyCursorState` data class
   - Remove `dailyCursor` from `TrainingProgress`

2. **app/src/main/java/com/alexpo/grammermate/data/PackDailyCursorStore.kt** (NEW)
   - Create `PackDailyCursorStore` interface
   - Create `PackDailyCursorStoreImpl` class
   - Implement `migrateGlobalToPackScoped()`

3. **app/src/main/java/com/alexpo/grammermate/data/ProgressStore.kt**
   - Remove `dailyCursor` from `loadProgress()` parsing
   - Remove `dailyCursor` from `saveProgress()` serialization
   - Add `migrateGlobalDailyCursorToPackScoped()` method

4. **app/src/main/java/com/alexpo/grammermate/feature/daily/DailyPracticeCoordinator.kt**
   - Inject `PackDailyCursorStore` (scoped to `activePackId`)
   - Update `getCursor()` to read from pack-scoped store
   - Update `advanceCursor()` to write to pack-scoped store
   - Update `resetState()` to use pack-scoped store

5. **app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt**
   - Update `init` to pass `activePackId` to `DailyPracticeCoordinator`
   - Update `startDailyPractice()` to handle pack-scoped cursor

6. **app/src/main/java/com/alexpo/grammermate/ui/AppRoot.kt**
   - Add migration call in `checkAndMigrate()`
   - Define `VERSION_080_STATE_ISOLATION = 80`

---

## Acceptance Criteria

### UC-21: Start a daily practice session

**NEW ACs:**
- **AC11:** `startDailyPractice()` loads cursor from `daily_cursor_{packId}.yaml` for current `activePackId`
- **AC12:** Switching active pack while daily session is active triggers `SessionInvalidatedException` and rebuilds session with new pack's cursor
- **AC13:** Daily session content uses cursor state from the active pack only (no cross-pack contamination)

### UC-24: Daily practice cursor tracking

**NEW ACs:**
- **AC8:** Cursor state is persisted to `daily_cursor_{packId}.yaml` per pack (not global `progress.yaml`)
- **AC9:** Switching between packs preserves each pack's cursor independently
- **AC10:** On app upgrade, existing global cursor is migrated to `daily_cursor_{activePackId}.yaml`

### Migration Acceptance Criteria

- **AC-MIG1:** If global cursor has non-default values, migration copies it to active pack's file
- **AC-MIG2:** After migration, global cursor in `progress.yaml` is cleared to defaults
- **AC-MIG3:** Migration runs only once (version check)
- **AC-MIG4:** If no active pack exists, migration logs warning and skips (no crash)

---

## Verification Checklist

### Functional Verification

1. [ ] **Pack isolation:**
   - [ ] Start daily practice in EN pack → complete session → verify cursor advanced
   - [ ] Switch to IT pack → start daily practice → verify cursor at IT pack position (not EN)
   - [ ] Switch back to EN pack → verify cursor preserved at EN pack position

2. [ ] **File storage:**
   - [ ] After EN pack session, check `daily_cursor_{en-pack-id}.yaml` exists
   - [ ] After IT pack session, check `daily_cursor_{it-pack-id}.yaml` exists
   - [ ] Verify `progress.yaml` no longer contains `dailyCursor` field

3. [ ] **Session invalidation:**
   - [ ] Start daily session in EN pack → switch active pack mid-session → verify session rebuilt
   - [ ] Verify `SessionInvalidatedException` is thrown when pack IDs don't match

4. [ ] **Migration:**
   - [ ] Install app version before TASK-080 → complete daily practice → upgrade to TASK-080
   - [ ] Verify global cursor migrated to `daily_cursor_{packId}.yaml`
   - [ ] Verify `progress.yaml` no longer has `dailyCursor` field
   - [ ] Verify migration doesn't run on subsequent app launches

5. [ ] **App restart:**
   - [ ] Complete daily practice in EN pack → kill app → restart → verify cursor restored
   - [ ] Switch to IT pack → kill app → restart → verify IT cursor restored (not EN)

### Regression Verification

6. [ ] **Regular training:**
   - [ ] Start regular lesson training → verify no daily cursor corruption
   - [ ] Complete sub-lesson → verify lesson progress works (unaffected)

7. [ ] **Verb drill:**
   - [ ] Start verb drill session → verify verb drill progress still pack-scoped
   - [ ] Verify no cross-contamination between verb drill and daily cursor

8. [ ] **Vocab drill:**
   - [ ] Start vocab drill session → verify vocab mastery still pack-scoped

9. [ ] **Home screen:**
   - [ ] Package selector shows correct active pack
   - [ ] Daily Practice tile navigates to correct pack's session

10. [ ] **Settings:**
    - [ ] Reset progress for current language → verify daily cursor cleared for that pack
    - [ ] Verify other packs' cursors are NOT affected

---

## Migration Strategy

### Step 1: Data Migration (One-Time)

**Trigger:** App upgrade to version 80

**Logic:**
1. Check if `progress.yaml` contains non-default `dailyCursor`
2. If yes:
   - Read `activePackId` from `progress.yaml`
   - If `activePackId` is null, find first installed pack
   - Create `daily_cursor_{packId}.yaml` with migrated data
   - Clear `dailyCursor` in `progress.yaml` to defaults
3. If no: Skip migration

**Edge Cases:**
- No packs installed: Skip migration, log warning
- Multiple packs installed: Migrate to active pack only (user must switch to other packs to initialize their cursors)

### Step 2: Code Migration (Backward Compatible)

**Phase 1:** Add new pack-scoped store alongside old global store
- Keep `DailyCursorState` in `TrainingProgress` temporarily
- `PackDailyCursorStore` reads/writes per-pack files

**Phase 2:** Switch consumers to pack-scoped store
- `DailyPracticeCoordinator` uses `PackDailyCursorStore`
- `ProgressStore` still loads/saves global cursor (for migration)

**Phase 3:** Remove global cursor
- Remove `dailyCursor` from `TrainingProgress`
- `ProgressStore` no longer loads/saves cursor
- Migration runs once, then removed

**Rollback Plan:** If migration fails, app continues using global cursor (old behavior). User can retry migration by upgrading to next version.

---

## Scope Boundaries

**DO NOT touch:**
- VOCAB block SRS logic (already pack-scoped via `WordMasteryStore`)
- Verb drill progress (already pack-scoped via `VerbDrillStore`)
- Regular lesson mastery (already pack-scoped via `MasteryStore`)
- UI components (HomeScreen package selector, DailyPracticeScreen)

**DO touch:**
- Daily cursor state (global → pack-scoped)
- `ProgressStore` (remove daily cursor, add migration)
- `DailyPracticeCoordinator` (use pack-scoped store)

---

## Regression Plan

### Phase 1: Pre-Implementation

1. **Baseline tests:**
   - Run `test` - ensure all tests pass
   - Build APK - ensure clean build

### Phase 2: Post-Implementation

2. **Build verification:**
   - `assembleDebug` - must pass with no errors
   - `test` - must pass, no new failures

3. **Per-pack verification:**
   - Install EN pack only → complete daily practice → verify cursor
   - Install IT pack only → complete daily practice → verify cursor
   - Install BOTH packs → switch between them → verify isolation

4. **Migration verification:**
   - Install old app version → complete daily practice → upgrade
   - Verify cursor migrated correctly
   - Verify no data loss

5. **Cross-feature regression:**
   - Regular lesson training (start, answer, complete)
   - Verb drill standalone
   - Vocab drill standalone
   - Boss battle
   - Home screen flowers

6. **UC/AC spot-check:**
   - UC-21 AC11-AC13 (new)
   - UC-24 AC8-AC10 (new)
   - UC-21 AC1-AC10 (existing - verify no regression)
   - UC-24 AC1-AC7 (existing - verify no regression)

---

## Git

**Branch:** `feature/state-isolation-bug` (from `main`)

**Commits:**
1. Add `PackDailyCursorStore` interface and implementation
2. Add migration logic to `ProgressStore`
3. Update `DailyPracticeCoordinator` to use pack-scoped store
4. Remove global cursor from `TrainingProgress`
5. Update `AppRoot` to call migration on upgrade

**Commit footer:**
```
Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

---

## Implementation Notes

### Pattern Reference: VerbDrillStore

This task follows the established pattern from `VerbDrillStore`:
- Files scoped by `packId` (or extracted `languageId`)
- Migration from global to per-pack files
- One store instance per pack (not singleton)

### Key Difference from Verb Drill

Verb drill uses `languageId` for scoping (extracted from `packId`).
Daily practice should use `packId` directly for scoping (more precise).

Example:
- Verb drill: `verb_drill_progress_en.yaml` (all EN packs share progress)
- Daily cursor: `daily_cursor_ru-en-v1.yaml` (per-pack cursor)

**Rationale:** Daily practice progress is pack-specific (lesson structure differs per pack).

---

## Related Tasks

- **TASK-079:** Daily Practice Pack Switching (added `packId` to `DailySessionState`)
- **TASK-070:** Daily Cursor Stuck (fixed cursor advancement logic)

---

## Open Questions

1. **Q:** Should daily cursor be scoped by `packId` or `languageId`?
   **A:** `packId` - lesson structure differs per pack, even within same language.

2. **Q:** What happens if user switches packs mid-session?
   **A:** `SessionInvalidatedException` is thrown, session rebuilt with new pack's cursor (existing behavior from TASK-079).

3. **Q:** Should migration copy cursor to ALL installed packs?
   **A:** No - only to active pack. Other packs initialize to default cursor on first use.

---

## Success Metrics

- **Primary:** Pack switching preserves daily cursor state per pack
- **Secondary:** Migration completes without data loss for existing users
- **Tertiary:** Zero regression in regular training, verb drill, vocab drill

---

## Completion Log

| Date | Phase | Status | Notes |
|------|-------|--------|-------|
| 2026-05-21 | Task creation | Done | Research complete, spec ready |
| | Phase 1: Data classes | | Pending |
| | Phase 2: Pack-scoped store | | Pending |
| | Phase 3: Migration | | Pending |
| | Phase 4: Coordinator update | | Pending |
| | Phase 5: ProgressStore cleanup | | Pending |
