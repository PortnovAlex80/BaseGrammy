package com.alexpo.grammermate.feature.training

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Owns the running clock; the session state remains the source of persisted active time. */
internal class SessionActiveTimer(
    private val scope: CoroutineScope,
    private val readPersistedMs: () -> Long,
    private val writePersistedMs: (Long) -> Unit,
    private val onDisplayTick: ((Long) -> Unit)?,
    private val onCheckpoint: () -> Unit,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime
) {
    private var job: Job? = null
    private var activeStartMs: Long? = null
    private var tickCounter = 0
    private var accumulatedMs = 0L

    fun resume() {
        if (job?.isActive == true) return
        activeStartMs = nowMs()
        tickCounter = 0
        accumulatedMs = readPersistedMs()
        job = scope.launch {
            while (true) {
                delay(500)
                val start = activeStartMs ?: continue
                val elapsed = nowMs() - start
                activeStartMs = nowMs()
                accumulatedMs += elapsed
                onDisplayTick?.invoke(accumulatedMs)

                tickCounter++
                if (tickCounter >= 20) {
                    tickCounter = 0
                    writePersistedMs(accumulatedMs)
                    onCheckpoint()
                }
            }
        }
    }

    fun pause() {
        if (accumulatedMs > 0 && job?.isActive == true) {
            writePersistedMs(accumulatedMs)
        }
        job?.cancel()
        job = null
        activeStartMs = null
        tickCounter = 0
    }
}
