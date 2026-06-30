package com.alexpo.grammermate.v2.core.domain.repository

import com.alexpo.grammermate.v2.core.domain.model.AppConfig
import kotlinx.coroutines.flow.Flow

/**
 * Key-value настройки приложения.
 *
 * [AppConfig] хранится единым документом; UI подписывается на него реактивно
 * через [observeAppConfig]. Точечные миграции после апдейта отмечаются
 * флагами ([setMigrationFlag]/[isMigrationDone]), чтобы не выполняться повторно.
 */
interface SettingsRepository {

    /** Реактивный конфиг приложения — для подписки UI. */
    fun observeAppConfig(): Flow<AppConfig>

    /** Текущий конфиг приложения (или дефолт при отсутствии). */
    suspend fun getAppConfig(): AppConfig

    /**
     * Атомарно обновить конфиг: [transform] применяется к текущему значению,
     * результат сохраняется. Гарантирует чтение-модификация-запись без гонок.
     */
    suspend fun updateAppConfig(transform: (AppConfig) -> AppConfig)

    /**
     * Отметить, выполнена ли одноразовая миграция [key].
     *
     * @param key идентификатор миграции (например, "migrate_yaml_to_room_v1").
     * @param done true — миграция выполнена.
     */
    suspend fun setMigrationFlag(key: String, done: Boolean)

    /** true, если миграция [key] уже выполнена. */
    suspend fun isMigrationDone(key: String): Boolean
}
