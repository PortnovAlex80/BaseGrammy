package com.alexpo.grammermate.v2

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GrammarMateApplicationV2 : Application() {
    override fun onCreate() {
        super.onCreate()
        // TODO: запуск YamlToRoomMigrator при первом запуске (Фаза 4)
    }
}
