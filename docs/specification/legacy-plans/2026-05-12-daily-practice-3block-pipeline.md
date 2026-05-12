# Daily Practice 3-Block Pipeline — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore Daily Practice as a 3-block pipeline: 10 sentence translations → 10 vocab flashcards → 10 verb drill cards. Total = 30 cards per session.

**Architecture:** Each block produces `List<SentenceCard>` (converting vocab/verb drill cards to SentenceCard format). Blocks are served one at a time to the existing `DailyPracticeScreen` via `getDailyCurrentCards()`. When a block completes, `advanceDailyTask()` loads the next block. Screen layer (DailyPracticeScreen, DailyPracticeSessionProvider, GrammarMateApp DAILY_PRACTICE section) is NOT modified.

**Tech Stack:** Kotlin, existing data stores (LessonStore, VerbDrillStore, WordMasteryStore, ItalianDrillVocabParser, VerbDrillCsvParser)

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `ui/helpers/DailySessionComposer.kt` | **Rewrite** | Build 3 blocks of 10 SentenceCards from lesson, vocab, verb drill data |
| `ui/helpers/DailySessionHelper.kt` | **Modify** | Track block index (0-2) instead of lesson/subLesson indices |
| `ui/helpers/DailySessionComposer.kt` | **Modify** | Current stub → full 3-block builder |
| `ui/TrainingViewModel.kt` | **Modify** | `startDailyPractice()`, `getDailyCurrentCards()`, `advanceDailyTask()`, `getDailyLessonTitle()`, `getDailySubLessonLabel()`, `getDailyProgress()` |
| `data/Models.kt` | **No change** | `DailySessionState` already has `subLessonIndex` which maps to blockIndex |

**Files NOT touched:** DailyPracticeScreen.kt, DailyPracticeSessionProvider.kt, GrammarMateApp.kt (screen routing), DailySessionState (current structure works)

---

## Task 1: Rewrite DailySessionComposer — Build 3 Blocks

**Files:**
- Modify: `app/src/main/java/com/alexpo/grammermate/ui/helpers/DailySessionComposer.kt`

The composer builds a data class holding 3 blocks of `List<SentenceCard>`. Each block has 10 cards. Vocab words and verb drill cards are converted to `SentenceCard` format.

**Block labels** shown in UI:
- Block 0: "Translations" → cards from active lesson's sub-lesson
- Block 1: "Vocabulary" → cards from vocab drill data, filtered by frequency
- Block 2: "Verbs" → cards from verb drill data, filtered by cumulative tenses

- [ ] **Step 1: Define DailySession data class and rewrite DailySessionComposer**

Replace the full content of `DailySessionComposer.kt` with:

```kotlin
package com.alexpo.grammermate.ui.helpers

import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.ItalianDrillVocabParser
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.MixedReviewScheduler
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.VerbDrillCsvParser
import com.alexpo.grammermate.data.VerbDrillStore
import com.alexpo.grammermate.data.WordMasteryStore

data class DailyBlocks(
    val blocks: List<DailyBlock>
) {
    val totalBlocks: Int get() = blocks.size
    fun blockAt(index: Int): DailyBlock? = blocks.getOrNull(index)
}

data class DailyBlock(
    val label: String,
    val cards: List<SentenceCard>
)

class DailySessionComposer(
    private val lessonStore: LessonStore,
    private val verbDrillStore: VerbDrillStore,
    private val wordMasteryStore: WordMasteryStore
) {
    companion object {
        const val CARDS_PER_BLOCK = 10
    }

    fun build(
        packId: String,
        languageId: String,
        lessonLevel: Int,
        lessons: List<com.alexpo.grammermate.data.Lesson>
    ): DailyBlocks {
        val blocks = mutableListOf<DailyBlock>()

        // Block 0: Sentence translations from active lesson
        blocks.add(buildTranslationBlock(lessons, languageId))

        // Block 1: Vocab flashcards from drill data
        blocks.add(buildVocabBlock(packId, languageId, lessonLevel))

        // Block 2: Verb drill cards
        blocks.add(buildVerbBlock(packId, languageId, lessonLevel))

        return DailyBlocks(blocks)
    }

    private fun buildTranslationBlock(
        lessons: List<com.alexpo.grammermate.data.Lesson>,
        languageId: String
    ): DailyBlock {
        // Find the first lesson that has cards
        val lesson = lessons.firstOrNull { it.cards.isNotEmpty() } ?: return DailyBlock("Translations", emptyList())

        // Build sub-lessons via MixedReviewScheduler and take first sub-lesson's cards
        val blockSize = TrainingConfig.SUB_LESSON_SIZE_DEFAULT
        val schedules = MixedReviewScheduler(blockSize).build(listOf(lesson))
        val schedule = schedules[lesson.id] ?: return DailyBlock("Translations", emptyList())
        val subLesson = schedule.subLessons.firstOrNull() ?: return DailyBlock("Translations", emptyList())

        val cards = subLesson.cards.shuffled().take(CARDS_PER_BLOCK)
        return DailyBlock("Translations", cards)
    }

    private fun buildVocabBlock(packId: String, languageId: String, lessonLevel: Int): DailyBlock {
        val vocabFiles = lessonStore.getVocabDrillFiles(packId, languageId)
        if (vocabFiles.isEmpty()) return DailyBlock("Vocabulary", emptyList())

        val allWords = mutableListOf<ItalianDrillVocabParser.ItalianDrillRow>()
        for (file in vocabFiles) {
            val rows = ItalianDrillVocabParser.parse(file.readText())
            allWords.addAll(rows)
        }

        // Filter by frequency rank based on lesson level
        // Each level covers a range: level 1 = ranks 1-50, level 2 = ranks 1-100, etc.
        val maxRank = lessonLevel * 50
        val eligible = allWords
            .filter { it.rank == null || it.rank <= maxRank }
            .shuffled()
            .take(CARDS_PER_BLOCK)

        if (eligible.isEmpty()) return DailyBlock("Vocabulary", emptyList())

        // Convert vocab rows to SentenceCard (RU meaning → IT word)
        val cards = eligible.mapIndexed { idx, row ->
            SentenceCard(
                id = "vocab_${row.rank}_${idx}",
                promptRu = row.meaningRu.ifBlank { row.word },
                acceptedAnswers = listOf(row.word),
                tense = row.posLabel
            )
        }
        return DailyBlock("Vocabulary", cards)
    }

    private fun buildVerbBlock(packId: String, languageId: String, lessonLevel: Int): DailyBlock {
        val cumulativeTenses = lessonStore.getCumulativeTenses(packId, lessonLevel)
        val verbFiles = lessonStore.getVerbDrillFiles(packId, languageId)
        if (verbFiles.isEmpty() || cumulativeTenses.isEmpty()) {
            return DailyBlock("Verbs", emptyList())
        }

        // Parse all verb drill cards
        val allCards = mutableListOf<com.alexpo.grammermate.data.VerbDrillCard>()
        for (file in verbFiles) {
            val (_, cards) = VerbDrillCsvParser.parse(file.readText())
            allCards.addAll(cards)
        }

        // Filter by cumulative tenses
        val eligible = allCards
            .filter { it.tense != null && it.tense in cumulativeTenses }
            .shuffled()
            .take(CARDS_PER_BLOCK)

        if (eligible.isEmpty()) return DailyBlock("Verbs", emptyList())

        // Convert VerbDrillCard to SentenceCard (they already have promptRu and acceptedAnswers)
        val cards = eligible.map { card ->
            SentenceCard(
                id = "verb_${card.id}",
                promptRu = card.promptRu,
                acceptedAnswers = listOf(card.answer),
                tense = card.tense
            )
        }
        return DailyBlock("Verbs", cards)
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain compileDebugKotlin 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

---

## Task 2: Update DailySessionHelper — 3-Block Tracking

**Files:**
- Modify: `app/src/main/java/com/alexpo/grammermate/ui/helpers/DailySessionHelper.kt`

The helper tracks which block (0-2) the user is on, instead of iterating through lesson/sub-lesson indices.

- [ ] **Step 1: Rewrite DailySessionHelper**

Replace the full content of `DailySessionHelper.kt` with:

```kotlin
package com.alexpo.grammermate.ui.helpers

import com.alexpo.grammermate.ui.TrainingUiState
import kotlinx.coroutines.flow.StateFlow

interface TrainingStateAccess {
    val uiState: StateFlow<TrainingUiState>
    fun updateState(transform: (TrainingUiState) -> TrainingUiState)
    fun saveProgress()
}

class DailySessionHelper(
    private val stateAccess: TrainingStateAccess
) {
    fun startDailySession(totalBlocks: Int) {
        stateAccess.updateState { state ->
            state.copy(
                dailySession = state.dailySession.copy(
                    active = true,
                    lessonIndex = 0,
                    subLessonIndex = 0,
                    totalSubLessons = totalBlocks,
                    globalSubLessonPosition = 1,
                    finishedToken = false
                )
            )
        }
        stateAccess.saveProgress()
    }

    fun getCurrentBlockIndex(): Int {
        return stateAccess.uiState.value.dailySession.subLessonIndex
    }

    fun advanceToNextBlock(): Boolean {
        val ds = stateAccess.uiState.value.dailySession
        if (!ds.active) return false

        val nextBlock = ds.subLessonIndex + 1
        if (nextBlock >= ds.totalSubLessons) {
            endSession()
            return false
        }

        stateAccess.updateState {
            it.copy(
                dailySession = it.dailySession.copy(
                    subLessonIndex = nextBlock,
                    globalSubLessonPosition = nextBlock + 1
                )
            )
        }
        stateAccess.saveProgress()
        return true
    }

    fun endSession() {
        stateAccess.updateState { state ->
            state.copy(
                dailySession = state.dailySession.copy(
                    active = false,
                    finishedToken = true
                )
            )
        }
        stateAccess.saveProgress()
    }

    fun isSessionComplete(): Boolean {
        val ds = stateAccess.uiState.value.dailySession
        return !ds.active || ds.finishedToken
    }

    fun getProgress(): DailyPipelineProgress {
        val ds = stateAccess.uiState.value.dailySession
        return DailyPipelineProgress(
            lessonIndex = 0,
            subLessonIndex = ds.subLessonIndex,
            totalSubLessons = ds.totalSubLessons,
            globalPosition = ds.globalSubLessonPosition
        )
    }
}

data class DailyPipelineProgress(
    val lessonIndex: Int,
    val subLessonIndex: Int,
    val totalSubLessons: Int,
    val globalPosition: Int
)
```

- [ ] **Step 2: Verify compilation**

Run: `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain compileDebugKotlin 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

---

## Task 3: Update TrainingViewModel — Daily Practice Methods

**Files:**
- Modify: `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt`

Update `startDailyPractice()`, `getDailyCurrentCards()`, `advanceDailyTask()`, `getDailyLessonTitle()`, `getDailySubLessonLabel()`, `getDailyProgress()`. Add a field to cache the `DailyBlocks`.

- [ ] **Step 1: Add DailyBlocks cache field**

In TrainingViewModel, find the `dailySessionHelper` field (around line 125) and add after it:

```kotlin
    private var dailyBlocks: DailyBlocks? = null
    private val dailyComposer = DailySessionComposer(lessonStore, VerbDrillStore(application), wordMasteryStore)
```

Note: `VerbDrillStore` needs `packId` in its constructor. Check the constructor signature. If it requires `packId`, create it lazily inside `startDailyPractice()` where packId is available.

- [ ] **Step 2: Rewrite startDailyPractice()**

Replace the current `startDailyPractice()` method (around line 1353) with:

```kotlin
    fun startDailyPractice(): Boolean {
        val state = _uiState.value
        val packId = state.activePackId ?: return false
        val langId = state.selectedLanguageId
        val lessons = state.lessons
        if (lessons.isEmpty()) return false

        // Derive lesson level from lesson index
        val selectedLessonId = state.selectedLessonId
        val lessonLevel = (lessons.indexOfFirst { it.id == selectedLessonId } + 1)
            .coerceIn(1, 12)

        val verbDrillStore = VerbDrillStore(getApplication(), packId = packId)
        val composer = DailySessionComposer(lessonStore, verbDrillStore, wordMasteryStore)
        val blocks = composer.build(packId, langId, lessonLevel, lessons)

        if (blocks.blocks.all { it.cards.isEmpty() }) return false

        dailyBlocks = blocks
        dailySessionHelper.startDailySession(blocks.totalBlocks)
        return true
    }
```

- [ ] **Step 3: Rewrite getDailyCurrentCards()**

Replace the current `getDailyCurrentCards()` (around line 1418) with:

```kotlin
    fun getDailyCurrentCards(): List<SentenceCard> {
        val ds = _uiState.value.dailySession
        if (!ds.active) return emptyList()
        val blocks = dailyBlocks ?: return emptyList()
        val block = blocks.blockAt(ds.subLessonIndex) ?: return emptyList()
        val hiddenIds = hiddenCardStore.getHiddenCardIds()
        return block.cards.filter { it.id !in hiddenIds }
    }
```

- [ ] **Step 4: Rewrite advanceDailyTask()**

Replace the current `advanceDailyTask()` (around line 1399) with:

```kotlin
    fun advanceDailyTask(): Boolean {
        return dailySessionHelper.advanceToNextBlock()
    }
```

- [ ] **Step 5: Update getDailyLessonTitle() and getDailySubLessonLabel()**

Replace (around line 1430):

```kotlin
    fun getDailyLessonTitle(): String {
        return "Daily Practice"
    }

    fun getDailySubLessonLabel(): String {
        val ds = _uiState.value.dailySession
        val block = dailyBlocks?.blockAt(ds.subLessonIndex)
        return block?.label ?: "Block ${ds.subLessonIndex + 1}"
    }
```

- [ ] **Step 6: Update getDailyProgress()**

The current `getDailyProgress()` calls `dailySessionHelper.getProgress()` — no change needed, but verify it still compiles with the new DailySessionHelper signature.

- [ ] **Step 7: Clean up unused imports**

Remove any imports that were only used by the old MixedReviewScheduler-based daily practice code (e.g., `LessonSchedule` if only used in daily methods).

- [ ] **Step 8: Build and verify**

Run: `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

---

## Task 4: Fix Edge Cases and Empty Blocks

**Files:**
- Modify: `app/src/main/java/com/alexpo/grammermate/ui/helpers/DailySessionComposer.kt`
- Modify: `app/src/main/java/com/alexpo/grammermate/ui/TrainingViewModel.kt`

Handle cases where vocab or verb drill data is not available for the active pack.

- [ ] **Step 1: Handle partially empty DailyBlocks**

In `DailySessionComposer.build()`, filter out blocks with zero cards:

```kotlin
    fun build(...): DailyBlocks {
        val blocks = mutableListOf<DailyBlock>()
        blocks.add(buildTranslationBlock(lessons, languageId))
        blocks.add(buildVocabBlock(packId, languageId, lessonLevel))
        blocks.add(buildVerbBlock(packId, languageId, lessonLevel))

        // Only keep blocks that have cards
        val nonEmptyBlocks = blocks.filter { it.cards.isNotEmpty() }
        return DailyBlocks(nonEmptyBlocks)
    }
```

- [ ] **Step 2: Handle "no blocks at all" in TrainingViewModel**

In `startDailyPractice()`, check after filtering:

```kotlin
        if (blocks.blocks.isEmpty()) return false
```

This is already handled by `blocks.blocks.all { it.cards.isEmpty() }` but the filter in Step 1 makes this check cleaner:

```kotlin
        if (blocks.totalBlocks == 0) return false
```

- [ ] **Step 3: Build and verify**

Run: `java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

---

## Task 5: Manual Testing Checklist

- [ ] **Start Daily Practice from HOME** → should show 10 translation cards
- [ ] **Complete all 10 translations** → should advance to vocab block (label changes to "Vocabulary")
- [ ] **Complete all 10 vocab cards** → should advance to verb block (label changes to "Verbs")
- [ ] **Complete all 10 verb cards** → should show CompletionSparkle then CompletionScreen
- [ ] **Progress bar** shows "1/3", "2/3", "3/3" across blocks
- [ ] **Exit mid-session** → returns to HOME, session cancelled
- [ ] **Restart app** → always starts on HOME (no stuck loader, no direct DAILY_PRACTICE)
- [ ] **Pack without vocab/verb drill** → only translation block shown (1/1)

---

## What GrammarMateApp.kt looks like (NO CHANGES needed)

The current DAILY_PRACTICE section in GrammarMateApp already calls:
- `vm.getDailyCurrentCards()` → returns current block's SentenceCards ✓
- `vm.getDailyLessonTitle()` → returns "Daily Practice" ✓
- `vm.getDailySubLessonLabel()` → returns block label (Translations/Vocabulary/Verbs) ✓
- `vm.getDailyProgress()` → returns DailyPipelineProgress with correct block indices ✓
- `vm::advanceDailyTask` → advances to next block ✓
- `vm.onDailyAnswerChecked()` → records mastery ✓
- `vm.cancelDailySession()` → ends session ✓

No changes to GrammarMateApp, DailyPracticeScreen, or DailyPracticeSessionProvider.

---

## Self-Review Checklist

- [x] **Spec coverage:** 3 blocks of 10 cards each → Tasks 1-4
- [x] **No placeholders:** All code is complete in every step
- [x] **Type consistency:** `DailyBlocks`, `DailyBlock`, `DailyPipelineProgress` defined consistently across tasks
- [x] **Screen forms untouched:** DailyPracticeScreen.kt, DailyPracticeSessionProvider.kt, GrammarMateApp.kt not modified
- [x] **Edge cases:** Empty vocab/verb blocks handled in Task 4
- [x] **Start always HOME:** Already fixed in previous commit (restoredScreen = "HOME")
- [x] **File size limits:** DailySessionComposer ~120 lines, DailySessionHelper ~80 lines — well within limits
