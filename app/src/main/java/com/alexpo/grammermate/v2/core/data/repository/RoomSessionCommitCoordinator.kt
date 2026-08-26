package com.alexpo.grammermate.v2.core.data.repository

import androidx.room.withTransaction
import com.alexpo.grammermate.domain.session.SessionCommitCoordinator
import com.alexpo.grammermate.v2.core.data.local.GrammarMateDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Продакшн-реализация [SessionCommitCoordinator] поверх единой
 * [GrammarMateDatabase] (Фаза 2 плана стабилизации 2026-08-26).
 *
 * `db.withTransaction` открывает внешнюю Room-транзакцию; все вложенные
 * транзакционные DAO-опуляции блока (`SessionDao.saveSnapshot`,
 * `MasteryDao.recordCardShow`) присоединяются к ней. Так составное
 * бизнес-событие — «answer + shown + mastery» либо «completion +
 * markLessonCompleted» — применяется целиком или откатывается целиком
 * (ADR-001 pre-mortem №3: атомарность не останавливается на снимке сессии).
 */
@Singleton
class RoomSessionCommitCoordinator @Inject constructor(
    private val db: GrammarMateDatabase,
) : SessionCommitCoordinator {

    override suspend fun <R> commit(block: suspend () -> R): R =
        db.withTransaction { block() }
}
