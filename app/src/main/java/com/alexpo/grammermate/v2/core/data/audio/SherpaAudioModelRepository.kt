package com.alexpo.grammermate.v2.core.data.audio

import android.content.Context
import com.alexpo.grammermate.domain.audio.AudioModelRepository
import com.alexpo.grammermate.domain.audio.DownloadProgress
import com.alexpo.grammermate.domain.audio.ModelStatus
import com.alexpo.grammermate.domain.model.LanguageId
import com.alexpo.grammermate.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data-слой адаптер [AudioModelRepository] — управление скачиванием/распаковкой
 * моделей Sherpa-ONNX (TTS per-language, ASR Whisper+VAD) и Bluetooth-микрофоном.
 *
 * **Архитектурная роль.** Как и [SherpaAudioRepository], это граница инверсии
 * зависимости: домен ([AudioModelRepository]) не знает про `.tar.bz2` архивы,
 * `HttpURLConnection`, `filesDir` и bluetooth-роутинг. Этот класс прячет всё
 * это за чистым интерфейсом; Hilt привязывает интерфейс к этому адаптеру
 * (см. `di/RepositoryModule`).
 *
 * ## Статус миграции (Фаза 9 — изоляция, НЕ полная миграция v1)
 *
 * Полные legacy-менеджеры (`data/TtsModelManager.kt`, `data/AsrModelManager.kt`
 * с распаковкой `.tar.bz2`, metered-проверкой, повторами и т.д.) живут в
 * `app/legacy-src`, который **не входит** в compile source-set v2. Поэтому этот
 * адаптер — **архитектурный каркас**: контракт [AudioModelRepository]
 * реализован и компилируется, а тяжёлая логика сетевой загрузки/распаковки
 * оставлена как задокументированные `TODO` со ссылками на legacy. Лёгкие части
 * (запрос статуса по файлам, persist bluetooth-флага) реализованы полноценно.
 *
 * ### Что РЕАЛИЗОВАНО сейчас:
 *  - [getTtsModelStatus] — по наличию каталога `filesDir/tts/`.
 *  - [getAsrModelStatus] — по манифесту файлов Whisper+VAD (стабильные имена).
 *  - [observeBluetoothMic] / [setBluetoothMic] — persist флага в `AppConfig`
 *    через [SettingsRepository].
 *
 * ### Что TODO (со ссылкой на legacy):
 *  - [downloadTtsModel] — сетевая загрузка + распаковка `.tar.bz2` TTS-модели.
 *  - [downloadAsrModel] — сетевая загрузка VAD + Whisper + распаковка.
 *  - Точный per-language манифест TTS-файлов (реестр legacy TtsModelRegistry).
 *
 * @param context application context (для `filesDir`).
 * @param settingsRepository конфиг приложения — persist `useBluetoothMic`.
 *
 * @see AudioModelRepository доменный контракт.
 * @see SherpaAudioRepository воспроизведение/распознавание (другой порт).
 */
@Singleton
class SherpaAudioModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : AudioModelRepository {

    // ── Скачивание моделей ────────────────────────────────────────────────

    /**
     * Скачать TTS-модель для [languageId]. Каркас `Flow<DownloadProgress>`
     * (status-проверка → Done если готово, иначе эмитит Failed с TODO) готов;
     * сетевая загрузка/распаковка — TODO.
     *
     * TODO(phase-9-migration): портировать логику из legacy
     *   `data/TtsModelManager.download()`:
     *   1. По [languageId] → `TtsModelSpec` (URL, archivePrefix, requiredFiles)
     *      из legacy `data/TtsModelRegistry.kt`.
     *   2. `HttpURLConnection` со стримингом прогресса → [DownloadProgress.Downloading]
     *      (legacy TtsModelManager; v1 использует java.net + tar.bz2 stream-extract).
     *   3. Распаковка `.tar.bz2` в `filesDir/tts/${spec.modelDirName}/` →
     *      [DownloadProgress.Extracting], затем [DownloadProgress.Initializing]
     *      и [DownloadProgress.Done] после проверки requiredFiles.
     *   4. Корректная отмена (Flow-collector cancelled → прервать коннект,
     *      удалить частичный файл). Metered-проверку НЕ делать здесь — это UI.
     *
     * Сейчас: если модель уже на диске → `Done`; иначе → `Failed(IllegalStateException)`.
     */
    override fun downloadTtsModel(languageId: LanguageId): Flow<DownloadProgress> = flow {
        if (getTtsModelStatus(languageId) == ModelStatus.READY) {
            emit(DownloadProgress.Done)
            return@flow
        }
        // TODO(phase-9-migration): портировать сетевую загрузку/распаковку TTS
        //   из legacy TtsModelManager (см. KDoc выше).
        @Suppress("UNUSED_VARIABLE")
        val lang = languageId.value
        emit(
            DownloadProgress.Failed(
                IllegalStateException(
                    "TTS model download not yet ported (lang=${languageId.value}). " +
                        "See legacy app/legacy-src/.../data/TtsModelManager.kt + TtsModelRegistry.kt"
                )
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Скачать ASR-модель (VAD + Whisper). Каркас `Flow<DownloadProgress>`
     * (status-проверка → Done если готово, иначе Failed с TODO); сетевая
     * загрузка/распаковка — TODO.
     *
     * TODO(phase-9-migration): портировать логику из legacy
     *   `data/AsrModelManager.downloadVad()` + `downloadAsr()`:
     *   1. Сначала VAD (~2 МБ): plain `.onnx` файл → `filesDir/asr/vad/`.
     *   2. Затем Whisper (~375 МБ): `.tar.bz2` → stream-extract в
     *      `filesDir/asr/whisper-small/` (legacy AsrModelRegistry.defaultModel).
     *   3. Прогресс по байтам → [DownloadProgress.Downloading];
     *      распаковка → [DownloadProgress.Extracting];
     *      проверка requiredFiles → [DownloadProgress.Initializing] → [DownloadProgress.Done].
     *   4. Отмена коллектора → прервать + удалить частичные файлы.
     *
     * Сейчас: если готово → `Done`; иначе → `Failed(IllegalStateException)`.
     */
    override fun downloadAsrModel(): Flow<DownloadProgress> = flow {
        if (getAsrModelStatus() == ModelStatus.READY) {
            emit(DownloadProgress.Done)
            return@flow
        }
        // TODO(phase-9-migration): портировать сетевую загрузку/распаковку ASR
        //   из legacy AsrModelManager (см. KDoc выше).
        emit(
            DownloadProgress.Failed(
                IllegalStateException(
                    "ASR model download not yet ported. " +
                        "See legacy app/legacy-src/.../data/AsrModelManager.kt + AsrModelRegistry.kt"
                )
            )
        )
    }.flowOn(Dispatchers.IO)

    // ── Статус моделей ────────────────────────────────────────────────────

    /**
     * Статус TTS-модели для [languageId]. **Реализовано**: `READY` если
     * per-language манифест (`TtsModelSpec.requiredFiles` + `requiredDirs`,
     * ненулевой размер) полностью присутствует на диске, иначе `NOT_DOWNLOADED`.
     *
     * Использует тот же manifest, что [SherpaAudioRepository.isTtsAvailable]
     * (AC-11) — единый источник правды в [TtsModelRegistry]. `ERROR`/
     * `DOWNLOADING` пока не различаются (`NOT_DOWNLOADED` покрывает «нет/неполно»).
     */
    override suspend fun getTtsModelStatus(languageId: LanguageId): ModelStatus {
        val ready = TtsModelRegistry.isAvailable(context.filesDir, languageId.value)
        return if (ready) ModelStatus.READY else ModelStatus.NOT_DOWNLOADED
    }

    /**
     * Статус ASR-модели (Whisper + VAD). **Реализовано** — через
     * [AsrModelManifest.isAvailable] (`asr/whisper-small/` + `asr/vad/silero_vad.onnx`).
     * Regression-lock AC-12 — единый источник правды с [SherpaAudioRepository.isAsrAvailable].
     */
    override suspend fun getAsrModelStatus(): ModelStatus {
        val ready = AsrModelManifest.isAvailable(context.filesDir)
        return if (ready) ModelStatus.READY else ModelStatus.NOT_DOWNLOADED
    }

    // ── Bluetooth микрофон ────────────────────────────────────────────────

    /**
     * Реактивно наблюдать за флагом `useBluetoothMic` из [AppConfig].
     *
     * TODO(phase-9-migration): фактический bluetooth SCO-роутинг (start/stop
     *   bluetooth audio) живёт в legacy `shared/audio/BluetoothAudioRouter.kt`
     *   и выполняется на стороне записи ASR ([SherpaAudioRepository]) — здесь
     *   мы лишь отражаем пользовательский preference. При миграции можно
     *   расширить порт, чтобы он эмитил ещё и состояние подключения SCO.
     */
    override fun observeBluetoothMic(): Flow<Boolean> =
        settingsRepository.observeAppConfig()
            // AppConfig.useBluetoothMic — preference пользователя (true = хотим bt).
            .map { it.useBluetoothMic }

    /**
     * Persist флага использования Bluetooth-микрофона в [AppConfig] через
     * [SettingsRepository.updateAppConfig]. **Реализовано.**
     *
     * Сам SCO-роутинг (запуск/останов bluetooth-audio) выполняется на стороне
     * записи ASR при следующем `recognizeSpeech` — здесь хранится только
     * preference (см. legacy `BluetoothAudioRouter`).
     */
    override suspend fun setBluetoothMic(enabled: Boolean) {
        settingsRepository.updateAppConfig { it.copy(useBluetoothMic = enabled) }
    }

    companion object {
        private const val TAG = "SherpaAudioModelRepository"
    }
}
