package com.alexpo.grammermate.ui.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.alexpo.grammermate.shared.audio.SpeechRouteLease
import kotlinx.coroutines.CancellationException

/** UI-owned OS recognizer attempt; the audio layer owns its route lease. */
class SpeechRecognitionAttempt(private val acquireRoute: suspend () -> SpeechRouteLease?) {
    private val lock = Any()
    private var generation = 0L
    private var busy = false
    private var disposed = false
    private var route: SpeechRouteLease? = null

    /** Waits for routing before launching. Returns false when busy, disposed or unavailable. */
    suspend fun launch(startActivity: () -> Unit): Boolean {
        val id = synchronized(lock) {
            if (busy || disposed) return false
            busy = true
            ++generation
        }
        var pendingRoute: SpeechRouteLease? = null
        try {
            pendingRoute = acquireRoute() ?: return false
            val current = synchronized(lock) {
                if (disposed || generation != id) false else {
                    route = pendingRoute
                    true
                }
            }
            if (!current) return false
            pendingRoute = null // Ownership transferred to the ActivityResult callback.
            return try {
                startActivity()
                true
            } catch (cancelled: CancellationException) {
                finish()
                throw cancelled
            } catch (_: Exception) {
                finish()
                false
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        } finally {
            pendingRoute?.close()
            synchronized(lock) {
                if (generation == id && route == null) busy = false
            }
        }
    }

    /** Call before handling any result, including a canceled recognizer. */
    fun finish() {
        val closing = synchronized(lock) {
            val current = route
            route = null
            busy = false
            generation++
            current
        }
        closing?.close()
    }

    /** Route cleanup when the launcher leaves composition. */
    fun dispose() {
        val closing = synchronized(lock) {
            disposed = true
            generation++
            busy = false
            val current = route
            route = null
            current
        }
        closing?.close()
    }
}

@Composable
fun rememberSpeechRecognitionAttempt(acquireRoute: suspend () -> SpeechRouteLease?): SpeechRecognitionAttempt {
    val latestAcquire by rememberUpdatedState(acquireRoute)
    val attempt = remember { SpeechRecognitionAttempt { latestAcquire() } }
    DisposableEffect(attempt) {
        onDispose { attempt.dispose() }
    }
    return attempt
}
