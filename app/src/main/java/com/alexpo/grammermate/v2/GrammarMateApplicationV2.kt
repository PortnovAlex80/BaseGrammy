package com.alexpo.grammermate.v2

import android.app.Application
import com.alexpo.grammermate.v2.core.data.packimport.BundledPackSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class GrammarMateApplicationV2 : Application() {

    @Inject
    lateinit var bundledPackSeeder: BundledPackSeeder

    /**
     * Application-scope для фонового seed'а: SupervisorJob — ошибка seed'а не
     * отменяет скоуп; Dispatchers.IO — ZIP-распаковка/Room-write вне main thread.
     */
    private val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Q3 greenfield data: YamlToRoomMigrator больше не активный путь данных
        // (класс помечен @Deprecated, AC-17). Startup больше не запускает миграцию.
        // Флаг миграции SettingsRepository.isMigrationDone(...) остаётся в контракте
        // порта — новый код его не вызывает, но значение персистируется в Room.
        //
        // Фаза 1 плана стабилизации 2026-08-26: fresh install получает bundled-пак
        // (идемпотентно, на IO) — Home реактивно показывает его через
        // ContentRepository.observePacks.
        seedScope.launch { bundledPackSeeder.seedIfNeeded() }
    }
}
