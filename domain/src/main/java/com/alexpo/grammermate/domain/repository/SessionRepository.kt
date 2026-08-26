package com.alexpo.grammermate.domain.repository

import com.alexpo.grammermate.domain.model.CardId
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionId
import com.alexpo.grammermate.domain.model.SessionSnapshot
import com.alexpo.grammermate.domain.model.TrainingMode

/**
 * Управление тренировочными сессиями — ★ критично, фикс бага card_15.
 *
 * Сессия хранится и возобновляется как единый целостный снимок
 * ([SessionSnapshot]): курсор + упорядоченный пул + множество показанных +
 * текущая карточка по PK. Resume и persist выполняются атомарно (одна
 * транзакция Room / один SELECT), что исключает рассинхрон состояния,
 * из-за которого после возобновления показывалась чужая карточка.
 *
 * Все mutating-методы — `suspend`; UI не подписывается на сессию реактивно,
 * поэтому [Flow] здесь не нужен.
 */
interface SessionRepository {

    /**
     * Создать новую сессию или возобновить существующую по [sessionId].
     *
     * Если сессия уже есть (ACTIVE) — возвращает её снимок. Иначе сохраняет
     * свежий снимок с переданным пулом.
     *
     * **Владение пулом (ADR-001, Фаза 1 плана стабилизации 2026-08-26):** пул
     * собирает вызывающий доменный код (`SessionEngine.buildPool` — фильтры,
     * нарезка под-уроков), data-слой только персистит то, что ему передали.
     * [poolCardIds] — обязательный параметр: сессия урока без пула —
     * compile-time ошибка, а не молчаливый пустой пул (P0-дефект плана §2).
     *
     * @param sessionId      стабильный идентификатор сессии (см. [SessionId.forLesson] и др.).
     * @param packId         пак тренировки.
     * @param lessonId       урок (null для drill/daily/помодоро).
     * @param mode           режим тренировки.
     * @param poolCardIds    готовый упорядоченный пул карточек (может быть пустым —
     *                       например, все карты урока скрыты; UI покажет Empty).
     * @param selectedTense  фильтр времени для verb-drill, либо null.
     * @param selectedGroup  фильтр группы спряжения для drill, либо null.
     * @param selectedPerson фильтр лица/числа для drill, либо null.
     */
    suspend fun getOrCreateSession(
        sessionId: SessionId,
        packId: PackId,
        lessonId: LessonId?,
        mode: TrainingMode,
        poolCardIds: List<CardId>,
        selectedTense: String? = null,
        selectedGroup: String? = null,
        selectedPerson: String? = null,
    ): SessionSnapshot

    /**
     * Resume: атомарно загрузить снимок сессии одним SELECT.
     *
     * @return снимок целиком либо null, если сессии нет.
     */
    suspend fun loadSession(sessionId: SessionId): SessionSnapshot?

    /**
     * Атомарно сохранить весь снимок в одной транзакции.
     *
     * Гарантирует целостность resume: курсор, пул и currentCardId всегда
     * согласованы (фикс card_15).
     */
    suspend fun saveSession(snapshot: SessionSnapshot)

    /** Пометить сессию завершённой ([SessionStatus.COMPLETED]). */
    suspend fun completeSession(sessionId: SessionId)

    /**
     * Установить текущую карточку по её первичному ключу [cardId] (★ НЕ по индексу).
     *
     * Это устраняет класс ошибок, когда курсор и реальная карточка расходились.
     */
    suspend fun setCurrentCard(sessionId: SessionId, cardId: CardId)

    /** Добавить карточку в множество показанных ([SessionSnapshot.shownCardIds]). */
    suspend fun markCardShown(sessionId: SessionId, cardId: CardId)

    /** Обновить счётчики правильных/неправильных/подсказок сессии. */
    suspend fun updateProgress(sessionId: SessionId, correct: Int, incorrect: Int, hint: Int)

    /** Удалить сессию целиком (снимок + связанные данные). */
    suspend fun deleteSession(sessionId: SessionId)
}
