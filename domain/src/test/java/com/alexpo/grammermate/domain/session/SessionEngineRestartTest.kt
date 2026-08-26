package com.alexpo.grammermate.domain.session

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
 * Restart-семантика урока (Фаза 3 slice 2 плана стабилизации 2026-08-26).
 *
 * [SessionEngine.restartLessonSession] сбрасывает ТОЛЬКО контекст сессии:
 * пул пересобирается, курсор/счётчики/shown-set обнуляются, PK сессии тот же.
 * Mastery (показы/завершённость — зона MasteryRepository, не сессии) не
 * участвует и потому не может быть потерян рестартом.
 */
class SessionEngineRestartTest {

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
            frequencyRank = idx,
        )
    }

    private fun buildEngine(
        content: FakeContentRepository,
        sessionRepo: FakeSessionRepository = FakeSessionRepository(),
    ): Pair<SessionEngine, FakeSessionRepository> =
        SessionEngine(
            sessionRepository = sessionRepo,
            contentRepository = content,
            userContentRepository = FakeUserContentRepository(),
            clock = { 1_700_000_000_000L },
        ) to sessionRepo

    @Test
    fun `restart clears session context and rebuilds same pool`() = runTest {
        val content = FakeContentRepository().apply { setCardsForLesson(lessonId, lessonCards(3)) }
        val (engine, _) = buildEngine(content)

        engine.startLessonSession(packId, lessonId, sessionSize = 10)
        engine.submitAnswer(sessionId, CardId("card_0"), isCorrect = true, inputMode = InputMode.KEYBOARD)
        engine.submitAnswer(sessionId, CardId("card_1"), isCorrect = false, inputMode = InputMode.KEYBOARD)

        val restarted = engine.restartLessonSession(packId, lessonId, sessionSize = 10)

        assertThat(restarted.status).isEqualTo(SessionStatus.ACTIVE)
        assertThat(restarted.correctCount).isEqualTo(0)
        assertThat(restarted.incorrectCount).isEqualTo(0)
        assertThat(restarted.shownCardIds).isEmpty()
        assertThat(restarted.currentCardId?.value).isEqualTo("card_0")
        assertThat(restarted.poolCardIds.map { it.value })
            .containsExactly("card_0", "card_1", "card_2")
            .inOrder()
    }

    @Test
    fun `resume after restart returns the fresh snapshot`() = runTest {
        val content = FakeContentRepository().apply { setCardsForLesson(lessonId, lessonCards(3)) }
        val (engine, sessionRepo) = buildEngine(content)

        engine.startLessonSession(packId, lessonId, sessionSize = 10)
        engine.submitAnswer(sessionId, CardId("card_0"), isCorrect = true, inputMode = InputMode.KEYBOARD)
        engine.restartLessonSession(packId, lessonId, sessionSize = 10)

        val resumed = engine.resumeSession(sessionId)
        assertThat(resumed).isNotNull()
        assertThat(resumed!!.correctCount).isEqualTo(0)
        assertThat(resumed.shownCardIds).isEmpty()
        // Под тем же PK — ровно свежая сессия (старой строки нет).
        assertThat(sessionRepo.loadSession(sessionId)!!.revision)
            .isEqualTo(resumed.revision)
    }

    @Test
    fun `restart of absent session simply starts fresh`() = runTest {
        val content = FakeContentRepository().apply { setCardsForLesson(lessonId, lessonCards(2)) }
        val (engine, _) = buildEngine(content)

        val snapshot = engine.restartLessonSession(packId, lessonId, sessionSize = 10)

        assertThat(snapshot.status).isEqualTo(SessionStatus.ACTIVE)
        assertThat(snapshot.poolCardIds.map { it.value })
            .containsExactly("card_0", "card_1")
            .inOrder()
    }
}
