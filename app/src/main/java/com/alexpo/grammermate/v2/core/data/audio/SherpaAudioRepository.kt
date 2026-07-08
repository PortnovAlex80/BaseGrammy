package com.alexpo.grammermate.v2.core.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.alexpo.grammermate.R
import com.alexpo.grammermate.domain.audio.AudioEvent
import com.alexpo.grammermate.domain.audio.AudioRepository
import com.alexpo.grammermate.domain.audio.RecognitionEvent
import com.alexpo.grammermate.domain.audio.SoundEffect
import com.alexpo.grammermate.domain.model.LanguageId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data-слой адаптер [AudioRepository] поверх нативной библиотеки **Sherpa-ONNX**
 * (`com.k2fsa.sherpa.onnx.*`) и Android `SoundPool`.
 *
 * **Архитектурная роль.** Это граница инверсии зависимости: домен
 * ([AudioRepository]) не знает про Sherpa-ONNX/Android, а этот класс реализует
 * доменный интерфейс, скрывая все нативные/платформенные детали за ним. Hilt
 * привязывает интерфейс к этому адаптеру (см. `di/RepositoryModule`); в тестах
 * домена подставляется fake-реализация `AudioRepository`, не дотрагиваясь до
 * нативного кода.
 *
 * ## Статус миграции (E03 — scaffold фиксирует API surface)
 *
 * Helper-обёртки (`TtsEngineWrapper`, `AsrEngineWrapper`, `SegmentPlayer`,
 * `BluetoothAudioRouter`, `MemoryChecker`) перенесены в v2 data-слой как скелеты
 * (scaffold E03 task #467) и инжектируются в конструктор (SRS-003 §2.1).
 * API surface адаптера и helper-классов зафиксирован; 24 body-задачи (AC-1..AC-29)
 * реализуют тяжёлую нативную логику **внутри** этого фиксированного контракта.
 * Доменные порты [AudioRepository]/[AudioModelRepository] **immutable**
 * (regression-lock E01) — не меняются.
 *
 * ### Что РЕАЛИЗОВАНО сейчас:
 *  - [playSoundEffect] — полностью (SoundPool + готовые mp3 из `res/raw`).
 *  - [isAsrAvailable] — проверка файлов Whisper+VAD по известному манифесту
 *    (стабильные имена файлов, одна модель на все языки).
 *  - [stop] — делегирует в обёртки (пока no-op скелеты).
 *
 * ### Что TODO (body-задачи реализуют внутри фиксированного контракта):
 *  - [speak] — делегировать в `ttsEngine.speak` → `Flow<AudioEvent>`.
 *  - [recognizeSpeech] — делегировать в `asrEngine.recordAndTranscribe` → `Flow<RecognitionEvent>`.
 *  - [isTtsAvailable] — точный per-language манифест файлов TTS-модели.
 *
 * @param context application context (для доступа к `filesDir`, `SoundPool`, raw).
 * @param ttsEngine обёртка над Sherpa-ONNX OfflineTts (FR-2/FR-3/FR-4).
 * @param asrEngine обёртка над Whisper+VAD (FR-6/FR-9/FR-11).
 * @param memoryChecker pre-check памяти (FR-10, NFR-2).
 * @param segmentPlayer sequential playback (FR-12, shared с E08/E09 — SRS §5).
 *
 * @see AudioRepository доменный контракт.
 */
@Singleton
class SherpaAudioRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsEngine: TtsEngineWrapper,
    private val asrEngine: AsrEngineWrapper,
    private val memoryChecker: MemoryChecker,
    private val segmentPlayer: SegmentPlayer,
) : AudioRepository {

    // ── SoundPool (SFX) ────────────────────────────────────────────────────
    // Инициализируется единожды для синглтона; клипы загружаются асинхронно,
    // готовые sampleId попадают в [loadedSamples]. Логика портирована из
    // legacy AudioCoordinator.init (строки 91-95, 129-133).

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .build()

    private val correctSoundId: Int = soundPool.load(context, R.raw.voicy_correct_answer, 1)
    private val errorSoundId: Int = soundPool.load(context, R.raw.voicy_bad_answer, 1)

    /** SampleId готовых (загруженных в память) клипов SoundPool. */
    private val loadedSamples = mutableSetOf<Int>()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                synchronized(loadedSamples) { loadedSamples.add(sampleId) }
            } else {
                Log.w(TAG, "SoundPool sample $sampleId failed to load (status=$status)")
            }
        }
    }

    // ── AudioRepository ────────────────────────────────────────────────────

    /**
     * Озвучивание текста. Каркас `Flow<AudioEvent>` готов; тяжёлый путь синтеза
     * делегируется в [ttsEngine] (Sherpa-ONNX `OfflineTts` + `AudioTrack`).
     *
     * TODO(body, AC-2+AC-3+AC-4): делегировать в `ttsEngine`:
     *   1. `ttsEngine.initialize(languageId)` — выбор/загрузка нативной модели
     *      VITS_PIPER/KOKORO из `filesDir/tts/` (legacy TtsEngine.kt:235-348),
     *      с resident LRU-кэром для мгновенного переключения языков
     *      (ResidentTtsCache) и system-TTS fallback для 32-bit ARM /
     *      отсутствующих файлов (legacy TtsEngine.kt:354-431).
     *   2. Эмитить [AudioEvent.Started] после `TtsState.Ready`.
     *   3. `ttsEngine.speak(text, languageId, speed)` — синтез PCM-float через
     *      `generateWithConfigAndCallback` + воспроизведение через `AudioTrack`
     *      (legacy TtsEngine.kt:565-678); по мере генерации можно эмитить
     *      [AudioEvent.Progress] (доля по playbackHeadPosition/totalSamples).
     *   4. По завершению drain'а — [AudioEvent.Completed]; при ошибке —
     *      [AudioEvent.Failed]. Отмена коллекктора (flow) должна вызывать
     *      `ttsEngine.stop()` (legacy TtsEngine.kt:689-706).
     *
     * Сейчас: эмитит `Started`, затем `Failed(IllegalStateException)` — чтобы
     * контракт `Flow<AudioEvent>` был наблюдаем и компилировался без падения
     * на этапе сборки. Body-задача AC-2/AC-3 заменяет TODO-блоком.
     */
    override fun speak(text: String, languageId: LanguageId, speed: Float): Flow<AudioEvent> = flow {
        if (text.isBlank()) return@flow
        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
        emit(AudioEvent.Started)
        // TODO(phase-9-migration): Sherpa-ONNX OfflineTts synthesis — см. KDoc выше.
        @Suppress("UNUSED_VARIABLE")
        val lang = languageId.value
        emit(
            AudioEvent.Failed(
                IllegalStateException(
                    "TTS synthesis not yet ported (lang=${languageId.value}, speed=$safeSpeed). " +
                        "See legacy app/legacy-src/.../data/TtsEngine.kt"
                )
            )
        )
    }

    /**
     * Остановить воспроизведение/запись. Делегирует в обёртки (FR-15, NFR-5).
     *
     * TODO(body, AC-20): `ttsEngine.stop()` (неблокирующий: флаг isStopped +
     * cancel speakJob БЕЗ join — legacy ANR-fix) и `asrEngine.stopRecording()`
     * (`audioRecord.stop()+release()` в try/catch). Обёртки — пока no-op скелеты;
     * body-задача AC-20 реализует <100ms non-blocking контракт.
     */
    override suspend fun stop() {
        // TODO(body): подтвердить <100ms latency после реализации wrapper TODO.
        ttsEngine.stop()
        asrEngine.stopRecording()
        Log.d(TAG, "stop() — delegated to wrappers (tts+asr)")
    }

    /**
     * Распознавание речи (ASR). Каркас `Flow<RecognitionEvent>` готов; тяжёлый
     * путь записи + VAD + Whisper — TODO.
     *
     * TODO(phase-9-migration): делегировать в обёртку над legacy `data/AsrEngine.kt`:
     *   1. Эмитить [RecognitionEvent.ListeningStarted] после `AsrState.READY`.
     *   2. При необходимости переключить язык: `asrEngine.setLanguage(languageId)`
     *      (legacy AsrEngine.kt:177-225) — мультиязычная модель Whisper.
     *   3. `asrEngine.recordAndTranscribe()` в streaming-режиме: открыть
     *      `AudioRecord` (VOICE_RECOGNITION, 16 кГц), кормить чанки в
     *      `Vad.acceptWaveform` (legacy AsrEngine.kt:232-344).
     *   4. По сегментам речи эмитить [RecognitionEvent.Partial]
     *      (промежуточные `getResult`) и [RecognitionEvent.EndpointDetected]
     *      когда VAD нашёл паузу; финал — [RecognitionEvent.Final].
     *   5. Маршрут на Bluetooth-микрофон если включён (через
     *      AudioModelRepository / legacy BluetoothAudioRouter).
     *
     * Сейчас: эмитит `ListeningStarted`, затем `Failed(IllegalStateException)`.
     */
    override fun recognizeSpeech(languageId: LanguageId): Flow<RecognitionEvent> = flow {
        emit(RecognitionEvent.ListeningStarted)
        // TODO(phase-9-migration): Whisper + Silero VAD — см. KDoc выше.
        emit(
            RecognitionEvent.Failed(
                IllegalStateException(
                    "ASR recognition not yet ported (lang=${languageId.value}). " +
                        "See legacy app/legacy-src/.../data/AsrEngine.kt"
                )
            )
        )
    }

    /**
     * Сыграть SFX через `SoundPool`. **Полностью реализовано** — клипы
     * (`voicy_correct_answer`, `voicy_bad_answer`) грузятся в [init], играют
     * мгновенно. Логика портирована из legacy `AudioCoordinator.playSuccessSound`
     * / `playErrorSound` (строки 169-185).
     *
     * Для [SoundEffect.LESSON_COMPLETE] отдельного ресурса пока нет —
     * переиспользуется клип корректного ответа (TODO: добавить `res/raw`).
     */
    override suspend fun playSoundEffect(effect: SoundEffect) {
        val sampleId = when (effect) {
            SoundEffect.CORRECT_ANSWER -> correctSoundId
            SoundEffect.WRONG_ANSWER -> errorSoundId
            SoundEffect.LESSON_COMPLETE -> {
                // TODO(phase-9-migration): добавить отдельный res/raw для LESSON_COMPLETE
                //   (пока переиспользуем correct-answer clip).
                correctSoundId
            }
        }
        val ready = synchronized(loadedSamples) { sampleId in loadedSamples }
        if (ready) {
            soundPool.play(sampleId, 1f, 1f, 0, 0, 1f)
        } else {
            Log.d(TAG, "playSoundEffect($effect): sample $sampleId not loaded yet — skipped")
        }
    }

    /**
     * Готова ли TTS-модель для [languageId]. Каркас — проверяет наличие
     * непустого каталога модели под `filesDir/tts/`.
     *
     * TODO(phase-9-migration): точная проверка по манифесту `requiredFiles`/
     * `requiredDirs` из legacy `data/TtsModelRegistry.kt` (имена каталогов и
     * файлов варьируются по языкам — `vits-piper-en_US-amy-low`, ...). Сейчас
     * проверяется только существование каталога под `tts/`.
     */
    override suspend fun isTtsAvailable(languageId: LanguageId): Boolean {
        val ttsRoot = File(context.filesDir, "tts")
        // TODO(phase-9-migration): сопоставить languageId → TtsModelSpec.modelDirName
        //   из legacy TtsModelRegistry и проверить spec.requiredFiles.
        val hasModelDir = ttsRoot.listFiles()
            ?.any { it.isDirectory && it.listFiles()?.isNotEmpty() == true }
            ?: false
        return hasModelDir
    }

    /**
     * Готова ли ASR-модель (Whisper + VAD). **Реализовано** — манифест файлов
     * стабилен (одна multilingual-модель): `asr/whisper-small/` с тремя файлами
     * + `asr/vad/silero_vad.onnx`. Соответствует legacy `AsrModelRegistry`
     * (defaultModel.requiredFiles + vadModel.requiredFiles).
     */
    override suspend fun isAsrAvailable(): Boolean {
        val asrDir = File(context.filesDir, "asr/whisper-small")
        val whisperReady = ASR_REQUIRED_FILES.all { f ->
            val file = File(asrDir, f)
            file.exists() && file.length() > 0L
        }
        val vadFile = File(context.filesDir, "asr/vad/silero_vad.onnx")
        val vadReady = vadFile.exists() && vadFile.length() > 0L
        return whisperReady && vadReady
    }

    companion object {
        private const val TAG = "SherpaAudioRepository"

        /** Обязательные файлы Whisper Small (legacy AsrModelRegistry.defaultModel.requiredFiles). */
        private val ASR_REQUIRED_FILES =
            listOf("small-encoder.int8.onnx", "small-decoder.int8.onnx", "small-tokens.txt")
    }
}
