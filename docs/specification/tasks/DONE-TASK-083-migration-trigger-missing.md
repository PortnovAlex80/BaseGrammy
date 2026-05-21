# TASK-083: Migration Trigger Missing — App Update Breaks State Isolation

**Status:** DONE
**Created:** 2026-05-21
**Branch:** feature/migration-trigger-missing (from main)
**Priority:** CRITICAL
**Complexity:** MODERATE
**UC:** UC-21, UC-24
**Scenario:** scenario-06-daily-practice.md

---

## Problem Statement

### Bug: Migration logic exists but never runs on app update

TASK-080 implemented daily cursor migration from global to pack-scoped storage, but the **migration trigger mechanism** was never implemented. Users who UPDATE the app (not reinstall) experience cross-pack contamination because migration never runs.

**Current Broken State:**
- Migration method `ProgressStore.migrateGlobalDailyCursorToPackScoped()` exists and is tested
- BUT: No version tracking in `AppConfigStore`
- BUT: No migration trigger in `AppRoot.kt`
- BUT: No version constants defined
- Result: Migration **never runs** on app update

**User Impact:**

**Scenario:** User has app version 79 (pre-TASK-080) with global daily cursor
1. User updates app to version 80 (TASK-080 installed via Google Play Update)
2. App launches - NO migration trigger exists
3. `progress.yaml` still contains global `dailyCursor` field
4. User starts daily practice for EN_WORD_ORDER_A1 pack
5. App loads global cursor from `progress.yaml` (should load from `daily_cursor_en_word_order_a1.yaml`)
6. **BUG:** Cursor contains data from IT_VERB_GROUPS_ALL pack (last used pack before update)
7. EN pack daily practice shows Italian verbs instead of English word order
8. User reports: "verbs come from wrong pack after Update"

**Expected Behavior:**
1. User updates app to version 80
2. App launches - detects version change (79 → 80)
3. Migration runs automatically: global cursor → pack-scoped cursor
4. `progress.yaml` global cursor cleared
5. `daily_cursor_{packId}.yaml` files created for active pack
6. User starts daily practice - correct pack data loaded
7. State isolation works correctly

---

## Root Cause Analysis

### Missing Component 1: Version Tracking

**File:** `app/src/main/java/com/alexpo/grammermate/data/AppConfigStore.kt`

**Current state:**
```kotlin
data class AppConfig(
    val languageId: String = "en",
    val themeMode: String = "system",
    val sessionSize: Int = 10,
    // NO appVersion field!
)
```

**Required state:**
```kotlin
data class AppConfig(
    val languageId: String = "en",
    val themeMode: String = "system",
    val sessionSize: Int = 10,
    val appVersion: Int = 0  // ← ADD THIS
)

interface AppConfigStore {
    // ... existing methods ...
    fun getLastVersion(): Int        // ← ADD THIS
    fun setLastVersion(version: Int) // ← ADD THIS
}
```

### Missing Component 2: Migration Trigger

**File:** `app/src/main/java/com/alexpo/grammermate/ui/AppRoot.kt`

**Current state:**
```kotlin
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val container = remember { AppContainer(context) }

    GrammarMateApp(
        // ... app initialization ...
    )
}
```

**Required state:**
```kotlin
// Version constants
private const val VERSION_080_STATE_ISOLATION = 80
private const val VERSION_081_LESSON_PROGRESS_ISOLATION = 81

@Composable
fun AppRoot() {
    val context = LocalContext.current

    // ← ADD MIGRATION TRIGGER
    LaunchedEffect(Unit) {
        checkAndMigrate(context)
    }

    val container = remember { AppContainer(context) }

    GrammarMateApp(
        // ... app initialization ...
    )
}

// ← ADD MIGRATION FUNCTION
private fun checkAndMigrate(context: Context) {
    val configStore = AppConfigStoreImpl(context)
    val progressStore = ProgressStoreImpl(context)
    val lastVersion = configStore.getLastVersion()

    if (lastVersion < VERSION_080_STATE_ISOLATION) {
        // Migrate daily cursor from global to pack-scoped
        val config = configStore.load()
        val packCursorStore = PackDailyCursorStoreImpl(context)
        val migrated = progressStore.migrateGlobalDailyCursorToPackScoped(
            activePackId = config.activePackId?.value,
            packCursorStore = packCursorStore
        )
        if (migrated) {
            Log.i("Migration", "Successfully migrated daily cursor to pack-scoped")
        }
        configStore.setLastVersion(VERSION_080_STATE_ISOLATION)
    }

    // Future migrations: VERSION_081, etc.
}
```

### Missing Component 3: Version Constants

**File:** `app/src/main/java/com/alexpo/grammermate/data/Config.kt` (or new constants file)

**Required:**
```kotlin
object AppVersions {
    const val INITIAL = 0
    const val VERSION_080_STATE_ISOLATION = 80
    const val VERSION_081_LESSON_PROGRESS_ISOLATION = 81
}
```

---

## Acceptance Criteria

### AC1: AppConfigStore tracks app version
**Given:** App is installed or updated
**When:** `AppConfigStore.load()` is called
**Then:** `AppConfig.appVersion` field contains the last version stored (0 for new installs, >0 for updates)

### AC2: Version persists across app launches
**Given:** App version is set to 80 via `setLastVersion(80)`
**And:** App process is killed and restarted
**When:** `getLastVersion()` is called
**Then:** Function returns 80 (version persisted in `config.yaml`)

### AC3: Migration runs on app update (v79 → v80)
**Given:** User has app version 79 with global daily cursor in `progress.yaml`
**And:** Active pack is EN_WORD_ORDER_A1
**When:** User updates to version 80 and launches app
**Then:** `checkAndMigrate()` detects version change
**And:** `migrateGlobalDailyCursorToPackScoped()` is called
**And:** Global cursor migrated to `daily_cursor_en_word_order_a1.yaml`
**And:** Global cursor in `progress.yaml` cleared
**And:** `appVersion` updated to 80 in `config.yaml`

### AC4: Migration does NOT run on clean install
**Given:** User installs app fresh (no existing `config.yaml`)
**When:** App launches for first time
**Then:** `getLastVersion()` returns 0 (initial version)
**And:** Migration is skipped (0 is not < VERSION_080_STATE_ISOLATION)
**And:** No error occurs (migration handles missing data gracefully)

### AC5: Migration is idempotent
**Given:** User has already migrated (version = 80)
**When:** App launches again
**Then:** `checkAndMigrate()` detects lastVersion == VERSION_080_STATE_ISOLATION
**And:** Migration is NOT re-run
**And:** No duplicate `daily_cursor_*.yaml` files created

### AC6: Migration handles missing active pack gracefully
**Given:** User has global daily cursor data
**And:** No active pack is set (`activePackId` is null)
**When:** Migration runs
**Then:** Migration uses `progress.activePackId` as fallback
**And:** If both are null, migration logs warning and returns false (no crash)

---

## Implementation Plan

### Phase 1: Add Version Tracking to AppConfigStore
**File:** `app/src/main/java/com/alexpo/grammermate/data/AppConfigStore.kt`

**Changes:**
1. Add `appVersion: Int = 0` field to `AppConfig` data class
2. Implement `getLastVersion(): Int` in `AppConfigStoreImpl`
3. Implement `setLastVersion(version: Int)` in `AppConfigStoreImpl`
4. Update `load()` to deserialize `appVersion` from `config.yaml`
5. Update `save()` to serialize `appVersion` to `config.yaml`

**Verification:**
- Unit test: `testGetLastVersion_returnsZeroForNewInstall()`
- Unit test: `testSetLastVersion_persistsAcrossRestarts()`

### Phase 2: Add Version Constants
**File:** Create `app/src/main/java/com/alexpo/grammermate/data/AppVersions.kt`

**Changes:**
1. Create `object AppVersions` with version constants
2. Define `VERSION_080_STATE_ISOLATION = 80`
3. Define `VERSION_081_LESSON_PROGRESS_ISOLATION = 81`
4. Add Javadoc explaining version numbering scheme

**Verification:**
- Constants are accessible from `AppRoot.kt`
- Constants follow semantic versioning (80 = TASK-080, 81 = TASK-081)

### Phase 3: Implement Migration Trigger in AppRoot
**File:** `app/src/main/java/com/alexpo/grammermate/ui/AppRoot.kt`

**Changes:**
1. Import required classes: `AppConfigStore`, `ProgressStore`, `PackDailyCursorStoreImpl`
2. Import version constants: `AppVersions`
3. Add `LaunchedEffect(Unit)` block to trigger migration on app launch
4. Implement `checkAndMigrate(context: Context)` function
5. Call `checkAndMigrate()` before `GrammarMateApp()` composition

**Verification:**
- Manual test: Update from v79 → v80, verify migration runs
- Manual test: Clean install, verify no migration runs
- Manual test: Launch after migration, verify no re-migration

### Phase 4: Update Documentation
**Files:**
- `docs/specification/02-data-stores.md` - document version tracking pattern
- `docs/specification/22-use-case-registry.md` - add UC-21 AC14 for migration trigger
- `docs/specification/scenario-06-daily-practice.md` - document update scenario

---

## Verification Checklist

Before marking task complete, verify:

- [ ] `AppConfig.appVersion` field added with default value 0
- [ ] `AppConfigStore.getLastVersion()` implemented and tested
- [ ] `AppConfigStore.setLastVersion()` implemented and tested
- [ ] Version persists in `config.yaml` (check file content after `setLastVersion(80)`)
- [ ] `AppVersions` object created with constants
- [ ] `AppRoot.checkAndMigrate()` function implemented
- [ ] Migration triggered via `LaunchedEffect(Unit)` in `AppRoot`
- [ ] Manual test: Update scenario (v79 → v80) - migration runs
- [ ] Manual test: Clean install - no migration runs
- [ ] Manual test: Re-launch after migration - no re-migration
- [ ] Daily practice works correctly after update (no cross-pack contamination)
- [ ] Existing tests pass (no regression)
- [ ] Integration test: `testMigrationRunsOnAppUpdate`

---

## Scope Boundaries

**DO NOT touch:**
- Migration logic in `ProgressStore.migrateGlobalDailyCursorToPackScoped()` (already correct)
- `PackDailyCursorStore` implementation (already correct)
- Daily practice business logic (unrelated to this task)
- UI components (unrelated to this task)

**Focus ONLY on:**
- Adding version tracking to `AppConfigStore`
- Implementing migration trigger in `AppRoot`
- Adding version constants

---

## Regression Plan

After all fixes are implemented, run:

1. **Build:** `assembleDebug` - must pass with no errors
2. **Tests:** `test` - must pass, no new failures
3. **Per-task verification:** check each item from the Verification Checklist above
4. **Cross-task regression:** verify that TASK-080 migration logic still works (migration is idempotent)
5. **Update scenario test:** simulate app update from v79 to v80, verify daily practice uses correct pack data
6. **Clean install test:** verify new installs work without migration
7. **UC/AC spot-check:** read UC-21 and UC-24 from `22-use-case-registry.md`, confirm ACs hold
8. **Spec sync:** if code diverged from spec intentionally, update spec + CHANGELOG + trace-index

---

## References

**Related Tasks:**
- **TASK-080:** State isolation bug - implemented migration LOGIC but not TRIGGER
- **TASK-081:** Lesson progress isolation - will need same trigger pattern

**Source Files:**
- `app/src/main/java/com/alexpo/grammermate/data/AppConfigStore.kt` - add version tracking
- `app/src/main/java/com/alexpo/grammermate/ui/AppRoot.kt` - add migration trigger
- `app/src/main/java/com/alexpo/grammermate/data/ProgressStore.kt` - migration logic (already correct)

**Documentation:**
- `docs/specification/02-data-stores.md` - AppConfigStore pattern
- `docs/specification/22-use-case-registry.md` - UC-21 (Start daily practice), UC-24 (Cursor tracking)
- `docs/specification/scenario-06-daily-practice.md` - Daily practice flows

**Pattern Reference:**
- `VerbDrillStore` migration in `init()` block shows correct pattern for scoped migrations

---

## Complexity Assessment

**MODERATE** - 3 files modified, new pattern established for future migrations.

**Change Scope:**
- Add `appVersion` field to `AppConfig` (1 line)
- Implement `getLastVersion()` / `setLastVersion()` in `AppConfigStore` (~10 lines)
- Create `AppVersions` object (~5 lines)
- Add `checkAndMigrate()` function in `AppRoot` (~30 lines)
- Update documentation (UC-21, scenarios)

**Risk Level:** MEDIUM
- Changes app initialization flow (migration runs on every launch)
- Migration is idempotent (safe to re-run)
- No data loss risk (migration copies data, then clears source)
- Requires thorough testing of update scenario

**Why not SIMPLE:**
- Establishes new pattern for all future migrations
- Requires understanding of app initialization flow
- Needs manual testing of update scenario (hard to automate)

---

## Notes

**Why this wasn't caught in TASK-080:**
- TASK-080 focused on migration LOGIC (data transformation)
- Migration TRIGGER (when to run) was considered "deployment concern"
- Assumption: "Migration will be called manually during testing" - wrong assumption
- No integration test for full update scenario

**Prevention for future:**
- ALL migration tasks must include trigger mechanism in scope
- Add acceptance criterion: "Migration runs automatically on app update"
- Add integration test: `testFullUpdateScenario_v79_to_v80`
- Document migration pattern in `02-data-stores.md`

**Future migrations:**
- TASK-081 (lesson progress isolation) will use same pattern
- Add version constant `VERSION_081_LESSON_PROGRESS_ISOLATION = 81`
- Reuse `checkAndMigrate()` function - add new `if` block for lesson progress migration

---

## Completion Log
| Date | Fix | Status | Notes |
|------|-----|--------|-------|
| 2026-05-21 | Phase 1: Add version tracking to AppConfigStore | DONE | Added appVersion field to AppConfig, implemented getLastVersion() and setLastVersion() methods in AppConfigStoreImpl. |
| 2026-05-21 | Phase 2: Add version constants | DONE | Created AppVersions.kt with VERSION_080_STATE_ISOLATION = 80 and VERSION_081_LESSON_PROGRESS_ISOLATION = 81 constants. |
| 2026-05-21 | Phase 3: Implement migration trigger in AppRoot | DONE | Added checkAndMigrate() function with proper error handling and LaunchedEffect(Unit) trigger. Commit 5733443. |
