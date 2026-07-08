package com.alexpo.grammermate.v2.core.data.audio

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper для проверки памяти устройства перед загрузкой тяжёлых моделей.
 * Различает Java heap и native memory (Whisper ~375 МБ, VITS ~150 МБ — native).
 *
 * **Архитектурная роль.** Pre-check перед каждым `OfflineTts(...)` / `OfflineRecognizer(...)`
 * constructor — hard block вместо OOM-crash (FR-10, NFR-2). SRS-003 §2.10.
 *
 * **AC-13 (hard block).** При нехватке памяти ([hasEnoughMemory] → false /
 * [checkForLoad] → [MemoryCheckResult.Blocked]) вызывающий wrapper
 * (`TtsEngineWrapper.initialize` / `AsrEngineWrapper.initialize`) обязан НЕ
 * вызывать нативный constructor и перевести стейт в `Error`/`ERROR` с
 * каноническим сообщением [TTS_BLOCK_MESSAGE]/[ASR_BLOCK_MESSAGE]. Это
 * гарантирует предсказуемый сигнал вместо OOM-crash процесса (AC-13, NFR-2).
 *
 * Перенос из legacy `data/MemoryChecker.kt` (v1). SRS-003 FR-10, NFR-2.
 *
 * @param context application context (для `ActivityManager`).
 */
@Singleton
class MemoryChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    /**
     * Test seam: возвращает доступную native-память (МБ) или `null`, если
     * `ActivityManager` недоступен (→ heap-only fallback через [checkHeapOnly]).
     *
     * Production читает `ActivityManager.MemoryInfo.availMem`. Тесты в том же
     * package переназначают это поле, чтобы детерминированно симулировать
     * low-memory устройство (AC-13: `availMem < requiredMb × 1.5`) без запуска
     * нативного кода и без Robolectric. См. `MemoryCheckerHardBlockTest`.
     */
    @Volatile
    internal var nativeAvailableMbProvider: () -> Long? = ::readNativeAvailableMb

    /**
     * Test seam: возвращает доступный Java heap (МБ) =
     * `Runtime.maxMemory − (total − free)`. Production считает по реальному
     * `Runtime`. Тесты переназначают это поле, чтобы сделать формулу hard-block
     * (`availableTotalMb = native + heap`) полностью детерминированной,
     * независимо от heap-размера тестовой JVM (AC-13 regression-lock).
     */
    @Volatile
    internal var heapAvailableMbProvider: () -> Long = ::readHeapAvailableMb

    private fun readNativeAvailableMb(): Long? {
        val info = ActivityManager.MemoryInfo()
        val am = activityManager ?: return null
        am.getMemoryInfo(info)
        return info.availMem / MB
    }

    private fun readHeapAvailableMb(): Long {
        val rt = Runtime.getRuntime()
        val maxHeapMb = rt.maxMemory() / MB
        val usedHeapMb = (rt.totalMemory() - rt.freeMemory()) / MB
        return maxHeapMb - usedHeapMb
    }

    /**
     * Достаточно ли памяти для модели размера [requiredMb].
     *
     * Решение: `availableTotalMb >= requiredMb × safetyMargin`, где
     * `availableTotalMb = native(availMem) + heap(maxMemory − used)`. При
     * недоступности `ActivityManager` проверяется только heap (fallback).
     *
     * @param requiredMb требуемая память в МБ (TTS ≈ [REQUIRED_MB_TTS],
     *  ASR ≈ [REQUIRED_MB_ASR]).
     * @param safetyMargin дополнительный запас (default [DEFAULT_SAFETY_MARGIN]=1.5).
     * @return true если памяти достаточно, false — hard block (AC-13).
     */
    fun hasEnoughMemory(requiredMb: Long, safetyMargin: Float = DEFAULT_SAFETY_MARGIN): Boolean {
        val nativeAvailableMb = nativeAvailableMbProvider()
        if (nativeAvailableMb == null) {
            Log.w(TAG, "ActivityManager not available, using heap-only check")
            return checkHeapOnly(requiredMb, safetyMargin)
        }

        val availableHeapMb = heapAvailableMbProvider()
        val totalAvailableMb = nativeAvailableMb + availableHeapMb

        val enough = isEnough(totalAvailableMb, requiredMb, safetyMargin)

        Log.d(
            TAG,
            "Memory check: required=${requiredMb}MB, native=${nativeAvailableMb}MB, " +
                "heap=${availableHeapMb}MB, total=${totalAvailableMb}MB, enough=$enough"
        )

        return enough
    }

    /**
     * Типизированный hard-block статус для [ModelKind.TTS]/[ModelKind.ASR] (AC-13).
     *
     * В отличие от булева [hasEnoughMemory], несёт каноническое сообщение и
     * метрики — wrapper использует [MemoryCheckResult.message] как причину
     * `TtsState.Error`/`AsrState.ERROR` (FR-10), без магических строк.
     *
     * @param kind тип модели (определяет [requiredMb] и каноническое сообщение).
     * @param safetyMargin запас (default [DEFAULT_SAFETY_MARGIN]).
     * @return [MemoryCheckResult.Enough] либо [MemoryCheckResult.Blocked] (hard block).
     */
    fun checkForLoad(kind: ModelKind, safetyMargin: Float = DEFAULT_SAFETY_MARGIN): MemoryCheckResult {
        val requiredMb = kind.requiredMb
        if (hasEnoughMemory(requiredMb, safetyMargin)) {
            return MemoryCheckResult.Enough
        }
        val nativeMb = nativeAvailableMbProvider()
        val availableMb = (nativeMb ?: 0L) + heapAvailableMbProvider()
        return MemoryCheckResult.Blocked(
            message = kind.blockMessage,
            modelKind = kind,
            requiredMb = requiredMb,
            availableMb = availableMb,
            safetyMargin = safetyMargin,
        )
    }

    /** Сахар над [hasEnoughMemory] для TTS (requiredMb=[REQUIRED_MB_TTS]). FR-10. */
    fun hasEnoughMemoryForTts(safetyMargin: Float = DEFAULT_SAFETY_MARGIN): Boolean =
        hasEnoughMemory(REQUIRED_MB_TTS, safetyMargin)

    /** Сахар над [hasEnoughMemory] для ASR (requiredMb=[REQUIRED_MB_ASR]). FR-10. */
    fun hasEnoughMemoryForAsr(safetyMargin: Float = DEFAULT_SAFETY_MARGIN): Boolean =
        hasEnoughMemory(REQUIRED_MB_ASR, safetyMargin)

    /**
     * Pure-решение о достаточности памяти (без Android-зависимостей).
     *
     * Вынесено отдельно для детерминированного unit-теста границы hard-block
     * (AC-13): `availableTotalMb >= requiredMb × safetyMargin`.
     */
    internal fun isEnough(availableTotalMb: Long, requiredMb: Long, safetyMargin: Float): Boolean =
        availableTotalMb >= (requiredMb * safetyMargin).toLong()

    /**
     * Детальная memory info для логирования (ModelLoadLogger writes it to Downloads).
     */
    fun getMemoryInfo(): String {
        val rt = Runtime.getRuntime()
        val maxHeapMb = rt.maxMemory() / (1024 * 1024)
        val totalHeapMb = rt.totalMemory() / (1024 * 1024)
        val freeHeapMb = rt.freeMemory() / (1024 * 1024)
        val usedHeapMb = totalHeapMb - freeHeapMb

        val info = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(info)

        return buildString {
            append("Heap: ${usedHeapMb}MB/${maxHeapMb}MB (free: ${freeHeapMb}MB)")
            // `info` всегда non-null когда activityManager non-null (см. getMemoryInfo above);
            // для null-activityManager (крайне редко на real device) — только heap.
            info?.let { mi ->
                val totalMemMb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    mi.totalMem / (1024 * 1024)
                } else {
                    "unknown"
                }
                val availMemMb = mi.availMem / (1024 * 1024)
                append(", Native: ${availMemMb}MB available (total: ${totalMemMb}MB)")
                if (mi.lowMemory) append(", LOW_MEMORY=true")
            }
        }
    }

    /**
     * В low-memory ли устройство сейчас.
     */
    fun isLowMemoryDevice(): Boolean {
        val info = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(info) ?: return false
        return info.lowMemory
    }

    private fun checkHeapOnly(requiredMb: Long, safetyMargin: Float): Boolean {
        return isEnough(heapAvailableMbProvider(), requiredMb, safetyMargin)
    }

    /**
     * Тип модели для типизированного [checkForLoad] hard-block статуса (AC-13).
     *
     * @property requiredMb канонический порог свободной памяти для загрузки модели
     *  (FR-10: TTS≈150, ASR≈800 МБ). Канон — SRS-003 §FR-10, AC-13.
     * @property blockMessage каноническое сообщение-причина для `TtsState.Error`/
     *  `AsrState.ERROR` при hard block. Закреплено AC-13 etalon — wrapper'ы
     *  используют ровно эту строку.
     */
    enum class ModelKind(val requiredMb: Long, val blockMessage: String) {
        /**
         * Sherpa-ONNX OfflineTts (VITS-Piper/KOKORO). Канонический порог
         * [REQUIRED_MB_TTS]≈150 МБ native (FR-10).
         */
        TTS(REQUIRED_MB_TTS, TTS_BLOCK_MESSAGE),

        /**
         * Whisper Small (encoder+decoder.int8.onnx) + Silero VAD. Канонический
         * порог [REQUIRED_MB_ASR]≈800 МБ native (FR-10) — модель тяжелее TTS.
         */
        ASR(REQUIRED_MB_ASR, ASR_BLOCK_MESSAGE),
    }

    companion object {
        private const val TAG = "MemoryChecker"

        /** Байт в МБ (`/ 1024 / 1024`) — единый делитель для всего класса. */
        internal const val MB: Long = 1024L * 1024L

        /**
         * Канонический safety margin (×1.5). AC-13: hard block при
         * `availableMb < requiredMb × 1.5`. Источник: SRS-003 §FR-10.
         */
        const val DEFAULT_SAFETY_MARGIN: Float = 1.5f

        /**
         * Канонический порог свободной памяти для загрузки TTS-модели (≈150 МБ).
         * AC-13 / FR-10 etalon. wrapper вызывает `hasEnoughMemory(REQUIRED_MB_TTS)`.
         */
        const val REQUIRED_MB_TTS: Long = 150L

        /**
         * Канонический порог свободной памяти для загрузки ASR-модели (≈800 МБ;
         * Whisper Small + Silero VAD). AC-13 / FR-10 etalon.
         */
        const val REQUIRED_MB_ASR: Long = 800L

        /**
         * Каноническое сообщение-причина при hard block TTS (AC-13 etalon).
         * `TtsEngineWrapper` устанавливает его в `TtsState.Error(reason)`.
         */
        const val TTS_BLOCK_MESSAGE: String = "Not enough memory to load voice model"

        /**
         * Каноническое сообщение-причина при hard block ASR (AC-13 etalon).
         * `AsrEngineWrapper` устанавливает его в `errorMessage`/`AsrState.ERROR`.
         */
        const val ASR_BLOCK_MESSAGE: String = "Not enough memory to load speech recognition model"
    }
}

/**
 * Результат типизированной проверки памяти перед native load (AC-13).
 *
 * wrapper'ы (`TtsEngineWrapper`/`AsrEngineWrapper`) сопоставляют Enough →
 * proceed to native constructor, Blocked → hard-block (state → Error/ERROR с
 * [Blocked.message], `Flow<AudioEvent>`/`Flow<RecognitionEvent>` эмитит `Failed`,
 * native constructor **не вызывается**, процесс жив — нет OOM-crash).
 */
sealed interface MemoryCheckResult {
    /** Памяти достаточно — native load разрешён. */
    data object Enough : MemoryCheckResult

    /**
     * Hard block (AC-13): памяти недостаточно. native constructor НЕ вызывается.
     *
     * @property message каноническая причина ([MemoryChecker.TTS_BLOCK_MESSAGE] /
     *  [MemoryChecker.ASR_BLOCK_MESSAGE]) — для `TtsState.Error`/`AsrState.ERROR`.
     * @property modelKind тип модели (TTS/ASR), для которого сработал блок.
     * @property requiredMb канонический порог ([ModelKind.requiredMb]).
     * @property availableMb фактическая доступная память на момент проверки (МБ).
     * @property safetyMargin применённый запас (×[requiredMb]).
     */
    data class Blocked(
        val message: String,
        val modelKind: MemoryChecker.ModelKind,
        val requiredMb: Long,
        val availableMb: Long,
        val safetyMargin: Float,
    ) : MemoryCheckResult
}
