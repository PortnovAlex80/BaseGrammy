package com.alexpo.grammermate.ui.helpers

import android.app.Application
import android.util.Log
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.DailyCursorState
import com.alexpo.grammermate.data.DailySessionState
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.data.SpacedRepetitionConfig
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillComboProgress
import com.alexpo.grammermate.data.VerbDrillStore
import com.alexpo.grammermate.data.WordMasteryState
import com.alexpo.grammermate.data.WordMasteryStore
import com.alexpo.grammermate.ui.TrainingUiState

/**
 * Stateful module that orchestrates the 3-block daily practice session
 * (Translate, Vocab, Verbs).
 *
 * Absorbs [DailySessionHelper] + [DailySessionComposer] + per-call store creation
 * into a single coherent module. The ViewModel delegates all daily-practice
 * methods here and applies results back to state.
 *
 * Cross-module calls (advanceCursor -> ProgressTracker, playSuccessSound ->
 * AudioCoordinator) are NOT called directly. The ViewModel orchestrates these
 * by inspecting results returned from this coordinator.
 */
class DailyPracticeCoordinator(
    private val stateAccess: TrainingStateAccess,
    private val appContext: Application,
    private val answerValidator: AnswerValidator,
    private val lessonStore: LessonStore,
    private val masteryStore: MasteryStore
) {

    private val logTag = "GrammarMate"

    // ── Owned private mutable state ────────────────────────────────────

    /** Pre-computed daily session from background init. */
    var prebuiltDailySession: List<DailyTask>? = null
        private set

    /** In-memory cache for repeat. */
    var lastDailyTasks: List<DailyTask>? = null
        private set

    /** Per-block VOICE/KEYBOARD answered card counts for cursor advancement. */
    private var dailyPracticeAnsweredCounts: MutableMap<DailyBlockType, Int> = mutableMapOf()

    /** Cursor state saved at session start; used to roll back on cancel. */
    private var dailyCursorAtSessionStart: DailyCursorState = DailyCursorState()

    // ── Drill store factory (per-pack caching) ─────────────────────────

    private var cachedVerbDrillStore: VerbDrillStore? = null
    private var cachedWordMasteryStore: WordMasteryStore? = null
    private var cachedPackId: String? = null

    fun getVerbDrillStore(packId: String): VerbDrillStore {
        if (cachedPackId != packId) {
            cachedVerbDrillStore = null
            cachedWordMasteryStore = null
            cachedPackId = packId
        }
        return cachedVerbDrillStore ?: VerbDrillStore(appContext, packId = packId).also {
            cachedVerbDrillStore = it
        }
    }

    fun getWordMasteryStore(packId: String): WordMasteryStore {
        if (cachedPackId != packId) {
            cachedVerbDrillStore = null
            cachedWordMasteryStore = null
            cachedPackId = packId
        }
        return cachedWordMasteryStore ?: WordMasteryStore(appContext, packId = packId).also {
            cachedWordMasteryStore = it
        }
    }

    // ── Internal helpers (absorbed from DailySessionHelper) ────────────

    private fun startDailySession(tasks: List<DailyTask>, lessonLevel: Int) {
        if (tasks.isEmpty()) return
        stateAccess.updateState { state ->
            state.copy(
                dailySession = DailySessionState(
                    active = true,
                    tasks = tasks,
                    taskIndex = 0,
                    blockIndex = 0,
                    level = lessonLevel,
                    finishedToken = false
                )
            )
        }
        stateAccess.saveProgress()
    }

    fun getCurrentTask(): DailyTask? {
        val ds = stateAccess.uiState.value.dailySession
        if (!ds.active) return null
        return ds.tasks.getOrNull(ds.taskIndex)
    }

    fun getCurrentBlockType(): DailyBlockType? {
        return getCurrentTask()?.blockType
    }

    private fun nextTask(): Boolean {
        val ds = stateAccess.uiState.value.dailySession
        if (!ds.active) return false

        val nextIndex = ds.taskIndex + 1
        if (nextIndex >= ds.tasks.size) {
            endSession()
            return false
        }

        val currentBlock = getCurrentBlockType()
        val nextBlock = ds.tasks.getOrNull(nextIndex)?.blockType
        val nextBlockIndex = if (nextBlock != currentBlock) ds.blockIndex + 1 else ds.blockIndex

        stateAccess.updateState {
            it.copy(
                dailySession = it.dailySession.copy(
                    taskIndex = nextIndex,
                    blockIndex = nextBlockIndex
                )
            )
        }
        stateAccess.saveProgress()
        return true
    }

    fun advanceToNextBlock(): Boolean {
        val ds = stateAccess.uiState.value.dailySession
        if (!ds.active) return false

        val currentBlock = getCurrentBlockType() ?: return false
        var idx = ds.taskIndex
        while (idx < ds.tasks.size && ds.tasks[idx].blockType == currentBlock) {
            idx++
        }

        if (idx >= ds.tasks.size) {
            endSession()
            return false
        }

        val nextBlockIndex = ds.blockIndex + 1

        stateAccess.updateState {
            it.copy(
                dailySession = it.dailySession.copy(
                    taskIndex = idx,
                    blockIndex = nextBlockIndex
                )
            )
        }
        stateAccess.saveProgress()
        return true
    }

    fun replaceCurrentBlock(newTasks: List<DailyTask>) {
        val ds = stateAccess.uiState.value.dailySession
        if (!ds.active) return

        val currentBlock = getCurrentBlockType() ?: return

        var blockStart = ds.taskIndex
        for (i in ds.tasks.indices) {
            if (ds.tasks[i].blockType == currentBlock) {
                blockStart = i
                break
            }
        }
        var blockEnd = blockStart
        for (i in blockStart until ds.tasks.size) {
            if (ds.tasks[i].blockType != currentBlock) break
            blockEnd = i
        }

        val newTaskList = ds.tasks.subList(0, blockStart) +
            newTasks +
            ds.tasks.subList(blockEnd + 1, ds.tasks.size)

        stateAccess.updateState {
            it.copy(
                dailySession = it.dailySession.copy(
                    tasks = newTaskList,
                    taskIndex = blockStart
                )
            )
        }
        stateAccess.saveProgress()
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

    fun getBlockProgress(): BlockProgress {
        val ds = stateAccess.uiState.value.dailySession
        if (!ds.active) return BlockProgress.Empty

        val tasks = ds.tasks
        val currentBlock = getCurrentBlockType() ?: return BlockProgress.Empty

        var blockStart = 0
        for (i in tasks.indices) {
            if (tasks[i].blockType == currentBlock) {
                blockStart = i
                break
            }
        }

        var blockEnd = blockStart
        for (i in blockStart until tasks.size) {
            if (tasks[i].blockType != currentBlock) break
            blockEnd = i
        }
        val blockSize = blockEnd - blockStart + 1
        val positionInBlock = ds.taskIndex - blockStart + 1

        return BlockProgress(
            blockType = currentBlock,
            positionInBlock = positionInBlock.coerceIn(1, blockSize),
            blockSize = blockSize,
            totalTasks = tasks.size,
            globalPosition = ds.taskIndex + 1
        )
    }

    // ── Session start / resume ─────────────────────────────────────────

    fun hasResumableDailySession(): Boolean {
        val cursor = stateAccess.uiState.value.dailyCursor
        val today = java.time.LocalDate.now().toString()
        return cursor.firstSessionDate == today &&
            (cursor.firstSessionSentenceCardIds.isNotEmpty() ||
                cursor.firstSessionVerbCardIds.isNotEmpty())
    }

    /**
     * Start a new daily practice session.
     *
     * @param resolveProgressLessonInfo returns (lessonId, level) based on mastery progress.
     * @param onStoreFirstSessionCardIds callback to store first-session card IDs (delegates to ProgressTracker).
     * @return true if session was started successfully.
     */
    fun startDailyPractice(
        resolveProgressLessonInfo: () -> Pair<String, Int>?,
        onStoreFirstSessionCardIds: (sentenceIds: List<String>, verbIds: List<String>) -> Unit
    ): Boolean {
        // Save cursor at session start for rollback on cancel
        dailyCursorAtSessionStart = stateAccess.uiState.value.dailyCursor
        // Reset per-block VOICE/KEYBOARD answered counters
        dailyPracticeAnsweredCounts = mutableMapOf()

        val state = stateAccess.uiState.value
        val packId = state.activePackId ?: return false
        val langId = state.selectedLanguageId

        val progressInfo = resolveProgressLessonInfo()
        val lessonId = progressInfo?.first ?: return false
        val effectiveLevel = progressInfo.second

        val cursor = state.dailyCursor
        val today = java.time.LocalDate.now().toString()
        val isFirstSessionToday = cursor.firstSessionDate != today

        // Try pre-built session first (only valid for first session of the day)
        val cached = prebuiltDailySession
        if (isFirstSessionToday && cached != null && cached.isNotEmpty()) {
            lastDailyTasks = cached
            startDailySession(cached, effectiveLevel)
            prebuiltDailySession = null
            val sentenceIds = cached
                .filterIsInstance<DailyTask.TranslateSentence>()
                .map { it.card.id }
            val verbIds = cached
                .filterIsInstance<DailyTask.ConjugateVerb>()
                .map { it.card.id }
            onStoreFirstSessionCardIds(sentenceIds, verbIds)
            return true
        }

        // Fallback to synchronous build
        val verbDrillStore = getVerbDrillStore(packId)
        val packWordMasteryStore = getWordMasteryStore(packId)
        val cumulativeTenses = lessonStore.getCumulativeTenses(packId, effectiveLevel)
        val composer = DailySessionComposer(lessonStore, verbDrillStore, packWordMasteryStore)
        val tasks = composer.buildSession(effectiveLevel, packId, langId, lessonId, cumulativeTenses, cursor)
        Log.d(logTag, "DailyPractice fallback: built ${tasks.size} tasks, per-block=${tasks.groupBy { it.blockType }.mapValues { it.value.size }}")
        if (tasks.isEmpty()) return false

        lastDailyTasks = tasks
        startDailySession(tasks, effectiveLevel)

        if (isFirstSessionToday) {
            val sentenceIds = tasks
                .filterIsInstance<DailyTask.TranslateSentence>()
                .map { it.card.id }
            val verbIds = tasks
                .filterIsInstance<DailyTask.ConjugateVerb>()
                .map { it.card.id }
            onStoreFirstSessionCardIds(sentenceIds, verbIds)
        }
        return true
    }

    fun repeatDailyPractice(
        lessonLevel: Int,
        resolveProgressLessonInfo: () -> Pair<String, Int>?
    ): Boolean {
        val state = stateAccess.uiState.value
        val packId = state.activePackId ?: return false
        val langId = state.selectedLanguageId

        val progressInfo = resolveProgressLessonInfo()
        val lessonId = progressInfo?.first ?: return false

        val cursor = state.dailyCursor
        val today = java.time.LocalDate.now().toString()

        // Try in-memory cache first (fastest path, same app run)
        val cached = lastDailyTasks
        if (cached != null && cached.isNotEmpty()) {
            startDailySession(cached, lessonLevel)
            return true
        }

        // Reconstruct from stored first-session card IDs
        if (cursor.firstSessionDate == today &&
            (cursor.firstSessionSentenceCardIds.isNotEmpty() || cursor.firstSessionVerbCardIds.isNotEmpty())
        ) {
            val cumulativeTenses = lessonStore.getCumulativeTenses(packId, lessonLevel)
            val verbDrillStore = getVerbDrillStore(packId)
            val packWordMasteryStore = getWordMasteryStore(packId)
            val composer = DailySessionComposer(lessonStore, verbDrillStore, packWordMasteryStore)
            val tasks = composer.buildRepeatSession(
                lessonLevel, packId, langId, lessonId, cumulativeTenses,
                sentenceCardIds = cursor.firstSessionSentenceCardIds,
                verbCardIds = cursor.firstSessionVerbCardIds
            )
            if (tasks.isNotEmpty()) {
                lastDailyTasks = tasks
                startDailySession(tasks, lessonLevel)
                return true
            }
        }

        // Last resort: build fresh with cursor at position 0 (start of day)
        val resetCursor = cursor.copy(sentenceOffset = 0)
        val verbDrillStore = getVerbDrillStore(packId)
        val packWordMasteryStore = getWordMasteryStore(packId)
        val cumulativeTenses = lessonStore.getCumulativeTenses(packId, lessonLevel)
        val composer = DailySessionComposer(lessonStore, verbDrillStore, packWordMasteryStore)
        val tasks = composer.buildSession(lessonLevel, packId, langId, lessonId, cumulativeTenses, resetCursor)
        if (tasks.isEmpty()) return false

        lastDailyTasks = tasks
        startDailySession(tasks, lessonLevel)
        return true
    }

    // ── Task / block navigation ────────────────────────────────────────

    fun advanceDailyTask(
        onPersistVerbProgress: (VerbDrillCard) -> Unit
    ): Boolean {
        val task = getCurrentTask()
        if (task is DailyTask.ConjugateVerb) {
            onPersistVerbProgress(task.card)
        }
        return nextTask()
    }

    fun recordDailyCardPracticed(
        blockType: DailyBlockType,
        resolveCardLessonId: (card: com.alexpo.grammermate.data.SentenceCard) -> String
    ) {
        val count = dailyPracticeAnsweredCounts[blockType] ?: 0
        dailyPracticeAnsweredCounts[blockType] = count + 1

        if (blockType == DailyBlockType.TRANSLATE) {
            val task = getCurrentTask() as? DailyTask.TranslateSentence
            if (task != null) {
                val card = task.card
                val lessonId = resolveCardLessonId(card)
                val languageId = stateAccess.uiState.value.selectedLanguageId
                masteryStore.recordCardShow(lessonId, languageId, card.id)
            }
        }
    }

    fun advanceDailyBlock(): Boolean {
        return advanceToNextBlock()
    }

    fun persistDailyVerbProgress(card: VerbDrillCard) {
        val packId = stateAccess.uiState.value.activePackId ?: return
        val store = getVerbDrillStore(packId)
        val comboKey = "${card.group ?: ""}|${card.tense ?: ""}"
        val existing = store.loadProgress()[comboKey]
        val everShown = (existing?.everShownCardIds ?: emptySet()) + card.id
        val todayShown = (existing?.todayShownCardIds ?: emptySet()) + card.id
        val updated = VerbDrillComboProgress(
            group = card.group ?: "",
            tense = card.tense ?: "",
            totalCards = existing?.totalCards ?: 0,
            everShownCardIds = everShown,
            todayShownCardIds = todayShown,
            lastDate = java.time.LocalDate.now().toString()
        )
        store.upsertComboProgress(comboKey, updated)
    }

    fun repeatDailyBlock(
        resolveProgressLessonInfo: () -> Pair<String, Int>?
    ): Boolean {
        val state = stateAccess.uiState.value
        val ds = state.dailySession
        if (!ds.active) return false
        val blockType = getCurrentBlockType() ?: return false
        val packId = state.activePackId ?: return false
        val langId = state.selectedLanguageId

        val progressInfo = resolveProgressLessonInfo()
        val lessonId = progressInfo?.first ?: return false
        val lessonLevel = ds.level

        val verbDrillStore = getVerbDrillStore(packId)
        val packWordMasteryStore = getWordMasteryStore(packId)
        val cumulativeTenses = lessonStore.getCumulativeTenses(packId, lessonLevel)
        val composer = DailySessionComposer(lessonStore, verbDrillStore, packWordMasteryStore)
        val newTasks = composer.rebuildBlock(blockType, lessonLevel, packId, langId, lessonId, cumulativeTenses)
        if (newTasks.isEmpty()) return false

        replaceCurrentBlock(newTasks)
        return true
    }

    /**
     * Cancel the daily session. Conditionally advances cursor based on
     * VOICE/KEYBOARD completion of TRANSLATE and VERBS blocks.
     *
     * @return the sentence count to advance cursor by, or null if no advancement needed.
     */
    fun cancelDailySession(): Int? {
        val ds = stateAccess.uiState.value.dailySession
        var sentenceCountToAdvance: Int? = null

        if (ds.finishedToken) {
            val sentenceCount = dailyPracticeAnsweredCounts[DailyBlockType.TRANSLATE] ?: 0
            val verbCount = dailyPracticeAnsweredCounts[DailyBlockType.VERBS] ?: 0
            val expectedSentenceCount = ds.tasks.count { it is DailyTask.TranslateSentence }
            val expectedVerbCount = ds.tasks.count { it is DailyTask.ConjugateVerb }
            val allSentencePracticed = sentenceCount >= expectedSentenceCount
            val allVerbsPracticed = verbCount >= expectedVerbCount
            if (allSentencePracticed && allVerbsPracticed) {
                sentenceCountToAdvance = sentenceCount
            }
        }
        dailyPracticeAnsweredCounts.clear()
        endSession()
        return sentenceCountToAdvance
    }

    // ── Vocab SRS ──────────────────────────────────────────────────────

    fun rateVocabCard(rating: Int) {
        val task = getCurrentTask() as? DailyTask.VocabFlashcard ?: return
        val wordId = task.word.id
        val state = stateAccess.uiState.value
        val packId = state.activePackId ?: return
        val store = getWordMasteryStore(packId)
        val current = store.getMastery(wordId) ?: WordMasteryState.new(wordId)
        val now = System.currentTimeMillis()
        val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        val newStepIndex = when (rating) {
            0 -> 0  // Again - reset
            1 -> current.intervalStepIndex  // Hard - stay
            2 -> (current.intervalStepIndex + 1).coerceIn(0, maxStep)  // Good - +1
            else -> (current.intervalStepIndex + 2).coerceIn(0, maxStep)  // Easy - +2
        }
        val newNextReview = WordMasteryState.computeNextReview(now, newStepIndex)
        val LEARNED_THRESHOLD = 3
        val isLearned = newStepIndex >= LEARNED_THRESHOLD
        val updated = current.copy(
            intervalStepIndex = newStepIndex,
            correctCount = current.correctCount + (if (rating != 0) 1 else 0),
            incorrectCount = current.incorrectCount + (if (rating == 0) 1 else 0),
            lastReviewDateMs = now,
            nextReviewDateMs = newNextReview,
            isLearned = isLearned
        )
        store.upsertMastery(updated)
    }

    // ── Current state queries ──────────────────────────────────────────

    fun getDailyCurrentTask(): DailyTask? = getCurrentTask()

    fun getDailyBlockProgress(): BlockProgress = getBlockProgress()

    // ── Answer submission ──────────────────────────────────────────────

    fun submitDailySentenceAnswer(input: String): Boolean {
        val task = getCurrentTask() as? DailyTask.TranslateSentence ?: return false
        val card = task.card
        return answerValidator.validate(input, card.acceptedAnswers).isCorrect
    }

    fun submitDailyVerbAnswer(input: String): Boolean {
        val task = getCurrentTask() as? DailyTask.ConjugateVerb ?: return false
        val card = task.card
        return answerValidator.validate(input, card.acceptedAnswers).isCorrect
    }

    // ── Answer retrieval ───────────────────────────────────────────────

    fun getDailySentenceAnswer(): String? {
        val task = getCurrentTask() as? DailyTask.TranslateSentence ?: return null
        return task.card.acceptedAnswers.firstOrNull()
    }

    fun getDailyVerbAnswer(): String? {
        val task = getCurrentTask() as? DailyTask.ConjugateVerb ?: return null
        return task.card.answer
    }

    // ── Pre-build session (for background init) ────────────────────────

    /**
     * Build a daily practice session in the background for faster start.
     * Called from the ViewModel's init block on a background thread.
     */
    fun prebuildSession(
        packId: String,
        langId: String,
        lessonId: String,
        lessonLevel: Int,
        cursor: DailyCursorState
    ) {
        val verbDrillStore = getVerbDrillStore(packId)
        val packWordMasteryStore = getWordMasteryStore(packId)
        val cumulativeTenses = lessonStore.getCumulativeTenses(packId, lessonLevel)
        val composer = DailySessionComposer(lessonStore, verbDrillStore, packWordMasteryStore)
        val tasks = composer.buildSession(lessonLevel, packId, langId, lessonId, cumulativeTenses, cursor)
        if (tasks.isNotEmpty()) {
            prebuiltDailySession = tasks
        }
    }

    // ── Reset (for resetAllProgress) ───────────────────────────────────

    fun resetState() {
        lastDailyTasks = null
        prebuiltDailySession = null
        dailyPracticeAnsweredCounts.clear()
    }

    /**
     * Clear the prebuilt session cache. Called when prebuilt data is consumed
     * during startDailyPractice or when it should be discarded.
     */
    fun clearPrebuiltSession() {
        prebuiltDailySession = null
    }
}
