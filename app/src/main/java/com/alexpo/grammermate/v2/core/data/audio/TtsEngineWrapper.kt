package com.alexpo.grammermate.v2.core.data.audio

import android.content.Context
import android.util.Log
import com.alexpo.grammermate.domain.model.InitPhase
import com.alexpo.grammermate.domain.model.TtsState
import com.k2fsa.sherpa.onnx.OfflineTts
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над Sherpa-ONNX `OfflineTts` + Android `AudioTrack` (+ fallback на
 * системный `TextToSpeech` для 32-bit ARM / отсутствующих VITS-моделей).
 *
 * **Архитектурная роль.** Чистый data-слойный адаптер, инжектируемый в
 * [SherpaAudioRepository] и [SegmentPlayer]. Хостит резидентный LRU-кэш
 * [ResidentTtsCache] (мгновенное переключение языков), state machine
 * [TtsState] и всю нативную/платформенную логику синтеза. Домен про Sherpa/Android
 * не знает — он видит только `Flow<AudioEvent>` через [SherpaAudioRepository].
 *
 * Перенос из legacy `data/TtsEngine.kt` (v1, 939 строк AudioCoordinator →
 * изолированная обёртка). SRS-003 §2.3, FR-2, FR-3, FR-4, FR-15, FR-16.
 *
 * ## Статус (scaffold, E03 task #467)
 *
 * Это **скелет**: публичная поверхность (state machine, suspend-методы,
 * конструктор-контракт) зафиксирована, тяжёлая нативная логика — TODO со
 * ссылками на legacy-исходники. Body-задачи (AC-2..AC-7) реализуют TODO внутри
 * этого фиксированного контракта. НЕ менять сигнатуры публичных методов —
 * SegmentPlayer (shared с E08/E09) и SherpaAudioRepository зависят от них.
 *
 * @param context application context (для `filesDir`, `AudioManager`, системного TTS).
 * @param memoryChecker pre-check памяти перед загрузкой каждой нативной модели
 *  (FR-10, NFR-2 — hard block вместо OOM-crash).
 */
@Singleton
class TtsEngineWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryChecker: MemoryChecker,
) {
    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)

    /** Реактивное состояние движка (FR-16). UI/data наблюдают переходы. */
    val state: StateFlow<TtsState> = _state

    /** Готов ли движок (синтаксический сахар над [state]). */
    val isReady: Boolean
        get() = _state.value == TtsState.Ready

    /** Текущий активный язык (language id), или null если движок не инициализирован. */
    var activeLanguageId: String? = null
        private set

    /**
     * Резидентный LRU-кэш нативных [OfflineTts]-моделей по language id.
     * Заменяет single-model var v1 — позволяет it + ru сосуществовать в RAM.
     * FR-3 (maxSize=3, eviction policy), NFR-4 (latency <100 мс).
     *
     * Тип-носитель зафиксирован как [OfflineTts] через фабрику
     * [ResidentTtsCache.forOfflineTts] — freeFn дёргает native `free()`.
     */
    private val offlineTtsCache: ResidentTtsCache<OfflineTts> =
        ResidentTtsCache.forOfflineTts()

    /**
     * Callback прогресса инициализации (фаза + проценты). SRS-003 §2.3 —
     *对应ствие legacy `onInitializing`. Body-задача AC может подключать сюда
     * `DownloadState.Initializing`.
     */
    var onInitializing: ((InitPhase, Int) -> Unit)? = null
        private set

    fun setInitializingCallback(callback: ((InitPhase, Int) -> Unit)?) {
        onInitializing = callback
    }

    /**
     * Инициализировать/загрузить TTS-модель для [languageId].
     *
     * TODO(body, AC-2+AC-3+AC-4+AC-5): перенести логику из legacy
     *   `data/TtsEngine.kt:235-348`:
     *   1. Fast-path: язык уже резидентен в [offlineTtsCache] → `Ready` без load.
     *   2. Иначе: `TtsModelRegistry.specFor(languageId)` → spec (modelDirName,
     *      requiredFiles, modelType). MemoryChecker.hasEnoughMemory pre-check.
     *   3. Загрузка `OfflineTts(null, buildConfig(spec, modelDir))` →
     *      `offlineTtsCache.put(languageId, tts)` (LRU eviction если >maxSize).
     *   4. Fallback на Android `TextToSpeech` (32-bit ARM SIGBUS / VITS_PIPER
     *      файлы отсутствуют) — legacy `TtsEngine.kt:354-431`.
     *   5. State machine CAS: Idle/Error → Initializing → Ready | Error(reason).
     *
     * Сейчас: no-op skeleton (оставляет Idle).
     */
    suspend fun initialize(languageId: String = "en") {
        // TODO(body): port legacy TtsEngine.initialize (see KDoc).
        Log.d(TAG, "initialize($languageId) — skeleton no-op (resident=${offlineTtsCache.size()})")
    }

    /**
     * Синтез + воспроизведение [text] на языке [languageId] со скоростью [speed].
     *
     * TODO(body, AC-2+AC-4+AC-7): перенести логику из legacy
     *   `data/TtsEngine.kt:465-687`:
     *   1. Если язык не резидентен — [initialize].
     *   2. Native VITS/KOKORO: `tts.generateWithConfigAndCallback(text,
     *      GenerationConfig(sid, speed))` → `AudioTrack` PCM-float playback
     *      (USAGE_MEDIA/CONTENT_TYPE_SPEECH). Drain-loop по playbackHeadPosition.
     *   3. System-TTS fallback: `TextToSpeech.speak` + UtteranceProgressListener.
     *   4. RU text-scale: для lang=="ru" применяется тюнинг lengthScale/noise
     *      (legacy TtsEngine RU-branch, SRS FR-4).
     *   5. Cancellation-safe: `isStopped` флаг, `speakJob.cancel()` (NFR-5).
     *
     * @param text markdown-cleaned текст для синтеза.
     * @param languageId целевой язык (модель должна быть резидентна).
     * @param speed множитель скорости речи (legacy coerce 0.3..3.0).
     */
    suspend fun speak(text: String, languageId: String = "en", speed: Float = 1.0f) {
        if (text.isBlank()) return
        // TODO(body): port legacy TtsEngine.speak (see KDoc).
        Log.d(TAG, "speak(text.length=${text.length}, lang=$languageId, speed=$speed) — skeleton no-op")
    }

    /**
     * Неблокирующая остановка воспроизведения (FR-15, NFR-5).
     *
     * TODO(body, AC-20): перенести из legacy `TtsEngine.kt:689-706`:
     *   `wasStopped.set(true)`, `generation.incrementAndGet()` (инвалидация
     *   drain-loop), `isStopped.set(true)`, `systemTts?.stop()`,
     *   `currentTrack?.stop()` в try/catch, `speakJob.cancel()` БЕЗ `join`
     *   (legacy ANR-fix: runBlocking+join → 5s ANR на main).
     *
     * Сейчас: no-op skeleton.
     */
    fun stop() {
        // TODO(body): port legacy TtsEngine.stop (see KDoc).
        Log.d(TAG, "stop() — skeleton no-op (state=${_state.value})")
    }

    /** Приостановить воспроизведение (`AudioTrack.pause`). TODO(body): legacy `pause()`. */
    fun pause() {
        // TODO(body): port legacy TtsEngine.pause.
        Log.d(TAG, "pause() — skeleton no-op")
    }

    /** Возобновить воспроизведение (`AudioTrack.play`). TODO(body): legacy `resume()`. */
    fun resume() {
        // TODO(body): port legacy TtsEngine.resume.
        Log.d(TAG, "resume() — skeleton no-op")
    }

    /**
     * Полный teardown: освободить ВСЕ резидентные модели, shutdown system-TTS,
     * сбросить state в Idle. TODO(body): legacy `release()`.
     */
    fun release() {
        // TODO(body): port legacy TtsEngine.release.
        offlineTtsCache.clear()
        activeLanguageId = null
        _state.value = TtsState.Idle
        Log.d(TAG, "release() — skeleton (cache cleared)")
    }

    /**
     * Per-language teardown: освободить резидентную модель для [lang] не затрагивая
     * остальные языки. TODO(body): legacy `releaseLanguage(lang)`.
     */
    fun releaseLanguage(lang: String) {
        // TODO(body): port legacy TtsEngine.releaseLanguage.
        offlineTtsCache.remove(lang)
        if (activeLanguageId == lang) activeLanguageId = null
        Log.d(TAG, "releaseLanguage($lang) — skeleton (removed from cache)")
    }

    companion object {
        private const val TAG = "TtsEngineWrapper"
    }
}
