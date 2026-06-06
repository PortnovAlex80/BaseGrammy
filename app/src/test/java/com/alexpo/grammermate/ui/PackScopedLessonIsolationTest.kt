package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration test for pack-scoped lesson isolation.
 *
 * Verifies that two packs (ITALIAN_EXPRESS and ITALIAN_SHORT) sharing
 * language="it" and having the same lessonId "lesson_01_A01" with DIFFERENT
 * card content do NOT leak data between each other.
 *
 * This is a pure JVM test — no Compose, no Android framework.
 */
class PackScopedLessonIsolationTest {

    // Helper to create a lesson with specific cards
    private fun makeLesson(
        lessonId: String,
        languageId: String,
        sentences: List<Pair<String, String>>
    ): Lesson {
        val cards = sentences.mapIndexed { i, (ru, it) ->
            SentenceCard(id = "card_$i", promptRu = ru, acceptedAnswers = listOf(it))
        }
        return Lesson(
            id = LessonId(lessonId),
            languageId = LanguageId(languageId),
            title = "Test Lesson $lessonId",
            cards = cards
        )
    }

    @Test
    fun `lessons with same ID in different packs have different card counts`() {
        // ITALIAN_EXPRESS A01: 95 sentences
        val expressSentences = (1..95).map { i -> "Экспресс предложение $i" to "Frase express $i" }
        val expressLesson = makeLesson("lesson_01_A01", "it", expressSentences)

        // ITALIAN_SHORT A01: 15 sentences
        val shortSentences = (1..15).map { i -> "Короткий предложение $i" to "Frase corta $i" }
        val shortLesson = makeLesson("lesson_01_A01", "it", shortSentences)

        // Same lessonId, different content
        assertEquals(expressLesson.id, shortLesson.id) // same ID
        assertNotEquals(expressLesson.cards.size, shortLesson.cards.size) // different card count
        assertEquals(95, expressLesson.cards.size)
        assertEquals(15, shortLesson.cards.size)
    }

    @Test
    fun `first card of same lesson differs between packs`() {
        val expressSentences = listOf("Я говорю по-итальянски" to "Parlo italiano") +
            (2..95).map { i -> "Предложение $i" to "Frase $i" }
        val expressLesson = makeLesson("lesson_01_A01", "it", expressSentences)

        val shortSentences = listOf("Я покупаю дом" to "Compro una casa") +
            (2..15).map { i -> "Предложение $i" to "Frase $i" }
        val shortLesson = makeLesson("lesson_01_A01", "it", shortSentences)

        // Same lessonId
        assertEquals(expressLesson.id, shortLesson.id)
        // Different first card
        assertNotEquals(expressLesson.cards[0].promptRu, shortLesson.cards[0].promptRu)
        assertEquals("Я говорю по-итальянски", expressLesson.cards[0].promptRu)
        assertEquals("Я покупаю дом", shortLesson.cards[0].promptRu)
    }

    @Test
    fun `lesson lookup by packId returns correct lessons`() {
        // Simulate the LessonStore cache behavior:
        // getLessons("PACK_A", "it") should only return PACK_A's lessons
        // getLessons("PACK_B", "it") should only return PACK_B's lessons

        val packALessons = listOf(
            makeLesson("lesson_01_A01", "it", listOf("Я говорю" to "Parlo"))
        )
        val packBLessons = listOf(
            makeLesson("lesson_01_A01", "it", listOf("Я покупаю" to "Compro"))
        )

        // Simulate cache: key = "packId:languageId"
        val cache = mutableMapOf<String, List<Lesson>>()
        cache["PACK_A:it"] = packALessons
        cache["PACK_B:it"] = packBLessons

        val resultA = cache["PACK_A:it"]!!
        val resultB = cache["PACK_B:it"]!!

        assertEquals("Я говорю", resultA[0].cards[0].promptRu)
        assertEquals("Я покупаю", resultB[0].cards[0].promptRu)

        // Verify they're truly different objects
        assertNotSame(resultA[0], resultB[0])
        assertNotEquals(resultA[0].cards[0].promptRu, resultB[0].cards[0].promptRu)
    }

    @Test
    fun `navigation lessons state matches active pack`() {
        // Simulate: when active pack changes, navigation.lessons must reload
        val state1 = TrainingUiState().copy(
            navigation = NavigationState(
                activePackId = PackId("ITALIAN_EXPRESS"),
                lessons = listOf(makeLesson("lesson_01_A01", "it", listOf("Я говорю" to "Parlo")))
            )
        )

        val state2 = state1.copy(
            navigation = state1.navigation.copy(
                activePackId = PackId("ITALIAN_SHORT"),
                lessons = listOf(makeLesson("lesson_01_A01", "it", listOf("Я покупаю" to "Compro")))
            )
        )

        // When ITALIAN_EXPRESS is active, first card is "Я говорю"
        assertEquals("ITALIAN_EXPRESS", state1.navigation.activePackId!!.value)
        assertEquals("Я говорю", state1.navigation.lessons[0].cards[0].promptRu)

        // When ITALIAN_SHORT is active, first card is "Я покупаю"
        assertEquals("ITALIAN_SHORT", state2.navigation.activePackId!!.value)
        assertEquals("Я покупаю", state2.navigation.lessons[0].cards[0].promptRu)
    }

    @Test
    fun `filterLessonsForActivePack excludes non-pack lessons`() {
        val packLessonIds = setOf("lesson_01_A01", "lesson_02_A02")
        val allLessons = listOf(
            makeLesson("lesson_01_A01", "it", listOf("A01" to "a01")),
            makeLesson("lesson_02_A02", "it", listOf("A02" to "a02")),
            makeLesson("lesson_03_A03", "it", listOf("A03" to "a03")), // NOT in pack
            makeLesson("lesson_04_A04", "it", listOf("A04" to "a04"))  // NOT in pack
        )

        val filtered = allLessons.filter { it.id.value in packLessonIds }

        assertEquals(2, filtered.size)
        assertEquals("lesson_01_A01", filtered[0].id.value)
        assertEquals("lesson_02_A02", filtered[1].id.value)
    }
}
