package com.alexpo.grammermate.domain.audio

import com.alexpo.grammermate.domain.model.LanguageId
import kotlinx.coroutines.flow.Flow

/**
 * Доменный порт для управления жизненным циклом аудио-моделей Sherpa-ONNX:
 * скачивание/распаковка TTS- и ASR-моделей, запрос статуса готовности и
 * управление маршрутизацией на Bluetooth-микрофон.
 *
 * **Зачем отдельный порт от [AudioRepository].** В v1 всё это было намешано в
 * `AudioCoordinator`/`TtsModelManager`/`AsrModelManager` вместе с логикой
 * воспроизведения. В v2 мы разделяем «произнести/распознать»
 * ([AudioRepository]) и «доставить/подготовить модели» (этот порт):
 *   - UI-экран настроек зависит от `AudioModelRepository` (скачать модель,
 *     показать статус, переключить bluetooth-микрофон);
 *   - UI тренировки зависит от `AudioRepository` (озвучить/распознать).
 * Это соответствует разным сценариям и позволяет независимо подменять
 * реализации в тестах.
 *
 * Домен не знает, что модели — это `.tar.bz2` архивы Sherpa-ONNX, не знает про
 * `HttpURLConnection`, `filesDir` и `AudioManager`/Sco bluetooth. Всё это —
 * детали реализации data-слоя (`SherpaAudioModelRepository`), инжектируемой
 * через Hilt.
 *
 * @see DownloadProgress реактивный прогресс скачивания модели.
 * @see ModelStatus дискретный статус модели на диске.
 * @see AudioRepository высокоуровневое воспроизведение/распознавание.
 */
interface AudioModelRepository {

    /**
     * Скачать и распаковать TTS-модель для языка [languageId]. Возвращает `Flow`
     * прогресса (`Downloading` % → `Extracting` → `Initializing` → `Done` или
     * `Failed`). Повторный вызов для уже готовой модели — no-op с немедленным
     * `Done`.
     *
     * Реализация должна корректно обрабатывать отмену коллеккора (прерывать
     * сетевую загрузку и удалять частичный файл). Проверка metered-сети здесь
     * **не** выполняется — это забота UI (домен про сеть ничего не знает).
     *
     * @param languageId целевой язык TTS-модели.
     */
    fun downloadTtsModel(languageId: LanguageId): Flow<DownloadProgress>

    /**
     * Скачать и распаковать ASR-модель (Whisper + VAD). Возвращает `Flow`
     * прогресса. Модель одна на все языки (multilingual), поэтому язык не
     * передаётся. VAD (~2 МБ) качается первым, затем основная модель (~375 МБ).
     * Повторный вызов для готовой модели — no-op с `Done`.
     */
    fun downloadAsrModel(): Flow<DownloadProgress>

    /**
     * Текущий статус TTS-модели для [languageId] на диске. Дешёвая операция —
     * проверка наличия/размера файлов, без загрузки нативной модели в память.
     */
    suspend fun getTtsModelStatus(languageId: LanguageId): ModelStatus

    /**
     * Текущий статус ASR-модели (Whisper + VAD) на диске.
     */
    suspend fun getAsrModelStatus(): ModelStatus

    /**
     * Реактивно наблюдать за состоянием Bluetooth-микрофона (включён/выключен).
     * Эмитит текущее значение при подписке и каждое изменение. UI использует
     * это для отображения переключателя в настройках аудио.
     */
    fun observeBluetoothMic(): Flow<Boolean>

    /**
     * Включить/выключить использование Bluetooth-микрофона для ASR.
     * При включении реализация при следующей записи ASR попытается
     * проложить маршрут через bluetooth SCO; при выключении — вернёт встроенный
     * микрофон. Значениеpersistируется (см. `AppConfig.useBluetoothMic`).
     *
     * @param enabled true — использовать bluetooth-микрофон.
     */
    suspend fun setBluetoothMic(enabled: Boolean)
}

/**
 * Реактивный прогресс скачивания/подготовки модели (TTS или ASR).
 *
 * Эмитится реализациями [downloadTtsModel] / [downloadAsrModel]. Состояния
 * следуют в порядке: `Downloading`(ы) → `Extracting` → `Initializing` → `Done`,
 * либо на любом шаге → `Failed`. `percent` — целое 0..100 для UI-прогрессбара.
 */
sealed interface DownloadProgress {
    /** Идёт сетевая загрузка; [percent] — 0..100. */
    data class Downloading(val percent: Int) : DownloadProgress

    /** Архив скачан, распаковывается на диск. */
    data object Extracting : DownloadProgress

    /** Файлы готовы, инициализируется/проверяется нативный движок. */
    data object Initializing : DownloadProgress

    /** Модель полностью готова к использованию. Терминальное состояние успеха. */
    data object Done : DownloadProgress

    /** Сбой на любом этапе. Терминальное состояние ошибки. */
    data class Failed(val error: Throwable) : DownloadProgress
}

/**
 * Дискретный статус модели на диске (для экрана настроек «модели загружены?»).
 *
 * В отличие от `DownloadProgress` (поток событий скачивания), это —
 * точечный снимок состояния по запросу: готова ли модель прямо сейчас.
 */
enum class ModelStatus {
    /** Модель не скачана (файлы отсутствуют на диске). */
    NOT_DOWNLOADED,

    /** Идёт скачивание/распаковка (не запрашивайте параллельную загрузку). */
    DOWNLOADING,

    /** Модель готова к использованию (файлы на диске, корректного размера). */
    READY,

    /** Ошибка (битые/неполные файлы после прерванной загрузки). */
    ERROR,
}
