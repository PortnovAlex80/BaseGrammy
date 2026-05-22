# Verb/Vocab Drill Architectural Risk Classification

**Analysis Date:** 2026-05-22
**Scope:** Architectural risks in VerbDrill and VocabDrill systems
**Purpose:** Classify problems by severity and blast radius for safe refactoring

---

## Executive Summary

**Total Risks Identified:** 12
- **HIGH:** 3 risks (urgent, blocks evolution)
- **MEDIUM:** 6 risks (important, limits maintainability)
- **LOW:** 3 risks (nice-to-have, technical debt)

**Critical Findings:**
1. VerbDrillViewModel is too large (1017 lines) with mixed responsibilities
2. UX inconsistency: VerbDrill has SessionCard, VocabDrill doesn't
3. No integration with main progress system (WORD_BANK ≠ mastery)
4. Test-only code leaked into production
5. Pack scoping creates edge cases and migration risks

---

## HIGH RISKS

### Risk #1: VerbDrillViewModel God Object

**Title:** VerbDrillViewModel violates single responsibility (1017 lines)

**Current behavior:**
```kotlin
// VerbDrillViewModel.kt:44-1017
class VerbDrillViewModel(
    private val application: Application,
    private val testStore: VerbDrillStore? = null  // Test-only param
) : AndroidViewModel(application) {
    // Card loading from CSV (lines 148-327)
    // Session management (lines 449-652)
    // Progress persistence (lines 604-626)
    // Last session handling (lines 704-833)
    // TTS integration (lines 835-900)
    // Bad sentence flagging (lines 902-950)
    // Speed tracking (lines 952-1017)
}
```

**Why this is a problem:**
- CLAUDE.md guidance: "TrainingViewModel is ~1500 lines. Decompose helpers to `feature/` when adding logic"
- VerbDrillViewModel is approaching this limit with 1017 lines
- Mixes infrastructure (CSV parsing, file I/O), business logic (session rules, progress calculation), and UI state
- Hard to test: need to mock CSV parser, store, TTS, and config just to test session logic
- Hard to understand: no clear separation between "what it does" and "how it works"

**Blast radius:**
- Any change to session logic risks breaking card loading or progress persistence
- Adding new features (e.g., new session type) requires touching 1000+ line file
- Test coverage is spotty because tests need to navigate complex setup

**Evidence:**
- `VerbDrillViewModel.kt:1-1017` - File size
- `VerbDrillViewModel.kt:148-327` - Card loading logic
- `VerbDrillViewModel.kt:449-652` - Session management
- `VerbDrillViewModel.kt:604-626` - Progress persistence
- `VerbDrillViewModel.kt:704-833` - Last session handling

**Proposed direction:**
Extract to `feature/verbdrill/` package:
```
feature/verbdrill/
  session/
    VerbDrillSessionManager.kt     # Session batch selection, state mutations
    VerbDrillSessionRepository.kt  # Last session persistence
  progress/
    VerbDrillProgressCalculator.kt # Progress tracking, combo rules
  cards/
    VerbDrillCardLoader.kt         # CSV parsing, card loading
  ui/
    VerbDrillViewModel.kt          # UI state only (delegates to feature classes)
```

**Risk level:** HIGH
**Must-have tests before refactor:**
1. `VerbDrillSessionCardRegressionTest` - All 4 tests must pass
2. Test that verifies `startSession()` produces same card batches after extraction
3. Test that verifies `persistCardProgress()` updates same progress state after extraction
4. Test that verifies `onRepeatSession()` restores same session state after extraction

---

### Risk #2: UX Inconsistency - VocabDrill Lacks SessionCard

**Title:** VocabDrill missing resume/repeat/reset functionality

**Current behavior:**
```kotlin
// VerbDrillViewModel.kt:734-833
fun onResumeSession() { /* Continue from saved session */ }
fun onRepeatSession() { /* Replay same cards */ }
fun onStartFresh() { /* Reset, delete last session */ }

// VocabDrillViewModel.kt - NO EQUIVALENT METHODS
// VocabDrill has no last session persistence
```

**Why this is a problem:**
- Users expect consistent UX across drills
- VerbDrill users can pause and resume, VocabDrill users cannot
- Inconsistent with "both drills are first-class features" expectation
- Blocks adding SessionCard to VocabDrill (would require designing from scratch)

**Blast radius:**
- User confusion: "Why can I resume VerbDrill but not VocabDrill?"
- Incomplete sessions in VocabDrill are lost on exit
- Cannot implement "continue where I left off" for VocabDrill
- Future features (e.g., session history) must handle both drills differently

**Evidence:**
- `VerbDrillViewModel.kt:734-833` - SessionCard methods
- `VerbDrillScreen.kt:101-127` - SessionCard UI
- `VocabDrillViewModel.kt:69-354` - No SessionCard methods
- `VocabDrillScreen.kt` - No SessionCard UI

**Proposed direction:**
1. Design `VocabDrillSessionCard` data structure (similar to `VerbDrillLastSessionState`)
2. Add `VocabDrillLastSessionState` to `WordMasteryStore`
3. Implement `onResumeSession()`, `onRepeatSession()`, `onStartFresh()` in `VocabDrillViewModel`
4. Add SessionCard UI to `VocabDrillScreen`
5. Adapt SRS algorithm to handle session resumption (e.g., exclude rated cards from Continue)

**Risk level:** HIGH
**Must-have tests before refactor:**
1. Test that verifies `onResumeSession()` excludes rated cards in VocabDrill
2. Test that verifies `onRepeatSession()` replays same cards in VocabDrill
3. Test that verifies `onStartFresh()` deletes last session but keeps mastery in VocabDrill
4. Regression test for VocabDrill SessionCard (similar to VerbDrill)

---

### Risk #3: No Integration with Main Progress System

**Title:** Drill progress is isolated from main app progress

**Current behavior:**
```kotlin
// VerbDrillViewModel.kt:632-639
private fun recordFireStreakIfNeeded() {
    if (correctCount >= sessionSize - badCount) {
        streakStore.recordPracticeTypeCompletion(languageId, PracticeType.VERB)
    }
}
// No integration with ProgressStore

// VocabDrillViewModel.kt:337-354
private fun recordFireStreakIfNeeded() {
    if (_hasRatedCards) {
        streakStore.recordPracticeTypeCompletion(languageId, PracticeType.VOCAB)
    }
}
// No integration with ProgressStore

// ProgressStore tracks WORD_BANK, VOICE, KEYBOARD only
// Drill progress is separate (VerbDrillComboProgress, WordMasteryState)
```

**Why this is a problem:**
- CLAUDE.md: "WORD_BANK ≠ mastery. Only VOICE and KEYBOARD grow flowers."
- Drills have their own mastery systems (VerbDrill: combo progress; VocabDrill: SRS steps)
- No way to see drill progress in main flower-growing UI
- Inconsistent "mastery" definition: drills have separate mastery from main app

**Blast radius:**
- Users cannot see drill progress in flower garden
- Drill mastery doesn't contribute to overall language progress
- Confusing UX: "Why doesn't VerbDrill grow my flowers?"
- Cannot implement "total mastery" dashboard that includes drills

**Evidence:**
- `VerbDrillViewModel.kt:632-639` - Fire streak recording only
- `VocabDrillViewModel.kt:337-354` - Fire streak recording only
- `ProgressStore.kt` - Tracks WORD_BANK, VOICE, KEYBOARD only
- `VerbDrillStore.kt:15-22` - Separate `VerbDrillComboProgress`
- `WordMasteryStore.kt:21-29` - Separate `WordMasteryState`

**Proposed direction:**
1. Define "drill mastery" in spec (e.g., "completed N sessions" or "reached SRS step 3")
2. Add drill mastery to `ProgressStore` as new practice types
3. Update flower-growing logic to include drill mastery
4. Decide: Should drill mastery grow flowers, or be separate?
5. Implement drill→progress sync on session completion

**Risk level:** HIGH
**Must-have tests before refactor:**
1. Test that verifies VerbDrill session completion updates ProgressStore
2. Test that verifies VocabDrill session completion updates ProgressStore
3. Test that verifies flower-growing logic includes drill mastery
4. Test that verifies WORD_BANK ≠ drill mastery (drill mastery counts, WORD_BANK doesn't)

---

## MEDIUM RISKS

### Risk #4: Test-Only Code Leaked into Production

**Title:** Test-only constructor and methods in VerbDrillViewModel

**Current behavior:**
```kotlin
// VerbDrillViewModel.kt:59-80
class VerbDrillViewModel(
    private val application: Application,
    private val testStore: VerbDrillStore? = null  // Test-only param
) : AndroidViewModel(application) {

    private val verbDrillStore: VerbDrillStore by lazy {
        testStore ?: container.verbDrillStore(currentPackId)
    }

    fun injectTestCards(cards: List<VerbDrillCard>) {
        allCards = cards
    }
}
```

**Why this is a problem:**
- Production code has test-only parameters and methods
- Violates "production code should not know about tests"
- Risk of accidental misuse in production (e.g., `injectTestCards()` called from UI)
- Unclear API: which constructor is "real"?

**Blast radius:**
- Production APK includes test-only code (increases APK size)
- Potential security risk if `injectTestCards()` is exploited
- Confusing for new developers: "Why is there a `testStore` param?"
- Cannot safely delete test code without reviewing all usages

**Evidence:**
- `VerbDrillViewModel.kt:59-80` - Test-only constructor and `injectTestCards()`
- `VerbDrillSessionCardRegressionTest.kt:40-60` - Uses test constructor

**Proposed direction:**
1. Remove `testStore` parameter from production constructor
2. Move `injectTestCards()` to test-only base class or factory
3. Use dependency injection (e.g., Hilt) to provide test doubles
4. Or: Keep test code but mark as `@VisibleForTesting` and add documentation

**Risk level:** MEDIUM
**Must-have tests before refactor:**
1. Test that verifies production constructor works without testStore
2. Test that verifies test constructor still works after refactoring
3. Test that verifies `injectTestCards()` behavior is preserved in test-only path

---

### Risk #5: Pack Scoping Edge Cases

**Title:** Pack-scoped vs legacy mode creates complex migration paths

**Current behavior:**
```kotlin
// VerbDrillViewModel.kt:148-206
fun reloadForPack(packId: String) {
    currentPackId = packId
    verbDrillStore = container.verbDrillStore(packId)  // Pack-scoped
    // Load cards from pack
}

fun reloadForLanguage(languageId: String) {
    // Find pack with verb drill files
    val pack = packs.find { it.hasVerbDrill }
    currentPackId = pack?.id
    verbDrillStore = container.verbDrillStore(currentPackId)  // Still pack-scoped
    // Legacy fallback: no pack found
}

// VerbDrillStore.kt:54-65
fun verbDrillStore(packId: String?): VerbDrillStore {
    return packId?.let {
        VerbDrillStore(getPackDrillPath(packId, "verb_drill_progress.yaml"))
    } ?: VerbDrillStore(getGlobalDrillPath("verb_drill_progress_${languageId}.yaml"))
}
```

**Why this is a problem:**
- Two code paths for same feature (pack-scoped vs legacy)
- Migration logic is complex: must detect legacy files and migrate to pack-scoped
- Edge cases: What if user has both pack-scoped and legacy files? Which takes precedence?
- Hard to test: need to simulate both modes and migration scenarios

**Blast radius:**
- Users with legacy progress files may lose data if migration fails
- Pack switching may not preserve progress correctly
- Hard to add new pack features (e.g., pack-to-pack progress transfer)
- Migration bugs could corrupt progress files

**Evidence:**
- `VerbDrillViewModel.kt:148-206` - Pack vs language loading
- `VocabDrillViewModel.kt:69-106` - Pack vs language loading
- `VerbDrillStore.kt:54-65` - Pack-scoped vs legacy file paths
- `WordMasteryStore.kt:38-48` - Pack-scoped vs legacy file paths

**Proposed direction:**
1. Decide: Deprecate legacy mode or support both forever?
2. If deprecating: Write migration script (legacy → pack-scoped)
3. Add validation: Detect and warn about conflicting files
4. Simplify ViewModel: Always use pack-scoped, fail fast if no pack
5. Add tests for migration scenarios

**Risk level:** MEDIUM
**Must-have tests before refactor:**
1. Test that verifies legacy → pack-scoped migration preserves all progress
2. Test that verifies pack switching preserves progress correctly
3. Test that verifies conflicting files (legacy + pack-scoped) are handled safely
4. Test that verifies error messages are clear when pack is missing

---

### Risk #6: State Mutation Complexity

**Title:** VerbDrill has multiple state mutation paths with inconsistent rules

**Current behavior:**
```kotlin
// VerbDrillViewModel.kt:524-599
// Path 1: Correct answer
submitCorrectAnswer() {
    session = session.copy(currentIndex++, correctCount++)
    persistCardProgress(card)
}

// Path 2: Card completed (hint/skip)
markCardCompleted() {
    session = session.copy(currentIndex++, incorrectCount++)
    persistCardProgress(card)
}

// Path 3: Navigation only (prev/next without Check)
// Does NOT call persistCardProgress()
// Does NOT mark card as shown

// Path 4: SessionCard actions
onResumeSession() { /* Loads new cards */ }
onRepeatSession() { /* Replays saved cards */ }
onStartFresh() { /* Deletes session */ }
```

**Why this is a problem:**
- Four different ways to mutate session state
- Easy to introduce bugs: e.g., forget to persist in one path
- Unclear which path is "correct" for a given user action
- Hard to test: need to cover all paths

**Blast radius:**
- Progress loss if `persistCardProgress()` is forgotten
- Inconsistent "shown" tracking if paths diverge
- Session state corruption if mutations race
- Regression bugs when adding new features

**Evidence:**
- `VerbDrillViewModel.kt:524-599` - Multiple state mutation paths
- `VerbDrillViewModel.kt:604-626` - `persistCardProgress()` called in some paths but not others
- `VerbDrillViewModel.kt:734-833` - SessionCard actions mutate state differently

**Proposed direction:**
1. Extract state machine for session mutations
2. Single entry point for state mutations (e.g., `transitionTo(action, data)`)
3. Separate "navigation" from "completion" in state machine
4. Add invariant checks (e.g., "card must be shown before advancing")

**Risk level:** MEDIUM
**Must-have tests before refactor:**
1. Test that verifies all state paths produce same progress after extraction
2. Test that verifies navigation-only cards are NOT marked shown
3. Test that verifies state machine invariants (e.g., no double-counting)
4. Test that verifies SessionCard actions preserve state correctly

---

### Risk #7: Cache Invalidation Risks

**Title:** In-memory caching with manual invalidation is fragile

**Current behavior:**
```kotlin
// VerbDrillStore.kt:74-84
private var progressCache: Map<String, VerbDrillComboProgress>? = null

fun loadProgress(): Map<String, VerbDrillComboProgress> {
    return progressCache ?: loadProgressFromFile().also { progressCache = it }
}

fun upsertComboProgress(key: String, progress: VerbDrillComboProgress) {
    // ... write to file ...
    progressCache = null  // Manual invalidation
}

// WordMasteryStore.kt:52-54
private var cache: Map<String, WordMasteryState>? = null

fun invalidateCache() {
    cache = null  // Manual invalidation, must be called explicitly
}
```

**Why this is a problem:**
- Manual cache invalidation is error-prone
- If `invalidateCache()` is forgotten, stale data is served
- No cache expiration or TTL
- Risk of race conditions if multiple invalidation paths exist

**Blast radius:**
- Stale progress shown to users
- Lost updates if cache is not invalidated before write
- Inconsistent UI: progress changes don't appear immediately
- Hard to debug: cache bugs are intermittent

**Evidence:**
- `VerbDrillStore.kt:74-84` - Manual cache invalidation
- `WordMasteryStore.kt:52-54` - Manual cache invalidation
- `VerbDrillStore.kt:194-199` - `upsertComboProgress()` invalidates cache
- `WordMasteryStore.kt:128-133` - `upsertMastery()` invalidates cache

**Proposed direction:**
1. Use auto-invalidating cache (e.g., `StateFlow` or `Observable`)
2. Or: Remove cache and load from file every time (files are small)
3. Add cache TTL to prevent stale data
4. Add logging to track cache hits/misses for debugging

**Risk level:** MEDIUM
**Must-have tests before refactor:**
1. Test that verifies cache is invalidated on write
2. Test that verifies stale data is not served after invalidation
3. Test that verifies cache invalidation is thread-safe
4. Test that verifies multiple writes don't lose updates

---

### Risk #8: Fire Streak Logic Inconsistency

**Title:** Different conditions for recording fire streak across drills

**Current behavior:**
```kotlin
// VerbDrillViewModel.kt:632-639
private fun recordFireStreakIfNeeded() {
    if (correctCount >= sessionSize - badCount) {
        streakStore.recordPracticeTypeCompletion(languageId, PracticeType.VERB)
    }
}

// VocabDrillViewModel.kt:337-354
private fun recordFireStreakIfNeeded() {
    if (_hasRatedCards) {  // Any non-AGAIN rating
        streakStore.recordPracticeTypeCompletion(languageId, PracticeType.VOCAB)
    }
}
```

**Why this is a problem:**
- VerbDrill requires "good session" (correct >= sessionSize - badCount)
- VocabDrill requires "any effort" (at least one non-AGAIN rating)
- Inconsistent: Why is VocabDrill more lenient?
- Confusing for users: "Why did VerbDrill record a streak but VocabDrill didn't?"

**Blast radius:**
- Users may not understand streak rules
- Hard to document: "When does a session count?"
- Cannot unify streak logic across drills
- Risk of exploits: Users can farm streaks in VocabDrill with minimal effort

**Evidence:**
- `VerbDrillViewModel.kt:632-639` - Streak condition: correct >= sessionSize - badCount
- `VocabDrillViewModel.kt:337-354` - Streak condition: hasRatedCards
- `StreakStore.kt` - Records streaks per practice type

**Proposed direction:**
1. Define unified streak condition in spec (e.g., "completed session with ≥ 50% correct")
2. Implement `shouldRecordStreak()` in shared drill interface
3. Or: Accept that drills have different streak rules and document clearly

**Risk level:** MEDIUM
**Must-have tests before refactor:**
1. Test that verifies VerbDrill streak condition after unification
2. Test that verifies VocabDrill streak condition after unification
3. Test that verifies streak recording is consistent across drills

---

### Risk #9: Last Session Validation Edge Cases

**Title:** VerbDrill validates last session on load, may discard valid sessions

**Current behavior:**
```kotlin
// VerbDrillViewModel.kt:328-346
private fun loadLastSession() {
    val lastSession = verbDrillStore.loadLastSession()
    if (lastSession != null) {
        // Validate pack ID
        if (lastSession.packId != currentPackId) {
            // Discard session
            return
        }
        // Validate card IDs exist in current cards
        val validCardIds = lastSession.sessionCardIds.filter { it in cardIdSet }
        if (validCardIds.size < lastSession.sessionCardIds.size) {
            // Discard or truncate session
            return
        }
    }
}
```

**Why this is a problem:**
- Aggressive validation may discard valid sessions
- Edge case: User switches packs, then switches back → session is lost
- Edge case: Cards are removed from CSV → session is truncated or discarded
- No user notification: Session disappears silently

**Blast radius:**
- Users lose session data unexpectedly
- Support tickets: "Where did my session go?"
- Cannot recover from validation failures
- Hard to debug: No logs showing why session was discarded

**Evidence:**
- `VerbDrillViewModel.kt:328-346` - Last session validation
- `VerbDrillViewModel.kt:704-833` - SessionCard actions depend on last session

**Proposed direction:**
1. Add user notification when session is discarded
2. Add logging to track validation failures
3. Or: Relax validation (e.g., allow cross-pack sessions if card IDs match)
4. Or: Archive invalid sessions instead of deleting

**Risk level:** MEDIUM
**Must-have tests before refactor:**
1. Test that verifies valid session is loaded correctly
2. Test that verifies invalid session (pack mismatch) is handled gracefully
3. Test that verifies partial session (some cards missing) is truncated or recovered
4. Test that verifies user is notified when session is discarded

---

## LOW RISKS

### Risk #10: Session Size Configuration Mid-Session

**Title:** Session size read from config on every reload, may change mid-session

**Current behavior:**
```kotlin
// VerbDrillViewModel.kt:138,149,185
private var sessionSize: Int = 10

fun reloadForPack(packId: String) {
    sessionSize = configStore.getSessionSize()  // Read from config
    // ...
}

// If user changes session size in config mid-session:
// - Current session continues with old size
// - Next session uses new size
// - Inconsistent behavior
```

**Why this is a problem:**
- Session size can change between sessions
- No validation: What if config has invalid size (e.g., 0 or 1000)?
- No UI feedback: User doesn't know current session size
- Hard to test: Need to simulate config changes

**Blast radius:**
- Minor: Session size changes are rare
- User confusion: "Why did my session size change?"
- Edge cases: Invalid config values may cause crashes

**Evidence:**
- `VerbDrillViewModel.kt:138,149,185` - Session size read from config
- `VocabDrillViewModel.kt:70,92` - Session size read from config
- `ConfigStore.kt` - Stores session size

**Proposed direction:**
1. Cache session size at session start, don't re-read until next session
2. Add validation for session size (e.g., 1-50 range)
3. Add UI indicator showing current session size

**Risk level:** LOW
**Must-have tests before refactor:**
1. Test that verifies session size is cached at session start
2. Test that verifies invalid session size is handled gracefully
3. Test that verifies session size changes apply to next session only

---

### Risk #11: No Drill Abstraction Layer

**Title:** VerbDrill and VocabDrill share no common interface

**Current behavior:**
```kotlin
// VerbDrillViewModel and VocabDrillViewModel are separate classes
// No shared interface or base class
// No way to treat drills polymorphically

// Example: Cannot do this:
val drills: List<DrillViewModel> = listOf(verbDrillViewModel, vocabDrillViewModel)
drills.forEach { it.startSession() }
```

**Why this is a problem:**
- Duplicated code across ViewModels (e.g., session size config, fire streak recording)
- Hard to add new drill types (e.g., NounDrill, GrammarDrill)
- Cannot implement drill-agnostic features (e.g., "start any drill")
- Inconsistent APIs: Each drill has different method signatures

**Blast radius:**
- Code duplication increases maintenance burden
- Adding new drills requires copying code
- Cannot unify drill UI (e.g., drill selection screen)
- Hard to test: Need separate test suites for each drill

**Evidence:**
- `VerbDrillViewModel.kt` - 1017 lines, no base class
- `VocabDrillViewModel.kt` - 536 lines, no base class
- No `DrillViewModel` interface in codebase

**Proposed direction:**
1. Define `DrillViewModel` interface with common methods (e.g., `startSession()`, `exitSession()`)
2. Extract common logic to abstract base class
3. Or: Use composition instead of inheritance (e.g., `SessionManager`, `ProgressTracker`)

**Risk level:** LOW
**Must-have tests before refactor:**
1. Test that verifies common interface works for both drills
2. Test that verifies extracted common logic preserves behavior
3. Test that verifies new drill can be added using interface

---

### Risk #12: TTS and Voice Input Coupling

**Title:** TTS and voice input are tightly coupled to drill ViewModels

**Current behavior:**
```kotlin
// VerbDrillViewModel.kt:835-900
private fun speakVerb(verb: String) {
    ttsEngine.speak(verb, languageId)
}

// VocabDrillViewModel.kt:376-479
private fun startVoiceRecognition() {
    voiceRecognitionIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageId)
    startActivity(voiceRecognitionIntent)
}
```

**Why this is a problem:**
- TTS and voice input are infrastructure concerns, not business logic
- Hard to test: Need to mock TTS and RecognizerIntent
- Hard to reuse: Cannot use TTS/voice in other screens without copying code
- Tight coupling to Android framework (RecognizerIntent)

**Blast radius:**
- Minor: TTS and voice input are stable, rarely change
- Test complexity: Need to mock Android framework
- Code duplication: TTS/voice code copied across ViewModels

**Evidence:**
- `VerbDrillViewModel.kt:835-900` - TTS integration
- `VocabDrillViewModel.kt:376-479` - Voice input integration
- `TtsEngine.kt` - TTS service
- No shared TTS/voice abstraction layer

**Proposed direction:**
1. Extract TTS/voice to separate use cases or managers
2. Define interfaces (e.g., `Speaker`, `VoiceRecognizer`) to decouple from Android
3. Or: Accept coupling as reasonable (TTS/voice are drill-specific)

**Risk level:** LOW
**Must-have tests before refactor:**
1. Test that verifies TTS still works after extraction
2. Test that verifies voice input still works after extraction
3. Test that verifies extracted interfaces are easier to mock

---

## Summary Table

| Risk | Level | Component | Blast Radius | Effort to Fix |
|------|-------|-----------|--------------|---------------|
| #1 VerbDrillViewModel size | HIGH | VerbDrillViewModel | Session, progress, TTS, CSV | High (extract to feature/) |
| #2 VocabDrill lacks SessionCard | HIGH | VocabDrillViewModel, UI | UX consistency, session resumption | High (design from scratch) |
| #3 No progress integration | HIGH | Both ViewModels, ProgressStore | Flower-growing, mastery tracking | High (spec decision needed) |
| #4 Test-only code in production | MEDIUM | VerbDrillViewModel | APK size, security, clarity | Low (remove or mark @VisibleForTesting) |
| #5 Pack scoping edge cases | MEDIUM | Both ViewModels, Stores | Migration, data loss | Medium (migration script) |
| #6 State mutation complexity | MEDIUM | VerbDrillViewModel | Progress loss, state corruption | Medium (extract state machine) |
| #7 Cache invalidation risks | MEDIUM | Both Stores | Stale data, lost updates | Low (remove cache or add TTL) |
| #8 Fire streak inconsistency | MEDIUM | Both ViewModels | Streak tracking, user confusion | Low (unify condition) |
| #9 Last session validation | MEDIUM | VerbDrillViewModel | Session loss, user confusion | Medium (add notification) |
| #10 Session size mid-session | LOW | Both ViewModels | Minor UX inconsistency | Low (cache session size) |
| #11 No drill abstraction | LOW | Both ViewModels | Code duplication, extensibility | Medium (define interface) |
| #12 TTS/voice coupling | LOW | Both ViewModels | Test complexity, reusability | Low (extract to use cases) |

---

## Refactoring Priority

**Phase 1 (Urgent - Blocks Evolution):**
1. Risk #3: No progress integration (spec decision needed)
2. Risk #2: VocabDrill lacks SessionCard (UX consistency)

**Phase 2 (Important - Limits Maintainability):**
3. Risk #1: VerbDrillViewModel size (extract to feature/)
4. Risk #5: Pack scoping edge cases (migration script)
5. Risk #6: State mutation complexity (extract state machine)

**Phase 3 (Technical Debt - Nice to Have):**
6. Risk #4: Test-only code in production
7. Risk #7: Cache invalidation risks
8. Risk #8: Fire streak inconsistency
9. Risk #9: Last session validation

**Phase 4 (Optional - Low Impact):**
10. Risk #10: Session size mid-session
11. Risk #11: No drill abstraction
12. Risk #12: TTS/voice coupling

---

## Evidence References

**VerbDrillViewModel:**
- Size and responsibilities: `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt:1-1017`
- Test-only code: `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt:59-80`
- State mutations: `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt:524-599`
- SessionCard actions: `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt:734-833`
- Pack scoping: `app/src/main/java/com/alexpo/grammermate/ui/VerbDrillViewModel.kt:148-206`

**VocabDrillViewModel:**
- Size and responsibilities: `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillViewModel.kt:1-536`
- Pack scoping: `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillViewModel.kt:69-106`
- Fire streak: `app/src/main/java/com/alexpo/grammermate/ui/VocabDrillViewModel.kt:337-354`

**Stores:**
- VerbDrillStore: `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt:1-376`
- WordMasteryStore: `app/src/main/java/com/alexpo/grammermate/data/WordMasteryStore.kt:1-189`
- Cache invalidation: `app/src/main/java/com/alexpo/grammermate/data/VerbDrillStore.kt:74-84`, `WordMasteryStore.kt:52-54`

**Tests:**
- VerbDrillSessionCardRegressionTest: `app/src/test/java/com/alexpo/grammermate/ui/VerbDrillSessionCardRegressionTest.kt:40-359`

---

## Next Steps

1. **Review this document** with team to validate risk classifications
2. **Create implementation tasks** for Phase 1 and Phase 2 risks
3. **Write missing tests** before refactoring (see "Must-have tests before refactor" for each risk)
4. **Update specs** to define progress integration and SessionCard for VocabDrill
5. **Execute refactor** in waves (Wave 3: Progress integration, Wave 4: SessionCard, Wave 5: ViewModel extraction)
