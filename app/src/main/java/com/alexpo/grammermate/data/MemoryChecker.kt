package com.alexpo.grammermate.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Helper for checking device memory before loading large models.
 * Distinguishes between Java heap and native memory.
 */
class MemoryChecker(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    /**
     * Check if device has enough memory for a model of given size.
     * @param requiredMb Required memory in MB
     * @param safetyMargin Additional safety margin (default 50%)
     * @return true if enough memory available, false otherwise
     */
    fun hasEnoughMemory(requiredMb: Long, safetyMargin: Float = 1.5f): Boolean {
        val info = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(info) ?: run {
            Log.w(TAG, "ActivityManager not available, using heap-only check")
            return checkHeapOnly(requiredMb, safetyMargin)
        }

        // Native memory available (totalMem - threshold)
        val availableNativeMb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            (info.availMem / (1024 * 1024))
        } else {
            @Suppress("DEPRECATION")
            (info.availMem / (1024 * 1024))
        }

        // Java heap available
        val rt = Runtime.getRuntime()
        val maxHeapMb = rt.maxMemory() / (1024 * 1024)
        val usedHeapMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val availableHeapMb = maxHeapMb - usedHeapMb

        // Total available with safety margin
        val totalAvailableMb = availableNativeMb + availableHeapMb
        val requiredWithMargin = (requiredMb * safetyMargin).toLong()

        val enough = totalAvailableMb >= requiredWithMargin

        Log.d(TAG, "Memory check: required=${requiredMb}MB, " +
                "native=${availableNativeMb}MB, heap=${availableHeapMb}MB, " +
                "total=${totalAvailableMb}MB, enough=$enough")

        return enough
    }

    /**
     * Get detailed memory info for logging.
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
            if (info != null) {
                val totalMemMb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    info.totalMem / (1024 * 1024)
                } else "unknown"
                val availMemMb = info.availMem / (1024 * 1024)
                append(", Native: ${availMemMb}MB available (total: ${totalMemMb}MB)")
                if (info.lowMemory) append(", LOW_MEMORY=true")
            }
        }
    }

    /**
     * Check if device is in low memory state.
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
