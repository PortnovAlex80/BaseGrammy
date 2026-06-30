package com.alexpo.grammermate.v2.di

import com.alexpo.grammermate.v2.core.data.repository.ContentRepositoryImpl
import com.alexpo.grammermate.v2.core.data.repository.MasteryRepositoryImpl
import com.alexpo.grammermate.v2.core.data.repository.ProgressRepositoryImpl
import com.alexpo.grammermate.v2.core.data.repository.SessionRepositoryImpl
import com.alexpo.grammermate.v2.core.data.repository.SettingsRepositoryImpl
import com.alexpo.grammermate.v2.core.data.repository.UserContentRepositoryImpl
import com.alexpo.grammermate.v2.core.domain.repository.ContentRepository
import com.alexpo.grammermate.v2.core.domain.repository.MasteryRepository
import com.alexpo.grammermate.v2.core.domain.repository.ProgressRepository
import com.alexpo.grammermate.v2.core.domain.repository.SessionRepository
import com.alexpo.grammermate.v2.core.domain.repository.SettingsRepository
import com.alexpo.grammermate.v2.core.domain.repository.UserContentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Привязки интерфейсов репозиториев к их Room/DataStore-реализациям.
 *
 * `@Binds` эффективнее `@Provides` для interface→impl: не требует тела, Hilt
 * генерирует factory напрямую. Все реализации — application-scoped singletons:
 * они stateless (вся mutable state в Room DB, не в объектах).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindContentRepository(impl: ContentRepositoryImpl): ContentRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindMasteryRepository(impl: MasteryRepositoryImpl): MasteryRepository

    @Binds
    @Singleton
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds
    @Singleton
    abstract fun bindUserContentRepository(impl: UserContentRepositoryImpl): UserContentRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
