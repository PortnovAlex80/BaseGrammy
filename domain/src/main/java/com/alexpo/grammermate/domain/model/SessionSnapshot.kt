package com.alexpo.grammermate.domain.model

/**
 * Атомарный снимок тренировочной сессии — фикс бага card_15.
 *
 * Раньше состояние сессии было размазано по нескольким полям/файлам, что
 * приводило к рассинхрону: курсор ссылался на один индекс, а пул — на другой,
 * из-за чего после возобновления показывалась чужая карточка (card_15).
 *
 * [SessionSnapshot] — единая целостная единица: пул + множество показанных +
 * текущая карточка по первичному ключу ([currentCardId], а не по индексу).
 * Data-слой (Room) загружает и сохраняет весь снимок в одной транзакции,
 * что гарантирует атомарность resume.
 *
 * Фаза 2 плана стабилизации 2026-08-26 — единый источник истины позиции:
 * `cursorIndex` удалён из снимка (второй источник истины; в БД колонка
 * остаётся и пишется производным значением `pool.indexOf(currentCardId)`).
 * Позиция всегда выводится из [currentCardId] + порядка [poolCardIds].
 *
 * @property sessionId               идентификатор сессии.
 * @property packId                  пак тренировки.
 * @property lessonId                урок (null для drill/daily/помодоро).
 * @property mode                    режим тренировки.
 * @property currentCardId           ★ PK текущей карточки, НЕ индекс.
 * @property status                  жизненный цикл сессии (см. [SessionStatus]).
 * @property state                   состояние шага экрана (см. [SessionState]).
 * @property revision                монотонная ревизия снимка: +1 на каждую
 *                                   durable-мутацию; command-токен для защиты
 *                                   от stale-результатов и double-tap (Фаза 2).
 * @property poolCardIds             упорядоченный пул карточек сессии.
 * @property shownCardIds            множество уже показанных карточек.
 * @property correctCount            счётчик правильных ответов.
 * @property incorrectCount          счётчик неправильных ответов.
 * @property hintCount               сколько раз показывали подсказку.
 * @property completedSubLessonCount сколько подуроков завершено.
 * @property selectedTense           выбранный фильтр времени (drill), либо null.
 * @property selectedGroup           выбранный фильтр группы (drill), либо null.
 * @property selectedPerson          выбранный фильтр лица (drill), либо null.
 * @property startedAtMs             epoch-мс старта сессии.
 * @property updatedAtMs             epoch-мс последнего обновления снимка.
 */
data class SessionSnapshot(
    val sessionId: SessionId,
    val packId: PackId,
    val lessonId: LessonId?,
    val mode: TrainingMode,
    val currentCardId: CardId?,
    val status: SessionStatus,
    val state: SessionState,
    val revision: Long,
    val poolCardIds: List<CardId>,
    val pendingCardIds: List<CardId> = emptyList(),
    val sessionSize: Int = 10,
    val shownCardIds: Set<CardId>,
    val correctCount: Int,
    val incorrectCount: Int,
    val hintCount: Int,
    val completedSubLessonCount: Int,
    val selectedTense: String?,
    val selectedGroup: String?,
    val selectedPerson: String?,
    val startedAtMs: Long,
    val updatedAtMs: Long,
)
