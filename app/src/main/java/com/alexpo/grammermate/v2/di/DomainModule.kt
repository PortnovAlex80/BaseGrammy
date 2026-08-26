package com.alexpo.grammermate.v2.di

import com.alexpo.grammermate.domain.repository.ContentRepository
import com.alexpo.grammermate.domain.repository.SessionRepository
import com.alexpo.grammermate.domain.repository.UserContentRepository
import com.alexpo.grammermate.domain.session.SessionEngine
import com.alexpo.grammermate.domain.validation.AnswerValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Доменные движки/валидаторы — stateless, собираются из репозиториев.
 *
 * Фаза 1 плана стабилизации 2026-08-26: `SessionEngine` становится единственным
 * mutating-путём сессии для presentation (ADR-001). Хук `onMarkShown` остаётся
 * no-op: wiring `MasteryRepository.recordCardShow` входит в транзакционный
 * координатор Фазы 2 (pre-mortem ADR-001 №3 — атомарность не должна остановиться
 * на снимке сессии), а по сигнатуре требует pack/lesson-контекст, которого у
 * хука нет — расширение контракта вместе с Фазой 2.
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
    ): SessionEngine = SessionEngine(
        sessionRepository = sessionRepository,
        contentRepository = contentRepository,
        userContentRepository = userContentRepository,
    )

    @Provides
    @Singleton
    fun provideAnswerValidator(): AnswerValidator = AnswerValidator()
}
