package com.alexpo.grammermate.v2.core.data.repository

import androidx.test.core.app.ApplicationProvider
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.model.TrainingMode
import com.alexpo.grammermate.v2.core.data.local.GrammarMateDatabase
import com.alexpo.grammermate.v2.core.data.local.TrainingDbFixture
import com.alexpo.grammermate.v2.core.data.local.entity.SessionCardEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room-контрактные тесты [SessionRepositoryImpl] против доменного порта
 * `SessionRepository` (Фаза 0 плана стабилизации 2026-08-26: «Repository
 * contract suite проходит одинаково для fake и in-memory Room»).
 *
 * Бывший RED-якорь P0 «сессия сохраняется с пустым pool» снят с `@Ignore`
 * changeset'ом Фазы 1: порт `getOrCreateSession` получил ОБЯЗАТЕЛЬНЫЙ
 * `poolCardIds` (ADR-001 — пул строит SessionEngine, data-слой персистит
 * переданное), т.е. «создать сессию урока без пула» больше не компилируется.
 * Ниже — позитивная фиксация нового контракта.
 *
 * RED-якорь P1 «молчаливая подмена currentCard» остаётся под `@Ignore`
 * до Фазы 2 (уравнивание recovery-семантики fake/Room).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.13: max supported SDK
class SessionRepositoryContractTest {

    private lateinit var db: GrammarMateDatabase
    private lateinit var repository: SessionRepositoryImpl

    private val packId = PackId(TrainingDbFixture.PACK_ID)
    private val lessonId = LessonId(TrainingDbFixture.LESSON_ID)
    private val sessionId = SessionId.forLesson(packId, lessonId)

    @Before
    fun setUp() {
        db = TrainingDbFixture.inMemory(ApplicationProvider.getApplicationContext())
        repository = SessionRepositoryImpl(db.sessionDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Новый контракт порта (Фаза 1, вместо RED-якоря «пустой пул»): пул —
     * обязательный аргумент, сохраняется в одной транзакции целиком и в
     * порядке передачи; `currentCardId` новой сессии — первая карта пула.
     */
    @Test
    fun getOrCreateSession_persistsRequiredPoolAndFirstCardAsCurrent() = runTest {
        TrainingDbFixture.seedTrainingContent(db)

        val snapshot = repository.getOrCreateSession(
            sessionId = sessionId,
            packId = packId,
            lessonId = lessonId,
            mode = TrainingMode.LESSON,
            poolCardIds = TrainingDbFixture.CARD_IDS.map(::CardId),
        )

        assertThat(snapshot.poolCardIds.map { it.value })
            .containsExactlyElementsIn(TrainingDbFixture.CARD_IDS)
            .inOrder()
        assertThat(snapshot.currentCardId?.value).isEqualTo(TrainingDbFixture.CARD_IDS.first())
        assertThat(snapshot.shownCardIds).isEmpty()
    }

    /**
     * P1-дефект «молчиливая подмена currentCardId» (план Фаза 2: «никакой
     * молчаливой замены current card»).
     *
     * Целевая семантика (инварианты SessionEngine): если сохранённый PK выпал
     * из пула (контент мутировал между persist и resume), репозиторий возвращает
     * снимок как есть — recovery делает доменный Engine явно
     * (`restoreCurrentCardIfNeeded` возвращает карту в пул). Текущая реализация
     * молча подставляет первую карту пула — репродукция card_15 на data-слое.
     *
     * RED до Фазы 2 (уравнивание recovery-семантики fake/Room).
     */
    @Ignore("RED-якорь P1 «молчаливая подмена currentCard» — план Фаза 2")
    @Test
    fun loadSession_orphanCurrentCard_isNotSilentlySubstituted() = runTest {
        // Полная сессия с пулом и курсором на средней карточке.
        repository.getOrCreateSession(
            sessionId = sessionId,
            packId = packId,
            lessonId = lessonId,
            mode = TrainingMode.LESSON,
            poolCardIds = TrainingDbFixture.CARD_IDS.map(::CardId),
        )
        repository.setCurrentCard(sessionId, CardId("card_fix_3"))

        // Симуляция пересборки пула (pack update): текущая карточка выпала.
        db.sessionDao().replacePool(
            sessionId.value,
            listOf(
                SessionCardEntity(sessionId.value, ord = 0, cardId = "card_fix_2"),
                SessionCardEntity(sessionId.value, ord = 1, cardId = "card_fix_4"),
            )
        )

        val loaded = repository.loadSession(sessionId)!!

        // Цель: сохранённый PK возвращается как есть (recovery — зона Engine).
        assertThat(loaded.currentCardId?.value).isEqualTo("card_fix_3")
    }

    /**
     * Зелёный guard: saveSession → loadSession roundtrip сохраняет порядок
     * пула, currentCardId по PK и множество показанных (фикс card_15).
     */
    @Test
    fun saveLoadRoundtrip_preservesPoolOrderCurrentAndShown() = runTest {
        repository.getOrCreateSession(
            sessionId = sessionId,
            packId = packId,
            lessonId = lessonId,
            mode = TrainingMode.LESSON,
            poolCardIds = TrainingDbFixture.CARD_IDS.map(::CardId),
        )
        repository.setCurrentCard(sessionId, CardId("card_fix_3"))
        repository.markCardShown(sessionId, CardId("card_fix_2"))

        val loaded = repository.loadSession(sessionId)!!

        assertThat(loaded.poolCardIds.map { it.value })
            .containsExactly("card_fix_2", "card_fix_3", "card_fix_4")
            .inOrder()
        assertThat(loaded.currentCardId?.value).isEqualTo("card_fix_3")
        assertThat(loaded.shownCardIds.map { it.value }).containsExactly("card_fix_2")
    }
}
