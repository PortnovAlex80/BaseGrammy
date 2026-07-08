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
     * Достаточно ли памяти для модели размера [requiredMb].
     *
     * @param requiredMb требуемая память в МБ.
     * @param safetyMargin дополнительный запас (default 50%).
     * @return true если памяти достаточно, false иначе.
     */
    fun hasEnoughMemory(requiredMb: Long, safetyMargin: Float = 1.5f): Boolean {
        val info = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(info) ?: run {
            Log.w(TAG, "ActivityManager not available, using heap-only check")
            return checkHeapOnly(requiredMb, safetyMargin)
        }

        // Native-память доступна (availMem, totalMem - threshold).
        val availableNativeMb = info.availMem / (1024 * 1024)

        // Java heap доступен.
        val rt = Runtime.getRuntime()
        val maxHeapMb = rt.maxMemory() / (1024 * 1024)
        val usedHeapMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val availableHeapMb = maxHeapMb - usedHeapMb

        // Total доступно с safety margin.
        val totalAvailableMb = availableNativeMb + availableHeapMb
        val requiredWithMargin = (requiredMb * safetyMargin).toLong()

        val enough = totalAvailableMb >= requiredWithMargin

        Log.d(
            TAG,
            "Memory check: required=${requiredMb}MB, native=${availableNativeMb}MB, " +
                "heap=${availableHeapMb}MB, total=${totalAvailableMb}MB, enough=$enough"
        )

        return enough
    }

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
        val rt = Runtime.getRuntime()
        val maxHeapMb = rt.maxMemory() / (1024 * 1024)
        val usedHeapMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val availableHeapMb = maxHeapMb - usedHeapMb
        return availableHeapMb >= (requiredMb * safetyMargin)
    }

    companion object {
        private const val TAG = "MemoryChecker"
    }
}
