# TASK-081: Lesson Progress Isolation Bug - Pack-Scoped Lesson Progress

**Status:** DONE
**Created:** 2026-05-21
**Branch:** feature/lesson-progress-isolation-bug (from main)
**Priority:** HIGH
**Complexity:** MODERATE
**UC:** UC-02, UC-06, UC-22
**Scenario:** scenario-01-training-flow.md, scenario-12-state-persistence.md

---

## Problem Statement

### Bug: Regular lesson progress state shared across ALL packs

Regular lesson training (TrainingScreen) uses **global** progress state from `progress.yaml`, causing cross-pack contamination. When a user switches between lesson packs (e.g., English → Italian), the lesson position and card index from the previous pack is incorrectly used for the new pack.

**Root Cause:**
- `TrainingProgress.lessonId` - GLOBAL (shared across all packs)
- `TrainingProgress.currentIndex` - GLOBAL (shared across all packs)
- File: `progress.yaml` contains single global state for lesson progress
- Switching packs → wrong lesson selected, wrong card position

**Current State Architecture (BROKEN):**
```
progress.yaml
├── TrainingProgress
│   ├── lessonId: String?          ← GLOBAL - shared across all packs
│   ├── currentIndex: Int          ← GLOBAL - shared across all packs
│   ├── correctCount: Int          ← GLOBAL - shared across all packs
│   ├── incorrectCount: Int        ← GLOBAL - shared across all packs
│   └── (session state fields)     ← GLOBAL - shared across all packs
```

**User Impact:**

**Scenario:** User has 2 packs installed:
1. **EN_WORD_ORDER_A1** - Training at Lesson 3, card 15
2. **IT_VERB_GROUPS_ALL** - Training at Lesson 7, card 8

**Broken Flow:**
1. User starts training in EN pack → Lesson 3, card 15
2. User exits session, progress saved: `lessonId = "lesson-03"`, `currentIndex = 15`
3. User switches to IT pack via HomeScreen package selector
4. User taps Lesson 7 tile on Home screen
5. **BUG:** TrainingScreen loads at card index 15 (WRONG - uses EN pack's currentIndex)
6. User practices IT pack → currentIndex advances to 25
7. User switches back to EN pack, taps Lesson 3
8. **BUG:** TrainingScreen loads at card index 25 (WRONG - lesson position corrupted)

**Expected Flow:**
1. EN pack lesson progress: lessonId="lesson-03", currentIndex=15
2. IT pack lesson progress: lessonId="lesson-07", currentIndex=8
3. Switch to IT pack → training uses IT pack's progress (lesson 7, card 8)
4. Switch to EN pack → training uses EN pack's progress (lesson 3, card 15)

**Desired State Architecture (CORRECT):**
```
progress.yaml
├── TrainingProgress
│   ├── activePackId: PackId?           ← Global pack selector only
│   ├── currentScreen: String           ← Global navigation state
│   └── (no lessonId, no currentIndex)

lesson_progress_{packId}.yaml  ← Per-pack lesson progress
├── lessonId: String?
├── currentIndex: Int
├── correctCount: Int
├── incorrectCount: Int
├── incorrectAttemptsForCard: Int
├── activeTimeMs: Long
├── state: SessionState
├── voiceActiveMs: Long
├── voiceWordCount: Int
├── hintCount: Int
└── (session-specific fields)
```

---

## Research Findings

### TASK-080: Daily Cursor State Already Fixed

**Good Reference:** `TASK-080` already implemented pack-scoped state for daily cursor:

```kotlin
// PackDailyCursorState (lines 384-398 in Models.kt)
data class PackDailyCursorState(
    val packId: String,
    val sentenceOffset: Int = 0,
    val currentLessonIndex: Int = 0,
    val lastSessionHash: Int = 0,
    val firstSessionDate: String = "",
    val firstSessionSentenceCardIds: List<String> = emptyList(),
    val firstSessionVerbCardIds: List<String> = emptyList(),
    val verbOffset: Int = 0
)
```

**Key Pattern:**
- Pack-scoped state stored in per-pack files: `daily_cursor_{packId}.yaml`
- Migration logic: `migrateGlobalDailyCursorToPackScoped()` moves global → per-pack
- Each `PackDailyCursorStore` instance is pack-scoped

### Current Lesson Progress Implementation (BROKEN)

**TrainingProgress fields (lines 152-175 in Models.kt):**
```kotlin
data class TrainingProgress(
    val languageId: LanguageId = LanguageId("en"),
    val mode: TrainingMode = TrainingMode.LESSON,
    val lessonId: String? = null,              // ← GLOBAL - WRONG
    val currentIndex: Int = 0,                 // ← GLOBAL - WRONG
    val correctCount: Int = 0,                 // ← GLOBAL - WRONG
    val incorrectCount: Int = 0,               // ← GLOBAL - WRONG
    val incorrectAttemptsForCard: Int = 0,
    val activeTimeMs: Long = 0L,
    val state: SessionState = SessionState.PAUSED,
    // ... other session fields
    val activePackId: PackId? = null           // ← Global pack selector only
)
```

**Problem:** Lesson progress fields are stored globally in `progress.yaml` → cross-pack contamination.

### Verb Drill: Already Pack-Scoped by Language

**Reference Implementation:** `VerbDrillStore` already implements per-language state correctly:

```kotlin
// VerbDrillStoreImpl.kt (lines 53-66)
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

---

## Solution Architecture

### Phase 1: Create Pack-Scoped Data Classes

**1.1 Create `PackLessonProgressState` data class**

```kotlin
// data/Models.kt
/**
 * Pack-scoped lesson progress state. Each pack maintains its own training
 * session position to prevent cross-pack contamination when switching between
 * lesson packs.
 *
 * Used by TASK-081: Lesson progress isolation bug fix.
 */
data class PackLessonProgressState(
    val packId: PackId,                       // Pack identifier
    val languageId: LanguageId,               // Language for this pack
    val mode: TrainingMode = TrainingMode.LESSON,
    val lessonId: String? = null,             // Current lesson ID in this pack
    val currentIndex: Int = 0,                // Current card index in lesson
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val incorrectAttemptsForCard: Int = 0,
    val activeTimeMs: Long = 0L,
    val state: SessionState = SessionState.PAUSED,
    val voiceActiveMs: Long = 0L,
    val voiceWordCount: Int = 0,
    val hintCount: Int = 0
) {
    companion object {
        /** Create default lesson progress for a pack */
        fun forPack(packId: PackId, languageId: LanguageId) = PackLessonProgressState(
            packId = packId,
            languageId = languageId
        )
    }
}
```

**Rationale:** Wrapper that includes `packId` and `languageId` for validation and prevents cross-pack loading.

### Phase 2: Create Pack-Scoped Store

**2.1 Create `PackLessonProgressStore` interface**

```kotlin
// data/PackLessonProgressStore.kt
interface PackLessonProgressStore {
    /**
     * Load lesson progress for a specific pack.
     * @param packId Pack identifier
     * @return Lesson progress state, or default if no file exists
     */
    fun getLessonProgress(packId: PackId): PackLessonProgressState

    /**
     * Save lesson progress for a specific pack.
     * @param progress Progress state to save (must contain matching packId)
     */
    fun saveLessonProgress(progress: PackLessonProgressState)

    /**
     * Migrate global lesson progress to pack-scoped file.
     * @param globalProgress Global TrainingProgress to migrate
     * @param packId Target pack ID for migration
     */
    fun migrateGlobalToPackScoped(globalProgress: TrainingProgress, packId: PackId)

    /**
     * Delete lesson progress for a specific pack.
     * @param packId Pack identifier
     */
    fun deleteProgress(packId: PackId)
}
```

**2.2 Implement `PackLessonProgressStoreImpl`**

```kotlin
// data/PackLessonProgressStore.kt
class PackLessonProgressStoreImpl(
    context: Context
) : PackLessonProgressStore {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val mutex = ReentrantLock()

    private fun getFile(packId: PackId) = File(baseDir, "lesson_progress_${packId.value}.yaml")

    override fun getLessonProgress(packId: PackId): PackLessonProgressState {
        mutex.lock()
        return try {
            val file = getFile(packId)
            if (!file.exists()) {
                // Return default progress if no file exists
                return PackLessonProgressState(
                    packId = packId,
                    languageId = LanguageId("en") // Default, will be overridden on first save
                )
            }

            val data = yaml.load<Any>(file.inputStream())
            val payload = when (data) {
                is Map<*, *> -> (data["data"] as? Map<*, *>) ?: data
                else -> null
            } ?: return PackLessonProgressState.forPack(packId, LanguageId("en"))

            PackLessonProgressState(
                packId = packId, // Validate packId matches
                languageId = LanguageId(payload["languageId"] as? String ?: "en"),
                mode = TrainingMode.valueOf(payload["mode"] as? String ?: TrainingMode.LESSON.name),
                lessonId = payload["lessonId"] as? String,
                currentIndex = (payload["currentIndex"] as? Number)?.toInt() ?: 0,
                correctCount = (payload["correctCount"] as? Number)?.toInt() ?: 0,
                incorrectCount = (payload["incorrectCount"] as? Number)?.toInt() ?: 0,
                incorrectAttemptsForCard = (payload["incorrectAttemptsForCard"] as? Number)?.toInt() ?: 0,
                activeTimeMs = (payload["activeTimeMs"] as? Number)?.toLong() ?: 0L,
                state = SessionState.valueOf(payload["state"] as? String ?: SessionState.PAUSED.name),
                voiceActiveMs = (payload["voiceActiveMs"] as? Number)?.toLong() ?: 0L,
                voiceWordCount = (payload["voiceWordCount"] as? Number)?.toInt() ?: 0,
                hintCount = (payload["hintCount"] as? Number)?.toInt() ?: 0
            )
        } catch (e: Exception) {
            Log.e("PackLessonProgressStore", "Failed to load progress for pack: $packId", e)
            PackLessonProgressState.forPack(packId, LanguageId("en"))
        } finally {
            mutex.unlock()
        }
    }

    override fun saveLessonProgress(progress: PackLessonProgressState) {
        mutex.lock()
        try {
            val file = getFile(progress.packId)
            val payload = linkedMapOf(
                "packId" to progress.packId.value,
                "languageId" to progress.languageId.value,
                "mode" to progress.mode.name,
                "lessonId" to progress.lessonId,
                "currentIndex" to progress.currentIndex,
                "correctCount" to progress.correctCount,
                "incorrectCount" to progress.incorrectCount,
                "incorrectAttemptsForCard" to progress.incorrectAttemptsForCard,
                "activeTimeMs" to progress.activeTimeMs,
                "state" to progress.state.name,
                "voiceActiveMs" to progress.voiceActiveMs,
                "voiceWordCount" to progress.voiceWordCount,
                "hintCount" to progress.hintCount
            )
            val data = linkedMapOf(
                "schemaVersion" to 1,
                "data" to payload
            )
            AtomicFileWriter.writeText(file, yaml.dump(data))
            Log.i("PackLessonProgressStore", "Saved lesson progress for pack: ${progress.packId}")
        } finally {
            mutex.unlock()
        }
    }

    override fun migrateGlobalToPackScoped(globalProgress: TrainingProgress, packId: PackId) {
        mutex.lock()
        try {
            val file = getFile(packId)

            // Skip if pack-scoped file already exists
            if (file.exists()) {
                Log.d("PackLessonProgressStore", "Pack progress file already exists: $packId")
                return
            }

            // Create pack-scoped progress from global state
            val packProgress = PackLessonProgressState(
                packId = packId,
                languageId = globalProgress.languageId,
                mode = globalProgress.mode,
                lessonId = globalProgress.lessonId,
                currentIndex = globalProgress.currentIndex,
                correctCount = globalProgress.correctCount,
                incorrectCount = globalProgress.incorrectCount,
                incorrectAttemptsForCard = globalProgress.incorrectAttemptsForCard,
                activeTimeMs = globalProgress.activeTimeMs,
                state = globalProgress.state,
                voiceActiveMs = globalProgress.voiceActiveMs,
                voiceWordCount = globalProgress.voiceWordCount,
                hintCount = globalProgress.hintCount
            )

            saveLessonProgress(packProgress)
            Log.i("PackLessonProgressStore", "Migrated global progress to pack: $packId")
        } finally {
            mutex.unlock()
        }
    }

    override fun deleteProgress(packId: PackId) {
        mutex.lock()
        try {
            val file = getFile(packId)
            if (file.exists()) {
                file.delete()
                Log.i("PackLessonProgressStore", "Deleted progress for pack: $packId")
            }
        } finally {
            mutex.unlock()
        }
    }
}
```

**Pattern:** Copy `PackDailyCursorStoreImpl` structure (mutex, AtomicFileWriter, YAML parsing, per-pack files).

### Phase 3: Migrate Existing Global State

**3.1 Add migration to `ProgressStore`**

```kotlin
// data/ProgressStore.kt
/**
 * Migrate global lesson progress to pack-scoped files.
 * Called once during app upgrade to TASK-081.
 *
 * @param activePackId The currently active pack ID to migrate global progress to
 * @param packProgressStore The pack-scoped progress store to save migrated data
 * @return true if migration was performed, false if no data to migrate
 */
fun migrateGlobalLessonProgressToPackScoped(
    activePackId: String?,
    packProgressStore: PackLessonProgressStore
): Boolean = mutex.withLock {
    // Check if there's existing global lesson progress to migrate
    val progress = load()

    // Check if global progress has meaningful data (not defaults)
    val hasDataToMigrate = progress.lessonId != null ||
        progress.currentIndex > 0 ||
        progress.correctCount > 0 ||
        progress.incorrectCount > 0 ||
        progress.activeTimeMs > 0L ||
        progress.voiceActiveMs > 0L

    if (!hasDataToMigrate) {
        Log.d("ProgressStore", "No global lesson progress to migrate")
        return@withLock false
    }

    // Determine target pack ID for migration
    val targetPackId = activePackId ?: progress.activePackId?.value
    if (targetPackId == null) {
        Log.w("ProgressStore", "Cannot migrate global lesson progress: no active pack ID available")
        return@withLock false
    }

    try {
        // Migrate to pack-scoped file
        packProgressStore.migrateGlobalToPackScoped(progress, PackId(targetPackId))
        Log.i("ProgressStore", "Migrated global lesson progress to pack: $targetPackId")

        // Clear global lesson progress from progress.yaml
        val clearedProgress = progress.copy(
            lessonId = null,
            currentIndex = 0,
            correctCount = 0,
            incorrectCount = 0,
            incorrectAttemptsForCard = 0,
            activeTimeMs = 0L,
            state = SessionState.PAUSED,
            voiceActiveMs = 0L,
            voiceWordCount = 0,
            hintCount = 0
        )
        save(clearedProgress)
        Log.i("ProgressStore", "Cleared global lesson progress from progress.yaml after migration")

        return@withLock true
    } catch (e: Exception) {
        Log.e("ProgressStore", "Failed to migrate global lesson progress to pack: $targetPackId", e)
        return@withLock false
    }
}
```

**3.2 Call migration on app upgrade**

```kotlin
// ui/AppRoot.kt
fun checkAndMigrate() {
    val lastVersion = AppConfigStore.getLastVersion()
    if (lastVersion < VERSION_081_LESSON_PROGRESS_ISOLATION) {
        // Migrate daily cursor (TASK-080)
        if (lastVersion < VERSION_080_STATE_ISOLATION) {
            ProgressStore(context).migrateGlobalDailyCursorToPackScoped(
                activePackId = _coreState.value.navigation.activePackId?.value,
                packCursorStore = packDailyCursorStore
            )
        }

        // Migrate lesson progress (TASK-081)
        val activePackId = _coreState.value.navigation.activePackId?.value
        if (activePackId != null) {
            ProgressStore(context).migrateGlobalLessonProgressToPackScoped(
                activePackId = activePackId,
                packProgressStore = packLessonProgressStore
            )
        }

        AppConfigStore.setLastVersion(VERSION_081_LESSON_PROGRESS_ISOLATION)
    }
}

private val VERSION_080_STATE_ISOLATION = 80
private val VERSION_081_LESSON_PROGRESS_ISOLATION = 81
```

### Phase 4: Update TrainingViewModel

**4.1 Add pack-scoped lesson progress loading**

```kotlin
// ui/TrainingViewModel.kt
class TrainingViewModel(
    // ... existing dependencies
    private val packLessonProgressStore: PackLessonProgressStore  // ← NEW
) : ViewModel() {

    private val activePackId: PackId?
        get() = _coreState.value.navigation.activePackId

    init {
        viewModelScope.launch {
            // ... existing initialization

            // Load pack-scoped lesson progress
            loadPackLessonProgress()
        }
    }

    private suspend fun loadPackLessonProgress() {
        val packId = activePackId ?: return
        val packProgress = withContext(Dispatchers.IO) {
            packLessonProgressStore.getLessonProgress(packId)
        }

        // Merge pack progress into card session state
        _coreState.update { currentState ->
            currentState.copy(
                cardSession = currentState.cardSession.copy(
                    currentIndex = packProgress.currentIndex,
                    correctCount = packProgress.correctCount,
                    incorrectCount = packProgress.incorrectCount,
                    incorrectAttemptsForCard = packProgress.incorrectAttemptsForCard,
                    activeTimeMs = packProgress.activeTimeMs,
                    voiceActiveMs = packProgress.voiceActiveMs,
                    voiceWordCount = packProgress.voiceWordCount,
                    hintCount = packProgress.hintCount
                )
            )
        }
    }

    private fun savePackLessonProgress() {
        val packId = activePackId ?: return
        val cardSession = _coreState.value.cardSession

        val packProgress = PackLessonProgressState(
            packId = packId,
            languageId = _coreState.value.navigation.selectedLanguageId,
            mode = _coreState.value.navigation.mode,
            lessonId = _coreState.value.navigation.selectedLessonId?.value,
            currentIndex = cardSession.currentIndex,
            correctCount = cardSession.correctCount,
            incorrectCount = cardSession.incorrectCount,
            incorrectAttemptsForCard = cardSession.incorrectAttemptsForCard,
            activeTimeMs = cardSession.activeTimeMs,
            state = cardSession.sessionState,
            voiceActiveMs = cardSession.voiceActiveMs,
            voiceWordCount = cardSession.voiceWordCount,
            hintCount = cardSession.hintCount
        )

        viewModelScope.launch(Dispatchers.IO) {
            packLessonProgressStore.saveLessonProgress(packProgress)
        }
    }
}
```

**4.2 Call save on card advance and session end**

```kotlin
// ui/TrainingViewModel.kt
fun advanceToNextCard() {
    // ... existing advance logic

    // Save pack-scoped progress
    savePackLessonProgress()
}

fun pauseSession() {
    // ... existing pause logic

    // Save pack-scoped progress
    savePackLessonProgress()
}
```

**4.3 Update `selectLesson` to load pack progress**

```kotlin
// ui/TrainingViewModel.kt
fun selectLesson(lessonId: String) {
    viewModelScope.launch {
        // ... existing lesson selection logic

        // Load pack-scoped progress for the new lesson
        loadPackLessonProgress()
    }
}
```

### Phase 5: Update ProgressStore

**5.1 Remove lesson progress fields from TrainingProgress**

```kotlin
// data/Models.kt - TrainingProgress
data class TrainingProgress(
    val languageId: LanguageId = LanguageId("en"),
    val mode: TrainingMode = TrainingMode.LESSON,
    // REMOVED: val lessonId: String? = null
    // REMOVED: val currentIndex: Int = 0
    // REMOVED: val correctCount: Int = 0
    // REMOVED: val incorrectCount: Int = 0
    // REMOVED: val incorrectAttemptsForCard: Int = 0
    // REMOVED: val activeTimeMs: Long = 0L
    // REMOVED: val state: SessionState = SessionState.PAUSED
    // REMOVED: val voiceActiveMs: Long = 0L
    // REMOVED: val voiceWordCount: Int = 0
    // REMOVED: val hintCount: Int = 0

    // Keep: boss rewards, elite state, daily state
    val bossLessonRewards: Map<String, String> = emptyMap(),
    val bossMegaReward: String? = null,
    val bossMegaRewards: Map<String, String> = emptyMap(),
    val eliteStepIndex: Int = 0,
    val eliteBestSpeeds: List<Double> = emptyList(),
    val currentScreen: String = "HOME",
    val activePackId: PackId? = null,
    val dailyLevel: Int = 0,
    val dailyTaskIndex: Int = 0,
    val dailyCursor: DailyCursorState = DailyCursorState()
)
```

**5.2 Update ProgressStore serialization**

```kotlin
// data/ProgressStore.kt
override fun load(): TrainingProgress = mutex.withLock {
    // ... existing logic

    // REMOVED: lessonId, currentIndex, correctCount, etc. parsing from YAML
    // Keep: boss rewards, elite state, daily state, currentScreen, activePackId

    return TrainingProgress(
        languageId = LanguageId(payload["languageId"] as? String ?: "en"),
        mode = TrainingMode.valueOf(payload["mode"] as? String ?: TrainingMode.LESSON.name),
        // REMOVED: lessonId, currentIndex, etc.
        bossLessonRewards = /* ... */,
        bossMegaReward = /* ... */,
        bossMegaRewards = /* ... */,
        eliteStepIndex = /* ... */,
        eliteBestSpeeds = /* ... */,
        currentScreen = /* ... */,
        activePackId = /* ... */,
        dailyLevel = /* ... */,
        dailyTaskIndex = /* ... */,
        dailyCursor = /* ... */
    )
}

override fun save(progress: TrainingProgress) {
    mutex.withLock {
        val payload = linkedMapOf(
            "languageId" to progress.languageId.value,
            "mode" to progress.mode.name,
            // REMOVED: lessonId, currentIndex, correctCount, etc.
            "bossLessonRewards" to progress.bossLessonRewards,
            "bossMegaReward" to progress.bossMegaReward,
            "bossMegaRewards" to progress.bossMegaRewards,
            "eliteStepIndex" to progress.eliteStepIndex,
            "eliteBestSpeeds" to progress.eliteBestSpeeds,
            "currentScreen" to progress.currentScreen,
            "activePackId" to progress.activePackId?.value,
            "dailyLevel" to progress.dailyLevel,
            "dailyTaskIndex" to progress.dailyTaskIndex,
            "dailyCursor" to linkedMapOf(/* ... */)
        )
        // ... existing save logic
    }
}
```

**Rationale:** Lesson progress is now pack-scoped, not part of global training progress.

---

## Changes

### File List

1. **app/src/main/java/com/alexpo/grammermate/data/Models.kt**
   - Add `PackLessonProgressState` data class
   - Remove lesson progress fields from `TrainingProgress` (lessonId, currentIndex, correctCount, incorrectCount, etc.)

2. **app/src/main/java/com/alexpo/grammermate/data/PackLessonProgressStore.kt** (NEW)
   - Create `PackLessonProgressStore` interface
   - Create `PackLessonProgressStoreImpl` class
   - Implement `migrateGlobalToPackScoped()`

3. **app/src/main/java/com/alexpo/grammermate/data/ProgressStore.kt**
   - Remove lesson progress fields from `load()` parsing
   - Remove lesson progress fields from `save()` serialization
   - Add `migrateGlobalLessonProgressToPackScoped()` method

4. **app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt**
   - Inject `PackLessonProgressStore` (scoped to `activePackId`)
   - Add `loadPackLessonProgress()` method
   - Add `savePackLessonProgress()` method
   - Call `savePackLessonProgress()` on card advance and session end
   - Update `selectLesson()` to load pack progress

5. **app/src/main/java/com/alexpo/grammermate/ui/AppRoot.kt**
   - Add migration call in `checkAndMigrate()`
   - Define `VERSION_081_LESSON_PROGRESS_ISOLATION = 81`

6. **app/src/main/java/com/alexpo/grammermate/di/AppContainer.kt** (if using DI)
   - Add `packLessonProgressStore()` provider method

---

## Acceptance Criteria

### UC-02: Start a Training Sub-Lesson

**NEW ACs:**
- **AC6:** `selectLesson()` loads lesson progress from `lesson_progress_{packId}.yaml` for current `activePackId`
- **AC7:** Starting a training session uses pack-scoped `currentIndex` and `correctCount` (not global)
- **AC8:** Switching active pack resets training state to new pack's lesson progress (no cross-pack contamination)

### UC-06: Complete a Sub-Lesson

**NEW ACs:**
- **AC7:** Session completion saves progress to `lesson_progress_{packId}.yaml` per pack (not global `progress.yaml`)
- **AC8:** Switching between packs preserves each pack's lesson progress independently

### UC-22: Resume App After Kill (State Restoration)

**NEW ACs:**
- **AC8:** App restoration loads lesson progress from `lesson_progress_{activePackId}.yaml` for the active pack
- **AC9:** Switching active pack after restoration loads the correct pack's lesson progress

### Migration Acceptance Criteria

- **AC-MIG1:** If global lesson progress has non-default values (lessonId != null, currentIndex > 0, etc.), migration copies it to active pack's file
- **AC-MIG2:** After migration, global lesson progress fields in `progress.yaml` are cleared to defaults
- **AC-MIG3:** Migration runs only once (version check)
- **AC-MIG4:** If no active pack exists, migration logs warning and skips (no crash)

---

## Verification Checklist

### Functional Verification

1. [ ] **Pack isolation:**
   - [ ] Start training in EN pack → complete 5 cards → verify progress saved
   - [ ] Switch to IT pack → start training → verify `currentIndex` starts from 0 (not EN pack's 5)
   - [ ] Complete cards in IT pack → switch back to EN pack → verify EN pack's `currentIndex` preserved

2. [ ] **File storage:**
   - [ ] After EN pack training, check `lesson_progress_{en-pack-id}.yaml` exists
   - [ ] After IT pack training, check `lesson_progress_{it-pack-id}.yaml` exists
   - [ ] Verify `progress.yaml` no longer contains `lessonId`, `currentIndex`, `correctCount`, etc.

3. [ ] **Session persistence:**
   - [ ] Start training in EN pack → exit mid-session → kill app → restart
   - [ ] Verify session restored at correct `currentIndex` for EN pack
   - [ ] Switch to IT pack → start training → verify IT pack has separate progress

4. [ ] **Migration:**
   - [ ] Install app version before TASK-081 → start training → complete 10 cards
   - [ ] Upgrade to TASK-081 → verify `lesson_progress_{packId}.yaml` created with migrated data
   - [ ] Verify `progress.yaml` no longer has `lessonId`, `currentIndex`, etc.
   - [ ] Verify migration doesn't run on subsequent app launches

5. [ ] **Card advance:**
   - [ ] Start training in EN pack → answer 3 cards correctly
   - [ ] Verify `correctCount` saved to `lesson_progress_{en-pack-id}.yaml`
   - [ ] Switch to IT pack → verify `correctCount` is 0 (not EN pack's 3)

### Regression Verification

6. [ ] **Daily practice:**
   - [ ] Start daily practice → verify no interference with lesson progress
   - [ ] Verify daily cursor still pack-scoped (TASK-080 not broken)

7. [ ] **Verb drill:**
   - [ ] Start verb drill session → verify verb drill progress still pack-scoped
   - [ ] Verify no cross-contamination between verb drill and lesson progress

8. [ ] **Vocab drill:**
   - [ ] Start vocab drill session → verify vocab mastery still pack-scoped

9. [ ] **Boss battle:**
   - [ ] Start boss battle → verify boss rewards still saved globally (not affected)

10. [ ] **Home screen:**
    - [ ] Package selector shows correct active pack
    - [ ] Lesson tiles navigate to correct pack's lessons

11. [ ] **Settings:**
    - [ ] Reset progress for current language → verify lesson progress cleared for that pack
    - [ ] Verify other packs' lesson progress are NOT affected

---

## Migration Strategy

### Step 1: Data Migration (One-Time)

**Trigger:** App upgrade to version 81

**Logic:**
1. Check if `progress.yaml` contains non-default lesson progress fields
2. If yes:
   - Read `activePackId` from `progress.yaml`
   - If `activePackId` is null, find first installed pack
   - Create `lesson_progress_{packId}.yaml` with migrated data
   - Clear lesson progress fields in `progress.yaml` to defaults
3. If no: Skip migration

**Edge Cases:**
- No packs installed: Skip migration, log warning
- Multiple packs installed: Migrate to active pack only (user must switch to other packs to initialize their progress)

### Step 2: Code Migration (Backward Compatible)

**Phase 1:** Add new pack-scoped store alongside old global store
- Keep lesson progress fields in `TrainingProgress` temporarily
- `PackLessonProgressStore` reads/writes per-pack files

**Phase 2:** Switch consumers to pack-scoped store
- `TrainingViewModel` uses `PackLessonProgressStore`
- `ProgressStore` still loads/saves global progress (for migration)

**Phase 3:** Remove global lesson progress
- Remove lesson progress fields from `TrainingProgress`
- `ProgressStore` no longer loads/saves lesson progress
- Migration runs once, then removed

**Rollback Plan:** If migration fails, app continues using global progress (old behavior). User can retry migration by upgrading to next version.

---

## Scope Boundaries

**DO NOT touch:**
- Daily cursor state (already pack-scoped via TASK-080)
- Verb drill progress (already pack-scoped via `VerbDrillStore`)
- Vocab drill mastery (already pack-scoped via `WordMasteryStore`)
- Lesson mastery flowers (already pack-scoped via `MasteryStore`)
- Boss rewards (global, not affected)
- Elite state (global, not affected)

**DO touch:**
- Lesson progress state (global → pack-scoped)
- `TrainingProgress` data class (remove lesson progress fields)
- `ProgressStore` (remove lesson progress, add migration)
- `TrainingViewModel` (use pack-scoped store)

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
   - Install EN pack only → start training → complete 5 cards → verify progress
   - Install IT pack only → start training → complete 5 cards → verify progress
   - Install BOTH packs → switch between them → verify isolation

4. **Migration verification:**
   - Install old app version → start training → complete 10 cards → upgrade
   - Verify progress migrated correctly to `lesson_progress_{packId}.yaml`
   - Verify no data loss (correctCount, incorrectCount, currentIndex preserved)
   - Verify `progress.yaml` no longer has lesson progress fields

5. **Cross-feature regression:**
   - Daily practice (start, complete blocks, verify cursor still pack-scoped)
   - Verb drill standalone (verify progress still pack-scoped)
   - Vocab drill standalone (verify mastery still pack-scoped)
   - Boss battle (verify rewards still global)
   - Home screen flowers (verify mastery still pack-scoped)

6. **UC/AC spot-check:**
   - UC-02 AC6-AC8 (new)
   - UC-06 AC7-AC8 (new)
   - UC-22 AC8-AC9 (new)
   - UC-02 AC1-AC5 (existing - verify no regression)
   - UC-06 AC1-AC6 (existing - verify no regression)
   - UC-22 AC1-AC7 (existing - verify no regression)

---

## Git

**Branch:** `feature/lesson-progress-isolation-bug` (from `main`)

**Commits:**
1. Add `PackLessonProgressState` data class to Models.kt
2. Create `PackLessonProgressStore` interface and implementation
3. Add migration logic to `ProgressStore`
4. Update `TrainingViewModel` to use pack-scoped lesson progress
5. Remove lesson progress fields from `TrainingProgress`
6. Update `AppRoot` to call migration on upgrade

**Commit footer:**
```
Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

---

## Implementation Notes

### Pattern Reference: TASK-080 (Daily Cursor)

This task follows the established pattern from `TASK-080`:
- Pack-scoped state stored in per-pack files
- Migration from global to per-pack files
- One store instance per pack (not singleton)

### Key Difference from TASK-080

Daily cursor uses `packId` for scoping (lesson position within pack).
Lesson progress also uses `packId` for scoping (session state within pack).

**Rationale:** Lesson progress is pack-specific because:
- Different packs have different lesson structures
- Card indices are not comparable across packs
- Session state (correctCount, incorrectCount) is per-pack

### Key Difference from Verb Drill

Verb drill uses `languageId` for scoping (extracted from `packId`).
Lesson progress uses `packId` directly for scoping (more precise).

Example:
- Verb drill: `verb_drill_progress_en.yaml` (all EN packs share progress)
- Lesson progress: `lesson_progress_ru-en-v1.yaml` (per-pack progress)

**Rationale:** Lesson progress depends on pack-specific lesson structure, not just language.

---

## Related Tasks

- **TASK-080:** Daily Cursor State Isolation (pack-scoped daily cursor)
- **TASK-079:** Daily Practice Pack Switching (added `packId` to `DailySessionState`)

---

## Open Questions

1. **Q:** Should lesson progress be scoped by `packId` or `languageId`?
   **A:** `packId` - lesson structure differs per pack, even within same language. Card indices are not comparable across packs.

2. **Q:** What happens if user switches packs mid-session?
   **A:** Session is paused, progress saved for current pack. New pack's progress is loaded on switch. This is consistent with daily practice behavior (TASK-079).

3. **Q:** Should migration copy lesson progress to ALL installed packs?
   **A:** No - only to active pack. Other packs initialize to default progress on first use.

4. **Q:** What about `activeTimeMs`, `voiceActiveMs`, `voiceWordCount`, `hintCount`?
   **A:** These are session-specific metrics that should also be pack-scoped. They don't make sense to share across packs.

---

## Success Metrics

- **Primary:** Pack switching preserves lesson progress state per pack
- **Secondary:** Migration completes without data loss for existing users
- **Tertiary:** Zero regression in daily practice, verb drill, vocab drill, boss battle

---

## Completion Log

| Date | Phase | Status | Notes |
|------|-------|--------|-------|
| 2026-05-21 | Task creation | OPEN | Research complete, spec ready for implementation |
| 2026-05-21 | Wave 1: Data model + Store | DONE | Created PackLessonProgressState data class and PackLessonProgressStore interface/implementation |
| 2026-05-21 | Wave 2: Migration logic | DONE | Added migrateGlobalLessonProgressToPackScoped() method and AppRoot trigger for VERSION_081 |
| 2026-05-21 | Wave 3: TrainingViewModel integration | DONE | Updated TrainingViewModel and ProgressTracker to use pack-scoped lesson progress |
| 2026-05-21 | Wave 3: Legacy cleanup | DONE | Removed lesson progress fields from global TrainingProgress data class |
| 2026-05-21 | Full implementation | DONE | All phases complete, commit cee76d3 |

---

## References

- **TASK-080:** Daily Cursor State Isolation Bug (completed 2026-05-21)
- **TASK-079:** Daily Practice Pack Switching (added `packId` to `DailySessionState`)
- **VerbDrillStore:** Reference implementation for pack-scoped state
- **PackDailyCursorStore:** Reference implementation for per-pack file storage
- **UC-02:** Start a Training Sub-Lesson
- **UC-06:** Complete a Sub-Lesson
- **UC-22:** Resume App After Kill (State Restoration)
