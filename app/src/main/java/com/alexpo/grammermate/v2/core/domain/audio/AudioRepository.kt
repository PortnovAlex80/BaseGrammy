package com.alexpo.grammermate.v2.core.domain.audio

import com.alexpo.grammermate.v2.core.domain.model.LanguageId
import kotlinx.coroutines.flow.Flow

/**
 * Высокоуровневый доменный порт для всего воспроизведения и распознавания
 * звука: TTS-озвучивание текста, ASR-распознавание речи и короткие SFX
 * (правильный/неправильный ответ).
 *
 * **Зачем этот интерфейс в домене.** В v1 аудио было сосредоточено в одном
 * God Object'е — `AudioCoordinator.kt` (939 строк, TTS + ASR + soundpack +
 * bluetooth), который тянул в себя нативную библиотеку Sherpa-ONNX
 * (`com.k2fsa.sherpa.onnx.*`), Android `SoundPool`/`AudioTrack` и т.д. Любой
 * модуль домена/UI, которому нужно было «произнести текст», заражался этой
 * зависимостью. В clean-архитектуре v2 домен **ничего не знает** ни про
 * Sherpa-ONNX, ни про Android: он зависит только от этого чистого интерфейса.
 *
 * Конкретная реализация (Sherpa-ONNX TTS/ASR) живёт в data-слое
 * (`SherpaAudioRepository`) и подключается через Hilt; в unit-тестах вместо неё
 * подставляется fake/mock — домен остаётся тестируемым на чистом JVM.
 *
 * Все операции — реактивные (`Flow`) или `suspend`: ни один метод не блокирует
 * поток. Коллектор `Flow` получает поток дискретных событий (`AudioEvent` /
 * `RecognitionEvent`), что позволяет UI показывать прогресс озвучивания и
 * частичные результаты распознавания.
 *
 * @see AudioEvent события жизненного цикла озвучивания (TTS).
 * @see RecognitionEvent события распознавания речи (ASR).
 * @see SoundEffect звуковые эффекты короткого действия.
 * @see AudioModelRepository скачивание/статус моделей (отдельный порт).
 */
interface AudioRepository {

    /**
     * Озвучить [text] на языке [languageId] со скоростью [speed] (0.5..2.0,
     * по умолчанию 1.0). Возвращает `Flow` событий жизненного цикла озвучки:
     * `Started` → опционально `Progress` → `Completed` (успех) либо
     * `Failed` (ошибка).
     *
     * Коллектор должен быть активен на всю длительность озвучивания: отписка
     * (отмена корутины-коллектора) отменяет и саму озвучку. Чтобы остановить
     * воспроизведение явным вызовом (а не отменой коллекктора), используйте
     * [stop].
     *
     * Пустой или blank [text] — no-op (Flow немедленно завершается без эмитов).
     *
     * @param text текст для синтеза речи.
     * @param languageId язык озвучивания (модель должна быть загружена, иначе
     *  поток завершится с `AudioEvent.Failed`; проверьте через [isTtsAvailable]).
     * @param speed множитель скорости речи; значения вне диапазона
     *  коэрсятся реализацией.
     */
    fun speak(text: String, languageId: LanguageId, speed: Float = 1.0f): Flow<AudioEvent>

    /**
     * Немедленно остановить текущее озвучивание/воспроизведение (TTS) и освободить
     * аудиопоток. Идемпотентен: безопасен, если ничего не играет. Не отменяет
     * корутину-источник `speak`, а сигнализирует движку остановиться — поэтому
     * коллектор `speak` получит обычное завершение потока.
     */
    suspend fun stop()

    /**
     * Записать голос с микрофона и распознать речь (ASR / offline Whisper + VAD)
     * для языка [languageId]. Возвращает `Flow` событий распознавания:
     * `ListeningStarted` → `Partial` (промежуточные гипотезы) →
     * `EndpointDetected` (VAD нашёл паузу конца фразы) → `Final` (готовый текст)
     * либо `Failed`.
     *
     * Распознавание заканчивается, когда VAD обнаруживает конец речи (пауза после
     * фразы) либо по таймауту. Отмена коллекктора прерывает запись. Реализация
     * маршрутизирует на Bluetooth-микрофон, если он включён
     * (см. [AudioModelRepository.observeBluetoothMic]).
     *
     * Перед вызовом ASR-модель должна быть загружена ([isAsrAvailable]), иначе
     * поток завершится с `RecognitionEvent.Failed`.
     *
     * @param languageId язык распознавания (Whisper language code выводится из него).
     */
    fun recognizeSpeech(languageId: LanguageId): Flow<RecognitionEvent>

    /**
     * Сыграть короткий звуковой эффект (SFX) — например, сигнал правильного/
     * неправильного ответа. Не блокирует надолго (звуки < 2 с). Идемпотентен и
     * не мешает текущему TTS/ASR.
     *
     * @param effect тип эффекта (см. [SoundEffect]).
     */
    suspend fun playSoundEffect(effect: SoundEffect)

    /**
     * Готова ли TTS-модель для [languageId] (файлы загружены и распакованы на диск).
     * Дешёвая проверка наличия файлов — без загрузки нативной модели в память.
     */
    suspend fun isTtsAvailable(languageId: LanguageId): Boolean

    /**
     * Готова ли ASR-модель (Whisper + VAD файлы на диске). Модель одна на все
     * языки (multilingual), поэтому язык не передаётся.
     */
    suspend fun isAsrAvailable(): Boolean
}

/**
 * Дискретные события жизненного цикла озвучивания текста (TTS).
 *
 * Эмитятся реализацией [AudioRepository.speak] в поток. UI реагирует на каждое:
 * `Started` — показать индикатор «говорит»; `Completed`/`Failed` — скрыть.
 */
sealed interface AudioEvent {
    /** Озвучивание началось (модель загружена, аудиопоток открыт). */
    data object Started : AudioEvent

    /**
     * Прогресс озвучивания как доля `0f..1f`. Может не эмититься вовсе (если
     * движок не сообщает прогресс) — UI должен это допускать.
     */
    data class Progress(val fraction: Float) : AudioEvent

    /** Озвучивание успешно завершено (дойдено до конца текста). */
    data object Completed : AudioEvent

    /** Озвучивание завершилось ошибкой. [error] — причина (для лога/UI). */
    data class Failed(val error: Throwable) : AudioEvent
}

/**
 * Дискретные события распознавания речи (ASR).
 *
 * Эмитятся реализацией [AudioRepository.recognizeSpeech]. `Partial` —
 * промежуточные гипотезы (можно показывать «печатаемый» текст в реальном
 * времени), `Final` — итоговый распознанный текст после детекции конца фразы.
 */
sealed interface RecognitionEvent {
    /** Микрофон начал запись (VAD готов, ожидается речь). */
    data object ListeningStarted : RecognitionEvent

    /** VAD обнаружил конец фразы (пауза) — скоро придёт [Final]. */
    data object EndpointDetected : RecognitionEvent

    /** Промежуточная (нестабильная) гипотеза распознавания в реальном времени. */
    data class Partial(val text: String) : RecognitionEvent

    /** Финальный распознанный текст (поток после этого завершается). */
    data class Final(val text: String) : RecognitionEvent

    /** Распознавание завершилось ошибкой. */
    data class Failed(val error: Throwable) : RecognitionEvent
}

/**
 * Короткие звуковые эффекты (SFX) для обратной связи на ответ пользователя
 * и завершение урока. Играются через `SoundPool` мгновенно, без TTS.
 *
 * Соответствие ресурсам: `CORRECT_ANSWER` / `WRONG_ANSWER` — mp3-клипы из
 * `res/raw` (`voicy_correct_answer`, `voicy_bad_answer`); `LESSON_COMPLETE`
 * — пока может использовать тот же клип корректного ответа, пока не добавлен
 * отдельный ресурс (см. TODO в `SherpaAudioRepository`).
 */
enum class SoundEffect { CORRECT_ANSWER, WRONG_ANSWER, LESSON_COMPLETE }
