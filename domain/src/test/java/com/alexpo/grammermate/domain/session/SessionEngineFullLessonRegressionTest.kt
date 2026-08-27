package com.alexpo.grammermate.domain.session

import com.alexpo.grammermate.domain.TrainingConfig
import com.alexpo.grammermate.domain.model.Card
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.CardType
import com.alexpo.grammermate.domain.model.InputMode
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.model.SessionStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Регрессия аудита 2026-08-26 «урок обрывается после первого под-урока,
 * остаток карточек теряется»: урок 25 карт при sessionSize=10 обязан
 * пройти ВСЕ 25 карт тремя под-уроками (10+10+5) и завершиться только
 * после последней карты последнего под-урока.
 */
class SessionEngineFullLessonRegressionTest {

    private val packId = PackId("pack")
    private val lessonId = LessonId("lesson")
    private val sessionId = SessionId.forLesson(packId, lessonId)

    private fun lessonCards(n: Int): List<Card> = (0 until n).map { idx ->
        Card(
            id = CardId("card_$idx"),
            packId = packId,
            lessonId = lessonId,
            ord = idx,
            type = CardType.SENTENCE,
            promptRu = "prompt $idx",
            acceptedAnswers = listOf("answer $idx"),
            tense = null, verb = null, verbGroup = null, person = null,
            frequencyRank = null,
        )
    }

    private fun engine(cardsCount: Int): SessionEngine {
        val content = FakeContentRepository().apply { setCardsForLesson(lessonId, lessonCards(cardsCount)) }
        return SessionEngine(
            sessionRepository = FakeSessionRepository(clock = { 1L }),
            contentRepository = content,
            userContentRepository = FakeUserContentRepository(),
            clock = { 1L },
        )
    }

    @Test
    fun `lesson of 25 cards passes all subLessons and completes only at card 25`() = runTest {
        val engine = engine(cardsCount = 25)
        val size = TrainingConfig.SUB_LESSON_SIZE_DEFAULT // 10

        val first = engine.startLessonSession(packId, lessonId, sessionSize = size)
        assertThat(first.poolCardIds).hasSize(10)

        var snapshot = first
        var answeredTotal = 0
        // Под-урок 1: 10 карт → next на последней даёт под-урок 2, НЕ COMPLETED.
        repeat(size - 1) {
            snapshot = engine.submitAnswer(sessionId, snapshot.currentCardId!!, true, InputMode.KEYBOARD)
            snapshot = engine.nextCardOrComplete(sessionId)
            assertThat(snapshot.status).isEqualTo(SessionStatus.ACTIVE)
        }
        snapshot = engine.submitAnswer(sessionId, snapshot.currentCardId!!, true, InputMode.KEYBOARD)
        snapshot = engine.nextCardOrComplete(sessionId)
        assertThat(snapshot.status).isEqualTo(SessionStatus.ACTIVE)
        assertThat(snapshot.poolCardIds).hasSize(10)
        assertThat(snapshot.completedSubLessonCount).isEqualTo(1)
        assertThat(snapshot.poolCardIds.first().value).isEqualTo("card_10")
        answeredTotal += size

        // Под-урок 2: карты 10..19 → переход к под-уроку 3 (5 карт).
        repeat(size - 1) {
            snapshot = engine.submitAnswer(sessionId, snapshot.currentCardId!!, true, InputMode.KEYBOARD)
            snapshot = engine.nextCardOrComplete(sessionId)
        }
        snapshot = engine.submitAnswer(sessionId, snapshot.currentCardId!!, true, InputMode.KEYBOARD)
        snapshot = engine.nextCardOrComplete(sessionId)
        assertThat(snapshot.status).isEqualTo(SessionStatus.ACTIVE)
        assertThat(snapshot.poolCardIds.map { it.value })
            .containsExactly("card_20", "card_21", "card_22", "card_23", "card_24")
            .inOrder()
        answeredTotal += size

        // Под-урок 3 (хвост 5 карт): на card_24 — COMPLETED, всего 25 ответов.
        repeat(4) {
            snapshot = engine.submitAnswer(sessionId, snapshot.currentCardId!!, true, InputMode.KEYBOARD)
            snapshot = engine.nextCardOrComplete(sessionId)
            assertThat(snapshot.status).isEqualTo(SessionStatus.ACTIVE)
        }
        snapshot = engine.submitAnswer(sessionId, snapshot.currentCardId!!, true, InputMode.KEYBOARD)
        snapshot = engine.nextCardOrComplete(sessionId)
        assertThat(snapshot.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(snapshot.correctCount).isEqualTo(25)
    }

    @Test
    fun `short lesson within one subLesson completes as before`() = runTest {
        val engine = engine(cardsCount = 3)
        engine.startLessonSession(packId, lessonId, sessionSize = 10)

        var snapshot: com.alexpo.grammermate.domain.model.SessionSnapshot? = null
        repeat(3) {
            val cur = engine.resumeSession(sessionId)!!
            engine.submitAnswer(sessionId, cur.currentCardId!!, true, InputMode.KEYBOARD)
            snapshot = engine.nextCardOrComplete(sessionId)
        }

        assertThat(snapshot!!.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(snapshot!!.correctCount).isEqualTo(3)
    }
}
