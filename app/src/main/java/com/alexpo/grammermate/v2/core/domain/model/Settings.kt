package com.alexpo.grammermate.v2.core.domain.model

/**
 * Модели настроек приложения — key-value конфигурация пользователя.
 *
 * Чистый Kotlin, без Room-аннотаций. [AppConfig] агрегирует все настраиваемые
 * параметры; data-слой (DataStore) хранит их как единый документ. Источник
 * истины для UI-состояния настроек.
 */

/**
 * Конфигурация приложения — все пользовательские настройки в одном значении.
 *
 * Поля соответствуют v1 `AppConfigStore` (config.yaml). Data-слой обязан
 * применять clamp-диапазоны при сохранении/загрузке:
 *   - [ttsSpeed]            0.5..1.5
 *   - [ruTextScale]         1.0..2.0
 *   - [sessionSize]         3..1000
 *   - [bgVocabSentencePauseMs] 500..6000
 *
 * @property testMode                 режим автопринятия ответов (для дебага).
 * @property sessionSize              размер тренировочной сессии (карточек).
 * @property hintLevel                уровень подсказок во время практики.
 * @property ttsSpeed                 скорость TTS (0.5..1.5).
 * @property ruTextScale              масштаб шрифта русского текста (1.0..2.0).
 * @property voiceAutoStart           авто-старт голосового ввода.
 * @property uiLanguage               язык интерфейса ("system", "en", "ru").
 * @property themeMode                режим темы (LIGHT/DARK/SYSTEM).
 * @property useOfflineAsr            использовать оффлайн-распознавание речи.
 * @property useBluetoothMic          использовать Bluetooth-микрофон.
 * @property vocabSprintLimit         лимит карточек в vocab-sprint.
 * @property eliteSizeMultiplier      множитель размера elite-сессии.
 * @property clickableWordHints       кликабельные слова-подсказки в ответах.
 * @property bgVocabSentencePauseMs   пауза (мс) между предложениями в bg-vocab (500..6000).
 * @property appVersion               версия приложения, для которой актуален конфиг.
 */
data class AppConfig(
    val testMode: Boolean = false,
    val sessionSize: Int = 10,
    val hintLevel: HintLevel = HintLevel.EASY,
    val ttsSpeed: Float = 1.0f,
    val ruTextScale: Float = 1.0f,
    val voiceAutoStart: Boolean = true,
    val uiLanguage: String = "system",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useOfflineAsr: Boolean = false,
    val useBluetoothMic: Boolean = false,
    val vocabSprintLimit: Int = 20,
    val eliteSizeMultiplier: Double = 1.25,
    val clickableWordHints: Boolean = true,
    val bgVocabSentencePauseMs: Long = 500L,
    val appVersion: Int = 0,
)
