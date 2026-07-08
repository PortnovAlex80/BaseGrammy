package com.alexpo.grammermate.v2.core.data.audio

import android.content.Context
import android.media.AudioRecord
import android.util.Log
import com.alexpo.grammermate.domain.audio.RecognitionEvent
import com.alexpo.grammermate.domain.model.AsrState
import com.alexpo.grammermate.domain.model.InitPhase
import com.k2fsa.sherpa.onnx.HomophoneReplacerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над Sherpa-ONNX `OfflineRecognizer` (Whisper Small multilingual) +
 * `Vad` (Silero) + `AudioRecord`, с VAD-based endpointing.
 *
 * **Архитектурная роль.** Чистый data-слойный адаптер, инжектируемый в
 * [SherpaAudioRepository]. Хостит state machine [AsrState] и всю нативную
 * логику записи/распознавания. Домен про Sherpa/Android не знает — он видит
 * только `Flow<RecognitionEvent>`.
 *
 * Перенос из legacy `data/AsrEngine.kt` (v1). SRS-003 §2.4, FR-6, FR-9, FR-10,
 * FR-11, FR-15, FR-16.
 *
 * **GAP C4 / AC-28 (HomophoneReplacerConfig hook).** v1 передавал
 * `HomophoneReplacerConfig()` (no-op) в `OfflineRecognizerConfig`. Конфиг
 * `hr: HomophoneReplacerConfig` зарезервирован здесь (поле [homophoneReplacerHook]),
 * чтобы будущая homophone-correction (например, для it/ru) могла подключаться
 * без смены конструктора. Body-задача AC-28 проверяет сохранность хука.
 *
 * ## Статус (scaffold, E03 task #467)
 *
 * Скелет: публичная поверхность зафиксирована, нативная логика — TODO. Body-задачи
 * (AC-8..AC-13) реализуют TODO внутри фиксированного контракта.
 *
 * @param context application context (для `filesDir`).
 * @param memoryChecker pre-check памяти перед load Whisper (~375 МБ). FR-10.
 * @param bluetoothRouter маршрутизация на Bluetooth-микрофон при записи. FR-11.
 */
@Singleton
class AsrEngineWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryChecker: MemoryChecker,
    private val bluetoothRouter: BluetoothAudioRouter,
) : AsrEnginePort {
    private val _state = MutableStateFlow(AsrState.IDLE)

    /** Реактивное состояние движка (FR-16). */
    override val state: StateFlow<AsrState> = _state

    /** Готов ли движок к записи. */
    override val isReady: Boolean
        get() = _state.value == AsrState.READY

    /**
     * Человекочитаемое описание последней ошибки, или null. SRS FR-16 —
     * `Error(reason)` хранит причину (OOM, native lib error, timeout).
     */
    var errorMessage: String? = null
        private set

    /**
     * Текущий язык распознавания (Whisper language code, напр. "en"/"it"/"ru").
     * Выводится из ISO 639-1 через `AsrModelRegistry.whisperLanguageCode`.
     */
    var currentLanguage: String = "en"
        private set

    /**
     * GAP C4 hook — конфиг homophone-replacer'а, передаваемый в
     * `OfflineRecognizerConfig.hr`. Зарезервирован для будущей
     * homophone-correction без смены конструктора. AC-28 проверяет сохранность.
     * По умолчанию no-op (`HomophoneReplacerConfig()`), как в legacy.
     */
    @Volatile
    var homophoneReplacerHook: HomophoneReplacerConfig = HomophoneReplacerConfig()
        private set

    /**
     * Активный [AudioRecord] (null — запись не идёт). Доступен для
     * [stopRecording] / cancellation-cleanup (NFR-5, FR-15). Перенос legacy
     * `AsrEngine.audioRecord`.
     */
    @Volatile
    private var audioRecord: AudioRecord? = null

    /**
     * Callback прогресса инициализации (фаза + проценты). Соответствие legacy
     * `onInitializing`.
     */
    var onInitializing: ((InitPhase, Int) -> Unit)? = null
        private set

    fun setInitializingCallback(callback: ((InitPhase, Int) -> Unit)?) {
        onInitializing = callback
    }

    /**
     * Инициализировать ASR-движок (Whisper Small + Silero VAD).
     *
     * TODO(body, AC-8+AC-9+AC-10): перенести логику из legacy
     *   `data/AsrEngine.kt:65-170`:
     *   1. State CAS: IDLE/ERROR → INITIALIZING (детерминированные переходы).
     *   2. Проверка файлов Whisper (`small-encoder/decoder.int8.onnx` +
     *      `small-tokens.txt`) и VAD (`silero_vad.onnx`).
     *   3. `MemoryChecker.hasEnoughMemory(~800 МБ)` pre-check (FR-10, NFR-2).
     *   4. `OfflineRecognizer(null, config)` + `Vad(null, vadConfig)`.
     *      config.featConfig=FeatureConfig(), config.hr=[homophoneReplacerHook].
     *   5. State → READY. При ошибке → ERROR + errorMessage (OOM/native/unknown).
     *
     * Сейчас: no-op skeleton (оставляет IDLE).
     *
     * @param language ISO 639-1 код (default "en"). Маппится в Whisper-код.
     */
    override suspend fun initialize(language: String) {
        // TODO(body): port legacy AsrEngine.initialize (see KDoc).
        currentLanguage = language
        Log.d(TAG, "initialize($language) — skeleton no-op")
    }

    /**
     * Сменить язык распознавания без перезагрузки файлов (runtime, FR).
     *
     * TODO(body): перенести из legacy `data/AsrEngine.kt:177-225` —
     *   `recognizer.setConfig(newConfig)` обновляет только language-параметр.
     *
     * @param language ISO 639-1 код (напр. "en", "it", "ru").
     */
    override fun setLanguage(language: String) {
        // TODO(body): port legacy AsrEngine.setLanguage (see KDoc).
        currentLanguage = language
        Log.d(TAG, "setLanguage($language) — skeleton no-op")
    }

    /**
     * Стриминг-распознавание речи — реализация [AsrEnginePort.streamRecognition].
     *
     * Перенос legacy `AsrEngine.recordAndTranscribe` (строки 232-344), но вместо
     * возврата `String` эмитит доменные [RecognitionEvent]:
     *   1. State → RECORDING; `vad.reset()`.
     *   2. `AudioRecord(VOICE_RECOGNITION, 16 кГц, MONO, PCM_16BIT)` — открыть
     *      (buffer ≥ 3200 = 100 мс при 16 кГц). AC-10 locks параметры.
     *   3. Цикл: читать чанки по 1600 short (100 мс) → ÷32768 → `FloatArray` →
     *      `vad.acceptWaveform`; копить speech-сегменты (`vad.front()/pop()`).
     *      При `isSpeechDetected()` → ранее эмитит [RecognitionEvent.Partial]
     *      (streaming-гипотеза). AC-8 допускает `Partial ≥ 0`; legacy движок
     *      делал batch-decode, поэтому streaming-Partials появляются с AC-10.
     *   4. Когда была речь и `!isSpeechDetected()` → VAD нашёл паузу конца фразы
     *      (endpoint) → эмит [RecognitionEvent.EndpointDetected], выход из цикла.
     *   5. State → RECOGNIZING; `rec.createStream()` + `stream.acceptWaveform` +
     *      `rec.decode(stream)` + `rec.getResult(stream)` → итоговый текст.
     *   6. State → READY; эмит [RecognitionEvent.Final] (пустой текст = no speech,
     *      но поток всё равно корректно завершается через Final, не Failed).
     *   7. Cancellation-safe: `audioRecord.stop()+release()` в `finally` (NFR-5).
     *
     * Если recognizer/vad не инициализированы (AC-9 model-missing путь) —
     * эмитится [RecognitionEvent.Failed] без native crash (NFR-3 graceful).
     */
    override fun streamRecognition(maxDurationMs: Long): Flow<RecognitionEvent> = flow {
        // TODO(body, AC-9+AC-10): native recognizer/vad wiring (legacy AsrEngine.kt
        //   65-170, 232-344). Пока нативные объекты не инициализируются, поток
        //   честно сообщает об отсутствии движка (Failed), а НЕ Started→Failed:
        //   ListeningStarted репозиторий эмитит раньше; здесь движок решает финал.
        if (!isReady) {
            emit(
                RecognitionEvent.Failed(
                    IllegalStateException(
                        "ASR engine not initialized (state=${_state.value}); " +
                            "call initialize(language) and ensure model files present."
                    )
                )
            )
            return@flow
        }
        // Real capture→endpoint→decode→Final loop is ported in AC-10
        // (AudioRecord + Sherpa OfflineRecognizer/Vad). This contract surface is
        // verified end-to-end via the fake-engine adapter test (AC-8 DoD).
        // TODO(body, AC-10): port legacy recordAndTranscribe capture loop here,
        //   emitting Partial/EndpointDetected/Final as described above.
        emit(
            RecognitionEvent.Failed(
                IllegalStateException(
                    "ASR capture/decode not yet ported (maxMs=$maxDurationMs); " +
                        "see legacy app/legacy-src/.../data/AsrEngine.kt"
                )
            )
        )
    }.flowOn(Dispatchers.Default)

    /**
     * Записать голос с микрофона + VAD endpointing + распознать.
     *
     * TODO(body, AC-8+AC-10): перенести логику из legacy
     *   `data/AsrEngine.kt:232-344`:
     *   1. State → RECORDING. `vad.reset()`.
     *   2. `AudioRecord(VOICE_RECOGNITION, 16 кГц, MONO, PCM_16BIT)` — открыть.
     *   3. При включённом Bluetooth — `bluetoothRouter.startBluetoothAudio()` (FR-11).
     *   4. Цикл: читать 100 мс чанки → `vad.acceptWaveform(floatChunk)` →
     *      собирать speech-сегменты; эмитить Partial (через callback, если
     *      streaming-вариант) и EndpointDetected на паузе.
     *   5. State → RECOGNIZING; `rec.decode(stream)` + `rec.getResult(stream)`.
     *   6. State → READY. Возврат текста (пустой = no speech).
     *   7. Cancellation-safe: `audioRecord.stop()+release()` в finally (NFR-5).
     *
     * @param maxDurationMs таймаут записи (default 10 с).
     * @return распознанный текст, или пустая строка при отсутствии/ошибке.
     */
    suspend fun recordAndTranscribe(maxDurationMs: Long = 10_000): String {
        // TODO(body): port legacy AsrEngine.recordAndTranscribe (see KDoc).
        Log.d(TAG, "recordAndTranscribe(maxMs=$maxDurationMs) — skeleton no-op, returns empty")
        errorMessage = "ASR not yet ported"
        return ""
    }

    /**
     * Остановить активную запись (FR-15, идемпотентен).
     *
     * TODO(body): перенести из legacy `data/AsrEngine.kt:349-358` —
     *   `audioRecord?.stop()+release()` в try/catch, state → READY если RECORDING.
     */
    override fun stopRecording() {
        // TODO(body): port legacy AsrEngine.stopRecording (see KDoc).
        if (_state.value == AsrState.RECORDING) {
            _state.value = AsrState.READY
        }
        Log.d(TAG, "stopRecording() — skeleton no-op")
    }

    /**
     * Освободить все нативные ресурсы (recognizer + vad).
     *
     * TODO(body): перенести из legacy `data/AsrEngine.kt:367-374` —
     *   `stopRecording()`, `recognizer?.release()`, `vad?.release()`, state → IDLE.
     */
    override fun release() {
        // TODO(body): port legacy AsrEngine.release (see KDoc).
        stopRecording()
        _state.value = AsrState.IDLE
        Log.d(TAG, "release() — skeleton no-op")
    }

    companion object {
        private const val TAG = "AsrEngineWrapper"

        /** Частота дискретизации записи (Whisper/VAD — 16 кГц, SRS FR-8/AC-10). */
        const val SAMPLE_RATE = 16000
    }
}
