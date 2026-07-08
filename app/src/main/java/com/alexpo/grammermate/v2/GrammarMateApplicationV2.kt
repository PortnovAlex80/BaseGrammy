package com.alexpo.grammermate.v2

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GrammarMateApplicationV2 : Application() {

    override fun onCreate() {
        super.onCreate()
        // Q3 greenfield data: YamlToRoomMigrator больше не активный путь данных
        // (класс помечен @Deprecated, AC-17). Startup больше не запускает миграцию.
        // Флаг миграции SettingsRepository.isMigrationDone(...) остаётся в контракте
        // порта — новый код его не вызывает, но значение персистируется в Room.
    }
}
