package com.alexpo.grammermate.v2.core.data.repository

import android.util.Log
import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.model.SessionSnapshot
import com.alexpo.grammermate.domain.model.SessionState
import com.alexpo.grammermate.domain.model.SessionStatus
import com.alexpo.grammermate.domain.model.TrainingMode
import com.alexpo.grammermate.domain.repository.SessionRepository
import com.alexpo.grammermate.v2.core.data.local.dao.SessionDao
import com.alexpo.grammermate.v2.core.data.local.dao.SessionSnapshotParts
import com.alexpo.grammermate.v2.core.data.local.entity.SessionCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.SessionEntity
import com.alexpo.grammermate.v2.core.data.local.entity.SessionPendingCardEntity
import com.alexpo.grammermate.v2.core.data.local.entity.SessionShownCardEntity
import javax.inject.Inject

/**
 * Битое значение enum-поля в строке сессии (Фаза 2 плана стабилизации
 * 2026-08-26: «ошибочные enum/status/content версии возвращать typed
 * recovery/failure и логировать»).
 *
 * Раньше мусорное значение молча превращалось в `LESSON/ACTIVE` через
 * `getOrDefault` — сессия «оживала» с чужой семантикой. Теперь это явный
 * typed failure: presentation показывает recoverable error, причина — в логе.
 */
class SessionCorruptionException(
    val field: String,
    val rawValue: String,
    val sessionId: String,
) : IllegalStateException(
    "Corrupted session row '$sessionId': field '$field' has unknown value '$rawValue'",
)

/**
 * Реализация [SessionRepository] поверх Room — ★ центральный фикс бага card_15.
 *
 * Сессия хранится и возобновляется как единый целостный снимок
 * ([SessionSnapshot]): упорядоченный пул + множество показанных + текущая
 * карточка по первичному ключу. Resume — одна транзакция чтения
 * ([SessionDao.loadSnapshotParts]); persist — одна транзакция записи
 * ([SessionDao.saveSnapshot]) с проверкой ревизии и hot updates (Фаза 2).
 *
 * Recovery-семантика (Фаза 2, паритет с fake): `currentCardId` вне пула
 * возвращается КАК ЕСТЬ — никакого молчаливого восстановления первой картой;
 * явное восстановление делает домен (`SessionEngine.restoreCurrentCardIfNeeded`
 * возвращает карту в пул).
 *
 * `cursorIndex` — производная проекция `pool.indexOf(currentCardId)`: единственный
 * источник истины позиции — [SessionSnapshot.currentCardId] + порядок пула
 * (второго источника истины больше нет; колонка сохраняется для совместимости).
 *
 * Маппинг entity ↔ domain инкапсулирован здесь. Enum-поля домена хранятся как
 * String; неизвестные значения — [SessionCorruptionException] (+ лог), а не
 * молчаливый дефолт.
 */
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
) : SessionRepository {

    // ── Создание / возобновление ─────────────────────────────────────────────

    /**
     * Создать новую сессию или возобновить существующую по [sessionId].
     *
     * - Если есть ACTIVE-строка — атомарно собрать её снимок (одной транзакцией
     *   чтения) и вернуть как есть (это и есть resume).
     * - Иначе создать свежую сессию с переданным пулом (пул строит
     *   SessionEngine — ADR-001; обязательность параметра закрывает P0
     *   «сессия с пустым пулом» на этапе компиляции).
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
        pendingCardIds: List<CardId>,
        sessionSize: Int,
        selectedTense: String?,
        selectedGroup: String?,
        selectedPerson: String?,
    ): SessionSnapshot {
        // 1. Попытка возобновить существующую ACTIVE-сессию — одним
        //    транзакционным чтением всех частей снимка.
        val parts = sessionDao.loadSnapshotParts(sessionId.value)
        if (parts?.session?.status == SessionStatus.ACTIVE.name) {
            return assembleSnapshot(parts)
        }

        // 2. Нет ACTIVE (нет строки, либо COMPLETED) — свежая сессия с тем же
        //    PK. Завершённая строка удаляется ЦЕЛИКОМ (CASCADE: pool/shown):
        //    свежий проход стартует с ревизией 0, конфликт stale-check
        //    [SessionDao.saveSnapshot] невозможен.
        if (parts != null) {
            sessionDao.deleteSession(sessionId.value)
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
            status = SessionStatus.ACTIVE,
            state = SessionState.ACTIVE,
            revision = 0L,
            poolCardIds = pool,
            pendingCardIds = pendingCardIds.filter { it.value.isNotBlank() },
            sessionSize = sessionSize.coerceAtLeast(1),
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
     * Resume: атомарно загрузить снимок сессии — session + pool + shown
     * ОДНОЙ транзакцией чтения ([SessionDao.loadSnapshotParts], Фаза 2).
     * Возвращает null, если сессии нет.
     *
     * Recovery: сохранённый [SessionSnapshot.currentCardId] вне пула
     * возвращается как есть — явное восстановление делает домен, не data-слой.
     */
    override suspend fun loadSession(sessionId: SessionId): SessionSnapshot? =
        sessionDao.loadSnapshotParts(sessionId.value)?.let(::assembleSnapshot)

    /**
     * Атомарно сохранить весь снимок в одной транзакции ([SessionDao.saveSnapshot]):
     * upsert session + diff pool + diff shown. Обычный Submit/Next не делает
     * delete/reinsert пула и shown-множества (hot updates, Фаза 2).
     *
     * @throws com.alexpo.grammermate.domain.session.StaleSessionRevisionException
     *         при попытке сохранить снимок с устаревшей ревизией.
     */
    override suspend fun saveSession(snapshot: SessionSnapshot) {
        val now = System.currentTimeMillis()
        sessionDao.saveSnapshot(
            session = snapshot.toEntity(now),
            cards = snapshot.toCardEntities(),
            pending = snapshot.toPendingEntities(),
            shown = snapshot.toShownEntities(now),
        )
    }

    /** Пометить сессию завершённой ([SessionStatus.COMPLETED]); ревизия +1. */
    override suspend fun completeSession(sessionId: SessionId) {
        sessionDao.updateStatus(sessionId.value, SessionStatus.COMPLETED.name, System.currentTimeMillis())
    }

    // Фаза 7 плана: granular writer API (setCurrentCard/markCardShown/
    // updateProgress) удалён из порта — единственный путь записи это атомарный
    // saveSession (hot-updates Фазы 2 внутри SessionDao.saveSnapshot).

    /** Удалить сессию целиком (CASCADE снесёт pool и shown-set). */
    override suspend fun deleteSession(sessionId: SessionId) {
        sessionDao.deleteSession(sessionId.value)
    }

    // ── Маппинг entity → domain (сборка снимка) ──────────────────────────────

    /**
     * Собрать [SessionSnapshot] из транзакционно прочитанных [SessionSnapshotParts].
     *
     * `currentCardId` возвращается как сохранён (в т.ч. вне пула) — recovery
     * выполняет домен. `cursorIndex` — производная `pool.indexOf(currentCardId)`.
     */
    private fun assembleSnapshot(parts: SessionSnapshotParts): SessionSnapshot {
        val entity = parts.session
        val poolCardIds = parts.cards
            .sortedBy { it.ord }
            .map { CardId(it.cardId) }

        val shownCardIds = parts.shown.map { CardId(it.cardId) }.toSet()
        val pendingCardIds = parts.pending.sortedBy { it.ord }.map { CardId(it.cardId) }

        val savedCurrent = entity.currentCardId?.takeIf { it.isNotBlank() }?.let(::CardId)

        return SessionSnapshot(
            sessionId = SessionId(entity.id),
            packId = PackId(entity.packId),
            lessonId = entity.lessonId?.takeIf { it.isNotBlank() }?.let(::LessonId),
            mode = entity.mode.toTrainingMode(entity.id),
            currentCardId = savedCurrent,
            status = entity.status.toSessionStatus(entity.id),
            state = entity.state.toSessionState(entity.id),
            revision = entity.revision,
            poolCardIds = poolCardIds,
            pendingCardIds = pendingCardIds,
            sessionSize = entity.sessionSize,
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

    /** Снимок → строка сессии (cursorIndex — производная от currentCardId). */
    private fun SessionSnapshot.toEntity(now: Long): SessionEntity = SessionEntity(
        id = sessionId.value,
        packId = packId.value,
        lessonId = lessonId?.value,
        mode = mode.name,
        subLessonIndex = 0, // под-урок управляется отдельно; здесь нейтральное значение
        cursorIndex = derivedCursor(),
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
        revision = revision,
        sessionSize = sessionSize,
    )

    /** Позиция currentCardId в пуле — единственная согласованная запись курсора. */
    private fun SessionSnapshot.derivedCursor(): Int =
        currentCardId?.let { poolCardIds.indexOf(it) }?.takeIf { it >= 0 } ?: 0

    /** Снимок → упорядоченный пул карточек (ord = позиция в списке). */
    private fun SessionSnapshot.toCardEntities(): List<SessionCardEntity> =
        poolCardIds.mapIndexed { ord, cardId ->
            SessionCardEntity(
                sessionId = sessionId.value,
                ord = ord,
                cardId = cardId.value,
            )
        }

    private fun SessionSnapshot.toPendingEntities(): List<SessionPendingCardEntity> =
        pendingCardIds.mapIndexed { ord, cardId ->
            SessionPendingCardEntity(sessionId = sessionId.value, ord = ord, cardId = cardId.value)
        }

    /**
     * Снимок → целевое множество показанных. `shownAtMs` здесь — время persist
     * ТОЛЬКО для новых строк: DAO вставляет лишь отсутствующие, существующие
     * строки сохраняют исходный timestamp (hot updates).
     */
    private fun SessionSnapshot.toShownEntities(now: Long): List<SessionShownCardEntity> =
        shownCardIds.map { cardId ->
            SessionShownCardEntity(
                sessionId = sessionId.value,
                cardId = cardId.value,
                shownAtMs = now,
            )
        }

    // ── Маппинг String → enum: битые значения — typed failure ────────────────

    private fun String.toTrainingMode(sessionId: String): TrainingMode =
        parseEnum<TrainingMode>("mode", sessionId, this)

    private fun String.toSessionStatus(sessionId: String): SessionStatus =
        parseEnum<SessionStatus>("status", sessionId, this)

    private fun String.toSessionState(sessionId: String): SessionState =
        parseEnum<SessionState>("state", sessionId, this)

    private inline fun <reified T : Enum<T>> parseEnum(
        field: String,
        sessionId: String,
        raw: String,
    ): T = try {
        enumValueOf<T>(raw)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Corrupted session row: field '$field' = '$raw' (session $sessionId)")
        throw SessionCorruptionException(field, raw, sessionId)
    }

    private companion object {
        const val TAG = "SessionRepository"
    }
}
