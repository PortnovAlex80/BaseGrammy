package com.alexpo.grammermate.domain.session

import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.model.SessionStatus
import com.alexpo.grammermate.domain.model.TrainingMode
import com.alexpo.grammermate.domain.model.VerbDrillCard
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Verb-drill сессия (Фаза 4 срез 2, доменная часть).
 *
 * Пул = combo-фильтрованные карты пака в порядке частотности; фильтры
 * персистятся в снимке (resume восстанавливает combo); PK стабилен.
 */
class SessionEngineVerbDrillTest {

    private val packId = PackId("pack")

    private fun card(
        id: String,
        tense: String,
        group: String? = null,
        rank: Int? = null,
    ) = VerbDrillCard(
        id = id,
        promptRu = "p $id",
        answer = "a $id",
        verb = null,
        tense = tense,
        group = group,
        person = null,
        rank = rank,
    )

    private fun engineWith(vararg cards: VerbDrillCard): Pair<SessionEngine, FakeSessionRepository> {
        val content = FakeContentRepository().apply { setVerbDrillCards(packId, cards.toList()) }
        val sessionRepo = FakeSessionRepository(clock = { 1L })
        return SessionEngine(
            sessionRepository = sessionRepo,
            contentRepository = content,
            userContentRepository = FakeUserContentRepository(),
            clock = { 1L },
        ) to sessionRepo
    }

    @Test
    fun `pool is ranked combo-filtered and mode is VERB_DRILL`() = runTest {
        val (engine, _) = engineWith(
            card("vc_1", tense = "present", rank = 5),
            card("vc_2", tense = "present", rank = 1),
            card("vc_3", tense = "passato", rank = 0),
        )

        val snapshot = engine.startVerbDrillSession(packId, sessionSize = 10, tense = "present")

        assertThat(snapshot.mode).isEqualTo(TrainingMode.VERB_DRILL)
        assertThat(snapshot.sessionId).isEqualTo(SessionId.forVerbDrill(packId))
        assertThat(snapshot.lessonId).isNull()
        // Частотность: rank 1 раньше rank 5; passato отфильтрован.
        assertThat(snapshot.poolCardIds.map { it.value })
            .containsExactly("vc_2", "vc_1").inOrder()
        assertThat(snapshot.status).isEqualTo(SessionStatus.ACTIVE)
    }

    @Test
    fun `combo filters are persisted in snapshot for resume`() = runTest {
        val (engine, _) = engineWith(card("vc_1", tense = "present", group = "irregular"))

        val snapshot = engine.startVerbDrillSession(
            packId, sessionSize = 10, tense = "present", group = "irregular", person = "Io",
        )

        assertThat(snapshot.selectedTense).isEqualTo("present")
        assertThat(snapshot.selectedGroup).isEqualTo("irregular")
        assertThat(snapshot.selectedPerson).isEqualTo("Io")
    }

    @Test
    fun `sessionSize caps pool and zero means unlimited`() = runTest {
        val (engine, _) = engineWith(
            card("vc_1", tense = "present", rank = 1),
            card("vc_2", tense = "present", rank = 2),
            card("vc_3", tense = "present", rank = 3),
        )

        val capped = engine.startVerbDrillSession(packId, sessionSize = 2)
        assertThat(capped.poolCardIds.map { it.value }).containsExactly("vc_1", "vc_2").inOrder()

        // Тот же PK резюмит существующую сессию (create-or-resume) — «без
        // ограничения» проверяем на свежем движке/репозитории.
        val (freshEngine, _) = engineWith(
            card("vc_1", tense = "present", rank = 1),
            card("vc_2", tense = "present", rank = 2),
            card("vc_3", tense = "present", rank = 3),
        )
        val full = freshEngine.startVerbDrillSession(packId, sessionSize = 0)
        assertThat(full.poolCardIds).hasSize(3)
    }

    @Test
    fun `empty combo result yields empty pool snapshot (no crash)`() = runTest {
        val (engine, _) = engineWith(card("vc_1", tense = "present"))

        val snapshot = engine.startVerbDrillSession(packId, sessionSize = 10, tense = "futuro")

        assertThat(snapshot.poolCardIds).isEmpty()
        assertThat(snapshot.currentCardId).isNull()
    }
}
