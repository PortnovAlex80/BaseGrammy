# Wave 1 Agent A3: Progress Architecture Map

**Generated:** 2026-05-22
**Scope:** Progress Tracking (ProgressTracker, Mastery, SRS, Cursor)
**Status:** FACTUAL MAP ONLY - NO CODE CHANGES

---

## Active User Flows

### 1. Lesson Training Mode (Voice/Keyboard)
**Flow:** User practices lesson cards → cards marked shown → mastery increases → flower grows

**Progress Updates:**
- `recordCardShowForMastery()` called when card is shown (ProgressTracker.kt:53-78)
- Skipped if `bossActive` or `inputMode == WORD_BANK` (lines 61-72)
- Only VOICE and KEYBOARD modes count for mastery/flower growth (line 69-72)
- WORD_BANK mode never counts for skill formation (line 69)

**When Card is Marked Shown:**
- Called from `TrainingViewModel.recordCardShowForMastery()` (TrainingViewModel.kt:1210-1217)
- Triggered on card navigation (TrainingViewModel.kt:452) and session events (TrainingViewModel.kt:1310)
- Updates `MasteryStore.recordCardShow()` (MasteryStore.kt:154-198)

### 2. Word Bank Mode
**Flow:** User practices with word bank → cards tracked but NOT counted for mastery

**Progress Updates:**
- `markSubLessonCardsShown()` for batch marking (ProgressTracker.kt:85-102)
- Only operates when `inputMode == WORD_BANK` (line 92)
- Updates `shownCardIds` but NOT `uniqueCardShows`/`totalCardShows` (MasteryStore.kt:203-218)

### 3. Daily Practice Mode
**Flow:** User completes daily 3-block session → cursor advances → mastery updated

**Progress Updates:**
- Cards marked shown via `DailyPracticeCoordinator` (DailyPracticeCoordinator.kt:557)
- Cursor advances via `advanceCursor()` (ProgressTracker.kt:345-365)
- First session card IDs stored for repeat capability (ProgressTracker.kt:373-384)

### 4. Boss Battle Mode
**Flow:** Boss battles → NO mastery progress recorded

**Progress Updates:**
- Boss battles skipped in `recordCardShowForMastery()` (ProgressTracker.kt:61-62)
- Exception: ELITE boss saves progress (ProgressTracker.kt:274-276)

### 5. Lesson Completion
**Flow:** User completes 15 sub-lessons → lesson marked completed → boss unlocked

**Progress Updates:**
- `checkAndMarkLessonCompleted()` when `completedSubLessonCount >= 15` (ProgressTracker.kt:110-119)
- Updates `LessonMasteryState.completedAtMs` (MasteryStore.kt:223-236)

---

## Main Classes and Responsibilities

### ProgressTracker
**File:** `feature/progress/ProgressTracker.kt`
**Lines:** 1-487

**What It Tracks:**
- Single source of truth for all mastery tracking and progress persistence (lines 25-34)
- Wrapper around MasteryStore + ProgressStore + PackLessonProgressStore (lines 35-42)
- **READER** of state (takes values as parameters) and **WRITER** to data stores (lines 32-34)
- Does NOT directly update TrainingUiState (line 33)

**Key Methods:**
- `recordCardShowForMastery()` - Records card show for mastery tracking (lines 53-78)
- `markSubLessonCardsShown()` - Batch marking for WORD_BANK mode (lines 85-102)
- `calculateCompletedSubLessons()` - Counts completed sub-lessons (lines 128-156)
- `checkAndMarkLessonCompleted()` - Marks lesson completed at 15 sub-lessons (lines 110-119)
- `saveProgress()` - Serializes state to stores (lines 269-334)
- `advanceCursor()` - Advances daily cursor after session (lines 345-365)
- `storeFirstSessionCardIds()` - Stores first session for repeat (lines 373-384)
- `resetStores()` - Clears all progress data (lines 395-403)

**Critical Gotchas:**
- WORD_BANK mode never counts for mastery (line 69)
- Boss battles don't count for mastery (line 61-62)
- Only VOICE and KEYBOARD grow flowers (lines 68-72)

---

### MasteryStore
**File:** `data/stores/MasteryStore.kt`
**Lines:** 1-340

**Data Structures:**
```kotlin
// LessonMasteryState (Models.kt:180-190)
data class LessonMasteryState(
    val lessonId: LessonId,
    val languageId: LanguageId,
    val uniqueCardShows: Int = 0,        // Unique cards shown (counts toward mastery)
    val totalCardShows: Int = 0,         // Total card shows (including repeats)
    val lastShowDateMs: Long = 0L,       // Timestamp of last show
    val intervalStepIndex: Int = 0,      // SRS ladder step (0-9)
    val completedAtMs: Long? = null,     // When lesson was completed
    val shownCardIds: Set<String> = emptySet(),  // All cards ever shown
    val cardEncounterCounts: Map<String, Int> = emptyMap()  // Per-card encounter count
)
```

**Key Methods:**
- `loadAll()` - Load all mastery states (lines 58-123)
- `get(lessonId, languageId)` - Get specific lesson mastery (lines 128-131)
- `save(state)` - Save mastery state (lines 136-145)
- `recordCardShow()` - Record card show and update SRS (lines 154-198)
- `markCardsShownForProgress()` - Mark cards shown without mastery metrics (lines 203-218)
- `markLessonCompleted()` - Mark lesson as completed (lines 223-236)
- `recordCardEncounter()` - Record per-card encounter count (lines 251-268)
- `getCardEncounterCount()` - Get encounter count for card (lines 273-276)
- `clear()` - Clear all mastery data (lines 281-287)
- `clearLanguage(languageId)` - Clear specific language (lines 292-296)

**Storage Format:**
- File: `grammarmate/mastery.yaml` (line 46)
- Format: YAML with schema version (lines 332-338)
- Structure: `Map<languageId, Map<lessonId, LessonMasteryState>>` (line 56)

**SRS Integration:**
- Calculates `intervalStepIndex` based on days since last show (lines 164-176)
- Uses `SpacedRepetitionConfig.nextIntervalStep()` (line 173)
- Uses `SpacedRepetitionConfig.wasRepetitionOnTime()` (line 171)

**Mastery Thresholds:**
- MASTERY_THRESHOLD = 150 unique card shows (SpacedRepetitionConfig.kt:31)
- LEARNED_THRESHOLD = 3 (TrainingConfig.kt:13) - NOTE: This appears to be for lesson completion detection
- BOSS_UNLOCK_SUB_LESSONS = 15 (TrainingConfig.kt:8) - Sub-lessons needed to unlock boss

---

### ProgressStore
**File:** `data/ProgressStore.kt`
**Lines:** 1-309

**Data Structures:**
```kotlin
// TrainingProgress (from Models.kt, referenced in ProgressStore)
data class TrainingProgress(
    val languageId: LanguageId,
    val mode: TrainingMode,
    val lessonId: String?,  // Legacy field - no longer used
    val currentIndex: Int,  // Legacy field - no longer used
    val correctCount: Int,  // Legacy field - no longer used
    val incorrectCount: Int,  // Legacy field - no longer used
    val incorrectAttemptsForCard: Int,  // Legacy field - no longer used
    val activeTimeMs: Long,  // Legacy field - no longer used
    val state: SessionState,
    val bossLessonRewards: Map<LessonId, String>,  // Boss battle rewards
    val bossMegaReward: String?,
    val bossMegaRewards: Map<LessonId, String>,
    val voiceActiveMs: Long,
    val voiceWordCount: Int,
    val hintCount: Int,
    val eliteStepIndex: Int,
    val eliteBestSpeeds: List<Double>,
    val currentScreen: String,
    val activePackId: PackId?,
    val dailyLevel: Int,
    val dailyTaskIndex: Int,
    val dailyCursor: DailyCursorState  // Global cursor (legacy, being migrated)
)
```

**Key Methods:**
- `load()` - Load training progress (lines 53-100)
- `save(progress)` - Save training progress (lines 102-145)
- `clear()` - Clear progress data (lines 147-149)
- `migrateGlobalDailyCursorToPackScoped()` - Migrate cursor to pack-scoped (lines 151-206)
- `migrateGlobalLessonProgressToPackScoped()` - Migrate lesson progress to pack-scoped (lines 208-307)

**Storage Format:**
- File: `grammarmate/progress.yaml` (line 49)
- Format: YAML with schema version (lines 128-131)
- Migration: Supports migration from global to pack-scoped storage (lines 151-307)

**Phase 5 Changes (Wave 3.1):**
- Lesson progress fields migrated to `PackLessonProgressStore` (ProgressTracker.kt:260-298)
- Remaining fields (mode, elite, daily, etc.) stay in global ProgressStore (ProgressTracker.kt:300-326)

---

### PackLessonProgressStore
**File:** `data/PackLessonProgressStore.kt`
**Lines:** 1-235

**Data Structures:**
```kotlin
// PackLessonProgressState (Models.kt, referenced in store)
data class PackLessonProgressState(
    val packId: String,
    val lessonProgress: Map<LessonId, LessonProgress>
)

data class LessonProgress(
    val currentIndex: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val incorrectAttemptsForCard: Int,
    val activeTimeMs: Long,
    val state: SessionState
)
```

**Key Methods:**
- `loadPackProgress(packId)` - Load progress for specific pack (lines 59-87)
- `savePackProgress(progress)` - Save progress for specific pack (lines 89-122)
- `deletePackProgress(packId)` - Delete progress for specific pack (lines 124-134)
- `loadAllPackProgress()` - Load all pack progress states (lines 136-165)
- `flush()` - No-op for API compatibility (lines 167-170)
- `migrateGlobalToPackScoped()` - Migrate global progress to pack-scoped (lines 193-234)

**Storage Format:**
- File pattern: `grammarmate/lesson_progress_{packId}.yaml` (line 56)
- Example: `lesson_progress_ru-en-v1.yaml`
- Format: YAML with schema version (lines 93-106)
- Per-pack isolation prevents cross-pack contamination (line 13)

**Purpose:**
- TASK-081: Lesson progress isolation bug fix (line 15)
- Follows PackDailyCursorStore pattern for consistency (line 16)

---

## Business Rules Found

### Mastery Progress Rules

**1. Card Show Recording**
- **When:** Card is shown in training session
- **Where:** `ProgressTracker.recordCardShowForMastery()` (ProgressTracker.kt:53-78)
- **Conditions:**
  - NOT bossActive (line 62)
  - NOT WORD_BANK mode (line 69)
  - Only VOICE and KEYBOARD count (lines 68-72)
- **Updates:**
  - `uniqueCardShows++` if card not shown before (MasteryStore.kt:181-184)
  - `totalCardShows++` always (MasteryStore.kt:186)
  - `lastShowDateMs = now` (MasteryStore.kt:187)
  - `intervalStepIndex` updated via SRS (MasteryStore.kt:172-176)

**2. WORD_BANK Mode Exception**
- **Rule:** WORD_BANK never counts for mastery/flower growth
- **Evidence:** ProgressTracker.kt:69-72
- **Reasoning:** Word bank is for learning exposure, not skill formation
- **Tracking:** Cards still marked in `shownCardIds` for progress tracking (MasteryStore.kt:203-218)

**3. Boss Battle Exception**
- **Rule:** Boss battles don't count for mastery
- **Evidence:** ProgressTracker.kt:61-62
- **Exception:** ELITE boss saves progress (ProgressTracker.kt:274-276)

### SRS (Spaced Repetition System)

**1. Interval Ladder**
- **Definition:** `SpacedRepetitionConfig.INTERVAL_LADDER_DAYS` (SpacedRepetitionConfig.kt:26)
- **Values:** `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]` days
- **Purpose:** Expected intervals between repetitions to maintain memory

**2. SRS Calculation**
- **When:** Every `recordCardShow()` call (MasteryStore.kt:154-198)
- **Formula:** `R = e^(-t/S)` (SpacedRepetitionConfig.kt:9)
  - R = retention probability
  - t = time since last repetition
  - S = memory stability
- **Stability Calculation:** `S = S0 * multiplier^step` (SpacedRepetitionConfig.kt:68-74)
  - BASE_STABILITY_DAYS = 0.9 (line 50)
  - STABILITY_MULTIPLIER = 2.2 (line 57)

**3. Interval Step Advancement**
- **When:** Card is shown again
- **Where:** `SpacedRepetitionConfig.nextIntervalStep()` (SpacedRepetitionConfig.kt:144-152)
- **Logic:**
  - If `wasRepetitionOnTime` → advance to next step (line 147)
  - Else → stay on current step (line 150)
- **On-Time Definition:** `daysSinceLastShow <= expectedInterval` (SpacedRepetitionConfig.kt:161-170)

**4. Health Calculation**
- **Formula:** `health = 0.5 + 0.5 * e^(-overdue/stability)` (SpacedRepetitionConfig.kt:131)
- **Range:** 50% (WILTED_THRESHOLD) to 100% (SpacedRepetitionConfig.kt:36)
- **Gone Threshold:** 90 days without repetition = flower disappears (SpacedRepetitionConfig.kt:41)

### Mastery Thresholds

**1. "Learned" Threshold**
- **Definition:** `LEARNED_THRESHOLD = 3` (TrainingConfig.kt:13)
- **Context:** Used for lesson completion detection
- **Note:** This is NOT the mastery step threshold

**2. "Mastered" Threshold**
- **Definition:** `MASTERY_THRESHOLD = 150` unique card shows (SpacedRepetitionConfig.kt:31)
- **Visual:** 100% flower (masteryPercent) (FlowerCalculator.kt:33-34)
- **Calculation:** `(uniqueCardShows / 150) * 100%` (FlowerCalculator.kt:33)

**3. Boss Unlock Threshold**
- **Definition:** `BOSS_UNLOCK_SUB_LESSONS = 15` (TrainingConfig.kt:8)
- **Context:** First full cycle through lesson (15 sub-lessons completed)
- **Effect:** Boss battle unlocked (ProgressTracker.kt:110-119)

**4. Sub-Lesson Completion**
- **Definition:** All cards in sub-lesson shown at least once
- **Where:** `calculateCompletedSubLessons()` (ProgressTracker.kt:128-156)
- **Sequential:** Stops counting at first incomplete sub-lesson (line 152)

### Flower Visual States

**States:** (FlowerCalculator.kt:72-90)
- **SEED:** masteryPercent < 33%
- **SPROUT:** 33% ≤ masteryPercent < 66%
- **BLOOM:** masteryPercent ≥ 66% AND health = 100%
- **WILTING:** health < 100% (but > 50%)
- **WILTED:** health ≤ 50% (WILTED_THRESHOLD)
- **GONE:** > 90 days without practice (SpacedRepetitionConfig.kt:41)
- **LOCKED:** UI-only state for locked lessons (FlowerCalculator.kt:23)

**Scale Calculation:**
- `scaleMultiplier = masteryPercent * healthPercent` (FlowerCalculator.kt:59)
- Range: 0.5 to 1.0 (line 59)

### Cursor Movement

**1. Cursor Advance**
- **When:** After daily session is built
- **Where:** `advanceCursor()` (ProgressTracker.kt:345-365)
- **Logic:**
  - `sentenceOffset += sentenceCount` (line 350)
  - If `sentenceOffset >= lessonSize`:
    - `currentLessonIndex++` (line 357)
    - `sentenceOffset = 0` (line 360)

**2. First Session Storage**
- **When:** First daily session of the day
- **Where:** `storeFirstSessionCardIds()` (ProgressTracker.kt:373-384)
- **Purpose:** Allows "Repeat" to reconstruct exact same cards (line 369)
- **Stores:**
  - `firstSessionDate` (line 379)
  - `firstSessionSentenceCardIds` (line 381)
  - `firstSessionVerbCardIds` (line 382)

**3. Pack-Scoped Cursor**
- **File:** `daily_cursor_{packId}.yaml` (PackDailyCursorStore.kt:47)
- **Purpose:** Prevent cross-pack contamination (line 13)
- **TASK-080:** State isolation bug fix (line 15)

---

## Infrastructure Dependencies

### File I/O
- **AtomicFileWriter:** All file writes use temp → fsync → rename pattern (referenced in stores)
- **Format:** YAML for all progress/mastery files
- **Location:** `{context.filesDir}/grammarmate/`

### Storage Files

**1. MasteryStore**
- File: `mastery.yaml`
- Location: `grammarmate/mastery.yaml`
- Format: YAML with schema version
- Structure: `Map<languageId, Map<lessonId, LessonMasteryState>>`

**2. ProgressStore**
- File: `progress.yaml`
- Location: `grammarmate/progress.yaml`
- Format: YAML with schema version
- Contains: Global settings (mode, elite, daily, etc.)
- Note: Lesson progress migrated to PackLessonProgressStore

**3. PackLessonProgressStore**
- File pattern: `lesson_progress_{packId}.yaml`
- Example: `lesson_progress_ru-en-v1.yaml`
- Location: `grammarmate/lesson_progress_{packId}.yaml`
- Per-pack isolation

**4. PackDailyCursorStore**
- File pattern: `daily_cursor_{packId}.yaml`
- Example: `daily_cursor_ru-en-v1.yaml`
- Location: `grammarmate/daily_cursor_{packId}.yaml`
- Per-pack isolation

### Thread Safety
- **ReentrantLock:** All stores use mutex with `withLock` pattern
- **Cache Invalidation:** In-memory caches invalidated on save
- **Immediate Writes:** No write-behind batching (all writes via AtomicFileWriter)

---

## State Mutations

### When Cards Are Marked Shown
1. **Card displayed in training session**
2. `TrainingViewModel.recordCardShowForMastery()` called (TrainingViewModel.kt:1210-1217)
3. `ProgressTracker.recordCardShowForMastery()` checks mode/boss (ProgressTracker.kt:61-72)
4. `MasteryStore.recordCardShow()` updates state (MasteryStore.kt:154-198):
   - `uniqueCardShows++` (if new card)
   - `totalCardShows++`
   - `lastShowDateMs = now`
   - `intervalStepIndex` updated via SRS
   - `shownCardIds += cardId`

### When Mastery Levels Change
1. **Every card show** → `intervalStepIndex` potentially advances (MasteryStore.kt:172-176)
2. **SRS calculation:**
   - Calculate days since last show (MasteryStore.kt:164-168)
   - Check if on time (MasteryStore.kt:171)
   - Advance step if on time (MasteryStore.kt:172-176)
3. **Flower visual recalculation** → `FlowerCalculator.calculate()` (FlowerCalculator.kt:20-67)

### When Lessons Are Completed
1. **15 sub-lessons completed** → `checkAndMarkLessonCompleted()` (ProgressTracker.kt:110-119)
2. `MasteryStore.markLessonCompleted()` sets `completedAtMs` (MasteryStore.kt:223-236)
3. Boss battle unlocked (TrainingConfig.kt:8)

### When Cursor Moves
1. **Daily session completed** → `advanceCursor()` (ProgressTracker.kt:345-365)
2. `sentenceOffset += sentenceCount` (line 350)
3. If exceeds lesson size → `currentLessonIndex++`, `sentenceOffset = 0` (lines 356-360)

### When Progress Is Persisted
1. **Timer-based:** Every 30 seconds (TrainingViewModel.kt:175, 585, 633, etc.)
2. **Session events:** On block complete, on answer submit (TrainingViewModel.kt:1302)
3. **Mode changes:** On navigation, on settings changes (TrainingViewModel.kt:1266)
4. **Manual:** User requests save (TrainingViewModel.kt:1186)

---

## Test Coverage

### Unit Tests
- **FlowerCalculatorTest.kt** - Flower visual state calculations
- **SpacedRepetitionConfigTest.kt** - SRS interval calculations
- **LessonLadderCalculatorTest.kt** - Lesson progression logic

### Integration Tests
- **DailyPracticeClickUiTest.kt** - Daily practice flow integration
- **VerbDrillSessionCardRegressionTest.kt** - Verb drill session card behavior

### Test Infrastructure
- **FakeMasteryStore** (test-harness/FakeMasteryStore.kt) - Test double for MasteryStore
- **Test harness** supports progress tracking simulation (test-harness/README.md)

### Coverage Gaps
- **No direct unit tests** for ProgressTracker (delegates to stores)
- **No integration tests** for pack-scoped migration logic
- **No regression tests** for cross-pack contamination bugs (TASK-080, TASK-081)

---

## Risks / Unclear Points

### Architectural Concerns

**1. ProgressTracker Complexity**
- **Risk:** ~500 lines, handles multiple concerns (mastery, progress, cursor, reset)
- **Evidence:** ProgressTracker.kt:1-487
- **Impact:** Difficult to test, hard to reason about side effects

**2. Dual Storage Pattern**
- **Risk:** Lesson progress split between ProgressStore (legacy) and PackLessonProgressStore (new)
- **Evidence:** ProgressTracker.kt:260-298 (new), ProgressTracker.kt:300-326 (legacy)
- **Impact:** Confusing save logic, potential for data inconsistency
- **Status:** Phase 5 migration in progress (Wave 3.1)

**3. Global vs Pack-Scoped Cursor**
- **Risk:** Both `DailyCursorState` (global) and `PackDailyCursorState` (pack-scoped) exist
- **Evidence:** Models.kt:367-375 (global), Models.kt:383-397 (pack-scoped)
- **Impact:** Migration complexity, potential for data loss
- **Status:** TASK-080 migration implemented but legacy fields remain

**4. "Learned" vs "Mastered" Ambiguity**
- **Risk:** `LEARNED_THRESHOLD = 3` unclear purpose
- **Evidence:** TrainingConfig.kt:13
- **Impact:** Unclear business rule, may be dead code
- **Note:** Mastery threshold is clearly 150 unique shows (SpacedRepetitionConfig.kt:31)

**5. WORD_BANK Mode Tracking**
- **Risk:** WORD_BANK cards tracked in `shownCardIds` but not counted for mastery
- **Evidence:** ProgressTracker.kt:85-102, MasteryStore.kt:203-218
- **Impact:** Confusing semantics - "shown" doesn't mean "counted for mastery"
- **Question:** Why track WORD_BANK shows at all if they don't count?

**6. Boss Battle Progress Exception**
- **Risk:** ELITE boss saves progress but other bosses don't
- **Evidence:** ProgressTracker.kt:274-276
- **Impact:** Inconsistent behavior, potential for user confusion
- **Question:** Should ELITE boss really count for mastery?

**7. SRS Complexity**
- **Risk:** SRS calculation spread across multiple files
- **Evidence:** SpacedRepetitionConfig.kt, MasteryStore.kt:154-198, FlowerCalculator.kt
- **Impact:** Difficult to verify correctness, hard to modify
- **Note:** Well-documented with scientific sources (SpacedRepetitionConfig.kt:14-18)

**8. No Rollback Mechanism**
- **Risk:** If migration fails, no way to rollback
- **Evidence:** ProgressStore.kt:151-206, 208-307
- **Impact:** Potential data loss during upgrade
- **Mitigation:** Migration checks for existing data before overwriting (lines 160-170, 243-251)

### Data Consistency Risks

**1. Cross-Pack Contamination**
- **Risk:** Global stores could allow cross-pack data mixing
- **Evidence:** TASK-080, TASK-081 fix descriptions
- **Impact:** User switches packs, progress corrupted
- **Status:** Pack-scoped stores implemented, migration in progress

**2. Cache Invalidation**
- **Risk:** In-memory caches may diverge from disk
- **Evidence:** PackDailyCursorStore.kt:44, PackLessonProgressStore.kt:53
- **Impact:** Stale data returned if cache not invalidated
- **Mitigation:** Caches invalidated on save (lines 112-114, 76-78)

**3. Concurrent Access**
- **Risk:** Multiple threads could corrupt YAML files
- **Evidence:** ReentrantLock usage in all stores
- **Impact:** Data corruption if lock not held
- **Mitigation:** All file operations wrapped in `mutex.withLock`

### Performance Concerns

**1. YAML Parsing Overhead**
- **Risk:** Full YAML parse on every load
- **Evidence:** All stores use `yaml.load()` (MasteryStore.kt:73, ProgressStore.kt:55)
- **Impact:** Slow startup, slow save operations
- **Mitigation:** In-memory caches (MasteryStore.kt:51, PackDailyCursorStore.kt:44)

**2. Full File Rewrite**
- **Risk:** Entire YAML file rewritten on every save
- **Evidence:** `persistToFile()` writes all cache data (MasteryStore.kt:311-338)
- **Impact:** Slow for large data sets, potential I/O bottleneck
- **Mitigation:** AtomicFileWriter prevents corruption

**3. No Incremental Updates**
- **Risk:** Cannot update single lesson without rewriting all lessons
- **Evidence:** `save()` overwrites entire file (MasteryStore.kt:136-145)
- **Impact:** Poor scalability as lesson count grows
- **Question:** Should we switch to SQLite or incremental file format?

---

## Evidence

### File:Line References

**ProgressTracker**
- Wrapper definition: feature/progress/ProgressTracker.kt:25-34
- recordCardShowForMastery: feature/progress/ProgressTracker.kt:53-78
- WORD_BANK exception: feature/progress/ProgressTracker.kt:69-72
- Boss battle exception: feature/progress/ProgressTracker.kt:61-62
- saveProgress: feature/progress/ProgressTracker.kt:269-334
- advanceCursor: feature/progress/ProgressTracker.kt:345-365
- resetStores: feature/progress/ProgressTracker.kt:395-403

**MasteryStore**
- Data structure: data/Models.kt:180-190
- recordCardShow: data/MasteryStore.kt:154-198
- SRS calculation: data/MasteryStore.kt:164-176
- markCardsShownForProgress: data/MasteryStore.kt:203-218
- markLessonCompleted: data/MasteryStore.kt:223-236
- Storage format: data/MasteryStore.kt:311-338

**ProgressStore**
- Data structure: data/Models.kt (referenced in data/ProgressStore.kt)
- load: data/ProgressStore.kt:53-100
- save: data/ProgressStore.kt:102-145
- Migration methods: data/ProgressStore.kt:151-307

**PackLessonProgressStore**
- Definition: data/PackLessonProgressStore.kt:1-235
- File pattern: data/PackLessonProgressStore.kt:56
- TASK-081 reference: data/PackLessonProgressStore.kt:15

**PackDailyCursorStore**
- Definition: data/PackDailyCursorStore.kt:1-172
- File pattern: data/PackDailyCursorStore.kt:47
- TASK-080 reference: data/PackDailyCursorStore.kt:15

**SpacedRepetitionConfig**
- INTERVAL_LADDER_DAYS: data/SpacedRepetitionConfig.kt:26
- MASTERY_THRESHOLD: data/SpacedRepetitionConfig.kt:31
- calculateStability: data/SpacedRepetitionConfig.kt:65-75
- calculateHealthPercent: data/SpacedRepetitionConfig.kt:106-135
- nextIntervalStep: data/SpacedRepetitionConfig.kt:144-152

**FlowerCalculator**
- calculate: data/FlowerCalculator.kt:20-67
- determineFlowerState: data/FlowerCalculator.kt:72-90
- masteryPercent calculation: data/FlowerCalculator.kt:33-34

**TrainingConfig**
- BOSS_UNLOCK_SUB_LESSONS: data/TrainingConfig.kt:8
- LEARNED_THRESHOLD: data/TrainingConfig.kt:13

**TrainingViewModel Integration**
- recordCardShowForMastery: ui/TrainingViewModel.kt:1210-1217
- saveProgress calls: ui/TrainingViewModel.kt:585, 633, 656, 671, 860, 901, 1091, 1107, 1186, 1266, 1302
- progressTracker delegation: ui/TrainingViewModel.kt:168, 175, 452, 613, 982, 1138, 1153, 1198, 1214, 1310, 1316, 1334, 1344, 1428

**DailyPracticeCoordinator Integration**
- Card show recording: feature/daily/DailyPracticeCoordinator.kt:557
- Progress tracking: feature/daily/DailyPracticeCoordinator.kt:64-66

---

## Summary

### Current State
- **Progress tracking** is split across 4 stores (MasteryStore, ProgressStore, PackLessonProgressStore, PackDailyCursorStore)
- **ProgressTracker** acts as a facade/wrapper around these stores
- **SRS system** is scientifically grounded but complex
- **Pack-scoped migration** is in progress (TASK-080, TASK-081)
- **WORD_BANK mode** has special semantics (tracked but not counted)

### Key Strengths
- Well-documented SRS algorithm with scientific sources
- Thread-safe file operations with ReentrantLock
- AtomicFileWriter prevents corruption
- Pack-scoped isolation prevents cross-contamination
- Comprehensive flower visual feedback system

### Key Weaknesses
- ProgressTracker is too complex (500 lines, multiple concerns)
- Dual storage pattern (legacy + pack-scoped) creates confusion
- No incremental updates (full file rewrite on every save)
- YAML parsing overhead
- "Learned" vs "mastered" terminology ambiguity
- No rollback mechanism for failed migrations

### Recommendations for Refactoring
1. Split ProgressTracker into smaller, focused modules
2. Complete migration to pack-scoped storage, remove legacy fields
3. Consider incremental file format or SQLite for better scalability
4. Clarify "learned" vs "mastered" terminology
5. Add rollback mechanism for migrations
6. Consolidate SRS calculation into single module
7. Add integration tests for migration logic
8. Document WORD_BANK mode semantics more clearly

---

**END OF MAP**
