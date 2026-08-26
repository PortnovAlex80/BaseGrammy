package com.alexpo.grammermate.domain.daily

import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.CardType
import com.alexpo.grammermate.domain.model.DailyBlockType
import com.alexpo.grammermate.domain.model.DailyCursor
import com.alexpo.grammermate.domain.model.DailyTask
import com.alexpo.grammermate.domain.model.InputMode
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.VerbDrillCard
import com.alexpo.grammermate.domain.model.VocabDrillDirection
import com.alexpo.grammermate.domain.model.VocabWord
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Тесты композитора дневной нормы (gap #2, AC-16).
 *
 * AC-16 Given/When/Then:
 *  - Given: :domain содержит sealed DailyTask + DailyBlockType + DailyCursor.
 *  - When: для {TRANSLATE:5, VOCAB:3, VERBS:2} и текущего DailyCursor вызывается
 *    композитор (pure-функция от (cursor, content, settings)).
 *  - Then:
 *    1. упорядоченный List<DailyTask> длиной 10 с корректным blockType;
 *    2. стабильный id у каждой задачи;
 *    3. подтипы переиспользуют Card/VocabWord/InputMode/VocabDrillDirection;
 *    4. pure Kotlin (0 import android — covered структурно модулем Kotlin/JVM).
 */
class DailyTaskComposerTest {

    private val packId = PackId("pack-it")
    private val lessonId = LessonId("lesson-1")

    /** Курсор дневной нормы с нулевыми смещениями. */
    private val cursor = DailyCursor(
        packId = packId,
        sentenceOffset = 0,
        currentLessonIndex = 0,
        verbOffset = 0,
        firstSessionDate = null,
        firstSessionSentenceCardIds = emptyList(),
        firstSessionVerbCardIds = emptyList(),
        firstSessionLessonId = null,
    )

    /** Конфигурация дня из AC-16: 5 TRANSLATE + 3 VOCAB + 2 VERBS = 10. */
    private val settings = DailySettings(
        blockConfig = linkedMapOf(
            DailyBlockType.TRANSLATE to 5,
            DailyBlockType.VOCAB to 3,
            DailyBlockType.VERBS to 2,
        ),
        inputMode = InputMode.KEYBOARD,
        vocabDirection = VocabDrillDirection.IT_TO_RU,
    )

    private fun sentenceCard(suffix: String): Card = Card(
        id = CardId("sentence-$suffix"),
        packId = packId,
        lessonId = lessonId,
        ord = 0,
        type = CardType.SENTENCE,
        promptRu = "перевод $suffix",
        acceptedAnswers = listOf("answer-$suffix"),
        tense = null,
        verb = null,
        verbGroup = null,
        person = null,
        frequencyRank = null,
    )

    private fun verbCard(suffix: String): VerbDrillCard = VerbDrillCard(
        id = "verb-$suffix",
        promptRu = "спрягай $suffix",
        answer = "verb-answer-$suffix",
    )

    private fun vocabWord(suffix: String): VocabWord = VocabWord(
        id = "nouns_$suffix",
        word = "parola-$suffix",
        pos = "nouns",
        rank = suffix.toIntOrNull() ?: 0,
        meaningRu = "слово-$suffix",
    )

    /** Контент с запасом под конфигурацию {5,3,2}. */
    private val content = DailyContent(
        sentenceCards = List(8) { sentenceCard(it.toString()) },
        verbCards = List(5) { verbCard(it.toString()) },
        vocabWords = List(6) { vocabWord(it.toString()) },
    )

    @Test
    fun compose_returnsOrderedListOfTenForTranslateVocabVerbsConfig() {
        val tasks = DailyTaskComposer.compose(cursor, content, settings)

        // Then-1: длина 10.
        assertThat(tasks).hasSize(10)
    }

    @Test
    fun compose_eachTaskHasCorrectBlockTypeInConfigOrder() {
        val tasks = DailyTaskComposer.compose(cursor, content, settings)

        // Then-1: корректный blockType у каждой задачи + порядок блоков из конфига.
        val expectedOrder = listOf(
            DailyBlockType.TRANSLATE, DailyBlockType.TRANSLATE, DailyBlockType.TRANSLATE,
            DailyBlockType.TRANSLATE, DailyBlockType.TRANSLATE,
            DailyBlockType.VOCAB, DailyBlockType.VOCAB, DailyBlockType.VOCAB,
            DailyBlockType.VERBS, DailyBlockType.VERBS,
        )
        assertThat(tasks.map { it.blockType })
            .isEqualTo(expectedOrder)
    }

    @Test
    fun compose_translatesAreFirstFiveVocabNextThreeVerbsLastTwo() {
        val tasks = DailyTaskComposer.compose(cursor, content, settings)

        // Подтипы соответствуют позициям блоков (Then-1 + Then-3: переиспользуют модели).
        tasks.subList(0, 5).forEach {
            assertThat(it).isInstanceOf(DailyTask.TranslateSentence::class.java)
        }
        tasks.subList(5, 8).forEach {
            assertThat(it).isInstanceOf(DailyTask.VocabFlashcard::class.java)
        }
        tasks.subList(8, 10).forEach {
            assertThat(it).isInstanceOf(DailyTask.ConjugateVerb::class.java)
        }
    }

    @Test
    fun compose_eachTaskCarriesStableId() {
        val tasks = DailyTaskComposer.compose(cursor, content, settings)

        // Then-2: стабильный id у каждой задачи + все уникальны (UI-ключи).
        val ids = tasks.map { it.id }
        assertThat(ids).containsNoDuplicates()
        // Формат id: "daily:<block>:<contentId>" — id следует за КОНТЕНТОМ,
        // а не индексом (фикс аудита M-12: коллизии между днями).
        assertThat(ids).containsExactly(
            "daily:translate:sentence-0", "daily:translate:sentence-1",
            "daily:translate:sentence-2", "daily:translate:sentence-3",
            "daily:translate:sentence-4",
            "daily:vocab:nouns_0", "daily:vocab:nouns_1", "daily:vocab:nouns_2",
            "daily:verbs:verb-0", "daily:verbs:verb-1",
        ).inOrder()
    }

    /** Фикс аудита M-12: id другого ДНЯ (другой контент) отличаются. */
    @Test
    fun compose_idsFollowContent_notDayIndex() {
        val day1 = DailyTaskComposer.compose(cursor, content, settings)
        val nextDay = cursor.copy(sentenceOffset = 5, verbOffset = 2, vocabOffset = 3)
        val day2 = DailyTaskComposer.compose(nextDay, content, settings)

        assertThat(day2.map { it.id }).containsExactly(
            "daily:translate:sentence-5", "daily:translate:sentence-6",
            "daily:translate:sentence-7", // пул 8: 5+4 за границей → только 3
            "daily:vocab:nouns_3", "daily:vocab:nouns_4", "daily:vocab:nouns_5",
            "daily:verbs:verb-2", "daily:verbs:verb-3",
        ).inOrder()
        // Ни один id дня 2 не совпадает с днём 1 при том же формате.
        assertThat(day1.map { it.id }.toSet() intersect day2.map { it.id }.toSet()).isEmpty()
    }

    /** Фикс аудита M-13: vocab-блок идёт по курсору, не с нуля каждый день. */
    @Test
    fun compose_vocabRespectsCursorOffset() {
        val advanced = cursor.copy(vocabOffset = 4)

        val tasks = DailyTaskComposer.compose(advanced, content, settings)

        val vocab = tasks.filterIsInstance<DailyTask.VocabFlashcard>()
        assertThat(vocab.map { it.word.id }).containsExactly("nouns_4", "nouns_5").inOrder()
    }

    @Test
    fun compose_stableIdsAreDeterministicAcrossCalls() {
        // Then-2: одинаковые входы → одинаковые id (стабильность для UI-ключей).
        val first = DailyTaskComposer.compose(cursor, content, settings).map { it.id }
        val second = DailyTaskComposer.compose(cursor, content, settings).map { it.id }

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun compose_translateSubtypeReusesCardAndInputMode() {
        val tasks = DailyTaskComposer.compose(cursor, content, settings)

        val first = tasks[0] as DailyTask.TranslateSentence
        // Then-3: переиспользует Card (не дублирует модель) + inputMode из настроек.
        assertThat(first.card).isInstanceOf(Card::class.java)
        assertThat(first.card.id).isEqualTo(CardId("sentence-0"))
        assertThat(first.inputMode).isEqualTo(InputMode.KEYBOARD)
        assertThat(first.blockType).isEqualTo(DailyBlockType.TRANSLATE)
    }

    @Test
    fun compose_vocabSubtypeReusesVocabWordAndVocabDrillDirection() {
        val tasks = DailyTaskComposer.compose(cursor, content, settings)

        val vocabTask = tasks[5] as DailyTask.VocabFlashcard
        // Then-3: переиспользует VocabWord + VocabDrillDirection.
        assertThat(vocabTask.word).isInstanceOf(VocabWord::class.java)
        assertThat(vocabTask.word.id).isEqualTo("nouns_0")
        assertThat(vocabTask.direction).isEqualTo(VocabDrillDirection.IT_TO_RU)
        assertThat(vocabTask.blockType).isEqualTo(DailyBlockType.VOCAB)
    }

    @Test
    fun compose_verbsSubtypeReusesVerbDrillCardAndInputMode() {
        val tasks = DailyTaskComposer.compose(cursor, content, settings)

        val verbsTask = tasks[8] as DailyTask.ConjugateVerb
        // Then-3: переиспользует VerbDrillCard + inputMode.
        assertThat(verbsTask.card).isInstanceOf(VerbDrillCard::class.java)
        assertThat(verbsTask.card.id).isEqualTo("verb-0")
        assertThat(verbsTask.inputMode).isEqualTo(InputMode.KEYBOARD)
        assertThat(verbsTask.blockType).isEqualTo(DailyBlockType.VERBS)
    }

    @Test
    fun compose_respectsSentenceOffsetFromCursor() {
        // DailyCursor.sentenceOffset должен сдвигать срез предложений.
        val offsetCursor = cursor.copy(sentenceOffset = 3)
        val tasks = DailyTaskComposer.compose(offsetCursor, content, settings)

        val first = tasks[0] as DailyTask.TranslateSentence
        // Карточка-предложение берётся с позиции offset=3.
        assertThat(first.card.id).isEqualTo(CardId("sentence-3"))
    }

    @Test
    fun compose_respectsVerbOffsetFromCursor() {
        // DailyCursor.verbOffset должен сдвигать срез глаголов.
        val offsetCursor = cursor.copy(verbOffset = 2)
        val tasks = DailyTaskComposer.compose(offsetCursor, content, settings)

        val verbsTask = tasks[8] as DailyTask.ConjugateVerb
        // Карточка-глагол берётся с позиции offset=2.
        assertThat(verbsTask.card.id).isEqualTo("verb-2")
    }

    @Test
    fun compose_skipsExhaustedSentencePoolGracefully() {
        // Pure-композитор не падает, если пул предложений меньше конфигурации:
        // отдаёт столько, сколько есть.
        val sparse = content.copy(sentenceCards = List(2) { sentenceCard(it.toString()) })
        val tasks = DailyTaskComposer.compose(cursor, sparse, settings)

        // 2 TRANSLATE (вместо 5) + 3 VOCAB + 2 VERBS = 7.
        assertThat(tasks).hasSize(7)
        assertThat(tasks.count { it.blockType == DailyBlockType.TRANSLATE }).isEqualTo(2)
        assertThat(tasks.count { it.blockType == DailyBlockType.VOCAB }).isEqualTo(3)
        assertThat(tasks.count { it.blockType == DailyBlockType.VERBS }).isEqualTo(2)
    }

    @Test
    fun compose_emptyConfigProducesEmptyList() {
        val empty = DailySettings(
            blockConfig = emptyMap(),
            inputMode = InputMode.KEYBOARD,
            vocabDirection = VocabDrillDirection.IT_TO_RU,
        )
        val tasks = DailyTaskComposer.compose(cursor, content, empty)

        assertThat(tasks).isEmpty()
    }
}
