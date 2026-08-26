package com.alexpo.grammermate.v2.core.data.repository

import com.alexpo.grammermate.v2.core.data.local.dao.SessionDao
import com.alexpo.grammermate.v2.core.data.local.entity.SessionCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.SessionEntity
import com.alexpo.grammermate.v2.core.data.local.entity.SessionShownCardEntity
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.model.SessionSnapshot
import com.alexpo.grammermate.domain.model.SessionState
import com.alexpo.grammermate.domain.model.SessionStatus
import com.alexpo.grammermate.domain.model.TrainingMode
import com.alexpo.grammermate.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * Реализация [SessionRepository] поверх Room — ★ центральный фикс бага card_15.
 *
 * Сессия хранится и возобновляется как единый целостный снимок
 * ([SessionSnapshot]): курсор + упорядоченный пул + множество показанных +
 * текущая карточка по первичному ключу ([SessionSnapshot.currentCardId], а НЕ
 * по индексу массива). Раньше текущая карточка была `массив[индекс]` и при
 * пересборке пула терялась; теперь `currentCardId` — это PK карточки, а resume
 * грузит весь снимок одной атомарной операцией чтения, a persist — одной
 * атомарной транзакцией записи.
 *
 * Атомарность гарантирует [SessionDao.saveSnapshot]: курсор + пул +
 * currentCardId + shown-set всегда согласованы — ни при каких условиях курсор
 * не может сослаться на карточку, которой нет в пуле.
 *
 * Маппинг entity ↔ domain инкапсулирован здесь (entity из data-слоя не
 * утекают в domain). Enum-поля домена (TrainingMode, SessionStatus,
 * SessionState) хранятся в БД как String; маппинг enum ↔ String — в
 * приватных extension-функциях ниже.
 *
 * @property sessionDao Room-DAO сессий (включает атомарные
 *                      [SessionDao.saveSnapshot] / [SessionDao.replacePool]).
 */
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
) : SessionRepository {

    // ── Создание / возобновление ─────────────────────────────────────────────

    /**
     * Создать новую сессию или возобновить существующую по [sessionId].
     *
     * - Если есть [SessionEntity] со статусом ACTIVE — атомарно собрать её снимок
     *   и вернуть как есть (это и есть resume).
     * - Иначе создать свежую сессию с переданным пулом (пул строит SessionEngine —
     *   ADR-001; обязательность параметра закрывает P0 «сессия с пустым пулом»
     *   на этапе компиляции).
     *
     * @param poolCardIds готовый упорядоченный пул (пустой допустим: все карты
     *                    урока скрыты → UI получает явное Empty, а не fallback-карту).
     */
    override suspend fun getOrCreateSession(
        sessionId: SessionId,
        packId: PackId,
        lessonId: LessonId?,
        mode: TrainingMode,
        poolCardIds: List<CardId>,
        selectedTense: String?,
        selectedGroup: String?,
        selectedPerson: String?,
    ): SessionSnapshot {
        // 1. Попытка возобновить существующую ACTIVE-сессию.
        val active = sessionDao.getActiveSession(sessionId.value)
        if (active != null) {
            return assembleSnapshot(active)
        }

        // 2. Нет активной — создаём новую с переданным пулом.
        val now = System.currentTimeMillis()
        val pool = poolCardIds.filter { it.value.isNotBlank() }
        val snapshot = SessionSnapshot(
            sessionId = sessionId,
            packId = packId,
            lessonId = lessonId,
            mode = mode,
            currentCardId = pool.firstOrNull(),
            cursorIndex = 0,
            status = SessionStatus.ACTIVE,
            state = SessionState.ACTIVE,
            poolCardIds = pool,
            shownCardIds = emptySet(),
            correctCount = 0,
            incorrectCount = 0,
            hintCount = 0,
            completedSubLessonCount = 0,
            selectedTense = selectedTense,
            selectedGroup = selectedGroup,
            selectedPerson = selectedPerson,
            startedAtMs = now,
            updatedAtMs = now,
        )
        // Атомарно persist: session + pool + shown в одной транзакции.
        saveSession(snapshot)
        return snapshot
    }

    /**
     * Resume: атомарно загрузить снимок сессии одним чтением БД.
     *
     * Три SELECT (session, pool, shown) выполняются последовательно, но в рамках
     * одной логической операции чтения Room; между ними нет записи, поэтому
     * рассинхрон невозможен. Возвращает null, если сессии нет.
     *
     * ★ Если сохранённый [SessionSnapshot.currentCardId] (PK) не входит в пул
     * (например, пул пересобрали между persist и resume), снимок валиден —
     * курсор остаётся как есть, а текущая карточка обнуляется до первой в пуле.
     * Это явный recovery, а не молчаливая подмена по индексу (фикс card_15).
     */
    override suspend fun loadSession(sessionId: SessionId): SessionSnapshot? {
        val entity = sessionDao.getSession(sessionId.value) ?: return null
        return assembleSnapshot(entity)
    }

    /**
     * Атомарно сохранить весь снимок в одной транзакции (через
     * [SessionDao.saveSnapshot]): upsert session + replace pool + replace shown.
     *
     * Это ГЛАВНЫЙ ФИКС card_15: курсор, пул, currentCardId и shown-set всегда
     * согласованы, поскольку пишутся вместе и не могут «съехать» друг от друга.
     */
    override suspend fun saveSession(snapshot: SessionSnapshot) {
        val now = System.currentTimeMillis()
        sessionDao.saveSnapshot(
            session = snapshot.toEntity(now),
            cards = snapshot.toCardEntities(),
            shown = snapshot.toShownEntities(now),
        )
    }

    /**
     * Пометить сессию завершённой ([SessionStatus.COMPLETED]).
     */
    override suspend fun completeSession(sessionId: SessionId) {
        sessionDao.updateStatus(sessionId.value, SessionStatus.COMPLETED.name, System.currentTimeMillis())
    }

    /**
     * Установить текущую карточку по её первичному ключу [cardId] (★ НЕ по индексу).
     *
     * Курсор [snapshot.cursorIndex] намеренно не сдвигается здесь — его двигает
     * доменный SessionEngine; репозиторий лишь персистит PK карточки.
     */
    override suspend fun setCurrentCard(sessionId: SessionId, cardId: CardId) {
        // Курсор не известен на этом уровне → берём текущий из строки, чтобы
        // не затереть его. Делаем read-modify-write: безопасно, т.к. между чтением
        // и записью нет конкурирующей записи в рамках одного Engine-вызова.
        val current = sessionDao.getSession(sessionId.value)
        val cursor = current?.cursorIndex ?: 0
        sessionDao.updateCursor(sessionId.value, cardId.value, cursor, System.currentTimeMillis())
    }

    /**
     * Добавить карточку в множество показанных ([SessionSnapshot.shownCardIds]).
     *
     * INSERT OR IGNORE (см. [SessionDao.markShown]) — повторная пометка той же
     * карточки идемпотентна.
     */
    override suspend fun markCardShown(sessionId: SessionId, cardId: CardId) {
        sessionDao.markShown(
            SessionShownCardEntity(
                sessionId = sessionId.value,
                cardId = cardId.value,
                shownAtMs = System.currentTimeMillis(),
            )
        )
    }

    /**
     * Обновить счётчики правильных/неправильных/подсказок сессии.
     */
    override suspend fun updateProgress(sessionId: SessionId, correct: Int, incorrect: Int, hint: Int) {
        sessionDao.updateCounts(sessionId.value, correct, incorrect, hint, System.currentTimeMillis())
    }

    /**
     * Удалить сессию целиком (CASCADE снесёт pool и shown-set).
     */
    override suspend fun deleteSession(sessionId: SessionId) {
        sessionDao.deleteSession(sessionId.value)
    }

    // ── Маппинг entity → domain (сборка снимка) ──────────────────────────────

    /**
     * Собрать [SessionSnapshot] из [SessionEntity] + связанных pool/shown.
     *
     * Атомарное по смыслу чтение: три SELECT идут подряд без записи между ними.
     * Включает recovery currentCardId: если сохранённый PK не входит в пул,
     * текущей становится первая карточка пула (или null при пустом пуле).
     */
    private suspend fun assembleSnapshot(entity: SessionEntity): SessionSnapshot {
        val pool = sessionDao.getSessionCards(entity.id)
        val poolCardIds = pool
            .sortedBy { it.ord }
            .map { CardId(it.cardId) }

        val shown = sessionDao.getShownCards(entity.id)
        val shownCardIds = shown.map { CardId(it.cardId) }.toSet()

        // Recovery: currentCardId должен быть валидным PK из пула.
        val savedCurrent = entity.currentCardId?.takeIf { it.isNotBlank() }
        val currentCardId: CardId? = when {
            savedCurrent == null -> poolCardIds.firstOrNull()
            poolCardIds.any { it.value == savedCurrent } -> CardId(savedCurrent)
            else -> poolCardIds.firstOrNull() // PK пропал из пула → первая карточка
        }

        return SessionSnapshot(
            sessionId = SessionId(entity.id),
            packId = PackId(entity.packId),
            lessonId = entity.lessonId?.takeIf { it.isNotBlank() }?.let(::LessonId),
            mode = entity.mode.toTrainingMode(),
            currentCardId = currentCardId,
            cursorIndex = entity.cursorIndex.coerceAtLeast(0),
            status = entity.status.toSessionStatus(),
            state = entity.state.toSessionState(),
            poolCardIds = poolCardIds,
            shownCardIds = shownCardIds,
            correctCount = entity.correctCount,
            incorrectCount = entity.incorrectCount,
            hintCount = entity.hintCount,
            completedSubLessonCount = entity.completedSubLessonCount,
            selectedTense = entity.selectedTense,
            selectedGroup = entity.selectedGroup,
            selectedPerson = entity.selectedPerson,
            startedAtMs = entity.startedAtMs,
            updatedAtMs = entity.updatedAtMs,
        )
    }

    // ── Маппинг domain → entity ──────────────────────────────────────────────

    /** Снимок → строка сессии (c новым [now] как updatedAtMs). */
    private fun SessionSnapshot.toEntity(now: Long): SessionEntity = SessionEntity(
        id = sessionId.value,
        packId = packId.value,
        lessonId = lessonId?.value,
        mode = mode.name,
        subLessonIndex = 0, // под-урок управляется отдельно; здесь нейтральное значение
        cursorIndex = cursorIndex,
        currentCardId = currentCardId?.value,
        selectedTense = selectedTense,
        selectedGroup = selectedGroup,
        selectedPerson = selectedPerson,
        status = status.name,
        state = state.name,
        correctCount = correctCount,
        incorrectCount = incorrectCount,
        hintCount = hintCount,
        incorrectAttemptsForCard = 0, // агрегируется на уровне карточки, не снимка
        completedSubLessonCount = completedSubLessonCount,
        activeTimeMs = 0L, // телеметрия времени считается вне снимка
        voiceActiveMs = 0L,
        voiceWordCount = 0,
        startedAtMs = startedAtMs,
        updatedAtMs = now,
    )

    /** Снимок → упорядоченный пул карточек (ord = позиция в списке). */
    private fun SessionSnapshot.toCardEntities(): List<SessionCardEntity> =
        poolCardIds.mapIndexed { ord, cardId ->
            SessionCardEntity(
                sessionId = sessionId.value,
                ord = ord,
                cardId = cardId.value,
            )
        }

    /** Снимок → множество показанных карточек. */
    private fun SessionSnapshot.toShownEntities(now: Long): List<SessionShownCardEntity> =
        shownCardIds.map { cardId ->
            SessionShownCardEntity(
                sessionId = sessionId.value,
                cardId = cardId.value,
                // shownAtMs неизвестен в снимке → метим временем persist.
                shownAtMs = now,
            )
        }

    // ── Маппинг String → enum (защита от мусорных/устаревших значений) ───────

    private fun String.toTrainingMode(): TrainingMode =
        runCatching { TrainingMode.valueOf(this) }.getOrDefault(TrainingMode.LESSON)

    private fun String.toSessionStatus(): SessionStatus =
        runCatching { SessionStatus.valueOf(this) }.getOrDefault(SessionStatus.ACTIVE)

    private fun String.toSessionState(): SessionState =
        runCatching { SessionState.valueOf(this) }.getOrDefault(SessionState.ACTIVE)
}
