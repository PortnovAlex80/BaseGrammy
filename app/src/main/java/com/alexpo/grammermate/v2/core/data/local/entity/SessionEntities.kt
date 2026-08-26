package com.alexpo.grammermate.v2.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ForeignKey.Companion.CASCADE

/**
 * Entities сессии — резюмируемое состояние тренировки.
 *
 * ★ Ключевая идея архитектуры: [currentCardId] — это **первичный ключ текущей
 * карточки** (PK карточки), а НЕ индекс в массиве пула. Это устраняет баг v1
 * (`card_15`): при resume позиция восстанавливается по стабильному ID, а не по
 * порядковому номеру, который может «съехать» при пересборке пула (hidden/
 * sessionSize). Пул и множество показанных карточек хранятся отдельными
 * таблицами ([SessionCardEntity], [SessionShownCardEntity]) для атомарного
 * сохранения и одного SELECT при resume.
 *
 * Resume = один атомарный SELECT с LEFT JOIN session_cards (GROUP_CONCAT).
 * Валидация: если пул пересобрался и `currentCardId` в нём нет — явный recovery,
 * не подмена (см. ROOM_SCHEMA.md).
 *
 * Enum-поля домена (TrainingMode, SessionStatus, SessionState) хранятся как
 * [String]; маппинг enum ↔ String — в repository-реализации.
 *
 * @see <a href="../../../../../../../../docs/v2-architecture/ROOM_SCHEMA.md">ROOM_SCHEMA.md</a>
 */

/**
 * Состояние тренировочной сессии.
 *
 * @property id                        ID сессии ("<mode>:<packId>:<lessonId>" или "<mode>:<packId>").
 * @property packId                    пак сессии.
 * @property lessonId                  урок сессии, либо null (для drill/daily).
 * @property mode                      режим (LESSON / VERB_DRILL / DAILY_TRANSLATE / ...).
 * @property subLessonIndex            активный под-урок.
 * @property cursorIndex               позиция в пуле.
 * @property currentCardId             ★ PK текущей карточки (НЕ индекс!), либо null.
 * @property selectedTense             выбранное время (для verb drill), либо null.
 * @property selectedGroup             выбранная группа, либо null.
 * @property selectedPerson            выбранное лицо, либо null.
 * @property status                    статус (ACTIVE / PAUSED / COMPLETED).
 * @property state                     состояние шага (ACTIVE / HINT_SHOWN).
 * @property correctCount              счётчик верных ответов.
 * @property incorrectCount            счётчик неверных ответов.
 * @property hintCount                 сколько подсказок использовано.
 * @property incorrectAttemptsForCard  неверных попыток по текущей карточке.
 * @property completedSubLessonCount   завершено под-уроков.
 * @property activeTimeMs              суммарное активное время (мс).
 * @property voiceActiveMs             активное время голосового ввода (мс).
 * @property voiceWordCount            слов, распознанных голосом.
 * @property startedAtMs               epoch-мс старта сессии.
 * @property updatedAtMs               epoch-мс последнего обновления.
 * @property revision                  ★ монотонная ревизия снимка (schema v2,
 *                                     Фаза 2 плана): optimistic-concurrency токен —
 *                                     [SessionDao.saveSnapshot] отвергает запись,
 *                                     если ревизия снимка ≠ stored + 1.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val packId: String,
    val lessonId: String?,
    val mode: String,
    val subLessonIndex: Int,
    val cursorIndex: Int,
    val currentCardId: String?,
    val selectedTense: String?,
    val selectedGroup: String?,
    val selectedPerson: String?,
    val status: String,
    val state: String,
    val correctCount: Int,
    val incorrectCount: Int,
    val hintCount: Int,
    val incorrectAttemptsForCard: Int,
    val completedSubLessonCount: Int,
    val activeTimeMs: Long,
    val voiceActiveMs: Long,
    val voiceWordCount: Int,
    val startedAtMs: Long,
    val updatedAtMs: Long,
    val revision: Long = 0L,
)

/**
 * Карточка в пуле сессии — детерминированный порядок выдачи.
 *
 * Составной PK (sessionId + ord) делает порядок уникальным в рамках сессии.
 * Удаляется каскадно вместе с сессией.
 *
 * @property sessionId ID сессии (FK → [SessionEntity], CASCADE).
 * @property ord       детерминированный порядковый номер в пуле.
 * @property cardId    ID карточки (FK → [CardEntity], ссылка без принудительного FK).
 */
@Entity(
    tableName = "session_cards",
    primaryKeys = ["sessionId", "ord"],
    indices = [Index("sessionId"), Index("cardId")],
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = CASCADE)]
)
data class SessionCardEntity(
    val sessionId: String,
    val ord: Int,
    val cardId: String,
)

/**
 * Показанная в сессии карточка — множество «уже выданных».
 *
 * Составной PK (sessionId + cardId) гарантирует уникальность показа.
 * Используется для проверки shown-set при resume.
 *
 * @property sessionId ID сессии (FK → [SessionEntity], CASCADE).
 * @property cardId    ID показанной карточки.
 * @property shownAtMs epoch-мс момента показа.
 */
@Entity(
    tableName = "session_shown_cards",
    primaryKeys = ["sessionId", "cardId"],
    indices = [Index("cardId")],
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = CASCADE)]
)
data class SessionShownCardEntity(
    val sessionId: String,
    val cardId: String,
    val shownAtMs: Long,
)
