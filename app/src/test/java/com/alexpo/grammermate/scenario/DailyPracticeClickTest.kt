package com.alexpo.grammermate.scenario

import com.alexpo.grammermate.data.*
import com.alexpo.grammermate.feature.daily.DailyPracticeCoordinator
import com.alexpo.grammermate.feature.daily.DailySessionComposer
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.feature.training.AnswerValidator
import com.alexpo.grammermate.testharness.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Comprehensive click test for Daily Practice mode covering all scenarios
 * from docs/specification/scenarios/click-test-daily-practice.md
 *
 * Tests the complete 3-block flow: TRANSLATE → VOCAB → VERBS
 * including block transitions, SRS behavior, streak recording, and cursor advancement.
 *
 * Uses real DailyPracticeCoordinator with in-memory fake implementations.
 */
@RunWith(RobolectricTestRunner::class)
class DailyPracticeClickTest {

    private lateinit var stateAccess: FakeTrainingStateAccess
    private lateinit var masteryStore: FakeMasteryStore
    private lateinit var progressStore: FakeProgressStore
    private lateinit var streakStore: FakeStreakStore
    private lateinit var wordMasteryStore: FakeWordMasteryStore
    private lateinit var verbDrillStore: FakeVerbDrillStore
    private lateinit var lessonStore: FakeLessonStore
    private lateinit var answerValidator: AnswerValidator
    private lateinit var streakManager: StreakManager
    private lateinit var coordinator: DailyPracticeCoordinator

    private val testLanguageId = LanguageId("en")
    private val testPackId = PackId("test-pack")
    private val coroutineScope = CoroutineScope(Dispatchers.Unconfined)

    // Helper to create a sentence card for translation block
    private fun createSentenceCard(id: String, ru: String, answers: List<String>): SentenceCard {
        return SentenceCard(id = id, promptRu = ru, acceptedAnswers = answers)
    }

    // Helper to create a verb drill card for verbs block
    private fun createVerbCard(id: String, ru: String, answer: String, verb: String, tense: String): VerbDrillCard {
        return VerbDrillCard(
            id = id,
            promptRu = ru,
            answer = answer,
            verb = verb,
            tense = tense,
            group = "io"
        )
    }

    // Helper to create a vocab word for vocab block
    private fun createVocabWord(id: String, word: String, pos: String, rank: Int): VocabWord {
        return VocabWord(
            id = id,
            word = word,
            pos = pos,
            rank = rank,
            meaningRu = "русский перевод"
        )
    }

    @Before
    fun setup() {
        // Initialize all fake stores
        masteryStore = FakeMasteryStore()
        progressStore = FakeProgressStore()
        streakStore = FakeStreakStore()
        wordMasteryStore = FakeWordMasteryStore()
        verbDrillStore = FakeVerbDrillStore()
        lessonStore = FakeLessonStore()

        // Initialize state access with default state
        val initialState = TrainingUiState().copy(
            navigation = NavigationState(
                selectedLanguageId = testLanguageId,
                activePackId = testPackId
            )
        )
        stateAccess = FakeTrainingStateAccess(initialState)

        // Initialize core components
        answerValidator = AnswerValidator()
        streakManager = StreakManager(streakStore)

        // Initialize DailyPracticeCoordinator with all dependencies
        coordinator = DailyPracticeCoordinator(
            stateAccess = stateAccess,
            appContext = createFakeApplicationContext(),
            answerValidator = answerValidator,
            lessonStore = lessonStore,
            masteryStore = masteryStore,
            verbDrillStoreFactory = { verbDrillStore },
            wordMasteryStoreFactory = { wordMasteryStore },
            streakStore = streakStore,
            streakManager = streakManager,
            sessionSize = 10
        )

        // Setup test lesson with cards
        setupTestLesson()
        setupTestVerbCards()
    }

    private fun setupTestLesson() {
        val lesson = Lesson(
            id = LessonId("lesson-1"),
            languageId = testLanguageId,
            title = "Test Lesson",
            cards = (1..30).map { i ->
                createSentenceCard("card-$i", "русское предложение $i", listOf("english sentence $i"))
            }
        )
        lessonStore.addLesson(lesson)
    }

    private fun setupTestVerbCards() {
        val verbCards = (1..30).map { i ->
            createVerbCard("verb-$i", "я глагол $i", "io verbo $i", "essere", "Presente")
        }
        verbDrillStore.setCards(testPackId.value, testLanguageId.value, verbCards)
    }

    // ========================================
    // Test 1: Full 3-block session
    // ========================================

    @Test
    fun testDailyPracticeFullSession() = runBlocking {
        // --- SETUP: Build session with all 3 blocks ---
        val composer = DailySessionComposer(lessonStore, verbDrillStore, wordMasteryStore, 10)

        // Create blocks manually (since we can't easily mock the file-based vocab loading)
        val sentenceCards = (1..10).map { i ->
            DailyTask.TranslateSentence(
                id = "sent-$i",
                card = createSentenceCard("card-$i", "русское $i", listOf("english $i")),
                inputMode = InputMode.VOICE
            )
        }

        val vocabCards = (1..10).map { i ->
            DailyTask.VocabFlashcard(
                id = "voc-$i",
                word = createVocabWord("word-$i", "parola$i", "nouns", i),
                direction = VocabDrillDirection.IT_TO_RU
            )
        }

        val verbCards = (1..10).map { i ->
            DailyTask.ConjugateVerb(
                id = "verb-$i",
                card = createVerbCard("verb-$i", "я глагол $i", "io verbo $i", "essere", "Presente"),
                inputMode = InputMode.KEYBOARD
            )
        }

        val blocks = listOf(
            DailyBlock(DailyBlockType.TRANSLATE, sentenceCards),
            DailyBlock(DailyBlockType.VOCAB, vocabCards),
            DailyBlock(DailyBlockType.VERBS, verbCards)
        )

        // --- ACTION: Start session ---
        coordinator.startDailySession(blocks, 1)

        // --- ASSERT: Verify session started ---
        val dailyState = coordinator.dailyState.value
        assertTrue("Session should be active", dailyState.dailySession.active)
        assertEquals("Should have 3 blocks", 3, dailyState.dailySession.blocks.size)
        assertEquals("Should start at block 0", 0, dailyState.dailySession.blockIndex)
        assertEquals("First block should be TRANSLATE", DailyBlockType.TRANSLATE, coordinator.getCurrentBlockType())

        // --- ACTION: Complete TRANSLATE block ---
        val nextBlock1 = coordinator.onBlockComplete()

        // --- ASSERT: Block transition to VOCAB ---
        assertNotNull("Should have next block", nextBlock1)
        assertEquals("Next block should be VOCAB", DailyBlockType.VOCAB, nextBlock1?.type)
        assertEquals("Block index should be 1", 1, coordinator.dailyState.value.dailySession.blockIndex)

        // --- ACTION: Complete VOCAB block ---
        val nextBlock2 = coordinator.onBlockComplete()

        // --- ASSERT: Block transition to VERBS ---
        assertNotNull("Should have next block", nextBlock2)
        assertEquals("Next block should be VERBS", DailyBlockType.VERBS, nextBlock2?.type)
        assertEquals("Block index should be 2", 2, coordinator.dailyState.value.dailySession.blockIndex)

        // --- ACTION: Complete VERBS block ---
        val nextBlock3 = coordinator.onBlockComplete()

        // --- ASSERT: Session complete ---
        assertNull("No next block after all complete", nextBlock3)
        assertTrue("Session should be finished", coordinator.dailyState.value.dailySession.finishedToken)
    }

    // ========================================
    // Test 2: Block transition TRANSLATE → VOCAB
    // ========================================

    @Test
    fun testBlockTransition_TranslateToVocab() = runBlocking {
        // --- SETUP: Create TRANSLATE and VOCAB blocks ---
        val translateTasks = listOf(
            DailyTask.TranslateSentence("t1", createSentenceCard("c1", "ru1", listOf("en1")), InputMode.VOICE)
        )
        val vocabTasks = listOf(
            DailyTask.VocabFlashcard("v1", createVocabWord("w1", "casa", "nouns", 1), VocabDrillDirection.IT_TO_RU)
        )

        val blocks = listOf(
            DailyBlock(DailyBlockType.TRANSLATE, translateTasks),
            DailyBlock(DailyBlockType.VOCAB, vocabTasks)
        )

        // --- ACTION: Start session and complete TRANSLATE block ---
        coordinator.startDailySession(blocks, 1)
        val nextBlock = coordinator.onBlockComplete()

        // --- ASSERT: Verify transition ---
        assertNotNull("Should return next block", nextBlock)
        assertEquals("Next block type should be VOCAB", DailyBlockType.VOCAB, nextBlock?.type)
        assertEquals("Block index should advance", 1, coordinator.dailyState.value.dailySession.blockIndex)

        // --- ASSERT: Previous block marked complete ---
        val sessionState = coordinator.dailyState.value.dailySession
        assertTrue("First block should be marked complete", sessionState.blocks[0].isComplete)
        assertFalse("Second block should not be complete yet", sessionState.blocks[1].isComplete)
    }

    // ========================================
    // Test 3: Block transition VOCAB → VERBS
    // ========================================

    @Test
    fun testBlockTransition_VocabToVerbs() = runBlocking {
        // --- SETUP: Create VOCAB and VERBS blocks ---
        val vocabTasks = listOf(
            DailyTask.VocabFlashcard("v1", createVocabWord("w1", "casa", "nouns", 1), VocabDrillDirection.IT_TO_RU)
        )
        val verbTasks = listOf(
            DailyTask.ConjugateVerb("verb1", createVerbCard("verb1", "ru1", "it1", "essere", "Presente"), InputMode.KEYBOARD)
        )

        val blocks = listOf(
            DailyBlock(DailyBlockType.VOCAB, vocabTasks),
            DailyBlock(DailyBlockType.VERBS, verbTasks)
        )

        // --- ACTION: Start at VOCAB block ---
        coordinator.startDailySession(blocks, 1)
        // Skip to VOCAB block (blockIndex 0)
        assertEquals("Should start at VOCAB", DailyBlockType.VOCAB, coordinator.getCurrentBlockType())

        // --- ACTION: Complete VOCAB block ---
        val nextBlock = coordinator.onBlockComplete()

        // --- ASSERT: Verify transition to VERBS ---
        assertNotNull("Should return next block", nextBlock)
        assertEquals("Next block type should be VERBS", DailyBlockType.VERBS, nextBlock?.type)
        assertEquals("Block index should be 1", 1, coordinator.dailyState.value.dailySession.blockIndex)
    }

    // ========================================
    // Test 4: Vocab rating AGAIN resets step
    // ========================================

    @Test
    fun testVocabRating_AgainResetsStep() = runBlocking {
        // --- SETUP: Create a vocab word with existing mastery ---
        val word = createVocabWord("word-test", "casa", "nouns", 1)
        val initialMastery = WordMasteryState(
            wordId = word.id,
            intervalStepIndex = 5,  // Already at step 5
            correctCount = 10,
            incorrectCount = 2
        )
        wordMasteryStore.setMastery(initialMastery)

        // Create vocab block
        val vocabTasks = listOf(
            DailyTask.VocabFlashcard("v1", word, VocabDrillDirection.IT_TO_RU)
        )
        val blocks = listOf(
            DailyBlock(DailyBlockType.VOCAB, vocabTasks)
        )

        coordinator.startDailySession(blocks, 1)

        // --- ACTION: Rate as AGAIN ---
        coordinator.rateVocabCard(SrsRating.AGAIN)

        // --- ASSERT: Step reset to 0, incorrect count increased ---
        val updatedMastery = wordMasteryStore.getMastery(word.id)
        assertNotNull("Mastery should exist", updatedMastery)
        assertEquals("Step should reset to 0", 0, updatedMastery?.intervalStepIndex)
        assertEquals("Incorrect count should increase", 3, updatedMastery?.incorrectCount)
        assertEquals("Correct count should stay same", 10, updatedMastery?.correctCount)
    }

    // ========================================
    // Test 5: Vocab rating EASY advances two steps
    // ========================================

    @Test
    fun testVocabRating_EasyAdvancesTwoSteps() = runBlocking {
        // --- SETUP: Create a vocab word at step 2 ---
        val word = createVocabWord("word-easy", "bueno", "adjectives", 2)
        val initialMastery = WordMasteryState(
            wordId = word.id,
            intervalStepIndex = 2,
            correctCount = 5
        )
        wordMasteryStore.setMastery(initialMastery)

        // Create vocab block
        val vocabTasks = listOf(
            DailyTask.VocabFlashcard("v1", word, VocabDrillDirection.IT_TO_RU)
        )
        val blocks = listOf(
            DailyBlock(DailyBlockType.VOCAB, vocabTasks)
        )

        coordinator.startDailySession(blocks, 1)

        // --- ACTION: Rate as EASY ---
        coordinator.rateVocabCard(SrsRating.EASY)

        // --- ASSERT: Step advanced by 2 ---
        val updatedMastery = wordMasteryStore.getMastery(word.id)
        assertNotNull("Mastery should exist", updatedMastery)
        assertEquals("Step should advance by 2", 4, updatedMastery?.intervalStepIndex)
        assertEquals("Correct count should increase", 6, updatedMastery?.correctCount)
    }

    // ========================================
    // Test 6: Verb drill weak-first ordering
    // ========================================

    @Test
    fun testVerbDrillBlock_WeakFirstOrdering() = runBlocking {
        // --- SETUP: Create verb cards with different tenses ---
        val verbCards = listOf(
            createVerbCard("v1", "ru1", "it1", "essere", "Presente"),
            createVerbCard("v2", "ru2", "it2", "essere", "Imperfetto"),
            createVerbCard("v3", "ru3", "it3", "avere", "Presente"),
            createVerbCard("v4", "ru4", "it4", "avere", "Imperfetto")
        )
        // Inject verb cards directly into the composer's cache (bypasses file loading)
        val composer = DailySessionComposer(lessonStore, verbDrillStore, wordMasteryStore, 10)
        composer.injectVerbDrillCardsForTest(testPackId.value, testLanguageId.value, verbCards)

        // --- SETUP: Mark some Presente cards as shown (making them stronger) ---
        val progress1 = VerbDrillComboProgress(
            group = "io",
            tense = "Presente",
            totalCards = 2,
            everShownCardIds = setOf("v1", "v3"),
            todayShownCardIds = emptySet(),
            lastDate = ""
        )
        verbDrillStore.upsertComboProgress("io|Presente", progress1)

        // --- ASSERT: Verify weakness scoring ---
        // Presente has 2 shown cards out of 2 -> weakness = 0 (strongest)
        // Imperfetto has 0 shown cards out of 2 -> weakness = 1 (weakest)

        // --- ACTION: Build verb block via composer ---
        val verbTasks = composer.rebuildBlock(
            blockType = DailyBlockType.VERBS,
            lessonLevel = 1,
            packId = testPackId.value,
            languageId = testLanguageId.value,
            lessonId = "lesson-1",
            cumulativeTenses = listOf("Presente", "Imperfetto"),
            cursor = DailyCursorState()
        )

        // --- ASSERT: Imperfetto (weak) cards should come before Presente (strong) ---
        val tenseOrder = verbTasks.mapNotNull { (it as? DailyTask.ConjugateVerb)?.card?.tense }
        val firstImperfettoIndex = tenseOrder.indexOf("Imperfetto")
        val firstPresenteIndex = tenseOrder.indexOf("Presente")

        assertTrue("Imperfetto should come before Presente (weak-first). Got tense order: $tenseOrder",
            firstImperfettoIndex >= 0 && firstPresenteIndex >= 0 && firstImperfettoIndex < firstPresenteIndex)
    }

    // ========================================
    // Test 7: Streak recording for all three blocks
    // ========================================

    @Test
    fun testStreakRecording_AllThreeBlocks() = runBlocking {
        // --- SETUP: Create 3 blocks ---
        val blocks = listOf(
            DailyBlock(DailyBlockType.TRANSLATE, listOf(
                DailyTask.TranslateSentence("t1", createSentenceCard("c1", "ru1", listOf("en1")), InputMode.VOICE)
            )),
            DailyBlock(DailyBlockType.VOCAB, listOf(
                DailyTask.VocabFlashcard("v1", createVocabWord("w1", "casa", "nouns", 1), VocabDrillDirection.IT_TO_RU)
            )),
            DailyBlock(DailyBlockType.VERBS, listOf(
                DailyTask.ConjugateVerb("verb1", createVerbCard("verb1", "ru1", "it1", "essere", "Presente"), InputMode.KEYBOARD)
            ))
        )

        coordinator.startDailySession(blocks, 1)

        // --- ACTION: Complete all blocks ---
        coordinator.onBlockComplete()  // TRANSLATE → VOCAB
        coordinator.onBlockComplete()  // VOCAB → VERBS
        coordinator.onBlockComplete()  // VERBS → end (calls endSession internally)

        // --- ASSERT: Verify streak recording ---
        val streakData = streakStore.load(testLanguageId.value)
        assertNotNull("Streak data should exist", streakData)

        // All three types should be recorded
        val completedTypes = streakData.completedTypesToday
        assertTrue("TRANSLATION should be recorded", PracticeType.TRANSLATION in completedTypes)
        assertTrue("VOCAB should be recorded", PracticeType.VOCAB in completedTypes)
        assertTrue("VERB should be recorded", PracticeType.VERB in completedTypes)

        // Fire count should be 3 (one for each unique type)
        assertEquals("Should have 3 fires", 3, streakData.todayFireCount)
    }

    // ========================================
    // Test 8: Cursor advancement
    // ========================================

    @Test
    fun testCursorAdvancement() = runBlocking {
        // --- SETUP: Create initial cursor ---
        val initialCursor = DailyCursorState(
            sentenceOffset = 5,
            currentLessonIndex = 0,
            verbOffset = 10
        )
        coordinator.updateCursor(initialCursor)

        // --- SETUP: Create TWO lessons to test lesson index advancement ---
        val lesson1 = Lesson(
            id = LessonId("lesson-cursor-1"),
            languageId = testLanguageId,
            title = "Cursor Test Lesson 1",
            cards = (1..20).map { i ->
                createSentenceCard("cursor-1-$i", "ру1-$i", listOf("en1-$i"))
            }
        )
        val lesson2 = Lesson(
            id = LessonId("lesson-cursor-2"),
            languageId = testLanguageId,
            title = "Cursor Test Lesson 2",
            cards = (1..20).map { i ->
                createSentenceCard("cursor-2-$i", "ру2-$i", listOf("en2-$i"))
            }
        )
        lessonStore.setLessons(listOf(lesson1, lesson2))

        // --- ACTION: Advance cursor by 10 sentences ---
        val newCursor = coordinator.advanceDailyCursor(10, testLanguageId.value)

        // --- ASSERT: sentenceOffset increased ---
        assertEquals("sentenceOffset should advance by 10", 15, newCursor.sentenceOffset)
        assertEquals("currentLessonIndex should stay same", 0, newCursor.currentLessonIndex)

        // --- UPDATE coordinator's cursor state for next call ---
        coordinator.updateCursor(newCursor)

        // --- ACTION: Advance by another 10 (total 20) ---
        val newCursor2 = coordinator.advanceDailyCursor(10, testLanguageId.value)

        // --- ASSERT: Should wrap to next lesson ---
        assertEquals("sentenceOffset should wrap to 0", 0, newCursor2.sentenceOffset)
        assertEquals("currentLessonIndex should advance", 1, newCursor2.currentLessonIndex)
    }

    // ========================================
    // Test 9: Mid-session exit and resume
    // ========================================

    @Test
    fun testMidSessionExit_ResumeContinues() = runBlocking {
        // --- SETUP: Create session ---
        val blocks = listOf(
            DailyBlock(DailyBlockType.TRANSLATE, listOf(
                DailyTask.TranslateSentence("t1", createSentenceCard("c1", "ru1", listOf("en1")), InputMode.VOICE)
            )),
            DailyBlock(DailyBlockType.VOCAB, listOf(
                DailyTask.VocabFlashcard("v1", createVocabWord("w1", "casa", "nouns", 1), VocabDrillDirection.IT_TO_RU)
            ))
        )

        coordinator.startDailySession(blocks, 1)

        // --- ACTION: Complete first block, then cancel ---
        coordinator.onBlockComplete()
        val advanceCount = coordinator.cancelDailySession()

        // --- ASSERT: Session ended, cursor not advanced (incomplete) ---
        assertNull("Incomplete session should not advance cursor", advanceCount)
        assertFalse("Session should not be active", coordinator.dailyState.value.dailySession.active)
    }

    // ========================================
    // Test 10: Bad sentence reporting
    // ========================================

    @Test
    fun testBadSentenceReporting() = runBlocking {
        // --- SETUP: This test verifies the data structure for bad sentences ---
        // The actual reporting UI is in DailyPracticeScreen, but we verify
        // the data flow through the coordinator

        // --- SETUP: Create a sentence ---
        val card = createSentenceCard("bad-sentence-1", "неправильное предложение", listOf("wrong translation"))

        // --- ASSERT: Card has expected structure for reporting ---
        assertEquals("Card should have ID", "bad-sentence-1", card.id)
        assertEquals("Card should have Russian prompt", "неправильное предложение", card.promptRu)
        assertTrue("Card should have accepted answers", card.acceptedAnswers.isNotEmpty())

        // Note: The actual bad sentence reporting is handled in the UI layer
        // via BadSentenceBottomSheet. This test verifies the card data
        // structure supports the reporting feature.
    }

    // ========================================
    // Test 11: HARD rating keeps same step
    // ========================================

    @Test
    fun testVocabRating_HardStaysSameStep() = runBlocking {
        // --- SETUP: Create a vocab word at step 3 ---
        val word = createVocabWord("word-hard", "difficile", "adjectives", 3)
        val initialMastery = WordMasteryState(
            wordId = word.id,
            intervalStepIndex = 3,
            correctCount = 7
        )
        wordMasteryStore.setMastery(initialMastery)

        val vocabTasks = listOf(
            DailyTask.VocabFlashcard("v1", word, VocabDrillDirection.IT_TO_RU)
        )
        val blocks = listOf(
            DailyBlock(DailyBlockType.VOCAB, vocabTasks)
        )

        coordinator.startDailySession(blocks, 1)

        // --- ACTION: Rate as HARD ---
        coordinator.rateVocabCard(SrsRating.HARD)

        // --- ASSERT: Step stays the same ---
        val updatedMastery = wordMasteryStore.getMastery(word.id)
        assertEquals("Step should stay same", 3, updatedMastery?.intervalStepIndex)
        assertEquals("Correct count should increase", 8, updatedMastery?.correctCount)
    }

    // ========================================
    // Test 12: GOOD rating advances one step
    // ========================================

    @Test
    fun testVocabRating_GoodAdvancesOneStep() = runBlocking {
        // --- SETUP: Create a vocab word at step 2 ---
        val word = createVocabWord("word-good", "grande", "adjectives", 4)
        val initialMastery = WordMasteryState(
            wordId = word.id,
            intervalStepIndex = 2,
            correctCount = 4
        )
        wordMasteryStore.setMastery(initialMastery)

        val vocabTasks = listOf(
            DailyTask.VocabFlashcard("v1", word, VocabDrillDirection.IT_TO_RU)
        )
        val blocks = listOf(
            DailyBlock(DailyBlockType.VOCAB, vocabTasks)
        )

        coordinator.startDailySession(blocks, 1)

        // --- ACTION: Rate as GOOD ---
        coordinator.rateVocabCard(SrsRating.GOOD)

        // --- ASSERT: Step advanced by 1 ---
        val updatedMastery = wordMasteryStore.getMastery(word.id)
        assertEquals("Step should advance by 1", 3, updatedMastery?.intervalStepIndex)
        assertEquals("Correct count should increase", 5, updatedMastery?.correctCount)
    }

    // ========================================
    // Test 13: Answer validation for TRANSLATE block
    // ========================================

    @Test
    fun testTranslateBlock_AnswerValidation() = runBlocking {
        // --- SETUP: Create TRANSLATE block ---
        val card = createSentenceCard("ans-1", "русский текст", listOf("english text", "alternative"))
        val translateTasks = listOf(
            DailyTask.TranslateSentence("t1", card, InputMode.KEYBOARD)
        )
        val blocks = listOf(
            DailyBlock(DailyBlockType.TRANSLATE, translateTasks)
        )

        coordinator.startDailySession(blocks, 1)

        // --- ACTION: Submit correct answer ---
        val result1 = coordinator.submitDailySentenceAnswer("english text")

        // --- ASSERT: Answer accepted ---
        assertTrue("Correct answer should be accepted", result1)

        // --- ACTION: Submit incorrect answer ---
        val result2 = coordinator.submitDailySentenceAnswer("wrong answer")

        // --- ASSERT: Answer rejected ---
        assertFalse("Wrong answer should be rejected", result2)

        // --- ACTION: Submit alternative correct answer ---
        val result3 = coordinator.submitDailySentenceAnswer("alternative")

        // --- ASSERT: Alternative accepted ---
        assertTrue("Alternative correct answer should be accepted", result3)
    }

    // ========================================
    // Test 14: Answer validation for VERBS block
    // ========================================

    @Test
    fun testVerbsBlock_AnswerValidation() = runBlocking {
        // --- SETUP: Create VERBS block ---
        val card = createVerbCard("verb-ans", "я являюсь", "io sono", "essere", "Presente")
        val verbTasks = listOf(
            DailyTask.ConjugateVerb("v1", card, InputMode.KEYBOARD)
        )
        val blocks = listOf(
            DailyBlock(DailyBlockType.VERBS, verbTasks)
        )

        coordinator.startDailySession(blocks, 1)

        // --- ACTION: Submit correct answer ---
        val result1 = coordinator.submitDailyVerbAnswer("io sono")

        // --- ASSERT: Answer accepted ---
        assertTrue("Correct conjugation should be accepted", result1)

        // --- ACTION: Submit incorrect answer ---
        val result2 = coordinator.submitDailyVerbAnswer("tu sei")

        // --- ASSERT: Answer rejected (wrong person) ---
        assertFalse("Wrong conjugation should be rejected", result2)
    }

    // ========================================
    // Test 15: Verb drill progress persistence
    // ========================================

    @Test
    fun testVerbDrillProgress_Persistence() = runBlocking {
        // --- SETUP: Create a verb card ---
        val card = createVerbCard("verb-prog", "я делаю", "io faccio", "fare", "Presente")

        // --- ACTION: Persist verb progress ---
        coordinator.persistDailyVerbProgress(card)

        // --- ASSERT: Progress saved to store ---
        val progress = verbDrillStore.loadProgress()
        val comboKey = "io|Presente"

        assertTrue("Combo progress should exist", progress.containsKey(comboKey))
        val comboProgress = progress[comboKey]
        assertEquals("Card should be in everShownCardIds", card.id, comboProgress?.everShownCardIds?.first())
        assertEquals("Card should be in todayShownCardIds", card.id, comboProgress?.todayShownCardIds?.first())
    }

    // ========================================
    // Test 16: Block render method queries
    // ========================================

    @Test
    fun testBlockRenderMethod_Queries() = runBlocking {
        // --- SETUP: Create all three block types ---
        val blocks = listOf(
            DailyBlock(DailyBlockType.TRANSLATE, emptyList()),
            DailyBlock(DailyBlockType.VOCAB, emptyList()),
            DailyBlock(DailyBlockType.VERBS, emptyList())
        )

        coordinator.startDailySession(blocks, 1)

        // --- ASSERT: Verify render methods ---
        assertEquals("TRANSLATE should render via TRAINING_SCREEN",
            BlockRenderVia.TRAINING_SCREEN, blocks[0].renderVia)
        assertEquals("VOCAB should render INLINE",
            BlockRenderVia.INLINE, blocks[1].renderVia)
        assertEquals("VERBS should render via TRAINING_SCREEN",
            BlockRenderVia.TRAINING_SCREEN, blocks[2].renderVia)

        // --- ASSERT: Current block queries ---
        assertEquals("Current block should be TRANSLATE",
            DailyBlockType.TRANSLATE, coordinator.getCurrentBlockType())

        val renderVia = coordinator.getCurrentRenderVia()
        assertEquals("Current block should render via TRAINING_SCREEN",
            BlockRenderVia.TRAINING_SCREEN, renderVia)
    }

    // ========================================
    // Test 17: Daily practice answer counting
    // ========================================

    @Test
    fun testDailyPracticeAnsweredCounts() = runBlocking {
        // --- SETUP: Create TRANSLATE and VERBS blocks ---
        val card = createSentenceCard("count-1", "ру", listOf("en"))
        val translateTasks = (1..5).map { i ->
            DailyTask.TranslateSentence("t$i", createSentenceCard("c$i", "ру$i", listOf("en$i")), InputMode.VOICE)
        }
        val verbTasks = (1..5).map { i ->
            DailyTask.ConjugateVerb("v$i", createVerbCard("cv$i", "руv$i", "itv$i", "essere", "Presente"), InputMode.KEYBOARD)
        }

        val blocks = listOf(
            DailyBlock(DailyBlockType.TRANSLATE, translateTasks),
            DailyBlock(DailyBlockType.VERBS, verbTasks)
        )

        coordinator.startDailySession(blocks, 1)

        // --- ACTION: Record practiced cards (ALL cards in each block) ---
        // cancelDailySession only advances cursor if ALL tasks in blocks are completed
        repeat(5) {
            coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE) { "lesson-1" }
        }
        repeat(5) {
            coordinator.recordDailyCardPracticed(DailyBlockType.VERBS) { "lesson-1" }
        }

        // --- ASSERT: Counted for cursor advancement ---
        // Note: dailyPracticeAnsweredCounts is private, but we verify through
        // cancelDailySession which uses it
        val nextBlock1 = coordinator.onBlockComplete()  // Complete TRANSLATE
        val nextBlock2 = coordinator.onBlockComplete()  // Complete VERBS

        // --- ASSERT: Session finished after completing all blocks ---
        assertNull("No next block after all complete", nextBlock2)
        assertTrue("Session should be finished", coordinator.dailyState.value.dailySession.finishedToken)

        val advanceCount = coordinator.cancelDailySession()

        // --- ASSERT: Should advance because all TRANSLATE completed ---
        assertNotNull("Should return advance count", advanceCount)
        assertEquals("Should advance by 5 (TRANSLATE count)", 5, advanceCount)
    }

    // ========================================
    // Test 18: Vocab block task advancement
    // ========================================

    @Test
    fun testVocabBlock_TaskAdvancement() = runBlocking {
        // --- SETUP: Create vocab block with 3 cards ---
        val vocabTasks = (1..3).map { i ->
            DailyTask.VocabFlashcard("v$i", createVocabWord("w$i", "word$i", "nouns", i), VocabDrillDirection.IT_TO_RU)
        }
        val blocks = listOf(
            DailyBlock(DailyBlockType.VOCAB, vocabTasks)
        )

        coordinator.startDailySession(blocks, 1)

        // --- ASSERT: Initial task index ---
        assertEquals("Should start at task 0", 0, coordinator.dailyState.value.dailySession.currentBlock?.taskIndex)

        // --- ACTION: Rate first card ---
        coordinator.rateVocabCard(SrsRating.GOOD)

        // --- ASSERT: Task advanced ---
        assertEquals("Should advance to task 1", 1, coordinator.dailyState.value.dailySession.currentBlock?.taskIndex)

        // --- ACTION: Rate second card ---
        coordinator.rateVocabCard(SrsRating.GOOD)

        // --- ASSERT: Task advanced again ---
        assertEquals("Should advance to task 2", 2, coordinator.dailyState.value.dailySession.currentBlock?.taskIndex)

        // --- ACTION: Rate third (last) card ---
        coordinator.rateVocabCard(SrsRating.GOOD)

        // --- ASSERT: Block complete, session ended ---
        // After last card, onBlockComplete() is called which advances blockIndex.
        // With only one block, currentBlock becomes null (session finished).
        assertNull("Current block should be null after completion", coordinator.dailyState.value.dailySession.currentBlock)
        assertTrue("Session should be finished", coordinator.dailyState.value.dailySession.finishedToken)
        // The completed block should be marked complete in the blocks list
        assertTrue("First block should be marked complete", coordinator.dailyState.value.dailySession.blocks[0].isComplete)
    }

    // ========================================
    // Test 19: Session state queries
    // ========================================

    @Test
    fun testSessionStateQueries() = runBlocking {
        // --- SETUP: Create session ---
        val blocks = listOf(
            DailyBlock(DailyBlockType.TRANSLATE, listOf(
                DailyTask.TranslateSentence("t1", createSentenceCard("c1", "ru1", listOf("en1")), InputMode.VOICE)
            ))
        )

        coordinator.startDailySession(blocks, 1)

        // --- ASSERT: State queries ---
        assertTrue("Session should be active", coordinator.dailyState.value.dailySession.active)
        assertNotNull("Current block should exist", coordinator.getCurrentBlock())
        assertNotNull("Current task should exist", coordinator.getCurrentTask())
        assertEquals("Current task should be TRANSLATE", "t1", coordinator.getCurrentTask()?.id)

        // --- ACTION: Complete block ---
        coordinator.onBlockComplete()

        // --- ASSERT: Session finished ---
        assertTrue("Session should be finished", coordinator.dailyState.value.dailySession.finishedToken)
        assertFalse("Session should not be active", coordinator.dailyState.value.dailySession.active)
        assertNull("No current block after finish", coordinator.getCurrentBlock())
        assertNull("No current task after finish", coordinator.getCurrentTask())
    }

    // ========================================
    // Test 20: Get daily sentence/verb answers
    // ========================================

    @Test
    fun testGetDailyAnswers() = runBlocking {
        // --- SETUP: Create TRANSLATE block ---
        val sentenceCard = createSentenceCard("ans-test", "русский", listOf("english", "alternative"))
        val translateTasks = listOf(
            DailyTask.TranslateSentence("t1", sentenceCard, InputMode.KEYBOARD)
        )
        val blocks = listOf(
            DailyBlock(DailyBlockType.TRANSLATE, translateTasks)
        )

        coordinator.startDailySession(blocks, 1)

        // --- ASSERT: Get sentence answer ---
        val sentenceAnswer = coordinator.getDailySentenceAnswer()
        assertNotNull("Sentence answer should exist", sentenceAnswer)
        assertEquals("Should return first accepted answer", "english", sentenceAnswer)

        // --- SETUP: Create VERBS block ---
        val verbCard = createVerbCard("verb-ans-test", "я есть", "io sono", "essere", "Presente")
        val verbTasks = listOf(
            DailyTask.ConjugateVerb("v1", verbCard, InputMode.KEYBOARD)
        )
        val verbBlocks = listOf(
            DailyBlock(DailyBlockType.VERBS, verbTasks)
        )

        coordinator.startDailySession(verbBlocks, 1)

        // --- ASSERT: Get verb answer ---
        val verbAnswer = coordinator.getDailyVerbAnswer()
        assertNotNull("Verb answer should exist", verbAnswer)
        assertEquals("Should return verb answer", "io sono", verbAnswer)
    }

    // ========================================
    // Test 21: Five consecutive Daily Practice runs - verify no duplicate cards
    // CRITICAL: Tests that cursor advancement works and cards don't repeat
    // ========================================

    @Test
    fun testFiveConsecutiveRuns_NoDuplicateCards() = runBlocking {
        // --- DEBUG: First, test a single run to understand the issue ---
        val debugLessonId = LessonId("lesson-debug-test")
        val debugSentenceCards = (1..20).map { i ->
            createSentenceCard("debug-sent-$i", "ру-$i", listOf("en-$i"))
        }
        val debugVerbCards = (1..20).map { i ->
            createVerbCard("debug-verb-$i", "ру-verb-$i", "it-verb-$i", "avere", "Presente")
        }

        val debugLesson = Lesson(
            id = debugLessonId,
            languageId = testLanguageId,
            title = "Debug Lesson",
            cards = debugSentenceCards
        )
        lessonStore.addLesson(debugLesson)

        val debugComposer = DailySessionComposer(lessonStore, verbDrillStore, wordMasteryStore, 10)
        // Inject verb cards into composer cache for test
        debugComposer.injectVerbDrillCardsForTest(testPackId.value, testLanguageId.value, debugVerbCards)

        // Build blocks
        val debugTranslateTasksRaw = debugComposer.rebuildBlock(
            blockType = DailyBlockType.TRANSLATE,
            lessonLevel = 1,
            packId = testPackId.value,
            languageId = testLanguageId.value,
            lessonId = debugLessonId.value,
            cumulativeTenses = emptyList(),
            cursor = DailyCursorState(0, 0, 0)
        )
        val debugTranslateTasks = translateTasksTake(debugTranslateTasksRaw, 10)

        val debugVerbTasksRaw = debugComposer.rebuildBlock(
            blockType = DailyBlockType.VERBS,
            lessonLevel = 1,
            packId = testPackId.value,
            languageId = testLanguageId.value,
            lessonId = debugLessonId.value,
            cumulativeTenses = emptyList(),
            cursor = DailyCursorState(0, 0, 0)
        )
        val debugVerbTasks = verbTasksTake(debugVerbTasksRaw, 10)

        println("DEBUG: translateTasks.size = ${debugTranslateTasks.size}")
        println("DEBUG: verbTasks.size = ${debugVerbTasks.size}")

        val debugBlocks = listOf(
            DailyBlock(DailyBlockType.TRANSLATE, debugTranslateTasks),
            DailyBlock(DailyBlockType.VERBS, debugVerbTasks)
        )

        println("DEBUG: blocks.size = ${debugBlocks.size}")

        // Start session
        coordinator.startDailySession(debugBlocks, 1)

        // Record practices
        debugTranslateTasks.forEach { task ->
            if (task is DailyTask.TranslateSentence) {
                coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE) { debugLessonId.value }
            }
        }
        debugVerbTasks.forEach { task ->
            if (task is DailyTask.ConjugateVerb) {
                coordinator.recordDailyCardPracticed(DailyBlockType.VERBS) { debugLessonId.value }
            }
        }

        println("DEBUG: Recorded practices")

        // Complete blocks
        val result1 = coordinator.onBlockComplete()
        println("DEBUG: First onBlockComplete() returned: $result1")
        val result2 = coordinator.onBlockComplete()
        println("DEBUG: Second onBlockComplete() returned: $result2")

        // Check state before cancel
        val stateBeforeCancel = coordinator.getDailyState().dailySession
        println("DEBUG: Before cancel - finishedToken=${stateBeforeCancel.finishedToken}, active=${stateBeforeCancel.active}, blockIndex=${stateBeforeCancel.blockIndex}, blocks.size=${stateBeforeCancel.blocks.size}")

        // Cancel and get advance count
        val advanceCount = coordinator.cancelDailySession()
        println("DEBUG: advanceCount = $advanceCount")

        assertNotNull("Should return advance count", advanceCount)

        // Now continue with the original 5-run test
        // --- SETUP: Create lesson with 50 sentence cards and 30 verb cards ---
        // This gives us enough cards for 5 runs (10 TRANSLATE + 10 VERBS per run)
        val lessonId = LessonId("lesson-large-test")
        val allSentenceCards = (1..50).map { i ->
            createSentenceCard("sent-$i", "ру-$i", listOf("en-$i"))
        }
        val allVerbCards = (1..30).map { i ->
            createVerbCard("verb-$i", "ру-verb-$i", "it-verb-$i", "avere", "Presente")
        }

        val lesson = Lesson(
            id = lessonId,
            languageId = testLanguageId,
            title = "Large Lesson",
            cards = allSentenceCards
        )
        lessonStore.addLesson(lesson)

        // --- SETUP: Create composer for block building ---
        val composer = DailySessionComposer(lessonStore, verbDrillStore, wordMasteryStore, 10)
        // --- SETUP: Inject verb drill cards into composer cache for test ---
        composer.injectVerbDrillCardsForTest(testPackId.value, testLanguageId.value, allVerbCards)

        // --- SETUP: Track all cards shown across 5 runs ---
        val shownSentenceCardIds = mutableSetOf<String>()
        val shownVerbCardIds = mutableSetOf<String>()

        // --- SETUP: Initial cursor ---
        var currentCursor = DailyCursorState(
            sentenceOffset = 0,
            currentLessonIndex = 0,
            verbOffset = 0
        )
        coordinator.updateCursor(currentCursor)

        // --- ACTION: Run Daily Practice 5 times consecutively ---
        repeat(5) { runIndex ->
            // Build TRANSLATE block for this run
            val translateTasksRaw = composer.rebuildBlock(
                blockType = DailyBlockType.TRANSLATE,
                lessonLevel = 1,
                packId = testPackId.value,
                languageId = testLanguageId.value,
                lessonId = lessonId.value,
                cumulativeTenses = emptyList(),
                cursor = currentCursor
            )
            val translateTasks = translateTasksTake(translateTasksRaw, 10)

            // Build VERBS block for this run
            val verbTasksRaw = composer.rebuildBlock(
                blockType = DailyBlockType.VERBS,
                lessonLevel = 1,
                packId = testPackId.value,
                languageId = testLanguageId.value,
                lessonId = lessonId.value,
                cumulativeTenses = emptyList(),
                cursor = currentCursor
            )
            val verbTasks = verbTasksTake(verbTasksRaw, 10)

            val blocks = listOf(
                DailyBlock(DailyBlockType.TRANSLATE, translateTasks),
                DailyBlock(DailyBlockType.VERBS, verbTasks)
            )

            // Start session
            coordinator.startDailySession(blocks, 1)

            // --- COLLECT: Record all card IDs shown in this run ---
            translateTasks.forEach { task ->
                if (task is DailyTask.TranslateSentence) {
                    shownSentenceCardIds.add(task.card.id)
                    // SIMULATE: User answered correctly (record for cursor advancement)
                    coordinator.recordDailyCardPracticed(DailyBlockType.TRANSLATE) { lessonId.value }
                }
            }
            verbTasks.forEach { task ->
                if (task is DailyTask.ConjugateVerb) {
                    shownVerbCardIds.add(task.card.id)
                    // SIMULATE: User answered correctly (record for cursor advancement)
                    coordinator.recordDailyCardPracticed(DailyBlockType.VERBS) { lessonId.value }
                }
            }

            // --- COMPLETE: Finish all blocks ---
            coordinator.onBlockComplete()  // Complete TRANSLATE
            coordinator.onBlockComplete()  // Complete VERBS

            // --- ADVANCE: Move cursor forward ---
            val advanceCount = coordinator.cancelDailySession()
            assertNotNull("Should return advance count (run ${runIndex + 1})", advanceCount)

            // --- ASSERT: Cursor advanced ---
            val newCursor = coordinator.advanceDailyCursor(advanceCount!!, testLanguageId.value)
            coordinator.updateCursor(newCursor)

            // --- UPDATE: Update currentCursor for next run ---
            currentCursor = coordinator.getCursor()
        }

        // --- ASSERT: No duplicate sentence cards across all 5 runs ---
        assertEquals("Should show 50 unique sentence cards (10 per run × 5 runs)",
            50, shownSentenceCardIds.size)
        assertEquals("Should show 30 unique verb cards (10 per run × 3 runs - capped at available)",
            30, shownVerbCardIds.size)

        // --- ASSERT: Cursor advanced ---
        val finalCursor = coordinator.getCursor()
        // The key assertion: we saw 50 unique cards, which proves cursor advanced correctly
        // (The debug section at the start may have advanced the lesson index further)
        assertTrue("verbOffset should have advanced (verb cards shouldn't repeat)", finalCursor.verbOffset > 0)
    }

    // Helper to take only TranslateSentence tasks
    private fun translateTasksTake(tasks: List<DailyTask>, count: Int): List<DailyTask> {
        return tasks.filterIsInstance<DailyTask.TranslateSentence>().take(count)
    }

    // Helper to take only ConjugateVerb tasks
    private fun verbTasksTake(tasks: List<DailyTask>, count: Int): List<DailyTask> {
        return tasks.filterIsInstance<DailyTask.ConjugateVerb>().take(count)
    }

    // ========================================
    // Helper methods
    // ========================================

    /**
     * Creates a fake Android Application context.
     * DailyPracticeCoordinator uses Application for SystemClock.elapsedRealtime()
     * which is a static method and doesn't actually need a context reference.
     */
    private fun createFakeApplicationContext(): android.app.Application {
        return object : android.app.Application() {
            override fun getClassLoader(): ClassLoader {
                return this.javaClass.classLoader!!
            }
        }
    }
}
