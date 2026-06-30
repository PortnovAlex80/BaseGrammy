package com.alexpo.grammermate.v2

import android.app.Application
import android.util.Log
import com.alexpo.grammermate.v2.core.data.migration.MigrationResult
import com.alexpo.grammermate.v2.core.data.migration.YamlToRoomMigrator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GrammarMateApplicationV2 : Application() {

    @Inject
    lateinit var migrator: YamlToRoomMigrator

    // Application-scoped scope, переживающий смену конфигурации; SupervisorJob — чтобы
    // падение одной дочерней корутины не отменяло всё дерево.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Запуск миграции в фоне при первом запуске v2.
        // Идемпотентен: повторные запуски — no-op по флагу migration_yaml_v1_to_v2 в БД.
        // Все файловые операции внутри migrator — на Dispatchers.IO.
        appScope.launch {
            when (val result = migrator.migrateIfNeeded()) {
                is MigrationResult.Migrated -> Log.i(
                    "GrammarMateV2",
                    "YAML→Room migration done: $result",
                )
                MigrationResult.NotNeeded -> Log.d(
                    "GrammarMateV2",
                    "YAML→Room migration not needed (already done or fresh install).",
                )
                is MigrationResult.Failed -> Log.e(
                    "GrammarMateV2",
                    "YAML→Room migration failed; legacy .yaml left intact.",
                    result.error,
                )
            }
        }
    }
}
