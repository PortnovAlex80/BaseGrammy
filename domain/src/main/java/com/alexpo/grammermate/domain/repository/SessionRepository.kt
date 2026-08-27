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
 * ([SessionSnapshot]): упорядоченный пул + множество показанных + текущая
 * карточка по PK. Resume и persist выполняются атомарно (одна транзакция
 * Room), что исключает рассинхрон состояния, из-за которого после
 * возобновления показывалась чужая карточка.
 *
 * **Контракт recovery (Фаза 2 плана стабилизации 2026-08-26):** load возвращает
 * сохранённый снимок КАК ЕСТЬ — включая `currentCardId` вне пула. Никакой
 * молчаливой замены текущей карточки; явное восстановление — зона домена
 * (`SessionEngine.restoreCurrentCardIfNeeded`).
 *
 * **Контракт ревизий (Фаза 2):** [saveSession] принимает снимок, чья
 * `snapshot.revision` равна `stored.revision + 1`; сохранение с любой другой
 * ревизией отвергается [StaleSessionRevisionException] без изменения
 * хранилища. Новая строка (нет существующей) сохраняется с ревизией снимка.
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
        pendingCardIds: List<CardId> = emptyList(),
        sessionSize: Int = 10,
        selectedTense: String? = null,
        selectedGroup: String? = null,
        selectedPerson: String? = null,
    ): SessionSnapshot

    /**
     * Resume: атомарно загрузить снимок сессии (session + pool + shown одной
     * транзакцией чтения).
     *
     * @return снимок целиком как сохранён (recovery — зона Engine), либо null,
     *         если сессии нет. Битые enum-значения — typed failure data-слоя.
     */
    suspend fun loadSession(sessionId: SessionId): SessionSnapshot?

    /**
     * Атомарно сохранить весь снимок в одной транзакции.
     *
     * Гарантирует целостность resume: пул и currentCardId всегда согласованы
     * (фикс card_15). Обычный Submit/Next не переписывает пул/shown целиком —
     * применяются только изменившиеся части (hot updates, Фаза 2).
     *
     * @throws com.alexpo.grammermate.domain.session.StaleSessionRevisionException
     *         если ревизия снимка не следует за хранимой (устаревший писатель).
     */
    suspend fun saveSession(snapshot: SessionSnapshot)

    /** Пометить сессию завершённой ([SessionStatus.COMPLETED]). */
    suspend fun completeSession(sessionId: SessionId)

    /**
     * Удалить сессию целиком (снимок + связанные данные).
     *
     * Фаза 7 плана: granular writer API (setCurrentCard/markCardShown/
     * updateProgress) удалён — единственный путь записи это атомарный
     * [saveSession] (hot-updates Фазы 2); presentation никогда не собирал
     * снимок по частям (ADR-001).
     */
    suspend fun deleteSession(sessionId: SessionId)
}
