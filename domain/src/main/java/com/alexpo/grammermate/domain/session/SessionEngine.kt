package com.alexpo.grammermate.domain.session

import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.InputMode
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.model.SessionSnapshot
import com.alexpo.grammermate.domain.model.SessionState
import com.alexpo.grammermate.domain.model.SessionStatus
import com.alexpo.grammermate.domain.model.TrainingMode
import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.repository.SessionRepository
import com.alexpo.grammermate.domain.repository.UserContentRepository

/**
 * Доменное ядро тренировочной сессии — ★ фикс бага `card_15`.
 *
 * В v1 текущая карта идентифицировалась индексом массива, а массив
 * пересобирался в рантайме → после resume индекс указывал на чужую карту,
 * а `card_15` бесследно терялась (не скрыта, не отвечена, но не показывалась).
 *
 * Здесь текущая карта — это **первичный ключ** [SessionSnapshot.currentCardId],
 * а пул хранится целиком в снимке. Любое изменение пула (например,
 * [hideCard]) явно пересчитывает `currentCardId` по PK и сохраняет весь
 * снимок одной операцией [SessionRepository.saveSession] — НИКОГДА
 * молчаливой подмены.
 *
 * Чистый Kotlin (suspend, корутины), ноль Android-импортов: тестируется
 * на чистом JVM через in-memory fake-репозитории.
 *
 * Гарантируемые инварианты (покрыты `SessionEngineResumeRegressionTest` и
 * `SessionEnginePropertyTest`):
 * 1. `currentCardId` ВСЕГДА ∈ `poolCardIds` (или null, если пул пуст).
 * 2. `resumeSession` возвращает снимок с тем же `currentCardId`, что был
 *    при сохранении — идентичность сохранена.
 * 3. Скрытие ТЕКУЩЕЙ карты ЯВНО переводит `currentCardId` на следующую
 *    доступную (не на null, не молча).
 * 4. `submitAnswer` атомарно обновляет прогресс и (для VOICE/KEYBOARD)
 *    пометку shown одной `saveSession`.
 * 5. WORD_BANK не помечает карту shown (дизайн v1 — сохранён).
 * 6. Mastery/flower-advance GATED по [InputMode]: только VOICE/KEYBOARD
 *    триггерят [onMarkShown]-хук (downstream подключает его к
 *    `MasteryRepository.recordCardShow` → `FlowerCalculator`). WORD_BANK
 *    НИКОГДА не дёргает mastery — пассивный показ из набора слов не считается
 *    «самостоятельным вводом». AC-7 #3 / AC-8 #3.
 * 7. Повторный submit той же карточки в том же проходе идемпотентен: карта
 *    уже в `shownCardIds` → снимок возвращается без изменений (double-tap /
 *    stale retry не двойной счёт). MODE_MATRIX → Normal lesson → Attempt:
 *    «одна submit-попытка закрывает карточку прохода».
 * 8. `revision` строго растёт на каждой durable-мутации (+1); сохранение
 *    устаревшей ревизии отвергается [StaleSessionRevisionException].
 *
 * @property sessionRepository    персистенция снимков (одна транзакция = атомарность).
 * @property contentRepository    read-only контент паков (карточки урока).
 * @property userContentRepository скрытые/«плохие» карточки пользователя.
 * @property clock                injectable источник времени (детерминизм в тестах).
 * @property commitCoordinator    транзакционный координатор составных событий
 *                                (answer+shown+mastery, completion+lesson-done)
 *                                — Фаза 2 плана; по умолчанию проходной.
 * @property onMarkShown          optional хук «карточка помечена shown в активном
 *                                режиме (VOICE/KEYBOARD)» с pack/lesson-контекстом
 *                                (Фаза 2: сигнатуры хватает для
 *                                `MasteryRepository.recordCardShow`). null-реализация
 *                                по умолчанию — Engine не зависит от
 *                                MasteryRepository.
 * @property onSessionCompleted   optional хук «сессия завершена» — downstream
 *                                фиксирует завершение урока
 *                                (`MasteryRepository.markLessonCompleted`) в той же
 *                                транзакции координатора.
 */
class SessionEngine(
    private val sessionRepository: SessionRepository,
    private val contentRepository: ContentRepository,
    private val userContentRepository: UserContentRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val commitCoordinator: SessionCommitCoordinator = PassThroughCommitCoordinator,
    private val onMarkShown: suspend (packId: PackId, lessonId: LessonId?, cardId: CardId, nowMs: Long) -> Unit = { _, _, _, _ -> },
    private val onSessionCompleted: suspend (packId: PackId, lessonId: LessonId?, nowMs: Long) -> Unit = { _, _, _ -> },
) {

    // ── Построение пула ────────────────────────────────────────────────────

    /**
     * Собрать активный пул карточек для урока:
     * 1. все карты урока ([ContentRepository.getCards]);
     * 2. минус скрытые пользователем ([UserContentRepository.getHiddenCardIds]);
     * 3. порядок карт — по [LessonOrderPolicy] для [mode] (Фаза 4 срез 1:
     *    ALL_MIXED = детерминированное чередование половин);
     * 4. нарезать на под-уроки по [sessionSize] через [SubLessonScheduler];
     * 5. выбрать активный под-урок (индекс 0 для новой сессии).
     *
     * @return упорядоченный пул [CardId] активного под-урока.
     */
    private suspend fun buildPool(
        lessonId: LessonId,
        sessionSize: Int,
        mode: TrainingMode,
        activeSubLessonIndex: Int = 0,
    ): List<CardId> {
        val allCards = contentRepository.getCards(lessonId)
        val hidden = userContentRepository.getHiddenCardIds()
        val visible = LessonOrderPolicy.apply(
            cards = allCards.filter { it.id !in hidden },
            mode = mode,
        )
        val subLessons = SubLessonScheduler.buildSubLessons(visible, sessionSize)
        return SubLessonScheduler.activeSubLesson(subLessons, activeSubLessonIndex)
    }

    // ── Публичный API ──────────────────────────────────────────────────────

    /**
     * Начать новую сессию урока: построить пул, создать снимок, сохранить.
     *
     * `currentCardId` = первая карта пула (новая сессия). Стабильный
     * [sessionId] выводится из `packId`+`lessonId`. [mode] задаёт политику
     * порядка пула ([LessonOrderPolicy]): LESSON/ALL_SEQUENTIAL — порядок
     * урока; ALL_MIXED — чередование половин (mixed review, Фаза 4 срез 1).
     */
    suspend fun startLessonSession(
        packId: PackId,
        lessonId: LessonId,
        sessionSize: Int,
        mode: TrainingMode = TrainingMode.LESSON,
    ): SessionSnapshot {
        val sessionId = SessionId.forLesson(packId, lessonId)
        val pool = buildPool(lessonId, sessionSize, mode)
        return sessionRepository.getOrCreateSession(
            sessionId = sessionId,
            packId = packId,
            lessonId = lessonId,
            mode = mode,
            poolCardIds = pool,
        )
    }

    /**
     * Resume: атомарно загрузить снимок из БД одним чтением.
     *
     * Если `currentCardId` оказался вне пула (пул изменился внешне, либо
     * повреждение) — **явное восстановление**: карта возвращается в пул,
     * снимок пересохраняется. НИКОГДА не возвращаем снимок, где
     * `currentCardId` указывает мимо пула, и НИКОГДА не подменяем карту молча.
     *
     * @return снимок целиком либо null, если сессии нет.
     */
    suspend fun resumeSession(sessionId: SessionId): SessionSnapshot? {
        val snapshot = sessionRepository.loadSession(sessionId) ?: return null
        val restored = restoreCurrentCardIfNeeded(snapshot)
        if (restored === snapshot) return restored
        // Явное восстановление персистится с ревизией +1 (инвариант 8).
        val resaved = restored.copy(revision = snapshot.revision + 1)
        sessionRepository.saveSession(resaved)
        return resaved
    }

    /**
     * Начать урок заново: удалить сохранённую сессию (пул/курсор/счётчики
     * ответов) и построить свежую ([startLessonSession]).
     *
     * Reset-семантика (MODE_MATRIX): очищается ТОЛЬКО контекст сессии —
     * mastery/цветы (uniqueCardShows, completedAtMs) не трогаются.
     * Владелец мутации — Engine: presentation не вызывает delete напрямую.
     */
    suspend fun restartLessonSession(
        packId: PackId,
        lessonId: LessonId,
        sessionSize: Int,
        mode: TrainingMode = TrainingMode.LESSON,
    ): SessionSnapshot {
        sessionRepository.deleteSession(SessionId.forLesson(packId, lessonId))
        return startLessonSession(
            packId = packId,
            lessonId = lessonId,
            sessionSize = sessionSize,
            mode = mode,
        )
    }

    /**
     * Перейти к следующей карточке. Обновляет `currentCardId` по PK
     * (индекс в пуле + 1, с зацикливанием). Пустой пул → currentCardId null.
     */
    suspend fun nextCard(sessionId: SessionId): SessionSnapshot {
        val current = requireActive(sessionId)
        val pool = current.poolCardIds
        if (pool.isEmpty()) return saveTimestamped(current, currentCardId = null)
        val idx = current.currentCardId?.let { pool.indexOf(it) } ?: -1
        val nextIdx = if (idx < 0) 0 else (idx + 1) % pool.size
        return saveTimestamped(current, currentCardId = pool[nextIdx])
    }

    /**
     * Перейти к предыдущей карточке (индекс в пуле − 1, с зацикливанием).
     */
    suspend fun previousCard(sessionId: SessionId): SessionSnapshot {
        val current = requireActive(sessionId)
        val pool = current.poolCardIds
        if (pool.isEmpty()) return saveTimestamped(current, currentCardId = null)
        val idx = current.currentCardId?.let { pool.indexOf(it) } ?: 0
        val prevIdx = if (idx <= 0) pool.lastIndex else idx - 1
        return saveTimestamped(current, currentCardId = pool[prevIdx])
    }

    /**
     * Advance режима LESSON с терминальным условием «полный проход пула»
     * (MODE_MATRIX.md → Normal lesson → Completion; Фаза 1 плана стабилизации
     * 2026-08-26): если текущая карта — последняя в пуле, сессия завершается
     * ([SessionStatus.COMPLETED], снимок сохранён); иначе — как [nextCard].
     *
     * Политика завершения живёт здесь, в домене, а не в ViewModel (план §3.1.7:
     * mode задаёт completion). Используется и для Next после ответа, и для Skip.
     *
     * Завершение — составное событие (Фаза 2): смена статуса и
     * [onSessionCompleted] (фиксация завершения урока) применяются в одной
     * транзакции [commitCoordinator.commit].
     *
     * @return снимок после advance либо завершённый снимок (status = COMPLETED).
     */
    suspend fun nextCardOrComplete(sessionId: SessionId): SessionSnapshot {
        val current = requireActive(sessionId)
        val pool = current.poolCardIds
        if (pool.isEmpty()) return saveTimestamped(current, currentCardId = null)
        val idx = current.currentCardId?.let { pool.indexOf(it) } ?: -1
        return if (idx == pool.lastIndex) {
            val nowMs = clock()
            commitCoordinator.commit {
                val completed = current.copy(
                    status = SessionStatus.COMPLETED,
                    updatedAtMs = nowMs,
                    revision = current.revision + 1,
                )
                sessionRepository.saveSession(completed)
                onSessionCompleted(current.packId, current.lessonId, nowMs)
                completed
            }
        } else {
            nextCard(sessionId)
        }
    }

    /**
     * Зафиксировать ответ: обновить прогресс (correct/incorrect) и, для
     * VOICE/KEYBOARD, пометить карту показанной. WORD_BANK — НЕ mark shown
     * (дизайн v1: ввод из набора слов не считается полноценным показом).
     *
     * Атомарность составного события (Фаза 2 плана): load → счётчики →
     * saveSession → mastery-хук выполняются внутри [commitCoordinator.commit]
     * — персистенция снимка и запись mastery применяются одной транзакцией
     * (ADR-001 pre-mortem №3).
     *
     * Идемпотентность повторного submit (инвариант 7): если карточка уже в
     * `shownCardIds` (самостоятельный ввод уже засчитан), снимок возвращается
     * как есть — без второго счёта и без повторного mastery.
     *
     * **Mastery/flower gate (AC-7 #3 / AC-8 #3):** только когда карта реально
     * помечается shown (inputMode == VOICE или KEYBOARD), вызывается хук
     * [onMarkShown] — downstream продвигает `LessonMastery` (счётчик показов,
     * `FlowerCalculator`). Для WORD_BANK хук НЕ дёргается → mastery/flower не
     * растут от пассивного показа. Это прямое доказуемое соответствие бизнес-
     * правилу «WORD_BANK ≠ mastery» (TARGET_ARCHITECTURE.md §12).
     */
    suspend fun submitAnswer(
        sessionId: SessionId,
        cardId: CardId,
        isCorrect: Boolean,
        inputMode: InputMode,
    ): SessionSnapshot = commitCoordinator.commit {
        val current = requireActive(sessionId)
        // Только «самостоятельный» ввод (VOICE/KEYBOARD) помечает карту shown.
        val markShown = inputMode != InputMode.WORD_BANK
        // Идемпотентность повторного submit той же карточки прохода.
        if (markShown && cardId in current.shownCardIds) {
            return@commit current
        }
        val newCorrect = current.correctCount + (if (isCorrect) 1 else 0)
        val newIncorrect = current.incorrectCount + (if (!isCorrect) 1 else 0)
        val nowMs = clock()
        val newShown = if (markShown) current.shownCardIds + cardId else current.shownCardIds
        val updated = current.copy(
            correctCount = newCorrect,
            incorrectCount = newIncorrect,
            shownCardIds = newShown,
            updatedAtMs = nowMs,
            revision = current.revision + 1,
        )
        sessionRepository.saveSession(updated)
        // Mastery/flower продвигаются ТОЛЬКО для «самостоятельного» ввода.
        // AC-7 (WORD_BANK): hook не вызывается → FlowerCalculator не вызывается для X.
        // AC-8 (KEYBOARD/VOICE): hook вызывается → mastery advanced для X.
        if (markShown) {
            onMarkShown(current.packId, current.lessonId, cardId, nowMs)
        }
        updated
    }

    /**
     * Пометить карточку «плохой» (flag). По дизайну v1 **bad ≠ hide**:
     * флаг НЕ убирает карту из пула и не меняет `currentCardId`.
     * (Сама запись «плохого» предложения делается data/ui-слоем с полным
     * контекстом карты; здесь — гарантия, что пул сессии не трогается.)
     */
    suspend fun flagCard(sessionId: SessionId, cardId: CardId): SessionSnapshot {
        val current = requireActive(sessionId)
        // Пул и currentCardId намеренно не меняются.
        return saveTimestamped(current)
    }

    /**
     * Скрыть карточку: убрать её из пула, из множества показанных И из учёта
     * завершения.
     *
     * Если скрыта ТЕКУЩАЯ карта — `currentCardId` **ЯВНО** переходит на
     * следующую доступную карту (на ту же позицию в урезанном пуле, с
     * зацикливанием, если скрыта хвостовая). Если скрыта не текущая —
     * `currentCardId` сохраняется (он всё ещё в пуле). Пустой пул → null.
     *
     * Скрытая карта исключается и из `shownCardIds` (Фаза 2): карточки больше
     * нет в учёте сессии, инвариант `shownCardIds ⊆ poolCardIds` сохраняется.
     */
    suspend fun hideCard(sessionId: SessionId, cardId: CardId): SessionSnapshot {
        val current = requireActive(sessionId)
        userContentRepository.hideCard(cardId, clock())
        val oldPool = current.poolCardIds
        val newPool = oldPool.filter { it != cardId }
        val newCurrent = reassignCurrentAfterHide(current.currentCardId, cardId, oldPool, newPool)
        val updated = current.copy(
            poolCardIds = newPool,
            currentCardId = newCurrent,
            shownCardIds = current.shownCardIds - cardId,
            updatedAtMs = clock(),
            revision = current.revision + 1,
        )
        sessionRepository.saveSession(updated)
        return updated
    }

    /** Завершить сессию ([SessionStatus.COMPLETED]). */
    suspend fun completeSession(sessionId: SessionId) {
        sessionRepository.completeSession(sessionId)
    }

    // ── Внутренние хелперы ─────────────────────────────────────────────────

    /**
     * Явный пересчёт `currentCardId` после скрытия карты.
     *
     * - скрыта НЕ текущая → текущая сохраняется (она в новом пуле);
     * - скрыта ТЕКУЩАЯ → **ЯВНЫЙ** переход на «следующую»: берём элемент,
     *   который шёл сразу после скрытой в исходном порядке [oldPool], с
     *   зацикливанием на первый доступный, если скрыта хвостовая;
     * - пустой пул → null.
     *
     * Семантика advance (как [nextCard]): пользователь скрыл текущую карту,
     * значит урок должен продолжиться со следующей, а не зависнуть.
     */
    private fun reassignCurrentAfterHide(
        currentCardId: CardId?,
        hiddenCardId: CardId,
        oldPool: List<CardId>,
        newPool: List<CardId>,
    ): CardId? {
        if (newPool.isEmpty()) return null
        // Скрыта не текущая (или текущей нет) → идентичность сохранена.
        if (currentCardId == null || currentCardId != hiddenCardId) {
            return if (currentCardId in newPool) currentCardId else newPool.first()
        }
        // Скрыта ТЕКУЩАЯ: следующий за ней по исходному порядку, с wrap-around.
        val hiddenIdx = oldPool.indexOf(hiddenCardId)
        return if (hiddenIdx in oldPool.indices && hiddenIdx + 1 < oldPool.size) {
            oldPool[hiddenIdx + 1]
        } else {
            newPool.first()
        }
    }

    /**
     * Восстановление при resume, если `currentCardId` оказался вне пула.
     *
     * Сценарий бага card_15: пул пересобран/изменён, а курсор ссылается на
     * выпавшую карту. Решение — **вернуть карту в пул** (явное восстановление,
     * не подмена): карта не была ни скрыта, ни отвечена, значит она должна
     * показатьcя снова. Если пул пуст — currentCardId = null.
     */
    private fun restoreCurrentCardIfNeeded(snapshot: SessionSnapshot): SessionSnapshot {
        val current = snapshot.currentCardId ?: return snapshot
        if (current in snapshot.poolCardIds) return snapshot
        // Карта выпала из пула — возвращаем её явно (в конец).
        return snapshot.copy(
            poolCardIds = snapshot.poolCardIds + current,
            updatedAtMs = clock(),
        )
    }

    /** Загрузить снимок; бросить, если сессии нет (программная ошибка вызывающего). */
    private suspend fun requireActive(sessionId: SessionId): SessionSnapshot =
        sessionRepository.loadSession(sessionId)
            ?: error("Session $sessionId not found — call startLessonSession/resumeSession first.")

    /** Сохранить снимок с обновлённым `currentCardId`/timestamp и ревизией +1. */
    private suspend fun saveTimestamped(
        current: SessionSnapshot,
        currentCardId: CardId? = current.currentCardId,
    ): SessionSnapshot {
        val updated = current.copy(
            currentCardId = currentCardId,
            updatedAtMs = clock(),
            revision = current.revision + 1,
        )
        sessionRepository.saveSession(updated)
        return updated
    }
}
