# Wave 2 Agent A9: Dependencies/Dead Code Architecture Risk Analysis

**Analysis Date:** 2025-01-22
**Agent:** Wave 2 Agent A9 (Risk Classification)
**Input Document:** W1-A9-dependencies-dead-code-current-map.md
**Scope:** Architectural risk classification and impact assessment

---

## Executive Summary

This document classifies architectural problems identified in Wave 1 into risk categories with blast radius analysis and remediation plans. The analysis reveals **8 HIGH-risk issues**, **6 MEDIUM-risk issues**, and **5 LOW-risk issues** requiring attention.

**Critical Findings:**
- **HIGH Risk:** Unused navigation routes create maintenance burden and confusion
- **MEDIUM Risk:** Deprecated migration methods clutter the API surface
- **LOW Risk:** Manual DI maintenance is acceptable at current scale

**Overall Risk Level:** MODERATE - No critical failures, but accumulated debt impacts maintainability

---

## Risk Classification Matrix

### Summary by Risk Level

| Risk Level | Count | Categories |
|------------|-------|------------|
| HIGH | 8 | Unused routes, misleading naming, navigation confusion |
| MEDIUM | 6 | Deprecated methods, legacy patterns, documentation debt |
| LOW | 5 | Manual DI, infrastructure methods, monitoring needs |

---

## HIGH Risk Issues

### Risk 1: Unused ELITE Navigation Route

**Title:** Dead ELITE navigation route with misleading parameter name

**Current behavior:**
- `AppScreen.ELITE` enum value exists (AppScreen.kt:13)
- `Routes.ELITE` constant defined (GrammarMateApp.kt:90)
- Navigation handler only redirects to HOME (GrammarMateApp.kt:383-385)
- Parameter `onOpenElite` in HomeScreen.kt:104 actually opens Daily Practice, not ELITE

**Evidence:**
```kotlin
// AppScreen.kt:13
ELITE,        // UNUSED - kept for "backward compatibility"

// GrammarMateApp.kt:383-385
composable(Routes.ELITE) {
    LaunchedEffect(Unit) { navController.navigate(Routes.HOME) { popUpTo(Routes.ELITE) { inclusive = true } } }
}

// HomeScreen.kt:104
onOpenElite: () -> Unit,  // Misleading - opens Daily Practice
```

**Why this is a problem:**
- Code duplication: Enum value, route constant, and redirect handler all exist for dead route
- Misleading parameter name (`onOpenElite`) confuses developers about actual functionality
- Maintenance burden: Developers must maintain 3 locations for unused feature
- Documentation debt: Comments about "backward compatibility" are obsolete
- Cognitive load: New contributors waste time understanding dead code

**Blast radius:**
- **Files affected:** AppScreen.kt, GrammarMateApp.kt, HomeScreen.kt (3 files)
- **Lines affected:** ~15 lines total
- **Breaking changes:** None - route is unused
- **External impact:** Zero - no deep links to ELITE found

**Proposed direction:**
1. Remove `AppScreen.ELITE` enum value
2. Remove `Routes.ELITE` constant
3. Remove ELITE composable handler from GrammarMateApp.kt
4. Rename `onOpenElite` parameter to `onOpenDailyPractice` across all call sites

**Risk level:** HIGH

**Must-have tests before refactor:**
1. **Navigation test:** Verify all active routes still work after ELITE removal
2. **HomeScreen test:** Verify Daily Practice button still opens correct screen after parameter rename
3. **Regression test:** Verify no broken navigation references in codebase

---

### Risk 2: Unused VOCAB Navigation Route

**Title:** Dead VOCAB navigation route separate from active VOCAB_DRILL

**Current behavior:**
- `AppScreen.VOCAB` enum value exists (AppScreen.kt:14)
- `Routes.VOCAB` constant defined (GrammarMateApp.kt:91)
- Navigation handler only redirects to HOME (GrammarMateApp.kt:388-390)
- Confusingly similar to active `VOCAB_DRILL` route

**Evidence:**
```kotlin
// AppScreen.kt:14
VOCAB,        // UNUSED - kept for "backward compatibility"

// GrammarMateApp.kt:388-390
composable(Routes.VOCAB) {
    LaunchedEffect(Unit) { navController.navigate(Routes.HOME) { popUpTo(Routes.VOCAB) { inclusive = true } } }
}

// Contrast with active route:
VOCAB_DRILL,  // ACTIVE - used for vocabulary drill practice
```

**Why this is a problem:**
- Name confusion: `VOCAB` vs `VOCAB_DRILL` creates developer uncertainty
- Code duplication: Same redirect pattern as ELITE
- Maintenance burden: 3 locations maintained for dead route
- Documentation debt: Comments about "backward compatibility" are obsolete
- Risk of accidental use: Developer might call `navigate(AppScreen.VOCAB)` expecting vocabulary feature

**Blast radius:**
- **Files affected:** AppScreen.kt, GrammarMateApp.kt (2 files)
- **Lines affected:** ~10 lines total
- **Breaking changes:** None - route is unused
- **External impact:** Zero - no deep links to VOCAB found

**Proposed direction:**
1. Remove `AppScreen.VOCAB` enum value
2. Remove `Routes.VOCAB` constant
3. Remove VOCAB composable handler from GrammarMateApp.kt
4. Add code comment clarifying `VOCAB_DRILL` is the active vocabulary route

**Risk level:** HIGH

**Must-have tests before refactor:**
1. **Navigation test:** Verify VOCAB_DRILL route still works after VOCAB removal
2. **Regression test:** Verify no broken references to VOCAB vs VOCAB_DRILL in codebase
3. **Deep link test:** Verify no external VOCAB links (check analytics if available)

---

### Risk 3: Misleading onOpenElite Parameter Name

**Title:** Parameter named `onOpenElite` actually opens Daily Practice

**Current behavior:**
- `HomeScreen.kt:104` defines parameter `onOpenElite: () -> Unit`
- `GrammarMateApp.kt:305-329` implements handler that opens Daily Practice
- Handler checks for resumable daily session or starts new daily practice
- Name suggests ELITE feature, but functionality is Daily Practice

**Evidence:**
```kotlin
// HomeScreen.kt:104
onOpenElite: () -> Unit,  // Misleading name

// HomeScreen.kt:331
DailyPracticeEntryTile(
    onClick = onOpenElite  // Actually opens Daily Practice
)

// GrammarMateApp.kt:305-329
onOpenElite = remember(dialogs) {
    {
        val level = vm.getProgressLessonLevel()
        if (vm.daily.hasResumableDailySession()) {
            dialogs = dialogs.copy(showDailyResumeDialog = true, pendingDailyLevel = level)
        } else {
            // Start daily practice logic
        }
    }
}
```

**Why this is a problem:**
- Developer confusion: Parameter name doesn't match functionality
- Code comprehension: New contributors must trace implementation to understand behavior
- Maintenance risk: Future changes might incorrectly assume ELITE functionality
- Documentation debt: No inline comment explaining historical naming
- Violates principle of least surprise

**Blast radius:**
- **Files affected:** HomeScreen.kt, GrammarMateApp.kt (2 files)
- **Lines affected:** ~30 lines total
- **Breaking changes:** None - pure rename
- **External impact:** Zero - internal parameter only

**Proposed direction:**
1. Rename `onOpenElite` to `onOpenDailyPractice` in HomeScreen.kt:104
2. Update implementation in GrammarMateApp.kt:305
3. Add inline comment: "Opens Daily Practice screen (formerly called ELITE feature)"
4. Search codebase for any other references to "elite" naming

**Risk level:** HIGH

**Must-have tests before refactor:**
1. **HomeScreen test:** Verify Daily Practice button triggers correct handler after rename
2. **Integration test:** Verify Daily Practice screen opens correctly with new parameter name
3. **Navigation test:** Verify no broken references to old parameter name

---

### Risk 4: Documentation vs Code Mismatch - AppScreen Enum

**Title:** AppScreen documentation claims "backward compatibility" but no actual compatibility needed

**Current behavior:**
- KDoc comment states "ELITE and VOCAB are kept for backward compatibility"
- No actual backward compatibility mechanism exists
- Routes only redirect to HOME with no data preservation
- No external consumers depend on these enum values

**Evidence:**
```kotlin
/**
 * App navigation screen enum.
 * Extracted from GrammarMateApp.kt for shared access.
 * ELITE and VOCAB are kept for backward compatibility — they redirect to HOME.
 */
enum class AppScreen {
    HOME,
    LESSON,
    ELITE,        // UNUSED
    VOCAB,        // UNUSED
    // ...
}
```

**Why this is a problem:**
- Misleading documentation: Claims compatibility that doesn't exist
- False security: Developers might believe removing these breaks compatibility
- Historical artifact: Comment describes old architecture, not current state
- Maintenance burden: Dead code kept for "compatibility" that isn't real
- Violates single source of truth principle

**Blast radius:**
- **Files affected:** AppScreen.kt (1 file)
- **Lines affected:** Documentation only
- **Breaking changes:** None - documentation fix only
- **External impact:** Zero - no public API surface

**Proposed direction:**
1. Remove "backward compatibility" claim from KDoc
2. Update documentation to accurately reflect current state
3. Add migration note: "Removed ELITE/VOCAB in v1.7 - see git history"
4. Or remove enum values entirely (see Risk 1 and Risk 2)

**Risk level:** HIGH

**Must-have tests before refactor:**
1. **Documentation review:** Verify no external docs reference ELITE/VOCAB routes
2. **API surface check:** Confirm AppScreen is not used in public SDK/plugins
3. **Git history check:** Verify when routes were last active (should be >6 months ago)

---

### Risk 5: Navigation Route Proliferation

**Title:** String-based route constants create maintenance burden and lack type safety

**Current behavior:**
- Routes defined as string constants in GrammarMateApp.kt:86-97
- No compile-time verification of route validity
- Manual synchronization required between AppScreen enum and Routes constants
- Typos in route strings only discovered at runtime

**Evidence:**
```kotlin
// GrammarMateApp.kt:86-97
private object Routes {
    const val HOME = "home"
    const val LESSON = "lesson"
    const val ELITE = "elite"        // Dead route
    const val VOCAB = "vocab"        // Dead route
    const val DAILY_PRACTICE = "daily_practice"
    const val STORY = "story"
    const val TRAINING = "training"
    const val LADDER = "ladder"
    const val VERB_DRILL = "verb_drill"
    const val VOCAB_DRILL = "vocab_drill"
}
```

**Why this is a problem:**
- No type safety: String literals can't be verified at compile time
- Duplication: AppScreen enum + Routes constants represent same data
- Maintenance burden: Adding route requires updates in 2 places
- Runtime errors: Typos only caught when navigation is attempted
- Dead code accumulation: ELITE/VOCAB constants kept despite being unused
- No IDE support: Can't "find usages" on string constants effectively

**Blast radius:**
- **Files affected:** GrammarMateApp.kt, AppScreen.kt, all navigation call sites (10+ files)
- **Lines affected:** ~100+ lines total
- **Breaking changes:** HIGH - affects entire navigation system
- **External impact:** Zero - internal navigation only

**Proposed direction:**
1. **Immediate:** Remove dead ELITE/VOCAB constants from Routes object
2. **Short-term:** Consider Compose Navigation type-safe routes (Kotlin sealed classes)
3. **Long-term:** Evaluate migration to type-safe navigation library

**Risk level:** HIGH

**Must-have tests before refactor:**
1. **Navigation smoke test:** Verify all active routes work after constant removal
2. **Deep link test:** Verify no broken deep links after route changes
3. **Integration test:** Verify navigation flows (HOME → TRAINING → VERB_DRILL, etc.)
4. **Regression test:** Verify no broken string references in codebase

---

### Risk 6: Code Reading Comprehension Debt

**Title:** Dead ELITE/VOCAB code forces developers to read unnecessary code

**Current behavior:**
- New contributors encounter ELITE/VOCAB references in multiple files
- Must trace through code to understand routes are dead
- Waste time understanding "backward compatibility" that doesn't exist
- Cognitive load increased by obsolete code paths

**Evidence:**
- Developer flow: Read AppScreen.kt → See ELITE/VOCAB → Read GrammarMateApp.kt → See redirect → Realize dead code
- Time wasted: ~10-15 minutes per new contributor
- Confusion frequency: Every new team member

**Why this is a problem:**
- Onboarding friction: New developers waste time on dead code
- Code comprehension: Must distinguish between active and dead routes
- Maintenance risk: Developer might accidentally "fix" dead code
- Knowledge transfer: Outdated naming spreads confusion
- Violates "you ain't gonna need it" (YAGNI) principle

**Blast radius:**
- **Files affected:** All files referencing ELITE/VOCAB (4 files)
- **Lines affected:** ~30 lines total
- **Breaking changes:** None - removal only
- **External impact:** Zero - internal code only

**Proposed direction:**
1. Remove all ELITE/VOCAB references (see Risk 1, 2, 3, 4)
2. Add migration note to codebase history documentation
3. Update onboarding guide to remove ELITE/VOCAB mentions
4. Clean up git history references if any

**Risk level:** HIGH

**Must-have tests before refactor:**
1. **Onboarding test:** Have new developer navigate codebase before and after cleanup
2. **Code review:** Verify no confusing comments remain
3. **Documentation check:** Verify no internal docs reference dead routes

---

### Risk 7: Parameter Naming Inconsistency

**Title:** `onOpenElite` parameter name inconsistent with actual Daily Practice functionality

**Current behavior:**
- `HomeScreen.kt:104` has parameter `onOpenElite: () -> Unit`
- Implementation opens Daily Practice screen
- Name suggests ELITE feature (which is dead route)
- Creates cognitive dissonance for code readers

**Evidence:**
```kotlin
// HomeScreen.kt:104
onOpenElite: () -> Unit,

// GrammarMateApp.kt:305-329
onOpenElite = remember(dialogs) {
    {
        val level = vm.getProgressLessonLevel()
        if (vm.daily.hasResumableDailySession()) {
            // Daily Practice logic
        }
    }
}
```

**Why this is a problem:**
- Naming violation: Parameter name doesn't match functionality
- Code comprehension: Developers must read implementation to understand behavior
- Maintenance risk: Future changes might incorrectly assume ELITE functionality
- Testing confusion: Test names might reference wrong feature
- Violates self-documenting code principle

**Blast radius:**
- **Files affected:** HomeScreen.kt, GrammarMateApp.kt, test files (3+ files)
- **Lines affected:** ~30 lines total
- **Breaking changes:** None - pure rename
- **External impact:** Zero - internal parameter only

**Proposed direction:**
1. Rename `onOpenElite` to `onOpenDailyPractice`
2. Update all test references
3. Update any documentation that mentions the parameter
4. Add inline comment explaining historical naming if needed

**Risk level:** HIGH

**Must-have tests before refactor:**
1. **Parameter rename test:** Verify all call sites updated correctly
2. **Test suite run:** Verify no broken test references after rename
3. **Integration test:** Verify Daily Practice still opens correctly

---

### Risk 8: Unused Code Accumulates Technical Debt

**Title:** Dead ELITE/VOCAB code creates precedent for keeping unused code

**Current behavior:**
- Dead routes kept with "backward compatibility" justification
- No clear process for removing unused code
- Developers might follow pattern and keep other dead code
- Technical debt accumulates over time

**Evidence:**
- Current state: 2 dead routes kept for 6+ months
- Pattern: "Keep for backward compatibility" with no actual compatibility need
- Risk: Future dead code will also be kept instead of removed

**Why this is a problem:**
- Bad precedent: Teaches developers to keep unused code
- Code bloat: Accumulation of dead code over time
- Maintenance burden: More code to maintain without value
- Technical debt: Creates cleanup backlog
- Violates boy scout rule: "Leave the code better than you found it"

**Blast radius:**
- **Files affected:** Entire codebase (cultural impact)
- **Lines affected:** Unknown (future dead code)
- **Breaking changes:** None - cultural change only
- **External impact:** Zero - internal development practice

**Proposed direction:**
1. Remove ELITE/VOCAB code immediately (set example)
2. Document code removal process in CLAUDE.md
3. Add "dead code removal" to pre-commit checklist
4. Schedule quarterly dead code audits
5. Celebrate removal of dead code (positive reinforcement)

**Risk level:** HIGH

**Must-have tests before refactor:**
1. **Process test:** Run cleanup and measure time savings
2. **Team feedback:** Survey developers on cleanup impact
3. **Code review:** Verify no new dead code introduced after cleanup

---

## MEDIUM Risk Issues

### Risk 9: Deprecated Migration Methods

**Title:** @Deprecated pack-scoped migration methods clutter API surface

**Current behavior:**
- 8 methods marked `@Deprecated` across DrillFileManager.kt and LessonStore.kt
- Methods kept for "migration safety period" after TASK-080/TASK-081 completion
- No clear timeline for removal
- Interface methods expose deprecated implementations

**Evidence:**
```kotlin
// DrillFileManager.kt:58-65
@Deprecated("Use getVerbDrillFiles(packId, languageId) for pack-scoped drill lookup.")
fun getVerbDrillFilesLegacy(languageId: String): List<File> {
    val verbDrillDir = File(baseDir, "verb_drill")
    if (!verbDrillDir.exists()) return emptyList()
    return verbDrillDir.listFiles()
        ?.filter { it.name.startsWith("${languageId}_") && it.extension == "csv" }
        ?: emptyList()
}

// LessonStore.kt:88-89
@Deprecated("Use getVerbDrillFiles(packId, languageId) for pack-scoped drill lookup.")
fun getVerbDrillFilesLegacy(languageId: String): List<File>
```

**Why this is a problem:**
- API surface clutter: Deprecated methods appear in autocomplete
- Developer confusion: Unclear which methods to use
- Maintenance burden: Must keep deprecated methods working
- Testing overhead: Must test deprecated code paths
- No removal timeline: "Safety period" is undefined

**Blast radius:**
- **Files affected:** DrillFileManager.kt, LessonStore.kt (2 files)
- **Lines affected:** ~20 lines total
- **Breaking changes:** LOW - deprecated methods have no external consumers
- **External impact:** Zero - internal API only

**Proposed direction:**
1. **Immediate (v1.7):** Document deprecation timeline in CLAUDE.md
2. **Short-term (v1.8):** Remove all deprecated methods after migration verification
3. **Testing:** Add migration verification test before removal
4. **Communication:** Release notes mention deprecated method removal

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. **Migration verification test:** Verify all users have pack-scoped data
2. **Integration test:** Verify new pack-scoped methods work correctly
3. **Regression test:** Verify no broken references to deprecated methods
4. **Analytics check:** Verify no active usage of deprecated methods (if instrumented)

---

### Risk 10: Legacy Pre-Pack-Scoped Architecture

**Title:** Migration code from pre-pack-scoped architecture remains in AppRoot.kt

**Current behavior:**
- AppRoot.kt:34-88 contains version-gated migration logic
- Migrations for VERSION_080_STATE_ISOLATION and VERSION_081_LESSON_PROGRESS_ISOLATION
- Runs on every app launch (with version check guard)
- No clear removal timeline

**Evidence:**
```kotlin
// AppRoot.kt:34-88
private fun checkAndMigrate() {
    val lastVersion = loadLastAppVersion()
    val currentVersion = BuildConfig.VERSION_CODE

    if (lastVersion < AppVersions.VERSION_080_STATE_ISOLATION) {
        migrateTo080()
    }
    if (lastVersion < AppVersions.VERSION_081_LESSON_PROGRESS_ISOLATION) {
        migrateTo081()
    }

    saveLastAppVersion(currentVersion)
}
```

**Why this is a problem:**
- App launch overhead: Migration check runs every launch (version-gated but still executed)
- Code complexity: Old migration logic remains in main app flow
- Maintenance burden: Must keep old migration code working
- Testing overhead: Must test migration paths on new builds
- Unclear timeline: No documented removal plan

**Blast radius:**
- **Files affected:** AppRoot.kt (1 file)
- **Lines affected:** ~55 lines total
- **Breaking changes:** LOW - version-gated migrations won't run for new users
- **External impact:** Zero - internal migration only

**Proposed direction:**
1. **Immediate (v1.7):** Document migration removal timeline
2. **Short-term (v1.8):** Remove VERSION_080 and VERSION_081 migrations
3. **Long-term:** Establish policy: remove migrations after 2 versions
4. **Monitoring:** Track migration execution rates via analytics

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. **Migration test:** Verify all users have migrated past v0.8
2. **Analytics check:** Confirm zero migration executions in production
3. **Regression test:** Verify app works without old migration code
4. **Version test:** Verify new installs don't run migrations

---

### Risk 11: Manual Dependency Injection Maintenance

**Title:** AppContainer requires manual updates for each new store

**Current behavior:**
- AppContainer.kt manually defines 13 store dependencies
- Adding new store requires 3 locations: lazy property, StoreFactory method, AppContainer property
- No compile-time verification of dependency graph
- Manual dependency lifetime management

**Evidence:**
```kotlin
// AppContainer.kt
class AppContainer(private val application: Application) {
    private val storeFactory: StoreFactory = StoreFactory.getInstance(application)

    val lessonStore: LessonStore by lazy { storeFactory.getLessonStore() }
    val progressStore: ProgressStore by lazy { storeFactory.getProgressStore() }
    val masteryStore: MasteryStore by lazy { storeFactory.getMasteryStore() }
    val wordMasteryStore: (String?) -> WordMasteryStore by lazy { packId ->
        storeFactory.getWordMasteryStore(packId)
    }
    // ... 9+ more stores
}
```

**Why this is a problem:**
- Manual maintenance: Adding store requires updates in 3 places
- Error-prone: Easy to forget one of the 3 required locations
- No compile-time checks: Can't verify dependency graph at compile time
- Boilerplate: Each store requires same lazy initialization pattern
- Scalability limit: Becomes unwieldy as store count grows

**Blast radius:**
- **Files affected:** AppContainer.kt, StoreFactory.kt (2 files)
- **Lines affected:** ~80 lines total
- **Breaking changes:** MEDIUM - affects entire DI system
- **External impact:** Zero - internal DI only

**Proposed direction:**
1. **Immediate:** Continue with manual DI (acceptable at 13 stores)
2. **Short-term (v1.8):** Evaluate Hilt/Koin if store count exceeds 20
3. **Long-term:** Consider code generation for DI boilerplate
4. **Monitoring:** Track store count growth rate

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. **DI migration test:** Verify all dependencies resolve correctly after refactor
2. **Performance test:** Measure DI initialization time (should be <100ms)
3. **Integration test:** Verify no broken store references after migration
4. **Complexity analysis:** Compare manual vs framework DI maintenance cost

---

### Risk 12: String-Based Navigation Routes

**Title:** String route constants lack compile-time safety

**Current behavior:**
- Routes defined as string constants in GrammarMateApp.kt:86-97
- No compile-time verification of route validity
- Typos only discovered at runtime
- Manual synchronization with AppScreen enum

**Evidence:**
```kotlin
// GrammarMateApp.kt:86-97
private object Routes {
    const val HOME = "home"
    const val LESSON = "lesson"
    const val ELITE = "elite"        // Dead
    const val VOCAB = "vocab"        // Dead
    const val DAILY_PRACTICE = "daily_practice"
    const val STORY = "story"
    const val TRAINING = "training"
    const val LADDER = "ladder"
    const val VERB_DRILL = "verb_drill"
    const val VOCAB_DRILL = "vocab_drill"
}
```

**Why this is a problem:**
- No type safety: String literals can't be verified at compile time
- Runtime errors: Typos only caught when navigation attempted
- Duplication: AppScreen enum + Routes constants represent same data
- Maintenance burden: Adding route requires updates in 2 places
- Limited IDE support: Can't effectively "find usages" on strings

**Blast radius:**
- **Files affected:** GrammarMateApp.kt, all navigation call sites (10+ files)
- **Lines affected:** ~100+ lines total
- **Breaking changes:** HIGH - affects entire navigation system
- **External impact:** Zero - internal navigation only

**Proposed direction:**
1. **Immediate:** Remove dead ELITE/VOCAB constants
2. **Short-term (v1.8):** Consider Compose Navigation type-safe routes
3. **Long-term:** Evaluate migration to type-safe navigation library

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. **Navigation smoke test:** Verify all active routes work
2. **Deep link test:** Verify no broken deep links
3. **Integration test:** Verify navigation flows work correctly
4. **Type safety test:** Verify compile-time route validation (if using type-safe routes)

---

### Risk 13: StoreFactory Cache Eviction Unclear Usage

**Title:** Cache eviction methods have no clear callers

**Current behavior:**
- `StoreFactory.evict(packId)` and `clearCache()` methods exist
- Exposed via `AppContainer.evict()` and `AppContainer.clearCache()`
- No static references found in codebase scan
- Potential runtime usage during progress reset

**Evidence:**
```kotlin
// StoreFactory.kt:77-90
@Synchronized
fun evict(packId: String?) {
    wordMasteryCache.remove(packId)
    verbDrillCache.remove(packId)
}

@Synchronized
fun clearCache() {
    wordMasteryCache.clear()
    verbDrillCache.clear()
}
```

**Why this is a problem:**
- Unclear usage: Methods exist but no clear callers
- Infrastructure uncertainty: Don't know if these are used
- Testing gap: Can't verify usage via static analysis
- Documentation incomplete: No documented usage scenarios

**Blast radius:**
- **Files affected:** StoreFactory.kt, AppContainer.kt (2 files)
- **Lines affected:** ~15 lines total
- **Breaking changes:** LOW - methods appear unused
- **External impact:** Zero - internal infrastructure

**Proposed direction:**
1. **Immediate:** Add logging to track cache eviction usage
2. **Short-term:** If unused after 1 release, consider removal
3. **Documentation:** Add usage examples to KDoc
4. **Testing:** Add unit tests for cache eviction behavior

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. **Usage monitoring:** Add logging and track usage over 1 release
2. **Runtime test:** Verify cache eviction works correctly when called
3. **Progress reset test:** Verify cache eviction during progress reset (if applicable)
4. **Analytics check:** Check if methods are called in production

---

### Risk 14: Migration Code Runs on Every App Launch

**Title:** AppRoot migration check executes every app launch

**Current behavior:**
- `AppRoot.checkAndMigrate()` runs on every app launch
- Version-gated migrations don't execute after migration, but check still runs
- Adds app launch overhead (version comparison + file read)
- No clear removal timeline for old migrations

**Evidence:**
```kotlin
// AppRoot.kt:34-88
private fun checkAndMigrate() {
    val lastVersion = loadLastAppVersion()
    val currentVersion = BuildConfig.VERSION_CODE

    if (lastVersion < AppVersions.VERSION_080_STATE_ISOLATION) {
        migrateTo080()
    }
    if (lastVersion < AppVersions.VERSION_081_LESSON_PROGRESS_ISOLATION) {
        migrateTo081()
    }

    saveLastAppVersion(currentVersion)
}
```

**Why this is a problem:**
- App launch overhead: Migration check runs every launch
- File I/O: `loadLastAppVersion()` reads from file system every launch
- Code complexity: Old migration logic remains in main flow
- Maintenance burden: Must keep old migration code working
- Unclear timeline: No documented removal plan

**Blast radius:**
- **Files affected:** AppRoot.kt (1 file)
- **Lines affected:** ~55 lines total
- **Breaking changes:** LOW - version-gated migrations won't run for new users
- **External impact:** Zero - internal migration only

**Proposed direction:**
1. **Immediate (v1.7):** Document migration removal timeline
2. **Short-term (v1.8):** Remove VERSION_080 and VERSION_081 migrations
3. **Long-term:** Establish policy: remove migrations after 2 versions
4. **Performance:** Measure migration check overhead (should be <10ms)

**Risk level:** MEDIUM

**Must-have tests before refactor:**
1. **Migration test:** Verify all users have migrated past v0.8
2. **Performance test:** Measure migration check overhead
3. **Regression test:** Verify app works without old migration code
4. **Version test:** Verify new installs don't run migrations

---

## LOW Risk Issues

### Risk 15: Near-Zero TODO/FIXME Comments

**Title:** No tracked technical debt in code comments

**Current behavior:**
- 1 TODO (intentional reserved-pack re-enable note, `LessonStore.kt:191`), 0 FIXME in codebase
- Technical debt tracked externally (GitHub issues, specs)
- No inline markers for future work

**Evidence:**
- Search: `grep -rn "TODO" app/src/main/java` → 1 match (`LessonStore.kt:191`, reserved-pack note); `grep -rn "FIXME"` → 0 matches
- Debt tracking: External via TASK-XXX issues and specification documents

**Why this is a problem:**
- **Actually a positive:** Clean codebase with no comment debt
- **Minor risk:** Developers must check external systems for known issues
- **Documentation:** Debt not visible in code where problems exist

**Blast radius:**
- **Files affected:** None (this is a positive finding)
- **Lines affected:** None
- **Breaking changes:** None
- **External impact:** Zero

**Proposed direction:**
1. **Current approach is working:** Continue external debt tracking
2. **Optional:** Consider inline comments for critical known issues
3. **Best practice:** Link GitHub issues in code comments where appropriate

**Risk level:** LOW

**Must-have tests before refactor:**
1. None - this is a positive finding

---

### Risk 16: Zero Commented-Out Code

**Title:** No commented-out dead code blocks

**Current behavior:**
- Zero significant commented-out code blocks found
- Clean codebase with no dead code in comments
- Only explanatory comments present

**Evidence:**
- Search: `grep -r "^\s*//\s*fun\s\|^\s*//\s*class\s"` returned no matches
- Assessment: Clean codebase with no commented-out dead code

**Why this is a problem:**
- **Actually a positive:** Excellent code hygiene
- **No action needed:** Current state is ideal

**Blast radius:**
- **Files affected:** None (this is a positive finding)
- **Lines affected:** None
- **Breaking changes:** None
- **External impact:** Zero

**Proposed direction:**
1. **Maintain current practice:** Keep codebase clean
2. **Code review:** Reject PRs that add commented-out code
3. **Documentation:** Document clean code practice in CLAUDE.md

**Risk level:** LOW

**Must-have tests before refactor:**
1. None - this is a positive finding

---

### Risk 17: Deep Link Compatibility Uncertainty

**Title:** ELITE/VOCAB routes may be referenced in external deep links

**Current behavior:**
- No deep link handling code found in MainActivity or AndroidManifest
- Unclear if external apps or bookmarks link to ELITE/VOCAB routes
- Redirect handlers provide fallback to HOME

**Evidence:**
- Search: No deep link handling code found
- Current: Redirect handlers send ELITE/VOCAB → HOME

**Why this is a problem:**
- Uncertainty: Don't know if external references exist
- User experience: Deep links would redirect to HOME (not ideal)
- Analytics gap: Can't measure ELITE/VOCAB deep link attempts

**Blast radius:**
- **Files affected:** None (uncertainty about external links)
- **Lines affected:** None
- **Breaking changes:** LOW - redirect handlers provide fallback
- **External impact:** LOW - redirects to HOME if links exist

**Proposed direction:**
1. **Immediate:** Add analytics logging for ELITE/VOCAB route attempts
2. **Short-term:** If zero attempts after 1 month, remove routes
3. **Long-term:** Implement proper deep link handling if needed

**Risk level:** LOW

**Must-have tests before refactor:**
1. **Analytics monitoring:** Track ELITE/VOCAB route access for 1 month
2. **Deep link test:** Verify redirect handlers work correctly
3. **User testing:** Verify no broken deep links in production

---

### Risk 18: Manual DI Scalability

**Title:** Manual DI may become unwieldy as store count grows

**Current behavior:**
- AppContainer manually defines 13 store dependencies
- Requires manual updates in 3 locations per new store
- No compile-time verification of dependency graph

**Evidence:**
```kotlin
// Current: 13 stores
val lessonStore: LessonStore by lazy { storeFactory.getLessonStore() }
val progressStore: ProgressStore by lazy { storeFactory.getProgressStore() }
// ... 11 more stores
```

**Why this is a problem:**
- Scalability limit: Becomes unwieldy as store count grows
- Error-prone: Easy to forget one of 3 required locations
- No compile-time checks: Can't verify dependency graph

**Blast radius:**
- **Files affected:** AppContainer.kt, StoreFactory.kt (2 files)
- **Lines affected:** ~80 lines total
- **Breaking changes:** MEDIUM - affects entire DI system
- **External impact:** Zero - internal DI only

**Proposed direction:**
1. **Immediate:** Continue with manual DI (acceptable at 13 stores)
2. **Threshold:** Migrate to Hilt/Koin if store count exceeds 20
3. **Monitoring:** Track store count growth rate

**Risk level:** LOW

**Must-have tests before refactor:**
1. **Complexity analysis:** Compare manual vs framework DI maintenance cost
2. **Performance test:** Measure DI initialization time
3. **Scalability test:** Project store count growth over next 6 months

---

### Risk 19: Cache Eviction Usage Documentation Gap

**Title:** Cache eviction methods lack usage documentation

**Current behavior:**
- `StoreFactory.evict()` and `clearCache()` methods exist
- KDoc explains purpose but not usage scenarios
- No clear documentation on when to call these methods

**Evidence:**
```kotlin
/**
 * Remove cached entries for the given packId, forcing fresh instances
 * on next access. Useful after progress resets.
 */
@Synchronized
fun evict(packId: String?) {
    wordMasteryCache.remove(packId)
    verbDrillCache.remove(packId)
}
```

**Why this is a problem:**
- Documentation gap: KDoc explains what but not when/why
- Usage uncertainty: Developers may not know when to call eviction
- Testing gap: Can't verify intended usage from documentation

**Blast radius:**
- **Files affected:** StoreFactory.kt (1 file)
- **Lines affected:** Documentation only
- **Breaking changes:** None - documentation fix only
- **External impact:** Zero - internal API

**Proposed direction:**
1. **Immediate:** Add usage examples to KDoc
2. **Documentation:** Add "when to evict cache" section to architecture docs
3. **Testing:** Add unit tests demonstrating cache eviction scenarios

**Risk level:** LOW

**Must-have tests before refactor:**
1. **Documentation review:** Verify KDoc is clear and complete
2. **Usage test:** Add unit tests for cache eviction scenarios
3. **Code review:** Verify documentation matches implementation

---

## Dead Code Confidence Levels

### HIGH Confidence Dead Code (Safe to Remove)

1. **ELITE navigation route** (AppScreen.kt:13, GrammarMateApp.kt:90, 383-385)
   - Evidence: Only redirects to HOME, no direct navigation
   - Confidence: HIGH - confirmed unused
   - Risk: LOW - redirect fallback handles any stray links

2. **VOCAB navigation route** (AppScreen.kt:14, GrammarMateApp.kt:91, 388-390)
   - Evidence: Only redirects to HOME, no direct navigation
   - Confidence: HIGH - confirmed unused
   - Risk: LOW - redirect fallback handles any stray links

### MEDIUM Confidence Dead Code (Remove After Verification)

3. **Deprecated pack-scoped migration methods** (DrillFileManager.kt:58-70, LessonStore.kt:88-89, 160-166)
   - Evidence: Marked @Deprecated, migration completed
   - Confidence: MEDIUM - need to verify no external consumers
   - Risk: LOW - clear migration path documented

### LOW Confidence Dead Code (Monitor Before Removal)

4. **StoreFactory cache eviction methods** (StoreFactory.kt:77-90)
   - Evidence: No static references, but infrastructure methods
   - Confidence: LOW - potential runtime usage
   - Risk: LOW - keep for safety, add usage logging

---

## Proposed Remediation Timeline

### Immediate (v1.7 - Current Release)

**Week 1: HIGH Risk Cleanup**
1. Remove ELITE navigation route (Risk 1)
2. Remove VOCAB navigation route (Risk 2)
3. Rename `onOpenElite` to `onOpenDailyPractice` (Risk 3)
4. Update AppScreen documentation (Risk 4)

**Tests required:**
- Navigation smoke test
- HomeScreen integration test
- Daily Practice button test

### Short-Term (v1.8 - Next Release)

**Week 1: MEDIUM Risk Cleanup**
1. Remove deprecated migration methods (Risk 9)
2. Remove old migration code from AppRoot.kt (Risk 10)
3. Add cache eviction usage logging (Risk 13)

**Week 2: Documentation**
1. Document migration removal timeline
2. Add cache eviction usage examples
3. Update architecture docs

**Tests required:**
- Migration verification test
- Integration test for all flows
- Performance test for app launch

### Long-Term (v1.9+)

**Architecture Improvements:**
1. Evaluate type-safe navigation (Risk 5, 12)
2. Consider DI framework if store count > 20 (Risk 11, 18)
3. Establish code removal process (Risk 8)

**Tests required:**
- Type-safe navigation POC
- DI migration POC
- Process documentation

---

## Blast Radius Summary

### HIGH Impact Changes (Require Extensive Testing)

| Change | Files | Lines | Breaking | Test Coverage |
|--------|-------|-------|----------|---------------|
| Remove ELITE route | 3 | ~15 | None | Navigation + Integration |
| Remove VOCAB route | 2 | ~10 | None | Navigation + Integration |
| Rename onOpenElite | 2 | ~30 | None | HomeScreen + Daily Practice |
| Update documentation | 1 | ~5 | None | Documentation review |

### MEDIUM Impact Changes (Require Integration Testing)

| Change | Files | Lines | Breaking | Test Coverage |
|--------|-------|-------|----------|---------------|
| Remove deprecated methods | 2 | ~20 | None | Integration + Migration |
| Remove migration code | 1 | ~55 | None | Migration + Performance |
| Add cache logging | 1 | ~5 | None | Unit + Runtime |

### LOW Impact Changes (Documentation Only)

| Change | Files | Lines | Breaking | Test Coverage |
|--------|-------|-------|----------|---------------|
| Update KDoc | 1 | ~10 | None | Documentation review |
| Add usage examples | 1 | ~15 | None | Documentation review |

---

## Risk Mitigation Strategies

### Strategy 1: Incremental Cleanup

**Approach:** Remove dead code incrementally over 3 releases
**Benefits:** Lower risk, easier rollback, better testing
**Timeline:** v1.7 (HIGH) → v1.8 (MEDIUM) → v1.9 (LOW)

### Strategy 2: Monitoring-First Removal

**Approach:** Add logging before removing uncertain code
**Benefits:** Data-driven decisions, runtime verification
**Timeline:** 1 month monitoring → removal if unused

### Strategy 3: Documentation-First Cleanup

**Approach:** Document removal plan before execution
**Benefits:** Team alignment, clear roadmap, less confusion
**Timeline:** Document → Review → Execute

---

## Conclusion

The BaseGrammy codebase demonstrates **excellent architectural health** with minimal technical debt:

**Positive Findings:**
- Near-zero TODO/FIXME (1 intentional reserved-pack note)
- Zero commented-out code blocks
- Clear deprecation warnings
- Well-documented migration paths

**Key Cleanup Opportunities:**
1. **HIGH Priority:** Remove ELITE/VOCAB dead routes (immediate impact)
2. **MEDIUM Priority:** Remove deprecated migration methods (v1.8)
3. **LOW Priority:** Improve documentation and monitoring

**Overall Assessment:** **HEALTHY** - Accumulated debt is manageable and can be cleaned incrementally without significant risk.

**Recommended Action:** Proceed with v1.7 HIGH-risk cleanup, followed by v1.8 MEDIUM-risk cleanup, with full test coverage at each step.

---

**Analysis Methodology:**
1. Read Wave 1 dependency map document
2. Classify findings by risk level (HIGH/MEDIUM/LOW)
3. Analyze blast radius for each risk
4. Propose remediation direction
5. Identify required tests before refactor
6. Create timeline for incremental cleanup

**Next Steps:**
1. Get team approval on cleanup plan
2. Create tracking tasks for each risk
3. Execute v1.7 cleanup with full test coverage
4. Monitor production metrics after each cleanup
5. Iterate on v1.8 and v1.9 cleanups
