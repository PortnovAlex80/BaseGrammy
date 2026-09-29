package com.alexpo.grammermate.feature.daily

import android.app.Application
import android.util.Log
import com.alexpo.grammermate.data.BlockRenderVia
import com.alexpo.grammermate.data.DailyBlock
import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.DailyCursorState
import com.alexpo.grammermate.data.DailyPracticeState
import com.alexpo.grammermate.data.DailySessionState
import com.alexpo.grammermate.data.DailyTask
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.data.PackDailyCursorState
import com.alexpo.grammermate.data.PackDailyCursorStore
import com.alexpo.grammermate.data.PackId
import com.alexpo.grammermate.data.SrsRating
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillStore
import com.alexpo.grammermate.data.WordMasteryStore
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.StreakStore
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.feature.training.AnswerValidator
import com.alexpo.grammermate.shared.TrainingStateAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Exception thrown when the active pack changes during a daily practice session.
 * This signals that the current session is invalid and must be rebuilt.
 */
class SessionInvalidatedException(message: String) : Exception(message)

/**
 * Stateful module that orchestrates the 3-block daily practice session
 * (Translate, Vocab, Verbs) using a clean block-config model.
 *
 * ## Architecture
 *
 * The session is an ordered list of [DailyBlock]s. The coordinator:
 * 1. Builds blocks via [DailySessionComposer]
 * 2. Tracks [DailySessionState.blockIndex] to know which block is current
 * 3. Provides block-type-specific rendering hints ([BlockRenderVia])
 * 4. Signals block completion through a single [onBlockComplete] path
 *
 * ## Block rendering
 * - TRANSLATE / VERBS: render via TrainingScreen (navigate away)
 * - VOCAB: render inline within DailyPracticeScreen
 *
 * ## Completion flow
 * - TRANSLATE/VERBS: TrainingScreen finishes -> GrammarMateApp detects daily mode
 *   -> coordinator.onBlockComplete() -> startNextBlock()
 * - VOCAB: DailyPracticeScreen calls coordinator.onBlockComplete() -> startNextBlock()
 *
 * ## Progress tracking
 * All progress tracking (streaks, daily answered counts, verb progress, mastery)
 * is session-scoped and independent of where training was invoked from.
 */
class DailyPracticeCoordinator(
    private val stateAccess: TrainingStateAccess,
    private val appContext: Application,
    private val answerValidator: AnswerValidator,
    private val lessonStore: LessonStore,
    private val masteryStore: MasteryStore,
    private val verbDrillStoreFactory: (String?) -> VerbDrillStore,
    private val wordMasteryStoreFactory: (String?) -> WordMasteryStore,
    private val streakStore: StreakStore,
    private val streakManager: StreakManager,
    private val packDailyCursorStore: PackDailyCursorStore,
    private var sessionSize: Int = 10
) {

    private val logTag = "GrammarMate"
    private val cursorController = DailyCursorController(packDailyCursorStore, lessonStore)
    private val taskProgress = DailyTaskProgressRecorder(masteryStore, verbDrillStoreFactory, wordMasteryStoreFactory)
    private val streakCompletion = DailyStreakCompletion(stateAccess, streakStore, streakManager)

    // ── Owned state flow ─────────────────────────────────────────────────
    private val _state = MutableStateFlow(DailyPracticeState())
    val dailyState: StateFlow<DailyPracticeState> = _state

    // ── Owned private mutable state ────────────────────────────────────

    private val planCache = DailySessionPlanCache()

    /** Per-block VOICE/KEYBOARD answered card counts for cursor advancement. */
    private var dailyPracticeAnsweredCounts: MutableMap<DailyBlockType, Int> = mutableMapOf()

    /** Get the current active pack ID dynamically (not cached). */
    private fun getCurrentActivePackId(): String? {
        return stateAccess.uiState.value.navigation.activePackId?.value
    }

    // ── Drill store access (delegated to factory functions) ──────────────────

    fun getVerbDrillStore(packId: String) = verbDrillStoreFactory(packId)

    fun getWordMasteryStore(packId: String) = wordMasteryStoreFactory(packId)

    /**
     * Get the current pack's daily cursor state.
     * Each pack maintains its own cursor to prevent cross-pack contamination.
     */
    fun getCurrentPackCursor(): PackDailyCursorState = cursorController.getPackCursor(getCurrentActivePackId())

    fun initializeCursor(legacyCursor: DailyCursorState = DailyCursorState()) {
        val cursor = cursorController.initialize(getCurrentActivePackId(), legacyCursor) ?: return
        _state.update { it.copy(dailyCursor = cursor) }
    }

    internal fun startDailySession(blocks: List<DailyBlock>, lessonLevel: Int, packId: String) {
        if (blocks.isEmpty()) return
        Log.d(logTag, "DailyPractice: startSession blocks=${blocks.size}, level=$lessonLevel, packId=$packId, blockTypes=${blocks.map { it.type }}")
        _state.update { state ->
            state.copy(dailySession = DailySessionState(
                active = true,
                blocks = blocks,
                blockIndex = 0,
                level = lessonLevel,
                finishedToken = false,
                packId = packId
            ))
        }
        stateAccess.saveProgress()
    }

    fun endSession() {
        val ds = _state.value.dailySession
        val languageId = stateAccess.uiState.value.navigation.selectedLanguageId?.value ?: return
        val blockTypes = ds.blocks.map { it.type }.toSet()
        val anyNewFire = streakCompletion.record(languageId, blockTypes)
        Log.d(logTag, "DailyPractice: endSession, blockTypes=$blockTypes, streakFires=$anyNewFire")

        _state.update { state ->
            state.copy(dailySession = state.dailySession.copy(
                active = false,
                finishedToken = true
            ))
        }
        stateAccess.saveProgress()
    }

    // ── Block navigation (single completion path) ───────────────────────

    /**
     * Signal that the current block has been completed.
     * Advances blockIndex and returns the next block to render, or null
     * if all blocks are done (session ends).
     *
     * This is the ONE completion mechanism for ALL blocks:
     * - TRANSLATE/VERBS: called by GrammarMateApp when TrainingScreen finishes
     * - VOCAB: called by DailyPracticeScreen when the flashcard block ends
     */
    fun onBlockComplete(): DailyBlock? {
        val ds = _state.value.dailySession
        if (!ds.active) return null

        Log.d(logTag, "DailyPractice: onBlockComplete blockIndex=${ds.blockIndex}, totalBlocks=${ds.blocks.size}, nextIndex=${ds.blockIndex + 1}")

        // Mark current block as complete
        val updatedBlocks = ds.blocks.mapIndexed { index, block ->
            if (index == ds.blockIndex) block.copy(isComplete = true) else block
        }

        val nextIndex = ds.blockIndex + 1
        if (nextIndex >= updatedBlocks.size) {
            // All blocks done — update state and end session
            _state.update {
                it.copy(dailySession = it.dailySession.copy(
                    blocks = updatedBlocks,
                    blockIndex = nextIndex
                ))
            }
            stateAccess.saveProgress()
            endSession()
            return null
        }

        _state.update {
            it.copy(dailySession = it.dailySession.copy(
                blocks = updatedBlocks,
                blockIndex = nextIndex
            ))
        }
        stateAccess.saveProgress()
        return updatedBlocks[nextIndex]
    }

    /**
     * Get the current block to render.
     * Returns null if session is not active or all blocks are done.
     * Throws [SessionInvalidatedException] if the active pack has changed.
     */
    fun getCurrentBlock(): DailyBlock? {
        val ds = _state.value.dailySession
        if (!ds.active) return null

        Log.d(logTag, "DailyPractice: getCurrentBlock active=${ds.active}, blockIndex=${ds.blockIndex}, type=${ds.currentBlock?.type}")

        // Validate that the session's packId matches the current active pack
        val currentPackId = stateAccess.uiState.value.navigation.activePackId?.value
        if (ds.packId.isNotEmpty() && currentPackId != null && ds.packId != currentPackId) {
            invalidateDailySession()
            throw SessionInvalidatedException("Pack changed from ${ds.packId} to $currentPackId")
        }

        return ds.currentBlock
    }

    /**
     * Get the current block type.
     */
    fun getCurrentBlockType(): DailyBlockType? {
        return getCurrentBlock()?.type
    }

    /**
     * Get the current task within the current block.
     * Used for VOCAB blocks rendered inline.
     */
    fun getCurrentTask(): DailyTask? {
        val block = getCurrentBlock() ?: return null
        return block.tasks.getOrNull(block.taskIndex)
    }

    /**
     * Get the render method for the current block.
     */
    fun getCurrentRenderVia(): BlockRenderVia? {
        return getCurrentBlock()?.renderVia
    }

    // ── Session start / resume ─────────────────────────────────────────

    fun hasResumableDailySession(): Boolean {
        val cursor = getCurrentPackCursor()
        val today = java.time.LocalDate.now().toString()
        val state = stateAccess.uiState.value
        val packId = state.navigation.activePackId?.value ?: return false
        val languageId = state.navigation.selectedLanguageId?.value ?: return false
        return DailySessionIdentityResolver.forStoredRepeat(
            packId, languageId, cursor, today,
            lessonStore.getLessonIdsForPack(packId), lessonStore.getLessons(packId, languageId)
        ) != null
    }

    /**
     * Start a new daily practice session.
     *
     * Level and lesson are derived from the cursor (currentLessonIndex), NOT from
     * mastery-based progress. This ensures Block 1 and Block 3 stay in sync:
     *   effectiveLevel = cursor.currentLessonIndex + 1
     *   lessonId = lesson at cursor.currentLessonIndex
     *
     * @param resolveProgressLessonInfo fallback for lesson ID when cursor index is invalid.
     * @param onStoreFirstSessionCardIds callback to store first-session card IDs.
     * @return true if session was started successfully.
     */
    suspend fun startDailyPractice(
        resolveProgressLessonInfo: () -> Pair<String, Int>?,
        onStoreFirstSessionCardIds: (lessonId: String, sentenceIds: List<String>, verbIds: List<String>) -> Unit
    ): Boolean {
        val cursor = getCursor()
        _state.update { it.copy(dailyCursor = cursor) }
        dailyPracticeAnsweredCounts = mutableMapOf()

        val state = stateAccess.uiState.value
        val packId = state.navigation.activePackId ?: return false
        val langId = state.navigation.selectedLanguageId ?: return false

        val orderedLessonIds = lessonStore.getLessonIdsForPack(packId.value)
        val fallback = if (cursor.currentLessonIndex in orderedLessonIds.indices) null else resolveProgressLessonInfo()
        val identity = DailySessionIdentityResolver.forStart(
            packId.value, langId.value, cursor, orderedLessonIds, fallback
        ) ?: return false
        val effectiveLevel = identity.level
        val lessonId = identity.lessonId

        val today = java.time.LocalDate.now().toString()
        val isFirstSessionToday = cursor.firstSessionDate != today

        Log.d(logTag, "DailyPractice: startDailyPractice cursor=$cursor, firstToday=$isFirstSessionToday, identity=$identity")

        // Try pre-built blocks first (only valid for first session of the day)
        val prebuildKey = DailyPrebuildKey(identity, today, cursor.sentenceOffset, cursor.verbOffset, sessionSize)
        val cached = if (isFirstSessionToday) planCache.takePrebuilt(prebuildKey) else null
        if (!cached.isNullOrEmpty()) {
            startDailySession(cached, effectiveLevel, packId.value)
            val (sentenceIds, verbIds) = cached.firstSessionCardIds()
            onStoreFirstSessionCardIds(lessonId, sentenceIds, verbIds)
            planCache.rememberFirst(DailyRepeatKey(identity, today, sentenceIds, verbIds), cached)
            return true
        }

        // Build fresh blocks
        Log.d(logTag, "DailyPractice: building fresh blocks, packId=${packId.value}, langId=${langId.value}")
        val verbDrillStore = getVerbDrillStore(packId.value)
        val packWordMasteryStore = getWordMasteryStore(packId.value)
        val cumulativeTenses = lessonStore.getCumulativeTenses(packId.value, effectiveLevel)
        val composer = DailySessionComposer(lessonStore, verbDrillStore, packWordMasteryStore, sessionSize)

        // Clear cache from previous pack
        composer.invalidateCache()

        val blocks = composer.buildBlocks(effectiveLevel, packId.value, langId.value, lessonId, cumulativeTenses, cursor)
        Log.d(logTag, "DailyPractice: built ${blocks.size} blocks, per-type=${blocks.associate { it.type to it.tasks.size }}")
        if (blocks.isEmpty()) return false

        startDailySession(blocks, effectiveLevel, packId.value)

        if (isFirstSessionToday) {
            val (sentenceIds, verbIds) = blocks.firstSessionCardIds()
            onStoreFirstSessionCardIds(lessonId, sentenceIds, verbIds)
            planCache.rememberFirst(DailyRepeatKey(identity, today, sentenceIds, verbIds), blocks)
        }
        return true
    }

    suspend fun repeatDailyPractice(
        lessonLevel: Int,
        resolveProgressLessonInfo: () -> Pair<String, Int>?
    ): Boolean {
        Log.d(logTag, "DailyPractice: repeatDailyPractice level=$lessonLevel")
        val state = stateAccess.uiState.value
        val packId = state.navigation.activePackId ?: return false
        val langId = state.navigation.selectedLanguageId ?: return false

        // Use pack-scoped cursor instead of global cursor
        val cursor = getCurrentPackCursor()
        val today = java.time.LocalDate.now().toString()
        val orderedLessonIds = lessonStore.getLessonIdsForPack(packId.value)
        val storedIdentity = DailySessionIdentityResolver.forStoredRepeat(
            packId.value, langId.value, cursor, today, orderedLessonIds,
            lessonStore.getLessons(packId.value, langId.value)
        )
        val savedKey = storedIdentity?.let {
            DailyRepeatKey(it, today, cursor.firstSessionSentenceCardIds, cursor.firstSessionVerbCardIds)
        }

        // Try in-memory cache first (fastest path, same app run)
        val cached = savedKey?.let { planCache.repeat(it) }
        if (storedIdentity != null && !cached.isNullOrEmpty()) {
            startDailySession(cached, storedIdentity.level, packId.value)
            return true
        }

        // Create composer and clear cache before repeat
        val composer = DailySessionComposer(lessonStore, getVerbDrillStore(packId.value), getWordMasteryStore(packId.value), sessionSize)
        composer.invalidateCache()

        // Reconstruct from today's card IDs using the actual pack lesson, not browsing progress.
        if (storedIdentity != null && savedKey != null) {
            val cumulativeTenses = lessonStore.getCumulativeTenses(packId.value, storedIdentity.level)
            val blocks = composer.buildRepeatBlocks(
                storedIdentity.level, packId.value, langId.value, storedIdentity.lessonId, cumulativeTenses,
                sentenceCardIds = cursor.firstSessionSentenceCardIds,
                verbCardIds = cursor.firstSessionVerbCardIds
            )
            val restoredSentenceCount = blocks
                .find { it.type == DailyBlockType.TRANSLATE }
                ?.tasks
                ?.size
                ?: 0
            val restoredVerbCount = blocks
                .find { it.type == DailyBlockType.VERBS }
                ?.tasks
                ?.size
                ?: 0
            val expectedSentenceCount = cursor.firstSessionSentenceCardIds.size
            val expectedVerbCount = cursor.firstSessionVerbCardIds.size
            val restoredStoredIds =
                restoredSentenceCount == expectedSentenceCount &&
                    restoredVerbCount == expectedVerbCount
            Log.d(logTag, "DailyPractice: repeat rebuilding from stored IDs, restoredSentence=$restoredSentenceCount/$expectedSentenceCount, restoredVerbs=$restoredVerbCount/$expectedVerbCount, match=$restoredStoredIds")
            if (blocks.isNotEmpty() && restoredStoredIds) {
                planCache.rememberFirst(savedKey, blocks)
                startDailySession(blocks, storedIdentity.level, packId.value)
                return true
            }
            Log.i(logTag, "Stored repeat card ids do not match pack ${packId.value}; rebuilding daily session from cursor.")
        }

        // Last resort: build fresh with cursor at position 0 (start of day)
        Log.d(logTag, "DailyPractice: repeat building fresh")
        val resetPackCursor = cursor.copy(sentenceOffset = 0)
        // Convert PackDailyCursorState to DailyCursorState for legacy API
        val resetCursor = DailyCursorState(
            sentenceOffset = resetPackCursor.sentenceOffset,
            currentLessonIndex = resetPackCursor.currentLessonIndex,
            lastSessionHash = resetPackCursor.lastSessionHash,
            firstSessionDate = resetPackCursor.firstSessionDate,
            firstSessionSentenceCardIds = resetPackCursor.firstSessionSentenceCardIds,
            firstSessionVerbCardIds = resetPackCursor.firstSessionVerbCardIds,
            verbOffset = resetPackCursor.verbOffset
        )
        val progressInfo = if (resetCursor.currentLessonIndex in orderedLessonIds.indices) null
            else resolveProgressLessonInfo()
        val fallbackIdentity = DailySessionIdentityResolver.forStart(
            packId.value, langId.value, resetCursor, orderedLessonIds, progressInfo
        ) ?: return false
        val cumulativeTenses = lessonStore.getCumulativeTenses(packId.value, fallbackIdentity.level)
        val blocks = composer.buildBlocks(fallbackIdentity.level, packId.value, langId.value, fallbackIdentity.lessonId, cumulativeTenses, resetCursor)
        if (blocks.isEmpty()) return false

        startDailySession(blocks, fallbackIdentity.level, packId.value)
        return true
    }

    // ── Progress tracking ────────────────────────────────────────────────

    fun recordDailyCardPracticed(
        blockType: DailyBlockType,
        sentenceCard: SentenceCard?,
        resolveCardLessonId: (card: SentenceCard) -> String,
        accepted: Boolean = true
    ) {
        val count = dailyPracticeAnsweredCounts[blockType] ?: 0
        dailyPracticeAnsweredCounts[blockType] = count + 1

        if (blockType == DailyBlockType.TRANSLATE && accepted && sentenceCard != null) {
            val packId = stateAccess.uiState.value.navigation.activePackId?.value ?: return
            taskProgress.recordSentenceShow(packId, resolveCardLessonId(sentenceCard), sentenceCard.id)
        }
    }

    fun persistDailyVerbProgress(card: VerbDrillCard) {
        val packId = stateAccess.uiState.value.navigation.activePackId?.value ?: return
        taskProgress.recordVerbShow(packId, card)
    }

    // ── Repeat / rebuild ─────────────────────────────────────────────────

    fun repeatDailyBlock(): Boolean {
        val ds = _state.value.dailySession
        if (!ds.active) return false
        val block = ds.currentBlock ?: return false
        if (block.tasks.isEmpty()) return false
        replaceCurrentBlock(block.copy(taskIndex = 0, isComplete = false))
        return true
    }

    private fun replaceCurrentBlock(newBlock: DailyBlock) {
        val ds = _state.value.dailySession
        if (!ds.active) return

        val updatedBlocks = ds.blocks.mapIndexed { index, block ->
            if (index == ds.blockIndex) newBlock else block
        }

        _state.update {
            it.copy(dailySession = it.dailySession.copy(blocks = updatedBlocks))
        }
        stateAccess.saveProgress()
    }

    // ── Cancel ───────────────────────────────────────────────────────────

    /**
     * Cancel the daily session. Conditionally advances cursor based on
     * VOICE/KEYBOARD completion of TRANSLATE and VERBS blocks.
     *
     * @return the sentence count to advance cursor by, or null if no advancement needed.
     */
    fun cancelDailySession(): Int? {
        val ds = _state.value.dailySession
        var sentenceCountToAdvance: Int? = null

        val sentenceCount = dailyPracticeAnsweredCounts[DailyBlockType.TRANSLATE] ?: 0
        val verbCount = dailyPracticeAnsweredCounts[DailyBlockType.VERBS] ?: 0

        if (ds.finishedToken) {
            val sentenceBlock = ds.blocks.find { it.type == DailyBlockType.TRANSLATE }
            val verbBlock = ds.blocks.find { it.type == DailyBlockType.VERBS }
            val expectedSentenceCount = sentenceBlock?.tasks?.size ?: 0
            val expectedVerbCount = verbBlock?.tasks?.size ?: 0
            val allSentencePracticed = sentenceCount >= expectedSentenceCount
            val allVerbsPracticed = verbCount >= expectedVerbCount
            if (allSentencePracticed && allVerbsPracticed) {
                sentenceCountToAdvance = sentenceCount
            }
        }
        Log.d(logTag, "DailyPractice: cancelDailySession, finishedToken=${ds.finishedToken}, sentenceCount=$sentenceCount, verbCount=$verbCount, sentenceCountToAdvance=$sentenceCountToAdvance")
        dailyPracticeAnsweredCounts.clear()
        _state.update { it.copy(dailySession = DailySessionState()) }
        stateAccess.saveProgress()
        return sentenceCountToAdvance
    }

    // ── Vocab SRS ──────────────────────────────────────────────────────

    fun rateVocabCard(rating: SrsRating) {
        val block = getCurrentBlock() ?: return
        val task = block.tasks.getOrNull(block.taskIndex) as? DailyTask.VocabFlashcard ?: return
        val wordId = task.word.id
        val state = stateAccess.uiState.value
        val packId = state.navigation.activePackId ?: return
        val newStepIndex = taskProgress.rateVocab(packId.value, wordId, rating)
        Log.d(logTag, "DailyPractice: rateVocabCard wordId=$wordId, rating=$rating, newStepIndex=$newStepIndex, taskIndex=${block.taskIndex}/${block.tasks.size}")

        // Advance task index within the VOCAB block
        val nextIndex = block.taskIndex + 1
        if (nextIndex >= block.tasks.size) {
            // All VOCAB cards rated — mark block complete
            onBlockComplete()
        } else {
            // Advance to next vocab card
            val ds = _state.value.dailySession
            _state.update {
                it.copy(dailySession = it.dailySession.copy(
                    blocks = it.dailySession.blocks.mapIndexed { i, b ->
                        if (i == ds.blockIndex) b.copy(taskIndex = nextIndex) else b
                    }
                ))
            }
            stateAccess.saveProgress()
        }
    }

    // ── Current state queries ──────────────────────────────────────────

    fun getDailyCurrentTask(): DailyTask? = getCurrentTask()

    fun getDailyBlockProgress(): BlockProgress {
        val ds = _state.value.dailySession
        if (!ds.active) return BlockProgress.Empty

        val currentBlock = ds.currentBlock ?: return BlockProgress.Empty
        val blockSize = currentBlock.tasks.size

        // Calculate global position: sum of all completed blocks + position within current block
        val completedTasks = ds.blocks.take(ds.blockIndex).sumOf { it.tasks.size }
        val positionInBlock = currentBlock.taskIndex + 1
        val globalPosition = completedTasks + positionInBlock

        return BlockProgress(
            blockType = currentBlock.type,
            positionInBlock = positionInBlock,
            blockSize = blockSize,
            totalTasks = ds.totalTasks,
            globalPosition = globalPosition
        )
    }

    // ── Answer submission ──────────────────────────────────────────────

    fun submitDailySentenceAnswer(input: String): Boolean {
        val block = getCurrentBlock() ?: return false
        val task = block.tasks.firstOrNull() as? DailyTask.TranslateSentence ?: return false
        val card = task.card
        return answerValidator.validate(input, card.acceptedAnswers).isCorrect
    }

    fun submitDailyVerbAnswer(input: String): Boolean {
        val block = getCurrentBlock() ?: return false
        val task = block.tasks.firstOrNull() as? DailyTask.ConjugateVerb ?: return false
        val card = task.card
        return answerValidator.validate(input, card.acceptedAnswers).isCorrect
    }

    // ── Answer retrieval ───────────────────────────────────────────────

    fun getDailySentenceAnswer(): String? {
        val block = getCurrentBlock() ?: return null
        val task = block.tasks.firstOrNull() as? DailyTask.TranslateSentence ?: return null
        return task.card.acceptedAnswers.firstOrNull()
    }

    fun getDailyVerbAnswer(): String? {
        val block = getCurrentBlock() ?: return null
        val task = block.tasks.firstOrNull() as? DailyTask.ConjugateVerb ?: return null
        return task.card.answer
    }

    // ── Pre-build session (for background init) ────────────────────────

    /**
     * Build a daily practice session in the background for faster start.
     * Called from the ViewModel's init block on a background thread (Dispatchers.IO).
     */
    suspend fun prebuildSession(
        packId: String,
        langId: String,
        lessonId: String,
        lessonLevel: Int,
        cursor: DailyCursorState
    ) {
        val verbDrillStore = getVerbDrillStore(packId)
        val packWordMasteryStore = getWordMasteryStore(packId)
        val cumulativeTenses = lessonStore.getCumulativeTenses(packId, lessonLevel)
        val composer = DailySessionComposer(lessonStore, verbDrillStore, packWordMasteryStore, sessionSize)
        val blocks = composer.buildBlocks(lessonLevel, packId, langId, lessonId, cumulativeTenses, cursor)
        if (blocks.isNotEmpty()) {
            val identity = DailySessionIdentity(packId, langId, lessonId, lessonLevel)
            planCache.putPrebuilt(DailyPrebuildKey(
                identity, java.time.LocalDate.now().toString(),
                cursor.sentenceOffset, cursor.verbOffset, sessionSize
            ), blocks)
        }
    }

    // ── Reset ───────────────────────────────────────────────────────────

    fun setSessionSize(size: Int) {
        sessionSize = size
    }

    /**
     * Invalidate the daily session when the active pack changes.
     * Clears the session state to force a rebuild with the new pack.
     */
    private fun invalidateDailySession() {
        _state.update { it.copy(dailySession = DailySessionState()) }
        stateAccess.saveProgress()
    }

    /**
     * Soft reset: clears active session and caches but preserves [dailyCursor].
     * Used by selectLesson(), selectLanguage(), importLessonPack(), refreshLessons(), etc.
     * The cursor represents accumulated daily progress and must survive these operations.
     */
    fun resetState() {
        Log.d(logTag, "DailyPractice: resetState (soft reset)")
        planCache.clear()
        dailyPracticeAnsweredCounts.clear()
        _state.update { it.copy(dailySession = DailySessionState()) }
    }

    /**
     * Full reset: clears everything including [dailyCursor].
     * Used ONLY by "reset all progress" / "reset language progress" in Settings.
     */
    fun resetAllDailyState() {
        Log.d(logTag, "DailyPractice: resetAllDailyState (full reset)")
        planCache.clear()
        dailyPracticeAnsweredCounts.clear()
        _state.update { DailyPracticeState() }
        // Delete pack cursor from disk so stale card IDs don't resurface on restart
        cursorController.delete(getCurrentActivePackId())
    }

    /**
     * Clear the prebuilt session cache. Called when prebuilt data is consumed
     * during startDailyPractice or when it should be discarded.
     */
    fun clearPrebuiltSession() {
        planCache.clearPrebuilt()
    }

    // ── Cursor management (called by ViewModel) ──────────────────────────

    fun updateCursor(cursor: DailyCursorState) {
        if (!cursorController.save(getCurrentActivePackId(), cursor)) return
        _state.update { it.copy(dailyCursor = cursor) }
    }

    fun getCursor(): DailyCursorState = cursorController.getCursor(getCurrentActivePackId())

    /** Test-only accessor to inspect internal state. */
    internal fun getDailyState(): DailyPracticeState = _state.value

    /**
     * Advance the daily cursor with lesson transition and pack wrapping.
     *
     * Increases sentenceOffset by [sentenceCount]. If the offset exceeds the
     * current lesson's card count, advances currentLessonIndex and resets
     * sentenceOffset. When currentLessonIndex goes past the last lesson in
     * the pack, it wraps back to 0 (pack wrap).
     *
     * Also increases verbOffset by [sessionSize]. When verbOffset exceeds
     * the total verb pool size (filtered by active tenses for the current
     * lesson level), it wraps back to 0.
     *
     * @param sentenceCount number of VOICE/KEYBOARD sentence cards completed.
     * @param languageId the active language (for looking up lesson card counts).
     * @return the updated cursor state (caller must apply via updateCursor).
     */
    fun advanceDailyCursor(sentenceCount: Int, languageId: String): DailyCursorState {
        val cursor = getCursor()
        _state.update { it.copy(dailyCursor = cursor) }
        val result = cursorController.advance(
            cursor, getCurrentActivePackId(), languageId, sentenceCount, sessionSize
        )
        Log.d(logTag, "DailyPractice: advanceDailyCursor result: newLessonIndex=${result.currentLessonIndex}, newSentenceOffset=${result.sentenceOffset}, newVerbOffset=${result.verbOffset}")
        return result
    }

    /** Get the SessionCard list for the current TRANSLATE block. */
    fun getCurrentTranslateCards(): List<com.alexpo.grammermate.data.SessionCard> {
        val ds = _state.value.dailySession
        val block = ds.blocks.getOrNull(ds.blockIndex) ?: return emptyList()
        if (block.type != DailyBlockType.TRANSLATE) return emptyList()
        return block.tasks.filterIsInstance<DailyTask.TranslateSentence>().map { it.card }
    }

    /**
     * Get the SessionCard list for the current VERBS block.
     * Used by GrammarMateApp to pass to TrainingScreen.
     */
    fun getCurrentVerbCards(): List<com.alexpo.grammermate.data.SessionCard> {
        val ds = _state.value.dailySession
        val block = ds.blocks.getOrNull(ds.blockIndex) ?: return emptyList()
        if (block.type != DailyBlockType.VERBS) return emptyList()
        return block.tasks.filterIsInstance<DailyTask.ConjugateVerb>().map { it.card }
    }

    /**
     * Get the DailyTask list for the current block (all types).
     * Used by the daily-practice block flow to access tasks for the block.
     */
    fun getCurrentBlockTasks(): List<DailyTask> {
        return getCurrentBlock()?.tasks ?: emptyList()
    }
}
