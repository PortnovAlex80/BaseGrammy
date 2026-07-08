package com.alexpo.grammermate.v2.core.data.audio

import com.alexpo.grammermate.domain.audio.RecognitionEvent
import com.alexpo.grammermate.domain.model.AsrState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Тестовый шов (seam) над ASR-движком — граница инверсии зависимости между
 * [SherpaAudioRepository] и нативной реализацией [AsrEngineWrapper].
 *
 * **Зачем этот интерфейс.** SRS-003 §2.4 фиксирует `AsrEngineWrapper :
 * AsrEnginePort`. Репозиторий зависит от этого интерфейса (НЕ от конкретного
 * класса), поэтому unit-тесты адаптера подставляют fake-реализацию (AC-8 DoD:
 * "adapter unit test with fake AsrEngine emits full chain"), не дотрагиваясь до
 * нативного Sherpa-ONNX/`AudioRecord`, которые не работают на чистой JVM.
 * Hilt привязывает интерфейс к [AsrEngineWrapper] в production (см. `di/`).
 *
 * Контракт `streamRecognition` эмитит доменные [RecognitionEvent] (`Partial`* →
 * `EndpointDetected` → `Final`); [SherpaAudioRepository.recognizeSpeech]
 * добавляет вперед `ListeningStarted` (FR-6). Все методы — `suspend`/`Flow`,
 * ни один не блокирует поток надолго (NFR-4/NFR-5).
 *
 * @see AsrEngineWrapper production-реализация поверх Sherpa-ONNX.
 */
interface AsrEnginePort {

    /** Реактивное состояние движка (FR-16). */
    val state: StateFlow<AsrState>

    /** Готов ли движок к записи (`AsrState.READY`). */
    val isReady: Boolean

    /**
     * Инициализировать ASR-движок (Whisper Small + Silero VAD) для [language].
     * Idempotent; переводит [state] в `READY` (или `ERROR`). FR-10 pre-check.
     */
    suspend fun initialize(language: String = "en")

    /** Сменить язык распознавания без перезагрузки модели (runtime, FR-6). */
    fun setLanguage(language: String)

    /**
     * Стриминг-распознавание речи: открыть `AudioRecord`, кормить чанки в VAD,
     * эмитить [RecognitionEvent.Partial] (промежуточные гипотезы, ≥ 0),
     * [RecognitionEvent.EndpointDetected] (VAD нашёл паузу конца фразы) и
     * [RecognitionEvent.Final] (итоговый текст после `decode`/`getResult`).
     * При ошибке/отсутствии речи — [RecognitionEvent.Failed] (NFR-3 graceful).
     *
     * AC-8: `recognizeSpeech` должен эмитить полную цепочку до `Final`, НЕ
     * `Started → Failed`. AC-8 допускает `Partial ≥ 0` (legacy движок не делал
     * streaming-гипотез — декодирование было batch; частичные придут с AC-10
     * streaming-pipeline). Этот контракт `Flow` уже допускает их появление.
     *
     * Отмена коллекктора прерывает запись (NFR-5): `AudioRecord.stop()+release()`
     * в `finally`. Реальная нативная логика — в [AsrEngineWrapper] (перенос legacy
     * `AsrEngine.recordAndTranscribe`, строки 232-344).
     *
     * @param maxDurationMs жёсткий потолок записи (default 10 с, FR-7).
     */
    fun streamRecognition(maxDurationMs: Long = 10_000): Flow<RecognitionEvent>

    /** Остановить активную запись (FR-15, идемпотентен). */
    fun stopRecording()

    /** Освободить все нативные ресурсы (recognizer + vad). */
    fun release()
}
