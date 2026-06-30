package com.alexpo.grammermate.v2.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alexpo.grammermate.v2.core.data.local.dao.UserContentDao
import com.alexpo.grammermate.v2.core.data.local.entity.MigrationFlagEntity
import com.alexpo.grammermate.v2.core.domain.model.AppConfig
import com.alexpo.grammermate.v2.core.domain.model.HintLevel
import com.alexpo.grammermate.v2.core.domain.model.ThemeMode
import com.alexpo.grammermate.v2.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * DataStore-реализация [SettingsRepository] — key-value настройки приложения.
 *
 * [AppConfig] хранится в [DataStore]<[Preferences]>: каждое поле — отдельный
 * [Preferences.Key], что позволяет точечно обновлять отдельные настройки без
 * перезаписи всего конфига и устойчиво к конкурентным правкам.
 *
 * Особенности реализации:
 *  - **Clamp-диапазоны**: при чтении ([prefsToConfig]) и записи
 *    ([applyClamp]) применяются диапазоны из KDoc [AppConfig]
 *    (ttsSpeed 0.5..1.5, ruTextScale 1.0..2.0, sessionSize 3..1000,
 *    bgVocabSentencePauseMs 500..6000). clamp применяется на обоих путях, чтобы
 *    некорректные данные (от старой версии/миграции) нормализовались при первом
 *    доступе, а не только при следующем сохранении.
 *  - **enum↔String**: [HintLevel]/[ThemeMode] хранятся как строки, маппятся через
 *    `valueOf` с безопасным дефолтом при битом значении.
 *  - **Миграции** ([setMigrationFlag]/[isMigrationDone]): хранятся в Room через
 *    [MigrationFlagEntity] (DAO `migratable_files`), а не в DataStore. Решение:
 *    флаги миграции должны быть атомарны с самими миграциями данных в БД — иначе
 *    между «сбитым» DataStore-флагом и реально мигрированной БД возникнет
 *    рассогласование. Поэтому инжектируется [UserContentDao].
 *
 * @property dataStore DataStore настроек (один на приложение, см. `DataModule`).
 * @property userContentDao DAO для флагов миграции (таблица `migratable_files`).
 */
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val userContentDao: UserContentDao,
) : SettingsRepository {

    // ── Чтение конфига ─────────────────────────────────────────────────────────

    /** Реактивный конфиг с применением clamp-диапазонов — для подписки UI. */
    override fun observeAppConfig(): Flow<AppConfig> =
        dataStore.data.map(::prefsToConfig)

    /** Текущий конфиг (с clamp); первый эмисс DataStore — текущее значение. */
    override suspend fun getAppConfig(): AppConfig =
        prefsToConfig(dataStore.data.first())

    /**
     * Атомарно обновить конфиг.
     *
     * [transform] применяется к текущему значению внутри `dataStore.edit`,
     * затем результат перезаписывает все ключи (с clamp). Чтение-модификация-запись
     * выполняется под блокировкой DataStore — без гонок.
     */
    override suspend fun updateAppConfig(transform: (AppConfig) -> AppConfig) {
        dataStore.edit { prefs ->
            val current = prefsToConfig(prefs)
            val next = applyClamp(transform(current))
            writeConfig(prefs, next)
        }
    }

    // ── Флаги миграции (Room: migratable_files) ────────────────────────────────

    override suspend fun setMigrationFlag(key: String, done: Boolean) {
        userContentDao.setMigrationFlag(MigrationFlagEntity(key, done, nowMs()))
    }

    /** true, если флаг есть и `done=true`; false, если флага нет или done=false. */
    override suspend fun isMigrationDone(key: String): Boolean =
        userContentDao.getMigrationFlag(key) == true

    // ── Маппинг Preferences ↔ AppConfig (с clamp) ──────────────────────────────

    /** Читает [Preferences] в [AppConfig], применяя дефолты и clamp-диапазоны. */
    private fun prefsToConfig(prefs: Preferences): AppConfig {
        val testMode = prefs[KEY_TEST_MODE] ?: false
        val sessionSize = prefs[KEY_SESSION_SIZE] ?: DEFAULT_SESSION_SIZE
        val hintLevel = parseHintLevel(prefs[KEY_HINT_LEVEL])
        val ttsSpeed = prefs[KEY_TTS_SPEED] ?: DEFAULT_TTS_SPEED
        val ruTextScale = prefs[KEY_RU_TEXT_SCALE] ?: DEFAULT_RU_TEXT_SCALE
        val voiceAutoStart = prefs[KEY_VOICE_AUTO_START] ?: true
        val uiLanguage = prefs[KEY_UI_LANGUAGE] ?: "system"
        val themeMode = parseThemeMode(prefs[KEY_THEME_MODE])
        val useOfflineAsr = prefs[KEY_USE_OFFLINE_ASR] ?: false
        val useBluetoothMic = prefs[KEY_USE_BLUETOOTH_MIC] ?: false
        val vocabSprintLimit = prefs[KEY_VOCAB_SPRINT_LIMIT] ?: 20
        val eliteSizeMultiplier = prefs[KEY_ELITE_SIZE_MULTIPLIER] ?: 1.25
        val clickableWordHints = prefs[KEY_CLICKABLE_WORD_HINTS] ?: true
        val bgVocabSentencePauseMs = prefs[KEY_BG_VOCAB_SENTENCE_PAUSE_MS] ?: DEFAULT_BG_VOCAB_PAUSE_MS
        val appVersion = prefs[KEY_APP_VERSION] ?: 0
        return applyClamp(
            AppConfig(
                testMode = testMode,
                sessionSize = sessionSize,
                hintLevel = hintLevel,
                ttsSpeed = ttsSpeed,
                ruTextScale = ruTextScale,
                voiceAutoStart = voiceAutoStart,
                uiLanguage = uiLanguage,
                themeMode = themeMode,
                useOfflineAsr = useOfflineAsr,
                useBluetoothMic = useBluetoothMic,
                vocabSprintLimit = vocabSprintLimit,
                eliteSizeMultiplier = eliteSizeMultiplier,
                clickableWordHints = clickableWordHints,
                bgVocabSentencePauseMs = bgVocabSentencePauseMs,
                appVersion = appVersion,
            ),
        )
    }

    /** Записывает все поля [config] в [prefs] (внутри `dataStore.edit`). */
    private fun writeConfig(prefs: MutablePreferences, config: AppConfig) {
        prefs[KEY_TEST_MODE] = config.testMode
        prefs[KEY_SESSION_SIZE] = config.sessionSize
        prefs[KEY_HINT_LEVEL] = config.hintLevel.name
        prefs[KEY_TTS_SPEED] = config.ttsSpeed
        prefs[KEY_RU_TEXT_SCALE] = config.ruTextScale
        prefs[KEY_VOICE_AUTO_START] = config.voiceAutoStart
        prefs[KEY_UI_LANGUAGE] = config.uiLanguage
        prefs[KEY_THEME_MODE] = config.themeMode.name
        prefs[KEY_USE_OFFLINE_ASR] = config.useOfflineAsr
        prefs[KEY_USE_BLUETOOTH_MIC] = config.useBluetoothMic
        prefs[KEY_VOCAB_SPRINT_LIMIT] = config.vocabSprintLimit
        prefs[KEY_ELITE_SIZE_MULTIPLIER] = config.eliteSizeMultiplier
        prefs[KEY_CLICKABLE_WORD_HINTS] = config.clickableWordHints
        prefs[KEY_BG_VOCAB_SENTENCE_PAUSE_MS] = config.bgVocabSentencePauseMs
        prefs[KEY_APP_VERSION] = config.appVersion
    }

    /**
     * Применяет clamp-диапазоны [AppConfig] (см. KDoc модели).
     *
     * Возвращает новую нормализованную копию. Вызывается на обоих путях
     * (чтение и запись), чтобы битые значения из миграции/старой версии
     * нормализовались при первом доступе.
     */
    private fun applyClamp(config: AppConfig): AppConfig = config.copy(
        ttsSpeed = config.ttsSpeed.coerceIn(MIN_TTS_SPEED, MAX_TTS_SPEED),
        ruTextScale = config.ruTextScale.coerceIn(MIN_RU_TEXT_SCALE, MAX_RU_TEXT_SCALE),
        sessionSize = config.sessionSize.coerceIn(MIN_SESSION_SIZE, MAX_SESSION_SIZE),
        bgVocabSentencePauseMs = config.bgVocabSentencePauseMs.coerceIn(
            MIN_BG_VOCAB_PAUSE_MS, MAX_BG_VOCAB_PAUSE_MS,
        ),
    )

    // ── Вспомогательное: enum↔String ───────────────────────────────────────────

    /**
     * Безопасный enum↔String маппинг для [HintLevel].
     * Битое/неизвестное → дефолт [HintLevel.EASY].
     */
    private fun parseHintLevel(raw: String?): HintLevel =
        raw?.takeUnless { it.isBlank() }
            ?.let { runCatching { HintLevel.valueOf(it) }.getOrNull() }
            ?: HintLevel.EASY

    /**
     * Безопасный enum↔String маппинг для [ThemeMode].
     * Битое/неизвестное → дефолт [ThemeMode.SYSTEM].
     */
    private fun parseThemeMode(raw: String?): ThemeMode =
        raw?.takeUnless { it.isBlank() }
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM

    private fun nowMs(): Long = System.currentTimeMillis()

    private companion object {
        // ── Preferences keys (по одному на поле AppConfig) ───────────────────
        private val KEY_TEST_MODE = booleanPreferencesKey("testMode")
        private val KEY_SESSION_SIZE = intPreferencesKey("sessionSize")
        private val KEY_HINT_LEVEL = stringPreferencesKey("hintLevel")
        private val KEY_TTS_SPEED = floatPreferencesKey("ttsSpeed")
        private val KEY_RU_TEXT_SCALE = floatPreferencesKey("ruTextScale")
        private val KEY_VOICE_AUTO_START = booleanPreferencesKey("voiceAutoStart")
        private val KEY_UI_LANGUAGE = stringPreferencesKey("uiLanguage")
        private val KEY_THEME_MODE = stringPreferencesKey("themeMode")
        private val KEY_USE_OFFLINE_ASR = booleanPreferencesKey("useOfflineAsr")
        private val KEY_USE_BLUETOOTH_MIC = booleanPreferencesKey("useBluetoothMic")
        private val KEY_VOCAB_SPRINT_LIMIT = intPreferencesKey("vocabSprintLimit")
        private val KEY_ELITE_SIZE_MULTIPLIER = doublePreferencesKey("eliteSizeMultiplier")
        private val KEY_CLICKABLE_WORD_HINTS = booleanPreferencesKey("clickableWordHints")
        private val KEY_BG_VOCAB_SENTENCE_PAUSE_MS = longPreferencesKey("bgVocabSentencePauseMs")
        private val KEY_APP_VERSION = intPreferencesKey("appVersion")

        // ── Clamp-диапазоны (из KDoc AppConfig) ───────────────────────────────
        private const val MIN_TTS_SPEED = 0.5f
        private const val MAX_TTS_SPEED = 1.5f
        private const val MIN_RU_TEXT_SCALE = 1.0f
        private const val MAX_RU_TEXT_SCALE = 2.0f
        private const val MIN_SESSION_SIZE = 3
        private const val MAX_SESSION_SIZE = 1000
        private const val MIN_BG_VOCAB_PAUSE_MS = 500L
        private const val MAX_BG_VOCAB_PAUSE_MS = 6000L

        // ── Дефолты (дублируют дефолты data class AppConfig) ──────────────────
        private const val DEFAULT_SESSION_SIZE = 10
        private const val DEFAULT_TTS_SPEED = 1.0f
        private const val DEFAULT_RU_TEXT_SCALE = 1.0f
        private const val DEFAULT_BG_VOCAB_PAUSE_MS = 500L
    }
}
