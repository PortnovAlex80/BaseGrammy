package com.alexpo.grammermate.v2.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.alexpo.grammermate.v2.core.data.local.GrammarMateDatabase
import com.alexpo.grammermate.v2.core.data.local.dao.ContentDao
import com.alexpo.grammermate.v2.core.data.local.dao.DrillDao
import com.alexpo.grammermate.v2.core.data.local.dao.MasteryDao
import com.alexpo.grammermate.v2.core.data.local.dao.ProgressDao
import com.alexpo.grammermate.v2.core.data.local.dao.SessionDao
import com.alexpo.grammermate.v2.core.data.local.dao.UserContentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** DataStore для настроек key-value (один на приложение). */
private val Context.appConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "grammarmate_app_config",
)

/**
 * DI-модуль data-слоя: Room database, DAOs, DataStore.
 *
 * Все singletons — [Singleton] в [SingletonComponent] (application scope).
 * DAOs получаются из одного [GrammarMateDatabase] — поэтому все разделяют
 * одно SQLite-соединение и могут участвовать в общих транзакциях через
 * `db.runInTransaction { ... }` (важно для атомарности).
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GrammarMateDatabase =
        GrammarMateDatabase.build(context)

    @Provides fun provideContentDao(db: GrammarMateDatabase): ContentDao = db.contentDao()

    @Provides fun provideSessionDao(db: GrammarMateDatabase): SessionDao = db.sessionDao()

    @Provides fun provideMasteryDao(db: GrammarMateDatabase): MasteryDao = db.masteryDao()

    @Provides fun provideDrillDao(db: GrammarMateDatabase): DrillDao = db.drillDao()

    @Provides fun provideProgressDao(db: GrammarMateDatabase): ProgressDao = db.progressDao()

    @Provides
    fun provideUserContentDao(db: GrammarMateDatabase): UserContentDao = db.userContentDao()

    @Provides
    @Singleton
    fun provideAppConfigDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.appConfigDataStore

    /**
     * Application-scoped [CoroutineScope] для аудио-компонентов (SRS-003 §2.11).
     *
     * Нужен [BluetoothAudioRouter] для запуска `AudioManager`-коллбэков
     * (`addOnCommunicationDeviceChangedListener` ожидает Executor/scope.launch).
     * [SupervisorJob] — сбой одного коллбэка не отменяет скоуп; [Dispatchers.Main]
     * потому что AudioManager-коллбэки должны выполняться на main thread (SRS §2.11).
     *
     * Помечен [AudioCoroutineScope] qualifier'ом, чтобы не конфликтовать с другими
     * application-scoped CoroutineScope'ами (если появятся).
     */
    @Provides
    @Singleton
    @AudioCoroutineScope
    fun provideAudioCoroutineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

/**
 * Qualifier: application-scoped [CoroutineScope] для аудио-компонентов
 * (BluetoothAudioRouter). SRS-003 §2.11.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AudioCoroutineScope
