package com.alexpo.grammermate.domain.model

import com.alexpo.grammermate.domain.srs.SrsCardState

/**
 * Модели прогресса пользователя.
 *
 * Чистый Kotlin, без Room-аннотаций. Идентификаторы — value classes из [Pack.kt].
 * Сюда попадают изменяемые во времени состояния: освоенность уроков, серия дней,
 * курсоры дневной нормы, история помодоро и т. д.
 */

/**
 * Освоенность урока пользователем.
 *
 * @property packId             пак урока.
 * @property lessonId           урок.
 * @property uniqueCardShows    сколько уникальных карточек показано.
 * @property totalCardShows     всего показов карточек (с повторами).
 * @property lastShowDateMs     epoch-мс последнего показа любой карточки урока.
 * @property intervalStepIndex  индекс шага интервала в упрощённой схеме SRS.
 * @property srsState           агрегированное SRS-состояние урока, либо null.
 * @property dueAtMs            epoch-мс, когда урок снова доступен (0 — доступен).
 * @property completedAtMs      epoch-мс завершения урока, либо null.
 * @property shownCardIds       множество показанных карточек урока.
 * @property cardEncounterCounts сколько раз встречалась каждая карточка.
 */
data class LessonMastery(
    val packId: PackId,
    val lessonId: LessonId,
    val uniqueCardShows: Int,
    val totalCardShows: Int,
    val lastShowDateMs: Long,
    val intervalStepIndex: Int,
    val srsState: SrsCardState?,
    val dueAtMs: Long,
    val completedAtMs: Long?,
    val shownCardIds: Set<CardId>,
    val cardEncounterCounts: Map<CardId, Int>,
)

/**
 * Серия дней (streak) по языку.
 *
 * @property languageId              язык.
 * @property currentStreak           текущая непрерывная серия (дней).
 * @property longestStreak           рекордная серия.
 * @property lastCompletionDateMs    epoch-мс последнего завершённого дня, либо null.
 * @property totalSubLessonsCompleted всего завершённых подуроков.
 * @property completedTypesToday     типы практик, выполненных сегодня.
 * @property todayFireCount          сколько «огоньков» зажжено сегодня.
 * @property lastFireDateMs          epoch-мс последнего «огонька», либо null.
 */
data class StreakData(
    val languageId: LanguageId,
    val currentStreak: Int,
    val longestStreak: Int,
    val lastCompletionDateMs: Long?,
    val totalSubLessonsCompleted: Int,
    val completedTypesToday: Set<PracticeType>,
    val todayFireCount: Int,
    val lastFireDateMs: Long?,
)

/**
 * Прогресс по главе.
 *
 * @property packId            пак главы.
 * @property chapterId         глава.
 * @property lessonsStarted    сколько уроков начато.
 * @property lessonsCompleted  сколько уроков завершено.
 * @property lastAccessedMs    epoch-мс последнего обращения к главе.
 * @property totalLessons      общее число уроков в главе (вычисляется).
 * @property progress          доля завершённых уроков 0..1 (вычисляется).
 */
data class ChapterProgress(
    val packId: PackId,
    val chapterId: ChapterId,
    val lessonsStarted: Int,
    val lessonsCompleted: Int,
    val lastAccessedMs: Long,
) {
    /** Полное число уроков главы (задаётся/обновляется внешним кодом). */
    var totalLessons: Int = 0
        private set

    /**
     * Доля завершённых уроков от [totalLessons] в диапазоне 0..1.
     * Возвращает 0, если [totalLessons] не задан.
     */
    val progress: Float
        get() = if (totalLessons <= 0) 0f else lessonsCompleted.toFloat() / totalLessons.toFloat()

    /** Установить полное число уроков главы (для расчёта [progress]). */
    fun withTotalLessons(total: Int): ChapterProgress = apply { totalLessons = total }
}

/**
 * Прогресс drill-тренировки (глаголы/вспомогательные и т. п.).
 *
 * @property packId            пак тренировки.
 * @property drillType         тип drill (например, "verb", "aux").
 * @property comboKey          ключ комбинации параметров drill, либо null.
 * @property totalCards        всего карточек в drill.
 * @property everShownCardIds  показанные за всё время карточки.
 * @property todayShownCardIds показанные сегодня карточки.
 * @property lastDate          дата последней тренировки (ISO), либо null.
 * @property cursor            позиция курсора в последовательности drill.
 */
data class DrillProgress(
    val packId: PackId,
    val drillType: String,
    val comboKey: String?,
    val totalCards: Int,
    val everShownCardIds: Set<CardId>,
    val todayShownCardIds: Set<CardId>,
    val lastDate: String?,
    val cursor: Int,
)

/**
 * Реактивный прогресс пака по урокам (ADR-002, слой 1: истина — счётчики).
 *
 * `completedLessons` агрегируется из `mastery_states.completedAtMs` — без
 * SRS-математики (лестница/FSRS в агрегате не участвуют до активации слоя 2;
 * см. docs/architecture/decisions/002-srs-single-source-of-truth.md).
 *
 * @property packId           пак.
 * @property totalLessons     всего уроков пака.
 * @property completedLessons уроков с completedAtMs != null.
 */
data class PackLessonProgress(
    val packId: PackId,
    val totalLessons: Int,
    val completedLessons: Int,
) {
    /** Доля завершённых уроков 0..1 (`totalLessons <= 0` → 0). */
    val fraction: Float
        get() = if (totalLessons <= 0) 0f else completedLessons.toFloat() / totalLessons
}

/**
 * Курсор дневной нормы — состояние проходжения дневного набора карточек.
 *
 * @property packId                        пак.
 * @property sentenceOffset                смещение в списке карточек-предложений.
 * @property currentLessonIndex            индекс текущего урока в дневной норме.
 * @property verbOffset                    смещение в списке карточек-глаголов.
 * @property firstSessionDate              дата первой сессии дневной нормы (ISO), либо null.
 * @property firstSessionSentenceCardIds   карточки-предложения первой сессии.
 * @property firstSessionVerbCardIds       карточки-глаголы первой сессии.
 * @property firstSessionLessonId          урок первой сессии, либо null.
 */
data class DailyCursor(
    val packId: PackId,
    val sentenceOffset: Int,
    val currentLessonIndex: Int,
    val verbOffset: Int,
    val firstSessionDate: String?,
    val firstSessionSentenceCardIds: List<CardId>,
    val firstSessionVerbCardIds: List<CardId>,
    val firstSessionLessonId: LessonId?,
)

/**
 * Запись истории помодоро-сессии.
 *
 * @property id               идентификатор записи.
 * @property languageId       язык тренировки.
 * @property packId           пак тренировки, либо null.
 * @property lessonId         урок тренировки, либо null.
 * @property completedAtMs    epoch-мс завершения помодоро.
 * @property durationMinutes  плановая длительность в минутах.
 * @property totalSeconds     фактическое полное время в секундах.
 * @property remainingSeconds сколько секунд осталось на таймере.
 * @property cardsShown       показано карточек.
 * @property cardsCorrect     правильных ответов.
 * @property cardsIncorrect   неправильных ответов.
 * @property wordsPerMinute   темп набора (слов/мин).
 */
data class PomodoroHistoryEntry(
    val id: String,
    val languageId: LanguageId,
    val packId: PackId?,
    val lessonId: LessonId?,
    val completedAtMs: Long,
    val durationMinutes: Int,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val cardsShown: Int,
    val cardsCorrect: Int,
    val cardsIncorrect: Int,
    val wordsPerMinute: Double,
)
