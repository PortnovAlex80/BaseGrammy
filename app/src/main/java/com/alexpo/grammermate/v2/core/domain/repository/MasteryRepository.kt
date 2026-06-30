package com.alexpo.grammermate.v2.core.domain.repository

import com.alexpo.grammermate.v2.core.domain.model.CardId
import com.alexpo.grammermate.v2.core.domain.model.LessonId
import com.alexpo.grammermate.v2.core.domain.model.LessonMastery
import com.alexpo.grammermate.v2.core.domain.model.PackId
import com.alexpo.grammermate.v2.core.domain.srs.SrsCardState
import kotlinx.coroutines.flow.Flow

/**
 * Освоенность уроков и интервальное повторение (SRS).
 *
 * Хранит, какие карточки урока показаны и как часто, считает «цветок» освоения
 * и дату следующего повтора. [observeMastery] даёт UI реактивный «цветок»,
 * [getDueCards] выбирает карточки к повторению по расписанию SRS.
 */
interface MasteryRepository {

    /** Состояние освоения урока или null, если по нему ещё нет данных. */
    suspend fun getMastery(packId: PackId, lessonId: LessonId): LessonMastery?

    /** Реактивное состояние освоения урока — для подписки UI на «цветок». */
    fun observeMastery(packId: PackId, lessonId: LessonId): Flow<LessonMastery?>

    /**
     * Зафиксировать показ карточки урока в момент [nowMs].
     *
     * Обновляет счётчики показов, множество показанных и интервал SRS,
     * возвращает свежее состояние освоения.
     */
    suspend fun recordCardShow(packId: PackId, lessonId: LessonId, cardId: CardId, nowMs: Long): LessonMastery

    /** Пометить урок завершённым в момент [nowMs] (если ещё не завершён). */
    suspend fun markLessonCompleted(packId: PackId, lessonId: LessonId, nowMs: Long)

    /**
     * Карточки, подлежащие повторению к моменту [nowMs], не более [limit] штук.
     * SRS-выборка для режима Review.
     */
    suspend fun getDueCards(nowMs: Long, limit: Int): List<CardId>

    /** Сохранить агрегированное SRS-состояние урока (после повтора). */
    suspend fun updateSrsState(packId: PackId, lessonId: LessonId, srs: SrsCardState)
}
