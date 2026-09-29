package com.alexpo.grammermate.shared.audio

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** A single recording's claim on the microphone route. Closing it is idempotent. */
class SpeechRouteLease internal constructor(private val release: () -> Unit) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) release()
    }
}

/** Serializes Bluetooth routing across offline and external speech recognition. */
internal class SpeechRouteOwner(
    private val connect: suspend () -> Boolean,
    private val disconnect: () -> Unit,
    private val publishConnected: (Boolean) -> Unit
) {
    private enum class Phase { IDLE, PREPARING, ACTIVE, CLOSED }

    private val lock = Any()
    private var phase = Phase.IDLE
    private var nextId = 0L
    private var activeId = 0L
    private var routeConnected = false
    private var bluetoothEnabled = false

    /** Returns null while another recording owns or is preparing the route. */
    suspend fun acquire(): SpeechRouteLease? {
        val (id, useBluetooth) = synchronized(lock) {
            if (phase != Phase.IDLE) return null
            phase = Phase.PREPARING
            activeId = ++nextId
            activeId to bluetoothEnabled
        }

        var setupAttempted = false
        var connected = false
        try {
            if (useBluetooth) {
                setupAttempted = true
                try {
                    connected = connect()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Recognition can still use the device microphone.
                }
            }
            return synchronized(lock) {
                val abandoned = phase != Phase.PREPARING || activeId != id
                val keepRoute = connected && bluetoothEnabled && !abandoned
                if (setupAttempted && !keepRoute) disconnect()
                if (abandoned) {
                    if (phase != Phase.CLOSED && activeId == id) phase = Phase.IDLE
                    publishConnected(false)
                    null
                } else {
                    phase = Phase.ACTIVE
                    routeConnected = keepRoute
                    publishConnected(keepRoute)
                    SpeechRouteLease { release(id) }
                }
            }
        } catch (failure: Throwable) {
            synchronized(lock) {
                if (setupAttempted) disconnect()
                if (phase != Phase.CLOSED && activeId == id) phase = Phase.IDLE
                routeConnected = false
                publishConnected(false)
            }
            throw failure
        }
    }

    private fun release(id: Long) = synchronized(lock) {
        if (phase != Phase.ACTIVE || activeId != id) return@synchronized
        if (routeConnected) disconnect()
        routeConnected = false
        phase = Phase.IDLE
        publishConnected(false)
    }

    /** Turning Bluetooth off releases routing without ending recognition. */
    fun setBluetoothEnabled(enabled: Boolean) = synchronized(lock) {
        bluetoothEnabled = enabled
        if (!enabled && phase == Phase.ACTIVE && routeConnected) {
            disconnect()
            routeConnected = false
            publishConnected(false)
        }
    }

    /** Reject future attempts; an in-flight connect cleans itself up when it returns. */
    fun shutdown() = synchronized(lock) {
        if (phase == Phase.ACTIVE && routeConnected) disconnect()
        routeConnected = false
        bluetoothEnabled = false
        phase = Phase.CLOSED
        publishConnected(false)
    }
}
