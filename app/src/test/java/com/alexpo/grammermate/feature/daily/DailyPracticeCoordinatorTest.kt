package com.alexpo.grammermate.feature.daily

import android.app.Application
import com.alexpo.grammermate.data.*
import com.alexpo.grammermate.feature.training.AnswerValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import android.content.ContentResolver
import android.net.Uri
import java.io.File

/**
 * Unit tests for [DailyPracticeCoordinator] -- session lifecycle, block boundaries,
 * cursor advancement, cancel/repeat, verb progress persistence, vocab SRS rating,
 * and edge cases.
 *
 * Uses Robolectric because [DailyPracticeCoordinator] takes [Application] as a
 * constructor parameter (used for Log.d calls).
 *
 * Follows the same patterns as [com.alexpo.grammermate.feature.training.SessionRunnerTest]:
 * - [TestStateAccess] backed by [MutableStateFlow]
 * - In-memory mock stores
 * - Real [AnswerValidator] (pure Kotlin, no side effects)
 */
@RunWith(RobolectricTestRunner::class)
class DailyPracticeCoordinatorTest {

    // ── System under test ──────────────────────────────────────────────

    private lateinit var coordinator: DailyPracticeCoordinator
    private lateinit var app: Application
    private lateinit var stateFlow: MutableStateFlow<TrainingUiState>
    private lateinit var stateAccess: TestStateAccess
    private lateinit var mockLessonStore: MockLessonStore
    private lateinit var mockMasteryStore: MockMasteryStore
    private lateinit var mockVerbDrillStore: MockVerbDrillStore
    private lateinit var mockWordMasteryStore: MockWordMasteryStore

    // ── Test fixtures ──────────────────────────────────────────────────

    private val translateCard1 = SentenceCard(id = "tc1", promptRu = "ru1", acceptedAnswers = listOf("hello"))
    private val translateCard2 = SentenceCard(id = "tc2", promptRu = "ru2", acceptedAnswers = listOf("world"))
    private val translateCard3 = SentenceCard(id = "tc3", promptRu = "ru3", acceptedAnswers = listOf("test"))

    private val verbCard1 = VerbDrillCard(id = "vc1", promptRu = "vr1", answer = "parlo", verb = "parlare", tense = "Presente", group = "regular")
    private val verbCard2 = VerbDrillCard(id = "vc2", promptRu = "vr2", answer = "parli", verb = "parlare", tense = "Presente", group = "regular")

    private val vocabWord1 = VocabWord(id = "vw1", word = "casa", pos = "nouns", rank = 1, meaningRu = "house")
    private val vocabWord2 = VocabWord(id = "vw2", word = "gatto", pos = "nouns", rank = 2, meaningRu = "cat")

    /** Standard 3-block session: 3 translate + 2 vocab + 2 verb = 7 tasks */
    private val standardTasks: List<DailyTask> by lazy {
        listOf(
            DailyTask.TranslateSentence("sent_tc1", translateCard1, InputMode.VOICE),
            DailyTask.TranslateSentence("sent_tc2", translateCard2, InputMode.KEYBOARD),
            DailyTask.TranslateSentence("sent_tc3", translateCard3, InputMode.WORD_BANK),
            DailyTask.VocabFlashcard("voc_vw1", vocabWord1, VocabDrillDirection.IT_TO_RU),
            DailyTask.VocabFlashcard("voc_vw2", vocabWord2, VocabDrillDirection.RU_TO_IT),
            DailyTask.ConjugateVerb("verb_vc1", verbCard1, InputMode.KEYBOARD),
            DailyTask.ConjugateVerb("verb_vc2", verbCard2, InputMode.WORD_BANK)
        )
    }

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        stateFlow = MutableStateFlow(TrainingUiState())
        stateAccess = TestStateAccess(stateFlow)
        mockLessonStore = MockLessonStore()
        mockMasteryStore = MockMasteryStore()
        mockVerbDrillStore = MockVerbDrillStore()
        mockWordMasteryStore = MockWordMasteryStore()

        coordinator = DailyPracticeCoordinator(
            stateAccess = stateAccess,
            appContext = app,
            answerValidator = AnswerValidator(),
            lessonStore = mockLessonStore,
            masteryStore = mockMasteryStore,
            verbDrillStoreFactory = { _ -> mockVerbDrillStore },
            wordMasteryStoreFactory = { _ -> mockWordMasteryStore }
        )
    }

    @After
    fun tearDown() {
        coordinator.resetState()
    }

    // ══════════════════════════════════════════════════════════════════
    // 1. Session lifecycle: startDailySession -> 3 blocks -> complete
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun startDailySession_emptyTasks_doesNotActivateSession() = runBlocking {
        setNavigationState()
        val result = coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 3 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )
        assertFalse(result)
        assertFalse(coordinator.dailyState.value.dailySession.active)
    }

    @Test
    fun startDailySession_withPrebuiltTasks_activatesSession() = runBlocking {
        setNavigationState()
        coordinator.injectPrebuiltSession(standardTasks)

        val result = coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 3 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )
        assertTrue(result)
        val ds = coordinator.dailyState.value.dailySession
        assertTrue(ds.active)
        assertEquals(7, ds.tasks.size)
        assertEquals(0, ds.taskIndex)
        assertEquals(0, ds.blockIndex)
    }

    @Test
    fun startDailySession_setsFinishedTokenToFalse() = runBlocking {
        setNavigationState()
        coordinator.injectPrebuiltSession(standardTasks)

        coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 3 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )
        assertFalse(coordinator.dailyState.value.dailySession.finishedToken)
    }

    @Test
    fun startDailySession_noActivePack_returnsFalse() = runBlocking {
        stateFlow.value = TrainingUiState(
            navigation = NavigationState(activePackId = null)
        )
        coordinator.injectPrebuiltSession(standardTasks)

        val result = coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 3 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )
        assertFalse(result)
    }

    @Test
    fun startDailySession_resolveProgressReturnsNull_returnsFalse() = runBlocking {
        setNavigationState()
        coordinator.injectPrebuiltSession(standardTasks)

        val result = coordinator.startDailyPractice(
            resolveProgressLessonInfo = { null },
            onStoreFirstSessionCardIds = { _, _ -> }
        )
        assertFalse(result)
    }

    @Test
    fun startDailySession_callsOnStoreFirstSessionCardIds() = runBlocking {
        setNavigationState()
        coordinator.injectPrebuiltSession(standardTasks)

        var capturedSentenceIds: List<String>? = null
        var capturedVerbIds: List<String>? = null

        coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 3 },
            onStoreFirstSessionCardIds = { sentenceIds, verbIds ->
                capturedSentenceIds = sentenceIds
                capturedVerbIds = verbIds
            }
        )

        assertNotNull(capturedSentenceIds)
        assertEquals(listOf("tc1", "tc2", "tc3"), capturedSentenceIds)
        assertNotNull(capturedVerbIds)
        assertEquals(listOf("vc1", "vc2"), capturedVerbIds)
    }

    @Test
    fun startDailySession_savesProgress() = runBlocking {
        setNavigationState()
        coordinator.injectPrebuiltSession(standardTasks)
        val savesBefore = stateAccess.saveCount

        coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 3 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )

        assertTrue(stateAccess.saveCount > savesBefore)
    }

    @Test
    fun startDailySession_fallbackToComposerBuildsSession() = runBlocking {
        setNavigationState()
        // No prebuilt session -- coordinator falls back to DailySessionComposer
        // which reads from mockLessonStore. With empty mock, tasks will be empty
        // so the result is false. Verify the fallback path is attempted.
        val result = coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 3 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )
        assertFalse(result)
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. Block boundaries: TRANSLATE -> VOCAB -> VERBS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun getCurrentTask_initial_returnsFirstTask() = runBlocking {
        startActiveSession()

        val task = coordinator.getCurrentTask()
        assertNotNull(task)
        assertTrue(task is DailyTask.TranslateSentence)
        assertEquals("sent_tc1", task!!.id)
    }

    @Test
    fun getCurrentBlockType_initial_returnsTranslate() = runBlocking {
        startActiveSession()

        assertEquals(DailyBlockType.TRANSLATE, coordinator.getCurrentBlockType())
    }

    @Test
    fun advanceDailyTask_withinTranslateBlock_advancesWithinBlock() = runBlocking {
        startActiveSession()

        coordinator.advanceDailyTask {}
        assertEquals(DailyBlockType.TRANSLATE, coordinator.getCurrentBlockType())
        assertEquals("sent_tc2", coordinator.getCurrentTask()!!.id)
    }

    @Test
    fun advanceDailyTask_fromTranslateToVocab_crossesBlockBoundary() = runBlocking {
        startActiveSession()

        // Advance through 3 translate cards
        coordinator.advanceDailyTask {}  // tc1 -> tc2
        coordinator.advanceDailyTask {}  // tc2 -> tc3
        coordinator.advanceDailyTask {}  // tc3 -> vocab1

        assertEquals(DailyBlockType.VOCAB, coordinator.getCurrentBlockType())
        val task = coordinator.getCurrentTask()
        assertNotNull(task)
        assertTrue(task is DailyTask.VocabFlashcard)
    }

    @Test
    fun advanceDailyTask_blockIndexAdvancesAtBlockBoundary() = runBlocking {
        startActiveSession()
        assertEquals(0, coordinator.dailyState.value.dailySession.blockIndex)

        // Advance to VOCAB block
        coordinator.advanceDailyTask {}
        coordinator.advanceDailyTask {}
        coordinator.advanceDailyTask {}

        assertEquals(1, coordinator.dailyState.value.dailySession.blockIndex)
    }

    @Test
    fun advanceDailyTask_fromVocabToVerbs_crossesBlockBoundary() = runBlocking {
        startActiveSession()

        // Skip translate block
        coordinator.advanceDailyTask {}
        coordinator.advanceDailyTask {}
        coordinator.advanceDailyTask {} // now at vocab1
        assertEquals(DailyBlockType.VOCAB, coordinator.getCurrentBlockType())

        // Advance through vocab
        coordinator.advanceDailyTask {} // vocab1 -> vocab2
        coordinator.advanceDailyTask {} // vocab2 -> verb1

        assertEquals(DailyBlockType.VERBS, coordinator.getCurrentBlockType())
        assertEquals(2, coordinator.dailyState.value.dailySession.blockIndex)
    }

    @Test
    fun advanceDailyTask_pastLastTask_endsSession() = runBlocking {
        startActiveSession()

        // Advance through all 7 tasks
        repeat(6) { coordinator.advanceDailyTask {} } // now at last task

        // Last advance triggers endSession
        val result = coordinator.advanceDailyTask {}
        assertFalse(result) // no more tasks
        assertFalse(coordinator.dailyState.value.dailySession.active)
        assertTrue(coordinator.dailyState.value.dailySession.finishedToken)
    }

    @Test
    fun advanceDailyTask_withVerbTask_callsOnPersistVerbProgress() = runBlocking {
        startActiveSession()

        // Move to the first verb task (index 5 = verb_vc1)
        repeat(4) { coordinator.advanceDailyTask {} } // now at verb1 (index 5)

        var persistedCard: VerbDrillCard? = null
        // Advance from verb1 -- should persist verb1's card
        coordinator.advanceDailyTask { card -> persistedCard = card }

        assertNotNull(persistedCard)
        assertEquals("vc1", persistedCard!!.id)
    }

    @Test
    fun advanceDailyTask_withTranslateTask_doesNotCallOnPersistVerbProgress() = runBlocking {
        startActiveSession()

        var persisted = false
        coordinator.advanceDailyTask { persisted = true }

        assertFalse(persisted)
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. advanceToNextBlock / advanceDailyBlock
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun advanceToNextBlock_fromTranslate_skipsToVocab() = runBlocking {
        startActiveSession()
        assertEquals(DailyBlockType.TRANSLATE, coordinator.getCurrentBlockType())

        val result = coordinator.advanceToNextBlock()
        assertTrue(result)
        assertEquals(DailyBlockType.VOCAB, coordinator.getCurrentBlockType())
        assertEquals(1, coordinator.dailyState.value.dailySession.blockIndex)
    }

    @Test
    fun advanceToNextBlock_fromVocab_skipsToVerbs() = runBlocking {
        startActiveSession()
        coordinator.advanceToNextBlock() // TRANSLATE -> VOCAB

        val result = coordinator.advanceToNextBlock()
        assertTrue(result)
        assertEquals(DailyBlockType.VERBS, coordinator.getCurrentBlockType())
        assertEquals(2, coordinator.dailyState.value.dailySession.blockIndex)
    }

    @Test
    fun advanceToNextBlock_fromVerbs_atLastBlock_endsSession() = runBlocking {
        startActiveSession()
        coordinator.advanceToNextBlock() // -> VOCAB
        coordinator.advanceToNextBlock() // -> VERBS

        val result = coordinator.advanceToNextBlock() // past VERBS
        assertFalse(result)
        assertFalse(coordinator.dailyState.value.dailySession.active)
        assertTrue(coordinator.dailyState.value.dailySession.finishedToken)
    }

    @Test
    fun advanceDailyBlock_delegatesToAdvanceToNextBlock() = runBlocking {
        startActiveSession()
        val result = coordinator.advanceDailyBlock()
        assertTrue(result)
        assertEquals(DailyBlockType.VOCAB, coordinator.getCurrentBlockType())
    }

    @Test
    fun advanceToNextBlock_midBlock_skipsRemainingTasksInBlock() = runBlocking {
        startActiveSession()
        // We are at index 0 (first translate card)
        // advanceToNextBlock should skip tc2, tc3 and land on vocab1
        coordinator.advanceToNextBlock()

        assertEquals(DailyBlockType.VOCAB, coordinator.getCurrentBlockType())
        // taskIndex should point to first VOCAB task (index 3)
        assertEquals(3, coordinator.dailyState.value.dailySession.taskIndex)
    }

    @Test
    fun advanceToNextBlock_noActiveSession_returnsFalse() {
        // No session started
        val result = coordinator.advanceToNextBlock()
        assertFalse(result)
    }

    // ══════════════════════════════════════════════════════════════════
    // 4. cancelDailySession() -- cursor advancement logic
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun cancelDailySession_notFinished_returnsNull() = runBlocking {
        startActiveSession()
        // Session is active but not finished
        val result = coordinator.cancelDailySession()
        assertNull(result)
    }

    @Test
    fun cancelDailySession_finishedButIncomplete_returnsNull() = runBlocking {
        startActiveSession()
        // End session directly (no cards practiced via VOICE/KEYBOARD)
        coordinator.endSession()
        // finishedToken is true, but dailyPracticeAnsweredCounts is empty
        val result = coordinator.cancelDailySession()
        assertNull(result)
    }

    @Test
    fun cancelDailySession_finishedAllSentenceAndVerbCards_returnsSentenceCount() = runBlocking {
        startActiveSession()

        // Record card practiced for all translate cards (3) and all verb cards (2)
        repeat(3) {
            coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE) { _ -> "lesson-1" }
            coordinator.advanceDailyTask {} // advance past translate tasks
        }
        // Now at VOCAB -- skip past vocab
        coordinator.advanceToNextBlock()
        // Now at VERBS
        repeat(2) {
            coordinator.recordDailyCardPracticed(DailyBlockType.VERBS) { _ -> "lesson-1" }
            coordinator.advanceDailyTask {}
        }
        // Session should have ended (past last task)

        val result = coordinator.cancelDailySession()
        // 3 translate cards practiced = sentence count to advance
        assertEquals(3, result)
    }

    @Test
    fun cancelDailySession_partialTranslate_returnsNull() = runBlocking {
        startActiveSession()

        // Only practice 2 of 3 translate cards
        coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE) { _ -> "lesson-1" }
        coordinator.advanceDailyTask {}
        coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE) { _ -> "lesson-1" }

        // End session manually
        coordinator.endSession()
        // Not all translate cards practiced (2 < 3)
        val result = coordinator.cancelDailySession()
        assertNull(result)
    }

    @Test
    fun cancelDailySession_partialVerbs_returnsNull() = runBlocking {
        startActiveSession()

        // Practice all translate cards
        repeat(3) {
            coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE) { _ -> "lesson-1" }
            coordinator.advanceDailyTask {}
        }
        coordinator.advanceToNextBlock() // skip vocab

        // Practice only 1 of 2 verb cards
        coordinator.recordDailyCardPracticed(DailyBlockType.VERBS) { _ -> "lesson-1" }

        coordinator.endSession()
        // All translate practiced (3 >= 3) but not all verbs (1 < 2)
        val result = coordinator.cancelDailySession()
        assertNull(result)
    }

    @Test
    fun cancelDailySession_clearsAnsweredCounts() = runBlocking {
        startActiveSession()
        coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE) { _ -> "lesson-1" }
        coordinator.endSession()

        coordinator.cancelDailySession()
        // After cancel, if we cancel again immediately, answered counts are empty
        val result2 = coordinator.cancelDailySession()
        assertNull(result2)
    }

    @Test
    fun cancelDailySession_endsSession() = runBlocking {
        startActiveSession()
        assertTrue(coordinator.dailyState.value.dailySession.active)

        coordinator.cancelDailySession()
        assertFalse(coordinator.dailyState.value.dailySession.active)
    }

    @Test
    fun cancelDailySession_midBlock_endsSession() = runBlocking {
        startActiveSession()
        coordinator.advanceDailyTask {} // advance to tc2

        val result = coordinator.cancelDailySession()
        assertNull(result) // not finished
        assertFalse(coordinator.dailyState.value.dailySession.active)
    }

    // ══════════════════════════════════════════════════════════════════
    // 5. endSession()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun endSession_setsActiveFalseAndFinishedTokenTrue() = runBlocking {
        startActiveSession()
        coordinator.endSession()

        val ds = coordinator.dailyState.value.dailySession
        assertFalse(ds.active)
        assertTrue(ds.finishedToken)
    }

    @Test
    fun endSession_preservesTasks() = runBlocking {
        startActiveSession()
        coordinator.endSession()

        val ds = coordinator.dailyState.value.dailySession
        assertEquals(7, ds.tasks.size)
    }

    @Test
    fun endSession_savesProgress() = runBlocking {
        startActiveSession()
        val savesBefore = stateAccess.saveCount

        coordinator.endSession()
        assertTrue(stateAccess.saveCount > savesBefore)
    }

    // ══════════════════════════════════════════════════════════════════
    // 6. repeatDailyPractice()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun repeatDailyPractice_withCachedTasks_reusesTasks() = runBlocking {
        setNavigationState()
        coordinator.injectPrebuiltSession(standardTasks)

        // Start first session to cache tasks
        coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 3 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )
        coordinator.endSession()

        // Repeat -- should use cached tasks
        val result = coordinator.repeatDailyPractice(
            lessonLevel = 3,
            resolveProgressLessonInfo = { "lesson-1" to 3 }
        )
        assertTrue(result)

        val ds = coordinator.dailyState.value.dailySession
        assertTrue(ds.active)
        assertEquals(0, ds.taskIndex)
    }

    @Test
    fun repeatDailyPractice_noCacheNoStoredCards_returnsFalse() = runBlocking {
        setNavigationState()
        coordinator.resetState()

        val result = coordinator.repeatDailyPractice(
            lessonLevel = 3,
            resolveProgressLessonInfo = { "lesson-1" to 3 }
        )
        // With empty mock stores, the fallback builder produces empty tasks
        assertFalse(result)
    }

    @Test
    fun repeatDailyPractice_noActivePack_returnsFalse() = runBlocking {
        stateFlow.value = TrainingUiState(
            navigation = NavigationState(activePackId = null)
        )

        val result = coordinator.repeatDailyPractice(
            lessonLevel = 3,
            resolveProgressLessonInfo = { "lesson-1" to 3 }
        )
        assertFalse(result)
    }

    @Test
    fun repeatDailyPractice_resolveProgressReturnsNull_returnsFalse() = runBlocking {
        setNavigationState()
        coordinator.injectPrebuiltSession(standardTasks)

        val result = coordinator.repeatDailyPractice(
            lessonLevel = 3,
            resolveProgressLessonInfo = { null }
        )
        assertFalse(result)
    }

    @Test
    fun repeatDailyPractice_startsSessionAtTaskIndexZero() = runBlocking {
        setNavigationState()
        coordinator.injectPrebuiltSession(standardTasks)

        // Start and complete first session
        coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 3 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )
        coordinator.endSession()

        // Repeat
        coordinator.repeatDailyPractice(
            lessonLevel = 3,
            resolveProgressLessonInfo = { "lesson-1" to 3 }
        )

        assertEquals(0, coordinator.dailyState.value.dailySession.taskIndex)
    }

    // ══════════════════════════════════════════════════════════════════
    // 7. getBlockProgress()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun getBlockProgress_noActiveSession_returnsEmpty() {
        val progress = coordinator.getBlockProgress()
        assertEquals(BlockProgress.Empty, progress)
    }

    @Test
    fun getBlockProgress_atStartOfTranslateBlock_returnsCorrectPosition() = runBlocking {
        startActiveSession()

        val progress = coordinator.getBlockProgress()
        assertEquals(DailyBlockType.TRANSLATE, progress.blockType)
        assertEquals(1, progress.positionInBlock)  // 1-based
        assertEquals(3, progress.blockSize)
        assertEquals(7, progress.totalTasks)
        assertEquals(1, progress.globalPosition)  // 1-based
    }

    @Test
    fun getBlockProgress_midTranslateBlock_returnsCorrectPosition() = runBlocking {
        startActiveSession()
        coordinator.advanceDailyTask {} // index 1

        val progress = coordinator.getBlockProgress()
        assertEquals(DailyBlockType.TRANSLATE, progress.blockType)
        assertEquals(2, progress.positionInBlock)
        assertEquals(3, progress.blockSize)
        assertEquals(2, progress.globalPosition)
    }

    @Test
    fun getBlockProgress_atVocabBlock_returnsCorrectPosition() = runBlocking {
        startActiveSession()
        coordinator.advanceToNextBlock() // skip to vocab

        val progress = coordinator.getBlockProgress()
        assertEquals(DailyBlockType.VOCAB, progress.blockType)
        assertEquals(1, progress.positionInBlock)
        assertEquals(2, progress.blockSize)
        assertEquals(4, progress.globalPosition)
    }

    @Test
    fun getBlockProgress_atVerbsBlock_returnsCorrectPosition() = runBlocking {
        startActiveSession()
        coordinator.advanceToNextBlock() // -> vocab
        coordinator.advanceToNextBlock() // -> verbs

        val progress = coordinator.getBlockProgress()
        assertEquals(DailyBlockType.VERBS, progress.blockType)
        assertEquals(1, progress.positionInBlock)
        assertEquals(2, progress.blockSize)
        assertEquals(6, progress.globalPosition)
    }

    // ══════════════════════════════════════════════════════════════════
    // 8. replaceCurrentBlock()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun replaceCurrentBlock_replacesTranslateBlock() = runBlocking {
        startActiveSession()
        val newTasks = listOf(
            DailyTask.TranslateSentence("new1", translateCard1, InputMode.KEYBOARD),
            DailyTask.TranslateSentence("new2", translateCard2, InputMode.VOICE)
        )

        coordinator.replaceCurrentBlock(newTasks)

        val ds = coordinator.dailyState.value.dailySession
        // Original: 3 translate + 2 vocab + 2 verb = 7
        // Replaced translate block: 2 + 2 vocab + 2 verb = 6
        assertEquals(6, ds.tasks.size)
        assertEquals("new1", ds.tasks[0].id)
        assertEquals("new2", ds.tasks[1].id)
    }

    @Test
    fun replaceCurrentBlock_noActiveSession_doesNothing() {
        coordinator.replaceCurrentBlock(
            listOf(DailyTask.TranslateSentence("x", translateCard1, InputMode.KEYBOARD))
        )
        // No crash, state unchanged
        assertFalse(coordinator.dailyState.value.dailySession.active)
    }

    @Test
    fun replaceCurrentBlock_setsTaskIndexToBlockStart() = runBlocking {
        startActiveSession()
        // Advance to second card in translate block
        coordinator.advanceDailyTask {}
        assertEquals(1, coordinator.dailyState.value.dailySession.taskIndex)

        val newTasks = listOf(
            DailyTask.TranslateSentence("new1", translateCard1, InputMode.KEYBOARD)
        )
        coordinator.replaceCurrentBlock(newTasks)

        assertEquals(0, coordinator.dailyState.value.dailySession.taskIndex)
    }

    // ══════════════════════════════════════════════════════════════════
    // 9. Verb progress persistence
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun persistDailyVerbProgress_savesCardToVerbDrillStore() = runBlocking {
        setNavigationState()
        startActiveSession()

        coordinator.persistDailyVerbProgress(verbCard1)

        val progressMap = mockVerbDrillStore.storedProgress
        assertTrue(progressMap.isNotEmpty())

        val comboKey = "regular|Presente"
        assertTrue(progressMap.containsKey(comboKey))
        val progress = progressMap[comboKey]!!
        assertTrue(progress.everShownCardIds.contains("vc1"))
        assertTrue(progress.todayShownCardIds.contains("vc1"))
    }

    @Test
    fun persistDailyVerbProgress_addsToExistingProgress() = runBlocking {
        setNavigationState()
        startActiveSession()

        // First persist
        coordinator.persistDailyVerbProgress(verbCard1)
        // Second persist for same combo
        coordinator.persistDailyVerbProgress(verbCard2)

        val comboKey = "regular|Presente"
        val progress = mockVerbDrillStore.storedProgress[comboKey]!!
        assertTrue(progress.everShownCardIds.contains("vc1"))
        assertTrue(progress.everShownCardIds.contains("vc2"))
    }

    @Test
    fun persistDailyVerbProgress_noActivePack_doesNothing() {
        stateFlow.value = TrainingUiState(
            navigation = NavigationState(activePackId = null)
        )
        // Should not crash
        coordinator.persistDailyVerbProgress(verbCard1)
        assertTrue(mockVerbDrillStore.storedProgress.isEmpty())
    }

    @Test
    fun advanceDailyTask_persistVerbProgress_calledForVerbTasks() = runBlocking {
        startActiveSession()

        // Navigate to verb block
        coordinator.advanceToNextBlock() // -> VOCAB
        coordinator.advanceToNextBlock() // -> VERBS
        // Now at verb_vc1 (index 5)

        var persistedCard: VerbDrillCard? = null
        coordinator.advanceDailyTask { card -> persistedCard = card }

        assertNotNull(persistedCard)
        assertEquals("vc1", persistedCard!!.id)
    }

    // ══════════════════════════════════════════════════════════════════
    // 10. Vocab card rating (SRS)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun rateVocabCard_again_resetsIntervalToZero() = runBlocking {
        setNavigationState()
        startActiveSession()
        // Navigate to vocab block
        coordinator.advanceToNextBlock()
        // Set existing mastery for the word
        mockWordMasteryStore.setMastery(WordMasteryState(wordId = "vw1", intervalStepIndex = 5, correctCount = 3))

        coordinator.rateVocabCard(SrsRating.AGAIN)

        val mastery = mockWordMasteryStore.getMastery("vw1")!!
        assertEquals(0, mastery.intervalStepIndex)
        assertEquals(1, mastery.incorrectCount)
        assertEquals(3, mastery.correctCount) // correctCount unchanged for AGAIN
    }

    @Test
    fun rateVocabCard_hard_staysAtCurrentStep() = runBlocking {
        setNavigationState()
        startActiveSession()
        coordinator.advanceToNextBlock()
        mockWordMasteryStore.setMastery(WordMasteryState(wordId = "vw1", intervalStepIndex = 3))

        coordinator.rateVocabCard(SrsRating.HARD)

        val mastery = mockWordMasteryStore.getMastery("vw1")!!
        assertEquals(3, mastery.intervalStepIndex)
        assertEquals(1, mastery.correctCount)
    }

    @Test
    fun rateVocabCard_good_advancesOneStep() = runBlocking {
        setNavigationState()
        startActiveSession()
        coordinator.advanceToNextBlock()
        mockWordMasteryStore.setMastery(WordMasteryState(wordId = "vw1", intervalStepIndex = 3))

        coordinator.rateVocabCard(SrsRating.GOOD)

        val mastery = mockWordMasteryStore.getMastery("vw1")!!
        assertEquals(4, mastery.intervalStepIndex)
        assertEquals(1, mastery.correctCount)
    }

    @Test
    fun rateVocabCard_easy_advancesTwoSteps() = runBlocking {
        setNavigationState()
        startActiveSession()
        coordinator.advanceToNextBlock()
        mockWordMasteryStore.setMastery(WordMasteryState(wordId = "vw1", intervalStepIndex = 3))

        coordinator.rateVocabCard(SrsRating.EASY)

        val mastery = mockWordMasteryStore.getMastery("vw1")!!
        assertEquals(5, mastery.intervalStepIndex)
        assertEquals(1, mastery.correctCount)
    }

    @Test
    fun rateVocabCard_easy_atMaxStep_doesNotExceedMax() = runBlocking {
        setNavigationState()
        startActiveSession()
        coordinator.advanceToNextBlock()
        val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        mockWordMasteryStore.setMastery(WordMasteryState(wordId = "vw1", intervalStepIndex = maxStep))

        coordinator.rateVocabCard(SrsRating.EASY)

        val mastery = mockWordMasteryStore.getMastery("vw1")!!
        assertEquals(maxStep, mastery.intervalStepIndex) // clamped at max
    }

    @Test
    fun rateVocabCard_good_newWord_advancesFromZero() = runBlocking {
        setNavigationState()
        startActiveSession()
        coordinator.advanceToNextBlock()
        // No existing mastery -- rateVocabCard creates new state

        coordinator.rateVocabCard(SrsRating.GOOD)

        val mastery = mockWordMasteryStore.getMastery("vw1")!!
        assertEquals(1, mastery.intervalStepIndex)
        assertEquals(1, mastery.correctCount)
        assertEquals(0, mastery.incorrectCount)
    }

    @Test
    fun rateVocabCard_again_newWord_staysAtZero() = runBlocking {
        setNavigationState()
        startActiveSession()
        coordinator.advanceToNextBlock()

        coordinator.rateVocabCard(SrsRating.AGAIN)

        val mastery = mockWordMasteryStore.getMastery("vw1")!!
        assertEquals(0, mastery.intervalStepIndex)
        assertEquals(1, mastery.incorrectCount)
        assertEquals(0, mastery.correctCount)
    }

    @Test
    fun rateVocabCard_good_reachesLearnedThreshold_marksLearned() = runBlocking {
        setNavigationState()
        startActiveSession()
        coordinator.advanceToNextBlock()
        // LEARNED_THRESHOLD = 3, so step index 3 means learned
        mockWordMasteryStore.setMastery(WordMasteryState(wordId = "vw1", intervalStepIndex = 2))

        coordinator.rateVocabCard(SrsRating.GOOD)

        val mastery = mockWordMasteryStore.getMastery("vw1")!!
        assertTrue(mastery.isLearned)
        assertEquals(3, mastery.intervalStepIndex)
    }

    @Test
    fun rateVocabCard_notAtVocabTask_doesNothing() = runBlocking {
        setNavigationState()
        startActiveSession()
        // We are at TRANSLATE block, not VOCAB

        coordinator.rateVocabCard(SrsRating.GOOD)

        // No mastery should be stored for a non-vocab task
        assertTrue(mockWordMasteryStore.getAllMastery().isEmpty())
    }

    @Test
    fun rateVocabCard_noActivePack_doesNothing() {
        stateFlow.value = TrainingUiState(
            navigation = NavigationState(activePackId = null)
        )
        // Should not crash
        coordinator.rateVocabCard(SrsRating.GOOD)
        assertTrue(mockWordMasteryStore.getAllMastery().isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════
    // 11. Answer submission
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun submitDailySentenceAnswer_correctAnswer_returnsTrue() = runBlocking {
        startActiveSession()

        val result = coordinator.submitDailySentenceAnswer("hello")
        assertTrue(result)
    }

    @Test
    fun submitDailySentenceAnswer_wrongAnswer_returnsFalse() = runBlocking {
        startActiveSession()

        val result = coordinator.submitDailySentenceAnswer("wrong")
        assertFalse(result)
    }

    @Test
    fun submitDailySentenceAnswer_notAtTranslateTask_returnsFalse() = runBlocking {
        startActiveSession()
        coordinator.advanceToNextBlock() // -> VOCAB

        val result = coordinator.submitDailySentenceAnswer("hello")
        assertFalse(result)
    }

    @Test
    fun submitDailySentenceAnswer_noActiveSession_returnsFalse() {
        val result = coordinator.submitDailySentenceAnswer("hello")
        assertFalse(result)
    }

    @Test
    fun submitDailyVerbAnswer_correctAnswer_returnsTrue() = runBlocking {
        startActiveSession()
        coordinator.advanceToNextBlock() // -> VOCAB
        coordinator.advanceToNextBlock() // -> VERBS

        val result = coordinator.submitDailyVerbAnswer("parlo")
        assertTrue(result)
    }

    @Test
    fun submitDailyVerbAnswer_wrongAnswer_returnsFalse() = runBlocking {
        startActiveSession()
        coordinator.advanceToNextBlock()
        coordinator.advanceToNextBlock()

        val result = coordinator.submitDailyVerbAnswer("wrong")
        assertFalse(result)
    }

    @Test
    fun submitDailyVerbAnswer_notAtVerbTask_returnsFalse() = runBlocking {
        startActiveSession()

        val result = coordinator.submitDailyVerbAnswer("parlo")
        assertFalse(result)
    }

    // ══════════════════════════════════════════════════════════════════
    // 12. Answer retrieval
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun getDailySentenceAnswer_atTranslateTask_returnsFirstAnswer() = runBlocking {
        startActiveSession()

        val answer = coordinator.getDailySentenceAnswer()
        assertEquals("hello", answer)
    }

    @Test
    fun getDailySentenceAnswer_notAtTranslateTask_returnsNull() = runBlocking {
        startActiveSession()
        coordinator.advanceToNextBlock() // -> VOCAB

        assertNull(coordinator.getDailySentenceAnswer())
    }

    @Test
    fun getDailyVerbAnswer_atVerbTask_returnsAnswer() = runBlocking {
        startActiveSession()
        coordinator.advanceToNextBlock()
        coordinator.advanceToNextBlock()

        val answer = coordinator.getDailyVerbAnswer()
        assertEquals("parlo", answer)
    }

    @Test
    fun getDailyVerbAnswer_notAtVerbTask_returnsNull() = runBlocking {
        startActiveSession()

        assertNull(coordinator.getDailyVerbAnswer())
    }

    // ══════════════════════════════════════════════════════════════════
    // 13. recordDailyCardPracticed
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun recordDailyCardPracticed_translateBlock_recordsCardShow() = runBlocking {
        startActiveSession()

        coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE) { _ -> "lesson-1" }

        assertTrue(mockMasteryStore.recordedShows.contains("tc1"))
    }

    @Test
    fun recordDailyCardPracticed_translateBlock_callsResolveCardLessonId() = runBlocking {
        startActiveSession()

        var resolvedCard: SentenceCard? = null
        coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE) { card ->
            resolvedCard = card
            "resolved-lesson"
        }

        assertNotNull(resolvedCard)
        assertEquals("tc1", resolvedCard!!.id)
        assertTrue(mockMasteryStore.recordedShows.contains("tc1"))
        assertEquals("resolved-lesson", mockMasteryStore.lastLessonId)
    }

    @Test
    fun recordDailyCardPracticed_verbBlock_incrementsCountOnly() = runBlocking {
        startActiveSession()
        coordinator.advanceToNextBlock()
        coordinator.advanceToNextBlock()

        coordinator.recordDailyCardPracticed(DailyBlockType.VERBS) { _ -> "lesson-1" }

        // Verb block does not call masteryStore.recordCardShow
        assertFalse(mockMasteryStore.recordedShows.contains("vc1"))
    }

    @Test
    fun recordDailyCardPracticed_vocabBlock_incrementsCountOnly() = runBlocking {
        startActiveSession()
        coordinator.advanceToNextBlock()

        coordinator.recordDailyCardPracticed(DailyBlockType.VOCAB) { _ -> "lesson-1" }

        assertTrue(mockMasteryStore.recordedShows.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════
    // 14. repeatDailyBlock
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun repeatDailyBlock_noActiveSession_returnsFalse() {
        val result = coordinator.repeatDailyBlock { "lesson-1" to 3 }
        assertFalse(result)
    }

    @Test
    fun repeatDailyBlock_resolveProgressReturnsNull_returnsFalse() = runBlocking {
        startActiveSession()

        val result = coordinator.repeatDailyBlock { null }
        assertFalse(result)
    }

    // ══════════════════════════════════════════════════════════════════
    // 15. hasResumableDailySession
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun hasResumableDailySession_noCursor_returnsFalse() {
        assertFalse(coordinator.hasResumableDailySession())
    }

    @Test
    fun hasResumableDailySession_todayWithCardIds_returnsTrue() = runBlocking {
        val today = java.time.LocalDate.now().toString()
        coordinator.updateCursor(DailyCursorState(
            firstSessionDate = today,
            firstSessionSentenceCardIds = listOf("tc1", "tc2")
        ))

        assertTrue(coordinator.hasResumableDailySession())
    }

    @Test
    fun hasResumableDailySession_yesterdayWithCardIds_returnsFalse() {
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        coordinator.updateCursor(DailyCursorState(
            firstSessionDate = yesterday,
            firstSessionSentenceCardIds = listOf("tc1")
        ))

        assertFalse(coordinator.hasResumableDailySession())
    }

    @Test
    fun hasResumableDailySession_todayWithEmptyCardIds_returnsFalse() {
        val today = java.time.LocalDate.now().toString()
        coordinator.updateCursor(DailyCursorState(
            firstSessionDate = today,
            firstSessionSentenceCardIds = emptyList(),
            firstSessionVerbCardIds = emptyList()
        ))

        assertFalse(coordinator.hasResumableDailySession())
    }

    @Test
    fun hasResumableDailySession_todayWithVerbCardIdsOnly_returnsTrue() {
        val today = java.time.LocalDate.now().toString()
        coordinator.updateCursor(DailyCursorState(
            firstSessionDate = today,
            firstSessionSentenceCardIds = emptyList(),
            firstSessionVerbCardIds = listOf("vc1")
        ))

        assertTrue(coordinator.hasResumableDailySession())
    }

    // ══════════════════════════════════════════════════════════════════
    // 16. Cursor management
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun updateCursor_setsNewCursorState() {
        val newCursor = DailyCursorState(
            sentenceOffset = 10,
            currentLessonIndex = 1,
            firstSessionDate = "2026-01-01"
        )
        coordinator.updateCursor(newCursor)

        assertEquals(newCursor, coordinator.getCursor())
    }

    @Test
    fun getCursor_defaultState_returnsDefaultCursor() {
        assertEquals(DailyCursorState(), coordinator.getCursor())
    }

    // ══════════════════════════════════════════════════════════════════
    // 17. resetState
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun resetState_clearsDailyState() = runBlocking {
        startActiveSession()
        assertTrue(coordinator.dailyState.value.dailySession.active)

        coordinator.resetState()

        assertFalse(coordinator.dailyState.value.dailySession.active)
        assertEquals(DailyPracticeState(), coordinator.dailyState.value)
    }

    // ══════════════════════════════════════════════════════════════════
    // 18. clearPrebuiltSession
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun clearPrebuiltSession_afterPrebuild_preventsCachedSessionUse() = runBlocking {
        setNavigationState()
        coordinator.injectPrebuiltSession(standardTasks)
        coordinator.clearPrebuiltSession()

        // startDailyPractice should now fall through to DailySessionComposer
        // which with empty mock stores returns empty -> false
        val result = coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 3 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )
        assertFalse(result)
    }

    // ══════════════════════════════════════════════════════════════════
    // 19. getDailyCurrentTask / getDailyBlockProgress delegation
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun getDailyCurrentTask_delegatesToGetCurrentTask() = runBlocking {
        startActiveSession()

        assertEquals(coordinator.getCurrentTask(), coordinator.getDailyCurrentTask())
    }

    @Test
    fun getDailyBlockProgress_delegatesToGetBlockProgress() = runBlocking {
        startActiveSession()

        assertEquals(coordinator.getBlockProgress(), coordinator.getDailyBlockProgress())
    }

    // ══════════════════════════════════════════════════════════════════
    // 20. Edge cases
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun singleCardPerBlock_threeBlocks_navigatesCorrectly() = runBlocking {
        val singleTasks = listOf(
            DailyTask.TranslateSentence("s1", translateCard1, InputMode.KEYBOARD),
            DailyTask.VocabFlashcard("v1", vocabWord1, VocabDrillDirection.IT_TO_RU),
            DailyTask.ConjugateVerb("vb1", verbCard1, InputMode.KEYBOARD)
        )
        coordinator.injectPrebuiltSession(singleTasks)
        setNavigationState()
        coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 1 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )

        // Start at TRANSLATE
        assertEquals(DailyBlockType.TRANSLATE, coordinator.getCurrentBlockType())

        // Advance to VOCAB
        coordinator.advanceDailyTask {}
        assertEquals(DailyBlockType.VOCAB, coordinator.getCurrentBlockType())

        // Advance to VERBS
        coordinator.advanceDailyTask {}
        assertEquals(DailyBlockType.VERBS, coordinator.getCurrentBlockType())

        // Advance past last task ends session
        val result = coordinator.advanceDailyTask {}
        assertFalse(result)
        assertFalse(coordinator.dailyState.value.dailySession.active)
    }

    @Test
    fun advanceToNextBlock_translateOnlyBlock_endsSession() = runBlocking {
        val translateOnlyTasks = listOf(
            DailyTask.TranslateSentence("s1", translateCard1, InputMode.KEYBOARD),
            DailyTask.TranslateSentence("s2", translateCard2, InputMode.VOICE)
        )
        coordinator.injectPrebuiltSession(translateOnlyTasks)
        setNavigationState()
        coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 1 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )

        // Advance to next block -- no other block types exist
        val result = coordinator.advanceToNextBlock()
        assertFalse(result) // no more blocks
        assertFalse(coordinator.dailyState.value.dailySession.active)
    }

    @Test
    fun interruptedSession_cancelThenRestart_worksCorrectly() = runBlocking {
        setNavigationState()
        coordinator.injectPrebuiltSession(standardTasks)

        // Start session
        coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 3 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )
        assertTrue(coordinator.dailyState.value.dailySession.active)

        // Advance partway
        coordinator.advanceDailyTask {}
        assertEquals(1, coordinator.dailyState.value.dailySession.taskIndex)

        // Cancel
        coordinator.cancelDailySession()
        assertFalse(coordinator.dailyState.value.dailySession.active)

        // Restart
        coordinator.injectPrebuiltSession(standardTasks)
        val result = coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 3 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )
        assertTrue(result)
        assertTrue(coordinator.dailyState.value.dailySession.active)
        assertEquals(0, coordinator.dailyState.value.dailySession.taskIndex)
    }

    @Test
    fun getCurrentTask_noActiveSession_returnsNull() {
        assertNull(coordinator.getCurrentTask())
    }

    @Test
    fun getCurrentBlockType_noActiveSession_returnsNull() {
        assertNull(coordinator.getCurrentBlockType())
    }

    @Test
    fun cancelDailySession_doubleCancel_doesNotCrash() = runBlocking {
        startActiveSession()
        coordinator.cancelDailySession()
        coordinator.cancelDailySession()
        // No crash
    }

    // ══════════════════════════════════════════════════════════════════
    // 21. Store factory delegation
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun getVerbDrillStore_returnsStoreFromFactory() {
        val store = coordinator.getVerbDrillStore("pack-123")
        assertNotNull(store)
        assertSame(mockVerbDrillStore, store)
    }

    @Test
    fun getWordMasteryStore_returnsStoreFromFactory() {
        val store = coordinator.getWordMasteryStore("pack-456")
        assertNotNull(store)
        assertSame(mockWordMasteryStore, store)
    }

    // ══════════════════════════════════════════════════════════════════
    // 22. DailyPracticeState isolation from TrainingUiState
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun dailyStateFlow_independentOfTrainingUiState() = runBlocking {
        startActiveSession()
        val dailyTasks = coordinator.dailyState.value.dailySession.tasks

        // Update TrainingUiState (unrelated change)
        stateFlow.value = stateFlow.value.copy(
            cardSession = CardSessionState(inputText = "changed")
        )

        // Daily state should be unaffected
        assertEquals(dailyTasks, coordinator.dailyState.value.dailySession.tasks)
        assertTrue(coordinator.dailyState.value.dailySession.active)
    }

    // ══════════════════════════════════════════════════════════════════
    // 23. Full lifecycle integration
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun fullLifecycle_traverseAllBlocks_cancelReturnsSentenceCount() = runBlocking {
        setNavigationState()
        coordinator.injectPrebuiltSession(standardTasks)

        // Start
        coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 3 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )

        // Practice all translate cards
        repeat(3) {
            coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE) { _ -> "lesson-1" }
            coordinator.advanceDailyTask {}
        }

        // Skip vocab block
        coordinator.advanceToNextBlock()

        // Practice all verb cards
        repeat(2) {
            coordinator.recordDailyCardPracticed(DailyBlockType.VERBS) { _ -> "lesson-1" }
            coordinator.advanceDailyTask {}
        }

        // Session ended via advanceDailyTask past last task
        assertFalse(coordinator.dailyState.value.dailySession.active)
        assertTrue(coordinator.dailyState.value.dailySession.finishedToken)

        // Cancel should return sentence count
        val sentenceCount = coordinator.cancelDailySession()
        assertEquals(3, sentenceCount)
    }

    @Test
    fun fullLifecycle_cancelEarly_noCursorAdvancement() = runBlocking {
        setNavigationState()
        coordinator.injectPrebuiltSession(standardTasks)

        coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 3 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )

        // Practice 1 card only
        coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE) { _ -> "lesson-1" }
        coordinator.advanceDailyTask {}

        // Cancel mid-session
        val result = coordinator.cancelDailySession()
        assertNull(result) // not finished, no cursor advancement
        assertFalse(coordinator.dailyState.value.dailySession.active)
    }

    // ══════════════════════════════════════════════════════════════════
    // 24. cancelDailySession off-by-one verification
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun cancelDailySession_exactExpectedCounts_returnsExactSentenceCount() = runBlocking {
        // Verify the exact boundary: answered == expected for both blocks
        setNavigationState()
        val tasks = listOf(
            DailyTask.TranslateSentence("s1", translateCard1, InputMode.KEYBOARD),
            DailyTask.ConjugateVerb("v1", verbCard1, InputMode.KEYBOARD)
        )
        coordinator.injectPrebuiltSession(tasks)
        coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 1 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )

        // Practice 1 translate + 1 verb = exactly expected counts
        coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE) { _ -> "lesson-1" }
        coordinator.advanceDailyTask {}
        coordinator.recordDailyCardPracticed(DailyBlockType.VERBS) { _ -> "lesson-1" }
        coordinator.advanceDailyTask {}
        // Session ended

        val result = coordinator.cancelDailySession()
        assertEquals(1, result) // exactly 1 sentence card practiced
    }

    @Test
    fun cancelDailySession_oneFewerTranslateThanExpected_returnsNull() = runBlocking {
        setNavigationState()
        val tasks = listOf(
            DailyTask.TranslateSentence("s1", translateCard1, InputMode.KEYBOARD),
            DailyTask.TranslateSentence("s2", translateCard2, InputMode.KEYBOARD),
            DailyTask.ConjugateVerb("v1", verbCard1, InputMode.KEYBOARD)
        )
        coordinator.injectPrebuiltSession(tasks)
        coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 1 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )

        // Practice only 1 of 2 translate cards, but all verbs
        coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE) { _ -> "lesson-1" }
        coordinator.advanceDailyTask {}
        coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE) { _ -> "lesson-1" }
        coordinator.advanceDailyTask {}
        coordinator.recordDailyCardPracticed(DailyBlockType.VERBS) { _ -> "lesson-1" }
        coordinator.advanceDailyTask {}

        // But we only counted 2 TRANSLATE answers for 2 translate tasks - this should pass
        // Actually, 2 practiced >= 2 expected = true. Let me test the real off-by-one:
        // Only record 1 TRANSLATE answer (forget the second)
        // Reset and retry
        coordinator.cancelDailySession()
        coordinator.resetState()

        // New test: only 1 TRANSLATE answer for 2 expected translate tasks
        coordinator.injectPrebuiltSession(tasks)
        coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 1 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )

        coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE) { _ -> "lesson-1" }
        coordinator.advanceDailyTask {}
        // Skip recording for second translate card
        coordinator.advanceDailyTask {}
        coordinator.recordDailyCardPracticed(DailyBlockType.VERBS) { _ -> "lesson-1" }
        coordinator.advanceDailyTask {}

        val result = coordinator.cancelDailySession()
        // 1 translate answer < 2 expected -> null
        assertNull(result)
    }

    // ══════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════

    /**
     * Set up navigation state with active pack and language.
     */
    private fun setNavigationState() {
        stateFlow.value = TrainingUiState(
            navigation = NavigationState(
                activePackId = PackId("pack-1"),
                selectedLanguageId = LanguageId("en")
            )
        )
    }

    /**
     * Start an active session with standardTasks via prebuilt cache injection.
     */
    private suspend fun startActiveSession() {
        setNavigationState()
        coordinator.injectPrebuiltSession(standardTasks)
        coordinator.startDailyPractice(
            resolveProgressLessonInfo = { "lesson-1" to 3 },
            onStoreFirstSessionCardIds = { _, _ -> }
        )
    }

    // ── Test doubles ───────────────────────────────────────────────────

    /**
     * Simple [TrainingStateAccess] backed by a [MutableStateFlow].
     * Records calls for verification.
     */
    private class TestStateAccess(
        private val flow: MutableStateFlow<TrainingUiState>
    ) : TrainingStateAccess {
        var updateCount = 0
        var saveCount = 0

        override val uiState: StateFlow<TrainingUiState> = flow

        override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
            updateCount++
            flow.value = transform(flow.value)
        }

        override fun saveProgress() {
            saveCount++
        }
    }

    /**
     * In-memory [LessonStore] mock. Returns empty data by default.
     * Implements all methods from the interface with no-op stubs.
     */
    private class MockLessonStore : LessonStore {
        override fun ensureSeedData() {}
        override fun seedDefaultPacksIfNeeded(): Boolean = false
        override fun updateDefaultPacksIfNeeded(): Boolean = false
        override fun forceReloadDefaultPacks(): Boolean = false
        override fun getLanguages(): List<Language> = emptyList()
        override fun addLanguage(name: String): Language = Language(LanguageId("en"), "English")
        override fun getInstalledPacks(): List<LessonPack> = emptyList()
        override fun getPackIdForLesson(lessonId: String): String? = null
        override fun getLessonIdsForPack(packId: String): List<String> = emptyList()
        override fun getCumulativeTenses(packId: String, lessonLevel: Int): List<String> = emptyList()
        override fun importPackFromUri(uri: Uri, resolver: ContentResolver): LessonPack =
            LessonPack(packId = PackId("mock"), packVersion = "1", languageId = LanguageId("en"), importedAt = 0L)
        override fun importPackFromAssets(assetPath: String): LessonPack =
            LessonPack(packId = PackId("mock"), packVersion = "1", languageId = LanguageId("en"), importedAt = 0L)
        override fun removeInstalledPackData(packId: String): Boolean = true
        override fun importFromUri(languageId: String, uri: Uri, resolver: ContentResolver): Lesson =
            Lesson(LessonId("mock"), LanguageId(languageId), "Mock", emptyList())
        override fun getLessons(languageId: String): List<Lesson> = emptyList()
        override fun deleteAllLessons(languageId: String) {}
        override fun deleteLesson(languageId: String, lessonId: String) {}
        override fun createEmptyLesson(languageId: String, title: String): Lesson =
            Lesson(LessonId("mock"), LanguageId(languageId), title, emptyList())
        override fun getStoryQuizzes(lessonId: String, phase: StoryPhase, languageId: String): List<StoryQuiz> = emptyList()
        override fun getVocabEntries(lessonId: String, languageId: String): List<VocabEntry> = emptyList()
        override fun getVerbDrillFiles(packId: String, languageId: String): List<File> = emptyList()
        override fun getVerbDrillFilesForPack(packId: String): List<File> = emptyList()
        override fun getVocabDrillFiles(packId: String, languageId: String): List<File> = emptyList()
        override fun getVocabDrillFilesForPack(packId: String): List<File> = emptyList()
        override fun getVocabWordsByRankRange(packId: String, languageId: String, fromRank: Int, toRank: Int): List<VocabWord> = emptyList()
        override fun hasVerbDrill(packId: String, languageId: String): Boolean = false
        override fun hasVocabDrill(packId: String, languageId: String): Boolean = false
        override fun getVerbDrillFiles(languageId: String): List<File> = emptyList()
        @Suppress("DEPRECATION")
        override fun hasVerbDrillLessons(languageId: String): Boolean = false
    }

    /**
     * In-memory [MasteryStore] mock that records card show calls.
     */
    private class MockMasteryStore : MasteryStore {
        val recordedShows = mutableListOf<String>()
        var lastLessonId: String? = null

        override fun recordCardShow(lessonId: String, languageId: String, cardId: String) {
            recordedShows.add(cardId)
            lastLessonId = lessonId
        }

        override fun loadAll(): Map<String, Map<String, LessonMasteryState>> = emptyMap()
        override fun get(lessonId: String, languageId: String): LessonMasteryState? = null
        override fun save(state: LessonMasteryState) {}
        override fun markCardsShownForProgress(lessonId: String, languageId: String, cardIds: Collection<String>) {}
        override fun markLessonCompleted(lessonId: String, languageId: String) {}
        override fun getOrCreate(lessonId: String, languageId: String): LessonMasteryState =
            LessonMasteryState(lessonId = LessonId(lessonId), languageId = LanguageId(languageId))
        override fun clear() {}
        override fun clearLanguage(languageId: String) {}
    }

    /**
     * In-memory [VerbDrillStore] mock.
     */
    private class MockVerbDrillStore : VerbDrillStore {
        val storedProgress = mutableMapOf<String, VerbDrillComboProgress>()

        override fun loadProgress(): Map<String, VerbDrillComboProgress> = storedProgress.toMap()
        override fun saveProgress(progress: Map<String, VerbDrillComboProgress>) {
            storedProgress.putAll(progress)
        }
        override fun getComboProgress(key: String): VerbDrillComboProgress? = storedProgress[key]
        override fun upsertComboProgress(key: String, progress: VerbDrillComboProgress) {
            storedProgress[key] = progress
        }
        override fun loadAllCardsForPack(targetPackId: String, languageId: String): List<VerbDrillCard> = emptyList()
        override fun getCardsForTenses(packId: String, languageId: String, tenses: List<String>): List<VerbDrillCard> = emptyList()
    }

    /**
     * In-memory [WordMasteryStore] mock.
     */
    private class MockWordMasteryStore : WordMasteryStore {
        private val masteryMap = mutableMapOf<String, WordMasteryState>()

        fun setMastery(state: WordMasteryState) {
            masteryMap[state.wordId] = state
        }

        fun getAllMastery(): Map<String, WordMasteryState> = masteryMap.toMap()

        override fun loadAll(): Map<String, WordMasteryState> = masteryMap.toMap()
        override fun saveAll(mastery: Map<String, WordMasteryState>) {
            masteryMap.clear()
            masteryMap.putAll(mastery)
        }
        override fun getMastery(wordId: String): WordMasteryState? = masteryMap[wordId]
        override fun upsertMastery(state: WordMasteryState) {
            masteryMap[state.wordId] = state
        }
        override fun getDueWords(): Set<String> = emptySet()
        override fun getMasteredCount(pos: String?): Int = 0
        override fun getMasteredByPos(): Map<String, Int> = emptyMap()
    }
}

// ── Extension to inject prebuilt sessions via reflection ────────────

/**
 * Inject a prebuilt session into the coordinator's internal cache for testing.
 * Uses reflection because [DailyPracticeCoordinator.prebuiltDailySession] has
 * a private setter.
 */
private fun DailyPracticeCoordinator.injectPrebuiltSession(tasks: List<DailyTask>) {
    val field = this::class.java.getDeclaredField("prebuiltDailySession")
    field.isAccessible = true
    field.set(this, tasks)
}
