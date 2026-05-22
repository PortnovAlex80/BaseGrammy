# Wave 3: Target Architecture Proposal

**Project:** GrammarMate (BaseGrammy)  
**Date:** 2025-01-22  
**Wave:** 3 of 4 (Mapping → Risks → Target Proposal → Synthesis)  
**Status:** Draft for Review

---

## Executive Summary

GrammarMate suffers from architectural debt accumulated over 3+ years of iterative development. The primary issues are:

1. **God Objects**: TrainingViewModel (1,570 lines), VerbDrillViewModel (1,017 lines), DailyPracticeCoordinator (970 lines), ProgressTracker (487 lines)
2. **Business Logic in UI**: Core rules embedded in Compose callbacks and UI state holders
3. **State Mutation Scatter**: `currentIndex` and other state mutated in 8+ locations
4. **Zero Test Coverage**: No unit tests for ViewModels, no real store tests, no feature tests for special modes
5. **Dual Storage Patterns**: Legacy global stores mixed with new pack-scoped stores
6. **Event-Driven Complexity**: SessionEvent with callbacks creating circular dependencies

This proposal defines a **target clean architecture** that:

- **Preserves all user-visible behavior** (no breaking changes)
- **Extracts business logic to pure Kotlin** (testable without Android)
- **Separates concerns** into Domain → Application → Infrastructure → UI layers
- **Removes god objects** through decomposition
- **Adds comprehensive test coverage** before refactoring
- **Migrates incrementally** via small, safe PRs

**Estimated effort**: 6-8 months across 4 phases  
**Risk level**: Medium (mitigated by test-first approach and incremental migration)

---

## Current Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                           Compose UI Layer                           │
├─────────────────────────────────────────────────────────────────────┤
│ GrammarMateApp.kt (1,321 lines)                                     │
│ - Navigation (string-based routes)                                  │
│ - Dialog state management                                           │
│ - Session coordination logic                                        │
│ - Persistence callbacks                                             │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ StateFlow<TrainingUiState>
                                    │ direct ViewModel access
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         Presentation Layer                           │
├─────────────────────────────────────────────────────────────────────┤
│ TrainingViewModel (1,570 lines) ──────┐                            │
│ - ALL app state (25+ fields)          │                            │
│ - Business rules (mastery, SRS)       │                            │
│ - Session orchestration               │    VerbDrillViewModel      │
│ - State mutation (currentIndex × 8)   │    (1,017 lines)           │
│ - Event handling (SessionEvent)       │    - Duplicate state       │
│ - Persistence coordination            │    - No progress integration│
│                                       │                            │
│ ┌─────────────────────────────────┐  │                            │
│ │ SessionRunner (1,240 lines)     │  │                            │
│ │ - Card session logic            │  │                            │
│ │ - Retry/hint state machine      │  │                            │
│ │ - Event callbacks               │  │                            │
│ └─────────────────────────────────┘  │                            │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Direct store access
                                    │ Business logic in callbacks
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Business Logic (Scattered)                        │
├─────────────────────────────────────────────────────────────────────┤
│ ProgressTracker (487 lines)                                         │
│ - Wraps 4 stores                                                     │
│ - Dual storage pattern (legacy + pack-scoped)                       │
│ - WORD_BANK mode ambiguity                                          │
│                                                                      │
│ DailyPracticeCoordinator (970 lines)                                │
│ - Triple caching strategy                                           │
│ - Session composition (3 blocks)                                    │
│ - No blocking logic                                                 │
│                                                                      │
│ Special Modes (Boss, Elite, Story, Pomodoro)                        │
│ - Command/event pattern                                             │
│ - State duplication                                                 │
│ - Zero test coverage                                                │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ YAML read/write
                                    │ AtomicFileWriter pattern
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Data Stores (Mixed Scope)                       │
├─────────────────────────────────────────────────────────────────────┤
│ Global Stores                     Pack-Scoped Stores                │
│ - UserProgressStore               - PackProgressStore               │
│ - DailyStatsStore                 - PackMasteryStore                │
│ - VerbDrillStore                  - PackSessionStore                │
│                                   - PackVocabDrillStore             │
│                                   - PackVerbDrillStore              │
└─────────────────────────────────────────────────────────────────────┘
```

**Key Problems:**
- No separation between business logic and UI concerns
- State mutated from multiple locations
- Direct store access from ViewModels (no repository abstraction)
- Business rules embedded in UI callbacks
- No test coverage for critical paths

---

## Target Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                           Compose UI Layer                           │
├─────────────────────────────────────────────────────────────────────┤
│ Screens (TrainingScreen, VerbDrillScreen, etc.)                     │
│ - Pure Compose functions (stateless where possible)                 │
│ - UI state only (loading, error, navigation)                        │
│ - No business logic                                                 │
│                                                                      │
│ ViewModels (thin orchestrators)                                     │
│ - TrainingSessionViewModel (~200 lines)                             │
│ - VerbDrillViewModel (~300 lines, no god object)                    │
│ - DailyPracticeViewModel (~200 lines)                               │
│ - UI state management only                                          │
│ - Delegate to use cases                                             │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Use Case invocations
                                    │ Request/Response models
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         Application Layer                            │
├─────────────────────────────────────────────────────────────────────┤
│ Use Cases (interactors)                                             │
│ - StartSessionUseCase                                               │
│ - SubmitAnswerUseCase                                               │
│ - UpdateMasteryUseCase                                              │
│ - ComposeDailyPracticeUseCase                                       │
│ - HandleBossBattleUseCase                                           │
│ - (15-20 use cases total)                                           │
│                                                                      │
│ Orchestration Only (no business rules)                              │
│ - Coordinate repositories                                           │
│ - Emit UI state flows                                                │
│ - Handle error scenarios                                             │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Repository interfaces
                                    │ Domain model queries
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Domain Layer (Pure Kotlin)                        │
├─────────────────────────────────────────────────────────────────────┤
│ Domain Models (immutable data classes)                              │
│ - Session, Card, Answer, Mastery, Progress                          │
│ - Pack, GrammarPoint, VocabItem, VerbItem                           │
│ - DailyPracticeSession, BossBattle, StoryChapter                    │
│                                                                      │
│ Business Rules (pure functions, testable without Android)           │
│ - calculateNextReview(interval, success)                            │
│ - isCardLearned(masteryStep)                                         │
│ - shouldIncludeInDailyPractice(progress, stats)                     │
│ - composeSessionBlocks(grammar, vocab, verb)                        │
│ - determineBossTrigger(sessionCount, stats)                         │
│ - calculateScoreChange(answer, difficulty)                          │
│ - (30-40 pure functions)                                            │
│                                                                      │
│ Domain Services (stateless operations)                              │
│ - MasteryCalculator                                                 │
│ - DailyPracticeComposer                                             │
│ - SessionScheduler                                                  │
│ - BossBattleTrigger                                                 │
│ - CardSelector                                                      │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Repository interface contracts
                                    │ Data source abstraction
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       Infrastructure Layer                           │
├─────────────────────────────────────────────────────────────────────┤
│ Repository Implementations                                          │
│ - SessionRepository (YAML-based)                                    │
│ - ProgressRepository (pack-scoped)                                  │
│ - MasteryRepository (pack-scoped)                                   │
│ - DailyStatsRepository                                              │
│ - VerbDrillRepository                                               │
│                                                                      │
│ Data Sources                                                        │
│ - PackScopedStore<T> (generic pack-isolated storage)                │
│ - LegacyStoreAdapter (migration compatibility)                      │
│ - YAML parsers (CsvParser, YamlParser)                              │
│                                                                      │
│ Persistence Strategy                                                │
│ - Single PackScopedStore implementation                             │
│ - AtomicFileWriter (temp → fsync → rename)                          │
│ - Migration scripts (Phase 0)                                       │
└─────────────────────────────────────────────────────────────────────┘
```

**Key Improvements:**
- **Pure Kotlin domain layer**: All business rules testable without Android
- **Repository pattern**: Data access abstracted behind interfaces
- **Use cases**: Orchestration layer coordinates repositories
- **Thin ViewModels**: UI state only, no business logic
- **Dependency inversion**: Domain defines interfaces, infrastructure implements

---

## Architectural Principles

### 1. Behavioral Preservation (NON-NEGOTIABLE)
- **DO NOT change any user-visible behavior**
- All existing features must work identically after refactoring
- Use existing behavior as test oracles (regression tests first)
- If behavior differs, it's a bug

### 2. Pure Kotlin Business Logic
- Extract all business rules to pure Kotlin functions
- No Android dependencies in domain layer
- Testable with JVM unit tests (no Android emulator needed)
- Domain models are immutable data classes

### 3. Separation of Concerns
- **Domain**: Business rules (no Android, no UI)
- **Application**: Orchestration (use cases, repositories)
- **Infrastructure**: Data access (YAML, file system)
- **UI**: Compose screens and state management only

### 4. Dependency Inversion
- Domain layer defines repository interfaces
- Infrastructure layer implements them
- UI depends on application layer, not infrastructure
- Use cases depend on abstractions, not concretions

### 5. Test First, Refactor Second
- Add regression tests before changing code
- Unit tests for business logic (pure Kotlin)
- Integration tests for repositories (real YAML files)
- UI clickable tests for critical user flows

### 6. Incremental Migration
- Small PRs (max 500 lines changed)
- No big bang rewrites
- Each PR must pass all tests
- Continuous deployment throughout migration

---

## Domain Layer (Pure Kotlin)

### Domain Models

**Core Models:**
```kotlin
// Session models
data class Session(
    val id: SessionId,
    val packId: PackId,
    val cards: List<Card>,
    val currentIndex: Int,
    val startTime: Instant,
    val endTime: Instant?
)

data class Card(
    val id: CardId,
    val grammarPointId: GrammarPointId,
    val prompt: TextString,
    val correctAnswers: List<Answer>,
    val difficulty: Difficulty
)

data class Answer(
    val text: String,
    val isCorrect: Boolean,
    val timestamp: Instant
)

// Mastery models
data class Mastery(
    val itemId: ItemId,
    val step: Int,  // 0-9 on INTERVAL_LADDER_DAYS
    val lastReviewed: Instant,
    val nextReview: Instant,
    val totalReviews: Int,
    val correctReviews: Int
)

// Progress models
data class Progress(
    val packId: PackId,
    val totalItems: Int,
    val learnedItems: Int,  // step >= 3
    val masteredItems: Int, // step == 9
    val lastSessionDate: LocalDate
)
```

**Special Mode Models:**
```kotlin
data class BossBattle(
    val sessionCount: Int,
    val difficulty: BossDifficulty,
    val cards: List<Card>,
    val threshold: Int  // correct answers needed
)

data class DailyPracticeSession(
    val date: LocalDate,
    val blocks: List<SessionBlock>,
    val totalCards: Int,
    val estimatedDuration: Duration
)

data class SessionBlock(
    val type: SessionType,  // TRANSLATE, VOCAB, VERBS
    val cardCount: Int,
    val timeLimit: Duration?
)
```

### Business Rules (Pure Functions)

**Mastery Calculation:**
```kotlin
// In MasteryCalculator.kt (pure Kotlin, no Android)

fun calculateNextReview(
    currentInterval: Int,
    answerIsCorrect: Boolean
): Int {
    if (!answerIsCorrect) {
        return 0  // Reset to day 0
    }
    return minOf(currentInterval + 1, INTERVAL_LADDER_DAYS.lastIndex)
}

fun isCardLearned(mastery: Mastery): Boolean {
    return mastery.step >= 3
}

fun isCardMastered(mastery: Mastery): Boolean {
    return mastery.step == INTERVAL_LADDER_DAYS.lastIndex
}

fun calculateMasteryStep(
    currentStep: Int,
    answerHistory: List<Boolean>
): Int {
    // Pure business logic for SRS progression
    // Testable without Android
}
```

**Daily Practice Composition:**
```kotlin
// In DailyPracticeComposer.kt (pure Kotlin)

fun composeDailyPracticeSession(
    availableGrammarPoints: List<GrammarPoint>,
    availableVocabItems: List<VocabItem>,
    availableVerbItems: List<VerbItem>,
    targetDuration: Duration,
    userStats: DailyStats
): DailyPracticeSession {
    // Pure business logic for session composition
    // No Android dependencies
    // Testable with JVM unit tests
}

fun shouldIncludeInDailyPractice(
    itemProgress: Progress,
    lastPracticeDate: LocalDate?,
    today: LocalDate
): Boolean {
    // Pure business logic for item selection
}
```

**Session Scoring:**
```kotlin
// In SessionScorer.kt (pure Kotlin)

fun calculateScoreChange(
    answerIsCorrect: Boolean,
    difficulty: Difficulty,
    timeTaken: Duration,
    hintsUsed: Int
): Int {
    // Pure scoring logic
}

fun determineBossTrigger(
    completedSessions: Int,
    recentAccuracy: Double,
    daysSinceLastBoss: Int
): Boolean {
    // Pure business logic for boss battle triggers
}
```

**Card Selection:**
```kotlin
// In CardSelector.kt (pure Kotlin)

fun selectCardsForSession(
    availableCards: List<Card>,
    masteryMap: Map<ItemId, Mastery>,
    sessionSize: Int,
    selectionStrategy: SelectionStrategy
): List<Card> {
    // Pure business logic for card selection
    // Testable without Android
}
```

### Domain Services (Stateless)

```kotlin
// In domain/services/

class MasteryCalculator {
    fun calculateProgress(masteryList: List<Mastery>): Progress
    fun getDueItems(masteryList: List<Mastery>, date: LocalDate): List<ItemId>
}

class DailyPracticeComposer {
    fun composeSession(
        availableItems: AvailableItems,
        targetDuration: Duration,
        userStats: DailyStats
    ): DailyPracticeSession
}

class SessionScheduler {
    fun scheduleNextReview(mastery: Mastery, completedAt: Instant): Instant
    fun isOverdue(mastery: Mastery, now: Instant): Boolean
}

class BossBattleTrigger {
    fun shouldTrigger(sessionCount: Int, stats: DailyStats): Boolean
    fun determineDifficulty(sessionCount: Int): BossDifficulty
}
```

**All domain layer code:**
- Pure Kotlin (no Android imports)
- Testable with JVM unit tests
- Immutable data structures
- Stateless services
- No file I/O, no UI logic

---

## Application Layer (Orchestration)

### Use Cases

**Session Use Cases:**
```kotlin
class StartSessionUseCase(
    private val sessionRepository: SessionRepository,
    private val masteryRepository: MasteryRepository,
    private val cardSelector: CardSelector
) {
    suspend operator fun invoke(
        packId: PackId,
        sessionType: SessionType
    ): Result<Session> {
        // Orchestrate repository calls
        // Emit UI state updates
        // Handle errors
    }
}

class SubmitAnswerUseCase(
    private val sessionRepository: SessionRepository,
    private val masteryRepository: MasteryRepository,
    private val masteryCalculator: MasteryCalculator
) {
    suspend operator fun invoke(
        sessionId: SessionId,
        cardId: CardId,
        answer: String
    ): Result<AnswerResult> {
        // Orchestrate mastery updates
        // Persist progress
        // Return result for UI
    }
}

class NavigateToNextCardUseCase(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(sessionId: SessionId): Result<Card> {
        // Update currentIndex
        // Persist session state
        // Return next card
    }
}
```

**Daily Practice Use Cases:**
```kotlin
class ComposeDailyPracticeUseCase(
    private val progressRepository: ProgressRepository,
    private val dailyStatsRepository: DailyStatsRepository,
    private val dailyPracticeComposer: DailyPracticeComposer
) {
    suspend operator fun invoke(
        packId: PackId,
        targetDuration: Duration
    ): Result<DailyPracticeSession> {
        // Orchestrate composition
        // Return session blocks
    }
}

class StartDailyPracticeUseCase(
    private val composeDailyPractice: ComposeDailyPracticeUseCase,
    private val startSession: StartSessionUseCase
) {
    suspend operator fun invoke(packId: PackId): Result<DailyPracticeSession> {
        // Compose and start daily practice
    }
}
```

**Special Mode Use Cases:**
```kotlin
class HandleBossBattleUseCase(
    private val bossRepository: BossBattleRepository,
    private val bossTrigger: BossBattleTrigger,
    private val startSession: StartSessionUseCase
) {
    suspend operator fun invoke(
        packId: PackId,
        sessionCount: Int
    ): Result<BossBattle> {
        // Determine if boss should trigger
        // Compose boss session
        // Start battle
    }
}
```

### Repository Interfaces (Defined in Domain)

```kotlin
// In domain/repositories/

interface SessionRepository {
    suspend fun loadSession(sessionId: SessionId): Session?
    suspend fun saveSession(session: Session)
    suspend fun deleteSession(sessionId: SessionId)
    fun observeSession(sessionId: SessionId): Flow<Session>
}

interface MasteryRepository {
    suspend fun loadMastery(packId: PackId, itemId: ItemId): Mastery?
    suspend fun saveMastery(packId: PackId, mastery: Mastery)
    suspend fun loadAllMastery(packId: PackId): List<Mastery>
    fun observeMastery(packId: PackId): Flow<List<Mastery>>
}

interface ProgressRepository {
    suspend fun loadProgress(packId: PackId): Progress?
    suspend fun saveProgress(progress: Progress)
    fun observeProgress(packId: PackId): Flow<Progress>
}

interface DailyStatsRepository {
    suspend fun loadStats(date: LocalDate): DailyStats?
    suspend fun saveStats(stats: DailyStats)
    suspend fun loadRecentStats(days: Int): List<DailyStats>
}
```

**Use case characteristics:**
- Orchestration only (no business rules)
- Coordinate multiple repositories
- Emit UI state flows
- Handle error scenarios
- Testable with mock repositories

---

## Infrastructure Layer (Repositories)

### Repository Implementations

```kotlin
// In data/repositories/

class SessionRepositoryImpl(
    private val sessionStore: PackScopedStore<Session>
) : SessionRepository {
    
    override suspend fun loadSession(sessionId: SessionId): Session? {
        return sessionStore.load(sessionId.packId, sessionId.id)
    }
    
    override suspend fun saveSession(session: Session) {
        sessionStore.save(session.packId, session.id, session)
    }
    
    override fun observeSession(sessionId: SessionId): Flow<Session> {
        return sessionStore.observe(sessionId.packId, sessionId.id)
    }
}

class MasteryRepositoryImpl(
    private val masteryStore: PackScopedStore<Map<ItemId, Mastery>>
) : MasteryRepository {
    
    override suspend fun loadMastery(packId: PackId, itemId: ItemId): Mastery? {
        val masteryMap = masteryStore.load(packId, MASTERY_KEY)
        return masteryMap?.get(itemId)
    }
    
    override suspend fun saveMastery(packId: PackId, mastery: Mastery) {
        val masteryMap = masteryStore.load(packId, MASTERY_KEY) ?: emptyMap()
        val updatedMap = masteryMap + (mastery.itemId to mastery)
        masteryStore.save(packId, MASTERY_KEY, updatedMap)
    }
    
    override suspend fun loadAllMastery(packId: PackId): List<Mastery> {
        val masteryMap = masteryStore.load(packId, MASTERY_KEY)
        return masteryMap?.values?.toList() ?: emptyList()
    }
}
```

### Generic Pack-Scoped Store

```kotlin
// In data/store/

class PackScopedStore<T>(
    private val fileSystem: FileSystem,
    private val serializer: YamlSerializer<T>,
    private val atomicWriter: AtomicFileWriter
) {
    suspend fun load(packId: PackId, key: String): T? {
        val file = fileSystem.getFile(packId, key)
        if (!file.exists()) return null
        
        val content = file.readText()
        return serializer.deserialize(content)
    }
    
    suspend fun save(packId: PackId, key: String, value: T) {
        val file = fileSystem.getFile(packId, key)
        val content = serializer.serialize(value)
        atomicWriter.writeAtomically(file, content)
    }
    
    fun observe(packId: PackId, key: String): Flow<T> {
        return fileSystem.observeFile(packId, key)
            .map { serializer.deserialize(it) }
    }
}
```

### YAML Persistence

```kotlin
// In data/persistence/

class YamlSerializer<T>(
    private val clazz: Class<T>
) {
    fun serialize(value: T): String {
        return Yaml().dump(value)
    }
    
    fun deserialize(content: String): T {
        return Yaml().loadAs(content, clazz)
    }
}

class AtomicFileWriter {
    suspend fun writeAtomically(file: File, content: String) {
        val tempFile = File(file.path + ".tmp")
        try {
            tempFile.writeText(content)
            tempFile.getFD().sync()  // fsync
            tempFile.renameTo(file)
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }
}
```

**Infrastructure characteristics:**
- Implements domain repository interfaces
- Handles YAML parsing and file I/O
- Uses AtomicFileWriter for safe persistence
- No business logic (only data access)

---

## UI Layer (Compose)

### ViewModels (Thin Orchestrators)

```kotlin
@HiltViewModel
class TrainingSessionViewModel @Inject constructor(
    private val startSession: StartSessionUseCase,
    private val submitAnswer: SubmitAnswerUseCase,
    private val navigateToNextCard: NavigateToNextCardUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<TrainingUiState>(TrainingUiState.Loading)
    val uiState: StateFlow<TrainingUiState> = _uiState.asStateFlow()
    
    fun startSession(packId: PackId, sessionType: SessionType) {
        viewModelScope.launch {
            _uiState.value = TrainingUiState.Loading
            startSession(packId, sessionType)
                .onSuccess { session -> _uiState.value = TrainingUiState.Ready(session) }
                .onFailure { error -> _uiState.value = TrainingUiState.Error(error) }
        }
    }
    
    fun submitAnswer(answer: String) {
        viewModelScope.launch {
            val currentState = _uiState.value as? TrainingUiState.Active ?: return
            submitAnswer(currentState.sessionId, currentState.currentCard.id, answer)
                .onSuccess { result -> _uiState.value = currentState.withAnswerResult(result) }
                .onFailure { error -> _uiState.value = TrainingUiState.Error(error) }
        }
    }
    
    fun goToNextCard() {
        viewModelScope.launch {
            val currentState = _uiState.value as? TrainingUiState.Active ?: return
            navigateToNextCard(currentState.sessionId)
                .onSuccess { card -> _uiState.value = currentState.withNextCard(card) }
                .onFailure { error -> _uiState.value = TrainingUiState.Error(error) }
        }
    }
}

// ViewModel size: ~200 lines (vs 1,570 currently)
```

### Compose Screens (Stateless Where Possible)

```kotlin
@Composable
fun TrainingScreen(
    viewModel: TrainingSessionViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    when (uiState) {
        is TrainingUiState.Loading -> LoadingIndicator()
        is TrainingUiState.Ready -> SessionContent(
            session = (uiState as TrainingUiState.Ready).session,
            onSubmitAnswer = viewModel::submitAnswer,
            onNavigateNext = viewModel::goToNextCard
        )
        is TrainingUiState.Error -> ErrorDisplay(
            error = (uiState as TrainingUiState.Error).error,
            onRetry = { viewModel.retry() }
        )
    }
}

@Composable
private fun SessionContent(
    session: Session,
    onSubmitAnswer: (String) -> Unit,
    onNavigateNext: () -> Unit
) {
    val currentCard = session.cards[session.currentIndex]
    
    Column {
        CardPrompt(card = currentCard)
        AnswerInput(
            onSubmit = onSubmitAnswer
        )
        NavigationButtons(
            onNavigateNext = onNavigateNext
        )
    }
}
```

**UI layer characteristics:**
- ViewModels are thin orchestrators (~200 lines each)
- No business logic in Compose functions
- UI state management only
- Delegate to use cases for all operations

---

## Business Rules to Extract

### From TrainingViewModel (1,570 lines → pure functions)

**Extract to MasteryCalculator:**
- `calculateNextInterval()`: SRS interval progression
- `isCardLearned()`: Mastery step >= 3 check
- `calculateProgress()`: Pack progress percentage
- `getDueItems()`: Filter items due for review

**Extract to SessionScheduler:**
- `scheduleNextReview()`: Calculate next review date
- `isOverdue()`: Check if review is due
- `updateMastery()`: Apply mastery step changes

**Extract to CardSelector:**
- `selectCardsForSession()`: Filter and shuffle cards
- `applyDifficultyFilter()`: Select by difficulty
- `balanceItemTypes()`: Ensure variety

### From ProgressTracker (487 lines → pure functions)

**Extract to MasteryCalculator:**
- `calculateMasteryStep()`: Determine new mastery step
- `calculateAccuracy()`: Correct reviews / total reviews
- `isLearningStreak()`: Consecutive days threshold

**Extract to ProgressAggregator:**
- `aggregatePackProgress()`: Summarize pack statistics
- `getLearnedItemCount()`: Count items with step >= 3
- `getMasteredItemCount()`: Count items with step == 9

### From DailyPracticeCoordinator (970 lines → pure functions)

**Extract to DailyPracticeComposer:**
- `composeSessionBlocks()`: Create TRANSLATE/VOCAB/VERBS blocks
- `calculateBlockDuration()`: Estimate time per block
- `selectItemsForBlock()`: Choose items by type

**Extract to BossBattleTrigger:**
- `shouldTriggerBoss()`: Determine if boss appears
- `calculateBossDifficulty()`: Scale difficulty with progress
- `composeBossSession()`: Select boss cards

### From Special Modes (scattered logic)

**Extract to BossBattleService:**
- `calculateBossThreshold()`: Correct answers needed
- `determineBossReward()`: XP/mastery bonuses
- `handleBossDefeat()`: Apply loss consequences

**Extract to StoryModeService:**
- `unlockNextChapter()`: Chapter progression logic
- `calculateStoryProgress()`: Chapter completion percentage

**Extract to PomodoroService:**
- `calculatePomodoroDuration()`: Session time limits
- `handlePomodoroBreak()`: Break period logic

**Total extraction: ~30-40 pure functions**
**All testable without Android dependencies**

---

## Test Strategy

### Phase 0: Safety Tests (Before Refactoring)

**Regression Tests (UI Clickable):**
- All existing 7 UI tests must pass
- Add missing critical path tests:
  - Daily practice full flow
  - Boss battle full flow
  - Verb drill session card actions
  - Progress persistence

**Test-Only Production Code Removal:**
- Remove test-only utilities from main codebase
- Move to `test/` or `androidTest/` directories

**Test Coverage Goals:**
- Current: ~10% (UI tests only)
- Phase 0 target: ~30% (add missing UI tests)
- Final target: ~80% (unit + integration + UI)

### Phase 1: Unit Tests (Pure Kotlin)

**Domain Layer Tests:**
```kotlin
// Example: MasteryCalculatorTest
class MasteryCalculatorTest {
    @Test
    fun `calculateNextReview resets to 0 on incorrect answer`() {
        val result = calculateNextReview(currentInterval = 5, answerIsCorrect = false)
        assertEquals(0, result)
    }
    
    @Test
    fun `calculateNextReview increments on correct answer`() {
        val result = calculateNextReview(currentInterval = 5, answerIsCorrect = true)
        assertEquals(6, result)
    }
    
    @Test
    fun `isCardLearned returns true when step >= 3`() {
        val mastery = Mastery(itemId = "test", step = 3, ...)
        assertTrue(isCardLearned(mastery))
    }
}
```

**Test Characteristics:**
- Pure JVM tests (no Android emulator)
- Fast execution (milliseconds)
- High coverage of business rules
- Test edge cases and error scenarios

**Coverage Targets:**
- Domain layer: 90%+
- Application layer: 80%+
- Infrastructure layer: 70%+

### Phase 2: Integration Tests

**Repository Integration Tests:**
```kotlin
// Example: SessionRepositoryIntegrationTest
@RunWith(AndroidJUnit4::class)
class SessionRepositoryIntegrationTest {
    private lateinit var repository: SessionRepository
    private lateinit var tempDir: File
    
    @Before
    fun setup() {
        tempDir = createTempDir()
        repository = SessionRepositoryImpl(
            PackScopedStore(..., tempDir)
        )
    }
    
    @Test
    fun `save and load session persists data correctly`() {
        val session = createTestSession()
        repository.saveSession(session)
        
        val loaded = repository.loadSession(session.id)
        assertEquals(session, loaded)
    }
    
    @Test
    fun `concurrent writes do not corrupt data`() {
        // Test atomicity
    }
}
```

**Test Characteristics:**
- Real YAML file I/O
- Test repository implementations
- Verify AtomicFileWriter correctness
- Test migration scripts

### Phase 3: UI Tests (Clickable)

**ViewModel UI Tests:**
```kotlin
// Example: TrainingSessionViewModelTest
class TrainingSessionViewModelTest {
    @Test
    fun `submitAnswer updates UI state correctly`() {
        val viewModel = TrainingSessionViewModel(
            mockSubmitAnswerUseCase
        )
        
        viewModel.submitAnswer("test answer")
        
        val state = viewModel.uiState.value
        assertTrue(state is TrainingUiState.ShowingResult)
    }
}
```

**UI Clickable Tests:**
- All existing tests must pass
- Add tests for new use cases
- Test error scenarios

### Test Execution Strategy

**Continuous Testing:**
- Run unit tests on every commit
- Run integration tests before merging
- Run UI tests on PRs

**Test Performance:**
- Unit tests: < 10 seconds
- Integration tests: < 30 seconds
- UI tests: < 2 minutes

**Test Maintenance:**
- Review flaky tests weekly
- Update tests with refactoring
- Keep tests independent

---

## Migration Phases

### Phase 0: Safety Tests (2-3 weeks)

**Goal:** Add regression tests before refactoring

**Tasks:**
1. Add missing UI clickable tests
   - Daily practice full flow
   - Boss battle full flow
   - Verb drill session card actions
2. Remove test-only production code
3. Add repository integration test skeleton
4. Document all existing behavior

**Deliverables:**
- 10+ new UI clickable tests
- Test-only code removed
- Behavior documentation

**Risks:**
- Low (tests only, no code changes)

### Phase 1: Low-Risk Extraction (4-6 weeks)

**Goal:** Extract business logic to pure Kotlin

**Tasks:**
1. Create domain layer structure
   - `domain/model/`
   - `domain/service/`
   - `domain/repository/` (interfaces)
2. Extract mastery calculation logic
   - `MasteryCalculator` (pure functions)
   - Unit tests for mastery rules
3. Extract progress aggregation logic
   - `ProgressAggregator` (pure functions)
   - Unit tests for progress rules
4. Extract card selection logic
   - `CardSelector` (pure functions)
   - Unit tests for selection rules

**Deliverables:**
- Domain layer with 15+ pure functions
- 50+ unit tests (passing)
- No behavior changes

**Risks:**
- Low (pure extraction, tests first)

### Phase 2: Medium-Risk Orchestration (6-8 weeks)

**Goal:** Create application layer with use cases

**Tasks:**
1. Create repository interfaces in domain
   - `SessionRepository`
   - `MasteryRepository`
   - `ProgressRepository`
2. Implement repository adapters
   - Wrap existing stores
   - Add integration tests
3. Create use cases
   - `StartSessionUseCase`
   - `SubmitAnswerUseCase`
   - `UpdateMasteryUseCase`
4. Refactor ViewModels to use use cases
   - Remove business logic from ViewModels
   - Add ViewModel unit tests

**Deliverables:**
- Application layer with 10+ use cases
- Repository layer with 4+ implementations
- ViewModel size reduced by 50%+
- 30+ integration tests (passing)

**Risks:**
- Medium (touches ViewModels, requires careful testing)

### Phase 3: Infrastructure Isolation (4-6 weeks)

**Goal:** Consolidate data layer and remove dual storage

**Tasks:**
1. Create generic `PackScopedStore<T>`
   - Replace 6+ store implementations
   - Add integration tests
2. Implement migration scripts
   - Migrate legacy global stores to pack-scoped
   - Test rollback procedures
3. Remove dual storage pattern
   - Delete legacy stores
   - Update all repository implementations
4. Consolidate special mode storage
   - Boss, Elite, Story use pack-scoped stores
   - Remove state duplication

**Deliverables:**
- Single pack-scoped store implementation
- Migration scripts (tested)
- Dual storage removed
- 20+ integration tests (passing)

**Risks:**
- Medium (data migration, requires backups)

### Phase 4: Cleanup (2-3 weeks)

**Goal:** Remove dead code and finalize architecture

**Tasks:**
1. Remove deprecated code
   - Legacy store implementations
   - Dead navigation routes
   - Unused helper classes
2. Finalize god object decomposition
   - TrainingViewModel → ~200 lines
   - VerbDrillViewModel → ~300 lines
   - DailyPracticeCoordinator → deleted
3. Update documentation
   - Architecture diagrams
   - API documentation
   - Migration guide
4. Performance tuning
   - Optimize repository queries
   - Reduce UI recomposition

**Deliverables:**
- Clean architecture (no god objects)
- Updated documentation
- Performance benchmarks

**Risks:**
- Low (cleanup only, all tests passing)

---

## Non-Goals

### Out of Scope for This Migration

1. **New Features**
   - No new user-facing features
   - No UI redesigns
   - No new practice modes

2. **Performance Optimization**
   - No algorithm changes
   - No data structure changes
   - Only cleanup-level optimizations

3. **Platform Changes**
   - No iOS port
   - No web version
   - No backend API

4. **Library Upgrades**
   - No Kotlin version changes
   - No Compose BOM upgrades
   - Only critical security fixes

5. **Behavioral Changes**
   - NO changes to user-visible behavior
   - NO changes to SRS algorithm
   - NO changes to scoring logic

**Rationale:** Focus on architectural health without scope creep

---

## Risks and Mitigations

### High Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Data loss during migration** | Critical | - Full backups before Phase 3<br>- Test migration on staging data<br>- Rollback procedures documented<br>- AtomicFileWriter prevents corruption |
| **Test coverage gaps** | High | - Phase 0 adds regression tests<br>- Code review for test coverage<br>- Mutation testing for critical paths<br>- Manual QA after each phase |
| **Behavioral regressions** | High | - Regression tests before refactoring<br.- A/B testing during migration<br.- Canary deployments<br.- Rapid rollback capability |

### Medium Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Breaking existing PRs** | Medium | - Coordinate with team<br>- Migration branch strategy<br.- Update PR templates<br.- Communication plan |
| **Performance degradation** | Medium | - Benchmark before/after<br.- Profile critical paths<br.- Optimize hot spots<br.- Monitor in production |
| **Incomplete migration** | Medium | - Clear phase criteria<br.- Definition of done<br.- Architecture decision records<br.- Regular architecture reviews |

### Low Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Learning curve** | Low | - Pair programming<br.- Documentation<br.- Training sessions |
| **Git conflicts** | Low | - Small, frequent PRs<br.- Clear ownership<br.- Conflict resolution procedures |

---

## Open Questions

1. **Repository Granularity**
   - Q: Should we have separate repositories per domain or a generic `PackScopedRepository<T>`?
   - A: Start with separate repositories for clarity, consider generic later

2. **State Management**
   - Q: Should we use StateFlow or Kotlin Flow in repositories?
   - A: StateFlow for UI state, Flow for data streams

3. **Error Handling**
   - Q: Should we use Result<T>, exceptions, or sealed classes?
   - A: Result<T> for use cases, exceptions for infrastructure

4. **Dependency Injection**
   - Q: Hilt or manual DI?
   - A: Keep Hilt (already in use), add testing modules

5. **Coroutines vs RxJava**
   - Q: Migrate from coroutines to RxJava or stay with coroutines?
   - A: Stay with coroutines (team familiarity)

6. **YAML vs JSON**
   - Q: Keep YAML or migrate to JSON?
   - A: Keep YAML (existing data format, migration cost too high)

7. **Test Data Strategy**
   - Q: How to manage test data for integration tests?
   - A: Fixtures in `test/fixtures/`, reset before each test

---

## Success Criteria

### Phase Completion Criteria

**Phase 0 (Safety Tests):**
- [ ] All existing UI tests passing
- [ ] 10+ new UI tests added
- [ ] Test-only production code removed
- [ ] Behavior documentation complete

**Phase 1 (Domain Extraction):**
- [ ] Domain layer created with 15+ pure functions
- [ ] 50+ unit tests passing (JVM, no Android)
- [ ] All existing tests still passing
- [ ] No behavioral changes detected

**Phase 2 (Application Layer):**
- [ ] 10+ use cases implemented
- [ ] Repository interfaces defined in domain
- [ ] ViewModel size reduced by 50%+
- [ ] 30+ integration tests passing

**Phase 3 (Infrastructure):**
- [ ] Single PackScopedStore implementation
- [ ] Migration scripts tested and documented
- [ ] Dual storage pattern removed
- [ ] 20+ integration tests passing

**Phase 4 (Cleanup):**
- [ ] No god objects (max 300 lines per class)
- [ ] Dead code removed
- [ ] Documentation updated
- [ ] Performance benchmarks met

### Overall Success Metrics

**Code Quality:**
- Max 300 lines per class
- 80%+ test coverage
- Zero test-only production code
- No circular dependencies

**Architecture:**
- Clear layer separation
- Dependency inversion followed
- Pure Kotlin domain layer
- Repository pattern implemented

**Maintainability:**
- Onboarding time < 1 day for new developers
- PR review time < 30 minutes
- Bug fix time < 2 hours

**Performance:**
- App startup time: < 2 seconds
- Session load time: < 500ms
- Persistence time: < 100ms per operation

---

## References

### Wave 1: Architecture Maps
- W1-A1: UI/Navigation Current Map
- W1-A2: Training Core Current Map
- W1-A3: Progress Current Map
- W1-A4: Daily Practice Current Map
- W1-A5: Verb/Vocab Drill Current Map
- W1-A6: Special Modes Current Map
- W1-A7: Data Infrastructure Current Map
- W1-A8: Tests Current Map
- W1-A9: Dependencies/Dead Code Current Map

### Wave 2: Risk Analyses
- W2-A1: UI/Navigation Risks (9 risks)
- W2-A2: Training Core Risks (18 risks)
- W2-A3: Progress Risks (12 risks)
- W2-A4: Daily Practice Risks (15 risks)
- W2-A5: Verb/Vocab Drill Risks (12 risks)
- W2-A6: Special Modes Risks (12 risks)
- W2-A7: Data Infrastructure Risks (12 risks)
- W2-A8: Tests Risks (12 risks)
- W2-A9: Dependencies/Dead Code Risks (19 risks)

### Specification Documents
- `docs/specification/01-models-and-state.md`
- `docs/specification/02-data-stores.md`
- `docs/specification/08-training-viewmodel.md`
- `docs/specification/scenarios/*.md`

### External References
- Clean Architecture by Robert C. Martin
- Domain-Driven Design by Eric Evans
- Android Architecture Blueprints
- Jetpack Compose Best Practices

---

**Next Step:** Wave 4: Synthesis and Roadmap
