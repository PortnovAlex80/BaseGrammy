package com.alexpo.grammermate.v2.di

import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.repository.MasteryRepository
import com.alexpo.grammermate.domain.repository.SessionRepository
import com.alexpo.grammermate.domain.repository.UserContentRepository
import com.alexpo.grammermate.domain.session.SessionEngine
import com.alexpo.grammermate.domain.validation.AnswerValidator
import com.alexpo.grammermate.v2.core.data.repository.RoomSessionCommitCoordinator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Доменные движки/валидаторы — stateless, собираются из репозиториев.
 *
 * Фаза 2 плана стабилизации 2026-08-26: `SessionEngine` собирается с
 * транзакционным координатором [RoomSessionCommitCoordinator] (одна Room-
 * транзакция на составное событие) и hook'ами mastery:
 *  - `onMarkShown` → `MasteryRepository.recordCardShow` — в той же транзакции,
 *    что и персистенция снимка (ADR-001 pre-mortem №3);
 *  - `onSessionCompleted` → `MasteryRepository.markLessonCompleted` — атомарно
 *    со сменой статуса сессии на COMPLETED.
 *
 * WORD_BANK по-прежнему не дёргает mastery (gate в самом Engine,
 * TARGET_ARCHITECTURE §12). Streak/progress-агрегация — Фазы 4/5 (режимы).
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun provideSessionEngine(
        sessionRepository: SessionRepository,
        contentRepository: ContentRepository,
        userContentRepository: UserContentRepository,
        masteryRepository: MasteryRepository,
        commitCoordinator: RoomSessionCommitCoordinator,
    ): SessionEngine = SessionEngine(
        sessionRepository = sessionRepository,
        contentRepository = contentRepository,
        userContentRepository = userContentRepository,
        commitCoordinator = commitCoordinator,
        onMarkShown = { packId, lessonId, cardId, nowMs ->
            lessonId?.let { masteryRepository.recordCardShow(packId, it, cardId, nowMs) }
        },
        onSessionCompleted = { packId, lessonId, nowMs ->
            lessonId?.let { masteryRepository.markLessonCompleted(packId, it, nowMs) }
        },
    )

    @Provides
    @Singleton
    fun provideAnswerValidator(): AnswerValidator = AnswerValidator()
}
