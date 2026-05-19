package com.alexpo.grammermate.feature.daily

import com.alexpo.grammermate.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Unit tests for [DailySessionComposer] -- block builders for daily practice sessions.
 *
 * Tests cover all three block types (TRANSLATE, VOCAB, VERBS), repeat session logic,
 * and edge cases (empty data, offset overflow, cycling, numbers exclusion).
 *
 * Uses Robolectric because [LessonStore] interface references Android types
 * ([android.net.Uri], [android.content.ContentResolver]) in its mock implementation.
 *
 * Mock stores return in-memory data. CSV files for vocab/verb drills are created as
 * temp files and cleaned up in [tearDown].
 */
@RunWith(RobolectricTestRunner::class)
class DailySessionComposerTest {

    // -- System under test ------------------------------------------------

    private lateinit var composer: DailySessionComposer
    private lateinit var mockLessonStore: MockComposerLessonStore
    private lateinit var mockVerbDrillStore: MockComposerVerbDrillStore
    private lateinit var mockWordMasteryStore: MockComposerWordMasteryStore

    // -- Temp files for CSV data ------------------------------------------

    private val tempFiles = mutableListOf<File>()

    // -- Constants --------------------------------------------------------

    private companion object {
        const val PACK_ID = "pack-test"
        const val LANGUAGE_ID = "it"
        const val LESSON_ID = "lesson-1"
        const val SESSION_SIZE = 3
    }

    // -- Test fixtures ----------------------------------------------------

    private val card1 = SentenceCard(id = "c1", promptRu = "ru1", acceptedAnswers = listOf("one"))
    private val card2 = SentenceCard(id = "c2", promptRu = "ru2", acceptedAnswers = listOf("two"))
    private val card3 = SentenceCard(id = "c3", promptRu = "ru3", acceptedAnswers = listOf("three"))
    private val card4 = SentenceCard(id = "c4", promptRu = "ru4", acceptedAnswers = listOf("four"))
    private val card5 = SentenceCard(id = "c5", promptRu = "ru5", acceptedAnswers = listOf("five"))

    private val verbCard1 = VerbDrillCard(id = "vc1", promptRu = "vr1", answer = "parlo", verb = "parlare", tense = "Presente", group = "regular", rank = 1)
    private val verbCard2 = VerbDrillCard(id = "vc2", promptRu = "vr2", answer = "parli", verb = "parlare", tense = "Presente", group = "regular", rank = 2)
    private val verbCard3 = VerbDrillCard(id = "vc3", promptRu = "vr3", answer = "mangio", verb = "mangiare", tense = "Presente", group = "regular", rank = 1)
    private val verbCard4 = VerbDrillCard(id = "vc4", promptRu = "vr4", answer = "parlavo", verb = "parlare", tense = "Imperfetto", group = "regular", rank = 1)
    private val verbCard5 = VerbDrillCard(id = "vc5", promptRu = "vr5", answer = "mangiavo", verb = "mangiare", tense = "Imperfetto", group = "regular", rank = 2)

    @Before
    fun setUp() {
        mockLessonStore = MockComposerLessonStore()
        mockVerbDrillStore = MockComposerVerbDrillStore()
        mockWordMasteryStore = MockComposerWordMasteryStore()
        composer = DailySessionComposer(
            lessonStore = mockLessonStore,
            verbDrillStore = mockVerbDrillStore,
            wordMasteryStore = mockWordMasteryStore,
            sessionSize = SESSION_SIZE
        )
    }

    @After
    fun tearDown() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    // ====================================================================
    // 1. TRANSLATE block (buildSentenceBlock via buildSession/buildBlocks)
    // ====================================================================

    @Test
    fun buildBlocks_translatesFromCursorLessonIndexAndOffset() = runBlocking {
        // Lesson at index 0 with 5 cards, starting at offset 0
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "Lesson 1", listOf(card1, card2, card3, card4, card5))
        )
        val cursor = DailyCursorState(currentLessonIndex = 0, sentenceOffset = 0)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cursor = cursor
        )

        val translateBlock = blocks.first { it.type == DailyBlockType.TRANSLATE }
        assertEquals("expected 3 cards for sessionSize=3", SESSION_SIZE, translateBlock.tasks.size)
        // Cards in sequential order
        assertEquals("c1", (translateBlock.tasks[0] as DailyTask.TranslateSentence).card.id)
        assertEquals("c2", (translateBlock.tasks[1] as DailyTask.TranslateSentence).card.id)
        assertEquals("c3", (translateBlock.tasks[2] as DailyTask.TranslateSentence).card.id)
    }

    @Test
    fun buildBlocks_translateStartsAtSentenceOffset() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "Lesson 1", listOf(card1, card2, card3, card4, card5))
        )
        val cursor = DailyCursorState(currentLessonIndex = 0, sentenceOffset = 2)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cursor = cursor
        )

        val translateBlock = blocks.first { it.type == DailyBlockType.TRANSLATE }
        assertEquals("expected 3 cards from offset 2", SESSION_SIZE, translateBlock.tasks.size)
        assertEquals("c3", (translateBlock.tasks[0] as DailyTask.TranslateSentence).card.id)
        assertEquals("c4", (translateBlock.tasks[1] as DailyTask.TranslateSentence).card.id)
        assertEquals("c5", (translateBlock.tasks[2] as DailyTask.TranslateSentence).card.id)
    }

    @Test
    fun buildBlocks_translateDoesNotCrossLessonBoundaries() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId("lesson-1"), LanguageId(LANGUAGE_ID), "L1", listOf(card1, card2)),
            Lesson(LessonId("lesson-2"), LanguageId(LANGUAGE_ID), "L2", listOf(card3, card4, card5))
        )
        // Cursor at lesson index 0, offset 0 -- only 2 cards in this lesson
        val cursor = DailyCursorState(currentLessonIndex = 0, sentenceOffset = 0)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = "lesson-1", cursor = cursor
        )

        val translateBlock = blocks.first { it.type == DailyBlockType.TRANSLATE }
        // Only 2 cards available in lesson at index 0, sessionSize=3 but only 2 returned
        assertEquals("should not cross to lesson 2", 2, translateBlock.tasks.size)
    }

    @Test
    fun buildBlocks_translateOffsetExceedsLessonSize_returnsEmpty() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1, card2))
        )
        // Offset 5 > lesson size 2
        val cursor = DailyCursorState(currentLessonIndex = 0, sentenceOffset = 5)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cursor = cursor
        )

        // No translate block when offset is exhausted
        val hasTranslate = blocks.any { it.type == DailyBlockType.TRANSLATE }
        assertFalse("no translate block when offset >= lesson size", hasTranslate)
    }

    @Test
    fun buildBlocks_translateCardsInSequentialOrder() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1",
                listOf(card1, card2, card3, card4, card5))
        )
        val cursor = DailyCursorState(currentLessonIndex = 0, sentenceOffset = 0)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cursor = cursor
        )

        val translateBlock = blocks.first { it.type == DailyBlockType.TRANSLATE }
        val ids = translateBlock.tasks.map { (it as DailyTask.TranslateSentence).card.id }
        assertEquals(listOf("c1", "c2", "c3"), ids)
    }

    @Test
    fun buildBlocks_sessionSizeLargerThanRemaining_returnsFewerCards() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1, card2))
        )
        // sessionSize=3 but only 2 cards remaining from offset 0
        val cursor = DailyCursorState(currentLessonIndex = 0, sentenceOffset = 0)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cursor = cursor
        )

        val translateBlock = blocks.first { it.type == DailyBlockType.TRANSLATE }
        assertEquals("should return 2 cards, not padded to 3", 2, translateBlock.tasks.size)
    }

    @Test
    fun buildBlocks_translateInputModesCycleVoiceKeyboardWordbank() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1",
                listOf(card1, card2, card3, card4, card5))
        )
        val cursor = DailyCursorState(currentLessonIndex = 0, sentenceOffset = 0)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cursor = cursor
        )

        val translateBlock = blocks.first { it.type == DailyBlockType.TRANSLATE }
        val modes = translateBlock.tasks.map { (it as DailyTask.TranslateSentence).inputMode }
        assertEquals(InputMode.VOICE, modes[0])
        assertEquals(InputMode.KEYBOARD, modes[1])
        assertEquals(InputMode.WORD_BANK, modes[2])
    }

    @Test
    fun buildBlocks_emptyLesson_returnsNoTranslateBlock() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", emptyList())
        )
        val cursor = DailyCursorState(currentLessonIndex = 0, sentenceOffset = 0)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cursor = cursor
        )

        val hasTranslate = blocks.any { it.type == DailyBlockType.TRANSLATE }
        assertFalse("no translate block for empty lesson", hasTranslate)
    }

    // ====================================================================
    // 2. VOCAB block (buildVocabBlock)
    // ====================================================================

    @Test
    fun buildBlocks_vocabSelectsNewWordsByRankAscending() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        // Create a vocab CSV file with nouns sorted by rank
        val vocabFile = createVocabNounsCsv(
            "rank,noun,collocations,ru" to listOf(
                "3,casa,,house",
                "1,gatto,,cat",
                "2,libro,,book"
            )
        )
        mockLessonStore.vocabDrillFiles = listOf(vocabFile)
        // No mastery state -- all words are "new"

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID
        )

        val vocabBlock = blocks.firstOrNull { it.type == DailyBlockType.VOCAB }
        assertNotNull("vocab block should exist when vocab data present", vocabBlock)
        assertEquals("expected 3 vocab tasks", SESSION_SIZE, vocabBlock!!.tasks.size)
        val words = vocabBlock.tasks.map { (it as DailyTask.VocabFlashcard).word }
        // New words sorted by rank ascending
        assertEquals("gatto", words[0].word)
        assertEquals("libro", words[1].word)
        assertEquals("casa", words[2].word)
    }

    @Test
    fun buildBlocks_vocabSessionSizeLimitsOutput() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        // 5 words available, sessionSize=3
        val vocabFile = createVocabNounsCsv(
            "rank,noun,collocations,ru" to listOf(
                "1,casa,,house", "2,gatto,,cat", "3,libro,,book",
                "4,tavolo,,table", "5,sedia,,chair"
            )
        )
        mockLessonStore.vocabDrillFiles = listOf(vocabFile)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID
        )

        val vocabBlock = blocks.first { it.type == DailyBlockType.VOCAB }
        assertEquals("sessionSize should limit output to 3", SESSION_SIZE, vocabBlock.tasks.size)
    }

    @Test
    fun buildBlocks_vocabDueWordsSelectedFirst() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        val vocabFile = createVocabNounsCsv(
            "rank,noun,collocations,ru" to listOf(
                "1,casa,,house", "2,gatto,,cat", "3,libro,,book"
            )
        )
        mockLessonStore.vocabDrillFiles = listOf(vocabFile)
        // "gatto" is due (nextReviewDate in the past)
        val now = System.currentTimeMillis()
        mockWordMasteryStore.setMastery(
            WordMasteryState(wordId = "nouns_2_gatto", intervalStepIndex = 2, lastReviewDateMs = now - 5_000_000, nextReviewDateMs = now - 1_000_000)
        )
        // "casa" is not due (nextReviewDate in the future)
        mockWordMasteryStore.setMastery(
            WordMasteryState(wordId = "nouns_1_casa", intervalStepIndex = 2, lastReviewDateMs = now, nextReviewDateMs = now + 10_000_000)
        )
        // "libro" is new (no mastery)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID
        )

        val vocabBlock = blocks.first { it.type == DailyBlockType.VOCAB }
        val words = vocabBlock.tasks.map { (it as DailyTask.VocabFlashcard).word }
        // gatto is due, libro is new, casa is scheduled (future). Due first, then new.
        assertEquals("gatto should be first (due)", "gatto", words[0].word)
        assertEquals("libro should be second (new)", "libro", words[1].word)
        assertEquals("casa should be third (fallback)", "casa", words[2].word)
    }

    @Test
    fun buildBlocks_vocabExcludesNumbersCategory() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        // Numbers CSV file -- should be excluded
        val numbersFile = createVocabNumbersCsv(
            listOf("cardinal,uno,one,m,," , "cardinale,due,two,m,,")
        )
        mockLessonStore.vocabDrillFiles = listOf(numbersFile)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID
        )

        val hasVocab = blocks.any { it.type == DailyBlockType.VOCAB }
        assertFalse("numbers should be excluded from vocab block", hasVocab)
    }

    @Test
    fun buildBlocks_vocabReturnsEmptyWhenNoVocabFilesAvailable() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        mockLessonStore.vocabDrillFiles = emptyList()

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID
        )

        val hasVocab = blocks.any { it.type == DailyBlockType.VOCAB }
        assertFalse("no vocab block when no vocab files", hasVocab)
    }

    @Test
    fun buildBlocks_vocabDirectionAlternatesItRuAndRuIt() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        val vocabFile = createVocabNounsCsv(
            "rank,noun,collocations,ru" to listOf(
                "1,casa,,house", "2,gatto,,cat", "3,libro,,book"
            )
        )
        mockLessonStore.vocabDrillFiles = listOf(vocabFile)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID
        )

        val vocabBlock = blocks.first { it.type == DailyBlockType.VOCAB }
        val directions = vocabBlock.tasks.map { (it as DailyTask.VocabFlashcard).direction }
        assertEquals(VocabDrillDirection.IT_TO_RU, directions[0])
        assertEquals(VocabDrillDirection.RU_TO_IT, directions[1])
        assertEquals(VocabDrillDirection.IT_TO_RU, directions[2])
    }

    // ====================================================================
    // 3. VERBS block (buildVerbBlock)
    // ====================================================================

    @Test
    fun buildBlocks_verbsFiltersByActiveTenses() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        val verbFile = createVerbDrillCsv(listOf(
            "Presente congiunto" to listOf("io parlo;parlo;parlare;Presente;regular;1", "tu parli;parli;parlare;Presente;regular;2"),
            "Imperfetto congiunto" to listOf("io parlavo;parlavo;parlare;Imperfetto;regular;1")
        ))
        mockLessonStore.verbDrillFiles = listOf(verbFile)
        // Only Presente is active
        val activeTenses = listOf("Presente")

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cumulativeTenses = activeTenses
        )

        val verbsBlock = blocks.first { it.type == DailyBlockType.VERBS }
        val cards = verbsBlock.tasks.map { (it as DailyTask.ConjugateVerb).card }
        assertTrue("all cards should be Presente tense", cards.all { it.tense == "Presente" })
    }

    @Test
    fun buildBlocks_verbsReturnsEmptyWhenActiveTensesIsEmpty() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        val verbFile = createVerbDrillCsv(listOf(
            "Presente congiunto" to listOf("io parlo;parlo;parlare;Presente;regular;1")
        ))
        mockLessonStore.verbDrillFiles = listOf(verbFile)

        composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cumulativeTenses = emptyList(),
            cursor = DailyCursorState()
        )
        // TENSE_LADDER[1] = ["Presente"] -- but lessonLevel=0 is not in the ladder,
        // so activeTenses will be empty.
        // Now test with lessonLevel=0 explicitly
        val blocks2 = composer.buildBlocks(
            lessonLevel = 0, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cumulativeTenses = emptyList()
        )
        val hasVerbs = blocks2.any { it.type == DailyBlockType.VERBS }
        assertFalse("no verbs block when no active tenses and lessonLevel=0", hasVerbs)
    }

    @Test
    fun buildBlocks_verbsExcludesPreviouslyShownCards() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        val verbFile = createVerbDrillCsv(listOf(
            "Presente congiunto" to listOf(
                "io parlo;parlo;parlare;Presente;regular;1",
                "tu parli;parli;parlare;Presente;regular;2",
                "lui parla;parla;parlare;Presente;regular;3",
                "noi parliamo;parliamo;parlare;Presente;regular;4"
            )
        ))
        mockLessonStore.verbDrillFiles = listOf(verbFile)

        // Mark vc1 and vc2 as previously shown
        mockVerbDrillStore.progress = mapOf(
            "regular|Presente" to VerbDrillComboProgress(
                group = "regular", tense = "Presente", totalCards = 4,
                everShownCardIds = setOf("vc1", "vc2")
            )
        )

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cumulativeTenses = listOf("Presente")
        )

        val verbsBlock = blocks.first { it.type == DailyBlockType.VERBS }
        val ids = verbsBlock.tasks.map { (it as DailyTask.ConjugateVerb).card.id }
        assertFalse("vc1 should be excluded (already shown)", ids.contains("vc1"))
        assertFalse("vc2 should be excluded (already shown)", ids.contains("vc2"))
        assertTrue("vc3 should be included", ids.contains("vc3"))
        assertTrue("vc4 should be included", ids.contains("vc4"))
    }

    @Test
    fun buildBlocks_verbsCyclesWhenAllCardsShown() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        val verbFile = createVerbDrillCsv(listOf(
            "Presente congiunto" to listOf(
                "io parlo;parlo;parlare;Presente;regular;1",
                "tu parli;parli;parlare;Presente;regular;2"
            )
        ))
        mockLessonStore.verbDrillFiles = listOf(verbFile)

        // Mark ALL cards as previously shown
        mockVerbDrillStore.progress = mapOf(
            "regular|Presente" to VerbDrillComboProgress(
                group = "regular", tense = "Presente", totalCards = 2,
                everShownCardIds = setOf("vc1", "vc2")
            )
        )

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cumulativeTenses = listOf("Presente")
        )

        val verbsBlock = blocks.first { it.type == DailyBlockType.VERBS }
        val ids = verbsBlock.tasks.map { (it as DailyTask.ConjugateVerb).card.id }
        // When all shown, composer cycles and reuses all filtered cards
        assertTrue("should cycle and include vc1", ids.contains("vc1"))
        assertTrue("should cycle and include vc2", ids.contains("vc2"))
    }

    @Test
    fun buildBlocks_verbsSessionSizeLimitsOutput() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        // 5 verb cards, sessionSize=3
        val verbFile = createVerbDrillCsv(listOf(
            "Presente congiunto" to listOf(
                "io parlo;parlo;parlare;Presente;regular;1",
                "tu parli;parli;parlare;Presente;regular;2",
                "lui parla;parla;parlare;Presente;regular;3",
                "noi parliamo;parliamo;parlare;Presente;regular;4",
                "voi parlate;parlate;parlare;Presente;regular;5"
            )
        ))
        mockLessonStore.verbDrillFiles = listOf(verbFile)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cumulativeTenses = listOf("Presente")
        )

        val verbsBlock = blocks.first { it.type == DailyBlockType.VERBS }
        assertEquals("sessionSize should limit verb output to 3", SESSION_SIZE, verbsBlock.tasks.size)
    }

    @Test
    fun buildBlocks_verbsReturnsEmptyWhenNoVerbFiles() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        mockLessonStore.verbDrillFiles = emptyList()

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cumulativeTenses = listOf("Presente")
        )

        val hasVerbs = blocks.any { it.type == DailyBlockType.VERBS }
        assertFalse("no verbs block when no verb drill files", hasVerbs)
    }

    @Test
    fun buildBlocks_verbsInputModesAlternateKeyboardWordbank() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        val verbFile = createVerbDrillCsv(listOf(
            "Presente congiunto" to listOf(
                "io parlo;parlo;parlare;Presente;regular;1",
                "tu parli;parli;parlare;Presente;regular;2",
                "lui parla;parla;parlare;Presente;regular;3"
            )
        ))
        mockLessonStore.verbDrillFiles = listOf(verbFile)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cumulativeTenses = listOf("Presente")
        )

        val verbsBlock = blocks.first { it.type == DailyBlockType.VERBS }
        val modes = verbsBlock.tasks.map { (it as DailyTask.ConjugateVerb).inputMode }
        assertEquals(InputMode.KEYBOARD, modes[0])
        assertEquals(InputMode.WORD_BANK, modes[1])
        assertEquals(InputMode.KEYBOARD, modes[2])
    }

    // ====================================================================
    // 4. Integration: buildBlocks
    // ====================================================================

    @Test
    fun buildBlocks_returns3BlocksInTranslateVocabVerbsOrder() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1",
                listOf(card1, card2, card3))
        )
        val vocabFile = createVocabNounsCsv(
            "rank,noun,collocations,ru" to listOf(
                "1,casa,,house", "2,gatto,,cat", "3,libro,,book"
            )
        )
        val verbFile = createVerbDrillCsv(listOf(
            "Presente congiunto" to listOf(
                "io parlo;parlo;parlare;Presente;regular;1",
                "tu parli;parli;parlare;Presente;regular;2",
                "lui parla;parla;parlare;Presente;regular;3"
            )
        ))
        mockLessonStore.vocabDrillFiles = listOf(vocabFile)
        mockLessonStore.verbDrillFiles = listOf(verbFile)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cumulativeTenses = listOf("Presente")
        )

        assertEquals("expected 3 blocks", 3, blocks.size)
        assertEquals(DailyBlockType.TRANSLATE, blocks[0].type)
        assertEquals(DailyBlockType.VOCAB, blocks[1].type)
        assertEquals(DailyBlockType.VERBS, blocks[2].type)
    }

    @Test
    fun buildBlocks_withSessionSize3_producesBlocksOf3CardsEach() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1",
                listOf(card1, card2, card3, card4, card5))
        )
        val vocabFile = createVocabNounsCsv(
            "rank,noun,collocations,ru" to listOf(
                "1,casa,,house", "2,gatto,,cat", "3,libro,,book"
            )
        )
        val verbFile = createVerbDrillCsv(listOf(
            "Presente congiunto" to listOf(
                "io parlo;parlo;parlare;Presente;regular;1",
                "tu parli;parli;parlare;Presente;regular;2",
                "lui parla;parla;parlare;Presente;regular;3"
            )
        ))
        mockLessonStore.vocabDrillFiles = listOf(vocabFile)
        mockLessonStore.verbDrillFiles = listOf(verbFile)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cumulativeTenses = listOf("Presente")
        )

        blocks.forEach { block ->
            assertEquals("block ${block.type} should have 3 tasks", SESSION_SIZE, block.tasks.size)
        }
    }

    @Test
    fun buildBlocks_omitsEmptyBlocks() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1",
                listOf(card1, card2, card3))
        )
        // No vocab or verb files -- those blocks will be empty
        mockLessonStore.vocabDrillFiles = emptyList()
        mockLessonStore.verbDrillFiles = emptyList()

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cumulativeTenses = listOf("Presente")
        )

        assertEquals("only translate block should be present", 1, blocks.size)
        assertEquals(DailyBlockType.TRANSLATE, blocks[0].type)
    }

    @Test
    fun buildSession_returnsFlatTaskListInOrder() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1",
                listOf(card1, card2, card3))
        )
        val vocabFile = createVocabNounsCsv(
            "rank,noun,collocations,ru" to listOf(
                "1,casa,,house", "2,gatto,,cat"
            )
        )
        val verbFile = createVerbDrillCsv(listOf(
            "Presente congiunto" to listOf(
                "io parlo;parlo;parlare;Presente;regular;1",
                "tu parli;parli;parlare;Presente;regular;2"
            )
        ))
        mockLessonStore.vocabDrillFiles = listOf(vocabFile)
        mockLessonStore.verbDrillFiles = listOf(verbFile)

        val tasks = composer.buildSession(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cumulativeTenses = listOf("Presente")
        )

        // TRANSLATE (3) + VOCAB (2) + VERBS (2) = 7
        assertEquals("expected 7 tasks total", 7, tasks.size)
        assertTrue(tasks[0] is DailyTask.TranslateSentence)
        assertTrue(tasks[1] is DailyTask.TranslateSentence)
        assertTrue(tasks[2] is DailyTask.TranslateSentence)
        assertTrue(tasks[3] is DailyTask.VocabFlashcard)
        assertTrue(tasks[4] is DailyTask.VocabFlashcard)
        assertTrue(tasks[5] is DailyTask.ConjugateVerb)
        assertTrue(tasks[6] is DailyTask.ConjugateVerb)
    }

    // ====================================================================
    // 5. buildRepeatBlocks
    // ====================================================================

    @Test
    fun buildRepeatBlocks_usesStoredSentenceCardIds() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1",
                listOf(card1, card2, card3, card4, card5))
        )
        val sentenceCardIds = listOf("c2", "c4")

        val blocks = composer.buildRepeatBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, sentenceCardIds = sentenceCardIds
        )

        val translateBlock = blocks.first { it.type == DailyBlockType.TRANSLATE }
        val ids = translateBlock.tasks.map { (it as DailyTask.TranslateSentence).card.id }
        assertEquals(listOf("c2", "c4"), ids)
    }

    @Test
    fun buildRepeatBlocks_usesStoredVerbCardIds() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        val verbFile = createVerbDrillCsv(listOf(
            "Presente congiunto" to listOf(
                "io parlo;parlo;parlare;Presente;regular;1",
                "tu parli;parli;parlare;Presente;regular;2",
                "lui parla;parla;parlare;Presente;regular;3"
            )
        ))
        mockLessonStore.verbDrillFiles = listOf(verbFile)
        val verbCardIds = listOf("vc2", "vc3")

        val blocks = composer.buildRepeatBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, verbCardIds = verbCardIds
        )

        val verbsBlock = blocks.first { it.type == DailyBlockType.VERBS }
        val ids = verbsBlock.tasks.map { (it as DailyTask.ConjugateVerb).card.id }
        assertEquals(listOf("vc2", "vc3"), ids)
    }

    @Test
    fun buildRepeatBlocks_vocabBlockBuiltIndependently() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        val vocabFile = createVocabNounsCsv(
            "rank,noun,collocations,ru" to listOf(
                "1,casa,,house", "2,gatto,,cat"
            )
        )
        mockLessonStore.vocabDrillFiles = listOf(vocabFile)
        // Repeat with empty card ID lists -- vocab block should still be built from SRS
        val blocks = composer.buildRepeatBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID
        )

        val vocabBlock = blocks.firstOrNull { it.type == DailyBlockType.VOCAB }
        assertNotNull("vocab block should exist even in repeat mode", vocabBlock)
        assertEquals(2, vocabBlock!!.tasks.size)
    }

    @Test
    fun buildRepeatBlocks_emptyCardIds_returnsNoTranslateAndVerbsBlocks() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        mockLessonStore.vocabDrillFiles = emptyList()
        mockLessonStore.verbDrillFiles = emptyList()

        val blocks = composer.buildRepeatBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, sentenceCardIds = emptyList(), verbCardIds = emptyList()
        )

        // No blocks at all: no sentence IDs, no vocab files, no verb IDs
        assertTrue("all blocks should be empty", blocks.isEmpty())
    }

    // ====================================================================
    // 6. buildRepeatSession (flat task list)
    // ====================================================================

    @Test
    fun buildRepeatSession_returnsFlatTasksInTranslateVocabVerbsOrder() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1",
                listOf(card1, card2))
        )
        val vocabFile = createVocabNounsCsv(
            "rank,noun,collocations,ru" to listOf("1,casa,,house")
        )
        val verbFile = createVerbDrillCsv(listOf(
            "Presente congiunto" to listOf("io parlo;parlo;parlare;Presente;regular;1")
        ))
        mockLessonStore.vocabDrillFiles = listOf(vocabFile)
        mockLessonStore.verbDrillFiles = listOf(verbFile)

        val tasks = composer.buildRepeatSession(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, sentenceCardIds = listOf("c1", "c2"),
            verbCardIds = listOf("vc1")
        )

        // 2 translate + 1 vocab + 1 verb = 4
        assertEquals(4, tasks.size)
        assertTrue(tasks[0] is DailyTask.TranslateSentence)
        assertTrue(tasks[1] is DailyTask.TranslateSentence)
        assertTrue(tasks[2] is DailyTask.VocabFlashcard)
        assertTrue(tasks[3] is DailyTask.ConjugateVerb)
    }

    // ====================================================================
    // 7. rebuildBlock
    // ====================================================================

    @Test
    fun rebuildBlock_translate_returnsTranslateTasks() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1",
                listOf(card1, card2, card3))
        )
        val cursor = DailyCursorState(currentLessonIndex = 0, sentenceOffset = 0)

        val tasks = composer.rebuildBlock(
            blockType = DailyBlockType.TRANSLATE,
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cursor = cursor
        )

        assertEquals(SESSION_SIZE, tasks.size)
        assertTrue(tasks.all { it is DailyTask.TranslateSentence })
    }

    @Test
    fun rebuildBlock_vocab_returnsVocabTasks() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        val vocabFile = createVocabNounsCsv(
            "rank,noun,collocations,ru" to listOf(
                "1,casa,,house", "2,gatto,,cat", "3,libro,,book"
            )
        )
        mockLessonStore.vocabDrillFiles = listOf(vocabFile)

        val tasks = composer.rebuildBlock(
            blockType = DailyBlockType.VOCAB,
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID
        )

        assertEquals(SESSION_SIZE, tasks.size)
        assertTrue(tasks.all { it is DailyTask.VocabFlashcard })
    }

    @Test
    fun rebuildBlockAsBlock_returnsDailyBlockWithCorrectType() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1",
                listOf(card1, card2, card3))
        )
        val cursor = DailyCursorState(currentLessonIndex = 0, sentenceOffset = 0)

        val block = composer.rebuildBlockAsBlock(
            blockType = DailyBlockType.TRANSLATE,
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID, cursor = cursor
        )

        assertEquals(DailyBlockType.TRANSLATE, block.type)
        assertEquals(SESSION_SIZE, block.tasks.size)
    }

    // ====================================================================
    // 8. tasksToBlocks
    // ====================================================================

    @Test
    fun tasksToBlocks_groupsTasksByBlockType() {
        val tasks = listOf(
            DailyTask.TranslateSentence("s1", card1, InputMode.VOICE),
            DailyTask.TranslateSentence("s2", card2, InputMode.KEYBOARD),
            DailyTask.VocabFlashcard("v1", VocabWord(id = "w1", word = "casa", pos = "nouns", rank = 1, meaningRu = "house"), VocabDrillDirection.IT_TO_RU),
            DailyTask.ConjugateVerb("vb1", verbCard1, InputMode.KEYBOARD),
            DailyTask.ConjugateVerb("vb2", verbCard2, InputMode.WORD_BANK)
        )

        val blocks = composer.tasksToBlocks(tasks)

        assertEquals(3, blocks.size)
        assertEquals(DailyBlockType.TRANSLATE, blocks[0].type)
        assertEquals(2, blocks[0].tasks.size)
        assertEquals(DailyBlockType.VOCAB, blocks[1].type)
        assertEquals(1, blocks[1].tasks.size)
        assertEquals(DailyBlockType.VERBS, blocks[2].type)
        assertEquals(2, blocks[2].tasks.size)
    }

    @Test
    fun tasksToBlocks_emptyList_returnsEmpty() {
        val blocks = composer.tasksToBlocks(emptyList())
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun tasksToBlocks_singleBlock_returnsSingleBlock() {
        val tasks = listOf(
            DailyTask.TranslateSentence("s1", card1, InputMode.VOICE),
            DailyTask.TranslateSentence("s2", card2, InputMode.KEYBOARD)
        )

        val blocks = composer.tasksToBlocks(tasks)

        assertEquals(1, blocks.size)
        assertEquals(DailyBlockType.TRANSLATE, blocks[0].type)
        assertEquals(2, blocks[0].tasks.size)
    }

    // ====================================================================
    // 9. TENSE_LADDER
    // ====================================================================

    @Test
    fun tenseLadder_level1_returnsPresente() {
        val tenses = DailySessionComposer.TENSE_LADDER[1]
        assertEquals(listOf("Presente"), tenses)
    }

    @Test
    fun tenseLadder_level3_returnsThreeTenses() {
        val tenses = DailySessionComposer.TENSE_LADDER[3]
        assertEquals(3, tenses!!.size)
        assertEquals("Presente", tenses[0])
        assertEquals("Imperfetto", tenses[1])
        assertEquals("Passato Prossimo", tenses[2])
    }

    @Test
    fun tenseLadder_level12_returnsAllTenses() {
        val tenses = DailySessionComposer.TENSE_LADDER[12]
        assertEquals(12, tenses!!.size)
    }

    @Test
    fun tenseLadder_level0_returnsNull() {
        val tenses = DailySessionComposer.TENSE_LADDER[0]
        assertNull(tenses)
    }

    @Test
    fun tenseLadder_level13_returnsNull() {
        val tenses = DailySessionComposer.TENSE_LADDER[13]
        assertNull(tenses)
    }

    // ====================================================================
    // 10. invalidateCache
    // ====================================================================

    @Test
    fun invalidateCache_withPackAndLanguage_clearsMatchingCache() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId(LESSON_ID), LanguageId(LANGUAGE_ID), "L1", listOf(card1))
        )
        val vocabFile = createVocabNounsCsv(
            "rank,noun,collocations,ru" to listOf("1,casa,,house")
        )
        mockLessonStore.vocabDrillFiles = listOf(vocabFile)

        // Build once to populate cache
        composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID
        )

        // Invalidate
        composer.invalidateCache(PACK_ID, LANGUAGE_ID)

        // Modify data
        val newFile = createVocabNounsCsv(
            "rank,noun,collocations,ru" to listOf("1,gatto,,cat", "2,libro,,book")
        )
        mockLessonStore.vocabDrillFiles = listOf(newFile)

        // Build again -- should use fresh data
        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = LESSON_ID
        )
        val vocabBlock = blocks.first { it.type == DailyBlockType.VOCAB }
        val word = (vocabBlock.tasks[0] as DailyTask.VocabFlashcard).word.word
        assertEquals("gatto", word)
    }

    @Test
    fun invalidateCache_withNullParams_clearsAllCaches() {
        // Should not crash
        composer.invalidateCache()
    }

    // ====================================================================
    // 11. Edge case: cursor falls back to lessonId match
    // ====================================================================

    @Test
    fun buildBlocks_cursorIndexOutOfBounds_fallsBackToLessonIdMatch() = runBlocking {
        mockLessonStore.lessons = listOf(
            Lesson(LessonId("other"), LanguageId(LANGUAGE_ID), "Other", listOf(card1)),
            Lesson(LessonId("lesson-1"), LanguageId(LANGUAGE_ID), "L1", listOf(card2, card3, card4))
        )
        // Index 99 is out of bounds, should fall back to matching lessonId "lesson-1"
        val cursor = DailyCursorState(currentLessonIndex = 99, sentenceOffset = 0)

        val blocks = composer.buildBlocks(
            lessonLevel = 1, packId = PACK_ID, languageId = LANGUAGE_ID,
            lessonId = "lesson-1", cursor = cursor
        )

        val translateBlock = blocks.first { it.type == DailyBlockType.TRANSLATE }
        val ids = translateBlock.tasks.map { (it as DailyTask.TranslateSentence).card.id }
        assertEquals(listOf("c2", "c3", "c4"), ids)
    }

    // ====================================================================
    // Helpers: temp file creation for CSV data
    // ====================================================================

    /**
     * Create a temp CSV file with verb drill data.
     * Format: title line, header line (RU;IT;Verb;Tense;Group), data rows.
     * Each entry maps a title to a list of semicolon-separated data rows.
     */
    private fun createVerbDrillCsv(
        sections: List<Pair<String, List<String>>>
    ): File {
        val sb = StringBuilder()
        for ((title, rows) in sections) {
            sb.appendLine("# $title")
            sb.appendLine("RU;IT;Verb;Tense;Group")
            for (row in rows) {
                sb.appendLine(row)
            }
        }
        val file = File.createTempFile("verb_drill_test", ".csv")
        file.writeText(sb.toString())
        tempFiles.add(file)
        return file
    }

    /**
     * Create a temp CSV file with vocab noun data.
     * Format: header + data rows as comma-separated values.
     */
    private fun createVocabNounsCsv(
        data: Pair<String, List<String>>
    ): File {
        val sb = StringBuilder()
        sb.appendLine(data.first)
        for (row in data.second) {
            sb.appendLine(row)
        }
        val file = File.createTempFile("vocab_nouns_test", ".csv")
        file.writeText(sb.toString())
        tempFiles.add(file)
        return file
    }

    /**
     * Create a temp CSV file with numbers vocab data.
     * Format: category,italian,ru,form_m,form_f,notes
     */
    private fun createVocabNumbersCsv(rows: List<String>): File {
        val sb = StringBuilder()
        sb.appendLine("category,italian,ru,form_m,form_f,notes")
        for (row in rows) {
            sb.appendLine(row)
        }
        val file = File.createTempFile("vocab_numbers_test", ".csv")
        file.writeText(sb.toString())
        tempFiles.add(file)
        return file
    }

    // ====================================================================
    // Test doubles
    // ====================================================================

    /**
     * In-memory [LessonStore] mock with configurable data.
     * Only the methods used by [DailySessionComposer] are implemented.
     */
    private class MockComposerLessonStore : LessonStore {
        var lessons: List<Lesson> = emptyList()
        var vocabDrillFiles: List<File> = emptyList()
        var verbDrillFiles: List<File> = emptyList()

        override fun getLessons(languageId: String): List<Lesson> = lessons
        override fun getVocabDrillFiles(packId: String, languageId: String): List<File> = vocabDrillFiles
        override fun getVerbDrillFiles(packId: String, languageId: String): List<File> = verbDrillFiles

        // -- Unused stubs ------------------------------------------------

        override fun ensureSeedData() {}
        override fun seedDefaultPacksIfNeeded(): Boolean = false
        override fun updateDefaultPacksIfNeeded(): Boolean = false
        override fun forceReloadDefaultPacks(): Boolean = false
        override fun getLanguages(): List<Language> = emptyList()
        override fun addLanguage(name: String): Language = Language(LanguageId("it"), "Italian")
        override fun getInstalledPacks(): List<LessonPack> = emptyList()
        override fun getPackIdForLesson(lessonId: String): String? = null
        override fun getLessonIdsForPack(packId: String): List<String> = emptyList()
        override fun getCumulativeTenses(packId: String, lessonLevel: Int): List<String> = emptyList()
        override fun importPackFromUri(uri: android.net.Uri, resolver: android.content.ContentResolver): LessonPack =
            LessonPack(packId = PackId("mock"), packVersion = "1", languageId = LanguageId("it"), importedAt = 0L)
        override fun importPackFromAssets(assetPath: String): LessonPack =
            LessonPack(packId = PackId("mock"), packVersion = "1", languageId = LanguageId("it"), importedAt = 0L)
        override fun removeInstalledPackData(packId: String): Boolean = true
        override fun importFromUri(languageId: String, uri: android.net.Uri, resolver: android.content.ContentResolver): Lesson =
            Lesson(LessonId("mock"), LanguageId(languageId), "Mock", emptyList())
        override fun deleteAllLessons(languageId: String) {}
        override fun deleteLesson(languageId: String, lessonId: String) {}
        override fun createEmptyLesson(languageId: String, title: String): Lesson =
            Lesson(LessonId("mock"), LanguageId(languageId), title, emptyList())
        override fun getStoryQuizzes(lessonId: String, phase: StoryPhase, languageId: String): List<StoryQuiz> = emptyList()
        override fun getVocabEntries(lessonId: String, languageId: String): List<VocabEntry> = emptyList()
        override fun getVerbDrillFilesForPack(packId: String): List<File> = emptyList()
        override fun getVocabDrillFilesForPack(packId: String): List<File> = emptyList()
        override fun getVocabWordsByRankRange(packId: String, languageId: String, fromRank: Int, toRank: Int): List<VocabWord> = emptyList()
        override fun hasVerbDrill(packId: String, languageId: String): Boolean = false
        override fun hasVocabDrill(packId: String, languageId: String): Boolean = false
        @Suppress("DEPRECATION")
        override fun getVerbDrillFiles(languageId: String): List<File> = emptyList()
        @Suppress("DEPRECATION")
        override fun hasVerbDrillLessons(languageId: String): Boolean = false
    }

    /**
     * In-memory [VerbDrillStore] mock with configurable progress data.
     */
    private class MockComposerVerbDrillStore : VerbDrillStore {
        var progress: Map<String, VerbDrillComboProgress> = emptyMap()

        override fun loadProgress(): Map<String, VerbDrillComboProgress> = progress
        override fun saveProgress(progress: Map<String, VerbDrillComboProgress>) {}
        override fun getComboProgress(key: String): VerbDrillComboProgress? = progress[key]
        override fun upsertComboProgress(key: String, progress: VerbDrillComboProgress) {}
        override fun loadAllCardsForPack(targetPackId: String, languageId: String): List<VerbDrillCard> = emptyList()
        override fun getCardsForTenses(packId: String, languageId: String, tenses: List<String>): List<VerbDrillCard> = emptyList()
        override fun flush() {}
    }

    /**
     * In-memory [WordMasteryStore] mock with settable mastery state.
     */
    private class MockComposerWordMasteryStore : WordMasteryStore {
        private val masteryMap = mutableMapOf<String, WordMasteryState>()

        fun setMastery(state: WordMasteryState) {
            masteryMap[state.wordId] = state
        }

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
