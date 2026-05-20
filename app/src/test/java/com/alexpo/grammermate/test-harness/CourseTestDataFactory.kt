package com.alexpo.grammermate.testharness

import com.alexpo.grammermate.data.*

/**
 * Factory for generating test data for full-course simulation.
 *
 * Creates deterministic test data for lessons, cards, and drill content
 * with configurable parameters for course size and content distribution.
 *
 * Usage:
 * ```kotlin
 * val factory = CourseTestDataFactory()
 * val courseData = factory.buildMinimalCourse(
 *     lessonCount = 10,
 *     packId = "test_pack",
 *     languageId = "it"
 * )
 * lessonStore.loadCourseTestData(courseData)
 * ```
 */
class CourseTestDataFactory {

    /**
     * Complete test course data including all lessons, cards, and drill content.
     */
    data class CourseTestData(
        val packId: String,
        val languageId: String,
        val lessons: List<LessonTestData>,
        val verbDrill: VerbDrillCourseData?,
        val vocabDrill: VocabDrillCourseData?
    ) {
        /** Total number of sentence cards across all lessons. */
        val totalCards: Int get() = lessons.sumOf { it.cards.size }

        /** Total number of drill cards across all lessons. */
        val totalDrillCards: Int get() = lessons.sumOf { it.drillCards.size }
    }

    /**
     * Test data for a single lesson including main pool, reserve pool, and drill cards.
     */
    data class LessonTestData(
        val lessonId: String,
        val title: String,
        val cards: List<SentenceCard>,
        val drillCards: List<SentenceCard>
    ) {
        /** Main pool cards (first 150 cards for mastery calculation). */
        val mainPool: List<SentenceCard> get() = cards.take(Lesson.MAIN_POOL_SIZE)

        /** Reserve pool cards (cards beyond first 150). */
        val reservePool: List<SentenceCard> get() = cards.drop(Lesson.MAIN_POOL_SIZE)

        /** Convert to Lesson domain model. */
        fun toLesson(languageId: LanguageId): Lesson = Lesson(
            id = LessonId(lessonId),
            languageId = languageId,
            title = title,
            cards = cards,
            drillCards = drillCards
        )
    }

    /**
     * Test data for verb drill content.
     */
    data class VerbDrillCourseData(
        val tenseGroups: List<TenseGroupData>
    ) {
        data class TenseGroupData(
            val tense: String,
            val group: String,
            val cards: List<VerbDrillCard>
        )

        /** All cards across all tense-group combinations. */
        val allCards: List<VerbDrillCard> get() = tenseGroups.flatMap { it.cards }
    }

    /**
     * Test data for vocab drill content.
     */
    data class VocabDrillCourseData(
        val wordsByPos: Map<String, List<VocabWord>>
    ) {
        /** All words across all parts of speech. */
        val allWords: List<VocabWord> get() = wordsByPos.values.flatten()

        /** Get words for a specific part of speech. */
        fun getWordsForPos(pos: String): List<VocabWord> = wordsByPos[pos] ?: emptyList()
    }

    /**
     * Builder for configurable course test data generation.
     */
    class Builder(
        private var lessonCount: Int = 5,
        private var cardsPerLesson: Int = 200,
        private var drillCardsPerLesson: Int = 50,
        private var packId: String = "test_pack",
        private var languageId: String = "it",
        private var includeVerbDrill: Boolean = true,
        private var includeVocabDrill: Boolean = true,
        private var verbTenses: List<String> = listOf("Presente", "Passato Prossimo"),
        private var verbGroups: List<String> = listOf("ARE", "ERE", "IRE"),
        private var verbCardsPerCombo: Int = 30,
        private var vocabPosList: List<String> = listOf("nouns", "verbs", "adjectives", "adverbs"),
        private var vocabWordsPerPos: Int = 100
    ) {
        fun lessonCount(count: Int) = apply { lessonCount = count.coerceIn(5, 20) }
        fun cardsPerLesson(count: Int) = apply { cardsPerLesson = count.coerceAtLeast(150) }
        fun drillCardsPerLesson(count: Int) = apply { drillCardsPerLesson = count.coerceAtLeast(0) }
        fun packId(id: String) = apply { packId = id }
        fun languageId(id: String) = apply { languageId = id }
        fun includeVerbDrill(include: Boolean) = apply { includeVerbDrill = include }
        fun includeVocabDrill(include: Boolean) = apply { includeVocabDrill = include }
        fun verbTenses(tenses: List<String>) = apply { verbTenses = tenses }
        fun verbGroups(groups: List<String>) = apply { verbGroups = groups }
        fun verbCardsPerCombo(count: Int) = apply { verbCardsPerCombo = count.coerceAtLeast(1) }
        fun vocabPosList(list: List<String>) = apply { vocabPosList = list }
        fun vocabWordsPerPos(count: Int) = apply { vocabWordsPerPos = count.coerceAtLeast(1) }

        fun build(): CourseTestData {
            val factory = CourseTestDataFactory()
            return factory.build(
                lessonCount = lessonCount,
                cardsPerLesson = cardsPerLesson,
                drillCardsPerLesson = drillCardsPerLesson,
                packId = packId,
                languageId = languageId,
                includeVerbDrill = includeVerbDrill,
                includeVocabDrill = includeVocabDrill,
                verbTenses = verbTenses,
                verbGroups = verbGroups,
                verbCardsPerCombo = verbCardsPerCombo,
                vocabPosList = vocabPosList,
                vocabWordsPerPos = vocabWordsPerPos
            )
        }
    }

    /**
     * Build a minimal course with default parameters.
     *
     * @param lessonCount Number of lessons (5-20, default 5)
     * @param packId Pack identifier for scoping
     * @param languageId Language code (default "it")
     */
    fun buildMinimalCourse(
        lessonCount: Int = 5,
        packId: String = "test_pack",
        languageId: String = "it"
    ): CourseTestData = build(
        lessonCount = lessonCount,
        cardsPerLesson = 200,
        drillCardsPerLesson = 50,
        packId = packId,
        languageId = languageId,
        includeVerbDrill = true,
        includeVocabDrill = true
    )

    /**
     * Build course data with full configuration.
     */
    fun build(
        lessonCount: Int,
        cardsPerLesson: Int,
        drillCardsPerLesson: Int,
        packId: String,
        languageId: String,
        includeVerbDrill: Boolean,
        includeVocabDrill: Boolean,
        verbTenses: List<String> = listOf("Presente", "Passato Prossimo"),
        verbGroups: List<String> = listOf("ARE", "ERE", "IRE"),
        verbCardsPerCombo: Int = 30,
        vocabPosList: List<String> = listOf("nouns", "verbs", "adjectives", "adverbs"),
        vocabWordsPerPos: Int = 100
    ): CourseTestData {
        val lessons = (1..lessonCount).map { lessonIndex ->
            createLessonTestData(
                lessonIndex = lessonIndex,
                packId = packId,
                cardsPerLesson = cardsPerLesson,
                drillCardsPerLesson = drillCardsPerLesson
            )
        }

        val verbDrill = if (includeVerbDrill) {
            createVerbDrillData(
                packId = packId,
                tenses = verbTenses,
                groups = verbGroups,
                cardsPerCombo = verbCardsPerCombo
            )
        } else null

        val vocabDrill = if (includeVocabDrill) {
            createVocabDrillData(
                packId = packId,
                posList = vocabPosList,
                wordsPerPos = vocabWordsPerPos
            )
        } else null

        return CourseTestData(
            packId = packId,
            languageId = languageId,
            lessons = lessons,
            verbDrill = verbDrill,
            vocabDrill = vocabDrill
        )
    }

    /**
     * Create test data for a single lesson.
     */
    fun createLessonTestData(
        lessonIndex: Int,
        packId: String,
        cardsPerLesson: Int,
        drillCardsPerLesson: Int
    ): LessonTestData {
        val lessonId = "${packId}_lesson_$lessonIndex"
        val title = "Lesson $lessonIndex"

        val cards = (1..cardsPerLesson).map { cardIndex ->
            createCard(
                lessonId = lessonId,
                cardIndex = cardIndex,
                isMainPool = cardIndex <= Lesson.MAIN_POOL_SIZE
            )
        }

        val drillCards = (1..drillCardsPerLesson.coerceAtLeast(0)).map { cardIndex ->
            createDrillCard(
                lessonId = lessonId,
                cardIndex = cardIndex
            )
        }

        return LessonTestData(
            lessonId = lessonId,
            title = title,
            cards = cards,
            drillCards = drillCards
        )
    }

    /**
     * Create a deterministic sentence card.
     *
     * IDs are deterministic for reproducible tests.
     */
    fun createCard(
        lessonId: String,
        cardIndex: Int,
        isMainPool: Boolean = true,
        tense: String? = null
    ): SentenceCard {
        val cardId = "${lessonId}_card_$cardIndex"
        val poolPrefix = if (isMainPool) "main" else "reserve"
        val promptRu = "Тестовый перевод $cardIndex ($poolPrefix)"
        val answer = "Test answer $cardIndex"

        return SentenceCard(
            id = cardId,
            promptRu = promptRu,
            acceptedAnswers = listOf(answer, "Alternative $cardIndex"),
            tense = tense
        )
    }

    /**
     * Create a drill card for lesson drills.
     */
    fun createDrillCard(
        lessonId: String,
        cardIndex: Int,
        tense: String? = null
    ): SentenceCard {
        val cardId = "${lessonId}_drill_$cardIndex"
        val promptRu = "Дрилл карточка $cardIndex"
        val answer = "Drill answer $cardIndex"

        return SentenceCard(
            id = cardId,
            promptRu = promptRu,
            acceptedAnswers = listOf(answer),
            tense = tense
        )
    }

    /**
     * Create verb drill test data.
     */
    fun createVerbDrillData(
        packId: String,
        tenses: List<String>,
        groups: List<String>,
        cardsPerCombo: Int
    ): VerbDrillCourseData {
        val tenseGroups = mutableListOf<VerbDrillCourseData.TenseGroupData>()

        for (tense in tenses) {
            for (group in groups) {
                val cards = (1..cardsPerCombo).map { cardIndex ->
                    createVerbCard(
                        packId = packId,
                        tense = tense,
                        group = group,
                        cardIndex = cardIndex
                    )
                }
                tenseGroups.add(VerbDrillCourseData.TenseGroupData(tense, group, cards))
            }
        }

        return VerbDrillCourseData(tenseGroups)
    }

    /**
     * Create a deterministic verb drill card.
     */
    fun createVerbCard(
        packId: String,
        tense: String,
        group: String,
        cardIndex: Int
    ): VerbDrillCard {
        val cardId = "${packId}_verb_${tense}_${group}_$cardIndex"
        val promptRu = "Я спрягаю (verb) $tense $group $cardIndex"
        val answer = "io conjugate $cardIndex"
        val verb = "verb_${group.lowercase()}"

        return VerbDrillCard(
            id = cardId,
            promptRu = promptRu,
            answer = answer,
            verb = verb,
            tense = tense,
            group = group,
            rank = cardIndex
        )
    }

    /**
     * Create vocab drill test data.
     */
    fun createVocabDrillData(
        packId: String,
        posList: List<String>,
        wordsPerPos: Int
    ): VocabDrillCourseData {
        val wordsByPos = posList.associateWith { pos ->
            (1..wordsPerPos).map { wordIndex ->
                createVocabWord(
                    packId = packId,
                    pos = pos,
                    wordIndex = wordIndex
                )
            }
        }

        return VocabDrillCourseData(wordsByPos)
    }

    /**
     * Create a deterministic vocab word.
     */
    fun createVocabWord(
        packId: String,
        pos: String,
        wordIndex: Int
    ): VocabWord {
        val wordId = "${pos}_${packId}_$wordIndex"
        val word = when (pos) {
            "nouns" -> "parola$wordIndex"
            "verbs" -> "verbere$wordIndex"
            "adjectives" -> "aggettivo$wordIndex"
            "adverbs" -> "avverbio$wordIndex"
            else -> "word$wordIndex"
        }
        val meaningRu = "слово$wordIndex"

        return VocabWord(
            id = wordId,
            word = word,
            pos = pos,
            rank = wordIndex,
            meaningRu = meaningRu,
            collocations = listOf("collocation $wordIndex"),
            forms = if (pos == "adjectives") {
                mapOf("msg" to "${word}o", "fsg" to "${word}a")
            } else emptyMap()
        )
    }

    /**
     * Create a fresh mastery state for testing.
     */
    fun createFreshMastery(
        lessonId: String,
        languageId: String
    ): LessonMasteryState = LessonMasteryState(
        lessonId = LessonId(lessonId),
        languageId = LanguageId(languageId),
        uniqueCardShows = 0,
        totalCardShows = 0,
        lastShowDateMs = 0L,
        intervalStepIndex = 0,
        completedAtMs = null,
        shownCardIds = emptySet(),
        cardEncounterCounts = emptyMap()
    )

    /**
     * Create a completed mastery state for testing.
     */
    fun createCompletedMastery(
        lessonId: String,
        languageId: String,
        uniqueShows: Int = Lesson.MAIN_POOL_SIZE
    ): LessonMasteryState = LessonMasteryState(
        lessonId = LessonId(lessonId),
        languageId = LanguageId(languageId),
        uniqueCardShows = uniqueShows,
        totalCardShows = uniqueShows + 20,
        lastShowDateMs = System.currentTimeMillis(),
        intervalStepIndex = 3,
        completedAtMs = System.currentTimeMillis() - 86_400_000L,
        shownCardIds = (1..uniqueShows).map { "${lessonId}_card_$it" }.toSet(),
        cardEncounterCounts = (1..uniqueShows).associate { "${lessonId}_card_$it" to 1 }
    )
}
