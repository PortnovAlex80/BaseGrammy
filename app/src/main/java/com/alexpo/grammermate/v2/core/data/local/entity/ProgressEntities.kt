package com.alexpo.grammermate.v2.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entities прогресса — серия дней (streak), drill-прогресс, прогресс глав,
 * дневные курсоры. Отражает изменяемое во времени состояние пользователя.
 *
 * Enum-поля домена (PracticeType и т. д.) хранятся как [String]; маппинг —
 * в repository-реализации.
 *
 * @see <a href="../../../../../../../../docs/v2-architecture/ROOM_SCHEMA.md">ROOM_SCHEMA.md</a>
 */

/**
 * Серия дней (streak) по языку — один streak на язык.
 *
 * @property languageId              язык (PK).
 * @property currentStreak           текущая непрерывная серия (дней).
 * @property longestStreak           рекордная серия.
 * @property lastCompletionDateMs    epoch-мс последнего завершённого дня, либо null.
 * @property totalSubLessonsCompleted всего завершённых подуроков.
 * @property todayFireCount          сколько «огоньков» зажжено сегодня.
 * @property lastFireDateMs          epoch-мс последнего «огонька», либо null.
 */
@Entity(tableName = "streaks")
data class StreakEntity(
    @PrimaryKey val languageId: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val lastCompletionDateMs: Long?,
    val totalSubLessonsCompleted: Int,
    val todayFireCount: Int,
    val lastFireDateMs: Long?,
)

/**
 * Тип практики, выполненной сегодня — отдельная таблица для множества типов.
 *
 * Составной PK (languageId + practiceType) допускает несколько записей на язык.
 *
 * @property languageId   язык.
 * @property practiceType тип (TRANSLATION / VOCAB / VERB).
 */
@Entity(
    tableName = "streak_practice_today",
    primaryKeys = ["languageId", "practiceType"]
)
data class StreakPracticeTodayEntity(
    val languageId: String,
    val practiceType: String,
)

/**
 * Прогресс drill-тренировки (глаголы / aux / словарь) по паку.
 *
 * [everShownCardIdsJson] и [todayShownCardIdsJson] хранят Set<String> как
 * JSON-строку (см. [com.alexpo.grammermate.v2.core.data.local.Converters]).
 * Уникальный индекс (packId, drillType) — один прогресс на pak+тип.
 *
 * @property id                    составной ключ "<packId>:<drillType>".
 * @property packId                пак тренировки.
 * @property drillType             тип drill (VERB / AUX / VOCAB).
 * @property comboKey              ключ комбинации (group:tense для verb / verb:tense для aux), либо null.
 * @property totalCards            всего карточек в drill.
 * @property everShownCardIdsJson  показанные за всё время карточки (JSON Set<String>).
 * @property todayShownCardIdsJson показанные сегодня карточки (JSON Set<String>).
 * @property lastDate              дата последней тренировки (ISO yyyy-MM-dd), либо null.
 * @property cursor                позиция курсора (для vocab drill).
 * @property updatedAtMs           epoch-мс последнего обновления.
 */
@Entity(tableName = "drill_progress", indices = [Index(value = ["packId", "drillType"], unique = true)])
data class DrillProgressEntity(
    @PrimaryKey val id: String,
    val packId: String,
    val drillType: String,
    val comboKey: String?,
    val totalCards: Int,
    val everShownCardIdsJson: String,
    val todayShownCardIdsJson: String,
    val lastDate: String?,
    val cursor: Int,
    val updatedAtMs: Long,
)

/**
 * Прогресс главы — счётчики начатых/завершённых уроков.
 *
 * Составной PK (packId + chapterId).
 *
 * @property packId            пак главы.
 * @property chapterId         глава.
 * @property lessonsStarted    сколько уроков начато.
 * @property lessonsCompleted  сколько уроков завершено.
 * @property lastAccessedMs    epoch-мс последнего обращения к главе.
 */
@Entity(tableName = "chapter_progress", primaryKeys = ["packId", "chapterId"])
data class ChapterProgressEntity(
    val packId: String,
    val chapterId: String,
    val lessonsStarted: Int,
    val lessonsCompleted: Int,
    val lastAccessedMs: Long,
)

/**
 * Курсор дневной нормы — состояние прохождения дневного набора карточек.
 *
 * Запоминает смещения и «замороженные» наборы карточек первой сессии дня,
 * чтобы дневная норма оставалась стабильной в течение суток. JSON-поля
 * (CardId-списки) сериализуются через
 * [com.alexpo.grammermate.v2.core.data.local.Converters] (List<String>).
 *
 * @property packId                                пак (PK).
 * @property sentenceOffset                        смещение в списке карточек-предложений.
 * @property currentLessonIndex                    индекс текущего урока дневной нормы.
 * @property verbOffset                            смещение в списке карточек-глаголов.
 * @property firstSessionDate                      дата первой сессии (ISO), либо null.
 * @property firstSessionSentenceCardIdsJson       карточки-предложения первой сессии (JSON List<String>).
 * @property firstSessionVerbCardIdsJson           карточки-глаголы первой сессии (JSON List<String>).
 * @property firstSessionLessonId                  урок первой сессии, либо null.
 * @property updatedAtMs                           epoch-мс последнего обновления.
 */
@Entity(tableName = "daily_cursors", primaryKeys = ["packId"])
data class DailyCursorEntity(
    val packId: String,
    val sentenceOffset: Int,
    val currentLessonIndex: Int,
    val verbOffset: Int,
    /** Курсор vocab-блока (schema v4, срез 4 Фазы 4). */
    val vocabOffset: Int = 0,
    val firstSessionDate: String?,
    val firstSessionSentenceCardIdsJson: String,
    val firstSessionVerbCardIdsJson: String,
    val firstSessionLessonId: String?,
    val updatedAtMs: Long,
)
