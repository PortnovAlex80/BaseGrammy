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
import com.alexpo.grammermate.data.SpacedRepetitionConfig
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillComboProgress
import com.alexpo.grammermate.data.VerbDrillStore
import com.alexpo.grammermate.data.WordMasteryState
import com.alexpo.grammermate.data.WordMasteryStore
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.PracticeType
import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.StreakStore
import com.alexpo.grammermate.data.VerbDrillCsvParser
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.feature.training.AnswerValidator
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

    // ── Owned state flow ─────────────────────────────────────────────────
    private val _state = MutableStateFlow(DailyPracticeState())
    val dailyState: StateFlow<DailyPracticeState> = _state

    // ── Owned private mutable state ────────────────────────────────────

    /** Pre-computed daily session from background init. */
    var prebuiltDailyBlocks: List<DailyBlock>? = null
        private set

    /** Level the prebuilt session was built for; used to validate cache match. */
    private var prebuiltSessionLevel: Int = 0

    /** In-memory cache for repeat (structured blocks). */
    var lastDailyBlocks: List<DailyBlock>? = null
        private set

    /** Per-block VOICE/KEYBOARD answered card counts for cursor advancement. */
    private var dailyPracticeAnsweredCounts: MutableMap<DailyBlockType, Int> = mutableMapOf()

    /** Cursor state saved at session start; used to roll back on cancel. */
    private var dailyCursorAtSessionStart: DailyCursorState = DailyCursorState()

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
    fun getCurrentPackCursor(): PackDailyCursorState {
        val packId = stateAccess.uiState.value.navigation.activePackId?.value
            ?: return PackDailyCursorState.forPack("unknown")
        return packDailyCursorStore.loadPackCursor(packId) ?: PackDailyCursorState.forPack(packId)
    }

    /**
     * Save cursor state for the current pack.
     */
    private fun saveCurrentPackCursor(cursor: PackDailyCursorState) {
        packDailyCursorStore.savePackCursor(cursor)
    }

    private fun PackDailyCursorState.toDailyCursor(): DailyCursorState {
        return DailyCursorState(
            sentenceOffset = sentenceOffset,
            currentLessonIndex = currentLessonIndex,
            lastSessionHash = lastSessionHash,
            firstSessionDate = firstSessionDate,
            firstSessionSentenceCardIds = firstSessionSentenceCardIds,
            firstSessionVerbCardIds = firstSessionVerbCardIds,
            verbOffset = verbOffset
        )
    }

    private fun DailyCursorState.hasMeaningfulData(): Boolean {
        return sentenceOffset > 0 ||
            currentLessonIndex > 0 ||
            lastSessionHash != 0 ||
            firstSessionDate.isNotEmpty() ||
            firstSessionSentenceCardIds.isNotEmpty() ||
            firstSessionVerbCardIds.isNotEmpty() ||
            verbOffset > 0
    }

    /**
     * Initialize in-memory cursor from the active pack. Legacy global progress is used
     * only when the pack-scoped cursor does not exist yet.
     */
    fun initializeCursor(legacyCursor: DailyCursorState = DailyCursorState()) {
        val packId = stateAccess.uiState.value.navigation.activePackId?.value ?: return
        val stored = packDailyCursorStore.loadPackCursor(packId)
        val cursor = when {
            stored != null -> stored.toDailyCursor()
            legacyCursor.hasMeaningfulData() -> {
                updateCursor(legacyCursor)
                return
            }
            else -> DailyCursorState()
        }
        _state.update { it.copy(dailyCursor = cursor) }
    }

    // ── Session lifecycle ────────────────────────────────────────────────

    internal fun startDailySession(blocks: List<DailyBlock>, lessonLevel: Int, packId: String) {
        if (blocks.isEmpty()) return
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
        val languageId = stateAccess.uiState.value.navigation.selectedLanguageId.value
        val blockTypes = ds.blocks.map { it.type }.toSet()
        val blockTypeToPracticeType = mapOf(
            DailyBlockType.TRANSLATE to PracticeType.TRANSLATION,
            DailyBlockType.VOCAB to PracticeType.VOCAB,
            DailyBlockType.VERBS to PracticeType.VERB
        )
        var lastStreakData: StreakData? = null
        var anyNewFire = false
        for (blockType in blockTypes) {
            val practiceType = blockTypeToPracticeType[blockType] ?: continue
            val (updated, isNewFire) = streakStore.recordPracticeTypeCompletion(languageId, practiceType)
            lastStreakData = updated
            if (isNewFire) anyNewFire = true
        }

        // Update in-memory core state so HomeScreen shows the new streak immediately
        // without requiring an app restart. The store already persists to disk,
        // but the UI reads from _coreState which was not being updated here.
        // Also generate a celebration message when a new fire is earned, so the
        // streak celebration dialog appears after daily practice (same as regular training).
        val streakSnapshot = lastStreakData
        if (streakSnapshot != null) {
            val message = if (anyNewFire && streakSnapshot.currentStreak > 0) {
                streakManager.getCelebrationMessage(streakSnapshot.currentStreak, streakSnapshot.todayFireCount)
            } else null
            stateAccess.updateState { state ->
                state.copy(cardSession = state.cardSession.copy(
                    currentStreak = streakSnapshot.currentStreak,
                    longestStreak = streakSnapshot.longestStreak,
                    todayFireCount = streakSnapshot.todayFireCount,
                    streakMessage = message,
                    streakCelebrationToken = if (message != null) state.cardSession.streakCelebrationToken + 1 else state.cardSession.streakCelebrationToken
                ))
            }
        }

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
        return cursor.firstSessionDate == today &&
            (cursor.firstSessionSentenceCardIds.isNotEmpty() ||
                cursor.firstSessionVerbCardIds.isNotEmpty())
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
        onStoreFirstSessionCardIds: (sentenceIds: List<String>, verbIds: List<String>) -> Unit
    ): Boolean {
        val cursor = getCursor()
        _state.update { it.copy(dailyCursor = cursor) }
        dailyCursorAtSessionStart = cursor
        dailyPracticeAnsweredCounts = mutableMapOf()

        val state = stateAccess.uiState.value
        val packId = state.navigation.activePackId ?: return false
        val langId = state.navigation.selectedLanguageId

        val packLessons = lessonStore.getLessons(langId.value)
        val effectiveLevel: Int
        val lessonId: String
        val levelFromCursor: Boolean

        if (cursor.currentLessonIndex in packLessons.indices) {
            lessonId = packLessons[cursor.currentLessonIndex].id.value
            effectiveLevel = cursor.currentLessonIndex + 1
            levelFromCursor = true
        } else {
            val progressInfo = resolveProgressLessonInfo()
            lessonId = progressInfo?.first ?: return false
            effectiveLevel = progressInfo.second
            levelFromCursor = false
        }

        val today = java.time.LocalDate.now().toString()
        val isFirstSessionToday = cursor.firstSessionDate != today

        // Try pre-built blocks first (only valid for first session of the day)
        val cached = prebuiltDailyBlocks
        if (isFirstSessionToday && cached != null && cached.isNotEmpty()) {
            // NEW: Validate that cached session matches current pack
            val currentPackId = stateAccess.uiState.value.navigation.activePackId?.value
            val cachedPackId = packId.value

            if (cachedPackId != currentPackId) {
                Log.i(logTag, "Prebuilt cache packId mismatch: cached=$cachedPackId, current=$currentPackId. Clearing cache.")
                prebuiltDailyBlocks = null
                prebuiltSessionLevel = 0
                // Fall through to rebuild session
            } else {
                val levelMismatch = levelFromCursor && prebuiltSessionLevel != effectiveLevel
                if (levelMismatch) {
                    val cachedLevelValue = prebuiltSessionLevel
                    prebuiltDailyBlocks = null
                    prebuiltSessionLevel = 0
                    Log.d(logTag, "DailyPractice: discarded prebuilt session (level mismatch: cached=$cachedLevelValue cursor=$effectiveLevel)")
                } else {
                    lastDailyBlocks = cached
                    startDailySession(cached, effectiveLevel, packId.value)
                    prebuiltDailyBlocks = null
                    prebuiltSessionLevel = 0
                    val allTasks = cached.flatMap { it.tasks }
                    val sentenceIds = allTasks
                        .filterIsInstance<DailyTask.TranslateSentence>()
                        .map { it.card.id }
                    val verbIds = allTasks
                        .filterIsInstance<DailyTask.ConjugateVerb>()
                        .map { it.card.id }
                    onStoreFirstSessionCardIds(sentenceIds, verbIds)
                    return true
                }
            }
        }

        // Build fresh blocks
        val verbDrillStore = getVerbDrillStore(packId.value)
        val packWordMasteryStore = getWordMasteryStore(packId.value)
        val cumulativeTenses = lessonStore.getCumulativeTenses(packId.value, effectiveLevel)
        val composer = DailySessionComposer(lessonStore, verbDrillStore, packWordMasteryStore, sessionSize)

        // Clear cache from previous pack
        composer.invalidateCache()

        val blocks = composer.buildBlocks(effectiveLevel, packId.value, langId.value, lessonId, cumulativeTenses, cursor)
        Log.d(logTag, "DailyPractice: built ${blocks.size} blocks, per-type=${blocks.associate { it.type to it.tasks.size }}")
        if (blocks.isEmpty()) return false

        lastDailyBlocks = blocks
        startDailySession(blocks, effectiveLevel, packId.value)

        if (isFirstSessionToday) {
            val allTasks = blocks.flatMap { it.tasks }
            val sentenceIds = allTasks
                .filterIsInstance<DailyTask.TranslateSentence>()
                .map { it.card.id }
            val verbIds = allTasks
                .filterIsInstance<DailyTask.ConjugateVerb>()
                .map { it.card.id }
            onStoreFirstSessionCardIds(sentenceIds, verbIds)
        }
        return true
    }

    suspend fun repeatDailyPractice(
        lessonLevel: Int,
        resolveProgressLessonInfo: () -> Pair<String, Int>?
    ): Boolean {
        val state = stateAccess.uiState.value
        val packId = state.navigation.activePackId ?: return false
        val langId = state.navigation.selectedLanguageId

        val progressInfo = resolveProgressLessonInfo()
        val lessonId = progressInfo?.first ?: return false

        // Use pack-scoped cursor instead of global cursor
        val cursor = getCurrentPackCursor()
        val today = java.time.LocalDate.now().toString()

        // Try in-memory cache first (fastest path, same app run)
        val cached = lastDailyBlocks
        if (cached != null && cached.isNotEmpty()) {
            // NEW: Validate that cached session matches current pack
            val currentPackId = stateAccess.uiState.value.navigation.activePackId?.value
            val cachedPackId = packId.value

            if (cachedPackId != currentPackId) {
                Log.i(logTag, "Repeat cache packId mismatch: cached=$cachedPackId, current=$currentPackId. Clearing cache.")
                lastDailyBlocks = null
                // Fall through to rebuild session
            } else {
                startDailySession(cached, lessonLevel, packId.value)
                return true
            }
        }

        // Create composer and clear cache before repeat
        val composer = DailySessionComposer(lessonStore, getVerbDrillStore(packId.value), getWordMasteryStore(packId.value), sessionSize)
        composer.invalidateCache()

        // Reconstruct from stored first-session card IDs
        if (cursor.firstSessionDate == today &&
            (cursor.firstSessionSentenceCardIds.isNotEmpty() || cursor.firstSessionVerbCardIds.isNotEmpty())
        ) {
            val cumulativeTenses = lessonStore.getCumulativeTenses(packId.value, lessonLevel)
            val blocks = composer.buildRepeatBlocks(
                lessonLevel, packId.value, langId.value, lessonId, cumulativeTenses,
                sentenceCardIds = cursor.firstSessionSentenceCardIds,
                verbCardIds = cursor.firstSessionVerbCardIds
            )
            if (blocks.isNotEmpty()) {
                lastDailyBlocks = blocks
                startDailySession(blocks, lessonLevel, packId.value)
                return true
            }
        }

        // Last resort: build fresh with cursor at position 0 (start of day)
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
        val cumulativeTenses = lessonStore.getCumulativeTenses(packId.value, lessonLevel)
        val blocks = composer.buildBlocks(lessonLevel, packId.value, langId.value, lessonId, cumulativeTenses, resetCursor)
        if (blocks.isEmpty()) return false

        lastDailyBlocks = blocks
        startDailySession(blocks, lessonLevel, packId.value)
        return true
    }

    // ── Progress tracking ────────────────────────────────────────────────

    fun recordDailyCardPracticed(
        blockType: DailyBlockType,
        resolveCardLessonId: (card: com.alexpo.grammermate.data.SentenceCard) -> String
    ) {
        val count = dailyPracticeAnsweredCounts[blockType] ?: 0
        dailyPracticeAnsweredCounts[blockType] = count + 1

        if (blockType == DailyBlockType.TRANSLATE) {
            val block = getCurrentBlock() ?: return
            val task = block.tasks.firstOrNull() as? DailyTask.TranslateSentence ?: return
            val card = task.card
            val lessonId = resolveCardLessonId(card)
            val languageId = stateAccess.uiState.value.navigation.selectedLanguageId
            masteryStore.recordCardShow(lessonId, languageId.value, card.id)
        }
    }

    fun persistDailyVerbProgress(card: VerbDrillCard) {
        val packId = stateAccess.uiState.value.navigation.activePackId ?: return
        val store = getVerbDrillStore(packId.value)
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

    // ── Repeat / rebuild ─────────────────────────────────────────────────

    fun repeatDailyBlock(
        resolveProgressLessonInfo: () -> Pair<String, Int>?
    ): Boolean {
        val state = stateAccess.uiState.value
        val ds = _state.value.dailySession
        if (!ds.active) return false
        val blockType = getCurrentBlockType() ?: return false
        val packId = state.navigation.activePackId ?: return false
        val langId = state.navigation.selectedLanguageId

        val progressInfo = resolveProgressLessonInfo()
        val lessonId = progressInfo?.first ?: return false
        val lessonLevel = ds.level

        val verbDrillStore = getVerbDrillStore(packId.value)
        val packWordMasteryStore = getWordMasteryStore(packId.value)
        val cumulativeTenses = lessonStore.getCumulativeTenses(packId.value, lessonLevel)
        val composer = DailySessionComposer(lessonStore, verbDrillStore, packWordMasteryStore, sessionSize)
        val newBlock = composer.rebuildBlockAsBlock(blockType, lessonLevel, packId.value, langId.value, lessonId, cumulativeTenses)
        if (newBlock.tasks.isEmpty()) return false

        replaceCurrentBlock(newBlock)
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

        if (ds.finishedToken) {
            val sentenceCount = dailyPracticeAnsweredCounts[DailyBlockType.TRANSLATE] ?: 0
            val verbCount = dailyPracticeAnsweredCounts[DailyBlockType.VERBS] ?: 0
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
        val store = getWordMasteryStore(packId.value)
        val current = store.getMastery(wordId) ?: WordMasteryState.new(wordId)
        val now = System.currentTimeMillis()
        val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        val newStepIndex = when (rating) {
            SrsRating.AGAIN -> 0  // reset
            SrsRating.HARD -> current.intervalStepIndex  // stay
            SrsRating.GOOD -> (current.intervalStepIndex + 1).coerceIn(0, maxStep)  // +1
            SrsRating.EASY -> (current.intervalStepIndex + 2).coerceIn(0, maxStep)  // +2
        }
        val newNextReview = WordMasteryState.computeNextReview(now, newStepIndex)
        val isLearned = newStepIndex >= TrainingConfig.LEARNED_THRESHOLD
        val updated = current.copy(correctCount = current.correctCount + (if (rating != SrsRating.AGAIN) 1 else 0), incorrectCount = current.incorrectCount + (if (rating == SrsRating.AGAIN) 1 else 0), intervalStepIndex = newStepIndex, lastReviewDateMs = now, nextReviewDateMs = newNextReview, isLearned = isLearned)
        store.upsertMastery(updated)

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
            prebuiltDailyBlocks = blocks
            prebuiltSessionLevel = lessonLevel
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
        lastDailyBlocks = null
        prebuiltDailyBlocks = null
        prebuiltSessionLevel = 0
        dailyPracticeAnsweredCounts.clear()
        _state.update { it.copy(dailySession = DailySessionState()) }
    }

    /**
     * Full reset: clears everything including [dailyCursor].
     * Used ONLY by "reset all progress" / "reset language progress" in Settings.
     */
    fun resetAllDailyState() {
        lastDailyBlocks = null
        prebuiltDailyBlocks = null
        prebuiltSessionLevel = 0
        dailyPracticeAnsweredCounts.clear()
        _state.update { DailyPracticeState() }
    }

    /**
     * Clear the prebuilt session cache. Called when prebuilt data is consumed
     * during startDailyPractice or when it should be discarded.
     */
    fun clearPrebuiltSession() {
        prebuiltDailyBlocks = null
        prebuiltSessionLevel = 0
    }

    // ── Cursor management (called by ViewModel) ──────────────────────────

    fun updateCursor(cursor: DailyCursorState) {
        // Convert to pack-scoped cursor and save
        val packId = stateAccess.uiState.value.navigation.activePackId?.value ?: return
        val packCursor = PackDailyCursorState(
            packId = packId,
            sentenceOffset = cursor.sentenceOffset,
            currentLessonIndex = cursor.currentLessonIndex,
            lastSessionHash = cursor.lastSessionHash,
            firstSessionDate = cursor.firstSessionDate,
            firstSessionSentenceCardIds = cursor.firstSessionSentenceCardIds,
            firstSessionVerbCardIds = cursor.firstSessionVerbCardIds,
            verbOffset = cursor.verbOffset
        )
        saveCurrentPackCursor(packCursor)
        _state.update { it.copy(dailyCursor = cursor) }
    }

    fun getCursor(): DailyCursorState {
        // Return pack-scoped cursor as legacy DailyCursorState for compatibility
        val packCursor = getCurrentPackCursor()
        return packCursor.toDailyCursor()
    }

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
    fun advanceDailyCursor(
        sentenceCount: Int,
        languageId: String
    ): DailyCursorState {
        val cursor = getCursor()
        _state.update { it.copy(dailyCursor = cursor) }
        val lessons = lessonStore.getLessons(languageId)
        if (lessons.isEmpty()) return cursor

        var sentenceOffset = cursor.sentenceOffset + sentenceCount
        var currentLessonIndex = cursor.currentLessonIndex

        val currentLesson = lessons.getOrNull(currentLessonIndex)
        if (currentLesson != null && sentenceOffset >= currentLesson.cards.size) {
            currentLessonIndex++
            sentenceOffset = 0
            if (currentLessonIndex >= lessons.size) {
                currentLessonIndex = 0
            }
        }

        // Advance verbOffset with cycling
        val state = stateAccess.uiState.value
        val packId = state.navigation.activePackId?.value
        val newVerbOffset = if (packId != null) {
            val effectiveLevel = currentLessonIndex + 1
            val cumulativeTenses = lessonStore.getCumulativeTenses(packId, effectiveLevel)
            val totalVerbPoolSize = getTotalVerbPoolSize(packId, languageId, cumulativeTenses)
            val incremented = cursor.verbOffset + sessionSize
            if (totalVerbPoolSize > 0 && incremented >= totalVerbPoolSize) 0 else incremented
        } else {
            cursor.verbOffset
        }

        return cursor.copy(
            currentLessonIndex = currentLessonIndex,
            sentenceOffset = sentenceOffset,
            verbOffset = newVerbOffset
        )
    }

    /**
     * Get the total number of verb cards filtered by active tenses.
     * Used for cycling the verbOffset in advanceDailyCursor.
     */
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

    // ── Convenience: get cards for current TRANSLATE or VERBS block ────

    /**
     * Get the SessionCard list for the current TRANSLATE block.
     * Used by GrammarMateApp to pass to TrainingScreen.
     */
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
     * Used by DailyPracticeSessionProvider to access tasks for the block.
     */
    fun getCurrentBlockTasks(): List<DailyTask> {
        return getCurrentBlock()?.tasks ?: emptyList()
    }
}
