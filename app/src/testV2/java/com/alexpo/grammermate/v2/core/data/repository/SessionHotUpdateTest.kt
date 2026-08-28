package com.alexpo.grammermate.v2.core.data.repository

import androidx.test.core.app.ApplicationProvider
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.model.TrainingMode
import com.alexpo.grammermate.v2.core.data.local.GrammarMateDatabase
import com.alexpo.grammermate.v2.core.data.local.TrainingDbFixture
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Hot updates обычного Submit/Next (Фаза 2 плана стабилизации 2026-08-26,
 * guardrail: «Full pool rewrite на обычный Submit/Next = 0»).
 *
 * [SessionDao.saveSnapshot] применяет diff: пул переписывается только при
 * фактическом изменении; shown — вставка только новых строк, исходные
 * `shownAtMs` сохраняются. Свидетель diff-пути (вместо подсчёта SQL):
 *  - после count-only save `shownAtMs` существующих shown-строк НЕ меняется
 *    (старый delete+reinsert перештамповывал бы их временем persist);
 *  - пул (ord/cardId) остаётся побитово тем же.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionHotUpdateTest {

    private lateinit var db: GrammarMateDatabase
    private lateinit var repository: SessionRepositoryImpl

    private val packId = PackId(TrainingDbFixture.PACK_ID)
    private val lessonId = LessonId(TrainingDbFixture.LESSON_ID)
    private val sessionId = SessionId.forLesson(packId, lessonId)
    private val pool = TrainingDbFixture.CARD_IDS.map(::CardId)

    @Before
    fun setUp() = runTest {
        db = TrainingDbFixture.inMemory(ApplicationProvider.getApplicationContext())
        TrainingDbFixture.seedTrainingContent(db)
        repository = SessionRepositoryImpl(db.sessionDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `count-only save preserves shown timestamps and pool`() = runTest {
        val started = repository.getOrCreateSession(
            sessionId, packId, lessonId, TrainingMode.LESSON, pool,
        )
        // Ответ по первой карте: shown-строка создаётся в T1.
        val submitted = started.copy(
            correctCount = 1,
            shownCardIds = setOf(pool[0]),
            currentCardId = pool[0],
            revision = started.revision + 1,
        )
        repository.saveSession(submitted)
        val shownBefore = db.sessionDao().getShownCards(sessionId.value).single()
        val poolBefore = db.sessionDao().getSessionCards(sessionId.value)

        // Обычный Next: счётчики не меняются, курсор идёт вперёд — снимок
        // отличается ТОЛЬКО currentCardId. System.currentTimeMillis должен
        // уйти от T1, чтобы перештамповка была бы обнаружима.
        delay(50)
        val advanced = submitted.copy(
            currentCardId = pool[1],
            revision = submitted.revision + 1,
        )
        repository.saveSession(advanced)

        val shownAfter = db.sessionDao().getShownCards(sessionId.value).single()
        val poolAfter = db.sessionDao().getSessionCards(sessionId.value)

        // Diff-путь: исходный shownAtMs сохранён, пул не тронут.
        assertThat(shownAfter.shownAtMs).isEqualTo(shownBefore.shownAtMs)
        assertThat(shownAfter.cardId).isEqualTo(shownBefore.cardId)
        assertThat(poolAfter).isEqualTo(poolBefore)

        // Состояние всё же зафиксировано: курсор/pool домена обновились.
        val loaded = repository.loadSession(sessionId)!!
        assertThat(loaded.currentCardId).isEqualTo(pool[1])
        assertThat(loaded.revision).isEqualTo(2L)
    }

    @Test
    fun `pool change triggers full rewrite with correct order`() = runTest {
        val started = repository.getOrCreateSession(
            sessionId, packId, lessonId, TrainingMode.LESSON, pool,
        )
        // Скрытие карты: пул реально меняется → replacePool выполняется.
        val hidden = started.copy(
            poolCardIds = pool - pool[1],
            currentCardId = pool[0],
            revision = started.revision + 1,
        )
        repository.saveSession(hidden)

        val loaded = repository.loadSession(sessionId)!!
        assertThat(loaded.poolCardIds).containsExactly(pool[0], pool[2]).inOrder()
    }

    @Test
    fun `shown removal deletes only removed rows`() = runTest {
        val started = repository.getOrCreateSession(
            sessionId, packId, lessonId, TrainingMode.LESSON, pool,
        )
        val withShown = started.copy(
            shownCardIds = setOf(pool[0], pool[1]),
            revision = started.revision + 1,
        )
        repository.saveSession(withShown)

        // Hide card[1]: shown-множество уменьшается — удаляется ровно одна строка.
        val reduced = withShown.copy(
            shownCardIds = setOf(pool[0]),
            revision = withShown.revision + 1,
        )
        repository.saveSession(reduced)

        val shownRows = db.sessionDao().getShownCards(sessionId.value)
        assertThat(shownRows.map { it.cardId }).containsExactly(pool[0].value)
        val loaded = repository.loadSession(sessionId)!!
        assertThat(loaded.shownCardIds).containsExactly(pool[0])
    }
}
