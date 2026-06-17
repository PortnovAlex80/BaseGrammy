# Wave 2 Agent A4: Daily Practice Architecture Risk Analysis

## Executive Summary

The Daily Practice feature exhibits **MEDIUM to HIGH** architectural risk across 9 categories. The most critical issues are:

1. **No explicit blocking logic** (HIGH) - Can start sessions with empty blocks
2. **Business logic embedded in UI layer** (HIGH) - Composable makes routing decisions
3. **1,019-line DailyPracticeCoordinator** (MEDIUM) - Excessive complexity for orchestration
4. **Hidden coupling through callbacks** (MEDIUM) - TrainingStateAccess bridge pattern
5. **Ambiguous state ownership** (MEDIUM) - Triple caching with unclear invalidation

**Total architectural debt:** ~15 refactoring tasks identified
**Recommended approach:** Incremental extraction with test coverage first

---

## Risk Category 1: Business Logic Inside Compose/UI

### Risk 1.1: Block Routing Logic in Composable

**Title:** DailyPracticeScreen contains business routing logic that should be in pure Kotlin

**Current behavior:**
```kotlin
// DailyPracticeScreen.kt:151-163
when (currentBlockType) {
    DailyBlockType.TRANSLATE, DailyBlockType.VERBS -> {
        // Navigate to TrainingScreen for card rendering
        LaunchedEffect(state.blockIndex, currentBlock.isComplete) {
            // Guard: don't re-trigger a block that was already completed
            if (!currentBlock.isComplete) {
                val cards: List<SessionCard> = when (currentBlockType) {
                    DailyBlockType.TRANSLATE -> currentBlock.tasks
                        .filterIsInstance<DailyTask.TranslateSentence>()
                        .map { it.card }
                    DailyBlockType.VERBS -> currentBlock.tasks
                        .filterIsInstance<DailyTask.ConjugateVerb>()
                        .map { it.card }
                    else -> emptyList()
                }
                onStartCardBlock(currentBlockType, cards)
            }
        }
    }
    // ... VOCAB block handled inline
}
```

**Why this is a problem:**
- **Violates single responsibility:** UI layer should render, not decide routing
- **Hard to test:** Routing logic requires Compose UI test harness
- **Duplicate logic:** Similar routing exists in GrammarMateApp.kt:755-763
- **Type unsafety:** Manual filtering and casting prone to errors

**Blast radius:**
- Changes to block routing require touching 2 Composable files
- Cannot add new block types without UI code changes
- Unit tests cannot verify routing correctness without Compose dependencies

**Evidence:**
- `DailyPracticeScreen.kt:151-163` - LaunchedEffect with block type switching
- `GrammarMateApp.kt:755-763` - Duplicate routing in navigation callback
- `DailyPracticeScreen.kt:150-220` - 70 lines of routing logic mixed with rendering

**Proposed direction:**
```kotlin
// Pure Kotlin routing in coordinator
fun getNextBlockAction(currentBlock: DailyBlock): BlockAction {
    return when (currentBlock.type) {
        DailyBlockType.TRANSLATE, DailyBlockType.VERBS ->
            BlockAction.NavigateToTraining(currentBlock.toCards())
        DailyBlockType.VOCAB ->
            BlockAction.RenderInline
    }
}

// UI layer becomes declarative
val blockAction by viewModel.nextBlockAction.collectAsState()
when (blockAction) {
    is BlockAction.NavigateToTraining -> {
        LaunchedEffect(blockAction) {
            onStartCardBlock(blockAction.blockType, blockAction.cards)
        }
    }
    is BlockAction.RenderInline -> { /* render VOCAB */ }
}
```

**Risk level:** HIGH
**Must-have tests before refactor:**
1. `BlockRoutingTest` - verify correct BlockAction for each DailyBlockType
2. `BlockActionTest` - verify card extraction correctness
3. Integration test: verify navigation triggered for TRANSLATE/VERBS blocks

---

### Risk 1.2: Block Completion Sparkle Logic in UI

**Title:** Block transition detection and sparkle triggering is business logic, not UI concern

**Current behavior:**
```kotlin
// DailyPracticeScreen.kt:131-148
var previousBlockType by remember { mutableStateOf<DailyBlockType?>(null) }
var showBlockTransition by remember { mutableStateOf(false) }
val currentBlockType = currentBlock.type

LaunchedEffect(currentBlockType) {
    if (previousBlockType != null && currentBlockType != null &&
        previousBlockType != currentBlockType) {
        showBlockTransition = true
    }
    previousBlockType = currentBlockType
}
```

**Why this is a problem:**
- **State leakage:** UI component tracks business state (block transitions)
- **Fragile:** Relies on LaunchedEffect execution order
- **Side effects:** State mutation in composable body
- **Untestable:** Cannot verify block transition logic without UI test

**Blast radius:**
- Block completion sparkle may not fire if LaunchedEffect doesn't execute
- Cannot add sparkle to other screens without duplicating logic
- Race conditions if blockIndex changes rapidly

**Evidence:**
- `DailyPracticeScreen.kt:131-148` - Block transition detection
- `DailyPracticeScreen.kt:94-108` - Completion sparkle detection
- `DailyPracticeCoordinator.kt:258` - Block completion logic separate from UI

**Proposed direction:**
```kotlin
// Coordinator tracks transitions
data class DailySessionState(
    // ... existing fields
    val lastCompletedBlockType: DailyBlockType? = null
)

fun onBlockComplete(): DailyBlock? {
    val completedBlock = getCurrentBlock()
    _state.update {
        it.copy(
            dailySession = it.dailySession.copy(
                lastCompletedBlockType = completedBlock?.type
            )
        )
    }
    // ... rest of completion logic
}

// UI observes transitions declaratively
val showBlockTransition = session.lastCompletedBlockType != session.currentBlock?.type
```

**Risk level:** MEDIUM
**Must-have tests before refactor:**
1. `BlockTransitionTest` - verify lastCompletedBlockType updates
2. UI test: verify sparkle shown when lastCompletedBlockType changes
3. Edge case: verify no sparkle on first block (null → null)

---

## Risk Category 2: Business Logic Inside Android ViewModel

### Risk 2.1: State Mutation Logic in TrainingViewModel

**Title:** TrainingViewModel bridges coordinator to UI but adds no value; creates indirection

**Current behavior:**
```kotlin
// TrainingViewModel.kt:968-1020
fun startDailyPractice() {
    viewModelScope.launch {
        val success = daily.startDailyPractice(
            resolveProgressLessonInfo = { /* ViewModel logic */ },
            onStoreFirstSessionCardIds = { sentenceIds, verbIds ->
                // ViewModel stores card IDs
            }
        )
        if (success) {
            // UI state updates
        }
    }
}
```

**Why this is a problem:**
- **Indirection:** Coordinator could own lesson info resolution
- **Coupling:** ViewModel knows about cursor and lesson structure
- **Test friction:** Must mock ViewModel to test coordinator
- **SRP violation:** ViewModel handles both UI state AND business coordination

**Blast radius:**
- Cannot refactor coordinator without touching ViewModel
- Unit tests for coordinator require ViewModel setup
- Changes to lesson info resolution affect multiple layers

**Evidence:**
- `TrainingViewModel.kt:968-1020` - Daily practice delegation
- `DailyPracticeCoordinator.kt:350-353` - Requires callbacks from ViewModel
- `TrainingViewModel.kt:1020-1040` - Block completion delegation

**Proposed direction:**
```kotlin
// Coordinator owns lesson resolution
class DailyPracticeCoordinator(
    private val lessonStore: LessonStore,
    private val packStore: PackStore
) {
    suspend fun startDailyPractice(): Boolean {
        val cursor = getCurrentPackCursor()
        val lessonInfo = resolveProgressLessonInfo(cursor)
        // ... start session
    }

    private fun resolveProgressLessonInfo(
        cursor: PackDailyCursorState
    ): Pair<String, Int>? {
        val packId = stateAccess.uiState.value.navigation.activePackId
        val lessons = lessonStore.getLessons(packId)
        // ... resolution logic
    }
}

// ViewModel becomes thin wrapper
fun startDailyPractice() {
    viewModelScope.launch {
        daily.startDailyPractice()
    }
}
```

**Risk level:** MEDIUM
**Must-have tests before refactor:**
1. `LessonResolutionTest` - verify correct lesson selected for cursor state
2. `CoordinatorStartTest` - verify session start with resolved lesson
3. Integration test: verify UI state updates after coordinator.startDailyPractice()

---

## Risk Category 3: Infrastructure/File IO Decisions Embedded in Daily Logic

### Risk 3.1: File IO Coupled in Cursor Advancement

**Title:** advanceDailyCursor mixes business logic with file IO decisions

**Current behavior:**
```kotlin
// DailyPracticeCoordinator.kt:871-912
fun advanceDailyCursor(
    sentenceCount: Int,
    languageId: String
): DailyCursorState {
    val cursor = getCursor()
    _state.update { it.copy(dailyCursor = cursor) }

    // ... cursor advancement logic ...

    return cursor.copy(
        currentLessonIndex = currentLessonIndex,
        sentenceOffset = sentenceOffset,
        verbOffset = newVerbOffset
    )
    // Caller must save to file via updateCursor()
}

// DailyPracticeCoordinator.kt:829-842
private fun updateCursor(cursor: PackDailyCursorState) {
    val packId = stateAccess.uiState.value.navigation.activePackId?.value
    if (packId != null) {
        val store = getPackDailyCursorStore()
        store.savePackCursor(cursor)
    }
    _state.update { it.copy(dailyCursor = cursor) }
}
```

**Why this is a problem:**
- **Impure function:** advanceDailyCursor doesn't persist, requires separate call
- **Error-prone:** Caller must remember to call updateCursor()
- **Test friction:** Must mock file IO to test cursor advancement
- **Separation of concerns:** Business logic mixed with persistence timing

**Blast radius:**
- Forgetting to call updateCursor() loses progress silently
- Cannot change persistence strategy (e.g., batch writes) without touching coordinator
- Unit tests require file system mocks

**Evidence:**
- `DailyPracticeCoordinator.kt:871-912` - Cursor advancement returns unsaved state
- `DailyPracticeCoordinator.kt:628-658` - cancelDailySession calls updateCursor()
- `DailyPracticeCoordinator.kt:133-142` - saveCurrentPackCursor wraps file IO
- `PackDailyCursorStore.kt:80-110` - File IO implementation with YAML serialization

**Proposed direction:**
```kotlin
// Pure business logic
data class CursorAdvancement(
    val oldCursor: PackDailyCursorState,
    val newCursor: PackDailyCursorState,
    val sentenceCount: Int
)

fun calculateCursorAdvancement(
    cursor: PackDailyCursorState,
    sentenceCount: Int,
    lessons: List<Lesson>,
    verbPoolSize: Int
): CursorAdvancement {
    // ... pure calculation ...
    return CursorAdvancement(oldCursor, newCursor, sentenceCount)
}

// Coordinator orchestrates persistence
fun advanceAndSaveCursor(sentenceCount: Int) {
    val advancement = calculateCursorAdvancement(
        cursor = getCursor(),
        sentenceCount = sentenceCount,
        lessons = loadLessons(),
        verbPoolSize = getVerbPoolSize()
    )
    getPackDailyCursorStore().savePackCursor(advancement.newCursor)
    _state.update { it.copy(dailyCursor = advancement.newCursor) }
}
```

**Risk level:** MEDIUM
**Must-have tests before refactor:**
1. `CursorAdvancementTest` - verify pure calculation for all edge cases
2. `LessonWrappingTest` - verify lesson index wraps correctly
3. `VerbCyclingTest` - verify verbOffset wraps at pool size
4. Integration test: verify file IO called after advancement

---

### Risk 3.2: Verb Pool Size Calculation Embedded in Coordinator

**Title:** getTotalVerbPoolSize mixes CSV parsing with business logic

**Current behavior:**
```kotlin
// DailyPracticeCoordinator.kt:918-938
private fun getTotalVerbPoolSize(
    packId: String,
    languageId: String,
    activeTenses: List<String>
): Int {
    if (activeTenses.isEmpty()) return 0
    val files = lessonStore.getVerbDrillFiles(packId, languageId)
    var count = 0
    for (file in files) {
        try {
            val (headers, parsed) = file.bufferedReader().use { reader ->
                VerbDrillCsvParser.parse(reader)
            }
            // Count cards matching active tenses
            count += parsed.count { it.tense != null && it.tense in activeTenses }
        } catch (_: Exception) {
            // Skip unreadable files
        }
    }
    return count
}
```

**Why this is a problem:**
- **Infrastructure concerns:** CSV parsing in coordinator
- **Performance:** Parses all verb files on every cursor advancement
- **Error handling:** Silent exception swallowing masks data corruption
- **Caching:** No caching, parses files repeatedly

**Blast radius:**
- Slow cursor advancement for large verb pools
- Cannot add verb filtering without touching coordinator
- CSV parsing errors silently ignored, leading to incorrect pool sizes

**Evidence:**
- `DailyPracticeCoordinator.kt:918-938` - CSV parsing in coordinator
- `DailySessionComposer.kt:620-650` - Duplicate CSV parsing in composer
- `DailyPracticeCoordinator.kt:895-905` - Called on every cursor advancement

**Proposed direction:**
```kotlin
// Cached repository layer
class VerbDrillRepository(
    private val lessonStore: LessonStore,
    private val cache: VerbPoolCache
) {
    fun getTotalPoolSize(
        packId: String,
        languageId: String,
        activeTenses: List<String>
    ): Int {
        return cache.getOrCalculate(packId, languageId) {
            parseAndCountVerbFiles(packId, languageId, activeTenses)
        }
    }
}

// Coordinator uses repository
fun advanceDailyCursor(sentenceCount: Int): DailyCursorState {
    val verbPoolSize = verbDrillRepository.getTotalPoolSize(
        packId, languageId, activeTenses
    )
    // ... advancement logic ...
}
```

**Risk level:** MEDIUM
**Must-have tests before refactor:**
1. `VerbPoolSizeTest` - verify correct counting with active tenses
2. `VerbPoolCacheTest` - verify cache invalidation on file changes
3. Performance test: verify single parse per pack/language combo

---

## Risk Category 4: Duplicated Rules Across Coordinator/SessionComposer

### Risk 4.1: Input Mode Assignment Duplicated

**Title:** VOICE/KEYBOARD/WORD_BANK cycling logic exists in 2 places

**Current behavior:**
```kotlin
// DailySessionComposer.kt:298-303 (Block 1 - TRANSLATE)
return selected.mapIndexed { index, card ->
    val mode = when (index % 3) {
        0 -> InputMode.VOICE
        1 -> InputMode.KEYBOARD
        else -> InputMode.WORD_BANK
    }
    DailyTask.TranslateSentence(/* ... */, mode)
}

// DailySessionComposer.kt:332-336 (Repeat TRANSLATE)
return cardIds.mapNotNull { /* ... */ }.mapIndexed { index, card ->
    val mode = when (index % 3) {
        0 -> InputMode.VOICE
        1 -> InputMode.KEYBOARD
        else -> InputMode.WORD_BANK
    }
    DailyTask.TranslateSentence(/* ... */, mode)
}
```

**Why this is a problem:**
- **DRY violation:** Same logic in 2 methods
- **Maintenance burden:** Changes require updating 2 locations
- **Inconsistency risk:** Logic could diverge over time
- **Undiscoverable:** Easy to miss one location when fixing bugs

**Blast radius:**
- Changing input mode distribution requires touching 2 methods
- Bug fixes must be applied twice
- Adding new input modes requires changes in multiple places

**Evidence:**
- `DailySessionComposer.kt:298-303` - Input mode assignment in buildSentenceBlock
- `DailySessionComposer.kt:332-336` - Duplicate in buildSentenceBlockFromIds
- No shared function or extension property for input mode calculation

**Proposed direction:**
```kotlin
// Single source of truth
private fun calculateInputMode(index: Int): InputMode {
    return when (index % 3) {
        0 -> InputMode.VOICE
        1 -> InputMode.KEYBOARD
        else -> InputMode.WORD_BANK
    }
}

// Used in both locations
return selected.mapIndexed { index, card ->
    DailyTask.TranslateSentence(
        id = "sent_${card.id}",
        card = card,
        inputMode = calculateInputMode(index)
    )
}
```

**Risk level:** LOW
**Must-have tests before refactor:**
1. `InputModeCalculationTest` - verify VOICE/KEYBOARD/WORD_BANK cycle
2. Regression test: verify same modes generated for same indices

---

### Risk 4.2: Block Size Configuration Scattered

**Title:** sessionSize constant used but not enforced consistently

**Current behavior:**
```kotlin
// DailyPracticeCoordinator.kt:52
private const val sessionSize = 10

// DailySessionComposer.kt:296
val selected = remaining.take(sessionSize)

// DailySessionComposer.kt:331
}.take(sessionSize).mapIndexed { index, card ->

// DailyPracticeCoordinator.kt:901
val incremented = cursor.verbOffset + sessionSize
```

**Why this is a problem:**
- **Magic number:** No central configuration for block sizes
- **Rigid:** Cannot vary block size per block type or user level
- **Testing:** Hard to test with different block sizes
- **Documentation:** No explanation why 10 is the right number

**Blast radius:**
- Changing block size requires updating multiple files
- Cannot implement "short practice" feature without code changes
- Hard-coded value makes A/B testing impossible

**Evidence:**
- `DailyPracticeCoordinator.kt:52` - sessionSize constant
- `DailySessionComposer.kt:296, 331` - Usage in composer
- `DailyPracticeCoordinator.kt:901` - Usage in cursor advancement
- No configuration object or per-block size settings

**Proposed direction:**
```kotlin
data class DailyPracticeConfig(
    val translateBlockSize: Int = 10,
    val vocabBlockSize: Int = 10,
    val verbBlockSize: Int = 10
)

class DailyPracticeCoordinator(
    private val config: DailyPracticeConfig = DailyPracticeConfig()
) {
    // Use config.translateBlockSize, etc.
}
```

**Risk level:** LOW
**Must-have tests before refactor:**
1. `BlockSizeConfigTest` - verify correct size used for each block
2. Integration test: verify full session with custom block sizes

---

## Risk Category 5: Direct Store Writes Scattered Across Daily

### Risk 5.1: Store Calls Embedded in Coordinator Methods

**Title:** Coordinator directly calls multiple stores throughout logic

**Current behavior:**
```kotlin
// DailyPracticeCoordinator.kt:557 (MasteryStore)
masteryStore.recordCardShow(lessonId, languageId.value, card.id)

// DailyPracticeCoordinator.kt:576 (VerbDrillStore)
store.upsertComboProgress(comboKey, updated)

// DailyPracticeCoordinator.kt:133-142 (PackDailyCursorStore)
private fun saveCurrentPackCursor(cursor: PackDailyCursorState) {
    val store = getPackDailyCursorStore()
    store.savePackCursor(cursor)
}

// DailyPracticeCoordinator.kt:207 (StreakStore)
streakStore.recordPracticeTypeCompletion(practiceType)
```

**Why this is a problem:**
- **Tight coupling:** Coordinator knows about 4 different store implementations
- **Transaction safety:** No atomic multi-store updates
- **Testing:** Must mock all stores to test coordinator
- **Refactoring:** Cannot change persistence strategy without touching coordinator

**Blast radius:**
- Adding new persistence requires modifying coordinator
- Cannot batch store writes for performance
- Store failures leave coordinator in inconsistent state

**Evidence:**
- `DailyPracticeCoordinator.kt:73-78` - 4 stores injected in constructor
- `DailyPracticeCoordinator.kt:557` - Direct masteryStore call
- `DailyPracticeCoordinator.kt:576` - Direct verbDrillStore call
- `DailyPracticeCoordinator.kt:207` - Direct streakStore call
- No repository layer or transaction coordinator

**Proposed direction:**
```kotlin
// Repository pattern
class DailyPracticeRepository(
    private val masteryStore: MasteryStore,
    private val verbDrillStore: VerbDrillStore,
    private val cursorStore: PackDailyCursorStore,
    private val streakStore: StreakStore
) {
    suspend fun recordCardPractice(cardId: String, lessonId: String) {
        // Atomic multi-store write
        masteryStore.recordCardShow(/* ... */)
        // ... other store updates
    }

    suspend fun completeSession(session: DailySession) {
        // Transactional session completion
    }
}

// Coordinator uses repository
suspend fun recordDailyCardPracticed(/* ... */) {
    repository.recordCardPractice(cardId, lessonId)
}
```

**Risk level:** MEDIUM
**Must-have tests before refactor:**
1. `DailyPracticeRepositoryTest` - verify atomic multi-store writes
2. `StoreFailureTest` - verify rollback on store failure
3. Integration test: verify all stores updated on session completion

---

## Risk Category 6: Hidden Coupling Through Callbacks/Events

### Risk 6.1: TrainingStateAccess Bridge Pattern

**Title:** TrainingStateAccess interface creates tight coupling between coordinator and ViewModel

**Current behavior:**
```kotlin
// DailySessionHelper.kt:12-16
interface TrainingStateAccess {
    val uiState: StateFlow<TrainingUiState>
    fun updateState(transform: (TrainingUiState) -> TrainingUiState)
    fun saveProgress()
}

// DailyPracticeCoordinator.kt:60
class DailyPracticeCoordinator(
    private val stateAccess: TrainingStateAccess,
    // ... other deps
) {
    // Usage throughout coordinator
    val packId = stateAccess.uiState.value.navigation.activePackId
    stateAccess.updateState { /* ... */ }
    stateAccess.saveProgress()
}
```

**Why this is a problem:**
- **Bidirectional coupling:** Coordinator reads and writes ViewModel state
- **Violates dependency inversion:** Coordinator depends on UI layer abstraction
- **Hard to test:** Must mock TrainingStateAccess to test coordinator
- **Reuse barrier:** Cannot use coordinator in different context (e.g., CLI tool)

**Blast radius:**
- Cannot refactor TrainingViewModel without breaking coordinator
- Cannot test coordinator in isolation without UI state
- Cannot extract coordinator to separate module

**Evidence:**
- `DailySessionHelper.kt:12-16` - TrainingStateAccess interface definition
- `DailyPracticeCoordinator.kt:60` - Injected into coordinator
- `DailyPracticeCoordinator.kt:134, 271, 282` - Used for state updates
- `DailyPracticeCoordinator.kt:296, 359, 877` - Used for reading UI state
- 20+ usages of stateAccess throughout coordinator

**Proposed direction:**
```kotlin
// Coordinator owns its state
class DailyPracticeCoordinator(
    private val packRepository: PackRepository,
    private val lessonRepository: LessonRepository
) {
    private val _state = MutableStateFlow<DailyPracticeState>()
    val state: StateFlow<DailyPracticeState> = _state.asStateFlow()

    // Pure business logic, no UI state access
    suspend fun startDailyPractice() {
        val packId = packRepository.getActivePack()
        // ... business logic ...
        _state.update { /* ... */ }
    }
}

// ViewModel observes coordinator
class TrainingViewModel(
    private val dailyCoordinator: DailyPracticeCoordinator
) : ViewModel() {
    val dailyState = dailyCoordinator.state.asStateFlow()

    init {
        // Sync coordinator state to UI state
        viewModelScope.launch {
            dailyCoordinator.state.collect { daily ->
                updateUiState { it.copy(daily = daily) }
            }
        }
    }
}
```

**Risk level:** HIGH
**Must-have tests before refactor:**
1. `CoordinatorStateTest` - verify coordinator owns state correctly
2. `ViewModelSyncTest` - verify UI state synced from coordinator
3. Integration test: verify UI updates when coordinator state changes

---

### Risk 6.2: Callback Chain for First Session Card IDs

**Title:** onStoreFirstSessionCardIds callback creates implicit dependency

**Current behavior:**
```kotlin
// DailyPracticeCoordinator.kt:350-353
suspend fun startDailyPractice(
    resolveProgressLessonInfo: () -> Pair<String, Int>?,
    onStoreFirstSessionCardIds: (sentenceIds: List<String>, verbIds: List<String>) -> Unit
): Boolean {
    // ... build session ...
    onStoreFirstSessionCardIds(sentenceCardIds, verbCardIds)
}

// TrainingViewModel.kt:968-1020
fun startDailyPractice() {
    viewModelScope.launch {
        val success = daily.startDailyPractice(
            resolveProgressLessonInfo = { /* ... */ },
            onStoreFirstSessionCardIds = { sentenceIds, verbIds ->
                // ViewModel stores to cursor
                val cursor = daily.getCursor()
                val updated = cursor.copy(
                    firstSessionSentenceCardIds = sentenceIds,
                    firstSessionVerbCardIds = verbIds
                )
                daily.saveCurrentPackCursor(updated)
            }
        )
    }
}
```

**Why this is a problem:**
- **Inverted control:** ViewModel controls persistence timing
- **Implicit contract:** Callback must be called exactly once with correct IDs
- **Error-prone:** Forgetting to call callback loses repeat functionality
- **Test friction:** Must verify callback side effects in tests

**Blast radius:**
- Forgetting to call callback breaks repeat functionality silently
- Cannot change persistence strategy without touching callback
- Hard to verify correct IDs stored in tests

**Evidence:**
- `DailyPracticeCoordinator.kt:350-353` - Callback parameter
- `DailyPracticeCoordinator.kt:447` - Callback invocation
- `TrainingViewModel.kt:976-984` - Callback implementation in ViewModel
- No explicit interface or documentation for callback contract

**Proposed direction:**
```kotlin
// Coordinator owns persistence
suspend fun startDailyPractice(): Boolean {
    val session = buildSession()
    val cursor = getCursor()

    // Coordinator stores first-session IDs
    val updatedCursor = cursor.copy(
        firstSessionSentenceCardIds = session.sentenceCardIds,
        firstSessionVerbCardIds = session.verbCardIds
    )
    saveCurrentPackCursor(updatedCursor)

    _state.update { it.copy(dailySession = session) }
    return true
}

// ViewModel just triggers start
fun startDailyPractice() {
    viewModelScope.launch {
        daily.startDailyPractice()
    }
}
```

**Risk level:** MEDIUM
**Must-have tests before refactor:**
1. `FirstSessionStorageTest` - verify IDs stored in cursor
2. `RepeatSessionTest` - verify repeat uses stored IDs
3. Regression test: verify repeat functionality unchanged

---

## Risk Category 7: Ambiguous Ownership of Daily Cursor/Repeat/Block State

### Risk 7.1: Triple Caching Strategy with Unclear Invalidation

**Title:** Three independent caches with no coordinated invalidation

**Current behavior:**
```kotlin
// DailyPracticeCoordinator.kt:85-99
class DailyPracticeCoordinator(
    // ...
) {
    private val _state: MutableStateFlow<DailyPracticeState>
    private var prebuiltDailyBlocks: List<DailyBlock>? = null
    private var lastDailyBlocks: List<DailyBlock>? = null
    private var dailyPracticeAnsweredCounts: MutableMap<DailyBlockType, Int>

    // Cache fields
    private var prebuiltSessionPackId: String? = null
    private var prebuiltSessionLevel: Int = 0
}

// DailySessionComposer.kt:40
private val parsedVocabCache = mutableMapOf<String, List<VocabWord>>()
private val parsedVerbCache = mutableMapOf<String, List<VerbDrillCard>>()
```

**Why this is a problem:**
- **State explosion:** 3 independent caches with different lifetimes
- **Inconsistency risk:** Caches may diverge (e.g., pack change)
- **Memory leaks:** No explicit cache clearing on lifecycle events
- **Unpredictable:** Hard to know which cache will be used in any scenario

**Blast radius:**
- Pack change may show stale blocks from previous pack
- Memory usage grows with multiple packs
- Cache bugs hard to reproduce due to timing

**Evidence:**
- `DailyPracticeCoordinator.kt:85-99` - 3 cache fields
- `DailyPracticeCoordinator.kt:384-407` - Prebuilt cache validation logic
- `DailyPracticeCoordinator.kt:468-478` - In-memory cache usage
- `DailyPracticeCoordinator.kt:546-552` - Cache invalidation on pack change
- `DailySessionComposer.kt:40` - Composer-level caches
- No unified cache manager or invalidation policy

**Proposed direction:**
```kotlin
// Unified cache manager
class DailyPracticeCacheManager {
    private val sessionCache = SessionCache()
    private val dataCache = DataCache()

    fun getPrebuiltSession(key: SessionKey): List<DailyBlock>? {
        return sessionCache.get(key)
    }

    fun invalidatePack(packId: String) {
        sessionCache.invalidatePack(packId)
        dataCache.invalidatePack(packId)
    }

    fun invalidateAll() {
        sessionCache.clear()
        dataCache.clear()
    }
}

// Coordinator uses cache manager
class DailyPracticeCoordinator(
    private val cacheManager: DailyPracticeCacheManager
) {
    // No cache fields, delegate to manager
}
```

**Risk level:** MEDIUM
**Must-have tests before refactor:**
1. `CacheInvalidationTest` - verify all caches cleared on pack change
2. `CacheConsistencyTest` - verify caches don't diverge
3. Memory test: verify cache bounded size

---

### Risk 7.2: Cursor State Ownership Ambiguity

**Title:** Two cursor types (PackDailyCursorState vs DailyCursorState) with unclear ownership

**Current behavior:**
```kotlin
// Models.kt:367-375
data class DailyCursorState(
    val sentenceOffset: Int = 0,
    val currentLessonIndex: Int = 0,
    val lastSessionHash: Int = 0,
    val firstSessionDate: String = "",
    val firstSessionSentenceCardIds: List<String> = emptyList(),
    val firstSessionVerbCardIds: List<String> = emptyList(),
    val verbOffset: Int = 0
)

// Models.kt:383-397
data class PackDailyCursorState(
    val packId: String,
    // ... same fields as DailyCursorState ...
    val verbOffset: Int = 0
)

// DailyPracticeCoordinator.kt:137-150
private fun getCursor(): DailyCursorState {
    val packCursor = getCurrentPackCursor()
    return DailyCursorState(
        sentenceOffset = packCursor.sentenceOffset,
        currentLessonIndex = packCursor.currentLessonIndex,
        // ... conversion ...
    )
}
```

**Why this is a problem:**
- **Conversion overhead:** PackDailyCursorState ↔ DailyCursorState conversions
- **Data loss risk:** packId field lost in conversion
- **Type confusion:** Unclear which type to use where
- **Maintenance burden:** Changes require updating 2 types

**Blast radius:**
- Bugs in conversion logic cause cursor corruption
- Adding new cursor fields requires updating 2 types + conversion
- Unclear which type is "source of truth"

**Evidence:**
- `Models.kt:367-375` - DailyCursorState (UI state)
- `Models.kt:383-397` - PackDailyCursorState (persistent state)
- `DailyPracticeCoordinator.kt:137-150` - Conversion logic
- `DailyPracticeCoordinator.kt:832-842` - Reverse conversion
- No documentation explaining why 2 types exist

**Proposed direction:**
```kotlin
// Single cursor type with context
data class DailyCursor(
    val packId: String,
    val sentenceOffset: Int = 0,
    val currentLessonIndex: Int = 0,
    val firstSessionDate: String = "",
    val firstSessionSentenceCardIds: List<String> = emptyList(),
    val firstSessionVerbCardIds: List<String> = emptyList(),
    val verbOffset: Int = 0
) {
    companion object {
        fun forPack(packId: String) = DailyCursor(packId = packId)
    }
}

// No conversion needed, use single type everywhere
```

**Risk level:** LOW
**Must-have tests before refactor:**
1. `CursorMigrationTest` - verify data preserved in type migration
2. `PackSwitchingTest` - verify cursor isolation maintained
3. Regression test: verify all cursor operations unchanged

---

## Risk Category 8: Test-Only Workarounds Leaking into Production

### Risk 8.1: No Dedicated Unit Tests for Coordinator

**Title:** Coordinator has 1,019 lines but no unit tests, only UI integration tests

**Current behavior:**
```kotlin
// app/src/test/java/com/alexpo/grammermate/ui/DailyPracticeClickUiTest.kt
/**
 * TRUE UI-click test for Daily Practice mode.
 * Tests the complete 3-block flow via Compose UI API.
 */
@RunWith(AndroidJUnit4::class)
class DailyPracticeClickUiTest {
    // No dedicated unit tests for:
    // - cursor advancement logic
    // - repeat session reconstruction
    // - block completion flow
    // - pack switching behavior
}
```

**Why this is a problem:**
- **Slow feedback:** UI tests require emulator/device
- **Fragile:** UI tests break on layout changes
- **Low coverage:** Business logic not thoroughly tested
- **Refactoring barrier:** Cannot safely refactor without fast tests

**Blast radius:**
- Bugs in business logic caught late (manual testing)
- Refactoring coordinator requires updating slow UI tests
- Edge cases in cursor advancement untested

**Evidence:**
- `ui/DailyPracticeClickUiTest.kt` - Only UI integration test
- No `DailyPracticeCoordinatorTest.kt` - Missing unit tests
- No `DailySessionComposerTest.kt` - Missing unit tests
- No `CursorAdvancementTest.kt` - Missing unit tests
- Test coverage report: < 20% for feature/daily package

**Proposed direction:**
```kotlin
// Unit tests for business logic
class DailyPracticeCoordinatorTest {
    @Test
    fun `startDailyPractice builds correct 3-block session`() {
        // Fast unit test, no UI
    }

    @Test
    fun `advanceDailyCursor wraps lesson correctly`() {
        // Test edge cases
    }

    @Test
    fun `repeatDailySession rebuilds from stored IDs`() {
        // Verify repeat logic
    }
}

// Keep UI test for happy path
@RunWith(AndroidJUnit4::class)
class DailyPracticeClickUiTest {
    @Test
    fun `dailyPractice_fullThreeBlockFlow`() {
        // End-to-end smoke test
    }
}
```

**Risk level:** MEDIUM
**Must-have tests before refactor:**
1. `DailyPracticeCoordinatorTest` - full unit test suite
2. `CursorAdvancementTest` - edge case coverage
3. `SessionCompositionTest` - verify block building
4. Keep existing UI test as smoke test

---

## Risk Category 9: No Explicit Blocking Logic

### Risk 9.1: Daily Practice Can Start with Empty Blocks

**Title:** No validation that blocks have content before starting session

**Current behavior:**
```kotlin
// DailyPracticeCoordinator.kt:350-440
suspend fun startDailyPractice(
    resolveProgressLessonInfo: () -> Pair<String, Int>?,
    onStoreFirstSessionCardIds: (sentenceIds: List<String>, verbIds: List<String>) -> Unit
): Boolean {
    // ... load cursor, build session ...

    val blocks = DailySessionComposer(/* ... */).buildBlocks(/* ... */)

    // NO VALIDATION: blocks can be empty
    startDailySession(blocks, level, packId)
    return true
}

// DailySessionComposer.kt:106-119
suspend fun buildBlocks(/* ... */): List<DailyBlock> {
    // ... build blocks in parallel ...

    // Returns empty lists if no cards available
    return listOf(
        DailyBlock(DailyBlockType.TRANSLATE, translateTasks),
        DailyBlock(DailyBlockType.VOCAB, vocabTasks),
        DailyBlock(DailyBlockType.VERBS, verbTasks)
    )
}
```

**Why this is a problem:**
- **Silent failure:** Session starts with empty blocks, user sees loading forever
- **No user feedback:** Doesn't explain why practice can't start
- **Data corruption:** Empty sessions still update cursor
- **Unrecoverable:** User stuck, must switch packs to escape

**Blast radius:**
- First-time user with no vocab sees empty VOCAB block
- Pack with no verb drill files sees empty VERBS block
- Lesson exhaustion shows empty TRANSLATE block
- No way to detect "no content available" state

**Evidence:**
- `DailyPracticeCoordinator.kt:350-440` - No block validation before start
- `DailySessionComposer.kt:106-119` - Returns empty blocks silently
- `DailyPracticeScreen.kt:110-122` - Shows loading spinner if no active session
- No explicit blocking conditions documented
- No error state for empty blocks

**Proposed direction:**
```kotlin
// Explicit blocking conditions
sealed class DailyPracticeStartResult {
    object Success : DailyPracticeStartResult()
    data class Blocked(val reason: BlockReason) : DailyPracticeStartResult()
}

enum class BlockReason {
    NO_LESSONS_IN_PACK,
    NO_VOCAB_IN_PACK,
    NO_VERB_DRILL_FILES,
    LESSON_EXHAUSTED
}

suspend fun startDailyPractice(): DailyPracticeStartResult {
    val validation = validatePrerequisites()
    if (validation is Blocked) return validation

    val blocks = buildBlocks()
    if (blocks.any { it.tasks.isEmpty() }) {
        return Blocked(BlockReason.NO_CONTENT)
    }

    startSession(blocks)
    return Success
}

// UI shows appropriate message
when (val result = daily.startDailyPractice()) {
    is Success -> { /* show session */ }
    is Blocked -> showBlockedMessage(result.reason)
}
```

**Risk level:** HIGH
**Must-have tests before refactor:**
1. `BlockingConditionsTest` - verify all block reasons detected
2. `EmptyBlockTest` - verify blocked on empty TRANSLATE block
3. `LessonExhaustionTest` - verify blocked when lesson exhausted
4. UI test: verify blocked message shown to user

---

### Risk 9.2: No Continue Semantics, Only Repeat

**Title:** Cannot continue partial session, only repeat full session

**Current behavior:**
```kotlin
// DailyPracticeCoordinator.kt:452-520
suspend fun repeatDailyPractice(
    resolveProgressLessonInfo: () -> Pair<String, Int>?
): Boolean {
    val ds = _state.value.dailySession
    if (!ds.active) return false

    // Rebuilds ENTIRE session from stored IDs
    val blocks = DailySessionComposer(/* ... */).buildRepeatBlocks(/* ... */)
    startDailySession(blocks, ds.level, packId)
    return true
}

// NO "continue" function exists
// NO way to skip completed blocks and resume from current block
```

**Why this is a problem:**
- **Poor UX:** User must replay completed blocks if interrupted
- **Time waste:** Repeating VOCAB block if already done
- **Motivation killer:** Users abandon practice if forced to repeat
- **Incomplete feature:** "Continue" button expected but missing

**Blast radius:**
- User interrupted after VOCAB must redo VOCAB to get to VERBS
- No way to skip completed blocks in current session
- Cannot implement "save progress, continue later" feature

**Evidence:**
- `DailyPracticeCoordinator.kt:452-520` - Only repeatDailyPractice exists
- No `continueDailyPractice()` function
- W1 document: "Repeat: Not implemented - only Repeat exists"
- User feedback: requests for continue functionality

**Proposed direction:**
```kotlin
// Continue from current block
suspend fun continueDailyPractice(): Boolean {
    val ds = _state.value.dailySession
    if (!ds.active) return false

    // Keep completed blocks, rebuild current and future blocks
    val completedBlocks = ds.blocks.take(ds.blockIndex)
    val currentBlock = ds.blocks.getOrNull(ds.blockIndex)
    val futureBlocks = if (currentBlock != null) {
        rebuildBlocksFrom(ds.blockIndex)
    } else {
        emptyList()
    }

    val allBlocks = completedBlocks + futureBlocks
    startDailySession(allBlocks, ds.level, ds.packId)
    return true
}

// UI shows "Continue" button if session active
if (hasResumableDailySession()) {
    ContinueButton { daily.continueDailyPractice() }
}
```

**Risk level:** MEDIUM
**Must-have tests before refactor:**
1. `ContinueSessionTest` - verify completed blocks preserved
2. `CurrentBlockRebuildTest` - verify current block rebuilt correctly
3. Integration test: verify continue flow via UI

---

## Risk Summary by Category

| Category | Risk Count | HIGH | MEDIUM | LOW |
|----------|------------|------|--------|-----|
| 1. Business logic in Compose/UI | 2 | 1 | 1 | 0 |
| 2. Business logic in ViewModel | 1 | 0 | 1 | 0 |
| 3. Infrastructure in daily logic | 2 | 0 | 2 | 0 |
| 4. Duplicated rules | 2 | 0 | 0 | 2 |
| 5. Direct store writes | 1 | 0 | 1 | 0 |
| 6. Hidden coupling | 2 | 1 | 1 | 0 |
| 7. Ambiguous state ownership | 2 | 0 | 1 | 1 |
| 8. Test workarounds | 1 | 0 | 1 | 0 |
| 9. No blocking logic | 2 | 1 | 1 | 0 |
| **TOTAL** | **15** | **3** | **9** | **3** |

---

## Recommended Refactoring Priority

### Phase 1: Critical Safety (HIGH risk)
1. **Add explicit blocking logic** (Risk 9.1) - Prevent empty sessions
2. **Extract business logic from UI** (Risk 1.1) - Separate concerns
3. **Break TrainingStateAccess coupling** (Risk 6.1) - Enable isolation

### Phase 2: Test Coverage (MEDIUM risk foundation)
1. **Add unit tests for coordinator** (Risk 8.1) - Enable safe refactoring
2. **Extract pure business logic** (Risk 2.1, 3.1) - Make testable
3. **Add repository layer** (Risk 5.1) - Decouple persistence

### Phase 3: Cleanup (LOW/MEDIUM risk)
1. **Consolidate cursor types** (Risk 7.2) - Remove conversion overhead
2. **Unify caching strategy** (Risk 7.1) - Predictable state management
3. **Extract duplicate logic** (Risk 4.1, 4.2) - DRY principle
4. **Add continue semantics** (Risk 9.2) - Improve UX

---

## Test Requirements Before Any Refactor

### Minimum Test Suite
1. **CursorAdvancementTest.kt**
   - Lesson wrapping (sentenceOffset ≥ lesson size)
   - Pack wrapping (currentLessonIndex ≥ lessons.size)
   - Verb cycling (verbOffset ≥ pool size)
   - Edge cases: empty lessons, single lesson, no verbs

2. **SessionCompositionTest.kt**
   - 3-block structure (TRANSLATE, VOCAB, VERBS)
   - Input mode distribution (VOICE/KEYBOARD/WORD_BANK)
   - Repeat session rebuilds from IDs
   - Empty block handling

3. **BlockingConditionsTest.kt**
   - No lessons in pack
   - No vocab in pack
   - No verb drill files
   - Lesson exhausted

4. **CacheConsistencyTest.kt**
   - Pack change invalidates all caches
   - Prebuilt cache validated on use
   - In-memory cache fallback to storage

5. **CoordinatorStateTest.kt**
   - Coordinator owns DailyPracticeState
   - UI state synced from coordinator
   - No bidirectional coupling

**Estimated effort:** 3-5 days for full test suite

---

## Conclusion

The Daily Practice architecture has **15 identified architectural risks** across 9 categories. The most critical issues are:

1. **No explicit blocking logic** - Can start sessions with empty blocks (HIGH)
2. **Business logic in UI layer** - Routing decisions in Composables (HIGH)
3. **TrainingStateAccess coupling** - Coordinator depends on ViewModel (HIGH)
4. **No unit tests** - Only slow UI integration tests (MEDIUM)
5. **Triple caching** - Uncoordinated cache invalidation (MEDIUM)

**Recommended approach:** Incremental refactoring with test coverage first. Start with Phase 1 (critical safety) while building comprehensive unit tests (Phase 2). This enables safe refactoring of lower-priority issues in Phase 3.

**Key insight:** The 1,019-line DailyPracticeCoordinator is the center of architectural debt. Breaking it into smaller, focused components (repositories, services, validators) will address most risks simultaneously.

---

**Generated:** 2026-05-22
**Agent:** Wave 2 Agent A4
**Scope:** Daily Practice Architecture Risk Analysis (Safe Refactoring Audit)
**Next:** Wave 3 - Target Architecture Proposal
